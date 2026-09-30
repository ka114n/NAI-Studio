package com.kallan.naistudio.models

import org.json.JSONArray
import org.json.JSONObject

/**
 * 「画师超市」—— 源自 **法典图鉴**（<https://novelai.quicktagcloud.com>，开源项目
 * `AgIzT/NovelAI-Tag`，代码 MIT）的 NovelAI 画师/画风提示词图鉴。
 *
 * ## 数据是怎么组织的（都是实测出来的，不是猜的）
 *
 * ```text
 * GET  {SITE}/data-source.json          → { baseUrl, pointer:"current.json", remoteHosts[] }
 * GET  {baseUrl}/current.json           → { release:"r-<20位hex>", publishedAt, ... }
 * GET  {baseUrl}/releases/{release}/{path}          ← 数据正文（R2 直连）
 *      {SITE}/data/releases/{release}/{path}        ← 同源 Pages 代理（R2 不可达时）
 *      {SITE}/data/{path}                           ← 便利路径，永远指向当前 release
 * GET  {mediaBase}/{images|originals}/{codexId}/{file}?v={assetRev}    ← 缩略图/原图
 * ```
 *
 * 关键文件：
 *  · `codexes.json` —— 全部法典的摘要（13 本，含 2 本 NSFW 与 1 本外链）
 *  · `<codexId>.json` —— 一本法典的全部词条 + 目录树
 *  · `media.json` —— 图片基址配置（176 字节）
 *
 * ## 为什么不做服务端搜索
 *
 * 站点是**整本 JSON 一次性拉下来、本地筛**。< 3 MB 的文本在手机上完全放得下，
 * 换来的是搜索**零延迟**、可以离线翻、也不用给对方服务器压力。
 * 这跟角色图鉴（AnimaDex 是服务端分页 + facets）是两种路子，别沿用那边的分页做法。
 *
 * 本对象是**纯逻辑、不联网**，所以能写 JVM 单测。
 */
object TagCodexProtocol {

    /** 站点根（便利路径与页面都在这个域下）。 */
    const val SITE = "https://novelai.quicktagcloud.com"

    /** 数据源配置文件（同源）。 */
    const val CONFIG_PATH = "data-source.json"

    /** 同源代理前缀：`{SITE}/data/{path}` 永远指向当前 release。 */
    const val PROXY_PREFIX = "$SITE/data"

    /** release 指针文件名。 */
    const val POINTER = "current.json"

    /** release id 的形状（站长自己也是这么校验的，与它一致）。 */
    private val RELEASE_RE = Regex("^r-[0-9a-f]{20}$")

    /** 默认打开哪一本（用户的入口就是这本）。 */
    const val DEFAULT_CODEX = "artist_nai5_personal"

    /** 法典图的授权提示：代码 MIT，但**数据汇编不许再分发**，所以只能在线取、本地缓存。 */
    const val LICENSE_NOTE =
        "词条与配图来自「法典图鉴」（AgIzT/NovelAI-Tag），代码 MIT；" +
            "词条原文归各原编纂者，数据汇编未经许可不得再分发。本工具只在线读取并本地缓存，不打包分发。"

    // ------------------------------------------------------------------ URL

    fun configUrl(): String = "$SITE/$CONFIG_PATH"

    fun pointerUrl(baseUrl: String): String = "${trimSlash(baseUrl)}/$POINTER"

    /** R2 直连：`{baseUrl}/releases/{release}/{path}`。 */
    fun releaseUrl(baseUrl: String, release: String, path: String): String =
        "${trimSlash(baseUrl)}/releases/$release/${encodePath(path)}"

    /** 同源代理：`{SITE}/data/releases/{release}/{path}`（R2 不可达时的同一份不可变数据）。 */
    fun proxyReleaseUrl(release: String, path: String): String =
        "$PROXY_PREFIX/releases/$release/${encodePath(path)}"

    /** 便利路径：`{SITE}/data/{path}`。永远指向当前 release，不需要先拿指针。 */
    fun proxyUrl(path: String): String = "$PROXY_PREFIX/${encodePath(path)}"

