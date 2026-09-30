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
import com.kallan.naistudio.ui.RefModalBottomSheet as ModalBottomSheet
import com.kallan.naistudio.ui.RefOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import com.kallan.naistudio.ui.RefTextButton as TextButton
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
import com.kallan.naistudio.ui.NetworkImage
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.kallan.naistudio.ui.LocalRef
import com.kallan.naistudio.ui.RefBtn
import com.kallan.naistudio.ui.RefBtnKind
import com.kallan.naistudio.ui.RefHint
import com.kallan.naistudio.ui.RefSeg
import com.kallan.naistudio.ui.RefFChip
import com.kallan.naistudio.ui.RefSheetTitle
import com.kallan.naistudio.ui.SlidersIcon

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

        val ref = LocalRef.current
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RefSeg(
                options = listOf(t("anima.modeCharacters"), t("anima.modeSeries")),
                selected = if (state.animaMode == "copyrights") 1 else 0,
                onSelect = { i -> state.animaSetMode(if (i == 1) "copyrights" else "characters") },
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LazyRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(AnimaDexProtocol.SORTS) { sort ->
                        RefFChip(
                            text = t("anima.sort.$sort"),
                            on = state.animaSort == sort,
                            onClick = { state.animaSetSort(sort) },
                        )
                    }
                }
                val active = state.animaFilters.size
                RefBtn(
                    text = t("anima.filtersShow") + if (active > 0) " · $active" else "",
                    onClick = { filtersOpen = !filtersOpen },
                    kind = RefBtnKind.Ghost,
                    small = true,
                    icon = SlidersIcon,
                )
                // 齿轮：每行展示多少个
                Box {
                    RefBtn(
                        text = t("anima.settings"),
                        onClick = { settingsOpen = true },
                        kind = RefBtnKind.Ghost,
                        small = true,
                    )
                    DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }) {
                        Text(
                            t("anima.columnsLabel"),
                            fontSize = 12.5.sp,
                            color = ref.muted,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                        listOf(2, 3, 4).forEach { columns ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        t("anima.columnsN").replace("{n}", columns.toString()),
                                        color = if (state.animaColumns == columns) ref.accent else ref.text,
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
            if (state.animaFilters.isNotEmpty() || state.animaError != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.animaFilters.isNotEmpty()) {
                        Text(
                            t("anima.filtersActive") + " ${state.animaFilters.size}",
                            fontSize = 12.5.sp,
                            color = ref.accent,
                        )
                        RefBtn(
                            text = t("anima.filtersClear"),
                            onClick = { state.animaClearFilters() },
                            kind = RefBtnKind.Ghost,
                            small = true,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    state.animaError?.let { message ->
                        Text(
                            message,
                            fontSize = 12.sp,
                            color = ref.negative,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                    fontSize = 13.5.sp,
                    color = LocalRef.current.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(if (state.animaMode == "copyrights") 2 else state.animaColumns),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp),
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
                    color = LocalRef.current.accent,
                )
            }
        }

        // ---- 底部 `.pager`：上一页 ghost / 「第 x / y 页 · 共 n」12.5 faint / 下一页 ghost
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RefBtn(
                text = t("anima.prevPage"),
                onClick = { state.animaGoPage(state.animaPage - 1) },
                enabled = state.animaPage > 1 && !state.animaBusy,
                kind = RefBtnKind.Ghost,
                small = true,
            )
            Text(
                "${state.animaPage} / ${state.animaPages} · ${t("anima.total")} ${state.animaTotal}",
                fontSize = 12.5.sp,
                color = LocalRef.current.faint,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            RefBtn(
                text = t("anima.nextPage"),
                onClick = { state.animaGoPage(state.animaPage + 1) },
                enabled = state.animaPage < state.animaPages && !state.animaBusy,
                kind = RefBtnKind.Ghost,
                small = true,
            )
        }
    }

    // ---- 筛选面板（底部弹层，比塞进页面更省地方）
    if (filtersOpen) {
        ModalBottomSheet(onDismissRequest = { filtersOpen = false }) {
            RefSheetTitle(title = t("anima.filtersTitle"), onClose = { filtersOpen = false })
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.animaFacets.forEach { facet ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        // 维度名与取值名都显示中文；**发请求时用的仍是服务端的英文 value**
                        AnimaDexLabels.facet(facet.key, facet.label),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalRef.current.muted,
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
    val ref = LocalRef.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        NetworkImage(
            url = character.thumbUrl.takeIf { character.hasImage },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(16.dp))
                .background(ref.field)
                .border(1.dp, ref.border, RoundedCornerShape(16.dp))
                .clickable(onClick = onOpen),
        )
        Text(
            character.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = ref.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onCopyName),
        )
        // 作品名显示中文（查询用的还是英文 slug）；点 = 看这个作品的角色
        Text(
            AnimaDexLabels.series(character.copyrightSlug, character.copyrightName) +
                " · ${formatCount(character.count)}",
            fontSize = 11.5.sp,
            color = ref.faint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (-4).dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onFilterSeries),
        )
    }
}

