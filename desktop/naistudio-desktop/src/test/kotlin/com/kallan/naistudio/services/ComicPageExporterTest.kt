package com.kallan.naistudio.services

import com.kallan.naistudio.models.ComicBoardPage
import com.kallan.naistudio.models.ComicBubble
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.models.ComicPanel
import com.kallan.naistudio.models.normalizedOverlays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **拼页导出的几何**（高级漫画第 ⑦ 项）—— 全是纯函数 ✓（不碰平台、不碰文件 ✓）。
 *
 * 钉住的都是"错了就会导出一张看着不对的图"的地方：
 *  · 页尺寸 = 底板 与 格子并集 取大 ✓（格子被拖到底板外面时**不能裁掉它** ✗）；
 *  · 底板层 **Fit**（和画布预览的 `ContentScale.Fit` 同一口径 ✓）、
 *    生成层 **Cover 进它自己那一格**（和预览里贴图的 `ContentScale.Crop` 同一口径 ✓）；
 *  · 隐藏 / 全透明的层**不画** ✓；指向不存在格子 / 解不开的图的层**跳过** ✓（别静默画错 ✗）；
 *  · **顺序就是图层叠顺序** ✓（气泡夹在两层位图中间时，合成也必须夹在中间 ✗ 不能全丢到最上面）。
 */
class ComicPageExporterTest {

    private val imageSizes = mapOf(
        "base.png" to (1000 to 2000),
        "base-wide.png" to (500 to 2000),
        "gen.png" to (600 to 300),
        "other.png" to (300 to 300),
    )

    private val sizeOf: (String) -> Pair<Int, Int>? = { imageSizes[it] }

    private fun page(
        layers: List<ComicLayer>,
        panels: List<ComicPanel> = emptyList(),
        basePath: String? = "base.png",
        width: Int = 1000,
        height: Int = 2000,
        bubbles: List<ComicBubble> = emptyList(),
        texts: List<com.kallan.naistudio.models.ComicText> = emptyList(),
    ) = ComicBoardPage(
        id = "p1",
        name = "第 1 页",
        basePath = basePath,
        baseWidth = width,
        baseHeight = height,
        panels = panels,
        bubbles = bubbles,
        texts = texts,
        layers = layers,
    )

    private fun baseLayer(path: String? = "base.png") =
        ComicLayer("base", "底板", ComicLayer.Kind.BASE, imagePath = path)

    @Test
    fun page_size_follows_the_base_when_every_panel_is_inside_it() {
        val panels = listOf(ComicPanel("pan1", 100f, 200f, 300f, 400f))
        assertEquals(1000 to 2000, ComicPageExporter.pageSize(page(emptyList(), panels)))
    }

    @Test
    fun page_size_grows_so_a_panel_outside_the_base_is_never_cropped() {
        // 盘上被手改过（格子跑到 100×100 的底板外面）：宁可多一圈纸色，也不能把格子裁掉 ✓
        val panels = listOf(ComicPanel("pan1", 0f, 0f, 300f, 150f))
        assertEquals(300 to 150, ComicPageExporter.pageSize(page(emptyList(), panels, width = 100, height = 100)))
    }

    @Test
    fun empty_page_is_1x1_not_zero() {
        // 0 宽高开不出位图 ✗（`ComicLayout.pageBounds` 与这里都是这个口径 ✓）
        assertEquals(1000 to 2000, ComicPageExporter.pageSize(page(emptyList())))
    }

    @Test
    fun base_layer_is_fit_into_the_whole_page() {
        val plan = ComicPageExporter.plan(page(listOf(baseLayer())), sizeOf)
        val step = plan.steps.single() as ComicPageExporter.Step.Bitmap
        assertEquals(ComicLayer.Kind.BASE, step.kind)
        assertEquals(0, step.x)
        assertEquals(0, step.y)
        assertEquals(1000, step.width)
        assertEquals(2000, step.height)
    }