    /**
     * 词条图片地址。
     *
     * 站点 `media.js` 的算法（依据）：`[prefix || images/originals] / [codexId] / file`，
     * 再拼 `?v={assetRev}`。注意路径里是**法典 id**，不是 release —— 图片不随 release 变版本。
     */
    fun imageUrl(
        media: TagCodexMedia,
        codexId: String,
        file: String,
        assetRev: String = "",
        original: Boolean = false,
    ): String {
        if (file.isBlank()) return ""
        if (isAbsoluteUrl(file)) return withRev(file, assetRev)
        val prefix = if (original) media.originalPrefix else media.imagePrefix
        val path = listOf(prefix, codexId, file)
            .filter { it.isNotBlank() }
            .joinToString("/") { encodeSegment(it) }
        val base = trimSlash(media.baseUrl)
        return withRev(if (base.isEmpty()) path else "$base/$path", assetRev)
    }

    fun isAbsoluteUrl(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:")

    private fun withRev(url: String, rev: String): String {
        if (rev.isBlank()) return url
        val sep = if (url.contains('?')) '&' else '?'
        return "$url$sep" + "v=" + encodeSegment(rev)
    }

    private fun trimSlash(value: String): String = value.trimEnd('/')

    /** 站点 `cleanRelativePath` 的做法：按 `/` 分段分别转义，段内的 `/` 不会被吃掉。 */
    private fun encodePath(path: String): String =
        path.split('/').joinToString("/") { encodeSegment(it) }

    private fun encodeSegment(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    // ------------------------------------------------------------------ 解析

    /** `data-source.json` → R2 数据基址。拿不到就用 null，调用方退回便利路径。 */
    fun parseConfig(json: JSONObject?): String? {
        if (json == null) return null
        if (json.optInt("schemaVersion", 0) != 1) return null
        val base = json.optString("baseUrl").trim()
        if (!base.startsWith("https://")) return null
        return trimSlash(base)
    }

    /** `current.json` → release id；形状不对返回 null。 */
    fun parseRelease(json: JSONObject?): String? {
        val release = json?.optString("release").orEmpty()
        return release.takeIf { RELEASE_RE.matches(it) }
    }

    fun parseMedia(json: JSONObject?): TagCodexMedia {
        if (json == null) return TagCodexMedia()
        return TagCodexMedia(
            baseUrl = json.optString("baseUrl").trim(),
            imagePrefix = json.optString("imagePrefix").ifBlank { "images" },
            originalPrefix = json.optString("originalPrefix").ifBlank { "originals" },
        )
    }

    /**
     * `codexes.json` → 法典摘要列表。
     *
     * **默认过滤掉两类**：
     *  · `nsfw: true` —— 用户 2026-09-17 要求做成**开关**（[includeNsfw] = true 才列出来）；
     *  · 带 `dataUrl` 的外链法典 —— 数据不在本 release 里，取法完全不同（要另配 assetBaseUrl）。
     */
    fun parseCodexes(array: JSONArray?, includeNsfw: Boolean = false): List<TagCodexSummary> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").trim()
            if (id.isEmpty()) return@mapNotNull null
            val nsfw = item.optBoolean("nsfw", false)
            // NSFW 本默认不列；开关打开才列出来（下面 summary 里会带上 nsfw 标记，界面可以标一下）
            if (nsfw && !includeNsfw) return@mapNotNull null
            if (item.optString("dataUrl").isNotBlank()) return@mapNotNull null
            TagCodexSummary(
                id = id,
                title = item.optString("title").ifBlank { id },
                type = item.optString("type"),
                version = item.optString("version"),
                author = item.optString("author"),
                entryCount = item.optInt("entryCount"),
                nsfw = nsfw,
                external = false,
            )
        }
    }

