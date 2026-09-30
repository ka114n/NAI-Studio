package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.state.AppState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * **「网页笔刷」收图那条路**（第 ㉕ 批 ✓；用户口径「直接搬，不修」✓）。
 *
 * 钉死三件事（这三件就是"采用"这个动作的全部内容 ✓）：
 *  ① 那张 PNG **真落盘**了（且字节**逐字不变** —— 我们不对它做任何重采样 ✗）；
 *  ② 页上**真的多了一个 PAINT 层**，它的 `imagePath` 指向刚写的那个文件 ✓
 *     （⇒ 关掉软件再打开，`loadComicPaintSession` 还能把它读回页大小像素 ✓，不会"画完就没了" ✗）；
 *  ③ 建完**当场选中**它 ✓（不然用户"采用完接着画"会画到别层上 ✗）。
 *
 * 上游那一半（CEF 初始化 → 加载网页 → 按钮导出 PNG）**没有窗口就验不了** ✗，
 * 由探针 `WebBrushProbe.kt` 负责（实测 `READY:1024x1024` → `PROBE_ADOPT=24664` ✓）。
 */
class WebBrushAdoptTest {

    private val platform: Platform = desktopPlatform()

    /**
     * ⚠️ **必须自己保证"有一页"** ✗：整套测试共用一个隔离档案（`build/test-home`），
     * 而页表是**存在 prefs 里**的（`ComicBoardStore.KEY_PAGES` ✓）—— 别的测试跑完可能
     * 在盘上留了一张**空的页表**，于是这里的 `comicBoardPage` 是 null，采用就会如实返回
     * false（第一版就是这么挂的：单跑绿、全量红 ✓）。没有页就自建一页，和用户点「新建」同一条路 ✓。
     */
    private fun newState(): AppState = AppState(platform).also { state ->
        state.ensureComicBoardLoaded()
        if (state.comicBoardPage == null) state.setComicBoardBase(2480, 3508, basePath = null)
    }

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // 画点认得出的东西：不透明红 + 一条斜线（免得写出个全空的图，那样"字节相等"就没意义了）
                image.setRGB(x, y, if (x == y) 0xFF102030.toInt() else 0xFFFF0000.toInt())
            }
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    @Test
    fun adopt_writes_the_png_verbatim_and_adds_a_selected_paint_layer() {
        val state = newState()
        val page = requireNotNull(state.comicBoardPage) { "先决条件：这一页应该在" }
        val idsBefore = page.layers.map { it.id }.toSet()
        val png = pngBytes(64, 64)
        assertTrue("小图上采用必须成功", png.isNotEmpty())

        assertTrue("采用必须返回 true（否则界面上会 toast 说失败）", state.adoptWebBrushImage(png))

        val after = requireNotNull(state.comicBoardPage)
        assertEquals("页上应该正好多一层", idsBefore.size + 1, after.layers.size)
        val added = after.layers.firstOrNull { it.id !in idsBefore }
        assertNotNull("必须找得到新增的那一层", added)
        val layer = requireNotNull(added)
        assertEquals("收进来的应该是普通绘画层", ComicLayer.Kind.PAINT, layer.kind)
        val path = requireNotNull(layer.imagePath) { "绘画层必须有图（imagePath 是它的像素来源）" }
        val file = File(path)
        assertTrue("图要真的落在盘上：$path", file.isFile)
        assertArrayEquals("落盘字节必须与收到的一模一样（不做任何重编码）", png, file.readBytes())
        assertEquals("建完要当场选中它", layer.id, state.comicPaintLayerId)
    }

    @Test
    fun adopt_keeps_every_previous_layer() {
        val state = newState()
        val before = requireNotNull(state.comicBoardPage).layers.map { it.id }
        assertTrue(state.adoptWebBrushImage(pngBytes(32, 32)))
        assertTrue(state.adoptWebBrushImage(pngBytes(32, 32)))
        val after = requireNotNull(state.comicBoardPage).layers.map { it.id }
        assertEquals("两次采用 = 加两层，一层都不许丢", before.size + 2, after.size)
        before.forEach { id -> assertTrue("老层 $id 不见了", after.contains(id)) }
    }
}
