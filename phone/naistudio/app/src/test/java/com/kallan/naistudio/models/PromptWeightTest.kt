package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NovelAI 加强 / 削弱语法的解析。
 *
 * 这套规则**反直觉的地方太多**，所以用例写得比一般单测细 —— 尤其是"右方括号是乘、
 * 左方括号是除"这一条，凭直觉写配对括号递归的实现会一路错下去，而错的表现只是
 * "颜色看着有点怪"，很难被发现。
 *
 * 官方原文：<https://docs.novelai.net/en/image/strengthening-weakening>
 */
class PromptWeightTest {

    /** 取某个偏移处文字的权重，用起来比逐段断言顺手。 */
    private fun weightAt(text: String, offset: Int): Double {
        val span = PromptWeight.scan(text).firstOrNull { offset in it.start until it.end }
        return span?.weight ?: error("偏移 $offset 没有被任何段覆盖（区间必须完整覆盖整串）")
    }

    private fun kindAt(text: String, offset: Int): PromptWeightKind =
        PromptWeight.kindOf(weightAt(text, offset))

    private fun assertCovered(text: String) {
        val spans = PromptWeight.scan(text)
        assertEquals("第一段必须从 0 开始", 0, spans.first().start)
        assertEquals("最后一段必须到串尾", text.length, spans.last().end)
        spans.zipWithNext().forEach { (a, b) ->
            assertEquals("段与段之间不能有缝也不能重叠", a.end, b.start)
        }
    }

    // ------------------------------------------------------------ 括号累积

    @Test
    fun `没有括号时整串都是原样`() {
        val text = "1girl, solo, blue hair"
        val spans = PromptWeight.scan(text)
        assertEquals(1, spans.size)
        assertEquals(PromptWeightKind.NORMAL, spans[0].kind)
        assertEquals(1.0, spans[0].weight, 1e-9)
        assertCovered(text)
    }

    @Test
    fun `空串返回空表`() {
        assertTrue(PromptWeight.scan("").isEmpty())
    }

    @Test
    fun `单层花括号加强到 1 点 05`() {
        val text = "{chibi}"
        // `{` 本身和它右边的一切都乘 —— 所以从下标 0 起就是 1.05
        assertEquals(1.05, weightAt(text, 0), 1e-9)
        assertEquals(1.05, weightAt(text, 3), 1e-9)
        // `}` 是除，它**自己**已经回到 1.0
        assertEquals(1.0, weightAt(text, 6), 1e-9)
        assertCovered(text)
    }

    @Test
    fun `三层花括号约等于 1 点 157625`() {
        val text = "{{{chibi}}}"
        assertEquals(1.157625, weightAt(text, 3), 1e-9)
        assertEquals(PromptWeightKind.STRONG, kindAt(text, 3))
        assertCovered(text)
    }

    @Test
    fun `方括号削弱`() {
        val text = "[[chibi]]"
        assertEquals(1.0 / (1.05 * 1.05), weightAt(text, 2), 1e-9)
        assertEquals(PromptWeightKind.WEAK, kindAt(text, 2))
    }

    @Test
    fun `右方括号是乘左方括号是除 —— 官方那句最容易看错的话`() {
        // 官方："every `{` or `]` acts as a ×1.05 multiplier ... and every `}` or `[`
        // acts as a ÷1.05 divider"。注意是**反的**：`]` 乘、`[` 除。
        // 凭直觉写成"`[` 是削弱、`]` 结束削弱"的实现会在这里挂掉。
        assertEquals(1.05, weightAt("]x", 0), 1e-9)   // `]` = 乘
        assertEquals(1.0 / 1.05, weightAt("[x", 0), 1e-9) // `[` = 除
    }

    @Test
    fun `配对的括号正好互相抵消`() {
        // {chibi} 之后回到 1.0
        val text = "1girl, {chibi}, solo"
        assertEquals(1.0, weightAt(text, 0), 1e-9)
        assertEquals(1.05, weightAt(text, 7), 1e-9)
        assertEquals(1.0, weightAt(text, 14), 1e-9)
        assertCovered(text)
    }

    @Test
    fun `不平衡的括号按累积算而不是按配对算`() {
        // `{{{a}} b}` —— 配对递归的写法会认为 b 在 `{` 里（1.05），
        // 累积口径下 b 前面已经消耗了 3 个乘和 2 个除，是 1.05 没错，
        // 但结尾那个 `}` 会让它变 1.0。这里钉的是"逐字符累积"这个实现方式。
        val text = "{{{a}} b}"
        assertEquals(1.157625, weightAt(text, 3), 1e-9) // a
        assertEquals(1.05, weightAt(text, 7), 1e-9)     // b 前面是 3 乘 2 除
        assertEquals(1.0, weightAt(text, 8), 1e-9)      // 收尾的 `}` 之后
        assertCovered(text)
    }

    // ------------------------------------------------------------ 数值加权

