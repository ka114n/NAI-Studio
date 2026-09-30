package com.kallan.naistudio.desktop.platform

import com.kallan.naistudio.platform.logInfo
import com.kallan.naistudio.platform.logWarn
import com.sun.jna.CallbackReference
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Window

/**
 * **把系统标题栏"借"来画我们自己的菜单**（用户 2026-09-19 选的方案 C）。
 *
 * ## 想要的效果
 *
 * 一条**真正的系统标题栏**（系统自己画的最小化/最大化/关闭三颗按钮、阴影、圆角、
 * 拖到屏幕边的贴靠都在），而**图标旁边那一块是我们画的**：
 * `[AppLogo] 文件 编辑 视图 工具 窗口 帮助  NAI Studio — 生图 … [— □ × 系统按钮]`。
 * 参考实现是 VS Code / Chrome / Windows Terminal / 用户截图里那个 DeepSeek Harness。
 *
 * ## 怎么做到
 *
 * 系统标题栏是 **非客户区（NC）**，Compose 画不进去。唯一的办法是把它"并进客户区"：
 * 挂一个窗口过程（WndProc）钩子，处理两条消息：
 *
 * | 消息 | 处理 | 为什么 |
 * | --- | --- | --- |
 * | `WM_NCCALCSIZE` | 先让系统按样式算一遍，再把客户区的**上边**往上扩 [SM_CYCAPTION] 那么多 | 于是"标题栏那一条"变成客户区，Compose 能在里面画；左右下的**边框仍是 NC**（缩放边框不动） |
 * | `WM_NCHITTEST` | 系统答 `HTCAPTION`（标题栏）→ 我们改答 `HTCLIENT` | 标题栏那一条现在归 Compose 收鼠标。⚠️ **只改 HTCAPTION**：三颗系统按钮的 `HTCLOSE/HTMINBUTTON/HTMAXBUTTON`、四边的 `HTLEFT/…` 原样放行，所以按钮和缩放边框照旧是系统在管 |
 *
 * 拖动窗口因此改由 Compose 侧搬 `setLocation`（见 `WindowChrome.titleBarDrag`）——
 * 代价是**拖到屏幕边贴靠仍然没有**（那要 `HTCAPTION` 才成立），其余（系统按钮、阴影、
 * 圆角、边缘缩放）都是真的。
 *
 * ## ⚠️ 为什么用 JNA 挂回调而不是更"正规"的办法
 *
 * Java/AWT 没有"改窗口过程"的公开 API；CMP 也没有"自绘标题栏 + 保留系统按钮"的开关
 *（1.8.2 只有 `WindowDecoration.SystemDefault`，没有自定义实现）。JNA 的 `StdCallCallback`
 * 是唯一能在 JVM 里挂 WndProc 的路子。注意两条：
 *  1. **回调对象必须强引用住**（[callbackRef]）—— 被 GC 掉之后窗口消息进来就会踩空指针；
 *  2. 钩子挂失败/异常**绝不能把窗口搞死**：整段包在 `runCatching` 里，失败就返回 false，
 *     界面退化成"系统标题栏 + 我们那条菜单行在它下面"（§107 的样子），仍然可用。
 */
object DesktopTitleBarHook {

    // ---- Win32 常量 ----

    private const val GWL_WNDPROC = -4
    private const val WM_NCCALCSIZE = 0x0083
    private const val WM_NCHITTEST = 0x0084
    private const val WM_NCLBUTTONDOWN = 0x00A1

    /** `GetWindowLongPtr(GWL_STYLE)` 里的"已最大化"位。 */
    private const val GWL_STYLE = -16
    private const val WS_MAXIMIZE = 0x01000000

    /** `MonitorFromWindow` 的"最近的显示器"。 */
    private const val MONITOR_DEFAULTTONEAREST = 2

    /** `GetSystemMetrics` 的"标题栏文字高度"，也就是要把客户区往上扩多少。 */
    private const val SM_CYCAPTION = 4

    private const val HTCLIENT = 1
    private const val HTCAPTION = 2

    /** "请让系统接管这次拖动"的自定义消息（`WM_APP + 1`）。 */
    private const val WM_APP_START_MOVE = 0x8000 + 1

    private const val SWP_NOSIZE = 0x0001
    private const val SWP_NOMOVE = 0x0002
    private const val SWP_NOZORDER = 0x0004
    private const val SWP_NOACTIVATE = 0x0010
    private const val SWP_FRAMECHANGED = 0x0020

