package com.kallan.naistudio.models

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **笔刷引擎（走位那一半 + 纸纹 / 混色 / keepOpacity 的落点）** —— 第二批的行为钉子 ✓。
 *
 * [BrushEngineTest] 钉的是**纯函数**（散布 / 抖动 / 计数 / 采样 ✓）；
 * 这一份走**真会话**（[ImageEditSession] ✓），因为它要证明的是
 * "这些参数**真的画到像素上**了" ✓：
 *
 *  1. **Spacing 1 / 10 / 20 / 50 / 100 / 200 % 各画一条横线 ⇒ 沿中线不许有 ≥2px 的洞** ✓
 *     （用户 2026-09-20 那条钉子：「画的线是一排点」✗ ⇒ 护栏 0.4 ✓，见 `DAB_MAX_STEP_RATIO` ✓）；
 *  2. **画用纸**：单颗笔尖时像素 alpha **不均匀** ✓；关掉 ⇒ 全均匀 ✓；
 *  3. **保持不透明度**（`keepOpacity`）：同一处反复涂，alpha **不超过单笔浓度** ✓；
 *     关掉时同一处反复涂**会叠深**（这是对照，说明上一条不是"没生效"✓）；
 *  4. **`integerPosition`**：笔尖中心真的对齐到像素边界 ✓；
 *  5. **模糊笔压**：`blurPressure = 100` 时强度随压力变 ✓，`= 0`（恒定 ✓）时两档压力结果**逐位一致** ✓；
 *  6. **采样硬上限 64px**：混色只读 dab 附近那一小块 ✓（**绝不全画布读** ✗）；
 *  7. **回归**：默认参数（`spacing = 10%` + 鼠标恒压）下步距**还是 `0.2 × 半径`** ✓ ——
 *     也就是"护栏从 0.9 收到 0.4"**没有**改默认笔迹密度 ✓（改的只有 spacing > 20% 与扁笔尖那两档 ✓）。
 */
class BrushStrokeIntegrationTest {

    private fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

    private fun grayOf(argb: Int): Int = (argb ushr 16) and 0xFF

    /** 沿中线那一带扫过去：**最长的一段连续没有墨的列数** ✓（`>= 2` 就是肉眼可见的断点 ✓）。 */
    private fun longestGapRun(
        pixels: IntArray,
        width: Int,
        height: Int,
        centerY: Int,
        from: Int,
        to: Int,
        band: Int = 4,
    ): Int {
        var longest = 0
        var run = 0
        for (x in from..to) {
            var covered = false
            for (y in maxOf(0, centerY - band)..minOf(height - 1, centerY + band)) {
                if (alphaOf(pixels[y * width + x]) > 0) {
                    covered = true
                    break
                }
            }
            if (covered) run = 0 else { run++; if (run > longest) longest = run }
        }
        return longest
    }

    private fun columnHasInk(pixels: IntArray, width: Int, height: Int, x: Int, centerY: Int, band: Int = 4): Boolean {
        if (x < 0 || x >= width) return false
        for (y in maxOf(0, centerY - band)..minOf(height - 1, centerY + band)) {
            if (alphaOf(pixels[y * width + x]) > 0) return true
        }
        return false
    }

    /** 画一条恒压横线（模拟数位板：每个 move 事件喂一次位置 + 压力 ✓）。 */
    private fun drawLine(
        width: Int,
        height: Int,
        centerY: Int,
        brushPixels: Int,
        pressure: Float,
        brush: BrushSpec,
        fromX: Float = 12f,
        toX: Float = 208f,
        stepPx: Float = 2f,
    ): ImageEditSession {
        val session = ImageEditSession(width, height, IntArray(width * height))
        val spec = StrokeSpec(
            brushPixels = brushPixels,
            color = 0xFF000000.toInt(),
            brush = brush,
        )
        session.beginStroke(fromX, centerY.toFloat(), spec, pressure = pressure)
        var x = fromX + stepPx
        while (x <= toX) {
            session.strokeTo(x, centerY.toFloat(), pressure = pressure)
            x += stepPx
        }
        session.endStroke()
        return session
    }

