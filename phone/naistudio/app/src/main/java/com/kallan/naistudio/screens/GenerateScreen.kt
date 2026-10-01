package com.kallan.naistudio.screens

import androidx.compose.ui.draw.scale
import com.kallan.naistudio.ui.RefSwitch
import com.kallan.naistudio.ui.RefChevronRightIcon
import com.kallan.naistudio.ui.RefDropIcon
import com.kallan.naistudio.ui.RefCountBadge
import com.kallan.naistudio.ui.RefOptionCard
import com.kallan.naistudio.ui.runCanvasBackdrop
import com.kallan.naistudio.ui.runGradient
import com.kallan.naistudio.ui.runSkin
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.draw.drawBehind
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import com.kallan.naistudio.ui.RefButton as Button
import com.kallan.naistudio.ui.RefCard as Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.kallan.naistudio.ui.RefFilterChip as FilterChip
import androidx.compose.material3.Icon
import com.kallan.naistudio.ui.RefIconButton as IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import com.kallan.naistudio.ui.RefModalBottomSheet as ModalBottomSheet
import com.kallan.naistudio.ui.RefOutlinedButton as OutlinedButton
import com.kallan.naistudio.ui.RefOutlinedTextField as OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import com.kallan.naistudio.ui.RefTextButton as TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
// ⑨c 苹果风（2026-09-24）：中控台那两块要一圈细亮边 + 顶上一条内高光 ✓
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import com.kallan.naistudio.models.GenerateActions
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.kallan.naistudio.BuildConfig
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.NaiCatalog
import com.kallan.naistudio.models.NaiOption
import com.kallan.naistudio.services.DirectorTools
import com.kallan.naistudio.services.MaskCodec
import com.kallan.naistudio.services.OfficialUpscale
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.state.formatDuration
import com.kallan.naistudio.models.PromptField
import com.kallan.naistudio.models.TextFieldTarget
import com.kallan.naistudio.ui.BoltIcon
import com.kallan.naistudio.ui.SlidersIcon
import com.kallan.naistudio.ui.CollapsibleSubSection
import com.kallan.naistudio.ui.FileImage
import com.kallan.naistudio.ui.FullscreenImageViewer
import com.kallan.naistudio.ui.LabeledSlider
import com.kallan.naistudio.ui.NumberField
import com.kallan.naistudio.ui.PickerField
import com.kallan.naistudio.ui.ClockIcon
import com.kallan.naistudio.ui.followFingerForSelection
import com.kallan.naistudio.ui.LockClosedIcon
import com.kallan.naistudio.ui.LockOpenIcon
import com.kallan.naistudio.ui.MaskSelectIcon
import com.kallan.naistudio.ui.PictureIcon
import com.kallan.naistudio.ui.RedoIcon
import com.kallan.naistudio.ui.UndoIcon
import com.kallan.naistudio.ui.rememberPromptHighlight
import com.kallan.naistudio.ui.NaiGlass
import com.kallan.naistudio.ui.NaiShape
import com.kallan.naistudio.ui.SectionCard
import com.kallan.naistudio.ui.SparkleIcon
import com.kallan.naistudio.ui.StrokedText
import com.kallan.naistudio.ui.SubHeader
import com.kallan.naistudio.ui.SwitchRow
import com.kallan.naistudio.ui.ViewerImage
import com.kallan.naistudio.ui.useSplitLayout
import com.kallan.naistudio.ui.LocalRef
import com.kallan.naistudio.ui.glass
import com.kallan.naistudio.ui.RefBtn
import com.kallan.naistudio.ui.RefBtnKind
import com.kallan.naistudio.ui.RefChip
import com.kallan.naistudio.ui.RefFChip
import com.kallan.naistudio.ui.RefSeg
import com.kallan.naistudio.ui.RefHint
import com.kallan.naistudio.ui.RefSheetTitle
import com.kallan.naistudio.ui.RefChevron
import com.kallan.naistudio.ui.RefResizeIcon
import com.kallan.naistudio.ui.RefMovieIcon
import com.kallan.naistudio.ui.RefUsersIcon
import com.kallan.naistudio.ui.RefTrashIcon
import com.kallan.naistudio.ui.RefHistoryIcon
import com.kallan.naistudio.ui.RefEditIcon
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import com.kallan.naistudio.ui.RefStrip

