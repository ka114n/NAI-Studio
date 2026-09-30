package com.kallan.naistudio.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * 图片解码工具。
 *
 * **这是相对参考实现的一处有意优化。** 参考实现把预览帧的原始字节直接交给
 * `Image.memory`（不设 `cacheWidth`），由 Flutter 的解码器与图片缓存兜底。
 *
 * 本项目的硬约束是"UI 层不持有 base64 / 原始 PNG 字节"，且预览帧每 ~110ms 就来一张，
 * 所以要：
 *  1. 先 `inJustDecodeBounds` 量尺寸、算出 `inSampleSize`，再真正解码 —— 只解出够看的位图；
 *  2. 调用方负责只在后台线程调用它，并且**同一时刻只保留一张**预览位图。
 *
 * 注意：**不做图像格式判断**。参考实现也不做；已确认服务端中间帧可能是 PNG 也可能是
 * JPEG（桌面端的格式嗅探代码可佐证），`BitmapFactory` 两者都能解。
 */
object ImageDecoder {

    /** 预览位图最长边。预览只为看构图，不需要全分辨率。 */
    const val PREVIEW_MAX_DIMENSION = 1080

    /**
     * 从内存字节降采样解码。**必须在后台线程调用。**
     * 解不出来（数据损坏、非图片）返回 null，由调用方决定是否沿用旧帧。
     */
    fun decodeSampled(bytes: ByteArray, maxDimension: Int = PREVIEW_MAX_DIMENSION): Bitmap? {
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }
}
