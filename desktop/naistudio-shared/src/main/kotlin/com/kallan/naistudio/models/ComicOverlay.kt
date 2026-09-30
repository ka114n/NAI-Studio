package com.kallan.naistudio.models

import org.json.JSONArray
import org.json.JSONObject

/**
 * 高级漫画的**气泡 + 页面文本**（`docs/43-方案-高级漫画模式.md` §9.1 / §9.2）。
 *
 * 口径（用户 2026-09-26）：
 *  · 「内置一个气泡图形库，**选定气泡样式后直接拖到相应位置**调大小」→ 气泡是**矢量 + 文本**，
 *    不是位图贴图 ✗（放大不糊、文字随时能改 ✓）；
 *  · 「加可以打文本的功能，**可以导入字体**，打字」→ 文本属性：字号 / 颜色 / 对齐 / 字体；
 *  · 「一页 = 多图层（气泡 / 文本各自一层）」→ [ComicBubble] / [ComicText] 与
 *    [ComicLayer] 里的 `BUBBLE` / `TEXT` 一一对应（**图层 id 就用气泡/文本自己的 id** ✓，
 *    省掉一套"谁对应谁"的映射表 —— 那种表迟早对不上 ✗）。
 *
 * ## 坐标与单位（和 [ComicPanel] 一个口径）
 *
 * 一律是**底板像素**（`ComicBoardPage.baseWidth/Height` 那套）：界面按窗口大小等比缩放显示，
 * 存下来的数字对着底板那张图的真实像素 —— 换窗口大小、换机器都不漂移 ✓。
 * [ComicBubble.fontSize] / [ComicText.fontSize] 同样是**底板像素**（跟着页面一起缩 ✓）。
 *
 * ## 为什么样式存的是字符串而不是枚举
 *
 * 气泡轮廓的实现（`ui/BubbleShapes.kt` 的 `BubbleStyle`）住在 `ui/` 里，而这里是**模型层**：
 * 让模型去 import UI 层的枚举，等于让"存盘格式"跟着界面代码走 ✗。所以这里只存**样式 id**
 * （正好是那几个枚举名：`ROUND` / `RECT` / `THOUGHT` / `SHOUT`），
 * 界面那边用 `bubbleStyleOf(id)` 解析，**认不出来的 id 一律回落圆泡** ✓（盘上被手改过也不能崩 ✗）。
 */
enum class ComicTextAlign { START, CENTER, END }

/** 气泡样式 id（与 `ui/BubbleStyle` 的枚举名一一对应；界面那边解析失败会回落 [ROUND]）。 */
object ComicBubbleStyles {
    const val ROUND = "ROUND"
    const val RECT = "RECT"
    const val THOUGHT = "THOUGHT"
    const val SHOUT = "SHOUT"

    val all = listOf(ROUND, RECT, THOUGHT, SHOUT)

    fun isKnown(id: String): Boolean = id in all
}

/** 气泡 / 文本共用的尺寸与字号常量（**全部按页面宽度取比例** —— 2480 宽的 A4 和 512 宽的自定义画布用同一串像素数字会出洋相 ✗）。 */
object ComicOverlayMetrics {
    /** 新建气泡的默认大小（占底板宽 / 高的比例）。 */
    const val BUBBLE_WIDTH_RATIO = 0.30f
    const val BUBBLE_HEIGHT_RATIO = 0.13f

    /** 新建文本框的默认大小。 */
    const val TEXT_WIDTH_RATIO = 0.42f
    const val TEXT_HEIGHT_RATIO = 0.10f

    /** 缩放时的最小边长（小于它就是误触，拽不成一条线 ✗）。 */
    const val MIN_SIDE_RATIO = 0.04f

    /** 默认字号 / 可调范围（占底板宽的比例）。 */
    const val FONT_RATIO = 0.026f
    const val MIN_FONT_RATIO = 0.006f
    const val MAX_FONT_RATIO = 0.16f

    fun minSide(pageWidth: Int): Float = pageWidth * MIN_SIDE_RATIO

