package com.kallan.naistudio.services

import java.io.ByteArrayOutputStream

/**
 * **多图层 PSD（Photoshop 原生格式）写出器** —— 纯 Kotlin / JVM、**零依赖**、不碰任何平台 API
 * （不用 `java.awt` / `javax.imageio` ✓），所以手机端与电脑端编的是同一份代码 ✓。
 *
 * 为什么自己写：仓库离线、Gradle 缓存里没有现成的 PSD 库，而「导出 PSD」要的恰恰是
 * **图层** —— PNG 给不了 ✓。这里按 Adobe《Photoshop File Formats Specification》(2019-11)
 * 逐段写字节 ✓，除 RLE 外不做任何花活 ✗。
 *
 * ## 文件骨架（每个长度字段都**如实等于**后面写出的字节数 ✓）
 * ```
 * 文件头 26 字节                     8BPS / version 1 / 6×0 / channels=4 / 高 / 宽 / depth=8 / mode=3
 * Color Mode Data        长度 0      RGB 不需要调色板 ✓
 * Image Resources        长度 0      不写资源块（分辨率、缩略图都不是必需的 ✓）
 * Layer and Mask Info    长度 4 + 层信息 + 4
 *   ├ Layer info         长度 2 + 层记录×N + 通道数据×N（尾部补 0 到 4 字节边界 ✓）
 *   │   ├ layer count    有符号 2 字节：**我们每层都带 alpha → 写正数** ✓（负数那套不用 ✗）
 *   │   ├ 层记录 × N      矩形 / 通道表 / 8BIM+norm / 不透明度 / flags / extra
 *   │   └ 通道数据 × N    每层 0(R),1(G),2(B),-1(alpha)，**顺序与层记录一致** ✓
 *   └ Global layer mask info 长度 0
 * Image Data             合并图：compression(2) + 通道平面 R→G→B→A（Photoshop 打开时先看它 ✓）
 * ```
 *
 * ## 像素口径
 * 一律 **非预乘 ARGB**，`Int` 下标 = `y * width + x`，与 [ComicComposer.blendOver] 同一套口径 ✓。
 * `alpha == 0` 的像素 **RGB 也照原样写** ✓（不预乘、不归零 ✗）—— Photoshop 里
 * "透明的红" 和 "透明的黑" 是两种数据，抹平了就回不来了。
 *
 * ## RLE（PackBits）在文件里出现在**三个**地方，写法**不一样**（最容易写错的一处 ⚠️）
 * 1. **层通道数据**：每个通道**自成一包** —— `compression(2)` + **该通道自己的**行字节数表
 *    （高 × 2 字节）+ 该通道的 PackBits 数据；层记录里 `channelDataLength` = 2 + 上面这些 ✓。
 *    依据：层记录给每个通道都写了长度，读文件的人就是**按这个长度逐通道切片**的
 *    （psd_tools `ChannelDataList.read` → `ChannelData.read(fp, length - 2)`，
 *    ag-psd / GIMP 的 psd 插件同样按通道切片）—— 所以通道的行长表**必须**待在自己的包里 ✓。
 * 2. **合并图 Image Data**：**全通道的行长表先写在一起**（高 × 4 通道 × 2 字节），
 *    再写全部通道的 PackBits 数据 ✓ —— 这一段才是"表在前、数据在后"的那个写法 ✓。
 * 3. **PackBits 单行**：≥3 个重复字节 → 重复包（1 字节头 = `257 - 重复数`，重复数 3..128）；
 *    否则字面包（头 = 字节数 - 1，最长 128）✓；行宽 > 0 时每行**至少 1 个包** ✓，
 *    行与行之间不跨行 ✓（Photoshop 按行表切分）。
 */
object PsdWriter {

    /** PSD v1 单边最大像素（规范 1..30000；PSB 才是 300000）✓。 */
    private const val MAX_DIMENSION = 30_000

    /** 压缩标志：0 = 原样，1 = RLE(PackBits) ✓。 */
    private const val COMPRESSION_RAW = 0
    private const val COMPRESSION_RLE = 1

    /** 每层固定 4 个通道：0(R), 1(G), 2(B), -1(alpha) ✓。 */
    private val CHANNEL_IDS = intArrayOf(0, 1, 2, -1)

