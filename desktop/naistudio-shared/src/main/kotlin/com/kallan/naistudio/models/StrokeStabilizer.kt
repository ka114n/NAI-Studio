package com.kallan.naistudio.models

/**
 * **笔画稳定器（EMA）** —— 第 ㉚ 批 · 照 `docs/brush-lab-simple.html` 的**输入侧**逐行搬 ✓。
 *
 * ## 网页那边是怎么写的（原文，别自己发明 ✗）
 *
 * ```js
 * // :156   默认值（面板上的「稳定器」滑杆，0..95%）
 * color: "#1a1a1a", eraser: false, stabilize: 35,
 *
 * // :729   pointerdown —— 落笔时把 smooth **种子化**成落笔点
 * drawing = true; smooth = { x: p[0], y: p[1] };
 * // :740   落笔那一颗笔尖用的就是 seed 点（smooth 还没被 EMA 动过 ✓）
 * liveDab(strokeState, smooth.x, smooth.y, r, pv);
 *
 * // :747-755  pointermove —— **每个采样点都过一次 EMA**，再喂给 walkTo
 * const k = 1 - (P.stabilize / 100);
 * const evs = (typeof e.getCoalescedEvents === "function" && e.getCoalescedEvents().length) ? e.getCoalescedEvents() : [e];
 * for (const ev of evs){
 *   const dp = docXY(ev);
 *   smooth.x += (dp[0] - smooth.x) * k;
 *   smooth.y += (dp[1] - smooth.y) * k;
 *   const pp = pressureOf(ev);
 *   liveDab(strokeState, smooth.x, smooth.y, r, pp);
 * }
 * ```
 *
 * ⇒ 语义就三条，一个字都不多：
 *  1. `k = 1 - stabilize/100`（35% ⇒ **k = 0.65** ✓）；
 *  2. `smooth += (p - smooth) * k` —— **一阶低通 / 指数滑动平均** ✓；
 *  3. 种子 = 落笔点 ✓，**落笔那一颗不吃 EMA** ✓，**每个采样点都过**（包括同一帧里
 *     `getCoalescedEvents()` 拆出来的每一个 ✓）。
 *
 * ## ⚠️ 它在整条链里的位置（放错地方就是另一个东西 ✗）
 *
 * 它是**输入侧**的变换 ✓ —— 网页里它在 `pointermove` 里、在 `walkTo()` **之前** ✓。
 * 所以本仓库也放在**会话入口**（`ImageEditSession.beginStroke/strokeTo` ✓），
 * 而**不是**放进走位（`walkTo` 那一侧 ✓）。
 *
 * ⚠️ 而且它**默认关**（`StrokeSpec.stabilizePercent = 0f` ✓）：㉗ 批抽的网页真值
 * （`docs/brush-lab-simple-truth.json`）是**没有稳定器**的那条路抽出来的 ✓ ——
 * 默认开着的话判据①（928 颗）当场就变了 ✗。界面那条路**按网页默认档开 35%** ✓
 * （见 `AppState.comicPaintStabilize` ✓），真值对照那条路保持关 ✓ —— 两边各说各话、都对 ✓。
 *
 * ## 数值例子（单测就按这个钉 ✓）
 *
 * `stabilize = 35` ⇒ `k = 0.65`；seed = (0,0)，来一个 (100, 0)：
 * `smooth.x = 0 + (100 - 0) * 0.65 = 65` ✓（不是 100 ✗、也不是 35 ✗）。
 */
class StrokeStabilizer(
    stabilizePercent: Float = DEFAULT_STABILIZE_PERCENT,
) {

    /** 稳定器强度（网页 `stabilize` ✓，0..95 ✓）。0 = **完全不平滑**（= 老行为 ✓）。 */
    var stabilizePercent: Float = clampPercent(stabilizePercent)
        set(value) {
            field = clampPercent(value)
        }

    /** 网页 `const k = 1 - (P.stabilize / 100)` ✓。 */
    val k: Float get() = 1f - stabilizePercent / 100f

    /** 当前平滑点（页面坐标 ✓）—— 没种子化时是 (0,0) ✓。 */
    var smoothX: Float = 0f
        private set

    var smoothY: Float = 0f
        private set

    /** 种子化过没有（= 这一笔落笔了 ✓）。 */
    var seeded: Boolean = false
        private set

    /** 这一笔一共过了几个采样点（诊断 / 实测输入率用 ✓ —— 不参与画面 ✓）。 */
    var points: Int = 0
        private set

    /** 网页 `:729/:776`：落笔那一刻 `smooth = {x: p[0], y: p[1]}` ✓。 */
    fun seed(x: Float, y: Float) {
        smoothX = if (x.isFinite()) x else 0f
        smoothY = if (y.isFinite()) y else 0f
        seeded = true
        points = 0
    }

    /** 一笔收口（`ImageEditSession.endStroke` ✓）—— 下次落笔必须重新种子化 ✗。 */
    fun reset() {
        seeded = false
        smoothX = 0f
        smoothY = 0f
        points = 0
    }

    /**
     * 网页 `:751-752`：把**一个采样点**过一遍 EMA，然后 [smoothX]/[smoothY] 就是喂给走位的点 ✓。
     *
     * 刻意**不返回 Pair / Offset** ✗ —— 这一笔要过几百上千个点，每点分配一个对象是白烧 ✓
     *（用户点名的是手感 ✓）。读 [smoothX]/[smoothY] 即可 ✓。
     */
    fun next(x: Float, y: Float) {
        if (!seeded) {
            seed(x, y)
            return
        }
        val px = if (x.isFinite()) x else smoothX
        val py = if (y.isFinite()) y else smoothY
        val factor = k
        smoothX += (px - smoothX) * factor
        smoothY += (py - smoothY) * factor
        points++
    }

    companion object {

        /** 网页 `:156` 的默认档：**35%** ✓（⇒ `k = 0.65` ✓）。 */
        const val DEFAULT_STABILIZE_PERCENT: Float = 35f

        /** 网页滑杆范围：`:670` `min: 0, max: 95, step: 1` ✓。 */
        const val MAX_STABILIZE_PERCENT: Float = 95f

        /** 收敛（NaN / 越界 → 安全值 ✓）。 */
        fun clampPercent(value: Float): Float =
            if (!value.isFinite()) 0f else value.coerceIn(0f, MAX_STABILIZE_PERCENT)
    }
}
