package com.kallan.naistudio.models

/**
 * **无限画布的撤销 / 重做栈**（纯逻辑 ✓ 不碰平台 API ✓ 可单测 ✓）。
 *
 * ## 为什么必须有它（不是"锦上添花" ✓）
 *
 * 一次聚焦生成是**花钱**的 ✓，而"贴回画布"是**破坏性**的 ✓：贴错了 / 生成跑偏了，
 * 画布上那一块就定了 ✗。没有撤销的话，用户唯一的出路是**再花一次钱**把那一块盖回去 ✓ ——
 * 而且未必盖得回来（模型不是确定性的 ✓）。所以撤销这一条是**这一档能不能放心用**的分界线 ✓。
 *
 * ## 栈里存什么
 *
 * 每一步存的是**画布快照文件 + 原点**（[Entry] ✓）——
 *  · 文件：`files/infinite/canvas-<时间戳>.png`（`AppState.encodeInfiniteCanvas` 写的 ✓）；
 *  · 原点：往左 / 上拓展过之后 `originX/originY` 不是 0 ✓，不一起记就会**文档坐标整体错位** ✗。
 *
 * ⚠️ 本类**只记账、不碰文件** ✗：要删哪些文件由调用方按返回值去删 ✓
 * （纯逻辑才能单测 ✓；再说"哪个目录里的文件归谁"是调用方的口径 ✓，和漫画那边同一条 ✓）。
 *
 * ## 三条口径
 *
 *  1. **容量有限**（默认 [DEFAULT_CAPACITY] ✓）：一张 4096² 的 PNG 十几 MB ✓，
 *     栈无限长就是拿磁盘换后悔药 ✗ ⇒ 挤出去的**返回给调用方去删** ✓；
 *  2. **撤销之后再改 ⇒ 重做线断掉** ✓（和所有编辑器一个口径 ✓）：那条分支上的快照一并
 *     **返回去删** ✓，不然盘上会留一串永远回不去的图 ✗；
 *  3. **`commit` 的是"上一步的状态"** ✓（不是新状态 ✓）—— 调用方在新状态**已经写盘之后**调它 ✓。
 */
class InfiniteCanvasHistory(val capacity: Int = DEFAULT_CAPACITY) {

    /** 一步画布状态：快照文件 + 那张画布的原点 ✓。 */
    data class Entry(val path: String, val originX: Int, val originY: Int)

    /** 撤销 / 重做一次的结果：**要恢复的那一步** + **要删盘的那几条** ✓。 */
    data class Swap(val restore: Entry, val evicted: List<Entry>)

    private val undoStack = ArrayDeque<Entry>()
    private val redoStack = ArrayDeque<Entry>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoDepth: Int get() = undoStack.size
    val redoDepth: Int get() = redoStack.size

    /**
     * **提交一步**：把"刚才那一步的状态" [previous] 压进撤销栈 ✓，重做线清空 ✓。
     *
     * @return 要删盘的条目（清掉的重做线 + 超出容量的最老那几条 ✓）；没有就返回空表 ✓
     */
    fun commit(previous: Entry): List<Entry> {
        val evicted = ArrayList<Entry>(redoStack)
        redoStack.clear()
        undoStack.addLast(previous)
        while (undoStack.size > capacity) evicted.add(undoStack.removeFirst())
        return evicted
    }

    /**
     * 撤销：把 [current] 挪进重做栈 ✓，返回**要恢复的那一步** ✓；没得撤 ⇒ null ✓。
     */
    fun undo(current: Entry): Swap? {
        val target = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        return Swap(target, enforceCapacity())
    }

    /**
     * 重做：把 [current] 挪回撤销栈 ✓，返回**要恢复的那一步** ✓；没得重做 ⇒ null ✓。
     */
    fun redo(current: Entry): Swap? {
        val target = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        return Swap(target, enforceCapacity())
    }

    /** 清空两栈 ✓（换画布 / 以新图重开时调 ✓）—— 条目**返回给调用方删** ✓。 */
    fun clear(): List<Entry> {
        val all = ArrayList<Entry>(undoStack.size + redoStack.size)
        all.addAll(undoStack)
        all.addAll(redoStack)
        undoStack.clear()
        redoStack.clear()
        return all
    }

    /** 撤销栈超容量时挤掉最老的 ✓（重做栈**不设**容量：它天然不会超过撤销栈的深度 ✓）。 */
    private fun enforceCapacity(): List<Entry> {
        val evicted = ArrayList<Entry>(0)
        while (undoStack.size > capacity) evicted.add(undoStack.removeFirst())
        return evicted
    }

    companion object {
        /** 撤销 / 重做各留几步 ✓（4 步 ≈ 最近四次拓展 ✓ —— 再多就是拿磁盘换后悔药 ✗）。 */
        const val DEFAULT_CAPACITY = 4
    }
}