    // ------------------------------------------------------------------
    // ① Spacing 全线扫一遍：**一格洞都不许有** ✓（照网页那条钉子 ✓）
    // ------------------------------------------------------------------

    @Test
    fun every_spacing_percent_still_leaves_no_hole_in_the_line() {
        val width = 220
        val height = 60
        val centerY = 30
        // 间距扫那 6 档（网页 UI 的范围是 1..200 ✓，引擎还允许到 1000 ✓）
        for (spacing in listOf(1f, 10f, 20f, 50f, 100f, 200f)) {
            val session = drawLine(
                width = width,
                height = height,
                centerY = centerY,
                brushPixels = 10,             // 基础半径 5 px（小半径 ✓）
                pressure = 0.15f,             // 轻按（最容易被步距坑到的那一档 ✓）
                brush = BrushSpec(spacing = spacing),
            )
            val gap = longestGapRun(session.pixels, width, height, centerY, 13, 207)
            assertTrue(
                "Spacing = $spacing% 时断了 $gap 列 ✗ —— `step ≤ 0.4 × 行进方向半径` 那条护栏没夹住",
                gap <= 1,
            )
            val uncovered = (13..207).filterNot { columnHasInk(session.pixels, width, height, it, centerY) }
            assertTrue(
                "Spacing = $spacing% 有 ${uncovered.size} 整列是空的（头几列：${uncovered.take(6)}）✗",
                uncovered.isEmpty(),
            )
        }
    }

    // ------------------------------------------------------------------
    // ② 画用纸：逐像素相乘 ⇒ alpha 不均匀 ✓
    // ------------------------------------------------------------------

