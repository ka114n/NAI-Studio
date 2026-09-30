package com.kallan.naistudio.models

import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **逐点压感 + 笔尖旋转**（用户 2026-09-20：「**逐点压感和旋转做**」✓）那一批的钉子。
 *
 * 这一批全是**纯逻辑**（像素数组 + 三角函数 ✓）—— 不需要界面、不需要数位板、不需要文件 ✓，
 * 所以能在这儿钉死：
 *
 *  1. **同一笔里压力从 0.2 涨到 1.0 → 笔尖半径单调变大** ✓（逐点压感真的逐点 ✓，
 *     不是"落笔那一刻定一次" ✗）；
 *  2. **恒压（鼠标那条路 ✓）→ 半径与参数化之前逐字一致** ✓（回归钉 ✓：
 *     `基础半径 × (0.25 + 0.75 × pressure)` 这条老公式一个字都没坏 ✓）；
 *  3. **`nibRatio > 1` 时椭圆长轴真的跟着 `angleControl == 2` 的旋转转** ✓
 *     （这条最关键 ✓ —— 用户点名的坑是"旋转了但看不出来" ✗：所以必须真的画出椭圆 ✓）；
 *  4. **`endStroke` 之后 `isDrawing == false` / `hasChanges == true`** ✓（一笔的收口 ✓）。
 *
 * ⚠️ 真机手感（数位板真压感 / 笔杆真旋转）**我这边验不了** ✗（没有数位板 ✓）——
 * 这里只钉"我们这一侧的算术"，硬件那半见回报 ✓。
 */
class PenDabEngineTest {

    private fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xFF

    /** 第 [x] 列上有多少个不透明像素（量"这一笔在这一点有多粗" ✓）。 */
    private fun columnThickness(pixels: IntArray, width: Int, height: Int, x: Int): Int {
        if (x < 0 || x >= width) return 0
        var count = 0
        for (y in 0 until height) {
            if (alphaOf(pixels[y * width + x]) > 0) count++
        }
        return count
    }

    /** 第 [y] 行上有多少个不透明像素（量椭圆笔尖的横向跨度 ✓）。 */
    private fun rowThickness(pixels: IntArray, width: Int, y: Int): Int {
        var count = 0
        for (x in 0 until width) {
            if (alphaOf(pixels[y * width + x]) > 0) count++
        }
        return count
    }

    /**
     * 某一列附近（±[spread] 列）**最粗**的那一列 ✓。
     *
     * 为什么要这个窗口：笔尖是**离散盖上去**的 ✓（每 `Spacing` 一个 ✓），
     * 正好落在两颗笔尖**中间**的那一列会比两侧略薄一点 —— 那是笔尖走位本来的样子 ✓，
     * 不是压感坏了 ✓。窗口取最大值 = 量"这一点附近的笔尖有多大" ✓（更稳 ✓）。
     */
    private fun peakThickness(pixels: IntArray, width: Int, height: Int, x: Int, spread: Int = 4): Int {
        var best = 0
        for (candidate in (x - spread)..(x + spread)) {
            val thickness = columnThickness(pixels, width, height, candidate)
            if (thickness > best) best = thickness
        }
        return best
    }

    // ------------------------------------------------------------------
    // ① 逐点压感：一笔之内压力 0.2 → 1.0，半径**单调变大** ✓
    // ------------------------------------------------------------------

