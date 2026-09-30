package com.kallan.naistudio.desktop

import com.kallan.naistudio.models.BrushSpec
import com.kallan.naistudio.models.ImageEditSession
import com.kallan.naistudio.models.StrokeMode
import com.kallan.naistudio.models.StrokeSpec
import com.kallan.naistudio.screens.BrushPresets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **B 方案（2026-09-21）：笔画期间直接落表面 ⇒ 落笔即见墨** ✓
 *
 * 用户口径：「**这个版本在桌面备份一个文件夹，然后 ab 各做一个**」✓ ——
 * 本文件钉的是 **B 那一档的语义** ✓：
 *
 *  · `BrushSpec.directSurfaceStroke = true` ⇒
 *    走 `strokeCoverage` 的档（`wc3/wc4` ✓）**不再攒** ✗ ⇒ 每颗笔尖**当场落表面** ✓
 *    ⇒ **喂第一个点之后表面上就有墨** ✓（零滞后 ✓）；
 *  · 关掉（默认 = A ✓）⇒ 回到"先攒后合"✓ ⇒ 没合之前表面**干干净净** ✓。
 *
 * ⚠️ **代价如实**（不是判据，是事实 ✗）：B 走的是逐颗 `source-over` ✓
 * ⇒ `docs/65/66` 治好的"一个个圆"**会回来** ✗（相邻笔尖之间的周期性起伏 ✓）。
 * 所以 B 是"手感优先"的那一档 ✓，判据**不检查它连贯**（那本来就做不到 ✓，不该假装 ✓）。
 *
 * ⚠️ 本判据**两头都改全局静态开关** ⇒ 用 `@After` 复位 ✓
 *（不复位会污染同 JVM 里别的测试 ✗ —— 本仓库单测是同一个 Gradle worker ✓）。
 */
class DirectSurfaceStrokeSwitchTest {

    private val w = 420
    private val h = 260
    private val lineY = 130f

    private fun spec() = BrushPresets.first { it.spec.strokeCoverage }.spec
        .copy(spacing = 0.04f, sizeJitter = 0f, scattering = 0f)

    @After
    fun restoreDefault() {
        BrushSpec.directSurfaceStroke = false
    }

    private fun run(): ImageEditSession {
        val s = ImageEditSession(w, h, IntArray(w * h))
        s.beginStroke(
            20f, lineY,
            StrokeSpec(
                mode = StrokeMode.PAINT, brushPixels = 48, color = 0xFF22262E.toInt(),
                brush = spec(), minRadiusRatio = 0.15f, pressureCurve = 1f, seed = 11L,
            ),
            1f,
        )
        var x = 30f
        while (x <= 300f) {
            s.strokeTo(x, lineY, 1f)
            x += 6f
        }
        return s
    }

    private fun inked(s: ImageEditSession): Int {
        var n = 0
        for (p in s.pixels) if (((p ushr 24) and 0xFF) > 0) n++
        return n
    }

    /** B：喂完点（还没抬笔）表面**就该有墨** ✓ —— 这就是"落笔即见墨" ✓。 */
    @Test
    fun with_direct_surface_the_ink_is_on_the_canvas_before_the_stroke_ends() {
        BrushSpec.directSurfaceStroke = true
        val s = run()
        val beforeEnd = inked(s)
        assertTrue("B 方案下抬笔之前表面上就必须有墨（实测 $beforeEnd）", beforeEnd > 0)
        s.endStroke()
        assertEquals("抬笔不该再改变墨量（B 不攒，抬笔无合成）", beforeEnd, inked(s))
    }

    /** A（默认）：没合过之前表面**一个像素都没有** ✓ —— 这就是"先出一排点"的机制 ✓。 */
    @Test
    fun with_the_default_mode_nothing_reaches_the_canvas_until_composited() {
        BrushSpec.directSurfaceStroke = false
        val s = run()
        assertEquals("默认（A）下没合过之前表面不该有墨", 0, inked(s))
        assertTrue("合一次之后才出现墨", s.compositeCoverageFrameTick())
        assertTrue("合过之后表面必须有墨", inked(s) > 0)
        s.endStroke()
    }

    /** 开关**默认必须是 A** ✓（不许悄悄把默认改成 B ✗ —— 那会全局改变观感 ✓）。 */
    @Test
    fun the_switch_defaults_to_mode_a() {
        // `@After` 已复位，这里读的就是字段的初始值口径 ✓
        assertEquals("`directSurfaceStroke` 默认必须是 false（= A 方案）", false, BrushSpec.directSurfaceStroke)
    }

    /**
     * **系统属性真的能切过去** ✓ —— 这一条钉的是"B 那一份 cfg 到底生不生效"✓。
     *
     * ⚠️ 交付的 B 那一份靠 `NAI Studio.cfg` 里一行
     * `java-options=-Dnai.brush.directSurface=true` ✓ —— 这里就用**同一个属性名**验 ✓
     *（名字写错 / 读法写错的话这条立刻红 ✗，不会等到用户实机才发现 ✗）。
     */
    @Test
    fun the_system_property_switches_to_mode_b() {
        System.setProperty("nai.brush.directSurface", "true")
        try {
            BrushSpec.readDirectSurfaceFromSystemProperty()
            assertTrue("设了 -Dnai.brush.directSurface=true 之后必须切到 B", BrushSpec.directSurfaceStroke)
        } finally {
            System.clearProperty("nai.brush.directSurface")
            BrushSpec.directSurfaceStroke = false
        }
    }

    /** 属性的**其它值**都不算 B ✓（只有 `true` 才算 ✓ —— 防"随便写点什么都开" ✗）。 */
    @Test
    fun only_the_literal_true_enables_mode_b() {
        for (v in listOf("false", "1", "yes", "")) {
            System.setProperty("nai.brush.directSurface", v)
            BrushSpec.readDirectSurfaceFromSystemProperty()
            assertEquals("属性值 '$v' 不该切到 B", false, BrushSpec.directSurfaceStroke)
        }
        System.clearProperty("nai.brush.directSurface")
        BrushSpec.directSurfaceStroke = false
    }
}
