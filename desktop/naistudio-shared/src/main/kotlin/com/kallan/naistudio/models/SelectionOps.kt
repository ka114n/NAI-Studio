package com.kallan.naistudio.models

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * **选区形状**：矩形或多边形（都存**归一化图片坐标** 0..1，和遮罩那套一个口径，
 * 这样缩放/平移画布不影响它）。
 */
sealed interface SelectionShape {

    data class Rect(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) : SelectionShape

    /** 套索：一串点，首尾自动闭合。 */
    data class Polygon(val points: List<MaskPoint>) : SelectionShape
}

/**
 * 选区几何（纯函数，可单测）：包围盒、点在不在里面。
 */
object SelectionGeometry {

    /** 选区在**像素坐标**下的包围盒；空选区返回 null。 */
    fun bounds(shape: SelectionShape, width: Int, height: Int): EditRect? {
        if (width <= 0 || height <= 0) return null
        val (minX, minY, maxX, maxY) = when (shape) {
            is SelectionShape.Rect -> listOf(shape.left, shape.top, shape.right, shape.bottom)
            is SelectionShape.Polygon -> {
                if (shape.points.isEmpty()) return null
                listOf(
                    shape.points.minOf { it.x },
                    shape.points.minOf { it.y },
                    shape.points.maxOf { it.x },
                    shape.points.maxOf { it.y },
                )
            }
        }
        val left = (min(minX, maxX) * width).toInt()
        val top = (min(minY, maxY) * height).toInt()
        val right = (max(minX, maxX) * width).toInt()
        val bottom = (max(minY, maxY) * height).toInt()
        val rect = EditRect(left, top, right - left + 1, bottom - top + 1).clampTo(width, height)
        return if (rect.isEmpty) null else rect
    }

    /** 归一化坐标 ([nx],[ny]) 在不在选区里（多边形用 even-odd 射线法）。 */
    fun contains(shape: SelectionShape, nx: Float, ny: Float): Boolean = when (shape) {
        is SelectionShape.Rect -> nx >= min(shape.left, shape.right) &&
            nx <= max(shape.left, shape.right) &&
            ny >= min(shape.top, shape.bottom) &&
            ny <= max(shape.top, shape.bottom)

        is SelectionShape.Polygon -> {
            val points = shape.points
            if (points.size < 3) {
                false
            } else {
                var inside = false
                var j = points.size - 1
                for (i in points.indices) {
                    val xi = points[i].x
                    val yi = points[i].y
                    val xj = points[j].x
                    val yj = points[j].y
                    if ((yi > ny) != (yj > ny) &&
                        nx < (xj - xi) * (ny - yi) / (yj - yi) + xi
                    ) {
                        inside = !inside
                    }
                    j = i
                }
                inside
            }
        }
    }
}

/**
 * **被"抬起来"的那块像素 + 它的变换**（官方那套浮动选区）。
 *
 * ## 坐标系
 *
 * patch 有自己的局部坐标 (u,v) ∈ [0,pw)×[0,ph)。映射到画布像素坐标是：
 *
 * ```
 * 中心 = (originX + pw/2 + dx, originY + ph/2 + dy)
 * canvas = 中心 + R(rotation) · S(scaleX, scaleY) · (local - (pw/2, ph/2))
 * ```
 *
 * 也就是**先缩放、再旋转、最后平移**（和界面上的 `graphicsLayer` 顺序一致，
 * 所以显示和最终落盘的像素不会差一个顺序 —— 这种地方差一点就是"看着对、合上去偏了"）。
 */
