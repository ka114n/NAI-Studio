package com.kallan.naistudio.models

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份里的**漫画小节**（0.2.82 新增）。
 *
 * 这一节是我当初"顺手加的"，一直**没有断言**盯着 —— 而备份是那种"平时不跑、
 * 一跑就是救数据"的功能，必须有测试。这里钉三件事：
 *
 *  1. 导出 → 解析，漫画的设置与分格要**原样回来**（含每格的 `panelRole`）；
 *  2. **老备份（没有 comic 小节）解析出来必须是 `comic == null`** ——
 *     这样导入老备份时 `bundle.comic?.let{}` 不会执行，**不会覆盖本机已有的漫画数据**；
 *  3. 新备份对老版本也安全：多出来的 `comic` 键在那边是"未知键"，会被忽略。
 */
class ComicBackupTest {

    private val settings = AppSettings(apiBaseUrl = "https://api.novelai.net")

    private val comicSettings = ComicSettings(
        mode = true,
        layout = "v4a",
        order = ComicLayout.ORDER_LTR,
        stylePrompt = true,
        style = "manga page, monochrome, screentone",
    )

    private val panels = listOf(
        CharCaptionItem(name = "格 1", prompt = "city street at night", panelRole = "scene"),
        CharCaptionItem(name = "格 2", prompt = "girl, looking back", panelRole = ""),
        CharCaptionItem(name = "格 3", prompt = "text, speech bubble \"bye\"", panelRole = "text"),
    )

    private fun bundleWithComic() = BackupCodec.Bundle(
        settings = settings,
        comic = BackupCodec.ComicBundle(settings = comicSettings, panels = panels),
    )

    private fun roundTrip(bundle: BackupCodec.Bundle): BackupCodec.Bundle =
        BackupCodec.decode(BackupCodec.encode(bundle, "0.2.90", "2026-09-14T00:00:00Z"))

    // -----------------------------------------------------------------------
    // 往返
    // -----------------------------------------------------------------------

    @Test
    fun `漫画设置原样往返`() {
        val restored = roundTrip(bundleWithComic()).comic!!
        assertTrue(restored.settings.mode)
        assertEquals("v4a", restored.settings.layout)
        assertEquals(ComicLayout.ORDER_LTR, restored.settings.order)
        assertTrue(restored.settings.stylePrompt)
        assertEquals("manga page, monochrome, screentone", restored.settings.style)
    }

    @Test
    fun `分格数量与内容原样往返`() {
        val restored = roundTrip(bundleWithComic()).comic!!
        assertEquals(3, restored.panels.size)
        assertEquals(
            listOf("格 1", "格 2", "格 3"),
            restored.panels.map { it.name },
        )
        assertEquals(
            listOf("city street at night", "girl, looking back", "text, speech bubble \"bye\""),
            restored.panels.map { it.prompt },
        )
    }

    @Test
    fun `每格的 panelRole 原样往返`() {
        val restored = roundTrip(bundleWithComic()).comic!!
        assertEquals(listOf("scene", "", "text"), restored.panels.map { it.panelRole })
    }

    @Test
    fun `分格与角色两节互不干扰`() {
        val characters = listOf(CharCaptionItem(name = "少女", prompt = "1girl"))
        val restored = roundTrip(
            BackupCodec.Bundle(
                settings = settings,
                charCaptions = characters,
                comic = BackupCodec.ComicBundle(settings = comicSettings, panels = panels),
            ),
        )
        assertEquals(1, restored.charCaptions!!.size)
        assertEquals("少女", restored.charCaptions!![0].name)
        assertEquals(3, restored.comic!!.panels.size)
        // 角色小节里绝不能混进分格
        assertFalse(restored.charCaptions!!.any { it.prompt.contains("city street") })
    }

