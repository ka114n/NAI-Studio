package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提示词框 id 与"按框读写"的那对纯函数。
 *
 * 用户 2026-09-16 要求：底部那条常驻按钮栏（撤销 / 重做 / 历史 / 翻译 / 优化 / 角色图鉴）
 * 全部**按当前选中的框**分派。于是"哪个框"成了贯穿状态层和界面层的键 ——
 * 这里把它的行为钉死，免得以后有人手写字符串字面量拼错一个字母。
 */
class PromptFieldTest {

    @Test
    fun `三个 id 互不相同且可枚举`() {
        assertEquals(3, PromptField.ALL.size)
        assertEquals(PromptField.ALL.size, PromptField.ALL.toSet().size)
        assertEquals(listOf("style", "positive", "negative"), PromptField.ALL)
    }

    @Test
    fun `不认识的名字兜底成正面提示词`() {
        // 持久化过的"当前选中框"理论上可能因为版本回退变成不认识的串 —— 不该崩
        assertEquals(PromptField.POSITIVE, PromptField.orDefault(""))
        assertEquals(PromptField.POSITIVE, PromptField.orDefault("stlye"))
        assertEquals(PromptField.POSITIVE, PromptField.orDefault("POSITIVE"))
        // 认得的原样返回
        assertEquals(PromptField.STYLE, PromptField.orDefault(PromptField.STYLE))
        assertEquals(PromptField.NEGATIVE, PromptField.orDefault(PromptField.NEGATIVE))
    }

    @Test
    fun `promptOf 各读各的字段`() {
        val p = GenerateParams(
            stylePrompt = "manga page",
            positivePrompt = "1girl",
            negativePrompt = "lowres",
        )
        assertEquals("manga page", p.promptOf(PromptField.STYLE))
        assertEquals("1girl", p.promptOf(PromptField.POSITIVE))
        assertEquals("lowres", p.promptOf(PromptField.NEGATIVE))
        // 兜底
        assertEquals("1girl", p.promptOf("nope"))
    }

    @Test
    fun `withPrompt 只动目标字段`() {
        val p = GenerateParams(
            stylePrompt = "manga page",
            positivePrompt = "1girl",
            negativePrompt = "lowres",
        )
        val style = p.withPrompt(PromptField.STYLE, "screentone")
        assertEquals("screentone", style.stylePrompt)
        assertEquals("1girl", style.positivePrompt)
        assertEquals("lowres", style.negativePrompt)

        val positive = p.withPrompt(PromptField.POSITIVE, "2girls")
        assertEquals("manga page", positive.stylePrompt)
        assertEquals("2girls", positive.positivePrompt)
        assertEquals("lowres", positive.negativePrompt)

        val negative = p.withPrompt(PromptField.NEGATIVE, "bad hands")
        assertEquals("manga page", negative.stylePrompt)
        assertEquals("1girl", negative.positivePrompt)
        assertEquals("bad hands", negative.negativePrompt)
    }

    @Test
    fun `withPrompt 不认识的名字写进正面框`() {
        // 和 promptOf 的兜底必须**一致**：否则会出现"读的是正面、写的是别处"这种错位
        val p = GenerateParams(stylePrompt = "s", positivePrompt = "p", negativePrompt = "n")
        assertEquals("x", p.withPrompt("whatever", "x").positivePrompt)
        assertEquals("x", p.withPrompt("whatever", "x").promptOf("whatever"))
    }

    @Test
    fun `改完还能原样读回来（读写口径一致）`() {
        val p = GenerateParams()
        for (field in PromptField.ALL) {
            val written = p.withPrompt(field, "value-$field")
            assertEquals("value-$field", written.promptOf(field))
        }
    }

    /**
     * 工具栏（撤销/重做/翻译/优化）用的键：**剧情自成一档**。
     *
     * 用户 2026-09-18 报："键盘上的工具条虽然说作用于剧情上但作用不了，只能在提示词栏使用"——
     * 根因就是工具栏拿了 `orDefault`：它把 `plot` 折成 `positive` ✗，于是标签写着"作用于剧情"、
     * 按下去改的却是正面提示词。这条测试把这个折法钉死。
     */
    @Test
    fun `剧情框在工具栏里有自己的键`() {
        assertEquals(PromptField.PLOT, PromptField.stackKey(PromptField.PLOT))
        assertTrue(PromptField.isPlot(PromptField.PLOT))
        // 三个提示词原样
        assertEquals(PromptField.STYLE, PromptField.stackKey(PromptField.STYLE))
        assertEquals(PromptField.POSITIVE, PromptField.stackKey(PromptField.POSITIVE))
        assertEquals(PromptField.NEGATIVE, PromptField.stackKey(PromptField.NEGATIVE))
        // 不认识的照旧兜底成正面
        assertEquals(PromptField.POSITIVE, PromptField.stackKey(""))
        assertEquals(PromptField.POSITIVE, PromptField.stackKey("stlye"))
        // ⚠️ `orDefault` **必须**继续把 plot 折成正面：promptOf / withPrompt 靠它兜底，
        // 真改了它，`orDefault(PLOT)` 就会去 `params` 里找一个叫 plot 的字段（找不到 → 正面），
        // 语义反而更绕。要用"剧情"的地方统一走 `stackKey` + `isPlot`。
        assertEquals(PromptField.POSITIVE, PromptField.orDefault(PromptField.PLOT))
    }
}
