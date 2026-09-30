package com.kallan.naistudio.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * **Reference 皮肤**（2026-09-28 起手机线与电脑线同一套 UI 规范，1.1.121 起生效）。
 *
 * 色值逐个对齐电脑线 `ui/Skin.kt` 与手机网页原型（`20260928-phone-ui-web/index.html`）。
 * 配色只有这一套（经典画廊 / 蓝白 / 色相预设已撤掉），深浅两档；
 * 用户可以在「设置 → 界面 → 调色板」改 4 个基色（主色 / 底色 / 卡片色 / 文字色），
 * 其余色槽按原型里的 color-mix 公式**派生**（见 [refTokens]）。
 */
@Immutable
data class RefTokens(
    val dark: Boolean,
    val bg: Color,
    val panel: Color,
    val field: Color,
    val subtle: Color,
    val subtleHover: Color,
    val strip: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val muted: Color,
    val faint: Color,
    val accent: Color,
    val accentStrong: Color,
    val accentPressed: Color,
    val accentSoft: Color,
    val accentContrast: Color,
    val selected: Color,
    val selectedHover: Color,
    val onSelected: Color,
    val selectedBorder: Color,
    val focus: Color,
    val success: Color,
    val negative: Color,
    val negativeSoft: Color,
    val warning: Color,
    /** 配色方案 key（冰川 = "reference"），见 [refSchemeBase]。 */
    val scheme: String = "reference",
    /** 统计热力格 5 档（网页 `--lv0..4`）。 */
    val lv: List<Color> = emptyList(),
    /** 侧栏标志画框（网页 `--brand-ink`）。 */
    val brandInkColor: Color = Color.Unspecified,
)

val RefDark = RefTokens(
    dark = true,
    bg = Color(0xFF15202B),
    panel = Color(0xFF1C2B39),
    field = Color(0xFF182633),
    subtle = Color(0xFF223443),
    subtleHover = Color(0xFF2B4052),
    strip = Color(0xFF324C60),
    border = Color(0xFF344C60),
    borderStrong = Color(0xFF648297),
    text = Color(0xFFE4EEF5),
    muted = Color(0xFFACBDCC),
    faint = Color(0xFF91A7B9),
    accent = Color(0xFF5CD4EA),
    accentStrong = Color(0xFF83E1EF),
    accentPressed = Color(0xFF3BB8D1),
    accentSoft = Color(0x2128D5EF),
    accentContrast = Color(0xFF082C39),
    selected = Color(0xFF244B60),
    selectedHover = Color(0xFF2A596E),
    onSelected = Color(0xFFACF0FB),
    selectedBorder = Color(0xFF5594AB),
    focus = Color(0xFF91E6F3),
    success = Color(0xFF8FDBB6),
    negative = Color(0xFFE06C75),
    negativeSoft = Color(0x24E06C75),
    warning = Color(0xFFE3A75C),
    lv = listOf(Color(0xFF263746), Color(0xFF18475A), Color(0xFF1F6476), Color(0xFF3AA8BD), Color(0xFF69D0DF)),
    brandInkColor = Color(0xFFF5F8FB),
)

val RefLight = RefTokens(
    dark = false,
    bg = Color(0xFFEDF3F7),
    panel = Color(0xFFFFFFFF),
    field = Color(0xFFF6F9FB),
    subtle = Color(0xFFEAF1F6),
    subtleHover = Color(0xFFE0EAF1),
    strip = Color(0xFFCEDEE9),
    border = Color(0xFFCAD8E3),
    borderStrong = Color(0xFF7A92A4),
    text = Color(0xFF183247),
    muted = Color(0xFF4E687C),
    faint = Color(0xFF607889),
    accent = Color(0xFF08799B),
    accentStrong = Color(0xFF066782),
    accentPressed = Color(0xFF055A72),
    accentSoft = Color(0x1F00ABD8),
    accentContrast = Color(0xFFFFFFFF),
    selected = Color(0xFFCDEAF4),
    selectedHover = Color(0xFFBFE3EF),
    onSelected = Color(0xFF075875),
    selectedBorder = Color(0xFF4687A1),
    focus = Color(0xFF006C93),
    success = Color(0xFF2E8B63),
    negative = Color(0xFFC62828),
    negativeSoft = Color(0x1AC62828),
    warning = Color(0xFFB96A1A),
    lv = listOf(Color(0xFFE0EAF1), Color(0xFFB9E3EF), Color(0xFF7FCBE0), Color(0xFF2FA3C0), Color(0xFF08799B)),
    brandInkColor = Color(0xFF183247),
)

