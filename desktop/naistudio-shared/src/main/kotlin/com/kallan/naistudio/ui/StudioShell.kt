package com.kallan.naistudio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
// 「NAI」的 I 常态蓝色：只给那一个字母上色 ✓（见 BrandTitle ✓）
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.HistoryItem
import com.kallan.naistudio.models.ToolCatalog
import com.kallan.naistudio.models.ToolEntry
import com.kallan.naistudio.models.ToolSurface
import com.kallan.naistudio.platform.LocalPlatform
import com.kallan.naistudio.platform.LocalUiHost
import com.kallan.naistudio.screens.CanvasEditorScreen
import com.kallan.naistudio.screens.ComicModeScreen
import com.kallan.naistudio.screens.GalleryScreen
import com.kallan.naistudio.screens.GenerateScreen
import com.kallan.naistudio.screens.ProfileScreen
import com.kallan.naistudio.screens.SettingsScreen
import com.kallan.naistudio.screens.TAB_CHARACTERS
import com.kallan.naistudio.screens.TAB_PARAMS
import com.kallan.naistudio.screens.TOP_BAR_HEIGHT
import com.kallan.naistudio.screens.ToolContent
import com.kallan.naistudio.screens.ToolsScreen
import com.kallan.naistudio.screens.StatsSurfaceStyle
import com.kallan.naistudio.screens.UsageStatsScreen
import com.kallan.naistudio.screens.panelTabTitleKey
import com.kallan.naistudio.state.AppState
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 应用外壳。
 *
 * **导航只有左侧边栏（抽屉）一种形态**：原来的底部导航栏已删除，它把 14 项搬进抽屉，
 * 抽屉里**只有那 14 项**，不再放别的功能；宽度是手机屏幕的一半。
 *
 * **生成 → 图库 是一个 `HorizontalPager`**：左右拖动完全跟手、松手自动吸附，连续来回滑也顺滑。
 * 图库页屏蔽「左滑开侧边栏」，把左缘让给「滑回文生图」；工具 / 设置仍由抽屉切换。
 *
 * 顶部栏左上角是汉堡按钮；右上角在生成页显示账号状态，**在图库页换成「排序与显示」按钮**
 * （图库页不显示余额）。
 *
 * 角色分区编辑器在生成页的底部抽屉里；风格预设仍在「选择预设」里。
 */
private const val GALLERY_INDEX = 1

/**
 * 「统计」那扇**小毛玻璃窗**的尺寸 —— 用户 2026-09-24：选了 **⑨c · 苹果风·压扁** ✓
 * ⇒ 尺寸跟着那一档走 **300 × 380** ✓（对比页上"压扁版"就是这两个数 ✓；圆角 20dp ✓）。
 *
 * ⚠️ 尺寸改过两次 ✗：先是 520×640（照工具窗口 ✓）→ 340×440（用户挑了 C 档 ✓）→
 * 现在是 **300×380** ✓（装配苹果风那一档 ✓）。
 */
private val STATS_WINDOW_WIDTH = 300.dp
private val STATS_WINDOW_HEIGHT = 380.dp

/** 统计那扇窗的圆角（⑨c 用 20dp ✓ —— 苹果那种"更圆的方框" ✓）。 */
private val STATS_WINDOW_CORNER = 20.dp

/** 统计那扇窗与按钮之间留的缝 ✓（贴太紧像是按钮的一部分 ✓）。 */
private val STATS_WINDOW_GAP = 8.dp

/**
 * **侧边栏图标的大小**（用户 2026-09-24：「**侧边栏的图标加大一点**」✓）。
 *
 *  · [SIDEBAR_ICON] = 页面图标（原来吃 `Icon` 默认的 24dp ✓，现在 28dp ✓）；
 *  · [SIDEBAR_ICON_SMALL] = 底部那两颗（统计 / 深浅色 ✓，原来是 20dp ✓，现在 24dp ✓）——
 *    两颗留在同一档，免得一排图标里高矮不一 ✓（它们本来就是"次级"那一组 ✓）。
 *
 * ⚠️ 收纳态那条 68dp 窄栏用的是同一组常量 ✓（那里原来是 20dp ✓）：两边一个尺寸 ✓，
 * 展开 / 收纳切来切去才不跳 ✓。
 */
private val SIDEBAR_ICON = 28.dp
private val SIDEBAR_ICON_SMALL = 24.dp

/** 统计那扇窗离窗口边缘至少留这么多 ✓（和工具窗口同一条兜底 ✓）。 */
private val STATS_WINDOW_MARGIN = 12.dp

/**
 * **统计窗的毛玻璃** —— 用户 2026-09-24 在对比页里挑定了 **⑨c · 苹果风·压扁** ✓。
 *
 * ## 为什么会有"塑料感"（先说根因 ✓）
 *
 * 项目里的真毛玻璃是**两步** ✓：① 面板半透明 ✓；② **把面板底下的那层内容整体糊掉** ✓
 *（`Modifier.blur` ✓，底部抽屉就是这么做的 ✓）。而统计窗是 `Popup` ✓ —— **独立的浮层** ✗，
 * 糊不到底下的画布 ✓ ⇒ 它一直只有第 ① 步：一层 78% 不透明的 `surface` 盖在画布上 ✓。
 * 半透明但**不模糊**，看着就是"一块塑料片" ✓ —— 这正是用户说的那个感觉 ✓。
 *
 * ## 苹果风那五件事，这一档做到了哪几件
 *
 * | 苹果风的要素 | 这一档 |
 * | --- | --- |
 * | 半透明面 | ✓ 42% ✓ |
 * | **极细亮边 + 顶边内高光**（玻璃的厚度 ✓） | ✓ 1dp 亮边 + 里面叠一层"上亮下透"的渐变 ✓ |
 * | **大而软的投影** | ✓ 16dp ✓ |
 * | **左上角一团柔光** | ✓ 径向渐变 ✓ |
 * | 字重加粗一档、字色压深 | ✓ ✓ |
 * | **背后真的糊**（苹果风的地基 ✗✗） | ⛔ **没做** —— 见下面那条 ⚠️ |
 *
 * ## ⚠️ 如实交代：这一档**不糊背后** ✗
 *
 * 对比页上那张 ⑨c 之所以"背后是糊的"，是因为 CSS 有 `backdrop-filter` ✓；
 * **Compose 没有这个属性** ✗，而统计窗是 `Popup` ✓（独立浮层 ✓）⇒ 想真糊，得把它**从弹层挪到窗口层**、
 * 再给背后那层内容加 `Modifier.blur` ✓ —— 那是动生成页布局的大改 ✓，用户**没有**点这一条 ✓，
 * 所以这一版只把"看得见的那四样"（亮边 / 内高光 / 投影 / 柔光）做实 ✓，糊的这一步留着 ✓。
 */
enum class StatsGlassStyle {
    /** 偏实（88% ✓）：最接近"不透明面板"，对比时用的 ✓。 */
    Plain,

    /** 78% ✓，无描边无投影 ✓ —— 用户说的"有点塑料"就是它 ✓（留在表里当回落 ✓）。 */
    Current,

    /** 更透 + 亮边 + 投影：像一块**厚玻璃** ✓。 */
    Crystal,

    /** 最透 + 窗内一层微光 + 投影 + 内容摊平：像**磨砂** ✓。 */
    Frost,

    /**
     * **苹果风·压扁**（✅ **用户 2026-09-24 选定的这一款** ✓）——
     * 面 42% + 1dp 亮边 + 顶边内高光 + 大软投影 + 左上柔光 + 字重加粗 ✓；
     * 窗子尺寸也跟着它走 **300 × 380** ✓（见 [STATS_WINDOW_WIDTH] ✓）。
     */
    Apple,

    /** 网页参考稿的青蓝玻璃浮层。 */
    Reference,
}

/** 统计窗"外形"的可调量（面 / 描边 / 投影 / 内层 ✓）—— 由 [StatsGlassStyle] 派生 ✓。 */
private data class StatsGlassLook(
    val alpha: Float,
    val border: BorderStroke?,
    val shadow: Dp,
    /** 窗内那层"斜向微光"（D 磨砂用 ✓）。 */
    val innerSheen: Boolean,
    /** 窗内那层"上亮下透 + 左上柔光"（⑨c 苹果风用 ✓，也就是苹果的内高光 ✓）。 */
    val appleGloss: Boolean,
)

/** 把款式翻译成具体数值（**唯一一处** ✓）。 */
private fun statsGlassLook(style: StatsGlassStyle): StatsGlassLook = when (style) {
    StatsGlassStyle.Plain -> StatsGlassLook(
        alpha = 0.88f,
        border = null,
        shadow = 0.dp,
        innerSheen = false,
        appleGloss = false,
    )

    StatsGlassStyle.Current -> StatsGlassLook(
        alpha = NaiGlass.PANEL_ALPHA,
        border = null,
        shadow = 0.dp,
        innerSheen = false,
        appleGloss = false,
    )

    StatsGlassStyle.Crystal -> StatsGlassLook(
        alpha = 0.62f,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
        shadow = 10.dp,
        innerSheen = false,
        appleGloss = false,
    )

    StatsGlassStyle.Frost -> StatsGlassLook(
        alpha = 0.55f,
        border = null,
        shadow = 10.dp,
        innerSheen = true,
        appleGloss = false,
    )

    // ⑨c：对比页上那一套的 Compose 版（背后不糊 ✓，其余四样都做 ✓）
    StatsGlassStyle.Apple -> StatsGlassLook(
        alpha = 0.42f,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.55f)),
        shadow = 16.dp,
        innerSheen = false,
        appleGloss = true,
    )

    StatsGlassStyle.Reference -> StatsGlassLook(
        alpha = 0.70f,
        border = null,
        shadow = 12.dp,
        innerSheen = false,
        appleGloss = false,
    )
}

/**
 * 统计窗当前用哪一款 ✓ —— **用户已选定 [StatsGlassStyle.Apple]（⑨c）** ✓，
 * 所以这里不再是"审查用的开关" ✓，就是它本身 ✓（菜单里那几条对照项已经撤掉 ✓）。
 */
private val statsGlassStyle = StatsGlassStyle.Apple

/** 工具页在 pager 里的下标（侧边栏「工具」下拉里的工具名要翻到这一页）。*/
private const val TOOLS_INDEX = 2

/**
 * ⛔ **高级漫画那一整屏已经退役**（用户 2026-09-23：「**高级漫画放到主页**，按钮变为无限画布旁边的漫画」✓）。
 *
 * 原来这里有个 `COMIC_INDEX = 3`（pager 里单独一页 `ComicModeScreen` ✓）；现在：
 *  · 漫画 = 生图页画布区那一档（画布左上角 / 中控台里那排「普通 / 无限画布 / 漫画」✓）；
 *  · 底板 = 主页那张画布 ✓、图层与气泡工具条 = 中控台 ✓、「跑这一页」= 底栏那颗「生成图片」✓。
 * ⇒ 侧边栏不再列这一项 ✓、pager 少一页 ✓（设置从前面的 4 回到 **3** ✓）。
 */

/**
 * 设置页在 pager 里的下标。
 *
 * ⚠️ **这个数改过三次**（别照老注释/老代码抄 ✗）：
 *  · 用户 2026-09-20 要求「新开一页，旧模式并存」—— 高级漫画在「工具」之后插了一页，
 *    设置 / 我的各往后挪一位 → **4** ✓；
 *  · 用户 2026-09-20 改口「将画布放在首页…」—— 那一页删掉，这里**挪回 3** ✗；
 *  · 用户 2026-09-20 又改回「还是把高级漫画模式放回侧边栏，其余不变」—— 那一页加回来，
 *    于是这里**又回到 4** ✓；
 *  · **用户 2026-09-23：「高级漫画放到主页」** ✓ —— 那一整屏**退役** ✗，
 *    漫画变成生图页画布区的「漫画」那一档 ✓ ⇒ 这里**回到 3** ✓。
 *
 * 凡是用**写死数字**指设置页的地方都要跟着改（余额小窗口里那颗「配置 Token」用的是
 * 这个常量本身 ✓；`:2627` 那处以前踩过"写死 3"的坑 ✓）。
 */
private const val SETTINGS_INDEX = 3

/** 抽屉：横向甩动超过这个速度（px/s）就直接开/关，不看拖了多远）。*/
private const val FLING_VX = 700f

/** 两次返回退出的时间窗（毫秒）。*/
private const val EXIT_INTERVAL_MS = 2000L

/** 常驻侧栏"收纳成图标条"这个偏好的存储键（电脑上落在 `prefs.json`）。*/
private const val KEY_SIDEBAR_COLLAPSED = "shell.sidebarCollapsed"

/**
 * 常驻侧栏**展开宽度**（整数 dp）的存储键 — 和"收纳"同一个口径、同一个 `prefs.json`。
 *
 * 为什么是 `getInt` / `putInt` 而不是题目里说的 `getFloat` / `putFloat`？
 * 共用层的 `KeyValueStore` 只暴露了 `getString` / `getBoolean` / `getInt`（照 SharedPreferences
 * 那几条做的，见 `platform/Platform.kt`），**没有浮点那对**；为了一个宽度去动平台接口，
 * 连带改两端实现 + 手机线，代价和风险都不划算。宽度本来就按整数 dp 算。
 *（`GenerateScreen` 里那条提示词栏分隔条存的就是 `generate.leftPanelWidthDp` 整数），
 * 存整数还省掉了浮点字面量的误差。想改用 float 的话：给 `KeyValueStore` 加两个方法即可，
 * 键名不用动（JSON 里存的是数字，`org.json` 读回 float 也没问题）。
 */
private const val KEY_SIDEBAR_WIDTH = "shell.sidebarWidth"

/** 侧栏展开宽度的默认值。*/
/**
 * 侧边栏**默认**宽度（展开态仍是"图标在左、名字在右"的横排，所以默认值不变 ✓）。
 *
 * ⚠️ 用户 2026-09-26 更正过口径：「**展开时不变，是收纳时在下面显示名字**」——
 * 所以"图标在上、名字在下"只用在**收纳态**（见 `CollapsedNavItem`），
 * 展开态保持原样（默认宽度也就没必要改 ✗ 之前那版改小了，已还原）。
 */
private const val DEFAULT_SIDEBAR_WIDTH_DP = 280

/** 侧栏展开宽度的**下限**：再窄放不下"图标 + 文字"。*/
private val SidebarMinWidth = 200.dp

/** 侧栏展开宽度的**上限**（还要再跟窗宽一起夹一次，见 `sidebarMaxWidth`）。*/
private val SidebarMaxWidth = 420.dp

/** 侧栏右边缘那条**可拖把手**的宽度（看起来是一条细线，热区按它算）。*/
private val SidebarHandleWidth = 6.dp

/** 抽屉：速度不够时，拉出这个比例（0.2 = 20% 宽度 ≈ 144px）也认。*/
private const val DRAG_TO_OPEN = 0.2f

/**
 * **手机那套抽屉**的宽度：窗口宽度的一半（上限 280dp，免得平板上横跨半个屏）。
 *
 * ⚠️ 常驻侧栏（平板 / 电脑）**不再用这个了** — 它现在走可拖、可落盘的
 * `sidebarWidthState`（见 [StudioShell] 里那段）。这里只留给浮层抽屉。
 */
@Composable
private fun drawerWidth(): Dp {
    // 窗口宽度走共用的 LocalWindowSize（原来读 LocalConfiguration.screenWidthDp，那是 Android 专属）
    return minOf(LocalWindowSize.current.width / 2, 280.dp)
}

/**
 * **收纳后的侧栏宽度**：只放得下 logo 和图标。
 *
 * 68dp = 24dp 图标 + 两侧各 22dp 的呼吸 — 和 Windows 上那种折叠导航一个量级。
 * 这一条只在常驻侧栏（平板/电脑）里用，手机那套抽屉永远是展开的。
 */
