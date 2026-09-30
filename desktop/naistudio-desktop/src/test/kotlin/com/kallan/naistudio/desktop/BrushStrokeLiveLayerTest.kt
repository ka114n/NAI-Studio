package com.kallan.naistudio.desktop

import androidx.compose.ui.graphics.toPixelMap
import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.BrushSpec
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.models.StrokeMode
import com.kallan.naistudio.models.StrokeSpec
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.state.AppState
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **"一笔画得动不动得了"的结构性判据**（第 ㉔ 批开的头，第 ㉜a 批按新链路改口径 ✓）。
 *
 * ⚠️ **口径怎么变的（如实 ✓）**：㉔ / ㉖ 那两条判据量的是"**整页层位图重建了几次**"
 * 和"**活笔画小块封顶几块**"—— 那两样东西**在第 ㉜a 批被整条删掉了** ✗
 * （层真源换成 skiko `Surface` ✓，显示走"可见区回读 → 视口 Surface" ✓，
 * 见 `docs/53` / `docs/54` ✓）。所以判据换成**同一条意思的新说法** ✓：
 *  · 旧：「一笔 100+ 颗笔尖 ⇒ 整页位图重建 ≤ 2」→ 新：「整页位图**根本不存在** ✓：
 *    显示一次都不整页回读（`comicPaintPageReadbacks == 0` ✓）、笔画一次都不整页上传回表面
 *    （`surfaceUploads == 0` ✓）」✓；
 *  · 旧：「活笔画叠加层块数封顶」→ 新：「显示那条链**没有任何"越画越多"的东西** ✓：
 *    层表面恒一张（= 页字节数 ✓）、视口帧恒一个（= 请求的视口尺寸 ✓）」✓。
 * 判据**没有放松** ✓（都还是结构性计数，不是"我觉得快了"✗）。
 *
 * ⚠️ 用的是**测试隔离档案**（构建脚本把 `%APPDATA%` 指到 `build/test-home` ✓，不碰用户目录 ✓）。
 */
class BrushStrokeLiveLayerTest {

    private val platform: Platform = desktopPlatform()

    private fun freshState(): AppState {
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .apply()
        return AppState(platform).also { it.ensureComicBoardLoaded() }
    }

    /** 开一张空白的页 + 把底板层开成绘画会话（和 `ComicPaintStrokeContinuityTest` 同一套 ✓）。 */
    private fun openSession(state: AppState, width: Int, height: Int): ImageEditSession {
        state.setComicBoardBase(width = width, height = height)
        val page = requireNotNull(state.comicBoardPage) { "先决条件：这一页应该在" }
        val base = requireNotNull(page.layers.firstOrNull { it.kind == ComicLayer.Kind.BASE }) {
            "先决条件：底板层应该在"
        }
        assertTrue("底板层必须能开始编辑", state.beginComicLayerPaint(base.id))
        return requireNotNull(state.comicPaintSession) { "开始之后就该有一段会话" }
    }

    /** 视口那一帧里**真的有墨**吗（alpha > 0 的像素数 ✓ —— 层是透明的，墨就是笔迹 ✓）。 */
    private fun inkPixels(frame: androidx.compose.ui.graphics.ImageBitmap): Int {
        val pm = frame.toPixelMap()
        var ink = 0
        for (i in pm.buffer.indices) if (((pm.buffer[i] ushr 24) and 0xFF) != 0) ink++
        return ink
    }

    // ------------------------------------------------------------------
    // 判据 ①（㉜a 新口径）：一笔 100+ 颗笔尖 ⇒ **整页那一份活一次都不干**
    // ------------------------------------------------------------------

