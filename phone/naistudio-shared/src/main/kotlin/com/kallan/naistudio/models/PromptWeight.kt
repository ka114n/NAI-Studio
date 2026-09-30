package com.kallan.naistudio.models

import kotlin.math.abs

/**
 * 提示词里一段文字的**加权档位**（用来上色）。
 *
 * 官方文档：<https://docs.novelai.net/en/image/strengthening-weakening>
 */
enum class PromptWeightKind {
    /** 权重正好 1.0 —— 不加不减。 */
    NORMAL,

    /** 被加强：`{}` 或 `1.5::`。 */
    STRONG,

    /** 被削弱：`[]` 或 `0.5::`（`0 < w < 1`）。 */
    WEAK,

    /** **负数**加权：`-1::hat ::`。只对 V4.5+ 有效，但高亮照显（看得见才好调）。 */
    NEGATIVE,
}

/**
 * 一段**连续同权重**的文字。
 *
 * 只记权重，档位由 [kind] 现算 —— 这样"多深算强"这件事只有一处定义（[PromptWeight.kindOf]）。
 */
data class PromptWeightSpan(
    val start: Int,
    val end: Int,
    val weight: Double,
) {
    val kind: PromptWeightKind get() = PromptWeight.kindOf(weight)
}

/**
 * NovelAI 提示词的**加强 / 削弱**解析。
 *
 * ## 规则（照抄官方文档，别凭直觉改）
 *
 * 官方那句最容易看错的话是：
 *
 * > Technically, every `{` or `]` acts as a ×1.05 focus multiplier to everything
 * > that's to their right, and every `}` or `[` acts as a ÷1.05 focus divider to
 * > everything that's to their right.
 *
 * 也就是说**括号不是"配对"语义**，而是**从左往右的运行累积**：
 *
 * | 字符 | 作用 |
 * |---|---|
 * | `{` | ×1.05 |
 * | `]` | ×1.05 ← 注意：**右方括号也是乘**，不是减 |
 * | `}` | ÷1.05 |
 * | `[` | ÷1.05 ← 注意：**左方括号是除** |
 *
 * `{{` = 1.1025；`{{{chibi}}}` 里 `chibi` 是 1.157625。写成"配对括号递归"的实现
 * 在 `{{{a}} b}` 这种不平衡输入上会和官方对不上，所以这里老老实实按累积做。
 *
 * ## 数值加权
 *
 * `1.5::rain, night ::` —— 数字紧贴在 `::` 左边就是**区间开始**，光秃秃的 `::` 是**区间结束**；
 * 负数（`-1::hat ::`）同样支持。官方还说明了一句：
 *
 * > `::` also serves to close any open brackets
 *
 * 所以 `::`（不管是开始还是结束）都会把括号累积**清零**，不必去数括号配平没有。
 */
object PromptWeight {

    /** 官方步进：每层 `{` / `]` ×1.05，每层 `}` / `[` ÷1.05。 */
    const val STEP = 1.05

    /** 浮点比较容差。1.05 累乘几层误差很小，1e-9 足够。 */
    private const val EPS = 1e-9

    /** 权重 → 档位。**只有这一处定义"多深算强"**。 */
    fun kindOf(weight: Double): PromptWeightKind = when {
        weight < -EPS -> PromptWeightKind.NEGATIVE
        weight > 1.0 + EPS -> PromptWeightKind.STRONG
        weight < 1.0 - EPS -> PromptWeightKind.WEAK
        else -> PromptWeightKind.NORMAL
    }

    /**
     * 把整串提示词切成若干段。
     *
     * 返回的区间**首尾相接、完整覆盖** `0 until text.length`（空串返回空表），
     * 所以调用方可以直接按它上色，不用自己补空隙。
     */
    fun scan(text: String): List<PromptWeightSpan> {
        if (text.isEmpty()) return emptyList()
        val spans = ArrayList<PromptWeightSpan>()

        /** 括号累积出来的权重。 */
        var level = 1.0

        /** 数值区间生效时的权重；`null` = 不在数值区间里。 */
        var numeric: Double? = null

        /** 当前这一段从哪开始。 */
        var segStart = 0

        fun weightNow(): Double = numeric ?: level

        /** 把 [segStart, endExclusive) 这段按**当前**权重收尾。 */
        fun flush(endExclusive: Int) {
            if (endExclusive <= segStart) return
            spans += PromptWeightSpan(segStart, endExclusive, weightNow())
        }

        var i = 0
        while (i < text.length) {
            val c = text[i]

            // ---- 数值加权：`1.5::` 开、`::` 关 ----
            if (c == ':' && i + 1 < text.length && text[i + 1] == ':') {
                // 往前抠出紧贴着的那个数字（允许小数点与负号）
                var s = i
                while (s > 0 && (text[s - 1].isDigit() || text[s - 1] == '.' || text[s - 1] == '-')) s--
                val value = text.substring(s, i).toDoubleOrNull()
                // 数字必须**自己是一个 token**：前面是开头 / 空白 / 逗号。
                // 否则 `1girl1.5::` 这种会被误读成"1.5 的数值区间"。
                val standalone = s == 0 ||
                    text[s - 1].isWhitespace() ||
                    text[s - 1] == ','

                if (value != null && standalone) {
                    // 区间开始。数字和 `::` 本身**一起**归到新区间里（看起来才像一个整体），
                    // 所以这里从数字的开头切段，而不是从冒号切。
                    flush(s)
                    segStart = s
                    numeric = value
                    // 官方：`::` 会顺手把还开着的括号关掉
                    level = 1.0
                } else {
                    // 光秃秃的 `::` = 区间结束，同时把括号清零
                    flush(i)
                    segStart = i
                    numeric = null
                    level = 1.0
                }
                i += 2
                continue
            }

            // ---- 括号累积 ----
            val factor = when (c) {
                '{', ']' -> STEP
                '}', '[' -> 1.0 / STEP
                else -> {
                    i++
                    continue
                }
            }
            // 括号字符本身归到**操作之后**的那一段：`{{{chibi` 是一整条渐深的颜色，
            // 而不是让三个 `{` 各自孤立地留在上一档。
            flush(i)
            level *= factor
            segStart = i
            i++
        }
        flush(text.length)
        return merged(spans)
    }

    /** 相邻同权重的段并起来，少生成几个 `SpanStyle`。 */
    private fun merged(spans: List<PromptWeightSpan>): List<PromptWeightSpan> {
        if (spans.size < 2) return spans
        val out = ArrayList<PromptWeightSpan>(spans.size)
        for (span in spans) {
            val last = out.lastOrNull()
            if (last != null && last.end == span.start && abs(last.weight - span.weight) < EPS) {
                out[out.size - 1] = last.copy(end = span.end)
            } else {
                out += span
            }
        }
        return out
    }
}
