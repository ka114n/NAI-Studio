package com.kallan.naistudio.desktop.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.ui.BubbleStyle
import com.kallan.naistudio.ui.bubbleOutline
import com.kallan.naistudio.ui.bubbleShape
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **气泡那条"矢量轮廓 + 描边"到底能不能在电脑端画出来**（2026-09-20 · 用户实机报错后的回归钉子 ✗）。
 *
 * 实机现场：用户进高级漫画页、放下气泡时弹了一个
 * `java.lang.RuntimeException: Failed to Image::makeFromBitmap Bitmap(_ptr=0x…)` 的**错误框** ✗
 * —— 这条消息来自 skiko：**拿一张"不是位图"的（1×1、可变的）Bitmap 去 `Image.makeFromBitmap`** ✗。
 * 栈是 `SkiaBackedCanvas.drawImageRect` ← `CanvasDrawScope.drawImage` ✓ ——
 * 也就是说：**`Modifier.border(宽, 色, <GenericShape>)` 在电脑端会走"画一张位图"那条路** ✗
 * （标准形状 `RoundedCornerShape` / `CircleShape` 不走 ✗ —— 所以整个仓库只有气泡这一处中招 ✓）。
 *
 * 这条测试同时干三件事 ✓：
 *  1. 钉住**现在的画法**（[bubbleOutline] = `bubblePath` + `drawPath` ✓）四种样式都能画、且真的画出像素 ✓；
 *  2. 留一个**对照**：标准形状的 `border` 一直好着呢 ✓（别把整个 `border` 都当禁用 ✗）；
 *  3. 留一个**绊线**：那个会炸的写法仍然会炸 ✓ —— 哪天 Compose 修了它，这条会**故意红** ✓，
 *     提醒后来人"这个绕法可以撤了" ✓（别让绕法变成永远没人敢动的谜 ⚠️）。
 *
 * 为什么在**离屏场景**里试而不是起窗口：`renderComposeScene` 走的是**同一套 Skia 绘制栈** ✓，
 * 而它能在单测里跑 ✓（`DesktopComicRasterTest` / `DesktopComicRaster` 已经证明了这条路 ✓）。
 */
class ComicOverlayDrawTest {

    private val platform = desktopPlatform()

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(body: @Composable () -> Unit): ByteArray? {
        val image = renderComposeScene(200, 200, Density(1f)) { body() }
        return image.encodeToData(EncodedImageFormat.PNG)?.bytes
    }

    /** PNG → ARGB 像素（失败 → null ✓）。 */
    private fun pixelsOf(png: ByteArray?): IntArray? {
        if (png == null) return null
        val image = platform.images.decodeBytes(png) ?: return null
        val pixels = platform.images.pixelsOf(image)
        image.recycle()
        return pixels
    }

    private fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

    /** ① 现在的画法（[bubbleOutline]）：四种样式都得画得出来、且真有像素 ✓。 */
    @Test
    fun every_bubble_style_draws_through_the_helper() {
        BubbleStyle.entries.forEach { style ->
            val png = render {
                Box(
                    Modifier
                        .size(120.dp, 70.dp)
                        .bubbleOutline(
                            style = style,
                            fill = Color.White,
                            stroke = Color.Black,
                            strokeWidth = 1.5.dp,
                        ),
                )
            }
            val pixels = pixelsOf(png)
            assertNotNull("样式 ${style.name} 的底色 + 描边必须能画 ✗", pixels)
            assertTrue(
                "样式 ${style.name} 应当画出不透明像素 ✗",
                pixels!!.any { alphaOf(it) > 0 },
            )
            assertEquals(
                "样式 ${style.name} 的**泡心**应当是白色填充（填色真的画进去了 ✗）",
                0xFFFFFFFF.toInt(),
                pixels[200 * 35 + 60],
            )
        }
    }

    /** 圆泡的四个角在轮廓之外 → 必须还是透明（证明画的是**轮廓**、不是一整块方形底色 ✓）。 */
    @Test
    fun a_round_bubble_is_transparent_outside_its_outline() {
        val pixels = pixelsOf(
            render {
                Box(
                    Modifier
                        .size(120.dp, 70.dp)
                        .bubbleOutline(
                            style = BubbleStyle.ROUND,
                            fill = Color.White,
                            stroke = Color.Black,
                            strokeWidth = 1.5.dp,
                        ),
                )
            },
        )!!
        assertEquals("左上角不该被画到（圆角之外 ✓）", 0, alphaOf(pixels[0]))
        assertEquals("右下角不该被画到 ✓", 0, alphaOf(pixels[200 * 200 - 1]))
    }

    /** ② 对照组：**标准形状**的 `border` 一直都是好的 ✓（别把 `border` 整个当禁用 ✗）。 */
    @Test
    fun rounded_shape_border_still_draws() {
        val bytes = render {
            Box(
                Modifier
                    .size(120.dp, 70.dp)
                    .background(Color.White, RoundedCornerShape(2.dp))
                    .border(1.5.dp, Color.Black, RoundedCornerShape(2.dp)),
            )
        }
        assertNotNull("标准形状的描边必须能画 ✗", bytes)
    }

    /**
     * ③ **绊线**：那个会炸的写法（`border` + `GenericShape`）现在仍然会炸 ✓。
     *
     * 哪天这条**红了**（= 不再抛），说明 Compose 修好了 ✗ —— 那时可以把 [bubbleOutline]
     * 换回 `.background(...) + .border(...)`，顺手把这条测试和 `bubbleShape` 头上那段 ⚠️ 一起删掉 ✓。
     */
    @Test(expected = RuntimeException::class)
    fun modifier_border_with_a_generic_shape_is_the_known_desktop_trap() {
        render {
            Box(
                Modifier
                    .size(120.dp, 70.dp)
                    .border(1.5.dp, Color.Black, bubbleShape(BubbleStyle.ROUND)),
            )
        }
    }
}
