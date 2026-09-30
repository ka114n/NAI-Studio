package com.kallan.naistudio.desktop

import com.kallan.naistudio.models.BrushSpec
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.models.StrokeMode
import com.kallan.naistudio.models.StrokeSpec
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * **㉜c · 「画出来的线由一个个圆组成」那条判据**（用户 2026-09-21 实机报的 ✗）。
 *
 * 病象：笔迹的**轮廓**不是一条平滑的带子，而是一串圆弧（每颗笔尖自己的边被看出来 ✓）。
 * 为什么"我觉得圆"不算判据 ✗：这种观感**能量化** —— 一条**直线**笔画的
 *  · **轴向剖面**（沿笔尖中心那条线取 alpha）在相邻笔尖之间应该是平的 ✓；
 *  · **边缘剖面**（每一列上"alpha 跨过 50% 的那一行"）应该是一条**直线** ✓ ——
 *    一旦它随笔尖间距**周期性起伏**，那就是"一个个圆" ✓（起伏幅度 = 肉眼看到的波浪 ✓）。
 *
 * ⚠️ 为什么必须**同时**量这两条（自己踩出来的 ✗）：
 *  · 轴向那一条**只在"笔尖间距 > 笔尖直径"时才红** ✓（= 老口径的"一排断点" ✓）；
 *    间距正常（`Spacing ≤ 10%` ✓）时中心线是**叠满**的，任何实现在这里都是平的 ⚠️；
 *  · 用户报的"一个个圆"是**边缘**上的波浪 ⇒ 只有边缘剖面抓得到 ✓。
 *  两条都留着：一条守"断点"、一条守"波浪" ✓。
 *
 * 口径（两组，同一串输入点 ✓）：
 *  · `plain`：出厂水彩那一套参数、**纸纹关**（`paperOn = false` ✓ ⇒ 走 Skia 表面 ✓）；
 *  · `paper`：同一套参数、**纸纹开**（`paperOn = true / 50` ✓ ⇒ ㉜c 起也走 Skia 表面 ✓）。
 */
class PaperStrokeContinuityTest {

    private val w = 1000
    private val h = 700
    private val lineY = 350f
    private val x0 = 160f
    private val x1 = 840f
    private val step = 10f
    private val pressure = 0.85f

    /** 出厂 `wc1` 那一套（`BrushPanelData.kt` ✓）——低浓度 + 水分 + 混色 ⇒ 最容易看出"一个个圆" ✓。 */
    private fun wc1(paperOn: Boolean, strength: Float) = BrushSpec(
        paperOn = paperOn,
        paperStrength = strength,
        spreadNoise = false,
        spreadNoiseStrength = 0f,
        blending = 76f,
        water = 80f,
        colorStretch = 62f,
        spacing = 6f,
        scattering = 0f,
        sizeJitter = 0f,
        count = 1,
        wxJitter = 0f,
        countJitter = 0f,
        hueJitter = 0f,
        saturationJitter = 0f,
        brightnessJitter = 0f,
        colJitter = 0f,
        opacity = 80f,
        density = 30f,
        minDensity = 0f,
        wxRatio = 0f,
        angleControl = 1,
        angle = 0f,
        angleJitter = 0f,
        scaling = 100f,
        shapeSize = 100f,
        pressureAlpha = false,
    )

    private class Probe(
        val id: String,
        /** 这一笔的会话（剖面要从 `pixels` 里取 ✓）。 */
        val session: ImageEditSession,
        val onSurface: Boolean,
        val dabs: Int,
        /** 轴向：相邻笔尖中心之间**凹下去多少**（alpha 0..255 ✓，越大越像"一排点" ✗）。 */
        val axialDip: Double,
        val axialMin: Int,
        val axialMax: Int,
        /** 边缘：每一列 50% 交点那一行的**最大 − 最小**（像素 ✓，越大越像"一串圆" ✗）。 */
        val edgeSpreadTop: Double,
        val edgeSpreadBottom: Double,
        /** 边缘阈值用到的"峰值的一半"（诊断 ✓）。 */
        val edgeThreshold: Int,
        val edgeCols: Int,
        /** 边缘的**逐列最大跳变**（像素 ✓ —— 波浪的"陡"程度 ✓）。 */
        val edgeMaxJump: Double,
        /** 相邻笔尖中心的平均间距（像素 ✓，用来判断"边缘的周期是不是笔尖间距" ✓）。 */
        val meanStep: Double,
        val alphaFirstDab: Int,
    )