    /**
     * 走的是**界面那条真实的路**：
     * `AppState.beginComicPaintStroke` / `comicPaintStrokeTo` ✓，
     * 而且**每一个输入点之后都像界面那样取一次视口那一帧**
     * （`AppState.comicPaintViewportFrame` ✓，就是 `ComicPaintViewportLayer` 每帧干的那件事 ✓）。
     *
     * 判据（结构性 ✓，一条都不许放松 ✗）：
     *  1. 这一笔**真的落了 100 颗以上笔尖** ✓（否则这条测试没意义 ✗）；
     *  2. 每一帧都拿到了视口帧（**一次 null 都不许有** ✗ —— null 就是"这一层画不出来"✗）；
     *  3. **落笔期间就看得见墨** ✓（不是抬笔才出现 ✗）；
     *  4. 显示那条路**一次都没整页回读** ✓（`comicPaintPageReadbacks == 0`）；
     *  5. 这一笔**一次都没把页缓冲整块传回表面** ✓（`surfaceUploads == 0` ——
     *     画笔 / 橡皮是**直接画在层表面上**的 ✓），回读**只有每颗笔尖那一小块** ✓；
     *  6. 显示代价 ∝ **视口像素**、不随页大小走 ✓（视口帧尺寸 = 请求值 ✓）。
     */
    @Test
    fun one_stroke_of_100_plus_dabs_never_touches_the_whole_page() {
        val state = freshState()
        val session = openSession(state, width = 900, height = 1200)
        val pagePixels = 900L * 1200L
        val viewportW = 300
        val viewportH = 400

        state.beginComicPaintStroke(60f, 600f)
        var samples = 0
        var frames = 0
        var inkFrames = 0
        var x = 60f
        while (x < 840f && samples < 400) {
            x += 3f
            val y = 600f + 40f * kotlin.math.sin((x / 90f).toDouble()).toFloat()
            state.comicPaintStrokeTo(x, y)
            // —— 界面每一帧做的那一件事：拿视口那一帧（可见区回读 → 视口 Surface ✓）——
            val frame = state.comicPaintViewportFrame(
                srcX = 0,
                srcY = 0,
                srcW = 900,
                srcH = 1200,
                dstW = viewportW,
                dstH = viewportH,
                revisionKey = state.comicPaintFrameRevision(),
            )
            if (frame != null) {
                frames++
                if (inkPixels(frame) > 0) inkFrames++
            }
            samples++
        }
        val dabs = session.strokeDabStats().dabs
        val readbacksDuringStroke = session.surfaceReadbacks
        val uploadsDuringStroke = session.surfaceUploads
        val pageReadbacksDuringStroke = state.comicPaintPageReadbacks
        state.endComicPaintStroke()
        // 抬笔之后界面还会再取一帧 ✓（这时墨迹已经在层表面上 ✓，照旧只读可见区 ✓）
        state.comicPaintViewportFrame(
            srcX = 0, srcY = 0, srcW = 900, srcH = 1200,
            dstW = viewportW, dstH = viewportH,
            revisionKey = state.comicPaintFrameRevision(),
        )

        println(
            "[㉜a-B1] samples=$samples dabs=$dabs 视口帧=$frames（其中有墨 $inkFrames 帧）" +
                " 整页回读=${state.comicPaintPageReadbacks} 整块上传=$uploadsDuringStroke " +
                " 每笔尖回读=$readbacksDuringStroke 视口=${state.comicPaintViewportWidth}×" +
                "${state.comicPaintViewportHeight}（整页 $pagePixels px）",
        )

        assertTrue(
            "先决条件：这一笔必须真的落够 100 颗以上笔尖（实测 $dabs 颗）—— 不然「整页那一份活」就没意义了 ✗",
            dabs >= 100,
        )
        assertTrue(
            "界面每一帧都要拿到视口帧（实测 $samples 帧里只拿到 $frames 帧）✗ —— " +
                "拿到 null 就是「这一层画不出来」✗",
            frames == samples,
        )
        assertTrue(
            "**落笔期间就看得见墨**（实测 $samples 帧里只有 $inkFrames 帧有墨）✗ —— " +
                "抬笔才出现 = 用户报过的那种「画完一笔过一会才加载完线条」✗",
            inkFrames > 0,
        )
        assertTrue(
            "显示那条路**一次都不许整页回读**（实测 ${state.comicPaintPageReadbacks} 次，笔画期间 $pageReadbacksDuringStroke 次）✗ —— " +
                "整页回读 = 显示代价随页大小走（㉖ 那套 ✗）",
            state.comicPaintPageReadbacks == 0 && pageReadbacksDuringStroke == 0,
        )
        assertTrue(
            "这一笔**不许把页缓冲整块传回表面**（实测 $uploadsDuringStroke 次）✗ —— " +
                "画笔 / 橡皮是直接画在层表面上的 ✓，整块上传只该出现在「盘上已有内容」那一档 ✓",
            uploadsDuringStroke == 0,
        )
        assertTrue(
            "回读必须是**每颗笔尖那一小块**（实测 $readbacksDuringStroke 次 / $dabs 颗笔尖）✗ —— " +
                "次数应该跟着笔尖走、不该是「一次回读一大片」✗",
            readbacksDuringStroke in 1..(dabs + 8),
        )
        assertTrue(
            "显示代价必须 ∝ **视口像素**（实测视口帧 ${state.comicPaintViewportWidth}×" +
                "${state.comicPaintViewportHeight}，要 $viewportW×$viewportH）✗",
            state.comicPaintViewportWidth == viewportW && state.comicPaintViewportHeight == viewportH,
        )
    }

