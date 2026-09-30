package com.kallan.naistudio.ui

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import com.kallan.naistudio.platform.KeyValueStore

/**
 * **可改的快捷键**（用户 2026-09-19：「在帮助里添加一个快捷键选项，可以改变各个功能的快捷键」）。
 *
 * ## 为什么要有这一层
 *
 * 原来键位是**写死**在两处的：入口 `Window(onKeyEvent)` 里一个 `when (event.key)`、
 * 菜单项上硬编码的 `shortcut = "Ctrl+O"`。要能改，就得有一份**唯一的键位表**：
 *  - 入口用它把按键翻成动作 id（[actionFor]）；
 *  - 菜单用它显示当前的键（[keysFor]）；
 *  - 设置对话框用它列条目、改绑（[setKeys] / [reset] / [resetAll]）。
 *
 * ## 存哪儿
 *
 * 覆盖项存 `platform.kv` 的 `shortcuts.overrides`（JSON：`{"importImage":"Ctrl+Shift+O"}`）——
 * **只存"和默认不一样"的那些**，于是以后默认值改了，没动过的条目会跟着变 ✓。
 *
 * ## 键串格式
 *
 * `Ctrl+Shift+O` 这样：修饰键 `Ctrl` / `Shift` / `Alt`（顺序不限），最后一段是主键
 *（单个字母 / 数字 / `,` `.` `/` `[` `]`）。解析与格式化都在本文件里（[parseKeys] / [formatEvent]）。
 */
data class AppShortcut(
    /** 动作 id：和 `ShellCommands.dispatch(id)` 用的是同一套（`quit` 例外，入口自己处理）。 */
    val id: String,
    /** i18n 键：显示在设置对话框里。 */
    val labelKey: String,
    /** 默认键串。 */
    val default: String,
)

/** 全局可改快捷键；画布编辑器工具键由编辑器单独处理。 */
val APP_SHORTCUTS: List<AppShortcut> = listOf(
    // 用户 2026-09-19：「快捷键加个执行，ctrl+↩︎（执行一次生图）」
    AppShortcut("generate", "shortcuts.generate", "Ctrl+Enter"),
    AppShortcut("importImage", "menu.importImage", "Ctrl+O"),
    AppShortcut("exportCurrentImage", "menu.exportCurrentImage", "Ctrl+Alt+Shift+W"),
    AppShortcut("exportBackup", "menu.exportBackup", "Ctrl+S"),
    AppShortcut("importBackup", "menu.importBackup", "Ctrl+Alt+O"),
    AppShortcut("presets", "menu.presets", "Ctrl+P"),
    AppShortcut("darkMode", "menu.darkMode", "Shift+F1"),
    AppShortcut("sidebar", "menu.collapseSidebar", "Tab"),
    AppShortcut("cancelGeneration", "common.cancel", "Esc"),
    AppShortcut("upscale", "upscale.button", "Ctrl+Alt+I"),
    AppShortcut("director", "director.title", "Ctrl+Alt+F"),
    AppShortcut("maskToggle", "mask.toggle", "Q"),
    AppShortcut("settings", "settings.title", "Ctrl+K"),
    AppShortcut("shortcutsDialog", "menu.shortcuts", "Ctrl+Alt+Shift+K"),
    AppShortcut("resetPlacement", "menu.resetImagePlacement", "Ctrl+1"),
    AppShortcut("fitCanvas", "shortcuts.fitCanvas", "Ctrl+0"),
    AppShortcut("zoomIn", "shortcuts.zoomIn", "Ctrl+="),
    AppShortcut("zoomOut", "shortcuts.zoomOut", "Ctrl+-"),
    AppShortcut("toggleInfinite", "canvasMode.infinite", "Ctrl+Alt+N"),
    // ⛔ `detachConsole` / `detachBottomBar`（2026-09-26 加的 ✓）**撤了** ✗ ——
    //    用户 2026-09-24：「窗口的分离功能去掉，以后不需要分离了，中控底栏变成固定的」✓。
    //    这两个动作在 `ShellCommands.dispatch` 里也已经没有分支了 ✓（留着的话设置对话框里
    //    会挂两条"按了没反应"的键位 ✗ —— 那正是本仓库最忌讳的假控件 ✗）。
    AppShortcut("canvasEditor", "menu.canvasEditor", "Ctrl+E"),
    AppShortcut("quit", "menu.quit", "Ctrl+Q"),
)

