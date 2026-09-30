package com.kallan.naistudio.services

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PsdWriter] 的钉子：**把写出来的字节用一个只在测试里存在的最小 PSD 解析器读回来逐段核对** ✓。
 *
 * 为什么值得这么测：PSD 全是"长度字段 + 大端整数"，写错一个长度、少一个补位，
 * 文件在 Photoshop 里就是"打不开"而不是"显示有点不对" —— 肉眼看字节根本看不出 ✗。
 * 所以这里把结构钉死（层数 / 名字 / 矩形 / flags / 每通道长度 / 逐像素）✓。
 *
 * 这个解析器**刻意只认本文件写出来的那套结构** ✓，不是通用 PSD 阅读器 ✗。
 */
class PsdWriterTest {

    private val docWidth = 3
    private val docHeight = 2

    // ------------------------------------------------------------------
    // 夹具：3×2 文档 + 两层（含半透明像素、A=0 但 RGB 非零的像素、中文名字）✓
    // ------------------------------------------------------------------

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        (alpha shl 24) or (red shl 16) or (green shl 8) or blue

    /** 底层：铺满 3×2；最后一个像素 **A=0 但 RGB 非零** ✓ —— 钉住"非预乘照原样写" ✓。 */
    private fun bottomLayer() = PsdWriter.Layer(
        name = "底 层",
        x = 0, y = 0, width = docWidth, height = docHeight,
        argb = intArrayOf(
            argb(255, 255, 0, 0), argb(255, 0, 255, 0), argb(255, 0, 0, 255),
            argb(255, 255, 255, 0), argb(128, 0, 255, 255), argb(0, 0xAB, 0xCD, 0xEF),
        ),
    )

    /** 上层：2×2 放在 (1,0)，半透明 + 不可见 + 不透明度 200 ✓ → 钉住 flags 的可见位反转 ✓。 */
    private fun bubbleLayer() = PsdWriter.Layer(
        name = "气泡 1",
        x = 1, y = 0, width = 2, height = 2,
        argb = intArrayOf(
            argb(128, 255, 255, 255), argb(255, 10, 20, 30),
            argb(0, 1, 2, 3), argb(64, 200, 100, 50),
        ),
        visible = false,
        opacity = 200,
    )

    /** 合并图：和任何一层都不一样 ✓ —— 免得"把层数据当成合并图"也能蒙混过关 ✗。 */
    private val composite = IntArray(docWidth * docHeight) {
        argb(255, it * 10, 255 - it * 10, it)
    }

    private fun smallLayers() = listOf(bottomLayer(), bubbleLayer())

    // ------------------------------------------------------------------
    // 1) 文件头
    // ------------------------------------------------------------------

    @Test
    fun header_is_a_26_byte_rgb_psd_header() {
        val psd = requireNotNull(PsdWriter.write(docWidth, docHeight, composite, smallLayers()))
        val parsed = parsePsd(psd)

        assertEquals("签名", "8BPS", parsed.signature)
        assertEquals("版本", 1, parsed.version)
        assertArrayEquals("头里 6 个保留字节必须是 0", ByteArray(6), parsed.reserved)
        assertEquals("通道数（R,G,B,A）", 4, parsed.channels)
        assertEquals("宽", docWidth, parsed.width)
        assertEquals("高", docHeight, parsed.height)
        assertEquals("深度", 8, parsed.depth)
        assertEquals("色彩模式 RGB", 3, parsed.colorMode)
        assertEquals("Color Mode Data 段长度 0", 0, parsed.colorModeDataLength)
        assertEquals("Image Resources 段长度 0", 0, parsed.imageResourcesLength)
        // parsePsd 里已经钉了"读完头正好 26 字节"✓
    }

    // ------------------------------------------------------------------
    // 2) 段长度自洽（最能抓"长度字段写错"）
    // ------------------------------------------------------------------

