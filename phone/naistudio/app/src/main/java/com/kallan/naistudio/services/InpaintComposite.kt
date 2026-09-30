package com.kallan.naistudio.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kallan.naistudio.models.PixelMask
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 局部重做的**本地回贴**（对应节点里的 `add_original_image` / `mask_feather` / `edge_protection`）。
 *
 * 为什么要有它：infill 请求固定发 `add_original_image = false`（官方客户端要"原始 infill"，
 * 自己贴回原图）。服务端返回的是整张图，未遮罩区域即使内容没变也可能被重编码/轻微改变；
 * 按官方口径贴回去，未涂区域就和原图**逐像素一致**。
 *
 * 算法口径来自插件 `modular_nodes.py:203-230` 与参考实现 `compositeInpaintResult`（两边一致）：
 *   1. 遮罩最近邻缩到 **1/8 潜空间**
 *   2. 在潜空间上**膨胀 4 格**
 *   3. 最近邻放大回原尺寸
 *   4. **模糊半径 = max(20, mask_feather)**
 *   5. `out = gen·α + original·(1−α)`
 * `edge_protection = false` 时跳过 2–4，用硬边（插件传 feather = -1 就是这条路径）。
 *
 * 纯计算的三个函数不碰 android.graphics，所以可以写 JVM 单测；
 * 只有最后的 PNG 解码/编码需要 Android。
 */
object InpaintComposite {

    /** 潜空间下采样倍率（NovelAI 是 1/8）。 */
    private const val LATENT_SCALE = 8

    /** 潜空间上固定膨胀 4 格（官方客户端口径）。 */
    private const val LATENT_DILATE_CELLS = 4

    /** 模糊半径下限（官方客户端是 20）。 */
    private const val MIN_FEATHER = 20

    /**
     * 生成回贴用的软边 alpha（长度 = width*height，值 0..255）。
     *
     * @param feather        `mask_feather`（0..64）
     * @param edgeProtection false → 硬边（直接用膨胀后的遮罩当 alpha）
     */
    fun blendAlpha(
        mask: PixelMask,
        feather: Int,
        edgeProtection: Boolean,
    ): ByteArray {
        val width = mask.width
        val height = mask.height
        if (!edgeProtection) return mask.pixels.copyOf()

        val latentWidth = max(1, width / LATENT_SCALE)
        val latentHeight = max(1, height / LATENT_SCALE)

        // 1) 最近邻降到潜空间（与插件/参考实现一致：先取"有任一像素被涂"）
        val latent = ByteArray(latentWidth * latentHeight)
        for (ly in 0 until latentHeight) {
            for (lx in 0 until latentWidth) {
                var value: Byte = 0
                val x0 = lx * LATENT_SCALE
                val y0 = ly * LATENT_SCALE
                outer@ for (y in y0 until minOf(y0 + LATENT_SCALE, height)) {
                    for (x in x0 until minOf(x0 + LATENT_SCALE, width)) {
                        if (mask.pixels[y * width + x].toInt() != 0) {
                            value = 255.toByte()
                            break@outer
                        }
                    }
                }
                latent[ly * latentWidth + lx] = value
            }
        }

        // 2) 潜空间膨胀 4 格（切比雪夫距离）
        val dilated = dilateCells(latent, latentWidth, latentHeight, LATENT_DILATE_CELLS)

        // 3) 最近邻放大回原尺寸
        val expanded = ByteArray(width * height)
        for (y in 0 until height) {
            val ly = (y / LATENT_SCALE).coerceAtMost(latentHeight - 1)
            for (x in 0 until width) {
                val lx = (x / LATENT_SCALE).coerceAtMost(latentWidth - 1)
                expanded[y * width + x] = dilated[ly * latentWidth + lx]
            }
        }

        // 4) 模糊（可分离的两趟滑动平均，等价于 box blur）
        val radius = max(MIN_FEATHER, feather).coerceAtLeast(1)
        return boxBlur(expanded, width, height, radius)
    }

