package com.kallan.naistudio.models

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 遮罩膨胀（`mask_expand`）：方形（切比雪夫距离）膨胀，可分离两趟实现。
 */
class MaskExpandTest {

    @Test
    fun `radius zero returns an unchanged copy`() {
        val pixels = ByteArray(16 * 16)
        pixels[5 * 16 + 5] = 255.toByte()
        val out = MaskExpand.dilate(pixels, 16, 16, 0)
        assertArrayEquals(pixels, out)
        assertTrue(pixels !== out)
    }

    @Test
    fun `a single pixel grows into a square block`() {
        val width = 32
        val height = 32
        val pixels = ByteArray(width * height)
        pixels[16 * width + 16] = 255.toByte()

        val one = MaskExpand.dilate(pixels, width, height, 1)
        assertEquals(9, one.count { it.toInt() != 0 })

        val two = MaskExpand.dilate(pixels, width, height, 2)
        assertEquals(25, two.count { it.toInt() != 0 })

        // 方形：对角也在里面
        assertEquals(255, two[14 * width + 14].toInt() and 0xFF)
        assertEquals(0, two[13 * width + 13].toInt() and 0xFF)
    }

    @Test
    fun `expansion is clipped at the borders`() {
        val width = 8
        val height = 8
        val pixels = ByteArray(width * height)
        pixels[0] = 255.toByte() // 左上角
        val out = MaskExpand.dilate(pixels, width, height, 3)
        // 只剩下右下 4×4 能扩（0..3 行 / 0..3 列）
        assertEquals(16, out.count { it.toInt() != 0 })
    }

    @Test
    fun `an all zero mask stays empty`() {
        val pixels = ByteArray(24 * 24)
        val out = MaskExpand.dilate(pixels, 24, 24, 7)
        assertEquals(0, out.count { it.toInt() != 0 })
    }

    @Test
    fun `an all painted mask stays fully painted`() {
        val width = 24
        val height = 24
        val pixels = ByteArray(width * height) { 255.toByte() }
        val out = MaskExpand.dilate(pixels, width, height, 7)
        assertEquals(width * height, out.count { it.toInt() != 0 })
    }

    @Test
    fun `radius is clamped and huge radius does not crash`() {
        val width = 40
        val height = 40
        val pixels = ByteArray(width * height)
        pixels[20 * width + 20] = 255.toByte()
        // 999 会被夹到 128：整张图都会被点亮，但绝不越界
        val out = MaskExpand.dilate(pixels, width, height, 999)
        assertEquals(width * height, out.count { it.toInt() != 0 })
    }

    @Test
    fun `PixelMask overload keeps the size`() {
        val mask = PixelMask(20, 10, ByteArray(20 * 10).also { it[5 * 20 + 5] = 255.toByte() })
        val expanded = MaskExpand.dilate(mask, 2)
        assertEquals(20, expanded.width)
        assertEquals(10, expanded.height)
        assertTrue(expanded.selectedCount > 1)
    }
}
