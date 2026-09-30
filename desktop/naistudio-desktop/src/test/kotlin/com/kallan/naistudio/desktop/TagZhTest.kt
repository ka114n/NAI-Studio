package com.kallan.naistudio.models

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **第 ㊿ 批：tag 中文对照 —— 口径照原网页** ✓
 *
 * 依据：站点 `assets/app/tag-zh-core.js`（`tagZhKey` / `splitPromptPieces` / `lookupTagZh` ✓）
 * + `tag-zh.js`（`CORE_PATH` / 书分片 / 优先级 ✓）。**逐条对齐，不自创** ✓。
 *
 * ⚠️ 用户口径两条（都要钉住 ✓）：
 *  · 「**tag 下跟着中文翻译**」✓ —— 显示要有 ✓；
 *  · 「**复制不带翻译**」✓ —— 复制永远拿原文 ✓。
 */
class TagZhTest {

    // ---------------------------------------------------------------- 查表键

    @Test
    fun `查表键剥掉权重与包裹`() {
        // 站点 tagZhKey 的几条真实口径
        assertEquals("girl", TagZhProtocol.key("girl"))
        assertEquals("girl", TagZhProtocol.key("  GIRL  "))
        assertEquals("girl", TagZhProtocol.key("1.2::girl::"))
        assertEquals("girl", TagZhProtocol.key("-1::girl::"))
        assertEquals("girl", TagZhProtocol.key("{girl}"))
        assertEquals("girl", TagZhProtocol.key("[girl]"))
        assertEquals("girl", TagZhProtocol.key("\"girl\""))
        // SD 的权重后缀
        assertEquals("girl", TagZhProtocol.key("(girl:1.2)"))
        // 下划线当空格
        assertEquals("two side up", TagZhProtocol.key("two_side_up"))
    }

    /** **不查**的那几类 ✓（站点同口径：返回空串 = 不查 ✓）。 */
    @Test
    fun `这几类不给中文`() {
        assertEquals("空", "", TagZhProtocol.key(""))
        assertEquals("纯数字", "", TagZhProtocol.key("2025"))
        assertEquals("纯中文", "", TagZhProtocol.key("女孩子"))
        // ⚠️ 画师名不翻译（那是人名）—— 站点 tagZhKey 里写死的 key.startsWith('artist:')
        assertEquals("artist: 开头不查", "", TagZhProtocol.key("artist:nyong nyong"))
        assertEquals("artist: 带权重也不查", "", TagZhProtocol.key("0.6::artist:ebora::"))
        // 超长不查
        assertTrue("超长不查", TagZhProtocol.key("a".repeat(301)).isEmpty())
    }

    // ---------------------------------------------------------------- 切段

    /**
     * **切段必须能原样拼回** ✓ —— 这是"复制不带翻译"的地基 ✓：
     * 复制走的永远是原文 ✓，中文只是**另画一行** ✓。
     */
    @Test
    fun `切段拼回去与原文逐字相同`() {
        val samples = listOf(
            "artist:382, year 2025",
            "0.6::artist:nyong nyong::, 0.8::artist:hiro (dismaless)::, very aesthetic",
            "1girl,\n1boy，\nnight",
            "worst quality, bad quality, lowres",
            "  girl ,  smile  ",
        )
        for (s in samples) {
            val rebuilt = TagZhProtocol.split(s).joinToString("") { it.lead + it.text + it.sep }
            assertEquals("拼回必须与原文逐字相同：$s", s, rebuilt)
        }
    }

    @Test
    fun `切段按逗号全角逗号与换行`() {
        assertEquals(3, TagZhProtocol.split("a, b，c").size)
        assertEquals(3, TagZhProtocol.split("a\nb\r\nc").size)
    }

    // ---------------------------------------------------------------- 查表

