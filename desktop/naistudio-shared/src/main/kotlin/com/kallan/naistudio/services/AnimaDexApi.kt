package com.kallan.naistudio.services

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * 角色图鉴（AnimaDex）· 接口层。
 *
 * 数据来自 <https://animadex.net>（开源项目 `zetaneko/AnimaDex`，Flask + SQLite）。
 * 接口全公开、不需要登录、也不需要 Key —— **直接用手机请求即可**（网页版当初需要本地代理
 * 只是因为浏览器跨域，App 没这个问题）。
 *
 * 两个接口（参数名是从站点自己的 `app.js` 里挖出来的，实测有效）：
 * ```text
 * GET https://animadex.net/api/characters/facets
 *   → { total, facets: { copyright|hair_color|hair_length|eye_color|gender|character:
 *                        { label, total, values:[{value,label,count}] } } }
 * GET https://animadex.net/api/characters/search?q=&sort=&page=&<facet>=<value>
 *   → { total, page, page_size:36, pages, results:[{ slug,name,copyright,copyright_name,
 *        trigger,tags[],count,url,thumb_url,img_url,has_image,rating{up,down},fav_count }] }
 * ```
 * 注意：`loras=1` 在服务端是**筛选**（只看有 LoRA 的角色），本项目**不用 LoRA**，所以一律不带。
 */
data class AnimaCharacter(
    val slug: String,
    val name: String,
    /** 作品 slug（`vocaloid`）——**筛选与查询都用它**。 */
    val copyrightSlug: String,
    /** 作品名（"Genshin Impact"）。 */
    val copyrightName: String,
    /** **可直接用的提示词串**，例如 `ganyu (genshin impact), genshin impact`。 */
    val trigger: String,
    val tags: List<String>,
    /** 这个角色的作品数（服务端叫 count）。 */
    val count: Int,
    /** 点赞数（服务端在 `rating.up` 里）。 */
    val favCount: Int,
    val danbooruUrl: String,
    val thumbUrl: String,
    val imgUrl: String,
    val hasImage: Boolean,
)

data class AnimaFacetValue(val value: String, val label: String, val count: Int)

data class AnimaFacet(val key: String, val label: String, val values: List<AnimaFacetValue>)

data class AnimaSearchPage(
    val total: Int,
    val page: Int,
    val pages: Int,
    val results: List<AnimaCharacter>,
)

/** 角色图鉴接口的错误（消息直接给用户看，中文）。 */
class AnimaDexException(message: String) : Exception(message)

/**
 * **作品（版权）模式**的一条：AnimaDex 的 `copyrights` 视图。
 * 缩略图是每个作品的 2×2 拼贴，点进去就是带 `copyright=<slug>` 筛选的角色列表。
 */
data class AnimaSeries(
    val slug: String,
    val name: String,
    /** 这个作品下有多少角色。 */
    val count: Int,
    val thumbUrl: String,
    val hasImage: Boolean,
)

/** 纯逻辑：拼 URL + 解析 JSON（**不联网**，所以能 JVM 单测）。 */
object AnimaDexProtocol {

    const val BASE_URL = "https://animadex.net"

    /** 服务端每页固定 36 条。 */
    const val PAGE_SIZE = 36

    /**
     * 排序合法值（服务端校验；`score` 只对 artists 模式有效，这里不用）。
     * 默认 `count` = 图片数。
     */
    val SORTS = listOf("count", "liked", "favourited", "recent", "az", "random")

    /** 界面上按这个顺序显示筛选维度（去掉 character：那是"角色名"本身，用搜索框更直接）。 */
    val FACET_KEYS = listOf("copyright", "hair_color", "hair_length", "eye_color", "gender")

