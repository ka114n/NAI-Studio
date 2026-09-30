package com.kallan.naistudio.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * **面板 / 窗口的统一外观**（用户 2026-09-26，照他给的参考图）：
 * **深色标题栏 + 深色描边** —— 中控台、底栏、画布右边缘那条竖向工具栏，以及所有"独立窗口"
 *（分离出来的面板、工具浮动窗口、临时图库面板）都套这一套 ✓，看起来才像一族窗口。
 *
 * ⚠️ 这里用**固定色**、**不跟主题走** ✗ —— 参考图里浅色主题下标题栏也是深色；
 * 而且这几块本来就跟画布/图片贴在一起，深色描边在浅色与深色主题下都够清楚 ✓。
 */
object NaiPanelChrome {
    /**
     * 标题栏的**默认**底色 —— 用户 2026-09-26 第二轮：「**去除**描边和深色标题栏」✗。
     * 现在默认是 `null`（见 [LocalPanelTitleBarColor]）：标题栏**跟随面板自己的底色**
     *（也就是"没有深色标题栏" ✓）；想上色就在设置里选 ✓。
     */
    val TitleBar: Color? = null

    /** 标题栏文字的默认色（没上色时用主题前景色；上色后按底色亮度自动取黑/白 ✓）。 */
    val OnTitleBar: Color = Color(0xFFE8EDF2)

    /** 面板描边默认色（现在默认**不画**描边，这个值只在把宽度调回去时才用得上）。 */
    val Border: Color = Color(0xFF3A4149)

    /**
     * 描边宽度 —— 用户 2026-09-26：「**去除描边**」✗ → **0dp**（不画 ✓）。
     * （同时它也是"内收"的量，0 就等于不内缩 ✓。想恢复粗边框就把这个数改回 2.dp ✓。）
     */
    val BorderWidth = 0.dp
}

/**
 * **标题栏颜色（设置可调）** —— 用户 2026-09-26：「在设置界面加一栏**标题栏的颜色设置**」。
 *
 * `null`（默认）= 标题栏跟随面板底色（= 视觉上没有独立标题栏 ✓）；
 * 非 null = 用设置里选的颜色，文字按该颜色的亮度自动取黑/白 ✓。
 */
val LocalPanelTitleBarColor = staticCompositionLocalOf<Color?> { NaiPanelChrome.TitleBar }

/**
 * **窗口面板底色（设置可调）** —— 用户 2026-09-26：「右边栏的背景 / 各个窗口的背景色」。
 *
 * `null`（默认）= 跟随主题 `surface` ✓；非 null = 调色板里「窗口」那一行选的颜色 ✓。
 * 覆盖：中控台 / 底栏 / 分离面板 / 工具浮窗 / 画布右边栏 ✓（"窗口"这一族一起变 ✓）。
 */
val LocalPanelBackgroundColor = staticCompositionLocalOf<Color?> { null }

/**
 * **面板描边颜色（可被设置覆盖）** —— 用户 2026-09-26：「设置可调边框颜色」。
 *
 * 默认就是 [NaiPanelChrome.Border]；入口（`Main.kt`）会拿
 * `settings.windowBorderColor` 解析出来的颜色 provide 下去 ✓。
 * 之所以走 CompositionLocal 而不是给每个面板加参数：套这套描边的地方有五处
 *（中控台 / 底栏 / 右边栏 / 临时图库 / 工具浮动窗口），穿参数会把签名搅乱 ✗。
 */
val LocalPanelBorderColor = staticCompositionLocalOf { NaiPanelChrome.Border }

/**
 * 把设置里存的 `#RRGGBB`（`NaiColorPicker` 给的格式）解析成 [Color]。
 * 空串 / 位数不对 / 解析失败 → `null`（调用方回落到默认深色 ✓）。
 */
fun parseHexColor(value: String): Color? {
    val v = value.trim().removePrefix("#")
    if (v.length != 6) return null
    return runCatching { Color(0xFF000000L or v.toLong(16)) }.getOrNull()
}

/**
 * NAI Studio 的视觉层（**画廊风**：图片是主角，控件低对比、少线条、大留白）。
 *
 * 三条设计原则（改配色时请守住，否则"画廊感"会散掉）：
 *  1. **背景与容器反差要小**：靠层次（background → surface → surfaceContainer）区分，
 *     不靠边框。所以把 `outlineVariant` 压到几乎看不见 —— 那才是"少线条"。
 *  2. **文字只用三档**：主文字（onSurface）、次文字（onSurfaceVariant）、强调（primary）。
 *     不给控件上花哨颜色，彩色只留给"图和状态"。
 *  3. **强调色只用一处**：主按钮 / 选中态 / 进度。铺开就俗了。
 *
 * 之所以能"改一处、全局生效"：全项目的 UI 代码**零硬编码颜色**（实测 `Color(0x` 0 处），
 * 一律走 `MaterialTheme.colorScheme.*`；圆角和排版也走 `MaterialTheme.shapes/typography`。
 */

// ---------------------------------------------------------------- 调色板：深青 / 晴蓝

/** 暗色底：中性偏冷（不是纯黑，纯黑在大屏上"发死"，图片放上去反而显脏）。 */
// ⚠️ 用户 2026-09-26 第二轮重调（原话：「现有的有点瞎眼，**暗色对比度太高，浅色对比度太低**」）：
//  · **暗色系**：底不再近黑（#0E1114 → #15181C 那种中等深灰）✗，正文也不再用接近纯白，
//    对比从"刺眼"降到"耐看"；层次仍然靠 surface 那几档底色差 ✓。
//  · **浅色系**：底不再纯白（纯白会让"白底白卡"整片糊在一起 ✗），正文保持近黑、
//    次要文字再压深一档 → 感知对比明显上来 ✓。
private val Ink = Color(0xFF15181C)
private val InkSurface = Color(0xFF1B1F24)
private val InkContainer = Color(0xFF232830)
private val InkContainerHigh = Color(0xFF2C333C)

