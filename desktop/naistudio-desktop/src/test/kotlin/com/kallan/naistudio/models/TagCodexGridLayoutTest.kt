package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画师超市网格摆放判据 ✓（第 ㊿e2 批 2026-09-22 ✓）。
 *
 * 口径**逐条来自用户原话** ✓：
 *  「**竖图属于一个宽度，长图属于两格宽度**」
 *  「**长图等比放大，占满格子，但要显示完整，格子占不完可留白**」
 *
 * ⚠️ **判据里的比例取真实数据的两档** ✓（不是编的 ✓）：
 *  · **0.685** = 832×1216 标准竖图 ✓（实测 1200 张 ✓，主群 ✓）
 *  · **1.451** = 1216×832 标准横图 ✓（实测 506 张 ✓，次群 ✓）
 *
 * ⚠️ 旧版判据用 0.45 / 0.72 这种**真实数据里几乎不存在的值** ✗ ——
 * 判据全绿但用户看到的**清一色同宽** ✗（他报"没改变"✓）。
 * **教训**：判据的输入要取自**真实分布** ✓，否则测的是想象中的界面 ✗。
 */
class TagCodexGridLayoutTest {

    private val portrait = 0.685f   // 真实竖图（主群 ✓）⇒ 1 格
    private val landscape = 1.451f  // 真实横图（次群 ✓）⇒ 2 格
    private val square = 1.0f       // 方图 ⇒ 2 格（≥ 阈值 ✓）

    private fun spans(rows: List<TagCodexGridLayout.Row>) =
        rows.map { r -> r.slots.map { it.span } }

    @Test
    fun `真实两档 竖图一格 横图两格`() {
        assertEquals("832x1216 竖图 = 1 格", 1, TagCodexGridLayout.spanOf(portrait))
        assertEquals("1216x832 横图 = 2 格", 2, TagCodexGridLayout.spanOf(landscape))
        assertEquals("方图 = 2 格", 2, TagCodexGridLayout.spanOf(square))
        assertEquals("取不到比例 = 1 格", 1, TagCodexGridLayout.spanOf(0f))
    }

    @Test
    fun `三列一张横图 同行补一张竖图`() {
        val rows = TagCodexGridLayout.rows(listOf(landscape, portrait, portrait), columns = 3)
        assertEquals(listOf(2, 1), spans(rows)[0])
        assertEquals(listOf(1), spans(rows)[1])
    }

    @Test
    fun `四列一张横图 同行补两张竖图`() {
        val rows = TagCodexGridLayout.rows(listOf(landscape, portrait, portrait), columns = 4)
        assertEquals(listOf(2, 1, 1), spans(rows)[0])
    }

    @Test
    fun `四列两张横图 一行两横图`() {
        val rows = TagCodexGridLayout.rows(listOf(landscape, landscape), columns = 4)
        assertEquals(listOf(2, 2), spans(rows)[0])
    }

    @Test
    fun `三列两张横图 后面的竖图提上来填`() {
        // ⚠️ **第 ㊿e4 批改口径** ✓：原来期望"第二张横图换行、第一行只剩 2 格" ✗，
        //   现在**把后面那张竖图提上来** ✓（用户新口径 ✓）。
        val rows = TagCodexGridLayout.rows(listOf(landscape, landscape, portrait), columns = 3)
        assertEquals("第一行 = 横图 + 提上来的竖图", listOf(2, 1), spans(rows)[0])
        assertEquals("第二行 = 第二张横图", listOf(2), spans(rows)[1])
    }

    @Test
    fun `三列两横图 第一行留白`() {
        // 后面**没有**竖图可提 ⇒ 留白 ✓（用户：「格子占不完可留白」✓）
        val rows = TagCodexGridLayout.rows(listOf(landscape, landscape), columns = 3)
        assertTrue("没得填 ⇒ 留白", rows[0].slots.sumOf { it.span } < 3)
    }

    // ---------------------------------------------- 第 ㊿e4 批：提上来填充

