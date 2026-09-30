package com.kallan.naistudio.comic

import com.kallan.naistudio.desktop.platform.DesktopImageIo
import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.services.ComicPageExporter
import com.kallan.naistudio.state.AppState
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **高级漫画「导出 PSD」这条链路的钉子**（第 ⑧ 项；用户 2026-09-20：「加入导出功能，导出 psd 文件」✓）。
 *
 * 为什么要这一组：PSD 是"长度字段 + 大端整数"堆起来的二进制 —— 写错一个长度、少一个补位，
 * 文件在 Photoshop 里就是**打不开**而不是"显示有点不对"✗，肉眼看字节根本看不出来 ✓。
 * 所以这里不满足于"返回了一串非空字节"，而是用一个**只在测试里存在的最小 PSD 阅读器**
 * （[parseComicPsd] ✓，口径与 `PsdWriterTest` 那份一致 ✓）把结构读回来核对：
 * 头 4 字节 / 页尺寸与 `ComicPageExporter` 一致 / **层数 == 页面图层数** / 每层名字与矩形 ✓。
 *
 * 覆盖五条：
 *  1. **真出一条 PSD**：底板（真造一张 PNG 落盘 ✓）+ 一颗气泡 → 非 null、头 `8BPS`、
 *     宽高与几何一致、层数与层名（含中文 `luni`）对得上，并把样例落到 `build/psd-test/` ✓；
 *  2. **没有页 / 没有内容 → null 且不抛** ✓（原因要写进 `status` ✓）；
 *  3. **合并图与图层真的带像素** ✓（不是"结构对但全透明"✗）；
 *  4. **落到宿主给的 ref**（= 电脑端「另存为」选中的那个路径 ✓）—— 写出来的文件头同样是 `8BPS`，
 *     并且跑完会放开闸门 [AppState.comicPsdExporting] ✓；
 *  5. **图没了 / 写不进去的层要如实说话** ✓（整页导不出就是 null + 一句原因，不硬凑 ✗）。
 *
 * ⚠️ 档案是**隔离**的（`build.gradle.kts` 把 `%APPDATA%` / `user.home` 指到 `build/test-home` ✓），
 * 产物一律落 `build/psd-test/`（**build/ 下 ✓，绝不写进源码目录** ✗）；每个用例开头清自己那两个键 ✓。
 */
class ComicPsdExportTest {

    private val platform: Platform = desktopPlatform()

    /** 测试产物目录（**build/ 下 ✓**）—— 那条真 PSD 也落这儿，方便用 psd-tools 复核 ✓。 */
    private val dumpDir = File("build/psd-test")

    private fun dumpDir(): File {
        if (!dumpDir.isDirectory) dumpDir.mkdirs()
        return dumpDir
    }

    /** 每个用例都从干净的盘开始（隔离档案跨次保留，不清会有上一轮的页 / 图层残留 ✗）。 */
    private fun freshState(): AppState {
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .apply()
        return AppState(platform).also { it.ensureComicBoardLoaded() }
    }

    /**
     * 造一张**不透明**的小图落盘当底板 ✓（同 `DesktopSavePipelineTest` 那条：`Platform.images`
     * 编码 PNG → 写文件 ✓，测试里不依赖任何图片素材 ✗）。
     */
    private fun writeBasePng(width: Int, height: Int, name: String): String {
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val checker = if ((x / 10 + y / 10) % 2 == 0) 0x33 else 0xCC
            (0xFF shl 24) or (checker shl 16) or ((x * 255 / width) shl 8) or (y * 255 / height)
        }
        val image = DesktopImageIo.fromArgb(pixels, width, height)
        val bytes = DesktopImageIo.pngBytes(image)
        image.recycle()
        val file = File(dumpDir(), name)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

    // ------------------------------------------------------------------
    // 1) 真出一条 PSD：结构 + 层名 + 落盘样例
    // ------------------------------------------------------------------

