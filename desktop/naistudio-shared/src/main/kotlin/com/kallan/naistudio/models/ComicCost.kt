package com.kallan.naistudio.models

/**
 * **跑前花费估算**（高级漫画模式"队列跑"之前的那个确认框要用）—— 用户 2026-09-26 的口径里有
 * 「跑前报总消耗并确认」（方案 §5 风险 1：逐格生成 = 多次付费调用 ✗）。
 *
 * 设计上刻意**不认识具体价格**：价格函数由调用方注入 ✓
 * （真正的 Anlas 规则在 `services/NaiApi` / `OfficialUpscale` 那边 ✓，
 *   这里只做"把每格的尺寸喂给价格函数、加起来、给个清单"✓）——
 * 这样单测能塞假价格 ✓，将来价格口径变了也不用动这里 ✓。
 */
object ComicCost {

    /** 一行 = 一格的花费 ✓（清单给用户看："第 3 格 832×1216 → 2 Anlas"✓）。 */
    data class Line(val panelId: String, val width: Int, val height: Int, val anlas: Int)

    data class Estimate(val lines: List<Line>) {
        val total: Int get() = lines.sumOf { it.anlas }
        val count: Int get() = lines.size

        /** 一句给确认框用的话（i18n 那边可以再包一层 ✓）。 */
        fun summaryText(): String = "$count 格，合计约 $total Anlas"
    }

    /**
     * 估算。
     *
     * @param panels 每格：`panelId to (宽, 高)` —— 尺寸请用
     *   [com.kallan.naistudio.services.InpaintSize.forRect] 算出来的那对（**保证 64 倍数、面积不超上限** ✓）。
     * @param priceOf 价格函数：给定尺寸返回 Anlas ✓（注入 ✓，不在这里写死 ✗）。
     * @return 逐格清单 + 合计 ✓；空输入 → 空清单 + 0 ✓。
     */
    fun estimate(
        panels: List<Pair<String, Pair<Int, Int>>>,
        priceOf: (width: Int, height: Int) -> Int,
    ): Estimate = Estimate(
        panels.map { (id, size) ->
            val (w, h) = size
            Line(panelId = id, width = w, height = h, anlas = priceOf(w, h).coerceAtLeast(0))
        },
    )
}