    private fun parseTree(array: JSONArray?): List<TagCodexNode> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").trim()
            if (name.isEmpty()) return@mapNotNull null
            TagCodexNode(
                name = name,
                count = item.optInt("count"),
                children = parseTree(item.optJSONArray("children")),
            )
        }
    }

    private fun parseImages(array: JSONArray?): List<TagCodexImage> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val path = item.optString("path").trim()
            if (path.isEmpty()) return@mapNotNull null
            TagCodexImage(path = path, original = item.optString("original").trim())
        }
    }

    private fun parseStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            array.optString(index).trim().takeIf { it.isNotEmpty() }
        }
    }

    /**
     * **角色词**（数据里的 `characterPrompts` ✓，第 ㊾ 批）。
     *
     * 取不到就是**空表** ✓ —— 画师词典那本没有这个字段 ✓（实测 0 次 ✓），
     * 界面据此决定"人物"那颗按钮出不出 ✓。
     *
     * ⚠️ 一条都没有 `prompt` 的也**不算数** ✗（`label` 有、正文空 ⇒ 复制出来是空串 ✗）。
     */
    private fun parseCharacters(array: JSONArray?): List<TagCodexCharacter> {
        if (array == null) return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val prompt = item.optString("prompt").trim()
            if (prompt.isEmpty()) return@mapNotNull null
            TagCodexCharacter(
                label = item.optString("label").trim(),
                prompt = prompt,
                negative = item.optString("negative").trim(),
            )
        }
    }

    /**
     * 一本法典的正文。
     *
     * `summary` 用 `codexes.json` 里那一份（标题、作者之类都更全），
     * 但若正文自带 `title` 之类也接受 —— 独立取一本 JSON 时也能配上。
     */
    fun parseDoc(
        json: JSONObject,
        summary: TagCodexSummary,
        media: TagCodexMedia,
    ): TagCodexDoc {
        val entries = mutableListOf<TagCodexEntry>()
        val array = json.optJSONArray("entries")
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                if (id.isEmpty()) continue
                val image = item.optString("image").trim()
                entries.add(
                    TagCodexEntry(
                        id = id,
                        title = item.optString("title").trim().ifBlank { id },
                        tags = item.optString("tags").trim(),
                        path = parseStrings(item.optJSONArray("path")),
                        rating = item.optString("rating").trim(),
                        isNew = item.optBoolean("isNew", false),
                        updateBatches = parseStrings(item.optJSONArray("updateBatches")),
                        image = image,
                        original = item.optString("original").trim(),
                        hasOriginal = item.optString("original").isNotBlank(),
                        width = item.optInt("imageWidth"),
                        height = item.optInt("imageHeight"),
                        assetRev = item.optString("assetRev").trim(),
                        // ⚠️ 第 ㊿g 批：图片目录 id ✓ —— 不是每本都有 ✓，空了就回落法典 id ✓
                        assetCodexId = item.optString("assetCodexId").trim(),
                        images = parseImages(item.optJSONArray("images")).ifEmpty {
                            // 老数据/单图词条只有 image/original 两个字段
                            if (image.isEmpty()) {
                                emptyList()
                            } else {
                                listOf(TagCodexImage(image, item.optString("original").trim()))
                            }
                        },
                        // ⚠️ 第 ㊾ 批：这两个字段**只有 `codex` / `pack` 那几本有** ✗
                        //（画师词典实测一个字都没有 ✓）⇒ 取不到就是空 ✓，界面据此决定出不出按钮 ✓。
                        negative = item.optString("negative").trim(),
                        characters = parseCharacters(item.optJSONArray("characterPrompts")),
                    ),
                )
            }
        }

        val updateFilters = mutableListOf<Pair<String, String>>()
        json.optJSONArray("updateFilters")?.let { list ->
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                if (id.isEmpty()) continue
                updateFilters.add(id to item.optString("label").ifBlank { id })
            }
        }

        return TagCodexDoc(
            summary = summary.copy(
                title = json.optString("title").trim().ifBlank { summary.title },
                author = json.optString("author").trim().ifBlank { summary.author },
                version = json.optString("version").trim().ifBlank { summary.version },
                entryCount = entries.size,
            ),
            tree = parseTree(json.optJSONArray("tree")),
            entries = entries,
            updateFilters = updateFilters,
            newFilterLabel = json.optString("newFilterLabel").trim(),
            media = media,
        )
    }
}

// ---------------------------------------------------------------------- 数据

/** 一本法典的摘要（`codexes.json` 里的一条）。 */
data class TagCodexSummary(
    val id: String,
    val title: String,
    val type: String = "",
    val version: String = "",
    val author: String = "",
    val entryCount: Int = 0,
    val nsfw: Boolean = false,
    val external: Boolean = false,
)

/** 目录树节点（书名 → 作者/分组 → 具体来源）。 */
data class TagCodexNode(val name: String, val count: Int, val children: List<TagCodexNode>)

/** 词条下的一张图（有的一格挂多张例图）。 */
data class TagCodexImage(val path: String, val original: String)

