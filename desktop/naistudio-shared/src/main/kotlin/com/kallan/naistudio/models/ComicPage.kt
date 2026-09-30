package com.kallan.naistudio.models

import com.kallan.naistudio.services.InpaintSize
import org.json.JSONArray
import org.json.JSONObject

/**
 * **高级漫画模式（`docs/43-方案-高级漫画模式.md`）M1 的数据模型**：
 * 一页 = 一块**底板** + N 个**格子**。
 *
 * 这一块只负责"底板 + 格子的几何与持久化"，**不碰生成管线** ✗：
 * 聚焦重绘调用、队列、气泡、文本、图层、拼页导出都不在这里（后面的批次接力）。
 *
 * ## ⚠️ 为什么类名是 [ComicBoardPage]，而不是方案里写的 `ComicPage`
 *
 * 同一个包里（[NaiModels]）**已经有一个 `ComicPage`** —— 那是**旧漫画模式（狂暴漫画）**的一页：
 * 它的格子坐标由版式（[ComicLayout]）**派生**，"第几页 / 阅读顺序"挂在它上面，
 * 和 `comic_panels_v1` 那份 `CharCaptionItem` 列表是配套的。
 *
 * 高级漫画这一页是**自由矩形**（模板铺 + 手拖），字段、语义、存储键全都不同，
 * 两者不是一回事 —— 所以新类型**另起一个名字**，旧那个一个字都不动 ✓
 *（用户口径：「新开一页，旧模式并存」✓）。文件名仍按方案文档叫 `ComicPage.kt` ✓。
 */

/**
 * 一页里的**一个格子**（自由矩形，单位 = 底板像素）。
 *
 * 坐标是**底板像素**而不是屏幕像素：底板可以被界面任意缩放显示（适应窗口），
 * 但存下来的矩形永远对着底板那张图的真实像素 —— 这样"目标像素"（[targetPixelText]）
 * 才不会随窗口大小漂移 ✓。
 *
 * @param id 稳定 id（拖动 / 选中 / 删除都按它找，不用下标 —— 换序之后下标会错位 ✗）
 * @param x 左上角 X（底板像素）
 * @param y 左上角 Y（底板像素）
 * @param w 宽（底板像素，> 0）
 * @param h 高（底板像素，> 0）
 * @param prompt 这一格的**正面**提示词（负面提示词全页共用，用户口径 ✓）。
 * @param resultPath 这一格**最近一次**生成出来的图（图库里的那张 ✓，见 [ComicRunPlan] 那一批）。
 *   null = 还没跑过。同一格重生成只把它指向新图，**旧图不删**（旧的那张还在图库里 ✓）。
 * @param order **生成序号**（用户口径：队列按序号逐格跑 ✓）。0 起、页内唯一。
 */