    @Test
    fun a_real_psd_comes_out_with_one_layer_per_page_layer() = runBlocking {
        val state = freshState()
        val basePath = writeBasePng(200, 260, "src-base-200x260.png")
        state.setComicBoardBase(width = 200, height = 260, basePath = basePath)
        val bubbleId = requireNotNull(state.addComicBubble("ROUND", 20f, 30f, 80f, 50f)) {
            "先决条件：气泡应该建得出来"
        }
        state.updateComicBubble(bubbleId) { it.copy(text = "你好") }
        val page = requireNotNull(state.comicBoardPage) { "先决条件：这一页应该在" }

        val psd = requireNotNull(state.buildComicPsd()) { "有底板 + 气泡的一页必须能出 PSD" }
        assertTrue("PSD 不该是空字节", psd.size > 0)
        assertEquals("头 4 字节必须是 8BPS", "8BPS", String(psd.copyOfRange(0, 4), Charsets.US_ASCII))

        val parsed = parseComicPsd(psd)
        assertEquals("格式版本", 1, parsed.version)
        assertEquals("通道数（R,G,B,A）", 4, parsed.channels)
        // 页尺寸 = `ComicPageExporter` 给的那一套（**同一套几何** ✓，不是测试里另算一个 ✗）
        val plan = ComicPageExporter.plan(page) { path -> platform.images.size(path) }
        assertEquals("宽必须等于几何给的页宽", plan.pageWidth, parsed.width)
        assertEquals("高必须等于几何给的页高", plan.pageHeight, parsed.height)

        assertEquals("层数必须 == 页面图层数", page.layers.size, parsed.layerCount)
        assertEquals(
            "层名（含中文）要按**从下到上**的顺序对得上",
            page.layers.map { it.name },
            parsed.layers.map { it.name },
        )
        assertEquals(
            "`luni` 里的 Unicode 名字也要对得上（中文名在 Photoshop 里就靠它 ✓）",
            page.layers.map { it.name },
            parsed.layers.map { it.unicodeName },
        )
        assertTrue("这一页至少要有两层（底板 + 气泡）—— 不然上面那条等于没测", parsed.layerCount >= 2)

        // 每层矩形都必须**落在页内** ✓：`cover` 出来的溢出部分在合成时就被裁掉了，
        // PSD 里照抄那个矩形的话层坐标会跑到页外（打开时看着「位置偏了」✗）
        parsed.layers.forEach { layer ->
            assertTrue(
                "层「${layer.name}」左上角跑到页外了：(${layer.left},${layer.top})",
                layer.left >= 0 && layer.top >= 0,
            )
            assertTrue(
                "层「${layer.name}」右下角超出页了：(${layer.right},${layer.bottom})，" +
                    "页 = ${parsed.width}×${parsed.height}",
                layer.right <= parsed.width && layer.bottom <= parsed.height,
            )
        }

        // 落盘（**build/psd-test/ ✓**）：给用户用 psd-tools / Pillow / Photoshop 复核 ✓
        val file = File(dumpDir(), "comic-page.psd")
        file.writeBytes(psd)
        assertTrue("样例文件要真的落盘：${file.absolutePath}", file.length() > 0)
        println("PSD 样例：${file.absolutePath}（${psd.size} 字节，${parsed.layerCount} 层）")
    }

    // ------------------------------------------------------------------
    // 2) 没有页 / 没有内容 → null（不抛 ✓）
    // ------------------------------------------------------------------

    @Test
    fun no_page_or_no_content_gives_null_instead_of_throwing() = runBlocking {
        val noPage = freshState()
        assertNull("还没有页的时候应当是 null（**不抛** ✓）", noPage.buildComicPsd())
        assertTrue("要说得出原因（status ✓），实际：${noPage.status}", noPage.status.isNotBlank())

        val blank = freshState()
        // 空白画布：有页、有那一层「底板」，但底板层没有图、也没有气泡 / 文本 → 没有可画的东西 ✓
        blank.setComicBoardBase(width = 64, height = 64)
        assertNull("空白页没有内容 → null ✓", blank.buildComicPsd())
        assertTrue("原因要写进 status：${blank.status}", blank.status.isNotBlank())
    }

    // ------------------------------------------------------------------
    // 3) 合并图与图层真的带像素（不是"结构对但全透明"✗）
    // ------------------------------------------------------------------

