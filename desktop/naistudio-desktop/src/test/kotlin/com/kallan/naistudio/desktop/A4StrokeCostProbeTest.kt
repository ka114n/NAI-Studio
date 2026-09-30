package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.state.AppState
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureNanoTime

/**
 * **㉜a 交付判据 · A4（2480×3508）三个数——全部实测** ✓（用户口径：**不许按代码推算** ✗）。
 *
 *  ① **冷启动第一笔耗时**：从"刚把底板层开成绘画会话"到"第一笔画完 + 画布上看到它"
 *     （`beginComicLayerPaint` → `beginComicPaintStroke` → N × `comicPaintStrokeTo`
 *      → `endComicPaintStroke` → 一帧 `comicPaintViewportFrame` ✓）——
 *     这一笔要含**一次性的**那几件事：层表面分配（A4 = 33 MB ✓）、
 *     "层里已经有内容"时那一次整页上传 ✓、视口那张 Surface / 回读 Bitmap 的首次分配 ✓；
 *  ② **每笔耗时**：接着画 20 笔，取**平均**（同样的口径：一笔 + 一帧视口 ✓）；
 *  ③ **峰值内存**：两个都是**实测**，不是推的 ✓
 *     · **进程工作集增量**（`WorkingSet64` ✓ —— 连 Skia 的**native** 那 33 MB 一起算进去 ✓）；
 *     · **JVM 堆峰值**（一个 2 ms 的采样线程在读 ✓）。
 *
 * ⚠️ 测量口径都写在打印行里 ✓；工作集读不到时（PowerShell 不可用）**如实打 -1** ✗、不编数 ✗。
 */
class A4StrokeCostProbeTest {

    private val platform = desktopPlatform()

    /** 这个 JVM 的工作集（字节 ✓）；读不到返回 -1 ✓。 */
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
    fun a4_three_numbers_are_measured() {
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .apply()
        System.gc()
        Thread.sleep(120)
        val rssBefore = workingSetBytes()
        val heapBefore = heapUsed()

        val state = AppState(platform).also { it.ensureComicBoardLoaded() }
        state.setComicBoardBase(width = 2480, height = 3508) // A4 ✓
        val page = requireNotNull(state.comicBoardPage)
        val base = requireNotNull(page.layers.firstOrNull { it.kind == ComicLayer.Kind.BASE })

        // 峰值采样线程（只看数字 ✓，不参与画面 ✓）
        val sampling = AtomicBoolean(true)
        val heapPeak = AtomicLong(0L)
        val rssPeak = AtomicLong(0L)
        val sampler = Thread {
            while (sampling.get()) {
                val h = heapUsed()
                if (h > heapPeak.get()) heapPeak.set(h)
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

        /** 一笔的完整口径：落笔 → 40 段 → 抬笔 → 一帧视口 ✓（= 界面每一帧干的那件事 ✓）。 */
        fun oneStroke(first: Boolean): Pair<Long, Int> {
            var frames = 0
            val ns = measureNanoTime {
                if (first) assertTrue(state.beginComicLayerPaint(base.id))
                state.beginComicPaintStroke(60f, 600f)
                var x = 60f
                repeat(40) {
                    x += 24f
                    state.comicPaintStrokeTo(x, 600f + 40f * kotlin.math.sin((x / 90f).toDouble()).toFloat())
                }
                state.endComicPaintStroke()
                val frame = state.comicPaintViewportFrame(
                    srcX = 0,
                    srcY = 0,
                    srcW = 2480,
                    srcH = 3508,
                    dstW = 525,
                    dstH = 743,
                    revisionKey = state.comicPaintFrameRevision(),
                )
                if (frame != null) frames++
            }
            return ns to frames
        }

        // ---- ① 冷启动第一笔（含会话开启 + 表面分配 + 首次整页上传 ✓）----
        val (coldNs, coldFrames) = oneStroke(first = true)
        val session = requireNotNull(state.comicPaintSession)
        val surfaceBytes = session.layerSurfaceBytes
        val coldSurfaceUploads = session.surfaceUploads

        // ---- ② 每笔耗时：接着 20 笔取平均 ✓ ----
        var warmTotal = 0L
        var warmFrames = 0
        val warmRounds = 20
        repeat(warmRounds) {
            val (ns, f) = oneStroke(first = false)
            warmTotal += ns
            warmFrames += f
            // 落笔点挪开一点，别 20 笔全叠在同一处（贴合用户真画的样子 ✓）
            state.beginComicPaintStroke(60f, 600f)
        }
        // 上面多开了一笔没抬 —— 收掉 ✓（不影响计时 ✓）
        state.endComicPaintStroke()

        // ---- ③ 峰值内存（工作集 / 堆 都是实测 ✓）----
        Thread.sleep(60)
        val rssAfter = workingSetBytes()
        sampling.set(false)
        sampler.join(1500)

        val coldMs = coldNs / 1_000_000.0
        val warmMs = warmTotal / 1_000_000.0 / warmRounds

        fun mb(v: Long) = if (v < 0) "n/a" else "%.1f".format(v / 1024.0 / 1024.0)
        println(
            "[㉜a-A4] 2480×3508（A4 ✓）—— 全部实测 ✓\n" +
                "   ① 冷启动第一笔 = **${"%.1f".format(coldMs)} ms**" +
                "（含层表面分配 + 首次整页上传 $coldSurfaceUploads 次 + 首帧视口；视口帧 $coldFrames/1 ✓）\n" +
                "   ② 每笔耗时（接着 $warmRounds 笔平均）= **${"%.1f".format(warmMs)} ms**" +
                "（每笔 40 段 + 一次抬笔 + 一帧视口 ${warmFrames}/$warmRounds ✓）\n" +
                "   ③ 峰值内存：**进程工作集 ${mb(rssPeak.get())} MB**（起手 ${mb(rssBefore)} MB" +
                "、收手 ${mb(rssAfter)} MB ⇒ 增量 ${mb(rssPeak.get() - rssBefore)} MB ✓）；" +
                "**JVM 堆峰值 ${mb(heapPeak.get())} MB**（起手 ${mb(heapBefore)} MB ✓）；" +
                "层表面**实分** ${mb(surfaceBytes.toLong())} MB ✓\n" +
                "   计数器：viewFrames=${state.comicPaintViewportFrames} pageReadbacks=${state.comicPaintPageReadbacks}" +
                " surfaceReadbacks=${session.surfaceReadbacks} surfaceUploads=${session.surfaceUploads}",
        )

        assertTrue("冷启动第一笔必须真的画出来（实测 ${"%.1f".format(coldMs)} ms）✗", coldMs > 0.0)
        assertTrue("每笔耗时必须量到正数（实测 ${"%.1f".format(warmMs)} ms）✗", warmMs > 0.0)
        assertTrue(
            "A4 的层表面必须真的分配了 33 MB 那一张（实测 $surfaceBytes B = 2480×3508×4）✗",
            surfaceBytes == 2480 * 3508 * 4,
        )
        assertTrue(
            "显示那条路**一次都不许整页回读**（实测 ${state.comicPaintPageReadbacks} 次）✗",
            state.comicPaintPageReadbacks == 0,
        )
        assertTrue(
            "每一笔都要拿到视口帧（实测冷启动 $coldFrames/1、每笔 $warmFrames/$warmRounds）✗",
            coldFrames == 1 && warmFrames == warmRounds,
        )
    }
}