data class ComicPanel(
    val id: String,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val prompt: String = "",
    val resultPath: String? = null,
    val order: Int = 0,
    /**
     * **这一格自己的画面**（第 ㊿l 批「格子与图层合一」✓，用户 2026-09-22 ✓）。
     *
     * 合一之后：**一个格子 = 一个框 + 一张图 + 一段提示词** ✓ ——
     * 原来的 `ComicLayer`（`imagePath` / `opacity` / `visible`）并到格子上 ✓，
     * 于是"点图层就能写提示词"不再是两件事 ✓（用户原话：
     * 「**格子和图层功能重合，合为一体，点击图层即可写提示词，新建图层变为新建格子**」✓）。
     *
     * ⚠️ **不迁移老工程**（用户拍板 ✓）：旧 `prefs.json` 里那些"绑在格子上的生成层"**不搬过来** ✓
     * —— 旧工程读出来就是**空页** ✓（用户自己测试用的工程，无所谓 ✓）。
     * 旧的 `resultPath` **照旧读** ✓（那是老字段，留着不碍事 ✓）。
     *
     * @param imagePath 这一格的图（图库里的那张 / scratch 里那张 ✓）；null = 还没画面 ✓
     * @param opacity 不透明度（原来在图层的字段上 ✓）
     * @param visible 眼睛（同上 ✓）
     */
    val imagePath: String? = null,
    val opacity: Float = 1f,
    val visible: Boolean = true,
) {
    val right: Float get() = x + w
    val bottom: Float get() = y + h

    /**
     * **这一格的目标像素**（只读显示用，比如「1792×576」）。
     *
     * 走的是已经写好并有单测的那一个换算函数 [InpaintSize.forRect] ✓
     * —— 用户口径（`docs/43` §9.4）：宽高按格子比例等比放大到 1024×1024 面积上限、
     * 两边对齐到 64 的倍数。**界面里只读，不给人手改** ✗。
     */
    fun targetPixelText(): String {
        val (width, height) = targetSize()
        return "$width×$height"
    }

    /** 同上，但给的是**那两个数**（确认框 / 执行器要拿它喂价格与请求 ✓，别去解析字符串 ✗）。 */
    fun targetSize(): Pair<Int, Int> = InpaintSize.forRect(w, h)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("w", w.toDouble())
        put("h", h.toDouble())
        // 空提示词不写键：JSON 小一点，读侧缺省就是空串（和 ComicPage 的 summary 一个口径）
        if (prompt.isNotEmpty()) put("prompt", prompt)
        // 还没跑过的格子不写键（同上：读侧缺省就是 null）
        if (resultPath != null) put("resultPath", resultPath)
        put("order", order)
        // ---- 第 ㊿l 批「格子与图层合一」：格子自己的画面 / 不透明度 / 眼睛 ✓ ----
        // 缺省值一律不写键（与上面同一条规矩：JSON 小一点 ✓，读侧缺省就是
        // "还没画面 / 完全不透明 / 可见" ✓）
        if (imagePath != null) put("imagePath", imagePath)
        if (opacity != 1f) put("opacity", opacity.toDouble())
        if (!visible) put("visible", false)
    }

    companion object {
        fun fromJson(json: JSONObject?): ComicPanel? {
            if (json == null) return null
            val id = json.optString("id", "")
            if (id.isEmpty()) return null
            val w = json.optDouble("w", 0.0).toFloat()
            val h = json.optDouble("h", 0.0).toFloat()
            // 退化的宽高（0 / 负数）读进来只会画出一个看不见、也点不中的格子 —— 直接丢掉
            if (w <= 0f || h <= 0f) return null
            return ComicPanel(
                id = id,
                x = json.optDouble("x", 0.0).toFloat(),
                y = json.optDouble("y", 0.0).toFloat(),
                w = w,
                h = h,
                prompt = json.optString("prompt", ""),
                resultPath = json.optString("resultPath", "").takeIf { it.isNotEmpty() },
                order = json.optInt("order", 0),
                // 合一之后格子自己带画面 ✓（老工程没有这几个键 ⇒ 就是"空页" ✓，用户拍板不迁移 ✓）
                imagePath = json.optString("imagePath", "").takeIf { it.isNotEmpty() },
                opacity = json.optDouble("opacity", 1.0).toFloat().coerceIn(0f, 1f),
                visible = json.optBoolean("visible", true),
            )
        }
    }
}

/**
 * 高级漫画的**一页**：底板 + 格子。
 *
 * @param id 页 id（`comic.page.current` 存的就是它）
 * @param name 页名（页条 / 导出文件名以后用得上）
 * @param basePath 底板图片路径；**null = 空白画布**（新建画布那种，还没有图）
 * @param baseWidth 底板宽（像素）
 * @param baseHeight 底板高（像素）
 * @param panels 格子。**顺序不保证** —— 要按序号读请走 [orderedPanels] ✓
 * @param bubbles 对话气泡（`docs/43` §9.1；见 `ComicOverlay.kt`）✓
 * @param texts 页面上的独立文本（§9.2）✓
 * @param layers 图层叠：**下标 0 = 最底下** ✓（底板 / 气泡 / 文本各自一层，见 [ComicLayer]）
 */