/** CSS `color-mix(in srgb, a p%, b)`：sRGB 线性插值（Compose 的 `lerp` 走 Oklab，结果不一样）。 */
fun mixSrgb(a: Color, p: Float, b: Color): Color = Color(
    red = a.red * p + b.red * (1f - p),
    green = a.green * p + b.green * (1f - p),
    blue = a.blue * p + b.blue * (1f - p),
    alpha = a.alpha * p + b.alpha * (1f - p),
)

private val AccentInkDark = Color(0xFF082C39)

/**
 * 基础 token + 用户覆盖的 4 个基色 → 最终 token。公式与网页原型 `applyPal()` 逐条一致：
 * 没覆盖的那一组保持手调的原值（不派生），覆盖了才整组重算。
 */
fun refTokens(
    dark: Boolean,
    o: PaletteOverrides = PaletteOverrides.None,
    scheme: String = "reference",
): RefTokens {
    val base = refSchemeBase(scheme, dark)
    if (o.isEmpty) return base
    val panel = o.panel ?: base.panel
    val text = o.text ?: base.text
    var t = base.copy(panel = panel, text = text)
    val white = Color.White
    val black = Color.Black
    o.primary?.let { a ->
        t = t.copy(
            accent = a,
            focus = mixSrgb(a, 0.70f, if (dark) white else black),
            accentStrong = mixSrgb(a, if (dark) 0.78f else 0.85f, if (dark) white else black),
            accentPressed = mixSrgb(a, 0.82f, black),
            accentSoft = a.copy(alpha = if (dark) 0.14f else 0.12f),
            accentContrast = if (contrastRatio(a, white) >= contrastRatio(a, AccentInkDark)) white else AccentInkDark,
            selected = mixSrgb(a, if (dark) 0.26f else 0.22f, panel),
            selectedHover = mixSrgb(a, if (dark) 0.32f else 0.28f, panel),
            selectedBorder = mixSrgb(a, 0.62f, panel),
            onSelected = mixSrgb(a, if (dark) 0.55f else 0.70f, if (dark) white else black),
        )
    }
    o.background?.let { b ->
        t = t.copy(bg = b, field = mixSrgb(b, if (dark) 0.92f else 0.60f, white))
    }
    if (o.panel != null) {
        t = t.copy(
            subtle = mixSrgb(panel, if (dark) 0.90f else 0.94f, text),
            subtleHover = mixSrgb(panel, if (dark) 0.84f else 0.88f, text),
            strip = mixSrgb(panel, if (dark) 0.76f else 0.82f, text),
            border = mixSrgb(panel, if (dark) 0.78f else 0.84f, text),
            borderStrong = mixSrgb(panel, if (dark) 0.58f else 0.55f, text),
        )
    }
    if (o.text != null) {
        t = t.copy(muted = mixSrgb(text, 0.76f, panel), faint = mixSrgb(text, 0.62f, panel))
    }
    return t
}

/**
 * Reference token → M3 ColorScheme。全 App 的控件都走 `MaterialTheme.colorScheme`，
 * 所以映射一次，所有页面 / 抽屉 / 弹窗就一起换成 Reference。
 *
 *  · 容器：Low / Container / High = panel（底部面板、菜单、对话框），Highest = subtle，Lowest = field；
 *  · 选中态：primaryContainer / secondaryContainer = selected + onSelected（抽屉选中项、筛选胶囊）；
 *  · 描边：outline = borderStrong，outlineVariant = border。
 */
