package com.kallan.naistudio.services

import com.kallan.naistudio.models.GenerateParams
import com.kallan.naistudio.models.NaiCatalog
import com.kallan.naistudio.models.StealthPng
import org.json.JSONObject

/**
 * 图片元数据解析（工具页「元数据」用）。
 *
 * 参考 ComfyUI 的 **NAI 元数据加载器** 节点：很多工具在存 PNG 时会把生成参数写进
 * PNG 的文本块（`tEXt` / `iTXt` / `zTXt`），所以**图本身自带"这张图怎么来的"**。
 * 这里把这些块读出来并归一化成统一的字段表，能认三种来源：
 *
 * | 来源 | 认法 | 典型关键字 |
 * | --- | --- | --- |
 * | NovelAI | `Comment` 是 JSON（`prompt` / `uc` / `steps` / `scale` / `sampler` / `seed` / `width` / `height`），或新版 `v4_prompt`；旧图是 `Description` 纯文本 + `Software: NovelAI` | `Comment` / `Description` |
 * | WebUI (A1111 / Forge) | `parameters` 纯文本：第一段是正向，`Negative prompt:` 之后是负向，最后一行是 `Steps: …, Sampler: …, CFG scale: …, Seed: …, Size: WxH, Model: …` | `parameters` |
 * | ComfyUI | `prompt` 是 API 格式 JSON（`{节点id: {class_type, inputs}}`）；只有 `workflow`（界面格式）时也认，只是参数抽取有限 | `prompt` / `workflow` |
 *
 * 纯逻辑、不碰 Android，所以能在 JVM 单测里跑（见 `ImageMetadataTest`）。
 */
data class ImageMetadata(
    val source: Source,
    /** 归一化后的展示字段（顺序固定，界面直接遍历）。 */
    val fields: List<Field>,
    val positivePrompt: String = "",
    val negativePrompt: String = "",
    /**
     * 能凑齐的生成参数（"复用参数"用）。
     *
     * 采样器/模型名不在 NAI 目录里时**保留默认值**（WebUI 的 `DPM++ 2M` 之类 NAI 没有对应项）。
     */
    val params: GenerateParams? = null,
    /** 原始文本块（界面上的「完整元数据」看这个）。 */
    val rawChunks: List<Pair<String, String>> = emptyList(),
) {
    /** 元数据来源。 */
    enum class Source(val id: String) {
        /** NovelAI 官方客户端 / 本 App 生成的图。 */
        NAI("nai"),

        /** Stable Diffusion WebUI（A1111 / Forge 等）。 */
        WEBUI("webui"),

        /** ComfyUI。 */
        COMFYUI("comfyui"),

        /** 有文本块但认不出来源。 */
        UNKNOWN("unknown"),
    }

    data class Field(val label: String, val value: String)

    /** 有没有可以填进正向提示词框的内容。 */
    val hasPrompt: Boolean get() = positivePrompt.isNotBlank() || negativePrompt.isNotBlank()
}

object ImageMetadataReader {

    /** 从图片字节里读元数据；读不出来（不是 PNG / 没有文本块也没有隐写）返回 null。 */
    fun read(bytes: ByteArray): ImageMetadata? {
        if (!PngMetadata.isPng(bytes)) return null

        // ① 常规路径：PNG 文本块（tEXt / iTXt / zTXt）
        val chunks = PngMetadata.readTextChunks(bytes)
        if (chunks.isNotEmpty()) return fromChunks(chunks)

        // ② NovelAI 的 alpha 通道隐写（`stealth_pngcomp`）。
        //
        // 为什么必须补这一条：**NovelAI 生成的图不一定带文本块**。实测一张正规下载的
        // 832×1216 图，chunk 只有 `IHDR / IDAT / IEND`，元数据全在 alpha 最低位里
        //（肉眼不可见）。以前只读文本块 → 这种图的提示词一条都读不出来。
        //
        // 隐写解出来的键（`Comment` / `Description` / `Software` / `Source`）
        // 与文本块那条路**完全同名**，所以直接复用下面同一套解析器。
        val stealth = StealthPng.decode(bytes) ?: return null
        return fromChunks(stealth.map { (key, value) -> key to value })
    }

    /** 直接从"关键字 → 文本"的块列表解析（单测用这个就够了）。 */
    fun fromChunks(chunks: List<Pair<String, String>>): ImageMetadata? {
        if (chunks.isEmpty()) return null
        val map = LinkedHashMap<String, String>()
        chunks.forEach { (keyword, text) -> map.putIfAbsent(keyword, text) }

        parseComfyUi(map)?.let { return it.copy(rawChunks = chunks) }
        parseWebUi(map)?.let { return it.copy(rawChunks = chunks) }
        parseNai(map)?.let { return it.copy(rawChunks = chunks) }
        return ImageMetadata(
            source = ImageMetadata.Source.UNKNOWN,
            fields = map.map { (key, value) -> ImageMetadata.Field(key, summarize(value)) },
            rawChunks = chunks,
        )
    }

