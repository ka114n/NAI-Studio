package com.kallan.naistudio.models

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max

/**
 * **出图积分的预测**（用户 2026-09-24：「参考这个仓库，给**双端**加上**积分预测**功能，
 * 显示在**生成按钮**上，显示 **[数字] 闪电图标**」✓）。
 *
 * ## 公式从哪来（**逐条照抄参考仓库** ✓ 不是自创 ✗）
 *
 * 参考：[Aaalice233/Aaalice_NAI_Launcher](https://github.com/Aaalice233/Aaalice_NAI_Launcher)
 * 的 `lib/core/services/anlas_calculator.dart` ✓（外加它上层那个
 * `generation_anlas_estimator.dart` ✓ —— 那边负责把"几张 / 一批几张 / 订阅档"喂进来 ✓）。
 *
 * 三条**老规矩**（照抄时也要守住 ✗）：
 *  1. **服务端计费才是最终依据** ✓ —— 这里只做预测 ✓（参考仓库自己也是这么写的 ✓）；
 *     ⇒ 界面上要**如实**标注这是预估 ✓，别做成"承诺" ✗；
 *  2. 公式里的常数是**网页端的当前口径** ✓（2026-09 抓的 ✓）—— NAI 什么时候调价，
 *     这里就得跟着改 ✓（所以常数全部集中在本文件、各带一条注释 ✓）；
 *  3. **免费额度**（Opus + ≤28 步 + ≤1MP ✓）会抵掉**一张**的价钱 ✓（`nSamples - 1` ✓）——
 *     这是 V3/V4 的老口径 ✓；**V5 不一样** ✗（它走"免费池"，透支就不再抵 ✓，
 *     所以参考仓库拿 `opusQuotaExhausted` 挡住了 ✓，我们照做 ✓）。
 *
 * ## 用到哪儿
 *
 *  · 文生图 / 图生图 / 局部重绘 / 无限画布聚焦 → 规模按钮上那个「⚡ 数字」✓；
 *  · 漫画逐格 / 官方 2× 放大 / 导演工具**不走这儿** ✗（它们的计价是另一套 ✓，
 *    尤其放大是按"输入面积分档"✓，见 [novelAiUpscaleCost] ✓）。
 */
object AnlasCost {

    /** Opus 档的编号 ✓（参考仓库 `AnlasCalculator.opusTier` ✓）。 */
    const val OPUS_TIER = 3

    /** Opus 免费额度覆盖的最大面积 ✓（1024×1024 ✓）。 */
    const val OPUS_FREE_MAX_PIXELS = 1024 * 1024

    /** 单张的成本上限 ✓；超过就认为"这组参数算不出来"✓（参考仓库同口径 ✓）。 */
    const val MAX_PER_SAMPLE_COST = 140

    /** 算不出来的标记 ✓（`-3` ✓，与参考仓库的 `invalidCost` 一致 ✓）。 */
    const val INVALID = -3

    /** 面积系数 ✓（网页端口径 ✓）。 */
    private const val AREA_COEFFICIENT = 2.951823174884865e-6

    /** 面积 × 步数 的系数 ✓。 */
    private const val STEP_AREA_COEFFICIENT = 5.753298233447344e-7

    /** V5 那一代的模型倍率 ✓（参考仓库 `capabilities.anlasMultiplier` = 1.5 ✓）。 */
    private const val MODERN_MODEL_MULTIPLIER = 1.5

    /** 旧模型（V3/V4 以前）的指数估算 —— 参考仓库原样保留的那条 ✓（别拿现代公式回算旧模型 ✓）。 */
    private const val LEGACY_A = 15.266497014243718
    private const val LEGACY_B = 0.6326248927474729
    private const val LEGACY_C = 15.225164493059737
    private const val LEGACY_BASE_STEPS = 28.0

    /** 单张最低 2 点 ✓（参考仓库 `math.max(..., 2)` ✓）。 */
    private const val MIN_PER_SAMPLE = 2

    /**
     * **这张图大概要几点** ✓（一次算"一张"✓，直接的界面用它是够了 ✓）。
     *
     * @param width / height 请求尺寸 ✓（像素 ✓）
     * @param steps 步数 ✓
     * @param model 模型 id ✓（判"现代 / 旧"那一档 ✓，见 [usesModernFormula] ✓）
     * @param smea / smeaDyn 两个开关 ✓（倍率 1.0 / 1.2 / 1.4 ✓，参考仓库同口径 ✓）
     * @param strength 重绘强度 ✓（文生图传 1.0 ✓；图生图 / 局部重绘传各自的强度 ✓）
     * @param isOpus 是不是 Opus 档 ✓（**只有免费那一张**会抵掉 ✓）
     * @param opusQuotaExhausted V5 的免费池透支了没 ✓（透支就不再抵 ✓）
     * @param samples 一次要几张 ✓（默认 1 ✓）
     * @return 点数 ✓；[INVALID] = 这组参数算不出来 ✓（界面上**不显示** ✗ 别显示个负数 ✓）
     */
    fun estimate(
        width: Int,
        height: Int,
        steps: Int,
        model: String,
        smea: Boolean = false,
        smeaDyn: Boolean = false,
        strength: Double = 1.0,
        isOpus: Boolean = false,
        opusQuotaExhausted: Boolean = false,
        samples: Int = 1,
    ): Int {
        if (width <= 0 || height <= 0 || steps <= 0 || samples <= 0) return 0
        val perSample = perSampleCost(width, height, steps, model, smea, smeaDyn, strength)
        if (perSample == INVALID) return INVALID
        // 免费额度只抵**一张** ✓（参考仓库：`nSamples - opusDiscount` ✓）
        val free = isOpusFree(isOpus, steps, width * height) && !quotaBlocked(model, opusQuotaExhausted)
        val billable = max(samples - if (free) 1 else 0, 0)
        return perSample * billable
    }

