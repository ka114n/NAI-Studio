package com.kallan.naistudio.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * NAI Studio 的视觉层 —— **Reference 皮肤**（1.1.121 起与电脑线同一套 UI 规范）。
 *
 * 色值全在 `ReferenceSkin.kt`（[RefDark] / [RefLight] + 用户调色板派生），这里只负责
 * 把 token 映射进 MaterialTheme（颜色 / 圆角 / 排版），所以全 App 改一处就全局生效。
 * 旧的「经典画廊 / 蓝白 / 色相预设」已撤掉，`palette` 设置项保留字段但不再读取。
 */

// ---------------------------------------------------------------- 形状：Reference 圆角刻度

/**
 * Reference 圆角：sm 8 / md 12 / lg 16 / xl 20。M3 组件按 key 取：
 *  · extraSmall = 12 —— 输入框、下拉菜单（Reference field = 12）
 *  · small      = 8  —— 筛选胶囊、小标签
 *  · medium     = 12 —— 卡片
 *  · large      = 16 —— 大面板
 *  · extraLarge = 20 —— 对话框、底部面板
 */
internal val NaiShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

// ---------------------------------------------------------------- 排版：三档层级

/**
 * 排版只调"层级"，不换字体（中文换字体要内嵌几 MB，收益不如把层次拉开）。
 * 关键三点：标题加重且字距略收、区块标签字距略放、正文行高放松（画廊风要"透气"）。
 */
internal val NaiTypography: Typography = Typography().let { base ->
    // 字号对齐手机网页原型（base 14）：topbar/sheet 标题 17/600、卡片标题 15/600、按钮 14/600、
    // .label 12.5、.hint 12、小字 11.5。
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleMedium = base.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        titleSmall = base.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.sp),
        bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.sp, color = Color.Unspecified),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = base.labelMedium.copy(fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        labelSmall = base.labelSmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    )
}

/**
 * App 主题入口。`dark` 由设置里「深色/浅色/跟随系统」决定；
 * [overrides] = 用户在「调色板」里自己调的那几档（深/浅各一套，调用方按当前模式挑好）。
 * [palette] = 配色方案 key（见 [RefSchemeKeys]；不认识的旧值当「冰川」）。
 */
@Composable
fun NaiStudioTheme(
    dark: Boolean,
    palette: String = "",
    overrides: PaletteOverrides = PaletteOverrides.None,
    content: @Composable () -> Unit,
) {
    val tokens = refTokens(dark, overrides, palette)
    val colorScheme = tokens.toColorScheme()
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = NaiShapes,
        typography = NaiTypography,
    ) {
        CompositionLocalProvider(
            LocalRef provides tokens,
            LocalPromptHighlight provides PromptHighlightColors(
                strong = tokens.warning,
                // 削弱 = 冷色：直接借主色
                weak = colorScheme.primary,
                negative = colorScheme.error,
            ),
            content = content,
        )
    }
}

/**
 * `(深色?, 覆盖)` → ColorScheme。抽出来是为了让**主题切换动画**能提前知道
 * "切过去是什么颜色"：扩散遮罩要在主题还没变的时候就用目标底色画圆。
 */
fun naiColorScheme(
    dark: Boolean,
    palette: String = "",
    overrides: PaletteOverrides = PaletteOverrides.None,
): ColorScheme = refTokens(dark, overrides, palette).toColorScheme()

/** 直接取某个主题的**底色**（扩散遮罩画圆用它）。 */
fun naiBackgroundColor(dark: Boolean, palette: String = ""): Color =
    naiColorScheme(dark, palette).background

/** 侧边栏顶部 App 名字的颜色：跟 Reference 正文色走。 */
fun naiBrandTitleColor(dark: Boolean): Color = if (dark) RefDark.text else RefLight.text

/** 常用形状的语义别名（Reference：field 12 / card 12 / panel 16 / pill）。 */
object NaiShape {
    val Field = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(16.dp)
    val Box = RoundedCornerShape(12.dp)
    val Panel = RoundedCornerShape(16.dp)
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
     * **大面板**（底部抽屉、剧情放大页、中控台）的不透明度。
     * 压得比较低 —— 它们面积大，内容也都是自己排的版，透一点更像玻璃。
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