    @Test
    fun `两格后面剩一格 把后面的竖图提上来填`() {
        // 用户原话：「还有一些异形图，占据两格后右边还有一格可以放图，
        //          做成将排在后面的竖图放到上面填充」
        // 3 列：横图(2格) + 横图(2格，放不下) + 竖图(1格) ⇒ **竖图提到第一行** ✓
        val rows = TagCodexGridLayout.rows(
            listOf(landscape, landscape, portrait),
            columns = 3,
        )
        assertEquals("第一行 = 横图 + 提上来的竖图", listOf(2, 1), spans(rows)[0])
        assertEquals("第二行 = 那张横图", listOf(2), spans(rows)[1])
        // ⚠️ **被提上来的那张不能重复出现** ✓
        val all = rows.flatMap { r -> r.slots.map { it.index } }.sorted()
        assertEquals("每个下标恰好一次", listOf(0, 1, 2), all)
    }

    @Test
    fun `提上来的竖图取自更后面`() {
        // 横图(2) + 横图 + 横图 + 竖图 ⇒ 最后那张竖图**跨过两张横图**被提上来
        val rows = TagCodexGridLayout.rows(
            listOf(landscape, landscape, landscape, portrait),
            columns = 3,
        )
        assertEquals("第一行 = 横图 + 竖图（取自第 4 位）", listOf(2, 1), spans(rows)[0])
        assertEquals("第一行第二个来自下标 3", 3, rows[0].slots[1].index)
    }

    @Test
    fun `后面全是横图 就留白`() {
        val rows = TagCodexGridLayout.rows(
            listOf(landscape, landscape, landscape),
            columns = 3,
        )
        // 第一行只剩 1 格、后面全是 2 格宽的横图 ⇒ 填不进 ⇒ 留白 ✓
        assertEquals("第一行只有一张横图", listOf(2), spans(rows)[0])
        assertEquals("没排满 ⇒ 留白", 2, rows[0].slots.sumOf { it.span })
    }

    @Test
    fun `四列 三张竖图加一张横图 横图放最后`() {
        // 3 竖图占 3 格、第 4 位是横图（要 2 格）⇒ 横图换行 ✓
        val rows = TagCodexGridLayout.rows(
            listOf(portrait, portrait, portrait, landscape),
            columns = 4,
        )
        assertEquals(listOf(1, 1, 1), spans(rows)[0])
        assertEquals(listOf(2), spans(rows)[1])
    }

    @Test
    fun `四列四张竖图`() {
        val rows = TagCodexGridLayout.rows(
            List(4) { portrait },
            columns = 4,
        )
        assertEquals(listOf(1, 1, 1, 1), spans(rows)[0])
    }

    @Test
    fun `竖图判定 真实值`() {
        assertTrue("832x1216 是竖图", TagCodexGridLayout.isTall(832, 1216))
        assertFalse("1216x832 不是竖图", TagCodexGridLayout.isTall(1216, 832))
        assertTrue("取不到尺寸当竖图", TagCodexGridLayout.isTall(0, 0))
    }

    @Test
    fun `真实数据前 40 条里横图必须占两格`() {
        // 实测分布：前 40 条里有若干 1.451 的横图 ⇒ **必须**出现 span=2
        //（旧阈值 0.6 下这里**一张都不会是 2 格** ⇒ 这正是用户看到"没改变"的原因 ✓）
        val ratios = listOf(
            portrait, portrait, portrait, landscape, landscape, portrait,
            landscape, landscape, landscape, portrait, portrait, landscape,
        )
        // 上面 12 条里横图 = 6 张（下标 3、4、6、7、8、11 ✓）
        val expected = ratios.count { it >= TagCodexGridLayout.TALL_RATIO }
        assertEquals("横图张数 = 6", 6, expected)
        val rows = TagCodexGridLayout.rows(ratios, columns = 3)
        val twoCount = rows.sumOf { r -> r.slots.count { it.span == 2 } }
        assertEquals("横图张数 = 占2格的张数", expected, twoCount)
    }

    @Test
    fun `不丢条目 每张只出现一次`() {
        val ratios = listOf(portrait, landscape, landscape, portrait, portrait, landscape, portrait)
        val rows = TagCodexGridLayout.rows(ratios, columns = 3)
        val seen = rows.flatMap { r -> r.slots.map { it.index } }.sorted()
        assertEquals("每个下标恰好出现一次", ratios.indices.toList(), seen)
    }
}
