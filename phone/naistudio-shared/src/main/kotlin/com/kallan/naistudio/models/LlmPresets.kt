package com.kallan.naistudio.models

import org.json.JSONObject

/**
 * **一条用户自建的 LLM 预设** —— 名字 + 服务地址 + API Key + 模型名。
 *
 * 用户 2026-09-17："预设是像提示词预设那样，**自己填 url、模型名、api，然后设置个名字**，
 * 然后在分功能 AI 里**选择要使用的 ai**，就不用一个个输入了。"
 *
 * 所以：预设由**用户自己建**（本类）；每个功能只挑一条（`AppSettings.llm*Preset` 存预设 id）。
 * 内置的服务商清单只作为"新建预设时快速填充"的参考（[LlmProviderHint]），**不参与选择**。
 */
data class LlmPresetEntry(
    /** 稳定 id（各功能的 `llm*Preset` 存的就是它）。 */
    val id: String,
    /** 用户起的名字，例如"我的 DeepSeek"。 */
    val name: String,
    /** 服务地址（`/chat/completions` 之前那一段）。 */
    val baseUrl: String,
    /** 密钥。⚠️ 属于敏感信息：`toBackupJson()` 会逐条剔除。 */
    val apiKey: String,
    /** 模型名。 */
    val model: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("baseUrl", baseUrl)
        put("apiKey", apiKey)
        put("model", model)
    }

    companion object {
        fun fromJson(json: JSONObject): LlmPresetEntry = LlmPresetEntry(
            id = json.optString("id"),
            name = json.optString("name"),
            baseUrl = json.optString("baseUrl"),
            apiKey = json.optString("apiKey"),
            model = json.optString("model"),
        )
    }
}

/**
 * 新建预设时的**快速填充**参考：服务地址取自各家官方文档那条（相对稳定）；
 * **模型名各家迭代很快**，这里给的是长期存在的常见值，填进去之后照样可以改。
 */
data class LlmProviderHint(
    val label: String,
    val baseUrl: String,
    val model: String,
    val note: String = "",
)

object LlmPresets {

    /** 常见服务商（只用于"快速填充"，不是"预设"本身）。 */
    val HINTS: List<LlmProviderHint> = listOf(
        LlmProviderHint("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "另有 deepseek-reasoner"),
        LlmProviderHint("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "需能直连；也可换 gpt-4o"),
        LlmProviderHint("月之暗面 Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k", "模型名以官网为准"),
        LlmProviderHint("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", "另有 glm-4-plus"),
        LlmProviderHint(
            "通义千问（兼容模式）",
            "https://dashscope.aliyuncs.com/compatible-mode/v1",
            "qwen-plus",
            "另有 qwen-max / qwen-turbo",
        ),
        LlmProviderHint(
            "硅基流动 SiliconFlow",
            "https://api.siliconflow.cn/v1",
            "Qwen/Qwen2.5-7B-Instruct",
            "模型名按站内列表填",
        ),
        LlmProviderHint(
            "Google Gemini（OpenAI 兼容）",
            "https://generativelanguage.googleapis.com/v1beta/openai",
            "gemini-2.0-flash",
            "模型名以官网为准",
        ),
        LlmProviderHint(
            "Anthropic Claude（OpenAI 兼容）",
            "https://api.anthropic.com/v1",
            "claude-3-5-sonnet-latest",
            "模型名以官网为准",
        ),
        LlmProviderHint("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "模型名以官网为准"),
        LlmProviderHint(
            "OpenRouter（聚合）",
            "https://openrouter.ai/api/v1",
            "openai/gpt-4o-mini",
            "模型名要带前缀：openai/…、anthropic/…",
        ),
        LlmProviderHint("Ollama（本地）", "http://localhost:11434/v1", "qwen2.5:7b", "本地不用 key"),
        LlmProviderHint("LM Studio（本地）", "http://localhost:1234/v1", "local-model", "只认已加载的那个模型"),
    )

    /** 新建预设时给个不重复的 id（时间戳够用，且不会因为中文名撞车）。 */
    fun newId(): String = "llm-" + System.currentTimeMillis().toString(36)
}
