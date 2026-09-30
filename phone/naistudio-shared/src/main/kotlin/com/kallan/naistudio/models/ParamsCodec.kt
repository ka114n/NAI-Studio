package com.kallan.naistudio.models

import org.json.JSONObject

/**
 * `GenerateParams` 的 JSON 编解码。
 *
 * 字段名与 fromJson 的默认值**照抄参考实现** `mobile/lib/models/nai_models.dart`。
 * 参考实现的 `fromJson` 末尾会调用 `.normalized()`，这里保持一致（由 [fromJson] 负责）。
 */
object GenerateParamsCodec {

    fun toJson(params: GenerateParams): JSONObject = JSONObject().apply {
        put("model", params.model)
        put("stylePrompt", params.stylePrompt)
        put("positivePrompt", params.positivePrompt)
        put("negativePrompt", params.negativePrompt)
        put("width", params.width)
        put("height", params.height)
        put("steps", params.steps)
        put("cfgScale", params.cfgScale)
        put("cfgRescale", params.cfgRescale)
        put("sampler", params.sampler)
        put("noiseSchedule", params.noiseSchedule)
        put("seed", params.seed)
        put("seedMode", params.seedMode)
        put("ucPreset", params.ucPreset)
        put("qualityPreset", params.qualityPreset)
        put("qualityToggle", params.qualityToggle)
        put("transparentBackground", params.transparentBackground)
        put("smea", params.smea)
        put("smeaDyn", params.smeaDyn)
        put("variety", params.variety)
        put("fileNamePrefix", params.fileNamePrefix)
    }

    fun fromJson(json: JSONObject?): GenerateParams {
        if (json == null) return GenerateParams()
        val defaults = GenerateParams()

        val qualityToggle = json.optBoolean("qualityToggle", defaults.qualityToggle)
        // 参考实现：qualityPreset 缺键时由 qualityToggle 推导
        val qualityPreset = json.optString("qualityPreset").takeIf { it.isNotEmpty() }
            ?: if (qualityToggle) "standard" else "none"

        return GenerateParams(
            model = json.optString("model", defaults.model),
            stylePrompt = json.optString("stylePrompt", defaults.stylePrompt),
            positivePrompt = json.optString("positivePrompt", defaults.positivePrompt),
            negativePrompt = json.optString("negativePrompt", defaults.negativePrompt),
            width = json.optInt("width", defaults.width),
            height = json.optInt("height", defaults.height),
            steps = json.optInt("steps", defaults.steps),
            cfgScale = json.optDouble("cfgScale", defaults.cfgScale),
            cfgRescale = json.optDouble("cfgRescale", defaults.cfgRescale),
            sampler = json.optString("sampler", defaults.sampler),
            noiseSchedule = json.optString("noiseSchedule", defaults.noiseSchedule),
            seed = json.optLong("seed", defaults.seed),
            seedMode = json.optString("seedMode", defaults.seedMode),
            ucPreset = json.optInt("ucPreset", defaults.ucPreset),
            qualityPreset = qualityPreset,
            qualityToggle = qualityToggle,
            transparentBackground = json.optBoolean("transparentBackground", defaults.transparentBackground),
            smea = json.optBoolean("smea", defaults.smea),
            smeaDyn = json.optBoolean("smeaDyn", defaults.smeaDyn),
            variety = json.optBoolean("variety", defaults.variety),
            fileNamePrefix = json.optString("fileNamePrefix", defaults.fileNamePrefix),
        ).normalized()
    }
}

/**
 * 角色卡的 JSON 编解码。字段名沿用 ComfyUI 节点 `novelai-genytools` 的 `defaultRole`：
 * `name / positive / negative / enabled / ai_choice / row / col / x / y / expanded`。
 * 坐标 clamp 到 0..1，非法值回落到 0.5。
 */
object CharCaptionCodec {

    fun toJson(item: CharCaptionItem): JSONObject = JSONObject().apply {
        put("name", item.name)
        put("positive", item.prompt)
        put("negative", item.negativePrompt)
        put("enabled", item.enabled)
        // 节点存的是 ai_choice，我们存的是它的取反值
        put("ai_choice", !item.useCoords)
        put("col", (item.x * 5).toInt().coerceIn(0, 4))
        put("row", (item.y * 5).toInt().coerceIn(0, 4))
        put("x", item.x)
        put("y", item.y)
        put("expanded", item.expanded)
        // 漫画模式的槽位类型。**新增的可选字段**：老备份没有这个键 → 读到默认空串，
        // 且老版本读到它也只是忽略未知键，所以两个方向都兼容。
        if (item.panelRole.isNotEmpty()) put("panelRole", item.panelRole)
    }

    fun fromJson(json: JSONObject?): CharCaptionItem {
        if (json == null) return CharCaptionItem()
        // 兼容两种键名：节点的 positive/negative/ai_choice，以及更早的 prompt/negativePrompt/useCoords
        val positive = json.optString("positive").ifEmpty { json.optString("prompt", "") }
        val negative = json.optString("negative").ifEmpty { json.optString("negativePrompt", "") }
        val useCoords = if (json.has("ai_choice")) {
            !json.optBoolean("ai_choice", true)
        } else {
            json.optBoolean("useCoords", false)
        }
        return CharCaptionItem(
            name = json.optString("name", ""),
            prompt = positive,
            negativePrompt = negative,
            enabled = json.optBoolean("enabled", true),
            useCoords = useCoords,
            x = coordinate(json, "x"),
            y = coordinate(json, "y"),
            expanded = json.optBoolean("expanded", false),
            panelRole = json.optString("panelRole", ""),
        )
    }

    private fun coordinate(json: JSONObject, key: String): Double {
        if (!json.has(key) || json.isNull(key)) return 0.5
        val value = json.optDouble(key, 0.5)
        if (!value.isFinite()) return 0.5
        return value.coerceIn(0.0, 1.0)
    }
}
