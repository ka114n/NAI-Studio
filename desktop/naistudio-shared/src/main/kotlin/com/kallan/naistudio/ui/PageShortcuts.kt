package com.kallan.naistudio.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * **页面快捷键的信箱 + 当前页广播**（电脑端）。
 *
 * ## 为什么需要这么一个"信箱"
 *
 * 电脑上接全局按键只有一条稳的路：`Window(onKeyEvent = …)` —— 它挂在**入口**
 * （`application {}` 作用域）。可"当前在第几页"住在外壳 `StudioShell` 里，
 * 入口够不着。两边一上一下，中间就得有个信箱：
 * 入口往里发号（[goToPage]），外壳往里登记处理函数（[register]）与**广播当前页**（[notifyPageChanged]）。
 * 电脑端的窗口标题（`NAI Studio — 图库`）就是这么来的 —— 任务栏悬停、Alt+Tab 都能看出在哪一页。
 *
 * ## 为什么不把"当前页"塞进 AppState
 *
 * 那是最省事的做法，但它会**改变手机版的行为**：手机冷启动是刻意停在文生图页的
 * （开屏期间要预热那一页的抽屉，见 `SplashOverlay` 的注释），"记住上次在哪页"会破坏这套设计。
 * 电脑要的是"按 Ctrl+1 就跳过去 + 标题显示当前页"，不是"记住"，所以信箱比全局状态更合适。
 *
 * ## 用法
 *
 * · 入口：`CompositionLocalProvider(LocalPageShortcuts provides shortcuts)`（**包住 `Window` 调用**，
 *   这样 `onKeyEvent` 的 lambda 才能捕获到它），按键时调 [PageShortcuts.goToPage]，
 *   并用 [PageShortcuts.onPageChanged] 收标题；
 * · 外壳：`DisposableEffect` 里 `register { … }`；页面（或工具页里选中的工具）一变就调
 *   [PageShortcuts.notifyPageChanged]。
 *
 * 没人登记时 [PageShortcuts.goToPage] 什么也不做 —— 不会崩。
 */
class PageShortcuts {
    private var handler: ((Int) -> Unit)? = null
    private var pageListener: ((String) -> Unit)? = null
    var currentPage: Int = 0
        private set

    /** 外壳登记"怎么切页"；传 null 注销。 */
    fun register(handler: ((Int) -> Unit)?) {
        this.handler = handler
    }

    /** 入口调用：切到第 [index] 页。 */
    fun goToPage(index: Int) {
        handler?.invoke(index)
    }

    /** 有没有人登记（入口可以据此忽略按键，别把事件吞掉）。 */
    val isAvailable: Boolean get() = handler != null

    /** 入口登记"当前页变了要做什么"（电脑端用来改窗口标题）；传 null 注销。 */
    fun onPageChanged(listener: ((String) -> Unit)?) {
        pageListener = listener
    }

    /** 外壳调用：当前页标题变了（切换页面、或在工具页里换了工具）。 */
    fun notifyPageChanged(title: String, index: Int = currentPage) {
        currentPage = index
        pageListener?.invoke(title)
    }
}

val LocalPageShortcuts = staticCompositionLocalOf { PageShortcuts() }

/**
 * **画布编辑器的键位信箱**（电脑端）。
 *
 * 和 [PageShortcuts] 同一个套路、同一个理由：接键只能在入口的 `Window(onKeyEvent = …)` 上接，
 * 而"编辑器开着没开着、当前是什么工具"住在界面里 —— 中间需要一个信箱。
 *
 * 与 [PageShortcuts] 的两点不同：
 *  1. 这里传的是**原始按键事件**（编辑器要认 `b` / `e` / `[` / `]` 这些普通键，
 *     不是 Ctrl+数字那种组合），由编辑器自己判断、自己决定吃不吃；
 *  2. **只有编辑器被组合着的时候才登记**（`DisposableEffect` 里注册、关掉时注销），
 *     所以编辑器没开时这些字母键照旧是打字。
 *
 * 用窗口的 `onKeyEvent`（**不是** `onPreviewKeyEvent`）：焦点节点先过一遍，
 * 输入框（比如笔刷大小那个数值框）会把字符键消费掉，于是往框里打字不会被抢走。
 */
class EditorShortcuts {
    private var handler: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null