    fun searchUrl(
        query: String,
        sort: String,
        page: Int,
        filters: Map<String, String>,
        seed: Int? = null,
    ): String {
        val parts = mutableListOf<String>()
        if (query.isNotBlank()) parts += "q=" + encode(query.trim())
        parts += "sort=" + encode(if (sort in SORTS) sort else "count")
        parts += "page=${page.coerceAtLeast(1)}"
        if (sort == "random" && seed != null) parts += "seed=$seed"
        filters.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                parts += encode(key) + "=" + encode(value)
            }
        }
        return "$BASE_URL/api/characters/search?" + parts.joinToString("&")
    }

    fun facetsUrl(): String = "$BASE_URL/api/characters/facets"

    /**
     * **作品（版权）视图**的列表：`/api/copyrights/search`
     * （这个视图**没有 facets**，实测 `/api/copyrights/facets` 返回空对象）。
     */
    fun seriesUrl(query: String, sort: String, page: Int): String {
        val parts = mutableListOf<String>()
        if (query.isNotBlank()) parts += "q=" + encode(query.trim())
        parts += "sort=" + encode(if (sort in SORTS) sort else "count")
        parts += "page=${page.coerceAtLeast(1)}"
        return "$BASE_URL/api/copyrights/search?" + parts.joinToString("&")
    }

    fun parseSeries(json: JSONObject): Pair<Int, List<AnimaSeries>> {
        val list = mutableListOf<AnimaSeries>()
        json.optJSONArray("results")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                list.add(
                    AnimaSeries(
                        slug = item.optString("slug"),
                        name = item.optString("name"),
                        count = item.optInt("count"),
                        thumbUrl = item.optString("thumb_url"),
                        hasImage = item.optBoolean("has_image", false),
                    ),
                )
            }
        }
        return json.optInt("total", list.size) to list
    }

    /** `URLEncoder` 会把空格写成 `+`，服务端按 URL 解码，统一成 `%20` 更稳。 */
    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun parseSearch(json: JSONObject): AnimaSearchPage {
        val results = mutableListOf<AnimaCharacter>()
        json.optJSONArray("results")?.let { array ->
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val tags = mutableListOf<String>()
                item.optJSONArray("tags")?.let { list ->
                    for (slot in 0 until list.length()) {
                        list.optString(slot).takeIf { it.isNotBlank() }?.let { tags.add(it) }
                    }
                }
                results.add(
                    AnimaCharacter(
                        slug = item.optString("slug"),
                        name = item.optString("name"),
                        copyrightSlug = item.optString("copyright"),
                        copyrightName = item.optString("copyright_name")
                            .ifBlank { item.optString("copyright") },
                        trigger = item.optString("trigger"),
                        tags = tags,
                        count = item.optInt("count"),
                        favCount = item.optJSONObject("rating")?.optInt("up") ?: 0,
                        danbooruUrl = item.optString("url"),
                        thumbUrl = item.optString("thumb_url"),
                        imgUrl = item.optString("img_url"),
                        hasImage = item.optBoolean("has_image", false),
                    ),
                )
            }
        }
        return AnimaSearchPage(
            total = json.optInt("total", results.size),
            page = json.optInt("page", 1),
            pages = json.optInt("pages", 1),
            results = results,
        )
    }

    fun parseFacets(json: JSONObject): List<AnimaFacet> {
        val facets = json.optJSONObject("facets") ?: return emptyList()
        // 按 FACET_KEYS 的顺序取；万一服务端加了新维度，也把剩余的带上（放在后面）
        val keys = FACET_KEYS.filter { facets.has(it) } +
            facets.keys().asSequence().filter { it !in FACET_KEYS && it != "character" }.toList()
        return keys.mapNotNull { key ->
            val facet = facets.optJSONObject(key) ?: return@mapNotNull null
            val values = mutableListOf<AnimaFacetValue>()
            facet.optJSONArray("values")?.let { array ->
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    values.add(
                        AnimaFacetValue(
                            value = item.optString("value"),
                            label = item.optString("label").ifBlank { item.optString("value") },
                            count = item.optInt("count"),
                        ),
                    )
                }
            }
            if (values.isEmpty()) null else AnimaFacet(
                key = key,
                label = facet.optString("label").ifBlank { key },
                values = values,
            )
        }
    }
}

/** 真正发请求的那一层（阻塞式，和 `NaiApi` 一个风格，调用方放 IO 线程）。 */
class AnimaDexApi(
    private val client: OkHttpClient = defaultClient(),
) {

    fun facets(): JSONObject = get(AnimaDexProtocol.facetsUrl())

    fun search(
        query: String,
        sort: String,
        page: Int,
        filters: Map<String, String>,
        seed: Int? = null,
    ): JSONObject = get(AnimaDexProtocol.searchUrl(query, sort, page, filters, seed))

    /** 作品（版权）视图的一页。 */
    fun series(query: String, sort: String, page: Int): JSONObject =
        get(AnimaDexProtocol.seriesUrl(query, sort, page))

    private fun get(url: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            // 站点会按 UA 限流；给个明确的自定义 UA，礼貌且便于对方识别
            .header("user-agent", "NAI-Studio-Android/1.0 (AnimaDex tool)")
            .header("accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AnimaDexException("角色图鉴请求失败（HTTP ${response.code}）：${body.take(160)}")
            }
            return runCatching { JSONObject(body) }.getOrElse {
                throw AnimaDexException("角色图鉴返回的内容看不懂（不是 JSON）")
            }
        }
    }

    companion object {
        /** 连接/读取超时都短一点：这是"查一下就回来"的接口，卡住不如早报错。 */
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}

/** 小工具：把服务端返回的 JSON 数组转成字符串列表（内部用）。 */
internal fun JSONArray.toStringList(): List<String> =
    (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
