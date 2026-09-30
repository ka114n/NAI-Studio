package com.kallan.naistudio.models

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画师超市（法典图鉴）· 协议层。
 *
 * 这里的断言**全部对着实测拿到的真实数据形状**写（release `r-bfd63742c6b690dd0e9e`，
 * 画师词典 2688 条）。字段名不是猜的：`data-source.json` / `current.json` /
 * `codexes.json` / `artist_nai5_personal.json` / `media.json` 五份都真拉下来看过。
 *
 * 钉住的三件事：
 *  1. **URL 怎么拼** —— 图片路径里是**法典 id** 不是 release，这个搞错了图全是 404；
 *  2. **哪些法典不该出现在列表里** —— NSFW 与外链本会被解析层直接剔除；
 *  3. **检索语法** —— 站点的「空格=同时满足 / 引号=短语 / -词=排除」得真的是这个语义。
 */
class TagCodexTest {

    private val media = TagCodexMedia(
        baseUrl = "https://assets.quicktagcloud.com",
        imagePrefix = "images",
        originalPrefix = "originals",
    )

    // ------------------------------------------------------------------ URL

    @Test
    fun `图片地址是 基址-前缀-法典id-文件名 再挂 assetRev`() {
        val url = TagCodexProtocol.imageUrl(
            media = media,
            codexId = "artist_nai5_personal",
            file = "artist_nai5_personal_huazhiyu_0001.jpg",
            assetRev = "aa71793a8120742f",
        )
        assertEquals(
            "https://assets.quicktagcloud.com/images/artist_nai5_personal/" +
                "artist_nai5_personal_huazhiyu_0001.jpg?v=aa71793a8120742f",
            url,
        )
    }

    @Test
    fun `原图走 originals 前缀`() {
        val url = TagCodexProtocol.imageUrl(
            media = media,
            codexId = "artist_nai5_personal",
            file = "artist_nai5_personal_huazhiyu_0001.png",
            original = true,
        )
        assertEquals(
            "https://assets.quicktagcloud.com/originals/artist_nai5_personal/" +
                "artist_nai5_personal_huazhiyu_0001.png",
            url,
        )
    }

    @Test
    fun `没有 assetRev 时不挂查询串`() {
        val url = TagCodexProtocol.imageUrl(media, "c1", "a.jpg")
        assertEquals("https://assets.quicktagcloud.com/images/c1/a.jpg", url)
    }

    @Test
    fun `文件名是绝对地址时原样用`() {
        // 外链法典的图就是这么给的（mengshen_r18 用 assetBaseUrl + relative）
        val url = TagCodexProtocol.imageUrl(
            media = media,
            codexId = "c1",
            file = "https://prompt-vault-gallery.pages.dev/images/x.png",
            assetRev = "rev1",
        )
        assertEquals("https://prompt-vault-gallery.pages.dev/images/x.png?v=rev1", url)
    }

    @Test
    fun `文件名为空时返回空串`() {
        assertEquals("", TagCodexProtocol.imageUrl(media, "c1", ""))
    }

    @Test
    fun `地址里的中文与空格会被转义`() {
        val url = TagCodexProtocol.imageUrl(media, "法典 一", "画师 图.jpg")
        assertTrue(url.contains("%20"))
        assertTrue(url.contains("images/%E6%B3%95%E5%85%B8%20%E4%B8%80/"))
    }

    @Test
    fun `release 路径与代理路径`() {
        assertEquals(
            "https://assets.quicktagcloud.com/data/releases/r-abc/artist.json",
            TagCodexProtocol.releaseUrl("https://assets.quicktagcloud.com/data", "r-abc", "artist.json"),
        )
        assertEquals(
            "https://novelai.quicktagcloud.com/data/releases/r-abc/artist.json",
            TagCodexProtocol.proxyReleaseUrl("r-abc", "artist.json"),
        )
        assertEquals(
            "https://novelai.quicktagcloud.com/data/codexes.json",
            TagCodexProtocol.proxyUrl("codexes.json"),
        )
    }

    @Test
    fun `基址结尾多一个斜杠不会拼出双斜杠`() {
        assertEquals(
            "https://assets.quicktagcloud.com/data/current.json",
            TagCodexProtocol.pointerUrl("https://assets.quicktagcloud.com/data/"),
        )
    }