    /**
     * 按 alpha 把生成图混回原图。
     *
     * @param generated 生成图（ARGB）
     * @param original  送请求用的底图（ARGB，同尺寸）
     * @param alpha     长度 = 像素数，0 = 完全保留原图，255 = 完全用生成图
     */
    fun composite(generated: IntArray, original: IntArray, alpha: ByteArray): IntArray {
        if (generated.size != original.size || alpha.size != generated.size) return generated
        val out = IntArray(generated.size)
        for (index in generated.indices) {
            val a = alpha[index].toInt() and 0xFF
            if (a == 255) {
                out[index] = generated[index]
                continue
            }
            if (a == 0) {
                out[index] = original[index]
                continue
            }
            val inv = 255 - a
            val gen = generated[index]
            val org = original[index]
            val r = (((gen ushr 16) and 0xFF) * a + ((org ushr 16) and 0xFF) * inv) / 255
            val g = (((gen ushr 8) and 0xFF) * a + ((org ushr 8) and 0xFF) * inv) / 255
            val b = ((gen and 0xFF) * a + (org and 0xFF) * inv) / 255
            val aOut = (((gen ushr 24) and 0xFF) * a + ((org ushr 24) and 0xFF) * inv) / 255
            out[index] = (aOut shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    /**
     * Android 侧入口：把 [generatedPng] 按遮罩贴回 [originalPng] 上。
     *
     * 尺寸不一致（服务端返回了别的尺寸）时返回 null —— 调用方就退回原始返回图，
     * 不要在这种边界上猜。
     */
    fun composePng(
        generatedPng: ByteArray,
        originalPng: ByteArray,
        mask: PixelMask,
        feather: Int,
        edgeProtection: Boolean,
    ): ByteArray? {
        val generated = decode(generatedPng) ?: return null
        val original = decode(originalPng) ?: return null
        if (generated.width != original.width || generated.height != original.height) return null
        if (generated.width != mask.width || generated.height != mask.height) return null

        val generatedPixels = IntArray(generated.width * generated.height)
        val originalPixels = IntArray(original.width * original.height)
        generated.getPixels(generatedPixels, 0, generated.width, 0, 0, generated.width, generated.height)
        original.getPixels(originalPixels, 0, original.width, 0, 0, original.width, original.height)

        val alpha = blendAlpha(mask, feather, edgeProtection)
        val composed = composite(generatedPixels, originalPixels, alpha)

        val out = Bitmap.createBitmap(generated.width, generated.height, Bitmap.Config.ARGB_8888)
        out.setPixels(composed, 0, generated.width, 0, 0, generated.width, generated.height)
        val stream = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.PNG, 100, stream)
        generated.recycle()
        original.recycle()
        out.recycle()
        return stream.toByteArray()
    }

    // -----------------------------------------------------------------------
    // 纯数组实现（可单测）
    // -----------------------------------------------------------------------

    /** 潜空间上的方形膨胀（切比雪夫距离 ≤ radius）。 */
    internal fun dilateCells(cells: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
        if (radius <= 0) return cells.copyOf()
        val out = ByteArray(cells.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var value: Byte = 0
                outer@ for (dy in -radius..radius) {
                    val ny = y + dy
                    if (ny < 0 || ny >= height) continue
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        if (nx < 0 || nx >= width) continue
                        if (cells[ny * width + nx].toInt() != 0) {
                            value = 255.toByte()
                            break@outer
                        }
                    }
                }
                out[y * width + x] = value
            }
        }
        return out
    }

    /**
     * 盒子模糊（可分离，边缘用钳制索引）。
     * 官方客户端在潜空间放大后用高斯；这里用两趟平均近似，半径同样取 max(20, feather)。
     */
    internal fun boxBlur(pixels: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
        val r = radius.coerceAtLeast(0)
        if (r == 0) return pixels.copyOf()
        val window = r * 2 + 1

        val horizontal = ByteArray(pixels.size)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var sum = 0
                for (dx in -r..r) {
                    val nx = (x + dx).coerceIn(0, width - 1)
                    sum += pixels[row + nx].toInt() and 0xFF
                }
                horizontal[row + x] = (sum / window).toByte()
            }
        }

        val out = ByteArray(pixels.size)
        for (x in 0 until width) {
            for (y in 0 until height) {
                var sum = 0
                for (dy in -r..r) {
                    val ny = (y + dy).coerceIn(0, height - 1)
                    sum += horizontal[ny * width + x].toInt() and 0xFF
                }
                out[y * width + x] = (sum / window).toByte()
            }
        }
        return out
    }

    private fun decode(bytes: ByteArray): Bitmap? = try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: OutOfMemoryError) {
        null
    } catch (e: Exception) {
        null
    }

    /** 供 UI 显示"回贴软边覆盖了多少"之类的诊断（暂未使用，保留给后续提示）。 */
    internal fun coveragePercent(alpha: ByteArray): Int {
        if (alpha.isEmpty()) return 0
        var sum = 0L
        for (value in alpha) sum += (value.toInt() and 0xFF)
        return (sum.toDouble() * 100.0 / (alpha.size.toDouble() * 255.0)).roundToInt()
    }
}
