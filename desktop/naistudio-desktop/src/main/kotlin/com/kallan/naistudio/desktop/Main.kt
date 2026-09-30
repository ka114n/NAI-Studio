package com.kallan.naistudio.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
// ⚠️ 是**扩展属性**（`val KeyEvent.utf16CodePoint`），必须显式 import ——
// 它 = AWT `KeyEvent.getKeyChar()`，是"打字事件（KEY_TYPED）"里那个字符（见 `onPreviewKeyEvent` ③）。
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.kallan.naistudio.desktop.platform.appDir
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.platform.logInfo
import com.kallan.naistudio.platform.logWarn
import kotlin.math.roundToInt
import kotlin.system.exitProcess
import kotlinx.coroutines.flow.debounce
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import com.kallan.naistudio.desktop.platform.DesktopBackdrop
import com.kallan.naistudio.desktop.platform.DesktopExit
import com.kallan.naistudio.desktop.platform.DesktopTitleBarHook
import com.kallan.naistudio.desktop.platform.DesktopToastOverlay
import com.kallan.naistudio.desktop.platform.DesktopUiHost
import com.kallan.naistudio.desktop.platform.WebBrushWindow
import androidx.compose.ui.awt.SwingPanel
import com.kallan.naistudio.desktop.platform.captureWindowFrame
import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.platform.LocalImageIo
import com.kallan.naistudio.platform.LocalPlatform
import com.kallan.naistudio.platform.LocalUiHost
import com.kallan.naistudio.screens.LoginScreen
import com.kallan.naistudio.services.CustomBackground
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.AppBackdrop
import com.kallan.naistudio.ui.LocalAppBackdrop
import com.kallan.naistudio.ui.LocalThemeSwitch
import com.kallan.naistudio.ui.NaiStudioTheme
import com.kallan.naistudio.ui.naiColorScheme
import com.kallan.naistudio.ui.PageShortcuts
import com.kallan.naistudio.ui.LocalPageShortcuts
import com.kallan.naistudio.ui.LocalShellCommands
import com.kallan.naistudio.ui.LocalPanelBorderColor
import com.kallan.naistudio.ui.LocalPanelBackgroundColor
import com.kallan.naistudio.ui.LocalPanelTitleBarColor
import com.kallan.naistudio.ui.LocalNaiSkin
import com.kallan.naistudio.ui.NaiSkin
import com.kallan.naistudio.ui.adaptForMode
import com.kallan.naistudio.ui.parseHexColor
import com.kallan.naistudio.ui.referenceBackdrop
// 「视图 → 重置图片位置」的信箱（外壳发号、生成页画布收号，见 `CanvasViewCommands`）
import com.kallan.naistudio.ui.CanvasViewCommands
import com.kallan.naistudio.ui.LocalCanvasViewCommands
import com.kallan.naistudio.ui.LocalShortcutBindings
import com.kallan.naistudio.ui.LocalWindowChrome
import com.kallan.naistudio.ui.ShellCommands
import com.kallan.naistudio.ui.ShortcutBindings
import com.kallan.naistudio.ui.WindowChrome
import com.kallan.naistudio.ui.EditorShortcuts
import com.kallan.naistudio.ui.LocalEditorShortcuts
import com.kallan.naistudio.ui.ProvideWindowSize
import com.kallan.naistudio.ui.SplashOverlay
import com.kallan.naistudio.ui.SplashOverlayFadeMillis
import com.kallan.naistudio.ui.SplashOverlayMaxMillis
import com.kallan.naistudio.ui.SplashOverlayMinMillis
import com.kallan.naistudio.ui.StudioShell
import com.kallan.naistudio.ui.ThemeWipeOverlay
import com.kallan.naistudio.ui.paletteOverridesOf
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import java.awt.Frame
import java.awt.MouseInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow

/**
 * **电脑版入口**。
 *
 * 和手机入口（`MainActivity`）**结构一一对应**，只是把平台相关的那几件事换成了桌面实现：
 *
 * | 手机 | 电脑 |
 * | --- | --- |
 * | `androidPlatform(applicationContext)` | `desktopPlatform()`（`%APPDATA%\NAI Studio`） |
 * | `AndroidUiHost`（ActivityResult / Toast / Intent） | [DesktopUiHost]（AWT/Swing 对话框 / 浮层 / 浏览器） |
 * | `LocalConfiguration.screenWidthDp` | `ProvideWindowSize` 量根约束 |
 * | `PixelCopy` 抓屏做主题扩散 | `Robot` 抓客户区（[captureWindowFrame]）做同一套扩散 |
 *
 * ⚠️ **界面本体一行都不是为电脑重写的**：`StudioShell` + 13 个页面全部来自
 * `naistudio-shared`，和手机编的是同一份源码（`docs/25` §12 有清单与验证记录）。
 *
 * 关于主题切换动画：手机是"抓当前窗口那一帧 → 从按钮圆心圆形铺开"（`WindowCapture` 走
 * `PixelCopy`），电脑这边 2026-09-16 起**用同一套** —— 遮罩本体（`ThemeWipeOverlay`）就在
 * 共用树里，平台差异只剩"怎么抓那一帧"（[captureWindowFrame] 走 AWT 的 `Robot`）。
 * 抓不到就退回直接切（见 `switchTheme`）。
 */
fun main() {
    // ---- 画笔引擎的 A / B 方案开关（2026-09-21 ✓）----
    // 用户口径：「**这个版本在桌面备份一个文件夹，然后 ab 各做一个**」✓
    //  · 不设 / false ⇒ **A 方案**（按帧合成 ✓，默认 ✓ —— 保"连贯笔画"✓）
    //  · `-Dnai.brush.directSurface=true` ⇒ **B 方案**（笔画期间直接落表面 ✓ —— 落笔即见墨 ✓，
    //    代价是"一个个圆"会回来 ✗，见 `BrushSpec.directSurfaceStroke` 的说明 ✓）
    // ⚠️ 必须在 `application { }` **之前**读 ✓：`BrushSpec` 是**静态伴生字段** ✓，
    //    界面 / 引擎随时会读它 ✓ —— 开机先定下来，笔画中途不许再变 ✗。
    com.kallan.naistudio.models.BrushSpec.readDirectSurfaceFromSystemProperty()
    logInfo(
        "BrushMode",
        "[BrushMode] 笔画合成方案 = " + (
            if (com.kallan.naistudio.models.BrushSpec.directSurfaceStroke) {
                "B（直接落表面：落笔即见墨；代价=逐颗 source-over）"
            } else {
                "A（按帧合成：保连贯笔画）"
            }
            ),
    )
    // ---- 单实例（活 2）----
    // ⚠️ 必须在 `application { }` **之前**：第二个实例连 Compose / AWT 都不该起，更不该开第二个窗口。
    // "拿不到锁" = 已经有一个 NAI Studio 在跑 → 把那个窗口带到前台，然后本进程直接结束
    //（见 `acquireSingleInstanceLock` / `activateExistingInstanceAndExit`）。
    // ⚠️ 别把"锁机制本身坏了"当成"已有实例"：那种情况 `acquireSingleInstanceLock` 返回 true、
    // 照常启动（锁文件写不出来时，宁可多开一个窗口也不能让 App 永远打不开）。
    if (!acquireSingleInstanceLock()) activateExistingInstanceAndExit()
    runDesktopApp()
}

/**
 * 桌面应用本体 —— 就是原来 `main()` 里那个 `application { … }`，**内容一字未动**。
 *
 * 为什么拆出来：原来写的是表达式体 `fun main() = application { … }`，那样没法在
 * `application` 启动**之前**插单实例检查（而检查必须在窗口存在之前）。
 */