fun RefTokens.toColorScheme(): ColorScheme {
    val onError = if (dark) Color(0xFF2A0A0E) else Color.White
    val errorContainer = mixSrgb(negative, 0.16f, panel)
    val onErrorContainer = mixSrgb(negative, 0.70f, text)
    return if (dark) {
        darkColorScheme(
            primary = accent, onPrimary = accentContrast,
            primaryContainer = selected, onPrimaryContainer = onSelected,
            inversePrimary = accentStrong,
            secondary = accent, onSecondary = accentContrast,
            secondaryContainer = selected, onSecondaryContainer = onSelected,
            tertiary = accentStrong, onTertiary = accentContrast,
            tertiaryContainer = selected, onTertiaryContainer = onSelected,
            background = bg, onBackground = text,
            surface = panel, onSurface = text,
            surfaceVariant = subtle, onSurfaceVariant = muted,
            surfaceTint = Color.Transparent,
            inverseSurface = text, inverseOnSurface = panel,
            error = negative, onError = onError,
            errorContainer = errorContainer, onErrorContainer = onErrorContainer,
            outline = borderStrong, outlineVariant = border,
            scrim = Color.Black,
            surfaceBright = subtleHover, surfaceDim = bg,
            surfaceContainerLowest = field, surfaceContainerLow = panel,
            surfaceContainer = panel, surfaceContainerHigh = panel,
            surfaceContainerHighest = subtle,
        )
    } else {
        lightColorScheme(
            primary = accent, onPrimary = accentContrast,
            primaryContainer = selected, onPrimaryContainer = onSelected,
            inversePrimary = accentStrong,
            secondary = accent, onSecondary = accentContrast,
            secondaryContainer = selected, onSecondaryContainer = onSelected,
            tertiary = accentStrong, onTertiary = accentContrast,
            tertiaryContainer = selected, onTertiaryContainer = onSelected,
            background = bg, onBackground = text,
            surface = panel, onSurface = text,
            surfaceVariant = subtle, onSurfaceVariant = muted,
            surfaceTint = Color.Transparent,
            inverseSurface = text, inverseOnSurface = panel,
            error = negative, onError = onError,
            errorContainer = errorContainer, onErrorContainer = onErrorContainer,
            outline = borderStrong, outlineVariant = border,
            scrim = Color.Black,
            surfaceBright = panel, surfaceDim = subtleHover,
            surfaceContainerLowest = field, surfaceContainerLow = panel,
            surfaceContainer = panel, surfaceContainerHigh = panel,
            surfaceContainerHighest = subtle,
        )
    }
}

/** 当前 Reference token（自定义控件要用 field / selectedBorder / accentSoft 这类 M3 没有的槽）。 */
val LocalRef = staticCompositionLocalOf { RefDark }

/** 调色板取色器里每个基色的建议色（与网页原型 `PAL_SWATCH` 相同）。 */
fun paletteSwatches(slot: PaletteSlot, dark: Boolean): List<Color> = when (slot) {
    PaletteSlot.PRIMARY -> if (dark) listOf(0xFF5CD4EA, 0xFF8FDBB6, 0xFFE3A75C, 0xFFE06C75, 0xFFB48CF0, 0xFF6FA8FF)
    else listOf(0xFF08799B, 0xFF2E8B63, 0xFFB96A1A, 0xFFC62828, 0xFF7B4FC9, 0xFF2F62C8)
    PaletteSlot.BACKGROUND -> if (dark) listOf(0xFF15202B, 0xFF101418, 0xFF1B1D24, 0xFF1A1F1C, 0xFF221B24, 0xFF000000)
    else listOf(0xFFEDF3F7, 0xFFF5F5F5, 0xFFF3F0EA, 0xFFEEF4EE, 0xFFF4EEF6, 0xFFFFFFFF)
    PaletteSlot.PANEL -> if (dark) listOf(0xFF1C2B39, 0xFF181C21, 0xFF242731, 0xFF212823, 0xFF2A2230, 0xFF111111)
    else listOf(0xFFFFFFFF, 0xFFFAFAFA, 0xFFFBF8F2, 0xFFF7FBF7, 0xFFFBF7FC, 0xFFF2F5F8)
    PaletteSlot.TEXT -> if (dark) listOf(0xFFE4EEF5, 0xFFFFFFFF, 0xFFEDE6DA, 0xFFDDE8DF, 0xFFEBE2F0, 0xFFC9D3DB)
    else listOf(0xFF183247, 0xFF111111, 0xFF3A2E22, 0xFF1F3326, 0xFF2E2236, 0xFF37474F)
}.map { Color(it) }

// ---------------------------------------------------------------- 配色方案（1.1.123，口径同网页原型 v2）

/**
 * 配色方案 key（设置里存在 `AppSettings.palette`）。「冰川」= [RefDark] / [RefLight] 原值；
 * 其余五套按冰川的明度 / 饱和度口径换色相生成（`.phoneweb/schemes.py`），每套都有暗 / 浅两版。
 * 旧值 `classic` 之类不认识的 key 一律当冰川。成功 / 危险 / 警告色不随方案变。
 */
val RefSchemeKeys = listOf("reference", "graphite", "violet", "forest", "amber", "rose")