private const val KEY_SHORTCUT_OVERRIDES = "shortcuts.overrides"

/** 一次按键的"解析结果"：修饰键 + 主键名。 */
internal data class ParsedKeys(
    val ctrl: Boolean,
    val shift: Boolean,
    val alt: Boolean,
    val key: String,
) {
    fun format(): String = buildString {
        if (ctrl) append("Ctrl+")
        if (shift) append("Shift+")
        if (alt) append("Alt+")
        append(key)
    }
}

/** `"Ctrl+Shift+O"` → [ParsedKeys]；认不出来返回 null。 */
internal fun parseKeys(text: String): ParsedKeys? {
    if (text.isBlank()) return null
    var ctrl = false
    var shift = false
    var alt = false
    var main: String? = null
    for (raw in text.split('+')) {
        val part = raw.trim()
        if (part.isEmpty()) continue
        when (part.lowercase()) {
            "ctrl", "control" -> ctrl = true
            "shift" -> shift = true
            "alt" -> alt = true
            else -> {
                val normalised = normaliseMainKey(part) ?: return null
                if (main != null) return null // 两个主键 → 不合法
                main = normalised
            }
        }
    }
    val key = main ?: return null
    return ParsedKeys(ctrl, shift, alt, key)
}

/** 主键名归一化：字母统一大写、数字/符号原样、常见别名映射。 */
private fun normaliseMainKey(part: String): String? {
    val upper = part.uppercase()
    if (upper.length == 1) {
        val c = upper[0]
        return when {
            c in 'A'..'Z' -> c.toString()
            c in '0'..'9' -> c.toString()
            c in ",./[]-=;'" -> c.toString()
            else -> null
        }
    }
    return when (upper) {
        "COMMA" -> ","
        "PERIOD" -> "."
        "SLASH" -> "/"
        "SPACE" -> "Space"
        "ESC", "ESCAPE" -> "Esc"
        "ENTER", "RETURN" -> "Enter"
        "DELETE", "DEL" -> "Delete"
        "BACKSPACE" -> "Backspace"
        "TAB" -> "Tab"
        else -> if (upper.startsWith("F") && upper.drop(1).toIntOrNull() in 1..12) upper else null
    }
}

/**
 * 主键名 → 它产生的那**一个字符**（`"Q"` → `'q'`、`"Enter"` → `'\n'`）；认不出来返回 `-1`。
 *
 * 只给"认 KEY_TYPED 归谁"用（见 [ShortcutBindings.actionForTyped] / [ShortcutBindings.typedCharFor]）：
 * 统一按**小写**比 —— 大小写那点差异由修饰键（Shift）那一步判，两边分开才不会互相干扰。
 */
private fun typedCharOfMainKey(key: String): Int {
    if (key.length == 1) return key[0].lowercaseChar().code
    return when (key) {
        "Enter" -> '\n'.code
        "Space" -> ' '.code
        "Tab" -> '\t'.code
        "Backspace" -> '\b'.code
        "Delete" -> 0x7F
        "Esc" -> 0x1B
        else -> -1 // F1..F12：没有对应的"打字字符"
    }
}

/**
 * 这个字符是不是主键 [mainKey] 按下时产生的？两种形态：
 *
 *  · **可打印的那个字符**：`Alt+Q` → `'q'`、`Alt+1` → `'1'` —— 输入框会把它插进去 ✗，必须认出来；
 *  · **控制字符**：`Ctrl+O` → U+000F、`Ctrl+Enter` → U+000A（实测数据见
 *    [ShortcutBindings.actionForTyped] 的注释）—— 输入框自己会按 `isISOControl` 挡掉，
 *    但既然命中了键位表，就顺手一起认掉，别剩半截状态没人管。
 *
 * 控制字符那一支只在**字母**上算（`'O'.code and 0x1F` = U+000F），而且它本身不可能被打字打出来，
 * 所以"多吃一个"的风险为零。
 */