    /** 窗口过程签名（`LRESULT CALLBACK WndProc(HWND, UINT, WPARAM, LPARAM)`）。 */
    interface WndProc : StdCallLibrary.StdCallCallback {
        fun callback(
            hwnd: WinDef.HWND,
            msg: Int,
            wParam: WinDef.WPARAM,
            lParam: WinDef.LPARAM,
        ): WinDef.LRESULT
    }

    /**
     * JNA 的 `User32` 里**没有** `ReleaseCapture`（5.14.0 实测），单独声明一个最小的。
     *
     * 它必须和"发起拖动"在**同一个线程**调用（鼠标捕获按线程算），所以整段放在
     * `WM_APP_START_MOVE` 的处理里 —— 那是窗口所在线程。
     */
    private interface User32Extra : StdCallLibrary {
        fun ReleaseCapture(): Boolean

        companion object {
            val INSTANCE: User32Extra? by lazy {
                runCatching {
                    Native.load("user32", User32Extra::class.java, W32APIOptions.DEFAULT_OPTIONS)
                }.getOrNull()
            }
        }
    }

    /** `WM_NCCALCSIZE` 带 `wParam=TRUE` 时 `lParam` 指向它（winuser.h 的 `NCCALCSIZE_PARAMS`）。 */
    @Structure.FieldOrder("rgrc", "lppos")
    class NcCalcSizeParams(pointer: Pointer) : Structure(pointer) {
        @JvmField
        var rgrc: Array<WinDef.RECT> = arrayOf(WinDef.RECT(), WinDef.RECT(), WinDef.RECT())

        @JvmField
        var lppos: Pointer? = null
    }

    /** 强引用（见类注释第 1 条）。 */
    @Volatile
    private var callbackRef: WndProc? = null

    @Volatile
    private var oldProc: Pointer? = null

    /**
     * **请求让 Windows 接管这次拖动**（自绘标题栏按下时调）。
     *
     * 走 `PostMessage(WM_APP_START_MOVE)` 而不是直接在这儿调 —— 原因见回调里那段的注释
     *（鼠标捕获按线程算，必须回到窗口所在线程里 `ReleaseCapture`）。
     *
     * @return 请求发出去了没有；false = 钩子没装上（调用方该退回"自己搬 setLocation"那条路）。
     */
    fun requestWindowMove(window: Window): Boolean {
        if (callbackRef == null) return false
        val hwnd = runCatching { Native.getWindowPointer(window) }.getOrNull() ?: return false
        return runCatching {
            User32.INSTANCE.PostMessage(
                WinDef.HWND(hwnd),
                WM_APP_START_MOVE,
                WinDef.WPARAM(0),
                WinDef.LPARAM(0),
            )
            true
        }.getOrDefault(false)
    }