private val CollapsedSidebarWidth = 68.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioShell(state: AppState, appName: String) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val updaterPlatform = com.kallan.naistudio.platform.LocalPlatform.current
    ApplicationUpdateHost(updaterPlatform.appVersion, "Windows", java.io.File(updaterPlatform.paths.filesDir, "updates"), updaterPlatform::installApplicationUpdate, state.settings.language)
    val language = state.settings.language
    val t: (String) -> String = { key -> RuntimeText.text(language, key) }

    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var galleryControls by rememberSaveable { mutableStateOf(false) }
    // 底部面板正在用（打开或正在拉）时，屏蔽侧边栏手势
    var bottomPanelActive by remember { mutableStateOf(false) }
    // 生成页在遮罩模式下会置位：手指属于遮罩画布，翻页手势得让位。
    var contentGestureLocked by remember { mutableStateOf(false) }
    // 图库页：当前看的那张 + 批量删除模式
    var gallerySelection by remember { mutableStateOf<HistoryItem?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmingBatchDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 返回键 / 轻提示 / 退出：都走宿主能力（手机是 BackHandler + Toast + Activity.finish）
    // 电脑端（ui-backhandler + 自绘浮层 + 关窗口），共用代码不碰这些平台 API。
    val uiHost = LocalUiHost.current
    var lastBackPress by remember { mutableLongStateOf(0L) }

    /**
     * 「深/浅色」那颗按钮的中心（**窗口坐标**）——主题圆形扩散动画的圆心。
     *
     * 声明在这一层而不是按钮自己那儿：按钮画在侧边栏内容里，而遮罩画在 `MainActivity`
     * 最外层，两边不在同一个组合子树里。`onGloballyPositioned` 报的是窗口坐标，
     * 正好也是遮罩那层用的坐标系。
     */
    var themeButtonCenter by remember { mutableStateOf<Offset?>(null) }

    /**
     * **「统计」那一页开着没**（用户 2026-09-23 ✓）。
     *
     * ⚠️ 它是一个**盖在上面的整页浮层** ✗ 不是 pager 里的第 7 页 ✓ ——
     * 加一页要动 `tabs` / 页码常量 / 翻页手势锁 / 分离面板那几张索引表 ✓，
     * 而统计是一次性看一眼就关的东西 ✓（它是"从侧边栏点开的浮层"，不是"和生图并列的一栏"✓）。
     */
    var statsOpen by remember { mutableStateOf(false) }

    /** 主题扩散动画的统一入口（抓屏 + 遮罩都在 `MainActivity` 提供）。*/
    val switchTheme = LocalThemeSwitch.current

    val tabs = listOf(
        t("generate.titleTextToImage") to Icons.Filled.Home,
        // ⚠️ 用户 2026-09-23：「**把侧边栏的图库图标变成文件夹样式**」✓ ——
        // 原来用的是 `Icons.AutoMirrored.Filled.List`（一条条横线，像"清单"不像"图库"✗）✓；
        // `FolderIcon` 是本项目自绘的文件夹（不引 material-icons-extended ✓，见 `AppIcons.kt` ✓）。
        t("gallery.title") to FolderIcon,
        t("tools.title") to Icons.Filled.Build,
        // ⛔ 高级漫画**不再单独占一页** ✗（用户 2026-09-23：「高级漫画放到主页，
        // 按钮变为无限画布旁边的漫画」✓）—— 它现在是生图页画布区那一档 ✓，
        // 底板 = 主页画布、图层与气泡工具条 = 中控台、「跑这一页」= 底栏「生成图片」✓。
        t("settings.title") to Icons.Filled.Settings,
        // 两个版本都叫「我的」（纯净版里只是页内卡片标题变成「API」，页名不变）。
        t("profile.title") to Icons.Filled.AccountCircle,
    )

    // **每一页都放进 pager**（页数取 `tabs.size`，本批加上「高级漫画」是 6 页 ✓）：
    // 这样「图库 / 工具 / 高级漫画 / 设置 → 文生图」也能走 animateScrollToPage，
    // 拿到和「图库滑回文生图」完全一样的滑动动画（早前工具和设置是直接换内容，没有过渡）。
    val pagerState = rememberPagerState(initialPage = index.coerceIn(0, tabs.lastIndex)) { tabs.size }

    // 单向同步：pager 是页码的唯一真源
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page -> if (index != page) index = page }
    }

    // 手势滑动**只允许在文生图 → 图库之间**：工具 / 设置只能从抽屉进、用返回箭头出）。
    // 用 nestedScroll 的 onPreScroll/onPreFling 把不允许方向的位移整段吃掉。
    // `contentGestureLocked`：生成页进入遮罩模式时置位 — 那时手指是用来涂遮罩的，
    // 翻页手势必须先让位（否则横着涂两下就翻到图库去了）。
    val swipeGate = remember(pagerState) {
        object : NestedScrollConnection {
            private fun allowed(): Boolean {
                // ⚠️ **底部面板打开时禁止翻页**（用户要求）。
                // 早先这里只看"当前是哪一页"，生图页永远"可翻" — 所以面板开着左滑照样
                // 滑去图库。面板打开时这段横向手势应该留给面板自己（切换 提示词/角色/参数）。
                if (bottomPanelActive) return false
                return when (pagerState.currentPage) {
                    0 -> true
                    GALLERY_INDEX -> true
                    else -> false
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && contentGestureLocked) {
                    return Offset(available.x, 0f)
                }
                // ⚠️ 不允许翻页的页面（工具 / 设置）在**这里不吃**横向位移。
                // 工具页里有的是横向滚动的东西（排序胶囊、筛选胶囊），必须让它们先滚。
                // 剩下的位移在 onPostScroll 里兜住，翻页照样被挡住。
                // （早先在 pre 阶段整段吃掉 →"工具页的胶囊"超出屏幕又滑不动，就是这个原因。）
                if (source == NestedScrollSource.UserInput && !allowed()) {
                    return Offset.Zero
                }
                // 图库页只准往右（回文生图），文生图页只准往左（去图库）
                if (source == NestedScrollSource.UserInput) {
                    val toGallery = pagerState.currentPage == 0 && available.x < 0f
                    val toGenerate = pagerState.currentPage == GALLERY_INDEX && available.x > 0f
                    if (!toGallery && !toGenerate) return Offset(available.x, 0f)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // 子级（内部横向滚动）先吃，剩下的横向位移由这里吃掉 — 挡住翻页
                if (source == NestedScrollSource.UserInput && !allowed() && available.x != 0f) {
                    return Offset(available.x, 0f)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // ⚠️ 这里**只能吃横向**。之前返回整个 `available`（含 y），
                // 结果把竖向滚动列表松手后的惯性也吞了 — 表现就是"设置页下滑特别跟手、没有惯性"。
                // nestedScroll 的约定：返回值是"本父级消费掉的速度"，所以横向之外必须原样放行。
                if (contentGestureLocked) return Velocity(available.x, 0f)
                // 不允许翻页的页面：先让内部滚动飞（onPostFling 再兜住）
                if (!allowed()) return Velocity.Zero
                val atBoundary = when (pagerState.currentPage) {
                    GALLERY_INDEX -> available.x < 0f
                    0 -> available.x > 0f
                    else -> true
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
    //  · 进工具 / 设置 → 从右边滑入；从工具 / 设置退回主界面 ← 从左边滑回；其余淡入淡出
    //  · +1：**新页面从右边滑入**（进工具 / 设置）— 瞬时换页 + 把整块内容从右侧 translateX 归位。
    //        而不是让 pager 一路平移过去（后者会顺带组合中间的图库页，跨两页时明显卡顿）
    //  · -1：**从左边滑回**（工具 / 设置退回主界面）— 同上，方向相反。
    //  ·  0：缓入缓出的淡入淡出（文生图 → 图库、图库退回主界面）。
    val screenWidthPx = with(LocalDensity.current) {
        LocalWindowSize.current.width.toPx()
    }
    var contentAlpha by remember { mutableFloatStateOf(1f) }
    var slideX by remember { mutableFloatStateOf(0f) }
    // 只在动画期间挂 graphicsLayer：全屏图层常驻等于每帧多画一层 1440×3200 的纹理，
    // 静止时白白吃带宽（切页卡顿的元凶之一）。动画起止各改一次，不会每帧重组。
    var animating by remember { mutableStateOf(false) }
    var navJob by remember { mutableStateOf<Job?>(null) }

    // 进工具 / 设置 → 从右边滑入；从工具 / 设置退回主界面 ← 从左边滑回；
    // **图库 → 生图 也走"从左边滑回"**（用户要求：和手指右滑返回的动画统一）。
    // 早先这里落到 else 分支 = 淡入淡出，所以点头上的返回箭头和滑动返回看起来不一样）。
    val directionFor: (Int, Int) -> Int = { from, to ->
        when {
            to > GALLERY_INDEX -> 1
            from >= GALLERY_INDEX && to == 0 -> -1
            from > GALLERY_INDEX && to <= GALLERY_INDEX -> -1
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

    // **电脑端 Ctrl+1..5 切页**：入口接全局按键（`Window(onKeyEvent=…)`），
    // 记录当前第几页 / 怎么按都住在这里，所以中间走 [LocalPageShortcuts] 这个信箱。
    // 手机上没 Ctrl，等于不存在；这里只是登记了一个可以调用的入口"。
    val pageShortcuts = LocalPageShortcuts.current
    DisposableEffect(pageShortcuts) {
        pageShortcuts.register { target ->
            if (target in tabs.indices) {
                goToPage(target, directionFor(pagerState.currentPage, target))
            }
        }
        onDispose { pageShortcuts.register(null) }
    }

    // **当前页广播**（电脑端拿它改窗口标题：`NAI Studio → 图库`）。
    // 工具页比较特别：标题要跟着**选中的工具**走，所以 key 里带上 `selectedTool`。
    LaunchedEffect(pageShortcuts, index, state.selectedTool, language) {
        val title = if (index == TOOLS_INDEX) {
            t(ToolCatalog.byId(state.selectedTool).titleKey)
        } else {
            tabs[index.coerceIn(tabs.indices)].first
        }
        pageShortcuts.notifyPageChanged(title, index)
    }

    // 一次性提示：AppState 里挂上 toastEvent 就弹一个提示（导入/导出的成败都走这里）。
    // 弹完立刻消费掉，避免重组时重复弹。
    LaunchedEffect(state.toastEvent) {
        val event = state.toastEvent ?: return@LaunchedEffect
        uiHost.toast(event.message, long = event.isError)
        state.consumeToast()
    }

    // 图库点「使用此图图生图」后回到文生图页（AppState 里的计数器；必须放在 goToPage 之后）。
    // ⚠️ 只保留这一处与滑动无关的真 bug 修复：早先只判断 `tick > 0`，于是每次这段副作用
    // 重新进入组合（开抽屉、点工具、任何重组）都会执行一次，导致"点侧边栏工具被弹回生图页"。
    var handledGenerateTick by remember { mutableStateOf(state.navigateToGenerateTick) }
    LaunchedEffect(state.navigateToGenerateTick) {
        if (state.navigateToGenerateTick > handledGenerateTick) {
            handledGenerateTick = state.navigateToGenerateTick
            if (pagerState.currentPage != 0) backToGenerate()
        }
    }

    // 窗口够宽（平板 / 电脑）→ 侧栏**常驻**，不再做成抽屉。
    // 判定用共用的 `classifyWindow`：按**短边**分类，手机横屏依旧是手机（不会突然长出侧栏）。
    val windowSize = LocalWindowSize.current
    val permanentSidebar = classifyWindow(windowSize.width, windowSize.height) != WindowClass.PHONE
    val referenceSkin = NaiSkin.fromId(state.settings.skin) == NaiSkin.Reference

    // 常驻侧栏可以**收纳成图标条**（用户 2026-09-16 要求）。
    // 状态存平台层（电脑上就在 `prefs.json`）：收起来之后下次打开还是收着的 — 和
    // "记住窗口大小"一个口径，属于电脑上的使用习惯，不该每次重来。
    // 手机上这个值没有界面去改（那套是抽屉），所以它一直是 false，等于不存在。
    val platform = LocalPlatform.current
    var sidebarCollapsed by rememberSaveable {
        mutableStateOf(platform.kv.getBoolean(KEY_SIDEBAR_COLLAPSED, false))
    }
    val setSidebarCollapsed: (Boolean) -> Unit = { collapsed ->
        sidebarCollapsed = collapsed
        platform.kv.edit().putBoolean(KEY_SIDEBAR_COLLAPSED, collapsed).apply()
    }

    // ---- 常驻侧栏宽度**可拖**（对齐生成页那条"提示词栏"分隔条，用户 2026-09-17 要求）----
    // 默认 280dp，范围 200 .. min(窗宽一半, 420)：上限跟着窗口走，免得把右边的画布/内容挤没。
    // ⚠️ `coerceIn` 在 min > max 会抛异常，所以上限先 `coerceAtLeast(min)` 兜一下。
    //（窗口窄到 400dp 以下时，窗宽一半就小于 200 了）。
    val sidebarMinWidth = if (referenceSkin) 180.dp else SidebarMinWidth
    val sidebarMaxWidth = minOf(windowSize.width / 2, SidebarMaxWidth)
        .coerceAtLeast(sidebarMinWidth)
    val defaultSidebarWidth = if (referenceSkin) {
        (windowSize.width.value * 0.115f).roundToInt().coerceIn(180, 236)
    } else {
        DEFAULT_SIDEBAR_WIDTH_DP
    }
    val savedSidebarWidth = remember { platform.kv.getInt(KEY_SIDEBAR_WIDTH, -1) }
    var sidebarWidthCustomized by remember { mutableStateOf(savedSidebarWidth > 0) }
    val sidebarWidthState = remember {
        mutableFloatStateOf(
            (savedSidebarWidth.takeIf { it > 0 } ?: defaultSidebarWidth).toFloat(),
        )
    }
    LaunchedEffect(referenceSkin, windowSize.width) {
        if (!sidebarWidthCustomized) sidebarWidthState.floatValue = defaultSidebarWidth.toFloat()
    }
    // 读出来就夹：窗口比上次小的时候，旧的 420 不能原样铺出去。
    // **只在使用处夹、不回写** — 窗口拉回来还是用户当初拖的那个宽度。
    val sidebarWidthDp = sidebarWidthState.floatValue
        .coerceIn(sidebarMinWidth.value, sidebarMaxWidth.value)
    // 拖动**结束**才落盘（每帧写会把 prefs.json 磨穿）。写的是状态里的原值（用户意图），
    // 下次开窗再按当时的窗口夹一遍。
    val commitSidebarWidth: () -> Unit = {
        platform.kv.edit()
            .putInt(KEY_SIDEBAR_WIDTH, sidebarWidthState.floatValue.roundToInt())
            .apply()
        sidebarWidthCustomized = true
    }

    // 「工具」下拉分组是否展开**住在外壳这一层**：收起 / 展开两态是 `if/else` 两个分支。
    // 状态放里面的话一收纳就被丢掉（回来时工具名单是收着的）；放这一层还能跟 `rememberSaveable`
    // 一起存下来。收纳态那颗 logo 展开后就能直接看到原来展开的工具分组。
    var toolsExpanded by rememberSaveable { mutableStateOf(false) }

    // ---- 窗口标题栏（**全宽**）----
    // 用户 2026-09-19：「**侧边栏也放在标题栏下面**」—— 原来标题栏只盖住"内容区"那一块，
    //（侧栏整条顶到窗口最上面）。现在标题栏横跨整个窗口宽度，侧栏与页面内容都从它下面开始。
    // 做法：整块抽屉整体下移一个标题栏高度（`StudioDrawer(modifier = padding(top = …))`），
    // 标题栏自己画在 Box 的**最后一层**（于是它盖在侧栏上面、而不是被侧栏挤到右边）。
    Box(Modifier.fillMaxSize()) {
    StudioDrawer(
        modifier = Modifier.padding(top = MENU_BAR_HEIGHT),
        open = drawerOpen,
        onOpenChange = { drawerOpen = it },
        // 常驻侧栏**可调宽度**（拖把手 + 落盘）；手机那套浮层抽屉仍是"屏幕一半、上限 280"
        width = if (permanentSidebar) sidebarWidthDp.dp else drawerWidth(),
        // 侧边栏**只有文生图页**能呼出：
        //  · 图库页留给 pager 的「滑回文生图」
        //  · 底部面板在用（打开或正在拉）时也屏蔽 — 那时候右滑多半是想调参数
        //  · 工具 / 设置页顶栏是返回箭头，不提供抽屉入口
        gesturesEnabled = pagerState.currentPage == 0 && !bottomPanelActive,
        permanent = permanentSidebar,
        collapsed = permanentSidebar && sidebarCollapsed,
        sidebarWidthDp = sidebarWidthState,
        sidebarMinWidth = sidebarMinWidth,
        sidebarMaxWidth = sidebarMaxWidth,
        onSidebarWidthCommit = commitSidebarWidth,
        drawerContent = {
            // 主题深浅在这一层算一次（顶部标题、底部那颗切换按钮都要用同一份判断）
            val dark = when (state.settings.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            // 手机那套抽屉永远展开（收纳是常驻侧栏才有的形态）
            if (permanentSidebar && sidebarCollapsed) {
                // ⚠️ 收纳态**保留那排栏目图标**（用户 2026-09-18：「收纳态也留着那一排图标，
                // 和之前未改一样」）— 上面那版"只剩 logo"的改法已撤回。
                // 侧栏宽度可调 + 落盘那条**保留**（那是另一件事，用户没提）。
                CollapsedSidebar(
                    dark = dark,
                    t = t,
                    currentPage = pagerState.currentPage,
                    tabs = tabs,
                    onSelectTab = { tabIndex ->
                        // 收纳态点图标的口径（用户 2026-09-16 要求）：
                        //  · 点「工具」→ **先展开侧栏、再展开工具分组**（收纳态没有下拉的位置）。
                        //  · 点当前正在看的那一页 → 退回文生图（主界面）。
                        //  · 其它 → 正常切页
                        if (tabIndex == TOOLS_INDEX && pagerState.currentPage != TOOLS_INDEX) {
                            toolsExpanded = true
                            setSidebarCollapsed(false)
                        } else if (tabIndex == pagerState.currentPage) {
                            goToPage(0, directionFor(pagerState.currentPage, 0))
                        } else {
                            goToPage(tabIndex, directionFor(pagerState.currentPage, tabIndex))
                        }
                    },
                    onToggleTheme = {
                        val center = themeButtonCenter
                        if (center != null) {
                            switchTheme(center, !dark)
                        } else {
                            state.setSettings { it.copy(theme = if (dark) "light" else "dark") }
                        }
                    },
                    onExpand = { setSidebarCollapsed(false) },
                    statsOpen = statsOpen,
                    onToggleStats = { statsOpen = !statsOpen },
                    onCloseStats = { statsOpen = false },
                    state = state,
                    // 收纳态那颗深浅色按钮也要报圆心，否则扩散动画没有圆心可用（会退化成直接切）
                    onThemeItemPositioned = { themeButtonCenter = it },
                )
            } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp, bottom = 16.dp),
            ) {
                // ---- 顶部：App 图标 + 名字（用户 2026-09-16 要求）----
                // **图标在上、名字在下、整体居中**（用户口径）；图标按 SVG 的 108×108
                // 视口等比缩放，任何尺寸都不变形。
                // 名字颜色**正黑 / 正白**，不用 onSurface（那两档带主题色调）— 由
                // `naiBrandTitleColor` 统一给，界面里不写死色值。
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = if (referenceSkin) 26.dp else 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // ⚠️ 侧栏这枚**扁平单色标记**（用户 2026-09-19：「重绘一个扁平化的
                        // 单色 logo，和截图差不多，放大一些」）：不再用带底纹、渐变的应用图标
                        // （`AppLogo` 留给开屏与窗口图标），只画图形本身。
                        // 尺寸 56 → 72 → **88**：星芒挪高之后外接框变高，跟着 `size` 抬上去，
                        // 画框本体才和上一版一样大（甚至更大一点）。
                        // ⚠️ 星星单独给色（用户 2026-09-24：「**右上角星星常态蓝色**」✓）——
                        // 画框仍跟主题走 ✓（深底白框 / 浅底黑框 ✓）。
                        FlatBrandMark(
                            size = if (referenceSkin) 58.dp else 88.dp,
                            color = if (referenceSkin) NaiSkinTokens.brandInk() else naiBrandTitleColor(dark),
                            starColor = if (referenceSkin) NaiSkinTokens.brandStar() else naiBrandAccent(dark),
                        )
                        // ⚠️ 间距从 10dp 收到 4dp（用户 2026-09-20：「图标和标题之间空隙太大了，靠近一些」）——
                        // 同时 `FlatBrandMark` 也改成按外接框比例出画布（不再在图形上下留空白）。
                        Spacer(Modifier.height(4.dp))
                        // **「NAI」的 I 常态蓝色**（用户 2026-09-24 ✓）——
                        // ⚠️ 下一个词首字母开始**另起一个 `Text`** ✗ 别再拆成三段：`buildAnnotatedString`
                        //    或逐字母摆都行 ✓，这里让"NAI"整体是一个 `Text`（字母间距、省略号口径都不变 ✓）。
                        BrandTitle(
                            appName = appName,
                            dark = dark,
                        )
                    }
                    // **侧栏开关**：右上角一颗（用户 2026-09-16：图标换成"圆角框 + 竖线"）
                    // 放右上角，下面那颗「收起侧栏」按钮去掉）。收起、展开都靠它。
                    IconButton(
                        onClick = { setSidebarCollapsed(true) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(
                            SidebarToggleIcon,
                            contentDescription = t("drawer.collapse"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // 上半：侧边栏 = 原来的底栏，只有页面导航（可滚动的）
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // 「工具」那一项是个**下拉分组**（用户要求）：点一下展开工具名，
                    // 再点工具名进对应界面 — 反推提示词在工具页，导演与角色分区在生成页。
                    // ⚠️ `toolsExpanded` 住在外壳那一层（见它的声明）：收纳态也要能把它点亮。
                    val openTool: (ToolEntry) -> Unit = { entry ->
                        when (entry.surface) {
                            ToolSurface.TOOLS -> {
                                // 用户 2026-09-20：「工具窗口**只能开在画布**，在其他页面不能开」——
                                // 所以只有生在画布（第 0 页）时才浮窗口；别的页面点它还是老行为（切到工具页）。
                                state.selectTool(entry.id)
                                if (pagerState.currentPage == 0) {
                                    state.openToolWindow(entry.id)
                                } else {
                                    goToPage(TOOLS_INDEX, directionFor(pagerState.currentPage, TOOLS_INDEX))
                                }
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
                        val isToolsGroup = tabIndex == TOOLS_INDEX
                        StudioNavigationItem(
                            label = label,
                            icon = icon,
                            selected = pagerState.currentPage == tabIndex,
                            trailing = if (isToolsGroup) {
                                // 右侧箭头：收起朝下、展开朝上（和「设置」里的折叠卡片一个意思）
                                {
                                    Icon(
                                        imageVector = if (toolsExpanded) {
                                            Icons.Filled.KeyboardArrowUp
                                        } else {
                                            Icons.Filled.KeyboardArrowDown
                                        },
                                        contentDescription = null,
                                    )
                                }
                            } else {
                                null
                            },
                            onClick = {
                                if (isToolsGroup) {
                                    // 点「工具」本身：展开/收起下拉，不翻页。
                                    // ⚠️ 例外**人已经在工具页上**时，这一下算"再点一次该界面的图标
                                    // 就退回主界面"（和其它几项同一个口径，用户 2026-09-16 要求），
                                    // 顺手把下拉收起 — 站在工具页里展开工具名单没有意义。
                                    if (pagerState.currentPage == TOOLS_INDEX) {
                                        toolsExpanded = false
                                        goToPage(0, directionFor(pagerState.currentPage, 0))
                                        drawerOpen = false
                                    } else {
                                        toolsExpanded = !toolsExpanded
                                    }
                                } else if (pagerState.currentPage == tabIndex) {
                                    // **已经在这一页了，再点一次 = 退回主界面**（用户 2026-09-16 要求）：
                                    // 点当前标签再点一次收起，一个手感，不用去够顶栏的返回箭头。
                                    goToPage(0, directionFor(pagerState.currentPage, 0))
                                    drawerOpen = false
                                } else {
                                    // 进工具 / 设置从右滑入；从工具 / 设置回主界面从左滑回；其余淡入淡出。
                                    goToPage(
                                        tabIndex,
                                        directionFor(pagerState.currentPage, tabIndex),
                                    )
                                    drawerOpen = false
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                        )

                        // 展开后的工具名（缩进一格，字小一号，点名字进对应界面）
                        // 展开/收起**弹出动画**（用户要求）：垂直展开 + 淡入淡出。
                        AnimatedVisibility(
                            visible = isToolsGroup && toolsExpanded,
                            enter = expandVertically(
                                animationSpec = tween(180, easing = FastOutSlowInEasing),
                            ) + fadeIn(animationSpec = tween(150)),
                            exit = shrinkVertically(
                                animationSpec = tween(140, easing = FastOutSlowInEasing),
                            ) + fadeOut(animationSpec = tween(90)),
                        ) {
                            Column {
                                ToolCatalog.entries.forEach { entry ->
                                // 高亮：**那扇浮动窗口开着**也算"这个工具正开着"——
                                // 点它不再翻页了，总得有处反馈告诉你"它已经出来了"
                                val active = state.isToolWindowOpen(entry.id) ||
                                    (
                                        state.selectedTool == entry.id &&
                                            when (entry.surface) {
                                                ToolSurface.TOOLS -> pagerState.currentPage == TOOLS_INDEX
                                                ToolSurface.GENERATE -> pagerState.currentPage == 0
                                            }
                                        )
                                StudioNavigationItem(
                                    label = t(entry.titleKey),
                                    subtitle = t(entry.hintKey),
                                    selected = active,
                                    onClick = { openTool(entry) },
                                    modifier = Modifier.padding(
                                        start = 32.dp,
                                        end = 12.dp,
                                        bottom = 2.dp,
                                    ),
                                )
                                }
                            }
                        }
                    }
                }

                // 底部固定：一条横线 + **统计** + 暗色/浅色切换（月亮 / 太阳）— 钉在侧边栏最底下
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                // 用户 2026-09-23：「**在侧边栏下方，暗色模式上方加一个柱状图图标"统计"**」✓ ——
                // 位置就是这里：页面图标那一列之下、主题开关之上 ✓（原文点名的两处夹着它 ✓）。
                // ⚠️ 同日二次定稿：统计**不再铺右边那一整块** ✗，而是**贴着这颗按钮弹出的一个小窗** ✓
                //（大小照"呼出画师超市"那扇工具窗口 520×640 ✓，无边框 / 圆角 / 毛玻璃 ✓，
                //  再点这颗按钮或点窗外 / 按 Esc 都收回 ✓）—— 见 [StatsAnchor] ✓。
                StatsAnchor(
                    open = statsOpen,
                    onToggle = { statsOpen = !statsOpen },
                    onClose = { statsOpen = false },
                    state = state,
                    t = t,
                ) {
                    StudioNavigationItem(
                        label = t("stats.title"),
                        icon = BarChartIcon,
                        selected = false,
                        onClick = { statsOpen = !statsOpen },
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 3.dp),
                    )
                }
                StudioNavigationItem(
                    label = if (dark) t("drawer.lightMode") else t("drawer.darkMode"),
                    icon = if (dark) SunIcon else MoonIcon,
                    selected = false,
                    onClick = {
                        // 不直接切主题：交给「圆形扩散」动画 — **这颗按钮的中心**当圆心，
                        // 抓屏 / 铺遮罩 / 挖洞都在 MainActivity 那边（`LocalThemeSwitch`）。
                        // 圆心是窗口坐标，因为遮罩画在最外层、和这颗按钮不在同一个坐标系里。
                        val center = themeButtonCenter
                        if (center != null) {
                            switchTheme(center, !dark)
                        } else {
                            // 还没量到位置（理论上第一帧之后就有了）：退回直接切，别把功能卡住。
                            state.setSettings { it.copy(theme = if (dark) "light" else "dark") }
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .onGloballyPositioned { coords ->
                            val bounds = coords.boundsInWindow()
                            themeButtonCenter = bounds.center
                        },
                )

                // ⚠️ 这里**曾经**有一颗「☰ 收起侧栏」按钮：用户 2026-09-16 要求去掉。
                // 改成右上角那颗 [SidebarToggleIcon]（见顶部标题那一块）。
            }
            }
        },
    ) {
        // 返回键：**从最上面那一层往下逐级退，任何一级都不直接退出 App**。
        // 顺序（上面的先吃）：
        //  1. 侧边栏开着 → 关侧边栏
        //  2. 剧情放大编辑页开着 → 退回剧情框（抽屉继续留着）
        //  3. 遮罩模式开着 → 退出遮罩
        //  4. 底部抽屉拉着（含正在拖）→ 收起抽屉
        //  5. 图库在批量选择 → 退出选择
        //  6. 不在文生图页 → 回文生图
        //  7. 已经在文生图页、上面什么都没有 →**两次退出**
        // ⚠️ 第 4 条是 2026-09-16 补的：抽屉开着时按返回会一路落到第 7 条，
        // 结果再按一次退出 — 用户报的正是这个。抽屉的开合状态住在 GenerateScreen 里，
        // 这里够不着，所以走 `state.requestPanelCollapse()` 发一次性请求。
        // 弹窗 / ModalBottomSheet **各自**有 BackHandler，而且比这里组合得晚，
        // 会先吃掉返回键；全屏看图是个 `Dialog`，系统返回键本来就自动关掉 — 都不用管。
        when {
            // 画布编辑器开着 → 返回键 = 退出编辑器（**丢弃这次编辑**，和左上角那个叉一致；
            // 想留下改动用顶栏的「完成」）。放在最前面：它盖在所有东西上面。
            state.canvasEditorOpen -> uiHost.backHandler { state.closeCanvasEditor(commit = false) }
            drawerOpen -> uiHost.backHandler { drawerOpen = false }
            index == 0 && state.plotEditorOpen -> uiHost.backHandler { state.closePlotEditor() }
            index == 0 && state.maskMode && state.canvasMode != 2 -> uiHost.backHandler { state.toggleMaskMode() }
            index == 0 && bottomPanelActive -> uiHost.backHandler { state.requestPanelCollapse() }
            index == GALLERY_INDEX && selecting -> uiHost.backHandler {
                selecting = false
                selectedIds = emptySet()
            }
            index != 0 -> uiHost.backHandler { backToGenerate() }
            else -> uiHost.backHandler {
                // ⚠️ 用户 2026-09-26：「**Esc 键不要拿来当退出键，Esc 不能退出程序**」——
                // 电脑端（`permanentSidebar`）**取消"再按一次退出"** ✗：那边 Esc 就是返回键，
                // 一路退到第 7 条时按 Esc 只会把窗口关掉，属于"手滑就没了" ✗。
                // 手机保留原来的双击返回退出（Android 惯例 ✓，用户没提这条）。
                if (!permanentSidebar) {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPress <= EXIT_INTERVAL_MS) {
                        uiHost.exitApp()
                    } else {
                        lastBackPress = now
                        uiHost.toast(t("app.pressBackAgainToExit"))
                    }
                }
            }
        }

        // 遮罩模式下顶栏整条让给遮罩工具条（GenerateScreen 自己画在内容最上面），
        // 这样图片既不被工具条压住、也不用因为多一行而缩小。
        // ⚠️ 用户 2026-09-26：**电脑宽屏**的画布编辑器也走这条路 —— 它现在就画在画布区里、
        // 中控台与底栏照旧留着，所以外壳这条顶栏**不用再让位** ✗（窄窗口 / 手机仍是整页替换 ✓）。
        val maskToolbarOwnsTopBar = pagerState.currentPage == 0 && state.maskMode && state.canvasMode != 2
        val contentOwnsTopBar = maskToolbarOwnsTopBar || (state.canvasEditorOpen && !permanentSidebar)

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            // 青蓝玻璃的页面渐变在 Main.kt 的窗口根层；默认 Scaffold 底色会把它整片盖住。
            // 仅此外观透出根背景，让两团柔光能穿过画布区；毛玻璃仍沿用主题底色。
            containerColor = if (referenceSkin) Color.Transparent else MaterialTheme.colorScheme.background,
            // 显式给出前景色：透明容器无法通过 contentColorFor 推导颜色。Glass 下仍是旧底色，
            // 只有 Reference 需要透出 Main.kt 根层的参考渐变。
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                // ⚠️ **窗口标题栏（菜单那一行）不在这儿** — 它住在最外层那个 `Box` 里、横跨全宽，
                // 好让**侧边栏也从它下面开始**（用户 2026-09-19：「侧边栏也放在标题栏下面」）。
                // 这条 `topBar` 现在只剩"**页面**标题"（图库 / 工具 / 设置 / 我的 用的那条）。
                Column(Modifier.fillMaxWidth()) {
                    // ⚠️ **第 0 页（文生图）不发射页面标题栏**（用户 2026-09-19）
                    // 「顶栏整条 52dp 也去掉、只留一颗余额胶囊浮在右上角」）— 所以
                    // 标题栏不发射 = 内容少让出 52dp，画布顶到窗口标题栏下沿。
                    if (!contentOwnsTopBar && pagerState.currentPage != 0) {
                    // ---- 顶栏**自绘的一行**（不再用 M3 `TopAppBar`）----
                    // 为什么自绘：`TopAppBar` 内部**把 64dp 写死在排版里**（它在 measure 里
                    // `TopAppBarExpandedHeight` 算基线），我们在外面用 `Modifier.height(52.dp)`
                    // 压低之后**测得的高度是 52、排版仍是 64** — 标题就往上偏
                    //（用户 2026-09-19：「顶栏的'生图'要上下居中，位置有点偏上」）。
                    // 自绘这一行之后 `verticalAlignment = CenterVertically` 才是真的居中。
                    // 高度也由 [TOP_BAR_HEIGHT] 一处说了算（和遮罩模式那条工具条同源）。
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer
                            .copy(alpha = NaiGlass.BAR_ALPHA),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth().height(TOP_BAR_HEIGHT),
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // ---- 左：导航图标（文生图页是汉堡、其它页是返回箭头）----
                            // **宽窗口下侧栏常驻**，没有"开侧边栏"这回事，所以文生图页留空。
                            if (pagerState.currentPage == 0) {
                                if (!permanentSidebar) {
                                    IconButton(onClick = { drawerOpen = true }) {
                                        Icon(Icons.Filled.Menu, contentDescription = t("drawer.open"))
                                    }
                                }
                            } else {
                                IconButton(onClick = backToGenerate) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = t("drawer.backToGenerate"),
                                    )
                                }
                            }

                            // ---- 中：标题（或滑出来的搜索胶囊）----
                            // 用 `Row` 而不用 `Box`：`AnimatedVisibility` 在 `RowScope` 里有
                            // 扩展重载，套在 `Box` 里会因为拿不到那个接收者而编译不过。
                            Row(
                                Modifier.weight(1f).padding(start = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // 工具页显示**当前工具的名字**（不再是笼统的"工具"
                                val title = if (pagerState.currentPage == TOOLS_INDEX) {
                                    t(ToolCatalog.byId(state.selectedTool).titleKey)
                                } else {
                                    tabs[pagerState.currentPage].first
                                }
                                // 角色图鉴 / 图库：放大镜点开后，标题位置滑出一颗**胶囊搜索框**
                                val animaOpen = pagerState.currentPage == TOOLS_INDEX &&
                                    state.selectedTool == ToolCatalog.ANIMADEX && state.animaSearchOpen
                                val galleryOpen =
                                    pagerState.currentPage == GALLERY_INDEX && state.gallerySearchOpen
                                if (animaOpen || galleryOpen) {
                                    AnimatedVisibility(
                                        visible = true,
                                        enter = expandHorizontally() + fadeIn(),
                                    ) {
                                        if (galleryOpen) {
                                            SearchCapsule(
                                                state = state,
                                                focusKey = "shell.gallery.search",
                                                value = state.galleryQuery,
                                                onValueChange = { state.updateGalleryQuery(it) },
                                                placeholder = t("gallery.searchHint"),
                                                onSubmit = { state.closeGallerySearch() },
                                            )
                                        } else {
                                            SearchCapsule(state, t)
                                        }
                                    }
                                } else if (pagerState.currentPage != 0) {
                                    // ⚠️ **文生图页（第 0 页）不画标题**（用户 2026-09-19）
                                    // 「顶栏，生图那一行去掉，只留余额」）— 那一行现在只用来把余额
                                    // 挤到右边，所以它照旧占着 `weight(1f)`，只是不画「生图」两个字。
                                    // 其余页面（图库 / 工具 / 设置 / 我的）标题原样保留。
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            // ---- 右：动作 ----
                            // 图库页右上角不显示余额：
                            //  常态：[垃圾桶（批量删除）] [横杠排序按钮] [放大镜]
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
                                    // 图库搜索：右上角的放大镜（点开顶栏滑出胶囊）
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
                                // ⚠️ **到不了这里**：第 0 页不再发射这层**页面标题**（见上面那段），
                                // 余额胶囊在顶部菜单条右端（`WindowMenuBar(trailing = …)`）。
                                // 留这个分支只是为了哪天把标题栏还给第 0 页时一眼看到该放哪儿。
                            }
                        }
                    }
                }
                }
            },
            // 没有底栏：导航在抽屉。
        ) { inner ->
            // imePadding：键盘弹起时把四页内容整体上移（edge-to-edge + adjustResize 下，
            // 没有 imePadding 的层不会被键盘顶起，中下部的输入框会被键盘盖住 — 用户报过）。
            // TopAppBar 在 insets 之上不受影响；框内光标跟随由各滚动容器的
            // bringIntoView 自动接住（BasicTextField 获得焦点/换行时会向祖先请求滚动）。
            // ⚠️ **生成页（第 0 页）例外 — 它自己处理键盘**。
            // 生成页底部是两块贴底覆盖层，「把手行 + 抽屉 + 按钮栏」那一块要跟着键盘上浮。
            // 而「x放大/导演台 + 生成图片」那一块要留在屏幕底部（让位给键盘）。
            // 整页一起 imePadding 的话这两块会一起被顶上去，就没法分开。
            // 判据用 `settledPage` 而不用 `currentPage`：翻页途中反复换修饰符会让布局抖。
            // ⚠️ 试过**不吃顶部内边距**（让图片铺到毛玻璃顶栏后面），已按用户要求回退。
            // 那样图片会整体上移、被顶栏压掉一截，看图为主的页面不划算。
            // 顶栏的毛玻璃保留，只是它后面是页面底色而不是图片 — 观感更安静。
            // ⚠️ 2026-09-19 下半段：用户改口要「顶栏整条 52dp 也去掉、只留一颗余额胶囊浮在右上角」
            // 于是**第 0 页顶栏整条不再发射**（见 `topBar`），所以第 0 页的 `inner.top` 就是 0。
            // 画布真的顶到窗口上沿。上面那句回退"只对**第 0 页**成立"。
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
                // `beyondViewportPageCount = 1` 是为了"从图库滑回生图"不黑帧（见下面那段注释）。
                // 代价是**冷启动时图库页也被一起组合**：它的网格会把首屏十几个格子
                // （每格一次缩略图读盘 / 解码）立刻拉起来，正好和"用户第一次拉底栏"抢主线程。
                // 所以：预热窗（800ms）内不预组合；窗口一过、或者用户真的开始翻页，立刻恢复 — 而
                // 后者保证"窗口内就滑去图库"的那次也不会在滑回来时黑帧。
                var neighborPrewarmReady by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(800)
                    neighborPrewarmReady = true
                }
                val pagerInUse = pagerState.isScrollInProgress || pagerState.currentPage != 0

                // 四页都在 pager 里：手势滑动（只允许 文生图 → 图库）走 pager 自带的跟手动画；
                // 抽屉点击 / 返回箭头 / 返回键走 goToPage 的淡入淡出。
                HorizontalPager(
                    state = pagerState,
                    // **只有 生图(0) → 图库(1) 允许手指左右滑翻页**，且**底部面板打开时一律不翻页**。
                    // ⚠️ 面板打开时不能靠 swipeGate 拦：闸门为了"面板内左右换栏"和"工具页横向胶囊"
                    // 能工作，改成在 post 阶段才吃位移 — 那时 pager 已经把位移消费掉了，闸门只看得到 0。
                    // 所以必须在 pager 上直接关掉滑动（和工具 / 设置页禁滑同一招）。
                    userScrollEnabled = !bottomPanelActive && (
                        pagerState.currentPage == 0 || pagerState.currentPage == GALLERY_INDEX
                        ),
                    // 相邻页**保持组合**：否则从图库滑回生图时，生图页是刚被重建的，
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
                        // ⛔ 高级漫画那一整屏**已退役**（用户 2026-09-23：「高级漫画放到主页」✓）——
                        // 它现在长在生图页画布区的「漫画」那一档里 ✓（`GenerateScreen` 的
                        // `canvasMode == 2` 分支 → `ComicCanvasHost` ✓）。
                        SETTINGS_INDEX -> SettingsScreen(state)
                        else -> ProfileScreen(state)
                    }
                }

                // **画布编辑器**：窄窗口 / 手机（没有中控台与底栏可留）时**整页替换** ✓；
                // ⚠️ 用户 2026-09-26：**电脑宽屏**不再这样 —— 那边改成"就地画在画布区"、
                // 中控台与底栏照旧留着（和遮罩模式一个口径 ✓）。
                // 实现落在 `GenerateScreen` 宽屏分支里（盖在预览那一块上）。
                if (state.canvasEditorOpen && !permanentSidebar) {
                    CanvasEditorScreen(
                        state = state,
                        t = t,
                        onCancel = { state.closeCanvasEditor(commit = false) },
                        onCommit = { state.closeCanvasEditor(commit = true) },
                    )
                }

                // ---- **统计**：贴着按钮弹出来的小毛玻璃窗（用户 2026-09-23 二次定稿 ✓）----
                // ⚠️ 口径改过两次 ✗，以最后一次为准：
                //   第一版「整页铺满」→ 第二版「**右边弹一个小毛玻璃窗口**」✓ →
                //   第三版（本次 ✓）「**统计……要放在按钮旁边的小窗口，呼出后再点击统计或点击窗口外会收回，
                //   小窗口大小参考呼出画师超市的大小，无边框，圆角，毛玻璃**」✓。
                // ⇒ 那扇窗**不再画在这一层** ✗，改成**贴着「统计」那颗按钮**（[StatsAnchor] ✓）——
                //   这一层的浮层留着会被 Popup 盖住、也会跟着页面滚动 ✗，所以整段撤掉 ✓。

                // ---- 第 0 页浮在右上角的那颗「余额胶囊」----
                // 用户 2026-09-19：「余额位置还是单独的胶囊」— 标题栏那一行只留
                // `[logo] 菜单 →[第 ×]`，余额仍旧是**内容层的一颗独立胶囊**（第 0 页专属，
                // 既有口径：其它页不显示）。位置贴着画布右上角（标题栏正下方留一点边距）。
                // ⚠️ 用户 2026-09-26：「余额胶囊会遮住按键，在这两个模式下隐藏胶囊」——
                // 遮罩模式本来就通过 `contentOwnsTopBar` 藏了 ✓；**画布编辑器**这一路是我这轮
                // 把它从 `contentOwnsTopBar` 里摘出来的（现在它不抢顶栏了），所以这里要显式补上 ✓。
                if (pagerState.currentPage == 0 && !contentOwnsTopBar && !state.canvasEditorOpen) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                end = if (referenceSkin) 14.dp else 10.dp,
                                top = if (referenceSkin) 14.dp else 8.dp,
                            ),
                    ) {
                        AccountChip(state, t)
                    }
                }
            }
        }
    }

    // ---- 窗口标题栏：**全宽**，画在最上面 = 盖在侧栏与内容之上 ----
    // 为什么不放 Scaffold 的 `topBar` 里：那样它只会占"内容区"那一块宽度（侧栏在它左边顶到窗口顶）。
    // 用户 2026-09-19：「**侧边栏也放在标题栏下面**」—— 所以要横跨全宽、让侧栏从它下面开始。
    //（侧栏那边由 `StudioDrawer(modifier = padding(top = …))` 让位）。
    // ---- 工具浮动窗口：盖在页面之上，但**让出**那条窗口菜单栏（它是"系统标题栏"，得一直够得着）----
    // 画在 `WindowMenuBar` **前面** = 排在它下面；每扇窗口的 y 也被夹在菜单栏之下（见夹取那段）。
    // 左边同理让出侧栏（`sidebarWidth`）。
    // ⚠️ 用户 2026-09-20：「**只在画布显示**」—— 工具窗口只在第 0 页（生图页）渲染；
    // 切到别的页面整层收起（位置仍记在盘上，回画布照旧摆出来；"哪几个开着"也照旧）。
    if (pagerState.currentPage == 0) {
        ToolWindowLayer(
            state = state,
            t = t,
            sidebarWidth = if (permanentSidebar) sidebarWidthDp.dp else 0.dp,
        )
    }

    WindowMenuBar(
        state = state,
        t = t,
        tabs = tabs,
        sidebarCollapsed = sidebarCollapsed,
        onToggleSidebar = {
            sidebarCollapsed = !sidebarCollapsed
            platform.kv.edit().putBoolean(KEY_SIDEBAR_COLLAPSED, sidebarCollapsed).apply()
        },
        showDrawerButton = !permanentSidebar,
        onOpenDrawer = { drawerOpen = true },
        // 窗口标题（`NAI Studio - 生图`）跟页面标题栏同一套算法：工具页显示当前工具名。
        // ⚠️ 余额胶囊**不放这一行**（用户 2026-09-19：「余额位置还是单独的胶囊」）——
        // 它仍旧是浮在内容区右上角那颗独立胶囊（见内容 Box 末尾那段）。
        // ⚠️ 连接符用 ASCII 的 `-`：中文破折号在自绘标题栏那套字体下会显示成方块/问号
        //（用户 2026-09-19 报的「标题栏乱码」就是这个）。
        windowTitle = appName + " - " + if (pagerState.currentPage == TOOLS_INDEX) {
            t(ToolCatalog.byId(state.selectedTool).titleKey)
        } else {
            tabs[pagerState.currentPage].first
        },
    )
    }

    // 批量删除：确定后再确认一遍，才真删。
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
 * **收纳后的侧栏**（68dp 一竖条，用户 2026-09-16 / 2026-09-17 要求）。
 *
 * 形态就三块，从上到下：**logo**（收纳态**唯一那颗展开开关**，点一下放出来）。
 * **页面图标**（选中那个带底）和**底部**（只有主题切换）。宽度见 [CollapsedSidebarWidth]。
 *
 * 几个取舍：
 *  · **不做悬停气泡提示**：CMP 的 `TooltipArea` 是桌面专有 API，共用树里用不了。
 *    图标本身来自底栏那套（`tabs`），用户认得住；
 *  · **底部原来那颗「☰ 收起侧栏」已经去掉**（用户 2026-09-16：开关统一放侧栏右上角）；
 *  · **“工具”在收纳态点击，先把侧栏放出来**，展开后再露出工具分组 — 见调用处 `onSelectTab`。
 *  · 手机永远走展开那套（收纳是常驻侧栏才有的形态）。
 *  · ⚠️ 2026-09-18 定稿：中途曾把收纳态改成"只剩 logo、图标排全没" — 用户要求撤回
 *    （「收纳态也留着那一排图标，和之前未改一样」）；随后用户又澄清口径：
 *    「**只**改收纳态的按钮，把 logo 顶上原来那颗侧栏开关，其余不变」→
 *    就是现在这份：logo 占掉了 [SidebarToggleIcon] 的位置与职能，图标排、主题切换一字未动。
 */
@Composable
private fun CollapsedSidebar(
    dark: Boolean,
    t: (String) -> String,
    currentPage: Int,
    tabs: List<Pair<String, ImageVector>>,
    onSelectTab: (Int) -> Unit,
    onToggleTheme: () -> Unit,
    onExpand: () -> Unit,
    /** 统计那扇小窗现在开着没（开着才画 ✓ —— 状态住在外壳里 ✓，见 `StatsAnchor` ✓）。 */
    statsOpen: Boolean,
    /** 点「统计」（用户 2026-09-23：**收纳状态下也得看得见** ✓；**再点一下收回** ✓）。 */
    onToggleStats: () -> Unit,
    /** 关掉统计那扇小窗（点窗外 / 按 Esc ✓）。 */
    onCloseStats: () -> Unit,
    /** 那扇小窗要读统计（`state.usage` ✓）。 */
    state: AppState,
    /**
     * 报一个**底部那颗深浅色按钮的中心**（窗口坐标）。
     *
     * ⚠️ 2026-09-19 补：主题切换的**圆形扩散动画**要拿它当圆心；而之前只有**展开态**那颗按钮
     * 报了坐标（`NavigationDrawerItem` 上的 `onGloballyPositioned`）。侧栏一收纳，那颗按钮
     * 根本不在组合里 → 圆心是 null → 落到"直接切主题"的兜底上 →**动画没了**（用户报
     * 「浅色深色切换的扩散动画失效」，他的侧栏正是收纳状态）。
     */
    onThemeItemPositioned: (Offset) -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 顶部那颗**logo 就是收纳态那颗开关**（用户 2026-09-18：收纳态只把
        // 「侧栏开关图标」换成 logo，其余那一排页面图标、底部主题切换全不动）。
        // 收纳态宽度只有 68dp，所以 logo 自己占一行、居中。
        Box(
            Modifier
                .size(48.dp)
                .clickable(onClick = onExpand)
                .semantics { contentDescription = t("drawer.expand") },
            contentAlignment = Alignment.Center,
        ) {
            // 收纳态那颗"展开"按钮上的标记：和展开态同一枚扁平标记（只是小）
            FlatBrandMark(
                size = 34.dp,
                color = if (LocalNaiSkin.current == NaiSkin.Reference) NaiSkinTokens.brandInk() else naiBrandTitleColor(dark),
                starColor = if (LocalNaiSkin.current == NaiSkin.Reference) NaiSkinTokens.brandStar() else naiBrandAccent(dark),
            )
        }
        Spacer(Modifier.height(6.dp))

        // 页面图标（可滚动：窗口很矮时也不会把底部的按钮挤出去）
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { tabIndex, (label, icon) ->
                CollapsedNavItem(
                    label = label,
                    selected = currentPage == tabIndex,
                    onClick = { onSelectTab(tabIndex) },
                ) {
                    // 和展开态**同一个尺寸** ✓（用户 2026-09-24：「侧边栏的图标加大一点」✓；
                    // 两边不一致的话，展开 / 收纳切一下图标会跳 ✓）
                    Icon(icon, contentDescription = label, modifier = Modifier.size(SIDEBAR_ICON))
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        // ⚠️ 用户 2026-09-23：「**统计在收纳状态下看不见**」✓ —— 收纳态（68dp 那条窄栏 ✓）
        // 原来只有"页面图标 + 主题开关" ✗，统计那颗没跟着下来 ✓ ⇒ **这里补一颗** ✓
        // （和展开态那颗同一处位置：主题开关**上面** ✓；弹出的那扇小窗也是同一个 [StatsAnchor] ✓）。
        StatsAnchor(
            open = statsOpen,
            onToggle = onToggleStats,
            onClose = onCloseStats,
            state = state,
            t = t,
        ) {
            CollapsedNavItem(
                label = t("stats.title"),
                selected = false,
                onClick = onToggleStats,
            ) {
                Icon(
                    imageVector = BarChartIcon,
                    contentDescription = t("stats.title"),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(SIDEBAR_ICON_SMALL),
                )
            }
        }
        // 包一层只为了量出这颗按钮的中心（主题扩散动画的圆心，见参数注释）
        Box(
            Modifier.onGloballyPositioned { coords ->
                onThemeItemPositioned(coords.boundsInWindow().center)
            },
        ) {
            CollapsedNavItem(
                label = if (dark) t("drawer.lightMode") else t("drawer.darkMode"),
                selected = false,
                onClick = onToggleTheme,
            ) {
                // 收纳态这颗和展开态那颗同一套（用户 2026-09-26：深色 → 白色太阳、浅色 → 黑色月亮）
                Icon(
                    imageVector = if (dark) SunIcon else MoonIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(SIDEBAR_ICON_SMALL),
                )
            }
        }
    }
}

/**
 * 侧栏右边缘那条**可拖把手**（用户 2026-09-17 要求：侧栏宽度对"提示词栏那样可调"）。
 *
 * ## 为什么收 [MutableFloatState] 而不用 `Float`
 *
 * 拖拽回调住在 `pointerInput` 里，它只在 key 变化时重建 — 直接捕获一个 `Float`
 * 会定格在建立那一刻的值（拖起来要么不动、要么越拖越离谱）。捕获状态**对象**则每次读
 * `floatValue` 都是最新的，也就不需要 `rememberUpdatedState`（和 `GenerateScreen.ColumnSplitter` 一个口径）。
 *
 * ## 落盘时机
 *
 * **只在 `onDragEnd` / `onDragCancel` 各写一次**（`onCommit`）— 拖动过程每帧都写
 * 会把 `prefs.json` 磨穿。取消（手指被别的手势抢走、窗口失焦）也算一次收口，免得白拖。
 *
 * ## 光标
 *
 * 用 `UiHost.horizontalResizeCursor()`：电脑上是"左右箭头"，手机返回空 Modifier。
 * ⚠️ 没用题目里提的 `pointerHoverIcon(PointerIcon.Hand)` — 因为共用树里构造那个指针要 AWT 的
 * `Cursor`（`platform/UiHost.kt` 里专门写了这条注释），而这个平台钩子**本来就在**
 * 生成页那条分隔条用的也是它；"左右箭头"这个说法更贴切。
 */
@Composable
private fun SidebarResizeHandle(
    /** 侧栏宽度（dp，直接改数值 — 固定宽度而不是比例）。*/
    widthDp: MutableFloatState,
    minWidth: Dp,
    maxWidth: Dp,
    /** 松手 / 被打断时**写一次盘**。*/
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // 鼠标靠上去是"左右调整"的光标 — 手机返回空的 Modifier
    val resizeCursor = LocalUiHost.current.horizontalResizeCursor()
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }

    Box(
        modifier
            .width(SidebarHandleWidth)
            .fillMaxHeight()
            .hoverable(interaction)
            .then(resizeCursor)
            // key 带上边界：窗口变了，maxWidth 也跟着变，手势块重建，拿到新的夹取范围
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
                    // 拖动量是**像素**，宽度记的是 **dp** — 所以先换算再夹取
                    val deltaDp = with(density) { dragAmount.x.toDp().value }
                    widthDp.floatValue = (widthDp.floatValue + deltaDp)
                        .coerceIn(minWidth.value, maxWidth.value)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 平时是一条几乎看不见的细线；悬停时 2dp、拖动变 3dp 并换成主色 — 告诉用户"这条能拖"
        Box(
            Modifier
                .width(
                    when {
                        dragging -> 3.dp
                        hovered -> 2.dp
                        else -> 1.dp
                    },
                )
                .fillMaxHeight()
                .background(
                    when {
                        dragging -> MaterialTheme.colorScheme.primary
                        hovered -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.outlineVariant
                    },
                ),
        )
    }
}

/**
 * **侧栏标题「NAI …」**（用户 2026-09-24：「侧边栏优化 logo，…「NAI」的 **I 常态蓝色**」✓）。
 *
 * 画法：`buildAnnotatedString` 把**第一个词里的 `I`** 单独上那抹品牌蓝 ✓ ——
 * App 名是 `NAI Studio` ✓，第一个词正好就是「NAI」✓。
 *
 * ⚠️ 三条守住的口径 ✗：
 *  1. **只染第一个词里的 I** ✓ —— 整串里的 `i` 都染的话，"Stud**i**o" 那个 i 也会变蓝 ✗
 *     （用户点名的是「**NAI 的 I**」✓）；
 *  2. **文字内容一个字不改** ✓（只上色 ✓）；
 *  3. 首词里没有 I（以后改了 App 名 ✓）⇒ **原样显示** ✓，不硬凑 ✗。
 */
@Composable
private fun BrandTitle(appName: String, dark: Boolean) {
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    val base = if (referenceSkin) NaiSkinTokens.brandInk() else naiBrandTitleColor(dark)
    val accent = if (referenceSkin) NaiSkinTokens.brandStar() else naiBrandAccent(dark)
    val firstWordEnd = appName.indexOf(' ').let { if (it < 0) appName.length else it }
    val annotated = buildAnnotatedString {
        appName.forEachIndexed { index, ch ->
            if (index < firstWordEnd && (ch == 'I' || ch == 'i')) {
                withStyle(SpanStyle(color = accent)) { append(ch) }
            } else {
                append(ch)
            }
        }
    }
    Text(
        text = annotated,
        style = if (referenceSkin) {
            MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, letterSpacing = 0.1.sp)
        } else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = base,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private data class DrawerItemFeedback(
    val interaction: MutableInteractionSource,
    val referenceSkin: Boolean,
    val container: Color,
    val content: Color,
    val border: Color,
)

@Composable
private fun rememberDrawerItemFeedback(selected: Boolean): DrawerItemFeedback {
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val container by animateColorAsState(
        targetValue = when {
            !referenceSkin -> if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent
            selected && pressed -> NaiSkinTokens.selectedPressed()
            selected && hovered -> NaiSkinTokens.selectedHover()
            selected -> NaiSkinTokens.selected()
            pressed -> NaiSkinTokens.strip()
            hovered -> NaiSkinTokens.subtleHover()
            else -> Color.Transparent
        },
        animationSpec = if (referenceSkin) tween(120, easing = FastOutSlowInEasing) else snap(),
        label = "drawerItemBackground",
    )
    val content by animateColorAsState(
        targetValue = when {
            selected && referenceSkin -> NaiSkinTokens.onSelected()
            selected -> MaterialTheme.colorScheme.primary
            referenceSkin && hovered -> NaiSkinTokens.text()
            referenceSkin -> NaiSkinTokens.muted()
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = if (referenceSkin) tween(120, easing = FastOutSlowInEasing) else snap(),
        label = "drawerItemContent",
    )
    return DrawerItemFeedback(
        interaction = interaction,
        referenceSkin = referenceSkin,
        container = container,
        content = content,
        border = if (referenceSkin && selected) NaiSkinTokens.selectedBorder() else Color.Transparent,
    )
}

/** 收纳侧栏里的一格：48dp 见方，选中时带一块圆角底（和展开态的胶囊选中一个意思）。*/
@Composable
private fun CollapsedNavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = when {
            !referenceSkin -> if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            selected && pressed -> NaiSkinTokens.selectedPressed()
            selected && hovered -> NaiSkinTokens.selectedHover()
            selected -> NaiSkinTokens.selected()
            pressed -> NaiSkinTokens.strip()
            hovered -> NaiSkinTokens.subtleHover()
            else -> Color.Transparent
        },
        animationSpec = if (referenceSkin) tween(120, easing = FastOutSlowInEasing) else snap(),
        label = "collapsedNavigationBackground",
    )
    val foreground by animateColorAsState(
        targetValue = when {
            selected && referenceSkin -> NaiSkinTokens.onSelected()
            selected -> MaterialTheme.colorScheme.onSecondaryContainer
            referenceSkin && hovered -> NaiSkinTokens.text()
            referenceSkin -> NaiSkinTokens.muted()
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = if (referenceSkin) tween(120, easing = FastOutSlowInEasing) else snap(),
        label = "collapsedNavigationForeground",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .then(if (referenceSkin && selected) Modifier.border(1.dp, NaiSkinTokens.selectedBorder(), MaterialTheme.shapes.small) else Modifier)
            .then(
                if (referenceSkin) {
                    Modifier
                        .hoverable(interaction)
                        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .semantics { contentDescription = label }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides foreground,
        ) {
            content()
        }
        // ⚠️ 用户 2026-09-26 更正：「**展开时不变，是收纳时在下面显示名字**」——
        // 所以名字只在**收纳态**这一格上画（展开态仍是"图标在左、名字在右"的原样 ✗ 没动）。
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 自己实现的抽屉（替代 `ModalNavigationDrawer`）
 *
 * 为什么不用官方的：官方那套滑动是 `anchoredDraggable` 挂在父级，而这台机器上有两道墙 ——
 *  1. 系统手势导航把最外侧 ~24dp 的滑动当「返回」，从边缘起手的事件 App 根本收不到；
 *  2. 更关键的是 `HorizontalPager` 在子层级会先把横向拖动吃掉（scrollable 一过 slop 就独占手势）。
 *     父级的拖动检测再也拿不到，于是「右滑不呼出、也不跟手」。
 *
 * 这里改成：
 *  · 拖动处理挂在**最外层**，并且跑在 `PointerEventPass.Initial` — 先于所有子级拿到事件，
 *    所以 pager 抢不走；
 *  · 起手区是**整个屏幕**（左右半边都能拉出来），按**方向**区分意图：
 *    往右拖 = 开侧边栏，往左拖 = 交给 pager 翻去图库。
 *  · 位移自己驱动（状态 + `snap()`），所以**完全跟手**，松手按一半位置吸附开/关。
 */
/**
 * 侧边栏的一项：**图标在上、名字在下**（用户 2026-09-26：「侧边栏的格式也改成那样，
 * 名字显示在图标下面」—— 参考他给的那张竖栏工具图）。
 *
 * 和 `NavigationDrawerItem` 的区别：那是"图标在左、文字在右"的**横排** ✗；这一版是**竖排**
 *（图标 22dp 居中，名字 `labelSmall` 在下面一行，最多一行、超出省略 ✓），
 * 选中态 = 主色 + 一层淡色圆角底 ✓（和画布那条竖向工具栏一个口径）。
 *
 * @param trailing 名字右边的小挂件（「工具」那项用它放展开/收起的小箭头 ✓）。
 */
@Composable
private fun NaiRailItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        Color.Transparent
    }
    val foreground = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier
            // ⚠️ 顺序：先 clip 再 background 再 clickable —— 涟漪和底色都被圆角裁住 ✓
            //（`clickable` 必须排在尺寸/内边距**之后**那类老教训这里不适用：本项没有外层偏移）。
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(22.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.invoke()
        }
    }
}

@Composable
private fun StudioDrawer(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    width: Dp,
    gesturesEnabled: Boolean,
    /**
     * 加在整个抽屉外面的修饰符。
     *
     * ⚠️ 2026-09-19 新增：窗口标题栏改成**全宽、横跨侧栏之上**之后，这里要给它让位
     *（`Modifier.padding(top = 菜单栏高度)`），侧栏与页面内容才都从标题栏**下面**开始。
     */
    modifier: Modifier = Modifier,
    /** true = **常驻侧栏**（平板 / 电脑宽窗口）：直接并排画，不走浮层、不吃手势。*/
    permanent: Boolean,
    /** 常驻侧栏是否**收纳成图标条**（只有 [permanent] 时有意义）。*/
    collapsed: Boolean,
    /** 常驻侧栏的宽度状态：拖右边缘那条把手时改它（浮层抽屉用不到）。*/
    sidebarWidthDp: MutableFloatState,
    /** 可拖宽度的下 / 上限（上限已经按窗宽夹过，见调用处）。*/
    sidebarMinWidth: Dp,
    sidebarMaxWidth: Dp,
    /** 拖动结束 / 被打断时写一次盘（**不能每帧**）。*/
    onSidebarWidthCommit: () -> Unit,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    // ---- 宽版：侧栏常驻，内容在右侧 ----
    // 为什么不复用下面那套浮层：电脑上"抽屉"这个交互本身就不对劲 — 侧栏该一直看得见。
    // 鼠标也不该为了开侧栏去拖边缘。窗口分类见 `WindowSize.kt`（按**短边**判，
    // 手机横屏仍然算手机，不会突然长出侧栏）。
    if (permanent) {
        val windowSize = LocalWindowSize.current
        val sidebarWidth = if (collapsed) CollapsedSidebarWidth else width
        // 多包一层 Box：右边缘那条把手用**覆盖**（不吃布局宽度），
        // 这样内容区的可用宽度还是"窗宽 - 侧栏"，不用再扣把手那 6dp。
        Box(modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize()) {
                Surface(
                    // 毛玻璃：常驻侧栏这块玻璃跟**App 的样式**一致（`NaiGlass.panelColor()`）— 和
                    // 提示词栏、底栏现在都取同一个函数，同屏几块玻璃才是同一种材质。
                    // 它背后是电脑端铺的磨砂壁纸层（见 `DesktopBackdrop`）。
                    color = NaiSkinTokens.sidebar(),
                    contentColor = NaiSkinTokens.text(),
                    modifier = Modifier.width(sidebarWidth).fillMaxHeight(),
                ) {
                    drawerContent()
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    // ⚠️ 让**内容层**重报一次窗口尺寸：侧栏常驻之后，内容实际可用的宽度比窗口小
                    // 一截（少掉侧栏那一截）。共用页面判断要不要双栏、图库排几列、面板最高多少
                    // 用的都是 `LocalWindowSize` — 不扣掉侧栏的话，700dp 的窗口会被当成宽屏，
                    // 生成页硬切成两栏，实际每栏只有 200dp 出头，挤成一团。
                    // 收纳之后侧栏窄了，内容区跟着变宽 — 所以这里用 `sidebarWidth` 而不用 `width`。
                    CompositionLocalProvider(
                        LocalWindowSize provides DpSize(
                            width = (windowSize.width - sidebarWidth).coerceAtLeast(0.dp),
                            height = windowSize.height,
                        ),
                    ) {
                        content()
                    }
                }
            }
            // **侧栏右边缘的可拖把手**：压在"侧栏 / 内容"的分界线上（左右各占 3dp）。
            // 收纳态不用 — 那时候宽度是常量 [CollapsedSidebarWidth]。
            if (!collapsed) {
                SidebarResizeHandle(
                    widthDp = sidebarWidthDp,
                    minWidth = sidebarMinWidth,
                    maxWidth = sidebarMaxWidth,
                    onCommit = onSidebarWidthCommit,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = sidebarWidth - SidebarHandleWidth / 2),
                )
            }
        }
        return
    }

    val density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }

    // 手势里要读「当前允不允许拉」，但**不能**把它放进 pointerInput 的 key 里
    // 翻页过程中这个值会变，key 一变 Compose 就取消正在进行的手势协程。
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

    // 汉堡按钮 / 点导航项 / 返回键引起的开合，走后端动画。
    LaunchedEffect(open, widthPx) {
        dragging = false
        target = if (open) 0f else -widthPx
    }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(widthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val alreadyOpen = target > -widthPx + 1f
                    // 图库页不让「右滑拉出」，但已经开着时仍然允许往回收
                    if (!drawable && !alreadyOpen) return@awaitEachGesture

                    // ⚠️ **不要**写成 `var claiming = alreadyOpen`。
                    // 那样写等于抽屉一开着就立刻认领手势，于是下面每一帧移动都
                    // `change.consume()` — 手指按下去那几像素的抖动也算位移。
                    // 结果是**抽屉里的点击全被这一层吃掉**。
                    //   · 「工具」那一项展不开（点击进不到 NavigationDrawerItem）；
                    //   · 工具列表也滚不动（纵向位移同样被 consume）。
                    // 而且松手时会走到下面的收口逻辑：在"当前页不允许手势"时
                    // （比如底栏抽屉开着 → `drawable = false`）`settleOpen` 算成 false。
                    // 把整个侧边栏**收回去**。用户报的「点工具会收回」就是这个。
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
                                    // ⚠️ 必须"**明确右滑且横向占优**"才接管，而且接管后**一律不 consume**。
                                    // 早先只看"第一段位移偏横向就 claim"：连续快滑时手指起步那一两帧的
                                    // 反向抖动会被当成"开抽屉"，吃掉手势、同时取消 pager 正在进行的
                                    // 翻页动画 → 画面落回生图页（用户反复反馈"快滑会退页"）。
                                    val beyondSlop = abs(travelled) > slop || abs(travelledY) > slop
                                    if (!beyondSlop) {
                                        // 还没过阈值：继续观察，什么都不做
                                    } else if (alreadyOpen) {
                                        // 抽屉**已经开着**：向左拖 = 往回收起。
                                        // 向右拖没有意义（target 已经贴在 0 上，coerceIn 会夹住）。
                                        // 所以那种情况不认领、也不 consume，让事件原样落到子级。
                                        // 纵向占优同理让开 — 抽屉里的工具列表要能滚。
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
                                        // 左滑 / 纵向 / 斜向：放手，交给 pager 或列表。
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
                        // 正常松手、被取消、指针丢失……都必须把状态收干净。
                        // 不留 `dragging = true`，也不留「半开却不动」的抽屉。
                        if (claiming) {
                            dragging = false
                            // 松手判据放宽：**先看甩动速度**（轻轻一甩就算）。
                            // 速度不够时只要拉过 25% 就认 — 原来要拖过一半，手感很硬。
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
            // 比之前的 0.32 淡一些 — 模糊本身已经在做隔离，不需要压那么黑。
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f * progress))
                    .pointerInput(Unit) { detectTapGestures { onOpenChange(false) } },
            )
            ModalDrawerSheet(
                // ---- 毛玻璃下半：抽屉自己半透明 ----
                // `drawerTonalElevation = 0`：色调抬升会给半透明底色再叠一层不透明色，
                // 否则会把这件事直接抵消掉，所以必须关掉。
                drawerContainerColor = MaterialTheme.colorScheme.surface
                    .copy(alpha = NaiGlass.PANEL_ALPHA),
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
                drawerTonalElevation = 0.dp,
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight()
                    .offset { IntOffset(offset.roundToInt(), 0) },
            ) {
                drawerContent()
            }
        }
    }
}

