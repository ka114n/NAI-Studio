package com.kallan.naistudio.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图片元数据解析（工具页「源数据」）：NovelAI / WebUI / ComfyUI 三类来源各来一份真实形状的样本。
 *
 * 样本都按各工具**实际写进 PNG 文本块的样子**构造（关键字 + 文本），
 * 所以这些用例同时是"认哪种格式"的可执行定义。
 */
class ImageMetadataTest {

    // ---------------------------------------------------------------- NovelAI

    /** V3 起的 NAI：`Comment` 是一个 JSON。 */
    private val naiChunks = listOf(
        "Software" to "NovelAI",
        "Source" to "Stable Diffusion XL C3E4",
        "Title" to "AI generated image",
        "Comment" to """
            {"prompt":"1girl, silver hair, red eyes, looking at viewer",
             "uc":"lowres, bad anatomy, worst quality",
             "steps":28,"scale":7.0,"sampler":"k_euler_ancestral","seed":917009594,
             "width":832,"height":1216,"model":"nai-diffusion-5-full"}
        """.trimIndent(),
    )

    @Test
    fun `reads novelai json metadata`() {
        val meta = ImageMetadataReader.fromChunks(naiChunks) ?: error("没解析出来")

        assertEquals(ImageMetadata.Source.NAI, meta.source)
        assertEquals("1girl, silver hair, red eyes, looking at viewer", meta.positivePrompt)
        assertEquals("lowres, bad anatomy, worst quality", meta.negativePrompt)
        assertEquals("832×1216", meta.fields.first { it.label == "Size" }.value)
        assertEquals("28", meta.fields.first { it.label == "Steps" }.value)
        assertEquals("7", meta.fields.first { it.label == "CFG" }.value) // 7.0 显示成 7
        assertEquals("k_euler_ancestral", meta.fields.first { it.label == "Sampler" }.value)

        val params = meta.params ?: error("没有参数")
        assertEquals("nai-diffusion-5-full", params.model)
        assertEquals(832, params.width)
        assertEquals(1216, params.height)
        assertEquals(28, params.steps)
        assertEquals(7.0, params.cfgScale, 1e-9)
        assertEquals(917009594L, params.seed)
        assertEquals("k_euler_ancestral", params.sampler)
    }

    @Test
    fun `reads novelai v4 caption plus character captions`() {
        val chunks = listOf(
            "Software" to "NovelAI",
            "Comment" to """
                {"v4_prompt":{"caption":{"base_caption":"1girl, cinematic lighting",
                 "char_captions":[{"char_caption":"2girls, twintails"}]}},
                 "v4_negative_prompt":{"caption":{"base_caption":"bad hands"}},
                 "steps":23,"scale":5.0,"sampler":"k_dpmpp_2m","seed":42,
                 "width":1024,"height":1024,"model":"nai-diffusion-4-5-full"}
            """.trimIndent(),
        )

        val meta = ImageMetadataReader.fromChunks(chunks) ?: error("没解析出来")
        assertEquals(ImageMetadata.Source.NAI, meta.source)
        assertTrue(meta.positivePrompt.startsWith("1girl, cinematic lighting"))
        assertTrue(meta.positivePrompt.contains("2girls, twintails"))
        assertEquals("bad hands", meta.negativePrompt)
        assertEquals(23, meta.params?.steps)
    }

    @Test
    fun `reads legacy novelai description`() {
        val chunks = listOf(
            "Software" to "NovelAI",
            "Description" to "1girl, masterpiece, best quality",
            "Comment" to "bad anatomy, lowres",
        )

        val meta = ImageMetadataReader.fromChunks(chunks) ?: error("没解析出来")
        assertEquals(ImageMetadata.Source.NAI, meta.source)
        assertEquals("1girl, masterpiece, best quality", meta.positivePrompt)
        assertEquals("bad anatomy, lowres", meta.negativePrompt)
    }

    /**
     * 真机抓下来的 NovelAI PNG 就是长这样：`uc` 是 **JSON null**、提示词在 `v4_prompt` 里，
     * 而且键值之间带空格。
     *
     * 踩坑：`optString("uc")` 对 JSON null 会返回字符串 `"null"` —— 不特判的话
     * 负向提示词会显示成"null"这个词。
     */
    @Test
    fun `real novelai file with null fields does not leak the word null`() {
        val chunks = listOf(
            "Title" to "AI generated image",
            "Description" to "year 2025, masterpiece, 1girl, silver hair",
            "Software" to "NovelAI",
            "Source" to "Stable Diffusion XL C3E4",
            "Comment" to """
                {"prompt": "year 2025, masterpiece, 1girl, silver hair",
                 "uc": null, "steps": 28, "scale": 7.0, "sampler": "k_euler_ancestral",
                 "seed": 917009594, "width": 832, "height": 1216,
                 "model": "nai-diffusion-5-full",
                 "v4_prompt": {"caption": {"base_caption": "1girl, silver hair",
                   "char_captions": null}},
                 "v4_negative_prompt": {"caption": {"base_caption": null}}}
            """.trimIndent(),
        )

        val meta = ImageMetadataReader.fromChunks(chunks) ?: error("没解析出来")
        assertEquals(ImageMetadata.Source.NAI, meta.source)
        assertEquals("1girl, silver hair", meta.positivePrompt)
        assertEquals("", meta.negativePrompt)
        assertEquals("832×1216", meta.fields.first { it.label == "Size" }.value)
        assertEquals(28, meta.params?.steps)
    }