    // ------------------------------------------------------------------
    // 判据 ③（㉜a 新口径）：一笔很长 ⇒ 显示那条链**没有任何"越画越多"的东西**
    // ------------------------------------------------------------------

    /**
     * 用户 2026-09-20 的第二条实机反馈：「**卡顿，我画完一笔过一会才加载完线条**」✗。
     *
     * ㉖ 那批的根因是**越画越慢**：一笔长的会往 `comicPaintLivePatches` 里攒**上百个**小块，
     * 每一块都是一个 `Image` 节点 + 一次纹理上传 ✗。㉜a 把**整条叠加层删掉了** ✗ ——
     * 墨迹从落笔起就在层表面上 ✓，显示每帧只读**可见区** ✓ ⇒ 一笔的长短**不影响**显示那条链 ✓。
     *
     * 判据（结构性 ✓，比"块数封顶"更强 ✓）：
     *  · 层表面**只有一张**、字节数**恒等于页大小** ✓（不随笔画长度增长 ✓）；
     *  · 视口那一帧**恒一个缓存**、尺寸 = 请求的视口 ✓（不随笔画长度增长 ✓）；
     *  · 全程 `comicPaintPageReadbacks == 0` ✓（显示从不整页回读 ✓）；
     *  · 每颗笔尖的回读次数**跟着笔尖走**（不是"一次回读一片"✗）。
     */
    @Test
    fun a_very_long_stroke_accumulates_nothing_in_the_display_chain() {
        val state = freshState()
        val session = openSession(state, width = 2480, height = 3508) // A4：用户实际在用的尺寸 ✓
        val pageBytes = 2480 * 3508 * 4
        // docs/54 里实测的那个视口尺寸 ✓（A4 缩到屏幕上的可见区 ✓）
        val viewportW = 525
        val viewportH = 743

        state.beginComicPaintStroke(60f, 600f)
        var samples = 0
        var x = 60f
        while (x < 1260f && samples < 400) {
            x += 3f
            state.comicPaintStrokeTo(x, 600f + 40f * kotlin.math.sin((x / 90f).toDouble()).toFloat())
            state.comicPaintViewportFrame(
                srcX = 0,
                srcY = 0,
                srcW = 2480,
                srcH = 3508,
                dstW = viewportW,
                dstH = viewportH,
                revisionKey = state.comicPaintFrameRevision(),
            )
            samples++
        }
        val dabs = session.strokeDabStats().dabs
        val layerBytes = session.layerSurfaceBytes
        val readbacks = session.surfaceReadbacks
        val uploads = session.surfaceUploads
        state.endComicPaintStroke()

        println(
            "[㉜a-B3] A4 一笔 $samples 段 / $dabs 颗笔尖：层表面=${layerBytes / 1024 / 1024} MB（页 $pageBytes B）" +
                " 视口帧=${state.comicPaintViewportWidth}×${state.comicPaintViewportHeight}" +
                " 整页回读=${state.comicPaintPageReadbacks} 整块上传=$uploads 每笔尖回读=$readbacks",
        )

        assertTrue("先决条件：这一段必须真的走了足够多次输入（实测 $samples 次）✗", samples >= 100)
        assertTrue(
            "层表面必须**只有一张**、字节数恒等于页大小（实测 $layerBytes / 页 $pageBytes）✗ —— " +
                "随笔画长度增长就是「越画越多」✗",
            layerBytes == pageBytes,
        )
        assertTrue(
            "视口那一帧必须**恒是请求的那个尺寸**（实测 ${state.comicPaintViewportWidth}×" +
                "${state.comicPaintViewportHeight}，要 $viewportW×$viewportH）✗",
            state.comicPaintViewportWidth == viewportW && state.comicPaintViewportHeight == viewportH,
        )
        assertTrue(
            "显示那条路**一次都不许整页回读**（实测 ${state.comicPaintPageReadbacks} 次）✗",
            state.comicPaintPageReadbacks == 0,
        )
        assertTrue(
            "这一笔**不许把页缓冲整块传回表面**（实测 $uploads 次）✗",
            uploads == 0,
        )
        assertTrue(
            "每颗笔尖的回读次数要**跟着笔尖走**（实测 $readbacks 次 / $dabs 颗）✗",
            readbacks in 1..(dabs + 8),
        )
    }

