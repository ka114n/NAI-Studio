package com.kallan.naistudio.models

import com.kallan.naistudio.services.InpaintSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * **聚焦重绘（Focused Inpainting）的纯几何**：官方那套"框一小块 → 放大到约 1MP 再重绘"。
 *
 * 官方口径（`docs.novelai.net/en/image/inpaint`）：
 *  · 用矩形选区框住要修的地方（框里可以刷蒙版，也可以留空 —— 留空就是整框重绘）；
 *  · **重绘前这一块会被放大到约 100 万像素**，所以细节更多（选区因此有尺寸上限）；
 *  · 框外还留一圈「**Minimum Context Area**」：只做参考、**不重绘**，让模型看得到周围。
 *
 * 落实成请求就是三步：
 *  1. 从原图里裁出 `框 + 上下文` → 缩放到目标尺寸（**框**的面积 ≈ 1MP）；
 *  2. 把蒙版按同一变换搬过去（框里有涂过就用涂的，没涂就整框涂白）；
 *  3. 出图之后**只把框那一块**贴回原图（上下文那圈丢掉 —— 它只是给模型看的）。
 *
 * 这个文件只有算术，不碰任何平台 API，所以能写 JVM 单测（见 `FocusedInpaintTest`）。
 */
object FocusedInpaint {

    /** 目标：让**框内**约 100 万像素（官方就是"approximately one megapixel"）。 */
    const val TARGET_FRAME_PIXELS = 1_048_576

    /**
     * **官方免费档**（2026-09-16 用户要求"改为官方的标准"）：
     * 整块请求 **每边 ≤1024、总面积 ≤1024×1024 = 1MP**。
     *
     * 依据（官方原文）：
     *  · Inpaint 页：「this section gets **upscaled** to approximately **one megapixel**
     *    (the size of the selection **is limited to a smaller size**)」
     *    —— 选区要被限制住，正是为了让整块留在 1MP 这一档；
     *  · Subscription 页脚注：「For images of **up to 1024x1024 pixels** and up to 28 steps…」
     *  · Inpaint 页：「if you are an **Opus** subscriber, Focused Inpainting also lets you
     *    inpaint regions on large images at **zero Anlas** cost.」
     *
     * 也就是说：**"大图零点数"的条件就是"这次请求 ≤1MP"**（源图多大无所谓）。
     * 代价：带上下文圈时，框本身吃不满 1MP（上下文也占面积）—— 想最大化框就把"最小上下文"调到 0。
     */
    const val FREE_TIER_MAX_SIDE = 1024
    const val FREE_TIER_MAX_PIXELS = 1024 * 1024

    /** 请求面积上限：和 `fitNaiImageSize` 一个口径（超过服务端不收）。 */
    const val MAX_REQUEST_PIXELS = 3_145_728

    /**
     * 放大倍率上限（兜底）：真正决定尺寸的是用户调的那个值 + 下面的整块上限。
     * 官方没写这个数，留 8× 只是防住荒谬的极端值。
     */
    const val MAX_UPSCALE = 8f

    /** 倍率下限：允许缩小（框本身就比上限大时），但不至于缩到看不见。 */
    const val MIN_SCALE = 0.1f

    /**
     * 一次聚焦重绘的**裁剪与请求方案**。
     *
     * @param cropX/cropY/cropW/cropH 从**原图**里裁出来的矩形（含上下文）
     * @param requestW/requestH 发出去的尺寸（64 对齐、**整块 ≤1024×1024**，见 [FREE_TIER_MAX_PIXELS]）
     * @param frameInCrop 框在**裁剪图**里的位置（缩放前，原图像素）
     * @param frameInRequest 框在**请求图**里的位置（缩放后）
     */
    data class Plan(
        val cropX: Int,
        val cropY: Int,
        val cropW: Int,
        val cropH: Int,
        val requestW: Int,
        val requestH: Int,
        val frameInCrop: EditRect,
        val frameInRequest: EditRect,
        /** 放大倍率（1 = 原尺寸；整块超上限时会 <1，即缩小）。 */
        val scale: Float,
        /** 用户**调的**倍率（[scale] 可能因为上限被夹小，两者不等时界面上要说明）。 */
        val requestedScale: Float,
        /** true = 这次是"框比免费档小 → 自动放大到接近额度"（用户设的倍率没参与）。 */
        val autoFilled: Boolean,
        /** 框本身被放大到了多少像素（界面上可以提示"≈1MP"）。 */
        val frameRequestPixels: Int,
        /** 这次请求是否在官方免费档内（每边 ≤1024、面积 ≤1MP）→ Opus 大图聚焦重绘 0 点数。 */
        val inFreeTier: Boolean,
    )

