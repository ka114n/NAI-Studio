package com.kallan.naistudio.desktop.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kallan.naistudio.platform.KeyValueStore
import com.kallan.naistudio.platform.LocalPlatform
import com.kallan.naistudio.platform.RefLauncher
import com.kallan.naistudio.platform.UiHost
import java.awt.Cursor
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.io.File
import javax.swing.JFileChooser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * **电脑端的宿主能力**（[UiHost] 的桌面实现）。
 *
 * 手机那套（ActivityResult / Toast / Intent）在桌面上没有对应物，所以这里是**另一套**：
 *
 * · 选图 / 选文件 / 另存为 → AWT [FileDialog]（Windows 上就是系统原生那一套：
 *   `GetOpenFileName` / `GetSaveFileName`）；
 * · 选目录 → **Windows 原生的文件夹选择器**（Shell 的 `IFileOpenDialog` + `FOS_PICKFOLDERS`，
 *   见 [DesktopFolderDialog]）；**这条路走不通才退回** Swing [JFileChooser]
 *   （AWT 的 `FileDialog` 在 Windows 上只能选文件、选不了文件夹，所以退路只能是它）；
 * · 轻提示 → [DesktopToasts] 自绘浮层（桌面没有 Toast，见那里的说明）；
 * · 打开链接 → `Desktop.browse`（系统默认浏览器）。
 *
 * ## 对话框是**阻塞**的，这是故意的
 *
 * `FileDialog.isVisible = true`（选目录那条是 `IFileOpenDialog::Show`）会阻塞到用户选完为止。
 * 它跑在 AWT 事件线程上，而模态对话框本来就会吃掉输入（Swing/Compose Desktop 的标准做法），
 * 所以"界面在对话框开着时不动"是预期行为，不是卡死。
 *
 * ## owner 与"上次用过的目录"（用户 2026-09-24）
 *
 * 三条文件对话框都做两件事：
 *  1. **把主窗口当 owner 传进去** —— 不传的话对话框不属于本窗口，可能跑到主窗口后面，
 *     也不和主窗口互斥（用户点得到主窗口、界面状态就乱了）；
 *  2. **初始目录 = 上次用过的那个**（键 `dialog.lastDir`，存在 `platform.kv` 里）。
 *
 * 回调统一用 [rememberUpdatedState] 取最新那份 —— 和手机端同一个理由。
 */
class DesktopUiHost : UiHost {

    @Composable
    override fun rememberImagePicker(onPicked: (String?) -> Unit): RefLauncher =
        fileLauncher(onPicked, title = "选择图片", extensions = IMAGE_EXTENSIONS, save = false)

    @Composable
    override fun rememberFilePicker(
        mimeTypes: List<String>,
        onPicked: (String?) -> Unit,
    ): RefLauncher {
        // MIME 过滤在桌面上没有意义（对话框按扩展名认），只做一次粗略映射
        val extensions = remember(mimeTypes) { extensionsOf(mimeTypes) }
        return fileLauncher(onPicked, title = "选择文件", extensions = extensions, save = false)
    }

    @Composable
    override fun rememberFileCreator(mimeType: String, onPicked: (String?) -> Unit): RefLauncher {
        val extensions = remember(mimeType) { extensionsOf(listOf(mimeType)) }
        return fileLauncher(onPicked, title = "另存为", extensions = extensions, save = true)
    }

