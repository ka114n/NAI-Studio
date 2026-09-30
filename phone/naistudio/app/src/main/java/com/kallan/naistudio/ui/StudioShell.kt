package com.kallan.naistudio.ui

import androidx.compose.ui.draw.shadow
import com.kallan.naistudio.ui.BoltIcon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import com.kallan.naistudio.ui.RefModalBottomSheet as ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.kallan.naistudio.ui.RefAlertDialog as AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.kallan.naistudio.ui.RefIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.kallan.naistudio.ui.RefTextButton as TextButton
import com.kallan.naistudio.ui.RefTopAppBar as TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kallan.naistudio.BuildConfig
import com.kallan.naistudio.R
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.HistoryItem
import com.kallan.naistudio.models.ToolCatalog
import com.kallan.naistudio.models.ToolEntry
import com.kallan.naistudio.models.ToolSurface
import com.kallan.naistudio.screens.CanvasEditorScreen
import com.kallan.naistudio.screens.GalleryScreen
import com.kallan.naistudio.screens.GenerateScreen
import com.kallan.naistudio.screens.ProfileScreen
import com.kallan.naistudio.screens.SettingsScreen
import com.kallan.naistudio.screens.ToolsScreen
import com.kallan.naistudio.screens.UsageStatsScreen
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.FolderIcon
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * 应用外壳。
 *
 * **导航只有左侧边栏（抽屉）一种形态**：原来的底部导航栏已删除，它的 4 项搬进抽屉，
 * 抽屉里**只有这 4 项**，不再放别的功能；宽度是手机屏幕的一半。
 *
 * **生成 ↔ 图库 是一个 `HorizontalPager`**：左右拖动完全跟手、松手自动吸附，连续来回滑也顺。
 * 图库页屏蔽「左滑开侧边栏」，把左缘让给「滑回文生图」；工具 / 设置仍由抽屉切换。
 *
 * 顶部栏左上角是汉堡按钮；右上角在生成页显示账号状态，**在图库页换成「排序与显示」按钮**
 * （图库页不显示余额）。
 *
 * 角色分区编辑器在生成页的底部抽屉里；风格预设仍在「选择预设」里。
 */
private const val GALLERY_INDEX = 1

/** 工具页在 pager 里的下标（侧边栏「工具」下拉里的工具名要翻到这一页）。 */
private const val TOOLS_INDEX = 2

/** 抽屉：横向甩动超过这个速度（px/s）就直接开/关，不看拖了多远。 */
private const val FLING_VX = 700f

/** 两次返回退出的时间窗（毫秒）。 */
private const val EXIT_INTERVAL_MS = 2000L

/** 从任意 Context 往上找宿主 Activity（两次退出要调 finish）。 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** 抽屉：速度不够时，拉出这个比例（0.2 = 20% 宽度 ≈ 144px）也认。 */
private const val DRAG_TO_OPEN = 0.2f