    // ------------------------------------------------------------------ 解析

    @Test
    fun `解析 data-source 配置`() {
        val json = JSONObject(
            """{"schemaVersion":1,"baseUrl":"https://assets.quicktagcloud.com/data",
               "pointer":"current.json","remoteHosts":["novelai.quicktagcloud.com"]}""",
        )
        assertEquals("https://assets.quicktagcloud.com/data", TagCodexProtocol.parseConfig(json))
    }

    @Test
    fun `配置的 schemaVersion 不对或不是 https 就拒绝`() {
        assertNull(
            TagCodexProtocol.parseConfig(
                JSONObject("""{"schemaVersion":2,"baseUrl":"https://a.com/data"}"""),
            ),
        )
        assertNull(
            TagCodexProtocol.parseConfig(
                JSONObject("""{"schemaVersion":1,"baseUrl":"http://a.com/data"}"""),
            ),
        )
        assertNull(TagCodexProtocol.parseConfig(null))
    }

    @Test
    fun `解析 release 指针 形状不对就返回 null`() {
        assertEquals(
            "r-bfd63742c6b690dd0e9e",
            TagCodexProtocol.parseRelease(JSONObject("""{"release":"r-bfd63742c6b690dd0e9e"}""")),
        )
        // 站长自己也是用这个正则卡形状的
        assertNull(TagCodexProtocol.parseRelease(JSONObject("""{"release":"latest"}""")))
        assertNull(TagCodexProtocol.parseRelease(JSONObject("""{"release":"r-XYZ"}""")))
        assertNull(TagCodexProtocol.parseRelease(JSONObject("{}")))
    }

    @Test
    fun `解析 media 配置`() {
        val parsed = TagCodexProtocol.parseMedia(
            JSONObject(
                """{"baseUrl":"https://assets.quicktagcloud.com","bucket":"b",
                   "imagePrefix":"images","originalPrefix":"originals","localFallback":true}""",
            ),
        )
        assertEquals("https://assets.quicktagcloud.com", parsed.baseUrl)
        assertEquals("images", parsed.imagePrefix)
        assertEquals("originals", parsed.originalPrefix)
    }

    @Test
    fun `media 缺字段时给默认前缀`() {
        val parsed = TagCodexProtocol.parseMedia(JSONObject("""{"baseUrl":"https://a.com"}"""))
        assertEquals("images", parsed.imagePrefix)
        assertEquals("originals", parsed.originalPrefix)
    }

    @Test
    fun `法典列表会剔除 NSFW 与外链本`() {
        val array = JSONArray(
            """
            [
              {"id":"artist_nai5_personal","title":"NovelAI v5画师词典","type":"string",
               "version":"2026.9.13","author":"花枝鱼","entryCount":2688},
              {"id":"suozhang_r18","title":"所长色色","type":"codex","nsfw":true,"entryCount":11840},
              {"id":"mengshen_r18","title":"涩涩法典","type":"codex","nsfw":true,
               "dataUrl":"https://prompt-vault-gallery.pages.dev/data/prompt-vault.json"},
              {"id":"suozhang","title":"所长常规","type":"codex","entryCount":5671}
            ]
            """.trimIndent(),
        )
        val list = TagCodexProtocol.parseCodexes(array)

        assertEquals(2, list.size)
        assertEquals(listOf("artist_nai5_personal", "suozhang"), list.map { it.id })
        assertEquals("NovelAI v5画师词典", list[0].title)
        assertEquals(2688, list[0].entryCount)
    }

    @Test
    fun `法典列表会跳过没有 id 的条目`() {
        val list = TagCodexProtocol.parseCodexes(JSONArray("""[{"title":"没有 id"},{"id":"ok"}]"""))
        assertEquals(listOf("ok"), list.map { it.id })
    }