data class ComicBoardPage(
    val id: String,
    val name: String,
    val basePath: String? = null,
    val baseWidth: Int,
    val baseHeight: Int,
    val panels: List<ComicPanel> = emptyList(),
    val bubbles: List<ComicBubble> = emptyList(),
    val texts: List<ComicText> = emptyList(),
    val layers: List<ComicLayer> = emptyList(),
) {
    /** 按**生成序号**排好的格子（同号的按列表顺序，稳定排序 —— 不会自己抖）。 */
    fun orderedPanels(): List<ComicPanel> = panels.sortedBy { it.order }

    fun withPanels(next: List<ComicPanel>): ComicBoardPage = copy(panels = next)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        if (basePath != null) put("basePath", basePath)
        put("baseWidth", baseWidth)
        put("baseHeight", baseHeight)
        put(
            "panels",
            JSONArray().apply { panels.forEach { put(it.toJson()) } },
        )
        // 气泡 / 文本 / 图层：**空就不写键** ✓（没有气泡的页 JSON 与老版本逐字一致 ——
        // 老数据读回来、写回去不会平白多出三个 `[]`）
        if (bubbles.isNotEmpty()) put("bubbles", encodeBubbleArray(bubbles))
        if (texts.isNotEmpty()) put("texts", encodeTextArray(texts))
        if (layers.isNotEmpty()) put("layers", encodeLayerArray(layers))
    }

    companion object {
        fun fromJson(json: JSONObject?): ComicBoardPage? {
            if (json == null) return null
            val id = json.optString("id", "")
            if (id.isEmpty()) return null
            // 尺寸缺失 / 退化 → 用默认页（宁可给一块合法底板，也不要一个 0×0 的画布把界面算崩）
            val width = json.optInt("baseWidth", ComicBoardPresets.DEFAULT_WIDTH)
                .takeIf { it > 0 } ?: ComicBoardPresets.DEFAULT_WIDTH
            val height = json.optInt("baseHeight", ComicBoardPresets.DEFAULT_HEIGHT)
                .takeIf { it > 0 } ?: ComicBoardPresets.DEFAULT_HEIGHT
            val array = json.optJSONArray("panels")
            val panels = if (array == null) {
                emptyList()
            } else {
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let { ComicPanel.fromJson(it) }
                }.normalizePanelOrder()
            }
            return ComicBoardPage(
                id = id,
                name = json.optString("name", ""),
                basePath = json.optString("basePath", "").takeIf { it.isNotEmpty() },
                baseWidth = width,
                baseHeight = height,
                panels = panels,
                bubbles = decodeBubbleArray(json.optJSONArray("bubbles")),
                texts = decodeTextArray(json.optJSONArray("texts")),
                layers = decodeLayerArray(json.optJSONArray("layers")),
            ).normalizedOverlays()
        }
    }
}

/**
 * 高级漫画的**落盘**（两个键，都在 `platform.kv` 里 —— 和 `comic_panels_v1` 那套同一个仓库）。
 *
 * ⚠️ 键名带 `comic.` 前缀是为了和旧漫画模式的 `comic_panels_v1` / `comic_settings_v1` **分开**：
 * 新旧两套共用一个 kv 文件，前缀撞了就会互相覆盖 ✗（用户口径：并存 ✓）。
 */
object ComicBoardStore {
    /** 当前页的 id（字符串；空串 = 还没有任何页）。 */
    const val KEY_CURRENT = "comic.page.current"

    /** 全部页（一个 JSON 数组，元素是 [ComicBoardPage.toJson]）。 */
    const val KEY_PAGES = "comic.pages"

    fun encodePages(pages: List<ComicBoardPage>): String =
        JSONArray().apply { pages.forEach { put(it.toJson()) } }.toString()

    fun decodePages(raw: String?): List<ComicBoardPage> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { ComicBoardPage.fromJson(it) }
            }
        } catch (e: Exception) {
            // 盘上的内容坏了也**不能崩**：退回"没有页"，用户重新建一块底板就是了
            emptyList()
        }
    }
}

/**
 * 界面上用的**底板尺寸预设**（用户口径 `docs/43` §Q7：A4 / B5 / 正方形 / 自定义 ✓）。
 *
 * [titleKey] 是 i18n 键（和 [ToolCatalog] 一个口径，模型里只放键、文案在 `RuntimeText`）。
 */
data class ComicBoardPreset(
    val id: String,
    val titleKey: String,
    val width: Int,
    val height: Int,
)

object ComicBoardPresets {
    const val A4 = "a4"
    const val B5 = "b5"
    const val SQUARE = "square"
    const val CUSTOM = "custom"

    /** 自定义画布的默认边长（也当读盘兜底用）。 */
    const val DEFAULT_WIDTH = 2048
    const val DEFAULT_HEIGHT = 2048

    /** 自定义尺寸的可填范围（太小没法摆格子，太大 canvas 也扛不住）。 */
    const val MIN_SIDE = 256
    const val MAX_SIDE = 8192

    val all = listOf(
        ComicBoardPreset(A4, "comic.preset.a4", 2480, 3508),
        ComicBoardPreset(B5, "comic.preset.b5", 2079, 2953),
        ComicBoardPreset(SQUARE, "comic.preset.square", 2048, 2048),
    )

    fun byId(id: String): ComicBoardPreset? = all.firstOrNull { it.id == id }
}

