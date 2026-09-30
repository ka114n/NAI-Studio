package com.kallan.naistudio.models

import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * **历史按 256 对齐的块来记**（第 ㉜a 批 ✓ —— 与 ㉔ 批 `LiveStrokeLayer.CHUNK` 同一个数 ✓）。
 *
 * 一笔长线如果记整块包围盒，A4 上对角一笔 ≈ 35 MB × 2 ✗（历史预算当场爆 ✓）；
 * 按块 = 一笔只占它真的碰过的几块 ✓。
 */
private const val BLOCK: Int = 256

/**
 * **画布编辑器的一次编辑会话**：一张可变像素缓冲 + 撤销/重做。
 *
 * 刻意**纯 Kotlin**（只有 `IntArray` + [EditHistory]），所以：
 *  · 涂一笔 → 撤销 → 像素必须**逐位回到原样**这种事能写 JVM 单测；
 *  · 手机与电脑共用同一份（读盘/落盘那一步在 `AppState` 里走 `ImageIo`）。
 *
 * ## 一笔的生命期
 *
 * `beginStroke` → `strokeTo` × N → `endStroke`。整笔是历史里的**一步**（见 [EditHistory.beginGroup]）；
 * 中途每段都按脏矩形记一份前后差量，所以撤销是精确的、也不吃内存。
 *
 * ## 一笔是怎么画出来的：**沿路径盖笔尖**（dab stamping ✓）
 *
 * 画笔 / 橡皮是"沿这一段按 [BrushSpec.spacing] 的步距走位、每个笔尖**现算半径与朝向**、
 * 各盖一次"✓（内核照 `docs/brush-lab-simple.html` 的 `stamp()` / `walkTo()` ✓）——
 * 半径**逐笔尖**跟着压力走（= **逐点压感** ✓）、朝向逐笔尖跟着笔画方向走
 *（= **笔尖自动朝向** ✓，见 [BrushEngine.nibAngleRadians] ✓）。
 * 模糊 / 图章仍走**整段一次**的老路（见 `strokeSegmentRadius` ✓）。
 *
 * ⚠️ 参数全部走默认值时，半径公式 = `基础半径 × (0.25 + 0.75 × 压力)` ✓ ——
 * 与加逐点压感之前**同一条公式** ✓（鼠标那条路（压力恒 1）画出来就是老行为 ✓）。
 *
 * ## 第二批（2026-09-20）：网页那**一整套**笔刷参数也搬进来了
 *
 * 散布 / 大小抖动 / 角度抖动 / 计数 / 颜色抖动 / 浓度 / 扩散和噪点 / 画用纸 / 混色 / 水分量 /
 * 色延伸 / 保持不透明度 —— 全在 [StrokeSpec.brush]（[BrushSpec] ✓）。
 * 引擎那一半在 [BrushEngine]（**纯函数** ✓，逐函数照网页搬 ✓）；这里负责三件"有状态"的事：
 *  · **随机数可复现**：`Random([StrokeSpec.seed])` ✓，**绝不碰全局随机** ✗；
 *  · **纸纹贴图**：128×128 一张、按参数缓存 ✓（见 [textureFor] ✓）；
 *  · **keepOpacity**：整笔先画在独立笔画缓冲上、抬笔时一次性合成 ✓（见 [commitKeepOpacityLayer] ✓）。
 * ⚠️ 参数面板**还没接**（下一批 ✓）—— 这一批只有引擎 + 单测在读这些参数 ✓。
 *
 * ## 四种笔（[StrokeMode]）
 *
 * 画笔 / 橡皮 / 模糊 / 图章 —— 几何一样（都是"沿着点走一段、按笔尖形状盖印"），
 * 差别只在**每个像素算出来的新值**：改色、清 alpha、取模糊值、取源点的像素。
 * 所以这里把"盖印"这一步按模式分派给 [ImageEditOps] 里对应的纯函数。
 *
 * ## 像素坐标
 *
 * 全部是**图片自身的像素坐标**（0..width-1 / 0..height-1），
 * 屏幕坐标 → 图片坐标的换算在界面层（`CanvasEditor`）完成。
 */