    // -------------------------------------------------------------------- WebUI

    private val webUiChunks = listOf(
        "parameters" to """
            2girls, Sakayori's chest close-up perspective, head out of frame,
            Kaguya touching Sakayori's water chest
            Negative prompt: Low resolution, artistic errors, worst quality, (text:1.5), watermark
            Steps: 28, Sampler: DPM++ 2M Karras, CFG scale: 7, Seed: 1234567890, Size: 832x1216, Model hash: 6ce0161689, Model: animagineXL_v31, Clip skip: 2
        """.trimIndent(),
    )

    @Test
    fun `reads webui parameters block`() {
        val meta = ImageMetadataReader.fromChunks(webUiChunks) ?: error("没解析出来")

        assertEquals(ImageMetadata.Source.WEBUI, meta.source)
        assertTrue(meta.positivePrompt.startsWith("2girls, Sakayori's chest close-up perspective"))
        assertTrue(meta.positivePrompt.contains("Kaguya touching"))
        assertTrue(meta.negativePrompt.startsWith("Low resolution, artistic errors"))
        assertEquals("832×1216", meta.fields.first { it.label == "Size" }.value)
        assertEquals("animagineXL_v31", meta.fields.first { it.label == "Model" }.value)
        assertEquals("1234567890", meta.fields.first { it.label == "Seed" }.value)
        // 值里带逗号（DPM++ 2M Karras 没逗号，但 "Model hash: …, Model: …" 那一段要能拆对）
        assertEquals("DPM++ 2M Karras", meta.fields.first { it.label == "Sampler" }.value)

        val params = meta.params ?: error("没有参数")
        assertEquals(832, params.width)
        assertEquals(1216, params.height)
        assertEquals(28, params.steps)
        assertEquals(7.0, params.cfgScale, 1e-9)
        assertEquals(1234567890L, params.seed)
        // 采样器名 WebUI 有、NAI 没有 → 保留默认值（不瞎映射）
        assertEquals(com.kallan.naistudio.models.GenerateParams().sampler, params.sampler)
    }

    // ------------------------------------------------------------------ ComfyUI

    private val comfyChunks = listOf(
        "prompt" to """
            {
              "3": {"class_type":"KSampler","inputs":{"seed":777,"steps":30,"cfg":6.5,
                    "sampler_name":"euler_ancestral","positive":["6",0],"negative":["7",0]}},
              "4": {"class_type":"CheckpointLoaderSimple","inputs":{"ckpt_name":"nai-v5-full.safetensors"}},
              "5": {"class_type":"EmptyLatentImage","inputs":{"width":1216,"height":832}},
              "6": {"class_type":"CLIPTextEncode","inputs":{"text":"1girl, night sky, city lights"}},
              "7": {"class_type":"CLIPTextEncode","inputs":{"text":"blurry, low quality"}}
            }
        """.trimIndent(),
        "workflow" to """{"nodes":[],"links":[]}""",
    )

    @Test
    fun `reads comfyui api prompt graph`() {
        val meta = ImageMetadataReader.fromChunks(comfyChunks) ?: error("没解析出来")

        assertEquals(ImageMetadata.Source.COMFYUI, meta.source)
        assertEquals("1girl, night sky, city lights", meta.positivePrompt)
        assertEquals("blurry, low quality", meta.negativePrompt)
        assertEquals("1216×832", meta.fields.first { it.label == "Size" }.value)
        assertEquals("30", meta.fields.first { it.label == "Steps" }.value)
        assertEquals("6.5", meta.fields.first { it.label == "CFG" }.value)
        assertEquals("euler_ancestral", meta.fields.first { it.label == "Sampler" }.value)
        assertEquals("nai-v5-full.safetensors", meta.fields.first { it.label == "Model" }.value)

        val params = meta.params ?: error("没有参数")
        assertEquals(1216, params.width)
        assertEquals(832, params.height)
        assertEquals(777L, params.seed)
    }

    // -------------------------------------------------------------------- 其它

    @Test
    fun `unknown text chunks are shown but not claimed`() {
        val meta = ImageMetadataReader.fromChunks(
            listOf("Comment" to "hello world", "SomeTool" to "v1.2"),
        ) ?: error("有文本块就该有结果")
        assertEquals(ImageMetadata.Source.UNKNOWN, meta.source)
        assertEquals(2, meta.fields.size)
        assertTrue(meta.fields.any { it.label == "SomeTool" })
    }

    @Test
    fun `no text chunks means no metadata`() {
        assertNull(ImageMetadataReader.fromChunks(emptyList()))
    }

    @Test
    fun `a png without text chunks has no metadata`() {
        // 只有签名 + IHDR + IEND 的空壳 PNG
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        ) + byteArrayOf(0, 0, 0, 13) + "IHDR".toByteArray() + ByteArray(13) +
            byteArrayOf(0, 0, 0, 0) + "IEND".toByteArray() + byteArrayOf(0, 0, 0, 0)
        assertNull(ImageMetadataReader.read(png))
        // 不是 PNG 的字节也直接返回 null，不炸
        assertNull(ImageMetadataReader.read("not an image".toByteArray()))
    }
}
