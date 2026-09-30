package com.kallan.naistudio.desktop.platform

import com.kallan.naistudio.models.ComicBoardPage
import com.kallan.naistudio.models.ComicBubble
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.models.ComicText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **电脑端的矢量层栅格化真的能出图**（高级漫画第 ⑦ 项拼页导出的那一厘米）。
 *
 * 为什么值得专门钉一条：这条链路上"看着能编译"和"真能出图"差得很远 ✗ ——
 * `ImageComposeScene` 要求 `Density(1f)`（否则气泡位置按 dp 缩放，导出会整体偏掉 ✗）、
 * 场景要**透明底**（否则一层白底会把下面那张图糊掉 ✗）。这些只有真渲一次才看得出来 ✓。
 *
 * 实测抓到过一条：`Image.toComposeImageBitmap()` 在渲染产物上会抛
 * `Failed to Image::makeFromBitmap` ✗ —— 所以出口改成 skia 自己的 `encodeToData(PNG)` ✓
 * （这条测试就是那次失败的回归钉子 ✓）。
 *
 * 这条测试**不开窗口** ✓（离屏渲染 ✓），但会初始化 skiko，所以比纯逻辑测试慢一点 ✓。
 */
class DesktopComicRasterTest {

    private val platform = desktopPlatform()

    private fun page(
        bubbles: List<ComicBubble> = emptyList(),
        texts: List<ComicText> = emptyList(),
        layers: List<ComicLayer>,
    ) = ComicBoardPage(
        id = "p1",
        name = "第 1 页",
        basePath = null,
        baseWidth = 200,
        baseHeight = 200,
        bubbles = bubbles,
        texts = texts,
        layers = layers,
    )

    /** 把 PNG 字节解成 ARGB 像素（失败 → null ✓）。 */
    private fun pixelsOf(png: ByteArray?): IntArray? {
        if (png == null) return null
        val image = platform.images.decodeBytes(png) ?: return null
        val pixels = platform.images.pixelsOf(image)
        image.recycle()
        return pixels
    }

    @Test
    fun a_bubble_layer_comes_back_as_a_transparent_png() {
        val bubble = ComicBubble(id = "b1", x = 20f, y = 20f, w = 120f, h = 70f, text = "Hi")
        val page = page(
            bubbles = listOf(bubble),
            layers = listOf(baseLayer(), ComicLayer("b1", "气泡", ComicLayer.Kind.BUBBLE)),
        )
        val pixels = pixelsOf(DesktopComicRaster.render(platform, page, "b1", 200, 200))
        assertNotNull("电脑端必须能栅格化气泡层（拿不到 = 导出会少一层 ✗）", pixels)
        assertEquals(200 * 200, pixels!!.size)
        assertTrue("气泡（白底 + 墨色轮廓）应当真的画出不透明像素 ✗", pixels.any { alphaOf(it) > 0 })
        // **透明底**：四个角不在气泡里，应当还是全透明 ✓（不然一层白底会把下面的图糊掉 ✗）
        assertEquals("左上角不该被画到（透明底 ✓）", 0, alphaOf(pixels[0]))
        assertEquals("右下角不该被画到（透明底 ✓）", 0, alphaOf(pixels[200 * 200 - 1]))
    }

    @Test
    fun a_text_layer_comes_back_with_drawn_pixels() {
        val text = ComicText(id = "t1", x = 20f, y = 20f, w = 160f, h = 60f, text = "Hi", fontSize = 36f)
        val page = page(
            texts = listOf(text),
            layers = listOf(baseLayer(), ComicLayer("t1", "文本", ComicLayer.Kind.TEXT)),
        )
        val pixels = pixelsOf(DesktopComicRaster.render(platform, page, "t1", 200, 200))
        assertNotNull("电脑端必须能栅格化文本层 ✗", pixels)
        assertTrue("文字应当真的画出不透明像素 ✗", pixels!!.any { alphaOf(it) > 0 })
    }

    @Test
    fun a_bitmap_layer_id_renders_nothing() {
        // 位图层由 `ComicComposer` 负责 ✓ —— 这里给它的 id 应当得到一张**全透明**的图 ✓
        val page = page(layers = listOf(baseLayer()))
        val png = DesktopComicRaster.render(platform, page, "base", 32, 32)
        assertNotNull("空白内容也该给一张合法的透明 PNG（不是 null ✗）", png)
        val pixels = pixelsOf(png)!!
        assertTrue("位图层不该由矢量那条路画出来 ✗", pixels.all { alphaOf(it) == 0 })
    }

    @Test
    fun a_degenerate_canvas_is_refused_instead_of_crashing() {
        val page = page(layers = listOf(baseLayer()))
        assertEquals(null, DesktopComicRaster.render(platform, page, "base", 0, 100))
        assertEquals(null, DesktopComicRaster.render(platform, page, "base", 100, -1))
    }

    private fun baseLayer() =
        ComicLayer("base", "底板", ComicLayer.Kind.BASE, imagePath = "base.png")

    private fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF
}
