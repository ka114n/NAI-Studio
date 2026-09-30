package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **提示词词典**的判据（用户 2026-09-24：「补充词典库」✓，见 [TagDictionary] 的说明 ✓）。
 *
 * 钉三件事 ✓：
 *  1. **一行一条的解析** ✓ —— 坏行**跳过不抛** ✗（词典坏一条不该拖垮启动 ✓）；
 *  2. **建议的排序口径** ✓ —— 前缀命中优先、同级按热度降序 ✓（"先给最多人用的" ✓）；
 *  3. **下划线 / 空格归一** ✓ —— 用户打 `long hair` 也该命中 `long_hair` ✓。
 */
class TagDictionaryTest {

    private val sample = listOf(
        TagDictionary.Entry("long_hair", 'g', 5327932),
        TagDictionary.Entry("long_skirt", 'g', 120000),
        TagDictionary.Entry("very_long_hair", 'g', 900000),
        TagDictionary.Entry("long_sleeves", 'g', 4000000),
        TagDictionary.Entry("hakurei_reimu", 'c', 30000),
    )

    @Test
    fun `解析一行 —— 正常、坏行、注释`() {
        assertEquals(
            TagDictionary.Entry("long_hair", 'g', 5327932),
            TagDictionary.parseLine("long_hair\tg\t5327932"),
        )
        assertNull("空行跳过", TagDictionary.parseLine(""))
        assertNull("字段不够跳过", TagDictionary.parseLine("long_hair\tg"))
        assertNull("热度不是数字跳过", TagDictionary.parseLine("long_hair\tg\tabc"))
        assertNull("空 tag 跳过", TagDictionary.parseLine("\tg\t123"))
        assertNull("注释行跳过", TagDictionary.parseLine("# 这是说明"))
    }

    @Test
    fun `解析整份 —— 坏行不会让整份失败`() {
        val raw = """
            long_hair	g	5327932
            垃圾行
            solo	g	6162806
            
            long_sleeves	g	4000000
        """.trimIndent()
        val entries = TagDictionary.parse(raw)
        assertEquals(3, entries.size)
        assertEquals("long_hair", entries[0].tag)
        assertEquals("solo", entries[1].tag)
    }

    /** **前缀优先** ✓：`long` 的候选里，`long_hair` / `long_skirt` / `long_sleeves` 要排在 `very_long_hair` 前面。 */
    @Test
    fun `建议 —— 前缀优先于子串`() {
        val got = TagDictionary.suggest(sample, "long", limit = 5).map { it.tag }
        assertEquals("long_hair", got[0])
        assertEquals("long_sleeves", got[1])
        assertEquals("long_skirt", got[2])
        // very_long_hair 只是**子串**命中 ⇒ 排在前缀命中之后
        assertEquals("very_long_hair", got[3])
    }

    /**
     * 同级**按热度降序** ✓。
     *
     * ⚠️ 这条一开始我写错成"整体降序" ✗ —— 实际口径是**分组内**降序 ✓：
     * **前缀命中整组排在子串命中之前** ✓（`very_long_hair` 热度再高也只是子串命中 ✓，
     * 也得排在那三个前缀命中**之后** ✓）。所以序列是"降 → 升"的 ✓，本条钉的是这个形状 ✓。
     */
    @Test
    fun `建议 —— 前缀组内按热度降序，子串组整体靠后`() {
        val got = TagDictionary.suggest(sample, "long", limit = 5)
        // 前三 = 前缀命中，组内降序
        val prefix = got.take(3).map { it.count }
        assertEquals(prefix.sortedDescending(), prefix)
        // 第四 = 子串命中（very_long_hair），它热度 900000 > long_skirt 的 120000，
        // 但依然排在前缀组之后 ✓ —— 这就是"前缀优先"的意思 ✓。
        assertEquals("very_long_hair", got[3].tag)
    }

    /** **下划线 / 空格归一** ✓：打 `long hair` 也要命中 `long_hair` ✓。 */
    @Test
    fun `建议 —— 空格与下划线等价`() {
        val spaced = TagDictionary.suggest(sample, "long hair", limit = 3).map { it.tag }
        assertEquals("long_hair", spaced.first())
        val underscored = TagDictionary.suggest(sample, "long_hair", limit = 3).map { it.tag }
        assertEquals("long_hair", underscored.first())
        // 大写也认
        assertEquals("long_hair", TagDictionary.suggest(sample, "LONG", limit = 1).first().tag)
    }

    /** **空查询 = 最热的那几条** ✓（打开就有建议 ✓，不是空列表 ✗）。 */
    @Test
    fun `建议 —— 空查询给最热的`() {
        val got = TagDictionary.suggest(sample, "", limit = 2).map { it.tag }
        assertEquals(listOf("long_hair", "long_sleeves"), got)
    }

    /** `limit` 封顶 ✓；`limit <= 0` ⇒ 空 ✓。 */
    @Test
    fun `建议 —— limit 封顶`() {
        assertEquals(1, TagDictionary.suggest(sample, "long", limit = 1).size)
        assertTrue(TagDictionary.suggest(sample, "long", limit = 0).isEmpty())
    }

    /** 查不到就是**空** ✓（界面据此"不显示下拉" ✗ 而不是显示一堆不相干 ✓）。 */
    @Test
    fun `建议 —— 查不到给空`() {
        assertTrue(TagDictionary.suggest(sample, "zzzz-not-a-tag", limit = 5).isEmpty())
    }

    /** 分类码翻成中文 ✓（界面按分类显示用 ✓）。 */
    @Test
    fun `分类名`() {
        assertEquals("角色", TagDictionary.categoryLabel('c'))
        assertEquals("画师", TagDictionary.categoryLabel('a'))
        assertEquals("通用", TagDictionary.categoryLabel('g'))
        assertEquals("通用", TagDictionary.categoryLabel('?'))
    }

    /**
     * **真资源在不在**（打包那一环最容易静默失败 ✗，见 build.gradle.kts 那两条 `srcDir` ✓）——
     * 这条测试就是那道护栏 ✓：读不到 `/dictionary.tsv` 或条数太少，直接红 ✗。
     */
    @Test
    fun `打包进去的词典 —— 读得到、条数够、能建议`() {
        val raw = checkNotNull(
            TagDictionaryTest::class.java.getResourceAsStream("/dictionary.tsv"),
        ) { "读不到 /dictionary.tsv —— 两端的 build.gradle.kts 里 resources.srcDir 没配上？" }
            .use { it.readBytes().toString(Charsets.UTF_8) }
        val entries = TagDictionary.parse(raw)
        assertTrue("词典条数太少：${entries.size}", entries.size > 20000)
        // 最热的几个一定在
        val tags = entries.take(500).map { it.tag }.toSet()
        assertTrue("前 500 条里应该有 1girl", tags.contains("1girl"))
        // 还能真的给出建议
        val suggested = TagDictionary.suggest(entries, "long hair", limit = 5)
        assertTrue("long hair 应该有候选", suggested.isNotEmpty())
        assertTrue(
            "候选里应该有 long_hair（拿到的是 ${suggested.map { it.tag }}）",
            suggested.any { it.tag == "long_hair" },
        )
    }
}
