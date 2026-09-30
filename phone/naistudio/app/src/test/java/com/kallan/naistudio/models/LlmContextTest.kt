package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **导入上下文**的单测 —— 对应 ComfyUI「API LLM通用链路」节点的 `user_history` / `historical_record` 两个口：
 * 认三种格式（App 记忆文件 / OpenAI 消息数组 / 带壳的 history），并能在两种形状之间来回转。
 */
class LlmContextTest {

    @Test
    fun `openai message array becomes turns`() {
        val json = """
            [
              {"role":"system","content":"你是助手"},
              {"role":"user","content":"第一问"},
              {"role":"assistant","content":"第一答"},
              {"role":"user","content":"第二问"},
              {"role":"assistant","content":"第二答"}
            ]
        """.trimIndent()
        val turns = LlmContext.parse(json)
        assertEquals(2, turns.size)
        assertEquals("第一问", turns[0].user)
        assertEquals("第一答", turns[0].assistant)
        assertEquals("第二答", turns[1].assistant)
    }

    @Test
    fun `app memory file becomes turns (all purposes, in file order)`() {
        val json = """
            {
              "storyboard": [ {"user":"分镜问","assistant":"分镜答"} ],
              "optimize":   [ {"user":"优化问","assistant":"优化答"} ]
            }
        """.trimIndent()
        val turns = LlmContext.parse(json)
        assertEquals(2, turns.size)
        // 键顺序不保证 → 断言"内容都在"，不断言谁在前（代码里已按 key 排序保证可重复）
        val users = turns.map { it.user }.toSet()
        assertTrue(users.contains("分镜问"))
        assertTrue(users.contains("优化问"))
        val answers = turns.map { it.assistant }.toSet()
        assertTrue(answers.contains("分镜答"))
        assertTrue(answers.contains("优化答"))
    }

    @Test
    fun `shelled history array is recognised`() {
        val json = """{"history":[{"role":"user","content":"问"},{"role":"assistant","content":"答"}]}"""
        val turns = LlmContext.parse(json)
        assertEquals(1, turns.size)
        assertEquals("问", turns.single().user)
    }

    @Test
    fun `content parts only take the text`() {
        val json = """
            [
              {"role":"user","content":[{"type":"text","text":"看图"},{"type":"image_url","image_url":{"url":"data:image/png;base64,AAA"}}]},
              {"role":"assistant","content":"好"}
            ]
        """.trimIndent()
        val turns = LlmContext.parse(json)
        assertEquals("看图", turns.single().user)
        assertEquals("好", turns.single().assistant)
    }

    @Test
    fun `junk in becomes nothing out`() {
        assertTrue(LlmContext.parse("").isEmpty())
        assertTrue(LlmContext.parse("不是 JSON").isEmpty())
        assertTrue(LlmContext.parse("""{"a":1}""").isEmpty())
    }

    @Test
    fun `round trip through user_history json`() {
        val turns = listOf(LlmTurn("问一", "答一"), LlmTurn("问二", "答二"))
        val json = LlmContext.toUserHistoryJson(turns)
        // 发出去时就是 OpenAI 消息数组的形状
        assertTrue(json.startsWith("["))
        assertTrue(json.contains("\"role\":\"user\""))
        assertTrue(json.contains("\"role\":\"assistant\""))
        // 再认回来应当一模一样
        assertEquals(turns, LlmContext.parse(json))
    }

    @Test
    fun `round trip through memory json`() {
        val memory = mapOf(
            LlmMemoryKind.STORYBOARD to listOf(LlmTurn("问", "答")),
            LlmMemoryKind.REVERSE to listOf(LlmTurn("反推问", "反推答")),
        )
        val json = LlmContext.toMemoryJson(memory)
        assertTrue(json.contains("storyboard"))
        val turns = LlmContext.parse(json)
        assertEquals(2, turns.size)
        val answers = turns.map { it.assistant }.toSet()
        assertTrue(answers.contains("答"))
        assertTrue(answers.contains("反推答"))
        // 排序后 reverse 在前 storyboard 在后 → 结果可重复
        assertEquals(listOf("反推答", "答"), turns.map { it.assistant })
    }

    @Test
    fun `only a trailing user message is still a turn`() {
        val turns = LlmContext.parse("""[{"role":"user","content":"只有问"}]""")
        assertEquals(1, turns.size)
        assertEquals("只有问", turns.single().user)
        assertEquals("", turns.single().assistant)
    }

    /** `toMemoryJson` ↔ `fromMemoryJson`：记忆落盘/读回（用户 2026-09-18：重启不要重置记忆）。 */
    @Test
    fun `记忆文件形状能按用途分桶读回来`() {
        val memory = mapOf(
            "storyboard" to listOf(LlmTurn("u1", "a1"), LlmTurn("u2", "a2")),
            "translate" to listOf(LlmTurn("t1", "ta1")),
        )
        val back = LlmContext.fromMemoryJson(LlmContext.toMemoryJson(memory))
        assertEquals(setOf("storyboard", "translate"), back.keys)
        assertEquals(2, back["storyboard"]!!.size)
        assertEquals(LlmTurn("u1", "a1"), back["storyboard"]!![0])
        assertEquals(LlmTurn("u2", "a2"), back["storyboard"]!![1])
        assertEquals(1, back["translate"]!!.size)
        // 空串 / 坏文本 / 空对象 → 空表（启动读不动就当没记忆，绝不能崩）
        assertTrue(LlmContext.fromMemoryJson("").isEmpty())
        assertTrue(LlmContext.fromMemoryJson("{ 不是 JSON").isEmpty())
        assertTrue(LlmContext.fromMemoryJson("{}").isEmpty())
    }
}