    private fun shard(m: Map<String, String> = emptyMap(), d: Map<String, String> = emptyMap(), a: Map<String, String> = emptyMap()) =
        TagZhProtocol.Shard(m = m, d = d, a = a)

    /** 优先级 **m > d > a** ✓（站点 LOOKUP_ORDER ✓）。 */
    @Test
    fun `译名优先级是 人工 大于 词库 大于 机翻`() {
        val s = shard(
            m = mapOf("girl" to "人工译"),
            d = mapOf("girl" to "词库译"),
            a = mapOf("girl" to "机翻译"),
        )
        assertEquals("人工优先", "人工译", TagZhProtocol.lookup(listOf(s), "girl"))
        assertEquals("没有人工就取词库", "词库译", TagZhProtocol.lookup(listOf(shard(d = mapOf("girl" to "词库译"), a = mapOf("girl" to "机翻译"))), "girl"))
        assertEquals("都没有才取机翻", "机翻译", TagZhProtocol.lookup(listOf(shard(a = mapOf("girl" to "机翻译"))), "girl"))
    }

    /** 同级**先 core 再书分片** ✓（传进来的顺序 ✓）。 */
    @Test
    fun `同一层先查通用表再查书分片`() {
        val core = shard(d = mapOf("girl" to "通用"))
        val book = shard(d = mapOf("girl" to "本书"))
        assertEquals("core 在前", "通用", TagZhProtocol.lookup(listOf(core, book), "girl"))
        assertEquals("只给书分片时用它", "本书", TagZhProtocol.lookup(listOf(book), "girl"))
    }

    @Test
    fun `查不到就是 null 不显示空占位`() {
        assertNull(TagZhProtocol.lookup(listOf(shard(d = mapOf("girl" to "女孩"))), "unknown"))
        assertNull(TagZhProtocol.lookup(listOf(shard()), "girl"))
        assertNull("空键不查", TagZhProtocol.lookup(listOf(shard(d = mapOf("girl" to "女孩"))), ""))
    }

    // ---------------------------------------------------------------- 词表解析

    @Test
    fun `词表解析要求 schema 对得上`() {
        val ok = JSONObject("""{"schema":1,"m":{"a":"甲"},"d":{"b":"乙"},"a":{"c":"丙"}}""")
        val s = TagZhProtocol.parseShard(ok)!!
        assertEquals("甲", s.m["a"])
        assertEquals("乙", s.d["b"])
        assertEquals("丙", s.a["c"])

        assertNull("schema 不对 → 当作没有对照表", TagZhProtocol.parseShard(JSONObject("""{"schema":2,"m":{}}""")))
        assertNull("null → 没有", TagZhProtocol.parseShard(null))
    }

    @Test
    fun `空译名不占位`() {
        val j = JSONObject("""{"schema":1,"m":{"a":"  "},"d":{"b":"乙"}}""")
        val s = TagZhProtocol.parseShard(j)!!
        assertNull("空白译名不算数", s.m["a"])
        assertEquals("乙", s.d["b"])
    }

    /** 一个真实的 tag 走完整条链：切段 → 查键 → 取译名。 */
    @Test
    fun `真实一条提示词走完整条链`() {
        val text = "1.2::very aesthetic::, amazing quality, 1girl, artist:somebody"
        val shard = shard(d = mapOf(
            "very aesthetic" to "非常唯美",
            "amazing quality" to "极佳品质",
            "1girl" to "1个女孩",
        ))
        val rendered = TagZhProtocol.split(text).map { p ->
            p.text.trim() to TagZhProtocol.lookup(listOf(shard), p.key)
        }
        assertEquals("非常唯美", rendered.first { it.first.contains("very aesthetic") }.second)
        assertEquals("极佳品质", rendered.first { it.first == "amazing quality" }.second)
        assertEquals("1个女孩", rendered.first { it.first == "1girl" }.second)
        assertNull("画师名不翻译", rendered.first { it.first.contains("artist:") }.second)
    }
}
