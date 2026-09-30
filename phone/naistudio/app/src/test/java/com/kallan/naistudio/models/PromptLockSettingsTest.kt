package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提示词锁（用户 2026-09-16 要求）的**持久化**部分。
 *
 * 锁本身的行为（手打不进、AI 不改写、垃圾桶清不掉）落在 `AppState.params` 的写入口上，
 * 那是 ViewModel 里的事，跑不了纯 JVM 单测。但**存不下来**是这套东西最容易出的错：
 * 三个字段里漏一个 `put`/`optBoolean`，表现就是"锁了之后重启一次又开了"——
 * 而且不会有任何报错，只有用户会觉得这功能时灵时不灵。所以这里把序列化钉死。
 */
class PromptLockSettingsTest {

    @Test
    fun `三个锁默认都是关的`() {
        val s = AppSettings()
        assertFalse(s.promptLockStyle)
        assertFalse(s.promptLockPositive)
        assertFalse(s.promptLockNegative)
    }

    @Test
    fun `三个锁能过一遍 JSON 往返`() {
        val s = AppSettings(
            promptLockStyle = true,
            promptLockPositive = false,
            promptLockNegative = true,
        )
        val back = AppSettings.fromJson(s.toJson())
        assertTrue(back.promptLockStyle)
        assertFalse(back.promptLockPositive)
        assertTrue(back.promptLockNegative)
    }

    @Test
    fun `老设置没有这些键时读出来是关的`() {
        val legacy = AppSettings().toJson().apply {
            remove("promptLockStyle")
            remove("promptLockPositive")
            remove("promptLockNegative")
        }
        val back = AppSettings.fromJson(legacy)
        assertFalse(back.promptLockStyle)
        assertFalse(back.promptLockPositive)
        assertFalse(back.promptLockNegative)
    }

    @Test
    fun `锁是配置不是凭证所以备份里要带着`() {
        // 和密钥的口径相反：锁的状态换个手机也该跟着走，不该让用户重设一遍
        val exported = AppSettings(promptLockPositive = true).toBackupJson()
        assertTrue(exported.getBoolean("promptLockPositive"))
        // 但它**绝不能**被"长得像密钥"的看门狗规则误伤（见 BackupPrivacyTest）
        assertFalse(AppSettings.SECRET_KEYS.contains("promptLockPositive"))
    }

    @Test
    fun `提示词锁与固定提示词是两组互不相干的开关`() {
        // promptLock* = 改不了（本轮的锁图标）；lockStylePrompt/lockNegativePrompt = 跨重启保留。
        // 名字像但语义不同，这里钉一下，免得以后有人"顺手合并"。
        val s = AppSettings(
            promptLockStyle = true,
            lockStylePrompt = false,
            promptLockNegative = true,
            lockNegativePrompt = false,
        )
        assertTrue(s.promptLockStyle)
        assertFalse(s.lockStylePrompt)
        assertTrue(s.promptLockNegative)
        assertFalse(s.lockNegativePrompt)
        // 两者都进 JSON，互不覆盖
        val back = AppSettings.fromJson(s.toJson())
        assertTrue(back.promptLockStyle)
        assertFalse(back.lockStylePrompt)
    }

    @Test
    fun `三条提示词各存各的字段`() {
        // 风格 + 正面合成一个**边框**之后，字段仍然是三个各存各的。
        // 这一条防的是"合框的时候顺手把两个字段也合并成一个"。
        val p = GenerateParams(
            stylePrompt = "manga page, screentone",
            positivePrompt = "1girl, blue hair",
            negativePrompt = "lowres, bad hands",
        )
        val back = GenerateParamsCodec.fromJson(GenerateParamsCodec.toJson(p))
        assertEquals("manga page, screentone", back.stylePrompt)
        assertEquals("1girl, blue hair", back.positivePrompt)
        assertEquals("lowres, bad hands", back.negativePrompt)
    }
}