    @Test
    fun paper_texture_makes_the_dab_alpha_uneven() {
        /** 只落**一颗**笔尖（一笔的起点 ✓）—— 这样量到的是纯纸纹，没有重叠累积的干扰 ✓。 */
        fun singleDab(brush: BrushSpec): IntArray {
            val width = 80
            val height = 80
            val session = ImageEditSession(width, height, IntArray(width * height))
            session.beginStroke(40f, 40f, StrokeSpec(brushPixels = 20, color = 0xFF000000.toInt(), brush = brush))
            session.endStroke()
            return session.pixels
        }

        val withPaper = singleDab(BrushSpec(paperOn = true, paperStrength = 100f))
        val without = singleDab(BrushSpec())

        val paperAlphas = withPaper.filter { alphaOf(it) > 0 }.map { alphaOf(it) }
        val plainAlphas = without.filter { alphaOf(it) > 0 }.map { alphaOf(it) }
        assertTrue("画用纸开着时这颗笔尖必须有像素（实际 ${paperAlphas.size}）", paperAlphas.size > 100)
        assertTrue(
            "`paperOn = true, strength = 100` 时同一笔的像素 alpha **必须不均匀** ✗" +
                "（实际只有 ${paperAlphas.distinct().size} 种 alpha：${paperAlphas.distinct().sorted().take(8)}…）",
            paperAlphas.distinct().size > 20,
        )
        // ⚠️ 第 ㉔ 批（用户：「**画出来的线条锯齿大**」✗ ⇒ **抗锯齿** ✓）之后，
        //    "硬边 ⇒ 全 255"这条老断言**不再成立** ✗ —— 边缘那一圈现在按"像素中心到轮廓的距离"
        //    拿 0.5..1 的覆盖度 ✓（这正是抗锯齿的定义 ✓，见 `ImageEditOps.stampDab` ✓）。
        //    所以这里换成**同一件事的更准的两条判据**（一条都没放松 ✗）：
        //     ① 笔尖**内部**必须还是实心的 255 ✓（实心面积 = 半径 9 / 10 的圆 ≈ 81% ⇒ 下界取 70% ✓）；
        //     ② 边缘必须真的出现**中间 alpha** ✓（第 ㉔ 批新增的那条保证 ✓）。
        val plainOpaque = plainAlphas.count { it == 255 }
        assertTrue(
            "`paperOn = false` 时笔尖**内部**必须还是实心的（255 ✓）：" +
                "实际 $plainOpaque / ${plainAlphas.size}",
            plainAlphas.isNotEmpty() && plainOpaque >= plainAlphas.size * 7 / 10,
        )
        assertTrue(
            "第 ㉔ 批：**边缘必须有中间 alpha**（抗锯齿 ✓，不再是 0/255 的硬跳变 ✗）：" +
                "实际 ${plainAlphas.distinct().sorted().take(8)}…",
            plainAlphas.any { it in 1..254 },
        )

        // ⚠️ 如实钉一条**已知的观感**（照网页搬过来的必然结果 ✓，不是坏 ✗）：
        //    密集重叠的一笔里，同一个像素被 n 颗笔尖按 source-over 各叠一次，
        //    而纸纹取的是**像素自己的画布坐标** ⇒ 每一颗笔尖乘的都是**同一个 t** ✓
        //    ⇒ 结果 alpha = `1 − (1 − t)^n`（网页 `texturedDab()` + `drawImage` 是同一条路 ✓）。
        //    也就是：**纸纹仍然不均匀，但被压缩了**（t = 0.15、n = 11 ⇒ 0.837 ⇒ alpha 213 ✓），
        //    最深的那几格会一路叠到接近 255 ✓。这一批**不动它** ✗（要改就得先改网页口径 ✓）。
        val dense = drawLine(220, 60, 30, 20, 1f, BrushSpec(paperOn = true, paperStrength = 100f))
        val denseAlphas = (60..180).map { alphaOf(dense.pixels[30 * 220 + it]) }
        assertTrue(
            "密集重叠的一笔里纸纹**仍然**不均匀（只是被叠成 `1−(1−t)^n` ✓）：" +
                "实际只有 ${denseAlphas.distinct().size} 种，范围 ${denseAlphas.min()}..${denseAlphas.max()}",
            denseAlphas.distinct().size > 5,
        )
        assertTrue(
            "密集重叠会把最深的那些格子叠到接近满（实际 min=${denseAlphas.min()} ✓）—— " +
                "这是 source-over 的必然结果（照网页 ✓），不是纸纹没生效 ✓",
            denseAlphas.min() >= 200,
        )
    }

    // ------------------------------------------------------------------
    // ③ keepOpacity：整笔一个浓度，同一处反复涂**不叠深** ✓
    // ------------------------------------------------------------------

    @Test
    fun keep_opacity_never_lets_one_stroke_get_darker_by_repainting() {
        val width = 200
        val height = 80
        val centerY = 40

        /** 来回涂三趟（同一笔之内 ✓），返回正中间那个像素的 alpha。 */
        fun scribble(brush: BrushSpec): Int {
            val session = ImageEditSession(width, height, IntArray(width * height))
            val spec = StrokeSpec(brushPixels = 20, color = 0xFF000000.toInt(), brush = brush)
            session.beginStroke(20f, centerY.toFloat(), spec)
            repeat(3) {
                session.strokeTo(180f, centerY.toFloat())
                session.strokeTo(20f, centerY.toFloat())
            }
            session.endStroke()
            return alphaOf(session.pixels[centerY * width + 100])
        }

        // 浓度 40 ⇒ 单笔浓度 = 0.4 ⇒ alpha ≈ 102 ✓
        val keep = scribble(BrushSpec(density = 40f, keepOpacity = true))
        assertEquals(
            "keepOpacity 时同一处来回涂三趟，alpha 必须**还是单笔浓度**（0.4 × 255 ≈ 102 ✓），实际 $keep",
            102,
            keep,
        )

        // 对照：关掉 keepOpacity ⇒ 同一处反复涂会**叠深**（这一条证明上面那条不是"没生效"✓）
        val plain = scribble(BrushSpec(density = 40f, keepOpacity = false))
        assertTrue("关掉 keepOpacity 时反复涂必然叠深（实际 $plain ≠ $keep ✓）", plain > keep)

        // 浓度 100 + keepOpacity ⇒ 整笔不透明（255 ✓）
        val solid = scribble(BrushSpec(density = 100f, keepOpacity = true))
        assertEquals("浓度 100 + keepOpacity ⇒ 就是满不透明（255 ✓）", 255, solid)

        // 而且**仍然只有一步历史**（抬笔合成那一条 ✓）
        val session = ImageEditSession(width, height, IntArray(width * height))
        session.beginStroke(
            20f,
            centerY.toFloat(),
            StrokeSpec(brushPixels = 20, color = 0xFF000000.toInt(), brush = BrushSpec(keepOpacity = true)),
        )
        session.strokeTo(180f, centerY.toFloat())
        session.endStroke()
        assertTrue("keepOpacity 那一笔也要能撤销", session.undo())
        assertTrue(
            "撤销之后应当一个不透明像素都不剩（独立缓冲那条历史记全了 ✓）",
            session.pixels.all { alphaOf(it) == 0 },
        )
    }

