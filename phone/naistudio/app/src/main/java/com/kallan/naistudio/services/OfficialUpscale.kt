package com.kallan.naistudio.services

import org.json.JSONObject

/**
 * NovelAI **官方放大（Official Upscale）**的规格（**纯逻辑，可写 JVM 单测**）。
 *
 * 口径来自 ComfyUI 插件 `novelai-genytools` 的 `nai_client.py`（`NAIOfficialUpscale` 节点）：
 *  · 端点 `POST {imageBase}/ai/upscale`；
 *  · `multipart/form-data`：一个 `image` 文件部件（PNG）+ 一个 `request` JSON 部件；
 *  · request 里 `image` 字段的值是**附件键名**（NovelAI 的老约定："哪个字段名对应哪个附件"）；
 *  · `model` 固定 `nai-diffusion-5-curated`，`declared_blur_sigma` 固定 0；
 *  · **输出固定 2×**（新版权衡），所以界面上叫「2x 放大」；
 *  · **输入总像素有上限 3,145,728**，费用按输入像素分档：1 / 2 / 3 / 4 Anlas。
 */
object OfficialUpscale {

    /** 端点路径（拼在 imageBase 后面）。 */
    const val PATH = "/ai/upscale"

    /** 官方放大固定使用的模型。 */
    const val MODEL = "nai-diffusion-5-curated"

    /** 固定 0（官方新版不需要声明模糊）。 */
    const val DECLARED_BLUR_SIGMA = 0

    /** 输入总像素上限；超过官方会拒。 */
    const val MAX_INPUT_PIXELS = 3_145_728

    /** 输出倍数（固定 2×）。 */
    const val FACTOR = 2

    /** 费用分档：不超过该像素数 → 对应 Anlas。 */
    private val PRICE_TIERS = listOf(
        1_048_576 to 1,
        1_747_627 to 2,
        2_446_678 to 3,
        MAX_INPUT_PIXELS to 4,
    )

    /**
     * 按输入宽高算**预计消耗的 Anlas**；尺寸非法或超出上限返回 null（调用方据此拦下并提示）。
     */
    fun price(width: Int, height: Int): Int? {
        if (width <= 0 || height <= 0) return null
        val pixels = width.toLong() * height.toLong()
        if (pixels > MAX_INPUT_PIXELS) return null
        return PRICE_TIERS.firstOrNull { pixels <= it.first }?.second
    }

    /** 这张图能不能走官方放大。 */
    fun supported(width: Int, height: Int): Boolean = price(width, height) != null

    /** 放大后的尺寸（固定 2×）。 */
    fun outputSize(width: Int, height: Int): Pair<Int, Int> = width * FACTOR to height * FACTOR

    /** `request` 部件的内容。 */
    fun requestJson(): JSONObject = JSONObject().apply {
        // 值是附件键名，与 multipart 里的 "image" 部件对应
        put("image", "image")
        put("model", MODEL)
        put("declared_blur_sigma", DECLARED_BLUR_SIGMA)
    }
}
