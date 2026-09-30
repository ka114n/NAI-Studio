package com.kallan.naistudio.models

import com.kallan.naistudio.services.LlmApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **JSON `null` 不能变成字符串 "null"**（2026-09-18，用户报"一直回 nullnull 循环后接正文"）。
 *
 * 根因：`org.json` 的 `optString()` 遇到 `"content": null` 返回 `JSONObject.NULL.toString()` = 字面量 **"null"** ✗。
 * 两条泄漏路径：
 *  ① 流式分片里服务商常发 `"content": null`（只带 role / reasoning 的那种）→ 每片追加一个 "null" ✗；
 *  ② ST 预设里 `content: null` 的条目被读成 "null" 当正文注入提示词 ✗。
 * 一旦 "null" 进了上下文，模型还会继续模仿 → 就成了"null 循环" ✗。
 */
class LlmNullHandlingTest {

    private val api = LlmApi()

    @Test
    fun `streaming delta with content null yields an empty string`() {
        val delta = JSONObject("""{"role":"assistant","content":null}""")
        assertEquals("", api.deltaContent(delta))
        assertTrue("绝不能是字面量 null", api.deltaContent(delta) != "null")
    }

    @Test
    fun `streaming delta with real content is untouched`() {
        assertEquals("正文", api.deltaContent(JSONObject("""{"content":"正文"}""")))
    }

    @Test
    fun `non-streaming message with content null keeps only the reasoning`() {
        val root = JSONObject(
            """{"choices":[{"message":{"content":null,"reasoning_content":"想一下"}}]}""",
        )
        val result = api.parseCompletion(root)
        assertEquals("", result.content)
        assertEquals("想一下", result.reasoning)
    }

    @Test
    fun `tool call arguments that are null do not become the string null`() {
        val root = JSONObject(
            """{"choices":[{"message":{"content":"","tool_calls":[
                 {"id":null,"function":{"name":"another_llm","arguments":null}}]}}]}""",
        )
        val call = api.parseCompletion(root).toolCalls.single()
        assertEquals("another_llm", call.name)
        assertEquals("", call.arguments)
        assertTrue(call.id.isNotBlank())
    }

    @Test
    fun `sanitizer strips leading and trailing null runs`() {
        assertEquals("正文", api.sanitizeModelText("nullnull正文"))
        assertEquals("正文", api.sanitizeModelText("null null\n正文"))
        assertEquals("正文", api.sanitizeModelText("正文\nnull"))
        assertEquals("", api.sanitizeModelText("null"))
        assertEquals("正文", api.sanitizeModelText("正文"))
    }

    @Test
    fun `sanitizer keeps null in the middle of real text`() {
        // 正文中间提到 null（比如讨论 JSON）不该被吃掉
        assertEquals("字段是 null 的意思", api.sanitizeModelText("字段是 null 的意思"))
    }

    @Test
    fun `preset entry with content null is treated as empty`() {
        val json = """
            {"prompts":[
              {"identifier":"a","name":"A","role":"system","content":null},
              {"identifier":"b","name":"B","role":"system","content":"真内容"}
            ],"prompt_order":[{"character_id":100000,"order":[{"identifier":"a"},{"identifier":"b"}]}]}
        """.trimIndent()
        val preset = StPreset.parse(json)!!
        assertEquals("", preset.entries.first { it.identifier == "a" }.content)
        assertEquals("空内容不进 payload", listOf("b"), preset.payloadEntries.map { it.identifier })
    }

    @Test
    fun `preset entry with null name falls back to the identifier`() {
        val json = """
            {"prompts":[{"identifier":"c","name":null,"role":null,"content":"x"}],
             "prompt_order":[{"character_id":100000,"order":[{"identifier":"c"}]}]}
        """.trimIndent()
        val entry = StPreset.parse(json)!!.entries.single()
        assertEquals("c", entry.name)
        assertEquals("system", entry.role)
    }
}
