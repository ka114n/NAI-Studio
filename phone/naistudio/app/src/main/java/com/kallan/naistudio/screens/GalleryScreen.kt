package com.kallan.naistudio.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import com.kallan.naistudio.ui.RefAlertDialog as AlertDialog
import com.kallan.naistudio.ui.RefCard as Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import com.kallan.naistudio.ui.RefFilterChip as FilterChip
import androidx.compose.material3.Icon
import com.kallan.naistudio.ui.RefIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.kallan.naistudio.ui.RefModalBottomSheet as ModalBottomSheet
import com.kallan.naistudio.ui.RefOutlinedTextField as OutlinedTextField
import com.kallan.naistudio.ui.RefSlider as Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import com.kallan.naistudio.ui.RefTextButton as TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.HistoryItem
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.FileImage
import com.kallan.naistudio.ui.FullscreenImageViewer
import com.kallan.naistudio.ui.ViewerImage
import kotlin.math.roundToInt
import kotlin.random.Random
import com.kallan.naistudio.ui.LocalRef
import com.kallan.naistudio.ui.RefSeg
import com.kallan.naistudio.ui.RefFChip
import com.kallan.naistudio.ui.RefHint
import com.kallan.naistudio.ui.RefSheetTitle
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * 图库页。
 *
 * 历史索引来自存储层（一条 JSON 数组，索引 0 = 最新）。
 * 列表项用降采样解码显示，缩略图按需生成 —— 参考实现本身**不生成缩略图文件**，
 * 界面直接显示原文件，这里保持一致的做法但加了降采样（避免大图占满内存）。
 *
 * 两种显示模式（`设置 → galleryLayout`）：
 *  · **列表**：缩略图 + 提示词 / seed / 文件名
 *  · **松散网格**：只有图 + 一行提示词，**每行数目可调**（2..6）
 *
 * 排序与显示设置住在**页面右上角那个按钮**打开的底部抽屉里（顶部栏由 `StudioShell` 画，
 * 所以打开动作由外壳回调进来）。搜索只匹配提示词，是临时状态，不落盘。
 */