    /**
     * **一批几张**（`batchSize > 1` 时只有**第一张**吃订阅档 ✓）——
     * 参考仓库 `calculateRequestCost` 里那个循环的逐条翻译 ✓。
     *
     * ⚠️ 本项目目前一次请求只发一张（`n_samples = 1` ✓，批量是"排队跑多次"✓）⇒
     * 界面走 [estimate] 就够了 ✓；这个方法留着是**把参考口径补全** ✓，
     * 将来真做一次多张时直接用 ✓（不用再去翻那边的源码 ✓）。
     */
    fun estimateRequest(
        width: Int,
        height: Int,
        steps: Int,
        model: String,
        batchCount: Int,
        batchSize: Int,
        smea: Boolean = false,
        smeaDyn: Boolean = false,
        strength: Double = 1.0,
        isOpus: Boolean = false,
        opusQuotaExhausted: Boolean = false,
    ): Int {
        if (batchCount <= 0 || batchSize <= 0) return 0
        var single = 0
        for (index in 0 until batchSize) {
            val cost = estimate(
                width = width,
                height = height,
                steps = steps,
                model = model,
                smea = smea,
                smeaDyn = smeaDyn,
                strength = strength,
                isOpus = isOpus && index == 0,
                opusQuotaExhausted = opusQuotaExhausted,
                samples = 1,
            )
            if (cost == INVALID) return INVALID
            single += cost
        }
        return single * batchCount
    }

    /**
     * **单张的成本** ✓（参考仓库 `calculateFromValues` ✓）。
     *
     * 现代模型（V5 ✓）：`ceil(面积项 + 面积×步数项)` → ×SMEA 倍率 → ×模型倍率 ✓；
     * 旧模型：参考仓库保留的那条指数估算 ✓（**别拿现代公式回算旧请求** ✗）。
     * 最后 × 重绘强度 ✓、向上取整 ✓、**最低 2 点** ✓、超过 [MAX_PER_SAMPLE_COST] 判无效 ✓。
     */
    private fun perSampleCost(
        width: Int,
        height: Int,
        steps: Int,
        model: String,
        smea: Boolean,
        smeaDyn: Boolean,
        strength: Double,
    ): Int {
        val pixels = width.toDouble() * height.toDouble()
        val perSample = if (usesModernFormula(model)) {
            val base = ceil(AREA_COEFFICIENT * pixels + STEP_AREA_COEFFICIENT * pixels * steps)
            val smeaFactor = if (!smea) 1.0 else if (!smeaDyn) 1.2 else 1.4
            base * smeaFactor * MODERN_MODEL_MULTIPLIER
        } else {
            (LEGACY_A * exp(pixels / 1024.0 / 1024.0 * LEGACY_B) - LEGACY_C) *
                steps / LEGACY_BASE_STEPS
        }
        val cost = max(ceil(perSample * strength), MIN_PER_SAMPLE.toDouble())
        if (cost > MAX_PER_SAMPLE_COST) return INVALID
        return cost.toInt()
    }

    /** Opus 免费三条件 ✓：是 Opus + ≤28 步 + ≤1MP ✓（参考仓库 `_isOpusFree` ✓）。 */
    fun isOpusFree(isOpus: Boolean, steps: Int, pixels: Int): Boolean =
        isOpus && steps <= 28 && pixels <= OPUS_FREE_MAX_PIXELS

    /**
     * **这个模型走现代公式吗** ✓（V5 系列 ✓ —— 参考仓库是按"能力表"查的 ✓，
     * 我们这边没有那张表 ✓，就按 id 前缀判 ✓；新模型出来时改这一处 ✓）。
     */
    fun usesModernFormula(model: String): Boolean = model.startsWith("nai-diffusion-5")

    /** V5 的免费池透支时不再抵扣 ✓（参考仓库 `hasOpusUsageLimit && opusQuotaExhausted` ✓）。 */
    private fun quotaBlocked(model: String, exhausted: Boolean): Boolean =
        exhausted && usesModernFormula(model)

    /**
     * **官方 2× 放大的价钱** ✓（参考仓库 `calculateNovelAiUpscaleCost` ✓）——
     * 网页端按**输入面积分档** ✓，放大倍数不参与 ✓：
     *  · Opus 且输入 ≤ 640×640 ⇒ **免费** ✓；
     *  · ≤ 1MP ⇒ 1 点 ✓ / ≤ 1.75MP ⇒ 2 点 ✓ / ≤ 2.45MP ⇒ 3 点 ✓ / ≤ 3MP ⇒ 4 点 ✓；
     *  · 再大 ⇒ 超出网页端最高分档 ✓（[INVALID] ✓ 如实说"算不出来"✗ 别报个假的 ✓）。
     */
    fun novelAiUpscaleCost(
        inputWidth: Int,
        inputHeight: Int,
        scale: Int,
        isOpus: Boolean,
    ): Int {
        if (inputWidth <= 0 || inputHeight <= 0 || scale <= 0) return INVALID
        val pixels = inputWidth * inputHeight
        if (isOpus && pixels <= 640 * 640) return 0
        return when {
            pixels <= 1048576 -> 1
            pixels <= 1747627 -> 2
            pixels <= 2446678 -> 3
            pixels <= 3145728 -> 4
            else -> INVALID
        }
    }
}
