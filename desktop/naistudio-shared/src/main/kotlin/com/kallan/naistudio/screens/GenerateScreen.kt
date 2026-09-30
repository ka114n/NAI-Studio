package com.kallan.naistudio.screens

import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.ScrollState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
// ⚠️ 本文件里 `androidx.compose.foundation.lazy.items`（列表）已经在用了，
// 网格的 `items` 必须起别名才不会撞（和 `AnimaDexTool.kt` 一个写法）
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import com.kallan.naistudio.platform.LocalImageIo
import com.kallan.naistudio.models.GenerateActions
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.HistoryItem
import com.kallan.naistudio.models.NaiCatalog
import com.kallan.naistudio.models.NaiOption
import com.kallan.naistudio.models.MaskPoint
import com.kallan.naistudio.models.PromptField
import com.kallan.naistudio.models.SelectionShape
import com.kallan.naistudio.models.TextFieldTarget
import com.kallan.naistudio.platform.KeyValueStore
import com.kallan.naistudio.platform.LocalImageIo
import com.kallan.naistudio.platform.LocalPlatform
import com.kallan.naistudio.platform.LocalUiHost
import com.kallan.naistudio.platform.logInfo
import com.kallan.naistudio.services.DirectorTools
import com.kallan.naistudio.services.MaskCodec
import com.kallan.naistudio.services.OfficialUpscale
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.state.formatDuration
import com.kallan.naistudio.ui.BoltIcon
import com.kallan.naistudio.ui.CollapsibleSubSection
import com.kallan.naistudio.ui.canvasBackdrop
import com.kallan.naistudio.ui.canvasBaseColor
import com.kallan.naistudio.ui.contrastRatio
import com.kallan.naistudio.ui.ReferenceCanvasColor
import com.kallan.naistudio.ui.FileImage
import com.kallan.naistudio.ui.FullscreenImageViewer
import com.kallan.naistudio.ui.LabeledSlider
import com.kallan.naistudio.ui.CANVAS_CMD_RESET_PLACEMENT
import com.kallan.naistudio.ui.CANVAS_CMD_ZOOM_IN
import com.kallan.naistudio.ui.CANVAS_CMD_ZOOM_OUT
import com.kallan.naistudio.ui.LocalCanvasViewCommands
import com.kallan.naistudio.ui.LocalWindowSize
import com.kallan.naistudio.ui.NumberField
import com.kallan.naistudio.ui.PickerField
import com.kallan.naistudio.ui.ClockIcon
import com.kallan.naistudio.ui.followFingerForSelection
import com.kallan.naistudio.ui.LockClosedIcon
import com.kallan.naistudio.ui.LockOpenIcon
import com.kallan.naistudio.ui.MaskSelectIcon
import com.kallan.naistudio.ui.PictureIcon
import com.kallan.naistudio.ui.TuneIcon
import com.kallan.naistudio.ui.RedoIcon
import com.kallan.naistudio.ui.UndoIcon
import com.kallan.naistudio.ui.rememberPromptHighlight
import com.kallan.naistudio.ui.NaiGlass
import com.kallan.naistudio.ui.glassBackdrop
import com.kallan.naistudio.ui.NaiShape
import com.kallan.naistudio.ui.SectionCard
import com.kallan.naistudio.ui.SparkleIcon
import com.kallan.naistudio.ui.StrokedText
import com.kallan.naistudio.ui.SubHeader
import com.kallan.naistudio.ui.SwitchRow
import com.kallan.naistudio.ui.ViewerImage
import com.kallan.naistudio.ui.LocalPanelBackgroundColor
import com.kallan.naistudio.ui.LocalPanelBorderColor
import com.kallan.naistudio.ui.LocalPanelTitleBarColor
import com.kallan.naistudio.ui.NaiPanelChrome
import com.kallan.naistudio.ui.onSecondaryClick
import com.kallan.naistudio.ui.NaiSkinTokens
import com.kallan.naistudio.ui.LocalNaiSkin
import com.kallan.naistudio.ui.NaiSkin
import com.kallan.naistudio.ui.referenceGrid
import com.kallan.naistudio.ui.trackTextInputFocus
import com.kallan.naistudio.ui.useSplitLayout

/**
 * 底部抽屉 / 宽屏左栏的 Tab 索引：提示词 / **角色与分镜（中间）** / 参数。
 *
 * ⚠️ `internal` 而不是 `private`：**菜单「窗口 → 分离 …」**（在 `StudioShell`）与
 * **分离出去的独立窗口**（在桌面入口 `Main.kt`）都要按同一套编号认面板（用户 2026-09-19）。
 */
internal const val TAB_PROMPTS = 0
internal const val TAB_CHARACTERS = 1
internal const val TAB_PARAMS = 2

/**
 * 两个归一化点 → **聚焦框的矩形**（聚焦重绘用）。
 *
 * 存 `min/max` 而不是按下点/抬起点：框可以从任意方向拖出来，
 * 后面所有算法（裁剪、放大、回贴）都按"左上 + 宽高"理解它。
 */
private fun focusRectOf(a: MaskPoint, b: MaskPoint): SelectionShape.Rect =
    SelectionShape.Rect(
        left = minOf(a.x, b.x),
        top = minOf(a.y, b.y),
        right = maxOf(a.x, b.x),
        bottom = maxOf(a.y, b.y),
    )

/**
 * 双栏之间那条**左栏宽度**（dp，整数）存在平台层用的键 —— 电脑上落在 `prefs.json`。
 *
 * 曾经存过"左栏占比"（`generate.splitPercentV2`）：比例制在**拉窗口**时会让两边一起变，
 * 而用户 2026-09-16 明确要求"只有画布变大小、侧边栏和竖条不变"，所以改成存**固定 dp**。
 * 旧键留着不读也不写（新键从默认值开始，用户最多再拖一次）。
 */
private const val KEY_LEFT_PANEL_WIDTH = "generate.leftPanelWidthDp"

// ---------------------------------------------------------------------------
// 面板布局的落盘（提示词栏 / 底栏的位置与尺寸）
// ---------------------------------------------------------------------------

/**
 * **持久记忆提示词栏和底栏的位置**（用户 2026-09-19：「移动后每次重启会记住」）。
 *
 * 两块面板"飘在哪、多大"原来只住在组合状态里 —— 重启就回到吸附态。
 * 现在拖动结束（`endPanelGesture`）/「重排面板」时写进 `platform.kv`
 *（电脑上就是 `%APPDATA%\NAI Studio\prefs.json`），下次启动原样摆回来。
 *
 * ⚠️ 只记**像素**（相对内容格左上角）：和窗口几何同一套口径 —— 窗口大小本身也会被记住，
 * 于是这串坐标在下次启动时仍然对得上。窗口被拉小的话，`rootSize` 变化时会把面板夹回可见范围。
 *
 * ⚠️ [KEY_PROMPT_FLOATING] / [KEY_BOTTOM_FLOATING] 是**遗留键**：老版本用它记"永远是窗口"，
 * 现在"是不是浮动"由用户的选择决定（`AppState.promptPanelFloating`），这两个键**只写不读**。
 */
private const val KEY_PROMPT_FLOATING = "panels.prompt.floating"
private const val KEY_PROMPT_X = "panels.prompt.x"
private const val KEY_PROMPT_Y = "panels.prompt.y"
private const val KEY_PROMPT_W = "panels.prompt.w"
private const val KEY_PROMPT_H = "panels.prompt.h"
private const val KEY_BOTTOM_FLOATING = "panels.bottom.floating"
private const val KEY_BOTTOM_X = "panels.bottom.x"
private const val KEY_BOTTOM_Y = "panels.bottom.y"
private const val KEY_BOTTOM_W = "panels.bottom.w"
private const val KEY_BOTTOM_H = "panels.bottom.h"

/**
 * 底栏**吸附态**的高度（px）也要记。
 *
 * ⚠️ 这是 2026-09-19 那个"**底栏的吸附不见了**"的根因：吸附态高度只在"底栏贴底渲染"时由
 * `onSizeChanged` 量出来；面板位置一旦持久化，底栏就会**以浮动态启动** —— 那时它压根没贴底渲染过，
 * `bottomBarHeightPx` 一直是 0 → 吸附目标算成 `rootSize.height`（差了一整条底栏）→ 怎么拖都吸不回去 ✗。
 * 记下来之后，浮动态启动也有正确的吸附目标。
 */
private const val KEY_BOTTOM_DOCK_H = "panels.bottom.dockH"

/** 没有记录、也没量到时的兜底底栏高度（真机量到的是 128~168px 这个量级）。 */
private val BOTTOM_BAR_FALLBACK_HEIGHT = 168.dp

/** 分离出来的那块框的位置键（`panels.detached.<tab>.x` / `.y`）。 */
private fun detachedKey(tab: Int, axis: String): String = "panels.detached.$tab.$axis"

/**
 * 底栏那块窗口的**默认落点**：跟在提示词栏右边、贴近内容区底边
 *（= 它以前"吸附态"的样子，用户 2026-09-19 改成窗口之后用它当初始位置 / 「重排面板」的归位点）。
 */
private fun defaultBottomOffset(root: IntSize, promptWidthPx: Float, barHeightPx: Int): Offset = Offset(
    x = promptWidthPx,
    y = (root.height - barHeightPx).toFloat().coerceAtLeast(0f),
)

/**
 * 底栏的**吸附目标 y**（它贴底那一行的位置）。
 *
 * ⚠️ 必须容忍"高度还没量到"：面板位置持久化之后，底栏可能**以浮动态启动**，
 * 那时吸附态那块压根没渲染过、`onSizeChanged` 也没跑，高度就是 0 ——
 * 直接拿它算会让吸附目标落在内容区底边**之下**一整条，于是"怎么拖都吸不回去"
 *（用户 2026-09-19 报的「底栏的吸附不见了」就是这个）。量不到就用兜底高度。
 */
private fun dockBarY(root: IntSize, dockHeightPx: Int, density: androidx.compose.ui.unit.Density): Float {
    val h = if (dockHeightPx > 0) {
        dockHeightPx.toFloat()
    } else {
        with(density) { BOTTOM_BAR_FALLBACK_HEIGHT.toPx() }
    }
    return (root.height - h).coerceAtLeast(0f)
}

/**
 * 读回来的那两份面板布局。
 *
 * ⚠️ 这里**没有**"是不是浮动的"—— 那件事现在由用户的选择决定（`AppState.promptPanelFloating` /
 * `bottomBarFloating`，「窗口 → 分离操作面板」里勾），**默认停靠**。
 * 老版本的两个键 `panels.prompt.floating` / `panels.bottom.floating` 只是"永远是窗口"那版的遗留：
 * 这里照旧在写盘时刷成当时的真值（方便查看），但**读的时候不用它们**
 *（用户 2026-09-26：第一次运行 / 没设置过 / 老设置都应当是停靠 —— 见 `promptPanelFloating`）。
 */
private data class SavedPanelLayout(
    val promptOffset: Offset,
    val promptSize: Size?,
    val bottomOffset: Offset,
    val bottomSize: Size?,
    /** 底栏吸附态的高度（px，0 = 没记过）；见 [KEY_BOTTOM_DOCK_H]。 */
    val bottomDockHeight: Int,
)

private fun KeyValueStore.panelSize(wKey: String, hKey: String): Size? {
    val w = getInt(wKey, 0)
    val h = getInt(hKey, 0)
    return if (w > 0 && h > 0) Size(w.toFloat(), h.toFloat()) else null
}

private fun loadPanelLayout(kv: KeyValueStore): SavedPanelLayout = SavedPanelLayout(
    promptOffset = Offset(
        kv.getInt(KEY_PROMPT_X, 0).toFloat(),
        kv.getInt(KEY_PROMPT_Y, 0).toFloat(),
    ),
    promptSize = kv.panelSize(KEY_PROMPT_W, KEY_PROMPT_H),
    bottomOffset = Offset(
        kv.getInt(KEY_BOTTOM_X, 0).toFloat(),
        kv.getInt(KEY_BOTTOM_Y, 0).toFloat(),
    ),
    bottomSize = kv.panelSize(KEY_BOTTOM_W, KEY_BOTTOM_H),
    bottomDockHeight = kv.getInt(KEY_BOTTOM_DOCK_H, 0),
)

/** 两栏之间那条分隔条的**热区宽度**（看起来只有 1px 的线，能抓住的范围要宽得多）。 */
private val SplitterWidth = 12.dp

/**
 * 双栏之间的**可拖拽分隔条**（用户 2026-09-16 要求：觉得提示词/参数那栏太宽，想自己拉）。
 *
 * ## 为什么收 `MutableFloatState` 而不是 `Float`
 *
 * 拖拽回调住在 `pointerInput` 里，它只会在 key 变化时重建 —— 直接捕获一个 `Float`
 * 会**定格在建立那一刻的值**，拖起来要么不动、要么越拖越离谱（每次都用同一个基准算）。
 * 捕获状态**对象**则每次读 `floatValue` 都是最新的，也就不需要 `rememberUpdatedState`。
 *
 * ## 边界
 *
 * 两侧各留 [minColumnWidth]（宽窗口下约等于 30%），换算成比例后夹住 ——
 * 免得手一抖把一边拖到看不见，然后就找不到那条线了。
 * 鼠标靠上去本来是"左右箭头"光标最合适，但 `PointerIcon` 的桌面构造在共用树里用不了
 *（要看 AWT 的 `Cursor`），所以改成拖动时线加粗变色作为提示。
 */
@Composable
private fun ColumnSplitter(
    /** 左栏宽度（**dp 整数**，就直接改它 —— 固定宽度而不是比例）。 */
    widthDp: MutableIntState,
    contentWidth: Dp,
    minWidth: Dp,
    /** 右边（画布）至少留多宽。 */
    minOtherWidth: Dp,
    onCommit: () -> Unit,
    /**
     * 摆放用（宽屏改成"画布铺满 + 面板浮在上面"之后，这条分隔条要**手动定位**到面板右边缘：
     * 调用方传 `Modifier.align(...).offset(x = 面板宽)`）。默认不变 —— 老的两栏布局直接挨着放。
     */
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val maxWidth = (contentWidth.value - minOtherWidth.value).coerceAtLeast(minWidth.value)
    var dragging by remember { mutableStateOf(false) }
    // 鼠标靠上去变成"左右调整"的光标（用户给的样式）—— 手机返回空 Modifier
    val resizeCursor = LocalUiHost.current.horizontalResizeCursor()

    Box(
        modifier
            .width(SplitterWidth)
            .fillMaxHeight()
            .then(resizeCursor)
            .pointerInput(minWidth, maxWidth) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        onCommit()
                    },
                    onDragCancel = {
                        dragging = false
                        onCommit()
                    },
                ) { change, dragAmount ->
                    change.consume()
                    // 拖动量是**像素**，左栏记的是 **dp** —— 先换算再夹取
                    val deltaDp = with(density) { dragAmount.x.toDp().value }
                    widthDp.intValue = (widthDp.intValue + deltaDp)
                        .coerceIn(minWidth.value, maxWidth)
                        .toInt()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 平时是一条细线，拖动时加粗并用主色 —— 告诉用户"这条能拖"
        Box(
            Modifier
                .width(if (dragging) 3.dp else 1.dp)
                .fillMaxHeight()
                .background(
                    if (dragging) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
        )
    }
}

/** 尺寸下拉的选项：**只有 W×H 数值**，label 与 value 都用同一个字符串（不在列表里时也能原样回显）。 */
private val sizeOptions: List<NaiOption> = NaiCatalog.sizePresets.map {
    NaiOption("${it.width}×${it.height}", "${it.width}×${it.height}")
}

/** 甩动速度阈值（px/s）：超过它就直接开/收，不看拖了多远。 */
private const val FLING_VELOCITY = 900f


/**
 * 生成页。
 *
 * **竖屏结构对齐 NovelAI 手机网页：**
 * ```
 * ┌──────────────────────────┐
 * │        图片（吃满）        │  ← 按真实宽高比居中，留黑边
 * ├──────────────────────────┤
 * │  ⌐⌐ 把手                  │  ← 抽屉收起态：把手 + 提示词摘要 + ▲
 * │  high detail, best...  ▲ │
 * ├──────────────────────────┤
 * │  ⚙️   [ 生成图片 ]        │  ← 常驻运行条
 * └──────────────────────────┘
 * ```
 * 点摘要行（或 ⚙️）弹出**覆盖式底部抽屉**，里面用 **Tab** 分组：`提示词` / `参数` / `角色`；
 * 也支持**按住底栏往上滑**把它拉出来。覆盖式意味着**图片不缩小**，只是被盖住。
 *
 * **宽屏**仍走双栏：左栏图片 + 运行条，右栏提示词 / 参数 / 角色滚动。
 *
 * 侧边栏（左上角三条横杠）**只负责页面导航**；风格预设仍是「选择预设」打开的面板。
 */
