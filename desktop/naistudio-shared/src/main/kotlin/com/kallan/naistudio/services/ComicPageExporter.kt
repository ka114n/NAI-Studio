package com.kallan.naistudio.services

import androidx.compose.ui.geometry.Rect
import com.kallan.naistudio.models.ComicBoardPage
import com.kallan.naistudio.models.ComicLayer
import kotlin.math.roundToInt

/**
 * **拼页导出（高级漫画第 ⑦ 项）的几何部分** —— 用户 2026-09-26 方案 §4 M4：
 * 「所有格子出图后拼成整页 → 进图库 / 导出 PNG」✓。
 *
 * 这里**只有纯函数** ✓（不碰平台、不碰文件 → 有单测 ✓）。真正吃像素的是
 * [ComicComposer.compose]（它在合成那一刻才去解码 / 缩放 / 编码 ✓），
 * 而"每一层落在页上哪一块"由本文件说了算 ✓ —— 和画布预览**同一套公式** ✓：
 *
 * | 层 | 落点 | 为什么 |
 * |---|---|---|
 * | 底板 [ComicLayer.Kind.BASE] | [ComicLayout.fit] 铺满整页 | 画布预览里底板那张图就是 `ContentScale.Fit` ✓ |
 * | **绘画层 [ComicLayer.Kind.PAINT]** | [ComicLayout.fit] 铺满整页 | 它和底板**同一个坐标空间**（页大小、全透明底 ✓）—— 预览里就是叠在纸上的那一层 ✓ |
 * | 生成层 [ComicLayer.Kind.GENERATED] | [ComicLayout.cover] 进**它自己那一格** | 预览里格子里贴的是 `ContentScale.Crop` ✓ |
 * | 气泡 / 文本 | 矢量 → 由电脑端 `ImageComposeScene` 栅格化成整页大小的位图 ✓ | 放大不糊 ✓（这里只报"该栅格化哪一层"✓） |
 *
 * ## 页尺寸怎么定（`ComicLayout.pageBounds` 用在这儿 ✓）
 *
 * 一页的可见范围 = **底板那块画布** 和 **所有格子的并集** 里大的那个：
 *  · 正常情况下格子都被夹在底板里（`ComicPanel.clampedTo` ✓），于是页尺寸就是底板尺寸 ✓；
 *  · 万一盘上有个跑到底板外面的格子（手改过 / 老数据），按并集放大画布，
 *    **宁可多一圈纸色，也不要把格子裁掉** ✓（`pageBounds` 本来就是这个语义 ✓）。
 */
object ComicPageExporter {

    /** 合成顺序里的一步（**下标 0 = 最底下** ✓，和 `ComicLayerStack.all` 一个方向 ✓）。 */
    sealed class Step {
        abstract val layerId: String
        abstract val kind: ComicLayer.Kind

        /** 投影出来的透明度 ✓（位图交给 `BitmapPlacement.opacity`；矢量在栅格化时由调用方保证一致 ✓）。 */
        abstract val opacity: Float

        /** **位图层**：图在哪 + 落在页上的哪个矩形（像素整数 ✓）。 */
        data class Bitmap(
            override val layerId: String,
            override val kind: ComicLayer.Kind,
            val imagePath: String,
            val x: Int,
            val y: Int,
            val width: Int,
            val height: Int,
            override val opacity: Float,
        ) : Step()

        /** **矢量层**（气泡 / 文本）：先由调用方栅格化成"整页大小、透明底"的位图再叠 ✓。 */
        data class Overlay(
            override val layerId: String,
            override val kind: ComicLayer.Kind,
            override val opacity: Float,
        ) : Step()
    }

    /**
     * 一次导出的完整计划。
     *
     * @param pageWidth/pageHeight 出口 PNG 的像素尺寸 ✓
     * @param steps **从下到上**排好的合成步骤 ✓（顺序 = 图层叠顺序 ✓ —— 谁盖谁由它说了算 ✓）
     */
    data class Plan(
        val pageWidth: Int,
        val pageHeight: Int,
        val steps: List<Step>,
    ) {
        /** 有没有位图层（底板 / 生成层 / **绘画层**）—— 一个都没有时"压平"没意义 ✓（调用方据此提示 ✓）。 */
        val hasBitmap: Boolean get() = steps.any { it is Step.Bitmap }
    }

    /** 一页的出口尺寸：底板 与 格子并集 取大 ✓（空页给 1×1 —— 别给 0 ✗，位图开不出来 ✓）。 */
    fun pageSize(page: ComicBoardPage): Pair<Int, Int> {
        val (unionW, unionH) = ComicLayout.pageBounds(page.panels.map { it.rect() })
        val width = maxOf(page.baseWidth, unionW)
        val height = maxOf(page.baseHeight, unionH)
        return width.coerceAtLeast(1) to height.coerceAtLeast(1)
    }

    /**
     * 排一次合成的步骤 ✓。
     *
     * @param imageSizeOf 读一张图的像素尺寸（**只读文件头**就够 ✓ —— 调用方给 `ImageIo.size` ✓；
     *   解不开返回 null → 这一层跳过，**不静默画错** ✓）。
     */
    fun plan(page: ComicBoardPage, imageSizeOf: (String) -> Pair<Int, Int>?): Plan {
        val (pageWidth, pageHeight) = pageSize(page)
        val pageRect = Rect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat())