private fun runDesktopApp() = application {
    val platform = remember { desktopPlatform() }
    val uiHost = remember { DesktopUiHost() }
    // 页面快捷键的信箱：入口（`Window(onKeyEvent=…)`）与外壳（"当前第几页"）中间的中转。
    // 同一个实例既要被 onKeyEvent 捕获、又要在下面的 provider 里给外壳读（见 `PageShortcuts`）。
    val pageShortcuts = remember { PageShortcuts() }
    // 画布编辑器的键位信箱：同样是"入口收键、界面处理"（b/e/g/s/l/c/r/j、[ ]、Ctrl+Z）。
    // 编辑器只在**自己开着**的时候登记处理函数，所以编辑器没开时这些键照旧是打字。
    val editorShortcuts = remember { EditorShortcuts() }
    // **菜单快捷键的信箱**（`Ctrl+O/S/I/P/D/B/E/Q`）：入口把按键翻成命令 id，
    // 外壳的菜单条那边登记"收到 id 干什么"（那边才有文件选择器与 AppState）。
    val shellCommands = remember { ShellCommands() }
    // **「视图 → 重置图片位置」的信箱**：方向与 `shellCommands` 相反（外壳发号 → 生成页画布收号）。
    // 摆放在画布里（`previewScale` / `previewOffset`），外壳的菜单够不着，所以中间要这一个中转。
    val canvasViewCommands = remember { CanvasViewCommands() }
    // **可改的键位表**（帮助 → 快捷键… 能改；菜单标注与入口派发都读它）
    val shortcuts = remember { ShortcutBindings(platform.kv) }
    // **被快捷键吃掉、还没抬起的那几颗键**（键 → 命中的命令 id）。
    //
    // 记它们有两个用处（都在下面 `onPreviewKeyEvent` 里）：
    //  ① 抬起（KeyUp）也得吃掉：**Compose 的 clickable 是在回车的抬起上直接 `onClick()` 的** ——
    //     按下那一颗被我们吃掉之后，抬起若不挡住，`Ctrl+Enter` 就会"生一次图 + 顺手把聚焦的按钮点一下"。
    //     顺带把"按住不放的连发"挡成一次（连发会一遍遍来 KeyDown）。
    //  ② **AWT 的"打字事件"（`KEY_TYPED`）没有 key**（`keyCode` 是 0 → `Key.Unknown`、`type` 是
    //     `KeyEventType.Unknown`），`actionFor` 认不出它属于哪条绑定 —— 靠这里记的"还按着的那颗键"兜底
    //    （2026-09-26 查的"用快捷键执行却往提示词里打字"，实测与推导见那段注释）。
    val heldShortcutKeys = remember { mutableMapOf<Key, String>() }
    // 窗口标题跟着当前页走（`NAI Studio — 图库`）：任务栏悬停、Alt+Tab 一眼看出在哪一页。
    // 页面标题由外壳广播过来（见 `PageShortcuts.onPageChanged` 的注释）。
    var pageTitle by remember { mutableStateOf("") }
    LaunchedEffect(pageShortcuts) {
        pageShortcuts.onPageChanged { title -> pageTitle = title }
    }

    // **记住上次的窗口大小与位置**（电脑上的基本期待）。
    // 读一次即可：`rememberWindowState` 拿到的就是初值，之后由用户拖动决定。
    val restored = remember(platform) { loadWindowGeometry(platform) }
    val windowState = rememberWindowState(
        size = DpSize(restored.width.dp, restored.height.dp),
        position = WindowPosition(restored.x.dp, restored.y.dp),
    )

    // 拖动/缩放过程中别每帧都写盘：停下来 600ms 再落一次（`snapshotFlow` + `debounce`）。
    // 最小化时窗口会跑到 (-32000, -32000)，**必须跳过** —— 否则下次启动就把这个坐标读回来了。
    LaunchedEffect(windowState) {
        snapshotFlow {
            Triple(
                windowState.size,
                windowState.position,
                windowState.isMinimized,
            )
        }
            .debounce(600)
            .collect { (size, position, minimized) ->
                if (minimized) return@collect
                // ⚠️ **最大化 / 看起来像最大化时也不存**（用户 2026-09-19：「顶部突出的窗口收回」）：
                // 最大化窗口的"尺寸/坐标"是"屏幕 + 四周隐形边框"（实测存下来的是 `-8,-8 / 2576×1408`，
                // 比屏幕还大），下次当成普通窗口恢复出来，窗口就是**顶出屏幕**的样子。
                // 跳过之后盘上留着的仍是用户自己摆的那个大小与位置。
                if (windowState.placement == WindowPlacement.Maximized) return@collect
                platform.kv.edit()
                    .putInt(KEY_WINDOW_WIDTH, size.width.value.roundToInt())
                    .putInt(KEY_WINDOW_HEIGHT, size.height.value.roundToInt())
                    .putInt(KEY_WINDOW_X, position.x.value.roundToInt())
                    .putInt(KEY_WINDOW_Y, position.y.value.roundToInt())
                    .apply()
            }
    }

    // 「再按一次退出」→ 真的关窗口：把入口作用域里的 exitApplication 挂给平台实现。
    // （共用页面只知道 `UiHost.exitApp()`，不知道是谁在关窗口。）
    remember(::exitApplication) {
        DesktopExit.handler = { exitApplication() }
        true
    }

    val state = remember(platform) { AppState(platform) }

    // ---- ㉕ 批：**网页笔刷**（用户 2026-09-20 口径：「直接搬，不修」✓）----
    // 只做一件事：把"开窗"这个动作挂到共用信箱上 ✓（共用面板那颗按钮负责调它，
    // 见 `platform/WebBrush.kt` ✓）；**现有那套 Kotlin 笔刷一个字都没动** ✓。
    // ⚠️ 页尺寸必须**现读**（`open` 那一刻才读 ✓）—— 换页 / 新建页之后它是要变的，
    //    在 `remember` 里算死就会"换了页还是老尺寸" ✗。
    // ---- ㉛ 批：**网页画布摆进「高级漫画」的画布区** ✓ ----
    // 用户口径：「我以后的测试**只在高级漫画模式的画布上**进行，不在网页上进行」✓
    // ⇒ 网页那张 canvas 挂在画布区（左边参数面板 / 右边图层面板都不动 ✓），
    //    不是盖住整个窗口 ✗（上一版就是那样，错了 ✓）。
    var webCanvasSize by remember { mutableStateOf(1024 to 1024) }
    remember(state) {
        com.kallan.naistudio.platform.WebBrush.canvas = { mod ->
            SwingPanel(
                factory = {
                    WebBrushWindow.embeddedPanel(
                        pageWidth = webCanvasSize.first,
                        pageHeight = webCanvasSize.second,
                        layerPng = null,
                        onAdopt = { bytes -> state.adoptWebBrushImage(bytes) },
                    )
                },
                modifier = mod,
            )
        }
        com.kallan.naistudio.platform.WebBrush.showCanvas = true
        com.kallan.naistudio.platform.WebBrush.open = {
            // 画布区现在**只认 canvas** ✓（Kotlin 画布已从画布区删掉 ✗）——
            // 这颗按钮不再切换任何东西，只留一条日志，免得面板上有个"点了没反应"的按钮 ✗。
            logInfo("WebBrush", "[WebBrush] 画布区就是网页 canvas（空白画布）✓")
        }
        logInfo("WebBrush", "[WebBrush] 网页画布已挂到「高级漫画」的画布区 ✓")
        // ★ 用户口径「加载快、无感嵌入」✓ ⇒ **启动时就把 CEF 内核预热**（后台，不建窗口不加载页 ✓）。
        //   预热之后第一次看到画布区是"秒出"，不是点开才开始等 ✗。
        WebBrushWindow.prewarm()
        true
    }

    // 冷启动：读设置/参数/历史/账号，并趁开屏盖着让生成页把抽屉预热一次
    //（与手机入口同一套流程 —— `splashDone` 在 AppState 里，转屏/重开不会重来）。
    var splashVisible by remember { mutableStateOf(!state.splashDone) }
    LaunchedEffect(Unit) {
        val startedAt = System.nanoTime()
        state.load(startWithEmptyCanvas = true)
        state.requestPanelPrewarm()
        runCatching { snapshotFlow { state.panelPrewarmed }.first { it } }
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        if (elapsedMs < SplashOverlayMinMillis) delay(SplashOverlayMinMillis - elapsedMs)
        state.markSplashDone()
        splashVisible = false
    }
    LaunchedEffect(Unit) {
        // 上限兜底：预热万一没做完也得放人进去
        delay(SplashOverlayMaxMillis)
        state.markSplashDone()
        splashVisible = false
    }

    // **空格 + 画布键位的统一入口**（用户 2026-09-22：「去除空格的焦点击功能」）。
    //
    // ## 要解决什么
    //
    // 「点过的按钮一直拿着焦点 → 再按空格又把它激活了一次」（上一轮只给 RunBar 那 4 颗挂了
    // `NoButtonFocus`，提示词栏的垃圾桶 / 撤销 / 历史 / 翻译 / 优化、Tab、滑杆… 全都还会吃空格）。
    // 目标：**空格只剩「按住 = 拖图」**，任何按钮都不该被空格激活。
    //
    // ## 为什么必须在这里（预览阶段）、而不是在下面的 `onKeyEvent`
    //
    // ⚠️ 这条是**实测字节码**得出的（不是推理）：
    //
    //  · `ComposeSceneMediator.onKeyEvent(AWT)` 的顺序是
    //    `onPreviewKeyEvent`（窗口层，我们这里）→ `scene.sendKeyEvent`（**焦点路径**）→
    //    `onKeyEvent`（窗口层，冒泡兜底）；`scene.sendKeyEvent` 返回 true 时窗口层的
    //    `onKeyEvent` **根本不会被调用**；
    //  · 而 `AbstractClickableNode.onKeyEvent`（1.8.2，`ClickableKt.isPress/isClick`）对 Space
    //    的 **KeyDown/KeyUp 都返回 true（消费掉）** ——
    //    所以"聚焦着的按钮"会在焦点路径上先把空格吃掉，放在窗口 `onKeyEvent` 里的判断
    //    **永远收不到那个空格**，也就拦不住它（那一版等于没改）。
    //  · 只有**预览阶段**（本 lambda）跑在焦点路径之前。`CanvasEditor` 早就靠这一点吃空格
    //    （`onCanvasShortcut` 里"返回值恒为 true：吃掉它，免得空格去点聚焦着的按钮"）。
    //
    // ## 为什么只在"没有输入框拿着焦点"时吃
    //
    // 输入框里必须还能打出空格。闸门是 `AppState.textInputFocused`（统一标记）：
    // 所有文本输入框都用 `Modifier.trackTextInputFocus(state, key)` 报焦点（见那个修饰符的注释）。
    //
    //  · 没有输入框拿焦点 → 吃掉空格（KeyDown/KeyUp 都吃），自己把"按住没按住"写进
    //    `AppState.canvasSpacePan` —— 页面里 `shortcutSpaceKeys` 读的就是它，
    //    所以"按住空格 + 左键拖动 = 平移"照旧可用，而且焦点控件再也收不到这个键；
    //  · 有输入框拿焦点 → 原样放行（交给编辑器那套 / 焦点节点），空格就是打字。
    val handleCanvasKeys: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { event ->
        // ⚠️ **Ctrl 的按下 / 抬起只记状态、绝不吃掉** ✓（用户 2026-09-22：「按住 ctrl 才能拖图」）——
        //    Ctrl 还挂在别的组合键上（`Ctrl+Z` / `Ctrl+O` / `Ctrl+Enter`…），吃掉就把它们全弄坏了 ✗。
        //    为什么不读指针事件自带的 `keyboardModifiers`：实测按着 Ctrl 拖也判不出来 ✗（见
        //    `AppState.canvasCtrlDown` 的说明 ✓）—— 那条键盘路是**已经在用**的（空格 + 拖动 ✓）。
        if (event.key == Key.CtrlLeft || event.key == Key.CtrlRight) {
            state.holdCanvasCtrl(event.type == KeyEventType.KeyDown)
        }
        if (event.key == Key.Spacebar && !state.textInputFocused) {
            state.holdCanvasSpace(event.type == KeyEventType.KeyDown)
            true
        } else {
            // ⚠️ 这里原来是 `handleCanvasKeys(event)` —— **自己调自己**（无限递归 → StackOverflowError，
            // 而且只要按的不是命中键位表的键就会走到这儿）。按本段注释的原意（"交给编辑器那套"）改回来。
            editorShortcuts.dispatch(event)
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        // ⚠️ **这里必须是 ASCII 的连字符 `-`（活 3）**：原来写的是 U+2014 `—`，自绘标题栏那行
        // （`StudioShell` 读这个标题）把它显示成一个 `?` —— 那是一条字体的坑，换 ASCII 最省事。
        // ⚠️ 只改本文件这一处（共用树里那些 `—` 不能动，别的地方显示正常）。
        title = if (pageTitle.isBlank()) "NAI Studio" else "NAI Studio - $pageTitle",
        // **画布编辑器的键位**：这里用 `onPreviewKeyEvent`（**抢在焦点节点之前**）。
        //
        // 为什么非抢不可：编辑器要"按住空格 = 平移"，而**空格的抬起（KeyUp）会被聚焦着的按钮吃掉**
        // ——Compose 的 Button 把 Space 当成"激活"，KeyUp 也被它消费。结果就是
        // 用户松开了空格、`canvasSpacePan` 还停在 true，画布一直能拖（用户 2026-09-16 报的正是这个）。
        // 抢在焦点之前，按下和抬起都能拿到。
        //
        // 代价：字母键也会先到编辑器手里。所以编辑器**自己**看
        // `AppState.canvasTextFocused` ——输入框拿着焦点时它一个键都不吃（见 `onCanvasShortcut`）。
        //
        // ⚠️ **键位表里的组合（`Ctrl+Enter` = 执行一次生图 …）也在这里先认一遍**（2026-09-19：
        // 用户报「右 Ctrl 与回车的组合不能触发执行」时补的）。为什么非得在预览阶段：
        // 这些组合原来只在**冒泡阶段**（下面的 `onKeyEvent`）认，而冒泡排在"聚焦节点"后面 ——
        // 本仓库里有两条**实测**会把回车（连 Ctrl+Enter 一起）先吃掉的：
        //  ① 画布编辑器开着时 `EditorShortcuts`（就是上面这个 `dispatch`）整颗吃掉回车；
        //  ② Compose 的 `Modifier.clickable`（按钮/开关/列表项/侧栏项）：它把**回车按下**当
        //     "press"、抬起当"click"，而且**不检查任何修饰键**（1.8.2 的 `ClickableKt.isPress/
        //     isClick`，判的是 `Enter/NumPadEnter/DirectionCenter/Spacebar`）—— 只要某个可点节点
        //     拿着焦点，`Ctrl+Enter` 就到不了窗口层。于是同一个组合"有时能用、有时不能用"。
        // 在预览阶段认掉，就跟"焦点在谁身上"无关了；键位表本身一个字没改（还是 `actionFor`）。
        onPreviewKeyEvent = { event ->
            // 诊断：`Ctrl+Enter` 不触发时，请把这一行贴回来（`%APPDATA%\NAI Studio\app.log`）。
            // ⚠️ 条件特意放宽成"**带任一修饰键**的回车"：这次要查的正是"右 Ctrl 的 Ctrl 位到底有没有
            // 报上来"——若只按 `isCtrlPressed` 打，恰好会出现"右 Ctrl 没被认成 Ctrl → 一条日志都不留"，
            // 跟"按键根本没到窗口"分不开。光回车（不带修饰键）照旧不打，量还是只有这几次。
            if (
                event.type == KeyEventType.KeyDown &&
                (event.key == Key.Enter || event.key == Key.NumPadEnter) &&
                (event.isCtrlPressed || event.isShiftPressed || event.isAltPressed)
            ) {
                logInfo(
                    "Shortcuts",
                    "回车+修饰键：ctrl=${event.isCtrlPressed} shift=${event.isShiftPressed} " +
                        "alt=${event.isAltPressed} key=${event.key} " +
                        "matched=${shortcuts.actionFor(event) ?: "none"} " +
                        "editor=${editorShortcuts.isActive}",
                )
            }
            // ① 键位表里的组合优先（必须抢在聚焦节点/编辑器之前，理由见上面那段注释）
            val key = event.key
            when (event.type) {
                KeyEventType.KeyDown -> {
                    val pageTarget = if (event.isCtrlPressed && event.isAltPressed && !event.isShiftPressed) {
                        when (key) {
                            Key.One -> 0
                            Key.Two -> 1
                            Key.Three -> 2
                            Key.Four -> 3
                            Key.Five -> 4
                            else -> -1
                        }
                    } else -1
                    val infiniteHandled = if (
                        pageShortcuts.currentPage == 0 && state.canvasMode == 1 &&
                        !state.textInputFocused && !editorShortcuts.isActive && !event.isAltPressed
                    ) {
                        when {
                            event.isCtrlPressed && !event.isShiftPressed && key == Key.D -> {
                                state.updateInfiniteFrame(null)
                                true
                            }
                            event.isCtrlPressed && !event.isShiftPressed && key == Key.Z -> {
                                state.undoInfiniteCanvas()
                                true
                            }
                            event.isCtrlPressed && event.isShiftPressed && key == Key.Z -> {
                                state.redoInfiniteCanvas()
                                true
                            }
                            !event.isCtrlPressed && !event.isShiftPressed &&
                                (key == Key.LeftBracket || key == Key.RightBracket) -> {
                                val step = if (key == Key.RightBracket) 64 else -64
                                state.setSettings {
                                    it.copy(infiniteContext = (it.infiniteContext + step).coerceIn(0, 512))
                                }
                                true
                            }
                            else -> false
                        }
                    } else false
                    if (pageTarget >= 0 && pageShortcuts.isAvailable) {
                        pageShortcuts.goToPage(pageTarget)
                        true
                    } else if (infiniteHandled) {
                        true
                    } else if (heldShortcutKeys.containsKey(key)) {
                        // 连发（按住不放）：同一条命令已经派过一次了，这里只吃掉，别再来一遍
                        true
                    } else {
                        val command = if (state.textInputFocused &&
                            (event.key == Key.Tab || event.key == Key.Q)
                        ) null else shortcuts.actionFor(event)
                        if (command != null && shellCommands.dispatch(command)) {
                            // 记下"这颗键被快捷键吃了、还没抬起"（KeyUp 与打字事件都要用它）
                            heldShortcutKeys[key] = command
                            logInfo(
                                "Shortcuts",
                                "命中 $command，已消费（焦点在输入框=${state.textInputFocused}）",
                            )
                            true
                        } else {
                            // ② 没命中键位表才轮到画布编辑器（空格 / 字母 那套键位）
                            handleCanvasKeys(event)
                        }
                    }
                }

                KeyEventType.KeyUp -> {
                    // 上面吃掉过的那颗键，抬起也在吃 —— clickable 正是在这一步 `onClick()`
                    if (heldShortcutKeys.remove(key) != null) true else handleCanvasKeys(event)
                }

                else -> {
                    // ③ **AWT 的"打字事件"（`KEY_TYPED`）就落在这一支**（2026-09-26 查清的那颗 bug）。
                    //
                    // 它长这样：`type=KeyEventType.Unknown`（`KeyEvent.desktop.kt` 里 401/402 之外的 id
                    // 全映射成 Unknown）、`key=Key.Unknown`（KEY_TYPED 的 keyCode 是 0）、那个字符在
                    // `utf16CodePoint` 里。**`actionFor` 永远认不出它**（没有 key 可翻）。
                    //
                    // 实测（本机 JDK 17，AWT 原始事件）：`Alt+Q` 会发三个事件 ——
                    // `KEY_PRESSED`(Q) → **`KEY_TYPED`(keyCode=0, keyChar='q', alt=true)** → `KEY_RELEASED`。
                    // 第一个我们吃了（命令照常派发 ✓），**第三个没吃** → 落到焦点路径 → 输入框的
                    // `TextFieldKeyInput` 判 `isTypedEvent`（= KEY_TYPED 且 keyChar 可打印，'q' 可打印）
                    // → `CommitTextCommand("q")` 插进提示词 ✗。用户的原话「打完提示词后使用快捷键执行，
                    // 会在提示词里输出按键」就是这个 'q'。
                    //
                    // ⚠️ `Ctrl+字母` 那几条（Ctrl+O/S/I/P/D/B/E/Q、Ctrl+1..5）看不见这个现象，只是因为
                    // keyChar 是控制字符（实测 Ctrl+O→U+000F、Ctrl+Enter→U+000A、Ctrl+Shift+O→U+000F，
                    // Ctrl+1..5 干脆不发 KEY_TYPED），被 `isISOControl` 挡掉了 —— 不是我们吃干净了。
                    // 所以这里不管哪条组合，只要对得上就一律吃掉（两条判据，见下）。
                    val typedChar = event.utf16CodePoint
                    val typedHit = if (typedChar == 0) {
                        null
                    } else {
                        // 判据 A：修饰键 + 字符与某条绑定**完全一致**（和 `actionFor` 一个口径）
                        shortcuts.actionForTyped(
                            codePoint = typedChar,
                            ctrl = event.isCtrlPressed,
                            shift = event.isShiftPressed,
                            alt = event.isAltPressed,
                        )
                            // 判据 B：那颗命中键位表的键**还按着**，而且这个字符正是它会产生的
                            //（万一某个布局/输入法报上来的修饰键位和绑定时对不上，这条照样咽得掉）
                            ?: heldShortcutKeys.values.firstOrNull {
                                shortcuts.typedCharFor(it) == typedChar
                            }
                    }
                    if (typedHit != null) {
                        logInfo(
                            "Shortcuts",
                            "命中 $typedHit，已消费（焦点在输入框=${state.textInputFocused}）",
                        )
                        true
                    } else {
                        // 普通的打字事件（真在输入框里打的那个字）照旧放行
                        handleCanvasKeys(event)
                    }
                }
            }
        },
        // **电脑端快捷键**：`Ctrl+Alt+1..5` 切到侧栏对应页面。
        //
        // 接到窗口这一层是因为：Compose 的按键"先给焦点节点、没被消费才往上冒泡"，
        // 而"没聚焦任何东西"时（刚启动、点过空白处）只有窗口这层收得到 —— 所以在窗口上接最稳。
        // 具体怎么切页由外壳登记（见 `LocalPageShortcuts` 的注释）。
        onKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else {
                // **菜单上标着的快捷键**（用户 2026-09-19：标了就得真能用）。
                // 动作住在外壳的菜单条里，这里只把按键翻成命令 id（见 `ShellCommands`）。
                // ⚠️ 键位**不再是写死的**：`ShortcutBindings` 是唯一那份表（帮助 → 快捷键… 能改），
                // 菜单上显示的也是它 —— 两边永远不会打架。
                // ⚠️ 这一段现在是**兜底**：正常情况下这些组合在上面 `onPreviewKeyEvent` 里就认掉了
                // （那里早于聚焦节点，见那段注释）；只有"那会儿外壳还没登记好"之类的边角才会走到这儿。
                val command = if (state.textInputFocused &&
                    (event.key == Key.Tab || event.key == Key.Q)
                ) null else shortcuts.actionFor(event)
                if (command != null && shellCommands.dispatch(command)) {
                    true
                } else {
                    // 页面切换使用 Ctrl+Alt+1..6；Ctrl+1 留给画布 100%。
                    // ⚠️ 用户 2026-09-26 加了「高级漫画」页（插在「工具」之后）= 第 4 页，
                    // 所以这里必须补上 `Ctrl+6`：菜单「工具」那一栏是**按 tabs 顺序自动生成**的
                    // （快捷键文字就是 `Ctrl+${index+1}`），少了这一个键菜单上就会标一个按不动的快捷键 ✗。
                    val target = if (
                        event.isCtrlPressed && !event.isShiftPressed && event.isAltPressed
                    ) {
                        when (event.key) {
                            Key.One -> 0
                            Key.Two -> 1
                            Key.Three -> 2
                            Key.Four -> 3
                            Key.Five -> 4
                            else -> -1
                        }
                    } else {
                        -1
                    }
                    if (target >= 0 && pageShortcuts.isAvailable) {
                        pageShortcuts.goToPage(target)
                        true
                    } else {
                        false
                    }
                }
            }
        },
    ) {
        // **窗口与任务栏的图标**。
        //
        // ⚠️ jpackage 的 `--icon` 只管**启动器 exe 的**图标（文件资源管理器里那个）；
        // 窗口标题栏与任务栏上运行时显示的图标来自 `java.awt.Window.setIconImage` ——
        // 不设就是 Java 的 Duke 图标（实测：exe 图标换了、标题栏还是 Duke）。
        // 资源由 tools\make_app_icon.ps1 按 brand/nai-icon.svg 的坐标生成。
        val window = this.window
        LaunchedEffect(window) {
            runCatching {
                val stream = object {}.javaClass.getResourceAsStream("/icon/app-256.png")
                if (stream != null) {
                    stream.use { window.iconImage = javax.imageio.ImageIO.read(it) }
                }
            }
        }

        // **自绘标题栏**：把系统标题栏那一条并进客户区（Windows 上走 WndProc 钩子）——
        // 于是共用树里那条菜单行能画在标题栏上（用户 2026-09-19 选的方案 C）。
        // 钩子只改命中与客户区尺寸，**描边 / 阴影 / 圆角 / 边缘缩放仍然是系统的**。
        LaunchedEffect(window) {
            DesktopTitleBarHook.install(window)
        }
        // 拖动标题栏 = 拖窗口、双击 = 最大化/还原；三颗窗口按钮也由我们画（见 `WindowChrome`）
        val windowChrome = rememberWindowChrome(window, ::exitApplication)

        // 电脑专属：**把图片文件拖进窗口 = 导入图片**（图生图的底图）。
        // 手机上"导图"只有一条路（系统相册），电脑上有鼠标就该能拖 —— 这是桌面版最省事、
        // 也最容易被期待的一个操作（见 docs/25 §2.3 的交互范式清单）。
        // 落地逻辑在 [FileDropTarget]（为什么不能自己挂 AWT `DropTarget`，那儿有实证）。
        val dropTarget = remember(state, uiHost) {
            FileDropTarget(
                onImage = { file ->
                    state.importDroppedImage(file.absolutePath)
                    uiHost.toast(
                        RuntimeText.format(
                            state.settings.language,
                            "drop.done",
                            mapOf("name" to file.name),
                        ),
                    )
                },
                onNotImage = {
                    uiHost.toast(RuntimeText.text(state.settings.language, "drop.notImage"), long = true)
                },
            )
        }

        val settings = state.settings
        val dark = when (settings.theme) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        }
        // 自定义调色：深/浅各一套（同一个 `paletteOverridesOf`，手机那份）
        val overrides = if (dark) {
            paletteOverridesOf(
                primaryHex = settings.colorPrimaryDark,
                containerHex = settings.colorContainerDark,
                backgroundHex = settings.colorBackgroundDark,
                textHex = settings.colorTextDark,
            )
        } else {
            paletteOverridesOf(
                primaryHex = settings.colorPrimaryLight,
                containerHex = settings.colorContainerLight,
                backgroundHex = settings.colorBackgroundLight,
                textHex = settings.colorTextLight,
            )
        }

        // ---- 背景层（用户 2026-09-20 口径）：**自定义背景图** + 系统标题栏染色 ----
        //
        // ⚠️ **不再读 Windows 桌面壁纸**（老口径已废弃，见 `DesktopBackdrop` 顶部说明）。
        // 自定义背景图优先；新的 glass_* 主题在未设图时提供内置柔光磨砂底图。
        // 选完图的那一刻就复制进了 App 数据目录（`services/CustomBackground`），
        // 这里按设置里记的文件名读回来，等比 Crop 铺满；
        // **糊不糊看「毛玻璃质感」开关**（用户 2026-09-20：「自选的背景图可选毛玻璃质感」）：
        // 关 = 原图原样；开 = 缩到 512 再两遍均值模糊（见 `DesktopBackdrop.customBackgroundBitmap`）。
        //
        // 原有主题没设图时仍使用不透明底色；glass_* 主题退回自己的柔光底图。
        val backdropFile = remember(settings.customBackground) {
            CustomBackground.fileOf(platform, settings.customBackground)
        }
        // ⚠️ 解码 +（开了毛玻璃时）卷积都是 **CPU 活**：在 IO 线程算完再写进状态，
        // **组合期不做任何大图运算** —— 4K 原图直接卷积能把主线程按在那儿半秒。
        //
        // 键是「设置里那个文件名 + 毛玻璃开关」：**两者任一变化都重算**
        //（切开关就得换一张糊过的图，只看图名会继续用旧那张 ✗）。
        //
        // 这里刻意用 `mutableStateOf` 而不是 `produceState`：换图 / 切开关时**旧的那张先留在屏上**，
        // 等新的算完再替换，不会闪一帧主题底色（`produceState` 换键会先把值退回 initialValue）。
        var customBackdrop by remember { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(backdropFile, settings.customBackgroundFrosted) {
            customBackdrop = backdropFile?.let { file ->
                withContext(Dispatchers.IO) {
                    DesktopBackdrop.customBackgroundBitmap(
                        file = file,
                        frosted = settings.customBackgroundFrosted,
                    )
                }
            }
        }
        // 仅生成小尺寸的渐变，不涉及文件解码；主题切换立即匹配新的深浅配色。
        val themeBackdrop = remember(settings.palette, dark) {
            DesktopBackdrop.themeBackgroundBitmap(settings.palette, dark)
        }
        // ⚠️ **参考稿风格不铺"主题自带的那张柔光底图"**（用户 2026-09-27：「两套共存，可切换」）：
        //    参考稿自己有一层**径向渐变 + 24px 网格**的画布底（见 `NaiSkinTokens` 的说明），
        //    再叠一张柔光图会两层打架。但**用户自己选的自定义背景图仍然优先** ——
        //    那是用户明确要的，不该被风格开关夺走。
        val skin = NaiSkin.fromId(settings.skin)
        val backdrop = if (skin == NaiSkin.Reference) {
            customBackdrop.takeIf { backdropFile != null }
        } else {
            customBackdrop.takeIf { backdropFile != null } ?: themeBackdrop
        }
        // 背景层铺开时的窗口尺寸（提示词栏 / 底栏要按它对齐重画，见 glassBackdrop）
        var backdropSize by remember { mutableStateOf(IntSize.Zero) }

        // 系统标题栏跟着主题走：深色模式切深色标题栏，标题栏底色/文字色用 App 自己的那两档。
        // ⚠️ **底下铺的是什么（有没有背景图）都不参与取色**：这里只认主题色，
        // 免得标题栏颜色跟着用户那张照片变（同一套主题在不同图上颜色不一致会很怪）。
        // Win10 不认底色那三条（返回错误码 → 内部忽略），"深色标题栏"照样生效。
        val titleBarScheme = remember(dark, settings.palette, overrides) {
            naiColorScheme(dark, settings.palette, overrides)
        }
        LaunchedEffect(window, dark, titleBarScheme) {
            DesktopBackdrop.styleTitleBar(
                window = window,
                dark = dark,
                background = titleBarScheme.background.toArgb(),
                foreground = titleBarScheme.onBackground.toArgb(),
            )
        }

        // 侧边栏那颗「深/浅」按钮要调它。
        //
        // ⚠️ 这里 2026-09-16 改成**和手机一样播扩散动画**（用户："修暗色模式和浅色模式的
        // 切换动画，没有扩散和遮罩动画"）。原先电脑是直接切（理由是老代码里"Robot 抓屏不一定准"），
        // 现在按手机的同一套路来：
        //   1. 抓**切换前**那一帧客户区（[captureWindowFrame]）；
        //   2. 交给 `AppState.startThemeWipe` —— 它在同一次状态写入里"先切主题、再铺旧截图"，
        //      所以不会闪一帧新主题；
        //   3. 遮罩（[ThemeWipeOverlay]，共用树里那份，手机电脑同一份源码）从按钮圆心把洞挖大。
        // 抓屏失败就走 [AppState.setTheme] 兜底 —— 宁可没有动画，也不能点不动。
        val switchTheme: (androidx.compose.ui.geometry.Offset, Boolean) -> Unit = remember(state, window) {
            { origin, targetDark ->
                val frame = captureWindowFrame(window)
                // 出问题时先看 app.log 这一行：capture=false 就是抓屏失败（退化成"直接切主题"）
                com.kallan.naistudio.platform.logInfo(
                    "ThemeWipe",
                    "click targetDark=$targetDark capture=${frame != null} origin=(${origin.x},${origin.y})",
                )
                if (frame != null) {
                    state.startThemeWipe(
                        snapshot = frame.bitmap,
                        originX = origin.x,
                        originY = origin.y,
                        dark = targetDark,
                        // 窗口有一部分在屏幕外（最大化）时，截图只是屏幕里那部分：
                        // 把偏移和窗口物理尺寸一起交给遮罩层，让它按逻辑坐标摆正
                        snapshotOffsetX = frame.offsetX,
                        snapshotOffsetY = frame.offsetY,
                        windowWidthPx = frame.windowWidth,
                        windowHeightPx = frame.windowHeight,
                    )
                } else {
                    state.setTheme(if (targetDark) "dark" else "light")
                }
            }
        }

        CompositionLocalProvider(
            // 共用 ui/ 的能力入口（默认值是 error(...)，漏了会当场抛）
            LocalImageIo provides platform.images,
            LocalPlatform provides platform,
            LocalUiHost provides uiHost,
            LocalThemeSwitch provides switchTheme,
            LocalPageShortcuts provides pageShortcuts,
            LocalEditorShortcuts provides editorShortcuts,
            LocalShellCommands provides shellCommands,
            LocalCanvasViewCommands provides canvasViewCommands,
            LocalShortcutBindings provides shortcuts,
            LocalWindowChrome provides windowChrome,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    // 量一下窗口客户区尺寸：磨砂壁纸是按它 Crop 铺开的，
                    // 面板要"自己重画一遍壁纸"（[glassBackdrop]）就得知道这个基准
                    .onSizeChanged { backdropSize = it }
                    // **拖放导入图片的落点**：这条就是 CMP 拖放管理器自己那条路
                    //（[FileDropTarget] 的注释里写了为什么挂 AWT `DropTarget` 收不到）。
                    // 挂在根 Box 上、且 `shouldStartDragAndDrop` 只认文件拖动 ——
                    // 拖一段文字进来不会被这一层接走（也就不会弹提示）。
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { event -> event.isFileDrag() },
                        target = dropTarget,
                    ),
            ) {
                // 背景图层：自选图或主题柔光底图，位于所有内容的最底下。
                // 用 `Crop` 铺满（等比不裁会留黑边；超大的图在 `customBackgroundBitmap` 里已经缩过）。
                backdrop?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // 参考稿风格：在**没有任何背景图**时铺它自己那层底
                // （两团径向渐变 + 纯色，见 `referenceBackdrop`）。
                // ⚠️ 有背景图时让图优先 —— 那是用户明确选的，不该被风格顶掉。
                if (skin == NaiSkin.Reference && backdrop == null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .referenceBackdrop(dark = dark, palette = settings.palette),
                    )
                }
                // 提示词栏 / 底栏要"和侧栏一样的毛玻璃"：它们自己按窗口对齐重画这层背景图
                // （见 `glassBackdrop` 的注释 —— 比把页面底色去掉稳，不会把内容色弄成黑的）
                CompositionLocalProvider(
                    LocalAppBackdrop provides backdrop?.let { AppBackdrop(it, backdropSize) },
                    // 面板/窗口的描边颜色（用户 2026-09-26：「设置可调边框颜色」）——
                    // 设置里是空的就用 `NaiPanelChrome.Border` 那套默认深色 ✓。
                    LocalPanelBorderColor provides (
                        parseHexColor(settings.windowBorderColor)
                            ?: com.kallan.naistudio.ui.NaiPanelChrome.Border
                        ),
                    // 标题栏颜色（用户 2026-09-26）：空 = null = 跟随面板底色 = 没有独立标题栏 ✓
                    // ⚠️ 这三档都是"一份值、两套模式共用"✗ → 走 `adaptForMode` 按当前深浅自动换算 ✓
                    //（用户 2026-09-26：「调色后调整为暗色模式时，要自动调色」✓）。
                    LocalPanelTitleBarColor provides parseHexColor(settings.windowTitleBarColor)
                        ?.let { adaptForMode(it, dark) },
                    // 窗口面板底色（用户 2026-09-26「各个窗口的背景色」）：空 = null = 跟随 surface ✓
                    LocalPanelBackgroundColor provides parseHexColor(settings.windowColor)
                        ?.let { adaptForMode(it, dark) },
                ) {
                NaiStudioTheme(
                    dark = dark,
                    palette = settings.palette,
                    overrides = overrides,
                    // 只有真的铺了背景图才把页面底色降成半透明（否则底层没东西，
                    // 半透明只会露出窗口后面的黑）—— 没铺时就是不透明的主题底色。
                    glass = backdrop != null,
                ) {
                    // **外观风格**（用户 2026-09-27）：界面各处读它决定"这块面该长什么样"。
                    // ⚠️ 必须包在 `NaiStudioTheme` **里面** —— `NaiSkinTokens` 要按
                    //    `MaterialTheme.colorScheme` 取色（玻璃风格用的就是主题的 surface）。
                    CompositionLocalProvider(LocalNaiSkin provides skin) {
                    // 窗口尺寸给共用页面用（图库列宽 / 工具页最大高度 / 生成页双栏判定）
                    ProvideWindowSize {
                        when {
                            !state.booted -> BootPlaceholder()
                            state.showLoginGate -> LoginScreen(state)
                            else -> StudioShell(state, appName = "NAI Studio")
                        }
                    }
                    }
                    // **拖着图片悬在窗口上时的提示浮层**（活 1）。
                    // ⚠️ 刻意放在 `NaiStudioTheme` **里面**：它按 `MaterialTheme.colorScheme` 取色，
                    // 放在外面（开屏/轻提示那一层的旁边）拿到的是 Material3 默认配色、不是 App 主题。
                    // 层序：在界面之上、开屏/轻提示/主题扩散之下（它不该盖住那几层）。
                    // 状态来自 [FileDropTarget]（Compose 拖放回调写、这里读）。
                    if (dropTarget.isHovering) {
                        DropHintOverlay(
                            language = state.settings.language,
                            isHovering = true,
                            fileName = dropTarget.hoverName,
                        )
                    }
                }
                }
                if (splashVisible) {
                    SplashOverlay(alpha = 1f, appName = "NAI Studio")
                }
                // 轻提示浮层：`UiHost.toast()` 的地面（桌面没有系统 Toast）
                DesktopToastOverlay()
                // 主题扩散遮罩：**最上面那一层**（要盖住侧边栏、内容、开屏、提示）。
                // 和手机入口一样挂在最外层，播完自己撤掉。
                state.themeWipe?.let { wipe ->
                    ThemeWipeOverlay(wipe = wipe, onFinished = { state.finishThemeWipe() })
                }
            }
        }
    }
}