/**
 * 顶栏里滑出来的**胶囊搜索框**（角色图鉴用）。
 *
 * 为什么做成胶囊而不是占一整行：工具页顶栏高度有限，胶囊更贴合"画廊"的气质，
 * 也让「点放大镜 → 滑出 → 输入 → 回车收起」这条动线很干净。
 */
@Composable
private fun SearchCapsule(
    state: AppState,
    focusKey: String,
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
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            // 搜索框要登记焦点（空格闸门 `AppState.textInputFocused` 读它）：
            // 搜索词里带空格很常见（"white dress"），漏了在这个框里就打不出空格。
            .trackTextInputFocus(state, focusKey)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            // 加一层**深色描边**（用户要求）：胶囊在深底上原本和背景糊在一起，
            // 描边用主题的 outline 色，淡淡的但边界清楚（画廊风：线条要少，但不能没有）
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            }
        },
    )
}

/** 角色图鉴用的一层包装（搜索、标语/回车行为都在 AppState 里）。*/
@Composable
private fun SearchCapsule(state: AppState, t: (String) -> String) {
    SearchCapsule(
        state = state,
        focusKey = "shell.anima.search",
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

/** 顶部菜单条的高度（比页面标题栏那 52dp 矮一截：它是"窗口家具"，不该抢内容的地方）。*/
private val MENU_BAR_HEIGHT = 30.dp

/**
 * **窗口顶部那条菜单**（用户 2026-09-19，参考图是常见桌面 App 那一种
 * 「文件 / 编辑 / 视图 / 工具 / 窗口 / 帮助」）。
 *
 * ## 为什么自己画而不是用 Swing 的 `JMenuBar`
 *
 * `JMenuBar` 长在**原生标题栏那一层**里：底色/字体不跟主题走，深色模式下会是一块白条，
 * 和我们这套界面完全不搭。自绘一层 `Surface` + `DropdownMenu` 则和其它弹层同一套观感，
 * 而且能在里面直接用 `AppState` / `UiHost`。
 *
 * ## 每一项落在哪：
 *
 * | 菜单 | 说明 |
 * | --- | --- |
 * | 文件 | 导入图片（和"选图"一样走 `importDroppedImage`，和「导入图片」按钮、拖放进来的图同一条路）、导出/导入备份（和「我的」页那两颗按钮同源）、**导出 PSD（第 ⑱ 批搬进来的，`Ctrl+Shift+S`）/ 导出这一页 PNG**、预设（打开风格预设面板）、退出 |
 * | 编辑 | 撤销 / 重做 / 清空这个框 — 见 `AppState.lastEditField`（最后动过的那个框；菜单离文本框很远，没有"当前聚焦"这回事） |
 * | 视图 | 深色模式（勾选）、收纳侧栏（勾选）、重排面板（把浮动的两块吸附回原位） |
 * | 工具 | 五个页面 + 画布编辑器 |
 * | 窗口 | 分离 提示词 / 角色与分镜 / 参数（勾上 = 已在独立窗口里，见桌面入口 `Main.kt`） |
 * | 帮助 | 关于、打开数据文件夹 |
 *
 * 右边 `trailing` 是那颗余额胶囊（只在文生图页给）— 菜单条就是窗口最上面那一条，
 * 放这儿比让它浮在画布右上角（会压住菜单和图片）合适。
 */
@Composable
private fun WindowMenuBar(
    state: AppState,
    t: (String) -> String,
    tabs: List<Pair<String, androidx.compose.ui.graphics.vector.ImageVector>>,
    sidebarCollapsed: Boolean,
    onToggleSidebar: () -> Unit,
    showDrawerButton: Boolean,
    onOpenDrawer: () -> Unit,
    windowTitle: String,
    trailing: @Composable () -> Unit = {},
) {
    val uiHost = LocalUiHost.current
    val platform = LocalPlatform.current
    val shortcuts = LocalPageShortcuts.current
    // 自绘标题栏那道"窗口能力"（电脑入口 provide；手机拿到默认值 = 不拖、不画窗口按钮）
    val chrome = LocalWindowChrome.current
    val language = state.settings.language

    // 文件 → 导入图片：和生图页那颗按钮、以及拖进来的图走同一条路
    val importLauncher = uiHost.rememberImagePicker { ref ->
        if (ref != null) state.importDroppedImage(ref)
    }
    // 文件 → 备份：导出用"另存为"、导入用"打开文件"（和「我的」页那两颗按钮同源）
    val exportBackupLauncher = uiHost.rememberFileCreator("application/json") { ref ->
        if (ref != null) state.exportBackup(ref)
    }
    val exportImageLauncher = uiHost.rememberFileCreator("image/png") { ref ->
        val path = state.workImagePath
        if (ref != null && path != null) state.exportImageFile(path, ref)
    }
    val importBackupLauncher = uiHost.rememberFilePicker(
        listOf("application/json", "image/png", "text/plain"),
    ) { ref -> if (ref != null) state.importBackup(ref) }

    var aboutOpen by remember { mutableStateOf(false) }
    // 快捷键设置对话框（帮助菜单里那个）
    var shortcutsOpen by remember { mutableStateOf(false) }
    val shortcutBindings = LocalShortcutBindings.current
    val lastField = state.lastEditField

    // ---- 菜单快捷键：**入口收键、这里干活**（见 `ShellCommands` 的注释）----
    // 菜单上标了快捷键就必须真的能用（用户 2026-09-19：参考图里每条都带快捷键）
    // 而且「加上快捷键」是他明确要的）。这里把命令 id 落到具体动作上，
    // 入口 `Window(onKeyEvent)` 负责把 Ctrl+O / Ctrl+S / … 翻译成 id
    val shellCommands = LocalShellCommands.current
    // ---- 画布命令：**菜单发号、画布收号**（见 `CanvasViewCommands` 的注释）----
    // 「视图 → 重置图片位置」点的是这里发出去的一条命令；真正把 `previewScale` / `previewOffset`
    // 摆回 1 倍 + 居中、并写盘的是生成页画布（`GenerateScreen.PreviewCard`）。
    // ⚠️ 没人登记（比如用户停在图库页、生成页不在组合树里）时 `resetPlacement()` 返回 false，
    //    这一项就是"什么也不做"—— 刻意**不**在这里顺手 `goToPage(0)`：
    //    翻页要等下一帧才组合出画布，而命令是当场发的，翻过去也接不到（只会白跳一下页）。
    val canvasViewCommands = LocalCanvasViewCommands.current
    DisposableEffect(shellCommands, state, shortcuts) {
        shellCommands.register { id ->
            when (id) {
                "importImage" -> {
                    importLauncher.launch(null)
                    true
                }
                "exportBackup" -> {
                    exportBackupLauncher.launch("NAI-Studio-backup.json")
                    true
                }
                "exportCurrentImage" -> {
                    if (state.workImagePath != null) {
                        exportImageLauncher.launch("NAI-Studio-image.png")
                        true
                    } else false
                }
                "importBackup" -> {
                    importBackupLauncher.launch(null)
                    true
                }
                "presets" -> {
                    shortcuts.goToPage(0)
                    state.openStyleSheet()
                    true
                }
                "darkMode" -> {
                    state.setTheme(if (state.settings.theme == "dark") "light" else "dark")
                    true
                }
                "sidebar" -> {
                    onToggleSidebar()
                    true
                }
                "cancelGeneration" -> {
                    if (state.berserkBusy) {
                        state.cancelBerserk()
                        true
                    } else if (state.busy || state.infiniteRunning) {
                        state.cancelGeneration()
                        true
                    } else false
                }
                "upscale" -> {
                    if (state.workImagePath != null && !state.busy) {
                        state.officialUpscale()
                        true
                    } else false
                }
                "director" -> {
                    shortcuts.goToPage(0)
                    state.directorShortcutOpen = true
                    true
                }
                "maskToggle" -> {
                    if (state.canvasMode == 0 && state.workImagePath != null) {
                        state.toggleMaskMode()
                        true
                    } else false
                }
                "settings" -> {
                    shortcuts.goToPage(SETTINGS_INDEX)
                    true
                }
                "shortcutsDialog" -> {
                    shortcutsOpen = true
                    true
                }
                "resetPlacement" -> canvasViewCommands.resetPlacement()
                "fitCanvas" -> canvasViewCommands.resetPlacement()
                "zoomIn" -> canvasViewCommands.zoomIn()
                "zoomOut" -> canvasViewCommands.zoomOut()
                "toggleInfinite" -> {
                    shortcuts.goToPage(0)
                    state.chooseCanvasMode(if (state.canvasMode == 1) 0 else 1)
                    true
                }
                // ⛔ 原来这里还有 `detachConsole` / `detachBottomBar` 两个动作 ✓ ——
                //    分离功能整个去掉之后它们没有意义了 ✓（用户 2026-09-24 ✓，见 `Shortcuts.kt` ✓）。
                "canvasEditor" -> {
                    shortcuts.goToPage(0)
                    state.openCanvasEditor()
                    true
                }
                "quit" -> {
                    uiHost.exitApp()
                    true
                }
                // 用户 2026-09-19：`Ctrl+Enter` = **执行一次生图**（和底部那颗「生成图片」同一个入口）
                "generate" -> {
                    shortcuts.goToPage(0)
                    state.generate()
                    true
                }
                else -> false
            }
        }
        onDispose { shellCommands.register(null) }
    }

    Surface(
        // 顶栏这块小条走 `NaiSkinTokens.bar()`：玻璃风格 = 半透明（同改动之前逐像素一致），
        // 实色风格 = 参考稿那种不透明 `#232326`（用户 2026-09-27「两套共存、可切换」）。
        color = NaiSkinTokens.bar(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth().height(MENU_BAR_HEIGHT),
    ) {
        Row(
            // ⚠️ 这一行现在**就是窗口标题**（系统标题栏已经关掉，见 `WindowChrome` 的注释）。
            // `chrome.titleBarDrag` 的"空白处按住拖 = 拖窗口、双击 = 最大化/还原"生效。
            // 菜单标题与三颗按钮是它的子节点，点它们不会触发拖动（子节点先把事件消费掉）。
            Modifier.fillMaxSize().then(chrome.titleBarDrag).padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ---- 最左：App 图标（和侧栏 / 开屏同一颗 `AppLogo`）----
            // 用户 2026-09-19：「（菜单）放在 logo 旁边，不是做一个顶栏」
            AppLogo(size = 18.dp, modifier = Modifier.padding(start = 2.dp, end = 6.dp))

            // 窄窗口（侧栏不是常驻）时抽屉只能从这儿开
            if (showDrawerButton) {
                IconButton(onClick = onOpenDrawer, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = t("drawer.open"),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(2.dp))
            }

            // ---------------- 文件 ----------------
            MenuTitle(t("menu.file")) { close ->
                MenuAction(t("menu.importImage"), shortcut = shortcutBindings.keysFor("importImage")) {
                    close()
                    importLauncher.launch(null)
                }
                MenuDivider()
                MenuAction(t("menu.exportBackup"), enabled = !state.busy, shortcut = shortcutBindings.keysFor("exportBackup")) {
                    close()
                    exportBackupLauncher.launch("NAI-Studio-backup.json")
                }
                MenuAction(t("menu.exportCurrentImage"), enabled = state.workImagePath != null,
                    shortcut = shortcutBindings.keysFor("exportCurrentImage")) {
                    close()
                    exportImageLauncher.launch("NAI-Studio-image.png")
                }
                MenuAction(t("menu.importBackup"), enabled = !state.busy, shortcut = shortcutBindings.keysFor("importBackup")) {
                    close()
                    importBackupLauncher.launch(null)
                }
                MenuDivider()
                MenuAction(t("menu.presets"), shortcut = shortcutBindings.keysFor("presets")) {
                    close()
                    // 预设面板长在生成页里：先把用户送回那一页。
                    shortcuts.goToPage(0)
                    state.openStyleSheet()
                }
                MenuDivider()
                MenuAction(t("menu.quit"), shortcut = shortcutBindings.keysFor("quit")) {
                    close()
                    uiHost.exitApp()
                }
            }

            // ---------------- 编辑 ----------------
            MenuTitle(t("menu.edit")) { close ->
                MenuAction(
                    text = t("menu.undo"),
                    enabled = state.canUndoPrompt(lastField),
                    shortcut = "Ctrl+Z",
                ) {
                    close()
                    state.undoPrompt(lastField)
                }
                MenuAction(
                    text = t("menu.redo"),
                    enabled = state.canRedoPrompt(lastField),
                    shortcut = "Ctrl+Y",
                ) {
                    close()
                    state.redoPrompt(lastField)
                }
                MenuDivider()
                MenuAction(t("menu.clearField")) {
                    close()
                    state.clearPromptField(lastField)
                }
            }

            // ---------------- 视图 ----------------
            MenuTitle(t("menu.view")) { close ->
                MenuAction(
                    text = t("menu.darkMode"),
                    checked = state.settings.theme == "dark",
                    shortcut = shortcutBindings.keysFor("darkMode"),
                ) {
                    close()
                    state.setTheme(if (state.settings.theme == "dark") "light" else "dark")
                }
                MenuAction(
                    text = t("menu.collapseSidebar"),
                    checked = sidebarCollapsed,
                    enabled = !showDrawerButton,
                    shortcut = shortcutBindings.keysFor("sidebar"),
                ) {
                    close()
                    onToggleSidebar()
                }
                MenuDivider()
                MenuAction(t("menu.resetPanels")) {
                    close()
                    shortcuts.goToPage(0)
                    state.requestPanelRedock()
                }
                // 用户 2026-09-26：「在视图里加上个重置图片位置的按钮」——
                // 把画布上那张图摆回 **1 倍 + 居中**（并写盘）。面板那一条是 `AppState` 的计数器，
                // 这一条走 `CanvasViewCommands` 信箱（摆放状态住在画布里，见那边的注释）。
                MenuAction(t("menu.resetImagePlacement")) {
                    close()
                    canvasViewCommands.resetPlacement()
                }
            }

            // ---------------- 工具 ----------------
            MenuTitle(t("menu.tools")) { close ->
                tabs.forEachIndexed { index, tab ->
                    MenuAction(text = tab.first, shortcut = "Ctrl+Alt+${index + 1}") {
                        close()
                        shortcuts.goToPage(index)
                    }
                }
                MenuDivider()
                MenuAction(t("menu.canvasEditor"), shortcut = shortcutBindings.keysFor("canvasEditor")) {
                    close()
                    shortcuts.goToPage(0)
                    state.openCanvasEditor()
                }
            }

            // ---------------- 窗口 ----------------
            MenuTitle(t("menu.window")) { close ->
                // **显示 / 隐藏**那两块主面板（勾上 = 显示；用户 2026-09-19）
                // 「窗口选项里添加显示某某窗口的选项，可以隐藏窗口」）
                MenuAction(t("menu.showPromptPanel"), checked = !state.promptPanelHidden) {
                    close()
                    state.togglePromptPanel()
                }
                MenuAction(t("menu.showBottomBar"), checked = !state.bottomBarHidden) {
                    close()
                    state.toggleBottomBar()
                }
                MenuDivider()
                // ⛔ **「分离操作面板」整块去掉了** ✗（用户 2026-09-24：
                //    「窗口的分离功能去掉，以后不需要分离了，**中控底栏变成固定的，按现在的形态**」✓）。
                //
                // 去掉的不止是这两条菜单项 ✓：`AppState` 那边把"浮动 / 分离"整套也钉死了 ✓ ——
                //  · `promptPanelFloating` / `bottomBarFloating` **恒为 false** ✓（永远停靠 ✓）；
                //  · `detachedPanels` **恒为空集** ✓（角色 / 参数那两栏也不再能飘出去 ✓）；
                //  · 老设置里那几个键**一律不读** ✗（否则老用户升级后还是浮动的、而菜单里已经没处收回 ✗）。
                // 快捷键那两条（`detachConsole` / `detachBottomBar`）也一并从 `Shortcuts.kt` 撤了 ✓。

                // ⛔ 「统计玻璃 A/B/C/D」那几条对照项**撤掉了** ✗ —— 用户 2026-09-24 已经选定
                //    **⑨c 苹果风·压扁** ✓（写死在 `statsGlassStyle` ✓），留着就是"点了没用"的假控件 ✓。
            }

            // ---------------- 帮助 ----------------
            MenuTitle(t("menu.help")) { close ->
                MenuAction(t("menu.about")) {
                    close()
                    aboutOpen = true
                }
                MenuAction(t("menu.openDataDir")) {
                    close()
                    // browse(file:) 在 Windows 上就是把资源管理器打开到那个目录。
                    uiHost.openUrl(platform.paths.filesDir.toURI().toString())
                }
                // 「快捷键…」（用户 2026-09-19：「在帮助里添加一个快捷键选项，可以改变各个功能的快捷键」）
                MenuAction(t("menu.shortcuts")) {
                    close()
                    shortcutsOpen = true
                }
            }

            // ---- 菜单右边是**窗口标题**（用户 2026-09-19 给了参考图：菜单之后紧跟标题）----
            // 就是任务栏 / Alt+Tab 上那串（`NAI Studio → 生图`），工具页显示当前工具名。
            // ⚠️ 它只是**展示**在这儿：真正的窗口标题仍由入口 `Window(title = …)` 给（任务栏认那个）。
            Text(
                text = windowTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 14.dp, end = 8.dp),
            )

            Spacer(Modifier.weight(1f))
            trailing()
            // ---- 最右：最小化 / 最大化-还原 / 关闭（电脑才有）----
            WindowControlButtons(chrome)
        }
    }

    // 「帮助 → 快捷键…」：能改各个功能的键位（用户 2026-09-19）
    if (shortcutsOpen) {
        ShortcutSettingsDialog(
            bindings = shortcutBindings,
            t = t,
            onDismiss = { shortcutsOpen = false },
        )
    }

    if (aboutOpen) {
        AlertDialog(
            onDismissRequest = { aboutOpen = false },
            confirmButton = {
                TextButton(onClick = { aboutOpen = false }) { Text(t("common.confirm")) }
            },
            title = { Text(t("settings.about")) },
            text = {
                Text(
                    buildString {
                        append("NAI Studio")
                        append("\n")
                        append(t("settings.version"))
                        append(" ")
                        append(platform.appVersion)
                        if (platform.debugBuild) append(" (debug)")
                    },
                )
            },
        )
    }
}

/** 菜单标题：点一下在它下面弹出那一栏（`DropdownMenu` 是真弹窗，不会被顶栏裁掉）。*/
@Composable
private fun MenuTitle(
    label: String,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { open = true }
                .padding(horizontal = 9.dp, vertical = 5.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            content { open = false }
        }
    }
}

/**
 * 菜单里的一条（用户 2026-09-19 给的参考图：**左边菜单名、右边灰色快捷键**，分组用细线隔开）。
 * 第二版按用户「字体要缩小、紧凑、加上快捷键」调过）。
 *
 * ⚠️ **不用 M3 的 `DropdownMenuItem`**：它写死了 48dp 行高、`bodyLarge` 字号。
 * 怎么调都比参考图松、比参考图大。这里自己排一行：`bodySmall` + 上下 5dp、
 * 行高 26dp — 和参考图那种紧凑菜单一个观感。
 *
 * @param shortcut 只有**真实生效**的快捷键才写（纯英文，不用进 i18n）。
 *   挂个按不动的提示比不写更糟，所以没接线的动作一律留空。
 * @param checked 勾选态（深色模式 / 收纳侧栏 / 分离窗口那几个开关）。
 */
@Composable
private fun MenuAction(
    text: String,
    enabled: Boolean = true,
    checked: Boolean = false,
    shortcut: String? = null,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ⚠️ 勾选标记**用图标画，不要用 "✓" 这个字符**（用户 2026-09-20 报「窗口显示乱码」）：
        // 菜单那套字体渲染不出 `✓`，屏幕上就是一个方块/问号 ✗。图标是矢量，一定有。
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) scheme.onSurface else scheme.onSurface.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (shortcut != null) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(18.dp))
            Text(
                text = shortcut,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.38f),
                maxLines = 1,
            )
        }
    }
}

