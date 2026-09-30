package com.kallan.naistudio.desktop

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * **㉜a 批 · 动手之前的探针**（只打印，不当判据 ✓）。
 *
 * 要回答的问题（每一个都直接决定显示链怎么写 ✗）：
 * 1. **CoW 到底成不成立**：`makeImageSnapshot()` 是 0.002 ms（写时复制 ✓），
 *    但"**快照之后继续往表面上画**"会不会触发**整页拷贝**？
 *    —— 显示链每帧都要看表面、而笔画每颗笔尖都要写表面 ⇒ 这一条是生死线 ✓；
 * 2. **层表面第一次创建**（A4 整页 `pixels` → 表面的一次上传）要多久 —— 就是"冷启动第一笔"里那一段 ✓；
 * 3. **视口那条路每一段各要多少 ms**：可见区回读 / 视口 blit / `toComposeImageBitmap` ✓。
 */
class SurfaceCanvasProbeTest {

    private val w = 2480
    private val h = 3508

    private fun bench(label: String, rounds: Int, block: () -> Unit): Double {
        repeat(2) { block() }
        val ns = kotlin.system.measureNanoTime { repeat(rounds) { block() } }
        val ms = ns / 1_000_000.0 / rounds
        println("   $label = ${"%.4f".format(ms)} ms/次")
        return ms
    }

    @Test
    fun probe_copy_on_write_and_viewport_costs() {
        val pageBytes = w.toLong() * h * 4
        println("[㉜a-probe] A4 $w×$h（整页 = ${pageBytes / 1024 / 1024} MB）")
        val surface = Surface.makeRasterN32Premul(w, h)
        val canvas = surface.canvas
        val paint = Paint().apply {
            isAntiAlias = true
            color = 0xFF224466.toInt()
        }
        val dab = Rect.makeLTRB(-30f, -30f, 30f, 30f)
        fun drawDab(i: Int) {
            canvas.save()
            canvas.translate(200f + (i % 60) * 30f, 600f + (i % 60) * 30f)
            canvas.drawOval(dab, paint)
            canvas.restore()
        }

        // ---- ① 纯画：不留快照 ----
        val pure = bench("100 颗笔尖【不留任何快照】/颗", 100) { drawDab(0) }

        // ---- ② 每画一颗之前**快照一次并留着**（不 close）----
        val kept = ArrayList<Image>()
        val keepSnap = bench("100 颗笔尖【每颗之前快照一次、**留着不关**】/颗", 60) {
            kept.add(surface.makeImageSnapshot())
            drawDab(1)
        }
        kept.forEach { it.close() }

        // ---- ③ 每画一颗之前快照一次、**画完立刻关掉**（= 显示链每帧的用法 ✓）----
        val perFrameClose = bench("100 颗笔尖【每颗之前快照、画完 close】/颗", 60) {
            val s = surface.makeImageSnapshot()
            drawDab(2)
            s.close()
        }

        // ---- ④ 快照一次、之后连着画 20 颗（看"快照之后**第一次**画"有多贵 ✓）----
        run {
            val s = surface.makeImageSnapshot()
            val first = kotlin.system.measureNanoTime { drawDab(3) } / 1_000_000.0
            val rest = kotlin.system.measureNanoTime { repeat(19) { drawDab(4) } } / 1_000_000.0 / 19
            println("   ①快照之后**第一次**画 = ${"%.3f".format(first)} ms；之后每颗 = ${"%.4f".format(rest)} ms")
            s.close()
        }

        // ---- ⑤ 视口那几段（都按 525×743 = 0.39 MP ✓）----
        val vw = 525
        val vh = 743
        val vpBitmap = Bitmap().also {
            it.allocPixels(ImageInfo(vw, vh, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
        }
        val readRegion = bench("Surface.readPixels(复用 Bitmap $vw×$vh 区域)", 40) {
            surface.readPixels(vpBitmap, 100, 100)
        }
        val toComposeMs = bench("Image.makeFromBitmap(视口) + toComposeImageBitmap()", 40) {
            Image.makeFromBitmap(vpBitmap).toComposeImageBitmap()
        }
        val viewport = Surface.makeRasterN32Premul(vw, vh)
        val blitPaint = Paint().apply { blendMode = BlendMode.SRC }
        val srcRect = Rect.makeXYWH(100f, 100f, vw.toFloat(), vh.toFloat())
        val dstRect = Rect.makeXYWH(0f, 0f, vw.toFloat(), vh.toFloat())
        val snap = surface.makeImageSnapshot()
        for (mode in listOf(
            "LINEAR" to SamplingMode.LINEAR,
            "MITCHELL" to SamplingMode.MITCHELL,
            "CATMULL_ROM" to SamplingMode.CATMULL_ROM,
        )) {
            bench("视口 blit（整页快照 → $vw×$vh，${mode.first}）", 20) {
                viewport.canvas.drawImageRect(snap, srcRect, dstRect, mode.second, blitPaint, true)
            }
        }
        val subSnap = bench("makeImageSnapshot(IRect $vw×$vh)", 20) {
            surface.makeImageSnapshot(org.jetbrains.skia.IRect.makeXYWH(100, 100, vw, vh))?.close()
        }
        val wholeSnap = bench("makeImageSnapshot()（整页）", 20) {
            surface.makeImageSnapshot()?.close()
        }

        // ---- ⑥ 层表面第一次创建：整页 pixels → 表面 ----
        val pixels = IntArray(w * h) { if (it % 7 == 0) 0xFF3366CC.toInt() else 0 }
        val bytes = ByteArray(w * h * 4)
        val memcpyMs = bench("整页 IntArray → ByteArray（memcpy）", 5) {
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(pixels, 0, w * h)
        }
        val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        val fresh = Surface.makeRasterN32Premul(w, h)
        val uploadMs = bench("整页 Image.makeRaster(UNPREMUL) + drawImageRect(SRC) → 表面", 5) {
            val img = Image.makeRaster(info, bytes, w * 4)
            fresh.canvas.drawImageRect(
                img,
                Rect.makeWH(w.toFloat(), h.toFloat()),
                Rect.makeWH(w.toFloat(), h.toFloat()),
                SamplingMode.DEFAULT,
                blitPaint,
                true,
            )
            img.close()
        }
        bench("makeRasterN32Premul($w×$h)（只分配、不碰像素）", 5) {
            Surface.makeRasterN32Premul(w, h).close()
        }

        println(
            "[㉜a-probe] 小结：\n" +
                "   纯画一颗 = ${"%.4f".format(pure)} ms；每颗前快照(留着) = ${"%.4f".format(keepSnap)} ms；" +
                "每颗前快照(画完关) = ${"%.4f".format(perFrameClose)} ms\n" +
                "   ⇒ 快照与「继续画」共存时单颗成本 = 纯画的 **×${"%.0f".format(keepSnap / maxOf(1e-4, pure))}**" +
                "（≫1 ⇒ 写时复制在「快照 + 继续画」这条组合下**不成立** ✗，每颗笔尖都要拷一整页 ✓）\n" +
                "   视口各段：区域回读 ${"%.3f".format(readRegion)} / 视口→Compose 位图 ${"%.3f".format(toComposeMs)} ms\n" +
                "   冷启动上传（整页）= ${"%.1f".format(uploadMs)} ms（memcpy ${"%.1f".format(memcpyMs)} ms）；" +
                "整页快照 ${"%.3f".format(wholeSnap)} / 子区快照 ${"%.3f".format(subSnap)} ms",
        )
        assertTrue("探针至少要真的跑出数（uploadMs=$uploadMs）✗", uploadMs > 0.0)
    }
}
