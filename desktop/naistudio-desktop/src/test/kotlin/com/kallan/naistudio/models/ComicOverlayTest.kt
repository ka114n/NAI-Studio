package com.kallan.naistudio.models

import com.kallan.naistudio.ui.BubbleStyle
import com.kallan.naistudio.ui.bubbleStyleOf
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **④对话气泡 + ⑤文本（docs/43 §9.1 §9.2 §9.3）纯函数单测**。
 *
 * 这几样都是"看着对、其实会出洋相"的东西，必须机械验证：
 *  1. **拖动**气泡时尾巴要跟着走（不跟的话尾巴会指着空气 ✗）、且不越页；
 *  2. **缩放**时对角固定、不小于最小边长，尾巴尖**不动**（它指的是说话的人 ✓）；
 *  3. **尾巴尖**只允许拉到泡身下方（轮廓是"从底边拉一个三角"，目标在上边就画不出来 ✗）；
 *  4. 气泡 / 文本 / 图层的 **JSON 往返一模一样**（"关掉再打开还在"就靠它 ✓）；
 *  5. **图层叠对齐**：底板在最下、每个气泡/文本一层、指向不存在的东西的幽灵层要丢掉 ✓。
 */
class ComicOverlayTest {

    private val pageWidth = 2400
    private val pageHeight = 3200

    private fun bubble(
        id: String = "b1",
        x: Float = 100f,
        y: Float = 200f,
        w: Float = 600f,
        h: Float = 400f,
    ) = ComicBubble(id = id, x = x, y = y, w = w, h = h)

    // -----------------------------------------------------------------------
    // 几何
    // -----------------------------------------------------------------------

    @Test
    fun `拖动气泡时尾巴跟着走、且不越页`() {
        val original = bubble().copy(tailX = 400f, tailY = 700f)
        val moved = original.movedBy(50f, 25f, pageWidth, pageHeight)
        assertEquals(150f, moved.x, 0.01f)
        assertEquals(225f, moved.y, 0.01f)
        assertEquals("尾巴要跟着泡身走", 450f, moved.tailX!!, 0.01f)
        assertEquals(725f, moved.tailY!!, 0.01f)

        // 往左上拖到页外：贴边（位移被吃掉 100 / 200），尾巴跟着走同样多 —— 相对位置不变 ✓
        val far = original.movedBy(-99999f, -99999f, pageWidth, pageHeight)
        assertEquals(0f, far.x, 0.001f)
        assertEquals(0f, far.y, 0.001f)
        assertEquals(300f, far.tailX!!, 0.001f)
        assertEquals(500f, far.tailY!!, 0.001f)
    }

    @Test
    fun `拽右下角缩放时左上角不动、尾巴尖不动、不小于最小边长`() {
        val original = bubble(x = 500f, y = 600f, w = 400f, h = 300f).copy(tailX = 700f, tailY = 1200f)
        val bigger = original.resizedBy(ComicPanelCorner.BOTTOM_RIGHT, 100f, 80f, pageWidth, pageHeight)
        assertEquals(500f, bigger.x, 0.001f)
        assertEquals(600f, bigger.y, 0.001f)
        assertEquals(500f, bigger.w, 0.001f)
        assertEquals(380f, bigger.h, 0.001f)
        assertEquals("尾巴尖指的是说话的人，不跟着框走", 700f, bigger.tailX!!, 0.001f)

        val tiny = original.resizedBy(ComicPanelCorner.BOTTOM_RIGHT, -99999f, -99999f, pageWidth, pageHeight)
        val minSide = ComicOverlayMetrics.minSide(pageWidth)
        assertEquals(minSide, tiny.w, 0.01f)
        assertEquals(minSide, tiny.h, 0.01f)
    }

    @Test
    fun `拽左上角缩放时右下角不动`() {
        val original = bubble(x = 500f, y = 600f, w = 400f, h = 300f)
        val resized = original.resizedBy(ComicPanelCorner.TOP_LEFT, 100f, 100f, pageWidth, pageHeight)
        assertEquals(600f, resized.x, 0.001f)
        assertEquals(700f, resized.y, 0.001f)
        assertEquals(900f, resized.right, 0.001f)
        assertEquals(900f, resized.bottom, 0.001f)
    }

    @Test
    fun `尾巴尖只能拉到泡身下方、且不出页`() {
        val original = bubble()
        // 往上拖（拖进泡身里）：轮廓画不出"从底边往上"的尾巴，所以要被压在底边下面 ✓
        val up = original.tailDraggedTo(500f, 10f, pageWidth, pageHeight)
        assertTrue("尾巴尖必须在泡身下方", up.tailY!! > up.bottom)
        // 往右下拖到页外：夹在页内
        val out = original.tailDraggedTo(99999f, 99999f, pageWidth, pageHeight)
        assertEquals(pageWidth.toFloat(), out.tailX!!, 0.01f)
        assertEquals(pageHeight.toFloat(), out.tailY!!, 0.01f)
    }

