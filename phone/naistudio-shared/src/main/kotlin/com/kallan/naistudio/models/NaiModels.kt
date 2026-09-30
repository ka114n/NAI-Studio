package com.kallan.naistudio.models

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * 数据模型层。
 *
 * **行为对齐参考实现**：`novelai-image-desktop/mobile/lib/models/nai_models.dart`
 * （https://github.com/2786886095/novelai-image-desktop）。
 * 字段名、默认值、取值范围、归一化规则、UC 预设与质量标签文本都以其为准。
 * 代码是 Kotlin 自写，不搬 Dart 结构。
 *
 * 改这里的任何默认值之前，先回去核对上面那个文件。
 */

/** 下拉选项。label 保持英文原文，方便与参考实现逐条对照。 */
data class NaiOption(val label: String, val value: String)

object NaiModelId {
    const val V5_FULL = "nai-diffusion-5-full"
    const val V5_CURATED = "nai-diffusion-5-curated"
    const val V45_FULL = "nai-diffusion-4-5-full"
    const val V45_CURATED = "nai-diffusion-4-5-curated"
    const val V4_FULL = "nai-diffusion-4-full"
    const val V4_CURATED = "nai-diffusion-4-curated"
    const val V3 = "nai-diffusion-3"
    const val FURRY_V3 = "nai-diffusion-furry-3"
}

/** 模型 / 采样器 / 调度 / UC / 尺寸等全部可选项。 */
object NaiCatalog {

    val models = listOf(
        // 名字**故意简化**（用户要求）：选择器里塞不下 "NAI Diffusion xxx (Full model)" 这种长串，
        // 而且 "Full / Curated" 本身就说清了差别，括号里的解释是冗余的。
        NaiOption("NAI V5 Full", NaiModelId.V5_FULL),
        NaiOption("NAI V5 Curated", NaiModelId.V5_CURATED),
        NaiOption("NAI V4.5 Full", NaiModelId.V45_FULL),
        NaiOption("NAI V4.5 Curated", NaiModelId.V45_CURATED),
        NaiOption("NAI V4 Full", NaiModelId.V4_FULL),
        NaiOption("NAI V4 Curated", NaiModelId.V4_CURATED),
        NaiOption("NAI V3", NaiModelId.V3),
        NaiOption("NAI V3 Furry", NaiModelId.FURRY_V3),
    )

    val inpaintModels = listOf(
        NaiOption("NAI V5 Full Inpaint", "nai-diffusion-5-full-inpainting"),
        NaiOption("NAI V5 Curated Inpaint", "nai-diffusion-5-curated-inpainting"),
        NaiOption("NAI V4.5 Full Inpaint", "nai-diffusion-4-5-full-inpainting"),
        NaiOption("NAI V4.5 Curated Inpaint", "nai-diffusion-4-5-curated-inpainting"),
        NaiOption("NAI V4 Curated Inpaint", "nai-diffusion-4-curated-inpainting"),
        NaiOption("NAI V4 Full Inpaint", "nai-diffusion-4-full-inpainting"),
        NaiOption("NAI V3 Inpaint", "nai-diffusion-3-inpainting"),
    )

    /** 只有三个。参考实现里没有 polyexponential 这类插件侧的扩展值。 */
    val noiseSchedules = listOf(
        NaiOption("Native", "native"),
        NaiOption("Karras (common)", "karras"),
        NaiOption("Exponential", "exponential"),
    )

    /** 只有七个。 */
    val samplers = listOf(
        NaiOption("Euler Ancestral (Recommended)", "k_euler_ancestral"),
        NaiOption("Euler", "k_euler"),
        NaiOption("DPM++ 2M", "k_dpmpp_2m"),
        NaiOption("DPM++ 2M SDE", "k_dpmpp_2m_sde"),
        NaiOption("DPM++ SDE", "k_dpmpp_sde"),
        NaiOption("DPM++ 2S Ancestral", "k_dpmpp_2s_ancestral"),
        NaiOption("DDIM", "ddim_v3"),
    )

    val ucPresets = listOf(
        NaiOption("Heavy (strong negative)", "0"),
        NaiOption("Light (light negative)", "1"),
        NaiOption("Human Focus", "2"),
        NaiOption("None", "3"),
    )