private val GraphiteDark = RefDark.copy(
    scheme = "graphite",
    bg = Color(0xFF1D1F23),
    panel = Color(0xFF272A2F),
    field = Color(0xFF222529),
    subtle = Color(0xFF2E3237),
    subtleHover = Color(0xFF393E44),
    strip = Color(0xFF43484F),
    border = Color(0xFF444950),
    borderStrong = Color(0xFF767C84),
    text = Color(0xFFEAECEF),
    muted = Color(0xFFB7BBC0),
    faint = Color(0xFF9FA4AB),
    accent = Color(0xFF86B4FF),
    accentStrong = Color(0xFFAFCDFF),
    accentPressed = Color(0xFF5898FF),
    accentContrast = Color(0xFF081B39),
    selected = Color(0xFF243B60),
    selectedHover = Color(0xFF2C456D),
    onSelected = Color(0xFFB2CEFA),
    selectedBorder = Color(0xFF5475AB),
    focus = Color(0xFFD8E7FF),
    brandInkColor = Color(0xFFF7F8F9),
    accentSoft = Color(0x2186B4FF),
    lv = listOf(Color(0xFF31353A), Color(0xFF193157), Color(0xFF224277), Color(0xFF3D6CB8), Color(0xFF739BDE)),
)

private val GraphiteLight = RefLight.copy(
    scheme = "graphite",
    bg = Color(0xFFF1F2F4),
    panel = Color(0xFFFFFFFF),
    field = Color(0xFFF8F8F9),
    subtle = Color(0xFFEEEFF1),
    subtleHover = Color(0xFFE6E8EA),
    strip = Color(0xFFD8DBDF),
    border = Color(0xFFD3D6DA),
    borderStrong = Color(0xFF898E95),
    text = Color(0xFF292E36),
    muted = Color(0xFF5E646B),
    faint = Color(0xFF6F747A),
    accent = Color(0xFF2F62C8),
    accentStrong = Color(0xFF2A58B3),
    accentPressed = Color(0xFF2650A3),
    accentContrast = Color(0xFFFFFFFF),
    selected = Color(0xFFCDDAF3),
    selectedHover = Color(0xFFBFCFEF),
    onSelected = Color(0xFF072C74),
    selectedBorder = Color(0xFF4664A0),
    focus = Color(0xFF2C5CBC),
    brandInkColor = Color(0xFF292E36),
    accentSoft = Color(0x1F2F62C8),
    lv = listOf(Color(0xFFE6E8EA), Color(0xFFBBCBED), Color(0xFF82A1DE), Color(0xFF3060C0), Color(0xFF2F62C8)),
)

private val VioletDark = RefDark.copy(
    scheme = "violet",
    bg = Color(0xFF1C1729),
    panel = Color(0xFF261E37),
    field = Color(0xFF211A31),
    subtle = Color(0xFF2D2441),
    subtleHover = Color(0xFF382E4F),
    strip = Color(0xFF41365C),
    border = Color(0xFF42375D),
    borderStrong = Color(0xFF746793),
    text = Color(0xFFE9E5F4),
    muted = Color(0xFFB6AECA),
    faint = Color(0xFF9E94B6),
    accent = Color(0xFFB69CFF),
    accentStrong = Color(0xFFD4C5FF),
    accentPressed = Color(0xFF946EFF),
    accentContrast = Color(0xFF150839),
    selected = Color(0xFF342460),
    selectedHover = Color(0xFF3D2C6D),
    onSelected = Color(0xFFC5B2FA),
    selectedBorder = Color(0xFF6B54AB),
    focus = Color(0xFFF2EEFF),
    brandInkColor = Color(0xFFF7F6FA),
    accentSoft = Color(0x21B69CFF),
    lv = listOf(Color(0xFF302843), Color(0xFF291957), Color(0xFF392277), Color(0xFF5D3DB8), Color(0xFF8F73DE)),
)

private val VioletLight = RefLight.copy(
    scheme = "violet",
    bg = Color(0xFFF1EEF6),
    panel = Color(0xFFFFFFFF),
    field = Color(0xFFF8F6FB),
    subtle = Color(0xFFEEEBF5),
    subtleHover = Color(0xFFE5E1EF),
    strip = Color(0xFFD7D0E7),
    border = Color(0xFFD2CBE1),
    borderStrong = Color(0xFF887DA1),
    text = Color(0xFF281C43),
    muted = Color(0xFF5D5179),
    faint = Color(0xFF6D6386),
    accent = Color(0xFF6A45C4),
    accentStrong = Color(0xFF5E3AB6),
    accentPressed = Color(0xFF5635A6),
    accentContrast = Color(0xFFFFFFFF),
    selected = Color(0xFFD8CDF3),
    selectedHover = Color(0xFFCDBFEF),
    onSelected = Color(0xFF270774),
    selectedBorder = Color(0xFF6046A0),
    focus = Color(0xFF623CBE),
    brandInkColor = Color(0xFF281C43),
    accentSoft = Color(0x1F6A45C4),
    lv = listOf(Color(0xFFE5E1EF), Color(0xFFC9BBED), Color(0xFF9D82DE), Color(0xFF5A30C0), Color(0xFF6A45C4)),
)

