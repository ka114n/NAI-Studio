package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分功能 AI 的**回落规则**（用户 2026-09-16 要求）。
 *
 * 需求一句话：「给每个需要用到 AI 的提示词框都配置单独的 AI，未填的默认使用正面提示词的 AI」。
 * 这句话里最容易写错的不是"单独配"，而是**"未填"的粒度** —— 是整组回落，还是逐字段回落。
 *
 * 这里把它钉成规范：**逐字段**。理由很实际 —— 最常见的用法是"只换个模型"
 * （图片反推要视觉模型、翻译想用便宜的小模型），地址和密钥还是同一家。
 * 整组回落的话用户得把地址和密钥再抄一遍，抄错一个字符就是一次莫名的 401。
 */
class LlmConfigTest {

    /** 主配置（= 正面提示词那一套），也是所有功能的兜底。 */
    private val base = AppSettings(
        llmApiUrl = "https://api.deepseek.com/v1",
        llmApiKey = "sk-main",
        llmModel = "deepseek-chat",
    )

    @Test
    fun `一项都不填时整套跟随正面提示词`() {
        val cfg = base.llmConfig()
        assertEquals("https://api.deepseek.com/v1", cfg.url)
        assertEquals("sk-main", cfg.key)
        assertEquals("deepseek-chat", cfg.model)
    }

    @Test
    fun `只填模型名时地址与密钥仍然跟随`() {
        // 这就是最常见的用法：同一家服务商换一个模型
        val cfg = base.llmConfig(model = "qwen-vl-max")
        assertEquals("https://api.deepseek.com/v1", cfg.url)
        assertEquals("sk-main", cfg.key)
        assertEquals("qwen-vl-max", cfg.model)
    }

    @Test
    fun `只填密钥时地址与模型仍然跟随`() {
        val cfg = base.llmConfig(key = "sk-other")
        assertEquals("https://api.deepseek.com/v1", cfg.url)
        assertEquals("sk-other", cfg.key)
        assertEquals("deepseek-chat", cfg.model)
    }

    @Test
    fun `三项都填时完全独立于主配置`() {
        val cfg = base.llmConfig(
            url = "https://api.moonshot.cn/v1",
            key = "sk-kimi",
            model = "kimi-k2",
        )
        assertEquals("https://api.moonshot.cn/v1", cfg.url)
        assertEquals("sk-kimi", cfg.key)
        assertEquals("kimi-k2", cfg.model)
    }

    @Test
    fun `空白的字段等同于未填而不是空字符串覆盖`() {
        // 用户在框里敲了几个空格就切走 —— 那不该把主配置的地址清掉
        val cfg = base.llmConfig(url = "   ", key = "\t", model = "  ")
        assertEquals("https://api.deepseek.com/v1", cfg.url)
        assertEquals("sk-main", cfg.key)
        assertEquals("deepseek-chat", cfg.model)
    }

    @Test
    fun `填了的值会去掉首尾空白`() {
        val cfg = base.llmConfig(url = " https://x.dev/v1 ", key = " sk-k ", model = " m ")
        assertEquals("https://x.dev/v1", cfg.url)
        assertEquals("sk-k", cfg.key)
        assertEquals("m", cfg.model)
    }

    @Test
    fun `地址与密钥都齐了才算配好`() {
        assertTrue(base.llmConfig().configured)
        // 主配置没填 → 只有地址是不够的（发请求必然 401）
        val blank = AppSettings(llmApiUrl = "https://x.dev/v1", llmApiKey = "", llmModel = "m")
        assertFalse(blank.llmConfig().configured)
        // 但单独配了密钥也算配好
        assertTrue(blank.llmConfig(key = "sk-x").configured)
    }

    // ------------------------------------------------------------ 各功能各取各的槽位

    @Test
    fun `五个功能各自读自己的槽位`() {
        val s = base.copy(
            llmTranslateModel = "translate-model",
            llmOptimizeModel = "optimize-model",
            llmStoryboardModel = "storyboard-model",
            llmPlanModel = "plan-model",
            llmReverseModel = "reverse-model",
        )
        assertEquals("translate-model", s.llmConfigForTranslate().model)
        assertEquals("optimize-model", s.llmConfigForOptimize().model)
        assertEquals("storyboard-model", s.llmConfigForStoryboard().model)
        assertEquals("plan-model", s.llmConfigForPlan().model)
        assertEquals("reverse-model", s.llmConfigForReverse().model)
        // 地址与密钥都还是主配置那套
        listOf(
            s.llmConfigForTranslate(),
            s.llmConfigForOptimize(),
            s.llmConfigForStoryboard(),
            s.llmConfigForPlan(),
            s.llmConfigForReverse(),
        ).forEach {
            assertEquals("https://api.deepseek.com/v1", it.url)
            assertEquals("sk-main", it.key)
        }
    }