    /**
     * **选目录**（设置页那个"自定义保存目录"）。
     *
     * 用户 2026-09-24：「选择文件的框能不能用 win 的文件管理器? 这个自带的不太好用」——
     * 原来这里用的是 `JFileChooser`：那是 **Java/Swing 自己画的**界面，和资源管理器长得完全不同。
     * 现在改成 **Windows 原生的文件夹选择器**（Shell 的 `IFileOpenDialog`，见 [DesktopFolderDialog]）。
     *
     * 三条出路，按顺序：
     *  1. 原生对话框（Windows + JNA 在位 + COM 建得出来）—— 正常情况走这条；
     *  2. 原生**建不出来**（非 Windows / 缺 JNA / COM 失败）→ 退回 [swingFolderPicker]
     *     （和以前那个 `JFileChooser` 同一份行为，只是补上了 owner 与"上次用过的目录"）；
     *  3. 用户**取消** → 回调 `null`（与手机 SAF / 原来的实现同一个契约，见 `UiHost` 的说明）。
     */
    @Composable
    override fun rememberFolderPicker(onPicked: (String?) -> Unit): RefLauncher {
        val current = rememberUpdatedState(onPicked)
        // ⚠️ 用**入口那一份** `platform.kv`（不是自己 new 一个同文件的 store）：
        // `FileKeyValueStore` 是"整份 JSON 读进内存、写回时整份覆盖"，
        // 另开一个实例写同一个 `prefs.json` 会把 App 后面写的设置覆盖掉。
        val kv = LocalPlatform.current.kv
        return remember(kv) {
            object : RefLauncher {
                override fun launch(input: String?) {
                    val owner = desktopOwnerFrame()
                    val startedIn = lastDialogDir(kv)
                    // 先把主窗口提到前面（对话框挂在它身上，见类注释"owner 与上次用过的目录"）
                    runCatching { owner?.toFront() }
                    when (val result = DesktopFolderDialog.pick(owner, startedIn)) {
                        is FolderPick.Picked -> {
                            rememberDialogDir(kv, File(result.path))
                            current.value(result.path)
                        }
                        // 取消 = 只把 null 交回去（**不再弹备用的那个框**：
                        // 用户刚说了"不要"，再糊他一个更丑的对话框是最差的体验）
                        FolderPick.Cancelled -> current.value(null)
                        // 原生这条路今天走不通 → 退回 Swing 那个选择器（= 以前的行为）
                        FolderPick.Unavailable -> current.value(swingFolderPicker(owner, startedIn, kv))
                    }
                }
            }
        }
    }

