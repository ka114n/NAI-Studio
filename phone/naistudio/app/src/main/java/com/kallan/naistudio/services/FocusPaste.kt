package com.kallan.naistudio.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kallan.naistudio.models.FocusedInpaint
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * **聚焦重绘的本地回贴**：把服务端返回的图**只把框那块**贴回原图。
 *
 * 为什么不是直接用 `InpaintComposite.composePng`：那条路是"整图 + 请求尺寸遮罩"，
 * 而聚焦重绘发出去的是**裁剪+放大**过的一块（请求尺寸 ≠ 源图尺寸），所以这里要
 * 把请求图里的**框内**区域按比例映射回源图的框位置，再带一圈羽化贴回去 —— 框外（含上下文那圈）
 * 一个像素都不动（与桌面线 §37 的口径一致：真机验证过"框外 0 像素改动"）。
 */
object FocusPaste {

    /**
     * @param sourcePng 原图（**源尺寸**）的 PNG 字节
     * @param generatedPng 服务端返回的图（请求尺寸 = 裁剪缩放后）
     * @param plan 聚焦方案（含裁剪矩形、框在请求图里的位置、请求尺寸）
     * @param feather 框边缘羽化像素（按**源图**尺度；0 = 硬边）
     * @return 结果 PNG（源尺寸）；任何一步失败返回 null，上层原样用服务端的图
     */
    fun pasteFrame(
        sourcePng: ByteArray,
        generatedPng: ByteArray,
        plan: FocusedInpaint.Plan,
        feather: Int,
    ): ByteArray? = runCatching {
        val source = BitmapFactory.decodeByteArray(sourcePng, 0, sourcePng.size) ?: return null
        val generated = BitmapFactory.decodeByteArray(generatedPng, 0, generatedPng.size) ?: return null
        val out = source.copy(Bitmap.Config.ARGB_8888, true) ?: return null

        // 框在**源图**里的位置（裁剪偏移 + 框在裁剪图里的偏移）
        val frameLeft = (plan.cropX + plan.frameInCrop.x).coerceIn(0, out.width)
        val frameTop = (plan.cropY + plan.frameInCrop.y).coerceIn(0, out.height)
        val frameRight = (plan.cropX + plan.frameInCrop.x + plan.frameInCrop.w).coerceIn(frameLeft, out.width)
        val frameBottom = (plan.cropY + plan.frameInCrop.y + plan.frameInCrop.h).coerceIn(frameTop, out.height)
        val frameW = frameRight - frameLeft
        val frameH = frameBottom - frameTop
        if (frameW <= 0 || frameH <= 0) return null

        // 源图 → 请求图 的映射（裁剪区等比缩放）
        val mapX = plan.requestW.toFloat() / plan.cropW.toFloat()
        val mapY = plan.requestH.toFloat() / plan.cropH.toFloat()
        val pad = max(0, feather)

        val genRow = IntArray(generated.width)
        val rowSource = IntArray(frameW)
        val rowOut = IntArray(frameW)
        for (y in 0 until frameH) {
            val sourceY = frameTop + y
            val genY = ((frameTop + y - plan.cropY) * mapY).toInt().coerceIn(0, generated.height - 1)
            // 整行读进来（步长必须 = 读宽），再按映射取框内的列
            generated.getPixels(genRow, 0, generated.width, 0, genY, generated.width, 1)
            out.getPixels(rowSource, 0, frameW, frameLeft, sourceY, frameW, 1)
            for (x in 0 until frameW) {
                val sourceX = frameLeft + x
                val genX = ((sourceX - plan.cropX) * mapX).toInt().coerceIn(0, generated.width - 1)
                val src = genRow[genX]
                // 边到边线性羽化：核心区 alpha=255，往外 pad 像素内渐隐
                val alpha = when {
                    pad <= 0 -> 255
                    else -> {
                        val dx = min(x, frameW - 1 - x)
                        val dy = min(y, frameH - 1 - y)
                        val d = min(dx, dy)
                        if (d >= pad) 255 else (255 * d / pad).coerceIn(0, 255)
                    }
                }
                rowOut[x] = if (alpha >= 255) {
                    src
                } else if (alpha <= 0) {
                    rowSource[x]
                } else {
                    blend(src, rowSource[x], alpha)
                }
            }
            out.setPixels(rowOut, 0, frameW, frameLeft, sourceY, frameW, 1)
        }

        val bytes = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        source.recycle()
        generated.recycle()
        out.recycle()
        bytes.toByteArray()
    }.getOrNull()

    /** 按 alpha（0..255）把 [top] 叠到 [base] 上（都是 ARGB_8888 的 int）。 */
    private fun blend(top: Int, base: Int, alpha: Int): Int {
        val a = alpha / 255f
        val ia = 1f - a
        val r = ((top shr 16 and 0xFF) * a + (base shr 16 and 0xFF) * ia).toInt()
        val g = ((top shr 8 and 0xFF) * a + (base shr 8 and 0xFF) * ia).toInt()
        val b = ((top and 0xFF) * a + (base and 0xFF) * ia).toInt()
        return (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }
}
