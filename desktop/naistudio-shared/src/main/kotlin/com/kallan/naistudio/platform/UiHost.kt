package com.kallan.naistudio.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 一个"发起选择、选完回调"的把手。
 *
 * 形状**刻意贴着 Android 的 `ActivityResultLauncher` 写**：共用页面从手机工程搬过来时，
 * `launcher.launch(x)` 这一行不用改写，只是 `x` 的含义交给实现方解释。
 */
interface RefLauncher {
    /**
     * @param input 随场景解释：**新建文件**时是建议文件名（手机 `CreateDocument` 就把名字
     *   放在 `launch` 里），选图/选目录时忽略。
     */
    fun launch(input: String? = null)
}

/**
 * **宿主能力**：那些"必须由系统出面"的动作。
 *
 * ## 为什么单独抽出来、还要走 CompositionLocal
 *
 * 手机上是 `ActivityResultContracts`（相册/文件/目录选择器）+ `Toast` + `Intent(ACTION_VIEW)`，
 * 电脑上是 AWT/Swing 的对话框 + 自绘浮层 + 浏览器 —— 这是**两种完全不同的 UI 语言**，
 * 共用页面只该"发起动作"，不该知道是谁在实现。
 *
 * 于是：接口在共用树里（`screens/` 直接调），实现各在各的源集
 * （手机 `services/AndroidUiHost.kt`、电脑 `desktop/platform/DesktopUiHost.kt`），
 * 两个入口 `provide` 一次。
 *
 * ## 为什么是 `@Composable` 的方法
 *
 * 选完的回调必须**跟着重组走**（用户点的那一下之后才回来），而 Compose 里"注册一个
 * 跨组合生命周期的 launcher"本身就是 `@Composable`（手机 `rememberLauncherForActivityResult`）。
 * 所以这里让接口方法带 `@Composable`，实现方各自用自己那套 remember。
 */
interface UiHost {
    /** 选一张图（手机的相册选图 / 电脑的文件对话框）。回调给的是 ref 字符串，取消则给 null。 */
    @Composable
    fun rememberImagePicker(onPicked: (String?) -> Unit): RefLauncher

    /** 选任意文件（可给 MIME 过滤；手机是 `OpenDocument`，电脑按扩展名过滤）。 */
    @Composable
    fun rememberFilePicker(mimeTypes: List<String>, onPicked: (String?) -> Unit): RefLauncher

    /** 新建文件（给建议文件名与 MIME；手机 `CreateDocument`，电脑"另存为"对话框）。 */
    @Composable
    fun rememberFileCreator(mimeType: String, onPicked: (String?) -> Unit): RefLauncher

    /** 选一个目录（手机 SAF 的 `OpenDocumentTree`，电脑选文件夹）。 */
    @Composable
    fun rememberFolderPicker(onPicked: (String?) -> Unit): RefLauncher

    /**
     * 轻提示（手机 `Toast`，电脑一条自动消失的浮层）。
     *
     * @param long 停留久一点。手机上就是 `Toast.LENGTH_LONG`（**失败**类消息用）；
     *   电脑上把浮层时间从 2 秒提到 3.5 秒 —— 语义一致，别让"错误一闪而过"。
     */
    fun toast(message: String, long: Boolean = false)

    /** 用系统浏览器/应用打开一个链接（设置页的"开源许可/项目地址"用）。 */
    fun openUrl(url: String)

    /**
     * **铺满整个窗口**的对话框属性（全屏图片查看器用）。
     *
     * 为什么要走平台层：两端的 [androidx.compose.ui.window.DialogProperties] **参数不一样** ——
     * 手机上要 `decorFitsSystemWindows = false`（内容顶到状态栏底下，黑底铺满整屏），
     * 桌面那份**没有这个参数**（写了就编不过，实测）。
     */
    fun fullscreenDialogProperties(): androidx.compose.ui.window.DialogProperties

    /**
     * 接管**系统返回**。
     *
     * 手机上是返回键/返回手势（`androidx.activity.compose.BackHandler`）；电脑上没有"返回键"
     * 这个系统概念，实现在那边是空操作（依赖 `ui-backhandler`，鼠标侧键/快捷键要接的话在那一层接）。
     *
     * ⚠️ 用法和原来一致、**优先级也和原来一致**：多个 `backHandler` 同时存在时，
     * **后组合的那个先生效** —— 弹窗 / `ModalBottomSheet` 自己注册的返回处理就是这样
     * 盖住外壳这一层的（见 `StudioShell` 里那段注释）。
     */
    @Composable
    fun backHandler(enabled: Boolean = true, onBack: () -> Unit)

    /**
     * 退出应用。
     *
     * 手机 = 宿主 Activity `finish()`（返回栈清空、真的退出）；
     * 电脑 = 关掉窗口（由入口把 `exitApplication` 挂进来，见 `DesktopUiHost`）。
     */
    fun exitApp()

    /**
     * **鼠标指针**：左右调整的样子（分隔条专用）。返回一段要 `then` 上去的 Modifier。
     *
     * 手机返回空 Modifier（触摸设备没有"指针"这个概念）；电脑返回
     * `pointerHoverIcon(PointerIcon(Cursor.E_RESIZE_CURSOR))` —— 也就是用户截给我看的
     * "两个箭头夹一条竖线"。
     *
     * 为什么要走平台层：`Modifier.pointerHoverIcon` 是共用 API，但**构造**那个指针要 AWT 的
     * `java.awt.Cursor`，只在电脑端拿得到；共用树里直接写会编不过。
     *
     * 为什么不是 `fun Modifier.horizontalResizeCursor()`：成员**扩展**函数有两个接收者，
     * 调用点得写成 `with(uiHost) { Modifier.… }` 才行，太别扭；返回 Modifier 最直白。
     */
    fun horizontalResizeCursor(): Modifier

    /**
     * **鼠标指针**：画布平移用的"手"（用户 2026-09-16 指定要 SAI 那两只手）。
     *
     * @param grabbing true = **正在拖动**（握手）；false = **可以拖动**（张手）。
     *
     * 手机返回空 Modifier（触摸设备没有指针）。电脑上用 SAI（`C:\SAI Ver.2 2021`）
     * 资源里导出的两张 32×32 位图做**自定义光标**；万一加载失败就退回系统那两种
     * 近似的手型（不为了一个指针把功能卡住）。
     */
    fun panCursor(grabbing: Boolean): Modifier
}

/**
 * 宿主能力的入口。**默认值是 error**（和 `LocalImageIo` / `LocalPlatform` 一个口径）：
 * 漏 provide 的话，页面一组合就抛，比"点了没反应"好查得多。
 */
val LocalUiHost = staticCompositionLocalOf<UiHost> {
    error("LocalUiHost 没提供：入口要 CompositionLocalProvider(LocalUiHost provides …)")
}
