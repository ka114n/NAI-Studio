package com.kallan.naistudio.desktop

import com.kallan.naistudio.models.TagCodexProtocol
import com.kallan.naistudio.models.TagCodexSummary
import com.kallan.naistudio.models.TagCodexMedia
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **第 ㊾ 批（2026-09-21）：画师超市的三个提示词槽** ✓
 *
 * 用户口径：**「原网页可选复制正面提示词,人物提示词,负面提示词,加上」** ✓
 *
 * ## 口径来源（**照原网页，不自创** ✓）
 *
 * 原站点把这段写在 `assets/app/copy.js` 的 `entryPromptText` / `copyEntry` 里 ✓：
 *  · **正面** = `entry.tags` ✓；
 *  · **人物** = 各 `characterPrompts[].prompt`，**去掉 `char1:` 标记** ✓；
 *  · **负面** = `entry.negative` ✓，**单独一步**（"再复制负面"✓）——
 *    源码那行注释写得很直白：*"绝不能进正面串——那是'不要什么'，混进去等于反向作画"* ✓。
 *
 * ## 为什么要本文件
 *
 * 数据里**只有部分法典**有那两个字段 ✗（实测：`nai5_community_pack` 有 ✓、
 * 画师词典 `artist_nai5_personal` **一个字都没有** ✗）⇒
 * "按钮该不该出"必须按**真实的空/非空**判 ✓ —— 这一条钉的就是它 ✓。
 */
class TagCodexPromptsTest {

    private fun doc(entries: String): JSONObject = JSONObject(
        """{"title":"t","entries":[$entries]}""",
    )

    private fun parse(json: JSONObject) =
        TagCodexProtocol.parseDoc(json, TagCodexSummary(id = "x", title = "x"), TagCodexMedia())

    /** 实测那份 `nai5_community_pack` 里一条真实词条的**形状**（字段名逐字对齐 ✓）。 */
    private val packEntry = """
        {
          "id": "nai5_community_pack_x_0001",
          "title": "远坂凛与Archer",
          "tags": "very aesthetic, amazing quality, 1boy, 1girl, night, city",
          "negative": "worst quality, bad quality, lowres, watermark",
          "characterPrompts": [
            {"label": "char1", "prompt": "girl, tohsaka rin, blue eyes", "negative": ""},
            {"label": "char2", "prompt": "boy, archer (fate), grey eyes", "negative": ""}
          ]
        }
    """.trimIndent()

    /** 画师词典那本的形状：**没有** negative / characterPrompts（实测 0 次 ✓）。 */
    private val artistEntry = """
        {"id": "a1", "title": "382, 2025", "tags": "artist:382, year 2025"}
    """.trimIndent()

    @Test
    fun the_three_slots_are_parsed_from_the_pack_shape() {
        val e = parse(doc(packEntry)).entries.single()
        assertEquals("正面 = tags", "very aesthetic, amazing quality, 1boy, 1girl, night, city", e.tags)
        assertTrue("负面必须读上来", e.negative.startsWith("worst quality"))
        assertEquals("两个角色都要读上来", 2, e.characters.size)
        assertEquals("char1", e.characters[0].label)
        assertEquals("char2", e.characters[1].label)
    }

    /**
     * **人物文本去掉 `char1:` 标记** ✓ —— 这是用户点名的口径，也是原网页的口径 ✓
     *（源码注释：最初那条反馈要去掉的正是 `char1：` 这个垃圾 token ✓）。
     */
    @Test
    fun character_prompt_text_drops_the_char_label() {
        val e = parse(doc(packEntry)).entries.single()
        val text = e.characterPromptText
        assertEquals(
            "各角色 prompt 换行拼接，且**不带 char1/char2 标记**",
            "girl, tohsaka rin, blue eyes\nboy, archer (fate), grey eyes",
            text,
        )
        assertFalse("标记不许出现", text.contains("char1"))
        assertFalse("标记不许出现", text.contains("char2"))
    }

    /** **三个"有没有"判据** —— 界面就按它们决定出不出那颗按钮 ✓。 */
    @Test
    fun the_availability_flags_follow_the_real_data() {
        val pack = parse(doc(packEntry)).entries.single()
        assertTrue("这本三样都有", pack.hasPositive && pack.hasCharacters && pack.hasNegative)

        val artist = parse(doc(artistEntry)).entries.single()
        assertTrue("画师词典有正面", artist.hasPositive)
        assertFalse("画师词典**没有**人物词 ⇒ 按钮不许出", artist.hasCharacters)
        assertFalse("画师词典**没有**负面 ⇒ 按钮不许出", artist.hasNegative)
        assertEquals("没有就是空串，不是 null", "", artist.negative)
        assertTrue("没有就是空表", artist.characters.isEmpty())
    }

    /** **负面绝不混进正面** ✓（原网页那句"混进去等于反向作画"的判据 ✓）。 */
    @Test
    fun the_negative_is_never_folded_into_the_positive() {
        val e = parse(doc(packEntry)).entries.single()
        assertFalse("正面串里不许出现负面词", e.tags.contains("worst quality"))
        assertFalse("人物串里不许出现负面词", e.characterPromptText.contains("lowres"))
    }

    /** 只有 `label` 没有 `prompt` 的角色**不算数** ✓（复制出来会是空串 ✗）。 */
    @Test
    fun characters_without_a_prompt_are_skipped() {
        val json = doc(
            """{"id":"z","title":"z","tags":"t","characterPrompts":[
                 {"label":"char1","prompt":"","negative":"x"},
                 {"label":"char2","prompt":"girl, smile"}
               ]}""",
        )
        val e = parse(json).entries.single()
        assertEquals("只留真有正文的那一条", 1, e.characters.size)
        assertEquals("girl, smile", e.characterPromptText)
    }

    /** 一个字段都没有的老条目**不能崩** ✓（画师词典 2688 条全是这种 ✓）。 */
    @Test
    fun entries_without_the_new_fields_still_parse() {
        val e = parse(doc("""{"id":"old","title":"old","tags":"artist:x"}""")).entries.single()
        assertEquals("artist:x", e.tags)
        assertEquals(0, e.characters.size)
        assertEquals("", e.negative)
    }
}
