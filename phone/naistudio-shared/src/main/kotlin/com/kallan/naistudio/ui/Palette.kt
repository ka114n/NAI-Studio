package com.kallan.naistudio.ui

import androidx.compose.ui.graphics.Color
import com.kallan.naistudio.models.AppSettings

/**
 * **自定义调色板**（Reference 皮肤，1.1.121 起；口径同手机网页原型）。
 *
 * · 可调 4 个基色：**主色 / 底色 / 卡片色 / 文字色**；
 * · **深色 / 浅色各存一套** —— 在哪个模式下就调哪一套，「全部恢复」也只清当前这一套；
 * · 其余色槽按 color-mix 公式自动派生（见 `ReferenceSkin.kt` 的 [refTokens]）。
 *   四项都没改时一个色都不动，就是 Reference 原样。
 */
data class PaletteOverrides(
    val primary: Color? = null,
    val background: Color? = null,
    /** 卡片 / 面板 / 抽屉底。 */
    val panel: Color? = null,
    /** 文字色：正文与标题共用，次要文字由它派生。 */
    val text: Color? = null,
) {
    val isEmpty: Boolean
        get() = primary == null && background == null && panel == null && text == null

    companion object {
        val None = PaletteOverrides()
    }
}

/** `"#RRGGBB"` / `"#AARRGGBB"` → Color；空串或解析失败返回 null（= 没设这一项）。 */
fun parseHexColor(hex: String?): Color? {
    val raw = hex?.trim()?.removePrefix("#") ?: return null
    if (raw.isEmpty()) return null
    val value = raw.toLongOrNull(16) ?: return null
    return when (raw.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> null
    }
}

/** Color → `"#RRGGBB"`（落设置/备份用；统一去掉透明度）。 */
fun Color.toHexString(): String =
    "#%02X%02X%02X".format(
        (red * 255f + 0.5f).toInt().coerceIn(0, 255),
        (green * 255f + 0.5f).toInt().coerceIn(0, 255),
        (blue * 255f + 0.5f).toInt().coerceIn(0, 255),
    )

/** 这一档颜色上该用深字还是浅字（按 WCAG 对比度挑黑/白里更高的那个）。 */
fun onColorFor(background: Color): Color = if (contrastRatio(background, Color.White) >=
    contrastRatio(background, Color.Black)
) {
    Color.White
} else {
    Color(0xFF0B1114)
}

/** WCAG 对比度（1..21）。取色器里的「对比度 x : 1」读数靠它。 */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

/** WCAG 相对亮度（0..1）。 */
private fun Color.relativeLuminance(): Float {
    fun lin(v: Float): Float = if (v <= 0.03928f) {
        v / 12.92f
    } else {
        Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
    return 0.2126f * lin(red) + 0.7152f * lin(green) + 0.0722f * lin(blue)
}

/** 设置里当前模式那一套 → 覆盖项（空/非法一律当"没设"）。 */
fun paletteOverridesOf(settings: AppSettings, dark: Boolean): PaletteOverrides = PaletteOverrides(
    primary = parseHexColor(paletteHexOf(settings, dark, PaletteSlot.PRIMARY)),
    background = parseHexColor(paletteHexOf(settings, dark, PaletteSlot.BACKGROUND)),
    panel = parseHexColor(paletteHexOf(settings, dark, PaletteSlot.PANEL)),
    text = parseHexColor(paletteHexOf(settings, dark, PaletteSlot.TEXT)),
)

/** 读某个基色在当前模式下的自定义值（空串 = 跟随主题）。 */
fun paletteHexOf(settings: AppSettings, dark: Boolean, slot: PaletteSlot): String = when (slot) {
    PaletteSlot.PRIMARY -> if (dark) settings.colorPrimaryDark else settings.colorPrimaryLight
    PaletteSlot.BACKGROUND -> if (dark) settings.colorBackgroundDark else settings.colorBackgroundLight
    PaletteSlot.PANEL -> if (dark) settings.colorPanelDark else settings.colorPanelLight
    PaletteSlot.TEXT -> if (dark) settings.colorTextDark else settings.colorTextLight
}

/** 写某个基色（**只动当前模式那一套**）；`hex` 传空串 = 恢复跟随主题。 */
fun withPaletteSlot(
    settings: AppSettings,
    dark: Boolean,
    slot: PaletteSlot,
    hex: String,
): AppSettings = when (slot) {
    PaletteSlot.PRIMARY ->
        if (dark) settings.copy(colorPrimaryDark = hex) else settings.copy(colorPrimaryLight = hex)
    PaletteSlot.BACKGROUND ->
        if (dark) settings.copy(colorBackgroundDark = hex) else settings.copy(colorBackgroundLight = hex)
    PaletteSlot.PANEL ->
        if (dark) settings.copy(colorPanelDark = hex) else settings.copy(colorPanelLight = hex)
    PaletteSlot.TEXT ->
        if (dark) settings.copy(colorTextDark = hex) else settings.copy(colorTextLight = hex)
}

/** 当前模式的 4 个基色全恢复跟随主题（另一个模式那套不动）。 */
fun clearAllPaletteSlots(settings: AppSettings, dark: Boolean): AppSettings =
    PaletteSlot.entries.fold(settings) { acc, slot -> withPaletteSlot(acc, dark, slot, "") }

/** 当前模式自定义了几项（设置页副标题「暗色已自定义 N 项」、「全部恢复」是否可点）。 */
fun paletteOverrideCount(settings: AppSettings, dark: Boolean): Int =
    PaletteSlot.entries.count { parseHexColor(paletteHexOf(settings, dark, it)) != null }

/** 设置页的 4 个基色（顺序同网页原型：主色 / 底色 / 卡片色 / 文字色）。 */
enum class PaletteSlot(val key: String) {
    PRIMARY("primary"),
    BACKGROUND("background"),
    PANEL("panel"),
    TEXT("text"),
    ;

    /** 这一档在 Reference 里当前生效的颜色。 */
    fun of(t: RefTokens): Color = when (this) {
        PRIMARY -> t.accent
        BACKGROUND -> t.bg
        PANEL -> t.panel
        TEXT -> t.text
    }

    companion object {
        fun orNull(key: String?): PaletteSlot? = entries.firstOrNull { it.key == key }
    }
}
