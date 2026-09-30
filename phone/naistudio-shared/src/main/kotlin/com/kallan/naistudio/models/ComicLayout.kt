package com.kallan.naistudio.models

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * 漫画分格版式：**用矩形定义**，位置锚点由矩形中心算出来（单一事实源）。
 *
 * 为什么是矩形而不是锚点：早先只存"位置锚点"、预览再按 `ceil(sqrt(格数))` 猜格子大小，
 * 结果「上下对半」被画成两个并排的竖长条 —— 2 格算出来 cols=2/rows=1，完全不对。
 * 矩形才是漫画版式的本来面目。
 *
 * 刻意的设计取向：**不对称**。参考实现（NovelAI Harness）的自动布局是等分布点
 * （2→左右、4→四角、6→两行三列），而它自己的 v5-architect 技能却写着
 * "严禁死板的等分格子"。主格/副格、通栏、斜切才是漫画该有的样子。
 *
 * 本对象是**纯逻辑、不碰 Android**，所以能写 JVM 单测（见 `ComicLayoutTest`）。
 */
object ComicLayout {

    /** 默认版式：上下对半（主格 + 副格）。 */
    const val DEFAULT = "v2"

    /** 按现有格数自动排布，不固定格数。 */
    const val AUTO = "auto"

    /** 日漫阅读顺序：同一行内从右往左。 */
    const val ORDER_RTL = "rtl"

    /** 从左往右。 */
    const val ORDER_LTR = "ltr"

    fun normalizeOrder(order: String?): String =
        if (order == ORDER_LTR) ORDER_LTR else ORDER_RTL

    /** 归一化矩形，取值都在 0..1。`[x, y, w, h]`。 */
    data class Rect(val x: Double, val y: Double, val w: Double, val h: Double) {
        /** 矩形中心 X —— 就是发给 NovelAI 的 `centers.x`。 */
        val centerX: Double get() = x + w / 2

        /** 矩形中心 Y —— 就是发给 NovelAI 的 `centers.y`。 */
        val centerY: Double get() = y + h / 2

        /** 面积，用来判断"主格"。 */
        val area: Double get() = w * h
    }

    /**
     * 一个版式模板。
     *
     * 普通版式用 [rects]；斜切没法用矩形表示，改用 [diagonal] + [cells]。
     * [order] 是**显式声明的阅读顺序**（rects 的下标序列），只给带"主格"的版式用 ——
     * 通高主格的中心 Y 落在中间，靠 Y 排序会被算成"中间那一格"，那是错的。
     */
    data class Template(
        val id: String,
        val name: String,
        val rects: List<Rect>? = null,
        val order: List<Int>? = null,
        val diagonal: Boolean = false,
        val cells: List<Pair<Double, Double>> = emptyList(),
    ) {
        /** 这个版式固定几格；null = 跟随现有格数（AUTO）。 */
        val panelCount: Int?
            get() = rects?.size ?: cells.size.takeIf { diagonal }
    }

    private fun r(x: Double, y: Double, w: Double, h: Double) = Rect(x, y, w, h)

    /** 全部版式模板（顺序即选择弹层里的展示顺序）。 */
    val templates: List<Template> = listOf(
        Template(
            id = "v2",
            name = "上下对半",
            rects = listOf(
                r(0.03, 0.03, 0.94, 0.52), // 主格（大）
                r(0.03, 0.59, 0.94, 0.38), // 副格（小）
            ),
        ),
        Template(
            id = "v2h",
            name = "左右对半",
            rects = listOf(
                r(0.03, 0.03, 0.44, 0.94),
                r(0.53, 0.03, 0.44, 0.94),
            ),
        ),
        Template(
            id = "diag",
            name = "斜切二分",
            diagonal = true,
            cells = listOf(0.30 to 0.28, 0.70 to 0.72),
        ),
        Template(
            id = "v3",
            name = "上1下2",
            rects = listOf(
                r(0.03, 0.03, 0.94, 0.40),
                r(0.03, 0.47, 0.45, 0.50),
                r(0.52, 0.47, 0.45, 0.50),
            ),
        ),
        Template(
            id = "v3v",
            name = "竖排三格",
            rects = listOf(
                r(0.03, 0.03, 0.94, 0.28),
                r(0.03, 0.35, 0.94, 0.28),
                r(0.03, 0.67, 0.94, 0.30),
            ),
        ),
        Template(
            id = "v4",
            name = "田字四格",
            rects = listOf(
                r(0.03, 0.03, 0.45, 0.45),
                r(0.52, 0.03, 0.45, 0.45),
                r(0.03, 0.52, 0.45, 0.45),
                r(0.52, 0.52, 0.45, 0.45),
            ),
        ),
        Template(
            id = "v4a",
            name = "主格+三副格",
            // 显式阅读顺序：大主格先读，再看右列自上而下
            order = listOf(0, 1, 2, 3),
            rects = listOf(
                r(0.03, 0.03, 0.60, 0.94), // 左侧通高主格
                r(0.66, 0.03, 0.31, 0.28),
                r(0.66, 0.36, 0.31, 0.28),
                r(0.66, 0.69, 0.31, 0.28),
            ),
        ),
        Template(
            id = "v5",
            name = "通栏主格+四格",
            rects = listOf(
                r(0.03, 0.03, 0.94, 0.40),
                r(0.03, 0.47, 0.45, 0.22),
                r(0.52, 0.47, 0.45, 0.22),
                r(0.03, 0.72, 0.45, 0.25),
                r(0.52, 0.72, 0.45, 0.25),
            ),
        ),
        Template(
            id = "v6",
            name = "两行三列",
            rects = listOf(
                r(0.03, 0.03, 0.30, 0.45),
                r(0.35, 0.03, 0.30, 0.45),
                r(0.67, 0.03, 0.30, 0.45),
                r(0.03, 0.52, 0.30, 0.45),
                r(0.35, 0.52, 0.30, 0.45),
                r(0.67, 0.52, 0.30, 0.45),
            ),
        ),
        Template(
            id = "v6a",
            name = "通栏主格+五格",
            rects = listOf(
                r(0.03, 0.03, 0.94, 0.34),
                r(0.03, 0.40, 0.45, 0.27),
                r(0.52, 0.40, 0.45, 0.27),
                r(0.03, 0.70, 0.30, 0.27),
                r(0.35, 0.70, 0.30, 0.27),
                r(0.67, 0.70, 0.30, 0.27),
            ),
        ),
        Template(id = AUTO, name = "按数量自动", rects = null),
    )

