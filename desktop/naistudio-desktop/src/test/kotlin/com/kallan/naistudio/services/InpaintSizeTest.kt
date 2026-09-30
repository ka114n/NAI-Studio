package com.kallan.naistudio.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **聚焦像素自动计算**的钉子（用户 2026-09-26 口径：等比 → 面积顶到 1024×1024 → **保 64**）。
 *
 * 这些断言就是"用户口径的可执行版本" ✓ —— 以后谁要改 [InpaintSize]，先看这里为什么这么定。
 */
class InpaintSizeTest {

    private fun assertAligned(size: Pair<Int, Int>) {
        assertEquals("宽必须是 64 的倍数", 0, size.first % InpaintSize.STEP)
        assertEquals("高必须是 64 的倍数", 0, size.second % InpaintSize.STEP)
        assertTrue("面积不能超过 1024×1024", size.first.toLong() * size.second <= InpaintSize.AREA_LIMIT)
        assertTrue("两边都不能小于 64", size.first >= InpaintSize.MIN_SIDE && size.second >= InpaintSize.MIN_SIDE)
    }

    @Test
    fun square_goes_to_full_cap() {
        val (w, h) = InpaintSize.forRect(200f, 200f)
        assertEquals(1024 to 1024, w to h)
        assertAligned(w to h)
    }

    @Test
    fun wide_strip_keeps_ratio_and_keeps_64() {
        // 3:1 —— 顶到上限本来是 1792×597，但 597 不是 64 的倍数 → **保 64** 退到 576 ✓
        val (w, h) = InpaintSize.forRect(300f, 100f)
        assertEquals(1792 to 576, w to h)
        assertAligned(w to h)
        assertTrue("宽可以超过 1024（用户口径 ✓）", w > 1024)
    }

    @Test
    fun tall_strip_mirrors_the_wide_one() {
        val (w, h) = InpaintSize.forRect(100f, 300f)
        assertEquals(576 to 1792, w to h)
        assertAligned(w to h)
        assertTrue(h > 1024)
    }

    @Test
    fun portrait_two_thirds_matches_the_usual_novelai_size() {
        // 2:3 —— 算法应当自然落到 NovelAI 那档常规竖图 832×1216 ✓
        val (w, h) = InpaintSize.forRect(832f, 1216f)
        assertEquals(832 to 1216, w to h)
        assertAligned(w to h)
    }

    @Test
    fun tiny_box_is_scaled_up_not_left_tiny() {
        // 很小的框也要放大到"同比例的最高像素"✓（不是原样返回小尺寸 ✗）
        val (w, h) = InpaintSize.forRect(64f, 64f)
        assertEquals(1024 to 1024, w to h)
    }

    @Test
    fun extreme_strip_still_obeys_the_area_cap() {
        // 32:1 这种极端比例：两边仍要 ≥64、面积仍不能超上限 ✓
        val (w, h) = InpaintSize.forRect(3200f, 100f)
        assertAligned(w to h)
        assertTrue(w > 1024)
    }

    @Test
    fun degenerate_input_does_not_crash() {
        val (w, h) = InpaintSize.forRect(0f, 0f)
        assertEquals(InpaintSize.MIN_SIDE to InpaintSize.MIN_SIDE, w to h)
    }
}
