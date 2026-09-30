package com.kallan.naistudio.desktop.platform

import com.kallan.naistudio.platform.logInfo
import com.kallan.naistudio.platform.logWarn
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Window
import java.io.File

/**
 * 一次"选目录"的结果。
 *
 * 为什么要分 [Cancelled] 与 [Unavailable]（而不是简单地 `String?`）：
 * 对调用方来说这两件事**要做的事完全不同** ——
 * 取消是"用户不想选，收工"；不可用是"这条路今天走不通，请换个办法再问一次"。
 * 合成一个 null 的话，取消也会再弹一个（更丑的）备用对话框，那就成了"点了取消又冒出来一个框"✗。
 */
internal sealed class FolderPick {
    /** 用户选了某个目录（绝对路径）。 */
    class Picked(val path: String) : FolderPick()

    /** 用户取消（点"取消"、按 Esc、右上角关掉）。 */
    object Cancelled : FolderPick()

    /** 原生对话框用不了（非 Windows / JNA 缺件 / COM 建不出来）→ 调用方退回 Swing 那个选择器。 */
    object Unavailable : FolderPick()
}

/**
 * **Windows 原生的"选文件夹"对话框**（用户 2026-09-24：「选择文件的框能不能用 win 的文件管理器?
 * 这个自带的不太好用」——说的就是原来那个 `JFileChooser`：它是 Java/Swing **自己画的**界面，
 * 跟资源管理器长得完全不一样；而选文件那条早就走 AWT 的 `FileDialog` 了，所以只有它显得突兀）。
 *
 * ## 为什么不能继续用 `java.awt.FileDialog`
 *
 * AWT 的 `FileDialog` 在 Windows 上就是 `GetOpenFileName`/`GetSaveFileName` 那一套，
 * 但它**没有"选文件夹"这个模式**（`FileDialog.LOAD` 只能选文件）—— 这就是当初退而求其次
 * 用 `JFileChooser` 的原因（见 `DesktopUiHost` 顶部那段说明）。
 *
 * 想要"Windows 自己的文件夹选择器"，只有一条路：Shell 的 **`IFileOpenDialog`**，
 * 带上 `FOS_PICKFOLDERS` 标志（Vista 起的 Common Item Dialog，就是资源管理器里
 * 「选择文件夹」那个框）。它是 **COM 接口，JDK 与 JNA 都没有现成封装**
 * （实测 `jna-platform` 5.14.0 只到 `Shell32.SHGetKnownFolderPath`，没有 `IFileOpenDialog`），
 * 所以这里**手写 vtable 调用**。
 *
 * ## 手写 vtable 调用是怎么做的（对照 shobjidl.h 的声明顺序）
 *
 * COM 接口在内存里长这样：`[lpVtbl] → [QueryInterface, AddRef, Release, …方法指针]`。
 * 所以「调第 N 个方法」= 读 `obj[0]` 拿到 vtable、再读 `vtable[N]` 拿到函数地址，
 * 用 stdcall 调它，**第一个参数手动传 `this`**（JNA 的 `Function` 是裸函数指针，没有代理帮忙塞 this）。
 * 这也是为什么下面每个下标都要写清楚：
 *
 * | 接口 | 下标 | 方法 |
 * |---|---|---|
 * | IUnknown | 0/1/2 | QueryInterface / AddRef / **Release** |
 * | IModalWindow | 3 | **Show(HWND)** |
 * | IFileDialog | 9 / 10 | **SetOptions / GetOptions**（DWORD 标志位） |
 * | IFileDialog | 12 | **SetFolder(IShellItem\*)**（初始目录） |
 * | IFileDialog | 17 | SetTitle |
 * | IFileDialog | 20 | **GetResult(IShellItem\*\*)** |
 * | IShellItem | 5 | **GetDisplayName(SIGDN, LPWSTR\*)** |
 *
 * 标志位：`FOS_PICKFOLDERS`（选文件夹，不是选文件）+ `FOS_FORCEFILESYSTEM`
 * （必须是文件系统里的真实路径 —— 少了它 `SIGDN_FILESYSPATH` 会失败）。
 *
 * ## 线程与 COM 套间（STA/MTA）
 *
 * `Show()` 会**阻塞到用户选完**（它自己跑一个模态消息循环），而调用它的正是 AWT 事件线程
 * —— 和原来 `FileDialog.isVisible = true` 的阻塞方式、以及"界面在对话框开着时不动"的
 * 既定口径完全一致（见 `DesktopUiHost` 顶部）。
 *
 * 进 COM 之前要 `CoInitializeEx(COINIT_APARTMENTTHREADED)`。三种返回都要正确处理：
 *  · `S_OK` / `S_FALSE`：初始化成功（后者=本线程早就初始化过了）→ **配对 `CoUninitialize`**；
 *  · `RPC_E_CHANGED_MODE`：本线程已经是**别的套间模型**（AWT 的拖放可能会先把线程搞成 MTA）
 *    —— 这时 COM **本来就是可用的**，什么都不用还，照旧往下走；
 *  · 其它失败：这条路判死，交给调用方退回 Swing。
 *
 * ## 绝不把 App 弄崩（硬要求）
 *
 * 整条路包在 `runCatching` 里 —— 它连 `NoClassDefFoundError` / `UnsatisfiedLinkError`
 * （JNA 不在、系统不是 Windows）一起接住；任何一步出岔子都只记一条日志、
 * 返回 [FolderPick.Unavailable]，由调用方退回原来那个 `JFileChooser`。
 * 换句话说：**最坏情况 = 今天没换上，跟以前一模一样**。
 */
