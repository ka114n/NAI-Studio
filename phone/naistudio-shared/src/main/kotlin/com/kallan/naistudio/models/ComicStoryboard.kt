package com.kallan.naistudio.models

import org.json.JSONArray
import org.json.JSONObject

/**
 * 「按剧情分镜」的 **LLM 输出解析器**（纯逻辑、不碰 Android，所以能写 JVM 单测）。
 *
 * LLM 输出 JSON 是**不稳定**的：爱用 ```json 代码块包起来、爱在前面写一句"好的，这是分镜："、
 * 爱在末尾补一段解释、role 有时给中文有时给英文。所以这里必须容错，
 * 而不是 `JSONObject(text)` 一扔了事。
 *
 * 解析契约与 [com.kallan.naistudio.services.PromptRules.COMIC_STORYBOARD_RULES] 一一对应，
 * **改这里要同步改提示词**。
 */
object ComicStoryboard {

    /** 分格数量的上下限（工作台的版式只支持 2~6 格）。 */
    const val MIN_PANELS = 2
    const val MAX_PANELS = 6

    /** 页数的上下限。上限是**硬护栏**：模型偶尔会把一格拆成一页，写个 200 页出来。 */
    const val MIN_PAGES = 1
    const val MAX_PAGES = 24

    /** 狂暴模式的默认页数上限（用户可以在界面上调）。 */
    const val DEFAULT_MAX_PAGES = 8

    /** 单个分格解析出来的内容。 */
    data class Panel(
        val name: String,
        /** `""` 角色 / `"scene"` 场景 / `"text"` 台词 / `"prop"` 道具。 */
        val role: String,
        val prompt: String,
    )

    /** 一页的分镜。 */
    data class Storyboard(
        val style: String,
        val panels: List<Panel>,
    )

    /** 解析失败。`message` 是给用户看的中文。 */
    class StoryboardException(message: String) : Exception(message)

    /** 合法 role 值（空串也是合法的，表示"角色"）。 */
    private val VALID_ROLES = setOf("", "scene", "text", "prop")

    /** 中文 / 近义词 → 内部值。模型经常不听话给中文，所以两种都收。 */
    private fun normalizeRole(raw: String?): String {
        val text = raw?.trim()?.lowercase().orEmpty()
        return when (text) {
            "", "character", "char", "role", "角色", "人物" -> ""
            "scene", "background", "bg", "场景", "背景" -> "scene"
            "text", "bubble", "speech", "台词", "文字", "气泡" -> "text"
            "prop", "object", "道具", "物件" -> "prop"
            else -> "" // 认不出来就当角色，不因为一个字段废掉整次生成
        }
    }

    /**
     * 从模型返回的文本里**抠出 JSON**。
     *
     * 依次尝试：去掉 Markdown 代码块 → 取第一个 `{` 到最后一个 `}` → 取第一个 `[` 到最后一个 `]`。
     * 这样"好的，这是分镜：{...} 希望有帮助"这种也能救回来。
     */
    fun extractJson(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) throw StoryboardException("模型没有返回内容")

        // 1) 去掉 ```json ... ``` / ``` ... ``` 代码块
        val fence = Regex("```(?:json|JSON)?\\s*([\\s\\S]*?)```").find(text)
        val body = fence?.groupValues?.get(1)?.trim().takeUnless { it.isNullOrEmpty() } ?: text

        val objStart = body.indexOf('{')
        val arrStart = body.indexOf('[')

        // 2) **谁先出现就以谁为准**。
        // 不能一律先找对象：模型直接给数组时，数组里每个元素也都是 `{`，
        // 按"第一个 { 到最后一个 }"抠出来会得到 `{...},{...}` 这种非法 JSON。
        if (arrStart >= 0 && (objStart < 0 || arrStart < objStart)) {
            val arrEnd = body.lastIndexOf(']')
            if (arrEnd > arrStart) return body.substring(arrStart, arrEnd + 1)
        }

        // 3) 对象
        if (objStart >= 0) {
            val objEnd = body.lastIndexOf('}')
            if (objEnd > objStart) return body.substring(objStart, objEnd + 1)
        }

