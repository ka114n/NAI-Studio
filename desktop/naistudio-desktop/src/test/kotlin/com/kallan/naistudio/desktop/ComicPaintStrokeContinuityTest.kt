package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.state.AppState
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **端到端"这一笔能不能连起来"**（用户 2026-09-20 实机报：「**还是一排点**」✗）。
 *
 * ## 为什么必须走这条路（而不是直接调 `ImageEditSession`）
 *
 * `PenDabEngineTest` 那一组钉的是**纯公式** ✓（半径 / 步距 / 椭圆 ✓），它**全绿** ✓ ——
 * 可用户实机还是看到了点 ✗。差的那一段正是"界面 → `AppState` → 会话"这条**接线**：
 *  · 默认笔刷直径 / `PEN_BRUSH_MIN_FACTOR` / `PEN_PRESSURE_CURVE` / `spacing` 到底是几 ✓；
 *  · 压力**真的**有没有逐点喂进去 ✓；
 *  · `begin` / `strokeTo` / `end` 的调用次序（`AppState` 那两层的口径 ✓）。
 * 所以这里**一个 `session.xxx` 都不直接调** ✗，全走 `AppState` 的三个方法 ✓
 * （和 `ComicModeScreen` 那两条手势逐字同一条路 ✓）。
 *
 * ## 判据：**逐列扫像素**（不是"我觉得看着连" ✓）
 *
 * 笔画从起点到终点，**每一列**都必须有不透明像素 ✓ —— 一个 ≥2 像素的洞都不许有 ✗
 * （"一排点"在像素里就是"好几列全透明" ✓，这条断言就是它的照妖镜 ✓）。
 *
 * ## 参数：**界面默认值** + **真实压力档** + **两种笔速**
 *
 *  · 笔刷直径 = `comicPaintBrushPixels` 的默认值 ✓（测试里**不碰**滑杆 ✓）；
 *  · `spacing` = `BrushSpec` 的默认（= `ImageEditOps.DEFAULT_SPACING_PERCENT` = 10% ✓ ——
 *    第二批把它从 `StrokeSpec` 搬进了 `BrushSpec` ✓，**默认值一个字没改** ✓）；
 *  · `nibRatio` / `angleControl` = `AppState` 的默认 ✓；
 *  · 压力 0.05 / 0.3 / 1.0 三档各一条 ✓ —— **0.05 那条最容易露馅** ⚠️
 *    （半径被压到最小，步距必须**跟着**变小 ✓）；
 *  · 笔速：慢 2px/次 / 快 20px/次 ✓（模拟 60~120Hz 的采样 ✓）。
 */
class ComicPaintStrokeContinuityTest {

    private val platform: Platform = desktopPlatform()

    private fun freshState(): AppState {
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .apply()
        return AppState(platform).also { it.ensureComicBoardLoaded() }
    }

    /** 开一张空白的页 + 把底板层开成绘画会话 ✓（和 `ComicPaintLayerTest` 同一套 ✓）。 */
    private fun openSession(state: AppState, width: Int, height: Int): ImageEditSession {
        state.setComicBoardBase(width = width, height = height)
        val page = requireNotNull(state.comicBoardPage) { "先决条件：这一页应该在" }
        val base = requireNotNull(page.layers.firstOrNull { it.kind == ComicLayer.Kind.BASE }) {
            "先决条件：底板层应该在（normalizedOverlays 保证有且只有一层 ✓）"
        }
        assertTrue("底板层必须能开始编辑", state.beginComicLayerPaint(base.id))
        return requireNotNull(state.comicPaintSession) { "开始之后就该有一段会话" }
    }

    /** 一笔量出来的东西（**测试里唯一的判据来源** ✓）。 */
    private class Probe(
        val pressure: Float,
        val speedPx: Float,
        val samples: Int,
        val dabs: Int,
        val segments: Int,
        val distance: Float,
        val radiusMin: Float,
        val radiusMax: Float,
        val stepMin: Float,
        val stepMax: Float,
        val worstStepOverR: Float,
        /** 最长的"整列全透明"连续段（0 = 一次都没有 ✓）。 */
        val maxGap: Int,
    ) {
        fun line(): String = "pressure=$pressure speed=${fmt(speedPx)}px samples=$samples dabs=$dabs " +
            "segments=$segments dabsPerSegment=${fmt(if (segments > 0) dabs.toFloat() / segments else 0f)} " +
            "dist=${fmt(distance)}px r=${fmt(radiusMin)}..${fmt(radiusMax)} " +
            "step=${fmt(stepMin)}..${fmt(stepMax)} worstStepOverR=${fmt(worstStepOverR)} " +
            "maxGap=$maxGap"

        /** 小数点是固定的 `.` ✓（区域设置会写出 `3,2`，测试输出就不好读了 ✗）。 */
        private fun fmt(value: Float): String = String.format(java.util.Locale.US, "%.2f", value)
    }

