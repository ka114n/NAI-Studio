package com.kallan.naistudio.services

import com.kallan.naistudio.models.CharCaptionItem
import com.kallan.naistudio.models.ComicLayout
import com.kallan.naistudio.models.GenerateExtras
import com.kallan.naistudio.models.GenerateParams
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 漫画模式请求体的字段契约。
 *
 * 这里钉的是**真正发出去的东西**，不是界面：
 *  · 漫画模式发的是**分格列表**，不是角色列表（两份数据互不干扰）；
 *  · 分格的坐标**由版式派生**，跟分格自己的 `x/y` 无关；
 *  · 漫画模式**强制** `use_coords = true`（否则版式白选了）；
 *  · 漫画模式与普通模式共用用户填写的风格、正面和负面提示词。
 *
 * 纯 JVM 可跑，不联网、不花 Anlas。
 */
class ComicPayloadTest {

    private val api = NaiApi()

    private val params = GenerateParams(
        model = "nai-diffusion-5-full",
        positivePrompt = "1girl, solo",
        width = 832,
        height = 1216,
    )

    private fun charCaptions(payload: org.json.JSONObject): JSONArray =
        payload.getJSONObject("parameters")
            .getJSONObject("v4_prompt")
            .getJSONObject("caption")
            .getJSONArray("char_captions")

    private fun centerOf(payload: org.json.JSONObject, index: Int): Pair<Double, Double> {
        val centers = charCaptions(payload).getJSONObject(index).getJSONArray("centers")
        val point = centers.getJSONObject(0)
        return point.getDouble("x") to point.getDouble("y")
    }

    private fun captionOf(payload: org.json.JSONObject, index: Int): String =
        charCaptions(payload).getJSONObject(index).getString("char_caption")

    private fun baseCaption(payload: org.json.JSONObject): String =
        payload.getJSONObject("parameters")
            .getJSONObject("v4_prompt")
            .getJSONObject("caption")
            .getString("base_caption")

    // -----------------------------------------------------------------------

    @Test
    fun `漫画模式发的是分格列表_不是角色列表`() {
        val extras = GenerateExtras(
            comicMode = true,
            charCaptions = listOf(CharCaptionItem(prompt = "这是角色，不该发出去")),
            comicPanels = listOf(
                CharCaptionItem(name = "格 1", prompt = "panel one"),
                CharCaptionItem(name = "格 2", prompt = "panel two"),
            ),
        )

        val payload = api.buildPayload(params = params, seed = 1L, extras = extras)

        assertEquals(2, charCaptions(payload).length())
        assertEquals("panel one", captionOf(payload, 0))
        assertEquals("panel two", captionOf(payload, 1))
        assertFalse(
            "角色列表的内容绝不能出现在漫画模式的请求里",
            payload.toString().contains("这是角色，不该发出去"),
        )
    }

    @Test
    fun `角色模式不理会分格列表`() {
        val extras = GenerateExtras(
            comicMode = false,
            charCaptions = listOf(CharCaptionItem(prompt = "这是角色")),
            comicPanels = listOf(CharCaptionItem(name = "格 1", prompt = "panel one")),
        )

        val payload = api.buildPayload(params = params, seed = 1L, extras = extras)

        assertEquals(1, charCaptions(payload).length())
        assertEquals("这是角色", captionOf(payload, 0))
        assertFalse(payload.toString().contains("panel one"))
    }

    @Test
    fun `漫画模式下没动过的格按版式派生坐标`() {
        // useCoords = false 表示"用户没在手拖编辑器里动过这一格" → 用版式锚点
        val extras = GenerateExtras(
            comicMode = true,
            comicLayout = "v2h", // 左右对半
            comicOrder = ComicLayout.ORDER_LTR,
            comicPanels = listOf(
                CharCaptionItem(prompt = "a"),
                CharCaptionItem(prompt = "b"),
            ),
        )

        val payload = api.buildPayload(params = params, seed = 1L, extras = extras)

        val expected = ComicLayout.anchorsFor("v2h", ComicLayout.ORDER_LTR, 2)
        assertEquals(expected[0].first, centerOf(payload, 0).first, 1e-9)
        assertEquals(expected[0].second, centerOf(payload, 0).second, 1e-9)
        assertEquals(expected[1].first, centerOf(payload, 1).first, 1e-9)
    }

    @Test
    fun `漫画模式下动过的格用手拖坐标_覆盖版式`() {
        // 用户在版式预览里点开位置编辑挪过第 1 格 → useCoords = true
        // 第 2 格没动过 → 仍按版式
        val extras = GenerateExtras(
            comicMode = true,
            comicLayout = "v2h",
            comicOrder = ComicLayout.ORDER_LTR,
            comicPanels = listOf(
                CharCaptionItem(prompt = "a", useCoords = true, x = 0.111, y = 0.222),
                CharCaptionItem(prompt = "b"),
            ),
        )

        val payload = api.buildPayload(params = params, seed = 1L, extras = extras)

        assertEquals("手拖过的格必须用手拖坐标", 0.111, centerOf(payload, 0).first, 1e-9)
        assertEquals(0.222, centerOf(payload, 0).second, 1e-9)

        val expectedLayout = ComicLayout.anchorsFor("v2h", ComicLayout.ORDER_LTR, 2)
        assertEquals("没动过的格仍按版式", expectedLayout[1].first, centerOf(payload, 1).first, 1e-9)
    }