    @Test
    fun a_narrower_base_is_fit_and_centered() {
        val plan = ComicPageExporter.plan(page(listOf(baseLayer("base-wide.png"))), sizeOf)
        val step = plan.steps.single() as ComicPageExporter.Step.Bitmap
        // 500×2000 塞进 1000×2000：等比按高算（scale = 1）→ 宽只剩 500，左右各留 250 ✓
        assertEquals(250, step.x)
        assertEquals(0, step.y)
        assertEquals(500, step.width)
        assertEquals(2000, step.height)
    }

    @Test
    fun generated_layer_is_covered_into_its_own_panel() {
        val panels = listOf(ComicPanel("pan1", 100f, 100f, 300f, 300f))
        val layers = listOf(
            baseLayer(),
            ComicLayer("gen1", "生成层", ComicLayer.Kind.GENERATED, panelId = "pan1", imagePath = "gen.png"),
        )
        val plan = ComicPageExporter.plan(page(layers, panels), sizeOf)
        val step = plan.steps.last() as ComicPageExporter.Step.Bitmap
        // 600×300 铺满 300×300 的格子（cover）：等比按宽算到 1:2 → 高 300，宽 600 → 左右各溢出 150 ✓
        assertEquals(-50, step.x)
        assertEquals(100, step.y)
        assertEquals(600, step.width)
        assertEquals(300, step.height)
    }

    @Test
    fun hidden_and_transparent_layers_are_not_drawn() {
        val hidden = ComicLayer("gen-h", "生成层", ComicLayer.Kind.GENERATED, panelId = "pan1", imagePath = "gen.png")
            .apply { visible = false }
        val transparent = ComicLayer("gen-t", "生成层", ComicLayer.Kind.GENERATED, panelId = "pan1", imagePath = "gen.png")
            .apply { opacity = 0f }
        val panels = listOf(ComicPanel("pan1", 0f, 0f, 300f, 300f))
        val plan = ComicPageExporter.plan(page(listOf(baseLayer(), hidden, transparent), panels), sizeOf)
        assertEquals(listOf("base"), plan.steps.map { it.layerId })
    }

    @Test
    fun steps_keep_the_exact_stack_order_with_overlays_in_between() {
        val panels = listOf(ComicPanel("pan1", 0f, 0f, 300f, 300f))
        val bubble = ComicBubble("bub1", 10f, 10f, 100f, 60f)
        val layers = listOf(
            baseLayer(),
            ComicLayer("bub1", "气泡", ComicLayer.Kind.BUBBLE),
            ComicLayer("gen1", "生成层", ComicLayer.Kind.GENERATED, panelId = "pan1", imagePath = "gen.png"),
            ComicLayer("txt1", "文本", ComicLayer.Kind.TEXT),
        )
        val plan = ComicPageExporter.plan(
            page(layers, panels, bubbles = listOf(bubble), texts = listOf(com.kallan.naistudio.models.ComicText("txt1", 0f, 0f, 100f, 40f))),
            sizeOf,
        )
        // 顺序 = 叠顺序（气泡在下、文本在上）——**不许**把矢量层统一挪到最上面 ✗
        assertEquals(listOf("base", "bub1", "gen1", "txt1"), plan.steps.map { it.layerId })
        assertTrue(plan.steps[1] is ComicPageExporter.Step.Overlay)
        assertTrue(plan.steps[3] is ComicPageExporter.Step.Overlay)
    }

    @Test
    fun layers_with_missing_panel_or_unreadable_image_are_skipped() {
        val panels = listOf(ComicPanel("pan1", 0f, 0f, 300f, 300f))
        val layers = listOf(
            baseLayer(),
            // 指向一个不存在的格子（气泡被删干净了之类的盘上脏数据 ✓）
            ComicLayer("gen-gone", "生成层", ComicLayer.Kind.GENERATED, panelId = "pan-x", imagePath = "gen.png"),
            // 图读不出来（文件没了 / 尺寸非法 → imageSizeOf 给 null ✓）
            ComicLayer("gen-bad", "生成层", ComicLayer.Kind.GENERATED, panelId = "pan1", imagePath = "missing.png"),
            // 生成层没有图路径（坏数据 ✓）
            ComicLayer("gen-null", "生成层", ComicLayer.Kind.GENERATED, panelId = "pan1", imagePath = null),
        )
        val plan = ComicPageExporter.plan(page(layers, panels), sizeOf)
        assertEquals(listOf("base"), plan.steps.map { it.layerId })
    }

