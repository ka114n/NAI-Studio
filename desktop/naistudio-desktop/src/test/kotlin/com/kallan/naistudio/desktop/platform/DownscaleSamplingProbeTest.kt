package com.kallan.naistudio.desktop.platform

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.state.AppState
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test

/**
 * **㉖ 批的"到底改没改到"探针**（临时用 ✓，量完可以删 ✓）。
 *
 * 用户 2026-09-20 在**新 jar**（21:59 重启 ✓）上实机说「软件上没感觉改了」✗ ——
 * 那就不猜了，把两件事**在进程里量出来**：
 *
 *  ① `Image(filterQuality=Low/High)` 把一张大图**缩小**画出来，
 *     边缘到底有没有差别？（= 我那条 mipmap 修复在**这条渲染栈**上有没有用 ✓）
 *  ② 抬笔那一下（`endComicPaintStroke()` + 重建整页显示位图）**花多少毫秒**？
 *     （= 用户说的"画完一笔过一会才加载完线条"到底是不是这一下 ✓）
 *
 * 全部走 `renderComposeScene`（和实机同一套 Skia 绘制栈 ✓，见 `ComicOverlayDrawTest` 的说明 ✓）。
 */
class DownscaleSamplingProbeTest {

    private val platform = desktopPlatform()

    /** 造一张"细斜线 + 细横线"的页大小图（黑线 / 白底 ✓）。 */
    private fun lineArt(width: Int, height: Int): ImageBitmap {
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        // 1px 粗的 45° 斜线（最能暴露锯齿 ✓）
        var t = 0
        while (t < minOf(width, height)) {
            pixels[t * width + t] = 0xFF000000.toInt()
            t++
        }
        // 1px 细横线
        val y = height / 3
        for (x in 0 until width) pixels[y * width + x] = 0xFF000000.toInt()
        return requireNotNull(platform.images.composeImageOf(pixels, width, height))
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun draw(image: ImageBitmap, w: Int, h: Int, quality: FilterQuality): IntArray? {
        val rendered = renderComposeScene(w, h, Density(1f)) {
            Box(Modifier.fillMaxSize().background(Color.White)) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    filterQuality = quality,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val png = rendered.encodeToData(EncodedImageFormat.PNG)?.bytes ?: return null
        val decoded = platform.images.decodeBytes(png) ?: return null
        val out = platform.images.pixelsOf(decoded)
        decoded.recycle()
        return out
    }

    /** 灰阶分布：统计"既不是纯黑也不是纯白"的像素数（= 抗锯齿中间档越多越顺 ✓）。 */
    private fun survey(pixels: IntArray?): Triple<Int, Int, Int> {
        if (pixels == null) return Triple(-1, -1, -1)
        var black = 0
        var white = 0
        var mid = 0
        val levels = HashSet<Int>()
        for (p in pixels) {
            val v = p and 0xFF // 灰阶：R=G=B ✓
            when {
                v <= 8 -> black++
                v >= 247 -> white++
                else -> {
                    mid++
                    levels.add(v)
                }
            }
        }
        return Triple(black, white, levels.size)
    }

    @Test
    fun probe_downscale_quality() {
        // 页 992×1404 → 屏上 248×351（**4× 缩小** ✓，和实机 A4 那 3.5× 同量级 ✓）
        val art = lineArt(992, 1404)
        val low = survey(draw(art, 248, 351, FilterQuality.Low))
        val high = survey(draw(art, 248, 351, FilterQuality.High))
        val none = survey(draw(art, 248, 351, FilterQuality.None))
        println("[㉖-probe] 4× 缩小：黑/白/中间灰阶档数")
        println("  None  : black=${none.first} white=${none.second} 灰阶档=${none.third}")
        println("  Low   : black=${low.first} white=${low.second} 灰阶档=${low.third}")
        println("  High  : black=${high.first} white=${high.second} 灰阶档=${high.third}")
    }

    /**
     * ⚠️ **第 ㉜a 批改过口径**（如实 ✓）：㉖ 那一版量的是"抬笔 + **重建整页显示位图**"多少毫秒 ——
     * 那条链（`comicPaintImage()` / `comicPaintBitmapBuilds`）**已经整条删掉了** ✗
     * （层真源 = skiko `Surface` ✓，显示走"可见区回读 → 视口 Surface" ✓，见 `docs/53` / `docs/54` ✓）。
     *
     * 现在量的是**新链路**抬笔那一下 + **一帧视口**：
     *  ① `session.endStroke()`（抬笔收尾：只有历史按块记账 ✓，**没有合成** ✓）；
     *  ② `state.endComicPaintStroke()` + 一次 `comicPaintViewportFrame`（界面每帧干的那件事 ✓）。
     *
     * 判据（结构性 ✓）：抬笔**不许**再多出任何"整页"的活 ✓ —— 显示那条路的整页回读计数
     * `comicPaintPageReadbacks` 必须**一次都不动** ✓。
     */
    @Test
    fun probe_post_stroke_display_cost_ms() {
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .apply()
        val state = AppState(platform).also { it.ensureComicBoardLoaded() }
        state.setComicBoardBase(width = 2480, height = 3508) // A4：用户实际在用的尺寸 ✓
        val page = requireNotNull(state.comicBoardPage)
        val base = requireNotNull(page.layers.firstOrNull { it.kind == ComicLayer.Kind.BASE })
        state.beginComicLayerPaint(base.id)

        // 先画一笔（模拟用户）→ 量"抬笔 + 一帧视口"这一下
        state.beginComicPaintStroke(100f, 100f)
        var x = 100f
        while (x < 1200f) {
            x += 4f
            state.comicPaintStrokeTo(x, 1200f + 300f * kotlin.math.sin((x / 120f).toDouble()).toFloat())
        }
        // ---- 拆开量 ----
        // ① 抬笔收尾（㉜a：只有"这一笔碰过的块"记成历史的一步 ✓，没有合成 ✓）
        val session = requireNotNull(state.comicPaintSession)
        val t1 = kotlin.system.measureTimeMillis { session.endStroke() }
        // ② 抬笔通知 + **一帧视口**（可见区回读 → 缩到视口 → Compose 位图 ✓）
        val pageReadbacksBefore = state.comicPaintPageReadbacks
        val t2 = kotlin.system.measureTimeMillis {
            state.endComicPaintStroke()
            state.comicPaintViewportFrame(
                srcX = 0,
                srcY = 0,
                srcW = 2480,
                srcH = 3508,
                dstW = 525,
                dstH = 743,
                revisionKey = state.comicPaintFrameRevision(),
            )
        }
        println("[㉜a-probe] ① 抬笔收尾（endStroke）= **${t1} ms**")
        println("[㉜a-probe] ② 抬笔通知 + 一帧视口（525×743）= **${t2} ms**")
        println(
            "[㉜a-probe] 整页回读=${state.comicPaintPageReadbacks}（抬笔前 $pageReadbacksBefore）" +
                " 视口帧=${state.comicPaintViewportFrames} 视口=${state.comicPaintViewportWidth}×" +
                "${state.comicPaintViewportHeight} 层表面=${session.layerSurfaceBytes / 1024 / 1024} MB",
        )

        org.junit.Assert.assertTrue(
            "抬笔**不许**再多出任何整页回读（实测 ${state.comicPaintPageReadbacks} 次，抬笔前 $pageReadbacksBefore 次）✗ " +
                "—— 显示只读可见区 ✓（㉖ 那条「抬笔重建整页」的链已经删掉了 ✓）",
            state.comicPaintPageReadbacks == pageReadbacksBefore,
        )
    }
}
