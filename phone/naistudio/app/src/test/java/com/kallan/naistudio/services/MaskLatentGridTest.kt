package com.kallan.naistudio.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 遮罩吸附到 8px 潜空间网格（节点的 `prepare_official_inpaint_mask` 口径）。
 *
 * 这一步是"涂区外圈留一圈半重绘带"的对策：不吸附的话，服务端在潜空间里理解到的重绘边界
 * 会和用户画的线错开。
 */
class MaskLatentGridTest {

    private fun block(pixels: ByteArray, width: Int, left: Int, top: Int, size: Int = 8) {
        for (y in top until top + size) {
            for (x in left until left + size) {
                pixels[y * width + x] = 255.toByte()
            }
        }
    }

    @Test
    fun `a fully painted block stays painted`() {
        val width = 32
        val height = 32
        val pixels = ByteArray(width * height)
        block(pixels, width, left = 8, top = 8)

        val snapped = MaskCodec.snapToLatentGrid(pixels, width, height)
        assertEquals(8 * 8, snapped.count { it.toInt() != 0 })
        assertEquals(255, snapped[8 * width + 8].toInt() and 0xFF)
        // 格子外必须干净
        assertEquals(0, snapped[7 * width + 8].toInt() and 0xFF)
        assertEquals(0, snapped[8 * width + 16].toInt() and 0xFF)
    }

    @Test
    fun `a mostly painted block survives and a thin smear does not`() {
        val width = 16
        val height = 16

        // 一格涂满 60%（> 61% 阈值附近，用 80% 稳妥）→ 保留
        val mostly = ByteArray(width * height)
        block(mostly, width, left = 0, top = 0, size = 7)
        val snappedMostly = MaskCodec.snapToLatentGrid(mostly, width, height)
        assertEquals(255, snappedMostly[0].toInt() and 0xFF)

        // 一格只涂 1 像素 → 被吃掉（这就是插件里"请把区域画大一些"的原因）
        val thin = ByteArray(width * height)
        thin[0] = 255.toByte()
        val snappedThin = MaskCodec.snapToLatentGrid(thin, width, height)
        assertEquals(0, snappedThin.count { it.toInt() != 0 })
    }

    @Test
    fun `expansion compensates for the erosion`() {
        val width = 64
        val height = 64
        val thin = ByteArray(width * height)
        for (y in 28 until 36) thin[y * width + 32] = 255.toByte() // 一根 8px 长的细线

        // 直接吸附 → 大概率被吃掉
        val snapped = MaskCodec.snapToLatentGrid(thin, width, height)
        assertEquals(0, snapped.count { it.toInt() != 0 })

        // 先膨胀 7px（App 里 mask_expand 的默认值）再吸附 → 活下来
        val expanded = com.kallan.naistudio.models.MaskExpand.dilate(thin, width, height, 7)
        val recovered = MaskCodec.snapToLatentGrid(expanded, width, height)
        assertTrue("膨胀后的细线应该能在吸附后保留", recovered.count { it.toInt() != 0 } > 0)
    }

    @Test
    fun `nearest scaling keeps the mask aligned`() {
        val width = 4
        val height = 4
        val pixels = ByteArray(width * height)
        pixels[1 * width + 1] = 255.toByte()

        val scaled = MaskCodec.scaleNearest(pixels, width, height, 8, 8)
        assertEquals(64, scaled.size)
        // 原来那一格放大成 2×2
        assertEquals(255, scaled[2 * 8 + 2].toInt() and 0xFF)
        assertEquals(255, scaled[3 * 8 + 3].toInt() and 0xFF)
        assertEquals(0, scaled[0].toInt() and 0xFF)
    }

    @Test
    fun `inpaintMask returns null when everything is eroded away`() {
        val pixels = ByteArray(64 * 64)
        pixels[10 * 64 + 10] = 255.toByte() // 单像素，吸附后为空
        val mask = com.kallan.naistudio.models.PixelMask(64, 64, pixels)
        assertNull(MaskCodec.inpaintMask(mask, 64, 64))
    }

    @Test
    fun `inpaintMask snaps to the request size`() {
        val pixels = ByteArray(64 * 64)
        block(pixels, 64, left = 16, top = 16, size = 16)
        val mask = com.kallan.naistudio.models.PixelMask(64, 64, pixels)

        val prepared = MaskCodec.inpaintMask(mask, 128, 128)
        assertTrue(prepared != null)
        assertEquals(128, prepared!!.width)
        assertEquals(128, prepared.height)
        // 吸附后每一格是 8×8 的整块
        val first = prepared.pixels.indexOfFirst { it.toInt() != 0 }
        assertEquals(0, first % 8)
    }
}
