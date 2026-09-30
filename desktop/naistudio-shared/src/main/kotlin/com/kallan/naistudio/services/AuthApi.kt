package com.kallan.naistudio.services

import com.kallan.naistudio.models.AuthQuota
import com.kallan.naistudio.models.AuthTokens
import com.kallan.naistudio.models.AuthUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 自建账号体系的 API 层（见 docs/05-方案-账号体系与接口契约.md）。
 *
 * 只用 OkHttp + org.json，符合项目“运行时第三方依赖只有 OkHttp”的硬约束。
 * 无状态：会话 token 由 [com.kallan.naistudio.state.AppState] 持有并传入；
 * 401 续期/重试的编排放在 AppState（它掌握 refreshToken 与持久化）。
 */

/** 账号接口错误。`code` 是契约里的机器码（如 INVALID_CREDENTIALS），`message` 给用户看。 */
class AuthException(
    val code: String,
    message: String,
    val statusCode: Int,
) : Exception(message)

/** 登录/注册结果。 */
data class AuthSession(
    val tokens: AuthTokens,
    val user: AuthUser,
)

/** /auth/me 结果。 */
data class AuthMe(
    val user: AuthUser,
    val quota: AuthQuota,
)

/** /sync/bundle 拉取结果。`version` 单调递增，PUT 时回传做乐观锁。 */
data class SyncBundle(
    val version: Int,
    val updatedAt: String,
    val bundle: JSONObject,
)

class AuthApi {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ---------------------------------------------------------------- 鉴权

    suspend fun register(
        serverUrl: String,
        email: String,
        password: String,
        inviteCode: String? = null,
    ): AuthSession = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("email", email.trim())
            put("password", password)
            if (!inviteCode.isNullOrBlank()) put("inviteCode", inviteCode.trim())
        }
        parseSession(post(serverUrl, "/auth/register", body, null))
    }

    suspend fun login(
        serverUrl: String,
        email: String,
        password: String,
    ): AuthSession = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("email", email.trim())
            put("password", password)
        }
        parseSession(post(serverUrl, "/auth/login", body, null))
    }

    suspend fun refresh(
        serverUrl: String,
        refreshToken: String,
    ): AuthTokens = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("refreshToken", refreshToken) }
        val json = post(serverUrl, "/auth/refresh", body, null)
        AuthTokens(
            accessToken = json.optString("accessToken"),
            refreshToken = json.optString("refreshToken").ifEmpty { refreshToken },
        )
    }

    /** 登出：使服务端 refreshToken 失效。网络失败不抛（本地照样清会话）。 */
    suspend fun logout(serverUrl: String, accessToken: String) = withContext(Dispatchers.IO) {
        runCatching { post(serverUrl, "/auth/logout", JSONObject(), accessToken) }
        Unit
    }

    suspend fun me(serverUrl: String, accessToken: String): AuthMe = withContext(Dispatchers.IO) {
        val json = get(serverUrl, "/auth/me", accessToken)
        AuthMe(
            user = AuthUser.fromJson(json.optJSONObject("user") ?: JSONObject()),
            quota = AuthQuota.fromJson(json.optJSONObject("quota")),
        )
    }

    // ---------------------------------------------------------------- 数据同步

    /** 拉云端配置；服务端 404（尚无数据）返回 null。 */
    suspend fun getBundle(serverUrl: String, accessToken: String): SyncBundle? =
        withContext(Dispatchers.IO) {
            try {
                val json = get(serverUrl, "/sync/bundle", accessToken)
                SyncBundle(
                    version = json.optInt("version", 0),
                    updatedAt = json.optString("updatedAt", ""),
                    bundle = json.optJSONObject("bundle") ?: JSONObject(),
                )
            } catch (e: AuthException) {
                if (e.statusCode == 404) null else throw e
            }
        }

    /**
     * 推本地配置。乐观锁：带上次的 [baseVersion]；服务端不一致抛 409 的 [AuthException]
     * （code=VERSION_CONFLICT），调用方处理冲突。返回新的 version。
     */
    suspend fun putBundle(
        serverUrl: String,
        accessToken: String,
        baseVersion: Int,
        bundle: JSONObject,
    ): Int = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("baseVersion", baseVersion)
            put("bundle", bundle)
        }
        val json = put(serverUrl, "/sync/bundle", body, accessToken)
        json.optInt("version", baseVersion)
    }

    // ---------------------------------------------------------------- HTTP 基础

    private fun post(serverUrl: String, path: String, body: JSONObject, accessToken: String?): JSONObject {
        val builder = Request.Builder()
            .url(normalizeBase(serverUrl) + path)
            .post(body.toString().toRequestBody(jsonMedia))
        if (accessToken != null) builder.header("Authorization", "Bearer $accessToken")
        return execute(builder.build())
    }

    private fun put(serverUrl: String, path: String, body: JSONObject, accessToken: String): JSONObject {
        val request = Request.Builder()
            .url(normalizeBase(serverUrl) + path)
            .header("Authorization", "Bearer $accessToken")
            .put(body.toString().toRequestBody(jsonMedia))
            .build()
        return execute(request)
    }

    private fun get(serverUrl: String, path: String, accessToken: String): JSONObject {
        val request = Request.Builder()
            .url(normalizeBase(serverUrl) + path)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .get()
            .build()
        return execute(request)
    }

    private fun execute(request: Request): JSONObject {
        val text: String
        val code: Int
        try {
            http.newCall(request).execute().use { response ->
                code = response.code
                text = response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw AuthException("NETWORK", "网络连接失败，请检查网络或服务器地址", 0)
        }
        if (code in 200..299) {
            return if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrElse { JSONObject() }
        }
        // 错误体：{ "error": { "code", "message" } }；解析不出就给通用文案
        val err = runCatching { JSONObject(text).optJSONObject("error") }.getOrNull()
        val serverCode = err?.optString("code")?.takeIf { it.isNotEmpty() } ?: "HTTP_$code"
        val message = err?.optString("message")?.takeIf { it.isNotEmpty() }
            ?: defaultMessage(code)
        throw AuthException(serverCode, message, code)
    }

    private fun parseSession(json: JSONObject): AuthSession = AuthSession(
        tokens = AuthTokens(
            accessToken = json.optString("accessToken"),
            refreshToken = json.optString("refreshToken"),
        ),
        user = AuthUser.fromJson(json.optJSONObject("user") ?: JSONObject()),
    )

    private fun defaultMessage(code: Int): String = when (code) {
        400 -> "请求有误（400）"
        401 -> "登录已过期或凭证无效（401）"
        402 -> "额度不足（402）"
        403 -> "没有权限（403）"
        404 -> "接口不存在（404）"
        409 -> "数据版本冲突（409）"
        429 -> "请求过于频繁（429）"
        in 500..599 -> "服务器出错（$code）"
        else -> "请求失败（$code）"
    }

    private fun normalizeBase(value: String): String = value.trim().trimEnd('/')
}
