package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图层叠的钉子（用户 2026-09-26：「图层功能，**生的图都是一个新图层**，方便修改」✓）。
 */
class ComicLayerTest {

    private fun gen(id: String, panel: String) =
        ComicLayer(id, "生成层 $id", ComicLayer.Kind.GENERATED, panelId = panel, imagePath = "$id.png")

    @Test
    fun new_generation_lands_on_top() {
        val stack = ComicLayerStack()
        stack.add(ComicLayer("base", "底板", ComicLayer.Kind.BASE, imagePath = "page.png"))
        stack.addGenerated("g1", "p1", "g1.png")
        stack.addGenerated("g2", "p2", "g2.png")
        // 下标 0 = 最底下 ✓ → 底板在最下、后生成的盖在上面 ✓
        assertEquals(listOf("base", "g1", "g2"), stack.all.map { it.id })
        assertEquals("g2", stack.all.last().id)
    }

    @Test
    fun move_up_and_down_are_strict_about_the_edges() {
        val stack = ComicLayerStack(listOf(
            ComicLayer("a", "A", ComicLayer.Kind.BASE),
            ComicLayer("b", "B", ComicLayer.Kind.GENERATED),
        ))
        assertTrue(stack.moveUp("a"))
        assertEquals(listOf("b", "a"), stack.all.map { it.id })
        assertFalse("已经在最上面了，上移应当返回 false（别静默成功 ✗）", stack.moveUp("a"))
        assertTrue(stack.moveDown("a"))
        assertFalse("已经在最下面了", stack.moveDown("a"))
    }

    @Test
    fun visible_layers_skip_hidden_and_fully_transparent() {
        val stack = ComicLayerStack(listOf(
            ComicLayer("a", "A", ComicLayer.Kind.BASE),
            ComicLayer("b", "B", ComicLayer.Kind.GENERATED),
            ComicLayer("c", "C", ComicLayer.Kind.TEXT),
        ))
        stack.setVisible("b", false)
        stack.setOpacity("c", 0f)
        assertEquals(listOf("a"), stack.visibleLayers().map { it.id })
        assertTrue(stack.canFlatten())
        stack.setVisible("a", false)
        assertFalse("全隐藏 / 全透明时不该往下合成 ✓", stack.canFlatten())
    }

    @Test
    fun opacity_is_clamped() {
        val stack = ComicLayerStack(listOf(ComicLayer("a", "A", ComicLayer.Kind.BASE)))
        stack.setOpacity("a", 2.5f)
        assertEquals(1f, stack.byId("a")!!.opacity, 0.0001f)
        stack.setOpacity("a", -1f)
        assertEquals(0f, stack.byId("a")!!.opacity, 0.0001f)
    }

    @Test
    fun remove_and_lookup() {
        val stack = ComicLayerStack(listOf(gen("g1", "p1")))
        assertTrue(stack.remove("g1"))
        assertFalse(stack.remove("g1"))       // 删过了，第二次 false ✓
        assertTrue(stack.all.isEmpty())
    }

    @Test
    fun per_panel_lookup_helps_redo_one_cell() {
        // "改某一格不用重跑整页"✓ —— 按 panelId 能找到那一层 ✓
        val stack = ComicLayerStack()
        stack.addGenerated("g1", "p1", "g1.png")
        stack.addGenerated("g2", "p2", "g2.png")
        val forP1 = stack.all.filter { it.panelId == "p1" }
        assertEquals(1, forP1.size)
        assertEquals("g1", forP1.first().id)
    }
}
