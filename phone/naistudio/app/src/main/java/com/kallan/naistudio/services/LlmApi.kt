package com.kallan.naistudio.services

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.kallan.naistudio.models.StPreset
import org.json.JSONArray
import org.json.JSONObject

/** LLM 调用失败。`message` 是给用户看的中文。 */
class LlmException(message: String) : Exception(message)

/**
 * **LLM API 客户端** —— 行为**照 ComfyUI「API LLM通用链路」节点的源码**（`comfyui_LLM_party/llm.py`）。
 *
 * 用户 2026-09-16："**全按照节点功能实现，包括对话格式**"。逐条复刻，行号对应 main 分支那份 `llm.py`：
 *
 * | 源码 | 这里的实现 |
 * | --- | --- |
 * | `1454` 历史第一条是 system | [messages] 永远以 system 开头（空的会被剔除，见下） |
 * | `1603` `system_prompt = system_prompt + system_prompt_input` | [ChatRequest.systemPromptInput] 拼接 |
 * | `577-581` 空 system **整条剔除** | [messages] 里空的 system 直接不放 |
 * | `582-588` `o1/o2/o3`：system 的 role 换成 user 并补一条 assistant 确认 | [messages] 同款 |
 * | `1690-1692 / 1736-1740` system = 当前 system_prompt（+ 工具说明） | [ChatRequest.toolsInSysPrompt] |
 * | `1748-1751` `file_content` 追加到 system | [ChatRequest.fileContent] |
 * | `625-630` 请求体 `model`/`messages`/`temperature`/`max_tokens`/`stream`/`**extra` | [Request] 各字段 |
 * | `413-417` `gpt-5*` 用 `max_completion_tokens` | [normalizeOpenAiChatKwargs] |
 * | `620-621` 本轮 user 追加在最后 | [messages] 末尾 |
 * | `539-576` 图片：无 imgbb key → base64；有 key → 先上传取 URL | [ChatRequest.imageDataUrl] / [uploadToImgbb] |
 * | `526-536` `ensure_version_suffix` | [endpoint] |
 * | 工具调用循环 / `stream=True` 增量拼接（含 arguments 拼接） | [send] |
 */
class LlmApi {

    /**
     * 一次 LLM 请求的对话参数。
     *
     * @param reasoningEffort 「思考程度」。`null` / `"off"` = 不发 `reasoning_effort`
     *   （字段是 OpenAI o 系列带起来的，大量兼容服务不认识；默认不发最稳）。
     * @param temperature 采样温度（节点 `temperature`：默认 0.7、0.0–1.0）。
     */
    data class AiParams(
        val reasoningEffort: String? = null,
        val temperature: Double = DEFAULT_TEMPERATURE,
    ) {
        companion object {
            const val DEFAULT_TEMPERATURE = 0.7

            fun of(reasoningEffort: String, temperature: Double = DEFAULT_TEMPERATURE): AiParams =
                AiParams(
                    reasoningEffort = reasoningEffort.takeIf { it != "off" },
                    temperature = temperature,
                )
        }

        fun applyTo(payload: JSONObject) {
            payload.put("temperature", temperature)
            reasoningEffort?.let { payload.put("reasoning_effort", it) }
        }
    }

    /** 一轮历史对话（多轮记忆用）。 */
    data class Turn(val user: String, val assistant: String)

    /** 工具声明（节点 `tools` 输入里的函数定义）。 */
    data class ToolSpec(val name: String, val description: String, val parametersJson: String)

    /** 模型要求调用某个工具（`tool_calls` 里的一项）。 */
    data class ToolCall(val id: String, val name: String, val arguments: String)

    /** 一次调用的结果。 */
    data class ChatResult(
        val content: String,
        val reasoning: String = "",
        val toolCalls: List<ToolCall> = emptyList(),
    )

