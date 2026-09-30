package com.kallan.naistudio.models

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.ClipMode
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil

/**
 * **Skia 光栅工具**（第 ㉘ 批接进生产链路；第 ㉜a 批变成**主路径** ✓）。
 *
 * 为什么非得用 Skia（不是口味问题）：网页 `docs/brush-lab-simple.html` 的 `stamp()` 是拿 canvas2d 画
 * **抗锯齿的椭圆** ✓，而 Chromium 的 canvas2d 在 Windows 上就是 **Skia** ✓；本仓库的 Compose Desktop
 * 也是 Skia ✓ —— 同一套光栅器，才可能"画出来一模一样" ✓。
 * 实测（`SimpleBrushParityTest` 判据②，928 颗笔尖、1000×700）：
 * 墨迹像素差 **0.20%**、抗锯齿中间灰阶差 **0.06%**、逐像素灰度差**平均 0.023/255** ✓✓。
 *
 * ## ㉜a 批之后：这里只剩**三件事**，而且都作用在**层表面**上
 *
 * 以前（㉘/㉙）这里是"开一小块离屏表面 → 画椭圆 → 回读 → 按 source-over 合进 `IntArray`"，
 * 那一层间接是给**活笔画缓冲**用的 ✗。现在层本身就是一张 `org.jetbrains.skia.Surface`
 * （见 [ImageEditSession] ✓），所以：
 *
 *  · [stampOnSurface]：**把一档笔尖直接画到层表面上** ✓（页面坐标 ✓，不再有"三个坐标系"那套
 *    容易写混的换算 ✗ —— 表面就是整页大小 ✓）；橡皮 = `DST_OUT` ✓（**与网页 `stamp()` 的
 *    `globalCompositeOperation = "destination-out"` 逐字同口径** ✓，见 `brush-lab-simple.html:455` ✓）；
 *  · [readRegionInto]：从层表面**一把读**回一块（快路 = `Surface.readPixels(Bitmap)` +
 *    `Bitmap.readPixels(info, rowBytes, 0, 0)` 一次 JNI ✓），**除回非预乘**写进页缓冲 ✓；
 *  · [writeRegionFrom]：反过来，把页缓冲的一块**传回**层表面（IntArray 那些操作改完像素之后 ✓）。
 *
 * ## 两条被实测钉死的口径（`SkiaStampCostTest` 钉着 ✓）
 *
 * 1. **读回来是预乘** ✓：层表面是 `N32Premul`（`BGRA_8888` + `ColorAlphaType.PREMUL` ✓）——
 *    Skia 的光栅画布必须预乘才能正确融合 ✓ ⇒ 写进页缓冲之前**必须除回去一次** ✓。
 *    ㉘ 批就栽在这：多除一次 alpha，抗锯齿中间灰阶像素 1694 → **8801（+419%）** ✗。
 * 2. **`Bitmap.installPixels(info, 我们的 ByteArray, rowBytes)` 是死胡同** ✗（**别再试** ✓）——
 *    skiko 的 JNI 实现是 `jbyte* pixels = new jbyte[pixelsLen]; GetByteArrayRegion(...)` 之后
 *    把这块**新分配的 native 内存**交给 `SkBitmap::installPixels` ✓ ⇒ Skia 读的/写的是那块副本，
 *    **我们自己的数组永远收不到像素** ✗。`Bitmap.readPixels` 那条（回读）是对的 ✓。
 *
 * ## ⚠️ 显示那条路**不许**用 `makeImageSnapshot()`（㉜a 批实测 ✓）
 *
 * `Surface.makeImageSnapshot()` 本身只要 **0.001 ms**（写时复制 ✓），但**拿着快照继续往表面上画**
 * 会触发**整页拷贝**：实测 A4 上"每颗笔尖之前快照一次"= **7.04 ms/颗**（纯画只要 0.0102 ms/颗，
 * **×690** ✗✗），"快照之后第一次画" = **6.9 ms** ✓。⇒ 显示链**每帧快照**等于每帧拷 33 MB ✗，
 * 所以 [ImageEditSession] 的显示走"**区域回读**"（[readRegionInto]，525×743 实测 0.076 ms ✓）。
 * 实测数字都在 `docs/54` 与 `SurfaceCanvasProbeTest` 里 ✓。
 */
internal object SkiaDabRaster {

    /** 回读 / 上传用的位图：**只增不减** ✓（一笔之内不会反复分配 ✓）。 */
    private var bitmap: Bitmap? = null

    private var bitmapW = 0
    private var bitmapH = 0

    /** 回读字节的**批量**落点（复用、只增不减 ✓）。 */
    private var region: IntArray = IntArray(0)

    /** 上传时用的字节缓冲（复用、只增不减 ✓）。 */
    private var uploadBytes: ByteArray = ByteArray(0)

    private fun regionInts(size: Int): IntArray {
        if (region.size < size) region = IntArray(size)
        return region
    }

    /** 诊断用：上一次失败发生在哪一步（`null` = 没失败过 ✓）。空着不影响生产行为 ✓。 */
    internal var lastError: String? = null

    /**
     * **真的走到快路的次数**（= `Bitmap.readPixels` 一把读成功 ✓）。
     *
     * 这是 ㉙ 批那条性能修复的**证据口** ✓：`SkiaStampCostTest` 断言它 > 0、
     * 而 [slowPathReads] == 0 ✓ —— 没有这两个数，"到底还在不在跑慢路"就只能靠嘴说 ✗。
     */
    var fastPathReads: Long = 0
        private set

    /** 掉进 `peekPixels().getColor()` 逐像素慢路的次数（**生产上必须是 0** ✓）。 */
    var slowPathReads: Long = 0
        private set

    /** **实测的回读缓冲峰值字节数**（= 最大的那一档笔尖包围盒 ✓，不是整页 ✗）—— 写进回报 ✓。 */
    val peakReadBytes: Int get() = bitmapW * bitmapH * 4

    /** 回读缓冲边长（诊断用 ✓）。 */
    val readBufWidth: Int get() = bitmapW
    val readBufHeight: Int get() = bitmapH

    /** 探针归零（测试用 ✓）。 */
    fun resetProbeCounters() {
        fastPathReads = 0
        slowPathReads = 0
    }

    // -----------------------------------------------------------------------
    // ⓪ **笔画级覆盖度累积**（第 ㉟① 批 ✓ —— 用户：「笔画不应是一个个个圆组成的」✗）
    // -----------------------------------------------------------------------

    private var coverageShape: ByteArray = ByteArray(0)

    /**
     * **墨量**缓冲（0..255 ✓）—— **累加饱和** ✓（`255 − (255−p)·(255−a)/255` ✓）。
     *
     * 这是治"**浓度**"的那一本账 ✓：与老路逐颗 source-over **同一个数学** ✓
     * ⇒ "单颗淡、靠重叠累积"那套浓度设计照旧成立 ✓
     *（第一版只有 `max` 一本账，实测内部均值从 247.80 塌到 32.51 ✗ —— 见 [accumulateOneShape] ✓）。
     */
    private var coverageInk: ByteArray = ByteArray(0)

    private var coverageW = 0
    private var coverageH = 0

