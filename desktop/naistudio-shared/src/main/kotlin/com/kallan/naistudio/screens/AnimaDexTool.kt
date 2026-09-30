package com.kallan.naistudio.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kallan.naistudio.services.AnimaCharacter
import com.kallan.naistudio.services.AnimaDexLabels
import com.kallan.naistudio.services.AnimaDexProtocol
import com.kallan.naistudio.services.AnimaSeries
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.LocalWindowSize
import com.kallan.naistudio.ui.NetworkImage

/**
 * 工具页：**角色图鉴**（AnimaDex）。
 *
 * 和网页版原型（`web/animadex`）同一套功能与视觉：
 * 搜索 + 排序 + 筛选 + 网格结果 + 详情面板；详情里那一串 `trigger` 就是**可直接用的提示词**
 * （例如 `ganyu (genshin impact), genshin impact`），一键复制或直接追加进生成页的正面提示词。
 *
 * **不含 LoRA 相关**（用户要求去掉）。
 *
 * 注意滚动结构：这个工具自带 `LazyVerticalGrid`，所以**不能**再套在工具页那个
 * `verticalScroll` 里（嵌套滚动会互相打架）——`ToolsScreen` 里对它单独放行、铺满整页。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnimaDexTool(
    state: AppState,
    t: (String) -> String,
    clipboard: ClipboardManager,
    modifier: Modifier = Modifier,
    onCopied: (String) -> Unit,
    /**
     * 点**角色名字**时干什么 —— 三个入口共用这一个组件，行为各自不同：
     *  · 工具页（默认 null）：只复制名字；
     *  · 提示词旁的抽屉：直接填入正面提示词；
     *  · 「角色」栏里的抽屉：新建一个角色分区。
     */
    onNamePick: ((AnimaCharacter) -> Unit)? = null,
) {
    var filtersOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    // 首次进入：拉筛选表 + 出默认结果（图片数最多的角色）
    LaunchedEffect(Unit) {
        state.animaEnsureFacets()
        if (state.animaResults.isEmpty()) state.animaSearch(reset = true)
    }

    Column(modifier.fillMaxSize()) {
        // 搜索框已经搬到**顶栏**（右上角放大镜点开滑出胶囊，见 StudioShell），这里不再占一行。

        // ---- 排序（横向滚动胶囊；外层 swipeGate 已改成放行内部横向滚动）
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(AnimaDexProtocol.SORTS) { sort ->
                Chip(
                    label = t("anima.sort.$sort"),
                    selected = state.animaSort == sort,
                    onClick = { state.animaSetSort(sort) },
                )
            }
        }

        // ---- 筛选 / 视图切换 / 齿轮（每行个数）
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(onClick = { filtersOpen = !filtersOpen }) {
                Text(
                    if (filtersOpen) t("anima.filtersHide") else t("anima.filtersShow"),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            val active = state.animaFilters.size
            if (active > 0) {
                Text(
                    t("anima.filtersActive") + " $active",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = { state.animaClearFilters() }) {
                    Text(t("anima.filtersClear"), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.weight(1f))
            state.animaError?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 视图切换：角色 ↔ 作品（对应站点上的 ?mode=characters / ?mode=copyrights）
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    ModeSegment(t("anima.modeCharacters"), state.animaMode == "characters") {
                        state.animaSetMode("characters")
                    }
                    ModeSegment(t("anima.modeSeries"), state.animaMode == "copyrights") {
                        state.animaSetMode("copyrights")
                    }
                }
            }
            // 齿轮：每行展示多少个
            Box {
                IconChip(t("anima.settings")) { settingsOpen = true }
                DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }) {
                    Text(
                        t("anima.columnsLabel"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                    listOf(2, 3, 4).forEach { columns ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    t("anima.columnsN").replace("{n}", columns.toString()),
                                    color = if (state.animaColumns == columns) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            },
                            onClick = {
                                state.animaSetColumns(columns)
                                settingsOpen = false
                            },
                        )
                    }
                }
            }
        }

        // ---- 结果网格（角色 or 作品，按设置的列数）
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val empty = if (state.animaMode == "copyrights") {
                state.animaSeries.isEmpty()
            } else {
                state.animaResults.isEmpty()
            }
            when {
                state.animaBusy && empty -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                empty -> Text(
                    t("anima.empty"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(state.animaColumns),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.animaMode == "copyrights") {
                        gridItems(state.animaSeries, key = { it.slug }) { series ->
                            SeriesCard(
                                series = series,
                                // 点作品 = 回到角色视图并按这个作品筛选
                                onClick = {
                                    state.animaFilterByCopyright(series.slug, series.name)
                                },
                            )
                        }
                    } else {
                        gridItems(state.animaResults, key = { it.slug }) { character ->
                            CharacterCard(
                                character = character,
                                // 点**立绘** → 开全屏详情
                                onOpen = { state.animaOpenDetail(character) },
                                // 点**名字** → 只复制名字
                                onCopyName = {
                                    // 有回调就用回调（抽屉版：填提示词 / 建角色分区）；
                                    // 没有（工具页）就维持原样：只复制名字
                                    if (onNamePick != null) {
                                        onNamePick(character)
                                    } else {
                                        clipboard.setText(
                                            androidx.compose.ui.text.AnnotatedString(character.name),
                                        )
                                        onCopied(t("anima.copiedName"))
                                    }
                                },
                                // 点**名字下的作品** → 看这个作品的角色
                                onFilterSeries = {
                                    state.animaFilterByCopyright(
                                        character.copyrightSlug,
                                        character.copyrightName,
                                    )
                                },
                            )
                        }
                    }
                }
            }
            if (state.animaBusy && !empty) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(22.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        // ---- 底部：统计 + 翻页
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                t("anima.total") + " " + state.animaTotal.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { state.animaGoPage(state.animaPage - 1) },
                enabled = state.animaPage > 1 && !state.animaBusy,
            ) { Text(t("anima.prevPage")) }
            Text(
                "${state.animaPage} / ${state.animaPages}",
                style = MaterialTheme.typography.labelMedium,
            )
            TextButton(
                onClick = { state.animaGoPage(state.animaPage + 1) },
                enabled = state.animaPage < state.animaPages && !state.animaBusy,
            ) { Text(t("anima.nextPage")) }
        }
    }

    // ---- 筛选面板（底部弹层，比塞进页面更省地方）
    if (filtersOpen) {
        ModalBottomSheet(onDismissRequest = { filtersOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(t("anima.filtersTitle"), style = MaterialTheme.typography.titleMedium)
                state.animaFacets.forEach { facet ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        // 维度名与取值名都显示中文；**发请求时用的仍是服务端的英文 value**
                        AnimaDexLabels.facet(facet.key, facet.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(facet.values) { value ->
                            Chip(
                                label = AnimaDexLabels.value(value.value, value.label),
                                count = value.count,
                                selected = state.animaFilters[facet.key] == value.value,
                                onClick = { state.animaToggleFilter(facet.key, value.value) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ---- 详情：**全屏**展示（完整立绘 + 提示词），按返回键/下滑抽屉退下
    state.animaDetail?.let { character ->
        ModalBottomSheet(
            onDismissRequest = { state.animaCloseDetail() },
            // skipPartiallyExpanded：一拉就到底，不做"半开"，否则立绘头上会被截掉
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.fillMaxSize(),
        ) {
            AnimaCharacterDetail(
                character = character,
                t = t,
                clipboard = clipboard,
                onCopied = onCopied,
                onAppend = {
                    state.animaAppendPrompt(character.trigger)
                    state.animaCloseDetail()
                },
                onFilterSeries = { slug, name -> state.animaFilterByCopyright(slug, name) },
                onClose = { state.animaCloseDetail() },
            )
        }
    }
}

/**
 * 一张角色卡。**三个点击区**（用户要求，点开详情之前就能直接用）：
 *  · 立绘 → 开全屏详情；
 *  · 名字 → **只复制名字**；
 *  · 名字下的作品 → **看这个作品的角色**（按该作品筛选）。
 */
@Composable
private fun CharacterCard(
    character: AnimaCharacter,
    onOpen: () -> Unit,
    onCopyName: () -> Unit,
    onFilterSeries: () -> Unit,
) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        NetworkImage(
            url = character.thumbUrl.takeIf { character.hasImage },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clickable(onClick = onOpen),
        )
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                character.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onCopyName)
                    .padding(vertical = 1.dp),
            )
            // 作品名显示中文（查询用的还是英文 slug）
            Text(
                AnimaDexLabels.series(character.copyrightSlug, character.copyrightName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClick = onFilterSeries)
                    .padding(vertical = 2.dp),
            )
            Text(
                "${formatCount(character.count)} 图 · ♥ ${character.favCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onOpen),
            )
        }
    }
}

/** 一张作品卡（作品视图）：2×2 拼贴缩略图 + 作品名 + 角色数。 */
@Composable
private fun SeriesCard(series: AnimaSeries, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
    ) {
        NetworkImage(
            url = series.thumbUrl.takeIf { series.hasImage },
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                AnimaDexLabels.series(series.slug, series.name),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${series.count} 个角色",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 视图切换的一段（角色 / 作品）。 */
@Composable
private fun ModeSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 齿轮按钮（打开"每行几个"的设置）。 */
@Composable
private fun IconChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/**
 * 详情：**整屏**。
 *
 * 布局是"立绘占满剩余空间 + 下面一块可滚动的信息区"：
 *  · 立绘用 `ContentScale.Fit`（等比缩放、**不裁切**）——早先给固定 300dp 高，头会被切掉；
 *  · 信息区（提示词 / 标签 / 按钮）最高占屏 45%，内容多了自己滚，不会把立绘挤没了；
 *  · 名字点一下复制，作品点一下筛选（和卡片上的手感一致），右上角可关闭，返回键也能退下。
 */
@Composable
private fun AnimaCharacterDetail(
    character: AnimaCharacter,
    t: (String) -> String,
    clipboard: ClipboardManager,
    onCopied: (String) -> Unit,
    onAppend: () -> Unit,
    onFilterSeries: (String, String) -> Unit,
    onClose: () -> Unit,
) {
    // 窗口高度走共用的 LocalWindowSize（原来读 LocalConfiguration.screenHeightDp，那是 Android 专属）
    val screenHeight = LocalWindowSize.current.height
    Column(Modifier.fillMaxSize()) {
        // ---- 顶部：名字 / 作品 / 关闭
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    character.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(character.name))
                            onCopied(t("anima.copiedName"))
                        }
                        .padding(vertical = 2.dp),
                )
                Text(
                    AnimaDexLabels.series(character.copyrightSlug, character.copyrightName),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onFilterSeries(character.copyrightSlug, character.copyrightName) }
                        .padding(vertical = 3.dp, horizontal = 2.dp),
                )
            }
            TextButton(onClick = onClose) { Text(t("anima.close")) }
        }
        Text(
            t("anima.tapNameHint"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // ---- 立绘：占满剩余空间，等比完整显示。
        // **渐进显示**：先铺缩略图（已经在缓存里，立刻可见），原图（几 MB 的 PNG）下好了盖在上面，
        // 否则刚打开时这里是一块空白，看着像坏了。
        if (character.hasImage) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp),
            ) {
                NetworkImage(
                    url = character.thumbUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                if (character.imgUrl.isNotBlank()) {
                    NetworkImage(
                        url = character.imgUrl,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // ---- 信息区：可滚动，最高 45% 屏高
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.45f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                t("anima.triggerLabel"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                character.trigger,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(10.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                t("anima.tagsLabel") + " · " + character.tags.size,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                character.tags.forEach { tag ->
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(character.trigger))
                        onCopied(t("anima.copiedTrigger"))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                ) { Text(t("anima.copyTrigger")) }
                TextButton(
                    onClick = onAppend,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary),
                ) {
                    Text(
                        t("anima.appendPrompt"),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                t("anima.appendHint"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** 小胶囊按钮（和网页版 `.chip` 一个形态）。 */
@Composable
private fun Chip(label: String, count: Int? = null, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = if (count == null) label else "$label $count",
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

/** 大数字换成人话（1.8 万），中文语境更好读。 */
private fun formatCount(count: Int): String =
    if (count >= 10000) "%.1f万".format(count / 10000f) else count.toString()
