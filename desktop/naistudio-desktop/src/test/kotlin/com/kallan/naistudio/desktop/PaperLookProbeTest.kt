package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.models.StrokeMode
import com.kallan.naistudio.models.StrokeSpec
import com.kallan.naistudio.screens.BrushPresets
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **㉜c 的肉眼旁证** ✓：出厂那批**水彩 / 混色预设原样**（`paperOn = true` ✗ 用户手里就是这一档）
 * 各导一张 PNG 落进 `docs/`（与 `docs/mix-look-*.png` 同一个习惯 ✓）。
 *
 * 这一张不是判据（判据是 `PaperBrushParityTest` / `PaperStrokeContinuityTest` ✓）——
 * 它只是让用户**一眼**看出"打开就有画布、能画、笔迹是不是连贯、有没有颗粒"✓。
 */
class PaperLookProbeTest {

    private val platform = desktopPlatform()

    @Test
    fun dump_factory_watercolor_presets() {
        val w = 1240
        val h = 1754
        val presets = listOf("wc1", "wc2", "paint2", "mix1")
        val index = StringBuilder()
        index.append("# ㉜c 肉眼旁证：出厂预设（`paperOn=true`）现在的样子\n\n")
        index.append("口径：1240×1754、一笔（正弦长线、压力恒定 0.85）、`seed=11`、直接走生产链路。\n\n")
        for (key in presets) {
            val preset = BrushPresets.firstOrNull { it.id == key } ?: continue
            val session = ImageEditSession(w, h, IntArray(w * h))
            session.dabProbeEnabled = true
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
                    brush = preset.spec,
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
            val composited = IntArray(w * h) { i ->
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
            java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(composited)
            val bmp = org.jetbrains.skia.Bitmap()
            assertTrue("位图分配失败 ✗", bmp.allocPixels(info))
            bmp.installPixels(info, bytes, w * 4)
            val png = org.jetbrains.skia.Image.makeFromBitmap(bmp)
                .encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes
            val out = File("../docs/paper-look-kotlin-$key.png")
            val target = if (out.parentFile?.isDirectory == true) out else File("docs/paper-look-kotlin-$key.png")
            target.parentFile?.mkdirs()
            target.writeBytes(png)
            val stats = session.strokeDabStats()
            index.append(
                "- `paper-look-kotlin-$key.png`：预设 `$key`（size=${preset.sizePx}、" +
                    "paperOn=${preset.spec.paperOn}/strength=${preset.spec.paperStrength}、" +
                    "blending=${preset.spec.blending}/water=${preset.spec.water}/cs=${preset.spec.colorStretch}、" +
                    "scattering=${preset.spec.scattering}/sizeJitter=${preset.spec.sizeJitter}）" +
                    " ⇒ 走表面=${session.strokeOnLayerSurface}、笔尖 ${stats.dabs} 颗、采样 ${stats.samples} 次\n",
            )
            println("[㉜c-旁证] $key 走表面=${session.strokeOnLayerSurface} dabs=${stats.dabs} → ${target.name}")
        }
        val idx = File("../docs/paper-look-README.md")
        val idxTarget = if (idx.parentFile?.isDirectory == true) idx else File("docs/paper-look-README.md")
        idxTarget.writeText(index.toString())
    }
}
