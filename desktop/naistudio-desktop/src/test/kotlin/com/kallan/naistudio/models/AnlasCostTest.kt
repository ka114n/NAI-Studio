package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **积分预测**的判据（用户 2026-09-24：「给双端加上积分预测功能，显示在生成按钮上，
 * 显示 [数字] 闪电图标」✓，公式照 `Aaalice233/Aaalice_NAI_Launcher` 抄 ✓）。
 *
 * 这一族钉的是**四条"错了会误导用户花钱"的事** ✗：
 *  1. 现代模型（V5）的公式：`ceil(面积项 + 面积×步数项) × SMEA 倍率 × 模型倍率` ✓；
 *  2. **免费额度只抵一张** ✓（Opus + ≤28 步 + ≤1MP ✓）—— 抵多了会让人以为"越多越便宜" ✗；
 *  3. V5 的免费池**透支后不再抵** ✓（参考仓库的 `opusQuotaExhausted` ✓）；
 *  4. 官方 2× 放大是**按输入面积分档** ✓（不是按模型公式 ✓）。
 *
 * ⚠️ 期望值是**按公式手算**出来的 ✓（不是拿实现跑一遍抄回来的 ✗）——
 * 抄回来的期望值只能证明"代码没变"，证明不了"算得对" ✗。
 */
class AnlasCostTest {

    /** 832×1216、28 步、V5、无 SMEA：`ceil(2.951823174884865e-6*1011712 + 5.753298233447344e-7*1011712*28)`。 */
    @Test
    fun `V5 常规尺寸 —— 与公式手算一致`() {
        val pixels = 832.0 * 1216.0
        val base = kotlin.math.ceil(
            2.951823174884865e-6 * pixels + 5.753298233447344e-7 * pixels * 28,
        )
        val expected = kotlin.math.ceil(base * 1.0 * 1.5).toInt()
        val got = AnlasCost.estimate(
            width = 832,
            height = 1216,
            steps = 28,
            model = "nai-diffusion-5-full",
        )
        assertEquals(expected, got)
    }

    /** SMEA 两档倍率：开 SMEA = ×1.2 ✓；再开 Dyn = ×1.4 ✓（参考仓库同口径 ✓）。 */
    @Test
    fun `SMEA 的 1_2 与 1_4 两档`() {
        val plain = AnlasCost.estimate(1024, 1024, 28, "nai-diffusion-5-full")
        val smea = AnlasCost.estimate(1024, 1024, 28, "nai-diffusion-5-full", smea = true)
        val dyn = AnlasCost.estimate(
            1024, 1024, 28, "nai-diffusion-5-full", smea = true, smeaDyn = true,
        )
        assertTrue("开了 SMEA 应该更贵（$smea vs $plain）", smea > plain)
        assertTrue("再开 Dyn 应该更贵（$dyn vs $smea）", dyn > smea)
        // 倍率关系：1.4/1.2 ≈ 1.1667 ⇒ 取整后大致同比例 ✓（给一格容差 ✓）
        val ratio = dyn.toDouble() / smea.toDouble()
        assertTrue("1.4/1.2 的比例不对：$ratio", ratio in 1.10..1.24)
    }

    /** 步数越多越贵 ✓（面积项之外还有"面积×步数"那一项 ✓）。 */
    @Test
    fun `步数越多越贵`() {
        val s20 = AnlasCost.estimate(1024, 1024, 20, "nai-diffusion-5-full")
        val s28 = AnlasCost.estimate(1024, 1024, 28, "nai-diffusion-5-full")
        val s50 = AnlasCost.estimate(1024, 1024, 50, "nai-diffusion-5-full")
        assertTrue(s20 < s28)
        assertTrue(s28 < s50)
    }

    /** **Opus 免费那一张**：同一组参数，非 Opus 收 N 点、Opus 收 (N-1)×单张 ✓。 */
    @Test
    fun `Opus 免费额度只抵一张`() {
        // 1024×1024 / 28 步 = 正好卡在免费条件上（≤28 步 ✓ ≤1MP ✓）
        val two = AnlasCost.estimate(1024, 1024, 28, "nai-diffusion-5-full", samples = 2)
        val twoOpus = AnlasCost.estimate(
            1024, 1024, 28, "nai-diffusion-5-full", isOpus = true, samples = 2,
        )
        val one = AnlasCost.estimate(1024, 1024, 28, "nai-diffusion-5-full")
        assertEquals("两张 = 单张 ×2", one * 2, two)
        assertEquals("Opus 两张只收一张的钱", one, twoOpus)
        // 一张时 Opus 免费 ⇒ 0
        assertEquals(
            0,
            AnlasCost.estimate(1024, 1024, 28, "nai-diffusion-5-full", isOpus = true),
        )
    }