private fun matchesTypedChar(mainKey: String, codePoint: Int): Boolean {
    if (typedCharOfMainKey(mainKey) == codePoint) return true
    if (mainKey.length != 1) return false
    val c = mainKey[0].uppercaseChar()
    if (c !in 'A'..'Z') return false
    return (c.code and 0x1F) == codePoint
}

/** 一个 [KeyEvent] → 键串；不是"字母/数字/常见符号"那种主键就返回 null（用于"按下新键"的采集）。 */
internal fun formatEvent(event: KeyEvent): String? {
    // ⚠️ `Key` 是 inline class（`keyCode` 是 Long），**不能用 `Key.A..Key.Z` 这种区间** ——
    // 上一版写成区间直接编译不过。这里按 `keyCode` 的数值区间判，再用偏移量还原字符。
    val kc = event.key.keyCode
    val name = when {
        kc in Key.A.keyCode..Key.Z.keyCode -> ('A' + (kc - Key.A.keyCode).toInt()).toString()
        kc in Key.Zero.keyCode..Key.Nine.keyCode -> ('0' + (kc - Key.Zero.keyCode).toInt()).toString()
        event.key == Key.Comma -> ","
        event.key == Key.Period -> "."
        event.key == Key.Slash -> "/"
        event.key == Key.LeftBracket -> "["
        event.key == Key.RightBracket -> "]"
        event.key == Key.Minus -> "-"
        event.key == Key.Equals -> "="
        event.key == Key.Semicolon -> ";"
        event.key == Key.Apostrophe -> "'"
        event.key == Key.Spacebar -> "Space"
        event.key == Key.Tab -> "Tab"
        event.key == Key.Escape -> "Esc"
        event.key == Key.F1 -> "F1"
        // 回车（用户 2026-09-19 要的 `Ctrl+Enter` = 执行一次生图）
        event.key == Key.Enter || event.key == Key.NumPadEnter -> "Enter"
        else -> null
    } ?: return null
    return ParsedKeys(event.isCtrlPressed, event.isShiftPressed, event.isAltPressed, name).format()
}

/**
 * 键位表本体：默认值 + 覆盖项（落盘）。
 *
 * 覆盖项住在一个 **`SnapshotStateMap`** 里 —— 改了要能立刻让菜单上的快捷键标注跟着变。
 */
class ShortcutBindings(private val kv: KeyValueStore?) {

    private val current = mutableStateMapOf<String, String>().apply { putAll(load()) }

    private fun defaultOf(id: String): String =
        APP_SHORTCUTS.firstOrNull { it.id == id }?.default.orEmpty()

    /** 当前键串（没改过就是默认值）。 */
    fun keysFor(id: String): String = current[id] ?: defaultOf(id)

    /** 改绑；给空串（或等于默认值）= 恢复默认。 */
    fun setKeys(id: String, keys: String) {
        if (keys.isBlank() || keys == defaultOf(id)) current.remove(id) else current[id] = keys
        persist()
    }

    fun reset(id: String) = setKeys(id, "")

    fun resetAll() {
        current.clear()
        persist()
    }

    /** 「改」过的那几个（对话框会标一下）。 */
    fun isCustom(id: String): Boolean = current.containsKey(id)

    /**
     * 这个按键对应哪个动作（没命中返回 null）。
     *
     * ⚠️ 一次只认**一个**动作：**完全匹配**修饰键（Ctrl/Shift/Alt 三者状态都要对得上），
     * 所以 `Ctrl+Shift+O` 不会顺带触发 `Ctrl+O`。
     */
    fun actionFor(event: KeyEvent): String? {
        val pressed = formatEvent(event) ?: return null
        for (spec in APP_SHORTCUTS) {
            if (keysFor(spec.id) == pressed) return spec.id
        }
        return null
    }