/** 亮色底：米白偏冷，比纯白柔和，长时间看图不刺眼。 */
private val Paper = Color(0xFFF1F4F7)
private val PaperSurface = Color(0xFFFAFBFD)
private val PaperContainer = Color(0xFFE6ECF1)
private val PaperContainerHigh = Color(0xFFDBE3EA)

// ============================================================ 调色板二：蓝白（Breathable Blue）
//
// 定位：干净、轻快的「产品蓝」——白底大留白 + 深海蓝强调，蓝只落在主按钮 / 选中态 / 进度，
// 延续画廊风三原则（背景与容器反差小 / 文字三档 / 强调色只用一处）。

/** 暗色底：比经典款更蓝一点的深夜（蓝白主题的暗色也要带蓝，不能变成纯灰黑）。 */
private val NightBlue = Color(0xFF141A28)
private val NightBlueSurface = Color(0xFF1A2334)
private val NightBlueContainer = Color(0xFF212D45)
private val NightBlueContainerHigh = Color(0xFF2A3A57)

/** 暗色主色：亮钴蓝（在深夜蓝底上足够跳）。 */
private val CobaltBright = Color(0xFF7CB8F5)
/** 亮色主色：深海蓝（白底上对比充足，按钮文字用白）。 */
private val DeepSea = Color(0xFF1E62D0)

/** 暗色次色：浅蓝紫；亮色次色：青蓝（次级强调、开关、图表）。 */
private val Periwinkle = Color(0xFF9DB6E8)
private val AzureDim = Color(0xFF4C8BD6)

/** 主色：晴蓝（选中 / 主按钮 / 进度）。 */
private val SkyDark = Color(0xFF5BC8E8)
private val SkyLight = Color(0xFF0B7A99)
/** 次色：深青（次级强调、开关、图表）。 */
private val TealDark = Color(0xFF59D6C4)
private val TealLight = Color(0xFF0F8A79)

internal val NaiDarkColors = darkColorScheme(
    primary = SkyDark,
    onPrimary = Color(0xFF04222C),
    // ⚠️ **这一档被用户要求"浅一些"**（2026-09-16）：原来是 #0E3B4A（很深的青），
    // 用它做的按钮（角色图鉴、导演台选中那格）在满屏毛玻璃里是一块突兀的深色块。
    // 现在走 M3 该有的关系 —— **浅容器 + 深字**，对比度约 10:1，也顺手把
    // "强调色只留给生成按钮"那条规矩找回来了（深色块本身就在抢注意力）。
    primaryContainer = Color(0xFF9FDCEB),
    onPrimaryContainer = Color(0xFF04303C),
    secondary = TealDark,
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF103B36),
    onSecondaryContainer = Color(0xFFB4EDE3),
    tertiary = Color(0xFF9DB6E8),
    onTertiary = Color(0xFF0D1B33),
    background = Ink,
    onBackground = Color(0xFFD6DCE4),
    surface = InkSurface,
    onSurface = Color(0xFFD6DCE4),
    surfaceVariant = InkContainerHigh,
    onSurfaceVariant = Color(0xFFA8B4BF),
    surfaceContainerLowest = Color(0xFF0B0E11),
    surfaceContainerLow = InkSurface,
    surfaceContainer = InkContainer,
    surfaceContainerHigh = InkContainerHigh,
    surfaceContainerHighest = Color(0xFF27313A),
    // 画廊风：描边几乎看不见，层次靠底色差
    outline = Color(0xFF3A464F),
    outlineVariant = Color(0xFF232C34),
    error = Color(0xFFFF8A94),
    onError = Color(0xFF3A0410),
    errorContainer = Color(0xFF4A1119),
    onErrorContainer = Color(0xFFFFD9DD),
    inverseSurface = Color(0xFFD6DCE4),
    inverseOnSurface = Color(0xFF1A2026),
    scrim = Color(0xFF000000),
)

internal val NaiLightColors = lightColorScheme(
    primary = SkyLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCBEBF7),
    onPrimaryContainer = Color(0xFF00323F),
    secondary = TealLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC4EDE6),
    onSecondaryContainer = Color(0xFF00251F),
    tertiary = Color(0xFF3D5A8A),
    onTertiary = Color(0xFFFFFFFF),
    background = Paper,
    onBackground = Color(0xFF101619),
    surface = PaperSurface,
    onSurface = Color(0xFF101619),
    surfaceVariant = PaperContainerHigh,
    onSurfaceVariant = Color(0xFF39454F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FCFD),
    surfaceContainer = PaperContainer,
    surfaceContainerHigh = PaperContainerHigh,
    surfaceContainerHighest = Color(0xFFD9E2E8),
    outline = Color(0xFFB9C6CF),
    outlineVariant = Color(0xFFE2E9EE),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF2A3238),
    inverseOnSurface = Color(0xFFF1F5F8),
    scrim = Color(0xFF000000),
)