    @Test
    fun a_blank_canvas_base_layer_without_an_image_is_skipped() {
        // 空白画布（还没导入图）：底板层没有图 → 不产生位图步骤，只留纸色 ✓
        val layers = listOf(baseLayer(path = null))
        val plan = ComicPageExporter.plan(page(layers, basePath = null), sizeOf)
        assertTrue(plan.steps.isEmpty())
        assertTrue(!plan.hasBitmap)
    }

    @Test
    fun bitmap_placements_keep_only_the_bitmap_steps() {
        val panels = listOf(ComicPanel("pan1", 0f, 0f, 300f, 300f))
        val layers = listOf(
            baseLayer(),
            ComicLayer("txt1", "文本", ComicLayer.Kind.TEXT),
            ComicLayer("gen1", "生成层", ComicLayer.Kind.GENERATED, panelId = "pan1", imagePath = "gen.png"),
        )
        val plan = ComicPageExporter.plan(page(layers, panels), sizeOf)
        val placements = ComicPageExporter.bitmapPlacements(plan.steps)
        assertEquals(listOf("base.png", "gen.png"), placements.map { it.imagePath })
    }

    @Test
    fun opacity_rides_along_to_the_placement() {
        val semi = ComicLayer("gen1", "生成层", ComicLayer.Kind.GENERATED, panelId = "pan1", imagePath = "gen.png")
            .apply { opacity = 0.4f }
        val panels = listOf(ComicPanel("pan1", 0f, 0f, 300f, 300f))
        val plan = ComicPageExporter.plan(page(listOf(baseLayer(), semi), panels), sizeOf)
        assertEquals(0.4f, ComicPageExporter.bitmapPlacements(plan.steps).last().opacity, 0.0001f)
    }
}

/**
 * **生成层不能被"对齐图层叠"那一步扫掉** —— 第 ⑥ 项"每生成一张 = 新图层"的地基。
 *
 * 真事故：`normalizedOverlays()` 原来只留"底板 + 有主的气泡/文本"，而生成层的主人
 * 是**格子**（`panelId`）不是气泡 ✗ → 每次读盘 / 加一个气泡都会把生成层**静默删光** ✗
 * （用户在图层列表里刚看见的"生成层"，下次进页面就没了 ✓）。
 */
class ComicOverlayNormalizeGeneratedTest {

    @Test
    fun normalized_overlays_keeps_generated_layers() {
        val layers = listOf(
            ComicLayer("base", "底板", ComicLayer.Kind.BASE, imagePath = "base.png"),
            ComicLayer("gen1", "生成层", ComicLayer.Kind.GENERATED, panelId = "pan1", imagePath = "gen.png"),
        )
        val page = ComicBoardPage(
            id = "p1",
            name = "第 1 页",
            basePath = "base.png",
            baseWidth = 1000,
            baseHeight = 2000,
            panels = listOf(ComicPanel("pan1", 0f, 0f, 300f, 300f)),
            layers = layers,
        )
        val normalized = page.normalizedOverlays()
        assertEquals(
            "生成层是「每生成一张 = 新图层」那条口径存下来的，对齐时不许扫掉 ✗",
            listOf("base", "gen1"),
            normalized.layers.map { it.id },
        )
    }

    @Test
    fun normalized_overlays_still_drops_ghost_bubble_layers() {
        val layers = listOf(
            ComicLayer("base", "底板", ComicLayer.Kind.BASE, imagePath = "base.png"),
            ComicLayer("ghost", "气泡", ComicLayer.Kind.BUBBLE),
        )
        val page = ComicBoardPage(
            id = "p1",
            name = "第 1 页",
            basePath = "base.png",
            baseWidth = 1000,
            baseHeight = 2000,
            layers = layers,
        )
        assertEquals(listOf("base"), page.normalizedOverlays().layers.map { it.id })
    }
}
