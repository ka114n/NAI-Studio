package com.kallan.naistudio.desktop.platform

import com.kallan.naistudio.platform.LocalImageTagger
import com.kallan.naistudio.services.RemoteTaggerClient
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Test

class TaggerSharingTest {
    private val code = "a".repeat(43)
    @Test fun rejectsHttpRemoteEndpointsBeforeSendingSecrets() {
        try { RemoteTaggerClient().request("http://example.com", code); fail() }
        catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("HTTPS")) }
    }
    @Test fun authenticatedClientReceivesTagsAndUploadedTemporaryFileIsDeleted() {
        var uploaded: File? = null
        val tagger = object : LocalImageTagger {
            override suspend fun infer(imagePath: String, onProgress: (String) -> Unit): String {
                uploaded = File(imagePath); assertTrue(uploaded!!.exists()); return "solo, smile"
            }
            override fun close() {}
        }
        val server = TaggerHttpServer(code, tagger, { 64 to 64 }, 0)
        val picture = File.createTempFile("test-share-", ".png").apply { writeBytes(byteArrayOf(1,2,3)) }
        server.start(5000, true)
        try {
            val address = "http://127.0.0.1:${server.listeningPort}"
            val client = RemoteTaggerClient(true)
            assertTrue(client.request(address, code).contains("连接成功"))
            assertEquals("solo, smile", client.request(address, code, picture))
            assertFalse(uploaded!!.exists())
            val response = OkHttpClient().newCall(Request.Builder().url("$address/v1/tagger").header("Authorization", "Bearer wrong").post(byteArrayOf(1).toRequestBody()).build()).execute()
            response.use { assertEquals(401, it.code) }
        } finally { server.stop(); picture.delete() }
    }
    @Test fun authenticatedRtxUpscaleReturnsPngAndDeletesUploadedTemporaryFile() {
        var uploaded: File? = null
        val expectedPng = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 1, 2, 3)
        val tagger = object : LocalImageTagger {
            override suspend fun infer(imagePath: String, onProgress: (String) -> Unit): String = "unused"
            override suspend fun upscale(imagePath: String): ByteArray {
                uploaded = File(imagePath); assertTrue(uploaded!!.exists()); return expectedPng
            }
            override fun close() {}
        }
        val server = TaggerHttpServer(code, tagger, { 64 to 64 }, 0)
        server.start(5000, true)
        try {
            val address = "http://127.0.0.1:${server.listeningPort}"
            val request = Request.Builder().url("$address/v1/upscale").header("Authorization", "Bearer $code")
                .post(byteArrayOf(1, 2, 3).toRequestBody("image/png".toMediaType())).build()
            OkHttpClient().newCall(request).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("image/png", response.header("Content-Type"))
                assertArrayEquals(expectedPng, response.body!!.bytes())
            }
            assertFalse(uploaded!!.exists())
        } finally { server.stop() }
    }
    @Test fun malformedImagesAreRejectedAndDoNotReachTheModel() {
        val tagger = object : LocalImageTagger {
            override suspend fun infer(imagePath: String, onProgress: (String) -> Unit): String { fail(); return "" }
            override fun close() {}
        }
        val server = TaggerHttpServer(code, tagger, { null }, 0)
        server.start(5000, true)
        try {
            OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:${server.listeningPort}/v1/tagger")
                .header("Authorization", "Bearer $code").post(byteArrayOf(1).toRequestBody()).build()).execute().use { assertEquals(400, it.code) }
        } finally { server.stop() }
    }
    @Test fun concurrentUploadIsRejectedWhileGpuIsBusy() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val tagger = object : LocalImageTagger {
            override suspend fun infer(imagePath: String, onProgress: (String) -> Unit): String {
                entered.countDown(); check(release.await(10, java.util.concurrent.TimeUnit.SECONDS)); return "solo"
            }
            override fun close() {}
        }
        val server = TaggerHttpServer(code, tagger, { 32 to 32 }, 0)
        server.start(5000, true)
        val client = OkHttpClient()
        val request = Request.Builder().url("http://127.0.0.1:${server.listeningPort}/v1/tagger")
            .header("Authorization", "Bearer $code").post(byteArrayOf(1).toRequestBody()).build()
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val first = executor.submit<Int> { client.newCall(request).execute().use { it.code } }
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            client.newCall(request).execute().use { assertEquals(503, it.code) }
            release.countDown()
            assertEquals(200, first.get(5, java.util.concurrent.TimeUnit.SECONDS).toInt())
        } finally { release.countDown(); executor.shutdownNow(); server.stop() }
    }
}
