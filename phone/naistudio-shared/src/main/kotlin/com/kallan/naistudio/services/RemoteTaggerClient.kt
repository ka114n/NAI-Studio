package com.kallan.naistudio.services

import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** HTTPS client: never follows redirects carrying the pairing secret. */
class RemoteTaggerClient(private val allowLoopbackForTest: Boolean = false) {
    @Volatile private var active: okhttp3.Call? = null
    private val client = OkHttpClient.Builder().dns(FunnelDns()).connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES).callTimeout(12, TimeUnit.MINUTES)
        .followRedirects(false).followSslRedirects(false).build()

    fun request(address: String, code: String, image: File? = null): String {
        val url = address.trim().trimEnd('/').toHttpUrl()
        require(url.isHttps || (allowLoopbackForTest && url.host == "127.0.0.1")) { "请填写电脑提供的 HTTPS 地址" }
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null && url.encodedPath == "/") { "请填写完整的服务根地址，不包含参数或路径" }
        require(code.matches(Regex("[A-Za-z0-9_-]{32,64}"))) { "请填写电脑显示的配对码" }
        if (image != null) require(image.isFile && image.length() in 1..MAX_IMAGE_BYTES.toLong()) { "图片无效或超过 16 MB" }
        val endpoint = url.newBuilder().addPathSegments(if (image == null) "v1/health" else "v1/tagger").build()
        val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $code")
        if (image != null) request.post(image.asRequestBody("application/octet-stream".toMediaType()))
        val call = client.newCall(request.build())
        active = call
        try {
            call.execute().use { response ->
                val body = response.body ?: error("电脑未返回结果")
                require(body.contentLength() <= 512 * 1024) { "服务返回数据过大" }
                val bytes = body.byteStream().use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 512 * 1024) { "服务返回数据过大" }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                val result = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrElse { error("电脑服务返回了无效响应（HTTP ${response.code}）") }
                check(response.isSuccessful) { result.optString("error", "连接失败（HTTP ${response.code}）") }
                return if (image == null) "电脑反推服务连接成功" else result.getString("text").also { require(it.isNotBlank()) { "未识别到标签" } }
            }
        } finally { active = null }
    }
    fun upscale(address: String, code: String, imagePng: ByteArray): ByteArray {
        val url = address.trim().trimEnd('/').toHttpUrl()
        require(url.isHttps || (allowLoopbackForTest && url.host == "127.0.0.1")) { "请填写电脑提供的 HTTPS 地址" }
        require(url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null && url.encodedPath == "/") { "请填写完整的服务根地址，不包含参数或路径" }
        require(code.matches(Regex("[A-Za-z0-9_-]{32,64}"))) { "请填写电脑显示的配对码" }
        require(imagePng.isNotEmpty() && imagePng.size <= MAX_IMAGE_BYTES) { "图片无效或超过 16 MB" }
        val endpoint = url.newBuilder().addPathSegments("v1/upscale").build()
        val request = Request.Builder().url(endpoint).header("Authorization", "Bearer $code")
            .post(imagePng.toRequestBody("image/png".toMediaType())).build()
        val call = client.newCall(request)
        active = call
        try {
            call.execute().use { response ->
                val body = response.body ?: error("电脑未返回放大结果")
                if (!response.isSuccessful) {
                    val detail = runCatching { JSONObject(body.string()).optString("error") }.getOrNull()
                    error(detail?.takeIf { it.isNotBlank() } ?: "电脑 RTX 放大失败（HTTP ${response.code}）")
                }
                require(body.contentLength() in 1..MAX_UPSCALE_OUTPUT_BYTES.toLong()) { "RTX 放大结果过大" }
                val output = java.io.ByteArrayOutputStream()
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= MAX_UPSCALE_OUTPUT_BYTES) { "RTX 放大结果过大" }
                        output.write(buffer, 0, count)
                    }
                }
                val bytes = output.toByteArray()
                require(bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()) { "电脑返回的不是有效 PNG 图像" }
                return bytes
            }
        } finally { active = null }
    }

    fun cancel() { active?.cancel() }
    companion object { const val MAX_IMAGE_BYTES = 16 * 1024 * 1024; const val MAX_UPSCALE_OUTPUT_BYTES = 128 * 1024 * 1024 }
}