    /**
     * 算出方案。
     *
     * @param frame 用户框的那块（**原图像素**）
     * @param contextPixels 「Minimum Context Area」：框外留多少像素做参考（0 = 不留）
     * @param scale **放大倍率 = 用户自己调的**（1.0 = 不放大，即"不强制放大"）。
     *   官方口径里这一步是"选区放大到约 1MP 以获取更多细节"，但那是**官方按自己的目标算**；
     *   用户 2026-09-16 要求"改成放大倍率自己调，不强制放大"，所以就按这个值来，
     *   只在下面两个上限处夹一次：
     * @param limitToFreeTier true = 整块请求夹在官方免费档内（每边 ≤1024、面积 ≤1MP，
     *   Opus 大图 0 点数的前提）；false = 只受服务端上限 [MAX_REQUEST_PIXELS] 约束（会扣点数）。
     *   ⚠️ [autoSizeByRect] = true 时这一项**不再参与尺寸计算**（只影响界面上的免费档提示 ✓）。
     * @param autoSizeByRect **用户 2026-09-26 的新口径：尺寸全自动算** ✓。
     *
     *   用户原话：「聚焦修改的**长宽自动计算**，框出来的大小**自动按照比例**，然后在
     *   **1024×1024 像素以下**自动计算**最高像素**；**宽高可以超过 1024，但总像素不行**。」
     *   （落到代码就是 [InpaintSize.forRect]，纯函数 + 单测见那个文件）
     *
     *   true 时请求尺寸改由 [InpaintSize.forRect] 算：**等比 → 面积顶到 1MP 以下的最大值 →
     *   两边对齐 64（"保 64"，超了就只退长边）**；上面那两个旧旋钮（[scale] 倍率、
     *   [limitToFreeTier] 免费档）就此**不再决定尺寸** —— "自动计算"就是不让它们插手 ✓。
     *
     *   ⚠️ 喂给 [InpaintSize.forRect] 的是**裁剪块**（框 + 上下文圈）的像素尺寸，不是"只有框"：
     *   请求图发的就是这张裁剪块，若按**框**的比例定请求尺寸，加了上下文圈之后裁剪块的
     *   宽高比和请求不一致，缩放会变成**各向异性拉伸**（框里的东西被压扁/拉长 ✗）。
     *   按裁剪块的比例定就永远是等比缩放 ✓；`contextPixels = 0` 时裁剪块 = 框本身，
     *   于是 `forRect(裁剪块) == forRect(框宽, 框高)`，与用户口径逐字一致 ✓。
     *   界面上**只读**显示的那个"聚焦尺寸"用的是 `forRect(框宽, 框高)`（框本身的目标尺寸）。
     */
    fun plan(
        frame: EditRect,
        contextPixels: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        scale: Float = 1f,
        limitToFreeTier: Boolean = true,
        autoSizeByRect: Boolean = false,
    ): Plan? {
        if (sourceWidth <= 0 || sourceHeight <= 0) return null
        val safeFrame = frame.clampTo(sourceWidth, sourceHeight)
        if (safeFrame.isEmpty) return null
        val context = contextPixels.coerceIn(0, 512)

        // ⚠️ 先把四条边各自夹进画布，**再**算宽高。
        // 反过来（先算 `框宽 + 2×上下文` 再夹起点）会在贴着画布边时把那一侧的上下文
        // "补"到另一侧去 —— 明明左边没地方了，右边却凭空多出一倍的参考圈（单测抓到过）。
        val left = (safeFrame.x - context).coerceAtLeast(0)
        val top = (safeFrame.y - context).coerceAtLeast(0)
        val right = (safeFrame.x + safeFrame.w + context).coerceAtMost(sourceWidth)
        val bottom = (safeFrame.y + safeFrame.h + context).coerceAtMost(sourceHeight)
        val cropX = left
        val cropY = top
        val cropW = right - left
        val cropH = bottom - top
        if (cropW <= 0 || cropH <= 0) return null

        val frameInCrop = EditRect(
            safeFrame.x - cropX,
            safeFrame.y - cropY,
            safeFrame.w,
            safeFrame.h,
        )

        // 倍率有两套来源（用户 2026-09-16 定的口径）：
        //  · **框比免费档额度小** → 自动放大到"填满额度"（尽可能接近 1MP，拿细节）；
        //  · **框已经超过额度** → 按用户自己设的倍率（"以设定放大为准"）。
        // 免费档关掉时不做自动放大（没有"免费数值"可对齐），一律按设定。
        val requested = scale.coerceIn(MIN_SCALE, MAX_UPSCALE)
        val cropPixels = cropW.toFloat() * cropH.toFloat()
        val maxPixels = if (limitToFreeTier) {
            FREE_TIER_MAX_PIXELS.toFloat()
        } else {
            MAX_REQUEST_PIXELS.toFloat()
        }
        val sideCap = if (limitToFreeTier) FREE_TIER_MAX_SIDE else NAI_MAX_DIMENSION
        // "整块填满额度"所需的倍率（面积与单边两个约束取小）
        val areaFill = sqrt(maxPixels / cropPixels)
        val sideFill = min(
            sideCap.toFloat() / cropW.toFloat(),
            sideCap.toFloat() / cropH.toFloat(),
        )
        val fill = min(areaFill, sideFill)
        // ⚠️ 这个只是**旧口径**（2026-09-16"倍率自己调"）的标记；[autoSizeByRect] = true 时
        //    尺寸走 `InpaintSize.forRect`（见下面那段），这个值只在界面上沿用"自动放大"的说法 ✓。
        val autoFilledLegacy = limitToFreeTier && fill > 1f
        val wanted = if (autoFilledLegacy) fill else requested
        // ⚠️ 仍然要夹一次：允许小于 1（框本身就超额度时只能缩下来，官方 "selection size is limited" 同义）
        val scale = minOf(wanted, areaFill, sideFill).coerceIn(MIN_SCALE, MAX_UPSCALE)

        val rawW = (cropW * scale).roundToInt().coerceAtLeast(64)
        val rawH = (cropH * scale).roundToInt().coerceAtLeast(64)
        // 64 对齐 + 面积夹取：和别处用同一个函数，保证"我们算的尺寸"就是"payload 里的尺寸"
        var (requestW, requestH) = fitNaiImageSize(rawW, rawH, rawW, rawH)
        // 对齐是"就近取 64"，有可能顶破上限（例如 1000→1024 没事，1050→1088 就破了）——
        // 这里再硬夹一次，保证不超过这次用的额度上限
        if (requestW > sideCap || requestH > sideCap ||
            requestW.toLong() * requestH > maxPixels.toLong()
        ) {
            val fit = minOf(
                sideCap.toDouble() / requestW,
                sideCap.toDouble() / requestH,
                sqrt(maxPixels.toDouble() / (requestW.toDouble() * requestH)),
            )
            requestW = max(
                NAI_MIN_DIMENSION,
                (requestW * fit / NAI_DIMENSION_STEP).toInt() * NAI_DIMENSION_STEP,
            )
            requestH = max(
                NAI_MIN_DIMENSION,
                (requestH * fit / NAI_DIMENSION_STEP).toInt() * NAI_DIMENSION_STEP,
            )
        }

        // ---- 用户 2026-09-26 的口径：尺寸**全自动**算（`InpaintSize.forRect`）----
        //
        // 上面那一段是 2026-09-16 的旧口径（"倍率自己调" + 免费档夹取）。用户 2026-09-26
        // 把它换成一句话：「长宽自动计算，框出来的大小自动按照比例，1024×1024 以下自动计算
        // 最高像素；宽高可以超过 1024，但总像素不行」—— 所以这里**覆盖**掉旧算式 ✓。
        //
        // 为什么喂的是**裁剪块**（框 + 上下文圈）而不是"只有框"：
        //  · 请求图发出去的就是这张裁剪块，缩放是 cropW×cropH → requestW×requestH；
        //  · 若按**框**的宽高比定请求尺寸，加了上下文圈之后裁剪块的比例和请求不一样，
        //    于是 actualScaleX ≠ actualScaleY —— **各向异性拉伸**，框里的东西会被压扁/拉长 ✗
        //    （框越细长越明显：3:1 的框配 96px 上下文，裁剪块只有约 2:1）；
        //  · 按裁剪块的比例定，则 request ≈ crop × 统一倍率，**永远等比** ✓。
        //  · `contextPixels = 0` 时裁剪块就是框本身，`forRect(裁剪块) == forRect(框宽, 框高)` ✓。
        //
        // `InpaintSize.forRect` 保证：等比 ✓、总面积 ≤ 1024×1024 ✓、两边都是 64 的倍数 ✓、
        // 最短边 ≥ 64 ✓；最大边最多到 1MP / 64 = 16384，远小于服务端单边上限
        // （[NAI_MAX_DIMENSION] = 3.1MP / 64）✓ —— 所以这里不需要再夹一次。
        if (autoSizeByRect) {
            val (autoW, autoH) = InpaintSize.forRect(cropW.toFloat(), cropH.toFloat())
            requestW = max(NAI_MIN_DIMENSION, autoW)
            requestH = max(NAI_MIN_DIMENSION, autoH)
        }
        // 自动算尺寸时，对界面来说"倍率没参与"这件事同样成立（详情行会写"自动 …×"）✓
        val autoFilled = autoSizeByRect || autoFilledLegacy

        val actualScaleX = requestW.toFloat() / cropW.toFloat()
        val actualScaleY = requestH.toFloat() / cropH.toFloat()
        val frameInRequest = EditRect(
            (frameInCrop.x * actualScaleX).roundToInt(),
            (frameInCrop.y * actualScaleY).roundToInt(),
            (frameInCrop.w * actualScaleX).roundToInt().coerceAtLeast(1),
            (frameInCrop.h * actualScaleY).roundToInt().coerceAtLeast(1),
        ).clampTo(requestW, requestH)
        if (frameInRequest.isEmpty) return null

        return Plan(
            cropX = cropX,
            cropY = cropY,
            cropW = cropW,
            cropH = cropH,
            requestW = requestW,
            requestH = requestH,
            frameInCrop = frameInCrop,
            frameInRequest = frameInRequest,
            scale = requestW.toFloat() / cropW.toFloat(),
            requestedScale = requested,
            autoFilled = autoFilled,
            frameRequestPixels = frameInRequest.area,
            // ⛔ 2026-09-22 修：**只看总面积** ✓ —— 官方那句 "up to 1024x1024 pixels" 说的是**面积** ✓
            //    （用户实测：`1792×576` = 1.03MP 这一档**实际没扣点数** ✓，
            //     而旧判据"每边都要 ≤1024"把它判成"要扣" ✗ —— 正是他报的"显示不在免费档" ✓）。
            inFreeTier = requestW.toLong() * requestH <= FREE_TIER_MAX_PIXELS,
        )
    }