    val sizePresets = listOf(
        SizePreset("Square 1024×1024", 1024, 1024),
        SizePreset("Landscape 1216×832", 1216, 832),
        SizePreset("Portrait 832×1216", 832, 1216),
        SizePreset("Portrait 1024×1536", 1024, 1536),
        SizePreset("Landscape 1536×1024", 1536, 1024),
        SizePreset("Large square 1472×1472", 1472, 1472),
        SizePreset("Wallpaper portrait 1088×1920", 1088, 1920),
        SizePreset("Wallpaper landscape 1920×1088", 1920, 1088),
        SizePreset("Small portrait 512×768", 512, 768),
        SizePreset("Small landscape 768×512", 768, 512),
        SizePreset("Small square 640×640", 640, 640),
    )

    val qualityPresets = listOf(
        NaiOption("Standard", "standard"),
        NaiOption("Light (V5 only)", "light"),
        NaiOption("None", "none"),
    )

    /** V5 不支持 noise_schedule 控制与 variety —— 见 [GenerateParams]。 */
    fun modelLabel(value: String): String =
        models.firstOrNull { it.value == value }?.label ?: value
}

data class SizePreset(val label: String, val width: Int, val height: Int)

/**
 * 角色分区里的单个角色。
 *
 * 字段对齐 **ComfyUI 节点** `novelai-genytools` 的 `defaultRole`
 * （`web/js/settings.js`）：`name / positive / negative / enabled / ai_choice / row / col / x / y / expanded`。
 *
 * 这里的映射关系：
 *  · `prompt` / `negativePrompt` → 节点的 `positive` / `negative`
 *  · `enabled` → 节点的 `enabled`（不勾选的角色整个不参与请求）
 *  · `useCoords` → 节点 `ai_choice` 的**取反**（勾了"AI决定位置"就是 `useCoords = false`）
 *
 * `useCoords` 之所以存反过来的量，是为了让 `NaiApi.buildPayload` 里的
 * `use_coords` 语义与参考 App 保持一致，不用再取反一次。
 *
 * `name` 可以点击修改（节点是双击改名）；留空时按序号显示 `角色 N`。
 */
data class CharCaptionItem(
    val name: String = "",
    val prompt: String = "",
    val negativePrompt: String = "",
    val enabled: Boolean = true,
    val useCoords: Boolean = false,
    val x: Double = 0.5,
    val y: Double = 0.5,
    /**
     * 卡片展开状态。**这里刻意跟着节点做**：源码 `defaultRole` 就把 `expanded` 放在角色数据里。
     * 放在数据里而不是 UI 局部状态，还能避开"编辑提示词导致重组、卡片当场收起"的坑。
     */
    val expanded: Boolean = false,
    /**
     * 漫画模式下的**槽位类型**：`""`=角色 / `"scene"`=场景 / `"text"`=台词 / `"prop"`=道具。
     *
     * 注意它**只能影响提示词前缀**，不可能靠槽位名生效 ——
     * `name` 字段不进 payload（`NaiApi` 只发 `char_caption = prompt`）。
     * 角色模式不用这个字段。旧数据缺省为空串，行为与之前一致。
     */
    val panelRole: String = "",
)

/**
 * 生成时的附加输入。
 *
 * `stylePresetPrompts` 是**在请求时**拼进风格提示词的：勾选预设不会改动风格提示词输入框，
 * 用户自己写的内容与预设内容各归各的，只在组装 payload 那一刻合并。
 *
 * ## 漫画模式（与角色分区**分别存储、互不干扰**）
 *
 * [charCaptions]（角色）与 [comicPanels]（分格）是**同一种东西**（同一个 [CharCaptionItem]
 * 形状），但是**两份独立数据**：在漫画模式里增删改分格，绝不会碰到角色列表；反之亦然。
 * 切换模式只翻 [comicMode] 一个布尔值，不搬运、不转换、不复制。
 *
 * 分格的坐标**永远由版式派生**（见 [ComicLayout]），所以分格自己的 `x/y/useCoords`
 * 不参与请求，也不参与编辑。
 */
