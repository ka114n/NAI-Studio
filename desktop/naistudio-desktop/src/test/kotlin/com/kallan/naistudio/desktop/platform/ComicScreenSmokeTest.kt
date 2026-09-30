package com.kallan.naistudio.desktop.platform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kallan.naistudio.models.CanvasTool
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.models.SelectionShape
import com.kallan.naistudio.platform.LocalPlatform
import com.kallan.naistudio.platform.LocalUiHost
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.platform.RefLauncher
import com.kallan.naistudio.platform.UiHost
import com.kallan.naistudio.screens.ComicBasePanelBody
import com.kallan.naistudio.screens.ComicCanvasHost
import com.kallan.naistudio.screens.ComicModeScreen
import com.kallan.naistudio.screens.ComicModeUiState
import com.kallan.naistudio.screens.ComicOverlayPanelBody
import com.kallan.naistudio.screens.ComicOverlayUiState
import com.kallan.naistudio.screens.ComicPanelsPanelBody
import com.kallan.naistudio.state.AppState
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **高级漫画这一页"到底能不能组合出来"**（离屏冒烟 ✓）—— 用户 2026-09-20 报「双击打字会崩溃 ✗」之后补的。
 *
 * 为什么必须补这条 ✗：这一页从第一批到第七批**一次 GUI 都没起过** ✓，
 * 前两轮用户实机各崩了一次（气泡描边 ✗、双击打字 ✗）—— 全是"编译绿 + 单测绿"却一碰界面就炸的类型 ✗。
 * `renderComposeScene` 走的是**同一套 Compose 组合 + Skia 绘制栈** ✓，
 * 能在**不开窗口**的前提下把"这一页 + 那个对话框"真组合、真画一帧 ✓ ——
 * 组合期 / 绘制期抛的异常会**原样冒出来** ✓，比等用户截图快得多 ✓。
 *
 * 覆盖：整页（底板 + 格子 + 气泡 + 文本）✓、**双击打字那个编辑对话框** ✓（本轮崩的就是它 ⚠️）、
 * 文字**描边**那条分支 ✓（`drawStyle = Stroke` 画两遍 ✓）。
 *
 * ⚠️ 用的是**测试隔离档案**（构建脚本把 `%APPDATA%` 指到 `build/test-home` ✓，绝不碰用户的 prefs.json ✓）。
 */
class ComicScreenSmokeTest {

    private val platform: Platform = desktopPlatform()

    /** 一个**不弹真对话框**的宿主（离屏测试里没人点"选图"，只要接口在就行 ✓）。 */
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

    @OptIn(ExperimentalComposeUiApi::class)
    private fun render(width: Int = 1400, height: Int = 900, body: @Composable () -> Unit): ByteArray? {
        val image = renderComposeScene(width, height, Density(1f)) {
            CompositionLocalProvider(
                LocalPlatform provides platform,
                LocalUiHost provides QuietUiHost(),
            ) {
                MaterialTheme { body() }
            }
        }
        return image.encodeToData(EncodedImageFormat.PNG)?.bytes
    }

    /** 每个用例都从干净的盘开始（隔离档案是跨次保留的 ✓，不清会有上一轮的残留 ✗）。 */
    private fun freshState(): AppState {
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            // 画布摆放（第 ⑫ 批加的 `comic.canvas.*`）也要清 ✓：
            // 上一个用例把缩放留在盘上的话，这一个用例渲出来的就是 1.7 倍，不是它自己设的那个数 ✗
            .remove("comic.canvas.scale")
            .remove("comic.canvas.x")
            .remove("comic.canvas.y")
            .apply()
        return AppState(platform).also { it.ensureComicBoardLoaded() }
    }

    private val t: (String) -> String = { key -> key }

