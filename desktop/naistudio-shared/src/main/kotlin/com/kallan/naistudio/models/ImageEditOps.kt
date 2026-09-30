package com.kallan.naistudio.models

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 笔尖形状 —— 照 NovelAI 官方画布那三种：**圆 / 软圆 / 方**。
 *
 * 圆和方过去是硬边（像素级二值覆盖 ✗）—— 第 ㉔ 批起**三种都有抗锯齿** ✓
 *（用户 2026-09-24：「**画出来的线条锯齿大**」✗）：圆 / 方现在按"像素中心到轮廓的距离"
 * 算 1px 线性羽化 ✓（边界处覆盖度 0.5 ✓，见 [ImageEditOps.stampDab] ✓）；
 * 软圆照旧是"从半径一半处开始羽化的那一圈" ✓（它本来就是羽化边 ✓，公式一个字没动 ✓）。
 */
enum class BrushShape {
    ROUND,
    SOFT_ROUND,
    SQUARE,
}

/** 图像上的一块矩形（像素坐标）。历史栈按它存前后差量，所以叫"脏矩形"。 */
data class EditRect(val x: Int, val y: Int, val w: Int, val h: Int) {

    val isEmpty: Boolean get() = w <= 0 || h <= 0
    val area: Int get() = if (isEmpty) 0 else w * h

    /** 两个脏矩形合并（历史栈按"一笔"分组时不一定用到，但笔画累加要用）。 */
    fun union(other: EditRect): EditRect {
        if (isEmpty) return other
        if (other.isEmpty) return this
        val left = min(x, other.x)
        val top = min(y, other.y)
        val right = max(x + w, other.x + other.w)
        val bottom = max(y + h, other.y + other.h)
        return EditRect(left, top, right - left, bottom - top)
    }

    fun clampTo(width: Int, height: Int): EditRect {
        if (isEmpty) return this
        val left = x.coerceIn(0, max(0, width))
        val top = y.coerceIn(0, max(0, height))
        val right = (x + w).coerceIn(0, width)
        val bottom = (y + h).coerceIn(0, height)
        return EditRect(left, top, right - left, bottom - top)
    }

    companion object {
        val EMPTY = EditRect(0, 0, 0, 0)

        /**
         * 一段笔画的包围盒：线段两端各外扩 [radius]，再按边界裁剪。
         * 整段都落在画布外时返回 null（这时不用记历史）。
         */
        fun ofSegment(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            radius: Float,
            width: Int,
            height: Int,
        ): EditRect? {
            val left = min(x0, x1) - radius
            val top = min(y0, y1) - radius
            val right = max(x0, x1) + radius
            val bottom = max(y0, y1) + radius
            val x = kotlin.math.floor(left).toInt()
            val y = kotlin.math.floor(top).toInt()
            val w = kotlin.math.ceil(right).toInt() - x + 1
            val h = kotlin.math.ceil(bottom).toInt() - y + 1
            val rect = EditRect(x, y, w, h).clampTo(width, height)
            return if (rect.isEmpty) null else rect
        }
    }
}

/**
 * **画布编辑器的像素运算**（纯数组，不碰任何平台 API）。
 *
 * 和遮罩那套（[PixelMask] / `MaskBrush`）是**两件事**：
 *  · 遮罩是 1 字节/像素的二值位图，只用来告诉服务端"重绘哪一块"；
 *  · 这里动的是**图本身的 ARGB 像素**（画布编辑器 = 官方 Canvas 那套工具）。
 *
 * 颜色统一用 ARGB 打包整数（和 `ImageIo.fromArgb` / `pixelsOf` 一个口径）。
 * 混合按**非预乘**的 source-over 做（见 [overPixel]），所以半透明边上不会发黑。
 *
 * ## 第 ㉔ 批（用户 2026-09-24：「**画出来的线条锯齿大**」✗）
 *
 * [stampDab] 的覆盖度从"硬边（0/1 ✓）"改成**按距离的 1px 线性羽化** ✓ ——
 * 斜线与圆弧的边缘于是会出现**中间 alpha**（既不是 0 也不是 255 ✓）；
 * `highQualitySampling` 那一档再叠一层**边缘 2×2 超采样** ✓。
 * 这两条都**只改覆盖度怎么算** ✓，不改半径 / 步距 / 混色那些数值口径 ✓。
 */
object ImageEditOps {

    /** 笔刷直径（原图像素）。上限给得比遮罩那套大一点 —— 画布上刷大块是常见操作。 */
    const val BRUSH_MIN_PIXELS = 1
    const val BRUSH_MAX_PIXELS = 600

    /** 默认笔刷：官方截图里 Draw 的默认值就是 45。 */
    const val DEFAULT_BRUSH_PIXELS = 45

    // ---- 逐点压感 / 笔尖朝向的默认值（用户 2026-09-20：「**逐点压感和旋转做**」✓）----
    //
    // ⚠️ 这三个默认值的口径：**加起来必须等于"参数化之前"那条老公式** ✓ ——
    // 老代码是 `radius = base * (0.25 + 0.75 * pressure)` ✓（见 `AppState.PEN_BRUSH_MIN_FACTOR` ✓），
    // 也就是 `最小比例 0.25 + 曲线 1.0（线性）` ✓。所以默认状态下**默认手感一个字都没变** ✓。

    /** 最轻那一档的半径比例（SAI 的「最小半径」✓）：0.25 = 再轻也留 1/4 粗 ✓。 */
    const val DEFAULT_MIN_RADIUS_RATIO = 0.25f

    /** 压感曲线（1 = 线性 ✓）。 */
    const val DEFAULT_PRESSURE_CURVE = 1f

    /**
     * 笔尖之间的步距：**相对笔尖直径的百分比** ✓（SAI 的 `Spacing` 语义 ✓）。
     *
     * ⚠️ 这里的"直径"是**这一颗笔尖自己的直径**（压感之后的 ✓）—— 不是滑杆上那个基础直径 ✗。
     * 用户 2026-09-20 实机报的「**画出来是一排点**」就是这两者混用出来的 ✓
     *（轻按时笔尖只有基础半径的 25%，步距却按基础半径走 ⇒ 步距 > 笔尖直径 ⇒ 一排点 ✗）：
     * 口径见 `ImageEditSession.strokeTo` + [dabStepPixels] ✓。
     *
     * 默认 **10%**（SAI 的水彩 / 软笔常见 4~15% ✓）：25% 在轻压那一档会稀到看得出"一颗一颗" ✓，
     * 而 10% 又能被下面那条 [DAB_MAX_STEP_RATIO] 护栏兜住 ✓（护栏才是"永远不断线"的保证 ✓）。
     */
    const val DEFAULT_SPACING_PERCENT = 10f

    // ---- dab 走位的两条硬护栏（用户 2026-09-20：「保证永远不断线」✓）----

    /** dab 走位的**最小步距**（像素 ✓）：再密也不能到 0（否则一个像素盖一次，白白慢一倍 ✗）。 */
    const val DAB_MIN_STEP_PIXELS = 0.35f

