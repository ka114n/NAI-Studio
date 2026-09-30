package com.kallan.naistudio.screens

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **"屏幕坐标 → 原图像素"那条链子**的钉子（用户 2026-09-20：「还是一排点」⇒ 怀疑重点 ✓）。
 *
 * ## 为什么单独立一条
 *
 * 用户的点距是 **4~6 个屏幕像素** ⇒ 换算到原图像素要乘 `1 / zoom` ✓；
 * 要是哪一环把 `zoom`（或 DPR、或 `padding(14.dp)`）漏了 / 多乘一次，
 * 屏幕上的点距就会被**放大好几倍** ✗ —— 那完全能解释"等距的点" ✓。
 * 这一段**不依赖界面** ✓（纯函数 ✓），所以能在这儿把每一环钉死：
 *
 *  1. [paper] 是**纸的屏幕矩形**、而且**已经含了 fit 缩放** ✓（= `Box(size(drawW, drawH))`
 *     的 `onSizeChanged` ✓）⇒ 换算里 `paper.width / pageWidth` 这一步**只能出现一次** ✓
 *     （再除一次 fit 就是重复 ✗）；
 *  2. `zoom`（= `canvasScale`）**该除** ✓ —— 它只作用在纸那层的 `graphicsLayer`
 *     中心缩放上 ✓（`transformOrigin` 默认 = 层中心 ✓）；
 *  3. 屏幕位移 d ⇒ 原图位移必须是 `d / (paper.width / pageWidth) / zoom` ✓（用户点名的口径 ✓）；
 *  4. 纸 / 屏幕**同一个 px 空间** ✓（`onSizeChanged` 的 `IntSize` 与 `pointerInput` 的
 *     `position` 都是 px ✓）—— 谁把一边换成 dp、另一边留 px，第 ③ 条立刻红 ✓。
 *
 * ⚠️ 第 4 条在电脑端还有一层**实机口径**（写在 `comicScreenToPagePixel` 的注释里 ✓）：
 * exe 不是 DPI 感知的，`LocalDensity.density` **恒为 1.0** ✓（Windows 把整个窗口位图拉伸 ✓，
 * 见 `Platform.uiScale` ✓）⇒ 150% 那台机器上屏幕点距会被**均匀**放大 1.5 倍，
 * 但比值不变 ✓（这就是为什么日志里要带 `dpr=` ✓）。
 */
class ComicScreenToPagePixelTest {

    /** 一个像样的场景：832×1216 的 A4 页，纸在屏幕上是 410×600 px（**已经 fit 过** ✓）。 */
    private val paper = IntSize(410, 600)
    private val pageWidth = 832
    private val pageHeight = 1216

    // ------------------------------------------------------------------
    // ① 屏幕位移 d ⇒ 原图位移 = d / (paper.w / pageW) / zoom ✓
    // ------------------------------------------------------------------

    @Test
    fun a_screen_delta_maps_to_page_delta_through_the_fit_ratio_and_zoom() {
        val zoom = 0.5f
        val pan = Offset(30f, -12f)
        val a = requireNotNull(
            comicScreenToPagePixel(Offset(220f, 300f), paper, pageWidth, pageHeight, zoom, pan),
        ) { "先决条件：这个屏幕点在纸上" }
        val d = 100f
        val b = requireNotNull(
            comicScreenToPagePixel(Offset(220f + d, 300f), paper, pageWidth, pageHeight, zoom, pan),
        ) { "先决条件：这个屏幕点在纸上" }

        // paper 里**已经含了 fit** ✓ ⇒ 这里只再除 zoom，**不再乘任何 fit 的倒数** ✓
        val expected = d / (paper.width.toFloat() / pageWidth) / zoom
        assertEquals("屏幕 100px 在原图上应该是多少（= d / fit比 / zoom）", expected, b.x - a.x, 1e-3f)
        assertEquals("纯水平位移不该改变 y", 0f, b.y - a.y, 1e-3f)
        assertTrue("这一条得有实际量级（免得上面那个 1e-3 容差把 0 也算过 ✗）", expected > 300f)
    }

    // ------------------------------------------------------------------
    // ② 逆变换必须**正好**是 graphicsLayer 那个中心缩放 + 平移的逆 ✓
    // ------------------------------------------------------------------

