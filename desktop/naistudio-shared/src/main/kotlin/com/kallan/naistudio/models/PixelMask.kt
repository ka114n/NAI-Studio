package com.kallan.naistudio.models

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 逐像素遮罩。
 *
 * 曾经的实现是"每字节 = 原图 8×8 一格"（与参考实现一致，为的是天然对齐 NovelAI 的
 * 1/8 潜空间网格），但用户要求笔刷**按像素**、最小 1 px —— 1 px 的笔在 8 px 的格子里
 * 根本表示不出来，所以改成**每个字节 = 一个原图像素**（0/255）。
 *
 * 代价与取舍：
 *  · 内存：832×1216 的遮罩 = 约 1.0 MB；导出成 PNG 时再临时建一张同尺寸 ARGB 位图（约 4 MB，用完即释放）
 *  · 不再在客户端把遮罩吸附到 8 px 网格 —— 发出去的就是用户画的那张。
 *    如果实测发现"很细的笔迹被服务端忽略"，再补一个"加粗/吸附到潜空间网格"的选项。
 *
 * 这个文件刻意不依赖 android.graphics（PNG 编码在 `services/MaskCodec.kt`），
 * 所以笔刷几何与撤销行为都能写纯 JVM 单测。
 */

/** 归一化图片坐标（0..1，相对图片本身，与屏幕缩放无关）。 */
data class MaskPoint(val x: Float, val y: Float)

/**
 * 一笔（或一次点击）。
 *
 * @param erasing     橡皮：把像素清回 0
 * @param square      方形笔尖；false = 圆形
 * @param brushPixels 笔刷直径，单位是**原图像素**（[MASK_BRUSH_MIN_PIXELS]..[MASK_BRUSH_MAX_PIXELS]）
 */
data class MaskStroke(
    val erasing: Boolean = false,
    val square: Boolean = false,
    val brushPixels: Int = DEFAULT_MASK_BRUSH_PIXELS,
    val points: List<MaskPoint> = emptyList(),
)

const val MASK_BRUSH_MIN_PIXELS = 1
const val MASK_BRUSH_MAX_PIXELS = 350
const val DEFAULT_MASK_BRUSH_PIXELS = 32

/** 不可变的逐像素遮罩快照（送请求用）。 */
class PixelMask(
    val width: Int,
    val height: Int,
    val pixels: ByteArray,
) {

    init {
        require(width > 0 && height > 0) { "遮罩尺寸必须为正：${width}x${height}" }
        require(pixels.size == width * height) {
            "遮罩尺寸不匹配：${width}x${height} 需要 ${width * height} 字节，实际 ${pixels.size}"
        }
    }

    val selectedCount: Int
        get() {
            var count = 0
            for (value in pixels) if (value.toInt() != 0) count++
            return count
        }

    /** 涂过的面积占比（0..1），用于状态行显示。 */
    val coverage: Float
        get() = selectedCount.toFloat() / (width.toFloat() * height.toFloat())

    fun isEmpty(): Boolean = selectedCount == 0

    fun valueAt(x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0
        return pixels[y * width + x].toInt() and 0xFF
    }

    fun copy(): PixelMask = PixelMask(width, height, pixels.copyOf())
}

/**
 * 笔刷几何：把一笔铺到像素上。
 *
 * 判定用**点到线段的最短距离**（圆形用欧氏距离、方形用切比雪夫距离），
 * 所以快速拖动得到的是连续的一笔，而不是一串离散点；单点（点击）也走同一条路径。
 */
object MaskBrush {

    /**
     * 边界容差：归一化坐标来回换算成像素会有 1e-5 级别的浮点误差，
     * 正好落在"圆边上"的相邻像素会时有时无（小笔刷尤其明显）。加一点点容差就稳了。
     */
    private const val BOUNDARY_EPSILON = 1e-3f

