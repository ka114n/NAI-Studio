package com.kallan.naistudio.screens

import androidx.compose.ui.graphics.vector.ImageVector
import com.kallan.naistudio.models.BrushEngine
import com.kallan.naistudio.models.BrushShape
import com.kallan.naistudio.models.BrushSpec
import com.kallan.naistudio.models.CanvasTool
import com.kallan.naistudio.models.ImageEditOps
import com.kallan.naistudio.ui.BlurIcon
import com.kallan.naistudio.ui.BrushIcon
import com.kallan.naistudio.ui.BucketIcon
import com.kallan.naistudio.ui.CanvasSelectIcon
import com.kallan.naistudio.ui.DropperIcon
import com.kallan.naistudio.ui.EraserIcon
import com.kallan.naistudio.ui.LassoIcon
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * **SAI 笔刷面板的"数据那一半"**（第 26 轮 · 第 ㉔ 批瘦身 ✓）—— 网页完整版 `docs/brush-lab.html`
 * 那套工具面板（`TOOLS` / `PRESET_LIST` / `SIZE_GRID` / `drawDemoStroke`）
 * 逐条搬过来的**纯数据 + 纯函数**。
 *
 * 界面那一半在 `screens/BrushPanel.kt`。
 * 分成两个文件只有一个理由：**数据这一半能单独读**（7 颗工具 + 16 个预设 + 预览画法
 * 一共几百行，混在 Composable 里就没法一眼对网页了）。
 *
 * 口径三条：
 *  1. **数值一个都没有自己发明** —— 每一条 KDoc 里都标着网页的行号；
 *  2. **不碰引擎** —— 这里只**读** `BrushSpec` / `BrushEngine` 的现成 API；
 *  3. ⚠️ 第 ㉔ 批（用户「**去除分类** · **编辑器只留原来有的**」✓）：
 *     **分类胶囊那一整块（`BrushCatNames`）删掉了** ✗、
 *     **22 颗工具收成 7 颗**（只留 `CanvasTool` 里真有实现的 ✓）、
 *     **变体（`BrushVariantKeys` / `variant*` 那 6 个 i18n 键）整块删掉** ✗ ——
 *     见 [BrushPanelTools] 的说明 ✓。
 *     同时 `highQualitySampling`（SAI 的「高品质缩小」✓）**改成界面上可选** ✓ ——
 *     它现在真的映射到"笔尖边缘 2×2 超采样开关"（见 `ImageEditOps.stampDab` ✓）。
 */

// ---------------------------------------------------------------------------
// 2 工具网格（**只留真能用的 7 颗** ✓ —— 见这一节的说明 ✓）
// ---------------------------------------------------------------------------

/**
 * 一颗工具**点下去到底会发生什么**。
 *
 * ⚠️ 第 ㉔ 批（用户：「**编辑器只留原来有的**」✓）起，面板上**只剩真正实现了的那 7 颗** ✗：
 * 每一种 kind 都对应一个**真的能画**的 [CanvasTool] ✓ —— 不再有"占位工具 + toast 说明"那一套 ✗
 *（原来那句"这一版等同 XX"的提示、以及它配套的 i18n 键，跟着占位工具**一起删掉了** ✓）。
 */
enum class BrushToolKind {
    /** 画笔（`CanvasTool.DRAW` —— 沿路径盖笔尖 ✓） */
    DAB,

    /** 橡皮（`CanvasTool.ERASE`） */
    ERASER,

    /** 模糊（`CanvasTool.BLUR`） */
    BLUR,

    /** 油漆桶（`CanvasTool.FILL`） */
    FILL,

    /** 选择（`CanvasTool.SELECT` —— 矩形选框 ✓） */
    SELECT,

    /** 套索（`CanvasTool.LASSO` —— 多边形选框 ✓） */
    LASSO,

    /** 吸管 / 取色（`CanvasTool.PICKER` ✓） */
    PICKER,
}

