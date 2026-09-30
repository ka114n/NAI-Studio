package com.kallan.naistudio.services

import com.kallan.naistudio.models.AppSettings
import com.kallan.naistudio.models.BackupCodec
import com.kallan.naistudio.models.GenerateParams
import com.kallan.naistudio.models.StylePreset
import com.kallan.naistudio.models.StylePresetLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * 备份内嵌进图片（PNG `tEXt` 块）的读写。
 *
 * 对照 ComfyUI / NovelAI 的做法：图片照常能看，参数 JSON 跟在文本块里。
 * 这些用例是纯字节操作，不碰 Android。
 */
class PngMetadataTest {

    /** 造一张结构合法、内容无所谓的最小 PNG（签名 + IHDR + IDAT + IEND）。 */
    private fun minimalPng(): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        output.write(chunk("IHDR", ByteArray(13) { if (it == 3) 8 else 0 }))
        output.write(chunk("IDAT", ByteArray(4)))
        output.write(chunk("IEND", ByteArray(0)))
        return output.toByteArray()
    }

    private fun chunk(type: String, payload: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(intBytes(payload.size))
            write(type.toByteArray(Charsets.US_ASCII))
            write(payload)
            val crc = CRC32().apply {
                update(type.toByteArray(Charsets.US_ASCII))
                update(payload)
            }
            write(intBytes(crc.value.toInt()))
        }.toByteArray()

    private fun intBytes(value: Int) = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private fun backupJson(): String = BackupCodec.encode(
        bundle = BackupCodec.Bundle(
            settings = AppSettings(theme = "dark", i2iStrength = 0.42),
            params = GenerateParams(positivePrompt = "1girl, 中文提示词", width = 832),
            styleLibrary = StylePresetLibrary(
                presets = listOf(StylePreset(id = "p1", name = "厚涂", positive = "thick paint")),
            ),
        ),
        appVersion = "0.2.0",
        exportedAt = "2026-09-12T05:40:00Z",
        pretty = false,
    )

    @Test
    fun `isPng only accepts real png bytes`() {
        assertTrue(PngMetadata.isPng(minimalPng()))
        assertFalse(PngMetadata.isPng("{\"a\":1}".toByteArray()))
        assertFalse(PngMetadata.isPng(ByteArray(0)))
    }

    @Test
    fun `embedded backup can be read back unchanged in content`() {
        val json = backupJson()
        val embedded = PngMetadata.embedText(minimalPng(), json)
        assertTrue(embedded != null)

        val extracted = PngMetadata.extractBackupJson(embedded!!)
        assertTrue(extracted != null)
        // 读回来的是"非 ASCII 已转义"的等价 JSON：用 JSONObject 往返一次做规范化再比（结构一致）
        assertEquals(
            org.json.JSONObject(json).toString(),
            org.json.JSONObject(extracted!!).toString(),
        )
        // 而且能直接喂给解码器
        assertEquals("dark", BackupCodec.decode(extracted).settings?.theme)
    }

    @Test
    fun `chinese survives the round trip`() {
        val json = backupJson()
        val embedded = PngMetadata.embedText(minimalPng(), json)!!
        val extracted = PngMetadata.extractBackupJson(embedded)!!

        // 关键：中文在 tEXt 里被转义成 \uXXXX（Latin-1 安全），解析回来内容一致
        assertTrue(extracted.contains("\\u"))
        val decoded = BackupCodec.decode(extracted)
        assertEquals("厚涂", decoded.styleLibrary?.presets?.first()?.name)
        assertEquals("1girl, 中文提示词", decoded.params?.positivePrompt)
        assertEquals(0.42, decoded.settings?.i2iStrength ?: 0.0, 1e-9)
    }

    @Test
    fun `embedded chunk is placed before IDAT and image data stays`() {
        val png = minimalPng()
        val embedded = PngMetadata.embedText(png, backupJson())!!

        val chunks = PngMetadata.readTextChunks(embedded)
        assertEquals(1, chunks.size)
        assertEquals(PngMetadata.KEYWORD, chunks.first().first)

        // 原有数据块顺序不变（IHDR … IEND），且整张图变长了正好一个块的长度
        val types = allChunkTypes(embedded)
        assertEquals(listOf("IHDR", "tEXt", "IDAT", "IEND"), types)
        assertTrue(embedded.size > png.size)
    }

    @Test
    fun `a png without our backup returns null`() {
        assertNull(PngMetadata.extractBackupJson(minimalPng()))
        assertNull(PngMetadata.extractBackupJson("not a png".toByteArray()))
    }

    @Test
    fun `another tool's text chunk with our json is still recognised`() {
        // 模拟"别的工具重新内嵌过"：关键字不是我们的，内容还是我们的 JSON
        val payload = ByteArrayOutputStream().apply {
            write("other-tool".toByteArray(Charsets.ISO_8859_1))
            write(0)
            write(backupJson().toByteArray(Charsets.ISO_8859_1))
        }.toByteArray()
        val png = minimalPng()
        val output = ByteArrayOutputStream().apply {
            write(png, 0, 8)
            write(chunk("tEXt", payload))
            write(png, 8, png.size - 8)
        }.toByteArray()

        assertTrue(PngMetadata.extractBackupJson(output) != null)
    }

    @Test
    fun `base64 wrapped json is also accepted`() {
        val encoded = java.util.Base64.getEncoder().encodeToString(backupJson().toByteArray(Charsets.UTF_8))
        val payload = ByteArrayOutputStream().apply {
            write(PngMetadata.KEYWORD.toByteArray(Charsets.ISO_8859_1))
            write(0)
            write(encoded.toByteArray(Charsets.ISO_8859_1))
        }.toByteArray()
        val png = minimalPng()
        val output = ByteArrayOutputStream().apply {
            write(png, 0, 8)
            write(chunk("tEXt", payload))
            write(png, 8, png.size - 8)
        }.toByteArray()

        assertTrue(PngMetadata.extractBackupJson(output) != null)
    }

    @Test
    fun `garbage in a text chunk does not break reading`() {
        val payload = ByteArrayOutputStream().apply {
            write("junk".toByteArray(Charsets.ISO_8859_1))
            write(0)
            write("not json at all".toByteArray(Charsets.ISO_8859_1))
        }.toByteArray()
        val png = minimalPng()
        val output = ByteArrayOutputStream().apply {
            write(png, 0, 8)
            write(chunk("tEXt", payload))
            write(png, 8, png.size - 8)
        }.toByteArray()

        assertNull(PngMetadata.extractBackupJson(output))
        assertEquals(1, PngMetadata.readTextChunks(output).size)
    }

    @Test
    fun `cannot embed into something that is not a png`() {
        assertNull(PngMetadata.embedText("nope".toByteArray(), backupJson()))
    }

    private fun allChunkTypes(png: ByteArray): List<String> {
        val types = mutableListOf<String>()
        var offset = 8
        while (offset + 12 <= png.size) {
            val length = ((png[offset].toInt() and 0xff) shl 24) or
                ((png[offset + 1].toInt() and 0xff) shl 16) or
                ((png[offset + 2].toInt() and 0xff) shl 8) or
                (png[offset + 3].toInt() and 0xff)
            val type = String(png, offset + 4, 4, Charsets.US_ASCII)
            types.add(type)
            offset += 12 + length
            if (type == "IEND") break
        }
        return types
    }
}
