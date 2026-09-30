package com.kallan.naistudio.services

import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.platform.Platform

/**
 * **拼页合成**（高级漫画第 ⑦ 项「拼页导出」的像素部分）—— 用户 2026-09-26 的方案 §4 M4 ✓。
 *
 * 做法：开一张页大小的 ARGB 画布 → **按图层从下到上**把每层的图像（等比缩放后）混上去 ✓。
 * 位置由调用方用 [ComicLayout.fit] / [ComicLayout.cover] 算好传进来 ✓
 *（屏幕预览与导出用**同一套几何** ✓ —— 否则"看着对齐、导出偏了"✗）。
 *
 * ⚠️ **只处理位图图层**（底板 / 生成层 / **绘画层** ✓）；气泡与文本是矢量 ✗ ——
 * 它们的栅格化要交给电脑端的 `ImageComposeScene`（在导出那批做 ✓，这里先留口子 ✓）。
 *
 * 混色部分 [blendOver] 是**纯函数** ✓（不碰平台 API ✓）→ 有单测 ✓；[compose] 只是把
 * 解码 / 缩放 / 编码串起来 ✓。
 */
object ComicComposer {

    /**
     * 把源像素按 [opacity] 叠到目标画布上（**source-over** ✓，标准 alpha 合成 ✓）。
     *
     * @param dst     目标画布（ARGB ✓，原地修改 ✓）
     * @param dstW/dstH 画布尺寸
     * @param src     源像素（ARGB ✓）
     * @param srcW/srcH 源尺寸
     * @param x/y     左上角落点（可以越界 ✓ —— 越界部分自动裁掉 ✓）
     * @param opacity 0~1 ✓（1 = 原样 ✓）
     */
    fun blendOver(
        dst: IntArray,
        dstW: Int,
        dstH: Int,
        src: IntArray,
        srcW: Int,
        srcH: Int,
        x: Int,
        y: Int,
        opacity: Float = 1f,
    ) {
        val alphaScale = opacity.coerceIn(0f, 1f)
        if (alphaScale <= 0f) return
        for (sy in 0 until srcH) {
            val dy = y + sy
            if (dy < 0 || dy >= dstH) continue
            val srcRow = sy * srcW
            val dstRow = dy * dstW
            for (sx in 0 until srcW) {
                val dx = x + sx
                if (dx < 0 || dx >= dstW) continue
                val s = src[srcRow + sx]
                val sa = ((s ushr 24) and 0xFF) * alphaScale
                if (sa <= 0f) continue
                if (sa >= 255f) {
                    dst[dstRow + dx] = s
                    continue
                }
                val d = dst[dstRow + dx]
                val a = sa / 255f
                val inv = 1f - a
                val r = ((s ushr 16) and 0xFF) * a + ((d ushr 16) and 0xFF) * inv
                val g = ((s ushr 8) and 0xFF) * a + ((d ushr 8) and 0xFF) * inv
                val b = (s and 0xFF) * a + (d and 0xFF) * inv
                val outA = (sa + ((d ushr 24) and 0xFF) * inv).coerceIn(0f, 255f)
                dst[dstRow + dx] = (outA.toInt() shl 24) or
                    (r.toInt().coerceIn(0, 255) shl 16) or
                    (g.toInt().coerceIn(0, 255) shl 8) or
                    b.toInt().coerceIn(0, 255)
            }
        }
    }

    /** 一张要合成的位图图层：图像路径 + 它在页上的目标矩形（像素 ✓，整数 ✓）。 */
    data class BitmapPlacement(
        val imagePath: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val opacity: Float = 1f,
    )

    /**
     * 合成整页 → PNG 字节 ✓；没有任何图层 / 尺寸非法 / 编码失败 → `null` ✓（调用方提示一下就行 ✓）。
     *
     * @param background 纸底色（ARGB ✓，默认不透明白 ✓ —— 漫画页一般要有纸色 ✓）
     * @param placements **从下到上**排好序 ✓（调用方按 `ComicLayerStack.all` 的顺序给 ✓）
     */
    fun compose(
        platform: Platform,
        pageWidth: Int,
        pageHeight: Int,
        placements: List<BitmapPlacement>,
        background: Int = 0xFFFFFFFF.toInt(),
    ): ByteArray? {
        if (pageWidth <= 0 || pageHeight <= 0) return null
        if (placements.isEmpty()) return null
        val canvas = IntArray(pageWidth * pageHeight) { background }
        var drewSomething = false
        for (p in placements) {
            if (p.width <= 0 || p.height <= 0) continue
            val image = platform.images.decodeFull(p.imagePath) ?: continue
            // 等比无所谓：调用方给的矩形已经是"算好的目标框"✓ —— 这里就按它缩放 ✓（smooth ✓）
            val scaled = platform.images.scale(image, p.width, p.height, smooth = true)
            val pixels = platform.images.pixelsOf(scaled)
            if (pixels.size < p.width * p.height) continue
            blendOver(canvas, pageWidth, pageHeight, pixels, p.width, p.height, p.x, p.y, p.opacity)
            drewSomething = true
        }
        if (!drewSomething) return null
        val out = platform.images.fromArgb(canvas, pageWidth, pageHeight)
        return platform.images.pngBytes(out)
    }

    /** 只挑"位图类"的可见图层（底板 / 生成层 / **绘画层** ✓）；气泡文本由矢量那边画 ✓。 */
    fun bitmapLayers(layers: List<ComicLayer>): List<ComicLayer> =
        layers.filter { it.visible && it.opacity > 0f && it.imagePath != null }
}