    /** 一次请求的全部输入 —— **字段与节点输入口一一对应**（括号里是节点上的名字）。 */
    data class ChatRequest(
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        /** 节点 `system_prompt`。 */
        val systemPrompt: String,
        /** 节点 `system_prompt_input`（**拼在** system_prompt 后面，源码 `1603`）。 */
        val systemPromptInput: String = "",
        /** 节点 `user_prompt`。 */
        val userPrompt: String,
        /** 已按轮数裁好的历史（节点 `history` 的窗口部分）。 */
        val history: List<Turn> = emptyList(),
        /** 节点 `user_history`：一段 JSON，非空时**直接覆盖**历史（源码 `1668-1672`）。 */
        val userHistoryJson: String? = null,
        /** 节点 `file_content`：拼到 system 末尾的"已知信息"。 */
        val fileContent: String? = null,
        /** 节点 `tools`。 */
        val tools: List<ToolSpec>? = null,
        /** 节点 `is_tools_in_sys_prompt`：把工具说明写进 system prompt。 */
        val toolsInSysPrompt: Boolean = false,
        /** 节点 `images`（base64 data URL 那条路）。 */
        val imageDataUrl: String? = null,
        /** 节点 `img_URL`（直接给 URL 那条路）。 */
        val imageUrl: String? = null,
        val params: AiParams? = null,
        /** 节点 `extra_parameters`：原样合并进请求体。 */
        val extra: JSONObject? = null,
        /** 节点 `stream`。 */
        val stream: Boolean = false,
        /** **SillyTavern 预设**（已解析；null = 不用）。注入规则见 [messages]。 */
        val stPreset: StPreset.Preset? = null,
        /** 宏展开用的"人名"（`{{user}}` / `{{char}}`）。 */
        val macroNames: StPreset.MacroNames = StPreset.MacroNames(),
    )

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build()

    // -----------------------------------------------------------------------
    // 对话格式（纯函数，可单测）—— 与源码逐条对齐
    // -----------------------------------------------------------------------

    /** 工具写进 system prompt 的示例 —— **原样取自源码**（`llm.py:1720-1735`）。 */
    internal val toolExamplePrompt: String = """
        You will receive a JSON string containing a list of callable tools. Please parse this JSON string and return a JSON object containing the tool name and tool parameters. Here is an example of the tool list:

        {"tools": [{"name": "plus_one", "description": "Add one to a number", "parameters": {"type": "object","properties": {"number": {"type": "string","description": "The number that needs to be changed, for example: 1","default": "1",}},"required": ["number"]}},{"name": "minus_one", "description": "Minus one to a number", "parameters": {"type": "object","properties": {"number": {"type": "string","description": "The number that needs to be changed, for example: 1","default": "1",}},"required": ["number"]}}]}

        Based on this tool list, generate a JSON object to call a tool. For example, if you need to add one to number 77, return:

        {"tool": "plus_one", "parameters": {"number": "77"}}

        Please note that the above is just an example and does not mean that the plus_one and minus_one tools are currently available.
    """.trimIndent()

    /** o1/o2/o3 系列把 system 换成 user 之后补的那句 assistant 确认（源码 `587`）。 */
    internal val oSeriesSystemAck = "好的，我会按照你的指示来操作"