@Composable
fun GalleryScreen(
    state: AppState,
    controlsOpen: Boolean,
    onDismissControls: () -> Unit,
    /** 把「当前看的那张」报给外壳。 */
    onSelect: (HistoryItem?) -> Unit,
    /** 批量删除模式：开时右下角出现勾选框，点图是勾选而不是看大图。 */
    selecting: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
) {
    val language = state.settings.language
    val t: (String) -> String = { key -> RuntimeText.text(language, key) }
    val settings = state.settings

    var detail by remember { mutableStateOf<HistoryItem?>(null) }
    var renaming by remember { mutableStateOf<HistoryItem?>(null) }
    // 点图 → 全屏预览（双指缩放），和相册一样
    var viewing by remember { mutableStateOf<HistoryItem?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    // 随机排序的洗牌种子：切到「随机」时重新掷一次，之后的 recomposition 保持稳定
    var shuffleSeed by remember { mutableIntStateOf(Random.nextInt()) }

    val visible = remember(state.history, query, settings.gallerySort, settings.gallerySortDescending, shuffleSeed) {
        val keyword = query.trim()
        val matched = if (keyword.isEmpty()) {
            state.history
        } else {
            state.history.filter { it.prompt.contains(keyword, ignoreCase = true) }
        }
        when (settings.gallerySort) {
            // createdAt 是 ISO8601，字典序即时间序
            "random" -> matched.shuffled(Random(shuffleSeed))
            else -> {
                val byTime = matched.sortedBy { it.createdAt }
                if (settings.gallerySortDescending) byTime.reversed() else byTime
            }
            // 防线：Lazy 列表的 key 重复会直接闪退，这里按 id 去重（坏数据也不至于崩）
        }.distinctBy { it.id }
    }

    /**
     * 图库格子：**一张原图 + 它的官方 2× 放大版**合成一格（放大版不再单独占位）。
     * 缩略图与"点开看的大图"都用放大后的（[display]），而长按弹窗里的操作仍绑在原图上
     * （图生图 / 遮罩重绘必须用放大前那张）。
     *
     * ⚠️ 这里必须保证**每个 item 的 id 至多出现在一个格子里** —— LazyGrid/LazyColumn 的 key
     * 重复会直接抛 `IllegalArgumentException: Key ... was already used` 闪退（真机上踩过一次：
     * "没有父链接的放大版"被第一轮当普通图加了一遍、第二轮兜底又加了一遍）。
     */
    val cells = remember(visible) {
        // 放大版 → 父图路径。优先用落盘的 upscaleOfPath；老数据没有这个字段（早期版本存的），
        // 就按命名规则反查：放大版文件名是「父图名 + 2x」。
        fun parentPathOf(item: HistoryItem): String? {
            if (item.feature != "upscale") return null
            val linked = item.upscaleOfPath?.takeIf { it.isNotEmpty() }
            if (linked != null) return linked
            val stem = item.filePath.substringAfterLast('/').substringBeforeLast('.')
            if (!stem.endsWith("2x")) return null
            val parentStem = stem.dropLast(2)
            return visible.firstOrNull { other ->
                other.id != item.id &&
                    other.filePath.substringAfterLast('/').substringBeforeLast('.') == parentStem
            }?.filePath
        }

        val upscalesByParent = visible
            .mapNotNull { up -> parentPathOf(up)?.let { it to up } }
            .groupBy({ it.first }, { it.second })
        val consumed = mutableSetOf<String>()
        val result = ArrayList<GalleryCell>(visible.size)
        // 第一轮：正常图（带上它的放大版）。放大版自己留给父图那格，这里跳过。
        visible.forEach { item ->
            if (item.id in consumed) return@forEach
            if (parentPathOf(item) != null) return@forEach
            val upscaled = upscalesByParent[item.filePath]?.firstOrNull { it.id !in consumed }
            consumed.add(item.id)
            if (upscaled != null) consumed.add(upscaled.id)
            result.add(GalleryCell(base = item, upscaled = upscaled))
        }
        // 第二轮：兜底 —— 父图不在当前可见集合里（被删 / 被搜索过滤掉）的放大版，自己成一格，
        // 绝不会凭空消失，也绝不会和第一轮重复。
        visible.forEach { item ->
            if (item.id in consumed) return@forEach
            consumed.add(item.id)
            result.add(GalleryCell(base = item, upscaled = null))
        }
        result
    }

    // 缩略图按**实际格子像素**来取：取小了会被放大（糊 + 锯齿），取大了白吃内存。
    // 格子是 2:3 竖图 + Crop 铺满，长边是「高」不是「宽」——按格子宽度算的 maxDimension
    // 只够填满宽度，Crop 还要再把图放大 1.5 倍去盖住高度，于是必糊。这里直接按「格子高度」
    // （宽度 × 1.5）来定 maxDimension，与实际渲染尺寸对齐。
    val columns = settings.galleryColumns.coerceIn(2, 6)
    val cellWidthPx = with(LocalDensity.current) {
        (LocalConfiguration.current.screenWidthDp.dp.toPx() / columns).roundToInt()
    }
    val cellThumbPx = (cellWidthPx * 1.5f).roundToInt().coerceIn(256, 1536)
    // 列表那一行的缩略图是 120dp
    val listThumbPx = with(LocalDensity.current) { 120.dp.toPx().roundToInt() }.coerceIn(256, 1024)

    Box(Modifier.fillMaxSize()) {
        when {
            state.history.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    t("gallery.empty"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            visible.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    t("gallery.noMatch"),
                    fontSize = 13.5.sp,
                    color = LocalRef.current.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            settings.galleryLayout == "list" -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(cells, key = { it.base.id }) { cell ->
                    GalleryListRow(
                        item = cell.display,
                        thumbPx = listThumbPx,
                        selecting = selecting,
                        selected = cell.base.id in selectedIds,
                        upscaledBadge = cell.upscaled != null,
                        onClick = {
                            // 点开看放大后的那张（查看器仍可左右滑动浏览全部）
                            if (selecting) onToggleSelect(cell.base.id) else viewing = cell.display
                        },
                        onLongClick = { detail = cell.base },
                    )
                }
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cells, key = { it.base.id }) { cell ->
                    GalleryGridCell(
                        item = cell.display,
                        thumbPx = cellThumbPx,
                        selecting = selecting,
                        selected = cell.base.id in selectedIds,
                        upscaledBadge = cell.upscaled != null,
                        onClick = {
                            if (selecting) onToggleSelect(cell.base.id) else viewing = cell.display
                        },
                        onLongClick = { detail = cell.base },
                    )
                }
            }
        }
    }

    /**
     * 查看器里的换页顺序：按图库格子顺序展开，**同一格的「2× + 原图」紧挨着**。
     *
     * 早前是直接拿 `visible`（也就是按时间排的历史）来翻页：放大版是**刚生成**的，
     * 于是给一张**旧图**放大之后，2× 那张跑到列表最前面，跟原图隔着十万八千里，没法左右滑比对。
     * 现在按格子展开，放大版永远贴在它原图旁边 —— 无论什么时候放大的。
     */
    val viewerItems = remember(cells) {
        cells.flatMap { cell ->
            if (cell.upscaled != null) listOf(cell.upscaled, cell.base) else listOf(cell.base)
        }
    }

    // 全屏预览：多张一起交给查看器，左右滑切换的动画和主 pager 同款
    viewing?.let { item ->
        val index = viewerItems.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        FullscreenImageViewer(
            images = viewerItems.map { ViewerImage(path = it.filePath) },
            initialPage = index,
            onPageChange = { page ->
                viewerItems.getOrNull(page)?.let { viewing = it }
            },
            onDismiss = { viewing = null },
        )
    }

    // 把「当前看的那张」报给外壳（右上角垃圾桶用它做目标）；图被删掉时也把本地弹窗收掉
    LaunchedEffect(viewing, detail) {
        onSelect(viewing ?: detail)
    }
    LaunchedEffect(state.history) {
        if (detail != null && state.history.none { it.id == detail?.id }) detail = null
        if (viewing != null && state.history.none { it.id == viewing?.id }) viewing = null
    }

    if (controlsOpen) {
        GalleryControlsSheet(
            state = state,
            t = t,
            query = query,
            onQueryChange = { query = it },
            onShuffle = { shuffleSeed = Random.nextInt() },
            onDismiss = onDismissControls,
        )
    }

    detail?.let { item ->
        ImageParamsDialog(
            item = item,
            t = t,
            onDismiss = { detail = null },
            onReuse = {
                state.reuseParams(item)
                detail = null
            },
            onRename = {
                renaming = item
                detail = null
            },
            onUseAsI2i = {
                state.useImageAsI2iBase(item.filePath, item.width, item.height)
                detail = null
            },
            onOfficialUpscale = {
                // 官方 2× 放大（付费）；结果作为新条目进图库，放大前那张原样保留
                state.officialUpscale(item.filePath)
                detail = null
            },
            // 画布编辑另存出来的那张：多一颗「重置修改」（用户 2026-09-26）
            canResetEdit = state.canResetCanvasEdit(item.filePath),
            onResetEdit = {
                state.resetCanvasEdit(item.filePath)
                detail = null
            },
        )
    }

    renaming?.let { item ->
        var name by remember(item.id) {
            mutableStateOf(item.filePath.substringAfterLast('/').substringBeforeLast('.'))
        }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(t("gallery.rename")) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(t("gallery.renameHint")) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state.renameHistory(item, name)
                    renaming = null
                }) { Text(t("common.confirm")) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text(t("common.cancel")) }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// 两种显示模式
