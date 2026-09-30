package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.CanvasTool
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.screens.BrushPresets
import com.kallan.naistudio.state.AppState
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **第 ㉟③ 探针：用户「**还是一个个圆组成的笔画**」到底卡在哪一条** ✗ ——
 * **真机口径**（同一层、自由曲线、多档压力、快笔速 ✓），`wc1` vs `wc3` 各画一条 ✓。
 *
 * ## 为什么必须新开一条探针（上一批的判据不够用 ✗）
 *
 * `ContinuousStrokeCoverageProbeTest` 量的是**离屏空白层 + 一条直线 + 恒定压力** ✓ ——
 * 它证明的是"**合成口径**本身没有周期性起伏"✓（凹陷 0.0 ✓），但**真机**是：
 *  · **在已有内容的层上**画 ✓；· **自由曲线**（轴线一直在动 ✓）；· **笔速快**（采样点稀 ✓）；
 *  · **压力逐点变**（逐颗半径不同 ✓）；· **混色开着**（每颗采样色不同 ✓）。
 * ⇒ 这几条**都可能**让"还是一串圆"，而它们**一条都没量过** ✗。
 *
 * ## 本探针量什么（**逐颗数组** ✓，不是"看着像" ✗）
 *
 * 从 `session.dabProbeLog` 拿**这一笔的每一颗**：`plan.alpha` / `radius` / `sampled` / `mixT` ✓，
 * 再算三个数：
 *  ① **alpha 方差**（逐颗 ✓）—— 混色采样让 alpha/色逐颗剧变时它会大 ✗；
 *  ② **radius 方差**（逐颗 ✓）—— `sizeJitter` 的来路 ✓；
 *  ③ **轴向凹陷**（沿路径 ✓）—— "一颗颗圆"的**正主**✓（与 `PaperStrokeContinuityTest` 同口径 ✓）。
 *
 * 另加两个"真机才有"的量：
 *  ④ **采样点的平均间距**（px ✓）—— 快笔速下点稀 ⇒ 走位靠 carry 内插 ✓，看它够不够密；
 *  ⑤ **笔尖重叠倍数**（= `2r / step` ✓）—— < 1 就是"根本没叠上"✗（那必然是一串圆 ✓）。
 *
 * ⚠️ **只读探针** ✓：不改任何生产语义 ✗（不开/关任何开关 ✓，只**替换当前笔刷**再画 ✓）。
 */
class RealCanvasWatercolorProbeTest {

    private val platform: Platform = desktopPlatform()

    private fun freshState(): AppState {
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .apply()
        return AppState(platform).also { it.ensureComicBoardLoaded() }
    }

    private fun openSession(state: AppState, width: Int, height: Int): ImageEditSession {
        state.setComicBoardBase(width = width, height = height)
        state.chooseComicPaintTool(CanvasTool.DRAW)
        val page = requireNotNull(state.comicBoardPage) { "先决条件：这一页应该在" }
        val base = requireNotNull(page.layers.firstOrNull { it.kind == ComicLayer.Kind.BASE }) {
            "先决条件：底板层应该在"
        }
        assertTrue("底板层必须能开始编辑", state.beginComicLayerPaint(base.id))
        val s = requireNotNull(state.comicPaintSession) { "开始之后就该有一段会话" }
        s.dabProbeEnabled = true
        return s
    }

    private class Probe(
        val id: String,
        val presetId: String,
        val coverageOn: Boolean,
        val dabs: Int,
        val ink: Int,
        /** ① 逐颗 `plan.alpha` 的方差 ✓（混色/水分让 alpha 逐颗剧变的证据 ✓）。 */
        val alphaVar: Double,
        val alphaMin: Double,
        val alphaMax: Double,
        /** ② 逐颗半径的方差 ✓（`sizeJitter` 的来路 ✓）。 */
        val radiusVar: Double,
        val radiusMin: Double,
        val radiusMax: Double,
        /** ③ 轴向凹陷（alpha 0..255 ✓ —— "一颗颗圆"的正主 ✓）。 */
        val axialDip: Double,
        val axialMin: Int,
        val axialMax: Int,
        /** ④ 输入采样点平均间距（px ✓）。 */
        val meanInputStep: Double,
        /** ⑤ 笔尖重叠倍数（`2r / 走位步距` ✓ —— < 1 = 根本没叠上 ✗）。 */
        val overlap: Double,
        /** 混色开了没（`sampled` / `mixT` 的逐颗统计 ✓）。 */
        val sampledCount: Int,
        val mixTMax: Double,
    )

