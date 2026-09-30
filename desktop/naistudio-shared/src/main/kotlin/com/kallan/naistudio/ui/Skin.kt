package com.kallan.naistudio.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * **外观风格**（用户 2026-09-27：「参考这个 HTML，网页做的 UI 做电脑线 UI」+
 * 「两套共存，做成可切换风格」）。
 *
 * ## 两套是什么
 *
 *  · [Glass]（默认）—— 现有的**毛玻璃**体系：面板半透明 + 背景柔光，
 *    观感与加这个功能之前**完全一致**。它是默认值，所以升级用户**看不出任何变化**。
 *  · [Reference] —— 参考稿（`generate-screen.html`）那套**青蓝毛玻璃**：半透明面板、
 *    青色主色、细描边、画布网格和柔光底色。可与四套现有配色组合使用。
 *
 * ## 为什么做成"风格"而不是直接把颜色换掉
 *
 * 用户要的是**两套共存、可切换**。做法上如果去各处把 `surface` 改成写死的 `#232326`，
 * 就等于**把毛玻璃拆掉了** —— 而毛玻璃是之前专门做过的工作（`NaiGlass` + `DesktopBackdrop`
 * 的磨砂壁纸）。所以这里不去动那些既有配色，而是**新加一层"风格"**：
 * 两套风格各自回答"面板该是什么色、圆角多大"，调用方只问 [NaiSkinTokens]，不关心是哪一套。
 *
 * ⚠️ 参考稿使用半透明面板。桌面背景由矢量渐变和可选的用户背景提供；不模糊文字前景。
 *
 * ## 怎么加一套新风格
 *
 * 在枚举里加一项、在 [NaiSkinTokens] 的各 `when` 里补一支。**别在调用点写 if/else** ——
 * 那样每加一套风格就要满仓库改。
 */
enum class NaiSkin(
    /** 落盘用的 id（存进 `AppSettings.skin`）。**是存储契约，不能改名**。 */
    val id: String,
    /** 设置页里显示这一项用的文案 key（走 `RuntimeText`）。 */
    val labelKey: String,
) {
    Glass("glass", "settings.skinGlass"),
    /** 参考稿那套**青蓝毛玻璃**。旧持久化 id `solid` 保持兼容。 */
    Reference("solid", "settings.skinReference"),
    ;

    companion object {
        /** 默认 = 毛玻璃：升级用户不动设置就跟以前一模一样。 */
        val Default = Glass

        /** id → 风格；认不出来的一律回落到默认（**绝不抛**，别让设置里一个错值把界面打崩）。 */
        fun fromId(id: String?): NaiSkin =
            entries.firstOrNull { it.id == id } ?: Default
    }
}

/**
 * 当前风格 —— 由入口（`Main.kt` / `StudioShell`）用 `CompositionLocalProvider` 提供，
 * 界面各处读它来决定"这块面该长什么样"。
 *
 * 默认 [NaiSkin.Glass]：任何忘了包 provider 的地方都回落到老观感，不会突然变样。
 */
val LocalNaiSkin = staticCompositionLocalOf { NaiSkin.Default }

/**
 * **设计 token**：两套风格各自的一份"尺寸与颜色表"。
 *
 * ## 为什么要有这一层
 *
 * 参考稿里有大量**写死的数值**（面板 `#232326`、圆角 `12px`、间距 `16px`…）。
 * 如果把这些数字散着搬进各个 Composable，以后想微调一处就得全仓库找。
 * 集中在这里之后：**改风格只改这一个文件**。
 *
 * ## 取值的出处
 *
 * [Solid] 那一套**逐条对应参考稿的 CSS 变量**，注释里标了原值，方便对照：
 * `generate-screen.html` 的 `:root` / `html[data-theme="dark"]`。
 * 参考稿是 px，Compose 是 dp —— 桌面端 1px ≈ 1dp，直接照抄。
 */
object NaiSkinTokens {

    /** 使用系统 sans-serif；Windows 会回退到系统 UI 字体，中文由系统字体链覆盖。 */
    val ReferenceFontFamily: FontFamily = FontFamily.SansSerif

    // ---------------------------------------------------------------- 颜色

    /**
     * **面板底**：参数栏、画布容器、卡片那一类"成块的面"。
     *
     * 玻璃风格 = 现有的 `surface @ PANEL_ALPHA`（和 [NaiGlass.panelColor] 一致，
     * 所以切到玻璃风格时**逐像素等于改动之前**）。
     */
    @Composable
    fun panel(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.surface.copy(alpha = NaiGlass.PANEL_ALPHA)
        NaiSkin.Reference -> surface()
    }