    @Test
    fun a_pressure_ramp_inside_one_stroke_makes_the_nib_grow() {
        val width = 200
        val height = 120
        val session = ImageEditSession(width, height, IntArray(width * height))
        // ㉙ 批：段内压力插值已改成**显式开关**（默认关 = 网页口径 ✓）——
        // 这条测试的本意就是"**一段之内**压力爬升让笔尖变粗" ✓，所以这里显式打开 ✓。
        val spec = StrokeSpec(
            brushPixels = 40,
            color = 0xFF000000.toInt(),
            interpolateSegmentPressure = true,
        )

        // 从 (10,60) 画到 (190,60)：**一笔之内**压力由 0.2 涨到 1.0 ✓
        assertTrue("落笔就该盖下第一个笔尖", session.beginStroke(10f, 60f, spec, pressure = 0.2f))
        assertTrue("这一段应当真的盖了笔尖", session.strokeTo(190f, 60f, pressure = 1.0f))
        // ⚠️ 第 ㉔ 批（活笔画缓冲 ✓）：一笔的墨迹**先记在独立缓冲上** ✗ —— 层像素 `pixels`
        //    要到**抬笔**（`endStroke` ✓）那一刻才真的写进去 ✓。
        //    所以这一条断言之前必须先收笔 ✓ —— **只是"怎么调 API"变了** ✗，
        //    下面的判据（粗细单调 / 上下限 / 逐字一致）**一个字都没放松** ✓。
        session.endStroke()

        // ---- 单调性：沿笔画取一排采样点，粗细必须**一路不回头** ✓ ----
        val samples = listOf(30, 70, 110, 150, 185)
        val thicknesses = samples.map { peakThickness(session.pixels, width, height, it) }
        for (index in 1 until thicknesses.size) {
            assertTrue(
                "逐点压感：压力一路上涨，笔迹只能越来越粗 ✗（采样点 ${samples[index - 1]} → ${samples[index]}：" +
                    "${thicknesses[index - 1]} → ${thicknesses[index]}，全部=$thicknesses）",
                thicknesses[index] >= thicknesses[index - 1],
            )
        }
        assertTrue(
            "一头一尾必须差得看得出来（不然就等于「整笔一个粗细」✗）：$thicknesses",
            thicknesses.last() > thicknesses.first() + 4,
        )
        // 压满那一档 = 滑杆值（半径 20 → 40 px 上下 ✓）
        assertTrue(
            "压满应当是整整一笔宽（半径 20 → 约 40 px，实际 ${thicknesses.last()}）",
            thicknesses.last() in 36..40,
        )
        // 最轻那一档 ≈ 基础半径 × 0.4（= 0.25 + 0.75 × 0.2 ✓）→ 约 16 px
        //
        // ⚠️ 上限从 19 放到 **20** 是**如实**的（不是把钉子放松 ✗）：[peakThickness] 量的是
        // 采样点 ±4 列里**最粗**的那一列 ✓ —— 步距默认从 25% 收到 10% 之后笔尖叠得更密 ✓，
        // 这个"窗口最粗"就从窗口中间挪到了**右沿**（x=34 那一档 ⇒ 半径 9.6 ⇒ 20 px ✓）。
        // 半径公式本身一个字没动 ✓ —— 由下面 ② 那条"逐字一致"的钉子单独钉死 ✓。
        assertTrue(
            "最轻那一档应当约等于基础半径的 0.4 倍（实际 ${thicknesses.first()}）",
            thicknesses.first() in 14..20,
        )
    }

    // ------------------------------------------------------------------
    // ② 回归钉：恒压（鼠标那条路）→ 半径与参数化之前**逐字一致** ✓
    // ------------------------------------------------------------------