/** 蓝白 · 暗色：深夜蓝底 + 亮钴蓝强调。 */
internal val NaiBlueDarkColors = darkColorScheme(
    primary = CobaltBright,
    onPrimary = Color(0xFF0A1D33),
    // 和经典暗色同一个口径：浅容器 + 深字（原来是 #1A3A5C 的深蓝按钮）。
    primaryContainer = Color(0xFFAFCBF2),
    onPrimaryContainer = Color(0xFF08284E),
    secondary = Periwinkle,
    onSecondary = Color(0xFF0D1B33),
    secondaryContainer = Color(0xFF24365C),
    onSecondaryContainer = Color(0xFFD6E1F8),
    tertiary = Color(0xFF8FD0E8),
    onTertiary = Color(0xFF0A2129),
    background = NightBlue,
    onBackground = Color(0xFFD5DEEC),
    surface = NightBlueSurface,
    onSurface = Color(0xFFD5DEEC),
    surfaceVariant = NightBlueContainerHigh,
    onSurfaceVariant = Color(0xFFA9B8CE),
    surfaceContainerLowest = Color(0xFF090E1A),
    surfaceContainerLow = NightBlueSurface,
    surfaceContainer = NightBlueContainer,
    surfaceContainerHigh = NightBlueContainerHigh,
    surfaceContainerHighest = Color(0xFF253757),
    outline = Color(0xFF44546E),
    outlineVariant = Color(0xFF1F2A40),
    error = Color(0xFFFF8A94),
    onError = Color(0xFF3A0410),
    errorContainer = Color(0xFF4A1119),
    onErrorContainer = Color(0xFFFFD9DD),
    inverseSurface = Color(0xFFD5DEEC),
    inverseOnSurface = Color(0xFF16223A),
    scrim = Color(0xFF000000),
)

/** 蓝白 · 亮色：纯白纸面 + 深海蓝强调（比经典款更「素」，留白更多）。 */
internal val NaiBlueLightColors = lightColorScheme(
    primary = DeepSea,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E7FB),
    onPrimaryContainer = Color(0xFF0A2A52),
    secondary = AzureDim,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEAF9),
    onSecondaryContainer = Color(0xFF0E2E52),
    tertiary = Color(0xFF2E7BA6),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF2F6FB),
    onBackground = Color(0xFF101820),
    surface = Color(0xFFFAFCFE),
    onSurface = Color(0xFF101820),
    surfaceVariant = Color(0xFFE2E9F3),
    onSurfaceVariant = Color(0xFF36445A),
    surfaceContainerLowest = Color(0xFFFDFEFF),
    surfaceContainerLow = Color(0xFFF6F9FD),
    surfaceContainer = Color(0xFFEDF2F9),
    surfaceContainerHigh = Color(0xFFE2E9F3),
    surfaceContainerHighest = Color(0xFFD6E0EE),
    outline = Color(0xFFB4C2D4),
    outlineVariant = Color(0xFFE1E9F2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF2A3440),
    inverseOnSurface = Color(0xFFF1F6FB),
    scrim = Color(0xFF000000),
)

// ---------------------------------------------------------------- 「经典配色」（用户 2026-09-26）
//
// 用户给了 NovelAI 官方界面的暗色截图：「**做一套配色，暗色模式下的配色，叫经典配色**」。
// 取色思路就照它来：**近黑的蓝调底**（不是纯黑，纯黑配蓝按钮会发脏）+ **中饱和的蓝**当强调色，
// 文字冷白、次要文字蓝灰；层次的区分全靠 `surface*` 那几档底色差（和经典画廊一个路子 ✓）。
// 浅色那一套是为了"切到浅色时不崩"配的：同样的蓝、白底冷灰字 ✓。
internal val NaiNovelAiDarkColors = darkColorScheme(
    primary = Color(0xFF3D6BE5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF22315E),
    onPrimaryContainer = Color(0xFFD5DEFF),
    secondary = Color(0xFF7C8DB5),
    onSecondary = Color(0xFF11141C),
    secondaryContainer = Color(0xFF2A3247),
    onSecondaryContainer = Color(0xFFD6DDF0),
    tertiary = Color(0xFF8AB4F8),
    onTertiary = Color(0xFF0B1524),
    background = Color(0xFF1B1B2F),
    onBackground = Color(0xFFD9DCE5),
    surface = Color(0xFF15152A),
    onSurface = Color(0xFFD9DCE5),
    surfaceVariant = Color(0xFF23233A),
    onSurfaceVariant = Color(0xFFAFB4C4),
    surfaceContainerLowest = Color(0xFF0A0A14),
    surfaceContainerLow = Color(0xFF15152A),
    surfaceContainer = Color(0xFF1B1B2F),
    surfaceContainerHigh = Color(0xFF23233A),
    surfaceContainerHighest = Color(0xFF2A2A44),
    outline = Color(0xFF3A3D4A),
    outlineVariant = Color(0xFF262833),
    error = Color(0xFFFF8A94),
    onError = Color(0xFF3A0410),
    errorContainer = Color(0xFF4A1119),
    onErrorContainer = Color(0xFFFFD9DD),
    inverseSurface = Color(0xFFD9DCE5),
    inverseOnSurface = Color(0xFF1A1C24),
    scrim = Color(0xFF000000),
)

internal val NaiNovelAiLightColors = lightColorScheme(
    primary = Color(0xFF2F5BD0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E1FF),
    onPrimaryContainer = Color(0xFF11214F),
    secondary = Color(0xFF5A6B93),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE3F5),
    onSecondaryContainer = Color(0xFF1B2236),
    tertiary = Color(0xFF2C6BB5),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F8FB),
    onBackground = Color(0xFF16181F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16181F),
    surfaceVariant = Color(0xFFE8EAF2),
    onSurfaceVariant = Color(0xFF5A6072),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F6FA),
    surfaceContainer = Color(0xFFEFF1F7),
    surfaceContainerHigh = Color(0xFFE8EAF2),
    surfaceContainerHighest = Color(0xFFE0E3ED),
    outline = Color(0xFF8A90A3),
    outlineVariant = Color(0xFFD5D9E6),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF2C2F3A),
    inverseOnSurface = Color(0xFFF0F1F6),
    scrim = Color(0xFF000000),
)

