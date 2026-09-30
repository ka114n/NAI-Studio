package com.kallan.naistudio.desktop.platform

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.DialogProperties
import com.kallan.naistudio.models.BrushSpec
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.platform.LocalPlatform
import com.kallan.naistudio.platform.LocalUiHost
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.platform.RefLauncher
import com.kallan.naistudio.platform.UiHost
import com.kallan.naistudio.screens.ComicCanvasHost
import com.kallan.naistudio.screens.ComicModeUiState
import com.kallan.naistudio.state.AppState
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * **缩放 / 平移画布之后，笔迹还钉不钉在纸上**（用户 2026-09-21 实机报：
 * 「**放大缩小画布，线条会乱飞，没有跟随画布**」✗）。
 *
 * ## 判据（不许"我看着好了" ✗）
 *
 * 同一页、同一笔（程序化画的一条固定线段 ✓），在 **同一个组合场景**里依次摆到
 * `zoom = 1.0 → 2.0 → 1.0 → 0.5 → 2.0+平移 → 1.5+平移`，
 * 每一档各渲几帧、然后量**渲染出来的像素**：
 *
 *  ① **笔迹包围盒**必须等于"会话里那一条墨迹的包围盒"按**单次变换**
 *    （纸内坐标 → 居中缩放 → 平移 ✓）投到屏幕上的那个矩形 ✓（容差 ±4 px ✓）；
 *  ② **纸的矩形**（纸色那块 ✓）必须等于同一个模型 ∩ 画布区裁剪 ✓。
 *
 * ## 为什么要"同一个场景里连着渲"（这条是踩出来的 ✗）
 *
 * 每一档单独起一个场景的话，**每档都是"第一帧"** ⇒ 量不出"上一档留下的缓冲"这一类问题 ✗。
 * 用户报的那一手正好是**连着缩放**（放大 → 缩小 ✓）⇒ 必须让同一张视口缓冲跨档复用，
 * 判据才盖得住 ✓（第一版这样跑出来的 1.0 档，墨迹整体被**横向压扁**了 ✗ —— 见
 * `AppState.comicPaintViewportFrame` 里那条说明 ✓）。
 *
 * ## 为什么必须多渲几帧
 *
 * 视口层要等 `onSizeChanged` 把"纸的像素尺寸"量回来才有得画（第一帧里 `paperPx` 还是
 * Zero ⇒ 那一层早退 ✓）；`renderComposeScene` 只画一帧 ✗ ⇒ 这里用 [ImageComposeScene]
 * 反复 `render()` ✓（同一份组合、同一份状态 ✓）。
 */
class ComicCanvasZoomAnchorTest {

    private val platform: Platform = desktopPlatform()

    private class QuietUiHost : UiHost {
        @Composable
        private fun noop(): RefLauncher = remember { object : RefLauncher { override fun launch(input: String?) = Unit } }

        @Composable
        override fun rememberImagePicker(onPicked: (String?) -> Unit): RefLauncher = noop()

        @Composable
        override fun rememberFilePicker(mimeTypes: List<String>, onPicked: (String?) -> Unit): RefLauncher = noop()

        @Composable
        override fun rememberFileCreator(mimeType: String, onPicked: (String?) -> Unit): RefLauncher = noop()

        @Composable
        override fun rememberFolderPicker(onPicked: (String?) -> Unit): RefLauncher = noop()

        override fun toast(message: String, long: Boolean) = Unit
        override fun openUrl(url: String) = Unit
        override fun fullscreenDialogProperties(): DialogProperties = DialogProperties()
        @Composable
        override fun backHandler(enabled: Boolean, onBack: () -> Unit) = Unit
        override fun exitApp() = Unit
        override fun horizontalResizeCursor(): Modifier = Modifier
        override fun panCursor(grabbing: Boolean): Modifier = Modifier
    }

    private data class Box(val l: Int, val t: Int, val r: Int, val b: Int) {
        val w: Int get() = r - l
        val h: Int get() = b - t
        override fun toString() = "[$l,$t .. $r,$b] ${w}x$h"
    }

