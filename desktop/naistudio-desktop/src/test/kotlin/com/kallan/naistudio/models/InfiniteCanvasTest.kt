package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 无限画布的**文档几何**判据（`docs/69` ✓）。
 *
 * 这一族钉的是三条**容易写错、错了还很难看出来**的事 ✓：
 *  1. 拓展之后**已有内容不搬家** ✓（文档坐标 → 画布像素 只差一个 origin ✓）；
 *  2. 到上限**返回 null**（不是静默截断 ✗）；
 *  3. `headroom` 与 `extended` **互相自洽** ✓（界面靠前者变灰、靠后者长 ✓，两者对不上就会出现
 *     "按钮是亮的、点了没反应"✗ —— 用户最烦这个 ✓）。
 */
class InfiniteCanvasTest {

    @Test
    fun `请求范围在大框和窄长框四边都保留上下文`() {
        val context = 128
        for ((w, h) in listOf(1090 to 958, 1920 to 384, 384 to 1920)) {
            val plan = FocusedInpaint.plan(
                frame = EditRect(context, context, w, h),
                contextPixels = context,
                sourceWidth = w + context * 2,
                sourceHeight = h + context * 2,
                autoSizeByRect = true,
            )!!
            assertEquals(context, plan.frameInCrop.x)
            assertEquals(context, plan.frameInCrop.y)
            assertEquals(context, plan.cropW - plan.frameInCrop.x - plan.frameInCrop.w)
            assertEquals(context, plan.cropH - plan.frameInCrop.y - plan.frameInCrop.h)
        }
    }

    @Test
    fun `往左长一块 —— 宽度加一块、原点半块、内容不动`() {
        val base = InfiniteCanvas.create(1024, 1024, maxSide = 4096)
        val grown = base.extended(InfiniteSide.LEFT, tiles = 1)
        assertNotNull(grown)
        grown!!
        assertEquals(1024 + INFINITE_TILE, grown.width)
        assertEquals(1024, grown.height)
        assertEquals(-INFINITE_TILE, grown.originX)
        assertEquals(0, grown.originY)
        // 内容不搬家：同一个文档点在**新画布**里的像素位置整体右移一块 ✓
        val doc = 300f
        assertEquals(base.pixelX(doc) + INFINITE_TILE, grown.pixelX(doc), 0.001f)
    }

    @Test
    fun `往右上长 —— 原点不动`() {
        val base = InfiniteCanvas.create(1024, 1024, maxSide = 4096)
        val right = base.extended(InfiniteSide.RIGHT)!!
        assertEquals(0, right.originX)
        assertEquals(1024 + INFINITE_TILE, right.width)
        val top = base.extended(InfiniteSide.TOP)!!
        assertEquals(-INFINITE_TILE, top.originY)
        assertEquals(1024 + INFINITE_TILE, top.height)
    }

    @Test
    fun `到上限返回 null —— 不静默截断`() {
        // 上限 = 1024，已经 1024 宽 ⇒ 再长一块就 1536 > 1024 ⇒ null ✓
        val atLimit = InfiniteCanvas.create(1024, 512, maxSide = 1024)
        assertNull(atLimit.extended(InfiniteSide.RIGHT))
        assertNull(atLimit.extended(InfiniteSide.LEFT))
        // 高那边还有空间 ⇒ 照旧能长 ✓
        assertNotNull(atLimit.extended(InfiniteSide.BOTTOM))
        // 一次要两块、但只放得下一块 ⇒ 也是 null ✓（宁可拒绝，也不偷偷少长 ✓）
        val room = InfiniteCanvas.create(1024 - INFINITE_TILE, 512, maxSide = 1024)
        assertNull(room.extended(InfiniteSide.RIGHT, tiles = 2))
        assertNotNull(room.extended(InfiniteSide.RIGHT, tiles = 1))
    }