    @Test
    fun the_merged_image_and_the_base_layer_actually_carry_pixels() = runBlocking {
        val state = freshState()
        val basePath = writeBasePng(160, 160, "src-base-160.png")
        state.setComicBoardBase(width = 160, height = 160, basePath = basePath)

        val psd = requireNotNull(state.buildComicPsd()) { "一张不透明底板必须能出 PSD" }
        val parsed = parseComicPsd(psd)
        assertEquals("合并图像素数", parsed.width * parsed.height, parsed.composite.size)
        assertTrue(
            "合并图不该是全透明（有纸色 + 那张不透明底板 ✓）",
            parsed.composite.any { alphaOf(it) > 0 },
        )
        assertTrue(
            "至少有一层的像素非全透明 ✓",
            parsed.layers.any { layer -> layer.argb.any { alphaOf(it) > 0 } },
        )
        // 底板层（最底下那层）：160×160 的图 Fit 进 160×160 的页 = 正好铺满 ✓
        val base = parsed.layers.first()
        assertEquals("底板层应当正好铺满整页", parsed.width * parsed.height, base.width * base.height)
        assertTrue("底板层左上角应当有像素", alphaOf(base.argb[0]) > 0)
        assertTrue("底板层右下角应当有像素", alphaOf(base.argb[base.argb.size - 1]) > 0)
    }

    // ------------------------------------------------------------------
    // 4) 落到宿主给的 ref（= 电脑端「另存为」选中的那个路径 ✓）
    // ------------------------------------------------------------------

    @Test
    fun exporting_to_a_ref_writes_the_psd_and_releases_the_busy_gate() = runBlocking {
        val state = freshState()
        val basePath = writeBasePng(120, 120, "src-base-120.png")
        state.setComicBoardBase(width = 120, height = 120, basePath = basePath)
        val target = File(dumpDir(), "comic-page-saved.psd")
        if (target.exists()) target.delete()

        state.exportComicPsdTo(target.absolutePath)
        // ⚠️ `exportComicPsdTo` 走 `viewModelScope`（电脑端 = AWT EDT ✓）→ 等它写完再断言 ✓
        //（闸门是**同步**置上的 ✓，所以"闸门放开"就是"这一趟真的跑完了" —— 有上限，别挂死 ✗）
        withTimeout(60_000) {
            while (state.comicPsdExporting || !target.isFile) delay(50)
        }

        val written = target.readBytes()
        assertTrue("写出来的文件不该是空的", written.isNotEmpty())
        assertEquals("写出来的也得是 8BPS", "8BPS", String(written.copyOfRange(0, 4), Charsets.US_ASCII))
        assertFalse("跑完必须放开闸门（不然按钮一辈子是灰的 ✗）", state.comicPsdExporting)
        assertTrue("成功要把字节量说出来（KB ✓），实际：${state.status}", state.status.contains("KB"))
    }

    // ------------------------------------------------------------------
    // 5) 有东西写不进去时要如实说话（不硬凑一份"看着像成功"的文件 ✗）
    // ------------------------------------------------------------------

    @Test
    fun a_layer_whose_image_is_gone_is_reported_instead_of_faked() = runBlocking {
        val state = freshState()
        val basePath = writeBasePng(120, 120, "src-base-120x120.png")
        state.setComicBoardBase(width = 120, height = 120, basePath = basePath)
        // 把底板那张图**从盘上删掉**（模拟「文件没了 / 用户清理过目录」✓）
        assertTrue("先决条件：底板图应当在盘上", File(basePath).delete())

        val psd = state.buildComicPsd()
        assertNull("底板图都没了就不该硬凑一份 PSD 出来 ✗", psd)
        assertTrue("原因要写进 status：${state.status}", state.status.isNotBlank())
        assertEquals("一层都没写进去时跳过计数应当是 0（那是「整页不能导」，不是「少几层」✓）", 0, state.comicPsdSkippedLayers)
    }
}

