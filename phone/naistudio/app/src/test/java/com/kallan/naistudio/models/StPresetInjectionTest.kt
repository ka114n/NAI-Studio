package com.kallan.naistudio.models

import com.kallan.naistudio.services.LlmApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **SillyTavern 预设的注入**（2026-09-17；口径已按源码订正过一次 —— 见 [StPreset] 类注释）。
 *
 * 断言的就是这些源码行为：
 *  · `openai.js:1242-1257` / `:1198-1201`：**RELATIVE 进提示词序列**（按 order），ABSOLUTE 不进序列；
 *  · `openai.js:1334` + `:820-870`：**ABSOLUTE 按 depth 插进对话**（同 depth 同 order 按 role 合并）；
 *  · `openai.js:885-890`：`chatHistory` marker 处插入真实对话；
 *  · `openai.js:3919-3950`：`squash_system_messages` 合并相邻 system、丢弃空 system。
 */
class StPresetInjectionTest {

    private val api = LlmApi()

    private fun entry(
        id: String,
        content: String,
        role: String = "system",
        position: Int = StPreset.POSITION_RELATIVE,
        depth: Int = 4,
        order: Int = 100,
        marker: Boolean = false,
    ) = StPreset.Entry(
        identifier = id,
        name = id,
        role = role,
        content = content,
        enabled = true,
        position = position,
        depth = depth,
        order = order,
        marker = marker,
        core = false,
    )

    private fun preset(vararg entries: StPreset.Entry) = StPreset.Preset(
        name = "测试预设",
        temperature = 1.0,
        topP = 0.9,
        frequencyPenalty = 0.2,
        presencePenalty = 0.1,
        maxTokens = 9500,
        extraSamplers = mapOf("top_k" to 40.0, "repetition_penalty" to 1.05),
        entries = entries.toList(),
        allEntries = entries.toList(),
    )

    private fun request(
        stPreset: StPreset.Preset?,
        history: List<LlmApi.Turn> = listOf(
            LlmApi.Turn("u1", "a1"),
            LlmApi.Turn("u2", "a2"),
        ),
        system: String = "功能规则",
    ) = LlmApi.ChatRequest(
        baseUrl = "https://api.example.com",
        apiKey = "k",
        model = "gpt-4o-mini",
        systemPrompt = system,
        userPrompt = "now",
        history = history,
        stPreset = stPreset,
    )

    private fun roles(messages: org.json.JSONArray) =
        (0 until messages.length()).map { messages.getJSONObject(it).getString("role") }

    private fun contents(messages: org.json.JSONArray) =
        (0 until messages.length()).map { messages.getJSONObject(it).getString("content") }

    @Test
    fun `relative entries go into the prompt sequence, in prompt_order`() {
        // RELATIVE（0）= 进提示词序列；chatHistory marker 在中间 → 对话就插在那个位置
        val messages = api.messages(
            request(
                preset(
                    entry("e1", "第一段"),
                    entry("chatHistory", "", marker = true),
                    entry("e2", "尾巴", role = "assistant"),
                ),
            ),
        )
        assertEquals(
            listOf("system", "system", "user", "assistant", "user", "assistant", "user", "assistant"),
            roles(messages),
        )
        assertEquals(
            listOf("功能规则", "第一段", "u1", "a1", "u2", "a2", "now", "尾巴"),
            contents(messages),
        )
    }

    @Test
    fun `without a chatHistory marker the conversation is appended at the end`() {
        val messages = api.messages(request(preset(entry("e1", "第一段"))))
        assertEquals(
            listOf("功能规则", "第一段", "u1", "a1", "u2", "a2", "now"),
            contents(messages),
        )
    }

    @Test
    fun `absolute entries are injected into the chat at the given depth`() {
        // 对话 = u1 a1 u2 a2 now（5 条）；depth 4 → 从末尾数第 4 条之前 = 索引 1
        val messages = api.messages(
            request(preset(entry("abs", "深度注入", position = StPreset.POSITION_ABSOLUTE))),
        )
        assertEquals(
            listOf("system", "user", "system", "assistant", "user", "assistant", "user"),
            roles(messages),
        )
        assertEquals(listOf("功能规则", "u1", "深度注入", "a1", "u2", "a2", "now"), contents(messages))
    }

