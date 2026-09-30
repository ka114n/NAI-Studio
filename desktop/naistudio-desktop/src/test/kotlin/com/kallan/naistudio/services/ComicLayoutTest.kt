package com.kallan.naistudio.services

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拼页几何的钉子（高级漫画第 ⑦ 项「拼页导出」，也同时给屏幕预览用 ✓ —— 两处必须同一套公式 ✓）。
 */
class ComicLayoutTest {

    private val panel = Rect(Offset(100f, 50f), Size(200f, 200f))

    @Test
    fun fit_centres_and_keeps_aspect() {
        // 2:1 的图放进 200×200 的格子 → 变成 200×100，上下各留 50 ✓
        val r = ComicLayout.fit(panel, 400, 200)
        assertEquals(200f, r.width, 0.01f)
        assertEquals(100f, r.height, 0.01f)
        assertEquals(100f, r.left, 0.01f)      // 横向刚好铺满 ✓
        assertEquals(100f, r.top, 0.01f)       // 纵向居中：50 + (200-100)/2 ✓
        assertTrue("Fit 必须完全落在格子里", r.left >= panel.left - 0.01f && r.right <= panel.right + 0.01f)
    }

    @Test
    fun cover_fills_the_panel_and_overflows_equally() {
        // 同一张 2:1 的图用 cover → 200×200，横向溢出 100 由调用方裁 ✓
        val r = ComicLayout.cover(panel, 400, 200)
        assertEquals(400f, r.width, 0.01f)
        assertEquals(200f, r.height, 0.01f)
        assertEquals("横向居中（两边各溢出一半 ✓）", 0f, r.left, 0.01f)
        assertEquals(50f, r.top, 0.01f)
    }

    @Test
    fun degenerate_inputs_give_an_empty_rect_instead_of_crashing() {
        assertEquals(Size.Zero, ComicLayout.fit(panel, 0, 100).size)
        assertEquals(Size.Zero, ComicLayout.fit(Rect(Offset.Zero, Size.Zero), 100, 100).size)
    }

    @Test
    fun page_bounds_take_the_union_of_all_panels() {
        val (w, h) = ComicLayout.pageBounds(listOf(
            Rect(0f, 0f, 800f, 600f),
            Rect(800f, 600f, 1200f, 1000f),
        ))
        assertEquals(1200, w)
        assertEquals(1000, h)
    }

    @Test
    fun negative_panels_are_pulled_back_into_the_page() {
        // 用户把格子拖到左上角外面（负坐标 ✓）→ 整页要把它们拉回来，不能导出时被裁掉 ✗
        val (w, h) = ComicLayout.pageBounds(listOf(Rect(-100f, -50f, 300f, 250f)))
        assertEquals(400, w)
        assertEquals(300, h)
    }

    @Test
    fun empty_page_is_one_pixel_not_zero() {
        // 位图开不出来 0×0 ✗ —— 空页给 1×1，调用方据此跳过导出 ✓
        assertEquals(1 to 1, ComicLayout.pageBounds(emptyList()))
    }
}