/** 侧边栏宽度：手机屏幕的 66%（同网页原型；上限 280dp，免得平板上横跨半个屏）。 */
@Composable
private fun drawerWidth(): Dp {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    return minOf(screenWidthDp * 0.66f, 280.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioShell(state: AppState) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val language = state.settings.language
    val t: (String) -> String = { key -> RuntimeText.text(language, key) }

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    /** 「统计」那扇窗开着没（侧边栏底部那颗柱状图图标，用户 2026-09-26）。 */
    var statsOpen by rememberSaveable { mutableStateOf(false) }
    var galleryControls by rememberSaveable { mutableStateOf(false) }
    // 底部面板正在用（打开或正在拉）时，屏蔽侧边栏手势
    var bottomPanelActive by remember { mutableStateOf(false) }
    // 生成页在遮罩模式下会置位：手指属于遮罩画布，翻页手势得让位
    var contentGestureLocked by remember { mutableStateOf(false) }
    // 图库页：当前看的那张 + 批量删除模式
    var gallerySelection by remember { mutableStateOf<HistoryItem?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmingBatchDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 两次退出用的状态 + 宿主 Activity（第一次返回只在底部弹提示）
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var lastBackPress by remember { mutableLongStateOf(0L) }

    /**
     * 「深/浅色」那颗按钮的中心（**窗口坐标**）——主题圆形扩散动画的圆心。
     *
     * 声明在这一层而不是按钮自己那儿：按钮画在侧边栏内容里，而遮罩画在 `MainActivity`
     * 最外层，两边不在同一个组合子树里。`onGloballyPositioned` 报的是窗口坐标，
     * 正好也是遮罩那层用的坐标系。
     */
    var themeButtonCenter by remember { mutableStateOf<Offset?>(null) }

    /** 主题扩散动画的统一入口（抓屏 + 遮罩都在 `MainActivity` 提供）。 */
    val switchTheme = LocalThemeSwitch.current

    val tabs = listOf(
        t("generate.titleTextToImage") to Icons.Filled.Home,
        // ⚠️ 用户 2026-09-24：「手机：……**图库图标变为文件夹**」✓ ——
        // 原来用的是 `Icons.AutoMirrored.Filled.List`（一条条横线，像"清单"不像"图库"✗）✓；
        // 换成手写的 `FolderIcon` ✓（`naistudio-shared` 的 `AppIcons.kt` ✓，和桌面线同一份实现 ✓，
        // 刻意不引 `material-icons-extended` ✓）。
        t("gallery.title") to FolderIcon,
        t("tools.title") to Icons.Filled.Build,
        t("settings.title") to Icons.Filled.Settings,
        // 两个版本都叫「我的」（纯净版里只是页内卡片标题变成「API」，页名不变）
        t("profile.title") to Icons.Filled.AccountCircle,
    )

    // **四页都放进 pager**：这样「图库 / 工具 / 设置 → 文生图」也能走 animateScrollToPage，
    // 拿到和「图库滑回文生图」完全一样的滑动动画（早前工具/设置是直接换内容，没有过渡）。
    val pagerState = rememberPagerState(initialPage = index.coerceIn(0, tabs.lastIndex)) { tabs.size }

    // 单向同步：pager 是页码的唯一真源
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page -> if (index != page) index = page }
    }

    // 手势滑动**只允许在文生图 ↔ 图库之间**：工具 / 设置只能从抽屉进、用返回箭头出。
    // 用 nestedScroll 的 onPreScroll/onPreFling 把不允许方向的位移整段吃掉。
    // `contentGestureLocked`：生成页进入遮罩模式时置位 —— 那时手指是用来涂遮罩的，
    // 翻页手势必须先让位（否则横着涂两下就翻到图库去了）。
    val swipeGate = remember(pagerState) {
        object : NestedScrollConnection {
            // ⚠️ 判据一律用 `settledPage`（**翻页动画落定后**才变的那个），不用 `currentPage`：
            //    后者在手指拖到一半时就已经翻成目标页，会让闸门中途改口 ——
            //    表现就是"图库往左滑工具，滑到后半段手指被吃掉、卡在半路"。
            fun page(): Int = pagerState.settledPage

            private fun allowed(): Boolean {
                // ⚠️ **底部面板打开时禁止翻页**（用户要求）：
                // 早先这里只看"当前是哪一页"，生图页永远算"可翻" —— 所以面板开着左滑照样
                // 滑去图库。面板打开时这段横向手势应该留给面板自己（切换 提示词/角色/参数）。
                if (bottomPanelActive) return false
                // 生图 / 图库两页可翻；工具 / 设置 / 我的 一律拦死
                // （工具页里有横向滚动的胶囊，靠下面 post 阶段兜位移来挡翻页）。
                return when (page()) {
                    0 -> true
                    GALLERY_INDEX -> true
                    else -> false
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && contentGestureLocked) {
                    return Offset(available.x, 0f)
                }
                // ⚠️ 不允许翻页的页面（工具 / 设置）在**这里不吃**横向位移：
                // 工具页里有的是横向滚动的东西（排序胶囊、筛选胶囊），必须让它们先滚。
                // 剩下的位移在 onPostScroll 里兜住，翻页照样被挡住。
                // （早先在 pre 阶段整段吃掉 → 工具页的胶囊"超出屏幕又滑不动"，就是这个原因。）
                if (source == NestedScrollSource.UserInput && !allowed()) {
                    return Offset.Zero
                }
                // 方向门（available.x < 0 = 往下一页翻）：
                //  生图页只准往左（去图库）；图库页**两边都行** —— 往右回生图、往左去工具
                //  （⚠️ 用户 2026-09-26：「图库再往右滑是工具」）。工具页本身整段拦死，
                //  所以从工具**滑不回**图库 —— 回图库走顶栏返回箭头或抽屉（和设置页一样）。
                if (source == NestedScrollSource.UserInput) {
                    val ok = when (page()) {
                        0 -> available.x < 0f
                        GALLERY_INDEX -> true
                        else -> false
                    }
                    if (!ok) return Offset(available.x, 0f)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // 子级（内部横向滚动）先吃，剩下的横向位移由这里吃掉 → 挡住翻页
                if (source == NestedScrollSource.UserInput && !allowed() && available.x != 0f) {
                    return Offset(available.x, 0f)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // ⚠️ 这里**只能吃横向**。之前返回整个 `available`（含 y），
                // 结果把竖向滚动列表松手后的惯性也吞了 —— 表现就是"设置页下滑特别跟手、没有惯性"。
                // nestedScroll 的约定：返回值是"本父级消费掉的速度"，所以横向之外必须原样放行。
                if (contentGestureLocked) return Velocity(available.x, 0f)
                // 不允许翻页的页面：先让内部滚动飞（onPostFling 再兜）
                if (!allowed()) return Velocity.Zero
                val atBoundary = when (page()) {
                    // 生图页：往右没有页了
                    0 -> available.x > 0f
                    // 图库页：两边都能去（右→生图 / 左→工具）⇒ 不在边界，让它飞过去
                    else -> false
                }
                return if (atBoundary) Velocity(available.x, 0f) else Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!allowed() && available.x != 0f) return Velocity(available.x, 0f)
                return Velocity.Zero
            }
        }
    }

    // 离开图库就把批量删除模式收掉
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page != GALLERY_INDEX) {
                selecting = false
                selectedIds = emptySet()
            }
        }
    }

    // 所有「去某一页」都走这里。三种动画（由 directionFor 决定）：
    //  · 进工具 / 设置 → 从右边滑入；从工具 / 设置退回主界面 → 从左边滑回；其余淡入淡出
    //  · +1：**新页面从右边滑入**（进工具 / 设置）—— 瞬时换页 + 把整块内容从右侧 translateX 归位，
    //        而不是让 pager 一路平移过去（后者会顺带组合中间的图库页，跨两页时明显卡顿）
    //  · -1：**从左边滑回**（工具 / 设置退回主界面）—— 同上，方向相反
    //  ·  0：缓入缓出的淡入淡出（文生图 ↔ 图库、图库退回主界面）
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    var contentAlpha by remember { mutableFloatStateOf(1f) }
    var slideX by remember { mutableFloatStateOf(0f) }
    // 只在动画期间挂 graphicsLayer：全屏图层常驻等于每帧多画一张 1440×3200 的纹理，
    // 静止时白白吃带宽（切页卡顿的元凶之一）。动画起止各改一次，不会每帧重组。
    var animating by remember { mutableStateOf(false) }
    var navJob by remember { mutableStateOf<Job?>(null) }

    // 进工具 / 设置 → 从右边滑入；从工具 / 设置退回主界面 → 从左边滑回；
    // **图库 → 生图 也走"从左边滑回"**（用户要求：和手指右滑返回的动画统一，
    // 早先这里落到了 else 分支 = 淡入淡出，所以点头上的返回箭头和滑动返回看起来不一样）。
    // ⚠️ 用户 2026-09-26：「**点击进入时，图库到生图有滑动动画，但是反过来没有**」——
    //    早前这里的判据是"是不是工具 / 设置"，所以 **生图 → 图库** 落到了 else 分支
    //    = 淡入淡出，只有 **图库 → 生图** 是滑动 ⇒ 点击来回动画不对称。
    //    现在改成**纯比页码**：去更靠右的页从右边滑入，回更靠左的页从左边滑回，
    //    同一页才淡入淡出。这样 生图 ↔ 图库 来回都是滑动，方向和手指滑动的方向一致。
    val directionFor: (Int, Int) -> Int = { from, to ->
        when {
            to > from -> 1
            to < from -> -1
            else -> 0
        }
    }

    val goToPage: (Int, Int) -> Unit = { page, direction ->
        index = page
        if (page != pagerState.currentPage) {
            navJob?.cancel()
            navJob = scope.launch {
                animating = true
                try {
                    if (direction != 0) {
                        contentAlpha = 1f
                        pagerState.scrollToPage(page)
                        val from = if (direction > 0) screenWidthPx else -screenWidthPx
                        slideX = from
                        animate(
                            initialValue = from,
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = 220,
                                easing = FastOutSlowInEasing,
                            ),
                        ) { v, _ -> slideX = v }
                    } else {
                        slideX = 0f
                        animate(1f, 0f, animationSpec = tween(durationMillis = 70)) { v, _ ->
                            contentAlpha = v
                        }
                        pagerState.scrollToPage(page)
                        animate(0f, 1f, animationSpec = tween(durationMillis = 130)) { v, _ ->
                            contentAlpha = v
                        }
                    }
                } finally {
                    contentAlpha = 1f
                    slideX = 0f
                    animating = false
                }
            }
        }
    }
    val backToGenerate: () -> Unit = {
        goToPage(0, directionFor(pagerState.currentPage, 0))
    }

    // 一次性提示：AppState 里挂了 toastEvent 就弹一个 Toast（导入/导出的成败都走这里），
    // 弹完立刻消费掉，避免重组时重复弹。
    val toastContext = LocalContext.current
    LaunchedEffect(state.toastEvent) {
        val event = state.toastEvent ?: return@LaunchedEffect
        Toast.makeText(
            toastContext,
            event.message,
            if (event.isError) Toast.LENGTH_LONG else Toast.LENGTH_SHORT,
        ).show()
        state.consumeToast()
    }

    // 图库点「使用此图图生图」后回到文生图页（AppState 里的计数器；必须放在 goToPage 之后）
    // ⚠️ 只保留这一处与滑动无关的真 bug 修复：早先只判断 `tick > 0`，于是每次这段副作用
    // 重新进入组合（开抽屉、点工具、任何重组）都会执行一次 → "点侧边栏工具被弹回生图页"。
    var handledGenerateTick by remember { mutableStateOf(state.navigateToGenerateTick) }
    LaunchedEffect(state.navigateToGenerateTick) {
        if (state.navigateToGenerateTick > handledGenerateTick) {
            handledGenerateTick = state.navigateToGenerateTick
            if (pagerState.currentPage != 0) backToGenerate()
        }
    }

    StudioDrawer(
        open = drawerOpen,
        onOpenChange = { drawerOpen = it },
        width = drawerWidth(),
        // 侧边栏**只有文生图页**能呼出：
        //  · 图库页留给 pager 的「滑回文生图」
        //  · 底部面板在用（打开或正在拉）时也屏蔽 —— 那时候右滑多半是想调参数
        //    （⚠️ 遮罩模式也算"面板在用"：GenerateScreen 里 `onPanelActiveChange(panelActive || maskMode)`
        //     置位，所以涂遮罩时右滑不会把侧边栏拉出来）
        //  · 工具 / 设置页顶栏是返回箭头，不提供抽屉入口
        //  · **画布编辑器开着时同样屏蔽**（用户 2026-09-18 深夜：「画布编辑器和遮罩一样，
        //    在画布编辑器状态下禁止滑动呼出侧边栏」）—— 画布上横向拖是画笔/选区，
        //    从左边往右划更可能是想画到画布左侧，不该把抽屉带出来。
        //    pager 那边的滑动早就在 `userScrollEnabled` 里关掉了，这里补的是**抽屉**这条手势。
        gesturesEnabled = pagerState.currentPage == 0 && !bottomPanelActive &&
            !state.canvasEditorOpen && state.canvasMode != 1,
        drawerContent = {
            // 主题深浅在这一层算一次（顶部标题、底部那颗切换按钮都要用同一份判断）
            val dark = when (state.settings.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val ref = LocalRef.current
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 22.dp),
            ) {
                // ---- 顶部：App 图标 + 名字（用户 2026-09-16 要求）----
                // **图标在上、名字在下、整体居中**（用户口径）；图标按 SVG 的 108×108
                // 视口等比缩放，任何尺寸都不变形。
                // 名字颜色是**正黑 / 正白**，不走 onSurface（那两档带主题色调）——
                // 由 `naiBrandTitleColor` 统一给，界面里不写死色值。
                // 网页 v2 `.brand`：电脑线 FlatBrandMark（无底板、扁平：框 brandInk、星 brandStar）
                // 58 宽 + 间距 4 + 15/600 名字（「NAI」的 I 染蓝），上 4 下 26
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FlatBrandMark(size = 58.dp, color = ref.brandInk(), starColor = ref.brandStar())
                    Spacer(Modifier.height(4.dp))
                    val appName = stringResource(R.string.app_name)
                    val starBlue = ref.brandStar()
                    Text(
                        text = if (appName.startsWith("NAI")) {
                            buildAnnotatedString {
                                append("NA")
                                withStyle(SpanStyle(color = starBlue)) { append("I") }
                                append(appName.substring(3))
                            }
                        } else {
                            AnnotatedString(appName)
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.1.sp,
                        color = ref.brandInk(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // 上半：侧边栏 = 原来的底栏，只有页面导航（可滚动）
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    // 「工具」那一项是个**下拉分组**（用户要求）：点一下展开工具名，
                    // 再点工具名进对应界面 —— 反推提示词在工具页，导演台/角色分区在生成页。
                    var toolsExpanded by rememberSaveable { mutableStateOf(false) }
                    val openTool: (ToolEntry) -> Unit = { entry ->
                        when (entry.surface) {
                            ToolSurface.TOOLS -> {
                                state.selectTool(entry.id)
                                goToPage(TOOLS_INDEX, directionFor(pagerState.currentPage, TOOLS_INDEX))
                            }
                            ToolSurface.GENERATE -> {
                                entry.generateAction?.let { action ->
                                    state.requestGenerateAction(entry.id, action)
                                }
                                goToPage(0, directionFor(pagerState.currentPage, 0))
                            }
                        }
                        drawerOpen = false
                    }

                    tabs.forEachIndexed { tabIndex, (label, icon) ->
                        // ⚠️ 2026-09-25 更正：用户说的是「从底部抽屉删除**参数**」，不是设置 ✗ ——
                        //    上一版我把设置从这儿撤了，**已经还原** ✓（设置照旧在抽屉里列着）。
                        //    「参数」是**底部抽屉的栏目**（`ControlPanel` 那条 TabRow），
                        //    撤掉的是那一条，见 `ControlPanel` 里的说明 ✓。
                        val isToolsGroup = tabIndex == TOOLS_INDEX
                        RefNavItem(
                            label = label,
                            icon = icon,
                            trailing = if (isToolsGroup) {
                                { RefChevron(toolsExpanded) }
                            } else {
                                null
                            },
                            selected = !isToolsGroup && pagerState.currentPage == tabIndex,
                            onClick = {
                                if (isToolsGroup) {
                                    // 点「工具」本身：展开/收起下拉，不翻页
                                    toolsExpanded = !toolsExpanded
                                } else {
                                    // 进工具 / 设置从右滑入；从工具 / 设置回主界面从左滑回；其余淡入淡出
                                    goToPage(
                                        tabIndex,
                                        directionFor(pagerState.currentPage, tabIndex),
                                    )
                                    drawerOpen = false
                                }
                            },
                        )

                        // 展开后的工具名（缩进一格，字小一号，点名字进对应界面）
                        // 展开/收起带**弹出动画**（用户要求）：垂直展开 + 淡入淡出。
                        AnimatedVisibility(
                            visible = isToolsGroup && toolsExpanded,
                            enter = expandVertically(
                                animationSpec = tween(180, easing = FastOutSlowInEasing),
                            ) + fadeIn(animationSpec = tween(150)),
                            exit = shrinkVertically(
                                animationSpec = tween(140, easing = FastOutSlowInEasing),
                            ) + fadeOut(animationSpec = tween(90)),
                        ) {
                            Column(
                                Modifier.padding(start = 26.dp, top = 2.dp, bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                ToolCatalog.entries.forEach { entry ->
                                    val active = state.selectedTool == entry.id &&
                                        when (entry.surface) {
                                            ToolSurface.TOOLS -> pagerState.currentPage == TOOLS_INDEX
                                            ToolSurface.GENERATE -> pagerState.currentPage == 0
                                        }
                                    RefSubNavItem(
                                        title = t(entry.titleKey),
                                        hint = t(entry.hintKey),
                                        selected = active,
                                        onClick = { openTool(entry) },
                                    )
                                }
                            }
                        }
                    }

                    // 统计：和页面导航同一列（网页原型：统计紧跟「我的」之下）
                    RefNavItem(
                        label = t("stats.title"),
                        icon = BarChartIcon,
                        selected = false,
                        onClick = { statsOpen = !statsOpen },
                    )
                }

                // ⚠️ 用户 2026-09-26：「**删除统计，参照电脑的侧边栏统计**」——
                //    我上一版在这里塞了一段**纯文字**统计（共 N 张 · 今日 N · 近 7 天 N），
                //    那是自己编的，电脑那边根本不是这个：电脑是**一根柱状图图标 → 弹出统计窗**
                //    （GitHub 贡献表那套：出图 / 消耗点数 / tag 字数 / 本月天数，能翻月份、
                //      点某天看明细，见 `UsageStatsScreen`）。现在**那段文字删掉**，
                //    改成和电脑一样的一颗入口 —— 位置也照电脑：**页面导航之下、深浅色之上**。
                // 底部 `.foot`：一条顶边线 + 暗色/浅色切换（深色 = warning 色太阳，浅色 = 月亮）
                Box(Modifier.fillMaxWidth().height(1.dp).background(ref.border))
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .onGloballyPositioned { coords ->
                            themeButtonCenter = coords.boundsInWindow().center
                        }
                        .clickable {
                            // 不直接切主题：交给「圆形扩散」动画 —— 报**这颗按钮的中心**当圆心
                            val center = themeButtonCenter
                            if (center != null) {
                                switchTheme(center, !dark)
                            } else {
                                state.setSettings { it.copy(theme = if (dark) "light" else "dark") }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = if (dark) SunIcon else MoonIcon,
                        contentDescription = null,
                        tint = if (dark) ref.warning else ref.muted,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        if (dark) t("drawer.lightMode") else t("drawer.darkMode"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ref.muted,
                    )
                }
            }
        },
    ) {
        // ⚠️ 统计窗（用户 2026-09-26：「参照电脑的侧边栏统计」）——
        //    抽屉点完那颗柱状图图标**顺手把它关掉**再弹，不然两个浮层叠着，
        //    关掉统计窗之后还留着一层抽屉（用户会以为没关掉）。
        if (statsOpen) {
            LaunchedEffect(Unit) { drawerOpen = false }
            PhoneStatsSheet(state = state, t = t, onDismiss = { statsOpen = false })
        }
        // 返回键：**从最上面那一层往下逐级退，任何一级都不直接退出 App**。
        // 顺序（上面的先吃）：
        //  1. 侧边栏开着 → 关侧边栏
        //  2. 剧情放大编辑页开着 → 退回剧情框（抽屉继续留着）
        //  3. 遮罩模式开着 → 退出遮罩
        //  4. 底部抽屉拉着（含正在拖）→ 收起抽屉
        //  5. 图库在批量选择 → 退出选择
        //  6. 不在文生图页 → 回文生图
        //  7. 已经在文生图页、上面什么都没有 → **两次退出**
        //
        // ⚠️ 第 4 条是 2026-09-16 补的：抽屉开着时按返回会一路落到第 7 条，
        // 弹"再按一次退出"—— 用户报的正是这个。抽屉的开合状态住在 GenerateScreen 里，
        // 这里够不着，所以走 `state.requestPanelCollapse()` 发一次性请求。
        //
        // 弹窗 / ModalBottomSheet **各自**有 BackHandler，而且比这里组合得晚，
        // 会先吃掉返回键；全屏看图是个 `Dialog`，系统返回键本来就自动关它 —— 都不用管。
        when {
            // 画布编辑器开着 → 返回键 = 退出编辑器（**丢弃这次编辑**，和左上角那个叉一致；
            // 想留下改动用顶栏的「完成」）。放在最前面：它盖在所有东西上面。
            // （照电脑线 `StudioShell.kt:797` 的同一条分支搬过来。）
            state.canvasEditorOpen -> BackHandler { state.closeCanvasEditor(commit = false) }
            drawerOpen -> BackHandler { drawerOpen = false }
            index == 0 && state.plotEditorOpen -> BackHandler { state.closePlotEditor() }
            index == 0 && state.maskMode && state.canvasMode != 2 -> BackHandler { state.toggleMaskMode() }
            index == 0 && bottomPanelActive -> BackHandler { state.requestPanelCollapse() }
            index == GALLERY_INDEX && selecting -> BackHandler {
                selecting = false
                selectedIds = emptySet()
            }
            index != 0 -> BackHandler { backToGenerate() }
            else -> BackHandler {
                val now = System.currentTimeMillis()
                if (now - lastBackPress <= EXIT_INTERVAL_MS) {
                    activity?.finish()
                } else {
                    lastBackPress = now
                    Toast.makeText(
                        context,
                        t("app.pressBackAgainToExit"),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

        // 遮罩模式下顶栏整条让给遮罩工具条（GenerateScreen 自己画在内容最上面），
        // 这样图片既不被工具条压住、也不用因为多一行而缩小。
        // 画布编辑器同理：它自带一条顶栏（取消/重置/完成），外壳这条让位
        //（照电脑线 `StudioShell.kt:822` 的 `contentOwnsTopBar` 搬过来）。
        val maskToolbarOwnsTopBar = pagerState.currentPage == 0 && state.maskMode && state.canvasMode != 2
        val contentOwnsTopBar = maskToolbarOwnsTopBar || state.canvasEditorOpen
        // ⚠️ 用户 2026-09-24：「**生图页的顶栏去除，都变为小按钮**」✓ ⇒
        //   文生图页**整条顶栏不画** ✗（原来是一条 64dp 的 `TopAppBar` ✓）——
        //   腾出来的高度全给画布 ✓；原来栏里的东西改成**浮在画布上的圆形小按钮** ✓
        //   （侧栏开关 / 余额胶囊 ✓，见下面 `GenerateFloatingBars` ✓）。
        //   其它页（图库 / 工具 / 设置 / 我的）**照旧保留顶栏** ✓（那边要靠它返回 / 搜索 / 排序 ✓）。
        val hideTopBar = contentOwnsTopBar || pagerState.currentPage == 0

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (!hideTopBar) {
                    TopAppBar(
                    navigationIcon = {
                        // 只有文生图页是「汉堡（开侧边栏）」；其它页是返回箭头
                        if (pagerState.currentPage == 0) {
                            IconButton(onClick = { drawerOpen = true }) {
                                Icon(Icons.Filled.Menu, contentDescription = t("drawer.open"))
                            }
                        } else {
                            IconButton(onClick = backToGenerate) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = t("drawer.backToGenerate"),
                                )
                            }
                        }
                    },
                    title = {
                        // 工具页显示**当前工具的名字**（不再是笼统的"工具"）
                        val title = if (pagerState.currentPage == TOOLS_INDEX) {
                            t(ToolCatalog.byId(state.selectedTool).titleKey)
                        } else {
                            tabs[pagerState.currentPage].first
                        }
                        // 角色图鉴 / 图库：放大镜点开后，标题位置滑出一枚**胶囊搜索框**
                        val animaOpen = pagerState.currentPage == TOOLS_INDEX &&
                            state.selectedTool == ToolCatalog.ANIMADEX && state.animaSearchOpen
                        val galleryOpen = pagerState.currentPage == GALLERY_INDEX && state.gallerySearchOpen
                        if (animaOpen || galleryOpen) {
                            AnimatedVisibility(visible = true, enter = expandHorizontally() + fadeIn()) {
                                if (galleryOpen) {
                                    SearchCapsule(
                                        value = state.galleryQuery,
                                        onValueChange = { state.updateGalleryQuery(it) },
                                        placeholder = t("gallery.searchHint"),
                                        onSubmit = { state.closeGallerySearch() },
                                    )
                                } else {
                                    SearchCapsule(state, t)
                                }
                            }
                        } else {
                            Text(title)
                        }
                    },
                    actions = {
                        // 图库页右上角不显示余额：
                        //  常态：[垃圾桶（批量删除）] [横杠排序按钮]
                        //  批量删除模式：[取消] [确定]
                        if (pagerState.currentPage == GALLERY_INDEX) {
                            if (selecting) {
                                TextButton(onClick = {
                                    selecting = false
                                    selectedIds = emptySet()
                                }) { Text(t("common.cancel")) }
                                TextButton(
                                    onClick = { confirmingBatchDelete = true },
                                    enabled = selectedIds.isNotEmpty(),
                                ) { Text(t("common.confirm")) }
                            } else {
                                IconButton(
                                    onClick = {
                                        selecting = true
                                        selectedIds = emptySet()
                                    },
                                    enabled = state.history.isNotEmpty(),
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = t("gallery.batchDelete"),
                                    )
                                }
                                SortIconButton(
                                    contentDescription = t("gallery.controls"),
                                    onClick = { galleryControls = true },
                                )
                                // 图库搜索：搬到右上角的放大镜（和角色图鉴一样，点开顶栏滑出胶囊）
                                IconButton(onClick = { state.toggleGallerySearch() }) {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = t("gallery.search"),
                                    )
                                }
                            }
                        } else if (pagerState.currentPage == TOOLS_INDEX &&
                            state.selectedTool == ToolCatalog.ANIMADEX
                        ) {
                            // 角色图鉴：右上角一枚放大镜，点一下顶栏滑出搜索框（用户要求）
                            IconButton(onClick = { state.animaToggleSearch() }) {
                                Icon(Icons.Filled.Search, contentDescription = t("anima.search"))
                            }
                        } else if (pagerState.currentPage == 0) {
                            // **余额只在主页面（文生图）右上角展示**，其余页面不显示（用户要求）
                            AccountChip(state, t)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        // 毛玻璃：顶栏半透明。**后面得有东西才看得出来** ——
                        // 所以生成页的内容故意不吃顶部内边距（见下面 pager 那段的说明），
                        // 图片会铺到顶栏背后，隔着这条半透明看过去就是糊的。
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                            .copy(alpha = NaiGlass.BAR_ALPHA),
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                            .copy(alpha = NaiGlass.BAR_ALPHA),
                    ),
                    )
                }
            },
            // ⚠️ 用户 2026-09-26：「**去除底栏**」——原来这里挂了一条常驻底部栏
            //    （生图 / 图库 / 工具 三格）；现在整条拿掉，导航回到抽屉里。
            //    连带效果：`inner.bottom` 归零 ⇒ 生成页那摞贴底覆盖层直接落到窗口底边；
            //    键盘弹起时也不必再扣"一条底栏"的高度 —— `phoneBottomBarHeightPx` 会
            //    永远是 0，而 `GenerateScreen` 里那个减法是自适应的，留着无害。
            containerColor = if (pagerState.currentPage == 0 && hideTopBar) {
                LocalRef.current.runSkin().cvBg
            } else {
                MaterialTheme.colorScheme.background
            },
        ) { inner ->
            // imePadding：键盘弹起时把四页内容整体上移（edge-to-edge + adjustResize 下，
            // 没有 imePadding 的层不会被键盘顶起，中下部的输入框会被键盘盖住 —— 用户报过）。
            // TopAppBar 在 insets 之上不受影响；框内光标跟随由各滚动容器的
            // bringIntoView 自动接住（BasicTextField 获得焦点/换行时会向祖先请求滚动）。
            //
            // ⚠️ **生成页（第 0 页）例外 —— 它自己处理键盘**：
            // 生成页底部是两块贴底覆盖层，「把手行 + 抽屉 + 按钮栏」那一块要跟着键盘上浮，
            // 而「2x放大/导演台 + 生成图片」那一块要留在屏幕底部（让位给键盘）。
            // 整页一起 imePadding 的话这两块会一起被顶上去，就没法分开。
            // 判据用 `settledPage` 而不是 `currentPage`：翻页途中反复换修饰符会让布局抖。
            //
            // ⚠️ 试过**不吃顶部内边距**（让图片铺到毛玻璃顶栏后面），已按用户要求回退：
            // 那样图片会整体上移、被顶栏压掉一截，看图为主的页面不划算。
            // 顶栏的毛玻璃保留，只是它后面是页面底色而不是图片 —— 观感更安静。
            Box(
                Modifier
                    .fillMaxSize()
                    .then(
                        if (pagerState.settledPage == 0) {
                            Modifier
                        } else {
                            Modifier.imePadding()
                        },
                    )
                    .padding(inner),
            ) {
                // ---- 错峰：冷启动的预热窗内**不**预组合相邻页 ----
                // `beyondViewportPageCount = 1` 是为了"从图库滑回生图"不黑帧（见下面那段注释），
                // 代价是**冷启动时图库页也被一起组合**：它的网格会把首屏十几个格子
                // （每格一次缩略图读盘 / 解码）立刻拉起来，正好和"用户第一次拉底栏"抢主线程。
                // 所以：预热窗（800ms）内不预组合；窗口一过、或者用户真的开始翻页，立刻恢复 ——
                // 后者保证"窗口内就滑去图库"的那次也不会在滑回来时黑帧。
                var neighborPrewarmReady by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(800)
                    neighborPrewarmReady = true
                }
                val pagerInUse = pagerState.isScrollInProgress || pagerState.currentPage != 0

                // 四页都在 pager 里：手势滑动（只允许 文生图 ↔ 图库）走 pager 自带的跟手动画；
                // 抽屉点选 / 返回箭头 / 返回键走 goToPage 的淡入淡出
                HorizontalPager(
                    state = pagerState,
                    // **只有 生图(0) 与 图库(1) 允许手指左右滑翻页**，且**底部面板打开时一律不翻页**。
                    // ⚠️ 面板打开时不能靠 swipeGate 拦：闸门为了让"面板内左滑=换栏"和"工具页横向胶囊"
                    // 能工作，改成了 post 阶段才吃位移 —— 那时 pager 已经把位移消费掉了，闸门只看到 0。
                    // 所以必须在 pager 上直接关掉滑动（和工具/设置页禁滑同一招）。
                    // **生图(0) / 图库(1) 允许手指左右滑翻页**（方向由 swipeGate 按页放行：
                    // 生图只往左、图库两边都行），工具 / 设置 / 我的 只能从抽屉进。
                    // ⚠️ 用户 2026-09-26「**图库再往右滑是工具**」：从图库滑向工具的那一次手势里，
                    // 判据必须用 **`settledPage`**（落定后才变）而不是 `currentPage` ——
                    // 后者滑到一半就翻成 2，这个开关会当场翻成 false、把进行中的拖拽掐断。
                    // 用 settledPage 还有个好处：**手势结束时**它就变成工具页 ⇒ 开关自动关上，
                    // 免得在工具页误滑到设置 / 我的。工具页的横向胶囊照旧先滚（闸门 post 阶段兜底）。
                    // ⚠️ 面板打开时不能靠 swipeGate 拦：闸门为了让"面板内左滑=换栏"和"工具页横向胶囊"
                    // 能工作，改成了 post 阶段才吃位移 —— 那时 pager 已经把位移消费掉了，闸门只看到 0。
                    // 所以必须在 pager 上直接关掉滑动（和工具/设置页禁滑同一招）。
                    // 用户 2026-09-29：「无限画布禁止滑动拉出侧边栏，默认滑动是拖动或放大缩小画布」✓
                    userScrollEnabled = !bottomPanelActive && !state.canvasEditorOpen && (
                        pagerState.settledPage <= GALLERY_INDEX
                        ) && !(pagerState.settledPage == 0 && state.canvasMode == 1),
                    // 相邻页**保持组合**：否则"从图库滑回生图"时，生图页是刚被重建的，
                    // 图片要重新解码、首帧还没画出来 → 滑动过程中黑几帧（看着像卡顿）。
                    // ⚠️ 但**冷启动那 800ms 除外**（见上面那段"错峰"注释）。
                    beyondViewportPageCount = if (neighborPrewarmReady || pagerInUse) 1 else 0,
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(swipeGate)
                        .then(
                            if (animating) {
                                Modifier.graphicsLayer {
                                    alpha = contentAlpha
                                    translationX = slideX
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) { page ->
                    when (page) {
                        0 -> GenerateScreen(
                            state = state,
                            onPanelActiveChange = { bottomPanelActive = it },
                            onContentGestureLockChange = { contentGestureLocked = it },
                            // ⚠️ 用户 2026-09-25：设置从抽屉里撤了 ⇒ 改由生图页运行条上那颗
                            //    齿轮图标进（就在「导入图片」和「生成图片」中间）。翻页仍旧走
                            //    `goToPage`（和抽屉点选同一套滑入动画）。
                        )

                        GALLERY_INDEX -> GalleryScreen(
                            state = state,
                            controlsOpen = galleryControls,
                            onDismissControls = { galleryControls = false },
                            onSelect = { gallerySelection = it },
                            selecting = selecting,
                            selectedIds = selectedIds,
                            onToggleSelect = { id ->
                                selectedIds = if (id in selectedIds) {
                                    selectedIds - id
                                } else {
                                    selectedIds + id
                                }
                            },
                        )

                        2 -> ToolsScreen(state)
                        3 -> SettingsScreen(state)
                        else -> ProfileScreen(state)
                    }
                }

                // ⚠️ 用户 2026-09-24：「生图页的顶栏去除，都变为小按钮，呼出侧边栏的三横变为圆形按钮，
                //    额度像电脑一样变成胶囊」✓（随后又报「**手机的左上角没有悬浮的圆形按钮啊**」✓，
                //    这一版把它挪到**正确的位置**了 ✓）——
                //
                // ⚠️⚠️ **必须画在 `HorizontalPager` 之后** ✗✗（这是上一版"看不见"的真因 ✓）：
                //    同一个 `Box` 里**后画的盖前面的** ✓ —— 上一版这一段排在 pager **前面** ✓，
                //    于是 pager 那一整块（= 生图页）**整个把它盖住了** ✗，按钮明明在、就是看不见 ✓。
                //    ⇒ 挪到 pager 之后，并配 `.zIndex(...)` 把顺序钉死 ✓（以后谁在旁边插一层都不会再盖住 ✓）。
                // ⚠️ 只在文生图页画 ✓（别的页顶栏还在 ✓、里面本来就有它们 ✓，画两遍就重了 ✗）；
                //   遮罩模式 / 画布编辑器例外 ✓（那两档有自己的顶栏条 ✓）。
                if (pagerState.currentPage == 0 && !contentOwnsTopBar) {
                    GenerateFloatingBars(
                        state = state,
                        t = t,
                        onOpenDrawer = { drawerOpen = true },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .zIndex(5f)
                            // ⚠️ 量它的**高度 = 下沿**（这一条是从顶部往下摆的 ⇒ 高度就是下沿 ✓），
                            //    抽屉靠这个数"顶到余额胶囊下面就停"（见 `maxPanelHeight` ✓）。
                            .onSizeChanged { state.phoneFloatingBottomPx = it.height },
                    )
                }

                // **画布编辑器**：盖住整个内容区（和电脑线 `StudioShell.kt:1040` 同一个位置/同一个理由）。
                // 它自带顶栏、工具行、参数行，所以外壳那条顶栏在它开着的时候要让位
                // （见上面 `contentOwnsTopBar`）。
                //
                // ⚠️ 手机线多一层考虑：pager 还在底下组合着（相邻页要保活），而画布区自己要吃
                // 手指（画笔/选区），所以顺手把 pager 的手势关了（见上面 `userScrollEnabled`），
                // 免得在画布上横拖时把页面翻走。
                if (state.canvasEditorOpen) {
                    CanvasEditorScreen(
                        state = state,
                        t = t,
                        onCancel = { state.closeCanvasEditor(commit = false) },
                        onCommit = { state.closeCanvasEditor(commit = true) },
                    )
                }
            }
        }
    }

    // 批量删除：确定后再确认一遍，才真删
    if (confirmingBatchDelete) {
        AlertDialog(
            onDismissRequest = { confirmingBatchDelete = false },
            title = { Text(t("gallery.batchDelete")) },
            text = {
                Text(
                    RuntimeText.format(
                        language,
                        "gallery.batchDeleteConfirm",
                        mapOf("count" to selectedIds.size),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedIds.forEach { id -> state.deleteHistory(id) }
                    selectedIds = emptySet()
                    selecting = false
                    confirmingBatchDelete = false
                }) { Text(t("common.confirm")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingBatchDelete = false }) { Text(t("common.cancel")) }
            },
        )
    }
}

/**
 * 自己实现的抽屉（替代 `ModalNavigationDrawer`）。
 *
 * 为什么不用官方的：官方那套滑动是 `anchoredDraggable` 挂在父级，而这台机器上有两道墙 ——
 *  1. 系统手势导航把最外侧 ~24dp 的滑动当「返回」，从边缘起手的事件 App 根本收不到；
 *  2. 更关键的是 `HorizontalPager` 在子层级会先把横向拖动吃掉（scrollable 一过 slop 就独占手势），
 *     父级的拖动检测再也拿不到，于是「右滑不呼出、也不跟手」。
 *
 * 这里改成：
 *  · 拖动处理挂在**最外层**，并且跑在 `PointerEventPass.Initial` —— 先于所有子级拿到事件，
 *    所以 pager 抢不走；
 *  · 起手区是**整个屏幕**（左右半边都能拉出来），靠**方向**区分意图：
 *    往右拖 = 开侧边栏，往左拖 = 交给 pager 翻去图库；
 *  · 位移自己驱动（状态 + `snap()`），所以**完全跟手**，松手按一半位置吸附开/关。
 */
@Composable
private fun StudioDrawer(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    width: Dp,
    gesturesEnabled: Boolean,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }

    // 手势里要读「当前允不允许拉」，但**不能**把它当 pointerInput 的 key：
    // 翻页过程中这个值会变，key 一变 Compose 就取消正在进行的手势协程，
    // `dragging` 会永久卡在 true（动画停摆、抽屉半开挡屏）——这就是「快滑着切换时卡死」的根因。
    val drawable by rememberUpdatedState(gesturesEnabled)

    // 拖动中用 snap（完全跟手），松手后用 spring 吸附。
    // 手势回调跑在受限协程作用域里，不能直接调 Animatable，所以用「状态 + 动画」这套。
    var dragging by remember { mutableStateOf(false) }
    var target by remember { mutableFloatStateOf(-widthPx) }
    val offset by animateFloatAsState(
        targetValue = target,
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.85f, stiffness = 750f),
        label = "drawerOffset",
    )

    // 汉堡按钮 / 点导航项 / 返回键引起的开合，走后端动画
    LaunchedEffect(open, widthPx) {
        dragging = false
        target = if (open) 0f else -widthPx
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(widthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val alreadyOpen = target > -widthPx + 1f
                    // 图库页不让「右滑拉出」，但已经开着时仍然允许往回收
                    if (!drawable && !alreadyOpen) return@awaitEachGesture

                    // ⚠️ **不要**写成 `var claiming = alreadyOpen`。
                    //
                    // 那样写等于"抽屉一开着就立刻认领手势"，于是下面每一帧移动都会
                    // `change.consume()` —— 手指按下去那几像素的抖动也算位移，
                    // 结果是**抽屉里的点击全被这一层吃掉**：
                    //   · 「工具」那一项展不开（点击进不到 NavigationDrawerItem）；
                    //   · 工具列表也滚不动（纵向位移同样被 consume）。
                    // 而且松手时会走到下面的收口逻辑：在"当前页不允许手势"时
                    // （比如底栏抽屉开着 → `drawable = false`）`settleOpen` 算成 false，
                    // 把整个侧边栏**收回去**。用户报的「点工具会收回」就是这个。
                    //
                    // 改成和"关着"时一个口径：**过了阈值、确认是横向，才认领**。
                    var claiming = false
                    var lastX = down.position.x
                    var lastY = down.position.y
                    var travelled = 0f
                    var travelledY = 0f
                    val slop = viewConfiguration.touchSlop
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val dx = change.position.x - lastX
                            val dy = change.position.y - lastY
                            lastX = change.position.x
                            lastY = change.position.y
                            travelled += dx
                            travelledY += dy
                            if (dx != 0f || dy != 0f) {
                                if (!claiming) {
                                    // ⚠️ 必须"**明确右滑且横向占优**"才接管，而且接管前**一律不 consume**。
                                    // 早先只看"第一段位移偏右"就 claim：连续快滑时手指起步那一两帧的
                                    // 反向抖动会被当成"开抽屉"→ 吃掉手势、同时取消 pager 正在进行的
                                    // 翻页动画 → 画面落回生图页（用户反复反馈的"快滑会退回"）。
                                    val beyondSlop = abs(travelled) > slop || abs(travelledY) > slop
                                    if (!beyondSlop) {
                                        // 还没过阈值：继续观察，什么都不做
                                    } else if (alreadyOpen) {
                                        // 抽屉**已经开着**：向左拖 = 往回收。
                                        // 向右拖没有意义（target 已经贴在 0 上，coerceIn 会夹住），
                                        // 所以那种情况不认领、也不 consume，让事件原样落到子级。
                                        // 纵向占优同理让开 —— 抽屉里的工具列表要能滚。
                                        if (travelled < -slop && abs(travelled) > abs(travelledY)) {
                                            claiming = true
                                            dragging = true
                                        } else if (abs(travelled) <= abs(travelledY)) {
                                            return@awaitEachGesture
                                        }
                                    } else if (travelled > slop && abs(travelled) > abs(travelledY) * 1.2f) {
                                        claiming = true
                                        dragging = true
                                    } else {
                                        // 左滑 / 纵向 / 斜向：放手，交给 pager 或列表
                                        return@awaitEachGesture
                                    }
                                }
                                if (claiming) {
                                    change.consume()
                                    target = (target + dx).coerceIn(-widthPx, 0f)
                                }
                            }
                            if (!change.pressed) break
                        }
                    } finally {
                        // 正常松手、被取消、指针丢失……都必须把状态收干净：
                        // 不留 `dragging = true`，也不留「半开却不动」的抽屉。
                        if (claiming) {
                            dragging = false
                            // 松手判据放宽：**先看甩动速度**（轻轻一甩就算），
                            // 速度不够时只要拉出 25% 就认 —— 原来要拖过一半，手感很硬。
                            val vx = tracker.calculateVelocity().x
                            val settleOpen = drawable && when {
                                vx > FLING_VX -> true
                                vx < -FLING_VX -> false
                                else -> target > -widthPx * (1f - DRAG_TO_OPEN)
                            }
                            target = if (settleOpen) 0f else -widthPx
                            onOpenChange(settleOpen)
                        }
                    }
                }
            },
    ) {
        // ---- 毛玻璃上半：把**背后的内容**糊掉 ----
        // 抽屉自己是半透明的（见下面的 ModalDrawerSheet），隔着它看过去的就是这一层。
        // 半径**乘上拉开进度**：收起时是 0，完全没有离屏图层的开销；拉开才逐渐糊掉。
        // ⚠️ Android 12 以下 `Modifier.blur` 是空操作，那时只剩半透明、没有模糊。
        val glassProgress = (1f + offset / widthPx).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxSize()
                .blur(radius = NaiGlass.blurRadius * glassProgress),
        ) { content() }

        val progress = glassProgress
        if (progress > 0.001f) {
            // 遮罩：让背后那层再暗一点（玻璃后面不该比玻璃还亮），点一下收起。
            // 比之前的 0.32 淡一些 —— 模糊本身已经在做隔离，不需要压那么黑。
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f * progress))
                    .pointerInput(Unit) { detectTapGestures { onOpenChange(false) } },
            )
            ModalDrawerSheet(
                // ---- 毛玻璃下半：抽屉自己半透明（⑨c 苹果风：42% ✓）----
                // `drawerTonalElevation = 0`：色调抬升会给半透明底色再叠一层不透明色，
                // 把"透"这件事直接抵消掉，所以必须关掉。
                drawerContainerColor = LocalRef.current.sidebar(),
                drawerContentColor = LocalRef.current.text,
                drawerTonalElevation = 0.dp,
                drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight()
                    .offset { IntOffset(offset.roundToInt(), 0) }
                    .border(1.dp, LocalRef.current.border, RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)),
            ) {
                drawerContent()
            }
        }
    }
}