    /**
     * 退路：Swing 的 [JFileChooser]（原来的实现）。
     *
     * 相对原来那版做了两处最小改进，都只是为了"跟上面那条原生的一样好用"：
     *  · 把主窗口当 owner 传（原来传的是 `null`）→ 对话框属于本窗口、不会跑到后面；
     *  · 初始目录 = 上次用过的目录。
     *
     * 返回选中的绝对路径；取消返回 null。
     */
    private fun swingFolderPicker(owner: Frame?, initialDir: String?, kv: KeyValueStore?): String? {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "选择保存目录"
            isMultiSelectionEnabled = false
            if (initialDir != null) currentDirectory = File(initialDir)
        }
        val result = chooser.showOpenDialog(owner)
        val picked = if (result == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.absolutePath
        } else {
            null
        }
        if (picked != null) rememberDialogDir(kv, File(picked))
        return picked?.takeIf { it.isNotBlank() }
    }

    override fun toast(message: String, long: Boolean) {
        DesktopToasts.show(message, long)
    }

    override fun openUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(java.net.URI(url))
            }
        }
    }

    /**
     * 全屏查看器的对话框：桌面这份 `DialogProperties` **没有** `decorFitsSystemWindows`
     * （那是 Android 的窗口概念），只要"别用平台默认宽度"就够了。
     */
    override fun fullscreenDialogProperties(): DialogProperties =
        DialogProperties(usePlatformDefaultWidth = false)

    /**
     * 系统返回。**电脑上没有"返回键"这个系统概念**，所以接的是 CMP 的多平台
     * `ui-backhandler`（鼠标侧键 / 将来的快捷键会走这里）；没有事件源时它就是"注册了也没人调"，
     * 页面逻辑（抽屉先关、图库退出多选、再按一次退出）照旧挂着，不会有副作用。
     */
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Composable
    override fun backHandler(enabled: Boolean, onBack: () -> Unit) {
        androidx.compose.ui.backhandler.BackHandler(enabled = enabled, onBack = onBack)
    }

    /** 退出 = 关窗口：由入口把 `exitApplication` 挂进 [DesktopExit]。 */
    override fun exitApp() {
        DesktopExit.request()
    }

    /**
     * 分隔条上的**左右调整**光标（用户 2026-09-16 给的样式：两个箭头夹一条竖线）。
     *
     * `PointerIcon(java.awt.Cursor)` 是桌面端才有的构造；共用树里只声明
     * [UiHost.horizontalResizeCursor]。
     */
    override fun horizontalResizeCursor(): Modifier =
        Modifier.pointerHoverIcon(
            PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)),
        )

    /**
     * 画布平移用的那两只手（用户 2026-09-16 指定："可以拖动时张手，拖动时握手"）。
     *
     * 位图是从 **SAI**（`C:\SAI Ver.2 2021\sai2.exe` 的光标资源里导出来的，
     * `#11 id318` 张手 / `#12 id319` 握手，32×32、热点 11,9）——
     * 见 `src/main/resources/cursor/{grab,grabbing}.png`。
     * 用 AWT 的 `createCustomCursor` 装成自定义光标：**热点必须给对**，
     * 否则手感是"手心和指针错开 10 像素"。
     *
     * ⚠️ 光标是**一次建好缓存起来**的（`createCustomCursor` 每次调用都要过一遍
     * Toolkit，别放进每帧都跑的 `pointerHoverIcon` 参数里）；
     * 万一资源缺失/建不出来，退回系统那两种近似手型 —— 不能为了一只指针把功能卡住。
     */
    override fun panCursor(grabbing: Boolean): Modifier =
        Modifier.pointerHoverIcon(PointerIcon(HandCursors.of(grabbing)))

    /**
     * **选文件 / 另存为**（选图走的是同一份）：AWT [FileDialog]，
     * Windows 上它就是系统原生对话框（`GetOpenFileName` / `GetSaveFileName`），不需要换。
     *
     * 这里只把三个常见的坑补上（用户 2026-09-24 的复查）：
     *  1. **owner**：原来传的是 `null`（`FileDialog(null as Frame?, …)`）—— 那样对话框
     *     不属于本窗口，可能跑到主窗口后面、也不和主窗口互斥。现在把主窗口传进去；
     *  2. **初始目录**：记住上次用过的那个（`dialog.lastDir`），下次直接从那儿开；
     *  3. **置前**：开框之前把主窗口 `toFront()`（对话框是挂在它身上的，
     *     主窗口在前面时对话框才不会被别的窗口压住）。
     *
     * `save = true` 时是"保存"模式，建议文件名走 [RefLauncher.launch] 的 `input`。
     */
    @Composable
    private fun fileLauncher(
        onPicked: (String?) -> Unit,
        title: String,
        extensions: List<String>,
        save: Boolean,
    ): RefLauncher {
        val current = rememberUpdatedState(onPicked)
        val exts = remember(extensions) { extensions }
        val kv = LocalPlatform.current.kv
        return remember(title, exts, save, kv) {
            object : RefLauncher {
                override fun launch(input: String?) {
                    val owner = desktopOwnerFrame()
                    val startedIn = lastDialogDir(kv)
                    runCatching { owner?.toFront() }
                    val dialog = FileDialog(
                        owner,
                        title,
                        if (save) FileDialog.SAVE else FileDialog.LOAD,
                    )
                    dialog.isMultipleMode = false
                    // 初始目录 = 上次用过的那个（不存在就不设，交给系统的默认位置）
                    if (startedIn != null) dialog.directory = startedIn
                    // 「另存为」的建议文件名；选文件/选图时 input 一般是 null
                    if (!input.isNullOrBlank()) dialog.file = input
                    if (exts.isNotEmpty() && !isWindowsPlatform()) {
                        // ⚠️ **Windows 上刻意不装这个过滤器**（用户 2026-09-24 的复查点）。
                        //
                        // 三条实证/事实：
                        //  1. AWT 的 Windows 实现对文件列表**不做**扩展名过滤 ——
                        //     它给原生对话框塞的"文件类型"只有一条「所有文件 (*.*)」
                        //     （`WFileDialogPeer` 里那个静态的 `setFilterString`），
                        //     所以用户看到的永远是全部文件；真正会用到 `FilenameFilter` 的
                        //     只有 `WFileDialogPeer.checkFilenameFilter`，那是**选完之后**的校验；
                        //  2. 于是"按扩展名过滤"在 Windows 上**既没起到过滤作用**，
                        //     又可能把用户选中的文件判为不合格 —— 典型的"看不到提示又选不中"✗；
                        //  3. 而"所有文件"这个选项**始终都在**（第 1 条），不存在被搞丢的问题。
                        //
                        // 所以在 Windows 上干脆不装：全部文件都能选（想选什么就选什么），
                        // 选完由调用方按自己的规则处理（导入失败会 toast，不会静默）。
                        // 别的平台（Motif/GTK 那套）这个过滤器是真生效的，照旧留着。
                        dialog.filenameFilter = java.io.FilenameFilter { _, name ->
                            exts.any { name.lowercase().endsWith(it) }
                        }
                    }
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val name = dialog.file
                    val picked = if (dir != null && name != null) File(dir, name).absolutePath else null
                    // 成功才记目录（取消时 `FileDialog` 会把 directory/file 清成 null，
                    // 不被这个 if 挡住的话就会记下"瞎猜的目录"）
                    if (picked != null) rememberDialogDir(kv, File(picked))
                    current.value(picked)
                }
            }
        }
    }

    private fun extensionsOf(mimeTypes: List<String>): List<String> {
        val out = mutableListOf<String>()
        mimeTypes.forEach { mime ->
            when {
                // PSD（高级漫画的「导出 PSD」走这条）：`image/vnd.adobe.photoshop` ✓ ——
                // ⚠️ 必须排在下面 `image/` 那条**前面**：不然会被当成普通图片，
                // 非 Windows 平台（过滤器真生效的那几个 ✓）就会把用户建议的 `comic.psd`
                // 判成"不合格的文件名"✗（Windows 上这个过滤器本来也不生效 ✓）
                mime.contains("photoshop") || mime.endsWith("psd") -> out += PSD_EXTENSIONS
                mime.startsWith("image/") -> out += IMAGE_EXTENSIONS
                mime.contains("json") -> out += ".json"
                mime.endsWith("png") -> out += ".png"
                mime.endsWith("jpeg") || mime.endsWith("jpg") -> out += ".jpg"
                // 字体（高级漫画的「导入字体」走这条）：`font/ttf` / `application/x-font-ttf` 之类
                mime.contains("font") || mime.contains("ttf") || mime.contains("otf") ||
                    mime.contains("ttc") || mime.contains("opentype") -> out += FONT_EXTENSIONS
                mime == "*/*" -> Unit
                else -> Unit
            }
        }
        return out.distinct()
    }

    private companion object {
        val IMAGE_EXTENSIONS = listOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp")

        /** PSD（`PsdWriter` 写出来的那种 ✓）。只给「导出 PSD」那条另存为用 ✓。 */
        val PSD_EXTENSIONS = listOf(".psd")

        /**
         * 用户字体库里认的三种格式（与 `FontLibrary.EXTENSIONS` 一致 ✓）。
         *
         * ⚠️ 和图片那条一样：**Windows 上这个过滤器其实不生效**（AWT 的 Windows 实现给原生对话框
         * 只塞了"所有文件"，见 [fileLauncher] 里那段注释）—— 留着是给别的平台用的，
         * 而且真正认不认由 `FontLibrary.install` 说了算（不支持的会 toast，不会静默 ✗）。
         */
        val FONT_EXTENSIONS = listOf(".ttf", ".otf", ".ttc")
    }
}