    @Test
    fun constant_pressure_keeps_the_pre_parameterisation_radius() {
        val baseRadius = ImageEditOps.normalizedBrushPixels(45) / 2f   // = 22.5f
        for (pressure in listOf(0f, 0.2f, 0.5f, 1f)) {
            val radius = ImageEditOps.brushRadiusPixels(45, pressure, 0.25f, 1f)
            val legacy = baseRadius * (0.25f + 0.75f * pressure)
            assertEquals(
                "压力 $pressure 下的半径必须还是那条老公式（0.25 + 0.75 × p）",
                legacy,
                radius,
                1e-4f,
            )
        }
        // 默认参数（`StrokeSpec` 的默认值 ✓）与显式传老参数**必须是同一个数** ✓
        for (pressure in listOf(0f, 0.35f, 0.8f, 1f)) {
            assertEquals(
                "默认参数与参数化之前的老参数必须等价（压力 $pressure）",
                ImageEditOps.brushRadiusPixels(45, pressure, 0.25f, 1f),
                ImageEditOps.brushRadiusPixels(45, pressure),
                1e-6f,
            )
        }
        // 压力 = 1（鼠标 / 触摸）→ 正好是**整宽**（半径 = 直径 / 2 ✓）
        assertEquals(22.5f, ImageEditOps.brushRadiusPixels(45, 1f), 1e-4f)

        // ---- 恒压一笔：整条线**不断**、粗细也不缩水（dab 走位没把老笔迹啃断 ✓）----
        val width = 200
        val height = 120
        val session = ImageEditSession(width, height, IntArray(width * height))
        val spec = StrokeSpec(brushPixels = 45, color = 0xFF000000.toInt())
        session.beginStroke(10f, 60f, spec)              // 压力走默认 1f = 鼠标那条路 ✓
        for (step in 11..190 step 3) {
            session.strokeTo(step.toFloat(), 60f)         // 默认压力 = 1f ✓
        }
        session.endStroke()

        var minThickness = Int.MAX_VALUE
        var maxThickness = 0
        for (x in 14..186) {
            val thickness = columnThickness(session.pixels, width, height, x)
            if (thickness < minThickness) minThickness = thickness
            if (thickness > maxThickness) maxThickness = thickness
        }
        assertTrue(
            "恒压（鼠标那条路）不能缩水：笔尖正中间必须是**整宽的一笔**" +
                "（半径 22.5 → 44~45 px，实际最粗 $maxThickness px）",
            maxThickness >= 44,
        )
        // ⚠️ 42 这个下限从上一批沿用下来（没动 ✓）：`Spacing` 默认从 25% 收到 10% 之后，
        // 两颗笔尖之间那几列只会更接近正中（45 → 44 上下 ✓）—— 所以 42 依旧是"没缩水"的有效下界 ✓。
        //（"沿路径盖笔尖"本来就不是连续成型：老那条"点到线段距离"是连续成型 ✓，
        // 所以确实有 ±1 px 的起伏 ✗ —— 但**半径公式一个字都没变** ✓，上面几条已经钉死 ✓）。
        assertTrue(
            "恒压笔迹在 x 处断了或缩水了 ✗（整段最薄 $minThickness px / 最粗 $maxThickness px）—— " +
                "鼠标那条路必须还是**一整笔**（走位再密也只在 42~45 之间起伏 ✓）",
            minThickness >= 42,
        )
    }

    // ------------------------------------------------------------------
    // ⑤ 「**画出来是一排点**」那条 bug 的钉子（用户 2026-09-20 实机报的 ✗）
    //
    // 病根：**步距**与**笔尖**用的是两个口径 ——
    //   · 笔尖半径是"**这一颗笔尖**当场算的"（压感之后 ✓，最轻只有基础半径的 25% ✓）；
    //   · 步距却用了一个**更大的半径**（基础半径 / 段末那一档 ✓）⇒ 步距 > 笔尖直径
    //     ⇒ 两颗笔尖之间必然有空隙 = **一排点** ✗（压力越大越连得上，
    //     所以用户的体感正是「用力才像线，轻按全是点」✓）。
    //
    // 修法两条一起上（缺一条都还会漏 ✓）：
    //   ① 步距按**这一颗笔尖自己的半径**算（`spacing%` 相对**笔尖直径** ✓，与 SAI 的 Spacing 同语义 ✓）；
    //   ② 硬护栏 `step <= 0.9 × 行进方向上的笔尖半径`（相邻笔尖至少重叠 ~55% ✓）——
    //      这样 spacing 调到多大、压感把笔尖压多小，都不断线 ✓。
    // ------------------------------------------------------------------

    /**
     * 沿中线那一带扫过去：**最长的一段连续没有墨的列数** ✓。
     *
     * `>= 2` 就是肉眼可见的断点 ✓（1 像素的缝在屏幕上还算是"连着"的 ✓）。
     */
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