data class GenerateExtras(
    val charCaptions: List<CharCaptionItem> = emptyList(),
    val stylePresetPrompts: List<String> = emptyList(),
    /**
     * **预设的负面词**（用户 2026-09-24：「**负面进负面**」）——
     * 与 [stylePresetPrompts]（正面 → 风格提示词）同一条链路：请求那一刻才并入，
     * 勾选 / 取消不回写输入框。
     */
    val stylePresetNegatives: List<String> = emptyList(),
    /** 当前是不是漫画模式。 */
    val comicMode: Boolean = false,
    /** 漫画分格列表（**当前页的活副本**）—— 角色列表的"同形不同份"兄弟。 */
    val comicPanels: List<CharCaptionItem> = emptyList(),
    /** 版式模板 id，见 [ComicLayout.templates]。 */
    val comicLayout: String = ComicLayout.DEFAULT,
    /** 阅读顺序：`rtl`（日漫右起）/ `ltr`。 */
    val comicOrder: String = ComicLayout.ORDER_RTL,
    /** 请求时是否自动加上漫画风格词（不改用户输入框，只在组装 payload 时拼）。 */
    val comicStylePrompt: Boolean = true,
    /**
     * 实际追加的风格词。默认是 [COMIC_DEFAULT_STYLE]。
     *
     * ⚠️ **只有用户能改它**。早先"按剧情分镜"会拿 LLM 输出的 style 覆盖这里，
     * 于是用户自己挑的调性被一次分镜冲掉；现在两段提示词都不再让模型输出风格词，
     * 这个字段是纯用户设置。
     */
    val comicStyle: String = COMIC_DEFAULT_STYLE,
    /**
     * 狂暴模式开关。
     *
     * 打开：剧情框里放**整部**剧情，主按钮变成"AI 自动分页 + 逐页分镜"，
     * 规划出来的每一页可以一次全部跑完。
     * 关闭：剧情框里放**一段**剧情，主按钮只处理当前这一页。
     */
    val comicBerserk: Boolean = false,
    /**
     * 漫画剧情框的文本（「按剧情分镜」与狂暴模式共用）。
     *
     * 持久化在 `comic_settings_v1` 的 `plot` 键（[ComicSettings.plot]），
     * 与正面提示词同一个口径：不改就一直活着，切页 / 杀进程 / 重启都不丢。
     */
    val comicPlot: String = "",
    /**
     * 漫画的**全部页**（含当前页）。
     *
     * 空列表 = 还没建立页表（老数据 / 从没进过狂暴模式）。这时用 [ensureComicPages] 补一页。
     */
    val comicPages: List<ComicPage> = emptyList(),
    /** 当前在第几页（0 起）。 */
    val comicPageIndex: Int = 0,
) {
    /** 当前模式生效的那份列表。所有增删改都只走它。 */
    val activeItems: List<CharCaptionItem>
        get() = if (comicMode) comicPanels else charCaptions

    /**
     * 换掉当前模式那份列表（另一份原样不动）。
     *
     * 漫画模式下会**同时**把改动写回 [comicPages] 的当前页 ——
     * 否则切页或批量出图时这一页的改动会丢。
     */
    fun withActiveItems(items: List<CharCaptionItem>): GenerateExtras =
        if (comicMode) withComicPage(panels = items) else copy(charCaptions = items)

    /** 当前页下标，夹到合法范围。页表为空时恒为 0。 */
    val comicPageSafeIndex: Int
        get() = if (comicPages.isEmpty()) 0 else comicPageIndex.coerceIn(0, comicPages.lastIndex)

    /**
     * 漫画模式下**唯一**的写入口：让"当前页的活副本"与"全部页表"一起变。
     *
     * - 只给 `panels` / `layout` / `order` = 改**当前页**（活副本与页表同步更新）
     * - 给 `pages` = 整份页表替换（狂暴模式规划、切页、加页、导入）
     *
     * 页表为空（单页时代的老数据）时只改活副本，不凭空造页表 ——
     * 造页表是 [ensureComicPages] 的职责，它只在进模式和读盘时调用。
     */
    fun withComicPage(
        panels: List<CharCaptionItem> = comicPanels,
        layout: String = comicLayout,
        order: String = comicOrder,
        summary: String? = null,
        pages: List<ComicPage>? = null,
    ): GenerateExtras {
        val mirror = copy(
            comicPanels = panels,
            comicLayout = layout,
            comicOrder = order,
            comicPages = pages ?: comicPages,
        )
        if (mirror.comicPages.isEmpty()) return mirror
        val index = mirror.comicPageSafeIndex
        val existing = mirror.comicPages[index]
        val updated = mirror.comicPages.toMutableList()
        updated[index] = existing.copy(
            panels = panels,
            layout = layout,
            order = order,
            summary = summary ?: existing.summary,
        )
        return mirror.copy(comicPages = updated)
    }

    /**
     * 保证漫画模式下**至少有一页**，并把当前的活副本灌进去。
     *
     * ⚠️ 只在**进入漫画模式**和**读盘**时调用：它会凭空造页表，
     * 不适合放在每次编辑的路径上。
     */
    fun ensureComicPages(): GenerateExtras {
        if (!comicMode) return this
        if (comicPages.isNotEmpty()) return withComicPage()
        return copy(
            comicPages = listOf(
                ComicPage(layout = comicLayout, order = comicOrder, panels = comicPanels),
            ),
            comicPageIndex = 0,
        )
    }

    /** 换页：先把当前页的改动写回页表，再装载目标页到活副本。 */
    fun withComicPageAt(index: Int): GenerateExtras {
        if (comicPages.isEmpty()) return this
        val synced = withComicPage()
        val target = index.coerceIn(0, synced.comicPages.lastIndex)
        val page = synced.comicPages[target]
        return synced.copy(
            comicPageIndex = target,
            comicPanels = page.panels,
            comicLayout = page.layout,
            comicOrder = page.order,
        )
    }

    /**
     * 造一份「渲染第 [index] 页」用的 extras 快照，**不动页表也不动活副本**。
     *
     * 狂暴模式的批量出图靠它：每一页排一个 [com.kallan.naistudio.state.QueuedJob]，
     * 各自带着自己那一页的分格与版式，于是 `NaiApi` 组装 payload 时读到的就是对的。
     */
    fun forComicPage(pages: List<ComicPage>, index: Int): GenerateExtras {
        val page = pages.getOrNull(index) ?: return this
        return copy(
            comicMode = true,
            comicPanels = page.panels,
            comicLayout = page.layout,
            comicOrder = page.order,
            comicPages = pages,
            comicPageIndex = index,
        )
    }

    /** 抽出漫画设置（持久化与备份用）。 */
    fun toComicSettings(): ComicSettings = ComicSettings(
        mode = comicMode,
        layout = comicLayout,
        order = comicOrder,
        stylePrompt = comicStylePrompt,
        style = comicStyle,
        pages = comicPages,
        pageIndex = comicPageSafeIndex,
        berserk = comicBerserk,
        plot = comicPlot,
    )
}

