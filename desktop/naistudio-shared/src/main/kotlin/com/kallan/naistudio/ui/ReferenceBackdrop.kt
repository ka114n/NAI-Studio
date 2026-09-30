package com.kallan.naistudio.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * **参考稿那层底**（用户 2026-09-27：「参考这个 HTML，网页做的 UI 做电脑线 UI」）。
 *
 * `generate-screen.html` 最后生效那一层的 `body` 背景，是**两团径向渐变 + 一层纯色底**，
 * 外加画布区一层 **24px 网格**。这两样是它观感的一半 —— 面板只是半透明的话，
 * 没有这层底就只剩一片灰，看不出是参考稿那套。
 *
 * ```css
 * background: radial-gradient(ellipse at 0% 60%,   rgba(0,175,220,.16), transparent 45%),
 *             radial-gradient(ellipse at 85% 0%,   rgba(92,110,247,.13), transparent 50%),
 *             var(--bg);
 * ```
 *
 * ## 为什么不复用 [glassBackdrop]
 *
 * [glassBackdrop] 画的是"**用户自己的背景图**按窗口对齐重画一遍"（要位图、要 `positionInWindow`
 * 对齐）。而这一层是**纯矢量的渐变**，跟窗口尺寸对齐没关系 —— 它只是"铺满我这一块"。
 * 两件事分开，代码和心智都更简单。
 *
 * ## ⚠️ 只在参考稿风格下调用
 *
 * 玻璃风格下页面底是主题色（可能还透出壁纸），**不该**被这层渐变顶掉。
 * 所以调用点要先判断 `LocalNaiSkin.current == NaiSkin.Reference`。
 */
fun Modifier.referenceBackdrop(dark: Boolean, palette: String = "accent"): Modifier = drawBehind {
    val bg = if (dark) REF_BG_DARK else REF_BG_LIGHT
    val (glowPrimary, glowSecondary) = referenceGlowColors(palette)
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return@drawBehind

    // ① 纯色底
    drawRect(color = bg)

    // ② 左下一团青（`at 0% 60%`，半径 45%）
    //    RadialGradient 的 center 是"整块的相对坐标"，radius 相对**较短边** ——
    //    CSS 那个 45% 是相对渐变盒的对角尺度，这里用短边近似（观感一致，数值不必逐像素对）。
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                glowPrimary.copy(alpha = if (dark) 0.16f else 0.12f),
                Color.Transparent,
            ),
            center = Offset(x = 0f, y = h * 0.6f),
            radius = maxOf(w, h) * 0.45f,
        ),
    )

    // ③ 右上一团靛（`at 85% 0%`，半径 50%）
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                glowSecondary.copy(alpha = if (dark) 0.13f else 0.10f),
                Color.Transparent,
            ),
            center = Offset(x = w * 0.85f, y = 0f),
            radius = maxOf(w, h) * 0.5f,
        ),
    )
}

/**
 * **画布区那层 24px 网格**（参考稿 `.canvas-pane` 的 `background-image`）。
 *
 * ```css
 * background-image: linear-gradient(var(--grid) 1px, transparent 1px),
 *                   linear-gradient(90deg, var(--grid) 1px, transparent 1px);
 * background-size: 24px 24px;
 * ```
 *
 * 就是"横竖各画 1px 细线、每 24px 一根"。参考稿里它是**画布区独有**的（参数栏没有），
 * 所以只在画布那块调用。
 *
 * ⚠️ 网格线用极低的透明度（深色 `rgba(172,214,242,.055)`）：它是"底纹"不是"内容"，
 *    亮一点点就会跟画布里的图抢注意力。
 */
fun Modifier.referenceGrid(dark: Boolean, palette: String = "accent"): Modifier = drawBehind {
    val (primary, _) = referenceGlowColors(palette)
    val line = if (palette == "accent" || palette == "glass_cyan") {
        if (dark) GRID_DARK else GRID_LIGHT
    } else {
        primary.copy(alpha = if (dark) 0.055f else 0.07f)
    }
    val step = 24.dp.toPx()
    if (step <= 0f) return@drawBehind

    var x = 0f
    while (x <= size.width) {
        drawLine(
            color = line,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f,
        )
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(
            color = line,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
        y += step
    }
}

private fun referenceGlowColors(palette: String): Pair<Color, Color> = when (palette) {
    "glass_violet", "violet" -> Color(0xFFAA83DD) to Color(0xFF719FD5)
    "glass_emerald", "forest" -> Color(0xFF60B69A) to Color(0xFF85CAD0)
    "glass_rose", "sepia" -> Color(0xFFDD95AE) to Color(0xFFC0AAF1)
    "glass_cyan", "accent", "classic", "bluewhite", "novelai" ->
        Color(0xFF00AFDC) to Color(0xFF5C6EF7)
    else -> Color(0xFF00AFDC) to Color(0xFF5C6EF7)
}

// ---------------------------------------------------------------- 参考稿原值

/** `--bg`：深色 `#0c1724` / 浅色 `#e9f2f8`。 */
private val REF_BG_DARK = Color(0xFF0C1724)
private val REF_BG_LIGHT = Color(0xFFE9F2F8)

/** `--grid`：深色 `rgba(172,214,242,.055)` / 浅色 `rgba(40,113,150,.07)`。 */
private val GRID_DARK = Color(0x0EACD6F2)
private val GRID_LIGHT = Color(0x12307196)
