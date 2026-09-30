package com.kallan.naistudio.models

import kotlin.math.roundToInt

/**
 * **画布编辑器的一次编辑会话**：一张可变像素缓冲 + 撤销/重做。
 *
 * 刻意**纯 Kotlin**（只有 `IntArray` + [EditHistory]），所以：
 *  · 涂一笔 → 撤销 → 像素必须**逐位回到原样**这种事能写 JVM 单测；
 *  · 手机与电脑共用同一份（读盘/落盘那一步在 `AppState` 里走 `ImageIo`）。
 *
 * ## 一笔的生命期
 *
 * `beginStroke` → `strokeTo` × N → `endStroke`。整笔是历史里的**一步**（见 [EditHistory.beginGroup]）；
 * 中途每段都按脏矩形记一份前后差量，所以撤销是精确的、也不吃内存。
 *
 * ## 四种笔（[StrokeMode]）
 *
 * 画笔 / 橡皮 / 模糊 / 图章 —— 几何一样（都是"沿着点走一段、按笔尖形状盖印"），
 * 差别只在**每个像素算出来的新值**：改色、清 alpha、取模糊值、取源点的像素。
 * 所以这里把"盖印"这一步按模式分派给 [ImageEditOps] 里对应的纯函数。
 *
 * ## 像素坐标
 *
 * 全部是**图片自身的像素坐标**（0..width-1 / 0..height-1），
 * 屏幕坐标 → 图片坐标的换算在界面层（`CanvasEditor`）完成。
 */