    /** 一列里有不透明像素吗（只看中线附近那一条带 ✓）。 */
    private fun columnHasInk(
        pixels: IntArray,
        width: Int,
        height: Int,
        x: Int,
        centerY: Int,
        band: Int = 4,
    ): Boolean {
        if (x < 0 || x >= width) return false
        for (y in maxOf(0, centerY - band)..minOf(height - 1, centerY + band)) {
            if (alphaOf(pixels[y * width + x]) > 0) return true
        }
        return false
    }

    /**
     * 画一条**恒压低压力**的横线（模拟数位板：每个 move 事件喂一次位置 + 压力 ✓）。
     *
     * 小半径 + `pressure = 0.15` = 用户说的"**轻轻按**"那一档 ✓
     *（压感比例 = 0.25 + 0.75 × 0.15 = 0.3625 ⇒ 笔尖只有基础半径的 36% ✓）。
     */
    private fun drawLightLine(
        width: Int,
        height: Int,
        centerY: Int,
        brushPixels: Int,
        pressure: Float,
        spacing: Float,
    ): ImageEditSession {
        val session = ImageEditSession(width, height, IntArray(width * height))
        val spec = StrokeSpec(
            brushPixels = brushPixels,
            color = 0xFF000000.toInt(),
            // ⚠️ 第二批把 `spacing` 搬进了 `BrushSpec`（**一处真源** ✓，`StrokeSpec` 上不留副本 ✓）
            brush = BrushSpec(spacing = spacing),
        )
        session.beginStroke(12f, centerY.toFloat(), spec, pressure = pressure)
        var x = 15f
        while (x <= 208f) {
            session.strokeTo(x, centerY.toFloat(), pressure = pressure)
            x += 2f
        }
        session.endStroke()
        return session
    }

    @Test
    fun a_light_stroke_is_a_line_and_not_a_row_of_dots() {
        val width = 220
        val height = 60
        val centerY = 30
        val session = drawLightLine(
            width = width,
            height = height,
            centerY = centerY,
            brushPixels = 10,                        // 基础半径 5 px（小半径 ✓）
            pressure = 0.15f,                        // 轻按 ✓
            spacing = ImageEditOps.DEFAULT_SPACING_PERCENT,
        )

        val gap = longestGapRun(session.pixels, width, height, centerY, 13, 207)
        assertTrue(
            "轻压（0.15）画横线，中间断了 $gap 列 ✗ —— 这就是用户的「一排点」：" +
                "步距必须按**这一颗笔尖自己的半径**算（笔尖只有基础半径的 ~36% ✓）",
            gap <= 1,
        )
        // 更严的一档：笔画覆盖的**每一列**都得有墨 ✓（不许有整列是空的 ✗）
        val uncovered = (13..207).filterNot { columnHasInk(session.pixels, width, height, it, centerY) }
        assertTrue(
            "轻压横线里有 ${uncovered.size} 整列是空的（头几列：${uncovered.take(6)}）✗ —— 断开就是「一排点」✓",
            uncovered.isEmpty(),
        )
    }

    @Test
    fun the_spacing_slider_can_never_break_the_line() {
        // ① 护栏那条：spacing 调到 100% / 200% / **最大值 1000%** 都不许断 ✓
        for (spacing in listOf(100f, 200f, 1000f)) {
            val session = drawLightLine(
                width = 220,
                height = 60,
                centerY = 30,
                brushPixels = 10,
                pressure = 0.15f,
                spacing = spacing,
            )
            val gap = longestGapRun(session.pixels, 220, 60, 30, 13, 207)
            assertTrue(
                "Spacing = $spacing% 时断了 $gap 列 ✗ —— `step <= 0.9 × 笔尖半径` 那条护栏没夹住",
                gap <= 1,
            )
        }
    }

