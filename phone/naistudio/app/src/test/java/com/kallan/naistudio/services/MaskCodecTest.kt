package com.kallan.naistudio.services

import com.kallan.naistudio.models.NAI_MAX_PIXEL_AREA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 局部重绘的尺寸规则。
 *
 * 关键前提：**渲染底图/遮罩用的尺寸**和**payload 里的 width/height**必须是同一个值，
 * 否则遮罩会错位。两边都走 `fitNaiImageSize`，这里把这个约定钉住。
 */
class MaskCodecTest {

    @Test
    fun `request size keeps already aligned dimensions`() {
        assertEquals(832 to 1216, MaskCodec.requestSize(832, 1216))
        assertEquals(1024 to 1024, MaskCodec.requestSize(1024, 1024))
    }

    @Test
    fun `request size aligns to multiples of sixty four`() {
        val (width, height) = MaskCodec.requestSize(850, 1200)
        assertEquals(0, width % 64)
        assertEquals(0, height % 64)
        // 取最接近的 64 倍数：850 → 832，1200 → 1216
        assertEquals(832, width)
        assertEquals(1216, height)
    }

    @Test
    fun `tiny images are lifted to the minimum dimension`() {
        assertEquals(64 to 64, MaskCodec.requestSize(10, 10))
        assertEquals(64 to 64, MaskCodec.requestSize(1, 63))
    }

    @Test
    fun `invalid dimensions fall back to the default preset`() {
        // 与 fitNaiImageSize 的既有约定一致：非正数用默认 832×1216 兜底，而不是 0×0
        assertEquals(832 to 1216, MaskCodec.requestSize(0, -5))
    }

    @Test
    fun `request size respects the pixel area cap`() {
        val (width, height) = MaskCodec.requestSize(4096, 4096)
        assertTrue(width.toLong() * height <= NAI_MAX_PIXEL_AREA)
        assertEquals(0, width % 64)
        assertEquals(0, height % 64)
    }

    @Test
    fun `request size is always a multiple of sixty four`() {
        for ((width, height) in listOf(832 to 1216, 850 to 1200, 1024 to 1024, 4096 to 4096)) {
            val (w, h) = MaskCodec.requestSize(width, height)
            assertEquals(0, w % 64)
            assertEquals(0, h % 64)
            assertTrue(w.toLong() * h <= NAI_MAX_PIXEL_AREA)
        }
    }
}
