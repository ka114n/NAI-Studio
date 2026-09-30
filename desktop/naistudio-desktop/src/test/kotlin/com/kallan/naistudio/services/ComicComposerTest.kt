package com.kallan.naistudio.services

import com.kallan.naistudio.models.ComicLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 拼页**混色**的钉子（纯 ARGB 运算 ✓，不碰平台 API ✓）。
 * 用户 2026-09-26 方案第 ⑦ 项「拼页导出」：一张页 = 多图层从下往上叠 ✓。
 */
class ComicComposerTest {

    private val white = 0xFFFFFFFF.toInt()
    private val red = 0xFFFF0000.toInt()
    private val halfBlue = 0x800000FF.toInt()

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun opaque_source_replaces_the_destination() {
        val dst = IntArray(4) { white }
        // 1×1 的红点放在 (1,1) → 只该写右下那格 ✓（其余保持纸色 ✓）
        ComicComposer.blendOver(dst, 2, 2, intArrayOf(red), 1, 1, 1, 1)
        assertEquals("没覆盖到的保持纸色", white, dst[0])
        assertEquals(red, dst[3])
    }

    @Test
    fun opacity_zero_changes_nothing() {
        val dst = IntArray(1) { white }
        ComicComposer.blendOver(dst, 1, 1, intArrayOf(red), 1, 1, 0, 0, opacity = 0f)
        assertEquals(white, dst[0])
    }

    @Test
    fun half_transparent_blue_on_white_is_light_blue() {
        val dst = IntArray(1) { white }
        ComicComposer.blendOver(dst, 1, 1, intArrayOf(halfBlue), 1, 1, 0, 0)
        val out = dst[0]
        val r = (out ushr 16) and 0xFF
        val g = (out ushr 8) and 0xFF
        val b = out and 0xFF
        assertTrue("红绿应当被白底抬到 ~127（半透明 ✓）", r in 120..135 && g in 120..135)
        assertEquals("蓝应当接近满", 255, b)
        assertEquals("alpha 应当接近不透明", 255, (out ushr 24) and 0xFF)
    }

    @Test
    fun out_of_bounds_pixels_are_clipped_not_wrapped() {
        // 2×2 的红块放到 (-1,-1)：只有它的右下角那 1 像素落在画布 (0,0) ✓，其余裁掉 ✓
        //（若实现写错成"取模绕回"，dst[3] 就会被写上红 ✗ —— 这条就是防那个的 ✓）
        val src = intArrayOf(red, red, red, red)
        val dst = IntArray(4) { white }
        ComicComposer.blendOver(dst, 2, 2, src, 2, 2, -1, -1)
        assertEquals("左上角应当被那一像素写到", red, dst[0])
        assertEquals("其余三格不该被写（不能绕回来 ✗）", white, dst[3])
    }

    @Test
    fun layer_helpers_filter_to_bitmap_layers_only() {
        val layers = listOf(
            ComicLayer("g", "生成层", ComicLayer.Kind.GENERATED, imagePath = "g.png"),
            ComicLayer("t", "文本层", ComicLayer.Kind.TEXT, imagePath = null),
            ComicLayer("h", "隐藏层", ComicLayer.Kind.GENERATED, imagePath = "h.png").apply { visible = false },
            ComicLayer("o", "全透明", ComicLayer.Kind.GENERATED, imagePath = "o.png").apply { opacity = 0f },
        )
        assertEquals(listOf("g"), ComicComposer.bitmapLayers(layers).map { it.id })
    }
}