    @Test
    fun `headroom 与 extended 自洽 —— 界面变灰的判据不能骗人`() {
        var canvas = InfiniteCanvas.create(512, 512, maxSide = 512 + INFINITE_TILE * 2)
        assertEquals(2, canvas.headroom(InfiniteSide.RIGHT))
        canvas = canvas.extended(InfiniteSide.RIGHT)!!
        assertEquals(1, canvas.headroom(InfiniteSide.RIGHT))
        canvas = canvas.extended(InfiniteSide.RIGHT)!!
        assertEquals(0, canvas.headroom(InfiniteSide.RIGHT))
        // 说 0 就必须真的长不了 ✓
        assertNull(canvas.extended(InfiniteSide.RIGHT))
    }

    @Test
    fun `pasteOffset —— 往左上长才非零`() {
        val base = InfiniteCanvas.create(1024, 1024, maxSide = 4096)
        val left = base.extended(InfiniteSide.LEFT)!!
        assertEquals(INFINITE_TILE to 0, base.pasteOffsetFor(left))
        val top = base.extended(InfiniteSide.TOP)!!
        assertEquals(0 to INFINITE_TILE, base.pasteOffsetFor(top))
        val right = base.extended(InfiniteSide.RIGHT)!!
        assertEquals(0 to 0, base.pasteOffsetFor(right))
    }

    @Test
    fun `文档坐标与画布像素互为逆变换`() {
        val canvas = InfiniteCanvas.create(2048, 1024, maxSide = 4096).extended(InfiniteSide.LEFT)!!
        for (doc in listOf(-500f, 0f, 123.5f, 4096f)) {
            assertEquals(doc, canvas.docX(canvas.pixelX(doc)), 0.001f)
            assertEquals(doc, canvas.docY(canvas.pixelY(doc)), 0.001f)
        }
        assertTrue(canvas.pixelX(0f) > 0f) // 往左长过 ⇒ 文档原点不在画布左上角 ✓
    }

    @Test
    fun `create 把尺寸夹进上限`() {
        val canvas = InfiniteCanvas.create(9000, 100, maxSide = 4096)
        assertEquals(4096, canvas.width)
        assertEquals(100, canvas.height)
    }

    // ------------------------------------------------------------------
    // 拓展时的像素搬运（`InfiniteCanvasPixels.blit` ✓）—— "内容不搬家"的**像素级**判据 ✓
    // ------------------------------------------------------------------

    @Test
    fun `往左长 —— 老内容整体右移一块，一个像素都没丢`() {
        val srcW = 4
        val srcH = 3
        // 用"每个像素都不一样"的花纹（纯色看不出行距 / 偏移类 bug ✓，见 docs/25 那条教训 ✓）
        val src = IntArray(srcW * srcH) { i -> 0xFF000000.toInt() or (i * 7 + 1) }
        val out = InfiniteCanvasPixels.blit(
            src = src,
            srcW = srcW,
            srcH = srcH,
            dstW = srcW + 2,
            dstH = srcH,
            dx = 2,
            dy = 0,
        )
        // 老内容：逐像素对上 ✓
        for (row in 0 until srcH) {
            for (col in 0 until srcW) {
                assertEquals(
                    src[row * srcW + col],
                    out[row * (srcW + 2) + (col + 2)],
                )
            }
        }
        // 新长出来的那两块：全透明 ✓
        assertEquals(0, out[0])
        assertEquals(0, out[1])
        assertEquals(0, out[(srcH - 1) * (srcW + 2)])
    }

    @Test
    fun `往上长 —— dy 生效且不串行`() {
        val srcW = 3
        val srcH = 2
        val src = IntArray(srcW * srcH) { i -> 0xFF112233.toInt() + i }
        val out = InfiniteCanvasPixels.blit(src, srcW, srcH, dstW = srcW, dstH = srcH + 1, dx = 0, dy = 1)
        for (i in src.indices) assertEquals(src[i], out[i + srcW])
        for (i in 0 until srcW) assertEquals(0, out[i])
    }