    @Test
    fun `漫画模式强制 use_coords 为 true`() {
        val extras = GenerateExtras(
            comicMode = true,
            comicPanels = listOf(CharCaptionItem(prompt = "a", useCoords = false)),
        )

        val payload = api.buildPayload(params = params, seed = 1L, extras = extras)
        assertTrue(
            "漫画模式必须有确定坐标，否则版式就白选了",
            payload.getJSONObject("parameters").getBoolean("use_coords"),
        )
    }

    @Test
    fun `角色模式保留各自的手拖坐标与开关`() {
        val extras = GenerateExtras(
            comicMode = false,
            charCaptions = listOf(
                CharCaptionItem(prompt = "a", useCoords = true, x = 0.25, y = 0.75),
                // 没开坐标 → 发 (0.5, 0.5) 哨兵值
                CharCaptionItem(prompt = "b", useCoords = false, x = 0.9, y = 0.9),
            ),
        )

        val payload = api.buildPayload(params = params, seed = 1L, extras = extras)

        assertEquals(0.25, centerOf(payload, 0).first, 1e-9)
        assertEquals(0.75, centerOf(payload, 0).second, 1e-9)
        assertEquals("没开坐标走 0.5 哨兵", 0.5, centerOf(payload, 1).first, 1e-9)
    }

    @Test
    fun `阅读顺序会改变发送的坐标顺序`() {
        val base = GenerateExtras(
            comicMode = true,
            comicLayout = "v2h",
            comicPanels = listOf(
                CharCaptionItem(prompt = "a"),
                CharCaptionItem(prompt = "b"),
            ),
        )

        val rtl = api.buildPayload(params = params, seed = 1L, extras = base.copy(comicOrder = "rtl"))
        val ltr = api.buildPayload(params = params, seed = 1L, extras = base.copy(comicOrder = "ltr"))

        // 第 1 格在 rtl 下靠右、ltr 下靠左
        assertTrue("rtl 第一格该偏右", centerOf(rtl, 0).first > 0.5)
        assertTrue("ltr 第一格该偏左", centerOf(ltr, 0).first < 0.5)
    }

    @Test
    fun `漫画模式使用统一风格词且不追加旧整页默认词`() {
        val panel = listOf(CharCaptionItem(prompt = "a"))
        val on = GenerateExtras(comicMode = true, comicStylePrompt = true, comicPanels = panel)
        val off = GenerateExtras(comicMode = true, comicStylePrompt = false, comicPanels = panel)
        val styled = params.copy(stylePrompt = "ink wash")

        assertTrue(
            baseCaption(api.buildPayload(params = styled, seed = 1L, extras = on))
                .contains("ink wash"),
        )
        assertFalse(
            baseCaption(api.buildPayload(params = styled, seed = 1L, extras = off))
                .contains("manga page"),
        )
    }

    @Test
    fun `角色模式不会追加漫画风格词`() {
        val extras = GenerateExtras(
            comicMode = false,
            comicStylePrompt = true, // 就算这个开关是开的
            charCaptions = listOf(CharCaptionItem(prompt = "a")),
        )

        assertFalse(
            baseCaption(api.buildPayload(params = params, seed = 1L, extras = extras))
                .contains("manga page"),
        )
    }

    @Test
    fun `漫画模式没有分格时不发 char captions 也不开 use_coords`() {
        val extras = GenerateExtras(comicMode = true, comicPanels = emptyList())

        val payload = api.buildPayload(params = params, seed = 1L, extras = extras)

        assertEquals(0, charCaptions(payload).length())
        assertFalse(payload.getJSONObject("parameters").getBoolean("use_coords"))
    }

    @Test
    fun `未启用或提示词为空的分格不参与请求`() {
        val extras = GenerateExtras(
            comicMode = true,
            comicPanels = listOf(
                CharCaptionItem(prompt = "有效"),
                CharCaptionItem(prompt = "被停用", enabled = false),
                CharCaptionItem(prompt = "   "),
            ),
        )

        val payload = api.buildPayload(params = params, seed = 1L, extras = extras)

        assertEquals("只该剩 1 个", 1, charCaptions(payload).length())
        assertEquals("有效", captionOf(payload, 0))
    }

    @Test
    fun `分格数量超过模型上限时按上限截断`() {
        val many = (1..40).map { CharCaptionItem(prompt = "p$it") }
        val extras = GenerateExtras(comicMode = true, comicPanels = many)

        val payload = api.buildPayload(params = params, seed = 1L, extras = extras)

        val limit = params.maxCharacterPrompts
        assertEquals("V5 上限 32", 32, limit)
        assertEquals(limit, charCaptions(payload).length())
    }
}