/**
 * **"上次用过的目录"**存在哪个键上（选图 / 选文件 / 另存为 / 选目录 四条共用）。
 *
 * 存在入口那一份 `platform.kv` 里（= `%APPDATA%\NAI Studio\prefs.json`），
 * 和窗口几何（`window.x` 那几项）同一格抽屉 —— 桌面上这类"上次用哪儿"的东西都放这儿。
 */
private const val KEY_DIALOG_LAST_DIR = "dialog.lastDir"

/**
 * 读"上次用过的目录"。
 *
 * ⚠️ 读到之后必须**确认它现在还是个目录**：用户可能把 U 盘拔了、把那个文件夹改名/删了，
 * 或者那本来就是个网络位置而此刻连不上。那种情况返回 null，让对话框用它自己的
 * 默认位置 —— 而不是抱着一个不存在的路径发呆（甚至卡住）。
 *
 * [kv] 允许为 null（探针/单测里没有平台层时）：那时就是"没有记忆"，不影响选文件这件事。
 */
private fun lastDialogDir(kv: KeyValueStore?): String? {
    val path = runCatching { kv?.getString(KEY_DIALOG_LAST_DIR) }.getOrNull()
    if (path.isNullOrBlank()) return null
    return runCatching { path.takeIf { File(it).isDirectory } }.getOrNull()
}

