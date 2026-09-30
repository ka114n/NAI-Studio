package com.kallan.naistudio.models

import kotlin.math.roundToInt

/**
 * **一笔的笔刷参数**（网页版笔刷引擎那一套 ✓，**唯一真源** ✓）。
 *
 * ## 从哪来
 *
 * 逐字段照 `docs/brush-lab-simple.html` 的 `DEFAULTS`（网页版 = 本仓库**已验证可用**的那份 ✓）搬过来 ✓，
 * 命名与 `docs/44-方案-笔刷引擎与SAI参数清单.md` 对齐 ✓（SAI 的两层：**资源层** `init` 目录里的 `.ini` + **工具层** 工具面板 ✓）。
 * 每个字段的 KDoc 里都写了它的 **SAI / 网页键名**（`Spacing` / `SizeJitter` / `Scattering` … ✓）——
 * 那份 `buildExport()` 导出的 JSON 就是按这些键名来的 ✓，Kotlin 这边一个字段对一条 ✓。
 *
 * ## 为什么是**独立对象**而不是继续往 `StrokeSpec` 上加
 *
 * `StrokeSpec` 是"**这一笔**怎么画"（模式 / 直径 / 形状 / 颜色 / 压感曲线 ✓，一笔之内不变 ✓），
 * 而这里全是 SAI 的**笔刷参数**（笔尖怎么盖 ✓）—— 两者的生命周期不同：
 * 界面以后要"换一支笔就把这一整套换掉"（预设网格 ✓），那正好换成换一个 [BrushSpec] ✓，
 * 不用去动 `StrokeSpec` 的结构 ✓。
 * `spacing` / `angleControl` / 纵横比这三条**已经整体搬进这里** ✓（`StrokeSpec` 上不再留副本 ✗）——
 * "一处真源"这条不能破 ✓。
 *
 * ## 界面（第 ㉓ / ㉔ 批之后）
 *
 * `scattering` / `sizeJitter` / `count` / `paperOn` / `blending` / `water` / `colorStretch` /
 * `keepOpacity` / `colJitter` 一族 … 现在**面板上都能改** ✓（参数区「常用」+ 折叠的「更多」✓，
 * 见 `screens/BrushPanel.kt` ✓）—— 面板**直接读写这一个对象**，没有第二套参数 ✓。
 * 第 ㉔ 批把面板里的**分类胶囊**与 15 颗占位工具删掉了 ✗（用户「**去除分类** · **编辑器只留原来有的**」✓），
 * 顺手给 [highQualitySampling] 接上了**唯一的**作用点 ✓（见那一格 ✓）。
 *
 * @property opacity SAI 的 `Opacity` **浓度**（0..100，默认 **100**）——
 *   整笔的总不透明度基数。真正乘出来的笔尖 alpha 见 [density] ✓。
 * @property angleControl SAI 的 `AngleControl` **角度控制**（**只有两态** ✓：0 固定 / 1 自动 ✓）。
 *   ⚠️ SAI 原版还有个 `2 笔杆方向`，那是给**笔杆旋转**用的 —— 用户 2026-09-20 取消了旋转 ✓，
 *   网页版也拿掉了 ✓，所以这里**没有第三态** ✗（顺手删掉了，和网页对齐 ✓）。
 * @property angle SAI 的 `Angle` **基准角度**（-180..180 度，默认 **0**）——
 *   `angleControl = 0`（固定 ✓）时笔尖长轴就指这个方向 ✓；圆笔尖（`wxRatio = 0`）看不出来 ✓。
 * @property angleJitter SAI 的 `AngleJitter` **角度抖动**（0..100 %，默认 **0**）——
 *   每个笔尖随机偏转 `±180° × 百分比` ✓。
 * @property scaling SAI 的 `Scaling` **倍率**（0..200 %，默认 **100**）——
 *   图案（笔尖）相对"画笔大小"的缩放 ✓。
 * @property sizeJitter SAI 的 `SizeJitter` **大小抖动**（0..100 %，默认 **0**）——
 *   每颗笔尖的半径随机 `±100% × 百分比` ✓。
 * @property spacing SAI 的 `Spacing` **间距**（1..1000 %，默认 **10**）——
 *   相对**这一颗笔尖自己的直径**（压感之后的 ✓）。
 *   ⚠️ 步距还被 `[ImageEditOps.DAB_MAX_STEP_RATIO]（0.4）× 行进方向上的半径` 夹住 ✓
 *   ⇒ **调到 20% 以上不会再变稀**，只会更密、不可能成"一排点" ✓（想要颗粒感请用 [scattering] / [count] ✓）。
 * @property scattering SAI 的 `Scattering` **散布**（0..1000 %，默认 **0**）——
 *   笔尖偏离笔画中心线的幅度（相对笔尖半径 ✓）。
 * @property highQualitySampling SAI 的 `HighQualitySampling` **高品质缩小**（0/1，默认 **0**）。
 *   ⚠️ 第 ㉔ 批起它**真的有作用点**了 ✓（用户 2026-09-24：「**画出来的线条锯齿大**」✗ ⇒ 要能选 ✓）：
 *   映射成"笔尖边缘 **2×2 超采样**"开关 ✓（见 [ImageEditOps.stampDab] ✓）——
 *   开着时边缘像素按 4 个子采样点求平均 ✓（成本只在边缘那一圈 ✓），关着（默认 ✓）走解析式 1px 羽化 ✓。
 *   面板上它在参数区折叠的「更多」里 ✓（`comic.board.p.highQualitySampling` ✓）。
 * @property shapeSize SAI 的 `ShapeSize` **图案大小**（1..5000 px，默认 **100**）——
 *   只有 [absoluteSize] = 1 时才当尺寸用 ✓（半径 = `shapeSize / 2` ✓）。
 * @property absoluteSize SAI 的 `AbsoluteSize` **绝对大小**（0 用"画笔大小 × 倍率" / 1 用 [shapeSize]，默认 **0**）。
 * @property wxRatio SAI 的 `WxHRatio` **纵横比**（-99..99，默认 **0** = 圆 ✓）。
 *   换算照网页：`> 0` ⇒ `100/(100−w)`、`< 0` ⇒ `(100+w)/100`、`0` ⇒ 1（圆 ✓）——
 *   正的 = 横向拉长（扁笔 ✓），负的 = 纵向拉长 ✓。Kotlin 侧惯用的"1 = 圆 / >1 = 扁"由 [nibRatio] 派生 ✓。
 * @property wxJitter SAI 的 `WxHJitter` **纵横比抖动**（0..100 %，默认 **0**）。
 * @property count SAI 的 `Count` **计数**（1..20，默认 **1**）—— 一档落几个笔尖 ✓。
 * @property countJitter SAI 的 `CountJitter` **计数抖动**（0..100 %，默认 **0**）——
 *   实际颗数 = `max(1, round(count × (1 ± 百分比)))` ✓（照网页 ✓：抖动可以把它压到**少于** [count] ✓）。
 * @property allDirScattering SAI 的 `AllDirScattering` **往全方向散布**（0 仅与笔画**直交**方向 / 1 全方向，默认 **0**）。
 * @property gaussianDistribution SAI 的 `GaussianDistribution` **高斯分布**（0 一致密度 / 1 **离光标越远越稀**，默认 **0**）。
 * @property integerPosition SAI 的 `IntegerPosition` **整数坐标**（0 / 1 **对齐到像素边界**，默认 **0**）——
 *   线稿有用 ✓（笔尖中心落在整数坐标上，边缘不会糊成半像素 ✓）。
 * @property colJitter SAI 的 `ColJitter` **前景↔背景色抖动**（0..100 %，默认 **0**）——
 *   背景按**白色**算 ✓（照网页 ✓）。
 * @property hueJitter SAI 的 `HueJitter` **色相抖动**（0..100 %，默认 **0**）—— `±180° × 百分比` ✓。
 * @property saturationJitter SAI 的 `SaturationJitter` **饱和度抖动**（0..100 %，默认 **0**）—— `±1 × 百分比` ✓。
 * @property brightnessJitter SAI 的 `BrightnessJitter` **亮度抖动**（0..100 %，默认 **0**）—— `±0.5 × 百分比` ✓。
 * @property applyToEachShape SAI 的 `ApplyToEachShape` **应用到每个形状**（0 每个笔画 / 1 **每个笔尖**，默认 **0**）——
 *   颜色抖动的作用粒度 ✓（= 0 时只有**第一颗**笔尖抖、其余用基准色 ✓，照网页 ✓）。
 * @property density **浓度（画笔浓度）**（0..100，默认 **100**）—— 笔尖 alpha 的基数 ✓
 *  （真正落下去的 alpha = `opacity/100 × density/100` 再乘水分量 / 压感那几条 ✓）。
 * @property minDensity **最小浓度**（0..100 %，默认 **0**）—— **压感→浓度**的**下限** ✓
 *   （只有 [pressureAlpha] 开着才参与 ✓，公式与最小半径同形 ✓）。
 *   ⚠️ 它和 `StrokeSpec.minRadiusRatio`（**最小半径** ✓）是**并列的两条** ✓：一个管粗细、一个管浓淡 ✓。
 * @property spreadNoise **扩散和噪点**开关（默认 **false**）—— 每颗笔尖的半径 / 位置 / alpha 抖动 + 高频噪声遮罩 ✓。
 * @property spreadNoiseStrength **扩散和噪点强度**（0..100 %，默认 **30**）—— 只在 [spreadNoise] 开着时起作用 ✓。
 * @property paperOn **画用纸（纸纹）**开关（默认 **false**）—— 程序生成的 128×128 无缝噪声贴图 ✓，
 *   落笔尖时**按画布坐标对齐**、**逐像素相乘** ✓（见 [BrushEngine.buildTile] / [BrushEngine.textureAt] ✓）。
 * @property paperStrength **画用纸强度**（0..100 %，默认 **35**）—— 只在 [paperOn] 开着时起作用 ✓。
 * @property blending **混色**（0..100，默认 **0**）—— 落笔尖前**采样画布局部平均色**，把笔色往采样色拉 ✓；
 *   比例 = `min(1, blending/100 + 0.25 × 水分/100) × 0.85` ✓（`×0.85` 是**留 15% 本色** ✓，
 *   否则在纯白纸上笔迹会"隐形" ✗）。
 * @property water **水分量**（0..100，默认 **0**）—— ① 采样区域放大到 `r × (0.8 + 水分/100)` ✓
 *   ② alpha 乘 `1 − 0.55 × 水分/100`（更淡 ✓）③ 额外加权混色 ✓。
 * @property colorStretch **色延伸**（0..100，默认 **0**）—— ① 采样点**沿笔画方向后拖** `2r × 色延伸/100` ✓
 *   ② 把**上一个笔尖的混色结果**混进来 = 拖色 ✓。
 * @property keepOpacity **保持不透明度**（默认 **false**）—— 整笔先画在**独立笔画缓冲**（alpha = 1 ✓，
 *   混色仍从主缓冲采样 ✓），抬笔时按浓度**一次性合成** ✓ ⇒ 同一笔里反复涂不会越涂越深 ✓。
 * @property blurPressureOn **模糊笔压**开关（默认 **true**）—— 模糊工具的强度随不随笔压走 ✓。
 * @property blurPressure **模糊笔压强度**（0..100 %，默认 **60**）——
 *   `0` = 强度恒定 ✓；`100` = 强度完全跟笔压走 ✓（中间按 `(1−k) + k × 压力` 插值 ✓）。
 * @property pressureAlpha **压感→浓度**开关（默认 **false**，照网页 `DEFAULTS.pressureAlpha` ✓）——
 *   关着时 [minDensity] 不参与 ✓（只有大小吃压感 ✓，也就是本仓库一直以来的行为 ✓）。
 * @property pressureSize **压感→大小**开关（默认 **true**，照网页 `DEFAULTS.pressureSize` ✓）——
 *   ⚠️ 它**不在这里**参与运算 ✗：半径的压感换算在 `ImageEditSession.nibRadiusAt`（口径与
 *   `StrokeSpec.minRadiusRatio` / `pressureCurve` 同一条 ✓）—— 本字段只为"导出 JSON 对得上"保留 ✓。
 */