    /**
     * dab 走位步距的**硬上限**：相对 [nibRadiusAlong]（**行进方向上的**笔尖半径 ✓）的比例 ✓。
     *
     * ## 为什么是 `0.4` 而不是 `0.9`（用户 2026-09-20 实机报「**画出来是一排点**」那条 ✓）
     *
     * `0.9` **只保证"相邻两颗笔尖勉强搭上"** ✓ —— 可笔迹不是"两颗笔尖"的事：
     *  · 叠上 `SizeJitter`（这一颗比基准小 ✓）之后，0.9r 的间隙就会被放大成**珠链** ✗；
     *  · 屏幕上看的是"一整条线"，而 0.9r 时相邻笔尖的重叠只有 `2r − 0.9r = 1.1r`（≈55% ✓）——
     *    人眼在半透明边、抗锯齿、缩放三件事叠起来之后，**依然看得出节** ✗。
     *
     * `0.4` ⇒ 相邻笔尖至少重叠 **~60%**（重叠 `2r − 0.4r = 1.6r`，占直径 2r 的 80% ✓，
     * 也就是"至少 60% 是**两颗一起**盖住的"✓）。网页版 `docs/brush-lab-simple.html` 的
     * `dabStep()` 里也是这一条（`return Math.max(0.35, Math.min(bySpacing, along * 0.4));` ✓），
     * 两边**同一个数** ✓ —— 这就是"口径一致"的意思 ✓。
     *
     * ⚠️ **它只在 spacing 调大时才咬得住** ✓：`Spacing = 10%`（默认 ✓）时
     * `bySpacing = 2r × 10% = 0.2r < 0.4r` ⇒ 默认笔迹密度**一个字都没变** ✓
     * （这也是 `PenDabEngineTest` / `CanvasStrokeCompatTest` / `ComicPaintStrokeContinuityTest`
     * 那几条老断言**不需要改数**的原因 ✓，见回报 ✓）。
     * 真正会变的只有两种：**spacing > 20%** ✓、以及**扁笔尖沿短轴走位**（那时行进方向上的半径
     * 只有 `r / nibRatio` ✓，`nibRatio = 8` ⇒ 步距从 `0.9 × r/8 = 2.25px` 收到 `0.4 × r/8 = 1.0px` ✓）。
     */
    const val DAB_MAX_STEP_RATIO = 0.4f

    /** 笔尖宽高比的可选范围（1 = 圆 ✓，越大越扁 ✓ —— 界面滑杆用同一对数 ✓）。 */
    const val NIB_MIN_RATIO = 1f
    const val NIB_MAX_RATIO = 8f

    /** 完全透明（橡皮擦出来的就是它）。 */
    const val TRANSPARENT = 0x00000000

    /**
     * 把一段笔画盖到像素上（从 [x0],[y0] 到 [x1],[y1]，单点就是首尾重合）。
     *
     * 判定用**点到线段的最短距离**（和遮罩笔刷同一套算法），所以快速拖动是连续的一笔，
     * 而不是一串离散的圆点。
     *
     * @param shape [BrushShape.ROUND] / [BrushShape.SQUARE] 是硬边；[BrushShape.SOFT_ROUND]
     *              在半径内侧一半处开始羽化。
     * @param erasing true = 擦（alpha 按覆盖度衰减，RGB 不动）。
     * @return 真正被改动的矩形；整段在画布外或半径不足 1 时返回 null。
     */
    fun stampSegment(
        pixels: IntArray,
        width: Int,
        height: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float,
        shape: BrushShape,
        color: Int,
        erasing: Boolean,
        /**
         * **选区裁剪**：非 0 才允许改这个像素（长度 = width*height）；null = 不裁。
         *
         * "有选区时就只在选区内工作"这件事就是靠它实现的（见 `ImageEditSession`）。
         */
        clip: ByteArray? = null,
    ): EditRect? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val r = radius.coerceAtLeast(0.5f)
        val rect = EditRect.ofSegment(x0, y0, x1, y1, r, width, height) ?: return null

        val dx = x1 - x0
        val dy = y1 - y0
        val lengthSquared = dx * dx + dy * dy
        val soft = shape == BrushShape.SOFT_ROUND
        // 软笔从半径的一半开始羽化；半径很小时至少留 0.5px 的过渡带，免得退化成硬边
        val featherInner = if (soft) max(0f, r * 0.5f) else 0f