    /**
     * 拼 `messages` —— **"对话格式"的唯一实现处**，逐条照源码：
     *
     * 1. 先放 system（`history[0]`），内容 = `system_prompt + system_prompt_input`；
     * 2. `is_tools_in_sys_prompt` 打开 → 工具示例 + 指令 `+=` 到 system；
     * 3. `file_content` → 追加"以下是可以参考的已知信息"；
     * 4. **空的 system 整条剔除**（源码 `577-581`）；
     * 5. `o1/o2/o3` → system 的 role 换成 `user`，再补一条 assistant 确认（源码 `582-588`）；
     * 6. 历史对话（user/assistant 交替）；
     * 7. 本轮 user（带图时是 `[{type:text},{type:image_url}]` 数组）。
     */
    internal fun messages(req: ChatRequest): JSONArray {
        val historyTurns: List<Turn> = req.userHistoryJson?.takeIf { it.isNotBlank() }?.let { json ->
            runCatching { parseHistoryJson(json) }.getOrElse { req.history }
        } ?: req.history

        // ---- SillyTavern 预设（口径见 StPreset 类注释；⚠️ 两个 position 的方向别搞反）----
        val preset = req.stPreset
        // ⚠️ 这里**不能用 payloadEntries**：它把 marker 全过滤掉了，而 `chatHistory` marker 的**位置**
        // 决定了对话插在哪里（`openai.js:886-890`）。只保留 chatHistory 这个 marker，其余 marker 跳过。
        val entries = preset?.let { p ->
            StPreset.expandAll(
                p.entries.filter { entry ->
                    entry.identifier == "chatHistory" || (!entry.marker && entry.content.isNotBlank())
                },
                req.macroNames,
            ).first
        }.orEmpty()
        val absolute = entries.filter { it.position == StPreset.POSITION_ABSOLUTE }

        // 对话（历史 + 本轮 user）：**depth 只在这部分里数** ——
        // ST 就是把 chat 那串交给 populationInjectionPrompts()（openai.js:1334），装配进 chatHistory 位置。
        val chat = ArrayList<JSONObject>()
        historyTurns.forEach { turn ->
            chat.add(message("user", turn.user))
            chat.add(message("assistant", turn.assistant))
        }
        chat.add(userMessage(req))
        if (absolute.isNotEmpty()) injectAbsolute(chat, absolute)

        val out = ArrayList<JSONObject>()
        // 1) 我们自己的功能规则（⚠️ **ST 没有这一段**：它的请求内容完全由预设决定。见 docs/25 §53 的差异表）
        val system = systemContent(req)
        if (system.isNotEmpty()) {
            out.add(message(if (isOSeries(req.model)) "user" else "system", system))
            if (isOSeries(req.model)) out.add(message("assistant", oSeriesSystemAck))
        }
        // 2) 按 prompt_order 铺开：RELATIVE 条目各自成消息（尊重 role），
        //    chatHistory marker 处把对话插进去，其余 marker（世界书/角色卡/人设/示例）我们没有数据 → 跳过
        var chatPlaced = false
        entries.forEach { entry ->
            when {
                entry.position == StPreset.POSITION_ABSOLUTE -> Unit // 已经注进对话里了
                entry.marker ->
                    if (entry.identifier == "chatHistory") {
                        out.addAll(chat)
                        chatPlaced = true
                    }
                else -> out.add(message(entry.role, entry.content))
            }
        }
        if (!chatPlaced) out.addAll(chat) // 预设里没有 chatHistory（或压根没用预设）→ 照旧把对话接上

        // 3) squash_system_messages：相邻 system 合并、空 system 丢弃（openai.js:3919-3950；这份预设为 true）
        val finalMessages = if (preset?.squashSystemMessages == true) squashSystem(out) else out
        return JSONArray().apply { finalMessages.forEach { put(it) } }
    }

    /** 系统提示词那一段的内容（工具说明 / file_content / o 系列的角色处理都在这里）。 */
    private fun systemContent(req: ChatRequest): String {
        var content = req.systemPrompt + req.systemPromptInput
        if (req.toolsInSysPrompt) {
            val tools = req.tools.orEmpty()
            if (tools.isNotEmpty()) {
                val instructions = buildString {
                    tools.forEach { tool ->
                        append(tool.name).append(':')
                            .append("Call this tool to interact with the ").append(tool.name)
                            .append(" API. What is the ").append(tool.name)
                            .append(" API useful for? ").append(tool.description)
                            .append(". Parameters:").append(tool.parametersJson)
                            .append("Required parameters:")
                            .append(
                                runCatching {
                                    JSONObject(tool.parametersJson).optJSONArray("required")?.toString()
                                }.getOrNull() ?: "[]",
                            )
                            .append('\n')
                    }
                }
                val returnFormat =
                    """{"tool": "tool name", "parameters": {"parameter name": "parameter value"}}"""
                val instruction = """
        Answer the following questions as best you can. You have access to the following APIs:
        $instructions

        Use the following format:
        ```tool_json
        $returnFormat
        ```

        Please choose the appropriate tool according to the user's question. If you don't need to call it, please reply directly to the user's question. When the user communicates with you in a language other than English, you need to communicate with the user in the same language.

        When you have enough information from the tool results, respond directly to the user with a text message without having to call the tool again.
                    """.trimIndent()
                content += "\n" + toolExamplePrompt + "\n" + instruction + "\n"
            }
        }
        req.fileContent?.takeIf { it.isNotBlank() }?.let {
            content += "\n以下是可以参考的已知信息:\n" + it
        }
        return content
    }

    private fun message(role: String, content: String): JSONObject =
        JSONObject().apply {
            put("role", role)
            put("content", content)
        }

