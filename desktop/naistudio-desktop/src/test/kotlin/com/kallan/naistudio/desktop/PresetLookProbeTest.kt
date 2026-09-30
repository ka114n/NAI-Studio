package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.BrushSpec
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.models.StrokeMode
import com.kallan.naistudio.models.StrokeSpec
import com.kallan.naistudio.screens.BrushPresets
import org.junit.Test
import java.io.File

/**
 * 临时探针 ✗（用户实机报「上色笔 / 水彩笔还是一颗颗圆」⇒ 先复现，再决定修哪一条 ✓）。
 *
 * 用**出厂预设原样**（`BrushPresets` 里那些 ✓）+ 同一串输入点各导一张 PNG：
 *  · `as-shipped`（`paperOn = true` ✓ —— 用户手里就是这一档）；
 *  · `paper-off`（只把纸纹关掉 ✓ —— 用来分离"纸纹那条老路"与"参数本身的颗粒感"）。
 */
class PresetLookProbeTest {

    private val platform = desktopPlatform()

    @Test
    fun dump_presets() {
        val w = 1240
        val h = 1754
        val presets = listOf("paint1", "paint2", "wc1", "wc2", "mix1", "blend1")
        for (key in presets) {
            val preset = BrushPresets.firstOrNull { it.id == key } ?: continue
            for (paperOff in listOf(false, true)) {
                val spec = if (paperOff) preset.spec.copy(paperOn = false, paperStrength = 0f) else preset.spec
                val session = ImageEditSession(w, h, IntArray(w * h))
                session.dabProbeEnabled = true
                // 一笔：一条带弧度的长线（用户真画起来大概就是这样 ✓）
                val points = ArrayList<Pair<Float, Float>>()
                var x = 200f
                while (x <= 1050f) {
                    val t = (x - 200f) / 850f
                    points += x to (700f + 260f * kotlin.math.sin((t * 6.0)).toFloat())
                    x += 18f
                }
                session.beginStroke(
                    points[0].first, points[0].second,
                    StrokeSpec(
                        mode = StrokeMode.PAINT,
                        brushPixels = preset.sizePx,
                        color = 0xFF22262E.toInt(),
                        brush = spec,
                        minRadiusRatio = 0.15f,
                        pressureCurve = 1f,
                        seed = 11L,
                    ),
                    0.85f,
                )
                for (i in 1 until points.size) {
                    session.strokeTo(points[i].first, points[i].second, 0.85f)
                }
                session.endStroke()
                if (key == "wc1" && !paperOff) {
                    val log = session.dabProbeLog
                    val sb = StringBuilder()
                    var prevX = 0f
                    var prevY = 0f
                    for (i in 0 until minOf(log.size, 40)) {
                        val d = log[i]
                        val dist = if (i == 0) 0f else kotlin.math.hypot(d.x - prevX, d.y - prevY)
                        prevX = d.x
                        prevY = d.y
                        sb.append(
                            "#$i (${"%.1f".format(d.x)},${"%.1f".format(d.y)}) r=${"%.2f".format(d.radius)}" +
                                " step=${"%.2f".format(d.stepPixels)} d=${"%.2f".format(dist)}" +
                                " alpha=${"%.4f".format(d.plan.alpha)}" +
                                " shapes=${d.plan.shapes.map { "(${"%.1f".format(it.x)},${"%.1f".format(it.y)},${"%.1f".format(it.radius)})" }}\n",
                        )
                    }
                    println("[dabs] wc1（前 40 颗）:\n$sb")
                }
                val px = IntArray(w * h) { i ->
                    val p = session.pixels[i]
                    val a = ((p ushr 24) and 0xFF) / 255f
                    if (a <= 0f) {
                        0xFFFFFFFF.toInt()
                    } else {
                        fun ch(sh: Int) = ((((p ushr sh) and 0xFF) * a + 255f * (1f - a)) + 0.5f)
                            .toInt().coerceIn(0, 255)
                        (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
                    }
                }
                val info = org.jetbrains.skia.ImageInfo(
                    w, h,
                    org.jetbrains.skia.ColorType.BGRA_8888,
                    org.jetbrains.skia.ColorAlphaType.UNPREMUL,
                )
                val bytes = ByteArray(w * h * 4)
                java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(px)
                val bmp = org.jetbrains.skia.Bitmap()
                bmp.allocPixels(info)
                bmp.installPixels(info, bytes, w * 4)
                val png = org.jetbrains.skia.Image.makeFromBitmap(bmp)
                    .encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes
                val out = File("build/preset-$key-paperoff-$paperOff.png")
                out.parentFile?.mkdirs()
                out.writeBytes(png)
                println(
                    "[preset] $key size=${preset.sizePx} paperOn=${spec.paperOn} " +
                        "走表面=${session.strokeOnLayerSurface} dabs=${session.strokeDabStats().dabs} " +
                        "samples=${session.strokeDabStats().samples} → ${out.name}",
                )
                if (key == "wc1" && paperOff) {
                    // 1:1 的 ASCII 灰度图（0 = 全黑 … 9 = 全白 ✓）—— 直接看"一个个圆"到底长什么样 ✓
                    val x0 = 460
                    val y0 = 880
                    val map = StringBuilder()
                    for (y in y0 until y0 + 70 step 2) {
                        for (x in x0 until x0 + 110 step 1) {
                            val p = session.pixels[y * w + x]
                            val a = ((p ushr 24) and 0xFF) / 255f
                            val g = (34 * a + 255 * (1 - a)).toInt().coerceIn(0, 255)
                            map.append(((9 - g / 26).coerceIn(0, 9)))
                        }
                        map.append('\n')
                    }
                    println("[pixmap] wc1 paperOff（x=$x0..${x0 + 110}, y=$y0..${y0 + 70}，每 2 行一行）:\n$map")
                }
            }
        }
    }
}
