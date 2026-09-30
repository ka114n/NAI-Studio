package com.kallan.naistudio.models

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * **一块的边长**（px ✓）—— 拓展按它长大 ✓。
 *
 * ⚠️ 必须与**潜空间 8 px 网格**成整数倍 ✓（512 = 8 × 64 ✓）：聚焦生成的请求尺寸要按 8px 对齐
 * （见 `MaskCodec.inpaintMask` ✓），块边长不整除会出现"框贴回去差半格"✗（本仓库被"错位"坑过，
 * 见 `docs/25` ✓）。
 */
const val INFINITE_TILE = 512

/**
 * 文档尺寸上限（**桌面 4096 / 手机 3072** ✓ —— 用户 2026-09-22 拍板 ✓）。
 *
 * 为什么要有它：内存峰值 = 上限面积 × 2 张全尺寸表面（画布本身 + 编辑会话那张 ✓）——
 *  · 4096² 的 ARGB = **67 MB** ⇒ 峰值 ~134 MB ✓（桌面没问题 ✓，手机偏重 ✗）；
 *  · 3072² = **37 MB** ⇒ 峰值 ~74 MB ✓（手机也稳 ✓）。
 * ⇒ 到上限就**如实提示、拒绝再长** ✗ 不静默截断 ✓（静默截断会让用户以为"拓展没反应"✗）。
 */
const val INFINITE_MAX_SIDE_DESKTOP = 4096
const val INFINITE_MAX_SIDE_PHONE = 3072

/**
 * **框的总像素上限** ✓（用户 2026-09-29：「框大小可自定义，最大不超 1024×1024，不是单边的 1024，
 * 而是框的像素小于 1024×1024」）—— 与遮罩重绘「聚焦」同一口径 ✓：宽 / 高**可以**超过 1024 ✓，
 * 只有 `宽 × 高` 不能超 ✓。
 */
const val INFINITE_FRAME_MAX_AREA = 1024 * 1024

/**
 * 把框**等比**收进 [INFINITE_FRAME_MAX_AREA] ✓（没超就原样返回 ✓）。
 * [keepRight] / [keepBottom] = 钉住右 / 下边（从那一侧拖的时候 ✓），否则钉住左 / 上边。
 */
fun capInfiniteFrameArea(
    frame: EditRect,
    keepRight: Boolean = false,
    keepBottom: Boolean = false,
): EditRect {
    val area = frame.w.toLong() * frame.h
    if (area <= INFINITE_FRAME_MAX_AREA) return frame
    val k = sqrt(INFINITE_FRAME_MAX_AREA.toDouble() / area)
    var w = floor(frame.w * k).toInt().coerceAtLeast(1)
    var h = floor(frame.h * k).toInt().coerceAtLeast(1)
    while (w.toLong() * h > INFINITE_FRAME_MAX_AREA) {
        if (w >= h) w-- else h--
    }
    val x = if (keepRight) frame.x + frame.w - w else frame.x
    val y = if (keepBottom) frame.y + frame.h - h else frame.y
    return EditRect(x, y, w, h)
}

/**
 * 拓展方向：**四边中点各一个 `+` 把手** ✓（用户 2026-09-22 选的口径 ✓，`docs/69` §五 Q1 ✓）。
 * 四角**不做** ✗（斜着长在手机上难按，而且用户没点 ✓）。
 */
enum class InfiniteSide { LEFT, RIGHT, TOP, BOTTOM }