    /**
     * ① **四块各自单独渲一次**（第 ⑧ 批把整页拆成"可嵌进生成页的几块" ✓）：
     * 底板 + 两个格子 + 一颗气泡 + 一段描边文本，四块**每一块都要能组合 + 画一帧** ✓。
     *
     * 为什么是**分开**渲 ✗：下一批要把 ①②③④ 嵌进生成页的**两个地方**（中间画布 + 侧栏面板 ✓），
     * 每一块都必须**单独**站得住 —— 整页渲一次绿、单块一嵌就炸，是这个仓库踩过的坑 ✓。
     *
     * ⚠️ 第 ⑨ 批一度把那个**临时宿主** `ComicModeScreen` 连同侧栏第 4 项一起删了 ✗
     *（那时高级漫画被改成"生成页里的一个模式" ✗）；用户 2026-09-20 又改回来
     *（「**还是把高级漫画模式放回侧边栏，其余不变**」✓）→ 宿主**加回来了** ✓，
     * 整页那一帧的冒烟见下面 `the_whole_comic_page_composes_and_draws` ✓。
     */    @Test
    fun the_four_comic_blocks_compose_and_draw() {
        val state = freshState()
        state.setComicBoardBase(width = 2480, height = 3508)
        val firstPanel = state.addComicPanel(120f, 160f, 2200f, 1200f)
        assertNotNull("第一格应该建得出来", firstPanel)
        state.addComicPanel(120f, 1500f, 1050f, 1800f)
        val bubbleId = state.addComicBubble("ROUND", 300f, 400f, 700f, 320f)
        assertNotNull("气泡应该建得出来", bubbleId)
        state.updateComicBubble(bubbleId!!) { it.copy(text = "你好", stroke = true) }
        val textId = state.addComicText(300f, 2200f, 900f, 260f)
        assertNotNull("文本层应该建得出来", textId)
        state.updateComicText(textId!!) { it.copy(text = "旁白", stroke = true) }

        // 选中态也点上：**四角把手 / 尾巴把手 / 属性区**这几个分支一并画到 ✓（最容易崩的就是它们 ✗）
        val ui = ComicModeUiState().apply {
            selectedPanelId = firstPanel
            selectedId = bubbleId
        }

        val canvas = render { ComicCanvasHost(state = state, ui = ui, t = t) }
        assertNotNull("① 画布块必须能组合 + 画出来 ✗", canvas)
        assertTrue("① 画布块画出来的 PNG 不该是空的 ✗", canvas!!.isNotEmpty())

        val base = render { ComicBasePanelBody(state = state, ui = ui, t = t) }
        assertNotNull("② 底板与模板栏必须能组合 + 画出来 ✗", base)
        assertTrue("② 底板与模板栏画出来的 PNG 不该是空的 ✗", base!!.isNotEmpty())

        val panelsBody = render { ComicPanelsPanelBody(state = state, ui = ui, t = t) }
        assertNotNull("③ 格子栏必须能组合 + 画出来 ✗", panelsBody)
        assertTrue("③ 格子栏画出来的 PNG 不该是空的 ✗", panelsBody!!.isNotEmpty())

        val overlay = render { ComicOverlayPanelBody(state = state, ui = ui, t = t) }
        assertNotNull("④ 图层 / 字体栏必须能组合 + 画出来 ✗", overlay)
        assertTrue("④ 图层 / 字体栏画出来的 PNG 不该是空的 ✗", overlay!!.isNotEmpty())
    }