    // ------------------------------------------------------------------ NovelAI

    private fun parseNai(map: Map<String, String>): ImageMetadata? {
        val comment = map["Comment"]
        val description = map["Description"]
        val looksNai = map["Software"]?.contains("NovelAI", ignoreCase = true) == true ||
            map["Source"]?.contains("Stable Diffusion", ignoreCase = true) == true
        if (comment == null && description == null) return null

        // 1) `Comment` 是 JSON（V3 起的格式）
        val json = comment?.let { text ->
            runCatching { JSONObject(text.trim()) }.getOrNull()
        }
        if (json != null && (json.has("prompt") || json.has("v4_prompt") || json.has("uc"))) {
            return fromNaiJson(json, map)
        }
        // 2) 旧格式：`Description` = 正向提示词，`Comment` = 负向（或别的备注）
        if (description != null || looksNai) {
            val positive = description?.trim().orEmpty()
            val negative = comment?.trim().orEmpty()
            if (positive.isEmpty() && negative.isEmpty()) return null
            return ImageMetadata(
                source = ImageMetadata.Source.NAI,
                fields = buildList {
                    add(ImageMetadata.Field("Positive", positive))
                    if (negative.isNotEmpty()) add(ImageMetadata.Field("Negative", negative))
                    map["Software"]?.let { add(ImageMetadata.Field("Software", it)) }
                },
                positivePrompt = positive,
                negativePrompt = negative,
                params = GenerateParams(
                    positivePrompt = positive,
                    negativePrompt = negative,
                ),
            )
        }
        return null
    }

    private fun fromNaiJson(json: JSONObject, map: Map<String, String>): ImageMetadata {
        // V4/V5：提示词在 v4_prompt.caption（base_caption + 角色分区），旧版直接在 prompt
        val v4 = json.optJSONObject("v4_prompt")?.optJSONObject("caption")
        val v4Negative = json.optJSONObject("v4_negative_prompt")?.optJSONObject("caption")
        val positive = buildString {
            val base = v4.text("base_caption").ifBlank { json.text("prompt") }
            append(base.trim())
            val characters = v4?.optJSONArray("char_captions")
            if (characters != null) {
                for (index in 0 until characters.length()) {
                    val caption = characters.optJSONObject(index).text("char_caption").trim()
                    if (caption.isNotEmpty()) {
                        if (isNotEmpty()) append('\n')
                        append(caption)
                    }
                }
            }
        }
        val negative = v4Negative.text("base_caption")
            .ifBlank { json.text("uc") }
            .trim()

        val width = json.optInt("width", 0)
        val height = json.optInt("height", 0)
        val steps = json.optInt("steps", 0)
        val scale = json.optDouble("scale", 0.0)
        val sampler = json.text("sampler")
        val model = json.text("model")
        val seed = json.optLong("seed", 0L)

        return ImageMetadata(
            source = ImageMetadata.Source.NAI,
            fields = buildList {
                add(ImageMetadata.Field("Positive", positive))
                if (negative.isNotEmpty()) add(ImageMetadata.Field("Negative", negative))
                if (model.isNotEmpty()) add(ImageMetadata.Field("Model", model))
                if (width > 0 && height > 0) add(ImageMetadata.Field("Size", "${width}×${height}"))
                if (steps > 0) add(ImageMetadata.Field("Steps", steps.toString()))
                if (scale > 0) add(ImageMetadata.Field("CFG", trimNumber(scale)))
                if (sampler.isNotEmpty()) add(ImageMetadata.Field("Sampler", sampler))
                if (seed != 0L) add(ImageMetadata.Field("Seed", seed.toString()))
                map["Software"]?.let { add(ImageMetadata.Field("Software", it)) }
                map["Source"]?.let { add(ImageMetadata.Field("Source", it)) }
            },
            positivePrompt = positive,
            negativePrompt = negative,
            params = GenerateParams(
                model = model.takeIf { it in NaiCatalog.models.map { entry -> entry.value } }
                    ?: GenerateParams().model,
                positivePrompt = positive,
                negativePrompt = negative,
                width = width.takeIf { it > 0 } ?: GenerateParams().width,
                height = height.takeIf { it > 0 } ?: GenerateParams().height,
                steps = steps.takeIf { it > 0 } ?: GenerateParams().steps,
                cfgScale = scale.takeIf { it > 0 } ?: GenerateParams().cfgScale,
                sampler = sampler.takeIf { name -> NaiCatalog.samplers.any { it.value == name } }
                    ?: GenerateParams().sampler,
                seed = seed.takeIf { it != 0L } ?: GenerateParams().seed,
            ),
        )
    }