    @Test
    fun `解析一本法典的正文`() {
        val json = JSONObject(
            """
            {
              "id":"artist_nai5_personal",
              "title":"NovelAI v5画师词典",
              "version":"2026.9.13",
              "author":"花枝鱼 / 九七",
              "newFilterLabel":"本次9.13更新",
              "updateFilters":[{"id":"2026.9.10","label":"9.10更新"},
                               {"id":"2026.9.13","label":"9.13更新","latest":true}],
              "tree":[{"name":"单画师词典","count":2199,"children":[
                        {"name":"花枝鱼","count":384,"children":[
                          {"name":"NAI5单画师测试图","count":384,"children":[]}]}]}],
              "entries":[
                {"id":"artist_nai5_personal_huazhiyu_0001","title":"382, 2025",
                 "tags":"artist:382, year 2025",
                 "path":["单画师词典","花枝鱼","NAI5单画师测试图"],
                 "rating":"safe","isNew":false,"updateBatches":["2026.9.10"],
                 "image":"artist_nai5_personal_huazhiyu_0001.jpg",
                 "imageWidth":753,"imageHeight":1100,
                 "original":"artist_nai5_personal_huazhiyu_0001.png",
                 "images":[{"path":"artist_nai5_personal_huazhiyu_0001.jpg",
                            "original":"artist_nai5_personal_huazhiyu_0001.png",
                            "rawTag":"artist:382, year 2025"}],
                 "assetRev":"aa71793a8120742f"}
              ]
            }
            """.trimIndent(),
        )

        val doc = TagCodexProtocol.parseDoc(
            json = json,
            summary = TagCodexSummary(id = "artist_nai5_personal", title = "占位", entryCount = 0),
            media = media,
        )

        // 正文里的标题/作者覆盖掉摘要里的占位值
        assertEquals("NovelAI v5画师词典", doc.summary.title)
        assertEquals("花枝鱼 / 九七", doc.summary.author)
        assertEquals(1, doc.summary.entryCount)

        assertEquals(1, doc.tree.size)
        assertEquals("单画师词典", doc.tree[0].name)
        assertEquals("花枝鱼", doc.tree[0].children[0].name)
        assertEquals("NAI5单画师测试图", doc.tree[0].children[0].children[0].name)

        assertEquals(2, doc.updateFilters.size)
        assertEquals("2026.9.10" to "9.10更新", doc.updateFilters[0])
        assertEquals("本次9.13更新", doc.newFilterLabel)

        val entry = doc.entries[0]
        assertEquals("382, 2025", entry.title)
        assertEquals("artist:382, year 2025", entry.tags)
        assertEquals(listOf("单画师词典", "花枝鱼", "NAI5单画师测试图"), entry.path)
        assertEquals(753, entry.width)
        assertTrue(entry.hasOriginal)
        assertEquals(1, entry.images.size)
        assertEquals("aa71793a8120742f", entry.assetRev)
    }

    @Test
    fun `词条只有 image 没有 images 数组时也能取到一张图`() {
        val json = JSONObject(
            """{"entries":[{"id":"a","title":"A","tags":"artist:a",
               "image":"a.jpg","original":"a.png"}]}""",
        )
        val doc = TagCodexProtocol.parseDoc(json, TagCodexSummary("c", "C"), media)

        assertEquals(1, doc.entries.size)
        assertEquals(1, doc.entries[0].images.size)
        assertEquals("a.jpg", doc.entries[0].images[0].path)
        assertTrue(doc.entries[0].hasOriginal)
    }

    @Test
    fun `解析正文会跳过没有 id 的词条`() {
        val json = JSONObject("""{"entries":[{"title":"没有 id"},{"id":"ok","tags":"t"}]}""")
        val doc = TagCodexProtocol.parseDoc(json, TagCodexSummary("c", "C"), media)
        assertEquals(listOf("ok"), doc.entries.map { it.id })
    }

    // ------------------------------------------------------------------ 检索

    @Test
    fun `检索语法 空格等于同时满足`() {
        val (must, mustNot) = TagCodexQuery.parse("krenz 2025")
        assertEquals(listOf("krenz", "2025"), must)
        assertTrue(mustNot.isEmpty())
    }

    @Test
    fun `检索语法 引号里的空格不断句`() {
        val (must, _) = TagCodexQuery.parse("\"kuroboshi kohaku\" 2025")
        assertEquals(listOf("kuroboshi kohaku", "2025"), must)
    }

    @Test
    fun `检索语法 减号是排除`() {
        val (must, mustNot) = TagCodexQuery.parse("artist -r18")
        assertEquals(listOf("artist"), must)
        assertEquals(listOf("r18"), mustNot)
    }

