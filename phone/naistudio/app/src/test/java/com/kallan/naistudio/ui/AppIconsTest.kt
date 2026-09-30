package com.kallan.naistudio.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自绘图标的结构约束。
 *
 * ## 为什么会有这个测试
 *
 * 用户 2026-09-16 报：「关锁时锁的上半部分变实心了」。
 *
 * 根因是 `LockClosedIcon` 用的是 Material 官方 `lock` 的路径，而那条路径是**四段闭合子轮廓**：
 * 外轮廓 / 钥匙孔 / 锁体挖空 / **锁梁内圈**。抄漏最后那段「锁梁内圈」之后，
 * 外弧到锁体之间没有东西去挖，整个上半部分就被填成一坨实心 ——
 * 而**开锁那条没这个问题**，因为它的内圈是并进主轮廓一笔画完的。
 *
 * 这种错编译器不会有任何意见、屏幕上也"看着像一把锁"，只有盯着看才发现不对。
 * 所以这里把"关锁必须比开锁多一段闭合子轮廓"钉成断言 —— 删掉那段内圈，这条就红。
 */
class AppIconsTest {

    /** 一个图标里所有子路径的节点（把所有 path 的 nodes 摊平）。 */
    private fun nodesOf(icon: ImageVector): List<PathNode> {
        val paths = mutableListOf<VectorPath>()
        fun walk(node: VectorNode) {
            when (node) {
                is VectorPath -> paths += node
                is VectorGroup -> node.forEach { walk(it) }
            }
        }
        walk(icon.root)
        return paths.flatMap { it.pathData }
    }

    private fun closeCount(icon: ImageVector): Int =
        nodesOf(icon).count { it is PathNode.Close }

    @Test
    fun `关锁比开锁多一段闭合子轮廓 —— 多的那段就是锁梁内圈`() {
        val closed = closeCount(LockClosedIcon)
        val open = closeCount(LockOpenIcon)

        // 开锁：主轮廓（内圈并进去了）+ 钥匙孔 + 锁体挖空 = 3 段
        assertEquals("开锁的闭合子轮廓数变了，先确认图标本身没问题", 3, open)
        // 关锁：上面那 3 段之外，**还要有一段锁梁内圈** = 4 段
        assertEquals(
            "关锁少了「锁梁内圈」那段子轮廓 —— 表现就是锁的上半部分被填成实心。" +
                "见 LockClosedIcon 的注释（那是官方 lock 路径的第 4 段）。",
            4,
            closed,
        )
        assertTrue(closed > open)
    }

    @Test
    fun `锁图标不是空壳`() {
        // 防"有人为了过测试把路径清空"
        assertTrue(nodesOf(LockClosedIcon).size > 20)
        assertTrue(nodesOf(LockOpenIcon).size > 20)
    }
}