class FloatingSelection(
    val patchWidth: Int,
    val patchHeight: Int,
    val pixels: IntArray,
    /** patch 左上角在**原图**里的位置（变换前）。 */
    val originX: Int,
    val originY: Int,
) {

    var dx: Float = 0f
        private set
    var dy: Float = 0f
        private set
    var scaleX: Float = 1f
        private set
    var scaleY: Float = 1f
        private set
    var rotationDeg: Float = 0f
        private set

    init {
        require(patchWidth > 0 && patchHeight > 0) { "patch 尺寸必须为正" }
        require(pixels.size == patchWidth * patchHeight) { "patch 像素数不匹配" }
    }

    val centerX: Float get() = originX + patchWidth / 2f + dx
    val centerY: Float get() = originY + patchHeight / 2f + dy

    fun translateBy(deltaX: Float, deltaY: Float) {
        dx += deltaX
        dy += deltaY
    }

    /**
     * 缩放，并让**画布上的 ([anchorX],[anchorY]) 这个点不动**。
     *
     * 怎么做到的：先把锚点换算成 patch 的局部坐标（用**旧的**变换），
     * 改完缩放后再算一次它落到哪，差值补进平移 —— 于是锚点在视觉上原地不动。
     * 不这么做的话，缩放会以 patch 中心为基准，手感是"拖角的时候整块在跑"。
     */
    fun scaleBy(factorX: Float, factorY: Float, anchorX: Float, anchorY: Float) {
        if (factorX <= 0f || factorY <= 0f) return
        val anchorLocal = toLocal(anchorX, anchorY)
        scaleX = (scaleX * factorX).coerceIn(0.02f, 50f)
        scaleY = (scaleY * factorY).coerceIn(0.02f, 50f)
        val anchorAfter = toCanvas(anchorLocal.first, anchorLocal.second)
        dx += anchorX - anchorAfter.first
        dy += anchorY - anchorAfter.second
    }

    fun rotateBy(deltaDeg: Float) {
        rotationDeg = (rotationDeg + deltaDeg) % 360f
    }

    fun toCanvas(localX: Float, localY: Float): Pair<Float, Float> {
        val lx = (localX - patchWidth / 2f) * scaleX
        val ly = (localY - patchHeight / 2f) * scaleY
        val rad = Math.toRadians(rotationDeg.toDouble())
        val rx = (lx * cos(rad) - ly * sin(rad)).toFloat()
        val ry = (lx * sin(rad) + ly * cos(rad)).toFloat()
        return (centerX + rx) to (centerY + ry)
    }

    fun toLocal(canvasX: Float, canvasY: Float): Pair<Float, Float> {
        val rad = Math.toRadians(-rotationDeg.toDouble())
        val ox = canvasX - centerX
        val oy = canvasY - centerY
        val rx = (ox * cos(rad) - oy * sin(rad)).toFloat()
        val ry = (ox * sin(rad) + oy * cos(rad)).toFloat()
        return (rx / scaleX + patchWidth / 2f) to (ry / scaleY + patchHeight / 2f)
    }

    /** 变换之后四个角在画布上的位置（画选框/控制点用）。 */
    fun corners(): List<Pair<Float, Float>> = listOf(
        toCanvas(0f, 0f),
        toCanvas(patchWidth.toFloat(), 0f),
        toCanvas(patchWidth.toFloat(), patchHeight.toFloat()),
        toCanvas(0f, patchHeight.toFloat()),
    )

    /** 变换之后在画布上的包围盒（像素）。 */
    fun bounds(): EditRect {
        val corners = corners()
        val minX = corners.minOf { it.first }
        val maxX = corners.maxOf { it.first }
        val minY = corners.minOf { it.second }
        val maxY = corners.maxOf { it.second }
        return EditRect(
            kotlin.math.floor(minX).toInt(),
            kotlin.math.floor(minY).toInt(),
            (kotlin.math.ceil(maxX).toInt() - kotlin.math.floor(minX).toInt()) + 1,
            (kotlin.math.ceil(maxY).toInt() - kotlin.math.floor(minY).toInt()) + 1,
        )
    }

    /** 有没有真的动过（没动就不用提交，省一步历史）。 */
    fun isIdentity(): Boolean =
        abs(dx) < 0.01f && abs(dy) < 0.01f &&
            abs(scaleX - 1f) < 1e-4f && abs(scaleY - 1f) < 1e-4f &&
            abs(rotationDeg) < 1e-3f
}

/**
 * 浮动选区的**抬起**与**落回**（纯数组运算）。
 */
object SelectionOps {

