package com.kallan.naistudio.models

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **提示词翻译管道判据**（第 ㊿h 批 2026-09-22）——
 * 与网页原型 `translate/hybrid.test.mjs` 同一套口径，逐条搬过来。
 *
 * ⚠️ 这里钉住的都是**实测逼出来**的性质，不是想当然：
 *   · 百度**吞空行**（5 行进 ⇒ 3 行出）⇒ 批前必须滤空段；
 *   · 百度**吃括号、改坏 `::`** ⇒ 权重壳绝不能外发；
 *   · 行数对不上时**宁可某条没翻，绝不串位**。
 */
class PromptTranslateTest {

    private val dict = PromptTranslate.Dict(
        fwd = mapOf(
            "1girl" to "单人女性", "long hair" to "长发", "smile" to "微笑",
            "blue eyes" to "蓝眼睛", "twintails" to "双马尾",
        ),
        rev = mapOf(
            "单人女性" to listOf("1girl"), "长发" to listOf("long hair"),
            "微笑" to listOf("smile"), "蓝眼睛" to listOf("blue eyes"),
            "双马尾" to listOf("twintails"),
        ),
    )

    private val P = PromptTranslate.Params()

    /** 直通后端：把每行包一层，用来验证"回填落到了哪一段" */
    private fun echo(prefix: String = "EN["): suspend (List<String>) -> List<String> =
        { lines -> lines.map { "$prefix$it]" } }

    /**
     * ⚠️ 两处命名/顺序都踩过：
     *   · 名字不能叫 `run` —— 撞 Kotlin 标准库的 `run`；
     *   · `backend` 必须放**最后** —— trailing lambda 只绑最后一个参数，
     *     放中间的话 `pipeline(x) { … }` 会去绑 `params` ⇒ 类型不对。
     */
    private fun pipeline(
        raw: String,
        params: PromptTranslate.Params = P,
        backend: suspend (List<String>) -> List<String> = echo(),
    ) = runBlocking { PromptTranslate.run(raw, dict, params, backend) }

    private fun render(out: PromptTranslate.Outcome, params: PromptTranslate.Params = P) =
        PromptTranslate.render(out.items, params)

    // ---------------------------------------------------------------- 标点

    @Test
    fun `只修逗号类 句号不是分隔符`() {
        val got = PromptTranslate.splitInput("1girl，长发、蓝眼睛；smile, 双马尾").map { it.text }
        assertEquals(listOf("1girl", "长发", "蓝眼睛", "smile", "双马尾"), got)

        // 句号**不是分隔符** —— 切开会把一句自然语言拆成两截（NAI 吃自然语言）
        val dot = PromptTranslate.splitInput("一个女孩站在雨中。她撑着伞。").map { it.text }
        assertEquals(1, dot.size)
        assertEquals("一个女孩站在雨中。她撑着伞。", dot[0])   // ⚠️ 切段时**保留全角**
    }

    @Test
    fun `全角标点半角化 但只在输出端`() {
        assertEquals("微笑!你好?", PromptTranslate.toAsciiPunct("微笑！你好？"))
        assertEquals("一个女孩站在雨中.", PromptTranslate.toAsciiPunct("一个女孩站在雨中。"))
        assertEquals("1girl(test)\"x\"", PromptTranslate.toAsciiPunct("1girl（test）“x”"))

        // ⚠️ 送翻译的**必须是全角原文** —— 实测提前换成 `.` 百度会把标点整个丢掉
        val sent = mutableListOf<String>()
        pipeline("一个女孩站在雨中。她撑着伞。") { l -> sent.addAll(l); l }
        assertTrue("送翻译应为全角原文，实际=$sent", sent.any { it.contains("。") })
    }

    // ---------------------------------------------------------------- 换行

    @Test
    fun `连续多个换行原样保留`() {
        val cases = listOf("a\nb" to 1, "a\n\nb" to 2, "a\n\n\nb" to 3, "a\n,,\nb" to 2)
        for ((input, want) in cases) {
            val tags = render(pipeline(input)).tags
            assertEquals("输入 ${input.replace("\n", "\\n")}", want, tags.count { it == '\n' })
        }
        // 结尾多余换行丢掉（不留一串空行）
        assertEquals("a", render(pipeline("a\n\n\n")).tags)
    }

    // ---------------------------------------------------------------- 权重壳

    @Test
    fun `三种权重壳 保留与不保留`() {
        val cases = listOf(
            "{long hair}" to "{long hair}",
            "[long hair]" to "[long hair]",
            "{{long hair}}" to "{{long hair}}",
            "1.2::long hair::" to "1.2::long hair::",
            "{微笑}" to "{smile}",
        )
        for ((input, wantOn) in cases) {
            val out = pipeline("x, $input")
            val on = render(out).tags.substringAfterLast(", ")
            val off = render(out, P.copy(keepWeight = false)).tags.substringAfterLast(", ")
            assertEquals("保留权重：$input", wantOn, on)
            assertFalse("关掉权重应无壳：$input ⇒ $off", off.contains("::") || off.contains("{") || off.contains("["))
        }
    }

    @Test
    fun `权重壳里的多个 tag 是一组 外壳只拼一次`() {
        assertEquals("[smile, blue eyes], EN[红色女孩]", render(pipeline("[微笑,蓝眼睛]，红色女孩")).tags)
        assertEquals("[smile, blue eyes, EN[红色女孩]]", render(pipeline("[微笑,蓝眼睛,红色女孩]")).tags)
        assertEquals("1.2::smile, blue eyes::", render(pipeline("1.2::微笑,蓝眼睛::")).tags)
        assertEquals("（smile, blue eyes）", render(pipeline("（微笑,蓝眼睛）")).tags)
    }