/** 面板上的一颗工具（名字 / 图标 / 切到哪个 [CanvasTool]）。 */
data class BrushPanelTool(
    val id: String,
    /** i18n 键（**一律用现成的 `canvas.tool.*`** ✓ —— 面板不再自带一套工具名 ✓）。 */
    val key: String,
    val icon: ImageVector,
    val kind: BrushToolKind,
)

/**
 * **面板上的 7 颗工具**（用户口径：「**编辑器只留原来有的**」✓）：
 * `画笔 / 橡皮 / 油漆桶 / 模糊 / 取色 / 选择 / 套索`。
 *
 * ## 为什么只剩这 7 颗（**这不是"少做了"，是"删掉了做不了的"** ✓）
 *
 * 上一版那 22 颗是照网页 `TOOLS` 抄的 ✓，其中 15 颗是**网页里的占位档**（马克笔 / 水彩笔 ×2 /
 * 散布 / 特效笔 / 涂抹 / 球形笔刷 / 涂黑 / 二值笔 ×4 / 选区笔 ×2 / 选区擦 ×2 / 渐变 ✗）——
 * 它们在网页里也只是"换几个滑杆数值 + 说一句这一版等同 XX" ✓，点了既不换引擎、也没有独立实现 ✗。
 * 用户口径是「**只留原来有的**」✓ ⇒ 全部删掉 ✗，留下的这 7 颗**每一颗都在 `CanvasTool` 里有真实实现** ✓。
 *
 * ## 图标怎么挑（**一律仓库自绘** ✓）
 *
 * 7 颗全部来自 `ui/AppIcons.kt`（[BrushIcon] / [EraserIcon] / [BucketIcon] / [BlurIcon] /
 * [DropperIcon] / [CanvasSelectIcon] / [LassoIcon] ✓）——
 * 于是 `material-icons-core` 那几颗也不再需要了 ✓（`material-icons-extended` 一直没引 ✓：
 * 一个包 **37.7 MB**，见 `AppIcons.kt` 开头 ✓）。
 *
 * ## 工具**不写笔刷参数**（一处真源 ✓）
 *
 * 上一版每颗工具还带一套 `spec`（切工具时顺手盖掉 `comicPaintBrush` ✗）——
 * 现在**整块删掉** ✗：工具图标**只负责切工具** ✓，参数只由「预设网格 / 参数区 / 大小盘」写 ✓
 * （用户口径：「工具图标只负责切工具」✓）。于是 `AppState.comicPaintBrush` 这一处真源更干净 ✓。
 */
val BrushPanelTools: List<BrushPanelTool> = listOf(
    BrushPanelTool("draw", "canvas.tool.draw", BrushIcon, BrushToolKind.DAB),
    BrushPanelTool("eraser", "canvas.tool.erase", EraserIcon, BrushToolKind.ERASER),
    BrushPanelTool("bucket", "canvas.tool.fill", BucketIcon, BrushToolKind.FILL),
    BrushPanelTool("blur", "canvas.tool.blur", BlurIcon, BrushToolKind.BLUR),
    BrushPanelTool("picker", "canvas.tool.picker", DropperIcon, BrushToolKind.PICKER),
    BrushPanelTool("select", "canvas.tool.select", CanvasSelectIcon, BrushToolKind.SELECT),
    BrushPanelTool("lasso", "canvas.tool.lasso", LassoIcon, BrushToolKind.LASSO),
)

/** 这一颗工具切到本仓库的哪个 [CanvasTool] ✓（7 颗**颗颗都有真实实现** ✓，没有"占位"这一支 ✗）。 */
fun brushToolCanvasTool(kind: BrushToolKind): CanvasTool = when (kind) {
    BrushToolKind.DAB -> CanvasTool.DRAW
    BrushToolKind.ERASER -> CanvasTool.ERASE
    BrushToolKind.BLUR -> CanvasTool.BLUR
    BrushToolKind.FILL -> CanvasTool.FILL
    BrushToolKind.SELECT -> CanvasTool.SELECT
    BrushToolKind.LASSO -> CanvasTool.LASSO
    BrushToolKind.PICKER -> CanvasTool.PICKER
}

