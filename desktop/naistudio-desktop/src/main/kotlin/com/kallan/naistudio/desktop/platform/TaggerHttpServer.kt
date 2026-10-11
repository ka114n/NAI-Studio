package com.kallan.naistudio.desktop.platform

import com.kallan.naistudio.platform.LocalImageTagger
import com.kallan.naistudio.services.RemoteTaggerClient
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Semaphore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** Loopback only. Tailscale Funnel supplies public HTTPS; application pairing remains mandatory. */
internal class TaggerHttpServer(
    private val code: String,
    private val tagger: LocalImageTagger,
    private val imageSize: (String) -> Pair<Int, Int>?,
    port: Int = 18965,
) : NanoHTTPD("127.0.0.1", port) {
    private val busy = Semaphore(1)
    init {
        // Reject excess connections instead of creating unbounded request threads.
        val active = java.util.Collections.synchronizedSet(mutableSetOf<ClientHandler>())
        val slots = Semaphore(4)
        setAsyncRunner(object : AsyncRunner {
            override fun exec(handler: ClientHandler) {
                if (!slots.tryAcquire()) { handler.close(); return }
                active.add(handler)
                Thread(handler, "tagger-share-request").apply { isDaemon = true; start() }
            }
            override fun closed(handler: ClientHandler) { if (active.remove(handler)) slots.release() }
            override fun closeAll() { synchronized(active) { active.toList() }.forEach { it.close() } }
        })
    }
    private fun json(status: Response.Status, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString()).apply {
            addHeader("Cache-Control", "no-store")
            closeConnection(true)
        }
    private fun failure(status: Response.Status, message: String) = json(status, JSONObject().put("error", message))
    override fun serve(session: IHTTPSession): Response {
        val supplied = session.headers["authorization"].orEmpty().removePrefix("Bearer ")
        if (!MessageDigest.isEqual(code.toByteArray(), supplied.toByteArray()))
            return failure(Response.Status.UNAUTHORIZED, "配对码错误，请重新复制电脑端配对码")
        if (session.uri == "/v1/health" && session.method == Method.GET)
            return json(Response.Status.OK, JSONObject().put("model", "PixAI Tagger v1.0").put("backend", "cuda"))
        if (session.method != Method.POST || session.uri !in setOf("/v1/tagger", "/v1/upscale"))
            return failure(Response.Status.NOT_FOUND, "接口不存在")
        val length = session.headers["content-length"]?.toLongOrNull() ?: -1L
        if (length !in 1..RemoteTaggerClient.MAX_IMAGE_BYTES.toLong() || session.headers.containsKey("transfer-encoding"))
            return failure(Response.Status.BAD_REQUEST, "图片必须包含有效长度且不超过 16 MB")
        if (!busy.tryAcquire()) return failure(Response.Status.SERVICE_UNAVAILABLE, "电脑正在反推，请稍后重试")
        var image: File? = null
        return try {
            val bytes = ByteArray(length.toInt())
            java.io.DataInputStream(session.inputStream).readFully(bytes)
            image = File.createTempFile("nai-remote-tagger-", ".img")
            image.writeBytes(bytes)
            val dimensions = imageSize(image.absolutePath) ?: error("上传的文件不是支持的图片")
            require(dimensions.first > 0 && dimensions.second > 0 && dimensions.first.toLong() * dimensions.second <= 25_000_000) { "图片尺寸过大，请缩小后重试" }
            return if (session.uri == "/v1/upscale") {
                require(dimensions.first.toLong() * dimensions.second <= 4_000_000) { "RTX 2× 放大输入上限为 400 万像素" }
                val result = runBlocking { tagger.upscale(image.absolutePath) }
                require(result.size in 1..MAX_UPSCALE_OUTPUT_BYTES) { "RTX 放大结果大小异常" }
                newFixedLengthResponse(Response.Status.OK, "image/png", java.io.ByteArrayInputStream(result), result.size.toLong()).apply {
                    addHeader("Cache-Control", "no-store")
                    closeConnection(true)
                }
            } else {
                val text = runBlocking { tagger.infer(image.absolutePath) {} }
                json(Response.Status.OK, JSONObject().put("text", text))
            }
        } catch (error: Exception) {
            failure(Response.Status.BAD_REQUEST, error.message ?: "电脑反推失败")
        } finally { image?.delete(); busy.release() }
    }

    companion object { const val MAX_UPSCALE_OUTPUT_BYTES = 128 * 1024 * 1024 }
}