    // ------------------------------------------------------------------
    // 判据 ②：斜线边缘有中间 alpha（抗锯齿 ✓）
    // ------------------------------------------------------------------

    /**
     * 一条 45° 斜线（硬边圆笔尖 ✓ 默认档 ✓）——
     * 沿边缘取样必须出现**中间 alpha**（`1..254` ✓）。
     *
     * 改前：覆盖度是 0/1 二值 ⇒ 斜线上**一个中间 alpha 都没有**（实测 0 个 ⚠️）；
     * 改后：按"像素中心到笔尖轮廓的距离"算 1px 线性羽化 ⇒ 边缘那一圈落在 128..255 ✓，
     * 加上 `highQualitySampling` 打开时的 2×2 超采样还能再多出一档 ✓。
     */
    @Test
    fun a_diagonal_edge_has_intermediate_alpha() {
        fun drawDiagonal(brush: BrushSpec): IntArray {
            val size = 170
            val session = ImageEditSession(size, size, IntArray(size * size))
            session.beginStroke(
                20f, 20f,
                StrokeSpec(
                    mode = StrokeMode.PAINT,
                    brushPixels = 24,
                    color = 0xFF000000.toInt(),
                    brush = brush,
                ),
            )
            session.strokeTo(150f, 150f)
            session.endStroke()
            return session.pixels
        }

        fun survey(pixels: IntArray): Triple<Int, Int, Int> {
            var empty = 0
            var full = 0
            var middle = 0
            for (p in pixels) {
                when (val a = (p ushr 24) and 0xFF) {
                    0 -> empty++
                    255 -> full++
                    else -> {
                        middle++
                        if (a <= 0) empty++
                    }
                }
            }
            return Triple(empty, full, middle)
        }

        val (empty, full, middle) = survey(drawDiagonal(BrushSpec()))
        println("[㉔-B2] 斜线 170×170：alpha=0 有 $empty，alpha=255 有 $full，**中间 alpha** 有 $middle")

        assertTrue("先决条件：斜线必须真的画出来了（不透明像素 $full 个）✗", full > 0)
        assertTrue("先决条件：画布上还该留白（透明像素 $empty 个）✗", empty > 0)
        assertTrue(
            "**斜线边缘必须出现中间 alpha**（`1..254` ✓）—— 实测 $middle 个 ✗。" +
                "改前这一格恒为 **0**（硬边覆盖度 ⇒ 全 0/255 ⚠️）：用户报的「线条锯齿大」就是它 ✓",
            middle > 0,
        )

        // `highQualitySampling`（SAI 的「高品质缩小」✓，面板「更多」里那颗勾选 ✓）——
        // 第 ㉔ 批起它真的映射到"笔尖边缘 2×2 超采样" ✓，这里钉它**也**画得出中间 alpha ✓。
        val (hqsEmpty, hqsFull, hqsMiddle) = survey(drawDiagonal(BrushSpec(highQualitySampling = 1)))
        println("[㉔-B2] 高品质采样开着：alpha=0 有 $hqsEmpty，255 有 $hqsFull，中间 alpha 有 $hqsMiddle")
        assertTrue("高品质采样那一档也必须画得出笔迹（$hqsFull 个实心像素）✗", hqsFull > 0)
        assertTrue(
            "**高品质采样（边缘 2×2 超采样）开着时，中间 alpha 必须还在**（实测 $hqsMiddle 个）✗" +
                " —— 这一档是用户能在面板上选的 ✓，不能是个摆设 ✗",
            hqsMiddle > 0,
        )
    }
}
