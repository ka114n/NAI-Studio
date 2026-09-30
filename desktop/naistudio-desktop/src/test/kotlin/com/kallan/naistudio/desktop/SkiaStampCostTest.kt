package com.kallan.naistudio.desktop

import com.kallan.naistudio.models.BrushSpec
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.models.SkiaDabRaster
import com.kallan.naistudio.models.StrokeMode
import com.kallan.naistudio.models.StrokeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **㉙ 批：A4 实测 + "回读快路" / "预乘口径"两条断言** ✓
 *
 * 用户/上级口径：数字**必须实测**，不许按代码读 ✗。这一跑量四件事（A4 = 2480×3508 ✓）：
 *  ① 一笔 1000+ 颗笔尖的**打点总耗时**（`strokeTo` 全部，含 Skia 打点 + 回读那一小块 ✓）；
 *  ② **抬笔收尾**耗时（`endStroke` ✓ —— ㉜a 起这里**没有合成** ✓，只有历史按块记账 ✓）；
 *  ③ **回读缓冲**峰值内存（[SkiaDabRaster.peakReadBytes] = 真的分配了多少字节 ✓）——
 *     **两档笔刷各量一次**（45 px 与 600 px ✓）：这是"只增不减"的证据，也证明它**不是整页** ✓
 *    （A4 整页 = 2480×3508×4 = **34.8 MB** ✗，而回读缓冲只跟着**最大那颗笔尖的包围盒**长 ✓）。
 *    ⚠️ **㉜a 改过口径**（如实 ✓）：从前这里有"Scratch 画布峰值"（`peakSurfaceBytes`）——
 *    打点改成**直接画在层表面上**之后那张 scratch 画布**不存在了** ✗，所以换成同一条意思的
 *    "回读缓冲峰值"✓，并**加上**层表面自己的字节数（= 页大小、一张 ✓，见 `docs/54` ✓）；
 *  ④ **回读走的是快路**：`fastPathReads > 0` 且 `slowPathReads == 0` ✓。
 *
 * ## 两个被实测钉死的口径（各一条断言）
 *
 * 1. **预乘**：Skia 的离屏表面是 `N32Premul` ⇒ 读回来的字节是**预乘**的 ⇒ 合成前必须除一次 ✓。
 *    用"白 + 50% 透明"钉死（预乘 ⇒ R≈128；非预乘 ⇒ R=255 ✓，一眼可分 ✓）。
 *    ⚠️ ㉘ 批就是这条没钉住，多除了一次 alpha，抗锯齿中间灰阶像素 1694 → **8801（+419%）** ✗。
 * 2. **快路**：㉘ 批的 `bitmap.readPixels(info, 0, 0, w * 4)` 形参序写错 ✗（skiko 的真实序是
 *    `(dstInfo, dstRowBytes, srcX, srcY)` ✓）⇒ 恒返回 null ⇒ **每颗笔尖**都掉进
 *    `peekPixels().getColor(col, row)` 逐像素 JNI 慢路 ✗✗。现在这两条断言把它焊死 ✓。
 */
class SkiaStampCostTest {

    private fun neutralBrush() = BrushSpec(
        paperOn = false,
        paperStrength = 0f,
        spreadNoise = false,
        spreadNoiseStrength = 0f,
        blending = 0f,
        water = 0f,
        colorStretch = 0f,
        sizeJitter = 0f,
        wxJitter = 0f,
        countJitter = 0f,
        scattering = 0f,
        count = 1,
        hueJitter = 0f,
        saturationJitter = 0f,
        brightnessJitter = 0f,
        colJitter = 0f,
        opacity = 100f,
        spacing = 10f,
        wxRatio = 0f,
        angleControl = 1,
        angle = 0f,
        angleJitter = 0f,
        integerPosition = 0,
        allDirScattering = 0,
        gaussianDistribution = 0,
        applyToEachShape = 0,
        scaling = 100f,
        shapeSize = 100f,
        absoluteSize = 0,
        density = 100f,
        minDensity = 0f,
        pressureAlpha = false,
    )

    // ------------------------------------------------------------------
    // 口径 ①：读回来是**预乘**
    // ------------------------------------------------------------------

