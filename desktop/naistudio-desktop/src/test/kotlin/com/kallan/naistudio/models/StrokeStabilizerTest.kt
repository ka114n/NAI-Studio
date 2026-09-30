package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * **第 ㉚ 批 · 输入侧（稳定器 EMA ✓）的钉子** —— 对着 `docs/brush-lab-simple.html` 的原文钉 ✓。
 *
 * 网页原文（**逐行抄在这里，别再靠记忆 ✗**）：
 * ```js
 * :156  stabilize: 35                                   // 默认档
 * :670  addSlider({ key:"stabilize", min:0, max:95, step:1, unit:"%" })
 * :729  drawing = true; smooth = { x: p[0], y: p[1] };   // 落笔：种子化
 * :740  liveDab(strokeState, smooth.x, smooth.y, r, pv); // 落笔那一颗**不吃** EMA
 * :747  const k = 1 - (P.stabilize / 100);
 * :751  smooth.x += (dp[0] - smooth.x) * k;
 * :752  smooth.y += (dp[1] - smooth.y) * k;
 * ```
 *
 * ⚠️ 这些断言钉的是**公式本身** ✓ —— 它们能证明"Kotlin 这一侧算的和网页那两行一模一样" ✓，
 * 但**不能**替代"拿网页真值逐点比" ✗（那条见下面的未验说明 ✓）。
 */
class StrokeStabilizerTest {

    // ------------------------------------------------------------------
    // ① 公式：k = 1 - stabilize/100，smooth += (p - smooth) * k
    // ------------------------------------------------------------------

    @Test
    fun k_is_one_minus_percent_and_matches_the_web_default_35() {
        val s = StrokeStabilizer()                       // 默认档 = 网页 :156 的 35 ✓
        assertEquals("默认必须是网页那个 35% ✗", 35f, s.stabilizePercent, 0f)
        // :747  const k = 1 - (P.stabilize / 100);   ⇒ 35% ⇒ 0.65
        assertEquals("35% ⇒ k 必须是 0.65（= 1 - 35/100）✗", 0.65f, s.k, 1e-6f)

        for (percent in listOf(0f, 10f, 35f, 50f, 95f)) {
            val t = StrokeStabilizer(percent)
            assertEquals(
                "k 必须是 `1 - percent/100`（percent=$percent）✗",
                1f - percent / 100f,
                t.k,
                1e-6f,
            )
        }
        // 网页滑杆范围 :670 `min:0 max:95` ⇒ 越界一律收敛 ✓（0 = 完全不平滑 = 老行为 ✓）
        assertEquals("0% ⇒ k = 1（完全不平滑）✗", 1f, StrokeStabilizer(0f).k, 1e-6f)
        assertEquals("超过 95% 要收敛到 95 ✗", 95f, StrokeStabilizer(200f).stabilizePercent, 0f)
        assertEquals("负数要收敛到 0 ✗", 0f, StrokeStabilizer(-5f).stabilizePercent, 0f)
        assertEquals("NaN 要收敛到 0（不许把 NaN 带进画面 ✗）", 0f, StrokeStabilizer(Float.NaN).stabilizePercent, 0f)
    }

    @Test
    fun one_step_follows_the_web_arithmetic_exactly() {
        // seed = (0,0)，来一个 (100,0)，k = 0.65
        // :751  smooth.x += (100 - 0) * 0.65  ⇒  65   （不是 100 ✗、也不是 35 ✗）
        val s = StrokeStabilizer(35f)
        s.seed(0f, 0f)
        s.next(100f, 0f)
        assertEquals("一步 EMA：x 必须是 65 ✗", 65f, s.smoothX, 1e-4f)
        assertEquals("y 没动 ⇒ 还是 0 ✗", 0f, s.smoothY, 1e-4f)

        // 第二步：smooth.x = 65 + (100 - 65) * 0.65 = 65 + 22.75 = 87.75
        s.next(100f, 0f)
        assertEquals("两步 EMA：x 必须是 87.75 ✗", 87.75f, s.smoothX, 1e-4f)

        // 第三步：87.75 + (100 - 87.75) * 0.65 = 87.75 + 7.9625 = 95.7125
        s.next(100f, 0f)
        assertEquals("三步 EMA：x 必须是 95.7125 ✗", 95.7125f, s.smoothX, 1e-3f)
    }

