package com.kallan.naistudio.models

/**
 * **一帧内到达的 motion 采样点先攒起来，在帧边界"按到达顺序、一个不少"地喂给引擎**
 * （第 ㉝ 批 ① · 就是网页 `PointerEvent.getCoalescedEvents()` 的等价物 ✓）。
 *
 * ## 为什么需要它（用户 2026-09-21 实机报的「**不够跟手**」✗）
 *
 * 实机 `[PenDab]` 日志里 `ptPerSec` 只有 **21~48 点/秒** ✗，而网页靠
 * `PointerEvent.getCoalescedEvents()`（`docs/brush-lab-simple.html:796` ✓）把**一帧内的点全拿到**
 * （几百~上千/秒 ✓）。Compose 桌面版**没有**对应物 ✗ ⇒ 只能自己攒 ✓。
 *
 * ## 关键的一条：不是"少喂点"，是"**别在事件回调里干重活**"（这一条是机制，不是口味 ✓）
 *
 * 原来两条输入通道（数位板 `compose-stylus` 那条回调 / 鼠标触摸那条 `awaitPointerEvent` 循环 ✓）
 * 都是**一收到 motion 就当场 `strokeTo`**：那一刻要算笔尖、要走位、要 `drawOval`、要回读一块像素
 * （实测 A4 上 127~217 µs/颗 ✓，一笔几十毫秒 ✗）—— 事件回调里干这么重的话，
 * **Windows 的消息队列来不及抽干** ⇒ 同一帧里 OS 会把后面的 `WM_MOUSEMOVE` **合掉** ✗
 * （位置直接丢，找回不来 —— 网页的 `getCoalescedEvents()` 也找不回被 OS 合掉的那些 ✓，
 *  Chromium 拿到那么多点只是因为它**渲染与消息泵分开**、泵一直很轻 ✓）。
 *
 * ⇒ 这里把回调那条路变成 **O(1) 的"往数组里塞一个点"** ✓（`offer` ✓），
 * 攒下的点等**帧边界**一次抽干、按顺序喂引擎 ✓（`drainTo` ✓）——
 * 消息泵因此一直很轻 ⇒ **同一帧能收到的事件数自然上去了** ✓（实机日志里的
 * `inputPts` / `ptPerSec` / `inputPtsPerFrame` 就是量这件事的 ✓）。
 *
 * ## 三条硬口径（判据钉着 ✓，见 `CoalescedInputSamplingTest` ✓）
 *
 *  1. **一个都不许丢** ✓：`drainTo` 把攒下的点**全部**按到达顺序喂一遍，
 *     **不是只喂最后一个** ✗（只喂最后一个 = 一帧只走一段 = 快速划动时笔迹变成折线 ✗）；
 *  2. **压力逐点保留** ✓：每个点自己带自己的压力（一笔之内随力度变粗细那条 ✓）；
 *  3. **顺序 = 到达顺序** ✓（先到的先喂 —— 反了的话笔迹会来回折 ✗）。
 *
 * ## ⚠️ 第四条（第 ㉝① 回归修复 ✓）：**笔优先**
 *
 * 用户 2026-09-21 实机报：「**数位板画没有压感**」✗ —— 这是把两条通道合成一个攒点器之后
 * **暴露出来**的一条（`ComicModeScreen.kt:1932` 那个"笔按着就让开"的判断**只在手势开始那一刻**
 * 生效 ✓，所以"鼠标那条手势先开、笔再按下去"那一种顺序下，鼠标那条会在整笔里继续喂点 ✓）。
 *
 * **Windows 会把同一支笔再提升成一遍鼠标事件** ✓（本仓库早就知道这条：
 * `ComicModeScreen` 里"两条通道先后顺序不保证"那段注释 ✓），
 * 而那串点的压力**恒为 1** ✗（AWT 鼠标管线把 pressure / tilt 全丢了 ✓）、坐标还跟笔那串几乎重合
 * ⇒ 喂进引擎就是把真压感冲掉 ✓（后到的赢 ✓）。
 *
 * ⇒ 规则（[penDriven] ✓ + [offer] 里的来源标记 ✓）：
 *  · 这一笔是**笔按下**开的 ⇒ **鼠标那条送来的点全部丢掉** ✓（计数进 [droppedMouse] ✓）；
 *  · 兜底：某一帧里**同时**有笔的点和鼠标的点（`penDriven` 还没置上那种顺序 ✓）⇒
 *    **[drainTo] 只喂笔的那几个** ✓。
 *
 * ⇒ 判据：`CoalescedInputSamplingTest`（同一帧 pen(0.42) + 鼠标(1.0) ⇒ 引擎只收到 **0.42** ✓；
 * 反例：只喂鼠标 ⇒ 1.0 ✓）；日志字段 `inputDroppedMouse=` ✓。
 *
 * ⚠️ 这一类是**纯 Kotlin**（不碰 Compose ✓）⇒ 单测可以直接"模拟一帧内到达 8 个点" ✓，
 * 不需要真窗口、真帧时钟 ✓。
 *
 * ⚠️ 计数只用于**日志与探针** ✓（`[PenDab]` 那条汇总 ✓），**不参与画面、不进像素判据** ✗。
 */