// ---------------------------------------------------------------- 三个"看得出区别"的色系（用户 2026-09-26）
//
// 用户反馈：「三个选项全是一种配色，**多做几个色系的**」—— 上一轮三套的底色都落在
// "深灰蓝"那一带（青 / 蓝 / 深蓝紫 ✗），只有强调色不同，看着就是一样 ✓。
// 这三套**换掉色相本身**：紫罗兰 / 林绿 / 暖棕 ✓ —— 底色、面、强调色一起走各自的色系 ✓。
// 只写关键槽位：其余交给 M3 默认（error / inverse 很少露面 ✓），每套十来行、以后加色系也快 ✓。
internal val NaiVioletDarkColors = darkColorScheme(
    primary = Color(0xFFB48CFF),
    onPrimary = Color(0xFF230F4A),
    primaryContainer = Color(0xFF3A2A63),
    onPrimaryContainer = Color(0xFFE3D6FF),
    secondary = Color(0xFF9D8FC4),
    onSecondary = Color(0xFF171028),
    background = Color(0xFF17131F),
    onBackground = Color(0xFFDCD6E6),
    surface = Color(0xFF1F1A29),
    onSurface = Color(0xFFDCD6E6),
    surfaceVariant = Color(0xFF2A2436),
    onSurfaceVariant = Color(0xFFA79FB8),
    surfaceContainerLowest = Color(0xFF110E17),
    surfaceContainerLow = Color(0xFF1A1623),
    surfaceContainer = Color(0xFF221C2D),
    surfaceContainerHigh = Color(0xFF2A2436),
    surfaceContainerHighest = Color(0xFF332B41),
    outline = Color(0xFF4A4258),
    outlineVariant = Color(0xFF312A3C),
)

internal val NaiVioletLightColors = lightColorScheme(
    primary = Color(0xFF6A3FD1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7DBFF),
    onPrimaryContainer = Color(0xFF24104F),
    secondary = Color(0xFF615884),
    background = Color(0xFFEFE9F8),
    onBackground = Color(0xFF1A1622),
    surface = Color(0xFFF7F2FE),
    onSurface = Color(0xFF1A1622),
    surfaceVariant = Color(0xFFDFD4EF),
    onSurfaceVariant = Color(0xFF4A4458),
    surfaceContainerLowest = Color(0xFFFBF8FF),
    surfaceContainerLow = Color(0xFFF3ECFC),
    surfaceContainer = Color(0xFFE9E0F6),
    surfaceContainerHigh = Color(0xFFDFD4EF),
    surfaceContainerHighest = Color(0xFFD5C8E8),
    outline = Color(0xFF8C82A0),
    outlineVariant = Color(0xFFD8D0E4),
)

internal val NaiForestDarkColors = darkColorScheme(
    primary = Color(0xFF7FD39A),
    onPrimary = Color(0xFF08301A),
    primaryContainer = Color(0xFF1E4430),
    onPrimaryContainer = Color(0xFFC9F2D8),
    secondary = Color(0xFF8FB9A0),
    onSecondary = Color(0xFF0D2317),
    background = Color(0xFF111813),
    onBackground = Color(0xFFD5E0D7),
    surface = Color(0xFF18211A),
    onSurface = Color(0xFFD5E0D7),
    surfaceVariant = Color(0xFF232E25),
    onSurfaceVariant = Color(0xFF9FB0A2),
    surfaceContainerLowest = Color(0xFF0C110D),
    surfaceContainerLow = Color(0xFF141C16),
    surfaceContainer = Color(0xFF1B251D),
    surfaceContainerHigh = Color(0xFF232E25),
    surfaceContainerHighest = Color(0xFF2C392E),
    outline = Color(0xFF435246),
    outlineVariant = Color(0xFF2A362C),
)

internal val NaiForestLightColors = lightColorScheme(
    primary = Color(0xFF1F7A45),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCEEDA),
    onPrimaryContainer = Color(0xFF072914),
    secondary = Color(0xFF4C6355),
    background = Color(0xFFF2F7F3),
    onBackground = Color(0xFF131A15),
    surface = Color(0xFFFAFDFB),
    onSurface = Color(0xFF131A15),
    surfaceVariant = Color(0xFFE3EDE6),
    onSurfaceVariant = Color(0xFF3F4C43),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6FAF7),
    surfaceContainer = Color(0xFFEDF4EF),
    surfaceContainerHigh = Color(0xFFE3EDE6),
    surfaceContainerHighest = Color(0xFFDAE6DD),
    outline = Color(0xFF7F8F84),
    outlineVariant = Color(0xFFCFDCD3),
)

internal val NaiSepiaDarkColors = darkColorScheme(
    primary = Color(0xFFE0A96D),
    onPrimary = Color(0xFF3A2208),
    primaryContainer = Color(0xFF4C3517),
    onPrimaryContainer = Color(0xFFFFE0BE),
    secondary = Color(0xFFC0A48A),
    onSecondary = Color(0xFF2A1C10),
    background = Color(0xFF1A1512),
    onBackground = Color(0xFFE4DAD2),
    surface = Color(0xFF231C18),
    onSurface = Color(0xFFE4DAD2),
    surfaceVariant = Color(0xFF2F2721),
    onSurfaceVariant = Color(0xFFB0A296),
    surfaceContainerLowest = Color(0xFF130F0D),
    surfaceContainerLow = Color(0xFF1E1814),
    surfaceContainer = Color(0xFF271F1A),
    surfaceContainerHigh = Color(0xFF2F2721),
    surfaceContainerHighest = Color(0xFF3A3029),
    outline = Color(0xFF544840),
    outlineVariant = Color(0xFF382F29),
)

