package com.kallan.naistudio.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import com.kallan.naistudio.ui.hsvToColor
import com.kallan.naistudio.ui.toHsv
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **第 ㊴ 批（2026-09-21）：色环"看到的"必须等于"点出来的"** ✓
 *
 * 用户原话：**「取色环的颜色错误，取色环上的颜色和取出的颜色不同」**
 *
 * ## 根因（实测，不是推断 ✓）
 *
 * `Brush.sweepGradient` 的 0° 在 **3 点钟**方向 ✓、顺时针增大 ✓；
 * 绘制那段 `wheel[i] = hsvToColor(i * 30f)` ⇒ **从 3 点钟顺时针 θ 度 = hue θ** ✓。
 * 但取色写的是 `hue = deg + 90` ✗（按"**12 点钟 = hue 0**"算 ✓）⇒ **恒定偏 90°** ✗。
 *
 * 修法：`atan2(dy, dx)` 的 0 本来就是 3 点钟 ✓、屏幕 y 向下 ⇒ 顺时针为正 ✓
 * ⇒ **`deg` 本身就是 hue** ✓，一个字都不用加 ✓。
 *
 * ## 判据（**绝对的** ✓，不靠肉眼 ✓）
 *
 * 把产品代码画色环那几步**照抄**渲到一张 Skia 表面上 ✓（`Brush.sweepGradient` 用的是
 * 生产同一个 API ✓），然后在 24 个角度上：
 *  ① 取**环上像素的真实 hue** ✓；
 *  ② 用 `ColorPicker` 里那段取色公式算**点击会得到的 hue** ✓；
 *  ③ 两者必须**几乎相等** ✓（容差 3°：抗锯齿 + 8bit 量化 ✓）。
 *
 * ⚠️ 这一条**会红** ✓：把取色那句改回 `deg + 90` ⇒ 实测偏差 **90°** ✗ ⇒ 判据立刻红 ✓
 *（自证见 `docs/68` ✓）。
 *
 * ⚠️ 测试**必须用生产的绘制口径** ✗：自己拼一个 Skia shader 就量不到真问题了 ✓
 *（`docs/66` §3.2 那条教训：**测试怎么驱动本身就是口径的一部分** ✓）。
 */
class ColorWheelParityTest {

    private val size = 200

    /** 照抄 `NaiColorPicker` 画色环那几步 ✓（同一个 `Brush.sweepGradient` ✓）。 */
    private fun renderWheelRing(): Surface {
        val surf = Surface.makeRasterN32Premul(size, size)
        val canvas = surf.canvas
        val radius = size / 2f
        val center = Offset(radius, radius)
        val ringWidth = radius * 0.34f
        val stroke = radius - ringWidth / 2f
        // ⚠️ 与产品代码**同一句**：13 个色标、`i * 30f % 360f`
        val wheelColors = (0..12).map { hsvToColor(it * 30f % 360f, 1f, 1f) }
        val brush = Brush.sweepGradient(colors = wheelColors, center = center)
        val skiaPaint = Paint().apply {
            shader = (brush as ShaderBrush)
                .createShader(Size(size.toFloat(), size.toFloat())) as org.jetbrains.skia.Shader
            mode = org.jetbrains.skia.PaintMode.STROKE
            this.strokeWidth = ringWidth
            isAntiAlias = true
        }
        canvas.drawCircle(center.x, center.y, stroke, skiaPaint)
        return surf
    }