/**
 * 漫画模式默认追加的风格词。
 *
 * 一句话概括「整页的漫画风格与排版氛围」——具体画面内容归每一格的提示词管。
 * 依据参考实现 `v5-architect` 技能里"画漫画与多样分镜"那一段。
 */
const val COMIC_DEFAULT_STYLE = "manga page, dynamic paneling, dramatic layout"

/**
 * 漫画的**一页**。
 *
 * ## 与 [GenerateExtras.comicPanels] 的关系
 *
 * `comicPanels` / `comicLayout` / `comicOrder` 是"**当前页的活副本**"：
 * 界面上编辑的、`NaiApi` 组装 payload 时读的都是它 —— 这样分格编辑器、位置网格、
 * 坐标派生全都不用为多页改造。真正存"全部页"的是 [GenerateExtras.comicPages]。
 *
 * 两者由 [GenerateExtras.withComicPage] 一起维护。
 * **不要绕过它直接改 `comicPanels`**，否则切页时这一页的改动会丢。
 *
 * `order` 存在每一页上而不是全局：以后想让某一页横排、某一页右起都行。
 */
data class ComicPage(
    val layout: String = ComicLayout.AUTO,
    val order: String = ComicLayout.ORDER_RTL,
    val panels: List<CharCaptionItem> = emptyList(),
    /** 这一页在讲什么。狂暴模式规划出来的，给页条当标题用。 */
    val summary: String = "",
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("layout", layout)
        put("order", order)
        if (summary.isNotEmpty()) put("summary", summary)
        put(
            "panels",
            org.json.JSONArray().apply {
                panels.forEach { put(CharCaptionCodec.toJson(it)) }
            },
        )
    }

    companion object {
        fun fromJson(json: org.json.JSONObject?): ComicPage? {
            if (json == null) return null
            val array = json.optJSONArray("panels")
            val panels = if (array == null) {
                emptyList()
            } else {
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let { CharCaptionCodec.fromJson(it) }
                }
            }
            return ComicPage(
                layout = json.optString("layout", ComicLayout.AUTO)
                    .takeIf { it.isNotEmpty() } ?: ComicLayout.AUTO,
                order = ComicLayout.normalizeOrder(json.optString("order", ComicLayout.ORDER_RTL)),
                panels = panels,
                summary = json.optString("summary", ""),
            )
        }
    }
}

