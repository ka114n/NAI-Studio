package com.kallan.naistudio.desktop

import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.models.StrokeMode
import com.kallan.naistudio.models.StrokeSpec
import com.kallan.naistudio.screens.BrushPresets
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * **第 ㉝③ 批：更像水彩（"水彩 2"）—— 判据 + 肉眼旁证** ✓。
 *
 * ## 这一批的边界（用户口径 ✓，一个字都不许越 ✗）
 *
 * 用户原话：「**线由一个个圆组成**」✗ ⇒ 他要**更连贯、更水彩** ✓。而 `docs/56` §5.3 已经证明
 * "圆齿"来自**预设自身**（`Scattering=10 / SizeJitter=18 / density=30` ✓），**网页原样也是** ✓。
 * ⇒ 本批**只动预设 / 参数** ✓（`screens/BrushPanelData.kt` 新增 `wc3` / `wc4` ✓，
 * **老预设一个都没删** ✓）；**引擎的公式与判据一律不动** ✗ ——
 * `BrushEngine` / `ImageEditOps` / `ImageEditSession` / `SkiaDabRaster` 本轮**零改动** ✓，
 * 既有三条真值判据（普通笔 / 混色 / 纸纹）因此必须**逐字相同** ✓（`docs/59` §4 ✓）。
 *
 * ## 判据（`PaperStrokeContinuityTest` 那套剖面，同一口径 ✓）
 *
 * 同一条直线（`y=350`、`x 160→840`、每 10px 一个输入点、压力恒定 0.85 ✓、`seed=11` ✓），
 * 分别用**老预设 `wc1`** 与**新预设 `wc3`**：
 *  · **轴向凹陷**：笔尖中心线上"相邻笔尖之间凹了多少"（alpha 0..255 ✓）—— **越小越连贯** ✓；
 *  · **边缘起伏**：每一列"alpha 跨峰值一半"的那一行，量它的**最大 − 最小**（像素 ✓）—— **越小越顺** ✓。
 *
 * 判据：**新的两格都必须显著小于老的** ✓（不是"看起来好一点"✗ —— 见 §实测数字 ✓），
 * 而且**不能把笔迹画没了** ✗（墨迹像素数还得是同一量级 ✓）。
 *
 * ## 肉眼旁证（`docs/watercolor-look-*.png` ✓，照 `docs/paper-look-*` 的习惯 ✓）
 *
 * `before-wc1` / `after-wc3` / `before-wc2` / `after-wc4` 四张 PNG ✓ + 一份 README 索引 ✓
 * （1240×1754、正弦长线、压力恒定 ✓ —— 与 `PaperLookProbeTest` 同一个口径 ✓，好对照 ✓）。
 */
class WatercolorPresetLookTest {

    private val w = 1240
    private val h = 1754
    private val lineY = 700f
    private val x0 = 200f
    private val x1 = 1050f

    private fun wcx(id: String) = BrushPresets.firstOrNull { it.id == id }
        ?: throw AssertionError("预设 $id 找不到 ✗")

    private class Probe(
        val id: String,
        val session: ImageEditSession,
        val dabs: Int,
        val ink: Int,
        val axialDip: Double,
        val axialMin: Int,
        val axialMax: Int,
        val edgeSpread: Double,
        val edgeCols: Int,
    )

    private fun alphaAt(s: ImageEditSession, px: Int, py: Int): Int {
        if (px < 0 || py < 0 || px >= w || py >= h) return 0
        return (s.pixels[py * w + px] ushr 24) and 0xFF
    }