    @Test
    fun `absolute entries with the same depth and order merge into one message per role`() {
        val messages = api.messages(
            request(
                preset(
                    entry("s1", "系统甲", position = StPreset.POSITION_ABSOLUTE),
                    entry("s2", "系统乙", position = StPreset.POSITION_ABSOLUTE),
                    entry("u1e", "用户甲", role = "user", position = StPreset.POSITION_ABSOLUTE),
                ),
            ),
        )
        val texts = contents(messages)
        val merged = texts.indexOf("系统甲\n系统乙")
        assertTrue("同组 system 合并成一条", merged >= 0)
        assertEquals("用户甲", texts[merged + 1])
        assertEquals("system", roles(messages)[merged])
        assertEquals("user", roles(messages)[merged + 1])
    }

    @Test
    fun `absolute entries with a higher injection_order go first`() {
        val messages = api.messages(
            request(
                preset(
                    entry("low", "低优先", position = StPreset.POSITION_ABSOLUTE, order = 50),
                    entry("high", "高优先", position = StPreset.POSITION_ABSOLUTE, order = 200),
                ),
            ),
        )
        val texts = contents(messages)
        assertTrue(texts.indexOf("高优先") < texts.indexOf("低优先"))
    }

    @Test
    fun `deeper absolute entries stay relative to the original end`() {
        val messages = api.messages(
            request(
                preset(
                    entry("d2", "浅", position = StPreset.POSITION_ABSOLUTE, depth = 2),
                    entry("d4", "深", position = StPreset.POSITION_ABSOLUTE, depth = 4),
                ),
            ),
        )
        // 完整列表 = [system, u1, 深, a1, u2, 浅, a2, now]
        val texts = contents(messages)
        assertEquals("u1", texts[1])
        assertEquals("深", texts[2])
        assertEquals("浅", texts[5])
    }

    @Test
    fun `squash merges adjacent system messages and drops empty ones`() {
        val messages = api.messages(
            request(
                preset(
                    entry("e1", "第一段"),
                    entry("e2", "第二段"),
                    entry("empty", ""),
                    entry("chatHistory", "", marker = true),
                ).copy(squashSystemMessages = true),
            ),
        )
        // 我们自己的功能规则 + 预设的两段 system（相邻）→ 合并成一条；空 system 丢弃
        assertEquals("system", roles(messages)[0])
        assertEquals("功能规则\n第一段\n第二段", contents(messages)[0])
        assertEquals(listOf("system", "user", "assistant", "user", "assistant", "user"), roles(messages))
    }

    @Test
    fun `without squash the system messages stay separate`() {
        val messages = api.messages(
            request(
                preset(
                    entry("e1", "第一段"),
                    entry("e2", "第二段"),
                    entry("chatHistory", "", marker = true),
                ),
            ),
        )
        assertEquals(listOf("功能规则", "第一段", "第二段"), contents(messages).take(3))
    }

    @Test
    fun `no preset means the message list is unchanged`() {
        val messages = api.messages(request(null))
        assertEquals(listOf("system", "user", "assistant", "user", "assistant", "user"), roles(messages))
        assertEquals(listOf("功能规则", "u1", "a1", "u2", "a2", "now"), contents(messages))
    }

    @Test
    fun `markers other than chatHistory are skipped and macros are expanded`() {
        val messages = api.messages(
            request(
                preset(
                    entry("worldInfoBefore", "", marker = true),
                    entry("charDescription", "", marker = true),
                    entry("m", "你好 {{user}}"),
                ),
            ),
        )
        val all = contents(messages).joinToString("\n")
        assertTrue("宏已展开", all.contains("你好 用户"))
        assertTrue("展开后不应残留 {{", !all.contains("{{"))
        // 没有 chatHistory marker → 对话接在最后
        assertEquals("now", contents(messages).last())
    }

    @Test
    fun `preset sampler params win over the app temperature`() {
        val payload = api.buildPayload(request(preset(entry("x", "一"))))
        assertEquals(1.0, payload.getDouble("temperature"), 1e-9)
        assertEquals(0.9, payload.getDouble("top_p"), 1e-9)
        assertEquals(0.2, payload.getDouble("frequency_penalty"), 1e-9)
        assertEquals(0.1, payload.getDouble("presence_penalty"), 1e-9)
        // 用户 2026-09-18「把输出上限去除」：预设的 `openai_max_tokens`（9500）也不再发进请求
        assertFalse("请求里不该再有 max_tokens", payload.has("max_tokens"))
        assertEquals(40.0, payload.getDouble("top_k"), 1e-9)
        assertEquals(1.05, payload.getDouble("repetition_penalty"), 1e-9)
    }

    @Test
    fun `user extra parameters still override the preset`() {
        val req = request(preset(entry("x", "一"))).copy(
            extra = JSONObject().apply { put("temperature", 0.3) },
        )
        assertEquals(0.3, api.buildPayload(req).getDouble("temperature"), 1e-9)
    }
}
