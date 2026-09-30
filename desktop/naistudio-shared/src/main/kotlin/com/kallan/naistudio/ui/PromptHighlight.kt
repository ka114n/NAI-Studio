package com.kallan.naistudio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.kallan.naistudio.models.PromptWeight
import com.kallan.naistudio.models.PromptWeightKind

/**
 * 提示词框里的**加权高亮**：`{}` 加强暖色、`[]` 削弱冷色、`-1::x ::` 负加权用错误色。
 * 规则本身在 [PromptWeight]，这里只负责把段位翻译成颜色。
 *
 * ## 为什么用 `VisualTransformation` 而不是"底下铺一层彩色 Text"
 *
 * 最容易想到的做法是放两个重叠的控件：底层一个带颜色的 `Text`，上层一个透明的
 * `BasicTextField`。但那样要**手动对齐**换行点、滚动位置、字号行距、内边距 ——
 * 任何一处对不上就是"颜色和字错位"，而且长提示词滚动时错位会更明显。
 *
 * `VisualTransformation` 是把"要显示的文字"整个交给输入框自己去画：
 * 换行、滚动、光标、选择区全由它算，我们只往字符上贴样式。**没有第二份文本要对齐**，
 * 这是最省事也最快的路子。而且配 [OffsetMapping.Identity]（长度不变）之后，
 * 光标位置和选区偏移都不用换算，天然精确。
 */
class PromptHighlightTransformation(
    private val colors: PromptHighlightColors,
) : VisualTransformation {

    /**
     * 上次的输入与结果。
     *
     * `filter` 每次测量 / 重绘都会被叫一次，而扫一遍整串是 O(n)（还要建 AnnotatedString）。
     * 打字时相邻两次调用之间文本通常是变的，但**滚动、聚焦、窗口尺寸变化**这些
     * 都会拿同一串再来一次 —— 那几次直接回缓存。宽字符长提示词下这个差别很明显。
     *
     * 只在 UI 线程调用，不用加锁。
     */
    private var cachedInput: String? = null
    private var cachedOutput: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val cached = cachedOutput
        if (cached != null && cachedInput == raw) return cached

        val spans = PromptWeight.scan(raw)
        val annotated = buildAnnotatedString {
            // ⚠️ 一定要 `append(text)`（AnnotatedString 重载）而不是 `append(raw)`。
            // 前者会把框架塞进来的 spanStyles / 注释**原样带过去** ——
            // 其中就包括中文输入法"正在拼字"那一段。换成纯字符串之后，
            // 用拼音打中文时候选词的下划线会消失。
            append(text)
            for (span in spans) {
                val style = when (span.kind) {
                    // 原样：不贴样式，用输入框自己的文字颜色
                    PromptWeightKind.NORMAL -> null
                    PromptWeightKind.STRONG -> SpanStyle(
                        color = colors.strong,
                        fontWeight = FontWeight.SemiBold,
                    )
                    PromptWeightKind.WEAK -> SpanStyle(color = colors.weak)
                    PromptWeightKind.NEGATIVE -> SpanStyle(
                        color = colors.negative,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (style != null) addStyle(style, span.start, span.end)
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity).also {
            cachedInput = raw
            cachedOutput = it
        }
    }
}

/**
 * 取一个属于**当前这个输入框**的高亮转换器。
 *
 * ⚠️ 必须在每个输入框里各自调一次：转换器内部带缓存，两个框共用一个实例的话，
 * 缓存会在两段文本之间来回失效 —— 比不加缓存还慢。
 */
@Composable
fun rememberPromptHighlight(): VisualTransformation {
    val colors = LocalPromptHighlight.current
    return remember(colors) { PromptHighlightTransformation(colors) }
}