        for (y in rect.y until (rect.y + rect.h)) {
            val row = y * width
            val centerY = y + 0.5f
            for (x in rect.x until (rect.x + rect.w)) {
                val index = row + x
                if (clip != null && (index >= clip.size || clip[index].toInt() == 0)) continue
                val centerX = x + 0.5f
                val t = if (lengthSquared <= 0f) {
                    0f
                } else {
                    (((centerX - x0) * dx + (centerY - y0) * dy) / lengthSquared).coerceIn(0f, 1f)
                }
                val nearestX = x0 + dx * t
                val nearestY = y0 + dy * t
                val offX = abs(centerX - nearestX)
                val offY = abs(centerY - nearestY)

                val distance = if (shape == BrushShape.SQUARE) {
                    max(offX, offY)
                } else {
                    kotlin.math.hypot(offX, offY)
                }
                if (distance > r) continue

                val coverage = if (!soft) {
                    1f
                } else if (distance <= featherInner) {
                    1f
                } else {
                    ((r - distance) / (r - featherInner)).coerceIn(0f, 1f)
                }
                if (coverage <= 0f) continue

                pixels[index] = if (erasing) {
                    erasePixel(pixels[index], coverage)
                } else {
                    overPixel(pixels[index], color, coverage)
                }
            }
        }
        return rect
    }

    /** 取某个像素的颜色；越界返回 0（透明）。 */
    fun colorAt(pixels: IntArray, width: Int, height: Int, x: Int, y: Int): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return TRANSPARENT
        val index = y * width + x
        if (index < 0 || index >= pixels.size) return TRANSPARENT
        return pixels[index]
    }

    /** 把一块矩形里的像素拷出来（历史栈的"前/后"两份就是它）。 */
    fun copyRect(pixels: IntArray, width: Int, height: Int, rect: EditRect): IntArray {
        val safe = rect.clampTo(width, height)
        if (safe.isEmpty) return IntArray(0)
        val out = IntArray(safe.area)
        for (row in 0 until safe.h) {
            val from = (safe.y + row) * width + safe.x
            System.arraycopy(pixels, from, out, row * safe.w, safe.w)
        }
        return out
    }

    /** [copyRect] 的反操作：把一份像素写回同一块矩形（撤销/重做用它）。 */
    fun writeRect(pixels: IntArray, width: Int, height: Int, rect: EditRect, data: IntArray) {
        val safe = rect.clampTo(width, height)
        if (safe.isEmpty) return
        if (data.size < safe.area) return
        for (row in 0 until safe.h) {
            val to = (safe.y + row) * width + safe.x
            System.arraycopy(data, row * safe.w, pixels, to, safe.w)
        }
    }

    /**
     * 从"整块 [outer] 的快照"里抠出子矩形 [inner] 那一份 ✓（行距按 `outer.w` 算 ✓）。
     *
     * 为什么需要它（第 ㉙ 批）：`stampDabAt` 那条"直接写层像素"的分支**先**按计划包围盒
     * （[outer]）抓 `before` ✓，可"真的改到哪一块"（[inner]）要**画完**才知道 ✓ ——
     * 两边不一致时，`PixelDelta` 的 `rect` 是 [inner] 而 `before`/`after` 是 [outer] 的形状 ✗
     * ⇒ [writeRect] 会按行距错位地把快照写回去 = **撤销啃掉一块画面** ✗。
     * 抠成同一块之后两者永远同口径 ✓。
     *
     * @param inner 必须是 [outer] 的子矩形（越界部分按"没有"处理 ✓）
     */
    fun cropSnapshot(snapshot: IntArray, outer: EditRect, inner: EditRect): IntArray {
        if (inner.isEmpty) return IntArray(0)
        val out = IntArray(inner.area)
        val dx = inner.x - outer.x
        val dy = inner.y - outer.y
        for (row in 0 until inner.h) {
            val srcRow = dy + row
            if (srcRow < 0 || srcRow >= outer.h) continue
            for (col in 0 until inner.w) {
                val srcCol = dx + col
                if (srcCol < 0 || srcCol >= outer.w) continue
                val from = srcRow * outer.w + srcCol
                if (from < 0 || from >= snapshot.size) continue
                out[row * inner.w + col] = snapshot[from]
            }
        }
        return out
    }

    /** 把整张（或一整块）刷成一个颜色 —— 画布编辑器的「清除」用它刷成透明。 */
    fun fillRect(
        pixels: IntArray,
        width: Int,
        height: Int,
        rect: EditRect,
        color: Int,
    ): EditRect {
        val safe = rect.clampTo(width, height)
        if (safe.isEmpty) return EditRect.EMPTY
        for (row in 0 until safe.h) {
            val offset = (safe.y + row) * width + safe.x
            java.util.Arrays.fill(pixels, offset, offset + safe.w, color)
        }
        return safe
    }

    /** 归一化笔刷直径 → 像素（1..[BRUSH_MAX_PIXELS]）。 */
    fun normalizedBrushPixels(requested: Int): Int =
        requested.coerceIn(BRUSH_MIN_PIXELS, BRUSH_MAX_PIXELS)

    // -----------------------------------------------------------------------
    // 逐点压感 + 笔尖朝向（**纯函数** ✓ —— 单测直接钉这几个 ✓，见 `PenDabEngineTest` ✓）
    //
    // 口径与 `docs/brush-lab.html` 的 `stamp()` 一致 ✓（参数名与语义照 SAI ✓，
    // 见 `docs/44-方案-笔刷引擎与SAI参数清单.md` ✓）：
    //   半径 = 基础半径 × (最小比例 + (1 - 最小比例) × pow(pressure, 压感曲线)) ✓
    // -----------------------------------------------------------------------

    /**
     * **压感 → 半径比例**（0..1 ✓）。
     *
     * `最小比例 + (1 - 最小比例) × pressure^曲线` ✓ ——
     *  · `minRadiusRatio = 0.25` + `pressureCurve = 1`（两个默认值 ✓）时
     *    正好等于参数化之前那条 `0.25 + 0.75 * p` ✓（**默认手感没变** ✓）；
     *  · 最轻也留 [minRadiusRatio] 那一档 ✓（不然"轻轻一点"看不见，用户会以为工具坏了 ✗）；
     *  · 压满 = 1 ✓（= 滑杆上那个值，手感的锚点 ✓）。
     *
     * ⚠️ 三个入参都可能来自界面 / 硬件（NaN、负数、0 曲线 ✓）—— 一律**收敛到合法区间** ✓，
     * 绝不把 NaN 漏进半径（NaN 半径会让矩形算不出来、笔尖一个像素都盖不上 ✗）。
     */
    fun pressureRadiusScale(
        pressure: Float,
        minRadiusRatio: Float = DEFAULT_MIN_RADIUS_RATIO,
        pressureCurve: Float = DEFAULT_PRESSURE_CURVE,
    ): Float {
        val minRatio = if (minRadiusRatio.isFinite()) minRadiusRatio.coerceIn(0f, 1f) else DEFAULT_MIN_RADIUS_RATIO
        val curve = if (pressureCurve.isFinite() && pressureCurve > 0f) pressureCurve else DEFAULT_PRESSURE_CURVE
        val p = if (pressure.isFinite()) pressure.coerceIn(0f, 1f) else 1f
        return minRatio + (1f - minRatio) * p.pow(curve)
    }

    /**
     * **压力 → 笔尖半径**（原图像素 ✓）：`基础半径（= 笔刷直径 / 2）× 压感比例` ✓。
     *
     * 这就是"逐点压感"那一条的落点 ✓ —— 一笔之内每个笔尖**各算各的** ✓，
     * 所以同一笔会自然由细变粗 / 由粗变细 ✓。
     */
    fun brushRadiusPixels(
        brushPixels: Int,
        pressure: Float,
        minRadiusRatio: Float = DEFAULT_MIN_RADIUS_RATIO,
        pressureCurve: Float = DEFAULT_PRESSURE_CURVE,
    ): Float = (normalizedBrushPixels(brushPixels) / 2f) *
        pressureRadiusScale(pressure, minRadiusRatio, pressureCurve)

    /**
     * **行进方向上的笔尖半径**（像素 ✓）—— 圆笔尖就是 [radius] 本身 ✓，扁笔尖是它在那条方向上的支撑半径 ✓。
     *
     * 为什么走位护栏非要用它：**扁笔尖横着走的时候，行进方向上的笔尖只有短轴那么厚** ✓ ——
     * 护栏要是只认长轴，`nibRatio = 8`（长轴 = 半径、短轴 = 半径 / 8）那一档沿短轴走位
     * 必然断成"一排点" ✗（用户 2026-09-20 那条 bug 的另一半 ✓）。
     *
     * 椭圆（半轴 `rx = radius` 沿局部 x、`ry = radius / nibRatio` 沿局部 y ✓）在方向 φ 上的
     * 支撑半径 = `1 / hypot(cosφ / rx, sinφ / ry)` ✓；这里 `φ = 行进方向 − 笔尖朝向`
     *（笔尖朝向见 [BrushEngine.nibAngleRadians] ✓）。
     */
    fun nibRadiusAlong(
        radius: Float,
        nibRatio: Float = 1f,
        nibAngleRadians: Float = 0f,
        travelAngleRadians: Float = 0f,
    ): Float {
        val rx = if (radius.isFinite()) radius.coerceAtLeast(0.5f) else 0.5f
        val ry = rx / nibRatioOrOne(nibRatio)
        val angle = (if (travelAngleRadians.isFinite()) travelAngleRadians else 0f) -
            (if (nibAngleRadians.isFinite()) nibAngleRadians else 0f)
        val cosA = cos(angle) / rx
        val sinA = sin(angle) / ry
        return 1f / kotlin.math.hypot(cosA, sinA)
    }

    /**
     * **沿路径盖笔尖时，两颗笔尖之间走多远**（像素 ✓）—— 走位那一半的**唯一口径** ✓
     *（`ImageEditSession.strokeTo` 用它 ✓，`docs/brush-lab.html` 的 `walkTo` 用同一个公式 ✓）。
     *
     * ```
     * step = clamp(r × 2 × spacing%, 下限 = DAB_MIN_STEP_PIXELS, 上限 = 0.4 × 行进方向上的 r)
     * ```
     *
     *  · 第一项：`spacing%` 是**相对这一颗笔尖的直径** ✓（SAI 的 `Spacing` 语义 ✓）——
     *    这里的 `r` 必须是**这一颗笔尖当场算出来的半径**（压感之后的 ✓）；
     *    拿基础半径来算就退化成用户实机报的「**一排点**」✗（轻按时 r 只有基础半径的 25%，
     *    步距却按基础半径走 ⇒ 步距 > 笔尖直径 ⇒ 两颗笔尖之间必然有空隙 ✗）；
     *  · 第二项：**硬护栏** ✓ —— spacing 调到 1000%、压感把笔尖压到最小、扁笔尖沿短轴走位，
     *    都**不可能**出现肉眼可见的断点 ✓（见 [DAB_MAX_STEP_RATIO] ✓）。
     *
     * @param radius 这一颗笔尖的半径（**压力已经算进来** ✓）
     * @param spacingPercent 相对笔尖直径的百分比（越界会收敛到 1..1000 ✓）
     */
    fun dabStepPixels(
        radius: Float,
        spacingPercent: Float = DEFAULT_SPACING_PERCENT,
        nibRatio: Float = 1f,
        nibAngleRadians: Float = 0f,
        travelAngleRadians: Float = 0f,
    ): Float {
        val r = if (radius.isFinite()) radius.coerceAtLeast(0.5f) else 0.5f
        val spacing = if (spacingPercent.isFinite()) {
            spacingPercent.coerceIn(1f, 1000f)
        } else {
            DEFAULT_SPACING_PERCENT
        }
        val bySpacing = r * 2f * (spacing / 100f)
        val guard = nibRadiusAlong(r, nibRatio, nibAngleRadians, travelAngleRadians) * DAB_MAX_STEP_RATIO
        return maxOf(DAB_MIN_STEP_PIXELS, minOf(bySpacing, guard))
    }

    /**
     * 把任意角度收敛到 **[0, 360)** ✓（NaN / 无穷 → 0 ✓）。
     *
     * ⚠️ 现在**只有**"显示笔状态"那一路还在用它（`DesktopPlatform.penRotationDegrees` ✓，
     * 弧度 → 度 ✓）—— 笔刷那一路的"笔杆朝向"（`barrelAngleDegrees`）已经随旋转一起删掉了 ✓
     *（见 [NibAngleControl] 的说明 ✓）。
     */
    fun normalizeDegrees(degrees: Float): Float {
        if (!degrees.isFinite()) return 0f
        val wrapped = degrees % 360f
        return if (wrapped < 0f) wrapped + 360f else wrapped
    }

    /**
     * 一个笔尖的**脏矩形**（旋转后的椭圆 / 矩形外接盒 ✓，已裁到画布内 ✓）。
     *
     * ⚠️ 它和 [stampDab] 用的是**同一个算法** ✓ —— 所以"历史里存的前后快照"和"真正被改的像素"
     * 永远是同一块矩形 ✓（这两边只要差一格，撤销就会把没改过的像素写回去、画面被啃掉一块 ✗，
     * 这条老教训见 [stampSegment] 头上那段 ✓）。
     *
     * @param radius **长轴半径**（压力已经算进来的 ✓）
     * @param nibRatio 宽高比：1 = 圆 ✓、>1 = 短轴 = `radius / nibRatio`（扁 ✓）
     * @return 整块都在画布外 → null ✓
     */
    fun dabBounds(
        centerX: Float,
        centerY: Float,
        radius: Float,
        nibRatio: Float = 1f,
        angleRadians: Float = 0f,
        width: Int,
        height: Int,
    ): EditRect? {
        if (width <= 0 || height <= 0) return null
        if (!centerX.isFinite() || !centerY.isFinite()) return null
        val rx = if (radius.isFinite()) radius.coerceAtLeast(0.5f) else 0.5f
        val ry = rx / nibRatioOrOne(nibRatio)
        val angle = if (angleRadians.isFinite()) angleRadians else 0f
        val cosA = cos(angle)
        val sinA = sin(angle)
        // 旋转后的外接半宽半高（半轴 rx 沿局部 x、ry 沿局部 y ✓）
        val halfW = abs(rx * cosA) + abs(ry * sinA)
        val halfH = abs(rx * sinA) + abs(ry * cosA)
        val left = floor((centerX - halfW).toDouble()).toInt()
        val top = floor((centerY - halfH).toDouble()).toInt()
        val right = ceil((centerX + halfW).toDouble()).toInt()
        val bottom = ceil((centerY + halfH).toDouble()).toInt()
        val rect = EditRect(left, top, right - left + 1, bottom - top + 1).clampTo(width, height)
        return if (rect.isEmpty) null else rect
    }

    /** 宽高比收敛（NaN / 越界 → 圆 ✓）。 */
    private fun nibRatioOrOne(nibRatio: Float): Float =
        if (nibRatio.isFinite()) nibRatio.coerceIn(1f, NIB_MAX_RATIO) else 1f

    /**
     * **盖一个笔尖（dab）** —— 沿路径走位、每个笔尖各盖一次的"那一盖" ✓
     *（内核照 `docs/brush-lab.html` 的 `stamp()` ✓：椭圆笔尖 + 旋转 + 覆盖度 ✓）。
     *
     * 和 [stampSegment]（"点到线段的最短距离"，整段一次成型 ✓）的区别：
     *  · 这个**只认一个点 + 一个半径** ✓ ⇒ 半径可以**逐笔尖**不同（= 逐点压感 ✓）、
     *    角度也可以**逐渐尖**不同（= 笔杆旋转 ✓）；
     *  · 代价是"连成一笔"要靠**步距**（[DEFAULT_SPACING_PERCENT] ✓）保证 ✓
     *    —— 走位那一半在 `ImageEditSession.strokeTo` 里 ✓（照 lab 的 `walkTo` ✓）。
     *
     * 判定（都先把像素转进**笔尖自己的坐标系** ✓ —— 长轴 = 局部 x ✓）：
     *  · 圆 / 软圆：`hypot(nx/rx, ny/ry) <= 1` ✓（软圆在 0.5 处开始羽化 ✓，与 [stampSegment] 同口径 ✓）；
     *  · 方：`max(|nx|/rx, |ny|/ry) <= 1` ✓（= 跟着转的方块 ✓；不转 + 圆比 1 时就是原来的正方块 ✓）。
     *
     * @param radius **长轴半径**（压力已经算进来 ✓；最小 0.5 px ✓）
     * @param nibRatio 1 = 圆 ✓、>1 = 扁（短轴 = `radius / nibRatio` ✓）
     * @param angleRadians 笔尖朝向（见 [BrushEngine.nibAngleRadians] ✓；0 = 长轴水平 ✓）
     * @param highQualitySampling 边缘**超采样**开关（SAI 的 `HighQualitySampling` ✓，第 ㉔ 批起有作用点 ✓）——
     *   开着时边缘那圈像素按 **2×2 子采样**求平均（斜线 / 圆弧更顺 ✓，代价是那一圈多算 3 次 ✓）；
     *   关着（默认 ✓）时用解析式距离算覆盖度 ✓。
     * @return 真正被改动的矩形；一个像素都没碰到（整块在画布外 / 被选区裁光）→ null ✓
     */
    fun stampDab(
        pixels: IntArray,
        width: Int,
        height: Int,
        centerX: Float,
        centerY: Float,
        radius: Float,
        shape: BrushShape,
        color: Int,
        erasing: Boolean,
        nibRatio: Float = 1f,
        angleRadians: Float = 0f,
        /** 选区裁剪（同 [stampSegment] ✓）。 */
        clip: ByteArray? = null,
        /**
         * **纸纹 / 噪点贴图**（128×128 的 alpha ✓，见 `BrushEngine.buildTile` ✓）——
         * **按画布坐标对齐、逐像素相乘** ✓（网页 `texturedDab()` 那三步合成出来的净效果就是它 ✓）。
         *
         * null = 不用贴图 ✓（默认 ✓ ⇒ 老调用点一个字节都不变 ✓）。
         */
        texture: ByteArray? = null,
        /** 边缘 2×2 超采样（默认关 ✓ —— 关着就是"解析式 1px 羽化"，老画面一个字都不变 ✓）。 */
        highQualitySampling: Boolean = false,
    ): EditRect? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val rect = dabBounds(centerX, centerY, radius, nibRatio, angleRadians, width, height) ?: return null
        val rx = if (radius.isFinite()) radius.coerceAtLeast(0.5f) else 0.5f
        val ry = rx / nibRatioOrOne(nibRatio)
        val angle = if (angleRadians.isFinite()) angleRadians else 0f
        val cosA = cos(angle)
        val sinA = sin(angle)
        val soft = shape == BrushShape.SOFT_ROUND
        val square = shape == BrushShape.SQUARE

        var touched = 0
        for (y in rect.y until (rect.y + rect.h)) {
            val row = y * width
            val dy = centerY - (y + 0.5f)
            for (x in rect.x until (rect.x + rect.w)) {
                val index = row + x
                if (index < 0 || index >= pixels.size) continue
                if (clip != null && (index >= clip.size || clip[index].toInt() == 0)) continue
                val dx = centerX - (x + 0.5f)
                // 转进笔尖坐标系：长轴 = 局部 x ✓（与 `canvas.rotate` / `atan2` 同一套方向 ✓）
                val nx = dx * cosA + dy * sinA
                val ny = -dx * sinA + dy * cosA

                // ---- 覆盖度（第 ㉔ 批：**抗锯齿** ✓）----
                //
                // 以前是硬边（形状内 = 1、形状外 = 0 ✗）⇒ 斜线 / 圆弧必然"锯齿大" ✓（用户报的 ✓）。
                // 现在按**像素中心到笔尖轮廓的距离**算 1px 线性羽化 ✓：
                //   coverage = clamp(0.5 + 距离(px), 0, 1)   ← 边界处正好 0.5（标准盒式滤波 ✓）
                // 距离用一阶近似 `(1 − edge) / |∇edge|` ✓：
                //  · 圆：`|∇| = 1/r` ⇒ `距离 = (1 − edge) · r` ✓（= r − 像素到圆心的距离，逐字精确 ✓）；
                //  · 椭圆：`|∇| = hypot(nx/rx², ny/ry²)` ✓（扁笔尖的长短轴各自算对 ✓）；
                //  · 方：沿主导轴的 `(1 − max) · 该轴半径` ✓。
                //
                // ⚠️ **羽化只往形状内部做**（`edge > 1` 一律 continue ✓）—— 这是**故意的取舍** ✓：
                //    形状的像素覆盖范围被老单测钉死了（`PenDabEngineTest`：椭圆 38..40 / 18..20 ✓、
                //    恒压笔迹 42..45 px ✓），往外扩半个像素会当场把它们弄红 ✗。
                //    往内做照样消灭"0 → 255"的硬跳变（边上一圈落在 128..255 ✓，
                //    斜线边缘于是有**中间 alpha** ✓），只是最外圈比理想盒式滤波"实"一点 ⚠️。
                val edge: Float
                val gradient: Float
                if (square) {
                    val ex = abs(nx) / rx
                    val ey = abs(ny) / ry
                    if (ex >= ey) {
                        if (ex > 1f) continue
                        edge = ex
                        gradient = 1f / rx
                    } else {
                        if (ey > 1f) continue
                        edge = ey
                        gradient = 1f / ry
                    }
                } else {
                    edge = kotlin.math.hypot(nx / rx, ny / ry)
                    if (edge > 1f) continue
                    gradient = kotlin.math.hypot(nx / (rx * rx), ny / (ry * ry))
                }

                val shape0: Float = when {
                    soft -> if (edge <= 0.5f) 1f else ((1f - edge) / 0.5f).coerceIn(0f, 1f)
                    highQualitySampling -> supersampledCoverage(
                        centerX = centerX - x,
                        centerY = centerY - y,
                        rx = rx,
                        ry = ry,
                        cosA = cosA,
                        sinA = sinA,
                        square = square,
                    )

                    else -> {
                        val distance = if (gradient > 1e-6f) (1f - edge) / gradient else 0f
                        (0.5f + distance).coerceIn(0f, 1f)
                    }
                }
                if (shape0 <= 0f) continue
                // 纸纹 / 噪点：**贴图钉在画布上**（按像素坐标取格子 ✓），与笔尖覆盖度相乘 ✓
                val coverage = if (texture == null) shape0 else shape0 * BrushEngine.textureAt(texture, x, y)
                if (coverage <= 0f) continue
                pixels[index] = if (erasing) {
                    erasePixel(pixels[index], coverage)
                } else {
                    overPixel(pixels[index], color, coverage)
                }
                touched++
            }
        }
        return if (touched == 0) null else rect
    }

    /**
     * **一个像素的 2×2 超采样覆盖度**（`highQualitySampling = 1` 那一档 ✓）。
     *
     * 4 个子采样点取在像素的四个象限中心（±0.25 px ✓），各自算一次"形状内/外 + 1px 羽化"，
     * 最后平均 ✓ —— 斜线 / 圆弧的边缘因此能出现**真正的中间值** ✓
     * （包括"像素中心在外面、但有一个子采样点落在里面"的那种边角像素 ✓）。
     *
     * ⚠️ 这个分支**只在用户把「高品质采样」打开时才走** ✓（默认关 ✓）⇒
     *   默认路径的**像素覆盖范围与老版本逐格一致** ✓（老单测的门槛不会被它顶破 ✓）。
     *
     * @param centerX/centerY 这个像素中心**相对笔尖中心**的偏移（像素 ✓，注意符号与调用点一致 ✓）
     */
    private fun supersampledCoverage(
        centerX: Float,
        centerY: Float,
        rx: Float,
        ry: Float,
        cosA: Float,
        sinA: Float,
        square: Boolean,
    ): Float {
        val offsets = SUPERSAMPLE_OFFSETS
        var sum = 0f
        var i = 0
        while (i < offsets.size) {
            // 子采样点相对像素中心的偏移（像素 ✓）
            val px = centerX + offsets[i]
            val py = centerY + offsets[i + 1]
            val nx = px * cosA + py * sinA
            val ny = -px * sinA + py * cosA
            val edge: Float
            val gradient: Float
            if (square) {
                val ex = abs(nx) / rx
                val ey = abs(ny) / ry
                if (ex >= ey) {
                    edge = ex
                    gradient = 1f / rx
                } else {
                    edge = ey
                    gradient = 1f / ry
                }
            } else {
                edge = kotlin.math.hypot(nx / rx, ny / ry)
                gradient = kotlin.math.hypot(nx / (rx * rx), ny / (ry * ry))
            }
            val distance = if (gradient > 1e-6f) (1f - edge) / gradient else 0f
            sum += (0.5f + distance).coerceIn(0f, 1f)
            i += 2
        }
        return (sum / (offsets.size / 2)).coerceIn(0f, 1f)
    }

    /** [supersampledCoverage] 的 4 个子采样点（相对像素中心 ✓：四个象限中心 ✓）。 */
    private val SUPERSAMPLE_OFFSETS = floatArrayOf(-0.25f, -0.25f, 0.25f, -0.25f, -0.25f, 0.25f, 0.25f, 0.25f)

    /**
     * source-over 合成（非预乘）。
     *
     * `coverage` 是这一笔在这个像素上的覆盖度（硬边 = 1，软边 = 0..1）。
     * 底色是**不透明**时（生成图/导入图基本都是），结果就是朴素的线性插值；
     * 底色带 alpha 时按标准公式算，避免半透明边上出现"越描越黑"。
     */
    internal fun overPixel(dst: Int, src: Int, coverage: Float): Int {
        val a = coverage.coerceIn(0f, 1f) * (((src ushr 24) and 0xFF) / 255f)
        if (a <= 0f) return dst
        if (a >= 1f) return src

        val dstA = ((dst ushr 24) and 0xFF) / 255f
        val outA = a + dstA * (1f - a)
        if (outA <= 0f) return TRANSPARENT

        val srcR = (src ushr 16) and 0xFF
        val srcG = (src ushr 8) and 0xFF
        val srcB = src and 0xFF
        val dstR = (dst ushr 16) and 0xFF
        val dstG = (dst ushr 8) and 0xFF
        val dstB = dst and 0xFF

        val weight = dstA * (1f - a)
        val outR = ((srcR * a + dstR * weight) / outA).roundToInt().coerceIn(0, 255)
        val outG = ((srcG * a + dstG * weight) / outA).roundToInt().coerceIn(0, 255)
        val outB = ((srcB * a + dstB * weight) / outA).roundToInt().coerceIn(0, 255)
        val alpha = (outA * 255f).roundToInt().coerceIn(0, 255)
        return (alpha shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    /**
     * destination-out：只衰减 alpha，**RGB 原样留着**。
     *
     * 为什么不像"往透明色上混"那样顺手把 RGB 也拉向 0：PNG 里那些半透明的边
     * 一旦 RGB 变黑，之后当底图用时边缘会发灰发脏（半透明像素的颜色也是要用的）。
     *
     * 例外：**alpha 归零时归一成 0x00000000**。留着 `0x00RRGGBB` 虽然显示上同样是透明，
     * 但"全透明"的相等判断（`== TRANSPARENT`、`pixels.all { … }`）会失效 ——
     * 画布清空/撤销这些逻辑全靠它。
     */
    internal fun erasePixel(dst: Int, coverage: Float): Int {
        val c = coverage.coerceIn(0f, 1f)
        if (c <= 0f) return dst
        val dstA = (dst ushr 24) and 0xFF
        val outA = (dstA * (1f - c)).roundToInt().coerceIn(0, 255)
        if (outA == 0) return TRANSPARENT
        // 半透明：RGB 保持不变（透明像素的 RGB 无所谓，但半透明像素的 RGB 有用）
        return (outA shl 24) or (dst and 0x00FFFFFF)
    }

    // -----------------------------------------------------------------------
    // 二期那四个"改图"的工具：油漆桶 / 模糊 / 仿制图章 + HSV 调整、调整画布
    // -----------------------------------------------------------------------

    /**
     * **油漆桶**：从 ([x],[y]) 出发，把"颜色与起点相近"的**四连通**区域刷成 [color]。
     *
     * 「相近」= **每个通道（含 alpha）的差都 ≤ [tolerance]**（0..255）。
     * 容忍度是官方那个滑杆的含义：0 = 只有完全同色的一块，255 = 整张图。
     *
     * 用的是**显式栈 + visited 标记**（不是递归）：4K 图上递归必爆栈，
     * 而且 visited 能保证每个像素最多进栈几次，不会自己咬自己。
     *
     * @return 被刷到的矩形；起点越界、尺寸不对、或起点本来就是这个颜色（无事可做）时返回 null
     */
    fun floodFill(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        color: Int,
        tolerance: Int,
        /** 选区裁剪：只有选区里的像素才允许被灌（种子在外面就直接不做）。 */
        clip: ByteArray? = null,
    ): EditRect? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        if (x < 0 || y < 0 || x >= width || y >= height) return null
        val seedIndex = y * width + x
        if (clip != null && (seedIndex >= clip.size || clip[seedIndex].toInt() == 0)) return null
        val start = pixels[seedIndex]
        if (start == color) return null
        val tol = tolerance.coerceIn(0, 255)

        val visited = BooleanArray(width * height)
        // 可增长的显式栈：不动 4×n 的固定数组（4K 图上那是几十 MB 的白占）
        var stack = IntArray(1024)
        var top = 0
        stack[top++] = y * width + x
        var minX = x
        var maxX = x
        var minY = y
        var maxY = y
        var painted = 0

        while (top > 0) {
            val index = stack[--top]
            if (visited[index]) continue
            visited[index] = true
            if (clip != null && (index >= clip.size || clip[index].toInt() == 0)) continue
            if (!similar(pixels[index], start, tol)) continue

            pixels[index] = color
            painted++
            val px = index % width
            val py = index / width
            if (px < minX) minX = px
            if (px > maxX) maxX = px
            if (py < minY) minY = py
            if (py > maxY) maxY = py

            if (px > 0 && !visited[index - 1]) {
                if (top == stack.size) stack = stack.copyOf(stack.size * 2)
                stack[top++] = index - 1
            }
            if (px < width - 1 && !visited[index + 1]) {
                if (top == stack.size) stack = stack.copyOf(stack.size * 2)
                stack[top++] = index + 1
            }
            if (py > 0 && !visited[index - width]) {
                if (top == stack.size) stack = stack.copyOf(stack.size * 2)
                stack[top++] = index - width
            }
            if (py < height - 1 && !visited[index + width]) {
                if (top == stack.size) stack = stack.copyOf(stack.size * 2)
                stack[top++] = index + width
            }
        }
        if (painted == 0) return null
        return EditRect(minX, minY, maxX - minX + 1, maxY - minY + 1)
    }

    /** 每个通道的差都不超过 [tolerance] 才算"相近"。 */
    private fun similar(a: Int, b: Int, tolerance: Int): Boolean {
        if (a == b) return true
        val da = kotlin.math.abs(((a ushr 24) and 0xFF) - ((b ushr 24) and 0xFF))
        if (da > tolerance) return false
        val dr = kotlin.math.abs(((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF))
        if (dr > tolerance) return false
        val dg = kotlin.math.abs(((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF))
        if (dg > tolerance) return false
        val db = kotlin.math.abs((a and 0xFF) - (b and 0xFF))
        return db <= tolerance
    }

    /**
     * **模糊笔刷**：沿着这一笔把碰到的像素糊掉。
     *
     * @param intensity 0..100（官方那个强度滑杆）。0 = 不动，100 = 全用模糊值。
     * @param blurRadiusPixels 模糊半径（原图像素）—— 由界面用笔刷大小换算过来（约 1/3 笔径）。
     *
     * 关键点：**模糊要读"这一笔开始前的原图"**，不能边糊边读（那会顺着笔迹方向拖出一条尾巴）。
     * 所以先把要读的那块（矩形外扩一个模糊半径）快照下来，再做一次可分离方框模糊，
     * 最后按笔迹覆盖度把模糊值混回原像素。
     */
    fun blurStroke(
        pixels: IntArray,
        width: Int,
        height: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radiusPixels: Float,
        shape: BrushShape,
        blurRadiusPixels: Int,
        intensity: Int,
        /** 选区裁剪（见 [stampSegment]）。 */
        clip: ByteArray? = null,
    ): EditRect? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val amount = (intensity.coerceIn(0, 100)) / 100f
        if (amount <= 0f) return null
        val r = blurRadiusPixels.coerceIn(1, 64)
        val rect = EditRect.ofSegment(x0, y0, x1, y1, radiusPixels.coerceAtLeast(0.5f), width, height)
            ?: return null

        // 读源：矩形外扩 r（模糊要看到周围的像素）
        val srcRect = EditRect(rect.x - r, rect.y - r, rect.w + 2 * r, rect.h + 2 * r)
            .clampTo(width, height)
        if (srcRect.isEmpty) return null
        val snapshot = copyRect(pixels, width, height, srcRect)
        val blurred = boxBlur(snapshot, srcRect.w, srcRect.h, r)

        val soft = shape == BrushShape.SOFT_ROUND
        val featherInner = if (soft) radiusPixels * 0.5f else 0f
        val dx = x1 - x0
        val dy = y1 - y0
        val lengthSquared = dx * dx + dy * dy

        for (y in rect.y until (rect.y + rect.h)) {
            val centerY = y + 0.5f
            for (x in rect.x until (rect.x + rect.w)) {
                val clipIndex = y * width + x
                if (clip != null && (clipIndex >= clip.size || clip[clipIndex].toInt() == 0)) continue
                val centerX = x + 0.5f
                val t = if (lengthSquared <= 0f) {
                    0f
                } else {
                    (((centerX - x0) * dx + (centerY - y0) * dy) / lengthSquared).coerceIn(0f, 1f)
                }
                val offX = abs(centerX - (x0 + dx * t))
                val offY = abs(centerY - (y0 + dy * t))
                val distance = if (shape == BrushShape.SQUARE) max(offX, offY) else kotlin.math.hypot(offX, offY)
                if (distance > radiusPixels) continue
                val coverage = if (!soft || distance <= featherInner) {
                    1f
                } else {
                    ((radiusPixels - distance) / (radiusPixels - featherInner)).coerceIn(0f, 1f)
                }
                if (coverage <= 0f) continue
                val index = y * width + x
                val blurredIndex = (y - srcRect.y) * srcRect.w + (x - srcRect.x)
                if (blurredIndex < 0 || blurredIndex >= blurred.size) continue
                pixels[index] = lerpColor(pixels[index], blurred[blurredIndex], coverage * amount)
            }
        }
        return rect
    }

    /**
     * **仿制图章**：把"源点那块"按笔迹刷到当前位置。
     *
     * @param offsetX/offsetY 源点相对当前笔尖的偏移（界面在按下第一笔时定下来，整笔不变）。
     *        源像素越界就不动（不取边界像素去糊，那会在画布边上拖出一条脏边）。
     */
    fun cloneStroke(
        pixels: IntArray,
        width: Int,
        height: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radiusPixels: Float,
        shape: BrushShape,
        offsetX: Int,
        offsetY: Int,
        /** 选区裁剪（见 [stampSegment]）。 */
        clip: ByteArray? = null,
    ): EditRect? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        val rect = EditRect.ofSegment(x0, y0, x1, y1, radiusPixels.coerceAtLeast(0.5f), width, height)
            ?: return null
        val soft = shape == BrushShape.SOFT_ROUND
        val featherInner = if (soft) radiusPixels * 0.5f else 0f
        val dx = x1 - x0
        val dy = y1 - y0
        val lengthSquared = dx * dx + dy * dy
        var touched = 0

        for (y in rect.y until (rect.y + rect.h)) {
            val centerY = y + 0.5f
            for (x in rect.x until (rect.x + rect.w)) {
                val clipIndex = y * width + x
                if (clip != null && (clipIndex >= clip.size || clip[clipIndex].toInt() == 0)) continue
                val centerX = x + 0.5f
                val t = if (lengthSquared <= 0f) {
                    0f
                } else {
                    (((centerX - x0) * dx + (centerY - y0) * dy) / lengthSquared).coerceIn(0f, 1f)
                }
                val offX = abs(centerX - (x0 + dx * t))
                val offY = abs(centerY - (y0 + dy * t))
                val distance = if (shape == BrushShape.SQUARE) max(offX, offY) else kotlin.math.hypot(offX, offY)
                if (distance > radiusPixels) continue
                val coverage = if (!soft || distance <= featherInner) {
                    1f
                } else {
                    ((radiusPixels - distance) / (radiusPixels - featherInner)).coerceIn(0f, 1f)
                }
                if (coverage <= 0f) continue
                val srcX = x + offsetX
                val srcY = y + offsetY
                if (srcX < 0 || srcY < 0 || srcX >= width || srcY >= height) continue
                val index = y * width + x
                pixels[index] = overPixel(pixels[index], pixels[srcY * width + srcX], coverage)
                touched++
            }
        }
        return if (touched == 0) null else rect
    }

    /**
     * **HSV 调整**：把 [source] 逐像素调色后写进 [target]（两个数组都按 `width*height` 读）。
     *
     * @param hueShift 色相偏移，**度**（-180..180）
     * @param saturationScale 饱和度乘/加：-100..100（%），-100 = 去色
     * @param valueScale 明度：-100..100（%）
     *
     * 为什么是"源/目标两个数组"而不是就地改：对话框里拖滑杆要**实时预览**，
     * 每次都从同一份快照重算，才能来回拖都不累积（就地改的话越拖越黑）。
     */
    fun hsvAdjust(
        source: IntArray,
        target: IntArray,
        count: Int,
        hueShift: Float,
        saturationScale: Float,
        valueScale: Float,
    ) {
        val n = minOf(count, source.size, target.size)
        for (i in 0 until n) {
            val c = source[i]
            val a = (c ushr 24) and 0xFF
            if (a == 0) {
                target[i] = c
                continue
            }
            val rgb = rgbToHsv((c ushr 16) and 0xFF, (c ushr 8) and 0xFF, c and 0xFF)
            val h = (rgb[0] + hueShift + 360f) % 360f
            val s = (rgb[1] * (1f + saturationScale / 100f)).coerceIn(0f, 1f)
            val v = (rgb[2] * (1f + valueScale / 100f)).coerceIn(0f, 1f)
            val out = hsvToRgb(h, s, v)
            target[i] = (a shl 24) or (out[0] shl 16) or (out[1] shl 8) or out[2]
        }
    }

    /**
     * **调整画布大小**：内容按 ([left],[top]) 放到新画布上，露出来的地方是透明。
     *
     * `left`/`top` 是**内容在新画布里的左上角**（负数 = 从左边/上边裁掉一块）。
     * 尺寸校验交给调用方（`EditRect` / `NAI 尺寸`那套）。
     */
    fun resizeCanvas(
        pixels: IntArray,
        width: Int,
        height: Int,
        newWidth: Int,
        newHeight: Int,
        left: Int,
        top: Int,
    ): IntArray {
        val out = IntArray(newWidth * newHeight)
        if (newWidth <= 0 || newHeight <= 0) return out
        val fromX = max(0, -left)
        val fromY = max(0, -top)
        val toX = max(0, left)
        val toY = max(0, top)
        val copyW = min(width - fromX, newWidth - toX)
        val copyH = min(height - fromY, newHeight - toY)
        if (copyW <= 0 || copyH <= 0) return out
        for (row in 0 until copyH) {
            System.arraycopy(
                pixels,
                (fromY + row) * width + fromX,
                out,
                (toY + row) * newWidth + toX,
                copyW,
            )
        }
        return out
    }

    /** 可分离方框模糊（两趟一维均值）—— 比逐个像素开窗快一个量级。 */
    private fun boxBlur(src: IntArray, width: Int, height: Int, radius: Int): IntArray {
        if (radius <= 0 || width <= 0 || height <= 0) return src.copyOf()
        val horizontal = IntArray(src.size)
        val window = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val c = src[row + i.coerceIn(0, width - 1)]
                a += (c ushr 24) and 0xFF
                r += (c ushr 16) and 0xFF
                g += (c ushr 8) and 0xFF
                b += c and 0xFF
            }
            for (x in 0 until width) {
                horizontal[row + x] = ((a / window) shl 24) or ((r / window) shl 16) or
                    ((g / window) shl 8) or (b / window)
                val outIndex = (x - radius).coerceIn(0, width - 1)
                val inIndex = (x + radius + 1).coerceIn(0, width - 1)
                val out = src[row + outIndex]
                val into = src[row + inIndex]
                a += ((into ushr 24) and 0xFF) - ((out ushr 24) and 0xFF)
                r += ((into ushr 16) and 0xFF) - ((out ushr 16) and 0xFF)
                g += ((into ushr 8) and 0xFF) - ((out ushr 8) and 0xFF)
                b += (into and 0xFF) - (out and 0xFF)
            }
        }
        val out = IntArray(src.size)
        for (x in 0 until width) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val c = horizontal[i.coerceIn(0, height - 1) * width + x]
                a += (c ushr 24) and 0xFF
                r += (c ushr 16) and 0xFF
                g += (c ushr 8) and 0xFF
                b += c and 0xFF
            }
            for (y in 0 until height) {
                out[y * width + x] = ((a / window) shl 24) or ((r / window) shl 16) or
                    ((g / window) shl 8) or (b / window)
                val outIndex = (y - radius).coerceIn(0, height - 1)
                val inIndex = (y + radius + 1).coerceIn(0, height - 1)
                val o = horizontal[outIndex * width + x]
                val i = horizontal[inIndex * width + x]
                a += ((i ushr 24) and 0xFF) - ((o ushr 24) and 0xFF)
                r += ((i ushr 16) and 0xFF) - ((o ushr 16) and 0xFF)
                g += ((i ushr 8) and 0xFF) - ((o ushr 8) and 0xFF)
                b += (i and 0xFF) - (o and 0xFF)
            }
        }
        return out
    }

    /** 两个颜色按 [amount] 线性插值（含 alpha）。 */
    private fun lerpColor(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        if (t <= 0f) return from
        if (t >= 1f) return to
        val a = lerpChannel((from ushr 24) and 0xFF, (to ushr 24) and 0xFF, t)
        val r = lerpChannel((from ushr 16) and 0xFF, (to ushr 16) and 0xFF, t)
        val g = lerpChannel((from ushr 8) and 0xFF, (to ushr 8) and 0xFF, t)
        val b = lerpChannel(from and 0xFF, to and 0xFF, t)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerpChannel(from: Int, to: Int, t: Float): Int =
        (from + (to - from) * t).roundToInt().coerceIn(0, 255)

    /** RGB(0..255) → HSV（H 0..360、S/V 0..1）。 */
    private fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maxC = maxOf(rf, gf, bf)
        val minC = minOf(rf, gf, bf)
        val delta = maxC - minC
        val h = when {
            delta <= 1e-6f -> 0f
            maxC == rf -> 60f * (((gf - bf) / delta) % 6f)
            maxC == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }
        val s = if (maxC <= 1e-6f) 0f else delta / maxC
        return floatArrayOf(if (h < 0f) h + 360f else h, s, maxC)
    }

    /** HSV → RGB(0..255)。 */
    private fun hsvToRgb(h: Float, s: Float, v: Float): IntArray {
        val c = v * s
        val hp = (h % 360f) / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        return intArrayOf(
            ((r1 + m) * 255f).roundToInt().coerceIn(0, 255),
            ((g1 + m) * 255f).roundToInt().coerceIn(0, 255),
            ((b1 + m) * 255f).roundToInt().coerceIn(0, 255),
        )
    }
}