/**
 * 菜单里的**子菜单（分组）**：一行父项 + 缩进的子项（用户 2026-09-26 的截图就是这种
 * "带勾选框的子菜单"，例如「分离操作面板」下面挂着「分离中控台 / 分离底栏」）。
 *
 * ## 为什么是"同层展开"而不是右侧飞出的嵌套菜单
 *
 * Compose Desktop 里每扇 `DropdownMenu` 都是一层**独立的 `Popup`**：子层弹出来时会去抢焦点，
 * 父层什么时候收到 `onDismissRequest` 跟平台实现绑得很紧 —— 万一父层在子层弹出的同一帧自己关掉，
 * 那子菜单就永远点不开。这条**没法在这里真机验证**，所以选了不会失败的那种做法：
 * 点父项**就地展开 / 收起**子项（父项右边一颗上/下箭头），子项缩进排在**同一层弹窗**里。
 * 层级、勾选框、快捷键标注都和截图一致。
 *
 * ⚠️ 子项里的 `close()` 直接用外层 `MenuTitle` 给的那个（Kotlin 闭包捕获）——点一条就把整扇菜单收掉。
 */
@Composable
private fun MenuGroup(
    text: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = !open }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(18.dp))
        Icon(
            imageVector = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
    }
    if (open) {
        // 子项整体缩进一格：一眼看出是"挂在上面那条下面"的
        Column(Modifier.fillMaxWidth().padding(start = 12.dp)) { content() }
    }
}