/**
 * 格子留边（**装订线**）等几何常量。
 *
 * 全部按**页面宽度的比例**算，而不是写死像素 —— 2480 宽的 A4 和 512 宽的自定义画布
 * 用同一个数字的话，前者看不见缝、后者半页都是边 ✗。
 */
object ComicBoardMetrics {
    /** 页面四周的留边（出血 / 装订线）。 */
    const val PAGE_MARGIN_RATIO = 0.035f

    /** 格子之间的缝。 */
    const val GAP_RATIO = 0.014f

    /** 手拖新建 / 缩放时的最小格子边长（小于它就是误触，不建）。 */
    const val MIN_PANEL_RATIO = 0.06f

    fun pageMargin(pageWidth: Int): Float = pageWidth * PAGE_MARGIN_RATIO

    fun gap(pageWidth: Int): Float = pageWidth * GAP_RATIO

    fun minPanelSide(pageWidth: Int): Float = pageWidth * MIN_PANEL_RATIO
}

/** 格子的四个角把手（缩放用）。 */
enum class ComicPanelCorner {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
}

/**
 * 一键铺格子的**模板**（用户口径：模板或手拖，两种都要 ✓）。
 *
 * [columns] × [rows] 的等分网格 —— "2 格竖排"就是 1 列 2 行、"3 格横排"是 3 列 1 行 ✓。
 */
data class ComicPanelTemplate(
    val id: String,
    val titleKey: String,
    val columns: Int,
    val rows: Int,
) {
    val count: Int get() = columns * rows
}

object ComicPanelTemplates {
    const val TWO_VERTICAL = "two_vertical"
    const val THREE_HORIZONTAL = "three_horizontal"
    const val FOUR_GRID = "four_grid"
    const val SIX_GRID = "six_2x3"

    val all = listOf(
        ComicPanelTemplate(TWO_VERTICAL, "comic.template.twoVertical", columns = 1, rows = 2),
        ComicPanelTemplate(THREE_HORIZONTAL, "comic.template.threeHorizontal", columns = 3, rows = 1),
        ComicPanelTemplate(FOUR_GRID, "comic.template.fourGrid", columns = 2, rows = 2),
        ComicPanelTemplate(SIX_GRID, "comic.template.sixGrid", columns = 2, rows = 3),
    )

    fun byId(id: String): ComicPanelTemplate? = all.firstOrNull { it.id == id }

    /**
     * 按模板算出一页的格子（底板像素）。
     *
     * @param idPrefix 新格子 id 的前缀 —— 同一个前缀 + 下标保证唯一；
     *   调用方每次铺模板给一个新前缀（见 `AppState.applyComicPanelTemplate`）。
     */
    fun build(
        template: ComicPanelTemplate,
        pageWidth: Int,
        pageHeight: Int,
        idPrefix: String,
    ): List<ComicPanel> {
        val margin = ComicBoardMetrics.pageMargin(pageWidth)
        val gap = ComicBoardMetrics.gap(pageWidth)
        val areaW = (pageWidth - margin * 2).coerceAtLeast(1f)
        val areaH = (pageHeight - margin * 2).coerceAtLeast(1f)
        val cellW = ((areaW - gap * (template.columns - 1)) / template.columns).coerceAtLeast(1f)
        val cellH = ((areaH - gap * (template.rows - 1)) / template.rows).coerceAtLeast(1f)

        val panels = ArrayList<ComicPanel>(template.count)
        // 序号按**阅读顺序**：先上后下、先左后右（旧漫画模式的默认是右起，但那一套是版式派生的；
        // 这里用户是自由矩形，按最直观的左上起排 ✓）
        var order = 0
        for (row in 0 until template.rows) {
            for (column in 0 until template.columns) {
                panels += ComicPanel(
                    id = "$idPrefix-$order",
                    x = margin + column * (cellW + gap),
                    y = margin + row * (cellH + gap),
                    w = cellW,
                    h = cellH,
                    order = order,
                )
                order++
            }
        }
        return panels
    }
}

/**
 * 格子的几何运算：**拖动**与**四角缩放**，纯函数（所以有单测 ✓）。
 *
 * 两条硬规则（都是"不这样就会出洋相"的那种）：
 *  1. **永远夹在页内**：格子被拖出底板之外就再也点不中了 ✗；
 *  2. **尺寸不小于 [ComicBoardMetrics.minPanelSide]**：否则一拽就成一条线，
 *     既看不见、也算不出目标像素 ✗。
 */