/**
 * 记下这次用过的目录。
 *
 * [target] 给**文件**（记它的父目录）或**目录**（记它自己）都行 —— 四种对话框的落点不同，
 * 但用户心里那个"我上次在哪儿"是同一件事。
 *
 * ⚠️ 写盘失败（磁盘满 / 权限）绝不能把"选文件"整个动作带崩：这只是个便利功能，
 * 所以整段吞掉异常。
 */
private fun rememberDialogDir(kv: KeyValueStore?, target: File?) {
    val dir = when {
        target == null -> return
        target.isDirectory -> target
        else -> target.parentFile ?: return
    }
    val path = runCatching { dir.absolutePath }.getOrNull() ?: return
    runCatching { kv?.edit()?.putString(KEY_DIALOG_LAST_DIR, path)?.apply() }
}

/**
 * **主窗口**（给对话框当 owner 用）。
 *
 * 为什么要从 AWT 这张全局表里捞、而不是把窗口传进来：`UiHost` 那几个 `remember*`
 * 的**签名是手机电脑共用的**（用户明确要求不能动接口），方法里拿不到入口作用域里那个
 * `window`，所以只能在桌面这一侧自己找。
 *
 * 先看"当前活动窗口"—— 正常情况下点菜单/按钮时它就是主窗口；活动的是 Compose 的
 * 弹窗（一个 `Dialog`）之类时，退回"进程里第一个可见的 `Frame`"（= 主窗口）。
 *
 * 拿不到就返回 null：对话框退回"无主"形态 —— 和以前一样能用，只是没有父子关系。
 */
private fun desktopOwnerFrame(): Frame? = runCatching {
    val active = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    if (active is Frame && active.isVisible) {
        active
    } else {
        Window.getWindows().firstOrNull { it is Frame && it.isVisible } as? Frame
    }
}.getOrNull()

/**
 * 桌面端的**轻提示浮层**（[UiHost.toast] 的落地）。
 *
 * 桌面没有 Toast，而"复制成功 / 已保存"这类反馈又不能没有。做法：把消息丢进一个
 * 全局列表，界面最外层用一个 `ToastOverlay()` 渲染，到点自动消失。
 *
 * 用全局状态而不是 CompositionLocal：`toast()` 是从**点击回调**里调的（不在组合里），
 * 拿不到 CompositionLocal。全局一份对单窗口应用足够。
 */
object DesktopToasts {
    const val DURATION_MILLIS = 2000L

    /** `long = true`（失败类消息）多停一会儿，对应手机上的 `Toast.LENGTH_LONG`。 */
    const val DURATION_LONG_MILLIS = 3500L

    /** 待显示的消息（界面最外层的 `ToastOverlay()` 读它）。 */
    val messages: SnapshotStateList<String> = mutableStateListOf()

    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main) }

    fun show(message: String, long: Boolean = false) {
        if (message.isBlank()) return
        messages.add(message)
        scope.launch {
            delay(if (long) DURATION_LONG_MILLIS else DURATION_MILLIS)
            messages.remove(message)
        }
    }
}

/**
 * 桌面端的"退出应用"出口。
 *
 * 为什么要有这个中转：共用页面的"再按一次退出"调的是 [UiHost.exitApp]，
 * 而真正能关窗口的 `exitApplication` 是 **Compose 桌面入口作用域里的函数**
 *（`ApplicationScope.exitApplication`），平台实现那个类拿不到它。
 * 所以入口启动时挂一次：`DesktopExit.handler = ::exitApplication`。
 *
 * 没人挂的时候（单元测试、探针）退化成 `exitProcess(0)`，至少不会"点了没反应"。
 */
object DesktopExit {
    var handler: (() -> Unit)? = null

    fun request() {
        val action = handler
        if (action != null) action() else kotlin.system.exitProcess(0)
    }
}

/**
 * 把 [DesktopToasts] 里的消息画在窗口底部中间。
 *
 * 挂在界面**最外层**（和手机端 `Toast` 一样"浮在所有东西之上"），
 * 只读列表、自己不做淡入淡出 —— 2000ms 后 [DesktopToasts] 会把消息摘掉。
 */
@Composable
fun DesktopToastOverlay() {
    val items = DesktopToasts.messages
    if (items.isEmpty()) return
    Box(
        modifier = Modifier.fillMaxSize().padding(bottom = 72.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        items.takeLast(3).forEach { message ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 6.dp,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