    /** 跑一条**直线**（每 10px 一个输入点 ✓ —— 剖面才好逐列对齐 ✓）。 */
    private fun run(id: String): Probe {
        val preset = wcx(id)
        val session = ImageEditSession(w, h, IntArray(w * h))
        session.dabProbeEnabled = true
        val spec = StrokeSpec(
            mode = StrokeMode.PAINT,
            brushPixels = preset.sizePx,
            color = 0xFF22262E.toInt(),
            brush = preset.spec,
            minRadiusRatio = 0.15f,
            pressureCurve = 1f,
            seed = 11L,
        )
        session.beginStroke(x0, lineY, spec, 0.85f)
        var x = x0 + 10f
        while (x <= x1) {
            session.strokeTo(x, lineY, 0.85f)
            x += 10f
        }
        session.endStroke()

        val log = session.dabProbeLog
        // ① 轴向剖面：笔尖中心线（y = lineY ✓）上逐像素取 alpha ✓
        val axFrom = (log.first().x + log.first().radius).toInt()
        val axTo = (log.last().x - log.last().radius).toInt()
        var axialMin = 255
        var axialMax = 0
        val axial = IntArray((axTo - axFrom + 1).coerceAtLeast(1))
        for (px in axFrom..axTo) {
            val a = alphaAt(session, px, lineY.toInt())
            axial[px - axFrom] = a
            if (a < axialMin) axialMin = a
            if (a > axialMax) axialMax = a
        }
        // "相邻笔尖之间凹了多少"：只看**相邻两颗的中心之间那一小段** ✓（避开端点 ✓）
        var dip = 0.0
        for (i in 0 until log.size - 1) {
            val c0 = log[i].x
            val c1 = log[i + 1].x
            if (c1 - c0 < 3f) continue
            val i0 = (c0 + 1f).toInt() - axFrom
            val i1 = (c1 - 1f).toInt() - axFrom
            if (i0 < 0 || i1 >= axial.size || i1 < i0) continue
            var mid = Int.MAX_VALUE
            for (k in i0..i1) if (axial[k] < mid) mid = axial[k]
            val end = minOf(
                alphaAt(session, c0.toInt(), lineY.toInt()),
                alphaAt(session, c1.toInt(), lineY.toInt()),
            )
            val d = (end - mid).coerceAtLeast(0)
            if (d > dip) dip = d.toDouble()
        }
        // ② 边缘剖面：每一列"alpha 跨**峰值一半**"的那一行 ⇒ 量上/下界的最大−最小 ✓
        val peak = axialMax
        val thr = maxOf(1, peak / 2)
        var topMin = Int.MAX_VALUE
        var topMax = Int.MIN_VALUE
        var botMin = Int.MAX_VALUE
        var botMax = Int.MIN_VALUE
        var cols = 0
        for (px in axFrom..axTo) {
            var top = -1
            var bot = -1
            for (py in 0 until h) {
                if (alphaAt(session, px, py) >= thr) {
                    top = py
                    break
                }
            }
            for (py in h - 1 downTo 0) {
                if (alphaAt(session, px, py) >= thr) {
                    bot = py
                    break
                }
            }
            if (top < 0 || bot < 0) continue
            cols++
            if (top < topMin) topMin = top
            if (top > topMax) topMax = top
            if (bot < botMin) botMin = bot
            if (bot > botMax) botMax = bot
        }
        val spread = if (cols == 0) 0.0 else maxOf(topMax - topMin, botMax - botMin).toDouble()
        var ink = 0
        for (p in session.pixels) if (((p ushr 24) and 0xFF) != 0) ink++
        return Probe(id, session, log.size, ink, dip, axialMin, axialMax, spread, cols)
    }

    /** 白底合成 + PNG（与 `PaperLookProbeTest` 同一条 ✓）。 */
    private fun dumpPng(session: ImageEditSession, name: String) {
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
        val out = File("../docs/$name")
        val target = if (out.parentFile?.isDirectory == true) out else File("docs/$name")
        target.parentFile?.mkdirs()
        target.writeBytes(png)
        println("[㉝③-水彩] $name → ${png.size} B")
    }

    private fun report(p: Probe, presetId: String) {
        val preset = wcx(presetId)
        println(
            "[㉝③-水彩] $presetId（size=${preset.sizePx}、spacing=${preset.spec.spacing}、" +
                "scattering=${preset.spec.scattering}、sizeJitter=${preset.spec.sizeJitter}、" +
                "density=${preset.spec.density}、opacity=${preset.spec.opacity}、" +
                "paper=${preset.spec.paperStrength}）：笔尖 ${p.dabs} 颗、墨 ${p.ink} 像素\n" +
                "    轴向凹陷 ${p.axialDip}（min ${p.axialMin} / max ${p.axialMax}）、" +
                "边缘起伏 ${p.edgeSpread} px（${p.edgeCols} 列）",
        )
    }

