package com.kallan.naistudio.models

/**
 * **活笔画缓冲**（第 ㉔ 批 · 用户 2026-09-24：「**不跟手** · **卡顿**」✓）。
 *
 * ## 它解决的问题（一句话）
 *
 * 以前每落**一颗笔尖**就往层像素上盖 ✗ ⇒ 界面每颗笔尖都要把**整张页**（A4 = 2480×3508
 * ≈ 35 MB）重新交给 `composeImageOf` 建一遍位图 ✗✗ —— 一笔几十上百颗笔尖就是几十上百次全图重建
 * ⇒ 必然"不跟手 + 卡顿" ✓。
 *
 * 现在**这一笔的墨迹先住在它里面** ✓：
 *  · 层像素（[ImageEditSession.pixels]）在一笔结束之前**一个字节都不动** ✓ ⇒ 层位图**不用重建** ✓；
 *  · 界面只画"**这一笔自上次刷新以来碰到的那一小块**"（[takePatch] ✓）⇒ 每次重建的位图**只有那一小块** ✓；
 *  · 抬笔时把这一笔按脏矩形**一次**合进层像素 ✓（`ImageEditSession.commitLiveStroke` ✓），
 *    层位图在那时才重建**一次** ✓。
 *
 * ## 怎么组织（**按 256 对齐的方框，只随笔画长大** ✓）
 *
 *  · 缓冲只覆盖"**这一笔已经碰到过的包围盒**"✓，初始为空；
 *  · 每颗笔尖落下来之前先 [ensure] 一下 → 需要就把方框**按 256 像素的格子往外扩**
 *    （重新分配 + 把老内容搬过去 ✓）—— 一笔之内扩容次数是个位数 ✓，不是每颗笔尖一次 ✓；
 *  · 起点按 256 **对齐**，而 256 是纸纹贴图边长 [BrushEngine.TILE]（128 ✓）的整数倍 ✓ ⇒
 *    缓冲里的**局部坐标 `% 128` 与页面坐标 `% 128` 完全一致** ✓ ——
 *    纸纹 / 噪点贴图"钉在画布上"这条口径**一个字都没变** ✓（见 [BrushEngine.textureAt] ✓）。
 *
 * ## 三种模式怎么合成（**都不动引擎的数值口径** ✓）
 *
 *  · **画笔**：缓冲里存的是**墨迹本身**（source-over 累加 ✓）——抬笔时 `overPixel(层, 墨, 1)`
 *    逐笔尖盖与"最后一次性合成"**等价** ✓（source-over 可结合 ✓：
 *    `(a over b) over c = a over (b over c)` ✓）；
 *  · **橡皮**：缓冲里存的是**累计擦除覆盖度**（白 + alpha ✓，累加公式
 *    `c' = c + c_i(1 − c)` ✓）——抬笔时一次性 `erasePixel(层, c)`，与"每颗笔尖各擦一次"
 *    **等价** ✓（因为 `1 − c' = (1 − c)(1 − c_i)` ✓）；显示那一半用 `DstOut` 叠（同一个公式 ✓）；
 *  · **keepOpacity**：缓冲里每颗笔尖 alpha = 1（引擎那边本来就这么给 ✓），
 *    抬笔时按**整笔浓度**一次性合成 ✓（见 [BrushEngine.compositeLayer] ✓，口径一个字没变 ✓）。
 *
 * @param pageWidth 页宽（像素 ✓）—— 缓冲永远不越出页 ✓
 * @param pageHeight 页高（像素 ✓）
 */
