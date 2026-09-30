package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 遮罩草图的行为：**涂上即生效 + 随时撤销上一笔**（没有"确定"按钮，所以撤销必须可靠）。
 */
class MaskSketchTest {

    private fun newSketch() = MaskSketch(832, 1216)

    private fun MaskSketch.paintLine(from: MaskPoint, to: MaskPoint, brushPixels: Int = 32) {
        beginStroke(erasing = false, square = false, brushPixels = brushPixels)
        extend(from)
        extend(to)
        endStroke()
    }

    @Test
    fun `painted pixels take effect immediately`() {
        val sketch = newSketch()
        assertTrue(sketch.isEmpty)

        sketch.beginStroke(erasing = false, square = false, brushPixels = 32)
        sketch.extend(MaskPoint(0.5f, 0.5f))

        // 手指还没抬起来就已经生效了（这就是"涂上即生效"）
        assertTrue(sketch.selectedCount > 0)
        assertTrue(sketch.isDrawing)
        // 界面要画的那一笔也在里面（否则出墨会慢一拍）
        assertEquals(1, sketch.visibleStrokes().size)
    }

    @Test
    fun `undo removes the last stroke`() {
        val sketch = newSketch()
        sketch.paintLine(MaskPoint(0.2f, 0.3f), MaskPoint(0.6f, 0.3f))
        assertTrue(sketch.selectedCount > 0)
        assertTrue(sketch.canUndo)

        assertTrue(sketch.undo())
        assertEquals(0, sketch.selectedCount)
        assertFalse(sketch.canUndo)
        // 没有笔迹时再撤销是空操作，不能抛
        assertFalse(sketch.undo())
    }

    @Test
    fun `undo keeps earlier strokes`() {
        val sketch = newSketch()
        sketch.paintLine(MaskPoint(0.2f, 0.3f), MaskPoint(0.6f, 0.3f))
        val afterFirst = sketch.selectedCount
        sketch.paintLine(MaskPoint(0.2f, 0.8f), MaskPoint(0.6f, 0.8f))
        assertTrue(sketch.selectedCount > afterFirst)

        assertTrue(sketch.undo())
        assertEquals(afterFirst, sketch.selectedCount)
        // 撤销掉的那一笔确实没了：它所在的横线上不该有像素
        val row = (0.8f * sketch.height).toInt()
        val column = (0.3f * sketch.width).toInt()
        assertEquals(0, sketch.snapshot().valueAt(column, row))
    }

    @Test
    fun `erase strokes are undoable too`() {
        val sketch = newSketch()
        sketch.paintLine(MaskPoint(0.3f, 0.5f), MaskPoint(0.7f, 0.5f), brushPixels = 80)
        val painted = sketch.selectedCount

        sketch.beginStroke(erasing = true, square = false, brushPixels = 80)
        sketch.extend(MaskPoint(0.5f, 0.5f))
        sketch.endStroke()
        assertTrue("擦除应该减少像素", sketch.selectedCount < painted)

        assertTrue(sketch.undo())
        assertEquals(painted, sketch.selectedCount)
    }

    @Test
    fun `extend without begin is a no-op`() {
        val sketch = newSketch()
        sketch.extend(MaskPoint(0.5f, 0.5f))
        assertEquals(0, sketch.selectedCount)
        assertFalse(sketch.canUndo)
    }

    @Test
    fun `an empty stroke is not committed`() {
        val sketch = newSketch()
        sketch.beginStroke(erasing = false, square = false, brushPixels = 8)
        sketch.endStroke()
        assertFalse(sketch.canUndo)
    }

    @Test
    fun `clear wipes the mask and the undo history`() {
        val sketch = newSketch()
        sketch.paintLine(MaskPoint(0.2f, 0.2f), MaskPoint(0.8f, 0.8f))
        assertTrue(sketch.selectedCount > 0)

        sketch.clear()
        assertEquals(0, sketch.selectedCount)
        assertFalse(sketch.canUndo)
        assertTrue(sketch.isEmpty)
        assertTrue(sketch.visibleStrokes().isEmpty())
    }

    @Test
    fun `snapshot is a deep copy`() {
        val sketch = newSketch()
        sketch.paintLine(MaskPoint(0.2f, 0.2f), MaskPoint(0.8f, 0.8f))
        val snapshot = sketch.snapshot()
        val painted = snapshot.selectedCount
        assertTrue(painted > 0)

        sketch.clear()
        // 快照不受之后涂改影响（送请求用的就是它）
        assertEquals(painted, snapshot.selectedCount)
    }
}