    @Test
    fun `数值区间用指定的权重`() {
        val text = "1girl, 1.5::rain, night ::, black shoes"
        // 区间内的文字
        assertEquals(1.5, weightAt(text, 12), 1e-9)
        assertEquals(1.5, weightAt(text, 20), 1e-9)
        // 区间外
        assertEquals(1.0, weightAt(text, 0), 1e-9)
        assertEquals(1.0, weightAt(text, text.length - 1), 1e-9)
        // 数字和 `::` 本身也算在区间里（看起来才像一个整体）
        assertEquals(1.5, weightAt(text, 7), 1e-9)
        assertCovered(text)
    }

    @Test
    fun `数值区间内的强弱档位由数值决定`() {
        assertEquals(PromptWeightKind.STRONG, kindAt("1.5::x ::", 5))
        assertEquals(PromptWeightKind.WEAK, kindAt("0.5::x ::", 5))
        assertEquals(PromptWeightKind.NORMAL, kindAt("1.0::x ::", 5))
        // 到区间外就该回到原样
        assertEquals(PromptWeightKind.NORMAL, kindAt("1.5::x ::, y", 10))
    }

    @Test
    fun `负数加权单独一档`() {
        // 官方文档里的例子：-1::hat :: 用来"把帽子拿掉"
        val text = "-1::hat ::"
        assertEquals(-1.0, weightAt(text, 5), 1e-9)
        assertEquals(PromptWeightKind.NEGATIVE, kindAt(text, 5))
        assertEquals(PromptWeightKind.NEGATIVE, kindAt("-3::hat ::", 5))
        assertCovered(text)
    }

    @Test
    fun `数值区间结束后回到原样`() {
        val text = "a, 2::b ::, c"
        assertEquals(1.0, weightAt(text, 0), 1e-9)
        assertEquals(2.0, weightAt(text, 4), 1e-9)
        assertEquals(1.0, weightAt(text, text.length - 1), 1e-9)
        assertCovered(text)
    }

    @Test
    fun `数字必须自成一段才算数值加权`() {
        // `1girl1.5::` 里的 1.5 粘在单词屁股上，不当成权重，
        // 这种 `::` 只当作"关掉还开着的括号"
        val text = "1girl1.5::x"
        assertEquals(1.0, weightAt(text, 0), 1e-9)
        assertEquals(1.0, weightAt(text, text.length - 1), 1e-9)
    }

    @Test
    fun `冒号会把还开着的括号清零`() {
        // 官方："`::` also serves to close any open brackets"，
        // 所以 `{{{{{rain ::` 不用去数括号配平没有
        val text = "{{{{{rain ::"
        assertEquals(1.2762815625, weightAt(text, 5), 1e-6) // rain 之前累积了 5 层
        assertEquals(1.0, weightAt(text, text.length - 1), 1e-9) // `::` 之后清零
        assertCovered(text)
    }

    @Test
    fun `数值区间结束后括号也归零`() {
        val text = "{{a, 1.5::b ::, c"
        assertEquals(1.5, weightAt(text, 7), 1e-9)
        // 区间结束后不是回到 1.1025，而是回到 1.0（`::` 顺手把括号关了）
        assertEquals(1.0, weightAt(text, text.length - 1), 1e-9)
        assertCovered(text)
    }

    @Test
    fun `孤立的冒号不影响`() {
        val text = "1girl: solo"
        assertEquals(1, PromptWeight.scan(text).size)
        assertEquals(1.0, weightAt(text, 0), 1e-9)
    }

    @Test
    fun `连续三个冒号不会崩`() {
        // `:::` = 一个 `::` 后面跟一个孤立冒号
        val text = "a, 1.5:::b"
        assertEquals(1.5, weightAt(text, 8), 1e-9)
        assertCovered(text)
    }

    // ------------------------------------------------------------ 区间完整性

    @Test
    fun `复杂组合仍然是首尾相接的完整覆盖`() {
        // 官方文档最后那张图的例子
        val text = "{{1girl, {black hair, chibi,}}} {{catgirl [[under]]}} a {{{{{cherry blossom tree"
        assertCovered(text)
    }

    @Test
    fun `中文和换行也能正常切段`() {
        val text = "雨夜，{少女回头}\n[[少年走了]]"
        assertCovered(text)
        assertEquals(PromptWeightKind.STRONG, kindAt(text, 4))
        assertEquals(PromptWeightKind.WEAK, kindAt(text, 12))
    }

    @Test
    fun `档位判定的边界`() {
        assertEquals(PromptWeightKind.NORMAL, PromptWeight.kindOf(1.0))
        assertEquals(PromptWeightKind.STRONG, PromptWeight.kindOf(1.0000001))
        assertEquals(PromptWeightKind.WEAK, PromptWeight.kindOf(0.9999999))
        assertEquals(PromptWeightKind.WEAK, PromptWeight.kindOf(0.0))
        assertEquals(PromptWeightKind.NEGATIVE, PromptWeight.kindOf(-0.0000001))
        assertEquals(PromptWeightKind.NEGATIVE, PromptWeight.kindOf(-6.0))
    }
}
