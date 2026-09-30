package com.kallan.naistudio.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 网页 v6（`20260929-phone-ui-web-v6`）的 `--run-*` / `--cv-*` 两组变量：
 * 生成按钮渐变（= 电脑线 MainRunButton）+ 生图页画布背景（= 电脑线 referenceBackdrop + referenceGrid）。
 * 冰川用电脑线原值，其余五套按配色换色相，暗 / 浅各一组。
 */
data class RunSkin(
    val runA: Color,
    val runB: Color,
    val runInk: Color,
    val runGlow: Color,
    val cvBg: Color,
    val g1: Color,
    val g2: Color,
    val grid: Color,
)

private fun rgb(r: Int, g: Int, b: Int, a: Float) = Color(r, g, b).copy(alpha = a)

fun RefTokens.runSkin(): RunSkin {
    val a1 = if (dark) .16f else .12f
    val a2 = if (dark) .13f else .10f
    return when (scheme) {
        "graphite" -> if (dark) RunSkin(Color(0xFF8FC0FF), Color(0xFF6E8EFF), Color(0xFF081B39), rgb(110, 142, 255, .2f), Color(0xFF101215), rgb(134, 160, 200, a1), rgb(110, 142, 255, a2), rgb(200, 210, 225, .05f))
        else RunSkin(Color(0xFF3A6FD8), Color(0xFF2B4FBE), Color.White, rgb(47, 98, 200, .18f), Color(0xFFEEF0F2), rgb(120, 140, 170, a1), rgb(47, 98, 200, a2), rgb(60, 70, 85, .07f))
        "violet" -> if (dark) RunSkin(Color(0xFFC3A4FF), Color(0xFF8E86FF), Color(0xFF150839), rgb(150, 120, 255, .2f), Color(0xFF120F20), rgb(170, 131, 221, a1), rgb(113, 159, 213, a2), rgb(190, 170, 240, .055f))
        else RunSkin(Color(0xFF7B4FD6), Color(0xFF5A3EC2), Color.White, rgb(106, 69, 196, .18f), Color(0xFFF0EDF8), rgb(170, 131, 221, a1), rgb(113, 159, 213, a2), rgb(90, 60, 160, .07f))
        "forest" -> if (dark) RunSkin(Color(0xFF5FE0B0), Color(0xFF3FC4C4), Color(0xFF06301E), rgb(70, 200, 160, .18f), Color(0xFF0B1712), rgb(96, 182, 154, a1), rgb(133, 202, 208, a2), rgb(160, 225, 200, .055f))
        else RunSkin(Color(0xFF1F8A5A), Color(0xFF13707A), Color.White, rgb(29, 122, 80, .18f), Color(0xFFE9F4EF), rgb(96, 182, 154, a1), rgb(133, 202, 208, a2), rgb(30, 110, 80, .07f))
        "amber" -> if (dark) RunSkin(Color(0xFFFFC36A), Color(0xFFF59A4A), Color(0xFF3A2206), rgb(240, 160, 70, .2f), Color(0xFF18120A), rgb(224, 160, 80, a1), rgb(208, 138, 96, a2), rgb(240, 205, 160, .05f))
        else RunSkin(Color(0xFFA85A12), Color(0xFF8C400E), Color.White, rgb(166, 90, 16, .18f), Color(0xFFF6F0E7), rgb(224, 160, 80, a1), rgb(208, 138, 96, a2), rgb(140, 90, 30, .07f))
        "rose" -> if (dark) RunSkin(Color(0xFFFF9CC0), Color(0xFFE07BE0), Color(0xFF3A0818), rgb(240, 120, 180, .2f), Color(0xFF1A0F15), rgb(221, 149, 174, a1), rgb(192, 170, 241, a2), rgb(240, 180, 205, .055f))
        else RunSkin(Color(0xFFC23A6A), Color(0xFF9E2F88), Color.White, rgb(184, 50, 96, .18f), Color(0xFFF7EDF1), rgb(221, 149, 174, a1), rgb(192, 170, 241, a2), rgb(150, 40, 90, .07f))
        else -> if (dark) RunSkin(Color(0xFF22D7EB), Color(0xFF339EFA), Color(0xFF062534), rgb(0, 184, 232, .18f), Color(0xFF0C1724), rgb(0, 175, 220, a1), rgb(92, 110, 247, a2), rgb(172, 214, 242, .055f))
        else RunSkin(Color(0xFF008DAB), Color(0xFF067AC9), Color.White, rgb(0, 141, 171, .18f), Color(0xFFE9F2F8), rgb(0, 175, 220, a1), rgb(92, 110, 247, a2), rgb(40, 113, 150, .07f))
    }
}

/** 110° 的 CSS 线性渐变（run-a → run-b），按组件尺寸算起止点。 */
fun runGradient(skin: RunSkin, size: Size): Brush {
    // CSS 110deg：方向 (sin110°, -cos110°)，渐变线长度 = |w·sin| + |h·cos|
    val sx = 0.9397f
    val sy = 0.3420f
    val half = (size.width * sx + size.height * sy) / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    return Brush.linearGradient(
        colors = listOf(skin.runA, skin.runB),
        start = Offset(c.x - sx * half, c.y - sy * half),
        end = Offset(c.x + sx * half, c.y + sy * half),
    )
}

/** CSS `radial-gradient(ellipse at px py, color, transparent stop)`（ellipse 默认 farthest-corner）。 */
private fun DrawScope.cssEllipseGlow(fx: Float, fy: Float, color: Color, stop: Float) {
    val c = Offset(size.width * fx, size.height * fy)
    val rx = max(c.x, size.width - c.x) * sqrt(2f)
    val ry = max(c.y, size.height - c.y) * sqrt(2f)
    if (rx <= 0f || ry <= 0f) return
    scale(scaleX = 1f, scaleY = ry / rx, pivot = c) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to color,
                stop to color.copy(alpha = 0f),
                center = c,
                radius = rx,
            ),
            radius = rx * stop,
            center = c,
        )
    }
}

/** 生图页画布背景：纯色底 + 左下光（0% 60%，45%）+ 右上光（85% 0%，50%）+ 24dp 细网格。 */
fun Modifier.runCanvasBackdrop(skin: RunSkin): Modifier = drawBehind {
    drawRect(skin.cvBg)
    cssEllipseGlow(.85f, 0f, skin.g2, .5f)
    cssEllipseGlow(0f, .6f, skin.g1, .45f)
    val step = 24.dp.toPx()
    val line = 1.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawRect(skin.grid, topLeft = Offset(0f, y), size = Size(size.width, line))
        y += step
    }
    var x = 0f
    while (x < size.width) {
        drawRect(skin.grid, topLeft = Offset(x, 0f), size = Size(line, size.height))
        x += step
    }
}