    /** **超出免费条件就不免了** ✓：29 步、或面积 > 1MP ✓。 */
    @Test
    fun `超出免费条件 —— 照样收费`() {
        assertTrue(
            AnlasCost.estimate(1024, 1024, 29, "nai-diffusion-5-full", isOpus = true) > 0,
        )
        assertTrue(
            AnlasCost.estimate(1216, 1216, 28, "nai-diffusion-5-full", isOpus = true) > 0,
        )
    }

    /** V5 的免费池**透支**后不再抵 ✓（参考仓库 `opusQuotaExhausted` ✓）。 */
    @Test
    fun `V5 免费池透支 —— 不再抵扣`() {
        val free = AnlasCost.estimate(1024, 1024, 28, "nai-diffusion-5-full", isOpus = true)
        val blocked = AnlasCost.estimate(
            1024, 1024, 28, "nai-diffusion-5-full",
            isOpus = true,
            opusQuotaExhausted = true,
        )
        assertEquals("没透支时免费", 0, free)
        assertTrue("透支了要正常收费", blocked > 0)
    }

    /** 图生图 / 局部重绘的**强度**直接乘在单张成本上 ✓（越接近 1 越贵 ✓）。 */
    @Test
    fun `重绘强度越低越便宜`() {
        val full = AnlasCost.estimate(1024, 1024, 28, "nai-diffusion-5-inpainting", strength = 1.0)
        val half = AnlasCost.estimate(1024, 1024, 28, "nai-diffusion-5-inpainting", strength = 0.5)
        assertTrue("强度 0.5 应该比 1.0 便宜（$half vs $full）", half < full)
    }

    /** 旧模型走**指数估算**那条 ✓，而且**不套**现代公式的模型倍率 ✓。 */
    @Test
    fun `旧模型走指数估算`() {
        val legacy = AnlasCost.estimate(832, 1216, 28, "nai-diffusion-3")
        // 手算：a*exp(p/1024/1024*b) - c，再 × 28/28
        val p = 832.0 * 1216.0
        val raw = 15.266497014243718 *
            kotlin.math.exp(p / 1024.0 / 1024.0 * 0.6326248927474729) - 15.225164493059737
        assertEquals(kotlin.math.ceil(raw).toInt(), legacy)
    }

    /** 参数不合法 ⇒ **0 / 无效** ✓，**绝不能**返回负数让界面显示出来 ✗。 */
    @Test
    fun `参数不合法 —— 不返回负数`() {
        assertEquals(0, AnlasCost.estimate(0, 1024, 28, "nai-diffusion-5-full"))
        assertEquals(0, AnlasCost.estimate(1024, 1024, 0, "nai-diffusion-5-full"))
        assertEquals(0, AnlasCost.estimate(1024, 1024, 28, "nai-diffusion-5-full", samples = 0))
    }

    /** **官方 2× 放大**按输入面积分档 ✓：Opus ≤640×640 免费 ✓，其余四档 1/2/3/4 ✓。 */
    @Test
    fun `官方放大 —— 按输入面积分档`() {
        assertEquals(0, AnlasCost.novelAiUpscaleCost(640, 640, 2, isOpus = true))
        assertEquals(1, AnlasCost.novelAiUpscaleCost(640, 640, 2, isOpus = false))
        assertEquals(1, AnlasCost.novelAiUpscaleCost(1024, 1024, 2, isOpus = true))
        assertEquals(2, AnlasCost.novelAiUpscaleCost(1280, 1280, 2, isOpus = true))
        assertEquals(3, AnlasCost.novelAiUpscaleCost(1536, 1536, 2, isOpus = true))
        assertEquals(4, AnlasCost.novelAiUpscaleCost(1792, 1728, 2, isOpus = true))
        // 超出最高分档 ⇒ 无效（如实说"算不出来" ✓，别报个假的 ✓）
        assertEquals(AnlasCost.INVALID, AnlasCost.novelAiUpscaleCost(2048, 2048, 2, isOpus = true))
        assertEquals(AnlasCost.INVALID, AnlasCost.novelAiUpscaleCost(0, 1024, 2, isOpus = true))
    }
}
