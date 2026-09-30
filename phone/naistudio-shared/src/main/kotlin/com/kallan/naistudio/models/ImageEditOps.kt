package com.kallan.naistudio.models

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 笔尖形状 —— 照 NovelAI 官方画布那三种：**圆 / 软圆 / 方**。
 *
 * 圆和方是硬边（像素级二值覆盖），软圆是给边缘一圈羽化（`coverage` 从 1 渐到 0），
 * 所以画出来是"晕开"的。
 */
enum class BrushShape {
    ROUND,
    SOFT_ROUND,
    SQUARE,
}

/** 图像上的一块矩形（像素坐标）。历史栈按它存前后差量，所以叫"脏矩形"。 */
data class EditRect(val x: Int, val y: Int, val w: Int, val h: Int) {

    val isEmpty: Boolean get() = w <= 0 || h <= 0
    val area: Int get() = if (isEmpty) 0 else w * h

    /** 两个脏矩形合并（历史栈按"一笔"分组时不一定用到，但笔画累加要用）。 */
    fun union(other: EditRect): EditRect {
        if (isEmpty) return other
        if (other.isEmpty) return this
        val left = min(x, other.x)
        val top = min(y, other.y)
        val right = max(x + w, other.x + other.w)
        val bottom = max(y + h, other.y + other.h)
        return EditRect(left, top, right - left, bottom - top)
    }

    fun clampTo(width: Int, height: Int): EditRect {
        if (isEmpty) return this
        val left = x.coerceIn(0, max(0, width))
        val top = y.coerceIn(0, max(0, height))
        val right = (x + w).coerceIn(0, width)
        val bottom = (y + h).coerceIn(0, height)
        return EditRect(left, top, right - left, bottom - top)
    }

    companion object {
        val EMPTY = EditRect(0, 0, 0, 0)

        /**
         * 一段笔画的包围盒：线段两端各外扩 [radius]，再按边界裁剪。
         * 整段都落在画布外时返回 null（这时不用记历史）。
         */
        fun ofSegment(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            radius: Float,
            width: Int,
            height: Int,
        ): EditRect? {
            val left = min(x0, x1) - radius
            val top = min(y0, y1) - radius
            val right = max(x0, x1) + radius
            val bottom = max(y0, y1) + radius
            val x = kotlin.math.floor(left).toInt()
            val y = kotlin.math.floor(top).toInt()
            val w = kotlin.math.ceil(right).toInt() - x + 1
            val h = kotlin.math.ceil(bottom).toInt() - y + 1
            val rect = EditRect(x, y, w, h).clampTo(width, height)
            return if (rect.isEmpty) null else rect
        }
    }
}

/**
 * **画布编辑器的像素运算**（纯数组，不碰任何平台 API）。
 *
 * 和遮罩那套（[PixelMask] / `MaskBrush`）是**两件事**：
 *  · 遮罩是 1 字节/像素的二值位图，只用来告诉服务端"重绘哪一块"；
 *  · 这里动的是**图本身的 ARGB 像素**（画布编辑器 = 官方 Canvas 那套工具）。
 *
 * 颜色统一用 ARGB 打包整数（和 `ImageIo.fromArgb` / `pixelsOf` 一个口径）。
 * 混合按**非预乘**的 source-over 做（见 [overPixel]），所以半透明边上不会发黑。
 */
object ImageEditOps {

    /** 笔刷直径（原图像素）。上限给得比遮罩那套大一点 —— 画布上刷大块是常见操作。 */
    const val BRUSH_MIN_PIXELS = 1
    const val BRUSH_MAX_PIXELS = 600

    /** 默认笔刷：官方截图里 Draw 的默认值就是 45。 */
    const val DEFAULT_BRUSH_PIXELS = 45

    /** 完全透明（橡皮擦出来的就是它）。 */
    const val TRANSPARENT = 0x00000000