    /**
     * 画一条**真机口径**的曲线 ✓：同一层、自由正弦曲线、**压力逐点变**、可指定笔速 ✓。
     *
     * @param presetId 用哪支预设（`wc1` / `wc3` ✓）
     * @param speedPx 每次 `comicPaintStrokeTo` 走多远（**快笔速 = 采样稀** ✓）
     * @param pressureWave 压力是否逐点起伏（真手写就是这样的 ✓）
     */
    private fun run(
        presetId: String,
        speedPx: Float,
        pressureWave: Boolean,
        width: Int = 1400,
        height: Int = 1000,
    ): Probe {
        val preset = BrushPresets.firstOrNull { it.id == presetId }
            ?: throw AssertionError("预设 $presetId 找不到 ✗")
        val state = freshState()
        val session = openSession(state, width, height)
        // 把这一支预设**整套装进当前笔** ✓（与面板点一下**同一条路** ✓ —— 见 `BrushPanel.kt:597` ✓）
        state.applyComicPaintBrush(preset.spec)
        state.setComicPaintBrush(preset.sizePx)
        // 输入侧稳定器关掉 ✓（与 `ComicPaintStrokeContinuityTest` 同一个理由 ✓：
        // 它改的是**喂进去的点**、不是引擎 ✓，开着会把"路径形状"这件事搅进来 ✗）
        state.chooseComicPaintStabilize(0f)

        val y0 = height / 2f
        val x0 = 40f
        val x1 = (width - 40).toFloat()
        val firstP = if (pressureWave) 0.35f else 0.85f
        state.beginComicPaintStroke(
            x = x0, y = y0, pressure = firstP,
            screenX = x0 * 0.62f, screenY = y0 * 0.62f, zoom = 0.876f, dpr = 1f,
        )
        var samples = 0
        var x = x0
        while (x < x1) {
            x = (x + speedPx).coerceAtMost(x1)
            val t = (x - x0) / (x1 - x0)
            val yy = y0 + 120f * sin((t * 6.0)).toFloat()
            // **压力逐点起伏** ✓：0.25 ~ 1.0 来回（真手写的粗细变化 ✓）
            val p = if (pressureWave) {
                (0.25 + 0.75 * (0.5 + 0.5 * sin((t * 14.0)))).toFloat()
            } else {
                0.85f
            }
            state.comicPaintStrokeTo(x, yy, p)
            samples++
        }
        val log = session.dabProbeLog.toList()
        state.endComicPaintStroke()

        // ---- ① / ② 逐颗 alpha 与 radius ----
        val alphas = log.map { it.plan.alpha.toDouble() }
        val radii = log.map { it.radius.toDouble() }
        fun varOf(v: List<Double>): Double {
            if (v.size < 2) return 0.0
            val m = v.sum() / v.size
            return v.sumOf { (it - m) * (it - m) } / v.size
        }
        // ---- ③ 轴向凹陷：沿这一笔的**笔尖中心线**逐列取 alpha（与 ㉜c/㉟① 同口径 ✓）----
        // 曲线档轴线在动 ⇒ 每一列用"那一列笔尖中心的 y"（最近的那颗 ✓）。
        val centerY = HashMap<Int, Float>()
        for (d in log) centerY[d.x.toInt()] = d.y
        fun alphaAt(px: Int, py: Int): Int {
            if (px < 0 || py < 0 || px >= width || py >= height) return 0
            return (session.pixels[py * width + px] ushr 24) and 0xFF
        }
        fun axialAlpha(px: Int): Int {
            val cy = centerY[px] ?: return alphaAt(px, y0.toInt())
            var best = 0
            for (dy in -3..3) best = maxOf(best, alphaAt(px, cy.toInt() + dy))
            return best
        }
        val axFrom = (log.first().x + log.first().radius).toInt().coerceIn(0, width - 1)
        val axTo = (log.last().x - log.last().radius).toInt().coerceIn(0, width - 1)
        var axMin = 255
        var axMax = 0
        for (px in axFrom..axTo) {
            val a = axialAlpha(px)
            if (a < axMin) axMin = a
            if (a > axMax) axMax = a
        }
        var dip = 0.0
        for (i in 0 until log.size - 1) {
            val c0 = log[i].x
            val c1 = log[i + 1].x
            if (c1 - c0 < 3f) continue
            val i0 = (c0 + 1f).toInt()
            val i1 = (c1 - 1f).toInt()
            if (i1 < i0) continue
            var inner = 255
            for (px in i0..i1) inner = minOf(inner, axialAlpha(px))
            val end = minOf(axialAlpha(c0.toInt()), axialAlpha(c1.toInt()))
            dip = maxOf(dip, (end - inner).coerceAtLeast(0).toDouble())
        }
        // ---- ⑤ 笔尖重叠倍数 = 2r / 走位步距（用 dabProbeLog 的 stepPixels ✓）----
        val meanStep = if (log.size > 1) {
            var s = 0.0
            for (i in 1 until log.size) s += (log[i].x - log[i - 1].x)
            s / (log.size - 1)
        } else {
            0.0
        }
        val meanR = if (radii.isEmpty()) 1.0 else radii.sum() / radii.size
        val overlap = if (meanStep > 0.01) (2 * meanR) / meanStep else 999.0
        var ink = 0
        for (p in session.pixels) if (((p ushr 24) and 0xFF) != 0) ink++
        val sampledCount = log.count { it.plan.sampled }
        val mixTMax = log.maxOfOrNull { it.plan.mixT.toDouble() } ?: 0.0
        return Probe(
            id = "$presetId speed=${speedPx}px 压力${if (pressureWave) "起伏" else "恒定"}",
            presetId = presetId,
            coverageOn = preset.spec.strokeCoverage,
            dabs = log.size,
            ink = ink,
            alphaVar = varOf(alphas),
            alphaMin = alphas.minOrNull() ?: 0.0,
            alphaMax = alphas.maxOrNull() ?: 0.0,
            radiusVar = varOf(radii),
            radiusMin = radii.minOrNull() ?: 0.0,
            radiusMax = radii.maxOrNull() ?: 0.0,
            axialDip = dip,
            axialMin = axMin,
            axialMax = axMax,
            meanInputStep = if (samples > 1) ((x1 - x0) / samples).toDouble() else 0.0,
            overlap = overlap,
            sampledCount = sampledCount,
            mixTMax = mixTMax,
        )
    }