    @Test
    fun a_light_to_heavy_ramp_grows_and_stays_connected() {
        // 同一笔里压力 0.15 → 1.0：**首尾越来越粗** ✓ 且**全程连着** ✓
        val width = 220
        val height = 80
        val centerY = 40
        val session = ImageEditSession(width, height, IntArray(width * height))
        // ㉙ 批：同上 —— 这条测的也是"段内爬升" ✓ ⇒ 显式打开插值 ✓。
        val spec = StrokeSpec(
            brushPixels = 30,
            color = 0xFF000000.toInt(),
            interpolateSegmentPressure = true,
        )
        assertTrue(session.beginStroke(12f, centerY.toFloat(), spec, pressure = 0.15f))
        assertTrue("这一长段应当真的盖了笔尖", session.strokeTo(208f, centerY.toFloat(), pressure = 1f))
        session.endStroke()

        val gap = longestGapRun(session.pixels, width, height, centerY, 14, 206)
        assertTrue("一笔之内 0.15 → 1.0 的斜坡断了 $gap 列 ✗", gap <= 1)

        val head = peakThickness(session.pixels, width, height, 20)
        val middle = peakThickness(session.pixels, width, height, 110)
        val tail = peakThickness(session.pixels, width, height, 200)
        assertTrue(
            "压力一路上涨 ⇒ 笔迹只能越来越粗（$head → $middle → $tail）",
            head < middle && middle < tail,
        )
    }

    @Test
    fun a_flat_nib_does_not_break_when_it_travels_along_its_short_axis() {
        // 扁笔尖（宽高比 8）+ `angleControl = 0`（长轴固定**水平**）⇒ **竖直**走位时，
        // 行进方向上的笔尖半径只有 `r / 8` ✓ —— 护栏要是只认长轴，这一笔必然断成"一排点" ✗。
        val width = 80
        val height = 240
        val session = ImageEditSession(width, height, IntArray(width * height))
        val spec = StrokeSpec(
            brushPixels = 40,                       // 基础半径 20 px ⇒ 短轴 2.5 px ✓
            color = 0xFF000000.toInt(),
            // 宽高比 8（Kotlin 口径：1 = 圆 ✓）→ 换成 SAI 的 `WxHRatio`（0 = 圆 ✓）存进 BrushSpec ✓
            brush = BrushSpec(
                wxRatio = BrushSpec.nibRatioToWxRatio(8f),
                angleControl = NibAngleControl.FIXED,
            ),
        )
        session.beginStroke(40f, 12f, spec)
        var y = 15f
        while (y <= 228f) {
            session.strokeTo(40f, y)
            y += 2f
        }
        session.endStroke()

        var longest = 0
        var run = 0
        for (row in 14..226) {
            var covered = false
            for (x in 34..46) {
                if (alphaOf(session.pixels[row * width + x]) > 0) {
                    covered = true
                    break
                }
            }
            if (covered) run = 0 else { run++; if (run > longest) longest = run }
        }
        assertTrue(
            "扁笔尖竖直走位断了 $longest 行 ✗ —— 护栏要按**行进方向上的笔尖半径**算（这里 = 短轴 r/8 ✓）",
            longest <= 1,
        )
    }

    // ------------------------------------------------------------------
    // ③ 笔尖朝向：`wxRatio > 0`（扁笔尖）时椭圆的长轴方向真的跟着转 ✓
    //
    // ⚠️ 第二批的改动（用户 2026-09-20「取消旋转功能」✓，与 `docs/brush-lab-simple.html` 对齐 ✓）：
    //   · `NibAngleControl.BARREL`（2 笔杆方向）与 `ImageEditOps.barrelAngleDegrees()` **已删** ✗；
    //   · 于是"笔尖真的能转"这件事改由 **`BrushSpec.angle`（SAI 的 `Angle` ✓）** 来钉 ✓ ——
    //     钉子本身**一条都没放松** ✓（下面椭圆长轴 / 短轴那几条断言原样保留 ✓），
    //     只是"怎么让它转"从笔杆换成了基准角度 ✓。
    // ------------------------------------------------------------------