    /**
     * **ABSOLUTE 条目按 depth 插进对话** —— 口径照 `openai.js:820-870`：
     *  · depth 从**末尾**往前数（`messages.splice(i + totalInserted, 0, …)` 作用在反转后的数组上）；
     *  · 同一 depth 内先按 `injection_order` **降序**，再按 role `system → user → assistant` 分组，
     *    **同组合并成一条消息**（`\n` 连接）；
     *  · 已插入的条目让后续 depth 的落点继续往外挪（对应 ST 的 `totalInsertedMessages`）。
     */
    private fun injectAbsolute(chat: MutableList<JSONObject>, entries: List<StPreset.Entry>) {
        var inserted = 0
        entries.groupBy { it.depth.coerceAtLeast(0) }.toSortedMap().forEach { depth, group ->
            val batch = ArrayList<JSONObject>()
            group.groupBy { it.order }.entries.sortedByDescending { it.key }.forEach { sameOrder ->
                listOf("system", "user", "assistant").forEach { role ->
                    val text = sameOrder.value.filter { it.role == role }.joinToString("\n") { it.content }
                    if (text.isNotBlank()) batch.add(message(role, text))
                }
            }
            if (batch.isEmpty()) return@forEach
            val index = (chat.size - depth - inserted).coerceIn(0, chat.size)
            chat.addAll(index, batch)
            inserted += batch.size
        }
    }