/**
 * **无限画布的文档几何**（纯逻辑 ✓ 两端共用 ✓ 可单测 ✓，见 `docs/69` ✓）。
 *
 * ## 三条硬口径
 *
 *  1. `width/height` = **画布像素**尺寸；`originX/originY` = **文档坐标 → 画布像素**的平移 ✓
 *     （`pixel = doc − origin` ✓）；
 *  2. 拓展只做两件事：**改 origin**（往左 / 上长 ✓）+ **长大** ✓ ——
 *     **已有像素一个都不搬家** ✓（不重采样 ⇒ 画质一个字不降 ✓，这正是用户那句
 *     「**不要降低画质缩减倍率**」的地基 ✓）；
 *  3. 超过 [maxSide] ⇒ [extended] **返回 null** ✓（调用方如实提示"到上限了"✓），
 *     而 [headroom] 给界面用来把到顶的那个把手**变灰** ✓（别让用户点了没反应 ✗）。
 *
 * ## 坐标怎么用
 *
 *  · 界面手势给的是**画布像素** ⇒ 要存进文档 / 喂给 inpaint 时用 [docX] / [docY] 换成**文档坐标** ✓；
 *  · 反过来摆内容（框、笔迹、粘贴回来的结果 ✓）用 [pixelX] / [pixelY] ✓。
 *  ⚠️ 这一对换算是**唯一**一处 ✓：别的文件不许自己写 `± origin` ✗
 *  （两处各写一遍，迟早有一处忘了改 ✓ —— 这就是"图像和框对不上"那一族的来源 ✓）。
 */