    // -------------------------------------------------------------------- WebUI

    private fun parseWebUi(map: Map<String, String>): ImageMetadata? {
        val text = map["parameters"] ?: return null
        val lines = text.replace("\r\n", "\n").split('\n')
        val negativeIndex = lines.indexOfFirst { it.startsWith("Negative prompt:") }
        val settingsIndex = lines.indexOfFirst { SETTINGS_LINE.containsMatchIn(it) }
        if (settingsIndex < 0) return null

        val positiveEnd = listOf(negativeIndex, settingsIndex).filter { it >= 0 }.min()
        val positive = lines.subList(0, positiveEnd).joinToString("\n").trim()
        val negative = if (negativeIndex >= 0 && negativeIndex < settingsIndex) {
            buildString {
                append(lines[negativeIndex].removePrefix("Negative prompt:").trim())
                for (index in negativeIndex + 1 until settingsIndex) {
                    if (isNotEmpty()) append('\n')
                    append(lines[index].trim())
                }
            }.trim()
        } else {
            ""
        }

        val settings = parseSettingsLine(lines[settingsIndex])
        val size = settings["Size"].orEmpty()
        val width = size.substringBefore('x').trim().toIntOrNull() ?: 0
        val height = size.substringAfter('x', "").trim().toIntOrNull() ?: 0

        return ImageMetadata(
            source = ImageMetadata.Source.WEBUI,
            fields = buildList {
                add(ImageMetadata.Field("Positive", positive))
                if (negative.isNotEmpty()) add(ImageMetadata.Field("Negative", negative))
                settings["Model"]?.let { add(ImageMetadata.Field("Model", it)) }
                if (width > 0 && height > 0) add(ImageMetadata.Field("Size", "${width}×${height}"))
                settings["Steps"]?.let { add(ImageMetadata.Field("Steps", it)) }
                settings["CFG scale"]?.let { add(ImageMetadata.Field("CFG", it)) }
                settings["Sampler"]?.let { add(ImageMetadata.Field("Sampler", it)) }
                settings["Seed"]?.let { add(ImageMetadata.Field("Seed", it)) }
                settings["Schedule type"]?.let { add(ImageMetadata.Field("Schedule", it)) }
            },
            positivePrompt = positive,
            negativePrompt = negative,
            params = GenerateParams(
                positivePrompt = positive,
                negativePrompt = negative,
                width = width.takeIf { it > 0 } ?: GenerateParams().width,
                height = height.takeIf { it > 0 } ?: GenerateParams().height,
                steps = settings["Steps"]?.toIntOrNull() ?: GenerateParams().steps,
                cfgScale = settings["CFG scale"]?.toDoubleOrNull() ?: GenerateParams().cfgScale,
                seed = settings["Seed"]?.toLongOrNull() ?: GenerateParams().seed,
            ),
        )
    }

    /** `Steps: 20, Sampler: Euler a, CFG scale: 7, …` → 键值对（值里可能还有逗号）。 */
    private fun parseSettingsLine(line: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        val parts = line.split(SETTINGS_SPLIT)
        parts.forEach { part ->
            val separator = part.indexOf(": ")
            if (separator <= 0) return@forEach
            val key = part.substring(0, separator).trim()
            val value = part.substring(separator + 2).trim()
            if (key.isNotEmpty()) result[key] = value
        }
        return result
    }

    // ------------------------------------------------------------------ ComfyUI