// ---------------------------------------------------------------------------
// 3 预设（16 格，一整套 `BrushSpec`）
// ---------------------------------------------------------------------------

/**
 * 一个预设 = **一整套 `BrushSpec`** + 一笔画多粗（[sizePx]）。
 *
 * 口径照网页 `PRESET_LIST`（441 行起）：**逐条照抄它的字段**，
 * 只把网页的 `size` 落到本仓库的"笔刷直径"字段上
 * （`AppState.comicPaintBrushPixels` —— 大小不属于 `BrushSpec`，所以它是**并列**的一个数）。
 *
 * 网页里那些 `size`（14 / 24 / 4 / … / 150）全在 `1..600` 之内，直接落。
 */
data class BrushPreset(
    val id: String,
    /** i18n 键（名字照网页，重名的两支**共用一个键**）。 */
    val key: String,
    /** 这一档的笔刷直径（像素 —— 面板落下去时调 `setComicPaintBrush`）。 */
    val sizePx: Int,
    val spec: BrushSpec,
)

/**
 * **16 个预设**（名字与顺序**逐条照**网页）：
 * `草稿1 / 草稿2 / 线稿 / 线稿 / 上色 / 上色2 / 晕染 / 万能 / 水彩 / 水彩 / 模糊 / 模糊 /
 * 混色 / 混色 / 模糊 / 融合`。
 *
 * ## 每一档的数值（写在这儿 —— 用户点名要能查）
 *
 * | id | 名字 | 大小 / spacing / scattering / sizeJitter / density / opacity / blending / water / colorStretch / 纸纹 |
 * |---|---|---|
 * | draft1 | 草稿1 | 14 / 10 / 20 / 38 / 78 / 100 / 0 / 0 / 0 / 关（+ angleJitter 22）|
 * | draft2 | 草稿2 | 24 / 10 / 34 / 55 / 62 / 100 / 0 / 0 / 0 / 关（+ angleJitter 38）|
 * | line1  | 线稿  | 4 / 3 / 0 / 0 / 100 / 100 / 0 / 0 / 0 / 关（**整数坐标**）|
 * | line2  | 线稿  | 8 / 4 / 0 / 6 / 100 / 100 / 0 / 0 / 0 / 关（**整数坐标**）|
 * | paint1 | 上色  | 62 / 11 / 4 / 8 / 34 / 62 / 22 / 12 / 6 / 25 |
 * | paint2 | 上色2 | 96 / 13 / 6 / 10 / 24 / 50 / 38 / 22 / 10 / 35 |
 * | blend1 | 晕染  | 70 / 8 / 6 / 12 / 20 / 55 / 70 / 68 / 48 / 48 |
 * | omni   | 万能  | 24 / 8 / 2 / 6 / 86 / 100 / 30 / 26 / 22 / 30 |
 * | wc1    | 水彩  | 72 / 6 / 10 / 18 / 30 / 80 / 76 / 80 / 62 / 50 |
 * | wc2    | 水彩  | 112 / 5 / 14 / 24 / 22 / 70 / 86 / 92 / 78 / 60 |
 * | blur1  | 模糊  | 40（模糊笔压 72）|
 * | blur2  | 模糊  | 92（模糊笔压 34）|
 * | mix1   | 混色  | 46 / 6 / 4 / 8 / 60 / 90 / 100 / 58 / 70 / 28 |
 * | mix2   | 混色  | 30 / 5 / 2 / 4 / 74 / 100 / 92 / 40 / 52 / 24 |
 * | blur3  | 模糊  | 150（**模糊笔压关**，强度 100）|
 * | fusion | 融合  | 82 / 4 / 8 / 14 / 18 / 65 / 100 / 76 / 90 / 40 |
 *
 * 网页那三支「模糊」（blur1 / blur2 / blur3）在那边的 `tool` 是 `blur` ——
 * 本仓库的模糊**不吃** `BrushSpec`（走 `StrokeSpec.blurIntensity`），
 * 所以这里的 `spec` 只留 `blurPressureOn` / `blurPressure` **两个字段**（= 真能生效的那两个），
 * 其余一律 `DEFAULTS` —— **不假装**它能改混色 / 水彩（那两项在模糊工具下本来也不参与）。
 */