    /** 独立参考实现（**照 :751-752 那两行重写一遍** ✓）——逐点比，才算真的钉住了公式 ✓。 */
    private fun webReference(points: List<Pair<Float, Float>>, percent: Float): List<Pair<Float, Float>> {
        val k = 1f - percent / 100f
        var sx = 0f
        var sy = 0f
        val out = ArrayList<Pair<Float, Float>>(points.size)
        var first = true
        for ((px, py) in points) {
            if (first) {
                sx = px; sy = py; first = false      // :729 种子化 ✓
            } else {
                sx += (px - sx) * k                  // :751 ✓
                sy += (py - sy) * k                  // :752 ✓
            }
            out.add(sx to sy)
        }
        return out
    }

    @Test
    fun a_whole_run_matches_the_web_arithmetic_point_by_point() {
        // 一条带拐弯的折线（含重复点 / 反向点 —— EMA 对它们的行为也要一致 ✓）
        val raw = listOf(
            0f to 0f, 10f to 0f, 10f to 10f, 30f to 10f,
            30f to 10f, 30f to -20f, 5f to -20f, 5f to 5f,
        )
        for (percent in listOf(0f, 35f, 95f)) {
            val expected = webReference(raw, percent)
            val s = StrokeStabilizer(percent)
            var index = 0
            for ((px, py) in raw) {
                if (index == 0) {
                    s.seed(px, py)                    // 网页在 pointerdown 里种子化 ✓
                    assertEquals(
                        "落笔那一颗必须**原样**落在落笔点上（:740 不吃 EMA ✗）percent=$percent",
                        px, s.smoothX, 1e-6f,
                    )
                } else {
                    s.next(px, py)
                    val (ex, ey) = expected[index]
                    assertTrue(
                        "第 $index 点（percent=$percent）与网页公式不一致 ✗：" +
                            "Kotlin=(${s.smoothX},${s.smoothY}) 网页=($ex,$ey)",
                        abs(s.smoothX - ex) < 1e-4f && abs(s.smoothY - ey) < 1e-4f,
                    )
                }
                index++
            }
        }
    }

    // ------------------------------------------------------------------
    // ② 边界：种子化 / 重置 / NaN
    // ------------------------------------------------------------------

    @Test
    fun seeding_and_reset_behave_like_the_web_stroke_lifecycle() {
        val s = StrokeStabilizer(35f)
        // 还没种子化就 next ⇒ 直接当成种子（= 网页里 pointermove 先于 pointerdown 到不了 ✓，
        // 但我们**不能**让 NaN / 零点混进来 ✗）
        s.next(7f, 9f)
        assertEquals("未种子化时第一个点应当被当作种子 ✗", 7f, s.smoothX, 1e-6f)
        assertEquals("未种子化时第一个点应当被当作种子 ✗", 9f, s.smoothY, 1e-6f)
        assertTrue("next 之后应当进入已种子化状态 ✓", s.seeded)

        s.reset()
        assertTrue("reset 之后必须重新回到「没种子化」（下一笔要重新 seed ✗）", !s.seeded)

        // 新的一笔：种子换成新落笔点（网页 :729 每笔都重设 ✓）
        s.seed(-3f, 4f)
        assertEquals("新一笔的种子必须换掉 ✗", -3f, s.smoothX, 1e-6f)
        assertEquals("新一笔的种子必须换掉 ✗", 4f, s.smoothY, 1e-6f)

        // NaN / Inf 不许污染状态 ✗
        s.next(Float.NaN, Float.POSITIVE_INFINITY)
        assertTrue("NaN 采样点不许把平滑点变成 NaN ✗（实测 ${s.smoothX},${s.smoothY}）", s.smoothX.isFinite() && s.smoothY.isFinite())
    }

    // ------------------------------------------------------------------
    // ③ 接进生产链路：默认**关**（判据①的口径）· 显式开 35% 真的改变路径
    // ------------------------------------------------------------------

