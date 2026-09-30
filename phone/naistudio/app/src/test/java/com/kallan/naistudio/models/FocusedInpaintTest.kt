package com.kallan.naistudio.models

import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 手机线**聚焦重绘几何**的单测 —— 与电脑线 `FocusedInpaintTest` 同一套口径
 * （2026-09-17 从电脑线移植，见 `docs/25` §37 与 §43）。
 */
class FocusedInpaintTest {

    /** 像素矩形框（原图像素）—— 生产代码 `FocusedInpaint.plan` 收的就是 `EditRect`。 */
    private fun frame(x: Int, y: Int, w: Int, h: Int, width: Int, height: Int) =
        EditRect(x, y, w, h).clampTo(width, height)

    @Test
    fun `infinite canvas request surrounds large and narrow frames on all four sides`() {
        val context = 128
        for ((w, h) in listOf(1090 to 958, 1920 to 384, 384 to 1920)) {
            val plan = FocusedInpaint.plan(
                frame = EditRect(context, context, w, h),
                contextPixels = context,
                sourceWidth = w + context * 2,
                sourceHeight = h + context * 2,
                autoSizeByRect = true,
            )!!
            assertEquals("left context for $w × $h", context, plan.frameInCrop.x)
            assertEquals("top context for $w × $h", context, plan.frameInCrop.y)
            assertEquals("right context for $w × $h", context, plan.cropW - plan.frameInCrop.x - plan.frameInCrop.w)
            assertEquals("bottom context for $w × $h", context, plan.cropH - plan.frameInCrop.y - plan.frameInCrop.h)
        }
    }

    @Test
    fun `small frame on a big image is auto-filled into the free tier`() {
        val plan = FocusedInpaint.plan(
            frame = frame(600, 800, 200, 200, 832, 1216),
            contextPixels = 96,
            sourceWidth = 832,
            sourceHeight = 1216,
        )
        assertNotNull(plan)
        plan!!
        // 框 200×200 + 96 上下文；右侧贴到画布边被夹 → 宽 328、高 392
        assertEquals(328, plan.cropW)
        assertEquals(392, plan.cropH)
        // 免费档内：每边 ≤1024、面积 ≤1MP
        assertTrue("每边 ≤1024", plan.requestW <= 1024 && plan.requestH <= 1024)
        assertTrue("面积 ≤1MP", plan.requestW.toLong() * plan.requestH <= 1024L * 1024L)
        assertTrue("自动放大到接近额度", plan.autoFilled)
        assertTrue(plan.inFreeTier)
        // 框在请求图里应当**贴着**裁剪图的位置（缩放了但没跑偏）
        val expectedLeft = (plan.frameInCrop.x * (plan.requestW.toFloat() / plan.cropW)).roundToInt()
        assertEquals(expectedLeft, plan.frameInRequest.x)
        assertTrue("框在请求图内", plan.frameInRequest.x + plan.frameInRequest.w <= plan.requestW)
    }

    @Test
    fun `context is clamped at the canvas edge instead of doubling on the far side`() {
        // 框贴着左上角：上下文只能在右/下补，左边不该凭空多出 96
        val plan = FocusedInpaint.plan(
            frame = frame(0, 0, 100, 100, 800, 800),
            contextPixels = 96,
            sourceWidth = 800,
            sourceHeight = 800,
            limitToFreeTier = false,
        )
        assertNotNull(plan)
        plan!!
        assertEquals(0, plan.cropX)
        assertEquals(0, plan.cropY)
        assertEquals(196, plan.cropW)
        assertEquals(196, plan.cropH)
    }

    @Test
    fun `free tier off allows a bigger request but still within the server cap`() {
        val plan = FocusedInpaint.plan(
            frame = frame(100, 100, 400, 400, 2000, 2000),
            contextPixels = 64,
            sourceWidth = 2000,
            sourceHeight = 2000,
            scale = 2f,
            limitToFreeTier = false,
        )
        assertNotNull(plan)
        plan!!
        assertTrue("不超过服务端上限", plan.requestW.toLong() * plan.requestH <= FocusedInpaint.MAX_REQUEST_PIXELS)
    }

    @Test
    fun `empty mask inside the frame becomes a whole-frame repaint`() {
        val plan = FocusedInpaint.plan(frame(100, 100, 200, 200, 800, 800), 32, 800, 800)!!
        val empty = PixelMask(800, 800, ByteArray(800 * 800))
        val mask = FocusedInpaint.maskIntoRequest(empty, plan, fillWholeFrame = true)
        assertNotNull("框里没涂 → 整框重绘", mask)
        mask!!
        assertEquals(plan.requestW, mask.width)
        // 整框应该被填满：抽样框内几个点全是 255
        assertTrue(mask.valueAt(plan.frameInRequest.x + 1, plan.frameInRequest.y + 1) != 0)
        assertTrue(
            mask.valueAt(
                plan.frameInRequest.x + plan.frameInRequest.w - 1,
                plan.frameInRequest.y + plan.frameInRequest.h - 1,
            ) != 0,
        )
        // 框外（上下文那圈）必须是 0 —— 否则会把没让改的地方也重绘
        assertEquals(0, mask.valueAt(0, 0))
    }

    @Test
    fun `painted pixels outside the frame are dropped`() {
        val plan = FocusedInpaint.plan(frame(200, 200, 200, 200, 800, 800), 0, 800, 800)!!
        val pixels = ByteArray(800 * 800)
        // 涂层"框外"一块（左上空着）→ 搬进请求图后应当**一个都不剩** → 回落成整框重绘或 null
        for (y in 10 until 60) for (x in 10 until 60) pixels[y * 800 + x] = 255.toByte()
        val outside = FocusedInpaint.maskIntoRequest(PixelMask(800, 800, pixels), plan, fillWholeFrame = false)
        assertNull("框外涂的不算数", outside)
    }

    @Test
    fun `frame mask only covers the frame`() {
        val plan = FocusedInpaint.plan(frame(100, 100, 150, 150, 600, 600), 40, 600, 600)!!
        // 空蒙版 + fillWholeFrame → 整框重绘（等价于旧的 `frameMask(plan)`）
        val empty = PixelMask(600, 600, ByteArray(600 * 600))
        val mask = FocusedInpaint.maskIntoRequest(empty, plan, fillWholeFrame = true)!!
        assertEquals(plan.requestW, mask.width)
        assertEquals(0, mask.valueAt(0, 0))
        assertTrue(mask.valueAt(plan.frameInRequest.x + 1, plan.frameInRequest.y + 1) != 0)
    }
}
