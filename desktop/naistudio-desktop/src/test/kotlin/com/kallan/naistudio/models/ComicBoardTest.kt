package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **高级漫画模式（docs/43 · M1）纯函数单测**：模板几何、拖动 / 缩放夹取、JSON 往返。
 *
 * 这几样都是"看着对、其实会出洋相"的东西，必须机械验证：
 *  1. 模板算出来的格子**必须在页内、互不重叠**（不然铺完就得手改回页内 ✗）；
 *  2. 拖动 / 缩放**永远不越页、不小于最小边长**（跑出页外就再也点不中了 ✗）；
 *  3. 换底板（尤其是换成更小的画布）时，格子要**被夹回页内**；
 *  4. 落盘是 JSON，**读回来要一模一样**（"关掉再打开要还在"这条就靠它 ✓）。
 */
class ComicBoardTest {

    private val pageWidth = 2400
    private val pageHeight = 3200

    private fun page(panels: List<ComicPanel> = emptyList()) = ComicBoardPage(
        id = "comic-page-1",
        name = "第 1 页",
        basePath = null,
        baseWidth = pageWidth,
        baseHeight = pageHeight,
        panels = panels,
    )

    // -----------------------------------------------------------------------
    // 模板
    // -----------------------------------------------------------------------

    @Test
    fun `四个模板的格数分别是 2、3、4、6`() {
        assertEquals(2, ComicPanelTemplates.byId(ComicPanelTemplates.TWO_VERTICAL)?.count)
        assertEquals(3, ComicPanelTemplates.byId(ComicPanelTemplates.THREE_HORIZONTAL)?.count)
        assertEquals(4, ComicPanelTemplates.byId(ComicPanelTemplates.FOUR_GRID)?.count)
        assertEquals(6, ComicPanelTemplates.byId(ComicPanelTemplates.SIX_GRID)?.count)
    }

    @Test
    fun `铺出来的格子都在页内、面积为正、序号从 0 连号`() {
        ComicPanelTemplates.all.forEach { template ->
            val panels = ComicPanelTemplates.build(template, pageWidth, pageHeight, "t1")
            assertEquals("${template.id} 的格数", template.count, panels.size)
            assertEquals("${template.id} 的序号", (0 until template.count).toList(), panels.map { it.order })
            panels.forEach { panel ->
                assertTrue("${template.id} 越左界", panel.x >= 0f)
                assertTrue("${template.id} 越上界", panel.y >= 0f)
                assertTrue("${template.id} 越右界", panel.right <= pageWidth + 0.01f)
                assertTrue("${template.id} 越下界", panel.bottom <= pageHeight + 0.01f)
                assertTrue("${template.id} 有非正宽", panel.w > 0f)
                assertTrue("${template.id} 有非正高", panel.h > 0f)
            }
        }
    }

    @Test
    fun `模板格子互不重叠`() {
        ComicPanelTemplates.all.forEach { template ->
            val panels = ComicPanelTemplates.build(template, pageWidth, pageHeight, "t2")
            for (i in panels.indices) {
                for (j in i + 1 until panels.size) {
                    val a = panels[i]
                    val b = panels[j]
                    val overlapX = minOf(a.right, b.right) - maxOf(a.x, b.x)
                    val overlapY = minOf(a.bottom, b.bottom) - maxOf(a.y, b.y)
                    assertTrue(
                        "${template.id} 的第 $i / $j 格重叠了",
                        overlapX <= 0.01f || overlapY <= 0.01f,
                    )
                }
            }
        }
    }

    @Test
    fun `2 格竖排是上下两块通栏、3 格横排是并排三条`() {
        val vertical = ComicPanelTemplates.build(
            ComicPanelTemplates.byId(ComicPanelTemplates.TWO_VERTICAL)!!,
            pageWidth,
            pageHeight,
            "v",
        )
        assertEquals("竖排两格的 x 应该一样", vertical[0].x, vertical[1].x, 0.01f)
        assertEquals("竖排两格的宽应该一样", vertical[0].w, vertical[1].w, 0.01f)
        assertTrue("竖排第二格应该在第一格下面", vertical[1].y > vertical[0].bottom)

        val horizontal = ComicPanelTemplates.build(
            ComicPanelTemplates.byId(ComicPanelTemplates.THREE_HORIZONTAL)!!,
            pageWidth,
            pageHeight,
            "h",
        )
        assertEquals("横排三格的 y 应该一样", horizontal[0].y, horizontal[2].y, 0.01f)
        assertTrue("横排第二格应该在右边", horizontal[1].x > horizontal[0].right)
        assertTrue("横排第三格应该在更右边", horizontal[2].x > horizontal[1].right)
    }

