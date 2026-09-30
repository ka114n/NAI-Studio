package com.kallan.naistudio.services

import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * 流式解码器的单元测试。
 *
 * **不联网、不消耗任何 Anlas**，用手工构造的 MessagePack / SSE 字节流打解码器。
 * 覆盖重点是增量解析：底层数据怎么切块都不能影响结果 —— 这是这类解码器最常见的 bug，
 * 而它一旦出错的表现是"预览根本不出现"，在设备上很难查。
 */
class NaiStreamDecoderTest {

    // ------------------------------------------------------------ 测试用编码器

    private fun mpStr(value: String): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return when {
            bytes.size <= 31 -> byteArrayOf((0xA0 or bytes.size).toByte()) + bytes
            bytes.size <= 0xFF -> byteArrayOf(0xD9.toByte(), bytes.size.toByte()) + bytes
            else -> byteArrayOf(0xDA.toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte()) + bytes
        }
    }

    private fun mpBin(bytes: ByteArray): ByteArray = when {
        bytes.size <= 0xFF -> byteArrayOf(0xC4.toByte(), bytes.size.toByte()) + bytes
        else -> byteArrayOf(0xC5.toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte()) + bytes
    }

    private fun mpInt(value: Int): ByteArray = when {
        value in 0..127 -> byteArrayOf(value.toByte())
        value in -32..-1 -> byteArrayOf(value.toByte())
        else -> byteArrayOf(
            0xCE.toByte(),
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        )
    }

    private fun mpMap(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(0x80 or entries.size) // fixmap（条目 <= 15）
        entries.forEach { (key, value) ->
            out.write(mpStr(key))
            out.write(value)
        }
        return out.toByteArray()
    }

    /** 加 4 字节大端长度前缀，组成一个流式帧。 */
    private fun frame(payload: ByteArray): ByteArray {
        val length = payload.size
        return byteArrayOf(
            (length ushr 24).toByte(),
            (length ushr 16).toByte(),
            (length ushr 8).toByte(),
            length.toByte(),
        ) + payload
    }

    private fun msgPackFrame(
        eventType: String,
        image: ByteArray,
        sampleIndex: Int = 0,
        stepIndex: Int? = 1,
    ): ByteArray {
        val entries = mutableListOf<Pair<String, ByteArray>>(
            "event_type" to mpStr(eventType),
            "samp_ix" to mpInt(sampleIndex),
            "image" to mpBin(image),
        )
        if (stepIndex != null) entries.add("step_ix" to mpInt(stepIndex))
        return frame(mpMap(entries))
    }

    /** 每次最多吐 [chunkSize] 字节的数据源，用来模拟任意的网络分包边界。 */
    private class ChunkedSource(private val data: ByteArray, private val chunkSize: Int) : Source {
        private var position = 0
        override fun read(sink: Buffer, byteCount: Long): Long {
            if (position >= data.size) return -1L
            val count = minOf(chunkSize.toLong(), byteCount, (data.size - position).toLong())
            sink.write(data, position, count.toInt())
            position += count.toInt()
            return count
        }

        override fun timeout(): Timeout = Timeout.NONE
        override fun close() = Unit
    }

    private class Outcome(val previews: List<NaiPreview>, val result: NaiStreamResult)

    private fun consume(
        bytes: ByteArray,
        contentType: String = "",
        totalSteps: Int = 28,
        chunkSize: Int = 0,
    ): Outcome {
        val source = if (chunkSize <= 0) {
            Buffer().write(bytes)
        } else {
            ChunkedSource(bytes, chunkSize).buffer()
        }
        val previews = mutableListOf<NaiPreview>()
        val result = runBlocking {
            consumeNaiGenerationStream(source, totalSteps, contentType) { previews.add(it) }
        }
        return Outcome(previews, result)
    }

    // ------------------------------------------------------------ MessagePack

    @Test
    fun `messagepack 帧在任意分包边界下都能解出`() {
        val image = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val stream = msgPackFrame("intermediate", image, stepIndex = 3) +
            msgPackFrame("final", image, stepIndex = 28)

        for (chunkSize in listOf(0, 1, 2, 3, 5, 7, 13, 64)) {
            val outcome = consume(stream, chunkSize = chunkSize)
            assertEquals("chunkSize=$chunkSize 最终图数量", 1, outcome.result.images.size)
            assertArrayEquals("chunkSize=$chunkSize 图像字节", image, outcome.result.images[0])
            assertTrue("chunkSize=$chunkSize 应回调过预览", outcome.previews.isNotEmpty())
            assertTrue("chunkSize=$chunkSize 末帧应为 final", outcome.previews.last().isFinal)
        }
    }

    @Test
    fun `字段别名与嵌套 payload 都能识别`() {
        val nested = mpMap(
            listOf(
                "eventType" to mpStr("final"),
                "sampleIndex" to mpInt(1),
                "stepIndex" to mpInt(5),
                "imageData" to mpBin(byteArrayOf(9, 9, 9)),
            ),
        )
        val stream = frame(mpMap(listOf("payload" to nested)))

        val outcome = consume(stream)
        assertEquals(1, outcome.result.images.size)
        assertArrayEquals(byteArrayOf(9, 9, 9), outcome.result.images[0])
        assertEquals(1, outcome.previews.last().sampleIndex)
    }

    @Test
    fun `图片以 base64 字符串给出时也能解码`() {
        val raw = byteArrayOf(11, 22, 33, 44)
        val encoded = Base64.getEncoder().encodeToString(raw)
        val stream = frame(
            mpMap(
                listOf(
                    "event_type" to mpStr("final"),
                    "samp_ix" to mpInt(0),
                    "image" to mpStr("data:image/png;base64,$encoded"),
                ),
            ),
        )

        val outcome = consume(stream)
        assertArrayEquals(raw, outcome.result.images[0])
    }

    @Test
    fun `图片以整数数组给出时也能解码`() {
        val payload = ByteArrayOutputStream().apply {
            write(0x80 or 3)
            write(mpStr("event_type")); write(mpStr("final"))
            write(mpStr("samp_ix")); write(mpInt(0))
            write(mpStr("image"))
            write(0x93) // fixarray(3)
            write(mpInt(7)); write(mpInt(8)); write(mpInt(9))
        }.toByteArray()

        val outcome = consume(frame(payload))
        assertArrayEquals(byteArrayOf(7, 8, 9), outcome.result.images[0])
    }

    @Test
    fun `多张图按 samp_ix 排序收集`() {
        val stream = msgPackFrame("final", byteArrayOf(2), sampleIndex = 1) +
            msgPackFrame("final", byteArrayOf(0), sampleIndex = 0) +
            msgPackFrame("final", byteArrayOf(3), sampleIndex = 2)

        val outcome = consume(stream)
        assertEquals(3, outcome.result.images.size)
        assertArrayEquals(byteArrayOf(0), outcome.result.images[0])
        assertArrayEquals(byteArrayOf(2), outcome.result.images[1])
        assertArrayEquals(byteArrayOf(3), outcome.result.images[2])
    }

    @Test
    fun `error 帧抛异常并带上 previewStarted`() {
        val stream = msgPackFrame("intermediate", byteArrayOf(1)) +
            frame(mpMap(listOf("event_type" to mpStr("error"), "message" to mpStr("额度不足"))))

        val error = assertThrows(NaiStreamException::class.java) { consume(stream) }
        assertEquals("额度不足", error.message)
        assertTrue("出错前已经推过预览，previewStarted 应为 true", error.previewStarted)
    }

    @Test
    fun `非法帧长度直接报错`() {
        // 注意：坏长度出现在**首块**时，嗅探会退到 SSE 分支（参考实现同此逻辑），
        // 不会命中长度检查。所以先给一个合法帧让解码器进入 MessagePack 模式。
        val stream = msgPackFrame("intermediate", byteArrayOf(1)) +
            byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00)
        val error = assertThrows(NaiStreamException::class.java) { consume(stream) }
        assertTrue("实际消息：${error.message}", error.message!!.contains("帧长度非法"))
    }

    @Test
    fun `只收到中间稿没有终图时报错且不自动重发`() {
        val stream = msgPackFrame("intermediate", byteArrayOf(1), stepIndex = 2)
        val error = assertThrows(NaiStreamException::class.java) { consume(stream) }
        assertTrue(error.message!!.contains("没有收到最终图片"))
        assertTrue(error.previewStarted)
    }

    @Test
    fun `进度按步数换算且末步不超过 0_99`() {
        // 必须带上 final 帧，否则 result() 会（正确地）抛"没有收到最终图片"
        val stream = msgPackFrame("intermediate", byteArrayOf(1), stepIndex = 13) +
            msgPackFrame("final", byteArrayOf(2), stepIndex = 28)
        val outcome = consume(stream, totalSteps = 28)
        val intermediate = outcome.previews.first { !it.isFinal }
        // stepIndex 是 0-based → currentStep = 14
        assertEquals(14, intermediate.currentStep)
        assertEquals(0.5f, intermediate.progress, 0.001f)
    }

    @Test
    fun `final 帧进度恒为 1 且步数取总步数`() {
        val stream = msgPackFrame("final", byteArrayOf(1), stepIndex = 3)
        val outcome = consume(stream, totalSteps = 28)
        assertEquals(1f, outcome.previews.last().progress, 0.0001f)
        assertEquals(28, outcome.previews.last().currentStep)
        assertTrue(outcome.previews.last().isFinal)
    }

    // ------------------------------------------------------------------- SSE

    @Test
    fun `SSE 的 JSON data 能解出`() {
        val image = Base64.getEncoder().encodeToString(byteArrayOf(4, 5, 6))
        val body = buildString {
            append("event: final\n")
            append("data: {\"event_type\":\"final\",\"samp_ix\":0,\"image\":\"$image\"}\n")
            append("\n")
        }.toByteArray(Charsets.UTF_8)

        val outcome = consume(body, contentType = "text/event-stream; charset=utf-8")
        assertArrayEquals(byteArrayOf(4, 5, 6), outcome.result.images[0])
    }

    @Test
    fun `SSE 的 data 里塞 base64 MessagePack 也能解出`() {
        val inner = mpMap(
            listOf(
                "event_type" to mpStr("final"),
                "samp_ix" to mpInt(0),
                "image" to mpBin(byteArrayOf(6, 6)),
            ),
        )
        val encoded = Base64.getEncoder().encodeToString(inner)
        val body = "event: final\ndata: $encoded\n\n".toByteArray(Charsets.UTF_8)

        // 不预先声明 contentType，强制走字节嗅探
        val outcome = consume(body)
        assertArrayEquals(byteArrayOf(6, 6), outcome.result.images[0])
    }

    @Test
    fun `SSE 的 data 里是裸 base64 图片时按图片处理`() {
        val encoded = Base64.getEncoder().encodeToString(byteArrayOf(8, 8, 8, 8))
        val finalEncoded = Base64.getEncoder().encodeToString(byteArrayOf(9, 9, 9, 9))
        val body = (
            "data: $encoded\n\n" +
                "event: final\n" +
                "data: {\"event_type\":\"final\",\"image\":\"$finalEncoded\"}\n\n"
            ).toByteArray(Charsets.UTF_8)

        val outcome = consume(body)
        assertArrayEquals(byteArrayOf(8, 8, 8, 8), outcome.previews.first().image)
        assertArrayEquals(byteArrayOf(9, 9, 9, 9), outcome.result.images[0])
    }

    @Test
    fun `SSE 分块投递不影响结果`() {
        val image = Base64.getEncoder().encodeToString(byteArrayOf(1, 1, 1))
        val body = ("event: final\ndata: {\"event_type\":\"final\",\"image\":\"$image\"}\n\n")
            .toByteArray(Charsets.UTF_8)

        for (chunkSize in listOf(1, 3, 9, 32)) {
            val outcome = consume(body, chunkSize = chunkSize)
            assertArrayEquals("chunkSize=$chunkSize", byteArrayOf(1, 1, 1), outcome.result.images[0])
        }
    }

    // ------------------------------------------------------------------- ZIP

    @Test
    fun `裸 ZIP 响应原样交给调用方`() {
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 1, 2, 3, 4, 5)
        val outcome = consume(zip)
        assertTrue(outcome.result.images.isEmpty())
        assertArrayEquals(zip, outcome.result.archive)
    }

    @Test
    fun `ZIP 分块投递也能收全`() {
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(5000) { (it % 251).toByte() }
        for (chunkSize in listOf(1, 17, 512)) {
            val outcome = consume(zip, chunkSize = chunkSize)
            assertArrayEquals("chunkSize=$chunkSize", zip, outcome.result.archive)
        }
    }

    // --------------------------------------------------------------- 边界情况

    @Test
    fun `空流报错`() {
        assertThrows(NaiStreamException::class.java) { consume(ByteArray(0)) }
    }

    @Test
    fun `截断的帧不会当成完整帧处理`() {
        val full = msgPackFrame("final", byteArrayOf(1, 2, 3))
        val truncated = full.copyOf(full.size - 2)
        val error = assertThrows(NaiStreamException::class.java) { consume(truncated) }
        assertTrue(error.message!!.contains("没有收到最终图片"))
    }
}
