package com.kallan.naistudio.desktop

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.junit.Test

/**
 * **㉞ 第一步：GPU 可行性探针**（用户 2026-09-21 批准「**做 b，搬到 gpu 上**」✓）。
 *
 * ## 这一条**故意不做任何断言** ✓
 *
 * 它要回答的问题只有一个：**skiko 0.9.4.2 在这台 Windows 上到底能不能建出 GPU 上下文** ✓
 * —— **能 / 不能都要有实测证据** ✓（所以这里 `catch Throwable` 把异常**原样打出来** ✓，
 * 测试本身**永远绿** ✓：它是一份"体检报告"，不是一个判据 ✗）。
 *
 * ## 要量三个数（决定"搬 GPU 到底值不值"✓）
 *
 *  ① **每颗笔尖的 GPU 打点耗时**（对照现在 CPU 的：混色 ~127 µs/颗、纸纹 ~217 µs/颗 ✓）；
 *  ② **GPU 快照是"免费共享"还是"整面拷贝"** ✗ —— ㉜a 在 **CPU** 表面上实测过：
 *     "每颗笔尖之前快照一次" = **7.04 ms/颗**（拿快照继续画 ⇒ 整页 33 MB 拷 ✗）；
 *     GPU 上这张 A4 表面（33 MB）如果**同样是整面拷贝** ⇒ "显示不回读"这条就白想 ✓；
 *  ③ **GPU → CPU 单块回读耗时**（现在**每颗笔尖都回读一小块**进 `pixels` ✓ 一笔 +135 次 ✓，
 *     那是撤销 / 存盘的根基 ✓；GPU 上多半是**同步点** ✗ ⇒ 这正是"`pixels` 降级成按需镜像"
 *     那条改法的依据 ✓）。
 */
class GpuSurfaceFeasibilityProbeTest {

    private fun ms(n: Int, body: () -> Unit): Double {
        repeat(3) { body() }
        val t0 = System.nanoTime()
        repeat(n) { body() }
        return (System.nanoTime() - t0) / 1_000_000.0 / n
    }

