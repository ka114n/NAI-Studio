package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 跑前花费估算的钉子（用户口径：逐格生成 = 多次付费调用 → **跑前报总消耗并确认** ✓）。
 * 价格函数是注入的 ✓ —— 这里塞假价格，只钉"清单/合计/边界"这几件事 ✓。
 */
class ComicCostTest {

    /** 假价格：每 100 万像素 2 Anlas（只为了能算，不代表真实价格 ✗）。 */
    private val fakePrice: (Int, Int) -> Int = { w, h -> (w.toLong() * h / 500_000L).toInt() }

    @Test
    fun sums_every_panel_and_keeps_a_per_panel_list() {
        val est = ComicCost.estimate(
            listOf(
                "p1" to (1024 to 1024),   // 1048576 px → 2
                "p2" to (576 to 1792),    // 1032192 px → 2
            ),
            fakePrice,
        )
        assertEquals(2, est.count)
        assertEquals(listOf("p1", "p2"), est.lines.map { it.panelId })
        assertEquals(4, est.total)
        assertTrue(est.summaryText().contains("2 格"))
        assertTrue(est.summaryText().contains("4 Anlas"))
    }

    @Test
    fun empty_page_costs_nothing() {
        val est = ComicCost.estimate(emptyList(), fakePrice)
        assertEquals(0, est.count)
        assertEquals(0, est.total)
    }

    @Test
    fun negative_prices_are_clamped_to_zero() {
        // 防御：价格函数万一返回负数（或上游算错）✗ —— 不能出现"负花费"这种东西 ✓
        val est = ComicCost.estimate(listOf("p1" to (64 to 64))) { _, _ -> -5 }
        assertEquals(0, est.total)
    }

    @Test
    fun sizes_are_passed_through_untouched() {
        // 估算**不改尺寸** ✓（尺寸由 InpaintSize 定，这里只报账 ✓）
        val est = ComicCost.estimate(listOf("p1" to (1792 to 576)), fakePrice)
        assertEquals(1792, est.lines.first().width)
        assertEquals(576, est.lines.first().height)
    }
}
