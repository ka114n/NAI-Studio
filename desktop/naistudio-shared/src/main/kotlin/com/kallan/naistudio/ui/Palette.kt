package com.kallan.naistudio.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.kallan.naistudio.models.AppSettings

/**
 * **自定义调色**：用户在设置里覆盖四档颜色，其余色槽**自动派生**。
 *
 * ## 用户定的口径（2026-09-16）
 *
 * · 可调四处：**主色**（生成按钮那一类）、**胶囊浅色**（`primaryContainer`）、
 *   **页面底色**、**文字色**（正文与标题同一个色，次要文字自动派生）；
 * · **深色 / 浅色各存一套** —— 在哪个模式下就调哪一套；
 * · 卡片 / 抽屉 / 面板底**跟着底色自动派生**，免得出现"底变了、卡片还是老配色"的断层。
 *
 * ## 为什么派生而不是把 22 个槽都做成可调
 *
 * 让用户逐个调 22 个槽，实际只会调出"看不清"的配色。这里只让用户定**四个意图明确的点**，
 * 剩下的按相对关系算出来：往"反差方向"走的档（卡片/描边）用底色的明暗梯子，
 * 往"贴合方向"走的档（更干净的底）反向微调。默认（四项都没覆盖）时**一个色都不动**。
 */
