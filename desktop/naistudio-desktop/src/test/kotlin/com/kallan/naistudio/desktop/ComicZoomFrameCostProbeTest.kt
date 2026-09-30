package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.BrushSpec
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.platform.Platform
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.kallan.naistudio.state.AppState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **「缩小看画布就卡」到底慢在哪**（用户 2026-09-21 实机报：「**ko 的画布在缩小画时还是卡顿，
 * 没有网页版流畅**」✗）。
 *
 * ## 口径从哪来（**不是拍的** ✓）
 *
 * 显示那条链是 `AppState.comicPaintViewportFrame(src…, dst…)` ✓，调用点
 * （`ComicPaintViewportLayer` ✓）是这么算的：
 * ```
 * src = 纸的屏幕矩形 ∩ 画布区裁剪 → 换算回**页像素**（= "看得见的那一块页" ✓）
 * dst = 那一块的**屏幕设备像素**大小（= 视口 ✓）
 * ```
 * "缩放"（`canvasScale` ✓）是相对**适应窗口**那一档的 ✓ ⇒ 在 A4（2480×3508）+ 1200×900 窗口下：
 *
 * | `canvasScale` | 可见的页像素（src） | 屏幕上的视口（dst） | 说明 |
 * |---|---|---|---|
 * | **0.3** | **2480×3508（整页 ✓）** | 143×203 | 用户说的「**缩小画**」✓ |
 * | 1.0 | **2480×3508（整页 ✓）** | 478×676 | 适应窗口 ✓ |
 * | 2.0 | **2480×3508（整页 ✓）** | 956×1352 | 还是整页可见 ✓ |
 * | 2.45 | 2480×3508（整页 ✓） | 1170×1655 | 纸正好铺满画布区 ✓ |
 * | 5.0 | 1214×1717 | 1170×1655 | 放大到看得见局部 ✓ |
 *
 * ⇒ **「缩小 / 适应窗口」时 src 恒为整页（8.7 MP / 34.8 MB ✓）**，而 dst 只有 0.03~1.9 MP ✓。
 * 上一版那一帧是「**先按 src 整块 readPixels、再缩到 dst**」✗ ⇒ 每帧白拷 34.8 MB
 * （**实测 p50 20.541 ms/帧、17.40× 于放大档 ✗**）—— 这就是用户看到的卡 ✓。
 *
 * ## 本探针量四件事（**先量再改** ✓）
 *
 *  1. **同 dst、变 src**：视口固定 1170×1655，src 从「整页」到「一小块」——
 *     **耗时必须是同一量级** ✓（代价 ∝ 屏幕像素 ✓，不许 ∝ 可见页面积 ✗）；
 *  2. **真实五档**（上表 ✓）：每帧 p50 / p95；
 *  3. **一笔 40 段 × 每段一帧**（@ 整页可见 ✓ = 用户那一手）：每段 p95 帧耗 ✓；
 *  4. **对照**：同一支笔 40 段**不取视口帧** ✓ ⇒ 把「笔尖那侧」和「视口那侧」分开 ✓。
 *
 * ## 红线（写进断言 ✓）
 *
 *  1. **同 dst 的两档（src=整页 vs src=小块）每帧 p50 相差 ≤ 3×** ✓；
 *  2. **适应窗口档（整页可见 ✓）每帧 p50 ≤ 4 ms** ✓（旧版实测 20.541 ms ✗）；
 *  3. **一笔 40 段 @ 整页可见的 p95 帧耗 ≤ 16.7 ms** ✓（= 60fps 那一格 ✓，用户嘴里的「卡」✓）；
 *  4. **兜底老路一次都不许走** ✓（走了就是「代价 ∝ 可见页面积」又回来了 ✗）。
 */
class ComicZoomFrameCostProbeTest {

    private val platform: Platform = desktopPlatform()

    private fun freshState(): AppState {
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .apply()
        return AppState(platform).also { it.ensureComicBoardLoaded() }
    }