internal object DesktopFolderDialog {

    // ---- CLSID / IID（取自 shobjidl.h / shlguid.h，改了就是另一个对象了）----

    /** `CLSID_FileOpenDialog`：Common Item Dialog 的那个 CoClass。 */
    private const val CLSID_FILE_OPEN_DIALOG = "DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7"

    /** `IID_IFileOpenDialog`。 */
    private const val IID_IFILE_OPEN_DIALOG = "d57c7288-d4ad-4768-be02-9d969532d960"

    /** `IID_IShellItem`（`SHCreateItemFromParsingName` 要它）。 */
    private const val IID_ISHELL_ITEM = "43826d1e-e718-42ee-bc55-a1e261c37bfe"

    /** `CLSCTX_INPROC_SERVER`。 */
    private const val CLSCTX_INPROC_SERVER = 0x1

    /** `COINIT_APARTMENTTHREADED`（STA：Shell 对话框的常规要求）。 */
    private const val COINIT_APARTMENTTHREADED = 0x2

    // ---- HRESULT ----
    private const val S_OK = 0
    private const val S_FALSE = 1
    private const val RPC_E_CHANGED_MODE = 0x80010106.toInt()
    private const val E_ABORT = 0x80004004.toInt()

    /** `HRESULT_FROM_WIN32(ERROR_CANCELLED)` —— 用户取消时 `Show` 就回这个。 */
    private const val HRESULT_CANCELLED = 0x800704C7.toInt()

    // ---- vtable 下标（见类注释那张表）----
    private const val VT_RELEASE = 2
    private const val VT_SHOW = 3
    private const val VT_SET_OPTIONS = 9
    private const val VT_GET_OPTIONS = 10
    private const val VT_SET_FOLDER = 12
    private const val VT_SET_TITLE = 17
    private const val VT_GET_RESULT = 20

    /** `IShellItem::GetDisplayName`。 */
    private const val VT_ITEM_GET_DISPLAY_NAME = 5

    /** `SIGDN_FILESYSPATH`：要"能用 `File` 打开的绝对路径"（而不是"显示名"）。 */
    private const val SIGDN_FILESYSPATH = 0x80058000.toInt()

