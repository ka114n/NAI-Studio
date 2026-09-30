package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.CanvasTool
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.state.AppState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **第 ㉟② 追加：用户实机报的「画笔点下去画时会在起点出现一个大点」** ✗ —— 复现 + 定位。
 *
 * ## 用户症状（原话 ✓）
 * 「**画笔点下去画时会在起点出现一个大点**」✗
 *
 * ## 怀疑的机制（本测试去**证实或证伪**它 ✓，不许照抄假设 ✗）
 *
 * 笔通道**刚刚才接通**（第 ㉟② 那一行 `.then(penModifier)` ✓）⇒ 现在
 * **笔的 Press 和 Windows 提升出来的鼠标 down 都会各起一笔** ✗。
 * 旧的"交接"逻辑是"**收掉 + 撤回 + 按真压感重开**"✓（`ComicModeScreen` `:1794-1803` ✓），
 * 而**攒点器**是 ㉝① 新加的 ✓ ⇒ 怀疑"起点那一颗被落了两遍"✗。
 *
 * ## 本测试量什么（**判据，有牙 ✓**）
 *
 *  ① **起点不许有双叠**：模拟"鼠标 down 先落笔 + 笔 Press 同一点交接"✓
 *     ⇒ 墨迹面积必须只等于"**一颗笔尖**"量级 ✓（不是两颗叠 ✗）；
 *  ② **第一颗的半径 = 第一个采样点的压力换算值** ✓（不是满压、不是基准半径 ✗）；
 *  ③ **交接走完之后画面上不许残留那颗**（用户看到的就是它 ✗）。
 */
class PenMouseHandoffDotProbeTest {

    private val platform: Platform = desktopPlatform()

    private fun freshState(): AppState {
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .apply()
        return AppState(platform).also { it.ensureComicBoardLoaded() }
    }

    /** 开一张空白的页 + 把底板层开成绘画会话 ✓（与 `ComicPaintStrokeContinuityTest` 同一套 ✓）。 */
    private fun openSession(state: AppState, width: Int, height: Int): ImageEditSession {
        state.setComicBoardBase(width = width, height = height)
        state.chooseComicPaintTool(CanvasTool.DRAW)
        val page = requireNotNull(state.comicBoardPage) { "先决条件：这一页应该在" }
        val base = requireNotNull(page.layers.firstOrNull { it.kind == ComicLayer.Kind.BASE }) {
            "先决条件：底板层应该在"
        }
        assertTrue("底板层必须能开始编辑", state.beginComicLayerPaint(base.id))
        val s = requireNotNull(state.comicPaintSession) { "开始之后就该有一段会话" }
        // 诊断：要拿引擎**自己算的几何半径** ✓（像素量法会系统性偏小 ✗，见判据② ✓）
        s.dabProbeEnabled = true
        return s
    }

    /** 画布上 alpha > 0 的像素数 ✓ + alpha 总和 ✓（"一个大点"就是这两个数偏大 ✗）。 */
    private fun inkStats(s: ImageEditSession): Pair<Int, Long> {
        var n = 0
        var sum = 0L
        for (p in s.pixels) {
            val a = (p ushr 24) and 0xFF
            if (a != 0) {
                n++
                sum += a
            }
        }
        return n to sum
    }

    /** 起点那颗笔尖的**最大半径**（相对落笔点 ✓）—— 用它量"第一颗的半径"✓。 */
    private fun maxRadius(s: ImageEditSession, cx: Int, cy: Int): Double {
        var maxD = 0.0
        for (y in 0 until s.height) {
            for (x in 0 until s.width) {
                val a = (s.pixels[y * s.width + x] ushr 24) and 0xFF
                if (a < 8) continue
                val d = kotlin.math.hypot((x - cx).toDouble(), (y - cy).toDouble())
                if (d > maxD) maxD = d
            }
        }
        return maxD
    }