    /**
     * 照界面那条路画一笔：`beginComicPaintStroke` → N 次 `comicPaintStrokeTo` → `endComicPaintStroke` ✓。
     *
     * 路径是一条**轻微的正弦曲线**（用户图里那种"平滑曲线" ✓；斜率 ≈ 0.17 « 1，
     * 所以"每一列都该被盖住"这个判据是成立的 ✓）。
     */
    private fun strokeWith(
        width: Int,
        height: Int,
        pressure: Float,
        speedPx: Float,
    ): Triple<ImageEditSession, AppState, Probe> {
        val state = freshState()
        val session = openSession(state, width, height)
        // ⚠️⚠️ 第 ㉚ 批：**这一组测试把输入侧稳定器显式关掉** ✗ —— 改动的是"喂进去的点"，
        // 不是判据 ✗。理由（一条就够）：
        //  · 本文件钉的是**走位几何**（步距 vs 笔尖半径 ⇒ 会不会断成一排点 ✓），那是**引擎**属性 ✓；
        //  · 稳定器（EMA ✓）是**输入侧**的变换 ✓ —— 网页 `brush-lab-simple.html:751-752` 就在
        //    `walkTo()` **之前**把它作用掉 ✓，所以它**按定义**会改变路径形状 ✓；
        //  · 而 EMA **天然会滞后**（稳态滞后 ≈ 采样步长 × (1−k)/k ✓，35% ⇒ k=0.65 ⇒ 约 0.54 步 ✓），
        //    网页的 `endStroke`（`:758-769`）**也不做任何"追上"** ✗ ⇒ 线的**尾端**本来就短一截 ✓。
        //    开着它去断言"最后一列也必须有色"就是在断言"稳定器不存在" ✗ —— 那是把判据钉错了对象 ✓。
        // ⇒ 这里关掉它、**判据一个字都不放松** ✓（断点判据照旧逐列扫 ✓）；
        //   稳定器**开着**时的行为由 `ComicPaintStabilizerTest` 单独钉 ✓（含这条滞后 ✓，不藏 ✗）。
        state.chooseComicPaintStabilize(0f)
        val y = height / 2f
        val x0 = 30f
        val x1 = (width - 30).toFloat()

        // ⚠️ 屏幕坐标 / zoom / dpr 只是喂 `[PenDab]` 日志的 ✓（这里给真实形状的一组 ✓）
        state.beginComicPaintStroke(
            x = x0,
            y = y,
            pressure = pressure,
            screenX = x0 * 0.62f,
            screenY = y * 0.62f,
            zoom = 0.876f,
            dpr = 1f,
        )
        var samples = 0
        var x = x0
        while (x < x1) {
            x = (x + speedPx).coerceAtMost(x1)
            val yy = y + 10f * sin(((x - x0) / 60f).toDouble()).toFloat()
            state.comicPaintStrokeTo(x, yy, pressure)
            samples++
        }
        val stats = session.strokeDabStats()
        state.endComicPaintStroke()

        // ---- 逐列扫像素：每一列都要有不透明像素 ✓ ----
        val pixels = session.pixels
        var maxGap = 0
        var run = 0
        for (col in x0.toInt()..x1.toInt()) {
            var covered = false
            for (row in 0 until height) {
                if (((pixels[row * width + col] ushr 24) and 0xFF) > 0) {
                    covered = true
                    break
                }
            }
            if (covered) {
                run = 0
            } else {
                run++
                if (run > maxGap) maxGap = run
            }
        }

        val probe = Probe(
            pressure = pressure,
            speedPx = speedPx,
            samples = samples,
            dabs = stats.dabs,
            segments = stats.segments,
            distance = stats.distance,
            radiusMin = stats.radiusMin,
            radiusMax = stats.radiusMax,
            stepMin = stats.stepMin,
            stepMax = stats.stepMax,
            worstStepOverR = stats.worstStepOverRadius,
            maxGap = maxGap,
        )
        // 这一行走**测试的输出** ✓（红的时候一眼就能看到数字 ✓）。
        // ⚠️ `[PenDab]` 那两行走的是 `logInfo` ⇒ 在测试里落到**隔离档案**那份 app.log ✓
        //    （`build/test-home/NAI Studio/app.log` ✓ —— `DesktopPlatform` 的 init 装了文件后端 ✓，
        //    所以它**不会**出现在测试控制台上 ✓；这也是"日志没写进画布"那条口径的验证点 ✓）。
        println("[Probe] ${probe.line()}")
        return Triple(session, state, probe)
    }