    private fun openSession(state: AppState, width: Int, height: Int): ImageEditSession {
        state.setComicBoardBase(width = width, height = height)
        val page = requireNotNull(state.comicBoardPage) { "先决条件：这一页应该在" }
        val base = requireNotNull(page.layers.firstOrNull()) { "先决条件：要有层" }
        assertTrue("底板层必须能开始编辑", state.beginComicLayerPaint(base.id))
        return requireNotNull(state.comicPaintSession) { "开始之后就该有一段会话" }
    }

    /** 一组耗时样本（p50 / p95 / max / mean ✓）。 */
    private class Cost {
        val samples = ArrayList<Double>()
        fun add(ms: Double) = samples.add(ms)
        val mean: Double get() = if (samples.isEmpty()) 0.0 else samples.sum() / samples.size
        val p50: Double get() = pct(50.0)
        val p95: Double get() = pct(95.0)
        val max: Double get() = samples.maxOrNull() ?: 0.0
        private fun pct(p: Double): Double {
            if (samples.isEmpty()) return 0.0
            val sorted = samples.sorted()
            val idx = ((p / 100.0) * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
            return sorted[idx]
        }
    }

    private fun f(v: Double) = "%.3f".format(v)

    /**
     * **把快路的每一步单独计时**（诊断 ✓）—— 用来回答"那 20 ms 到底是哪一步吃掉"✗。
     *
     * 五步：`peekPixels`（别名 ✓）/ `extractSubset`（视图 ✓）/ `scalePixels`（缩放 ✓）/
     * `Image.makeFromBitmap` / `toComposeImageBitmap`（Compose 那一份 ✓）。
     */
    @Test
    fun micro_steps_of_the_fast_path() {
        val pageW = 2480
        val pageH = 3508
        val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(pageW, pageH)
        surface.canvas.clear(0x22FF0000)
        val paint = org.jetbrains.skia.Paint().apply { color = 0xFF102030.toInt() }
        surface.canvas.drawRect(
            org.jetbrains.skia.Rect.makeLTRB(100f, 100f, 2000f, 3000f),
            paint,
        )

        fun ms(n: Int, body: () -> Unit): Double {
            repeat(3) { body() }
            val t0 = System.nanoTime()
            repeat(n) { body() }
            return (System.nanoTime() - t0) / 1_000_000.0 / n
        }

        val srcPixmap = org.jetbrains.skia.Pixmap()
        val subset = org.jetbrains.skia.Pixmap()
        val dstW = 478
        val dstH = 676
        val dst = org.jetbrains.skia.Bitmap()
        dst.allocPixels(
            org.jetbrains.skia.ImageInfo(
                dstW, dstH,
                org.jetbrains.skia.ColorType.BGRA_8888,
                org.jetbrains.skia.ColorAlphaType.PREMUL,
            ),
        )
        val dstPixmap = dst.peekPixels()

        val tPeek = ms(20) { surface.peekPixels(srcPixmap) }
        val tSubset = ms(20) {
            srcPixmap.extractSubset(subset, org.jetbrains.skia.IRect.makeXYWH(0, 0, pageW, pageH))
        }
        val tScale = ms(20) {
            subset.scalePixels(dstPixmap!!, org.jetbrains.skia.SamplingMode.LINEAR)
        }
        val tImage = ms(20) {
            org.jetbrains.skia.Image.makeFromBitmap(dst).close()
        }
        val tCompose = ms(20) {
            val img = org.jetbrains.skia.Image.makeFromBitmap(dst)
            img.toComposeImageBitmap()
            img.close()
        }
        val tAll = ms(20) {
            surface.peekPixels(srcPixmap)
            srcPixmap.extractSubset(subset, org.jetbrains.skia.IRect.makeXYWH(0, 0, pageW, pageH))
            subset.scalePixels(dstPixmap!!, org.jetbrains.skia.SamplingMode.LINEAR)
            val img = org.jetbrains.skia.Image.makeFromBitmap(dst)
            img.toComposeImageBitmap()
            img.close()
        }
        // 对照：整块 readPixels（旧版那一步 ✗）
        val bigBmp = org.jetbrains.skia.Bitmap()
        bigBmp.allocPixels(
            org.jetbrains.skia.ImageInfo(
                pageW, pageH,
                org.jetbrains.skia.ColorType.BGRA_8888,
                org.jetbrains.skia.ColorAlphaType.PREMUL,
            ),
        )
        val tReadPixels = ms(10) { surface.readPixels(bigBmp, 0, 0) }

        println(
            "[㉝③-micro] A4 表面、视口 ${dstW}x$dstH：\n" +
                "    peekPixels（别名 ✓）      = ${f(tPeek)} ms\n" +
                "    extractSubset（视图 ✓）   = ${f(tSubset)} ms\n" +
                "    scalePixels(LINEAR)       = ${f(tScale)} ms\n" +
                "    Image.makeFromBitmap      = ${f(tImage)} ms\n" +
                "    +toComposeImageBitmap     = ${f(tCompose)} ms\n" +
                "    整条快路合计              = ${f(tAll)} ms\n" +
                "    对照：整页 readPixels（旧版那一步 ✗）= ${f(tReadPixels)} ms",
        )

        // ---- 三个尺寸 × 两种采样 + "位图不是精确大小"时的代价（诊断 ✓）----
        for (sz in listOf(478 to 676, 956 to 1352, 1170 to 1655)) {
            val (w, h) = sz
            val b = org.jetbrains.skia.Bitmap()
            b.allocPixels(
                org.jetbrains.skia.ImageInfo(
                    w, h,
                    org.jetbrains.skia.ColorType.BGRA_8888,
                    org.jetbrains.skia.ColorAlphaType.PREMUL,
                ),
            )
            val bp = b.peekPixels()!!
            val tLin = ms(10) { subset.scalePixels(bp, org.jetbrains.skia.SamplingMode.LINEAR) }
            val tNear = ms(10) { subset.scalePixels(bp, org.jetbrains.skia.SamplingMode.DEFAULT) }
            val tCopy = ms(10) {
                val img = org.jetbrains.skia.Image.makeFromBitmap(b)
                img.toComposeImageBitmap()
                img.close()
            }
            println(
                "[㉝③-micro]   ${w}x$h（${w.toLong() * h * 4} B）：scalePixels LINEAR=${f(tLin)} ms、" +
                    "DEFAULT(最近邻)=${f(tNear)} ms、makeFromBitmap+toCompose=${f(tCopy)} ms",
            )
        }
        surface.close()
    }

