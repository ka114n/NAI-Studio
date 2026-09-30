package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.COMIC_PAINT_LAYER_NAME
import com.kallan.naistudio.models.ComicBoardPage
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.models.ComicPanel
import com.kallan.naistudio.models.StrokeSpec
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.state.AppState
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **高级漫画 ⑬ 顶部画布编辑器**（用户 2026-09-20 两条口径）的钉子：
 *  · 「**顶部放入的是画布编辑器，可以选定图层或新建图层绘画**」✓；
 *  · 「**选定后画出的图不能被选定框拖拽或放大缩小，需要选择或套索才可以**」✓。
 *
 * 这一组**全部脱离界面**（`AppState` + `ImageEditSession` + 真文件系统 ✓）——
 * 因为这一段最容易出洋相的三件事全在状态层：
 *  1. **画完有没有真的落盘**（像素只在内存里的话，"收工 → 画的东西没了" ✗）；
 *  2. **`PAINT` 这一种新层会不会被静默删掉**（第 ⑥ 批踩过的坑：加一颗气泡就把
 *     "没主"的层扫光 ✗ —— 这一次是同一个坑的钉子 ✓）；
 *  3. **不可编的层（生成层 / 矢量层）有没有"话说"**（点了没反应最费解 ✗）。
 *
 * ⚠️ 档案是**隔离**的（`build.gradle.kts` 把 `%APPDATA%` / `user.home` 指到 `build/test-home` ✓）——
 * 写出来的图层 PNG 落在 `build/test-home` 下的 App 私有目录里，**绝不进仓库** ✗。
 * 每个用例开头清自己那两个键 ✓（隔离档案跨次保留，不清会有上一轮的残留 ✗）。
 */
class ComicPaintLayerTest {

    private val platform: Platform = desktopPlatform()

    private fun freshState(): AppState {
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .apply()
        return AppState(platform).also { it.ensureComicBoardLoaded() }
    }

    private fun baseLayerId(state: AppState): String {
        val page = requireNotNull(state.comicBoardPage) { "先决条件：这一页应该在" }
        return requireNotNull(page.layers.firstOrNull { it.kind == ComicLayer.Kind.BASE }) {
            "先决条件：底板层应该在（normalizedOverlays 保证有且只有一层 ✓）"
        }.id
    }

    private fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

    /** 把盘上那张图解回来（尺寸 + 像素 ✓）。 */
    private fun decode(path: String): Triple<Int, Int, IntArray> {
        val image = requireNotNull(platform.images.decodeFull(path)) { "这一层的图应该解得开：$path" }
        val width = image.width
        val height = image.height
        val pixels = platform.images.pixelsOf(image)
        image.recycle()
        return Triple(width, height, pixels)
    }