class ImageEditSession(
    width: Int,
    height: Int,
    pixels: IntArray,
    private val history: EditHistory = EditHistory(),
) {

    init {
        require(width > 0 && height > 0) { "画布尺寸必须为正：${width}x${height}" }
        require(pixels.size == width * height) {
            "画布尺寸不匹配：${width}x$height 需要 ${width * height} 个像素，实际 ${pixels.size}"
        }
    }

    /** 画布尺寸**可变**：调整画布大小会把整张换掉（见 [resizeCanvas]）。 */
    var width: Int = width
        private set

    var height: Int = height
        private set

    var pixels: IntArray = pixels
        private set

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    /** 有没有正在画的一笔（指针还按着）。 */
    var isDrawing: Boolean = false
        private set

    // -----------------------------------------------------------------------
    // 选区裁剪："有选区时就只在选区内工作"
    // -----------------------------------------------------------------------

    private var clipShape: SelectionShape? = null
    private var clipMask: ByteArray? = null

    /**
     * 告诉会话"现在的选区是哪个"。传 null = 没有选区（不裁）。
     *
     * 只存形状、不做运算：真正的逐像素掩码**等到落笔那一刻才建**（见 [selectionClipMask]），
     * 否则拖框的时候每一帧都要扫一遍整张图（832×1216 就是 100 万次判定）。
     */
    fun setSelectionClip(shape: SelectionShape?) {
        if (clipShape == shape) return
        clipShape = shape
        clipMask = null
    }

    /** 逐像素的裁剪掩码（惰性建、形状没变就复用）。 */
    private fun selectionClipMask(): ByteArray? {
        val shape = clipShape ?: return null
        clipMask?.let { return it }
        val mask = ByteArray(width * height)
        when (shape) {
            // 矩形走快路：只判包围盒，逐像素比对省一个量级
            is SelectionShape.Rect -> {
                val rect = SelectionGeometry.bounds(shape, width, height) ?: return null
                for (y in rect.y until (rect.y + rect.h)) {
                    val row = y * width
                    java.util.Arrays.fill(mask, row + rect.x, row + rect.x + rect.w, 1)
                }
            }

            is SelectionShape.Polygon -> {
                for (y in 0 until height) {
                    val ny = (y + 0.5f) / height
                    val row = y * width
                    for (x in 0 until width) {
                        if (SelectionGeometry.contains(shape, (x + 0.5f) / width, ny)) mask[row + x] = 1
                    }
                }
            }
        }
        clipMask = mask
        return mask
    }

    /**
     * **把选区里的像素清空**（退格 / 删除键）。
     *
     * 只对选区内、且当前不是全透明的像素动手；整件事算**一步**历史（可撤销）。
     * 有浮动块时先落回（否则清的是"已经搬走"的那块坐标）。
     */
    fun clearSelectionRegion(shape: SelectionShape): Boolean {
        endStroke()
        if (floating != null) commitSelection()
        val rect = SelectionGeometry.bounds(shape, width, height) ?: return false
        val before = ImageEditOps.copyRect(pixels, width, height, rect)
        var touched = 0
        for (y in rect.y until (rect.y + rect.h)) {
            val row = y * width
            val ny = (y + 0.5f) / height
            for (x in rect.x until (rect.x + rect.w)) {
                if (!SelectionGeometry.contains(shape, (x + 0.5f) / width, ny)) continue
                if (pixels[row + x] == ImageEditOps.TRANSPARENT) continue
                pixels[row + x] = ImageEditOps.TRANSPARENT
                touched++
            }
        }
        if (touched == 0) return false
        history.beginGroup()
        history.push(
            PixelDelta(
                rect = rect,
                before = before,
                after = ImageEditOps.copyRect(pixels, width, height, rect),
            ),
        )
        history.endGroup()
        return true
    }

    private var strokeRadius = ImageEditOps.DEFAULT_BRUSH_PIXELS / 2f
    private var strokeSpec = StrokeSpec()
    private var lastX = 0f
    private var lastY = 0f

    /**
     * 开始一笔（在图片像素坐标 [x],[y] 处）。
     *
     * 立刻落下第一个点 —— 官方那种"点一下也留一个墨点"的手感，
     * 否则轻点一下什么都不出现，用户会以为工具坏了。
     */
    fun beginStroke(x: Float, y: Float, spec: StrokeSpec) {
        history.beginGroup()
        isDrawing = true
        strokeSpec = spec
        strokeRadius = ImageEditOps.normalizedBrushPixels(spec.brushPixels) / 2f
        lastX = x
        lastY = y
        stamp(x, y, x, y)
    }

    /** 画到某个点（和上一点连成一段）。没有正在画的一笔时是空操作。 */
    fun strokeTo(x: Float, y: Float) {
        if (!isDrawing) return
        stamp(lastX, lastY, x, y)
        lastX = x
        lastY = y
    }

    /** 收笔：把这一笔提交成历史里的一步。 */
    fun endStroke() {
        if (!isDrawing) return
        isDrawing = false
        history.endGroup()
    }

    /** 撤销一步（写回 before）。 */
    fun undo(): Boolean {
        endStroke()
        val deltas = history.undo() ?: return false
        for (delta in deltas) {
            ImageEditOps.writeRect(pixels, width, height, delta.rect, delta.before)
        }
        return true
    }

    /** 重做一步（写回 after）。 */
    fun redo(): Boolean {
        endStroke()
        val deltas = history.redo() ?: return false
        for (delta in deltas) {
            ImageEditOps.writeRect(pixels, width, height, delta.rect, delta.after)
        }
        return true
    }

    /**
     * 清空画布（刷成透明）。
     *
     * ⚠️ 这是**一步可撤销的操作**，不是"重置"：官方那颗垃圾桶就是清内容、还能撤销回去。
     * 想回到"进编辑器时的原图"是另一件事（界面上的「重置」），由 `AppState` 重新读盘。
     */
    fun clear(): Boolean {
        endStroke()
        if (pixels.all { it == ImageEditOps.TRANSPARENT }) return false
        val rect = EditRect(0, 0, width, height)
        val before = ImageEditOps.copyRect(pixels, width, height, rect)
        val changed = ImageEditOps.fillRect(pixels, width, height, rect, ImageEditOps.TRANSPARENT)
        if (changed.isEmpty) return false
        history.beginGroup()
        history.push(
            PixelDelta(
                rect = changed,
                before = before,
                after = ImageEditOps.copyRect(pixels, width, height, changed),
            ),
        )
        history.endGroup()
        return true
    }

    /** 某个像素的颜色（吸管工具）。 */
    fun colorAt(x: Int, y: Int): Int = ImageEditOps.colorAt(pixels, width, height, x, y)

    // -----------------------------------------------------------------------
    // 油漆桶（一步，可撤销）
    // -----------------------------------------------------------------------

    /** 油漆桶灌一次；没改动返回 false。 */
    fun fillAt(x: Int, y: Int, color: Int, tolerance: Int): Boolean {
        endStroke()
        val rect = EditRect(0, 0, width, height)
        val before = ImageEditOps.copyRect(pixels, width, height, rect)
        val changed = ImageEditOps.floodFill(
            pixels, width, height, x, y, color, tolerance, clip = selectionClipMask(),
        ) ?: return false
        history.beginGroup()
        history.push(
            PixelDelta(
                rect = changed,
                before = ImageEditOps.copyRect(before, width, height, changed),
                after = ImageEditOps.copyRect(pixels, width, height, changed),
            ),
        )
        history.endGroup()
        return true
    }

    // -----------------------------------------------------------------------
    // HSV 调整：拖动时实时预览、Apply 才算一步
    // -----------------------------------------------------------------------

    private var hsvSource: IntArray? = null

    /**
     * 实时预览：每次都从**打开对话框那一刻的快照**重算（不是在上一次结果上再调）。
     *
     * 为什么不就地改：滑杆来回拖时，就地改会让"色相 +10 再 -10"回不到原样（浮点累积），
     * 而且"取消"就没法还原了。留一份快照最省事也最准。
     */
    fun previewHsv(hueShift: Float, saturation: Float, value: Float): Boolean {
        endStroke()
        val source = hsvSource ?: pixels.copyOf().also { hsvSource = it }
        ImageEditOps.hsvAdjust(source, pixels, pixels.size, hueShift, saturation, value)
        return true
    }

    /** 把当前所见提交成**一步**（可撤销）；预览期间没算作历史。 */
    fun commitHsv() {
        endStroke()
        val source = hsvSource ?: return
        hsvSource = null
        if (source.contentEquals(pixels)) return
        val rect = EditRect(0, 0, width, height)
        history.beginGroup()
        history.push(PixelDelta(rect = rect, before = source, after = pixels.copyOf()))
        history.endGroup()
    }

    /** 取消预览：把像素还原成打开对话框那一刻的样子。 */
    fun cancelHsv() {
        val source = hsvSource ?: return
        hsvSource = null
        if (source.size == pixels.size) System.arraycopy(source, 0, pixels, 0, pixels.size)
    }

    /** 有没有正在预览的 HSV 调整（对话框开着的时候是 true）。 */
    val hsvPreviewActive: Boolean get() = hsvSource != null

    // -----------------------------------------------------------------------
    // 调整画布大小
    // -----------------------------------------------------------------------

    /**
     * 换画布尺寸：内容按 ([left],[top]) 放到新画布上。
     *
     * ⚠️ **历史一并清掉**：像素坐标全变了，旧的脏矩形在新画布上没有意义
     * （留着撤销会把画面啃掉一块）。所以这一步本身也不可撤销 —— 和官方一样，
     * 尺寸是"画布级"的操作。想反悔就点顶栏的「重置」（回到进编辑器时那张图）。
     */
    fun resizeCanvas(newWidth: Int, newHeight: Int, left: Int, top: Int) {
        require(newWidth > 0 && newHeight > 0) { "新画布尺寸必须为正：${newWidth}x$newHeight" }
        endStroke()
        hsvSource = null
        dropFloating(restore = false)
        pixels = ImageEditOps.resizeCanvas(pixels, width, height, newWidth, newHeight, left, top)
        width = newWidth
        height = newHeight
        history.clear()
    }

    /** 换一张底图（「重置」会用）：像素整体替换，历史一并清掉。 */
    fun replaceAll(newPixels: IntArray) {
        require(newPixels.size == pixels.size) { "替换的像素尺寸不一致" }
        endStroke()
        hsvSource = null
        dropFloating(restore = false)
        System.arraycopy(newPixels, 0, pixels, 0, pixels.size)
        history.clear()
    }

    // -----------------------------------------------------------------------
    // 浮动选区（三期）：抬起 → 移动/缩放/旋转 → 落回
    // -----------------------------------------------------------------------

    private var floating: FloatingSelection? = null

    /** 抬起那一刻的**整张快照**（取消要还原、提交要当历史的 before）。 */
    private var floatingBefore: IntArray? = null

    val floatingSelection: FloatingSelection? get() = floating

    val hasFloatingSelection: Boolean get() = floating != null

    /**
     * 把选区里的像素**抬起来**（原处清空），之后就能随手拖 / 缩 / 转。
     *
     * 已经有浮动选区时：动过的先落回（提交），没动过的直接丢掉 —— 免得用户以为
     * "换了个框"，结果上一块还飘着。
     */
    fun beginSelection(shape: SelectionShape): Boolean {
        endStroke()
        hsvSource = null
        val existing = floating
        if (existing != null) {
            if (existing.isIdentity()) cancelSelection() else commitSelection()
        }
        val snapshot = pixels.copyOf()
        val lifted = SelectionOps.lift(pixels, width, height, shape) ?: return false
        floating = lifted
        floatingBefore = snapshot
        return true
    }

    fun translateSelection(deltaX: Float, deltaY: Float) {
        floating?.translateBy(deltaX, deltaY)
    }

    fun scaleSelection(factorX: Float, factorY: Float, anchorX: Float, anchorY: Float) {
        floating?.scaleBy(factorX, factorY, anchorX, anchorY)
    }

    fun rotateSelection(deltaDegrees: Float) {
        floating?.rotateBy(deltaDegrees)
    }

    /** **落回**：把变换后的 patch 盖回画布，整次操作算**一步**历史（可撤销）。 */
    fun commitSelection(): Boolean {
        val current = floating ?: return false
        val before = floatingBefore
        floating = null
        floatingBefore = null
        if (before == null) return false
        val touched = SelectionOps.stamp(pixels, width, height, current)
        if (touched == null) {
            // 没有落得下的像素（比如整块被拖到画布外）：像素回到抬起前，别留半截状态
            System.arraycopy(before, 0, pixels, 0, pixels.size)
            return false
        }
        val rect = EditRect(0, 0, width, height)
        history.beginGroup()
        history.push(PixelDelta(rect = rect, before = before, after = pixels.copyOf()))
        history.endGroup()
        return true
    }

    /** 「取消」：像素回到抬起前那一刻（选区形状由界面自己决定留不留）。 */
    fun cancelSelection(): Boolean {
        val before = floatingBefore ?: return false
        floating = null
        floatingBefore = null
        if (before.size == pixels.size) System.arraycopy(before, 0, pixels, 0, pixels.size)
        return true
    }

    private fun dropFloating(restore: Boolean) {
        val before = floatingBefore
        floating = null
        floatingBefore = null
        if (restore && before != null && before.size == pixels.size) {
            System.arraycopy(before, 0, pixels, 0, pixels.size)
        }
    }

    private fun stamp(x0: Float, y0: Float, x1: Float, y1: Float) {
        // ⚠️ 脏矩形在这里算一次、前后快照都按**这同一个矩形**取：
        // 如果让各个 ops 自己再算一遍，两边的 floor/ceil 只要差一格，
        // 存下来的 before 就和实际被改的区域错位 —— 撤销时会把没改过的像素写回去（画面被啃掉一块）。
        val rect = EditRect.ofSegment(x0, y0, x1, y1, strokeRadius, width, height) ?: return
        val spec = strokeSpec
        val before = ImageEditOps.copyRect(pixels, width, height, rect)
        val clip = selectionClipMask()
        val touched = when (spec.mode) {
            StrokeMode.PAINT, StrokeMode.ERASE -> ImageEditOps.stampSegment(
                pixels = pixels,
                width = width,
                height = height,
                x0 = x0,
                y0 = y0,
                x1 = x1,
                y1 = y1,
                radius = strokeRadius,
                shape = spec.shape,
                color = spec.color,
                erasing = spec.mode == StrokeMode.ERASE,
                clip = clip,
            )

            StrokeMode.BLUR -> ImageEditOps.blurStroke(
                pixels = pixels,
                width = width,
                height = height,
                x0 = x0,
                y0 = y0,
                x1 = x1,
                y1 = y1,
                radiusPixels = strokeRadius,
                shape = spec.shape,
                // 模糊半径跟着笔刷走（约 1/3 笔径）：笔大 = 糊得开，这是最直观的对应关系
                blurRadiusPixels = (strokeRadius / 3f).roundToInt().coerceAtLeast(1),
                intensity = spec.blurIntensity,
                clip = clip,
            )

            StrokeMode.CLONE -> ImageEditOps.cloneStroke(
                pixels = pixels,
                width = width,
                height = height,
                x0 = x0,
                y0 = y0,
                x1 = x1,
                y1 = y1,
                radiusPixels = strokeRadius,
                shape = spec.shape,
                offsetX = spec.cloneOffsetX,
                offsetY = spec.cloneOffsetY,
                clip = clip,
            )
        } ?: return
        val after = ImageEditOps.copyRect(pixels, width, height, rect)
        history.push(PixelDelta(rect = touched.clampTo(width, height), before = before, after = after))
    }
}