    fun stampInto(
        data: ByteArray,
        width: Int,
        height: Int,
        from: MaskPoint?,
        to: MaskPoint,
        stroke: MaskStroke,
    ) {
        if (width <= 0 || height <= 0 || data.size < width * height) return
        val value = if (stroke.erasing) 0.toByte() else 255.toByte()
        val radius = normalizedBrushPixels(stroke) / 2f

        val startX = (from ?: to).x * width
        val startY = (from ?: to).y * height
        val endX = to.x * width
        val endY = to.y * height
        val dx = endX - startX
        val dy = endY - startY
        val lengthSquared = dx * dx + dy * dy

        val minX = max(0, kotlin.math.floor(min(startX, endX) - radius).toInt())
        val maxX = min(width - 1, kotlin.math.floor(max(startX, endX) + radius).toInt())
        val minY = max(0, kotlin.math.floor(min(startY, endY) - radius).toInt())
        val maxY = min(height - 1, kotlin.math.floor(max(startY, endY) + radius).toInt())
        if (minX > maxX || minY > maxY) return

        val radiusSquared = radius * radius
        for (y in minY..maxY) {
            val rowOffset = y * width
            val centerY = y + 0.5f
            for (x in minX..maxX) {
                val centerX = x + 0.5f
                val t = if (lengthSquared <= 0f) {
                    0f
                } else {
                    (((centerX - startX) * dx + (centerY - startY) * dy) / lengthSquared)
                        .coerceIn(0f, 1f)
                }
                val nearestX = startX + dx * t
                val nearestY = startY + dy * t
                val offX = abs(centerX - nearestX)
                val offY = abs(centerY - nearestY)
                val inside = if (stroke.square) {
                    max(offX, offY) <= radius + BOUNDARY_EPSILON
                } else {
                    offX * offX + offY * offY <= radiusSquared + BOUNDARY_EPSILON
                }
                if (inside) data[rowOffset + x] = value
            }
        }
    }

    /** 夹到合法范围；圆形笔刷最小 2 px（1 px 的圆在像素网格上会时有时无）。 */
    fun normalizedBrushPixels(stroke: MaskStroke): Int {
        val requested = stroke.brushPixels.coerceIn(MASK_BRUSH_MIN_PIXELS, MASK_BRUSH_MAX_PIXELS)
        return if (stroke.square) requested else max(2, requested)
    }
}

/**
 * 遮罩形态学：**方形膨胀**（对应节点里的 `mask_expand`）。
 *
 * 口径与插件 `modular_nodes.py:155-163` 一致：`max_pool2d(kernel = 2r+1)`，等价于
 * "切比雪夫距离 ≤ r 的邻域取最大值"。这里是纯 Kotlin 版，用**可分离的两趟取最大**
 * （先横后纵）把复杂度从 O(w·h·r²) 降到 O(w·h·2r)。
 *
 * 用途：让服务端看到的遮罩比你涂的区域大几个像素，接缝落在选区外面；
 * 顺带把"很细的笔迹"加粗，降低被服务端下采样吃掉的概率。
 */
object MaskExpand {

    /** 最大膨胀半径（节点上限是 128 px）。 */
    const val MAX_RADIUS_PIXELS = 128

    fun dilate(pixels: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
        val r = radius.coerceIn(0, MAX_RADIUS_PIXELS)
        if (r == 0 || width <= 0 || height <= 0 || pixels.size < width * height) return pixels.copyOf()

        val horizontal = ByteArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val from = max(0, x - r)
                val to = min(width - 1, x + r)
                var value: Byte = 0
                for (index in from..to) {
                    if (pixels[row + index].toInt() != 0) {
                        value = 255.toByte()
                        break
                    }
                }
                horizontal[row + x] = value
            }
        }

        val result = ByteArray(width * height)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val from = max(0, y - r)
                val to = min(height - 1, y + r)
                var value: Byte = 0
                for (index in from..to) {
                    if (horizontal[index * width + x].toInt() != 0) {
                        value = 255.toByte()
                        break
                    }
                }
                result[y * width + x] = value
            }
        }
        return result
    }

    fun dilate(mask: PixelMask, radius: Int): PixelMask =
        PixelMask(mask.width, mask.height, dilate(mask.pixels, mask.width, mask.height, radius))
}
