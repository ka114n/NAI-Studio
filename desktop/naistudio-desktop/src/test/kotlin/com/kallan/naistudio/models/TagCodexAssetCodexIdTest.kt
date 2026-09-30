package com.kallan.naistudio.models

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **图片目录 id 判据** ✓（第 ㊿g 批 2026-09-22 ✓）。
 *
 * 用户给站点链接 `?c=artist_nai45_personal` 报「图出不来」✓ —— 真因：
 * **图片目录不一定是法典 id** ✗，条目自带 `assetCodexId` ✓（站点 `media.js` 用的是它 ✓）。
 *
 * 实测（2026-09-22 拉的真实数据 ✓）：
 * ```
 * images/artist_nai45_personal/artist_300_0001.jpg  → 404 ✗
 * images/artist_300/artist_300_0001.jpg             → 200 ✓
 * ```
 */
class TagCodexAssetCodexIdTest {

    private fun entryJson(extra: String = ""): String = """
        {
          "id": "e1", "title": "t", "tags": "a",
          "image": "artist_300_0001.jpg",
          "imageWidth": 753, "imageHeight": 1100,
          "assetRev": "868c09e4f9ba0f13"
          $extra
        }
    """.trimIndent()

    private fun parseOne(json: String): TagCodexEntry {
        val doc = TagCodexProtocol.parseDoc(
            json = JSONObject("""{"entries":[${json}]}"""),
            summary = TagCodexSummary(id = "artist_nai45_personal", title = "t"),
            media = TagCodexMedia(baseUrl = "https://assets.example.com"),
        )
        return doc.entries.first()
    }

    @Test
    fun `解析出 assetCodexId`() {
        val e = parseOne(entryJson(""", "assetCodexId": "artist_300" """))
        assertEquals("artist_300", e.assetCodexId)
    }

    @Test
    fun `没有 assetCodexId 就是空串`() {
        val e = parseOne(entryJson())
        assertEquals("", e.assetCodexId)
    }

    @Test
    fun `有 assetCodexId 时图片走它`() {
        val e = parseOne(entryJson(""", "assetCodexId": "artist_300" """))
        val url = TagCodexProtocol.imageUrl(
            media = TagCodexMedia(baseUrl = "https://assets.example.com"),
            codexId = e.assetCodexId.ifBlank { "artist_nai45_personal" },
            file = e.image,
            assetRev = e.assetRev,
        )
        assertTrue("必须用条目自带的目录", url.contains("/images/artist_300/"))
        assertTrue("不能用法典 id", !url.contains("/images/artist_nai45_personal/"))
        assertTrue("要带 assetRev", url.contains("v=868c09e4f9ba0f13"))
    }

    @Test
    fun `没有 assetCodexId 时回落法典 id`() {
        val e = parseOne(entryJson())
        val url = TagCodexProtocol.imageUrl(
            media = TagCodexMedia(baseUrl = "https://assets.example.com"),
            codexId = e.assetCodexId.ifBlank { "artist_nai45_personal" },
            file = e.image,
        )
        assertTrue("回落法典 id", url.contains("/images/artist_nai45_personal/"))
    }
}