/**
 * 漫画模式的设置（当前页的分格单独持久化在 `comic_panels_v1`）。
 *
 * 独立于 [AppSettings]，所以**不需要动 AppSettings 的字段与备份结构**；
 * 只在备份里加一个可缺省的 `comic` 小节（老备份读得进来，未知小节被忽略）。
 *
 * [pages] 是狂暴模式引入的：**老数据没有这个键** → 空表，加载时按
 * [layout] + `comic_panels_v1` 补一页，行为与单页时代完全一致。
 */
data class ComicSettings(
    val mode: Boolean = false,
    val layout: String = ComicLayout.DEFAULT,
    val order: String = ComicLayout.ORDER_RTL,
    val stylePrompt: Boolean = true,
    val style: String = COMIC_DEFAULT_STYLE,
    val pages: List<ComicPage> = emptyList(),
    val pageIndex: Int = 0,
    /** 狂暴模式开关（见 [GenerateExtras.comicBerserk]）。 */
    val berserk: Boolean = false,
    /**
     * 漫画剧情框的文本（「按剧情分镜」与狂暴模式共用）。
     *
     * 和正面提示词一个口径：**只要不改，就永远在**——切页面、杀进程、重启都不丢。
     * 0.2.116 及之前它只存内存（切页面就丢，用户报了 bug），0.2.117 起落盘进
     * `comic_settings_v1`；老数据没有 `plot` 键 → 空串，行为不变。
     */
    val plot: String = "",
    /**
     * 「整页风格词一次性退回默认」做过了没有。
     *
     * 0.2.103 之前 LLM 的输出会覆盖 [style]，存量安装里那个值可能是 AI 写的。
     * 用户要求"不给 AI 修改权限、回退到默认"，所以升级后要**退一次**；
     * 但不能每次启动都退 —— 那样用户手改的风格词永远留不住。
     * 这个标记就是那个"只做一次"的凭据。
     */
    val styleReverted: Boolean = false,
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("mode", mode)
        put("layout", layout)
        put("order", order)
        put("stylePrompt", stylePrompt)
        put("style", style)
        put("berserk", berserk)
        // 剧情为空串就不写：与「没有这个键」同形，老版本读回来也是空串
        if (plot.isNotEmpty()) {
            put("plot", plot)
        }
        put("styleReverted", styleReverted)
        // 空页表不写：让"没有多页"和"有一页空页"在盘上长得不一样
        if (pages.isNotEmpty()) {
            put(
                "pages",
                org.json.JSONArray().apply { pages.forEach { put(it.toJson()) } },
            )
            put("pageIndex", pageIndex)
        }
    }

    companion object {
        fun fromJson(json: org.json.JSONObject?): ComicSettings {
            if (json == null) return ComicSettings()
            val pages = json.optJSONArray("pages")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    ComicPage.fromJson(array.optJSONObject(index))
                }
            }.orEmpty()
            return ComicSettings(
                mode = json.optBoolean("mode", false),
                layout = json.optString("layout", ComicLayout.DEFAULT)
                    .takeIf { it.isNotEmpty() } ?: ComicLayout.DEFAULT,
                order = ComicLayout.normalizeOrder(json.optString("order", ComicLayout.ORDER_RTL)),
                stylePrompt = json.optBoolean("stylePrompt", true),
                style = json.optString("style", COMIC_DEFAULT_STYLE)
                    .takeIf { it.isNotEmpty() } ?: COMIC_DEFAULT_STYLE,
                pages = pages,
                pageIndex = json.optInt("pageIndex", 0),
                berserk = json.optBoolean("berserk", false),
                // 老数据没有 plot 键 → 空串（0.2.116 及之前剧情不落盘）
                plot = json.optString("plot", ""),
                // 老设置没有这个键 → false → 升级后**退一次**默认风格词
                styleReverted = json.optBoolean("styleReverted", false),
            )
        }
    }
}


// ---------------------------------------------------------------------------
// 尺寸归一化
// ---------------------------------------------------------------------------

const val NAI_DIMENSION_STEP = 64
const val NAI_MIN_DIMENSION = 64
const val NAI_MAX_PIXEL_AREA = 3_145_728
const val NAI_MAX_DIMENSION = NAI_MAX_PIXEL_AREA / NAI_MIN_DIMENSION

