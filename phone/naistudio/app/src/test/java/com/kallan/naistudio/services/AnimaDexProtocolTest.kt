package com.kallan.naistudio.services

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 角色图鉴接口层：拼 URL 与解析 JSON（纯逻辑，不联网）。 */
class AnimaDexProtocolTest {

    private val searchSample = JSONObject(
        """
        {
          "total": 6, "page": 1, "page_size": 36, "pages": 1,
          "results": [
            {
              "slug": "ganyu_(genshin_impact)",
              "name": "Ganyu (Genshin Impact)",
              "copyright": "genshin_impact",
              "copyright_name": "Genshin Impact",
              "trigger": "ganyu (genshin impact), genshin impact",
              "tags": ["1girl", "goat horns", "blue hair"],
              "count": 17983,
              "url": "https://danbooru.donmai.us/posts?tags=ganyu_(genshin_impact)",
              "thumb_url": "https://blobs.animadex.net/Outputs/thumbs/ganyu.webp?v=1",
              "img_url": "https://blobs.animadex.net/Outputs/ganyu.png?v=1",
              "has_image": true,
              "rating": {"up": 7, "down": 0},
              "fav_count": 112
            },
            {
              "slug": "hatsune_miku", "name": "Hatsune Miku",
              "copyright": "vocaloid", "copyright_name": "Vocaloid",
              "trigger": "hatsune miku, vocaloid", "tags": [], "count": 103500,
              "has_image": false
            }
          ]
        }
        """.trimIndent(),
    )

    private val facetsSample = JSONObject(
        """
        {
          "total": 36488,
          "facets": {
            "character": { "label": "Character", "values": [{"value":"hatsune_miku","label":"Hatsune Miku","count":103500}] },
            "copyright": { "label": "Copyright", "values": [
                {"value":"original","label":"Original","count":2150},
                {"value":"vocaloid","label":"Vocaloid","count":194}] },
            "hair_color": { "label": "Hair Color", "values": [{"value":"blue hair","label":"Blue","count":2704}] },
            "gender": { "label": "Gender", "values": [{"value":"1girl","label":"Female","count":22862}] }
          }
        }
        """.trimIndent(),
    )

    @Test
    fun `search url carries query sort page and filters`() {
        val url = AnimaDexProtocol.searchUrl(
            query = "初音 miku",
            sort = "liked",
            page = 3,
            filters = mapOf("copyright" to "vocaloid", "hair_color" to "blue hair"),
        )
        assertTrue(url.startsWith("https://animadex.net/api/characters/search?"))
        assertTrue(url.contains("q=%E5%88%9D%E9%9F%B3%20miku")) // 空格必须是 %20，不是 +
        assertTrue(url.contains("sort=liked"))
        assertTrue(url.contains("page=3"))
        assertTrue(url.contains("copyright=vocaloid"))
        assertTrue(url.contains("hair_color=blue%20hair"))
        // 不做 LoRA 筛选（loras=1 在服务端是"只看有 LoRA"，本项目不用）
        assertTrue(!url.contains("loras"))
    }

    @Test
    fun `search url falls back to count sort and clamps page`() {
        val url = AnimaDexProtocol.searchUrl("", "不存在的排序", 0, emptyMap())
        assertTrue(url.contains("sort=count"))
        assertTrue(url.contains("page=1"))
        assertTrue(!url.contains("q="))
    }

    @Test
    fun `random sort carries seed`() {
        val url = AnimaDexProtocol.searchUrl("miku", "random", 1, emptyMap(), seed = 12345)
        assertTrue(url.contains("sort=random"))
        assertTrue(url.contains("seed=12345"))
    }

    @Test
    fun `parse search results`() {
        val page = AnimaDexProtocol.parseSearch(searchSample)
        assertEquals(6, page.total)
        assertEquals(1, page.pages)
        assertEquals(2, page.results.size)

        val ganyu = page.results[0]
        assertEquals("ganyu (genshin impact)", ganyu.name.lowercase())
        assertEquals("ganyu (genshin impact), genshin impact", ganyu.trigger)
        assertEquals(listOf("1girl", "goat horns", "blue hair"), ganyu.tags)
        assertEquals(17983, ganyu.count)
        assertEquals(7, ganyu.favCount)
        assertTrue(ganyu.hasImage)
        assertTrue(ganyu.danbooruUrl.startsWith("https://danbooru.donmai.us"))
        assertTrue(ganyu.thumbUrl.isNotBlank())

        // 缺字段的条目也要能读：tags 空、没有图、没有 rating
        val miku = page.results[1]
        assertEquals("Hatsune Miku", miku.name)
        assertTrue(miku.tags.isEmpty())
        assertEquals(0, miku.favCount)
        assertEquals(false, miku.hasImage)
    }

    @Test
    fun `parse facets in ui order and skip character facet`() {
        val facets = AnimaDexProtocol.parseFacets(facetsSample)
        // character 是"角色名"本身（用搜索框更直接），不进筛选界面
        assertEquals(listOf("copyright", "hair_color", "gender"), facets.map { it.key })
        val copyright = facets.first()
        assertEquals("Copyright", copyright.label)
        assertEquals(2, copyright.values.size)
        assertEquals("Original", copyright.values[0].label)
        assertEquals(2150, copyright.values[0].count)
        assertEquals("vocaloid", copyright.values[1].value)
    }

    @Test
    fun `parse tolerates empty payloads`() {
        val empty = AnimaDexProtocol.parseSearch(JSONObject("{}"))
        assertEquals(0, empty.results.size)
        assertEquals(1, empty.pages)
        assertTrue(AnimaDexProtocol.parseFacets(JSONObject("{}")).isEmpty())
        assertNull(AnimaDexProtocol.parseFacets(JSONObject("{}")).firstOrNull())
    }
}
