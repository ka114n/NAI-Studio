package com.kallan.naistudio.models

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「**格子与图层合一**」（第 ㊿l 批 ✓，用户 2026-09-22，方案 `docs/70` ✓）。
 *
 * 这一族钉两件事：
 *  1. 格子自己那三个新字段（`imagePath` / `opacity` / `visible` ✓）**存得进、读得回** ✓；
 *  2. **缺省值不写键** ✓ —— 这条不是省字节，是**兼容判据**：
 *     老工程（没有这几个键 ✓）读出来必须正好是"**空页**"✓（用户拍板**不迁移** ✓：
 *     「旧工程当空页」✓）。写侧要是把缺省值也写进去，读侧就没法用"键在不在"区分新老了 ✓。
 */
class ComicPanelMergeTest {

    @Test
    fun `新字段能存能读`() {
        val panel = ComicPanel(
            id = "p1",
            x = 10f,
            y = 20f,
            w = 300f,
            h = 200f,
            prompt = "long hair",
            imagePath = "/tmp/layer-p1.png",
            opacity = 0.42f,
            visible = false,
        )
        val back = ComicPanel.fromJson(panel.toJson())
        assertEquals(panel, back)
    }

    @Test
    fun `缺省值不写键 —— 老工程的 JSON 长什么样，新代码就读成什么`() {
        val fresh = ComicPanel(id = "p2", x = 0f, y = 0f, w = 100f, h = 100f)
        val json = fresh.toJson()
        assertTrue("还没画面 ⇒ 不写 imagePath ✓", !json.has("imagePath"))
        assertTrue("完全不透明 ⇒ 不写 opacity ✓", !json.has("opacity"))
        assertTrue("可见 ⇒ 不写 visible ✓", !json.has("visible"))
        // 老工程的 JSON（只有那几个老键 ✓）读出来 = 空页 ✓
        val legacy = JSONObject()
            .put("id", "old")
            .put("x", 1.0).put("y", 2.0).put("w", 100.0).put("h", 50.0)
            .put("prompt", "1girl")
        val read = ComicPanel.fromJson(legacy)!!
        assertNull("老工程没有图 ⇒ imagePath 为 null ✓（用户拍板不迁移 ✓）", read.imagePath)
        assertEquals(1f, read.opacity, 0.0001f)
        assertTrue(read.visible)
        assertEquals("1girl", read.prompt)
    }

    @Test
    fun `退化的宽高照旧丢掉 —— 合一没有放松这条`() {
        val bad = JSONObject().put("id", "p3").put("x", 0.0).put("y", 0.0).put("w", 0.0).put("h", 10.0)
        assertNull(ComicPanel.fromJson(bad))
    }
}
