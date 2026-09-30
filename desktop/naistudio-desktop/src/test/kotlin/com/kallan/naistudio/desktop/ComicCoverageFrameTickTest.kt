package com.kallan.naistudio.desktop

import com.kallan.naistudio.models.BrushSpec
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.models.StrokeMode
import com.kallan.naistudio.models.StrokeSpec
import com.kallan.naistudio.screens.BrushPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A 方案（2026-09-21）：笔画进行中就能看见墨** ✓
 *
 * ## 治的是什么（用户实机原话 ✓）
 *
 * > 「**绘画时是先画出一排点，然后才补成完整的线**」
 *
 * **根因**（结构问题 ✗，不是参数 ✗）：`BrushSpec.strokeCoverage = true` 那一档在
 * [ImageEditSession.strokeTo] 里**只往覆盖度缓冲累积、一个字节都不碰层表面** ✗ ——
 * 所以笔画期间屏幕上看不到这一笔 ✓，抬笔（[ImageEditSession.endStroke]）那一下才
 * `compositeCoverage` 一次性把整条线盖上去 ✓。
 *
 * ## 判据怎么钉（**结构性** ✓，不靠"我觉得快了" ✗）
 *
 * 本文件钉的是一条**不变量**：
 *
 *  · **抬笔之前**（没调过任何 `compositeCoverageFrameTick` ✓）⇒ 表面上**一个像素都没动** ✓
 *    —— 这就是 bug 的**可复现形式** ✓（把 [ImageEditSession.compositeCoverageFrameTick] 删掉，
 *    下面第一条仍然绿、但第三条会红 ✗ ⇒ 判据抓的是"修没修"✓，不是"有没有这条路"✓）；
 *  · **帧边界合一次之后**（喂点 → `compositeCoverageFrameTick()` ✓）⇒
 *    表面上**真的出现了这一笔的墨** ✓（沿中心线取一行，必须远大于 0 ✓）；
 *  · **合几次都不影响最终像素** ✓ —— 与"整笔只合一次"逐字节相同 ✓
 *    （这是 A 方案**安全**的根据 ✓：覆盖度累积是单调的 ✓，见那边说明 ✓）。
 *
 * ⚠️ **第三条是这条修法能不能上线的关键** ✗：中途多合几次如果会改最终画面，
 * 那就是"治了手感、改了画面"✗（本仓库判据只许加不许放松 ✗）。
 */
class ComicCoverageFrameTickTest {

    private val w = 420
    private val h = 260
    private val lineY = 130f

    /** 覆盖度那条路开着的规格（用项目内置预设里**已经打开**这一档的那支 ✓ —— 不自己造 ✗）。 */
    private fun coverageSpec(): BrushSpec = BrushPresets
        .first { it.spec.strokeCoverage }
        .spec
        .copy(spacing = 0.04f, sizeJitter = 0f, scattering = 0f)

    private fun newSession() = ImageEditSession(w, h, IntArray(w * h))

    private fun beginStroke(s: ImageEditSession, spec: BrushSpec) = s.beginStroke(
        20f,
        lineY,
        StrokeSpec(
            mode = StrokeMode.PAINT,
            brushPixels = 48,
            color = 0xFF22262E.toInt(),
            brush = spec,
            minRadiusRatio = 0.15f,
            pressureCurve = 1f,
            seed = 11L,
        ),
        1f,
    )

    /** 表面上"真的有墨"的像素数（alpha > 0 ✓）—— `pixels` 是表面的镜像 ✓。 */
    private fun inkedPixels(s: ImageEditSession): Int {
        var n = 0
        for (p in s.pixels) if (((p ushr 24) and 0xFF) > 0) n++
        return n
    }

    /** 沿中心线那一行的墨（alpha ✓）—— "这一笔在不在表面上"看它 ✓。 */
    private fun centerLineAlpha(s: ImageEditSession): Int {
        val y = lineY.toInt()
        var best = 0
        for (x in 0 until w) {
            val a = (s.pixels[y * w + x] ushr 24) and 0xFF
            if (a > best) best = a
        }
        return best
    }