/**
 * 侧边栏底部那颗「统计」弹出的窗（用户 2026-09-26：「参照电脑的侧边栏统计」）。
 *
 * ⚠️ 电脑那边是**贴着按钮弹一扇 520×640 的小窗**（`StatsAnchor` / `StatsWindow`）；
 * 手机屏小得多，照搬"贴着按钮弹窗"既放不下也会顶出屏幕 ⇒ 这里按手机的做法：
 * **一个 `ModalBottomSheet`**，内容用**同一份** [UsageStatsScreen]（共用纯逻辑 + 共用界面，
 * 两线看起来一模一样，这才是"参照电脑"的意思）。点空白 / 下拉关闭。
 *
 * 高 0.85 屏：那里面是"一个月格子 + 四块汇总"，比参数面板（0.6）需要更多地方；
 * 内部自带 `verticalScroll`，所以再小也读得到全部内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneStatsSheet(state: AppState, t: (String) -> String, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp),
        ) {
            RefSheetTitle(title = t("stats.title"), onClose = onDismiss)
            UsageStatsScreen(state = state, t = t, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * 顶栏里滑出来的**胶囊搜索框**（角色图鉴用）。
 *
 * 为什么做成胶囊而不是占一整行：工具页顶栏高度有限，胶囊更贴合"画廊风"，
 * 也让「点放大镜 → 滑出 → 输入 → 回车收起」这条动线很干净。
 */