// ---------------------------------------------------------------------------

/**
 * 图库里的一个格子 = 一张原图（[base]）**加上它的官方 2× 放大版**（[upscaled]，可空）。
 *
 * 规则：放大版**不单独占格**（用户要求）；格子缩略图与点开的大图都用 [display]（放大后那张，
 * 没有放大版就是原图）；长按弹窗里的操作绑在 [base] 上 —— 图生图 / 遮罩重绘必须用放大前那张。
 */
private data class GalleryCell(val base: HistoryItem, val upscaled: HistoryItem?) {
    val display: HistoryItem get() = upscaled ?: base
}

/** 列表模式的一行：缩略图 + 提示词 / 时间尺寸 / seed 模型 / 文件名。点击看大图，长按看参数。 */
@Composable
private fun GalleryListRow(
    item: HistoryItem,
    thumbPx: Int,
    selecting: Boolean,
    selected: Boolean,
    /** 这一行里有官方 2× 放大版 → 缩略图左上角打「2x」角标。 */
    upscaledBadge: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // 网页 `.gitem`：panel 底、描边、圆角 16、padding 10、间距 12；选中 = accent 描边 + 1px 内描边
    val ref = LocalRef.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ref.panel)
            .border(if (selected) 2.dp else 1.dp, if (selected) ref.accent else ref.border, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // `.gthumb`：宽 96、2:3、圆角 8
            Box(Modifier.width(96.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))) {
                FileImage(
                    path = item.filePath,
                    maxDimension = thumbPx,
                    contentScale = ContentScale.Crop,
                    useThumbnail = true,
                    modifier = Modifier.fillMaxSize(),
                )
                if (upscaledBadge) Badge2x(Modifier.align(Alignment.TopStart).padding(6.dp))
            }
            // `.gmeta`：提示词 13.5 text 行高 1.45 三行；其余 12 faint 单行
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.prompt.ifBlank { "(无提示词)" },
                    fontSize = 13.5.sp,
                    lineHeight = 19.5.sp,
                    color = ref.text,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                GMeta(item.date)
                GMeta("${item.width}×${item.height} · seed ${item.seed}")
                GMeta("${item.model} · ${item.feature}")
                GMeta(item.filePath.substringAfterLast('/'))
            }
        }
        if (selecting) SelectionCheckbox(selected, Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

@Composable
private fun GMeta(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = LocalRef.current.faint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** `.badge2x`：10.5/700、padding 1/6、圆角 6、accent 底。 */
@Composable
private fun Badge2x(modifier: Modifier = Modifier) {
    val ref = LocalRef.current
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ref.accent)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text("2x", fontSize = 10.5.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, color = ref.accentContrast)
    }
}