class StrokeInputCoalescer {

    // 攒点的三个数组（同一个下标 = 同一个采样点 ✓）—— 稳态下**零分配** ✓
    private var xs = FloatArray(INITIAL_CAPACITY)
    private var ys = FloatArray(INITIAL_CAPACITY)
    private var pressures = FloatArray(INITIAL_CAPACITY)

    /** 每个点是**谁送来的** ✓（true = 数位板那条 ✓、false = 鼠标 / 触摸那条 ✓）—— 笔优先要用 ✓。 */
    private var fromPenFlags = BooleanArray(INITIAL_CAPACITY)
    private var count = 0

    /**
     * **这一笔是不是"笔在画"** ✓（笔那条通道按下的那一刻置 true ✓，见 [beginStroke] ✓）。
     *
     * 为真时**鼠标那条送来的点会被直接丢掉** ✓ —— 理由见类说明里"笔优先"那一节 ✓。
     */
    var penDriven: Boolean = false
        private set

    /** 一共攒进来过几个点（= 事件回调真的被调用了几次 ✓）。 */
    var offered: Int = 0
        private set

    /**
     * **被丢掉的"鼠标那条"的点数**（第 ㉝① 回归修复 ✓）——
     * 数位板一笔里 Windows 会把同一串移动**再提升成一遍鼠标事件** ✓，
     * 那串点压力恒为 1 ✗ ⇒ 混进来就是"数位板画没有压感" ✓。这个计数就是"丢了多少"的证据 ✓
     * （进 `[PenDab]` 日志的 `inputDroppedMouse=` ✓）。
     */
    var droppedMouse: Int = 0
        private set

    /** 一共真的喂给引擎几个点（**正常情况必须等于 [offered] − [droppedMouse]** ✓）。 */
    var fed: Int = 0
        private set

    /** 帧边界去了几次（= `drainTo` 被调了几次 ✓，空帧也算 ✓）。 */
    var drainCalls: Int = 0
        private set

    /** 其中**真的有点**的帧数（算"每帧几个点"的分母 ✓）。 */
    var pointFrames: Int = 0
        private set

    /** 最后一帧攒了几个点（日志里 `inputLastFramePts=` ✓ —— 一眼看出"一帧一个点"还是"一帧一把点" ✓）。 */
    var lastFramePoints: Int = 0
        private set

    /** 这一笔里**最挤的那一帧**攒了几个点（日志里 `inputMaxFramePts=` ✓）。 */
    var peakFramePoints: Int = 0
        private set

    /** 现在还攒着几个点没喂（抬笔前那一瞬间是 0 ✓ —— 见 `AppState.endComicPaintStroke` ✓）。 */
    val pending: Int get() = count

    /**
     * **每一笔开头调一次** ✓：记下"这一笔是谁在画" ✓ + 把计数归零 ✓（日志里那几个数才是"这一笔"的 ✓）。
     *
     * @param fromPen true = 这一笔是**数位板**按下的 ✓（笔优先规则从此生效 ✓）
     */
    fun beginStroke(fromPen: Boolean) {
        penDriven = fromPen
        reset()
    }

    /** 兼容老调用点（只归零、不改"谁在画"这个判断 ✓）。 */
    fun reset() {
        count = 0
        offered = 0
        fed = 0
        droppedMouse = 0
        drainCalls = 0
        pointFrames = 0
        lastFramePoints = 0
        peakFramePoints = 0
    }

    /**
     * 攒一个采样点 ✓（**不立刻喂引擎** ✗ —— 这是这一整类存在的意义 ✓）。
     *
     * @param x/y 已经换算成**纸面（原图）像素**的坐标 ✓（两条通道各自换算完再进来 ✓）
     * @param pressure 这一点自己的**笔压** 0..1 ✓（逐点保留 ✓；鼠标 / 触摸给 1f ✓）
     * @param fromPen 这一点是**数位板那条**送来的吗 ✓（默认 false = 鼠标 / 触摸 ✓）
     */
    fun offer(x: Float, y: Float, pressure: Float = 1f, fromPen: Boolean = false) {
        // ---- 笔优先（第 ㉝① 回归修复 ✓）：这一笔是笔在画 ⇒ 鼠标那条（含被提升出来的那串）一律丢 ✗ ----
        // 为什么必须丢而不是"留着让引擎自己判"：那串点的压力**恒为 1** ✗（AWT 的鼠标管线把
        // pressure / tilt 全丢了 ✓），坐标还跟笔那串几乎重合 ⇒ 喂进去只会把真压感冲掉 ✓
        // （用户 2026-09-21 报的「**数位板画没有压感**」就是它 ✓）。
        if (penDriven && !fromPen) {
            droppedMouse++
            return
        }
        if (count == xs.size) grow()
        xs[count] = x
        ys[count] = y
        pressures[count] = if (pressure.isFinite()) pressure.coerceIn(0f, 1f) else 1f
        fromPenFlags[count] = fromPen
        count++
        offered++
    }

