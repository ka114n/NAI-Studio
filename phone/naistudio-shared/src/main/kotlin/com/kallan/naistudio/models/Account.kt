package com.kallan.naistudio.models

import org.json.JSONObject

/**
 * 自建账号体系的客户端数据模型（见 docs/05-方案-账号体系与接口契约.md）。
 *
 * 与 NovelAI 账号无关：这是运营方自有服务器的账号。真 NovelAI token 永远在服务端，
 * 客户端只持有本账号的会话 token（access / refresh，存 Keystore）。
 */

/** 账号信息（对应 /auth/me 的 `user`）。 */
data class AuthUser(
    val id: String,
    val email: String,
    val displayName: String = "",
    val createdAt: String = "",
) {
    companion object {
        fun fromJson(json: JSONObject): AuthUser = AuthUser(
            id = json.optString("id"),
            email = json.optString("email", ""),
            displayName = json.optString("displayName", ""),
            createdAt = json.optString("createdAt", ""),
        )
    }
}

/** 生图额度（对应 /auth/me 的 `quota`）。 */
data class AuthQuota(
    val remaining: Int? = null,
    val total: Int? = null,
    /** 额度重置时间（ISO8601），可空。 */
    val resetAt: String? = null,
) {
    companion object {
        fun fromJson(json: JSONObject?): AuthQuota {
            if (json == null) return AuthQuota()
            return AuthQuota(
                remaining = if (json.has("remaining") && !json.isNull("remaining")) json.optInt("remaining") else null,
                total = if (json.has("total") && !json.isNull("total")) json.optInt("total") else null,
                resetAt = if (json.isNull("resetAt")) null else json.optString("resetAt").ifEmpty { null },
            )
        }
    }
}

/** 会话凭证。**只在内存与 Keystore 之间流转，绝不落明文盘、绝不进日志。** */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * 登录态。
 *  · [LoggedOut]：未登录（托管模式下会被门禁拦在登录页）
 *  · [LoggedIn]：已登录，带账号与额度
 */
sealed class AuthState {
    object LoggedOut : AuthState()
    data class LoggedIn(val user: AuthUser, val quota: AuthQuota = AuthQuota()) : AuthState()
}