    /** 相邻 system 合并 + 空 system 丢弃（`openai.js:3919-3950`，`squash_system_messages` 为 true 时用）。 */
    private fun squashSystem(messages: List<JSONObject>): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        var lastSystem: JSONObject? = null
        messages.forEach { current ->
            val isSystem = current.optString("role") == "system"
            val content = current.optText("content")
            if (isSystem && content.isEmpty()) return@forEach
            if (isSystem && lastSystem != null) {
                lastSystem!!.put("content", lastSystem!!.optText("content") + "\n" + content)
                return@forEach
            }
            out.add(current)
            lastSystem = if (isSystem) current else null
        }
        return out
    }

    /** `user_history` 的 JSON → 轮次（只认 user/assistant 成对）。 */
    internal fun parseHistoryJson(json: String): List<Turn> {
        val arr = JSONArray(json)
        val out = ArrayList<Turn>()
        var pendingUser: String? = null
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            when (m.optString("role")) {
                "user" -> pendingUser = m.optString("content")
                "assistant" -> {
                    out.add(Turn(pendingUser.orEmpty(), m.optText("content")))
                    pendingUser = null
                }
            }
        }
        if (pendingUser != null) out.add(Turn(pendingUser, ""))
        return out
    }

    /** 本轮 user 消息：纯文本，或带图时的 content 数组（源码 `539-576`）。 */
    private fun userMessage(req: ChatRequest): JSONObject {
        val url = req.imageUrl?.takeIf { it.isNotBlank() }
            ?: req.imageDataUrl?.takeIf { it.isNotBlank() }
        if (url == null) {
            return JSONObject().apply { put("role", "user"); put("content", req.userPrompt) }
        }
        return JSONObject().apply {
            put("role", "user")
            put(
                "content",
                JSONArray().apply {
                    put(JSONObject().apply { put("type", "text"); put("text", req.userPrompt) })
                    put(
                        JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply { put("url", url) })
                        },
                    )
                },
            )
        }
    }

    /** `o1/o2/o3` 系列（源码 `582`：`re.search(r'o[1-3]', model_name)`）。 */
    internal fun isOSeries(model: String): Boolean = Regex("o[1-3]").containsMatchIn(model)

    /** `gpt-5*` 把 `max_tokens` 换成 `max_completion_tokens`（源码 `413-417`）。 */
    internal fun normalizeOpenAiChatKwargs(payload: JSONObject): JSONObject {
        val model = payload.optString("model", "").trim().lowercase()
        if (model.startsWith("gpt-5") && !payload.has("max_completion_tokens") && payload.has("max_tokens")) {
            payload.put("max_completion_tokens", payload.remove("max_tokens"))
        }
        return payload
    }

    /**
     * 拼端点 —— 照源码 `ensure_version_suffix`（`526-536`）：
     *  · Perplexity 固定 `https://api.perplexity.ai`；
     *  · 已经是完整 `/chat/completions` 的原样用（我们自己加的兼容，源码没有这一支）；
     *  · 否则没有 `/v{n}/` 就补 `/v1/`，最后接 `chat/completions`。
     */
    fun endpoint(baseUrl: String): String {
        val base = baseUrl.trim()
        if (base.contains("api.perplexity.ai")) return "https://api.perplexity.ai/chat/completions"
        val trimmed = base.trimEnd('/')
        if (trimmed.endsWith("/chat/completions")) return trimmed
        var out = base.trimEnd('/')
        if (!Regex("/v[^/]+/?$").containsMatchIn(out)) out += "/v1"
        return "$out/chat/completions"
    }

    // -----------------------------------------------------------------------
    // 请求
    // -----------------------------------------------------------------------

    /**
     * 发一次请求（**含工具调用循环与流式**）。
     *
     * @param toolDispatcher 模型要求调用工具时由调用方执行并返回给模型的内容；
     *   返回 null = 不认识这个工具（源码里是 `Tool not found. Please use a provided tool.`）。
     * @param onDelta 流式增量回调（用于把回复实时显示出来）。
     */
    suspend fun send(
        req: ChatRequest,
        toolDispatcher: (suspend (ToolCall) -> String?)? = null,
        onDelta: ((String) -> Unit)? = null,
    ): ChatResult = withContext(Dispatchers.IO) {
        var history = req.history
        var userHistoryJson = req.userHistoryJson
        var first = true
        var last = ChatResult("")
        var rounds = 0
        while (true) {
            val payload = buildPayload(
                req.copy(
                    history = history,
                    userHistoryJson = userHistoryJson,
                    userPrompt = if (first) req.userPrompt else "请根据上面的工具结果继续回答。",
                    imageDataUrl = if (first) req.imageDataUrl else null,
                    imageUrl = if (first) req.imageUrl else null,
                ),
            )
            val result = post(payload, endpoint(req.baseUrl), req.apiKey, req.stream, onDelta)
            last = result
            if (result.toolCalls.isEmpty() || toolDispatcher == null || rounds >= MAX_TOOL_ROUNDS) break
            rounds++
            first = false
            userHistoryJson = null
            history = history + Turn(user = req.userPrompt, assistant = result.content.ifBlank { "(tool call)" })
            val toolOutput = StringBuilder()
            result.toolCalls.forEach { call ->
                val value = toolDispatcher(call)
                    ?: "Tool `${call.name}` not found. Please use a provided tool."
                toolOutput.append(value).append('\n')
            }
            history = history + Turn(
                user = toolOutput.toString().trim(),
                assistant = "",
            )
        }
        last
    }

    /** 组包（**internal** 是为了让单测能直接验采样参数映射）。 */
    internal fun buildPayload(req: ChatRequest): JSONObject = JSONObject().apply {
        put("model", req.model.ifBlank { "gpt-4o-mini" })
        (req.params ?: AiParams()).applyTo(this)
        // SillyTavern 预设的采样参数：**预设优先**（导入它就是为了用它的采样）
        req.stPreset?.let { preset ->
            preset.temperature?.let { put("temperature", it) }
            preset.topP?.let { put("top_p", it) }
            preset.frequencyPenalty?.let { put("frequency_penalty", it) }
            preset.presencePenalty?.let { put("presence_penalty", it) }
            // 非 OpenAI 标准字段（top_k / min_p / repetition_penalty…）：照发，服务商认就用
            preset.extraSamplers.forEach { (key, value) -> if (!has(key)) put(key, value) }
        }
        // ⚠️ **这里原来会发 `max_tokens`**（`req.maxTokens ?: preset.maxTokens`；真机上是 1920）。
        // 用户 2026-09-18：「把输出上限去除」—— 分镜那种长 JSON 老被它切在字符串中间，
        // 而且 ST 预设里的 `openai_max_tokens`（9500）因为 `req.maxTokens` 非空**从来没生效过**。
        // 现在**一个 `max_tokens` 都不发**，长度交给服务商自己的默认值；要自己限长度就在
        // 「额外请求参数」里填 `max_tokens`（下面的 `extra` 最后合并，仍能覆盖）。
        req.extra?.let { obj -> obj.keys().forEach { key -> put(key, obj.get(key)) } }
        put("messages", messages(req))
        if (req.stream) put("stream", true)
        // 原生工具调用与"写进 system prompt"二选一（源码同样二选一）
        if (!req.toolsInSysPrompt) {
            req.tools?.takeIf { it.isNotEmpty() }?.let { tools ->
                put(
                    "tools",
                    JSONArray().apply {
                        tools.forEach { tool ->
                            put(
                                JSONObject().apply {
                                    put("type", "function")
                                    put(
                                        "function",
                                        JSONObject().apply {
                                            put("name", tool.name)
                                            put("description", tool.description)
                                            put("parameters", JSONObject(tool.parametersJson))
                                        },
                                    )
                                },
                            )
                        }
                    },
                )
            }
        }
        normalizeOpenAiChatKwargs(this)
    }

    /** 发请求：流式（SSE）或一次性。 */
    private fun post(
        payload: JSONObject,
        url: String,
        apiKey: String,
        stream: Boolean,
        onDelta: ((String) -> Unit)?,
    ): ChatResult {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Content-Type", "application/json")
            .header("Accept", if (stream) "text/event-stream" else "application/json")
            .post(payload.toString().toRequestBody(jsonMedia))
            .build()
        if (stream) return postStreaming(request, onDelta)
        val text: String
        val code: Int
        try {
            http.newCall(request).execute().use { response ->
                code = response.code
                text = response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw LlmException("连不上 LLM 服务：${e.message ?: "网络错误"}")
        }
        if (code !in 200..299) {
            val detail = runCatching {
                JSONObject(text).optJSONObject("error")?.optString("message")
            }.getOrNull().orEmpty()
            throw LlmException("LLM 返回 $code${if (detail.isNotEmpty()) "：$detail" else ""}")
        }
        val root = runCatching { JSONObject(text) }.getOrNull()
            ?: throw LlmException("LLM 返回的不是 JSON（检查 base_url 是否填对）")
        val result = parseCompletion(root)
        if (result.content.isBlank() && result.toolCalls.isEmpty()) {
            throw LlmException("LLM 没有返回内容（检查模型名是否正确）")
        }
        return ChatResult(
            content = sanitizeModelText(result.content),
            reasoning = result.reasoning,
            toolCalls = result.toolCalls,
        )
    }

    /**
     * 解析一次性响应（**internal** 是为了让单测能直接验 `"content": null` 这类畸形返回）。
     */
    internal fun parseCompletion(root: JSONObject): ChatResult {
        val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            ?: return ChatResult("")
        return ChatResult(
            content = message.optText("content"),
            reasoning = message.optText("reasoning_content").ifBlank { message.optText("reasoning") },
            toolCalls = parseToolCalls(message.optJSONArray("tool_calls")),
        )
    }

    /**
     * 读一段**可能是 JSON `null`** 的文本。
     *
     * ⚠️ 用户 2026-09-18 报"一直回 nullnull 循环后接正文"，根因就在这里：
     * `org.json` 的 `optString()` 遇到 `"content": null` **不返回空串**，而是 `JSONObject.NULL.toString()`
     * = 字面量 **"null"** ✗。流式分片里很多服务商都会发 `"content": null`（只带 role / reasoning 的那种），
     * 于是我们每片追加一个 "null" ✗；它一旦进了上下文，模型还会继续模仿 → "null 循环" ✗。
     * **所有读模型/预设文本的地方都走这里**。
     */
    private fun JSONObject.optText(key: String): String =
        if (!has(key) || isNull(key)) "" else optString(key)

    /**
     * 抹掉模型输出**开头 / 结尾**连续的 `null` / `undefined`（有些中转把 JSON null 序列化成字面量发过来）。
     * 只动边界那一串，正文里的正常单词不受影响。
     */
    internal fun sanitizeModelText(text: String): String {
        if (text.isEmpty()) return text
        var out = Regex("^\\s*(?:(?:null|undefined)\\s*)+", RegexOption.IGNORE_CASE).replace(text, "")
        out = Regex("(?:\\s*(?:null|undefined))+\\s*$", RegexOption.IGNORE_CASE).replace(out, "")
        return out.trim()
    }

    /**
     * 从流式分片里取正文 —— **internal** 是为了让单测钉住"`"content": null` 必须得到空串，而不是字面量 null"**
     * （用户 2026-09-18 报的"一直回 nullnull"就是这个坑）。
     */
    internal fun deltaContent(delta: JSONObject): String = delta.optText("content")

    /** SSE 流式：拼接 `delta.content` 与 `delta.tool_calls[].function.arguments`（源码同款）。 */
    private fun postStreaming(request: Request, onDelta: ((String) -> Unit)?): ChatResult {
        val content = StringBuilder()
        val reasoning = StringBuilder()
        val arguments = LinkedHashMap<Int, StringBuilder>()
        val names = LinkedHashMap<Int, String>()
        val ids = LinkedHashMap<Int, String>()
        try {
            http.newCall(request).execute().use { response ->
                if (response.code !in 200..299) {
                    val body = response.body?.string().orEmpty()
                    throw LlmException("LLM 返回 ${response.code}：${body.take(200)}")
                }
                val source = response.body?.source() ?: throw LlmException("LLM 没有返回内容")
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val chunk = runCatching { JSONObject(data) }.getOrNull() ?: continue
                    val delta = chunk.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                        ?: continue
                    // ⚠️ 必须用 optText：`"content": null` 用 optString 会拿到字面量 "null"（就是 null 循环的根源）
                    deltaContent(delta).takeIf { it.isNotEmpty() }?.let {
                        content.append(it)
                        onDelta?.invoke(it)
                    }
                    delta.optText("reasoning_content").takeIf { it.isNotEmpty() }?.let {
                        reasoning.append(it)
                    }
                    delta.optJSONArray("tool_calls")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val one = arr.optJSONObject(i) ?: continue
                            val index = one.optInt("index", i)
                            one.optText("id").takeIf { it.isNotEmpty() }?.let { ids[index] = it }
                            val fn = one.optJSONObject("function") ?: continue
                            fn.optText("name").takeIf { it.isNotEmpty() }?.let { names[index] = it }
                            fn.optText("arguments").takeIf { it.isNotEmpty() }?.let {
                                arguments.getOrPut(index) { StringBuilder() }.append(it)
                            }
                        }
                    }
                }
            }
        } catch (e: LlmException) {
            throw e
        } catch (e: IOException) {
            throw LlmException("连不上 LLM 服务：${e.message ?: "网络错误"}")
        }
        val calls = arguments.entries.sortedBy { it.key }.map { (index, args) ->
            ToolCall(ids[index] ?: "call_$index", names[index].orEmpty(), args.toString())
        }
        if (content.isEmpty() && calls.isEmpty()) {
            throw LlmException("LLM 没有返回内容（检查模型名是否正确）")
        }
        return ChatResult(sanitizeModelText(content.toString()), reasoning.toString(), calls)
    }

    private fun parseToolCalls(arr: JSONArray?): List<ToolCall> {
        if (arr == null) return emptyList()
        val out = ArrayList<ToolCall>()
        for (i in 0 until arr.length()) {
            val one = arr.optJSONObject(i) ?: continue
            val fn = one.optJSONObject("function") ?: continue
            out.add(
                ToolCall(
                    id = one.optText("id").ifBlank { "call_$i" },
                    name = fn.optText("name"),
                    arguments = fn.optText("arguments"),
                ),
            )
        }
        return out
    }

    /**
     * 上传到 ImgBB 取 URL（节点 `imgbb_api_key` 那条路：有 key 用 URL，没有用 base64）。
     *
     * @param pngBase64 纯 base64（**不带** `data:image/png;base64,` 前缀）
     */
    suspend fun uploadToImgbb(apiKey: String, pngBase64: String): String = withContext(Dispatchers.IO) {
        val body = okhttp3.FormBody.Builder()
            .add("key", apiKey.trim())
            .add("image", pngBase64)
            .build()
        val request = Request.Builder()
            .url("https://api.imgbb.com/1/upload")
            .post(body)
            .build()
        val text: String
        try {
            http.newCall(request).execute().use { response ->
                text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw LlmException("图床上传失败：${response.code}")
            }
        } catch (e: LlmException) {
            throw e
        } catch (e: IOException) {
            throw LlmException("图床上传失败：${e.message ?: "网络错误"}")
        }
        val url = runCatching {
            JSONObject(text).optJSONObject("data")?.optString("url")
        }.getOrNull().orEmpty()
        if (url.isBlank()) throw LlmException("图床没有返回图片地址")
        url
    }

    companion object {
        /** 工具调用最多来回几轮（防打转）。 */
        const val MAX_TOOL_ROUNDS = 8

        /** 判断一段文字是不是"含中日韩字符"（决定翻译方向）。 */
        fun looksChinese(text: String): Boolean =
            text.any { it.code in 0x4E00..0x9FFF || it.code in 0x3040..0x30FF }
    }
}