    /** 编辑器登记处理函数；传 null 注销。 */
    fun register(handler: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)?) {
        this.handler = handler
    }

    /** 有没有人在用（入口可以据此少走一趟）。 */
    val isActive: Boolean get() = handler != null

    /** 入口调用：把按键交给编辑器。返回 true = 已被吃掉，入口别再处理。 */
    fun dispatch(event: androidx.compose.ui.input.key.KeyEvent): Boolean =
        handler?.invoke(event) == true
}

val LocalEditorShortcuts = staticCompositionLocalOf { EditorShortcuts() }

/**
 * **窗口标题栏菜单的快捷键信箱**（用户 2026-09-19：菜单要标快捷键，标了就得真的能用）。
 *
 * 和 [PageShortcuts] / [EditorShortcuts] 同一个套路、同一个理由：接键只能在入口的
 * `Window(onKeyEvent = …)` 上接，而"动作"住在 `StudioShell` 的菜单条里（那儿才有
 * 文件选择器、`AppState`、`LocalPageShortcuts`）—— 中间需要一个信箱。
 *
 * 这里只传一个**命令 id**（`"import"` / `"presets"` / `"sidebar"` …），由外壳那边解释；
 * 外壳没登记（比如还在开屏）时返回 false，入口就当这个键没人认。
 */
class ShellCommands {
    private var handler: ((String) -> Boolean)? = null

    /** 外壳登记"收到命令 id 干什么"；传 null 注销。 */
    fun register(handler: ((String) -> Boolean)?) {
        this.handler = handler
    }

    /** 入口调用：派发一条命令。返回 true = 处理掉了。 */
    fun dispatch(id: String): Boolean = handler?.invoke(id) == true
}

val LocalShellCommands = staticCompositionLocalOf { ShellCommands() }

/** 画布视图命令的 id：把预览图**摆回 1 倍 + 居中**（见 [CanvasViewCommands]）。 */
const val CANVAS_CMD_RESET_PLACEMENT = "resetPlacement"
const val CANVAS_CMD_ZOOM_IN = "zoomIn"
const val CANVAS_CMD_ZOOM_OUT = "zoomOut"

/**
 * **画布摆放（平移 + 缩放）的命令信箱**（用户 2026-09-26：「在视图里加上个重置图片位置的按钮」）。
 *
 * 和 [ShellCommands] 同一个套路、同一个理由：命令的**入口在菜单**（`StudioShell` 的「视图」菜单），
 * 而 `previewScale` / `previewOffset` 这两个状态**住在生成页的画布里**（`GenerateScreen.PreviewCard`
 * 的宽屏分支 —— 它们要跟着那块画布的实际尺寸算缩放锚点、平移夹取，搬到外壳会牵动整棵布局）。
 * 两边一上一下，中间就再要一个信箱。
 *
 * 方向与 [ShellCommands] 相反：那边是"入口 → 外壳"，这里是"外壳 → 页面"。
 * 页面登记（`register`）、外壳发号（[resetPlacement]）；没人登记时返回 false、什么也不做，
 * 不会崩（比如用户停在图库页时点这一项）。
 *
 * ⚠️ 为什么**不**放进 `AppState`：那等于把"画布摆放"抬到全局状态层（见上面
 * [PageShortcuts] 里"为什么不把当前页塞进 AppState"那条同样的理由）——
 * 这两个值只在生成页画布内部有意义，抬上去只会多一份要同步的副本。
 */
class CanvasViewCommands {
    private var handler: ((String) -> Boolean)? = null

    /** 画布登记"收到命令 id 干什么"；传 null 注销。 */
    fun register(handler: ((String) -> Boolean)?) {
        this.handler = handler
    }

    /** 外壳调用：派发一条命令。返回 true = 有人处理了。 */
    fun dispatch(id: String): Boolean = handler?.invoke(id) == true

    /** 外壳调用：「视图 → 重置图片位置」。返回 true = 画布接住了。 */
    fun resetPlacement(): Boolean = dispatch(CANVAS_CMD_RESET_PLACEMENT)
    fun zoomIn(): Boolean = dispatch(CANVAS_CMD_ZOOM_IN)
    fun zoomOut(): Boolean = dispatch(CANVAS_CMD_ZOOM_OUT)
}

val LocalCanvasViewCommands = staticCompositionLocalOf { CanvasViewCommands() }