class ImageEditSession(
    width: Int,
    height: Int,
    pixels: IntArray,
    private val history: EditHistory = EditHistory(),
) {

    init {
        require(width > 0 && height > 0) { "画布尺寸必须为正：${width}x${height}" }
        require(pixels.size == width * height) {
            "画布尺寸不匹配：${width}x$height 需要 ${width * height} 个像素，实际 ${pixels.size}"
        }
    }

    /** 画布尺寸**可变**：调整画布大小会把整张换掉（见 [resizeCanvas]）。 */
    var width: Int = width
        private set

    var height: Int = height
        private set

    var pixels: IntArray = pixels
        private set

    /**
     * **层像素被改动的代次**（第 ㉔ 批 ✓ —— 每真的动到 [pixels] 一次就 +1 ✓）。
     *
     * 为什么要有它 ✗：活笔画缓冲让"一笔之内 [pixels] 不动、抬笔才动"成为常态 ✓，
     * 界面那张整页位图（`AppState.comicPaintImage()` ✓）必须能**廉价地分辨**
     * "现在这份缓存还对得上 [pixels] 吗" ✓ —— 比版本号（界面状态 ✓）更接近真源 ✓，
     * 是"漏了一处 `version++` 也不会画错"的那道安全网 ✓（顺便让单测能**数**重建次数 ✓）。
     */
    var pixelsRevision: Int = 0
        private set

    /** 动过 [pixels] 之后统一走这里 ✓（**只有这一个入口** ✗ —— 免得漏掉某一处 ✓）。 */
    private fun notePixelsChanged() {
        pixelsRevision++
    }

    val canUndo: Boolean get() = history.canUndo
    val canRedo: Boolean get() = history.canRedo

    /**
     * 历史里现在有**几步可撤** ✓（第 ㊵ 批加 ✓）。
     *
     * 为什么需要它 ✗：`AppState` 那条"鼠标先落笔、笔再接管"的对账逻辑要
     * **只撤回"刚刚那一笔真的进过历史"** 的那种情况 ✓ ——
     * 光看 [canUndo] 不够 ✗：它会**连别人（上一步）的历史一起撤掉** ✗，
     * 实机症状就是用户报的「**加上 ctrl+z 撤回，可以撤回所有操作**」✓
     *（每按一次笔就多撤一步 ✓ —— 因为「收掉的那一笔」如果什么都没碰到，
     *  `commitStrokeDirtyBlocks` 根本不 push ✓，此时 `undoComicPaint()` 撤的是**上一步** ✗）。
     */
    val historyDepth: Int get() = history.undoDepth

    /** 有没有正在画的一笔（指针还按着）。 */
    var isDrawing: Boolean = false
        private set

    // -----------------------------------------------------------------------
    // 「这次会话到底改没改过」—— 判据只有这一个
    // -----------------------------------------------------------------------

    /**
     * 这次编辑会话**有没有真的改过像素**。
     *
     * 每提交一步真正动了像素的操作（笔画 / 油漆桶 / 清除 / 删除选区内容 / HSV Apply /
     * 画布尺寸 / 落回浮动选区）就置 true；只有 `AppState.resetCanvas()`（回到进编辑器
     * 那一刻的原图）那种"确实回到原样"的情况才会调 [markClean] 清掉。
     *
     * ⚠️ **刻意不做逐像素比对**：832×1216 是 100 万个像素，每点一次「完成」扫一遍
     * 就是几毫秒到几十毫秒白烧 CPU —— 这里只记"发生过操作"这个布尔。
     *
     * 保守之处：撤销到底（像素其实回到了原样）也仍然算"改过"，宁可多问一次也不漏。
     *
     * 界面（`CanvasEditor` 的「完成」）与 `AppState.closeCanvasEditor(commit = true)`
     * **都只读它**，不许各写一份判断。
     */
    var hasChanges: Boolean = false
        private set

    /** 把"改过"标记清掉。只有调用方确认像素已回到进编辑器时那张图时才该调（「重置」）。 */
    fun markClean() {
        hasChanges = false
    }

    // -----------------------------------------------------------------------
    // 选区裁剪："有选区时就只在选区内工作"
    // -----------------------------------------------------------------------

    private var clipShape: SelectionShape? = null
    private var clipMask: ByteArray? = null

    /**
     * 告诉会话"现在的选区是哪个"。传 null = 没有选区（不裁）。
     *
     * 只存形状、不做运算：真正的逐像素掩码**等到落笔那一刻才建**（见 [selectionClipMask]），
     * 否则拖框的时候每一帧都要扫一遍整张图（832×1216 就是 100 万次判定）。
     */
    fun setSelectionClip(shape: SelectionShape?) {
        if (clipShape == shape) return
        clipShape = shape
        clipMask = null
    }

    // ---- 第 ㉝② 批：选区**几何**（给 Skia 的 `clipRect` / `clipPath` 用 ✓）----
    //
    // 与 [selectionClipMask] 是**同一件事的两种表示** ✓：
    //  · 掩码（`ByteArray`）：逐像素 0/1，给老路（逐像素光栅）用 ✓；
    //  · 几何（Skia）：`clipRect` / `clipPath` + `isAntiAlias = false` ⇒ **也是逐像素 0/1** ✓
    //    （Skia 的非抗锯齿裁剪按像素中心取样 ✓），所以两条路裁出来的像素集合一致 ✓
    //   （`SelectionClipParityTest` 里有逐像素对照 ✓）。
    private var clipRectSkia: org.jetbrains.skia.Rect? = null
    private var clipPathSkia: org.jetbrains.skia.Path? = null
    private var clipGeometryShape: SelectionShape? = null

    /** 选区 → Skia 裁剪几何（**形状没变就复用** ✓ —— 与 [selectionClipMask] 同一个惰性口径 ✓）。 */
    private fun selectionClipGeometry() {
        if (clipGeometryShape == clipShape) return
        clipPathSkia?.close()
        clipPathSkia = null
        clipRectSkia = null
        clipGeometryShape = clipShape
        when (val shape = clipShape) {
            null -> Unit
            is SelectionShape.Rect -> {
                val r = SelectionGeometry.bounds(shape, width, height) ?: return
                clipRectSkia = org.jetbrains.skia.Rect.makeLTRB(
                    r.x.toFloat(),
                    r.y.toFloat(),
                    (r.x + r.w).toFloat(),
                    (r.y + r.h).toFloat(),
                )
            }

            is SelectionShape.Polygon -> {
                val pts = shape.points
                if (pts.size < 3) return
                val path = org.jetbrains.skia.Path()
                // ⚠️ 填充规则必须是 **EVEN_ODD** ✓：`SelectionGeometry.contains` 用的就是
                //    even-odd 射线法（自交的套索靠它才裁得对 ✓），默认的 WINDING 会不一样 ✗。
                path.fillMode = org.jetbrains.skia.PathFillMode.EVEN_ODD
                path.moveTo(pts[0].x * width, pts[0].y * height)
                for (i in 1 until pts.size) path.lineTo(pts[i].x * width, pts[i].y * height)
                path.closePath()
                clipPathSkia = path
            }
        }
    }

    /** 逐像素的裁剪掩码（惰性建、形状没变就复用）。 */
    private fun selectionClipMask(): ByteArray? {
        val shape = clipShape ?: return null
        clipMask?.let { return it }
        val mask = ByteArray(width * height)
        when (shape) {
            // 矩形走快路：只判包围盒，逐像素比对省一个量级
            is SelectionShape.Rect -> {
                val rect = SelectionGeometry.bounds(shape, width, height) ?: return null
                for (y in rect.y until (rect.y + rect.h)) {
                    val row = y * width
                    java.util.Arrays.fill(mask, row + rect.x, row + rect.x + rect.w, 1)
                }
            }

            is SelectionShape.Polygon -> {
                for (y in 0 until height) {
                    val ny = (y + 0.5f) / height
                    val row = y * width
                    for (x in 0 until width) {
                        if (SelectionGeometry.contains(shape, (x + 0.5f) / width, ny)) mask[row + x] = 1
                    }
                }
            }
        }
        clipMask = mask
        return mask
    }

    /**
     * **把选区里的像素清空**（退格 / 删除键）。
     *
     * 只对选区内、且当前不是全透明的像素动手；整件事算**一步**历史（可撤销）。
     * 有浮动块时先落回（否则清的是"已经搬走"的那块坐标）。
     */
    fun clearSelectionRegion(shape: SelectionShape): Boolean {
        endStroke()
        if (floating != null) commitSelection()
        val rect = SelectionGeometry.bounds(shape, width, height) ?: return false
        val before = ImageEditOps.copyRect(pixels, width, height, rect)
        var touched = 0
        for (y in rect.y until (rect.y + rect.h)) {
            val row = y * width
            val ny = (y + 0.5f) / height
            for (x in rect.x until (rect.x + rect.w)) {
                if (!SelectionGeometry.contains(shape, (x + 0.5f) / width, ny)) continue
                if (pixels[row + x] == ImageEditOps.TRANSPARENT) continue
                pixels[row + x] = ImageEditOps.TRANSPARENT
                touched++
            }
        }
        if (touched == 0) return false
        markPixelsDirty(rect)
        history.beginGroup()
        history.push(
            PixelDelta(
                rect = rect,
                before = before,
                after = ImageEditOps.copyRect(pixels, width, height, rect),
            ),
        )
        history.endGroup()
        hasChanges = true
        return true
    }

    /** 这一笔的**基础半径**（= 笔刷直径 / 2 ✓，**压力还没乘上来** ✓）。 */
    private var strokeBaseRadius = ImageEditOps.DEFAULT_BRUSH_PIXELS / 2f

    /**
     * **非 dab 模式**（模糊 / 图章 ✓）用的半径：由**落笔那一刻**的压力定一次、整笔不变 ✓。
     *
     * 为什么不给它们逐点压感：模糊每一段都要"快照 + 盒式模糊"（拆成笔尖会成倍变慢 ✗）、
     * 图章的源点偏移也要求整段一次 ✓ —— 所以这两种**照旧整段一次** ✓
     *（用户口径：「如果它也走 `strokeTo`，给它一个恒压分支即可」✓）。
     */
    private var strokeSegmentRadius = ImageEditOps.DEFAULT_BRUSH_PIXELS / 2f

    private var strokeSpec = StrokeSpec()
    private var lastX = 0f
    private var lastY = 0f

    /** 上一个点那一档的压力 ✓ —— 一段之内从它线性插到新压力 ⇒ **逐点压感** ✓。 */
    private var lastPressure = 1f

    // -----------------------------------------------------------------------
    // 笔刷引擎（第二批：把网页 `docs/brush-lab-simple.html` 那一整套搬进来 ✓）
    //
    // 三件事在这里汇合：
    //  · **随机数**：一律走 [brushRandom]（`kotlin.random.Random(seed)` ✓）——
    //    绝不碰 `Math.random()` / `Random.Default` ✗（用户口径：「随机数要可复现」✓）；
    //  · **纸纹贴图**：按参数缓存一份 128×128（同一套参数不重建 ✓，见 [textureFor] ✓）；
    //  · **keepOpacity 的独立笔画缓冲**：整笔先画在它上面（alpha = 1 ✓），抬笔时一次性合成 ✓。
    // -----------------------------------------------------------------------

    /** 这一笔的随机源（[StrokeSpec.seed] 决定 ✓）—— 同一 seed 两次跑**逐像素一致** ✓。 */
    private var brushRandom: Random = Random(0)

    /** 走位 / 混色要跨笔尖带的状态（**上一颗笔尖的混色结果**就在里面 ✓，色延伸要用 ✓）。 */
    private var brushState: BrushEngine.StrokeState = BrushEngine.StrokeState(Random(0))

    /** 这一笔的纸纹 / 噪点贴图（不需要时是 null ✓ —— 那就一个查表都不做 ✓）。 */
    private var strokeTexture: ByteArray? = null

    /**
     * **画布版本号**（第 ㉜b 批 ✓）—— 每真的落下一颗笔尖 +1 ✓。
     *
     * 干嘛用的：混色采样的"复用上一次结果"那条省法**只有在画布没变过时才成立** ✗
     *（笔一落下去画布就变了 ⇒ 采到的应该是"带着刚画上去那一笔"的样子 ✓，与网页每颗都重采同口径 ✓）。
     * 见 `BrushEngine.StrokeState.sampleEpoch` ✓。
     */
    private var canvasEpoch: Int = 0

    /** 纸纹贴图的缓存键（参数没变就复用 ✓ —— 128×128 生成一次是有成本的 ⚠️）。 */
    private var tileKey: TileKey? = null
    private var tileCache: ByteArray? = null

    private data class TileKey(
        val paperOn: Boolean,
        val paperStrength: Float,
        val spreadNoise: Boolean,
        val spreadNoiseStrength: Float,
    )

    // -----------------------------------------------------------------------
    // **层真源 = 一张 skiko `Surface`**（第 ㉜a 批 ✓ —— 用户口径：「用 Kotlin 把网页那张画布
    // **一比一移植**进软件」✓、**不嵌浏览器** ✗）
    //
    // 网页那套流畅的根子是**三件事同时成立**（见 `docs/52` §1 ✓），这一批把第 1 条搬进来了：
    //  · 笔**直接画在唯一那张画布上** ✓ —— 现在层本身就是一张 `Surface`，
    //    `stampDabAt` 把抗锯齿椭圆**直接画上去**（[SkiaDabRaster.stampOnSurface] ✓），
    //    **没有**活笔画缓冲、**没有**小块叠加、**没有**抬笔合层 ✓（㉔/㉖ 那三样整条删掉 ✓）；
    //  · canvas 分辨率 ≈ 显示分辨率（显示那一半在 `ComicModeScreen` ✓，走**视口**分辨率 ✓）；
    //  · 一帧多点采样（㉜c ✓，本批不做 ✗）。
    //
    // 分工（**一句话**）：
    //  · **表面 = 画画的真源** ✓（笔画落在它上面；橡皮 = `DST_OUT`，与网页同口径 ✓）；
    //  · [pixels] = **落盘 / 撤销差量 / PSD 的缓存** ✓ —— 每颗笔尖之后**只回读这一档笔尖那一块**
    //    （[SkiaDabRaster.readRegionInto] ✓，一把 JNI ✓），所以它**任何时刻都是对的** ✓
    //    （`comicPaintImage()` 那条"整页位图"的路子不用了 ✓）。
    //
    // ⚠️ **显示不许每帧 `makeImageSnapshot()`** ✗✗：快照本身 0.001 ms（写时复制 ✓），
    // 但**拿着快照继续往表面上画**会触发**整页拷贝** —— 实测 A4（2480×3508）：
    // 纯画一颗笔尖 **0.0102 ms**、每颗之前快照一次 **7.04 ms/颗（×690）**、快照后第一次画 **6.9 ms** ✓
    // （见 `SurfaceCanvasProbeTest` 与 `docs/54` ✓）。显示走"**区域回读**"（525×743 = 0.076 ms ✓）。
    // -----------------------------------------------------------------------

    /** 这一层的**光栅表面**（惰性创建 ✓；`null` = 还没建 ✓）。 */
    private var layerSurface: Surface? = null

    /**
     * 页缓冲（[pixels]）里**改过、还没传回表面**的那一块（第 ㉜a 批 ✓）。
     *
     * 为什么要有它：撤销 / 油漆桶 / HSV / 选区这些操作现在仍然在 `IntArray` 上算
     *（㉜b 才把依赖接管 ✓），算完必须把那一块**整块传回表面**（[SkiaDabRaster.writeRegionFrom] ✓）——
     * 但"算完立刻传"会在 HSV 预览那种连续调用上白烧 ✗，所以记一块、**等真要用表面时再传** ✓。
     */
    private var pendingUpload: EditRect? = null

    /** 这一笔碰过的 256 对齐块（键 = `blockIndex` ✓）→ **落笔前**的快照 ✓（历史按块记 ✓）。 */
    private val strokeDirtyBlocks = LinkedHashMap<Int, IntArray>()

    /** 这一笔是不是"**直接画在表面上**"那条路（落笔时定死 ✓ —— 中途不改 ✗）。 */
    private var strokeOnSurface = false

    /**
     * 上一笔（开笔那一刻定的 ✓）是不是走了"**直接画在层表面上**"那条路 ✓ ——
     * 判据 / 实测报告用（第 ㉜b 批 ✓）：混色笔接上表面之后这里必须是 `true` ✓，
     * 不然"混色到底走没走 Skia"就只能靠嘴说 ✗。
     */
    var strokeOnLayerSurface: Boolean = false
        private set

    /**
     * **每颗笔尖的诊断记录**（第 ㉜b 批 ✓；默认关 ✓ —— 生产上一个字节都不记 ✓）。
     *
     * 干嘛用的：判据要"网页真值 vs Kotlin **逐颗笔尖**的采样色 / 半径 / 步距"逐项对齐 ✓，
     * 而这些数全在 [BrushEngine.planDab] 的局部变量里 ⇒ 不把它们带出来就只能靠嘴说 ✗。
     * 打开方式：`dabProbeEnabled = true`（只有测试与探针会开 ✓），读完 [dabProbeLog] 自己清 ✓。
     */
    var dabProbeEnabled: Boolean = false

    /** 见 [dabProbeEnabled] ✓。 */
    val dabProbeLog: MutableList<DabProbe> = ArrayList()

    /** 一条笔尖诊断（第 ㉜b 批 ✓ —— 见 [dabProbeLog] ✓）。 */
    data class DabProbe(
        /** 这一笔里的第几颗（0 起 ✓，与走位顺序一致 ✓）。 */
        val index: Int,
        /** 这一颗笔尖的中心（**页面像素** ✓，= 走位给的那个点 ✓ —— 与网页 recorder 记的是同一个 ✓）。 */
        val x: Float,
        val y: Float,
        /** 这一颗的半径（压感 / 抖动之后 ✓）。 */
        val radius: Float,
        val pressure: Float,
        val directionRadians: Float,
        /** 这一颗之后用的步距（[ImageEditSession.dabStepPixels] ✓）。 */
        val stepPixels: Float,
        /** 计划（含混色诊断 ✓ 与要盖的那几颗形状 ✓）。 */
        val plan: BrushEngine.DabPlan,
    )

    /**
     * 这一笔是不是 **`keepOpacity`**（"整笔一个浓度" ✓）—— 落笔时定死 ✓。
     *
     * ⚠️ 为什么收笔时要补一步合成：`keepOpacity` 的引擎口径（[BrushEngine.dabAlpha] ✓）是
     * "每颗笔尖的 alpha 顶成 1、**抬笔时按整笔浓度合成一次**" ✗ —— 第 ㉜a 批把"抬笔合层"
     * 整条删掉之后这一下就丢了 ✗，结果同一处反复涂会一路叠到 255（= "保持不透明度"失效 ✗）。
     * 所以按老口径在 [commitStrokeDirtyBlocks] 里补回来 ✓（用现成的 [ImageEditOps.overPixel] ✓）。
     */
    private var strokeKeepsOpacity = false

    /**
     * 这一笔是不是走**笔画级覆盖度累积**那条路（第 ㉟① 批 ✓ —— 落笔时定死 ✓，中途不改 ✗）。
     *
     * 见 [BrushSpec.strokeCoverage] 的长说明 ✓：开着时这一笔**每颗笔尖只累积覆盖度**（取 `max` ✓）、
     * **不碰层表面** ✗；抬笔那一下才 [SkiaDabRaster.compositeCoverage] **一次性**合成 ✓
     * ⇒ 轮廓天生连续 ✓、间距 / 抖动再怎么调都不会把它拆成一颗颗圆 ✓。
     */
    private var strokeCoverageOn = false

    /**
     * 这一笔碰过的**并集矩形**（只有 [strokeCoverageOn] 时用 ✓）——
     * 抬笔合成只需要这一块 ✓（A4 整页 memcpy 是 33 MB ✗，那是白烧 ✓）。
     */
    private var strokeCoverageBounds: EditRect? = null

    /** 上一笔是不是走了**笔画级覆盖度累积** ✓（判据 / 实测报告用 ✓，与 [strokeOnLayerSurface] 同一个路子 ✓）。 */
    var strokeUsedCoverageAccumulation: Boolean = false
        private set

    /**
     * 上一笔累积了几颗笔尖进覆盖度缓冲 ✓（0 = 没走这条路 ✓）。
     * 判据要"证明它真的走上了这条路"就看这两个数 ✓（与 `strokeOnLayerSurface` 同源 ✓）。
     *
     * ⚠️ 读的是 [SkiaDabRaster.lastCoverageDabs]（**抬笔之后仍然在** ✓）—— 不是 `coverageDabs` ✗：
     * 那个在 `endCoverage()` 里被清了 ✓（第一版就踩了这个，判据读到恒 0 ✗）。
     */
    val strokeCoverageDabs: Long get() = SkiaDabRaster.lastCoverageDabs

    /** 诊断：层表面创建了几次 / 上传了几块 ✓（只在探针与日志里读 ✓）。 */
    var surfaceUploads: Int = 0
        private set

    /** 诊断：回读（表面 → 页缓冲）了几块 ✓。 */
    var surfaceReadbacks: Int = 0
        private set

    /** 层表面**真的分配了多少字节**（诊断 / 峰值内存实测 ✓，A4 = 33.2 MB ✓）。 */
    val layerSurfaceBytes: Int get() = if (layerSurface == null) 0 else width * height * 4

    /** 这一层的表面在不在（显示那一半据此走直画 ✓）。 */
    val hasLayerSurface: Boolean get() = layerSurface != null

    /**
     * **拿这一层的表面**（没有就惰性建一张 + 把当前像素传上去 ✓）。
     *
     * 建一张 A4 表面本身只要 0.010 ms（只分配、不碰像素 ✓）；**只有层里已经有内容时**
     * 才需要那一次整页上传（实测 **26.6 ms** / A4，其中 memcpy 3.3 ms ✓ —— 见 `docs/54` ✓）。
     *
     * ⛔ **第 ㊿i 批（2026-09-22）修：「聚焦哪个图层那个图层就会消失」** ✓
     *
     * **根因就在这一行**：表面原来是**只在落第一笔时**才建的（[ensureLayerSurface] 的调用点
     * 全在笔画 / 回读那几条路上 ✓），而**正在编辑的那一层**在画布上**只由这张表面画出来**
     *（`ComicPaintViewportLayer` → `comicPaintViewportFrame` ✓ —— 它在聚焦期间
     * **不画盘上那张 `FileImage`** ✓，理由是橡皮要能真的擦出透明 ✓）。
     * ⇒ 聚焦之后、落笔之前：表面还不存在 ⇒ 视口帧恒为 null ⇒ **这一层一个像素都不画** ✗
     *（用户看到的正是"点哪一层哪一层就没了" ✓）。
     *
     * ⇒ 显示这条路**自己要保证表面在** ✓（惰性建 ✓，只建一次 ✓）：
     * A4 那 26.6 ms 一次性上传，比"图画凭空消失"便宜太多 ✓，而且下一次读就是缓存 ✓。
     */
    fun layerSurfaceForDisplay(): Surface? {
        flushPendingUploads()
        return layerSurface ?: ensureLayerSurface()
    }

    /**
     * **建表面**（惰性 ✓）—— 把当前页缓冲整个传上去，之后笔画就画在它上面 ✓。
     *
     * 层是空的（全透明）时**不做那次整页上传** ✓（新开的绘画层绝大多数是这一档 ✓）。
     */
    private fun ensureLayerSurface(): Surface? {
        layerSurface?.let { return it }
        val s = runCatching { Surface.makeRasterN32Premul(width, height) }
            .onFailure { SkiaDabRaster.lastError = "makeRasterN32Premul(${width}x$height): ${it.message}" }
            .getOrNull() ?: return null
        layerSurface = s
        pendingUpload = null
        // 层里已经有内容 ⇒ 整页传一次（**空层跳过** ✓：花 8.7M 次扫描换 26 ms 不值得，
        // 而"新开的绘画层全透明"是绝大多数情况 ✓）。扫描本身 ~5 ms，比上传便宜一个量级 ✓。
        if (hasAnyInk()) {
            uploadRectToSurface(EditRect(0, 0, width, height))
        }
        return s
    }

    private fun hasAnyInk(): Boolean {
        for (p in pixels) if ((p ushr 24) and 0xFF != 0) return true
        return false
    }

    /** 把页缓冲的 [rect] 整块传回表面 ✓（表面没建就什么都不做 —— 建的时候会整页传 ✓）。 */
    private fun uploadRectToSurface(rect: EditRect) {
        val s = layerSurface ?: return
        val safe = rect.clampTo(width, height)
        if (safe.isEmpty) return
        if (SkiaDabRaster.writeRegionFrom(s, safe.x, safe.y, safe.w, safe.h, pixels, safe.y * width + safe.x, width)) {
            surfaceUploads++
        }
    }

    /** 记一块"页缓冲改过了、还没传回表面" ✓（惰性 ✓）。 */
    private fun markPixelsDirty(rect: EditRect) {
        if (layerSurface == null) return
        val safe = rect.clampTo(width, height)
        if (safe.isEmpty) return
        pendingUpload = pendingUpload?.union(safe) ?: safe
    }

    /** 把待传的那一块（并成一块 ✓）传回表面 ✓。 */
    private fun flushPendingUploads() {
        val pending = pendingUpload ?: return
        pendingUpload = null
        uploadRectToSurface(pending)
    }

    /**
     * **从表面回读一块进页缓冲** ✓（快路一把读 ✓，见 [SkiaDabRaster.readRegionInto] ✓）。
     *
     * ⚠️ 这一条是"表面 → 缓存"唯一的入口 ✗ —— 每颗笔尖之后**只回读这一档笔尖那一块** ✓，
     * 所以 [pixels] 全程都是对的（撤销差量、落盘、PSD 直接读它 ✓）。
     */
    private fun readBackFromSurface(rect: EditRect): EditRect? {
        val s = layerSurface ?: return null
        val safe = rect.clampTo(width, height)
        if (safe.isEmpty) return null
        val ok = SkiaDabRaster.readRegionInto(
            surface = s,
            x = safe.x,
            y = safe.y,
            w = safe.w,
            h = safe.h,
            out = pixels,
            outOffset = safe.y * width + safe.x,
            outStride = width,
        )
        if (ok) surfaceReadbacks++
        return if (ok) safe else null
    }

    /** 这一块（256 对齐 ✓）的页坐标矩形 ✓。 */
    private fun blockRect(index: Int): EditRect {
        val perRow = (width + BLOCK - 1) / BLOCK
        val bx = index % perRow
        val by = index / perRow
        return EditRect(bx * BLOCK, by * BLOCK, BLOCK, BLOCK).clampTo(width, height)
    }

    /** 这一块碰过没有；没碰过就把**落笔前**的内容记下来 ✓（历史按块记 ⇒ 一笔只占几块 ✓）。 */
    private fun touchBlocks(rect: EditRect) {
        val safe = rect.clampTo(width, height)
        if (safe.isEmpty) return
        val perRow = (width + BLOCK - 1) / BLOCK
        val bx0 = safe.x / BLOCK
        val by0 = safe.y / BLOCK
        val bx1 = (safe.x + safe.w - 1) / BLOCK
        val by1 = (safe.y + safe.h - 1) / BLOCK
        for (by in by0..by1) {
            for (bx in bx0..bx1) {
                val index = by * perRow + bx
                if (strokeDirtyBlocks.containsKey(index)) continue
                val rect = blockRect(index)
                if (rect.isEmpty) continue
                strokeDirtyBlocks[index] = ImageEditOps.copyRect(pixels, width, height, rect)
            }
        }
    }

    /**
     * **收笔时按块记历史** ✓（第 ㉜a 批 ✓ —— 从"活笔画缓冲 → 抬笔合层"换成这个 ✓）。
     *
     * 为什么按 256 块：一笔长线如果记**整块包围盒**，A4 上对角一笔 ≈ 35 MB × 2 ✗（历史预算当场爆 ✓）；
     * 按块 = 一笔只占它真的碰过的几块 ✓（和 ㉔ 批那条"最大一块 ≤ 整页/8"同源 ✓）。
     *
     * ⚠️ 这里**不回读** ✗：每颗笔尖之后已经回读过了（[pixels] 全程是对的 ✓）⇒ 这里只是把它们
     * 按块记成历史的**一步**（一笔 = 一组 ✓，可撤销 ✓）。
     */
    private fun commitStrokeDirtyBlocks() {
        if (strokeDirtyBlocks.isEmpty()) return
        // `keepOpacity`："整笔一个浓度" ✓ —— 每颗笔尖都按 alpha = 1 落的笔（[BrushEngine.dabAlpha] ✓），
        // 所以这里按整笔浓度把它们**一次性合成**回来 ✓（老口径 = [BrushEngine.compositeLayer] ✓，
        // 这里逐像素用同一个 [ImageEditOps.overPixel] ✓）。不是这一档时 `k = 1` = 原样 ✓。
        val keepOpacity = if (strokeKeepsOpacity) {
            BrushEngine.strokeCompositeAlpha(strokeSpec.brush)
        } else {
            1f
        }
        var touchedAny = false
        for ((index, before) in strokeDirtyBlocks) {
            val rect = blockRect(index)
            if (rect.isEmpty) continue
            val raw = ImageEditOps.copyRect(pixels, width, height, rect)
            val after = if (keepOpacity < 1f) {
                val blended = IntArray(raw.size)
                for (i in raw.indices) blended[i] = ImageEditOps.overPixel(before[i], raw[i], keepOpacity)
                blended
            } else {
                raw
            }
            if (after.contentEquals(before)) continue
            // 合成结果要**真的落到层里** ✓（写回 `pixels` + 记一块待传回表面 ✓）——
            // 否则显示那一半读的还是"没合成的 alpha = 1" ✗。
            if (keepOpacity < 1f) {
                ImageEditOps.writeRect(pixels, width, height, rect, after)
                markPixelsDirty(rect)
            }
            history.push(PixelDelta(rect = rect, before = before, after = after))
            touchedAny = true
        }
        strokeDirtyBlocks.clear()
        if (touchedAny) {
            hasChanges = true
            notePixelsChanged()
        }
    }

    /**
     * **这一笔的墨迹要不要"先记在活笔画缓冲上、抬笔时才合进 [pixels]"** ——
     * ⚠️ 第 ㉜a 批**整条删掉了** ✗：笔现在直接画在层表面上（[layerSurface] ✓），
     * 抬笔时**没有任何合成**（[commitStrokeDirtyBlocks] 只是把回读好的像素按块记成历史 ✓）。
     *
     * 这个口子留着（恒 `true` ✓）只是为了让"这是哪一条路"在日志 / 探针里还有个名字 ✓。
     */
    val strokesGoStraightToTheLayerSurface: Boolean get() = true

    /** 这一笔是不是**橡皮** ✓（界面据此知道层里的 alpha 真的被减掉了 ✓）。 */
    val liveStrokeErasing: Boolean
        get() = isDrawing && strokeSpec.mode == StrokeMode.ERASE

    /** **dab 走位的累加器**（离下一个笔尖还有多远 ✓，跨段带着走 ✓ —— 照 `brush-lab` 的 `carry` ✓）。 */
    private var dabCarry = 0f

    /**
     * **输入侧稳定器（EMA ✓）** —— 第 ㉚ 批照 `docs/brush-lab-simple.html` 搬的 ✓，
     * 只在 `StrokeSpec.stabilizePercent > 0` 时参与 ✓（默认 0 = 关，判据①口径 ✓）。
     */
    private val strokeStabilizer = StrokeStabilizer()

    /** 这一笔喂进来的**原始采样点**数（诊断 / 实测输入率 ✓，不参与画面 ✓）。 */
    private var strokeInputPoints = 0

    /** 这一笔起止时刻（纳秒 ✓）—— 只用来算**实测输入率** ✓，不参与画面、不进断言 ✓。 */
    private var strokeStartedAtNanos = 0L
    private var strokeElapsedNanos = 0L

    /**
     * [dabCarry] 的**配套基准**：**上一颗笔尖那一刻**的步距（由那一颗笔尖自己的半径算出来 ✓）。
     *
     * ⚠️ 这两个数必须同口径 —— 混用基础半径的话，跨段时"还差多远"就失真了 ✗
     *（用户 2026-09-20 实机报的「画出来是一排点」：步距用一个半径、笔尖用另一个半径 ✗）。
     */
    private var dabStepPixels = ImageEditOps.DAB_MIN_STEP_PIXELS

    /** 最后一个笔画方向（弧度 ✓）—— `angleControl == 1`（自动 ✓）用它 ✓。 */
    private var strokeDirection = 0f

    // -----------------------------------------------------------------------
    // **这一笔的 dab 走位统计**（用户 2026-09-20：「还是一排点」⇒ 要**数字**，不要"我觉得"✓）
    //
    // 现场没有调试器、也没有数位板那台机器 ✗ —— 唯一能把真相带回来的东西就是
    // `[PenDab]` 那两条日志（见 `AppState.beginComicPaintStroke` / `endComicPaintStroke` ✓）。
    // 统计口径（**全部真算** ✓，不是估的）：
    //  · `dabs`：这一笔一共走过几颗笔尖（`stampDabAt` 被调了几次 ✓）；
    //  · `stamped`：其中**真的改到像素**的那几颗（返回 true 的 ✓）；
    //  · `distance`：路径总长（原图像素 ✓）；
    //  · `radiusMin/Max`：每一颗笔尖**当场算出来**的半径（压感之后的 ✓）；
    //  · `stepMin/Max`：每一颗笔尖那一刻的步距（[ImageEditOps.dabStepPixels] 的返回值 ✓）；
    //  · `worstStepOverRadius`：**最坏的 `step / (2 × radius)`** ✓ —— 这就是"会不会断"的判据：
    //    正常必须 `≤ 0.5`（两颗笔尖至少重叠一半 ✓），`> 1` = 相邻两颗笔尖完全不挨着 = **必断** ✗。
    // -----------------------------------------------------------------------

    private var dabCount = 0
    private var dabStampedCount = 0
    private var dabDistance = 0f
    private var dabRadiusMin = Float.MAX_VALUE
    private var dabRadiusMax = 0f
    private var dabStepMin = Float.MAX_VALUE
    private var dabStepMax = 0f
    private var dabWorstStepOverRadius = 0f

    /**
     * 这一笔走了几段（= `strokeTo` 真的走起来的次数 ✓）。
     *
     * ⚠️ **这条是"一排点"最直接的判据** ✓：`dabs / segments` = 每一段走了几颗笔尖 ✓ ——
     *  · `≈ 1`（每个输入事件只盖一颗）⇒ 点距 = **手速 × 采样间隔** ✗✗
     *    （屏幕上就是"等距的小点"，而且**间距会跟着手速变** ✓ —— 这正是用户那张图的样子 ✓）；
     *  · `≫ 1`（一段里落好几颗）⇒ 走位真的在工作 ✓。
     */
    private var dabSegments = 0

    /**
     * 记一颗笔尖（走位那一半每盖一颗就调一次 ✓）。
     *
     * ⚠️ 只累加**数字** ✓ —— 不写盘、不打日志、不碰像素（每颗笔尖打一行会把 `app.log` 淹掉 ✗，
     * 用户点名不许 ✓）。
     */
    private fun noteDab(radius: Float, step: Float, stamped: Boolean) {
        dabCount++
        if (stamped) dabStampedCount++
        val r = if (radius.isFinite()) radius.coerceAtLeast(0.5f) else 0.5f
        val s = if (step.isFinite()) step.coerceAtLeast(0f) else 0f
        if (r < dabRadiusMin) dabRadiusMin = r
        if (r > dabRadiusMax) dabRadiusMax = r
        if (s < dabStepMin) dabStepMin = s
        if (s > dabStepMax) dabStepMax = s
        val ratio = s / (2f * r)
        if (ratio > dabWorstStepOverRadius) dabWorstStepOverRadius = ratio
    }

    private fun resetDabStats() {
        dabCount = 0
        dabStampedCount = 0
        dabDistance = 0f
        dabRadiusMin = Float.MAX_VALUE
        dabRadiusMax = 0f
        dabStepMin = Float.MAX_VALUE
        dabStepMax = 0f
        dabWorstStepOverRadius = 0f
        dabSegments = 0
    }

    /**
     * **这一笔到此刻的走位统计**（收笔时写进 `[PenDab]` 日志 ✓，单测也读它 ✓）。
     *
     * 没有笔尖时返回全 0（`hasDabs == false` ✓）—— 调用方据此决定要不要打那条汇总 ✓。
     */
    fun strokeDabStats(): StrokeDabStats = StrokeDabStats(
        dabs = dabCount,
        stamped = dabStampedCount,
        segments = dabSegments,
        distance = dabDistance,
        radiusMin = if (dabRadiusMin == Float.MAX_VALUE) 0f else dabRadiusMin,
        radiusMax = dabRadiusMax,
        stepMin = if (dabStepMin == Float.MAX_VALUE) 0f else dabStepMin,
        stepMax = dabStepMax,
        worstStepOverRadius = dabWorstStepOverRadius,
        spacingPercent = strokeSpec.brush.spacing,
        nibRatio = strokeSpec.brush.nibRatio,
        angleControl = strokeSpec.brush.angleControl,
        baseRadius = strokeBaseRadius,
        samples = brushState.sampleCalls,
        sampleReuses = brushState.sampleReuses,
    )

    /**
     * 开始一笔（在图片像素坐标 [x],[y] 处）。
     *
     * 立刻落下第一个点 —— 官方那种"点一下也留一个墨点"的手感，
     * 否则轻点一下什么都不出现，用户会以为工具坏了。
     *
     * @param pressure 落笔那一刻的**笔压** 0..1 ✓（默认 1 = 没有压感 ✓，
     *   也就是"加逐点压感之前"那一笔 ✓ —— 鼠标 / 触摸那条路传的就是默认值 ✓）。
     * @return 真的盖下了一个笔尖才 true ✓（整笔在画布外 / 被选区裁光时 false ✓）。
     */
    fun beginStroke(x: Float, y: Float, spec: StrokeSpec, pressure: Float = 1f): Boolean {
        // ⚠️ 上一笔还没收口就被新开一笔（抬笔事件丢了 / 两条输入通道打架 ✓）：
        //  **先把上一笔合并进层像素**再开新的 ✗ —— 活笔画缓冲里的墨迹要是直接丢掉，
        //  那半笔就凭空消失了 ✓（第 ㉔ 批之前是"每颗笔尖已经写进层像素"，没这个问题 ⚠️）。
        if (isDrawing) endStroke()
        history.beginGroup()
        isDrawing = true
        // ⚠️ 参数在这里**统一收敛一次** ✓（滑杆 / 预设 JSON 里的 NaN 一旦漏进半径，
        //    就是"笔尖一个像素都盖不上还看不出报错" ✗，见 `BrushSpec.sanitized` 的说明 ✓）
        strokeSpec = spec.copy(brush = spec.brush.sanitized())
        strokeBaseRadius = ImageEditOps.normalizedBrushPixels(spec.brushPixels) / 2f
        strokeSegmentRadius = ImageEditOps.brushRadiusPixels(
            spec.brushPixels,
            pressure,
            spec.minRadiusRatio,
            spec.pressureCurve,
        )
        lastX = x
        lastY = y
        lastPressure = if (pressure.isFinite()) pressure.coerceIn(0f, 1f) else 1f
        strokeDirection = 0f
        // 随机数：**只有这一个来源** ✓（seed 由 [StrokeSpec.seed] 给 ✓ ⇒ 可复现 ✓）
        brushRandom = Random(spec.seed)
        brushState = BrushEngine.StrokeState(brushRandom)
        strokeTexture = textureFor(strokeSpec.brush)
        // ---- 第 ㉜a 批：笔**直接画在层表面上** ✓ ----
        // 走上表面那条路的条件（不满足就**如实走老路** ✗，见 `SkiaDabRaster` 的说明 ✓）：
        //   · 没有纸纹 / 扩散噪点（`strokeTexture == null` ✓）  ← ㉜c 拆掉 ✗
        //   · 没有选区裁剪（`selectionClipMask() == null` ✓）  ← ㉝② 拆掉 ✗
        //   · 圆笔尖（`BrushShape.ROUND` ✓）                    ← ㉝② 拆掉（方笔尖也上表面 ✓）
        //   · **软圆（`SOFT_ROUND`）仍然走老路** ✓ —— 它要的是"从半径一半处开始羽化"的
        //     **解析式软边** ✓，Skia 的抗锯齿椭圆给不了那个（硬边 ✓）；这条**故意留着** ✓
        //     （`CanvasStrokeCompatTest` ② 钉着"软圆必须还有半透明边" ✓）。
        //
        // 橡皮**也走表面** ✓（`DST_OUT`，与网页 `globalCompositeOperation="destination-out"` 同口径 ✓）——
        // ㉙ 批那条"直接写层时橡皮会画白"的坑随着"层就是表面"**自然消失** ✓。
        //
        // ---- 第 ㉜b 批：**混色类也走表面** ✓（用户：「水彩笔一类的画笔还是能看出是一个个的点」✗）----
        // 原来这里还有一条 `!brush.wantsMix` ✗ —— 混色 / 水分 / 色延伸（`blending / water /
        // colorStretch > 0` ✓）被它挡在门外 ⇒ 自动退回**手写 coverage 光栅**（[ImageEditOps.stampDab] ✓），
        // 于是同一支笔"开一个参数就换了一个光栅器"：普通笔是 Skia 抗锯齿椭圆 ✓、
        // 混色笔是逐像素解析羽化 ✗ ⇒ 观感就是"一颗一颗的点" ✗。
        //
        // ---- 第 ㉜c 批：**纸纹 / 扩散噪点也走表面** ✓（`strokeTexture != null` 那一条也拆了 ✗）----
        // 为什么非拆不可：**出厂那批水彩 / 混色预设全是 `paperOn = true`** ✗
        //（`BrushPanelData.kt` 的 wc1/wc2/paint1/paint2/blend1/omni/mix1/mix2/fusion ✓）——
        // ㉜b 把混色接上去之后，用户点开一看**一点没变** ✓，因为它们走的还是纸纹这条老路 ✗。
        // 纸纹的口径在 [SkiaDabRaster.stampOnSurface] 里（**乘法作用在笔迹 alpha 上** ✓，
        // 与网页 `texturedDab()` 的净效果一致 ✓）。
        //
        // ---- 第 ㉝② 批：**选区裁剪 + 方笔尖也走表面** ✓（`docs/51` 那四条里最后两条 ✓）----
        //  · 选区：`Canvas.clipPath` / `clipRect`（**非抗锯齿** ✓ = 逐像素 0/1，与老掩码同一套 ✓），
        //    见 [selectionClipGeometry] ✓；
        //  · 方笔尖：`drawRect` ✓ —— 口径照网页 `stamp()` 那一套（同一个 r / ratio / 角度 / alpha ✓），
        //    只把图元从 `ellipse` 换成 `rect` ✓（网页 canvas2d 在 Windows 上就是 Skia ✓
        //    ⇒ 同一个光栅器 + 同一套抗锯齿 ✓）。
        //  ⚠️ 从此**唯一**还走老路的 dab 形状就是 `SOFT_ROUND` ✓（见上 ✓）。
        //
        // **采样源口径**（㉜b 的关键一条 ✓）：混色要采"这一笔现在看起来的样子"，
        // 网页简单版 `strokeState.sampleCtx = null` ⇒ 采的就是**那张画布自己** ✓
        //（`brush-lab-simple.html:782` + `:436` ✓）。这边**等价物 = [pixels]** ✓：
        //   · 表面=真源、`pixels` 是它的**逐字节镜像**（每颗笔尖之后 [readBackFromSurface]
        //     只回读这一档笔尖那一块 ✓）⇒ 采样窗口读到的那块与表面**同一份** ✓；
        //   · 为什么不去表面上直接读：表面是 `N32Premul` ⇒ 读回来要先除 alpha，
        //     这里**多一次舍入**；而页缓冲已经是"非预乘 ARGB"、与网页 `getImageData` 同口径 ✓
        //     （`SkiaDabRaster.readRegionInto` 的口径 ✓）⇒ 采样用 `pixels` **少一次往返误差** ✓；
        //   · 落笔之前那一次 [flushPendingUploads] 保证"表面与 `pixels` 在开笔那一刻逐字节一致" ✓
        //     （否则上一笔 `keepOpacity` 的合成结果还在页缓冲里、表面是旧的 ⇒ 采样与画面不同源 ✗）。
        strokeOnSurface = strokeSpec.shape != BrushShape.SOFT_ROUND
        if (strokeOnSurface) selectionClipGeometry()
        strokeOnLayerSurface = strokeOnSurface
        // ---- 第 ㉟① 批：**笔画级覆盖度累积**（用户：「笔画不应是一个个个圆组成的」✗）----
        // 开着 ⇒ 这一笔先累积进覆盖度缓冲（取 `max` ✓）、抬笔时一次性合成到表面 ✓
        //（根因与口径见 [BrushSpec.strokeCoverage] 的长说明 ✓）。
        // ⚠️ **默认关** ✓ ⇒ 老预设一个字节不走这条路 ✓、四条"与网页逐像素一致"的判据逐字不动 ✓。
        // ⚠️ 只跟"圆 / 方"（= `strokeOnSurface`）这条一起用 ✓：
        //    `SOFT_ROUND` 走的是手写解析羽化老路（`BrushEngine.stampPlan` ✓），
        //    它本来就没有"一颗颗圆"的周期性问题（羽化是连续的 ✓）⇒ 不掺这一条 ✓。
        strokeCoverageOn = strokeOnSurface &&
            strokeSpec.brush.strokeCoverage &&
            // ⚠️ **B 方案（2026-09-21）**：这一条一开 ⇒ **根本不攒** ✗ ⇒ 退到下面 `stampOnSurface`
            //    那条老路 ✓（每颗当场落表面 ✓、落笔即见墨 ✓、零滞后 ✓）。
            //    代价如实：逐颗 source-over ⇒ "一个个圆"会回来 ✗（见 `directSurfaceStroke` 的说明 ✓）。
            !BrushSpec.directSurfaceStroke &&
            strokeSpec.mode != StrokeMode.BLUR &&
            strokeSpec.mode != StrokeMode.CLONE
        if (strokeCoverageOn) {
            ensureLayerSurface()
            SkiaDabRaster.beginCoverage(width, height)
        }
        // `keepOpacity` 的"整笔一个浓度"要在收笔时补一次合成 ✓（见那个字段的说明 ✓）——
        // 橡皮不算 ✓（擦除走 `DST_OUT`，`dabAlpha` 那边也照旧给真实 alpha ✓）。
        strokeKeepsOpacity = strokeSpec.brush.keepOpacity && strokeSpec.mode != StrokeMode.ERASE
        strokeDirtyBlocks.clear()
        // 走表面之前先把"页缓冲改过、还没传上去"的那一块传掉 ✓（不然笔画会盖在旧内容上 ✗，
        // 混色笔还要**采**这一份 ⇒ 传晚一步就是"采到的画面"与"画上去的画面"不同源 ✗）。
        if (strokeOnSurface) {
            ensureLayerSurface()
            flushPendingUploads()
        }
        // 走位基准：第一颗笔尖就落在落笔点 ✓ ⇒ 步距由**它自己**的半径算 ✓、carry 从 0 起 ✓
        dabCarry = 0f
        resetDabStats()
        // ---- 第 ㉚ 批 · 输入侧稳定器（EMA ✓）----
        // 网页 `brush-lab-simple.html:729/:740`：落笔时把 `smooth` **种子化**成落笔点 ✓，
        // 而落笔那一颗笔尖用的**就是**那个 seed 点（还没被 EMA 动过 ✓）。
        // ⇒ 这里同样：种子化 + 这一颗照旧落在 (x,y) ✓，**一个字都不会变** ✓。
        if (spec.stabilizePercent > 0f) {
            strokeStabilizer.stabilizePercent = spec.stabilizePercent
            strokeStabilizer.seed(x, y)
        } else {
            strokeStabilizer.reset()
        }
        strokeInputPoints = 0
        strokeStartedAtNanos = System.nanoTime()
        strokeElapsedNanos = 0L
        val firstRadius = nibRadiusAt(lastPressure)
        dabStepPixels = dabStepAt(lastPressure, strokeDirection, firstRadius)
        // ⚠️ 模糊 / 图章**不吃逐点压感**、也不走"沿路径盖笔尖" ✓（见 [strokeTo] 的恒压分支 ✓）——
        //    它们在落笔这一刻**不盖笔尖** ✗：[BrushEngine.planDab] 是不看模式的 ⇒ 盖下去会先落一颗
        //    **画笔色**的实心笔尖（用户会看到"模糊笔一按下去先点了一个黑点" ✗）。
        //    照 [stampSegment] 的老口径补一段**零长度**的 ✓（= 只对落笔点那一小片生效 ✓）。
        val firstStamped = if (strokeSpec.mode == StrokeMode.BLUR || strokeSpec.mode == StrokeMode.CLONE) {
            stampSegment(x, y, x, y, strokeSegmentRadius, blurIntensityFor(lastPressure))
        } else {
            stampDabAt(x, y, lastPressure, strokeDirection, firstRadius)
        }
        // 落笔这颗笔尖也算一颗（日志里的 `dabs` / `worstStepOverR` 要含它 ✓）
        noteDab(firstRadius, dabStepPixels, firstStamped)
        return firstStamped
    }

    /**
     * 这一套参数对应的**纸纹 / 噪点贴图**（不需要就是 null ✓）。
     *
     * 按参数缓存 ✓ —— 128×128 × 3 个八度的 value noise 生成一次是几十微秒级，
     * 但"每一笔重建一次"在大笔刷高频落笔时就是白烧 ⚠️（而且**同一张纸不该每笔换一次** ✗）。
     * seed 固定（[BrushEngine.TILE_SEED] ✓）⇒ 同一套参数永远同一张贴图 ✓。
     */
    private fun textureFor(brush: BrushSpec): ByteArray? {
        if (!brush.needsTexture) return null
        val key = TileKey(brush.paperOn, brush.paperStrength, brush.spreadNoise, brush.spreadNoiseStrength)
        if (tileKey == key) return tileCache
        // ⚠️ 第 ㉜c 批：细颗粒改走 [BrushEngine.mulberryGrain] ✗（原来是 `Random(TILE_SEED)` ✓）——
        // 理由只有一个：**纸纹要跟网页逐字节对** ✓。网页那层细颗粒是 `Math.random()` 生的 ✓，
        // 抽真值时把它临时换成同一个 mulberry32（`tools/webview-spike` 的 `paper` 模式 ✓），
        // 于是两边那张 128×128 贴图**逐字节相同** ✓（`PaperBrushParityTest` 里钉着 ✓）。
        val tile = BrushEngine.buildTileFromGrain(brush, BrushEngine.mulberryGrain(BrushEngine.TILE_SEED.toInt()))
        tileKey = key
        tileCache = tile
        return tile
    }

    /**
     * 画到某个点（和上一点连成一段）。没有正在画的一笔时是空操作。
     *
     * ## 逐点压感（用户 2026-09-20：「**逐点压感和旋转做**」✓）
     *
     * 画笔 / 橡皮走**沿路径盖笔尖**（dab stamping ✓，内核照 `docs/brush-lab.html` 的
     * `stamp()` + `walkTo()` ✓）：沿这一段走位，**每个笔尖用它自己那一点的压力**现算半径 ✓
     *（这一段之内的压力从 [lastPressure] 线性插到 [pressure] ✓）⇒ 一笔之内会自然变粗变细 ✓。
     *
     * ⚠️ **步距也按"这一颗笔尖自己的半径"算** ✓（口径与护栏都在 [ImageEditOps.dabStepPixels] ✓：
     * `spacing%` 相对**笔尖直径**、再被 `0.4 × 行进方向上的半径` 夹住 ✓）——
     * 用户 2026-09-20 实机报的「**画出来是一排点**」就是"步距用基础半径、笔尖用压感后的半径" ✗：
     * 轻按时笔尖只有基础半径的 25%，步距却按基础半径走 ⇒ 步距 > 笔尖直径 ⇒ 必然有空隙 ✓。
     *
     * 模糊 / 图章走**恒压分支**（照旧整段一次 ✓，见 [strokeSegmentRadius] ✓）。
     *
     * @param pressure 这个点的**笔压** 0..1 ✓（默认 1 = 没有压感 = 老行为 ✓）。
     * @return 这一段**真的盖了至少一个笔尖**才 true ✓ —— 界面据此才 `comicPaintVersion++`
     *   （口径：**只在真的盖了笔尖时才重建显示位图** ✓，别走两步就重建一次 ✗）。
     */
    fun strokeTo(x: Float, y: Float, pressure: Float = 1f): Boolean {
        if (!isDrawing) return false
        val spec = strokeSpec
        val target = if (pressure.isFinite()) pressure.coerceIn(0f, 1f) else 1f
        // 输入侧计数（诊断 / 实测输入率 ✓，不参与画面 ✓）—— 数的是**喂进来的原始采样点** ✓
        strokeInputPoints++
        // ---- 第 ㉚ 批 · 输入侧稳定器（EMA ✓）：照网页 `:747-752` 先平滑**这个采样点**，
        //      再把平滑后的点交给下面那条走位链 ✓（`walkTo` 那一侧一个字都不动 ✓）----
        //
        // ⚠️ 位置很重要 ✗：网页里 EMA 在 `pointermove` 里、在 `walkTo()` **之前** ✓；
        //    所以这里也在**入口**平滑 ✓ —— 放进走位里就是另一个东西了 ✗。
        // ⚠️ 方向 / 距离 / carry 全部按**平滑之后**的点算 ✓（与网页一致：`liveDab` 收到的就是
        //    平滑点 ✓，`walkTo` 里的 `st.last` 自然也是平滑点 ✓）。
        val sx: Float
        val sy: Float
        if (spec.stabilizePercent > 0f) {
            strokeStabilizer.next(x, y)
            sx = strokeStabilizer.smoothX
            sy = strokeStabilizer.smoothY
        } else {
            sx = x
            sy = y
        }

        if (spec.mode == StrokeMode.BLUR || spec.mode == StrokeMode.CLONE) {
            val drawn = stampSegment(lastX, lastY, sx, sy, strokeSegmentRadius, blurIntensityFor(target))
            lastX = sx
            lastY = sy
            lastPressure = target
            return drawn
        }

        val dx = sx - lastX
        val dy = sy - lastY
        val distance = kotlin.math.hypot(dx, dy)
        // 走不到半个像素就别盖（照 lab 的 `walkTo`：`if (dist < 0.05) return;` ✓）——
        // ⚠️ 这里**故意不更新 lastX/lastY/lastPressure** ✓：这一小步会并进下一段算 ✓（压力斜坡不丢 ✓）。
        if (!distance.isFinite() || distance < 0.05f) return false

        val direction = kotlin.math.atan2(dy, dx)
        // 路径总长（日志里的 `dist=` ✓）—— 只算真的走起来的那几段 ✓（<0.05px 的抖动手抖不算 ✓）
        dabDistance += distance
        // 走起来一段（= 收到一个真的移动了的输入点 ✓）—— 日志里的 `segments=` ✓
        dabSegments++
        // ---- 走位（**每一颗笔尖的步距都由它自己那一点的半径算** ✓）----
        //
        // 口径（用户 2026-09-20 实机报的「**画出来是一排点**」就是这里搞错 ✗）：
        //  · `spacing%` 是相对**这一颗笔尖的直径**（压感之后的 ✓），公式与护栏都在
        //    [ImageEditOps.dabStepPixels] ✓；
        //  · `step` 与 `dabCarry` **同口径**：carry 记的是"上一颗笔尖之后走了多远"，
        //    而上一颗的步距基准（[dabStepPixels]）就是"还差多远才算下一颗" ✓ ——
        //    混用基础半径的话，跨段时这个差值就失真了 ✗。
        var step = dabStepPixels
        var sinceDab = dabCarry
        var next = maxOf(ImageEditOps.DAB_MIN_STEP_PIXELS, step - sinceDab)
        var lastStampAt = -1f
        var stamped = false
        while (next <= distance) {
            val k = next / distance
            val px = lastX + dx * k
            val py = lastY + dy * k
            // ⚠️ 第 ㉘ 批：**段内不再插值压力** ✗ —— 照网页口径（`brush-lab-simple.html:798-802`：
            //    一段里所有笔尖用的都是**当前事件那个压力** ✓）。实测差别：同一串 21 个输入点，
            //    插值那版落 943 颗、网页 928 颗（多 15 颗 = 段间 carry 被插值改过的半径带偏 ✗）；
            //    改成"一段一个压力"之后与网页一致 ✓（判据①的容差已从 ±20 收到 ±2 ✓）。
            //    代价说清楚：输入点稀疏时**压力分辨率**不如网页 —— 网页靠 `getCoalescedEvents()`
            //    一帧拿几十个采样点来补 ✓（见 `docs/50` 的未验清单：软件这边**没有**这条 ✗）。
            val p = if (spec.interpolateSegmentPressure) {
                lastPressure + (target - lastPressure) * k
            } else {
                target
            }
            // 半径只算**一次** ✓：走路（步距）与盖章（笔尖）必须用**同一个数** ✓ ——
            // 两处各算一套就是这个 bug 的老家 ✗（见 [nibRadiusAt] ✓）。
            val radius = nibRadiusAt(p)
            val hit = stampDabAt(px, py, p, direction, radius)
            if (hit) stamped = true
            lastStampAt = next
            // ★ 这一颗笔尖的真实半径 ⇒ **它就是"下一颗还差多远"的基准** ✓（与 carry 配套 ✓）
            step = dabStepAt(p, direction, radius)
            noteDab(radius, step, hit)
            next += step
        }
        dabCarry = if (lastStampAt < 0f) sinceDab + distance else distance - lastStampAt
        dabStepPixels = step
        // 兜底：按上面的推导 `carry < step` 永远成立 ✓（真落笔时 `next + step > distance` ✓、
        // 没落笔时 `sinceDab + distance < step` ✓）—— 留着只是防"以后改坏了" ✗
        if (dabCarry >= step) dabCarry %= step

        strokeDirection = direction
        lastX = sx
        lastY = sy
        lastPressure = target
        return stamped
    }

    /**
     * 收笔：把这一笔提交成历史里的一步。
     *
     * ⚠️ **第 ㉜a 批起没有"合层"这回事** ✓：这一笔的墨迹从头到尾就画在层表面上
     *（[SkiaDabRaster.stampOnSurface] ✓），[pixels] 在每颗笔尖之后就被回读同步过 ✓ ——
     * 所以收笔只做一件事：把**碰过的那几块**（256 对齐 ✓）按"落笔前 / 现在"记成历史 ✓。
     *
     * `keepOpacity` 的"整笔一个浓度"由引擎那一侧照旧给 alpha（见 [BrushEngine.strokeCompositeAlpha] ✓）。
     */
    fun endStroke() {
        if (!isDrawing) return
        isDrawing = false
        // 输入率实测的收口（只记数 ✓，不参与画面 ✓）—— 收笔这一刻停表 ✓
        strokeElapsedNanos = System.nanoTime() - strokeStartedAtNanos
        strokeStabilizer.reset()
        // ---- 第 ㉟① 批：抬笔那一刻**一次性合成**覆盖度 ✓（这就是"轮廓天生连续"的落点 ✓）----
        // ⚠️ 必须在 [commitStrokeDirtyBlocks] **之前** ✓：那边是"把 `pixels` 按块记成历史"✓，
        //    而覆盖度合成之前 `pixels` 里**还没有这一笔** ✗（这一笔一直只待在覆盖度缓冲里 ✓）
        //    ⇒ 顺序反了就会记成"一笔什么都没画" ✗（撤销时看不出问题、重做时才露馅 ✗）。
        compositeStrokeCoverage()
        commitStrokeDirtyBlocks()
        history.endGroup()
    }

    /**
     * **把这一笔的覆盖度缓冲一次性合成到层表面**（第 ㉟① 批 ✓ —— 见 [strokeCoverageOn] ✓）。
     *
     * 三件事必须一起做（少一件画面就是错的 ✗）：
     *  ① [SkiaDabRaster.compositeCoverage] 把覆盖度按 `source-over`（橡皮 `DST_OUT` ✓）
     *     合成到层表面 ✓ —— **整笔一次** ✗ 不是一颗一次 ✓（周期性起伏就此消失 ✓，见 [BrushSpec.strokeCoverage] ✓）；
     *  ② **回读**这一块进 `pixels` ✓ —— 撤销 / 存盘 / PSD / 混色采样读的都是它 ✓
     *     （与老路每颗之后回读同一个口径 ✓，只是这里一笔一次 ✓）；
     *  ③ 推进 `canvasEpoch` ✓（画布变了 ⇒ 混色的采样缓存不许复用 ✓，与每颗之后那一下同口径 ✓）。
     *
     * 没走覆盖度那条路时**一个字节都不做** ✓（`strokeCoverageOn == false` 直接返回 ✓）——
     * 这就是"老预设逐字不动"的保证 ✓。
     */
    private fun compositeStrokeCoverage() {
        if (!strokeCoverageOn) return
        strokeCoverageOn = false
        val bounds = strokeCoverageBounds
        strokeCoverageBounds = null
        SkiaDabRaster.endCoverage()
        strokeUsedCoverageAccumulation = true
        if (bounds == null || bounds.isEmpty) return
        val surf = layerSurface ?: ensureLayerSurface() ?: return
        val safe = bounds.clampTo(width, height)
        if (safe.isEmpty) return
        // ⚠️ 合成之前先把"`pixels` 改过、还没传上去"的那一块传掉 ✓ ——
        //    否则这一步会盖在**旧的**表面内容上 ✗（`keepOpacity` / 选区那些会改 `pixels` ✓）。
        flushPendingUploads()
        val ok = SkiaDabRaster.compositeCoverage(
            surface = surf,
            colorArgb = strokeSpec.color,
            erasing = strokeSpec.mode == StrokeMode.ERASE,
            rect = safe,
            clipRect = clipRectSkia,
            clipPath = clipPathSkia,
        )
        if (!ok) return
        // ② 回读（与老路"每颗之后只回读那一块"同一个口径 ✓，这里是一笔一次 ✓）
        readBackFromSurface(safe)
        canvasEpoch++
        surfaceComposites++
        hasChanges = true
        notePixelsChanged()
    }

    /** 诊断：覆盖度**一次性合成**发生了几次 ✓（0 ⇒ 没走那条路 ✓）。 */
    var surfaceComposites: Int = 0
        private set

    /**
     * **A 方案（2026-09-21）：笔画进行中，把已经攒下的覆盖度合成到表面上** ✓。
     *
     * ## 治的是什么（用户实机报的原话 ✓）
     *
     * > 「**绘画时是先画出一排点，然后才补成完整的线**」
     *
     * **根因**（不是参数问题 ✓，是结构问题 ✗）：走了 `strokeCoverageOn` 那条路之后，
     * [strokeTo] **只往覆盖度缓冲里累积**、**一个字节都不碰层表面** ✗ ⇒
     * 笔画期间/抬笔前**屏幕上什么都没有**（或只有上一次合成的残影）✗，
     * 要到 [endStroke] → [compositeStrokeCoverage] 那一下才一次性出现整条线 ✓。
     *
     * ## 为什么这样改是安全的（**语义一个字没动** ✓）
     *
     * `compositeCoverage` 合成的是**覆盖度缓冲的当前全部内容** ✓，而覆盖度的累积是**单调**的 ✓：
     *  · 形状那本取 `max` ✓（只会越来越大 ✓）；
     *  · 墨量那本累加饱和 ✓（只会越来越浓 ✓）。
     * ⇒ **中途多合成几次，不影响最终像素** ✓ —— 每次都只是"把目前攒的这张网盖上表面"✓，
     * 抬笔那一次照跑补齐 ✓。
     *
     * ⚠️ **与 `docs/66`（㊲）那条不冲突** ✓：那条治的是"**每个输入点**都合成"⇒ 太贵 ✗；
     * 这一条是"**每帧**合成一次"✓（与显示通知同频 ✓）—— 两者都是"按帧攒"的同一个纪律 ✓。
     *
     * ⚠️ **次序不能反** ✗：调用方必须"先把这一帧的点喂完（`strokeTo` ✓）、再来合"✓ ——
     * 先合的话这一帧的新墨要等下一帧才上表面 ⇒ 延迟翻倍 ✗。
     *
     * @return true = 真的合了一次（有新的墨上表面 ✓）
     */
    fun compositeCoverageFrameTick(): Boolean {
        if (!isDrawing || !strokeCoverageOn) return false
        val bounds = strokeCoverageBounds ?: return false
        if (bounds.isEmpty) return false
        // ⚠️ 这里**不能**像 `compositeStrokeCoverage()` 那样把 `strokeCoverageOn` 置 false ✗ ——
        //    那会把这笔剩下的点全部退回"不累积"那条路 ✗（笔画后半段会突然变成老路的"一颗颗圆"✗）。
        //    `compositeCoverage` 自己**不清**覆盖度缓冲 ✓（`endCoverage()` 才清 ✓）⇒ 直接复用 ✓。
        val surf = layerSurface ?: ensureLayerSurface() ?: return false
        val safe = bounds.clampTo(width, height)
        if (safe.isEmpty) return false
        flushPendingUploads()
        val ok = SkiaDabRaster.compositeCoverage(
            surface = surf,
            colorArgb = strokeSpec.color,
            erasing = strokeSpec.mode == StrokeMode.ERASE,
            rect = safe,
            clipRect = clipRectSkia,
            clipPath = clipPathSkia,
        )
        if (!ok) return false
        // 回读口径与抬笔那次完全一样 ✓（撤销 / 存盘 / 混色采样读的都是 `pixels` ✓）；
        // ⚠️ 第 ㊳ 批：按帧合时只回读"**这一帧真的写了**"那一块 ✓（整笔并集会越滚越大 ✗ ⇒ 白烧 ✗）。
        val readRect = SkiaDabRaster.coverageDeltaBounds?.clampTo(width, height) ?: safe
        readBackFromSurface(if (readRect.isEmpty) safe else readRect)
        canvasEpoch++
        surfaceComposites++
        hasChanges = true
        notePixelsChanged()
        return true
    }

    /**
     * **这一笔的输入侧统计**（第 ㉚ 批 ✓）—— 用来**实测**"软件这边一个事件一个点"到底缺不缺采样 ✓：
     *  · [points]：喂进来的**原始采样点**数（= `strokeTo` 被调了几次 ✓）；
     *  · [movedSegments]：其中真的走起来（≥0.05px ✓）的段数；
     *  · [elapsedMs]：这一笔从落笔到抬笔的挂钟时间 ✓；
     *  · [pointsPerSecond]：`points / elapsedMs * 1000` ✓ —— **这就是要跟网页比的数** ✓
     *    （网页一帧最多能吃到几十个 `getCoalescedEvents()` 采样点 ✓）。
     *
     * ⚠️ 只用于日志与探针 ✓，**不参与画面、不进断言** ✗（挂钟时间在测试里不确定 ✓）。
     */
    fun strokeInputStats(): StrokeInputStats = StrokeInputStats(
        points = strokeInputPoints,
        movedSegments = dabSegments,
        elapsedMs = strokeElapsedNanos / 1_000_000.0,
        pointsPerSecond = if (strokeElapsedNanos > 0L) {
            strokeInputPoints * 1_000_000_000.0 / strokeElapsedNanos
        } else {
            0.0
        },
    )

    /**
     * **把活笔画缓冲合进层像素** —— ⚠️ 第 ㉜a 批**整条删掉** ✗（连同 [LiveStrokeLayer] 的显示用途 ✓）：
     * 笔直接画在层表面上，抬笔没有合成这一步 ✓（见 [commitStrokeDirtyBlocks] ✓）。
     */

    /** 撤销一步（写回 before）。 */
    fun undo(): Boolean {
        endStroke()
        val deltas = history.undo() ?: return false
        for (delta in deltas) {
            ImageEditOps.writeRect(pixels, width, height, delta.rect, delta.before)
            markPixelsDirty(delta.rect)
        }
        notePixelsChanged()
        return true
    }

    /** 重做一步（写回 after）。 */
    fun redo(): Boolean {
        endStroke()
        val deltas = history.redo() ?: return false
        for (delta in deltas) {
            ImageEditOps.writeRect(pixels, width, height, delta.rect, delta.after)
            markPixelsDirty(delta.rect)
        }
        notePixelsChanged()
        return true
    }

    /**
     * 清空画布（刷成透明）。
     *
     * ⚠️ 这是**一步可撤销的操作**，不是"重置"：官方那颗垃圾桶就是清内容、还能撤销回去。
     * 想回到"进编辑器时的原图"是另一件事（界面上的「重置」），由 `AppState` 重新读盘。
     */
    fun clear(): Boolean {
        endStroke()
        if (pixels.all { it == ImageEditOps.TRANSPARENT }) return false
        val rect = EditRect(0, 0, width, height)
        val before = ImageEditOps.copyRect(pixels, width, height, rect)
        val changed = ImageEditOps.fillRect(pixels, width, height, rect, ImageEditOps.TRANSPARENT)
        if (changed.isEmpty) return false
        markPixelsDirty(changed)
        history.beginGroup()
        history.push(
            PixelDelta(
                rect = changed,
                before = before,
                after = ImageEditOps.copyRect(pixels, width, height, changed),
            ),
        )
        history.endGroup()
        hasChanges = true
        notePixelsChanged()
        return true
    }

    /** 某个像素的颜色（吸管工具）。 */
    fun colorAt(x: Int, y: Int): Int = ImageEditOps.colorAt(pixels, width, height, x, y)

    // -----------------------------------------------------------------------
    // 油漆桶（一步，可撤销）
    // -----------------------------------------------------------------------

    /** 油漆桶灌一次；没改动返回 false。 */
    fun fillAt(x: Int, y: Int, color: Int, tolerance: Int): Boolean {
        endStroke()
        val rect = EditRect(0, 0, width, height)
        val before = ImageEditOps.copyRect(pixels, width, height, rect)
        val changed = ImageEditOps.floodFill(
            pixels, width, height, x, y, color, tolerance, clip = selectionClipMask(),
        ) ?: return false
        markPixelsDirty(changed)
        history.beginGroup()
        history.push(
            PixelDelta(
                rect = changed,
                before = ImageEditOps.copyRect(before, width, height, changed),
                after = ImageEditOps.copyRect(pixels, width, height, changed),
            ),
        )
        history.endGroup()
        hasChanges = true
        notePixelsChanged()
        return true
    }

    // -----------------------------------------------------------------------
    // HSV 调整：拖动时实时预览、Apply 才算一步
    // -----------------------------------------------------------------------

    private var hsvSource: IntArray? = null

    /**
     * 实时预览：每次都从**打开对话框那一刻的快照**重算（不是在上一次结果上再调）。
     *
     * 为什么不就地改：滑杆来回拖时，就地改会让"色相 +10 再 -10"回不到原样（浮点累积），
     * 而且"取消"就没法还原了。留一份快照最省事也最准。
     */
    fun previewHsv(hueShift: Float, saturation: Float, value: Float): Boolean {
        endStroke()
        val source = hsvSource ?: pixels.copyOf().also { hsvSource = it }
        ImageEditOps.hsvAdjust(source, pixels, pixels.size, hueShift, saturation, value)
        markPixelsDirty(EditRect(0, 0, width, height))
        notePixelsChanged()
        return true
    }

    /** 把当前所见提交成**一步**（可撤销）；预览期间没算作历史。 */
    fun commitHsv() {
        endStroke()
        val source = hsvSource ?: return
        hsvSource = null
        if (source.contentEquals(pixels)) return
        val rect = EditRect(0, 0, width, height)
        history.beginGroup()
        history.push(PixelDelta(rect = rect, before = source, after = pixels.copyOf()))
        history.endGroup()
        hasChanges = true
        notePixelsChanged()
    }

    /** 取消预览：把像素还原成打开对话框那一刻的样子。 */
    fun cancelHsv() {
        val source = hsvSource ?: return
        hsvSource = null
        if (source.size == pixels.size) {
            System.arraycopy(source, 0, pixels, 0, pixels.size)
            markPixelsDirty(EditRect(0, 0, width, height))
            notePixelsChanged()
        }
    }

    /** 有没有正在预览的 HSV 调整（对话框开着的时候是 true）。 */
    val hsvPreviewActive: Boolean get() = hsvSource != null

    // -----------------------------------------------------------------------
    // 调整画布大小
    // -----------------------------------------------------------------------

    /**
     * 换画布尺寸：内容按 ([left],[top]) 放到新画布上。
     *
     * ⚠️ **历史一并清掉**：像素坐标全变了，旧的脏矩形在新画布上没有意义
     * （留着撤销会把画面啃掉一块）。所以这一步本身也不可撤销 —— 和官方一样，
     * 尺寸是"画布级"的操作。想反悔就点顶栏的「重置」（回到进编辑器时那张图）。
     */
    fun resizeCanvas(newWidth: Int, newHeight: Int, left: Int, top: Int) {
        require(newWidth > 0 && newHeight > 0) { "新画布尺寸必须为正：${newWidth}x$newHeight" }
        endStroke()
        hsvSource = null
        dropFloating(restore = false)
        val oldWidth = width
        val oldHeight = height
        pixels = ImageEditOps.resizeCanvas(pixels, width, height, newWidth, newHeight, left, top)
        width = newWidth
        height = newHeight
        // ⚠️ 尺寸变了 ⇒ 表面**必须重开**（旧的尺寸对不上 ✗）——建的时候会整页传一次 ✓
        dropLayerSurface()
        history.clear()
        notePixelsChanged()
        // 尺寸变了、或者内容被挪过（left/top 非 0）才算改过；原尺寸原位置走一遍等于没动
        if (newWidth != oldWidth || newHeight != oldHeight || left != 0 || top != 0) hasChanges = true
    }

    /** 换一张底图（「重置」会用）：像素整体替换，历史一并清掉。 */
    fun replaceAll(newPixels: IntArray) {
        require(newPixels.size == pixels.size) { "替换的像素尺寸不一致" }
        endStroke()
        hsvSource = null
        dropFloating(restore = false)
        System.arraycopy(newPixels, 0, pixels, 0, pixels.size)
        // 「重置」换了一整张底图 ⇒ 表面重开（旧内容全作废 ✗）
        dropLayerSurface()
        history.clear()
        notePixelsChanged()
    }

    /** 丢掉层表面（尺寸变了 / 整张换掉时 ✓）—— 下次要用时惰性重建 + 整页上传 ✓。 */
    private fun dropLayerSurface() {
        layerSurface?.close()
        layerSurface = null
        pendingUpload = null
        strokeDirtyBlocks.clear()
    }

    // -----------------------------------------------------------------------
    // 浮动选区（三期）：抬起 → 移动/缩放/旋转 → 落回
    // -----------------------------------------------------------------------

    private var floating: FloatingSelection? = null

    /** 抬起那一刻的**整张快照**（取消要还原、提交要当历史的 before）。 */
    private var floatingBefore: IntArray? = null

    val floatingSelection: FloatingSelection? get() = floating

    val hasFloatingSelection: Boolean get() = floating != null

    /**
     * 把选区里的像素**抬起来**（原处清空），之后就能随手拖 / 缩 / 转。
     *
     * 已经有浮动选区时：动过的先落回（提交），没动过的直接丢掉 —— 免得用户以为
     * "换了个框"，结果上一块还飘着。
     */
    fun beginSelection(shape: SelectionShape): Boolean {
        endStroke()
        hsvSource = null
        val existing = floating
        if (existing != null) {
            if (existing.isIdentity()) cancelSelection() else commitSelection()
        }
        val snapshot = pixels.copyOf()
        val lifted = SelectionOps.lift(pixels, width, height, shape) ?: return false
        floating = lifted
        floatingBefore = snapshot
        markPixelsDirty(EditRect(0, 0, width, height))
        // 抬起 = 那块像素**已经从画布上清掉了**（不管之后落回哪儿），所以这就算改过。
        // 「只是框了一个选区、没拖」不会走到这儿（`AppState.liftCanvasSelection` 只在
        // 拖动框内/控制点时才调），所以"什么都没改"不会被误判成改过。
        hasChanges = true
        notePixelsChanged()
        return true
    }

    fun translateSelection(deltaX: Float, deltaY: Float) {
        floating?.translateBy(deltaX, deltaY)
    }

    fun scaleSelection(factorX: Float, factorY: Float, anchorX: Float, anchorY: Float) {
        floating?.scaleBy(factorX, factorY, anchorX, anchorY)
    }

    fun rotateSelection(deltaDegrees: Float) {
        floating?.rotateBy(deltaDegrees)
    }

    /** **落回**：把变换后的 patch 盖回画布，整次操作算**一步**历史（可撤销）。 */
    fun commitSelection(): Boolean {
        val current = floating ?: return false
        val before = floatingBefore
        floating = null
        floatingBefore = null
        if (before == null) return false
        val touched = SelectionOps.stamp(pixels, width, height, current)
        if (touched == null) {
            // 没有落得下的像素（比如整块被拖到画布外）：像素回到抬起前，别留半截状态
            System.arraycopy(before, 0, pixels, 0, pixels.size)
            markPixelsDirty(EditRect(0, 0, width, height))
            notePixelsChanged()
            return false
        }
        val rect = EditRect(0, 0, width, height)
        markPixelsDirty(rect)
        history.beginGroup()
        history.push(PixelDelta(rect = rect, before = before, after = pixels.copyOf()))
        history.endGroup()
        notePixelsChanged()
        return true
    }

    /** 「取消」：像素回到抬起前那一刻（选区形状由界面自己决定留不留）。 */
    fun cancelSelection(): Boolean {
        val before = floatingBefore ?: return false
        floating = null
        floatingBefore = null
        if (before.size == pixels.size) {
            System.arraycopy(before, 0, pixels, 0, pixels.size)
            markPixelsDirty(EditRect(0, 0, width, height))
            notePixelsChanged()
        }
        return true
    }

    private fun dropFloating(restore: Boolean) {
        val before = floatingBefore
        floating = null
        floatingBefore = null
        if (restore && before != null && before.size == pixels.size) {
            System.arraycopy(before, 0, pixels, 0, pixels.size)
            markPixelsDirty(EditRect(0, 0, width, height))
            notePixelsChanged()
        }
    }

    /**
     * **这一档压力下的笔尖基准半径**（原图像素 ✓，最小 0.5 px ✓）。
     *
     * 三步**照网页 `nibRadius()` 的顺序** ✓（顺序不能换 ✗）：
     *  1. `Scaling / 100`（SAI 的倍率 ✓）—— 作用在**基础半径**上 ✓；
     *  2. `AbsoluteSize == 1` ⇒ **直接用 `ShapeSize / 2`** ✓（这时候"画笔大小"滑杆不参与 ✓）；
     *  3. **压感**：`最小比例 + (1 − 最小比例) × pressure^曲线` ✓
     *     ——口径见 [ImageEditOps.brushRadiusPixels] ✓（默认 0.25 + 曲线 1.0 = 参数化之前那条老公式 ✓）。
     *
     * ⚠️ `SizeJitter` / 扩散噪点的半径抖动**不在这里** ✗ ——
     * 它们是"这一颗笔尖"的事（[BrushEngine.jitterNibRadius] ✓）。
     * 走位（[dabStepAt]）用的是**不带抖动**的这个数 ✓（照网页 `dabStep()` ✓：
     * 护栏要的是"这一笔有多粗"，不是"这一颗抖成多大" ✓）。
     *
     * [stampDabAt]（真的画）与走位（[dabStepAt] 算步距）**共用这一个函数** ✓ ——
     * 两处各算一套就是这个 bug 的老家（笔尖按压感后的半径、步距按基础半径 ⇒ 一排点 ✗）。
     */
    private fun nibRadiusAt(pressure: Float): Float {
        val spec = strokeSpec
        val brush = spec.brush
        if (brush.absoluteSize == 1) {
            return (brush.shapeSize * 0.5f).coerceAtLeast(0.5f)
        }
        val scaled = (strokeBaseRadius * (brush.scaling / 100f)).coerceAtLeast(BrushEngine.DAB_MIN_RADIUS)
        return (scaled * ImageEditOps.pressureRadiusScale(
            pressure,
            spec.minRadiusRatio,
            spec.pressureCurve,
        )).coerceAtLeast(0.5f)
    }

    /**
     * **这一颗笔尖该走多远才落下一颗**（像素 ✓）—— 口径全在 [ImageEditOps.dabStepPixels] ✓：
     * `spacing%` 相对**它自己的直径** ✓，再被 `0.4 × 行进方向上的半径` 夹住 ✓。
     *
     * @param pressure 这一颗笔尖那一点的**笔压** ✓（半径是现算的 ✓）
     * @param directionRadians 这一段的**行进方向** ✓（既是行进方向、也是 `angleControl == 自动` 的笔尖朝向 ✓）
     * @param radius 这一颗笔尖的半径 ✓（默认按 [pressure] 现算 ✓；调用方已经算过就**传进来** ✓，
     *   保证"走路用的半径"和"盖章用的半径"是**同一个数** ✓ —— 别再各算一套 ✗）
     */
    private fun dabStepAt(
        pressure: Float,
        directionRadians: Float,
        radius: Float = nibRadiusAt(pressure),
    ): Float {
        val brush = strokeSpec.brush
        val angle = BrushEngine.nibAngleRadians(brush, directionRadians)
        return ImageEditOps.dabStepPixels(
            radius = radius,
            spacingPercent = brush.spacing,
            nibRatio = brush.nibRatio,
            nibAngleRadians = angle,
            travelAngleRadians = directionRadians,
        )
    }

    /**
     * **模糊强度**（0..100 ✓）—— 吃 `blurPressureOn` / `blurPressure` ✓。
     *
     * `blurPressure = 0` ⇒ **强度恒定**（= 这一批之前的老行为 ✓）；
     * `= 100` ⇒ 强度完全跟笔压走 ✓；中间按 `(1 − k) + k × 压力` 插值 ✓（照 SAI 的「模糊笔压」✓）。
     */
    private fun blurIntensityFor(pressure: Float): Int {
        val spec = strokeSpec
        if (spec.mode != StrokeMode.BLUR) return spec.blurIntensity
        val brush = spec.brush
        if (!brush.blurPressureOn) return spec.blurIntensity
        val k = (brush.blurPressure / 100f).coerceIn(0f, 1f)
        val p = if (pressure.isFinite()) pressure.coerceIn(0f, 1f) else 1f
        return (spec.blurIntensity * ((1f - k) + k * p)).roundToInt().coerceIn(0, 100)
    }

    /**
     * **盖一个笔尖**（画笔 / 橡皮的逐点压感 + 网页那一整套抖动 / 散布 / 混色 / 纸纹 ✓）。
     *
     * 走的顺序照网页 `stamp()` ✓：
     *  ① 半径（[ImageEditOps.brushRadiusPixels] 之后再过 `SizeJitter` / 扩散噪声抖动 ✓）；
     *  ② 朝向（[BrushEngine.nibAngleRadians] + 角度抖动 ✓）；
     *  ③ 纵横比（[BrushSpec.wxRatio] + 抖动 ✓）；
     *  ④ alpha（浓度 / 最小浓度 / 水分量 / 扩散噪声 ✓）；
     *  ⑤ 混色采样（**采样源 = 当前正在编辑的那张像素缓冲** ✓ —— Kotlin 这边比网页还方便 ✓，
     *    没有 `getImageData` 那一步 ✓）；
     *  ⑥ 计数 + 散布 + 颜色抖动 ✓。前六步全在 [BrushEngine.planDab]（纯函数 ✓）里，
     *    这里只负责"取前后快照 + 落笔 + 记历史" ✓。
     *
     * ## 为什么要"先计划、后落笔"（和网页不一样的一处，但**必须** ✓）
     *
     * `count > 1` + `scattering > 0` 时，一档的几颗笔尖会**散开** ✓ ——
     * 只按单颗的 `dabBounds` 取快照，散出去的那几颗就没进历史 ✗
     *（撤销时它们留在画面上 = 画面被啃掉一块 ✗）。
     * 所以先算出全部笔尖（[BrushEngine.planDab] ✓）、取它们的**并集**（[BrushEngine.planBounds] ✓），
     * 再落笔 ✓。
     *
     * ## 落笔落到哪（**第 ㉜a 批改的地方** ✓）
     *
     * **直接画在层表面（[layerSurface]）上** ✓ —— 与网页 `stamp()` 逐字同口径：
     *  · 画笔 = `source-over` ✓、橡皮 = `destination-out`（Skia `DST_OUT` ✓）；
     *  · 画完**只回读这一档笔尖那一块**进 [pixels]（[SkiaDabRaster.readRegionInto] ✓）——
     *    于是"落盘 / 撤销差量 / PSD 的缓存"任何时刻都是对的 ✓，而**显示**那一半读的是表面
     *    （见 `AppModeScreen` 的视口那一路 ✓）⇒ **一笔之内不用重建任何整页位图** ✓。
     *
     * 不满足"上表面"那四个条件时（纸纹 / 混色 / 选区裁剪 / 方笔尖 ✓）**如实走老路** ✗：
     * 老路写的是 [pixels]（逐像素 coverage ✓），写完把那一块**传回表面** ✓（[SkiaDabRaster.writeRegionFrom]）。
     *
     * ## 为什么要"先计划、后落笔"（和网页不一样的一处，但**必须** ✓）
     *
     * `count > 1` + `scattering > 0` 时，一档的几颗笔尖会**散开** ✓ ——
     * 只按单颗的 `dabBounds` 取快照，散出去的那几颗就没进历史 ✗
     *（撤销时它们留在画面上 = 画面被啃掉一块 ✗）。
     * 所以先算出全部笔尖（[BrushEngine.planDab] ✓）、取它们的**并集**（[BrushEngine.planBounds] ✓），
     * 再落笔 ✓。
     *
     * @return 真的落下了（改到了像素）才 true ✓ ⇒ 界面据此才重绘视口那一帧 ✓。
     */
    private fun stampDabAt(
        x: Float,
        y: Float,
        pressure: Float,
        directionRadians: Float,
        radius: Float = nibRadiusAt(pressure),
    ): Boolean {
        val spec = strokeSpec
        val brush = spec.brush
        val erasing = spec.mode == StrokeMode.ERASE
        val plan = BrushEngine.planDab(
            brush = brush,
            state = brushState,
            sampleSource = pixels,
            sampleWidth = width,
            sampleHeight = height,
            x = x,
            y = y,
            baseRadius = radius,
            pressure = pressure,
            directionRadians = directionRadians,
            colorArgb = spec.color,
            erasing = erasing,
            pressureCurve = spec.pressureCurve,
            // 混色采样的**读**那一侧仍然是"这一笔现在看起来的样子"（= 页缓冲本身 ✓）——
            // ㉜a 之后笔直接画在表面上、每颗之后回读同步，所以 [pixels] 就是最新的 ✓（不再需要叠加层 ✗）。
            sampleOverlay = null,
            sampleOverlayWidth = 0,
            sampleOverlayHeight = 0,
            sampleOverlayX = 0,
            sampleOverlayY = 0,
            // 画布版本（第 ㉜b 批 ✓）：只有它没变过，才允许复用上一次的采样结果 ✓
            sampleEpoch = canvasEpoch,
        )
        val rect = BrushEngine.planBounds(plan, width, height) ?: return false
        val safe = rect.clampTo(width, height)
        if (safe.isEmpty) return false
        // 诊断（第 ㉜b 批 ✓）：判据要逐颗笔尖的采样色 / 半径 / 步距 ⇒ 开着才记 ✓
        if (dabProbeEnabled) {
            dabProbeLog += DabProbe(
                index = dabProbeLog.size,
                x = x,
                y = y,
                radius = radius,
                pressure = pressure,
                directionRadians = directionRadians,
                stepPixels = dabStepAt(pressure, directionRadians, radius),
                plan = plan,
            )
        }
        // 落笔前先把"页缓冲改过、还没传上去"的那一块传掉 ✓（否则表面会画在旧内容上 ✗）
        touchBlocks(safe)
        // ---- 第 ㉟① 批：**笔画级覆盖度累积**那条路（开着时走这里 ✓，见 [strokeCoverageOn] ✓）----
        // ⚠️ 这一支**只累积覆盖度**（取 `max` ✓）、**不碰层表面** ✗ ——
        //    合成统一在抬笔那一下 `endStroke` → [compositeStrokeCoverage] ✓
        //    ⇒ 同一像素不会被反复 source-over ⇒ **不会有周期性起伏** ✓（这就是"轮廓不被拆"的机制 ✓）。
        if (strokeCoverageOn) {
            if (
                !SkiaDabRaster.accumulateCoverage(
                    plan = plan,
                    texture = strokeTexture,
                    square = strokeSpec.shape == BrushShape.SQUARE,
                )
            ) {
                return false
            }
            // 这一笔碰过的并集 ✓（抬笔只合成这一块 ✓）
            val prev = strokeCoverageBounds
            strokeCoverageBounds = if (prev == null) safe else prev.union(safe)
            hasChanges = true
            return true
        }
        if (strokeOnSurface) {
            val surf = layerSurface ?: ensureLayerSurface() ?: return false
            // 纸纹 / 扩散噪点（第 ㉜c 批 ✓）：**乘法作用在笔迹 alpha 上** ✓ ——
            // 详细口径与"为什么不照抄网页那三步"见 `SkiaDabRaster.stampTexturedOnSurface` ✓。
            // 方笔尖（㉝② ✓）/ 选区裁剪（㉝② ✓）：几何在 `selectionClipGeometry()` 里备好 ✓。
            if (
                !SkiaDabRaster.stampOnSurface(
                    surface = surf,
                    plan = plan,
                    erasing = erasing,
                    texture = strokeTexture,
                    square = strokeSpec.shape == BrushShape.SQUARE,
                    clipRect = clipRectSkia,
                    clipPath = clipPathSkia,
                )
            ) {
                return false
            }
            // ⚠️ 只回读**这一档笔尖那一块** ✓（一把 JNI ✓）—— 快路计数在 `SkiaStampCostTest` 钉着 ✓
            readBackFromSurface(safe)
            canvasEpoch++ // 画布变了 ⇒ 下一颗的采样**不许**复用旧结果 ✓（与网页每颗都重采同口径 ✓）
            hasChanges = true
            return true
        }
        // ---- 老路（纸纹 / 混色 / 选区裁剪 / 方笔尖 ✓）：逐像素写页缓冲，再整块传回表面 ✓ ----
        val touched = BrushEngine.stampPlan(
            target = pixels,
            width = width,
            height = height,
            plan = plan,
            shape = spec.shape,
            erasing = erasing,
            clip = selectionClipMask(),
            texture = if (erasing) null else strokeTexture,
        ) ?: return false
        val hit = touched.clampTo(width, height)
        if (hit.isEmpty) return false
        uploadRectToSurface(hit)
        canvasEpoch++ // 同上：画布变了 ⇒ 采样不许复用 ✓
        hasChanges = true
        notePixelsChanged()
        return true
    }

    /**
     * **整段一次**那条老路（模糊 / 图章 ✓ —— 见 [strokeSegmentRadius] 的说明 ✓）。
     *
     * 半径由调用方给（落笔那一刻定死的那一档 ✓），所以它**不吃逐点压感** ✓
     * （模糊那个"吃不吃压感"由 [blurIntensityFor] 单独管 ✓）。
     */
    private fun stampSegment(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radius: Float,
        intensityOverride: Int? = null,
    ): Boolean {
        // ⚠️ 脏矩形在这里算一次、前后快照都按**这同一个矩形**取：
        // 如果让各个 ops 自己再算一遍，两边的 floor/ceil 只要差一格，
        // 存下来的 before 就和实际被改的区域错位 —— 撤销时会把没改过的像素写回去（画面被啃掉一块）。
        val rect = EditRect.ofSegment(x0, y0, x1, y1, radius, width, height) ?: return false
        val spec = strokeSpec
        val before = ImageEditOps.copyRect(pixels, width, height, rect)
        val clip = selectionClipMask()
        val touched = when (spec.mode) {
            StrokeMode.PAINT, StrokeMode.ERASE -> ImageEditOps.stampSegment(
                pixels = pixels,
                width = width,
                height = height,
                x0 = x0,
                y0 = y0,
                x1 = x1,
                y1 = y1,
                radius = radius,
                shape = spec.shape,
                color = spec.color,
                erasing = spec.mode == StrokeMode.ERASE,
                clip = clip,
            )

            StrokeMode.BLUR -> ImageEditOps.blurStroke(
                pixels = pixels,
                width = width,
                height = height,
                x0 = x0,
                y0 = y0,
                x1 = x1,
                y1 = y1,
                radiusPixels = radius,
                shape = spec.shape,
                // 模糊半径跟着笔刷走（约 1/3 笔径）：笔大 = 糊得开，这是最直观的对应关系
                blurRadiusPixels = (radius / 3f).roundToInt().coerceAtLeast(1),
                // 强度：`blurPressureOn` + `blurPressure` 那一条（0 = 恒定 ✓，见 [blurIntensityFor] ✓）
                intensity = intensityOverride ?: spec.blurIntensity,
                clip = clip,
            )

            StrokeMode.CLONE -> ImageEditOps.cloneStroke(
                pixels = pixels,
                width = width,
                height = height,
                x0 = x0,
                y0 = y0,
                x1 = x1,
                y1 = y1,
                radiusPixels = radius,
                shape = spec.shape,
                offsetX = spec.cloneOffsetX,
                offsetY = spec.cloneOffsetY,
                clip = clip,
            )
        } ?: return false
        val after = ImageEditOps.copyRect(pixels, width, height, rect)
        markPixelsDirty(touched.clampTo(width, height))
        history.push(PixelDelta(rect = touched.clampTo(width, height), before = before, after = after))
        // 真的落了笔（画笔/橡皮/模糊/图章）→ 这次会话改过了
        hasChanges = true
        notePixelsChanged()
        return true
    }
}