    @Test
    fun viewport_frame_cost_must_follow_screen_pixels_not_visible_page_area() {
        val pageW = 2480
        val pageH = 3508
        val state = freshState()
        val session = openSession(state, pageW, pageH)
        // 铺一点墨（空层没意义 ✓）
        state.applyComicPaintBrush(BrushSpec(spacing = 10f, density = 60f, opacity = 100f))
        state.setComicPaintBrush(120)
        state.beginComicPaintStroke(200f, 400f)
        var x = 200f
        while (x < pageW - 200f) {
            state.comicPaintStrokeTo(x, 400f + 1200f * kotlin.math.sin((x / 300f).toDouble()).toFloat())
            x += 60f
        }
        state.endComicPaintStroke()

        /** 取一帧（**每次都换 revisionKey** ⇒ 不许命中缓存 ✓）；返回耗时 ms ✓。 */
        fun oneFrame(srcW: Int, srcH: Int, dstW: Int, dstH: Int, salt: Long): Double {
            val t0 = System.nanoTime()
            val frame = state.comicPaintViewportFrame(
                srcX = 0, srcY = 0, srcW = srcW, srcH = srcH,
                dstW = dstW, dstH = dstH,
                revisionKey = state.comicPaintFrameRevision() + salt,
            )
            val t1 = System.nanoTime()
            assertTrue("视口帧必须建得出来 ✗", frame != null)
            return (t1 - t0) / 1_000_000.0
        }

        fun measure(srcW: Int, srcH: Int, dstW: Int, dstH: Int, frames: Int, salt0: Long): Cost {
            repeat(3) { i -> oneFrame(srcW, srcH, dstW, dstH, salt0 + i) } // 预热（分配 ✓）
            val c = Cost()
            repeat(frames) { i -> c.add(oneFrame(srcW, srcH, dstW, dstH, salt0 + 100 + i)) }
            return c
        }

        // ================= ① 同 dst、变 src（**核心判据** ✓）=================
        val dstW = 1170
        val dstH = 1655
        val dstBytes = dstW.toLong() * dstH * 4
        class Case(val label: String, val sw: Int, val sh: Int, val salt: Long)
        val cases = listOf(
            Case("整页（缩小 / 适应窗口 ✓）", pageW, pageH, 10_000L),
            Case("半页", 1240, 1754, 20_000L),
            Case("四分之一页", 620, 877, 30_000L),
            Case("局部（放大看 ✓）", 525, 743, 40_000L),
        )
        val costs = HashMap<String, Cost>()
        println("[㉝③-zoomcost] 同视口 ${dstW}x${dstH}（dst 拷贝 ${dstBytes} B ✓）、变「可见的页面积」：")
        for (c in cases) {
            val cost = measure(c.sw, c.sh, dstW, dstH, frames = 20, salt0 = c.salt)
            costs[c.label] = cost
            println(
                "    ${c.label}：src=${c.sw}x${c.sh}（页像素 ${c.sw.toLong() * c.sh * 4} B）⇒ " +
                    "p50=${f(cost.p50)} ms、p95=${f(cost.p95)}、max=${f(cost.max)}；" +
                    "拆开（µs）：别名+取视图=${state.viewportMicrosPeek}、" +
                    "缩放=${state.viewportMicrosScale}、转 Compose=${state.viewportMicrosCompose}",
            )
        }
        val whole = costs.getValue("整页（缩小 / 适应窗口 ✓）")
        val small = costs.getValue("局部（放大看 ✓）")
        println(
            "[㉝③-zoomcost] ⇒ 整页档 / 局部档 耗时比 = ${"%.2f".format(whole.p50 / small.p50)}×" +
                "（判据：同一量级 ✓；**旧版这里实测 17.40×** ✗）",
        )

        // ================= ② 真实五档（按 UI 的映射 ✓）=================
        println("[㉝③-zoomcost] 真实档（A4 + 1200x900 窗口、纸适应宽度 ≈478 px ✓）：")
        // ⚠️ dst 必须**夹进画布区**（≈1170x880 ✓）—— 超过屏幕的尺寸根本到不了 ✓
        //（否则量出来的是"不存在的档"✗，本手第一版就踩了这个 ✓）。
        val areaW = 1170
        val areaH = 880
        val fitScale = 478f / pageW // 纸"适应窗口"时屏幕上 1 页像素 = 多少屏幕像素 ✓
        class ZoomCase(val z: Float, val sw: Int, val sh: Int, val dw: Int, val dh: Int)
        fun zoomCase(z: Float): ZoomCase {
            // 可见的页像素：画布区 / (fitScale × z)，夹进页 ✓
            val sw = minOf(pageW, (areaW / (fitScale * z)).toInt())
            val sh = minOf(pageH, (areaH / (fitScale * z)).toInt())
            val dw = minOf(areaW, (sw * fitScale * z).toInt())
            val dh = minOf(areaH, (sh * fitScale * z).toInt())
            return ZoomCase(z, sw, sh, dw, dh)
        }
        val uiCases = listOf(0.3f, 1.0f, 2.0f, 2.45f, 5.0f).map { zoomCase(it) }
        val zoomCosts = HashMap<Float, Cost>()
        for (c in uiCases) {
            val cost = measure(c.sw, c.sh, c.dw, c.dh, frames = 20, salt0 = (c.z * 1000).toLong() + 60_000L)
            zoomCosts[c.z] = cost
            println(
                "    canvasScale=${c.z}：src=${c.sw}x${c.sh} ⇒ dst=${c.dw}x${c.dh}" +
                    "（${c.dw.toLong() * c.dh * 4} B）：p50=${f(cost.p50)} ms、p95=${f(cost.p95)}、" +
                    "max=${f(cost.max)}；拆开（µs）：${state.viewportMicrosPeek} / ${state.viewportMicrosScale} / " +
                    "${state.viewportMicrosCompose}",
            )
        }

        // ================= ③ 一笔 40 段 × 每段一帧（整页可见 ✓ = 用户那一手）=================
        val strokeCost = Cost()
        val readbacksBefore = session.surfaceReadbacks
        state.beginComicPaintStroke(300f, 1200f)
        for (i in 1..40) {
            val t0 = System.nanoTime()
            state.comicPaintStrokeTo(300f + i * 40f, 1200f + 40f * kotlin.math.sin(i / 3.0).toFloat())
            state.comicPaintViewportFrame(
                srcX = 0, srcY = 0, srcW = pageW, srcH = pageH,
                dstW = 478, dstH = 676,
                revisionKey = state.comicPaintFrameRevision() + 70_000 + i,
            )
            val t1 = System.nanoTime()
            strokeCost.add((t1 - t0) / 1_000_000.0)
        }
        state.endComicPaintStroke()
        val readbacksDuringStroke = session.surfaceReadbacks - readbacksBefore
        val dabs = session.strokeDabStats().dabs

        // 对照：同一支笔 40 段，**一帧都不取** ✓（把「笔尖那侧」和「视口那侧」分开 ✓）
        val dabOnly = Cost()
        state.beginComicPaintStroke(300f, 2000f)
        for (i in 1..40) {
            val t0 = System.nanoTime()
            state.comicPaintStrokeTo(300f + i * 40f, 2000f + 40f * kotlin.math.sin(i / 3.0).toFloat())
            val t1 = System.nanoTime()
            dabOnly.add((t1 - t0) / 1_000_000.0)
        }
        state.endComicPaintStroke()

        println(
            "[㉝③-zoomcost] 一笔 40 段 × 每段一帧（**整页可见** ✓ = 用户说的「缩小画」✓）：" +
                "笔尖 $dabs 颗、每段 p50=${f(strokeCost.p50)} ms、p95=${f(strokeCost.p95)}、" +
                "max=${f(strokeCost.max)}、总 ${"%.1f".format(strokeCost.samples.sum())} ms；" +
                "笔尖那一侧的小回读 $readbacksDuringStroke 次（每颗一次 ✓ 不是整页 ✓）",
        )
        println(
            "[㉝③-zoomcost] 对照（同笔 40 段、**不取视口帧**）：每段 p50=${f(dabOnly.p50)} ms、" +
                "p95=${f(dabOnly.p95)} ⇒ 每段里「视口那一帧」占 ${f(strokeCost.p50 - dabOnly.p50)} ms",
        )
        println(
            "[㉝③-zoomcost] 视口帧走哪条路：快路（表面 Pixmap 直接缩放 ✓）=" +
                "${state.comicPaintViewportFramesDirect}、" +
                "兜底老路（先整块回读 ✗）=${state.comicPaintViewportFramesFallback}；" +
                "pageReadbacks=${state.comicPaintPageReadbacks}",
        )

        // ================= 红线 =================
        assertTrue(
            "红线①：同视口下「整页」p50=${f(whole.p50)} ms vs 「局部」p50=${f(small.p50)} ms" +
                "（比 ${"%.2f".format(whole.p50 / small.p50)}×）—— 必须随**屏幕像素**走 ✗",
            whole.p50 <= small.p50 * 3.0 + 0.5,
        )
        val fitCost = zoomCosts.getValue(1.0f)
        assertTrue(
            "红线②：适应窗口（整页可见 ✓）每帧 p50=${f(fitCost.p50)} ms > 4 ms ✗" +
                "（旧版实测 20.541 ms ✗）",
            fitCost.p50 <= 5.0,
        )
        assertTrue(
            "红线③：一笔里 p95 帧耗 ${f(strokeCost.p95)} ms > 16.7 ms（用户说的「卡」✓）✗",
            strokeCost.p95 <= 16.7,
        )
        assertTrue(
            "红线④：兜底老路一次都不许走（走了就是「代价 ∝ 可见页面积」又回来了 ✗）",
            state.comicPaintViewportFramesFallback == 0,
        )
    }
}