    fun defaultBubbleWidth(pageWidth: Int): Float = pageWidth * BUBBLE_WIDTH_RATIO
    fun defaultBubbleHeight(pageHeight: Int): Float = pageHeight * BUBBLE_HEIGHT_RATIO
    fun defaultTextWidth(pageWidth: Int): Float = pageWidth * TEXT_WIDTH_RATIO
    fun defaultTextHeight(pageHeight: Int): Float = pageHeight * TEXT_HEIGHT_RATIO

    fun defaultFontSize(pageWidth: Int): Float = pageWidth * FONT_RATIO
    fun minFontSize(pageWidth: Int): Float = pageWidth * MIN_FONT_RATIO
    fun maxFontSize(pageWidth: Int): Float = pageWidth * MAX_FONT_RATIO
}

/** 默认字色（漫画的正文就是黑的 ✓）。 */
const val COMIC_DEFAULT_TEXT_COLOR: Int = 0xFF000000.toInt()

/**
 * **一个对话气泡**：样式 + 位置尺寸 + 尾巴尖 + 里面的那段文字。
 *
 * @param id 稳定 id（同时就是它在图层叠里的那一层的 id ✓）
 * @param style 样式 id（见 [ComicBubbleStyles]）
 * @param x 左上角 X（底板像素）
 * @param y 左上角 Y
 * @param w 宽（> 0）
 * @param h 高（> 0）
 * @param tailX 尾巴尖 X；`null` = 还没拉过尾巴（画的时候按"没有尾巴"处理 ✓）
 * @param tailY 尾巴尖 Y
 * @param text 泡里的文字（用户双击气泡打的那段 ✓）
 */