    @Test
    fun `分格不是角色_导出时不会写进 characterPrompts 那一节`() {
        val json = JSONObject(BackupCodec.encode(bundleWithComic(), "0.2.90", "2026-09-14T00:00:00Z"))
        val payload = json.getJSONObject("payload")
        assertTrue("漫画必须写在独立的 comic 小节里", payload.has("comic"))
        assertFalse("没有角色时不该写 characterPrompts", payload.has("characterPrompts"))
        assertEquals(
            3,
            payload.getJSONObject("comic").getJSONArray("panels").length(),
        )
    }

    // -----------------------------------------------------------------------
    // 向后兼容：老备份没有 comic 小节
    // -----------------------------------------------------------------------

    @Test
    fun `老备份解析出来 comic 是 null_不会覆盖本机漫画数据`() {
        // 模拟 0.2.78 那种没有 comic 小节的备份
        val oldJson = JSONObject(BackupCodec.encode(
            BackupCodec.Bundle(settings = settings, charCaptions = panels),
            "0.2.78",
            "2026-09-13T00:00:00Z",
        ))
        oldJson.getJSONObject("payload").remove("comic")

        val restored = BackupCodec.decode(oldJson.toString())
        assertNull("老备份必须解析成 null，导入时才会跳过漫画那一节", restored.comic)
        assertEquals(3, restored.charCaptions!!.size)
    }

    @Test
    fun `缺 settings 的 comic 小节也不会炸`() {
        val json = JSONObject(BackupCodec.encode(bundleWithComic(), "0.2.90", "2026-09-14T00:00:00Z"))
        json.getJSONObject("payload").getJSONObject("comic").remove("settings")

        val restored = BackupCodec.decode(json.toString())
        assertEquals(3, restored.comic!!.panels.size)
        // 缺设置时回落到默认值，而不是崩掉
        assertEquals(ComicSettings(), restored.comic!!.settings)
    }

    // -----------------------------------------------------------------------
    // 摘要 / 小节
    // -----------------------------------------------------------------------

    @Test
    fun `摘要数得出漫画分格数并列出小节`() {
        val summary = roundTrip(bundleWithComic()).summary
        assertEquals(3, summary.comicPanels)
        assertTrue(summary.sections.contains(BackupCodec.SECTION_COMIC))
    }

    @Test
    fun `没有漫画数据时摘要里不出现漫画小节`() {
        val summary = roundTrip(BackupCodec.Bundle(settings = settings)).summary
        assertEquals(0, summary.comicPanels)
        assertFalse(summary.sections.contains(BackupCodec.SECTION_COMIC))
    }

    @Test
    fun `只有漫画内容的备份不会被当成空备份拒掉`() {
        val restored = roundTrip(
            BackupCodec.Bundle(
                comic = BackupCodec.ComicBundle(settings = comicSettings, panels = panels),
            ),
        )
        assertEquals(3, restored.comic!!.panels.size)
    }

    @Test
    fun `漫画分格为空的分节不算内容`() {
        val summary = roundTrip(
            BackupCodec.Bundle(
                settings = settings,
                comic = BackupCodec.ComicBundle(settings = comicSettings, panels = emptyList()),
            ),
        ).summary
        assertEquals(0, summary.comicPanels)
        assertTrue("有设置撑着，不该算空备份", !summary.isEmpty)
    }

    // -----------------------------------------------------------------------
    // 格式版本没动
    // -----------------------------------------------------------------------

    @Test
    fun `加漫画小节没有动 formatVersion`() {
        assertEquals(1, BackupCodec.FORMAT_VERSION)
    }

    @Test
    fun `老版本读到 comic 键只会忽略_不影响其它小节`() {
        // 老版本的写法就是"只读自己认识的键"，这里用一个只认识 settings 的解析模拟：
        // 把 comic 键丢掉，其余小节照样能解析出来。
        val json = JSONObject(BackupCodec.encode(bundleWithComic(), "0.2.90", "2026-09-14T00:00:00Z"))
        json.getJSONObject("payload").remove("comic")
        val restored = BackupCodec.decode(json.toString())
        assertEquals("https://api.novelai.net", restored.settings!!.apiBaseUrl)
        assertNull(restored.comic)
    }
}
