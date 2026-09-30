package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 「按剧情分镜」的 LLM 输出解析器单测。
 *
 * 为什么必须测：**LLM 输出 JSON 是不稳定的**。真实会遇到的情况有 ——
 * 用 ```json 包起来、前面写一句"好的，这是分镜："、后面补一段解释、
 * role 给中文、键名写成 positive/caption、给 7 格（超过一页上限）。
 *
 * 解析器要是脆的，用户看到的就是"分镜生成失败"，而不是一句能看懂的原因。
 * 这里的每一条断言都对应一种真实见过的输出形态。
 */
class ComicStoryboardTest {

    private val goodJson = """
        {
          "style": "manga page, dynamic paneling, monochrome",
          "panels": [
            {"name": "格 1", "role": "scene", "prompt": "city street at night, heavy rain"},
            {"name": "格 2", "role": "", "prompt": "girl, looking back, close-up"},
            {"name": "格 3", "role": "text", "prompt": "text, speech bubble \"...gone\""}
          ]
        }
    """.trimIndent()

    // -----------------------------------------------------------------------
    // 正常解析
    // -----------------------------------------------------------------------

    @Test
    fun `解析干净的JSON`() {
        val board = ComicStoryboard.parse(goodJson)
        assertEquals("manga page, dynamic paneling, monochrome", board.style)
        assertEquals(3, board.panels.size)
        assertEquals("格 1", board.panels[0].name)
        assertEquals("scene", board.panels[0].role)
        assertEquals("city street at night, heavy rain", board.panels[0].prompt)
        assertEquals("text", board.panels[2].role)
    }

    @Test
    fun `剥掉markdown代码块`() {
        val raw = "```json\n$goodJson\n```"
        val board = ComicStoryboard.parse(raw)
        assertEquals(3, board.panels.size)
    }

    @Test
    fun `剥掉没有语言标记的代码块`() {
        val board = ComicStoryboard.parse("```\n$goodJson\n```")
        assertEquals(3, board.panels.size)
    }

    @Test
    fun `模型前后加了解释文字也能救回来`() {
        val raw = "好的，这是你要的分镜：\n\n$goodJson\n\n希望对你有帮助！需要调整随时说。"
        val board = ComicStoryboard.parse(raw)
        assertEquals(3, board.panels.size)
        assertEquals("manga page, dynamic paneling, monochrome", board.style)
    }

    @Test
    fun `只给数组没有style也能解析`() {
        val raw = """
            [
              {"name": "格 1", "prompt": "a"},
              {"name": "格 2", "prompt": "b"}
            ]
        """.trimIndent()
        val board = ComicStoryboard.parse(raw)
        assertEquals("", board.style)
        assertEquals(2, board.panels.size)
    }

    // -----------------------------------------------------------------------
    // 字段容错
    // -----------------------------------------------------------------------

    @Test
    fun `role给中文也能认`() {
        val raw = """
            {"panels":[
              {"prompt":"a","role":"场景"},
              {"prompt":"b","role":"台词"},
              {"prompt":"c","role":"道具"},
              {"prompt":"d","role":"角色"}
            ]}
        """.trimIndent()
        val board = ComicStoryboard.parse(raw)
        assertEquals(listOf("scene", "text", "prop", ""), board.panels.map { it.role })
    }

    @Test
    fun `role给英文同义词也能认`() {
        val raw = """
            {"panels":[
              {"prompt":"a","role":"background"},
              {"prompt":"b","role":"bubble"},
              {"prompt":"c","role":"object"}
            ]}
        """.trimIndent()
        val board = ComicStoryboard.parse(raw)
        assertEquals(listOf("scene", "text", "prop"), board.panels.map { it.role })
    }

    @Test
    fun `role认不出来时当角色而不是整次失败`() {
        val raw = """{"panels":[{"prompt":"a","role":"随便写的"},{"prompt":"b"}]}"""
        val board = ComicStoryboard.parse(raw)
        assertEquals(listOf("", ""), board.panels.map { it.role })
    }

    @Test
    fun `prompt键名写成positive或caption也认`() {
        val raw = """
            {"panels":[{"positive":"a"},{"caption":"b"}]}
        """.trimIndent()
        val board = ComicStoryboard.parse(raw)
        assertEquals(listOf("a", "b"), board.panels.map { it.prompt })
    }

    @Test
    fun `panels键名写成panel或shots也认`() {
        assertEquals(2, ComicStoryboard.parse("""{"panel":[{"prompt":"a"},{"prompt":"b"}]}""").panels.size)
        assertEquals(2, ComicStoryboard.parse("""{"shots":[{"prompt":"a"},{"prompt":"b"}]}""").panels.size)
    }

    @Test
    fun `元素是裸字符串也能收`() {
        val board = ComicStoryboard.parse("""{"panels":["a","b"]}""")
        assertEquals(listOf("a", "b"), board.panels.map { it.prompt })
    }

    @Test
    fun `没有提示词的格子被跳过_其余保留`() {
        val raw = """
            {"panels":[{"prompt":"a"},{"prompt":"   "},{"note":"没有prompt"},{"prompt":"b"}]}
        """.trimIndent()
        val board = ComicStoryboard.parse(raw)
        assertEquals(listOf("a", "b"), board.panels.map { it.prompt })
    }

    @Test
    fun `name缺失时给空串_不崩`() {
        val board = ComicStoryboard.parse("""{"panels":[{"prompt":"a"},{"prompt":"b"}]}""")
        assertEquals(listOf("", ""), board.panels.map { it.name })
    }

    // -----------------------------------------------------------------------
    // 该报错的时候要报错，而且要给人话
    // -----------------------------------------------------------------------

    private fun expectFail(raw: String): String {
        try {
            ComicStoryboard.parse(raw)
        } catch (e: ComicStoryboard.StoryboardException) {
            return e.message.orEmpty()
        }
        fail("这段本该解析失败：$raw")
        return ""
    }

    @Test
    fun `什么都没有时报错`() {
        assertTrue(expectFail("   ").isNotEmpty())
    }

    @Test
    fun `找不到JSON时报错`() {
        // 故意用一句**不像拒绝**的话：拒绝会走另一条分支（见下面"模型拒绝时…"那条）
        assertTrue(expectFail("这是一段没有任何结构的说明文字。").contains("找不到"))
    }

    @Test
    fun `JSON坏了时报错`() {
        assertTrue(expectFail("{不是合法的 json").isNotEmpty())
    }

    @Test
    fun `没有panels数组时报错`() {
        assertTrue(expectFail("""{"style":"manga page"}""").contains("panels"))
    }

    @Test
    fun `panels是空数组时报错`() {
        assertTrue(expectFail("""{"panels":[]}""").isNotEmpty())
    }

    @Test
    fun `每一格都没提示词时报错`() {
        assertTrue(expectFail("""{"panels":[{"note":"x"},{"note":"y"}]}""").isNotEmpty())
    }

    @Test
    fun `只有一格时报错_一页至少两格`() {
        val msg = expectFail("""{"panels":[{"prompt":"a"}]}""")
        assertTrue("应该提示「至少」，实际：$msg", msg.contains("至少"))
    }

    @Test
    fun `超过六格时报错`() {
        val seven = (1..7).joinToString(",") { """{"prompt":"p$it"}""" }
        val msg = expectFail("""{"panels":[$seven]}""")
        assertTrue("应该提示超过上限，实际：$msg", msg.contains("超过"))
    }

    @Test
    fun `正好六格是合法的`() {
        val six = (1..6).joinToString(",") { """{"prompt":"p$it"}""" }
        assertEquals(6, ComicStoryboard.parse("""{"panels":[$six]}""").panels.size)
    }

    @Test
    fun `正好两格是合法的`() {
        assertEquals(2, ComicStoryboard.parse("""{"panels":[{"prompt":"a"},{"prompt":"b"}]}""").panels.size)
    }

    // -----------------------------------------------------------------------
    // 版式自动匹配
    // -----------------------------------------------------------------------

    @Test
    fun `按格数挑版式_优先选不对称的那个`() {
        // 4 格有两个候选（田字四格 / 主格+三副格），按参考技能"严禁死板的等分格子"选后者
        assertEquals("v4a", ComicStoryboard.defaultLayoutFor(4))
        assertEquals("v6a", ComicStoryboard.defaultLayoutFor(6))
        assertEquals("v2", ComicStoryboard.defaultLayoutFor(2))
        assertEquals("v3", ComicStoryboard.defaultLayoutFor(3))
        assertEquals("v5", ComicStoryboard.defaultLayoutFor(5))
    }

    @Test
    fun `挑出来的版式格数必须真的对得上`() {
        (2..6).forEach { count ->
            val id = ComicStoryboard.defaultLayoutFor(count)
            assertEquals("$count 格挑出的版式格数不对", count, ComicLayout.panelCountOf(id))
        }
    }

    @Test
    fun `格数没有对应版式时退回auto`() {
        assertEquals(ComicLayout.AUTO, ComicStoryboard.defaultLayoutFor(1))
        assertEquals(ComicLayout.AUTO, ComicStoryboard.defaultLayoutFor(9))
    }

    // ------------------------------------------------------------------
    // "找不到 JSON" 的诊断信息
    //
    // 这条来自一次真实的翻车：用户报"狂暴模式失败"，截图里只有
    // 「模型的输出里找不到 JSON」—— 模型的原文被丢掉了，谁也没法判断
    // 是拒绝、是闲聊还是被截断。下面几条钉住"必须把原文带出来"。
    // ------------------------------------------------------------------

    private fun noJsonError(raw: String): String = try {
        ComicStoryboard.parse(raw)
        fail("本该抛异常")
        ""
    } catch (e: ComicStoryboard.StoryboardException) {
        e.message.orEmpty()
    }

    @Test
    fun `找不到 JSON 时要把模型的原文带进错误里`() {
        val message = noJsonError("好的，我理解你的需求了。请提供更多信息。")
        assertTrue("错误里没带模型原文：$message", message.contains("好的，我理解你的需求了"))
        assertTrue(message.contains("找不到 JSON"))
    }

    @Test
    fun `模型拒绝时要说清是拒绝 而不是笼统的找不到 JSON`() {
        val message = noJsonError("抱歉，我无法协助处理这个请求。")
        assertTrue("没识别出拒绝：$message", message.contains("拒绝"))
        // 拒绝时给一句人话
        assertTrue(message.contains("剧情"))
    }

    @Test
    fun `普通跑偏不会被误报成拒绝`() {
        val message = noJsonError("这是一段普通的说明文字，没有任何 JSON。")
        assertTrue(message.contains("找不到 JSON"))
        assertFalse("不该说成拒绝：$message", message.contains("拒绝/说明"))
    }

    @Test
    fun `原文里的换行会被压平 免得错误信息一片空白`() {
        val message = noJsonError("第一行\n\n第二行\t第三行")
        assertTrue(message.contains("第一行 第二行 第三行"))
        assertFalse(message.contains("\n"))
    }

    @Test
    fun `原文很长时只带前若干字并加省略号`() {
        val long = "啊".repeat(300)
        val message = noJsonError(long)
        assertTrue(message.contains("…"))
        // 预览上限 + 前后包装，不该把 300 字全塞进去
        assertTrue("预览没截断，长度=${message.length}", message.length < 300)
        assertTrue(
            message.contains("啊".repeat(ComicStoryboard.NO_JSON_PREVIEW_CHARS)),
        )
    }
}
