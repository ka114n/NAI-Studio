package com.kallan.naistudio.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * **色相预设**（用户 2026-09-18：「手机按照当前的界面配色，多做几套其他颜色的配色预设，
 * **以当前颜色的饱和度为准**」）。
 *
 * 做法：拿经典画廊那套（深青 / 晴蓝 / 冷中性底）当基准，**只转色相** ——
 * HSV 里 `H` 加一个偏移量，`S`（饱和度）与 `V`（明度）**一位都不改**。
 * 所以每套配色的"浓淡"完全一致，只是换个色系；底色是近中性的（S≈0），转色相几乎看不出，
 * 整体冷暖关系不会散。
 *
 * ⚠️ **四档 error 语义色不转**（红就是红）—— 见 [rotateHue] 的 copy 列表里没有它们。
 */
data class HuePalette(
    val id: String,
    val labelKey: String,
    val hueShift: Float,
    /** 饱和度倍率（默认 1 = 原样不动）。"粉色"这种要**柔一点**的配色会压一点点。 */
    val saturationScale: Float = 1f,
    /** 明度增量（默认 0）。 */
    val valueShift: Float = 0f,
)

/**
 * 色系预设。
 *
 * 前六套**严格只转色相**（S/V 一位不改，用户 2026-09-18：「以当前颜色的饱和度为准」）；
 * 最后那套「粉色」是用户单独点的名（「做个粉色的方案放上去」）——
 * 只转色相会得到玫红/洋红，读起来不像粉 ✗，所以给它一点柔化（S×0.88、V+0.03）。
 */
val NaiHuePalettes = listOf(
    HuePalette("hue_blue", "settings.paletteHueBlue", 40f),
    HuePalette("hue_indigo", "settings.paletteHueIndigo", 78f),
    HuePalette("hue_violet", "settings.paletteHueViolet", 115f),
    HuePalette("hue_magenta", "settings.paletteHueMagenta", 155f),
    HuePalette("hue_pink", "settings.paletteHuePink", 140f, saturationScale = 0.88f, valueShift = 0.03f),
    HuePalette("hue_green", "settings.paletteHueGreen", -48f),
    HuePalette("hue_amber", "settings.paletteHueAmber", -140f),
)

fun huePaletteOf(id: String): HuePalette? = NaiHuePalettes.firstOrNull { it.id == id }

/** 配色 id → i18n 名（界面上的副标题、下拉项都用它）。 */
fun paletteLabelKey(id: String): String = when (id) {
    "bluewhite" -> "settings.paletteBluewhite"
    else -> huePaletteOf(id)?.labelKey ?: "settings.paletteClassic"
}

/**
 * 把一个颜色**只转色相**：S / V / alpha 原样保留。
 * 纯函数（不依赖平台），单测见 `PaletteTest`。
 */
fun rotateHue(
    color: Color,
    degrees: Float,
    saturationScale: Float = 1f,
    valueShift: Float = 0f,
): Color {
    if (degrees == 0f && saturationScale == 1f && valueShift == 0f) return color
    val hsv = hsvOf(color)
    val h = ((hsv.first + degrees) % 360f + 360f) % 360f
    val s = (hsv.second * saturationScale).coerceIn(0f, 1f)
    val v = (hsv.third + valueShift).coerceIn(0f, 1f)
    return hsvColor(h, s, v, color.alpha)
}

/** 颜色的 HSV（H 0..360，S 0..1，V 0..1）—— 测试与 [rotateHue] 共用。 */
internal fun hsvOf(color: Color): Triple<Float, Float, Float> {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min
    val v = max
    val s = if (max <= 0f) 0f else d / max
    var h = when {
        d <= 0f -> 0f
        max == r -> 60f * (((g - b) / d) % 6f)
        max == g -> 60f * (((b - r) / d) + 2f)
        else -> 60f * (((r - g) / d) + 4f)
    }
    if (h < 0f) h += 360f
    return Triple(h, s, v)
}

/** HSV → Color（[hsvOf] 的逆运算）。 */
internal fun hsvColor(h: Float, s: Float, v: Float, alpha: Float = 1f): Color {
    val hh = ((h % 360f) + 360f) % 360f
    val c = v * s
    val x = c * (1f - abs((hh / 60f) % 2f - 1f))
    val m = v - c
    val (r, g, b) = when {
        hh < 60f -> Triple(c, x, 0f)
        hh < 120f -> Triple(x, c, 0f)
        hh < 180f -> Triple(0f, c, x)
        hh < 240f -> Triple(0f, x, c)
        hh < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(red = r + m, green = g + m, blue = b + m, alpha = alpha)
}

/**
 * 整套 [ColorScheme] 只转色相。
 *
 * ⚠️ 列表里**故意没有** `error` / `onError` / `errorContainer` / `onErrorContainer` ——
 * 错误色是语义色，红必须还是红；也没动 `scrim`（纯黑，转了也看不出）。
 */
fun rotateHue(
    scheme: ColorScheme,
    degrees: Float,
    saturationScale: Float = 1f,
    valueShift: Float = 0f,
): ColorScheme {
    if (degrees == 0f && saturationScale == 1f && valueShift == 0f) return scheme
    fun c(color: Color) = rotateHue(color, degrees, saturationScale, valueShift)
    return scheme.copy(
        primary = c(scheme.primary),
        onPrimary = c(scheme.onPrimary),
        primaryContainer = c(scheme.primaryContainer),
        onPrimaryContainer = c(scheme.onPrimaryContainer),
        secondary = c(scheme.secondary),
        onSecondary = c(scheme.onSecondary),
        secondaryContainer = c(scheme.secondaryContainer),
        onSecondaryContainer = c(scheme.onSecondaryContainer),
        tertiary = c(scheme.tertiary),
        onTertiary = c(scheme.onTertiary),
        tertiaryContainer = c(scheme.tertiaryContainer),
        onTertiaryContainer = c(scheme.onTertiaryContainer),
        background = c(scheme.background),
        onBackground = c(scheme.onBackground),
        surface = c(scheme.surface),
        onSurface = c(scheme.onSurface),
        surfaceVariant = c(scheme.surfaceVariant),
        onSurfaceVariant = c(scheme.onSurfaceVariant),
        surfaceContainerLowest = c(scheme.surfaceContainerLowest),
        surfaceContainerLow = c(scheme.surfaceContainerLow),
        surfaceContainer = c(scheme.surfaceContainer),
        surfaceContainerHigh = c(scheme.surfaceContainerHigh),
        surfaceContainerHighest = c(scheme.surfaceContainerHighest),
        outline = c(scheme.outline),
        outlineVariant = c(scheme.outlineVariant),
        inverseSurface = c(scheme.inverseSurface),
        inverseOnSurface = c(scheme.inverseOnSurface),
        surfaceTint = c(scheme.surfaceTint),
        inversePrimary = c(scheme.inversePrimary),
    )
}
