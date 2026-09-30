package com.kallan.naistudio.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对话气泡轮廓的钉子（用户 2026-09-26：「内置一个图画框，选定气泡样式后直接拖到相应位置调大小」）。
 *
 * 不去比路径的每个点（那太脆 ✗），只钉**外接尺寸**与**尾巴方向**这两件"用户看得出来"的事 ✓。
 */
class BubbleShapesTest {

    private fun boundsOf(style: BubbleStyle, w: Float, h: Float, tail: Offset? = null)
        = bubblePath(style, Size(w, h), tail).getBounds()

    @Test
    fun round_bubble_covers_its_box() {
        val b = boundsOf(BubbleStyle.ROUND, 200f, 120f)
        assertEquals(0f, b.left, 1.5f)
        assertEquals(0f, b.top, 1.5f)
        assertEquals(200f, b.right, 1.5f)
        assertEquals(120f, b.bottom, 1.5f)
    }

    @Test
    fun square_caption_has_no_tail_even_when_target_given() {
        // 旁白框（RECT）按设计**没有尾巴** ✓ —— 给了目标点也不该冒出来
        val b = boundsOf(BubbleStyle.RECT, 180f, 90f, tail = Offset(90f, 260f))
        assertTrue("旁白框不该长出尾巴", b.bottom <= 91f)
    }

    @Test
    fun tail_reaches_towards_the_target_below() {
        val b = boundsOf(BubbleStyle.ROUND, 200f, 120f, tail = Offset(60f, 250f))
        assertTrue("尾巴应当把下边界拉到目标附近", b.bottom > 200f)
        assertTrue("尾巴不该乱改左右边界", b.left >= -1.5f && b.right <= 201.5f)
    }

    @Test
    fun tail_inside_the_bubble_is_ignored() {
        // 目标点在泡内 → 不画尾（否则是个怪缺口 ✗）
        val b = boundsOf(BubbleStyle.ROUND, 200f, 120f, tail = Offset(100f, 60f))
        assertTrue(b.bottom <= 121.5f)
    }

    @Test
    fun thought_and_shout_stay_within_a_sane_envelope() {
        // 云朵 / 锯齿的起伏往外鼓，但幅度受 depth 控制（短边的 ~7%~10% ✓），不该飞出去 ✗
        for (style in listOf(BubbleStyle.THOUGHT, BubbleStyle.SHOUT)) {
            val b = boundsOf(style, 240f, 160f)
            assertTrue("$style 左边界越界", b.left >= -40f)
            assertTrue("$style 上边界越界", b.top >= -40f)
            assertTrue("$style 右边界越界", b.right <= 280f)
            assertTrue("$style 下边界越界", b.bottom <= 200f)
        }
    }

    @Test
    fun degenerate_size_returns_empty_path() {
        val b = boundsOf(BubbleStyle.ROUND, 0f, 0f)
        assertTrue(b.isEmpty)
    }
}
