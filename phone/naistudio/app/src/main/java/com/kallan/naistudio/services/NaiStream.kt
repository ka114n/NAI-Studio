package com.kallan.naistudio.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.BufferedSource
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.TreeMap

/**
 * NovelAI 流式生成帧解码 —— 「模糊逐渐变清晰」的预览就来自这里。
 *
 * **行为对齐参考实现**：`mobile/lib/services/nai_stream.dart`（Dart）。
 *
 * ## 关于"模糊 → 清晰"
 * 规格调研确认：参考实现**没有任何客户端模糊**（全仓检索 `ImageFiltered`/`ImageFilter`/
 * `BackdropFilter`/`BlurStyle`/`GaussianBlur` 命中数为 0）。这个观感完全来自
 * **服务端按去噪 step 逐帧推送的中间稿**——低 step 的图本来就糊，越往后越清。
 * 客户端只负责：按约 110ms 的节奏替换帧，并保证新帧解码完成前保留旧帧（不闪白）。
 *
 * ## 协议（三种格式都要支持，靠首块字节嗅探区分）
 * 1. **长度前缀 MessagePack**（官方默认）：`[4 字节大端长度 N][N 字节 MessagePack 对象]`，重复
 * 2. **SSE**（部分网关）：`event:` / `data:` 行，data 里可能是 JSON、base64-MessagePack 或裸图片
 * 3. **ZIP**：服务端直接吐整包（等于非流式结果）
 *
 * 帧字段（命名有变体，全部容错）：`event_type`/`eventType`/`type`、
 * `samp_ix`/`sampleIndex`/`sample_index`、`step_ix`/`stepIndex`/`step_index`、
 * `image`/`data`/`image_data`/`imageData`、`message`/`error`。
 * 真实字段可能被包在 `payload` 或 `data` 子对象里。
 *
 * ## 相对参考实现的优化
 * 1. **不引第三方 MessagePack 库**：手写只覆盖本场景的紧凑解码器，无反射、无额外体积。
 * 2. **缓冲区不每帧重新分配**：Dart 版每次 push 都新建数组再拼接，大帧下是 O(n²) 拷贝；
 *    这里用可增长的 [FrameBuffer]，消费后做一次 memmove。
 * 3. 解码在 IO 线程，预览按时间节流，避免解码任务堆积。
 */

/** 单帧体积上限，与服务端约束一致。 */
private const val MAX_FRAME_BYTES = 128 * 1024 * 1024

/** MessagePack 递归深度上限，防止畸形输入打爆调用栈。 */
private const val MAX_DEPTH = 64

/** 一帧预览（或最终图）。字段名与参考实现的 `NaiGenerationPreview` 对应。 */
class NaiPreview(
    val image: ByteArray,
    val progress: Float,
    val currentStep: Int,
    val totalSteps: Int,
    val sampleIndex: Int,
    val isFinal: Boolean,
)

/** 流式失败。`previewStarted` 表示"已经出过预览图才失败"，用于决定提示文案。 */
class NaiStreamException(message: String, val previewStarted: Boolean = false) : Exception(message)

/** 流式结果：要么是逐帧收集到的图片，要么是整包 ZIP。 */
class NaiStreamResult(
    val images: List<ByteArray> = emptyList(),
    val archive: ByteArray? = null,
)

// ---------------------------------------------------------------------------
// MessagePack 解码（只覆盖本场景需要的类型）
// ---------------------------------------------------------------------------

/**
 * 极简 MessagePack 解码器。
 *
 * 支持：nil / bool / 全部整数宽度 / float32 / float64 / fixstr / str8-32 /
 * bin8-32 / fixarray / array16-32 / fixmap / map16-32。**不支持 ext 族**（本协议用不到）。
 *
 * 返回类型：`null` / `Boolean` / `Long` / `Double` / `String` / `ByteArray` /
 * `List<Any?>` / `Map<String, Any?>`。
 */
internal object MsgPack {

    fun decode(bytes: ByteArray): Any? = Reader(bytes).readValue(0)

