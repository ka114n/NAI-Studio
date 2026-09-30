package com.kallan.naistudio.models

/**
 * **拓展画布时，把老像素整块搬进新位图**（纯逻辑 ✓ 可单测 ✓，见 `docs/69` ✓）。
 *
 * ## 为什么单独拎出来
 *
 * "拓展之后内容还在原处"这句话，落地就是这一次 `arraycopy` ✓ —— 而它**最容易写错**：
 * 行首偏移少算一次、`dx` 忘了加、新位图没清底…… 每一样都会让画面**整体错位一格** ✗，
 * 而错位在缩放之后**很难一眼看出来**（本仓库被"错位"坑过好几次，见 `docs/25` ✓）。
 * 所以：**搬运只有这一处** ✓，别处不许自己写 `± origin` 或自己 arraycopy ✗。
 *
 * ⚠️ 这里**只做平移**：不缩放、不重采样 ✓ ⇒ 画质一个字不降 ✓
 * （用户 2026-09-22：「**不要降低画质缩减倍率**」✓）。
 */
object InfiniteCanvasPixels {

    /**
     * 把 [src]（`srcW × srcH` 的 ARGB ✓）整块放进一张 `dstW × dstH` 的新位图里，
     * 位置 = 老画布左上角在新画布里的坐标 `(dx, dy)` ✓（由 [InfiniteCanvas.pasteOffsetFor] 给 ✓）。
     *
     * @param fill 新长出来那块的底色 ✓（默认 `0` = 全透明 ✓ —— 无限画布新长的是**空白** ✓）
     * @throws IllegalArgumentException 尺寸对不上 / 放不下（**宁可抛，也不静默裁掉内容** ✗）
     */
    fun blit(
        src: IntArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        dx: Int,
        dy: Int,
        fill: Int = 0,
    ): IntArray {
        require(srcW > 0 && srcH > 0) { "老画布尺寸非法：${srcW}x$srcH" }
        require(src.size == srcW * srcH) { "像素数对不上：${src.size} != ${srcW * srcH}" }
        require(dstW >= srcW && dstH >= srcH) { "新画布比老画布小：${dstW}x$dstH < ${srcW}x$srcH" }
        require(dx >= 0 && dy >= 0 && dx + srcW <= dstW && dy + srcH <= dstH) {
            "放不下：老画布 ${srcW}x$srcH 在新画布 ${dstW}x$dstH 的 ($dx,$dy) 处"
        }
        val out = IntArray(dstW * dstH)
        if (fill != 0) out.fill(fill)
        for (row in 0 until srcH) {
            System.arraycopy(src, row * srcW, out, (dy + row) * dstW + dx, srcW)
        }
        return out
    }
}
