package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.models.StrokeStabilizer
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.state.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **第 ㉚ 批 · 输入侧稳定器在"界面那条真路"上的行为** ✓
 * （`AppState.beginComicPaintStroke` → `comicPaintStrokeTo` → `endComicPaintStroke` ✓，
 *  和 `ComicModeScreen` 的手势逐字同一条路 ✓）。
 *
 * ## 为什么单独一个文件（不是塞进 `ComicPaintStrokeContinuityTest`）
 *
 * 那个文件钉的是**引擎走位几何**（步距 vs 笔尖半径 ⇒ 会不会断成一排点 ✓），
 * 所以它把稳定器**关掉**跑 ✓（见那里 `chooseComicPaintStabilize(0f)` 那段说明 ✓）。
 * 稳定器**开着**时会**改变路径**（`brush-lab-simple.html:751-752` 就在 `walkTo()` 之前 ✓）——
 * 那是一个**独立的行为**，必须**独立钉** ✗，不能靠"关掉它"就当无事发生 ✓。
 *
 * ## 这里钉的三条（都是网页口径，不是"我觉得"✓）
 *
 * 1. **默认档就是网页那个 35%** ✓（`AppState.comicPaintStabilize` = `StrokeStabilizer.DEFAULT` ✓）；
 * 2. **开着它，笔画内部仍然不许有洞** ✓（逐列扫 ✓ —— 稳定器只改**喂进去的点**，
 *    不改"步距 vs 笔尖半径"那条护栏 ✓）；
 * 3. **尾端会短一截 —— 这是 EMA 的定义，网页也这样** ✓（网页 `endStroke` 不做"追上" ✗）。
 *    这一条**如实钉**（不是"忽略"✗）：滞后必须存在、且必须有界 ✗
 *    （理论稳态滞后 ≈ 采样步长 × (1−k)/k ✓，35% ⇒ 0.538 步 ✓）。
 */