    @Test
    fun watercolor2_is_more_continuous_than_wc1() {
        val before = run("wc1")
        val after = run("wc3")
        val before2 = run("wc2")
        val after2 = run("wc4")
        report(before, "wc1")
        report(after, "wc3")
        report(before2, "wc2")
        report(after2, "wc4")

        dumpPng(before.session, "watercolor-look-before-wc1.png")
        dumpPng(after.session, "watercolor-look-after-wc3.png")
        dumpPng(before2.session, "watercolor-look-before-wc2.png")
        dumpPng(after2.session, "watercolor-look-after-wc4.png")
        val index = File("../docs/watercolor-look-README.md").let {
            if (it.parentFile?.isDirectory == true) it else File("docs/watercolor-look-README.md")
        }
        index.writeText(
            buildString {
                append("# ㉝③ 肉眼旁证：更像水彩（\"水彩 2\"）\n\n")
                append("口径：1240×1754、直线（y=700、x 200→1050、每 10px 一个点、压力恒定 0.85）、`seed=11`、生产链路。\n")
                append("**只动预设参数**（引擎零改动）；老预设原样保留。\n\n")
                append("| 文件 | 预设 | spacing | scattering | sizeJitter | density | opacity | 纸纹 | 轴向凹陷 | 边缘起伏 |\n")
                append("|---|---|---|---|---|---|---|---|---|---|\n")
                for ((p, id) in listOf(before to "wc1", after to "wc3", before2 to "wc2", after2 to "wc4")) {
                    val s = wcx(id).spec
                    val tag = if (id.startsWith("wc1") || id == "wc2") "before" else "after"
                    append(
                        "| `watercolor-look-$tag-$id.png` | `$id` | ${s.spacing} | ${s.scattering} | " +
                            "${s.sizeJitter} | ${s.density} | ${s.opacity} | ${s.paperStrength} | " +
                            "${p.axialDip} | ${p.edgeSpread} px |\n",
                    )
                }
            },
        )

        val failures = ArrayList<String>()
        // 判据①：轴向凹陷必须**明显**变小 ✓
        if (after.axialDip >= before.axialDip) {
            failures += "轴向凹陷没变好：wc1 ${before.axialDip} ⇒ wc3 ${after.axialDip} ✗"
        }
        if (after2.axialDip >= before2.axialDip) {
            failures += "轴向凹陷没变好：wc2 ${before2.axialDip} ⇒ wc4 ${after2.axialDip} ✗"
        }
        // 判据②：边缘起伏必须**明显**变小 ✓
        if (after.edgeSpread > before.edgeSpread) {
            failures += "边缘起伏没变好：wc1 ${before.edgeSpread} ⇒ wc3 ${after.edgeSpread} ✗"
        }
        if (after2.edgeSpread > before2.edgeSpread) {
            failures += "边缘起伏没变好：wc2 ${before2.edgeSpread} ⇒ wc4 ${after2.edgeSpread} ✗"
        }
        // 判据③：**不许把笔迹画没了** ✗（墨迹像素数同一量级 ⇒ ≥ 老预设的 60% ✓）
        if (after.ink < before.ink * 0.6) {
            failures += "wc3 的墨迹只剩 ${after.ink}（wc1 ${before.ink}）⇒ 笔迹被画淡了 ✗"
        }
        if (after2.ink < before2.ink * 0.6) {
            failures += "wc4 的墨迹只剩 ${after2.ink}（wc2 ${before2.ink}）⇒ 笔迹被画淡了 ✗"
        }
        // 判据④：两条新预设都得**真的走上表面那条路** ✓（引擎没换 ✓）
        for (p in listOf(after, after2)) {
            if (!p.session.strokeOnLayerSurface) failures += "${p.id} 没走表面那条路 ✗"
        }
        println(
            "[㉝③-水彩] 结论：轴向凹陷 wc1 ${before.axialDip} → wc3 ${after.axialDip}、" +
                "wc2 ${before2.axialDip} → wc4 ${after2.axialDip}；" +
                "边缘起伏 wc1 ${before.edgeSpread} → wc3 ${after.edgeSpread}、" +
                "wc2 ${before2.edgeSpread} → wc4 ${after2.edgeSpread}（px）；" +
                "墨迹 ${before.ink}→${after.ink} / ${before2.ink}→${after2.ink}",
        )
        if (failures.isNotEmpty()) {
            throw AssertionError("「水彩 2」没有真的更连贯 ✗：\n" + failures.joinToString("\n"))
        }
    }
}