    // ---- FOS_* 标志位 ----
    private const val FOS_PICKFOLDERS = 0x20
    private const val FOS_FORCEFILESYSTEM = 0x40

    /** 对话框标题（与原来 `JFileChooser` 那个标题保持一致，用户看不出换了实现）。 */
    private const val TITLE = "选择保存目录"

    /**
     * 弹出原生"选文件夹"对话框。
     *
     * @param owner 主窗口（拿它的 HWND 当对话框的 owner：这样对话框才属于本窗口，
     *   不会跑到后面去、也不会和主窗口抢输入）。null = 无主对话框，照样能用。
     * @param initialDir 打开时定位到哪个目录（"上次用过的目录"）；null / 已不存在则用系统默认位置。
     */
    fun pick(owner: Window?, initialDir: String?): FolderPick {
        if (!isWindowsPlatform()) return FolderPick.Unavailable
        return runCatching { pickNative(owner, initialDir) }.getOrElse { error ->
            // 连 NoClassDefFoundError / UnsatisfiedLinkError 都在这里落地（runCatching 接 Throwable）
            logWarn("FolderDialog", "原生目录对话框不可用，退回 Swing 选择器：$error")
            FolderPick.Unavailable
        }
    }

    // ------------------------------------------------------------------
    // 下面就是"手写 COM"那部分
    // ------------------------------------------------------------------

    private fun pickNative(owner: Window?, initialDir: String?): FolderPick {
        val ole32 = Ole32.INSTANCE
        val uninitNeeded = comInit(ole32)
        try {
            val dialog = createDialog(ole32) ?: return FolderPick.Unavailable
            try {
                applyOptions(dialog)
                setTitle(dialog)
                setInitialFolder(dialog, initialDir)

                val ownerHwnd = hwndOf(owner)
                // 主窗口先提到前面：对话框是挂在它身上的，主窗口在前面时才不会有
                // "弹出来的框在别的窗口底下"的观感（拿不到 HWND 就跳过，不值得为此报错）。
                if (ownerHwnd != null) {
                    runCatching { User32.INSTANCE.SetForegroundWindow(WinDef.HWND(ownerHwnd)) }
                }

                // ⚠️ Show 会阻塞到用户选完（这是刻意保持的行为，见类注释）
                val hr = comCall(dialog, VT_SHOW, ownerHwnd)
                return when {
                    hr == HRESULT_CANCELLED || hr == E_ABORT -> FolderPick.Cancelled
                    hr == S_OK -> {
                        val path = resultPath(dialog)
                        if (path.isNullOrBlank()) FolderPick.Cancelled else FolderPick.Picked(path)
                    }
                    else -> {
                        logWarn(
                            "FolderDialog",
                            "IFileOpenDialog::Show 失败 hr=0x${Integer.toHexString(hr)}，退回 Swing 选择器",
                        )
                        FolderPick.Unavailable
                    }
                }
            } finally {
                // 无论成功、取消、还是中途抛了异常，接口指针都要还回去（不然就是内存泄漏）
                release(dialog)
            }
        } finally {
            if (uninitNeeded) runCatching { ole32.CoUninitialize() }
        }
    }

    /**
     * `CoInitializeEx(STA)`。
     *
     * @return 要不要配对调 `CoUninitialize`（S_OK / S_FALSE 要；`RPC_E_CHANGED_MODE` 不要，
     *   因为那说明 COM 是这个线程上**别人**初始化的，我们没资格收摊）。
     */
    private fun comInit(ole32: Ole32): Boolean {
        // ⚠️ 用 `toInt()` 而不是 `intValue()`：Kotlin 把 Java `Number.intValue()` 映射成了
        // `Number.toInt()`，直接点 `intValue()` 是 "Unresolved reference"（实测编译报错）。
        // 两者在 JVM 上就是同一个方法，读的是同一个 32 位 HRESULT。
        val hr = ole32.CoInitializeEx(null, COINIT_APARTMENTTHREADED).toInt()
        return when (hr) {
            S_OK, S_FALSE -> true
            RPC_E_CHANGED_MODE -> {
                // AWT（拖放那条路）可能已经把这个线程初始化成 MTA 了。COM 是可用的，
                // 只是套间模型不是我们要的那个 —— Common Item Dialog 在 MTA 里也能弹，
                // 所以照旧往下走，失败了自会退回 Swing。
                logInfo("FolderDialog", "本线程已是别的 COM 套间模型（MTA），照样试着开原生目录对话框")
                false
            }
            else -> error("CoInitializeEx 失败 hr=0x${Integer.toHexString(hr)}")
        }
    }