private val ForestDark = RefDark.copy(
    scheme = "forest",
    bg = Color(0xFF182821),
    panel = Color(0xFF20352C),
    field = Color(0xFF1B3027),
    subtle = Color(0xFF263F35),
    subtleHover = Color(0xFF304D41),
    strip = Color(0xFF385A4C),
    border = Color(0xFF395B4D),
    borderStrong = Color(0xFF6A9080),
    text = Color(0xFFE6F3ED),
    muted = Color(0xFFB0C8BE),
    faint = Color(0xFF96B4A7),
    accent = Color(0xFF6FD8A6),
    accentStrong = Color(0xFF8FE1BA),
    accentPressed = Color(0xFF4BCE90),
    accentContrast = Color(0xFF083922),
    selected = Color(0xFF246044),
    selectedHover = Color(0xFF2C6D4E),
    onSelected = Color(0xFFB2FAD8),
    selectedBorder = Color(0xFF54AB82),
    focus = Color(0xFFAFE9CE),
    brandInkColor = Color(0xFFF6FAF8),
    accentSoft = Color(0x216FD8A6),
    lv = listOf(Color(0xFF2A4238), Color(0xFF19573A), Color(0xFF22774F), Color(0xFF3DB87D), Color(0xFF73DEAB)),
)

private val ForestLight = RefLight.copy(
    scheme = "forest",
    bg = Color(0xFFEFF6F3),
    panel = Color(0xFFFFFFFF),
    field = Color(0xFFF6FBF9),
    subtle = Color(0xFFEBF4F0),
    subtleHover = Color(0xFFE2EFE9),
    strip = Color(0xFFD1E5DD),
    border = Color(0xFFCDE0D8),
    borderStrong = Color(0xFF7F9F91),
    text = Color(0xFF1E4132),
    muted = Color(0xFF547668),
    faint = Color(0xFF658477),
    accent = Color(0xFF1D7A50),
    accentStrong = Color(0xFF186542),
    accentPressed = Color(0xFF145538),
    accentContrast = Color(0xFFFFFFFF),
    selected = Color(0xFFCDF3E2),
    selectedHover = Color(0xFFBFEFD9),
    onSelected = Color(0xFF077443),
    selectedBorder = Color(0xFF46A077),
    focus = Color(0xFF1A6E48),
    brandInkColor = Color(0xFF1E4132),
    accentSoft = Color(0x1F1D7A50),
    lv = listOf(Color(0xFFE2EFE9), Color(0xFFBBEDD6), Color(0xFF82DEB4), Color(0xFF30C07F), Color(0xFF1D7A50)),
)

private val AmberDark = RefDark.copy(
    scheme = "amber",
    bg = Color(0xFF272018),
    panel = Color(0xFF352B20),
    field = Color(0xFF2F261C),
    subtle = Color(0xFF3F3326),
    subtleHover = Color(0xFF4C3F31),
    strip = Color(0xFF594A39),
    border = Color(0xFF594B3A),
    borderStrong = Color(0xFF8F7E6B),
    text = Color(0xFFF2EDE6),
    muted = Color(0xFFC7BDB1),
    faint = Color(0xFFB3A697),
    accent = Color(0xFFF0B866),
    accentStrong = Color(0xFFF4C98B),
    accentPressed = Color(0xFFECA53C),
    accentContrast = Color(0xFF392508),
    selected = Color(0xFF604824),
    selectedHover = Color(0xFF6D532C),
    onSelected = Color(0xFFFADDB2),
    selectedBorder = Color(0xFFAB8854),
    focus = Color(0xFFF7DAB0),
    brandInkColor = Color(0xFFFAF8F6),
    accentSoft = Color(0x21F0B866),
    lv = listOf(Color(0xFF41362A), Color(0xFF573E19), Color(0xFF775422), Color(0xFFB8863D), Color(0xFFDEB273)),
)