/** 底部抽屉的 Tab 索引：提示词 / **角色（中间）** / 参数。 */
private const val TAB_PROMPTS = 0
private const val TAB_CHARACTERS = 1
private const val TAB_PARAMS = 2

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
// ⚠️ 参数面板用 `ModalBottomSheet`（实验 API）⇒ 这一层要 OptIn
@OptIn(ExperimentalMaterial3Api::class)
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

    /**
     * **参数面板开着没有**（用户 2026-09-25：「参数面板**从抽屉体系剥离**，不能在抽屉中
     * 滑动进入，它是**抽屉样式**，**点击空白可退出**」）。
     *
     * ⚠️ 摆在 `GenerateScreen` 这一层：拉它的**滑条图标**在 `RunBar` 里（第 0 页那一摞），
     *    而面板本身画在这一层 —— 两者都在这个作用域内。
     * ⚠️ 它和底部那个抽屉**没有关系**：不在栏目条里、左右滑动进不来、
     *    也不参与 `panelTarget` 那套拖动，就是**独立的一层**（和角色抽屉同一种做法）。
     */
    var paramsSheetOpen by remember { mutableStateOf(false) }

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

    // ---- **参数面板**（用户 2026-09-25：「参数面板**从抽屉体系剥离**，不能在抽屉中滑动进入，
    //      它是**抽屉样式**，**点击空白可退出**」）----
    //
    // ⚠️ 它跟底部那个抽屉**一点关系都没有** ✗：
    //  · 不在栏目条里（那一条现在只有提示词 / 角色两格）；
    //  · 左右滑动切栏**进不来**（滑动上限就是角色，见 `panelTabSwipe`）；
    //  · 不参与 `panelTarget` 那套拖动 —— 就是一个**独立的一层**（和角色抽屉同一种做法）。
    //
    // ⚠️ 用 `ModalBottomSheet` 而不是自绘浮层：它天生就是"抽屉样式 + 点空白退出 + 下拉关闭"
    //   （正好是用户要的三条），自绘反而要自己补手势 ✗。
    // ⚠️ 内容直接用 `ParamsFields`（一个控件都没改），只是外面换了个容器。
    if (paramsSheetOpen) {
        // ⚠️ `GenerateScreen` 这一层没有现成的 `t`（它是各子组件自己造的）——
        //    这里按同一个口径现造一个（和上面那个角色图鉴抽屉一模一样）。
        val paramsLanguage = state.settings.language
        val paramsT: (String) -> String = { key -> RuntimeText.text(paramsLanguage, key) }
        val paramsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // ⚠️ 用户 2026-09-26：「**优化参数抽屉，拉起的抽屉低一些，半透明磨砂背景**」——
        //  · **低一些**：原来是"内容多高就多高" ⇒ 参数项排下来直接顶到屏幕上沿、几乎盖满。
        //    现在按**屏高的 0.6** 钳一档，超出的部分靠面板内部滚动看（`verticalScroll`）；
        //    上面那张图始终露着一截，也还能看见"参数是从哪张图上改的"。
        //  · **半透明磨砂**：原来用的是 M3 默认的**不透明**容器色 ⇒ 参数面板和底部抽屉
        //    那套毛玻璃长得不一样。现在改成和底部抽屉（`ControlPanel`）**同一档**：
        //    `surface @ NaiGlass.PANEL_ALPHA`（0.78），另一半交给下面那个
        //    "把背后内容糊掉"的 `Modifier.blur`（已按 `paramsSheetOpen` 触发）—— 真毛玻璃
        //    的两半就齐了（见 `NaiGlass` 的说明：只做一半不是"隔着磨砂板看清晰的图"就是看不见效果）。
        val paramsMaxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
        ModalBottomSheet(
            onDismissRequest = { paramsSheetOpen = false },
            sheetState = paramsSheetState,
            // 遮罩压得很淡：面板本身半透明、背后那层图已经糊了，再压一层黑会把"玻璃"压成"塑料"。
            // ⚠️ 遮罩照样**在位可点** —— 点空白退出不受 `scrimColor` 影响，只是不画那么黑。
            scrimColor = Color.Black.copy(alpha = 0.12f),
        ) {
            RefSheetTitle(title = paramsT("generate.params"), onClose = { paramsSheetOpen = false })
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = paramsMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            ) {
                ParamsFields(state, paramsT)
            }
        }
    }
    // 导入图片 = 图生图模式的入口（相册/文件选择器，走系统的 Photo Picker，不需要读存储权限）
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) state.importImage(uri)
    }
    val launchImport: () -> Unit = {
        importLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
    val language = state.settings.language
    val t: (String) -> String = { key -> RuntimeText.text(language, key) }

    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp.dp
    val heightDp = configuration.screenHeightDp.dp
    val split = useSplitLayout(widthDp, heightDp)

    // 底部控制面板：Tab（0=提示词 1=参数 2=角色）
    var panelTab by rememberSaveable { mutableIntStateOf(TAB_PROMPTS) }
    // 风格预设：仍然是「选择预设」按钮打开的那个独立面板
    var showPresetSheet by rememberSaveable { mutableStateOf(false) }
    // 导演台抽屉是否开着（由「导演台」那个方款按钮切换）
    var directorOpen by rememberSaveable { mutableStateOf(false) }
    // 「历史提示词」抽屉（正面提示词下面那个时钟图标）
    var promptHistoryOpen by rememberSaveable { mutableStateOf(false) }
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
    /** 常驻按钮栏（垃圾桶/撤销/历史/翻译/优化那一行）单独的高度。 */
    var actionBarHeightPx by remember { mutableIntStateOf(0) }
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
     * 上浮组距离**窗口底**的距离 —— 让「把手行 + 抽屉 + 文本框工具栏」停在键盘上方。
     *
     * ═══════════ 这一处我改坏过四次，机制写在下面，别再凭感觉动 ═══════════
     *
     * **决定性事实**（查证过，不是推测）：`targetSdk 35+` 之后，**`adjustResize` 不再压缩窗口**
     * （见 https://stackoverflow.com/questions/79177674/windowsoftinputmode-adjustresize-doesnt-work-in-target-sdk-35 ）。
     * 本项目 `targetSdk = 36` ⇒ **键盘弹起时窗口高度不变**，
     * 想避开键盘就**必须**自己按 `WindowInsets.ime` 把内容抬起来 ✓。
     *
     * ⇒ 这个值**必须**含 `imeBottom`：
     *  · 键盘收起：`imeBottom = 0` ⇒ 取 `fixedGroupHeight`，上浮组正好落在固定组上方 ✓；
     *  · 键盘弹起：`imeBottom` 大 ⇒ 整组抬到键盘上方，**最下面那条（文本框工具栏）贴着键盘** ✓。
     *
     * ⚠️⚠️ **改坏史（四次，都别再走）**：
     *  ① 写成 `fixedGroupHeight` ⇒ 键盘弹起时**一点都不抬** ⇒ **键盘把抽屉盖住** ✗
     *     （用户报的"键盘弹出底栏抽屉下拉，键盘遮挡抽屉"就是这个）；
     *  ② 往组里塞 `Spacer(floatLift)` ⇒ 面板下沉 + 面板上方被底色盖住 ✗；
     *  ③ 给整列加 `.background(...)` ⇒ 面板以上多出一片底色 ✗；
     *  ④ 在组下面铺一条"垫底带子" ⇒ 多盖一层（而且那条缝根本不存在）✗。
     *
     * ⚠️ 唯一**可能**还需要微调的情形：`WindowInsets.ime` 在某些 ROM 上会把
     *    系统手势条那一截也算进去 ⇒ 抬过头、露出一点点缝。真有那种缝（不是"盖住"）
     *    再来减掉 `navigationBars` 的底 inset，**不要**再把 imeBottom 整个去掉 ✗。
     */
    /**
     * ⚠️⚠️ **`imeBottom` 要减掉外壳底部那一段**（`chromeBottom` = 底部导航栏的高度）：
     *  · 内容的坐标系底边 = **窗口底 − 底部栏**（Box 吃了 `padding(inner)`）；
     *  · 而 `imeBottom` 是**相对窗口底**量的键盘高度；
     *  · ⇒ 想"让工具条贴在键盘上沿"，抬升量必须是 `imeBottom − chromeBottom` ✓；
     *    直接用 `imeBottom` 就**多抬一条底部栏**、键盘上方空出一条 ✗。
     */
    val chromeBottom = with(LocalDensity.current) { state.phoneBottomBarHeightPx.toDp() }
    val floatLift = maxOf(fixedGroupHeight, (imeBottom - chromeBottom).coerceAtLeast(0.dp))

    /** 「常驻按钮栏 + 2x放大/导演台 + 运行条」三行的总高（改动前那一整块）。 */
    val bottomBarHeight = with(LocalDensity.current) {
        (peekRowHeightPx + actionBarHeightPx + fixedGroupHeightPx).toDp()
    }
    /** 上浮组里"不包含抽屉面板"的那部分高度（把手行 + 按钮栏）。 */
    val peekAndActionHeight = with(LocalDensity.current) {
        (peekRowHeightPx + actionBarHeightPx).toDp()
    }

    /**
     * 抽屉面板能长到多高（px→dp 已换算好的钳制值）。`null` = 还没量到视口。
     *
     * 上浮组是从 (屏幕底 - floatLift) 往上长的，顶到屏幕顶为止 ——
     * 所以面板最多只能占「可用高度 - floatLift - 把手行 - 按钮栏」。
     */
    val availableForPanel: Dp? = if (viewportHeightPx > 0) {
        // ⚠️ 用户 2026-09-25：「底部抽屉现在铺满画面，**到余额胶囊下面**」——
        //    这里再扣掉**浮动条（含余额胶囊）的下沿**（`phoneFloatingBottomPx`，
        //    由 `StudioShell` 量好）⇒ 抽屉顶到胶囊下面就停，**永远盖不住余额**。
        //    ⚠️ 这一项**必须扣**：不扣的话抽屉会伸到胶囊那一层去把它盖掉
        //   （胶囊是浮层、画在 pager **之后**，抽屉在 pager **里面** —— 抽屉在后画的层下面，
        //    所以"被盖住"的是胶囊 ✗）。
        val floatingBottom = with(LocalDensity.current) { state.phoneFloatingBottomPx.toDp() }
        with(LocalDensity.current) {
            // ⚠️ `imeBottomPx` 同样要**减掉外壳底部**（`chromeBottomPx`）——理由与
            //    `floatLift` 完全一样：这个视口也是被 `padding(inner)` 让过的 ✗。
            // ⚠️ 这里用的是 `viewportHeightPx`（Box 实测高），它**已经**不含底部栏了 ✓，
            //    所以扣的那一项是"键盘盖在**这个视口**里的高度" = `imeBottomPx - phoneBottomBarHeightPx` ✓。
            val imeOverViewport = (imeBottomPx - state.phoneBottomBarHeightPx).coerceAtLeast(0)
            (viewportHeightPx - maxOf(fixedGroupHeightPx, imeOverViewport)).toDp()
        } - peekAndActionHeight - floatingBottom - 12.dp
    } else {
        null
    }

    /**
     * 抽屉面板的上限 = **一路到余额胶囊下面**（用户 2026-09-25 定的 ✓）。
     *
     * ⚠️ 这里原来还**多夹了一道屏幕的 0.6** ✗：这台机器 800dp 高 ⇒ 0.6 = 480dp，
     *    而"到胶囊下面"实际有约 570dp ⇒ **白白空掉 ~90dp 画布没用上** ✗ ——
     *    看起来就是"没铺满、在胶囊下面留了一条"（用户报的正是这个 ✓）。
     *    现在**以胶囊那条线为准**（`availableForPanel` 里已经扣掉了它 ✓），
     *    0.6 只在"视口还没量到"时当兜底 ✓。
     */
    val baseMaxPanelHeight = (configuration.screenHeightDp * 0.6f).dp
    // ⚠️ **不再夹 0.6** ✗（见上）：直接以"到胶囊下面"这条线为准 ✓。
    val maxPanelHeight = (availableForPanel ?: baseMaxPanelHeight).coerceAtLeast(0.dp)

    /**
     * **剧情放大编辑页**的上限：**屏幕的 0.85**。
     *
     * 比普通面板高一大截是有意的（用户 2026-09-16 授权"根据效果调整大小"）：
     * 这一页干的是一件很不一样的事 —— 单纯把长剧情摊开**读和校对**，
     * 不需要留出预览图，所以能占多少占多少，只留一条把手行 + 状态栏的余量。
     */
    val basePlotEditorHeight = (configuration.screenHeightDp * 0.85f).dp
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
            if (BuildConfig.DEBUG) {
                Log.d("NaiPanel", "settle net=$net vel=$velocityY ${panelTarget}->$decided")
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
        // ⚠️ 例外：**参数模式下收起来**要把栏目记忆退回「提示词」（用户 2026-09-25 把参数从
        //    栏目条里撤了）—— 参数那一档**不画栏目条**，留着它下次拉开就是"一片没有栏目条
        //    的参数表单"，用户没有出口 ✗。退回提示词之后再拉开就是正常的两格栏目条 ✓；
        //    想回参数，再点运行条上那颗齿轮即可 ✓。
        if (panelTarget > 0.5f && panelTab == TAB_PARAMS) panelTab = TAB_PROMPTS
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
                state.chooseCanvasMode(2)
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
    LaunchedEffect(peekExpanded, panelTab) {
        // ⚠️ **预热要把这条挡掉**（用户 2026-09-16）。`setPromptTabActive` 会
        // 「在提示词栏 → 强制角色模式」，而开屏预热是我们自己把抽屉瞬时打开的、
        // 用户并没有选择任何栏目 —— 不拦的话，漫画模式的用户一进 App（抽屉还没碰过）
        // 就被静默降级成角色模式了。预热只借这棵子树的**组合**，不借它的交互语义。
        if (peekExpanded && !panelPrewarm) state.setPromptTabActive(panelTab == TAB_PROMPTS)
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

    if (split) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { inner ->
            // 宽屏**没有**底部那两块覆盖层，躲不了键盘，所以这里自己 imePadding
            // （壳层对生成页不做 imePadding，见 StudioShell 里的说明）。
            Row(Modifier.fillMaxSize().imePadding().padding(inner)) {
                Column(
                    Modifier
                        .weight(5f)
                        .padding(start = 10.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 宽屏：工具条同样顶替顶栏那一条（单行 64dp，不占图片的高度）
                    if (state.maskMode && state.canvasMode != 2) {
                        RefStrip {
                            MaskToolBar(state, t)
                            InpaintParamsTrigger(
                                t = t,
                                expanded = inpaintParamsOpen,
                                onToggle = { inpaintParamsOpen = !inpaintParamsOpen },
                            )
                        }
                    } else if (state.img2imgActive && state.canvasMode != 2) {
                        RefStrip {
                            I2iParamsTrigger(
                                t = t,
                                expanded = i2iParamsOpen,
                                onToggle = { i2iParamsOpen = !i2iParamsOpen },
                            )
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CanvasArea(state, t, Modifier.fillMaxSize())
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
                    // 宽屏没有底部抽屉，但那条常驻按钮栏照样要挂在「导演台」上面
                    PromptActionBar(
                        state = state,
                        t = t,
                        // ⚠️ 目标 = **当前聚焦的框**（用户 2026-09-17 报：在剧情框里按翻译，翻译的却是提示词）。
                        // 以前这里用的是 UI 本地的 activePromptField（只跟着三个提示词框走），
                        // 而标签取自 state.textFieldTarget —— 于是"显示作用于剧情"但实际作用于提示词。
                        targetField = if (state.textFieldTarget == TextFieldTarget.Plot) {
                            PromptField.PLOT
                        } else {
                            activePromptField
                        },
                        onOpenHistory = { promptHistoryOpen = true },
                    )
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
                VerticalDivider()
                Column(
                    Modifier
                        .weight(6f)
                        .padding(start = 6.dp, top = 8.dp, end = 10.dp, bottom = 8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionCard(t("generate.prompts")) {
                        PromptFields(
                            state = state,
                            t = t,
                            onOpenPresets = { showPresetSheet = true },
                            activeField = activePromptField,
                            onFieldFocus = { activePromptField = it },
                        )
                    }
                    SectionCard(t("generate.params")) {
                        ParamsFields(state, t)
                    }
                    // 宽屏没有底部抽屉，角色分区直接摆在右栏（否则平板上够不着）
                    SectionCard(t(if (state.canvasMode == 2) "char.tab" else "char.title")) {
                        CharacterEditorPanel(state, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { inner ->
            // ⛔ 这里**不再自己量底栏高度** —— 试过，量到的是 **0**：
            //    生成页**自己那个 Scaffold 没有 `bottomBar`**、且 `contentWindowInsets` 清零，
            //    所以 `inner.calculateBottomPadding()` 恒为 0；真正让位的是**壳层**那个
            //    `.padding(inner)`（`StudioShell`）。
            //    ⇒ 底栏高度由**壳层**量好放进 `state.phoneBottomBarHeightPx`（`floatLift` 直接读）。
            Box(
                Modifier
                    .fillMaxSize()
                    // 网页 v6：画布背景 = 电脑线 referenceBackdrop + 24dp 网格（按配色 / 明暗换色）
                    .runCanvasBackdrop(LocalRef.current.runSkin())
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
                        //
                        // ⚠️ 用户 2026-09-26「参数抽屉**半透明磨砂背景**」：**参数面板也吃这一档** ——
                        //    它是独立的一层 `ModalBottomSheet`，和底部抽屉互不相干，所以
                        //    光把它的容器色调半透明、背后却还是清晰的图，就是"隔着磨砂板看清晰的图"。
                        //    `paramsSheetOpen` 一置位就把半径拉满（开/关各一次，不是每帧变）
                        //    ⇒ 真毛玻璃的另一半。参数面板收起时这一项归 0，退出离屏图层。
                        .blur(
                            radius = NaiGlass.blurRadius * if (state.canvasMode == 1 && imeBottomPx > 0) {
                                // 无限画布的尺寸框在画布层；键盘弹出时必须保持清晰。
                                0f
                            } else {
                                maxOf(
                                    if (panelDragging) 0f else panelFraction,
                                    if (imeBottomPx > 0) 1f else 0f,
                                    if (paramsSheetOpen) 1f else 0f,
                                )
                            },
                        ),
                ) {
                    // 遮罩模式的工具条**顶替隐藏掉的顶栏**（外壳在遮罩模式下不画 TopAppBar）。
                    // 注意**不要再加 statusBarsPadding**：外壳的 Scaffold 已经把状态栏 inset 算进
                    // innerPadding 里了，重复加会让工具条下移一整条状态栏、图片跟着缩小。
                    // 网页 `.strip`：悬空的 glass 圆角卡（左右 12），工具条 + 「重绘参数 ▾」都在卡里
                    if (state.maskMode && state.canvasMode != 2) {
                        RefStrip {
                            MaskToolBar(state, t)
                            InpaintParamsTrigger(
                                t = t,
                                expanded = inpaintParamsOpen,
                                onToggle = { inpaintParamsOpen = !inpaintParamsOpen },
                            )
                        }
                    } else if (state.img2imgActive && state.canvasMode != 2) {
                        // 图生图参数：和重绘参数同一套"从卡里下拉"的做法
                        // 外壳的侧栏/余额悬浮按钮在最上层；整张参数卡从它们下方开始。
                        Spacer(Modifier.height(with(LocalDensity.current) { state.phoneFloatingBottomPx.toDp() }))
                        RefStrip {
                            I2iParamsTrigger(
                                t = t,
                                expanded = i2iParamsOpen,
                                onToggle = { i2iParamsOpen = !i2iParamsOpen },
                            )
                        }
                    }
                    // 图片铺满剩余空间；面板是**盖在它上面**的（不挤压图片）。
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(4.dp)
                            // 网页 v2：图片下移 —— 普通模式上边让出「侧栏按钮 / 余额胶囊」那一排，
                            // 图片不再从两者中间开始；下边留 8 贴近收起的底部抽屉。
                            // 遮罩 / 图生图有自己的工具条顶在上面，不再额外让。
                            .padding(
                                top = if (state.canvasMode != 2 && (state.maskMode || state.img2imgActive)) 0.dp else 62.dp,
                                bottom = bottomBarHeight + 4.dp,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 手机生成页没有整体 imePadding；输入框所在的无限画布单独让出
                        // 键盘盖过画布底边的高度，长宽输入框才能贴着键盘上沿显示。
                        CanvasArea(
                            state,
                            t,
                            Modifier.fillMaxSize().padding(
                                bottom = if (state.canvasMode == 1) {
                                    (imeBottom - chromeBottom - bottomBarHeight - 4.dp).coerceAtLeast(0.dp)
                                } else {
                                    0.dp
                                },
                            ),
                        )
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
                        .background(LocalRef.current.glass())
                        .onSizeChanged { fixedGroupHeightPx = it.height },
                ) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalRef.current.border))
                    RunBar(
                        state = state,
                        t = t,
                        // ⚠️ 用户 2026-09-25：参数**从抽屉体系剥离** ⇒ 这里开的是一层
                        //    **独立**的抽屉式面板（点击空白退出），不再去动底部抽屉的栏目。
                        onOpenParams = { paramsSheetOpen = true },
                        onToggleMask = {
                            // 进遮罩模式就把面板收掉，让整张图能让出来涂
                            panelTarget = 0f
                            state.toggleMaskMode()
                        },
                        onImportImage = launchImport,
                    )
                }
                // ---- 上浮组：把手行 → 面板 → 文本框工具栏 ----
                // ⚠️ 用 `padding(bottom = floatLift)` 把它整体抬起；**不要塞 `Spacer`** ✗
                //   （padding 是"内容之外"，不改变内容相对关系；Spacer 会把内容往下挤 ✗）。
                // ⚠️ 也**不要**在下面再铺一条"垫底带子" ✗ —— 键盘弹起时窗口已被压缩
                //   （见 `floatLift` 的说明），底下没有露白，多铺一层反而会盖住画面 ✓。
                val cpanelShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = floatLift)
                        // 网页 `.cpanel`：margin 0 10、glass、描边、顶部 20 圆角、向上的柔和阴影
                        .padding(horizontal = 10.dp)
                        .shadow(
                            10.dp,
                            cpanelShape,
                            ambientColor = Color.Black.copy(alpha = 0.14f),
                            spotColor = Color.Black.copy(alpha = 0.14f),
                        )
                        .clip(cpanelShape)
                        .background(LocalRef.current.glass())
                        .border(1.dp, LocalRef.current.border, cpanelShape),
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
                                panelHeight = plotEditorHeight * panelFraction,
                            )
                        } else {
                            ControlPanel(
                                state = state,
                                t = t,
                                selectedTab = panelTab,
                                onOpenPresets = { showPresetSheet = true },
                                activeField = activePromptField,
                                onFieldFocus = { activePromptField = it },
                                panelHeight = maxPanelHeight * panelFraction,
                                scrollConnection = panelNestedScroll,
                                directorOpen = directorOpen,
                                onToggleDirector = {
                                    directorOpen = !directorOpen
                                    // 打开导演台时把底部参数抽屉收回去，只留导演台（用户要求）
                                    if (directorOpen) panelTarget = 0f
                                },
                                onOpenHistory = { promptHistoryOpen = true },
                            )
                        }
                    }
                }
            }
        }
    }

    // 导演台：从底部上滑的抽屉（「导演台」按钮开关它）
    if (directorOpen) {
        DirectorSheet(state = state, t = t, onDismiss = { directorOpen = false })
    }
    // 历史提示词抽屉（常驻栏里的时钟图标）
    if (promptHistoryOpen) {
        PromptHistorySheet(
            state = state,
            t = t,
            field = activePromptField,
            onDismiss = { promptHistoryOpen = false },
        )
    }
    if (showPresetSheet) {
        StylePresetSheet(state = state, onDismiss = { showPresetSheet = false })
    }

    // **底部抽屉**（对话 / 日志）：Dialog 独立窗口，永远在最上层
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
    onOpenPresets: () -> Unit,
    activeField: String,
    onFieldFocus: (String) -> Unit,
    panelHeight: Dp,
    scrollConnection: NestedScrollConnection,
    /** 「导演台」开关状态与切换（2x放大 / 导演台那一行现在长在**抽屉里**，由外层持有）。 */
    directorOpen: Boolean,
    onToggleDirector: () -> Unit,
    /** 常驻按钮栏那颗「历史提示词」钟表（抽屉里也要用 ⇒ 由外层给）。 */
    onOpenHistory: () -> Unit = {},
) {
    // 面板级**左右滑动 = 切换 提示词/角色/参数**（用户要求）。
    // ⚠️ 挂在**整个面板**上（含把手与 Tab 栏），不只是内容区 —— 早先只挂在内容列上，
    // 在把手/Tab 那一带左右滑就没反应（"禁了但滑不动"）。
    // 只认横向（纵向留给把手拖动与列表滚动），位移小于 70px 不切栏（防误触）。
    // 面板级**左右滑动 = 切换 提示词/分镜**（用户 2026-09-26：
    // 「底部抽屉**除提示词和分镜外还能滑到原来的参数里**」—— 那是漏的，已堵上）。
    // ⚠️ 上限必须写 **`TAB_CHARACTERS`（1）**，不能写 `TAB_PARAMS`（2）✗：
    //    参数**早就从底部抽屉删掉了**（见下面那条注释），栏目条上只有两格、没有参数这一栏；
    //    但滑动这边一直还留着"最多到参数"的旧上限 ⇒ 从分镜再往左滑**能滑进一个栏目条上
    //    根本不存在的参数栏**（图中栏目条还停在分镜上，内容却整个换了）—— 就是这个 bug ✓。
    Surface(
        // ---- 毛玻璃下半：面板自己半透明 ----
        // `tonalElevation = 0`：色调抬升会给半透明底色再叠一层不透明色，
        // 把"透"这件事直接抵消掉，所以必须关掉。
        color = Color.Transparent,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().height(panelHeight),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 抽屉只有一份内容，不再预留单选项标题栏的高度。
            // ⚠️ 用户 2026-09-24：「**把 2x放大和导演台放到提示词框上面，跟随抽屉**」——
            //    它们原来钉在**底部固定组**（不跟着抽屉走 ✓），现在挪进抽屉里、放在
            //    **栏目条下面、提示词框上面** ✓ ⇒ 抽屉拉开时它们跟着一起上浮 ✓、收起来时一起收 ✓。
            ActionButtonsRow(
                state = state,
                t = t,
                directorOpen = directorOpen,
                onToggleDirector = onToggleDirector,
            )
            // 左右滑动切栏要有**滑动动画**（用户要求）：新栏从滑动的方向推进来。
            // 方向由新旧下标决定 —— 往左滑去下一栏（新栏从右边进），往右滑回上一栏（从左边进）。
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    // 用户要求"加快一点"：默认时长约 300ms，这里收到 170ms。
                    // ⚠️ `tween(...)` **不要写显式类型参数**：它有个带 TwoWayConverter 的重载，
                    // 写了 `<IntOffset>` 会让编译器选中那个，然后报一个看不懂的
                    // "actual type is 'Int', but 'Int' was expected"。靠参数类型推断即可。
                    (slideInHorizontally(animationSpec = tween(110)) { width -> dir * width } +
                        fadeIn(tween(110))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(110)) { width -> -dir * width } +
                            fadeOut(tween(110)))
                },
                label = "panelTab",
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { tab ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollConnection)
                        .verticalScroll(rememberScrollState())
                        // 网页 `.tabpane`：padding 10/12/12、间距 10
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    when (tab) {
                        TAB_PROMPTS, TAB_CHARACTERS -> {
                            PromptFields(
                                state = state,
                                t = t,
                                onOpenPresets = onOpenPresets,
                                activeField = activeField,
                                onFieldFocus = onFieldFocus,
                            )
                            if (state.canvasMode == 2) {
                                CharacterEditorPanel(state, Modifier.fillMaxWidth())
                            }
                        }
                        // ⚠️ 参数**不再画在这里**（用户 2026-09-25：从抽屉体系剥离）——
                        //    参数现在长在独立的 `ParamsSheet` 里（点击空白退出）。
                        //    这一档理论上进不来（栏目条只有两格 + 滑动上限是角色），
                        //    真进来了就画个空 —— 不要把 `ParamsFields` 加回来 ✗。
                        else -> Box(Modifier.fillMaxWidth())
                    }
                }
            }
            // ⚠️ 用户 2026-09-24：「生成图片上的对文本框工具**只有拉起抽屉时才拉起**」——
            //    这条栏现在长在**抽屉里面**（原来在抽屉外、常驻在底部）⇒ 抽屉收起时它跟着走。
            PromptActionBar(
                state = state,
                t = t,
                targetField = activeField,
                onOpenHistory = onOpenHistory,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 底部抽屉
// ---------------------------------------------------------------------------

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
    /** 常驻按钮栏那颗「历史提示词」钟表（抽屉里也要用 ⇒ 由外层给）。 */
    onOpenHistory: () -> Unit = {},
) {
    val path = state.workImagePath
    val dims = remember(path, state.maskVersion) {
        path?.let { runCatching { MaskCodec.imageSize(it) }.getOrNull() }
    }
    val price = dims?.let { OfficialUpscale.price(it.first, it.second) }
    val upscaleEnabled = path != null && price != null && !state.busy

    // ⚠️ 用户 2026-09-26：「深色模式下 2x 放大 / 导演台 按钮**没有颜色，加上**」，
    // 紧接着又说「**浅色模式还是没有底色**」——所以**两种模式都上底** ✓：
    // 可用 = 主色淡染（深色下浓一点、浅色下浅一点），不可用 = 高一档容器色。
    // 之前这两颗一律 `Color.Transparent`（指望外层大横条的底色）✗，看着就是"没按钮的样子"。
    // 判据用当前配色 surface 的亮度，而不是某个状态位 —— 切换主题 / 换配色都自动跟上 ✓。
    val directorEnabled = path != null && !state.busy
    // 网页 v6 `.act-row`：padding 10/12/0、间距 8；2x放大 / 导演台 同一种 = btn-secondary sm + 主题色图标
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RefBtn(
            text = t("upscale.button"),
            onClick = { state.officialUpscale() },
            kind = RefBtnKind.Secondary,
            small = true,
            enabled = upscaleEnabled,
            icon = RefResizeIcon,
            iconTint = LocalRef.current.accent,
            modifier = Modifier.weight(1f),
        )
        RefBtn(
            text = t("director.button"),
            onClick = onToggleDirector,
            kind = RefBtnKind.Secondary,
            small = true,
            enabled = directorEnabled,
            icon = RefMovieIcon,
            iconTint = LocalRef.current.accent,
            modifier = Modifier.weight(1f),
        )
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

    RefSheetTitle(title = t("director.title"))
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RefHint(t("director.hint"))

        // 6 个工具：两行三列的小方款，选中高亮
        DirectorTools.ALL.chunked(3).forEach { rowTools ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowTools.forEach { item ->
                    val selected = item.id == tool.id
                    // 网页 `.dir-tile`：field 底、描边、圆角 12、12.5 muted；on = selected / onSelected / selectedBorder 600
                    val ref = LocalRef.current
                    val tileShape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(tileShape)
                            .background(if (selected) ref.selected else ref.field)
                            .border(1.dp, if (selected) ref.selectedBorder else ref.border, tileShape)
                            .clickable { state.setSettings { it.copy(directorTool = item.id) } }
                            .padding(horizontal = 6.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            t("director.tool.${item.id}"),
                            fontSize = 12.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) ref.onSelected else ref.muted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
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
                modifier = Modifier.fillMaxWidth(),
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

        RefBtn(
            text = if (state.busy) t("generate.reading") else t("director.run"),
            onClick = { state.runDirectorTool() },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * 收起态那一行：一个小把手 + 提示词摘要 + 箭头（收起时 ▲、拉出后 ▼）。
 * 整行可点击切换面板展开状态。
 */
@Composable
private fun DrawerPeekRow(summary: String, expanded: Boolean, onClick: () -> Unit) {
    // 网页 `.peek`：padding 6/14/10、竖排；36×4 borderStrong@.8 把手；摘要 13 muted 单行省略；faint 箭头（开着转 180°）
    // 底色在外层 `.cpanel` 上，这里不再自己上色。
    val ref = LocalRef.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(ref.borderStrong.copy(alpha = 0.8f)),
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                summary,
                fontSize = 13.sp,
                color = ref.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // 收起 = 向上箭头、展开 = 转 180°（RefChevron 本身是向下，所以取反）
            RefChevron(expanded = !expanded, size = 16.dp)
        }
    }
}

// ---------------------------------------------------------------------------
// 预览
// ---------------------------------------------------------------------------

// ---- 预览的缩放 ----
// ⚠️ **图片位置是固定的**（用户 2026-09-16 否掉了画布模式）：不铺平移、双指也不拖动，
//    1 倍 = 按 `ContentScale.Fit` 正好铺满可视区，就是它一直待的地方。
//    唯一的手势是**双指捏合缩放**，而且**以双指之间的中点为锚点** —— 手指底下那块画面
//    不会跑，想看哪块就在哪块上捏。缩放回到「1 倍或更小」时偏移强制归零，也就是
//    "一缩回装得下的大小就回到原位"。
private const val PREVIEW_MIN_SCALE = 0.2f
private const val PREVIEW_MAX_SCALE = 6f

// 图片**背后**那层方格（用户要求保留：背景就是"画布"）。
// 边长固定按**屏幕**算、不跟着缩放走 —— 它只是"这是一块画布"的视觉暗示，跟着放大缩小反而晃眼。
private val PREVIEW_GRID_CELL = 18.dp
// 第二色的透明度。用 onSurface 而不是 surfaceContainer 系列：
// 深色主题上是提亮、浅色主题上是压暗，一个值两边都成立，
// 也不怕哪天主题里几个 surfaceContainer 被调成同一个颜色（格子会直接隐形）。
private const val PREVIEW_GRID_ALPHA = 0.06f

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
 * 预览。图片按真实宽高比居中（`ContentScale.Fit`）—— 这就是它**固定的位置**，
 * 和加画布之前完全一样（用户 2026-09-16 否掉了画布模式）。**背后保留那层方格底**
 * （用户要求：背景还是"画布"）。
 *
 * 唯一的手势是**双指捏合缩放**（0.2~6 倍），锚点在双指之间；单指完全不接，
 * 所以单指横滑照旧翻到图库。缩放回到 1 倍或更小时偏移归零 —— 图一装得下就回到原位。
 * **双击 = 归位**（回 1 倍 + 居中）：放大之后想立刻回到原样，用这个。
 *
 * 涂遮罩（`maskMode`）时缩放整套关掉并归位：那时手指是画笔，而笔迹坐标是按
 * "1 倍 + 居中"算出来的，带着缩放就走样了。
 *
 * 内容优先级与参考实现一致：**流式预览帧** → 当前图片文件 → 空占位。
 * "模糊 → 清晰"来自服务端逐帧推送的去噪中间稿本身，客户端不做模糊动画。
 */
/**
 * **画布模式切换**（用户 2026-09-22：「**在生图页的左上角，画布左上角加切换按钮：
 * 普通 / 无限画布 / 漫画**，先做，暂时不填充功能」✓）。
 *
 * ⚠️ 本批**只做界面** ✓：选中的模式只活在这个 `remember` 里 —— **不落盘、不改任何画布行为** ✗。
 *    三种模式各自要做什么，等用户点单再一项一项接 ✓（现在硬接一半反而会让人以为"点了没反应是坏了"✗）。
 */
@Composable
private fun CanvasArea(state: AppState, t: (String) -> String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (state.canvasMode) {
            // 1 = 无限画布（用户 2026-09-22 点的单 ✓，方案 `docs/69` / 落地 `docs/71`、`docs/72` ✓）
            1 -> {
                // 进这一档时才铺画布（幂等 ✓）：有图就拿它当底，没有就先给一张空白 ✓
                LaunchedEffect(state.canvasMode) { state.ensureInfiniteCanvas() }
                InfiniteCanvasArea(state = state, t = t, modifier = Modifier.fillMaxSize())
            }

            2 -> {
                val preview = state.generationPreview.takeIf { state.comicCanvasPreviewRunning }
                val generatedPath = state.comicCanvasImagePath()
                when {
                    preview != null -> Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    generatedPath != null -> FileImage(
                        generatedPath,
                        modifier = Modifier.fillMaxSize(),
                    )
                    state.comicCanvasPreviewRunning -> CircularProgressIndicator()
                    else -> StoryboardLayoutPreview(state, Modifier.fillMaxSize())
                }
            }

            else -> PreviewCard(state, t, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun PreviewCard(state: AppState, t: (String) -> String, modifier: Modifier = Modifier) {
    val preview = state.generationPreview
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

    // ---- 预览缩放 ----
    // 刻意不用 rememberSaveable：换图、进遮罩、重新生成都回到 1 倍，
    // 免得隔天打开发现图还放大在某处、还找不到原因。
    var previewScale by remember { mutableFloatStateOf(1f) }
    var previewOffset by remember { mutableStateOf(Offset.Zero) }
    // 双击归位的那段动画。**手指一碰就得把它掐掉**，否则动画和捏合会互相拽
    // （两边都在写同一个 state），所以拿一个 Job 记着，随时 cancel。
    val previewScope = rememberCoroutineScope()
    var previewResetJob by remember { mutableStateOf<Job?>(null) }
    // 换图 / 进遮罩 / 开新一轮生成 → 缩放归位。
    // 遮罩尤其要紧：笔迹坐标是按"1 倍 + 居中"算的，图放大了就涂不到手指底下。
    LaunchedEffect(path, maskMode, state.busy) {
        previewResetJob?.cancel()
        previewResetJob = null
        previewScale = 1f
        previewOffset = Offset.Zero
    }

    Box(modifier.onSizeChanged { areaState.value = it }, contentAlignment = Alignment.Center) {
        // 有没有图：没有就没什么可缩放的，也不接手势
        val hasImage = preview != null || path != null

        // 方格底：铺在图片**背后**。图片位置是固定的，方格只是个"画布"的视觉底
        // （用户要求保留；空态不铺，免得像出错了）。
        if (hasImage) {
            val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = PREVIEW_GRID_ALPHA)
            Canvas(Modifier.fillMaxSize()) {
                val cell = PREVIEW_GRID_CELL.toPx()
                if (cell <= 0f) return@Canvas
                var row = 0
                var y = 0f
                while (y < size.height) {
                    var col = 0
                    var x = 0f
                    while (x < size.width) {
                        // 只画"深色格"，另一半留白 —— 画一半就够出棋盘格了
                        if ((row + col) % 2 == 0) {
                            drawRect(
                                color = gridColor,
                                topLeft = Offset(x, y),
                                size = Size(cell, cell),
                            )
                        }
                        x += cell
                        col++
                    }
                    y += cell
                    row++
                }
            }
        }

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
                        bitmap = preview.asImageBitmap(),
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
            Text(
                t("generate.previewEmpty"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        // 遮罩叠加：**不在遮罩模式也画**（文生图界面同样要看得见自己涂过哪里）
        if (maskStrokes.isNotEmpty() && preview == null && sourceWidth > 0) {
            MaskStrokeOverlay(
                strokes = maskStrokes,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                area = areaState.value,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 聚焦重绘的框 + 框外那圈上下文提示（用户 2026-09-17）
        if (state.maskFocusActive && preview == null && sourceWidth > 0) {
            MaskFocusOverlay(
                state = state,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                area = areaState.value,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 直接在图片上涂：单指涂抹（涂上即生效，没有"确定"这一步）
        // 聚焦模式：单指 = 拖一个框（不再涂遮罩）；否则 = 涂遮罩
        if (maskMode && preview == null && path != null && state.maskFocusTool) {
            MaskFocusGestureLayer(
                state = state,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                area = areaState.value,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (maskMode && preview == null && path != null && !state.maskFocusTool) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val rect = fitRect(areaState.value, aspect)
                            val start = toMaskPoint(down.position, rect)
                            if (start == null) return@awaitEachGesture
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
                                val point = toMaskPoint(change.position, fitRect(areaState.value, aspect))
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
                            previewOffset = if (next <= 1f || previewScale <= 0f) {
                                // 缩回"装得下"的尺寸就**直接归位** —— 图一旦不需要挪动，
                                // 就回到它固定的位置，不留上一轮缩放攒下的偏移
                                Offset.Zero
                            } else {
                                // 以双指之间的中点为锚点：手指底下那块画面留在原地。
                                // 设中点相对可视区中心的向量 u，变换是
                                //   screen = center + scale * (content - center) + offset，
                                // 要让 u 处的内容不动，解出 offset' = u - (scale'/scale)(u - offset)。
                                val dx = centroid.x - area.width / 2f
                                val dy = centroid.y - area.height / 2f
                                val ratio = next / previewScale
                                Offset(
                                    x = dx - (dx - previewOffset.x) * ratio,
                                    y = dy - (dy - previewOffset.y) * ratio,
                                )
                            }
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
                    },
            )
        }

        // 工具条不再浮在图上（会挡住画面）；它由外层布局摆在图片**上面**那一行
        // 图生图模式：预览区右上角一个叉，退出图生图
        if (state.img2imgActive && preview == null) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                IconButton(onClick = { state.exitImg2Img() }) {
                    Icon(Icons.Filled.Close, contentDescription = t("generate.exitImg2Img"))
                }
            }
        }

        // ---- （2026-09-16 删掉）图片区右下角那颗**浮动铅笔** ----
        //
        // 这里原来有一颗 `Surface(CircleShape) + IconButton(onClick = { state.openPlotEditor() })`
        // 的浮动铅笔：那是**替代口径** —— 当时手机线还没有画布编辑器，所以临时接到"剧情放大页"上
        // （`state.plotEditorOpen`），并在注释里写明"手机线 AppState 里连 `canvasEditorOpen` 都没有"。
        //
        // 用户要的是**电脑线那颗真铅笔**（`state.openCanvasEditor()` = 官方 Canvas 那一套工具），
        // 现在画布编辑器已经整套搬到手机线了，替代口径没有存在的理由，整颗删除。
        // 剧情放大页的入口不受影响：角色分区编辑器里那颗「放大剧情框」还在
        // （`CharacterEditorPanel.kt` 的 `state.openPlotEditor()`）。
        //
        // 真正的铅笔在下面 RunBar 的右边那颗（原来是占位，现在接的就是 `state.openCanvasEditor()`）。

        if (state.busy && preview == null) {
            CircularProgressIndicator()
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
    // 「无限画布」那一档：**同一颗按钮**，语义换成"按框拓展" ✓（用户 2026-09-22 ✓）。
    // ⚠️ 这一档的"忙"看的是 `infiniteRunning` ✗ 不是全局 `busy` ✓ —— 用 `busy` 的话，
    //    忙的时候再点会走"排队"那条路，排的却是**文生图** ✓（见 `runInfiniteFrame` ✓）。
    val infinite = state.canvasMode == 1
    val busy = if (infinite) state.infiniteRunning else state.busy
    val label = when {
        infinite -> t("generate.runInfinite")
        state.canvasMode == 2 -> t("generate.runTextToImage")
        state.maskMode -> t("generate.runRedo")
        state.img2imgActive -> t("generate.runImg2Img")
        state.batchCount > 1 ->
            "${t("generate.runGenerateCountPrefix")}${state.batchCount}${t("generate.runGenerateCountSuffix")}"
        else -> t("generate.runTextToImage")
    }
    val enabled = state.hasToken &&
        // 有聚焦框时"没涂"也算能跑（框里可以留空 = 整框重绘）；无限画布那一档只看有没有框 ✓
        (busy || infinite || state.canvasMode == 2 ||
            !(state.maskMode && state.maskPaintedPercent == 0 && state.maskFocusRect == null))
    // 主按钮做成**圆角方形**（用户 2026-09-22：「把双端的生成图片按钮改成方形的，要有圆角」✓）——
    // 原来是**完全胶囊**（`NaiShape.Pill` = 50dp 圆角，两端是半圆 ✗）；
    // 现在用 `NaiShape.Field`（12dp 圆角 ✓）：**四条边是直的、只有四个角是圆的** ✓。
    val shape = NaiShape.Field
    val fraction = state.generationPreviewProgress.coerceIn(0f, 1f)
    val showSteps = state.generationPreviewTotalSteps > 0

    val ref = LocalRef.current
    val skin = ref.runSkin()
    // 网页 v6 `.runbtn` = 电脑线 MainRunButton 1:1：高 44、圆角 12、110° run-a → run-b 渐变、run-ink 字、
    // 光晕阴影 `0 3px 12px run-glow`；生成中 = 55% 主色 + accentContrast 字；禁用 = text 12% 底 + muted 字
    val fg = when {
        !enabled -> ref.muted
        busy -> ref.accentContrast
        else -> skin.runInk
    }
    Box(
        modifier
            .height(44.dp)
            .then(
                if (enabled && !busy) {
                    Modifier.shadow(8.dp, shape, ambientColor = skin.runGlow, spotColor = skin.runGlow)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .drawBehind {
                when {
                    !enabled -> drawRect(ref.text.copy(alpha = 0.12f))
                    busy -> drawRect(ref.accent.copy(alpha = 0.55f))
                    else -> drawRect(runGradient(skin, size))
                }
            }
            .combinedClickable(
                enabled = enabled,
                onClick = {
                    if (busy && !infinite) {
                        // 生成中再点 = 按当前参数排一张（无限画布那一档不排队：见上面的注释 ✓）
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
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            if (showSteps) {
                // 进度填充：按步数从左往右长（按钮看上去就是一条进度条）
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(Color.White.copy(alpha = 0.22f)),
                )
            } else {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                ) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth(),
                        color = Color.White.copy(alpha = 0.85f),
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                }
            }
        }
        // **积分预测**（用户 2026-09-24）：`null` 就什么都不画。
        // 网页 v6：「生成图片」14.5 / 600 + 数字 14.5 / 400（左距 6）+ 实心闪电 18（数字在左、闪电在右）
        val cost = state.runAnlasEstimate
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, maxLines = 1, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = fg)
            if (cost != null) {
                Spacer(Modifier.width(6.dp))
                Text(text = cost.toString(), maxLines = 1, fontSize = 14.5.sp, fontWeight = FontWeight.Normal, color = fg)
                Spacer(Modifier.width(3.dp))
                Icon(
                    imageVector = BoltIcon,
                    contentDescription = t("generate.anlasCost"),
                    tint = fg,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 运行条
// ---------------------------------------------------------------------------

/**
 * 运行条。左边 ⚙️ 直接打开参数 Tab，中间是主按钮，右边是**遮罩开关（铅笔）**和临时图库。
 *
 * 主按钮的语义由**遮罩模式**决定（用户定的口径）：
 *  · 铅笔关着 → 「生成图片」：按提示词生成新图（图上有旧遮罩也照样生成，遮罩只是显示着）
 *  · 铅笔画着 → 「重做」：用当前图当底图 + 图上涂的遮罩跑一次 infill（提示词还是正面提示词框）
 * 两者都从 `state.generateOrInpaint()` 进去，UI 只负责把当前语义显示出来。
 */
@Composable
private fun RunBar(
    state: AppState,
    t: (String) -> String,
    onOpenParams: () -> Unit,
    onToggleMask: () -> Unit,
    onImportImage: () -> Unit,
    /** 齿轮 = 设置页（用户 2026-09-25 要求放在「导入图片」和「生成图片」中间）。 */
) {
    Surface(
        // 底色画在**外层固定组的 Column** 上（和顶栏同一档，见那里的说明），
        // 这里必须透明 —— 再叠一层半透明的 surfaceContainer 会比顶栏深一截。
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        tint = if (state.maskMode) LocalRef.current.accent else LocalRef.current.muted,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            state.maskMode && state.maskPaintedPercent > 0 ->
                                t("mask.modeOnShortPainted").replace("{percent}", state.maskPaintedPercent.toString())
                            state.maskMode -> t("mask.modeOnShort")
                            else -> t("mask.existingShort").replace("{percent}", state.maskPaintedPercent.toString())
                        },
                        fontSize = 12.5.sp,
                        color = if (state.maskMode) LocalRef.current.accent else LocalRef.current.muted,
                        modifier = Modifier.weight(1f),
                    )
                    state.maskFocusRect?.let {
                        val framePx = state.maskFocusSize()
                        val plan = state.maskFocusPlan()
                        if (plan != null) {
                            Text(
                                t("mask.focusPlan")
                                    .replace("{w}", framePx.first.toString())
                                    .replace("{h}", framePx.second.toString())
                                    .replace("{rw}", plan.requestW.toString())
                                    .replace("{rh}", plan.requestH.toString()) +
                                    if (plan.inFreeTier) " · 免费档" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        // 清框已挪到顶部工具条（图标）
                    }
                    if (state.maskVisible) {
                        RefBtn(
                            text = t("mask.clearMask"),
                            onClick = { state.clearMask() },
                            kind = RefBtnKind.Ghost,
                            small = true,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                // ⚠️ 用户 2026-09-25：「**底栏生成图片那一栏的图标紧凑一些**」——
                //    间距 8 → **2**，四颗 `IconButton` 也各自收到 **38dp / 图标 21dp**
                //    （M3 默认 48dp 见方，四颗排下来要吃掉近 200dp，把「生成图片」挤得很窄）。
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // 这里是**导入图片**（原来是打开参数面板的齿轮；参数面板从摘要行打开）
                // ⚠️ 用户 2026-09-26：「**在生成图片两旁的按钮下加功能名字**」——
                //    四颗都从"只有图标"改成 `RunBarAction`（图标 + 底下一行小字）。
                RunBarAction(
                    icon = PictureIcon,
                    label = t("runbar.import"),
                    description = t("generate.importImage"),
                    enabled = !state.busy,
                    onClick = { onImportImage() },
                )
                // ⚠️ 2026-09-25 更正：用户说的是「从底部抽屉删除**参数**」（不是设置）——
                //    所以这颗接的是**参数面板**（`onOpenParams`，和摘要行那条入口同一个动作），
                //    不是设置页。设置**照旧在侧边抽屉的导航里**，没动。
                //    ⚠️ 历史：这一格**原来就是"打开参数面板的齿轮"**，后来被「导入图片」顶掉了
                //    （见上面那行注释）；现在用户要参数回到运行条上，所以又回来了，
                //    并且底部抽屉的栏目条里**不再列参数**（见 `ControlPanel` 的说明）。
                RunBarAction(
                    // ⚠️ 用户 2026-09-25：「参数的图标**变为三条杠，上面有小点，含义滑条**」——
                    //    自绘的 `SlidersIcon`（见 `AppIcons.kt`），不是齿轮、也不是 `Icons.Tune`。
                    icon = SlidersIcon,
                    // 这颗的短名不用新开 key —— `generate.params` 本来就是"参数"两个字。
                    label = t("generate.params"),
                    description = t("generate.params"),
                    onClick = { onOpenParams() },
                )
                MainRunButton(state = state, t = t, modifier = Modifier.weight(1f))

                // ---- 右侧两颗（用户 2026-09-16：都放右边；临时图库去掉腾位置）----
                // 1) 铅笔 = **画布编辑器**（官方 Canvas 那一套工具：画笔/橡皮/油漆桶/选择/套索/
                //    吸管/模糊/图章 + 调整画布 + 撤销重做）。改的是**图本身**，点「完成」当底图用。
                //    它和右边那颗「遮罩」是两件事：遮罩只管"重绘哪一块"，图不动。
                //    没有图时也能点 —— 开一张空白画布（官方叫 Paint New Image）。
                //
                //    ⚠️ 这颗**原来是个占位**（点了只弹 `generate.placeholderSoon`「还没做」）；
                //    用户 2026-09-16 点名的就是"真铅笔"，所以这里接的正是电脑线那颗的同槽位实现
                //    （电脑线 `naistudio-shared/screens/GenerateScreen.kt:2433-2446`）：
                //    onClick = openCanvasEditor()、contentDescription = `canvas.title`、
                //    enabled = !busy、选中态用 primary。文案/注释与电脑线一一对应。
                RunBarAction(
                    icon = RefEditIcon,
                    label = t("runbar.edit"),
                    description = t("canvas.title"),
                    enabled = !state.busy,
                    on = state.canvasEditorOpen,
                    onClick = { state.openCanvasEditor() },
                )
                // 2) 框选（方框虚线 + 指针）= 遮罩开关：点一下进遮罩模式，再点一下回文生图。
                //    判据用 workImagePath —— 和 toggleMaskMode() 内部依据的对象保持一致。
                RunBarAction(
                    icon = MaskSelectIcon,
                    label = t("runbar.mask"),
                    description = t("mask.toggle"),
                    enabled = !state.busy,
                    on = state.maskMode,
                    onClick = onToggleMask,
                )
            }

            // 底部那行字幕 = **抽屉入口**（2026-09-16）：点它弹出「对话 / 日志」
            StatusBarLine(
                state = state,
                t = t,
                onOpen = { state.openStatusDrawer() },
            )
        }
    }
}

/**
 * 运行条上的一颗**带名字的小按钮**（用户 2026-09-26：「在生成图片两旁的按钮下加功能名字」）。
 *
 * 图标下面挂一行小字（导入 / 参数 / 编辑 / 遮罩）。原来四颗只有图标 ——
 * 「图片」还看得出是导入，「铅笔」勉强算编辑，可**参数（三条杠+小点）和遮罩（虚线框+指针）
 * 都是自己画的**，不配字谁也猜不出来。
 *
 * ## 尺寸怎么定的
 *
 *  · 宽 **44dp**：够放 `labelSmall` 的两个汉字（≈24dp），四颗 + 3 个 2dp 间距 = 182dp，
 *    360dp 屏上「生成图片」还剩 ~160dp（比原来的 38dp 版只少 24dp）✓；
 *  · 高 **44dp**：图标 20dp + 1dp + 一行字 ≈ 16dp = 37dp，居中放得下 ✓。
 *    比主按钮（40dp）高 4dp，整条运行条跟着高 4dp —— 固定组高度是**实测**的
 *    （`fixedGroupHeight`），键盘那套让位跟着自适应，不用另外改数。
 *  · 禁用态：`IconButton` 那层"变灰"是 M3 自己做的，自绘的 Column 得自己来 ——
 *    按 M3 的规矩把不透明度压到 **0.38**（`DisabledAlpha`，见材料规范）。
 *
 * @param icon 图标（自绘的和材料图标都行）
 * @param label 图标底下那行**短**字（两个汉字最稳，长了会挤出格子）
 * @param description 无障碍读屏用的**全称**（和短名分开：读屏该念"导入图片"而不是"导入"）
 */
@Composable
private fun RunBarAction(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    on: Boolean = false,
) {
    val ref = LocalRef.current
    val c = if (on) ref.accent else ref.muted
    Column(
        Modifier
            .size(width = 48.dp, height = 50.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) ref.accentSoft else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = c,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            color = c,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

// ---------------------------------------------------------------------------
// 提示词 / 参数内容（两处复用：底部抽屉的 Tab 与宽屏右栏）
// ---------------------------------------------------------------------------
@Composable
// ⚠️ 角色抽屉用的是 `ModalBottomSheet`（实验 API）⇒ 这个 composable 要 OptIn
@OptIn(ExperimentalMaterial3Api::class)
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

    /** 谁被点进去，谁就是底部那条常驻按钮栏的作用对象（用户 2026-09-16 要求）。 */
    fun focusHandler(field: String, setLocal: (Boolean) -> Unit): (Boolean) -> Unit = { focused ->
        setLocal(focused)
        if (focused) onFieldFocus(field)
        // 底栏垃圾桶靠这个知道"现在清哪个框"（用户 2026-09-16 报的 bug：
        // 原来只有提示词有身份，垃圾桶在角色 / 漫画的框上按下去没反应）
        state.onTextFieldFocus(TextFieldTarget.Prompt(field), focused)
    }

    // ---- 风格提示词 + 正面提示词：**合成一个框，中间一条横线**（用户 2026-09-16 要求）----
    // 两个字段各存各的（`stylePrompt` / `positivePrompt`），只是画在同一个描边里 ——
    // 合成之后样式和内容一眼能对上，也不用再纠结"风格词框该给几行"。
    // 「选择预设」跟着风格词走，所以放进**风格那一段的表头**（就是它的右上角）。
    var styleFocused by remember { mutableStateOf(false) }
    var positiveFocused by remember { mutableStateOf(false) }
    val styleSelected = activeField == PromptField.STYLE
    val positiveSelected = activeField == PromptField.POSITIVE
    /**
     * **现在看的是正面还是负面**（用户 2026-09-24：「提示词栏变为**切换式**负面提示词和正面提示词，
     * 风格提示词上面加一个胶囊开关…点击**切换**」✓）。
     *
     * ⚠️ 这是**纯界面状态** ✓（`remember` ✓、不落盘 ✓）——
     * 它只决定"那一个框里画谁" ✓，两个字段**各存各的**（`positivePrompt` / `negativePrompt` ✓），
     * 切换**不搬运、不转换、不拼接** ✗（照 `CharacterComicModeSwitch` 那条口径 ✓）。
     * ⚠️ 默认**正面** ✓（打开抽屉先看到正面词 ✓，那是主用法 ✓）。
     */
    var promptSidePositive by remember { mutableStateOf(true) }

    /**
     * **角色抽屉开着没有**（用户 2026-09-25：「角色功能移到原本角色图鉴的地方，
     * **点击弹出抽屉**，里面 ui 不变」）。
     *
     * ⚠️ 纯界面状态（`remember`、不落盘）✓。
     * ⚠️ 抽屉一开就要把全局的 `comicMode` 摆成 **false** —— 因为
     *    `CharacterEditorPanel` 是按**全局** `comicMode` 决定"画角色还是画分镜"的 ✓，
     *    而这一格专管**角色** ✓（分镜那一份在底部抽屉的「分镜」栏里 ✓）。
     */
    var characterDrawerOpen by remember { mutableStateOf(false) }
    // 网页 `.tabpane`：`.seg`（正面 / 负面）在最上面、pbox 外面；
    // 正面 = 一个 `.pbox`（风格 + 分隔线 + 正面），下面一排 `.chip.box 角色分区` + `.fchip 透明背景`；
    // 负面 = 一个 `.pbox.neg`（负面词单独一框，风格词不画 —— 用户 2026-09-25）。
    // 两个字段各存各的，切换不搬运 ✓（用户 2026-09-24）。
    PromptPolaritySwitch(
        positive = promptSidePositive,
        onSelect = { positive -> promptSidePositive = positive },
        t = t,
    )
    if (promptSidePositive) {
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
                            fontSize = 11.5.sp,
                            color = LocalRef.current.accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    RefChip(text = t("preset.select"), onClick = onOpenPresets)
                },
            )
            // 高度不设上限（写多少行就长多高）；下限 = 网页 `.ptext.style` 的 44
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)) {
                PromptTextField(
                    value = params.stylePrompt,
                    onValueChange = { value -> state.setParam { it.copy(stylePrompt = value) } },
                    readOnly = lockStyle,
                    placeholder = t("generate.stylePromptHint"),
                    onFocusChange = focusHandler(PromptField.STYLE) { styleFocused = it },
                    highlight = true,
                    state = state,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(LocalRef.current.border))
            PromptSectionHeader(
                label = t("generate.positivePrompt"),
                locked = lockPositive,
                onToggleLock = { state.setPromptLock(PromptField.POSITIVE, !lockPositive) },
                t = t,
                selected = positiveSelected,
            )
            // 生成期间**允许继续编辑**（用户要求 2026-09-15）：在飞的请求用的是发起时的参数快照。
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 88.dp)) {
                PromptTextField(
                    value = params.positivePrompt,
                    onValueChange = { text ->
                        state.setParam { it.copy(positivePrompt = text) }
                    },
                    readOnly = lockPositive,
                    placeholder = "1girl, solo, blue hair, white dress, garden, sunlight, smile",
                    onFocusChange = focusHandler(PromptField.POSITIVE) { positiveFocused = it },
                    highlight = true,
                    // 词典候选**只给这一框开** ✓
                    state = state,
                )
            }
        }
        // 普通画布的角色分区入口在提示词下方；漫画画布直接编辑分镜。
        if (state.canvasMode != 2) Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 网页 v6：同一种选项卡片并排两格 —— 角色分区（数量徽章 + 箭头）/ 透明背景（开关）
            RefOptionCard(
                text = t("char.title"),
                icon = RefUsersIcon,
                onClick = { characterDrawerOpen = true },
                modifier = Modifier.weight(1f),
            ) {
                val n = state.extras.activeItems.size
                // 箭头去掉：两格并排时它挤掉了「角色分区」四个字（用户 2026-09-29）
                RefCountBadge(n)
            }
            if (params.isV5) {
                val transparentOn = params.transparentBackground
                RefOptionCard(
                    text = t("generate.transparent"),
                    icon = RefDropIcon,
                    selected = transparentOn,
                    onClick = { state.setParam { it.copy(transparentBackground = !transparentOn) } },
                    modifier = Modifier.weight(1f),
                ) {
                    RefSwitch(
                        checked = transparentOn,
                        onCheckedChange = { v -> state.setParam { it.copy(transparentBackground = v) } },
                        modifier = Modifier.scale(0.86f),
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    } else {
        // ---- 负面提示词（同一格位置，只是换了内容 ✓）----
        var negativeFocused by remember { mutableStateOf(false) }
        PromptBox(focused = negativeFocused, negative = true) {
            PromptSectionHeader(
                label = t("generate.negativePrompt"),
                locked = lockNegative,
                onToggleLock = { state.setPromptLock(PromptField.NEGATIVE, !lockNegative) },
                t = t,
                selected = activeField == PromptField.NEGATIVE,
            )
            Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = 88.dp)) {
                PromptTextField(
                    value = params.negativePrompt,
                    onValueChange = { text ->
                        state.setParam { it.copy(negativePrompt = text) }
                    },
                    readOnly = lockNegative,
                    placeholder = "",
                    onFocusChange = focusHandler(PromptField.NEGATIVE) { negativeFocused = it },
                    state = state,
                )
            }
        }
    }
    if (state.canvasMode == 2) {
        val prompt = params.positivePrompt
        Button(
            onClick = {
                if (state.extras.comicBerserk) state.generateBerserkComic(prompt)
                else state.generateComicStoryboard(prompt)
            },
            enabled = prompt.isNotBlank() && !state.storyboardBusy && !state.berserkBusy,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) { Text(t("comic.storyboardRun")) }
    }

    // ---- **角色抽屉**（用户 2026-09-25：「角色功能移到原本角色图鉴的地方，点击弹出抽屉，
    //      里面 ui 不变，依旧可以使用角色图鉴快捷导入角色，添加角色，编辑角色位置等」）----
    //
    // ⚠️ 里面就是**原来那套**：`AnimaButton`（图鉴快捷导入）+ `CharacterEditorPanel`
    //    （添加 / 编辑名字 / 上下移 / 位置网格 / 角色图鉴 …全在里面）—— 一个控件都没换，
    //    只是外面从"底部抽屉的角色栏"换成了这个弹出抽屉。
    // ⚠️ 一打开就把全局 `comicMode` 摆成 **false**：`CharacterEditorPanel` 是按它决定
    //    "画角色还是画分镜"的，而这一格专管角色（分镜那一份在底部抽屉的「角色」栏里）。
    //    关掉之后不用还原：用户去点那一栏时，那边会自己摆回 true（见那一段的说明）。
    if (characterDrawerOpen) {
        val characterSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        LaunchedEffect(Unit) { if (state.extras.comicMode) state.setComicMode(false) }
        ModalBottomSheet(
            onDismissRequest = { characterDrawerOpen = false },
            sheetState = characterSheet,
        ) {
            RefSheetTitle(title = t("char.title"), onClose = { characterDrawerOpen = false })
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnimaButton(t = t, onClick = { state.openAnimaSheetForCharacters() })
                    RefHint(t("anima.charHint"))
                }
                CharacterEditorPanel(state, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * **「正面 / 负面」长条胶囊开关**（用户 2026-09-24：
 * 「风格提示词上面加一个胶囊开关，**长条胶囊**，里面写着**正面，负面**，点击切换。
 *  开关样式…像是**角色与分镜里角色模式和漫画模式切换按钮**，切换时**由正面的蓝色按钮转换为红色**」✓）。
 *
 * ## 样式照谁
 *
 * 照 [SegmentedCapsule]（`ui/Widgets.kt` ✓ —— 「角色模式 / 漫画模式」用的就是它 ✓）：
 * **外侧长条胶囊 + 每格各自一个小胶囊 + 选中那格填色** ✓（圆角 50 = 两端半圆 ✓，就是"长条胶囊" ✓）。
 *
 * ## 唯一和它不一样的地方：颜色
 *
 * [SegmentedCapsule] 选中一律用主题 `primary`（蓝 ✓）；这里按**极性**分色 ✓：
 *  · **正面 = 蓝** ✓（主色 ✓）—— 和原来那颗按钮一个色，看着没变 ✓；
 *  · **负面 = 红** ✓（用户点名"**由正面的蓝色按钮转换为红色**" ✓）。
 *
 * ⚠️ 红色**写死** ✗ 不跟主题走 ✓：它是"负面"这个**语义**的颜色 ✓（和主色那种"品牌色"不是一回事 ✓），
 * 主题换来换去它都该是红的 ✓。两种深浅各取一档 ✓（暗底上要亮一点才看得清 ✓）。
 */
@Composable
private fun PromptPolaritySwitch(
    positive: Boolean,
    onSelect: (Boolean) -> Unit,
    t: (String) -> String,
    modifier: Modifier = Modifier,
) {
    RefSeg(
        options = listOf(t("generate.positivePrompt"), t("generate.negativePrompt")),
        selected = if (positive) 0 else 1,
        onSelect = { onSelect(it == 0) },
        negIndex = 1,
        modifier = modifier,
    )
}

/**
 * **剧情放大编辑页**：把整个抽屉换成一个铺满的大文本框。
 *
 * ## 为什么要有它
 *
 * 漫画模式那个剧情框只有三行高（它得和右边的版式画布并排放，占太多会把画布挤没），
 * 而狂暴模式是要往里面贴**整部**剧情的 —— 几百上千字塞在三行里，既看不全也没法校对。
 * 点框右上角那个「四角」图标就把抽屉整块让给它（用户 2026-09-16 要求）。
 *
 * ## 几个约定
 *
 *  · 高度沿用抽屉面板那一套（`panelHeight`），所以"放大" = 铺满抽屉而不是铺满屏幕；
 *  · 与 `DrawerPeekRow` 是兄弟节点，把手行仍在上面 —— 还能下拉把它收回去；
 *  · 编辑直接写 `state.comicPlot`，走的是那个带防抖的 setter，
 *    所以这里改完关掉、甚至直接杀掉 App 都不会丢；
 *  · 右上角用 `common.close` 的叉，不另造一个"缩小"图标：进出的入口不必长成一对。
 */
@Composable
private fun PlotEditorPanel(
    state: AppState,
    t: (String) -> String,
    onClose: () -> Unit,
    panelHeight: Dp,
) {
    Surface(
        // 底色在外层 `.cpanel` 上
        color = Color.Transparent,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().height(panelHeight),
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
                    .onFocusChanged {
                        state.onTextFieldFocus(TextFieldTarget.Plot, it.isFocused)
                    },
            )
        }
    }
}

/**
 * **常驻提示词按钮栏**：垃圾桶 · 撤销 · 重做 · 历史 · 翻译 · 优化。
 *
 * ## 为什么从提示词框里搬出来
 *
 * 这些按钮原来画在正面提示词框**内部**最后一行。风格词 / 正面词一写长（几百字标签串很常见），
 * 那一行就被顶到很下面，还得先滚动抽屉才按得到 —— 用户 2026-09-16 反馈的就是这个。
 * 现在挂在底部固定区、**「导演台」那条的上面**，不参与任何滚动，永远在手边。
 *
 * ⚠️ 「角色图鉴」**不在这儿**：用户要求它留在正面提示词框的左下角（见 [PromptFields]），
 * 所以这条栏的第一个按钮就是垃圾桶。
 *
 * ## 「按当前选中的框用功能」
 *
 * 五个功能全都跟着 [targetField] 走（连撤销栈也按框分开存，见 `AppState.promptUndo`），
 * 所以垃圾桶右边紧接着一行「作用于 X」—— 否则在「角色」栏里按撤销，会不知道撤的是哪个框。
 * 这行字**贴在垃圾桶旁边**而不是单独占一行横幅（用户要求：横幅删掉，太占高度）。
 */
@Composable
private fun PromptActionBar(
    state: AppState,
    t: (String) -> String,
    targetField: String,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // ⚠️ 这里必须用 `stackKey` 而不是 `orDefault`：后者把剧情（PLOT）折成正面提示词 ✗ ——
    // 表现就是"标签写着作用于剧情，按下去改的却是提示词"（用户 2026-09-18 报的正是这个）。
    val field = PromptField.stackKey(targetField)
    // 「作用于 X」跟着**当前聚焦的文本框**走：提示词那三条照旧用 targetField（表头也靠它高亮），
    // 角色 / 漫画模式的框则报自己的名字 —— 否则会出现"写着作用于正面提示词，按下去清的是角色框"。
    val targetLabel = when (val target = state.textFieldTarget) {
        is TextFieldTarget.CharacterPrompt -> t(
            if (target.comic) "prompt.targetPanelPrompt" else "prompt.targetCharPrompt",
        ).replace("{index}", (target.index + 1).toString())

        is TextFieldTarget.CharacterNegative -> t(
            if (target.comic) "prompt.targetPanelNegative" else "prompt.targetCharNegative",
        ).replace("{index}", (target.index + 1).toString())

        TextFieldTarget.Plot -> t("prompt.targetPlot")
        TextFieldTarget.ComicStyle -> t("prompt.targetComicStyle")
        // ⚠️ `Prompt` 现在带 field ⇒ 标签直接显示**那一条**的名字（风格/正面/负面），
        // 不再是笼统的"提示词"（用户 2026-09-24：垃圾桶要认得出站在哪个框）。
        is TextFieldTarget.Prompt -> t(promptFieldNameKey(target.field))
        else -> t(promptFieldNameKey(field))
    }
    val pabBorder = LocalRef.current.border
    Row(
        // 常驻按钮栏也走顶栏那一档底色（用户 2026-09-16 要求"工具条和顶部一样"）：
        // 它收起态就贴在 2x放大那行上面，两块颜色必须接得上。
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(pabBorder, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // 垃圾桶：清**当前聚焦的那个文本框**（提示词那次清三条，角色/漫画各清各的）。
        // 锁着的提示词清不掉 —— 由 `AppState.params` 的写入口统一兜底。
        IconButton(
            onClick = { state.clearFocusedTextField() },
            modifier = Modifier.size(34.dp),
        ) {
            Icon(
                RefTrashIcon,
                contentDescription = "${t("common.delete")}$targetLabel",
                modifier = Modifier.size(16.dp),
            )
        }
        // 「作用于 X」紧挨着垃圾桶（用户要求放这儿，删掉原来那条单独一行的横幅）
        Text(
            "${t("prompt.targetPrefix")}$targetLabel",
            fontSize = 12.sp,
            color = LocalRef.current.faint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        // ---- 右：撤销 / 重做 / 历史 / 翻译 / 优化（全部按选中的框） ----
        // 没得撤销/重做时置灰，免得点了没反应还以为是 bug
        IconButton(
            onClick = { state.undoPrompt(field) },
            enabled = state.canUndoPrompt(field),
            modifier = Modifier.size(34.dp),
        ) {
            Icon(UndoIcon, contentDescription = t("generate.undoPrompt"), modifier = Modifier.size(17.dp))
        }
        IconButton(
            onClick = { state.redoPrompt(field) },
            enabled = state.canRedoPrompt(field),
            modifier = Modifier.size(34.dp),
        ) {
            Icon(RedoIcon, contentDescription = t("generate.redoPrompt"), modifier = Modifier.size(17.dp))
        }
        IconButton(
            onClick = onOpenHistory,
            modifier = Modifier.size(34.dp),
        ) {
            Icon(RefHistoryIcon, contentDescription = t("llm.history"), modifier = Modifier.size(17.dp))
        }
        if (state.llmBusy) {
            Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = LocalRef.current.accent,
                )
            }
        } else {
            IconButton(
                onClick = { state.translatePrompt(field) },
                modifier = Modifier.size(34.dp),
            ) {
                TranslateGlyph(label = t("llm.translate"))
            }
            IconButton(
                onClick = { state.optimizePrompt(field) },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(SparkleIcon, contentDescription = t("llm.optimize"), modifier = Modifier.size(17.dp))
            }
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
private fun AnimaButton(t: (String) -> String, onClick: () -> Unit) {
    RefBtn(
        text = t("anima.title"),
        onClick = onClick,
        kind = RefBtnKind.Tint,
        small = true,
        icon = RefUsersIcon,
    )
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
 * 所以这里手画边框，但**圆角 / 描边宽度 / 聚焦配色全部对齐 `OutlinedTextFieldDefaults`**，
 * 于是和别的输入框看起来仍是一套。
 */
@Composable
private fun PromptBox(
    focused: Boolean,
    modifier: Modifier = Modifier,
    negative: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 网页 `.pbox`：描边、圆角 12、padding 10/12、间距 8、field 底；
    // 聚焦 = accent 描边 + 1px 内描边（≈ 2dp），负面框聚焦用 negative
    val ref = LocalRef.current
    val shape = RoundedCornerShape(12.dp)
    val focusColor = if (negative) ref.negative else ref.accent
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ref.field)
            .border(if (focused) 2.dp else 1.dp, if (focused) focusColor else ref.border, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
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
    val ref = LocalRef.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            // 选中的那一段上主色：底下那条常驻按钮栏按的是哪个框，这里一眼能对上
            color = if (selected) ref.accent else ref.muted,
        )
        IconButton(
            onClick = onToggleLock,
            modifier = Modifier.size(26.dp),
        ) {
            Icon(
                imageVector = if (locked) LockClosedIcon else LockOpenIcon,
                contentDescription = t(if (locked) "prompt.unlock" else "prompt.lock"),
                tint = if (locked) ref.accent else ref.faint,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
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
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 21.sp),
    highlight: Boolean = false,
    state: AppState,
) {
    val color = if (readOnly) LocalRef.current.muted else LocalRef.current.text
    var focused by remember { mutableStateOf(false) }
    // 光标位置（要拿它把"正在打的那一个词"截出来 ✓）

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

    // Keep the selection and IME composition locally; app state still stores plain text.
    var editingValue by remember { mutableStateOf(TextFieldValue(value)) }
    val fieldValue = editingValue.copy(text = value)
    SideEffect {
        if (editingValue != fieldValue) editingValue = fieldValue
    }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val cursorRequester = remember { BringIntoViewRequester() }
    val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)

    // Attach outside BasicTextField's internal scroll node so the drawer receives the request.
    Box(Modifier.fillMaxWidth().bringIntoViewRequester(cursorRequester)) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { updated ->
                editingValue = updated
                if (updated.text != value) onValueChange(updated.text)
            },
            readOnly = readOnly,
            textStyle = textStyle.copy(color = color),
            cursorBrush = SolidColor(LocalRef.current.accent),
            visualTransformation = if (highlight) {
                rememberPromptHighlight()
            } else {
                VisualTransformation.None
            },
            onTextLayout = { textLayout = it },
            modifier = Modifier
                .fillMaxWidth()
                .followFingerForSelection()
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChange(it.isFocused)
                },
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty() && placeholder.isNotEmpty() && !readOnly) {
                        Text(placeholder, style = textStyle, color = LocalRef.current.faint)
                    }
                    innerTextField()
                }
            },
        )
    }

    // Follow the active selection endpoint, including edits in the middle and keyboard resizing.
    // Highlighting only changes spans and uses identity offset mapping.
    LaunchedEffect(focused, fieldValue, textLayout, imeBottomPx) {
        if (!focused || readOnly) return@LaunchedEffect
        withFrameNanos { }
        val layout = textLayout ?: return@LaunchedEffect
        if (layout.layoutInput.text.text != fieldValue.text) return@LaunchedEffect
        val offset = fieldValue.selection.end.coerceIn(0, layout.layoutInput.text.length)
        cursorRequester.bringIntoView(layout.getCursorRect(offset))
    }

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
        // 去掉 includeFontPadding：不然中文的字体上行距会把这句话在图标框里整体压低
        style = LocalTextStyle.current.copy(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
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
        RefSheetTitle(title = t("llm.history"), onClose = onDismiss)
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RefHint(t("llm.historyHint"))
            if (prompts.isEmpty()) {
                Text(
                    t("llm.historyEmpty"),
                    fontSize = 13.5.sp,
                    color = LocalRef.current.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp).padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(prompts, key = { it }) { prompt ->
                        Text(
                            prompt,
                            fontSize = 13.sp,
                            lineHeight = 19.5.sp,
                            color = LocalRef.current.text,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(LocalRef.current.field)
                                .border(1.dp, LocalRef.current.border, RoundedCornerShape(12.dp))
                                .clickable {
                                    // 用这一条 → 落进**当前选中的那个框**（历史抽屉在常驻栏的时钟里打开）
                                    state.setPromptField(field, prompt)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        )
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
    // ⚠️ 「透明背景」那条开关**搬走了** ✗（用户 2026-09-25：「把透明背景按钮放到角色图鉴右边」）——
    //    现在长在**正面提示词框下面那一行**（`PromptFields` 里，角色按钮右边）✓。
    //    这里**不要再加回来** ✗：同一个开关两处会互相打架 ✓。
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
    OutlinedTextField(
        value = params.fileNamePrefix,
        onValueChange = { value -> state.setParam { it.copy(fileNamePrefix = value) } },
        label = { Text(t("generate.fileNamePrefix")) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
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
                fontSize = 12.5.sp,
                color = LocalRef.current.faint,
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