data class BrushSpec(
    // ---- 资源层 · SAI 通用（brshape）----
    val opacity: Float = 100f,
    val angleControl: Int = NibAngleControl.AUTO,
    val angle: Float = 0f,
    val angleJitter: Float = 0f,
    val scaling: Float = 100f,
    val sizeJitter: Float = 0f,
    val spacing: Float = ImageEditOps.DEFAULT_SPACING_PERCENT,
    val scattering: Float = 0f,
    val highQualitySampling: Int = 0,
    // ---- 资源层 · SAI 散布（scatter）----
    val shapeSize: Float = 100f,
    val absoluteSize: Int = 0,
    val wxRatio: Float = 0f,
    val wxJitter: Float = 0f,
    val count: Int = 1,
    val countJitter: Float = 0f,
    val allDirScattering: Int = 0,
    val gaussianDistribution: Int = 0,
    val integerPosition: Int = 0,
    // ---- 资源层 · SAI 颜色抖动（简单版没给 UI，默认全 0 ✓）----
    val colJitter: Float = 0f,
    val hueJitter: Float = 0f,
    val saturationJitter: Float = 0f,
    val brightnessJitter: Float = 0f,
    val applyToEachShape: Int = 0,
    // ---- 工具层（SAI「工具」面板）----
    val density: Float = 100f,
    val minDensity: Float = 0f,
    val spreadNoise: Boolean = false,
    val spreadNoiseStrength: Float = 30f,
    val paperOn: Boolean = false,
    val paperStrength: Float = 35f,
    val blending: Float = 0f,
    val water: Float = 0f,
    val colorStretch: Float = 0f,
    val keepOpacity: Boolean = false,
    val blurPressureOn: Boolean = true,
    val blurPressure: Float = 60f,
    val pressureAlpha: Boolean = false,
    val pressureSize: Boolean = true,
    /**
     * **笔画级覆盖度累积**（第 ㉟① 批 ✓，用户 2026-09-21：「笔画的纹理不应是一个个个圆组成的笔画，
     * 笔画应该连贯」✗ ⇒ 用户 2026-09-21 追加口径：「**引擎也可以动啊，为了效果**」✓）。
     *
     * ## 它治的是什么（根因 ✓）
     *
     * 老路（= 网页口径 ✓）每颗笔尖各自 `source-over` 叠上去 ✓ ——
     * 于是"同一像素被 N 颗盖过"的合成是 `1 − Π(1 − αᵢ)`：**只要单颗淡、或者间距稍大，
     * 中心线上的 alpha 就会随笔尖间距周期性起伏** ✗ ⇒ 肉眼就是"一个个圆组成的笔画" ✗。
     * 把它调好只能**靠参数硬凑**（`spacing` 压小、`SizeJitter/Scattering` 压 0 ✓ ——
     * 这正是上一批 `wc3/wc4` 做的事 ✓，治标 ✓）：参数一动（用户换个间距 / 开点抖动）
     * 轮廓立刻就又被拆开 ✗。
     *
     * 开着这一条 ⇒ 这一笔先画进一张**笔画覆盖度缓冲** ✓，同一像素取
     * **`max(旧覆盖, 新覆盖)`** ✓（而不是反复叠加 ✗），抬笔（或需要读回时）**一次性合成到层表面** ✓：
     *  · 轮廓**天生连续** ✓（相邻笔尖在中心线上取到的是"最大的那一颗"，不会凹 ✗）；
     *  · **间距 / 抖动再怎么调都不会拆轮廓** ✓ —— 因为拆轮廓的机制（周期性起伏）被 `max` 抹平了 ✓；
     *  · 颗粒交给**贴图**（纸纹 ✓ —— 它按画布像素走 ✓，天然不随笔尖间距起伏 ✓）。
     *
     * ## ⚠️ 它**故意**与网页不一致（**这是效果取向，不是 bug** ✗）
     *
     * 网页就是逐颗 `source-over` ✓ ⇒ 开了这一条的档**必然**与网页逐像素对不上 ✓
     *（`SimpleBrushParityTest` / `MixBrushParityTest` / `PaperBrushParityTest` / 选区方笔那四条
     *  量的就是"与网页逐像素一致" ✓）。所以：
     *  · **默认 `false`** ✓ ⇒ 老 16 支 + `wc1/wc2` 一个字节不走这条路 ✓
     *    ⇒ 那四条判据**逐字不动、继续全绿** ✓（不是放松判据，是把新行为放在一条**默认关闭**的新路上 ✓）；
     *  · 只有**显式打开**的档（`wc3/wc4` + 面板那个用户可见的勾 ✓）才走 ✓。
     *  · ⚠️ **以后谁看到"这一档和网页对不上"，先看这个字段** ✓ —— 那是**有意为之** ✗，
     *    因为用户要的是"连贯 + 贴图纹理" ✓（见 `docs/61` ✓）。
     */
    val strokeCoverage: Boolean = false,
) {

    /**
     * **笔尖宽高比**（Kotlin 侧惯用口径：**1 = 圆 ✓、>1 = 扁 ✓**）—— 由 [wxRatio] 派生 ✓。
     *
     * 换算照网页 `stamp()` 里那一句 ✓：
     * ```
     * ratio = wxRatio > 0 ? 100 / (100 - wxRatio) : (100 + wxRatio) / 100     // wxRatio = 0 ⇒ 1
     * ```
     * 再夹到 `0.05..20` ✓（和网页同一个上限 ✓）。
     */
    val nibRatio: Float
        get() = wxRatioToNibRatio(wxRatio)

    /** 这一套参数要不要**纸纹 / 噪点贴图**（不需要就别去生成 128×128 ✗，见 `ImageEditSession.textureFor` ✓）。 */
    val needsTexture: Boolean
        get() = (paperOn && paperStrength > 0f) || (spreadNoise && spreadNoiseStrength > 0f)

    /** 这一笔要不要**采样混色**（混色 / 水分量 / 色延伸 三条任一 > 0 ✓，照网页 ✓）。 */
    val wantsMix: Boolean
        get() = blending > 0f || water > 0f || colorStretch > 0f

    /**
     * 把每个字段收敛到合法区间（NaN / 无穷 → 默认值 ✓）。
     *
     * 为什么必须有：这些数以后全来自界面滑杆 / 预设 JSON ——
     * 一个 NaN 漏进半径就是"笔尖一个像素都盖不上、还看不出报错" ✗（老教训见
     * `ImageEditOps.pressureRadiusScale` 的说明 ✓）。落笔那一刻统一过一遍 ✓。
     */
    fun sanitized(): BrushSpec = BrushSpec(
        opacity = safe(opacity, DEFAULTS.opacity, 0f, 100f),
        angleControl = if (angleControl == NibAngleControl.FIXED) NibAngleControl.FIXED else NibAngleControl.AUTO,
        angle = safe(angle, DEFAULTS.angle, -180f, 180f),
        angleJitter = safe(angleJitter, DEFAULTS.angleJitter, 0f, 100f),
        scaling = safe(scaling, DEFAULTS.scaling, 0f, 200f),
        sizeJitter = safe(sizeJitter, DEFAULTS.sizeJitter, 0f, 100f),
        spacing = safe(spacing, DEFAULTS.spacing, 1f, 1000f),
        scattering = safe(scattering, DEFAULTS.scattering, 0f, 1000f),
        highQualitySampling = if (highQualitySampling == 1) 1 else 0,
        shapeSize = safe(shapeSize, DEFAULTS.shapeSize, 1f, 5000f),
        absoluteSize = if (absoluteSize == 1) 1 else 0,
        wxRatio = safe(wxRatio, DEFAULTS.wxRatio, -WX_RATIO_LIMIT, WX_RATIO_LIMIT),
        wxJitter = safe(wxJitter, DEFAULTS.wxJitter, 0f, 100f),
        count = count.coerceIn(1, 20),
        countJitter = safe(countJitter, DEFAULTS.countJitter, 0f, 100f),
        allDirScattering = if (allDirScattering == 1) 1 else 0,
        gaussianDistribution = if (gaussianDistribution == 1) 1 else 0,
        integerPosition = if (integerPosition == 1) 1 else 0,
        colJitter = safe(colJitter, DEFAULTS.colJitter, 0f, 100f),
        hueJitter = safe(hueJitter, DEFAULTS.hueJitter, 0f, 100f),
        saturationJitter = safe(saturationJitter, DEFAULTS.saturationJitter, 0f, 100f),
        brightnessJitter = safe(brightnessJitter, DEFAULTS.brightnessJitter, 0f, 100f),
        applyToEachShape = if (applyToEachShape == 1) 1 else 0,
        density = safe(density, DEFAULTS.density, 0f, 100f),
        minDensity = safe(minDensity, DEFAULTS.minDensity, 0f, 100f),
        spreadNoise = spreadNoise,
        spreadNoiseStrength = safe(spreadNoiseStrength, DEFAULTS.spreadNoiseStrength, 0f, 100f),
        paperOn = paperOn,
        paperStrength = safe(paperStrength, DEFAULTS.paperStrength, 0f, 100f),
        blending = safe(blending, DEFAULTS.blending, 0f, 100f),
        water = safe(water, DEFAULTS.water, 0f, 100f),
        colorStretch = safe(colorStretch, DEFAULTS.colorStretch, 0f, 100f),
        keepOpacity = keepOpacity,
        blurPressureOn = blurPressureOn,
        blurPressure = safe(blurPressure, DEFAULTS.blurPressure, 0f, 100f),
        pressureAlpha = pressureAlpha,
        pressureSize = pressureSize,
        strokeCoverage = strokeCoverage,
    )

    companion object {

        /** `WxHRatio` 的绝对值上限 ✓（SAI 的 100:1 … 1:100；留 1 的余量免得除以 0 ✗）。 */
        const val WX_RATIO_LIMIT = 99f

        /**
         * **B 方案开关（2026-09-21）**：笔画期间**每颗笔尖直接落表面** ✓（= 落笔就看得见墨 ✓）。
         *
         * ## 为什么要有它（用户实机原话 ✓）
         *
         * > 「**绘画时是先画出一排点，然后才补成完整的线**」
         *
         * A 方案（`ImageEditSession.compositeCoverageFrameTick` ✓）把"累积→合成"压进同一帧 ✓，
         * 治的是**滞后** ✓；B 方案走的是另一条路：**根本不攒** ✗ ——
         * 每颗笔尖当场 `stampOnSurface` ✓（老路 = 网页口径 ✓），**落笔即见墨** ✓、零延迟 ✓。
         *
         * ## ⚠️ 代价（必须如实说 ✗）
         *
         * 直接落表面 = 逐颗 `source-over` ⇒ **`docs/65/66` 治好的"一个个圆"会回来** ✗
         *（同一像素 `1 − Π(1 − αᵢ)` 随笔尖间距周期性起伏 ✓ —— 数学必然 ✓，不是参数问题 ✓）。
         * 所以 B 是"**手感优先**"的那一档 ✓ —— 用户明确要求"ab 各做一个"来做对比验收 ✓，
         * **不是**要取代 A ✓。
         *
         * ⚠️ 只影响**有 `strokeCoverage` 的档**（`wc3/wc4` ✓）—— 老 16 支本来就不走那条路 ✓，
         * 开关对它们**零影响** ✓（它们两档都是"逐颗落表面"✓）。
         *
         * ## 怎么切（**运行期**，不是编译期 ✓）
         *
         * 由 JVM 系统属性 `nai.brush.directSurface` 决定 ✓ —— 见 `Main.kt` 开机那一段 ✓：
         * ```
         *   -Dnai.brush.directSurface=true   → B 方案（直接落表面 ✓）
         *   不设 / false                     → A 方案（按帧合成 ✓，默认 ✓）
         * ```
         * 切换方式是**改 `NAI Studio.cfg` 里的一行** ✓ ——
         * ⚠️ **`java-options=` 必须一行一个** ✗（`-Dk=v` 这种**单个** token 没问题 ✓，
         *    但 `--add-opens` 那种两段式的仍要拆两行 ✓ —— 见 `tools\patch_desktop_cfg.ps1` 顶部那段血泪 ✓）。
         *
         * 默认 false = **A 方案**（按帧合成 ✓，保连贯 ✓）。
         */
        @JvmStatic
        var directSurfaceStroke: Boolean = false

        /** 从系统属性读一次 ✓（`Main` 开机调 ✓；测试可直接赋值 ✓）。 */
        @JvmStatic
        fun readDirectSurfaceFromSystemProperty() {
            directSurfaceStroke = System.getProperty("nai.brush.directSurface")
                ?.equals("true", ignoreCase = true) == true
        }

        /**
         * 网页 `DEFAULTS` 的一份**只读快照** ✓ —— [sanitized] 的兜底值就是它 ✓。
         *
         * ⚠️ 改这里就等于改默认手感 ✓（口径见 `docs/brush-lab-simple.html` 第 154~176 行 ✓）。
         */
        val DEFAULTS = BrushSpec()

        /** SAI 的 `WxHRatio`（0 = 圆 ✓）→ Kotlin 的宽高比（1 = 圆 ✓）。 */
        fun wxRatioToNibRatio(wxRatio: Float): Float {
            val w = if (wxRatio.isFinite()) wxRatio.coerceIn(-WX_RATIO_LIMIT, WX_RATIO_LIMIT) else 0f
            val ratio = when {
                w > 0f -> 100f / (100f - w)
                w < 0f -> (100f + w) / 100f
                else -> 1f
            }
            return ratio.coerceIn(0.05f, 20f)
        }

        /**
         * [wxRatioToNibRatio] 的逆运算（界面用 **1 = 圆 / >1 = 扁** 那一套 ✓，存的时候换成 SAI 的 `WxHRatio` ✓）。
         *
         * `ratio = 100 / (100 − w)` ⇒ `w = 100 − 100 / ratio` ✓ —— 圆（1）正好给 0 ✓。
         */
        fun nibRatioToWxRatio(nibRatio: Float): Float {
            val ratio = if (nibRatio.isFinite()) nibRatio.coerceIn(0.05f, 20f) else 1f
            return (100f - 100f / ratio).coerceIn(-WX_RATIO_LIMIT, WX_RATIO_LIMIT)
        }

        /** 界面文字用：`1.0` / `2.5` …（和 `ComicModeScreen.nibRatioLabel` 同一个口径 ✓）。 */
        fun nibRatioLabel(nibRatio: Float): String {
            val scaled = (nibRatio.coerceIn(1f, ImageEditOps.NIB_MAX_RATIO) * 10f).roundToInt()
            return "${scaled / 10}.${scaled % 10}"
        }

        private fun safe(value: Float, fallback: Float, min: Float, max: Float): Float =
            if (value.isFinite()) value.coerceIn(min, max) else fallback
    }
}
