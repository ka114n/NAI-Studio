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

/** Native dialog result: cancellation never launches another picker. */
internal sealed class FolderPick {
    /** 用户选了某个目录（绝对路径）。 */
    class Picked(val path: String) : FolderPick()

    /** 用户取消（点"取消"、按 Esc、右上角关掉）。 */
    object Cancelled : FolderPick()

    /** 原生对话框用不了（非 Windows / JNA 缺件 / COM 建不出来）→ Windows 调用方提示失败。 */
    object Unavailable : FolderPick()
}

/** Windows Common Item Dialog for open, save and folder selection, on its own COM STA thread. */
internal object DesktopFolderDialog {

    // ---- CLSID / IID（取自 shobjidl.h / shlguid.h，改了就是另一个对象了）----

    /** `CLSID_FileOpenDialog`：Common Item Dialog 的那个 CoClass。 */
    private const val CLSID_FILE_SAVE_DIALOG = "C0B4E2F3-BA21-4773-8DBA-335EC946EB8B"
    private const val IID_IFILE_SAVE_DIALOG = "84bccd23-5fde-4cdb-aea4-af64b83d78ab"

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
    private const val VT_SET_FILE_TYPES = 4
    private const val VT_SET_FILE_NAME = 15
    private const val VT_SET_DEFAULT_EXTENSION = 22
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

    fun pick(owner: Window?, initialDir: String?): FolderPick =
        runSta { pickNative(owner, initialDir, WindowsDialogRequest(folder = true, title = "选择保存目录")) }

    fun pickFile(owner: Window?, initialDir: String?, request: WindowsDialogRequest): FolderPick =
        runSta { pickNative(owner, initialDir, request) }

    private fun runSta(action: () -> FolderPick): FolderPick {
        if (!isWindowsPlatform()) return FolderPick.Unavailable
        return runCatching {
            val task = java.util.concurrent.FutureTask(action)
            Thread(task, "NAI-Windows-FileDialog").apply { isDaemon = true; start() }
            task.get()
        }.getOrElse {
            logWarn("FileDialog", "Windows 原生文件对话框不可用：$it")
            FolderPick.Unavailable
        }
    }

    private fun pickNative(owner: Window?, initialDir: String?, request: WindowsDialogRequest): FolderPick {
        val ole32 = Ole32.INSTANCE
        val uninitNeeded = comInit(ole32)
        try {
            val dialog = createDialog(ole32, request.save) ?: return FolderPick.Unavailable
            try {
                applyOptions(dialog, request)
                requireSuccess(comCall(dialog, VT_SET_TITLE, WString(request.title)))
                setInitialFolder(dialog, initialDir)
                val filterMemory = configureFiles(dialog, request)

                val ownerHwnd = hwndOf(owner)
                // 主窗口先提到前面：对话框是挂在它身上的，主窗口在前面时才不会有
                // "弹出来的框在别的窗口底下"的观感（拿不到 HWND 就跳过，不值得为此报错）。
                if (ownerHwnd != null) {
                    runCatching { User32.INSTANCE.SetForegroundWindow(WinDef.HWND(ownerHwnd)) }
                }

                // ⚠️ Show 会阻塞到用户选完（这是刻意保持的行为，见类注释）
                val hr = try { comCall(dialog, VT_SHOW, ownerHwnd) }
                finally { java.lang.ref.Reference.reachabilityFence(filterMemory) }
                return when {
                    hr == HRESULT_CANCELLED || hr == E_ABORT -> FolderPick.Cancelled
                    hr == S_OK -> {
                        val path = resultPath(dialog)
                        if (path.isNullOrBlank()) FolderPick.Cancelled else FolderPick.Picked(path)
                    }
                    else -> {
                        logWarn(
                            "FolderDialog",
                            "IFileOpenDialog::Show 失败 hr=0x${Integer.toHexString(hr)}",
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
            RPC_E_CHANGED_MODE -> error("Windows 文件对话框需要 STA 线程")
            else -> error("CoInitializeEx 失败 hr=0x${Integer.toHexString(hr)}")
        }
    }

    private fun createDialog(ole32: Ole32, save: Boolean): Pointer? {
        val out = PointerByReference()
        val hr = ole32.CoCreateInstance(
            guid(if (save) CLSID_FILE_SAVE_DIALOG else CLSID_FILE_OPEN_DIALOG),
            null,
            CLSCTX_INPROC_SERVER,
            guid(if (save) IID_IFILE_SAVE_DIALOG else IID_IFILE_OPEN_DIALOG),
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
    private fun requireSuccess(hr: Int) {
        check(hr >= 0) { "Windows dialog HRESULT=0x${Integer.toHexString(hr)}" }
    }

    private fun applyOptions(dialog: Pointer, request: WindowsDialogRequest) {
        val buffer = Memory(4)
        requireSuccess(comCall(dialog, VT_GET_OPTIONS, buffer))
        // Single selection, local filesystem paths, existing parent; preserve Shell defaults.
        val current = buffer.getInt(0) and 0x200.inv()
        val options = current or FOS_FORCEFILESYSTEM or 0x800 or
            if (request.folder) FOS_PICKFOLDERS else if (request.save) 0x2 else 0x1000
        requireSuccess(comCall(dialog, VT_SET_OPTIONS, options))
    }

    private fun configureFiles(dialog: Pointer, request: WindowsDialogRequest): List<Memory> {
        if (request.folder) return emptyList()
        val filters = request.filters()
        val keep = mutableListOf<Memory>()
        val specs = Memory(filters.size.toLong() * Native.POINTER_SIZE * 2)
        keep += specs
        filters.forEachIndexed { index, (name, pattern) ->
            listOf(name, pattern).forEachIndexed { field, text ->
                val string = Memory((text.length + 1L) * Native.WCHAR_SIZE)
                string.setWideString(0, text)
                keep += string
                specs.setPointer((index * 2L + field) * Native.POINTER_SIZE, string)
            }
        }
        requireSuccess(comCall(dialog, VT_SET_FILE_TYPES, filters.size, specs))
        request.suggestedName?.takeIf { it.isNotBlank() }?.let {
            requireSuccess(comCall(dialog, VT_SET_FILE_NAME, WString(File(it).name)))
        }
        request.defaultExtension()?.let {
            requireSuccess(comCall(dialog, VT_SET_DEFAULT_EXTENSION, WString(it)))
        }
        return keep
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