/** 松散网格的一格（网页 `.ggrid .gitem`）：无内边距、圆角 12、只有图。点击看大图，长按看参数。 */
@Composable
private fun GalleryGridCell(
    item: HistoryItem,
    thumbPx: Int,
    selecting: Boolean,
    selected: Boolean,
    /** 这一格里有官方 2× 放大版 → 左上角打「2x」角标。 */
    upscaledBadge: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val ref = LocalRef.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ref.panel)
            .border(if (selected) 2.dp else 1.dp, if (selected) ref.accent else ref.border, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        FileImage(
            path = item.filePath,
            maxDimension = thumbPx,
            contentScale = ContentScale.Crop,
            useThumbnail = true,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
        )
        if (upscaledBadge) Badge2x(Modifier.align(Alignment.TopStart).padding(6.dp))
        if (selecting) SelectionCheckbox(selected, Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

/** 批量选择时右上角的圆勾（网页 `.selck`：24 圆、2px 白边、未选 黑 .25、选中 accent）。 */
@Composable
private fun SelectionCheckbox(selected: Boolean, modifier: Modifier = Modifier) {
    val ref = LocalRef.current
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) ref.accent else Color.Black.copy(alpha = 0.25f))
            .border(2.dp, if (selected) ref.accent else Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = ref.accentContrast, modifier = Modifier.size(14.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// 排序 / 显示 抽屉
// ---------------------------------------------------------------------------

/**
 * 图库右上角按钮打开的底部抽屉，交互与生成页那个底栏抽屉一致：`排序` / `显示` 两个 Tab。
 *
 * 排序 Tab 顶部是**搜索提示词**，下面是排序方式。参考截图里那种「列表 + 选中项带方向箭头」
 * 的样式，但**只有按时间与随机两项**可选；方向箭头只在按时间时出现（随机没有方向）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryControlsSheet(
    state: AppState,
    t: (String) -> String,
    query: String,
    onQueryChange: (String) -> Unit,
    onShuffle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val settings = state.settings
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
    var tab by rememberSaveable { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // 网页 `#sh-gallery`：sh-title「排序与显示」+ ✕；body = seg（排序 / 显示）+ 各自一栏
        RefSheetTitle(title = t("gallery.controls"), onClose = onDismiss)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxContentHeight)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RefSeg(
                options = listOf(t("gallery.sortTab"), t("gallery.displayTab")),
                selected = tab,
                onSelect = { tab = it },
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tab == 0) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text(t("gallery.searchHint")) },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = LocalRef.current.faint)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }, size = 32.dp) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = t("gallery.clearSearch"),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        keyboardOptions = KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (query.isNotBlank()) {
                        RefHint(
                            RuntimeText.format(
                                settings.language,
                                "gallery.matchCount",
                                mapOf("count" to state.history.count { it.prompt.contains(query.trim(), true) }),
                            ),
                        )
                    }
                    GLabel(t("gallery.sortTitle"))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val timeOn = settings.gallerySort == "time"
                        val dir = if (settings.gallerySortDescending) t("gallery.sortDesc") else t("gallery.sortAsc")
                        // 网页：`按时间 · 降序（新的在前）`；已选中时再点 = 切换升降序
                        RefFChip(
                            text = if (timeOn) "${t("gallery.sortTime")} · $dir" else t("gallery.sortTime"),
                            on = timeOn,
                            onClick = {
                                if (timeOn) {
                                    state.setSettings { it.copy(gallerySortDescending = !it.gallerySortDescending) }
                                } else {
                                    state.setSettings { it.copy(gallerySort = "time") }
                                }
                            },
                        )
                        RefFChip(
                            text = t("gallery.sortRandom"),
                            on = settings.gallerySort == "random",
                            onClick = {
                                state.setSettings { it.copy(gallerySort = "random") }
                                onShuffle()
                            },
                        )
                    }
                } else {
                    GLabel(t("gallery.displayMode"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RefFChip(
                            text = t("gallery.modeList"),
                            on = settings.galleryLayout == "list",
                            onClick = { state.setSettings { it.copy(galleryLayout = "list") } },
                        )
                        RefFChip(
                            text = t("gallery.modeGrid"),
                            on = settings.galleryLayout == "grid",
                            onClick = { state.setSettings { it.copy(galleryLayout = "grid") } },
                        )
                    }
                    // 网页 `.slider-head`：12.5 muted + 数值 text 500
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t("gallery.perRow"), fontSize = 12.5.sp, color = LocalRef.current.muted)
                            Text(
                                settings.galleryColumns.toString(),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = LocalRef.current.text,
                            )
                        }
                        Slider(
                            value = settings.galleryColumns.toFloat(),
                            onValueChange = { value ->
                                state.setSettings { it.copy(galleryColumns = value.toInt().coerceIn(2, 6)) }
                            },
                            valueRange = 2f..6f,
                            steps = 3,
                            enabled = settings.galleryLayout == "grid",
                        )
                    }
                }
            }
        }
    }
}

/** 网页 `.label`：12.5/600 muted。 */
@Composable
private fun GLabel(text: String) {
    Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = LocalRef.current.muted)
}
