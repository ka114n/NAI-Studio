package com.kallan.naistudio.models

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/**
 * NovelAI 的 **alpha 通道隐写**（`stealth_pngcomp`）：把元数据藏在像素 alpha 的**最低位**里。
 *
 * ## 为什么非要有它
 *
 * NovelAI 生成的图**不一定**带 PNG 文本块。实测一张 832×1216 的正规下载图：
 *
 * ```text
 * chunk 类型集合: ['IDAT', 'IEND', 'IHDR']     ← 一个 tEXt/iTXt/zTXt 都没有
 * alpha 取值分布: {254: 11039, 255: 1000673}   ← 254/255 混着，就是在载数据
 * ```
 *
 * 而 alpha 的**肉眼差异是不可见的**（254 vs 255），所以图看着完全正常。
 * 这就是官方 <https://github.com/NovelAI/novelai-image-metadata> 的 `nai_meta.py` 干的事。
 *
 * ## 算法（逐条与官方实现一致，**有两处极易写错**）
 *
 * 1. 取 alpha 通道 → **先转置**（`alpha.T`）再拉平 = **列优先**遍历，
 *    不是逐行！这是第一处坑。
 * 2. 每 8 个最低位打包成一个字节，**高位在前**（`np.packbits` 默认大端）。
 * 3. 前 **[MAGIC_BYTES] 字节**是魔数 —— 注意 `stealth_pngcomp` 是 **15 个字符**，
 *    不是 16。按 16 去比会一辈子匹配不上（我就这么卡了一轮）。
 * 4. 接着 4 字节**大端**整数 = 载荷长度，单位是**位**，要 `// 8`。
 * 5. 载荷是 **gzip**，解出来才是 JSON。
 *
 * 后面还可能跟一段 FEC 纠错数据（4 字节长度 + 数据），我们**只取 JSON**，直接不管它。
 *
 * 本对象**不碰 Android**，纯字节运算，所以能写 JVM 单测。
 */
object StealthPng {

    /** 魔数。⚠️ 是 15 个字符。 */
    const val MAGIC = "stealth_pngcomp"

    val MAGIC_BYTES: ByteArray = MAGIC.toByteArray(Charsets.US_ASCII)

    /**
     * 像素数上限。解压后是 `宽×高×4` 字节，一亿像素就是 400 MB ——
     * 用户随手选一张巨图不该把 App 干崩，超了就直接放弃隐写这条路。
     */
    private const val MAX_PIXELS = 40_000_000L

    /** PNG 签名。 */
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    /**
     * 解出隐写元数据；没有 / 不是这种格式 / 图不支持 → `null`。
     *
     * 返回的是"关键字 → 文本"的映射，**和 PNG 文本块那条路同一个形状**，
     * 所以调用方拿到之后可以直接喂给同一套解析器（`Comment` / `Description` /
     * `Software` / `Source` 这些键完全一致）。
     */
    fun decode(png: ByteArray): Map<String, String>? {
        val payload = extractPayload(png) ?: return null
        // 载荷是 gzip，解出来才是 JSON 文本
        val text = gunzip(payload) ?: return null
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val map = LinkedHashMap<String, String>()
        json.keys().forEach { key ->
            val value = json.opt(key)
            // 全都转成字符串：`Comment` 本身就是"内容是 JSON 的字符串"，
            // 其余标量照字面转；对象/数组转 JSON 文本，至少不丢信息。
            val text = when (value) {
                null, JSONObject.NULL -> return@forEach
                is String -> value
                is JSONObject, is JSONArray -> value.toString()
                else -> value.toString()
            }
            map[key] = text
        }
        return map.ifEmpty { null }
    }

    /**
     * 抠出 gzip 载荷（还没解压）。任何一步不符合预期都返回 `null`。
     *
     * 拆成独立一步是为了让单测能**分层断言**：先确认"能从这张图里读出载荷"，
     * 再确认"载荷解出来是那段 JSON" —— 出错时一眼看出是容器错了还是内容错了。
     */
    fun extractPayload(png: ByteArray): ByteArray? {
        if (png.size < 8 + 25) return null
        for (i in PNG_SIGNATURE.indices) {
            if (png[i] != PNG_SIGNATURE[i]) return null
        }

        val ihdr = readIhdr(png) ?: return null
        val (width, height) = ihdr
        if (width <= 0 || height <= 0) return null
        if (width.toLong() * height.toLong() > MAX_PIXELS) return null

        val inflated = inflateIdat(png) ?: return null
        val pixels = unfilter(inflated, width, height) ?: return null
        return readPayloadFromAlpha(pixels, width, height)
    }