@Composable
private fun SearchCapsule(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSubmit: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = LocalRef.current.text,
            fontSize = 14.sp,
        ),
        cursorBrush = SolidColor(LocalRef.current.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(50))
            .background(LocalRef.current.field)
            // 网页 `.search-capsule`：field 底 + 强描边
            .border(1.dp, LocalRef.current.borderStrong, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        color = LocalRef.current.faint,
                    )
                }
                inner()
            }
        },
    )
}

/** 角色图鉴用的一层包装（搜索词/标语/回车行为都在 AppState 里）。 */
@Composable
private fun SearchCapsule(state: AppState, t: (String) -> String) {
    SearchCapsule(
        value = state.animaQuery,
        onValueChange = { state.animaSetQuery(it) },
        placeholder = if (state.animaMode == "copyrights") {
            t("anima.searchPlaceholderSeries")
        } else {
            t("anima.searchPlaceholder")
        },
        onSubmit = {
            state.animaSearch(reset = true)
            state.animaCloseSearch()
        },
    )
}

/**
 * **生图页浮在画布上的那两样小事**（用户 2026-09-24：「生图页的顶栏去除，都变为小按钮，
 * 呼出侧边栏的三横变为圆形按钮，额度像电脑一样变成胶囊」✓）。
 *
 * 顶栏整个去掉之后，原来长在栏里的两样搬到这里 ✓：
 *  · **左**：一颗**圆形**小按钮（半透明 + 描边，和电脑线那套玻璃同源 ✓）—— 点开侧边栏 ✓；
 *  · **右**：`AccountChip`（已经改成**胶囊** ✓）—— 点一下刷新额度 ✓。
 *
 * ⚠️ 画在 `pager` **之后** ✓（同一个 `Box` 里后画的在上 ✓）⇒ 它盖在画布上、不占布局高度 ✓
 *（这正是"顶栏去除"要的效果 ✓：画布往上长一截 ✓）。
 * ⚠️ 尺寸按**手指**给 ✓：圆形按钮 44dp ✓（`IconButton` 默认 48 在顶栏里合适，
 *    浮在画布上就显得笨 ✓）、胶囊高度由 `AccountChip` 自己的内边距决定 ✓。
 *
 * @param modifier 调用方给的对齐 / 层级 ✓（**必须**排在 pager 之后 + `.zIndex` ✓，见调用处的说明 ✓）。
 */