    @Test
    fun `格子留边用常量控制 —— 模板留出页边`() {
        val panels = ComicPanelTemplates.build(
            ComicPanelTemplates.byId(ComicPanelTemplates.TWO_VERTICAL)!!,
            pageWidth,
            pageHeight,
            "m",
        )
        val margin = ComicBoardMetrics.pageMargin(pageWidth)
        assertEquals(margin, panels[0].x, 0.01f)
        assertEquals(margin, panels[0].y, 0.01f)
        assertEquals(pageWidth - margin, panels[0].right, 0.01f)
    }

    // -----------------------------------------------------------------------
    // 拖动 / 缩放
    // -----------------------------------------------------------------------

    @Test
    fun `拖动不会把格子拖出页外`() {
        val panel = ComicPanel(id = "p", x = 100f, y = 100f, w = 400f, h = 300f)
        val far = panel.movedBy(-9999f, -9999f, pageWidth, pageHeight)
        assertEquals(0f, far.x, 0.001f)
        assertEquals(0f, far.y, 0.001f)
        assertEquals(400f, far.w, 0.001f)
        assertEquals(300f, far.h, 0.001f)

        val beyond = panel.movedBy(9999f, 9999f, pageWidth, pageHeight)
        assertEquals((pageWidth - 400).toFloat(), beyond.x, 0.001f)
        assertEquals((pageHeight - 300).toFloat(), beyond.y, 0.001f)
    }

    @Test
    fun `拖右下角缩放时左上角不动、且不超最小边长`() {
        val panel = ComicPanel(id = "p", x = 500f, y = 600f, w = 400f, h = 400f)
        val bigger = panel.resizedBy(ComicPanelCorner.BOTTOM_RIGHT, 100f, 50f, pageWidth, pageHeight)
        assertEquals(500f, bigger.x, 0.001f)
        assertEquals(600f, bigger.y, 0.001f)
        assertEquals(500f, bigger.w, 0.001f)
        assertEquals(450f, bigger.h, 0.001f)

        // 往反方向拽到底：宽高被夹在最小边长上，不会变成 0 或负数
        val tiny = panel.resizedBy(ComicPanelCorner.BOTTOM_RIGHT, -9999f, -9999f, pageWidth, pageHeight)
        val minSide = ComicBoardMetrics.minPanelSide(pageWidth)
        assertEquals(500f, tiny.x, 0.001f)
        assertEquals(600f, tiny.y, 0.001f)
        assertEquals(minSide, tiny.w, 0.01f)
        assertEquals(minSide, tiny.h, 0.01f)
    }

    @Test
    fun `拖左上角缩放时右下角不动`() {
        val panel = ComicPanel(id = "p", x = 500f, y = 600f, w = 400f, h = 300f)
        val resized = panel.resizedBy(ComicPanelCorner.TOP_LEFT, 100f, 100f, pageWidth, pageHeight)
        assertEquals(600f, resized.x, 0.001f)
        assertEquals(700f, resized.y, 0.001f)
        assertEquals(300f, resized.w, 0.001f)
        assertEquals(200f, resized.h, 0.001f)
        assertEquals(900f, resized.right, 0.001f)
        assertEquals(900f, resized.bottom, 0.001f)
    }

    @Test
    fun `换更小的底板时格子被夹回页内`() {
        val panel = ComicPanel(id = "p", x = 2000f, y = 3000f, w = 1200f, h = 1600f)
        val clamped = panel.clampedTo(1024, 1024)
        assertTrue("宽应该被压到页内", clamped.w <= 1024f)
        assertTrue("高应该被压到页内", clamped.h <= 1024f)
        assertTrue(clamped.x >= 0f && clamped.right <= 1024f + 0.01f)
        assertTrue(clamped.y >= 0f && clamped.bottom <= 1024f + 0.01f)
    }