@Composable
fun GenerateScreen(
    state: AppState,
    /** 把「底部面板是否正在使用（打开或正在拉）」报给外壳，用来屏蔽侧边栏手势。 */
    onPanelActiveChange: (Boolean) -> Unit = {},
    /** 遮罩模式开着时把翻页手势也锁掉，否则在图上涂画会被「文生图 ↔ 图库」的滑动抢走。 */
    onContentGestureLockChange: (Boolean) -> Unit = {},
) {
    /**
     * **当前选中的提示词框**（[PromptField] 里那三个 id 之一）。
     *
     * 常驻按钮栏里那五个功能（撤销 / 重做 / 历史 / 翻译 / 优化）以及角色图鉴的"填入"，
     * 全都按它分派（用户 2026-09-16 要求）。谁被点进去编辑就认谁 —— 所以它是由
     * [PromptFields] 里各个框的焦点回调写进来的。
     *
     * 之所以放在这一层而不是 [PromptFields] 内部：按钮栏在**它的外面** ——
     * 底部那条固定栏和抽屉内容不是同一个组合层级，藏在里面就传不出来。
     *
     * ⚠️ 声明必须**早于**下面那个角色图鉴抽屉（抽屉的 onNamePick 要读它）。
     */
    var activePromptField by rememberSaveable { mutableStateOf(PromptField.POSITIVE) }

    // 角色图鉴抽屉放在**这一层**（GenerateScreen 顶层）：早先写在「提示词」那一栏内部，
    // 结果"在角色栏点按钮没反应、切回提示词栏抽屉才出来" —— 那一栏没被组合时谁都画不出来。
    if (state.animaSheetOpen) {
        val sheetLanguage = state.settings.language
        AnimaDexSheet(
            state = state,
            t = { key -> RuntimeText.text(sheetLanguage, key) },
            forCharacters = state.animaSheetForCharacters,
            promptField = activePromptField,
        )
    }
    // 导入图片 = 图生图模式的入口（走宿主能力：手机是系统相册，电脑是文件对话框）
    val importButton = LocalUiHost.current.rememberImagePicker { ref ->
        if (ref != null) state.importImage(ref)
    }
    val launchImport: () -> Unit = { importButton.launch(null) }
    val language = state.settings.language
    val t: (String) -> String = { key -> RuntimeText.text(language, key) }

    // 窗口尺寸走共用的 LocalWindowSize（原来读 LocalConfiguration，那是 Android 专属）
    val widthDp = LocalWindowSize.current.width
    val heightDp = LocalWindowSize.current.height
    val split = useSplitLayout(widthDp, heightDp)
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    // 诊断日志的开关（原来是 BuildConfig.DEBUG）
    val platform = LocalPlatform.current

    // **左栏宽度**（宽屏/电脑）：分隔条可拖着调，而且是**固定宽度** ——
    // 拉大/缩小窗口时只有画布跟着变，左栏和侧边栏都不动（用户 2026-09-16 要求）。
    //
    //  · 存的是**dp 整数**（不是比例）：比例制会让窗口一变两边一起变，正是要改掉的行为；
    //  · 值落在平台层（电脑上是 `prefs.json`）：拖完下次打开还是这个宽度；
    //  · 用 `MutableIntState` 存**状态对象**：拖拽回调是 `pointerInput` 里捕获的，
    //    捕获值会过期，捕获状态对象每次读都是最新的（见下面的 ColumnSplitter）；
    //  · 手机没有双栏，这个键永远读不到、也写不着。
    val referencePanelDefault = (widthDp.value * 0.196f).roundToInt().coerceIn(320, 400)
    val savedLeftPanelWidth = remember { platform.kv.getInt(KEY_LEFT_PANEL_WIDTH, -1) }
    var leftPanelWidthCustomized by remember { mutableStateOf(savedLeftPanelWidth > 0) }
    val leftPanelWidthDp = rememberSaveable {
        mutableIntStateOf(
            (savedLeftPanelWidth.takeIf { it > 0 } ?: if (referenceSkin) referencePanelDefault else 380)
                .coerceIn(240, 900),
        )
    }
    LaunchedEffect(referenceSkin, widthDp) {
        if (!leftPanelWidthCustomized) {
            leftPanelWidthDp.intValue = if (referenceSkin) referencePanelDefault else 380
        }
    }
    val persistLeftPanelWidth: () -> Unit = {
        platform.kv.edit().putInt(KEY_LEFT_PANEL_WIDTH, leftPanelWidthDp.intValue).apply()
        leftPanelWidthCustomized = true
    }
    /** 左栏最窄 / 画布最少留多宽（免得把任一边拖到看不见）。 */
    val minLeftPanelWidth = if (referenceSkin) 320.dp else 240.dp
    val minCanvasWidth = 320.dp

    // 底部控制面板：Tab（0=提示词 1=角色与分镜 2=参数）
    var panelTab by rememberSaveable { mutableIntStateOf(TAB_PROMPTS) }
    var wideParamsDrawerOpen by rememberSaveable { mutableStateOf(false) }
    // 风格预设面板：开合状态在 `AppState`（用户 2026-09-19 加顶部菜单后，
    // 「文件 → 预设」也要能开它，菜单在外壳那一层够不着这里的 rememberSaveable）
    val showPresetSheet = state.styleSheetOpen
    // 导演台抽屉是否开着（由「导演台」那个方款按钮切换）
    var directorOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.directorShortcutOpen) {
        if (state.directorShortcutOpen) {
            directorOpen = true
            state.directorShortcutOpen = false
        }
    }
    // 「历史提示词」抽屉的开合状态已挪进 `AppState.promptHistoryField`
    // （每个文本框各有一条工具栏，时钟图标不止长在提示词栏里）
    // 遮罩模式下的「重绘参数」折叠区（默认收起，不占图片高度）
    var inpaintParamsOpen by rememberSaveable { mutableStateOf(false) }
    // 面板滑出/滑回的进度（1 = 完全展开）
    val inpaintParamsProgress by animateFloatAsState(
        targetValue = if (state.canvasMode != 2 && state.maskMode && inpaintParamsOpen) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
        label = "inpaintParams",
    )
    // 图生图参数（导入图片后才出现）
    var i2iParamsOpen by rememberSaveable { mutableStateOf(false) }
    val i2iParamsProgress by animateFloatAsState(
        targetValue = if (state.canvasMode != 2 && state.img2imgActive && !state.maskMode && i2iParamsOpen) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
        label = "i2iParams",
    )

    // 「摘要行 + 运行条」的实际高度：预览区要按它留出底部空间，图片才能上下居中
    // 「底部常驻区」的总高 = 把手行 + （2x放大 + 运行条）。
    // 把手行现在挂在面板**上面**，所以要单独量一次再加起来 ——
    // 否则预览区会按偏小的高度给底部留空，图片不居中。
    var peekRowHeightPx by remember { mutableIntStateOf(0) }
    /** 实测的可用视口高度 —— 面板上限的钳制要用（见下面 `maxPanelHeight`）。 */
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    /** 「常驻按钮栏 + 2x放大/导演台 + 运行条」三行的总高（改动前那一整块）。 */
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    /** **不跟着键盘走**的那一组：2x放大/导演台 + 运行条。 */
    var fixedGroupHeightPx by remember { mutableIntStateOf(0) }

    /**
     * 键盘当前占掉的高度（px）。
     *
     * 生成页的键盘**由它自己处理**（壳层对第 0 页不做 imePadding，见 [StudioShell]）：
     * 「把手行 + 抽屉 + 按钮栏」那一块上浮到键盘上方，而「2x放大/导演台 + 运行条」
     * 留在屏幕底部让位给键盘。
     */
    val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)

    val fixedGroupHeight = with(LocalDensity.current) { fixedGroupHeightPx.toDp() }
    val imeBottom = with(LocalDensity.current) { imeBottomPx.toDp() }

    /**
     * 上浮组距离屏幕底边的距离 = **固定组高度**与**键盘高度**里的较大者。
     *
     * - 键盘收起：贴在固定组上方（和改动前一模一样）；
     * - 键盘弹起：贴到键盘上方，而固定组留在屏幕底部（被键盘盖住）。
     */
    val floatLift = maxOf(fixedGroupHeight, imeBottom)

    // ⚠️ 这里不再算「常驻按钮栏」那一行的高度：它已经下放到每个文本框下面、
    // 跟着提示词栏一起滚动（用户 2026-09-19），底栏固定区只剩「把手行 + 2x放大/导演台 + 运行条」。
    val bottomBarHeight = with(LocalDensity.current) {
        (peekRowHeightPx + fixedGroupHeightPx).toDp()
    }
    /** 上浮组里"不包含抽屉面板"的那部分高度（就剩把手行）。 */
    val peekAndActionHeight = with(LocalDensity.current) { peekRowHeightPx.toDp() }

    /**
     * 抽屉面板能长到多高（px→dp 已换算好的钳制值）。`null` = 还没量到视口。
     *
     * 上浮组是从 (屏幕底 - floatLift) 往上长的，顶到屏幕顶为止 ——
     * 所以面板最多只能占「可用高度 - floatLift - 把手行 - 按钮栏」。
     */
    val availableForPanel: Dp? = if (viewportHeightPx > 0) {
        with(LocalDensity.current) {
            (viewportHeightPx - maxOf(fixedGroupHeightPx, imeBottomPx)).toDp()
        } - peekAndActionHeight - 12.dp
    } else {
        null
    }

    /**
     * 普通抽屉面板的上限：**屏幕的 0.6**。
     * 再高就把预览图盖掉太多了（这一层不是给人长时间阅读的，是拿来改词的）。
     */
    val baseMaxPanelHeight = heightDp * 0.6f
    val maxPanelHeight = (availableForPanel?.let { minOf(baseMaxPanelHeight, it) } ?: baseMaxPanelHeight)
        .coerceAtLeast(0.dp)

    /**
     * **剧情放大编辑页**的上限：**屏幕的 0.85**。
     *
     * 比普通面板高一大截是有意的（用户 2026-09-16 授权"根据效果调整大小"）：
     * 这一页干的是一件很不一样的事 —— 单纯把长剧情摊开**读和校对**，
     * 不需要留出预览图，所以能占多少占多少，只留一条把手行 + 状态栏的余量。
     */
    val basePlotEditorHeight = heightDp * 0.85f
    val plotEditorHeight =
        (availableForPanel?.let { minOf(basePlotEditorHeight, it) } ?: basePlotEditorHeight)
            .coerceAtLeast(0.dp)

    // ---- 一体化底部面板：收起时只留「把手 + 摘要 + 运行条」，上滑把面板拉出来 ----
    // 关键是**面板和运行条是上下堆叠的一整块**，面板只盖住图片、绝不盖住运行条 ——
    // 这样改完提示词直接点「生成图片」就行，不用先把抽屉关掉（对齐 NovelAI 手机网页）。
    // 高度只占屏幕的「大半」（0.6），不然面板把画面盖掉太多。
    // 面板上限的钳制见上面（`maxPanelHeight`）。
    val maxPanelHeightPx = with(LocalDensity.current) { maxPanelHeight.toPx() }
    val dragSlopPx = with(LocalDensity.current) { 4.dp.toPx() }
    var panelDragging by remember { mutableStateOf(false) }
    var panelTarget by remember { mutableFloatStateOf(0f) }
    // 本次手势的净位移（+向上 / -向下），单位 = 面板高度比例。
    // 松手收口必须看「这根手指自上次决策以来净拖了多少」，不能拿「收口位置 - 起手位置」算：
    // 起手位置(dragStartFraction)会在手势中途被重置（嵌套滚动的一次内部收口把 panelDragging
    // 归 false，手指还在动时 dragPanel 又跑起来、重新记位），「从顶部起手」的信息就丢了 ——
    // 表现就是面板拉开后怎么拖都收不回（用户反馈 2026-09-15）。
    // 净位移在**手势接管**时清零（panelDrag 的 onStart）、在**每次收口**后也清零，
    // 所以它统计的永远是「上一次决策之后这根手指的净行程」，中途收口也不丢账。
    var dragNetFraction by remember { mutableFloatStateOf(0f) }
    // 【开屏预热】期间让面板**瞬时**开合（不走 spring）—— 见下面的 LaunchedEffect
    var panelPrewarm by remember { mutableStateOf(false) }
    val panelFraction by animateFloatAsState(
        targetValue = panelTarget,
        animationSpec = if (panelDragging || panelPrewarm) {
            snap()
        } else {
            spring(dampingRatio = 0.85f, stiffness = 560f)
        },
        label = "panelFraction",
    )
    val dragPanel: (Float) -> Unit = { dy ->
        panelDragging = true
        val fraction = -dy / maxPanelHeightPx
        panelTarget = (panelTarget + fraction).coerceIn(0f, 1f)
        dragNetFraction += fraction
    }
    /**
     * 手指是否**还按在**拖动区上。
     *
     * ⚠️ 必须跨重组稳定，所以用 `remember { mutableStateOf(...) }` 而不是普通局部 `var`：
     * 嵌套滚动那个 connection 是 `remember` 出来的**单例**，普通 var 会被重组切成两份，
     * 两边看不到同一个值。这里只在手势回调里读写，组合里不读 → 写它不会触发重组。
     */
    val panelPointerDown = remember { mutableStateOf(false) }
    /**
     * 松手收口。**只可能是 0 或 1**，绝不留中间态（中间态就是"抽屉停在半路"）。
     *
     * 判据是这次手势的**净位移方向**，不是"停在哪"：
     *
     *  · **净往上 → 一律开到底。** 必须这样，因为快速轻扫的位移很小
     *    （上滑 200px、面板只跟到 17%），按"停在过半才开"就会弹回去 ——
     *    表现就是"在主页面很难滑出底栏"（用户反馈）。只要净往上、或者上甩，就开。
     *
     *  · **净往下 → 只有"从顶部下来的"才收。** 这正是用户要的
     *    「没到顶部时，这次滑动无论如何都不会收回，只有在顶部时才会收回」：
     *    从底部（本来就收着）往下滑，只是保持收着；从半路往回收，也不会关。
     *
     *  · **没净位移（点一下 / 纯抖动）→ 按当前位置收口**，不看速度。
     *
     * ⚠️ 净位移**不能**用「收口位置 − 起手位置」算：起手位置会在手势中途被重置
     * （嵌套滚动的一次内部收口会让 `panelDragging` 归 false，手指还在动时 `dragPanel`
     * 又跑起来、重新记位），"从顶部起手"的信息就丢了 —— 面板拉开后怎么拖都收不回
     * （用户 2026-09-15 反馈）。现在改成在 dragPanel 里逐帧累加 `dragNetFraction`
     * （接管时清零、每次收口后清零），中途收口也不丢账；"从顶部下来"不再单独判断，
     * 拉得少时直接看**当前位置**收口 —— 位置即事实。
     */
    val settlePanel: (Float) -> Unit = { velocityY ->
        // ⚠️ **手指还按着就不收口**（用户 2026-09-16 反馈：慢速拖动时面板在中间上下跳）。
        //
        // 收口会把 `panelTarget` **钉成 0 或 1**；而嵌套滚动在**手势进行中**也会触发一次
        // 内部收口（`onPostFling`：内容还在甩动时手指按下去 → 甩动被打断 → 回调立刻发一次）。
        // 于是：手指还在慢慢拖，面板却被钉到顶 → 手指把它拖回中间 → 又被钉到顶……
        // 一两次收口就跳一下，看着正是"在中间上下跳"。
        //
        // 收口本来就**只该发生在手指抬起之后**：`onEnd` 会先把 `panelPointerDown` 置 false
        // 再调这里；`onPostFling` 天然是"甩动结束"（手指早已离开）时才发。
        if (panelDragging && !panelPointerDown.value) {
            panelDragging = false
            // 净位移（这根手指自上一次收口/接管以来）：正 = 净往上，负 = 净往下。
            val net = dragNetFraction
            dragNetFraction = 0f
            val movedUp = net.coerceAtLeast(0f)
            val movedDown = (-net).coerceAtLeast(0f)

            val decided = when {
                // ---- 往上（或上甩）：开到底 ----
                movedUp > 0.02f || velocityY < -FLING_VELOCITY -> 1f

                // ---- 往下：只有从顶部下来的才谈得上"收回" ----
                movedDown > 0f -> when {
                    // ① 拉掉够多 → 收
                    movedDown >= 0.25f -> 0f
                    // ② 确实往下带了 + 下甩 → 收（保住"顶部快速下甩关闭"的手感）
                    movedDown >= 0.08f && velocityY > FLING_VELOCITY -> 0f
                    // ③ 拉得不多 → 按**当前位置**收口：还在上半屏 → 弹回，不收回
                    //   （原来这里看 dragStartFraction，但它会被中途收口重置 → 判据失真，
                    //    是"下托收不回"的根因之一。位置即事实，直接看当前位置。）
                    panelTarget > 0.5f -> 1f
                    // ④ 从底部（本来就收着）往下滑 → 保持收着
                    else -> 0f
                }

                // ---- 没动：按停在哪收口 ----
                else -> if (panelTarget > 0.5f) 1f else 0f
            }

            // ⚠️ 这行是**验收用**的：一次手势应该**只收口一次**。
            // 如果用户还报"上下跳"，`adb logcat -s NaiPanel` 看一眼每次手势收了几次、
            // net 和 decided 各是多少，就能定位是"收口还在手势中途发生"还是别的原因。
            if (platform.debugBuild) {
                // 走共用日志出口：手机上仍然是 `adb logcat -s NaiPanel` 那条路（入口装了 Log 后端）
                logInfo("NaiPanel", "settle net=$net vel=$velocityY ${panelTarget}->$decided")
            }

            // 不加"每次打开都回到提示词栏"：用户要求**按上一次关闭时的栏目**恢复。
            // （`panelTab` 是 rememberSaveable，本来就记着上次选的是哪一栏。）
            panelTarget = decided
        }
    }
    // 底栏（把手行 + 运行条）：
    //  · 面板收着 → 只有往上滑才认账（单击生成按钮、横向滑页、往下滚列表都不受影响）；
    //  · 面板开着 → 上下都能认账。**这是"下托收回"的关键**：面板弹出后，最上面那条
    //    「把手 + 摘要」行（DrawerPeekRow，箭头本来就画着朝下）就是用户眼里的把手，
    //    之前它只认上滑，往下拖就被放弃手势 → 怎么拖都收不回（用户反馈 2026-09-15）。
    val bottomBarDrag = Modifier.panelDrag(
        allowDownWhileOpen = { panelTarget > 0.5f },
        slopPx = dragSlopPx,
        onDelta = dragPanel,
        onEnd = settlePanel,
        onStart = { dragNetFraction = 0f },
        onPressChanged = { panelPointerDown.value = it },
    )
    // 面板标题栏：上下都能拖，用来把面板按回去
    val panelHandleDrag = Modifier.panelDrag(
        allowDownWhileOpen = { true },
        slopPx = dragSlopPx,
        onDelta = dragPanel,
        onEnd = settlePanel,
        onStart = { dragNetFraction = 0f },
        onPressChanged = { panelPointerDown.value = it },
    )
    // 面板内容区的嵌套滚动：列表滚到顶之后继续往下拉 → 面板跟着手指往下走；
    // 快速下滑甩动（onPostFling 的 available 速度）→ 直接收起。
    // 这样「在抽屉里任何位置快速下滑」都能关掉，不用非得按标题栏那条把手。
    val panelNestedScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // ⚠️ **上下两个方向都要接**。
                // 早先写的是 `if (available.y <= 0f) return Offset.Zero`（只接往下），
                // 于是"在抽屉里往下拉一半、再往上滑回去"时面板**不会跟着回升**，
                // 但松手时 movedDown 仍然是那么大 → 照样收回。
                // 也就是同一个动作，**快速上滑能救回来、慢速上滑救不回**，手感很怪。
                // 现在面板双向跟手，movedUp / movedDown 才算得准。
                if (available.y == 0f) return Offset.Zero
                dragPanel(available.y)
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                // ⚠️ **无条件收口**。早先写的是 `if (available.y > 0f) settlePanel(...)`，
                // 而慢速下拉（手指拖住不动、没有甩动）结束时 available.y 是 0 →
                // 永远不会收口 → panelTarget 停在中间 →
                // 表现就是"抽屉向下拖会停在半路"（用户反馈）。
                // settlePanel 内部有 `if (panelDragging)` 守卫，所以"内容正常滚动"
                // （没有 overscroll、dragPanel 没被调用过）时这里是空操作。
                settlePanel(available.y)
                return Velocity.Zero
            }
        }
    }

    // 【Tab 即模式】切栏时**当场**把模式同步过去，不等下面那个 LaunchedEffect 的下一帧。
    // 为什么要在回调里也做一次：从「提示词」栏切回「角色」栏的那一帧，界面读到的还是
    // 被压过的 `comicMode=false`，会先闪一下角色列表再变成漫画分镜编辑器。
    val selectTab: (Int) -> Unit = { tab ->
        panelTab = tab
        state.setPromptTabActive(tab == TAB_PROMPTS)
    }

    val openPanel: (Int) -> Unit = { tab ->
        selectTab(tab)
        panelDragging = false
        panelTarget = 1f
    }
    val togglePanel: () -> Unit = {
        panelDragging = false
        // 打开时**不改** panelTab：按上一次关闭时的栏目恢复（用户要求）
        panelTarget = if (panelTarget > 0.5f) 0f else 1f
    }

    // 侧边栏「工具」下拉里点了住在生成页的工具（导演台 / 角色分区）：
    // 外壳负责翻回这一页，这里负责把对应面板打开，然后立刻消费掉请求。
    LaunchedEffect(state.pendingGenerateAction) {
        when (state.pendingGenerateAction) {
            GenerateActions.DIRECTOR -> {
                // ⚠️ 这里**故意不走 openPanel/selectTab**：它只是把抽屉的栏目记忆设成
                // 提示词栏、再立刻把抽屉收起来（下一行 `panelTarget = 0f`），用户并没有
                // 停在那一栏。走 selectTab 会顺带按"Tab 即模式"把模式压成角色模式 ——
                // 对一个只想开导演台的漫画用户，那就是一次莫名其妙的降级。
                panelTab = TAB_PROMPTS
                panelDragging = false
                directorOpen = true
                panelTarget = 0f
                state.consumeGenerateAction()
            }
            GenerateActions.CHARACTERS -> {
                // ⚠️ 角色那一栏**没了** ✗（用户 2026-09-24 ✓）⇒ 打开「提示词」那一栏 ✓
                // —— 角色编辑区就在提示词**下面** ✓，拉到底就能看见 ✓（`PanelTabBody` ✓）。
                openPanel(TAB_PROMPTS)
                state.consumeGenerateAction()
            }
        }
    }

    // ⚠️ **不要在组合体里直接读 `panelFraction`**。
    // 它是逐帧变化的动画值；直接读会让 GenerateScreen 的整个函数体（这个文件两千多行）
    // 每一帧都重跑一遍。下面转成三个 derivedStateOf 布尔量 ——
    // 只有**跨过阈值**的那一帧才会触发重组。
    val panelActive by remember { derivedStateOf { panelDragging || panelFraction > 0.01f } }
    val canvasCovered by remember { derivedStateOf { panelFraction > 0.02f } }
    val peekExpanded by remember { derivedStateOf { panelFraction > 0.5f } }
    // 面板**首次打开之后就常驻组合**，收起时只是高度归零。
    // 早先是纯 `if (panelFraction > 0.002f)`，每次上滑都要把整棵子树
    //（提示词栏好几个输入框 + 风格预设行）从零组合一遍 —— 那一下就是
    // "打开抽屉有点卡"（用户反馈）。用 `|| panelActive` 保证第一帧就组合，
    // 不引入延迟；用 LaunchedEffect 上闩，避免在组合期写状态。
    var panelEverOpened by remember { mutableStateOf(false) }
    LaunchedEffect(panelActive) { if (panelActive) panelEverOpened = true }

    // 【Tab 即模式】用户要求：在「提示词」页点生成图片，就按提示词来、走角色模式。
    //
    // 之前这里有个 bug：漫画模式是存在设置里的**全局**开关，`NaiApi` 组装 payload 时
    // 只读它、根本不管你在哪一栏 —— 于是在「角色」页开过漫画之后，切到「提示词」页
    // 点生成，发出去的还是漫画页。这里把"停在哪一栏"告诉状态层去压模式。
    //
    // ⚠️ **判据用 `peekExpanded` 而不是只看 `panelTab`**：抽屉收着的时候 `panelTab`
    // 仍然记着上次那一栏（用户要求"按上一次关闭时的栏目恢复"），可用户眼下并没有在看
    // 任何一栏。要是那时也按 panelTab 压模式，漫画用户一进 App（panelTab 默认就是
    // 提示词栏）就会被悄悄降级成角色模式 —— 那是个更糟的 bug。
    // 抽屉收起来时这里不动手，模式就留在最后一次拉开抽屉时的判定上。
    // ⚠️ 2026-09-29（用户：「提示词栏和参数栏相互切换**卡顿**」）：`panelTab` 不再当 key ✓ ——
    //    在整页这一层读它，每切一次栏整个生图页都要重组 ✗；改由 `snapshotFlow` 在协程里读 ✓。
    LaunchedEffect(peekExpanded) {
        snapshotFlow { panelTab }.collect { tab ->
        // ⚠️ **预热要把这条挡掉**（用户 2026-09-16）。`setPromptTabActive` 会
        // 「在提示词栏 → 强制角色模式」，而开屏预热是我们自己把抽屉瞬时打开的、
        // 用户并没有选择任何栏目 —— 不拦的话，漫画模式的用户一进 App（抽屉还没碰过）
        // 就被静默降级成角色模式了。预热只借这棵子树的**组合**，不借它的交互语义。
            if (peekExpanded && !panelPrewarm) state.setPromptTabActive(tab == TAB_PROMPTS)
        }
    }

    // 剧情放大编辑页：打开时把抽屉**整个拉开**，否则面板高度是 0、点开什么也看不见
    LaunchedEffect(state.plotEditorOpen) {
        if (state.plotEditorOpen) panelTarget = 1f
    }

    // 外壳按了返回键、而抽屉正拉着 → 收起抽屉（面板开合状态在这一层，外壳够不着）
    LaunchedEffect(state.panelCollapseRequested) {
        if (state.panelCollapseRequested) {
            panelTarget = 0f
            state.consumePanelCollapseRequest()
        }
    }

    // ---- 【开屏预热】趁开屏盖着，把抽屉的**首次组合**做掉 ----
    //
    // 用户报的"冷启动后第一次拉底栏卡"，前几轮已经排掉了每帧成本（拖动期间不挂实时模糊）
    // 和一次性读盘（挪 IO + 错峰让帧），**还是卡** —— 剩下的就是这个：
    // 抽屉子树（TabRow + 三个提示词框 + 权重高亮转换器 + 文字排版）**第一次组合**是一次性的重活，
    // 而它挤在"用户第一次下拉"的那几帧里。
    //
    // 做法：趁 `MainActivity` 的开屏还盖着，把它**瞬时**（snap，不走 spring）打开 → 等几帧让
    // 组合 / 测量 / 文字排版落定 → 再瞬时收回。收回后 `panelEverOpened` 已经上闩，
    // 整棵子树从此**常驻组合**（只是高度归零），用户真正那一拉就只剩纯动画。
    //
    // 用户在开屏后面，看不到这次"预演"。三帧的理由：
    //   第 1 帧组合 + 测量；第 2 帧文字排版落定；第 3 帧留给高亮转换器建缓存。
    LaunchedEffect(state.panelPrewarmRequested) {
        if (!state.panelPrewarmRequested) return@LaunchedEffect
        panelPrewarm = true
        panelTarget = 1f
        repeat(3) { withFrameNanos { } }
        panelTarget = 0f
        // 让"收回"这一帧也走 snap，再撤掉标记（否则收回会走 spring 白跑一段动画）
        withFrameNanos { }
        panelPrewarm = false
        state.markPanelPrewarmed()
    }

    // 面板「在用」= 正在拖动，或者已经拉出来一点。这期间外壳会把侧边栏手势关掉，
    // 免得想调参数时一不小心把侧边栏拖出来。
    // 遮罩模式也一样：涂画时侧边栏和翻页手势都必须让位。
    LaunchedEffect(panelActive, state.maskMode, state.canvasMode) {
        onPanelActiveChange(panelActive || (state.maskMode && state.canvasMode != 2))
        onContentGestureLockChange(state.maskMode && state.canvasMode != 2)
    }

    /**
     * **电脑键盘快捷键**：`Ctrl+Z` 回退提示词改动、`Ctrl+Y`（或 `Ctrl+Shift+Z`）重做。
     *
     * 挂在**根部**而不是靠抢：Compose 的键事件是"先给焦点节点、**没被消费**才往上冒泡"，
     * 所以文本框自己吃掉的那些按键（框内逐字撤销）不会到这里 —— 两种撤销互不打扰。
     * 手机上根本没有 Ctrl 键，这段等于不存在，零副作用。
     *
     * 只有"确实能撤销/重做"时才返回 true（消费掉），否则让按键继续传下去。
     *
     * ## 空格（用户 2026-09-19：「只有按住空格才能拖动图片的设定，在生图页也使用这个」）
     *
     * 画布编辑器里"按住空格 + 左键 = 平移"（见 `CanvasEditor`）现在**生图页的预览也照这个来**：
     * 这里只负责把"空格按着没按着"写进 `state.canvasSpacePan`。
     *
     * ⚠️ **用 `onPreviewKeyEvent` 但返回 false（不吃掉）**：空格必须照常进输入框
     *（在提示词里打空格不受影响），我们要的只是那个状态；真正的平移还得同时按着鼠标，
     * 所以光标在框里打字时这个状态被置上也无害（那种情况下没人能同时拖画布）。
     * 正因为不吃键，也不需要像编辑器那样去判"现在焦点在不在输入框"。
     *
     * ### ⚠️「按空格却进了遮罩模式」的真正原因（用户 2026-09-22 报的，2026-09-22 查清）
     *
     * **不是**有谁把空格绑到 `toggleMaskMode()`（全仓库只有 `CanvasEditor` 和这里两处碰
     * `Key.Spacebar`，没有第三处）。原因在 **Compose 的 `clickable` 自己把空格当"激活"键**：
     *
     *  · `ClickableKt.isPress` = `KeyDown && isEnter(key)`：按下那一下先发一个
     *    `PressInteraction`（按钮显示"按下"），**并且把 KeyDown 吃掉**；
     *  · `ClickableKt.isClick` = **`KeyUp`** `&& isEnter(key)`：**抬起那一下才真的 `onClick`**；
     *  · 而桌面端 `clickable` 走 `FocusableInNonTouchMode` —— **鼠标点过的按钮会一直拿着焦点**。
     *
     * 于是"点一下遮罩开关 → 再按空格想拖图"就变成了"空格又激活了一次那颗开关 → 遮罩模式被切掉/切回"。
     * 那颗「生成图片」按钮同理（空格会顺手发一次生图）、铅笔同理（空格会开画布编辑器）。
     *
     * ### 为什么这里**不**改成"吃掉空格"
     *
     * 吃掉 KeyDown 就等于"输入框里打不出空格"，而本页**并不是每个输入框都登记了焦点状态**
     *（`state.textFieldTarget` 只覆盖提示词 / 角色 / 剧情那几类；参数面板的「文件名前缀」、
     * 临时图库抽屉的搜索框等都没登记），拿它当闸门会漏。只吃 KeyUp 虽然不影响打字，但
     * Compose 的 `PressInteraction` 是**按下**发、**抬起**才 Release —— 抬起被吃掉，
     * 那个按钮会一直挂着"按下"的涟漪不散。
     * 所以改成**从源头掐掉**：画布旁边那几颗按钮**不许拿焦点**（见 `NoButtonFocus`），
     * 它们压根收不到空格；空格就只剩「按住 = 拖图」这一件事。
     */
    val shortcutSpaceKeys = Modifier.onPreviewKeyEvent { event ->
        if (event.key == Key.Spacebar) {
            state.holdCanvasSpace(event.type == KeyEventType.KeyDown)
        }
        false
    }

    val shortcutKeys = Modifier
        .then(shortcutSpaceKeys)
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) return@onKeyEvent false
            val field = activePromptField
            when (event.key) {
                Key.Z -> if (event.isShiftPressed) {
                    if (state.canRedoPrompt(field)) {
                        state.redoPrompt(field)
                        true
                    } else {
                        false
                    }
                } else {
                    if (state.canUndoPrompt(field)) {
                        state.undoPrompt(field)
                        true
                    } else {
                        false
                    }
                }
                Key.Y -> if (state.canRedoPrompt(field)) {
                    state.redoPrompt(field)
                    true
                } else {
                    false
                }
                else -> false
            }
        }

    if (split) {
        Scaffold(
            modifier = Modifier.fillMaxSize().then(shortcutKeys),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            // Reference 只透出 Main.kt 的青 / 靛根背景；Glass 继续使用原主题底，避免回归
            // 之前透明 Scaffold 造成的页面底色消失。显式指定前景色也避免透明容器的默认
            // contentColorFor 回退到黑色，让深色模式的文字与图标保持可读。
            containerColor = if (referenceSkin) Color.Transparent else MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) { inner ->
            // 宽屏**没有**底部那两块覆盖层，躲不了键盘，所以这里自己 imePadding
            // （壳层对生成页不做 imePadding，见 StudioShell 里的说明）。
            //
            // ---- 布局口径 ----
            // ```
            // Box ┌ ① 画布背景：铺满"除侧边栏以外"的整个内容区（棋盘格）
            //     │   图片：让开**停靠着**的面板之后 Fit 居中（面板浮起来之后图也不跳）
            //     ├ ② 中控台（原"提示词栏"）：**默认停靠**在左边、纵向铺满
            //     ├ ③ 底栏：**默认停靠**在下方、横向铺满
            //     └ ④ 分隔条：中控台停靠着的时候才可用（拖它改左栏宽度）
            // ```
            // **默认就是停靠**（用户 2026-09-26）；只有「窗口 → 分离操作面板 → 分离中控台 /
            // 分离底栏」勾上了，那一块才变成**可以拖着走、能拉大小、和别的窗口互相吸附**的窗口
            //（位置/尺寸照旧从盘上恢复，见 `loadPanelLayout`）。画布背景不论哪种状态都铺满整块。
            var rootSize by remember { mutableStateOf(IntSize.Zero) }
            // **面板布局从盘上恢复**（用户 2026-09-19：「移动后每次重启会记住」）
            val savedPanelLayout = remember { loadPanelLayout(platform.kv) }
            // 吸附上报（用户 2026-09-21：「还要和其他窗口吸附啊」）：这几块面板的矩形是报给
            // `AppState.reportPanelRect`、由工具浮动窗口当吸附候选用的（见下面各根节点的
            // `onGloballyPositioned`）。**整个宽屏分支离开组合树**时（窗口被拉窄 → 换窄屏那套）
            // 必须把它们全清掉 —— 否则工具窗口会吸到一堆已经不在画面上的"空气矩形"。
            // 分离面板那几块各自的 `DisposableEffect` 也会清自己那份，这里再兜一遍（幂等）。
            DisposableEffect(Unit) {
                onDispose {
                    state.reportPanelRect(PANEL_RECT_KEY_PROMPT, null)
                    state.reportPanelRect(PANEL_RECT_KEY_BOTTOM, null)
                    state.detachedPanels.forEach { tab ->
                        state.reportPanelRect(detachedPanelRectKey(tab), null)
                    }
                }
            }
            // 「有没有浮起来」= **用户的选择**（用户 2026-09-26：「只有在窗口选项中的分离操控面板中
            // 选择分离底栏/中控台，这底栏和中控台才分离」）：
            //
            //  · **默认（没设置过 / 老设置）= 停靠**：中控台贴左边、纵向铺满；底栏贴下方、横向铺满
            //    —— 就是电脑端本来的那个样子（下面那些 `if (!promptFloating)` 分支就是它）；
            //  · 「窗口 → 分离操作面板 → 分离中控台 / 分离底栏」**勾上**了才是浮动的窗口
            //    （能拖、能拉大小、和别的窗口互相吸附）。
            //
            // ⚠️ 状态住在 `AppState`（菜单那边点、这里读），落盘键 `panels.float.prompt` /
            // `panels.float.bottom`，缺省 false。老版本那两个"永远 true"的键**不再读**（见 AppState）。
            // ⚠️ 这里只在**切换菜单项**时变一次 —— 不是每帧算的，所以停靠态不会抖。
            val promptFloating = state.promptPanelFloating
            val bottomFloating = state.bottomBarFloating
            // 「浮到哪儿了」= **只给 layout 阶段读**的状态（`Modifier.offset { }` 的 lambda 里读）。
            //
            // ⚠️ 2026-09-19 用户报「拖拽不跟手」的根因就在这儿：之前把 `Offset?` 直接读进组合，
            // 于是**每帧位移都会重组整个宽屏分支**（画布、棋盘格、图片、两块面板全部重来一遍），
            // 一帧的活儿太多 → 面板明显落在光标后面。改成"位置只读在 layout lambda 里"之后，
            // 拖动每帧只失效布局，不触发重组。
            val promptOffsetState = remember { mutableStateOf(savedPanelLayout.promptOffset) }
            val bottomOffsetState = remember { mutableStateOf(savedPanelLayout.bottomOffset) }
            // 吸附高亮：这一帧要画哪些"边"（内容格坐标）。
            // 原来是两个布尔（提示词栏 / 底栏"回家"各一个），现在**所有窗口都能互相贴边**，
            // 所以改成一组矩形：竖边是 3dp 宽的竖条、横边是 3dp 高的横条（用户 2026-09-19：
            // 「拖到某个窗口的底部和侧边时会吸附，吸附时窗口的边边会高亮」）。
            var snapHighlights by remember { mutableStateOf<List<Rect>>(emptyList()) }
            // 浮动面板的尺寸（用户 2026-09-19：「拖出来的底栏不能随意调整大小，拉住边缘」）
            // null = 用默认尺寸；非 null = 用户拖过边缘，记 px
            var promptFloatSize by remember { mutableStateOf(savedPanelLayout.promptSize) }
            var bottomFloatSize by remember { mutableStateOf(savedPanelLayout.bottomSize) }
            // 吸附态高度：优先用盘上记的（浮动态启动时量不到，见 KEY_BOTTOM_DOCK_H）
            var bottomBarHeightPx by remember { mutableStateOf(savedPanelLayout.bottomDockHeight) }

            // **菜单「视图 → 重排面板」**（用户 2026-09-19）：把飘出去的两块面板一次收回来。
            // 位置状态都住在这个作用域里，而请求是从外壳的菜单发过来的计数器 —— 监听它。
            // ⚠️ 和 `navigateToGenerateTick` 同一个套路：只认"比上次大"，不会因为重组重放。
            // ⚠️ **必须放在 `persistPanelLayout` 之后**（局部函数只能引用它前面声明的东西），
            // 收回来之后要顺手把"已吸附"这件事也写盘（否则下次启动又把它们摆回飘着的样子）。
            val panelDensity = LocalDensity.current
            /**
             * 底栏窗口的"默认高度"：优先用**盘上记过的吸附态高度**，没有就用兜底
             *（它现在是窗口、永远不贴底渲染，所以 `onSizeChanged` 那条路量不到了 —— 见 [dockBarY] 的说明）。
             */
            val defaultBarHeightPx = if (bottomBarHeightPx > 0) {
                bottomBarHeightPx
            } else {
                with(panelDensity) { BOTTOM_BAR_FALLBACK_HEIGHT.toPx() }.roundToInt()
            }
            val promptWidthDp = leftPanelWidthDp.intValue.dp
            val promptWidthPx = with(panelDensity) { promptWidthDp.toPx() }
            val snapPx = with(panelDensity) { PANEL_SNAP_DP.toPx() }
            val floatPanelHeightPx = minOf(
                with(panelDensity) { PANEL_FLOAT_HEIGHT.toPx() },
                (rootSize.height - 80f).coerceAtLeast(160f),
            )
            val minPanelWidthPx = with(panelDensity) { PANEL_MIN_WIDTH.toPx() }
            val minPanelHeightPx = with(panelDensity) { PANEL_MIN_HEIGHT.toPx() }
            // 浮动面板的实际尺寸：没拖过就用默认值
            val promptFloatSizePx = promptFloatSize
                ?: Size(promptWidthPx, floatPanelHeightPx)
            val bottomFloatSizePx = bottomFloatSize ?: Size(
                // ⚠️ 浮动态默认**比吸附态窄**（用户 2026-09-19：「底栏拖出来时要缩短」）——
                // 拖出来就是一块"小窗口"，想更大就拉右下角那颗三角。
                width = with(panelDensity) { PANEL_FLOAT_WIDTH.toPx() }
                    .coerceAtMost(rootSize.width.toFloat().coerceAtLeast(minPanelWidthPx))
                    .coerceAtLeast(minPanelWidthPx),
                height = defaultBarHeightPx.toFloat().coerceAtLeast(minPanelHeightPx),
            )
            // 拖动时把面板夹在内容区里（留一点在外面，保证把手还抓得到）
            fun clampPanel(o: Offset): Offset = Offset(
                x = o.x.coerceIn(0f, (rootSize.width - 80f).coerceAtLeast(0f)),
                y = o.y.coerceIn(0f, (rootSize.height - 40f).coerceAtLeast(0f)),
            )

            // 分离出来的面板：位置（tab → 偏移）与尺寸（tab → 尺寸）
            val detachedOffsets = remember { mutableStateMapOf<Int, Offset>() }
            val detachedSizes = remember { mutableStateMapOf<Int, Size>() }
            // 盘上存过的位置/尺寸：某一块第一次出现时先摆回原位（用户 2026-09-19 说"移动后重启要记住"）
            LaunchedEffect(state.detachedPanels) {
                state.detachedPanels.forEach { tab ->
                    if (!detachedOffsets.containsKey(tab)) {
                        val savedX = platform.kv.getInt(detachedKey(tab, "x"), Int.MIN_VALUE)
                        if (savedX != Int.MIN_VALUE) {
                            detachedOffsets[tab] = Offset(
                                savedX.toFloat(),
                                platform.kv.getInt(detachedKey(tab, "y"), 0).toFloat(),
                            )
                        }
                    }
                    if (!detachedSizes.containsKey(tab)) {
                        val w = platform.kv.getInt(detachedKey(tab, "w"), 0)
                        val h = platform.kv.getInt(detachedKey(tab, "h"), 0)
                        if (w > 0 && h > 0) detachedSizes[tab] = Size(w.toFloat(), h.toFloat())
                    }
                }
            }
            fun detachedOffsetOf(tab: Int): Offset = detachedOffsets[tab] ?: Offset(
                x = (rootSize.width * 0.18f + tab * 44f).coerceAtLeast(0f),
                y = (rootSize.height * 0.14f + tab * 44f).coerceAtLeast(0f),
            )
            fun detachedSizeOf(tab: Int): Size = detachedSizes[tab] ?: Size(
                width = with(panelDensity) { DETACHED_BOX_WIDTH.toPx() }
                    .coerceAtMost(rootSize.width.toFloat().coerceAtLeast(0f)),
                height = with(panelDensity) { DETACHED_BOX_HEIGHT.toPx() }
                    .coerceAtMost(rootSize.height.toFloat().coerceAtLeast(0f)),
            )
            fun detachedRectNow(tab: Int): Rect {
                val o = detachedOffsetOf(tab)
                val s = detachedSizeOf(tab)
                return Rect(o.x, o.y, o.x + s.width, o.y + s.height)
            }

            // 吸附的实现在 `promptRectNow` / `bottomRectNow` **之后**（见下面 `snapAgainst`）：
            // 它要读那两块面板的矩形，而局部函数只能引用它前面声明的东西。

            /**
             * **把两块面板现在的位置/尺寸写盘**（用户 2026-09-19：「移动后每次重启会记住」）。
             *
             * 只在**拖动结束**、**收回**、**窗口尺寸变化**这几个时刻调 —— 拖动途中每帧写会把
             * `prefs.json` 磨穿（和侧栏宽度那条规矩一样，见 `commitSidebarWidth`）。
             */
            fun persistPanelLayout() {
                val editor = platform.kv.edit()
                    // 这两个键只写不读（见 `SavedPanelLayout` 的说明）：写的是**当时的真值**，
                    // 于是老版本留下的"true"会在第一次写盘时被刷成停靠态的 false，不再误导。
                    .putBoolean(KEY_PROMPT_FLOATING, promptFloating)
                    .putInt(KEY_PROMPT_X, promptOffsetState.value.x.roundToInt())
                    .putInt(KEY_PROMPT_Y, promptOffsetState.value.y.roundToInt())
                    .putInt(KEY_PROMPT_W, promptFloatSizePx.width.roundToInt())
                    .putInt(KEY_PROMPT_H, promptFloatSizePx.height.roundToInt())
                    .putBoolean(KEY_BOTTOM_FLOATING, bottomFloating)
                    .putInt(KEY_BOTTOM_X, bottomOffsetState.value.x.roundToInt())
                    .putInt(KEY_BOTTOM_Y, bottomOffsetState.value.y.roundToInt())
                    .putInt(KEY_BOTTOM_W, bottomFloatSizePx.width.roundToInt())
                    .putInt(KEY_BOTTOM_H, bottomFloatSizePx.height.roundToInt())
                    .putInt(KEY_BOTTOM_DOCK_H, bottomBarHeightPx)
                // 分离出来的那几块框的位置与尺寸也一起记
                state.detachedPanels.forEach { tab ->
                    val o = detachedOffsetOf(tab)
                    val s = detachedSizeOf(tab)
                    editor.putInt(detachedKey(tab, "x"), o.x.roundToInt())
                    editor.putInt(detachedKey(tab, "y"), o.y.roundToInt())
                    editor.putInt(detachedKey(tab, "w"), s.width.roundToInt())
                    editor.putInt(detachedKey(tab, "h"), s.height.roundToInt())
                }
                editor.apply()
            }

            // ---- 「视图 → 重排面板」：把两块面板摆回**默认位置/尺寸**，并写盘 ----
            // （它们现在是窗口，没有"吸附态"可回，所以这里 = 归位 + 清掉用户拉过的尺寸。）
            var handledRedockTick by remember { mutableStateOf(state.panelRedockTick) }
            LaunchedEffect(state.panelRedockTick) {
                if (state.panelRedockTick > handledRedockTick) {
                    handledRedockTick = state.panelRedockTick
                    promptOffsetState.value = Offset.Zero
                    promptFloatSize = null
                    bottomOffsetState.value = defaultBottomOffset(rootSize, promptWidthPx, bottomBarHeightPx)
                    bottomFloatSize = null
                    persistPanelLayout()
                }
            }

            // 底栏那块窗口的**初始落点**：盘上没存过就摆在"跟在提示词栏右边、贴底"
            //（= 它以前吸附态的样子；用户 2026-09-19 起它是个窗口，位置由用户自己摆）。
            var bottomPlaced by remember { mutableStateOf(false) }
            LaunchedEffect(rootSize) {
                if (!bottomPlaced && rootSize.width > 0 && rootSize.height > 0) {
                    bottomPlaced = true
                    if (savedPanelLayout.bottomOffset == Offset.Zero) {
                        bottomOffsetState.value = defaultBottomOffset(
                            rootSize,
                            promptWidthPx,
                            defaultBarHeightPx,
                        )
                    }
                }
            }

            // 窗口被拉小之后，从盘上恢复的坐标可能落在可见范围外 —— 跟着 `rootSize` 夹一次
            //（每帧拖动不写盘，这里只改状态；写完由下面那次"尺寸变了"的写盘收口）。
            LaunchedEffect(rootSize) {
                if (rootSize.width <= 0 || rootSize.height <= 0) return@LaunchedEffect
                val p = clampPanel(promptOffsetState.value)
                if (p != promptOffsetState.value) promptOffsetState.value = p
                val b = clampPanel(bottomOffsetState.value)
                if (b != bottomOffsetState.value) bottomOffsetState.value = b
            }

            // 面板状态写一条日志：`%APPDATA%\NAI Studio\app.log` 里能直接看到
            // "根尺寸 / 有没有浮起来 / 底栏量到多高" —— 拖动/吸附出问题时先看它
            LaunchedEffect(rootSize, promptFloating, bottomFloating, bottomBarHeightPx) {
                com.kallan.naistudio.platform.logInfo(
                    "Panels",
                    "root=${rootSize.width}x${rootSize.height} " +
                        "promptFloating=$promptFloating bottomFloating=$bottomFloating " +
                        "promptAt=${promptOffsetState.value} bottomAt=${bottomOffsetState.value} " +
                        "barH=$bottomBarHeightPx",
                )
            }

            /** 拉边缘改尺寸（绝对定位）：`NaN` 的那个轴不动，其余按"起点 + 指针位移"算。 */
            fun applyResize(startSize: Size, grab: Offset, pointer: Offset): Size = Size(
                width = (if (pointer.x.isNaN()) startSize.width else startSize.width + (pointer.x - grab.x))
                    .coerceIn(minPanelWidthPx, rootSize.width.toFloat().coerceAtLeast(minPanelWidthPx)),
                height = (if (pointer.y.isNaN()) startSize.height else startSize.height + (pointer.y - grab.y))
                    .coerceIn(
                        minPanelHeightPx,
                        rootSize.height.toFloat().coerceAtLeast(minPanelHeightPx),
                    ),
            )

            // ---- 面板交互（拖动 / 拉边缘）：**由内容格自己接收指针** ----
            //
            // ⚠️ 为什么不在把手 / 边缘上挂 pointerInput（2026-09-19 连着踩了三个坑）：
            //  · 「拖不动」：`detectDragGestures` 的 `dragAmount` 是**相对被拖节点**的位移，
            //    而把手就长在会动的面板上 —— 面板一跟着指针走，位移就趋近 0；
            //  · 「乱飞」：换成"量节点窗口位置 + 局部坐标"做绝对定位后，两次测量不同步
            //    （布局与事件各差一帧），误差被写回位置 → 发散；
            //  · 拉边缘同病（热区长在会变大的边上）。
            // 内容格**永远不动**，而且它的坐标系正是"面板偏移"的坐标系，指针坐标可直接用。
            val handlePx = with(panelDensity) { PANEL_HANDLE_HEIGHT.toPx() }
            // 只认右下角那颗三角（`PANEL_RESIZE_CORNER`）；两条边**不再**是热区
            val cornerPx = with(panelDensity) { PANEL_RESIZE_CORNER.toPx() }
            val handleTailPx = with(panelDensity) { 44.dp.toPx() }
            val grab = remember { PanelGrabState() }

            // 提示词栏整体还画不画：
            //  · 「窗口 → 显示提示词栏」关掉 → 不画（见 `state.promptPanelHidden`）；
            //  · **三栏全浮出去了**（用户 2026-09-19：「全部浮动就删除提示词栏」）→ 也不画。
            // 不画的时候画布**不让位**，于是画布铺满那一块。
            val promptColumnVisible = !state.promptPanelHidden &&
                listOf(TAB_PROMPTS, TAB_CHARACTERS, TAB_PARAMS)
                    .any { !state.isPanelDetached(it) }

            // 选中哪一栏 / 显示哪一栏挪进 [DockedPanelTabs] 里读 ✓（2026-09-29 卡顿修复：
            // 在这一层读 `panelTab` ⇒ 切一次栏整个生图页布局都重组 ✗）
            val promptColumnScrollState = rememberScrollState()
            val paramsColumnScrollState = rememberScrollState()

            /**
             * **画布要给"停靠着的"面板让出多少**（用户 2026-09-26：默认就是停靠布局）。
             *
             * 让位 = 图片在**剩余的那块**里 Fit 居中（= 电脑端原来"左栏铺满 + 底栏铺满"的观感）；
             * **浮动**的那块不让位 —— 它是飘在画布上的窗口，图片照旧铺满整块（用户 2026-09-19 的口径）。
             * 藏起来的（「窗口 → 显示 …」取消勾选）也不让位。
             *
             * ⚠️ 底栏的高度是**量出来的**（停靠态由 `onSizeChanged` 写 `bottomBarHeightPx`）：
             * 只读它、不写它，且它只在"高度真的变了"时才变 —— 不会每帧抖。
             */
            val canvasContentPadding = PaddingValues(
                start = if (!promptFloating && promptColumnVisible) promptWidthDp else 0.dp,
                // ⚠️ 用户 2026-09-26：停靠的底栏现在在**左栏正下方**（不在画布那一侧了）✓ ——
                // 所以画布**不再为它让出底部** ✗（原来要减去底栏高度，现在画布是整块高 ✓）。
                bottom = 0.dp,
            )

            fun promptRectNow(): Rect {
                val w = if (promptFloating) promptFloatSizePx.width else promptWidthPx
                val h = if (promptFloating) promptFloatSizePx.height else rootSize.height.toFloat()
                val x = if (promptFloating) promptOffsetState.value.x else 0f
                val y = if (promptFloating) promptOffsetState.value.y else 0f
                return Rect(x, y, x + w, y + h)
            }

            fun bottomRectNow(): Rect {
                val w = if (bottomFloating) {
                    bottomFloatSizePx.width
                } else {
                    // 停靠态：底栏在**左栏正下方**、和左栏同宽（用户 2026-09-26 ✓）
                    promptWidthPx
                }
                val h = if (bottomFloating) {
                    bottomFloatSizePx.height
                } else {
                    bottomBarHeightPx.toFloat()
                }
                val x = if (bottomFloating) bottomOffsetState.value.x else promptWidthPx
                val y = if (bottomFloating) {
                    bottomOffsetState.value.y
                } else {
                    (rootSize.height - bottomBarHeightPx).toFloat()
                }
                return Rect(x, y, x + w, y + h)
            }

            /**
             * **吸附：各个窗口之间贴边**（用户 2026-09-19：「各个窗口加上吸附功能，拖到某个窗口的
             * 底部和侧边时会吸附，吸附时窗口的边边会高亮」）。
             *
             * 横竖两个方向**各自**找最近的贴合边（所以能"贴着 A 的右边、同时贴着窗口底边"）：
             *  · 内容区四条边：左 `x=0` / 右 `x=root-w` / 上 `y=0` / 下 `y=root-h`；
             *  · 其它每个窗口的四条边：贴它左边 / 贴它右边（并排） / 贴它上边 / 贴它下边（上下叠）。
             *
             * 命中就把偏移换成贴合值，并给**被贴的那条边**生成一段 3dp 厚的高亮小条。
             *
             * @param selfKey 正在拖的是谁（`-1` 提示词栏 / `-2` 底栏 / `>=0` 分离出来的那一栏），
             *   用来把自己从候选里排除掉 —— 否则会自己吸自己。
             */
            fun snapAgainst(target: Offset, size: Size, selfKey: Int): Pair<Offset, List<Rect>> {
                val thin = with(panelDensity) { 3.dp.toPx() }
                val targetRect = Rect(target.x, target.y, target.x + size.width, target.y + size.height)

                // ⚠️ 只有**真窗口**参与吸附（内容区四条边不算）—— 否则拖到窗口边上、
                // 附近什么都没有的时候也会"吸"，用户 2026-09-19 报的「在空白的地方也有吸附」就是这个。
                // 另一个必要条件是**垂直方向要有重叠**：贴 A 的右边，只有当两块在竖直方向真的挨着才算，
                // 否则"离得老远也吸一下"看着就像吸到了空气。
                val ex = mutableListOf<Pair<Float, Pair<Float, ClosedFloatingPointRange<Float>>>>()
                val ey = mutableListOf<Pair<Float, Pair<Float, ClosedFloatingPointRange<Float>>>>()

                fun overlaps(a: Float, b: Float, c: Float, d: Float): Boolean =
                    minOf(b, d) - maxOf(a, c) > -snapPx

                fun addRect(r: Rect, key: Int) {
                    if (key == selfKey) return
                    // 竖边：要求竖直方向重叠
                    if (overlaps(targetRect.top, targetRect.bottom, r.top, r.bottom)) {
                        ex += r.left to (r.left to r.top..r.bottom)                     // 贴它左边
                        ex += r.right to (r.right to r.top..r.bottom)                   // 贴它右边
                        ex += (r.left - size.width) to (r.left to r.top..r.bottom)      // 排在它左边
                        ex += (r.right - size.width) to (r.right to r.top..r.bottom)    // 排在它右边
                    }
                    // 横边：要求水平方向重叠
                    if (overlaps(targetRect.left, targetRect.right, r.left, r.right)) {
                        ey += r.top to (r.top to r.left..r.right)
                        ey += r.bottom to (r.bottom to r.left..r.right)
                        ey += (r.top - size.height) to (r.top to r.left..r.right)
                        ey += (r.bottom - size.height) to (r.bottom to r.left..r.right)
                    }
                }
                if (!state.promptPanelHidden) addRect(promptRectNow(), -1)
                if (!state.bottomBarHidden) addRect(bottomRectNow(), -2)
                state.detachedPanels.forEach { tab -> addRect(detachedRectNow(tab), tab) }

                val bestX = ex.filter { abs(it.first - target.x) < snapPx }
                    .minByOrNull { abs(it.first - target.x) }
                val bestY = ey.filter { abs(it.first - target.y) < snapPx }
                    .minByOrNull { abs(it.first - target.y) }

                val highlights = mutableListOf<Rect>()
                bestX?.let { (_, edge) ->
                    highlights += Rect(edge.first - thin / 2f, edge.second.start, edge.first + thin / 2f, edge.second.endInclusive)
                }
                bestY?.let { (_, edge) ->
                    highlights += Rect(edge.second.start, edge.first - thin / 2f, edge.second.endInclusive, edge.first + thin / 2f)
                }
                return Offset(bestX?.first ?: target.x, bestY?.first ?: target.y) to highlights
            }

            // ---- 窗口的**显示优先级（z 序）** ----
            //
            // 用户 2026-09-19：「窗口显示的优先级是点击的窗口，**不要前面的窗口一直占据显示优先级**」——
            // 原来是"谁先声明谁在上面"，于是提示词栏 / 底栏永远压着别人。
            // 现在维护一份顺序表：**谁被点（按下）谁排到最后 = 画在最上面**。
            //
            // 条目标签：`-1` 提示词栏、`-2` 底栏、`>= 0` 分离出来的那一栏（值就是 `TAB_*`）。
            var panelZOrder by remember { mutableStateOf(listOf(-1, -2)) }
            // ⚠️ **新浮出来的那一栏必须进 z 序表**，否则命中测试根本轮不到它 ——
            // 表现就是"浮动显示的窗口不能拖动"（用户 2026-09-19 报的）。
            LaunchedEffect(state.detachedPanels) {
                val missing = state.detachedPanels.filter { it !in panelZOrder }
                if (missing.isNotEmpty()) panelZOrder = panelZOrder + missing
            }
            fun bringToFront(tag: Int) {
                panelZOrder = panelZOrder.filter { it != tag } + tag
            }
            fun zIndexOf(tag: Int): Float = panelZOrder.indexOf(tag).let { if (it < 0) 0f else it + 1f }

            /** 单块面板的命中测试（按标签分派）。⚠️ 必须写在 `beginPanelGesture` **前面**：
             *  局部函数只能引用它前面声明的东西（反过来编译不过）。 */
            fun hitPanel(tag: Int, p: Offset): Boolean {
                // 分离出来的那一栏：右下角那颗三角 = 拉大小，顶部那条把手 = 拖动
                if (tag >= 0) {
                    val r = detachedRectNow(tag)
                    if (p.x in (r.right - cornerPx)..r.right && p.y in (r.bottom - cornerPx)..r.bottom) {
                        grab.detachedTab = tag
                        grab.mode = 3
                        grab.startPointer = p
                        grab.startSize = detachedSizeOf(tag)
                        grab.startOffset = detachedOffsetOf(tag)
                        return true
                    }
                    if (p.y in r.top..(r.top + handlePx) && p.x in r.left..r.right) {
                        // 把手右端那颗"收回"按钮留给它自己点
                        if (p.x <= r.right - handleTailPx) {
                            grab.detachedTab = tag
                            grab.mode = 0
                            grab.startPointer = p
                            grab.startOffset = detachedOffsetOf(tag)
                            return true
                        }
                    }
                    return false
                }

                // 提示词栏（-1）/ 底栏（-2）：右下角那颗三角
                // ⚠️ 边界必须写全（`in .. ..`）：只写"大于右边/下边减 26dp"的话，
                // 面板**右下方的整片区域**都会被判成命中 —— 表现就是"在窗口外点一下也缩放"。
                //
                // ⚠️ 2026-09-19 追加（用户：「在提示词栏与底栏**未浮动**状态下也可以调整窗口的大小」）：
                // **吸附态**也要能拉 —— 提示词栏拉宽（等同那条分隔条）、底栏拉高（它底边贴底，
                // 所以"往上拖 = 变高"，`mode = 5` 里做这个反向映射）。
                if (tag == -1) {
                    val r = promptRectNow()
                    if (p.x in (r.right - cornerPx)..r.right && p.y in (r.bottom - cornerPx)..r.bottom) {
                        if (promptFloating) {
                            grab.panelIsPrompt = true; grab.mode = 3
                            grab.startSize = promptFloatSizePx
                        } else {
                            grab.panelIsPrompt = true; grab.mode = 4
                            grab.startOffset = Offset(leftPanelWidthDp.intValue.toFloat(), 0f)
                        }
                        grab.startPointer = p
                        return true
                    }
                }
                if (tag == -2) {
                    val r = bottomRectNow()
                    if (p.x in (r.right - cornerPx)..r.right && p.y in (r.bottom - cornerPx)..r.bottom) {
                        // ⚠️ **只有浮动态**能拉底栏（用户 2026-09-19：「不能随意改变大小，只能改变宽」）——
                        // 吸附态底栏的高度由内容决定，不给热区。
                        if (bottomFloating) {
                            grab.panelIsPrompt = false; grab.mode = 3
                            grab.startPointer = p; grab.startSize = bottomFloatSizePx
                            return true
                        }
                    }
                }
                // 顶部那条把手：**浮动**时 = 拖动（右端那颗"吸附回原位"按钮留给它自己点）；
                // **停靠**时 = 这一下照样**吃掉**，但什么都不改 —— 只是不让它漏给画布
                //（漏下去的话，"按着中控台/底栏的标题栏拖一下"会变成画布平移，很怪）。
                //
                // ⚠️ 用户 2026-09-26：「只有在窗口选项中的分离操控面板中选择分离底栏/中控台，
                // 这底栏和中控台才分离」—— 所以停靠态的拖动**不再**"顺手把它拖出去"
                //（那是老版本的转浮动态），分离只能从菜单勾。
                // ⛔ 2026-09-29（用户：「提示词栏和参数栏相互切换…**点击不起效**」）：停靠态的标题栏
                //    2026-09-24 已经删了，可这块 30dp 的隐形热区还在 ⇒ 正好压在栏目条上半截，
                //    点「提示词 / 参数」被这里在 Initial 阶段吃掉 ✗。现在**只有浮动态**才有把手区 ✓。
                if (tag == -1 && promptFloating) {
                    val prompt = promptRectNow()
                    if (p.y in prompt.top..(prompt.top + handlePx) && p.x in prompt.left..prompt.right) {
                        val tailStart = if (promptFloating) prompt.right - handleTailPx else prompt.right
                        if (p.x <= tailStart) {
                            grab.panelIsPrompt = true
                            grab.startPointer = p
                            if (promptFloating) {
                                grab.mode = 0
                                grab.startOffset = promptOffsetState.value
                            } else {
                                grab.mode = GRAB_MODE_DOCKED_HANDLE
                            }
                            return true
                        }
                    }
                }
                if (tag == -2 && bottomFloating) {
                    val bottom = bottomRectNow()
                    if (p.y in bottom.top..(bottom.top + handlePx) && p.x in bottom.left..bottom.right) {
                        val tailStart = if (bottomFloating) bottom.right - handleTailPx else bottom.right
                        if (p.x <= tailStart) {
                            grab.panelIsPrompt = false
                            grab.startPointer = p
                            if (bottomFloating) {
                                grab.mode = 0
                                grab.startOffset = bottomOffsetState.value
                            } else {
                                grab.mode = GRAB_MODE_DOCKED_HANDLE
                            }
                            return true
                        }
                    }
                }
                return false
            }

            /** 命中测试：这一下按在哪个面板的哪个部位（**从最上面那块开始试**）。 */
            fun beginPanelGesture(p: Offset): Boolean {
                // ⚠️ **按 z 序从上往下试** —— 否则叠在一起时点到的是下面那块。
                // 藏起来的面板（「窗口 → 显示 …」）不参与命中。
                for (tag in panelZOrder.asReversed()) {
                    if (tag == -1 && state.promptPanelHidden) continue
                    if (tag == -2 && state.bottomBarHidden) continue
                    if (!state.detachedPanels.contains(tag) && tag >= 0) continue
                    if (hitPanel(tag, p)) {
                        bringToFront(tag)
                        return true
                    }
                }
                return false
            }

            fun updatePanelGesture(p: Offset) {
                val delta = p - grab.startPointer
                // 停靠态按住了面板的标题栏：这一下只为**吃掉指针**（别漏给画布），什么都不改
                if (grab.mode == GRAB_MODE_DOCKED_HANDLE) return
                // 分离出来的那块：拖动 / 拉右下角（和其他窗口互相吸附）
                val detached = grab.detachedTab
                if (detached != null) {
                    if (grab.mode == 3) {
                        val size = applyResize(
                            grab.startSize,
                            grab.startPointer,
                            p,
                        )
                        detachedSizes[detached] = size
                        snapHighlights = emptyList()
                        return
                    }
                    val size = detachedSizeOf(detached)
                    val (snapped, highlights) = snapAgainst(grab.startOffset + delta, size, detached)
                    snapHighlights = highlights
                    // 吸附命中时**直接用贴合值**（视觉上就是"吸上去"）；没命中才夹进可见范围
                    detachedOffsets[detached] = if (highlights.isEmpty()) clampPanel(snapped) else snapped
                    return
                }
                if (grab.mode == 0) {
                    // 用户 2026-09-19：「底栏和提示词栏做成窗口吧，**只做窗口间吸附**」——
                    // 这两块不再"回家"（原来提示词栏回左上角、底栏回整条底部），
                    // 只和**其它窗口**贴边；贴边命中就直接吸上去。
                    val size = if (grab.panelIsPrompt) promptFloatSizePx else bottomFloatSizePx
                    val selfKey = if (grab.panelIsPrompt) -1 else -2
                    val (snapped, highlights) = snapAgainst(grab.startOffset + delta, size, selfKey)
                    snapHighlights = highlights
                    val next = if (highlights.isEmpty()) clampPanel(snapped) else snapped
                    if (grab.panelIsPrompt) promptOffsetState.value = next else bottomOffsetState.value = next
                } else if (grab.mode == 4) {
                    // 吸附态的提示词栏：**只改宽**（和那条分隔条同一个值、同一套边界）
                    snapHighlights = emptyList()
                    val maxW = (widthDp - minCanvasWidth).coerceAtLeast(minLeftPanelWidth)
                    leftPanelWidthDp.intValue = (grab.startOffset.x + delta.x)
                        .coerceIn(minLeftPanelWidth.value, maxW.value)
                        .roundToInt()
                } else {
                    // 拉尺寸时不该亮吸附边
                    snapHighlights = emptyList()
                    val size = applyResize(
                        grab.startSize,
                        Offset.Zero,
                        Offset(
                            x = if (grab.mode == 2) Float.NaN else delta.x,
                            y = if (grab.mode == 1) Float.NaN else delta.y,
                        ),
                    )
                    if (grab.panelIsPrompt) promptFloatSize = size else bottomFloatSize = size
                }
            }

            fun endPanelGesture() {
                snapHighlights = emptyList()
                // 停靠态按住标题栏那一下：没有状态要收口，也**不写盘**（什么都没动）
                if (grab.mode == GRAB_MODE_DOCKED_HANDLE) {
                    grab.mode = 0
                    return
                }
                // 分离出来的那块：松手就记一次（和那两块一个口径）
                if (grab.detachedTab != null) {
                    grab.detachedTab = null
                    persistPanelLayout()
                    return
                }
                if (grab.mode == 0) {
                    // 只记一条日志（原来这里还有"拖回原位就吸附回去"的分支 —— 用户 2026-09-19：
                    // 「只做窗口间吸附」，所以**不再回家**，停在哪儿就是哪儿）
                    com.kallan.naistudio.platform.logInfo(
                        "Panels",
                        if (grab.panelIsPrompt) {
                            "prompt drag end=${promptOffsetState.value}"
                        } else {
                            "bottom drag end=${bottomOffsetState.value}"
                        },
                    )
                }
                // 松手就记一次（**吸附也好、停在外头也好**，都是"用户摆好的样子"）
                persistPanelLayout()
            }

            val onPanelDown by rememberUpdatedState<(Offset) -> Boolean>({ beginPanelGesture(it) })
            val onPanelMove by rememberUpdatedState<(Offset) -> Unit>({ updatePanelGesture(it) })
            val onPanelUp by rememberUpdatedState<() -> Unit>({ endPanelGesture() })

            Box(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(inner)
                    .onSizeChanged { rootSize = it }
                    // 面板拖动 / 拉边缘统一在这里接收（内容格**永远不动**，见上面那段注释）
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            // Initial 阶段拿按下事件：这样"这一下属于面板"时能把**按下本身**也吃掉，
                            // 免得画布那几条手势（拖动平移 / 涂遮罩）跟着起手
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            if (down.type != PointerType.Mouse) return@awaitEachGesture
                            if (!onPanelDown(down.position)) return@awaitEachGesture
                            down.consume()
                            while (true) {
                                // ⚠️ 用 **Initial 阶段**：它在"子节点（画布）的 Main 阶段"**之前**跑，
                                // 于是我们能在画布拿到事件之前就 consume 掉 —— 否则面板和图片叠在一起时，
                                // 画布那条"鼠标拖动平移"会照旧执行，两者一起动（用户 2026-09-19 报的）。
                                // 画布那边也补了 `isConsumed` 判断（双保险）。
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                onPanelMove(change.position)
                                change.consume()
                            }
                            onPanelUp()
                        }
                    },
            ) {
                // ---- 吸附高亮：拖到贴合半径内时点亮**被贴的那条边** ----
                // 画在面板**下面**、画布**上面**：只做提示，不吃指针（没有 pointerInput）。
                val snapColor = MaterialTheme.colorScheme.primary
                snapHighlights.forEach { r ->
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(r.left.roundToInt(), r.top.roundToInt())
                            }
                            .size(
                                with(panelDensity) { r.width.toDp() },
                                with(panelDensity) { r.height.toDp() },
                            )
                            .background(snapColor.copy(alpha = 0.55f)),
                    )
                }

                // ---- ① 画布：背景铺满整块；图片让开吸附态的面板 ----
                // ⚠️ **画布模式切换放在这一层**（不在 `PreviewCard` 里面 ✓）——
                //    切到"无限画布"之后 `PreviewCard` 根本不画了 ✗，开关要是长在它里面，
                //    用户就**切不回来**了 ✓（这是最容易犯的那种"自己把自己锁住"的 UI bug ✗）。
                //
                // ⛔ **三档必须共用同一块画布区** ✗✗（用户 2026-09-23：「**无限模式和普通的画布位置要共通**」✓）：
                //    普通档把图片让开了停靠着的中控台（`canvasContentPadding` ✓），
                //    而无限画布 / 漫画这两档原来是**整块铺满** ✗ ⇒ 一切档，画布"位置就跳了" ✓，
                //    用户第一眼就是"这俩不是同一个地方" ✗。
                //    ⇒ 这三档**同一层 Box、同一个 padding** ✓，档位只换里面的内容 ✓。
                val canvasAreaModifier = Modifier
                    .fillMaxSize()
                    .padding(canvasContentPadding)
                Box(Modifier.fillMaxSize()) {
                    // 参考风格的环境网格铺满侧栏以外的画布区域；各画布内容在默认透明底下
                    // 不重复绘制网格，自选画布色则使用不透明填充并保留自己的网格。
                    if (referenceSkin) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(start = promptWidthDp)
                                .referenceGrid(
                                    dark = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                                    palette = state.settings.palette,
                                ),
                        )
                    }
                    when (state.canvasMode) {
                        // 1 = 无限画布（用户 2026-09-22 点的单 ✓，方案 `docs/69` ✓）
                        1 -> {
                            // 进这一档时才铺画布：手上已经有图 ⇒ 直接当画布底 ✓；
                            // 还没有图 ⇒ 先给一张空白（等生图完再替换 ✓）。
                            // ⚠️ 走的是 [AppState.syncSharedCanvas] ✗ 不是"重建" ✓ ——
                            //    普通那一档已经把画布备好了 ✓，到这里就是**同一张** ✓（切档 = 只换 UI ✓）；
                            //    "工作图换了新的一张"才换画布内容 ✓（见那个函数的护栏 ✓）。
                            LaunchedEffect(state.canvasMode) { state.syncSharedCanvas() }
                            // **同一块画布底**（用户 2026-09-24：「统一画布背景…」✓）——
                            // 原来这一档**什么都不铺** ✗（露的是页面底色）⇒ 切档背景就变 ✓。
                            val infiniteCanvasColor = canvasBaseColor(state.settings.canvasColor)
                            Box(
                                canvasAreaModifier
                                    .canvasBackdrop(
                                        infiniteCanvasColor,
                                        drawGrid = !referenceSkin ||
                                            infiniteCanvasColor != ReferenceCanvasColor,
                                    ),
                            ) {
                                InfiniteCanvasArea(
                                    state = state,
                                    t = t,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }

                        // Comic mode replaces the layout with streamed frames, then the saved page image.
                        2 -> {
                            val preview = state.generationPreview.takeIf { state.comicCanvasPreviewRunning }
                            val generatedPath = state.comicCanvasImagePath()
                            when {
                                preview != null -> Image(
                                    bitmap = preview,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = canvasAreaModifier,
                                )
                                generatedPath != null -> FileImage(
                                    generatedPath,
                                    modifier = canvasAreaModifier,
                                )
                                state.comicCanvasPreviewRunning -> Box(
                                    canvasAreaModifier,
                                    contentAlignment = Alignment.Center,
                                ) { CircularProgressIndicator() }
                                else -> StoryboardLayoutPreview(state, canvasAreaModifier)
                            }
                        }

                        else -> PreviewCard(
                            state = state,
                            t = t,
                            modifier = canvasAreaModifier
                                .blur(radius = NaiGlass.blurRadius * inpaintParamsProgress),
                            canvasGestures = true,
                            // ⚠️ **这里传空** ✗（2026-09-24 改 ✓）：让位由**外面那层** `canvasAreaModifier`
                            // 统一给 ✓（三档共用 ✓）。以前这里又传了一份 ⇒ `PreviewCard` 内部**再让一次位** ✗
                            // ⇒ 图片那块比无限画布那块**小一圈** ✓ ⇒ 同一个 `scale` 下两档的图不一样大 ✓
                            //（用户一直觉得"切档就换了张画布" ✓，这也是其中一条 ✓）。
                            // 现在两档的可用区**逐像素相同** ✓ ⇒ `ContentScale.Fit` 算出来的 fit 也相同 ✓。
                            contentPadding = PaddingValues(),
                        )
                    }
                    // 用户 2026-09-22：「**在生图页的左上角，画布左上角加切换按钮：普通 / 无限画布 / 漫画**」✓
                    // ⚠️ **这一份是刚需，不是重复** ✗：用户 2026-09-22 反馈「**没看到无限画布的按钮**」✓ ——
                    //    右边栏那一份（`ControlPanel` 里 ✓）埋在栏目条下面，中控台一旦被分离 / 收起就彻底看不见 ✓；
                    //    画布左上角这份**永远在**（和手机线同一处 ✓）。两处都通同一个 `state.canvasMode` ✓，
                    //    不会打架 ✓。左边让开提示词栏那一栏（和遮罩工具条同一个 `promptWidthDp` ✓）。
                    CanvasModeSwitcher(
                        t = t,
                        mode = state.canvasMode,
                        onMode = state::chooseCanvasMode,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = promptWidthDp + if (referenceSkin) 14.dp else 0.dp,
                                top = if (referenceSkin) 14.dp else 8.dp,
                            ),
                    )
                    if (state.img2imgActive && !state.maskMode && state.canvasMode != 2) {
                        I2iParamsTrigger(
                            t = t,
                            expanded = i2iParamsOpen,
                            onToggle = { i2iParamsOpen = !i2iParamsOpen },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = promptWidthDp + 12.dp, top = 54.dp)
                                .width(350.dp),
                        )
                    }
                    if (i2iParamsProgress > 0.001f) {
                        I2iParamsPanel(
                            state = state,
                            t = t,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = promptWidthDp + 12.dp, top = 84.dp)
                                .width(350.dp)
                                .graphicsLayer {
                                    translationY = -size.height * (1f - i2iParamsProgress)
                                    alpha = i2iParamsProgress
                                },
                        )
                    }
                    if (inpaintParamsProgress > 0.001f) {
                        InpaintParamsPanel(
                            state = state,
                            t = t,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .graphicsLayer {
                                    translationY = -size.height * (1f - inpaintParamsProgress)
                                    alpha = inpaintParamsProgress
                                },
                        )
                    }
                }

                // ---- ①b 画布编辑器：**就地盖在画布这一块上**（用户 2026-09-26）----
                // 「画布编辑器不隐藏中控和底栏，直接显示画布编辑器的 UI，和遮罩模式一样」——
                // 所以不再由外壳整页替换 ✗，而是和遮罩工具条同一个口径：画在画布区里，
                // 左边让开中控台那一栏，中控台 / 底栏照旧能用 ✓。
                if (state.canvasEditorOpen) {
                    // ⚠️ 包一层 Box 只为了**让开中控台那一栏**（编辑器自己没有 modifier 参数，
                    // 见 `CanvasEditorScreen` 的签名 —— 别硬塞一个进去 ✗）。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(start = promptWidthDp),
                    ) {
                        CanvasEditorScreen(
                            state = state,
                            t = t,
                            onCancel = { state.closeCanvasEditor(commit = false) },
                            onCommit = { state.closeCanvasEditor(commit = true) },
                        )
                    }
                }

                // ---- ② 遮罩工具条：吸附态下贴在顶部（从提示词栏右边开始）----
                if (state.maskMode && state.canvasMode != 2) {
                    Column(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = promptWidthDp)
                            .fillMaxWidth(),
                    ) {
                        MaskToolBar(state, t)
                        InpaintParamsTrigger(
                            t = t,
                            expanded = inpaintParamsOpen,
                            onToggle = { inpaintParamsOpen = !inpaintParamsOpen },
                        )
                    }
                }

                // ---- ③ 提示词栏：玻璃面板，可拖走 / 拖回吸附 ----
                // `alpha` 由「窗口 → 显示提示词栏」控制（藏起来时连同它的命中一起让开，
                // 见 `beginPanelGesture` 里对隐藏面板的跳过）；`zIndex` 跟着点击顺序走。
                //
                // 吸附上报（用户 2026-09-21：「还要和其他窗口吸附啊」）：这块的根节点把
                // **窗口客户区坐标**下的矩形报给 `state.reportPanelRect`，工具浮动窗口拿它当
                // 吸附候选（`boundsInWindow()` 给的正是窗口客户区坐标 → 和工具窗口那套同源，
                // 两边**不差任何偏移**，吸的时候不用换算）。
                // ⚠️ 这里是用 `alpha` 隐藏的（节点还在布），所以"不可见"时必须报 `null` 把
                // 那枚矩形**删掉**，否则工具窗口会吸到一块看不见的面板（= 吸到空气）。
                // ⚠️ 光靠 `onGloballyPositioned` 不够：`alpha` 翻转时位置没变，它不一定再回调。
                // 所以把坐标**缓存**一份，可见性一变就拿缓存重报 / 清除 —— 藏起来、再放出来
                // 两个方向都能对上（只清不重报的话，放出来之后这块就再也吸不上了）。
                val promptCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
                LaunchedEffect(promptColumnVisible) {
                    val c = promptCoords.value
                    state.reportPanelRect(
                        PANEL_RECT_KEY_PROMPT,
                        if (promptColumnVisible && c != null) c.boundsInWindow() else null,
                    )
                }
                Box(
                    Modifier
                        .zIndex(zIndexOf(-1))
                        .alpha(if (promptColumnVisible) 1f else 0f)
                        .align(Alignment.TopStart)
                        // 位置只在 layout 阶段读（`offset { }` 的 lambda）→ 拖动时**不重组**。
                        // ⚠️ **停靠态一律不偏移**：盘上记的是"浮动时把它拖到哪儿了"，停靠时用不上它
                        //（用了会让贴左的中控台被推走、和底栏对不齐）；重新勾上分离时那份位置还在 ✓。
                        .offset {
                            if (promptFloating) {
                                IntOffset(
                                    promptOffsetState.value.x.roundToInt(),
                                    promptOffsetState.value.y.roundToInt(),
                                )
                            } else {
                                IntOffset.Zero
                            }
                        }
                        .then(
                            if (!promptFloating) {
                                // ⚠️ 用户 2026-09-26：**停靠时底栏挪到这一栏的正下方**（同宽）✓ ——
                                // 所以中控台不再"纵向铺满"，要给底下那条留出它的高度 ✗（原来是 `fillMaxHeight()`）。
                                // 高度 = 内容区高 − 停靠底栏的实测高（藏起来时就是整个高 ✓）。
                                Modifier
                                    .width(promptWidthDp)
                                    .height(
                                        with(panelDensity) {
                                            (
                                                rootSize.height.toDp() -
                                                    if (!referenceSkin && !bottomFloating && !state.bottomBarHidden) {
                                                        // ⚠️ 多扣一条缝：两块各自的圆角 + 缝才看得出是两扇窗口 ✓
                                                        //（用户 2026-09-26：「两块粘成一条、没分界」）
                                                        (bottomBarHeightPx.toDp() + DOCK_PANEL_GAP).coerceAtLeast(0.dp)
                                                    } else {
                                                        0.dp
                                                    }
                                                ).coerceAtLeast(0.dp)
                                        },
                                    )
                            } else {
                                Modifier.size(
                                    with(panelDensity) { promptFloatSizePx.width.toDp() },
                                    with(panelDensity) { promptFloatSizePx.height.toDp() },
                                )
                            },
                        )
                        // 排在 `offset` / `size` **之后**：报出去的是摆好、量好之后的矩形
                        .onGloballyPositioned { coords ->
                            promptCoords.value = coords
                            if (promptColumnVisible) {
                                state.reportPanelRect(
                                    PANEL_RECT_KEY_PROMPT,
                                    coords.boundsInWindow(),
                                )
                            }
                        }
                        // 停靠态：**只圆上面两个角** ✓ —— 下面那两条边要和底栏拼在一起（融合 ✓）
                        .clip(
                            if (referenceSkin && !promptFloating) {
                                RoundedCornerShape(0.dp)
                            } else if (!promptFloating) {
                                RoundedCornerShape(
                                    topStart = DOCK_PANEL_CORNER,
                                    topEnd = DOCK_PANEL_CORNER,
                                )
                            } else {
                                RoundedCornerShape(14.dp)
                            },
                        )
                        // **实体底色**（用户 2026-09-19：「提示词栏和底栏变为回退回实体底色，其余不变」）：
                        // 毛玻璃那版要把背后的壁纸/画布透出来，现在改回不透明的 `surface`
                        // —— 于是 `glassBackdrop()` 也没必要了（不透明底会把壁纸层整个盖住）。
                        .background(
                            LocalPanelBackgroundColor.current
                                ?: if (referenceSkin) NaiSkinTokens.panel() else MaterialTheme.colorScheme.surface,
                        ),
                ) {
                    Column(Modifier.fillMaxSize()) {
                    // ⛔ **中控台那条标题栏（小字「中控」+ 抓手点阵）与右下角那颗"可以拉"的记号
                    //    都删掉了** ✗（用户 2026-09-24：「**中控和底栏的标题和拖动点删掉，
                    //    以后不需要拖动了**」✓）。理由：分离功能整个去掉之后（`docs/81` ✓），
                    //    这两块面板**固定停靠** ✓ ⇒ 标题（"这是一块能拖走的窗口"的标识 ✓）
                    //    和那两个拖动点都是**没有对应功能的装饰** ✗ —— 正是本仓库最忌讳的假控件 ✓。
                    //  · `PanelDragHandle` 这个 composable 本身留着 ✓（分离出来的那块框还在用 ✓，
                    //    它现在**永远不会被组合到** ✓ —— 见 `AppState.promptPanelFloating` 恒 false ✓）。
                    //  · 面板的**宽度**拖动不受影响 ✓（那是内容格那条指针处理，不是这里的把手 ✓）。
                    DockedPanelTabs(
                        state = state,
                        t = t,
                        selectedTab = { TAB_PROMPTS },
                        onSelectTab = { },
                        promptScroll = promptColumnScrollState,
                        paramsScroll = paramsColumnScrollState,
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            end = if (referenceSkin) 14.dp else 10.dp,
                            top = if (referenceSkin) 14.dp else 12.dp,
                            bottom = if (referenceSkin && !bottomFloating && !state.bottomBarHidden) {
                                with(panelDensity) { defaultBarHeightPx.toDp() } + 14.dp
                            } else {
                                12.dp
                            },
                        ),
                        spacing = if (referenceSkin) 12.dp else 8.dp,
                        activeField = activePromptField,
                        onFieldFocus = { activePromptField = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                    }
                    // ⛔ 中控台右下角那颗"可以拉"的记号也删了 ✓（用户 2026-09-24：
                    //    「中控和底栏的标题和**拖动点**删掉，以后不需要拖动了」✓）
                }

                // 宽屏参数页从提示词栏右边沿向画布侧拉出，保留提示词栏在原位。
                // 入口放在面板裁切范围外，成为贴着侧栏右边的小凸起。
                if (promptColumnVisible && !promptFloating && !state.isPanelDetached(TAB_PARAMS)) {
                    val paramsDrawerWidth = with(panelDensity) {
                        (widthDp - promptWidthDp).coerceIn(260.dp, 360.dp)
                    }
                    // 抽屉从提示词栏边缘滑入时，同步带动入口到抽屉外沿；
                    // 这样展开后入口仍贴着参数页，不会停在左边压住参数内容。
                    val paramsEdgeOffsetX by animateDpAsState(
                        targetValue = if (wideParamsDrawerOpen) paramsDrawerWidth else 0.dp,
                        animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
                        label = "paramsEdgeTriggerOffset",
                    )
                    val paramsTabCenterOffsetY = with(panelDensity) {
                        if (!referenceSkin && !bottomFloating && !state.bottomBarHidden) {
                            -((bottomBarHeightPx.toDp() + DOCK_PANEL_GAP).coerceAtLeast(0.dp)) / 2
                        } else 0.dp
                    }
                    ParamsEdgeTrigger(
                        t = t,
                        selected = wideParamsDrawerOpen,
                        onClick = { wideParamsDrawerOpen = !wideParamsDrawerOpen },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = promptWidthDp - 1.dp + paramsEdgeOffsetX, y = paramsTabCenterOffsetY)
                            .zIndex(zIndexOf(-1) + 0.1f),
                    )
                    DesktopParamsDrawer(
                        state = state,
                        t = t,
                        visible = wideParamsDrawerOpen,
                        onClose = { wideParamsDrawerOpen = false },
                        onOpenPresets = { state.openStyleSheet() },
                        activeField = activePromptField,
                        onFieldFocus = { activePromptField = it },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = promptWidthDp - 1.dp, y = 6.dp)
                            .padding(
                                bottom = with(panelDensity) {
                                    if (!referenceSkin && !bottomFloating && !state.bottomBarHidden) {
                                        (bottomBarHeightPx.toDp() + DOCK_PANEL_GAP).coerceAtLeast(0.dp)
                                    } else 12.dp
                                },
                            )
                            .width(paramsDrawerWidth)
                            .fillMaxHeight()
                            .zIndex(zIndexOf(-1) + 0.05f),
                    )
                }

                // ---- ③b 剧情放大窗口：贴在提示词栏**右边**（用户 2026-09-19）----
                //
                // 窄屏那版是"把整个抽屉换成大文本框"，宽屏没有抽屉可换，所以在这儿弹一扇窗口：
                // 左边从提示词栏右缘再让 10dp 开始，上面留到顶，**下面停在底栏上方**
                // （不让它压住那条底栏 —— 拖动把手/生成按钮都还在那儿）。
                // 宽度取"画布区能给的"和 560dp 里的小的一个，窄窗口下也不会溢出。
                if (state.plotEditorOpen) {
                    val plotWindowWidth = with(panelDensity) {
                        (rootSize.width.toDp() - promptWidthDp - 20.dp).coerceAtMost(560.dp)
                    }
                    PlotEditorPanel(
                        state = state,
                        t = t,
                        onClose = { state.closePlotEditor() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = promptWidthDp + 10.dp,
                                top = 10.dp,
                                bottom = with(panelDensity) { bottomBarHeightPx.toDp() } + 10.dp,
                            )
                            .width(plotWindowWidth)
                            .fillMaxHeight(),
                        color = LocalPanelBackgroundColor.current ?: MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                    )
                }

                // ---- ③c 分离出来的面板：**在窗口里飘的一块框**（用户 2026-09-19：
                //      「窗口分离功能就是像底栏一样多出一个可以在窗口内拖动的框」）----
                // 和那两块一样：位置只在 layout 阶段读（`.offset { }`），拖动每帧不重组；
                // 拖动/命中测试同样由**内容格那层**统一接管（见 `beginPanelGesture`）。
                state.detachedPanels.forEach { tab ->
                    val o = detachedOffsetOf(tab)
                    val s = detachedSizeOf(tab)
                    // 吸附上报（用户 2026-09-21：「还要和其他窗口吸附啊」）：这块框的**窗口客户区
                    // 坐标**矩形报给 `state.reportPanelRect`，工具浮动窗口拿它当吸附候选。
                    // ⚠️ 收回 / 不再分离时这个组合位置会**直接离开组合树**，`onGloballyPositioned`
                    // 不会再回调 —— 所以必须靠 `onDispose` 把 key 删掉，不然会留下一枚过期矩形
                    //（工具窗口吸到一块已经不在那儿的框上 = 吸到空气）。
                    DisposableEffect(tab) {
                        onDispose { state.reportPanelRect(detachedPanelRectKey(tab), null) }
                    }
                    Box(
                        Modifier
                            .zIndex(zIndexOf(tab))
                            .align(Alignment.TopStart)
                            .offset { IntOffset(o.x.roundToInt(), o.y.roundToInt()) }
                            .size(
                                with(panelDensity) { s.width.toDp() },
                                with(panelDensity) { s.height.toDp() },
                            )
                            // 排在 `offset` / `size` 之后：报出去的是摆好、量好之后的矩形
                            .onGloballyPositioned { coords ->
                                state.reportPanelRect(
                                    detachedPanelRectKey(tab),
                                    coords.boundsInWindow(),
                                )
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .background(LocalPanelBackgroundColor.current ?: MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(14.dp),
                            ),
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            PanelDragHandle(
                                label = t(panelTabTitleKey(tab)),
                                redockLabel = t("window.detachBack"),
                                floating = true,
                                onRedock = { state.closeDetachedPanel(tab) },
                            )
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PanelTabBody(
                                    tab = tab,
                                    state = state,
                                    t = t,
                                    onOpenPresets = { state.openStyleSheet() },
                                    activeField = activePromptField,
                                    onFieldFocus = { activePromptField = it },
                                    // 它**就是**那块面板本体，别又画一次"已在独立窗口中打开"的占位
                                    skipDetachedPlaceholder = true,
                                )
                            }
                        }
                        // 「可以拉大小」的记号（指针处理在内容格那层，见 beginPanelGesture 第 0 步）
                        PanelResizeMark(modifier = Modifier.align(Alignment.BottomEnd))
                    }
                }

                // ---- ④ 分隔条：提示词栏吸附、而且**确实画着**时才可用（拖它改面板宽度）----
                if (!promptFloating && promptColumnVisible) {
                    ColumnSplitter(
                        widthDp = leftPanelWidthDp,
                        contentWidth = widthDp,
                        minWidth = minLeftPanelWidth,
                        minOtherWidth = minCanvasWidth,
                        onCommit = persistLeftPanelWidth,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(x = promptWidthDp),
                    )
                }

                // ---- ⑤ 底栏：玻璃面板，可拖走 / 拖回吸附 ----
                // 吸附上报（用户 2026-09-21：「还要和其他窗口吸附啊」）：同提示词栏 —— 把
                // **窗口客户区坐标**下的矩形报给 `state.reportPanelRect`；它也是用 `alpha`
                // 藏起来的（节点还在布），所以不可见时报 `null`，可见性翻转时拿缓存的坐标重报 /
                // 清除（理由同上：只清不重报会在"藏起来再放出来"之后漏掉吸附）。
                val bottomCoords = remember { mutableStateOf<LayoutCoordinates?>(null) }
                LaunchedEffect(state.bottomBarHidden) {
                    val c = bottomCoords.value
                    state.reportPanelRect(
                        PANEL_RECT_KEY_BOTTOM,
                        if (!state.bottomBarHidden && c != null) c.boundsInWindow() else null,
                    )
                }
                Box(
                    Modifier
                        // ⚠️ 用户 2026-09-26：「中控台在不分离的情况下还是会盖住底栏」——
                        // 根因是**两者的 z 序**：中控台是 `zIndexOf(-1)`、底栏是 `zIndexOf(-2)` ✗，
                        // 于是只要中控台的内容比它那一格高一点点（内容不裁剪会溢出来画 ✗），
                        // 就压在底栏上面。**停靠时把底栏抬到中控台之上**（`-1 + 0.5`：
                        // 高于中控台、仍低于任何浮动面板 —— 它们的 z 是 1..N ✓）✓。
                        .zIndex(
                            if (bottomFloating) zIndexOf(-2) else zIndexOf(-1) + 0.5f,
                        )
                        .alpha(if (state.bottomBarHidden) 0f else 1f)
                        .align(if (!bottomFloating) Alignment.BottomStart else Alignment.TopStart)
                        // 同中控台：**停靠态一律不偏移**（否则贴底那条会被盘上的浮动坐标推开）
                        .offset {
                            if (bottomFloating) {
                                IntOffset(
                                    bottomOffsetState.value.x.roundToInt(),
                                    bottomOffsetState.value.y.roundToInt(),
                                )
                            } else {
                                IntOffset.Zero
                            }
                        }
                        .then(
                            if (!bottomFloating) {
                                // ⚠️ 用户 2026-09-26：停靠时底栏在**左栏正下方、和左栏同宽** ✓
                                //（原来是"横跨右边整条" ✗ —— 不再给它 `start = promptWidthDp` 的偏移，
                                //  宽度也改成中控台那一栏的宽 ✓）。
                                Modifier.width(promptWidthDp)
                                    // ⚠️ 不给"拉高度"的口子（用户 2026-09-19：「不能随意改变大小，只能改变宽」）：
                                    // 吸附态底栏的高度就是**内容高度**，由 `onSizeChanged` 量出来。
                            } else {
                                Modifier.size(
                                    with(panelDensity) { bottomFloatSizePx.width.toDp() },
                                    with(panelDensity) { bottomFloatSizePx.height.toDp() },
                                )
                            },
                        )
                        // 排在 `offset` / `size` **之后**：报出去的是摆好、量好之后的矩形
                        //（吸附态底栏的高度由内容决定，这里量到的就是真值 —— 比 `bottomRectNow()` 还准）
                        .onGloballyPositioned { coords ->
                            bottomCoords.value = coords
                            if (!state.bottomBarHidden) {
                                state.reportPanelRect(
                                    PANEL_RECT_KEY_BOTTOM,
                                    coords.boundsInWindow(),
                                )
                            }
                        }
                        // 停靠态：**只圆下面两个角** ✓ —— 上面那两条边和中控台拼在一起（融合 ✓）
                        .clip(
                            if (referenceSkin && !bottomFloating) {
                                RoundedCornerShape(0.dp)
                            } else if (!bottomFloating) {
                                RoundedCornerShape(
                                    bottomStart = DOCK_PANEL_CORNER,
                                    bottomEnd = DOCK_PANEL_CORNER,
                                )
                            } else {
                                RoundedCornerShape(14.dp)
                            },
                        )
                        // 同提示词栏：**实体底色**（不再透背后的壁纸/画布）
                        .background(
                            LocalPanelBackgroundColor.current
                                ?: if (referenceSkin) NaiSkinTokens.bar() else MaterialTheme.colorScheme.surface,
                        )
                        .onSizeChanged { if (!bottomFloating) bottomBarHeightPx = it.height },
                ) {
                    // ⚠️ 这里只能 `fillMaxWidth`，**不能 `fillMaxSize`**：吸附态的底栏高度是
                    // "由内容决定"的（它没有 height 约束），一旦 fillMaxSize 就会撑满整个内容区
                    // —— 表现为"底栏跑到顶上、占满一列"（2026-09-19 踩过：日志里 `barH=1016`，
                    // 正好等于内容区高度）。
                    Column(Modifier.fillMaxWidth()) {
                    // ⛔ 底栏那条标题栏（小字「底栏」+ 抓手点阵）也删掉了 ✗ —— 同中控台，
                    //    理由见那一处的注释 ✓（用户 2026-09-24：「中控和底栏的标题和拖动点删掉，
                    //    以后不需要拖动了」✓）。
                    // ⚠️ 原来这里挂着**一条共享的**提示词按钮栏（垃圾桶/撤销/历史/翻译/优化）。
                    // 用户 2026-09-19：「这个工作区在底栏去除，改为提示词栏的提示词、角色、分镜
                    // 每个文本框一个」—— 它已经下放到**每个文本框自己下面**了
                    //（见 [PromptActionBar] 与 [PromptFields] / `CharacterCard`），底栏这条撤销。
                    RunBar(
                        state = state,
                        t = t,
                        onOpenParams = { },
                        onToggleMask = {
                            panelTarget = 0f
                            state.toggleMaskMode()
                        },
                        onImportImage = launchImport,
                    )
                    }
                    // ⛔ 底栏那颗记号本来只在浮动态才画 ✓（吸附态不给 ✓ —— 它只能拖走 / 拖回 ✓）；
                    //    现在浮动整个没有了 ✓ ⇒ 这块也一并去掉 ✓（用户 2026-09-24 ✓）。
                }

                // ---- ⑥ 临时图库抽屉（用户 2026-09-22）----
                // 贴**内容区**（= 画布）的右边缘、纵向居中：收起时只露半个把手（向左的箭头），
                // 点开从右边推进来一扇 340dp 宽的「媒体资产」面板，同一位置的把手换成向右的箭头。
                //
                // ⚠️ 它**不参与**这一层 `pointerInput` 的"内容格统一接管面板拖动"那套：
                // 抽屉自己挂 clickable / 自己的手势，只在**自己那块矩形**里参与命中 ——
                // 位置由这里的 `align` 和内部尺寸定死，手势修饰符一律排在尺寸修饰符**之后**
                //（这个仓库刚踩过：手势排在前面时命中矩形会钉在原点）。
                SessionGalleryDrawer(
                    state = state,
                    t = t,
                    // 用户 2026-09-26：**导入图片** + RunBar 右下角那两颗（铅笔 / 遮罩）
                    // 都搬到这条竖向工具栏上了 —— 功能一个不少，只是换了个位置/排列方式 ✓。
                    onImport = launchImport,
                    onOpenCanvasEditor = { state.openCanvasEditor() },
                    onToggleMask = { state.toggleMaskMode() },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        // z 序排在那些浮动面板（`zIndexOf` 给的是 1..N）之上：
                        // 面板展开后会被用户拽到右边缘，抽屉该压在它们上面、命中测试也先轮到它
                        .zIndex(60f),
                )
            }
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize().then(shortcutKeys),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = if (referenceSkin) Color.Transparent else MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
                    // 实测键盘弹起后的可用视口高度：给 maxPanelHeight 做钳制用（见上）。
                    // imePadding 在壳层已消费，这里量到的就是键盘上方的真实高度。
                    .onSizeChanged { viewportHeightPx = it.height },
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        // ---- 毛玻璃上半：把抽屉**背后**的内容糊掉 ----
                        // 半径乘上「这次到底盖住了多少画面」：
                        //  · 抽屉拉开时 = panelFraction；
                        //  · 键盘弹起时也是 1 —— 那时把手行+按钮栏会被顶到图片上方悬着，
                        //    抽屉却是收着的（panelFraction=0），不给模糊的话图片会从行缝里
                        //    清清楚楚透出来，就是之前修过的「透视」。
                        // 两者都取 0 时半径是 0，一点离屏图层的开销都没有。
                        //
                        // ⚠️ **拖动期间一律不给模糊**（用户 2026-09-16 报"刚开应用拉底栏顿"）：
                        // `panelFraction` 在拖动时每帧都在变，而 `Modifier.blur` 的半径一变就要
                        // **重建 RenderEffect + 一张全屏离屏层** —— 那是每帧都要付的钱，拖得越久付得越多。
                        // 所以拖动中半径固定 0（一点离屏开销都没有），**松手停稳后再糊上去**：
                        // 那时 `panelDragging` 已归 false、`panelFraction` 正在往 1 收，模糊是渐入的。
                        // 代价：拖动过程中抽屉背后的图是清晰的（只有 78% 半透明面板盖着），松手瞬间糊住。
                        //
                        // ⚠️ Android 12 以下 `Modifier.blur` 是空操作，那时只剩半透明、没有模糊。
                        .blur(
                            radius = NaiGlass.blurRadius * maxOf(
                                if (panelDragging) 0f else panelFraction,
                                if (imeBottomPx > 0) 1f else 0f,
                            ),
                        ),
                ) {
                    // 遮罩模式的工具条**顶替隐藏掉的顶栏**（外壳在遮罩模式下不画 TopAppBar）。
                    // 注意**不要再加 statusBarsPadding**：外壳的 Scaffold 已经把状态栏 inset 算进
                    // innerPadding 里了，重复加会让工具条下移一整条状态栏、图片跟着缩小。
                    if (state.maskMode && state.canvasMode != 2) {
                        MaskToolBar(state, t)
                        // 触发行：文字在工具条下面（同一底色，不是胶囊），点它从工具条下拉出参数面板
                        InpaintParamsTrigger(
                            t = t,
                            expanded = inpaintParamsOpen,
                            onToggle = { inpaintParamsOpen = !inpaintParamsOpen },
                        )
                    } else if (state.img2imgActive && state.canvasMode != 2) {
                        // 图生图参数：和重绘参数同一套"从行里下拉"的做法
                        I2iParamsTrigger(
                            t = t,
                            expanded = i2iParamsOpen,
                            onToggle = { i2iParamsOpen = !i2iParamsOpen },
                        )
                    }
                    // 图片铺满剩余空间；面板是**盖在它上面**的（不挤压图片）。
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(4.dp)
                            .padding(bottom = bottomBarHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        PreviewCard(state, t, Modifier.fillMaxSize())
                        // 参数面板：从工具条下方滑出来、盖在图上，不占高度
                        // （自己用 graphicsLayer 做动画：与本仓库其它面板一致，也避开
                        //  AnimatedVisibility 在 Column/Box 混合作用域下的重载歧义）
                        if (inpaintParamsProgress > 0.001f) {
                            InpaintParamsPanel(
                                state = state,
                                t = t,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .graphicsLayer {
                                        translationY = -size.height * (1f - inpaintParamsProgress)
                                        alpha = inpaintParamsProgress
                                    },
                            )
                        }
                        if (i2iParamsProgress > 0.001f) {
                            I2iParamsPanel(
                                state = state,
                                t = t,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .graphicsLayer {
                                        translationY = -size.height * (1f - i2iParamsProgress)
                                        alpha = i2iParamsProgress
                                    },
                            )
                        }
                    }
                }

                // 面板开着时，在图片区域铺一层透明拦截层：**点抽屉外任意位置就收起**。
                // 它画在预览之后、底部区域之前，所以点面板和运行条仍然照旧生效。
                // 遮罩模式下不铺：那层会把"在图片上涂遮罩"的手指全吃掉。
                if (canvasCovered && !state.maskMode) {
                    val interaction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                onClick = togglePanel,
                            ),
                    )
                }

                // 底部一体化区域，从上到下：**把手行 → 面板 → 常驻按钮栏 → 2x放大那行 → 运行条**。
                // 把手行放在面板**上面**是关键（用户要求）：
                //  · 面板收起（高度 0）时，把手行正好落在按钮区最上面 —— 和以前完全一样；
                //  · 面板弹起时，把手行跟着面板顶边一起上移 —— 也就是"弹出后把手动"；
                //  · 下面的 2x放大 / 运行条**位置不变**。
                //
                // ⚠️ 两块**分开**（用户 2026-09-16 要求）：
                //  · **固定组**（2x放大/导演台 + 运行条）钉在屏幕底部，键盘弹起就让它被盖住；
                //  · **上浮组**（把手行 + 抽屉 + 常驻按钮栏）跟着键盘走，停在键盘上方。
                //    距离 = `floatLift`（固定组高度与键盘高度取大）—— 键盘收起时正好落在
                //    固定组上方，和改动前完全一致。
                //
                // ⚠️ 底部这一整摞（把手行 / 常驻按钮栏 / 2x放大那行 / 运行条）**统一用顶栏那一档底色**：
                //    `surfaceContainer` @ [NaiGlass.BAR_ALPHA]（用户 2026-09-16 要求"和顶部一样"）。
                //    所以底色画在**外层 Column** 上、内层条不再各自上色 —— 一块连续的面，
                //    中间不会露出页面底色或图片，也不会两层半透明叠出比顶栏更深的色。
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceContainer
                                .copy(alpha = NaiGlass.BAR_ALPHA),
                        )
                        .onSizeChanged { fixedGroupHeightPx = it.height },
                ) {
                    ActionButtonsRow(
                        state = state,
                        t = t,
                        directorOpen = directorOpen,
                        onToggleDirector = {
                            directorOpen = !directorOpen
                            // 打开导演台时把底部参数抽屉收回去，只留导演台（用户要求）
                            if (directorOpen) panelTarget = 0f
                        },
                    )
                    RunBar(
                        state = state,
                        t = t,
                        onOpenParams = { openPanel(TAB_PARAMS) },
                        onToggleMask = {
                            // 进遮罩模式就把面板收掉，让整张图能让出来涂
                            panelTarget = 0f
                            state.toggleMaskMode()
                        },
                        onImportImage = launchImport,
                        // 电脑宽屏：导入图 / 铅笔 / 遮罩这三颗**不在 RunBar 上了** ——
                        // 它们在画布右边缘那条竖向工具栏里（用户 2026-09-26 ✓）。
                        showCanvasTools = false,
                    )
                }
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = floatLift),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(bottomBarDrag)
                            .onSizeChanged { peekRowHeightPx = it.height },
                    ) {
                        DrawerPeekRow(
                            summary = state.params.positivePrompt.ifBlank { t("generate.previewEmpty") },
                            expanded = peekExpanded,
                            onClick = togglePanel,
                        )
                    }
                    // 首次打开后就常驻：收起时高度归零，但子树不再被销毁重建。
                    // 剧情放大页也算"要打开抽屉"，否则刚点开的那一帧它还没被组合出来。
                    if (panelEverOpened || panelActive || state.plotEditorOpen) {
                        if (state.plotEditorOpen) {
                            // 剧情放大编辑页：**整块抽屉换成一个大文本框**（用户要求），
                            // 而且比普通面板高（0.85 屏 vs 0.6 屏）—— 它是拿来读长剧情的。
                            PlotEditorPanel(
                                state = state,
                                t = t,
                                onClose = { state.closePlotEditor() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(plotEditorHeight * panelFraction),
                            )
                        } else {
                            ControlPanel(
                                state = state,
                                t = t,
                                selectedTab = panelTab,
                                onSelectTab = selectTab,
                                onOpenPresets = { state.openStyleSheet() },
                                activeField = activePromptField,
                                onFieldFocus = { activePromptField = it },
                                panelHeight = maxPanelHeight * panelFraction,
                                dragModifier = panelHandleDrag,
                                scrollConnection = panelNestedScroll,
                            )
                        }
                    }
                    // 【工具栏已下放到每个文本框】用户 2026-09-16 曾把这条按钮栏挪到这儿
                    // （那时它是一条共享的，提示词一写长、挂在框里的按钮会被顶到很下面）；
                    // 2026-09-19 用户又要求「底栏去除，改为每个文本框一个」→ 见 [PromptActionBar]。
                }
            }
        }
    }

    // 导演台：从底部上滑的抽屉（「导演台」按钮开关它）
    if (directorOpen) {
        DirectorSheet(state = state, t = t, onDismiss = { directorOpen = false })
    }
    // 历史提示词抽屉：开合状态在 `AppState`（每个文本框各有一条工具栏之后，
    // 时钟图标会长在角色 / 分镜的卡片上，状态留在这一层就传不下去）
    state.promptHistoryField?.let { historyField ->
        PromptHistorySheet(
            state = state,
            t = t,
            field = historyField,
            onDismiss = { state.closePromptHistory() },
        )
    }
    if (showPresetSheet) {
        StylePresetSheet(state = state, onDismiss = { state.closeStyleSheet() })
    }

    // **底部抽屉**（对话 / 日志）：画在函数**最后**，才会盖在所有内容之上。
    // 入口是运行条底下那行字幕（见 StatusBarLine）。
    StatusDrawer(
        state = state,
        t = t,
        open = state.statusDrawerOpen,
        onClose = { state.closeStatusDrawer() },
    )
}