    // ------------------------------------------------------------------
    // ④ integerPosition：笔尖中心对齐到像素边界 ✓
    // ------------------------------------------------------------------

    @Test
    fun integer_position_snaps_the_nib_centre_to_pixel_boundaries() {
        fun planned(dab: BrushSpec): FloatArray {
            val plan = BrushEngine.planDab(
                brush = dab,
                state = BrushEngine.StrokeState(kotlin.random.Random(3)),
                sampleSource = null,
                sampleWidth = 0,
                sampleHeight = 0,
                x = 50.4f,
                y = 30.7f,
                baseRadius = 6f,
                pressure = 1f,
                directionRadians = 0f,
                colorArgb = 0xFF000000.toInt(),
                erasing = false,
                pressureCurve = 1f,
            )
            val shape = plan.shapes[0]
            return floatArrayOf(shape.x, shape.y)
        }

        val loose = planned(BrushSpec())
        assertEquals("IntegerPosition = 0 ⇒ 中心保持原样 ✓", 50.4f, loose[0], 1e-5f)
        assertEquals("IntegerPosition = 0 ⇒ 中心保持原样 ✓", 30.7f, loose[1], 1e-5f)

        val snapped = planned(BrushSpec(integerPosition = 1))
        assertEquals("IntegerPosition = 1 ⇒ 笔尖中心对齐到像素边界（x ✓）", 50f, snapped[0], 1e-5f)
        assertEquals("IntegerPosition = 1 ⇒ 笔尖中心对齐到像素边界（y ✓）", 31f, snapped[1], 1e-5f)
    }

    // ------------------------------------------------------------------
    // ⑤ 模糊笔压：0 = 恒定 ✓，100 = 完全跟压力走 ✓
    // ------------------------------------------------------------------

