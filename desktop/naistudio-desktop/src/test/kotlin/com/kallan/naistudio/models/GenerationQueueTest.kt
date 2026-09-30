package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 逐格生成队列的钉子（用户 2026-09-26：「**队列跑，确定完格子后按照序号每次一张跑完一页**」✓）。
 * 顺带钉住"失败默认停下"这条默认口径（用户还没定 ✗，我按'花钱的事宁可停下'处理 ✓）。
 */
class GenerationQueueTest {

    @Test
    fun runs_in_order_one_at_a_time() {
        val q = GenerationQueue(listOf("p1", "p2", "p3"))
        assertEquals("p1", q.nextToRun()?.id)
        q.markRunning("p1")
        // 关键口径：**正在跑的时候就别再给下一格** ✓（"一次一张"✓）——
        // `nextPending()` 仍会看到 p2（那是"队列里下一个待跑的"），执行器要用 `nextToRun()` ✓
        assertNull(q.nextToRun())
        assertEquals("p2", q.nextPending()?.id)
        q.markDone("p1")
        assertEquals("p2", q.nextToRun()?.id)
        q.markRunning("p2"); q.markDone("p2")
        assertEquals("p3", q.nextToRun()?.id)
        q.markRunning("p3"); q.markDone("p3")
        assertNull(q.nextToRun())
        assertTrue(q.isFinished)
    }

    @Test
    fun progress_counts_failed_and_skipped_as_handled() {
        val q = GenerationQueue(listOf("a", "b", "c", "d"))
        q.markRunning("a"); q.markDone("a")
        q.markRunning("b"); q.markFailed("b", "网络超时")
        assertEquals(2 to 4, q.progress())
        q.skipRemaining()
        assertEquals(4 to 4, q.progress())
        assertTrue(q.isFinished)
    }

    @Test
    fun failure_stops_by_default() {
        val q = GenerationQueue(listOf("a", "b"), stopOnFailure = true)
        q.markRunning("a")
        val keepGoing = q.markFailed("a", "501")
        assertFalse("默认应当停下等人工（不默默继续花钱 ✗）", keepGoing)
        assertEquals(listOf("a"), q.failedIds())
        assertEquals("b", q.nextPending()?.id)           // 状态上 b 仍是 PENDING（等用户决定 ✓）
    }

    @Test
    fun can_be_configured_to_skip_and_continue() {
        val q = GenerationQueue(listOf("a", "b"), stopOnFailure = false)
        q.markRunning("a")
        assertTrue(q.markFailed("a") == true)
        assertEquals("b", q.nextPending()?.id)
    }

    @Test
    fun retry_puts_a_failed_item_back_in_line() {
        val q = GenerationQueue(listOf("a", "b"), stopOnFailure = true)
        q.markRunning("a"); q.markFailed("a", "x")
        assertFalse(q.isFinished)
        q.retry("a")
        assertEquals("a", q.nextPending()?.id)           // 退回队首（它是序号最小的 PENDING ✓）
        assertNull(q.items.first().error)
    }

    @Test
    fun reset_all_clears_everything() {
        val q = GenerationQueue(listOf("a", "b"))
        q.markRunning("a"); q.markDone("a")
        q.markRunning("b"); q.markFailed("b", "x")
        q.resetAll()
        assertEquals(0 to 2, q.progress())
        assertTrue(q.failedIds().isEmpty())
        assertEquals("a", q.nextPending()?.id)
    }
}