    @Test
    fun `异形权重 与 多层嵌套`() {
        // 用户口径：`{{{tag::` 等效 `{{{tag}}}`，但**输出原样保留用户的写法**
        assertEquals("{{{smile::", render(pipeline("{{{微笑::")).tags)
        assertEquals("[[[[[[[smile]]]]]]]", render(pipeline("[[[[[[[微笑]]]]]]]")).tags)
        assertEquals("[smile].", render(pipeline("[微笑]。")).tags)            // 句末标点拼在壳外
        assertEquals("1.2::smile::.", render(pipeline("1.2::微笑::。")).tags)  // 收尾 `::` 没被句号正则吃掉
    }

    @Test
    fun `权重壳一个字符都不外发`() {
        val sent = mutableListOf<String>()
        pipeline("[微笑,蓝眼睛]，一个词典外的中文") { l -> sent.addAll(l); l }
        val leaked = sent.filter { it.any { c -> c in "{}[]（）" } || it.contains("::") }
        assertTrue("外壳泄漏到翻译请求里了：$leaked", leaked.isEmpty())
    }

    // ---------------------------------------------------------------- 词典优先

    @Test
    fun `词典全命中则零请求`() {
        val sent = mutableListOf<String>()
        val out = pipeline("1girl, 长发, 微笑, 双马尾") { l -> sent.addAll(l); l }
        assertTrue("词典全命中却发了请求：$sent", sent.isEmpty())
        assertEquals(4, out.dictHits)
        assertEquals("1girl, long hair, smile, twintails", render(out).tags)
    }

    @Test
    fun `查不到的合并成一批一次请求`() {
        var calls = 0
        val batches = mutableListOf<List<String>>()
        val out = pipeline("1girl, 落词典外的中文甲, smile, 落词典外的中文乙") { l ->
            calls++; batches.add(l); l.map { "T[$it]" }
        }
        assertEquals("应当只发 1 次请求", 1, calls)
        assertEquals(2, batches[0].size)
        val tags = render(out).tags
        assertTrue(tags, tags.contains("T[落词典外的中文甲]") && tags.contains("T[落词典外的中文乙]"))
    }

    // ---------------------------------------------------------------- 回填对齐（核心）

    @Test
    fun `后端吞掉一行时绝不串位`() {
        val swallowFirst: suspend (List<String>) -> List<String> = { l ->
            if (l.size == 1) l.map { "T[$it]" } else l.drop(1).map { "T[$it]" }
        }
        val out = pipeline("x1, x2, x3, x4", backend = swallowFirst)
        // 关键：拿到的译文**必须与它所在那一段内容一致**（T(xN) 只能落在 xN 上）
        out.items.forEachIndexed { i, it ->
            val v = it.inner
            if (v.startsWith("T[")) assertEquals("串位了", "T[x${i + 1}]", v)
        }
    }

    @Test
    fun `后端永远行数不符则全部保留原文`() {
        val alwaysWrong: suspend (List<String>) -> List<String> = { emptyList() }
        val out = pipeline("z1, z2", backend = alwaysWrong)
        assertEquals(listOf("z1", "z2"), out.items.map { it.inner })
        assertEquals(2, out.misses)
    }

    @Test
    fun `后端抛异常不崩且保留原文`() {
        val boom: suspend (List<String>) -> List<String> = { throw RuntimeException("网络炸了") }
        val out = pipeline("w1, w2", backend = boom)
        assertEquals(listOf("w1", "w2"), out.items.map { it.inner })
    }

    @Test
    fun `空行绝不入批`() {
        val sent = mutableListOf<String>()
        pipeline("甲\n\n  \n乙\n") { l -> sent.addAll(l); l }
        assertEquals(listOf("甲", "乙"), sent)
    }

    @Test
    fun `超长单条自己一批`() {
        val long = "L".repeat(2500)
        val need = listOf(
            PromptTranslate.Need(0, long, PromptTranslate.Dir.TAG2ZH),
            PromptTranslate.Need(1, "short", PromptTranslate.Dir.TAG2ZH),
        )
        val batches = PromptTranslate.planBatches(need, P)
        assertEquals(2, batches.size)
        assertEquals(1, batches[0].size)
        assertEquals(long, batches[0][0].text)
    }

    // ---------------------------------------------------------------- 词典构建

    @Test
    fun `反向候选按频次排序 频次高的当首选`() {
        val shard = TagZhProtocol.Shard(
            d = mapOf("longhair" to "长发", "long hair" to "长发", "long hairs" to "长发"),
        )
        // 没有频次 ⇒ 只能按长度，会挑到 longhair（**这就是为什么需要频次**）
        val noFreq = PromptTranslate.buildDict(listOf(shard))
        assertEquals("longhair", noFreq.rev["长发"]!![0])
        // 有频次 ⇒ 挑真实提示词里最常用的那个
        val withFreq = PromptTranslate.buildDict(listOf(shard), mapOf("long hair" to 1200, "long hairs" to 5))
        assertEquals("long hair", withFreq.rev["长发"]!![0])
    }

    @Test
    fun `候选清洗 把被污染的原文还原`() {
        val shard = TagZhProtocol.Shard(a = mapOf("blue eyes/*蓝眼睛*/" to "蓝眼睛"))
        val d = PromptTranslate.buildDict(listOf(shard))
        assertEquals(listOf("blue eyes"), d.rev["蓝眼睛"])
    }
}
