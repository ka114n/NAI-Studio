package com.kallan.naistudio.desktop

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **㉚ 批的生产前探针（只打印，不判定 ✓）**：把"画布 = 一张真 skiko `Surface`"这个方案
 * 的**三个未知数**先量出来，再决定显示那一半怎么写 ✓。
 *
 * 1. `Surface.makeImageSnapshot()` 在 A4 光栅表面上到底是**共享像素**还是**拷一份 35 MB** ✗ ——
 *    这直接决定"每帧快照一次"能不能成立 ✓；
 * 2. 快照之后**再往 surface 上画**，旧快照会不会跟着变（= 是不是同一块内存 ✓）；
 * 3. `toComposeImageBitmap()` 与一次绘制各要多少 ms ✓。
 */
class SkiaSurfaceProbeTest {

    @Test
    fun makeImageSnapshot_cost_and_aliasing_on_a4() {
        val w = 2480
        val h = 3508
        val surface = Surface.makeRasterN32Premul(w, h)
        val canvas = surface.canvas
        val paint = Paint().apply {
            isAntiAlias = true
            color = 0xFF224466.toInt()
        }
        canvas.drawOval(Rect.makeLTRB(100f, 100f, 400f, 400f), paint)

        // ---- 1. 快照耗时 ----
        repeat(3) { surface.makeImageSnapshot() }
        val rounds = 20
        val snapNs = kotlin.system.measureNanoTime {
            repeat(rounds) { surface.makeImageSnapshot() }
        }
        val snapMs = snapNs / 1_000_000.0 / rounds

        // ---- 2. 快照之后继续画：旧快照会不会跟着变（判断共享还是拷贝）----
        val before = surface.makeImageSnapshot()
        val pxBefore = before.peekPixels()?.let { pm ->
            org.jetbrains.skia.Bitmap.makeFromImage(before)
            pm.getColor(200, 200)
        }
        canvas.drawOval(Rect.makeLTRB(150f, 150f, 250f, 250f), Paint().apply {
            isAntiAlias = false
            color = 0xFFFF0000.toInt()
        })
        val pxAfterOldSnapshot = before.peekPixels()?.getColor(200, 200)
        val freshSnapshot = surface.makeImageSnapshot()
        val pxFresh = freshSnapshot.peekPixels()?.getColor(200, 200)

        // ---- 3. toComposeImageBitmap 耗时 ----
        val img = surface.makeImageSnapshot()
        repeat(2) { img.toComposeImageBitmap() }
        val toBitmapNs = kotlin.system.measureNanoTime {
            repeat(rounds) { img.toComposeImageBitmap() }
        }
        val toBitmapMs = toBitmapNs / 1_000_000.0 / rounds

        // ---- 4. 一次整页 readPixels（对照：显示那条路**不该**走它 ✓）----
        val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bmp = Bitmap().also { it.allocPixels(info) }
        repeat(2) { surface.readPixels(bmp, 0, 0) }
        val readNs = kotlin.system.measureNanoTime {
            repeat(rounds) { surface.readPixels(bmp, 0, 0) }
        }
        val readMs = readNs / 1_000_000.0 / rounds

        println(
            "[㉚-probe] A4 2480×3508 光栅 Surface（整页 ${w.toLong() * h * 4 / 1024 / 1024} MB）\n" +
                "   makeImageSnapshot()      = ${"%.3f".format(snapMs)} ms/次\n" +
                "   toComposeImageBitmap()   = ${"%.3f".format(toBitmapMs)} ms/次\n" +
                "   （对照）Surface.readPixels(整页) = ${"%.3f".format(readMs)} ms/次\n" +
                "   快照后再画：旧快照 (200,200) = ${pxBefore?.let { "%08X".format(it) }} → ${pxAfterOldSnapshot?.let { "%08X".format(it) }}；" +
                "新快照 = ${pxFresh?.let { "%08X".format(it) }}（旧快照变了 = 共享内存 ✓；没变 = 拷贝 ✗）",
        )

        // ---- 5. `toComposeImageBitmap` 是不是**按像素拷贝**（缩放律 = 判据 ✓）----
        fun wrapCost(side: Int): Double {
            val s = Surface.makeRasterN32Premul(side, side)
            s.canvas.clear(0xFF102030.toInt())
            val im = s.makeImageSnapshot()
            repeat(3) { im.toComposeImageBitmap() }
            val ns = kotlin.system.measureNanoTime { repeat(20) { im.toComposeImageBitmap() } }
            return ns / 1_000_000.0 / 20
        }
        val c256 = wrapCost(256)
        val c1024 = wrapCost(1024)
        val c2048 = wrapCost(2048)
        println(
            "[㉚-probe] toComposeImageBitmap 的**缩放律**（若是拷贝 ⇒ 正比于像素数 ✗）：\n" +
                "   256×256   (0.26 MP) = ${"%.3f".format(c256)} ms\n" +
                "   1024×1024 (1.05 MP) = ${"%.3f".format(c1024)} ms  （×${"%.1f".format(c1024 / c256)} vs 面积 ×${"%.1f".format(1024.0 * 1024 / (256.0 * 256))}）\n" +
                "   2048×2048 (4.19 MP) = ${"%.3f".format(c2048)} ms  （×${"%.1f".format(c2048 / c256)} vs 面积 ×${"%.1f".format(2048.0 * 2048 / (256.0 * 256))}）",
        )
        assertTrue("探针至少要真的跑出数（pxBefore=$pxBefore）✗", pxBefore != null)
    }
}
