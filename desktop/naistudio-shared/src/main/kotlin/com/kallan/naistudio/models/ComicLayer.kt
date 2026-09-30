package com.kallan.naistudio.models

/**
 * **图层模型**（高级漫画模式）—— 用户 2026-09-26：
 * 「图层功能，**生的图都是一个新图层**，方便修改」。
 *
 * 一页 = 一叠图层，从下到上：
 * ```
 * [底板 BASE]  ← 新建的画布 / 导入的图
 * [绘画 PAINT …]  ← 顶部画布编辑器里"新建绘画层"画出来的位图层 ✓（用户 2026-09-20 口径）
 * [生成 GENERATED …]  ← 每生成一张 = 自动新增一层 ✓（用户点名的口径）
 * [气泡 BUBBLE …] / [文本 TEXT …]  ← 后加的在上面
 * ```
 * 这样"改某一格"不用重跑整页 ✓（把它那一层拿出来改/删/重生成就行 ✓）。
 *
 * ## 三种"位图层"（有 [imagePath]、参与拼页合成与 PSD 导出 ✓）
 *
 * [Kind.BASE]（底板）/ [Kind.GENERATED]（生成层）/ [Kind.PAINT]（绘画层）✓ ——
 * 它们的差别只在**落点几何**（见 `services/ComicPageExporter.plan` ✓）：
 * 底板与绘画层是**页大小、铺满整页**（Fit ✓），生成层是**cover 进它自己那一格** ✓。
 * 气泡 / 文本是**矢量**（没有 imagePath ✗，由 `renderComicOverlayLayer` 栅格化 ✓）。
 *
 * 这个类只管**叠放顺序与显隐**：不碰文件、不碰 UI ✓ → 能单测 ✓。
 * 真正的像素合成（拼页导出 ✓）在后面那批做 ✓。
 */
data class ComicLayer(
    val id: String,
    val name: String,
    val kind: Kind,
    /** 这一层对应的格子（气泡/文本/生成层都有 ✓；底板没有 → null ✓）。 */
    val panelId: String? = null,
    /** 图片层的文件路径（气泡/文本层没有 → null ✓）。 */
    val imagePath: String? = null,
    var visible: Boolean = true,
    /** 0f~1f（1 = 完全不透明 ✓）。 */
    var opacity: Float = 1f,
) {
    /**
     * 图层种类。
     *
     * ⚠️ **往这里加一种，凡是 `when (kind)` 的地方都会编译不过** ✓ —— 这正是想要的
     * （第 ⑬ 批加 [PAINT] 时就是这么把三处漏网的全找出来的：JSON 往返 ✓、
     * `normalizedOverlays()` 的保留名单 ✓、导出链 / PSD 的位图分支 ✓）。
     *
     * [PAINT]（绘画层，用户 2026-09-20：「顶部放入的是画布编辑器，可以**选定图层或新建图层绘画**」✓）
     * 是**位图层**：它和 [BASE] 一样是页大小、铺满整页 ✓，参与拼页合成与 PSD 导出 ✓；
     * 它的**主人是它自己**（不像 [GENERATED] 挂在某一格上、[BUBBLE]/[TEXT] 挂在气泡/文本上 ✓）
     * —— 所以 `normalizedOverlays()` 必须像保留 [GENERATED] 一样保留它 ✗（第 ⑥ 批的坑：加一颗
     * 气泡会把"没主"的层静默删光 ✓）。
     */
    enum class Kind { BASE, GENERATED, BUBBLE, TEXT, PAINT }
}

/**
 * 一页的图层叠（**下标 0 = 最底下** ✓）。
 * 所有"上移/下移"都按这个方向：+1 = 往上盖 ✓。
 */
class ComicLayerStack(initial: List<ComicLayer> = emptyList()) {

    private val layers: MutableList<ComicLayer> = initial.toMutableList()

    /** 从下到上的只读视图（界面按这个顺序画 ✓）。 */
    val all: List<ComicLayer> get() = layers.toList()

    /** 只看得见的那些（合成用 ✓）。 */
    fun visibleLayers(): List<ComicLayer> = layers.filter { it.visible && it.opacity > 0f }

    fun byId(id: String): ComicLayer? = layers.firstOrNull { it.id == id }

    /** 加一层：默认放到**最上面** ✓（新生成的就是盖在最上面 ✓）。 */
    fun add(layer: ComicLayer) {
        layers.add(layer)
    }

    /**
     * **"每生成一张 = 新图层"** ✓ 的入口：按格子造一层生成层 ✓。
     * 同一格重复生成时，旧的那层**不删**（用户可能要对比 ✗），由调用方决定删不删 ✓。
     */
    fun addGenerated(id: String, panelId: String, imagePath: String, name: String? = null): ComicLayer {
        val layer = ComicLayer(
            id = id,
            name = name ?: "生成层",
            kind = ComicLayer.Kind.GENERATED,
            panelId = panelId,
            imagePath = imagePath,
        )
        add(layer)
        return layer
    }

    fun remove(id: String): Boolean = layers.removeAll { it.id == id }

    /** 上移一层（已经是最上面就返回 false ✓，别静默成功 ✗）。 */
    fun moveUp(id: String): Boolean = move(id, +1)

    /** 下移一层（已经在最下面 → false ✓）。 */
    fun moveDown(id: String): Boolean = move(id, -1)

    private fun move(id: String, delta: Int): Boolean {
        val index = layers.indexOfFirst { it.id == id }
        if (index < 0) return false
        val target = index + delta
        if (target < 0 || target >= layers.size) return false
        val layer = layers.removeAt(index)
        layers.add(target, layer)
        return true
    }

    fun setVisible(id: String, visible: Boolean) {
        byId(id)?.visible = visible
    }

    fun setOpacity(id: String, opacity: Float) {
        byId(id)?.opacity = opacity.coerceIn(0f, 1f)
    }

    /** 压平前要确认"还有东西可合成"✓ —— 空叠 / 全隐藏就别往下走（调用方据此提示 ✓）。 */
    fun canFlatten(): Boolean = visibleLayers().isNotEmpty()
}