val BrushPresets: List<BrushPreset> = listOf(
    BrushPreset(
        "draft1", "comic.board.preset.draft1", 14,
        BrushSpec(spacing = 10f, scattering = 20f, sizeJitter = 38f, angleJitter = 22f, density = 78f),
    ),
    BrushPreset(
        "draft2", "comic.board.preset.draft2", 24,
        BrushSpec(spacing = 10f, scattering = 34f, sizeJitter = 55f, angleJitter = 38f, density = 62f),
    ),
    BrushPreset(
        "line1", "comic.board.preset.line1", 4,
        BrushSpec(spacing = 3f, density = 100f, opacity = 100f, integerPosition = 1),
    ),
    BrushPreset(
        "line2", "comic.board.preset.line2", 8,
        BrushSpec(spacing = 4f, sizeJitter = 6f, density = 100f, integerPosition = 1),
    ),
    BrushPreset(
        "paint1", "comic.board.preset.paint1", 62,
        BrushSpec(
            spacing = 11f, scattering = 4f, sizeJitter = 8f, density = 34f, opacity = 62f,
            blending = 22f, water = 12f, colorStretch = 6f, paperOn = true, paperStrength = 25f,
        ),
    ),
    BrushPreset(
        "paint2", "comic.board.preset.paint2", 96,
        BrushSpec(
            spacing = 13f, scattering = 6f, sizeJitter = 10f, density = 24f, opacity = 50f,
            blending = 38f, water = 22f, colorStretch = 10f, paperOn = true, paperStrength = 35f,
        ),
    ),
    BrushPreset(
        "blend1", "comic.board.preset.blend1", 70,
        BrushSpec(
            spacing = 8f, scattering = 6f, sizeJitter = 12f, density = 20f, opacity = 55f,
            blending = 70f, water = 68f, colorStretch = 48f, paperOn = true, paperStrength = 48f,
        ),
    ),
    BrushPreset(
        "omni", "comic.board.preset.omni", 24,
        BrushSpec(
            spacing = 8f, scattering = 2f, sizeJitter = 6f, density = 86f, opacity = 100f,
            blending = 30f, water = 26f, colorStretch = 22f, paperOn = true, paperStrength = 30f,
        ),
    ),
    BrushPreset(
        "wc1", "comic.board.preset.wc1", 72,
        BrushSpec(
            spacing = 6f, scattering = 10f, sizeJitter = 18f, density = 30f, opacity = 80f,
            blending = 76f, water = 80f, colorStretch = 62f, paperOn = true, paperStrength = 50f,
        ),
    ),
    BrushPreset(
        "wc2", "comic.board.preset.wc2", 112,
        BrushSpec(
            spacing = 5f, scattering = 14f, sizeJitter = 24f, density = 22f, opacity = 70f,
            blending = 86f, water = 92f, colorStretch = 78f, paperOn = true, paperStrength = 60f,
        ),
    ),
    BrushPreset(
        "blur1", "comic.board.preset.blur1", 40,
        BrushSpec(blurPressureOn = true, blurPressure = 72f),
    ),
    BrushPreset(
        "blur2", "comic.board.preset.blur2", 92,
        BrushSpec(blurPressureOn = true, blurPressure = 34f),
    ),
    BrushPreset(
        "mix1", "comic.board.preset.mix1", 46,
        BrushSpec(
            spacing = 6f, scattering = 4f, sizeJitter = 8f, density = 60f, opacity = 90f,
            blending = 100f, water = 58f, colorStretch = 70f, paperOn = true, paperStrength = 28f,
        ),
    ),
    BrushPreset(
        "mix2", "comic.board.preset.mix2", 30,
        BrushSpec(
            spacing = 5f, scattering = 2f, sizeJitter = 4f, density = 74f, opacity = 100f,
            blending = 92f, water = 40f, colorStretch = 52f, paperOn = true, paperStrength = 24f,
        ),
    ),
    BrushPreset(
        "blur3", "comic.board.preset.blur3", 150,
        BrushSpec(blurPressureOn = false, blurPressure = 100f),
    ),
    BrushPreset(
        "fusion", "comic.board.preset.fusion", 82,
        BrushSpec(
            spacing = 4f, scattering = 8f, sizeJitter = 14f, density = 18f, opacity = 65f,
            blending = 100f, water = 76f, colorStretch = 90f, paperOn = true, paperStrength = 40f,
        ),
    ),
    // -----------------------------------------------------------------------
    // 第 ㉝③ 批：**"水彩 2"**（用户 2026-09-21：「线由一个个圆组成」⇒ 要更连贯、更水彩 ✓）
    // 第 ㉟① 批：**打开"笔画级覆盖度累积"** ✓（用户 2026-09-21：「笔画的纹理不应是一个个个圆
    //           组成的笔画，笔画应该连贯，纹理是笔画周围的贴图」+「引擎也可以动啊，为了效果」✓）
    //
    // ⚠️ 边界（用户口径 ✓）：
    //  · ㉝③ 那批**只动预设 / 参数** ✓、引擎一个字没动 ✗ —— 那批证明的结论是
    //    「**参数治标**」✓：把 `spacing` 压到 2、抖动压到 6/0 之后凹陷确实从 12 掉到 0 ✓，
    //    但**参数一改回去立刻就又拆轮廓** ✗（那是"每颗各自 source-over"这个合成口径长出来的 ✓）。
    //  · ㉟① 换了个治法 ✓：**动引擎** —— 这一笔先累积进"覆盖度缓冲"（同一像素取 `max` ✓）、
    //    抬笔时**一次性**合成 ✓ ⇒ 轮廓天生连续 ✓、**与间距 / 抖动无关** ✓。
    //    开关就是 `BrushSpec.strokeCoverage`（**默认 false** ✓ ⇒ 老 16 支 + wc1/wc2 一个字节不走 ✓，
    //    那四条"与网页逐像素一致"的判据因此**逐字不动、继续全绿** ✓）。
    //    ⚠️ 开了它之后这一档**故意**与网页逐像素不一致（网页就是逐颗 source-over ✓）——
    //    **这是效果取向、不是 bug** ✗（用户原话见 `docs/61` ✓，以后别当 bug 来"修"✗）。
    //
    // 参数为什么这么配（㉟① 之后**颗粒改由纸纹承担** ✓，所以抖动可以放宽一点了 ✓）：
    //  · `spacing` 6 → **3**：覆盖度累积之后间距**不再拆轮廓** ✓，压太小只是白烧笔尖 ✗
    //   （3% 已经够密 ✓）；仍留着是为了"边缘坡更缓" ✓；
    //  · `scattering` / `sizeJitter` **各留一点点（2 / 4 ✓）**：轮廓由 `max` 保证 ✓，
    //    而这一点点抖动**只影响纹理与边缘的"不齐"** ✓ ⇒ 更像手绘、不像机器描的 ✓
    //    （用户要的"水彩"本来就不该是平涂 ✗）；
    //  · `density` / `opacity` = **单颗淡、靠累积** ✓（覆盖度取 max ⇒ 单颗多淡都不会掉浓度 ✓，
    //    而"淡"正好让边缘渐入 ✓）；
    //  · `paperStrength` **较低** ✓：颗粒细而不斑驳 ✓（颗粒**必须还在** ✓ ——
    //    用户要的是"纹理是笔画周围的贴图" ✓，不是平涂 ✗）。
    // -----------------------------------------------------------------------
    BrushPreset(
        "wc3", "comic.board.preset.wc3", 72,
        BrushSpec(
            spacing = 3f, scattering = 2f, sizeJitter = 4f, density = 44f, opacity = 58f,
            blending = 72f, water = 74f, colorStretch = 56f, paperOn = true, paperStrength = 22f,
            // ⚠️ 这一行就是本批的引擎改动入口 ✓（见上面那段说明 ✓）
            strokeCoverage = true,
        ),
    ),
    BrushPreset(
        "wc4", "comic.board.preset.wc4", 112,
        BrushSpec(
            spacing = 3f, scattering = 3f, sizeJitter = 6f, density = 38f, opacity = 52f,
            blending = 82f, water = 84f, colorStretch = 70f, paperOn = true, paperStrength = 28f,
            strokeCoverage = true,
        ),
    ),
)