/** 菜单里的分组线。*/
@Composable
private fun MenuDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * 排序图标（三条渐短的横线）。`material-icons-core` 里没有排序图标，
 * 为一个图标引 material-icons-extended（几 MB）不值得，所以直接画。
 */
@Composable
private fun SortIconButton(contentDescription: String, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun AccountChip(state: AppState, t: (String) -> String) {
    val account = state.account
    val params = state.params

    val tier = account.tierName ?: "API"
    val balance = account.anlasBalance?.toString() ?: "—"
    val connected = state.hasToken && !account.stale

    // 与参考实现一致：只有 Opus + V5 模型才显示 V5 免费额度
    val showV5Allowance = account.tierLevel == 3 && params.model.startsWith("nai-diffusion-5-")
    val usage = account.opusUsage
    val percent = if (showV5Allowance && usage != null) {
        if (usage.isNegative) 0 else usage.percent.coerceIn(0.0, 100.0).toInt()
    } else {
        null
    }

    var expanded by remember { mutableStateOf(false) }
    val pageShortcuts = LocalPageShortcuts.current
    val scheme = MaterialTheme.colorScheme

    /**
     * 余额小窗口（用户 2026-09-19 给了两张参考图）：
     *  · **平视**（收起）：一颗胶囊 `[档位] ≈21,879  V5 100% ▾`
     *  · **点开**：下拉一扇小面板 — 连接状态 + 配置 Token / 刷新、Anlas 余额、V5 免费额度（带进度条）。
     *
     * 设计语言沿用 App 那一套：胶囊用 [NaiShape.Pill]，面板由 `DropdownMenu` 自带
     * 的 `surfaceContainer` + 阴影给出（和其余弹层同一个观感），进度条用主色 primary。
     */
    Box {
        Surface(
            shape = NaiShape.Pill,
            color = if (LocalNaiSkin.current == NaiSkin.Reference) {
                NaiSkinTokens.bar()
            } else {
                scheme.surfaceContainerHigh
            },
            tonalElevation = 0.dp,
            modifier = Modifier.clickable { expanded = true },
        ) {
            Row(
                Modifier.padding(start = 8.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 档位胶囊（连接正常是主色底，兜底/未配置是灰底）
                Surface(
                    shape = NaiShape.Pill,
                    color = if (connected) scheme.primaryContainer else scheme.surfaceContainerHighest,
                    contentColor = if (connected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = tier,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                // 「◆」+ Anlas 的记号（不引图标库，直接画一个菱形）
                Canvas(Modifier.size(9.dp)) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width / 2f, 0f)
                        lineTo(size.width, size.height / 2f)
                        lineTo(size.width / 2f, size.height)
                        lineTo(0f, size.height / 2f)
                        close()
                    }
                    drawPath(path, color = scheme.primary)
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (state.hasToken) balance else t("generate.notConfigured"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (percent != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "V5",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary,
                    )
                }
                // 右边那颗向下的小箭头（点开就是下面那扇面板）
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = t("account.details"),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Column(Modifier.width(320.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
                // ---- 头：状态点 + 档位胶囊 + 连接文案 + 右侧两颗按钮 ----
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (connected) scheme.primary else scheme.error,
                            ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = NaiShape.Pill,
                        color = if (connected) {
                            scheme.primaryContainer
                        } else {
                            scheme.surfaceContainerHighest
                        },
                        contentColor = if (connected) {
                            scheme.onPrimaryContainer
                        } else {
                            scheme.onSurfaceVariant
                        },
                        tonalElevation = 0.dp,
                    ) {
                        Text(
                            text = tier,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            !state.hasToken -> t("generate.notConfigured")
                            account.stale -> t("account.stale")
                            else -> t("account.connected")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            // 「配置 Token」跳到设置页（token 在那一页填）
                            // ⚠️ 用 [SETTINGS_INDEX] 而不是写死数字：这个下标**改过一次**
                            //（高级漫画插过一页、又删掉了，见常量的说明 ✓），写死就会跳错页 ✗
                            pageShortcuts.goToPage(SETTINGS_INDEX)
                            expanded = false
                        },
                    ) { Text(t("account.configureToken"), style = MaterialTheme.typography.labelMedium) }
                    TextButton(onClick = { state.refreshAnlas() }) {
                        Text(t("account.refresh"), style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = scheme.outlineVariant,
                )

                // ---- Anlas 余额 ----
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = t("account.anlasBalance"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = balance,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // ---- V5 免费额度（只有 Opus + V5 模型才有这一行）----
                if (percent != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = t("account.v5Allowance"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${"%.1f".format(usage?.percent ?: 0.0)}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(NaiShape.Pill),
                        color = scheme.primary,
                        trackColor = scheme.surfaceContainerHighest,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 工具浮动窗口
//
// 用户 2026-09-20：「工具这些功能**不用跳转页面**，出现一个**和提示词框差不多的新窗口**，
// 要有 X 键」。于是侧边栏「工具」下面那几项点一下 = 在外壳这一层浮出一扇窗口（不再翻到工具页）。
//
// 形态照生成页那几块浮动面板（`GenerateScreen`）：圆角 + 边框 + **实体底色**（不透背后的壁纸/画布）。
// 这一版**不做缩放**（用户没要），只做"能拖 + 右上角一颗 X"。
//
// ⚠️ 拖动为什么挂在**标题条**上（而生成页那边是"内容格统一命中测试"）：
//   · 工具窗口盖在所有页面之上。照那边做"全局命中测试"的话，这一层的 pointerInput
//     会把底下的手势**全吃掉**（提示词栏 / 底栏 / 画布就都点不动了）；
//   · 只把手指按在标题条上，命中范围就限于这扇窗口自己 —— 不碰别人的手势。
//   ⚠️ 这里也没有缩放把手，所以"热区长在会变大的边上"那个坑不存在。
//   ⚠️ `dragAmount` 是**相对被拖节点**的位移，而标题条长在会动的窗口上 —— 正是 `GenerateScreen`
//     那段注释里"拖不动"的形状。但那边还叠了缩放 + 自定义命中测试，这里只有拖动一件事：
//     累计位移写进 `.offset { }` 读的状态即可（侧栏那条可拖分隔条也是"边动边跟着指针"的写法）。
//     万一真拖不动，下一手是照生成页那样把指针接收挪到不动的父层。

/** 工具窗口的默认尺寸（比提示词栏宽、比整屏小，够放两道网格的图鉴）。 */
private val ToolWindowWidth = 520.dp
private val ToolWindowHeight = 640.dp

/** 标题条高度：比分离面板那条 `26dp` 高一点 —— 这里还要塞右上角那颗 X。 */
private val ToolWindowBarHeight = 36.dp

/** 窗口离容器边的留白（默认落点与夹取都用它）。 */
private val ToolWindowMargin = 12.dp

/** 每多开一扇，默认落点往右下错开这么多，免得几扇窗口叠成一摞。 */
private val ToolWindowCascade = 28.dp

/**
 * **工具窗口之间的吸附半径**（用户 2026-09-21：「工具窗口不能吸附」）。
 *
 * 口径照生成页那几块浮动面板：拖到**另一扇工具窗口**的左边 / 右边 / 上边 / 下边
 *（± 这个半径）时"吸上去"。⚠️ 生成页那份 `PANEL_SNAP_DP` 是 `private`、而且那个文件
 * 这一手不许动，所以这里自己定一个：桌面鼠标拖动本来就好瞄，12dp 够用又不至于
 *"离得老远就被拽走"。
 */
private val ToolWindowSnap = 12.dp

/** 吸附高亮那条细线的厚度（和生成页 `snapAgainst` 里那个 3dp 同一个口径）。 */
private val ToolWindowSnapThickness = 3.dp

/** 吸附高亮的 z 序：压在**所有**工具窗口之上（窗口的 zIndex 从 1f 起、按开启数量往上排）。 */
private const val TOOL_WINDOW_SNAP_Z = 10_000f

/** "没记过位置"的哨兵值（和 kv 里真存着的像素值区分开）。 */
private const val NO_SAVED_WINDOW_POS = Int.MIN_VALUE

/** 每扇窗口的位置落盘键（`tools.window.<id>.x` / `.y`）。 */
private fun toolWindowKey(id: String, axis: String) = "tools.window.$id.$axis"

/**
 * **吸附的通用实现：把一个矩形贴到"任意一组候选矩形"的边上**。
 *
 * 2026-09-21 从 `ToolWindowLayer` 里那版"只吃别的工具窗口"的吸附抽出来 —— 用户这一手要求
 * 「工具窗口也要和**生成页那几块面板**（提示词栏 / 底栏 / 分离出来的框）吸附」，于是候选
 * 不再写死成工具窗口，改成**任意矩形列表**；口径一个字都没改：
 * 横竖两个方向**各自**找最近的一条边，所以能"贴着 A 的右边、同时贴着 B 的下边"。
 * 候选一共八条（对每个候选矩形）：
 *  · 竖边：贴它左边（左对齐）/ 贴它右边（右对齐）/ 排在它左边 / 排在它右边；
 *  · 横边：贴它上边（顶对齐）/ 贴它下边（底对齐）/ 排在它上边 / 排在它下边。
 *
 * 命中条件：该方向的偏差 < [snapPx]，**并且**垂直方向（竖边）/ 水平方向（横边）真的挨着
 *（`overlaps`）—— 否则拖到窗口的斜对角、"离得老远也吸一下"看着像吸到了空气。
 *
 * @param candidates 候选矩形。⚠️ 必须和 [target] **同坐标系**：工具窗口这套用的是**窗口客户区
 *   坐标**，而生成页那几块面板由 `boundsInWindow()` 报上来、正好也是窗口客户区坐标 ——
 *   **同源，这里不做任何偏移换算**（加了反而会错开一个侧栏宽度）。
 * @return 贴合后的位置 + 要画的"被贴那条边"的高亮矩形（同坐标系，`+x` 向右 / `+y` 向下）。
 */
private fun snapToRects(
    target: Offset,
    size: Size,
    candidates: List<Rect>,
    snapPx: Float,
    snapThinPx: Float,
): Pair<Offset, List<Rect>> {
    val targetRect = Rect(target.x, target.y, target.x + size.width, target.y + size.height)
    // 每一项 = (候选值, (被贴的那条边, 这条边从哪儿到哪儿))。高亮画在**被贴的那条边**上，
    // 所以第二项记的是**对面**那个矩形的边（比如"排在它左边"这个候选贴的是对面的 `r.left`）。
    val ex = mutableListOf<Pair<Float, Pair<Float, ClosedFloatingPointRange<Float>>>>()
    val ey = mutableListOf<Pair<Float, Pair<Float, ClosedFloatingPointRange<Float>>>>()

    fun overlaps(a: Float, b: Float, c: Float, d: Float): Boolean =
        minOf(b, d) - maxOf(a, c) > -snapPx

    candidates.forEach { r ->
        if (overlaps(targetRect.top, targetRect.bottom, r.top, r.bottom)) {
            ex += r.left to (r.left to r.top..r.bottom)                     // 左对齐
            ex += r.right to (r.right to r.top..r.bottom)                   // 右对齐
            ex += (r.left - size.width) to (r.left to r.top..r.bottom)      // 排在它左边
            ex += (r.right - size.width) to (r.right to r.top..r.bottom)    // 排在它右边
        }
        if (overlaps(targetRect.left, targetRect.right, r.left, r.right)) {
            ey += r.top to (r.top to r.left..r.right)                       // 顶对齐
            ey += r.bottom to (r.bottom to r.left..r.right)                 // 底对齐
            ey += (r.top - size.height) to (r.top to r.left..r.right)       // 排在它上边
            ey += (r.bottom - size.height) to (r.bottom to r.left..r.right) // 排在它下边
        }
    }

    val bestX = ex.filter { abs(it.first - target.x) < snapPx }
        .minByOrNull { abs(it.first - target.x) }
    val bestY = ey.filter { abs(it.first - target.y) < snapPx }
        .minByOrNull { abs(it.first - target.y) }

    val highlights = mutableListOf<Rect>()
    bestX?.let { (_, edge) ->
        highlights += Rect(
            edge.first - snapThinPx / 2f,
            edge.second.start,
            edge.first + snapThinPx / 2f,
            edge.second.endInclusive,
        )
    }
    bestY?.let { (_, edge) ->
        highlights += Rect(
            edge.second.start,
            edge.first - snapThinPx / 2f,
            edge.second.endInclusive,
            edge.first + snapThinPx / 2f,
        )
    }
    return Offset(bestX?.first ?: target.x, bestY?.first ?: target.y) to highlights
}

/**
 * **「统计」那颗按钮 + 贴着它弹出的那扇小毛玻璃窗**（用户 2026-09-23 三次定稿 ✓）。
 *
 * 最后一次口径原文：
 * > 「统计……**要放在按钮旁边的小窗口**，呼出后**再点击统计或点击窗口外会收回**，
 * >  小窗口**大小参考呼出画师超市的大小**，**无边框，圆角，毛玻璃**」✓
 *
 * ## 怎么做到的
 *
 *  · **贴按钮**：按钮外面包一层 `Box`，`Popup` 就声明在这个 `Box` 里 ✓ ——
 *    Compose 的 `Popup` 锚点正是"它所在的那个布局节点" ✓，于是 `calculatePosition` 拿到的
 *    `anchorBounds` 就是**这颗按钮自己的矩形** ✓（`Popup(offset = …)` 那种按窗口左上角算的写法
 *    在桌面端会飞到左上角去 ✗，见 `CanvasEditor` 里同一条注释 ✓）；
 *  · **位置**：贴在按钮**右边** ✓（侧栏在最左 ✓，右边永远有地方 ✓），纵向让窗户**底边对齐按钮底边** ✓
 *    —— 统计那颗钉在侧栏底部 ✓，这么对齐它看着就是"从按钮旁边长出来"的 ✓；四个方向都夹在窗口内 ✓；
 *  · **收回**：`onDismissRequest`（点窗外 / 按 Esc ✓）+ **再点这颗按钮**（`onToggle` ✓）两条都走 ✓；
 *    ⚠️ 点按钮那一下**两条路都可能生效** ✓（焦点弹窗会先吃掉落点 ✓ 也可能落到按钮上 ✓）——
 *    所以 `onDismissRequest` 一律**关**（✗ 不是取反 ✓），按钮才取反 ✓：两条路都收敛到"关" ✓。
 *  · **大小**：照"呼出画师超市"那扇工具窗口 [ToolWindowWidth] × [ToolWindowHeight] ✓（520×640 ✓），
 *    窄窗口里按"窗口尺寸 − 两边留 [STATS_WINDOW_MARGIN]"夹 ✓（和工具窗口同一条兜底 ✓）；
 *  · **长相**：`NaiGlass.PANEL_ALPHA` 半透明面 + 圆角 ✓ + **不加描边** ✗（用户明说"无边框" ✓）。
 */
@Composable
private fun StatsAnchor(
    open: Boolean,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    state: AppState,
    t: (String) -> String,
    content: @Composable () -> Unit,
) {
    Box {
        content()
        if (!open) return@Box

        val density = LocalDensity.current
        val windowSize = LocalWindowSize.current
        // 大小：和工具窗口同一套夹法（窄窗口里别把 520×640 原样铺出去 ✓）
        val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
        val widthDp = minOf(
            if (referenceSkin) 320.dp else STATS_WINDOW_WIDTH,
            (windowSize.width - STATS_WINDOW_MARGIN * 2).coerceAtLeast(300.dp),
        )
        val heightDp = minOf(
            if (referenceSkin) 520.dp else STATS_WINDOW_HEIGHT,
            (windowSize.height - STATS_WINDOW_MARGIN * 2).coerceAtLeast(260.dp),
        )
        val gapPx = with(density) { STATS_WINDOW_GAP.toPx() }
        val minYPx = with(density) { MENU_BAR_HEIGHT.toPx() }

        Popup(
            popupPositionProvider = remember(gapPx, minYPx) {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        // 右边：贴着按钮右边缘留一条缝 ✓；⚠️ 先夹再取整（`coerceIn` 的 lo>hi 会抛 ✓）
                        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                        val x = (anchorBounds.right + gapPx.roundToInt()).coerceIn(0, maxX)
                        // 纵向：窗户**底边对齐按钮底边** ✓，再整体夹进窗口里（上边让开菜单栏 ✓）
                        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(minYPx.roundToInt())
                        val y = (anchorBounds.bottom - popupContentSize.height)
                            .coerceIn(minYPx.roundToInt(), maxY)
                        return IntOffset(x, y)
                    }
                }
            },
            // 点窗外 / 按 Esc ⇒ **关**（不是取反 ✓，理由见上面那段注释 ✓）
            onDismissRequest = onClose,
            properties = PopupProperties(focusable = true),
        ) {
            val statsWindow: @Composable () -> Unit = {
                StatsWindow(
                    state = state,
                    t = t,
                    onClose = onClose,
                    width = widthDp,
                    height = heightDp,
                )
            }
            if (referenceSkin) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(140)) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(140),
                    ),
                ) {
                    statsWindow()
                }
            } else {
                statsWindow()
            }
        }
    }
}

/**
 * 统计那扇小窗本体：圆角 + 毛玻璃 ✓（用户 2026-09-23 定的"无边框 ✓"；
 * 2026-09-24 用户说「毛玻璃质感有点塑料，**再做几个版本的方框方便作对比**」✓ ⇒ 外形参数化 ✓，
 * 由 [StatsGlassStyle] 决定 ✓，**默认那一款 = 他现在看到的样子** ✓）。
 */
@Composable
private fun StatsWindow(
    state: AppState,
    t: (String) -> String,
    onClose: () -> Unit,
    width: Dp,
    height: Dp,
) {
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    val style = if (referenceSkin) StatsGlassStyle.Reference else statsGlassStyle
    val look = statsGlassLook(style)
    val shape = RoundedCornerShape(if (referenceSkin) 14.dp else STATS_WINDOW_CORNER)
    val referenceAlpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.70f else 0.72f
    Surface(
        // 毛玻璃 = 半透明面（⑨c 那一档是 42% ✓）；
        // 描边 / 投影按款式给 ✓（⑨c = 1dp 亮边 + 16dp 大软投影 ✓ —— 苹果风的那两样 ✓）。
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (referenceSkin) referenceAlpha else look.alpha),
        tonalElevation = 0.dp,
        shadowElevation = look.shadow,
        border = if (referenceSkin) BorderStroke(1.dp, NaiSkinTokens.border()) else look.border,
        shape = shape,
        modifier = Modifier.width(width).then(
            if (referenceSkin) Modifier.heightIn(max = height) else Modifier.height(height),
        ),
    ) {
        Box(if (referenceSkin) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
            // 「磨砂」那款在窗内自己铺一层微光（右下略亮、左上略透 ✓）——
            // ⚠️ 这不是"背景模糊"✗（Popup 糊不到下面的画布 ✗，见 `StatsGlassStyle` 的说明 ✓），
            //    只是让这块玻璃有厚薄感、不至于像一块平涂的塑料 ✓。
            if (look.innerSheen) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.02f),
                                    Color.White.copy(alpha = 0.10f),
                                ),
                            ),
                        ),
                )
            }
            // ⑨c 苹果风的**内高光**（对比页上那两笔 ✓）：
            //  · 左上角一团柔光（斜光打在玻璃上 ✓，径向渐变 ✓）；
            //  · 顶上一条"上亮下透"的竖渐变（苹果那种玻璃厚度 ✓，`inset 0 1px` 的 Compose 版 ✓）。
            // ⚠️ 这两笔都画在**窗内** ✓ 不是"背后模糊" ✗（那一步要挪到窗口层 ✓，见枚举的说明 ✓）。
            if (look.appleGloss) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.30f),
                                    Color.Transparent,
                                ),
                                radius = with(LocalDensity.current) { 220.dp.toPx() },
                            ),
                        ),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.Transparent,
                                ),
                                endY = with(LocalDensity.current) { 90.dp.toPx() },
                            ),
                        ),
                )
            }
            Column(if (referenceSkin) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (referenceSkin) 18.dp else 12.dp,
                            end = if (referenceSkin) 12.dp else 4.dp,
                            top = if (referenceSkin) 12.dp else 6.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(t("stats.title"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    // ⚠️ 关掉这颗**显式给 34dp** ✗：`IconButton` 默认 48dp ✗ ——
                    //    340×440 的小窗里那 14dp 是白丢的 ✓（用户 2026-09-24 嫌窗子大 ✓）。
                    IconButton(onClick = onClose, modifier = Modifier.size(if (referenceSkin) 26.dp else 34.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = t("common.close"),
                            modifier = Modifier.size(if (referenceSkin) 15.dp else 18.dp),
                        )
                    }
                }
                // 「磨砂」那款**内容摊平** ✓（不再套三块浅底 ✓ —— 那三块不透明底是"塑料感"的一大来源 ✓）
                UsageStatsScreen(
                    state = state,
                    t = t,
                    modifier = if (referenceSkin) {
                        Modifier.weight(1f, fill = false).fillMaxWidth()
                    } else {
                        Modifier.weight(1f)
                    },
                    surfaceStyle = if (style == StatsGlassStyle.Frost) {
                        StatsSurfaceStyle.Flat
                    } else if (style == StatsGlassStyle.Reference) {
                        StatsSurfaceStyle.Reference
                    } else {
                        StatsSurfaceStyle.Card
                    },
                )
            }
        }
    }
}