    /** Remove recognised alpha/RGB steganography without flattening genuine transparency. */
    fun stripHiddenMetadata(png: ByteArray): ByteArray {
        val (width, height) = readIhdr(png) ?: return png
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS) {
            "Image is too large to clear hidden metadata safely"
        }
        val pixels = unfilter(requireNotNull(inflateIdat(png)), width, height)
            ?: error("Cannot decode PNG pixels to clear hidden metadata")
        fun signature(channels: IntArray): String {
            val bytes = ByteArray(15)
            val available = width.toLong() * height * channels.size
            if (available < bytes.size * 8) return ""
            for (bit in 0 until bytes.size * 8) {
                val pixel = bit / channels.size
                val x = pixel / height
                val y = pixel % height
                val channel = channels[bit % channels.size]
                val lsb = pixels[(y * width + x) * 4 + channel].toInt() and 1
                bytes[bit / 8] = (bytes[bit / 8].toInt() or (lsb shl (7 - bit % 8))).toByte()
            }
            return String(bytes, Charsets.US_ASCII)
        }
        val alpha = signature(intArrayOf(3)) in setOf("stealth_pngcomp", "stealth_pnginfo")
        val rgb = signature(intArrayOf(0, 1, 2)) in setOf("stealth_rgbcomp", "stealth_rgbinfo")
        if (!alpha && !rgb) return png
        // Clear the entire carrier, including payload/FEC, rather than just corrupting its header.
        for (i in pixels.indices) {
            if ((alpha && i % 4 == 3) || (rgb && i % 4 != 3)) {
                pixels[i] = (pixels[i].toInt() or 1).toByte()
            }
        }
        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed).use { out ->
            val stride = width * 4
            for (y in 0 until height) {
                out.write(0) // PNG filter None
                out.write(pixels, y * stride, stride)
            }
        }
        fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
            fun writeInt(n: Int) { for (shift in intArrayOf(24, 16, 8, 0)) out.write(n ushr shift and 255) }
            val name = type.toByteArray(Charsets.US_ASCII)
            writeInt(data.size); out.write(name); out.write(data)
            val crc = CRC32().apply { update(name); update(data) }
            writeInt(crc.value.toInt())
        }
        val out = ByteArrayOutputStream()
        out.write(PNG_SIGNATURE)
        var offset = 8
        var replaced = false
        while (offset + 12 <= png.size) {
            val length = readInt(png, offset)
            require(length >= 0 && length <= png.size - offset - 12) { "Invalid PNG chunk" }
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            if (type == "IDAT") {
                if (!replaced) writeChunk(out, "IDAT", compressed.toByteArray())
                replaced = true
            } else out.write(png, offset, length + 12)
            offset += length + 12
            if (type == "IEND") break
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------------------ PNG 外壳

    /** IHDR → (宽, 高)。只支持 **8 位 RGBA、非隔行**（NovelAI 就是这个）。 */
    private fun readIhdr(png: ByteArray): Pair<Int, Int>? {
        var offset = 8
        while (offset + 8 <= png.size) {
            val length = readInt(png, offset)
            if (length < 0) return null
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            if (type == "IHDR") {
                if (offset + 8 + 13 > png.size) return null
                val width = readInt(png, offset + 8)
                val height = readInt(png, offset + 12)
                val bitDepth = png[offset + 16].toInt() and 0xFF
                val colorType = png[offset + 17].toInt() and 0xFF
                val interlace = png[offset + 20].toInt() and 0xFF
                // 6 = RGBA。别的颜色类型没有 alpha 通道，隐写无从谈起。
                if (bitDepth != 8 || colorType != 6 || interlace != 0) return null
                return width to height
            }
            if (type == "IEND") return null
            offset += 12 + length
        }
        return null
    }

    /** 把所有 IDAT 拼起来解压（zlib 流）。 */
    private fun inflateIdat(png: ByteArray): ByteArray? {
        val deflated = ByteArrayOutputStream()
        var offset = 8
        var seen = false
        while (offset + 8 <= png.size) {
            val length = readInt(png, offset)
            if (length < 0) return null
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            if (type == "IDAT") {
                val start = offset + 8
                if (start + length > png.size) return null
                deflated.write(png, start, length)
                seen = true
            }
            if (type == "IEND") break
            offset += 12 + length
        }
        if (!seen) return null

        val inflater = Inflater()
        val source = deflated.toByteArray()
        inflater.setInput(source)
        val out = ByteArrayOutputStream(source.size * 4)
        val buffer = ByteArray(64 * 1024)
        try {
            while (!inflater.finished()) {
                val read = inflater.inflate(buffer)
                if (read == 0) {
                    // 输入没了又没结束 = 数据不完整；needsDictionary 我们也不支持
                    if (inflater.needsInput() || inflater.needsDictionary()) return null
                }
                out.write(buffer, 0, read)
            }
        } catch (e: Exception) {
            return null
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    /** 逐行去滤波，返回滤干净的像素字节（RGBA 交错）。 */
    private fun unfilter(raw: ByteArray, width: Int, height: Int): ByteArray? {
        val bpp = 4
        val stride = width * bpp
        val expected = height.toLong() * (stride + 1)
        if (raw.size.toLong() < expected) return null

        val out = ByteArray(height * stride)
        val prev = ByteArray(stride)
        var pos = 0
        for (y in 0 until height) {
            val filter = raw[pos].toInt() and 0xFF
            pos++
            val rowStart = y * stride
            System.arraycopy(raw, pos, out, rowStart, stride)
            pos += stride
            when (filter) {
                0 -> Unit
                1 -> for (i in bpp until stride) {
                    out[rowStart + i] = (out[rowStart + i] + out[rowStart + i - bpp]).toByte()
                }
                2 -> for (i in 0 until stride) {
                    out[rowStart + i] = (out[rowStart + i] + prev[i]).toByte()
                }
                3 -> for (i in 0 until stride) {
                    val a = if (i >= bpp) out[rowStart + i - bpp].toInt() and 0xFF else 0
                    val b = prev[i].toInt() and 0xFF
                    out[rowStart + i] = (out[rowStart + i] + ((a + b) shr 1)).toByte()
                }
                4 -> for (i in 0 until stride) {
                    val a = if (i >= bpp) out[rowStart + i - bpp].toInt() and 0xFF else 0
                    val b = prev[i].toInt() and 0xFF
                    val c = if (i >= bpp) prev[i - bpp].toInt() and 0xFF else 0
                    out[rowStart + i] = (out[rowStart + i] + paeth(a, b, c)).toByte()
                }
                else -> return null
            }
            System.arraycopy(out, rowStart, prev, 0, stride)
        }
        return out
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = kotlin.math.abs(p - a)
        val pb = kotlin.math.abs(p - b)
        val pc = kotlin.math.abs(p - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }

    // ------------------------------------------------------------------ 位流

    /**
     * 从 alpha 最低位里读出 gzip 载荷。
     *
     * ⚠️ **列优先**（`x` 在外层、`y` 在内层）—— 对应官方的 `alpha.T.reshape(-1)`。
     * 按行读会得到一堆看起来"像那么回事"但永远匹配不上魔数的字节。
     */
    private fun readPayloadFromAlpha(pixels: ByteArray, width: Int, height: Int): ByteArray? {
        val stride = width * 4
        val stream = BitReader { index ->
            val x = index / height
            val y = index % height
            val at = y * stride + x * 4 + 3
            if (at >= pixels.size) -1 else pixels[at].toInt() and 1
        }

        val magic = ByteArray(MAGIC_BYTES.size)
        for (i in magic.indices) {
            magic[i] = stream.readByte() ?: return null
        }
        if (!magic.contentEquals(MAGIC_BYTES)) return null

        val lengthBits = stream.readInt() ?: return null
        if (lengthBits <= 0) return null
        val lengthBytes = lengthBits / 8
        if (lengthBytes <= 0) return null

        val payload = ByteArray(lengthBytes)
        for (i in 0 until lengthBytes) {
            payload[i] = stream.readByte() ?: return null
        }
        return payload
    }

    /** 按位取字节：每 8 位组成一个字节，**高位在前**。 */
    private class BitReader(private val bit: (Int) -> Int) {
        private var position = 0

        fun readByte(): Byte? {
            var value = 0
            for (i in 0 until 8) {
                val b = bit(position++)
                if (b < 0) return null
                value = (value shl 1) or (b and 1)
            }
            return value.toByte()
        }

        fun readInt(): Int? {
            var value = 0
            for (i in 0 until 4) {
                val b = readByte() ?: return null
                value = (value shl 8) or (b.toInt() and 0xFF)
            }
            return value
        }
    }

    // ------------------------------------------------------------------ 工具

    private fun readInt(bytes: ByteArray, at: Int): Int {
        if (at + 4 > bytes.size) return -1
        return ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)
    }

    /** gzip 解压（给单测和调用方用）。 */
    fun gunzip(payload: ByteArray): String? = runCatching {
        GZIPInputStream(ByteArrayInputStream(payload)).use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull()
}