/** 一笔的**动作类型**：画 / 擦 / 模糊 / 图章。 */
enum class StrokeMode {
    PAINT,
    ERASE,
    BLUR,
    CLONE,
}

/**
 * [BrushSpec.angleControl] 的**两个**取值 —— **语义照 SAI 的 `AngleControl`** ✓
 *（`docs/44-方案-笔刷引擎与SAI参数清单.md` §2.1 ✓，**不自己发明命名** ✓）。
 *
 * ## ⚠️ 这里**只有两态**了（2026-09-20 第二批：照 `docs/brush-lab-simple.html` 对齐 ✓）
 *
 * SAI 原版是 `0 无 / 1 自动 / 2 笔杆方向` 三态 ✓，但 `2` 是给**笔杆旋转**（twist）用的 ——
 * 用户 2026-09-20 已经取消了旋转 ✓，网页版 `docs/brush-lab-simple.html` 也把下拉框收成两条 ✓：
 * ```
 * <option value="0">0 固定（用 Angle）</option>
 * <option value="1">1 自动（跟随笔画方向）</option>
 * ```
 * ⇒ Kotlin 这边**照做** ✓：`BARREL` 与 `ImageEditOps.barrelAngleDegrees()` 一并删掉 ✓
 *（留着就是"有开关、没输入源"的空转 ✗，而且那个输入源本身还只是倾角方位角的**近似** ⚠️）。
 *
 * 用 `Int` 常量而不是 enum：[BrushSpec] 是**构造参数极多**的数据类 ✓，
 * 加一个 enum 会让"默认值 = 自动"这件事在界面 / 存档两边都要多一层转换 ✗；
 * 而且界面上就是一个下拉框 / 两颗芯片 ✓（值域 0..1 ✓）。
 */
