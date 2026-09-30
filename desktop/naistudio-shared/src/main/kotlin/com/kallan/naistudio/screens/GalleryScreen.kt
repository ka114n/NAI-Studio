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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.kallan.naistudio.platform.LocalPlatform
import com.kallan.naistudio.platform.LocalUiHost
import java.io.File
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.HistoryItem
import com.kallan.naistudio.platform.LocalUiHost
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.FileImage
import com.kallan.naistudio.ui.FullscreenImageViewer
import com.kallan.naistudio.ui.LocalWindowSize
import com.kallan.naistudio.ui.ViewerImage
import com.kallan.naistudio.ui.onSecondaryClick
import com.kallan.naistudio.ui.trackTextInputFocus
import kotlin.math.roundToInt
import kotlin.random.Random

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
    /**
     * **右键弹出的小窗口**（用户 2026-09-19 要求：图片右键开一个小窗口，样式与既有面板一致）——
     * 记的是"哪一格开着菜单"，菜单本体画在**那一格里**（锚点天然就对）。
     */
    var menuFor by remember { mutableStateOf<HistoryItem?>(null) }
    var renaming by remember { mutableStateOf<HistoryItem?>(null) }

    // 「另存为…」是**两步**：先记住要存哪张，等用户在系统对话框里选完位置再拷过去。
    // （手机 SAF 建文件 / 电脑"另存为"对话框，两边都是异步回调。）
    var pendingSaveAsPath by remember { mutableStateOf<String?>(null) }
    val saveAsLauncher = LocalUiHost.current.rememberFileCreator("image/png") { ref ->
        val source = pendingSaveAsPath
        if (ref != null && source != null) state.exportImageFile(source, ref)
        pendingSaveAsPath = null
    }
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

    // ⚠️ 2026-09-19 改成**固定边长**（用户：「图库的图片不管窗口模式还是全屏模式固定显示大小」
    //    「显示去除每行目数，改为图片显示大小」）：
    //    以前按"每行几张"算格子宽 = 窗口宽 / 张数 → 窗口一变图就跟着变 ✗。
    //    现在用 `GridCells.FixedSize(边长)`，窗口怎么变每张图都是那个尺寸，多出来的地方留白。
    val itemSizeDp = settings.galleryItemSizeDp.coerceIn(110, 320)
    // ⚠️ 还要乘上**系统 UI 缩放**（用户 2026-09-20：「缩略图狗牙多、模糊」）：
    // 电脑上那个 exe 不是 DPI 感知的，Windows 会把整个窗口**位图拉伸**到物理像素（150% 就 ×1.5），
    // 而 Compose 报的 `LocalDensity` 仍然当 1.0 ✗ —— 只按 dp 算出来的请求尺寸就偏小 1.5 倍，
    // 到屏幕上再被拉伸一次 → 糊。乘上 `uiScale` 之后解码像素和物理像素 1:1，就不糊了。
    val uiScale = LocalPlatform.current.uiScale
    val cellThumbPx = with(LocalDensity.current) {
        // 格子是 2:3 竖图 + Crop 铺满，长边是「高」：按格子高度（宽 × 1.5）取缩略图
        (itemSizeDp.dp.toPx() * 1.5f * uiScale).roundToInt().coerceIn(256, 2048)
    }
    // 列表那一行的缩略图是 120dp
    val listThumbPx = with(LocalDensity.current) {
        (120.dp.toPx() * uiScale).roundToInt()
    }.coerceIn(256, 1024)

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
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            settings.galleryLayout == "list" -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(cells, key = { it.base.id }) { cell ->
                    // 同网格那两处：包一层 `Box` 给右键小窗口当锚点，并量出这一格尺寸
                    var cellSize by remember { mutableStateOf(IntSize.Zero) }
                    Box(Modifier.onGloballyPositioned { cellSize = it.size }) {
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
                        onSecondaryClick = { menuFor = cell.base },
                    )
                    // 右键小窗口（锚在这一格上）
                    GalleryItemContextMenu(
                        item = cell.base,
                        expanded = menuFor?.id == cell.base.id,
                        state = state,
                        t = t,
                        onDismiss = { menuFor = null },
                        onShowMetadata = {
                            menuFor = null
                            detail = cell.base
                        },
                        anchorSize = cellSize,
                    )
                    }
                }
            }

            // **瀑布模式**：每张图按自己的宽高比占位（`LazyVerticalStaggeredGrid`），
            // 不裁切、不等高 —— 专门给"大小不一的图"用（用户 2026-09-19）。
            settings.galleryLayout == "waterfall" -> LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.FixedSize(itemSizeDp.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
            ) {
                items(cells, key = { it.base.id }) { cell ->
                    // ⚠️ 必须包一层 `Box`：右键小窗口（`DropdownMenu`）的锚点是**父节点** ——
                    // 直接当兄弟放在 item lambda 里，它会锚到整个网格上（用户 2026-09-19 报的
                    // 「右键弹出的弹窗不在图片旁边」就是这个）。包一层之后锚点就是这一格 ✓。
                    // 顺便量一下这一格的尺寸，菜单据此挪到**图片右边**。
                    var cellSize by remember { mutableStateOf(IntSize.Zero) }
                    Box(Modifier.onGloballyPositioned { cellSize = it.size }) {
                        GalleryGridCell(
                        item = cell.display,
                        thumbPx = cellThumbPx,
                        selecting = selecting,
                        selected = cell.base.id in selectedIds,
                        upscaledBadge = cell.upscaled != null,
                        fixedHeight = false,
                        onClick = {
                            if (selecting) onToggleSelect(cell.base.id) else viewing = cell.display
                        },
                        onLongClick = { detail = cell.base },
                        onSecondaryClick = { menuFor = cell.base },
                    )
                    // 右键小窗口（锚在这一格上）
                    GalleryItemContextMenu(
                        item = cell.base,
                        expanded = menuFor?.id == cell.base.id,
                        state = state,
                        t = t,
                        onDismiss = { menuFor = null },
                        onShowMetadata = {
                            menuFor = null
                            detail = cell.base
                        },
                        anchorSize = cellSize,
                    )
                    }
                }
            }

            else -> LazyVerticalGrid(
                // ⚠️ `FixedSize` 不是 `Adaptive`：边长**固定**，窗口/全屏怎么变图都一样大
                columns = GridCells.FixedSize(itemSizeDp.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cells, key = { it.base.id }) { cell ->
                    // ⚠️ 必须包一层 `Box`：右键小窗口（`DropdownMenu`）的锚点是**父节点** ——
                    // 直接当兄弟放在 item lambda 里，它会锚到整个网格上（用户 2026-09-19 报的
                    // 「右键弹出的弹窗不在图片旁边」就是这个）。包一层之后锚点就是这一格 ✓。
                    // 顺便量一下这一格的尺寸，菜单据此挪到**图片右边**。
                    var cellSize by remember { mutableStateOf(IntSize.Zero) }
                    Box(Modifier.onGloballyPositioned { cellSize = it.size }) {
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
                        onSecondaryClick = { menuFor = cell.base },
                    )
                    // 右键小窗口（锚在这一格上）
                    GalleryItemContextMenu(
                        item = cell.base,
                        expanded = menuFor?.id == cell.base.id,
                        state = state,
                        t = t,
                        onDismiss = { menuFor = null },
                        onShowMetadata = {
                            menuFor = null
                            detail = cell.base
                        },
                        anchorSize = cellSize,
                    )
                    }
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
            onSaveAs = {
                // 记住要存哪张，然后弹系统"另存为"（手机 SAF / 电脑文件对话框）。
                // 建议文件名沿用图库里那个名字，用户改不改都行。
                pendingSaveAsPath = item.filePath
                saveAsLauncher.launch(item.filePath.substringAfterLast('/'))
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
                    // 重命名框：登记焦点（空格闸门读 `AppState.textInputFocused`）——
                    // 文件名里带空格很常见，漏了这里就打不出空格。
                    modifier = Modifier.trackTextInputFocus(state, "gallery.rename"),
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
 * **图片右键的小窗口**（用户 2026-09-19 要求：图片右键开一个小窗口，样式与既有面板一致）——
 * 样式照着参考图来：一行 = **图标 + 文字**，分组之间一条细线，圆角深色卡片。
 *
 * 四件事：
 *  · 使用此图图生图 → [AppState.useImageAsI2iBase]（和弹窗里那颗按钮同一个入口）；
 *  · 放大 → [AppState.officialUpscale]（官方 2×，付费）；
 *  · 打开文件夹 → `UiHost.openUrl(图片所在目录)`（**原来在弹窗底部那颗"跳转到文件管理器"挪到这儿**）；
 *  · 查看元数据 → 就是原来那个参数弹窗（[ImageParamsDialog]）。
 */
@Composable
private fun GalleryItemContextMenu(
    item: HistoryItem,
    expanded: Boolean,
    state: AppState,
    t: (String) -> String,
    onDismiss: () -> Unit,
    onShowMetadata: () -> Unit,
    /** 锚点（那一格）的像素尺寸：用来把菜单挪到**图片右边**（用户 2026-09-19 要求）。 */
    anchorSize: IntSize = IntSize.Zero,
) {
    val uiHost = LocalUiHost.current
    val density = LocalDensity.current
    // Compose 的 `DropdownMenu` 默认落在**锚点的下方、左对齐**；这里用 `offset` 把它挪到
    // **锚点右边、顶对齐**：x 右移一格宽，y 上移一格高（默认 top = 锚点 bottom）。
    val offset = with(density) {
        DpOffset(
            x = with(density) { anchorSize.width.toDp() } + 6.dp,
            y = -with(density) { anchorSize.height.toDp() } + 6.dp,
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, offset = offset) {
        GalleryMenuRow(Icons.Filled.Edit, t("gallery.useAsI2i")) {
            onDismiss()
            state.useImageAsI2iBase(item.filePath, item.width, item.height)
        }
        GalleryMenuRow(Icons.Filled.Search, t("gallery.ctxUpscale")) {
            onDismiss()
            state.officialUpscale(item.filePath)
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        GalleryMenuRow(Icons.Filled.Home, t("gallery.openInFileManager")) {
            onDismiss()
            runCatching {
                File(item.filePath).parentFile?.let { uiHost.openUrl(it.toURI().toString()) }
            }
        }
        GalleryMenuRow(Icons.Filled.Settings, t("gallery.ctxMetadata")) { onShowMetadata() }
    }
}

/** 小窗口里的一行：图标 + 文字（按参考图那个样式）。 */
@Composable
private fun GalleryMenuRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        },
        onClick = onClick,
    )
}

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
    /** 这一行里有官方 2× 放大版 → 缩略图右上角打「2x」角标。 */
    upscaledBadge: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    /** **右键**（电脑）：和长按同一个去处 —— 打开这张图的详情与操作。 */
    onSecondaryClick: (Offset) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onSecondaryClick(onSecondaryClick)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box {
                FileImage(
                    path = item.filePath,
                    maxDimension = thumbPx,
                    useThumbnail = true,
                    modifier = Modifier.size(120.dp),
                )
                if (selecting) {
                    SelectionCheckbox(
                        selected = selected,
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                    )
                }
                if (upscaledBadge) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "2x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.prompt.ifBlank { "(无提示词)" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                Text(
                    "${item.date} · ${item.width}×${item.height}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "seed ${item.seed} · ${item.model} · ${item.feature}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    item.filePath.substringAfterLast('/'),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 松散网格的一格：图按原始比例铺满方块（裁剪），下面一行提示词。点击看大图，长按看参数。 */
@Composable
private fun GalleryGridCell(
    item: HistoryItem,
    thumbPx: Int,
    selecting: Boolean,
    selected: Boolean,
    /** 这一格里有官方 2× 放大版 → 右上角打「2x」角标。 */
    upscaledBadge: Boolean = false,
    /**
     * `true` = 固定 2:3 格子（网格模式，`Crop` 铺满）；
     * `false` = **按图片自己的宽高比**（瀑布模式：不裁切、不等高）。
     */
    fixedHeight: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    /** **右键**（电脑）：和长按同一个去处 —— 打开这张图的详情与操作。 */
    onSecondaryClick: (Offset) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onSecondaryClick(onSecondaryClick)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column {
            Box {
                FileImage(
                    path = item.filePath,
                    maxDimension = thumbPx,
                    contentScale = ContentScale.Crop,
                    useThumbnail = true,
                    modifier = if (fixedHeight) {
                        Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                    } else {
                        // 瀑布模式：按真实宽高比（读不到就退回 2:3）
                        Modifier.fillMaxWidth().aspectRatio(
                            (item.width.takeIf { it > 0 } ?: 2).toFloat() /
                                (item.height.takeIf { it > 0 } ?: 3).toFloat(),
                        )
                    },
                )
                if (selecting) {
                    SelectionCheckbox(
                        selected = selected,
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                    )
                }
                if (upscaledBadge) {
                    // 这一格里有 2× 放大版（格子缩略图显示的就是放大后那张）
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "2x",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            Text(
                item.prompt.ifBlank { "(无提示词)" },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/** 批量删除模式下每张图**左下角**的小方框。 */
@Composable
private fun SelectionCheckbox(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = null,
            modifier = Modifier.size(28.dp),
        )
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
    // 图库列表那一栏最多占窗口高度的 70%（窗口高度走共用的 LocalWindowSize）
    val maxContentHeight = LocalWindowSize.current.height * 0.7f
    var tab by rememberSaveable { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth()) {
            TabRow(selectedTabIndex = tab) {
                listOf(t("gallery.sortTab"), t("gallery.displayTab")).forEachIndexed { index, label ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(label) },
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxContentHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (tab == 0) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text(t("gallery.searchHint")) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = t("gallery.clearSearch"),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default,
                        // 图库搜索框：登记焦点（空格闸门读 `AppState.textInputFocused`）——
                        // 搜索词带空格很常见；顶栏那个胶囊是另一个节点，key 也不同名。
                        modifier = Modifier
                            .fillMaxWidth()
                            .trackTextInputFocus(state, "gallery.panel.search"),
                    )
                    if (query.isNotBlank()) {
                        Text(
                            RuntimeText.format(
                                settings.language,
                                "gallery.matchCount",
                                mapOf("count" to state.history.count { it.prompt.contains(query.trim(), true) }),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Text(t("gallery.sortTitle"), style = MaterialTheme.typography.labelMedium)
                    SortOptionRow(
                        label = t("gallery.sortTime"),
                        selected = settings.gallerySort == "time",
                        descending = settings.gallerySortDescending,
                        directionHint = if (settings.gallerySortDescending) {
                            t("gallery.sortDesc")
                        } else {
                            t("gallery.sortAsc")
                        },
                        onSelect = { state.setSettings { it.copy(gallerySort = "time") } },
                        onToggleDirection = {
                            state.setSettings { it.copy(gallerySortDescending = !it.gallerySortDescending) }
                        },
                    )
                    SortOptionRow(
                        label = t("gallery.sortRandom"),
                        selected = settings.gallerySort == "random",
                        descending = null,
                        directionHint = null,
                        onSelect = {
                            state.setSettings { it.copy(gallerySort = "random") }
                            onShuffle()
                        },
                        onToggleDirection = {},
                    )
                } else {
                    Text(t("gallery.displayMode"), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.galleryLayout == "list",
                            onClick = { state.setSettings { it.copy(galleryLayout = "list") } },
                            label = { Text(t("gallery.modeList")) },
                        )
                        FilterChip(
                            selected = settings.galleryLayout == "grid",
                            onClick = { state.setSettings { it.copy(galleryLayout = "grid") } },
                            label = { Text(t("gallery.modeGrid")) },
                        )
                        // **瀑布模式**（用户 2026-09-19：「显示模式添加瀑布模式，可以适配大小不一的图」）：
                        // 每张图按自己的宽高比占位，不裁切、不等高
                        FilterChip(
                            selected = settings.galleryLayout == "waterfall",
                            onClick = { state.setSettings { it.copy(galleryLayout = "waterfall") } },
                            label = { Text(t("gallery.modeWaterfall")) },
                        )
                    }

                    // 用户 2026-09-19：「显示去除每行目数，改为**图片显示大小**」——
                    // 原来是"每行几张"，窗口一变每张就又跟着变；现在直接定**每张图的边长**，
                    // 窗口/全屏怎么变，图都是这么大（`GridCells.FixedSize`）。
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(t("gallery.itemSize"), style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${settings.galleryItemSizeDp} dp",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Slider(
                        value = settings.galleryItemSizeDp.toFloat(),
                        onValueChange = { value ->
                            state.setSettings {
                                it.copy(galleryItemSizeDp = value.roundToInt().coerceIn(110, 320))
                            }
                        },
                        valueRange = 110f..320f,
                        steps = 6,
                        enabled = settings.galleryLayout != "list",
                    )
                }
            }
        }
    }
}

/**
 * 一行排序方式，样式对齐参考截图：左侧是方向指示位（只在选中且支持方向时出现箭头），
 * 右侧是文字。整行可点选，箭头可单独点来切换升降序。
 */
@Composable
private fun SortOptionRow(
    label: String,
    selected: Boolean,
    descending: Boolean?,
    directionHint: String?,
    onSelect: () -> Unit,
    onToggleDirection: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            if (selected && descending != null) {
                IconButton(onClick = onToggleDirection, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (descending) Icons.Filled.KeyboardArrowDown
                        else Icons.Filled.KeyboardArrowUp,
                        contentDescription = directionHint,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            if (selected && directionHint != null) {
                Text(
                    directionHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
