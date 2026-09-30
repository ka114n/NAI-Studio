package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 逐像素遮罩的笔刷几何。
 *
 * 口径：笔刷大小是**原图像素**（1..350），圆形按直径、方形按边长，
 * 拖动时用"点到线段距离"判断，所以得到的是连续的一笔。
 */
class PixelMaskTest {

    // 用 2 的幂尺寸：归一化坐标 ↔ 像素坐标来回换算是精确的，边界判定才可复现
    private val width = 1024
    private val height = 1024

    private fun pixel(x: Int, y: Int) = MaskPoint((x + 0.5f) / width, (y + 0.5f) / height)

    @Test
    fun `square brush of one pixel paints exactly one pixel`() {
        val mask = ByteArray(width * height)
        val stroke = MaskStroke(square = true, brushPixels = 1)
        MaskBrush.stampInto(mask, width, height, null, pixel(10, 20), stroke)

        val painted = mask.count { it.toInt() != 0 }
        assertEquals(1, painted)
        assertEquals(255, mask[20 * width + 10].toInt() and 0xFF)
    }

    @Test
    fun `round brush of two pixels paints a plus shape`() {
        val mask = ByteArray(width * height)
        MaskBrush.stampInto(
            mask, width, height, null, pixel(10, 20),
            MaskStroke(square = false, brushPixels = 2),
        )
        // 直径 2 的圆：中心 + 上下左右 = 5 个像素（对角线距离 √2 > 1，不算）
        assertEquals(5, mask.count { it.toInt() != 0 })
    }

    @Test
    fun `dragging paints a continuous line`() {
        val mask = ByteArray(width * height)
        val stroke = MaskStroke(square = true, brushPixels = 3)
        val from = MaskPoint(0.10f, 0.50f)
        val to = MaskPoint(0.50f, 0.50f)
        MaskBrush.stampInto(mask, width, height, from, to, stroke)

        val maskObj = PixelMask(width, height, mask)
        val row = (0.50f * height).toInt()
        assertTrue("起笔附近要有", maskObj.valueAt((0.10f * width).toInt(), row) != 0)
        assertTrue("中间不能断", maskObj.valueAt((0.30f * width).toInt(), row) != 0)
        assertTrue("收笔附近要有", maskObj.valueAt((0.50f * width).toInt(), row) != 0)
    }

    @Test
    fun `eraser clears painted pixels`() {
        val mask = ByteArray(width * height)
        MaskBrush.stampInto(
            mask, width, height, null, pixel(100, 100),
            MaskStroke(square = false, brushPixels = 40),
        )
        assertTrue(mask.any { it.toInt() != 0 })

        MaskBrush.stampInto(
            mask, width, height, null, pixel(100, 100),
            MaskStroke(erasing = true, square = false, brushPixels = 40),
        )
        assertEquals(0, mask.count { it.toInt() != 0 })
    }

    @Test
    fun `brush size is clamped and round brushes keep at least two pixels`() {
        assertEquals(350, MaskBrush.normalizedBrushPixels(MaskStroke(brushPixels = 999, square = true)))
        assertEquals(1, MaskBrush.normalizedBrushPixels(MaskStroke(brushPixels = 0, square = true)))
        // 圆形最小 2 px：1 px 的圆在像素网格上会时有时无
        assertEquals(2, MaskBrush.normalizedBrushPixels(MaskStroke(brushPixels = 1, square = false)))
        assertEquals(350, MaskBrush.normalizedBrushPixels(MaskStroke(brushPixels = 999, square = false)))
    }

    @Test
    fun `a huge brush near the corner stays inside the buffer`() {
        val mask = ByteArray(width * height)
        MaskBrush.stampInto(
            mask, width, height, null, MaskPoint(0.01f, 0.01f),
            MaskStroke(square = false, brushPixels = MASK_BRUSH_MAX_PIXELS),
        )
        val maskObj = PixelMask(width, height, mask)
        assertTrue(maskObj.valueAt(0, 0) != 0)
        assertTrue(maskObj.selectedCount > 0)
    }

    @Test
    fun `stamping completely outside does nothing`() {
        val mask = ByteArray(width * height)
        MaskBrush.stampInto(
            mask, width, height, null, MaskPoint(-1f, 5f),
            MaskStroke(square = false, brushPixels = 20),
        )
        assertEquals(0, mask.count { it.toInt() != 0 })
    }

    @Test
    fun `coverage is the painted fraction`() {
        val pixels = ByteArray(100 * 100)
        for (index in 0 until 2500) pixels[index] = 255.toByte()
        val mask = PixelMask(100, 100, pixels)
        assertEquals(2500, mask.selectedCount)
        assertEquals(0.25f, mask.coverage, 1e-6f)
        assertFalse(mask.isEmpty())
    }
}