object NibAngleControl {
    /** **0 固定**：笔尖长轴指 [BrushSpec.angle]（默认 0° = 长轴水平 ✓）。 */
    const val FIXED = 0

    /** **1 自动**（默认 ✓，与 SAI 的默认值一致 ✓）：长轴跟随**笔画方向** ✓。 */
    const val AUTO = 1
}

/**
 * 一笔的参数（在 `beginStroke` 那一刻定下来，整笔不变 ✓）。
 *
 * ## 两组参数，两个来源（别混 ✗）
 *
 *  · **这一笔怎么画**：[mode] / [brushPixels] / [shape] / [color] / [blurIntensity] /
 *    [cloneOffsetX] / [cloneOffsetY] ✓（本仓库一直以来的那几个 ✓）；
 *  · **笔刷参数**：[brush]（[BrushSpec] ✓ —— 网页版那一整套 SAI 参数 ✓，**唯一真源** ✓）。
 *
 * ⚠️ `spacing` / `angleControl` / 纵横比这三条**已经整体搬进 [BrushSpec]** ✓ ——
 * 这里**不再留副本** ✓（"一处真源"不能破 ✓）。`minRadiusRatio` / [pressureCurve] 留在
 * 这里是因为它们是**应用侧**的压感参数（管"压力怎么变成半径 / 浓度" ✓），
 * 和 SAI 那套"笔尖怎么盖"是两层 ✓（`docs/44` §5 把这两层分得清清楚楚 ✓）。
 *
 * ## 默认值 = 参数化之前那条老公式（回归钉 ✓）
 *
 * `radius = 基础半径 × (0.25 + 0.75 × pressure)` ✓（见 [ImageEditOps.DEFAULT_MIN_RADIUS_RATIO] ✓）：
 * 所以鼠标那条路（`pressure` 走默认的 1f ✓）画出来**和以前一模一样** ✓ ——
 * [brush] 默认（[BrushSpec.DEFAULTS] ✓）也只让它更像网页、不改画面 ✓。
 */