/** 拖动：只改位置，尺寸不动；越界就贴边。 */
fun ComicPanel.movedBy(
    dx: Float,
    dy: Float,
    pageWidth: Int,
    pageHeight: Int,
): ComicPanel {
    val maxX = (pageWidth - w).coerceAtLeast(0f)
    val maxY = (pageHeight - h).coerceAtLeast(0f)
    return copy(
        x = (x + dx).coerceIn(0f, maxX),
        y = (y + dy).coerceIn(0f, maxY),
    )
}

/** 缩放：拽某个角，**对角固定**，宽高联动；不小于最小边长、不越页。 */
fun ComicPanel.resizedBy(
    corner: ComicPanelCorner,
    dx: Float,
    dy: Float,
    pageWidth: Int,
    pageHeight: Int,
    /** 最小边长；不传（0）就按页面宽度算 [ComicBoardMetrics.minPanelSide]。 */
    minSide: Float = 0f,
): ComicPanel {
    // 对角那两个边：拽左上角时右 / 下边是不动的锚
    val left = x
    val top = y
    val right = x + w
    val bottom = y + h
    val limit = if (minSide > 0f) minSide else ComicBoardMetrics.minPanelSide(pageWidth)
    val minW = limit.coerceAtMost(pageWidth.toFloat())
    val minH = limit.coerceAtMost(pageHeight.toFloat())

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
 * 换底板之后把格子**夹回新页内**（换了小一点的底板，原来贴边的格子就跑到页外了 ✗）。
 *
 * 先保尺寸（不超过页、不低于最小边长），再贴位置 —— 顺序不能反，
 * 否则"先贴位置、再撑大尺寸"又会捅出页外 ✗。
 */
fun ComicPanel.clampedTo(
    pageWidth: Int,
    pageHeight: Int,
    minSide: Float = 0f,
): ComicPanel {
    val limit = if (minSide > 0f) minSide else ComicBoardMetrics.minPanelSide(pageWidth)
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

/**
 * **这一格现在能不能"直接"拖动 / 缩放**（用户 2026-09-20 第 ⑭ 批的口径 ✓）：
 *
 *  · `resultPath == null`（**还没出过图**，排版期）→ **可以** ✓ ——
 *    排版时把格子拖来拖去是正常操作，不该拦 ✓；
 *  · **出过图之后** → **只有它已经被选中**才可以 ✓。用户原话：
 *    「**格子:生成以后不能随意拖动与改变大小,只有经过套索选中后才能进行操作**」✓ ——
 *    出过图的格子是"画面上的一块内容"，按 PS 的口径它就不能乱动了 ✓。
 *
 * ⚠️ "选中"这件事由界面持有（`ComicModeUiState.selectedPanelId` ✓），所以这里**显式收一个参数**，
 * 而不在这个模型里再存一份"谁被选中了" ✗ —— 两份状态早晚会对不上（一份说选中了、另一份说没有 ✓）。
 */
fun ComicPanel.canBeDirectlyManipulated(selected: Boolean): Boolean =
    resultPath == null || selected

/**
 * 画布上一次「选择 / 套索」按下的**命中结果**（用户 2026-09-20：「画布逻辑和 ps 一样啊,
 * 在画布上的东西不能乱动,只有选中/套索后才可以动」✓）。
 *
 * 有它就够界面决定"这一下是选对象还是框像素"✓ —— 命中 = 选对象（**不进像素选区** ✗）✓，
 * 没命中 = 照旧走会话的像素选区 ✓。
 */
sealed interface ComicObjectHit {
    /** 命中的对象 id（格子 = `ComicPanel.id`；气泡 / 文本 = 它自己的 id ✓）。 */
    val id: String

    /** 一个**格子** ✓（出过图的那些要靠这条才能被选中、之后才允许拖动 / 缩放 ✓）。 */
    data class Panel(override val id: String) : ComicObjectHit

    /** 一个**气泡 / 文本** ✓（移动它们同样得先选中 ✓ —— 用户口径第 7 条 ✓）。 */
    data class Overlay(override val id: String) : ComicObjectHit
}

/**
 * 这一点（**底板像素** ✓）落在哪个对象上；什么都没沾 → `null` ✓。
 *
 * 优先级 = **画面上谁盖着谁** ✓：
 *  · 气泡 / 文本画在格子**之上**（`ComicOverlayLayer` 排在格子之后 ✓）→ 先看它们 ✓；
 *  · 格子之间后来者盖住先来者（`orderedPanels()` 正序画、后画的在上面 ✓）→ **倒着找** ✓。
 *  · 气泡与文本之间：文本层加在气泡之后（`normalizedOverlays` 的补齐顺序 ✓）→ 文本先看 ✓。
 */
fun ComicBoardPage.objectAt(pageX: Float, pageY: Float): ComicObjectHit? {
    texts.asReversed().firstOrNull { it.holdsPoint(pageX, pageY) }
        ?.let { return ComicObjectHit.Overlay(it.id) }
    bubbles.asReversed().firstOrNull { it.holdsPoint(pageX, pageY) }
        ?.let { return ComicObjectHit.Overlay(it.id) }
    panels.asReversed().firstOrNull { it.holdsPoint(pageX, pageY) }
        ?.let { return ComicObjectHit.Panel(it.id) }
    return null
}

/** 这一点在不在这个矩形里（闭区间 —— 贴着边按下去也算点上 ✓）。 */
private fun ComicPanel.holdsPoint(px: Float, py: Float): Boolean =
    px >= x && px <= right && py >= y && py <= bottom

private fun ComicBubble.holdsPoint(px: Float, py: Float): Boolean =
    px >= x && px <= right && py >= y && py <= bottom

private fun ComicText.holdsPoint(px: Float, py: Float): Boolean =
    px >= x && px <= right && py >= y && py <= bottom

/** 把序号规整成 0..n-1（按当前 order 排好之后重编号 —— 删掉一格之后序号就不会留空洞）。 */
fun List<ComicPanel>.normalizePanelOrder(): List<ComicPanel> =
    sortedBy { it.order }.mapIndexed { index, panel ->
        if (panel.order == index) panel else panel.copy(order = index)
    }

/** 上移 / 下移一格（`direction` 为 -1 上移、+1 下移），到边界就原样返回。 */
fun List<ComicPanel>.withPanelMoved(id: String, direction: Int): List<ComicPanel> {
    val ordered = sortedBy { it.order }.toMutableList()
    val index = ordered.indexOfFirst { it.id == id }
    val target = index + direction
    if (index < 0 || target !in ordered.indices) return this
    val moved = ordered[index]
    ordered[index] = ordered[target]
    ordered[target] = moved
    return ordered.mapIndexed { position, panel -> panel.copy(order = position) }
}

/**
 * **「跑这一页」的逐格清单**（第 ③ 批：队列逐格生成）。
 *
 * 存在的理由只有一个：**确认框里报的尺寸，必须就是真正发出去的尺寸** ✓。
 * 确认框和执行器各算一遍是两条口径，早晚会飘（界面上写 1792×576、请求里发 1024×1024 这种 ✗），
 * 所以两处都从这里取 ✓。
 *
 * 尺寸走的是**唯一那个换算函数** [InpaintSize.forRect]（`docs/43` §9.4：等比 → 面积 ≤1024×1024 →
 * 保 64 ✓）—— 和格子上/侧栏里只读显示的那个数是同一个 ✓。
 *
 * @param index 0 起的**生成序号**（界面上显示成"第 index+1 格"✓）
 */
data class ComicRunEntry(
    val panelId: String,
    val index: Int,
    val width: Int,
    val height: Int,
    val prompt: String,
)

object ComicRunPlan {

    /** 按生成序号排好的逐格清单（空页 → 空清单 ✓）。 */
    fun of(panels: List<ComicPanel>): List<ComicRunEntry> =
        panels.sortedBy { it.order }.mapIndexed { index, panel ->
            val (width, height) = panel.targetSize()
            ComicRunEntry(
                panelId = panel.id,
                index = index,
                width = width,
                height = height,
                prompt = panel.prompt,
            )
        }

    /**
     * 喂给 [ComicCost.estimate] 的形状（`panelId to (宽, 高)`）——
     * 这样价格函数拿到的尺寸与执行器要发的尺寸**必然一致** ✓。
     */
    fun costInput(entries: List<ComicRunEntry>): List<Pair<String, Pair<Int, Int>>> =
        entries.map { it.panelId to (it.width to it.height) }

    /** 提示词空着的那些格（发出去只会白花钱，跑之前要拦 ✓）。 */
    fun blankPrompt(entries: List<ComicRunEntry>): ComicRunEntry? =
        entries.firstOrNull { it.prompt.isBlank() }
}
