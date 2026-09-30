package com.kallan.naistudio.services

import com.kallan.naistudio.models.GenerateParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 局部重绘请求体的字段契约。
 *
 * 这些字段是照参考实现 `mobile/lib/services/nai_api.dart` 的 `inpaint()` 抄的，
 * 抄错了就是**一次白花钱的请求**（甚至被服务端当成普通文生图），所以在这里钉死：
 * 改 `buildPayload` 的 infill 分支必须先让这些断言继续绿。
 *
 * 纯 JVM 可跑（`org.json` 有单测依赖），不需要联网、不花 Anlas。
 */
class NaiApiInpaintPayloadTest {

    private val api = NaiApi()

    private val inpaintParams = GenerateParams(
        model = "nai-diffusion-5-full-inpainting",
        positivePrompt = "1girl, solo",
        width = 832,
        height = 1216,
    )

    @Test
    fun `infill payload matches the reference app field by field`() {
        val payload = api.buildPayload(
            params = inpaintParams,
            seed = 12_345L,
            action = "infill",
            inpaintStrength = 1.0,
        )

        assertEquals("infill", payload.getString("action"))
        // 模型必须活下来：normalized() 默认会把 -inpainting 模型打回文生图模型
        assertEquals("nai-diffusion-5-full-inpainting", payload.getString("model"))

        val parameters = payload.getJSONObject("parameters")
        // strength 固定 0.7：官方的 infill 报文保留的是通用 img2img 默认值
        assertEquals(0.7, parameters.getDouble("strength"), 1e-9)
        // noise 固定 0（参考实现里这个形参已经被丢弃）
        assertEquals(0.0, parameters.getDouble("noise"), 1e-9)
        assertEquals(12_344, parameters.getInt("extra_noise_seed"))
        assertEquals("NativeInfillingRequest", parameters.getString("request_type"))
        assertFalse(parameters.getBoolean("add_original_image"))
        // 用户调的那个强度只进 inpaintImg2ImgStrength
        assertEquals(1.0, parameters.getDouble("inpaintImg2ImgStrength"), 1e-9)
        // 强度 = 1 时不带 img2img 子对象
        assertFalse(parameters.has("img2img"))
        // 尺寸用源图对齐后的值，不套用别的东西
        assertEquals(832, parameters.getInt("width"))
        assertEquals(1216, parameters.getInt("height"))
    }

    @Test
    fun `non unit strength adds the img2img block but keeps strength at 0_7`() {
        val payload = api.buildPayload(
            params = inpaintParams,
            seed = 7L,
            action = "infill",
            inpaintStrength = 0.6,
        )

        val parameters = payload.getJSONObject("parameters")
        assertEquals(0.7, parameters.getDouble("strength"), 1e-9)
        assertEquals(0.6, parameters.getDouble("inpaintImg2ImgStrength"), 1e-9)
        val img2img = parameters.getJSONObject("img2img")
        assertEquals(0.6, img2img.getDouble("strength"), 1e-9)
        assertTrue(img2img.getBoolean("color_correct"))
    }

    @Test
    fun `out of range strength is clamped`() {
        val parameters = api.buildPayload(
            params = inpaintParams,
            seed = 7L,
            action = "infill",
            inpaintStrength = 3.5,
        ).getJSONObject("parameters")

        assertEquals(1.0, parameters.getDouble("inpaintImg2ImgStrength"), 1e-9)
        assertFalse(parameters.has("img2img"))
    }

    @Test
    fun `text to image payload is unchanged by the infill branch`() {
        val payload = api.buildPayload(
            params = GenerateParams(model = "nai-diffusion-5-full", positivePrompt = "1girl", width = 832, height = 1216),
            seed = 42L,
        )

        val parameters = payload.getJSONObject("parameters")
        assertEquals("generate", payload.getString("action"))
        // 文生图**刻意不发** request_type（本项目既定约定，见 SESSION-HANDOFF 第 4 节）；
        // 只有 infill 分支才会把它设成 NativeInfillingRequest
        assertFalse(parameters.has("request_type"))
        assertFalse(parameters.has("image"))
        assertFalse(parameters.has("mask"))
        assertFalse(parameters.has("inpaintImg2ImgStrength"))
        assertFalse(parameters.has("extra_noise_seed"))
    }

    @Test
    fun `img2img payload carries strength noise and request type`() {
        val payload = api.buildPayload(
            params = GenerateParams(
                model = "nai-diffusion-5-full",
                positivePrompt = "1girl",
                width = 832,
                height = 1216,
            ),
            seed = 42L,
            action = "img2img",
            img2imgStrength = 0.7,
            img2imgNoise = 0.0,
        )

        assertEquals("img2img", payload.getString("action"))
        val parameters = payload.getJSONObject("parameters")
        // NAI 图生图采样器节点的默认值：强度 0.70 / 附加噪声 0.00
        assertEquals(0.7, parameters.getDouble("strength"), 1e-9)
        assertEquals(0.0, parameters.getDouble("noise"), 1e-9)
        assertEquals("Img2ImgRequest", parameters.getString("request_type"))
        // base64 底图由 img2img() 填，buildPayload 只负责其它字段
        assertFalse(parameters.has("image"))
        assertFalse(parameters.has("inpaintImg2ImgStrength"))
        assertFalse(parameters.has("add_original_image"))
    }

    @Test
    fun `inpaint model candidates fall back from curated to full`() {
        assertEquals(
            listOf("nai-diffusion-5-curated-inpainting", "nai-diffusion-5-full-inpainting"),
            api.inpaintModelCandidates("nai-diffusion-5-curated-inpainting"),
        )
        assertEquals(
            listOf("nai-diffusion-4-5-curated-inpainting", "nai-diffusion-4-5-full-inpainting"),
            api.inpaintModelCandidates("nai-diffusion-4-5-curated-inpainting"),
        )
        assertEquals(
            listOf("nai-diffusion-4-curated-inpainting", "nai-diffusion-4-full-inpainting"),
            api.inpaintModelCandidates("nai-diffusion-4-curated-inpainting"),
        )
        // full 档没有可退的，就它自己一个
        assertEquals(
            listOf("nai-diffusion-5-full-inpainting"),
            api.inpaintModelCandidates("nai-diffusion-5-full-inpainting"),
        )
    }
}