/** 对齐到 64 的倍数，并夹到合法范围。 */
fun snapNaiDimension(value: Int, fallback: Int = 1024): Int {
    val source = if (value > 0) value else if (fallback > 0) fallback else 1024
    val snapped = (source.toDouble() / NAI_DIMENSION_STEP).roundToLong() * NAI_DIMENSION_STEP
    return snapped.coerceIn(NAI_MIN_DIMENSION.toLong(), NAI_MAX_DIMENSION.toLong()).toInt()
}

/** 在给定另一边的尺寸下，这一边最大能到多少（保证总面积不超限）。 */
fun maxNaiDimensionFor(pairedDimension: Int): Int {
    val paired = snapNaiDimension(pairedDimension, NAI_MIN_DIMENSION)
    return max(NAI_MIN_DIMENSION, (NAI_MAX_PIXEL_AREA / paired / NAI_DIMENSION_STEP) * NAI_DIMENSION_STEP)
}

fun snapNaiDimensionWithinArea(value: Int, pairedDimension: Int, fallback: Int = 1024): Int =
    min(snapNaiDimension(value, fallback), maxNaiDimensionFor(pairedDimension))

/** 把任意宽高规整成 NovelAI 接受的尺寸；超面积时等比缩到限内。 */
fun fitNaiImageSize(
    width: Int,
    height: Int,
    fallbackWidth: Int = 832,
    fallbackHeight: Int = 1216,
): Pair<Int, Int> {
    val snappedWidth = snapNaiDimension(width, fallbackWidth)
    val snappedHeight = snapNaiDimension(height, fallbackHeight)
    if (snappedWidth.toLong() * snappedHeight <= NAI_MAX_PIXEL_AREA) {
        return snappedWidth to snappedHeight
    }
    val scale = sqrt(NAI_MAX_PIXEL_AREA.toDouble() / (snappedWidth.toDouble() * snappedHeight))
    return max(
        NAI_MIN_DIMENSION,
        (snappedWidth * scale / NAI_DIMENSION_STEP).toInt() * NAI_DIMENSION_STEP,
    ) to max(
        NAI_MIN_DIMENSION,
        (snappedHeight * scale / NAI_DIMENSION_STEP).toInt() * NAI_DIMENSION_STEP,
    )
}

// ---------------------------------------------------------------------------
// 生成参数
// ---------------------------------------------------------------------------