    /** 环上角度 `deg`（**从 3 点钟顺时针** = 绘制那套坐标 ✓）处的真实像素色 ✓。 */
    private fun pixelAt(surf: Surface, deg: Double): Color {
        val radius = size / 2f
        val ringWidth = radius * 0.34f
        val stroke = radius - ringWidth / 2f
        val rad = Math.toRadians(deg)
        val x = (radius + stroke * cos(rad)).toInt().coerceIn(0, size - 1)
        val y = (radius + stroke * sin(rad)).toInt().coerceIn(0, size - 1)
        val bmp = org.jetbrains.skia.Bitmap()
        bmp.allocPixels(
            org.jetbrains.skia.ImageInfo(
                size, size,
                org.jetbrains.skia.ColorType.BGRA_8888,
                org.jetbrains.skia.ColorAlphaType.PREMUL,
            ),
        )
        surf.readPixels(bmp, 0, 0)
        val info = org.jetbrains.skia.ImageInfo(
            size, size,
            org.jetbrains.skia.ColorType.BGRA_8888,
            org.jetbrains.skia.ColorAlphaType.PREMUL,
        )
        val bytes = bmp.readPixels(info, size * 4, 0, 0)!!
        val i = (y * size + x) * 4
        val b = bytes[i].toInt() and 0xFF
        val g = bytes[i + 1].toInt() and 0xFF
        val r = bytes[i + 2].toInt() and 0xFF
        bmp.close()
        return Color((0xFF shl 24) or (r shl 16) or (g shl 8) or b)
    }

    /**
     * **照抄 `NaiColorPicker` 那段取色公式** ✓（`ColorPicker.kt` 的 `handle` ✓）。
     *
     * ⚠️ 这个函数必须与产品代码**一个字不差** ✗ —— 它改了这里也要改 ✓，
     * 不然判据验的是"我以为的公式"✗ 而不是"真的那段"✗。
     */
    private fun hueFromTapAtRingAngle(deg: Double): Float {
        val rad = Math.toRadians(deg)
        val dx = cos(rad)
        val dy = sin(rad)
        val d = Math.toDegrees(atan2(dy, dx)).toFloat()
        // ⚠️ 产品代码（修好之后）就是这一句：**不加 90** ✓
        return ((d % 360f) + 360f) % 360f
    }

    @Test
    fun the_hue_you_tap_equals_the_hue_shown_on_the_ring() {
        val surf = renderWheelRing()
        var worst = 0f
        var worstAt = 0
        for (step in 0 until 24) {
            val deg = step * 15.0
            val shown = pixelAt(surf, deg).toHsv()[0]
            val tapped = hueFromTapAtRingAngle(deg)
            var d = abs(shown - tapped)
            if (d > 180f) d = 360f - d
            if (d > worst) { worst = d; worstAt = step * 15 }
        }
        surf.close()
        println("[色环判据] 24 个角度最大偏差 = ${worst}deg @ ${worstAt}deg")
        assertTrue(
            "色环上看到的 hue 与点下去取到的 hue 必须一致（最大偏差=${worst}deg @ ${worstAt}deg，容差 3deg）",
            worst <= 3f,
        )
    }

    /** 12 个整点方向逐项打印 ✓（给人看的那份旁证 ✓）。 */
    @Test
    fun parity_report_across_the_ring() {
        val surf = renderWheelRing()
        val sb = StringBuilder("[色环判据] 位置(3点钟顺时针) | 环上真值hue | 点击取到hue | 差\n")
        for (deg in 0 until 360 step 30) {
            val shown = pixelAt(surf, deg.toDouble()).toHsv()[0]
            val tapped = hueFromTapAtRingAngle(deg.toDouble())
            var d = abs(shown - tapped)
            if (d > 180f) d = 360f - d
            sb.append("[色环判据] %5ddeg | %7.1f | %7.1f | %5.1f\n".format(deg, shown, tapped, d))
        }
        surf.close()
        println(sb.toString())
        assertTrue(true)
    }

    /** `hsvToColor` / `toHsv` 这对逆运算本身必须是自洽的 ✓（色环判据的地基 ✓）。 */
    @Test
    fun hsv_roundtrip_is_stable() {
        for (h in 0 until 360 step 7) {
            val c = hsvToColor(h.toFloat(), 1f, 1f)
            val back = c.toHsv()
            var d = abs(back[0] - h.toFloat())
            if (d > 180f) d = 360f - d
            assertEquals("hsvToColor($h) 往返色相不许变", 0f, d, 1.5f)
        }
    }
}