// ---------------------------------------------------------------------------
// 6 大小选择盘（紧凑版：20 个常用值）
// ---------------------------------------------------------------------------

/**
 * **大小选择盘的那 20 档**（用户给的列表，对数刻度）。
 *
 * 为什么不是网页那 52 个点：网页是给大屏的，这里挤在 **320dp 宽**的左窗口里 ——
 * 20 档已经覆盖"描线 1~8 / 上色 20~100 / 铺底 200~1200"这三段常用手感。
 *
 * ⚠️ **800 / 1200 这两档现实中贴不到** ✗：本仓库的笔刷上限是
 * [ImageEditOps.BRUSH_MAX_PIXELS] = **600** ✓（`setComicPaintBrush` 会把它夹回来 ✓）——
 * 用户给的这张表里有它们 ✓，所以**照列**（点了不会崩、也不会出现"和显示不一致"的笔宽 ✓：
 * 数字框里会显示夹回来的那个数 ✓）。要真支持到 1200 得动引擎上限 ✗（这一批不碰 ✓）。
 */
val BrushSizeSteps: List<Int> = listOf(
    1, 2, 3, 5, 8, 12, 16, 20, 30, 40, 50, 70, 100, 150, 200, 300, 400, 600, 800, 1200,
)

/** 数字框里能接受的上下限（照用户给的阶梯，本仓库笔刷上限是 600 —— 预设里最大 150，都在之内）。 */
const val BRUSH_SIZE_MIN_PX = 1
const val BRUSH_SIZE_MAX_PX = 1200