// ---------------------------------------------------------------------------
// 底部控制面板
// ---------------------------------------------------------------------------

/**
 * 底部面板的上下拖动。
 *
 * **不用 `Modifier.draggable`**：它和同一条底栏上「生成图片」按钮的 `clickable` 抢手势，
 * 实测上滑时灵时不灵。这里自己从 `down` 起算**原始位移**（`positionChange()` 在子级消费事件后
 * 会返回 0，不能用），并且：
 *  · [allowDownWhileOpen] 返回 false（面板收着的底栏）：只有往上走够 [slopPx] 才认账 ——
 *    单击、横向滑动、往下滚列表都不受影响
 *  · [allowDownWhileOpen] 返回 true（面板开着的底栏 / 面板标题栏）：上下都能拖 ——
 *    面板开着时，底栏那条「把手 + 摘要」行往下拖 = 收回面板
 *    （用户 2026-09-15 反馈"上托能开、下托收不回"的修复）。
 * [onStart] 在手势真正接管的那一刻回调一次（调用方用来清零净位移统计）。
 *
 * 松手/取消都会走 `onEnd`（`finally`），不会留下「拖到一半不动」的状态。
 */
private fun Modifier.panelDrag(
    allowDownWhileOpen: () -> Boolean,
    slopPx: Float,
    onDelta: (Float) -> Unit,
    onEnd: (Float) -> Unit,
    onStart: (() -> Unit)? = null,
    onPressChanged: ((Boolean) -> Unit)? = null,
): Modifier = this.pointerInput(Unit) {
    // 「放弃」门槛（只在 allowDownWhileOpen() 为 false 时生效）：手指往下走这么多才认定
    // "用户想往下滚"。取接管门槛的 5 倍（4dp → 20dp），就是为了容忍起手时那几 dp 的向下抖动。
    val abandonPx = slopPx * 5f
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // 报"手指按住了"。**必须在 `finally` 里先报"抬了"**，再调 onEnd ——
        // 否则收口会被自己那道"手指还按着就不收口"的闸挡掉，面板就永远停在半路。
        onPressChanged?.invoke(true)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var lastY = down.position.y
        var travelled = 0f
        var claiming = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPosition(change.uptimeMillis, change.position)
                val dy = change.position.y - lastY
                lastY = change.position.y
                travelled += dy
                if (!claiming) {
                    // ⚠️ allowDownWhileOpen 必须在事件里**实时读**（lambda 捕获的是状态对象），
                    // 不能当成 pointerInput 的 key：面板开合就发生在拖动过程中，key 一变 Compose
                    // 会取消进行中的手势协程 —— StudioDrawer 的 drawable 注释里记过同一个坑。
                    if (allowDownWhileOpen()) {
                        // 面板开着：上下都能接管（下托收回就靠这条）
                        if (kotlin.math.abs(travelled) >= slopPx) {
                            claiming = true
                            onStart?.invoke()
                        }
                    } else {
                        // 面板收着：往上走够 slopPx 才接管
                        if (travelled <= -slopPx) {
                            claiming = true
                            onStart?.invoke()
                        } else if (travelled >= abandonPx) {
                            // ⚠️ **放弃的门槛必须远大于接管门槛**。
                            // 早先两个都用 slopPx（4dp）：手指按下去时向下抖 4dp 是极常见的，
                            // 一抖到就 `return`，这次触摸整个作废（`awaitEachGesture` 要等手指
                            // 全部抬起才会开始下一次）→ 表现就是"在主页面很难滑出底栏"（用户反馈）。
                            // 下抖几 dp 是正常抖动；**往下走 20dp** 才算是"用户真想往下滚"。
                            return@awaitEachGesture
                        }
                    }
                }
                if (claiming) {
                    change.consume()
                    onDelta(dy)
                }
                if (!change.pressed) break
            }
        } finally {
            // ⚠️ **顺序要紧**：先报"手指抬了"，收口那道闸（`!panelPointerDown`）才会放行；
            // 反过来写的话，正常松手也会被判成"手势中"，面板就停在半路不动了。
            onPressChanged?.invoke(false)
            if (claiming) onEnd(tracker.calculateVelocity().y)
        }
    }
}