// ⚠️ 用户 2026-09-26：「**去除底栏**」—— 原来这里有一条常驻底部栏 `PhoneTabBar`
//    （生图 / 图库 / 工具 三格）。整条连同它的实现一起删掉：导航回到抽屉 +
//    页面之间的**手指左右滑**（生图 ↔ 图库 ↔ 工具）。留着实现只会是死代码。
@Composable
private fun GenerateFloatingBars(
    state: AppState,
    t: (String) -> String,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            // ⚠️ 不吃状态栏内边距 ✗：这一层在 `Scaffold` 的 `inner` 里面 ✓（`padding(inner)` 已经把
            //    状态栏那条让出来了 ✓）—— 再加一份会往下缩一截 ✓。
            .padding(start = 12.dp, end = 12.dp, top = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 网页 v3 `.fab`：44 圆角方形（14）+「侧栏」图标（外框 + 左侧一条主题色实心栏）
        Surface(
            onClick = onOpenDrawer,
            shape = RoundedCornerShape(14.dp),
            color = LocalRef.current.glass(),
            contentColor = LocalRef.current.text,
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, LocalRef.current.border),
            modifier = Modifier.size(44.dp),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val ink = LocalRef.current.text
                val pane = LocalRef.current.accent.copy(alpha = 0.9f)
                Canvas(Modifier.size(22.dp)) {
                    val u = size.width / 24f
                    drawRoundRect(
                        color = ink,
                        topLeft = Offset(3.5f * u, 4.5f * u),
                        size = Size(17f * u, 15f * u),
                        cornerRadius = CornerRadius(3.5f * u, 3.5f * u),
                        style = Stroke(width = 1.8f * u),
                    )
                    drawRoundRect(
                        color = pane,
                        topLeft = Offset(6.5f * u, 7.5f * u),
                        size = Size(3.5f * u, 9f * u),
                        cornerRadius = CornerRadius(1.2f * u, 1.2f * u),
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        CanvasModeToggle(mode = state.canvasMode, onMode = state::chooseCanvasMode, t = t)
        Spacer(Modifier.weight(1f))
        AccountCapsule(state, t)
    }
}

/**
 * **画布模式切换**（用户 2026-09-29：「手机无限画布的入口和电脑一样，在侧边栏按钮旁加一个切换键」）。
 * 普通 / 无限画布两档（同电脑线参考皮肤的分段控件：外壳玻璃 + 描边，选中 = accentSoft 底 + accent 字）。
 */
@Composable
private fun CanvasModeToggle(mode: Int, onMode: (Int) -> Unit, t: (String) -> String) {
    val ref = LocalRef.current
    val outer = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .height(44.dp)
            .shadow(2.dp, outer)
            .clip(outer)
            .background(ref.glass())
            .border(1.dp, ref.border, outer)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf("canvasMode.normal", "canvasMode.infinite", "canvasMode.comic").forEachIndexed { index, key ->
            val on = index == mode
            val seg = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .fillMaxHeight()
                    .clip(seg)
                    .background(if (on) ref.accentSoft else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, ref.borderStrong, seg) else Modifier)
                    .clickable { onMode(index) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t(key),
                    fontSize = 13.5.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) ref.accent else ref.muted,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 排序图标（三条渐短的横线）。`material-icons-core` 里没有排序图标，
 * 为一个图标引 material-icons-extended（几 MB）不值得，所以直接画。
 */
@Composable
private fun SortIconButton(contentDescription: String, onClick: () -> Unit) {
    val color = LocalRef.current.muted
    val stroke = 2.dp
    IconButton(onClick = onClick) {
        Canvas(Modifier.size(24.dp)) {
            // 三条（用户要求：原来是四条）
            val widths = listOf(1f, 0.68f, 0.34f)
            val step = size.height / (widths.size + 1)
            widths.forEachIndexed { i, fraction ->
                val y = step * (i + 1)
                drawLine(
                    color = color,
                    start = Offset(0f, y),
                    end = Offset(size.width * fraction, y),
                    strokeWidth = stroke.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * 网页 v3 `.acct-chip`：高 44、圆角 22、玻璃底 + 描边；
 * 左 32 渐变圆徽章（= 生成按钮同色）装实心闪电（run-ink），右两行：余额 15/650、`Opus · V5 额度 N%`（10.5 faint，档位 accent）；
 * 底边 2dp 额度条（左右内缩 20，strip 轨道，run-a → run-b 渐变）。点一下 = 刷新余额（徽章外转一圈 accent 弧线）。
 */
@Composable
private fun AccountCapsule(state: AppState, t: (String) -> String) {
    val account = state.account
    val params = state.params
    val ref = LocalRef.current
    val skin = ref.runSkin()
    val tier = account.tierName ?: "API"
    val balance = account.anlasBalance?.let { "%,d".format(it) } ?: "—"
    val showV5Allowance = account.tierLevel == 3 && params.model.startsWith("nai-diffusion-5-")
    val remainingPercent = account.opusUsage
        ?.takeIf { showV5Allowance }
        ?.let { if (it.isNegative) 0f else it.percent.coerceIn(0.0, 100.0).toFloat() }
    val spin = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Surface(
        onClick = {
            state.refreshAnlas()
            scope.launch {
                spin.snapTo(0f)
                spin.animateTo(1f, tween(800, easing = LinearEasing))
            }
        },
        shape = RoundedCornerShape(22.dp),
        color = ref.glass(),
        contentColor = ref.muted,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, ref.border),
        modifier = Modifier.height(44.dp),
    ) {
        Box {
            Row(
                Modifier.fillMaxHeight().padding(start = 6.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .drawBehind {
                            drawCircle(runGradient(skin, size))
                            val p = spin.value
                            if (p > 0f && p < 1f) {
                                val w = 2.dp.toPx()
                                val o = 3.dp.toPx() - w / 2f
                                drawArc(
                                    color = ref.accent,
                                    startAngle = -135f + 360f * p,
                                    sweepAngle = 90f,
                                    useCenter = false,
                                    topLeft = Offset(-o, -o),
                                    size = Size(size.width + 2 * o, size.height + 2 * o),
                                    style = Stroke(width = w, cap = StrokeCap.Round),
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (account.stale) Icons.Filled.Warning else BoltIcon,
                        contentDescription = null,
                        tint = skin.runInk,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (state.hasToken) {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            balance,
                            color = ref.text,
                            fontSize = 15.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight(650),
                            maxLines = 1,
                        )
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = ref.accent, fontWeight = FontWeight(650))) { append(tier) }
                                if (remainingPercent != null) append(" · V5 额度 ${remainingPercent.toInt()}%")
                            },
                            color = ref.faint,
                            fontSize = 10.5.sp,
                            lineHeight = 12.sp,
                            maxLines = 1,
                        )
                    }
                } else {
                    Text(t("generate.notConfigured"), color = ref.muted, fontSize = 12.5.sp, maxLines = 1)
                }
            }
            if (remainingPercent != null) {
                val fraction = (remainingPercent / 100f).coerceIn(0f, 1f)
                Canvas(Modifier.matchParentSize()) {
                    val h = 2.dp.toPx()
                    val inset = 20.dp.toPx()
                    val w = size.width - inset * 2
                    if (w <= 0f) return@Canvas
                    val top = size.height - h
                    drawRect(ref.strip, topLeft = Offset(inset, top), size = Size(w, h))
                    if (fraction > 0f) {
                        drawRect(
                            brush = Brush.horizontalGradient(listOf(skin.runA, skin.runB), startX = inset, endX = inset + w * fraction),
                            topLeft = Offset(inset, top),
                            size = Size(w * fraction, h),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountChip(state: AppState, t: (String) -> String) {
    val account = state.account
    val params = state.params

    val tier = account.tierName ?: "API"
    val balance = account.anlasBalance?.toString() ?: "—"

    // 与参考实现一致：只有 Opus 档 + V5 模型才显示 V5 免费额度
    val showV5Allowance = account.tierLevel == 3 && params.model.startsWith("nai-diffusion-5-")
    val usage = account.opusUsage
    val suffix = if (showV5Allowance && usage != null) {
        val percent = if (usage.isNegative) 0 else usage.percent.coerceIn(0.0, 100.0).toInt()
        " · V5 $percent%"
    } else {
        ""
    }

    // 用户 2026-09-24：「**额度像电脑一样变成胶囊**」✓ ——
    // 以前是一颗 `TextButton`（方方正正、带默认内边距 + 最小宽度 ✗），
    // 现在照电脑线那颗「余额胶囊」的样子：**圆角胶囊 + 半透明底 + 描边**，
    // 点一下仍然是刷新额度（`refreshAnlas` ✓ 功能没动 ✓）。
    //
    // ⚠️ 用户 2026-09-26：「**余额按钮下做一个进度条，看剩余的百分比，进度条采用渐变形式**」——
    //    所以整颗变成一列：[胶囊][3dp][进度条]。关于"剩余的百分比"取哪个数：
    //    就用**胶囊里那个 `V5 N%`**（同一个 `account.opusUsage`），不另算一套口径 ——
    //    这样"字是 62%、条也是 62%"，不会出现两个数打架。
    //    **条和字共用同一个条件**（Opus + V5 模型才算数）：字在不显示的时候条也不显示，
    //    免得出现"没写百分比、却挂着一条进度条"这种对不上的画面。
    val remainingPercent = usage
        ?.takeIf { showV5Allowance }
        ?.let { if (it.isNegative) 0f else it.percent.coerceIn(0.0, 100.0).toFloat() }
    val ref = LocalRef.current
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Surface(
            onClick = { state.refreshAnlas() },
            shape = RoundedCornerShape(50),
            color = ref.glass(),
            contentColor = ref.muted,
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, ref.border),
        ) {
            // 网页 v2：进度条收进胶囊底部 —— 3dp 通栏，被胶囊圆角裁切；有条时下内距 7 → 9
            Box {
            Row(
                Modifier.padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = 7.dp,
                    bottom = if (remainingPercent != null) 9.dp else 7.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (account.stale) Icons.Filled.Warning else Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = if (account.stale) ref.warning else ref.muted,
                    modifier = Modifier.size(14.dp),
                )
                // 网页：`↻ Opus · 12,480 · V5 86%` —— 档位 accent 600、余额 text 600、其余 muted
                val text = if (state.hasToken) {
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = ref.accent, fontWeight = FontWeight.SemiBold)) { append(tier) }
                        append(" · ")
                        withStyle(SpanStyle(color = ref.text, fontWeight = FontWeight.SemiBold)) { append(balance) }
                        append(suffix)
                    }
                } else {
                    AnnotatedString(t("generate.notConfigured"))
                }
                Text(
                    text = text,
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                    color = ref.muted,
                    maxLines = 1,
                )
            }
            if (remainingPercent != null) {
                RemainingPercentBar(percent = remainingPercent, modifier = Modifier.matchParentSize())
            }
            }
        }
    }
}

/**
 * 余额胶囊下面那条**剩余额度**进度条（用户 2026-09-26：「看剩余的百分比，进度条采用渐变形式」）。
 *
 * ## 为什么渐变要"铺满整条轨道、只露前面一截"
 *
 * 渐变的坐标给的是**整条轨道**（`startX = 0`、`endX = 轨道宽`），而画的长度只有 `percent`
 * 那么长 ⇒ 剩得越少，露出来的越是渐变**前段**那一截。
 * 换句话说"条在缩短"和"颜色在变深"是同一件事（电池那种读法），
 * 而不是"条缩短了但颜色永远一样"。
 *
 * ## 用哪两个颜色
 *
 * `primary → tertiary`（主题里那一对），所以**所有配色预设都跟着走** ——
 * 用户自己在设置里换主色 / 换配色时，这条也跟着变，不用另配一套。
 *
 * 高度 4dp：它是**贴在胶囊正下方**的附属信息，不能喧宾夺主（顶栏本来就只有 64dp 高）。
 */
@Composable
private fun RemainingPercentBar(percent: Float, modifier: Modifier = Modifier) {
    val fraction = (percent / 100f).coerceIn(0f, 1f)
    // 网页 v2 `.quota-bar`：胶囊底部 3dp 通栏、strip 轨道、accent → brandStar 渐变（圆角由胶囊裁）
    val track = LocalRef.current.strip
    val start = LocalRef.current.accent
    val end = LocalRef.current.brandStar()
    Canvas(modifier) {
        val h = 3.dp.toPx()
        val top = size.height - h
        drawRect(color = track, topLeft = Offset(0f, top), size = Size(size.width, h))
        if (fraction > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(start, end),
                    startX = 0f,
                    endX = size.width,
                ),
                topLeft = Offset(0f, top),
                size = Size(size.width * fraction, h),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
        }
    }
}