/**
 * **一笔的 dab 走位统计**（`[PenDab]` 日志 + 单测的唯一数据源 ✓）。
 *
 * 为什么要有它：用户 2026-09-20 实机报「**还是一排点**」—— 这一句没法在代码里"看出来"✗，
 * 只能让用户画一笔、把**数字**带回来 ✓（见 [ImageEditSession.strokeDabStats] 的说明 ✓）。
 *
 * @param dabs 这一笔走过几颗笔尖（含落笔那一颗 ✓）
 * @param stamped 其中真的改到像素的颗数（= 显示位图会重建的那几次 ✓）
 * @param segments 这一笔走了几段（= `strokeTo` 真的走起来的次数 = 收到的有效输入点数 ✓）——
 *   `dabs / segments ≈ 1` 就是"每个输入事件只盖一颗" = **一排点** ✗；
 *   `≫ 1` 才算"走位真的在工作" ✓（见 `[PenDab]` 汇总日志里的 `segments=` ✓）
 * @param distance 路径总长（**原图像素** ✓）
 * @param radiusMin/radiusMax 每一颗笔尖**当场**算出来的半径（压感之后的 ✓）
 * @param stepMin/stepMax 每一颗笔尖那一刻的步距（[ImageEditOps.dabStepPixels] ✓）
 * @param worstStepOverRadius **最坏的 `step / (2 × radius)`** ✓ —— `≤ 0.5` 正常 ✓、
 *   `> 1` = 相邻笔尖完全不重叠 = **必断成一排点** ✗
 * @param spacingPercent 当次 `spacing%`（[StrokeSpec.spacing] ✓）
 * @param nibRatio 当次笔尖宽高比（1 = 圆 ✓）
 * @param angleControl 当次角度控制（[NibAngleControl] ✓）
 * @param baseRadius 这一笔的**基础半径**（= 笔刷直径 / 2 ✓，压力还没乘 ✓）
 * @param samples **真的调了几次混色采样**（第 ㉔ 批 ✓ —— 只在 `blending / water / colorStretch > 0`
 *   时才可能 > 0 ✓；结构性计数，用户点名的"每 100 颗笔尖采样几次"就是它 ✓）
 * @param sampleReuses 其中**复用上一颗结果**的次数（采样点离上一颗 ≤ `BrushEngine.SAMPLE_REUSE_PX` ✓）
 */
