package com.kallan.naistudio.ui

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * **画布背景（三档统一）** —— 用户 2026-09-24：
 * 「**统一画布背景，用漫画模式的格子背景，颜色再深一些**」✓。
 *
 * ## 口径
 *
 *  · **一套**：普通 / 无限画布 / 漫画三档共用这里的底色与格子 ✓（**唯一一处** ✓）——
 *    以前是三套：生图页那档是"主题底色 / 用户自选色"✗、无限画布那档**什么都不画**（露页面底色 ✗）、
 *    漫画那档是浅灰 + 方格 ✓ ⇒ 切档背景就变 ✓（用户看得出来的那种不一致 ✓）；
 *  · **格子**：一格 [CanvasGridCell]（18dp ✓，与漫画那一版逐字相同 ✓），1px 细线 ✓；
 *  · **颜色更深**：底色从原来漫画那版的很浅的灰（`0xFFE3E4E6` ✓）压深到 [CanvasBackdropColor] ✓；
 *  · **线色跟底色走** ✗ 不写死：底色深 ⇒ 线更浅（白 12% ✓）、底色浅 ⇒ 线更深（黑 12% ✓）——
 *    用户自选画布颜色（设置 → 调色板里的「画布」✓）时，格子在深色底上也看得见 ✓。
 *
 * ⚠️ 底色**不跟主题走** ✗（浅色 / 暗色模式都是它 ✓）：这是"画布"不是"界面" ✓
 *（漫画那一版当初就是用户点名的「暗色模式也不变颜色」✓，这里沿用同一条 ✓）。
 */
val CanvasBackdropColor = Color(0xFFBFC3C9)

/** 格子边长 ✓（与漫画那一版逐字相同 ✓）。 */
val CanvasGridCell = 18.dp

/** 参考稿画布区的格子边长（`.canvas-pane` 的 `background-size: 24px 24px`）。 */
val ReferenceGridCell = 24.dp

/**
 * **青蓝玻璃风格**下画布区的底色 = **透明**。
 *
 * 参考稿 `.canvas-pane` 逐字就是 `background-color: transparent` ——
 * 它**不铺自己的底色**，让底下那层页面渐变（径向青 + 径向靛 + 深蓝底）透上来，
 * 画布区于是和四周是**同一片背景**，只在上面多一层 24px 网格。
 *
 * ⚠️ 我第一版在这里取了一个"比页面再深一档"的实色 —— 那是**偏离参考稿**的，
 *    等于又做出一块能和背景分辨的色块（只是从浅灰换成了深蓝），没解决用户说的
 *    「灰色实色」这个问题。改成透明才对。
 */
val ReferenceCanvasColor = Color.Transparent

/** 线色：按底色亮度自动选（深底用浅线 ✓）。 */
fun canvasGridLineColor(base: Color): Color =
    if (base.luminance() < 0.5f) Color(0x1FFFFFFF) else Color(0x22000000)

/**
 * 给一层铺上**画布背景**（底色 + 方格 ✓）。
 *
 * ## 两套风格各画各的（用户 2026-09-27：「画布背景现在是灰色实色，修改」）
 *
 *  · **毛玻璃**：底色 [CanvasBackdropColor]（浅灰）+ **18dp** 格子 + 自动选线色
 *    —— 完全保持用户 2026-09-24 定的口径 ✓，一点没动 ✓；
 *  · **青蓝玻璃**：底色**透明**（参考稿 `.canvas-pane { background-color: transparent }`）
 *    + **24dp** 格子 + 参考稿那个 `--grid` 线色 —— 让页面渐变透上来，
 *    画布区于是和四周是**同一片背景** ✓。
 *
 * ⚠️ **用户自选了画布颜色时**：两套风格都**照常铺那个颜色**（那是用户明确要的 ✓），
 *    格子按当前风格的边长画 ✓（原来那个 `base` 参数就是干这个的 ✓）。
 *
 * ⚠️ 透明底**不能**靠"按亮度自动选线色"—— `Color.Transparent` 的 luminance 是 0，
 *    会被判成深底从而画出浅色线，而透明底下面其实是页面渐变。
 *    所以青蓝玻璃那一档**显式给线色** ✓。
 */
@Composable
fun Modifier.canvasBackdrop(
    base: Color = CanvasBackdropColor,
    drawGrid: Boolean = true,
): Modifier {
    val skin = LocalNaiSkin.current
    val custom = base != CanvasBackdropColor && base != ReferenceCanvasColor
    val darkNow = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // 用户自选了颜色 ⇒ 两套风格都铺它；否则按风格给默认底
    val effectiveBase = when {
        custom -> base
        skin == NaiSkin.Reference -> ReferenceCanvasColor
        else -> base
    }
    // 格子边长与线色跟着风格走；自选色那档仍按底色亮度自动选线
    val cell = if (skin == NaiSkin.Reference && !custom) ReferenceGridCell else CanvasGridCell
    val line = when {
        custom -> canvasGridLineColor(effectiveBase)
        skin == NaiSkin.Reference -> if (darkNow) ReferenceGridDark else ReferenceGridLight
        else -> canvasGridLineColor(effectiveBase)
    }
    return this
        .background(effectiveBase)
        .drawBehind {
            if (!drawGrid) return@drawBehind
            val step = cell.toPx()
            if (step <= 0f) return@drawBehind
            var x = 0f
            while (x <= size.width) {
                drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }
}

/** 参考稿 `--grid`：深色 `rgba(172,214,242,.055)` / 浅色 `rgba(40,113,150,.07)`。 */
private val ReferenceGridDark = Color(0x0EACD6F2)
private val ReferenceGridLight = Color(0x12307196)

/**
 * 画布底色的**实际取值** ✓：用户自选的画布颜色优先 ✓（设置 → 调色板 → 「画布」✓），
 * 没设 / 设坏了就用**当前外观风格**的默认底 ✓。
 *
 * ⚠️ 自选色也要按当前深浅**换算一档** ✗（和别处 `adaptForMode` 同一个理由 ✓）：
 *    用户存的是"一份值、两套模式共用" ✓，暗色模式下不换算就会亮得刺眼 ✓。
 *
 * ## 2026-09-27 改动（用户：「画布背景现在是灰色实色，修改」）
 *
 * 原来没设自选色时**一律**返回写死的 [CanvasBackdropColor]（浅灰 `#BFC3C9`）。
 * 切到参考稿那套**青蓝玻璃**之后，四周全是深蓝半透明面板，中间一块浅灰画布就"跳"出来了
 * —— 参考稿里画布是**深底 + 24px 网格**，跟面板是一体的。
 *
 * 所以现在按风格分流：
 *  · 毛玻璃风格 → 仍是 [CanvasBackdropColor]（用户 2026-09-24 定的口径，**一点没动** ✓）；
 *  · 青蓝玻璃风格 → [ReferenceCanvasDark] / [ReferenceCanvasLight]，与那套背景同源 ✓。
 *
 * ⚠️ **用户自选的画布颜色在两套风格下都优先** ✓ —— 那是用户明确要的，
 *    不该被风格开关顶掉 ✓。
 */
@Composable
fun canvasBaseColor(settingsHex: String?): Color {
    val custom = parseHexColor(settingsHex)
    if (custom != null) {
        val darkNow = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        return adaptForMode(custom, darkNow)
    }
    return when (LocalNaiSkin.current) {
        NaiSkin.Glass -> CanvasBackdropColor
        // 参考稿：画布**不铺底色**，让页面渐变透上来（`.canvas-pane { background-color: transparent }`）
        NaiSkin.Reference -> ReferenceCanvasColor
    }
}