    /**
     * 把选区内的像素"抬起来"：内容进 patch，**原处清成透明**。
     *
     * 矩形选区就是整块；套索要逐像素判在不在多边形里。
     */
    fun lift(canvas: IntArray, width: Int, height: Int, shape: SelectionShape): FloatingSelection? {
        val rect = SelectionGeometry.bounds(shape, width, height) ?: return null
        val patch = IntArray(rect.area)
        var lifted = 0
        for (row in 0 until rect.h) {
            val y = rect.y + row
            val canvasRow = y * width
            val patchRow = row * rect.w
            for (col in 0 until rect.w) {
                val x = rect.x + col
                val nx = (x + 0.5f) / width
                val ny = (y + 0.5f) / height
                if (!SelectionGeometry.contains(shape, nx, ny)) continue
                val value = canvas[canvasRow + x]
                if (value == ImageEditOps.TRANSPARENT) continue
                patch[patchRow + col] = value
                canvas[canvasRow + x] = ImageEditOps.TRANSPARENT
                lifted++
            }
        }
        if (lifted == 0) return null
        return FloatingSelection(rect.w, rect.h, patch, rect.x, rect.y)
    }

    /**
     * 把 patch 按当前变换盖回画布（**最近邻**采样）。
     *
     * 为什么用最近邻：官方那套是像素级编辑，插值会给选区边缘带出一圈半透明的虚边，
     * 之后再当底图用时很脏。旋转 45° 这种会有锯齿 —— 但保持"像素就是像素"更重要。
     *
     * @return 实际改动的矩形；没有可落笔的像素时返回 null。
     */
    fun stamp(canvas: IntArray, width: Int, height: Int, floating: FloatingSelection): EditRect? {
        val rect = floating.bounds().clampTo(width, height)
        if (rect.isEmpty) return null
        var touched = 0
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        for (y in rect.y until (rect.y + rect.h)) {
            for (x in rect.x until (rect.x + rect.w)) {
                val (localX, localY) = floating.toLocal(x + 0.5f, y + 0.5f)
                val sx = localX.toInt()
                val sy = localY.toInt()
                if (sx < 0 || sy < 0 || sx >= floating.patchWidth || sy >= floating.patchHeight) continue
                val value = floating.pixels[sy * floating.patchWidth + sx]
                if (value == ImageEditOps.TRANSPARENT) continue
                // 半透明的补上去用 source-over；全不透明的直接覆盖
                canvas[y * width + x] = ImageEditOps.overPixel(
                    canvas[y * width + x],
                    value,
                    ((value ushr 24) and 0xFF) / 255f,
                )
                touched++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (touched == 0) return null
        return EditRect(minX, minY, maxX - minX + 1, maxY - minY + 1).clampTo(width, height)
    }

    /** 把整个 patch 原样（不缩放旋转）盖回它原来的地方 —— 「取消」用。 */
    fun stampBack(canvas: IntArray, width: Int, height: Int, floating: FloatingSelection) {
        for (row in 0 until floating.patchHeight) {
            val y = floating.originY + row
            if (y < 0 || y >= height) continue
            for (col in 0 until floating.patchWidth) {
                val x = floating.originX + col
                if (x < 0 || x >= width) continue
                val value = floating.pixels[row * floating.patchWidth + col]
                if (value == ImageEditOps.TRANSPARENT) continue
                canvas[y * width + x] = value
            }
        }
    }

    /** 选区形状从像素矩形反推（拖出来的框，夹到画布内）。 */
    fun rectShape(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        width: Int,
        height: Int,
    ): SelectionShape.Rect {
        val nx1 = (startX / width).coerceIn(0f, 1f)
        val ny1 = (startY / height).coerceIn(0f, 1f)
        val nx2 = (endX / width).coerceIn(0f, 1f)
        val ny2 = (endY / height).coerceIn(0f, 1f)
        return SelectionShape.Rect(nx1, ny1, nx2, ny2)
    }

    /** 落回之后把选区挪到新位置（用户接着还能再动它）。 */
    fun shiftedShape(shape: SelectionShape, dx: Float, dy: Float): SelectionShape = when (shape) {
        is SelectionShape.Rect -> SelectionShape.Rect(
            shape.left + dx,
            shape.top + dy,
            shape.right + dx,
            shape.bottom + dy,
        )

        is SelectionShape.Polygon -> SelectionShape.Polygon(
            shape.points.map { MaskPoint(it.x + dx, it.y + dy) },
        )
    }

    /** 选区"大小"（归一化下的宽高），界面显示用。 */
    fun sizeOf(shape: SelectionShape, width: Int, height: Int): Pair<Int, Int> {
        val rect = SelectionGeometry.bounds(shape, width, height) ?: return 0 to 0
        return rect.w to rect.h
    }
}