    @Test
    fun blur_pressure_scales_the_blur_intensity_with_the_pen() {
        val width = 200
        val height = 80
        val centerY = 40

        /** 底：灰底 + 中间一小块黑（模糊之后方块中心会变灰 ⇒ 亮起来 ✓）。 */
        fun base(): IntArray {
            val pixels = IntArray(width * height) { 0xFF808080.toInt() }
            for (y in 38..42) {
                for (x in 98..102) pixels[y * width + x] = 0xFF000000.toInt()
            }
            return pixels
        }

        fun blurCentre(brush: BrushSpec, pressure: Float): Int {
            val session = ImageEditSession(width, height, base())
            val spec = StrokeSpec(
                mode = StrokeMode.BLUR,
                brushPixels = 40,
                blurIntensity = 100,
                // ⚠️ 最小半径比例给 1 ⇒ 按感后半径**与压力无关**（恒 20 ✓）——
                //    这样"恒定 vs 跟压力走"这两个变量才**只剩强度一个** ✓（不然半径也跟着变，
                //    两档压力的差别会分不清是"笔压"还是"笔尖变小"✗）。
                minRadiusRatio = 1f,
                brush = brush,
            )
            session.beginStroke(20f, centerY.toFloat(), spec, pressure = pressure)
            session.strokeTo(180f, centerY.toFloat(), pressure = pressure)
            session.endStroke()
            return grayOf(session.pixels[centerY * width + 100])
        }

        val followPen = BrushSpec(blurPressureOn = true, blurPressure = 100f)
        val light = blurCentre(followPen, 0.2f)
        val heavy = blurCentre(followPen, 1f)
        assertTrue(
            "blurPressure = 100 ⇒ 强度完全跟压力走：轻按（0.2）该糊得**少**（黑方块中心还暗 ✓）" +
                "（实际 轻按 $light / 重按 $heavy）",
            light < heavy,
        )
        assertTrue("重按（1.0）应当把黑方块糊得更接近灰（实际 $heavy）", heavy > 0)

        // blurPressure = 0 ⇒ **强度恒定** ✓ ⇒ 两档压力结果**逐位一致** ✓
        val constant = BrushSpec(blurPressureOn = true, blurPressure = 0f)
        val lightConst = blurCentre(constant, 0.2f)
        val heavyConst = blurCentre(constant, 1f)
        assertEquals("blurPressure = 0 ⇒ 强度恒定（两档压力画出来必须一模一样 ✓）", heavyConst, lightConst)

        // 关掉「模糊笔压」开关 ⇒ 同样恒定 ✓
        val off = BrushSpec(blurPressureOn = false, blurPressure = 100f)
        assertEquals("blurPressureOn = false ⇒ 恒定 ✓", blurCentre(off, 1f), blurCentre(off, 0.2f))
    }

    // ------------------------------------------------------------------
    // ⑥ 混色采样**硬上限 64px**：只读 dab 附近那一小块 ✓（绝不全画布读 ✗）
    // ------------------------------------------------------------------

    @Test
    fun blending_never_samples_farther_than_the_64px_half_width_cap() {
        val width = 500
        val height = 200
        val blue = 0xFF0000FF.toInt()
        val red = 0xFFFF0000.toInt()

        /** 蓝底 + 在 [blockFrom, blockTo) 竖条上放一块红（**整高**✓，免得窗口在 y 方向被稀释 ✓）。 */
        fun bufferWithRed(blockFrom: Int, blockTo: Int): IntArray {
            val pixels = IntArray(width * height) { blue }
            for (y in 0 until height) {
                for (x in blockFrom until blockTo) pixels[y * width + x] = red
            }
            return pixels
        }

        /**
         * 画一颗半径 100 的笔尖（`water = 100` ⇒ 采样半宽本来会是 `100 × 1.8 = 180` ✓，
         * 被 `MAX_SAMPLE_PX = 64` 夹住 ✓），返回笔迹的 R 通道。
         */
        fun dabRed(buffer: IntArray): Int {
            val plan = BrushEngine.planDab(
                brush = BrushSpec(blending = 100f, water = 100f),
                state = BrushEngine.StrokeState(kotlin.random.Random(8)),
                sampleSource = buffer,
                sampleWidth = width,
                sampleHeight = height,
                x = 200f,
                y = 100f,
                baseRadius = 100f,
                pressure = 1f,
                directionRadians = 0f,
                colorArgb = 0xFFFFFFFF.toInt(),
                erasing = false,
                pressureCurve = 1f,
            )
            return (plan.shapes[0].colorArgb ushr 16) and 0xFF
        }

        val inside = dabRed(bufferWithRed(200, 264))   // 红块正好填满采样窗口的**右半** ✓（200..263 ✓）
        val outside = dabRed(bufferWithRed(264, 328))  // 红块左沿正好是窗口外的**第一列** ✓（264 ✓）
        assertTrue(
            "窗口内的采样色必须被吃到（R=$inside 该明显偏红 ✓）",
            inside > 120,
        )
        assertTrue(
            "窗口**外**一列都不许被采样（R=$outside 该保持蓝 ✓）—— " +
                "`MAX_SAMPLE_PX = 64` 那条硬上限就是干这个的（这里半径 100、水分 100，" +
                "不夹的话半宽会是 180 ✗，那就成整张画布读了 ✗）",
            outside < 60,
        )
        assertTrue(
            "两条的差必须看得出来（inside=$inside vs outside=$outside ✓）—— 差太小就说明上限没在起作用",
            inside > outside + 40,
        )
        assertEquals("采样半宽上限必须是 64px ✓", 64f, BrushEngine.MAX_SAMPLE_PX, 0f)
    }