// ---------------------------------------------------------------------------
// 4 预览画法（预设格 + 当前笔刷条共用）
// ---------------------------------------------------------------------------

/** 预览里那一笔的**固定颜色**（网页是 `#22262e` 深灰蓝，这里同色）。 */
const val PREVIEW_STROKE_ARGB: Int = 0xFF22262E.toInt()

/**
 * **真画一小段笔迹**（纯函数 —— 预设格与当前笔刷条**共用**它）。
 *
 * ## 怎么画的（照网页 `drawDemoStroke`，`docs/brush-lab.html` 1890 行）
 *
 *  * 白底铺满；
 *  * 走 **27 个采样点**：`x` 从左 6% 到右 94%、`y` 沿一条 `sin` 拱起、
 *    **压感 `0.1 → 1.0 → 0.1`**（`0.1 + 0.9 × sin(tπ)`，所以两头细中间粗）；
 *  * 每个点：方向 = 上一点到这一点（`angleControl = 自动` 时笔尖就跟着它转）、
 *    半径 = 预览半径 × 压感比（`ImageEditOps.pressureRadiusScale`，**和真落笔同一条公式**）、
 *    然后再进 [BrushEngine.planDab]（抖动 / 散布 / 计数 / 混色 / 颜色抖动）+
 *    [BrushEngine.stampPlan]（真的往像素上盖）；
 *  * 纸纹走 [BrushEngine.buildTile]（**固定 seed**）：同一套参数画两次**逐像素一致**
 *    （滑杆拖动时画面不会"跳"）。
 *
 * ## 两处如实说明（**别以为是逐像素还原真落笔**）
 *
 *  * 这是**独立的一小张画布**：混色 / 水分量采的是**它自己**（白底），
 *    不是真的那一页 —— 它的意义是"看得出这一套参数长什么样"，不是预览真画面；
 *  * **`keepOpacity` 在预览里不生效**（本仓库的"保持不透明度"要一整笔的缓冲，
 *    这里没走那条路）—— 好在网页那 16 个预设**一个都没开它**，
 *    所以预览和真笔迹在这一格上不会打架。
 *
 * @param radiusScale 相对倍率 —— 预设格要"小画布里也能看清"就传 3f（超采样），
 *   当前笔刷条按屏幕像素 1:1 就传 1f。
 * @return `width × height` 的 ARGB 像素（`null` = 尺寸不合法）
 */