internal val NaiSepiaLightColors = lightColorScheme(
    primary = Color(0xFF8A5A22),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF5DFC4),
    onPrimaryContainer = Color(0xFF2C1A05),
    secondary = Color(0xFF6B5A48),
    background = Color(0xFFF8F4EF),
    onBackground = Color(0xFF1C1611),
    surface = Color(0xFFFDFBF8),
    onSurface = Color(0xFF1C1611),
    surfaceVariant = Color(0xFFEDE4DA),
    onSurfaceVariant = Color(0xFF4C4238),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF7F2),
    surfaceContainer = Color(0xFFF4EEE7),
    surfaceContainerHigh = Color(0xFFEDE4DA),
    surfaceContainerHighest = Color(0xFFE5DACE),
    outline = Color(0xFF93867A),
    outlineVariant = Color(0xFFDCD1C5),
)

/**
 * **把用户选的颜色按当前模式自动换算** —— 用户 2026-09-26：
 * 「调色后调整为暗色模式时，**要自动调色**」。
 *
 * 背景：画布 / 窗口 / 标题栏这三档是"**一份值、两套模式共用**"✗ —— 用户在浅色下挑的浅色，
 * 切到暗色还照搬就会刺眼 ✗。这里做一个**保色相**的换算：
 *  · 暗色模式 → 压亮度（保留色相与一部分饱和）✓；
 *  · 浅色模式 → 抬亮度（同理 ✓，避免在浅色下用一块死黑 ✗）。
 * 用 HSV 而不是简单乘 RGB：这样"紫色还是紫色"，不会变成灰 ✓。
 */
fun adaptForMode(color: Color, dark: Boolean): Color {
    val hsv = color.toHsv()
    val h = hsv[0]
    val s = hsv[1]
    val v = hsv[2]
    return if (dark) {
        // 暗色：亮度压到 28%~45%，饱和略降
        Color.hsv(h, (s * 0.85f).coerceIn(0f, 1f), (v * 0.34f).coerceIn(0.10f, 0.55f))
    } else {
        // 浅色：亮度抬到 88% 以上，饱和降一档（浅色底不宜太艳）
        Color.hsv(h, (s * 0.55f).coerceIn(0f, 1f), (0.90f + v * 0.08f).coerceIn(0.80f, 0.99f))
    }
}

// ---------------------------------------------------------------- 形状：一套刻度，四处收口

/**
 * 圆角刻度。项目里原本有 28 处手写圆角（4/6/8/10/12/20dp 混用），
 * 统一成三档后观感立刻"成套"：
 *  · [Shapes.small]  = 12dp —— 输入框、小药丸、标签
 *  · [Shapes.medium] = 18dp —— 卡片、按钮、面板
 *  · [Shapes.large]  = 26dp —— 大图、底部面板、抽屉里的主卡片
 * （圆形/胶囊仍然手写 `RoundedCornerShape(50)`，那是形态不是圆角刻度。）
 */
internal val NaiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// ---------------------------------------------------------------- 排版：三档层级

/**
 * 排版只调"层级"，不换字体（中文换字体要内嵌几 MB，收益不如把层次拉开）。
 * 关键三点：标题加重且字距略收、区块标签字距略放、正文行高放松（画廊风要"透气"）。
 */
internal val NaiTypography: Typography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
        bodyLarge = base.bodyLarge.copy(lineHeight = 24.sp, lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        )),
        bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
        bodySmall = base.bodySmall.copy(lineHeight = 18.sp, color = Color.Unspecified),
        // 区块小标题：字距拉开一点，像画册的标签
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    )
}

/**
 * App 主题入口。`dark` 由设置里「深色/浅色/跟随系统」决定，`palette` 由设置里
 * 「界面 → 配色」决定（见 MainActivity / AppSettings.palette）。
 * [overrides] = 用户在「调色板」里自己调的那几档（深/浅各一套，调用方按当前模式挑好）。
 *
 * ⚠️ 之前是直接 `MaterialTheme(colorScheme = darkColorScheme())` —— 用的是 M3 默认紫，
 * 而且没有 Shapes/Typography，所以各处只能手写圆角。现在统一走这里。
 */