    private fun run(id: String, brush: BrushSpec): Probe {
        val session = ImageEditSession(w, h, IntArray(w * h))
        session.dabProbeEnabled = true
        val spec = StrokeSpec(
            mode = StrokeMode.PAINT,
            brushPixels = 72,
            color = 0xFF1A1A1A.toInt(),
            brush = brush,
            minRadiusRatio = 0.15f,
            pressureCurve = 1f,
            seed = 11L,
        )
        session.beginStroke(x0, lineY, spec, pressure)
        var x = x0
        while (x < x1) {
            x += step
            session.strokeTo(x, lineY, pressure)
        }
        session.endStroke()
        val log = session.dabProbeLog
        fun alphaAt(px: Int, py: Int): Int = (session.pixels[py * w + px] ushr 24) and 0xFF

        // ---- ① 轴向剖面：沿笔尖中心那条线（y = 350 ✓）逐像素取 alpha ----
        val axFrom = (log.first().x + log.first().radius).toInt()
        val axTo = (log.last().x - log.last().radius).toInt()
        var axialMin = 255
        var axialMax = 0
        val axial = IntArray((axTo - axFrom + 1).coerceAtLeast(1))
        for (px in axFrom..axTo) {
            val a = alphaAt(px, lineY.toInt())
            axial[px - axFrom] = a
            if (a < axialMin) axialMin = a
            if (a > axialMax) axialMax = a
        }
        // 相邻笔尖中心之间**真的取到**的那几个像素里最低的那一个 ⇒ 与两端比"凹了多少" ✓
        var dip = 0.0
        for (i in 0 until log.size - 1) {
            val c0 = log[i].x
            val c1 = log[i + 1].x
            if (c1 - c0 < 2.5f) continue
            val i0 = (c0 + 1f).toInt()
            val i1 = (c1 - 1f).toInt()
            if (i1 < i0) continue
            var inner = 255
            for (px in i0..i1) inner = minOf(inner, alphaAt(px, lineY.toInt()))
            val end = minOf(alphaAt(c0.toInt(), lineY.toInt()), alphaAt(c1.toInt(), lineY.toInt()))
            dip = maxOf(dip, (end - inner).toDouble())
        }

        // ---- ② 边缘剖面：每一列"alpha ≥ 这一笔峰值的一半"的最上面 / 最下面那一行 ----
        // ⚠️ 阈值必须**相对峰值**（第一版写死 128 就踩了：对照档（步距 0.4r、每颗 alpha 0.134）
        //    整条笔画都到不了 128 ⇒ 一列都没跨过 ⇒ 量出来是 `Int.MAX/MIN` 的溢出垃圾 ✗）。
        val mid = (log.first().radius * 2.5).toInt()
        val peak = axialMax
        val thr = maxOf(1, peak / 2)
        var topMin = Int.MAX_VALUE
        var topMax = Int.MIN_VALUE
        var botMin = Int.MAX_VALUE
        var botMax = Int.MIN_VALUE
        var jump = 0.0
        var prevTop = Int.MIN_VALUE
        var prevBot = Int.MIN_VALUE
        var cols = 0
        for (px in axFrom..axTo) {
            var top = -1
            var bot = -1
            for (py in 1 until h - 1) {
                if (alphaAt(px, py) >= thr) {
                    top = py
                    break
                }
            }
            for (py in h - 2 downTo 1) {
                if (alphaAt(px, py) >= thr) {
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
            if (prevTop != Int.MIN_VALUE) {
                jump = maxOf(jump, abs(top - prevTop).toDouble(), abs(bot - prevBot).toDouble())
            }
            prevTop = top
            prevBot = bot
        }
        var stepSum = 0.0
        for (i in 1 until log.size) stepSum += (log[i].x - log[i - 1].x).toDouble()
        val meanStep = if (log.size > 1) stepSum / (log.size - 1) else 0.0

        // ---- 肉眼旁证：把笔迹中心那一段打成 ASCII（0 = 全透 … 9 = 全不透 ✓） ----
        val map = StringBuilder()
        val y0 = (lineY - mid).toInt()
        val y1 = (lineY + mid).toInt()
        for (py in y0..y1 step 2) {
            for (px in (x0 + 60f).toInt() until (x0 + 60f).toInt() + 150) {
                map.append(((alphaAt(px, py) / 26)).coerceIn(0, 9))
            }
            map.append('\n')
        }
        println(
            "[㉜c-连续] $id 走表面=${session.strokeOnLayerSurface} 笔尖=${log.size} 平均间距=${"%.2f".format(meanStep)}px 半径=" +
                "${"%.1f".format(log.first().radius)}px\n" +
                "   ① 轴向剖面（笔尖中心线上）：最低 $axialMin / 最高 $axialMax ⇒ **相邻笔尖之间最大凹陷 $dip**（alpha 0..255）\n" +
                "   ② 边缘剖面（每列 alpha 跨「峰值一半」=$thr 那一行，$cols 列）：上边 ${topMin}..${topMax}" +
                "（差 ${if (cols == 0) -1 else topMax - topMin}px）、" +
                "下边 ${botMin}..${botMax}（差 ${if (cols == 0) -1 else botMax - botMin}px）、" +
                "逐列最大跳变 ${"%.1f".format(jump)}px\n" +
                "   中心那一段（150×${(y1 - y0) / 2} 的 ASCII，0=全透 9=全不透）：\n$map",
        )
        return Probe(
            id = id,
            session = session,
            onSurface = session.strokeOnLayerSurface,
            dabs = log.size,
            axialDip = dip,
            axialMin = axialMin,
            axialMax = axialMax,
            edgeSpreadTop = (topMax - topMin).toDouble(),
            edgeSpreadBottom = (botMax - botMin).toDouble(),
            edgeThreshold = thr,
            edgeCols = cols,
            edgeMaxJump = jump,
            meanStep = meanStep,
            alphaFirstDab = ((log.first().plan.alpha.coerceIn(0f, 1f)) * 255f).toInt(),
        )
    }

    @Test
    fun straight_stroke_has_no_periodic_scallops() {
        val plain = run("plain（纸纹关 ✓）", wc1(paperOn = false, strength = 0f))
        val paper = run("paper（纸纹开 50 ✓）", wc1(paperOn = true, strength = 50f))
        // 对照档：把 Spacing 拉到上限（`dabStep()` 的 0.4r 护栏 ⇒ 约 12.6px 一颗 ✓）——
        // **判据必须在它身上红** ✓（不然"没有凹陷"这个结论就是阈值太松换来的 ✗）。
        val coarse = run("coarse（Spacing=100 ⇒ 步距撞 0.4r 上限 ✓，**对照档**）", wc1(paperOn = true, strength = 50f).copy(spacing = 100f))

        val failures = ArrayList<String>()
        // ---- 走的必须是表面那条路（不然量的还是老的手写光栅 ✗）----
        if (!plain.onSurface) failures += "[plain] 没走层表面 ✗"
        if (!paper.onSurface) failures += "[paper] 纸纹没走层表面 ✗（㉜c 接上之后必须 true ✓）"
        // ---- 每颗笔尖的 alpha 必须真的 < 1（否则"交叠"这个前提不成立，判据没意义 ✗）----
        for (p in listOf(plain, paper)) {
            if (p.alphaFirstDab >= 250) failures += "[${p.id}] 每颗笔尖 alpha=${p.alphaFirstDab} 太满 ⇒ 判据无意义 ✗"
        }
        // ---- ① 轴向：相邻笔尖之间不许凹 ----
        // 阈值 = 实测最坏那一档 + 一点余量（实测：plain **4.0** / paper **11.0**，单位是 alpha 0..255 ✓）：
        //  · plain 那 4 个单位的来路很明确 —— 中心线上"盖住这一点的笔尖数"随 x 在 16/17 之间跳 ✓；
        //  · paper 多出来的那 7 个是**纸纹自己的调制**（每颗笔尖的 `tex` 各不相同 ⇒ `1 − Π(1 − α·tex)` 有波 ✓），
        //    与 `paperStrength` 同向 ✓ —— **网页一模一样**（同一条剖面对齐到 ≤5 ✓，见 `compareWithWebLine` ✓）。
        // ⇒ 这两条的意义是"**别退化成一颗颗的点**"，不是"绝对平" ✓（绝对平在数学上就不成立 ✗）。
        if (plain.axialDip > 8.0) {
            failures += "[plain] 轴向相邻笔尖之间凹陷 ${plain.axialDip} > 8（alpha 0..255）⇒ 看得出「一个个点」✗"
        }
        if (paper.axialDip > 20.0) {
            failures += "[paper] 轴向相邻笔尖之间凹陷 ${paper.axialDip} > 20（alpha 0..255）✗"
        }
        // ---- ② 边缘：不许周期性起伏 ----
        // 实测：plain **0px**、paper **1px**（阈值 2px 已经比"看得出波浪"严得多 ✓）；
        // 对照档（步距 0.4r）实测 **4px** ⇒ 这一条真的在测东西 ✓（见下面的"对照档必须红" ✓）。
        for (p in listOf(plain, paper)) {
            if (p.edgeSpreadTop > 2.0 || p.edgeSpreadBottom > 2.0) {
                failures += "[${p.id}] 边缘起伏 上 ${p.edgeSpreadTop}px / 下 ${p.edgeSpreadBottom}px > 2px ⇒ 一串圆 ✗"
            }
            if (p.edgeMaxJump > 1.5) {
                failures += "[${p.id}] 边缘逐列跳变 ${p.edgeMaxJump}px > 1.5px ✗"
            }
            if (p.edgeCols < 100) failures += "[${p.id}] 边缘剖面只量到 ${p.edgeCols} 列（太少，判据不可信 ✗）"
        }
        // ---- 对照档：判据必须在它身上红（不然判据没在测东西 ✗）----
        // 实测：coarse 的边缘起伏 **4px**（正常档 0~1px ✓）、轴向 9（与 plain 同量级 ⇒ 轴向那一条抓不到它 ⚠️）。
        val controlRed = coarse.edgeSpreadTop > 2.0 || coarse.edgeSpreadBottom > 2.0
        if (!controlRed) {
            failures += "[coarse] 对照档居然也过了（边缘起伏 " +
                "${coarse.edgeSpreadTop}/${coarse.edgeSpreadBottom}px、轴向 ${coarse.axialDip}）" +
                "⇒ 判据区分不了「间距拉大」✗"
        }

        // ---- ③ 与**网页那条直线**逐项对齐（真值：`brush-lab-simple-paper-truth.json` 的 `lineMix*` ✓）----
        compareWithWebLine(failures)

        if (failures.isNotEmpty()) throw AssertionError("笔画连续性：\n" + failures.joinToString("\n"))
        println(
            "[㉜c-连续] 两组都过了：轴向凹陷 plain ${plain.axialDip}（≤8）/ paper ${paper.axialDip}（≤20）、" +
                "边缘起伏 ≤2px ✓；对照档（步距 0.4r）按预期红：边缘起伏 " +
                "${coarse.edgeSpreadTop}/${coarse.edgeSpreadBottom}px（>2 ✓）、轴向 ${coarse.axialDip} ✓",
        )
    }

    /**
     * **网页那条直线（`lineMix50` / `lineMix0`）当真值** ✓：
     * 网页自己把"轴向 alpha / 每列 50% 交点"三个数组交回来（`profile` ✓），
     * 这边用**同一串输入点**跑生产链路、**同一个算法**取剖面，逐项比 ✓。
     */
    private fun compareWithWebLine(failures: MutableList<String>) {
        val f = listOf(File("../docs/brush-lab-simple-paper-truth.json"), File("docs/brush-lab-simple-paper-truth.json"))
            .firstOrNull { it.isFile }
        if (f == null) {
            failures += "纸纹真值文件找不到（先在 tools/webview-spike 里跑一次 `--args=paper`）✗"
            return
        }
        val truth = org.json.JSONObject(f.readText())
        val groups = truth.getJSONArray("groups")
        for (gi in 0 until groups.length()) {
            val g = groups.getJSONObject(gi)
            val id = g.getString("id")
            if (!id.startsWith("lineMix")) continue
            val prof = g.optJSONObject("profile") ?: continue
            val open = id == "lineMix50"
            val probe = if (open) {
                run("web-line-$id", wc1(paperOn = true, strength = 50f))
            } else {
                run("web-line-$id", wc1(paperOn = false, strength = 0f))
            }
            val session = probe.session
            val x0 = prof.getInt("x0")
            val x1 = prof.getInt("x1")
            val yMid = prof.getInt("yMid")
            val ax = prof.getJSONArray("axial")
            val tp = prof.getJSONArray("top")
            val bt = prof.getJSONArray("bottom")
            var maxAx = 0
            var maxTop = 0
            var maxBot = 0
            var n = 0
            for (i in 0 until minOf(ax.length(), x1 - x0 + 1)) {
                val px = x0 + i
                val mineA = (session.pixels[yMid * w + px] ushr 24) and 0xFF
                maxAx = maxOf(maxAx, abs(mineA - ax.getInt(i)))
                var myTop = -1
                var myBot = -1
                for (py in 1 until h - 1) {
                    if (((session.pixels[py * w + px] ushr 24) and 0xFF) >= 128) {
                        myTop = py
                        break
                    }
                }
                for (py in h - 2 downTo 1) {
                    if (((session.pixels[py * w + px] ushr 24) and 0xFF) >= 128) {
                        myBot = py
                        break
                    }
                }
                if (myTop >= 0 && tp.getInt(i) >= 0) maxTop = maxOf(maxTop, abs(myTop - tp.getInt(i)))
                if (myBot >= 0 && bt.getInt(i) >= 0) maxBot = maxOf(maxBot, abs(myBot - bt.getInt(i)))
                n++
            }
            println(
                "[㉜c-连续] 网页那条直线 $id（$x0..$x1 ✓）逐项比：轴向 alpha 最大差 **$maxAx**（0..255）、" +
                    "每列 50% 交点最大差 上 $maxTop / 下 $maxBot（像素 ✓）、比了 $n 列",
            )
            // 实测：轴向 ≤ 3、上下交点 ≤ 1px（阈值留一点余量 ✓）
            if (maxAx > 6) failures += "[$id] 轴向 alpha 与网页差 $maxAx > 6 ✗"
            if (maxTop > 2 || maxBot > 2) failures += "[$id] 50% 交点与网页差 上 $maxTop / 下 $maxBot > 2px ✗"
        }
    }
}