        throw StoryboardException(noJsonMessage(text))
    }

    /**
     * 模型没给 JSON 时，把**它到底回了什么**写进错误里。
     *
     * 之前这里只报一句"模型的输出里找不到 JSON"，然后**把模型的原文丢掉** ——
     * 结果三种完全不同的故障（模型拒绝了 / 模型在闲聊 / 输出被截断）长成同一个样子，
     * 用户看不出、我也查不出。实测就是这么踩的：一个本该一眼判断的问题，
     * 变成了一张只能看到"失败"两个字的截图。
     *
     * 所以这里带上原文前 [NO_JSON_PREVIEW_CHARS] 字，并且认一下常见的拒绝措辞 ——
     * 拒绝是最可能的一种（剧情里有过不了审的内容时），单独给一句人话解释。
     */
    private fun noJsonMessage(raw: String): String {
        val text = raw.trim()
        val flat = text.replace(Regex("\\s+"), " ")
        val preview = flat.take(NO_JSON_PREVIEW_CHARS)
        val looksLikeRefusal = REFUSAL_MARKERS.any { text.contains(it, ignoreCase = true) }
        return buildString {
            if (looksLikeRefusal) {
                append("模型没有按要求输出 JSON，而是回了一段拒绝/说明。")
                append("多半是剧情里有它不接受的内容 —— 换个模型、或把剧情改得含蓄些再试。")
            } else {
                append("模型的输出里找不到 JSON。")
            }
            append("它实际回的是：「")
            append(preview)
            if (flat.length > preview.length) append("…")
            append("」")
        }
    }

    /** 出错时把模型原文带出来多少字（够看出是拒绝还是跑偏，又不至于刷屏）。 */
    const val NO_JSON_PREVIEW_CHARS = 80

    /**
     * 常见的拒绝/免责措辞。**只是提示，不是判定** —— 命中就往"模型拒绝了"那边猜。
     *
     * 挑的都是比较硬的词（抱歉 / 无法 / 违规 / sorry …），故意不放"不能""不便"这类
     * 在正常中文里也常见的词，免得把"跑偏"误报成"拒绝"。
     */
    private val REFUSAL_MARKERS = listOf(
        "抱歉", "对不起", "无法", "拒绝", "违规", "不符合",
        "sorry", "cannot", "can't", "unable",
    )

    /** 解析模型输出。任何不合法都以 [StoryboardException] 抛出。 */
    fun parse(raw: String): Storyboard {
        val json = extractJson(raw)
        val trimmed = json.trimStart()

        val panelsArray: JSONArray
        var style = ""

        if (trimmed.startsWith("[")) {
            // 模型直接给了个数组：没有 style，只有分格
            panelsArray = try {
                JSONArray(trimmed)
            } catch (e: Exception) {
                throw StoryboardException("模型给的 JSON 数组看不明白")
            }
        } else {
            val root = try {
                JSONObject(trimmed)
            } catch (e: Exception) {
                throw StoryboardException("模型给的 JSON 看不明白")
            }
            style = root.optString("style", "").trim()
            panelsArray = root.optJSONArray("panels")
                ?: root.optJSONArray("panel")
                ?: root.optJSONArray("shots")
                ?: throw StoryboardException("分镜 JSON 里没有 panels 数组")
        }

        if (panelsArray.length() == 0) {
            throw StoryboardException("分镜是空的（panels 里一格都没有）")
        }

        val panels = (0 until panelsArray.length()).mapNotNull { index ->
            // 容错：元素可能是对象，也可能被模型写成裸字符串
            val item = panelsArray.optJSONObject(index)
                ?: panelsArray.optString(index, "").trim().takeIf { it.isNotEmpty() }?.let {
                    return@mapNotNull Panel(name = "", role = "", prompt = it)
                }
                ?: return@mapNotNull null

            val prompt = item.optString("prompt", "")
                .ifEmpty { item.optString("positive", "") }
                .ifEmpty { item.optString("caption", "") }
                .trim()
            if (prompt.isEmpty()) return@mapNotNull null // 没提示词的格子没有意义

            Panel(
                name = item.optString("name", "").trim(),
                role = normalizeRole(item.optString("role", "")),
                prompt = prompt,
            )
        }

        if (panels.isEmpty()) {
            throw StoryboardException("分镜里每一格都没有提示词")
        }
        if (panels.size > MAX_PANELS) {
            throw StoryboardException("分镜给了 ${panels.size} 格，超过一页最多 $MAX_PANELS 格")
        }
        if (panels.size < MIN_PANELS) {
            throw StoryboardException("分镜只给了 ${panels.size} 格，一页至少需要 $MIN_PANELS 格")
        }

        return Storyboard(style = style, panels = panels)
    }

    /**
     * 按格数挑一个**默认版式**。
     *
     * 刻意优先选**不对称**的那个（4 格选「主格+三副格」而不是「田字四格」，
     * 6 格选「通栏主格+五格」而不是「两行三列」）——
     * 参考实现自己的 v5-architect 技能就写着"严禁死板的等分格子"。
     * 找不到对应格数（比如 1 格）就退回 `auto`。
     */
    fun defaultLayoutFor(panelCount: Int): String = when (panelCount) {
        2 -> "v2"
        3 -> "v3"
        4 -> "v4a"
        5 -> "v5"
        6 -> "v6a"
        else -> ComicLayout.AUTO
    }

    // -----------------------------------------------------------------------
    // 狂暴漫画模式：第一段 LLM —— 整部剧情 → 分页规划
    // -----------------------------------------------------------------------

    /**
     * 规划出来的一页。
     *
     * [layout] 一定是**合法且具体**的模板 id（不会是 `auto`）：
     * 因为这一页要单独发一次分镜请求，格数必须在这一步就定死，
     * 否则第二段模型给几格都行，页与页之间就没法控制节奏了。
     */
    data class PlannedPage(
        val summary: String,
        val layout: String,
        val panelCount: Int,
    )

    /** 整部作品的规划。 */
    data class PagePlan(
        val title: String,
        val style: String,
        val pages: List<PlannedPage>,
    )

    private fun firstString(json: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = json.optString(key, "").trim()
            if (value.isNotEmpty()) return value
        }
        return ""
    }

    /**
     * 把 `panels` 字段读成**格数**。模型有时给数字，有时直接给一个数组，
     * 两种都收（数组就取长度）。
     */
    private fun panelCountOf(json: JSONObject): Int? {
        json.optJSONArray("panels")?.let { return it.length() }
        json.optJSONArray("panel")?.let { return it.length() }
        for (key in listOf("panels", "panelCount", "panel_count", "count", "cells", "格数")) {
            if (json.has(key)) {
                val value = json.optInt(key, -1)
                if (value > 0) return value
            }
        }
        // `panel` 也可能是数字
        if (json.has("panel")) {
            val value = json.optInt("panel", -1)
            if (value > 0) return value
        }
        return null
    }

    private fun isKnownLayout(id: String): Boolean =
        ComicLayout.templates.any { it.id == id } && id != ComicLayout.AUTO

    /**
     * 解析**分页规划**（狂暴模式第一段的输出）。
     *
     * 契约见 [com.kallan.naistudio.services.PromptRules.COMIC_PAGE_PLAN_RULES]。
     * 容错原则和 [parse] 一样：模型爱包代码块、爱加前言、爱换字段名，一律能救就救。
     *
     * @param maxPages 页数硬上限（`coerceIn` 到 [MIN_PAGES]..[MAX_PAGES]）。
     */
    fun parsePagePlan(raw: String, maxPages: Int = DEFAULT_MAX_PAGES): PagePlan {
        val json = extractJson(raw)
        val trimmed = json.trimStart()

        val pagesArray: JSONArray
        var title = ""
        var style = ""

        if (trimmed.startsWith("[")) {
            pagesArray = try {
                JSONArray(trimmed)
            } catch (e: Exception) {
                throw StoryboardException("模型给的 JSON 数组看不明白")
            }
        } else {
            val root = try {
                JSONObject(trimmed)
            } catch (e: Exception) {
                throw StoryboardException("模型给的 JSON 看不明白")
            }
            title = firstString(root, "title", "name", "作品名", "标题")
            style = firstString(root, "style", "artStyle", "风格")
            pagesArray = root.optJSONArray("pages")
                ?: root.optJSONArray("page")
                ?: root.optJSONArray("chapters")
                ?: root.optJSONArray("storyboard")
                ?: root.optJSONArray("分页")
                ?: throw StoryboardException("分页 JSON 里没有 pages 数组")
        }

        if (pagesArray.length() == 0) {
            throw StoryboardException("分页结果是空的（pages 里一页都没有）")
        }

        val limit = maxPages.coerceIn(MIN_PAGES, MAX_PAGES)
        val raw_pages = (0 until pagesArray.length()).mapNotNull { index ->
            val item = pagesArray.optJSONObject(index)
                ?: pagesArray.optString(index, "").trim().takeIf { it.isNotEmpty() }?.let {
                    // 模型把一页写成了裸字符串：当成概要，格数与版式都用默认值
                    return@mapNotNull PlannedPage(summary = it, layout = "", panelCount = 0)
                }
                ?: return@mapNotNull null

            val summary = firstString(
                item, "summary", "brief", "desc", "description", "plot", "content",
                "概要", "剧情", "内容",
            )
            val layoutRaw = firstString(item, "layout", "template", "版式", "布局")
            val count = panelCountOf(item)
            PlannedPage(summary = summary, layout = layoutRaw, panelCount = count ?: 0)
        }

        if (raw_pages.isEmpty()) {
            throw StoryboardException("分页里每一页都没有内容")
        }

        val pages = raw_pages.take(limit).map { page ->
            // 版式优先：模型给了合法的模板 id 就用它（格数由版式决定）。
            // 否则把它给的格数**先夹进 2..6 再挑版式** —— 先夹后挑，`panels: 99`
            // 才会落到 6 格的版式上，而不是因为"99 不在 2..6 里"整个掉回 4 格默认值。
            // `panelCount <= 0` 表示模型根本没给格数（比如把一页写成了裸字符串），
            // 这时才用 4 格默认值。
            val clamped = page.panelCount.coerceIn(MIN_PANELS, MAX_PANELS)
            val layout = when {
                isKnownLayout(page.layout) -> page.layout
                page.panelCount > 0 ->
                    defaultLayoutFor(clamped).takeIf { isKnownLayout(it) } ?: "v4a"
                else -> "v4a"
            }
            val count = ComicLayout.panelCountOf(layout) ?: clamped
            PlannedPage(summary = page.summary, layout = layout, panelCount = count)
        }

        return PagePlan(title = title, style = style, pages = pages)
    }
}