    private fun createDialog(ole32: Ole32): Pointer? {
        val out = PointerByReference()
        val hr = ole32.CoCreateInstance(
            guid(CLSID_FILE_OPEN_DIALOG),
            null,
            CLSCTX_INPROC_SERVER,
            guid(IID_IFILE_OPEN_DIALOG),
            out,
        ).toInt()
        if (hr != S_OK) {
            logWarn("FolderDialog", "CoCreateInstance(FileOpenDialog) 失败 hr=0x${Integer.toHexString(hr)}")
            return null
        }
        return out.value
    }

    /**
     * `GetOptions` → 或上我们要的几位 → `SetOptions`。
     *
     * 为什么要先 Get 再 Set：`SetOptions` 是**整体覆盖**，直接写一个值会把 Shell 自己
     * 加进去的默认位（比如"路径必须存在"）抹掉。按官方示例的口径来最稳。
     */
    private fun applyOptions(dialog: Pointer) {
        val buffer = Memory(4)
        val current = if (comCall(dialog, VT_GET_OPTIONS, buffer) == S_OK) buffer.getInt(0) else 0
        val options = current or FOS_PICKFOLDERS or FOS_FORCEFILESYSTEM
        comCall(dialog, VT_SET_OPTIONS, options)
    }

    private fun setTitle(dialog: Pointer) {
        comCall(dialog, VT_SET_TITLE, WString(TITLE))
    }

    /**
     * 打开时定位到 [initialDir]。
     *
     * `IFileDialog::SetFolder` 要的是 `IShellItem*`，所以先 `SHCreateItemFromParsingName`
     * 把路径转成一个 —— 这个函数 `jna-platform` 里**没有**声明，自己补一个最小的
     * （`shell32.dll` 的导出，Vista 起就有）。
     *
     * 任何一步失败都只是"这次不定位"，对话框照旧会开（在系统的默认位置）——
     * 初始目录不值得为一个它把功能卡住。
     */
    private fun setInitialFolder(dialog: Pointer, initialDir: String?) {
        val dir = initialDir?.takeIf { it.isNotBlank() && File(it).isDirectory } ?: return
        val shell32 = Shell32Extra.INSTANCE ?: return
        val out = PointerByReference()
        val hr = shell32.SHCreateItemFromParsingName(
            WString(dir),
            null,
            guid(IID_ISHELL_ITEM),
            out,
        ).toInt()
        if (hr != S_OK) {
            logInfo("FolderDialog", "初始目录转 IShellItem 失败 hr=0x${Integer.toHexString(hr)}，用系统默认位置")
            return
        }
        val item = out.value ?: return
        try {
            comCall(dialog, VT_SET_FOLDER, item)
        } finally {
            release(item)
        }
    }