    /**
     * 一层：名字 + 在文档里的位置尺寸 + **非预乘** ARGB 像素（下标 = `y * width + x` ✓）。
     *
     * @param x/y     左上角在文档里的落点（允许负值 / 超出画布 ✓ —— PSD 的层矩形本来就是文档坐标）
     * @param argb    **非预乘** ARGB，`size` 必须 == `width * height` ✓
     * @param opacity 0..255（越界会被夹到范围内 ✓）
     */
    data class Layer(
        val name: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val argb: IntArray,
        val visible: Boolean = true,
        val opacity: Int = 255,
    )

    /**
     * 写出整份 PSD。
     *
     * @param composite 合并图（Photoshop 打开时先显示它 ✓），`size` 必须 == `width * height` ✓
     * @param layers    **从下到上**排好序 ✓（和 [ComicLayerStack] 一样的顺序）
     * @param rle       true = 层通道与合并图都用 RLE(PackBits) ✓；false = 全部原样 ✓
     * @return 完整 PSD 字节；参数非法（尺寸 ≤0 / 超 30000 / 数组长度不对 / 层数为 0 / 层数超 int16）→ `null` ✓（**不抛** ✗）
     */
    fun write(
        width: Int,
        height: Int,
        composite: IntArray,
        layers: List<Layer>,
        rle: Boolean = true,
    ): ByteArray? {
        if (width <= 0 || height <= 0) return null
        if (width > MAX_DIMENSION || height > MAX_DIMENSION) return null
        val pixels = width.toLong() * height.toLong()
        if (composite.size.toLong() != pixels) return null
        if (layers.isEmpty() || layers.size > Short.MAX_VALUE) return null
        for (layer in layers) {
            if (layer.width <= 0 || layer.height <= 0) return null
            if (layer.width > MAX_DIMENSION || layer.height > MAX_DIMENSION) return null
            if (layer.argb.size.toLong() != layer.width.toLong() * layer.height.toLong()) return null
        }

        // ① 先把每层每个通道的"压缩块"编好 —— 层记录里的 channelDataLength 要写它的大小 ✓
        val channelBlocks = layers.map { layer ->
            planesOf(layer.argb, layer.width, layer.height).map { plane ->
                encodeChannel(plane, layer.width, layer.height, rle)
            }
        }

        val out = ByteArrayOutputStream(1 shl 16)

        // ② 文件头（26 字节，全大端 ✓）
        out.ascii("8BPS")
        out.u16(1)          // version
        out.zeros(6)        // reserved
        out.u16(4)          // channels：合并图 = R,G,B,A ✓
        out.s32(height)
        out.s32(width)
        out.u16(8)          // depth = 8 bit/通道
        out.u16(3)          // colorMode = RGB

        // ③ Color Mode Data + Image Resources（各只写长度 0 ✓ —— 长度字段本身不能省 ✗）
        out.s32(0)
        out.s32(0)

        // ④ Layer and Mask Information：长度 = layerInfo 长度字段(4) + 层信息 + 全局蒙版信息(4) ✓
        val layerInfo = ByteArrayOutputStream(1 shl 12)
        layerInfo.u16(layers.size)                                   // 正数：每层都带 alpha ✓
        for (index in layers.indices) layerInfo.layerRecord(layers[index], channelBlocks[index])
        for (index in layers.indices) {                              // 通道数据顺序 == 层记录顺序 ✓
            for (block in channelBlocks[index]) layerInfo.write(block, 0, block.size)
        }
        padTo(layerInfo, 4)                                          // 层信息尾部补 0 到 4 字节边界 ✓
        val layerInfoBytes = layerInfo.toByteArray()

        out.s32(4 + layerInfoBytes.size + 4)
        out.s32(layerInfoBytes.size)
        out.write(layerInfoBytes, 0, layerInfoBytes.size)
        out.s32(0)                                                   // Global layer mask info 长度 0 ✓

        // ⑤ Image Data：合并图，按通道平面 R → G → B → A ✓
        out.u16(if (rle) COMPRESSION_RLE else COMPRESSION_RAW)
        val compositePlanes = planesOf(composite, width, height)
        if (rle) {
            val packed = compositePlanes.map { packPlane(it, width, height) }
            for (row in packed) {                                    // 全通道的行长表先写在一起 ✓
                for (count in row.counts) out.u16(count)
            }
            for (row in packed) out.write(row.data, 0, row.data.size) // 再写全部通道的 PackBits 数据 ✓
        } else {
            for (plane in compositePlanes) out.write(plane, 0, plane.size)
        }

        return out.toByteArray()
    }

