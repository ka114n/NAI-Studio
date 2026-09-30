package com.kallan.naistudio.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * 图库缩略图的**磁盘缓存**。
 *
 * 为什么需要：图库一屏要显示十几张图，原图是 832×1216 的 PNG（每张几 MB）。
 * 每次都从原图降采样解码，既要读整份文件、又要走一遍 PNG 解码，页面重建时格外明显。
 * 这里第一次显示时把降采样结果写成一张小图（有 alpha 存 PNG，否则存 JPEG），
 * 之后直接从小图解码 —— 读的字节数少一个数量级。
 *
 * 缓存文件放在 `filesDir/thumbnails/`，文件名里带**原文件长度 + 修改时间**，
 * 所以原图被改名/替换后会自然失效，不需要额外的失效逻辑。
 */
object Thumbnails {
    /** 超过这个体积就按最久未使用裁剪。 */
    private const val MAX_CACHE_BYTES = 200L * 1024 * 1024

    /**
     * 缓存目录带版本号：改过缩略图生成算法（比如补上滤波缩放）之后直接把版本号 +1，
     * 旧文件自然失效，不会出现「代码改了但界面没变」的怪现象。
     */
    private const val DIR_NAME = "thumbnails_v2"

    private fun dir(context: Context): File =
        File(context.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /**
     * 返回可直接解码的**小图路径**；失败时返回 null（调用方回落到原图）。
     */
    fun fileFor(context: Context, sourcePath: String, maxDimension: Int): String? {
        val source = File(sourcePath)
        if (!source.isFile) return null

        val stamp = "${source.length()}_${source.lastModified()}"
        val key = "${sourcePath.hashCode().toUInt().toString(16)}_${stamp}_$maxDimension"
        val existing = dir(context).listFiles { f -> f.name.startsWith(key) }?.firstOrNull()
        if (existing != null && existing.length() > 0) {
            existing.setLastModified(System.currentTimeMillis())
            return existing.absolutePath
        }

        val bitmap = decodeSampled(sourcePath, maxDimension) ?: return null
        return try {
            // 有透明通道的（透明背景图）必须存 PNG，否则会变黑底
            val png = bitmap.hasAlpha()
            val target = File(dir(context), if (png) "$key.png" else "$key.jpg")
            FileOutputStream(target).use { out ->
                bitmap.compress(
                    if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                    if (png) 100 else 88,
                    out,
                )
            }
            bitmap.recycle()
            target.absolutePath
        } catch (e: Exception) {
            bitmap.recycle()
            null
        }
    }

    /** 启动时调用一次：按最久未使用裁到上限以内。 */
    fun prune(context: Context) {
        val files = dir(context).listFiles()?.filter { it.isFile } ?: return
        val total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return

        var remaining = total
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (remaining <= MAX_CACHE_BYTES) return@forEach
            val size = file.length()
            if (file.delete()) remaining -= size
        }
    }

    private fun decodeSampled(path: String, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null

        // 关键一步：`inSampleSize` 只是「隔几个像素取一个」，不做滤波，直接拿去画就会有锯齿。
        // 这里再按目标尺寸做一次**带滤波**的缩放（createScaledBitmap 的 filter = true），
        // 缩略图就干净了 —— 观感上「小图有锯齿」的根因就在这。
        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= maxDimension) return decoded

        val ratio = maxDimension.toFloat() / longest
        val targetWidth = (decoded.width * ratio).roundToInt().coerceAtLeast(1)
        val targetHeight = (decoded.height * ratio).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }
}
