package com.kallan.naistudio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * **自绘标题栏要用的那一点"窗口能力"**（电脑端提供；手机端用默认值 = 什么都没有）。
 *
 * ## 为什么需要它
 *
 * 用户 2026-09-19：「（菜单）放在 logo 旁边，不是做一个顶栏」—— 菜单要和 App 图标同处
 * **窗口标题栏**那一行。系统标题栏是 DWM 画的，塞不进 Compose 内容，所以只能把系统标题栏关掉
 * （`Window(decoration = WindowDecoration.Undecorated)`）、自己画一条：
 * `[logo] 文件 编辑 视图 工具 窗口 帮助 … [余额] [— □ ×]`。
 *
 * 那条标题栏画在**共用树**（`StudioShell` 的 `topBar`）里 —— 菜单项要调 `AppState` / `UiHost`，
 * 搬到桌面入口去就得把整套菜单再抄一遍。但"拖窗口 / 最小化 / 最大化 / 关闭"是 AWT 的事，
 * 共用树里不能写（不能 import AWT）。于是中间放这一个 `CompositionLocal` 信箱：
 *  · 电脑入口 provide 真实现（见桌面 `Main.kt` 的 `rememberWindowChrome`）；
 *  · 手机（或没 provide 的场合）拿到默认值 —— 标题栏不拖、右端不画那三颗按钮，
 *    界面照旧（手机那套是系统状态栏 + 手势，没有"窗口按钮"这回事）。
 */
class WindowChrome(
    /** 拖着这条标题栏 = 拖窗口（手机是空 Modifier）。 */
    val titleBarDrag: Modifier = Modifier,
    /** 窗口现在是不是最大化 —— 决定第 2 颗按钮画"最大化"还是"还原"。 */
    val maximized: Boolean = false,
    val onMinimize: (() -> Unit)? = null,
    val onToggleMaximize: (() -> Unit)? = null,
    val onClose: (() -> Unit)? = null,
) {
    /** 有没有"窗口按钮"这一套（手机 = 没有，标题栏右端就不画）。 */
    val hasButtons: Boolean
        get() = onMinimize != null || onToggleMaximize != null || onClose != null
}

val LocalWindowChrome = staticCompositionLocalOf { WindowChrome() }

/** 标题栏上的一颗窗口按钮多宽（照 Windows 那三颗的比例：宽一点、贴着右端）。 */
private val WINDOW_BUTTON_WIDTH = 46.dp

/**
 * 标题栏右端那三颗窗口按钮：**最小化 / 最大化-还原 / 关闭**。
 *
 * 图形全部用 `Canvas` 现画（两点一线、一个方框），不引图标库：
 * material-icons-core 里没有"最小化/最大化"这两个（`Close` 倒是有，但为了三颗大小一致也一起画）。
 * 悬停底色跟 Windows 一个口径：前两颗淡灰，**关闭那颗变红**。
 */
@Composable
internal fun WindowControlButtons(chrome: WindowChrome) {
    if (!chrome.hasButtons) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        chrome.onMinimize?.let { action ->
            WindowControlButton(action = action, close = false) { tint ->
                Canvas(Modifier.size(10.dp, 10.dp)) {
                    drawLine(tint, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1f)
                }
            }
        }
        chrome.onToggleMaximize?.let { action ->
            WindowControlButton(action = action, close = false) { tint ->
                Canvas(Modifier.size(10.dp, 10.dp)) {
                    if (chrome.maximized) {
                        // 还原：两个错开的小方框
                        drawRect(
                            tint,
                            topLeft = Offset(size.width * 0.28f, 0f),
                            size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.72f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1f),
                        )
                        drawRect(
                            tint,
                            topLeft = Offset(0f, size.height * 0.28f),
                            size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.72f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1f),
                        )
                    } else {
                        drawRect(
                            tint,
                            topLeft = Offset.Zero,
                            size = androidx.compose.ui.geometry.Size(size.width, size.height),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(1f),
                        )
                    }
                }
            }
        }
        chrome.onClose?.let { action ->
            WindowControlButton(action = action, close = true) { tint ->
                Canvas(Modifier.size(10.dp, 10.dp)) {
                    drawLine(
                        tint,
                        Offset(0f, 0f),
                        Offset(size.width, size.height),
                        1.2f,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        tint,
                        Offset(size.width, 0f),
                        Offset(0f, size.height),
                        1.2f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

/** 一颗窗口按钮：`46×标题栏高`，悬停给底色（关闭那颗给红底白叉）。 */
@Composable
private fun WindowControlButton(
    action: () -> Unit,
    close: Boolean,
    glyph: @Composable (Color) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    val background = when {
        !hovered -> Color.Transparent
        close -> scheme.error
        else -> scheme.onSurface.copy(alpha = 0.10f)
    }
    val tint = if (hovered && close) scheme.onError else scheme.onSurface
    Box(
        Modifier
            .width(WINDOW_BUTTON_WIDTH)
            .height(MENU_TITLE_BAR_HEIGHT)
            .background(background)
            .hoverable(source)
            .clickable(
                interactionSource = source,
                indication = null,
                onClick = action,
            ),
        contentAlignment = Alignment.Center,
    ) {
        glyph(tint)
    }
}

/** 自绘标题栏那一行的高度（和 `StudioShell` 里那条菜单条共用同一个值）。 */
internal val MENU_TITLE_BAR_HEIGHT = 30.dp