// ---------------------------------------------------------------------------
// 只在测试里用的**最小 PSD 阅读器** ✓
//
// 刻意只认 `services/PsdWriter` 写出来的那一套结构（口径与 `PsdWriterTest` 那份一致 ✓）——
// 它不是通用 PSD 阅读器 ✗，只是把"这份文件到底写成了什么"原样读回来核对 ✓。
// ---------------------------------------------------------------------------

/** 大端游标 ✓。 */
private class PsdCursor(private val bytes: ByteArray) {
    var pos = 0
    val size: Int get() = bytes.size

    fun u8(): Int = bytes[pos++].toInt() and 0xFF

    fun u16(): Int = (u8() shl 8) or u8()

    fun i16(): Int {
        val value = u16()
        return if (value >= 0x8000) value - 0x10000 else value
    }

    fun u32(): Int = (u8() shl 24) or (u8() shl 16) or (u8() shl 8) or u8()

    fun take(count: Int): ByteArray {
        val out = bytes.copyOfRange(pos, pos + count)
        pos += count
        return out
    }

    fun ascii(count: Int): String = String(take(count), Charsets.US_ASCII)
}

/** 读回来的一层（记录信息 + 已解压的像素 ✓）。 */
private class ComicPsdLayer(
    val name: String,
    val unicodeName: String?,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val opacity: Int,
    val flags: Int,
) {
    var argb: IntArray = IntArray(0)

    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val visible: Boolean get() = (flags and 0x02) == 0
}

private class ComicParsedPsd(
    val signature: String,
    val version: Int,
    val channels: Int,
    val width: Int,
    val height: Int,
    val layerCount: Int,
    val layers: List<ComicPsdLayer>,
    val composite: IntArray,
)