data class GenerateParams(
    val model: String = NaiModelId.V5_FULL,
    val stylePrompt: String = "",
    val positivePrompt: String = "",
    val negativePrompt: String = "",
    val width: Int = 832,
    val height: Int = 1216,
    val steps: Int = 28,
    val cfgScale: Double = 6.0,
    val cfgRescale: Double = 0.0,
    val sampler: String = "k_euler_ancestral",
    val noiseSchedule: String = "karras",
    val seed: Long = 0L,
    /** "random" 或 "fixed"。 */
    val seedMode: String = "random",
    val ucPreset: Int = 2,
    /** "standard" / "light" / "none"。 */
    val qualityPreset: String = "standard",
    val qualityToggle: Boolean = true,
    val transparentBackground: Boolean = false,
    val smea: Boolean = false,
    val smeaDyn: Boolean = false,
    val variety: Boolean = false,
    val fileNamePrefix: String = "",
) {

    val isV5: Boolean get() = model.startsWith("nai-diffusion-5")
    val isV4Plus: Boolean get() = model.startsWith("nai-diffusion-4") || model.startsWith("nai-diffusion-5")
    val isV45: Boolean get() = model.startsWith("nai-diffusion-4-5")

    /** V5 首发没有精准参考。**不要**把这个挂到 isV4Plus 上：结构化提示词与
     *  Director Reference 是相互独立的能力。 */
    val supportsPreciseReference: Boolean get() = isV45
    val supportsVibeTransfer: Boolean get() = !isV5
    val supportsNoiseScheduleControl: Boolean get() = !isV5
    val supportsVariety: Boolean get() = !isV5

    val maxCharacterPrompts: Int get() = if (isV5) 32 else if (isV4Plus) 6 else 0

    /**
     * 修掉来自旧版本、导入的元数据或存档工程的非法值。
     * 参考实现里有一句很关键的注释：持久化 JSON 以前会绕过 UI 约束，
     * 于是坏请求能一直活到用户清空 App 数据为止。所以任何进入请求的参数都必须先过这里。
     */
    fun normalized(allowInpaintModel: Boolean = false): GenerateParams {
        val supportedModels = (NaiCatalog.models.map { it.value } +
            if (allowInpaintModel) NaiCatalog.inpaintModels.map { it.value } else emptyList()).toSet()
        val supportedSamplers = NaiCatalog.samplers.map { it.value }.toSet()
        val supportedSchedules = NaiCatalog.noiseSchedules.map { it.value }.toSet()

        val normalizedModel = if (supportedModels.contains(model)) model else NaiModelId.V5_FULL
        val normalizedQuality = when {
            qualityPreset == "none" -> "none"
            qualityPreset == "light" && normalizedModel.startsWith("nai-diffusion-5") -> "light"
            else -> "standard"
        }
        val (fittedWidth, fittedHeight) = fitNaiImageSize(width, height, 832, 1216)

        return copy(
            model = normalizedModel,
            width = fittedWidth,
            height = fittedHeight,
            steps = steps.coerceIn(1, 50),
            cfgScale = finiteClamp(cfgScale, 0.0, 10.0, 6.0),
            cfgRescale = finiteClamp(cfgRescale, 0.0, 1.0, 0.0),
            sampler = if (supportedSamplers.contains(sampler)) sampler else "k_euler_ancestral",
            noiseSchedule = if (supportedSchedules.contains(noiseSchedule)) noiseSchedule else "karras",
            seed = seed.coerceIn(0L, 0xFFFFFFFFL),
            seedMode = if (seedMode == "fixed") "fixed" else "random",
            ucPreset = ucPreset.coerceIn(0, 3),
            qualityPreset = normalizedQuality,
            qualityToggle = normalizedQuality != "none",
            transparentBackground = normalizedModel.startsWith("nai-diffusion-5") && transparentBackground,
            smeaDyn = smea && smeaDyn,
        )
    }
}

internal fun finiteClamp(value: Double, minimum: Double, maximum: Double, fallback: Double): Double =
    if (!value.isFinite()) fallback else value.coerceIn(minimum, maximum)

// ---------------------------------------------------------------------------
// 提示词文本处理（质量标签 / UC 预设 / 合并）
// ---------------------------------------------------------------------------

object NaiText {

    /** 去掉 `-inpainting` 后缀，拿到基础模型 ID。 */
    fun baseModel(model: String): String =
        if (model.endsWith("-inpainting")) model.substring(0, model.length - "-inpainting".length) else model

    /** 按逗号合并两段提示词，去重（忽略大小写），保持顺序。 */
    fun merge(a: String, b: String): String {
        val seen = LinkedHashSet<String>()
        val result = ArrayList<String>()
        for (segment in listOf(a, b)) {
            for (part in segment.split(',').map { it.trim() }) {
                if (part.isEmpty()) continue
                if (seen.add(part.lowercase())) result.add(part)
            }
        }
        return result.joinToString(", ")
    }

    private val TEXT_DIRECTIVE = Regex("(?:^|[\\s,;|])Text\\s*:\\s*\\S", RegexOption.IGNORE_CASE)

    /**
     * 质量标签。`preset` 为 "none" 时返回空串。
     * 提示词里已经有 `Text:` 指令时，把 `no text` 这条去掉 —— 否则会和画面内文字需求打架。
     */
    fun qualityTags(model: String, preset: String = "standard", positivePrompt: String = ""): String {
        if (preset == "none") return ""

        val base = baseModel(model)
        var tags = if (preset == "light" && base.startsWith("nai-diffusion-5")) {
            "very aesthetic, amazing quality, no text"
        } else {
            when (base) {
                "nai-diffusion-5-full", "nai-diffusion-5-curated",
                "nai-diffusion-4-5-full",
                -> "very aesthetic, masterpiece, no text"

                "nai-diffusion-4-5-curated" ->
                    "very aesthetic, masterpiece, no text, -0.8::feet::, rating:general"

                "nai-diffusion-4-full" -> "no text, best quality, very aesthetic, absurdres"
                "nai-diffusion-4-curated" -> "rating:general, best quality, very aesthetic, absurdres"
                "nai-diffusion-3" -> "best quality, amazing quality, very aesthetic, absurdres"
                else -> ""
            }
        }

        if (TEXT_DIRECTIVE.containsMatchIn(positivePrompt)) {
            tags = tags.split(',')
                .map { it.trim() }
                .filter { it.lowercase() != "no text" }
                .joinToString(", ")
        }
        return tags
    }