@Composable
fun NaiStudioTheme(
    dark: Boolean,
    palette: String,
    overrides: PaletteOverrides = PaletteOverrides.None,
    /**
     * **电脑端"整体美化"**：窗口最底下铺了磨砂壁纸层时置 true（见 `Main.kt` 与
     * `DesktopBackdrop`）。开了之后页面底色（`background`）会降成
     * [NaiGlass.PAGE_ALPHA_DARK] / [NaiGlass.PAGE_ALPHA_LIGHT] 的**半透明**，
     * 于是 `Scaffold` / 侧栏 / 顶栏 / 底栏这些面背后透出壁纸纹理 —— 就是毛玻璃。
     *
     * 默认 false = 观感与加这个功能之前**完全一致**（手机端永远走默认）。
     */
    glass: Boolean = false,
    content: @Composable () -> Unit,
) {
    // palette："classic" = 经典画廊（默认），"bluewhite" = 蓝白；与深/浅色正交组合成四套。
    // overrides 为空时 `naiColorScheme` 原样返回那四套 —— 默认观感不变。
    val base = naiColorScheme(dark, palette, overrides)
    val colorScheme = if (glass) {
        // ⚠️ **只动 `background`**：卡片 / 面板 / 弹层用的 `surface*` 都保持不透明，
        // 于是"底是玻璃、卡是实心"，层级不会糊成一片。
        // ⚠️ 也**不动 `naiColorScheme()` 本身** —— 主题扩散遮罩（`ThemeWipeOverlay`）要的是
        // 不透明底色，它走的是 `naiBackgroundColor()`，不受这里影响。
        base.copy(
            background = base.background.copy(
                alpha = if (dark) NaiGlass.PAGE_ALPHA_DARK else NaiGlass.PAGE_ALPHA_LIGHT,
            ),
        )
    } else {
        base
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = NaiShapes,
        typography = NaiTypography,
    ) {
        CompositionLocalProvider(
            LocalPromptHighlight provides PromptHighlightColors(
                // ⚠️ 用户 2026-09-26 给了参考图「**按这个配**」—— 那套里正好有我们这三档：
                //   · **High Intensity** = 暗橙 → 加强（`{}` / `1.5::`）✓
                //   · **Low Intensity** = 蓝 → 削弱（`[]` / `0.5::`）✓
                //   · **Warning/Error** = 红 → 负加权 ✓
                // ⚠️ 参考图的 "Mid Intensity"（绿）**没有对应档** ✗：我们的高亮只有 强/弱/负 三档，
                // 要加"中等"得先改高亮器（按数值大小分档），这次没动 ✓。
                strong = if (dark) Color(0xFFFF8A3D) else Color(0xFFB34700),
                weak = if (dark) Color(0xFF4E9BFF) else Color(0xFF1565C0),
                negative = colorScheme.error,
            ),
            content = content,
        )
    }
}