/**
 * 底部控制面板本体：把手 + Tab（提示词 / 参数 / 角色）+ 可滚动内容。
 *
 * 它是**内嵌**在生成页底部的（不是 `ModalBottomSheet`）：后者是独立窗口，会连运行条一起盖住，
 * 改完提示词还得先关抽屉才能点「生成图片」。这里堆在运行条上方，高度由拖出来的比例决定；
 * 顶部那条把手也挂着拖动手势，可以再把它按回去。
 */
@Composable
private fun ControlPanel(
    state: AppState,
    t: (String) -> String,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    onOpenPresets: () -> Unit,
    activeField: String,
    onFieldFocus: (String) -> Unit,
    panelHeight: Dp,
    dragModifier: Modifier,
    scrollConnection: NestedScrollConnection,
) {
    // 面板级**左右滑动 = 切换 提示词/角色/参数**（用户要求）。
    // ⚠️ 挂在**整个面板**上（含把手与 Tab 栏），不只是内容区 —— 早先只挂在内容列上，
    // 在把手/Tab 那一带左右滑就没反应（"禁了但滑不动"）。
    // 只认横向（纵向留给把手拖动与列表滚动），位移小于 70px 不切栏（防误触）。
    val visibleTabIds = listOf(TAB_PROMPTS, TAB_PARAMS).filterNot { state.isPanelDetached(it) }
    val promptScrollState = rememberScrollState()
    val paramsScrollState = rememberScrollState()
    val selectedScrollState = if (selectedTab == TAB_PARAMS) paramsScrollState else promptScrollState
    var tabSwipeAccum by remember(selectedTab) { mutableStateOf(0f) }
    val panelTabSwipe = Modifier.pointerInput(selectedTab, visibleTabIds) {
        detectHorizontalDragGestures(
            onHorizontalDrag = { _, delta -> tabSwipeAccum += delta },
            onDragEnd = {
                val direction = when {
                    tabSwipeAccum < -70f -> 1
                    tabSwipeAccum > 70f -> -1
                    else -> 0
                }
                tabSwipeAccum = 0f
                val currentIndex = visibleTabIds.indexOf(selectedTab).coerceAtLeast(0)
                val targetIndex = (currentIndex + direction).coerceIn(0, visibleTabIds.lastIndex.coerceAtLeast(0))
                val target = visibleTabIds.getOrNull(targetIndex) ?: selectedTab
                if (target != selectedTab) onSelectTab(target)
            },
            onDragCancel = { tabSwipeAccum = 0f },
        )
    }
    Surface(
        // ---- 毛玻璃下半：面板自己半透明 ----
        // `tonalElevation = 0`：色调抬升会给半透明底色再叠一层不透明色，
        // 把"透"这件事直接抵消掉，所以必须关掉。
        // 底色改问 `NaiSkinTokens.panel()`（风格化）：玻璃风格 = 原来的 `surface @ PANEL_ALPHA`，
        // 实色风格 = 参考稿的不透明 `--bg-panel`。
        color = NaiSkinTokens.panel(),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().height(panelHeight).then(panelTabSwipe),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 整条标题栏（把手 + Tab）都能拖动，不用精确按那 4dp 的把手
            Column(dragModifier.fillMaxWidth()) {
                // 面板自己那条 4dp 把手**去掉了**：把手行（DrawerPeekRow）现在就在面板正上方
                // 跟着它一起动，两条把手叠在一起是重复的。留一点上边距即可。
                Spacer(Modifier.height(6.dp))
                // 栏目条：**浮出去的那一栏就不列在这儿了**（用户 2026-09-19：「浮动显示后，
                // 提示词栏的选项就去除掉相对应的栏目」）。
                // `TabRow.selectedTabIndex` 要的是"在可见列表里的位置"，所以下面做一次映射。
                //
                // ⚠️ 名字都走 `panelTabTitleKey`（**唯一一份**：分离窗口的标题、抽屉的栏目标、
                // 分离占位文案都用它 ✓）—— 原来那份手写的三行列表和 `panelTabTitleKey`
                // 是两处，迟早会漂 ✗。
                // ⚠️ 同 `PanelTabStrip`：栏目条上**只有两栏** ✓（角色那一栏删掉、内容并到提示词下面 ✓）。
                PanelTabStrip(
                    selectedTab = selectedTab,
                    onSelectTab = onSelectTab,
                    t = t,
                    hiddenTabs = listOf(TAB_PROMPTS, TAB_PARAMS)
                        .filter { state.isPanelDetached(it) }
                        .toSet(),
                    containerColor = NaiSkinTokens.bar(),
                    comicStoryboard = state.canvasMode == 2,
                )
            }
            // 用户 2026-09-22：「**无限画布按钮移到中控右边栏里**」✓ ——
            // 它管的是**画布**（左栏那块 ✓），放在右边栏栏目条下面最好找 ✓；
            // 原来那个"浮在画布左上角"的开关已摘掉 ✓（用户点名要挪 ✓）。
            CanvasModeSwitcher(
                t = t,
                mode = state.canvasMode,
                onMode = state::chooseCanvasMode,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            )
            // 提示词栏包含权重解析和角色区，参数栏包含大量控件。AnimatedContent 会在 110ms 内
            // 同时组合和测量新旧两整棵内容树，快速切换时容易掉帧；这里即时替换，保留点击与滑动切栏，
            // 去掉内容重叠的过场成本。
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .nestedScroll(scrollConnection)
                    .verticalScroll(selectedScrollState)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PanelTabBody(
                    tab = selectedTab,
                    state = state,
                    t = t,
                    onOpenPresets = onOpenPresets,
                    activeField = activeField,
                    onFieldFocus = onFieldFocus,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 底部抽屉
// ---------------------------------------------------------------------------

/**
 * **一个栏目里的内容**（提示词 / 角色 / 参数）。
 *
 * 抽出来是因为**两处要用同一份**：
 *  · 手机：底部抽屉的 Tab 内容（`ControlPanel`）；
 *  · 电脑/平板宽窗口：左栏也是"切换式"的（用户 2026-09-16 要求"别像那样全放一条"），
 *    Tab 栏跟手机一模一样、内容也一模一样。分成两份写迟早会漂。
 *
 * 外面负责滚动与内边距（抽屉和宽栏的边距不一样），这里只管"这一栏有什么"。
 */
@Composable
private fun PanelTabBody(
    tab: Int,
    state: AppState,
    t: (String) -> String,
    onOpenPresets: () -> Unit,
    activeField: String,
    onFieldFocus: (String) -> Unit,
    /** true = **这块就是分离出来的那一块本体**，别画"已在独立窗口中打开"的占位。 */
    skipDetachedPlaceholder: Boolean = false,
) {
    // 这一栏被「窗口 → 分离操作面板 → 分离 …」拿到那块浮动框里去了 → **原地什么都不画**
    //（用户 2026-09-19：「浮动显示后，提示词栏的选项就去除掉相对应的栏目」——
    //  栏目条那边也已经把这一栏摘掉了，所以这里不会再被选中）。
    if (state.isPanelDetached(tab) && !skipDetachedPlaceholder) return
    when (tab) {
        TAB_CHARACTERS -> CharacterSection(state = state)
        TAB_PARAMS -> ParamsFields(state, t)
        // 漫画模式沿用同一套提示词输入，再编辑分镜。
        else -> Column(Modifier.fillMaxWidth()) {
            PromptFields(
                state = state,
                t = t,
                onOpenPresets = onOpenPresets,
                activeField = activeField,
                onFieldFocus = onFieldFocus,
            )
            if (state.canvasMode == 2) {
                Spacer(Modifier.height(10.dp))
                CharacterEditorPanel(state, Modifier.fillMaxWidth())
            } else {
            // ⚠️ **角色挪到提示词下面** ✓（用户 2026-09-24：「**角色栏删除，把角色放到提示词下面**」✓）——
            //    「角色与分镜」那一栏从栏目条上**撤掉了** ✓（见 `PanelTabStrip` / `ControlPanel` 那两处
            //    列 tab 的地方 ✓），内容**原样搬进「提示词」这一栏、接在提示词框下面** ✓。
            //    两块之间那条分隔线只是把"提示词"和"角色"分开 ✓（否则会连成一片、看不出哪儿是哪儿 ✓）。
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            CharacterSection(state = state)
            }
        }
    }
}

/**
 * **角色那一块**（原来「角色与分镜」那一栏的正文 ✓，用户 2026-09-24 挪到提示词下面 ✓）。
 *
 * 抽成一个函数是因为它**现在两处都在**：① 「提示词」那一栏的下面 ✓；② 老的 `TAB_CHARACTERS`
 * 分支（栏目条上已经不再列它了 ✓，留着只是不让老索引落到空处 ✓）。一份实现、两处引用 ✓。
 */
@Composable
private fun CharacterSection(state: AppState) {
    CharacterEditorPanel(state, Modifier.fillMaxWidth())
}

/**
 * 面板名（菜单与分离窗口的标题都用它）。**唯一一份** —— 栏目条、分离窗口标题、
 * 分离占位文案都从这儿取，免得三处各写一份、加一档就漂 ✓。
 */
internal fun panelTabTitleKey(tab: Int): String =
    when (tab) {
        TAB_CHARACTERS -> "char.tab"
        TAB_PARAMS -> "generate.params"
        else -> "generate.prompts"
    }

/**
 * 「这一栏已经在独立窗口里了」的占位（用户 2026-09-19 的「窗口 → 分离 …」）。
 *
 * 原地留一条说明 + 一颗「收回」按钮：否则用户会以为面板内容丢了。
 */
@Composable
private fun DetachedPlaceholder(tab: Int, state: AppState, t: (String) -> String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            RuntimeText.format(
                state.settings.language,
                "window.detachedHint",
                mapOf("name" to t(panelTabTitleKey(tab))),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { state.closeDetachedPanel(tab) }) {
            Text(t("window.detachBack"))
        }
    }
}

/**
 * ⚠️ 这里**曾经**有一个 `DetachedPanelContent`：给"分离窗口"开**第二扇 OS 窗口**用的内容。
 * 用户 2026-09-19 改口：「窗口分离功能就是像底栏一样**多出一个可以在窗口内拖动的框**」——
 * 于是分离改成在**同一个窗口里**飘一块（见宽屏分支里的 ③c 与 `beginPanelGesture` 的第 0 步），
 * 那个函数连同桌面入口里的 `Window(...)` 一起删掉了。
 */

/**
 * **玻璃面板顶部的拖拽把手**（电脑宽屏：提示词栏 / 底栏。用户 2026-09-19：
 * 「把提示词栏与底栏做成可随意拖放的窗口，拖拽回原本地方后吸附，复原如初」）。
 *
 * 形态照用户给的那张「子工具」窗口：一条细标题栏 + 抓手的点阵；面板被拖出去之后，
 * 右边多一颗「吸附回原位」的按钮（拖回去也能吸附，两个入口）。
 *
 * ⚠️ **这里没有 `pointerInput`** —— 拖动/缩放的指针都由**内容格**统一接收（见 `GenerateScreen`
 * 宽屏分支里那段 `pointerInput` 与注释）。原因：把手自己长在**会动**的面板上，而 Compose 报的
 * 位移/位置都是**相对被拖节点**的 —— 节点跟着指针走之后位移趋近 0（"拖不动"）；
 * 换成"量节点窗口位置 + 局部坐标"的绝对定位又会因为两次测量不同步而**发散乱飞**。
 * 只有"永远不动的节点"才同时避免这两个坑。
 */
@Composable
private fun PanelDragHandle(
    label: String,
    redockLabel: String,
    floating: Boolean,
    onRedock: () -> Unit,
) {
    // ⚠️ 用户 2026-09-26 第三轮：「**未分离时标题栏也变色，分离才是正常颜色**」——
    // 也就是"标题栏颜色"这个设置**只管分离/浮动出来的面板** ✓；停靠时标题栏跟随面板底色
    //（= 视觉上没有独立标题栏 ✓，两块拼出来的窗口也就没有那条色带 ✓）。
    //  · `floating = true` → 用设置里的颜色，字/图标按该颜色亮度自动取黑或白 ✓；
    //  · `floating = false`（停靠）→ `null` → 跟随面板底色 ✓。
    val titleBarColor = if (floating) LocalPanelTitleBarColor.current else null
    val barColor = titleBarColor ?: MaterialTheme.colorScheme.surface
    val barContent = titleBarColor?.let {
        if (it.luminance() < 0.5f) Color.White else Color.Black
    } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .height(PANEL_HANDLE_HEIGHT)
            .background(barColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 抓手：两列三点（不依赖图标字体，任何语言下都一样）
        val gripColor = barContent
        Canvas(Modifier.padding(start = 10.dp).size(width = 8.dp, height = 12.dp)) {
            val r = size.width / 5f
            val left = size.width * 0.3f
            val right = size.width * 0.7f
            listOf(size.height * 0.18f, size.height * 0.5f, size.height * 0.82f).forEach { cy ->
                drawCircle(color = gripColor, radius = r, center = Offset(left, cy))
                drawCircle(color = gripColor, radius = r, center = Offset(right, cy))
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = barContent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, end = 6.dp),
        )
        Spacer(Modifier.weight(1f))
        if (floating) {
            IconButton(onClick = onRedock, modifier = Modifier.size(26.dp)) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = redockLabel,
                    modifier = Modifier.size(16.dp),
                    tint = barContent,
                )
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

/** 拖拽把手那条的高度（ComfyUI 那种"胖一点的标题栏"：用户 2026-09-26 要求做粗 ✓，26 → 30dp）。 */
private val PANEL_HANDLE_HEIGHT = 30.dp

/**
 * `PanelGrabState.mode` 的第 4 种：**停靠态按住了面板的标题栏**。
 *
 * 它什么都不做，存在的意义只是"这一下被面板吃掉了" —— 不然鼠标按在中控台 / 底栏的标题栏上
 * 往下拖会漏给画布，变成画布平移（用户 2026-09-26 改成默认停靠之后才会遇到）。
 */
private const val GRAB_MODE_DOCKED_HANDLE = 9

/** 一次面板手势的临时状态（只给指针回调读写，不进组合）。 */
private class PanelGrabState {
    var panelIsPrompt: Boolean = true

    /**
     * **分离出来的那一栏**（`TAB_CHARACTERS` / `TAB_PARAMS`）；
     * `null` = 拖的是原来那两块（中控台 / 底栏）。
     *
     * 用户 2026-09-19：「窗口分离功能就是像底栏一样**多出一个可以在窗口内拖动的框**」——
     * 分离不再开第二个 OS 窗口，而是在同一个窗口里飘一块；它和那两块共用这一套手势。
     */
    var detachedTab: Int? = null

    /**
     * 0 = 拖动，1 = 拉右边缘（只改宽），2 = 拉下边缘（只改高），3 = 拉右下角，
     * [GRAB_MODE_DOCKED_HANDLE] = 停靠态按住标题栏（吃掉指针、什么都不动）。
     */
    var mode: Int = 0
    var startPointer: Offset = Offset.Zero
    var startOffset: Offset = Offset.Zero
    var startSize: Size = Size.Zero
}

/** 分离出来的那块框的默认尺寸（比底栏大、比整条提示词栏小，够看内容）。 */
private val DETACHED_BOX_WIDTH = 460.dp
private val DETACHED_BOX_HEIGHT = 560.dp

/**
 * **浮动面板右下角那颗"可以拉"的记号**（纯装饰 + 提示）。
 *
 * ⚠️ 指针处理**不在这里** —— 由内容格统一命中测试（见 `GenerateScreen` 宽屏分支里的
 * `pointerInput`）。原因见 [PanelDragHandle] 的注释：会动的节点收不到可靠的指针坐标/位移。
 */
@Composable
private fun PanelResizeMark(modifier: Modifier = Modifier) {
    val gripColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier.size(PANEL_RESIZE_CORNER),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(11.dp)) {
            val stroke = 1.4f * density
            drawLine(
                color = gripColor,
                start = Offset(size.width * 0.15f, size.height * 0.95f),
                end = Offset(size.width * 0.95f, size.height * 0.15f),
                strokeWidth = stroke,
            )
            drawLine(
                color = gripColor,
                start = Offset(size.width * 0.55f, size.height * 0.95f),
                end = Offset(size.width * 0.95f, size.height * 0.55f),
                strokeWidth = stroke,
            )
        }
    }
}

// ⚠️ 曾经还有"右边缘 / 下边缘"两条 10dp 热区 —— 用户 2026-09-19 明确要求**只留右下角那颗三角**，
// 贴着边随便一拖不该改尺寸，所以那两条热区连同常量一起删了（`PanelGrabState.mode` 的
// 1 / 2 两档保留在实现里，以后要加回来只需在命中测试里补两行）。

/** 右下角那颗把手多大（要够好抓）。 */
private val PANEL_RESIZE_CORNER = 26.dp

/** 浮动面板能缩到多小（再小里面的输入框就没法用了）。 */
private val PANEL_MIN_WIDTH = 260.dp
private val PANEL_MIN_HEIGHT = 150.dp

/**
 * **宽窗口左栏的栏目条**（提示词 / 角色 / 参数）。
 *
 * 与手机抽屉共用 `panelTab` 这个状态 —— 电脑上切到"参数"，手机上拉开抽屉也是"参数" ✓
 * 栏目标签也用同一套（提示词 / 角色 / 参数，角色在中间）。
 *
 * ⚠️ 标签走 [panelTabTitleKey]（**唯一一份** ✓）。
 */
@Composable
private fun PanelTabStrip(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    t: (String) -> String,
    /** 已经浮出去的那几栏：**不在栏目条里列**（用户 2026-09-19：「浮动显示后…去除掉相对应的栏目」）。 */
    hiddenTabs: Set<Int> = emptySet(),
    containerColor: Color = NaiSkinTokens.strip(),
    /** Comic canvas replaces the prompt tab with the storyboard editor. */
    comicStoryboard: Boolean = false,
    showParamsButton: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(containerColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (TAB_PROMPTS !in hiddenTabs) {
            Box(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelectTab(TAB_PROMPTS) },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    if (comicStoryboard) t("char.tab") else t("generate.prompts"),
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = NaiSkinTokens.ReferenceFontFamily, fontWeight = FontWeight.Medium),
                    color = if (selectedTab == TAB_PROMPTS) NaiSkinTokens.onSelected() else NaiSkinTokens.muted(),
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (showParamsButton && TAB_PARAMS !in hiddenTabs) {
            val selected = selectedTab == TAB_PARAMS
            Surface(
                onClick = { onSelectTab(if (selected && TAB_PROMPTS !in hiddenTabs) TAB_PROMPTS else TAB_PARAMS) },
                shape = RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp, bottomStart = 7.dp, bottomEnd = 7.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else NaiSkinTokens.panel(),
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else NaiSkinTokens.borderStrong()),
                shadowElevation = 3.dp,
                modifier = Modifier.padding(end = 5.dp).offset(y = (-3).dp).width(44.dp).height(49.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(TuneIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(t("generate.params"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ParamsEdgeTrigger(
    t: (String) -> String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else NaiSkinTokens.bar(),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else NaiSkinTokens.borderStrong()),
        shadowElevation = 3.dp,
        modifier = modifier.width(40.dp).height(54.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(TuneIcon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(t("generate.params"), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun DesktopParamsDrawer(
    state: AppState,
    t: (String) -> String,
    visible: Boolean,
    onClose: () -> Unit,
    onOpenPresets: () -> Unit,
    activeField: String,
    onFieldFocus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f)) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f)) + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            color = NaiSkinTokens.panel(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, NaiSkinTokens.borderStrong()),
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().height(44.dp).padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t("generate.params"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = t("common.close"), modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = NaiSkinTokens.border())
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PanelTabBody(
                        tab = TAB_PARAMS,
                        state = state,
                        t = t,
                        onOpenPresets = onOpenPresets,
                        activeField = activeField,
                        onFieldFocus = onFieldFocus,
                    )
                }
            }
        }
    }
}

/**
 * 「2x放大」+「导演台」两个**方款按钮**（提示词抽屉上面那一行，各占一半宽度）。
 *
 * · **2x放大**：对当前工作图调 NovelAI 官方放大（固定 2×）。付费接口（按输入像素 1–4 Anlas），
 *   所以按钮里把预计消耗写出来。
 * · **导演台**：开关式按钮 —— 点亮就上滑出导演台抽屉（6 个官方工具 + 各自的参数）。
 * 两个都用方款（圆角矩形）而不是胶囊，是为了让"半宽并排"看起来是同一块控制条。
 */
@Composable
private fun ActionButtonsRow(
    state: AppState,
    t: (String) -> String,
    directorOpen: Boolean,
    onToggleDirector: () -> Unit,
) {
    val path = state.workImagePath
    // ⚠️ `LocalImageIo.current` 是 @Composable 读取，**必须先取出来**再进 remember 的 lambda
    val imageIo = LocalImageIo.current
    val dims = remember(path, state.maskVersion, imageIo) {
        path?.let { runCatching { MaskCodec.imageSize(imageIo, it) }.getOrNull() }
    }
    val price = dims?.let { OfficialUpscale.price(it.first, it.second) }
    val upscaleEnabled = path != null && price != null && !state.busy

    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ---------- 2x 放大（左半） ----------
        Surface(
            shape = MaterialTheme.shapes.medium,
            // **底色交给外层那条和顶栏同色的大横条**（用户 2026-09-16 要求"2x放大的背景
            // 和顶部一样"）：这里一律透明，不再自己上色 —— 上了就和背景同色，等于没上，
            // 还会在"可用/不可用"之间留下两块深浅不一的补丁。
            // 状态全靠**字色**：可用 = 主色，不可用 = 次要色。
            color = Color.Transparent,
            contentColor = if (upscaleEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = upscaleEnabled) { state.officialUpscale() },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                // 按钮上**只留「2x 放大」四个字**（用户要求）；价格不再显示
                Text(t("upscale.button"), style = MaterialTheme.typography.labelLarge)
            }
        }

        // ---------- 导演台（右半） ----------
        val directorEnabled = path != null && !state.busy
        Surface(
            shape = MaterialTheme.shapes.medium,
            // 同左：底色交给外面那条和顶栏同色的横条。
            // 只有**展开态**留一层主色淡染 —— 那是"面板正开着"的信息，不能省。
            color = if (directorOpen) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            } else {
                Color.Transparent
            },
            contentColor = if (directorOpen || directorEnabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = directorEnabled) { onToggleDirector() },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(t("director.button"), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 导演台抽屉（`ModalBottomSheet` 是实验 API，包在这里 OptIn）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectorSheet(state: AppState, t: (String) -> String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        DirectorSheetContent(state = state, t = t)
    }
}

/** 导演台抽屉内容：6 个工具 + 各自的参数 + 执行。参数直接读写设置 → 自动进"参数记忆"。 */
@Composable
private fun DirectorSheetContent(state: AppState, t: (String) -> String) {
    val settings = state.settings
    val tool = DirectorTools.byId(settings.directorTool)
    // 颜色化用 augmentDefry、修改表情用 augmentEmotionLevel（插件的 emotion 就是把 level 当 defry 发），
    // 两个值分开存，所以来回切工具不会互相覆盖。
    val defryValue = if (tool.hasEmotion) settings.augmentEmotionLevel else settings.augmentDefry
    val setDefry: (Float) -> Unit = { value ->
        state.setSettings {
            if (tool.hasEmotion) {
                it.copy(augmentEmotionLevel = value.toDouble())
            } else {
                it.copy(augmentDefry = value.toDouble())
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(t("director.title"), style = MaterialTheme.typography.titleMedium)
        Text(
            t("director.hint"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 6 个工具：两行三列的小方款，选中高亮
        DirectorTools.ALL.chunked(3).forEach { rowTools ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowTools.forEach { item ->
                    val selected = item.id == tool.id
                    Surface(
                        shape = MaterialTheme.shapes.medium,
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
                            .weight(1f)
                            .clickable { state.setSettings { it.copy(directorTool = item.id) } },
                    ) {
                        Text(
                            t("director.tool.${item.id}"),
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        )
                    }
                }
                // 补齐最后一行
                repeat(3 - rowTools.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // 各工具自己的参数
        if (tool.hasKeepBubbles) {
            SwitchRow(
                label = t("director.keepBubbles"),
                checked = settings.augmentKeepTextBubbles,
                onCheckedChange = { value ->
                    state.setSettings { it.copy(augmentKeepTextBubbles = value) }
                },
            )
        }
        if (tool.hasEmotion) {
            PickerField(
                label = t("director.emotion"),
                options = DirectorTools.EMOTIONS.map { NaiOption(it.second, it.first) },
                selected = settings.augmentEmotion,
                onSelect = { value -> state.setSettings { it.copy(augmentEmotion = value) } },
            )
        }
        if (tool.hasPrompt || tool.hasEmotion) {
            OutlinedTextField(
                value = settings.augmentColorizePrompt,
                onValueChange = { value ->
                    state.setSettings { it.copy(augmentColorizePrompt = value) }
                },
                label = { Text(t("director.prompt")) },
                // 导演工具提示词也是**真提示词**（"multiple views" 这种带空格的写法很常见）：
                // 登记焦点，否则空格被闸门吃掉、这里打不出空格。
                modifier = Modifier
                    .fillMaxWidth()
                    .trackTextInputFocus(state, "director.prompt"),
            )
        }
        if (tool.hasDefry) {
            LabeledSlider(
                label = t("director.strength"),
                value = defryValue.toFloat(),
                range = 0f..DirectorTools.MAX_DEFRY.toFloat(),
                steps = DirectorTools.MAX_DEFRY - 1,
                valueText = defryValue.toInt().toString(),
                onValueChange = setDefry,
            )
        }

        Button(
            onClick = { state.runDirectorTool() },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.busy) t("generate.reading") else t("director.run"))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * 收起态那一行：一个小把手 + 提示词摘要 + 箭头（收起时 ▲、拉出后 ▼）。
 * 整行可点击切换面板展开状态。
 */
@Composable
private fun DrawerPeekRow(summary: String, expanded: Boolean, onClick: () -> Unit) {
    // 毛玻璃：和顶栏同一档底色（`tonalElevation` 必须 0，色调抬升会给半透明底色
    // 再叠一层不透明色，把"透"直接抵消）。
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = NaiGlass.BAR_ALPHA),
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(top = 6.dp, bottom = 8.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown
                    else Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 预览
// ---------------------------------------------------------------------------

// ---- 预览的缩放 ----
// ⚠️ 默认（1 倍）= 按 `ContentScale.Fit` 正好铺满可视区，就是它一开始待的地方；
//    但**摆到别处是允许的、而且是会记住的**（用户 2026-09-26，见下面 `KEY_CANVAS_PREVIEW_*`）：
//    滚轮 / 双指捏合缩放、按住空格或中键拖动平移，摆完就落在 `platform.kv` 里。
//    缩放一律**以手指 / 光标底下那点为锚点** —— 想看哪块就在哪块上缩放。
//    ⚠️ 老口径「缩放回到 1 倍或更小时偏移强制归零」**已删除**（用户 2026-09-26：
//    「我摆的位置别被吃」）—— 缩放**不再**动 `previewOffset`，偏移只受 `±area/2` 的夹取约束
//    （防丢图，见 `LaunchedEffect(areaState.value)`）；归位只由「视图 → 重置图片位置」和双击触发。
private const val PREVIEW_MIN_SCALE = 0.2f
private const val PREVIEW_MAX_SCALE = 6f

/** 滚轮每一格缩放多少（`scrollDelta` 一格通常 ∓1）：0.15 的手感是滚三四格到 ~1.7 倍。 */
private const val PREVIEW_WHEEL_ZOOM_STEP = 0.15f

// ---- 画布摆放（平移 + 缩放）的持久化：**记忆生图区域** ----
//
// 用户 2026-09-26：「记忆生图区域，在画布上图片可以随意摆放，生图的流式与图片都按照图片的摆放来，
// 在视图里加上个重置图片位置的按钮」。于是原来那条"换图 / 重新生成就回到 1 倍"的口径作废：
//
//  · 摆放**写盘**（下面三个键，电脑上落在 `%APPDATA%\NAI Studio\prefs.json`），重启照旧；
//  · **新图到达不再复位**（流式每一帧、以及生成完落地的那张图，都按当前摆放渲染）；
//  · 只有用户主动点「视图 → 重置图片位置」才回 1 倍 + 居中。
//
// 口径与 `panels.*` 那几块面板一致：**只记像素**（相对画布可视区中心的偏移，和
// `previewOffset` 本身就是同一个量）、缩放记成**千分之一整数**（`KeyValueStore` 只有
// `getInt` / `putInt`，没有 Float），读回时按 `PREVIEW_MIN_SCALE..PREVIEW_MAX_SCALE` 夹一次。
private const val KEY_CANVAS_PREVIEW_SCALE = "canvas.preview.scale"
private const val KEY_CANVAS_PREVIEW_X = "canvas.preview.x"
private const val KEY_CANVAS_PREVIEW_Y = "canvas.preview.y"

/** 缩放落盘的整数倍率（`scale * 1000` 存 Int，读回 `÷ 1000`）。 */
private const val PREVIEW_SCALE_STORE_FACTOR = 1000f

/**
 * 摆放**停下来**多久之后才写盘。
 *
 * 拖动的每一帧、滚轮的每一格都会改 `previewScale` / `previewOffset`，**不能每帧写**
 *（会把 `prefs.json` 磨穿 —— 和侧栏宽度 `commitSidebarWidth` 那条"松手才提交"同一个理由）。
 * 这里用一个"最后一次改动之后静置 400ms"的防抖：拖完松手、滚轮停下、双击归位动画播完，
 * 都会各自收口成**一次**写入。
 */
private const val PREVIEW_PLACEMENT_SAVE_DELAY_MS = 400L

// 图片**背后**那层方格（用户要求保留：背景就是"画布"）。
// 边长固定按**屏幕**算、不跟着缩放走 —— 它只是"这是一块画布"的视觉暗示，跟着放大缩小反而晃眼。
private val PREVIEW_GRID_CELL = 18.dp
// 第二色的透明度。用 onSurface 而不是 surfaceContainer 系列：
// 深色主题上是提亮、浅色主题上是压暗，一个值两边都成立，
// 也不怕哪天主题里几个 surfaceContainer 被调成同一个颜色（格子会直接隐形）。
private const val PREVIEW_GRID_ALPHA = 0.06f

// ---------------------------------------------------------------------------
// 可拖放的玻璃面板（电脑宽屏：提示词栏 / 底栏。用户 2026-09-19）
// ---------------------------------------------------------------------------

/**
 * 拖到离吸附位多近就**吸附回去**（用户："拖拽回原本地方后吸附，复原如初"）。
 *
 * 48dp 大约是一根手指/一个光标能"瞄"的宽容度：近了自动归位，远了就留在那当浮窗。
 */
private val PANEL_SNAP_DP = 48.dp

/** 浮动状态下提示词栏的高度（吸附态是整列高度；拖出来之后收成一个"窗口"）。 */
private val PANEL_FLOAT_HEIGHT = 520.dp

/** 浮动状态下底栏的宽度（吸附态是"提示词栏右边到窗口右缘"）。 */
private val PANEL_FLOAT_WIDTH = 520.dp

/**
 * **面板矩形上报的 key**（用户 2026-09-21：「还要和其他窗口吸附啊」）。
 *
 * 生成页这几块面板把自己的矩形（`boundsInWindow()` = **窗口客户区坐标**）报给
 * `AppState.reportPanelRect`，工具浮动窗口（`StudioShell.ToolWindowLayer`）拿它当吸附候选。
 * 工具窗口的落点也在**窗口客户区坐标**里，所以两边同源、**不差任何偏移**，不用换算。
 * key 的三个口径见 [com.kallan.naistudio.state.AppState.panelRects]。
 */
private const val PANEL_RECT_KEY_PROMPT = "prompt"
private const val PANEL_RECT_KEY_BOTTOM = "bottom"

/** 分离出来的那一块（`TAB_*` → `"detach:<tab>"`）。 */
private fun detachedPanelRectKey(tab: Int) = "detach:$tab"


/**
 * 双指捏合缩放。**只认双指** —— 单指一律不认领（不 consume），两个原因：
 *  · 图片待在**固定位置**：一根手指在图上划来划去，它不该跟着跑；
 *  · 单指横向滑动还给图库翻页 —— 自己吃掉的话，"从主页滑到图库"这条常用路径就没了。
 *
 * 回调带上**双指之间的中点**（`centroid`）：缩放以它为锚点，手指底下那块画面不动。
 * 认领时机挂在 touchSlop 上（和官方 `detectTransformGestures` 同一套判断）：位移越过
 * 阈值时**如果屏幕上有两根手指**才认领；只有一根就一声不吭地继续等，让祖先的 pager 拿去。
 * 所以"先按一根、再补第二根"也能正常进入缩放（每帧重新看一次手指数，不是只在阈值那一帧定生死）。
 */
private suspend fun PointerInputScope.twoFingerPinchGestures(
    onPinch: (centroid: Offset, zoom: Float) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val touchSlop = viewConfiguration.touchSlop
        var zoomAccum = 1f
        var panAccum = Offset.Zero
        var slopped = false
        var claimed = false
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.all { !it.pressed }) break
            // 翻页手势已经把这串事件拿走了，就别再抢
            if (event.changes.any { it.isConsumed }) break
            val zoom = event.calculateZoom()
            val pan = event.calculatePan()
            if (!slopped) {
                zoomAccum *= zoom
                panAccum += pan
                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                val zoomMotion = abs(1f - zoomAccum) * centroidSize
                slopped = zoomMotion > touchSlop || panAccum.getDistance() > touchSlop
                // 还没起量，先不表态
                if (!slopped) continue
            }
            if (!claimed) {
                if (event.changes.count { it.pressed } < 2) continue
                claimed = true
            }
            onPinch(event.calculateCentroid(), zoom)
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        }
    }
}

/**
 * 预览。图片的**默认位置**是按真实宽高比居中（`ContentScale.Fit`），**背后保留那层方格底**
 * （用户要求：背景还是"画布"）。
 *
 * ⚠️ 它**不再**是钉死的位置了（用户 2026-09-26：「在画布上图片可以随意摆放…记忆生图区域」）：
 * 滚轮 / 双指捏合缩放（0.2~6 倍）、按住空格或中键拖动平移，摆到哪儿**会被记住**
 *（`KEY_CANVAS_PREVIEW_*`，重启照旧）；**新图到达 / 重新生成都不再复位**，
 * 要摆回去就点「视图 → 重置图片位置」。
 *
 * 手势：双指捏合缩放（锚点在双指之间）、滚轮缩放（宽屏，锚点在光标处）、按住空格 / 中键拖动平移；
 * 单指完全不接，所以单指横滑照旧翻到图库。**双击 = 归位**（回 1 倍 + 居中）：
 * 放大之后想立刻回到原样，用这个。⚠️ 缩放本身（滚轮 / 捏合）**不再动偏移** ——
 * 老口径"缩到 ≤1 倍就归零"已按用户 2026-09-26 的要求删掉（摆好的位置别被吃）。
 *
 * 涂遮罩（`maskMode`）**不再动缩放与平移**（用户 2026-09-22：「遮罩模式时会自动将图片填充画布，
 * 不需要」「遮罩模式只弹出窗口，图片不动」）：进出遮罩模式时图停在原处，
 * 滚轮缩放 + 按住空格 / 中键拖动**照旧可用**，而笔迹与聚焦框按[图片当前实际显示的矩形]
 * （`shownImageRect`，含缩放与平移）换算 —— 所以涂上去的位置永远落在同一个像素上。
 * 早先那一版是"进遮罩就归位 + 关掉整套手势"：笔迹按"1 倍 + 居中"算，图一跳位置就全错。
 *
 * 内容优先级与参考实现一致：**流式预览帧** → 当前图片文件 → 空占位。
 * "模糊 → 清晰"来自服务端逐帧推送的去噪中间稿本身，客户端不做模糊动画。
 */
/**
 * **画布模式切换**（用户 2026-09-22：「**在生图页的左上角，画布左上角加切换按钮：
 * 普通 / 无限画布 / 漫画**，先做，暂时不填充功能」✓）。
 *
 * ⚠️ 本批**只做界面** ✓：选中的模式只活在这个 `remember` 里 —— **不落盘、不改任何画布行为** ✗。
 *    三种模式各自要做什么（无限画布 = 画布随内容长大？漫画 = 走高级漫画那一套？），
 *    等用户点单再一项一项接 ✓（现在硬接一半反而会让人以为"点了没反应是坏了"✗）。
 */
@Composable
private fun CanvasModeSwitcher(
    t: (String) -> String,
    mode: Int,
    onMode: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    val keys = listOf("canvasMode.normal", "canvasMode.infinite", "canvasMode.comic")
    val groupShape = if (referenceSkin) RoundedCornerShape(12.dp) else NaiSkinTokens.boxShape()
    Row(
        modifier = modifier
            .padding(if (LocalNaiSkin.current == NaiSkin.Reference) 0.dp else 8.dp)
            // 参考稿那种"分段控件"的样子（`generate-screen.html` 的 tab 风格）：
            // 外壳走 `subtle()`（玻璃风格 = 主题的浅底，实色风格 = `#2a2a2e`），
            // 选中那颗仍然是主题主色 —— 所以"选了青蓝配色"时它才是青的。
            .clip(groupShape)
            .background(if (referenceSkin) NaiSkinTokens.field() else NaiSkinTokens.subtle())
            .then(if (referenceSkin) Modifier.border(1.dp, NaiSkinTokens.border(), groupShape) else Modifier)
            .padding(if (referenceSkin) 4.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        keys.forEachIndexed { index, key ->
            val on = index == mode
            val segmentShape = if (referenceSkin) RoundedCornerShape(8.dp) else NaiSkinTokens.boxShape()
            val interaction = remember(index) { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val pressed by interaction.collectIsPressedAsState()
            val segmentColor by animateColorAsState(
                targetValue = when {
                    !referenceSkin -> if (on) MaterialTheme.colorScheme.primary else Color.Transparent
                    on && pressed -> NaiSkinTokens.accentSoft(0.95f)
                    on && hovered -> NaiSkinTokens.accentSoft()
                    on -> NaiSkinTokens.accentSoft()
                    pressed -> NaiSkinTokens.accentSoft(0.72f)
                    hovered -> NaiSkinTokens.subtleHover()
                    else -> Color.Transparent
                },
                animationSpec = if (referenceSkin) tween(120) else snap(),
                label = "canvasModeSegmentBackground",
            )
            val segmentContent by animateColorAsState(
                targetValue = when {
                    on && referenceSkin -> NaiSkinTokens.accent()
                    on -> MaterialTheme.colorScheme.onPrimary
                    referenceSkin && hovered -> NaiSkinTokens.text()
                    referenceSkin -> NaiSkinTokens.muted()
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = if (referenceSkin) tween(120) else snap(),
                label = "canvasModeSegmentContent",
            )
            val pressScale by animateFloatAsState(
                targetValue = if (referenceSkin && pressed) 0.985f else 1f,
                animationSpec = if (referenceSkin) tween(120) else snap(),
                label = "canvasModeSegmentPressScale",
            )
            Surface(
                color = segmentColor,
                contentColor = segmentContent,
                shape = segmentShape,
                border = if (referenceSkin && on) BorderStroke(1.dp, NaiSkinTokens.borderStrong()) else null,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .hoverable(interaction)
                    .clickable(
                        interactionSource = interaction,
                        indication = if (referenceSkin) null else LocalIndication.current,
                    ) { onMode(index) },
            ) {
                Text(
                    t(key),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(
                        horizontal = if (LocalNaiSkin.current == NaiSkin.Reference) 12.dp else 9.dp,
                        vertical = if (LocalNaiSkin.current == NaiSkin.Reference) 7.dp else 5.dp,
                    ),
                )
            }
        }
    }
}

private fun Modifier.referenceRunGradient(start: Color, end: Color): Modifier = drawBehind {
    val directionX = 0.9396926f
    val directionY = 0.3420201f
    val halfLength = (size.width * directionX + size.height * directionY) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val startOffset = Offset(
        center.x - directionX * halfLength,
        center.y - directionY * halfLength,
    )
    val endOffset = Offset(
        center.x + directionX * halfLength,
        center.y + directionY * halfLength,
    )
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(start, end),
            start = startOffset,
            end = endOffset,
        ),
        size = size,
    )
}

private fun Color.brightened(factor: Float): Color = copy(
    red = (red * factor).coerceAtMost(1f),
    green = (green * factor).coerceAtMost(1f),
    blue = (blue * factor).coerceAtMost(1f),
)

@Composable
private fun PreviewCard(
    state: AppState,
    t: (String) -> String,
    modifier: Modifier = Modifier,
    /**
     * 是否接**画布手势**（滚轮缩放 + 鼠标拖动平移）。
     *
     * 只有宽屏（生成页双栏）才开：窄窗口时这个预览挂在一条可滚动页面里，
     * 滚轮得留给页面滚动。手机端即使开了也没用（没有滚轮，拖动又只认鼠标）。
     */
    canvasGestures: Boolean = false,
    /**
     * 画布内容（图片 + 遮罩 / 聚焦框叠加 + 手势）要**让开**的边距。
     *
     * 电脑宽屏那块中控台 / 底栏**默认停靠在边上**（用户 2026-09-26），也能在
     * 「窗口 → 分离操作面板」里**分离成可以拖走的窗口**（用户 2026-09-19）。
     * 而棋盘格背景要**铺满除侧边栏以外的整个区域**（"画布背景拓展"）。两者一分开：
     *  - **棋盘格**画在外层 Box（铺满整格，面板拖走后露出来的也是它）；
     *  - **图片**只在让开**停靠着**的面板之后的那块里 Fit 居中，所以面板分离出去、图片也不会跳，
     *    收回停靠也不会把图片压住（边距由 `GenerateScreen` 按"哪块停靠着"算出来）。
     */
    contentPadding: PaddingValues = PaddingValues(),
) {
    val preview = state.generationPreview
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    // 预览区显示"工作图"：图生图导入的图优先，否则最近生成的那张
    val path = state.workImagePath
    val maskMode = state.maskMode
    val areaState = remember { mutableStateOf(IntSize.Zero) }

    // 图片在屏幕上的实际显示矩形（ContentScale.Fit），涂遮罩时要用它把手指位置换算成归一化坐标
    val sourceWidth = state.workImageWidth
    val sourceHeight = state.workImageHeight
    val aspect = if (sourceHeight > 0) sourceWidth.toFloat() / sourceHeight.toFloat() else 0f

    // 遮罩笔迹（矢量）：涂改时按版本号重画描边 —— 完全不重建位图，所以大笔刷也不卡
    val maskStrokes = remember(state.maskVersion, path) {
        if (state.maskPaintedPercent > 0 || state.maskMode) state.maskStrokesSnapshot() else emptyList()
    }

    // ---- 预览缩放 / 平移 = **画布摆放**（用户 2026-09-26：「记忆生图区域」）----
    // 这两个值现在**从盘上读回来**（键见 `KEY_CANVAS_PREVIEW_*`）：上次停在哪儿、放大到几倍，
    // 重启之后照旧。刻意仍然**不用** `rememberSaveable` —— 它管的是"转屏 / 重组不丢"，
    // 而这里要的是**跨进程**记住，那是 `platform.kv` 的活儿（和 `panels.*` 那几块面板同一套）。
    val placementKv = LocalPlatform.current.kv
    // ---- **和无限画布 / 漫画共用同一套缩放平移**（用户 2026-09-23：「我要画布通用」✓）----
    // 初值三档一个口径（见 `AppState.canvasViewTouched` ✓）：
    //  · 这个会话里**动过**（在无限画布里放大过 ✓）⇒ 接着用共用值 ✓ —— 切档**图不跳、倍率不跳** ✓；
    //  · 没动过 ⇒ 照旧读盘上那份 ✓（跨重启记忆那条一点没动 ✓）。
    // ⚠️ 底下那 14 处 `previewScale = …` / `previewOffset = …` **一处都不用改** ✗：
    //    它们写的是这两个本地值 ✓，改动经下面那段 `snapshotFlow` 推回共用值 ✓。
    val sharedView = state.canvasViewTouched
    var previewScale by remember {
        mutableFloatStateOf(
            if (sharedView) {
                state.canvasViewScale.coerceIn(PREVIEW_MIN_SCALE, PREVIEW_MAX_SCALE)
            } else {
                (
                    placementKv.getInt(
                        KEY_CANVAS_PREVIEW_SCALE,
                        PREVIEW_SCALE_STORE_FACTOR.roundToInt(),
                    ).toFloat() / PREVIEW_SCALE_STORE_FACTOR
                    ).coerceIn(PREVIEW_MIN_SCALE, PREVIEW_MAX_SCALE)
            },
        )
    }
    var previewOffset by remember {
        mutableStateOf(
            if (sharedView) {
                Offset(x = state.canvasViewX, y = state.canvasViewY)
            } else {
                Offset(
                    x = placementKv.getInt(KEY_CANVAS_PREVIEW_X, 0).toFloat(),
                    y = placementKv.getInt(KEY_CANVAS_PREVIEW_Y, 0).toFloat(),
                )
            },
        )
    }
    // **用户真动了才推回共用值** ✓：`drop(1)` 丢掉挂载时那一发初值 ✓
    //（那写法会把盘上读回来的摆放**当场冲成默认** ✗，见 `AppState.canvasViewTouched` 的注释 ✓）。
    LaunchedEffect(state) {
        snapshotFlow { previewScale to previewOffset }
            .drop(1)
            .collect { (s, o) -> state.holdCanvasView(s, o.x, o.y) }
    }
    // 双击归位的那段动画。**手指一碰就得把它掐掉**，否则动画和捏合会互相拽
    // （两边都在写同一个 state），所以拿一个 Job 记着，随时 cancel。
    val previewScope = rememberCoroutineScope()
    var previewResetJob by remember { mutableStateOf<Job?>(null) }

    // ---- 平移（用户 2026-09-19：生图页也照画布编辑器那条规矩来）----
    // 「按住空格 + 左键拖动」或「中键拖动」才平移图片；空格是否按着住在 `AppState.canvasSpacePan`
    //（键在页面根部收，见上面 `shortcutSpaceKeys`）。`panning` 只为换光标：拖动中 = 握手。
    val spacePan = state.canvasSpacePan
    var previewPanning by remember { mutableStateOf(false) }
    val uiHost = LocalUiHost.current
    val panCursor = if (canvasGestures && (spacePan || previewPanning)) {
        uiHost.panCursor(grabbing = previewPanning)
    } else {
        Modifier
    }

    // ---- **新图到达不再复位**（用户 2026-09-26）----
    //
    // 这里原来是一段"换图 / 开新一轮生成 → 缩放归位"的 `LaunchedEffect(path, state.busy)`：
    // `path` 一变（生成完落地 / 图生图导入 / 从图库选图）或 `busy` 翻转（重做 / 再生成一轮），
    // 就把 `previewScale` 打回 `1f`、`previewOffset` 打回 `Offset.Zero`。
    //
    // 用户现在的口径是「**生图的流式与图片都按照图片的摆放来**」—— 于是那一段整个作废：
    //   · 流式预览帧（`state.generationPreview`）与生成完落地的那张图本来就画在**同一个
    //     `graphicsLayer`** 里（见下面的图片层），它们一直是跟着摆放走的；之前看着"跳回原位"
    //     是被上面那段复位推回去的，不是画的姿势不对；
    //   · 上一轮（2026-09-22）只做到"遮罩模式里点「重做」不归位"，这次把"**换图复位**"这条也去掉。
    //
    // 从此**只有两处**会动摆放，两处都是用户自己按的：
    //   · 「视图 → 重置图片位置」（见下面的信箱登记）—— 回 1 倍 + 居中，并写盘；
    //   · 在画布上**双击**（`detectTapGestures(onDoubleTap = …)`）—— 手势本身就是"归位"。
    //
    // 这一段保留下来的只有两件与摆放无关的收尾：换图 / 新一轮时**掐掉正在播的双击归位动画**
    //（否则它会继续把 `previewScale` 往 1 拽），以及把平移光标收回来。
    LaunchedEffect(path, state.busy) {
        previewResetJob?.cancel()
        previewResetJob = null
        previewPanning = false
    }

    // ---- 摆放**写盘**：停下来之后写一次 ----
    // 只在下面那个"静置 400ms"的防抖里调（拖动的每一帧、滚轮的每一格都改这两个状态，
    // 每帧写会把 `prefs.json` 磨穿 —— 和侧栏宽度那条"松手才提交"同一条规矩）。
    fun persistPlacement() {
        placementKv.edit()
            .putInt(KEY_CANVAS_PREVIEW_X, previewOffset.x.roundToInt())
            .putInt(KEY_CANVAS_PREVIEW_Y, previewOffset.y.roundToInt())
            .putInt(
                KEY_CANVAS_PREVIEW_SCALE,
                (previewScale * PREVIEW_SCALE_STORE_FACTOR).roundToInt(),
            )
            .apply()
    }
    // ⚠️ 用 `snapshotFlow` 而**不是**把这两个值当 `LaunchedEffect` 的 key：
    //    写成 key 就等于在**组合期**读它们，于是拖动 / 缩放的**每一帧都要重组一次
    //    `PreviewCard`** —— 那正是 2026-09-19 "拖拽不跟手"那条老账的根因
    //   （见宽屏分支里 `promptOffsetState` 的说明）。`snapshotFlow` 在协程里观察它们，
    //    重组一次都不会多；`collectLatest` + `delay` 就是那个"静置 400ms"的防抖。
    LaunchedEffect(Unit) {
        snapshotFlow { previewScale to previewOffset }
            .collectLatest {
                delay(PREVIEW_PLACEMENT_SAVE_DELAY_MS)
                persistPlacement()
            }
    }
    // 离开组合时**补一次**：上面那个防抖的 400ms 里用户要是已经走开了（切到图库页、
    // 或把窗口拉窄换成另一套布局），那次写入会跟着协程一起被取消 —— 补在 `onDispose` 上就不会丢。
    DisposableEffect(Unit) {
        onDispose { persistPlacement() }
    }

    // 窗口被拉小之后，从盘上恢复的偏移可能落在可视区之外（"图怎么不见了"）——
    // 量到画布尺寸后按拖动时同一条夹取规矩收一次（`±area/2`，和按下拖动那段一致）。
    LaunchedEffect(areaState.value) {
        val area = areaState.value
        if (area.width <= 0 || area.height <= 0) return@LaunchedEffect
        val clamped = Offset(
            x = previewOffset.x.coerceIn(-area.width / 2f, area.width / 2f),
            y = previewOffset.y.coerceIn(-area.height / 2f, area.height / 2f),
        )
        if (clamped != previewOffset) previewOffset = clamped
    }

    // ---- 「视图 → 重置图片位置」：外壳发号，这里收号（见 `CanvasViewCommands`）----
    // ⚠️ 只在这个画布**组合着**的时候登记，离开时注销（和 `CanvasEditor` 登记 `LocalEditorShortcuts`
    //    同一个规矩）；没人登记时外壳那条菜单就是"什么也不做"。
    // ⚠️ 登记**不看 `canvasGestures`**：窄窗口那套预览挂的是同一条菜单，也该能被重置
    //    （它只是没有滚轮 / 拖动那套手势而已）。
    val canvasViewCommands = LocalCanvasViewCommands.current
    DisposableEffect(canvasViewCommands) {
        canvasViewCommands.register { id ->
            when (id) {
                CANVAS_CMD_RESET_PLACEMENT -> {
                    previewResetJob?.cancel()
                    previewResetJob = null
                    previewScale = 1f
                    previewOffset = Offset.Zero
                    previewPanning = false
                    // 立刻写盘，不等那 400ms 的防抖：用户点完就该记住"已经归位了"
                    persistPlacement()
                    true
                }
                CANVAS_CMD_ZOOM_IN, CANVAS_CMD_ZOOM_OUT -> {
                    val factor = if (id == CANVAS_CMD_ZOOM_IN) 1.2f else 1f / 1.2f
                    previewScale = (previewScale * factor)
                        .coerceIn(PREVIEW_MIN_SCALE, PREVIEW_MAX_SCALE)
                    persistPlacement()
                    true
                }
                else -> false
            }
        }
        onDispose { canvasViewCommands.register(null) }
    }

    // ---- **三档共用同一张画布**（用户 2026-09-24：「切换时**只改 ui，画布不变**」✓）----
    // ⚠️ 这一档以前画的是**文件**（`FileImage(workImagePath)` ✓）—— 于是"普通模式看到的这张图"
    //    和"无限画布里那张画布"是**两份东西** ✓：切档时一边按文件重开画布、一边又画回文件 ✓，
    //    用户一眼就看出来「这不过是复制画布，不是同一张画布」✗。现在两边画的是**同一块位图**
    //    （`AppState.infiniteCanvasImage()` ✓ —— 按 `infiniteRevision` 缓存的同一份 ✓）。
    // ⚠️ 顺序：**流式那一张优先** ✓（正在生成时该看到的是流式帧 ✓，不是上一张画布 ✓）。
    // ⚠️ 只有**手上确实有工作图**时才拿画布当画面 ✗：不然"还没生成过"那一档会从
    //   "空态提示"变成"一张透明画布" ✓（无限画布那一档从空白起步是它自己的事 ✓）。
    val sharedCanvas = if (path == null) null else state.infiniteCanvasImage()
    // 有图就把画布备好（没图不建：普通档空着的时候没必要占几十 MB ✓，到无限档再建 ✓）
    LaunchedEffect(path) { if (path != null) state.syncSharedCanvas() }

    Box(
        modifier
            // ⚠️ **画布这一格自己铺一层不透明底**（用户 2026-09-19：「图片默认区域也有 bug」）。
            // 电脑端为了做整体美化，页面底色是半透明的（壁纸磨砂层会透上来），画布如果也跟着透，
            // 图片周围就会泛一层壁纸的蓝灰 —— 看着像渲染错了。`background.copy(alpha = 1f)`
            // 取的是**同一个主题底色、但不透明**，所以观感一致、又不透壁纸。
            // 参考稿画布与周围是同一张渐变底；不透明主题底会把根层青 / 靛光晕和外层网格盖掉。
            // 普通毛玻璃仍保留不透明底，避免壁纸渗进图片边缘；显式画布色由 canvasBackdrop 优先绘制。
            .background(
                if (referenceSkin) Color.Transparent else MaterialTheme.colorScheme.background.copy(alpha = 1f),
            )
            // ⚠️ **必须裁掉越界的那部分**（用户 2026-09-19 报的 bug：电脑端「拖拽图片时图片会
            // 显示在提示词栏和底栏上」）。
            //
            // 图片是**用 `graphicsLayer` 缩放 + 平移**的（滚轮缩放、鼠标拖动平移），而
            // `graphicsLayer` 只影响绘制、**不改子节点的布局边界** —— 不裁剪时图就会画到
            // 自己这一格外面去：宽屏下左栏是「提示词 / 角色 / 参数」，下面是
            // `PromptActionBar` + `RunBar`，而这几块都是 [`NaiGlass`] 的**半透明**底色，
            // 于是越界的那块图就从它们背后透出来，看着像"图片盖在栏上"。
            //
            // 裁掉之后图只在自己的画布格子里活动（和手机端一致：手机上这个预览也是
            // 捏合缩放的，同样只该在自己那块里）。
            .clipToBounds()
            // 空格按着（或正在拖）= 手型光标，和画布编辑器同一套（用户 2026-09-16 指定的那两只手）
            .then(panCursor),
        contentAlignment = Alignment.Center,
    ) {
        // 有没有图：没有就没什么可缩放的，也不接手势
        // ⚠️ **共用画布也算"有图"** ✓（画布在、只是还没生成过时，它是一张透明底 ✓，也要能框着生成 ✓）
        val hasImage = preview != null || sharedCanvas != null || path != null

        // 画布底：**三档统一的格子背景**（用户 2026-09-24：「**统一画布背景，用漫画模式的格子背景，
        // 颜色再深一些**」✓）—— 见 `ui/CanvasBackdrop.kt` ✓（底色 + 18dp 方格 ✓，唯一一处 ✓）。
        //
        // 沿革：空态不铺（像画布丢了 ✗）→ 一直铺棋盘格 ✓ → 用户 2026-09-26 改成纯色 ✓ →
        //         调色板可改画布颜色（`settings.canvasColor` ✓）→ **本轮统一成格子背景** ✓。
        //  · 用户自选了画布颜色 ⇒ 那颜色当**底** ✓、格子照样画在上面 ✓（深底自动换浅线 ✓）；
        //  · 没设 ⇒ 统一的 `CanvasBackdropColor` ✓（比原来那版浅灰深一些 ✓）。
        val canvasColor = canvasBaseColor(state.settings.canvasColor)
        Box(
            Modifier
                .fillMaxSize()
                // 画布底（底色 + 格子）**唯一一处**在 `canvasBackdrop` 里：
                // 毛玻璃 = 浅灰底 + 18dp 格；青蓝玻璃 = **透明底** + 24dp 格
                //（参考稿 `.canvas-pane { background-color: transparent }`，
                //  让页面渐变透上来 ⇒ 画布区和四周是同一片背景）。
                // 用户自选的画布颜色两套风格下都优先。
                .canvasBackdrop(canvasColor, drawGrid = !referenceSkin || canvasColor != ReferenceCanvasColor),
        )

        // ---- 图片 / 叠加 / 手势：只占"让开面板之后"的那块 ----
        // 这一层量出来的尺寸就是 `areaState`（涂遮罩的坐标换算、缩放锚点都用它），
        // 所以图片与叠加永远自洽；面板拖走/拖回都不会让图片跳。
        Box(
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .onSizeChanged { areaState.value = it },
            contentAlignment = Alignment.Center,
        ) {
        // 图片层：缩放在这里（`graphicsLayer` 的缩放原点是图层中心，和上面的锚点算法对得上）。
        // 没有方格底、没有平移 —— 图片就待在它一直待的位置（用户否掉了画布模式）。
        if (hasImage) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = previewScale
                        scaleY = previewScale
                        translationX = previewOffset.x
                        translationY = previewOffset.y
                    },
            ) {
                when {
                    preview != null -> Image(
                        bitmap = preview,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )

                    // **共用画布**：和无限画布那一档画的是**同一块位图** ✓
                    //（`ContentScale.Fit` + 上面那层 `graphicsLayer` 的缩放平移 ✓ —— 无限画布那边
                    //  是 `fit × scale` 的同一个式子 ✓，所以两档的"倍率 / 位置"逐字一致 ✓）
                    sharedCanvas != null -> Image(
                        bitmap = sharedCanvas,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )

                    path != null -> FileImage(
                        path,
                        maxDimension = 1440,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            if (referenceSkin) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val emptyCanvasAccent = NaiSkinTokens.accent()
                    Canvas(Modifier.size(40.dp)) {
                        val unit = size.minDimension / 40f
                        val stroke = Stroke(width = 2f * unit)
                        drawRoundRect(
                            color = emptyCanvasAccent,
                            topLeft = Offset(4f * unit, 5f * unit),
                            size = Size(32f * unit, 30f * unit),
                            cornerRadius = CornerRadius(3f * unit),
                            style = stroke,
                        )
                        drawCircle(
                            color = emptyCanvasAccent,
                            radius = 3f * unit,
                            center = Offset(14f * unit, 15f * unit),
                            style = stroke,
                        )
                        val mountains = androidx.compose.ui.graphics.Path().apply {
                            moveTo(8f * unit, 29f * unit)
                            lineTo(17f * unit, 20f * unit)
                            lineTo(23f * unit, 26f * unit)
                            lineTo(27f * unit, 22f * unit)
                            lineTo(33f * unit, 29f * unit)
                        }
                        drawPath(
                            path = mountains,
                            color = emptyCanvasAccent,
                            style = Stroke(width = 2f * unit),
                        )
                    }
                    Text(
                        t("generate.previewEmpty"),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.5.sp,
                            fontFamily = NaiSkinTokens.ReferenceFontFamily,
                        ),
                        color = NaiSkinTokens.faint(),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    t("generate.previewEmpty"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // 遮罩叠加：**不在遮罩模式也画**（文生图界面同样要看得见自己涂过哪里）
        if (maskStrokes.isNotEmpty() && preview == null && sourceWidth > 0) {
            MaskStrokeOverlay(
                strokes = maskStrokes,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                area = areaState.value,
                // 笔迹按**图片当前实际显示的矩形**画（含缩放 / 平移）：放大、拖动之后
                // 涂上去的那一笔仍旧贴在同一个像素上（用户 2026-09-22：「遮罩…跟随图片」）
                scale = previewScale,
                offset = previewOffset,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 聚焦框叠加（虚线框 + 外圈"最小上下文"）：在遮罩模式里拖出来的那个框
        val focusRect = state.maskFocusRect
        if (focusRect != null && preview == null && sourceWidth > 0) {
            MaskFocusOverlay(
                focus = focusRect,
                contextPixels = state.settings.inpaintFocusContext,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                area = areaState.value,
                // 聚焦框和笔迹同口径：跟着图片的缩放 / 平移走（别在放大后框到一半图上）
                scale = previewScale,
                offset = previewOffset,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 直接在图片上涂：单指涂抹（涂上即生效，没有"确定"这一步）
        //
        // ---- 遮罩模式下的手势口径（用户 2026-09-22 定稿）----
        // 「遮罩模式只弹出窗口，图片不动，并且图片和主页一样可以滚轮调整大小和按住空格拖动，
        //   遮罩和聚焦框跟随图片」。所以那套"进遮罩就把手势关掉"的做法作废了：
        //  · **手指 / 左键 = 画笔**（涂遮罩 / 拖聚焦框）—— 默认动作，没变；
        //  · **按住空格 + 左键 / 中键 = 平移**，**滚轮 = 缩放** —— 和主页同一套，
        //    闸门也沿用主页那条 `spacePan || middleDown`；这两个要**先判**，
        //    命中就整段走平移、一笔都不涂（否则按住空格拖图会顺手在图上拉一道）。
        //  · 滚轮挂在**同一个 Box 的第二个 `pointerInput`** 上：同一条修饰符链上的两个
        //    `pointerInput` 都能收到事件，一个只认 `scrollDelta`、一个只认按下 / 拖动，互不打扰；
        //    而**不能再挂成兄弟层** —— Compose 的命中测试只喂最上面那一层，
        //    兄弟层会把画笔整个饿死（这就是原来 `if (hasImage && !maskMode)` 必须二选一的原因）。
        //  · 坐标一律走 `shownImageRect`（Fit 矩形 + 当前缩放 / 平移），所以"手指按在哪个像素"
        //    和"涂到哪个像素"永远是同一个 —— 放大、拖走、再涂都对得上（这才是"跟随图片"）。
        if (maskMode && preview == null && path != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    // ① 滚轮缩放（只认滚轮，不碰按下 / 拖动）。只在宽屏挂（同主页：窄窗口滚轮留给页面滚动）
                    .pointerInput(canvasGestures, aspect) {
                        if (!canvasGestures) return@pointerInput
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                val scroll = change.scrollDelta.y
                                if (scroll == 0f) continue
                                previewResetJob?.cancel()
                                previewResetJob = null
                                val next = (previewScale * (1f - scroll * PREVIEW_WHEEL_ZOOM_STEP))
                                    .coerceIn(PREVIEW_MIN_SCALE, PREVIEW_MAX_SCALE)
                                if (next != previewScale) {
                                    val area = areaState.value
                                    val anchor = Offset(
                                        x = change.position.x - area.width / 2f,
                                        y = change.position.y - area.height / 2f,
                                    )
                                    val ratio = next / previewScale
                                    // ⚠️ **这里不再"缩到 ≤1 倍就归零"**（用户 2026-09-26：摆放要记住，
                                    //    缩小时别把用户摆好的位置吃掉）。缩放照旧夹在
                                    //    `PREVIEW_MIN_SCALE..PREVIEW_MAX_SCALE`，偏移只受下面那条
                                    //    `±area/2` 夹取约束（防丢图，在 `LaunchedEffect(areaState)` 里）。
                                    //    和主页那份滚轮口径**逐字一致**（见宽屏那段的同名分支）。
                                    previewOffset = Offset(
                                        x = anchor.x - (anchor.x - previewOffset.x) * ratio,
                                        y = anchor.y - (anchor.y - previewOffset.y) * ratio,
                                    )
                                    previewScale = next
                                }
                                change.consume()
                            }
                        }
                    }
                    // ② 按下：先判平移，再落到画笔
                    // ⚠️ `aspect` 必须进 key：它是组合期算出来的**普通 Float**（不是 `mutableStateOf`
                    //    的委托），协程不会因为重组而重启 —— 换了一张别的比例的图之后
                    //    `pointerInput` 不重启的话，这里的换算会一直用旧比例（笔就偏了）。
                    //    `previewScale` / `previewOffset` **不要**进 key：它们是委托状态，
                    //    协程里每次读都是最新的；进 key 反而会在滚轮缩放的每一步打断正在画的那一笔。
                    .pointerInput(state.maskFocusTool, aspect) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // ⚠️ 这一下要是被上层（面板拖动 / 拉边缘，见宽屏那段手势层）吃掉了，
                            // 就不能再当成"开始涂遮罩"—— 否则在面板上按一下会顺手在图上点一笔
                            if (down.isConsumed) return@awaitEachGesture

                            // `state.canvasSpacePan` **现读**：它是快照状态，按下/抬起都会刷新
                            //（见 `shortcutSpaceKeys`；别在这里提前存成局部 val —— 指针协程不会因为
                            // 重组而重启，存下来的那个值会一直是按下空格之前的）。
                            val spaceHeld = state.canvasSpacePan
                            val middleDown = down.type == PointerType.Mouse &&
                                currentEvent.buttons.isTertiaryPressed

                            // ---- 平移：按住空格 + 左键 / 中键 ----
                            if (canvasGestures && (spaceHeld || middleDown)) {
                                // 和主页同一条口径：**只有按在图上**才算平移（点空白处不动画面）
                                val shown = shownImageRect(
                                    areaState.value,
                                    aspect,
                                    previewScale,
                                    previewOffset,
                                )
                                if (shown.width <= 0f || !shown.contains(down.position)) {
                                    return@awaitEachGesture
                                }
                                previewResetJob?.cancel()
                                previewResetJob = null
                                previewPanning = true
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) break
                                        val change = pressed[0]
                                        val delta = change.position - change.previousPosition
                                        if (delta != Offset.Zero) {
                                            val area = areaState.value
                                            previewOffset = Offset(
                                                x = (previewOffset.x + delta.x)
                                                    .coerceIn(-area.width / 2f, area.width / 2f),
                                                y = (previewOffset.y + delta.y)
                                                    .coerceIn(-area.height / 2f, area.height / 2f),
                                            )
                                            change.consume()
                                        }
                                    }
                                } finally {
                                    // 中途被打断（工具被切、组合离开）也要把手型收回来
                                    previewPanning = false
                                }
                                return@awaitEachGesture
                            }
                            // 空格 / 中键按着时**一笔都不涂**：上面那条"按在图上"没通过（点在空白处）
                            // 也走这里 —— 免得按住空格点空白反而画上一笔。
                            if (spaceHeld || middleDown) return@awaitEachGesture

                            val start = toMaskPoint(
                                down.position,
                                shownImageRect(areaState.value, aspect, previewScale, previewOffset),
                            )
                            if (start == null) return@awaitEachGesture

                            // 聚焦框工具：这一下是**拖框**，不涂遮罩
                            if (state.maskFocusTool) {
                                var focusEnd = start
                                state.updateMaskFocus(focusRectOf(start, focusEnd))
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    val change = pressed[0]
                                    toMaskPoint(
                                        change.position,
                                        shownImageRect(
                                            areaState.value,
                                            aspect,
                                            previewScale,
                                            previewOffset,
                                        ),
                                    )?.let {
                                        focusEnd = it
                                        state.updateMaskFocus(focusRectOf(start, focusEnd))
                                    }
                                    change.consume()
                                }
                                return@awaitEachGesture
                            }

                            state.startMaskStroke()
                            state.paintMask(start)
                            var jumpedToPinch = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.isEmpty()) break
                                if (pressed.size >= 2) {
                                    // 涂改时手指就是画笔，不接画布手势；
                                    // 双指只用来"中止这一笔"，免得画歪
                                    jumpedToPinch = true
                                    pressed.forEach { it.consume() }
                                    continue
                                }
                                if (jumpedToPinch) continue
                                val change = pressed[0]
                                val point = toMaskPoint(
                                    change.position,
                                    shownImageRect(
                                        areaState.value,
                                        aspect,
                                        previewScale,
                                        previewOffset,
                                    ),
                                )
                                if (point != null) {
                                    state.paintMask(point)
                                    change.consume()
                                }
                            }
                            state.endMaskStroke()
                        }
                    },
            )
        }

        // 手势层：**只认双指捏合缩放**（锚点在双指之间），双击 = 归位。
        // ⚠️ 单指**完全不接** —— 图片待在固定位置（用户口径），单指横滑也还给图库翻页。
        //    双指**不做平移**：图不跟着手指走，只有"在哪捏就在哪放大"这一件事。
        //
        // ⚠️ 遮罩模式下这一层**不挂**（`!maskMode`）：那一层里手指是画笔，挂成兄弟层会把画笔
        //    整个饿死（Compose 的命中测试只喂最上面那一层）。所以**滚轮缩放 + 按住空格拖动**
        //    在遮罩模式下由**画笔那个 Box 自己**再挂一份（见上面那段 `pointerInput`）——
        //    两边口径逐字一致（同一个锚点公式、同一条 `spacePan || middleDown` 闸门），
        //    改这边的时候记得同步那边。
        if (hasImage && !maskMode) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        twoFingerPinchGestures { centroid, zoom ->
                            // 手指接管：先把归位动画掐掉，免得两边抢同一个 state
                            previewResetJob?.cancel()
                            previewResetJob = null
                            val next = (previewScale * zoom)
                                .coerceIn(PREVIEW_MIN_SCALE, PREVIEW_MAX_SCALE)
                            val area = areaState.value
                            // ⚠️ **这里不再"缩到 ≤1 倍就归零"**（用户 2026-09-26：摆放要记住，
                            //    缩小时别把用户摆好的位置吃掉 —— "记住摆放"与"缩小即回原位"直接冲突）。
                            //    偏移只受 `±area/2` 那条夹取约束（防丢图）。
                            //    以双指之间的中点为锚点：手指底下那块画面留在原地。
                            //    设中点相对可视区中心的向量 u，变换是
                            //      screen = center + scale * (content - center) + offset，
                            //    要让 u 处的内容不动，解出 offset' = u - (scale'/scale)(u - offset)。
                            val dx = centroid.x - area.width / 2f
                            val dy = centroid.y - area.height / 2f
                            // `previewScale` 理论上不会 ≤0（夹在 0.2..6），留着只是防"除法炸掉"的老护栏
                            val ratio = if (previewScale > 0f) next / previewScale else 1f
                            previewOffset = Offset(
                                x = dx - (dx - previewOffset.x) * ratio,
                                y = dy - (dy - previewOffset.y) * ratio,
                            )
                            previewScale = next
                        }
                    }
                    // 双击 **= 回到 1 倍 + 居中**：放大之后想立刻回到原样，用这个。
                    // ⚠️ 挂在捏合手势**后面**：修饰符链里后一个先拿到事件，双击由它认领；
                    //    它只 consume 按下与抬起，捏合期间的移动事件照旧流给上面的手势。
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                previewResetJob?.cancel()
                                previewResetJob = previewScope.launch {
                                    // ⚠️ 这里**不要**给 `tween` 写显式类型参数（它有个带
                                    // TwoWayConverter 的重载，写了会选中那个然后报个看不懂的错）。
                                    // 靠 `animate` 的形参类型推断即可。
                                    launch {
                                        animate(
                                            previewScale,
                                            1f,
                                            animationSpec = tween(durationMillis = 220),
                                        ) { v, _ -> previewScale = v }
                                    }
                                    launch {
                                        animate(
                                            previewOffset.x,
                                            0f,
                                            animationSpec = tween(durationMillis = 220),
                                        ) { v, _ -> previewOffset = previewOffset.copy(x = v) }
                                    }
                                    launch {
                                        animate(
                                            previewOffset.y,
                                            0f,
                                            animationSpec = tween(durationMillis = 220),
                                        ) { v, _ -> previewOffset = previewOffset.copy(y = v) }
                                    }
                                }
                            },
                        )
                    }
                    // ---- 电脑：**滚轮缩放 + 鼠标拖动平移**（用户 2026-09-16：把预览做成"真画布"）----
                    // 只在宽屏挂（`canvasGestures`）：窄窗口时这个预览在一条**可滚动的页面**里，
                    // 滚轮得留给页面滚动，抢过来很难受。
                    // ⚠️ 拖动**只认鼠标**（`PointerType.Mouse`）：手机的单指横滑必须继续留给图库翻页
                    //    —— 那是上面那段注释里有意定的口径，加画布不能把它破坏掉。
                    // ⚠️ 平移会夹住（图片中心不许跑出画布）：否则一把手甩出去，图就找不回来了
                    //    （双击归位虽然能救，但用户不该被迫知道这一点）。
                    .pointerInput(canvasGestures, aspect) {
                        if (!canvasGestures) return@pointerInput
                        awaitPointerEventScope {
                            var panning = false
                            // 诊断限流：一次按下只写一行日志（见下面的 `pan check`）
                            var panLogged = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                if (!change.pressed) panLogged = false
                                // ⚠️ 已经被上层吃掉的事件（面板拖动 / 拉边缘，见宽屏那段 Initial 阶段的手势层）
                                // **不要**再拿来平移图片 —— 否则"面板和图片叠在一起"时会两者一起动
                                //（用户 2026-09-19 报的）。
                                if (change.isConsumed) continue

                                // 1) 滚轮 = 围绕鼠标位置缩放（和双指捏合同一套锚点公式）
                                val scroll = change.scrollDelta.y
                                if (scroll != 0f) {
                                    previewResetJob?.cancel()
                                    previewResetJob = null
                                    val next = (previewScale * (1f - scroll * PREVIEW_WHEEL_ZOOM_STEP))
                                        .coerceIn(PREVIEW_MIN_SCALE, PREVIEW_MAX_SCALE)
                                    if (next != previewScale) {
                                        val area = areaState.value
                                        val anchor = Offset(
                                            x = change.position.x - area.width / 2f,
                                            y = change.position.y - area.height / 2f,
                                        )
                                        val ratio = next / previewScale
                                        // ⚠️ **这里不再"缩到 ≤1 倍就归零"**（用户 2026-09-26：摆放要记住，
                                        //    缩小时别把用户摆好的位置吃掉）。偏移只受 `±area/2` 那条
                                        //    夹取约束；要归位请用「视图 → 重置图片位置」或双击。
                                        previewOffset = Offset(
                                            x = anchor.x - (anchor.x - previewOffset.x) * ratio,
                                            y = anchor.y - (anchor.y - previewOffset.y) * ratio,
                                        )
                                        previewScale = next
                                    }
                                    change.consume()
                                    continue
                                }

                                // 2) 鼠标拖动 = 平移（触摸不接，见上）。
                                //    ⚠️ 用户 2026-09-19：「只有按住空格才能拖动图片的设定，在生图页
                                //    也使用这个」—— 光标按着**空格**（或**中键**，和画布编辑器一样）
                                //    才拖得动；否则左键在图上什么都不做（以前是一按就拖，容易误拖）。
                                if (change.type != PointerType.Mouse) continue
                                val middleDown = event.buttons.isTertiaryPressed
                                // ⚠️ 空格**现读 `state.canvasSpacePan`**（不再用上面那个局部 `spacePan`）：
                                // 局部 val 是在组合时取的值，而这个指针协程不会因为重组就重启 ——
                                // 存下来那个值会一直停在"还没按空格"的那一刻，闸门就永远是关的。
                                val panAllowed = state.canvasSpacePan || middleDown
                                if (change.pressed && !panning) {
                                    if (!panAllowed) continue
                                    // ⚠️ **只在图片范围内的那一下才算平移**（用户 2026-09-19：
                                    // 「图片也只有点击图片内时才能拖动」）—— 点在图片外的空白处
                                    // （棋盘格那一圈）不动画面，免得"想点空白处结果图飘走了"。
                                    // 判定用的是**屏幕上真实显示的那块图**（`shownImageRect`：
                                    // Fit 出来的矩形再按当前缩放 + 平移换算过去）—— 放大之后
                                    // 图比 Fit 矩形大，那一圈也得算"图内"。
                                    val viewArea = areaState.value
                                    val shown = shownImageRect(
                                        viewArea,
                                        aspect,
                                        previewScale,
                                        previewOffset,
                                    )
                                    val inside = shown.width > 0f && shown.contains(change.position)
                                    // 诊断：**一次按下只写一行**（原来每个移动事件都写，日志刷了 487 行）
                                    if (!panLogged) {
                                        panLogged = true
                                        com.kallan.naistudio.platform.logInfo(
                                            "Preview",
                                            "pan check pointer=${change.position} area=$viewArea " +
                                                "aspect=$aspect shown=$shown inside=$inside " +
                                                "space=${state.canvasSpacePan} middle=$middleDown",
                                        )
                                    }
                                    if (!inside) continue
                                    panning = true
                                    previewPanning = true
                                    previewResetJob?.cancel()
                                    previewResetJob = null
                                }
                                if (!change.pressed) {
                                    panning = false
                                    previewPanning = false
                                    continue
                                }
                                if (panning) {
                                    // ⚠️ 拖动**中途松开空格不打断**：一旦开始拖就拖到底（松手为止），
                                    // 免得手一抖松开空格，图就停在半路（编辑器那边也是这个手感）。
                                    val delta = change.position - change.previousPosition
                                    if (delta != Offset.Zero) {
                                        val area = areaState.value
                                        previewOffset = Offset(
                                            x = (previewOffset.x + delta.x)
                                                .coerceIn(-area.width / 2f, area.width / 2f),
                                            y = (previewOffset.y + delta.y)
                                                .coerceIn(-area.height / 2f, area.height / 2f),
                                        )
                                        change.consume()
                                    }
                                }
                            }
                        }
                    },
            )
        }

        // 工具条不再浮在图上（会挡住画面）；它由外层布局摆在图片**上面**那一行
        // 图生图模式：预览区右上角一个叉，退出图生图
        if (state.img2imgActive && preview == null) {
            // ⚠️ 用户 2026-09-22：「**图生图的右上角 x，放下来一些，被余额遮住了**」。
            // 宽屏（`canvasGestures` = 生成页双栏）这张预览是**顶到内容区最上沿**的，而外壳那颗
            // 余额胶囊（`AccountChip`，StudioShell 里 `align(TopEnd).padding(end = 10.dp, top = 8.dp)`）
            // 正钉在同一个角上：胶囊自身高约 26dp，占掉内容区顶端 `8..34dp` 那一条。
            // 这颗叉原来也是 `top = 8.dp`（48dp 的图标按钮 → 图标落在 `20..44dp`）→ 正好整颗被盖住、点不着。
            // 这里**只把叉往下让 44dp**（≈ 胶囊高度 + 上下留白）：top 从 8 变 52，胶囊就压不到它了。
            // ⚠️ 余额胶囊的位置**不动**（用户只要求挪叉）；窄窗口那套也不动 —— 那里预览本来就落在
            // 工具条 / 参数触发行**下面**（约 32dp 起），叉不会被胶囊盖住，别跟着往下挪。
            val exitTopPadding = if (canvasGestures) 52.dp else 8.dp
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(top = exitTopPadding, end = 8.dp),
            ) {
                IconButton(onClick = { state.exitImg2Img() }) {
                    Icon(Icons.Filled.Close, contentDescription = t("generate.exitImg2Img"))
                }
            }
        }

        if (state.busy && preview == null) {
            CircularProgressIndicator()
        }
        }
    }

    // ⚠️ 这里原来有「点图全屏看」（FullscreenImageViewer）—— 用户 2026-09-16 要求去掉：
    // 预览区现在是画布，手指归缩放/拖动，不再兼职开沉浸式看图。
    // 图库那一页的全屏看图不受影响（那是另一处入口）。
}