    private fun info(w: Int, h: Int) =
        ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL)

    @Test
    fun can_skiko_make_a_gpu_context_on_this_machine() {
        println("[㉞-gpu] ==== 探针：skiko GPU 可行性 ====")
        println("[㉞-gpu] os=${System.getProperty("os.name")} ${System.getProperty("os.version")} " +
            "arch=${System.getProperty("os.arch")} java=${System.getProperty("java.version")}")

        // ---- ① 建上下文：GL（Windows 上 skiko 走 ANGLE/WGL ✓）----
        var context: DirectContext? = null
        try {
            context = DirectContext.makeGL()
            println("[㉞-gpu] DirectContext.makeGL() ⇒ **成功** ✓ handle=${context != null}")
        } catch (t: Throwable) {
            println("[㉞-gpu] DirectContext.makeGL() ⇒ **失败** ✗")
            println("[㉞-gpu] 异常原文：${t::class.java.name}: ${t.message}")
            t.stackTrace.take(8).forEach { println("[㉞-gpu]     at $it") }
        }

        if (context == null) {
            // ---- 另一条入口：Direct3D（skiko 只暴露 makeDirect3D(adapterPtr, devicePtr, queuePtr) ✓，
            //      三个都是**原生 COM 指针** ✗ —— 公开 API 里没有"从当前窗口拿它们"的办法 ✓）----
            try {
                val d3d = DirectContext.makeDirect3D(0L, 0L, 0L)
                println("[㉞-gpu] DirectContext.makeDirect3D(0,0,0) ⇒ ${if (d3d != null) "成功 ✓" else "null ✗"}")
            } catch (t: Throwable) {
                println("[㉞-gpu] DirectContext.makeDirect3D(0,0,0) ⇒ **失败** ✗")
                println("[㉞-gpu] 异常原文：${t::class.java.name}: ${t.message}")
                t.stackTrace.take(4).forEach { println("[㉞-gpu]     at $it") }
            }
            println("[㉞-gpu] 环境：skiko.renderApi=${System.getProperty("skiko.renderApi")}、" +
                "SKIKO_RENDER_API=${System.getenv("SKIKO_RENDER_API")}")
            println("[㉞-gpu] ⇒ 结论：这台机器上**公开 API 建不出 GPU 上下文** ⇒ " +
                "「层表面换成 GPU render target」这条路（§8.7-④）**走不通** ✗")
            println("[㉞-gpu]   （GPU→CPU 回读 / GPU 快照那两个数因此**量不了** ✗ —— 没有上下文就没有表面 ✓）")
            return
        }

        // ---- ② 建 A4 的 GPU render target（对照 CPU：Surface.makeRasterN32Premul ✓）----
        val pageW = 2480
        val pageH = 3508
        val gpu: Surface?
        try {
            gpu = Surface.makeRenderTarget(context, false, info(pageW, pageH))
            println("[㉞-gpu] Surface.makeRenderTarget(A4 $pageW x $pageH) ⇒ ${if (gpu != null) "成功 ✓" else "null ✗"}")
        } catch (t: Throwable) {
            println("[㉞-gpu] Surface.makeRenderTarget ⇒ **抛异常** ✗ ${t::class.java.name}: ${t.message}")
            return
        }
        if (gpu == null) {
            println("[㉞-gpu] ⇒ 结论：上下文有了但**建不出 render target** ✗")
            return
        }

        val paint = Paint().apply { isAntiAlias = true; color = 0xCC102030.toInt() }
        // ---- ③ 打点 + 快照 + 回读 三个数 ----
        try {
            // (a) GPU 打点：一颗 24px 的抗锯齿椭圆（与生产同口径 ✓）
            val dab = ms(200) {
                gpu.canvas.drawOval(Rect.makeLTRB(100f, 100f, 124f, 124f), paint)
            }
            context.flush()
            // (b) 快照之后**继续画**（CPU 那版就是在这里踩到"整页 33 MB 拷贝"✗）
            val snap = gpu.makeImageSnapshot()
            val afterSnap = ms(60) {
                gpu.canvas.drawOval(Rect.makeLTRB(200f, 200f, 224f, 224f), paint)
            }
            // (c) 单独量一次"快照"本身
            val snapOnly = ms(60) {
                val s = gpu.makeImageSnapshot()
                s.close()
            }
            // (d) GPU → CPU 单块回读（现在每颗笔尖都这么做 ✓ 一笔 +135 次）
            val bmp = org.jetbrains.skia.Bitmap()
            bmp.allocPixels(info(130, 130))
            val readOne = ms(60) { gpu.readPixels(bmp, 100, 100) }
            // (e) CPU 侧同一颗笔尖（对照 ✓）
            val cpu = Surface.makeRasterN32Premul(pageW, pageH)
            val cpuDab = ms(200) { cpu.canvas.drawOval(Rect.makeLTRB(100f, 100f, 124f, 124f), paint) }
            // (f) 快照能不能编码出来（"看得见"的最终证据 ✓）
            val bytes = snap.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes
            println(
                "[㉞-gpu] ==== 三个数（us/次）====\n" +
                    "    ① GPU 打点一颗 24px 椭圆      = ${"%.1f".format(dab * 1000)} us/颗" +
                    "（CPU 对照 ${"%.1f".format(cpuDab * 1000)} us/颗）\n" +
                    "    ② 快照本身                    = ${"%.3f".format(snapOnly)} ms；" +
                    "**快照之后第一次画** = ${"%.3f".format(afterSnap)} ms\n" +
                    "       （CPU 那版的同口径实测是 6.9 ms ✗ —— 快照后第一次写会整页拷 33 MB ✓；" +
                    "这里若也在毫秒级 ⇒ GPU 同样是「写时整面拷贝」✗）\n" +
                    "    ③ GPU→CPU 单块回读 130x130     = ${"%.3f".format(readOne)} ms" +
                    "（现在每颗笔尖都做一次 ✓ 一笔 ~135 次 ⇒ 单看这一项 = " +
                    "${"%.1f".format(readOne * 135)} ms/笔 ✗）\n" +
                    "    ④ 快照能编码成 PNG 吗          = ${if (bytes != null) "能 ✓ ${bytes.size} B" else "不能 ✗"}",
            )
            snap.close()
            bmp.close()
            cpu.close()
            gpu.close()
            context.abandon()
        } catch (t: Throwable) {
            println("[㉞-gpu] 量到一半抛异常 ✗ ${t::class.java.name}: ${t.message}")
            t.stackTrace.take(10).forEach { println("[㉞-gpu]     at $it") }
        }
    }
}