    /** 造一张**不透明**的小图落盘当底板 ✓（测试里不依赖任何图片素材 ✗；落 `build/` 下 ✓）。 */
    private fun writeBasePng(width: Int, height: Int, name: String): String {
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            (0xFF shl 24) or ((x * 255 / width) shl 16) or ((y * 255 / height) shl 8) or 0x80
        }
        val image = platform.images.fromArgb(pixels, width, height)
        val bytes = platform.images.pngBytes(image)
        image.recycle()
        val dir = File("build/psd-test").apply { if (!isDirectory) mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    /** 造一张**纯色**的小图落盘 ✓（第 ⑭ 批用：要能在像素里认出"这块就是原来那张生成图" ✓）。 */
    private fun writeSolidPng(width: Int, height: Int, color: Int, name: String): String {
        val image = platform.images.fromArgb(IntArray(width * height) { color }, width, height)
        val bytes = platform.images.pngBytes(image)
        image.recycle()
        val dir = File("build/psd-test").apply { if (!isDirectory) mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    // ------------------------------------------------------------------
    // a) 画一笔 → 收工 → **真的落盘**（尺寸 == 页大小、中心像素不透明 ✓）
    // ------------------------------------------------------------------

    @Test
    fun a_stroke_is_committed_to_the_layers_png_on_disk() {
        val state = freshState()
        // 空白画布（没有底板图 ✓）：会话从**全透明**的页开始 ✓
        state.setComicBoardBase(width = 200, height = 260)
        val layerId = baseLayerId(state)
        assertTrue("底板层必须能开始编辑", state.beginComicLayerPaint(layerId))

        val session = requireNotNull(state.comicPaintSession) { "开始之后就该有一段会话" }
        // 契约点名的口径：**直接喂 session** 的 beginStroke / strokeTo / endStroke ✓（画在正中 ✓）
        session.beginStroke(
            100f,
            130f,
            StrokeSpec(brushPixels = 60, color = 0xFF112233.toInt()),
        )
        session.strokeTo(101f, 131f)
        session.endStroke()
        assertTrue("画过之后应该算改过", session.hasChanges)
        assertEquals("版本号要跟着走（界面据此重建显示位图 ✓）", 1, state.comicPaintVersion)

        state.commitComicLayerPaint()

        // ---- 断言盘上那张图 ----
        val layer = requireNotNull(state.comicBoardLayers.firstOrNull { it.id == layerId })
        val path = requireNotNull(layer.imagePath) { "收工之后这一层必须指向一张图" }
        val file = File(path)
        assertTrue("这一层的图必须真的存在：${file.absolutePath}", file.isFile)
        assertTrue("图不该是空的", file.length() > 0)

        val (width, height, pixels) = decode(path)
        assertEquals("图宽必须 == 页宽", 200, width)
        assertEquals("图高必须 == 页高", 260, height)
        val center = pixels[130 * width + 100]
        assertTrue("画过的中心像素必须不透明（实际 alpha=${alphaOf(center)}）", alphaOf(center) > 0)
        // 没画过的角上还是**全透明**（全透明底 ✓ —— 不然"新层盖住整页"就说不清了 ✗）
        assertEquals("没画过的地方应当仍是全透明", 0, alphaOf(pixels[0]))

        // ---- 状态与底板路径 ----
        assertTrue("status 要说出已保存，实际：${state.status}", state.status.isNotBlank())
        assertEquals(
            "底板层还多一步：画布上看的是 page.basePath，它也得换成刚写的那张 ✓",
            path,
            state.comicBoardPage?.basePath,
        )
        assertNull("收工 = 这一段会话结束", state.comicPaintSession)
        assertNull("收工之后不该还挂着正在编辑哪一层", state.comicPaintLayerId)
        assertFalse("收工之后这些手势开关都该是关的", state.comicPaintOpen)
    }

    // ------------------------------------------------------------------
    // b) 新建绘画层：层数 +1 / kind == PAINT / JSON 往返还在 / normalizedOverlays 不删它
    // ------------------------------------------------------------------

    @Test
    fun a_new_paint_layer_survives_the_json_round_trip_and_normalized_overlays() {
        val state = freshState()
        state.setComicBoardBase(width = 320, height = 480)
        val before = state.comicBoardLayers.size

        val id = requireNotNull(state.createComicPaintLayer()) { "新建绘画层必须给得出 id" }

        assertEquals("层数应当 +1", before + 1, state.comicBoardLayers.size)
        val layer = requireNotNull(state.comicBoardLayers.firstOrNull { it.id == id })
        assertEquals("这一层必须是 PAINT（绘画层）", ComicLayer.Kind.PAINT, layer.kind)
        assertEquals("名字按固定文案走", COMIC_PAINT_LAYER_NAME, layer.name)
        assertEquals("新建即选中并开始编辑 ✓", id, state.comicPaintLayerId)
        assertNotNull("新建即有一段活会话 ✓", state.comicPaintSession)

        // ---- 换一个 AppState 实例 = "关掉应用再打开"（JSON 往返 ✓）----
        val reader = AppState(platform)
        reader.ensureComicBoardLoaded()
        val restored = requireNotNull(reader.comicBoardLayers.firstOrNull { it.id == id }) {
            "绘画层必须活过 JSON 往返（Kind 的存 / 读 ✓）"
        }
        assertEquals(ComicLayer.Kind.PAINT, restored.kind)
        assertNull("会话是内存里的东西，不该跨实例 ✓", reader.comicPaintSession)

        // ---- 第 ⑥ 批那个坑的钉子：加一颗气泡**不能**把绘画层静默删光 ----
        val bubbleId = reader.addComicBubble("ROUND", 20f, 30f, 120f, 80f)
        assertNotNull("先决条件：气泡应该建得出来", bubbleId)
        assertTrue(
            "normalizedOverlays() 必须像保留生成层一样保留绘画层 ✗（第 ⑥ 批的坑 ✓）",
            reader.comicBoardLayers.any { it.id == id && it.kind == ComicLayer.Kind.PAINT },
        )
        // 再读一次盘：规整后的结果也得落进 JSON ✓
        val reader3 = AppState(platform)
        reader3.ensureComicBoardLoaded()
        assertTrue(
            "落盘之后再读回来，绘画层还在 ✓",
            reader3.comicBoardLayers.any { it.id == id && it.kind == ComicLayer.Kind.PAINT },
        )
    }

    // ------------------------------------------------------------------
    // c) **生成层可以画**（第 ⑭ 批改口径：用户 2026-09-20「包括生成的图像」✓）
    //
    // ⚠️ 旧的这条是"生成层**拒绝**编辑 ✗" —— 第 ⑭ 批起**反过来**了 ✓：
    //    落笔前先把它 materialize 成页大小的像素（cover 进那一格的几何烘焙进去 ✓），
    //    收工时 kind 转成 `PAINT` ✓。
    // ⚠️ **本轮没跑测试**（用户「不跑冒烟，改完直接打包」✓）—— 这条按新口径写好了，但未经执行 ✓。
    // ------------------------------------------------------------------

    @Test
    fun a_generated_layer_can_be_painted_and_becomes_a_paint_layer() {
        // ---- 摆一页：底板 + 一格 + 一层"生成层"（挂那一格上、有一张真图 ✓）----
        val generatedPath = writeSolidPng(100, 100, GENERATED_IMAGE_COLOR, "paint-gen-100x100.png")
        val panel = ComicPanel(
            id = "panel-1",
            x = 20f,
            y = 30f,
            w = 160f,
            h = 200f,
            resultPath = generatedPath,
            order = 0,
        )
        val page = ComicBoardPage(
            id = "paint-gen-page",
            name = "测试页",
            baseWidth = 200,
            baseHeight = 260,
            panels = listOf(panel),
            layers = listOf(
                ComicLayer("base-1", "底板", ComicLayer.Kind.BASE),
                ComicLayer(
                    id = "gen-1",
                    name = "生成层",
                    kind = ComicLayer.Kind.GENERATED,
                    panelId = panel.id,
                    imagePath = generatedPath,
                ),
            ),
        )
        platform.kv.edit()
            .putString(ComicBoardStore.KEY_PAGES, ComicBoardStore.encodePages(listOf(page)))
            .putString(ComicBoardStore.KEY_CURRENT, page.id)
            .apply()

        val state = AppState(platform)
        state.ensureComicBoardLoaded()
        assertTrue(
            "先决条件：生成层应当在（normalizedOverlays 保留它 ✓）",
            state.comicBoardLayers.any { it.id == "gen-1" && it.kind == ComicLayer.Kind.GENERATED },
        )

        // ---- ① 能开起来（第 ⑭ 批之前这里是 false ✗）----
        assertTrue(
            "生成层现在**能画** ✓（用户 2026-09-20：「包括生成的图像」✓）",
            state.beginComicLayerPaint("gen-1"),
        )
        assertEquals("聚焦的就是这一层", "gen-1", state.comicPaintLayerId)
        val session = requireNotNull(state.comicPaintSession) { "开会话之后应当有一段会话" }
        assertEquals("materialize 出来的是**页大小**（宽 ✓）", 200, session.width)
        assertEquals("materialize 出来的是**页大小**（高 ✓）", 260, session.height)
        assertEquals(
            "原来那张生成图的内容**已经烘焙进像素** ✓（cover 进它自己那一格 ✓）",
            GENERATED_IMAGE_COLOR,
            session.pixels[100 * session.width + 100],
        )
        assertEquals(
            "图覆盖不到的地方还是**全透明** ✓（没有凭空多出一片颜色 ✓）",
            0,
            alphaOf(session.pixels[5 * session.width + 5]),
        )

        // ---- ② 画一笔（画在生成图覆盖不到的地方，两样东西才分得清 ✓）----
        state.chooseComicPaintColor(STROKE_COLOR)
        state.setComicPaintBrush(40)
        state.beginComicPaintStroke(100f, 245f)
        state.comicPaintStrokeTo(120f, 245f)
        state.endComicPaintStroke()
        assertTrue("先决条件：这一笔应当真的改了像素", session.hasChanges)

        // ---- ③ 收工 ----
        state.commitComicLayerPaint()

        val layer = requireNotNull(state.comicBoardLayers.firstOrNull { it.id == "gen-1" })
        assertEquals(
            "画过之后它**不再是生成层** ✓（几何已经烘焙进像素、它现在是普通绘画层 ✓）",
            ComicLayer.Kind.PAINT,
            layer.kind,
        )
        assertEquals("名字 / panelId 留着以便回溯 ✓", "panel-1", layer.panelId)
        val path = requireNotNull(layer.imagePath) { "收工之后这一层必须指向一张图" }
        assertTrue("落盘写的是一个**新文件**，没把原来那张生成图盖掉 ✓", path != generatedPath)
        assertTrue("原来那张生成图还在盘上（旧图不删 ✓）", File(generatedPath).isFile)

        val (width, height, pixels) = decode(path)
        assertEquals("落盘的图必须是**页大小**（宽 ✓）", 200, width)
        assertEquals("落盘的图必须是**页大小**（高 ✓）", 260, height)
        assertEquals(
            "原来那张生成图的内容**还在**（没被抹掉 ✓）",
            GENERATED_IMAGE_COLOR,
            pixels[100 * width + 100],
        )
        assertEquals(
            "新笔迹也在 ✓ —— 实际 alpha=${alphaOf(pixels[245 * width + 110])}",
            STROKE_COLOR,
            pixels[245 * width + 110],
        )
    }

    // ------------------------------------------------------------------
    // c2) **矢量层（气泡 / 文本）仍然不可编，并且有话说**（旧 c 的那份意图 ✓）
    // ------------------------------------------------------------------

    @Test
    fun a_bubble_layer_refuses_to_be_painted_and_says_why() {
        val state = freshState()
        state.setComicBoardBase(width = 200, height = 260)
        val bubbleId = requireNotNull(state.addComicBubble("ROUND", 20f, 30f, 120f, 80f)) {
            "先决条件：气泡应该建得出来"
        }
        assertTrue(
            "先决条件：气泡层应当在（`addComicOverlay` 会补一层 ✓）",
            state.comicBoardLayers.any { it.id == bubbleId && it.kind == ComicLayer.Kind.BUBBLE },
        )

        assertFalse(
            "气泡是**矢量层** → 不能画 ✗（这条口径没变 ✓）",
            state.beginComicLayerPaint(bubbleId),
        )
        assertNull("没开起来就**不该**留下会话", state.comicPaintSession)
        assertNull(state.comicPaintLayerId)
        assertTrue(
            "必须有话说 ✗（不许静默什么都不做）—— 实际 status：${state.status}",
            state.status.isNotBlank(),
        )
        assertNotNull("也要弹一句（toast ✓）", state.toastEvent)

        // 不存在的层同理：一律 false 且**不留会话** ✓
        assertFalse(state.beginComicLayerPaint("不存在的层"))
    }

    // ------------------------------------------------------------------
    // d) 撤销 / 重做**只活在这一段会话里**，而"收工"写的是**当前所见**
    // ------------------------------------------------------------------

    @Test
    fun undo_and_redo_live_only_in_the_session_and_the_commit_writes_what_you_see() {
        val state = freshState()
        state.setComicBoardBase(width = 200, height = 260)
        val layerId = baseLayerId(state)
        assertTrue(state.beginComicLayerPaint(layerId))

        // 走 AppState 的入口（界面用的就是它 ✓）：画一笔
        state.beginComicPaintStroke(100f, 130f)
        state.comicPaintStrokeTo(110f, 140f)
        state.endComicPaintStroke()
        assertTrue("画完一笔之后「撤销」该是可用的", state.comicPaintCanUndo)

        val session = requireNotNull(state.comicPaintSession) { "这一段会话应当还开着" }

        // ---- 撤销：像素回到画之前（**只在会话里 ✓，一个字节都没落盘** ✗）----
        state.undoComicPaint()
        assertTrue("撤销之后「重做」该是可用的", state.comicPaintCanRedo)
        assertTrue(
            "撤销之后画布上应当一个不透明像素都没有",
            session.pixels.all { alphaOf(it) == 0 },
        )

        // （会话还在、层也还没写盘 —— 撤销不落盘这条就是它 ✓）
        assertNull(
            "撤销本身**不写盘** ✗（这一层还没有图 ✓）",
            state.comicBoardLayers.first { it.id == layerId }.imagePath,
        )

        // ---- 重做：那一笔又回来了 ----
        state.redoComicPaint()
        assertTrue("重做之后应当又有像素了", session.pixels.any { alphaOf(it) > 0 })

        // ---- 收工：盘上写的就是**当前所见**（重做回来的那一笔 ✓）----
        state.commitComicLayerPaint()
        val path = requireNotNull(state.comicBoardLayers.first { it.id == layerId }.imagePath)
        val (_, _, pixels) = decode(path)
        assertTrue("收工写出来的应当是当前所见（重做回来的那一笔 ✓）", pixels.any { alphaOf(it) > 0 })
    }

    // ------------------------------------------------------------------
    // e) （加分）PSD 导出里**包含 PAINT 层**
    // ------------------------------------------------------------------

    @Test
    fun the_psd_export_includes_the_paint_layer() = runBlocking {
        val state = freshState()
        // 真造一张底板图（**不用空白画布** ✓）：空白底板层会被 PSD 那条当成"没图 → 跳过一层"，
        // 那样就分不清"少的是底板还是绘画层"了 ✗ —— 这里要的是**一层都不落** ✓
        val basePath = writeBasePng(160, 200, "paint-test-base-160x200.png")
        state.setComicBoardBase(width = 160, height = 200, basePath = basePath)
        val id = requireNotNull(state.createComicPaintLayer()) { "先决条件：绘画层要建得出来" }
        val session = requireNotNull(state.comicPaintSession) { "先决条件：会话要开起来" }
        session.beginStroke(80f, 100f, StrokeSpec(brushPixels = 40, color = 0xFF00AA55.toInt()))
        session.endStroke()
        state.commitComicLayerPaint()
        assertNotNull(
            "先决条件：绘画层应当已经有图（不然 PSD 里没它可写 ✓）",
            state.comicBoardLayers.first { it.id == id }.imagePath,
        )

        val psd = requireNotNull(state.buildComicPsd()) { "一页有图的绘画层必须能出 PSD" }
        assertEquals("头 4 字节必须是 8BPS", "8BPS", String(psd.copyOfRange(0, 4), Charsets.US_ASCII))
        assertEquals(
            "一层都不该被跳过（底板 + 绘画层两层都在 ✓）—— 实际跳过 ${state.comicPsdSkippedLayers} 层",
            0,
            state.comicPsdSkippedLayers,
        )

        // PSD 的层名走 `luni` 附加块（UTF-16BE ✓，见 `PsdWriter`）——
        // 这里就按这个口径在字节流里找「绘画层」✓（比重新实现一个 PSD 阅读器省得多 ✓）
        assertTrue(
            "PSD 里必须能找到绘画层那一层的名字（UTF-16BE 的「绘画层」✓）",
            psd.containsBytes(COMIC_PAINT_LAYER_NAME.toByteArray(Charsets.UTF_16BE)),
        )
    }
}

/** 第 ⑭ 批那两条用例用的两个可辨认的颜色（纯色底图 / 落笔色 ✓）。 */
private val GENERATED_IMAGE_COLOR = 0xFF3366CC.toInt()
private val STROKE_COLOR = 0xFF112233.toInt()

/** 字节流里找一段子序列（PSD 结构里核对"这一层在不在"用 ✓）。 */
private fun ByteArray.containsBytes(needle: ByteArray): Boolean {
    if (needle.isEmpty() || needle.size > size) return false
    outer@ for (start in 0..(size - needle.size)) {
        for (offset in needle.indices) {
            if (this[start + offset] != needle[offset]) continue@outer
        }
        return true
    }
    return false
}