private val AmberLight = RefLight.copy(
    scheme = "amber",
    bg = Color(0xFFF6F2EF),
    panel = Color(0xFFFFFFFF),
    field = Color(0xFFFBF9F7),
    subtle = Color(0xFFF4F0EC),
    subtleHover = Color(0xFFEEE8E2),
    strip = Color(0xFFE5DCD2),
    border = Color(0xFFDFD7CD),
    borderStrong = Color(0xFF9E9080),
    text = Color(0xFF40311F),
    muted = Color(0xFF756655),
    faint = Color(0xFF837666),
    accent = Color(0xFFA65A10),
    accentStrong = Color(0xFF8F4D0E),
    accentPressed = Color(0xFF7C430C),
    accentContrast = Color(0xFFFFFFFF),
    selected = Color(0xFFF3E0CD),
    selectedHover = Color(0xFFEFD7BF),
    onSelected = Color(0xFF743D07),
    selectedBorder = Color(0xFFA07246),
    focus = Color(0xFF98520F),
    brandInkColor = Color(0xFF40311F),
    accentSoft = Color(0x1FA65A10),
    lv = listOf(Color(0xFFEEE8E2), Color(0xFFEDD3BB), Color(0xFFDEAF82), Color(0xFFC07730), Color(0xFFA65A10)),
)

private val RoseDark = RefDark.copy(
    scheme = "rose",
    bg = Color(0xFF27181D),
    panel = Color(0xFF352027),
    field = Color(0xFF2F1C22),
    subtle = Color(0xFF3F262E),
    subtleHover = Color(0xFF4C313A),
    strip = Color(0xFF593944),
    border = Color(0xFF593A45),
    borderStrong = Color(0xFF8F6B77),
    text = Color(0xFFF2E6EA),
    muted = Color(0xFFC7B1B8),
    faint = Color(0xFFB397A0),
    accent = Color(0xFFF48FB1),
    accentStrong = Color(0xFFF8B4CB),
    accentPressed = Color(0xFFF06594),
    accentContrast = Color(0xFF390818),
    selected = Color(0xFF602439),
    selectedHover = Color(0xFF6D2C42),
    onSelected = Color(0xFFFAB2CA),
    selectedBorder = Color(0xFFAB5471),
    focus = Color(0xFFFBD9E5),
    brandInkColor = Color(0xFFFAF6F7),
    accentSoft = Color(0x21F48FB1),
    lv = listOf(Color(0xFF412A32), Color(0xFF57192E), Color(0xFF77223F), Color(0xFFB83D66), Color(0xFFDE7397)),
)

private val RoseLight = RefLight.copy(
    scheme = "rose",
    bg = Color(0xFFF6EFF1),
    panel = Color(0xFFFFFFFF),
    field = Color(0xFFFBF7F8),
    subtle = Color(0xFFF4ECEE),
    subtleHover = Color(0xFFEEE2E6),
    strip = Color(0xFFE5D2D8),
    border = Color(0xFFDFCDD3),
    borderStrong = Color(0xFF9E808A),
    text = Color(0xFF401F2A),
    muted = Color(0xFF755560),
    faint = Color(0xFF836670),
    accent = Color(0xFFB83260),
    accentStrong = Color(0xFFA42D56),
    accentPressed = Color(0xFF94284D),
    accentContrast = Color(0xFFFFFFFF),
    selected = Color(0xFFF3CDDA),
    selectedHover = Color(0xFFEFBFCF),
    onSelected = Color(0xFF74072D),
    selectedBorder = Color(0xFFA04665),
    focus = Color(0xFFAC2F5A),
    brandInkColor = Color(0xFF401F2A),
    accentSoft = Color(0x1FB83260),
    lv = listOf(Color(0xFFEEE2E6), Color(0xFFEDBBCC), Color(0xFFDE82A2), Color(0xFFC03061), Color(0xFFB83260)),
)

/** 方案 key + 深浅 → 基础 token（还没叠用户调色板）。 */
fun refSchemeBase(scheme: String, dark: Boolean): RefTokens = when (scheme) {
    "graphite" -> if (dark) GraphiteDark else GraphiteLight
    "violet" -> if (dark) VioletDark else VioletLight
    "forest" -> if (dark) ForestDark else ForestLight
    "amber" -> if (dark) AmberDark else AmberLight
    "rose" -> if (dark) RoseDark else RoseLight
    else -> if (dark) RefDark else RefLight
}