    fun template(id: String?): Template =
        templates.firstOrNull { it.id == id } ?: templates.first()

    /** 这个版式固定几格；null = 跟随现有格数。 */
    fun panelCountOf(id: String?): Int? = template(id).panelCount

    fun nameOf(id: String?): String = template(id).name

    /**
     * 按数量生成等分矩形（`auto` 版式与格数超出模板时的兜底）。
     * 列数取 `ceil(sqrt(n))`，与参考实现的默认布局同构。
     */
    fun autoRects(count: Int): List<Rect> {
        if (count <= 0) return emptyList()
        val cols = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
        val rows = ceil(count.toDouble() / cols).toInt().coerceAtLeast(1)
        val gap = 0.02
        val margin = 0.03
        val w = (1.0 - 2 * margin - gap * (cols - 1)) / cols
        val h = (1.0 - 2 * margin - gap * (rows - 1)) / rows
        return (0 until count).map { index ->
            val row = index / cols
            val col = index % cols
            r(margin + col * (w + gap), margin + row * (h + gap), w, h)
        }
    }

    /**
     * 展开成**按阅读顺序排好**的矩形列表（长度 = 实际要分配的格数）。
     *
     * - [ord] 只影响**分配顺序**，不改几何（版式形状不变）；
     * - 模板显式声明了 [Template.order] 时以它为准（`ord` 对这类版式不生效）；
     * - 格数超出模板 → **整页退回自动网格**，不做"模板 + 补几个"的混合。
     */
    fun orderedRects(id: String?, ord: String?, count: Int): List<Rect> {
        val take = count.coerceAtLeast(0)
        val tpl = template(id)

        // 斜切版式没有矩形，走 cells
        if (tpl.diagonal) return emptyList()

        val rects = tpl.rects ?: return autoRects(count).take(take)
        if (count > rects.size) return autoRects(count).take(take)

        tpl.order?.takeIf { it.size == rects.size }?.let { order ->
            return order.map { rects[it] }.take(take)
        }

        val reading = normalizeOrder(ord)
        return rects.sortedWith { a, b ->
            // 先上后下（行高差超过阈值就算换行）
            if (abs(a.centerY - b.centerY) > 0.18) {
                a.centerY.compareTo(b.centerY)
            } else if (reading == ORDER_RTL) {
                b.centerX.compareTo(a.centerX)
            } else {
                a.centerX.compareTo(b.centerX)
            }
        }.take(take)
    }

    /** 位置锚点 = 矩形中心。这就是发给 NovelAI 的 `centers`。 */
    fun anchorsFor(id: String?, ord: String?, count: Int): List<Pair<Double, Double>> {
        val tpl = template(id)
        if (tpl.diagonal) {
            val reading = normalizeOrder(ord)
            return tpl.cells
                .sortedWith { a, b ->
                    if (a.second != b.second) {
                        a.second.compareTo(b.second)
                    } else if (reading == ORDER_RTL) {
                        b.first.compareTo(a.first)
                    } else {
                        a.first.compareTo(b.first)
                    }
                }
                .take(count.coerceAtLeast(0))
        }
        return orderedRects(id, ord, count).map { it.centerX to it.centerY }
    }

    /**
     * 从末尾数起**能删掉**几个分格：空格可删，一碰到有内容的格就停。
     *
     * 抽出来给两处共用：真正执行同步的 [com.kallan.naistudio.state.AppState.syncComicPanelCount]，
     * 和版式弹层里的"会怎么改"预告 —— 预告只承诺删得掉的空格，不骗用户。
     */
    fun removableTail(prompts: List<String>, target: Int): Int {
        var n = 0
        for (i in prompts.indices.reversed()) {
            if (i < target) break
            if (prompts[i].isNotBlank()) break
            n++
        }
        return n
    }
}
