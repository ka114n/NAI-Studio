package com.kallan.naistudio.models

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **笔刷引擎（纯逻辑那半）** —— 用户 2026-09-20 第二批：
 * 「把网页版那套笔刷引擎搬进 App ✓，笔刷取"简单版"（`docs/brush-lab-simple.html` ✓），
 * **这一批只做引擎**（纯逻辑 + 测试），**不做面板 UI** ✓」。
 *
 * 这里钉的全是 [BrushEngine] 的**纯函数** ✓（不需要界面、不需要数位板、不需要文件 ✓）：
 *
 *  1. **护栏 0.4**：任意压力 / 间距 / 宽高比下 `step ≤ 0.4 × 行进方向半径` ✓；
 *  2. **散布**：`scattering > 0` 时落点真的偏离中心线 ✓；`allDirScattering = 0` 时
 *     **只往直交方向**偏 ✓；`gaussianDistribution = 1` 时**远处更稀** ✓；
 *  3. **抖动**：`sizeJitter = 100` 半径不全相等 ✓ / `= 0` 全相等 ✓；
 *     `count = 5` 时每一档 5 颗 ✓（`countJitter` 会把它抖到 1..10 ✓，照网页的 `max(1, …)` ✓）；
 *  4. **混色 / 水分量 / 色延伸**：在"上一半红、下一半蓝"的缓冲上，白笔 + 高混色 ⇒
 *     笔迹颜色**偏向采样色** ✓；`water = 100` 更淡 ✓；`colorStretch = 100` 会把**前色拖过来** ✓；
 *  5. **画用纸 / 扩散和噪点**：贴图能生成、**按画布坐标对齐** ✓、逐像素相乘 ⇒ alpha 不均匀 ✓；
 *  6. **可复现**：同一个 seed 跑两次，**计划出来的笔尖逐位一致** ✓（这条是上面所有
 *     确定性断言的前提 ✓ —— 引擎里**没有一处**碰全局随机 ✓）。
 */
class BrushEngineTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

    // ------------------------------------------------------------------
    // ① 护栏 0.4：step ≤ 0.4 × 行进方向上的笔尖半径 ✓
    // ------------------------------------------------------------------

    @Test
    fun the_step_guard_is_never_wider_than_0_4_of_the_along_travel_radius() {
        // 4 档压力 × 7 档间距 × 4 档宽高比 × 2 种朝向 = 224 组，逐组核 ✓
        val pressures = listOf(0.02f, 0.15f, 0.5f, 1f)
        val spacings = listOf(1f, 10f, 20f, 50f, 100f, 200f, 1000f)
        val ratios = listOf(1f, 2f, 4f, 8f)
        var checked = 0
        var strictChecked = 0
        for (pressure in pressures) {
            for (spacing in spacings) {
                for (ratio in ratios) {
                    for (fixedAngle in listOf(false, true)) {
                        val brush = BrushSpec(
                            spacing = spacing,
                            wxRatio = BrushSpec.nibRatioToWxRatio(ratio),
                            angleControl = if (fixedAngle) NibAngleControl.FIXED else NibAngleControl.AUTO,
                        )
                        val radius = ImageEditOps.brushRadiusPixels(45, pressure)
                        val direction = 1.1f        // 一条斜着走的笔画 ✓
                        val nibAngle = BrushEngine.nibAngleRadians(brush, direction)
                        val step = ImageEditOps.dabStepPixels(
                            radius = radius,
                            spacingPercent = brush.spacing,
                            nibRatio = brush.nibRatio,
                            nibAngleRadians = nibAngle,
                            travelAngleRadians = direction,
                        )
                        val along = ImageEditOps.nibRadiusAlong(radius, brush.nibRatio, nibAngle, direction)
                        val guard = ImageEditOps.DAB_MAX_STEP_RATIO * along
                        assertTrue(
                            "护栏破了：step=$step > max(最小步距, 0.4 × along=$guard)" +
                                "（spacing=$spacing ratio=$ratio 固定=$fixedAngle pressure=$pressure）",
                            step <= maxOf(ImageEditOps.DAB_MIN_STEP_PIXELS, guard) + 1e-4f,
                        )
                        // ★ 严格那一档（护栏真的在起作用、不是被**最小步距**顶住的 ✓）
                        //
                        // ⚠️ 必须分开数（**如实** ✓，不是把钉子放松 ✗）：
                        //    笔尖被压到极小（压力 0.02 + 宽高比 8 ⇒ 行进方向上的半径只有 0.83px ✓）时
                        //    `0.4 × along = 0.33 < 0.35 = DAB_MIN_STEP_PIXELS` ——
                        //    这时顶住步距的是那条**下限**✓（网页 `Math.max(0.35, …)` 一字不差 ✓）：
                        //    再往下压就是"每 0.03 像素盖一次"，只会白慢一倍 ✗。
                        if (guard >= ImageEditOps.DAB_MIN_STEP_PIXELS) {
                            assertTrue(
                                "严格护栏：step=$step 必须 ≤ 0.4 × along=$guard" +
                                    "（spacing=$spacing ratio=$ratio 固定=$fixedAngle pressure=$pressure）",
                                step <= guard + 1e-4f,
                            )
                            strictChecked++
                        }
                        assertTrue("步距必须 ≥ 最小值", step >= ImageEditOps.DAB_MIN_STEP_PIXELS - 1e-6f)
                        checked++
                    }
                }
            }
        }
        assertEquals("这一组组合一共 4×7×4×2 = 224 组", 224, checked)
        assertTrue("绝大多数组合必须走**严格护栏**那一支（实际 $strictChecked / $checked）", strictChecked >= 200)
        // 护栏常数本身也钉一下（网页 `docs/brush-lab-simple.html` 的 `dabStep()` 是 `along * 0.4` ✓）
        assertEquals("护栏比例必须是 0.4（网页同一口径 ✓）", 0.4f, ImageEditOps.DAB_MAX_STEP_RATIO, 1e-6f)
    }

    // ------------------------------------------------------------------
    // ② 散布：方向 / 幅度 / 高斯分布 ✓
    // ------------------------------------------------------------------

    @Test
    fun scattering_only_offsets_perpendicular_when_all_directions_are_off() {
        val brush = BrushSpec(scattering = 100f, allDirScattering = 0)
        val random = Random(7)
        var maxPerp = 0f
        var maxAlong = 0f
        // 笔画沿 +x 走（direction = 0）⇒ 「直交方向」= y ✓
        repeat(400) {
            val offset = BrushEngine.scatterOffset(brush, directionRadians = 0f, radius = 10f, random = random)
            maxAlong = maxOf(maxAlong, kotlin.math.abs(offset[0]))
            maxPerp = maxOf(maxPerp, kotlin.math.abs(offset[1]))
        }
        assertTrue("散布必须真的偏出去（|oy| 最大才 $maxPerp ✗）", maxPerp > 1f)
        assertTrue("allDirScattering=0 时**只许**沿直交方向偏（|ox| 最大 $maxAlong ✗）", maxAlong < 1e-3f)
        // 幅度上限 = 1.6 × (r × scattering/100) = 1.6 × 10 = 16 ✓
        assertTrue("散布幅度不该超过 1.6 × 半径（实际 $maxPerp）", maxPerp <= 16.01f)

        // `scattering = 0` ⇒ 一动不动 ✓
        val still = BrushEngine.scatterOffset(BrushSpec(), 0f, 10f, Random(1))
        assertEquals(0f, still[0], 0f)
        assertEquals(0f, still[1], 0f)
    }

    @Test
    fun gaussian_scattering_thins_out_the_far_offsets() {
        val radius = 10f
        val uniform = BrushSpec(scattering = 100f, allDirScattering = 1, gaussianDistribution = 0)
        val gauss = uniform.copy(gaussianDistribution = 1)
        val randomA = Random(99)
        val randomB = Random(99)
        var farUniform = 0
        var farGauss = 0
        val samples = 4000
        repeat(samples) {
            val a = BrushEngine.scatterOffset(uniform, 0f, radius, randomA)
            val b = BrushEngine.scatterOffset(gauss, 0f, radius, randomB)
            // 「远」= 超过散布幅度的一半（amp = r × 100% = 10 ⇒ 门槛 5）✓
            if (kotlin.math.hypot(a[0], a[1]) > 5f) farUniform++
            if (kotlin.math.hypot(b[0], b[1]) > 5f) farGauss++
        }
        assertTrue(
            "高斯分布必须**越远越稀** ✓（均匀远处 $farUniform / 高斯远处 $farGauss）",
            farGauss < farUniform,
        )
        // 同一 seed 跑两次 ⇒ **同一个结果** ✓（可复现那一半 —— 整条引擎的随机都走 Random(seed) ✓）
        fun runGauss(): List<Float> {
            val again = Random(20260920)
            return (0 until 200).map { BrushEngine.scatterOffset(gauss, 0f, radius, again)[0] }
        }
        assertEquals("同一个 seed 的散布必须逐位一致 ✓", runGauss(), runGauss())
    }

    // ------------------------------------------------------------------
    // ③ 抖动：SizeJitter / Count / CountJitter ✓
    // ------------------------------------------------------------------

    @Test
    fun size_jitter_makes_the_nib_radii_differ_and_zero_keeps_them_equal() {
        val base = 12f
        val jittered = BrushSpec(sizeJitter = 100f)
        val plain = BrushSpec(sizeJitter = 0f)

        val randomA = Random(20260920)
        val values = (0 until 40).map { BrushEngine.jitterNibRadius(base, jittered, randomA) }
        assertTrue(
            "sizeJitter=100 时同一笔的半径**不许全相等** ✗（实际 ${values.distinct().size} 个不同值）",
            values.distinct().size > 5,
        )
        assertTrue("抖动后的半径不该全在基准上（范围 ${values.min()}..${values.max()}）", values.max() > base || values.min() < base)
        assertTrue("sizeJitter=100 的最小半径不能变成 0 / 负（实际 ${values.min()}）", values.min() >= BrushEngine.DAB_MIN_RADIUS)

        val randomB = Random(20260920)
        val flat = (0 until 40).map { BrushEngine.jitterNibRadius(base, plain, randomB) }
        assertTrue(
            "sizeJitter=0 时同一笔的半径必须**全相等** ✗（实际 ${flat.distinct()}）",
            flat.all { kotlin.math.abs(it - base) < 1e-5f },
        )
    }

    @Test
    fun count_and_count_jitter_decide_how_many_dabs_land() {
        val exact = BrushSpec(count = 5, countJitter = 0f)
        val random = Random(5)
        repeat(50) {
            assertEquals("count=5 + countJitter=0 ⇒ 每一档**正好 5 颗** ✓", 5, BrushEngine.dabCount(exact, random))
        }
        // countJitter=100：照网页 `max(1, round(Count × (1 ± 100%)))` ⇒ **1..10** ✓
        //（注意：抖动**可以把它压到少于 Count** ✓ —— 这是网页的公式，不是我们发明的 ✓）
        val wild = BrushSpec(count = 5, countJitter = 100f)
        val counts = (0 until 200).map { BrushEngine.dabCount(wild, Random(it.toLong())) }
        assertTrue("countJitter=100 时颗数必须**至少 1**（网页 `Math.max(1, …)` ✓）", counts.min() >= 1)
        assertTrue("countJitter=100 时颗数必须**会变** ✗（实际 ${counts.distinct().sorted()}）", counts.distinct().size > 3)
        assertTrue("countJitter=100 时颗数上限 = round(5 × 2) = 10", counts.max() <= 10)
    }

    // ------------------------------------------------------------------
    // ④ 混色 / 水分量 / 色延伸 ✓
    // ------------------------------------------------------------------

    /** 「上一半红、下一半蓝」的缓冲 ✓（采样混色那几条测试的共同底板 ✓）。 */
    private fun redBlueBuffer(width: Int, height: Int): IntArray {
        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        return IntArray(width * height) { index -> if ((index % width) < width / 2) red else blue }
    }

    /** 在一个点上算一档笔尖，把第一颗的形状颜色取回来 ✓（纯函数级断言 ✓）。 */
    private fun singleDabColor(
        brush: BrushSpec,
        buffer: IntArray,
        width: Int,
        height: Int,
        x: Float,
        y: Float,
        seed: Long = 11L,
    ): Int {
        val state = BrushEngine.StrokeState(Random(seed))
        val plan = BrushEngine.planDab(
            brush = brush,
            state = state,
            sampleSource = buffer,
            sampleWidth = width,
            sampleHeight = height,
            x = x,
            y = y,
            baseRadius = 10f,
            pressure = 1f,
            directionRadians = 0f,
            colorArgb = white,
            erasing = false,
            pressureCurve = 1f,
        )
        assertEquals("count 默认 1 ⇒ 一档一颗 ✓", 1, plan.shapes.size)
        return plan.shapes[0].colorArgb
    }

    @Test
    fun blending_pulls_the_dab_colour_towards_the_sampled_colour() {
        val width = 200
        val height = 60
        val buffer = redBlueBuffer(width, height)
        // 落点取右半（蓝的那一半，x ≥ 100 ✓）：采样窗口就在蓝里 ⇒ 白笔应当被拉成**蓝** ✓
        val plain = singleDabColor(BrushSpec(), buffer, width, height, 120f, 30f)
        val mixed = singleDabColor(BrushSpec(blending = 100f), buffer, width, height, 120f, 30f)

        val plainR = (plain ushr 16) and 0xFF
        val plainB = plain and 0xFF
        assertEquals("blending=0 时颜色就是笔色（白 ✓）", 255, plainR)
        assertEquals(255, plainB)
        val mixedR = (mixed ushr 16) and 0xFF
        val mixedB = mixed and 0xFF
        assertTrue(
            "blending=100 时笔迹必须**偏向采样色**（蓝 ✓）：实际 R=$mixedR B=$mixedB",
            mixedB - mixedR > 150,
        )
        assertTrue("留 15% 本色 ⇒ 不完全等于采样色（R=$mixedR）", mixedR > 0)

        // 落点还在红的那一半 ⇒ 同一个公式应当把白笔拉成**红** ✓（方向对得上 ✓）
        val redSide = singleDabColor(BrushSpec(blending = 100f), buffer, width, height, 60f, 30f)
        assertTrue(
            "在红的那一半混色应当拉成红（实际 R=${(redSide ushr 16) and 0xFF} B=${redSide and 0xFF}）",
            ((redSide ushr 16) and 0xFF) - (redSide and 0xFF) > 150,
        )
    }

    @Test
    fun water_lowers_the_alpha_and_stretches_the_sample_area() {
        val plain = BrushEngine.dabAlpha(BrushSpec(density = 100f), 1f, 1f, Random(1), erasing = false)
        val wet = BrushEngine.dabAlpha(BrushSpec(density = 100f, water = 100f), 1f, 1f, Random(1), erasing = false)
        assertEquals("不带水分量 ⇒ alpha 就是浓度（1 ✓）", 1f, plain, 1e-6f)
        assertEquals("water=100 ⇒ alpha × (1 − 0.55) = 0.45 ✓", 0.45f, wet, 1e-5f)
        assertTrue("水分量越大越淡 ✓", wet < plain)

        // 采样半宽也要跟着水分量放大（网页 `r × (0.8 + 1.0 × waterF)` ✓）——
        // 用"采样到的颜色"来间接核：把红蓝边界放在落点**左边一点**，
        // 采样窗口小的时候只吃到蓝、放大之后能吃到红 ✓
        val width = 200
        val height = 60
        val buffer = redBlueBuffer(width, height)
        val narrow = singleDabColor(BrushSpec(blending = 100f), buffer, width, height, 104f, 30f)
        val wide = singleDabColor(BrushSpec(blending = 100f, water = 100f), buffer, width, height, 104f, 30f)
        val narrowR = (narrow ushr 16) and 0xFF
        val wideR = (wide ushr 16) and 0xFF
        assertTrue(
            "water=100 时采样区域更大 ⇒ 更容易吃到**边界外侧**的红（R：$narrowR → $wideR）",
            wideR >= narrowR,
        )
    }

    @Test
    fun colour_stretch_drags_the_previous_colour_along_the_stroke() {
        val width = 200
        val height = 60
        val buffer = redBlueBuffer(width, height)
        // 落点刚过红蓝边界（x = 104，半径 10）：
        //  · 不拖色 ⇒ 采样点在 104（蓝）⇒ 白笔被拉成蓝 ✓；
        //  · colorStretch=100 ⇒ 采样点后拖 2r = 20 ⇒ 落在 84（**红**）⇒ 拉到红 ✓ = "被拖过来的前色" ✓
        val noStretch = singleDabColor(BrushSpec(blending = 100f), buffer, width, height, 104f, 30f)
        val stretched = singleDabColor(
            BrushSpec(blending = 100f, colorStretch = 100f),
            buffer,
            width,
            height,
            104f,
            30f,
        )
        val noStretchR = (noStretch ushr 16) and 0xFF
        val noStretchB = noStretch and 0xFF
        val stretchedR = (stretched ushr 16) and 0xFF
        val stretchedB = stretched and 0xFF
        assertTrue("不拖色时这一颗偏蓝（R=$noStretchR B=$noStretchB ✓）", noStretchB > noStretchR)
        assertTrue(
            "colorStretch=100 时起点附近必须出现**被拖过来的前色**（红 ✓）：实际 R=$stretchedR B=$stretchedB",
            stretchedR > stretchedB,
        )
    }

    // ------------------------------------------------------------------
    // ⑤ 画用纸 / 扩散和噪点：贴图生成 + 按画布坐标对齐 ✓
    // ------------------------------------------------------------------

    @Test
    fun paper_tile_is_generated_aligned_and_multiplied() {
        val paper = BrushSpec(paperOn = true, paperStrength = 100f)
        assertTrue("开着画用纸就该要贴图 ✓", paper.needsTexture)
        assertTrue("默认（纸纹 / 噪点全关）**不该**要贴图（省一次生成 ✓）", !BrushSpec().needsTexture)

        val tileA = BrushEngine.buildTile(paper, Random(BrushEngine.TILE_SEED))
        val tileB = BrushEngine.buildTile(paper, Random(BrushEngine.TILE_SEED))
        assertEquals("同一个 seed ⇒ 同一张贴图 ✓", tileA.size, tileB.size)
        assertTrue("贴图必须可复现（同一 seed 逐字节一致 ✓）", tileA.contentEquals(tileB))

        var min = 255
        var max = 0
        for (value in tileA) {
            val v = value.toInt() and 0xFF
            if (v < min) min = v
            if (v > max) max = v
        }
        assertTrue("画用纸贴图必须有明暗变化（实际 $min..$max ✗ 全等就没纸纹了）", max - min > 20)
        assertTrue("强度 100 时最暗处该被压得挺深（实际 $min）", min < 200)

        // **按画布坐标对齐** ✓：同一个画布坐标永远取到同一个格子 ✓，而且**周期是 128** ✓
        assertEquals(
            "画布坐标 → 贴图格子必须对齐（同一坐标 — 同一值 ✓）",
            BrushEngine.textureAt(tileA, 37, 91),
            BrushEngine.textureAt(tileA, 37, 91),
            1e-6f,
        )
        assertEquals(
            "贴图必须**无缝平铺**（x + 128 是同一格 ✓）",
            BrushEngine.textureAt(tileA, 37, 91),
            BrushEngine.textureAt(tileA, 37 + BrushEngine.TILE, 91),
            1e-6f,
        )
        assertEquals(
            "负数坐标也要夹回同一个格子（画布外采样不能崩 ✗）",
            BrushEngine.textureAt(tileA, -1, 5),
            BrushEngine.textureAt(tileA, BrushEngine.TILE - 1, 5),
            1e-6f,
        )

        // 强度 0 / 关掉 ⇒ 全 255（= 乘 1，等于没纹理 ✓）
        val off = BrushEngine.buildTile(BrushSpec(), Random(BrushEngine.TILE_SEED))
        assertTrue("纸纹关着时贴图必须全 255（乘 1 = 不改变画面 ✓）", off.all { (it.toInt() and 0xFF) == 255 })
    }

    @Test
    fun spread_noise_adds_grain_that_paper_alone_does_not() {
        val noise = BrushSpec(spreadNoise = true, spreadNoiseStrength = 100f)
        val tile = BrushEngine.buildTile(noise, Random(BrushEngine.TILE_SEED))
        var min = 255
        var max = 0
        for (value in tile) {
            val v = value.toInt() and 0xFF
            if (v < min) min = v
            if (v > max) max = v
        }
        assertTrue("扩散和噪点必须真的产生深浅（实际 $min..$max）", max - min > 20)

        // 扩散和噪点那一条还带**半径 / 位置 / alpha** 抖动 ✓（强度越高越明显 ✓）
        val wild = BrushSpec(spreadNoise = true, spreadNoiseStrength = 100f)
        val random = Random(31)
        val radii = (0 until 40).map { BrushEngine.jitterNibRadius(10f, wild, random) }
        assertTrue("扩散和噪点必须抖半径 ✗（实际 ${radii.distinct().size} 个不同值）", radii.distinct().size > 5)
        val plan = BrushEngine.planDab(
            brush = wild,
            state = BrushEngine.StrokeState(Random(31)),
            sampleSource = null,
            sampleWidth = 0,
            sampleHeight = 0,
            x = 50f,
            y = 50f,
            baseRadius = 10f,
            pressure = 1f,
            directionRadians = 0f,
            colorArgb = black,
            erasing = false,
            pressureCurve = 1f,
        )
        val shape = plan.shapes[0]
        assertTrue("扩散和噪点必须抖**位置** ✗", kotlin.math.abs(shape.x - 50f) > 1e-3f || kotlin.math.abs(shape.y - 50f) > 1e-3f)
    }

    // ------------------------------------------------------------------
    // ⑥ 可复现：同一 seed ⇒ 逐位一致（这是上面所有确定性断言的前提 ✓）
    // ------------------------------------------------------------------

    @Test
    fun the_same_seed_replays_the_exact_same_dabs() {
        val brush = BrushSpec(
            scattering = 120f,
            allDirScattering = 1,
            gaussianDistribution = 1,
            sizeJitter = 80f,
            angleJitter = 70f,
            wxRatio = 60f,
            wxJitter = 40f,
            count = 4,
            countJitter = 50f,
            hueJitter = 50f,
            brightnessJitter = 30f,
            colJitter = 20f,
            applyToEachShape = 1,
            spreadNoise = true,
            spreadNoiseStrength = 60f,
        )
        fun run(): List<String> {
            val state = BrushEngine.StrokeState(Random(4242))
            return (0 until 12).map { step ->
                val plan = BrushEngine.planDab(
                    brush = brush,
                    state = state,
                    sampleSource = null,
                    sampleWidth = 0,
                    sampleHeight = 0,
                    x = 10f + step * 3f,
                    y = 30f,
                    baseRadius = 8f,
                    pressure = 0.3f + step * 0.05f,
                    directionRadians = 0.2f,
                    colorArgb = 0xFF3366CC.toInt(),
                    erasing = false,
                    pressureCurve = 1f,
                )
                plan.shapes.joinToString("|") { "${it.x},${it.y},${it.radius},${it.ratio},${it.angleRadians},${it.colorArgb}" }
            }
        }
        assertEquals("同一个 seed 跑两次必须**逐位一致** ✓（引擎里不许有全局随机 ✗）", run(), run())
        assertNotEquals("换个 seed 就该不一样 ✓", run(), runWithSeed(4243, brush))
    }

    private fun runWithSeed(seed: Long, brush: BrushSpec): List<String> {
        val state = BrushEngine.StrokeState(Random(seed))
        return (0 until 12).map { step ->
            val plan = BrushEngine.planDab(
                brush = brush,
                state = state,
                sampleSource = null,
                sampleWidth = 0,
                sampleHeight = 0,
                x = 10f + step * 3f,
                y = 30f,
                baseRadius = 8f,
                pressure = 0.3f + step * 0.05f,
                directionRadians = 0.2f,
                colorArgb = 0xFF3366CC.toInt(),
                erasing = false,
                pressureCurve = 1f,
            )
            plan.shapes.joinToString("|") { "${it.x},${it.y},${it.radius},${it.ratio},${it.angleRadians},${it.colorArgb}" }
        }
    }

    // ------------------------------------------------------------------
    // ⑦ 采样：只取一小块、绝不全画布读 ✓（`MAX_SAMPLE_PX` 那条硬上限）
    // ------------------------------------------------------------------

    @Test
    fun sampling_is_alpha_weighted_and_capped() {
        val width = 40
        val height = 40
        val pixels = IntArray(width * height) { 0xFF204080.toInt() }
        val sample = requireNotNull(BrushEngine.sampleAvg(pixels, width, height, 20f, 20f, 4f))
        assertEquals("不透明底 ⇒ 采样色就是底色 R（0x20）", 0x20.toFloat(), sample[0], 1f)
        assertEquals("不透明底 ⇒ 采样色就是底色 G（0x40）", 0x40.toFloat(), sample[1], 1f)
        assertEquals("不透明底 ⇒ 采样色就是底色 B（0x80）", 0x80.toFloat(), sample[2], 1f)
        assertEquals("不透明底 ⇒ alpha 平均是 1", 1f, sample[3], 1e-4f)

        // 全透明 ⇒ 白色 + alpha 0 ✓（"在空白处落笔突然变黑"那条老坑 ✓）
        val empty = BrushEngine.sampleAvg(IntArray(width * height), width, height, 20f, 20f, 4f)
        assertTrue(empty != null)
        assertEquals(255f, empty!![0], 1e-4f)
        assertEquals(0f, empty[3], 1e-4f)

        // 半透明一半、不透明一半 ⇒ 颜色**按 alpha 加权**（不是简单平均 ✓）
        val half = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                half[y * width + x] = if (x < width / 2) 0x00FF0000 else 0xFFFFFFFF.toInt()
            }
        }
        val weighted = requireNotNull(BrushEngine.sampleAvg(half, width, height, 20f, 20f, 10f))
        assertEquals("全透明的红不该把颜色拉黑（按 alpha 加权 ✓）", 255f, weighted[0], 1f)

        // 采样半宽的**硬上限**是 64 px（像素 ✓，照网页 `MAX_SAMPLE_PX` ✓）
        assertEquals("采样半宽上限 = 64px", 64f, BrushEngine.MAX_SAMPLE_PX, 0f)
        assertEquals("贴图边长 = 128（照网页 TILE ✓）", 128, BrushEngine.TILE)
    }
}