    @Test
    fun `检索语法 单独一个减号不算排除`() {
        val (must, mustNot) = TagCodexQuery.parse("-")
        assertEquals(listOf("-"), must)
        assertTrue(mustNot.isEmpty())
    }

    @Test
    fun `检索语法 空串没有条件`() {
        val (must, mustNot) = TagCodexQuery.parse("   ")
        assertTrue(must.isEmpty())
        assertTrue(mustNot.isEmpty())
        assertTrue(TagCodexQuery.matches(entry("x"), "   "))
    }

    @Test
    fun `检索命中标题 提示词串 与目录路径`() {
        val e = entry(
            title = "krenz",
            tags = "artist:krenz, year 2025",
            path = listOf("单画师词典", "花枝鱼"),
        )
        assertTrue(TagCodexQuery.matches(e, "krenz"))
        assertTrue(TagCodexQuery.matches(e, "artist:krenz"))
        assertTrue(TagCodexQuery.matches(e, "花枝鱼"))
        assertFalse(TagCodexQuery.matches(e, "liduke"))
    }

    @Test
    fun `检索 同时满足不是任意满足`() {
        val e = entry(title = "krenz", tags = "artist:krenz, year 2025")
        assertTrue(TagCodexQuery.matches(e, "krenz 2025"))
        // 两个词里只有一个命中 → 不算
        assertFalse(TagCodexQuery.matches(e, "krenz 2019"))
    }

    @Test
    fun `检索 排除词真的排除`() {
        val e = entry(title = "krenz", tags = "artist:krenz, year 2025")
        assertTrue(TagCodexQuery.matches(e, "krenz -2019"))
        assertFalse(TagCodexQuery.matches(e, "krenz -2025"))
    }

    @Test
    fun `检索 大小写不敏感`() {
        val e = entry(title = "Krenz", tags = "artist:Krenz")
        assertTrue(TagCodexQuery.matches(e, "krenz"))
        assertTrue(TagCodexQuery.matches(e, "KRENZ"))
    }

    @Test
    fun `筛选 按目录前缀逐级下钻`() {
        val a = entry("a", path = listOf("单画师词典", "花枝鱼", "NAI5单画师测试图"))
        val b = entry("b", path = listOf("单画师词典", "九七(无原图)", "5F单artist画风炼度参考"))
        val c = entry("c", path = listOf("画风组词典", "梦神NAI5F画风合集"))
        val all = listOf(a, b, c)

        assertEquals(3, TagCodexQuery.filter(all).size)
        assertEquals(2, TagCodexQuery.filter(all, pathPrefix = listOf("单画师词典")).size)
        assertEquals(
            listOf("a"),
            TagCodexQuery.filter(all, pathPrefix = listOf("单画师词典", "花枝鱼")).map { it.id },
        )
        assertEquals(
            listOf("a", "b"),
            TagCodexQuery
                .filter(all, pathPrefix = listOf("单画师词典"))
                .map { it.id },
        )
    }

    @Test
    fun `筛选 路径比词条本身还长时不命中`() {
        val e = entry("a", path = listOf("单画师词典"))
        assertTrue(TagCodexQuery.filter(listOf(e), pathPrefix = listOf("单画师词典", "花枝鱼")).isEmpty())
    }

    @Test
    fun `筛选 更新批次`() {
        val a = entry("a").copy(updateBatches = listOf("2026.9.10"))
        val b = entry("b").copy(updateBatches = listOf("2026.9.13"))
        val all = listOf(a, b)

        assertEquals(listOf("a"), TagCodexQuery.filter(all, updateId = "2026.9.10").map { it.id })
        assertEquals(2, TagCodexQuery.filter(all, updateId = "").size)
        assertTrue(TagCodexQuery.filter(all, updateId = "2026.1.1").isEmpty())
    }

    @Test
    fun `筛选 只看新增`() {
        val a = entry("a").copy(isNew = true)
        val b = entry("b")
        assertEquals(listOf("a"), TagCodexQuery.filter(listOf(a, b), newOnly = true).map { it.id })
    }