    /**
     * 把"用户在整图上涂的蒙版"搬进请求图：**只保留框内的部分**（框可以留空 = 整框重绘）。
     *
     * @param fullMask 整图尺寸的蒙版（1 字节/像素，255 = 要重绘）
     * @param plan [plan] 算出来的方案
     * @param fillWholeFrame 框里一点都没涂时是否把整框当成蒙版（官方：留空就是整框重绘）
     * @return 请求尺寸的蒙版；**返回空**表示框里没内容可重绘
     */
    fun maskIntoRequest(
        fullMask: PixelMask,
        plan: Plan,
        fillWholeFrame: Boolean = true,
    ): PixelMask? {
        val out = ByteArray(plan.requestW * plan.requestH)
        val frame = plan.frameInRequest
        // 先把整图蒙版最近邻搬进请求图（只搬框内那部分需要的区域，省得整张扫）
        var paintedInsideFrame = false
        val scaleX = fullMask.width.toFloat() / plan.cropW.toFloat()
        val scaleY = fullMask.height.toFloat() / plan.cropH.toFloat()
        for (y in frame.y until (frame.y + frame.h)) {
            if (y < 0 || y >= plan.requestH) continue
            val sourceY = (plan.cropY + y / scaleY).toInt().coerceIn(0, fullMask.height - 1)
            val row = y * plan.requestW
            for (x in frame.x until (frame.x + frame.w)) {
                if (x < 0 || x >= plan.requestW) continue
                val sourceX = (plan.cropX + x / scaleX).toInt().coerceIn(0, fullMask.width - 1)
                if (fullMask.valueAt(sourceX, sourceY) != 0) {
                    out[row + x] = 255.toByte()
                    paintedInsideFrame = true
                }
            }
        }
        if (!paintedInsideFrame) {
            if (!fillWholeFrame) return null
            // 框里没涂 → 整框重绘（官方"leave it empty"那条）
            for (y in frame.y until (frame.y + frame.h)) {
                if (y < 0 || y >= plan.requestH) continue
                val row = y * plan.requestW
                for (x in frame.x until (frame.x + frame.w)) {
                    if (x < 0 || x >= plan.requestW) continue
                    out[row + x] = 255.toByte()
                }
            }
        }
        val requestMask = PixelMask(plan.requestW, plan.requestH, out)
        return if (requestMask.isEmpty()) null else requestMask
    }