/**
 * **窗口位置夹回容器里**。
 *
 *  · 左右与下边不许越出去（越出去那块就点不着了）；
 *  · 上边留出 [MENU_BAR_HEIGHT] 那条窗口菜单栏 —— 它是"系统标题栏"、永远画在窗口层之上，
 *    窗口要是钻到它底下，标题条（连同那颗 X）就抓不着了。
 *
 * ⚠️ `coerceIn` 在 `min > max` 时会抛异常，所以两个上界都先 `coerceAtLeast` 兜一下
 *（窗口比容器还小的极端情况）。
 */
private fun clampToolWindowOffset(
    value: Offset,
    windowWidthPx: Float,
    windowHeightPx: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
    minY: Float,
    /** 左边界（= 侧栏右边缘）：工具窗口不许盖住侧栏，否则侧栏点不动（用户 2026-09-20 报的）。 */
    minX: Float = 0f,
    /**
     * 下边界**只保这一截留在容器里**（调用处给的是标题条那一行 + 一点余量）。
     *
     * ⚠️ 2026-09-21 修用户报的「画布的下半部分放不下去」：原来下边界是
     * `containerHeight - windowHeight`（整扇窗都得在容器里），窗口高 640 时，
     * 1080 高的容器里**顶边最多只到 440** —— 画布下半部分永远够不着 ✗。
     * 现在和 Windows 拖窗口一个口径：**窗口主体可以压到容器下沿之外，
     * 只要标题条（抓手 + X）还留在里面**，就随时抓得回来。
     */
    keepVisiblePx: Float = 0f,
): Offset {
    val left = minX.coerceAtLeast(0f)
    val maxX = (containerWidthPx - windowWidthPx).coerceAtLeast(left)
    val top = minY.coerceAtLeast(0f)
    // 取"整扇窗装得下"与"只留 keepVisiblePx"里**更靠下**的那个 → 允许窗口下探出容器下沿
    val maxY = maxOf(
        containerHeightPx - windowHeightPx,
        containerHeightPx - keepVisiblePx,
    ).coerceAtLeast(top)
    return Offset(
        x = value.x.coerceIn(left, maxX),
        y = value.y.coerceIn(top, maxY),
    )
}