    @Test
    fun `换更小的底板时气泡与文本都被夹回页内`() {
        val big = bubble(x = 1500f, y = 900f, w = 700f, h = 400f).copy(tailX = 2300f, tailY = 1300f)
        val clamped = big.clampedTo(1024, 1024)
        assertTrue(clamped.w <= 1024f)
        assertTrue(clamped.h <= 1024f)
        assertTrue(clamped.x >= 0f && clamped.right <= 1024.01f)
        assertTrue(clamped.y >= 0f && clamped.bottom <= 1024.01f)
        assertTrue("尾巴尖也要夹回页内", clamped.tailX!! <= 1024f)

        val text = ComicText("t", 2000f, 2000f, 400f, 200f)
        val textClamped = text.clampedTo(1024, 1024)
        assertTrue(textClamped.x >= 0f && textClamped.right <= 1024.01f)
        assertTrue(textClamped.y >= 0f && textClamped.bottom <= 1024.01f)
    }

    // -----------------------------------------------------------------------
    // JSON
    // -----------------------------------------------------------------------

    @Test
    fun `气泡的 JSON 往返一模一样`() {
        val original = ComicBubble(
            id = "b1",
            x = 12.5f,
            y = 34.25f,
            w = 600f,
            h = 400f,
            style = ComicBubbleStyles.SHOUT,
            tailX = 400f,
            tailY = 720f,
            text = "喂！",
            fontSize = 88f,
            colorArgb = 0xFFE53935.toInt(),
            align = ComicTextAlign.END,
            fontFile = "my-font.ttf",
            stroke = true,
        )
        val back = ComicBubble.fromJson(JSONObject(original.toJson().toString()))
        assertEquals(original, back)
        assertTrue("描边要存下来", back!!.stroke)
    }

    @Test
    fun `文本的 JSON 往返一模一样`() {
        val original = ComicText(
            id = "t1",
            x = 10f,
            y = 20f,
            w = 500f,
            h = 200f,
            text = "旁白",
            fontSize = 72f,
            colorArgb = 0xFF1E88E5.toInt(),
            align = ComicTextAlign.CENTER,
            fontFile = "another.otf",
        )
        assertEquals(original, ComicText.fromJson(JSONObject(original.toJson().toString())))
    }

    @Test
    fun `认不出的样式回落圆泡、退化的宽高直接丢`() {
        val broken = ComicBubble.fromJson(JSONObject("""{"id":"b","w":10,"h":10,"style":"WAT"}"""))
        assertEquals(ComicBubbleStyles.ROUND, broken!!.style)
        assertEquals("缺 fontSize 时用默认值", 64f, broken.fontSize, 0.001f)
        assertEquals("缺 align 时居中对齐", ComicTextAlign.CENTER, broken.align)
        assertFalse("缺 stroke 时就是不描边", broken.stroke)

        assertNull("宽为 0 的气泡画不出来也点不中，直接丢", ComicBubble.fromJson(JSONObject("""{"id":"b","w":0,"h":10}""")))
        assertNull(ComicBubble.fromJson(JSONObject("""{"w":10,"h":10}""")))
        assertNull(ComicText.fromJson(JSONObject("""{"id":"t","w":10,"h":0}""")))
        assertNull(ComicBubble.fromJson(null))

        // 界面那边解析样式 id：认不出来 / null 都回落圆泡（不能崩 ✗）
        assertEquals(BubbleStyle.THOUGHT, bubbleStyleOf("THOUGHT"))
        assertEquals(BubbleStyle.ROUND, bubbleStyleOf("wat"))
        assertEquals(BubbleStyle.ROUND, bubbleStyleOf(null))
    }

    @Test
    fun `一页 JSON 里带着气泡 文本 图层、往返一模一样`() {
        val page = ComicBoardPage(
            id = "comic-page-1",
            name = "第 1 页",
            baseWidth = pageWidth,
            baseHeight = pageHeight,
            panels = listOf(ComicPanel("p1", 10f, 10f, 500f, 500f, order = 0)),
            bubbles = listOf(
                bubble("b1").copy(text = "你好", fontFile = "my.ttf", tailX = 400f, tailY = 700f),
            ),
            texts = listOf(
                ComicText("t1", 30f, 40f, 500f, 200f, text = "旁白", align = ComicTextAlign.END),
            ),
        ).normalizedOverlays()

        val back = ComicBoardStore.decodePages(ComicBoardStore.encodePages(listOf(page))).first()
        assertEquals(page.panels, back.panels)
        assertEquals(page.bubbles, back.bubbles)
        assertEquals(page.texts, back.texts)
        assertEquals(page.layers, back.layers)
    }

