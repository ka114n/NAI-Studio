package com.kallan.naistudio.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方 2× 放大的**规格**：费用分档、尺寸上限、倍数、请求体字段。
 *
 * 口径来自 ComfyUI 插件 `novelai-genytools` 的 `nai_client.py`
 * （`UPSCALE_PRICE_TIERS` / `UPSCALE_MAX_PIXELS` / `UPSCALE_FACTOR` / `upscale()` 的 payload）。
 */
class OfficialUpscaleTest {

    @Test
    fun `price tiers follow input pixels`() {
        // 每档的上界正好落在档位里（1 / 2 / 3 / 4 Anlas）
        assertEquals(1, OfficialUpscale.price(1024, 1024))          // 1,048,576 → 1
        assertEquals(1, OfficialUpscale.price(832, 1216))           // 1,011,712 → 1
        assertEquals(2, OfficialUpscale.price(1024, 1024 + 1))
        assertEquals(2, OfficialUpscale.price(1321, 1320))          // 1,743,720 → 2
        assertEquals(3, OfficialUpscale.price(1321, 1323))          // 1,747,683 → 3
        assertEquals(3, OfficialUpscale.price(1600, 1529))          // 2,446,400 → 3
        assertEquals(4, OfficialUpscale.price(1536, 2048))          // 3,145,728 → 4（正好上限）
    }

    @Test
    fun `exact tier boundaries`() {
        assertEquals(1, OfficialUpscale.price(1_048_576, 1))
        assertEquals(2, OfficialUpscale.price(1_048_577, 1))
        assertEquals(2, OfficialUpscale.price(1_747_627, 1))
        assertEquals(3, OfficialUpscale.price(1_747_628, 1))
        assertEquals(3, OfficialUpscale.price(2_446_678, 1))
        assertEquals(4, OfficialUpscale.price(2_446_679, 1))
        assertEquals(4, OfficialUpscale.price(OfficialUpscale.MAX_INPUT_PIXELS, 1))
    }

    @Test
    fun `over the official limit is rejected`() {
        assertNull(OfficialUpscale.price(OfficialUpscale.MAX_INPUT_PIXELS + 1, 1))
        assertNull(OfficialUpscale.price(4096, 4096)) // 16.7M 像素，远超上限
        assertFalse(OfficialUpscale.supported(4096, 4096))
    }

    @Test
    fun `invalid sizes are rejected`() {
        assertNull(OfficialUpscale.price(0, 1024))
        assertNull(OfficialUpscale.price(1024, 0))
        assertNull(OfficialUpscale.price(-64, 1024))
        assertFalse(OfficialUpscale.supported(0, 0))
    }

    @Test
    fun `output is always 2x`() {
        assertEquals(2, OfficialUpscale.FACTOR)
        assertEquals(1664 to 2432, OfficialUpscale.outputSize(832, 1216))
        assertEquals(2048 to 2048, OfficialUpscale.outputSize(1024, 1024))
    }

    @Test
    fun `request points at the image attachment and pins the model`() {
        val json = OfficialUpscale.requestJson()
        // "image" 的值是 multipart 里的附件键名（NovelAI 的老约定）
        assertEquals("image", json.getString("image"))
        assertEquals("nai-diffusion-5-curated", json.getString("model"))
        assertEquals(0, json.getInt("declared_blur_sigma"))
        assertEquals(3, json.length())
    }

    @Test
    fun `endpoint path matches the official api`() {
        assertEquals("/ai/upscale", OfficialUpscale.PATH)
        assertTrue(OfficialUpscale.MAX_INPUT_PIXELS == 3_145_728)
    }
}
