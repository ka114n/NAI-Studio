package com.kallan.naistudio.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导演工具（Director Tools）规格：6 个工具的 req_type、像素预算、请求体字段。
 *
 * 口径来自 ComfyUI 插件 `novelai-genytools`：
 * `nai_client.py` 的 `director()/prepare_director_image()` 与 `full_nodes.py` 的 6 个节点。
 */
class DirectorToolsTest {

    @Test
    fun `six tools in plugin order with correct req types`() {
        assertEquals(
            listOf("bg-removal", "declutter", "lineart", "sketch", "colorize", "emotion"),
            DirectorTools.ALL.map { it.id },
        )
        assertEquals(
            listOf("bg-removal", "declutter", "lineart", "sketch", "colorize", "emotion"),
            DirectorTools.ALL.map { it.reqType },
        )
        // 移除背景一次回 3 张（masked / generated / blend），其余 1 张
        assertEquals(3, DirectorTools.byId("bg-removal").resultCount)
        assertEquals(1, DirectorTools.byId("lineart").resultCount)
    }

    @Test
    fun `declutter switches req type when keeping text bubbles`() {
        val tool = DirectorTools.byId("declutter")
        assertEquals("declutter", DirectorTools.reqTypeOf(tool, keepTextBubbles = false))
        assertEquals("declutter-keep-bubbles", DirectorTools.reqTypeOf(tool, keepTextBubbles = true))
        // 其它工具不受这个开关影响
        assertEquals("lineart", DirectorTools.reqTypeOf(DirectorTools.byId("lineart"), true))
    }

    @Test
    fun `only colorize takes prompt and defry, only emotion is a tool too`() {
        val colorize = DirectorTools.byId("colorize")
        assertTrue(colorize.hasPrompt)
        assertTrue(colorize.hasDefry)
        assertFalse(colorize.hasEmotion)

        val emotion = DirectorTools.byId("emotion")
        assertTrue(emotion.hasEmotion)
        assertTrue(emotion.hasDefry)
        // 删除表情工具的 prompt 走 "emotion;;extra" 这一套，不当普通提示词用
        assertFalse(emotion.hasPrompt)

        assertFalse(DirectorTools.byId("bg-removal").hasDefry)
        assertTrue(DirectorTools.byId("declutter").hasKeepBubbles)
    }

    @Test
    fun `ten emotions match the plugin list`() {
        assertEquals(10, DirectorTools.EMOTIONS.size)
        assertEquals("happy", DirectorTools.EMOTIONS[1].first)
        assertTrue(DirectorTools.EMOTIONS.any { it.first == "smug" })
    }

    @Test
    fun `pixel budget shrinks oversized images`() {
        // 4096×4096 = 16.7M 像素 → 缩到上限附近
        val (w, h) = DirectorTools.budgetSize(4096, 4096)
        assertTrue(w.toLong() * h <= DirectorTools.MAX_PIXELS)
        assertEquals(w, h) // 等比
    }

    @Test
    fun `pixel budget grows tiny images to the target`() {
        // 512×512 = 262144 < 1011712 → 放大到目标 1048576 附近
        val (w, h) = DirectorTools.budgetSize(512, 512)
        assertTrue(w.toLong() * h >= DirectorTools.MIN_TRIGGER_PIXELS)
        assertEquals(w, h)
    }

    @Test
    fun `normal sizes pass through untouched`() {
        // 832×1216 = 1,011,712：正好等于触发下限 → 不动（既不缩也不放）
        assertEquals(832 to 1216, DirectorTools.budgetSize(832, 1216))
        // 1024×1024 在预算内
        assertEquals(1024 to 1024, DirectorTools.budgetSize(1024, 1024))
    }

    @Test
    fun `invalid sizes fall back safely`() {
        assertEquals(64 to 64, DirectorTools.budgetSize(0, 0))
        assertEquals(64 to 64, DirectorTools.budgetSize(-10, 20))
    }

    @Test
    fun `request carries the plugin fields`() {
        val json = DirectorTools.requestJson(
            reqType = "colorize",
            width = 1024,
            height = 1024,
            base64Image = "AAAA",
            prompt = "soft anime colors",
            defry = 3,
        )
        assertEquals("colorize", json.getString("req_type"))
        assertTrue(json.getBoolean("use_new_shared_trial"))
        assertEquals(1024, json.getInt("width"))
        assertEquals(1024, json.getInt("height"))
        assertEquals("AAAA", json.getString("image"))
        assertEquals("soft anime colors", json.getString("prompt"))
        assertEquals(3, json.getInt("defry"))
    }

    @Test
    fun `request omits optional fields and clamps defry`() {
        val bare = DirectorTools.requestJson("lineart", 832, 1216, "BBBB")
        assertFalse(bare.has("prompt"))
        assertFalse(bare.has("defry"))

        val clamped = DirectorTools.requestJson("emotion", 832, 1216, "CCCC", prompt = "happy;;", defry = 99)
        assertEquals(DirectorTools.MAX_DEFRY, clamped.getInt("defry"))
    }

    @Test
    fun `emotion prompt uses the plugin separator`() {
        assertEquals("smug;;glasses", DirectorTools.emotionPrompt("smug", "glasses"))
        assertEquals("happy;;", DirectorTools.emotionPrompt("happy", ""))
    }
}