/**
 * **一条角色词**（第 ㊾ 批 2026-09-21 加 ✓）—— 来自数据里的 `characterPrompts` ✓。
 *
 * ⚠️ **只有 `codex` / `pack` 那几本有它** ✗ —— 画师词典（`artist_nai5_personal` ✓）
 * 和数据里**一个字都没有** ✓（实测：`characterPrompts` 出现 0 次 ✓）。
 * 所以界面上那两颗按钮要**按实际有没有来出** ✓，别对着一本没数据的法典画死按钮 ✗。
 *
 * @param label 数据里的槽名（`char1` / `char2` … ✓）—— **复制时要去掉它** ✗
 *  （原网页 `copy.js` 的 `entryPromptText` 就是这么做的 ✓：最初那条反馈要去掉的
 *   正是 `char1：` 这个垃圾 token ✓，而不是 girl/blush 这些合法描述词 ✓）。
 * @param prompt 这个角色的**正面**词 ✓
 * @param negative 这个角色的**负面**词 ✓（原网页：**绝不混进正面串** ✗ —— 那是"不要什么"✓）
 */
data class TagCodexCharacter(
    val label: String = "",
    val prompt: String = "",
    val negative: String = "",
)

/**
 * 一条词条。`tags` 就是**可直接用的提示词串**。
 *
 * ## 三个提示词槽（第 ㊾ 批 ✓ —— 与原网页 `assets/app/copy.js` 对齐 ✓）
 *
 * 原网页那一页可以**分开复制**三样 ✓，我们照做 ✓：
 *  · **正面** = [tags] ✓（数据里的 `tags`，原网页 `entryPromptText` 的第一段 ✓）；
 *  · **人物** = [characters] 里每个 `.prompt` ✓（**去掉 `char1：` 标记**✓）；
 *  · **负面** = [negative] ✓（原网页是"再复制负面"那一步 ✓，**绝不并进正面** ✗）。
 */
