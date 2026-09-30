package com.kallan.naistudio.services

import com.kallan.naistudio.models.PixelMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地回贴（`add_original_image`）与软边 alpha（`mask_feather` / `edge_protection`）。
 *
 * 这里只测纯数组计算（不碰 android.graphics），口径对照插件
 * `modular_nodes.py:203-230` 与参考实现 `compositeInpaintResult`。
 */
class InpaintCompositeTest {

    private fun maskWithPaintedRect(
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ): PixelMask {
        val pixels = ByteArray(width * height)
        for (y in top until bottom) {
            for (x in left until right) {
                pixels[y * width + x] = 255.toByte()
            }
        }
        return PixelMask(width, height, pixels)
    }

    @Test
    fun `hard edge skips the softening and reuses the mask`() {
        val mask = maskWithPaintedRect(64, 64, 20, 20, 40, 40)
        val alpha = InpaintComposite.blendAlpha(mask, feather = 20, edgeProtection = false)
        assertEquals(mask.pixels.size, alpha.size)
        // 硬边 = 直接用（已经膨胀过的）遮罩比特当 alpha
        for (index in mask.pixels.indices) {
            assertEquals(mask.pixels[index], alpha[index])
        }
    }

    @Test
    fun `soft edge keeps the core opaque and fades outside`() {
        val width = 256
        val height = 256
        // 中心一块 16×16 的涂区；潜空间膨胀 4 格（=32px）+ 模糊 20px 之后，
        // 有效的软边带落在涂区外 ~32..52px 之间，四角则完全不受影响
        val mask = maskWithPaintedRect(width, height, 120, 120, 136, 136)
        val alpha = InpaintComposite.blendAlpha(mask, feather = 20, edgeProtection = true)

        val center = alpha[128 * width + 128].toInt() and 0xFF
        val insideDilated = alpha[128 * width + 100].toInt() and 0xFF
        val ramp = alpha[128 * width + 78].toInt() and 0xFF
        val farAway = alpha[5 * width + 5].toInt() and 0xFF

        assertEquals("中心必须是完全重绘", 255, center)
        assertTrue("膨胀带内应该接近全量，实际 $insideDilated", insideDilated > 100)
        assertTrue("涂区外要有渐变带（不是硬边），实际 $ramp", ramp in 1..254)
        assertEquals("离得远的地方必须完全保留原图", 0, farAway)
    }

    @Test
    fun `latent dilation grows the mask by four cells`() {
        val width = 64
        val height = 64
        val cells = ByteArray(8 * 8)
        cells[4 * 8 + 4] = 255.toByte()
        val dilated = InpaintComposite.dilateCells(cells, 8, 8, 4)
        // 半径 4 的方形膨胀：从 1 格变成 9×9（被 8×8 边界裁掉一部分）
        assertEquals(8 * 8, dilated.count { it.toInt() != 0 })
        assertEquals(255, dilated[0].toInt() and 0xFF)
        assertTrue(width > 0 && height > 0)
    }

    @Test
    fun `box blur keeps a flat mask flat and spreads a spike`() {
        val flat = ByteArray(16 * 16) { 255.toByte() }
        val blurredFlat = InpaintComposite.boxBlur(flat, 16, 16, 2)
        assertTrue(blurredFlat.all { (it.toInt() and 0xFF) == 255 })

        val spike = ByteArray(16 * 16)
        spike[8 * 16 + 8] = 255.toByte()
        val blurredSpike = InpaintComposite.boxBlur(spike, 16, 16, 2)
        assertTrue("尖峰必须被摊开", blurredSpike.count { it.toInt() != 0 } > 1)
        assertTrue("摊开后中心不再是满值", (blurredSpike[8 * 16 + 8].toInt() and 0xFF) < 255)
    }

    @Test
    fun `composite respects the alpha channel`() {
        // ARGB：0xFFFF0000 = 不透明红，0xFF00FF00 = 不透明绿
        val generated = intArrayOf(0xFFFF0000.toInt())
        val original = intArrayOf(0xFF00FF00.toInt())

        val allGenerated = InpaintComposite.composite(generated, original, byteArrayOf(255.toByte()))
        assertEquals(0xFFFF0000.toInt(), allGenerated[0])

        val allOriginal = InpaintComposite.composite(generated, original, byteArrayOf(0))
        assertEquals(0xFF00FF00.toInt(), allOriginal[0])

        val half = InpaintComposite.composite(generated, original, byteArrayOf(128.toByte()))[0]
        val red = (half ushr 16) and 0xFF
        val green = (half ushr 8) and 0xFF
        assertTrue("半透明时红应该在 ~128 附近，实际 $red", red in 120..136)
        assertTrue("半透明时绿应该在 ~127 附近，实际 $green", green in 120..136)
    }

    @Test
    fun `composite returns the generated image when sizes disagree`() {
        val generated = IntArray(4)
        val original = IntArray(9)
        val out = InpaintComposite.composite(generated, original, ByteArray(4))
        assertTrue(generated === out)
    }
}