/** 启动未就绪时的占位（和手机入口一样：转圈，而不是闪一下登录页）。 */
@Composable
private fun BootPlaceholder() {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

/** 自绘标题栏的双击判定窗口（和系统那道 500ms 差不多，收紧一点免得误触）。 */
private const val WINDOW_DOUBLE_CLICK_MS = 400L

/**
 * **自绘标题栏的那点窗口能力**（拖窗口 / 最小化 / 最大化 / 关闭）—— 交给共用树那条标题栏用。
 *
 * `maximized` 靠 `ComponentAdapter` 跟着窗口事件走（不是点按钮时才算）：Win+↑、双击标题栏、
 * 任务栏右键最大化这些**系统侧的操作**也认，第 2 颗按钮的图形才会跟着变。
 */
@Composable
private fun rememberWindowChrome(
    window: Frame,
    onClose: () -> Unit,
): WindowChrome {
    var maximized by remember(window) { mutableStateOf(isWindowMaximized(window)) }
    DisposableEffect(window) {
        val listener = object : java.awt.event.ComponentAdapter() {
            override fun componentResized(event: java.awt.event.ComponentEvent) {
                maximized = isWindowMaximized(window)
            }
        }
        window.addComponentListener(listener)
        onDispose { window.removeComponentListener(listener) }
    }
    // ⚠️ **三颗按钮得我们自己画**（用户选的方案 C 的必然结果）：菜单要画在标题栏那一行，
    // 就得把标题栏整条并进客户区；而一旦并进来，DWM 就没有非客户区可画 —— 系统那三颗按钮
    // **不会**再出现（实测：屏幕截图里那一段是空的，不是被我们的内容盖住，是真的没画）。
    // 外面的边框/阴影/圆角/边缘缩放仍然是**系统**的。
    return WindowChrome(
        titleBarDrag = Modifier.windowTitleBarDrag(window),
        maximized = maximized,
        onMinimize = { window.extendedState = window.extendedState or Frame.ICONIFIED },
        onToggleMaximize = { maximized = toggleWindowMaximized(window) },
        onClose = onClose,
    )
}

private fun isWindowMaximized(window: Frame): Boolean =
    (window.extendedState and Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH

/** 最大化 ↔ 还原；返回"做完之后是不是最大化"（给按钮图形用）。 */
private fun toggleWindowMaximized(window: Frame): Boolean {
    val next = !isWindowMaximized(window)
    window.extendedState = if (next) {
        window.extendedState or Frame.MAXIMIZED_BOTH
    } else {
        window.extendedState and Frame.MAXIMIZED_BOTH.inv()
    }
    return next
}

/**
 * 拖标题栏 = 拖窗口；**双击 = 最大化 / 还原**（Windows 的老习惯，自绘标题栏也得有）。
 *
 * 为什么不"自己 setLocation"（用户 2026-09-19：「拖动这个标题栏窗口会跳动」）：
 * 窗口跟着鼠标走之后，Compose 报的位移是**相对这个正在移动的窗口**的 —— 下一帧又把它喂回
 * `setLocation`，窗口就会漂。现在按下去**把拖动交给 Windows**
 *（[DesktopTitleBarHook.requestWindowMove]）：系统按屏幕坐标拖，一丝不抖，还顺带把
 * 贴靠 / 甩到屏幕边 / 拖最大化窗口自动还原全带回来了。
 *
 * 钩子万一没装上（`requestWindowMove` 返回 false），退回"按屏幕坐标自己搬"（`MouseInfo`
 * 拿的是真实光标位置，不是窗口内坐标，所以也不抖）。
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.windowTitleBarDrag(window: Frame): Modifier = this
    .pointerInput(window) {
        var lastDownAt = 0L
        awaitEachGesture {
            // 只认"没被别处消费的按下"：菜单标题 / 余额胶囊 / 三颗按钮自己先吃掉，
            // 所以点它们不会变成拖窗口。
            val down = awaitFirstDown(requireUnconsumed = true)
            val now = System.currentTimeMillis()
            if (now - lastDownAt < WINDOW_DOUBLE_CLICK_MS) {
                lastDownAt = 0L
                down.consume()
                toggleWindowMaximized(window)
                return@awaitEachGesture
            }
            lastDownAt = now
            down.consume()
            if (!DesktopTitleBarHook.requestWindowMove(window)) {
                val anchorCursor = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
                val anchorWindow = window.location
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    if (!change.pressed) break
                    val cursor = MouseInfo.getPointerInfo()?.location ?: continue
                    window.setLocation(
                        anchorWindow.x + (cursor.x - anchorCursor.x),
                        anchorWindow.y + (cursor.y - anchorCursor.y),
                    )
                }
            }
        }
    }

// ---------------------------------------------------------------------------
// 窗口几何：记住上次的大小与位置
// ---------------------------------------------------------------------------
private const val KEY_WINDOW_WIDTH = "window.width"
private const val KEY_WINDOW_HEIGHT = "window.height"
private const val KEY_WINDOW_X = "window.x"
private const val KEY_WINDOW_Y = "window.y"

/**
 * 首次启动的默认窗口（**用户 2026-09-19 指定**：「记录现在的窗口大小，以后默认开这么大」）。
 *
 * 值就是当时盘上记着的那一份（实测 `1692×1128 @ (369,154)`）。之后照旧"记住上次的大小与位置"——
 * 这里只是**没有记录时**的兜底，以及"记录看起来是最大化时存下来的"那种情况下回退用的大小。
 */
private const val DEFAULT_WINDOW_WIDTH = 1692
private const val DEFAULT_WINDOW_HEIGHT = 1128

/** 给单测看的默认尺寸（`internal`：测试在那个模块里）。 */
internal val DefaultWindowWidth = DEFAULT_WINDOW_WIDTH
internal val DefaultWindowHeight = DEFAULT_WINDOW_HEIGHT

/** 窗口最小尺寸：再小的话侧栏 + 内容就没法看了。 */
internal const val MIN_WINDOW_WIDTH = 720
internal const val MIN_WINDOW_HEIGHT = 520

/** 至少要有这么宽/这么高露在屏幕里，否则算"跑到屏幕外了"。 */
private const val MIN_VISIBLE = 80

/** 记住的窗口几何（**纯数据**，由 [restoreWindowGeometry] 算出来）。 */
internal data class WindowGeometry(val width: Int, val height: Int, val x: Int, val y: Int)

/**
 * 从记忆里算出这次的窗口几何（**纯函数，可测**）。
 *
 * 两件必须做的事：
 *  1. **尺寸夹取**：不小于最小尺寸，也不大于屏幕（有人会用 1024×600 的小屏，或者把
 *     4K 屏上的记录带回笔记本）——夹不住就会出现"启动后界面挤成一团"或"窗口比屏幕还大"；
 *  2. **位置校验**：窗口必须至少有 [MIN_VISIBLE] 那么宽/高落在屏幕里。
 *     ⚠️ 这条不是洁癖：显示器拔了、分辨率变了，记下来的坐标就可能完全在屏幕外 ——
 *     那时窗口**看不见也点不到**，用户只会觉得"App 打不开了"。
 *     落在外面就回到居中，宁可位置不准，也不能让窗口消失。
 */
internal fun restoreWindowGeometry(
    savedWidth: Int,
    savedHeight: Int,
    savedX: Int,
    savedY: Int,
    screenX: Int,
    screenY: Int,
    screenWidth: Int,
    screenHeight: Int,
): WindowGeometry {
    // ⚠️ **"记录看起来是最大化" → 不拿它当窗口大小**（用户 2026-09-19：「顶部突出的窗口收回」）：
    // 早期版本在最大化时也写盘，存下来的就是"屏幕 + 四周隐形边框"（比屏幕还大、坐标是负的）。
    // 那种记录恢复出来是"顶出屏幕"的样子，所以这里识别出来就改用默认尺寸居中。
    // 判据要**同时**满足：尺寸≈整块工作区（±64px）**且**坐标在原点之外（`≤ 0`）。
    val looksMaximized = savedWidth in (screenWidth - 64)..(screenWidth + 64) &&
        savedHeight in (screenHeight - 64)..(screenHeight + 64) &&
        savedX <= screenX && savedY <= screenY

    val width = (if (looksMaximized) DEFAULT_WINDOW_WIDTH else savedWidth)
        .coerceIn(MIN_WINDOW_WIDTH, maxOf(MIN_WINDOW_WIDTH, screenWidth))
    val height = (if (looksMaximized) DEFAULT_WINDOW_HEIGHT else savedHeight)
        .coerceIn(MIN_WINDOW_HEIGHT, maxOf(MIN_WINDOW_HEIGHT, screenHeight))

    val centeredX = screenX + (screenWidth - width) / 2
    val centeredY = screenY + (screenHeight - height) / 2
    val x = if (
        !looksMaximized &&
        savedX + width - MIN_VISIBLE >= screenX &&
        savedX <= screenX + screenWidth - MIN_VISIBLE
    ) {
        savedX
    } else {
        centeredX
    }
    val y = if (
        !looksMaximized &&
        savedY + height - MIN_VISIBLE >= screenY &&
        savedY <= screenY + screenHeight - MIN_VISIBLE
    ) {
        savedY
    } else {
        centeredY
    }
    return WindowGeometry(width, height, x, y)
}

/** 读记忆 + 用当前屏幕的可用区域做校验。没记忆过就用默认尺寸、居中。 */
private fun loadWindowGeometry(platform: Platform): WindowGeometry {
    val bounds = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        .maximumWindowBounds
    val kv = platform.kv
    val savedWidth = kv.getInt(KEY_WINDOW_WIDTH, DEFAULT_WINDOW_WIDTH)
    val savedHeight = kv.getInt(KEY_WINDOW_HEIGHT, DEFAULT_WINDOW_HEIGHT)
    // 没记过位置（首次启动）→ 给一个屏幕外的哨兵值，让它走"居中"那一支
    val savedX = kv.getInt(KEY_WINDOW_X, Int.MIN_VALUE / 2)
    val savedY = kv.getInt(KEY_WINDOW_Y, Int.MIN_VALUE / 2)
    return restoreWindowGeometry(
        savedWidth = savedWidth,
        savedHeight = savedHeight,
        savedX = savedX,
        savedY = savedY,
        screenX = bounds.x,
        screenY = bounds.y,
        screenWidth = bounds.width,
        screenHeight = bounds.height,
    )
}

/** 拖进窗口的文件里，哪些扩展名当图片处理。 */
private val DROPPABLE_IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

/**
 * 从 Compose 的拖放事件里掏出 AWT 那一份数据。
 *
 * 电脑上外来拖放的 `nativeEvent` 就是 AWT 的 `DropTargetDragEvent` / `DropTargetDropEvent`
 *（实证：`AwtDragAndDropManager$dropTargetListener` 里有这两个重载的
 *`DragAndDropEvent(DropTargetDragEvent)` / `(DropTargetDropEvent)`），两者的
 * `transferable` 都拿着"被拖的那些文件"。取不到就当"不是文件"。
 *
 * ⚠️ Compose 自己那个 `Transferable.dragData()` 是 **internal** 的（编译不过），
 * 所以这里直接读 `DataFlavor.javaFileListFlavor`。
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.awtTransferable(): Transferable? = when (val native = nativeEvent) {
    is DropTargetDropEvent -> native.transferable
    is DropTargetDragEvent -> native.transferable
    else -> null
}

/** 这次拖的是不是**文件**（不是文件就不接这一趟，免得拖一段文字进来也弹提示）。 */
@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.isFileDrag(): Boolean = runCatching {
    // ⚠️ 优先问 AWT 事件自己：`DropTargetDragEvent.isDataFlavorSupported` **不需要**能拿到
    // transferable（悬停阶段有的拖放源就是不给），比先去拿 transferable 再去问它稳。
    when (val native = nativeEvent) {
        is DropTargetDragEvent -> native.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        is DropTargetDropEvent -> native.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        else -> awtTransferable()?.isDataFlavorSupported(DataFlavor.javaFileListFlavor) == true
    }
}.getOrDefault(false)

/** 这次拖进来的**第一个文件**（不是文件拖放就是 null）。 */
@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.draggedFile(): File? = runCatching {
    val transferable = awtTransferable() ?: return@runCatching null
    @Suppress("UNCHECKED_CAST")
    val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
        ?: return@runCatching null
    files.firstOrNull()
}.getOrNull()

/**
 * **把图片文件拖进窗口 = 直接载入图片**（用户 2026-09-19：「新增将图片拖入直接加载图片的功能」）。
 *
 * ## 为什么早先那版拖进来毫无反应
 *
 * 早先是在 `Window` 的 `window`（那个 `ComposeWindow`，本质是 `JFrame`）上自己挂了一个 AWT
 * `DropTarget`。但 CMP 1.8.2 的 `ComposeSceneMediator` **无条件**会给内容面板（一个 `JComponent`）
 * 挂一个（`javap` 实证：`container.setDropTarget(dragAndDropManager.getDropTarget())`），
 * 而 AWT 的规矩是"拖放只交给光标底下**最深**的那个有活动 DropTarget 的组件"—— 内容面板铺满
 * 整个窗口，挂在外层 `JFrame` 上的那个**永远收不到** ✗（docs/25 §106.1 有实证记录）。
 * 所以那一版是死代码，本次已整体删掉。
 *
 * 现在改走 Compose 自己的 [dragAndDropTarget]：它就是管理器那条路，天然收得到；
 * 顺带还能拿到"正拖着东西悬在窗口上"的状态（[hoverName]），界面据此盖一层提示浮层。
 *
 * ## 落地动作
 *
 * [onImage]：交给 `AppState.importDroppedImage(path)` —— 和点「导入图片」走**同一条**逻辑
 *（读盘 → 缩放 → 复制进私有目录 → 填进图生图底图 + 预览），所以拖进来的图跟在选择器里选一张
 * 完全一致，并且会切回文生图页（拖放可能发生在图库 / 工具页）。
 *
 * ⚠️ 拖到**非图片**时给一句提示而不是静默忽略 —— 用户拖了个 PDF 进来，得知道为什么不理它。
 */
@OptIn(ExperimentalComposeUiApi::class)
private class FileDropTarget(
    private val onImage: (File) -> Unit,
    private val onNotImage: () -> Unit,
) : DragAndDropTarget {
    /** 正悬在窗口上的文件名（`null` = 名字读不出来，但确实有文件拖着）。提示浮层读它。 */
    var hoverName by mutableStateOf<String?>(null)
        private set

    /** 现在有没有文件悬在窗口上（提示浮层靠它决定画不画）。 */
    var isHovering by mutableStateOf(false)
        private set

    override fun onEntered(event: DragAndDropEvent) {
        hoverName = event.draggedFile()?.name
        isHovering = true
    }

    // 位置每帧都在变，但这儿的提示只看文件名，所以照样只在名字变了才写状态
    override fun onChanged(event: DragAndDropEvent) {
        val name = event.draggedFile()?.name
        if (name != hoverName) hoverName = name
        isHovering = true
    }

    override fun onExited(event: DragAndDropEvent) {
        hoverName = null
        isHovering = false
    }

    override fun onEnded(event: DragAndDropEvent) {
        hoverName = null
        isHovering = false
    }

    override fun onDrop(event: DragAndDropEvent): Boolean {
        hoverName = null
        isHovering = false
        val file = event.draggedFile()
        val image = file?.takeIf { it.isFile && it.extension.lowercase() in DROPPABLE_IMAGE_EXTENSIONS }
        if (image == null) {
            onNotImage()
            return false
        }
        onImage(image)
        return true
    }
}

/**
 * **拖着图片悬在窗口上时的提示浮层**（活 1 补回来的那一层）。
 *
 * 形状：整窗一层半透明遮罩 + 居中一颗胶囊，写「松开鼠标载入：<文件名>」。
 * 为什么要有：以前拖着文件进窗口**一点反馈都没有**，用户不知道这条路接没接上
 *（见 docs/25 §106 与 §130.4 的遗留清单：这一层在那次事故里丢了）。
 *
 * 文案走共用树的 i18n（`naistudio-shared/src/main/kotlin/com/kallan/naistudio/i18n/RuntimeText.kt`，
 * **只读确认、没有改那个文件**）：
 *  · `drop.hint`      =「松开鼠标，载入为图生图底图」—— 还不知道文件名时的兜底；
 *  · `drop.hintNamed` =「松开鼠标载入：{name}」—— 知道文件名时用它。
 * ⚠️ 任务里提到的 `drop.release` 这个键 **`RuntimeText` 里并不存在**
 *（`drop.*` 一共只有 `hint` / `hintNamed` / `done` / `notImage` 四条），所以用 **`drop.hintNamed`**
 * 顶上 —— 语义一致、中英两套都有。万一将来键名再变了，`RuntimeText.text` 找不到键时是
 * **原样返回 key**（不抛异常），所以这里不会崩，只是文案会露出键名。
 *
 * ⚠️ 位置由调用方决定：挂在 `NaiStudioTheme` 里面（要 App 的配色），
 * 在开屏 / 轻提示 / 主题扩散那几层**之下**。调用方只在 [isHovering] 为真时才调它。
 *
 * @param language 当前语言（`settings.language`），文案走 [RuntimeText]。
 * @param isHovering 有没有文件悬在窗口上（来自 `FileDropTarget.isHovering`）。
 * @param fileName 悬着的文件名；`null` / 空串时用不带名字的兜底文案。
 */
@Composable
private fun DropHintOverlay(language: String, isHovering: Boolean, fileName: String?) {
    if (!isHovering) return
    val hint = if (fileName.isNullOrBlank()) {
        RuntimeText.text(language, "drop.hint")
    } else {
        RuntimeText.format(language, "drop.hintNamed", mapOf("name" to fileName))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 半透明遮罩：底下的界面还看得见，但一眼就知道"现在是拖放状态"
            //（拖动期间的指针事件归平台拖放那条路，这层不吃点击，也没有点击可言）
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 8.dp,
        ) {
            Text(
                text = hint,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 单实例：第二个实例不再开窗口，把已有窗口带到前台然后自己退出（活 2）
// ---------------------------------------------------------------------------

/** 锁文件名：`%APPDATA%\NAI Studio\instance.lock`（目录复用 [appDir]，与 prefs.json 同一处）。 */
private const val INSTANCE_LOCK_FILE = "instance.lock"

/** `ShowWindow` 的"还原"（`SW_RESTORE`）。 */
private const val SW_RESTORE = 9

/** `WINDOWPLACEMENT.showCmd` 里"最小化"的值（`SW_SHOWMINIMIZED`）。 */
private const val SW_SHOWMINIMIZED = 2

/**
 * 找已有窗口时按顺序试的标题（`FindWindowW` 的第二个参数），最可能命中的排前面。
 *
 * ⚠️ 两条依据：
 *  · 标题来自 `Window(title = …)`：有页面标题时是 `"NAI Studio - xx"`（活 3 之后是 ASCII 连字符），
 *    页面标题还没广播过来时就是光秃秃的 `"NAI Studio"`；
 *  · `FindWindow` **不认通配符** —— 串里的 `*` 是普通字符，所以任务里写的 `"NAI Studio*"`
 *    放在最后只是"照约定试一次"，真正管用的是前两条。
 *    （`FindWindow` 是大小写不敏感、按标题**子串**匹配的，所以 `"NAI Studio"` 通常也能命中
 *     `"NAI Studio - 图库"`。）
 */
private val INSTANCE_WINDOW_TITLES = listOf("NAI Studio -", "NAI Studio", "NAI Studio*")

/**
 * 进程活着期间**必须一直被引用住**的锁（[acquireSingleInstanceLock] 抢到后放这里）。
 *
 * ⚠️ 为什么是顶层 `private var` 而不是局部变量：要求是"锁在进程活着期间一直持有"，
 * 把这个对象放在一个长期存活的引用上最直接 —— 不赌"没人引用了它也还在"。
 * 通道也一起留着：**关掉通道同样会放锁**（`FileLock` 是跟着通道走的）。
 */
private var instanceLock: FileLock? = null
private var instanceLockChannel: RandomAccessFile? = null

/**
 * **抢单实例锁**：`%APPDATA%\NAI Studio\instance.lock` 的独占文件锁。
 *
 * 为什么用文件锁而不是 Windows 命名互斥量：纯 JDK、跨平台，而且**进程一死锁自动就没了**
 *（不会有"上次崩了留下的残留锁"要把用户卡在外面）。
 * `createNewFile()` 先保证文件在（第一次启动时目录/文件都还没有），`tryLock()` 保证
 * "同一时刻只有一个进程进得去"。锁的是 **`FileLock`**，不是"文件存不存在"——
 * 文件一直留着没关系。
 *
 * @return true = 本进程就是第一个实例（继续启动窗口）；false = **已经有实例在跑**（调用方必须退出）。
 *
 * 失败口径（刻意分成两种）：
 *  · `tryLock()` 返回 null → 锁被别人占着 → **false**；
 *  · 其它异常（目录只读 / 被策略拦 / `OverlappingFileLockException`）→ **true**：
 *    单实例是"体验优化"，宁可多开一个窗口，也不能因为锁文件写不出来就让 App **永远打不开**。
 */
private fun acquireSingleInstanceLock(): Boolean = runCatching {
    val file = File(appDir(), INSTANCE_LOCK_FILE)
    file.parentFile?.mkdirs()
    file.createNewFile()
    val channel = RandomAccessFile(file, "rw")
    val lock = channel.channel.tryLock()
    if (lock == null) {
        channel.close()
        false
    } else {
        instanceLock = lock
        instanceLockChannel = channel
        logInfo("SingleInstance", "拿到实例锁：${file.absolutePath}")
        true
    }
}.getOrElse { error ->
    logWarn("SingleInstance", "实例锁没抢成（$error）→ 按第一个实例继续启动")
    true
}

/**
 * 已经有实例在跑时的收尾：**把它的窗口带到前台，然后结束本进程**。
 *
 * 为什么是 `exitProcess(0)` 而不是 Compose 的 `exitApplication()`：这里还在 `main()` 里，
 * `application { }` **还没进**（连窗口都还没建），作用域里那个 `exitApplication` 根本不存在。
 * 第二个实例要的就是"立刻消失"。
 *
 * 拿不到 JNA 类（`NoClassDefFoundError`）/ 非 Windows / 被系统拦 / 压根找不到窗口 ——
 * 一律只是"没有前台激活"，**本进程照样退出**：绝不开第二个窗口，这是这一步的全部目的。
 * 最坏情况是"用户以为点了没反应"，那也比多开一个窗口好。
 */
private fun activateExistingInstanceAndExit(): Nothing {
    runCatching {
        val user32 = User32.INSTANCE
        val hwnd: WinDef.HWND? = INSTANCE_WINDOW_TITLES.firstNotNullOfOrNull { title ->
            user32.FindWindow(null, title)
        }
        if (hwnd == null) {
            logInfo("SingleInstance", "没找到已有窗口（试过：$INSTANCE_WINDOW_TITLES）")
            return@runCatching
        }
        // 最小化的窗口光 `SetForegroundWindow` 是拉不起来的（它还是最小化的）→ 先还原。
        // `GetWindowPlacement` 要先填 `length`（Win32 的规矩），JNA 不会自己补。
        val placement = WinUser.WINDOWPLACEMENT()
        placement.length = placement.size()
        if (
            user32.GetWindowPlacement(hwnd, placement).booleanValue() &&
            placement.showCmd == SW_SHOWMINIMIZED
        ) {
            user32.ShowWindow(hwnd, SW_RESTORE)
        }
        user32.SetForegroundWindow(hwnd)
        logInfo("SingleInstance", "已把已有窗口带到前台，本进程退出")
    }.onFailure { error ->
        logInfo("SingleInstance", "前台激活没做成（$error）→ 仍然退出，不开第二个窗口")
    }
    exitProcess(0)
}
