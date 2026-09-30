package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.BrushSpec
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.models.SkiaDabRaster
import com.kallan.naistudio.state.AppState
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureNanoTime

/**
 * **㉜c · A4（2480×3508）开纸纹的代价——全部实测** ✓（用户口径：**不许按代码推算** ✗）。
 *
 * 四个数（与 `MixBrushA4CostProbeTest` 同一个口径 ✓，好横向比 ✓）：
 *  ① **每颗笔尖耗时**（纸纹每颗多一次 shader + colorFilter 的设置 ✓，这是本批必须量的那一个 ✗）；
 *  ② **每笔耗时**（一笔 = 40 段 + 一次抬笔 ✓）；
 *  ③ **峰值内存 / 回读缓冲峰值**；
 *  ④ **开纸纹 vs 关纸纹的倍数**（同一支笔、只动 `paperOn` ✓）。
 */
class PaperBrushA4CostProbeTest {

    private val platform = desktopPlatform()

    private fun workingSetBytes(): Long {
        val pid = ProcessHandle.current().pid()
        return try {
            val p = ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "(Get-Process -Id $pid).WorkingSet64",
            ).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            out.lineSequence().lastOrNull { it.trim().toLongOrNull() != null }?.trim()?.toLongOrNull() ?: -1L
        } catch (t: Throwable) {
            -1L
        }
    }

    private fun heapUsed(): Long {
        val rt = Runtime.getRuntime()
        return rt.totalMemory() - rt.freeMemory()
    }

    @Test
    fun a4_paper_brush_cost_is_measured() {
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .apply()
        System.gc()
        Thread.sleep(120)
        val rssBefore = workingSetBytes()
        val heapBefore = heapUsed()

        val state = AppState(platform).also { it.ensureComicBoardLoaded() }
        state.setComicBoardBase(width = 2480, height = 3508)
        val page = requireNotNull(state.comicBoardPage)
        val base = requireNotNull(page.layers.firstOrNull { it.kind == ComicLayer.Kind.BASE })

        /** 同一支笔，只动 `paperOn` ✓（`blending` 全关 ⇒ 量到的差**只来自纸纹** ✓）。 */
        fun brush(paperOn: Boolean) = BrushSpec(
            spacing = 8f,
            scattering = 4f,
            sizeJitter = 8f,
            density = 60f,
            opacity = 90f,
            paperOn = paperOn,
            paperStrength = if (paperOn) 50f else 0f,
        )
        state.applyComicPaintBrush(brush(true))
        state.setComicPaintBrush(72)

        val sampling = AtomicBoolean(true)
        val heapPeak = AtomicLong(0L)
        val rssPeak = AtomicLong(0L)
        val sampler = Thread {
            while (sampling.get()) {
                val hh = heapUsed()
                if (hh > heapPeak.get()) heapPeak.set(hh)
                val r = workingSetBytes()
                if (r > rssPeak.get()) rssPeak.set(r)
                try {
                    Thread.sleep(2)
                } catch (t: InterruptedException) {
                    return@Thread
                }
            }
        }
        sampler.isDaemon = true
        sampler.start()

        fun oneStroke(first: Boolean): Triple<Long, Int, Int> {
            var dabs = 0
            var samples = 0
            val ns = measureNanoTime {
                if (first) assertTrue(state.beginComicLayerPaint(base.id))
                state.beginComicPaintStroke(60f, 600f)
                var x = 60f
                repeat(40) {
                    x += 24f
                    state.comicPaintStrokeTo(x, 600f + 40f * kotlin.math.sin((x / 90f).toDouble()).toFloat())
                }
                state.endComicPaintStroke()
                val s = state.comicPaintSession?.strokeDabStats()
                dabs = s?.dabs ?: 0
                samples = s?.samples ?: 0
            }
            return Triple(ns, dabs, samples)
        }

        val (coldNs, coldDabs, _) = oneStroke(first = true)
        val session = requireNotNull(state.comicPaintSession)
        val surfaceBytes = session.layerSurfaceBytes
        val onSurface = session.strokeOnLayerSurface

        var warmTotal = 0L
        var warmDabs = 0
        val warmRounds = 8
        repeat(warmRounds) {
            val (ns, dabs, _) = oneStroke(first = false)
            warmTotal += ns
            warmDabs += dabs
        }
        val paperReadbacks = session.surfaceReadbacks

        // ---- 对照：**同一支笔把纸纹关掉** ✓ ----
        state.applyComicPaintBrush(brush(false))
        var plainTotal = 0L
        var plainDabs = 0
        repeat(warmRounds) {
            val (ns, dabs, _) = oneStroke(first = false)
            plainTotal += ns
            plainDabs += dabs
        }
        val plainOnSurface = state.comicPaintSession?.strokeOnLayerSurface == true

        Thread.sleep(60)
        val rssAfter = workingSetBytes()
        sampling.set(false)
        sampler.join(1500)

        val coldMs = coldNs / 1_000_000.0
        val coldPerDabUs = if (coldDabs > 0) coldNs / 1000.0 / coldDabs else -1.0
        val warmMs = warmTotal / 1_000_000.0 / warmRounds
        val warmPerDabUs = if (warmDabs > 0) warmTotal / 1000.0 / warmDabs else -1.0
        val warmDabsPerStroke = if (warmRounds > 0) warmDabs.toDouble() / warmRounds else 0.0
        val plainMs = plainTotal / 1_000_000.0 / warmRounds
        val plainPerDabUs = if (plainDabs > 0) plainTotal / 1000.0 / plainDabs else -1.0
        val ratio = if (plainPerDabUs > 0) warmPerDabUs / plainPerDabUs else -1.0
        fun mb(v: Long) = if (v < 0) "n/a" else "%.1f".format(v / 1024.0 / 1024.0)

        println(
            "[㉜c-A4] 2480×3508（A4 ✓）**开纸纹**（`paperOn=true / paperStrength=50` ✓、混色全关 ⇒ " +
                "量到的差只来自纸纹 ✓）\n" +
                "   ① 每颗笔尖：冷启动第一笔 ${"%.0f".format(coldPerDabUs)} µs/颗（那笔 $coldDabs 颗、" +
                "共 ${"%.1f".format(coldMs)} ms）；接着 $warmRounds 笔平均 **${"%.0f".format(warmPerDabUs)} µs/颗**" +
                "（每笔 ${"%.0f".format(warmDabsPerStroke)} 颗 ✓）\n" +
                "   ② 每笔耗时：**${"%.1f".format(warmMs)} ms/笔**（40 段 + 一次抬笔 ✓）\n" +
                "   ④ 对照（同一支笔**关掉纸纹** ✓，走的是同一条表面链）：**${"%.0f".format(plainPerDabUs)} µs/颗**、" +
                "**${"%.1f".format(plainMs)} ms/笔** ⇒ **开纸纹是关纸纹的 ${"%.2f".format(ratio)} 倍**\n" +
                "   ③ 峰值内存：进程工作集 ${mb(rssPeak.get())} MB（起手 ${mb(rssBefore)}、收手 ${mb(rssAfter)}" +
                " ⇒ 增量 ${mb(rssPeak.get() - rssBefore)} MB ✓）；JVM 堆峰值 ${mb(heapPeak.get())} MB" +
                "（起手 ${mb(heapBefore)} ✓）；层表面实分 ${mb(surfaceBytes.toLong())} MB ✓\n" +
                "   回读缓冲峰值：**${SkiaDabRaster.peakReadBytes / 1024} KB**" +
                "（${SkiaDabRaster.readBufWidth}×${SkiaDabRaster.readBufHeight} ✓）；" +
                "纸纹那 $warmRounds 笔期间表面回读 ${paperReadbacks} 次\n" +
                "   计数器：viewFrames=${state.comicPaintViewportFrames} pageReadbacks=${state.comicPaintPageReadbacks}" +
                " surfaceReadbacks=${session.surfaceReadbacks} surfaceUploads=${session.surfaceUploads}" +
                " 走表面（开纸纹）=$onSurface 走表面（关纸纹）=$plainOnSurface",
        )

        assertTrue("开纸纹**必须**走层表面（不然量的还是老路 ✗）", onSurface)
        assertTrue("关纸纹也必须走层表面（对照才有意义 ✗）", plainOnSurface)
        assertTrue("每笔必须真的落下笔尖（实测 $warmDabs 颗 ✗）", warmDabs > 0)
        assertTrue("开纸纹时**一次混色采样都不该有**（实测 ${session.strokeDabStats().samples} ✗）",
            session.strokeDabStats().samples == 0)
        assertTrue("每颗笔尖耗时必须量到正数（实测 ${"%.0f".format(warmPerDabUs)} µs ✗）", warmPerDabUs > 0.0)
        assertTrue("每笔耗时必须量到正数（实测 ${"%.1f".format(warmMs)} ms ✗）", warmMs > 0.0)
        assertTrue("开纸纹必须真的更贵（实测比值 ${"%.2f".format(ratio)} ✗）", ratio > 1.0)
        assertTrue(
            "A4 的层表面必须真的是 33 MB 那一张（实测 $surfaceBytes B ✗）",
            surfaceBytes == 2480 * 3508 * 4,
        )
        assertTrue(
            "回读缓冲峰值必须**远小于整页**（实测 ${SkiaDabRaster.peakReadBytes / 1024} KB ✗）",
            SkiaDabRaster.peakReadBytes < 2480 * 3508 * 4 / 8,
        )
        assertTrue(
            "显示那条路一次都不许整页回读（实测 ${state.comicPaintPageReadbacks} 次 ✗）",
            state.comicPaintPageReadbacks == 0,
        )
    }
}