    /**
     * **回贴**：把服务端返回的图**只把框那块**写回原图。
     *
     * 上下文那圈故意丢掉 —— 它只是给模型"看"的参考，留着反而会把没让你改的地方也改了。
     *
     * @param generated **已经缩放到"裁剪尺寸"**的像素（`plan.cropW × plan.cropH`）。
     *   ⚠️ 这里踩过一脚：函数原先声称要"请求尺寸"（`requestW × requestH`）的像素并按请求宽做行距，
     *   而调用方是**先缩放到裁剪尺寸再传进来**的 —— 两个尺寸差着放大倍率，于是按 1472 宽去索引
     *   644 宽的数组，贴出来是一片**横向雪花**（框外倒是 0 像素，所以看着"像"只改了框内）。
     *   现在按裁剪宽做行距，并且用**渐变**的假返回图写单测（纯色看不见行距错）。
     * @return 真正被改动的矩形（原图坐标）；没改到返回 null
     */
    fun pasteBack(
        canvas: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        generated: IntArray,
        plan: Plan,
    ): EditRect? {
        val frame = plan.frameInCrop
        val cropW = plan.cropW
        val cropH = plan.cropH
        if (generated.size < cropW * cropH) return null
        var touched = 0
        for (row in 0 until frame.h) {
            val cropY = frame.y + row
            if (cropY < 0 || cropY >= cropH) continue
            val canvasY = plan.cropY + cropY
            if (canvasY < 0 || canvasY >= sourceHeight) continue
            val sourceRow = cropY * cropW
            val canvasRow = canvasY * sourceWidth
            for (col in 0 until frame.w) {
                val cropX = frame.x + col
                if (cropX < 0 || cropX >= cropW) continue
                val canvasX = plan.cropX + cropX
                if (canvasX < 0 || canvasX >= sourceWidth) continue
                canvas[canvasRow + canvasX] = generated[sourceRow + cropX]
                touched++
            }
        }
        if (touched == 0) return null
        return EditRect(
            plan.cropX + frame.x,
            plan.cropY + frame.y,
            frame.w,
            frame.h,
        ).clampTo(sourceWidth, sourceHeight)
    }

    /** 框在整图上的像素矩形（界面画那个虚线框用）。 */
    fun frameRect(shape: SelectionShape, width: Int, height: Int): EditRect? =
        SelectionGeometry.bounds(shape, width, height)
}