    @Test
    fun `给分镜单独配一套不会顺带影响狂暴模式第一段`() {
        // 用户明确要求：狂暴模式的**逐页分镜**用「分镜」那套，
        // 但它的**第一段（分页规划）**是另一个槽位，不能被带着改。
        val s = base.copy(
            llmStoryboardApiUrl = "https://story.dev/v1",
            llmStoryboardApiKey = "sk-story",
            llmStoryboardModel = "story-model",
        )
        assertEquals("story-model", s.llmConfigForStoryboard().model)
        assertEquals("https://story.dev/v1", s.llmConfigForStoryboard().url)

        assertEquals("https://api.deepseek.com/v1", s.llmConfigForPlan().url)
        assertEquals("sk-main", s.llmConfigForPlan().key)
        assertEquals("deepseek-chat", s.llmConfigForPlan().model)
    }

    // ------------------------------------------------------------ 与备份/持久化的配合

    @Test
    fun `分功能字段能过一遍 JSON 往返`() {
        val s = base.copy(
            llmTranslateApiUrl = "https://t.dev/v1",
            llmTranslateApiKey = "sk-t",
            llmTranslateModel = "t-model",
            llmOptimizeApiUrl = "https://o.dev/v1",
            llmOptimizeApiKey = "sk-o",
            llmOptimizeModel = "o-model",
            llmStoryboardApiUrl = "https://s.dev/v1",
            llmStoryboardApiKey = "sk-s",
            llmStoryboardModel = "s-model",
            llmPlanApiUrl = "https://p.dev/v1",
            llmPlanApiKey = "sk-p",
            llmPlanModel = "p-model",
            llmReverseApiUrl = "https://r.dev/v1",
            llmReverseApiKey = "sk-r",
            llmReverseModel = "r-model",
        )
        val back = AppSettings.fromJson(s.toJson())
        assertEquals(s.llmTranslateApiUrl, back.llmTranslateApiUrl)
        assertEquals(s.llmTranslateApiKey, back.llmTranslateApiKey)
        assertEquals(s.llmTranslateModel, back.llmTranslateModel)
        assertEquals(s.llmOptimizeApiUrl, back.llmOptimizeApiUrl)
        assertEquals(s.llmOptimizeApiKey, back.llmOptimizeApiKey)
        assertEquals(s.llmOptimizeModel, back.llmOptimizeModel)
        assertEquals(s.llmStoryboardApiUrl, back.llmStoryboardApiUrl)
        assertEquals(s.llmStoryboardApiKey, back.llmStoryboardApiKey)
        assertEquals(s.llmStoryboardModel, back.llmStoryboardModel)
        assertEquals(s.llmPlanApiUrl, back.llmPlanApiUrl)
        assertEquals(s.llmPlanApiKey, back.llmPlanApiKey)
        assertEquals(s.llmPlanModel, back.llmPlanModel)
        assertEquals(s.llmReverseApiUrl, back.llmReverseApiUrl)
        assertEquals(s.llmReverseApiKey, back.llmReverseApiKey)
        assertEquals(s.llmReverseModel, back.llmReverseModel)
    }

    @Test
    fun `老设置没有这些键时读出来是空的也就是全部跟随`() {
        // 升级场景：盘上那份 JSON 里根本没有分功能字段
        val legacy = AppSettings().toJson().apply {
            remove("llmTranslateApiUrl")
            remove("llmTranslateApiKey")
            remove("llmTranslateModel")
            remove("llmStoryboardApiUrl")
            remove("llmStoryboardApiKey")
            remove("llmStoryboardModel")
        }
        val back = AppSettings.fromJson(legacy)
        assertEquals("", back.llmTranslateApiUrl)
        assertEquals("", back.llmTranslateModel)
        assertEquals("", back.llmStoryboardApiKey)
        // 空 = 跟随主配置，行为与升级前完全一致
        assertEquals(back.llmModel, back.llmConfigForStoryboard().model)
    }
}