    private class Reader(private val bytes: ByteArray) {
        private var pos = 0

        fun readValue(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw NaiStreamException("MessagePack 嵌套过深")
            val marker = u8()
            return when {
                marker <= 0x7f -> marker.toLong()
                marker >= 0xe0 -> (marker - 0x100).toLong()
                marker in 0x80..0x8f -> readMap(marker and 0x0f, depth)
                marker in 0x90..0x9f -> readArray(marker and 0x0f, depth)
                marker in 0xa0..0xbf -> readString(marker and 0x1f)
                else -> when (marker) {
                    0xc0 -> null
                    0xc2 -> false
                    0xc3 -> true
                    0xc4 -> readBinary(u8())
                    0xc5 -> readBinary(u16())
                    0xc6 -> readBinary(i32())
                    0xca -> readF32()
                    0xcb -> readF64()
                    0xcc -> u8().toLong()
                    0xcd -> u16().toLong()
                    0xce -> u32()
                    0xcf -> i64()
                    0xd0 -> u8().toByte().toLong()
                    0xd1 -> u16().toShort().toLong()
                    0xd2 -> i32().toLong()
                    0xd3 -> i64()
                    0xd9 -> readString(u8())
                    0xda -> readString(u16())
                    0xdb -> readString(i32())
                    0xdc -> readArray(u16(), depth)
                    0xdd -> readArray(i32(), depth)
                    0xde -> readMap(u16(), depth)
                    0xdf -> readMap(i32(), depth)
                    in 0xc7..0xc9, in 0xd4..0xd8 ->
                        throw NaiStreamException("不支持的 MessagePack ext 类型：0x%02x".format(marker))
                    else -> throw NaiStreamException("未知的 MessagePack 标记：0x%02x".format(marker))
                }
            }
        }

        private fun readMap(count: Int, depth: Int): Map<String, Any?> {
            if (count < 0) throw NaiStreamException("MessagePack map 长度非法")
            val result = LinkedHashMap<String, Any?>(count.coerceAtMost(64))
            repeat(count) {
                val key = readValue(depth + 1)
                val value = readValue(depth + 1)
                result[key?.toString() ?: "null"] = value
            }
            return result
        }

        private fun readArray(count: Int, depth: Int): List<Any?> {
            if (count < 0) throw NaiStreamException("MessagePack array 长度非法")
            val result = ArrayList<Any?>(count.coerceAtMost(1024))
            repeat(count) { result.add(readValue(depth + 1)) }
            return result
        }

        private fun readString(length: Int): String {
            if (length < 0) throw NaiStreamException("MessagePack str 长度非法")
            require(length)
            val value = String(bytes, pos, length, Charsets.UTF_8)
            pos += length
            return value
        }

        private fun readBinary(length: Int): ByteArray {
            if (length < 0) throw NaiStreamException("MessagePack bin 长度非法")
            require(length)
            val value = bytes.copyOfRange(pos, pos + length)
            pos += length
            return value
        }

        private fun readF32(): Double {
            require(4)
            val bits = ((bytes[pos].toInt() and 0xff) shl 24) or
                ((bytes[pos + 1].toInt() and 0xff) shl 16) or
                ((bytes[pos + 2].toInt() and 0xff) shl 8) or
                (bytes[pos + 3].toInt() and 0xff)
            pos += 4
            return Float.fromBits(bits).toDouble()
        }

        private fun readF64(): Double {
            require(8)
            var bits = 0L
            for (i in 0 until 8) bits = (bits shl 8) or (bytes[pos + i].toLong() and 0xff)
            pos += 8
            return Double.fromBits(bits)
        }

        private fun u8(): Int {
            require(1)
            return bytes[pos++].toInt() and 0xff
        }

        private fun u16(): Int {
            require(2)
            val value = ((bytes[pos].toInt() and 0xff) shl 8) or (bytes[pos + 1].toInt() and 0xff)
            pos += 2
            return value
        }