    // ------------------------------------------------------------------
    // ⑦ 回归：默认参数下步距**还是 0.2 × 半径** ✓（护栏 0.9 → 0.4 没改默认密度 ✓）
    // ------------------------------------------------------------------

    @Test
    fun the_default_spacing_step_is_unchanged_by_the_new_guard() {
        // 护栏只在 `2r × spacing% > 0.4r`（也就是 spacing > 20%）时才咬得住 ✓
        // ⇒ 默认 10% 那一档**一个字都没变**（这就是三条老测试不用改数的原因 ✓）
        for (radius in listOf(0.5f, 1.8f, 5f, 22.5f, 100f)) {
            val step = ImageEditOps.dabStepPixels(radius = radius, spacingPercent = 10f)
            val bySpacing = radius * 2f * 0.1f
            assertEquals(
                "r=$radius：默认 spacing 10% 的步距必须还是 `2r × 10% = 0.2r` ✓（护栏没咬到 ✓）",
                maxOf(ImageEditOps.DAB_MIN_STEP_PIXELS, bySpacing),
                step,
                1e-5f,
            )
            // ⚠️ 上限写成 `max(最小步距, 0.4r)` 是**如实**的 ✓（不是放松 ✗）：
            //    r = 0.5px 时 `0.4r = 0.2 < 0.35 = DAB_MIN_STEP_PIXELS`，
            //    顶住步距的是那条**下限**（网页 `Math.max(0.35, …)` 一字不差 ✓）。
            assertTrue(
                "默认步距必须 ≤ max(最小步距, 0.4r)（重叠 60% ✓，实际 $step / r=$radius）",
                step <= maxOf(ImageEditOps.DAB_MIN_STEP_PIXELS, radius * 0.4f) + 1e-5f,
            )
        }

        // 恒压（鼠标那条路 ✓）画一条：不断、也不缩水（回归钉 ✓）
        val session = drawLine(
            width = 220,
            height = 120,
            centerY = 60,
            brushPixels = 45,
            pressure = 1f,                       // 鼠标 / 触摸 = 压力恒 1 ✓
            brush = BrushSpec(),
            stepPx = 3f,
        )
        var minThickness = Int.MAX_VALUE
        var maxThickness = 0
        for (x in 14..206) {
            var count = 0
            for (y in 0 until 120) if (alphaOf(session.pixels[y * 220 + x]) > 0) count++
            if (count < minThickness) minThickness = count
            if (count > maxThickness) maxThickness = count
        }
        assertTrue("恒压不能缩水：最粗 $maxThickness 该是整宽（45 → 44/45 ✓）", maxThickness >= 44)
        assertTrue("恒压笔迹不该断（最薄 $minThickness ≥ 42 ✓）", minThickness >= 42)
        assertEquals("恒压横线一格洞都不许有 ✓", 0, longestGapRun(session.pixels, 220, 120, 60, 14, 206))
        assertTrue(
            "默认参数（`BrushSpec()`）与网页 `DEFAULTS` 必须一致 ✓：" +
                "spacing=${BrushSpec.DEFAULTS.spacing} density=${BrushSpec.DEFAULTS.density} " +
                "opacity=${BrushSpec.DEFAULTS.opacity}",
            abs(BrushSpec.DEFAULTS.spacing - 10f) < 1e-6f &&
                abs(BrushSpec.DEFAULTS.density - 100f) < 1e-6f &&
                abs(BrushSpec.DEFAULTS.opacity - 100f) < 1e-6f,
        )
    }
}