    /** 侧栏专用玻璃底（参考 CSS 的 `--bg-sidebar`）。 */
    @Composable
    fun sidebar(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.surface.copy(alpha = NaiGlass.PANEL_ALPHA)
        NaiSkin.Reference -> if (isDarkNow()) Color(0xE014212D) else Color(0xE6EFF7FB)
    }

    /** 参考稿标题栏 / 账户 chip 的毛玻璃底（`--glass`）。 */
    @Composable
    fun glass(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = NaiGlass.BAR_ALPHA)
        NaiSkin.Reference -> if (isDarkNow()) Color(0xE01C2B39) else Color(0xE8FFFFFF)
    }

    /** 不透明控件与表单底色，避免玻璃叠色影响关键对比。 */
    @Composable
    fun surface(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.surface
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF1C2B39) else Color.White
    }

    /** 参考稿主文字 token，不跟随用户选中的调色板漂移。 */
    @Composable
    fun text(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.onSurface
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFFE4EEF5) else Color(0xFF183247)
    }

    @Composable
    fun muted(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.onSurfaceVariant
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFFACBDCC) else Color(0xFF4E687C)
    }

    @Composable
    fun faint(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF91A7B9) else Color(0xFF607889)
    }

    @Composable
    fun accent(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.primary
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF5CD4EA) else Color(0xFF08799B)
    }

    @Composable
    fun accentStrong(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.primary
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF83E1EF) else Color(0xFF066782)
    }

    /** 独立的 accent-soft token：浅色为 rgba(0,171,216,.12)，不能从 accent alpha 推导。 */
    @Composable
    fun accentSoft(alphaMultiplier: Float = 1f): Color {
        val base = when (LocalNaiSkin.current) {
            NaiSkin.Glass -> MaterialTheme.colorScheme.primaryContainer
            NaiSkin.Reference -> if (isDarkNow()) Color(0x2128D5EF) else Color(0x1F00ABD8)
        }
        return base.copy(alpha = base.alpha * alphaMultiplier.coerceIn(0f, 1f))
    }

    @Composable
    fun accentContrast(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.onPrimary
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF082C39) else Color.White
    }

    @Composable
    fun primaryContainer(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.primaryContainer
        NaiSkin.Reference -> selected()
    }

    @Composable
    fun onPrimaryContainer(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.onPrimaryContainer
        NaiSkin.Reference -> onSelected()
    }

    @Composable fun accentPressed(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.primary
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF3BB8D1) else Color(0xFF05566D)
    }

    @Composable fun selected(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.primaryContainer
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF244B60) else Color(0xFFCDEAF4)
    }

    @Composable fun selectedHover(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.primaryContainer
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF2A596E) else Color(0xFFB9E0EE)
    }

    @Composable fun selectedPressed(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.primaryContainer
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF32677C) else Color(0xFFA5D4E5)
    }

    @Composable fun onSelected(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.onPrimaryContainer
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFFACF0FB) else Color(0xFF075875)
    }

    @Composable fun selectedBorder(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.outline
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF5594AB) else Color(0xFF4687A1)
    }

    @Composable fun controlBorder(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.outline
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF648297) else Color(0xFF7A92A4)
    }

    @Composable fun focus(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.primary
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF91E6F3) else Color(0xFF006C93)
    }

    @Composable fun brandInk(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.onSurface
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFFF5F8FB) else Color(0xFF182C3D)
    }

    @Composable fun brandStar(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.primary
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF5ABCF3) else Color(0xFF167DB7)
    }

    @Composable fun success(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> Color(0xFF73C99D)
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF8FDBB6) else Color(0xFF257451)
    }

    /** 统计热度格专用色阶；把数据颜色也集中管理，确保深浅主题下数字始终清楚。 */
    @Composable
    fun activityLevel(level: Int): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> when (level.coerceIn(0, 4)) {
            0 -> Color(0x33FFFFFF)
            1 -> Color(0xFF9BE9A8)
            2 -> Color(0xFF40C463)
            3 -> Color(0xFF30A14E)
            else -> Color(0xFF216E39)
        }
        NaiSkin.Reference -> when (level.coerceIn(0, 4)) {
            0 -> if (isDarkNow()) Color(0xFF263746) else Color(0xFFEAF1F6)
            1 -> if (isDarkNow()) Color(0xFF18475A) else Color(0xFFD0EDF3)
            2 -> if (isDarkNow()) Color(0xFF1F6476) else Color(0xFF9BD5E0)
            3 -> if (isDarkNow()) Color(0xFF3AA8BD) else Color(0xFF11758E)
            else -> if (isDarkNow()) Color(0xFF69D0DF) else Color(0xFF07566D)
        }
    }

    /** 与 [activityLevel] 配套的数字颜色，保证格子内字号较小时仍达到清晰对比。 */
    @Composable
    fun activityText(level: Int): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> if (level >= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        NaiSkin.Reference -> when {
            level >= 3 && !isDarkNow() -> Color.White
            level >= 3 -> Color(0xFF082C39)
            else -> text()
        }
    }

    /** **小条**（顶栏、栏目条、运行条）：玻璃风格下比大面板实一点（见 [NaiGlass.BAR_ALPHA]）。 */
    @Composable
    fun bar(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = NaiGlass.BAR_ALPHA)
        NaiSkin.Reference -> glass()
    }

    /** **页面底色**：参考风格使用带青蓝光晕的页面基色。 */
    @Composable
    fun pageBackground(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.background
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF15202B) else Color(0xFFEDF3F7)
    }

    /** **输入框 / 次级块**的底（参考稿 `--bg-subtle: #2a2a2e`）。 */
    @Composable
    fun subtle(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.surfaceContainerHigh
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF223443) else Color(0xFFEAF1F6)
    }

    /** 输入面：参考风格采用独立半透明输入底，玻璃风格沿用主题次级面。 */
    @Composable
    fun field(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.surfaceContainerHigh
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF182633) else Color(0xFFF6F9FB)
    }

    /** 悬停 / 选中的浅底（参考稿 `--bg-subtle-hover: #303034`）。 */
    @Composable
    fun subtleHover(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.surfaceContainerHighest
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF2B4052) else Color(0xFFE0EAF1)
    }

    /** tab 条那一层的底（参考稿 `--bg-strip: #201f22`，比面板略深）。 */
    @Composable
    fun strip(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.surfaceContainer
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF324C60) else Color(0xFFCEDEE9)
    }

    /**
     * **描边**：实色风格是参考稿那种 `rgba(255,255,255,.08)` 极细线；
     * 玻璃风格沿用主题的 `outlineVariant`（毛玻璃下描边太重会显脏）。
     */
    @Composable
    fun border(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.outlineVariant
        NaiSkin.Reference -> if (isDarkNow()) Color(0xFF344C60) else Color(0xFFCAD8E3)
    }

    /** 强描边（参考稿 `--border-strong`）：下拉框、步进器按钮那一圈。 */
    @Composable
    fun borderStrong(): Color = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> MaterialTheme.colorScheme.outline
        NaiSkin.Reference -> controlBorder()
    }

    // ---------------------------------------------------------------- 尺寸

    /**
     * **输入框 / 下拉 / 主按钮的圆角**（参考稿 `--radius-field` / `--radius-box`）。
     *
     * ⚠️ 参考稿**改过两轮**，最终生效值是：`--radius-field: 10px`、`--radius-box: 10px`、
     *    `--radius-sm: 7px`、`--radius-md: 9px`、`--radius-lg: 14px`（第 815 行那一组）。
     *    第 10 行那组（12/8/16）是被覆盖的旧值。
     */
    @Composable
    fun radiusField(): Dp = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> 12.dp
        NaiSkin.Reference -> 12.dp
    }

    /** **小方块**（提示词框、图标按钮）的圆角（参考稿 `--radius-box: 10px`）。 */
    @Composable
    fun radiusBox(): Dp = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> 10.dp
        NaiSkin.Reference -> 12.dp
    }

    /** **小圆角**（chip / 下拉 / 步进器按钮 / 关闭按钮）：参考稿 `--radius-sm: 7px`。 */
    @Composable
    fun radiusSmall(): Dp = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> 8.dp
        NaiSkin.Reference -> 8.dp
    }

    /** **中等圆角**（导航项 / 统计小卡）：参考稿 `--radius-md: 9px`。 */
    @Composable
    fun radiusMedium(): Dp = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> 12.dp
        NaiSkin.Reference -> 12.dp
    }

    /** **大容器**（参数栏、画布框、统计浮层）的圆角（参考稿 `--radius-lg: 14px`）。 */
    @Composable
    fun radiusLarge(): Dp = when (LocalNaiSkin.current) {
        NaiSkin.Glass -> 18.dp
        NaiSkin.Reference -> 16.dp
    }

    /** 药丸形（胶囊、chip）—— 两套风格一样，都是全圆。 */
    fun radiusPill(): Dp = 999.dp

    // ---------------------------------------------------------------- 便捷形状

    @Composable fun fieldShape() = RoundedCornerShape(radiusField())
    @Composable fun boxShape() = RoundedCornerShape(radiusBox())
    @Composable fun smallShape() = RoundedCornerShape(radiusSmall())
    @Composable fun mediumShape() = RoundedCornerShape(radiusMedium())
    @Composable fun largeShape() = RoundedCornerShape(radiusLarge())
    @Composable fun pillShape() = RoundedCornerShape(percent = 50)

    // ------------------------------------------- 参考稿风格（最终生效那一层原值）

    /** --bg */
    @Composable
    private fun solidPanel(): Color = if (isDarkNow()) SOLID_PANEL_DARK else SOLID_PANEL_LIGHT

    @Composable
    private fun solidPage(): Color = if (isDarkNow()) SOLID_PAGE_DARK else SOLID_PAGE_LIGHT

    @Composable
    private fun solidSubtle(): Color = if (isDarkNow()) SOLID_SUBTLE_DARK else SOLID_SUBTLE_LIGHT

    @Composable
    private fun solidSubtleHover(): Color = if (isDarkNow()) SOLID_HOVER_DARK else SOLID_HOVER_LIGHT

    @Composable
    private fun solidStrip(): Color = if (isDarkNow()) SOLID_STRIP_DARK else SOLID_STRIP_LIGHT

    @Composable
    private fun solidBorder(): Color = if (isDarkNow()) SOLID_BORDER_DARK else SOLID_BORDER_LIGHT

    @Composable
    private fun solidBorderStrong(): Color = if (isDarkNow()) SOLID_BORDER_STRONG_DARK else SOLID_BORDER_STRONG_LIGHT

    /**
     * 现在是不是深色模式。
     *
     * ⚠️ 用**背景色和黑/白的对比度**判断，而不是再去读一遍 `settings.theme`：主题已经解析过一轮了
     * （`system` 档要看系统设置），这里再读一次就会出现"两个地方各判一次、结论不一致"。
     * ⚠️ 用 [contrastRatio]（公开）而不是 `relativeLuminance()`（那是 `Palette.kt` 里的 private）。
     */
    @Composable
    private fun isDarkNow(): Boolean {
        val bg = MaterialTheme.colorScheme.background
        // 底色离黑更近 ⇒ 是深色模式
        return contrastRatio(bg, Color.Black) < contrastRatio(bg, Color.White)
    }

    // 深色（**最终生效的那一层**，`generate-screen.html` 第 922 行起那份
    // 「Desktop reference layout · cyan glass theme」—— 前面还有两层被它覆盖掉，
    //  改这里之前**别去抄第 10~812 行那些值**，那些是死代码）
    private val SOLID_PAGE_DARK = Color(0xFF0C1724)          // --bg
    private val SOLID_PANEL_DARK = Color(0xC2142535)         // --bg-panel rgba(20,37,53,.76)
    private val SOLID_SUBTLE_DARK = Color(0x1289BCDB)        // --bg-subtle rgba(137,188,219,.07)
    private val SOLID_HOVER_DARK = Color(0x217BC9EE)         // --bg-subtle-hover rgba(123,201,238,.13)
    private val SOLID_STRIP_DARK = Color(0x4D091928)         // --bg-strip rgba(9,25,40,.30)
    private val SOLID_BORDER_DARK = Color(0x21B5DDF6)        // --border rgba(181,221,246,.13)
    private val SOLID_BORDER_STRONG_DARK = Color(0x47A0DBF8) // --border-strong rgba(160,219,248,.28)

    // 浅色（同一层）
    private val SOLID_PAGE_LIGHT = Color(0xFFE9F2F8)         // --bg
    private val SOLID_PANEL_LIGHT = Color(0xA8FFFFFF)        // --bg-panel rgba(255,255,255,.66)
    private val SOLID_SUBTLE_LIGHT = Color(0x0E307597)       // --bg-subtle rgba(48,117,151,.055)
    private val SOLID_HOVER_LIGHT = Color(0x1A009ECC)        // --bg-subtle-hover rgba(0,158,204,.10)
    private val SOLID_STRIP_LIGHT = Color(0x61F0F9FF)        // --bg-strip rgba(240,249,255,.38)
    private val SOLID_BORDER_LIGHT = Color(0x2B3E7693)       // --border rgba(62,118,147,.17)
    private val SOLID_BORDER_STRONG_LIGHT = Color(0x521986AB) // --border-strong rgba(25,134,171,.32)

    // ------------------------------------------------- 实色风格的强调色

    /**
     * 参考稿的青蓝主色（`--accent`）。**这是 `ui/Theme.kt` 里那套青蓝配色预设用的同一个值** ——
     * 两处必须是同一个数，否则"选了青蓝配色"和"选了实色风格"会给出两种青。
     *
     * ⚠️ 取自**最终生效那一层**（第 922 行起）：`#28d5ef` / `#007eaa`。
     *    早前扫到的 `#3ccadd` 属于被覆盖掉的旧层，**是错的**。
     */
    val AccentDark = Color(0xFF28D5EF)
    val AccentLight = Color(0xFF007EAA)
}