        private fun i32(): Int {
            require(4)
            var value = 0
            for (i in 0 until 4) value = (value shl 8) or (bytes[pos + i].toInt() and 0xff)
            pos += 4
            return value
        }

        private fun u32(): Long = i32().toLong() and 0xFFFFFFFFL

        private fun i64(): Long {
            require(8)
            var value = 0L
            for (i in 0 until 8) value = (value shl 8) or (bytes[pos + i].toLong() and 0xff)
            pos += 8
            return value
        }

        private fun require(count: Int) {
            if (count < 0 || pos + count > bytes.size) {
                throw NaiStreamException("MessagePack 数据不完整（需要 $count 字节，剩余 ${bytes.size - pos}）")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 帧模型与字段归一化
// ---------------------------------------------------------------------------

internal class NaiFrame(
    val eventType: String,
    val sampleIndex: Int,
    val stepIndex: Int?,
    val image: ByteArray?,
    val error: String?,
)

private fun optionalInteger(value: Any?): Int? = when (value) {
    is Number -> value.toInt()
    is String -> value.toIntOrNull()
    else -> null
}

private val BASE64_RE = Regex("^[A-Za-z0-9+/]*={0,2}$")
private val DATA_URI_RE = Regex("^data:image/[^;]+;base64,", RegexOption.IGNORE_CASE)

private fun looksLikeBase64(value: String): Boolean {
    val compact = value.filterNot { it.isWhitespace() }
    return compact.length >= 4 && compact.length % 4 == 0 && BASE64_RE.matches(compact)
}

/**
 * 预览帧字节提取。支持 4 种形态，与参考实现的 `_decodeImage` 一致：
 * 二进制 / int 列表 / base64 字符串（会剥 `data:image/...;base64,` 前缀）。
 *
 * 注意：**不做图像格式嗅探** —— 参考实现也不做，字节原样交给平台解码器。
 * 已知中间稿可能是 PNG 也可能是 JPEG（桌面端的格式嗅探代码可佐证），
 * `BitmapFactory` 两者都能解。
 */
private fun decodeImage(value: Any?): ByteArray? = when (value) {
    is ByteArray -> value
    is List<*> -> if (value.isNotEmpty() && value.all { it is Number }) {
        ByteArray(value.size) { (value[it] as Number).toByte() }
    } else null
    is String -> {
        if (value.isEmpty()) null
        else {
            val compact = value.replaceFirst(DATA_URI_RE, "").filterNot { it.isWhitespace() }
            if (!looksLikeBase64(compact)) null
            else runCatching { Base64.getDecoder().decode(compact) }.getOrNull()
        }
    }
    else -> null
}

private fun asRecord(value: Any?): Map<String, Any?>? {
    val map = value as? Map<*, *> ?: return null
    // 我们的解码器产出的键本来就是 String；只有外部送进来的 map 才需要规整
    @Suppress("UNCHECKED_CAST")
    if (map.isEmpty() || map.keys.all { it is String }) return map as Map<String, Any?>
    return map.entries.associate { (key, entry) -> key.toString() to entry }
}

private fun frameFromRecord(message: Map<String, Any?>, fallbackEventType: String = ""): NaiFrame {
    val nested = asRecord(message["payload"]) ?: asRecord(message["data"])
    val source = if (nested != null && message["image"] == null && message["event_type"] == null) {
        message + nested
    } else {
        message
    }

    val eventType = (source["event_type"] ?: source["eventType"] ?: source["type"])
        ?.toString()
        ?.takeIf { it.isNotEmpty() }
        ?: fallbackEventType.ifEmpty { "intermediate" }

    val errorValue = source["message"] ?: source["error"]

    return NaiFrame(
        eventType = eventType,
        sampleIndex = optionalInteger(source["samp_ix"] ?: source["sampleIndex"] ?: source["sample_index"]) ?: 0,
        stepIndex = optionalInteger(source["step_ix"] ?: source["stepIndex"] ?: source["step_index"]),
        image = decodeImage(source["image"] ?: source["data"] ?: source["image_data"] ?: source["imageData"]),
        error = if (eventType == "error" || source["error"] != null) {
            (errorValue ?: "流式生成失败").toString()
        } else {
            null
        },
    )
}

// ---------------------------------------------------------------------------
// 可增长帧缓冲
// ---------------------------------------------------------------------------

private class FrameBuffer(initialCapacity: Int = 64 * 1024) {
    private var data = ByteArray(initialCapacity)
    var size: Int = 0
        private set

    fun append(chunk: ByteArray, count: Int) {
        if (size + count > data.size) {
            var capacity = data.size
            while (capacity < size + count) capacity = capacity shl 1
            data = data.copyOf(capacity)
        }
        System.arraycopy(chunk, 0, data, size, count)
        size += count
    }

    fun uint32BE(offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 24) or
            ((data[offset + 1].toInt() and 0xff) shl 16) or
            ((data[offset + 2].toInt() and 0xff) shl 8) or
            (data[offset + 3].toInt() and 0xff)

    fun slice(from: Int, to: Int): ByteArray = data.copyOfRange(from, to)

    fun indexOf(value: Byte, from: Int): Int {
        var i = if (from < 0) 0 else from
        while (i < size) {
            if (data[i] == value) return i
            i++
        }
        return -1
    }

    /** 消费前 count 字节；剩余做一次 memmove（而不是重新分配数组）。 */
    fun consume(count: Int) {
        if (count <= 0) return
        if (count >= size) {
            size = 0
            return
        }
        System.arraycopy(data, count, data, 0, size - count)
        size -= count
    }

    fun toByteArray(): ByteArray = data.copyOf(size)
}

// ---------------------------------------------------------------------------
// 三种帧解码路径
// ---------------------------------------------------------------------------

private class MessagePackFrameDecoder {
    private val buffer = FrameBuffer()

    fun push(chunk: ByteArray, count: Int): List<NaiFrame> {
        if (count <= 0) return emptyList()
        buffer.append(chunk, count)

        val frames = ArrayList<NaiFrame>(4)
        var offset = 0
        while (buffer.size - offset >= 4) {
            val length = buffer.uint32BE(offset)
            if (length <= 0 || length > MAX_FRAME_BYTES) {
                throw NaiStreamException("流式帧长度非法：$length")
            }
            if (buffer.size - offset < 4 + length) break
            val payload = buffer.slice(offset + 4, offset + 4 + length)
            val record = asRecord(MsgPack.decode(payload))
            if (record != null) frames.add(frameFromRecord(record))
            offset += 4 + length
        }
        buffer.consume(offset)
        return frames
    }
}

private class SseFrameDecoder {
    private val buffer = FrameBuffer(16 * 1024)
    private var eventType = ""
    private val dataLines = ArrayList<String>(4)

    fun push(chunk: ByteArray, count: Int): List<NaiFrame> {
        buffer.append(chunk, count)

        val frames = ArrayList<NaiFrame>(4)
        var consumed = 0
        while (true) {
            val newline = buffer.indexOf(NEWLINE, consumed)
            if (newline < 0) break
            val lineBytes = buffer.slice(consumed, newline)
            consumed = newline + 1
            frames.addAll(handleLine(String(lineBytes, Charsets.UTF_8)))
        }
        buffer.consume(consumed)
        return frames
    }

    fun finish(): List<NaiFrame> {
        if (buffer.size == 0) return dispatch()
        val raw = buffer.slice(0, buffer.size)
        buffer.consume(buffer.size)
        // 尾部 \r 由 handleLine 统一剥掉
        return handleLine(String(raw, Charsets.UTF_8)) + dispatch()
    }

    private fun handleLine(rawLine: String): List<NaiFrame> {
        // 逐行按 \n 切分，CRLF 换行会给每行留一个尾部 \r —— 统一在这里剥掉，
        // 否则多行 data 拼接时中间的 \r 会混进 base64 / JSON。
        val line = if (rawLine.endsWith("\r")) rawLine.dropLast(1) else rawLine
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return emptyList()

        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)

        return when (field) {
            "event" -> { eventType = value.trim(); emptyList() }
            "data" -> { dataLines.add(value); emptyList() }
            else -> emptyList()
        }
    }

    private fun dispatch(): List<NaiFrame> {
        if (dataLines.isEmpty()) {
            eventType = ""
            return emptyList()
        }
        val value = dataLines.joinToString("\n").trim()
        val currentEvent = eventType
        eventType = ""
        dataLines.clear()

        if (value.isEmpty() || value == "[DONE]") return emptyList()

        // 部分网关把整个 JSON 再包一层字符串
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            val unwrapped = runCatching {
                org.json.JSONTokener(value).nextValue() as? String
            }.getOrNull()
            if (unwrapped != null) return dispatchValue(currentEvent, unwrapped)
        }
        return dispatchValue(currentEvent, value)
    }

    private fun dispatchValue(currentEvent: String, value: String): List<NaiFrame> {
        val parsed = runCatching { JSONObject(value) }.getOrNull()
        if (parsed != null) {
            val record = jsonToRecord(parsed) ?: return emptyList()
            return listOf(frameFromRecord(record, currentEvent))
        }

        if (!looksLikeBase64(value)) {
            return if (currentEvent == "error") {
                listOf(NaiFrame(currentEvent, 0, null, null, value))
            } else {
                emptyList()
            }
        }
        val bytes = runCatching {
            Base64.getDecoder().decode(value.filterNot { it.isWhitespace() })
        }.getOrNull() ?: return emptyList()

        val decoded = runCatching { asRecord(MsgPack.decode(bytes)) }.getOrNull()
        if (decoded != null) {
            val frame = frameFromRecord(decoded, currentEvent)
            val resolvedEvent =
                if (frame.eventType == "intermediate" && currentEvent.isNotEmpty()) currentEvent
                else frame.eventType
            return listOf(NaiFrame(resolvedEvent, frame.sampleIndex, frame.stepIndex, frame.image, frame.error))
        }
        return listOf(NaiFrame(currentEvent.ifEmpty { "intermediate" }, 0, null, bytes, null))
    }

    private companion object {
        const val NEWLINE: Byte = 0x0a
    }
}

internal fun jsonToRecord(value: JSONObject): Map<String, Any?>? {
    val result = LinkedHashMap<String, Any?>(value.length())
    val keys = value.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        result[key] = jsonValue(value.opt(key))
    }
    return result
}

private fun jsonValue(value: Any?): Any? = when (value) {
    null, JSONObject.NULL -> null
    is org.json.JSONArray -> (0 until value.length()).map { jsonValue(value.opt(it)) }
    is JSONObject -> jsonToRecord(value)
    else -> value
}

// ---------------------------------------------------------------------------
// 消费入口
// ---------------------------------------------------------------------------

private enum class StreamMode { UNKNOWN, MESSAGE_PACK, SSE, ZIP }

private class StreamConsumer(
    private val totalSteps: Int,
    contentType: String,
    private val onPreview: (NaiPreview) -> Unit,
) {
    private var mode = if (contentType.lowercase().contains("text/event-stream")) {
        StreamMode.SSE
    } else {
        StreamMode.UNKNOWN
    }
    private val messagePack = MessagePackFrameDecoder()
    private val sse = SseFrameDecoder()
    private val zip = ByteArrayOutputStream(256 * 1024)
    private val prefix = ByteArrayOutputStream(64)
    private val finals = TreeMap<Int, ByteArray>()

    private var previewStarted = false
    private var lastPreviewAt = 0L

    fun push(chunk: ByteArray, count: Int) {
        if (mode == StreamMode.UNKNOWN) {
            prefix.write(chunk, 0, count)
            val buffered = prefix.toByteArray()
            if (buffered.size < 4) return
            mode = sniff(buffered)
            prefix.reset()
            dispatch(buffered, buffered.size)
            return
        }
        dispatch(chunk, count)
    }

    private fun sniff(head: ByteArray): StreamMode {
        if (head[0] == 0x50.toByte() && head[1] == 0x4b.toByte()) return StreamMode.ZIP
        val textual = String(head.copyOf(minOf(32, head.size)), Charsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\n', '\r')
        if (SSE_PREFIX_RE.containsMatchIn(textual)) return StreamMode.SSE
        val length = ((head[0].toInt() and 0xff) shl 24) or
            ((head[1].toInt() and 0xff) shl 16) or
            ((head[2].toInt() and 0xff) shl 8) or
            (head[3].toInt() and 0xff)
        return if (length in 1..MAX_FRAME_BYTES) StreamMode.MESSAGE_PACK else StreamMode.SSE
    }

    private fun dispatch(chunk: ByteArray, count: Int) {
        when (mode) {
            StreamMode.ZIP -> zip.write(chunk, 0, count)
            StreamMode.SSE -> consume(sse.push(chunk, count))
            StreamMode.MESSAGE_PACK -> consume(messagePack.push(chunk, count))
            StreamMode.UNKNOWN -> Unit
        }
    }

    fun finishSse() {
        if (mode == StreamMode.SSE) consume(sse.finish())
    }

    private fun consume(frames: List<NaiFrame>) {
        for (frame in frames) {
            frame.error?.let { throw NaiStreamException(it, previewStarted) }
            val image = frame.image ?: continue
            if (image.isEmpty()) continue

            previewStarted = true
            val currentStep = (frame.stepIndex ?: 0) + 1
            val isFinal = frame.eventType == "final"
            if (isFinal) finals[frame.sampleIndex] = image

            // 节流规则照抄参考实现：110ms 一帧；final 无条件回调；最后一步兜底不丢
            val now = System.currentTimeMillis()
            if (isFinal || now - lastPreviewAt >= PREVIEW_INTERVAL_MS || currentStep >= totalSteps) {
                lastPreviewAt = now
                onPreview(
                    NaiPreview(
                        image = image,
                        progress = if (isFinal) 1f
                        else (currentStep.toFloat() / totalSteps.coerceAtLeast(1)).coerceIn(0f, 0.99f),
                        currentStep = if (isFinal) totalSteps else currentStep,
                        totalSteps = totalSteps,
                        sampleIndex = frame.sampleIndex,
                        isFinal = isFinal,
                    ),
                )
            }
        }
    }

    fun result(): NaiStreamResult {
        if (mode == StreamMode.ZIP) {
            return NaiStreamResult(archive = zip.toByteArray())
        }
        if (finals.isEmpty()) {
            // 与参考实现同一句提示：不自动重发，避免重复扣费
            throw NaiStreamException(
                "流式生成结束，但没有收到最终图片。为避免重复扣费，未自动重发请求。",
                previewStarted,
            )
        }
        return NaiStreamResult(images = finals.values.toList())
    }

    private companion object {
        /** 参考实现里的节流阈值。 */
        const val PREVIEW_INTERVAL_MS = 110L
        val SSE_PREFIX_RE = Regex("^(?:event|data|id|retry)\\s*:|^:")
    }
}

/**
 * 消费一次流式生成响应。
 *
 * @param source 响应体（OkHttp 的 BufferedSource），在 IO 线程逐块读取
 * @param totalSteps 本次请求的 steps，用于算进度
 * @param contentType 响应 Content-Type，`text/event-stream` 时直接按 SSE 起步
 * @param onPreview 预览回调；已按约 110ms 节流，final 帧必定回调
 */
suspend fun consumeNaiGenerationStream(
    source: BufferedSource,
    totalSteps: Int,
    contentType: String,
    onPreview: (NaiPreview) -> Unit,
): NaiStreamResult = withContext(Dispatchers.IO) {
    val consumer = StreamConsumer(totalSteps, contentType, onPreview)
    val chunk = ByteArray(16 * 1024)
    while (true) {
        val read = source.read(chunk, 0, chunk.size)
        if (read == -1) break
        if (read > 0) consumer.push(chunk, read)
    }
    consumer.finishSse()
    consumer.result()
}