fun brushPreviewPixels(
    brush: BrushSpec,
    brushPixels: Int,
    width: Int,
    height: Int,
    colorArgb: Int,
    seed: Long,
    radiusScale: Float = 1f,
): IntArray? {
    if (width < 8 || height < 8) return null
    val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
    val spec = brush.sanitized()
    val state = BrushEngine.StrokeState(Random(seed))
    // 纸纹 / 噪点：**固定 seed** ⇒ 同一套参数每次画出来一样（`needsTexture` 为假时直接跳过）
    val tile = if (spec.needsTexture) {
        BrushEngine.buildTile(spec, Random(BrushEngine.TILE_SEED))
    } else {
        null
    }
    val scale = if (radiusScale.isFinite() && radiusScale > 0f) radiusScale else 1f
    // 长边上限：`absoluteSize` / `shapeSize` 都可能给出一个巨大的图案 ——
    // 预览里给个上限（不然一颗笔尖就扫满整张；这条只是**预览**的事，不动引擎）。
    val baseRadius = (ImageEditOps.brushRadiusPixels(
        brushPixels = brushPixels.coerceIn(1, ImageEditOps.BRUSH_MAX_PIXELS),
        pressure = 1f,
        minRadiusRatio = ImageEditOps.DEFAULT_MIN_RADIUS_RATIO,
        pressureCurve = ImageEditOps.DEFAULT_PRESSURE_CURVE,
    ) * (spec.scaling / 100f) * scale).coerceIn(0.6f, max(0.8f, height * 0.6f))

    val steps = 26
    var prevX = 0f
    var prevY = 0f
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val x = width * 0.06f + width * 0.88f * t
        val y = height * 0.74f - sin(t * PI.toFloat()) * height * 0.4f
        val pressure = 0.1f + 0.9f * sin(t * PI.toFloat())
        val dir = if (i == 0) 0f else atan2(y - prevY, x - prevX)
        prevX = x
        prevY = y
        val radius = baseRadius * ImageEditOps.pressureRadiusScale(
            pressure = pressure,
            minRadiusRatio = ImageEditOps.DEFAULT_MIN_RADIUS_RATIO,
            pressureCurve = ImageEditOps.DEFAULT_PRESSURE_CURVE,
        )
        val plan = BrushEngine.planDab(
            brush = spec,
            state = state,
            sampleSource = pixels,
            sampleWidth = width,
            sampleHeight = height,
            x = x,
            y = y,
            baseRadius = radius,
            pressure = pressure,
            directionRadians = dir,
            colorArgb = colorArgb,
            erasing = false,
            pressureCurve = ImageEditOps.DEFAULT_PRESSURE_CURVE,
        )
        // 整档都在画布外就直接跳过（`planBounds` 是引擎里现成的那一个）
        if (BrushEngine.planBounds(plan, width, height) == null) continue
        BrushEngine.stampPlan(
            target = pixels,
            width = width,
            height = height,
            plan = plan,
            shape = BrushShape.ROUND,
            erasing = false,
            clip = null,
            texture = tile,
        )
    }
    return pixels
}
