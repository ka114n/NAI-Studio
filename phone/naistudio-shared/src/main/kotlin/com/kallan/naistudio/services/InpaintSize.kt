package com.kallan.naistudio.services

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * **聚焦重绘区域的目标像素自动计算**（用户 2026-09-26 的口径，逐字落成算法）。
 *
 * 用户原话：
 * > 「聚焦修改的**长宽自动计算**，框出来的大小**自动按照比例**，然后在 **1024×1024 像素以下**
 * > 自动计算**最高像素**；**宽高可以超过 1024，但总像素不行**。」
 * 用户 2026-09-26 追加定音：「**保 64**」——
 * 也就是"顶到上限"和"边长必须是 64 的倍数"冲突时，**以 64 为准**（面积宁可略小于上限 ✓）。
 *
 * ## 算法（三步，别改动顺序 ✗）
 * 1. 取框的宽高比，等比放大到**面积 = [AREA_LIMIT]**（这就是"最高像素" ✓）；
 * 2. 两边各自**四舍五入到最近的 64 倍数**（NovelAI 的硬要求 ✓）；
 * 3. 若取整后**面积超了上限**，就把**长边**往上/往下各退一档 64，直到面积不再超 ✓
 *    （只退长边：退短边会让比例偏得更多 ✗）。
 *
 * 例子（都在单测里钉住了 ✓）：
 *  · 1:1 → `1024 × 1024`（面积 = 上限）
 *  · 3:1 → 目标 `1792 × 597` → 保 64 → **`1792 × 576`**（面积 1.03M ✓，597 退到 576）
 *  · 1:3 → **`576 × 1792`**
 *  · 2:3 → 目标 `836 × 1253` → 保 64 → **`832 × 1216`**（正好是 NovelAI 的常规竖图尺寸 ✓）
 *
 * ⚠️ 画布编辑器里的聚焦框、以及漫画模式里每一格的生成尺寸，**都用这一个函数** ✓
 *（口径只有一处，才不会出现"编辑器算出来 832×1216、漫画那边算出来 896×1152"这种漂移 ✗）。
 */
object InpaintSize {

    /** 总像素上限：**1024 × 1024**（用户口径："总像素不行"超过它 ✓）。 */
    const val AREA_LIMIT: Int = 1024 * 1024

    /** 边长必须是它的倍数（NovelAI 的硬要求，用户定音"保 64" ✓）。 */
    const val STEP: Int = 64

    /** 最短边下限（低于这个连 64 都不够 ✓）。 */
    const val MIN_SIDE: Int = 64

    /**
     * 按框的**实际宽高**（像素，可以是小数）算出聚焦重绘的目标尺寸。
     *
     * @return `width to height`，两边都是 [STEP] 的倍数、面积 ≤ [AREA_LIMIT]、比例尽量贴近输入 ✓。
     */
    fun forRect(width: Float, height: Float): Pair<Int, Int> {
        // 退化输入：给一个 64×64 的方块，别崩也别返回 0 ✗
        if (width <= 0f || height <= 0f) return MIN_SIDE to MIN_SIDE

        val area = width.toDouble() * height.toDouble()
        // 等比放大到面积顶满上限（= 用户要的"最高像素" ✓）
        val scale = sqrt(AREA_LIMIT.toDouble() / area)
        val rawW = width.toDouble() * scale
        val rawH = height.toDouble() * scale

        var w = snap(rawW)
        var h = snap(rawH)

        // 取整后面积可能超上限（两边都往上取整时最常见 ✗）——
        // 只退**长边**：退一档 64 再看；还不合就继续，最多退几次就该够了 ✓。
        var guard = 0
        while (w.toLong() * h.toLong() > AREA_LIMIT.toLong() && guard < 64) {
            if (w >= h) {
                w = (w - STEP).coerceAtLeast(MIN_SIDE)
            } else {
                h = (h - STEP).coerceAtLeast(MIN_SIDE)
            }
            guard++
        }
        // 极端长条（比如 32:1）可能出现一边被压到 MIN_SIDE 之后面积仍超 —— 再压另一边 ✓
        while (w.toLong() * h.toLong() > AREA_LIMIT.toLong()) {
            if (w >= h) w = (w - STEP).coerceAtLeast(MIN_SIDE) else h = (h - STEP).coerceAtLeast(MIN_SIDE)
            if (w == MIN_SIDE && h == MIN_SIDE) break
        }
        return w to h
    }

    /** 四舍五入到最近的 [STEP] 倍数，并不低于 [MIN_SIDE]。 */
    private fun snap(value: Double): Int {
        val steps = (value / STEP).roundToInt()
        return (steps * STEP).coerceAtLeast(MIN_SIDE)
    }
}