/**
 * 运行条的主按钮。
 *
 * 语义（用户定的口径）：
 *  · 空闲：文案「生成图片」（遮罩模式下是「重做」），点一下发请求
 *  · **生成中：按钮文案不变，按钮本身变成进度条** —— 按步数从左往右填充
 *    （有流式帧时用真实步数进度；非流式拿不到步数就用不定式进度条兜底）
 *  · **生成中再点一次 = 排队**（按此刻的参数排一张，当前这张完成后依次发）
 *  · **长按 = 取消当前生成并清空排队**（取消按钮被用户去掉了，长按是替代入口）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainRunButton(state: AppState, t: (String) -> String, modifier: Modifier = Modifier) {
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val darkTheme = when (state.settings.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val useReferenceCyanGradient = referenceSkin
    val schemePrimary = MaterialTheme.colorScheme.primary
    val schemeTertiary = MaterialTheme.colorScheme.tertiary
    val schemeOnPrimary = MaterialTheme.colorScheme.onPrimary
    fun safePaletteBlend(fraction: Float): Color {
        val candidate = lerp(schemePrimary, schemeTertiary, fraction)
        return when {
            contrastRatio(candidate, schemeOnPrimary) >= 4.5f -> candidate
            contrastRatio(schemePrimary, schemeOnPrimary) >= 4.5f -> schemePrimary
            contrastRatio(schemeTertiary, schemeOnPrimary) >= 4.5f -> schemeTertiary
            else -> candidate
        }
    }
    val idleStart = when {
        useReferenceCyanGradient && darkTheme -> Color(0xFF22D7EB)
        useReferenceCyanGradient -> Color(0xFF008DAB)
        else -> schemePrimary
    }
    val idleEnd = when {
        useReferenceCyanGradient && darkTheme -> Color(0xFF339EFA)
        useReferenceCyanGradient -> Color(0xFF067AC9)
        else -> safePaletteBlend(0.2f)
    }
    val gradientStart by animateColorAsState(
        targetValue = when {
            hovered && useReferenceCyanGradient -> idleStart.brightened(1.08f)
            hovered && !useReferenceCyanGradient -> safePaletteBlend(0.08f)
            else -> idleStart
        },
        animationSpec = tween(120),
        label = "mainRunGradientStart",
    )
    val gradientEnd by animateColorAsState(
        targetValue = when {
            hovered && useReferenceCyanGradient -> idleEnd.brightened(1.08f)
            hovered && !useReferenceCyanGradient -> safePaletteBlend(0.28f)
            else -> idleEnd
        },
        animationSpec = tween(120),
        label = "mainRunGradientEnd",
    )
    // 「无限画布」那一档：**同一颗按钮**，语义换成"按框拓展" ✓（用户 2026-09-22 ✓）。
    // ⚠️ 这一档的"忙"看的是 `infiniteRunning` ✗ 不是全局 `busy` ✓ ——
    //    用 `busy` 的话，忙的时候再点会走"排队"那条路，排的却是**文生图** ✓（见 `runInfiniteFrame` ✓）。
    val infinite = state.canvasMode == 1
    val busy = if (infinite) state.infiniteRunning else state.busy
    // 文案也从 `state.runAction` 取（和分派同一个判定）：遮罩模式下一律是「重做」，
    // 免得"按钮写着重做、点下去其实是文生图"这种自相矛盾（2026-09-16 的 bug）。
    val label = when {
        infinite -> t("generate.runInfinite")
        state.canvasMode == 2 -> t("generate.runTextToImage")
        state.runAction == AppState.RunAction.INPAINT -> t("generate.runRedo")
        state.img2imgActive -> t("generate.runImg2Img")
        state.batchCount > 1 ->
            "${t("generate.runGenerateCountPrefix")}${state.batchCount}${t("generate.runGenerateCountSuffix")}"
        else -> t("generate.runTextToImage")
    }
    /**
     * 能不能点。
     *
     * ⚠️ 这里**故意不看 `hasToken`**（2026-09-16 改）：没配 token 时按钮以前是"禁用"的，
     * 而**禁用没有任何视觉变化**（见下面的容器色）—— 结果就是"按钮亮着、点了没反应"，
     * 用户报的"生成图片按钮点击不了"有一半是这个观感问题。
     * 现在没 token 也让它可点：点下去 `generateOrInpaint()` 会给一句
     * 「请先配置 token」的白话提示，比死按钮强。
     */
    val enabled = infinite || state.canvasMode == 2 || busy || state.maskPaintedPercent > 0 ||
        state.maskFocusRect != null || !state.maskMode
    val pressOffset by animateDpAsState(
        targetValue = if (!referenceSkin && pressed && enabled && !busy) 1.dp else 0.dp,
        animationSpec = tween(120),
        label = "mainRunPressOffset",
    )
    // 主按钮做成**圆角方形**（用户 2026-09-22：「把双端的生成图片按钮改成方形的，要有圆角」✓）——
    // 原来是**完全胶囊**（`NaiShape.Pill` = 50dp 圆角，两端是半圆 ✗）；
    // 现在用 `NaiShape.Field`（12dp 圆角 ✓）：**四条边是直的、只有四个角是圆的** ✓。
    val shape = if (referenceSkin) RoundedCornerShape(12.dp) else NaiShape.Field
    val fraction = state.generationPreviewProgress.coerceIn(0f, 1f)
    val showSteps = state.generationPreviewTotalSteps > 0
    val runContentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        referenceSkin && !busy && useReferenceCyanGradient -> if (darkTheme) Color(0xFF062534) else Color.White
        referenceSkin && !busy -> readableOnGradient(
            preferred = MaterialTheme.colorScheme.onPrimary,
            start = gradientStart,
            end = gradientEnd,
            fallback = MaterialTheme.colorScheme.onSurface,
        )
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Surface(
        shape = shape,
        // 三种状态三种颜色：**生成中**（半透明）/ **不可点**（明显压暗）/ 正常。
        // 之前"不可点"和"正常"一模一样，用户看着是亮的、点了没反应（2026-09-16 报的 bug）。
        color = when {
            busy -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            referenceSkin -> Color.Transparent
            else -> MaterialTheme.colorScheme.primary
        },
        contentColor = runContentColor,
        modifier = modifier
            .height(if (referenceSkin) 44.dp else 40.dp)
            .then(
                if (referenceSkin && enabled && !busy) {
                    Modifier.shadow(
                        elevation = 6.dp,
                        shape = shape,
                        ambientColor = Color(0x2E00B8E8),
                        spotColor = Color(0x2E00B8E8),
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .then(
                if (referenceSkin && enabled && !busy) {
                    Modifier.referenceRunGradient(gradientStart, gradientEnd)
                } else {
                    Modifier
                },
            )
            .then(if (referenceSkin && enabled && !busy) Modifier.hoverable(interaction) else Modifier)
            .graphicsLayer {
                translationY = pressOffset.toPx()
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = {
                    if (busy && !infinite) {
                        // 生成中再点 = 按当前参数排一张（无限画布那一档不排队：见上面的 `infinite` 注释）
                        state.queueCurrentParams()
                    } else {
                        state.generateOrInpaint()
                    }
                },
                onLongClick = {
                    if (busy || state.queuedJobs.isNotEmpty()) state.cancelGeneration()
                    else state.clearQueue()
                },
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (busy) {
                if (showSteps) {
                    // 进度填充：按步数从左往右长（按钮看上去就是一条进度条）
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.22f)),
                    )
                } else {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                    ) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
            // **积分预测**（用户 2026-09-24：「给双端加上积分预测功能，显示在生成按钮上，
            // 显示 [数字] 闪电图标」✓）——
            //  · 数字来自 `state.runAnlasEstimate` ✓（公式照参考仓库抄的 ✓，见 `models/AnlasCost.kt` ✓）；
            //  · `null`（没配 token / 参数算不出来 ✓）就**什么都不画** ✗ ——
            //    宁可空着，也不显示一个"猜的"或负数 ✓（本仓库最忌讳假控件 ✓）。
            // ⚠️ 用 `Row` 把「文案 + 闪电 + 数字」排成一行 ✓（它们是**并排**的 ✓ 不是三段文字 ✓）——
            //    直接塞进这个 `Box` 只会三层叠在同一处 ✓。
            val cost = state.runAnlasEstimate
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    maxLines = 1,
                    style = if (referenceSkin) {
                        LocalTextStyle.current.copy(
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.1.sp,
                        )
                    } else {
                        LocalTextStyle.current
                    },
                )
                if (cost != null) {
                    Spacer(Modifier.width(6.dp))
                    // ⚠️ 用户 2026-09-24：「**数字在左边，闪电标志在右边，闪电标志加大点**」✓ ⇒
                    //    顺序反过来（原来闪电在前 ✗）+ 图标从 14dp 提到 **18dp** ✓。
                    //    数字那一档字号不动 ✓（位数多时收一档的老口径还在 ✓）。
                    Text(
                        text = cost.toString(),
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(3.dp))
                    Icon(
                        imageVector = BoltIcon,
                        contentDescription = t("generate.anlasCost"),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 运行条
// ---------------------------------------------------------------------------

/**
 * 运行条。左边是**导入图片**，中间主按钮，右边两颗：**铅笔（占位）** 与 **框选/遮罩开关**。
 *
 * （2026-09-16 用户口径：临时图库去掉；铅笔与新图标都放右边；铅笔先占位，新功能之后再做。）
 *
 * 主按钮的语义由**遮罩模式**决定（用户定的口径）：
 *  · 遮罩关着 → 「生成图片」：按提示词生成新图（图上有旧遮罩也照样生成，遮罩只是显示着）
 *  · 遮罩开着 → 「重做」：用当前图当底图 + 图上涂的遮罩跑一次 infill（提示词还是正面提示词框）
 * 两者都从 `state.generateOrInpaint()` 进去，UI 只负责把当前语义显示出来。
 */
/**
 * 「空格 = 按住平移」这条口径的**护栏**：画布旁边这几颗按钮**一律不许拿焦点**。
 *
 * 用户 2026-09-22：「在主页时，按空格是进遮罩模式，变为按住空格可以拖动图片」。
 * 查清的原因见 `shortcutSpaceKeys` 的注释 —— 是 **Compose 的 `clickable` 把空格当"激活"键**
 * （按下发 `PressInteraction`、**抬起才 `onClick`**），而桌面端鼠标点过的按钮会**一直拿着焦点**。
 * 所以只要这几颗按钮还收得到键事件，"点一下遮罩开关 → 按空格拖图"就一定会再切一次遮罩模式
 *（那颗「生成图片」更危险：空格会直接发一次生图；铅笔会开画布编辑器）。
 *
 * 掐掉焦点之后它们就再也收不到空格，而"按住空格 + 左键 / 中键拖动 = 平移"走的是
 * `AppState.canvasSpacePan` + 预览层自己的指针处理，和焦点无关，**照旧可用**。
 * 代价：Tab 键遍历不到这几颗按钮（这个 App 是鼠标操作的画布类工具，取舍如此）。
 */
private fun readableOnGradient(
    preferred: Color,
    start: Color,
    end: Color,
    fallback: Color,
): Color {
    val candidates = listOf(preferred, Color.Black, Color.White, fallback)
    fun minimumContrast(color: Color) = minOf(
        contrastRatio(color, start),
        contrastRatio(color, end),
    )
    return candidates.firstOrNull { minimumContrast(it) >= 4.5f }
        ?: candidates.maxBy(::minimumContrast)
}

private val NoButtonFocus = Modifier.focusProperties { canFocus = false }

@Composable
private fun RunBar(
    state: AppState,
    t: (String) -> String,
    onOpenParams: () -> Unit,
    onToggleMask: () -> Unit,
    onImportImage: () -> Unit,
    /**
     * 电脑宽屏 = false：**导入图 / 铅笔 / 遮罩**这三颗**不放这里**了，它们搬到画布右边缘那条
     * 竖向工具栏上（用户 2026-09-26）。窄窗口 / 手机那套布局仍为 true —— 那边没有那条工具栏，
     * 三颗留在原处 ✓。
     */
    showCanvasTools: Boolean = true,
) {
    Surface(
        // 底色画在**外层固定组的 Column** 上（和顶栏同一档，见那里的说明），
        // 这里必须透明 —— 再叠一层半透明的 surfaceContainer 会比顶栏深一截。
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(
                horizontal = if (LocalNaiSkin.current == NaiSkin.Reference) 10.dp else 8.dp,
                vertical = if (LocalNaiSkin.current == NaiSkin.Reference) 10.dp else 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(
                if (LocalNaiSkin.current == NaiSkin.Reference) 10.dp else 6.dp,
            ),
        ) {
            // 遮罩状态行：让人一眼看出"现在是遮罩模式"以及"图上已经有遮罩"
            if (state.canvasMode != 2 && (state.maskMode || state.maskVisible)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        MaskSelectIcon,
                        contentDescription = null,
                        tint = if (state.maskMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            // 聚焦框：说清"这次只重画框里"，以及框里没涂时的口径；
                            // 后面那截（上下文/框在请求里多大）是为了解释**请求尺寸为什么比框大很多**
                            // —— 用户 2026-09-16 就为这个问过"请求像素是不是错了"。
                            state.maskMode && state.maskFocusRect != null -> {
                                val (focusW, focusH) = state.maskFocusSize()
                                // 用户 2026-09-26：「长宽**自动计算**……1024×1024 以下自动计算最高像素」
                                // → 框本身的目标尺寸由 `InpaintSize.forRect` 算，**只读**显示
                                //（「聚焦：1792×576（3:1 ✓）」）；没有任何手改入口 ✗。
                                val (autoW, autoH) = state.maskFocusTargetSize()
                                val autoText = if (autoW > 0 && autoH > 0) {
                                    t("mask.focusAuto")
                                        .replace("{w}", autoW.toString())
                                        .replace("{h}", autoH.toString())
                                        .replace("{ratio}", aspectRatioLabel(focusW, focusH))
                                } else {
                                    ""
                                }
                                val plan = state.maskFocusPlan()
                                val detail = if (plan == null) {
                                    ""
                                } else {
                                    val clamped = plan.requestedScale - plan.scale > 0.01f
                                    val scaleNote = when {
                                        // 尺寸是自动算的（新口径）：倍率由 `InpaintSize.forRect` 决定，
                                        // 用户调的那个旋钮已经不参与了（用户 2026-09-26："自动计算"）
                                        plan.autoFilled -> "自动 %.2f×".format(plan.scale)
                                        // 设了倍率但被上限夹小
                                        clamped -> "%.2f×（你调的是 %.1f×，被上限夹小）"
                                            .format(plan.scale, plan.requestedScale)
                                        else -> "%.2f×".format(plan.scale)
                                    }
                                    t("mask.focusDetail")
                                        .replace("{ctx}", state.settings.inpaintFocusContext.toString())
                                        .replace("{fw}", plan.frameInRequest.w.toString())
                                        .replace("{fh}", plan.frameInRequest.h.toString())
                                        .replace(
                                            "{mp}",
                                            (plan.frameRequestPixels / 10_000).toString(),
                                        )
                                        .replace("{scale}", scaleNote)
                                }
                                val head = if (state.maskPaintedPercent > 0) {
                                    t("mask.focusWithMask")
                                        .replace("{w}", focusW.toString())
                                        .replace("{h}", focusH.toString())
                                        .replace("{percent}", state.maskPaintedPercent.toString())
                                } else {
                                    t("mask.focusEmptyBox")
                                        .replace("{w}", focusW.toString())
                                        .replace("{h}", focusH.toString())
                                }
                                // 官方免费档：整块 ≤1024×1024 **且** 单张 **且** 步数 ≤28
                                // （三者都对，Opus 的大图聚焦重绘才是 0 点数）
                                val freeTier = plan?.inFreeTier == true &&
                                    state.params.steps <= 28 &&
                                    state.batchCount == 1
                                (if (autoText.isEmpty()) "" else "$autoText · ") + head + detail + if (freeTier) {
                                    t("mask.focusFreeTier")
                                } else {
                                    t("mask.focusPaidTier")
                                }
                            }

                            state.maskMode && state.maskPaintedPercent > 0 ->
                                t("mask.modeOnShortPainted").replace("{percent}", state.maskPaintedPercent.toString())
                            state.maskMode -> t("mask.modeOnShort")
                            else -> t("mask.existingShort").replace("{percent}", state.maskPaintedPercent.toString())
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.maskMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (state.maskVisible) {
                        TextButton(onClick = { state.clearMask() }) { Text(t("mask.clearMask")) }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // ⚠️ 用户 2026-09-26：这里原本是**导入图片**那颗 —— 已经**删掉**了 ✗：
                // 图片 / 画笔 / 遮罩这三颗全部搬到画布右边缘那条竖向工具栏上 ✓，
                // 底栏只留中间那颗「生成图片」。
                //
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MainRunButton(
                        state = state,
                        t = t,
                        modifier = Modifier.fillMaxWidth().then(NoButtonFocus),
                    )
                }

                // ---- 右侧两颗：**已删除**（用户 2026-09-26）----
                // 铅笔（画布编辑器）/ 遮罩开关以前并排在这里；现在它们在**画布右边缘那条竖向工具栏**上 ✓，
                // 底栏不再重复一份 ✗。功能一个没少，只是入口唯一化（免得两处状态各说各话）。
                // 老的说明留一行备查：铅笔 = 官方 Canvas 那一套（画笔/橡皮/油漆桶/选择/套索/吸管/
                // 模糊/图章 + 调整画布 + 撤销重做，点「完成」当底图用）；遮罩 = "重绘哪一块"（图不动），
                // 判据用 `workImagePath`，和 `toggleMaskMode()` 内部依据的对象一致。
            }

            // 底部那行字幕 = **抽屉入口**（用户 2026-09-16：点它弹出「对话 / 日志」）。
            // 空闲时也留着，否则没话说的时候入口就没了。
            StatusBarLine(
                state = state,
                t = t,
                onOpen = { state.openStatusDrawer() },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 提示词 / 参数内容（两处复用：底部抽屉的 Tab 与宽屏右栏）
// ---------------------------------------------------------------------------

@Composable
private fun PromptFields(
    state: AppState,
    t: (String) -> String,
    onOpenPresets: () -> Unit,
    activeField: String = PromptField.POSITIVE,
    onFieldFocus: (String) -> Unit = {},
) {
    val params = state.params
    val activePresets = state.activeStylePresetPrompts().size

    val lockStyle = state.settings.promptLockStyle
    val lockPositive = state.settings.promptLockPositive
    val lockNegative = state.settings.promptLockNegative

    /**
     * **风格 + 正面共用那一条工具栏**现在作用在谁身上（用户 2026-09-19：
     * 「风格提示词使用正面提示词的工具，和之前一样，监测光标在哪个栏」）。
     *
     * 这两段画在**同一个描边**里，各配一条会挤；而风格词通常只写一句，单独一条也浪费。
     * 所以只有一条，跟着**光标在哪一段**走：点进风格词 → 它作用于风格词；
     * 点进正面词 → 作用于正面词。表头那点主色高亮（[PromptSectionHeader] 的 `selected`）
     * 就是"现在这条工具栏认的是哪一段"，和以前那条共享栏一个口径。
     */
    // 初值跟着 `activeField`（它跨栏保存）：切到"参数"栏再切回来，这条工具栏认的还是
    // 上次光标所在的那一段，而不是被打回正面词。
    var sharedField by remember {
        mutableStateOf(if (activeField == PromptField.STYLE) PromptField.STYLE else PromptField.POSITIVE)
    }

    /** 谁被点进去，谁就是那几条工具栏的作用对象（用户 2026-09-16 要求）。 */
    fun focusHandler(field: String, setLocal: (Boolean) -> Unit): (Boolean) -> Unit = { focused ->
        setLocal(focused)
        if (focused) {
            onFieldFocus(field)
            if (field == PromptField.STYLE || field == PromptField.POSITIVE) sharedField = field
        }
        // 底栏垃圾桶靠这个知道"现在清哪个框"（用户 2026-09-16 报的 bug：
        // 原来只有提示词有身份，垃圾桶在角色 / 漫画的框上按下去没反应）
        //
        // ⚠️ `focusKey` 必须显式给：风格 / 正面 / 负面**三条都报 `TextFieldTarget.Prompt`**，
        // 用默认 key 就三条同名 —— 焦点从一条搬到另一条时，旧那条的失焦回调会把统一标记
        // 误判成"没人聚焦"，那一刻按空格就在提示词里打不出空格（见 `textInputFocused`）。
        state.onTextFieldFocus(TextFieldTarget.Prompt(field), focused, focusKey = "prompt:$field")
    }

    // ---- 风格提示词 + 正面提示词：**合成一个框，中间一条横线**（用户 2026-09-16 要求）----
    // 两个字段各存各的（`stylePrompt` / `positivePrompt`），只是画在同一个描边里 ——
    // 合成之后样式和内容一眼能对上，也不用再纠结"风格词框该给几行"。
    // 「选择预设」跟着风格词走，所以放进**风格那一段的表头**（就是它的右上角）。
    var styleFocused by remember { mutableStateOf(false) }
    var positiveFocused by remember { mutableStateOf(false) }
    val styleSelected = activeField == PromptField.STYLE
    val positiveSelected = activeField == PromptField.POSITIVE
    PromptBox(focused = styleFocused || positiveFocused) {
        PromptSectionHeader(
            label = t("generate.stylePrompt"),
            locked = lockStyle,
            onToggleLock = { state.setPromptLock(PromptField.STYLE, !lockStyle) },
            t = t,
            // 选中的那一段给标题上主色 —— 底部按钮栏作用在谁身上，一眼看得出来
            selected = styleSelected,
            trailing = {
                if (activePresets > 0) {
                    Text(
                        "${t("preset.activePrefix")}$activePresets${t("preset.activeSuffix")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                PromptPresetButton(label = t("preset.select"), onClick = onOpenPresets)
            },
        )
        // ⚠️ 高度**不设上限**（用户要求取消了原来的 maxLines = 3）：
        // 风格词偶尔会写很长，截断成三行只看得到一小截；写多少行就长多高。
        // ⚠️ 用户 2026-09-24：「风格提示词**多一行**，有点**窄**」——
        //    原来没有最小高度 ⇒ 空着时只有一行高（`bodyMedium` 约 20dp），写一两个词就顶到边。
        //    给一个**两行**的下限：`defaultMinSize(minHeight = 52.dp)` ≈ 两行正文 + 上下余量。
        //    ⚠️ 只设**下限**不设上限：写长了一样"写多少行就长多高"（原口径没变）。
        Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp)) {
            PromptTextField(
                value = params.stylePrompt,
                onValueChange = { value -> state.setParam { it.copy(stylePrompt = value) } },
                readOnly = lockStyle,
                placeholder = t("generate.stylePromptHint"),
                onFocusChange = focusHandler(PromptField.STYLE) { styleFocused = it },
                textStyle = MaterialTheme.typography.bodyMedium,
                highlight = true,
                state = state,
            )
        }
        HorizontalDivider(
            Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        PromptSectionHeader(
            label = t("generate.positivePrompt"),
            locked = lockPositive,
            onToggleLock = { state.setPromptLock(PromptField.POSITIVE, !lockPositive) },
            t = t,
            selected = positiveSelected,
        )
        // 原来这里还挂着那排小图标（角色图鉴 / 垃圾桶 / 撤销 / 时钟 / 翻译 / 优化），
        // 已经改成**每个文本框自己下面那一条**（[PromptActionBar]，用户 2026-09-19）。
        // 生成期间**允许继续编辑**（用户要求 2026-09-15）：在飞的请求用的是发起时的参数快照，
        // 不受影响；生成中再点主按钮 = 排队，排队按点击那一刻的 params 快照，所以
        // 「生成中改词 → 再点生成」用的就是改后的提示词。
        Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 96.dp)) {
            PromptTextField(
                value = params.positivePrompt,
                onValueChange = { text -> state.setParam { it.copy(positivePrompt = text) } },
                readOnly = lockPositive,
                placeholder = "1girl, solo, blue hair, white dress, garden, sunlight, smile",
                onFocusChange = focusHandler(PromptField.POSITIVE) { positiveFocused = it },
                highlight = true,
                state = state,
            )
        }
        // **风格 + 正面共用这一条**：它跟着光标在那两段之间走（见 `sharedField`）。
        PromptActionBar(state = state, t = t, targetField = sharedField)
    }

    if (state.canvasMode == 2) {
        val prompt = params.positivePrompt
        Button(
            onClick = {
                if (state.extras.comicBerserk) state.generateBerserkComic(prompt)
                else state.generateComicStoryboard(prompt)
            },
            enabled = prompt.isNotBlank() && !state.storyboardBusy && !state.berserkBusy,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(t("comic.storyboardRun"))
        }
    }

    // ---- 负面提示词：自己一个框 ----
    var negativeFocused by remember { mutableStateOf(false) }
    PromptBox(focused = negativeFocused, modifier = Modifier.padding(top = 8.dp)) {
        PromptSectionHeader(
            label = t("generate.negativePrompt"),
            locked = lockNegative,
            onToggleLock = { state.setPromptLock(PromptField.NEGATIVE, !lockNegative) },
            t = t,
            selected = activeField == PromptField.NEGATIVE,
        )
        Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)) {
            PromptTextField(
                value = params.negativePrompt,
                onValueChange = { value -> state.setParam { it.copy(negativePrompt = value) } },
                readOnly = lockNegative,
                placeholder = "",
                onFocusChange = focusHandler(PromptField.NEGATIVE) { negativeFocused = it },
                textStyle = MaterialTheme.typography.bodyMedium,
                state = state,
            )
        }
        // 负面提示词自己那条工具栏
        PromptActionBar(state = state, t = t, targetField = PromptField.NEGATIVE)
    }
    if (state.canvasMode != 2) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PromptOptionCard(
                label = t("anima.title"),
                icon = PromptOptionIcon.People,
                selected = false,
                // 从新版角色图鉴选中名字时直接创建角色分区。
                onClick = { state.openAnimaSheetForCharacters() },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    state.extras.activeItems.size.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
            if (params.isV5) {
                PromptOptionCard(
                    label = t("generate.transparent"),
                    icon = PromptOptionIcon.Drop,
                    selected = params.transparentBackground,
                    onClick = { state.setParam { it.copy(transparentBackground = !it.transparentBackground) } },
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        Modifier.size(width = 36.dp, height = 20.dp)
                            .background(if (params.transparentBackground) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .border(1.dp, if (params.transparentBackground) MaterialTheme.colorScheme.primary else NaiSkinTokens.borderStrong(), CircleShape),
                    ) {
                        Box(
                            Modifier.offset(x = if (params.transparentBackground) 17.dp else 3.dp, y = 3.dp)
                                .size(14.dp).background(if (params.transparentBackground) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

private enum class PromptOptionIcon { People, Drop }

@Composable
private fun PromptOptionCard(
    label: String,
    icon: PromptOptionIcon,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) primary.copy(alpha = 0.16f) else NaiSkinTokens.panel(),
        border = BorderStroke(1.dp, if (selected) primary else NaiSkinTokens.borderStrong()),
        modifier = modifier.height(52.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                Modifier.size(30.dp).background(primary.copy(alpha = 0.18f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(20.dp)) {
                    if (icon == PromptOptionIcon.People) {
                        drawCircle(primary, radius = size.width * .13f, center = Offset(size.width * .5f, size.height * .31f), style = Stroke(width = 2.dp.toPx()))
                        drawArc(primary, 200f, 140f, false, topLeft = Offset(size.width * .22f, size.height * .49f), size = Size(size.width * .56f, size.height * .42f), style = Stroke(width = 2.dp.toPx()))
                    } else {
                        val drop = Path().apply {
                            moveTo(size.width * .5f, size.height * .07f)
                            cubicTo(size.width * .35f, size.height * .33f, size.width * .15f, size.height * .55f, size.width * .25f, size.height * .76f)
                            cubicTo(size.width * .34f, size.height * .98f, size.width * .66f, size.height * .98f, size.width * .75f, size.height * .76f)
                            cubicTo(size.width * .85f, size.height * .55f, size.width * .65f, size.height * .33f, size.width * .5f, size.height * .07f)
                            close()
                        }
                        drawPath(drop, primary, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            trailing()
        }
    }
}

/**
 * **剧情放大编辑页**：一个铺满的大文本框。
 *
 * ## 为什么要有它
 *
 * 漫画模式那个剧情框只有三行高（它得和右边的版式画布并排放，占太多会把画布挤没），
 * 而狂暴模式是要往里面贴**整部**剧情的 —— 几百上千字塞在三行里，既看不全也没法校对。
 * 点框右上角那个「四角」图标就开它（用户 2026-09-16 要求）。
 *
 * ## 两个平台各长各的（尺寸与观感都由调用方给）
 *
 *  · **窄屏（手机）**：把整个抽屉换成它 —— `fillMaxWidth() + height(0.85 屏)`，与
 *    `DrawerPeekRow` 是兄弟节点，把手行仍在上面，还能下拉收起；底色沿用抽屉那套半透明；
 *  · **宽屏（电脑）**：用户 2026-09-19 要求「点放大时在**提示词栏的右边**弹出一个窗口」——
 *    位置/尺寸同样由调用方（`GenerateScreen` 宽屏分支）用 `modifier` 决定：
 *    贴在提示词栏右侧、上面留到顶、下面停在那条底栏之上，圆角 + 实体底色。
 *
 * ## 几个约定
 *
 *  · 编辑直接写 `state.comicPlot`，走的是那个带防抖的 setter，
 *    所以这里改完关掉、甚至直接杀掉 App 都不会丢；
 *  · 右上角用 `common.close` 的叉，不另造一个"缩小"图标：进出的入口不必长成一对。
 */
@Composable
private fun PlotEditorPanel(
    state: AppState,
    t: (String) -> String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface.copy(alpha = NaiGlass.PANEL_ALPHA),
    shape: Shape = RectangleShape,
) {
    Surface(
        // 窄屏沿用底部抽屉那套毛玻璃参数（见 NaiGlass）；宽屏那扇窗口由调用方传实体底色 + 圆角
        color = color,
        shape = shape,
        tonalElevation = 0.dp,
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t("comic.storyboardTitle"),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = t("common.close"),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                t("comic.plotEditorHint"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 文本框吃掉剩下的全部高度 —— 这就是"放大"本身
            OutlinedTextField(
                value = state.comicPlot,
                // 走 comicPlot 的 setter：自带 600ms 防抖落盘，别在这里自己写盘
                onValueChange = { state.comicPlot = it },
                enabled = !state.storyboardBusy,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp)
                    // 这是同一个 comicPlot，所以和抽屉里那个三行的框**共用一个目标**
                    // （`focusKey` 给一个和角色面板那个剧情框不同的 key：两个框可能同时在屏幕上，
                    //   统一标记里重名会互相顶掉 —— 见 `AppState.textInputFocused`）
                    .onFocusChanged {
                        state.onTextFieldFocus(
                            TextFieldTarget.Plot,
                            it.isFocused,
                            focusKey = "plot:editor",
                        )
                    },
            )
        }
    }
}

/**
 * **一个文本框自己的按钮条**：垃圾桶 · 撤销 · 重做 · 历史 · 翻译 · 优化。
 *
 * ## 它现在是"每个文本框一条"（用户 2026-09-19）
 *
 * 原话：「这个工作区在底栏去除，改为提示词栏的提示词、角色、分镜每个文本框一个」。
 * 之前是**一条共享的**，挂在底栏最上面，靠"当前聚焦的框"猜自己该作用于谁 ——
 * 面板一被拖走它也跟着跑，跟文本就分家了。现在每个框自带一条，挂在**自己那个框的正下方**：
 *  · 提示词栏：风格 / 正面 / 负面各一条（见 [PromptFields]）；
 *  · 角色与分镜栏：每个角色（漫画模式下就是每一格分镜）的正面词 / 负面词各一条
 *    （见 `CharacterEditorPanel` 的 `CharacterCard`）。
 *
 * 因为挨着自己的框，原来垃圾桶旁边那行「作用于 X」就**删掉了**（同一屏里已经一目了然，
 * 而且宽只有 300dp 上下，多一行字按钮就挤出去了）。
 *
 * ## 每个按钮都只认自己那一条
 *
 * [targetField] 就是这条栏所属的框（提示词三条用 [PromptField] 的 id，
 * 角色 / 分镜用 [PromptField.charKey]）。撤销栈按框分开存（见 `AppState.promptUndo`），
 * 翻译/优化也写回同一个框 —— 所以"在角色框里按撤销，撤的是提示词"这种串台不会再出现。
 * ⚠️ 别把 `orDefault` 用在它上面：那会把剧情（PLOT）和角色框都折成正面提示词。
 */
@Composable
internal fun PromptActionBar(
    state: AppState,
    t: (String) -> String,
    targetField: String,
    modifier: Modifier = Modifier,
) {
    val field = PromptField.stackKey(targetField)
    Row(
        // 这一条现在长在**每个文本框自己下面**（见上面 KDoc），所以不再自己上色：
        // 提示词栏 / 角色卡片各自的底色就是它的底色，只留一点上下留白。
        modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 垃圾桶：清**这条栏所属的那个框**（不再看焦点 —— 点垃圾桶不会给框焦点，猜必错）。
        // 锁着的提示词清不掉，由 `AppState.setPromptField` 兜底。
        PromptActionIconButton(
            onClick = { state.clearPromptField(field) },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = t("common.delete"),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        // ---- 右：撤销 / 重做 / 历史 / 翻译 / 优化（全部按这一条所属的框） ----
        // 没得撤销/重做时置灰，免得点了没反应还以为是 bug
        PromptActionIconButton(
            onClick = { state.undoPrompt(field) },
            enabled = state.canUndoPrompt(field),
            modifier = Modifier.size(34.dp),
        ) {
            Icon(UndoIcon, contentDescription = t("generate.undoPrompt"), modifier = Modifier.size(19.dp))
        }
        PromptActionIconButton(
            onClick = { state.redoPrompt(field) },
            enabled = state.canRedoPrompt(field),
            modifier = Modifier.size(34.dp),
        ) {
            Icon(RedoIcon, contentDescription = t("generate.redoPrompt"), modifier = Modifier.size(19.dp))
        }
        PromptActionIconButton(
            onClick = { state.openPromptHistory(field) },
            modifier = Modifier.size(34.dp),
        ) {
            Icon(ClockIcon, contentDescription = t("llm.history"), modifier = Modifier.size(19.dp))
        }
        if (state.llmBusy) {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp)
            }
        } else {
            PromptActionIconButton(
                onClick = { state.translatePrompt(field) },
                modifier = Modifier.size(34.dp),
            ) {
                TranslateGlyph(label = t("llm.translate"))
            }
            PromptActionIconButton(
                onClick = { state.optimizePrompt(field) },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(SparkleIcon, contentDescription = t("llm.optimize"), modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun PromptActionIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    if (!referenceSkin) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
        return
    }

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            pressed -> NaiSkinTokens.accentSoft()
            hovered -> NaiSkinTokens.subtleHover()
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "promptActionBackground",
    )
    val foreground by animateColorAsState(
        targetValue = when {
            !enabled -> NaiSkinTokens.faint().copy(alpha = 0.5f)
            hovered -> NaiSkinTokens.text()
            else -> NaiSkinTokens.muted()
        },
        animationSpec = tween(120),
        label = "promptActionForeground",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "promptActionPressScale",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(7.dp),
        color = background,
        contentColor = foreground,
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/**
 * 「角色图鉴」按钮：**小圆角的方形** + **主题色**（用户 2026-09-16 要求：
 * "圆角小一点、再方一些，按钮变为主题色"；原来是 12dp 圆角的灰色块）。
 *
 * 提示词框左下角和「角色」栏共用这一个，保证两处长得一模一样。
 */
@Composable
private fun PromptPresetButton(label: String, onClick: () -> Unit) {
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    if (!referenceSkin) {
        Surface(
            shape = NaiShape.Pill,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onClick),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
        }
        return
    }

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = when {
            pressed -> NaiSkinTokens.accentSoft()
            hovered -> NaiSkinTokens.subtleHover()
            else -> NaiSkinTokens.accentSoft()
        },
        animationSpec = tween(120),
        label = "promptPresetButtonBackground",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120),
        label = "promptPresetButtonPressScale",
    )
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(7.dp),
        color = background,
        contentColor = NaiSkinTokens.accent(),
        modifier = Modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            },
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** 提示词框 id → 界面上那个名字（i18n key）。 */
private fun promptFieldNameKey(field: String): String = when (PromptField.orDefault(field)) {
    PromptField.STYLE -> "generate.stylePrompt"
    PromptField.NEGATIVE -> "generate.negativePrompt"
    else -> "generate.positivePrompt"
}

/**
 * 提示词盒子的外壳：M3 那套描边 + 圆角，里面由调用方自由排版。
 *
 * ## 为什么不用 `OutlinedTextField`
 *
 * 它只有**一个**正文槽位，而这一版要求把风格提示词和正面提示词画在同一个边框里、
 * 中间用横线隔开，还要在每一段的右上角放锁图标 —— 浮动标签撑不住这个结构。
 * 所以这里手画边框，但**描边宽度 / 聚焦配色仍对齐 `OutlinedTextFieldDefaults`**，
 * 圆角与静息底色改走设计 token（见 [NaiSkinTokens]）——
 * 于是和别的输入框看起来仍是一套。
 */
@Composable
private fun PromptBox(
    focused: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    val focusColor = NaiSkinTokens.accent()
    val focusRingColor = NaiSkinTokens.accentSoft()
    val focusProgress by animateFloatAsState(
        targetValue = if (referenceSkin && focused) 1f else 0f,
        animationSpec = if (referenceSkin) tween(120) else snap(),
        label = "promptBoxFocusRing",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) focusColor else NaiSkinTokens.border(),
        animationSpec = if (referenceSkin) tween(120) else snap(),
        label = "promptBoxBorder",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                if (focusProgress > 0f) {
                    val strokeWidth = 3.dp.toPx() * focusProgress
                    val inset = strokeWidth / 2f
                    drawRoundRect(
                        color = focusRingColor.copy(alpha = focusRingColor.alpha * focusProgress),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(width = strokeWidth),
                    )
                }
            },
    ) {
      Surface(
        // 圆角 / 底色 / 描边走设计 token（`NaiSkinTokens`），两套风格共用这一处：
        // 玻璃风格下底色是主题的 `surfaceContainerHigh`、描边是 `outlineVariant`，
        // 实色风格下是参考稿的 `--bg-subtle` + 极细描边。
        shape = NaiSkinTokens.boxShape(),
        color = NaiSkinTokens.field(),
        border = BorderStroke(
            width = if (focused && !referenceSkin) 2.dp else 1.dp,
            color = borderColor,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            content = content,
        )
      }
    }
}

/**
 * 提示词盒子里一小段的表头：左边是名字，**右边是锁图标**（用户要求放右上角）。
 *
 * 锁上 = 实心「关锁」+ 主色高亮；没锁 = 「开锁」+ 次要色（用户 2026-09-16 要求做成一开一关两个图标）。
 * 两个图标都是自绘的（core 图标集里只有 `Lock` 没有开锁，见 `AppIcons`）。
 */
@Composable
private fun PromptSectionHeader(
    label: String,
    locked: Boolean,
    onToggleLock: () -> Unit,
    t: (String) -> String,
    selected: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            // 选中的那一段上主色：底下那条常驻按钮栏按的是哪个框，这里一眼能对上
            color = when {
                selected -> NaiSkinTokens.accent()
                LocalNaiSkin.current == NaiSkin.Reference -> NaiSkinTokens.muted()
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
        PromptActionIconButton(
            onClick = onToggleLock,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                imageVector = if (locked) LockClosedIcon else LockOpenIcon,
                contentDescription = t(if (locked) "prompt.unlock" else "prompt.lock"),
                tint = when {
                    locked -> NaiSkinTokens.accent()
                    LocalNaiSkin.current == NaiSkin.Reference -> NaiSkinTokens.faint()
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * 提示词正文：一个**没有自己边框**的多行输入框（边框由 [PromptBox] 画）。
 *
 * [readOnly] 就是那把锁 —— 锁上之后键盘打不进去，但文字仍可选中 / 复制 / 滚动。
 * 页面上的锁图标只是"看得见的提示"，真正的兜底在 `AppState.params` 的写入口：
 * AI 翻译 / 优化、垃圾桶、撤销重做、元数据导入全都绕不过去。
 *
 * [highlight] 打开后按 NovelAI 的加强 / 削弱语法上色（见 `PromptWeight`）。
 * 只给风格词和正面词开：负面词主要在「负面提示词」和 UC 预设里，用不到数值加权，
 * 上色反而干扰。
 */
@Composable
private fun PromptTextField(
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean,
    placeholder: String,
    onFocusChange: (Boolean) -> Unit,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    highlight: Boolean = false,
    /** ⚠️ 原来还有个 `suggestions`（词典候选）参数 —— 已随补全一起去掉（见函数尾部的说明）。 */
    state: AppState,
) {
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    val color = when {
        referenceSkin && readOnly -> NaiSkinTokens.muted()
        referenceSkin -> NaiSkinTokens.text()
        readOnly -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    var focused by remember { mutableStateOf(false) }

    /**
     * **解锁不弹键盘**（用户 2026-09-22）。
     *
     * 锁着的时候点框只是为了**选中 / 复制 / 滚动**（见本函数的说明），焦点其实已经落在框里；
     * 一旦 `readOnly` 翻成 `false`，`BasicTextField` 会认为"现在可以编辑了"⇒ **输入法自己起来** ✗。
     * 这里在解锁那一下立刻把它收回去 —— 焦点与光标位置**都不动**，
     * 想编辑再点一下框即可（那一下才是用户真的要打字）。
     *
     * ⚠️ 用 `keyboard.hide()` 而不是 `focusManager.clearFocus()`：桌面端没有软键盘，
     *    `hide()` 是空操作 ⇒ 电脑上解锁**不会**把光标弄丢 ✓。
     */
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(readOnly) {
        if (!readOnly && focused) keyboard?.hide()
    }

    /**
     * 「光标尾巴」的定位器。
     *
     * ## 为什么需要它
     *
     * 用户的抱怨：**回车 / 换行之后画面不跟着光标走**，光标落到可见区下面去了。
     *
     * 正常应该由输入框自己发 `bringIntoView` 请求、祖先的滚动容器接住。但这个请求在
     * **高度不受限**的 `BasicTextField` 上会被**吃掉却滚不动**：它内部那个可滚动节点
     * （`maxLines = Int.MAX_VALUE` 时高度随内容长）`maxValue` 恒为 0，请求落到它身上
     * 就到此为止，永远不会再往上传给抽屉的滚动容器。
     *
     * 所以这里在输入框**外面**、紧贴它底部放一个 1dp 的小标记：它不在输入框的节点里，
     * 请求直接落到抽屉的滚动容器上，不会被吞。每敲一次（含换行）就把它顶进可见区 ——
     * 它就贴在光标所在那一行的下方，于是"光标跟着走"。
     */
    val tailRequester = remember { BringIntoViewRequester() }

    BasicTextField(
        // ⚠️ 回到**`String` 重载**（用户 2026-09-24：补全太卡，去掉）——
        //    上一版为了给词典候选拿光标位置改用了 `TextFieldValue` 重载；
        //    补全删掉后没必要再维护一份（那是**每敲一个字都新建一个对象**），
        //    回到最省的 `String` 重载。
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        textStyle = textStyle.copy(
            color = color,
            fontSize = if (referenceSkin) 13.sp else textStyle.fontSize,
            lineHeight = if (referenceSkin) 20.sp else textStyle.lineHeight,
            fontFamily = if (referenceSkin) NaiSkinTokens.ReferenceFontFamily else textStyle.fontFamily,
        ),
        cursorBrush = SolidColor(if (referenceSkin) NaiSkinTokens.accent() else MaterialTheme.colorScheme.primary),
        // 每个输入框各自 remember 一个转换器（内部带缓存，共用会互相顶掉）
        visualTransformation = if (highlight) {
            rememberPromptHighlight()
        } else {
            VisualTransformation.None
        },
        modifier = Modifier
            .fillMaxWidth()
            // 长按选词、把选词手柄往下拖时，让抽屉跟着手指滚（用户 2026-09-16 报的问题）。
            // 这类框高度不受限，它自己发不出能生效的滚动请求 —— 见 SelectionScroll.kt。
            .followFingerForSelection()
            .onFocusChanged {
                focused = it.isFocused
                onFocusChange(it.isFocused)
            },
        decorationBox = { innerTextField ->
            Column {
                Box {
                    if (value.isEmpty() && placeholder.isNotEmpty() && !readOnly) {
                        Text(
                            placeholder,
                            style = textStyle.copy(
                                fontSize = if (referenceSkin) 13.sp else textStyle.fontSize,
                                lineHeight = if (referenceSkin) 20.sp else textStyle.lineHeight,
                                fontFamily = if (referenceSkin) NaiSkinTokens.ReferenceFontFamily else textStyle.fontFamily,
                            ),
                            color = if (referenceSkin) NaiSkinTokens.faint() else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .bringIntoViewRequester(tailRequester),
                )
            }
        },
    )

    // 内容一变（打字 / 回车换行 / 粘贴）就把尾巴顶进可见区。
    //
    // ⚠️ 就顶**尾巴这一个点**，不额外算余量 —— 这是用户挑定的：
    // 试过 44dp / 16dp（顶得太高）、2dp（还是不行），最后回到"只让开最下沿那一点"。
    // 想要光标再往上就调 `bringIntoView(Rect(...))`，别改别的。
    LaunchedEffect(value) {
        if (!focused) return@LaunchedEffect
        // ⚠️ 等这一帧的布局落定再定位：这次换行会让输入框**长高一行**，
        // 尾巴的新位置要按新布局算；不等的话每次都差一行，越写越偏。
        withFrameNanos { }
        tailRequester.bringIntoView()
    }

    // ---- 词典候选：**已按用户要求删除**（用户 2026-09-24：「写提示词会有提示词补全，太卡了，
    //      把那个去掉」）----
    // ⚠️ 为什么它卡（写下来免得以后又加回去）：`state.tagSuggestions(...)` 是**一趟 39,577 条的
    //    线性扫描**，而它挂在**每次重组**上 ⇒ **每敲一个字符就全表扫一遍**；
    //    再叠上 `TextFieldValue` 那个重载（每敲一个字新建一个对象）就是"打字一顿一顿"的来源。
    //    ⇒ 补全整块删掉，`PromptTextField` 也回到 `String` 重载（见上面那段注释）。
    //    `AppState.tagSuggestions` / `TagDictionary` / `dictionary.tsv` **保留但不再被调用**
    //    （词典库本身没删 —— 以后要换成"按需触发"（比如手动按一下才查）还能用）。
}


/**
 * **翻译图标 = 一个 A + 一个「文」（A 在前）**。
 *
 * 为什么不画矢量路径：这俩字本身就是字形，直接用文字渲染最准、最直观
 * （自己拼 path 既难做得像，字重和间距也难对齐）；图标位只有 20dp，
 * 所以字号压到 12sp、去掉字体额外行距，让"文"撑满图标框。
 */
@Composable
private fun TranslateGlyph(label: String) {
    val color = LocalContentColor.current
    Text(
        text = "A文",
        color = color,
        fontSize = 12.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        // 去掉行高留白（手机是 includeFontPadding）：不然中文的字体上行距会把这句话
        // 在图标框里整体压低。电脑没这个概念，平台层原样返回。
        style = LocalPlatform.current.compactTextStyle(LocalTextStyle.current),
        modifier = Modifier.clearAndSetSemantics { contentDescription = label },
    )
}

/** 「历史提示词」抽屉：图库历史里出现过的正面提示词，点一条填进正面提示词框。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptHistorySheet(
    state: AppState,
    t: (String) -> String,
    field: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val prompts = remember(state.history) { state.recentPrompts() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(t("llm.history"), style = MaterialTheme.typography.titleMedium)
            Text(
                t("llm.historyHint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (prompts.isEmpty()) {
                Text(
                    t("llm.historyEmpty"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 8.dp)) {
                    items(prompts, key = { it }) { prompt ->
                        Text(
                            prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 用这一条 → 落进**当前选中的那个框**（历史抽屉在常驻栏的时钟里打开）
                                    state.setPromptField(field, prompt)
                                    onDismiss()
                                }
                                .padding(vertical = 10.dp),
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ParamsFields(state: AppState, t: (String) -> String) {
    val params = state.params

    SubHeader(t("generate.model"))
    PickerField(
        label = t("generate.model"),
        options = NaiCatalog.models,
        selected = params.model,
        onSelect = { value -> state.setParam { it.copy(model = value) } },
    )

    // 尺寸：和「模型」一样做成下拉，而且**只显示 W×H 数值**（不再带 `Portrait / Landscape` 那种英文描述）
    PickerField(
        label = t("generate.size"),
        options = sizeOptions,
        selected = "${params.width}×${params.height}",
        onSelect = { value ->
            val parts = value.split("×")
            val w = parts.getOrNull(0)?.trim()?.toIntOrNull()
            val h = parts.getOrNull(1)?.trim()?.toIntOrNull()
            if (w != null && h != null) state.setParam { it.copy(width = w, height = h) }
        },
    )

    SubHeader("采样")
    PickerField(
        label = t("generate.sampler"),
        options = NaiCatalog.samplers,
        selected = params.sampler,
        onSelect = { value -> state.setParam { it.copy(sampler = value) } },
    )
    LabeledSlider(
        label = t("generate.steps"),
        valueText = params.steps.toString(),
        value = params.steps.toFloat(),
        range = 1f..50f,
        steps = 48,
        onValueChange = { value -> state.setParam { it.copy(steps = value.toInt()) } },
    )
    LabeledSlider(
        label = t("generate.cfgScale"),
        valueText = "%.1f".format(params.cfgScale),
        value = params.cfgScale.toFloat(),
        range = 1f..10f,
        steps = 45,
        onValueChange = { value -> state.setParam { it.copy(cfgScale = (value * 10).toInt() / 10.0) } },
    )
    LabeledSlider(
        label = t("generate.cfgRescale"),
        valueText = "%.2f".format(params.cfgRescale),
        value = params.cfgRescale.toFloat(),
        range = 0f..1f,
        steps = 100,
        onValueChange = { value -> state.setParam { it.copy(cfgRescale = (value * 100).toInt() / 100.0) } },
    )
    if (params.supportsNoiseScheduleControl) {
        PickerField(
            label = t("generate.noiseSchedule"),
            options = NaiCatalog.noiseSchedules,
            selected = params.noiseSchedule,
            onSelect = { value -> state.setParam { it.copy(noiseSchedule = value) } },
        )
    }
    // 负面预设（UC）与质量标签已移到设置页

    SubHeader("种子与批量")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = params.seedMode == "random",
            onClick = { state.setParam { it.copy(seedMode = "random") } },
            label = { Text(t("generate.seedModeRandom")) },
        )
        FilterChip(
            selected = params.seedMode == "fixed",
            onClick = {
                state.setParam {
                    it.copy(
                        seedMode = "fixed",
                        seed = if (it.seed > 0) it.seed
                        else (java.security.SecureRandom().nextInt(Int.MAX_VALUE - 1) + 1).toLong(),
                    )
                }
            },
            label = { Text(t("generate.seedModeFixed")) },
        )
    }
    if (params.seedMode == "fixed") {
        NumberField(
            label = t("generate.seed"),
            value = params.seed.toInt(),
            maxLength = 10,
            onCommit = { value -> state.setParam { it.copy(seed = value.toLong(), seedMode = "fixed") } },
        )
    }
    NumberField(
        label = t("generate.batch"),
        value = state.batchCount,
        maxLength = 3,
        onCommit = { value -> state.updateBatchCount(value) },
    )
    NumberField(
        label = t("generate.batchInterval"),
        value = state.batchIntervalSeconds,
        hint = t("generate.batchIntervalHint"),
        maxLength = 4,
        onCommit = { value -> state.updateBatchIntervalSeconds(value) },
    )

    SubHeader("其它开关")
    if (params.supportsVariety) {
        SwitchRow(
            label = t("generate.variety"),
            help = t("generate.varietyHint"),
            checked = params.variety,
            onCheckedChange = { value -> state.setParam { it.copy(variety = value) } },
        )
    }
    if (!params.isV4Plus) {
        SwitchRow(
            label = t("generate.smea"),
            help = t("generate.smeaHint"),
            checked = params.smea,
            onCheckedChange = { value ->
                state.setParam { it.copy(smea = value, smeaDyn = if (value) it.smeaDyn else false) }
            },
        )
        SwitchRow(
            label = t("generate.smeaDyn"),
            checked = params.smeaDyn,
            enabled = params.smea,
            onCheckedChange = { value -> state.setParam { it.copy(smeaDyn = value) } },
        )
    }

    SubHeader(t("generate.output"))
    // 文件名前缀：**必须登记焦点**（空格闸门 `AppState.textInputFocused` 读它）——
    // 上一轮点名的两个"没登记"的框之一，漏了的话在前缀里打不出空格。
    OutlinedTextField(
        value = params.fileNamePrefix,
        onValueChange = { value -> state.setParam { it.copy(fileNamePrefix = value) } },
        label = { Text(t("generate.fileNamePrefix")) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .trackTextInputFocus(state, "params.fileNamePrefix"),
    )
}

/**
 * 「角色图鉴」抽屉（正面提示词左下角那个按钮点开）。
 *
 * 结构照用户要求：**顶部一行搜索框**（胶囊型，和顶栏那枚同款），**下面是完整的角色图鉴**
 * （排序、筛选、网格、翻页都在里面，直接复用 [AnimaDexTool]，不另写一份）。
 * 抽屉高 92%，够放下一屏内容；点卡片照旧弹全屏详情。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AnimaDexSheet(
    state: AppState,
    t: (String) -> String,
    forCharacters: Boolean = false,
    promptField: String = PromptField.POSITIVE,
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    ModalBottomSheet(
        onDismissRequest = { state.closeAnimaSheet() },
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // 底部悬空的修法：
        //  1. **不再给 sheet 指定 92% 高**（那是"半屏浮起"的观感，底部会留一条空带）；
        //  2. `contentWindowInsets` 清零 —— 否则系统还会再垫一层导航栏内边距，抽屉底部就空出来了。
        //     内边距改由内容自己给（下面的 Column 里统一加 padding）。
        contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0) },
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            // ---- 顶部：一句话说明"点角色名会发生什么"
            // 这个抽屉现在有两种入口（建角色分区 / 填进选中的提示词框），
            // 不说清楚的话点下去结果不一样会让人以为出 bug 了。
            Text(
                if (forCharacters) {
                    t("anima.charHint")
                } else {
                    "${t("prompt.targetPrefix")}${t(promptFieldNameKey(promptField))} · ${t("prompt.animaInsertHint")}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            // ---- 搜索框（胶囊型，标语跟随 角色/作品 视图）
            OutlinedTextField(
                value = state.animaQuery,
                onValueChange = { state.animaSetQuery(it) },
                placeholder = {
                    Text(
                        if (state.animaMode == "copyrights") {
                            t("anima.searchPlaceholderSeries")
                        } else {
                            t("anima.searchPlaceholder")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                singleLine = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { state.animaSearch(reset = true) },
                ),
                trailingIcon = {
                    TextButton(onClick = { state.animaSearch(reset = true) }) {
                        Text(t("anima.search"))
                    }
                },
                // 图鉴搜索框：登记焦点（空格闸门读 `AppState.textInputFocused`）——
                // 角色名 / 作品名里带空格很常见，漏了这里就打不出空格。
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .trackTextInputFocus(state, "anima.search"),
            )
            // ---- 下面：完整的角色图鉴
            AnimaDexTool(
                state = state,
                t = t,
                clipboard = clipboard,
                modifier = Modifier.weight(1f),
                onCopied = { },
                // 点角色名字干什么，取决于从哪进来的：
                //  · 「角色」栏 → **新建一个角色分区**；
                //  · 提示词旁（默认）→ **填进当前选中的那个提示词框**（用户 2026-09-16 要求：
                //    常驻栏那五个功能都跟着选中的框走，角色图鉴的"填入"也算一个）。
                onNamePick = { character ->
                    if (forCharacters) {
                        state.addCharacterFromAnima(character)
                    } else {
                        state.animaAppendPrompt(character.trigger, promptField)
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 临时图库抽屉（电脑宽屏：画布右边缘那个"半露的把手"拉开的媒体资产面板）
// ---------------------------------------------------------------------------

/**
 * 收起态把手的尺寸（用户 2026-09-22 规格：约 22dp 宽 × 56dp 高、圆角）。
 *
 * 为什么不是"真的裁掉一半的球"：把手贴在**内容区右边缘**上，只把**左侧两个角**做成圆角，
 * 看上去就是"从边缘上突出来的一半"；真裁掉一半的话里面那颗箭头也断了，反而看不懂。
 */
// ⚠️ 用户 2026-09-26：画布右边缘那个把手改成**竖向工具栏**（照他给的参考图竖排）——
// 里面放：图片（开合临时图库）/ 铅笔（画布编辑器）/ 虚框（遮罩）。
// 宽度按"一颗 34dp 图标 + 左右各 3dp"算；收起态它贴画布右边缘、展开态贴面板左边缘（同一条 Row 一起平移）。
// ⚠️ 用户 2026-09-26：「右边栏的图标太小了，加大点」——
// 图标 20 → 24dp、按钮 34 → 42dp、整条栏 40 → 50dp（图标周围留出 3dp 呼吸位 ✓）。
private val SESSION_RAIL_WIDTH = 64.dp
private val SESSION_RAIL_ICON = 42.dp
private val SESSION_RAIL_ICON_GLYPH = 24.dp
private val SESSION_RAIL_PAD = 6.dp
private val REFERENCE_RAIL_BUTTON_WIDTH = 54.dp
private val REFERENCE_RAIL_BUTTON_HEIGHT = 58.dp

/**
 * **停靠态**两块面板（中控台 / 底栏）相接处的圆角大小（用户 2026-09-26）。
 *
 * 三轮口径，按时间顺序（别再来回改了 ✗）：
 *  1. 「底栏标题栏和中控**合为一条**」→ 加了 10dp 缝 + 各自 8dp 圆角 ✓；
 *  2. 用户看图后：「**中控和底栏在未分离的情况下还没融合**」✗ —— 缝和圆角恰恰是
 *     把一扇窗口切成两扇的东西 ✓。
 *  3. 所以现在：**缝 = 0** ✓，相接那两条边做成**直角**（中控台只圆上面两角、
 *     底栏只圆下面两角 ✓）→ 两块拼成一扇完整的窗口 ✓（`DOCK_PANEL_CORNER` 给外侧用）。
 *  4. 用户 2026-09-26 第三轮反馈：「颜色也要融合，是**一条**接缝」→ 选 B「去掉分界线、同底色即可」✓。
 *     查过两块内部**没有任何分隔线** ✗ —— 剩下能看见的"线"只可能是
 *     ① 两块高度各自取整后留下的**一丝缝**（露出画布底 ✗）② 或者标题栏颜色那条带子 ✓。
 *     ① 用**负缝**堵掉：中控台往下多铺 1dp、压到底栏底下（底栏 z 序更高，重叠部分看不见 ✓）。
 */
internal val DOCK_PANEL_GAP = (-1).dp
internal val DOCK_PANEL_CORNER = 8.dp

/** 展开面板的宽度（用户 2026-09-22 建议值 340dp）。 */
private val SESSION_PANEL_WIDTH = 340.dp

/** 面板上下各留的边距：面板高 = 内容区高 − 2 × 它（"上下各留一点边距"）。 */
private val SESSION_PANEL_MARGIN = 16.dp

/**
 * **大图网格**的列数 / 左右边距 / 列间距（用户 2026-09-22 第二轮：
 * 「临时图库使用大图模式，不要用列表模式」）。
 *
 * 两列：面板固定 [SESSION_PANEL_WIDTH] 宽，扣掉左右各 [SESSION_GRID_PADDING]、
 * 中间一条 [SESSION_GRID_SPACING] 之后，每格约 155dp 宽 —— 缩略图是这块面板里最大的一块，
 * 这才叫"大图模式"（上一轮那种"一行一条"的列表已经删掉）。
 */
private val SESSION_GRID_COLUMNS = 2
private val SESSION_GRID_PADDING = 10.dp
private val SESSION_GRID_SPACING = 10.dp

/**
 * 一格缩略图的宽高比（宽 ÷ 高）= 2:3 竖版，和图库的网格模式（`GalleryGridCell` 的
 * `fixedHeight` 那一支）一个口径：`ContentScale.Crop` 裁掉溢出，整面墙是齐的。
 */
private const val SESSION_TILE_ASPECT = 2f / 3f

/**
 * 一格缩略图的**显示宽度**：面板宽扣掉左右边距与列间距，再平分给各列。
 * 缩略图解码像素按它算（见 `SessionGalleryPanel` 里的 `thumbPx`）。
 */
private val SESSION_TILE_WIDTH =
    (SESSION_PANEL_WIDTH - SESSION_GRID_PADDING * 2 - SESSION_GRID_SPACING * (SESSION_GRID_COLUMNS - 1)) /
        SESSION_GRID_COLUMNS

/**
 * 临时图库里的一项 = 一张图，或者**同一批**的多张图（右侧角标显示张数）。
 */
private data class SessionGalleryEntry(
    /** 代表这一项的那张（同一批里**最新**的那张）。 */
    val item: HistoryItem,
    /** 这一批的张数（≥1）；> 1 时才画角标。 */
    val count: Int,
    /** 生成耗时（毫秒）；导入的图、没有耗时的条目是 null（元信息行显示 `—`）。 */
    val durationMs: Long?,
)

/**
 * **「这条记录是导入进来的」的判据**（用户 2026-09-22 让先找现成标志位、没有就按路径判）。
 *
 * 现成标志位**没有**：`HistoryItem` 没有 `imported` 之类的字段，`feature` 里也没有 import 这一类
 *（只有 t2i / i2i / inpaint / upscale / director-*）。所以判据是**文件路径里有没有 `imported` 这一段**：
 * 导入的图由 `AppState.copyImageToPrivate(ref, "imported")` 落到 `<filesDir>/imported/…`
 *（见 `AppState.copyImportedImage`），而生成图落在 images 的输出目录里，二者天然分得开。
 *
 * ⚠️ 路径分隔符两端不一样（电脑是 `\`、手机是 `/`），所以先统一成正斜杠再找 `/imported/`。
 */
private fun isSessionImportedPath(path: String): Boolean =
    path.replace('\\', '/').contains("/imported/")

/**
 * 把"**本次会话**"的条目挑出来，并按**批次**合成一项。
 *
 * 三条判据（用户 2026-09-22）：
 *  · **本次会话**：`HistoryItem.createdAt >= AppState.sessionStartIso`。两边是同一个
 *    `yyyy-MM-dd'T'HH:mm:ss.SSS` 格式（见 `Storage.iso8601`），零填充 ⇒ **字典序 = 时间序**，
 *    直接比字符串就行，不必逐条解析时间。
 *  · **已生成 / 已导入**：[isSessionImportedPath]（按路径里有没有 `imported` 那一段）。
 *  · **同一批合成一项**：批次号来自 `AppState.sessionBatchIds`（`HistoryItem.id` → 批次号，
 *    在批量生成那条循环 / 导演工具 / 每次 `persistGeneratedImages` 里记账）。
 *    **没有记账的条目退回"自己一批"**（拿 `HistoryItem.id` 当批次号）——
 *    于是单张永远是一项，绝不会把无关的图凑到一起。
 *
 * @param imported true = 取「已导入」那一栏，false = 取「已生成」那一栏。
 */
private fun sessionGalleryEntries(state: AppState, imported: Boolean): List<SessionGalleryEntry> {
    // history 是"新的在前"，下面保持这个顺序（分组用 LinkedHashMap 保住插入顺序）
    val sessionItems = state.history.filter { item ->
        item.createdAt >= state.sessionStartIso &&
            isSessionImportedPath(item.filePath) == imported
    }
    val grouped = LinkedHashMap<String, MutableList<HistoryItem>>()
    sessionItems.forEach { item ->
        val batch = state.sessionBatchIds[item.id] ?: item.id
        grouped.getOrPut(batch) { mutableListOf() }.add(item)
    }
    return grouped.values.map { members ->
        SessionGalleryEntry(
            // 代表 = 先遇到的那张 = 这一批里最新的一张
            item = members.first(),
            count = members.size,
            // 同一批的耗时是同一个值，取第一个非空即可
            durationMs = members.firstNotNullOfOrNull { state.sessionImageDurationMs[it.id] },
        )
    }
}

/** 临时图库一格里的文件名（去目录、去扩展名；Windows 是反斜杠，两边都认）。 */
private fun sessionFileName(path: String): String =
    path.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')

/**
 * 元信息行的时长文案：`9.60s`（用户 2026-09-22 给的截图口径）。
 *
 * ⚠️ 和 `AppState.formatDuration`（`分:秒`，如 `0:12`）**不是一个口径**：那个是运行条旧角标用的。
 * 没有耗时（导入的图 / 老数据）显示 `—`。
 */
private fun sessionDurationText(ms: Long?): String =
    if (ms == null || ms <= 0L) "—" else String.format(java.util.Locale.US, "%.2fs", ms / 1000.0)

/**
 * **临时图库抽屉**：贴内容区（= 画布）右边缘、纵向居中（用户 2026-09-22）。
 *
 * ```
 * 收起：                                   展开：
 * ┌─────────画布───────────┬──┐           ┌────画布────┬──┬──────────┐
 *                          │◀ │           │            │▶ │ 临时图库  │
 *                          └──┘           │            │  │ 搜索 ⌄    │
 *                                         │            │  │ 已生成|已导入│
 *                                         │            │  │ ▣2 ▣2 ▣2 │
 *                                         └────────────┴──┴──────────┘
 * ```
 *
 * · 收起：一个 [SESSION_HANDLE_WIDTH] × [SESSION_HANDLE_HEIGHT] 的圆角把手，**贴画布右边缘半露**，
 *   里面是**向左**的箭头（[Icons.Filled.KeyboardArrowLeft]）；
 * · 点它展开：把手**贴在面板的左侧边缘**、和面板**一起**从右边滑进来（用户 2026-09-22 第二轮：
 *   「收回按钮出来后放在临时图库左边，按钮和临时图库窗口是一体的」），箭头换成**向右**，点它收起。
 *
 * ## "一体"是怎么成立的
 *
 * 把手和面板是**同一个 [Row] 的两个孩子**（把手在左、面板在右），整条 Row 挂在同一个
 * `graphicsLayer { translationX = … }` 上：收起时整条 Row 往右平移一个 [SESSION_PANEL_WIDTH]，
 * 面板于是整个滑出画布、被外层的 `clipToBounds()` 裁掉，而**把手正好停在画布右边缘**（半露）。
 * 两个态之间两者**永远紧挨着**，不需要"两个各自算的动画再去对齐"。
 *
 * ⚠️ Row 的宽度**写死** `SESSION_PANEL_WIDTH + SESSION_HANDLE_WIDTH`：收起动画跑完之后面板会
 * 离开组合树（见下面的 `visible`），那一刻宽度只能由容器自己占住 —— 否则整条 Row 会缩成只有
 * 把手那么宽、再被平移 340dp 推出画布，把手就"没了"。
 *
 * ## 为什么不会和别的手势打架
 *
 * 宽屏内容格那一层有一个"内容格统一接管面板拖动"的 `pointerInput`（提示词栏 / 底栏 / 分离面板）。
 * 这里**一个字都没改它**：抽屉只在**自己那块矩形**里参与命中 ——
 *  · 对齐 / 尺寸全在父 Box 的坐标系里定（外部传入的 `modifier` + 内部 `graphicsLayer`）；
 *  · 把手 / 面板各自的 `clickable` **排在 `size()` / `width()` / `fillMaxHeight()` 之后** ——
 *    这正是这个仓库刚踩过的教训：手势修饰符排在位置 / 尺寸**前面**时，命中矩形会钉在原点，
 *    把别的地方全挡住。
 *
 * @param modifier 由调用方给（`Modifier.align(Alignment.CenterEnd).fillMaxHeight()`）。
 */
@Composable
private fun SessionGalleryDrawer(
    state: AppState,
    t: (String) -> String,
    /** 工具栏上「图片」旁边那颗**导入**（在面板里，见 `SessionGalleryPanel`）——导入功能从 RunBar 搬进来了。 */
    onImport: () -> Unit,
    /** 工具栏「铅笔」= 画布编辑器（原来在 RunBar 右下角那颗，功能不变）。 */
    onOpenCanvasEditor: () -> Unit,
    /** 工具栏「虚框」= 遮罩开关（原来也在 RunBar，功能不变）。 */
    onToggleMask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    // ⚠️ 用户 2026-09-26：「遮罩模式 / 画布编辑器**打开时不能打开临时图库**」——
    // 这两个模式都在画布上画画，图库抽屉再滑出来就会盖住它们的 UI ✗。所以：
    //  · 已经在展开的抽屉，一旦进了这两个模式就**自动收起** ✓；
    //  · 工具栏那颗图片按钮这时**置灰**（点不开 ✓，见下面 `SessionRailButton` 的 `enabled`）。
    val modesBusy = (state.maskMode && state.canvasMode != 2) || state.canvasEditorOpen
    LaunchedEffect(modesBusy) { if (modesBusy) open = false }
    // ⚠️ 用 `animateFloatAsState` 的 **State**（不是 `by`）：逐帧的值只在 `graphicsLayer` 的
    // lambda 里读，**不进组合**——否则这个抽屉（连带它上面那层宽屏分支）每帧都要重组一遍。
    val progress = animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
        label = "sessionGalleryDrawer",
    )
    // 组合里只读"有没有跨过阈值"：收起动画跑完就让它离开组合树
    val visible by remember { derivedStateOf { open || progress.value > 0.001f } }

    val density = LocalDensity.current
    val panelWidthPx = with(density) { SESSION_PANEL_WIDTH.toPx() }
    // 只把**左侧**两个角做圆：收起态看着就是"从画布右边缘突出来的一半"；
    // 展开态把手贴在面板**左边缘**（右边和面板连成一体），同一个形状两个态都对得上。
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    val handleShape = RoundedCornerShape(
        topStart = if (referenceSkin) 16.dp else 12.dp,
        bottomStart = if (referenceSkin) 16.dp else 12.dp,
    )

    Box(modifier.clipToBounds()) {
        // 把手 + 面板 = 同一条 Row，一起平移 —— "一体"在结构上就成立
        Row(
            Modifier
                .align(Alignment.CenterEnd)
                // ⚠️ 顺序：先铺满高度、再用 padding 让出上下边距，最后才定宽 ——
                // 反过来的话面板会比内容区高 32dp、上下各被裁掉 16dp。
                .fillMaxHeight()
                .padding(vertical = SESSION_PANEL_MARGIN)
                // 固定宽度：收起态面板不在组合树里，这块宽度得由**工具栏**自己占住（见上面的 KDoc）
                .width(SESSION_PANEL_WIDTH + if (referenceSkin) 68.dp else SESSION_RAIL_WIDTH)
                // 从右边推进来：收起时整块平移到画布外，被外层的 clipToBounds() 裁掉。
                // 展开时 translationX = 0 → 面板贴画布右边缘，把手正好落在**面板的左边缘**上。
                .graphicsLayer { translationX = panelWidthPx * (1f - progress.value) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ---- 竖向工具栏（用户 2026-09-26：照参考图竖排）----
            // 它在 Row 里**排在面板前面**，所以收起时停在画布右边缘、展开时贴面板左边缘 ✓。
            // 三颗：图片（开合临时图库） / 铅笔（画布编辑器） / 虚框（遮罩）——
            // **导入图片那颗从 RunBar 撤掉了**，它的位置就是这里的第一颗（点开图库，图库里能导入 ✓）。
            Column(
                Modifier
                    .width(if (referenceSkin) 68.dp else SESSION_RAIL_WIDTH)
                    .clip(handleShape)
                    .background(
                        LocalPanelBackgroundColor.current
                            ?: if (referenceSkin) NaiSkinTokens.panel() else MaterialTheme.colorScheme.surface,
                    )
                    .then(
                        if (referenceSkin) Modifier.border(1.dp, NaiSkinTokens.borderStrong(), handleShape)
                        else Modifier
                    )
                    .padding(vertical = if (referenceSkin) 10.dp else SESSION_RAIL_PAD),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (referenceSkin) 8.dp else 4.dp),
            ) {
                SessionRailButton(
                    icon = PictureIcon,
                    label = t("rail.gallery"),
                    selected = open,
                    description = t(if (open) "session.collapse" else "session.expand"),
                    enabled = !modesBusy,
                    onClick = { open = !open },
                )
                if (state.canvasMode != 2) {
                    SessionRailButton(
                        icon = Icons.Filled.Edit,
                        label = t("rail.brush"),
                        selected = state.canvasEditorOpen,
                        description = t("canvas.title"),
                        enabled = !state.busy,
                        onClick = onOpenCanvasEditor,
                    )
                    SessionRailButton(
                        icon = MaskSelectIcon,
                        label = t("rail.mask"),
                        selected = state.maskMode,
                        description = t("mask.toggle"),
                        enabled = state.workImagePath != null && !state.busy,
                        onClick = onToggleMask,
                    )
                }
            }

            // 面板：只在开着（或收起动画还没跑完）时进组合树；宽度和把手加起来正好是 Row 的宽度
            if (visible) {
                SessionGalleryPanel(
                    state = state,
                    t = t,
                    onImport = onImport,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(SESSION_PANEL_WIDTH)
                        // 收起态透明度为 0：滑出去的途中淡出，画布上不留一条边
                        .graphicsLayer { alpha = progress.value },
                    // 展开时才在面板自己那块矩形里吃点击：收起 / 推出去的途中不拦画布
                    absorbClicks = open,
                )
            }
        }
    }
}

/**
 * 竖向工具栏上的一颗图标按钮（用户 2026-09-26 加的：画布右边缘那条竖排工具）。
 *
 * 选中态用主色、其余用次要色 —— 和 RunBar 那两颗（铅笔 / 遮罩）**同一个口径** ✓
 * （它们就是从 RunBar 搬过来的，只是从"横排"改成"竖排"）。
 * 同样挂 [NoButtonFocus]：这几颗也不许拿焦点，否则空格会被当成"激活按钮"而不是"按住平移"。
 */
@Composable
private fun SessionRailButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    // ⚠️ 用户 2026-09-26：「右边栏**底部显示工具名字**，图库 / 画笔 / 遮罩」——
    // 图标下面加一行小字 ✓（和侧栏"收纳态"那一套同一个口径：图标在上、名字在下 ✓）。
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = when {
            !referenceSkin -> if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else Color.Transparent
            selected && pressed -> NaiSkinTokens.accentSoft(0.95f)
            selected -> NaiSkinTokens.accentSoft()
            pressed -> NaiSkinTokens.accentSoft(0.72f)
            hovered -> NaiSkinTokens.subtleHover()
            else -> Color.Transparent
        },
        animationSpec = if (referenceSkin) tween(120) else snap(),
        label = "sessionRailButtonBackground",
    )
    val tint by animateColorAsState(
        targetValue = when {
            selected && referenceSkin -> NaiSkinTokens.accent()
            selected -> MaterialTheme.colorScheme.primary
            referenceSkin && hovered -> NaiSkinTokens.text()
            referenceSkin -> NaiSkinTokens.muted()
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = if (referenceSkin) tween(120) else snap(),
        label = "sessionRailButtonForeground",
    )
    val pressScale by animateFloatAsState(
        targetValue = if (referenceSkin && enabled && pressed) 0.96f else 1f,
        animationSpec = if (referenceSkin) tween(120) else snap(),
        label = "sessionRailButtonPressScale",
    )
    Column(
        Modifier
            .then(
                if (LocalNaiSkin.current == NaiSkin.Reference) {
                    Modifier.width(REFERENCE_RAIL_BUTTON_WIDTH).height(REFERENCE_RAIL_BUTTON_HEIGHT)
                } else {
                    Modifier.fillMaxWidth()
                },
            )
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .then(
                if (referenceSkin && selected) {
                    Modifier.border(1.dp, NaiSkinTokens.borderStrong(), RoundedCornerShape(10.dp))
                } else Modifier,
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(
                if (referenceSkin) {
                    Modifier
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
                } else Modifier.clickable(enabled = enabled, onClick = onClick),
            )
            .padding(vertical = if (LocalNaiSkin.current == NaiSkin.Reference) 4.dp else 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(SESSION_RAIL_ICON_GLYPH),
            tint = tint,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 展开后的那块「媒体资产」面板：标题 / 搜索 + 筛选按钮 / 已生成·已导入两个分段标签 /
 * **两列大图网格**（用户 2026-09-22 第二轮：不要列表模式）。
 *
 * @param absorbClicks 展开时在面板**自己那块矩形**里吃掉点击 —— 否则按在面板空白处会传到下面
 *   把画布拖动起来。它**不含**面板以外的任何地方（命中矩形就是面板本身，见调用处的修饰符顺序）。
 */
@Composable
private fun SessionGalleryPanel(
    state: AppState,
    t: (String) -> String,
    /** 「导入图片」——**导入功能从 RunBar 搬进这里了**（用户 2026-09-26：
     *  「导入图片的功能变为临时图库」）：以前是画布底下那颗图片按钮，现在是工具栏的图片按钮
     *  开出这个面板，导入放在面板头部 ✓。 */
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    absorbClicks: Boolean = false,
) {
    val platform = LocalPlatform.current
    val density = LocalDensity.current
    // 缩略图解到 2× 显示尺寸，并乘系统 UI 缩放（和高 DPI 下"缩略图糊"那条修法一个口径）。
    // ⚠️ 大图网格里一格的**长边是高度**（2:3 竖版），所以按 `max(宽, 高)` 算 ——
    // 只按宽度算的话竖图会解码不够、铺满格子时又糊。
    val tileHeight = SESSION_TILE_WIDTH / SESSION_TILE_ASPECT
    val thumbPx = with(density) {
        (maxOf(SESSION_TILE_WIDTH, tileHeight).toPx() * 2f * platform.uiScale).roundToInt()
    }.coerceIn(128, 2048)

    var query by rememberSaveable { mutableStateOf("") }
    /** 0 = 已生成 / 1 = 已导入。 */
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<HistoryItem?>(null) }

    // 本次会话的条目（已按批次合成一项）
    val grouped = remember(
        state.history,
        state.sessionStartIso,
        state.sessionBatchIds.toMap(),
        state.sessionImageDurationMs.toMap(),
        tab,
    ) { sessionGalleryEntries(state, imported = tab == 1) }

    /**
     * 「已导入」还有一个**不走 history** 的来源：本次会话导入的那张图生图底图。
     *
     * 为什么需要它：`AppState.importImage()` 只把文件复制进 `imported/` 并设成底图，
     * **并没有往 `history` 里加条目**，所以光靠 history 这一栏基本是空的。
     * 它同样落在 `imported/` 下，判据一致；宽高用 `MaskCodec.imageSize` 现读一次文件头
     *（就是 `setI2iDisplay` 用的那条路，只读头部、很便宜）。
     */
    val importEntry = if (tab == 1) {
        val path = state.i2iOriginalPath?.takeIf { it.isNotBlank() && isSessionImportedPath(it) }
        if (path != null && grouped.none { it.item.filePath == path }) {
            val dims = remember(path) {
                runCatching { MaskCodec.imageSize(platform.images, path) }.getOrNull()
            }
            SessionGalleryEntry(
                // 它没有 HistoryItem：当场合成一条**只用于展示 / 选用**的最小记录
                item = HistoryItem(
                    id = "session-import-$path",
                    filePath = path,
                    date = "",
                    createdAt = state.sessionStartIso,
                    seed = 0L,
                    model = "",
                    width = dims?.first ?: 0,
                    height = dims?.second ?: 0,
                    prompt = "",
                ),
                count = 1,
                // 导入不"耗时"，角标位留空
                durationMs = null,
            )
        } else {
            null
        }
    } else {
        null
    }

    val keyword = query.trim()
    val entries = remember(grouped, importEntry, keyword, newestFirst) {
        (listOfNotNull(importEntry) + grouped)
            // 搜索：**按名字过滤**（用户 2026-09-22：「能按名字过滤就行」）
            .filter {
                keyword.isEmpty() ||
                    sessionFileName(it.item.filePath).contains(keyword, ignoreCase = true)
            }
            .let { if (newestFirst) it else it.reversed() }
    }

    val panelShape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier.then(
            if (absorbClicks) {
                val interaction = remember { MutableInteractionSource() }
                Modifier.clickable(interactionSource = interaction, indication = null) { }
            } else {
                Modifier
            },
        ),
        shape = panelShape,
        // 和现有浮动面板一个外观：surface 底 + 1dp 描边 + 圆角
        color = LocalPanelBackgroundColor.current ?: MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                t("generate.sessionGallery"),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
            )

            // ---- 搜索框 + 右侧那颗筛选 / 排序小按钮 ----
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 「导入图片」：从 RunBar 搬进来的那颗（用户 2026-09-26）——
                // 图标沿用原来的 `PictureIcon`，点了走同一条 `launchImport` ✓。
                IconButton(
                    onClick = onImport,
                    enabled = !state.busy,
                    modifier = Modifier.size(34.dp).then(NoButtonFocus),
                ) {
                    Icon(
                        imageVector = PictureIcon,
                        contentDescription = t("generate.importImage"),
                        modifier = Modifier.size(19.dp),
                        tint = if (state.busy) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
                Row(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                t("session.search"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            // 临时图库抽屉的搜索框：**必须登记焦点**（上一轮点名的另一个框）——
                            // 搜索词里带空格（"white dress"）是很正常的事，漏了就打不出来。
                            modifier = Modifier
                                .fillMaxWidth()
                                .trackTextInputFocus(state, "session.search"),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = t("session.clearSearch"),
                            modifier = Modifier.size(15.dp).clickable { query = "" },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 筛选 / 排序：先做出样子；能用的那一半是**切换排序方向**（箭头即方向）
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable { newestFirst = !newestFirst },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (newestFirst) {
                            Icons.Filled.KeyboardArrowDown
                        } else {
                            Icons.Filled.KeyboardArrowUp
                        },
                        contentDescription = t("session.filter"),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- 两个分段标签：已生成 / 已导入 ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                SessionSegment(t("session.tabGenerated"), tab == 0, Modifier.weight(1f)) { tab = 0 }
                SessionSegment(t("session.tabImported"), tab == 1, Modifier.weight(1f)) { tab = 1 }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            if (entries.isEmpty()) {
                Text(
                    when {
                        keyword.isNotEmpty() -> t("session.noMatch")
                        tab == 1 -> t("session.emptyImported")
                        else -> t("session.emptyGenerated")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                )
            } else {
                // ---- 大图网格（用户 2026-09-22 第二轮：「临时图库使用大图模式，不要用列表模式」）----
                LazyVerticalGrid(
                    // 固定两列：面板宽度是定值，格子宽度也就是定值（见 SESSION_TILE_WIDTH）
                    columns = GridCells.Fixed(SESSION_GRID_COLUMNS),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(
                        start = SESSION_GRID_PADDING,
                        end = SESSION_GRID_PADDING,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(SESSION_GRID_SPACING),
                    verticalArrangement = Arrangement.spacedBy(SESSION_GRID_SPACING),
                ) {
                    gridItems(entries, key = { it.item.id }) { entry ->
                        SessionGalleryTile(
                            entry = entry,
                            thumbPx = thumbPx,
                            t = t,
                            menuOpen = menuFor == entry.item.id,
                            onOpenMenu = { menuFor = entry.item.id },
                            onDismissMenu = { menuFor = null },
                            onClick = { viewing = entry.item },
                            onUseAsCurrent = {
                                menuFor = null
                                state.useImageAsI2iBase(
                                    entry.item.filePath,
                                    entry.item.width,
                                    entry.item.height,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    // 点一项 = 全屏查看器（沿用现有行为：和画廊同一套多张左右滑）。
    // ⚠️ 图库那套**右键小菜单**（`GalleryScreen.GalleryItemContextMenu`）是 private，共用树里拿不到，
    // 所以右键这里只留「用作当前工作图」一条（用户 2026-09-22 允许的兜底口径）。
    viewing?.let { item ->
        FullscreenImageViewer(
            images = entries.map { ViewerImage(path = it.item.filePath) },
            initialPage = entries.indexOfFirst { it.item.id == item.id }.coerceAtLeast(0),
            onPageChange = { page -> entries.getOrNull(page)?.let { viewing = it.item } },
            onDismiss = { viewing = null },
        )
    }
}

/** 分段标签里的一格：选中时用 `surface` 底 + 主色文字（截图里那种两段式标签）。 */
@Composable
private fun SessionSegment(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * 大图网格里的**一格**：缩略图（复用现成的 [FileImage]，走同一套缩略图缓存）铺满格宽，
 * 下面两行小字（名字 / `时长 宽×高`）；同一批多张时右上角一颗角标显示张数。
 *
 * （上一轮这里是列表里的一行 `SessionGalleryRow`，用户 2026-09-22 第二轮改成**大图模式**。）
 *
 * 手势：**左键 = 看大图**、**右键 = 小菜单**（都排在 `fillMaxWidth` / `clip` / `padding`
 * 这些布局 / 尺寸修饰符之后 —— 顺序反了命中矩形就不是这一格）。
 */
@Composable
private fun SessionGalleryTile(
    entry: SessionGalleryEntry,
    thumbPx: Int,
    t: (String) -> String,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onClick: () -> Unit,
    onUseAsCurrent: () -> Unit,
) {
    // 包一层 Box：右键小窗口（DropdownMenu）的锚点是**父节点**，直接当兄弟会锚到整个网格上
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .onSecondaryClick { onOpenMenu() }
                .padding(4.dp),
        ) {
            // 缩略图：铺满格宽、2:3 固定比例（`Crop` 裁掉溢出）→ 整个网格是齐整的大图墙
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(SESSION_TILE_ASPECT)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                FileImage(
                    path = entry.item.filePath,
                    maxDimension = thumbPx,
                    useThumbnail = true,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // 第 1 行小字：名字
            Text(
                sessionFileName(entry.item.filePath),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 5.dp),
            )
            // 第 2 行小字：元信息 `时长  宽×高`（例：9.60s 832×1216）
            Text(
                "${sessionDurationText(entry.durationMs)}  ${entry.item.width}×${entry.item.height}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 2.dp),
            )
        }

        // 同一批多张 → **缩略图**右上角一颗角标（截图里那个「⧉ 2」）
        if (entry.count > 1) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    // 4dp 是上面 Column 的内边距，再加 4dp 才落在缩略图里面
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                SessionBatchGlyph(
                    color = MaterialTheme.colorScheme.onPrimary,
                    // 那个小图形读屏读不出来，文案挂在它身上：同一批几张
                    label = t("session.batchBadge").replace("{count}", entry.count.toString()),
                )
                Text(
                    entry.count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(
                text = {
                    Text(
                        t("session.useAsCurrent"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                onClick = onUseAsCurrent,
            )
        }
    }
}

/**
 * 角标里那个"两张叠在一起"的小图形。
 *
 * 截图里是字形 **⧉**（U+29C9）：字体缺字时会变成豆腐块，所以这里用**两个叠着的小方框**自己画，
 * 不依赖任何字体，两端观感一致。
 */
@Composable
private fun SessionBatchGlyph(color: Color, label: String) {
    val corner = RoundedCornerShape(1.dp)
    Box(
        Modifier
            .size(9.dp)
            .semantics { contentDescription = label },
    ) {
        Box(Modifier.align(Alignment.BottomStart).size(6.dp).border(1.dp, color, corner))
        Box(Modifier.align(Alignment.TopEnd).size(6.dp).border(1.dp, color, corner))
    }
}

/**
 * **宽窗口停靠的提示词栏**：栏目条 + 内容（2026-09-29 卡顿修复 ✓）。
 *
 *  · `panelTab` 只在**这里**读（`selectedTab` 是个 lambda ✓）⇒ 切栏只重组这一小块 ✓，不再牵动整个生图页 ✗；
 *  · 两栏的内容**都常驻组合** ✓，切栏只切显隐（隐藏的那一栏不测量、不摆放、不吃点击 ✓）——
 *    原来每切一次都要把「提示词 + 角色」或「参数」整棵树从零搭一遍，就是用户说的那一下卡 ✗；
 *  · 切栏时先清焦点 ✓：否则光标还留在隐藏的那个输入框里，打字会打进看不见的地方 ✗。
 */
@Composable
private fun DockedPanelTabs(
    state: AppState,
    t: (String) -> String,
    selectedTab: () -> Int,
    onSelectTab: (Int) -> Unit,
    promptScroll: ScrollState,
    paramsScroll: ScrollState,
    contentPadding: PaddingValues,
    spacing: Dp,
    activeField: String,
    onFieldFocus: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = selectedTab()
    // 选中的那一栏要是**已经浮出去了**，就退到第一个还在的栏（否则内容区一片空白 ✗）
    val shown = if (state.isPanelDetached(selected)) {
        listOf(TAB_PROMPTS, TAB_CHARACTERS, TAB_PARAMS).firstOrNull { !state.isPanelDetached(it) } ?: selected
    } else {
        selected
    }
    val focusManager = LocalFocusManager.current
    Column(modifier) {
        PanelTabStrip(
            selectedTab = selected,
            onSelectTab = { tab ->
                if (tab != selected) {
                    focusManager.clearFocus()
                    onSelectTab(tab)
                }
            },
            t = t,
            hiddenTabs = state.detachedPanels,
            comicStoryboard = state.canvasMode == 2,
            showParamsButton = false,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val paramsShown = shown == TAB_PARAMS
            DockedTabPage(visible = !paramsShown, scroll = promptScroll, contentPadding = contentPadding, spacing = spacing) {
                PanelTabBody(
                    tab = if (paramsShown) TAB_PROMPTS else shown,
                    state = state,
                    t = t,
                    onOpenPresets = { state.openStyleSheet() },
                    activeField = activeField,
                    onFieldFocus = onFieldFocus,
                )
            }
            DockedTabPage(visible = paramsShown, scroll = paramsScroll, contentPadding = contentPadding, spacing = spacing) {
                PanelTabBody(
                    tab = TAB_PARAMS,
                    state = state,
                    t = t,
                    onOpenPresets = { state.openStyleSheet() },
                    activeField = activeField,
                    onFieldFocus = onFieldFocus,
                )
            }
        }
    }
}

/** 一栏的滚动内容；`visible = false` 时照样组合，但**不测量、不摆放** ✓（0×0，点不到 ✓）。 */
@Composable
private fun DockedTabPage(
    visible: Boolean,
    scroll: ScrollState,
    contentPadding: PaddingValues,
    spacing: Dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .then(
                if (visible) {
                    Modifier
                } else {
                    Modifier.layout { _, _ -> layout(0, 0) {} }
                },
            )
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}