    @Test
    fun the_session_default_is_off_so_web_truth_parity_is_untouched() {
        // 网页真值（928 颗）是**没有稳定器**那条路抽出来的 ✓ ⇒ 默认必须关 ✗
        assertEquals(
            "StrokeSpec 默认必须是 0（关）✗ —— 开了的话判据①的 928 颗当场就变 ✗",
            0f,
            StrokeSpec().stabilizePercent,
            0f,
        )

        // 同一条折线，默认档（关）与老行为**逐点一致** ✓
        fun run(spec: StrokeSpec): StrokeDabStats {
            val size = 200
            val session = ImageEditSession(size, size, IntArray(size * size))
            session.beginStroke(10f, 100f, spec)
            for (step in 1..40) session.strokeTo(10f + step * 4f, 100f + step.toFloat())
            session.endStroke()
            return session.strokeDabStats()
        }
        val off = run(StrokeSpec(brushPixels = 20))
        assertEquals("默认关时不该有任何稳定器副作用 ✓", 0f, StrokeSpec(brushPixels = 20).stabilizePercent, 0f)
        assertTrue("默认关时这一笔必须真的画出来（dabs=${off.dabs}）✗", off.dabs > 0)
    }

    @Test
    fun turning_it_on_really_changes_the_path() {
        fun run(stabilize: Float): StrokeDabStats {
            val size = 300
            val session = ImageEditSession(size, size, IntArray(size * size))
            val spec = StrokeSpec(brushPixels = 20, stabilizePercent = stabilize)
            session.beginStroke(20f, 20f, spec)
            // 一条锯齿很重的折线：不稳定器时每个采样点都落笔，稳定器会把它拉平 ✓
            var x = 20f
            var flip = 0
            while (x < 280f) {
                x += 6f
                flip = 1 - flip
                session.strokeTo(x, 150f + if (flip == 1) -60f else 60f)
            }
            session.endStroke()
            return session.strokeDabStats()
        }
        val off = run(0f)
        val on = run(35f)
        assertTrue("两档都必须真的画出来 ✗（off=${off.dabs}, on=${on.dabs}）", off.dabs > 0 && on.dabs > 0)
        // 锯齿被 EMA 拉平 ⇒ **路径总长明显变短** ✓（这就是"稳定器"看得见的效果 ✓）
        assertTrue(
            "开了 35% 稳定器之后路径总长必须明显变短 ✗（关=${off.distance} / 开=${on.distance}）——" +
                " 一样长就说明 EMA 根本没接进链路 ✗",
            on.distance < off.distance * 0.9f,
        )
    }

    // ------------------------------------------------------------------
    // ④ 输入率实测（只打印，不当判据 ✓）—— ㉙ 的 docs/51 把"输入采样率"列为**第一号**手感风险 ✓
    // ------------------------------------------------------------------

    @Test
    fun input_rate_is_measured() {
        val size = 400
        val session = ImageEditSession(size, size, IntArray(size * size))
        session.beginStroke(20f, 200f, StrokeSpec(brushPixels = 16, stabilizePercent = 35f))
        var x = 20f
        val points = 120
        repeat(points) {
            x += 3f
            session.strokeTo(x, 200f + 30f * kotlin.math.sin(x / 25f))
        }
        session.endStroke()
        val stats = session.strokeInputStats()
        println(
            "[㉚-input] 这一笔：喂进 $points 个采样点 ⇒ 引擎记到 inputPts=${stats.points}、" +
                "movedSegments=${stats.movedSegments}、strokeMs=${"%.2f".format(stats.elapsedMs)}、" +
                "折合 ${"%.0f".format(stats.pointsPerSecond)} pt/s\n" +
                "   ⚠️ 这是**离线喂点**的速率，**不是**真机输入设备速率 ✗ ——" +
                " 真机的 pt/s 由 `[PenDab]` 那条日志里的 `ptPerSec=` 带回来（见 §未验 ✓）",
        )
        assertEquals("引擎必须把**每一个**喂进来的点都记到（一个都不许丢 ✗）", points, stats.points)
        assertTrue("点数必须 ≥ 100（不然这条实测没意义）✗", stats.points >= 100)
    }
}