    // ------------------------------------------------------------------
    // 层记录
    // ------------------------------------------------------------------

    /**
     * 一层记录：矩形 + 通道表 + 混合模式 + 不透明度/flags + extra ✓。
     *
     * extra = 图层蒙版长度 0(4) + 混合范围长度 0(4) + Pascal 名字（**补到 4 字节边界** ✓）
     * + `luni` 附加层信息（Unicode 名字）✓，整体补到偶数（PSD 的 extra 是偶数字节 ✓）。
     */
    private fun ByteArrayOutputStream.layerRecord(layer: Layer, blocks: List<ByteArray>) {
        s32(layer.y)                        // top
        s32(layer.x)                        // left
        s32(layer.y + layer.height)         // bottom
        s32(layer.x + layer.width)          // right
        u16(CHANNEL_IDS.size)
        for (index in CHANNEL_IDS.indices) {
            i16(CHANNEL_IDS[index])         // 0,1,2,-1 ✓
            s32(blocks[index].size)          // channelDataLength：把压缩标志那 2 字节也算进去 ✓
        }
        ascii("8BIM")                       // 混合模式签名
        ascii("norm")                       // 混合模式键 = 正常
        u8(layer.opacity.coerceIn(0, 255))
        u8(0)                               // clipping = base
        u8(if (layer.visible) 0x08 else 0x0A) // bit1 = 可见位**反转**（不可见置 1 ✓）；bit3 = Photoshop 5+ 常规位 ✓
        u8(0)                               // filler

        val extra = ByteArrayOutputStream(64)
        extra.s32(0)                        // 图层蒙版数据长度 0
        extra.s32(0)                        // 混合范围长度 0
        extra.pascalName(layer.name)        // Pascal 名字，补到 4 字节边界 ✓
        extra.unicodeName(layer.name)       // 'luni'：CJK 名字要靠它才在 Photoshop 里显示正确 ✓
        if (extra.size() % 2 != 0) extra.u8(0)
        val extraBytes = extra.toByteArray()
        s32(extraBytes.size)                // extra data 长度（含上面所有字节 ✓）
        write(extraBytes, 0, extraBytes.size)
    }

    /** Pascal 名字：长度字节 + 名字字节，**补 0 到 4 字节边界** ✓（名字按 UTF-8 存，最长 255 字节 ✓）。 */
    private fun ByteArrayOutputStream.pascalName(name: String) {
        val bytes = name.toByteArray(Charsets.UTF_8)
        val length = minOf(bytes.size, 255)
        u8(length)
        write(bytes, 0, length)
        padTo(this, 4, base = length + 1)
    }

    /**
     * 附加层信息块 `8BIM` + `luni` + 长度 + Unicode 字符串 ✓ —— 层记录里的 Pascal 名字是
     * 老式单字节串，中文名（「气泡 1」）只有这个块才能原样带过去 ✓。
     * 数据 = 4 字节长度（UTF-16 码元个数）+ UTF-16BE 字符 + 2 字节结束 0 ✓。
     */
    private fun ByteArrayOutputStream.unicodeName(name: String) {
        val utf16 = name.toByteArray(Charsets.UTF_16BE)
        ascii("8BIM")
        ascii("luni")
        s32(4 + utf16.size + 2)
        s32(utf16.size / 2)
        write(utf16, 0, utf16.size)
        u16(0)
    }

    // ------------------------------------------------------------------
    // 像素与压缩
    // ------------------------------------------------------------------

    /** ARGB → 4 个通道平面 R,G,B,A ✓（**非预乘照原样** ✓）。 */
    private fun planesOf(argb: IntArray, width: Int, height: Int): List<ByteArray> {
        val count = width * height
        val red = ByteArray(count)
        val green = ByteArray(count)
        val blue = ByteArray(count)
        val alpha = ByteArray(count)
        for (index in 0 until count) {
            val pixel = argb[index]
            alpha[index] = ((pixel ushr 24) and 0xFF).toByte()
            red[index] = ((pixel ushr 16) and 0xFF).toByte()
            green[index] = ((pixel ushr 8) and 0xFF).toByte()
            blue[index] = (pixel and 0xFF).toByte()
        }
        return listOf(red, green, blue, alpha)
    }