    private fun parseComfyUi(map: Map<String, String>): ImageMetadata? {
        val promptJson = map["prompt"]?.let { text ->
            runCatching { JSONObject(text.trim()) }.getOrNull()
        }
        val workflowJson = map["workflow"]?.let { text ->
            runCatching { JSONObject(text.trim()) }.getOrNull()
        }
        if (promptJson == null && workflowJson == null) return null
        // ComfyUI 的 prompt 是 {节点id: {class_type, inputs}}；workflow 是 {nodes:[…]}
        val isComfy = promptJson?.let { json ->
            json.keys().asSequence().any { key ->
                json.optJSONObject(key)?.has("class_type") == true
            }
        } ?: true
        if (!isComfy) return null

        val nodes = promptJson?.let { json ->
            json.keys().asSequence().mapNotNull { key ->
                json.optJSONObject(key)?.let { node -> key to node }
            }.toMap()
        }.orEmpty()

        val positive = resolveText(nodes, nodes.values.firstOrNull { isSampler(it) }
            ?.optJSONObject("inputs")?.opt("positive"))
        val negative = resolveText(nodes, nodes.values.firstOrNull { isSampler(it) }
            ?.optJSONObject("inputs")?.opt("negative"))
        val sampler = nodes.values.firstOrNull { isSampler(it) }?.optJSONObject("inputs")
        val latent = nodes.values.firstOrNull { isLatent(it) }?.optJSONObject("inputs")
        val checkpoint = nodes.values.firstOrNull { it.optString("class_type").contains("CheckpointLoader") }
            ?.optJSONObject("inputs").text("ckpt_name")

        val width = latent?.optInt("width", 0) ?: 0
        val height = latent?.optInt("height", 0) ?: 0
        val steps = sampler?.optInt("steps", 0) ?: 0
        val cfg = sampler?.optDouble("cfg", 0.0) ?: 0.0
        val seed = sampler?.optLong("seed", 0L) ?: 0L
        val samplerName = sampler.text("sampler_name")

        return ImageMetadata(
            source = ImageMetadata.Source.COMFYUI,
            fields = buildList {
                if (positive.isNotEmpty()) add(ImageMetadata.Field("Positive", positive))
                if (negative.isNotEmpty()) add(ImageMetadata.Field("Negative", negative))
                if (checkpoint.isNotEmpty()) add(ImageMetadata.Field("Model", checkpoint))
                if (width > 0 && height > 0) add(ImageMetadata.Field("Size", "${width}×${height}"))
                if (steps > 0) add(ImageMetadata.Field("Steps", steps.toString()))
                if (cfg > 0) add(ImageMetadata.Field("CFG", trimNumber(cfg)))
                if (samplerName.isNotEmpty()) add(ImageMetadata.Field("Sampler", samplerName))
                if (seed != 0L) add(ImageMetadata.Field("Seed", seed.toString()))
                add(ImageMetadata.Field("Nodes", nodes.size.toString()))
            },
            positivePrompt = positive,
            negativePrompt = negative,
            params = GenerateParams(
                positivePrompt = positive,
                negativePrompt = negative,
                width = width.takeIf { it > 0 } ?: GenerateParams().width,
                height = height.takeIf { it > 0 } ?: GenerateParams().height,
                steps = steps.takeIf { it > 0 } ?: GenerateParams().steps,
                cfgScale = cfg.takeIf { it > 0 } ?: GenerateParams().cfgScale,
                seed = seed.takeIf { it != 0L } ?: GenerateParams().seed,
            ),
        )
    }

    private fun isSampler(node: JSONObject): Boolean {
        val type = node.optString("class_type")
        return type.contains("KSampler") || type.contains("SamplerCustom")
    }

    private fun isLatent(node: JSONObject): Boolean {
        val type = node.optString("class_type")
        return type.contains("EmptyLatent") || type.contains("EmptySD3Latent")
    }

    /**
     * ComfyUI 里节点输入可能是 `["12", 0]` 这种**连线**，也可能是直接写的文本。
     * 连线就顺着 id 找回那个节点（只跟一层，够用了），取它的 `inputs.text` 或 `inputs.string`。
     */
    private fun resolveText(nodes: Map<String, JSONObject>, value: Any?): String = when (value) {
        is String -> value.trim()
        is org.json.JSONArray -> {
            val target = nodes[value.optString(0)]
            val inputs = target?.optJSONObject("inputs")
            inputs.text("text").ifBlank { inputs.text("string") }
                .ifBlank {
                    // 有的节点把文本放在 widgets 里（API 格式一般不会），再兜一层
                    target.text("title")
                }
                .trim()
        }
        else -> ""
    }

    // -------------------------------------------------------------------- 工具

    /**
     * 读一个"文本"字段。
     *
     * 为什么不能直接用 `optString`：JSON 里是 `null` 的字段，`optString` 会返回字符串 `"null"`
     * （`JSONObject.NULL.toString()` 就是 "null"），于是负向提示词会变成写着 "null" 的一句话。
     * NovelAI 的真实元数据里 `"uc": null` 很常见，所以统一走这个口径。
     */
    private fun JSONObject?.text(key: String): String {
        if (this == null || !has(key) || isNull(key)) return ""
        val value = optString(key)
        return if (value == "null") "" else value
    }

    private val SETTINGS_LINE = Regex("^Steps: \\d+")
    private val SETTINGS_SPLIT = Regex(",\\s*(?=[A-Z][A-Za-z ]*: )")

    /** `7.0` → `7`（界面上别显示没意义的小数零）。 */
    private fun trimNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    /** 原始块太长时截断一下（界面上的表格一行显示不了那么多字）。 */
    private fun summarize(text: String): String =
        if (text.length <= 120) text else text.take(117) + "..."
}
