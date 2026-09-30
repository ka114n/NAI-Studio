package com.kallan.naistudio.models

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * **导入上下文** —— 对应 ComfyUI「API LLM通用链路」节点上的两个输入口：
 *
 *  · `user_history`：直接给一段 JSON 当历史（[toUserHistoryJson] 产出的就是这个形状）；
 *  · `historical_record`：挑一份**以前存下来的历史**接着聊（我们这边就是 [toMemoryJson] 的产物，
 *    或 App 自己的 `llm_memory.json`）。
 *
 * 所以这里负责**认三种格式**，统一成对话轮次：
 *  ① 我们自己的记忆文件：`{ "storyboard": [ {"user": "…", "assistant": "…"} ], … }`
 *  ② OpenAI 消息数组：`[ {"role":"user","content":"…"}, {"role":"assistant","content":"…"} ]`
 *  ③ 带壳的消息数组：`{ "history": [...] }` / `{ "messages": [...] }`（节点导出的 history）
 *
 * 纯函数、不碰平台 API —— 两条线共用同一份，单测见 `LlmContextTest`。
 */
object LlmContext {

    /** 一次导入最多认多少轮（防手滑导入一个巨大文件把内存吃爆）。 */
    const val MAX_TURNS = 2000

    /** 单段文本上限（一轮 20 万字符足够任何正常对话；超了截断而不是整份丢掉）。 */
    const val MAX_TEXT_CHARS = 200_000

    /** 认格式 → 对话轮次；认不出来返回空。 */
    fun parse(text: String): List<LlmTurn> {
        val raw = text.trim()
        if (raw.isEmpty()) return emptyList()
        val json = runCatching { JSONTokener(raw).nextValue() }.getOrNull() ?: return emptyList()
        val turns = when (json) {
            is JSONArray -> parseMessages(json)
            is JSONObject -> {
                val shell = json.optJSONArray("history")
                    ?: json.optJSONArray("messages")
                    ?: json.optJSONArray("data")
                if (shell != null) parseMessages(shell) else parseMemoryFile(json)
            }
            else -> emptyList()
        }
        return turns.take(MAX_TURNS)
    }

    /**
     * ② / ③：OpenAI 消息数组。认 `role`+`content`，也认我们自己的 `user`+`assistant` 对象。
     * `content` 是数组（带图那种）时只取里面的文本片段。
     */
    private fun parseMessages(array: JSONArray): List<LlmTurn> {
        val out = ArrayList<LlmTurn>()
        var pendingUser: String? = null
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            // 我们自己的形状：{ user, assistant }
            if (item.has("user") || item.has("assistant")) {
                val user = textOf(item.opt("user"))
                val assistant = textOf(item.opt("assistant"))
                if (user.isNotEmpty() || assistant.isNotEmpty()) out.add(LlmTurn(user, assistant))
                continue
            }
            when (item.optString("role")) {
                "user" -> {
                    val text = textOf(item.opt("content"))
                    if (text.isNotEmpty()) pendingUser = text
                }
                "assistant" -> {
                    val text = textOf(item.opt("content"))
                    if (pendingUser != null || text.isNotEmpty()) {
                        out.add(LlmTurn(pendingUser.orEmpty(), text))
                        pendingUser = null
                    }
                }
                // system / tool 这些不参与"对话上下文"的搬运
            }
        }
        // 末尾只有一句 user（没有回复）也认
        if (pendingUser != null) out.add(LlmTurn(pendingUser, ""))
        return out
    }

    /**
     * ①：我们自己的记忆文件（用途键 → 轮次数组）。
     *
     * ⚠️ `org.json` 的 `JSONObject` 底层是 **HashMap**，键顺序**不保证** —— 所以这里显式**按 key 排序**，
     * 让"同一份文件每次导入得到的上下文完全一样"（多用途文件会被拼在一起，这是有意的：整份历史都能当参考）。
     */
    private fun parseMemoryFile(root: JSONObject): List<LlmTurn> {
        val out = ArrayList<LlmTurn>()
        val keys = ArrayList<String>()
        root.keys().forEach { keys.add(it) }
        keys.sort()
        keys.forEach { key ->
            val array = root.optJSONArray(key) ?: return@forEach
            out.addAll(parseMessages(array))
        }
        return out
    }

    /** 取一段文本：字符串直接用；数组（OpenAI 的 content parts）只取 `text` 片段；其它类型转字符串。 */
    private fun textOf(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is String -> value.take(MAX_TEXT_CHARS)
        is JSONArray -> buildString {
            for (i in 0 until value.length()) {
                val part = value.optJSONObject(i)
                val piece = part?.optString("text").orEmpty().ifBlank { part?.optString("content").orEmpty() }
                if (piece.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(piece)
                }
            }
        }.take(MAX_TEXT_CHARS)
        else -> value.toString().take(MAX_TEXT_CHARS)
    }

    /**
     * 轮次 → **`user_history` 形状**的 JSON（发请求时当历史用）。
     * 与 `LlmApi.messages()` 里解析的那个形状一致：`[{role:"user"|"assistant", content}]`。
     */
    fun toUserHistoryJson(turns: List<LlmTurn>): String {
        val array = JSONArray()
        turns.take(MAX_TURNS).forEach { turn ->
            if (turn.user.isNotBlank()) {
                array.put(JSONObject().apply { put("role", "user"); put("content", turn.user) })
            }
            if (turn.assistant.isNotBlank()) {
                array.put(JSONObject().apply { put("role", "assistant"); put("content", turn.assistant) })
            }
        }
        return array.toString()
    }

    /** 轮次 map → **记忆文件**形状的 JSON（导出用；也能再被 [parse] 认回来）。 */
    fun toMemoryJson(memory: Map<String, List<LlmTurn>>): String {
        val root = JSONObject()
        memory.forEach { (kind, turns) ->
            val array = JSONArray()
            turns.take(MAX_TURNS).forEach { turn ->
                array.put(
                    JSONObject().apply {
                        put("user", turn.user)
                        put("assistant", turn.assistant)
                    },
                )
            }
            root.put(kind, array)
        }
        return root.toString()
    }

    /**
     * **记忆文件形状 → 按用途分桶**（[toMemoryJson] 的逆运算；记忆落盘/读回用它）。
     *
     * 与 [parse] 的区别：`parse` 是"导入上下文"用的，会把所有用途**拼成一串**轮次；
     * 记忆是**按用途分桶**的，所以要单独一个函数把它拆开。
     * （用户 2026-09-18：「重启还会重置记忆吗，重置的话改成不重置」—— 记忆现在落盘。）
     */
    fun fromMemoryJson(text: String): Map<String, List<LlmTurn>> {
        val raw = text.trim()
        if (raw.isEmpty()) return emptyMap()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, List<LlmTurn>>()
        val keys = ArrayList<String>()
        root.keys().forEach { keys.add(it) }
        keys.sort()
        keys.forEach { key ->
            val array = root.optJSONArray(key) ?: return@forEach
            out[key] = parseMessages(array).take(MAX_TURNS)
        }
        return out
    }

    /** 一句人话的规模描述（界面/状态行用）。 */
    fun describe(turns: List<LlmTurn>): String = "${turns.size} 轮"
}