    /**
     * **已经合成到表面上的覆盖度**（0..255 ✓）—— 第 ㊳ 批（2026-09-21）加的**第三本账** ✓。
     *
     * ## 为什么必须有它（`compositeCoverage` 的真 bug ✗）
     *
     * `compositeCoverage` 写的是**当前完整覆盖度** `cov = shapeCov × ink / 255` ✓，
     * 却用 `SRC_OVER` 叠上去 ✗ ⇒ **同一像素每合成一次就再叠一次** ✗。
     * 抬笔只合一次时看不出来 ✓（等于"一笔一次"那个设计 ✓），
     * 但**按帧合成**（A 方案 ✓）就会把墨一遍遍加深 ✗ ——
     * 实测（`ComicCoverageFrameTickTest` ✓）：每 3 点合一次 ⇒ 与"整笔一次"差 **1525 像素** ✗。
     *
     * ## 修法：合成**真增量**（`cov − compositedCov` ✓）
     *
     * 覆盖度累积是**单调**的 ✓（形状取 `max` ✓、墨量累加饱和 ✓）⇒ `cov` 只会变大 ✓
     * ⇒ `cov − compositedCov ≥ 0` 恒成立 ✓（下面还夹了一道 `coerceAtLeast(0)` 兜底 ✓）。
     * 每次只把"**新长出来的那一点**"用 `SRC_OVER` 叠上去 ✓ ——
     * 这正是一次性合成与分多次合成**等价**的充要条件 ✓
     *（source-over 的增量可加性：`1−Π(1−aᵢ)` 逐次拆开与一次算完相同 ✓）。
     *
     * ⚠️ 抬笔那一次（`endCoverage()` ✓）后这本账**随缓冲一起留着** ✓ ——
     * 下次 `beginCoverage` 与另两本一起清零 ✓（否则下一笔会以为"已经合过了"✗、整笔不见墨 ✗）。
     */
    private var coverageComposited: ByteArray = ByteArray(0)

    /** 诊断：这一笔累积了几颗笔尖进覆盖度缓冲 ✓（0 ⇒ 没走这条路 ✓）。 */
    var coverageDabs: Long = 0
        private set

    /**
     * 诊断用：**上一笔**累积了几颗（`endCoverage` 不清它 ✓ —— 测试要在抬笔之后读 ✓）。
     *
     * ⚠️ 为什么单独留一个：第一版把 [coverageDabs] 在 [endCoverage] 里清了 ✗，
     * 于是判据读到的永远是 0 / "没走这条路" ✗（`ContinuousStrokeCoverageProbeTest` 第一轮
     * 就是红在这条上 ✓ —— 判据本身没错，是计数被自己清掉了 ✗）。
     */
    var lastCoverageDabs: Long = 0
        private set

    /** 覆盖度缓冲**真的分配了多少字节**（诊断 / 内存实测 ✓；**三本账**各一 ✓）。 */
    val coverageBytes: Int get() = if (coverageShape.isEmpty()) 0 else 3 * coverageW * coverageH

    /** 开一笔：把三本账都备好（尺寸不符才分配 ✓）、整块清零 ✓。 */
    fun beginCoverage(width: Int, height: Int) {
        val n = width * height
        if (coverageW != width || coverageH != height || coverageShape.size != n) {
            coverageShape = ByteArray(n)
            coverageInk = ByteArray(n)
            coverageComposited = ByteArray(n)
            coverageW = width
            coverageH = height
        } else {
            java.util.Arrays.fill(coverageShape, 0)
            java.util.Arrays.fill(coverageInk, 0)
            // ⚠️ 这本**必须**跟着清 ✗：不清的话下一笔会以为"这一块已经合过了"✗ ⇒
            //    增量恒 0 ⇒ **整笔一个像素都上不去** ✗（实机症状 = "画不出东西"✗，比原 bug 更重 ✗）。
            java.util.Arrays.fill(coverageComposited, 0)
        }
        coverageDabs = 0
        lastCoverageDabs = 0
    }

    /** 这一笔结束了（缓冲留着复用 ✓，只标记不再累积 ✓）。 */
    fun endCoverage() {
        lastCoverageDabs = coverageDabs
        coverageDabs = 0
    }

    /**
     * 把 [plan] 的几颗笔尖**累积进覆盖度缓冲**（取 `max` ✓）——
     * **不碰层表面** ✓（合成是 [compositeCoverage] 的事 ✓）。
     *
     * 覆盖度怎么算（与老路的 alpha 口径**对齐** ✓，这样"浓度"不会莫名其妙变 ✓）：
     *  · 每颗笔尖的 alpha = `plan.alpha × 形状自己的覆盖度` ✓ ——
     *    `plan.alpha` 就是老路 `stampOnSurface` 用的那个 `alphaByte` ✓；
     *  · 形状覆盖度由 **Skia 自己**给（抗锯齿椭圆 ✓ / 矩形 ✓）—— 与老路**同一个光栅器** ✓
     *    （这正是 ㉜b/㉜c 坚持"上表面"的理由 ✓：不要退回手写解析羽化 ✗）。
     *
     * 实现：把这一档的笔尖画进一张**临时 alpha 表面**（`SRC` 覆盖写 ⇒ 拿到"这一颗"的形状覆盖度 ✓），
     * 再与 `coverageBuf` 逐像素取 `max` ✓。
     * ⚠️ 为什么不在真表面上直接 `BlendMode.MAX` ✗：`MAX` 作用在**整个 RGBA** 上 ✓ ——
     * 颜色不同（混色笔每颗采样色都不同 ✓）时会得到"通道各取各的最大"的脏色 ✗。
     * 单通道覆盖度 + 最后一次性上色 ⇒ 颜色口径**完全可控** ✓。
     */
    fun accumulateCoverage(
        plan: BrushEngine.DabPlan,
        texture: ByteArray? = null,
        square: Boolean = false,
    ): Boolean {
        if (coverageShape.isEmpty()) return false
        val alphaByte = (plan.alpha.coerceIn(0f, 1f) * 255f).toInt()
        if (alphaByte <= 0) return false
        var accumulated = false
        for (shape in plan.shapes) {
            if (accumulateOneShape(shape, alphaByte, texture, square)) accumulated = true
        }
        if (accumulated) coverageDabs++
        return accumulated
    }

