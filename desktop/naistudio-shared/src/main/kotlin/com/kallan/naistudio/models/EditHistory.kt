package com.kallan.naistudio.models

/**
 * 一次编辑的**脏矩形差量**：只记那块矩形在改之前和改之后的像素。
 *
 * 为什么不存整图：画布是 ARGB 4 字节/像素，832×1216 一张就是 4 MB，
 * 一步一张谁都受不了。画一笔真正碰到的区域通常只有笔刷那一圈，差量是几十 KB 级。
 */
class PixelDelta(
    val rect: EditRect,
    val before: IntArray,
    val after: IntArray,
) {
    /** 这条记录占多少字节（历史栈的预算按它算）。 */
    val bytes: Int get() = (before.size + after.size) * 4
}

/**
 * **画布编辑器的历史栈**（撤销 / 重做）。
 *
 * ## 一次"编辑"= 一组差量
 *
 * 涂一笔在屏幕上会拆成几十段小线段（每次指针移动一段），但用户眼里的"一步"是**整笔**。
 * 所以有 [beginGroup] / [endGroup]：组里 push 的所有差量在撤销时**一起**回退、
 * 重做时一起重放。不这样分组的话，撤销一次只会退掉一小段线段，手感是坏的。
 *
 * ## 顺序
 *
 * 撤销要**倒着**应用（后画的先退），重做要**正着**应用 ——
 * 所以 [undo] / [redo] 返回的是 `List<PixelDelta>`，由调用方按返回顺序处理即可，
 * 每个差量自带"该写 before 还是 after"的语义（撤销写 before、重做写 after）。
 *
 * ## 上限
 *
 * 两个闸门同时管：**总字节**（[maxBytes]）与**步数**（[maxSteps]）。超了从**最老的**丢 ——
 * 用户想撤销的永远是刚才那几步，老早以前的丢掉不心疼。
 */
class EditHistory(
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
    private val maxSteps: Int = DEFAULT_MAX_STEPS,
) {

    /** 已提交的步骤，每个步骤 = 一组差量。队尾是最近一步。 */
    private val undoStack = ArrayDeque<MutableList<PixelDelta>>()
    private val redoStack = ArrayDeque<MutableList<PixelDelta>>()

    /** 每一步占的字节数，和 [undoStack] 一一对应（淘汰时要用）。 */
    private val undoBytes = ArrayDeque<Int>()

    private var totalBytes = 0
    private var openGroup: MutableList<PixelDelta>? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoDepth: Int get() = undoStack.size
    val redoDepth: Int get() = redoStack.size

    /** 开始攒一组（涂一笔按下时调）。已经在组里就先把上一组收口。 */
    fun beginGroup() {
        endGroup()
        openGroup = mutableListOf()
    }

    /** 往当前组里加一条差量（没开组就自动开一组，单次操作也能撤销）。 */
    fun push(delta: PixelDelta) {
        val group = openGroup ?: mutableListOf<PixelDelta>().also { openGroup = it }
        group.add(delta)
    }

    /** 收口当前组：空组直接丢掉（点了一下没改动就不该占一步）。 */
    fun endGroup() {
        val group = openGroup ?: return
        openGroup = null
        if (group.isEmpty()) return
        val stepBytes = group.sumOf { it.bytes }
        undoStack.addLast(group)
        undoBytes.addLast(stepBytes)
        totalBytes += stepBytes
        // 新的动作一旦发生，原来的"重做"就失效了（标准编辑器行为）
        redoStack.clear()
        evictIfNeeded()
    }

    /**
     * 撤销一步。返回要**倒序**应用的差量（写回 [PixelDelta.before]）；
     * 没得撤返回 null。
     */
    fun undo(): List<PixelDelta>? {
        endGroup()
        val group = undoStack.removeLastOrNull() ?: return null
        totalBytes -= undoBytes.removeLastOrNull() ?: 0
        redoStack.addLast(group)
        return group.asReversed()
    }

    /** 重做一步。返回要**正序**应用的差量（写回 [PixelDelta.after]）；没得重做返回 null。 */
    fun redo(): List<PixelDelta>? {
        endGroup()
        val group = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(group)
        val stepBytes = group.sumOf { it.bytes }
        undoBytes.addLast(stepBytes)
        totalBytes += stepBytes
        return group.toList()
    }

    fun clear() {
        openGroup = null
        undoStack.clear()
        redoStack.clear()
        undoBytes.clear()
        totalBytes = 0
    }

    /** 当前占用的字节数（诊断/测试用）。 */
    val bytesUsed: Int get() = totalBytes

    private fun evictIfNeeded() {
        while (undoStack.size > maxSteps || (totalBytes > maxBytes && undoStack.size > 1)) {
            undoStack.removeFirstOrNull() ?: break
            totalBytes -= undoBytes.removeFirstOrNull() ?: 0
        }
    }

    companion object {
        /** 默认预算：64 MB（一张 4K 图的量级，够几十笔了）。 */
        const val DEFAULT_MAX_BYTES = 64 shl 20

        /** 默认步数上限。 */
        const val DEFAULT_MAX_STEPS = 80
    }
}