    /** `GetResult` → `IShellItem::GetDisplayName(SIGDN_FILESYSPATH)` → 宽字符串 → 绝对路径。 */
    private fun resultPath(dialog: Pointer): String? {
        val itemOut = Memory(Native.POINTER_SIZE.toLong())
        val hr = comCall(dialog, VT_GET_RESULT, itemOut)
        if (hr != S_OK) {
            logWarn("FolderDialog", "GetResult 失败 hr=0x${Integer.toHexString(hr)}")
            return null
        }
        val item = itemOut.getPointer(0) ?: return null
        try {
            val nameOut = Memory(Native.POINTER_SIZE.toLong())
            val hr2 = comCall(item, VT_ITEM_GET_DISPLAY_NAME, SIGDN_FILESYSPATH, nameOut)
            if (hr2 != S_OK) {
                logWarn("FolderDialog", "IShellItem::GetDisplayName 失败 hr=0x${Integer.toHexString(hr2)}")
                return null
            }
            val text = nameOut.getPointer(0) ?: return null
            // ⚠️ 这段内存是 Shell 用 CoTaskMemAlloc 分配的 —— 必须用 CoTaskMemFree 还，
            // 不能用 JNA 的 Memory（那不是同一套分配器）。
            return try {
                text.getWideString(0)
            } finally {
                Ole32.INSTANCE.CoTaskMemFree(text)
            }
        } finally {
            release(item)
        }
    }

    // ---- 手写 vtable 的三个小工具 ----

    /** 读 `obj[0]`（lpVtbl）→ `vtable[index]`（函数地址）→ 一个可调用的函数指针。 */
    private fun vtableEntry(interfacePointer: Pointer, index: Int): Function {
        val vtable = interfacePointer.getPointer(0L) ?: error("COM 对象的 vtable 为空")
        val address = vtable.getPointer(index.toLong() * Native.POINTER_SIZE.toLong())
            ?: error("COM 方法 #$index 为空")
        // stdcall：COM 就是这个调用约定（32 位下必须说清楚，64 位只有一种约定，JNA 会忽略）
        return Function.getFunction(address, Function.ALT_CONVENTION)
    }

    /**
     * 调 `interfacePointer` 的第 [index] 个方法。
     *
     * ⚠️ **第一个参数是 `this`**：这是裸函数指针调用，没人替我们塞接口指针。
     */
    private fun comCall(interfacePointer: Pointer, index: Int, vararg args: Any?): Int =
        vtableEntry(interfacePointer, index).invokeInt(arrayOf<Any?>(interfacePointer, *args))

    /** `IUnknown::Release`（失败也不抛：已经在收尾路上了，再抛只会盖住真正的原因）。 */
    private fun release(interfacePointer: Pointer) {
        runCatching { comCall(interfacePointer, VT_RELEASE) }
    }

    /** 造一个 GUID，并明确写进本地内存（JNA 传结构体参数是"按引用"，写一次最保险）。 */
    private fun guid(uuid: String): Guid.GUID = Guid.GUID(uuid).apply { write() }

    /** 主窗口的原生句柄；拿不到就返回 null（= 无主对话框）。 */
    private fun hwndOf(owner: Window?): Pointer? =
        owner?.let { runCatching { Native.getWindowPointer(it) }.getOrNull() }

    /**
     * `shell32.dll` 里那个 `SHCreateItemFromParsingName`（`jna-platform` 5.14.0 没声明，
     * 补一个最小的；用不到就返回 null，绝不把调用方拖下水）。
     */
    private interface Shell32Extra : StdCallLibrary {
        fun SHCreateItemFromParsingName(
            pszPath: WString,
            pbc: Pointer?,
            riid: Guid.GUID,
            ppv: PointerByReference,
        ): WinNT.HRESULT

        companion object {
            val INSTANCE: Shell32Extra? by lazy {
                runCatching {
                    Native.load("shell32", Shell32Extra::class.java, W32APIOptions.DEFAULT_OPTIONS)
                }.getOrNull()
            }
        }
    }
}

/**
 * 是不是 Windows。
 *
 * 用来做"原生对话框这条路要不要试"以及"要不要装 AWT 的扩展名过滤器"的判断
 * （后者见 `DesktopUiHost.fileLauncher` 里的说明）。
 */
internal fun isWindowsPlatform(): Boolean = runCatching {
    System.getProperty("os.name")?.lowercase()?.contains("windows") == true
}.getOrDefault(false)