data class ComicBubble(
    val id: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val style: String = ComicBubbleStyles.ROUND,
    val tailX: Float? = null,
    val tailY: Float? = null,
    val text: String = "",
    val fontSize: Float = 64f,
    val colorArgb: Int = COMIC_DEFAULT_TEXT_COLOR,
    val align: ComicTextAlign = ComicTextAlign.CENTER,
    /** 用户导入的那个字体（[com.kallan.naistudio.services.FontLibrary] 的落盘名）；`null` = 系统默认字体 ✓ */
    val fontFile: String? = null,
    /**
     * **描边**（漫画字常见：黑边 + 亮色字芯，压在画面上也读得清 ✓）。
     *
     * 渲染是"画两遍"：先用 [androidx.compose.ui.graphics.drawscope.Stroke] 描一遍黑边，
     * 再把字芯盖上去 ✓（Compose 的 `drawStyle` 只给"描边或填充"二选一，不给"两者都画"✗）。
     */
    val stroke: Boolean = false,
) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h

    /** 尾巴尖（两个坐标都在才算数 —— 只有一个的话是坏数据 ✓）。 */
    val tail: Pair<Float, Float>?
        get() = if (tailX != null && tailY != null) tailX to tailY else null

    /** 拖动：位置走，**尾巴跟着一起走**（尾巴是"指向某人"，泡挪了尾巴也得挪 ✓）。 */
    fun movedBy(dx: Float, dy: Float, pageWidth: Int, pageHeight: Int): ComicBubble {
        val maxX = (pageWidth - w).coerceAtLeast(0f)
        val maxY = (pageHeight - h).coerceAtLeast(0f)
        val nextX = (x + dx).coerceIn(0f, maxX)
        val nextY = (y + dy).coerceIn(0f, maxY)
        val movedX = nextX - x
        val movedY = nextY - y
        return copy(
            x = nextX,
            y = nextY,
            tailX = tailX?.let { (it + movedX).coerceIn(0f, pageWidth.toFloat()) },
            tailY = tailY?.let { (it + movedY).coerceIn(0f, pageHeight.toFloat()) },
        )
    }

    /** 拽四角缩放（对角固定）；**尾巴尖不动** —— 它指的是说话的人，不跟着框走 ✓。 */
    fun resizedBy(
        corner: ComicPanelCorner,
        dx: Float,
        dy: Float,
        pageWidth: Int,
        pageHeight: Int,
        minSide: Float = 0f,
    ): ComicBubble {
        val limit = if (minSide > 0f) minSide else ComicOverlayMetrics.minSide(pageWidth)
        val minW = limit.coerceAtMost(pageWidth.toFloat())
        val minH = limit.coerceAtMost(pageHeight.toFloat())
        val left = x
        val top = y
        val right = x + w
        val bottom = y + h

        return when (corner) {
            ComicPanelCorner.TOP_LEFT -> {
                val newLeft = (left + dx).coerceIn(0f, right - minW)
                val newTop = (top + dy).coerceIn(0f, bottom - minH)
                copy(x = newLeft, y = newTop, w = right - newLeft, h = bottom - newTop)
            }

            ComicPanelCorner.TOP_RIGHT -> {
                val newRight = (right + dx).coerceIn(left + minW, pageWidth.toFloat())
                val newTop = (top + dy).coerceIn(0f, bottom - minH)
                copy(y = newTop, w = newRight - left, h = bottom - newTop)
            }

            ComicPanelCorner.BOTTOM_LEFT -> {
                val newLeft = (left + dx).coerceIn(0f, right - minW)
                val newBottom = (bottom + dy).coerceIn(top + minH, pageHeight.toFloat())
                copy(x = newLeft, w = right - newLeft, h = newBottom - top)
            }

            ComicPanelCorner.BOTTOM_RIGHT -> {
                val newRight = (right + dx).coerceIn(left + minW, pageWidth.toFloat())
                val newBottom = (bottom + dy).coerceIn(top + minH, pageHeight.toFloat())
                copy(w = newRight - left, h = newBottom - top)
            }
        }
    }

    /**
     * 拖尾巴尖。
     *
     * 两条夹取（都是"不这样就会看着像坏了"的那种）：
     *  1. 尾巴尖**只允许在泡身下方**（轮廓那条三角是从底边拉出来的，目标点在泡内 / 上边就画不出来 ✗
     *     —— `bubblePath` 里对 `tailTarget.y > bottom` 有判断，这里先兜住，
     *     免得用户拖了半天以为功能坏了）；
     *  2. 不出页（拖到页外就再也点不中了 ✗）。
     */
    fun tailDraggedTo(tx: Float, ty: Float, pageWidth: Int, pageHeight: Int): ComicBubble {
        val minY = bottom + (pageHeight * 0.004f).coerceAtLeast(1f)
        return copy(
            tailX = tx.coerceIn(0f, pageWidth.toFloat()),
            tailY = ty.coerceIn(minY.coerceAtMost(pageHeight.toFloat()), pageHeight.toFloat()),
        )
    }

    /** 收掉尾巴（切到 `RECT` 那种没有尾巴的样式时顺手清一下 ✓）。 */
    fun withTailCleared(): ComicBubble = copy(tailX = null, tailY = null)

    /** 换底板尺寸之后夹回页内（尺寸先夹、再贴位置 —— 顺序反了会又捅出页外 ✗）。 */
    fun clampedTo(pageWidth: Int, pageHeight: Int, minSide: Float = 0f): ComicBubble {
        val limit = if (minSide > 0f) minSide else ComicOverlayMetrics.minSide(pageWidth)
        val maxW = pageWidth.toFloat()
        val maxH = pageHeight.toFloat()
        val nextW = w.coerceIn(limit.coerceAtMost(maxW), maxW)
        val nextH = h.coerceIn(limit.coerceAtMost(maxH), maxH)
        return copy(
            x = x.coerceIn(0f, (pageWidth - nextW).coerceAtLeast(0f)),
            y = y.coerceIn(0f, (pageHeight - nextH).coerceAtLeast(0f)),
            w = nextW,
            h = nextH,
            tailX = tailX?.coerceIn(0f, maxW),
            tailY = tailY?.coerceIn(0f, maxH),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("w", w.toDouble())
        put("h", h.toDouble())
        if (style != ComicBubbleStyles.ROUND) put("style", style)
        tailX?.let { put("tailX", it.toDouble()) }
        tailY?.let { put("tailY", it.toDouble()) }
        // 空文本 / 默认字号 / 默认颜色 / 居中对齐都不写键：JSON 小一点，读侧缺省就是这些 ✓
        if (text.isNotEmpty()) put("text", text)
        if (fontSize != 64f) put("fontSize", fontSize.toDouble())
        if (colorArgb != COMIC_DEFAULT_TEXT_COLOR) put("color", colorArgb)
        if (align != ComicTextAlign.CENTER) put("align", align.name)
        fontFile?.let { put("font", it) }
        if (stroke) put("stroke", true)
    }

    companion object {
        fun fromJson(json: JSONObject?): ComicBubble? {
            if (json == null) return null
            val id = json.optString("id", "")
            if (id.isEmpty()) return null
            val w = json.optDouble("w", 0.0).toFloat()
            val h = json.optDouble("h", 0.0).toFloat()
            // 退化的宽高读进来只会画出一个看不见、也点不中的气泡 —— 直接丢 ✓
            if (w <= 0f || h <= 0f) return null
            val style = json.optString("style", ComicBubbleStyles.ROUND)
                .takeIf { ComicBubbleStyles.isKnown(it) } ?: ComicBubbleStyles.ROUND
            val tailX = if (json.has("tailX")) json.optDouble("tailX", 0.0).toFloat() else null
            val tailY = if (json.has("tailY")) json.optDouble("tailY", 0.0).toFloat() else null
            return ComicBubble(
                id = id,
                x = json.optDouble("x", 0.0).toFloat(),
                y = json.optDouble("y", 0.0).toFloat(),
                w = w,
                h = h,
                style = style,
                tailX = tailX,
                tailY = tailY,
                text = json.optString("text", ""),
                fontSize = json.optDouble("fontSize", 64.0).toFloat().takeIf { it > 0f } ?: 64f,
                colorArgb = json.optInt("color", COMIC_DEFAULT_TEXT_COLOR),
                align = ComicTextAlign.entries.firstOrNull {
                    it.name.equals(json.optString("align", ""), ignoreCase = true)
                } ?: ComicTextAlign.CENTER,
                fontFile = json.optString("font", "").takeIf { it.isNotEmpty() },
                stroke = json.optBoolean("stroke", false),
            )
        }
    }
}

