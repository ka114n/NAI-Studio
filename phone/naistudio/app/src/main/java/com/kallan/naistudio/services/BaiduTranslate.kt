package com.kallan.naistudio.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * **百度翻译**（可选翻译后端）。
 *
 * 口径取自 ComfyUI 插件 `prompt-assistant` 的 `services/baidu.py`：
 * ```
 * POST https://fanyi-api.baidu.com/api/trans/vip/translate   (form 表单)
 * q=<文本> & from=auto & to=zh & appid=<APPID> & salt=<随机数>
 * sign = md5(appid + q + salt + 密钥)
 * → trans_result[].dst（多行用 \n 拼回）
 * ```
 * 额外照抄它的两点工程处理：**长文本按段落切块（每块 ≤2000 字符）**、
 * **对限流/超时类错误码重试**。
 *
 * 需要百度翻译开放平台的 `APPID` 与`密钥`（设置里填）。
 */
class BaiduTranslate {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    /** 可重试的错误码（插件里的口径：QPS 限制 / 请求超时 / 系统错误）。 */
    private val retryableCodes = setOf("54003", "52001", "52002")

    suspend fun translate(
        appId: String,
        secretKey: String,
        text: String,
        from: String = "auto",
        to: String = "zh",
    ): String = withContext(Dispatchers.IO) {
        if (appId.isBlank() || secretKey.isBlank()) throw LlmException("百度翻译还没填 APPID / 密钥")
        val chunks = splitChunks(text)
        val parts = ArrayList<String>(chunks.size)
        for (chunk in chunks) {
            parts.add(translateChunk(appId, secretKey, chunk, from, to))
        }
        parts.joinToString("\n")
    }

    private fun translateChunk(
        appId: String,
        secretKey: String,
        chunk: String,
        from: String,
        to: String,
    ): String = callLines(appId, secretKey, chunk, from, to).joinToString("\n")

    /**
     * **多行进、多行出**（第 ㊿h 批 2026-09-22，给 `PromptTranslate` 用）。
     *
     * ⚠️ **如实返回百度给的行数** —— 不补齐、不截断、不自己切块：
     * 校验行数与二分重试是**管道的职责**。这里要是"贴心地补齐"，就把错位藏起来了，
     * 而错位的后果是**翻译静默串位**（A 的译文填到 B 上），用户看不出来、图就画歪了。
     *
     * ⚠️ 调用方**必须保证** `lines` 里没有空行：实测百度**吞空行**（5 行进 ⇒ 3 行出），
     * 那是它唯一的不守恒来源；批里没有空行，行数就守恒。
     */
    suspend fun translateLines(
        appId: String,
        secretKey: String,
        lines: List<String>,
        from: String = "auto",
        to: String = "en",
    ): List<String> = withContext(Dispatchers.IO) {
        if (appId.isBlank() || secretKey.isBlank()) throw LlmException("百度翻译还没填 APPID / 密钥")
        if (lines.isEmpty()) return@withContext emptyList()
        callLines(appId, secretKey, lines.joinToString("\n"), from, to)
    }

    /** 发一次请求，把 `trans_result[].dst` **按原顺序**取回来（行数如实）。 */
    private fun callLines(
        appId: String,
        secretKey: String,
        q: String,
        from: String,
        to: String,
    ): List<String> {
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                val salt = (32768..65536).random()
                val sign = md5("$appId$q$salt$secretKey")
                val body = FormBody.Builder()
                    .add("q", q)
                    .add("from", from)
                    .add("to", to)
                    .add("appid", appId)
                    .add("salt", salt.toString())
                    .add("sign", sign)
                    .build()
                val request = Request.Builder().url(ENDPOINT).post(body).build()
                val text: String
                http.newCall(request).execute().use { response ->
                    text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw LlmException("百度翻译 HTTP ${response.code}")
                    }
                }
                val json = JSONObject(text)
                if (json.has("error_code")) {
                    val code = json.optString("error_code")
                    val message = json.optString("error_msg").ifEmpty { "错误码 $code" }
                    if (code in retryableCodes && attempt == 0) {
                        lastError = LlmException("百度翻译：$message")
                        return@repeat
                    }
                    throw LlmException("百度翻译：$message")
                }
                val array = json.optJSONArray("trans_result")
                    ?: throw LlmException("百度翻译返回里没有 trans_result")
                return (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.optString("dst")
                }
            } catch (e: IOException) {
                lastError = LlmException("百度翻译网络失败：${e.message ?: "未知"}")
                if (attempt == 0) Thread.sleep(800)
            }
        }
        throw lastError ?: LlmException("百度翻译失败")
    }

    companion object {
        const val ENDPOINT = "https://fanyi-api.baidu.com/api/trans/vip/translate"
        private const val TAG = "BaiduTranslate"

        /** `sign = md5(appid + q + salt + 密钥)`（插件同款）。 */
        fun md5(value: String): String {
            val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        /**
         * 切块：优先按段落/换行切，单段过长再按长度硬切；块内保留原文（不丢空格）。
         */
        fun splitChunks(text: String, limit: Int = 2000): List<String> {
            if (text.length <= limit) return listOf(text)
            val chunks = ArrayList<String>()
            val current = StringBuilder()
            fun flush() {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString())
                    current.setLength(0)
                }
            }
            for (line in text.split("\n")) {
                if (line.length > limit) {
                    flush()
                    var index = 0
                    while (index < line.length) {
                        val end = minOf(index + limit, line.length)
                        chunks.add(line.substring(index, end))
                        index = end
                    }
                    continue
                }
                if (current.length + line.length + 1 > limit) flush()
                if (current.isNotEmpty()) current.append('\n')
                current.append(line)
            }
            flush()
            return chunks.ifEmpty { listOf(text) }
        }
    }
}