    private fun report(p: Probe) {
        println(
            "[㉟③-真机] **${p.id}**（预设 ${p.presetId}、`strokeCoverage=${p.coverageOn}` ✓）\n" +
                "    笔尖 ${p.dabs} 颗、墨 ${p.ink} 像素、输入点平均间距 ${"%.1f".format(p.meanInputStep)}px\n" +
                "    ① **逐颗 alpha 方差 ${"%.6f".format(p.alphaVar)}**" +
                "（范围 ${"%.4f".format(p.alphaMin)} ~ ${"%.4f".format(p.alphaMax)}）\n" +
                "    ② **逐颗半径方差 ${"%.4f".format(p.radiusVar)}**" +
                "（${"%.2f".format(p.radiusMin)} ~ ${"%.2f".format(p.radiusMax)}px）\n" +
                "    ③ **轴向凹陷 ${p.axialDip}**（alpha ${p.axialMin} ~ ${p.axialMax}）\n" +
                "    ⑤ **笔尖重叠倍数 ${"%.2f".format(p.overlap)}×**" +
                "（< 1 ⇒ 根本没叠上 ✗）\n" +
                "    混色：采样 ${p.sampledCount}/${p.dabs} 颗、`mixT` 最大 ${"%.3f".format(p.mixTMax)} ✓",
        )
    }

    @Test
    fun which_link_breaks_continuity_on_a_real_canvas() {
        // 两档笔速 × 两种压力（真机就是"快笔 + 手在抖"✓）—— wc1（老路）vs wc3（新路）各画 ✓
        val cases = ArrayList<Probe>()
        for (presetId in listOf("wc1", "wc2", "wc3", "wc4")) {
            for (speed in listOf(6f, 18f, 34f)) {
                for (wave in listOf(true, false)) {
                    cases += run(presetId, speed, wave)
                }
            }
        }
        cases.forEach { report(it) }

        // ---- 汇总两张对照表（**这就是要交给委托方判定的数** ✓）----
        fun avg(id: String, sel: (Probe) -> Double): Double {
            val v = cases.filter { it.presetId == id }.map(sel)
            return if (v.isEmpty()) 0.0 else v.sum() / v.size
        }
        println("[㉟③-真机] ==== 逐档平均（每档 6 条：3 档笔速 × 2 种压力 ✓）====")
        println("[㉟③-真机] 预设 | strokeCoverage | ①alpha方差 | ②半径方差 | ③凹陷 | ⑤重叠倍数")
        for (id in listOf("wc1", "wc2", "wc3", "wc4")) {
            val on = cases.first { it.presetId == id }.coverageOn
            println(
                "[㉟③-真机]   ${id} | $on | ${"%.6f".format(avg(id) { it.alphaVar })} | " +
                    "${"%.3f".format(avg(id) { it.radiusVar })} | " +
                    "${"%.1f".format(avg(id) { it.axialDip })} | " +
                    "${"%.2f".format(avg(id) { it.overlap })}×",
            )
        }
        println(
            "[㉟③-真机] ==== 判定用的两个问题 ====\n" +
                "    Q1 开关开着（wc3/wc4）时，凹陷是不是**也**大？（是 ⇒ 开关治不了真机的圆 ✗）\n" +
                "    Q2 混色档的**逐颗 alpha 方差**是不是远大于 '单颗固定 alpha' 那一档？\n" +
                "       （是 ⇒ 混色采样确实在让 [墨量] 那本账起伏 ✗ —— 这正是委托方点名的第 1 条 ✓）",
        )
    }
}