    @Test
    fun b_the_inverse_matches_the_paper_layers_graphics_layer_transform() {
        val zoom = 1.75f
        val pan = Offset(-40f, 25f)
        val page = Offset(300f, 700f)

        // 正变换（界面真做的那一套）：原图 → 纸内（fit ✓）→ graphicsLayer（**中心**缩放 + 平移 ✓）→ 屏幕
        val centerX = paper.width / 2f
        val centerY = paper.height / 2f
        val localX = page.x / pageWidth * paper.width
        val localY = page.y / pageHeight * paper.height
        val screen = Offset(
            centerX + (localX - centerX) * zoom + pan.x,
            centerY + (localY - centerY) * zoom + pan.y,
        )

        val back = requireNotNull(
            comicScreenToPagePixel(screen, paper, pageWidth, pageHeight, zoom, pan),
        ) { "正变换出来的屏幕点必须能逆回去" }
        assertEquals("原图 x 往返必须一致（多乘 / 漏乘一次 zoom 这条就红 ✗）", page.x, back.x, 1e-2f)
        assertEquals("原图 y 往返必须一致", page.y, back.y, 1e-2f)
    }

    // ------------------------------------------------------------------
    // ③ 纸与屏幕**同空间**（px）；fit 比只出现一次 ✓
    // ------------------------------------------------------------------

    @Test
    fun c_paper_rect_and_screen_offsets_live_in_the_same_px_space() {
        val zoom = 1f
        val pan = Offset.Zero
        val base = requireNotNull(
            comicScreenToPagePixel(Offset(220f, 300f), paper, pageWidth, pageHeight, zoom, pan),
        )
        val movedBy100 = requireNotNull(
            comicScreenToPagePixel(Offset(320f, 300f), paper, pageWidth, pageHeight, zoom, pan),
        )
        val baseDelta = movedBy100.x - base.x

        // (a) 纸的 px 尺寸翻倍 ⇒ **同样的屏幕位移**在原图上只走一半 ⇒ fit 比确实**只用了一次** ✓
        val doubledPaper = IntSize(paper.width * 2, paper.height * 2)
        val doubledBase = requireNotNull(
            comicScreenToPagePixel(Offset(220f, 300f), doubledPaper, pageWidth, pageHeight, zoom, pan),
        )
        val doubledMoved = requireNotNull(
            comicScreenToPagePixel(Offset(320f, 300f), doubledPaper, pageWidth, pageHeight, zoom, pan),
        )
        assertEquals("纸大一倍 ⇒ 同样的屏幕位移在原图上减半", baseDelta / 2f, doubledMoved.x - doubledBase.x, 1e-3f)

        // (b) 屏幕位移与纸尺寸**同时**翻倍 ⇒ 原图位移不变（= 整条链子是齐次的 ✓，
        //     这一条正是"density 变了但两边同空间"的代数写法 ✓）
        val scaledScreen = requireNotNull(
            comicScreenToPagePixel(Offset(440f, 600f), doubledPaper, pageWidth, pageHeight, zoom, pan),
        )
        val scaledMoved = requireNotNull(
            comicScreenToPagePixel(Offset(640f, 600f), doubledPaper, pageWidth, pageHeight, zoom, pan),
        )
        assertEquals("两边同空间 ⇒ 一起放大时原图位移不变", baseDelta, scaledMoved.x - scaledScreen.x, 1e-3f)
    }

    // ------------------------------------------------------------------
    // ④ 边界：纸外 / 参数不合法 → null（**绝不返回一个假坐标** ✗）
    // ------------------------------------------------------------------

    @Test
    fun d_outside_the_paper_or_bad_parameters_return_null() {
        // 落笔点换算到原图后在纸外（负坐标 / 超出纸宽 ✓）→ null ✓
        assertNull(
            "屏幕点跑到纸外 → null（不能硬算一个负数坐标 ✗）",
            comicScreenToPagePixel(Offset(-4000f, 300f), paper, pageWidth, pageHeight, 1f, Offset.Zero),
        )
        assertNull(
            "纸的尺寸还没量出来（0）→ null",
            comicScreenToPagePixel(Offset(10f, 10f), IntSize.Zero, pageWidth, pageHeight, 1f, Offset.Zero),
        )
        assertNull(
            "zoom ≤ 0 → null（除以 0 会得到 NaN，洒进笔尖半径就是一片空白 ✗）",
            comicScreenToPagePixel(Offset(10f, 10f), paper, pageWidth, pageHeight, 0f, Offset.Zero),
        )
        assertNull(
            "页尺寸非法 → null",
            comicScreenToPagePixel(Offset(10f, 10f), paper, 0, pageHeight, 1f, Offset.Zero),
        )
        // 正例对照：纸内那个点必须有值（免得上面四条其实是因为"整个函数恒 null"而通过 ✗）
        assertNotNull(
            "纸内的点必须有值",
            comicScreenToPagePixel(Offset(205f, 300f), paper, pageWidth, pageHeight, 1f, Offset.Zero),
        )
    }
}