data class StrokeSpec(
    val mode: StrokeMode = StrokeMode.PAINT,
    val brushPixels: Int = ImageEditOps.DEFAULT_BRUSH_PIXELS,
    val shape: BrushShape = BrushShape.ROUND,
    /** 画笔 / 填充色（PAINT 用；CLONE 与 BLUR 忽略）。 */
    val color: Int = 0xFF000000.toInt(),
    /** 模糊强度 0..100（BLUR 用）。 */
    val blurIntensity: Int = 50,
    /** 图章的源偏移（CLONE 用，**原图像素**）。 */
    val cloneOffsetX: Int = 0,
    val cloneOffsetY: Int = 0,
    /** 最轻那一档的半径比例（SAI 的「最小半径」✓，默认 0.25 ✓）。 */
    val minRadiusRatio: Float = ImageEditOps.DEFAULT_MIN_RADIUS_RATIO,
    /** 压感曲线（1 = 线性 ✓）。 */
    val pressureCurve: Float = ImageEditOps.DEFAULT_PRESSURE_CURVE,
    /**
     * **这一笔的笔刷参数**（[BrushSpec] ✓ —— 网页 `docs/brush-lab-simple.html` 的 `DEFAULTS` ✓）。
     *
     * 默认 `BrushSpec()` = 网页的默认值 ✓ —— 而网页的默认值**恰好就是**本仓库一贯的行为 ✓
     *（`spacing = 10%` ✓、圆笔尖 ✓、`angleControl = 自动` ✓、混色 / 水分 / 纸纹全关 ✓）：
     * 于是"多了一整套参数"这件事对老调用点是**零影响** ✓。
     */
    val brush: BrushSpec = BrushSpec(),
    /**
     * **段内压力插值**（第 ㉙ 批做成显式开关 ✓，**默认关 = 网页口径** ✓）。
     *
     * 为什么默认关：网页 `docs/brush-lab-simple.html:798-802` 在**一段之内**所有笔尖都用
     * 当前事件那**一个**压力 ✓；Kotlin 原来在段内线性插值 ✗ ⇒ 半径 / 步距都不同 ⇒ 同一串
     * 输入点比网页**多落 15 颗**笔尖（实测 943 vs 928 ✗，见 `SimpleBrushParityTest` ✓）。
     * 关掉之后 928 vs 928 ✓。
     *
     * ⚠️ **取舍照实说**：软件没有 `getCoalescedEvents()` ⇒ 事件率天然低于网页 ✗ ⇒
     * 关掉插值在真机上可能"**压感台阶更大**"✗。留给下一批（输入侧：coalesced + 稳定器）处理 ✓。
     */
    val interpolateSegmentPressure: Boolean = false,
    /**
     * **输入侧稳定器强度（EMA ✓）** —— 第 ㉚ 批照 `docs/brush-lab-simple.html` 搬的 ✓。
     *
     * ⚠️ **默认 0 = 关** ✓（= 参数化之前那条老行为 ✓）。为什么默认关：
     * ㉗ 批抽的网页真值（`docs/brush-lab-simple-truth.json`）走的是**没有稳定器**那条路 ✓，
     * 默认开着判据①（928 颗）当场就变 ✗。
     * 界面那条路按网页默认档**开 35%** ✓（见 `AppState.comicPaintStabilize` ✓）。
     *
     * 口径与公式见 [StrokeStabilizer] ✓（`k = 1 - stabilize/100`、`smooth += (p-smooth)*k`、
     * 落笔点种子化、落笔那一颗不吃 EMA ✓）。
     */
    val stabilizePercent: Float = 0f,
    /**
     * **随机数 seed**（`ImageEditSession` 用它建 `kotlin.random.Random(seed)` ✓）。
     *
     * ⚠️ 整条引擎**绝不碰全局随机** ✗（`Math.random()` / `Random.Default` 都不许 ✓）——
     * 用户 2026-09-20 的口径：「**随机数要可复现** ✓，测试要靠它做确定性断言 ✓」。
     *  · 测试：显式给同一个 seed ⇒ 两次跑逐像素一致 ✓；
     *  · 界面：`AppState` 每落一笔 +1（同一个笔画序号 ⇒ 同一份抖动 ✓，但两笔不会长得一模一样 ✓）。
     */
    val seed: Long = 0L,
)