    /** 三档压力各画一笔（同一种笔速），逐条断言 ✓。 */
    private fun assertContinuousAt(speedPx: Float, minDabsPerSegment: Int) {
        val width = 800
        val height = 300
        for (pressure in listOf(0.05f, 0.3f, 1.0f)) {
            val (_, _, probe) = strokeWith(width, height, pressure, speedPx)

            assertEquals(
                "压力 $pressure / 每步 ${speedPx}px：这一笔**每一列都得有像素** ✓ —— " +
                    "实测最长有 ${probe.maxGap} 列全透明（用户报的「一排点」在像素里就是它 ✗）｜" +
                    probe.line(),
                0,
                probe.maxGap,
            )
            assertTrue(
                "压力 $pressure / 每步 ${speedPx}px：`step / (2 × r)` 的最坏值必须 ≤ 0.5 ✓" +
                    "（> 1 = 相邻笔尖完全不重叠 = 必断 ✗）｜${probe.line()}",
                probe.worstStepOverR <= 0.5f,
            )
            assertTrue(
                "压力 $pressure / 每步 ${speedPx}px：步距必须**跟着**笔尖半径走 ✓" +
                    "（步距 > 笔尖直径就是老 bug ✗）｜${probe.line()}",
                probe.stepMax <= probe.radiusMax * 2f + 1e-4f,
            )
            assertTrue(
                "压力 $pressure / 每步 ${speedPx}px：这一段真的落了笔尖 ✓｜${probe.line()}",
                probe.dabs > 0 && probe.distance > 700f,
            )
            // ★ "一排点"最直接的判据：**快笔时一段里必须落好几颗笔尖** ✓
            //   `dabs / segments ≈ 1` = 每个输入事件只盖一颗 ⇒ 点距 = 手速 × 采样间隔 ✗✗
            //   （用户那张图就是它：间距跟着手速变 ✓，而且点和点之间是**纯白** ✓）。
            //   ⚠️ 慢笔（2px/次）**不该**要求这个 ✗：步距比一段还长时本来就会几段才落一颗 ✓
            //   （carry 累加 ✓ —— 那不是 bug，是省算力 ✓），所以这一条只在快笔那一档开 ✓。
            if (minDabsPerSegment > 0) {
                assertTrue(
                    "压力 $pressure / 每步 ${speedPx}px：**一段里必须落 $minDabsPerSegment 颗以上笔尖** ✓" +
                        "（`dabs / segments ≈ 1` 就是每个事件盖一颗 = 一排点 ✗）｜${probe.line()}",
                    probe.dabs >= minDabsPerSegment * probe.segments,
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // ① 慢笔：每步 2px（≈ 手慢的时候 / 高采样率 ✓）
    // ------------------------------------------------------------------

    @Test
    fun a_slow_pen_leaves_no_hole_at_any_pressure() {
        assertContinuousAt(speedPx = 2f, minDabsPerSegment = 0)
    }

    // ------------------------------------------------------------------
    // ② 快笔：每步 20px —— **每一段都要落好几颗笔尖** ✓
    // ------------------------------------------------------------------

    @Test
    fun b_fast_pen_leaves_no_hole_and_still_stamps_several_dabs_per_sample() {
        assertContinuousAt(speedPx = 20f, minDabsPerSegment = 3)

        // 快笔这一维单独再钉一条：每一段（20px）都得落**好几颗**笔尖 ✓
        //（一段只落一颗的话，速度一快就必然点状 ✗ —— 这是"走位"存在的原因 ✓）
        for (pressure in listOf(0.05f, 0.3f, 1.0f)) {
            val (_, _, probe) = strokeWith(800, 300, pressure, 20f)
            assertTrue(
                "压力 $pressure：快笔每一段（20px）至少该落 3 颗笔尖 ✓｜${probe.line()}",
                probe.dabs >= 3 * probe.samples,
            )
        }
    }

    // ------------------------------------------------------------------
    // ③ 低压力那一档**最容易露馅** ⚠️：半径被压到最小，步距必须跟着变小 ✓
    // ------------------------------------------------------------------

    @Test
    fun c_the_lightest_pressure_shrinks_the_step_together_with_the_nib() {
        val (_, _, light) = strokeWith(800, 300, pressure = 0.05f, speedPx = 20f)
        val (_, _, full) = strokeWith(800, 300, pressure = 1.0f, speedPx = 20f)

        assertTrue(
            "轻按（0.05）的笔尖必须**明显更小** ✓｜light=${light.line()} full=${full.line()}",
            light.radiusMax < full.radiusMax * 0.5f,
        )
        assertTrue(
            "轻按（0.05）的步距也必须**跟着变小** ✓（步距不跟着缩 ⇒ 必然一排点 ✗）｜" +
                "light=${light.line()} full=${full.line()}",
            light.stepMax < full.stepMax * 0.5f,
        )
        // 最狠的一条：轻按时**步距仍不许超过笔尖直径的一半** ✓（= `step / (2r) ≤ 0.5` ✓）
        assertTrue(
            "轻按时 `step / (2r)` 必须 ≤ 0.5 ✓｜${light.line()}",
            light.worstStepOverR <= 0.5f,
        )
        assertEquals("轻按那条也不许有洞 ✓｜${light.line()}", 0, light.maxGap)
    }
}
