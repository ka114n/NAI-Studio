package com.kallan.naistudio.models

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 自建账号模型的 JSON 解析与容错（对齐 docs/05 契约的 /auth/me 结构）。
 */
class AccountTest {

    @Test
    fun user_parses_all_fields() {
        val json = JSONObject(
            """{"id":"u_1","email":"a@b.com","displayName":"Neko","createdAt":"2026-09-12T00:00:00Z"}""",
        )
        val user = AuthUser.fromJson(json)
        assertEquals("u_1", user.id)
        assertEquals("a@b.com", user.email)
        assertEquals("Neko", user.displayName)
        assertEquals("2026-09-12T00:00:00Z", user.createdAt)
    }

    @Test
    fun user_tolerates_missing_optional_fields() {
        val user = AuthUser.fromJson(JSONObject("""{"id":"u_2"}"""))
        assertEquals("u_2", user.id)
        assertEquals("", user.email)
        assertEquals("", user.displayName)
    }

    @Test
    fun quota_parses_remaining_total_reset() {
        val quota = AuthQuota.fromJson(
            JSONObject("""{"remaining":12000,"total":20000,"resetAt":"2026-10-01T00:00:00Z"}"""),
        )
        assertEquals(12000, quota.remaining)
        assertEquals(20000, quota.total)
        assertEquals("2026-10-01T00:00:00Z", quota.resetAt)
    }

    @Test
    fun quota_null_json_is_empty() {
        val quota = AuthQuota.fromJson(null)
        assertNull(quota.remaining)
        assertNull(quota.total)
        assertNull(quota.resetAt)
    }

    @Test
    fun quota_handles_null_resetAt_and_missing_numbers() {
        val quota = AuthQuota.fromJson(JSONObject("""{"remaining":5,"resetAt":null}"""))
        assertEquals(5, quota.remaining)
        assertNull(quota.total)
        assertNull(quota.resetAt)
    }

    @Test
    fun settings_roundtrip_keeps_account_fields() {
        val s = AppSettings(hostedMode = true, accountServerUrl = "https://api.example.com")
        val back = AppSettings.fromJson(s.toJson())
        assertEquals(true, back.hostedMode)
        assertEquals("https://api.example.com", back.accountServerUrl)
    }
}