    private fun bboxOf(px: IntArray, w: Int, h: Int, hit: (Int) -> Boolean): Box? {
        var l = Int.MAX_VALUE
        var t = Int.MAX_VALUE
        var r = -1
        var b = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (hit(px[y * w + x])) {
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < t) t = y
                    if (y > b) b = y
                }
            }
        }
        return if (r < 0) null else Box(l, t, r, b)
    }

    private fun pixelsOfPng(bytes: ByteArray): IntArray? {
        val decoded = platform.images.decodeBytes(bytes) ?: return null
        val px = platform.images.pixelsOf(decoded)
        decoded.recycle()
        return px
    }

    private fun freshState(): AppState {
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .remove("comic.canvas.scale")
            .remove("comic.canvas.x")
            .remove("comic.canvas.y")
            .apply()
        return AppState(platform).also { it.ensureComicBoardLoaded() }
    }

    private val t: (String) -> String = { key -> key }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun ink_stays_glued_to_the_paper_across_zoom_and_pan() {
        val winW = 1200
        val winH = 900
        val pageW = 1240
        val pageH = 1754
        val brushPx = 48
        // 一笔画在**纸的左上那一片**（离纸边远 ⇒ 0.5~2 倍 + 那两平移下都完整可见 ✓）
        val sx = 200f
        val ex = 500f
        val sy = 700f

        val state = freshState()
        state.setComicBoardBase(width = pageW, height = pageH)
        val paintId = state.createComicPaintLayer()
        assertNotNull("先决条件：绘画层要建得出来 ✗", paintId)
        state.chooseComicPaintColor(0xFFE01B24.toInt())
        state.applyComicPaintBrush(BrushSpec(spacing = 6f, density = 100f, opacity = 100f))
        state.setComicPaintBrush(brushPx)
        state.beginComicPaintStroke(sx, sy)
        state.comicPaintStrokeTo(ex, sy)
        state.endComicPaintStroke()

        // 会话里那一条墨迹的**真实包围盒**（页像素 ✓）—— 判据的基准
        val session = state.comicPaintSession
        assertNotNull("先决条件：会话要开着（画完还在 ✓）", session)
        val inkPage = bboxOf(session!!.pixels, pageW, pageH) { p -> ((p ushr 24) and 0xFF) != 0 }
        assertNotNull("先决条件：这一笔必须真的画进会话像素 ✗", inkPage)
        assertTrue(
            "先决条件：这一笔要有几十像素宽（实测 $inkPage）✗",
            inkPage!!.w > 100 && inkPage.h > 10,
        )
        // 走的是**表面那条路**（㉜b：混色也在这里 ✓；这条判据只管几何 ✓）
        println(
            "[㉜b-zoom] 会话墨迹 bbox（页像素）= $inkPage" +
                "（页 ${pageW}x$pageH、笔刷 ${brushPx}px、段 ${sx}→${ex} @ y=$sy）" +
                " onSurface=${session.strokeOnLayerSurface}",
        )

        val ui = ComicModeUiState()
        val scene = ImageComposeScene(winW, winH, Density(1f)) {
            CompositionLocalProvider(
                LocalPlatform provides platform,
                LocalUiHost provides QuietUiHost(),
            ) {
                MaterialTheme { ComicCanvasHost(state = state, ui = ui, t = t) }
            }
        }
        var nanos = 0L
        fun renderFrames(n: Int): IntArray? {
            var image: org.jetbrains.skia.Image? = null
            repeat(n) {
                nanos += 16_000_000L
                image?.close()
                image = scene.render(nanos)
            }
            val bytes = image?.encodeToData(EncodedImageFormat.PNG)?.bytes
            image?.close()
            return bytes?.let { pixelsOfPng(it) }
        }

        try {
            // ---- 基准：zoom = 1（纸整个可见 ⇒ 纸的矩形就是它的布局矩形 ✓）----
            ui.canvasScale = 1f
            ui.canvasOffset = Offset.Zero
            val first = renderFrames(4)
            assertNotNull("第一帧必须画得出来 ✗", first)
            val paper1 = bboxOf(first!!, winW, winH) { (it and 0xFFFFFF) == 0xFCFCFA }
            assertNotNull("纸（纸色那块）必须看得见 ✗", paper1)
            val baseW = paper1!!.w
            val baseH = paper1.h
            val cx = paper1.l + baseW / 2f
            val cy = paper1.t + baseH / 2f
            val scale1 = baseW.toFloat() / pageW
            // 画布区的裁剪矩形：纸在 zoom=1 时贴着（内边距 14dp 之后的）左上角 ⇒ 反推出来 ✓
            val clipL = paper1.l - 15
            val clipT = paper1.t - 15
            val clipR = winW - 12
            val clipB = paper1.t + baseH + 15
            println(
                "[㉜b-zoom] 基准（zoom=1）：纸=$paper1 中心=($cx,$cy) 纸内比例=$scale1" +
                    " 画布区裁剪≈[$clipL,$clipT .. $clipR,$clipB]",
            )

            val cases = listOf(
                Triple(1.0f, Offset.Zero, "zoom=1.0 pan=0"),
                Triple(2.0f, Offset.Zero, "zoom=2.0 pan=0（笔迹左端被画布区裁掉 ✓）"),
                Triple(1.0f, Offset.Zero, "zoom=2.0→1.0（缩回去）"),
                Triple(0.5f, Offset.Zero, "zoom=1.0→0.5（缩回去）"),
                Triple(2.0f, Offset(200f, 60f), "zoom=2.0 pan=(200,60)"),
                Triple(1.5f, Offset(60f, -40f), "zoom=1.5 pan=(60,-40)"),
            )

            fun modelX(pageX: Float, zoom: Float, pan: Offset): Float =
                cx + (pageX * scale1 - baseW / 2f) * zoom + pan.x

            fun modelY(pageY: Float, zoom: Float, pan: Offset): Float =
                cy + (pageY * scale1 - baseH / 2f) * zoom + pan.y

            val failures = ArrayList<String>()

            for ((zoom, pan, label) in cases) {
                ui.canvasScale = zoom
                ui.canvasOffset = pan
                val px = renderFrames(3)
                assertNotNull("[$label] 必须画得出来 ✗", px)
                val paper = bboxOf(px!!, winW, winH) { (it and 0xFFFFFF) == 0xFCFCFA }
                val ink = bboxOf(px, winW, winH) {
                    val r = (it ushr 16) and 0xFF
                    val g = (it ushr 8) and 0xFF
                    val b = it and 0xFF
                    r > 120 && g < 140 && b < 140 && r - g > 60
                }
                // 模型：**单次变换**（纸内坐标 → 居中缩放 → 平移 ✓），再按"画布区裁剪"裁一刀 ✓
                val expInkL = maxOf(clipL.toFloat(), modelX(inkPage.l.toFloat(), zoom, pan))
                val expInkT = maxOf(clipT.toFloat(), modelY(inkPage.t.toFloat(), zoom, pan))
                val expInkR = minOf(clipR.toFloat(), modelX(inkPage.r + 1f, zoom, pan))
                val expInkB = minOf(clipB.toFloat(), modelY(inkPage.b + 1f, zoom, pan))
                val expPaperL = maxOf(clipL.toFloat(), cx - baseW / 2f * zoom + pan.x)
                val expPaperT = maxOf(clipT.toFloat(), cy - baseH / 2f * zoom + pan.y)
                val expPaperR = minOf(clipR.toFloat(), cx + baseW / 2f * zoom + pan.x)
                val expPaperB = minOf(clipB.toFloat(), cy + baseH / 2f * zoom + pan.y)
                println(
                    "[㉜b-zoom] $label\n" +
                        "    纸  实测=$paper 模型≈[${expPaperL.roundToInt()},${expPaperT.roundToInt()}" +
                        " .. ${expPaperR.roundToInt()},${expPaperB.roundToInt()}]\n" +
                        "    笔迹 实测=$ink 模型≈[${expInkL.roundToInt()},${expInkT.roundToInt()}" +
                        " .. ${expInkR.roundToInt()},${expInkB.roundToInt()}]",
                )
                val inkTol = 4
                val paperTol = 5
                if (paper == null || ink == null) {
                    failures += "[$label] 纸=$paper 笔迹=$ink（看不见 ✗）"
                    continue
                }
                fun check(name: String, actual: Int, expected: Float, tol: Int) {
                    if (abs(actual - expected) > tol) {
                        failures += "[$label] $name：实测 $actual vs 模型 ${expected.roundToInt()}" +
                            "（差 ${abs(actual - expected)} > $tol）"
                    }
                }
                check("笔迹左边界", ink.l, expInkL, inkTol)
                check("笔迹上边界", ink.t, expInkT, inkTol)
                check("笔迹右边界", ink.r, expInkR, inkTol)
                check("笔迹下边界", ink.b, expInkB, inkTol)
                check("纸左边界", paper.l, expPaperL, paperTol)
                check("纸上边界", paper.t, expPaperT, paperTol)
                check("纸右边界", paper.r, expPaperR, paperTol)
                check("纸下边界", paper.b, expPaperB, paperTol)
            }
            if (failures.isNotEmpty()) {
                throw AssertionError(
                    "缩放 / 平移之后笔迹没有钉在纸上（用户 2026-09-21 报的那一手 ✗）：\n" +
                        failures.joinToString("\n"),
                )
            }
        } finally {
            scene.close()
        }
    }
}
