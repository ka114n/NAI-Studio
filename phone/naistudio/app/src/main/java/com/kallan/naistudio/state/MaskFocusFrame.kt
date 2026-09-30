package com.kallan.naistudio.state

import com.kallan.naistudio.models.EditRect
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * **聚焦框**（手机线）：拖动得到的那块，坐标是**归一化**的（0..1，相对**显示出来的那张图** ✓）。
 *
 * ## 为什么它长在手机线的状态里，而不在共享模型里（⚠️ 这一段要读完）
 *
 * 这个类型原来叫 `FocusedInpaint.Frame` ✓，长在**共享模型**那一份 `FocusedInpaint.kt` 里。
 * 但那两条线的 `FocusedInpaint` **早就分家了** ✗：
 *  · 电脑线那份后来重构成**像素矩形**（`EditRect` ✓ —— 因为它那边已经统一走 `SelectionShape` +
 *    `SelectionGeometry.bounds` ✓，框的"归一化"是**界面手势层**的事 ✓）；
 *  · 手机线这份还停在"`Frame` + `Rect`"那一版 ✓。
 * 这一次把共享文件拉齐（两份树必须同源 ✓）时，那个 `Frame` **在电脑线那份里已经不存在了** ✗ ——
 * 于是按电脑线的口径把它**收进手机线自己的状态** ✓：它本来就是"界面拖出来的归一化框" ✓，
 * 不是共享算法（裁剪 / 放大 / 贴回）的一部分 ✓ —— 放在这里，两条线的 `FocusedInpaint` 才能真同源 ✓。
 *
 * 算法这边一像素都没改 ✗：像素化之后照样交给共享的 `FocusedInpaint.plan` ✓。
 */
data class MaskFocusFrame(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
) {

    val left: Float get() = min(x0, x1)
    val top: Float get() = min(y0, y1)
    val right: Float get() = max(x0, x1)
    val bottom: Float get() = max(y0, y1)

    /**
     * 这个框算不算数（手机线口径）：**两条边都超过千分之五** ✓，而且四个数都是有限值 ✓。
     *
     * 阈值取归一化的 0.005（1024 宽的图上约 5 px ✓）：手指点一下、抖一下不该留下一个框 ✓
     * —— 留了的话"以为自己没框、其实框了一小条"会生成一块莫名其妙的东西 ✗（那是花钱的 ✗）。
     */
    val isValid: Boolean
        get() = x0.isFinite() && y0.isFinite() && x1.isFinite() && y1.isFinite() &&
            (right - left) >= 0.005f && (bottom - top) >= 0.005f

    /** 换成**原图像素**矩形 ✓（夹在画布内 ✓，至少 1×1 ✓）。 */
    fun toPixels(width: Int, height: Int): EditRect {
        if (width <= 0 || height <= 0) return EditRect.EMPTY
        val x = (left * width).roundToInt().coerceIn(0, max(0, width - 1))
        val y = (top * height).roundToInt().coerceIn(0, max(0, height - 1))
        val w = ((right - left) * width).roundToInt().coerceAtLeast(1)
        val h = ((bottom - top) * height).roundToInt().coerceAtLeast(1)
        return EditRect(x, y, w, h).clampTo(width, height)
    }
}
