package com.kallan.naistudio.screens

import com.kallan.naistudio.models.ComicBoardMetrics
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.zIndex
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.CanvasTool
import com.kallan.naistudio.models.ComicBoardPage
import com.kallan.naistudio.models.ComicBoardPreset
import com.kallan.naistudio.models.ComicBoardPresets
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.models.ComicObjectHit
import com.kallan.naistudio.models.ComicOverlayMetrics
import com.kallan.naistudio.models.ComicPanel
import com.kallan.naistudio.models.ComicPanelCorner
import com.kallan.naistudio.models.ComicPanelTemplate
import com.kallan.naistudio.models.ComicPanelTemplates
import com.kallan.naistudio.models.EditRect
import com.kallan.naistudio.models.GenerationQueue
import com.kallan.naistudio.models.ImageEditOps
import com.kallan.naistudio.models.MaskPoint
import com.kallan.naistudio.models.NibAngleControl
import com.kallan.naistudio.models.SelectionGeometry
import com.kallan.naistudio.models.SelectionOps
import com.kallan.naistudio.models.SelectionShape
import com.kallan.naistudio.models.isBrushTool
import com.kallan.naistudio.platform.KeyValueStore
import com.kallan.naistudio.platform.LocalPlatform
import com.kallan.naistudio.platform.LocalUiHost
import com.kallan.naistudio.platform.PenPhase
// 拖动格子的**诊断**要写日志（`%APPDATA%\NAI Studio\app.log` ✓）——
// 用户 2026-09-22 报「底板图层能拖、图层里不行」时，就靠这几行把"哪一道门槛挡的"看出来 ✓。
import com.kallan.naistudio.platform.logInfo
import com.kallan.naistudio.services.ComicPageExporter
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.state.ComicRunQuote
import com.kallan.naistudio.ui.BlurIcon
import com.kallan.naistudio.ui.BrushIcon
import com.kallan.naistudio.ui.BucketIcon
import com.kallan.naistudio.ui.CanvasSelectIcon
import com.kallan.naistudio.ui.canvasBackdrop
import com.kallan.naistudio.ui.CanvasBackdropColor
import com.kallan.naistudio.ui.CloneIcon
import com.kallan.naistudio.ui.DropperIcon
import com.kallan.naistudio.ui.EraserIcon
import com.kallan.naistudio.ui.FileImage
import com.kallan.naistudio.ui.LassoIcon
import com.kallan.naistudio.ui.LocalPanelBackgroundColor
import com.kallan.naistudio.ui.LocalPanelTitleBarColor
import com.kallan.naistudio.ui.NaiColorPicker
import com.kallan.naistudio.ui.RedoIcon
import com.kallan.naistudio.ui.UndoIcon
import com.kallan.naistudio.ui.bubbleStyleOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.isAltPressed
// ⚠️ 第 ㊿i 批：`isCtrlPressed` 在**两个包**里各有一个（`key.*` 收键盘事件、`pointer.*` 读指针
//    事件带的那份修饰键 ✓）—— 接收者类型不同，两个都 import 不会打架 ✓（`CanvasEditor.kt` 同款 ✓）。
import androidx.compose.ui.input.pointer.isCtrlPressed
import kotlin.math.atan2
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.runtime.key

/**
 * **高级漫画 · 四块 + 整页宿主**（`docs/43-方案-高级漫画模式.md`）。
 * 第 ⑧ 批把原来那一整页拆成四块；第 ⑨ 批一度取消独立页、把四块嵌进生成页（画布进预览位、
 * 「**还是把高级漫画模式放回侧边栏，其余不变**」⇒ 四块**原样保留** ✓，
 * 再加回一个整页宿主 [ComicModeScreen]（侧栏「高级漫画」那一项 ✓，见 `ui/StudioShell.kt` ✓）。
 * 四块各管一处（原来那一页的每一处都**只出现一次** ✓）：
 *  1. [ComicCanvasHost] **① 画布**：气泡 / 文本工具栏 + **画布本体**
 *     （纸 / 格子 / 气泡 / 文本 / 四角把手）+ 队列那条进度 ✓；双击打开的**属性编辑框**、
 *  2. [ComicBasePanelBody] **② 第 1 栏：底板**（一颗「新建 +」，点开是预设尺寸 / 自定义宽高 /
 *  3. [ComicPanelsPanelBody] **③ 第 2 栏：格子**（列表 / 选中 / 上移下移 / 删除 /
 *  4. [ComicOverlayPanelBody] **④ 第 3 栏：气泡·文本·图层**（图层列表 / 字体库 /
 *     压平 / 导出这一页 ✓）。
 * ## 状态：宿主 `remember` 一份，四块共用
 *
 * 见 [ComicModeUiState] ✓（各块自己再 `remember` 一份的话，点画布侧栏不动、点侧栏画布不知道 ✗）。
 * ## 宿主：侧栏那一页（[ComicModeScreen] ✓）
 *
 * 布局 = **左 = 图层编辑器（窗口式面板）+ 中 = 画布 + 右 = 底板 / 格子**（第 ⑮ 批；
 * 内边距与圆角面板风格照 `ToolsScreen` ✓ —— 见 [ComicModeScreen] 头上那张图 ✓）。
 * `verticalScroll`**（那会给它无界高度 → 当场 `IllegalStateException: Vertically scrollable
 * component was measured with an infinity maximum height constraints` ✗），
 * `height(dp)` / `heightIn(...)` 给个**有界高度** ✓（`ComicScreenSmokeTest` 里那条"并排拼一次"
 * ⚠️ **重活不在这个文件里** ✗：顺序与状态在 `models/GenerationQueue.kt`、花费在 `models/ComicCost.kt`
 * + 官方报价接口、真花钱那一下在 `AppState.generateComicPanel`（复用聚焦重绘那条路 ✓）
 * ④⑤ 那一批（**气泡 / 文本 / 图层 / 字体**）在隔壁 `screens/ComicOverlayLayers.kt` ✓ ——
 * 这个文件只做接线与显示 ✓（不在这儿再长一份实现，免得两边各说各话 ✗）
 * 花钱前必须确认是用户定的硬规矩，落地成 [ComicRunConfirmDialog] ✓
 *
 * ⚠️ 还是**不做**的：跨格角色一致性（后面批次 ✓）；文本的**竖排**这一版没做 ✓
 * ## 坐标约定（**只有一处换算**）
 * 模型里的格子坐标永远是**底板像素**（`ComicBoardPage.baseWidth/Height` 那套）；
 * 界面显示时按"适应可用区域"等比缩到 dp，换算系数就是 [ComicCanvasArea] 里那个 `scale`
 *（单位：**dp / 底板像素**）。手势给的是 px，所以先 `toDp()` 再除 `scale` 回到底板像素 ✓ ——
 * ⚠️ 第 ⑫ 批加的**缩放 / 平移**（`graphicsLayer` ✓）**不**参与这个换算 ✗：
 * 它整体作用在"纸"那一层上，纸里面的坐标空间一点没变 —— 所以 `toPage` 那套**一个字都没改** ✓，
 * 而格子 / 气泡 / 文本的命中测试会跟着变换自动走 ✓（见 [ComicCanvasArea] 的 ② 那段 ✓）。
 */
/**
 * 漫画这套界面的**共享界面状态**：**画布与几条栏分处两地，状态必须同一个实例** ✓。
 * 宿主（侧栏那一页 [ComicModeScreen]）`remember { ComicModeUiState(platform.kv) }` 一次，四块一起收着用 ✓：
 *  ```kotlin
 *  val ui = remember { ComicModeUiState(LocalPlatform.current.kv) }
 *  ComicCanvasHost(state, ui, t)
 *  ComicBasePanelBody(state, ui, t)   // ②③④ 同理
 *  ```
 *
 * 继承 [ComicOverlayUiState]（气泡 / 文本 / 图层那块的状态，见 `screens/ComicOverlayLayers.kt` ✓）——
 * ## 哪些落盘、哪些不落盘
 *
 * 选中态那几样是**纯界面状态、不落盘** ✓（重启之后没必要记得"上次选中了哪一格 / 哪个气泡" ✓）。
 * ⚠️ **例外**：画布摆放（[canvasScale] / [canvasOffset]）**是落盘的** ✓ ——
 * 用户 2026-09-20：「画布和主页一样，可以随意拖动」；生图页那边用户 2026-09-26 又补了
 * 「**记忆生图区域**」⇒ 两边一个口径：**摆好的位置要记住** ✓，落在 `platform.kv`
 * （键 `comic.canvas.scale` / `comic.canvas.x` / `comic.canvas.y` ✓，缩放按千分之一整数存 ✓）。
 * 不会被读盘覆盖掉 ✓），**写走界面那边的 400ms 防抖** ✓（`ComicCanvasHost` 里那段
 * @param placementKv 画布摆放的仓库；`null` = 不读盘（离屏测试 / 单独挂某一块时用 ✓），
 */
@Stable
class ComicModeUiState(
    private val placementKv: KeyValueStore? = null,
) : ComicOverlayUiState() {
    /** 选中的格子 id（= `ComicPanel.id` ✓）。null = 没选。 */
    var selectedPanelId by mutableStateOf<String?>(null)

    /**
     * **正在拖哪一格 + 它拖动开始时在哪**（底板像素 ✓）——
     * 用户 2026-09-22：「**拖动的是格子，不是图像，要一起拖动**」✓。
     *
     * 绑在这一格上的绘画层（`ComicLayer.panelId == panelDragId` ✓）**像素不会自己跟着走** ✗
     * （`kind = PAINT` 的层是"钉在页上的页大小像素" ✓），所以：
     *  · **拖动期间**：显示那一层时按 `当前坐标 − 起始坐标` 加一个**临时偏移** ✓（不碰像素，零成本 ✓）；
     *  · **松手时**：`AppState.shiftComicPanelLayers` 把那份位移**真的写进像素** ✓，然后清空这三个值 ✓。
     * ⚠️ 用"格子当前坐标 − 起始坐标"而不是"累计拖动量" ✗：格子会被**夹在页内**
     * （`ComicPanel.movedBy` ✓），累计量与实际位移会不一样 ⇒ 图会多走一截 ✗。
     */
    var panelDragId by mutableStateOf<String?>(null)
    var panelDragStartX by mutableStateOf(0f)
    var panelDragStartY by mutableStateOf(0f)

    /** 模板铺之前要确认一次（会替换掉现有格子）—— 非 null = 那个确认框开着 ✓。 */
    var pendingTemplate by mutableStateOf<ComicPanelTemplate?>(null)

    /** 「新建 +」那个浮层开着没（预设尺寸 / 自定义宽高 / 模板 / 清空都收在里面 ✓）。 */
    var customOpen by mutableStateOf(false)

    /**
     * 「新建格子」开关（第 ⑮ 批 ✓）——
     * ⑮ 用户 2026-09-20：「**格子需要点击生成新格后才能在画布上进行拉新格子**」✓ ——
     *  · **只有它开着**时，在空白纸上拖才建新格 ✓；关着时拖动**什么都不做** ✓（画布惰性 ✓）；
     * ⚠️ 它是**纯界面状态**、不落盘 ✓（和 `selectedPanelId` 一个口径 ✓ —— 重启后没必要
     * 记得"上次那个开关开着" ✓）。状态放在这里（而不是界面里散着）是因为
     * 工具栏（`ComicOverlayToolbar` ✓）与画布的手势（`ComicCanvasArea` ✓）看**同一个实体** ✓。
     */
    var newPanelMode by mutableStateOf(false)

    /** 「自定义」尺寸那两个输入框（宽度 / 高度 ✓）—— 现在长在「新建 +」浮层里 ✓。 */
    var customWidth by mutableStateOf(ComicBoardPresets.DEFAULT_WIDTH.toString())
    var customHeight by mutableStateOf(ComicBoardPresets.DEFAULT_HEIGHT.toString())

    /**
     * 画布**缩放**（`f` = 按可用区域等比 fit 的那个大小 ✓，范围 `0.2f..6f` ✓）
     *
     * 从盘上读回来 —— 没存过 / 存坏了都退回 1f ✓（`coerceIn` 兜住 ⇒ 不崩 ✓）。
     */
    var canvasScale by mutableStateOf(
        (
            (
                placementKv?.getInt(
                    KEY_COMIC_CANVAS_SCALE,
                    COMIC_CANVAS_SCALE_STORE_FACTOR.roundToInt(),
                ) ?: COMIC_CANVAS_SCALE_STORE_FACTOR.roundToInt()
                ).toFloat() / COMIC_CANVAS_SCALE_STORE_FACTOR
            ).coerceIn(COMIC_CANVAS_MIN_SCALE, COMIC_CANVAS_MAX_SCALE),
    )

    /**
     * 画布**平移**（纸在画布里的落点，单位 = **像素** ✓）——
     * 和生成页 `previewOffset` 同一个量（那个也是 px，不是 dp ✓）。
     */
    var canvasOffset by mutableStateOf(
        Offset(
            x = (placementKv?.getInt(KEY_COMIC_CANVAS_X, 0) ?: 0).toFloat(),
            y = (placementKv?.getInt(KEY_COMIC_CANVAS_Y, 0) ?: 0).toFloat(),
        ),
    )


    /**
     * 画布的**旋转角**（度 ✓，第 ㊶ 批 2026-09-21 加 ✓）。
     *
     * 用户口径：「**按着 alt+空格可以旋转画布**」✓ ——
     * 与"空格+拖动 = 平移"同一条手势，只是**多按了 Alt** ✓（见 [ComicModeUiState.canvasRotateGesture] ✓）。
     */
    var canvasRotation by mutableStateOf(
        (placementKv?.getInt(KEY_COMIC_CANVAS_ROTATION, 0) ?: 0).toFloat(),
    )

    /**
     * 这一下是**旋转画布**吗（空格 + Alt 按着 ✓）。
     *
     * ⚠️ 判据在**按下那一刻**定 ✓（与 [canvasPanGesture] 同一个纪律 ✓）——
     *    中途松开 Alt 不该把这次旋转变成平移 ✗（手感会跳 ✓）。
     */
    var canvasRotateGesture by mutableStateOf(false)
    /** 「重置视图」：缩放回 1、平移 + 旋转归位 ✓（画布那条工具栏上那颗按钮 ✓）。 */
    fun resetCanvasView() {
        canvasScale = 1f
        canvasOffset = Offset.Zero
        canvasRotation = 0f
    }

    /**
     * **「选择 / 套索」按下的第一下 = 先在底板上做一次对象命中** ✓
     *
     * （用户 2026-09-20：「画布逻辑和 ps 一样啊,在画布上的东西不能乱动,**只有选中/套索后才可以动**」
     * + 「格子生成以后不能随意拖动与改变大小,**只有经过套索选中后才能进行操作**」✓）
     *
     *  · **命中格子** ⇒ 选中的是**那一格** ✓（`selectedPanelId` ✓ —— 它于是出现四角把手，
     *    才可以被拖动 / 缩放 ✓）；命中**气泡 / 文本** ⇒ 选中的是那一个 ✓（`selectedId` ✓）
     *    两者**互斥**（同一时刻只有一个当前对象 ✓）
     *  · **没命中** ⇒ 返回 false ✓，调用方照旧走会话的**像素选区**（`updateComicPaintSelection` ✓）
     *
     * @return true = 命中了对象、**已经把选中态设好、且绝不能再去开像素选区** ✗；
     *   false = 空白 ⇒ 照旧走像素选区 ✓
     */
    fun beginComicSelectAt(state: AppState, pageX: Float, pageY: Float): Boolean {
        return when (val hit = state.hitComicObject(pageX, pageY)) {
            is ComicObjectHit.Panel -> {
                selectedPanelId = hit.id
                selectedId = null
                // ⚠️ **第 ㊹ 批（2026-09-21）**：用户口径 ——
                //   「**新建格子后，格子和图层属于同一类型，同一时间只能聚焦一个。**
                //     **只有选择格子时才能拉动和调整大小,并且不能绘画,未选择格子时格子框不可见**」✓
                //
                // ⇒ 选中格子 = **这一下聚焦的是格子、不是图层** ✓ ⇒
                //   把图层焦点让出去 ✓（`selectedId = null` ✓ —— 图层行就不亮了 ✓，
                //   而且下面那条 `autoFocusComicPaintLayer` 的 effect 见到 `selectedId == null`
                //   会重新挑一层 ✓ —— 这正是"同一时间只聚焦一个"✓）。
                state.blurComicPaintLayer()
                true
            }

            is ComicObjectHit.Overlay -> {
                selectedId = hit.id
                selectedPanelId = null
                true
            }

            null -> false
        }
    }
}

/**
 * ① **画布那一块**：气泡 / 文本工具栏 + 画布本体（纸 / 格子 / 气泡 / 文本 / 四角把手）+ 队列进度那条 ✓。
 * 顺序与间距和原来那一页的 `Column` 一样（工具栏在上、画布吃掉剩下的高度 ✓）
 * 整页那句**惰性读盘**跟着画布块走 ✓ —— 换宿主（侧栏 ✓）也不用担心读盘漏掉
 *（漏了的表现：底板 / 格子全没有 ✗）。
 *
 * ⚠️ **跑前的确认框**和**双击的属性编辑框**都挂在这一块里：它们必须跟画布一样**永远在场上** ✓。
 */