    @Test
    fun `坏 JSON 与老数据都不崩`() {
        assertEquals(0, decodeBubbleArray(null).size)
        assertEquals(0, decodeTextArray(null).size)
        assertEquals(0, decodeLayerArray(null).size)
        // 老版本存的页（没有 bubbles / texts / layers 三个键）：读回来只是"还没有气泡"，
        // 但要补上最底下那一层底板（图层列表里得说得清谁在最下面 ✓）
        val old = ComicBoardPage.fromJson(
            JSONObject("""{"id":"p","name":"旧页","baseWidth":1024,"baseHeight":1024,"panels":[]}"""),
        )!!
        assertEquals(0, old.bubbles.size)
        assertEquals(0, old.texts.size)
        assertEquals(1, old.layers.size)
        assertEquals(ComicLayer.Kind.BASE, old.layers.first().kind)
    }

    @Test
    fun `图层 JSON 往返一模一样`() {
        val layer = ComicLayer(
            id = "b1",
            name = "气泡",
            kind = ComicLayer.Kind.BUBBLE,
            panelId = "p1",
            imagePath = null,
            visible = false,
            opacity = 0.5f,
        )
        val back = comicLayerFromJson(JSONObject(layer.toJson().toString()))!!
        assertEquals(layer.id, back.id)
        assertEquals(layer.name, back.name)
        assertEquals(layer.kind, back.kind)
        assertEquals(layer.panelId, back.panelId)
        assertFalse("隐藏要存下来", back.visible)
        assertEquals(0.5f, back.opacity, 0.001f)
    }

    // -----------------------------------------------------------------------
    // 图层叠对齐
    // -----------------------------------------------------------------------

    @Test
    fun `图层叠会被对齐到这一页实际有什么`() {
        val page = ComicBoardPage(
            id = "p",
            name = "第 1 页",
            baseWidth = pageWidth,
            baseHeight = pageHeight,
            bubbles = listOf(bubble("b1"), bubble("b2")),
            // 盘上可能长这样：有一条指向不存在的气泡的幽灵层；b1 那一层还漏了
            layers = listOf(
                ComicLayer("ghost", "幽灵", ComicLayer.Kind.BUBBLE),
                ComicLayer("b2", "气泡", ComicLayer.Kind.BUBBLE),
            ),
        )
        val fixed = page.normalizedOverlays()

        assertEquals("底板层要在最底下", ComicLayer.Kind.BASE, fixed.layers.first().kind)
        assertEquals(
            "幽灵层丢掉、缺的那一层补在最上面",
            listOf("b2", "b1"),
            fixed.layers.filter { it.kind == ComicLayer.Kind.BUBBLE }.map { it.id },
        )
        assertTrue(fixed.layers.none { it.id == "ghost" })
        assertEquals(3, fixed.layers.size)
    }

    @Test
    fun `图层叠对齐是幂等的、也没有重复层`() {
        val page = ComicBoardPage(
            id = "p",
            name = "第 1 页",
            baseWidth = pageWidth,
            baseHeight = pageHeight,
            bubbles = listOf(bubble("b1")),
            texts = listOf(ComicText("t1", 0f, 0f, 100f, 100f)),
            layers = listOf(
                ComicLayer("base-x", "底板", ComicLayer.Kind.BASE),
                ComicLayer("base-y", "底板", ComicLayer.Kind.BASE),
                ComicLayer("b1", "气泡", ComicLayer.Kind.BUBBLE),
                ComicLayer("b1", "气泡", ComicLayer.Kind.BUBBLE),
            ),
        )
        val once = page.normalizedOverlays()
        val twice = once.normalizedOverlays()
        assertEquals("底板只留一层", 1, once.layers.count { it.kind == ComicLayer.Kind.BASE })
        assertEquals("同一个 id 只留一次", 3, once.layers.size)
        assertEquals(once.layers, twice.layers)
        assertNotNull(once.layers.firstOrNull { it.id == "t1" })
    }

    @Test
    fun `缺尾巴样式没有尾巴、方形旁白也不长尾`() {
        val rounded = bubble().copy(tailX = 400f, tailY = 800f)
        assertEquals(400f to 800f, rounded.tail)
        val cleared = rounded.withTailCleared()
        assertNull("收掉尾巴之后两个坐标都要清", cleared.tail)
    }

    @Test
    fun `样式的尾巴开关与四种 id 都在`() {
        assertEquals(4, ComicBubbleStyles.all.size)
        ComicBubbleStyles.all.forEach { id ->
            assertTrue("$id 应当是认得的样式 id", ComicBubbleStyles.isKnown(id))
        }
        assertFalse(ComicBubbleStyles.isKnown("ROUND2"))
    }
}