    /**
     * 累积**一颗**形状（见 [accumulateCoverage] ✓）。
     *
     * ## ⚠️ 这里有两本账，**必须分开**（第一版把两件事混成一件，实测当场红了 ✗）
     *
     * 第一版写的是"`coverage = max(coverage, 形状覆盖度 × alphaByte)`" ——
     * **看起来**正是"取最大覆盖度"该有的样子 ✗，实测却塌了 ✓：
     * ```
     *   开关=关：内部均值 247.80、方差 44.06、宽度 61.95px ✓
     *   开关=开：内部均值  32.51、方差  0.33、宽度 64.50px ✗（一条淡到看不见的带子 ✗）
     * ```
     * 来路很清楚 ✓：`alphaByte` 是"**单颗**的 alpha"（这套参数下只有 ~33/255 ✓），
     * 取 `max` ⇒ 一整笔的浓度**顶到单颗那个值**就再也不涨了 ✗ ——
     * 老路 `1 − Π(1 − αᵢ)` 靠**几百颗叠**才把浓度堆到 248 ✓，`max` 把那个"叠"整个扔掉了 ✗。
     *
     * ⇒ 所以两本账分开 ✓（这才是"连贯 + 有浓度 + 有颗粒"三条同时成立的做法 ✓）：
     *  · **形状覆盖度**（抗锯齿边 + 纸纹 ✓）→ 取 `max` ✓
     *    —— 这是**治"一颗颗圆"**的那一本 ✓：中心线上相邻笔尖取到的是"最大的那一颗"，
     *    不会因为"这一颗正好淡一点 / 小一点"而凹下去 ✓ ⇒ **与间距 / 抖动无关** ✓；
     *  · **墨量**（`alphaByte` ✓）→ **累加并饱和**（`ink = 255 − (255−ink)·(255−α)/255` ✓，
     *    这就是逐颗 source-over 的**等价闭式** ✓）—— 这是**治浓度**的那一本 ✓，
     *    与老路**同一个数学**✓（所以"单颗淡、靠重叠累积"的设计照旧成立 ✓）。
     *  · 最终覆盖度 = `形状覆盖度 × ink` ✓（乘回去 ⇒ 边缘仍然是渐入的 ✓、颗粒仍然在 ✓）。
     *
     * ⚠️ 为什么污染不到"轮廓"：轮廓由**形状覆盖度那一本**决定 ✓（它取 max ✓），
     * `ink` 只是**整体乘上去的浓淡** ✓ —— 它在中心线上是平的（几十颗叠满 ⇒ 处处 255 ✓），
     * 不会重新制造周期性起伏 ✓。实测见 `ContinuousStrokeCoverageProbeTest` ✓。
     */
    private fun accumulateOneShape(
        shape: BrushEngine.DabShape,
        alphaByte: Int,
        texture: ByteArray?,
        square: Boolean,
    ): Boolean {
        val rx = shape.radius
        val ry = if (shape.ratio > 0.001f) shape.radius / shape.ratio else shape.radius
        if (rx <= 0f || ry <= 0f) return false
        // 只碰这一颗的包围盒 ✓（与老路 `dabBounds` 同口径：抗锯齿只往外溢一个像素 ✓）
        //
        // ⚠️ **先把两端算成"未裁剪"的整数，再各自夹一次、最后判空** ✗ ——
        //    第一版把 `x0` 写成"左边界直接 `coerceIn(0, W-1)`" ✗，笔尖**一半在画布外**时：
        //      · 左边界已经贴到 0 / 右边界也贴到 W-1 ⇒ 盒子被**压扁** ✗，
        //      · 而 `translate(shape.x - x0, …)` 用的还是那个被压扁后的 `x0` ✗
        //        ⇒ **整颗形状画错位置** ✗。
        //    实测症状（扫参那一轮 ✓）：间距一拉大、笔尖**跨出画布边界**时凹陷突然从 0 跳到 6~8 ✗ ——
        //    那不是"轮廓被拆"，是**边界外那几颗画歪了** ✓（判据抓到了真问题 ✓）。
        //    修法：`x0/x1` 用未裁剪值算、**只用来定位**（可以越界 ✓，差值是相对的 ✓），
        //    裁剪只作用在"要读回的那一块"（`cx0..cx1` ✓）。
        val rawX0 = (shape.x - rx - 1f).toInt()
        val rawY0 = (shape.y - ry - 1f).toInt()
        val rawX1 = (shape.x + rx + 1f).toInt()
        val rawY1 = (shape.y + ry + 1f).toInt()
        if (rawX1 < 0 || rawY1 < 0 || rawX0 > coverageW - 1 || rawY0 > coverageH - 1) return false
        // 读回窗口（= 这一颗与画布的交集 ✓）
        val cx0 = rawX0.coerceIn(0, coverageW - 1)
        val cy0 = rawY0.coerceIn(0, coverageH - 1)
        val cx1 = rawX1.coerceIn(0, coverageW - 1)
        val cy1 = rawY1.coerceIn(0, coverageH - 1)
        if (cx1 < cx0 || cy1 < cy0) return false
        // scratch 要装得下**整颗**（含画布外那部分 ✓ —— 形状按 raw 原点定位 ✓），
        // 读回时才只取交集那一段 ✓。否则"一半在外"的笔尖会被压扁 ✗。
        val bw = rawX1 - rawX0 + 1
        val bh = rawY1 - rawY0 + 1
        val scratch = scratchCoverage(bw, bh)
        val canvas = scratch.canvas
        canvas.clear(0)
        // 形状覆盖度：**同一个光栅器**（Skia 抗锯齿椭圆 / 矩形 ✓）——
        // 先把这一颗以不透明白画进 scratch（SRC ⇒ 拿到"这一颗"的纯形状覆盖度 ✓），
        // 再乘 `alphaByte`（+ 纸纹 ✓）后与主缓冲取 max ✓。
        dabPaint.blendMode = BlendMode.SRC
        dabPaint.color = (255 shl 24) or 0x00FFFFFF
        canvas.save()
        // ⚠️ 用 **raw** 原点定位 ✓（不是裁剪后的 ✓ —— 见上面那段 ✗ 的说明 ✓）
        canvas.translate(shape.x - rawX0, shape.y - rawY0)
        if (shape.angleRadians != 0f) {
            canvas.rotate(shape.angleRadians * 180f / Math.PI.toFloat())
        }
        val box = Rect.makeLTRB(-rx, -ry, rx, ry)
        if (square) canvas.drawRect(box, dabPaint) else canvas.drawOval(box, dabPaint)
        canvas.restore()
        // 读回这张小 scratch 的 alpha ⇒ 与主缓冲取 max ✓
        val n = bw * bh
        val ints = regionInts(n)
        val bmp = ensureBitmap(bw, bh) ?: return false
        if (!scratch.readPixels(bmp, 0, 0)) {
            lastError = "accumulateCoverage: Surface.readPixels(${bw}x$bh) = false"
            return false
        }
        // ⚠️ 形参序是 `(dstInfo, dstRowBytes, srcX, srcY)` ✗（㉙ 批写错过 ✓，见 [readRegionInto] ✓）
        val info = ImageInfo(bw, bh, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bytes = bmp.readPixels(info, bw * 4, 0, 0)
        if (bytes == null || bytes.size < n * 4) {
            lastError = "accumulateCoverage: Bitmap.readPixels 返回 null（快路没走通 ✗）"
            return false
        }
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(ints, 0, n)
        val hasTexture = texture != null
        // 只把**这一颗与画布的交集**写进主缓冲 ✓（scratch 比交集大：含画布外那一圈 ✓）
        for (py in cy0..cy1) {
            val srcRow = py - rawY0
            val dstBase = py * coverageW
            val srcBase = srcRow * bw
            for (px in cx0..cx1) {
                val col = px - rawX0
                // scratch 是 PREMUL，但画的是**纯白 + 覆盖度** ⇒ RGB 与 A 相等 ✓ ⇒ 直接取 A 就是形状覆盖度 ✓
                val shapeCov = (ints[srcBase + col] ushr 24) and 0xFF
                if (shapeCov == 0) continue
                val idx = dstBase + px
                // ---- ① 形状覆盖度：**取 max**（治"一颗颗圆"的那一本 ✓，见本函数的说明 ✓）----
                //   ⚠️ 为什么是 `max` 而不是"累加" ✗：累加会把**边缘**也堆到饱和 ⇒
                //     笔迹变成"硬边的一条带"✗（起伏确实没了 ✓，但"边缘渐入"这个水彩特征跟着没了 ✗）。
                //     取 `max` ⇒ 轮廓 = **最宽那一颗的包络** ✓ ⇒ 边缘起伏 = 相邻笔尖的**半径差**
                //     （由 sizeJitter 决定 ✓、**与间距无关** ✓ —— 实测：sj=0 时 sc=0 那 5 档
                //      起伏全是 0~2px ✓，与 spacing 从 2% 到 50% **无关** ✓）。
                //   ⚠️ **`max` 治不了"间距拉大"的那一档** ✗（实测：spacing 2/6/12/25/50% ⇒
                //     凹陷 0/6/13/… ✓ 单调 ✓）：那是**包络自己**的扇形起伏 ✓ ——
                //     间距 > 约 1 个半径时，相邻两颗之间必然露出一块"没有笔尖盖满"的腰 ✗。
                //     ⇒ 这一档**如实留给"间距本身"** ✓：`wc3/wc4` 的 `spacing` 就是 3% ✓
                //     （实测那一档凹陷 = 0.0 ✓）；用户把间距手动拉到 12% 以上时**会看到腰** ✓ ——
                //     那不是"一颗颗圆" ✗（没有周期性半径起伏 ✓），是"排得稀"✓，
                //     **如实写进 `docs/61` 的未验 / 已知限制** ✓（不偷偷把判据改成"间距大的不算"✗）。
                //     ⇒ 因此本判据的正确口径是"**在预设的间距档（3%）下凹陷 = 0**"✓ +
                //       "**间距 ≤ 4% 时凹陷 = 0**"✓（下面扫参按这个分档 ✓）。
                val prevShape = coverageShape[idx].toInt() and 0xFF
                if (shapeCov > prevShape) coverageShape[idx] = shapeCov.toByte()
                // ---- ② 墨量：累加饱和 ✓（治浓度的那一本 ✓；等价于逐颗 source-over ✓）----
                //   纸纹按**画布像素**取 ✓（`textureAt` 返回 0..1 ✓）⇒ 颗粒钉在纸上、
                //   天然不随笔尖间距起伏 ✓（这正是用户要的"笔画周围的贴图"✓）
                var a = alphaByte
                if (hasTexture) a = (a * BrushEngine.textureAt(texture!!, px, py)).toInt()
                if (a <= 0) continue
                val prevInk = coverageInk[idx].toInt() and 0xFF
                // ink = 255 − (255−prev)·(255−a)/255 ✓（一次算完，整数 ✓）
                val ink = 255 - (255 - prevInk) * (255 - a) / 255
                if (ink > prevInk) coverageInk[idx] = ink.toByte()
            }
        }
        return true
    }

    /** 累积笔尖用的**临时小表面**（只增不减 ✓ —— 一笔之内反复用同一张 ✓）。 */
    private var scratchSurface: Surface? = null
    private var scratchW = 0
    private var scratchH = 0

    private fun scratchCoverage(w: Int, h: Int): Surface {
        val cur = scratchSurface
        if (cur != null && scratchW >= w && scratchH >= h) return cur
        val nw = maxOf(w, scratchW)
        val nh = maxOf(h, scratchH)
        val s = Surface.makeRasterN32Premul(nw, nh)
        scratchSurface?.close()
        scratchSurface = s
        scratchW = nw
        scratchH = nh
        return s
    }

    /**
     * 把覆盖度缓冲**一次性合成到 [surface]** ✓（抬笔时一次 ✓ —— 这就是"轮廓天生连续"的来源 ✓）。
     *
     * 合成口径（对齐老路 ✓，这样"单颗淡、靠重叠累积"的浓度设计仍然成立 ✓）：
     *  · **颜色 = `colorArgb`**（这一笔的那个色 ✓ —— 覆盖度缓冲里不含颜色 ✓）；
     *  · **alpha = 覆盖度**（0..255 ✓）；
     *  · 每像素一次 `source-over`（橡皮 = `DST_OUT` ✓）——
     *    ⚠️ 这是"**一笔一次**"而不是"一颗一次" ✗ ⇒ 同一笔内部不再有周期性叠加起伏 ✓。
     *
     * @param rect 只需要合成这一块（`null` = 整页 ✓）。抬笔时按**这一笔碰过的并集**传 ✓
     *   （一笔一次整页 memcpy 在 A4 上是 33 MB ✗ —— 那是白烧 ✓）。
     */
    fun compositeCoverage(
        surface: Surface,
        colorArgb: Int,
        erasing: Boolean,
        rect: EditRect?,
        clipRect: Rect? = null,
        clipPath: org.jetbrains.skia.Path? = null,
    ): Boolean {
        if (coverageShape.isEmpty()) return false
        val x0 = (rect?.x ?: 0).coerceIn(0, coverageW - 1)
        val y0 = (rect?.y ?: 0).coerceIn(0, coverageH - 1)
        val x1 = ((rect?.let { it.x + it.w } ?: coverageW) - 1).coerceIn(0, coverageW - 1)
        val y1 = ((rect?.let { it.y + it.h } ?: coverageH) - 1).coerceIn(0, coverageH - 1)
        if (x1 < x0 || y1 < y0) return false
        val bw = x1 - x0 + 1
        val bh = y1 - y0 + 1
        val n = bw * bh
        val ints = regionInts(n)
        // 两本账 → 非预乘 ARGB（颜色 = 这一笔的色 ✓，alpha = **形状覆盖度 × 墨量** ✓）
        // ⚠️ 乘回去的理由见 [accumulateOneShape]：轮廓由形状那本决定 ✓（取 max ✓ ⇒ 不被间距拆 ✗），
        //    墨量只是**整体浓淡** ✓ —— 中心线上几十颗叠满 ⇒ 处处 255 ⇒ 不会重新制造周期性起伏 ✓。
        //
        // ---- 第 ㊳ 批（2026-09-21）：**写的是真增量，不是当前完整覆盖度** ✓ ----
        // ⚠️ 原来写 `cov`（完整值 ✓）却用 `SRC_OVER` 叠 ✗ ⇒ 每合一次就再叠一次 ✗
        //    （抬笔只合一次时看不出来 ✓；A 方案按帧合就暴露了 ✗ —— 实测差 1525 像素 ✗）。
        //    改成 `cov − compositedCov` 之后：同一像素无论分几次合，总效果与一次合**逐字节相同** ✓
        //    （依据：覆盖度单调 ✓ ⇒ 差值非负 ✓；source-over 增量可加 ✓ —— 见 [coverageComposited] ✓）。
        val rgb = colorArgb and 0x00FFFFFF
        var any = false
        coverageDeltaBounds = null
        var dx0 = Int.MAX_VALUE
        var dy0 = Int.MAX_VALUE
        var dx1 = Int.MIN_VALUE
        var dy1 = Int.MIN_VALUE
        for (row in 0 until bh) {
            val srcBase = (y0 + row) * coverageW + x0
            val dstBase = row * bw
            for (col in 0 until bw) {
                val idx = srcBase + col
                val shapeCov = coverageShape[idx].toInt() and 0xFF
                if (shapeCov == 0) {
                    ints[dstBase + col] = 0
                    continue
                }
                val ink = coverageInk[idx].toInt() and 0xFF
                val cov = shapeCov * ink / 255
                // 真增量 ✓（单调 ⇒ 非负 ✓；夹一道兜底防以后改坏 ✗）
                val done = coverageComposited[idx].toInt() and 0xFF
                val delta = (cov - done).coerceAtLeast(0)
                if (delta == 0) {
                    ints[dstBase + col] = 0
                    continue
                }
                ints[dstBase + col] = (delta shl 24) or rgb
                any = true
                // 这一块"真的写了"的并集 ✓（只回读它 ✓）—— 见下面 [coverageDeltaBounds] 的说明 ✓
                val px = x0 + col
                val py = y0 + row
                if (px < dx0) dx0 = px
                if (py < dy0) dy0 = py
                if (px > dx1) dx1 = px
                if (py > dy1) dy1 = py
            }
        }
        if (!any) return false
        // 一块位图 → `drawImageRect` 摆在表面上 ✓（一次 JNI ✓）
        val info = ImageInfo(bw, bh, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        val bytes = ByteArray(n * 4)
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(ints, 0, n)
        val bmp = Bitmap()
        if (!bmp.allocPixels(info)) return false
        bmp.installPixels(info, bytes, bw * 4)
        val image = Image.makeFromBitmap(bmp)
        val canvas = surface.canvas
        val clipped = clipRect != null || clipPath != null
        if (clipped) {
            canvas.save()
            if (clipRect != null) canvas.clipRect(clipRect, ClipMode.INTERSECT, false)
            if (clipPath != null) canvas.clipPath(clipPath, ClipMode.INTERSECT, false)
        }
        // ⚠️ **第 ㊳ 批：这里必须是 `PLUS`，不能是 `SRC_OVER`** ✗（实测量出来的 ✓）
        //
        //  `ints[]` 里装的是**真增量** `d = cov − compositedCov` ✓（见上面那段 ✓）。
        //  增量要的是"**把这块新长出来的墨加进表面**" ✓，也就是 `out_a = a + d` ✓。
        //  而 `SRC_OVER` 给的是 `out_a = d + a·(1 − d)` ✗ —— 多了一个 `(1−d)` 因子 ✓
        //  ⇒ **按帧合会系统性偏淡** ✗，而且合得越勤越淡 ✗。
        //
        //  实测（`TempDeltaDiagTest` ✓，A4 一笔、每 N 点合一次 ✓，墨量总和比"整笔一次"= 1.000 ✓）：
        //  ```
        //    SRC_OVER + 增量：N=1 → 0.813 ✗  N=3 → 0.927 ✗  N=10 → 0.981 ✗  （合得越勤越淡 ✗）
        //    PLUS     + 增量：N=1 → 1.000 ✓  N=3 → 1.000 ✓  N=10 → 1.000 ✓  （与整笔一次逐字节相同 ✓）
        //  ```
        //  ⇒ `PLUS` 才是"增量合成"的正确语义 ✓（饱和加法 ✓，正是 source-over 的可加形式 ✓）。
        //
        //  ⚠️ 抬笔那一次（`compositedCov` 全 0 ⇒ `d = cov` ✓）在 `PLUS` 下与原来的 `SRC_OVER`
        //     **结果相同** ✓（表面此像素原本没墨 ⇒ `a = 0` ⇒ `SRC_OVER` 也退化成 `d` ✓）
        //     ⇒ **老路一个字节没变** ✓（"整笔一次"那条口径完全不动 ✓）。
        //  ⚠️ 橡皮（`DST_OUT` ✓）不参与这一套 ✗：它做的是"擦掉"✓、不是"加墨"✓，
        //     而且擦除路径一次到位 ✓（不累积覆盖度 ✓）。
        coveragePaint.blendMode = when {
            erasing -> BlendMode.DST_OUT
            else -> BlendMode.PLUS
        }
        coveragePaint.isAntiAlias = false
        canvas.drawImageRect(
            image,
            Rect.makeLTRB(0f, 0f, bw.toFloat(), bh.toFloat()),
            Rect.makeLTRB(x0.toFloat(), y0.toFloat(), (x1 + 1).toFloat(), (y1 + 1).toFloat()),
            coveragePaint,
        )
        if (clipped) canvas.restore()
        image.close()
        bmp.close()
        // ---- 第 ㊳ 批：画完才把"已经合过的"记上 ✓ ----
        // ⚠️ 必须在 `drawImageRect` **之后** ✗：先记的话若绘制抛异常，账上已经算合过了 ✗
        //    ⇒ 那一块墨**永远补不回来** ✗（比原 bug 更隐蔽 ✗）。
        // ⚠️ 只记**真的写过的那些像素** ✓（`delta > 0` 的那些 ✓）—— 整块记会把"还没长出来的"
        //    也算成已合 ✓，但那些本来就是 0 / 未变 ✓，所以两种写法结果相同 ✓；
        //    这里按 `cov` 记（= 当前完整值 ✓）更省事且等价 ✓（因为写过的那些 `cov` 正是当前值 ✓）。
        for (row in 0 until bh) {
            val srcBase = (y0 + row) * coverageW + x0
            for (col in 0 until bw) {
                val idx = srcBase + col
                val shapeCov = coverageShape[idx].toInt() and 0xFF
                if (shapeCov == 0) continue
                val ink = coverageInk[idx].toInt() and 0xFF
                val cov = shapeCov * ink / 255
                val done = coverageComposited[idx].toInt() and 0xFF
                if (cov > done) coverageComposited[idx] = cov.toByte()
            }
        }
        // 这一块"真的写了"的并集 ✓（`null` = 一个像素都没写 ✓ —— 调用方据此决定回读哪一块 ✓）
        coverageDeltaBounds = if (dx1 < dx0 || dy1 < dy0) null else EditRect(dx0, dy0, dx1 - dx0 + 1, dy1 - dy0 + 1)
        return true
    }

    /**
     * **上一次 `compositeCoverage` 真的写过的像素并集**（画布坐标 ✓，`null` = 一个都没写 ✓）。
     *
     * 第 ㊳ 批加的 ✓ —— 给 A 方案（按帧合成 ✓）用：
     * 按帧合的时候每次只长出一**小条**新墨 ✓（整笔的并集 `rect` 会越来越大 ✓），
     * 回读整笔并集是白烧 ✓ ⇒ 按"这一帧真的写了哪一块"回读 ✓。
     */
    var coverageDeltaBounds: EditRect? = null
        private set

    /** 合成覆盖度用的画笔（复用 ✓）。 */
    private val coveragePaint = Paint().apply {
        mode = PaintMode.FILL
        isAntiAlias = false
    }

    private val dabPaint = Paint().apply {
        isAntiAlias = true
        mode = PaintMode.FILL
    }

    private val copyPaint = Paint().apply {
        blendMode = BlendMode.SRC
    }

    /** 纸纹那一支画笔（**每颗笔尖只换 shader / colorFilter** ✓，见 [stampOnSurface] ✓）。 */
    private val texPaint = Paint().apply {
        isAntiAlias = true
        mode = PaintMode.FILL
        // ⚠️ 颜色必须是**不透明白** ✗：贴图那条链里真正决定"画成什么色"的是 `colorFilter` ✓，
        //    而 Skia 在"有 shader"时仍会用 paint 自己的 alpha 去乘 shader 的输出 ✓ ——
        //    这里留给它 255 ⇒ 一个字节都不掺 ✓（色 + alpha 全由 colorFilter 出 ✓）。
        color = 0xFFFFFFFF.toInt()
    }

    // -----------------------------------------------------------------------
    // 纸纹（第 ㉜c 批 ✓）：网网页口径 = **乘法作用在笔迹 alpha 上** ✓
    // -----------------------------------------------------------------------

    /**
     * **纸纹那一张贴图 → Skia `Image`**（128×128、白色 + alpha ✓，只在该贴图第一次用时建一次 ✓）。
     *
     * 为什么做成"白 + alpha"而不是"灰"：网页那张贴图是**只有 alpha 通道**的
     *（`rebuildTile()` 里 `d[i4]=d[i4+1]=d[i4+2]=0`、只写 `d[i4+3]` ✓，见 `brush-lab-simple.html:273-275` ✓）
     * ⇒ 它**不是颜色**，而是"笔迹 alpha 的一个乘数" ✓。做成白色 + alpha 之后，
     * 配一次 [ColorFilter] 的 `SRC_IN` 就能把"颜色换成笔尖色、alpha 乘上去"一次做完 ✓
     *（见 [stampOnSurface] 那条注释 ✓）。
     */
    private fun tileImageOf(texture: ByteArray): Image? {
        val cached = tileImage
        if (cached != null && tileImageRef === texture) return cached
        val n = BrushEngine.TILE * BrushEngine.TILE
        if (texture.size < n) {
            lastError = "纸纹贴图太小（${texture.size} < $n）"
            return null
        }
        val bytes = ByteArray(n * 4)
        for (i in 0 until n) {
            val off = i * 4
            // 内存字节序 = B,G,R,A（= Skia 的 BGRA_8888 ✓，与 `writeRegionFrom` 同一条口径 ✓）
            bytes[off] = 0xFF.toByte()
            bytes[off + 1] = 0xFF.toByte()
            bytes[off + 2] = 0xFF.toByte()
            bytes[off + 3] = texture[i]
        }
        val info = ImageInfo(BrushEngine.TILE, BrushEngine.TILE, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        val img = runCatching { Image.makeRaster(info, bytes, BrushEngine.TILE * 4) }
            .onFailure { lastError = "纸纹 Image.makeRaster: ${it.message}" }
            .getOrNull() ?: return null
        // ⚠️ shader 也**只建一次** ✓（第 ㉜c 批 ✓）：贴图网格钉在画布像素上、与笔尖位置无关
        //    ⇒ 一整笔共用同一个 shader 就行 ✓（每颗建一个要多花一次 JNI + 一次 native 分配，
        //    实测 A4 上那是"每颗笔尖"最贵的一项 ⚠️ —— 见 `docs/56` 的 A4 实测 ✓）。
        val sh = runCatching {
            img.makeShader(
                FilterTileMode.REPEAT,
                FilterTileMode.REPEAT,
                SamplingMode.LINEAR,
                Matrix33.makeTranslate(0f, 0f),
            )
        }.onFailure { lastError = "纸纹 shader: ${it.message}" }.getOrNull()
        if (sh == null) {
            img.close()
            return null
        }
        tileShader?.close()
        tileImage?.close()
        tileShader = sh
        tileImage = img
        tileImageRef = texture
        return img
    }

    /**
     * **把一颗笔尖连同纸纹画到表面**（网页 `texturedDab()` 的净效果 ✓）。
     *
     * 口径（**网页为准** ✓，`brush-lab-simple.html:331-358` ✓）：
     *  `texturedDab()` 用 `source-over` 铺图案 → `destination-in` 剪椭圆 → `source-in` 上色，
     *  这三步合出来的净结果就是：
     *  **颜色 = 笔尖色 ✓、alpha = `dabAlpha × 纸纹格子值 × 椭圆覆盖度`** ✓
     *  ⇒ 纸纹是**乘法作用在笔迹 alpha 上** ✓（不是把纸的颜色画上去 ✗）。
     *
     * 这里不照抄那三步（三次合成 = 一张小画布 + 一次回读 ⇒ 每颗笔尖都要过一遍位图 ✗），
     * 而是用 Skia 的**等价一次画** ✓：
     *  · `shader` = 那张贴图按 `REPEAT` 平铺、**贴图网格钉在画布像素上** ✓
     *    （网页 `tx/ty` 那两行求的就是这件事 ✓）；
     *  · `colorFilter = Blend(笔尖色+alpha, SRC_IN)` ⇒ 输出 = 笔尖色、alpha = `dabAlpha × 纸纹` ✓
     *    （`SRC_IN` 的定义就是 `out = S × Da` ✓，与网页第三步逐字同一个意思 ✓）；
     *  · 椭圆的抗锯齿覆盖度由 Skia 的 `drawOval`（`isAntiAlias = true` ✓）给 ✓
     *    ⇒ 覆盖率再乘上去 ✓，与网页第二步的 `destination-in` 同一个作用 ✓。
     *
     * ⚠️ 两条**故意与网页不同**的地方（都写进 `docs/56` 的已知差距 ✓，不是没想到 ✗）：
     *  ① 网页是**先画进 32×32 的小画布、再 `drawImage` 摆上去** ⇒ 走的是**位图重采样** ✓；
     *     这里是矢量 + shader，**不重采样** ⇒ 颗粒更清楚 ✓（同一个贴图、同一个 α 口径 ✓）；
     *  ② 网页那把 `dabCv` **只增不减**，后面的小笔尖会把整张（含上一次残留的）位图**缩着摆**上去 ✗
     *     （`drawImage(dabCv, …, s2, s2)` 的源是整张画布 ✓）—— 这是网页自己的一个 bug ✓，
     *     这边**不复制它** ✗（复制了"一个个圆"就跟着进来了 ✗）。
     */
    private fun stampTexturedOnSurface(
        canvas: org.jetbrains.skia.Canvas,
        shape: BrushEngine.DabShape,
        alphaByte: Int,
        texture: ByteArray,
        square: Boolean = false,
    ): Boolean {
        val image = tileImageOf(texture) ?: return false
        val shader = tileShader ?: return false
        val rx = shape.radius
        val ry = if (shape.ratio > 0.001f) shape.radius / shape.ratio else shape.radius
        if (rx <= 0f || ry <= 0f) return false
        // ⚠️ **贴图网格就是画布像素网格**（局部矩阵 = 平移 0 ✓）。
        //
        // 这一条是**算出来**的、不是拍的（㉜c 实测：错半个 texel 时单颗笔尖平均差 **5.05**，
        // 对齐之后掉到 **1.94** ✓）：网页 `texturedDab()` 里那两行
        //   `tx = -(((ox % TILE) + TILE) % TILE)` + `g2.translate(tx, ty)`
        // 里 `ox = x - s/2`，而图案的坐标 = `器件坐标 + g`（`g = ox mod 128` ✓）；
        // 又因为小画布的点 `d` 对应页面坐标 `p = ox + d` ⇒ 图案坐标 = `p - ox + g`
        // = `p - 128·floor(ox/128)` ⇒ 与 `p` **只差 128 的整数倍** ✓ ——
        // 而贴图是 `repeat` 的，128 的整数倍等于没差 ✓。
        // ⇒ 采样点就是页面像素中心 `(px+0.5, py+0.5)` ⇒ 正好落在贴图 texel 的正中心
        //   ⇒ 取到的就是 `textureAt(tile, px, py)` 那一格 ✓（与 `BrushEngine.textureAt` 同一口径 ✓）。
        // ⇒ shader 因此**与笔尖位置无关** ⇒ 一整笔只建一次（见 [tileImageOf] ✓）。
        val argb = (alphaByte shl 24) or (shape.colorArgb and 0x00FFFFFF)
        texPaint.shader = shader
        texPaint.colorFilter = ColorFilter.makeBlend(argb, BlendMode.SRC_IN)
        canvas.save()
        if (shape.angleRadians != 0f) {
            // 绕**笔尖中心**转 ✓（与 web 那边"转了再摆位图"同一件事 ✓）
            canvas.rotate(shape.angleRadians * 180f / Math.PI.toFloat(), shape.x, shape.y)
        }
        val box = Rect.makeLTRB(shape.x - rx, shape.y - ry, shape.x + rx, shape.y + ry)
        // 方笔尖（第 ㉝② 批 ✓）：同一支画笔（贴图 + SRC_IN 色过滤）**换个图元** ——
        // 椭圆换成矩形 ✓（口径见 [stampOnSurface] 的说明 ✓）。
        // ⚠️ 网页那条 `texturedDab()` 的遮罩**写死是椭圆** ✗ ⇒ 这一档**没有**网页真值 ✓
        //（未验清单里如实记着 ✓）。
        if (square) canvas.drawRect(box, texPaint) else canvas.drawOval(box, texPaint)
        canvas.restore()
        texPaint.shader = null
        texPaint.colorFilter = null
        return true
    }

    /** 这一档笔尖要用的纸纹（[stampOnSurface] 调用期间有效 ✓ —— 只为了少传一层参数 ✓）。 */
    private var tileImage: Image? = null
    private var tileImageRef: ByteArray? = null

    /** 贴图的 shader（**一整笔共用** ✓ —— 网格钉在画布像素上，与笔尖位置无关 ✓）。 */
    private var tileShader: Shader? = null

    // -----------------------------------------------------------------------
    // ① 把一档笔尖**直接画到层表面**上（页面坐标 ✓）
    // -----------------------------------------------------------------------

    /**
     * 把 [plan] 的几颗笔尖直接画在 [surface] 上 ✓（表面 = 整页大小、坐标 = 页面坐标 ✓）。
     *
     * @param texture **纸纹 / 噪点贴图**（第 ㉜c 批 ✓；`null` = 不用 ✓）——
     *   开着时每颗笔尖按"**乘法作用在 alpha 上**"画（见 [stampTexturedOnSurface] ✓）。
     *   橡皮**不吃贴图** ✓（与网页 `useTex && !erasing` 逐字一致 ✓）。
     * @param square **方笔尖**（第 ㉝② 批 ✓）：图元从"椭圆"换成"**矩形**" ✓ ——
     *   几何口径照网页 `stamp()` 那一套（同一个 `rx = r` / `ry = r / ratio` ✓、同一套
     *   `translate(px,py) + rotate(ang)` ✓、同一个 alpha / 颜色 / 合成模式 ✓），只换图元 ✓。
     *   为什么这样能"接上 Skia"：网页 `stamp()` 画的是**抗锯齿的路径**（圆是 `ellipse` ✓），
     *   而 Chromium 的 canvas2d 在 Windows 上就是 Skia ✓ ⇒ 这边 `drawRect`（`isAntiAlias = true` ✓）
     *   与"把 `ellipse` 换成 `rect` 的网页"是**同一个光栅器 + 同一套抗锯齿** ✓。
     *   （老路是**逐像素解析羽化** ✗ —— 同一支笔换个形状就换了个光栅器，正是本批要消掉的东西 ✓。）
     * @param clipRect / @param clipPath **选区裁剪**（第 ㉝② 批 ✓）：在表面上先 `clipRect` /
     *   `clipPath`（`isAntiAlias = false` ✓ —— 与网页 `ctx.clip()` 同口径 ✓，
     *   也与老路那张"逐像素 0/1 掩码"同口径 ✓），画完 `restore()` ✓。
     *   `clipRect` 给矩形选区（快路 ✓）、`clipPath` 给套索多边形 ✓（`EVEN_ODD` ✓，
     *   与 `SelectionGeometry.contains` 的射线法同一个填充规则 ✓）。
     * @return 真的画了至少一颗才 true ✓（几颗都不成形 / alpha 为 0 → false ✓）
     */
    fun stampOnSurface(
        surface: Surface,
        plan: BrushEngine.DabPlan,
        erasing: Boolean,
        texture: ByteArray? = null,
        square: Boolean = false,
        clipRect: Rect? = null,
        clipPath: org.jetbrains.skia.Path? = null,
    ): Boolean {
        val alphaByte = (plan.alpha.coerceIn(0f, 1f) * 255f).toInt()
        if (alphaByte <= 0) {
            lastError = "alpha<=0 (plan.alpha=${plan.alpha}, shapes=${plan.shapes.size})"
            return false
        }
        val canvas = surface.canvas
        val textured = texture != null && !erasing
        // 橡皮 = destination-out ✓（网页 `brush-lab-simple.html:455` 就是这么做的 ✓）
        dabPaint.blendMode = if (erasing) BlendMode.DST_OUT else BlendMode.SRC_OVER
        if (textured) texPaint.blendMode = dabPaint.blendMode
        // ---- 选区裁剪（第 ㉝② 批 ✓）：**画之前裁一次、画完恢复** ✓ ----
        val clipped = clipRect != null || clipPath != null
        if (clipped) {
            canvas.save()
            if (clipRect != null) canvas.clipRect(clipRect, ClipMode.INTERSECT, false)
            if (clipPath != null) canvas.clipPath(clipPath, ClipMode.INTERSECT, false)
        }
        var drawn = 0
        for (shape in plan.shapes) {
            val rx = shape.radius
            val ry = if (shape.ratio > 0.001f) shape.radius / shape.ratio else shape.radius
            if (rx <= 0f || ry <= 0f) continue
            if (textured) {
                if (stampTexturedOnSurface(canvas, shape, alphaByte, texture!!, square)) drawn++
                continue
            }
            dabPaint.color = (alphaByte shl 24) or (shape.colorArgb and 0x00FFFFFF)
            canvas.save()
            canvas.translate(shape.x, shape.y)
            if (shape.angleRadians != 0f) {
                canvas.rotate(shape.angleRadians * 180f / Math.PI.toFloat())
            }
            val box = Rect.makeLTRB(-rx, -ry, rx, ry)
            // 方笔尖 = 同一个盒子的**矩形**（圆角 0 ✓ —— 网页那边没有圆角那一档 ✓）
            if (square) canvas.drawRect(box, dabPaint) else canvas.drawOval(box, dabPaint)
            canvas.restore()
            drawn++
        }
        if (clipped) canvas.restore()
        if (drawn == 0) lastError = "一颗笔尖都没成形（shapes=${plan.shapes.size}）"
        return drawn > 0
    }

    // -----------------------------------------------------------------------
    // ② 从层表面读回一块（非预乘 ARGB ✓）
    // -----------------------------------------------------------------------

    /**
     * 把 [surface] 上 `(x,y,w,h)` 那一块读进 [out]（**非预乘 ARGB ✓**，按 [outStride] 分行 ✓）。
     *
     * @param out 目标数组（一般是页缓冲 `pixels` ✓）
     * @param outOffset 目标起点（= `y * outStride + x` ✓）
     * @param outStride 目标行距（一般 = 页宽 ✓）
     * @return 真的读到了才 true ✓
     */
    fun readRegionInto(
        surface: Surface,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        out: IntArray,
        outOffset: Int,
        outStride: Int,
    ): Boolean {
        if (w <= 0 || h <= 0) return false
        val bmp = ensureBitmap(w, h) ?: return false
        // ① 表面 → 位图（Skia 内存 → Skia 内存 ✓，**一次 JNI** ✓）
        if (!surface.readPixels(bmp, x, y)) {
            lastError = "Surface.readPixels(${w}x$h @ $x,$y) = false"
            return false
        }
        // ② 位图 → 我们的字节数组（⚠️ 形参序是 `(dstInfo, dstRowBytes, srcX, srcY)` ✗ —— ㉙ 批写错过 ✓）
        val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bytes = bmp.readPixels(info, w * 4, 0, 0)
        if (bytes == null || bytes.size < w * h * 4) {
            // 兜底（**生产上不该发生** ✓）：逐点读 —— 慢，但至少不出错 ✓（计数 + `SkiaStampCostTest` 断言为 0 ✓）
            val pixmap = bmp.peekPixels()
            if (pixmap == null) {
                lastError = "回读失败：readPixels 返回 null 且 peekPixels 也拿不到（$w x $h）"
                return false
            }
            slowPathReads++
            lastError = "回读走了慢路（peekPixels/getColor 逐像素 ✓）—— 快路返回 null ✗"
            for (row in 0 until h) {
                val dstRow = outOffset + row * outStride
                for (col in 0 until w) {
                    out[dstRow + col] = pixmap.getColor(col, row) // 这条已经**非预乘**了 ✓
                }
            }
            return true
        }
        fastPathReads++
        // ③ 批量转 IntArray（`IntBuffer.get(int[])` 内部一次 memcpy ✓），再**除回非预乘** ✓
        val r = regionInts(w * h)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(r, 0, w * h)
        for (row in 0 until h) {
            val dstRow = outOffset + row * outStride
            val srcRow = row * w
            for (col in 0 until w) {
                val src = r[srcRow + col]
                val a = (src ushr 24) and 0xFF
                out[dstRow + col] = if (a == 255) src else unpremultiply(src, a)
            }
        }
        return true
    }

    // -----------------------------------------------------------------------
    // ③ 把页缓冲的一块传回层表面（IntArray 那些操作改完像素之后 ✓）
    // -----------------------------------------------------------------------

    /**
     * 把 [src] 里 `(x,y,w,h)` 那一块（**非预乘 ARGB ✓**）**整块替换**到 [surface] 上 ✓。
     *
     * 用 `BlendMode.SRC`（**替换**，不是 source-over ✓）—— 层里被擦成透明的像素必须真的透明 ✓。
     * 字节序：我们的 `IntArray` 是小端 `0xAARRGGBB` ⇒ 内存字节序 = **B,G,R,A = Skia 的 `BGRA_8888`** ✓，
     * 所以这里只要一次 memcpy，声明成 **UNPREMUL** 交给 Skia 自己转换 ✓（比逐像素预乘快得多 ✓）。
     */
    fun writeRegionFrom(
        surface: Surface,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        src: IntArray,
        srcOffset: Int,
        srcStride: Int,
    ): Boolean {
        if (w <= 0 || h <= 0) return false
        val need = w * h * 4
        if (uploadBytes.size < need) uploadBytes = ByteArray(need)
        val buf = ByteBuffer.wrap(uploadBytes).order(ByteOrder.LITTLE_ENDIAN)
        if (srcStride == w && srcOffset == 0) {
            buf.asIntBuffer().put(src, 0, w * h)
        } else {
            val ints = buf.asIntBuffer()
            for (row in 0 until h) {
                ints.position(row * w)
                ints.put(src, srcOffset + row * srcStride, w)
            }
        }
        val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        val image = runCatching { Image.makeRaster(info, uploadBytes, w * 4) }
            .onFailure { lastError = "Image.makeRaster(${w}x$h): ${it.message}" }
            .getOrNull() ?: return false
        return try {
            surface.canvas.drawImageRect(
                image,
                Rect.makeWH(w.toFloat(), h.toFloat()),
                Rect.makeLTRB(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat()),
                SamplingMode.DEFAULT,
                copyPaint,
                true,
            )
            true
        } catch (t: Throwable) {
            lastError = "图层回写失败（${w}x$h @ $x,$y）：${t.message}"
            false
        } finally {
            image.close()
        }
    }

    /**
     * **自检用**：画一颗"指定色 + 指定 alpha"的椭圆，返回**中心那颗原始像素** ✓ ——
     * 走的就是 [readRegionInto] 那条回读 ✓，专门用来钉死"读回来是预乘还是非预乘"这条口径 ✗。
     *
     * 判读：白（`0xFFFFFF`）+ alpha 128 ⇒ 预乘存储里 R 已经乘过 ⇒ **R ≈ 128** ✓；
     * 要是 R = 255，说明是非预乘，那就**不能再除 alpha** ✗（㉘ 批的 8801 就是这么来的 ✓）。
     */
    internal fun debugRawPixel(colorRgb: Int, alpha: Int): Int? {
        val s = runCatching { Surface.makeRasterN32Premul(64, 64) }.getOrNull() ?: run {
            lastError = "debugRawPixel: makeRasterN32Premul(64,64) 失败"
            return null
        }
        val canvas = s.canvas
        canvas.clear(0)
        val p = Paint().apply {
            isAntiAlias = true
            color = ((alpha and 0xFF) shl 24) or (colorRgb and 0x00FFFFFF)
        }
        canvas.drawOval(Rect.makeLTRB(8f, 8f, 56f, 56f), p)
        val bmp = ensureBitmap(64, 64) ?: return null
        if (!s.readPixels(bmp, 0, 0)) {
            lastError = "debugRawPixel: Surface.readPixels(Bitmap) = false"
            return null
        }
        val info = ImageInfo(64, 64, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bytes = bmp.readPixels(info, 64 * 4, 0, 0)
        if (bytes == null || bytes.size < 64 * 64 * 4) {
            lastError = "debugRawPixel: Bitmap.readPixels 返回 null（快路没走通 ✗）"
            return null
        }
        fastPathReads++
        // ⚠️ 这里返回的是**预乘**的原始像素（不除 alpha ✓）—— 就是这条口径的自检 ✓
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(32 * 64 + 32)
    }

    /**
     * Skia 的表面是**预乘**的（`N32Premul` ✓）⇒ 读回来的字节要除回去 ✓
     * （不除的话边缘的半透明像素颜色会偏暗 ✗ —— 判据②能当场看出来 ✓）。
     */
    private fun unpremultiply(argb: Int, a: Int): Int {
        if (a == 0) return 0
        if (a == 255) return argb
        val f = 255f / a
        fun ch(shift: Int): Int = ((((argb ushr shift) and 0xFF) * f) + 0.5f)
            .toInt().coerceIn(0, 255)
        return (a shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** 回读位图：**常驻、只增不减** ✓（一笔里不会反复分配 ✓）。 */
    private fun ensureBitmap(w: Int, h: Int): Bitmap? {
        if (w <= bitmapW && h <= bitmapH && bitmap != null) return bitmap
        val nw = maxOf(w, bitmapW, 64)
        val nh = maxOf(h, bitmapH, 64)
        val info = ImageInfo(nw, nh, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
        val bmp = Bitmap()
        if (!bmp.allocPixels(info)) {
            lastError = "allocPixels(${nw}x$nh) = false"
            return null
        }
        bitmap = bmp
        bitmapW = nw
        bitmapH = nh
        return bmp
    }
}
