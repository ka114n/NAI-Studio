package com.kallan.naistudio.services

import com.kallan.naistudio.platform.Platform
import java.io.File

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
 *
 * ## 从 Android 版搬过来时改了什么
 *
 * 原来这里直接用 `BitmapFactory` + `Context.filesDir`，所以只能在手机上跑。
 * 现在换成平台层（[Platform.images] 解码/编码、[Platform.paths] 定位目录），
 * **两端共用同一份逻辑**，缩略图策略不会各自漂移。
 *
 * 唯一的取舍：`ImageIo` 只有「按字节解码」，没有 Android 那个「先读文件头量尺寸」
 * （`inJustDecodeBounds`）。所以这里先把原文件读进内存再交给 `decodeSampled` ——
 * 多读一遍几 MB 的文件（顺序 I/O，很便宜），但**贵的 PNG 解码仍然是降采样的**，
 * 而缩略图只在第一次显示某张图时才生成。
 */
object Thumbnails {
    /** 超过这个体积就按最久未使用裁剪。 */
    private const val MAX_CACHE_BYTES = 200L * 1024 * 1024

    /**
     * 缓存目录带版本号：改过缩略图生成算法（比如补上滤波缩放）之后直接把版本号 +1，
     * 旧文件自然失效，不会出现「代码改了但界面没变」的怪现象。
     *
     * 版本历史：
     *  · `thumbnails_v2` —— 一步双线性降采样（2000px 直接缩到 320px，采样会漏像素 → **锯齿/糊** ✗）
     *  · `thumbnails_v3` —— 2026-09-20：改成**逐步折半**降采样（每步 2:1）+ 渲染端 `FilterQuality.High`
     *    （用户报「图库的缩略图狗牙多，模糊」；v2 那批旧图靠升版本号一次性作废 ✓）
     */
    private const val DIR_NAME = "thumbnails_v3"

    private fun dir(platform: Platform): File =
        File(platform.paths.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /**
     * 返回可直接解码的**小图路径**；失败时返回 null（调用方回落到原图）。
     */
    fun fileFor(platform: Platform, sourcePath: String, maxDimension: Int): String? {
        val source = File(sourcePath)
        if (!source.isFile) return null

        val stamp = "${source.length()}_${source.lastModified()}"
        val key = "${sourcePath.hashCode().toUInt().toString(16)}_${stamp}_$maxDimension"
        val existing = dir(platform).listFiles { f -> f.name.startsWith(key) }?.firstOrNull()
        if (existing != null && existing.length() > 0) {
            // 命中就更新访问时间 —— `prune` 是按「最久未使用」裁的
            existing.setLastModified(System.currentTimeMillis())
            return existing.absolutePath
        }

        val bytes = try {
            source.readBytes()
        } catch (e: Exception) {
            return null
        }
        val image = platform.images.decodeSampled(bytes, maxDimension) ?: return null
        return try {
            // 有透明通道的（透明背景图）必须存 PNG，否则会变黑底
            val png = platform.images.hasAlpha(image)
            val encoded = if (png) {
                platform.images.pngBytes(image)
            } else {
                platform.images.jpegBytes(image, 88)
            }
            val target = File(dir(platform), if (png) "$key.png" else "$key.jpg")
            target.writeBytes(encoded)
            target.absolutePath
        } catch (e: Exception) {
            null
        } finally {
            image.recycle()
        }
    }

    /** 启动时调用一次：按最久未使用裁到上限以内。 */
    fun prune(platform: Platform) {
        val files = dir(platform).listFiles()?.filter { it.isFile } ?: return
        val total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return

        var remaining = total
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (remaining <= MAX_CACHE_BYTES) return@forEach
            val size = file.length()
            if (file.delete()) remaining -= size
        }
    }
}