data class TagCodexEntry(
    val id: String,
    val title: String,
    val tags: String,
    val path: List<String> = emptyList(),
    val rating: String = "",
    val isNew: Boolean = false,
    val updateBatches: List<String> = emptyList(),
    val image: String = "",
    val original: String = "",
    val hasOriginal: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
    val assetRev: String = "",
    /**
     * ⚠️ **图片所在的目录 id** ✓（数据字段 `assetCodexId` ✓）——
     * **可能与本法典的 id 不同** ✓，为空时回落到法典 id ✓。
     *
     * ## 第 ㊿g 批（2026-09-22）修：用户报「换到别的目录图加载不出来」的真因
     *
     * 用户给了站点链接 `?c=artist_nai45_personal` ⇒ 我按**法典 id** 拼图片路径 ✓
     *（`{mediaBase}/images/{codexId}/{file}` ✓，站点 `media.js` 的写法 ✓）——
     * **但那对本里的 2449 条是 404** ✗。实测：
     *
     * ```
     * images/artist_nai45_personal/artist_300_0001.jpg  → 404 ✗
     * images/artist_300/artist_300_0001.jpg             → 200 ✓   ← 数据里的 assetCodexId
     * ```
     *
     * 这本 5468 条里 **2449 条**带 `assetCodexId` ✓，指向 **4 个**不同目录 ✓
     *（`artist_300` / `artist_45_collection` / `artist_nai45_strings` / `mengshen_pack` ✓）；
     * ⚠️ 更狠的是 **`nai45_community_pack` 全本 6855 条都带** ✓
     *（`mengshen_pack` / `community_ai_misc` ✓）⇒ **那一本的图整本都是裂的** ✗。
     * 其余 7 本一条都没有 ✓（回落法典 id ✓，行为不变 ✓）。
     */
    val assetCodexId: String = "",
    val images: List<TagCodexImage> = emptyList(),
    /** 负面提示词 ✓（数据里的 `negative` ✓；没有就是空串 ✓）。 */
    val negative: String = "",
    /** 人物提示词（每个角色一条 ✓；数据里没有就是**空表** ✓）。 */
    val characters: List<TagCodexCharacter> = emptyList(),
) {

    /**
     * **人物提示词那一路的复制文本** = 各角色 `prompt` 用换行拼起来 ✓。
     *
     * ⚠️ **去掉 `char1:` 标记** ✗（用户 2026-09-21 口径 + 原网页 `copy.js` 同口径 ✓）——
     * 那个标记在 SD 里是垃圾 token ✓，而里面那些词（girl / blush …）完全合法 ✓。
     * 想按角色分槽精确填的，看详情里**逐个角色**那一列 ✓（那里保留槽名 ✓）。
     */
    val characterPromptText: String
        get() = characters.map { it.prompt.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    /** 有正面可以复制吗 ✓。 */
    val hasPositive: Boolean get() = tags.trim().isNotEmpty()

    /** 有人物词可以复制吗 ✓。 */
    val hasCharacters: Boolean get() = characterPromptText.isNotEmpty()

    /** 有负面可以复制吗 ✓。 */
    val hasNegative: Boolean get() = negative.trim().isNotEmpty()
}


/** 图片基址配置（`media.json`）。 */
data class TagCodexMedia(
    val baseUrl: String = "https://assets.quicktagcloud.com",
    val imagePrefix: String = "images",
    val originalPrefix: String = "originals",
)

/** 一整本法典。 */
data class TagCodexDoc(
    val summary: TagCodexSummary,
    val tree: List<TagCodexNode>,
    val entries: List<TagCodexEntry>,
    val updateFilters: List<Pair<String, String>> = emptyList(),
    val newFilterLabel: String = "",
    val media: TagCodexMedia = TagCodexMedia(),
)

// ---------------------------------------------------------------------- 检索

/**
 * 站点的搜索语法（README 明写）：**空格 = 同时满足，双引号 = 完整短语，`-词` = 排除**。
 *
 * 这里照做，而不是简单的 `contains` —— 2688 条里靠"画师名的一部分"找，
 * 不区分"同时含两个词"和"含其中任意一个"就很难找。
 */
object TagCodexQuery {

    /** 把查询串拆成 (必须全部命中, 必须全部不命中)。纯函数，好单测。 */
    fun parse(raw: String): Pair<List<String>, List<String>> {
        val must = mutableListOf<String>()
        val mustNot = mutableListOf<String>()
        val text = raw.trim()
        if (text.isEmpty()) return must to mustNot

        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        text.forEach { ch ->
            when {
                ch == '"' -> {
                    if (quoted) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                    quoted = !quoted
                }
                ch.isWhitespace() && !quoted -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) tokens.add(current.toString())

        tokens.forEach { token ->
            if (token.isEmpty()) return@forEach
            if (token.startsWith("-") && token.length > 1) {
                mustNot.add(token.substring(1).lowercase())
            } else {
                must.add(token.lowercase())
            }
        }
        return must to mustNot
    }

    /** 一条词条是否命中查询串。检索范围 = 标题 + 提示词串 + 目录路径。 */
    fun matches(entry: TagCodexEntry, query: String): Boolean {
        val (must, mustNot) = parse(query)
        if (must.isEmpty() && mustNot.isEmpty()) return true
        val haystack = buildString {
            append(entry.title).append('\n')
            append(entry.tags).append('\n')
            append(entry.path.joinToString(" / "))
        }.lowercase()
        if (must.any { !haystack.contains(it) }) return false
        if (mustNot.any { haystack.contains(it) }) return false
        return true
    }

    /**
     * 全部筛选条件一次过。
     *
     * @param pathPrefix 目录树前缀（点目录节点时逐级下钻）；空 = 不过滤
     * @param updateId   只看这一次更新（`updateBatches` 里的 id）
     * @param newOnly    只看 `isNew`
     */
    fun filter(
        entries: List<TagCodexEntry>,
        query: String = "",
        pathPrefix: List<String> = emptyList(),
        updateId: String = "",
        newOnly: Boolean = false,
    ): List<TagCodexEntry> = entries.filter { entry ->
        if (newOnly && !entry.isNew) return@filter false
        if (updateId.isNotEmpty() && updateId !in entry.updateBatches) return@filter false
        if (pathPrefix.isNotEmpty()) {
            val path = entry.path
            if (path.size < pathPrefix.size) return@filter false
            pathPrefix.forEachIndexed { index, part ->
                if (path[index] != part) return@filter false
            }
        }
        matches(entry, query)
    }
}