        val steps = ArrayList<Step>(page.layers.size)
        page.layers.forEach { layer ->
            // 隐藏 / 全透明：**不画**（和界面上 `ComicLayerStack.visibleLayers` 一个口径 ✓）
            if (!layer.visible || layer.opacity <= 0f) return@forEach
            when (layer.kind) {
                ComicLayer.Kind.BUBBLE,
                ComicLayer.Kind.TEXT,
                -> steps += Step.Overlay(layer.id, layer.kind, layer.opacity)

                ComicLayer.Kind.BASE,
                ComicLayer.Kind.PAINT,
                -> {
                    // 底板层的图；老数据里底板层可能没写图（合成层就是 null）→ 退回页自己的 basePath ✓
                    // ⚠️ **绘画层没有这条退路** ✗：它没有"页的 basePath"可退（那会张冠李戴地
                    //    把底板图当成绘画层再叠一遍 ✓）—— 没有图的绘画层（新建了还没画）
                    //    就**不参与合成** ✓，和"隐藏层不画"是同一个口径 ✓。
                    val fallback = if (layer.kind == ComicLayer.Kind.BASE) page.basePath else null
                    val path = layer.imagePath ?: fallback ?: return@forEach
                    val size = imageSizeOf(path) ?: return@forEach
                    // Fit：和画布预览那个 `ContentScale.Fit` 逐字一致 ✓（留边就是留边，不裁 ✓）
                    // 绘画层是**页大小**的，所以 Fit 之后正好铺满整页 ✓ —— 和底板逐像素对齐 ✓
                    steps += bitmapStep(
                        layer = layer,
                        path = path,
                        target = ComicLayout.fit(pageRect, size.first, size.second),
                    )
                }

                ComicLayer.Kind.GENERATED -> {
                    // Crop（cover）：和预览里格子贴图的 `ContentScale.Crop` 一致 ✓（漫画格子的常用口径 ✓）
                    // ⚠️ 几何只有一处：[generatedPlacement] ✓（第 ⑭ 批的 materialize 用的是同一个 ✓）
                    generatedPlacement(page, layer, imageSizeOf)?.let { steps += it }
                }
            }
        }
        return Plan(pageWidth = pageWidth, pageHeight = pageHeight, steps = steps)
    }

    /**
     * **一层生成层在页上的落点**（几何与 [plan] 里 `GENERATED` 那一支**逐字同源** ✓ ——
     * 那一支现在就是调它 ✓）。
     *
     * 为什么单拎出来 ✗：第 ⑭ 批的 **materialize**（"生成层被画过之后变成普通绘画层"那一步 ✓）
     * 要的是**这一层自己**的落点，而 [plan] 会把**隐藏 / 全透明**的层整个跳过 ✓ ——
     * 对"拼页合成"来说那是对的 ✓，对"我要把这一层烘焙成像素"来说就查不到了 ✗。
     *
     * ⚠️ 这里**不看 `visible` / `opacity`** ✗：透明度留在图层自己身上（`layer.opacity` ✓），
     * 界面画这一层时会按它叠 ✓ —— 所以烘焙出来的样子和之前**一模一样** ✓（见 `AppState` 那边的说明 ✓）。
     *
     * @return `null` = 这一层没有图 / 没挂格子 / 那一格不在了 / 图尺寸读不出来 ✓
     *   （调用方如实报"读不出来"，**不静默** ✗）
     */
    fun generatedPlacement(
        page: ComicBoardPage,
        layer: ComicLayer,
        imageSizeOf: (String) -> Pair<Int, Int>?,
    ): Step.Bitmap? {
        if (layer.kind != ComicLayer.Kind.GENERATED) return null
        val path = layer.imagePath ?: return null
        val panel = layer.panelId?.let { id -> page.panels.firstOrNull { it.id == id } } ?: return null
        val size = imageSizeOf(path) ?: return null
        return bitmapStep(layer, path, ComicLayout.cover(panel.rect(), size.first, size.second))
    }

    private fun bitmapStep(layer: ComicLayer, path: String, target: Rect): Step.Bitmap = Step.Bitmap(
        layerId = layer.id,
        kind = layer.kind,
        imagePath = path,
        x = target.left.roundToInt(),
        y = target.top.roundToInt(),
        // 至少 1 像素：矩形退化成 0 的话 `ComicComposer.compose` 会跳过它（也就等于没画 ✗）
        width = target.width.roundToInt().coerceAtLeast(1),
        height = target.height.roundToInt().coerceAtLeast(1),
        opacity = layer.opacity,
    )

    /** 把 [Step.Bitmap] 那几步翻成 `ComicComposer` 吃的形状 ✓（顺序不变 ✓）；矢量层原样跳过 ✓。 */
    fun bitmapPlacements(steps: List<Step>): List<ComicComposer.BitmapPlacement> =
        steps.filterIsInstance<Step.Bitmap>().map { step ->
            ComicComposer.BitmapPlacement(
                imagePath = step.imagePath,
                x = step.x,
                y = step.y,
                width = step.width,
                height = step.height,
                opacity = step.opacity,
            )
        }

    /** 一格的矩形（底板像素 ✓）—— 和界面里 `offset/size` 用的那几个数同源 ✓。 */
    private fun com.kallan.naistudio.models.ComicPanel.rect(): Rect =
        Rect(x, y, x + w, y + h)
}