class LiveStrokeLayer(
    private val pageWidth: Int,
    private val pageHeight: Int,
) {

    /** 缓冲左上角在**页面坐标**里的位置（永远是 [CHUNK] 的整数倍 ✓）。 */
    var originX: Int = 0
        private set

    var originY: Int = 0
        private set

    /** 缓冲尺寸（0 = 还没有任何墨迹 ✓）。 */
    var width: Int = 0
        private set

    var height: Int = 0
        private set

    /** 缓冲像素（ARGB ✓ —— 画笔 = 墨迹；橡皮 = 累计覆盖度（白 + alpha）✓）。 */
    var pixels: IntArray = IntArray(0)
        private set

    /** 选区裁剪掩码的**局部副本**（`null` = 没有选区 ✓）—— `stampDab` 要按它逐像素裁 ✓。 */
    var clipMask: ByteArray? = null
        private set

    /** 有没有内容（没有任何笔尖落下来时是 false ✓）。 */
    val hasContent: Boolean get() = width > 0 && height > 0

    /** 自上次 [takePatch] 以来碰到的矩形（**页面坐标** ✓）。 */
    private var pending: EditRect? = null

    /**
     * 已经交给界面的那几块（**页面坐标 + 落笔顺序** ✓）。
     *
     * 抬笔合并时**按它们分块**记历史（每块只记那一小块的前后像素 ✓）——
     * 不这么做的话，一笔长线会记一整块包围盒（A4 上对角一笔 ≈ 35 MB × 2 ✗，历史预算当场爆掉 ✗）。
     */
    private val patchRects = ArrayList<EditRect>()

    /**
     * 把缓冲扩到**至少盖住** [rect]（页面坐标 ✓）。已经在里面就什么都不做 ✓。
     *
     * @param selectionClip 选区掩码（长度 = `pageWidth*pageHeight` ✓；`null` = 没有选区 ✓）——
     *   缓冲长大时会**同步一份局部副本** ✓（[ImageEditOps.stampDab] 的 `clip` 是按缓冲下标查的 ✓）。
     * @return 缓冲可用才 true ✓（`rect` 整块在页外 → false ✓）
     */
    fun ensure(rect: EditRect, selectionClip: ByteArray?): Boolean {
        val safe = rect.clampTo(pageWidth, pageHeight)
        if (safe.isEmpty) return false
        if (hasContent &&
            safe.x >= originX && safe.y >= originY &&
            safe.x + safe.w <= originX + width && safe.y + safe.h <= originY + height
        ) {
            return true
        }
        val curLeft = if (hasContent) originX else safe.x
        val curTop = if (hasContent) originY else safe.y
        val curRight = if (hasContent) originX + width else safe.x
        val curBottom = if (hasContent) originY + height else safe.y
        val left = alignDown(minOf(curLeft, safe.x))
        val top = alignDown(minOf(curTop, safe.y))
        val right = minOf(pageWidth, alignUp(maxOf(curRight, safe.x + safe.w)))
        val bottom = minOf(pageHeight, alignUp(maxOf(curBottom, safe.y + safe.h)))
        val newWidth = right - left
        val newHeight = bottom - top
        if (newWidth <= 0 || newHeight <= 0) return false

        val grown = IntArray(newWidth * newHeight)
        if (hasContent) {
            val shiftX = originX - left
            val shiftY = originY - top
            for (row in 0 until height) {
                System.arraycopy(
                    pixels,
                    row * width,
                    grown,
                    (shiftY + row) * newWidth + shiftX,
                    width,
                )
            }
        }
        pixels = grown
        originX = left
        originY = top
        width = newWidth
        height = newHeight
        clipMask = selectionClip?.let { mask -> copyClip(mask) }
        return true
    }

    /** 把一块（页面坐标 ✓）记成"待交给界面" ✓（并进 [pending] ✓）。 */
    fun markPending(global: EditRect) {
        if (global.isEmpty) return
        pending = pending?.union(global) ?: global
    }

    /**
     * 取走"自上次以来碰到的那一块"（**页面坐标 + 拷出来的一份像素** ✓）；没有就是 `null` ✓。
     *
     * ⚠️ 拷贝是**必须的** ✗：这块像素马上要交给 `composeImageOf` 建位图，
     * 而缓冲还会继续长大（重新分配）——直接把缓冲内部的数组交出去会被后面写坏 ✓。
     */
    fun takePatch(): LiveStrokePatch? {
        val rect = pending ?: return null
        pending = null
        val safe = rect.clampTo(pageWidth, pageHeight)
        if (safe.isEmpty) return null
        val local = EditRect(safe.x - originX, safe.y - originY, safe.w, safe.h).clampTo(width, height)
        if (local.isEmpty) return null
        val out = IntArray(local.w * local.h)
        for (row in 0 until local.h) {
            System.arraycopy(
                pixels,
                (local.y + row) * width + local.x,
                out,
                row * local.w,
                local.w,
            )
        }
        val global = EditRect(local.x + originX, local.y + originY, local.w, local.h)
        patchRects.add(global)
        return LiveStrokePatch(global.x, global.y, global.w, global.h, out)
    }

    /**
     * 抬笔用：**块列表**（页面坐标 + 落笔顺序 ✓）—— 还没交给界面的那最后一块也并进来 ✓。
     *
     * 返回之后缓冲内部的 pending 就清空了 ✓（[reset] 会连块列表一起清 ✓）。
     */
    fun consumePatchRects(): List<EditRect> {
        val rest = pending
        pending = null
        if (rest != null && !rest.isEmpty) {
            patchRects.add(rest.clampTo(pageWidth, pageHeight))
        }
        return patchRects.toList()
    }

    /** 一笔结束：整块释放 ✓（抬笔之后缓冲就没有用了 ✓）。 */
    fun reset() {
        pixels = IntArray(0)
        width = 0
        height = 0
        originX = 0
        originY = 0
        clipMask = null
        pending = null
        patchRects.clear()
    }

    /** 选区掩码 → 缓冲局部副本（页面坐标 → 缓冲坐标 ✓，越界一律 0 ✓）。 */
    private fun copyClip(mask: ByteArray): ByteArray {
        val local = ByteArray(width * height)
        for (row in 0 until height) {
            val globalY = originY + row
            if (globalY < 0 || globalY >= pageHeight) continue
            val srcRow = globalY * pageWidth
            val dstRow = row * width
            for (col in 0 until width) {
                val globalX = originX + col
                if (globalX < 0 || globalX >= pageWidth) continue
                val index = srcRow + globalX
                if (index in mask.indices) local[dstRow + col] = mask[index]
            }
        }
        return local
    }

    companion object {

        /**
         * 缓冲扩容的**格子边长**（像素 ✓）—— 256 = 8 个 32 的块、也是 [BrushEngine.TILE]（128）的整数倍 ✓
         * ⇒ 局部坐标与页面坐标**对 128 同余** ✓（纸纹对齐那条硬口径靠它 ✓）。
         */
        const val CHUNK = 256

        /** 向下对齐到 [CHUNK] 的整数倍（负数也正确 ✓）。 */
        internal fun alignDown(value: Int): Int {
            val quotient = value / CHUNK
            return if (value < 0 && quotient * CHUNK != value) {
                (quotient - 1) * CHUNK
            } else {
                quotient * CHUNK
            }
        }

        /** 向上对齐到 [CHUNK] 的整数倍（负数也正确 ✓）。 */
        internal fun alignUp(value: Int): Int = alignDown(value + CHUNK - 1)
    }
}

/**
 * **一块活笔画**（页面坐标 [x],[y] + 尺寸 + 拷出来的 ARGB ✓）—— 界面据此建一张**小位图** ✓。
 *
 * 刻意**不是** `data class` ✗：它带一个 `IntArray`，`equals`/`hashCode` 用引用比较更诚实 ✓
 * （逐像素比大小等于白烧 CPU ✓）。
 */
class LiveStrokePatch(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val pixels: IntArray,
)