    /**
     * 给 [window] 挂上钩子。
     *
     * @return 挂上了没有；false = 这次没成功（界面照旧可用，只是菜单行会落在系统标题栏下面）。
     */
    fun install(window: Window): Boolean {
        val user32 = runCatching { User32.INSTANCE }.getOrNull()
        if (user32 == null) {
            logWarn("TitleBar", "user32 拿不到，自绘标题栏放弃（菜单行会落在系统标题栏下面）")
            return false
        }
        if (callbackRef != null) return true // 已经挂过（同一进程只挂一次）

        return runCatching {
            val hwnd = WinDef.HWND(Native.getWindowPointer(window))

            val callback = object : WndProc {
                override fun callback(
                    hwnd: WinDef.HWND,
                    msg: Int,
                    wParam: WinDef.WPARAM,
                    lParam: WinDef.LPARAM,
                ): WinDef.LRESULT {
                    val previous = oldProc ?: return User32.INSTANCE
                        .DefWindowProc(hwnd, msg, wParam, lParam)

                    // ---- ① 把"标题栏那一条"并进客户区（只动上边，边框留给系统）----
                    if (msg == WM_NCCALCSIZE && wParam.toInt() != 0) {
                        val params = NcCalcSizeParams(Pointer(lParam.toLong()))
                        // ⚠️ **调用前** `rgrc[0]` 是"提议的新**窗口**矩形"（MSDN 原文），
                        // 调用后才被改成"提议的**客户区**"。所以先把窗口顶边记下来。
                        params.read()
                        val windowTop = params.rgrc[0].top

                        User32.INSTANCE.CallWindowProc(previous, hwnd, msg, wParam, lParam)
                        params.read()

                        val maximized = (User32.INSTANCE
                            .GetWindowLongPtr(hwnd, GWL_STYLE).toLong() and WS_MAXIMIZE.toLong()) != 0L
                        if (maximized) {
                            // 最大化：客户区 = **该显示器的工作区**。
                            // 最大化窗口的窗口矩形是"屏幕 + 四周 8px 隐形边框"（实测 `-8,-8 / 2576×1408`），
                            // 系统给的客户区已经等于工作区，再往上扩 23px 就会让客户区顶边跑到屏幕外、
                            // 标题栏最上面被切掉（用户 2026-09-19：「顶部突出的窗口收回」）。
                            val monitor = User32.INSTANCE.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST)
                            val info = WinUser.MONITORINFO()
                            info.cbSize = info.size()
                            if (User32.INSTANCE.GetMonitorInfo(monitor, info).booleanValue()) {
                                params.rgrc[0].left = info.rcWork.left
                                params.rgrc[0].top = info.rcWork.top
                                params.rgrc[0].right = info.rcWork.right
                                params.rgrc[0].bottom = info.rcWork.bottom
                                params.write()
                            }
                        } else {
                            // 普通窗口：客户区上边 = **窗口矩形顶边**。
                            //
                            // 原来是"减去 `SM_CYCAPTION`(23px)"—— 那只够跨过标题栏，
                            // 顶上那圈**隐形缩放边框**（实测还有 8px）留在外面由 DWM 按
                            // `DWMWA_BORDER_COLOR` 上色，浅色主题下就是用户截图里那条**白边**
                            //（用户 2026-09-19：「还在，小窗状态的」）。
                            // 直接对齐到窗口顶边，那条带子就没了（代价：顶边不再能拖边框缩放，
                            // 左右下三条边照旧 —— 顶上那一条正好在我们的标题栏里，本来就该是"拖动窗口"）。
                            params.rgrc[0].top = windowTop
                            params.write()
                        }
                        return WinDef.LRESULT(0)
                    }

                    // ---- ② 标题栏那一条改判"客户区"，让 Compose 收得到鼠标 ----
                    // 注意只拦 HTCAPTION：系统那三颗按钮（HTCLOSE/HTMINBUTTON/HTMAXBUTTON）
                    // 与四边缩放（HTLEFT/…）一律原样返回。
                    if (msg == WM_NCHITTEST) {
                        val hit = User32.INSTANCE.CallWindowProc(previous, hwnd, msg, wParam, lParam)
                        return if (hit.toInt() == HTCAPTION) {
                            WinDef.LRESULT(HTCLIENT.toLong())
                        } else {
                            hit
                        }
                    }

                    // ---- ③ "让系统接管这次拖动"（用户 2026-09-19：「拖动这个标题栏窗口会跳动」）----
                    //
                    // 自己搬 `setLocation` 一定抖：窗口跟着鼠标走之后，Compose 报的位移是
                    // **相对正在移动的窗口**的，下一帧又把这份位移喂回去 → 抖/漂。
                    // 正解是让 Windows 自己拖（它按屏幕坐标算，还顺带把贴靠/甩边/拖最大化窗口
                    // 自动还原全带回来）。
                    //
                    // ⚠️ 这活儿**必须在这个线程**干：鼠标捕获是按线程算的，在别处 `ReleaseCapture`
                    // 放掉的不是同一个捕获，系统不会接管。所以外面用 `PostMessage` 把请求投进来。
                    if (msg == WM_APP_START_MOVE) {
                        User32Extra.INSTANCE?.ReleaseCapture()
                        User32.INSTANCE.SendMessage(
                            hwnd,
                            WM_NCLBUTTONDOWN,
                            WinDef.WPARAM(HTCAPTION.toLong()),
                            WinDef.LPARAM(0),
                        )
                        return WinDef.LRESULT(0)
                    }

                    return User32.INSTANCE.CallWindowProc(previous, hwnd, msg, wParam, lParam)
                }
            }

            val pointer = CallbackReference.getFunctionPointer(callback)
            val replaced = user32.SetWindowLongPtr(hwnd, GWL_WNDPROC, pointer)
            callbackRef = callback
            oldProc = replaced
            logInfo("TitleBar", "WndProc 钩子装好了 old=${replaced}")

            // ⚠️ **必须踢一次让系统重算非客户区**：窗口的客户区是**创建时**算好的，
            // 那时钩子还没装上（`LaunchedEffect` 才跑），所以 `WM_NCCALCSIZE` 一次都没来过
            //（实测：日志里一条 NCCALCSIZE 都没有，客户区仍旧少一条标题栏）。
            // `SWP_FRAMECHANGED` 就是干这个的。
            user32.SetWindowPos(
                hwnd,
                null,
                0,
                0,
                0,
                0,
                SWP_NOMOVE or SWP_NOSIZE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED,
            )
            true
        }.getOrElse {
            logWarn("TitleBar", "WndProc 钩子没挂上：${it.message}")
            false
        }
    }
}
