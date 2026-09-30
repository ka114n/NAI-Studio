package com.kallan.naistudio.services

import org.json.JSONObject
import kotlin.math.sqrt

/**
 * NovelAI **导演工具（Director Tools）** 的规格（**纯逻辑，可写 JVM 单测**）。
 *
 * 口径来自 ComfyUI 插件 `novelai-genytools` 的 `full_nodes.py`（6 个工具节点）
 * 与 `nai_client.py` 的 `director()` / `prepare_director_image()`：
 *  · 端点 `POST {imageBase}/ai/augment-image`，**纯 JSON**（不是 multipart），
 *    body = `{ req_type, use_new_shared_trial: true, width, height, image: base64, …工具参数 }`；
 *  · `req_type` 六种（清理杂物有"保留文字气泡"变体）；
 *  · 输入图片有**像素预算**：超过上限就等比缩小，低于触发值就放大到目标值；
 *  · `defry` 一律 0–5；修改表情把 `emotion;;附加提示词` 塞进 `prompt`；
 *  · 返回 ZIP，**移除背景返回 3 张**（masked / generated / blend），其余 1 张。
 */
object DirectorTools {

    /** 端点路径。 */
    const val PATH = "/ai/augment-image"

    // ---- 像素预算（与插件的 prepare_director_image 一致）----
    const val MAX_PIXELS = 3_145_728 - 2_000
    const val MIN_TRIGGER_PIXELS = 1_011_712
    const val TARGET_PIXELS = 1_048_576

    /** defry 的取值范围。 */
    const val MAX_DEFRY = 5

    /** 一个导演工具。 */
    data class Tool(
        /** 我们的 id（也是 i18n key 的后缀）。 */
        val id: String,
        /** 中文名（与插件一致，便于对表）。 */
        val name: String,
        /** 默认 req_type。 */
        val reqType: String,
        /** 「保留文字气泡」对应的 req_type（仅清理杂物有）。 */
        val keepBubblesReqType: String? = null,
        val hasPrompt: Boolean = false,
        val hasDefry: Boolean = false,
        val hasEmotion: Boolean = false,
        val hasKeepBubbles: Boolean = false,
        /** 服务端会返回几张（移除背景 3 张）。 */
        val resultCount: Int = 1,
    )

    /** 六个工具，顺序与插件一致。 */
    val ALL = listOf(
        Tool(id = "bg-removal", name = "移除背景", reqType = "bg-removal", resultCount = 3),
        Tool(
            id = "declutter",
            name = "清理杂物",
            reqType = "declutter",
            keepBubblesReqType = "declutter-keep-bubbles",
            hasKeepBubbles = true,
        ),
        Tool(id = "lineart", name = "提取线稿", reqType = "lineart"),
        Tool(id = "sketch", name = "转换草图", reqType = "sketch"),
        Tool(id = "colorize", name = "自动上色", reqType = "colorize", hasPrompt = true, hasDefry = true),
        Tool(id = "emotion", name = "修改表情", reqType = "emotion", hasDefry = true, hasEmotion = true),
    )

    /** 修改表情可选的表情（与插件的 EMOTIONS 一致，配中文）。 */
    val EMOTIONS = listOf(
        "neutral" to "平静",
        "happy" to "开心",
        "sad" to "难过",
        "angry" to "生气",
        "scared" to "害怕",
        "surprised" to "惊讶",
        "tired" to "疲惫",
        "excited" to "兴奋",
        "confused" to "困惑",
        "smug" to "得意",
    )

    fun byId(id: String): Tool = ALL.firstOrNull { it.id == id } ?: ALL.first()

    /** 实际发出的 req_type（清理杂物按"保留文字气泡"切换）。 */
    fun reqTypeOf(tool: Tool, keepTextBubbles: Boolean): String =
        if (tool.hasKeepBubbles && keepTextBubbles) tool.keepBubblesReqType ?: tool.reqType else tool.reqType

    /**
     * 按插件规则算发给服务端的尺寸：
     * 像素 > [MAX_PIXELS] → 等比缩小；仍 < [MIN_TRIGGER_PIXELS] → 放大到 [TARGET_PIXELS]。
     */
    fun budgetSize(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 64 to 64
        var w = width
        var h = height
        val pixels = w.toLong() * h.toLong()
        if (pixels > MAX_PIXELS) {
            val scale = sqrt(MAX_PIXELS.toDouble() / pixels.toDouble())
            w = maxOf(1, (w * scale).toInt())
            h = maxOf(1, (h * scale).toInt())
        }
        val after = w.toLong() * h.toLong()
        if (after < MIN_TRIGGER_PIXELS) {
            val scale = sqrt(TARGET_PIXELS.toDouble() / after.toDouble())
            w = maxOf(1, (w * scale).toInt())
            h = maxOf(1, (h * scale).toInt())
        }
        return w to h
    }

    /**
     * 请求体。
     *
     * @param prompt 自动上色用；修改表情则传 `"<emotion>;;<附加提示词>"`
     * @param defry  自动上色 / 修改表情用（0–5）
     */
    fun requestJson(
        reqType: String,
        width: Int,
        height: Int,
        base64Image: String,
        prompt: String? = null,
        defry: Int? = null,
    ): JSONObject = JSONObject().apply {
        put("req_type", reqType)
        put("use_new_shared_trial", true)
        put("width", width)
        put("height", height)
        put("image", base64Image)
        if (!prompt.isNullOrEmpty()) put("prompt", prompt)
        if (defry != null) put("defry", defry.coerceIn(0, MAX_DEFRY))
    }

    /** 修改表情的 prompt 拼法（与插件一致：`emotion;;extra`）。 */
    fun emotionPrompt(emotion: String, extraPrompt: String): String = "$emotion;;$extraPrompt"
}