private fun parseComicPsd(data: ByteArray): ComicParsedPsd {
    val cursor = PsdCursor(data)

    // ---- 文件头（固定 26 字节 ✓）----
    val signature = cursor.ascii(4)
    val version = cursor.u16()
    cursor.take(6)                       // reserved
    val channels = cursor.u16()
    val height = cursor.u32()
    val width = cursor.u32()
    cursor.u16()                         // depth
    cursor.u16()                         // colorMode
    assertEquals("文件头必须是固定 26 字节", 26, cursor.pos)

    // ---- Color Mode Data / Image Resources（本写出器都写长度 0 ✓）----
    val colorModeLength = cursor.u32()
    if (colorModeLength > 0) cursor.take(colorModeLength)
    val resourcesLength = cursor.u32()
    if (resourcesLength > 0) cursor.take(resourcesLength)

    // ---- Layer and Mask Information ----
    val layerMaskLength = cursor.u32()
    val layerMaskEnd = cursor.pos + layerMaskLength
    val layerInfoLength = cursor.u32()
    val layerInfoEnd = cursor.pos + layerInfoLength
    val layerCount = cursor.i16()
    assertTrue("层数必须是正数（每层都带 alpha ✓）", layerCount >= 0)

    val layers = ArrayList<ComicPsdLayer>(layerCount)
    // 每层通道的 id / 长度：层记录阶段先收着，通道数据阶段**按同样顺序**读 ✓
    val channelIdsPerLayer = ArrayList<List<Int>>(layerCount)
    val channelLengthsPerLayer = ArrayList<List<Int>>(layerCount)
    for (index in 0 until layerCount) {
        val top = cursor.u32()
        val left = cursor.u32()
        val bottom = cursor.u32()
        val right = cursor.u32()
        val channelCount = cursor.u16()
        val channelIds = ArrayList<Int>(channelCount)
        val channelLengths = ArrayList<Int>(channelCount)
        repeat(channelCount) {
            channelIds.add(cursor.i16())
            channelLengths.add(cursor.u32())
        }
        assertEquals("混合模式签名", "8BIM", cursor.ascii(4))
        assertEquals("混合模式键（正常 ✓）", "norm", cursor.ascii(4))
        val opacity = cursor.u8()
        cursor.u8()                      // clipping
        val flags = cursor.u8()
        cursor.u8()                      // filler

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
        while (consumed % 4 != 0) {      // Pascal 名字补到 4 字节边界 ✓
            cursor.u8()
            consumed++
        }
        var unicodeName: String? = null
        while (cursor.pos + 12 <= extraEnd) {   // 附加层信息块（本写出器只写 luni ✓）
            val blockSignature = cursor.ascii(4)
            val blockKey = cursor.ascii(4)
            val blockLength = cursor.u32()
            assertEquals("附加层信息块签名", "8BIM", blockSignature)
            val blockEnd = cursor.pos + blockLength
            if (blockKey == "luni") {
                val units = cursor.u32()
                unicodeName = String(cursor.take(units * 2), Charsets.UTF_16BE)
                cursor.take(2)           // 结束 0
            }
            cursor.pos = blockEnd
        }
        while (cursor.pos < extraEnd) cursor.u8()
        assertEquals("层记录的 extra 长度必须如实等于写出的字节数", extraEnd, cursor.pos)

        layers.add(ComicPsdLayer(name, unicodeName, left, top, right, bottom, opacity, flags))
        channelIdsPerLayer.add(channelIds)
        channelLengthsPerLayer.add(channelLengths)
    }

    // ---- 通道数据：顺序与层记录一致 ✓ ----
    for (index in layers.indices) {
        val layer = layers[index]
        val ids = channelIdsPerLayer[index]
        val lengths = channelLengthsPerLayer[index]
        val planes = HashMap<Int, ByteArray>()
        for (channel in ids.indices) {
            val channelStart = cursor.pos
            val compression = cursor.u16()
            val plane = ByteArray(layer.width * layer.height)
            when (compression) {
                0 -> cursor.take(plane.size).copyInto(plane)
                1 -> {
                    val counts = IntArray(layer.height) { cursor.u16() }   // 本通道自己的行长表 ✓
                    for (row in 0 until layer.height) {
                        unpackPsdRow(cursor, plane, row * layer.width, layer.width, counts[row])
                    }
                }
                else -> throw AssertionError("不认识的层压缩方式：$compression")
            }
            assertEquals(
                "channelDataLength 必须等于该通道实际写出的字节数",
                lengths[channel].toLong(),
                (cursor.pos - channelStart).toLong(),
            )
            planes[ids[channel]] = plane
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
    cursor.u32()                          // Global layer mask info 长度（0 ✓）
    assertEquals("层段必须正好用完", layerMaskEnd, cursor.pos)

    // ---- Image Data（合并图）----
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
                    unpackPsdRow(cursor, plane, row * width, width, counts[channel][row])
                }
                planes.add(plane)
            }
        }
        else -> throw AssertionError("不认识的合并图压缩方式：$imageCompression")
    }
    assertEquals("文件必须被正好读完（多一个字节都说明某个长度字段写错了 ✗）", data.size, cursor.pos)

    return ComicParsedPsd(
        signature = signature,
        version = version,
        channels = channels,
        width = width,
        height = height,
        layerCount = layerCount,
        layers = layers,
        composite = argbFromPlanes(
            planes[0],
            planes[1],
            planes[2],
            if (planes.size > 3) planes[3] else null,
        ),
    )
}

/** 按行长表里给的字节数解一行 PackBits ✓，并核对"正好用完" ✓。 */
private fun unpackPsdRow(cursor: PsdCursor, out: ByteArray, offset: Int, width: Int, byteCount: Int) {
    val end = cursor.pos + byteCount
    var written = 0
    while (written < width) {
        val header = cursor.u8()
        when {
            header <= 127 -> {
                val count = header + 1
                repeat(count) {
                    out[offset + written] = cursor.u8().toByte()
                    written++
                }
            }
            header >= 129 -> {
                val count = 257 - header
                val value = cursor.u8().toByte()
                repeat(count) {
                    out[offset + written] = value
                    written++
                }
            }
            else -> Unit
        }
    }
    assertEquals("一行的 PackBits 解出来必须正好等于行长表里写的字节数", end, cursor.pos)
}

private fun argbFromPlanes(
    red: ByteArray,
    green: ByteArray,
    blue: ByteArray,
    alpha: ByteArray?,
): IntArray {
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