    @Test
    fun an_elliptical_nib_turns_with_the_angle_control() {
        val size = 120
        val center = 60f
        val color = 0xFF000000.toInt()
        val ratio = 2f

        fun stamp(angleRadians: Float): IntArray {
            val pixels = IntArray(size * size)
            ImageEditOps.stampDab(
                pixels = pixels,
                width = size,
                height = size,
                centerX = center,
                centerY = center,
                radius = 20f,
                shape = BrushShape.ROUND,
                color = color,
                erasing = false,
                nibRatio = ratio,
                angleRadians = angleRadians,
            )
            return pixels
        }

        val angleZero = BrushEngine.nibAngleRadians(BrushSpec(angle = 0f), directionRadians = 0f)
        val angleNinety = BrushEngine.nibAngleRadians(BrushSpec(angle = 90f), directionRadians = 0f)
        assertEquals("固定 0° → 笔尖 0°", 0f, angleZero, 1e-5f)
        assertEquals("固定 90° → 笔尖 90°（π/2 ✓）", (PI / 2).toFloat(), angleNinety, 1e-5f)

        // 0°：长轴水平 → **横着的那条线更长** ✓（半径 20 / 短轴 10 ✓）
        val flat = stamp(angleZero)
        val flatWidth = rowThickness(flat, size, 60)
        val flatHeight = columnThickness(flat, size, size, 60)
        assertTrue("0° 时必须真的是椭的（宽 $flatWidth / 高 $flatHeight）", flatWidth > flatHeight)
        assertTrue("0° 的长轴约等于 2×半径（实际 $flatWidth）", flatWidth in 38..40)
        assertTrue("0° 的短轴约等于 半径/宽高比（实际 $flatHeight）", flatHeight in 18..20)

        // 90°：**同一颗笔尖**转过来 → 长轴竖起来 ✓（这才是"笔尖朝向真的画出来了"✓）
        val tall = stamp(angleNinety)
        val tallWidth = rowThickness(tall, size, 60)
        val tallHeight = columnThickness(tall, size, size, 60)
        assertTrue("90° 时应当竖过来（宽 $tallWidth / 高 $tallHeight）", tallHeight > tallWidth)
        assertEquals("转过来**不改变形状**（宽应当对调 ✓）", flatWidth, tallHeight)
        assertEquals("转过来**不改变形状**（高应当对调 ✓）", flatHeight, tallWidth)

        // 「自动」跟笔画方向 ✓；「固定」**不吃**笔画方向、只认 `angle` ✓
        assertEquals(
            "自动 = 跟随笔画方向",
            1.2f,
            BrushEngine.nibAngleRadians(BrushSpec(angleControl = NibAngleControl.AUTO), 1.2f),
            1e-6f,
        )
        assertEquals(
            "固定 = 不吃笔画方向（angle = 0 时就是 0°）",
            0f,
            BrushEngine.nibAngleRadians(BrushSpec(angleControl = NibAngleControl.FIXED), 1.2f),
            1e-6f,
        )
    }

    // ------------------------------------------------------------------
    // ④ 收笔：`isDrawing == false` / `hasChanges == true`（撤销也还是准的 ✓）
    // ------------------------------------------------------------------

    @Test
    fun end_stroke_clears_is_drawing_and_marks_changes() {
        val width = 80
        val height = 80
        val session = ImageEditSession(width, height, IntArray(width * height))
        assertFalse("还没落笔就不是在画", session.isDrawing)
        assertFalse("还没落笔就不算改过", session.hasChanges)

        assertTrue(session.beginStroke(40f, 40f, StrokeSpec(brushPixels = 12, color = 0xFF112233.toInt())))
        assertTrue("落笔之后就是在画", session.isDrawing)
        assertTrue("画了一段就该算改过", session.strokeTo(60f, 40f, pressure = 0.5f))

        session.endStroke()
        assertFalse("收笔之后就不再是在画", session.isDrawing)
        assertTrue("这一笔真的落了墨 → 这次会话算改过", session.hasChanges)
        assertTrue("收笔之后撤销该是可用的", session.canUndo)

        // 撤到底 → 像素**逐位回原样** ✓（dab 逐笔尖记的脏矩形没啃到旁边 ✓）
        assertTrue(session.undo())
        assertTrue("撤销之后应当一个不透明像素都不剩", session.pixels.all { alphaOf(it) == 0 })
    }