    /**
     * UC 预设文本。
     * 注意 V5 被映射到 V4.5 的文案 —— 这是参考实现的既定行为，不是笔误。
     */
    fun ucPresetText(model: String, preset: Int): String {
        if (preset == 3) return ""

        val raw = baseModel(model)
        val normalized = when (raw) {
            "nai-diffusion-5-full" -> "nai-diffusion-4-5-full"
            "nai-diffusion-5-curated" -> "nai-diffusion-4-5-curated"
            else -> raw
        }

        if (preset == 2) {
            return when (normalized) {
                "nai-diffusion-4-5-full" ->
                    "lowres, artistic error, film grain, scan artifacts, worst quality, bad quality, " +
                        "jpeg artifacts, very displeasing, chromatic aberration, dithering, halftone, " +
                        "screentone, multiple views, logo, too many watermarks, negative space, blank page, " +
                        "@_@, mismatched pupils, glowing eyes, bad anatomy"

                "nai-diffusion-4-5-curated" ->
                    "blurry, lowres, upscaled, artistic error, film grain, scan artifacts, bad anatomy, " +
                        "bad hands, worst quality, bad quality, jpeg artifacts, very displeasing, " +
                        "chromatic aberration, halftone, multiple views, logo, too many watermarks, " +
                        "@_@, mismatched pupils, glowing eyes, negative space, blank page"

                "nai-diffusion-3" ->
                    "lowres, {bad}, error, fewer, extra, missing, worst quality, jpeg artifacts, bad quality, " +
                        "watermark, unfinished, displeasing, chromatic aberration, signature, extra digits, " +
                        "artistic error, username, scan, [abstract], bad anatomy, bad hands, @_@, " +
                        "mismatched pupils, heart-shaped pupils, glowing eyes"

                else -> ""
            }
        }

        val heavy = preset == 0
        return when (normalized) {
            "nai-diffusion-4-5-full" -> if (heavy) {
                "lowres, artistic error, film grain, scan artifacts, worst quality, bad quality, " +
                    "jpeg artifacts, very displeasing, chromatic aberration, dithering, halftone, " +
                    "screentone, multiple views, logo, too many watermarks, negative space, blank page"
            } else {
                "lowres, artistic error, scan artifacts, worst quality, bad quality, jpeg artifacts, " +
                    "multiple views, very displeasing, too many watermarks, negative space, blank page"
            }

            "nai-diffusion-4-5-curated" -> if (heavy) {
                "blurry, lowres, upscaled, artistic error, film grain, scan artifacts, worst quality, " +
                    "bad quality, jpeg artifacts, very displeasing, chromatic aberration, halftone, " +
                    "multiple views, logo, too many watermarks, negative space, blank page"
            } else {
                "blurry, lowres, upscaled, artistic error, scan artifacts, jpeg artifacts, logo, " +
                    "too many watermarks, negative space, blank page"
            }

            "nai-diffusion-4-full" -> if (heavy) {
                "blurry, lowres, error, film grain, scan artifacts, worst quality, bad quality, " +
                    "jpeg artifacts, very displeasing, chromatic aberration, multiple views, logo, " +
                    "too many watermarks"
            } else {
                "blurry, lowres, error, worst quality, bad quality, jpeg artifacts, very displeasing"
            }

            "nai-diffusion-4-curated" -> if (heavy) {
                "blurry, lowres, error, film grain, scan artifacts, worst quality, bad quality, " +
                    "jpeg artifacts, very displeasing, chromatic aberration, logo, dated, signature, " +
                    "multiple views, gigantic breasts"
            } else {
                "blurry, lowres, error, worst quality, bad quality, jpeg artifacts, very displeasing, " +
                    "logo, dated, signature"
            }

            "nai-diffusion-3" -> if (heavy) {
                "lowres, {bad}, error, fewer, extra, missing, worst quality, jpeg artifacts, bad quality, " +
                    "watermark, unfinished, displeasing, chromatic aberration, signature, extra digits, " +
                    "artistic error, username, scan, [abstract]"
            } else {
                "lowres, jpeg artifacts, worst quality, watermark, blurry, very displeasing"
            }

            else -> ""
        }
    }
}