data class InfiniteCanvas(
    val width: Int,
    val height: Int,
    val originX: Int = 0,
    val originY: Int = 0,
    val maxSide: Int = INFINITE_MAX_SIDE_DESKTOP,
) {
    /** 文档坐标 → 画布像素 ✓ */
    fun pixelX(docX: Float): Float = docX - originX
    fun pixelY(docY: Float): Float = docY - originY

    /** 画布像素 → 文档坐标 ✓ */
    fun docX(pixelX: Float): Float = pixelX + originX
    fun docY(pixelY: Float): Float = pixelY + originY

    /** 文档矩形（`x,y,w,h`）→ 画布像素矩形 ✓（拓展之后"内容仍在原处"就靠它 ✓） */
    fun pixelRect(x: Float, y: Float, w: Float, h: Float): FloatArray =
        floatArrayOf(pixelX(x), pixelY(y), w, h)

    /** 往 [side] 长 [tiles] 块 ✓；到上限 / 参数不合法 ⇒ **null** ✓（调用方如实提示 ✓） */
    fun extended(side: InfiniteSide, tiles: Int = 1): InfiniteCanvas? {
        if (tiles <= 0) return null
        val grow = INFINITE_TILE * tiles
        return when (side) {
            InfiniteSide.LEFT ->
                if (width + grow > maxSide) null else copy(width = width + grow, originX = originX - grow)

            InfiniteSide.RIGHT ->
                if (width + grow > maxSide) null else copy(width = width + grow)

            InfiniteSide.TOP ->
                if (height + grow > maxSide) null else copy(height = height + grow, originY = originY - grow)

            InfiniteSide.BOTTOM ->
                if (height + grow > maxSide) null else copy(height = height + grow)
        }
    }

    /**
     * **把文档扩到装得下这个矩形**（用户 2026-09-22 的新口径，取代了"四边 `+` 把手" ✓）：
     *
     * > 「**拓展画布交给用户，给一个可以调大小的框，用户想要拓展就将框移到画像外想要拓展的部分，
     * > 然后可能会生成一张凸出一块的图，未绘制的地方就是透明通道**」
     *
     * 落地口径：
     *  · 文档**只长成外接矩形** ✓（不做异形画布 ✗）—— "凸出一块"的样子靠
     *    **没画到的地方留透明** ✓ 自然形成 ✓（[InfiniteCanvasPixels.blit] 的新区域就是透明 ✓）；
     *  · 只长**需要的那些**（取并集 ✓）：框在文档内 ⇒ **返回自己** ✓（调用方据此跳过搬运 ✓）；
     *  · 超 [maxSide] ⇒ **null** ✓（调用方如实提示 ✓，绝不静默夹小 ✗ —— 夹小等于偷偷改用户的框 ✗）。
     *
     * @param x/y/w/h 框的**文档坐标**矩形 ✓（可以整个在文档外 ✓）
     */
    fun expandedToInclude(x: Float, y: Float, w: Float, h: Float): InfiniteCanvas? {
        if (w <= 0f || h <= 0f) return null
        val left = floor(minOf(x, originX.toFloat())).toInt()
        val top = floor(minOf(y, originY.toFloat())).toInt()
        val right = ceil(maxOf(x + w, (originX + width).toFloat())).toInt()
        val bottom = ceil(maxOf(y + h, (originY + height).toFloat())).toInt()
        if (left == originX && top == originY &&
            right == originX + width && bottom == originY + height
        ) {
            return this
        }
        val newWidth = right - left
        val newHeight = bottom - top
        if (newWidth > maxSide || newHeight > maxSide) return null
        return copy(width = newWidth, height = newHeight, originX = left, originY = top)
    }

    /** 这一边**还能长几块** ✓（0 = 到上限 ✓ —— 界面据此把那个 `+` 变灰 ✓） */
    fun headroom(side: InfiniteSide): Int {
        val room = when (side) {
            InfiniteSide.LEFT, InfiniteSide.RIGHT -> maxSide - width
            InfiniteSide.TOP, InfiniteSide.BOTTOM -> maxSide - height
        }
        return (room / INFINITE_TILE).coerceAtLeast(0)
    }

    /**
     * **把框夹进"平台还装得下"的范围** ✓ —— 用户 2026-09-22：「**无限画布模式拖动框没限制**」✓。
     *
     * 口径就是 [expandedToInclude] 那一条 ✓：**框 ∪ 画布**的外接矩形不许超过 [maxSide] ✓
     * （放不下 ⇒ 那一次生成必然失败 ✓）。既然"拖到装不下、点了生成才报上限"是事后诸葛亮 ✗，
     * 就**在拖动这一层夹住** ✓ —— 手感上跟"拖到头就停住"一样 ✓，不需要弹窗 ✗。
     *
     * 推导（一维，x 轴；y 轴同理 ✓）：
     *  · 外接左 = `min(x, originX)` ✓、外接右 = `max(x + w, originX + width)` ✓；
     *  · 要 `右 − 左 ≤ maxSide` ✓ ⇒
     *     - 框往**左**跑（`x < originX`）时"左"就是 `x` ⇒ `x ≥ originX + width − maxSide` ✓；
     *     - 框往**右**跑时"左"就是 `originX` ⇒ `x + w ≤ originX + maxSide` ✓。
     *
     * ⚠️ 框本身的宽 / 高也各夹一次 ✓（比上限还大的框，怎么摆都放不下 ✓）。
     * ⚠️ 夹取**只动位置、不动尺寸** ✗（除了上面那条本身超宽的兜底 ✓）——
     * 拖到边缘时"框被悄悄缩小"比"停住"更让人费解 ✓。
     */
    fun clampFrame(frame: EditRect): EditRect {
        val w = frame.w.coerceIn(1, maxSide)
        val h = frame.h.coerceIn(1, maxSide)
        // 允许的最左 / 最上（再过去，外接矩形就撑过上限了 ✓）
        val minX = originX + width - maxSide
        val minY = originY + height - maxSide
        val maxX = originX + maxSide - w
        val maxY = originY + maxSide - h
        // ⚠️ `coerceIn` 的上下界反了会**抛** ✗ —— 框比画布还宽时 hi 会小于 lo ✓，这里先取 max ✓
        val x = frame.x.coerceIn(minX, maxOf(minX, maxX))
        val y = frame.y.coerceIn(minY, maxOf(minY, maxY))
        return EditRect(x, y, w, h)
    }

    /**
     * **拓展后，新的画布像素里，老内容要贴在哪儿** ✓（左上角在画布像素里的偏移 ✓）。
     *
     * 往左 / 上长才会非零 ✓；往右 / 下长是 `(0, 0)` ✓。
     * 调用方按它把老位图**整块拷进新位图** ✓ —— 不缩放、不重采样 ✓（画质不动 ✓）。
     */
    fun pasteOffsetFor(extendedTo: InfiniteCanvas): Pair<Int, Int> =
        (originX - extendedTo.originX) to (originY - extendedTo.originY)

    companion object {
        /** 建一张初始画布 ✓（尺寸夹进上限里 ✓ —— 出图尺寸本来就 ≤ 上限 ✓） */
        fun create(
            width: Int,
            height: Int,
            maxSide: Int = INFINITE_MAX_SIDE_DESKTOP,
        ): InfiniteCanvas = InfiniteCanvas(
            width = width.coerceIn(1, maxSide),
            height = height.coerceIn(1, maxSide),
            originX = 0,
            originY = 0,
            maxSide = maxSide,
        )
    }
}
