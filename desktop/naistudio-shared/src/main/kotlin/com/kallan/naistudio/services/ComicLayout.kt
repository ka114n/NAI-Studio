package com.kallan.naistudio.services

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * **拼页的几何计算**（高级漫画模式第 ⑦ 项「拼页导出」的地基）—— 纯数学 ✓ 可单测 ✓。
 *
 * 一页要把每个格子里的生成图**放进它自己的矩形**里：
 *  · [fit]  = 完整放进去（留边 ✓，像 `ContentScale.Fit`✓）；
 *  · [cover] = 铺满格子、多的裁掉（像 `ContentScale.Crop`✓，漫画格子常用 ✓）。
 *
 * 屏幕上预览与导出成图**必须用同一套公式** ✓ —— 否则"看起来对齐了、导出来偏了"✗
 *（这仓库在遮罩/聚焦那块就是靠"一处 rect、四处共用"解决的 ✓，这里沿用同一条规矩 ✓）。
 */
object ComicLayout {

    /** 完整放进格子（等比，居中；`imageW/H` 非法时返回空矩形 ✓）。 */
    fun fit(panel: Rect, imageW: Int, imageH: Int): Rect = place(panel, imageW, imageH, cover = false)

    /** 铺满格子（等比，居中，超出部分由调用方裁 ✓）。 */
    fun cover(panel: Rect, imageW: Int, imageH: Int): Rect = place(panel, imageW, imageH, cover = true)

    private fun place(panel: Rect, imageW: Int, imageH: Int, cover: Boolean): Rect {
        if (imageW <= 0 || imageH <= 0 || panel.width <= 0f || panel.height <= 0f) {
            return Rect(Offset.Zero, Size.Zero)
        }
        val sx = panel.width / imageW
        val sy = panel.height / imageH
        val s = if (cover) maxOf(sx, sy) else minOf(sx, sy)
        val w = imageW * s
        val h = imageH * s
        // 居中到格子中心 ✓（Fit 时四周留相等的边 ✓）
        val left = panel.left + (panel.width - w) / 2f
        val top = panel.top + (panel.height - h) / 2f
        return Rect(left, top, left + w, top + h)
    }

    /**
     * 一页整图的画布尺寸（取所有格子的并集，向上取整 ✓）——
     * 导出时用它开画布，够装下最边上的格子 ✓；空页给 1×1（别给 0 ✗，位图开不出来 ✓）。
     */
    fun pageBounds(panels: List<Rect>): Pair<Int, Int> {
        if (panels.isEmpty()) return 1 to 1
        val right = panels.maxOf { it.right }
        val bottom = panels.maxOf { it.bottom }
        val left = panels.minOf { it.left }
        val top = panels.minOf { it.top }
        // 允许格子有负坐标（用户可能把格子拖到左上角外面 ✓）：这时候把整页**平移回来** ✓
        val w = (right - minOf(left, 0f)).coerceAtLeast(1f)
        val h = (bottom - minOf(top, 0f)).coerceAtLeast(1f)
        return w.toInt() to h.toInt()
    }
}
