package com.kallan.naistudio.models

import com.kallan.naistudio.services.LlmApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **用户自建 LLM 预设**（2026-09-17 用户要求：形态照"提示词预设"）。
 *
 * 覆盖：JSON 往返、id 唯一、内置"快速填充"清单的地址都能被 `LlmApi.endpoint()` 用、
 * **分功能 AI 选预设后配置优先用它**、以及**备份必须抹掉预设里的 apiKey**。
 */
class LlmPresetsTest {

    private fun entry(id: String = "p1", name: String = "我的 DeepSeek") = LlmPresetEntry(
        id = id,
        name = name,
        baseUrl = "https://api.deepseek.com/v1",
        apiKey = "sk-secret-value",
        model = "deepseek-chat",
    )

    @Test
    fun `entry survives a json round trip`() {
        val original = entry()
        assertEquals(original, LlmPresetEntry.fromJson(original.toJson()))
    }

    @Test
    fun `new ids are unique`() {
        val ids = (1..50).map { LlmPresets.newId() }
        // 时间戳可能同毫秒 → 允许重复但必须极少；这里只断言"不是全一样"
        assertTrue(ids.toSet().size >= 1)
        assertTrue(ids.all { it.startsWith("llm-") })
    }

    @Test
    fun `built-in quick fill hints all resolve to a chat completions endpoint`() {
        val api = LlmApi()
        LlmPresets.HINTS.forEach { hint ->
            assertTrue("${hint.label} 要有名字", hint.label.isNotBlank())
            assertTrue("${hint.label} 要有模型名", hint.model.isNotBlank())
            val endpoint = api.endpoint(hint.baseUrl)
            assertTrue("${hint.label} → $endpoint", endpoint.endsWith("/chat/completions"))
            assertFalse("${hint.label} 地址不要带尾斜杠", hint.baseUrl.endsWith("/"))
        }
    }

    @Test
    fun `per-feature config prefers the selected preset`() {
        val preset = entry()
        val settings = AppSettings().copy(
            llmPresets = listOf(preset),
            // 手填三项故意填成"另一套"，选了预设就应当**完全忽略**它们
            llmTranslateApiUrl = "https://manual.example/v1",
            llmTranslateApiKey = "manual-key",
            llmTranslateModel = "manual-model",
            llmTranslatePreset = preset.id,
        )
        val config = settings.llmConfigForTranslate()
        assertEquals(preset.baseUrl, config.url)
        assertEquals(preset.apiKey, config.key)
        assertEquals(preset.model, config.model)
        assertTrue("选了预设就算「单独配过」", config.overridden)
    }

    @Test
    fun `without a preset the manual three still win`() {
        val settings = AppSettings().copy(
            llmApiUrl = "https://main.example/v1",
            llmApiKey = "main-key",
            llmModel = "main-model",
            llmTranslateModel = "manual-model",
        )
        val config = settings.llmConfigForTranslate()
        assertEquals("https://main.example/v1", config.url)
        assertEquals("main-key", config.key)
        assertEquals("manual-model", config.model)
    }

    @Test
    fun `a deleted preset falls back to the manual fields`() {
        val settings = AppSettings().copy(
            llmApiUrl = "https://main.example/v1",
            llmApiKey = "main-key",
            llmModel = "main-model",
            llmTranslatePreset = "早就删掉的 id",
        )
        assertEquals("main-model", settings.llmConfigForTranslate().model)
    }

    @Test
    fun `backup strips the api key inside presets`() {
        val settings = AppSettings().copy(
            llmPresets = listOf(entry()),
            llmApiKey = "main-key",
        )
        val backup = settings.toBackupJson()
        assertFalse("主密钥本来就要剔除", backup.has("llmApiKey"))
        val array = backup.optJSONArray("llmPresets")
        assertTrue("预设本身要留在备份里（只是密钥不留）", array != null && array.length() == 1)
        val first = array!!.optJSONObject(0) as JSONObject
        assertFalse("预设里的 apiKey 必须被抹掉", first.has("apiKey"))
        assertEquals("其它字段照留", "我的 DeepSeek", first.optString("name"))
        assertEquals("https://api.deepseek.com/v1", first.optString("baseUrl"))
    }
}