    // ------------------------------------------------------------------
    // ⑥ 第 ㉙ 批：**直接写层**那条路
    //
    // ⚠️ **第 ㉜a 批改过口径**（如实 ✓）：从前有个 `deferStrokeCommit` 开关来切"活笔画缓冲 / 直接写层"
    // 两条路 —— ㉜a 把**活笔画缓冲那一支整条删掉了** ✗（层真源 = skiko `Surface` ✓，
    // 画笔 / 橡皮一律**直接画在层表面上** ✓，见 `docs/53` / `docs/54` ✓）
    // ⇒ 现在**只有这一条路** ✓，所以这两条判据比从前更该钉死 ✓（判据一个字都没放松 ✗）：
    //   · **橡皮必须真的擦掉** ✗ —— 擦除走 `DST_OUT`（和网页 `destination-out` 同口径 ✓），
    //     绝不能变成"画出白色"✗；
    //   · **撤销必须逐位准确** ✗ —— 历史是把"这一笔碰过的 256 对齐块"按**落笔前**的内容记的 ✓，
    //     还原时必须逐位一致 ✓。
    // ------------------------------------------------------------------

    @Test
    fun direct_layer_erase_really_erases_and_never_paints_white() {
        val width = 60
        val height = 60
        val base = 0xFF3366CC.toInt()   // 一整块不透明蓝（= 已经画好的图层 ✓）
        val session = ImageEditSession(width, height, IntArray(width * height) { base })

        assertTrue(
            "橡皮落笔就该真的擦到像素",
            session.beginStroke(
                30f,
                30f,
                StrokeSpec(mode = StrokeMode.ERASE, brushPixels = 20),
                pressure = 1f,
            ),
        )
        session.strokeTo(31f, 30f, pressure = 1f)
        session.endStroke()

        val center = session.pixels[30 * width + 30]
        assertEquals(
            "橡皮必须把中心**擦成全透明** ✗ —— 实测 ${"%08X".format(center)}" +
                "（`FFFFFFFF` = 把擦除当成了「画白色」✗，见 ㉙ 批第 5 条「没接」✓）",
            0,
            center,
        )
        var erased = 0
        for (p in session.pixels) if (alphaOf(p) == 0) erased++
        assertTrue("擦过的地方必须真的透明（实测 $erased 个透明像素 ✗）", erased > 0)

        // 撤销：必须**逐位**还原成那一整块蓝 ✓（真实触达 bbox 与 before/after 同口径 ✓）
        assertTrue("这一笔该是可撤销的", session.canUndo)
        assertTrue(session.undo())
        assertTrue(
            "撤销必须**逐位**还原（一个像素都不许错位 ✗）—— 实测第一个不对的像素是 " +
                "${session.pixels.indexOfFirst { it != base }} ✓",
            session.pixels.all { it == base },
        )
    }

    @Test
    fun direct_layer_paint_undo_is_bit_exact() {
        val width = 90
        val height = 90
        val session = ImageEditSession(width, height, IntArray(width * height))
        // ㉜a 起**只有**"直接画在层表面上"这一条路 ✓（`deferStrokeCommit` 那个开关已删 ✓）

        assertTrue(session.beginStroke(20f, 45f, StrokeSpec(brushPixels = 24, color = 0xFF112233.toInt())))
        assertTrue(session.strokeTo(70f, 45f, pressure = 1f))
        session.endStroke()

        val painted = session.pixels.count { alphaOf(it) > 0 }
        assertTrue("这一笔必须真的画上墨（实测 $painted 个像素）✗", painted > 0)
        assertTrue("这一笔该是可撤销的", session.canUndo)
        assertTrue(session.undo())
        assertTrue(
            "撤销必须**逐位**还原（真实触达 bbox 与 before/after 不同形状时会按错行距 ✗）",
            session.pixels.all { alphaOf(it) == 0 },
        )
    }
}