    // -----------------------------------------------------------------------
    // 序号
    // -----------------------------------------------------------------------

    @Test
    fun `删格之后序号压成 0 到 n-1、上移下移能换位`() {
        val panels = listOf(
            ComicPanel("a", 0f, 0f, 100f, 100f, order = 0),
            ComicPanel("b", 0f, 0f, 100f, 100f, order = 1),
            ComicPanel("c", 0f, 0f, 100f, 100f, order = 2),
        )
        val afterDelete = panels.filterNot { it.id == "b" }.normalizePanelOrder()
        assertEquals(listOf("a", "c"), afterDelete.map { it.id })
        assertEquals(listOf(0, 1), afterDelete.map { it.order })

        val moved = panels.withPanelMoved("c", -1)
        assertEquals(listOf("a", "c", "b"), moved.map { it.id })
        assertEquals(listOf(0, 1, 2), moved.map { it.order })

        // 到边界就原样返回（不抛、不绕回去）
        assertEquals(listOf("a", "b", "c"), panels.withPanelMoved("a", -1).map { it.id })
        assertEquals(listOf("a", "b", "c"), panels.withPanelMoved("c", 1).map { it.id })
    }

    // -----------------------------------------------------------------------
    // 目标像素（只读显示）
    // -----------------------------------------------------------------------

    @Test
    fun `目标像素走 InpaintSize —— 3 比 1 的格子显示 1792×576`() {
        val wide = ComicPanel("p", 0f, 0f, 300f, 100f)
        assertEquals("1792×576", wide.targetPixelText())
        val square = ComicPanel("q", 0f, 0f, 200f, 200f)
        assertEquals("1024×1024", square.targetPixelText())
    }

    // -----------------------------------------------------------------------
    // 落盘（JSON）
    // -----------------------------------------------------------------------

    @Test
    fun `一页 JSON 往返之后一模一样`() {
        val original = page(
            panels = listOf(
                ComicPanel("p1", 12.5f, 34.25f, 400f, 300f, prompt = "第一格", order = 0),
                ComicPanel("p2", 500f, 800f, 900f, 1200f, order = 1),
            ),
        ).copy(basePath = "/tmp/base.png")

        val restored = ComicBoardStore.decodePages(ComicBoardStore.encodePages(listOf(original)))

        assertEquals(1, restored.size)
        val back = restored.first()
        assertEquals(original.id, back.id)
        assertEquals(original.name, back.name)
        assertEquals("/tmp/base.png", back.basePath)
        assertEquals(original.baseWidth, back.baseWidth)
        assertEquals(original.baseHeight, back.baseHeight)
        assertEquals(original.panels, back.panels)
    }

    @Test
    fun `坏 JSON 与退化数据不会崩`() {
        assertEquals(0, ComicBoardStore.decodePages(null).size)
        assertEquals(0, ComicBoardStore.decodePages("").size)
        assertEquals(0, ComicBoardStore.decodePages("{这不是 JSON").size)
        // 宽高为 0 的格子读回来应该被丢掉（画不出来也点不中）
        assertNull(ComicPanel.fromJson(org.json.JSONObject("""{"id":"p","w":0,"h":0}""")))
        // 缺 id 的页 / 格子直接丢
        assertNull(ComicBoardPage.fromJson(org.json.JSONObject("""{"name":"x"}""")))
    }

    @Test
    fun `读盘时序号会被重排、格子按序号取`() {
        val restored = ComicBoardStore.decodePages(
            ComicBoardStore.encodePages(
                listOf(
                    page(
                        panels = listOf(
                            ComicPanel("late", 0f, 0f, 100f, 100f, order = 5),
                            ComicPanel("early", 0f, 0f, 100f, 100f, order = 1),
                        ),
                    ),
                ),
            ),
        ).first()

        assertEquals(listOf("early", "late"), restored.orderedPanels().map { it.id })
        assertNotNull(restored.orderedPanels().firstOrNull())
    }
}