/** 一张作品卡（作品视图）：2×2 拼贴缩略图 + 作品名 + 角色数。 */
@Composable
private fun SeriesCard(series: AnimaSeries, onClick: () -> Unit) {
    // `.dex-card.series`：ph 16:10
    val ref = LocalRef.current
    Column(
        Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        NetworkImage(
            url = series.thumbUrl.takeIf { series.hasImage },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(16.dp))
                .background(ref.field)
                .border(1.dp, ref.border, RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
        )
        Text(
            AnimaDexLabels.series(series.slug, series.name),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = ref.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${series.count} 个角色",
            fontSize = 11.5.sp,
            color = ref.faint,
            maxLines = 1,
            modifier = Modifier.offset(y = (-4).dp),
        )
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
    val screenHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
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
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = LocalRef.current.text,
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
                    fontSize = 12.sp,
                    color = LocalRef.current.faint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onFilterSeries(character.copyrightSlug, character.copyrightName) }
                        .padding(vertical = 3.dp, horizontal = 2.dp),
                )
            }
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = t("anima.close"),
                    tint = LocalRef.current.muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        RefHint(t("anima.tapNameHint"), modifier = Modifier.padding(horizontal = 16.dp))

        // ---- 立绘：占满剩余空间，等比完整显示。
        // **渐进显示**：先铺缩略图（已经在缓存里，立刻可见），原图（几 MB 的 PNG）下好了盖在上面，
        // 否则刚打开时这里是一块空白，看着像坏了。
        if (character.hasImage) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(LocalRef.current.field)
                    .border(1.dp, LocalRef.current.border, RoundedCornerShape(16.dp)),
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
            // `.kv`：提示词 + 右侧「复制」
            val ref = LocalRef.current
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ref.field)
                    .border(1.dp, ref.border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("anima.triggerLabel"), fontSize = 12.sp, color = ref.faint, modifier = Modifier.weight(1f))
                    RefBtn(
                        text = t("anima.copyTrigger"),
                        onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(character.trigger))
                            onCopied(t("anima.copiedTrigger"))
                        },
                        kind = RefBtnKind.Ghost,
                        small = true,
                    )
                }
                Text(character.trigger, fontSize = 13.5.sp, lineHeight = 20.sp, color = ref.text)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                t("anima.tagsLabel") + " · " + character.tags.size,
                fontSize = 12.5.sp,
                color = LocalRef.current.muted,
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                character.tags.forEach { tag ->
                    Text(
                        tag,
                        fontSize = 11.5.sp,
                        color = LocalRef.current.muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LocalRef.current.subtle)
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RefBtn(
                    text = t("anima.appendPrompt"),
                    onClick = onAppend,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            RefHint(t("anima.appendHint"))
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** 小胶囊按钮（和网页版 `.chip` 一个形态）。 */
@Composable
private fun Chip(label: String, count: Int? = null, selected: Boolean, onClick: () -> Unit) {
    RefFChip(
        text = if (count == null) label else "$label $count",
        on = selected,
        onClick = onClick,
    )
}

/** 大数字换成人话（1.8 万），中文语境更好读。 */
private fun formatCount(count: Int): String =
    if (count >= 10000) "%.1f万".format(count / 10000f) else count.toString()