    /**
     * **判据 ①②③：鼠标先落笔 → 笔 Press 同一点交接** ✓。
     *
     * 顺序照 Windows 实际的来（`ComicModeScreen` 的注释写着"两条通道先后顺序不保证"✓）：
     *  1. 鼠标那条先 `beginComicPaintStroke(pressure = 1f)` ✓（**满压 ⇒ 大点** ✗）
     *     + 一个 motion 点 ⇒ 它已经**落在层上了** ✓；
     *  2. 笔那条 `Press` 到同一个位置 ✓ ⇒ 走进"收掉 + 撤回 + 按真压感重开"那一支 ✓；
     *  3. 收笔 ⇒ 看画面。
     */
    @Test
    fun handoff_from_mouse_to_pen_must_not_leave_the_mouse_dot() {
        val state = freshState()
        val session = openSession(state, width = 1200, height = 1600)
        val w = session.width
        val cx = w / 2
        val cy = session.height / 2
        val penPressure = 0.42f

        // ---- ① 鼠标那条先落笔（满压 1.0 ⇒ 基准半径 ⇒ 如果留着就是"一个大点"✗）----
        state.beginComicPaintStroke(
            x = cx.toFloat(), y = cy.toFloat(), pressure = 1f,
            screenX = 100f, screenY = 100f, zoom = 1f, dpr = 1f,
            fromPen = false,
        )
        state.offerComicPaintStrokePoint(cx + 24f, cy.toFloat(), 1f, fromPen = false)
        state.flushComicPaintStrokePoints()
        val afterMouse = inkStats(session)
        val mouseR = maxRadius(session, cx, cy)
        val mouseGeomR = session.dabProbeLog.maxOfOrNull { it.radius } ?: 0f
        println(
            "[㉟②-起点] 鼠标那条落笔后：墨 ${afterMouse.first} 像素（alpha 和 ${afterMouse.second}）、" +
                "像素量到 ${"%.1f".format(mouseR)}px、**几何半径 ${"%.2f".format(mouseGeomR)}px**",
        )
        assertTrue("鼠标那条应当真的落了笔 ✗", afterMouse.first > 0)

        // ---- ② 交接：收掉 + 撤回（这就是 `ComicModeScreen` 那三行 ✓）----
        state.endComicPaintStroke()
        state.undoComicPaint()
        val afterUndo = inkStats(session)
        println("[㉟②-起点] 交接撤回之后：墨 ${afterUndo.first} 像素（应当回到 0 ✓）")

        // ⚠️ 把引擎的诊断日志**清干净**再开笔那条 ✓ —— 不然读到的是鼠标那笔留下的 ✓
        //    （第一版就是没清 ⇒ 量到的 `radius` 其实混着鼠标那笔的 ✓，好在那批也是 22.50 ✓，
        //     所以那个数**不是**混淆造成的 ✓；但判据必须只认"笔这一笔"✓）
        session.dabProbeLog.clear()
        state.beginComicPaintStroke(
            x = cx.toFloat(), y = cy.toFloat(), pressure = penPressure,
            screenX = 100f, screenY = 100f, zoom = 1f, dpr = 1f,
            fromPen = true,
        )
        val afterBegin = session.dabProbeLog.map { it.pressure }
        println(
            "[㉟②-起点] **begin 这一下**落了几颗：${afterBegin.size} 颗，压力 = " +
                afterBegin.joinToString(" / ") { "%.2f".format(it) } + "（喂进去的是 $penPressure ✓）",
        )
        state.endComicPaintStroke()
        val afterPen = inkStats(session)
        val penR = maxRadius(session, cx, cy)
        // ⚠️ **量出来的半径和引擎自己算的半径不是一个东西** —— 两条都要打 ✓：
        //  `maxRadius` 是"从落笔点向外量 alpha ≥ 8 的最远像素"✓ ⇒ 它会**系统性偏小** ✓
        //  （抗锯齿边那一圈 alpha < 8 就被截掉了 ✓，而 `dabProbeLog` 里的 `radius` 是**几何半径** ✓）。
        //  所以判据②**必须用引擎报的几何半径** ✓，不能用像素量出来的那个 ✗。
        val log = session.dabProbeLog
        println("[㉟②-起点] 笔那条这一笔的笔尖 ${log.size} 颗；前 3 颗的几何半径 = " +
            log.take(3).joinToString(" / ") { "${"%.2f".format(it.radius)}px(p=${"%.2f".format(it.pressure)})" })
        val geometricR = log.firstOrNull()?.radius ?: 0f
        println(
            "[㉟②-起点] 笔那条重开并收笔后：墨 ${afterPen.first} 像素、" +
                "**几何半径 ${"%.2f".format(geometricR)}px**（像素量出来 ${"%.1f".format(penR)}px ✓）、" +
                "鼠标那颗几何半径 ${"%.2f".format(mouseGeomR)}px",
        )

        // ---- 判据③：撤回必须真的把鼠标那颗弄掉 ✓ ----
        val leftover = afterUndo.first
        println("[㉟②-起点] 判据③（残留）：$leftover 像素（上限 ${afterMouse.first / 20} ✓）")
        assertTrue(
            "撤回之后还残留 $leftover 个墨迹像素 ✗（用户看到的「大点」就是它 ✗）",
            leftover * 20 <= afterMouse.first,
        )
        // ---- 判据①：起点不许双叠（笔重开后不许 ≈ 两颗叠 ✗）----
        println(
            "[㉟②-起点] 判据①（双叠）：${afterPen.first} vs 单颗 ${afterMouse.first}" +
                "（上限 ${(afterMouse.first * 1.6).toInt() + 40} ✓）",
        )
        assertTrue(
            "起点疑似双叠：鼠标那颗 ${afterMouse.first} 像素 ⇒ 笔重开后 ${afterPen.first} 像素 ✗",
            afterPen.first <= (afterMouse.first * 1.6).toInt() + 40,
        )
        // ---- 判据②：第一颗的半径 = 压力换算值（不是满压 ✗）----
        // ⚠️ **用几何半径判** ✓（`dabProbeLog[i].radius` ✓）——
        //   第一版我拿"像素量出来的半径"判 ✗，量到 14.2px 而期望 20.1px ✓，
        //   看着像"用了小半径"✗ —— 其实是**量法本身偏小** ✓：
        //   抗锯齿边那一圈 alpha < 8 被 `maxRadius` 截掉 ✓，而且这一笔只有一颗笔尖、
        //   它的外圈天生淡 ✓ ⇒ 像素半径 ≈ 0.71 × 几何半径 ✓（= 14.2 / 20.06 ✓）。
        //   **判据该量引擎自己算的那个数** ✓ —— 那才是"第一颗用了多少压力"的直接证据 ✓。
        val minRatio = state.comicPaintMinRadiusRatio
        val expected = mouseGeomR * (minRatio + (1f - minRatio) * penPressure)
        println(
            "[㉟②-起点] 判据②（半径）：鼠标满压几何半径 ${"%.2f".format(mouseGeomR)}px ⇒ " +
                "p=$penPressure 时应当 ≈ ${"%.2f".format(expected)}px（笔那条实测 " +
                "${"%.2f".format(geometricR)}px ✓，minRatio=$minRatio ✓；" +
                "像素量法 ${"%.1f".format(penR)}px 会系统性偏小 ✗）",
        )
        assertTrue(
            "第一颗几何半径 ${"%.2f".format(geometricR)}px 不是压力 $penPressure 的换算值 " +
                "${"%.2f".format(expected)}px ✗（±10% 之外 = 起点用了满压 / 基准半径 ✗）",
            kotlin.math.abs(geometricR - expected) <= expected * 0.10,
        )
    }
}