    /**
     * 把一段笔画盖到像素上（从 [x0],[y0] 到 [x1],[y1]，单点就是首尾重合）。
     *
     * 判定用**点到线段的最短距离**（和遮罩笔刷同一套算法），所以快速拖动是连续的一笔，
     * 而不是一串离散的圆点。
     *
     * @param shape [BrushShape.ROUND] / [BrushShape.SQUARE] 是硬边；[BrushShape.SOFT_ROUND]
     *              在半径内侧一半处开始羽化。
     * @param erasing true = 擦（alpha 按覆盖度衰减，RGB 不动）。
     * @return 真正被改动的矩形；整段在画布外或半径不足 1 时返回 null。
     */
    fun stampSegment(
        pixels: IntArray,
        width: Int,
        height: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float,
        shape: BrushShape,
        color: Int,
        erasing: Boolean,
        /**
         * **选区裁剪**：非 0 才允许改这个像素（长度 = width*height）；null = 不裁。
         *
         * "有选区时就只在选区内工作"这件事就是靠它实现的（见 `ImageEditSession`）。
         */
        clip: ByteArray? = null,
    ): EditRect? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val r = radius.coerceAtLeast(0.5f)
        val rect = EditRect.ofSegment(x0, y0, x1, y1, r, width, height) ?: return null

        val dx = x1 - x0
        val dy = y1 - y0
        val lengthSquared = dx * dx + dy * dy
        val soft = shape == BrushShape.SOFT_ROUND
        // 软笔从半径的一半开始羽化；半径很小时至少留 0.5px 的过渡带，免得退化成硬边
        val featherInner = if (soft) max(0f, r * 0.5f) else 0f

