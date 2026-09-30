package com.kallan.naistudio.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LLM 客户端的纯逻辑 + **提示词规则**（原样取自 ComfyUI「提示词小助手」插件）。
 */
class LlmApiTest {

    private val api = LlmApi()

    // 尖括号一律用 \u003C / \u003E 转义写：源码里不出现字面 "<tag>"，
    // 免得写作工具/编辑器把这种片段当成标记把尖括号吃掉（真踩过：`"<thinking>…"` 的 `<` 变成空格）。
    private val openThink = "\u003Cthinking\u003E"
    private val closeThink = "\u003C/thinking\u003E"

    @Test
    fun `appends chat completions to a base url`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            api.endpoint("https://api.openai.com/v1"),
        )
        assertEquals(
            "https://api.deepseek.com/v1/chat/completions",
            api.endpoint("https://api.deepseek.com/v1/"),
        )
    }

    @Test
    fun `keeps a full endpoint untouched`() {
        val full = "https://example.com/v1/chat/completions"
        assertEquals(full, api.endpoint(full))
        assertEquals(full, api.endpoint("$full/"))
    }

    @Test
    fun `trims surrounding spaces`() {
        assertEquals("https://x.y/v1/chat/completions", api.endpoint("  https://x.y/v1  "))
    }

    @Test
    fun `detects chinese text`() {
        assertTrue(LlmApi.looksChinese("一个女孩在花园里"))
        assertTrue(LlmApi.looksChinese("1girl, 花园"))
        assertTrue(LlmApi.looksChinese("花"))
        assertFalse(LlmApi.looksChinese("1girl, solo, blue hair, garden"))
        assertFalse(LlmApi.looksChinese(""))
    }

    // ---------------- 提示词规则（插件原文） ----------------

    @Test
    fun `translate rules keep the placeholders and the six commands`() {
        // 原样塞入：六个"最高指令"都在（取自「提示词小助手」插件的 translate_prompts.ZH）
        assertTrue(PromptRules.TRANSLATE_RULES.contains("最高指令 (Absolute Command)"))
        assertTrue(PromptRules.TRANSLATE_RULES.contains("风格镜像"))
        assertTrue(PromptRules.TRANSLATE_RULES.contains("符号保护"))
        assertTrue(PromptRules.TRANSLATE_RULES.contains("专有名词锁定"))
        assertTrue(PromptRules.TRANSLATE_RULES.contains("(word:1.2), [word], ((word)), {word}"))
        assertTrue(PromptRules.TRANSLATE_RULES.contains("严禁使用中文全角标点"))
        assertTrue(PromptRules.TRANSLATE_RULES.contains("纯净输出"))
        assertTrue(PromptRules.TRANSLATE_RULES.contains("Taylor Swift:1.2"))
        assertTrue(PromptRules.TRANSLATE_RULES.contains("LoRA, VAE, ControlNet"))
    }

    @Test
    fun `translate rules fill the language placeholders`() {
        val filled = PromptRules.translateRules("简体中文", "英文")
        assertTrue(filled.contains("简体中文"))
        assertTrue(filled.contains("英文"))
        assertFalse(filled.contains("{src_lang}"))
        assertFalse(filled.contains("{dst_lang}"))
    }

    @Test
    fun `optimize and reverse defaults are the plugin texts`() {
        assertTrue(PromptRules.DEFAULT_OPTIMIZE_RULES.contains("Danbooru 标签体系"))
        assertTrue(PromptRules.DEFAULT_OPTIMIZE_RULES.contains("标签化输出"))
        assertTrue(PromptRules.DEFAULT_OPTIMIZE_RULES.contains("(subject:1.2)"))

        assertTrue(PromptRules.DEFAULT_REVERSE_RULES.contains("视觉分析专家"))
        assertTrue(PromptRules.DEFAULT_REVERSE_RULES.contains("强制标签化"))
        assertTrue(PromptRules.DEFAULT_REVERSE_RULES.contains("像素级挖掘"))
    }

    @Test
    fun `cleans reasoning blocks out of model output`() {
        // 成对的思考块
        assertEquals(
            "1girl, solo",
            PromptRules.cleanModelOutput(openThink + "用户在问什么……" + closeThink + "1girl, solo"),
        )
        // 没闭合的思考块（响应被截断）
        assertEquals("1girl", PromptRules.cleanModelOutput("1girl" + openThink + "这里开始乱说"))
        // 首尾引号与空白
        assertEquals("1girl, solo", PromptRules.cleanModelOutput("  \"1girl, solo\"  "))
        // 正常输出原样保留
        assertEquals("1girl, solo, red dress", PromptRules.cleanModelOutput("1girl, solo, red dress"))
    }
}