/**
 * **页面上的独立文本**（不是泡里的那段字 —— 那个存在 [ComicBubble.text] 上 ✓）。
 *
 * 与气泡共用同一套位置尺寸、字号、颜色、对齐、字体；差别只有"没有样式、没有尾巴" ✓。
 */
data class ComicText(
    val id: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val text: String = "",
    val fontSize: Float = 64f,
    val colorArgb: Int = COMIC_DEFAULT_TEXT_COLOR,
    val align: ComicTextAlign = ComicTextAlign.START,
    val fontFile: String? = null,
    /** **描边**（黑边 + 字芯画两遍 ✓；见 [ComicBubble.stroke]）。 */
    val stroke: Boolean = false,
) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h

    fun movedBy(dx: Float, dy: Float, pageWidth: Int, pageHeight: Int): ComicText {
        val maxX = (pageWidth - w).coerceAtLeast(0f)
        val maxY = (pageHeight - h).coerceAtLeast(0f)
        return copy(
            x = (x + dx).coerceIn(0f, maxX),
            y = (y + dy).coerceIn(0f, maxY),
        )
    }

    fun resizedBy(
        corner: ComicPanelCorner,
        dx: Float,
        dy: Float,
        pageWidth: Int,
        pageHeight: Int,
        minSide: Float = 0f,
    ): ComicText {
        val limit = if (minSide > 0f) minSide else ComicOverlayMetrics.minSide(pageWidth)
        val minW = limit.coerceAtMost(pageWidth.toFloat())
        val minH = limit.coerceAtMost(pageHeight.toFloat())
        val left = x
        val top = y
        val right = x + w
        val bottom = y + h

        return when (corner) {
            ComicPanelCorner.TOP_LEFT -> {
                val newLeft = (left + dx).coerceIn(0f, right - minW)
                val newTop = (top + dy).coerceIn(0f, bottom - minH)
                copy(x = newLeft, y = newTop, w = right - newLeft, h = bottom - newTop)
            }

            ComicPanelCorner.TOP_RIGHT -> {
                val newRight = (right + dx).coerceIn(left + minW, pageWidth.toFloat())
                val newTop = (top + dy).coerceIn(0f, bottom - minH)
                copy(y = newTop, w = newRight - left, h = bottom - newTop)
            }

            ComicPanelCorner.BOTTOM_LEFT -> {
                val newLeft = (left + dx).coerceIn(0f, right - minW)
                val newBottom = (bottom + dy).coerceIn(top + minH, pageHeight.toFloat())
                copy(x = newLeft, w = right - newLeft, h = newBottom - top)
            }

            ComicPanelCorner.BOTTOM_RIGHT -> {
                val newRight = (right + dx).coerceIn(left + minW, pageWidth.toFloat())
                val newBottom = (bottom + dy).coerceIn(top + minH, pageHeight.toFloat())
                copy(w = newRight - left, h = newBottom - top)
            }
        }
    }

    fun clampedTo(pageWidth: Int, pageHeight: Int, minSide: Float = 0f): ComicText {
        val limit = if (minSide > 0f) minSide else ComicOverlayMetrics.minSide(pageWidth)
        val maxW = pageWidth.toFloat()
        val maxH = pageHeight.toFloat()
        val nextW = w.coerceIn(limit.coerceAtMost(maxW), maxW)
        val nextH = h.coerceIn(limit.coerceAtMost(maxH), maxH)
        return copy(
            x = x.coerceIn(0f, (pageWidth - nextW).coerceAtLeast(0f)),
            y = y.coerceIn(0f, (pageHeight - nextH).coerceAtLeast(0f)),
            w = nextW,
            h = nextH,
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("w", w.toDouble())
        put("h", h.toDouble())
        if (text.isNotEmpty()) put("text", text)
        if (fontSize != 64f) put("fontSize", fontSize.toDouble())
        if (colorArgb != COMIC_DEFAULT_TEXT_COLOR) put("color", colorArgb)
        if (align != ComicTextAlign.START) put("align", align.name)
        fontFile?.let { put("font", it) }
        if (stroke) put("stroke", true)
    }

    companion object {
        fun fromJson(json: JSONObject?): ComicText? {
            if (json == null) return null
            val id = json.optString("id", "")
            if (id.isEmpty()) return null
            val w = json.optDouble("w", 0.0).toFloat()
            val h = json.optDouble("h", 0.0).toFloat()
            if (w <= 0f || h <= 0f) return null
            return ComicText(
                id = id,
                x = json.optDouble("x", 0.0).toFloat(),
                y = json.optDouble("y", 0.0).toFloat(),
                w = w,
                h = h,
                text = json.optString("text", ""),
                fontSize = json.optDouble("fontSize", 64.0).toFloat().takeIf { it > 0f } ?: 64f,
                colorArgb = json.optInt("color", COMIC_DEFAULT_TEXT_COLOR),
                align = ComicTextAlign.entries.firstOrNull {
                    it.name.equals(json.optString("align", ""), ignoreCase = true)
                } ?: ComicTextAlign.START,
                fontFile = json.optString("font", "").takeIf { it.isNotEmpty() },
                stroke = json.optBoolean("stroke", false),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 与页 / 图层叠的接线（**纯函数**，有单测 ✓）
// ---------------------------------------------------------------------------

/** 气泡 / 文本的图层名（图层列表里显示用；和 `ComicLayer.addGenerated` 的 `"生成层"` 一个口径）。 */
const val COMIC_BUBBLE_LAYER_NAME = "气泡"
const val COMIC_TEXT_LAYER_NAME = "文本"

/** 底板层的固定名字（[ComicLayer.Kind.BASE]）。 */
const val COMIC_BASE_LAYER_NAME = "底板"

/**
 * 绘画层的固定名字（[ComicLayer.Kind.PAINT]）—— 用户 2026-09-20：
 * 「顶部放入的是画布编辑器，可以**选定图层或新建图层绘画**」✓。
 */
const val COMIC_PAINT_LAYER_NAME = "绘画层"

/** 这一页的气泡图层是不是 [id] 那一个。 */
fun ComicBoardPage.overlayKindOf(id: String): ComicLayer.Kind? = when {
    bubbles.any { it.id == id } -> ComicLayer.Kind.BUBBLE
    texts.any { it.id == id } -> ComicLayer.Kind.TEXT
    else -> null
}

fun ComicBoardPage.withBubbles(next: List<ComicBubble>): ComicBoardPage = copy(bubbles = next)

fun ComicBoardPage.withTexts(next: List<ComicText>): ComicBoardPage = copy(texts = next)

fun ComicBoardPage.withLayers(next: List<ComicLayer>): ComicBoardPage = copy(layers = next)

/**
 * 把图层叠**对齐**到"这一页实际有什么"（读盘和每次改动之后都该是恒等的 ✓）。
 *
 * 三件事（每一件都对应一种会出洋相的盘上数据）：
 *  1. **底板层在最底下、有且只有一层**（少一个"底板"层，图层面板里就说不清谁在最下面 ✗）；
 *  2. 每个气泡 / 文本在叠里**有且只有一层**（缺的补在最上面 —— 新加的本来就该盖在最上面 ✓）；
 *  3. 指向**已经不存在**的气泡 / 文本的图层一律丢掉（不然列表里会留一条点不中的幽灵 ✗）。
 *
 * ⚠️ **生成层（[ComicLayer.Kind.GENERATED]）不算"没主"** ✗：它是"每生成一张 = 新图层"那条口径
 * 存下来的层 ✓，它的主人是**格子**（`panelId`）而不是气泡 / 文本 —— 所以第 3 条**不能**把它扫掉。
 * （这一条是第 ⑥ 项接线时补的：少了它，**每次读盘 / 加一个气泡都会把生成层静默删光** ✗，
 * 用户在图层列表里刚看见的"生成层"下一次进页面就没了 ✓。）
 *
 * ⚠️ **绘画层（[ComicLayer.Kind.PAINT]）同理，也必须留** ✗（第 ⑬ 批，2026-09-20）：
 * 它的主人是**它自己**（没有 `panelId`、也不对应任何气泡 / 文本 ✓）——
 * 漏了这一条的表现和第 ⑥ 批那个坑一模一样：**加一颗气泡 → 用户刚画好的那一层静默消失** ✗
 *（钉子见 `ComicPaintLayerTest` 里"加一颗气泡之后再查 ✓"那条 ✓）。
 *
 * 生成的图层保持它们在原叠里的相对顺序 ✓（用户排过的顺序不会被这次规整打乱）。
 */
fun ComicBoardPage.normalizedOverlays(): ComicBoardPage {
    val bubblesById = bubbles.associateBy { it.id }
    val textsById = texts.associateBy { it.id }

    // ① 先过滤：只留"底板"、"生成层"（主人是格子 ✓）、"绘画层"（主人是它自己 ✓）
    //    和"确实有主"的气泡 / 文本层
    val kept = layers.filter { layer ->
        layer.kind == ComicLayer.Kind.BASE ||
            layer.kind == ComicLayer.Kind.GENERATED ||
            layer.kind == ComicLayer.Kind.PAINT ||
            bubblesById.containsKey(layer.id) ||
            textsById.containsKey(layer.id)
    }
    // ② 图层 id 撞了（同一层出现两次）只留第一次 —— 否则画两遍、点两遍
    val seen = HashSet<String>()
    val deduped = kept.filter { seen.add(it.id) }

    val baseLayer = deduped.firstOrNull { it.kind == ComicLayer.Kind.BASE }
        ?: ComicLayer(id = "$id-base", name = COMIC_BASE_LAYER_NAME, kind = ComicLayer.Kind.BASE, imagePath = basePath)
    val stack = deduped.filter { it.kind != ComicLayer.Kind.BASE }

    // ③ 缺的补在最上面（顺序：气泡先加的在前面，和 bubbles 列表一致 ✓）
    val missing = buildList {
        bubbles.forEach { bubble ->
            if (stack.none { it.id == bubble.id }) {
                add(ComicLayer(bubble.id, COMIC_BUBBLE_LAYER_NAME, ComicLayer.Kind.BUBBLE))
            }
        }
        texts.forEach { text ->
            if (stack.none { it.id == text.id }) {
                add(ComicLayer(text.id, COMIC_TEXT_LAYER_NAME, ComicLayer.Kind.TEXT))
            }
        }
    }

    return copy(layers = listOf(baseLayer) + stack + missing)
}

/** 解码一个 JSON 数组里的气泡 / 文本（坏数据吞掉，不抛 ✓）。 */
fun decodeBubbleArray(json: JSONArray?): List<ComicBubble> {
    if (json == null) return emptyList()
    return (0 until json.length()).mapNotNull { index ->
        json.optJSONObject(index)?.let { ComicBubble.fromJson(it) }
    }
}

fun decodeTextArray(json: JSONArray?): List<ComicText> {
    if (json == null) return emptyList()
    return (0 until json.length()).mapNotNull { index ->
        json.optJSONObject(index)?.let { ComicText.fromJson(it) }
    }
}

fun encodeBubbleArray(items: List<ComicBubble>): JSONArray =
    JSONArray().apply { items.forEach { put(it.toJson()) } }

fun encodeTextArray(items: List<ComicText>): JSONArray =
    JSONArray().apply { items.forEach { put(it.toJson()) } }

/** 图层叠的 JSON（顺序 = 从下到上，**读回来也要是这个顺序** ✓）。 */
fun encodeLayerArray(items: List<ComicLayer>): JSONArray =
    JSONArray().apply { items.forEach { put(it.toJson()) } }

fun decodeLayerArray(json: JSONArray?): List<ComicLayer> {
    if (json == null) return emptyList()
    return (0 until json.length()).mapNotNull { index ->
        json.optJSONObject(index)?.let { comicLayerFromJson(it) }
    }
}

/**
 * [ComicLayer] 的 JSON 编解码。
 *
 * ⚠️ 写成**扩展函数放在这里**（而不是往 `ComicLayer.kt` 里塞成员）：
 * 那个文件是**另一批人**刚落地并有单测的地基 ✓，这块接线不该去动它一行
 * （这一轮同时开工的任务多，改别人刚交付的文件是最容易撞车的地方 ✗）。
 * 同包扩展，调用方看不出差别 ✓。
 */
fun ComicLayer.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("kind", kind.name)
    panelId?.let { put("panelId", it) }
    imagePath?.let { put("imagePath", it) }
    // 默认值（可见 / 不透明）不写键：JSON 小一点，读侧缺省就是它们 ✓
    if (!visible) put("visible", false)
    if (opacity != 1f) put("opacity", opacity.toDouble())
}

fun comicLayerFromJson(json: JSONObject?): ComicLayer? {
    if (json == null) return null
    val id = json.optString("id", "")
    if (id.isEmpty()) return null
    // ⚠️ 认不出的旧值 / 手改坏的值一律兜底成 BUBBLE ✓（**不抛** ✗）——
    // 认得出的照名字解（`BASE` / `GENERATED` / `BUBBLE` / `TEXT` / `PAINT` ✓，
    // 2026-09-20 新加的 `PAINT` 走的就是这条通用解，**不用单独加分支** ✓）。
    val kind = ComicLayer.Kind.entries.firstOrNull {
        it.name.equals(json.optString("kind", ""), ignoreCase = true)
    } ?: ComicLayer.Kind.BUBBLE
    return ComicLayer(
        id = id,
        name = json.optString("name", "").ifBlank { id },
        kind = kind,
        panelId = json.optString("panelId", "").takeIf { it.isNotEmpty() },
        imagePath = json.optString("imagePath", "").takeIf { it.isNotEmpty() },
        visible = json.optBoolean("visible", true),
        opacity = json.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
    )
}
