package com.kallan.naistudio.services

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Inflater

/**
 * PNG 文本块的读写（**纯 Kotlin / 纯字节操作，可写 JVM 单测**）。
 *
 * 用来把备份 JSON **内嵌进一张图片**里 —— 和 ComfyUI / NovelAI 把生成参数写进 PNG 的做法一样：
 * 图片本身照常能看，元数据跟在 `tEXt` 块里，随便丢哪儿都带着走。
 *
 * 写法（与 ComfyUI 的约定对齐）：
 *  · 块类型 `tEXt`，关键字 `naistudio-backup`；
 *  · 内容是 **紧凑 JSON**，非 ASCII 字符按 JSON 规范转义成 `\uXXXX`
 *    —— 这样整块都是 Latin-1 安全的，而且**原样就是合法 JSON**（任何 JSON 解析器都能直接读）；
 *  · 块插在 `IHDR` 之后、其它块之前（PNG 规定文本块必须在 IDAT 之前）。
 *
 * 读法（容错优先）：
 *  · 扫**所有** `tEXt` / `iTXt` / `zTXt` 块，只要哪个块的内容能解析成我们的备份 JSON 就认；
 *  · 也会试 base64（有些工具会把 JSON 再套一层）。
 */
object PngMetadata {

    /** 我们用的关键字。 */
    const val KEYWORD = "naistudio-backup"

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < PNG_SIGNATURE.size) return false
        return PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }
    }

    /**
     * 把 [json] 作为 `tEXt` 块插进 [png]（关键字 [KEYWORD]）。
     * 输入不是 PNG、或结构不完整时返回 null（调用方自己决定怎么报错）。
     */
    fun embedText(png: ByteArray, json: String, keyword: String = KEYWORD): ByteArray? {
        if (!isPng(png)) return null
        val escaped = escapeNonAscii(json)
        val payload = ByteArrayOutputStream().apply {
            write(keyword.toByteArray(Charsets.ISO_8859_1))
            write(0)
            write(escaped.toByteArray(Charsets.ISO_8859_1))
        }.toByteArray()
        val chunk = buildChunk("tEXt", payload) ?: return null

        val output = ByteArrayOutputStream(png.size + chunk.size)
        output.write(png, 0, PNG_SIGNATURE.size)
        var offset = PNG_SIGNATURE.size
        var inserted = false
        while (offset + 12 <= png.size) {
            val length = readInt(png, offset)
            if (length < 0 || offset + 12 + length > png.size) return null
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            // 文本块必须排在 IDAT 之前；所以插在 IHDR 后面最稳
            if (!inserted && type != "IHDR") {
                output.write(chunk)
                inserted = true
            }
            output.write(png, offset, 12 + length)
            offset += 12 + length
            if (type == "IEND") break
        }
        if (!inserted) {
            // 理论上不会走到（PNG 一定有 IHDR），兜底插在最后
            output.write(chunk)
        }
        return output.toByteArray()
    }

    /** 逐个读出 PNG 里的文本块（关键字 → 文本）。 */
    fun readTextChunks(png: ByteArray): List<Pair<String, String>> {
        if (!isPng(png)) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        var offset = PNG_SIGNATURE.size
        while (offset + 12 <= png.size) {
            val length = readInt(png, offset)
            if (length < 0 || offset + 12 + length > png.size) break
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            val data = png.copyOfRange(offset + 8, offset + 8 + length)
            runCatching {
                when (type) {
                    "tEXt" -> readText(data)?.let { result.add(it) }
                    "iTXt" -> readInternationalText(data)?.let { result.add(it) }
                    "zTXt" -> readCompressedText(data)?.let { result.add(it) }
                }
            }
            offset += 12 + length
            if (type == "IEND") break
        }
        return result
    }

    /**
     * 从一张内嵌备份的图片里取出备份 JSON 文本；没有就返回 null。
     *
     * 不挑关键字：任何文本块的内容只要"看起来像我们的备份 JSON"就用
     * （这样用别的工具重新内嵌过也还认得出来）。
     */
    fun extractBackupJson(png: ByteArray): String? {
        for ((keyword, text) in readTextChunks(png)) {
            if (looksLikeBackup(text)) return text
            // 有些工具会把 JSON 再 base64 一层（用 java.util.Base64：minSdk 26 起可用，单测里也能跑）
            val decoded = runCatching {
                String(java.util.Base64.getMimeDecoder().decode(text), Charsets.UTF_8)
            }.getOrNull()
            if (decoded != null && looksLikeBackup(decoded)) return decoded
            if (keyword == KEYWORD && looksLikeBackup(text)) return text
        }
        return null
    }

    // -----------------------------------------------------------------------
    // 内部
    // -----------------------------------------------------------------------

    private fun looksLikeBackup(text: String): Boolean {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) return false
        return runCatching {
            JSONObject(trimmed).optString("format") == "naistudio.backup"
        }.getOrDefault(false)
    }

    /** 非 ASCII 一律转成 `\uXXXX`（JSON 规范里的转义，解析器会还原）。 */
    private fun escapeNonAscii(text: String): String {
        val builder = StringBuilder(text.length + 16)
        for (ch in text) {
            if (ch.code in 0x20..0x7E) {
                builder.append(ch)
            } else {
                builder.append("\\u").append("%04x".format(ch.code))
            }
        }
        return builder.toString()
    }

    private fun readText(data: ByteArray): Pair<String, String>? {
        val separator = data.indexOf(0.toByte())
        if (separator <= 0) return null
        val keyword = String(data, 0, separator, Charsets.ISO_8859_1)
        val text = String(data, separator + 1, data.size - separator - 1, Charsets.ISO_8859_1)
        return keyword to text
    }

    private fun readInternationalText(data: ByteArray): Pair<String, String>? {
        val keywordEnd = data.indexOf(0.toByte())
        if (keywordEnd <= 0 || keywordEnd + 3 > data.size) return null
        val keyword = String(data, 0, keywordEnd, Charsets.ISO_8859_1)
        val compressionFlag = data[keywordEnd + 1].toInt()
        var index = keywordEnd + 3
        val languageEnd = data.indexOf(0.toByte(), index)
        if (languageEnd < 0) return null
        index = languageEnd + 1
        val translatedEnd = data.indexOf(0.toByte(), index)
        if (translatedEnd < 0) return null
        index = translatedEnd + 1
        if (index > data.size) return null
        val raw = data.copyOfRange(index, data.size)
        val text = if (compressionFlag == 1) {
            runCatching { String(inflate(raw), Charsets.UTF_8) }.getOrNull() ?: return null
        } else {
            String(raw, Charsets.UTF_8)
        }
        return keyword to text
    }

    private fun readCompressedText(data: ByteArray): Pair<String, String>? {
        val separator = data.indexOf(0.toByte())
        if (separator <= 0 || separator + 2 > data.size) return null
        val keyword = String(data, 0, separator, Charsets.ISO_8859_1)
        val raw = data.copyOfRange(separator + 2, data.size)
        val text = runCatching { String(inflate(raw), Charsets.ISO_8859_1) }.getOrNull() ?: return null
        return keyword to text
    }

    private fun inflate(raw: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(raw)
        val output = ByteArrayOutputStream(raw.size * 3)
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count <= 0) break
            output.write(buffer, 0, count)
        }
        inflater.end()
        return output.toByteArray()
    }

    private fun buildChunk(type: String, payload: ByteArray): ByteArray? {
        if (type.length != 4) return null
        return ByteArrayOutputStream(payload.size + 12).apply {
            writeInt(this, payload.size)
            write(type.toByteArray(Charsets.US_ASCII))
            write(payload)
            val crc = CRC32().apply {
                update(type.toByteArray(Charsets.US_ASCII))
                update(payload)
            }
            writeInt(this, crc.value.toInt())
        }.toByteArray()
    }

    private fun writeInt(output: ByteArrayOutputStream, value: Int) {
        output.write((value ushr 24) and 0xFF)
        output.write((value ushr 16) and 0xFF)
        output.write((value ushr 8) and 0xFF)
        output.write(value and 0xFF)
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun ByteArray.indexOf(value: Byte, from: Int = 0): Int {
        for (index in from until size) if (this[index] == value) return index
        return -1
    }
}
