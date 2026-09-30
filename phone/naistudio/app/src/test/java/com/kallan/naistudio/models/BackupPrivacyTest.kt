package com.kallan.naistudio.models

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份**不带凭证**。
 *
 * 用户要求："llm 的密钥和翻译密钥等隐私信息不备份"。
 *
 * 这条约束真正的风险不在"忘了删这三个字段"（那是改一次就完事的），
 * 而在于**以后新加一个密钥字段时没人想起来登记** —— 它会安安静静地
 * 跟着备份跑出去，而备份是要塞进 PNG、传网盘、发给别人看的那种东西。
 *
 * 所以这里最要紧的是最后那条"凡是长得像密钥的键都必须登记"的断言：
 * 它不依赖任何人记得住，新加字段忘了登记就直接让构建失败。
 */
class BackupPrivacyTest {

    private fun settingsWithSecrets() = AppSettings(
        llmApiUrl = "https://api.example.com/v1",
        llmApiKey = "sk-super-secret",
        llmModel = "gpt-4o-mini",
        translateService = "baidu",
        baiduTranslateAppId = "2024010100",
        baiduTranslateSecret = "hunter2",
    )

    private fun exportedSettings(app: AppSettings): JSONObject {
        val text = BackupCodec.encode(
            bundle = BackupCodec.Bundle(settings = app),
            appVersion = "0.2.110",
            exportedAt = "2026-01-01T00:00:00Z",
        )
        return JSONObject(text).getJSONObject("payload").getJSONObject("settings")
    }

    // ------------------------------------------------------------ 导出侧

    @Test
    fun `toBackupJson 剔掉了全部登记过的密钥`() {
        val json = settingsWithSecrets().toBackupJson()
        AppSettings.SECRET_KEYS.forEach { key ->
            assertFalse("$key 不该出现在备份 JSON 里", json.has(key))
        }
    }

    @Test
    fun `toBackupJson 保留非密钥配置`() {
        // 判定口径是"代价不对称"，不是一刀切删敏感词：
        // 接口地址、模型名这些是配置不是凭证，备份里带着换机才方便。
        val json = settingsWithSecrets().toBackupJson()
        assertEquals("https://api.example.com/v1", json.optString("llmApiUrl"))
        assertEquals("gpt-4o-mini", json.optString("llmModel"))
        assertEquals("baidu", json.optString("translateService"))
    }

    @Test
    fun `toBackupJson 与 toJson 的差别只有那几个密钥`() {
        // 防"顺手多删了别的字段"：备份要能还原配置，删多了就丢东西
        val full = settingsWithSecrets().toJson()
        val backup = settingsWithSecrets().toBackupJson()
        val removed = full.keys().asSequence().filter { !backup.has(it) }.toList()
        assertEquals(AppSettings.SECRET_KEYS.sorted(), removed.sorted())
    }

    @Test
    fun `导出的备份文件里没有密钥`() {
        val settings = exportedSettings(settingsWithSecrets())
        AppSettings.SECRET_KEYS.forEach { key ->
            assertFalse("备份文件里不该有 $key", settings.has(key))
        }
        // 也不该以别的形式留下来（值层面的兜底检查）
        val text = settings.toString()
        assertFalse(text.contains("sk-super-secret"))
        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("2024010100"))
    }

    // ------------------------------------------------------------ 导入侧

    @Test
    fun `导入别人的备份不会清掉本机的密钥`() {
        // 这是"不备份"能成立的关键：导入走的是逐键合并，
        // 备份里没有的键根本不会碰本机设置。
        val mine = settingsWithSecrets()
        val overlay = exportedSettings(settingsWithSecrets())

        val merged = AppSettings.fromJson(
            BackupCodec.mergeSettingsJson(mine.toJson(), overlay),
        )

        assertEquals("sk-super-secret", merged.llmApiKey)
        assertEquals("hunter2", merged.baiduTranslateSecret)
        assertEquals("2024010100", merged.baiduTranslateAppId)
        // 备份里带来的普通配置照样生效
        assertEquals("gpt-4o-mini", merged.llmModel)
    }

    @Test
    fun `导入备份也不会把别人的密钥带进来`() {
        // 反向：本机是空的，导入一份备份，密钥仍然是空的
        val empty = AppSettings()
        val overlay = exportedSettings(settingsWithSecrets())

        val merged = AppSettings.fromJson(
            BackupCodec.mergeSettingsJson(empty.toJson(), overlay),
        )

        assertEquals("", merged.llmApiKey)
        assertEquals("", merged.baiduTranslateSecret)
        assertEquals("", merged.baiduTranslateAppId)
    }

    // ------------------------------------------------------------ 看门狗

    @Test
    fun `凡是长得像密钥的字段都必须登记进 SECRET_KEYS`() {
        // ⚠️ 这条断言不依赖任何人记得住新字段。
        // 以后有人加了 llmFallbackKey / openaiToken / xxxPassword 却忘了登记，
        // 它会在这里失败，而不是等某天备份流出去了才发现。
        val allKeys = AppSettings().toJson().keys().asSequence().toList()
        val suspicious = allKeys
            .filter { AppSettings.SECRET_KEY_PATTERN.containsMatchIn(it) }
            .sorted()

        assertEquals(
            "有看起来是密钥的字段没登记进 AppSettings.SECRET_KEYS，它会跟着备份跑出去",
            AppSettings.SECRET_KEYS.sorted(),
            suspicious,
        )
    }

    @Test
    fun `导出的 settings 节里没有任何像密钥的键`() {
        // 上一条查的是"登记表完不完整"，这条查的是"实际产物干不干净"。
        // 两条是不同角度：万一 toBackupJson 的剔除逻辑写错了（比如键名拼错），
        // 上一条照样绿，这一条会红。
        val settings = exportedSettings(settingsWithSecrets())
        val bad = settings.keys().asSequence()
            .filter { AppSettings.SECRET_KEY_PATTERN.containsMatchIn(it) }
            .toList()
        assertTrue("备份里出现了像密钥的键：$bad", bad.isEmpty())
    }

    @Test
    fun `密钥清单本身不是空的`() {
        // 防"有人把列表清空了让测试变绿"
        assertTrue(AppSettings.SECRET_KEYS.isNotEmpty())
        assertTrue(AppSettings.SECRET_KEYS.contains("llmApiKey"))
    }
}
