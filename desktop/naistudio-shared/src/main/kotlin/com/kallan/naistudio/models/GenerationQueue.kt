package com.kallan.naistudio.models

/**
 * **逐格生成队列**（高级漫画模式的核心节奏）—— 用户 2026-09-26 原话：
 * 「**队列跑，确定完格子后按照序号每次一张跑完一页**」。
 *
 * 也就是：格子定完 → 队列从**第 1 格**开始，**一次只跑一张**，跑完自动下一个，直到整页完成 ✓。
 * 这个类**只管顺序与状态**：不碰网络、不碰 UI、不认识 AppState ✓ —— 所以能单测、也方便
 * 后面接"跑前报总消耗并确认"（[totalCostHint] 那种汇总由调用方填 ✓）。
 *
 * 状态机：
 * ```
 * PENDING ──▶ RUNNING ──▶ DONE
 *                  └────▶ FAILED ──(重试)──▶ PENDING
 * PENDING ──(跳过)──▶ SKIPPED
 * ```
 *
 * ⚠️ 失败策略是**可配**的（用户还没定"跑一半失败怎么办" ✗）：
 *  · [stopOnFailure] = true（**默认** ✓）：一格失败就**停下等你** —— 生成是要花钱的 ✓，
 *    默默往下跑等于把钱花在一个已经出问题的页上 ✗；
 *  · false：跳过失败格继续跑完，最后统一报哪几格失败 ✓。
 */
class GenerationQueue(
    idsInOrder: List<String>,
    /** 一格失败时是否停下等人工（默认停 ✓，见类注释）。 */
    val stopOnFailure: Boolean = true,
) {

    enum class State { PENDING, RUNNING, DONE, FAILED, SKIPPED }

    data class Item(
        val id: String,
        val order: Int,
        var state: State = State.PENDING,
        /** 失败原因（给界面显示 ✓）。 */
        var error: String? = null,
    )

    val items: List<Item> = idsInOrder.mapIndexed { index, id -> Item(id = id, order = index) }

    /** 全部跑完（含失败 / 跳过）就算结束 —— 界面用它决定"队列视图"要不要收起来 ✓。 */
    val isFinished: Boolean
        get() = items.none { it.state == State.PENDING || it.state == State.RUNNING }

    /** 当前该跑的那一格（**按序号取第一个 PENDING** ✓）；没有就 `null` ✓。 */
    fun nextPending(): Item? = items.firstOrNull { it.state == State.PENDING }

    /** 有没有格子正在跑（跑的时候不许再起第二张 ✗ —— 用户要的是"一次一张"✓）。 */
    val isBusy: Boolean
        get() = items.any { it.state == State.RUNNING }

    /**
     * **执行器该用的那个入口** ✓：没人在跑时才给出下一格，否则 `null` ✓。
     * （`nextPending()` 是"队列里下一个待跑的"，不看有没有人在跑 —— 两者分开，
     *   免得调用方拿它写出并发跑两张的 bug ✗。）
     */
    fun nextToRun(): Item? = if (isBusy) null else nextPending()

    /** 界面显示的进度：`(已完成数, 总数)`（失败 / 跳过也算"已处理" ✓）。 */
    fun progress(): Pair<Int, Int> =
        items.count { it.state == State.DONE || it.state == State.FAILED || it.state == State.SKIPPED } to items.size

    /** 标记开始跑（幂等：不是 PENDING 就不动 ✓）。 */
    fun markRunning(id: String) {
        items.firstOrNull { it.id == id && it.state == State.PENDING }?.state = State.RUNNING
    }

    /** 这一格成功了 ✓。 */
    fun markDone(id: String) {
        items.firstOrNull { it.id == id }?.apply {
            state = State.DONE
            error = null
        }
    }

    /**
     * 这一格失败了 ✗。
     * @return 是否还能继续跑下一格 —— 配了 [stopOnFailure] 就返回 false（调用方据此停手 ✓）。
     */
    fun markFailed(id: String, reason: String? = null): Boolean {
        val item = items.firstOrNull { it.id == id } ?: return !stopOnFailure
        item.state = State.FAILED
        item.error = reason
        return !stopOnFailure
    }

    /** 把某一格退回待跑（"重试这一格" ✓）。 */
    fun retry(id: String) {
        items.firstOrNull { it.id == id }?.apply {
            state = State.PENDING
            error = null
        }
    }

    /** 把还没跑的都标记成跳过（用户按了"停" ✓）。 */
    fun skipRemaining() {
        items.filter { it.state == State.PENDING }.forEach { it.state = State.SKIPPED }
    }

    /** 整条队列复位（重跑整页 ✓）。 */
    fun resetAll() {
        items.forEach {
            it.state = State.PENDING
            it.error = null
        }
    }

    /** 失败清单（跑完给用户一句"第 3、7 格失败了" ✓）。 */
    fun failedIds(): List<String> = items.filter { it.state == State.FAILED }.map { it.id }
}