data class StrokeDabStats(
    val dabs: Int = 0,
    val stamped: Int = 0,
    val segments: Int = 0,
    val distance: Float = 0f,
    val radiusMin: Float = 0f,
    val radiusMax: Float = 0f,
    val stepMin: Float = 0f,
    val stepMax: Float = 0f,
    val worstStepOverRadius: Float = 0f,
    val spacingPercent: Float = ImageEditOps.DEFAULT_SPACING_PERCENT,
    val nibRatio: Float = 1f,
    val angleControl: Int = NibAngleControl.FIXED,
    val baseRadius: Float = 0f,
    val samples: Int = 0,
    val sampleReuses: Int = 0,
) {
    /** 这一笔真的落过笔尖吗 ✓（没落过就别打汇总日志 ✗）。 */
    val hasDabs: Boolean get() = dabs > 0
}

/**
 * **一笔的输入侧统计**（第 ㉚ 批 ✓）—— 见 [ImageEditSession.strokeInputStats] ✓。
 *
 * 存在的唯一理由：把"软件这边输入采样率到底够不够"变成**数字** ✓
 *（㉙ 的 `docs/51` 把它列为**第一号**"不像网页"的风险 ✗：网页一帧能吃到几十个
 * `getCoalescedEvents()` 采样点 ✓，软件一个指针事件一个点 ✗）。
 */
data class StrokeInputStats(
    /** 喂进来的原始采样点数（`strokeTo` 调用次数 ✓）。 */
    val points: Int = 0,
    /** 其中真的走起来（≥0.05px）的段数 ✓。 */
    val movedSegments: Int = 0,
    /** 落笔 → 抬笔的挂钟时间（ms ✓）。 */
    val elapsedMs: Double = 0.0,
    /** 折合每秒多少个采样点 ✓（`points / elapsedMs * 1000` ✓）。 */
    val pointsPerSecond: Double = 0.0,
)