// Desktop glass presets: tinted surfaces, soft accent containers and readable text.
// The backdrop supplies the frost; these colors also work without a background image.
private fun glassColorScheme(dark: Boolean, accent: Color, backdrop: Color, secondary: Color, tertiary: Color): ColorScheme {
    val base = if (dark) NaiDarkColors else NaiLightColors
    val lift = if (dark) Color.White else Color.Black
    val text = if (dark) Color(0xFFE2E6EC) else Color(0xFF20232B)
    val surface = lerp(backdrop, if (dark) accent else Color.White, if (dark) 0.045f else 0.56f)
    val container = lerp(surface, accent, if (dark) 0.12f else 0.09f)
    val primaryContainer = lerp(surface, accent, if (dark) 0.19f else 0.15f)
    val secondaryContainer = lerp(surface, secondary, if (dark) 0.18f else 0.13f)
    val tertiaryContainer = lerp(surface, tertiary, if (dark) 0.18f else 0.13f)
    return base.copy(
        primary = accent, onPrimary = onColorFor(accent),
        primaryContainer = primaryContainer,
        onPrimaryContainer = if (dark) lerp(accent, Color.White, 0.50f) else lerp(accent, Color.Black, 0.30f),
        secondary = secondary, onSecondary = onColorFor(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = if (dark) lerp(secondary, Color.White, 0.50f) else lerp(secondary, Color.Black, 0.30f),
        tertiary = tertiary, onTertiary = onColorFor(tertiary),
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = if (dark) lerp(tertiary, Color.White, 0.50f) else lerp(tertiary, Color.Black, 0.30f),
        background = backdrop, onBackground = text,
        surface = surface, onSurface = text, surfaceTint = accent,
        surfaceVariant = container, onSurfaceVariant = lerp(text, backdrop, 0.24f),
        surfaceContainerLowest = lerp(backdrop, if (dark) Color.Black else Color.White, 0.12f),
        surfaceContainerLow = surface,
        surfaceContainer = container,
        surfaceContainerHigh = lerp(container, lift, 0.035f),
        surfaceContainerHighest = lerp(container, lift, 0.075f),
        surfaceBright = lerp(container, lift, 0.075f),
        surfaceDim = lerp(backdrop, Color.Black, if (dark) 0.06f else 0.08f),
        outline = lerp(backdrop, text, if (dark) 0.34f else 0.38f),
        outlineVariant = lerp(backdrop, text, if (dark) 0.14f else 0.13f),
        inverseSurface = text, inverseOnSurface = backdrop,
        inversePrimary = if (dark) lerp(accent, Color.Black, 0.45f) else lerp(accent, Color.White, 0.55f),
    )
}

private val NaiGlassCyanDark = glassColorScheme(true, Color(0xFF3CCADD), Color(0xFF17232A), Color(0xFF6ED8C7), Color(0xFF91B8ED))
private val NaiGlassCyanLight = glassColorScheme(false, Color(0xFF087D8E), Color(0xFFE7F1F4), Color(0xFF167D6F), Color(0xFF426AA6))
private val NaiGlassVioletDark = glassColorScheme(true, Color(0xFFBAA0F5), Color(0xFF252132), Color(0xFFD49BD9), Color(0xFF91B9EB))
private val NaiGlassVioletLight = glassColorScheme(false, Color(0xFF7451B5), Color(0xFFEFEAF7), Color(0xFF9A568B), Color(0xFF416DA6))
private val NaiGlassEmeraldDark = glassColorScheme(true, Color(0xFF67D7AE), Color(0xFF1B2A27), Color(0xFF85CAD0), Color(0xFFCBD393))
private val NaiGlassEmeraldLight = glassColorScheme(false, Color(0xFF197C5D), Color(0xFFE8F2EC), Color(0xFF277780), Color(0xFF65772F))
private val NaiGlassRoseDark = glassColorScheme(true, Color(0xFFF29BAF), Color(0xFF302329), Color(0xFFF1B599), Color(0xFFC0AAF1))
private val NaiGlassRoseLight = glassColorScheme(false, Color(0xFFA94462), Color(0xFFF7ECEF), Color(0xFFA55D39), Color(0xFF7350AC))

/**
 * **青蓝**（用户 2026-09-27 指定的参考稿配色）。
 *
 * ⚠️ 取色必须与 `NaiSkinTokens.AccentDark/AccentLight` 一致 —— 两处是同一个青。
 *    值取自参考稿**最终生效那一层**（`generate-screen.html` 第 922 行起）：
 *    深色 `--accent: #28d5ef`、浅色 `--accent: #007eaa`；
 *    底色 `--bg` 深色 `#0c1724`、浅色 `#e9f2f8`。
 *    （前面两层被覆盖掉了，`#3ccadd` / `#1b1b1d` 那些是死值，别抄。）
 *
 * 复用 [glassColorScheme] 来派生整套色槽（它本来就是"底色 + 主色 → 全套"的算法）。
 */
private val NaiSolidAccentDark = glassColorScheme(
    dark = true,
    accent = Color(0xFF28D5EF),     // --accent
    backdrop = Color(0xFF0C1724),   // --bg
    secondary = Color(0xFF62E5F7),  // --accent-strong
    tertiary = Color(0xFF8FB4F0),
)
private val NaiSolidAccentLight = glassColorScheme(
    dark = false,
    accent = Color(0xFF007EAA),     // --accent
    backdrop = Color(0xFFE9F2F8),   // --bg
    secondary = Color(0xFF006C95),  // --accent-strong
    tertiary = Color(0xFF426AA6),
)

/** Resolves the preset, then applies saved custom colors without resetting preferences. */
fun naiColorScheme(
    dark: Boolean,
    palette: String,
    overrides: PaletteOverrides = PaletteOverrides.None,
): ColorScheme {
    val base = when {
        // 参考稿那套**青蓝实色**（用户 2026-09-27：「网页做的 UI 做电脑线 UI」）。
        // ⚠️ 取色必须与 `NaiSkinTokens.AccentDark/AccentLight` 一致 —— 两处是同一个青，
        //    否则"选青蓝配色"和"选实色风格"会给出两种不同的青。
        dark && palette == "accent" -> NaiSolidAccentDark
        !dark && palette == "accent" -> NaiSolidAccentLight
        dark && palette == "glass_cyan" -> NaiGlassCyanDark
        !dark && palette == "glass_cyan" -> NaiGlassCyanLight
        dark && palette == "glass_violet" -> NaiGlassVioletDark
        !dark && palette == "glass_violet" -> NaiGlassVioletLight
        dark && palette == "glass_emerald" -> NaiGlassEmeraldDark
        !dark && palette == "glass_emerald" -> NaiGlassEmeraldLight
        dark && palette == "glass_rose" -> NaiGlassRoseDark
        !dark && palette == "glass_rose" -> NaiGlassRoseLight
        dark && palette == "bluewhite" -> NaiBlueDarkColors
        !dark && palette == "bluewhite" -> NaiBlueLightColors
        // 三个"看得出区别"的色系（用户 2026-09-26）
        dark && palette == "violet" -> NaiVioletDarkColors
        !dark && palette == "violet" -> NaiVioletLightColors
        dark && palette == "forest" -> NaiForestDarkColors
        !dark && palette == "forest" -> NaiForestLightColors
        dark && palette == "sepia" -> NaiSepiaDarkColors
        !dark && palette == "sepia" -> NaiSepiaLightColors
        dark && palette == "novelai" -> NaiNovelAiDarkColors
        !dark && palette == "novelai" -> NaiNovelAiLightColors
        dark -> NaiDarkColors
        else -> NaiLightColors
    }
    return applyPaletteOverrides(base, overrides)
}

/** 直接取某个主题的**底色**（扩散遮罩画圆用它）。 */
fun naiBackgroundColor(dark: Boolean, palette: String): Color =
    naiColorScheme(dark, palette).background

/**
 * 侧边栏顶部那个 **App 名字**的颜色：**浅色模式纯黑、深色模式纯白**（用户 2026-09-16 要求）。
 *
 * 为什么不直接用 `colorScheme.onSurface`：那两档是**带主题色调**的近黑 / 近白
 * （暗色下偏冷的 #E7EEF3 之类），而用户要的是**正黑 / 正白**。
 * 放在 Theme.kt 里是为了守住"颜色只从这一处出"的规矩，不在界面上写死色值。
 */
fun naiBrandTitleColor(dark: Boolean): Color = if (dark) Color.White else Color.Black

/**
 * **品牌那抹蓝**（用户 2026-09-24：「侧边栏优化 logo，**logo 的右上角星星常态蓝色**，
 * 「NAI」的 **I 常态蓝色**」✓）。
 *
 * 口径（**"常态"这两个字是重点** ✓）：
 *  · 跟主题深浅**各取一档** ✓（暗色下要亮一点才压得住深底 ✓、亮色下要深一点才看得清 ✓）；
 *  · 但**不跟着"选中 / 悬停"变** ✗ —— 用户说的是常态 ✓，所以它就是这两个固定值 ✓。
 *
 * 取色沿用主题里已有的那两组：暗色 [SkyDark] / 亮色 [DeepSea] ✓
 *（和主按钮、进度条同一个色系 ✓，不会是"另一种蓝" ✓）。
 */
fun naiBrandAccent(dark: Boolean): Color = if (dark) SkyDark else DeepSea

/** 常用形状的语义别名（写代码时比 `shapes.medium` 更不容易用错档）。 */
object NaiShape {
    val Field = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(18.dp)
    val Panel = RoundedCornerShape(26.dp)
    val Pill = RoundedCornerShape(50)
}

/**
 * **毛玻璃**（用户 2026-09-16 要求）：底部抽屉与侧边栏共用这一套参数。
 *
 * ## 真毛玻璃是怎么来的
 *
 * 分两半，缺一不可：
 *  1. **抽屉自己半透明** —— 用 [PANEL_ALPHA] 的 `surface`；
 *  2. **背后的内容被糊掉** —— 由调用方在"抽屉拉开的进度"上乘 [blurRadius]，
 *     用 `Modifier.blur(...)` 施加。玻璃后面那层本来就是糊的，隔着半透明看过去才是那个质感。
 *
 * 只做第 1 步会变成"透过一块磨砂板看清晰的图"，很脏；只做第 2 步则是"图糊了但抽屉不透明"，看不见效果。
 *
 * ## 两个已知代价（不是 bug）
 *
 *  · `Modifier.blur` **在 Android 12（API 31）以下是空操作** —— 那时只剩半透明，没有模糊。
 *    minSdk 是 26，所以老机器上看到的就是一层薄纱。这是平台限制，没有便宜的替代方案。
 *  · 模糊会**强制内容进离屏图层**，抽屉拖动时每帧都要重画那层。所以半径别调太大
 *    （18dp 已经够"糊"了，再大既慢又看不出差别）。
 */
object NaiGlass {
    /**
     * **大面板**（底部抽屉、剧情放大页）的不透明度。压得比较低 —— 它们面积大，
     * 内容也都是自己排的版，透一点更像玻璃。
     */
    const val PANEL_ALPHA = 0.78f

    /**
     * **小条**（顶栏、抽屉的栏目条、2x放大/导演台、生成图片运行条）的不透明度。
     * 比面板高：这些小条上**压着要读的字和要点的按钮**，还得从底下那张图上区分出来。
     *
     * 两档的差别就是用户说的"**根据颜色深浅赋予对应的毛玻璃**" ——
     * 大块用浅玻璃、小条用实一点的，层级不会糊在一起。
     */
    const val BAR_ALPHA = 0.88f

    /**
     * 背后内容的模糊半径。**要乘上"抽屉拉开的进度"再用**，
     * 这样收起时是 0（完全没有模糊开销），拉开才逐渐糊掉。
     */
    val blurRadius = 18.dp

    /**
     * **页面底色**的不透明度 —— 只在电脑端"整体美化"打开时用（见 `NaiStudioTheme(glass = true)`）。
     *
     * 电脑端最底下会铺一层**磨砂壁纸**（`DesktopBackdrop.wallpaperBitmap()`），页面底色降到这个
     * 透明度之后：壁纸只透出一点纹理，字还是压得住 —— 就是 Windows 11 Mica 那个手感。
     *
     * 为什么不是更低：这套主题的底色本来就是"近黑/近白"，再淡下去深色模式在亮壁纸上会发灰、
     * 浅色模式在暗壁纸上会发脏。0.68 / 0.74 是"看得出来是玻璃、又不影响读字"的位置。
     *
     * ⚠️ 手机端**不用**这一档（那边没有铺壁纸层，底色变半透明只会露出窗口后面的黑）。
     */
    const val PAGE_ALPHA_DARK = 0.68f

    /** 浅色模式的页面底色不透明度（比深色略高：浅色底本来就亮，再淡就压不住花壁纸）。 */
    const val PAGE_ALPHA_LIGHT = 0.74f

    /**
     * **侧栏 / 提示词栏 / 底栏 / 手机抽屉共用的那一块玻璃**（用户 2026-09-19：
     * 「把提示词栏和底栏都换成侧边栏的毛玻璃样式」）。
     *
     * 就是常驻侧栏那一份配方：`surface` @ [PANEL_ALPHA]。抽成一个函数是为了**只有一处**：
     * 以前提示词栏写 `surface`、底栏写 `surfaceContainer`、栏目条写 `surfaceContainer @ BAR_ALPHA`
     * —— 同一屏上三块"玻璃"三种底色，看着就不像一套东西。
     *
     * ⚠️ 这些面背后得有东西（画布 / 壁纸磨砂层）才看得出是玻璃；背后是空页面时它跟不透明一样。
     */
    @Composable
    fun panelColor(): Color = NaiSkinTokens.panel()
}

// ---------------------------------------------------------------- 提示词加权高亮

/**
 * 提示词框里**加权高亮**用的颜色（`{}` 加强 / `[]` 削弱 / `-1::` 负加权）。
 *
 * 为什么单独开一组而不是直接用 `colorScheme`：这三档要能**同时**在一段文字里区分开，
 * 而 M3 的语义槽位（primary / secondary / tertiary）本来就是给"控件状态"用的，
 * 拿来当"语法高亮"会和页面上的选中态、按钮撞色。所以只借 `primary`（削弱=冷色）
 * 和 `error`（负加权），加强那档自己给一个暖色 —— 冷暖对比才一眼看得出哪边重。
 */
@Immutable
data class PromptHighlightColors(
    /** `{}` / `1.5::` 加强：暖色。 */
    val strong: Color,
    /** `[]` / `0.5::` 削弱：冷色，跟主色走。 */
    val weak: Color,
    /** `-1::x ::` 负加权：跟错误色走。 */
    val negative: Color,
)

/** 加强档的暖色：暗底要亮、亮底要深，否则总有一边看不清。 */
private val AmberDark = Color(0xFFFFC46B)
private val AmberLight = Color(0xFFA85C00)

/**
 * 当前主题下的加权高亮配色。默认给 `Unspecified`（= 不上色），
 * 所以任何没套 [NaiStudioTheme] 的地方（比如截图测试）也只是不着色，不会崩。
 */
val LocalPromptHighlight = staticCompositionLocalOf {
    PromptHighlightColors(
        strong = Color.Unspecified,
        weak = Color.Unspecified,
        negative = Color.Unspecified,
    )
}