@Composable
fun ComicCanvasHost(
    state: AppState,
    ui: ComicModeUiState,
    t: (String) -> String,
    canvasBase: Color = CanvasBackdropColor,
    drawCanvasGrid: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // 惰性读盘：盘上的页表第一次进这个界面时才读回来（见 AppState.ensureComicBoardLoaded 的注释；
    // AppState 里有 `comicBoardLoaded` 闸门 → 调多次也幂等 ✓）
    LaunchedEffect(Unit) { state.ensureComicBoardLoaded() }

    val page = state.comicBoardPage
    val panels = state.comicBoardPanels

    // ⚠️ 原来这里的那些 `remember`（选中的格子 / 选中的气泡 / 待确认的模板 / 自定义尺寸那两个框…）
    // 全部搬进 [ComicModeUiState] 了 ✓ —— 宿主 `remember` 一份、四块共用 ✓（见那个类上的注释 ✓）；
    // 选图那个 launcher 跟着「导入图像」那颗按钮走 → 在 ② [ComicBasePanelBody] 里 ✓。
    // 选集里被删掉的格子：选中态要跟着落空，否则"选中一个不存在的 id"会让列表和画布对不上
    LaunchedEffect(panels.map { it.id }) {
        if (ui.selectedPanelId != null && panels.none { it.id == ui.selectedPanelId }) {
            ui.selectedPanelId = null
        }
    }

    // 同理：气泡 / 文本 / **图层**被删掉了（可能是在图层列表里删的），那边的选中态也要落空 ✓
    // ⚠️ 必须把**生成层**也算进来（第 ⑭ 项：点某一层 = 只选中它 ✓）—— 只盯气泡 / 文本的话，
    // 选中一个生成层会在下一帧被这条 effect 立刻清掉 ✗，表现就是"点了没反应" ✓。
    val overlayIds = state.comicBoardBubbles.map { it.id } +
        state.comicBoardTexts.map { it.id } +
        state.comicBoardLayers.map { it.id }
    LaunchedEffect(overlayIds) {
        if (ui.selectedId != null && overlayIds.none { it == ui.selectedId }) {
            ui.selectedId = null
            ui.editingId = null
        }
    }

    // ---- ① 与「普通 / 无限画布」**共用同一套缩放平移** ----
    //（用户 2026-09-23：「我要画布通用」✓ —— 三档只有工具不同 ✓，摆好的姿势走到哪一档都一样 ✓）
    //
    // ⚠️ 漫画这一档的摆放活在 [ComicModeUiState]（`AppState` 里那个单例 ✓），**不是**本 composable 的
    //    `remember` ✗ ⇒ 用不了「挂载时读初值」那条（对照 `InfiniteCanvasArea` ✓）。这里换成
    //    **进档拉一次 + 之后推回去** ✓：
    //  · **拉**：这个会话里在别的档动过 ⇒ 把共用值搬进漫画的摆放 ✓（切档倍率 / 位置不跳 ✓）；
    //    没动过 ⇒ 照旧用漫画自己盘上记的那份 ✓（跨重启记忆没动 ✓）；
    //  · **推**：`drop(1)` 丢掉挂载那一发 ✓ —— 不丢的话一进漫画就把共用值冲成"漫画盘上那份" ✗。
    //  · ⚠️ 旋转（`canvasRotation`）**不参与共用** ✗：另外两档根本没有旋转这一维 ✓。
    LaunchedEffect(Unit) {
        if (state.canvasViewTouched) {
            ui.canvasScale = state.canvasViewScale
                .coerceIn(COMIC_CANVAS_MIN_SCALE, COMIC_CANVAS_MAX_SCALE)
            ui.canvasOffset = Offset(state.canvasViewX, state.canvasViewY)
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { ui.canvasScale to ui.canvasOffset }
            .drop(1)
            .collect { (s, o) -> state.holdCanvasView(s, o.x, o.y) }
    }

    // ---- ② 画布摆放（缩放 / 平移）的**落盘** ----
    //
    // 逐条照生成页 `PreviewCard` 抄（见那边的 `persistPlacement` 的注释 ✓）：
    //  · **不每帧写** ✗：`snapshotFlow` + `collectLatest` + `delay(400ms)` = "最后一次改动之后静了
    //    400ms 才写一次"的防抖 ✓（拖动的每一帧 / 滚轮的每一格都改这两个状态 ✓）；
    //  · ⚠️ `snapshotFlow` **不是**把值写进 `LaunchedEffect` 的 key：写进 key 等于在组合期读它们，
    //    于是拖动 / 缩放的每一帧都要重组一次这一块 ✗；
    //  · 离开组合时**补一次** ✓（防抖那 400ms 里用户已经切走的话，那次写入会随协程一起被取消 ✗）。
    val placementKv = LocalPlatform.current.kv
    fun persistCanvasPlacement() {
        placementKv.edit()
            .putInt(
                KEY_COMIC_CANVAS_SCALE,
                (ui.canvasScale * COMIC_CANVAS_SCALE_STORE_FACTOR).roundToInt(),
            )
            .putInt(KEY_COMIC_CANVAS_X, ui.canvasOffset.x.roundToInt())
            .putInt(KEY_COMIC_CANVAS_Y, ui.canvasOffset.y.roundToInt())
            .putInt(KEY_COMIC_CANVAS_ROTATION, ui.canvasRotation.roundToInt())
            .apply()
    }
    LaunchedEffect(Unit) {
        snapshotFlow { ui.canvasScale to ui.canvasOffset }
            .collectLatest {
                delay(COMIC_CANVAS_SAVE_DELAY_MS)
                persistCanvasPlacement()
            }
    }
    DisposableEffect(Unit) {
        onDispose {
            persistCanvasPlacement()
            // ⚠️ **离开这一页 = 收工** ✓（契约点名的两条之一 ✓）
            // 绘画会话的像素只活在内存里 —— 走的时候不写盘，用户刚画的那几笔就没了 ✗
            // （拖动过程绝不写盘 ✓；现在这一处与「切换图层前」那一处是**仅有的两个自动落盘点** ✓
            //  —— 那颗手动的「完成编辑」按钮删掉了 ✗，**这两条别删** ✗）
            state.commitComicLayerPaint()
        }
    }

    // ---- ⑰ 「选了工具就是编辑」：进这一页就**自动聚焦一个能画的层** ✓ ----
    //
    // 用户 2026-09-20（第 ⑰ 批）：「**画布编辑器不要用完成编辑按钮,选了工具就是编辑** ·
                //    **不需要点图层确定状态** · **选图层只是确定修改的层** · **软件的交互逻辑按照 ps 来**」✓ ——
    // 于是"**得先点一下图层行才能画**"✗ 这条前提**取消了** ✓：一进这一页（宽 / 窄两种布局
    // **都**走 [ComicCanvasHost] ✓）就自动把焦点放到一个可画的层上 ✓，工具在手里就能直接画 ✓。
    // 挑哪一层、按什么顺序，**只有一处**（[AppState.autoFocusComicPaintLayer] ✓）：
    //  · 否则优先**当前选中的那一层**（`ui.selectedId` ✓ —— 它可画才用 ✓）
    //  · 再否则取**图层叠最上面**那个可画的 ✓（BASE / GENERATED / PAINT ✓）
    //  · 一个都没有 ⇒ **不开会话、这里也绝不给 toast** ✗（进页面就弹很烦 ✓）——
    //     用户真动手画的时候才由画布那条手势如实说一句 ✓（`hintComicPaintNeedLayer` ✓）
    //
    // ⚠️ key 里放三样，正好覆盖"什么时候要重新挑"✓：
    //  · `ui.selectedId` —— 用户点了别的图层行 / 在画布上选了别的对象 ✓；
    //  · **图层 id 序列** —— 图层增删 / 换页（列表换了就重挑 ✓；顺序没变时 `List.equals`
    //    是结构比较 ✓，不会因为每帧新建一份 list 就重跑 ✗）
    //    要重挑一个可画的 ✓（这正是用户要的"自动换一个" ✓）。
    // ⚠️ 重跑本身是安全的 ✓：已经聚焦着、又是可画的层时 `autoFocusComicPaintLayer` 立刻早退 ✓
    //（**不会**把会话重开一遍、把撤销栈清掉 ✗）。
    val comicLayerIdSequence = state.comicBoardLayers.map { it.id }
    LaunchedEffect(ui.selectedId, comicLayerIdSequence, state.comicPaintLayerId, ui.selectedPanelId) {
        // ⚠️ **第 ㊹ 批（2026-09-21）**：用户口径 ——
        //   「**新建格子后，格子和图层属于同一类型，同一时间只能聚焦一个。**
        //     **只有选择格子时才能拉动和调整大小,并且不能绘画,未选择格子时格子框不可见**」✓
        //
        // ⇒ **选着格子时，这一页不许再自动聚焦图层** ✓（那就是"同一时间只聚焦一个"✓）：
        //   会话保持为空 ✓ ⇒ 画布那条手势走 `session == null` 那条早退 ✓ ⇒
        //   **不能绘画** ✓（正是用户要的 ✓）；格子那一侧接管了焦点 ✓，能拉能缩 ✓。
        // ⚠️ 用户**点图层行 / 取消选格子**时 `selectedPanelId` 变 null ⇒ 这条 effect 重跑 ⇒
        //    `autoFocusComicPaintLayer` 把会话开回来 ✓（切回去照旧能画 ✓）。
        if (ui.selectedPanelId != null) return@LaunchedEffect
        state.autoFocusComicPaintLayer(ui.selectedId)
        // ⚠️ **第 ㊴ 批（2026-09-21）**：用户报「**图层现在还需要选中才能删除或修改，
        //    不是一直默认选中正在工作的图层，还要点击一下**」✓
        //
        // **根因**：`autoFocusComicPaintLayer` 只负责**开会话** ✓（`comicPaintLayerId` ✓），
        // 但图层列表那一行**亮不亮 / 删除改不改得动**看的是 `ui.selectedId` ✓（见
        // `OverlayLayerRow` 的 `selected` 与 `LayerActionRow` 的 `selectedId` ✓）——
        // 这两个状态**从来没有人对齐过** ✗ ⇒ 进页面时已经自动聚焦了一层 ✓，
        // 但 `selectedId` 还是 null ✗ ⇒ 那一行没高亮、垃圾桶是灰的 ✓，
        // **必须手点一下**才把 `selectedId` 写上 ✓ —— 正是用户描述的那一下 ✗。
        //
        // **修法**：自动聚焦之后**把选中同步到"当前正在工作的那一层"** ✓
        //（`selectedId == null` 才补 ✗：用户手动选过/在画布上点过别的对象时不许顶掉他 ✓）。
        val focused = state.comicPaintLayerId
        if (ui.selectedId == null && focused != null && ui.selectedPanelId == null) ui.selectedId = focused
    }

    Column(modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // ⚠️ 用户 2026-09-20 第 2 条：**画布编辑器从这一条上撤掉了** ✓ ——
        // 它现在挂在**左窗口（图层编辑器）里、图层列表的上方** ✓（`ComicOverlayLayers.kt` 的
        // `ComicOverlaySidebar` 第一块 ✓，实现是 [ComicPaintToolbar] ✓）——**只放一份** ✓
        // ⑮ 画布那一排工具：格子工具 + 「新建格子」开关 + 重置视图 ✓（第 ㊼ 批：气泡 / 文本已删 ✓）
        ComicOverlayToolbar(
            page = page,
            ui = ui,
            t = t,
            // 归位之后**立刻写盘**（不等那 400ms 防抖：用户点完就该记住"已经归位了" ✓）
            onResetView = {
                ui.resetCanvasView()
                persistCanvasPlacement()
            },
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(8.dp))

        // 「跑这一页」那一行（第 ③ 批：队列逐格生成）。
        ComicRunBar(state = state, t = t)

        Spacer(Modifier.height(8.dp))

        // ---- 画布区：**软件自己的画布** ✓（㉜a 的 Kotlin 路径：层表面 = 真源 + 视口显示 ✓）----
        // ⚠️ 第 ㉜a 批**切回** `ComicCanvasArea` ✓ —— ㉛ 那批把画布区固定成"有网页画布就显示网页画布" ✗，
        //    于是 ㉜a 的层表面 / 视口显示**在界面上根本看不到** ✗（用户的验收面只有这一块 ✓）。
        // 网页画布（`WebBrush` / `WebBrushWindow`）**代码保留当对照** ✓，但**不再占画布区** ✗。
        ComicCanvasArea(
            state = state,
            page = page,
            selectedPanelId = ui.selectedPanelId,
            onSelect = { ui.selectedPanelId = it },
            overlayUi = ui,
            t = t,
            canvasBase = canvasBase,
            drawCanvasGrid = drawCanvasGrid,
            modifier = Modifier.weight(1f),
        )
    }


    val runQuote = state.comicRunQuote
    if (state.comicQuoteLoading || runQuote != null) {
        ComicRunConfirmDialog(state = state, quote = runQuote, panels = panels, t = t)
    }
}

/**
 * **跑前确认框**（第 ③ 批：队列逐格生成）—— 用户定的硬规矩在这里落地：
 * 「先弹确认框：列出逐格尺寸 + 预估 Anlas 合计；用户点确认才开跑；取消 = 什么都不做；
 * 列的东西：
 *  · **逐格**：`第 N 格 · 1792×576 · 2 Anlas`（尺寸 = `InpaintSize.forRect` ✓，与真正发出去的请求
 *    是同一个数 ✓；价格 = 官方报价接口 ✓）
 *  · **合计**：`ComicCost.Estimate.total` ✓（`docs/43`：价格函数注释 ✓）
 *  · 报价没拿到的尺寸**如实写"未知"** ✓（不能没问到就显示一个数，也不要 ✗）
 *
 * 确认按钮在**报价还没回来之前是灰的** ✓ —— 连价钱都没看到就点确认，等于没确认 ✓。
 */
@Composable
private fun ComicRunConfirmDialog(
    state: AppState,
    quote: ComicRunQuote?,
    panels: List<ComicPanel>,
    t: (String) -> String,
) {
    val language = state.settings.language
    // 清单里只给 panelId，这里翻成"第几格"（按界面上的顺序 = 生成序号 ✓）
    val indexOf: (String) -> Int = { id ->
        val at = panels.indexOfFirst { it.id == id }
        if (at < 0) 0 else at + 1
    }
    AlertDialog(
        // 点外面 = 取消：**什么都不做** ✓（一分钱都不花 ✓）
        onDismissRequest = { state.cancelComicRunRequest() },
        title = {
            Text(
                if (quote?.estimate?.count == 1) {
                    t("comic.runConfirmTitleOne")
                } else {
                    t("comic.runConfirmTitle")
                },
            )
        },
        text = {
            Column(
                Modifier
                    .width(360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (quote == null) {
                    Text(t("comic.runQuoting"), style = MaterialTheme.typography.bodySmall)
                } else {
                    quote.estimate.lines.forEach { line ->
                        Text(
                            text = if (quote.priced(line)) {
                                RuntimeText.format(
                                    language,
                                    "comic.runConfirmLine",
                                    mapOf(
                                        "index" to indexOf(line.panelId),
                                        "w" to line.width,
                                        "h" to line.height,
                                        "anlas" to line.anlas,
                                    ),
                                )
                            } else {
                                RuntimeText.format(
                                    language,
                                    "comic.runConfirmLineUnknown",
                                    mapOf(
                                        "index" to indexOf(line.panelId),
                                        "w" to line.width,
                                        "h" to line.height,
                                    ),
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (quote.hasUnknown) {
                            RuntimeText.format(
                                language,
                                "comic.runConfirmTotalPartial",
                                mapOf(
                                    "count" to quote.estimate.lines.count { quote.priced(it) },
                                    "total" to quote.estimate.total,
                                    "unknown" to quote.unpricedSizes.size,
                                ),
                            )
                        } else {
                            RuntimeText.format(
                                language,
                                "comic.runConfirmTotal",
                                mapOf("total" to quote.estimate.total, "count" to quote.estimate.count),
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t("comic.runConfirmHint"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { state.confirmComicRun() },
                enabled = quote != null,
            ) { Text(t("common.confirm")) }
        },
        dismissButton = {
            TextButton(onClick = { state.cancelComicRunRequest() }) { Text(t("common.cancel")) }
        },
    )
}

/** 队列里那一格的状态文案（等待 / 生成中 / 完成 / 失败 / 已跳过 ✓）。 */
private fun comicStatusText(
    item: GenerationQueue.Item,
    t: (String) -> String,
): String = when (item.state) {
    GenerationQueue.State.PENDING -> t("comic.status.pending")
    GenerationQueue.State.RUNNING -> t("comic.status.running")
    GenerationQueue.State.DONE -> t("comic.status.done")
    GenerationQueue.State.FAILED -> t("comic.status.failed")
    GenerationQueue.State.SKIPPED -> t("comic.status.skipped")
}

// ---------------------------------------------------------------------------
// 顶栏：底板（新建画布 / 导入图像）

/**
 * 底板那一块：**只有一颗「新建 +」加一颗「导入图像」**（用户 2026-09-20 的新口径 ✓）。
 * 用户原话：「**底板区只有一个"新建+"按钮，点击可以设置画布大小，可以预设画布大小**」✓ ——
 * 于是原来那一堆（三档预设 / 自定义那两个框 / 模板 / 清空 / 当前底板那行字）**全部收进
 * 「新建 +」打开的浮层**（[NewBoardDialog] ✓），底板区本身只剩两颗入口 ✓。
 * ⚠️ **导入图像没删** ✗（用户没说删）—— 它是"导入底图"的**唯一**入口 ✓
 * 与"新建"是两件事（一张空白页 vs 一张已有漫画页 ✓）
 * 模板与清空也没删 ✗，只是换到浮层里（功能一个不少 ✓）
 *
 * ⚠️ 第 ⑧ 批之后它**长在侧栏里**（② [ComicBasePanelBody] ✓），**不再有整页宽度可用** ✗ ——
 */
@Composable
private fun BaseToolbar(
    t: (String) -> String,
    onNewBoard: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            t("comic.base"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        // 「新建 +」：点开 = 选预设（A4 / B5 / 正方形）+ 自定义宽高 + 模板 + 清空（见 NewBoardDialog ✓）
        Button(
            onClick = onNewBoard,
            shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("comic.newBoard"), style = MaterialTheme.typography.labelMedium)
        }
        // ⛔ 用户 2026-09-23：「**导入图像功能删了，我想导入直接拖进来**」✓ ——
        //    原来这儿那颗「导入图像」按钮**整颗删掉** ✗；拖入那条路早就有 ✓
        //（`importDroppedImage` ✓：把图复制进私有目录 + 设成图生图底图 + 切回生图页 ✓），
        //    而"底板就是主页画布"之后，把图**拖进来**再点底栏那颗「新建底板」就是同一件事 ✓。
    }
}

/**
 * **「新建 +」打开的浮层**：尺寸预设（A4 竖 / B5 竖 / 正方形 ✓）+ 自定义宽高 + 模板 + 清空 +
 * 当前底板那一行只读信息 ✓。
 * 尺寸预设 [ComicBoardPresets.all]、模板 [ComicPanelTemplates.all]、清空（`clearComicBoardPanels`）
 * ⚠️ 铺模板那条路**照旧分两种**：画布还空着 → 直接铺 ✓；已经有格子 → 先弹那次确认
 * （`ui.pendingTemplate`，对话框在 [ComicBasePanelBody] 里 ✓）—— 这里先把浮层关掉，
 * ⚠️ 浮层内容自己带 `verticalScroll` + `heightIn(max = …)`（**有界** ✓）：
 */
@Composable
private fun NewBoardDialog(
    state: AppState,
    page: ComicBoardPage?,
    ui: ComicModeUiState,
    t: (String) -> String,
    onDismiss: () -> Unit,
) {
    val panels = state.comicBoardPanels
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("comic.newCanvas")) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // ---- 预设尺寸（点一下 = 立即按这个尺寸新建底板 ✓）----
                ComicBoardPresets.all.forEach { preset ->
                    PresetButton(preset, t, modifier = Modifier.fillMaxWidth()) {
                        state.setComicBoardBase(width = preset.width, height = preset.height)
                        onDismiss()
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ---- 自定义宽高 ----
                OutlinedTextField(
                    value = ui.customWidth,
                    onValueChange = { ui.customWidth = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text(t("comic.widthPx")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ui.customHeight,
                    onValueChange = { ui.customHeight = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = { Text(t("comic.heightPx")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        val w = ui.customWidth.toIntOrNull()
                        val h = ui.customHeight.toIntOrNull()
                        if (w != null && h != null) {
                            state.setComicBoardBase(
                                width = w.coerceIn(ComicBoardPresets.MIN_SIDE, ComicBoardPresets.MAX_SIDE),
                                height = h.coerceIn(ComicBoardPresets.MIN_SIDE, ComicBoardPresets.MAX_SIDE),
                            )
                            onDismiss()
                        }
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(t("comic.create")) }
                Text(
                    t("comic.customHint"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ---- 模板 + 清空（原来平铺在"底板与模板"那一栏里 ✓）----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t("comic.templates"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    if (panels.isNotEmpty()) {
                        TextButton(onClick = {
                            ui.selectedPanelId = null
                            state.clearComicBoardPanels()
                        }) { Text(t("comic.clearPanels"), style = MaterialTheme.typography.labelSmall) }
                    }
                }
                // 模板按钮两行摆（窄面板一行放不下 4 个）
                ComicPanelTemplates.all.chunked(2).forEach { rowTemplates ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowTemplates.forEach { template ->
                            OutlinedButton(
                                onClick = {
                                    // 空画布 → 直接铺；已经有格子 → 关掉浮层、交给那次确认（见函数注释 ✓）
                                    if (panels.isEmpty()) {
                                        state.applyComicPanelTemplate(template.id)
                                        ui.selectedPanelId = null
                                        onDismiss()
                                    } else {
                                        ui.pendingTemplate = template
                                        onDismiss()
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    t(template.titleKey),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        repeat(2 - rowTemplates.size) { Spacer(Modifier.weight(1f)) }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ---- 当前底板（只读）：空白画布还是哪张图、多大 ✓ ----
                Text(
                    if (page == null) {
                        t("comic.noBase")
                    } else {
                        RuntimeText.format(
                            state.settings.language,
                            "comic.baseSize",
                            mapOf("w" to page.baseWidth, "h" to page.baseHeight),
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(t("common.close")) }
        },
    )
}

/**
 * 「跑这一页」那一行（第 ③ 批：队列逐格生成）。
 * 三样东西，全在这一行里：
 *  1. **按钮**：没在跑 ⇒「跑这一页」（点了先弹确认 ✓，**绝不会未确认就花钱** ✗）
 *     正在跑 ⇒「停」（= `skipRemaining()` ✓，当前那一格跑完就收尾 ✓）
 *  2. **总进度**：`生成队列.progress()`（失败 / 跳过也算已处理 ✓）
 *  3. **跑完的结果**：失败几格、该点哪一格的重试 ✓
 *
 * ⚠️ 队列状态机（`GenerationQueue`）是**纯逻辑类**、不是 Compose 状态，所以这里读一下
 * `state.comicQueueTick` 把界面叫醒 ✓（每改一次队列状态那个计数就 +1 ✓）。
 */
@Composable
private fun ComicRunBar(state: AppState, t: (String) -> String) {
    val tick = state.comicQueueTick
    val queue = remember(tick) { state.comicQueue }
    val panels = state.comicBoardPanels
    val running = state.comicRunning

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (running) {
            OutlinedButton(onClick = { state.stopComicPageRun() }, shape = RoundedCornerShape(50)) {
                Text(t("comic.runStop"), style = MaterialTheme.typography.labelMedium)
            }
        } else {
            OutlinedButton(
                onClick = { state.requestComicRun() },
                enabled = panels.isNotEmpty(),
                shape = RoundedCornerShape(50),
            ) {
                Text(t("comic.runPage"), style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = comicQueueText(state, queue, t),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 总进度那一句话（没有队列 = 还没跑过 ✓）。 */
private fun comicQueueText(
    state: AppState,
    queue: GenerationQueue?,
    t: (String) -> String,
): String {
    if (queue == null) return t("comic.runIdle")
    val language = state.settings.language
    val total = queue.items.size
    val running = queue.items.firstOrNull { it.state == GenerationQueue.State.RUNNING }
    if (running != null) {
        return RuntimeText.format(
            language,
            "comic.runRunning",
            mapOf("index" to running.order + 1, "total" to total),
        )
    }
    val (done, _) = queue.progress()
    val failed = queue.failedIds().size
    return if (failed > 0) {
        RuntimeText.format(
            language,
            "comic.runProgressFailed",
            mapOf("done" to done, "total" to total, "failed" to failed),
        )
    } else {
        RuntimeText.format(language, "comic.runProgress", mapOf("done" to done, "total" to total))
    }
}

@Composable
private fun PresetButton(
    preset: ComicBoardPreset,
    t: (String) -> String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(t(preset.titleKey), style = MaterialTheme.typography.labelMedium)
    }
}

// ---------------------------------------------------------------------------
// 画布：底板 + 格子（手拖新建 / 选中 / 拖动 / 四角缩放）

// ---- ② 画布摆放（缩放 + 平移）：**照生成页 `PreviewCard` 那一套抄** ✓ ----
//
// 用户 2026-09-20：「**高级漫画模式的画布和主页一样，可以随意拖动**」✓
// 口径与生成页逐条对齐（见 `GenerateScreen.kt` 的 `PREVIEW_*` ✓）：
//  · 滚轮 = **以光标为锚点**缩放，范围 `0.2f..6f` ✓；
//  · 平移 = **鼠标中键拖动** 或 **按住空格 + 左键拖动** ✓（空格那条读 `AppState.canvasSpacePan`，
//    和生图页 / 画布编辑器同一处开关 ✓；手型光标是 `uiHost.panCursor` ✓）
//  · **左键在空白纸上拖动照旧 = 拖出一个新格子** ✓（平移那两下会先立旗子，
//    纸那一层的手势看到旗子就让开 —— 见 `ComicCanvasArea` 里的 `canvasPanGesture` ✓）
//  · 摆放**停下来之后**才写盘（400ms 防抖 ✓），键见下面的 `KEY_COMIC_CANVAS_*` ✓
//
// ⚠️ 变换**只作用在"纸"那一层**（`graphicsLayer` ✓）—— 纸里面的格子 / 气泡 / 文本
// 命中测试会跟着变换走 ✓，所以 `toPage` / `scale` 那套换算**一个字都不用改** ✓。
private const val COMIC_CANVAS_MIN_SCALE = 0.2f
private const val COMIC_CANVAS_MAX_SCALE = 6f

/** 滚轮每一格缩放多少（和生成页同一个数 ✓：`scrollDelta` 一格通常 ∓1）。 */
private const val COMIC_CANVAS_WHEEL_ZOOM_STEP = 0.15f

/** 缩放落盘的整数倍率（`KeyValueStore` 只有 `getInt` / `putInt`，没有 Float ✓ —— 和生成页同一套）。 */
private const val COMIC_CANVAS_SCALE_STORE_FACTOR = 1000f

/** 摆放**停下来**多久之后才写盘（拖动的每一帧 / 滚轮的每一格都改状态，**不能每帧写** ✗）。 */
private const val COMIC_CANVAS_SAVE_DELAY_MS = 400L

/** 画布摆放的三个键（自己一套，**不复用生成页的 `canvas.preview.*`** ✗ —— 两页各摆各的 ✓）。 */
private const val KEY_COMIC_CANVAS_SCALE = "comic.canvas.scale"
private const val KEY_COMIC_CANVAS_X = "comic.canvas.x"
private const val KEY_COMIC_CANVAS_Y = "comic.canvas.y"
private const val KEY_COMIC_CANVAS_ROTATION = "comic.canvas.rotation"

// ---------------------------------------------------------------------------
// ⑬ 画布编辑器（第 ⑯ 批：从**画布上方**搬进**左窗口、图层列表上方** ✓）
// ---------------------------------------------------------------------------

/** 画布编辑器里摆出来的工具（顺序 = 官方那 8 个的顺序 ✓）。 */
private val ComicPaintTools: List<CanvasTool> = listOf(
    CanvasTool.DRAW,
    CanvasTool.ERASE,
    CanvasTool.FILL,
    CanvasTool.SELECT,
    CanvasTool.LASSO,
    CanvasTool.PICKER,
    CanvasTool.BLUR,
)

/**
 * 工具 → **图标**（第 ⑯ 批：用户 2026-09-20 第 2 条「**做成图案和色环**」✓）。
 * 全部用仓库**现成的**自绘图标（`ui/AppIcons.kt` ✓）—— 这几个在 core 图标集里**没有** ✗
 *（`material-icons-core` 只有几十个矢量，`material-icons-extended` 这一个包就 37.7MB，
 *  · DRAW → [BrushIcon]（画笔）✓；ERASE → [EraserIcon] ✓；FILL → [BucketIcon] ✓；
 *  · SELECT → [CanvasSelectIcon]（虚线框 ✓，画布编辑器那颗专用 ✓）；LASSO → [LassoIcon] ✓；
 *  · PICKER → [DropperIcon] ✓；BLUR → [BlurIcon] ✓；CLONE → [CloneIcon] ✓
 * ⚠️ 这里用的是 **DRAW → `BrushIcon`** 而不是画布编辑器那边的 `Icons.Filled.Edit` ✗：
 * 画笔就该是一支笔 ✓（`Edit` 是一支铅笔 + 一个方框，画布编辑器那边有自己的历史原因 ✓）。
 */
private fun comicPaintToolIcon(tool: CanvasTool): ImageVector = when (tool) {
    CanvasTool.DRAW -> BrushIcon
    CanvasTool.ERASE -> EraserIcon
    CanvasTool.FILL -> BucketIcon
    CanvasTool.SELECT -> CanvasSelectIcon
    CanvasTool.LASSO -> LassoIcon
    CanvasTool.PICKER -> DropperIcon
    CanvasTool.BLUR -> BlurIcon
    CanvasTool.CLONE -> CloneIcon
}

/** 工具 → i18n 键（**复用现成的 `canvas.tool.*`** ✓ —— 不再新造一遍 ✗；只换了显示方式 ✓）。 */
private fun comicPaintToolKey(tool: CanvasTool): String = when (tool) {
    CanvasTool.DRAW -> "canvas.tool.draw"
    CanvasTool.ERASE -> "canvas.tool.erase"
    CanvasTool.FILL -> "canvas.tool.fill"
    CanvasTool.SELECT -> "canvas.tool.select"
    CanvasTool.LASSO -> "canvas.tool.lasso"
    CanvasTool.PICKER -> "canvas.tool.picker"
    CanvasTool.BLUR -> "canvas.tool.blur"
    CanvasTool.CLONE -> "canvas.tool.clone"
}

/** 画布编辑器里一颗图标按钮的边长（比画布编辑器那边的 34dp 再紧一档 —— 这一块只有 320dp 宽 ✓）。 */
private val ComicPaintIconButtonSize = 30.dp

/**
 * **画布编辑器里的一颗图标按钮**（七把工具 + 撤销 / 重做都走它 ✓）。
 * 行为照 `CanvasEditor.kt` 的 `CanvasToolButton` 抄一遍 ✓（那个是 `private`，跨文件拿不到 ✗ ——
 *  · **选中态看得出来** ✓：`primaryContainer` 底 + `onPrimaryContainer` 前景 ✓；
 *  · **`contentDescription` = 工具名** ✓（无障碍读屏 + 认不出图案时也有个说法 ✓）
 *  · **鼠标悬停有 tooltip** ✓（`PointerEventType.Enter/Exit` + `Popup` ✓）——
 *    ⚠️ **不用 `TooltipArea`** ✗：那是桌面专属 API，共用树里编不过（同一个理由见
 *    `CanvasEditor.kt` 里 `CanvasToolButton` 头上那段 ✓）；位置也必须自己算
 * ⚠️ 手势 modifier 顺序照仓库硬规矩：`size() → clip → pointerInput → clickable` ✓
 */
@Composable
private fun ComicPaintIconButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(ComicPaintIconButtonSize)
            .clip(RoundedCornerShape(7.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        when (awaitPointerEvent().type) {
                            PointerEventType.Enter -> hovered = true
                            PointerEventType.Exit -> hovered = false
                            else -> Unit
                        }
                    }
                }
            }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(19.dp),
        )
    }
    if (hovered) {
        Popup(
            popupPositionProvider = remember {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                        val y = anchorBounds.bottom + 4
                        // 别跑出窗口右边（最右边那颗的提示会溢出 ✓）
                        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                        return IntOffset(x.coerceIn(0, maxX), y)
                    }
                }
            },
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 4.dp,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * 常用色快捷键（用户 2026-09-20 第 2 条：「允许保留 2~3 个常用色快捷键」✓）——
 * 主体是上面那个色环 ✓（原来那 12 个色块一排**撤掉了** ✗）。
 */
private val ComicPaintQuickColors: List<Int> = listOf(
    0xFF000000.toInt(), // 黑（描线）
    0xFFFFFFFF.toInt(), // 白（填白）
)

/**
 * 一个色块（常用色快捷键 / 现状色预览 ✓）。
 * 选中时外面套一圈主题色 —— **不用 `Modifier.border(宽, 色, 自定义 Shape)`** ✗
 * （电脑端那条会抛 `Failed to Image::makeFromBitmap`，用户实机崩过 ✓）。
 * 这里纯粹是 `clip(CircleShape) + background`，一个 `border` 都没有 ✓）。
 * @param onClick `null` = **只显示、不可点** ✓（现状色预览就是这样 —— 不做假控件 ✗，
 *   也不会被误当成"点一下能换色"✓）。
 */
@Composable
private fun ComicPaintSwatch(
    argb: Int,
    selected: Boolean,
    size: Dp = 22.dp,
    onClick: (() -> Unit)? = null,
) {
    Box(
        Modifier
            .size(if (selected) size + 4.dp else size)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(argb))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        )
    }
}

// ⚠️ **第 ㊽ 批（2026-09-21）**：`ComicPaintToolbar`（画布编辑器：工具图标 / 色环 / 笔刷大小 /
//    笔尖形状 / 角度 / 撤销重做 ✓）**整块删掉** ✓ —— 用户口径「**删除画笔和压感功能**」✓。
//    这一页现在只剩「新建底板 / 图层 / 格子」三块 ✓（生成页那颗铅笔是**另一套**，没动 ✓）。
/**
 * 角度控制的**两态**（顺序 = SAI 的取值 ✓：0 固定 / 1 自动 ✓）。
 * ⚠️ `2 笔杆方向` 这一档**删掉了** ✓（用户 2026-09-20「取消旋转功能」✓；
 * 网页版 `docs/brush-lab-simple.html` 的下拉框也只有这两条 ✓，见 `NibAngleControl` 的说明 ✓）——
 * 旋转一取消，它就没有输入源了（留着 = 点了没反应 ✗）。
 */
private val ComicPaintAngleChips: List<Pair<Int, String>> = listOf(
    NibAngleControl.FIXED to "comic.board.penAngleFixed",
    NibAngleControl.AUTO to "comic.board.penAngleAuto",
)

/** 宽高比那一小格文字（1 = 圆 ✓；保留一位小数，够看清"拉开没拉开"✓）。 */
private fun nibRatioLabel(ratio: Float): String {
    val scaled = (ratio.coerceIn(ImageEditOps.NIB_MIN_RATIO, ImageEditOps.NIB_MAX_RATIO) * 10f)
        .roundToInt()
    return "${scaled / 10}.${scaled % 10}"
}

/**
 * **角度控制的一颗文字芯片**（三态选一个 ✓）。
 * 选中 = 主题色底 + 主题色前景 ✓（和工具那排图标按钮同一个"看得出选中"的口径 ✓）
 * ⚠️ **不用 `Modifier.border`** ✗（电脑端会崩，见上面那条 ✓）——只用 `Surface` 的
 * `color` + `shape`（`RoundedCornerShape` 是标准形状 ✓）
 * 手势顺序照仓库硬规矩：`clip → clickable` ✓（尺寸由 `contentPadding` / 文字决定 ✓，
 * 这里没有 `offset` / `size` 之争 ✓）。
 */
@Composable
private fun ComicPaintAngleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

/**
 * 画布区里那一点（**未经过缩放 / 平移的原始指针坐标** ✓）→ **底板像素**；落在纸外返回 null ✓。
 * 和 [ComicCanvasArea] 里那个 `toPage` 是**同一个换算**，只是喂进来的坐标系不同：
 *  · `toPage` 吃的是**纸里面**的坐标（已经过了 `graphicsLayer` 的缩放 + 平移 ✓）
 *  · 这里吃的是**手势层**（纸外面那一层 ✓）的坐标 —— 所以要多做一次"撤销变换"：
 *    `paper = 纸中心 + (screen - 纸中心 - 平移) / 缩放` ✓
 *    （和滚轮缩放算锚点用的是**同一个纸中心** ✓，见那边的 `change.position.x - paper.width / 2f` ✓）
 *
 * 为什么画笔手势要挂在外面那一层 ✗：格子 / 气泡 / 文本自己的 `detectDragGestures`
 * 会在**触摸斜率一过就 `consume()`**（早退只挡得住"改内容"，挡不住它吃掉事件 ✗）——
 * 挂在外层读原始坐标就绕开了这件事 ✓（第 ⑫ 批的平移手势本来就是这么做的 ✓）。
 * ## 逐环核过的那条链子（用户 2026-09-20「还是一排点」⇒ 怀疑坐标 / 缩放 / DPR ✓）
 *
 * **结论：这条链子是自洽的 ✓**（四个环节逐个核过 ✓，钉在 `ComicScreenToPagePixelTest` ✓）：
 *  1. **`paper`（= `paperPx`）是"纸的屏幕矩形"，而且它已经含了 fit 缩放** ✓ ——
 *     它是 `Box(size(drawW, drawH))` 的 `onSizeChanged` ✓（见 [ComicCanvasArea] ✓）
 *     `drawW/drawH` 就是"按可用区域等比 fit"那一档 dp ⇒ `paper.width px / pageWidth`
 *     已经是**"屏幕上每一原图像素占几个 px（zoom = 1 时）"** ✓
 *     再乘一次 fit 就是重复 —— 所以这里**只除 `zoom`** ✓，fit 的逆只在
 *     `localX / paper.width * pageWidth` 那一步出现、而且**只出现一次** ✓
 *  2. **`zoom`（= `canvasScale`）确实该除** ✓ —— 它只作用在**纸那一层**上
 *     `graphicsLayer{ scaleX/scaleY }` 里 ✓（布局尺寸一个字节都没变 ✓，见那边的说明 ✓）
 *     `graphicsLayer` 的 `transformOrigin` 默认 = **这一层的中心** = 纸的中心 ✓
 *     正变换是 `屏幕 = 纸中心 + (纸内点 − 纸中心) × zoom + 平移` ✓，
 *     所以逆变换就是下面那两行（先减平移、再除 zoom、最后加回纸中心 ✓）——
 *     **不多一处、不少一处** ✓
 *  3. **`screen` 和 `paper` 是同一个坐标空间** ✓：两者都是**"让开 `padding(14.dp)` 之后那一圈"的 px** ——
 *     `paperPx` 来自 `.padding(14.dp)` **之后**的 `onSizeChanged` ✓，而 `pointerInput` /
 *     `penInputModifier` 也挂在 `.padding(14.dp)` **之后**的修饰符链上 ✓（见 [ComicCanvasArea]
 *     里那两处的位置说明 ✓）。14dp 那种偏差**不存在** ✓
 *     （就算有，它只会让整幅**平移**、不会让点距**变** —— 两回都一样 ✓）
 *  4. **DPR（`LocalDensity.density`）在这条链上"没有第二个存在"** ✓：
 *     Compose 给 `pointerInput` 的 `position` 和 `onSizeChanged` 的 `IntSize` **都是 px** ✓，
 *     ⚠️ 电脑端的 `density` **恒为 1.0** ✓（exe 不是 DPI 感知的，Windows 把整个窗口**位图拉伸** ✓，
 *     见 `Platform.uiScale` 的说明 ✓）。150% 那台机器上"屏幕上的一个 px"其实是**物理** px，
 *     在窗口里只占 1/1.5 个逻辑 px —— 它会**均匀放大**屏幕点距，但**不改变比例** ✓
 *     （`[PenDab]` 日志里的 `dpr=` 就是它 ✓）
 *
 * 口径：全程**组件局部 px** ✓（不接受 dp ✗、不接受窗口坐标 ✗）。
 */
internal fun comicScreenToPagePixel(
    screen: Offset,
    paper: IntSize,
    pageWidth: Int,
    pageHeight: Int,
    zoom: Float,
    pan: Offset,
    // 第 ㊶ 批：画布旋转角（度 ✓，默认 0 = 老行为逐字节不变 ✓）
    rotationDegrees: Float = 0f,
): Offset? {
    if (paper.width <= 0 || paper.height <= 0 || pageWidth <= 0 || pageHeight <= 0) return null
    if (zoom <= 0f) return null
    val centerX = paper.width / 2f
    val centerY = paper.height / 2f
    // ⚠️ 第 ㊶ 批：旋转要**先撤销** ✗ ——
    //    `graphicsLayer` 的 order 是 scale → rotate → translate（都在纸中心锚点 ✓），
    //    正变换 = 纸中心 + R(θ)·((纸内点 − 纸中心) × zoom) + 平移 ✓，
    //    所以逆变换要先减平移、再**反向转 θ**、再除以 zoom ✓（顺序反了画出来就是歪的 ✗）。
    var dx = screen.x - centerX - pan.x
    var dy = screen.y - centerY - pan.y
    if (rotationDegrees != 0f) {
        val rad = Math.toRadians(-rotationDegrees.toDouble())
        val c = kotlin.math.cos(rad).toFloat()
        val s = kotlin.math.sin(rad).toFloat()
        val rx = dx * c - dy * s
        val ry = dx * s + dy * c
        dx = rx
        dy = ry
    }
    val localX = centerX + dx / zoom
    val localY = centerY + dy / zoom
    if (localX < 0f || localY < 0f || localX > paper.width || localY > paper.height) return null
    return Offset(
        x = localX / paper.width * pageWidth,
        y = localY / paper.height * pageHeight,
    )
}

/** 画布编辑器这一次拖动是**哪一种**（⑦：只有 SELECT / LASSO 才会走到后面的选区那几条 ✓）。 */
private enum class ComicPaintDrag { NONE, STROKE, MARQUEE, LASSO, MOVE, SCALE }

/**
 * **正在编辑的那一层**（第 ㉜a 批 ✓）—— 显示的是**视口分辨率**。
 *
 * ## 做法（三行）
 *
 * ```
 * ① 算出**可见矩形**（纸内局部 px → 页像素 ✓）
 * ② 交给 `AppState.comicPaintViewportFrame`：从层表面**区域回读** → blit 到视口大小的 Surface
 *    ⇒ 变成 Compose 位图（代价只跟**视口像素数**成正比 ✓）
 * ③ 画回来（`drawImage`，dst = 那个可见矩形 ✓）
 * ```
 *
 * ## 三条硬约束（都是踩出来的 ✗）
 *
 * 1. **不是"整页层位图 + 小块叠加"那条** ✓：那条链每次重建要 ~8.7 MP（A4 竖 ⇒ ~33 MB 纹理）
 *    （实测 7.55 ms/帧 ✓，见 `docs/53` ✓）—— 现在每帧只碰视口的 0.4~0.6 MP ✓；
 * 2. **不是"每帧 `makeImageSnapshot()`"** ✗✗：快照本身 ~0.001 ms（写时复制 ✓）；
 *    **拿着快照继续往表面上画**会触发**整页拷贝**（实测 7.04 ms/颗笔尖、~90 ✗，
 *    见 `SurfaceCanvasProbeTest` ✓）—— 显示走 `readPixels` 那个**区域回读**（~0.076 ms ✓）；
 * 3. **橡皮不用离屏 `DstOut` 层了** ✓：层表面上的 alpha 已经被真的减掉（`DST_OUT` ✓），
 *    直接画就是对的 ✓（旧版要离屏是因为橡皮在活笔画里存的是白面 ✗）。
 *
 * ⚠️ 状态一律在 **draw 阶段**读（`state.comicPaintFrameTick` / `comicPaintVersion` ✓）——
 * 读到快照状态只会让这一次绘制失效 ✓、**不触发重组** ✓（每颗笔尖都重画但不重组的关键 ✓）。
 *
 * ⛔ **2026-09-22 试过一版"整页 PNG 帧"（每收一笔整页编 PNG、整页 `Fit` 贴回来），实测卡飞了 ✗**
 *    —— 回读口径是对的（省），已按用户要求**回退**到本函数这条路 ✓。要再动这条链先量帧耗 ✓。
 */
@Composable
private fun ComicPaintViewportLayer(
    state: com.kallan.naistudio.state.AppState,
    paper: IntSize,
    area: IntSize,
    // ⚠️ 被裁的那一圈比 `area` 大出来的量（px ✓）：`area` 是"让开 padding 之后"的内框，
    //    而真正被 clip 的是外面那一圈 —— 纸放大之后会画进内边距，那几十像素**看得见** ✓。
    //    少了这一圈，视口帧就只覆盖内框 ⇒ 上 / 左各留一条没画满的窄带 ✗
    //（用户 2026-09-21 报的「放大缩小画布，线条会乱飞」里就带着这一条 ✓）。
    bleedX: Float,
    bleedY: Float,
    zoom: Float,
    pan: Offset,
    opacity: Float,
    /**
     * 拖动某一格时，**正在编辑的这层也要跟着走** ✓（用户 2026-09-22：「拖动的是格子，
     * 不是图像，要一起拖动」✓）—— 单位 **dp** ✓（调用方按"页像素 × `scale`"换算好传进来 ✓）。
     */
    dragOffsetDp: Offset = Offset.Zero,
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .alpha(opacity)
            .offset(x = dragOffsetDp.x.dp, y = dragOffsetDp.y.dp),
    ) {
        // ---- draw 阶段读状态（只让这次绘制失效 ✓）----
        val tick = state.comicPaintFrameTick
        val revision = state.comicPaintFrameRevision()
        val session = state.comicPaintSession ?: return@Canvas
        if (paper.width <= 0 || paper.height <= 0 || area.width <= 0 || area.height <= 0) return@Canvas
        if (zoom <= 0f) return@Canvas
        val cx = paper.width / 2f
        val cy = paper.height / 2f
        // 纸在屏幕上的矩形：`屏幕 = 纸中心 + (纸内点 − 纸中心) × zoom + 平移` ✓（和 `comicScreenToPagePixel` 同一套 ✓）
        val paperLeft = cx * (1f - zoom) + pan.x
        val paperTop = cy * (1f - zoom) + pan.y
        val paperRight = paperLeft + paper.width * zoom
        val paperBottom = paperTop + paper.height * zoom
        // ⚠️ 变换只有一处：纸那层的 `graphicsLayer`；这个 Canvas 是纸的子节点 ⇒ 局部坐标 = **纸内局部坐标**。
        // 可见区 = 纸的屏幕矩形 ∩ **真正被 clip 的那一圈**（内框 area + 两侧的 bleed）
        val visLeft = maxOf(-bleedX, paperLeft)
        val visTop = maxOf(-bleedY, paperTop)
        val visRight = minOf(area.width + bleedX, paperRight)
        val visBottom = minOf(area.height + bleedY, paperBottom)
        if (visRight - visLeft < 1f || visBottom - visTop < 1f) return@Canvas
        val localLeft = (cx + (visLeft - pan.x - cx) / zoom).coerceIn(0f, paper.width.toFloat())
        val localTop = (cy + (visTop - pan.y - cy) / zoom).coerceIn(0f, paper.height.toFloat())
        val localRight = (cx + (visRight - pan.x - cx) / zoom).coerceIn(0f, paper.width.toFloat())
        val localBottom = (cy + (visBottom - pan.y - cy) / zoom).coerceIn(0f, paper.height.toFloat())
        if (localRight - localLeft < 1f || localBottom - localTop < 1f) return@Canvas
        // 纸内局部 px → 页像素（纸就是页的等比缩放）——
        // ⚠️ **先取整、再由它们反推 dst**：这样"读出来的那块"与"摆回去的那块"**严丝合缝** ✓
        //（各算各的会差半个页像素 ⇒ 帧内容被拉 ~0.2px ✗，放大多倍时看得出来 ✓）。
        val srcX = floor(localLeft / paper.width * session.width).toInt().coerceIn(0, session.width - 1)
        val srcX1 = ceil(localRight / paper.width * session.width).toInt().coerceIn(srcX + 1, session.width)
        val srcY = floor(localTop / paper.height * session.height).toInt().coerceIn(0, session.height - 1)
        val srcY1 = ceil(localBottom / paper.height * session.height).toInt().coerceIn(srcY + 1, session.height)
        val srcW = srcX1 - srcX
        val srcH = srcY1 - srcY
        // 摆回去的 dst（纸内局部 px，由 src 精确反推 ✓）
        val dstX = srcX.toFloat() / session.width * paper.width
        val dstY = srcY.toFloat() / session.height * paper.height
        val dstWLocal = srcW.toFloat() / session.width * paper.width
        val dstHLocal = srcH.toFloat() / session.height * paper.height
        // 视口 = 可见矩形在**屏幕上的设备像素**大小 ✓（画到局部尺寸上、再由 `graphicsLayer` 放大 zoom ⇒ 1:1 ✓）
        val dstW = (dstWLocal * zoom).roundToInt().coerceAtLeast(1)
        val dstH = (dstHLocal * zoom).roundToInt().coerceAtLeast(1)
        val frame = state.comicPaintViewportFrame(
            srcX = srcX,
            srcY = srcY,
            srcW = srcW,
            srcH = srcH,
            dstW = dstW,
            dstH = dstH,
            revisionKey = revision * 1_000_003L + tick,
        ) ?: return@Canvas
        drawImage(
            image = frame,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(frame.width, frame.height),
            dstOffset = IntOffset(dstX.roundToInt(), dstY.roundToInt()),
            dstSize = IntSize(
                dstWLocal.roundToInt().coerceAtLeast(1),
                dstHLocal.roundToInt().coerceAtLeast(1),
            ),
            // 视口那一帧已经是**屏幕分辨率**了 ⇒ 这一步基本是 1:1 拷贝 ✓（`Low` = 双线性，最快 ✓）
            filterQuality = FilterQuality.Low,
        )
    }
}

/** 选区四个角（下标：0 左上 / 1 右上 / 2 左下 / 3 右下 ✓；对角 = `3 - index` ✓）。 */
private fun comicSelectionCorners(bounds: EditRect): List<Offset> = listOf(
    Offset(bounds.x.toFloat(), bounds.y.toFloat()),
    Offset((bounds.x + bounds.w).toFloat(), bounds.y.toFloat()),
    Offset(bounds.x.toFloat(), (bounds.y + bounds.h).toFloat()),
    Offset((bounds.x + bounds.w).toFloat(), (bounds.y + bounds.h).toFloat()),
)

/** 这一点在不在那个框里（坐标都是**底板像素** ✓）。 */
private fun comicInside(bounds: EditRect, point: Offset): Boolean =
    point.x >= bounds.x && point.x <= bounds.x + bounds.w &&
        point.y >= bounds.y && point.y <= bounds.y + bounds.h

/**
 * 这一点按在**哪个角控制点**上（返回 0..3 ✓；都不沾返回 null ✓）。
 * 坐标全是底板像素 ✓；命中半径由调用方按"屏幕上的 16dp"换算好传进来 ✓
 * （直接写一个固定像素数的话，A4 那么大的页上控制点会小得点不着 ✗）。
 */
private fun comicSelectionHandleAt(point: Offset, bounds: EditRect, radius: Float): Int? {
    if (radius <= 0f) return null
    val corners = comicSelectionCorners(bounds)
    var best: Int? = null
    var bestSquared = radius * radius
    corners.forEachIndexed { index, corner ->
        val dx = point.x - corner.x
        val dy = point.y - corner.y
        val squared = dx * dx + dy * dy
        if (squared <= bestSquared) {
            bestSquared = squared
            best = index
        }
    }
    return best
}

/**
 * 控制点的命中半径（**底板像素** ✓）：屏幕上的 16dp 换算成纸上多少像素。
 * `屏幕上每一底板像素占几个 px = 纸宽px / 页宽 × 缩放` ✓ —— 和滚轮锚点用的是同一套量 ✓。
 */
private fun comicSelectionHandleRadiusPx(
    paper: IntSize,
    pageWidth: Int,
    zoom: Float,
    density: Float,
): Float {
    if (paper.width <= 0 || pageWidth <= 0 || zoom <= 0f) return 0f
    val screenPxPerPagePx = paper.width.toFloat() / pageWidth.toFloat() * zoom
    if (screenPxPerPagePx <= 0f) return 0f
    return (16f * density) / screenPxPerPagePx
}

/**
 * **笔 vs 鼠标**：这一笔到底归谁画（`ComicCanvasArea` 里那个 `remember { PenPointerGate() }` ✓）。
 * ## 为什么需要它
 *
 * 桌面端的笔事件走的是**原生 JNI**（Windows = RealTimeStylus ✓）——**另一条通道** ✗，
 * 不经过 Compose 的指针管线 ✓；而 Windows **同时**会把同一支笔**提升成鼠标事件** ✓，
 * 于是"指针那条老路"也会看到这一下 ✓ —— 两条路都画的话就是**两笔** ✗。
 * ## 判据为什么是"笔此刻正按着"，而不是"最近收到过笔"
 *
 * 数位板搁在桌上、笔尖悬在板子上不动时，驱动**会一直报 Hover** ✓ ——
 * 拿"最近收到过笔事件"当判据，就会出现"笔在旁边悬着 + 用鼠标点 ⇒ 点了没反应" ✗
 * 所以闸门只在 [down]（笔**真的按在纸上** ✓）时为真 ✓
 *
 * ## 两种先后顺序都收敛到同一笔 ✓
 *  · **笔的事件先到**（常见 ✓）：笔回调开笔 → [down] = true → 指针那条路在
 *    `awaitFirstDown` 之后**整段让开** ✓（一笔，**有压感** ✓）
 *  · **指针的事件先到**（不保证不发生 ✗）：指针那条路先落了一笔（没压感 ✗）
 *    笔回调一进来看到 `session.isDrawing` ⇒ **收掉 + 撤回**那一笔、按**真压感重开** ✓
 *    （见 `ComicCanvasArea` 里那一支的说明 ✓）
 *
 *  ⚠️ 这一整套**我这边没有数位板，真机顺序只能由用户验** ✗（见回报 ✓）。
 */
private class PenPointerGate {
    /** 笔**正按着**（笔回调开的笔还没抬 ✓）—— 指针那条路看到它就让开 ✓。 */
    var down: Boolean = false

    /**
     * 笔尾橡皮**自动切过去之前**手里那个工具 ✓（切回笔尖时还回去 ✓，见笔回调 ✓）。
     * `null` = 现在这个橡皮**不是**自动切来的（用户自己选的 ✓）→ 什么都不做 ✓。
 */
    var beforeEraser: CanvasTool? = null
}

/**
 * 这个工具**是不是"沿着拖出一笔"的那种** ✓（笔压感只对这种有意义 ✓）。
 * 口径与指针那条路的 `else ->` 分支**逐个对齐** ✓：油漆桶 / 吸管是"点一下就完事" ✓、
 * 选择 / 套索只动选区 ✓ —— 这四种**统一交给指针那条老路** ✓（它们不吃笔压 ✓，
 * 而且"点一下"本来就不需要另开一条画法 ✓）。
 */
private fun CanvasTool.isPenStrokeTool(): Boolean = when (this) {
    CanvasTool.DRAW, CanvasTool.ERASE, CanvasTool.BLUR, CanvasTool.CLONE -> true
    CanvasTool.FILL, CanvasTool.PICKER, CanvasTool.SELECT, CanvasTool.LASSO -> false
}

/**
 * 画在纸上的**选区框 + 四角控制点**（只有 SELECT / LASSO 时出现 ✓）。
 * ⚠️ `Canvas` + `drawPath` / `drawRect(Stroke)` —— **绝不用 `Modifier.border(…, 自定义 Shape)`** ✓
 * （电脑端那条会抛 `Failed to Image::makeFromBitmap`，用户实机崩过 ✓）
 * ⚠️ **没有任何 `pointerInput`** ✓ —— 命中的是外层画笔手势（见 [comicScreenToPagePixel] ✓）
 * 这一层只负责"看得见" ✓。
 */
@Composable
private fun ComicPaintSelectionOverlay(
    shape: SelectionShape,
    bounds: EditRect,
    pageWidth: Int,
    pageHeight: Int,
    scale: Float,
    handlesVisible: Boolean,
) {
    val density = LocalDensity.current
    val ring = MaterialTheme.colorScheme.primary
    val factor = scale * density.density
    Canvas(Modifier.fillMaxSize().zIndex(70f)) {
        val path = Path()
        when (shape) {
            is SelectionShape.Rect -> {
                val l = minOf(shape.left, shape.right) * pageWidth * factor
                val t = minOf(shape.top, shape.bottom) * pageHeight * factor
                val r = maxOf(shape.left, shape.right) * pageWidth * factor
                val b = maxOf(shape.top, shape.bottom) * pageHeight * factor
                path.addRect(
                    androidx.compose.ui.geometry.Rect(l, t, maxOf(r, l + 1f), maxOf(b, t + 1f)),
                )
            }

            is SelectionShape.Polygon -> {
                val points = shape.points
                if (points.size >= 2) {
                    path.moveTo(points[0].x * pageWidth * factor, points[0].y * pageHeight * factor)
                    points.drop(1).forEach { point ->
                        path.lineTo(point.x * pageWidth * factor, point.y * pageHeight * factor)
                    }
                    path.close()
                }
            }
        }
        drawPath(path, color = ring, style = Stroke(width = 2f))
        // 四角控制点（拖它 = 缩放选区里的内容 ✓）：实心小方块，一眼看得见 ✓
        if (handlesVisible) {
            comicSelectionCorners(bounds).forEach { corner ->
                drawRect(
                    color = ring,
                    topLeft = Offset(corner.x * factor - 5f, corner.y * factor - 5f),
                    size = Size(10f, 10f),
                )
            }
        }
    }
}

/**
 * 画布区。
 * 三层叠起来（从下往上）：
 *  1. **底板**：空白画布 = 一张纸；导入了 = 那张图（`FileImage`，等比 Fit ✓）
 *  2. **格子**：`offset + size` 摆到对应位置，各自带手势 ✓
 *  3. **手拖预览**：正在拉的那个矩形（纯装饰，不接指针，免得抢掉格子的手势 ✗）
 *
 * ⚠️ 修饰符顺序（这仓库的老教训）：`offset{} / size()` **排在** `pointerInput` **前面** ✓ ——
 * ## ② 可以随意拖动 / 缩放（用户 2026-09-20，照生图页那套抄 ✓）
 *
 * 结构是**两层**：
 *   └─ Box（纸：size(drawW, drawH) + graphicsLayer{ scale + translate }）
 * ```
 *
 *  · **缩放 / 平移只画在"纸"那一层的 `graphicsLayer` 上** ✓ —— 布局尺寸一个字节都没变
 *    （`drawW/drawH` 还是"按可用区域等比 fit"那套 ✓），所以纸里面 `toPage` / `scale`
 *    的换算**一个字都没改** ✓，格子 / 气泡 / 文本的命中测试**自动**跟着变换走 ✓；
 *  · **手势挂在外面那一层**（未变换的坐标）✗ 不能挂在纸里面：拖动的 delta 一旦是"纸内坐标"，
 *    平移会自己追自己（每帧把位移算回去 → 拖不动 ✗）。滚轮的锚点同理，用的是**纸中心**在
 *  · **左键在空白纸上拖动照旧 = 拖出一个新格子** ✓：只有"空格按着"和"中键"那两下算平移；
 *    它们会在**按下那一刻**（`PointerEventPass.Initial`，比纸那一层的手势早 ✓）
 *    `ui.canvasPanGesture` 立起来，画布上每一处拖动 / 缩放 = 改内容的手势看到它就**整段让开** ✓
 */
@Composable
private fun ComicCanvasArea(
    state: AppState,
    page: ComicBoardPage?,
    selectedPanelId: String?,
    onSelect: (String?) -> Unit,
    overlayUi: ComicModeUiState,
    t: (String) -> String,
    canvasBase: Color = CanvasBackdropColor,
    drawCanvasGrid: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            // ⚠️ **画布底：三档统一那一套**（用户 2026-09-24：「**统一画布背景，用漫画模式的格子背景，
            //    颜色再深一些**」✓）—— 就是 `ui/CanvasBackdrop.kt` 里的 `canvasBackdrop()` ✓
            //    （底色 + 18dp 方格 ✓，深浅线自适应 ✓）。这一档是**这套背景的出处** ✓，
            //    现在收回共用处 ✓（原来那三个常量就删掉了 ✓，免得两处各写一遍 ✗）。
            //  · 沿革：跟主题走（暗色发黑、与白纸对比太硬 ✗）→ 用户 2026-09-22 定"**固定浅灰、
            //    暗色模式也不变**"✓ + "**画布背景换回格子背景**"✓ → 本轮把底色压深一点并收成共用 ✓。
            .canvasBackdrop(canvasBase, drawGrid = drawCanvasGrid),
        contentAlignment = Alignment.Center,
    ) {
        if (page == null) {
            // 还没有底板：给一条明确的路（用户口径：先"新建画布或导入图像"✓）
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Text(t("comic.noBase"), style = MaterialTheme.typography.titleMedium)
                Text(
                    t("comic.noBaseHint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Box
        }

        // ---- ② 摆放（缩放 / 平移）要用的状态 ----
        //
        // ⚠️ 这几样必须在 `BoxWithConstraints` **之前**声明：手势是挂在那一位的修饰符链上的，
        //    而修饰符链在内容 lambda 之前就建好了（`drawW` / `paperCenter` 那些在 lambda **里面**
        //    才算得出来 ✗）—— 所以坐标相关的量一律走**量出来的状态**（`onSizeChanged` ✓），
        //  · `canvasAreaPx`：画布区（让开 14dp 边距之后）的实际像素尺寸 —— 平移的夹取范围用它 ✓；
        //  · `paperPx`：**纸**的像素尺寸 —— 滚轮缩放的锚点原点 = 纸中心（= 纸尺寸的一半 ✓，
        //  · `panning`：正在拖 → 手型光标换成"握手" ✓；
        //  · `overlayUi.canvasPanGesture`：**按下那一刻**判定的"这一下是平移"（空格 / 中键 ✓）——
        //    看到就整段让开 ✓（**不能改成"此刻空格按着没"** ✗：松开之后才抬起的那次 tap
        //    会把自己判成"不是平移"，于是顺手落一个气泡 ✗）。放在共享状态里是因为
        var canvasAreaPx by remember { mutableStateOf(IntSize.Zero) }
        // ⚠️ **被裁的那一圈**（= 外层 Box，含那 14dp 内边距 ✓）——
        // 视口那一帧要盖住的是它，不是内框：纸放大之后会画进内边距，那几十像素用户看得见 ✓
        //（少算这一圈 = 上 / 左各留一条没画满的窄带 ✗，用户 2026-09-21 报的"线条乱飞"里带着它 ✓）。
        // `bleed` 直接从两次量出来的尺寸反推（不写死 14dp ✓，布局改了自动跟上 ✓）。
        var canvasOutlinePx by remember { mutableStateOf(IntSize.Zero) }
        var paperPx by remember(page.id) { mutableStateOf(IntSize.Zero) }
        var panning by remember(page.id) { mutableStateOf(false) }
        var rotating by remember(page.id) { mutableStateOf(false) }
        val canvasUiHost = LocalUiHost.current
        // ⚠️ 画笔手势挂在**修饰符链**上（在 `BoxWithConstraints` 的内容 lambda **之前** ✓）
        // 所以密度得在**这一层**读一次 ✓（内容 lambda 里那个 `density` 在这儿够不着 ✗）
        // 它只用来把"屏幕上的 16dp"换算成选区控制点的命中半径 ✓（见 `comicSelectionHandleRadiusPx` ✓）
val gestureDensity = LocalDensity.current
        val panCursor = if (state.canvasSpacePan || panning) {
            canvasUiHost.panCursor(grabbing = panning)
        } else {
            Modifier
        }

        // ---- ⓪′ 数位板 / 触控笔（用户 2026-09-20：「compose-stylus 先接这个」✓）----
        //
        // ⚠️ **这一层**（外层 = 未经过 `graphicsLayer` 的那套坐标 ✓）的理由，和下面那条画笔手势
        // 一模一样 ✓：库给出来的是"**这个 `Modifier` 所在组件的局部像素**" ✓
        //（库里已经减掉了 `positionInWindow()` ✓），和 `pointerInput` 里的 `position` **是同一套** ✓
        // ⚠️ 所以能**原样**喂给现成的 `comicScreenToPagePixel` ✓（不需要第二份换算 ✗）
        //
        // ⚠️ 两条"没破坏老路"的口径（用户点名要核 ✓）：
        //  · **鼠标 / 触摸照旧走下面那几条 `pointerInput`** ✓ 一个字都没动 ——
        //    库里那个节点只**旁观**、从不 `consume()` ✓（见 `Platform.penInputModifier` 的说明 ✓）
        //    而且下面那条画笔手势**只在"笔真的在按"时才整段让开** ✓（见 `PenPointerGate` ✓）
        //  · 收不到笔信号时这层**什么都不发** ✓（`penTool == false` 一律早退 ✓）——
        //    只是把状态行那句写成"没有信号" ✓，绝不冒充"压力 = 1" ✗（用户点名的坑 ✓）
        //
        // ⚠️ `penKey` 必须是**每个节点各一个**的（`remember { Any() }` ✓）：库里**同一个 key 的节点**
        //    会互相顶掉 ✗（后注册的把先注册的换掉 ✓）—— 这一页的宽 / 窄两种布局各有一个
        //    [ComicCanvasArea]，同 key 的话会有一份收不到事件 ✗。它只在组合里建一次 ✓，
        //    重组时 key 不变 ✓（key 一变库里会走 unregister/register，笔画中途被打断 ✗）。
        // ⚠️ **第 ㊽ 批（2026-09-21）**：用户口径「**删除画笔和压感功能**」✓ ——
        //    这一段原来装着：数位板压感回调（`penInputModifier` ✓）/ 双通道攒点器 / 帧边界喂点 ✓。
        //    **整段删掉** ✓（画布现在只做"看 + 拖格子"✓，不再接收任何笔迹输入 ✓）。
        //    ⚠️ 生成页那颗铅笔（`CanvasEditor` ✓）是**另一套、且保留** ✓ —— 它的引擎没动 ✓。

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                // ⚠️ **第 ㊸ 批（2026-09-21）**：用户报「**ctrl z没反应，只有点击工具栏的撤销才有反应**」✓
                //
                // **根因**：`Ctrl+Z` 那条键位**只写在 `CanvasEditor`（运行条铅笔那个编辑器）里** ✗ ——
                //    `ComicModeScreen` 全文件**一个 `onKeyEvent` 都没有** ✗ ⇒
                //    漫画模式里按 Ctrl+Z 根本没人接 ✓，只有工具栏那颗按钮能用 ✓（用户观察完全正确 ✓）。
                //
                // **修法**：在画布这一层挂 `onPreviewKeyEvent` ✓（**预览阶段** ⇒ 比子节点先拿到键 ✓ ——
                //    画布里的输入框不该把 Ctrl+Z 当"文本框撤销"吃掉 ✗）。
                //    键位与 `CanvasEditor` **同一个口径** ✓：Ctrl+Z 撤销、Ctrl+Y / Ctrl+Shift+Z 重做 ✓。
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (!event.isCtrlPressed) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.Z && event.isShiftPressed -> {
                            state.redoComicPaint(); true
                        }
                        event.key == Key.Z -> {
                            state.undoComicPaint(); true
                        }
                        event.key == Key.Y -> {
                            state.redoComicPaint(); true
                        }
                        else -> false
                    }
                }
                // 外层尺寸 = **真正被 clip 的那一圈**（下面 `padding` 之前量 ✓）
                .onSizeChanged { canvasOutlinePx = it }
                .padding(14.dp)
                // ⚠️ 顺序：`padding` 在前 —— 后面这几样的坐标系就是**让开边距之后**那一块
                .onSizeChanged { canvasAreaPx = it }
                // ⓪′ **数位板 / 触控笔那条路**（第 ㉟② 批·压感修复 ✓）——
                // ⚠️ 这一行是本批修的**唯一一个 bug** ✓：上面那个 `penModifier`（本函数第 1753 行 ✓）
                //    原先**只被赋值、从没进过修饰符链** ✗（`grep "penModifier"` 全文件只有定义那一处命中 ✗）
                //    ⇒ 库里那个节点根本没注册 ⇒ 回调**一次都不触发** ✗ ⇒ `[PenDab]` 里
                //    `inputPenDriven` **恒为 false**、`r=min/max` 恒等（用户 2026-09-21 报的
                //    「没压感，是平直的线」✓）。实机日志与它**完全自洽** ✓：
                //     `[PenInput] compose-stylus 原生库已加载` 一条不少（那是 `penInputModifier()`
                //     函数体里的探针，求值 `val` 时就会打 ✓）—— 而笔回调一次都没进来 ✓。
                //    ⚠️ Kotlin **不会**为"未使用的局部变量"报错 ⇒ 它是**静默失效** ✗（最阴的一类 ✓）。
                //
                // 位置讲究（与上面 `.padding(14.dp)` 同一个理由 ✓，见 `comicScreenToPagePixel` 的说明 ✓）：
                // 库给的是"**这个 Modifier 所在组件的局部像素**"✓（它已减掉 `positionInWindow()` ✓），
                // 必须和下面那几条 `pointerInput` **同一个原点** ✓ ⇒ 只能挂在 `padding` **之后** ✓；
                // 再靠前一点（`.then(panCursor)` 之前 ✓）是为了不与下面那几条手势的注册顺序打架 ✓。
                // 空格按着（或正在拖）= 手型光标（和生成页 / 画布编辑器同一套两只手 ✓）
                .then(panCursor)
                // 位置讲究：必须在 `padding(14.dp)` **之后** ✓（坐标原点才和下面那几条手势同一套 ✓）。
                // ⓪ 记"这一下是不是平移"（`Initial` 阶段 = 比纸那一层的手势**早**拿到事件 ✓）
                .pointerInput(page.id) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.changedToDown() }) {
                                // ⚠️ 空格**现读** `state.canvasSpacePan`（快照状态，永远是最新的 ✓）——
                                // 这个指针协程不会因为重组而重启，提前存成局部 val 会一直停在旧值 ✗
val mouse = event.changes.any { it.type == PointerType.Mouse }
                                overlayUi.canvasPanGesture = state.canvasSpacePan ||
                                    (mouse && event.buttons.isTertiaryPressed)
                                // 第 ㊿i 批（2026-09-22）：这一下按着 Ctrl 吗 ——「图画要按住 ctrl 才能拖动」✓。
                                // ⚠️ 同样**只认按下那一刻** ✓（理由见 `canvasCtrlGesture` 的说明 ✓）。
                                // ⚠️ 来源以**窗口层收的键盘状态**为准 ✓（`state.canvasCtrlDown`）——
                                //    指针事件自带的 `keyboardModifiers` 实测按着 Ctrl 也判不出来 ✗
                                //（用户 2026-09-22 报「按住 ctrl 不能拖动」✓）；两个都看，哪个通用哪个 ✓。
                                overlayUi.canvasCtrlGesture = state.canvasCtrlDown ||
                                    event.keyboardModifiers.isCtrlPressed
                            }
                        }
                    }
                }
                // ① 滚轮 = **以光标为锚点**缩放（锚点公式与生成页逐字一致 ✓）
                .pointerInput(page.id) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val scroll = change.scrollDelta.y
                            if (scroll == 0f) continue
                            val current = overlayUi.canvasScale
                            val next = (current * (1f - scroll * COMIC_CANVAS_WHEEL_ZOOM_STEP))
                                .coerceIn(COMIC_CANVAS_MIN_SCALE, COMIC_CANVAS_MAX_SCALE)
                            if (next != current) {
                                val paper = paperPx
                                val anchor = Offset(
                                    x = change.position.x - paper.width / 2f,
                                    y = change.position.y - paper.height / 2f,
                                )
                                val ratio = next / current
                                overlayUi.canvasOffset = Offset(
                                    x = anchor.x - (anchor.x - overlayUi.canvasOffset.x) * ratio,
                                    y = anchor.y - (anchor.y - overlayUi.canvasOffset.y) * ratio,
                                )
                                overlayUi.canvasScale = next
                            }
                            change.consume()
                        }
                    }
                }
                // ② 鼠标中键 / 按住空格 + 左键 = 平移（夹取到画布区的一半，防"一把手甩出去找不回来" ✓）
                .pointerInput(page.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val middleDown = down.type == PointerType.Mouse &&
                            currentEvent.buttons.isTertiaryPressed
                        // 既没按空格、也不是中键 → 这一下不是平移（照旧交给纸那一层去拖格子 ✓）
                        if (!(state.canvasSpacePan || middleDown)) return@awaitEachGesture
                        panning = true
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                val change = pressed[0]
                                val delta = change.position - change.previousPosition
                                if (delta != Offset.Zero) {
                                    val area = canvasAreaPx
                                    val limitX = if (area.width > 0) area.width / 2f else Float.MAX_VALUE
                                    val limitY = if (area.height > 0) area.height / 2f else Float.MAX_VALUE
                                    overlayUi.canvasOffset = Offset(
                                        x = (overlayUi.canvasOffset.x + delta.x).coerceIn(-limitX, limitX),
                                        y = (overlayUi.canvasOffset.y + delta.y).coerceIn(-limitY, limitY),
                                    )
                                    change.consume()
                                }
                            }
                        } finally {
                            // 中途被打断（工具被切、组合离开）也要把手型收回来 ✓
                        }
                    }
                }
                // ②b **空格 + Alt + 拖动 = 旋转画布**（第 ㊶ 批 2026-09-21 ✓）——
                //     用户口径：「**按着 alt+空格可以旋转画布**」✓。
                //     ⚠️ 判据在**按下那一刻**定 ✓（与平移那条同一个纪律 ✓）：
                //        中途松开 Alt 不该把这次旋转变成平移 ✗（手感会跳 ✓）。
                //     ⚠️ **必须排在下面平移那条之前** ✗：两条都用空格，
                //        先到的先 `consume()` ⇒ 平移那条就不该再吃这一次 ✓
                //        （这里 `consume()` 了 ⇒ 平移那条的 `awaitFirstDown` 拿不到未消费的按下 ✓）。
                .pointerInput(page.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // 这一下要转画布：空格按着 **且** Alt 按着 ✓
                        if (!state.canvasSpacePan || !currentEvent.keyboardModifiers.isAltPressed) {
                            return@awaitEachGesture
                        }
                        overlayUi.canvasRotateGesture = true
                        rotating = true
                        try {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            var lastAngle = atan2(
                                (down.position.y - center.y).toDouble(),
                                (down.position.x - center.x).toDouble(),
                            )
                            down.consume()
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                val change = pressed[0]
                                val angle = atan2(
                                    (change.position.y - center.y).toDouble(),
                                    (change.position.x - center.x).toDouble(),
                                )
                                var deg = Math.toDegrees(angle - lastAngle).toFloat()
                                lastAngle = angle
                                // 夹到 (-180, 180]：atan2 的差值本来就在这个区间 ✓（防跳变 ✓）
                                if (deg > 180f) deg -= 360f
                                if (deg < -180f) deg += 360f
                                overlayUi.canvasRotation = (overlayUi.canvasRotation + deg) % 360f
                                change.consume()
                            }
                        } finally {
                            overlayUi.canvasRotateGesture = false
                            rotating = false
                        }
                    }
                }
                //    SELECT / LASSO = 框选区、拖动选区里的内容、四角缩放 ✓（用户 2026-09-20 第 2 条 ✓）
                //
                // ⚠️ 这一条挂在**外面那一层**（未经过 `graphicsLayer` 的坐标 ✓）——
                //    原因见 `comicScreenToPagePixel` 的说明（格子 / 气泡自己的手势会 `consume()`，
                //    挂在纸里面就画不到 ✓）；坐标自己撤销缩放 + 平移 ✓
                // ⚠️ **只读**不缓存布尔：`state.comicPaintSelecting` / `state.comicPaintTool` 都是
                //    快照状态，指针协程里现读永远是最新的 ✓（`pointerInput` 的 key 里放 Boolean
                .pointerInput(page.id, page.baseWidth, page.baseHeight) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // ⓪′ **这一下已经是笔在画了** ⇒ 整段让开 ✓（笔那条路已经开好笔了 ✓，见
                        //    `ComicCanvasArea` 里那个笔回调 + `PenPointerGate` 的说明 ✓）
                        //    ⚠️ 判据**只有"笔此刻正按着"** ✓ —— 不是"最近收到过笔事件" ✗：
                        //    数位板搁在板上、笔尖悬着不动时也会一直报 Hover ✓，拿"最近"当判据
                        //    会让"笔在旁边悬着 + 用鼠标点"变成点了没反应 ✗
                        //    "笔先到 / 鼠标先到"两种顺序都收敛到**同一笔**（见笔回调里那一支 ✓）
// 第 ㊽ 批：笔那条通道已删 ⇒ 这道闸不再需要（见上）
                        if (overlayUi.canvasPanGesture) return@awaitEachGesture
                        // 只认**左键**（右键 / 中键不落笔 ✓）—— 这一条要排在下面那句"如实提示"前面 ✓，
                        if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) {
                            return@awaitEachGesture
                        }
                        val tool = state.comicPaintTool
                        val start = comicScreenToPagePixel(
                            screen = down.position,
                            paper = paperPx,
                            pageWidth = page.baseWidth,
                            pageHeight = page.baseHeight,
                            zoom = overlayUi.canvasScale,
                            pan = overlayUi.canvasOffset,
                                    rotationDegrees = overlayUi.canvasRotation,
                        )
                        // ⚠️ **第 ㊽ 批（2026-09-21）**：用户口径「**删除画笔和压感功能**」✓ ——
                        //    画笔类工具（画 / 擦 / 桶 / 模糊 / 取色 / 图章 ✓）在这一页**整类不受理** ✗：
                        //    拿在手里按下去**什么都不发生** ✓（不再开会话、不再喂点 ✓，也不弹提示 ✗ ——
                        //    界面上根本切不到那些工具了 ✓，见 `ComicPaintToolbar` 已随本批删除 ✓）。
                        //    ⇒ 这一页画布现在只认**选择 / 套索**（动格子 ✓）✓。
                        if (tool.isBrushTool()) return@awaitEachGesture
                        // ⑰ **没有会话**（这一页一个能画的层都没有 ✗，见 `autoFocusComicPaintLayer` ✓）
                        //    ⚠️ 早退本身照旧 —— 画布上"拖 / 点 = 改内容"的老路仍由 `comicPaintSelecting` 把关 ✓
                        val session = state.comicPaintSession
                        if (session == null) return@awaitEachGesture
                        val selectionBound = state.comicPaintSelection
                            ?.let { SelectionGeometry.bounds(it, session.width, session.height) }

                        // ⑭ **「选择 / 套索」= 唯一的"动东西"入口** ✓：按下时先做一次**对象命中** ✓
                        //（用户 2026-09-20：「**画布逻辑和 ps 一样啊,在画布上的东西不能乱动,
                        //  · 命中**格子 / 气泡 / 文本** → 选中的是**那个对象**（`ui.selectedId` /
                        //    `selectedPanelId` ✓，见 `ComicModeUiState.beginComicSelectAt` ✓），
                        // ⚠️ **飘着的那块像素优先** ✗：已经抬起来一块时，这一下按的是"接着处理那块"
                        //    （移动 / 缩放 / 落回 ✓）—— 对象命中让路，不让"刚抬起来就被对象抢走"✗
                        // ⚠️ 命中的是 `state.hitComicObject`（**现读当前那一份** ✓）——
                        //    不是这个协程建立时捕获的 `page`（新加的格子它看不见 ✗，本文件那条老教训 ✓）
if (start != null &&
                            (tool == CanvasTool.SELECT || tool == CanvasTool.LASSO) &&
                            state.comicPaintFloating == null &&
                            overlayUi.beginComicSelectAt(state, start.x, start.y)
                        ) {
                            return@awaitEachGesture
                        }

                        var drag = ComicPaintDrag.NONE
                        var lasso: List<MaskPoint> = emptyList()
                        var scaleAnchor = Offset.Zero
                        var lastLocal = Offset.Zero
                        var prevPoint = start ?: Offset.Zero

                        if (start != null) {
                            val px = start.x.toInt()
                            val py = start.y.toInt()
                            drag = when {
                                // ---- ① 选区类工具（**只有这两个**才动选区 ✓）----
                                tool == CanvasTool.SELECT || tool == CanvasTool.LASSO -> {
                                    // 命中判断用的框：已经抬起来时看**浮动块自己的包围盒**（它可能正被拖在别处 ✓），
                                    val floating = state.comicPaintFloating
                                    val hitBound = floating?.bounds() ?: selectionBound
                                    val handle = if (floating != null && hitBound != null) {
                                        comicSelectionHandleAt(
                                            point = start,
                                            bounds = hitBound,
                                            // 控制点的命中半径跟着**屏幕**走（16dp ✓）——
                                            // 换算成底板像素 = 16dp 的像素数 / 纸上一个像素占几个屏幕像素 ✓
                                            radius = comicSelectionHandleRadiusPx(
                                                paper = paperPx,
                                                pageWidth = session.width,
                                                zoom = overlayUi.canvasScale,
                                                density = gestureDensity.density,
                                            ),
                                        )
                                    } else {
                                        null
                                    }
                                    when {
                                        handle != null && hitBound != null -> {
                                            // 拖控制点 = 缩放选区里的内容（锚点 = 对角那个点 ✓）
                                            if (state.liftComicPaintSelection()) {
                                                scaleAnchor = comicSelectionCorners(hitBound)[3 - handle]
                                                val current = state.comicPaintFloating
                                                lastLocal = if (current != null) {
                                                    val local = current.toLocal(start.x, start.y)
                                                    Offset(local.first, local.second)
                                                } else {
                                                    start
                                                }
                                                ComicPaintDrag.SCALE
                                            } else {
                                                ComicPaintDrag.NONE
                                            }
                                        }

                                        hitBound != null && comicInside(hitBound, start) -> {
                                            // 框内按下 = 移动那块内容 ✓（还没抬起来就先抬起来 ✓）
                                            if (floating == null) state.liftComicPaintSelection()
                                            ComicPaintDrag.MOVE
                                        }

                                        else -> {
                                            // 框外：`updateComicPaintSelection` 会**先把飘着的那块落回** ✓，
                                            if (tool == CanvasTool.SELECT) {
                                                state.updateComicPaintSelection(
                                                    SelectionOps.rectShape(
                                                        start.x, start.y, start.x, start.y,
                                                        session.width, session.height,
                                                    ),
                                                )
                                                ComicPaintDrag.MARQUEE
                                            } else {
                                                lasso = listOf(
                                                    MaskPoint(
                                                        (start.x / session.width).coerceIn(0f, 1f),
                                                        (start.y / session.height).coerceIn(0f, 1f),
                                                    ),
                                                )
                                                state.updateComicPaintSelection(SelectionShape.Polygon(lasso))
                                                ComicPaintDrag.LASSO
                                            }
                                        }
                                    }
                                }

                                // ---- ② 点一下就完事的：油漆桶 / 吸管 ----
                                tool == CanvasTool.FILL -> {
                                    state.fillComicPaintAt(px, py)
                                    ComicPaintDrag.NONE
                                }

                                tool == CanvasTool.PICKER -> {
                                    state.pickComicPaintColor(px, py)
                                    ComicPaintDrag.NONE
                                }

                                // ---- ③ 其余画笔类：画笔 / 橡皮 / 模糊 = 落笔 ✓ ----
                                else -> {
                                    // ⚠️ 后四个参数**只喂 `[PenDab] begin` 那条日志** ✓（口径与
                                    //    笔那条路逐字一样 ✓，见 `AppState.beginComicPaintStroke` ✓）
                                    //    `down.position` 就是这个手势节点的**局部像素** ✓ ——
                                    //    和 `paperPx` / `comicScreenToPagePixel` 同一个坐标系 ✓
                                    //（那条链子的账见 `comicScreenToPagePixel` 的说明 ✓）
                                    // ⓪″ **笔此刻正按着 ⇒ 指针这条不开笔** ✓（第 ㉟② 追加 ✓）。
                                    //
                                    // ## 治的是什么（用户 2026-09-21 实机报的「起点出现一个大点」✗）
                                    //  Windows 会把同一支笔**再提升成一遍鼠标事件** ✓ ⇒ 顺序常常是
                                    //  "**鼠标 down 先到、笔 Press 后到**"✓。上面那一句
                                    //  `// 第 ㊽ 批：笔那条通道已删 ⇒ 这道闸不再需要（见上）`（`:1953` ✓）
                                    //  **只在这一条手势开始的那一刻判** ✗ —— 而那一刻笔的 Press
                                    //  还没进来 ⇒ `penGate.down` 仍是 `false` ✗ ⇒ 指针这条**照旧开笔** ✓、
                                    //  落的还是**满压（压力恒 1 ✗）的一颗基准半径笔尖** ✗。
                                    //  之后笔的 Press 到了、走"收掉 + 撤回 + 按真压感重开"✓（`:1794` ✓）——
                                    //  **撤回是干净的** ✓（实测残留 0 像素 ✓，见 `PenMouseHandoffDotProbeTest` ✓），
                                    //  但"先落一颗满压的、再撤掉"这个来回会让屏幕上**闪一下那个大点** ✗。
                                    //
                                    // ⇒ 这里加一道闸 ✓：**笔正按着**时就**压根不开笔** ✓ ——
                                    //  大点从头到尾不会出现 ✓（不是"画了再擦"✗）。
                                    //
                                    // ⚠️ **局限（如实写在这里，不粉饰 ✗）**：它只挡得住
                                    //  "**笔已经在按着**"的那些帧 ✓ —— 若"鼠标 down 真比笔 Press 早"
                                    //  到"指针这条已经开完笔了"，那**管不了** ✗（那一瞬该闪还是会闪 ✓）。
                                    //  想彻底消掉得让指针这条"先问一句这一下是不是笔 / 延迟一颗笔尖"✗ ——
                                    //  那是**结构改动** ✓，与用户"**保持现状（D ✓）**"的口径冲突 ✗
                                    //  ⇒ **故意不做** ✓（取舍记在 `docs/62` ✓）。
                                    //
                                    // ⚠️ 判据**只有"笔此刻正按着"** ✓（`PenPointerGate.down` ✓）——
                                    //  不是"最近收到过笔事件"✗：数位板搁着、笔尖悬空不动时也会一直报 Hover ✓，
                                    //  拿它当判据会让"笔在旁边悬着 + 用鼠标点"变成点了没反应 ✗。
                                    //  这也正是 `PenPointerGate` 的注释里那条口径 ✓（`:1951` ✓），一个字没改 ✓。
                                    run {
                                        state.beginComicPaintStroke(
                                            x = start.x,
                                            y = start.y,
                                            screenX = down.position.x,
                                            screenY = down.position.y,
                                            zoom = overlayUi.canvasScale,
                                            dpr = gestureDensity.density,
                                        )
                                        ComicPaintDrag.STROKE
                                    }
                                }
                            }
                        }

                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                // ⚠️ ㉝① 回归修复 ✓：**笔中途接管了这一笔** ⇒ 鼠标这条立刻停 ✗。
                                //    这条判断原来只在手势**开始**那一刻做过（:1932 ✓，"笔此刻正按着"✓）——
                                //    而"鼠标那条手势先开、笔再按下去"这一种顺序下（Windows 把同一支笔
                                //    先提升成鼠标事件 ✓，本仓库早就知道这条 ✓），鼠标那条会**在整笔里
                                //    继续喂压力恒 1 的点** ✗ ⇒ 用户报的「数位板画没有压感」✓。
                                //    笔那一条已经在笔回调里 `endComicPaintStroke() + undoComicPaint()`
                                //    把这一笔收掉并按真压感重开了 ✓，这里只需要**别再喂** ✓。

                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                val point = comicScreenToPagePixel(
                                    screen = change.position,
                                    paper = paperPx,
                                    pageWidth = page.baseWidth,
                                    pageHeight = page.baseHeight,
                                    zoom = overlayUi.canvasScale,
                                    pan = overlayUi.canvasOffset,
                                    rotationDegrees = overlayUi.canvasRotation,
                                )
                                when (drag) {
                                    ComicPaintDrag.STROKE -> {
                                        // 落在纸外的那几帧跳过（笔画不断 ✓，回到纸里接着画 ✓）
                                        val target = point ?: continue
                                        // ㉝ ①：**只攒点**（鼠标 / 触摸这条与数位板那条同一套 ✓）——
                                        // 帧边界一次按到达顺序喂完 ✓，绝不只喂最后一个 ✗。
                                        state.offerComicPaintStrokePoint(target.x, target.y)
                                        prevPoint = target
                                        change.consume()
                                    }

                                    ComicPaintDrag.MARQUEE -> {
                                        val target = point ?: continue
                                        state.updateComicPaintSelection(
                                            SelectionOps.rectShape(
                                                start?.x ?: target.x, start?.y ?: target.y,
                                                target.x, target.y,
                                                session.width, session.height,
                                            ),
                                        )
                                        change.consume()
                                    }

                                    ComicPaintDrag.LASSO -> {
                                        val target = point ?: continue
                                        lasso = lasso + MaskPoint(
                                            (target.x / session.width).coerceIn(0f, 1f),
                                            (target.y / session.height).coerceIn(0f, 1f),
                                        )
                                        state.updateComicPaintSelection(SelectionShape.Polygon(lasso))
                                        change.consume()
                                    }

                                    ComicPaintDrag.MOVE -> {
                                        val target = point ?: continue
                                        state.translateComicPaintSelection(
                                            target.x - prevPoint.x,
                                            target.y - prevPoint.y,
                                        )
                                        prevPoint = target
                                        change.consume()
                                    }

                                    ComicPaintDrag.SCALE -> {
                                        val target = point ?: continue
                                        val current = state.comicPaintFloating ?: continue
                                        val (localX, localY) = current.toLocal(target.x, target.y)
                                        val (anchorLocalX, anchorLocalY) =
                                            current.toLocal(scaleAnchor.x, scaleAnchor.y)
                                        val prevX = lastLocal.x
                                        val prevY = lastLocal.y
                                        // 和画布编辑器逐字同一条：分母太小时不缩放（否则会翻面 / 炸开 ✗）
                                        val factorX = if (abs(prevX - anchorLocalX) < 1e-3f) {
                                            1f
                                        } else {
                                            (localX - anchorLocalX) / (prevX - anchorLocalX)
                                        }
                                        val factorY = if (abs(prevY - anchorLocalY) < 1e-3f) {
                                            1f
                                        } else {
                                            (localY - anchorLocalY) / (prevY - anchorLocalY)
                                        }
                                        state.scaleComicPaintSelection(
                                            factorX, factorY, scaleAnchor.x, scaleAnchor.y,
                                        )
                                        lastLocal = Offset(localX, localY)
                                        change.consume()
                                    }

                                    ComicPaintDrag.NONE -> Unit
                                }
                            }
                        } finally {
                            if (drag == ComicPaintDrag.STROKE) state.endComicPaintStroke()
                            if (drag == ComicPaintDrag.MARQUEE) {
                                // 只拖出一个小到看不见的框 → 不算数（和画布编辑器一个口径 ✓）
                                val shape = state.comicPaintSelection
                                if (shape is SelectionShape.Rect) {
                                    val wide = abs(shape.right - shape.left) * session.width
                                    val high = abs(shape.bottom - shape.top) * session.height
                                    if (wide < 3f || high < 3f) state.updateComicPaintSelection(null)
                                }
                            }
                            // 选区里的浮动块**留在原地** ✓（等换工具 / 点框外 / 收工再落回 ✓，
                            // 和画布编辑器一个手感 ✓）
                        }
                    }
                },
        ) {
            // 等比缩放到可用区域（宽高都放不下时按高度算 —— 和图片 Fit 一个意思）
            val availW = if (maxWidth.value.isFinite()) maxWidth else 600.dp
            val availH = if (maxHeight.value.isFinite()) maxHeight else 600.dp
            val aspect = page.baseWidth.toFloat() / page.baseHeight.toFloat()
            var drawW: Dp = availW
            var drawH: Dp = availW / aspect
            if (drawH > availH) {
                drawH = availH
                drawW = availH * aspect
            }
            val scale = drawW.value / page.baseWidth
            val density = LocalDensity.current

            // 手势里的 px → 底板像素
            val toPage: (Offset) -> Offset = { local ->
                Offset(
                    (with(density) { local.x.toDp().value }) / scale,
                    (with(density) { local.y.toDp().value }) / scale,
                )
            }

            // 手拖新建：起点 / 当前矩形（都是底板像素）
            var dragStart by remember(page.id) { mutableStateOf<Offset?>(null) }
            var dragRect by remember(page.id) { mutableStateOf<Rect?>(null) }

            // ⚠️ `pointerInput` 的 key 里**没有**格子列表（那会每动一下就重建手势 ✗）
            // 所以块内直接读 `page` 会读到**建立那一刻的旧值** —— 新拖出来的格子在下一轮
            // 重建之前"在守卫眼里不存在"。用 `rememberUpdatedState` 拿最新的一份 ✓
val latestPage by rememberUpdatedState(page)

            Box(
                Modifier
                    .size(drawW, drawH)
                    // 量一下纸的像素尺寸（滚轮缩放的锚点 = 纸中心 ✓，见上面那几行说明 ✓）
                    .onSizeChanged { paperPx = it }
                    // ---- ② 缩放 + 平移：**只作用在这一层**（纸底 / 格子 / 气泡 / 文本都在里面 ✓）----
                    //
                    // ⚠️ 位置讲究：`graphicsLayer` 要排在 `background` / `border` **前面**
                    //（修饰符链里靠前的包住靠后的 ✓）—— 排在后面的话纸底和边框会**不被缩放**，
                    // 于是"格子放大了、纸还是原来那么大" ✗
                    // ⚠️ **不改布局尺寸**（`drawW/drawH` 一个字节没变 ✓），只改绘制与命中测试的换算 ——
                    // 所以纸里面 `toPage` / `scale` 那套一个字都不用改 ✓，格子 / 气泡 / 文本的
                    // 命中测试**自动**跟着变换走 ✓
                    .graphicsLayer {
                        // ⛔ **第 ㊻ 批（2026-09-21）修**：用户报「**旋转画面的旋转点在画布的中点**」✓
                        //
                        // 上一版靠"`transformOrigin` **默认** = 这一层的中心 = 纸中心"✗ ——
                        // 那个假设**不成立** ✗：`graphicsLayer` 的默认原点是**这一层自己的
                        // 布局尺寸中心** ✓，而这一层的尺寸是 `drawW × drawH` ✓ ——
                        // 它在**父容器里居中** ✓，但父容器（画布区）**不是方的** ✓
                        // ⇒ 纸中心与父容器中心**一般不重合** ✓ ⇒ 转起来是绕"别处"转 ✓。
                        //
                        // **修法**：把原点**显式钉在纸的中心** ✓（= 这一层自己宽高的一半 ✓，
                        // 单位是**层内像素** ✓）。这样旋转 / 缩放 / 平移**同一个锚点** ✓，
                        // 而且与 `comicScreenToPagePixel` 里那个 `centerX/centerY`（也用层尺寸算 ✓）
                        // **逐字一致** ✓ —— 两处必须同源 ✗，不然"转完之后画不准" ✓。
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                            pivotFractionX = 0.5f,
                            pivotFractionY = 0.5f,
                        )
                        scaleX = overlayUi.canvasScale
                        scaleY = overlayUi.canvasScale
                        translationX = overlayUi.canvasOffset.x
                        translationY = overlayUi.canvasOffset.y
                        rotationZ = overlayUi.canvasRotation
                    }
                    .background(ComicPaper)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    // ① 点一下：落在气泡 / 文本 / 格子上就交给它们自己（它们的手势在前面）；
                    .pointerInput(page.id, page.baseWidth, page.baseHeight, scale) {
                        detectTapGestures { position ->
                            val point = toPage(position)
                            // ⑬ ⑰ 画布编辑器里**手里是画笔类工具** → 这一下是画画，
                            // ⚠️ ⑰ 判据**只看工具**（`comicPaintSelecting` ✓）——**不再看"有没有会话"** ✗：
                            //    "选了工具就是编辑"✓，工具一直在手里 ✓，不需要先点一下图层 ✓
                            //（用户 2026-09-20：「画布编辑器不要用完成编辑按钮,选了工具就是编辑」
                            //  「不需要点图层确定状态」·「选图层只是确定修改的层」·「交互逻辑按照 ps 来」✓）
                            // ⚠️ 因此**选着「选择 / 套索」时这一条放行** ✓ —— 那两把工具下"动东西"是该做的 ✓
                            //（对象自己还是"必须先选中才拖得动"那一条 ✓，那条老规则没动 ✓）
                            // ⚠️ **第 ㊼ 批**：气泡 / 文本工具已删 ✓ ⇒ 这一条只剩「选择 / 套索」与「格子」✓。
                            val overlayToolTakesCanvas = state.comicPaintSelecting ||
                                overlayUi.tool == ComicCanvasTool.PANEL
                            if (!overlayToolTakesCanvas) return@detectTapGestures
                            if (overlayUi.canvasPanGesture) return@detectTapGestures
                            if (overlayUi.handleDragging) return@detectTapGestures
                            // ⚠️ 第 ㊼ 批：`overTailHandle` / `hitsOverlay` 随气泡 / 文本一起删了 ✓
                            //（那两个判据量的就是气泡与文本的命中 ✓，现在没有那两种对象 ✓）。
                            when (overlayUi.tool) {
                                ComicCanvasTool.PANEL -> {
                                    val hit = latestPage.panels.any { panel ->
                                        point.x >= panel.x && point.x <= panel.right &&
                                            point.y >= panel.y && point.y <= panel.bottom
                                    }
                                    if (!hit) {
                                        onSelect(null)
                                        overlayUi.selectedId = null
                                    }
                                }

                                else -> Unit
                            }
                        }
                    }
                    .pointerInput(page.id, page.baseWidth, page.baseHeight, scale) {
                        detectDragGestures(
                            onDragStart = { position ->
                                // ⚠️ ⑰ **手里是画笔类工具** ⇒ **不在空白纸上拖新格子 / 新气泡 / 新文本** ✓
                                //（这一下是画画 ✓；⑮ 的互斥：见 ⑮ 批那条"平移"旗子的同款写法 ✓）
                                // ⚠️ ⑰ 判据**只看工具** ✓（不再看"有没有会话" ✗ —— 见上面 tap 那一条 ✓）
                                // ⚠️ 选着「选择 / 套索」时放行 ✓（那两把工具下才允许建 / 动东西 ✓，
                                val overlayToolTakesCanvas = state.comicPaintSelecting || overlayUi.tool == ComicCanvasTool.PANEL || overlayUi.tool == ComicCanvasTool.BUBBLE || overlayUi.tool == ComicCanvasTool.TEXT
                                if (!overlayToolTakesCanvas) {
                                    dragStart = null
                                    dragRect = null
                                    return@detectDragGestures
                                }
                                // ⓪ 这一下是平移（按下时按着空格 / 中键）→ 不拖新格子 ✓
                                //（⚠️ **左键在空白纸上的拖动照旧是"拖出一个新格子"** ✓ ——
                                // 只有上面这个旗子立着的时候才让开 ✓）
                                if (overlayUi.canvasPanGesture) {
                                    dragStart = null
                                    dragRect = null
                                    return@detectDragGestures
                                }
                                if (overlayUi.handleDragging) {
                                    overlayUi.handleDragging = false
                                    dragStart = null
                                    dragRect = null
                                    return@detectDragGestures
                                }
                                // ⑮ 用户 2026-09-20：「**格子需要点击生成新格后才能在画布上进行拉新格子**」✓ ——
                                // 连预览框都不画 —— `dragStart` / `dragRect` 都清掉 ✓
                                // ⚠️ **只挡「格子」这一种** ✗：气泡 / 文本那两种工具各有自己的入口，
                                //    它们"拖出一个框"是既定的 ✓（连坐的话就把那两件事弄坏了 ✗）
                                // ⚠️ 现读 `overlayUi.newPanelMode`（快照状态）—— 这个指针协程不会因为
                                if (overlayUi.tool == ComicCanvasTool.PANEL && !overlayUi.newPanelMode) {
                                    dragStart = null
                                    dragRect = null
                                    return@detectDragGestures
                                }
                                // 落在已有格子 / 气泡 / 文本上就不画新的（那是拖 / 缩它们的那个手势）
                                val point = toPage(position)
                                val hitPanel = latestPage.panels.any { panel ->
                                    point.x >= panel.x && point.x <= panel.right &&
                                        point.y >= panel.y && point.y <= panel.bottom
                                }
                                val hit = hitPanel || hitsOverlay(latestPage, point)
                                dragStart = if (hit) null else point
                                dragRect = null
                            },
                            onDragEnd = {
                                val rect = dragRect
                                val tool = overlayUi.tool
                                dragStart = null
                                dragRect = null
                                if (rect != null) {
                                    when (tool) {
                                        ComicCanvasTool.BUBBLE -> {
                                            state.addComicBubble(
                                                style = overlayUi.bubbleStyle.name,
                                                x = rect.left,
                                                y = rect.top,
                                                w = rect.width,
                                                h = rect.height,
                                            )?.let { created -> overlayUi.selectedId = created }
                                        }

                                        ComicCanvasTool.TEXT -> {
                                            state.addComicText(
                                                x = rect.left,
                                                y = rect.top,
                                                w = rect.width,
                                                h = rect.height,
                                            )?.let { created ->
                                                overlayUi.selectedId = created
                                                overlayUi.editingId = created
                                            }
                                        }

                                        ComicCanvasTool.PANEL -> {
                                            val created = state.addComicPanel(
                                                x = rect.left,
                                                y = rect.top,
                                                w = rect.width,
                                                h = rect.height,
                                            )
                                            if (created != null) {
                                                onSelect(created)
                                                //（用户："点一下生成新格、再在画布上拉一个" ✓ —— 拉完就收 ✓，
                                                overlayUi.newPanelMode = false
                                            }
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                dragStart = null
                                dragRect = null
                            },
                        ) { change, amount ->
                            change.consume()
                            val start = dragStart ?: return@detectDragGestures
                            val current = toPage(change.position)
                            dragRect = Rect(
                                left = minOf(start.x, current.x),
                                top = minOf(start.y, current.y),
                                right = maxOf(start.x, current.x),
                                bottom = maxOf(start.y, current.y),
                            )
                            // 顺带量一下：太小的拖动不用提示，松手时自然建不出来（有最小边长兜底）
                            // ⚠️ `amount` 是这次拖动的增量，用不上 ✓ —— 直接忽略参数即可，
                        }
                    },
            ) {
                // ---- 位图层（底板 / **生成层** / 绘画层）：**按图层叠从下到上**画 ✓ ----
                //
                // ⚠️ 这层**不再只画 `page.basePath`** ✗：用户口径是「选定图层或新建图层**绘画**」✓ ——
                //    PAINT 层得看得见（不然"画完、收工、画的东西凭空消失" ✗）
                // ⚠️ **生成层也归这里画** ✓（用户 2026-09-20：「格子在生成后还在，并且有两个图像」✗）：
                //    以前 `GENERATED` 那张图是**格子框**（`ComicPanelBox`）自己贴的 ✗，而它同时也
                //    在图层叠里 —— 于是"生成层"被**叠画两遍**（`maxDimension = 1024` + `Crop` 那份糊 ✗）
                //    ⚠️ 落点几何**只有一处** ✓：`ComicPageExporter.generatedPlacement`（和拼页导出 /
                //    ⚠️ 落点几何**只有一处** ✓：`ComicPageExporter.generatedPlacement`（和拼页导出 /
                //    PSD / 生成层 materialize 同一个来源 ✓）—— 这里只是把那个矩形按 `scale` 摆出来 ✓
                // ⚠️ **正在画的那一层画的是"会话里那一张层表面"** ✓（第 ㉜a 批 ✓）：
                //    显示的是**视口分辨率** —— 每帧只把"可见矩形"从层表面读出来、缩到视口大小再贴回
                //    （见下面的 [ComicPaintViewportLayer] 与 `AppState.comicPaintViewportFrame` ✓）
                //    **一笔之内不用重建任何整页位图** ✓（"活笔画小块叠加"整条链删掉了 ✓）
                //    橡皮**不需要 `DstOut` 离屏层** ✓（层表面里的 alpha 本来就已经被真的减掉了 ✓）
                //    —— 所以正在编辑时**不画**盘上那张 `FileImage` ✗：叠上去的话，
                //    橡皮擦出来的透明区会被底下的旧图透出来，"擦不掉" ✗
                //（生成层被画过之后 kind 会转成 PAINT ✓，同一个 materialize 出来的就是页大小像素 ✓）。
                // ⛔ **第 ㊻ 批（2026-09-21）修**：用户报「**删除图层时还是只有和画布交互画布才出现画面**」✓
                //
                // **根因**：下面这个 `forEach` 既**没有 `key()`** ✗、这一块也**没订阅任何"图层变了"的状态** ✗
                //   ⇒ 删掉一层之后 Compose 按**位置**复用子节点 ✓，但那些 `FileImage` 的缓存是按
                //   `path` 记的 ✓ ⇒ 照旧显示旧组合 ✓ ⇒ 要点一下（触发别处重组）才对上 ✓。
                //   ⚠️ 上一批推的 `comicPaintFrameTick` **救不了它** ✗（那个 tick 只被**视口那一层**读 ✓）。
                //
                // **修法**：这里**读一次**图层表版本号 ✓ ⇒ 这一块订阅它 ✓（图层增删 / 换序都会 +1 ✓）；
                //   下面每个子节点再包 `key(layer.id)` ✓ ⇒ 身份跟着图层走 ✓，位置复用不会串 ✓。
                @Suppress("UNUSED_EXPRESSION")
                state.comicBoardRevision
val activePaintLayerId = state.comicPaintLayerId
                val imageSizeOf: (String) -> Pair<Int, Int>? = { path ->
                    runCatching { state.images.size(path) }.getOrNull()
                }
                page.layers.forEach { layer ->
                    val bitmapLayer = layer.kind == ComicLayer.Kind.BASE ||
                        layer.kind == ComicLayer.Kind.GENERATED ||
                        layer.kind == ComicLayer.Kind.PAINT
                    if (!bitmapLayer) return@forEach
                    if (!layer.visible || layer.opacity <= 0f) return@forEach
                    val layerOpacity = layer.opacity
                    // ⚠️ 用户 2026-09-22：「**拖动的是格子，不是图像，要一起拖动**」✓ ——
                    //    绑在这一格上的**绘画层**像素是"钉在页上"的 ✗，拖动期间这里按
                    //    "格子当前坐标 − 起始坐标"给它一个**临时偏移** ✓（不碰像素 ✓ 零成本 ✓）；
                    //    松手时 `AppState.shiftComicPanelLayers` 把这份位移真的写进像素 ✓。
                    //    ⚠️⚠️ 这一段**必须排在"正在编辑的那一层"分支之前** ✗✗ ——
                    //    聚焦那一层走的是 `ComicPaintViewportLayer`（读会话表面 ✓），**根本不走**
                    //    下面的 `FileImage` ✗：放下面 = **用户正在动的那一层永远不跟** ✓
                    //（2026-09-22 第一次就是这么写的，用户实测报回来的正是"拖动时不跟随" ✓）。
                    val dragPanelId = overlayUi.panelDragId
                    // ⚠️⚠️ **不能用 `kind` 一刀切** ✗：生成层分两种画法 ——
                    //   · **没聚焦**的生成层：画的是 `generatedPlacement` 的落点矩形 ✓
                    //     ⇒ 它**本来就跟着格子走** ✓，这里再加偏移就是**走两遍** ✗
                    //     （用户 2026-09-22 报「**图像和框对不上**」正是这个 ✓：图比框多走一倍 ✓）；
                    //   · **聚焦**的生成层：画的是会话里那层**烘焙过的页大小像素** ✓ ⇒ 它**不跟** ✗ ⇒ 要偏移 ✓。
                    val followsByGeometry = layer.kind == ComicLayer.Kind.GENERATED &&
                        layer.id != activePaintLayerId
                    val dragLayerShift = if (
                        dragPanelId != null &&
                        layer.panelId == dragPanelId &&
                        !followsByGeometry
                    ) {
                        val dragging = page.panels.firstOrNull { it.id == dragPanelId }
                        if (dragging == null) {
                            null
                        } else {
                            Offset(
                                dragging.x - overlayUi.panelDragStartX,
                                dragging.y - overlayUi.panelDragStartY,
                            )
                        }
                    } else {
                        null
                    }
                    if (layer.id == activePaintLayerId) {
                        // ⚠️ 这一层**必须**画出来（哪怕是空的 ✓）—— 用户口径：「打开就有画布、能画」✓
                        ComicPaintViewportLayer(
                            state = state,
                            paper = paperPx,
                            area = canvasAreaPx,
                            bleedX = ((canvasOutlinePx.width - canvasAreaPx.width) / 2f).coerceAtLeast(0f),
                            bleedY = ((canvasOutlinePx.height - canvasAreaPx.height) / 2f).coerceAtLeast(0f),
                            zoom = overlayUi.canvasScale,
                            pan = overlayUi.canvasOffset,
                            opacity = layerOpacity,
                            // 拖动那一格时，正在编辑的这层也要**跟着走** ✓（见上面那段说明 ✓）
                            dragOffsetDp = Offset(
                                (dragLayerShift?.x ?: 0f) * scale,
                                (dragLayerShift?.y ?: 0f) * scale,
                            ),
                        )
                        return@forEach
                    }
                    if (layer.kind == ComicLayer.Kind.GENERATED) {
                        // 生成层：**用自己那个落点矩形**在页坐标里定位 ✓（不是 `fillMaxSize` ✗）——
                        // 矩形是**底板像素**，界面这一层是 dp，所以乘 `scale`（dp / 底板像素 ✓）
                        // ⚠️ 修饰符顺序：`offset{}` / `size()` 在前、`alpha` 在后 ✓ —— 这里没有手势，
                        //    但仍然照这仓库的老规矩排 ✓
                        // ⚠️ **读文件头这一步不每帧做** ✗（`images.size` 要开文件 ✓）—— 只在
                        val panel = layer.panelId?.let { id -> page.panels.firstOrNull { it.id == id } }
                        val step = if (panel == null) {
                            null
                        } else {
                            remember(layer.id, layer.imagePath, panel.x, panel.y, panel.w, panel.h) {
                                ComicPageExporter.generatedPlacement(page, layer, imageSizeOf)
                            }
                        }
                        step ?: return@forEach
                        FileImage(
                            path = step.imagePath,
                            maxDimension = 2048,
                            modifier = Modifier
                                .offset(x = (step.x * scale).dp, y = (step.y * scale).dp)
                                .size(
                                    (step.width * scale).dp.coerceAtLeast(1.dp),
                                    (step.height * scale).dp.coerceAtLeast(1.dp),
                                )
                                .alpha(layerOpacity),
                            // 落点矩形已经是"等比 cover 进那一格"的结果（长宽比 = 原图 ✓）
                            // 所以 FillBounds 就是等比的那一套（几何仍然只有 `generatedPlacement` 一处 ✓）
                            contentScale = ContentScale.FillBounds,
                        )
                        return@forEach
                    }
                    val fallback = if (layer.kind == ComicLayer.Kind.BASE) page.basePath else null
                    val path = layer.imagePath ?: fallback ?: return@forEach
                    // **底板这一层 = 共用画布本身**（用户 2026-09-24：「切换时**只改 ui，画布不变**」✓）：
                    // 尺寸对得上（画布和这一页一样大 ✓ —— `新建底板` 就是按画布尺寸建页的 ✓）时，
                    // **直接画画布那块位图** ✓ 不落文件、不复制 ✓；对不上（老底板 / 画布后来长大了 ✓）
                    // 就照旧画文件 ✓ —— 宁可画一张小一号的旧底，也不要把画布硬塞进一个不匹配的页 ✓。
                    val canvasBitmap = if (layer.kind == ComicLayer.Kind.BASE) {
                        state.infiniteCanvas?.takeIf {
                            it.width == page.baseWidth && it.height == page.baseHeight
                        }?.let { state.infiniteCanvasImage() }
                    } else {
                        null
                    }
                    if (canvasBitmap != null) {
                        Image(
                            bitmap = canvasBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .offset(
                                    x = ((dragLayerShift?.x ?: 0f) * scale).dp,
                                    y = ((dragLayerShift?.y ?: 0f) * scale).dp,
                                )
                                .fillMaxSize()
                                .alpha(layerOpacity),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        FileImage(
                            path = path,
                            // ⚠️ 拖动期间**不再换低清代理** ✗（用户 2026-09-22：「**取消低清拖动**，其他保持」✓）——
                            //    之前试过"拖动时贴 512 px 的小图、松手恢复" ✓，用户否掉了 ✓；
                            //    跟随只靠**上面那个偏移** ✓，这条路的解码口径一个字没改 ✓。
                            maxDimension = 2048,
                            modifier = Modifier
                                .offset(
                                    x = ((dragLayerShift?.x ?: 0f) * scale).dp,
                                    y = ((dragLayerShift?.y ?: 0f) * scale).dp,
                                )
                                .fillMaxSize()
                                .alpha(layerOpacity),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                // ---- ㊿l：**格子自己的图**（格子与图层合一 ✓，用户 2026-09-22 ✓，方案 `docs/70` ✓）----
                // 每格按**自己的框**摆自己的图 ✓：`cover` 进框（等比放大到铺满、居中裁 ✓）——
                // 和旧口径 `ComicPageExporter.generatedPlacement` 用的是同一套几何 ✓，
                // 只是现在**图挂在格子上**（不再另起一个"生成层"✗）。
                // ⚠️ 画在**位图层之后、格子框之前** ✓（框和把手要盖在图上 ✓）。
                page.orderedPanels().forEach { panel ->
                    val panelImage = panel.imagePath ?: return@forEach
                    if (!panel.visible || panel.opacity <= 0f) return@forEach
                    val size = imageSizeOf(panelImage) ?: return@forEach
                    val imgAspect = size.first.toFloat() / size.second.toFloat().coerceAtLeast(1f)
                    val boxAspect = panel.w / panel.h.coerceAtLeast(1f)
                    // cover：图比框更宽 ⇒ 高度对齐框、宽度溢出；否则反过来 ✓
                    val drawW = if (imgAspect > boxAspect) panel.h * imgAspect else panel.w
                    val drawH = if (imgAspect > boxAspect) panel.h else panel.w / imgAspect
                    val left = panel.x - (drawW - panel.w) / 2f
                    val top = panel.y - (drawH - panel.h) / 2f
                    FileImage(
                        path = panelImage,
                        maxDimension = 2048,
                        modifier = Modifier
                            .offset(x = (left * scale).dp, y = (top * scale).dp)
                            .size(
                                (drawW * scale).dp.coerceAtLeast(1.dp),
                                (drawH * scale).dp.coerceAtLeast(1.dp),
                            )
                            .alpha(panel.opacity),
                        contentScale = ContentScale.FillBounds,
                    )
                }

                // ---- 格子 ----
                page.orderedPanels().forEach { panel ->
                    ComicPanelBox(
                        state = state,
                        page = page,
                        panel = panel,
                        scale = scale,
                        selected = panel.id == selectedPanelId,
                        onSelect = { onSelect(panel.id) },
                        // ⓪ 这一下按着空格 / 中键 = 平移画布 ⇒ 格子自己的手势整段让开 ✓
                        // ⚠️ 传的是**取值 lambda** 而不是一个 Boolean：`pointerInput` 的 key 没变时
                        //    协程**不会重建**，里面捕获的普通 Boolean 会一直停在"建那一刻"的旧值 ✗
                        //    （本文件头那条"块内直接读 page 会读到旧值"是同一条规矩 ✓）
                        //    lambda 捕获的是**同一个状态对象**，每次现读都是最新的 ✓
                        panGesture = { overlayUi.canvasPanGesture },
                        // ⚠️ 拖动开始时记下"哪一格 + 它现在在哪" ✓；松手时按**最终坐标 − 起始坐标**
                        //    把绑在它上面的绘画层真的挪一次像素 ✓（用户 2026-09-22：
                        //    「拖动的是格子，不是图像，要一起拖动」✓）。
                        //    ⚠️ 终点坐标要**现读** `state.comicBoardPage` ✗（这个 lambda 捕获的 `page`
                        //    是组合那一帧的快照 ✓，拖动过程中它是旧的 ✗）。
                        onMoveStart = {
                            overlayUi.panelDragId = panel.id
                            overlayUi.panelDragStartX = panel.x
                            overlayUi.panelDragStartY = panel.y
                        },
                        onMoveEnd = {
                            val draggingId = overlayUi.panelDragId
                            val startX = overlayUi.panelDragStartX
                            val startY = overlayUi.panelDragStartY
                            overlayUi.panelDragId = null
                            if (draggingId == panel.id) {
                                val now = state.comicBoardPage?.panels?.firstOrNull { it.id == draggingId }
                                if (now != null) {
                                    val dx = now.x - startX
                                    val dy = now.y - startY
                                    if (dx != 0f || dy != 0f) {
                                        // ⛔ **用户 2026-09-22 报的「松手还会偏移位置」就是缺了这一步** ✓：
                                        //    正在编辑的那一层画的是**会话里的像素** ✓，
                                        //    而 `shiftComicPanelLayers` 挪的是**盘上那个文件** ✗ ——
                                        //    会话不落盘就挪不动 ✗ ⇒ 松手后偏移被清掉、图弹回原处 ✓。
                                        //    ⇒ **先把这一段收工落盘** ✓（生成层会顺带从 `GENERATED` 转成
                                        //    `PAINT` ✓ —— 和"在它上面落过笔"是同一个结果 ✓，之后它就一直跟着格子走 ✓）；
                                        //    会话由页面那条 `autoFocusComicPaintLayer` 自动重开 ✓（撤销栈会清 ✗，如实记 ✓）。
                                        val focused = state.comicPaintLayerId
                                        val focusedPanel = focused?.let { id ->
                                            state.comicBoardPage?.layers?.firstOrNull { it.id == id }?.panelId
                                        }
                                        if (focused != null && focusedPanel == draggingId) {
                                            state.commitComicLayerPaint()
                                        }
                                        state.shiftComicPanelLayers(draggingId, dx, dy)
                                    }
                                }
                            }
                        },
                        // 「图画要按住 ctrl 才能拖动」（用户 2026-09-22）—— 同一条"取值 lambda"规矩 ✓
                        ctrlGesture = { overlayUi.canvasCtrlGesture },
                        // ⓪ 手势里现读"这一格此刻是不是选中了" ✓ —— 传的是**取值 lambda** ✓
                        //    而且它读的是**稳定对象** `overlayUi` 上的快照状态（不是捕获一个 Boolean ✗）
                        //    `pointerInput` 的协程不会因为重组而重启，捕获普通值会一直停在旧值 ✗
                        selectedNow = { overlayUi.selectedPanelId == panel.id },
                        t = t,
                    )
                }

                // ⚠️ **第 ㊼ 批（2026-09-21）**：气泡 / 文本那一层**整段删掉** ✓
                //（用户口径：「**删除气泡功能**」✓ —— 连代码一起删 ✓，见 `docs/69` ✓）。

                // ---- ⑬ ⑦ 选区：抬起来的那块（按它的变换画在正确位置上）+ 选框 / 四角控制点 ----
                //
                // ⚠️ 只有 **SELECT / LASSO** 才可能走到这里（别的工具下 `comicPaintFloating` 恒为 null ✓）——
                //    这正是用户 2026-09-20 第二条：「**选定后画出的图不能被选定框拖拽或放大缩小，
                //    需要选择或套索才可以**」✓ 的界面那一半 ✓
                // 抬起 = 那块像素已经从画布上抠走了（原处清成透明 ✓）⇒ **必须画出来** ✗，
                val paintFloating = state.comicPaintFloating
                // ⚠️ **第 ㊺ 批**：拖动 / 缩放浮动选区时**现读这个便宜计数** ✓ ——
                //    `FloatingSelection` 的 `dx/scaleX` 是**普通字段**（不是快照状态 ✗）⇒
                //    没有它界面**不会重组** ✓、手拖看不到块在动 ✗。
                //    它每帧 +1 ✓（只让这一层重画 ✓），**不是**那个很贵的 `comicPaintVersion` ✓
                //   （后者会重建 patch 位图 + 重读视口 ✓ = 卡死 ✓，见 `AppState` 的说明 ✓）。
                @Suppress("UNUSED_EXPRESSION")
                state.comicPaintFloatTick
                val paintFloatBitmap = if (paintFloating != null) state.comicPaintFloatingImage() else null
                if (paintFloating != null && paintFloatBitmap != null) {
                    // 变换照 `FloatingSelection` 的数据**逐条对齐** ✓（见那个类的说明 ✓）
                    // 布局框 = patch **未变化**时占的那块（originX/originY + patch 尺寸 ✓）
                    // `graphicsLayer` 的默认原点就是它的中心 ✓ —— 缩放 / 旋转都以 patch 中心为基准 ✓，
                    // 平移补上 dx/dy（`translationX/Y` 的单位是 px，所以要 × 密度 ✓）。
                    val layerDensity = LocalDensity.current
                    Box(
                        Modifier
                            .offset(
                                x = (paintFloating.originX * scale).dp,
                                y = (paintFloating.originY * scale).dp,
                            )
                            .size(
                                (paintFloating.patchWidth * scale).dp.coerceAtLeast(1.dp),
                                (paintFloating.patchHeight * scale).dp.coerceAtLeast(1.dp),
                            )
                            .zIndex(18f)
                            .graphicsLayer {
                                scaleX = paintFloating.scaleX
                                scaleY = paintFloating.scaleY
                                rotationZ = paintFloating.rotationDeg
                                translationX = with(layerDensity) {
                                    (paintFloating.dx * scale).dp.toPx()
                                }
                                translationY = with(layerDensity) {
                                    (paintFloating.dy * scale).dp.toPx()
                                }
                            },
                    ) {
                        Image(
                            bitmap = paintFloatBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                val paintShape = state.comicPaintSelection
                if (state.comicPaintOpen && paintShape != null) {
                    val paintPageWidth = state.comicPaintSession?.width ?: page.baseWidth
                    val paintPageHeight = state.comicPaintSession?.height ?: page.baseHeight
                    val selectionBounds = SelectionGeometry.bounds(
                        paintShape,
                        paintPageWidth,
                        paintPageHeight,
                    )
                    if (selectionBounds != null) {
                        ComicPaintSelectionOverlay(
                            shape = paintShape,
                            bounds = selectionBounds,
                            pageWidth = paintPageWidth,
                            pageHeight = paintPageHeight,
                            scale = scale,
                            //（和画布编辑器 `CanvasSelectionOverlay(handlesVisible = selectTool)` 一个口径 ✓）
                            handlesVisible = !state.comicPaintTool.isBrushTool(),
                        )
                    }
                }

                // ---- 手拖预览（纯装饰，没有任何 pointerInput）----
                dragRect?.let { rect ->
                    Box(
                        Modifier
                            .offset(x = (rect.left * scale).dp, y = (rect.top * scale).dp)
                            .size(
                                (rect.width * scale).dp.coerceAtLeast(1.dp),
                                (rect.height * scale).dp.coerceAtLeast(1.dp),
                            )
                            .zIndex(3f)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .border(1.dp, MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }

        // ---- ② 画布区尺寸变了 → 把摆放收一次 ----
        //
        // 两个用处（照生成页 `LaunchedEffect(areaState.value)` 那条 ✓）：
        //  · 窗口被拉小之后，**从盘上读回来的**偏移可能落在可视区之外（"画布怎么不见了" ✗）
        //    → 量到尺寸后按拖动时同一条夹取规矩（`±area/2` ✓）收一次 ✓；
        //  · 窗口变大时同理（偏移许可范围变大，不需要动它 —— 这条只在超界时才写 ✓）。
        LaunchedEffect(canvasAreaPx) {
            val area = canvasAreaPx
            if (area.width <= 0 || area.height <= 0) return@LaunchedEffect
            val clamped = Offset(
                x = overlayUi.canvasOffset.x.coerceIn(-area.width / 2f, area.width / 2f),
                y = overlayUi.canvasOffset.y.coerceIn(-area.height / 2f, area.height / 2f),
            )
            if (clamped != overlayUi.canvasOffset) overlayUi.canvasOffset = clamped
        }
    }
}

/**
 * 点在这一页的某个气泡 / 文本里吗。
 * 点在它们身上时**页面这一层什么都不做**（交给它们自己的手势 ✓）——
 * 这和原来那条"点在格子上就不新建"的守卫是同一个道理 ✓。
 */
private fun hitsOverlay(page: ComicBoardPage, point: Offset): Boolean =
    page.bubbles.any { point.x >= it.x && point.x <= it.right && point.y >= it.y && point.y <= it.bottom } ||
        page.texts.any { point.x >= it.x && point.x <= it.right && point.y >= it.y && point.y <= it.bottom }


/**
 * 一格（[ComicPanelBox] ✓）。
 *
 * 手势三层（从外到内），顺序**不能换** ✗：
 *  2. `pointerInput` + `detectDragGestures` → 拖动整格；
 *  3. 四个角的把手（子节点，比父节点先拿到事件）→ 缩放。
 * 角把手放在格子的 `Box` **里面**（不是外面那颗），于是"点把手"天然优先于"拖整格" ✓。
 * ## ⑭ 出过图的格子是"内容"了，不能乱动（用户 2026-09-20）
 * 用户原话：「**格子:生成以后不能随意拖动与改变大小,只有经过套索选中后才能进行操作**」；
 * 「**画布逻辑和 ps 一样啊,在画布上的东西不能乱动,只有选中/套索后才可以动**」✓。
 *  · **底色 / 边框 / 角上那行"目标像素" / 正中间那颗序号 / 四角把手**只在
 *    **"还没出图"**（排版期 ✓）时才画 —— 第 ⑭ 批用户改了口径：
 *    「**格子在生成后隐藏**」✓（**不看选中态** ✗，见 [chromeVisible] ✓）
 *  · ⚠️ **这一格自己不再画那张生成图** ✗（第 ⑭ 批：「格子在生成后还在，并且有两个图像」✓）——
 *    内容**只由图层叠画一次** ✓（见 [ComicCanvasArea] 里"位图层"那段 ✓）
 *  · **四角把手**是例外（照 PS 的 transform box ✓，见 [handlesVisible] ✓）
 *  · **直接拖动 / 直接四角缩放**由 [AppState.canDirectlyManipulateComicPanel] 说了算 ✓：
 *    没出过图照旧随手拖（排版期还得能拖 ✓）；出过图的**必须先被选中** ✓。
 * ⚠️ 选中态在**手势里必须现读** ✗：`pointerInput` 的协程不会因为重组而重启，
 * 捕获一个 `selected: Boolean` 会一直停在"建那一刻"的值 ✗ —— 所以额外收 [selectedNow]
 * 这个**取值 lambda** ✓（和 [panGesture] 同一条规矩 ✓）。
 */
@Composable
private fun BoxScope.ComicPanelBox(
    state: AppState,
    page: ComicBoardPage,
    panel: ComicPanel,
    scale: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    /** ② 这一下按着空格 / 中键 = **平移画布**（见 [ComicCanvasArea] 的 ② ✓）→ 本格整段让开 ✓。
     *  ⚠️ 是**取值 lambda**（不是 Boolean）：`pointerInput` 的协程不会因为重组而重建，
     * 捕获的普通值会变旧 ✗（见调用点的说明 ✓）。 */
    panGesture: () -> Boolean,
    /** ②c **这一下按着 Ctrl 吗** —— 「图画要按住 ctrl 才能拖动」（用户 2026-09-22 ✓）。
     *  没按 ⇒ 本格整段让开 ✓（左键在图上拖动**只平移画布** ✓）。同样是**取值 lambda** ✓。 */
    ctrlGesture: () -> Boolean,
    /** 这一格**此刻**是不是当前选中的那个对象 —— **手势里现读**（见函数注释那条 ⚠️ ✓）。 */
    selectedNow: () -> Boolean,
    /**
     * 拖动**开始**（真的能拖的那一下 ✓）：调用方记下"哪一格 + 它现在在哪" ✓，
     * 好让绑在它上面的绘画层在拖动期间**跟着一起走** ✓（用户 2026-09-22 ✓）。
     */
    onMoveStart: () -> Unit = {},
    /**
     * 拖动**结束 / 被打断**：调用方按"格子最终坐标 − 起始坐标"把绑着的层**真的挪一次像素** ✓。
     */
    onMoveEnd: () -> Unit = {},
    t: (String) -> String,
) {
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    // ⑮ 这一格的"格子框"要不要画：**出过图就隐藏**（用户 2026-09-20：「格子在生成后隐藏」✓）——
    // ⚠️ **不看选中态** ✗（第 ⑭ 批那个 `|| selected` 是用户实测"生成后框还在"的原因 ✓：
    // ⚠️ **第 ㊹ 批（2026-09-21）**：用户口径 ——
    //   「**新建格子后，格子和图层属于同一类型，同一时间只能聚焦一个。**
    //     **只有选择格子时才能拉动和调整大小,并且不能绘画,未选择格子时格子框不可见**」✓
    //
    // 于是"格子框"的判据从"**没出图**"改成"**被选中**"✓：
    //  · 选中 → 框 + 四角把手都在 ✓（能拉能缩 ✓）；
    //  · 没选中 → **框不可见** ✓（用户点名 ✓），也拉不动 ✓（见下面 `onDragStart` 的闸门 ✓）。
    // ⚠️ 老口径（`resultPath == null` ✓）是"排版期的格子一直露着框"✗ ——
    //    那正是用户说的"未选择时框还看得见"✓。
    val chromeVisible = selected
    // ⚠️ **第 ㊹ 批**：把手与框**同一个判据** ✓ —— 用户口径是
    //   「**只有选择格子时才能拉动和调整大小**」✓ ⇒ 没选中时把手也**不许露** ✗
    //   （露着就是"没选也能拖"的暗示 ✗，与上面那条口径自相矛盾 ✓）。
    val handlesVisible = selected

    Box(
        Modifier
            .offset(x = (panel.x * scale).dp, y = (panel.y * scale).dp)
            .size(
                (panel.w * scale).dp.coerceAtLeast(2.dp),
                (panel.h * scale).dp.coerceAtLeast(2.dp),
            )
            .zIndex(if (selected) 2f else 1f)
            // 底色 + 边框**出过图就一步都不画** ✓（用户：「格子在生成后隐藏」✓，
            // 哪怕它正被选中 —— 见 [chromeVisible] ✓）
            // ⚠️ 形状用的仍然是 `RoundedCornerShape`（早就在了 ✓）——
            //    **绝不用 `Modifier.border(…, 自定义 Shape)`** ✗（电脑端会抛
            //    `Failed to Image::makeFromBitmap`，用户实机崩过 ✓）
            .then(
                if (chromeVisible) {
                    Modifier
                        .background(
                            color = if (selected) {
                                primary.copy(alpha = 0.18f)
                            } else {
                                Color.White.copy(alpha = 0.10f)
                            },
                            shape = RoundedCornerShape(2.dp),
                        )
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) primary else outline.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(2.dp),
                        )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onSelect)
            .pointerInput(panel.id, page.baseWidth, page.baseHeight, scale) {
                // ⚠️⚠️ **第 ㊿i 批（2026-09-22）修：「按住空格时画布和图画被一起拖动」** ✓
                //
                // **根因**：`onDragStart` 里那些 `return@detectDragGestures` **取消不了这一串手势** ✗ ——
                //   它只是从**那个 lambda** 里返回 ✓（`detectDragGestures` 是普通函数，
                //   `onDragStart` 是它的参数，`invoke` 一下就完事 ✓），
                //   紧接着的拖动循环**照旧每帧调 `onDrag`** ✓ ⇒ 门槛形同虚设 ✗：
                //   按住空格在图上拖 = **画布平移 + 这一格也跟着跑** ✓（用户报的正是这个 ✓），
                //   拿着画笔在图上拖、没选中的格子拖…… 全都一样会动 ✗。
                // ⇒ 门槛**必须在这里也判一次** ✓（下面 `dragging` 那把闸 ✓）。
                //   `docs/43` §⑫ 写的"同一面旗子也接到格子拖 / 四角"本来就是这个意思 ✓，
                //   只是当时只写了 `onDragStart` 那一处 ⇒ 没生效 ✗。
                var dragging = false
                detectDragGestures(
                    // 手一按就先选中：拖一个没选中的格子时，用户预期它立刻变成"我正在动的那一格" ✓
                    onDragStart = {
                        dragging = false
                        // ⚠️ **诊断**（用户 2026-09-22：「**底板图层拖动时可以随意拖动，在图层里不行**」✓）——
                        //    这一次按下到底被哪一道门槛挡了，一行看全 ✓（拖动是低频事件 ✓，不刷屏 ✓）。
                        //    日志在 `%APPDATA%\NAI Studio\app.log` ✓。
                        val ctrlNow = ctrlGesture()
                        logInfo(
                            "ComicDrag",
                            "[ComicDrag] down panel=${panel.id} ctrl=$ctrlNow " +
                                "selecting=${state.comicPaintSelecting} pan=${panGesture()} " +
                                "selected=${selectedNow()} " +
                                "direct=${state.canDirectlyManipulateComicPanel(panel.id, selectedNow())} " +
                                "tool=${state.comicPaintTool} focusLayer=${state.comicPaintLayerId}",
                        )
                        // ② 这一下按着空格 / 中键 = 平移画布 ⇒ 整段让开 ✓（**平移永远优先** ✓，
                        //    和按没按 Ctrl 无关 —— 两件事一起发生才是用户报过的 bug ✗）
                        if (panGesture()) return@detectDragGestures
                        // ②c **Ctrl = "动图画"那把钥匙**（用户 2026-09-22：「**图画要按住 ctrl 才能拖动**」✓）——
                        //    ⚠️ 按着 Ctrl ⇒ **不看工具、也不要求"已选中"** ✓：Ctrl 本身就是那个门槛 ✓。
                        //    （第一版把 Ctrl 做成"在老门槛之上再加一道"，结果用户报
                        //      「**按住 ctrl 不能拖动**」✗ —— 因为老门槛还要求"选择 / 套索 + 已选中"，
                        //      手里不是那把工具时就一步都不动 ✓。⇒ Ctrl 改成**唯一门槛** ✓。）
                        //    没按 Ctrl ⇒ 保持老口径：只有"选择 / 套索 + 这一格已被选中"才动 ✓
                        //    （用户 2026-09-20：「生成以后不能随意拖动与改变大小，只有经过套索选中后才能操作」✓）。
                        if (!ctrlGesture()) {
                            if (!state.comicPaintSelecting) {
                                return@detectDragGestures
                            }
                            // ⑭ **出过图的格子：没被选中就一步都不动** ✓
                            if (!state.canDirectlyManipulateComicPanel(panel.id, selectedNow())) {
                                state.hintComicObjectSelectFirst()
                                return@detectDragGestures
                            }
                        }
                        onSelect()
                        dragging = true
                        // 记下"哪一格 + 它现在在哪" ✓ —— 拖动期间绑在它上面的绘画层要跟着走 ✓
                        onMoveStart()
                    },
                    // ⚠️ 拖动过程**不写盘**，松手 / 被打断各写一次（每帧写会把 prefs.json 磨穿 ✗）
                    onDragEnd = {
                        val moved = dragging
                        dragging = false
                        if (moved) onMoveEnd()
                        state.commitComicBoard()
                    },
                    onDragCancel = {
                        val moved = dragging
                        dragging = false
                        if (moved) onMoveEnd()
                        state.commitComicBoard()
                    },
                ) { change, amount ->
                    // 门槛没过的这一下**一步都不动** ✓，而且**不 consume** ✓ ——
                    // 让事件继续上浮给画布那条平移手势 ✓（"空格拖动 = 只平移"才对 ✓）。
                    if (!dragging) return@detectDragGestures
                    change.consume()
                    val dx = (with(density) { amount.x.toDp().value }) / scale
                    val dy = (with(density) { amount.y.toDp().value }) / scale
                    state.moveComicPanelBy(panel.id, dx, dy)
                }
            },
    ) {
        // ⚠️ **这一格自己不再画那张生成图** ✗（用户 2026-09-20：「格子在生成后还在，并且有两个图像」✓）——
        // 内容（`resultPath` 那张图）现在**只由图层叠画一次** ✓（见 [ComicCanvasArea] 里那一段
        // "位图层"的说明 ✓，几何与拼页导出同源 ✓）
        // ⚠️ 以前这里是 `panel.resultPath?.let { FileImage(maxDimension = 1024, Crop) }` ✓ ——
        // 它和图层叠里那层 `GENERATED` **画的是同一张图**（那份还是 1024 的糊图 ✓）
        // 画布上于是"一张图叠两遍 = 看起来有两个图像"✗。格子从此只画**结构 + 选中把手** ✓
        // 角上那条"目标像素"（只读 ✓）；出图之后底是图，垫一层半透明底才读得清 ✓
        // ⚠️ 序号那颗徽标**搬走了** ✓ —— 用户 2026-09-20：「格子的标号放正中间」✓
        //（现在是下面那颗居中的大号数字 ✓，不在左上角了 ✓）。
        // ⑮ **出过图 → 一个都不画** ✓（底色 / 边框 / 这行字 / 居中编号 / 左下角说明 ✓，
        // 哪怕它正被选中 ✓ —— 用户：「格子在生成后隐藏」✓）。
        if (chromeVisible) {
            Column(
                Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(3.dp),
                    )
                    .padding(3.dp),
            ) {
                Text(
                    text = panel.targetPixelText(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        // 选中那一格再多一条"目标像素"的完整说明（只读，不给人点 ✗）——
        // ⚠️ 同样跟"格子框"走 ✓（出过图就不画 ✓，哪怕它还选中着 ✓）
if (chromeVisible && selected) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.BottomStart).padding(3.dp),
            ) {
                Text(
                    text = RuntimeText.format(
                        state.settings.language,
                        "comic.targetPixels",
                        mapOf("size" to panel.targetPixelText()),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }

        // ⚠️ 四角把手**不是"格子框"** ✓（见 [handlesVisible] 那段说明 ✓）——
        // 出过图又没选中时一个都不画 ✓（用户：「生成后格子框隐藏」✓）
if (handlesVisible) {
            ComicPanelHandle(
                ComicPanelCorner.TOP_LEFT, Alignment.TopStart, panel, scale,
                state, onSelect, panGesture, ctrlGesture, selectedNow,
            )
            ComicPanelHandle(
                ComicPanelCorner.TOP_RIGHT, Alignment.TopEnd, panel, scale,
                state, onSelect, panGesture, ctrlGesture, selectedNow,
            )
            ComicPanelHandle(
                ComicPanelCorner.BOTTOM_LEFT, Alignment.BottomStart, panel, scale,
                state, onSelect, panGesture, ctrlGesture, selectedNow,
            )
            ComicPanelHandle(
                ComicPanelCorner.BOTTOM_RIGHT, Alignment.BottomEnd, panel, scale,
                state, onSelect, panGesture, ctrlGesture, selectedNow,
            )
        }

        // ⑤ 格子标号：**正中间**那颗大号数字（用户 2026-09-20：「格子的标号放正中间」✓）。
        // ⚠️ **不许挡手** ✗：这里只挂 `align`（布局位）自己的字号 —— **没有** `clickable`、
        // **没有** `pointerInput` ✓。Compose 的命中测试只认挂了手势修饰符的节点，所以这颗数字
        // 画在最后一个子节点（压在格内图与把手之上，视觉上）也**不吃**任何指针 ✓：
        // 拖动整格照旧落到这一格的 `pointerInput`、四角把手照旧先拿到事件 ✓。
        // 字号**跟着格子走**（同「版式预览」那套：不写死 sp ✗）—— 大格子里字要大 ✓，
        // 小格子里不能溢出格子（半格宽封顶 ✓）。颜色用**固定墨色**而不是主题色 ✓：
        // 底板是白纸（`ComicPaper`，不跟主题走 ✓），深色主题下的 `onSurface` 是浅色，
        // 落在白纸上等于看不见 ✗。
        // ⑭ 同样只在"格子框"该出现时才画 ✓（用户：「生成后格子框隐藏」✓）。
        if (chromeVisible) {
            val labelSize = (minOf(panel.w, panel.h) * scale * 0.5f).coerceIn(12f, 160f)
            Text(
                text = "${panel.order + 1}",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = with(density) { labelSize.dp.toSp() },
                    fontWeight = FontWeight.Bold,
                ),
                color = ComicPanelLabelInk.copy(alpha = 0.32f),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** 四角把手：拖它 = 缩放（对角固定）。 */
@Composable
private fun BoxScope.ComicPanelHandle(
    corner: ComicPanelCorner,
    align: Alignment,
    panel: ComicPanel,
    scale: Float,
    state: AppState,
    onSelect: () -> Unit,
    /** ② 这一下按着空格 / 中键 = **平移画布** ✓ → 把手整段让开（不然会一边平移一边缩放 ✗）。
     *  同 [ComicPanelBox]，是**取值 lambda**（普通 Boolean 会在 `pointerInput` 里变旧 ✗）。 */
    panGesture: () -> Boolean,
    /** ②c **这一下按着 Ctrl 吗** —— 「图画要按住 ctrl 才能拖动」（用户 2026-09-22 ✓）。
     *  没按 ⇒ 把手整段让开 ✓。同 [ComicPanelBox]，是**取值 lambda** ✓。 */
    ctrlGesture: () -> Boolean,
    /** 同 [ComicPanelBox]：这一格**此刻**是不是选中的 —— **手势里现读** ✓（第 ⑭ 批 ✓）。 */
    selectedNow: () -> Boolean,
) {
    val density = LocalDensity.current
    val half = 7.dp
    val outward = when (corner) {
        ComicPanelCorner.TOP_LEFT -> Offset(-0.5f, -0.5f)
        ComicPanelCorner.TOP_RIGHT -> Offset(0.5f, -0.5f)
        ComicPanelCorner.BOTTOM_LEFT -> Offset(-0.5f, 0.5f)
        ComicPanelCorner.BOTTOM_RIGHT -> Offset(0.5f, 0.5f)
    }
    Box(
        Modifier
            .align(align)
            .offset(x = half * outward.x * 2f, y = half * outward.y * 2f)
            .size(half * 2f)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(1.dp, Color.White, CircleShape)
            .pointerInput(panel.id, corner, scale) {
                // ⚠️ **第 ㊿i 批（2026-09-22）**：与 `ComicPanelBox` 同一个坑 ——
                //    `onDragStart` 里的 `return@detectDragGestures` **取消不了这一串手势** ✗，
                //    拖动循环照旧每帧都来 ⇒ 门槛必须在这里也判一次 ✓（`dragging` 那把闸 ✓）。
                var dragging = false
                detectDragGestures(
                    onDragStart = {
                        dragging = false
                        // ② 空格 / 中键 = 平移画布 ⇒ 整段让开 ✓（**平移永远优先** ✓）
                        if (panGesture()) return@detectDragGestures
                        // ②c Ctrl = "动图画"那把钥匙 ✓（与 `ComicPanelBox` 同一个口径 ✓）：
                        //    按着 Ctrl ⇒ 不看工具、不看选中态 ✓；没按 ⇒ 老口径（选择 / 套索 + 已选中 ✓）
                        if (!ctrlGesture()) {
                            // ⚠️ ⑰ **画笔类工具**下：四角控制点一步都不动 ✓（画的东西不受选定框支配 ✓）
                            //      **选择 / 套索**下放行 ✓（同一格那份说明 ✓）
                            // ⚠️ 判据**只看工具** ✓（`comicPaintSelecting` ✓，`comicPaintOpen` 不再参与 ✗）
                            if (!state.comicPaintSelecting) {
                                return@detectDragGestures
                            }
                            // ⚠️ 出过图的格子：只有**已经被选中**才允许直接缩放 ✓（同一格那份说明 ✓）
                            //    把手本身也只在"框该出现"时才在 ✓，所以这里正常走不到"没选中还想缩"✗
                            if (!state.canDirectlyManipulateComicPanel(panel.id, selectedNow())) {
                                return@detectDragGestures
                            }
                        }
                        onSelect()
                        dragging = true
                    },
                    onDragEnd = { dragging = false; state.commitComicBoard() },
                    onDragCancel = { dragging = false; state.commitComicBoard() },
                ) { change, amount ->
                    // 门槛没过 ⇒ 一步都不动、也**不 consume** ✓（让画布那条平移手势照旧收到 ✓）
                    if (!dragging) return@detectDragGestures
                    change.consume()
                    val dx = (with(density) { amount.x.toDp().value }) / scale
                    val dy = (with(density) { amount.y.toDp().value }) / scale
                    state.resizeComicPanelBy(panel.id, corner, dx, dy)
                }
            },
    )
}

/** 底板的"纸"：漫画的一页是**内容**不是界面，所以不跟主题走（深色下它也该是白的 ✓）。 */
private val ComicPaper = Color(0xFFFCFCFA)

/**
 * 底板周围那圈衬底 + 方格 —— ⛔ **本文件里那三个常量已删** ✗（2026-09-24 ✓）：
 * 它们搬去共用处 `ui/CanvasBackdrop.kt`（`CanvasBackdropColor` / `CanvasGridCell` /
 * `canvasBackdrop()` ✓），因为用户这一轮点名「**统一画布背景，用漫画模式的格子背景，颜色再深一些**」✓
 * —— 三档共用**同一个**实现 ✓，再各写一遍就又走回"两处各写一遍"的老路 ✗。
 */


/**
 * 底板永远偏白，深色主题下的 `onSurface` 是浅色、落在纸上等于看不见 ✗，所以写死一个深墨 ✓。
 */
private val ComicPanelLabelInk = Color(0xFF1A1A1A)

// ---------------------------------------------------------------------------
// ②③④ 三条栏（侧栏那一页的右边一列 ✓；每块各自"竖着堆 + 自己可滚" ✓）
//   · 各自"竖着堆 + 自己可滚"（`verticalScroll`），高度由宿主的 `modifier` 说了算 ✓
// ---------------------------------------------------------------------------

/**
 * ② **面板第 1 栏：底板**（一颗「新建 +」+ 一颗「导入图像」✓）。
 * ⚠️ 用户 2026-09-20 的新口径：「**底板区只有一个"新建+"按钮，点击可以设置画布大小，
 * 可以预设画布大小**」✓ —— 所以原来平铺在这一栏里的三档预设 / 自定义那两个框 / 模板 /
 * 清空 / 当前底板那行字**全部收进「新建 +」打开的浮层**（[NewBoardDialog] ✓），
 * 这一栏只剩两颗入口 ✓。铺模板前那次**确认**仍旧挂在触发它的这一块 ✓。
 * ⚠️ 这一栏**只有底板那点东西**（模板 / 清空已经收进「新建 +」那个浮层 ✓）；
 * 整块自己可滚 ✓，高度由宿主给**有界**值（见本文件头上那条硬约束 ✓）。
 */
@Composable
fun ComicBasePanelBody(
    state: AppState,
    ui: ComicModeUiState,
    t: (String) -> String,
    modifier: Modifier = Modifier,
) {
    // 惰性读盘（和画布块同一条 ✓；AppState 里有闸门 → 幂等 ✓）：单独把这一栏挂上去时也读得到盘 ✓
    LaunchedEffect(Unit) { state.ensureComicBoardLoaded() }

    val page = state.comicBoardPage
    val panels = state.comicBoardPanels

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
    ) {
        BaseToolbar(
            t = t,
            onNewBoard = { ui.customOpen = true },
        )
    }

    // 「新建 +」打开的浮层：预设尺寸 / 自定义宽高 / 模板 / 清空 / 当前底板 ✓
    if (ui.customOpen) {
        NewBoardDialog(
            state = state,
            page = page,
            ui = ui,
            t = t,
            onDismiss = { ui.customOpen = false },
        )
    }

    // 铺模板前的一次确认（已经有格子时才会走到这里）
    ui.pendingTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { ui.pendingTemplate = null },
            title = { Text(t("comic.replaceTitle")) },
            text = {
                Text(
                    RuntimeText.format(
                        state.settings.language,
                        "comic.replaceHint",
                        mapOf("count" to panels.size),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state.applyComicPanelTemplate(template.id)
                    ui.selectedPanelId = null
                    ui.pendingTemplate = null
                }) { Text(t("common.confirm")) }
            },
            dismissButton = {
                TextButton(onClick = { ui.pendingTemplate = null }) { Text(t("common.cancel")) }
            },
        )
    }
}

/**
 * ③ **面板第 2 栏：格子**（列表 / 选中 / 上移下移 / 删除 + 选中那一格的属性区 ✓）。
 * 属性区（提示词 + **只读**目标像素 + 队列状态 + 重试）放在**选中那一格的下面** ✓
 */
@Composable
fun ComicPanelsPanelBody(
    state: AppState,
    ui: ComicModeUiState,
    t: (String) -> String,
    modifier: Modifier = Modifier,
) {
    val panels = state.comicBoardPanels

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                t("comic.panels"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                RuntimeText.format(
                    state.settings.language,
                    "comic.board.panelCount",
                    mapOf("count" to panels.size),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))

        Text(
            t("comic.dragHint"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        panels.forEachIndexed { index, panel ->
            PanelRow(
                state = state,
                panel = panel,
                index = index,
                total = panels.size,
                selected = panel.id == ui.selectedPanelId,
                onSelect = { ui.selectedPanelId = panel.id },
                t = t,
            )
            // 选中的那一格：属性区（**提示词 + 只读尺寸 + 队列状态 + 重试** ✓）
            // 放在这一格的**下面**（列表本来就能滚 ✓）
if (panel.id == ui.selectedPanelId) {
                PanelProperty(state = state, panel = panel, t = t)
            }
            Spacer(Modifier.height(4.dp))
        }
        if (panels.isEmpty()) {
            Text(
                t("comic.noPanel"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

/**
 * ④ **图层编辑器那一块的内容：画布编辑器 + 图层**（第 ⑯ 批起；压平 / 导出这一页也在里面 ✓）。
 * 直接复用隔壁那一套（[OverlaySidebarSurface] ✓）——**不在这儿再长一份实现** ✓
 * ⚠️ ⑯ 用户 2026-09-20 第 1 条：「**字体放到右边**」✓ —— **字体库不在这里了** ✗：
 * 它搬去右列自成一块（[ComicFontPanelBody] ✓）；第 2 条「**…放在图层上方**」✓ ——
 * 画布编辑器（第 ⑯ 批起左窗口那一格是 [BrushPanel] ✓）在这个面板的**最前面**（
 * 图层列表上方 ✓）
 *
 * ⚠️ 气泡 / 文本那排"**工具与样式**"在 [ComicCanvasHost] 里（选完工具是要**在画布上拖 / 点**的 ✓，
 * 和画布贴在一起才顺手 ✓）；双击打开的**属性编辑框**也在那一块（它得跟画布一样**永远在场上** ✓）。
 */
@Composable
fun ComicOverlayPanelBody(
    state: AppState,
    ui: ComicModeUiState,
    t: (String) -> String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        OverlaySidebarSurface(
            state = state,
            page = state.comicBoardPage,
            overlayUi = ui,
            t = t,
            // `verticalScroll`**（那会给它无界高度 → 当场 `IllegalStateException: Vertically scrollable
            // measured with an infinity maximum height constraints` ✗ —— 和②③两条栏一个口径 ✓）
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

// ⚠️ 2026-09-20：「**导出 PSD**」那颗**按钮**（原来这里是一个
// `ComicPsdExportButton(state, t)`：`OutlinedButton` + `t("comic.board.exportPsd")` ✗）
// **已删** ✓ —— 用户「**导出默认导出 psd，按钮放在文件里**」「**那三个按钮删掉**」✓
// 现在它长在**「文件」菜单**里（`StudioShell.kt` 的 `WindowMenuBar` ✓，键还是 `comic.board.exportPsd` ✓，
// 快捷键 `Ctrl+Shift+S` ✓）——**导出逻辑一个字没动** ✓ —— 菜单那边用的还是这两条现成的：
//  · 选文件走 `LocalUiHost.current.rememberFileCreator("image/vnd.adobe.photoshop") { … }` ✓
//    是**电脑端 = AWT 的「另存为」对话框** ✓，回调给本地绝对路径 ✓；用户取消（`ref == null`
//    时）**什么都不做** ✓，绝不往仓库目录里写 ✗）
//  · 合成 / 落盘走 [AppState.exportComicPsdTo] ✓（几何与合成复用「导出这一页」那套 ✓）
//  · 进行中灰掉看 [AppState.comicPsdExporting] ✓（同一道闸门：同一页别并发跑两次 ✗）
//
/**
 * 选中格子的**属性区**（第 ③ 批加的）。
 * 三件事：
 *  2. **只读**显示「这一格会自动用 1792×576」✓（走 [InpaintSize.forRect]，和真正发出去的
 *     请求尺寸是同一个数 ✓，**不给人手改** ✗ —— 用户口径是"自动计算"）；
 *  3. 这一格在**队列里的状态**（等待 / 生成中 / 完成 / 失败 / 已跳过 ✓）+ 失败时的「重试」✓。
 * ⚠️ 输入框**每敲一下只改内存**（`setComicPanelPrompt` 不落盘），失去焦点时才 `commitComicBoard`
 * 写一次盘 ✓ —— 和拖动那套一个口径（每帧写会把 `prefs.json` 磨穿 ✗）。
 */
@Composable
private fun PanelProperty(
    state: AppState,
    panel: ComicPanel,
    t: (String) -> String,
) {
    val language = state.settings.language
    val tick = state.comicQueueTick
    val item = remember(tick, panel.id) {
        state.comicQueue?.items?.firstOrNull { it.id == panel.id }
    }
    var draft by remember(panel.id) { mutableStateOf(panel.prompt) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            t("comic.panelPrompt"),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = {
                draft = it
                state.setComicPanelPrompt(panel.id, it)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            placeholder = { Text(t("comic.board.panelPromptHint"), style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focus ->
                    if (!focus.isFocused) state.commitComicBoard()
                },
        )
        Text(
            t("comic.board.panelPromptHint"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (draft.isBlank()) {
            Text(
                t("comic.panelNoPrompt"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        // ⚠️ **只读**：这一格发出去会用的尺寸（InpaintSize.forRect ✓）
        Text(
            text = RuntimeText.format(
                language,
                "comic.panelAutoSize",
                mapOf("size" to panel.targetPixelText()),
            ),
            style = MaterialTheme.typography.labelMedium,
        )
        item?.let { queueItem ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comicStatusText(queueItem, t),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (queueItem.state == GenerationQueue.State.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                // 失败的格子可以**只重跑这一格** ✓（照样先弹确认框：跑一次就是花一次钱 ✓）
                if (queueItem.state == GenerationQueue.State.FAILED) {
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { state.requestComicRun(panel.id) }) {
                        Text(t("comic.retryPanel"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            queueItem.error?.let { reason ->
                Text(
                    reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 列表里的一行：序号 / 实际像素 / **目标像素（只读）** / **队列状态** / 上移 / 下移 / 删除。 */
@Composable
private fun PanelRow(
    state: AppState,
    panel: ComicPanel,
    index: Int,
    total: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    t: (String) -> String,
) {
    // 这一格在队列里的状态（还没跑过队列 → null，不显示 ✓）
    val tick = state.comicQueueTick
    val item = remember(tick, panel.id) {
        state.comicQueue?.items?.firstOrNull { it.id == panel.id }
    }
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
    ) {
        Row(
            Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(18.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${panel.w.roundToInt()}×${panel.h.roundToInt()} px",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = RuntimeText.format(
                        state.settings.language,
                        "comic.targetPixels",
                        mapOf("size" to panel.targetPixelText()),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 队列状态（等待 / 生成中 / 完成 / 失败 / 已跳过 ✓）
                item?.let { queueItem ->
                    Text(
                        text = comicStatusText(queueItem, t),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (queueItem.state == GenerationQueue.State.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = { state.moveComicPanelOrder(panel.id, -1) },
                enabled = index > 0,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = t("comic.moveUp"),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = { state.moveComicPanelOrder(panel.id, 1) },
                enabled = index < total - 1,
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = t("comic.moveDown"),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = {
                onSelect()
                state.deleteComicPanel(panel.id)
            }) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = t("comic.deletePanel"),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 整页宿主（侧边栏「高级漫画」那一页 ✓）
// ---------------------------------------------------------------------------

/**
 * **高级漫画 · 独立页宿主**（用户 2026-09-20：「**还是把高级漫画模式放回侧边栏，其余不变**」✓）。
 * 这一页走过一段弯路 ✓：第 ⑧ 批把它拆成四块（① 画布 + ②③④ 三条栏 ✓），第 ⑨ 批一度取消独立页、
 * 把四块嵌进生成页（画布进预览位、三条栏换掉提示词那三栏 ✗）—— 用户看过之后**改回来** ✓：
 * 还是侧边栏一页，四块**原样保留** ✓（这次只是把宿主加回来 ✓，四块里一个字节都没改 ✗）。
 * ## 布局：**左 = 图层编辑器（窗口）/ 中 = 画布 / 右 = 底板 + 格子 + 字体**
 *
 * ```
 * │ ④ 图层编辑器    │ ① ComicCanvasHost          │ ② 底板       │ ← 右列**三块**各 1/3 高
 * │  （窗口式面板： │   （气泡/文本工具栏 + 纸 +  │ ③ 格子       │   （第 ⑯ 批起字体也在这一列 ✓）
 * │   标题栏 →       │    格子 + 气泡/文本 +       │ ⑥ 字体       │
 * │   **画布编辑器** │    队列进度 ✓）             │              │
 *
 * ⚠️ 第 ⑮ 批（用户 2026-09-20 第 7 条）：「**图层编辑器做成窗口放在画布左边**」✓ ——
 * 原来右边一列是**三块**（底板 / 格子 / 图层 ✓），现在图层那一块**搬到左边** ✓
 * 做成一个**"窗口"样式的面板**（圆角 + 底色 + **标题栏** ✓，观感照 [NaiPanelChrome] /
 * 工具浮窗那一套 ✓，内容**自己滚** ✓）。**这一版就做成一块固定面板** ✓ ——
 * ⚠️ 第 ⑯ 批（用户 2026-09-20 三条里的前两条）：
 *  · 「**字体放到右边**」✓ —— 字体库从图层块里**摘出来**，右列从**两块变三块**
 *    （**底板 / 格子 / 字体**，各自 `weight(1f)` = **有界高度** ✓，见 [ComicFontPanelBody] ✓）；
 *  · 「**画布编辑器的各个功能…放在图层上面**」✓ —— [ComicPaintToolbar] 从**画布上方**
 *    搬进**左窗口、图层列表的上方** ✓（`ComicOverlayLayers.kt` 的 `ComicOverlaySidebar` 首项 ✓），
 * ⚠️ **窄窗口（宽度 < 900dp）竖着摞** ✓：画布在上、下面一行是"图层编辑器 + 底板/格子" ✓
 *（照原来 `BoxWithConstraints` 那套处理 ✓，宽窗口才三列并排 ✓）。
 * ## 两条硬约束（都是踩出来的 ✗）
 *
 *    ⚠️ 三条栏自己带 `verticalScroll` → 这里**不能再套一层 `verticalScroll`**（无界高度会把
 *    `IllegalStateException: Vertically scrollable component was measured with an infinity
 *    maximum height constraints` ✗，第 ⑧ 批第一版就是这么炸的 ✓）。这里给的是
 *    `weight(1f)` / `fillMaxHeight()`（= 有界 ✓），每块**自己滚自己** ✓；
 * 见 [ComicModeUiState] ✓（各块自己再 `remember` 一份的话，点画布侧栏不动、点侧栏画布不知道 ✗）。
 * 页面的内边距 / 圆角面板风格照 [ToolsScreen] 那一套（`padding(horizontal = 16.dp, vertical = 12.dp)` ✓）；
 * @param ui 四块共用的那一份状态 —— **默认自己 `remember` 一份**（并且接上 `platform.kv`，
 *   把画布的缩放 / 平移读回来 ✓）。留成参数是为了离屏冒烟能塞一份"已经摆好"的状态进去
 *   （`ComicScreenSmokeTest` 那条带缩放 / 平移的整页冒烟 ✓）
 *   ⚠️ 类型是**可空**的：这一条 Kotlin / Compose 里，`@Composable` 函数的**默认参数**
 *   不算组合上下文（`@Composable invocations can only happen from the context of a
 *   @Composable function` ✗，实测），所以"没传就自己 remember 一份"写在函数体第一行 ✓。
 */
@Composable
fun ComicModeScreen(
    state: AppState,
    t: (String) -> String,
    ui: ComicModeUiState? = null,
) {
    // ⚠️ **一份**，四块共用 ✓（见 [ComicModeUiState] 上的说明 ✓）
    // `LocalPlatform.current` 要**在这一层读**（它的 getter 是 `@Composable` 的，
    // 进不了 `remember {}` 那个普通 lambda ✗ —— 实测 `@Composable invocations can only happen
    // from the context of a @Composable function` ✓）。
    val placementKv = LocalPlatform.current.kv
    val uiState = ui ?: remember(placementKv) { ComicModeUiState(placementKv) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 900.dp
        // 用户 2026-09-22：「**图层（显示小眼睛和透明度的图层显示）宽一些宽一些，现在太小了**」✓
        // 原来宽窗 320dp / 窄窗 40% ✓ —— 一行里挤着"眼睛 + 缩略图 + 名字 + 透明度滑条 + 百分比" ✓，
        // 滑条只剩一小截、透明度几乎拖不准 ✗ ⇒ 抬到 **440dp / 窄窗 50%** ✓。
        val railWidth = if (narrow) maxWidth * 0.5f else 440.dp
        if (narrow) {
            // ---- ⑮/⑯ 窄窗口：**竖着摞** ✓（画布在上；下面一行是"图层编辑器 + 底板/格子/字体" ✓）----
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ① 画布：上面那一半（`weight(1f)` = **有界高度** ✓）
                ComicCanvasHost(
                    state = state,
                    ui = uiState,
                    t = t,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ④ 图层编辑器（窗口式面板 ✓，窄窗口里也给它一条宽度 ✓）
                    ComicLayerEditorWindow(
                        state = state,
                        ui = uiState,
                        t = t,
                        modifier = Modifier.width(railWidth).fillMaxHeight(),
                    )
                    // ②③⑥ 底板 + 格子 + 字体：剩下那一列，**三块各 `weight(1f)`** ✓
                    //（第 ⑯ 批：字体从图层块里搬过来 = 这一列的第三块 ✓）
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ComicBasePanelBody(
                            state = state,
                            ui = uiState,
                            t = t,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                        ComicPanelsPanelBody(
                            state = state,
                            ui = uiState,
                            t = t,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                }
            }
        } else {
            // ---- ⑮/⑯ 宽窗口：**左 = 图层编辑器 / 中 = 画布 / 右 = 底板 + 格子 + 字体** ✓ ----
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ④ 图层编辑器：最左边那一条（窗口式面板 ✓，`fillMaxHeight` = **有界高度** ✓）
                ComicLayerEditorWindow(
                    state = state,
                    ui = uiState,
                    t = t,
                    modifier = Modifier.width(railWidth).fillMaxHeight(),
                )
                // ① 画布：吃掉中间剩下的全部（`weight(1f)` = **有界宽度** ✓，高度铺满 ✓）
                ComicCanvasHost(
                    state = state,
                    ui = uiState,
                    t = t,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
                // ②③⑥ 底板 + 格子 + 字体：右边一列，**三块各自 `weight(1f)`** = **有界高度** ✓（自己滚 ✓）
                Column(
                    Modifier.width(railWidth).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ComicBasePanelBody(
                        state = state,
                        ui = uiState,
                        t = t,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    ComicPanelsPanelBody(
                        state = state,
                        ui = uiState,
                        t = t,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    // 和上面两块一个模子 ✓：圆角 + 底色 + **自己滚**（不是宿主套 `verticalScroll` ✗）
                }
            }
        }
    }
}

/**
 * ④ **图层编辑器** —— 画布左边那一块**"窗口"样式的面板**（第 ⑮ 批，用户 2026-09-20 第 7 条：
 * 「**图层编辑器做成窗口放在画布左边**」✓）。
 * 三样凑成"像一扇窗口" ✓（照 [NaiPanelChrome] / 工具浮窗那一套观感 ✓）：
 *  1. **圆角 + 底色** ✓（底色跟随设置里那个「窗口」色 —— `LocalPanelBackgroundColor` ✓）
 *  2. **标题栏** ✓（[ComicPanelTitleBar] ✓：高度固定、标题文字、颜色跟随设置 ✓）
 *  3. **内容自己滚** ✓ —— 直接复用 [ComicOverlayPanelBody]（= `OverlaySidebarSurface` ✓）
 *     它自己带 `verticalScroll` ✓，这里给 `weight(1f)` ⇒ **有界高度** ✓
 *
 * ## ⑯ 里面从上到下的顺序（用户 2026-09-20 第 2 条：「…**放在图层上面**」✓）
 * ```
 * 标题栏（"图层"）→ **笔刷面板**（[BrushPanel] ✓，
 *                    「分类胶囊 + 22 颗工具 + 16 个预设 + 预览条 + 参数条 + 色环 + 撤销/重做」；
 *                    ⚠️ 分类胶囊 + 15 颗占位工具 + 变体菜单**已删** ✗；
 *                    现在是「**7 颗工具 + 16 个预设 + 预览条 + 参数条 + 色环 + 撤销/重做**」✓
 *                → 图层操作行（`LayerActionRow` ✓）→ 图层列表 …
 *
 * ⚠️ 那一格原来是 `ComicPaintToolbar`（[ComicPaintToolbar] ✓），第 ⑯ 批**换成了** [BrushPanel] ✓ ——
 * 它里面那几样（工具图标 / 色环 / 笔刷 / 撤销重做 / 只读状态两行）**都搬进去了** ✓，
 * 画布编辑器是 `ComicOverlayPanelBody` → `OverlaySidebarSurface` → `ComicOverlaySidebar`
 * 的**第一项** ✓（落在那一块**已有的** `verticalScroll` 里 ✓ —— 窄窗口下这一整块能滚着看全 ✓，
 * 这里**没有**再套一层 `verticalScroll` ✗；面板自己那块**参数区**走
 * `heightIn(max = …) + verticalScroll`（= 有界高度 ✓）
 * **画布上方那一条撤掉了** ✓（只放一份 ✓）
 *
 * ⚠️ **这一版就做成一块固定面板** ✓ —— 用户**没有**要求拖动 / 浮动 ✗（别自己加 ✗；
 * 真要浮动另说 ✓）。
 */
@Composable
private fun ComicLayerEditorWindow(
    state: AppState,
    ui: ComicModeUiState,
    t: (String) -> String,
    modifier: Modifier = Modifier,
) {
    Surface(
        // 底色走设置里那个「窗口」色（没设就跟随主题 ✓，和分离面板 / 工具浮窗一个口径 ✓）
        color = LocalPanelBackgroundColor.current ?: MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize()) {
            ComicPanelTitleBar(t("comic.layers"))
            // ⚠️ `weight(1f)` = **有界高度** ✓（里面那块自己带 `verticalScroll` ——
            //    无界高度会当场 `IllegalStateException`，和②③两条栏一个口径 ✓）
            ComicOverlayPanelBody(
                state = state,
                ui = ui,
                t = t,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

/**
 * 「窗口」那条**标题栏**（只有标题 ✓ —— 这一版不做拖动 / 关闭 ✗，用户没要求 ✓）。
 * 颜色口径和仓库里那几处窗口**逐条一致** ✓（见 `ui/Theme.kt` 的 [LocalPanelTitleBarColor] ✓）：
 *  · 没设置标题栏颜色（默认）→ 跟随面板底色（= 视觉上没有独立标题栏 ✓），文字用 `onSurfaceVariant` ✓；
 *  · 设置了 → 用那个色，文字按**亮度**自动取黑 / 白 ✓。
 */
@Composable
private fun ComicPanelTitleBar(title: String) {
    val configured = LocalPanelTitleBarColor.current
    val barColor = configured ?: MaterialTheme.colorScheme.surfaceContainer
    val barContent = configured?.let { color ->
        if (color.luminance() < 0.5f) Color.White else Color.Black
    } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(barColor)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = barContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// 中控台「图层」栏（漫画档把提示词栏换成它 ✓）

/**
 * **漫画档的「图层」栏**（用户 2026-09-29：「漫画界面的提示词栏变为图层栏，**新建格子就加一层图层**，
 * **点击图层栏的图层可以输入提示词**」✓）。
 *
 *  · 一格 = 一层 ✓（`ComicPanel` 本来就是"格子 + 图层"合一的模型 ✓）——画布上拖出来的格子也会自己出现在这里 ✓；
 *  · 列表**新的在上** ✓（和图层面板一个口径 ✓）；
 *  · 点一层 = 选中（画布上同步亮起 ✓，`state.comicUi.selectedPanelId` ✓）并就地展开这一格的提示词框 ✓；
 *  · 「+ 新建格子」= 在页面中间放一格默认大小的格子并选中 ✓；
 *  · 负面提示词全页共用 ✓ —— 放在最下面，免得换成图层栏后没处改 ✗。
 */
@Composable
internal fun ComicPromptLayerBar(state: AppState, t: (String) -> String) {
    val ui = state.comicUi
    val page = state.comicBoardPage
    val panels = state.comicBoardPanels
    val language = state.settings.language
    var focusPanelId by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                t("comic.layers"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                RuntimeText.format(language, "comic.board.panelCount", mapOf("count" to panels.size)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    val p = page ?: return@OutlinedButton
                    val minSide = ComicBoardMetrics.minPanelSide(p.baseWidth)
                    val w = maxOf(p.baseWidth * 0.5f, minSide)
                    val h = maxOf(p.baseHeight * 0.3f, minSide)
                    val step = (panels.size % 5) * p.baseWidth * 0.04f
                    val id = state.addComicPanel(
                        x = (p.baseWidth - w) / 2f + step,
                        y = (p.baseHeight - h) / 2f + step,
                        w = w,
                        h = h,
                    )
                    if (id != null) {
                        ui.selectedPanelId = id
                        focusPanelId = id
                    }
                },
                enabled = page != null,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            ) {
                Text(t("comic.layerBar.newPanel"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
        if (page == null) {
            Text(
                t("comic.layerBar.noBoard"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (panels.isEmpty()) {
            Text(
                t("comic.noPanel"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (index in panels.indices.reversed()) {
            val panel = panels[index]
            key(panel.id) {
                ComicPromptLayerRow(
                    state = state,
                    panel = panel,
                    number = index + 1,
                    selected = panel.id == ui.selectedPanelId,
                    requestFocus = focusPanelId == panel.id,
                    onFocused = { focusPanelId = null },
                    onClick = {
                        if (ui.selectedPanelId == panel.id) {
                            ui.selectedPanelId = null
                        } else {
                            ui.selectedPanelId = panel.id
                            focusPanelId = panel.id
                        }
                    },
                    onDelete = {
                        if (ui.selectedPanelId == panel.id) ui.selectedPanelId = null
                        state.deleteComicPanel(panel.id)
                    },
                    t = t,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            t("comic.layerBar.negative"),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = state.params.negativePrompt,
            onValueChange = { value -> state.setParam { it.copy(negativePrompt = value) } },
            minLines = 2,
            maxLines = 6,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 图层栏里的一行（= 一格）：序号 / 名字 / 尺寸 / 提示词预览 / 队列状态 / 删除；选中时展开提示词框 ✓。 */
@Composable
private fun ComicPromptLayerRow(
    state: AppState,
    panel: ComicPanel,
    number: Int,
    selected: Boolean,
    requestFocus: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    t: (String) -> String,
) {
    val tick = state.comicQueueTick
    val item = remember(tick, panel.id) {
        state.comicQueue?.items?.firstOrNull { it.id == panel.id }
    }
    val failed = item?.state == GenerationQueue.State.FAILED
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(start = 8.dp, end = 2.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$number",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            RuntimeText.format(state.settings.language, "comic.layerBar.panel", mapOf("n" to number)),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${panel.w.roundToInt()}×${panel.h.roundToInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (!selected) {
                        Text(
                            if (panel.prompt.isBlank()) t("comic.panelNoPrompt") else panel.prompt,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (panel.prompt.isBlank()) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    item?.let { queueItem ->
                        Text(
                            comicStatusText(queueItem, t),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = t("comic.layerDelete"),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (selected) {
                val focus = remember { FocusRequester() }
                var draft by remember(panel.id) { mutableStateOf(panel.prompt) }
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        state.setComicPanelPrompt(panel.id, it)
                    },
                    minLines = 3,
                    maxLines = 8,
                    textStyle = MaterialTheme.typography.bodySmall,
                    placeholder = { Text(t("comic.panelPrompt"), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                        .focusRequester(focus)
                        .onFocusChanged { if (!it.isFocused) state.commitComicBoard() },
                )
                if (requestFocus) {
                    LaunchedEffect(panel.id) {
                        runCatching { focus.requestFocus() }
                        onFocused()
                    }
                }
                if (failed) {
                    TextButton(
                        onClick = { state.requestComicRun(panel.id) },
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Text(t("comic.retryPanel"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