        for (y in rect.y until (rect.y + rect.h)) {
            val row = y * width
            val centerY = y + 0.5f
            for (x in rect.x until (rect.x + rect.w)) {
                val index = row + x
                if (clip != null && (index >= clip.size || clip[index].toInt() == 0)) continue
                val centerX = x + 0.5f
                val t = if (lengthSquared <= 0f) {
                    0f
                } else {
                    (((centerX - x0) * dx + (centerY - y0) * dy) / lengthSquared).coerceIn(0f, 1f)
                }
                val nearestX = x0 + dx * t
                val nearestY = y0 + dy * t
                val offX = abs(centerX - nearestX)
                val offY = abs(centerY - nearestY)

                val distance = if (shape == BrushShape.SQUARE) {
                    max(offX, offY)
                } else {
                    kotlin.math.hypot(offX, offY)
                }
                if (distance > r) continue

                val coverage = if (!soft) {
                    1f
                } else if (distance <= featherInner) {
                    1f
                } else {
                    ((r - distance) / (r - featherInner)).coerceIn(0f, 1f)
                }
                if (coverage <= 0f) continue

                pixels[index] = if (erasing) {
                    erasePixel(pixels[index], coverage)
                } else {
                    overPixel(pixels[index], color, coverage)
                }
            }
        }
        return rect
    }

    /** 取某个像素的颜色；越界返回 0（透明）。 */
    fun colorAt(pixels: IntArray, width: Int, height: Int, x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return TRANSPARENT
        val index = y * width + x
        if (index < 0 || index >= pixels.size) return TRANSPARENT
        return pixels[index]
    }

    /** 把一块矩形里的像素拷出来（历史栈的"前/后"两份就是它）。 */
    fun copyRect(pixels: IntArray, width: Int, height: Int, rect: EditRect): IntArray {
        val safe = rect.clampTo(width, height)
        if (safe.isEmpty) return IntArray(0)
        val out = IntArray(safe.area)
        for (row in 0 until safe.h) {
            val from = (safe.y + row) * width + safe.x
            System.arraycopy(pixels, from, out, row * safe.w, safe.w)
        }
        return out
    }

    /** [copyRect] 的反操作：把一份像素写回同一块矩形（撤销/重做用它）。 */
    fun writeRect(pixels: IntArray, width: Int, height: Int, rect: EditRect, data: IntArray) {
        val safe = rect.clampTo(width, height)
        if (safe.isEmpty) return
        if (data.size < safe.area) return
        for (row in 0 until safe.h) {
            val to = (safe.y + row) * width + safe.x
            System.arraycopy(data, row * safe.w, pixels, to, safe.w)
        }
    }

    /** 把整张（或一整块）刷成一个颜色 —— 画布编辑器的「清除」用它刷成透明。 */
    fun fillRect(
        pixels: IntArray,
        width: Int,
        height: Int,
        rect: EditRect,
        color: Int,
    ): EditRect {
        val safe = rect.clampTo(width, height)
        if (safe.isEmpty) return EditRect.EMPTY
        for (row in 0 until safe.h) {
            val offset = (safe.y + row) * width + safe.x
            java.util.Arrays.fill(pixels, offset, offset + safe.w, color)
        }
        return safe
    }

    /** 归一化笔刷直径 → 像素（1..[BRUSH_MAX_PIXELS]）。 */
    fun normalizedBrushPixels(requested: Int): Int =
        requested.coerceIn(BRUSH_MIN_PIXELS, BRUSH_MAX_PIXELS)

    /**
     * source-over 合成（非预乘）。
     *
     * `coverage` 是这一笔在这个像素上的覆盖度（硬边 = 1，软边 = 0..1）。
     * 底色是**不透明**时（生成图/导入图基本都是），结果就是朴素的线性插值；
     * 底色带 alpha 时按标准公式算，避免半透明边上出现"越描越黑"。
     */
    internal fun overPixel(dst: Int, src: Int, coverage: Float): Int {
        val a = coverage.coerceIn(0f, 1f) * (((src ushr 24) and 0xFF) / 255f)
        if (a <= 0f) return dst
        if (a >= 1f) return src

        val dstA = ((dst ushr 24) and 0xFF) / 255f
        val outA = a + dstA * (1f - a)
        if (outA <= 0f) return TRANSPARENT

        val srcR = (src ushr 16) and 0xFF
        val srcG = (src ushr 8) and 0xFF
        val srcB = src and 0xFF
        val dstR = (dst ushr 16) and 0xFF
        val dstG = (dst ushr 8) and 0xFF
        val dstB = dst and 0xFF

        val weight = dstA * (1f - a)
        val outR = ((srcR * a + dstR * weight) / outA).roundToInt().coerceIn(0, 255)
        val outG = ((srcG * a + dstG * weight) / outA).roundToInt().coerceIn(0, 255)
        val outB = ((srcB * a + dstB * weight) / outA).roundToInt().coerceIn(0, 255)
        val alpha = (outA * 255f).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    /**
     * destination-out：只衰减 alpha，**RGB 原样留着**。
     *
     * 为什么不像"往透明色上混"那样顺手把 RGB 也拉向 0：PNG 里那些半透明的边
     * 一旦 RGB 变黑，之后当底图用时边缘会发灰发脏（半透明像素的颜色也是要用的）。
     *
     * 例外：**alpha 归零时归一成 0x00000000**。留着 `0x00RRGGBB` 虽然显示上同样是透明，
     * 但"全透明"的相等判断（`== TRANSPARENT`、`pixels.all { … }`）会失效 ——
     * 画布清空/撤销这些逻辑全靠它。
     */
    internal fun erasePixel(dst: Int, coverage: Float): Int {
        val c = coverage.coerceIn(0f, 1f)
        if (c <= 0f) return dst
        val dstA = (dst ushr 24) and 0xFF
        val outA = (dstA * (1f - c)).roundToInt().coerceIn(0, 255)
        if (outA == 0) return TRANSPARENT
        // 半透明：RGB 保持不变（透明像素的 RGB 无所谓，但半透明像素的 RGB 有用）
        return (outA shl 24) or (dst and 0x00FFFFFF)
    }

    // -----------------------------------------------------------------------
    // 二期那四个"改图"的工具：油漆桶 / 模糊 / 仿制图章 + HSV 调整、调整画布
    // -----------------------------------------------------------------------

    /**
     * **油漆桶**：从 ([x],[y]) 出发，把"颜色与起点相近"的**四连通**区域刷成 [color]。
     *
     * 「相近」= **每个通道（含 alpha）的差都 ≤ [tolerance]**（0..255）。
     * 容忍度是官方那个滑杆的含义：0 = 只有完全同色的一块，255 = 整张图。
     *
     * 用的是**显式栈 + visited 标记**（不是递归）：4K 图上递归必爆栈，
     * 而且 visited 能保证每个像素最多进栈几次，不会自己咬自己。
     *
     * @return 被刷到的矩形；起点越界、尺寸不对、或起点本来就是这个颜色（无事可做）时返回 null
     */
    fun floodFill(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        color: Int,
        tolerance: Int,
        /** 选区裁剪：只有选区里的像素才允许被灌（种子在外面就直接不做）。 */
        clip: ByteArray? = null,
    ): EditRect? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        if (x < 0 || y < 0 || x >= width || y >= height) return null
        val seedIndex = y * width + x
        if (clip != null && (seedIndex >= clip.size || clip[seedIndex].toInt() == 0)) return null
        val start = pixels[seedIndex]
        if (start == color) return null
        val tol = tolerance.coerceIn(0, 255)

        val visited = BooleanArray(width * height)
        // 可增长的显式栈：不动 4×n 的固定数组（4K 图上那是几十 MB 的白占）
        var stack = IntArray(1024)
        var top = 0
        stack[top++] = y * width + x
        var minX = x
        var maxX = x
        var minY = y
        var maxY = y
        var painted = 0

        while (top > 0) {
            val index = stack[--top]
            if (visited[index]) continue
            visited[index] = true
            if (clip != null && (index >= clip.size || clip[index].toInt() == 0)) continue
            if (!similar(pixels[index], start, tol)) continue

            pixels[index] = color
            painted++
            val px = index % width
            val py = index / width
            if (px < minX) minX = px
            if (px > maxX) maxX = px
            if (py < minY) minY = py
            if (py > maxY) maxY = py

            if (px > 0 && !visited[index - 1]) {
                if (top == stack.size) stack = stack.copyOf(stack.size * 2)
                stack[top++] = index - 1
            }
            if (px < width - 1 && !visited[index + 1]) {
                if (top == stack.size) stack = stack.copyOf(stack.size * 2)
                stack[top++] = index + 1
            }
            if (py > 0 && !visited[index - width]) {
                if (top == stack.size) stack = stack.copyOf(stack.size * 2)
                stack[top++] = index - width
            }
            if (py < height - 1 && !visited[index + width]) {
                if (top == stack.size) stack = stack.copyOf(stack.size * 2)
                stack[top++] = index + width
            }
        }
        if (painted == 0) return null
        return EditRect(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    /** 每个通道的差都不超过 [tolerance] 才算"相近"。 */
    private fun similar(a: Int, b: Int, tolerance: Int): Boolean {
        if (a == b) return true
        val da = kotlin.math.abs(((a ushr 24) and 0xFF) - ((b ushr 24) and 0xFF))
        if (da > tolerance) return false
        val dr = kotlin.math.abs(((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF))
        if (dr > tolerance) return false
        val dg = kotlin.math.abs(((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF))
        if (dg > tolerance) return false
        val db = kotlin.math.abs((a and 0xFF) - (b and 0xFF))
        return db <= tolerance
    }

    /**
     * **模糊笔刷**：沿着这一笔把碰到的像素糊掉。
     *
     * @param intensity 0..100（官方那个强度滑杆）。0 = 不动，100 = 全用模糊值。
     * @param blurRadiusPixels 模糊半径（原图像素）—— 由界面用笔刷大小换算过来（约 1/3 笔径）。
     *
     * 关键点：**模糊要读"这一笔开始前的原图"**，不能边糊边读（那会顺着笔迹方向拖出一条尾巴）。
     * 所以先把要读的那块（矩形外扩一个模糊半径）快照下来，再做一次可分离方框模糊，
     * 最后按笔迹覆盖度把模糊值混回原像素。
     */
    fun blurStroke(
        pixels: IntArray,
        width: Int,
        height: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radiusPixels: Float,
        shape: BrushShape,
        blurRadiusPixels: Int,
        intensity: Int,
        /** 选区裁剪（见 [stampSegment]）。 */
        clip: ByteArray? = null,
    ): EditRect? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val amount = (intensity.coerceIn(0, 100)) / 100f
        if (amount <= 0f) return null
        val r = blurRadiusPixels.coerceIn(1, 64)
        val rect = EditRect.ofSegment(x0, y0, x1, y1, radiusPixels.coerceAtLeast(0.5f), width, height)
            ?: return null

        // 读源：矩形外扩 r（模糊要看到周围的像素）
        val srcRect = EditRect(rect.x - r, rect.y - r, rect.w + 2 * r, rect.h + 2 * r)
            .clampTo(width, height)
        if (srcRect.isEmpty) return null
        val snapshot = copyRect(pixels, width, height, srcRect)
        val blurred = boxBlur(snapshot, srcRect.w, srcRect.h, r)

        val soft = shape == BrushShape.SOFT_ROUND
        val featherInner = if (soft) radiusPixels * 0.5f else 0f
        val dx = x1 - x0
        val dy = y1 - y0
        val lengthSquared = dx * dx + dy * dy

        for (y in rect.y until (rect.y + rect.h)) {
            val centerY = y + 0.5f
            for (x in rect.x until (rect.x + rect.w)) {
                val clipIndex = y * width + x
                if (clip != null && (clipIndex >= clip.size || clip[clipIndex].toInt() == 0)) continue
                val centerX = x + 0.5f
                val t = if (lengthSquared <= 0f) {
                    0f
                } else {
                    (((centerX - x0) * dx + (centerY - y0) * dy) / lengthSquared).coerceIn(0f, 1f)
                }
                val offX = abs(centerX - (x0 + dx * t))
                val offY = abs(centerY - (y0 + dy * t))
                val distance = if (shape == BrushShape.SQUARE) max(offX, offY) else kotlin.math.hypot(offX, offY)
                if (distance > radiusPixels) continue
                val coverage = if (!soft || distance <= featherInner) {
                    1f
                } else {
                    ((radiusPixels - distance) / (radiusPixels - featherInner)).coerceIn(0f, 1f)
                }
                if (coverage <= 0f) continue
                val index = y * width + x
                val blurredIndex = (y - srcRect.y) * srcRect.w + (x - srcRect.x)
                if (blurredIndex < 0 || blurredIndex >= blurred.size) continue
                pixels[index] = lerpColor(pixels[index], blurred[blurredIndex], coverage * amount)
            }
        }
        return rect
    }

    /**
     * **仿制图章**：把"源点那块"按笔迹刷到当前位置。
     *
     * @param offsetX/offsetY 源点相对当前笔尖的偏移（界面在按下第一笔时定下来，整笔不变）。
     *        源像素越界就不动（不取边界像素去糊，那会在画布边上拖出一条脏边）。
     */
    fun cloneStroke(
        pixels: IntArray,
        width: Int,
        height: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radiusPixels: Float,
        shape: BrushShape,
        offsetX: Int,
        offsetY: Int,
        /** 选区裁剪（见 [stampSegment]）。 */
        clip: ByteArray? = null,
    ): EditRect? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val rect = EditRect.ofSegment(x0, y0, x1, y1, radiusPixels.coerceAtLeast(0.5f), width, height)
            ?: return null
        val soft = shape == BrushShape.SOFT_ROUND
        val featherInner = if (soft) radiusPixels * 0.5f else 0f
        val dx = x1 - x0
        val dy = y1 - y0
        val lengthSquared = dx * dx + dy * dy
        var touched = 0

        for (y in rect.y until (rect.y + rect.h)) {
            val centerY = y + 0.5f
            for (x in rect.x until (rect.x + rect.w)) {
                val clipIndex = y * width + x
                if (clip != null && (clipIndex >= clip.size || clip[clipIndex].toInt() == 0)) continue
                val centerX = x + 0.5f
                val t = if (lengthSquared <= 0f) {
                    0f
                } else {
                    (((centerX - x0) * dx + (centerY - y0) * dy) / lengthSquared).coerceIn(0f, 1f)
                }
                val offX = abs(centerX - (x0 + dx * t))
                val offY = abs(centerY - (y0 + dy * t))
                val distance = if (shape == BrushShape.SQUARE) max(offX, offY) else kotlin.math.hypot(offX, offY)
                if (distance > radiusPixels) continue
                val coverage = if (!soft || distance <= featherInner) {
                    1f
                } else {
                    ((radiusPixels - distance) / (radiusPixels - featherInner)).coerceIn(0f, 1f)
                }
                if (coverage <= 0f) continue
                val srcX = x + offsetX
                val srcY = y + offsetY
                if (srcX < 0 || srcY < 0 || srcX >= width || srcY >= height) continue
                val index = y * width + x
                pixels[index] = overPixel(pixels[index], pixels[srcY * width + srcX], coverage)
                touched++
            }
        }
        return if (touched == 0) null else rect
    }

    /**
     * **HSV 调整**：把 [source] 逐像素调色后写进 [target]（两个数组都按 `width*height` 读）。
     *
     * @param hueShift 色相偏移，**度**（-180..180）
     * @param saturationScale 饱和度乘/加：-100..100（%），-100 = 去色
     * @param valueScale 明度：-100..100（%）
     *
     * 为什么是"源/目标两个数组"而不是就地改：对话框里拖滑杆要**实时预览**，
     * 每次都从同一份快照重算，才能来回拖都不累积（就地改的话越拖越黑）。
     */
    fun hsvAdjust(
        source: IntArray,
        target: IntArray,
        count: Int,
        hueShift: Float,
        saturationScale: Float,
        valueScale: Float,
    ) {
        val n = minOf(count, source.size, target.size)
        for (i in 0 until n) {
            val c = source[i]
            val a = (c ushr 24) and 0xFF
            if (a == 0) {
                target[i] = c
                continue
            }
            val rgb = rgbToHsv((c ushr 16) and 0xFF, (c ushr 8) and 0xFF, c and 0xFF)
            val h = (rgb[0] + hueShift + 360f) % 360f
            val s = (rgb[1] * (1f + saturationScale / 100f)).coerceIn(0f, 1f)
            val v = (rgb[2] * (1f + valueScale / 100f)).coerceIn(0f, 1f)
            val out = hsvToRgb(h, s, v)
            target[i] = (a shl 24) or (out[0] shl 16) or (out[1] shl 8) or out[2]
        }
    }

    /**
     * **调整画布大小**：内容按 ([left],[top]) 放到新画布上，露出来的地方是透明。
     *
     * `left`/`top` 是**内容在新画布里的左上角**（负数 = 从左边/上边裁掉一块）。
     * 尺寸校验交给调用方（`EditRect` / `NAI 尺寸`那套）。
     */
    fun resizeCanvas(
        pixels: IntArray,
        width: Int,
        height: Int,
        newWidth: Int,
        newHeight: Int,
        left: Int,
        top: Int,
    ): IntArray {
        val out = IntArray(newWidth * newHeight)
        if (newWidth <= 0 || newHeight <= 0) return out
        val fromX = max(0, -left)
        val fromY = max(0, -top)
        val toX = max(0, left)
        val toY = max(0, top)
        val copyW = min(width - fromX, newWidth - toX)
        val copyH = min(height - fromY, newHeight - toY)
        if (copyW <= 0 || copyH <= 0) return out
        for (row in 0 until copyH) {
            System.arraycopy(
                pixels,
                (fromY + row) * width + fromX,
                out,
                (toY + row) * newWidth + toX,
                copyW,
            )
        }
        return out
    }

    /** 可分离方框模糊（两趟一维均值）—— 比逐个像素开窗快一个量级。 */
    private fun boxBlur(src: IntArray, width: Int, height: Int, radius: Int): IntArray {
        if (radius <= 0 || width <= 0 || height <= 0) return src.copyOf()
        val horizontal = IntArray(src.size)
        val window = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val c = src[row + i.coerceIn(0, width - 1)]
                a += (c ushr 24) and 0xFF
                r += (c ushr 16) and 0xFF
                g += (c ushr 8) and 0xFF
                b += c and 0xFF
            }
            for (x in 0 until width) {
                horizontal[row + x] = ((a / window) shl 24) or ((r / window) shl 16) or
                    ((g / window) shl 8) or (b / window)
                val outIndex = (x - radius).coerceIn(0, width - 1)
                val inIndex = (x + radius + 1).coerceIn(0, width - 1)
                val out = src[row + outIndex]
                val into = src[row + inIndex]
                a += ((into ushr 24) and 0xFF) - ((out ushr 24) and 0xFF)
                r += ((into ushr 16) and 0xFF) - ((out ushr 16) and 0xFF)
                g += ((into ushr 8) and 0xFF) - ((out ushr 8) and 0xFF)
                b += (into and 0xFF) - (out and 0xFF)
            }
        }
        val out = IntArray(src.size)
        for (x in 0 until width) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val c = horizontal[i.coerceIn(0, height - 1) * width + x]
                a += (c ushr 24) and 0xFF
                r += (c ushr 16) and 0xFF
                g += (c ushr 8) and 0xFF
                b += c and 0xFF
            }
            for (y in 0 until height) {
                out[y * width + x] = ((a / window) shl 24) or ((r / window) shl 16) or
                    ((g / window) shl 8) or (b / window)
                val outIndex = (y - radius).coerceIn(0, height - 1)
                val inIndex = (y + radius + 1).coerceIn(0, height - 1)
                val o = horizontal[outIndex * width + x]
                val i = horizontal[inIndex * width + x]
                a += ((i ushr 24) and 0xFF) - ((o ushr 24) and 0xFF)
                r += ((i ushr 16) and 0xFF) - ((o ushr 16) and 0xFF)
                g += ((i ushr 8) and 0xFF) - ((o ushr 8) and 0xFF)
                b += (i and 0xFF) - (o and 0xFF)
            }
        }
        return out
    }

    /** 两个颜色按 [amount] 线性插值（含 alpha）。 */
    private fun lerpColor(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        if (t <= 0f) return from
        if (t >= 1f) return to
        val a = lerpChannel((from ushr 24) and 0xFF, (to ushr 24) and 0xFF, t)
        val r = lerpChannel((from ushr 16) and 0xFF, (to ushr 16) and 0xFF, t)
        val g = lerpChannel((from ushr 8) and 0xFF, (to ushr 8) and 0xFF, t)
        val b = lerpChannel(from and 0xFF, to and 0xFF, t)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerpChannel(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).roundToInt().coerceIn(0, 255)

    /** RGB(0..255) → HSV（H 0..360、S/V 0..1）。 */
    private fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maxC = maxOf(rf, gf, bf)
        val minC = minOf(rf, gf, bf)
        val delta = maxC - minC
        val h = when {
            delta <= 1e-6f -> 0f
            maxC == rf -> 60f * (((gf - bf) / delta) % 6f)
            maxC == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }
        val s = if (maxC <= 1e-6f) 0f else delta / maxC
        return floatArrayOf(if (h < 0f) h + 360f else h, s, maxC)
    }

    /** HSV → RGB(0..255)。 */
    private fun hsvToRgb(h: Float, s: Float, v: Float): IntArray {
        val c = v * s
        val hp = (h % 360f) / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        return intArrayOf(
            ((r1 + m) * 255f).roundToInt().coerceIn(0, 255),
            ((g1 + m) * 255f).roundToInt().coerceIn(0, 255),
            ((b1 + m) * 255f).roundToInt().coerceIn(0, 255),
        )
    }
}

/** 一笔的**动作类型**：画 / 擦 / 模糊 / 图章。 */
enum class StrokeMode {
    PAINT,
    ERASE,
    BLUR,
    CLONE,
}

/** 一笔的参数（在 [beginStroke] 那一刻定下来，整笔不变）。 */
data class StrokeSpec(
    val mode: StrokeMode = StrokeMode.PAINT,
    val brushPixels: Int = ImageEditOps.DEFAULT_BRUSH_PIXELS,
    val shape: BrushShape = BrushShape.ROUND,
    /** 画笔 / 填充色（PAINT 用；CLONE 与 BLUR 忽略）。 */
    val color: Int = 0xFF000000.toInt(),
    /** 模糊强度 0..100（BLUR 用）。 */
    val blurIntensity: Int = 50,
    /** 图章的源偏移（CLONE 用，**原图像素**）。 */
    val cloneOffsetX: Int = 0,
    val cloneOffsetY: Int = 0,
)