    /** 一个通道的压缩块：`compression(2)` + 数据 ✓（长度字段要写的就是这个 ByteArray 的大小 ✓）。 */
    private fun encodeChannel(plane: ByteArray, width: Int, height: Int, rle: Boolean): ByteArray {
        val block = ByteArrayOutputStream(plane.size + 8)
        if (!rle) {
            block.u16(COMPRESSION_RAW)
            block.write(plane, 0, plane.size)
            return block.toByteArray()
        }
        block.u16(COMPRESSION_RLE)
        val packed = packPlane(plane, width, height)
        for (count in packed.counts) block.u16(count)   // 本通道自己的行长表 ✓
        block.write(packed.data, 0, packed.data.size)   // 随后才是本通道的数据 ✓
        return block.toByteArray()
    }

    /** 一个通道平面压完的样子：每行的字节数（2 字节 × 高）+ 拼在一起的 PackBits 数据 ✓。 */
    private class Packed(val counts: IntArray, val data: ByteArray)

    /** 按行压 PackBits ✓ —— 每行独立、绝不跨行 ✓。 */
    private fun packPlane(plane: ByteArray, width: Int, height: Int): Packed {
        val counts = IntArray(height)
        val data = ByteArrayOutputStream(plane.size + plane.size / 64 + 16)
        for (row in 0 until height) {
            val packed = packBits(plane, row * width, width)
            counts[row] = packed.size
            data.write(packed, 0, packed.size)
        }
        return Packed(counts, data.toByteArray())
    }

    /**
     * PackBits 单行编码 ✓：
     * · ≥3 个相同字节 → 重复包：头 = `257 - 重复数`（即 -(重复数-1) 的有符号字节，重复数 3..128）+ 那一个字节 ✓
     * · 否则字面包：头 = 字节数 - 1（0..127，即最长 128 ✓）+ 原文 ✓
     * · 行宽 > 0 时**至少 1 个包** ✓（字面包的下界保证不会写出 0 长度包 ✓）
     */
    private fun packBits(data: ByteArray, from: Int, length: Int): ByteArray {
        val out = ByteArrayOutputStream(length + length / 128 + 2)
        val end = from + length
        var index = from
        while (index < end) {
            var run = 1
            while (index + run < end && run < 128 && data[index + run] == data[index]) run++
            if (run >= 3) {
                out.write(257 - run)                 // 有符号字节：-(run - 1)
                out.write(data[index].toInt())
                index += run
            } else {
                val start = index
                var literal = 0
                while (index < end && literal < 128) {
                    // 一旦"从这里开始的 3 个字节相同"，就收手，留给下一轮的重复包 ✓
                    if (index + 2 < end && data[index] == data[index + 1] && data[index] == data[index + 2]) break
                    index++
                    literal++
                }
                out.write(literal - 1)               // literal 至少 1 ✓
                for (i in start until start + literal) out.write(data[i].toInt())
            }
        }
        return out.toByteArray()
    }

    // ------------------------------------------------------------------
    // 大端小工具（PSD 全部整数都是大端 ✓）
    // ------------------------------------------------------------------

    private fun ByteArrayOutputStream.u8(value: Int) {
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.u16(value: Int) {
        u8((value ushr 8) and 0xFF)
        u8(value and 0xFF)
    }

    /** 有符号 2 字节（通道 id 里要写 -1 ✓）。 */
    private fun ByteArrayOutputStream.i16(value: Int) = u16(value and 0xFFFF)

    private fun ByteArrayOutputStream.s32(value: Int) {
        u8((value ushr 24) and 0xFF)
        u8((value ushr 16) and 0xFF)
        u8((value ushr 8) and 0xFF)
        u8(value and 0xFF)
    }

    private fun ByteArrayOutputStream.ascii(text: String) {
        val bytes = text.toByteArray(Charsets.US_ASCII)
        write(bytes, 0, bytes.size)
    }

    private fun ByteArrayOutputStream.zeros(count: Int) {
        repeat(count) { u8(0) }
    }

    /** 把 [stream] 补 0 到 [multiple] 的整数倍 ✓（[base] 用于"刚写了 n 字节"的场景 ✓）。 */
    private fun padTo(stream: ByteArrayOutputStream, multiple: Int, base: Int = stream.size()) {
        var written = base
        while (written % multiple != 0) {
            stream.u8(0)
            written++
        }
    }
}