    @Test
    fun `筛选 多个条件是与关系`() {
        val a = entry("a", title = "krenz", path = listOf("单画师词典"))
            .copy(updateBatches = listOf("2026.9.13"), isNew = true)
        val b = entry("b", title = "krenz", path = listOf("单画师词典"))
        val all = listOf(a, b)

        assertEquals(
            listOf("a"),
            TagCodexQuery.filter(
                all,
                query = "krenz",
                pathPrefix = listOf("单画师词典"),
                updateId = "2026.9.13",
                newOnly = true,
            ).map { it.id },
        )
    }

    // ------------------------------------------------------------------ 默认值

    @Test
    fun `默认打开的是用户给的那一本`() {
        assertEquals("artist_nai5_personal", TagCodexProtocol.DEFAULT_CODEX)
    }

    @Test
    fun `授权说明里点明了数据不许再分发`() {
        // 代码是 MIT，但词条与数据汇编另有归属 —— 所以只能在线读+本地缓存，不能打包分发。
        // 这条断言是给"以后有人想把这本 JSON 塞进 assets"提个醒。
        assertTrue(TagCodexProtocol.LICENSE_NOTE.contains("不得再分发"))
    }

    private fun entry(
        id: String = "e",
        title: String = id,
        tags: String = "artist:$id",
        path: List<String> = emptyList(),
    ) = TagCodexEntry(id = id, title = title, tags = tags, path = path)

    // ------------------------------------------- 第 ㊾ 批：三个提示词槽（双端同一份口径）

    /**
     * 数据里**只有 `codex` / `pack` 那几本**有 `characterPrompts` / `negative`
     *（画师词典 `artist_nai5_personal` 实测一个字都没有）。
     *
     * 口径照原网页 `assets/app/copy.js`：正面 = `tags`、人物 = 各 `prompt`（**去掉 `char1:` 标记**）、
     * 负面 = `negative`（**单独一步，绝不并进正面**）。
     */
    @Test
    fun `三个提示词槽按原网页口径解析与拼接`() {
        val raw = """{"title":"t","entries":[{"id":"x","title":"远坂凛与Archer",""" +
            """"tags":"very aesthetic, 1boy, 1girl",""" +
            """"negative":"worst quality, lowres",""" +
            """"characterPrompts":[{"label":"char1","prompt":"girl, tohsaka rin","negative":""},""" +
            """{"label":"char2","prompt":"boy, archer (fate)","negative":""}]}]}"""
        val e = TagCodexProtocol
            .parseDoc(JSONObject(raw), TagCodexSummary(id = "x", title = "x"), media)
            .entries.single()

        assertEquals("正面 = tags", "very aesthetic, 1boy, 1girl", e.tags)
        assertTrue("负面要读上来", e.negative.startsWith("worst quality"))
        assertEquals("两个角色都要读上来", 2, e.characters.size)
        assertEquals("char1", e.characters[0].label)

        // ⚠️ 人物文本**去掉 char1:/char2: 标记**（用户点名 + 原网页同口径）
        assertEquals("girl, tohsaka rin\nboy, archer (fate)", e.characterPromptText)
        assertFalse("标记不许出现", e.characterPromptText.contains("char1"))

        // 三个"有没有"判据 —— 界面按它们决定出不出那颗按钮
        assertTrue(e.hasPositive && e.hasCharacters && e.hasNegative)

        // 负面**绝不混进正面**（原网页："混进去等于反向作画"）
        assertFalse(e.tags.contains("worst quality"))
        assertFalse(e.characterPromptText.contains("lowres"))
    }

    /** 画师词典那本没有那两个字段 ⇒ 按钮不许出，且不许崩。 */
    @Test
    fun `没有人物与负面字段的条目仍然解析`() {
        val raw = """{"title":"t","entries":[{"id":"a1","title":"382, 2025",""" +
            """"tags":"artist:382, year 2025"}]}"""
        val e = TagCodexProtocol
            .parseDoc(JSONObject(raw), TagCodexSummary(id = "x", title = "x"), media)
            .entries.single()

        assertTrue("有正面", e.hasPositive)
        assertFalse("没有人物词 ⇒ 按钮不许出", e.hasCharacters)
        assertFalse("没有负面 ⇒ 按钮不许出", e.hasNegative)
        assertEquals("没有就是空串", "", e.negative)
        assertTrue("没有就是空表", e.characters.isEmpty())
    }
}