    /**
     * ①b **按下一批的处境拼一次**：中间画布 + 旁边三条栏（各自 `weight` = **有界高度** ✓）。
     *
     * ⚠️ 这条是踩出来的 ✗：三条栏自己带 `verticalScroll`，宿主**再套一层 `verticalScroll`** 就会
     * 无界高度 → 当场 `IllegalStateException: Vertically scrollable component was measured with an
     * infinity maximum height constraints` ✓（本批第一版临时宿主就是这么炸的 ✗）。
     * 这条测试把"嵌进生成页"的正确拼法钉住 ✓。
     */
    @Test
    fun the_four_blocks_compose_side_by_side_like_the_generation_page() {
        val state = freshState()
        state.setComicBoardBase(width = 2480, height = 3508)
        state.addComicPanel(120f, 160f, 2200f, 1200f)
        val ui = ComicModeUiState()

        val png = render {
            Row(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ComicCanvasHost(
                    state = state,
                    ui = ui,
                    t = t,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
                Column(
                    Modifier.width(320.dp).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ComicBasePanelBody(state = state, ui = ui, t = t, modifier = Modifier.fillMaxWidth().weight(1f))
                    ComicPanelsPanelBody(state = state, ui = ui, t = t, modifier = Modifier.fillMaxWidth().weight(1f))
                    ComicOverlayPanelBody(state = state, ui = ui, t = t, modifier = Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
        assertNotNull("画布 + 三条栏并排（有界高度）必须能组合 + 画出来 ✗", png)
        assertTrue("并排拼出来的 PNG 不该是空的 ✗", png!!.isNotEmpty())
    }

    /**
     * ①c **整页宿主渲一帧**（第 ⑨ 批删过那条，这次**加回来** ✓）。
     *
     * 用户 2026-09-20：「**还是把高级漫画模式放回侧边栏，其余不变**」✓ —— 侧栏那一页
     * （`ui/StudioShell.kt` 第 4 项 → [ComicModeScreen]）必须能**组合 + 画出来** ✓：
     * 左边/中间是画布块、右边一列是三条栏，三条栏各自 `weight(1f)`（= **有界高度** ✓，
     * 宿主**没有**再套一层 `verticalScroll` ✗ —— 那正是会当场
     * `IllegalStateException: Vertically scrollable component was measured with an infinity
     * maximum height constraints` 的写法 ✓，上面那条"并排拼一次"钉的是同一个约束 ✓）。
     */
    @Test
    fun the_whole_comic_page_composes_and_draws() {
        val state = freshState()
        state.setComicBoardBase(width = 2480, height = 3508)
        val panelId = state.addComicPanel(120f, 160f, 2200f, 1200f)
        assertNotNull("先决条件：格子应该建得出来", panelId)

        val png = render { ComicModeScreen(state = state, t = t) }
        assertNotNull("整页宿主（侧栏「高级漫画」那一页）必须能组合 + 画出来 ✗", png)
        assertTrue("整页宿主画出来的 PNG 不该是空的 ✗", png!!.isNotEmpty())
    }

    /**
     * ①d **带缩放 / 平移状态的整页冒烟**（第 ⑫ 批 ② 「画布可以随意拖动 / 缩放」✓）。
     *
     * 为什么单拎一条：这一批给画布加了 `graphicsLayer { scaleX/scaleY/translationX/translationY }`
     * （缩放 0.2~6 倍 + 任意平移 ✓）—— 1 倍的时候看不出来的问题（越界、被裁掉、Skia 那边
     * 画到层外），只有在**非 1 倍**下才可能冒出来 ✗。所以这里把 `ComicModeUiState`
     * 里的缩放设成一个**非 1** 的值、位移也挪开，再把**整页宿主**渲一帧 ✓
     * （不是单块 —— 这一条要的是"整页在摆放状态下站得住" ✓）。
     *
     * ⚠️ 摆放是**从 `platform.kv` 读回来**的（`comic.canvas.scale` ✓），所以先决条件里
     * 得把这个键清掉（见 [freshState] ✓），否则上一轮跑完留在盘上的缩放会盖掉这里设的值 ✗。
     */
    @Test
    fun the_whole_comic_page_composes_and_draws_with_zoom_and_pan() {
        val state = freshState()
        state.setComicBoardBase(width = 2480, height = 3508)
        val panelId = state.addComicPanel(120f, 160f, 2200f, 1200f)
        assertNotNull("先决条件：格子应该建得出来", panelId)
        val bubbleId = state.addComicBubble("ROUND", 300f, 400f, 700f, 320f)
        assertNotNull("先决条件：气泡应该建得出来", bubbleId)

        // 摆放：放大到 2.4 倍 + 往左上挪（非 1 倍、非零位移 ✓）
        val ui = ComicModeUiState().apply {
            canvasScale = 2.4f
            canvasOffset = Offset(-160f, 90f)
        }
        val png = render { ComicModeScreen(state = state, t = t, ui = ui) }
        assertNotNull("带缩放 / 平移的整页宿主必须能组合 + 画出来 ✗", png)
        assertTrue("带缩放 / 平移的整页 PNG 不该是空的 ✗", png!!.isNotEmpty())
    }

    /**
     * ①e **带一个活动绘画会话的整页冒烟**（第 ⑬ 批 ⑬ 顶部画布编辑器 ✓）。
     *
     * 为什么单拎一条：这一批给画布加了**一条最上面那条 `ComicPaintToolbar`**（`FlowRow` ✓）
     * 与**画在纸上的"会话活像素"层**（`Image` + `FillBounds` ✓）、**浮动选区那块 patch**
     * （`graphicsLayer` 变换 ✓）、**选区框 + 四角控制点**（`Canvas` + `drawPath` ✓）——
     * 这四样都只在"会话开着"时才组合出来，平时那几条冒烟**一条都覆盖不到** ✗。
     * 用户口径里最容易崩的又正是这一类"新加的绘制层"（`Modifier.border` + 自定义 Shape ✗ 就是这么炸的 ✓）。
     *
     * 跑的东西：建一层 PAINT → **真画一笔** → 拉一个矩形选区 → **抬起来 → 平移**
     * （于是"浮动块 + 选框 + 控制点"三条分支一并画到 ✓）→ 整页渲一帧 ✓。
     *
     * ⚠️ 会话的内存是"页大小 ARGB"（4 字节/像素 ✓），所以这条**刻意用小页**（900×1200 ≈ 4.3 MB ✓）——
     * 冒烟要的是"组合 + 画一帧不炸"，不是像素数 ✓。
     */
    @Test
    fun the_whole_comic_page_composes_and_draws_with_an_active_paint_session() {
        val state = freshState()
        state.setComicBoardBase(width = 900, height = 1200)
        val panelId = state.addComicPanel(60f, 80f, 780f, 420f)
        assertNotNull("先决条件：格子应该建得出来", panelId)

        val paintId = state.createComicPaintLayer()
        assertNotNull("先决条件：绘画层应该建得出来", paintId)

        // 真画一笔（画笔 + 平滑的一段 ✓）
        state.beginComicPaintStroke(300f, 400f)
        state.comicPaintStrokeTo(420f, 520f)
        state.comicPaintStrokeTo(480f, 640f)
        state.endComicPaintStroke()

        // 拉一个矩形选区（SELECT ✓）→ 抬起来 → 平移（于是浮动 patch 与选框都画得到 ✓）
        state.chooseComicPaintTool(CanvasTool.SELECT)
        state.updateComicPaintSelection(SelectionShape.Rect(0.1f, 0.1f, 0.6f, 0.7f))
        state.liftComicPaintSelection()
        state.translateComicPaintSelection(40f, 30f)

        val png = render { ComicModeScreen(state = state, t = t) }
        assertNotNull("带活动绘画会话的整页宿主必须能组合 + 画出来 ✗", png)
        assertTrue("画出来的 PNG 不该是空的 ✗", png!!.isNotEmpty())

        // ⑫ 那个坑的口径再钉一次：**1000px 宽的窗口**（`FlowRow` 折行的那个坎 ✓）。
        // 顶部这条工具栏比气泡那条还长，窄窗口下必须**折行而不是被裁掉** ✓ ——
        // 这里先把"窄窗口下也站得住（不抛）"画一帧 ✓（`ComicOverlayToolbar` 头上那段说明是同一条 ✓）。
        val narrow = render(width = 1000, height = 700) { ComicModeScreen(state = state, t = t) }
        assertNotNull("1000px 宽窗口下也必须能组合 + 画出来 ✗（第 ⑫ 批裁按钮那个坎 ✓）", narrow)
        assertTrue("窄窗口画出来的 PNG 不该是空的 ✗", narrow!!.isNotEmpty())
    }

}