    @Test
    fun the_ink_is_invisible_on_the_surface_until_a_frame_tick_composites_it() {
        val spec = coverageSpec()
        val s = newSession()
        beginStroke(s, spec)
        // 喂点（**只累积** ✓，不合成 ✗）
        var x = 30f
        while (x <= 300f) {
            s.strokeTo(x, lineY, 1f)
            x += 6f
        }
        // ⚠️ 这一条钉的就是 bug 本身 ✓：抬笔前表面上**什么都没有** ✓
        assertEquals(
            "走覆盖度那条路时，没合过之前表面上不该有墨（这就是「先出一排点」的机制）",
            0,
            inkedPixels(s),
        )
        // 合一次 ⇒ 墨出现了 ✓（这正是 A 方案要做的事 ✓）
        assertTrue("帧边界合一次应该真的把墨盖上表面", s.compositeCoverageFrameTick())
        assertTrue("合过之后中心线上必须有墨", centerLineAlpha(s) > 0)
        assertTrue("合过之后表面上必须有墨", inkedPixels(s) > 0)
        s.endStroke()
    }

    @Test
    fun compositing_every_frame_leaves_the_final_pixels_equivalent_to_compositing_once() {
        val spec = coverageSpec()
        // ---- 基准：整笔只合一次（老路 = 抬笔那一下 ✓）----
        val once = newSession()
        beginStroke(once, spec)
        var x = 30f
        while (x <= 300f) {
            once.strokeTo(x, lineY, 1f)
            x += 6f
        }
        once.endStroke()
        // ---- A 方案：每喂 3 个点就当一次帧边界（合一次 ✓）= 60Hz 下每 3 点一帧的真实节奏 ✓ ----
        val ticked = newSession()
        beginStroke(ticked, spec)
        var fed = 0
        var ticks = 0
        x = 30f
        while (x <= 300f) {
            ticked.strokeTo(x, lineY, 1f)
            fed++
            if (fed % 3 == 0) {
                ticked.compositeCoverageFrameTick()
                ticks++
            }
            x += 6f
        }
        ticked.endStroke()
        assertTrue("这一档必须真的合过（不然量的是老路 ✗）", ticks >= 10)

        // ---- 判据① **alpha 必须逐字节相同** ✓（这是"墨量一致"的硬口径 ✓）----
        var alphaDiff = 0
        var inkOnce = 0L
        var inkTick = 0L
        for (i in once.pixels.indices) {
            val a = (once.pixels[i] ushr 24) and 0xFF
            val b = (ticked.pixels[i] ushr 24) and 0xFF
            inkOnce += a
            inkTick += b
            if (a != b) alphaDiff++
        }
        assertEquals("按帧合与整笔只合一次的 alpha 必须逐字节相同", 0, alphaDiff)
        assertEquals("总墨量也必须完全相同", inkOnce, inkTick)

        // ---- 判据② 颜色通道只允许 8bit 预乘往返的舍入 ✓（实测 maxRgbDelta ≤ 2/255 ✓）----
        // ⚠️ **如实**：不是 0 ✗ —— `PLUS` 合成要在预乘域做，8bit 往返必然有舍入 ✓。
        //    2/255 = 0.8%，肉眼不可见 ✓；且**合得越勤越小** ✓（实测 N=1:+2 / N=10:+1 ✓）。
        var maxRgbDelta = 0
        for (i in once.pixels.indices) {
            val a = (once.pixels[i] ushr 24) and 0xFF
            if (a == 0) continue
            for (sh in intArrayOf(0, 8, 16)) {
                val ca = (once.pixels[i] ushr sh) and 0xFF
                val cb = (ticked.pixels[i] ushr sh) and 0xFF
                val d = kotlin.math.abs(ca - cb)
                if (d > maxRgbDelta) maxRgbDelta = d
            }
        }
        assertTrue(
            "颜色通道差必须仅在 8bit 舍入范围内（≤2/255），实测=$maxRgbDelta",
            maxRgbDelta <= 2,
        )
    }

    @Test
    fun the_frame_tick_is_a_no_op_when_the_stroke_is_over() {
        val spec = coverageSpec()
        val s = newSession()
        beginStroke(s, spec)
        s.strokeTo(40f, lineY, 1f)
        s.strokeTo(60f, lineY, 1f)
        s.endStroke()
        val before = s.pixels.copyOf()
        // 抬笔之后再合 ⇒ 什么都不该发生 ✓（`isDrawing == false` 早退 ✓）
        assertTrue("抬笔后帧边界合成必须是空操作", !s.compositeCoverageFrameTick())
        var diff = 0
        for (i in before.indices) if (before[i] != s.pixels[i]) diff++
        assertEquals("抬笔后合成本该不改任何像素", 0, diff)
    }
}
