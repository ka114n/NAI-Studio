package com.kallan.naistudio.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kallan.naistudio.models.PixelMask
import com.kallan.naistudio.models.fitNaiImageSize
import java.io.ByteArrayOutputStream

/**
 * 遮罩 / 底图的位图编解码。
 *
 * 遮罩 PNG 的口径照参考实现（`mobile/lib/inpaint/inpaint_mask.dart` 的 `renderInpaintMask()`）：
 * **黑底 + 白选区、alpha 恒 255**、尺寸等于请求尺寸（源图 64 对齐后的尺寸）。
 * 白色 = 要重做的区域。
 */
object MaskCodec {

    /**
     * 局部重做的请求尺寸：64 对齐 + 总面积上限。
     *
     * 与 `GenerateParams.normalized()` 用同一个函数（[fitNaiImageSize]），
     * 所以"我们渲染底图/遮罩用的尺寸"和"payload 里 width/height"必然一致。
     */
    fun requestSize(width: Int, height: Int): Pair<Int, Int> = fitNaiImageSize(width, height)

    /** 只读文件头拿像素尺寸（不解码整张图）。 */
    fun imageSize(path: String): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return try {
            BitmapFactory.decodeFile(path, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth to options.outHeight
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 全尺寸解码（送请求的底图必须用原图，不能被预览的降采样版本污染）。 */
    fun decodeFull(path: String): Bitmap? = try {
        BitmapFactory.decodeFile(path)
    } catch (e: OutOfMemoryError) {
        null
    } catch (e: Exception) {
        null
    }

    /** 底图 PNG：缩放到请求尺寸（带滤波，避免缩放锯齿）。 */
    fun basePng(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val scaled = if (bitmap.width == width && bitmap.height == height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        return scaled.toPngBytes()
    }

    /**
     * 逐像素遮罩 → PNG（黑底 + 白选区、alpha 恒 255）。
     *
     * 遮罩尺寸就是原图像素尺寸；只有原图不是 64 倍数时才会缩放一次
     * （用最近邻，保持"非黑即白"，不要让边缘糊成灰的）。
     */
    fun maskPng(mask: PixelMask, width: Int, height: Int): ByteArray {
        val pixels = IntArray(mask.width * mask.height)
        for (index in mask.pixels.indices) {
            pixels[index] = if (mask.pixels[index].toInt() != 0) WHITE else BLACK
        }
        val bitmap = Bitmap.createBitmap(pixels, mask.width, mask.height, Bitmap.Config.ARGB_8888)
        val scaled = if (mask.width == width && mask.height == height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, false)
        }
        val bytes = scaled.toPngBytes()
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        return bytes
    }

    /**
     * 局部重做的遮罩准备：**缩放到请求尺寸 → 吸附 8px 潜空间网格**（节点的口径）。
     *
     * 为什么必须吸附（`modular_nodes.py:166-200` 的 `prepare_official_inpaint_mask`）：
     * 服务端是按潜空间（1/8 分辨率）理解遮罩的。像素级精确的遮罩在它那里会被阈值化，
     * 边界和你画的那条线对不上，结果就是**涂区外圈留一圈"半重绘"的带**。
     * 官方客户端（和这个节点）都是先吸附到格子再发。
     *
     * 顺序：调用方先做 `mask_expand` 膨胀，再进这里（膨胀正好补偿吸附带来的收缩）。
     *
     * @return 吸附后的遮罩（尺寸 = 请求尺寸）；**对齐后为空**时返回 null，
     *         调用方应提示"把区域画大一些"，而不是发一次白花钱的请求。
     */
    fun inpaintMask(mask: PixelMask, requestWidth: Int, requestHeight: Int): PixelMask? {
        val scaled = scaleNearest(mask.pixels, mask.width, mask.height, requestWidth, requestHeight)
        val snapped = snapToLatentGrid(scaled, requestWidth, requestHeight)
        val result = PixelMask(requestWidth, requestHeight, snapped)
        return if (result.isEmpty()) null else result
    }

    /**
     * 最近邻缩放（纯数组，可单测）。
     * 只有源图不是 64 倍数时才会真正缩放。
     */
    internal fun scaleNearest(
        pixels: ByteArray,
        width: Int,
        height: Int,
        newWidth: Int,
        newHeight: Int,
    ): ByteArray {
        if (width == newWidth && height == newHeight) return pixels.copyOf()
        if (width <= 0 || height <= 0 || newWidth <= 0 || newHeight <= 0) return ByteArray(0)
        val out = ByteArray(newWidth * newHeight)
        for (y in 0 until newHeight) {
            val sourceY = (y.toLong() * height / newHeight).toInt().coerceIn(0, height - 1)
            val sourceRow = sourceY * width
            val targetRow = y * newWidth
            for (x in 0 until newWidth) {
                val sourceX = (x.toLong() * width / newWidth).toInt().coerceIn(0, width - 1)
                out[targetRow + x] = pixels[sourceRow + sourceX]
            }
        }
        return out
    }

    /**
     * 吸附到 8×8 潜空间网格（纯数组，可单测）。
     *
     * 口径照插件：把遮罩降到 1/8（那里是双线性，这里用等价的**块平均**），
     * **以 155/255 为阈值**二值化，再用最近邻放大回原尺寸 —— 于是遮罩边界落在格子边上。
     * 副作用：很细的笔迹会被吃掉（插件里那句"请把遮罩区域画大一些"就是这个原因），
     * 所以调用前先做 `mask_expand`。
     */
    internal fun snapToLatentGrid(
        pixels: ByteArray,
        width: Int,
        height: Int,
        threshold: Int = LATENT_THRESHOLD,
    ): ByteArray {
        if (width <= 0 || height <= 0 || pixels.isEmpty()) return ByteArray(0)
        val cell = LATENT_SCALE
        val out = ByteArray(width * height)
        for (top in 0 until height step cell) {
            for (left in 0 until width step cell) {
                var sum = 0L
                var count = 0
                val bottom = minOf(top + cell, height)
                val right = minOf(left + cell, width)
                for (y in top until bottom) {
                    val row = y * width
                    for (x in left until right) {
                        sum += pixels[row + x].toInt() and 0xFF
                        count++
                    }
                }
                if (count == 0) continue
                val average = (sum / count).toInt()
                val value = if (average >= threshold) 255.toByte() else 0
                for (y in top until bottom) {
                    val row = y * width
                    for (x in left until right) out[row + x] = value
                }
            }
        }
        return out
    }

    private fun Bitmap.toPngBytes(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    /** 潜空间下采样倍率。 */
    private const val LATENT_SCALE = 8

    /** 官方客户端的二值化阈值（155/255）。 */
    private const val LATENT_THRESHOLD = 155

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()
}
