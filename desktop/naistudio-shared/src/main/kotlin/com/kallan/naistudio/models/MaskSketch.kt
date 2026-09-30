package com.kallan.naistudio.models

/**
 * 遮罩草图：**笔迹列表 + 由它渲染出的逐像素遮罩**，两者始终一致。
 *
 * 为什么要单独一个类：涂改是"画上去立即生效"的（没有确定按钮），所以必须能随时
 * **撤销上一笔**。做法是保留笔迹列表，撤销 = 丢掉最后一笔再重放；涂抹过程中则就地盖章，不重放。
 *
 * 笔迹同时供**界面直接画**（Compose Canvas 矢量描边，笔宽 = 笔刷像素 ×显示缩放），
 * 这样涂画过程中完全不需要重建位图 —— 位图只在导出 PNG 时才建一次。
 *
 * 纯 Kotlin（不碰 android.graphics），撤销/重放行为可写 JVM 单测。
 */
class MaskSketch(val width: Int, val height: Int) {

    private var mask: PixelMask = PixelMask(width, height, ByteArray(width * height))

    /** 已提交的笔迹（含擦除笔）。撤销就是从这里去掉最后一条。 */
    private val strokes = mutableListOf<MaskStroke>()

    private var active: MaskStroke? = null
    private var activePoints = mutableListOf<MaskPoint>()

    val pixels: ByteArray get() = mask.pixels
    val selectedCount: Int get() = mask.selectedCount
    val isEmpty: Boolean get() = mask.isEmpty()
    val canUndo: Boolean get() = strokes.isNotEmpty()

    /** 是否有正在画的一笔（手指还没抬起来）。 */
    val isDrawing: Boolean get() = active != null

    /**
     * 界面要画的笔迹：**已提交的 + 正在画的这一笔**。
     * 正在画的那一笔还没进 [strokes]，但屏幕上必须立刻看得到，否则手感是"延迟出墨"。
     */
    fun visibleStrokes(): List<MaskStroke> {
        val current = active
        if (current == null || activePoints.isEmpty()) return strokes.toList()
        return strokes + current.copy(points = activePoints.toList())
    }

    /** 开始一笔。同一支笔的参数在这一笔内固定（中途改笔刷不影响已落的点）。 */
    fun beginStroke(erasing: Boolean, square: Boolean, brushPixels: Int) {
        endStroke()
        active = MaskStroke(erasing = erasing, square = square, brushPixels = brushPixels)
        activePoints = mutableListOf()
    }

    /** 画到某个点。没调用 [beginStroke] 时是空操作。 */
    fun extend(point: MaskPoint) {
        val stroke = active ?: return
        val previous = activePoints.lastOrNull()
        MaskBrush.stampInto(mask.pixels, width, height, previous, point, stroke)
        activePoints.add(point)
    }

    /** 收笔：把这一笔提交进笔迹列表（之后可撤销）。 */
    fun endStroke() {
        val stroke = active ?: return
        active = null
        if (activePoints.isEmpty()) return
        strokes.add(stroke.copy(points = activePoints.toList()))
        activePoints = mutableListOf()
    }

    /** 撤销上一笔。 */
    fun undo(): Boolean {
        endStroke()
        if (strokes.isEmpty()) return false
        strokes.removeAt(strokes.lastIndex)
        rebuild()
        return true
    }

    /** 清空（撤销历史也一起清掉，因为已经没意义了）。 */
    fun clear() {
        active = null
        activePoints = mutableListOf()
        strokes.clear()
        rebuild()
    }

    /** 给请求用的不可变快照。 */
    fun snapshot(): PixelMask = mask.copy()

    private fun rebuild() {
        val fresh = PixelMask(width, height, ByteArray(width * height))
        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            var previous: MaskPoint? = null
            for (point in stroke.points) {
                MaskBrush.stampInto(fresh.pixels, width, height, previous, point, stroke)
                previous = point
            }
        }
        mask = fresh
    }
}