    @Test
    fun premultiply_convention_is_pinned() {
        // 白（0xFFFFFF）+ alpha 128 ⇒ 预乘存储里 RGB 会被"乘过" ⇒ ≈128；非预乘会是 255 ✓
        val raw = SkiaDabRaster.debugRawPixel(0xFFFFFF, 128)
        assertTrue("自检画布没画出来（lastError=${SkiaDabRaster.lastError}）✗", raw != null)
        val a = (raw!! ushr 24) and 0xFF
        val r = (raw ushr 16) and 0xFF
        println("[㉙-口径] 白+alpha128 的原始像素 = A=$a R=$r（预乘应为 ≈128 ✓，非预乘会是 255 ✗）")
        // ⚠️ 这条同时把"回读真的拿到了非零字节"钉住 ✓ —— 零墨迹那条死胡同
        //（`installPixels` 把我们的数组拷进 native 副本 ✗）当场就会在这里红 ✓。
        assertEquals("alpha 必须是 128 ✗（读到 0 = 回读根本没拿到像素 ✗）", 128, a)
        assertTrue(
            "读回来必须是**预乘**（R≈128）✗ —— 实测 R=$r；若是 255 说明是非预乘，那就**不能再除 alpha** ✗",
            r in 120..136,
        )
    }

    // ------------------------------------------------------------------
    // A4 实测（三个数）+ 快路断言
    // ------------------------------------------------------------------

    @Test
    fun a4_skia_stamp_cost_is_measured() {
        val w = 2480
        val h = 3508
        val session = ImageEditSession(w, h, IntArray(w * h))

        // ---- 档 A：45 px 笔刷（日常档 ✓），一笔 1000+ 颗笔尖 ----
        SkiaDabRaster.resetProbeCounters()
        session.beginStroke(
            200f,
            400f,
            StrokeSpec(
                mode = StrokeMode.PAINT,
                brushPixels = 45,
                color = 0xFF000000.toInt(),
                brush = neutralBrush(),
                minRadiusRatio = 0.25f,
                pressureCurve = 1f,
            ),
            pressure = 0.3f,
        )
        var x = 200f
        var samples = 0
        val stampMs = kotlin.system.measureTimeMillis {
            while (x < 1700f && samples < 400) {
                x += 4f
                val y = 400f + 300f * kotlin.math.sin((x / 130f).toDouble()).toFloat()
                session.strokeTo(x, y, 0.3f + 0.7f * ((x - 200f) / 1500f))
                samples++
            }
        }
        val mergeMs = kotlin.system.measureTimeMillis { session.endStroke() }
        val peakSmall = SkiaDabRaster.peakReadBytes
        val scratchSmall = "${SkiaDabRaster.readBufWidth}x${SkiaDabRaster.readBufHeight}"
        val dabs = session.strokeDabStats().dabs
        val fastAfterSmall = SkiaDabRaster.fastPathReads
        val slowAfterSmall = SkiaDabRaster.slowPathReads

        println(
            "[㉙-A4] A4 2480×3508（整页位图 = ${w.toLong() * h * 4 / 1024 / 1024} MB）\n" +
                "   档A 45px 笔刷：$samples 段 / $dabs 颗笔尖\n" +
                "   ① 打点总耗时 = **${stampMs} ms**（${"%.3f".format(stampMs.toDouble() / maxOf(1, dabs))} ms/颗笔尖）\n" +
                "   ② 抬笔收尾   = **${mergeMs} ms**（㉜a：没有合成这一步 ✓）\n" +
                "   ③ 回读缓冲峰值 = **${peakSmall} B = ${"%.2f".format(peakSmall / 1024.0 / 1024.0)} MB**（$scratchSmall）" +
                "；层表面 = ${session.layerSurfaceBytes / 1024 / 1024} MB（页大小 ✓，一张 ✓）\n" +
                "   回读：快路 $fastAfterSmall 次 / 慢路 $slowAfterSmall 次（慢路必须为 0 ✓）",
        )

        assertTrue("档A 必须真的落够 100 颗以上笔尖（实测 $dabs）✗", dabs >= 100)
        assertTrue("Skia 打点必须真的发生（peakReadBytes=$peakSmall）✗", peakSmall > 0)
        assertTrue("打点总耗时不该是秒级（实测 ${stampMs} ms）✗ —— 超了就说明还在跑慢路 ✗", stampMs < 3000)

        // ---- 口径 ②：**回读真的走快路**（㉘ 批那条恒 null + 逐像素慢路的钉子 ✓）----
        assertTrue(
            "必须至少有一次 `Bitmap.readPixels` 一把读成功 ✗（实测快路 $fastAfterSmall 次）——" +
                " 0 次就说明回读又掉进慢路了 ✗",
            fastAfterSmall > 0,
        )
        assertEquals(
            "**慢路（peekPixels/getColor 逐像素 JNI）必须一次都不走** ✗（实测 $slowAfterSmall 次）——" +
                " 走了就是 A4 上几百 ms/笔的手感炸弹 ✓",
            0L,
            slowAfterSmall,
        )

        // ---- 档 B：600 px 大笔刷 —— 量 scratch 峰值内存到底跟着谁长 ✓ ----
        val big = ImageEditSession(w, h, IntArray(w * h))
        big.beginStroke(
            200f,
            1200f,
            StrokeSpec(
                mode = StrokeMode.PAINT,
                brushPixels = 600,
                color = 0xFF000000.toInt(),
                brush = neutralBrush(),
                minRadiusRatio = 1f,
                pressureCurve = 1f,
            ),
            pressure = 1f,
        )
        var bx = 200f
        var bigSamples = 0
        val bigMs = kotlin.system.measureTimeMillis {
            while (bx < 2200f && bigSamples < 40) {
                bx += 60f
                big.strokeTo(bx, 1200f, 1f)
                bigSamples++
            }
        }
        val bigDabs = big.strokeDabStats().dabs
        val peakBig = SkiaDabRaster.peakReadBytes
        big.endStroke()

        val pageBytes = w.toLong() * h * 4
        println(
            "   档B 600px 笔刷：$bigSamples 段 / $bigDabs 颗笔尖，打点 ${bigMs} ms（${"%.2f".format(bigMs.toDouble() / maxOf(1, bigDabs))} ms/颗）\n" +
                "   ③ 回读缓冲峰值（两档取大）= **${peakBig} B = ${"%.2f".format(peakBig / 1024.0 / 1024.0)} MB**" +
                "（${SkiaDabRaster.readBufWidth}x${SkiaDabRaster.readBufHeight}；整页 = ${pageBytes / 1024 / 1024} MB）",
        )
        assertTrue("档B 必须真的落笔（实测 $bigDabs 颗）✗", bigDabs >= 10)
        assertTrue("600px 笔刷下回读缓冲必须跟着长大（实测 $peakBig B）✗", peakBig > peakSmall)
        assertTrue(
            "回读缓冲**只该跟着最大笔尖包围盒长**，绝不该是整页 ✗（实测 $peakBig B vs 整页 $pageBytes B）",
            peakBig.toLong() < pageBytes / 4,
        )
        assertEquals("档B 也不许走慢路 ✗", 0L, SkiaDabRaster.slowPathReads - slowAfterSmall)
    }

