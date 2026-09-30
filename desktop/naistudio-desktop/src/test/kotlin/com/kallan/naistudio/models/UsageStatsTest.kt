package com.kallan.naistudio.models

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **每日使用统计**的判据（用户 2026-09-23：「记录每天跑图的数量、消耗的积分、tag 的字数」✓，`docs/76` ✓）。
 *
 * 这一族钉的是三条"错了会**悄悄把用户的数据搞脏**"的事 ✓：
 *  1. 同一天**累加**（不是追加 ✓，也不是覆盖 ✓）；
 *  2. 序列化**往返一致** ✓、坏条目**跳过而不抛** ✓（它旁边还有历史 / 风格库那些要紧东西 ✓）；
 *  3. 格子等级只由**张数**决定 ✓（0/1/2-3/4-7/≥8 ✓）—— 用户口径「越多越绿」✓。
 */
class UsageStatsTest {

    @Test
    fun `同一天累加 —— 张数、点数、字数各加各的`() {
        val stats = UsageStats()
            .plus("2026-09-23", images = 2, anlas = 30, tagChars = 120)
            .plus("2026-09-23", images = 3, anlas = 15, tagChars = 80)
        val day = stats.of("2026-09-23")
        assertEquals(5, day?.images)
        assertEquals(45, day?.anlas)
        assertEquals(200, day?.tagChars)
    }

    @Test
    fun `不同天各记各的`() {
        val stats = UsageStats()
            .plus("2026-09-23", images = 1, anlas = 10, tagChars = 50)
            .plus("2026-09-22", images = 4, anlas = 40, tagChars = 90)
        assertEquals(1, stats.of("2026-09-23")?.images)
        assertEquals(4, stats.of("2026-09-22")?.images)
        assertNull(stats.of("2026-09-21"))
    }

    @Test
    fun `负数不记账 —— 免得把累计值往回扣`() {
        val stats = UsageStats().plus("2026-09-23", images = -3, anlas = -5, tagChars = -9)
        assertEquals(0, stats.of("2026-09-23")?.images)
        assertEquals(0, stats.of("2026-09-23")?.anlas)
        assertEquals(0, stats.of("2026-09-23")?.tagChars)
    }

    @Test
    fun `序列化往返一致`() {
        val stats = UsageStats()
            .plus("2026-09-23", images = 2, anlas = 30, tagChars = 120)
            .plus("2026-09-22", images = 7, anlas = 0, tagChars = 12)
        val decoded = UsageStats.decode(stats.encode())
        assertEquals(stats.days, decoded.days)
    }

    @Test
    fun `坏条目跳过 —— 不抛异常`() {
        val decoded = UsageStats.decode("2026-09-23:2,30,120;垃圾;2026-09-22:1;2026-09-21:a,b,c;;")
        // 好条目照收（`2,30,120` ⇒ 2 张 ✓；这里原来写成 1，是我自己写错的期望值 ✗）
        assertEquals(2, decoded.of("2026-09-23")?.images)
        assertEquals(30, decoded.of("2026-09-23")?.anlas)
        // 三条坏条目（没有冒号 / 字段不够 / 数字解不出来）**全部跳过** ✓ 且**不抛** ✓
        assertNull(decoded.of("2026-09-22"))
        assertNull(decoded.of("2026-09-21"))
    }

    @Test
    fun `空串与 null —— 都读成空表`() {
        assertTrue(UsageStats.decode(null).days.isEmpty())
        assertTrue(UsageStats.decode("").days.isEmpty())
    }

    @Test
    fun `超过保留天数 —— 只留最近的`() {
        var stats = UsageStats()
        // 造 KEEP_DAYS + 10 天（日期用纯数字拼，字典序 == 时间序 ✓，与实现同一个口径 ✓）
        repeat(UsageStats.KEEP_DAYS + 10) { index ->
            stats = stats.plus("2026-01-01+$index", images = 1, anlas = 1, tagChars = 1)
        }
        assertEquals(UsageStats.KEEP_DAYS, stats.days.size)
    }

    @Test
    fun `格子等级 —— 只由张数决定`() {
        assertEquals(0, usageLevel(0))
        assertEquals(1, usageLevel(1))
        assertEquals(2, usageLevel(2))
        assertEquals(2, usageLevel(3))
        assertEquals(3, usageLevel(4))
        assertEquals(3, usageLevel(7))
        assertEquals(4, usageLevel(8))
        assertEquals(4, usageLevel(999))
    }

    @Test
    fun `格子图 —— 每列 7 格、最后一列含今天`() {
        val stats = UsageStats().plus("2026-09-23", images = 5, anlas = 1, tagChars = 1)
        val today = LocalDate.of(2026, 9, 23) // 周三
        val grid = usageGrid(stats, today, weeks = 53)
        assertEquals(53, grid.size)
        assertTrue("每一列都该是 7 格", grid.all { it.size == 7 })
        // 今天（周三）应该落在最后一列的第 3 格（周一在上 ✓）
        val lastColumn = grid.last()
        assertEquals("2026-09-23", lastColumn[2].date)
        assertEquals(3, lastColumn[2].level) // 5 张 ⇒ 3 级 ✓
        // 本周剩下的那几天是**未来** ✓（日期比今天大 ✓），等级 0 ✓
        assertEquals("2026-09-24", lastColumn[3].date)
        assertEquals(0, lastColumn[3].level)
        // 第一列第一格 = 53 周前的周一 ✓
        assertEquals("2025-09-22", grid.first().first().date)
    }

    /**
     * ⚠️ 用户 2026-09-23 改了口径：「**统计是最近 53 周，我只要最近 1 个月**」✓ ——
     * 界面从 `usageGrid(weeks = 53)` 换成 `usageMonth`（30 格 ✓）。这条钉住三件事：
     *  1. **正好 30 格** ✓（多一天少一天都会被看出来 ✓）；
     *  2. **从早到晚** ✓、最后一格是**今天** ✓（写反了格子图就整个反着画 ✓）；
     *  3. 哪天没图也**照样占一格**（0 级 ✓）—— 空格子的意思是"那天没跑" ✓，
     *     省掉就变成"那天不存在" ✗（用户看的是"我这个月哪天在跑" ✓）。
     */
    @Test
    fun `最近一个月 —— 正好 30 格、最后一格是今天`() {
        val stats = UsageStats().plus("2026-09-23", images = 9, anlas = 1, tagChars = 1)
        val today = LocalDate.of(2026, 9, 23)
        val month = usageMonth(stats, today)
        assertEquals(30, month.size)
        assertEquals("2026-09-23", month.last().date)
        assertEquals("2026-08-25", month.first().date) // 今天往回数 29 天 ✓
        assertEquals(4, month.last().level) // 9 张 ⇒ 4 级 ✓
        // 没记录的那几天照样在，且是 0 级 ✓
        assertEquals("2026-09-22", month[month.size - 2].date)
        assertEquals(0, month[month.size - 2].level)
        // 日期必须是**严格递增**的（排反了最容易看不出来 ✓）
        assertEquals(month.map { it.date }.sorted(), month.map { it.date })
    }
}