    @Test
    fun every_section_length_adds_up_to_the_file_size() {
        val psd = requireNotNull(PsdWriter.write(docWidth, docHeight, composite, smallLayers()))
        val parsed = parsePsd(psd)

        // 头 26 + 色彩模式 4 + 资源 4 + 层段的**长度字段** 4 + 层段 + 图像数据（含 2 字节压缩标志）✓
        val sum = 26 + 4 + 4 + 4 + parsed.layerMaskLength + parsed.imageDataLength
        assertEquals("各段长度之和必须正好等于文件大小", psd.size.toLong(), sum.toLong())

        // 层段内部：长度字段(4) + 层信息 + 全局蒙版信息长度字段(4) ✓
        assertEquals(
            "层段长度 = 4 + 层信息长度 + 4",
            (4 + parsed.layerInfoLength + 4).toLong(),
            parsed.layerMaskLength.toLong(),
        )
        assertEquals("全局图层蒙版信息长度必须是 0", 0, parsed.globalLayerMaskLength)
        assertTrue("层信息长度必须覆盖所有层记录与通道数据", parsed.layerInfoLength > 0)
    }

    // ------------------------------------------------------------------
    // 3) 层记录：层数 / 名字（含非 ASCII）/ 矩形 / 不透明度 / 可见位
    // ------------------------------------------------------------------

    @Test
    fun layer_records_keep_count_rect_name_opacity_and_visibility() {
        val psd = requireNotNull(PsdWriter.write(docWidth, docHeight, composite, smallLayers()))
        val parsed = parsePsd(psd)

        assertEquals("层数", 2, parsed.layerCount)
        assertEquals(2, parsed.layers.size)

        val bottom = parsed.layers[0]
        assertEquals("Pascal 名字（非 ASCII）", "底 层", bottom.name)
        assertEquals("luni 里的 Unicode 名字", "底 层", bottom.unicodeName)
        assertEquals("通道顺序必须是 0,1,2,-1（每层都带 alpha）", listOf(0, 1, 2, -1), bottom.channelIds)
        assertEquals("top", 0, bottom.top)
        assertEquals("left", 0, bottom.left)
        assertEquals("bottom", docHeight, bottom.bottom)
        assertEquals("right", docWidth, bottom.right)
        assertEquals("不透明度", 255, bottom.opacity)
        assertEquals("混合模式签名", "8BIM", bottom.blendSignature)
        assertEquals("混合模式键", "norm", bottom.blendKey)
        assertTrue("默认可见", bottom.visible)

        val bubble = parsed.layers[1]
        assertEquals("带空格与数字的中文名", "气泡 1", bubble.name)
        assertEquals("气泡 1", bubble.unicodeName)
        assertEquals("left", 1, bubble.left)
        assertEquals("top", 0, bubble.top)
        assertEquals("right", 3, bubble.right)
        assertEquals("bottom", 2, bubble.bottom)
        assertEquals("不透明度", 200, bubble.opacity)
        assertFalse("visible = false 必须写在 flags 的 bit1（反转位）里", bubble.visible)
        assertEquals("bit1 必须是 1（不可见）", 0x02, bubble.flags and 0x02)
    }

    // ------------------------------------------------------------------
    // 4) 通道数据能还原：raw 与 RLE 两种模式都跑
    // ------------------------------------------------------------------