    /**
     * **帧边界**：把攒下的点**按到达顺序一次喂完** ✓。
     *
     * ⚠️ 还有一条**兜底**（第 ㉝① 回归修复 ✓）：万一这一帧里**同时**有笔的点和鼠标的点
     *（`penDriven` 还没来得及置上 —— 比如"鼠标那条先落笔、笔再按下去"那一种顺序 ✓），
     * 那么**只喂笔的那几个** ✓（鼠标那几个丢掉 + 计数 ✓）——
     * "同一帧里笔和鼠标都有点"这件事本身已经说明这一帧是笔在画 ✓。
     *
     * @return 这一帧喂了几个（0 = 这一帧没有新点 ✓ —— 正常 ✓）
     */
    fun drainTo(consumer: (x: Float, y: Float, pressure: Float) -> Unit): Int {
        drainCalls++
        val n = count
        if (n == 0) {
            lastFramePoints = 0
            return 0
        }
        var penCount = 0
        for (i in 0 until n) if (fromPenFlags[i]) penCount++
        val penOnly = penCount > 0
        var fedThisFrame = 0
        for (i in 0 until n) {
            if (penOnly && !fromPenFlags[i]) {
                // 兜底丢掉的那些**也算进同一个计数** ✓（口径：`fed = offered - droppedMouse` ✓）
                droppedMouse++
                continue
            }
            consumer(xs[i], ys[i], pressures[i])
            fedThisFrame++
        }
        count = 0
        lastFramePoints = fedThisFrame
        if (fedThisFrame == 0) return 0
        pointFrames++
        if (fedThisFrame > peakFramePoints) peakFramePoints = fedThisFrame
        fed += fedThisFrame
        return fedThisFrame
    }

    /** 日志用的一份快照 ✓（`[PenDab]` 汇总那条 ✓）。 */
    fun stats(): StrokeInputCoalesceStats = StrokeInputCoalesceStats(
        points = fed,
        offered = offered,
        frames = pointFrames,
        lastFramePoints = lastFramePoints,
        peakFramePoints = peakFramePoints,
        pointsPerFrame = if (pointFrames > 0) fed.toDouble() / pointFrames else 0.0,
        pending = count,
        droppedMouse = droppedMouse,
        penDriven = penDriven,
    )

    private fun grow() {
        val n = xs.size * 2
        xs = xs.copyOf(n)
        ys = ys.copyOf(n)
        pressures = pressures.copyOf(n)
        fromPenFlags = fromPenFlags.copyOf(n)
    }

    private companion object {
        /** 一帧正常几个点（实测 3~15 ✓）；一次快划可能几十个 ⇒ 满了就翻倍 ✓。 */
        const val INITIAL_CAPACITY = 64
    }
}

/**
 * **这一笔的"攒点"统计**（`[PenDab]` 汇总日志用 ✓ —— 就是"每帧几个点 / 攒了几个点"那几个数 ✓）。
 *
 * @param points 真的喂进引擎的点数（= 引擎收到的输入点数 ✓）
 * @param offered 攒进来的点数（**正常 = [points] + [droppedMouse]** ✓；不等 = 有尾巴没喂 ✗）
 * @param frames 有点的帧数（"每帧几个点"的分母 ✓）
 * @param lastFramePoints 最后一帧攒了几个 ✓
 * @param peakFramePoints 最挤的一帧攒了几个 ✓
 * @param pointsPerFrame 平均每帧几个点 ✓（= `[points] / [frames]` ✓）
 * @param pending 收笔那一刻还攒着没喂的（**必须是 0** ✓）
 * @param droppedMouse 被丢掉的"鼠标那条"的点数 ✓（笔优先 ✓，见 [StrokeInputCoalescer] ✓）——
 *   正常是 0 ✓（鼠标画的那一笔）✗ / 数位板那一笔通常 ≈ 笔的点数 ✓
 * @param penDriven 这一笔是不是**笔按下**开的 ✓
 */
data class StrokeInputCoalesceStats(
    val points: Int = 0,
    val offered: Int = 0,
    val frames: Int = 0,
    val lastFramePoints: Int = 0,
    val peakFramePoints: Int = 0,
    val pointsPerFrame: Double = 0.0,
    val pending: Int = 0,
    val droppedMouse: Int = 0,
    val penDriven: Boolean = false,
)