data class PaletteOverrides(
    val primary: Color? = null,
    /** 胶囊浅色（角色图鉴等按钮那层底）。 */
    val primaryContainer: Color? = null,
    /** 页面底色。 */
    val background: Color? = null,
    /** 文字色：正文与标题共用，次要文字由它派生。 */
    val text: Color? = null,
) {
    val isEmpty: Boolean
        get() = primary == null && primaryContainer == null && background == null && text == null

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

/** Color → `"#RRGGBB"`（落设置/备份用；统一去掉透明度，这几档都不需要透明）。 */
fun Color.toHexString(): String =
    "#%02X%02X%02X".format(
        (red * 255f + 0.5f).toInt().coerceIn(0, 255),
        (green * 255f + 0.5f).toInt().coerceIn(0, 255),
        (blue * 255f + 0.5f).toInt().coerceIn(0, 255),
    )

/**
 * 这一档颜色上该用深字还是浅字（自动取反）。
 *
 * 用户把主色改成白色时，按钮上原来那层白字会直接看不见 —— 所以按钮文字不能跟着
 * 主色走，得按**对比度**自己选。用 WCAG 的相对亮度公式挑黑/白里对比更高的那个。
 */
fun onColorFor(background: Color): Color = if (contrastRatio(background, Color.White) >=
    contrastRatio(background, Color.Black)
) {
    Color.White
} else {
    Color(0xFF0B1114)
}

/** WCAG 对比度（1..21）。设置页用它给"文字色和底色太接近"提个醒。 */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    val hi = maxOf(la, lb)
    val lo = minOf(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

/** WCAG 相对亮度（0..1）。黑/白取反、对比度提示都靠它。 */
private fun Color.relativeLuminance(): Float {
    fun lin(v: Float): Float = if (v <= 0.03928f) {
        v / 12.92f
    } else {
        Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
    return 0.2126f * lin(red) + 0.7152f * lin(green) + 0.0722f * lin(blue)
}

/** 底色偏亮还是偏暗（决定"卡片该往哪边走"）。 */
private fun Color.isLightBackground(): Boolean = relativeLuminance() > 0.35f

/** 底色 × 反差色 的梯子：`t` 越大越靠反差色（深底 → 更亮；浅底 → 更暗）。 */
private fun Color.step(toward: Color, t: Float): Color = lerp(this, toward, t.coerceIn(0f, 1f))

/**
 * 把用户覆盖的四档套到基础配色上，并**派生**其余色槽。
 *
 * 四项都没改时原样返回（连一个色都不动）—— 默认观感必须和以前**完全一致**。
 */
fun applyPaletteOverrides(base: ColorScheme, overrides: PaletteOverrides): ColorScheme {
    if (overrides.isEmpty) return base

    val background = overrides.background ?: base.background
    val onBackground = overrides.text ?: base.onBackground
    val primary = overrides.primary ?: base.primary
    val container = overrides.primaryContainer ?: base.primaryContainer

    // 底色的明暗决定派生方向：深底往亮走、浅底往暗走
    val towardContrast = if (background.isLightBackground()) Color.Black else Color.White
    val towardFlat = if (background.isLightBackground()) Color.White else Color.Black

    val derived = base.copy(
        primary = primary,
        onPrimary = if (overrides.primary != null) onColorFor(primary) else base.onPrimary,
        primaryContainer = container,
        onPrimaryContainer = if (overrides.primaryContainer != null) {
            onColorFor(container)
        } else {
            base.onPrimaryContainer
        },

        // 次要色跟着主色走：否则设了主色之后，选中的抽屉项 / 筛选胶囊还是原来的青绿，
        // 一眼看出是两套东西。用户没改主色时保持原样。
        secondary = if (overrides.primary != null) primary else base.secondary,
        onSecondary = if (overrides.primary != null) onColorFor(primary) else base.onSecondary,
        secondaryContainer = if (overrides.primaryContainer != null) {
            container
        } else {
            base.secondaryContainer
        },
        onSecondaryContainer = if (overrides.primaryContainer != null) {
            onColorFor(container)
        } else {
            base.onSecondaryContainer
        },

        background = background,
        onBackground = onBackground,

        // ---- 卡片 / 面板 / 抽屉底：按底色的明暗梯子派生 ----
        surface = background.step(towardContrast, 0.02f),
        onSurface = onBackground,
        surfaceVariant = background.step(towardContrast, 0.07f),
        // 次要文字：文字色往底色方向混，保证"比正文淡但还看得清"
        onSurfaceVariant = if (overrides.text != null) {
            lerp(onBackground, background, 0.38f)
        } else {
            base.onSurfaceVariant
        },
        surfaceContainerLowest = background.step(towardFlat, 0.04f),
        surfaceContainerLow = background.step(towardFlat, 0.02f),
        surfaceContainer = background.step(towardContrast, 0.04f),
        surfaceContainerHigh = background.step(towardContrast, 0.07f),
        surfaceContainerHighest = background.step(towardContrast, 0.10f),
        outline = background.step(towardContrast, 0.28f),
        outlineVariant = background.step(towardContrast, 0.12f),
    )
    return derived
}

/** 设置里那四个十六进制串 → 覆盖项（空/非法一律当"没设"）。 */
fun paletteOverridesOf(
    primaryHex: String,
    containerHex: String,
    backgroundHex: String,
    textHex: String,
): PaletteOverrides = PaletteOverrides(
    primary = parseHexColor(primaryHex),
    primaryContainer = parseHexColor(containerHex),
    background = parseHexColor(backgroundHex),
    text = parseHexColor(textHex),
)

/** 读某个色槽在当前模式下有没有自定义值（设置页显示"已改/跟随主题"）。 */
fun paletteHexOf(settings: AppSettings, dark: Boolean, slot: PaletteSlot): String = when (slot) {
    PaletteSlot.PRIMARY -> if (dark) settings.colorPrimaryDark else settings.colorPrimaryLight
    PaletteSlot.CONTAINER -> if (dark) settings.colorContainerDark else settings.colorContainerLight
    PaletteSlot.BACKGROUND -> if (dark) settings.colorBackgroundDark else settings.colorBackgroundLight
    PaletteSlot.TEXT -> if (dark) settings.colorTextDark else settings.colorTextLight
    // 标题栏只有一份值（不分深浅 ✓）
    PaletteSlot.TITLE_BAR -> settings.windowTitleBarColor
    // 画布底色同样只有一份 ✓
    PaletteSlot.CANVAS -> settings.canvasColor
    // 窗口背景色同理 ✓
    PaletteSlot.WINDOW -> settings.windowColor
}

/**
 * 写某个色槽（**只动当前模式那一套**）。
 *
 * `hex` 传空串 = 这一档恢复"跟随主题"。别的模式、别的槽一个字节都不碰 ——
 * 之前那种"先全部清空再挑回来"的写法会在切换模式时把另一套悄悄抹掉。
 */
fun withPaletteSlot(
    settings: AppSettings,
    dark: Boolean,
    slot: PaletteSlot,
    hex: String,
): AppSettings = when (slot) {
    PaletteSlot.PRIMARY -> if (dark) {
        settings.copy(colorPrimaryDark = hex)
    } else {
        settings.copy(colorPrimaryLight = hex)
    }

    PaletteSlot.CONTAINER -> if (dark) {
        settings.copy(colorContainerDark = hex)
    } else {
        settings.copy(colorContainerLight = hex)
    }

    PaletteSlot.BACKGROUND -> if (dark) {
        settings.copy(colorBackgroundDark = hex)
    } else {
        settings.copy(colorBackgroundLight = hex)
    }

    PaletteSlot.TEXT -> if (dark) {
        settings.copy(colorTextDark = hex)
    } else {
        settings.copy(colorTextLight = hex)
    }

    // 标题栏不分深浅：一份值、两套模式共用 ✓
    PaletteSlot.TITLE_BAR -> settings.copy(windowTitleBarColor = hex)
    // 画布底色同理 ✓
    PaletteSlot.CANVAS -> settings.copy(canvasColor = hex)
    // 窗口背景色同理 ✓
    PaletteSlot.WINDOW -> settings.copy(windowColor = hex)
}

/** 全部恢复跟随主题（两套一起清 + 标题栏 / 画布 / 窗口那三档 ✓）。 */
fun clearAllPaletteSlots(settings: AppSettings): AppSettings = settings.copy(
    colorPrimaryLight = "",
    colorPrimaryDark = "",
    colorContainerLight = "",
    colorContainerDark = "",
    colorBackgroundLight = "",
    colorBackgroundDark = "",
    colorTextLight = "",
    colorTextDark = "",
    windowTitleBarColor = "",
    canvasColor = "",
    windowColor = "",
)

/** 有没有任何一档被自定义过（设置页决定要不要显示「全部恢复」）。 */
fun hasAnyPaletteOverride(settings: AppSettings): Boolean =
    listOf(
        settings.colorPrimaryLight, settings.colorPrimaryDark,
        settings.colorContainerLight, settings.colorContainerDark,
        settings.colorBackgroundLight, settings.colorBackgroundDark,
        settings.colorTextLight, settings.colorTextDark,
        settings.windowTitleBarColor,
        settings.canvasColor,
        settings.windowColor,
    ).any { it.isNotEmpty() }

/** 设置页要显示的一档。 */
enum class PaletteSlot(val key: String) {
    PRIMARY("primary"),
    CONTAINER("container"),
    BACKGROUND("background"),
    TEXT("text"),

    /**
     * **窗口标题栏**（用户 2026-09-26：「这个放到界面里的调色板里」）——
     * 和上面四档并排显示、用同一个取色弹窗 ✓。
     * ⚠️ 它**没有深浅两套**（只有 `windowTitleBarColor` 一个字段 ✓）：两套模式下同一个值，
     * 空串 = 跟随面板底色（= 视觉上没有独立标题栏 ✓）。
     */
    TITLE_BAR("titleBar"),

    /**
     * **画布底色**（用户 2026-09-26：「调色板添加可改画布颜色」）——
     * 就是生成页那块画布的底（图片背后那一圈 + 空态整块 ✓）。同样不分深浅、只有一份 ✓，
     * 空串 = 跟随主题的 `surfaceContainer` ✓。
     */
    CANVAS("canvas"),

    /**
     * **窗口背景色**（用户 2026-09-26：「右边栏的背景 / 各个窗口的背景色」）——
     * 中控台 / 底栏 / 分离面板 / 工具浮窗 / 画布右边栏那一族的面板底色 ✓。
     * 空串 = 跟随主题 `surface` ✓；同样只有一份值 ✓。
     */
    WINDOW("window"),
    ;

    companion object {
        fun orNull(key: String?): PaletteSlot? = entries.firstOrNull { it.key == key }
    }
}