    @Test
    fun layer_channel_pixels_round_trip_in_raw_and_rle() {
        val layers = smallLayers()
        val dumpDir = File("build/psd-test")
        for (rle in listOf(false, true)) {
            val psd = requireNotNull(PsdWriter.write(docWidth, docHeight, composite, layers, rle = rle))
            val parsed = parsePsd(psd)
            assertEquals("层数", 2, parsed.layers.size)
            // 顺手把这份"最小但带坑"的文档也落盘（build/ 下 ✓）：A=0 但 RGB 非零、半透明、
            // 不可见层、中文名 —— 方便用别的工具（psd-tools / Pillow / Photoshop）肉眼复核 ✓
            if (dumpDir.isDirectory || dumpDir.mkdirs()) {
                File(dumpDir, if (rle) "layered-3x2-rle.psd" else "layered-3x2-raw.psd").writeBytes(psd)
            }
            for (index in layers.indices) {
                val expected = layers[index]
                val actual = parsed.layers[index]
                assertEquals(
                    "rle=$rle 第 $index 层每个通道的压缩标志要跟参数一致",
                    if (rle) 1 else 0,
                    actual.channelCompressions[0],
                )
                assertEquals("每层 4 个通道都要读到", 4, actual.channelCompressions.size)
                assertTrue(
                    "rle=$rle 第 $index 层四个通道的压缩标志必须一致：${actual.channelCompressions}",
                    actual.channelCompressions.all { it == actual.channelCompressions[0] },
                )
                assertArrayEquals(
                    "rle=$rle 第 $index 层（${expected.name}）必须逐像素还原，" +
                        "包括 A=0 但 RGB 非零的像素（非预乘照原样写）",
                    expected.argb,
                    actual.argb,
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // 5) 合并图能还原
    // ------------------------------------------------------------------

    @Test
    fun merged_image_round_trips_in_raw_and_rle() {
        for (rle in listOf(false, true)) {
            val psd = requireNotNull(PsdWriter.write(docWidth, docHeight, composite, smallLayers(), rle = rle))
            val parsed = parsePsd(psd)
            assertEquals("合并图的压缩标志", if (rle) 1 else 0, parsed.imageCompression)
            assertArrayEquals("合并图必须逐像素还原（R→G→B→A 四个平面）", composite, parsed.composite)
        }
    }

    // ------------------------------------------------------------------
    // 6) 非法输入返回 null，不抛
    // ------------------------------------------------------------------

    @Test
    fun invalid_arguments_return_null_instead_of_throwing() {
        val pixels = IntArray(4) { argb(255, 1, 2, 3) }
        val layer = PsdWriter.Layer("x", 0, 0, 2, 2, pixels)

        assertNull("宽 0", PsdWriter.write(0, 2, IntArray(0), listOf(layer)))
        assertNull("高 0", PsdWriter.write(2, 0, IntArray(0), listOf(layer)))
        assertNull("负尺寸", PsdWriter.write(-2, 2, pixels, listOf(layer)))
        assertNull("合并图数组长度不对", PsdWriter.write(2, 2, IntArray(3), listOf(layer)))
        assertNull("层数为 0", PsdWriter.write(2, 2, pixels, emptyList()))
        assertNull(
            "层像素数组长度不对",
            PsdWriter.write(2, 2, pixels, listOf(PsdWriter.Layer("y", 0, 0, 3, 2, pixels))),
        )
        assertNull(
            "层尺寸为 0",
            PsdWriter.write(2, 2, pixels, listOf(PsdWriter.Layer("z", 0, 0, 0, 0, IntArray(0)))),
        )
        assertNull(
            "超过 PSD v1 的 30000 单边上限",
            PsdWriter.write(30_001, 1, IntArray(30_001), listOf(layer)),
        )
        assertNotNull("合法参数必须给出字节", PsdWriter.write(2, 2, pixels, listOf(layer)))
    }

    // ------------------------------------------------------------------
    // 7) 真实感用例：64×64、3 层、RLE，并落盘到 build/psd-test/ 方便肉眼验
    // ------------------------------------------------------------------

    @Test
    fun realistic_64x64_three_layer_document_round_trips_and_is_dumped() {
        val size = 64
        val layers = listOf(
            makeLayer("底板", 0, 0, size, size) { x, y -> argb(255, (x / 8) * 32 % 256, (y / 8) * 32 % 256, 64) },
            makeLayer("气泡 1", 8, 8, 32, 24) { x, y -> argb(255, 255, (x / 4) * 32 % 256, (y / 4) * 32 % 256) },
            makeLayer("半透明层", 16, 16, 40, 40) { x, y -> argb(96, (x / 8) * 51 % 256, 20, (y / 8) * 51 % 256) },
        )
        val merged = IntArray(size * size) { index ->
            val x = index % size
            val y = index / size
            argb(255, (x / 16) * 64 % 256, (y / 16) * 64 % 256, (x + y) % 256)
        }

        val psd = requireNotNull(PsdWriter.write(size, size, merged, layers, rle = true))
        val parsed = parsePsd(psd)
        assertEquals("宽", size, parsed.width)
        assertEquals("高", size, parsed.height)
        assertEquals("层数", 3, parsed.layers.size)
        assertArrayEquals("合并图逐像素", merged, parsed.composite)
        for (index in layers.indices) {
            assertArrayEquals("第 $index 层（${layers[index].name}）逐像素", layers[index].argb, parsed.layers[index].argb)
        }
        val semiTransparent = parsed.layers[2].argb.count { alphaOf(it) in 1..254 }
        assertTrue("半透明像素要如实带过来（A=96）", semiTransparent > 0)

        // 落盘：测试产物目录（build/ 下 ✓），方便用别的工具肉眼验 ✓
        val dir = File("build/psd-test")
        assertTrue("测试产物目录要能建出来：${dir.absolutePath}", dir.isDirectory || dir.mkdirs())
        val rleFile = File(dir, "layered-64-rle.psd")
        rleFile.writeBytes(psd)
        val rawPsd = requireNotNull(PsdWriter.write(size, size, merged, layers, rle = false))
        File(dir, "layered-64-raw.psd").writeBytes(rawPsd)
        assertTrue("落盘的 PSD 不能是空文件", rleFile.length() > 0)
        assertTrue("RLE 版应该比原样版小（这批数据有重复块）", psd.size < rawPsd.size)
    }

    private fun alphaOf(pixel: Int): Int = (pixel ushr 24) and 0xFF

    private fun makeLayer(
        name: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        pixel: (Int, Int) -> Int,
    ): PsdWriter.Layer {
        val argb = IntArray(width * height)
        for (row in 0 until height) {
            for (col in 0 until width) argb[row * width + col] = pixel(col, row)
        }
        return PsdWriter.Layer(name, x, y, width, height, argb)
    }
}

// ---------------------------------------------------------------------------
// 只在测试里用的最小 PSD 解析器 ✓
// ---------------------------------------------------------------------------

/** 大端游标 ✓。 */
private class Cursor(private val bytes: ByteArray) {
    var pos: Int = 0
    val size: Int get() = bytes.size

    fun u8(): Int {
        check(pos < bytes.size) { "读越界：pos=$pos，总长=${bytes.size}" }
        return bytes[pos++].toInt() and 0xFF
    }

    fun u16(): Int = (u8() shl 8) or u8()

    fun i16(): Int {
        val value = u16()
        return if (value >= 0x8000) value - 0x10000 else value
    }

    fun u32(): Int = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()

    fun take(count: Int): ByteArray {
        check(pos + count <= bytes.size) { "读越界：要 $count 字节，只剩 ${bytes.size - pos}" }
        val out = bytes.copyOfRange(pos, pos + count)
        pos += count
        return out
    }

    fun ascii(count: Int): String = String(take(count), Charsets.US_ASCII)
}

/** 读回来的一层（含记录 + 已解压的像素）✓。 */
private class ParsedLayer(
    val name: String,
    val unicodeName: String?,
    val top: Int,
    val left: Int,
    val bottom: Int,
    val right: Int,
    val channelIds: List<Int>,
    val channelLengths: List<Int>,
    val blendSignature: String,
    val blendKey: String,
    val opacity: Int,
    val clipping: Int,
    val flags: Int,
    val extraLength: Int,
) {
    val channelCompressions = ArrayList<Int>()
    var argb: IntArray = IntArray(0)

    val visible: Boolean get() = (flags and 0x02) == 0
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

private class ParsedPsd(
    val signature: String,
    val version: Int,
    val reserved: ByteArray,
    val channels: Int,
    val width: Int,
    val height: Int,
    val depth: Int,
    val colorMode: Int,
    val colorModeDataLength: Int,
    val imageResourcesLength: Int,
    val layerMaskLength: Int,
    val layerInfoLength: Int,
    val layerCount: Int,
    val layers: List<ParsedLayer>,
    val globalLayerMaskLength: Int,
    val imageCompression: Int,
    /** 从合并图的压缩标志算起，直到文件末尾的字节数（含那 2 字节压缩标志 ✓）。 */
    val imageDataLength: Int,
    val composite: IntArray,
)

private fun parsePsd(data: ByteArray): ParsedPsd {
    val cursor = Cursor(data)

    // ---- 文件头 ----
    val signature = cursor.ascii(4)
    val version = cursor.u16()
    val reserved = cursor.take(6)
    val channels = cursor.u16()
    val height = cursor.u32()
    val width = cursor.u32()
    val depth = cursor.u16()
    val colorMode = cursor.u16()
    assertEquals("文件头必须是固定 26 字节（4 + 2 + 6 + 2 + 4 + 4 + 2 + 2）", 26, cursor.pos)

    // ---- Color Mode Data / Image Resources ----
    val colorModeDataLength = cursor.u32()
    if (colorModeDataLength > 0) cursor.take(colorModeDataLength)
    val imageResourcesLength = cursor.u32()
    if (imageResourcesLength > 0) cursor.take(imageResourcesLength)

    // ---- Layer and Mask Information ----
    val layerMaskLength = cursor.u32()
    val layerMaskEnd = cursor.pos + layerMaskLength
    val layerInfoLength = cursor.u32()
    val layerInfoEnd = cursor.pos + layerInfoLength
    val layerCount = cursor.i16()
    check(layerCount >= 0) { "本写出器只写正数层数（每层都带 alpha）" }

    val layers = ArrayList<ParsedLayer>(layerCount)
    for (index in 0 until layerCount) {
        val top = cursor.u32()
        val left = cursor.u32()
        val bottom = cursor.u32()
        val right = cursor.u32()
        val channelCount = cursor.u16()
        val ids = ArrayList<Int>(channelCount)
        val lengths = ArrayList<Int>(channelCount)
        repeat(channelCount) {
            ids.add(cursor.i16())
            lengths.add(cursor.u32())
        }
        val blendSignature = cursor.ascii(4)
        val blendKey = cursor.ascii(4)
        val opacity = cursor.u8()
        val clipping = cursor.u8()
        val flags = cursor.u8()
        cursor.u8() // filler

        val extraLength = cursor.u32()
        val extraStart = cursor.pos
        val extraEnd = extraStart + extraLength

        val maskLength = cursor.u32()
        if (maskLength > 0) cursor.take(maskLength)
        val rangesLength = cursor.u32()
        if (rangesLength > 0) cursor.take(rangesLength)

        val nameLength = cursor.u8()
        val name = String(cursor.take(nameLength), Charsets.UTF_8)
        var consumed = cursor.pos - extraStart
        while (consumed % 4 != 0) {           // Pascal 名字补到 4 字节边界 ✓
            cursor.u8()
            consumed++
        }

        var unicodeName: String? = null
        while (cursor.pos + 12 <= extraEnd) { // 附加层信息块（我们只写 luni ✓）
            val blockSignature = cursor.ascii(4)
            val blockKey = cursor.ascii(4)
            val blockLength = cursor.u32()
            assertEquals("附加层信息块签名", "8BIM", blockSignature)
            val blockEnd = cursor.pos + blockLength
            if (blockKey == "luni") {
                val units = cursor.u32()
                unicodeName = String(cursor.take(units * 2), Charsets.UTF_16BE)
                cursor.take(2) // 结束 0
            }
            cursor.pos = blockEnd
        }
        while (cursor.pos < extraEnd) cursor.u8()
        assertEquals("layer record 的 extra 长度必须如实等于写出的字节数", extraEnd, cursor.pos)

        layers.add(
            ParsedLayer(
                name = name,
                unicodeName = unicodeName,
                top = top,
                left = left,
                bottom = bottom,
                right = right,
                channelIds = ids,
                channelLengths = lengths,
                blendSignature = blendSignature,
                blendKey = blendKey,
                opacity = opacity,
                clipping = clipping,
                flags = flags,
                extraLength = extraLength,
            ),
        )
    }

    // ---- 通道数据：顺序与层记录一致 ✓ ----
    for (layer in layers) {
        val planes = HashMap<Int, ByteArray>()
        for (index in layer.channelIds.indices) {
            val channelStart = cursor.pos
            val compression = cursor.u16()
            val plane = ByteArray(layer.width * layer.height)
            when (compression) {
                0 -> cursor.take(plane.size).copyInto(plane)
                1 -> {
                    val counts = IntArray(layer.height) { cursor.u16() } // 本通道自己的行长表 ✓
                    for (row in 0 until layer.height) {
                        unpackRow(cursor, plane, row * layer.width, layer.width, counts[row])
                    }
                }
                else -> throw AssertionError("不认识的压缩方式：$compression")
            }
            assertEquals(
                "channelDataLength 必须等于该通道实际写出的字节数（含 2 字节压缩标志）",
                layer.channelLengths[index].toLong(),
                (cursor.pos - channelStart).toLong(),
            )
            layer.channelCompressions.add(compression)
            planes[layer.channelIds[index]] = plane
        }
        layer.argb = argbFromPlanes(
            planes.getValue(0),
            planes.getValue(1),
            planes.getValue(2),
            planes.getValue(-1),
        )
    }
    assertTrue("通道数据不能越过层信息段", cursor.pos <= layerInfoEnd)
    cursor.pos = layerInfoEnd
    val globalLayerMaskLength = cursor.u32()
    assertEquals("层段必须正好用完（层信息 + 全局蒙版信息长度字段）", layerMaskEnd, cursor.pos)

    // ---- Image Data（合并图）----
    val imageDataStart = cursor.pos
    val imageCompression = cursor.u16()
    val planeSize = width * height
    val planes = ArrayList<ByteArray>(channels)
    when (imageCompression) {
        0 -> repeat(channels) { planes.add(cursor.take(planeSize)) }
        1 -> {
            // 合并图是"**全通道**行长表先写在一起，再写全部通道的数据" ✓
            val counts = Array(channels) { IntArray(height) { cursor.u16() } }
            repeat(channels) { channel ->
                val plane = ByteArray(planeSize)
                for (row in 0 until height) {
                    unpackRow(cursor, plane, row * width, width, counts[channel][row])
                }
                planes.add(plane)
            }
        }
        else -> throw AssertionError("合并图不认识的压缩方式：$imageCompression")
    }
    val imageDataLength = cursor.pos - imageDataStart
    assertEquals("文件必须被正好读完（多一个字节都说明某个长度字段写错了）", data.size, cursor.pos)

    val composite = argbFromPlanes(
        planes[0],
        planes[1],
        planes[2],
        if (planes.size > 3) planes[3] else null,
    )

    return ParsedPsd(
        signature = signature,
        version = version,
        reserved = reserved,
        channels = channels,
        width = width,
        height = height,
        depth = depth,
        colorMode = colorMode,
        colorModeDataLength = colorModeDataLength,
        imageResourcesLength = imageResourcesLength,
        layerMaskLength = layerMaskLength,
        layerInfoLength = layerInfoLength,
        layerCount = layerCount,
        layers = layers,
        globalLayerMaskLength = globalLayerMaskLength,
        imageCompression = imageCompression,
        imageDataLength = imageDataLength,
        composite = composite,
    )
}

/** 按行长表里给的字节数解一行 PackBits ✓，并核对"正好用完" ✓。 */
private fun unpackRow(cursor: Cursor, out: ByteArray, offset: Int, width: Int, byteCount: Int) {
    val end = cursor.pos + byteCount
    var written = 0
    while (written < width) {
        val header = cursor.u8()
        when {
            header <= 127 -> {                       // 字面包
                val count = header + 1
                repeat(count) {
                    out[offset + written] = cursor.u8().toByte()
                    written++
                }
            }
            header >= 129 -> {                       // 重复包
                val count = 257 - header
                val value = cursor.u8().toByte()
                repeat(count) {
                    out[offset + written] = value
                    written++
                }
            }
            else -> Unit                            // 128 = 空操作
        }
    }
    assertEquals("一行的 PackBits 解出来必须正好等于行长表里写的字节数", end, cursor.pos)
}

private fun argbFromPlanes(red: ByteArray, green: ByteArray, blue: ByteArray, alpha: ByteArray?): IntArray {
    val out = IntArray(red.size)
    for (index in out.indices) {
        val a = if (alpha != null) alpha[index].toInt() and 0xFF else 255
        out[index] = (a shl 24) or
            ((red[index].toInt() and 0xFF) shl 16) or
            ((green[index].toInt() and 0xFF) shl 8) or
            (blue[index].toInt() and 0xFF)
    }
    return out
}