/**
 * **工具浮动窗口层**：把 [AppState.openToolWindows] 里那几个工具各画成一扇窗口。
 *
 * 位置只在 layout 阶段读（`.offset { }` 的 lambda），拖动每帧不重组 —— 和生成页那几块面板同源。
 */
@Composable
private fun ToolWindowLayer(
    state: AppState,
    t: (String) -> String,
    /**
     * ⚠️ 用户 2026-09-20：「工具窗口打开后**不能切换别的界面**」。当时以为是"窗口盖住侧栏"，
     * 于是把窗口的 x 夹在侧栏右边；**2026-09-21 定位到真因**：不是遮住，而是那层指针的
     * 命中区留在了外壳左上角（见下面 `Box` 上 `.pointerInput` 那段注释）。这条夹取留着，
     * 是"别让浮动窗口压住导航"的产品口径，跟点得动点不动无关。
     * 侧边栏现在占多宽（收纳态是 [CollapsedSidebarWidth]，窄窗口没有常驻侧栏时是 0）。
     */
    sidebarWidth: Dp,
) {
    if (state.openToolWindows.isEmpty()) return

    val density = LocalDensity.current
    val windowSize = LocalWindowSize.current
    val platform = LocalPlatform.current

    // 容器（= 整个外壳）与窗口的像素尺寸：默认落点、拖动时夹取都要用
    val rootW = with(density) { windowSize.width.toPx() }
    val rootH = with(density) { windowSize.height.toPx() }
    // 窗口菜单栏画在窗口层**之上**（"系统标题栏"得一直够得着）→ 上边界从它下面开始
    val minY = with(density) { MENU_BAR_HEIGHT.toPx() }
    // 左边界 = 侧栏右边（同理：侧栏得一直够得着，见 `sidebarWidth` 的说明）
    val minX = with(density) { sidebarWidth.toPx() }
    // 顶边最多能下探到哪儿：**只把标题条（抓手 + X）留在容器里**（用户 2026-09-21：
    // 「画布的下半部分放不下去」—— 原来要求整扇窗都在容器里，顶边下不去，见 clampToolWindowOffset）。
    val keepVisiblePx = with(density) { (ToolWindowBarHeight + 8.dp).toPx() }
    // 默认尺寸也夹一下：窄窗口下别把 520×640 原样铺出去（上下左右各留一条边）
    val widthDp = minOf(
        ToolWindowWidth,
        (windowSize.width - ToolWindowMargin * 2).coerceAtLeast(240.dp),
    )
    val heightDp = minOf(
        ToolWindowHeight,
        (windowSize.height - ToolWindowMargin * 2).coerceAtLeast(200.dp),
    )
    val windowW = with(density) { widthDp.toPx() }
    val windowH = with(density) { heightDp.toPx() }
    val cascadePx = with(density) { ToolWindowCascade.toPx() }

    // z 序：**点哪扇哪扇排到最后 = 画在最上面**（和生成页那几块面板一个规矩）
    val zOrder = remember { mutableStateListOf<String>() }
    // 刚打开 / 重启读回来的那几扇还没进过表：这里只算一份"有效顺序"，组合期间**不写状态**
    val effectiveOrder = zOrder.filter { it in state.openToolWindows } +
        state.openToolWindows.filter { it !in zOrder }
    val bringToFront: (String) -> Unit = { id ->
        // 已经是最上面就什么都不做（否则每次按下都白白重组一遍）
        if (zOrder.lastOrNull() != id) {
            zOrder.remove(id)
            zOrder.add(id)
        }
    }

    // ---- 窗口的位置 / 尺寸：**提到这一层** ----
    //
    // 用户 2026-09-21：「工具窗口不能吸附」。吸附先要回答"别的窗口现在在哪儿"，而每扇窗口的
    // `offsetState` / `sizeState` 原来是 `key(toolId) { remember { ... } }` 里的**私有**状态，
    // 兄弟窗口根本读不到。所以改成这一层的两张表（键 = 工具 id），窗口的渲染与拖动都读写它们：
    //  · **读**：表里没有就用兜底（盘上记过的值 / 默认落点、默认尺寸）—— 和 `GenerateScreen`
    //    那几块面板的 `detachedOffsetOf` / `detachedSizeOf` 同一套，**组合期间不写状态**；
    //  · **写**：只在拖动那一帧写（松手时才另外落盘，键 `tools.window.<id>.x/y/w/h`）。
    val windowOffsets = remember { mutableStateMapOf<String, Offset>() }
    val windowSizes = remember { mutableStateMapOf<String, Size>() }
    // 这一帧要点亮哪几条"被贴的边"（外壳坐标：竖边是 3dp 宽的竖条、横边是 3dp 高的横条）——
    // 和 `GenerateScreen.snapHighlights` 同款，**拖动结束就清空**。
    var snapHighlights by remember { mutableStateOf<List<Rect>>(emptyList()) }
    val snapPx = with(density) { ToolWindowSnap.toPx() }
    val snapThinPx = with(density) { ToolWindowSnapThickness.toPx() }
    // 拖动途中的"**指针意图**位置"（哪扇窗口 + 它跟到哪儿）—— 只在拖动期间有值，松手清掉。
    // ⚠️ 吸附必须按它算，不能按"上一帧吸附之后的位置"算（见下面 `onDrag` 里那段说明）。
    var dragIntent by remember { mutableStateOf<Pair<String, Offset>?>(null) }

    /** 盘上记过的尺寸；没记过 = 默认尺寸（和以前 `sizeState` 的初值同一个口径）。 */
    fun savedWindowSize(id: String): Size {
        val savedW = platform.kv.getInt(toolWindowKey(id, "w"), 0)
        val savedH = platform.kv.getInt(toolWindowKey(id, "h"), 0)
        return if (savedW > 0 && savedH > 0) {
            Size(savedW.toFloat(), savedH.toFloat())
        } else {
            Size(windowW, windowH)
        }
    }

    /**
     * 这扇窗口**现在**的尺寸：层表优先，没有就回落到盘上 / 默认。
     *
     * ⚠️ 位置夹取必须拿它算，不能拿默认的 520×640：用户把窗口缩到 320×444 之后，
     * 按 640 算出来的下边界会把顶边死死压在「容器高 - 640」上（见 [clampToolWindowOffset]）。
     */
    fun windowSizeNow(id: String): Size = windowSizes[id] ?: savedWindowSize(id)

    /** 这扇窗口在开启列表里的序号（默认落点的错开量按它算，和以前的 `forEachIndexed` 等价）。 */
    fun windowIndexOf(id: String): Int = state.openToolWindows.indexOf(id).let { if (it < 0) 0 else it }

    /** 盘上记过的落点；没记过 = 默认落点（**居中偏右 + 每多一扇错开 28dp**）。 */
    fun savedWindowOffset(id: String): Offset {
        val savedX = platform.kv.getInt(toolWindowKey(id, "x"), NO_SAVED_WINDOW_POS)
        val savedY = platform.kv.getInt(toolWindowKey(id, "y"), NO_SAVED_WINDOW_POS)
        if (savedX == NO_SAVED_WINDOW_POS || savedY == NO_SAVED_WINDOW_POS) {
            val i = windowIndexOf(id)
            return Offset(
                x = (rootW - windowW) * 0.62f + i * cascadePx,
                y = (rootH - windowH) * 0.5f + i * cascadePx,
            )
        }
        return Offset(savedX.toFloat(), savedY.toFloat())
    }

    /**
     * 这扇窗口**现在**的位置：层表优先；没有就回落到"盘上的值 / 默认落点"，并且**夹一次**
     *（窗口比上次小的时候，旧的落点不能原样铺出去；只在使用处夹、不回写 —— 窗口拉回来
     * 还是用户当初放的那个位置）。和以前 `offsetState` 的初值算法等价。
     */
    fun windowOffsetNow(id: String): Offset {
        windowOffsets[id]?.let { return it }
        val size = windowSizeNow(id)
        return clampToolWindowOffset(
            savedWindowOffset(id),
            size.width,
            size.height,
            rootW,
            rootH,
            minY,
            minX,
            keepVisiblePx,
        )
    }

    /** 某扇窗口现在的矩形（吸附候选与命中高亮都用它）。 */
    fun windowRectNow(id: String): Rect {
        val at = windowOffsetNow(id)
        val size = windowSizeNow(id)
        return Rect(at.x, at.y, at.x + size.width, at.y + size.height)
    }

    /**
     * **吸附：工具窗口贴别的窗口 / 贴生成页那几块面板**（用户 2026-09-21：「工具窗口不能吸附」
     * →「**还要和其他窗口吸附啊**」）。
     *
     * 口径照 `GenerateScreen.snapAgainst`（"各个窗口加上吸附功能，拖到某个窗口的底部和侧边时
     * 会吸附"），几何算法整段抽到 [snapToRects]：候选一共八条边、半径 ±[ToolWindowSnap]、
     * 另一轴必须真的挨着。这里只负责**凑候选**：
     *  · 别的**工具窗口**（`state.openToolWindows` 里除自己之外的每一扇）；
     *  · **生成页那几块面板**（`state.panelRects` —— 提示词栏 / 底栏 / 分离出来的框）。
     *
     * ⚠️ 两类候选的坐标系**同源**：工具窗口的位置住在外壳最外层 `Box` 里（原点 = 窗口客户区
     * 左上角），而面板那边是用 `Modifier.onGloballyPositioned { it.boundsInWindow() }` 报上来的
     *（`boundsInWindow()` 给的就是窗口客户区坐标）—— 所以这里**不做任何偏移换算**。
     * 面板藏起来 / 被收回时由上报方把 key 删掉（见 `AppState.reportPanelRect`），不会"吸到空气"。
     * ⚠️ 容器四条边依旧不算候选（这是用户的明确口径）。
     *
     * @param selfId 正在拖的是哪扇窗口 —— 用来把自己从候选里排除掉，否则会自己吸自己。
     * @param extraRects 额外的吸附候选；默认取生成页那几块面板的矩形。
     * @return 贴合后的位置 + 要画的"被贴那条边"的高亮矩形（外壳坐标，`+x` 向右 / `+y` 向下）。
     */
    fun snapWindows(
        target: Offset,
        size: Size,
        selfId: String,
        extraRects: List<Rect> = state.panelRects.values.toList(),
    ): Pair<Offset, List<Rect>> {
        val candidates = mutableListOf<Rect>()
        state.openToolWindows.forEach { otherId ->
            if (otherId != selfId) candidates += windowRectNow(otherId)
        }
        candidates += extraRects
        return snapToRects(target, size, candidates, snapPx, snapThinPx)
    }

    state.openToolWindows.forEach { toolId ->
        key(toolId) {
            // 位置 / 尺寸都住在上面的**层表**里（`windowOffsets` / `windowSizes`）——
            // 以前是每扇窗口自己 `key(toolId) { remember { ... } }` 里的私有状态，兄弟窗口
            // 读不到，吸附也就无从谈起（用户 2026-09-21：「工具窗口不能吸附」）。
            //  · 尺寸在**组合期**读（`.size(...)` 要 Dp；初值 = 盘上的 / 默认的，同旧口径）；
            //  · 位置在 `.offset { }` 里读（layout 阶段），拖动每帧只失效布局、不重组。
            val minW = with(density) { 320.dp.toPx() }
            val minH = with(density) { 240.dp.toPx() }

            Box(
                Modifier
                    // 这扇窗口的**布局**（层级 / 落点 / 尺寸）—— 指针层排在它们之后，理由见下面那段
                    .zIndex(effectiveOrder.indexOf(toolId).let { if (it < 0) 0f else it + 1f })
                    .offset {
                        // ⚠️ 位置**只在 layout 阶段读**（就在 `.offset { }` 的 lambda 里）——
                        // 和以前读 `offsetState` 同一个理由：拖动每帧只失效布局、不重组。
                        val at = windowOffsetNow(toolId)
                        IntOffset(at.x.roundToInt(), at.y.roundToInt())
                    }
                    .size(
                        // 尺寸要 Dp，只能在组合期读（读的是层表，别的窗口量矩形时看到的是同一个值）
                        with(density) { windowSizeNow(toolId).width.toDp() },
                        with(density) { windowSizeNow(toolId).height.toDp() },
                    )
                    // ⚠️ 指针层**必须排在 `.offset{}` / `.size()` 后面**（2026-09-21 修）。
                    //
                    // 命中矩形是按**节点自己所在的坐标系**算的，而 `NodeCoordinator.head()` 只扫
                    // **本段**（相邻两个布局修饰符之间）的节点 —— 所以在 `offset` **前面**时，
                    // 这个指针层的命中区留在**外壳左上角的 (0,0)-(w,h)**：那儿什么都没画，
                    // 却正好压着侧栏和窗口菜单栏。更要命的是它一旦命中，父 Box 的同级命中测试
                    // 就**停住**（`InnerNodeCoordinator.hitTestChild`：`hitTestResult.hasHit()`
                    // → break，同级不再往下试；`Surface` 靠 `pointerInput(Unit){}` 挡穿透也是这个机制）。
                    // 于是用户 2026-09-20 报的「开着工具窗口时，侧栏和标题栏菜单全都点不动、
                    // 只有最下面那一项（我的）能点」就是这么来的：那块**死区的高度 = 工具窗口的高**，
                    // 窗口拉到 444 高时，只有 y > 444 的那一项露在外面。
                    // 摆到 `offset` / `size` 之后，命中区才跟着窗口的落点走。
                    .pointerInput(toolId) {
                        awaitPointerEventScope {
                            while (true) {
                                // ⚠️ 用 **Main** 阶段而不是 Initial（2026-09-21 改）：
                                // `bringToFront` 会写 `zOrder`（进而改 `zIndex` = 命中顺序），
                                // 排在子节点处理之前写状态，理论上会打断同一次按下的后续分发
                                // （用户报的"窗口里按钮点了没反应 / 窗口外也点不动"就是这一类症状）。
                                // Main 阶段在**子节点之后**跑，抬升只影响下一帧，绝不插到分发中间；
                                // 消费（`isConsumed`）不会把祖先摘出命中路径，所以"按窗口任何地方都抬升"照旧成立。
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                if (event.changes.any { it.pressed }) bringToFront(toolId)
                            }
                        }
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(LocalPanelBackgroundColor.current ?: MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxSize()) {
                    ToolWindowTitleBar(
                        label = t(ToolCatalog.byId(toolId).titleKey),
                        closeLabel = t("common.close"),
                        onDrag = { delta ->
                            // **当前**尺寸（用户可能已经拉过大小），不是默认的 520×640
                            val size = windowSizeNow(toolId)
                            // ⚠️ 吸附要以"**指针意图**的位置"为准，而不是上一帧吸附之后的位置：
                            // 后者每帧只差一个很小的 delta，永远落在半径里 —— 窗口一旦贴上就
                            // 再也拖不开（每帧都被拽回去）。生成页那边靠 `grab.startOffset`
                            //（手势起点）+ 指针位移避开的就是这个坑，这里用"另存一份意图位置"等价实现。
                            val intent = dragIntent?.takeIf { it.first == toolId }?.second
                                ?: windowOffsetNow(toolId)
                            val next = intent + delta
                            dragIntent = toolId to next
                            // 先问吸附：命中就"吸上去"，并记下要点亮的那条边
                            val (snapped, highlights) = snapWindows(next, size, toolId)
                            snapHighlights = highlights
                            // 吸附命中时**直接用贴合值**（不要再被夹取拽回去）；
                            // 没命中才走 clampToolWindowOffset —— 老口径照旧：窗口主体可以压到
                            // 容器下沿之外，只要标题条（抓手 + X）还留在里面（用户 2026-09-21）。
                            windowOffsets[toolId] = if (highlights.isEmpty()) {
                                clampToolWindowOffset(
                                    snapped,
                                    size.width,
                                    size.height,
                                    rootW,
                                    rootH,
                                    minY,
                                    minX,
                                    keepVisiblePx,
                                )
                            } else {
                                snapped
                            }
                        },
                        onDragEnd = {
                            // 这一手势结束：意图位置作废（下一次拖动手势重新从窗口当前位置起算），
                            // 那几条高亮也清掉（用户口径：只在拖的时候亮）
                            dragIntent = null
                            snapHighlights = emptyList()
                            // 松手才落盘（每帧写会把 prefs.json 磨穿）；写的是**吸附 / 夹取之后**的位置
                            val at = windowOffsetNow(toolId)
                            platform.kv.edit()
                                .putInt(toolWindowKey(toolId, "x"), at.x.roundToInt())
                                .putInt(toolWindowKey(toolId, "y"), at.y.roundToInt())
                                .apply()
                        },
                        onClose = { state.closeToolWindow(toolId) },
                    )
                    // 内容就是那个工具本身的界面：和整页**共用同一份** `ToolContent`，不抄第二份
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        ToolContent(toolId = toolId, state = state)
                    }
                }
                // 右下角：拉大拉小（用户 2026-09-20：「工具窗口不能调大小」）——
                // 指针只挂在这一小块上，不抢内容区的点击；松手才落盘尺寸。
                val markColor = MaterialTheme.colorScheme.onSurfaceVariant
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .pointerInput(toolId) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    // ⚠️ 上界不能小于**当前**尺寸：窗口被放到靠下 / 靠右之后，
                                    // `容器高 - y` 会小于现在的高度，`coerceIn(min, max)` 会把它
                                    // **缩小**（拉一下反而变小）。所以上界至少放行当前值。
                                    val size = windowSizeNow(toolId)
                                    val at = windowOffsetNow(toolId)
                                    val maxW = (rootW - at.x)
                                        .coerceAtLeast(maxOf(minW, size.width))
                                    val maxH = (rootH - at.y)
                                        .coerceAtLeast(maxOf(minH, size.height))
                                    windowSizes[toolId] = Size(
                                        (size.width + dragAmount.x).coerceIn(minW, maxW),
                                        (size.height + dragAmount.y).coerceIn(minH, maxH),
                                    )
                                },
                                onDragEnd = {
                                    val size = windowSizeNow(toolId)
                                    platform.kv.edit()
                                        .putInt(
                                            toolWindowKey(toolId, "w"),
                                            size.width.roundToInt(),
                                        )
                                        .putInt(
                                            toolWindowKey(toolId, "h"),
                                            size.height.roundToInt(),
                                        )
                                        .apply()
                                },
                                onDragCancel = { },
                            )
                        },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        for (i in 1..3) {
                            val d = size.minDimension * i / 3.4f
                            drawLine(
                                color = markColor.copy(alpha = 0.55f),
                                start = Offset(size.width - d, size.height - 2f),
                                end = Offset(size.width - 2f, size.height - d),
                                strokeWidth = 1.6f,
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- 吸附高亮：拖到贴合半径内时点亮**被贴的那条边** ----
    //
    // 画在窗口**上面**（3dp 的细线正好压在两扇窗口的接缝上；画在下面就有一半被窗口盖住）——
    // 它只做提示，**没有 pointerInput**，所以不吃指针、也不会挡住窗口的拖动与那颗 X
    //（命中测试只认带指针输入的节点，理由见上面 `pointerInput` 那段注释）。
    // ⚠️ `zIndex` 排在 `.offset { }` **前面**，和窗口那层同一个顺序：绘制 / 指针层必须落在
    // 布局修饰符**之后**，否则高亮会被钉在布局原点（外壳左上角）。
    val snapColor = MaterialTheme.colorScheme.primary
    snapHighlights.forEach { r ->
        Box(
            Modifier
                .zIndex(TOOL_WINDOW_SNAP_Z)
                .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                .size(
                    with(density) { r.width.toDp() },
                    with(density) { r.height.toDp() },
                )
                .background(snapColor.copy(alpha = 0.55f)),
        )
    }
}

/**
 * 工具窗口顶上那条：点阵抓手 + 工具名 + 右上角那颗 **X**。
 *
 * ⚠️ 拖动手势只罩**左半边**（抓手 + 标题那一段）：右边那颗 X 是 `IconButton`，
 * 连它一起罩住的话，点关闭时手一抖就会被判成拖动 —— 按钮反而点不着了。
 */
@Composable
private fun ToolWindowTitleBar(
    label: String,
    closeLabel: String,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
) {
    // `pointerInput(Unit)` 只在第一次组合时抓一次回调，所以用 `rememberUpdatedState` 转发：
    // 万一以后这扇窗口换了调用方（比如按 id 复用同一个组合位置），拖的还是当前那扇
    val drag by rememberUpdatedState(onDrag)
    val dragEnd by rememberUpdatedState(onDragEnd)
    // ⚠️ 用户 2026-09-26 第二轮：「去除深色标题栏」+「设置里加标题栏颜色」——
    // 同生成页那两块面板：没设置就跟随面板底色（= 没有独立标题栏 ✓），设置了就按亮度取黑/白字 ✓。
    val toolTitleBarColor = LocalPanelTitleBarColor.current
    val toolBarColor = toolTitleBarColor ?: MaterialTheme.colorScheme.surface
    val toolBarContent = toolTitleBarColor?.let {
        if (it.luminance() < 0.5f) Color.White else Color.Black
    } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .height(ToolWindowBarHeight)
            .background(toolBarColor),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                // 拖动：累计位移交给调用方写进 `.offset { }` 的状态（每帧只失效布局、不重组）
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { dragEnd() },
                        onDragCancel = { dragEnd() },
                    ) { change, dragAmount ->
                        change.consume()
                        drag(dragAmount)
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 抓手：两列三点（和生成页那几块面板一个样子，不依赖图标字体，什么语言下都一样）
            val gripColor = toolBarContent
            Canvas(Modifier.padding(start = 12.dp).size(width = 8.dp, height = 12.dp)) {
                val r = size.width / 5f
                listOf(0.18f, 0.5f, 0.82f).forEach { fy ->
                    drawCircle(gripColor, radius = r, center = Offset(size.width * 0.3f, size.height * fy))
                    drawCircle(gripColor, radius = r, center = Offset(size.width * 0.7f, size.height * fy))
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = toolBarContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp),
            )
        }
        // 右上角那颗 X（用户点名要的）
        IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = closeLabel,
                modifier = Modifier.size(18.dp),
                tint = toolBarContent,
            )
        }
        Spacer(Modifier.width(6.dp))
    }
}