    /**
     * **打字事件**（AWT `KEY_TYPED` → Compose `KeyEventType.Unknown`、`key` 是 `Key.Unknown`）
     * 是不是"某条命中键位表的组合"顺带产生的那一个字符？命中就返回动作 id。
     *
     * ## 为什么必须有这一条（2026-09-26 实测，本机 JDK 17 + Compose 1.8.2）
     *
     * `Alt+Q` 这种组合，Windows/Java 会发**三**个 AWT 事件：
     * `KEY_PRESSED`（keyCode=Q、alt=true）→ `KEY_TYPED`（**keyCode=0**、keyChar='q'、alt=true）
     * → `KEY_RELEASED`。第一个 `actionFor` 认得出（→ 派发命令 ✓），但第三个"打字事件"
     * **没有 key**（`toComposeEvent` 把 401/402 之外的 id 全映射成 `KeyEventType.Unknown`，
     * 而 KEY_TYPED 的 keyCode 是 0 → `Key(0)` = `Key.Unknown`），`actionFor` 永远认不出它 ——
     * 入口不在这儿吃掉，它就会落到焦点路径上，被输入框当成"打字"插进提示词
     *（Compose 的 `isTypedEvent` 判的就是"KEY_TYPED 且 keyChar 可打印"，'q' 恰好可打印）。
     * 用户报的「打完提示词用快捷键执行，提示词里却多出一个按键」正是这一条。
     *
     * ⚠️ `Ctrl+字母` 那几条之所以**看不见**这个 bug，只是因为 keyChar 是控制字符
     *（实测 Ctrl+O → U+000F、Ctrl+Enter → U+000A、Ctrl+Shift+O → U+000F），会被
     * `Character.isISOControl` 挡掉；Ctrl+1..5 更是**根本不发** KEY_TYPED。
     * 那是巧合、不是我们吃干净了 —— 所以这里按"修饰键 + 字符"精确认，一并吃掉。
     */
    fun actionForTyped(codePoint: Int, ctrl: Boolean, shift: Boolean, alt: Boolean): String? {
        if (codePoint <= 0) return null
        for (spec in APP_SHORTCUTS) {
            val parsed = parseKeys(keysFor(spec.id)) ?: continue
            // 修饰键完全匹配（和 [actionFor] 一个口径：`Alt+Q` 不吃 `Ctrl+Alt+Q` 的打字事件）
            if (parsed.ctrl != ctrl || parsed.shift != shift || parsed.alt != alt) continue
            if (matchesTypedChar(parsed.key, codePoint)) return spec.id
        }
        return null
    }

    /**
     * 这条绑定按下时会产生的那个字符（`Alt+Q` → `'q'`、`Ctrl+Enter` → `'\n'`）；
     * 没有字符的（`F1..F12` 之类）返回 `-1`。
     *
     * 入口拿它当"打字事件"的第二条判据：那颗命中键位表的键**还按着**、而且这个字符正是它会产生的
     * —— 万一日后某个输入法/布局报上来的修饰键位和绑定时对不上，这条兜底照样能咽掉那个字符。
     */
    fun typedCharFor(id: String): Int {
        val parsed = parseKeys(keysFor(id)) ?: return -1
        return typedCharOfMainKey(parsed.key)
    }

    private fun load(): Map<String, String> = runCatching {
        val raw = kv?.getString(KEY_SHORTCUT_OVERRIDES, null) ?: return@runCatching emptyMap()
        val obj = org.json.JSONObject(raw)
        obj.keys().asSequence()
            .mapNotNull { k -> obj.optString(k).takeIf { it.isNotBlank() && parseKeys(it) != null }?.let { k to it } }
            .toMap()
    }.getOrElse { emptyMap() }

    private fun persist() {
        val store = kv ?: return
        val obj = org.json.JSONObject()
        current.forEach { (k, v) -> obj.put(k, v) }
        store.edit().putString(KEY_SHORTCUT_OVERRIDES, obj.toString()).apply()
    }
}

/**
 * 键位表。⚠️ 默认值是"**没有落盘**的空表"（`kv = null`）而不是 `error(...)` ——
 * 万一哪个入口忘了 provide，界面照样能开（只是改的键位不保存），不会整个 App 起不来。
 */
val LocalShortcutBindings = staticCompositionLocalOf { ShortcutBindings(null) }
