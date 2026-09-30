package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 无限画布**撤销 / 重做栈**的判据（`docs/72` ✓）。
 *
 * 这一族钉的是四条"错了会**悄悄弄坏用户的东西**"的事 ✓：
 *  1. 撤销回到的**是上一步**（不是两步也不是原地 ✗）；
 *  2. 撤销再提交 ⇒ **重做线断掉** ✓，断掉的那些快照要**返回去删** ✓（不然盘上留一串回不去的图 ✗）；
 *  3. 超容量 ⇒ 挤掉**最老**的 ✓（挤掉最新的话，刚撤一步就没得撤了 ✗）；
 *  4. **原点跟着条目走** ✓（往左 / 上长过的画布还原时原点不能丢 ✗，丢了文档坐标整体错位 ✗）。
 */
class InfiniteCanvasHistoryTest {

    private fun entry(name: String, originX: Int = 0, originY: Int = 0) =
        InfiniteCanvasHistory.Entry("/tmp/$name.png", originX, originY)

    @Test
    fun `撤销回到上一步、重做回到撤销前那一步`() {
        val history = InfiniteCanvasHistory(capacity = 4)
        val a = entry("a")
        val b = entry("b")
        val c = entry("c")
        // a → b → c（提交的一直是"上一步" ✓）
        assertTrue(history.commit(a).isEmpty())
        assertTrue(history.commit(b).isEmpty())
        assertFalse(history.canRedo)

        val backToB = history.undo(c)
        assertEquals(b, backToB?.restore)
        assertTrue(backToB!!.evicted.isEmpty())
        assertTrue(history.canUndo)
        assertTrue(history.canRedo)

        val backToA = history.undo(b)
        assertEquals(a, backToA?.restore)
        assertFalse(history.canUndo)

        val forwardToB = history.redo(a)
        assertEquals(b, forwardToB?.restore)
        assertEquals(c, history.redo(b)?.restore)
        assertFalse(history.canRedo)
        assertNull(history.redo(c))
    }

    @Test
    fun `空栈上撤销与重做 —— 返回 null，不抛`() {
        val history = InfiniteCanvasHistory()
        assertNull(history.undo(entry("a")))
        assertNull(history.redo(entry("a")))
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun `撤销之后再提交 —— 重做线断掉，断掉的条目返回去删`() {
        val history = InfiniteCanvasHistory(capacity = 4)
        history.commit(entry("a"))
        history.commit(entry("b"))
        history.undo(entry("c")) // 回到 b，c 进了重做栈
        assertEquals(1, history.redoDepth)

        val evicted = history.commit(entry("b")) // 分叉：b → d
        assertFalse(history.canRedo)
        assertEquals(listOf(entry("c")), evicted)
    }

    @Test
    fun `超容量 —— 挤掉最老的那一步`() {
        val history = InfiniteCanvasHistory(capacity = 2)
        history.commit(entry("a"))
        history.commit(entry("b"))
        val evicted = history.commit(entry("c")) // 栈里 a,b + c ⇒ 超过 2 挤掉 a
        assertEquals(listOf(entry("a")), evicted)
        assertEquals(2, history.undoDepth)
        assertEquals(entry("c"), history.undo(entry("d"))?.restore)
        assertEquals(entry("b"), history.undo(entry("c"))?.restore)
        assertFalse(history.canUndo)
    }

    @Test
    fun `重做也不会让撤销栈超容量`() {
        val history = InfiniteCanvasHistory(capacity = 1)
        history.commit(entry("a"))
        history.undo(entry("b")) // 回到 a，b 进重做栈
        val swap = history.redo(entry("a"))
        assertEquals(entry("b"), swap?.restore)
        assertEquals(1, history.undoDepth)
        assertTrue(swap!!.evicted.isEmpty())
    }

    @Test
    fun `原点跟着条目走`() {
        val history = InfiniteCanvasHistory(capacity = 4)
        val leftGrown = entry("left", originX = -512, originY = -1024)
        history.commit(leftGrown)
        val swap = history.undo(entry("now"))
        assertEquals(-512, swap?.restore?.originX)
        assertEquals(-1024, swap?.restore?.originY)
    }

    @Test
    fun `clear 把两栈都倒出来给调用方删`() {
        val history = InfiniteCanvasHistory(capacity = 4)
        history.commit(entry("a"))
        history.commit(entry("b"))
        history.undo(entry("c"))
        val all = history.clear()
        assertEquals(setOf(entry("a"), entry("c")), all.toSet())
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }
}
