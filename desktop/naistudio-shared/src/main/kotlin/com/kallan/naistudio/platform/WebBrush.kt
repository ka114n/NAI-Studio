package com.kallan.naistudio.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * **「网页笔刷」的桥**（用户 2026-09-20 口径：「**直接搬，不修**」✓）。
 *
 * 用户要的是：**别动现有那套 Kotlin 笔刷管线**，直接把 `docs/brush-lab.html` 这一页
 * 原样搬进软件里跑 ✓。真的开窗那件事只能在**电脑端**做（要 Chromium + Swing 窗口 ✓，
 * 见 `naistudio-desktop/.../desktop/platform/WebBrushWindow.kt` ✓）——
 * 所以这里只放一个**信箱**：电脑端入口把自己的开窗动作挂进来 ✓，共用面板那颗按钮负责调它 ✓。
 *
 * 为什么不是往 `Platform` 接口上加一个方法 ✗：那要**两端各实现一遍**（手机那份也要动 ✗），
 * 而这件事**只有电脑端有**、手机端天然没有 ✓ —— 信箱式（和 `DesktopExit` 同一个套路 ✓）
 * 更诚实：没挂就是没有，[available] 直接说 false ✓，按钮当场置灰 ✓，不会"点了没反应" ✗。
 */
object WebBrush {
    /**
     * 电脑端装进来的**开窗动作**（null = 这台机器上没有/还没挂上 ✓）。
     *
     * ⚠️ 它会**在 UI 线程上被调用**（按钮的 onClick ✓）；实现里有任何耗时动作（比如
     * CEF 首次初始化要下载运行时）都必须在里面自己切线程 ✗ 不能阻塞界面 ✓。
     */
    var open: (() -> Unit)? = null

    /** 这颗按钮现在能不能点（电脑端挂上了才能 ✓）。 */
    val available: Boolean get() = open != null

    /**
     * **网页画布本体**（第 ㉛ 批 ✓）。
     *
     * 用户 2026-09-20 口径：「我以后的测试**只在高级漫画模式的画布上**进行，不在网页上进行」✓
     * ⇒ 网页那张 canvas 必须**摆进漫画页的画布区**（左边参数面板、右边图层面板都不动 ✓），
     * 而不是盖住整个窗口 ✗。
     * 电脑端把自己的 `SwingPanel { CEF }` 挂到这里 ✓，共用界面负责**摆位置** ✓。
     */
    var canvas: (@Composable (Modifier) -> Unit)? = null

    /** 画布区现在交给网页画布了没有（面板那颗按钮切的 ✓）。 */
    var showCanvas by mutableStateOf(false)

    /** 网页画布挂上了没有（置灰/提示用 ✓）。 */
    val canvasAvailable: Boolean get() = canvas != null
}