    @Test
    fun `放不下就抛 —— 不静默裁掉内容`() {
        val src = IntArray(4)
        // ⚠️ 刚好放得下（`dx + srcW == dstW` ✓）**不许抛** ✓ —— 边界那一格最容易写成"多判一位"✗
        InfiniteCanvasPixels.blit(src, 2, 2, dstW = 3, dstH = 2, dx = 1, dy = 0)
        // 差一格放不下 ⇒ 必须抛 ✓
        val threw = runCatching {
            InfiniteCanvasPixels.blit(src, 2, 2, dstW = 3, dstH = 2, dx = 2, dy = 0)
        }.isFailure
        assertTrue("放不下必须抛（宁可报错，也不悄悄裁）", threw)
    }

    @Test
    fun `expandedToInclude —— 框在文档内时返回自己（不长）`() {
        val canvas = InfiniteCanvas.create(1024, 1024, maxSide = 4096)
        val same = canvas.expandedToInclude(100f, 100f, 200f, 200f)
        assertEquals(canvas, same) // 值相等即可（调用方比引用也行，这里只是"没长" ✓）
    }

    @Test
    fun `expandedToInclude —— 框在右下角外 ⇒ 只长右下，原点不动`() {
        val canvas = InfiniteCanvas.create(1024, 1024, maxSide = 4096)
        val grown = canvas.expandedToInclude(1000f, 1000f, 400f, 400f)!!
        assertEquals(1400, grown.width)
        assertEquals(1400, grown.height)
        assertEquals(0, grown.originX)
        assertEquals(0, grown.originY)
        // 老内容原地不动 ✓
        assertEquals(0 to 0, canvas.pasteOffsetFor(grown))
    }

    @Test
    fun `expandedToInclude —— 框在左上角外 ⇒ 长左上、原点变小、老内容整体右下移`() {
        val canvas = InfiniteCanvas.create(1024, 1024, maxSide = 4096)
        val grown = canvas.expandedToInclude(-300f, -200f, 100f, 100f)!!
        assertEquals(1024 + 300, grown.width)
        assertEquals(1024 + 200, grown.height)
        assertEquals(-300, grown.originX)
        assertEquals(-200, grown.originY)
        val (dx, dy) = canvas.pasteOffsetFor(grown)
        assertEquals(300, dx)
        assertEquals(200, dy)
    }

    @Test
    fun `expandedToInclude —— 超上限返回 null（不偷偷夹小用户的框）`() {
        val canvas = InfiniteCanvas.create(4096, 512, maxSide = 4096)
        // 宽已经到顶（4096）⇒ 往左再要 1px ⇒ 并集 4097 > 4096 ⇒ **null** ✓
        assertNull(canvas.expandedToInclude(-1f, 0f, 100f, 100f))
        // ⚠️ 但**另一个方向**还有空间 ⇒ 照旧能长 ✓（"宽到顶"不该把"高"也一起禁掉 ✓）
        //    框放在**下边**（x 范围仍在 0..4096 内 ✓ ⇒ 宽不变、只长高 ✓）
        val grown = canvas.expandedToInclude(0f, 512f, 512f, 512f)
        assertNotNull(grown)
        assertEquals(4096, grown!!.width)
        assertEquals(1024, grown.height)
    }

    @Test
    fun `expandedToInclude —— 长出来的那块是全新的（矩阵角上那个像素没被写过）`() {
        // 走一遍真实路径：往右下长 ⇒ 搬运偏移 (0,0) ⇒ 右下那块的像素**保持透明** ✓
        val srcW = 2
        val srcH = 2
        val src = IntArray(srcW * srcH) { i -> 0xFF0000FF.toInt() + i }
        val base = InfiniteCanvas.create(srcW, srcH, maxSide = 4096)
        val grown = base.expandedToInclude(0f, 0f, 4f, 4f)!!
        val (dx, dy) = base.pasteOffsetFor(grown)
        val out = InfiniteCanvasPixels.blit(src, srcW, srcH, grown.width, grown.height, dx, dy)
        // 老内容 ✓
        assertEquals(src[0], out[0])
        assertEquals(src[3], out[srcW - 1 + (srcH - 1) * grown.width])
        // 新长出来那块：透明 ✓（"未绘制的地方就是透明通道" ✓）
        assertEquals(0, out[grown.width - 1])
        assertEquals(0, out[(grown.height - 1) * grown.width])
    }
}
