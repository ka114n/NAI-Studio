package com.kallan.naistudio.models

/**
 * 画师超市网格的**摆放算法** ✓（第 ㊿e 批 2026-09-22 ✓，**双端同一份** ✓）。
 *
 * ## 用户口径（逐字，第 ㊿e 批定稿）
 *
 * 「**竖图属于一个宽度，长图属于两格宽度**」
 * 「**长图等比放大，占满格子，但要显示完整，格子占不完可留白**」
 * 「长图有时占一行，有时竖图占两格，长图还不放大到匹配格子」（这是**报的毛病** ✗）
 *
 * ## 规则
 *
 * 1. **竖图 = 1 格宽** ✓；**长图 = 2 格宽** ✓（用户原话 ✓）；
 * 2. 一行按列数排满就换行 ✓（长图的 **2 格**照常占位 ✓）；
 * 3. ⚠️ **长图横向占 2 格** ✓ —— 这里**不再有"上下摞"那套** ✗：
 *    上一版（㊿d）我按"塞不下就把长图摞起来"做 ✓，用户实测**不对** ✗
 *    （他要的是"竖图一格 / 长图两格"这种**固定宽度**口径 ✓）。
 * 4. **一行放不下就换行** ✓ —— 长图放不下的位置**留白** ✓，不硬塞 ✗。
 */
object TagCodexGridLayout {

    /** 一个网格项 ✓。 */
    data class Slot(
        /** 原条目在输入列表里的下标 ✓。 */
        val index: Int,
        /** **占几格宽** ✓（1 = 竖图 ✓，2 = 长图 ✓）。 */
        val span: Int,
    )

    /** 一行 ✓。 */
    data class Row(val slots: List<Slot>)

    /**
     * 宽高比**小于这个值** = 竖图 ✓ ⇒ **占 1 格** ✓。
     *
     * ## ⚠️ 这个阈值是**照真实数据定的** ✓，不是拍脑袋 ✗（第 ㊿e2 批 2026-09-22）
     *
     * 用户报「**没改变啊**」✓ —— 我查了：**算法没问题 ✗，是阈值太窄** ✓。
     *
     * 实测 `nai5_community_pack` 2105 条的宽高比直方图 ✓：
     *
     * ```
     * 0.3~0.6 :    33        ← 我旧阈值只认这些（1.6% ✗）
     * 0.6~0.7 :  1200  ##########  ← **主群：832×1216 标准竖图（ratio 0.685）**
     * 0.7~1.0 :    59
     * 1.0~1.1 :   257  ###         ← 方图
     * 1.4~1.5 :   506  #########   ← **次群：1216×832 标准横图（ratio 1.451）**
     * ≥1.5    :    26
     * ```
     *
     * 也就是说真实数据**只有两档**：**0.685 的竖图**和 **1.451 的横图** ✓ ——
     * 旧阈值（0.6 ✓）把 1200 张竖图**全判成 1 格** ✓、506 张横图也**全判成 1 格** ✓
     * ⇒ 屏幕上**清一色同宽** ✓ ⇒ 用户当然"看不出变化" ✗。
     *
     * **改成 1.0 为界** ✓：**竖图（< 1.0）1 格** ✓、**横图 / 长图（≥ 1.0）2 格** ✓ ——
     * 这正是用户口径「**竖图属于一个宽度，长图属于两格宽度**」✓
     *（他说的"长图"就是指比竖图长的那些 ✓，含横构图 ✓）。
     */
    const val TALL_RATIO = 1.0f

    /** 一张图是不是**竖图**（占 1 格 ✓；取不到尺寸也当竖图 ✓，兜底 1 格最稳 ✓）。 */
    fun isTall(width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return true
        return width.toFloat() / height.toFloat() < TALL_RATIO
    }

    /**
     * 占几格宽 ✓ —— **竖图 1 格** ✓、**横图 / 长图 2 格** ✓（口径 ✓）。
     *
     * ⚠️ 取不到比例（`0f` ✓）也**按 1 格** ✓ —— 宁可窄一点 ✓，不要平白撑宽 ✗。
     */
    fun spanOf(ratio: Float): Int =
        if (ratio > 0f && ratio >= TALL_RATIO) 2 else 1

    /**
     * 把条目排成行 ✓。
     *
     * ⚠️ **第 ㊿e4 批（2026-09-22）**：加「**后面有竖图就提上来填**」✓。
     *
     * 用户报：「**还有一些异形图，占据两格后右边还有一格可以放图，
     *   做成将排在后面的竖图放到上面填充**」✓
     *
     * **原来错在哪**：我只会"**拿下一个**" ✓ —— 下一个放不下就 `break` 换行 ✗，
     *   于是**那 1 格就空着** ✓，而**更后面**明明有能填的竖图 ✗（正是用户看到的 ✓）。
     *
     * **现在**：放不下时**往后找** ✓ —— 找**第一张竖图（1 格）**提上来填满 ✓；
     *   找不到（后面全是横图 ✓）才让那格留白 ✓（用户：「格子占不完可留白」✓）。
     *
     * ⚠️ **被提上来的那张要从原位置"拿走"** ✓ —— 否则它会在后面**再出现一次** ✗
     *   （重复条目 ✓）。所以内部维护一个 `pending` 待排列表 ✓，而不是照着原顺序读 ✗。
     *
     * @param ratios 每条的**宽高比** ✓（`width / height` ✓；取不到给 `0f` ✓ = 当竖图 ✓）。
     * @param columns 列数 ✓（2 / 3 / 4 ✓）。
     */
    fun rows(ratios: List<Float>, columns: Int): List<Row> {
        require(columns >= 1) { "列数至少 1" }
        val out = mutableListOf<Row>()

        // 待排列表：**提上来的那张会从这里被拿走** ✓，所以不会重复出现 ✓
        val pending = ratios.indices.toMutableList()

        while (pending.isNotEmpty()) {
            val row = mutableListOf<Slot>()
            var used = 0

            // ---- 顺着拿 ✓（能放就放 ✓）----
            while (pending.isNotEmpty()) {
                val idx = pending.first()
                val need = spanOf(ratios[idx])
                if (used + need > columns) break
                row += Slot(index = idx, span = need)
                used += need
                pending.removeAt(0)
            }

            // ---- ⚠️ 没排满 ⇒ **往后找竖图提上来填** ✓（第 ㊿e4 批 ✓）----
            while (used < columns && pending.isNotEmpty()) {
                // 找**第一张放得进的** ✓（放得下就行 ✓，不限于 1 格 ✓）
                val pickAt = pending.indexOfFirst { spanOf(ratios[it]) <= columns - used }
                if (pickAt < 0) break   // 后面全放不下 ⇒ 留白 ✓
                val idx = pending.removeAt(pickAt)
                val sp = spanOf(ratios[idx])
                row += Slot(index = idx, span = sp)
                used += sp
            }

            // ⚠️ **死锁兜底** ✓：一行**什么都放不进** ✓（例如 1 列 + 横图要 2 格 ✓）
            //    ⇒ 至少放一个 ✓（按 1 格放 ✓），否则 `while` 转不出去 ⇒ **死循环** ✗。
            //    ⚠️ 只在 `row` 空时触发 ✓ —— 排到一半的情况上面那轮已经处理 ✓。
            if (row.isEmpty() && pending.isNotEmpty()) {
                row += Slot(index = pending.removeAt(0), span = 1)
            }

            out += Row(row)
        }
        return out
    }
}
