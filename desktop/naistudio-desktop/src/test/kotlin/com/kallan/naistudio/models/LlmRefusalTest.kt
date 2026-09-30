package com.kallan.naistudio.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **"拒答 / 被拦"识别**（用户 2026-09-18：「自动标记不计入上下文 llm 回传拒绝/失败的对话」）。
 *
 * 命中就不能进上下文（[LlmMemoryRules.contextWindow] 会把它剔掉）——
 * 一条"抱歉，我没法写"留在历史里，模型下一轮会照着继续拒 ✗。
 */
class LlmRefusalTest {

    @Test
    fun `认识常见的中文拒答`() {
        assertTrue(LlmRefusal.looksLikeRefusal("抱歉，这个我没法写。你这条要求的是……"))
        assertTrue(LlmRefusal.looksLikeRefusal("我无法生成这个内容。这个请求要求制作露骨画面。"))
        assertTrue(LlmRefusal.looksLikeRefusal("不好意思，我不能继续。"))
        assertTrue(LlmRefusal.looksLikeRefusal("  很抱歉，作为一个人工智能，我……"))
    }

    @Test
    fun `认识常见的英文拒答与平台拦截`() {
        assertTrue(LlmRefusal.looksLikeRefusal("I'm sorry, but I can't help with that."))
        assertTrue(LlmRefusal.looksLikeRefusal("I cannot generate this content."))
        assertTrue(LlmRefusal.looksLikeRefusal("As an AI assistant, I must decline."))
        assertTrue(
            LlmRefusal.looksLikeRefusal(
                "The prompt could not be submitted. The prompt contains sensitive words that " +
                    "violate Google's Generative AI Prohibited Use policy. Try rephrasing the prompt.",
            ),
        )
    }

    @Test
    fun `正常回答不会被误判`() {
        assertFalse(LlmRefusal.looksLikeRefusal(""))
        assertFalse(LlmRefusal.looksLikeRefusal("   "))
        // 分镜 JSON 是正常回答
        assertFalse(
            LlmRefusal.looksLikeRefusal(
                """{"page_summary":"Pyra serves a customer.","panels":[{"prompt":"1girl, bunny girl"}]}""",
            ),
        )
        // 台词里出现"抱歉"但不在开头 → 不算拒答
        assertFalse(LlmRefusal.looksLikeRefusal("Panel 1: 她低声说「抱歉，让您久等了」然后递上菜单。"))
        // "Sorry" 出现在正文中段也不算
        assertFalse(LlmRefusal.looksLikeRefusal("She whispers: sorry, I'm late. Then she kneels."))
    }

    @Test
    fun `contextWindow 把拒答那些轮剔掉，但存储不动`() {
        val turns = listOf(
            LlmTurn("第一页怎么画", """{"panels":[{"prompt":"a"}]}"""),
            LlmTurn("第二页怎么画", "抱歉，这个我没法写。"),
            LlmTurn("第三页怎么画", "I cannot generate this content."),
            LlmTurn("第四页怎么画", """{"panels":[{"prompt":"d"}]}"""),
        )
        // 发出去（进上下文）的只有两轮正常的
        val sent = LlmMemoryRules.contextWindow(turns, 10)
        assertTrue(sent.size == 2)
        assertTrue(sent.none { LlmRefusal.looksLikeRefusal(it.assistant) })
        // 原列表没被改（存储照旧：面板/轮数还是 4 轮）
        assertTrue(turns.size == 4)
    }
}