class ComicPaintStabilizerTest {

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
        val page = requireNotNull(state.comicBoardPage) { "先决条件：这一页应该在" }
        val base = requireNotNull(page.layers.firstOrNull { it.kind == ComicLayer.Kind.BASE }) {
            "先决条件：底板层应该在"
        }
        assertTrue("底板层必须能开始编辑", state.beginComicLayerPaint(base.id))
        return requireNotNull(state.comicPaintSession) { "开始之后就该有一段会话" }
    }

    /** 一笔扫出来的三件事（✓）：内部最长透明列段 / 尾端透明列数 / 最后一列有色到哪 ✓。 */
    private class Scan(val interiorGap: Int, val tailGap: Int, val lastInkColumn: Int, val endColumn: Int) {
        fun line(): String =
            "interiorGap=$interiorGap tailGap=$tailGap lastInkColumn=$lastInkColumn endColumn=$endColumn"
    }

    /**
     * 逐列扫像素 ✓：`interiorGap` 只看**已经有过墨**的那些列之间（= 真的有洞吗 ✓），
     * `tailGap` 单独量尾端（= 稳定器滞后造成的那一截 ✓）。
     */
    private fun scan(pixels: IntArray, width: Int, height: Int, from: Int, to: Int): Scan {
        fun columnHasInk(x: Int): Boolean {
            if (x < 0 || x >= width) return false
            for (y in 0 until height) {
                if (((pixels[y * width + x] ushr 24) and 0xFF) != 0) return true
            }
            return false
        }
        var firstInk = -1
        var lastInk = -1
        for (x in from..to) {
            if (columnHasInk(x)) {
                if (firstInk < 0) firstInk = x
                lastInk = x
            }
        }
        var interior = 0
        var run = 0
        for (x in maxOf(from, firstInk)..maxOf(from, lastInk)) {
            if (columnHasInk(x)) run = 0 else { run++; if (run > interior) interior = run }
        }
        val tail = if (lastInk < 0) to - from + 1 else to - lastInk
        return Scan(interior, tail, lastInk, to)
    }

    /** 照界面那条路画一笔（**不动稳定器档位** —— 用 `AppState` 的默认 ✓）。 */
    private fun strokeThroughUi(
        stabilize: Float?,
        width: Int = 800,
        height: Int = 300,
        speedPx: Float = 20f,
        pressure: Float = 1f,
    ): Triple<ImageEditSession, AppState, Scan> {
        val state = freshState()
        val session = openSession(state, width, height)
        if (stabilize != null) state.chooseComicPaintStabilize(stabilize)
        val y = height / 2f
        val x0 = 30f
        val x1 = (width - 30).toFloat()
        state.beginComicPaintStroke(x = x0, y = y, pressure = pressure)
        var x = x0
        while (x < x1) {
            x += speedPx
            state.comicPaintStrokeTo(x.coerceAtMost(x1), y)
        }
        state.endComicPaintStroke()
        val endColumn = (x1 - 1f).toInt()
        return Triple(session, state, scan(session.pixels, width, height, x0.toInt(), endColumn))
    }

    // ------------------------------------------------------------------
    // ① 默认档 = 网页那个 35%
    // ------------------------------------------------------------------

    @Test
    fun the_ui_default_is_the_web_default_35_percent() {
        val state = freshState()
        assertEquals(
            "界面默认档必须是网页 `brush-lab-simple.html:156` 的 **35%** ✗",
            StrokeStabilizer.DEFAULT_STABILIZE_PERCENT,
            state.comicPaintStabilize,
            0f,
        )
        // 滑杆（网页 :670 min:0 max:95）—— 越界收敛 ✓
        state.chooseComicPaintStabilize(200f)
        assertEquals("稳定器上限必须收敛到网页滑杆的 95 ✗", 95f, state.comicPaintStabilize, 0f)
        state.chooseComicPaintStabilize(-1f)
        assertEquals("稳定器下限必须收敛到 0 ✗", 0f, state.comicPaintStabilize, 0f)
    }

    // ------------------------------------------------------------------
    // ② 开着它，笔画**内部**仍然不许有洞
    // ------------------------------------------------------------------

    @Test
    fun with_the_stabilizer_on_the_stroke_still_has_no_interior_hole() {
        val (_, _, off) = strokeThroughUi(stabilize = 0f)
        val (_, _, on) = strokeThroughUi(stabilize = 35f)
        println("[㉚-stab] 关：${off.line()}\n[㉚-stab] 开35%：${on.line()}")
        assertEquals(
            "稳定器**关**时内部当然不许有洞（先决条件）✗｜${off.line()}",
            0,
            off.interiorGap,
        )
        assertEquals(
            "稳定器**开着**时内部照样不许有洞 ✗（它改的是喂进去的点，不是 `step ≤ 0.9×r` 那条护栏 ✓）" +
                "｜${on.line()}",
            0,
            on.interiorGap,
        )
    }

    // ------------------------------------------------------------------
    // ③ **稳态滞后**：如实钉住 —— 钉在**路径长度**上，不钉在像素列上 ✗
    //
    // ⚠️ 为什么不在像素上钉（第一版就是这么写的，量出来 tailGap=0 ✗，**不是没滞后**）：
    //    实测「关 740.00px / 开 729.23px」⇒ 滞后**整整 10.8px** ✓，可它在像素上看不见 ——
    //    因为**笔尖半径（十几 px）把这一截盖住了** ✓。用像素列去量滞后，量到的是
    //    `lag − 半径`（≤0 就恒为 0 ✗），那是**在量笔尖大小**，不是量稳定器 ✗。
    //    ⇒ 钉 `distance` 的差值 ✓：它就是"喂进去的点被拉回来多少"的**直接**度量 ✓，
    //      而且它和网页那两行的稳态解 `v × (1−k)/k` 可以对上 ✓ —— 比数透明列强得多 ✓。
    // ------------------------------------------------------------------

    @Test
    fun the_stabilizer_steady_state_lag_matches_the_web_formula() {
        val speed = 20f
        val pressure = 1f
        val (offSession, _, off) = strokeThroughUi(stabilize = 0f, speedPx = speed, pressure = pressure)
        val (onSession, _, on) = strokeThroughUi(stabilize = 35f, speedPx = speed, pressure = pressure)
        val offDistance = offSession.strokeDabStats().distance
        val onDistance = onSession.strokeDabStats().distance
        val nibRadius = onSession.strokeDabStats().radiusMax
        val k = 1f - 35f / 100f
        // 一阶低通的稳态滞后：`L = v × (1 − k) / k` ✓（v = 每个采样点走多远 ✓）
        // 35% ⇒ k = 0.65 ⇒ L = 20 × 0.35/0.65 ≈ 10.77 px
        val expectedLag = speed * (1f - k) / k
        val measuredLag = offDistance - onDistance
        println(
            "[㉚-stab] 稳态滞后：关 dist=${"%.2f".format(offDistance)}px / 开 dist=${"%.2f".format(onDistance)}px " +
                "⇒ 实测滞后 ${"%.2f".format(measuredLag)}px（理论 v×(1−k)/k = ${"%.2f".format(expectedLag)}px，" +
                "笔尖 r=${"%.2f".format(nibRadius)}px 把它在像素上盖住了 ⇒ 这里量路径长度 ✓）",
        )
        assertTrue(
            "稳定器关着时这一笔必须真的走起来（实测 ${"%.2f".format(offDistance)}px）✗",
            offDistance > 500f,
        )
        assertTrue(
            "开着 35% 稳定器，路径长度必须**真的变短** ✗（关=${"%.2f".format(offDistance)} / " +
                "开=${"%.2f".format(onDistance)}）—— 一样长就说明 EMA 没接进链路 ✗",
            onDistance < offDistance,
        )
        assertTrue(
            "实测滞后 ${"%.2f".format(measuredLag)}px 必须对上网页公式的稳态解 " +
                "${"%.2f".format(expectedLag)}px ✗（容差 ±30%）—— 对不上说明接的不是那条公式 ✗",
            kotlin.math.abs(measuredLag - expectedLag) <= expectedLag * 0.3f,
        )
        // 像素侧如实记一笔：**内部**不许有洞 ✓（这一条是硬的 ✓）
        assertEquals("开着稳定器，笔画**内部**照样不许有洞 ✗｜${on.line()}", 0, on.interiorGap)
        assertEquals("关着时内部当然也不许有洞（先决条件）✗｜${off.line()}", 0, off.interiorGap)
    }
}