    // ------------------------------------------------------------------
    // 大笔刷那 12 ms/颗 到底花在哪（拆开量 ✓）—— 只打印，不当判据 ✓
    // ------------------------------------------------------------------

    /**
     * 600 px 笔刷一颗笔尖 ≈ 601×601 px。把它拆成"清屏 / 画椭圆 / 表面→位图 / 位图→数组"四段，
     * 每段单独跑 60 次取平均 ⇒ 一眼看出该优化哪一段 ✓（**只打印，不判定** ✓）。
     */
    @Test
    fun where_the_big_brush_time_goes() {
        val n = 601
        val rounds = 60
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(n, n)
        val info = org.jetbrains.skia.ImageInfo(
            n, n,
            org.jetbrains.skia.ColorType.BGRA_8888,
            org.jetbrains.skia.ColorAlphaType.PREMUL,
        )
        val bmp = org.jetbrains.skia.Bitmap()
        bmp.allocPixels(info)
        val paint = org.jetbrains.skia.Paint().apply {
            isAntiAlias = true
            color = 0xFF000000.toInt()
        }
        val oval = org.jetbrains.skia.Rect.makeLTRB(0f, 0f, n.toFloat(), n.toFloat())
        var sink = 0

        fun bench(label: String, block: () -> Unit): Double {
            repeat(5) { block() } // 预热 ✓
            val ns = kotlin.system.measureNanoTime { repeat(rounds) { block() } }
            val ms = ns / 1_000_000.0 / rounds
            println("   $label = ${"%.3f".format(ms)} ms/次")
            return ms
        }

        val clear = bench("① canvas.clear(0)（601×601 memset）") { surface.canvas.clear(0) }
        val draw = bench("② drawOval(抗锯齿) ") { surface.canvas.drawOval(oval, paint) }
        val toBitmap = bench("③ Surface.readPixels(Bitmap)") {
            if (!surface.readPixels(bmp, 0, 0)) sink++
        }
        val toArray = bench("④ Bitmap.readPixels → ByteArray") {
            val b = bmp.readPixels(info, n * 4, 0, 0)
            if (b == null) sink++ else sink += b.size
        }
        println(
            "[㉙-A4 拆解] 600px 一颗笔尖（601×601）Skia 四段合计 ≈ " +
                "${"%.2f".format(clear + draw + toBitmap + toArray)} ms ✓ —— " +
                "对比「档B」那行的实测整颗耗时：扣掉这四段之后剩下的就是" +
                "「合进活笔画缓冲的逐像素循环」+「缓冲按 256 格子扩容重拷」✓" +
                "（㉙ 批靠两条**精确**快路把这段压下去了：源不透明 ⇒ 逐位就是源 ✓ / 目标全透明 ⇒ 也就是源 ✓）",
        )
        assertTrue("拆解必须真的跑起来（sink=$sink）✗", sink > 0)
    }
}
