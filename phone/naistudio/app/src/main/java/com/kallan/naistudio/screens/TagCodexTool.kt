package com.kallan.naistudio.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.kallan.naistudio.ui.RefModalBottomSheet as ModalBottomSheet
import com.kallan.naistudio.ui.RefSwitch as Switch
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.models.TagCodexEntry
import com.kallan.naistudio.models.TagCodexGridLayout
import com.kallan.naistudio.models.TagCodexNode
import com.kallan.naistudio.models.TagCodexProtocol
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.NetworkImage
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.kallan.naistudio.ui.RefBtn
import com.kallan.naistudio.ui.RefBtnKind
import com.kallan.naistudio.ui.RefFChip
import com.kallan.naistudio.ui.RefHint
import com.kallan.naistudio.ui.RefSheetTitle
import com.kallan.naistudio.ui.SlidersIcon

/**
 * 「画师超市」—— 法典图鉴（NovelAI v5 画师词典）的图鉴式浏览。
 *
 * UI 照着**角色图鉴**（[AnimaDexTool]）做：顶部一排控制、中间网格、点图开全屏详情。
 * 但底下是两种数据：
 *
 *  · 角色图鉴走**服务端检索 + 分页**（AnimaDex 有 facets / search 接口）；
 *  · 这里**整本拉下来本地筛**（法典图鉴没有检索接口，站点自己也是这么干的），
 *    所以**没有翻页**，搜索是零延迟的。
 *
 * 点一下缩略图 = 开详情；详情里可以复制画师串、或直接追加到生成页的正面提示词。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagCodexTool(
    state: AppState,
    t: (String) -> String,
    clipboard: ClipboardManager,
    onCopied: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filtersOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var codexMenuOpen by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<TagCodexEntry?>(null) }

    // 列表滚动位置：**必须建在网格那一层之上** —— 第 ㊿d 批起详情改成上拉抽屉 ✓，
    // 列表不再被卸载 ✓，但它仍然建在这里（改了详情形态也不必再挪 ✓）。
    val gridState = rememberLazyListState()

    LaunchedEffect(Unit) { state.tagCodexEnsureLoaded() }

    val doc = state.tagCodexDoc
    val filtered = state.tagCodexFiltered

    // ⚠️ **第 ㊿d 批（2026-09-22）**：详情从**整屏替换**改成**上拉抽屉** ✓。
    //
    // 用户报：「**点击图片看到完整提示词时点击返回不是退回首页而是退回画师超市界面 ✓，
    //   相当于点关闭 ✓，点击图片时有角色图鉴里点击角色同款的上拉动画**」✓
    //
    // **根因**：原来是 `detail?.let { ...; return }` ✓ —— 它把整棵列表子树**卸载**掉 ✓，
    //   于是系统返回键一路往上冒 ✓，落到工具页外壳上 ⇒ **退回首页** ✗。
    // **修法**：换成 `ModalBottomSheet` ✓（与 `AnimaDexTool` 点角色**同一套** ✓）——
    //   · 上拉滑入动画 ✓、下滑 / 返回键 / 点关闭都是 `onDismissRequest` ✓；
    //   · 关掉**只清 `detail`** ✓，人还在画师超市这一页 ✓（列表一直在下面 ✓，
    //     滚动位置也因此天然保住 ✓，比原来那个 `gridState` 提级更稳 ✓）。

    Column(modifier.fillMaxSize()) {
        // 网页 `.hint`
        RefHint(t("tagcodex.hintShort"), modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp))
        // ---- 第一行：法典选择 / 筛选 / 列数（网页 `.chips.nowrap` + `.fchip`）
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box {
                RefFChip(
                    text = (doc?.summary?.title ?: t("tagcodex.title")) + " ▾",
                    on = true,
                    onClick = { codexMenuOpen = true },
                    modifier = Modifier.widthIn(max = 150.dp),
                )
                DropdownMenu(expanded = codexMenuOpen, onDismissRequest = { codexMenuOpen = false }) {
                    state.tagCodexList.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        item.title,
                                        color = if (item.id == state.tagCodexId) {
                                            com.kallan.naistudio.ui.LocalRef.current.accent
                                        } else {
                                            com.kallan.naistudio.ui.LocalRef.current.text
                                        },
                                    )
                                    Text(
                                        t("tagcodex.codexMeta")
                                            .replace("{count}", item.entryCount.toString())
                                            .replace("{version}", item.version.ifBlank { "-" }),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = com.kallan.naistudio.ui.LocalRef.current.faint,
                                    )
                                }
                            },
                            onClick = {
                                state.tagCodexSelect(item.id)
                                codexMenuOpen = false
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(t("tagcodex.refresh")) },
                        onClick = {
                            state.tagCodexReload()
                            codexMenuOpen = false
                        },
                    )
                }
            }

            RefBtn(
                text = if (filtersOpen) t("tagcodex.filtersHide") else t("tagcodex.filtersShow"),
                onClick = { filtersOpen = !filtersOpen },
                kind = RefBtnKind.Ghost,
                small = true,
                icon = SlidersIcon,
            )
            if (state.tagCodexHasFilters) {
                Text(
                    t("tagcodex.filtersActive") + " " + filtered.size,
                    fontSize = 12.5.sp,
                    color = com.kallan.naistudio.ui.LocalRef.current.accent,
                )
                RefBtn(
                    text = t("tagcodex.filtersClear"),
                    onClick = { state.tagCodexClearFilters() },
                    kind = RefBtnKind.Ghost,
                    small = true,
                )
            }

            Spacer(Modifier.weight(1f))

            state.tagCodexError?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.labelSmall,
                    color = com.kallan.naistudio.ui.LocalRef.current.negative,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(120.dp),
                )
            }

            Box {
                TagCodexChip(label = t("tagcodex.columns"), selected = false) { settingsOpen = true }
                DropdownMenu(expanded = settingsOpen, onDismissRequest = { settingsOpen = false }) {
                    listOf(2, 3, 4).forEach { columns ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    t("tagcodex.columnsN").replace("{n}", columns.toString()),
                                    color = if (state.tagCodexColumns == columns) {
                                        com.kallan.naistudio.ui.LocalRef.current.accent
                                    } else {
                                        com.kallan.naistudio.ui.LocalRef.current.text
                                    },
                                )
                            },
                            onClick = {
                                state.tagCodexSetColumns(columns)
                                settingsOpen = false
                            },
                        )
                    }
                }
            }
        }

        // ---- 搜索框：**胶囊状**，与角色图鉴顶栏那个 SearchCapsule 同一套做法
        //（BasicTextField + RoundedCornerShape(50) + 一圈 outline 描边）。
        // 这里**不自动聚焦** —— 角色图鉴是"点放大镜才滑出来"所以应该自动聚焦，
        // 而这个胶囊是常驻的，进工具就弹键盘会很烦。
        TagCodexSearchCapsule(
            value = state.tagCodexQuery,
            onValueChange = { state.tagCodexQuery = it },
            placeholder = t("tagcodex.searchHint"),
        )

        // ---- 筛选面板
        if (filtersOpen && doc != null) {
            TagCodexFilterPanel(state = state, t = t)
        }

        // ---- 网格
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                doc == null && state.tagCodexBusy -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                doc == null -> Text(
                    state.tagCodexError ?: t("tagcodex.empty"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = com.kallan.naistudio.ui.LocalRef.current.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )

                filtered.isEmpty() -> Text(
                    t("tagcodex.noMatch"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = com.kallan.naistudio.ui.LocalRef.current.faint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )

                else -> {
                    // ⚠️ **第 ㊿d 批（2026-09-22）**：用户口径 ——
                    //   「**长图占据两个位置** ✓，比如我选列数 3 ✓，如果有一张长图 ✓，
                    //    那那一行就是一张长图、一张竖图 ✓；4 格也是 ✓ ——
                    //    一张长图两张竖图 ✓ 或两张长图 ✓ 或 4 张竖图 ✓。
                    //    三列两个长图的情况下把后面的竖图放上来 ✓，长图变为上下摆放 ✓。」
                    //
                    // **摆放全交给 [TagCodexGridLayout]** ✓（纯逻辑 ✓、有单测 ✓，
                    // 见 `TagCodexGridLayoutTest` ✓）—— 界面只负责按它给的行画 ✓。
                    val cols = state.tagCodexColumns
                    val layoutRows = remember(filtered, cols) {
                        TagCodexGridLayout.rows(
                            ratios = filtered.map { e ->
                                if (e.width > 0 && e.height > 0) {
                                    e.width.toFloat() / e.height.toFloat()
                                } else {
                                    0f
                                }
                            },
                            columns = cols,
                        )
                    }
                    LazyColumn(
                        state = gridState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            layoutRows.size,
                            // ⚠️ **第 ㊿f 批**：key 用**条目 id** ✓，不用下标 ✗。
                            //
                            // 用户报「换到别的目录**图加载不出来**」✓ —— 这是另一半原因 ✓：
                            // 原来 key 是 `slots.first().index` ✓（一行里第一个的**下标**✓）——
                            // 切目录后下标**还是 0/1/2…** ✓，Compose 认为"还是那批 item" ✓
                            // ⇒ **复用旧的 Composable** ✓ ⇒ `remember(url)` 里的旧状态被留着 ✓
                            // ⇒ 屏上还是**上一本的图**（或空白 ✗）。
                            // 换成**条目 id**（全局唯一 ✓）⇒ 换了书就是全新 item ✓，
                            // 该重建的都重建 ✓。
                            key = { layoutRows[it].slots.first().let { s -> filtered[s.index].id } },
                        ) { rowIndex ->
                            val row = layoutRows[rowIndex]
                            // ⚠️ **第 ㊿e3 批（2026-09-22）**：`FlowRow` → **`Row` + `weight`** ✓。
                            //
                            // 用户报：「**横图只是拉长了窗口，不是等比放大，横图右边也没有竖图**」✓
                            //
                            // **根因**：`FlowRow` 是按**子项实测宽度**换行的 ✓，而我给子项的是
                            //   `fillMaxWidth(frac)` ✓ —— 它在 `FlowRow` 的约束下会撑到**可用宽度** ✓，
                            //   于是「2 格宽的横图 + 1 格宽的竖图」被判为**放不下** ✓
                            //   ⇒ 竖图被挤到**下一行** ✓（正是"右边没有竖图" ✗）。
                            //   而横图自己又因为约束给错 ✓，图片没等比放大 ✗（"只是拉长了窗口"✗）。
                            //
                            // **修法**：一行既然是**算好的** ✓（`TagCodexGridLayout` ✓），
                            //   就用**固定权重的 `Row`** 排 ✓ —— `weight(span)` 是精确分格 ✓，
                            //   不存在"实测宽度判错"这回事 ✓。
                            //   ⚠️ 没排满的位置**补一个 `Spacer(weight)`** ✓ 占住空位 ✓
                            //   （用户：「格子占不完可留白」✓）。
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                row.slots.forEach { slot ->
                                    val entry = filtered[slot.index]
                                    TagCodexCard(
                                        state = state,
                                        entry = entry,
                                        t = t,
                                        span = slot.span,
                                        columns = cols,
                                        onOpen = { detail = entry },
                                        onCopyTags = {
                                            clipboard.setText(AnnotatedString(entry.tags))
                                            onCopied(t("tagcodex.copied"))
                                        },
                                    )
                                }
                                val used = row.slots.sumOf { it.span }
                                if (used < cols) {
                                    Spacer(Modifier.weight((cols - used).toFloat()))
                                }
                            }
                        }
                    }
                }
            }
            if (state.tagCodexBusy && doc != null) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp).size(22.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        // ---- 底栏：统计 + 授权说明
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t("tagcodex.total") + " " + (doc?.entries?.size ?: 0),
                    fontSize = 12.5.sp,
                    color = com.kallan.naistudio.ui.LocalRef.current.faint,
                )
                Spacer(Modifier.width(10.dp))
                if (doc != null && filtered.size != doc.entries.size) {
                    Text(
                        t("tagcodex.matched") + " " + filtered.size,
                        fontSize = 12.5.sp,
                        color = com.kallan.naistudio.ui.LocalRef.current.accent,
                    )
                }
                Spacer(Modifier.weight(1f))
                doc?.summary?.version?.takeIf { it.isNotBlank() }?.let { version ->
                    Text(
                        version,
                        style = MaterialTheme.typography.labelSmall,
                        color = com.kallan.naistudio.ui.LocalRef.current.faint,
                    )
                }
            }
            Text(
                t("tagcodex.license"),
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.faint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    // ---- 详情：**上拉抽屉** ✓（与角色图鉴点角色同一套 ✓，第 ㊿d 批 ✓）
    detail?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { detail = null },
            // ⚠️ `skipPartiallyExpanded` ✓：一拉就到底 ✓（不做"半开"✓，
            //    否则大图头上会被截掉 ✓）—— 与 `AnimaDexTool` 逐字一致 ✓。
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.fillMaxSize(),
        ) {
            TagCodexDetail(
                state = state,
                entry = entry,
                t = t,
                clipboard = clipboard,
                onCopied = onCopied,
                onAppend = { state.tagCodexAppendPrompt(entry.tags) },
                onClose = { detail = null },
            )
        }
    }
}

/** 筛选面板：目录树下钻 + 更新批次 + 只看新增。 */
@Composable
private fun TagCodexFilterPanel(state: AppState, t: (String) -> String) {
    val doc = state.tagCodexDoc ?: return
    val path = state.tagCodexPath

    // 当前这一层的节点：没下钻过就是顶层，否则是最后一个已选节点的 children
    val currentNodes: List<TagCodexNode> = run {
        var nodes = doc.tree
        path.forEach { name ->
            val hit = nodes.firstOrNull { it.name == name } ?: return@run emptyList()
            nodes = hit.children
        }
        nodes
    }

    val ref = com.kallan.naistudio.ui.LocalRef.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ref.field)
            .border(1.dp, ref.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 面包屑：点一下回退到该层
        if (path.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TextButton(onClick = { state.tagCodexSetPath(emptyList()) }) {
                    Text(t("tagcodex.allDirectories"), style = MaterialTheme.typography.labelSmall)
                }
                path.forEachIndexed { index, name ->
                    Text(
                        "›",
                        style = MaterialTheme.typography.labelSmall,
                        color = com.kallan.naistudio.ui.LocalRef.current.faint,
                    )
                    TextButton(onClick = { state.tagCodexSetPath(path.take(index + 1)) }) {
                        Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }

        if (currentNodes.isNotEmpty()) {
            Text(
                t("tagcodex.directory"),
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.faint,
            )
            // 横向可滑的一排目录胶囊（节点名长短差很多，不做网格）
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(currentNodes.size) { index ->
                    val node = currentNodes[index]
                    TagCodexChip(
                        label = node.name + " (" + node.count + ")",
                        selected = false,
                        onClick = { state.tagCodexSetPath(path + node.name) },
                    )
                }
            }
        }

        if (doc.updateFilters.isNotEmpty()) {
            Text(
                t("tagcodex.updates"),
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.faint,
            )
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(doc.updateFilters.size) { index ->
                    val (id, label) = doc.updateFilters[index]
                    TagCodexChip(
                        label = label,
                        selected = state.tagCodexUpdate == id,
                        onClick = { state.tagCodexSetUpdate(id) },
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = state.tagCodexNewOnly,
                onCheckedChange = { state.tagCodexNewOnly = it },
            )
            Text(
                doc.newFilterLabel.ifBlank { t("tagcodex.newOnly") },
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.faint,
            )
        }
    }
}

/**
 * 一张画师卡：缩略图 + 名字 + 目录路径。点图开详情，点名字复制画师串。
 *
 * ## ⚠️ 第 ㊿e 批（2026-09-22）定稿口径
 *
 * 用户原话：「**竖图属于一个宽度，长图属于两格宽度**」
 *          「**长图等比放大，占满格子，但要显示完整，格子占不完可留白**」
 *
 * **做法**（三件事必须一起做 ✓，少一件就是你报的那个毛病 ✗）：
 *
 * 1. **宽度**：竖图 = 1 格 ✓、长图 = 2 格 ✓（`fillMaxWidth(span / columns)` ✓）；
 * 2. **高度**：⚠️ **按「一格宽」算，与 span 无关** ✗ ——
 *    这是上一版最错的地方 ✓：我原来用 `aspectRatio(ratio)` 让**高度跟着宽度走** ✓，
 *    于是长图放成 2 格宽后高度**也翻倍** ✓ ⇒ 一格位置塞不下 ✓ ⇒
 *    看起来"长图占了一整行 / 竖图被挤成两格"✗（正是你报的 ✓）。
 * 3. **图片填充**：⚠️ `ContentScale.Fit` ✓（**不裁切** ✓）——
 *    `NetworkImage` 默认是 `Crop`（裁切 ✗）✓，那正是"没显示完整"的原因 ✓。
 */
@Composable
private fun RowScope.TagCodexCard(
    state: AppState,
    entry: TagCodexEntry,
    t: (String) -> String,
    onOpen: () -> Unit,
    onCopyTags: () -> Unit,
    span: Int = 1,
    columns: Int = 3,
) {
    val url = state.tagCodexImageUrl(entry)
    val ratio = if (entry.width > 0 && entry.height > 0) {
        entry.width.toFloat() / entry.height.toFloat()
    } else {
        0.72f
    }
    // ⚠️ **第 ㊿e3 批**：宽度用 **`weight(span)`** ✓ —— 在 `Row` 作用域里 ✓，
    //   精确按格分 ✓（不再用 `fillMaxWidth(frac)` ✗ —— 那在 `FlowRow` 下会判错换行 ✓）。
    val boxRatio = ratio.coerceIn(0.15f, 4f)
    Column(Modifier.weight(span.toFloat()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                // ⚠️ **用图自己的比例** ✓ —— 容器就按这张图的形状给 ✓，
                //   于是图片在容器里**等比撑满** ✓（`Fit` 下宽高比一致 ⇒ 无留白、不裁切 ✓）。
                //   一格宽的竖图与两格宽的横图，**高度按各自比例算** ✓，
                //   这正是"横图等比放大"要的效果 ✓。
                .aspectRatio(boxRatio)
                .clip(RoundedCornerShape(16.dp))
                .background(com.kallan.naistudio.ui.LocalRef.current.field)
                .border(1.dp, com.kallan.naistudio.ui.LocalRef.current.border, RoundedCornerShape(16.dp))
                .clickable(onClick = onOpen),
        ) {
            NetworkImage(
                url = url.ifBlank { null },
                modifier = Modifier.fillMaxSize(),
                // ⚠️ **Fit** ✓ —— 等比放大占满 ✓、**完整显示不裁切** ✓、富余留白 ✓
                contentScale = ContentScale.Fit,
            )
            // 新词条右上角点一个标记（站点也是这么标的）
            if (entry.isNew) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(com.kallan.naistudio.ui.LocalRef.current.accent),
                )
            }
        }
        Text(
            entry.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = com.kallan.naistudio.ui.LocalRef.current.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onCopyTags)
                .padding(vertical = 2.dp),
        )
        if (entry.path.isNotEmpty()) {
            Text(
                entry.path.joinToString(" / "),
                fontSize = 11.5.sp,
                color = com.kallan.naistudio.ui.LocalRef.current.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 全屏详情：大图 + 画师串 + 复制 / 填入提示词。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagCodexDetail(
    state: AppState,
    entry: TagCodexEntry,
    t: (String) -> String,
    clipboard: ClipboardManager,
    onCopied: (String) -> Unit,
    onAppend: () -> Unit,
    onClose: () -> Unit,
) {
    val ref = com.kallan.naistudio.ui.LocalRef.current
    Column(Modifier.fillMaxSize()) {
        RefSheetTitle(title = entry.title, onClose = onClose)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 主图。有原图优先用原图（画风细节看缩略图会失真）
            val imageUrl = state.tagCodexImageUrl(entry, original = entry.hasOriginal)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, ref.border, RoundedCornerShape(16.dp)),
            ) {
                NetworkImage(
                    url = imageUrl.ifBlank { null },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }

            // 画师串：网页 `.kv`（标题 + 右侧「复制」）；每个 tag 仍可单独点复制 ✓
            TagKv(label = t("tagcodex.tags"), copyLabel = t("tagcodex.copy"), onCopy = if (entry.hasPositive) {
                {
                    clipboard.setText(AnnotatedString(entry.tags.trim()))
                    onCopied(t("tagcodex.copiedPositive"))
                }
            } else {
                null
            }) {
                TagZhLine(
                    state = state,
                    text = entry.tags,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    zhStyle = MaterialTheme.typography.labelSmall,
                    clipboard = clipboard,
                    modifier = Modifier.fillMaxWidth(),
                    textColor = ref.accent,
                    onCopied = { onCopied(t("tagcodex.copied")) },
                )
            }

            // ⚠️ **第 ㊿ 批（2026-09-21）**：用户报「**显示出来的只有画师串/风格串,
            //    人物提示词和负面提示词没有显示出来**」✓
            //
            // **根因**：上一批（㊾）只加了**底部那三颗复制按钮** ✗，**内容区压根没渲染**这两块 ✗
            //   —— 详情页从头到尾只画了 `entry.tags` 一条 ✓。用户要的是"**显示出来**"✓，
            //   我只做了一半 ✓（复制有了、看得见没有 ✓）⇒ 用户看到的就是"只有画师串"✓。
            //
            // **修法**：把人物词、负面词也**画出来** ✓ —— 与画师串并列、样式同源 ✓，
            //   每块**点一下单独复制** ✓（与底部那三颗按钮同一套行为 ✓）。
            //   ⚠️ 没有数据的法典（画师词典 ✓）这两块**整块不画** ✗（不画空标题 ✗）。

            // ---- 人物提示词（每个角色一块 ✓）----
            if (entry.hasCharacters) {
                Text(
                    t("tagcodex.charactersTitle") +
                        if (entry.characters.size > 1) "（${entry.characters.size}）" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = com.kallan.naistudio.ui.LocalRef.current.faint,
                )
                entry.characters.forEachIndexed { index, character ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                clipboard.setText(AnnotatedString(character.prompt.trim()))
                                onCopied(t("tagcodex.copiedCharacters"))
                            }
                            .background(ref.field)
                            .border(1.dp, ref.border, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // ⚠️ 这里**保留 `char1` 槽名** ✓（**复制时**才去掉 ✓）——
                        //   按角色分槽精确填的用户需要看到"这是第几个角色" ✓
                        //  （原网页正是这个分工：灯箱里带槽名、一键复制去掉槽名 ✓）。
                        Text(
                            character.label.ifBlank { "char${index + 1}" },
                            style = MaterialTheme.typography.labelSmall,
                            color = com.kallan.naistudio.ui.LocalRef.current.faint,
                        )
                        TagZhLine(
                            state = state,
                            text = character.prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            zhStyle = MaterialTheme.typography.labelSmall,
                            clipboard = clipboard,
                            textColor = com.kallan.naistudio.ui.LocalRef.current.accent,
                            onCopied = { onCopied(t("tagcodex.copiedCharacters")) },
                        )
                    }
                }
            }

            // ---- 负面提示词（`.kv`）----
            if (entry.hasNegative) {
                TagKv(label = t("tagcodex.negativeTitle"), copyLabel = t("tagcodex.copy"), onCopy = {
                    clipboard.setText(AnnotatedString(entry.negative.trim()))
                    onCopied(t("tagcodex.copiedNegative"))
                }) {
                    TagZhLine(
                        state = state,
                        text = entry.negative,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        zhStyle = MaterialTheme.typography.labelSmall,
                        clipboard = clipboard,
                        textColor = ref.negative,
                        modifier = Modifier.fillMaxWidth(),
                        onCopied = { onCopied(t("tagcodex.copiedNegative")) },
                    )
                }
            }

            if (entry.path.isNotEmpty()) {
                Text(
                    entry.path.joinToString(" / "),
                    style = MaterialTheme.typography.labelSmall,
                    color = com.kallan.naistudio.ui.LocalRef.current.faint,
                )
            }

            // 同一个画师的多张例图
            if (entry.images.size > 1) {
                Text(
                    t("tagcodex.moreSamples").replace("{n}", entry.images.size.toString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = com.kallan.naistudio.ui.LocalRef.current.faint,
                )
                entry.images.drop(1).forEach { item ->
                    val url = state.tagCodexImageUrl(
                        entry.copy(image = item.path, original = item.original),
                        original = item.original.isNotBlank(),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, ref.border, RoundedCornerShape(16.dp)),
                    ) {
                        NetworkImage(
                            url = url.ifBlank { null },
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        // ⚠️ **第 ㊾ 批（2026-09-21）**：用户口径 ——
        //   「**原网页可选复制正面提示词,人物提示词,负面提示词,加上**」✓（双端都要 ✓）
        //
        // 口径**照原网页 `assets/app/copy.js` 逐条对齐** ✓（不自创 ✓）：
        //  · **正面** = `entry.tags` ✓；
        //  · **人物** = 各角色 `prompt`（**去掉 `char1:` 标记** ✓）；
        //  · **负面** = `entry.negative` ✓（**绝不并进正面** ✗ —— 那是"不要什么"✓）。
        //
        // ⚠️ **按钮按"数据里真的有吗"来出** ✗（画师词典那本只有正面 ✓）。
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawLine(ref.border, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.hasPositive) {
                    RefBtn(
                        text = t("tagcodex.copyPositive"),
                        onClick = {
                            clipboard.setText(AnnotatedString(entry.tags.trim()))
                            onCopied(t("tagcodex.copiedPositive"))
                        },
                        modifier = Modifier.weight(1f),
                        kind = RefBtnKind.Secondary,
                        small = true,
                    )
                }
                if (entry.hasCharacters) {
                    RefBtn(
                        text = t("tagcodex.copyCharacters"),
                        onClick = {
                            clipboard.setText(AnnotatedString(entry.characterPromptText))
                            onCopied(t("tagcodex.copiedCharacters"))
                        },
                        modifier = Modifier.weight(1f),
                        kind = RefBtnKind.Secondary,
                        small = true,
                    )
                }
                if (entry.hasNegative) {
                    RefBtn(
                        text = t("tagcodex.copyNegative"),
                        onClick = {
                            clipboard.setText(AnnotatedString(entry.negative.trim()))
                            onCopied(t("tagcodex.copiedNegative"))
                        },
                        modifier = Modifier.weight(1f),
                        kind = RefBtnKind.Secondary,
                        small = true,
                    )
                }
            }
            // 「填入正面提示词」= 送到生成页（网页 `.btn-primary.grow`）
            RefBtn(text = t("tagcodex.append"), onClick = onAppend, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** 网页 `.kv`：field 底 + border + 圆角 12 + padding 10/12；k 12 faint（右侧 ghost-sm 复制）。 */
@Composable
private fun TagKv(label: String, copyLabel: String, onCopy: (() -> Unit)?, content: @Composable () -> Unit) {
    val ref = com.kallan.naistudio.ui.LocalRef.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ref.field)
            .border(1.dp, ref.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = ref.faint, modifier = Modifier.weight(1f))
            if (onCopy != null) {
                RefBtn(text = copyLabel, onClick = onCopy, kind = RefBtnKind.Ghost, small = true)
            }
        }
        content()
    }
}

/** 一颗小胶囊。工具页里到处都要，本地留一份（AnimaDexTool 那份是私有的）。 */
@Composable
private fun TagCodexChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    RefFChip(text = label, on = selected, onClick = onClick)
}

/**
 * 胶囊搜索框。
 *
 * 与 `StudioShell.SearchCapsule` 同一套画法（那边是私有的，这里照抄一份）：
 * `BasicTextField` + 圆角 50% + `surfaceContainer` 底色 + 一圈 `outline` 描边。
 * 描边不是装饰 —— 深色底上胶囊原本会和背景糊在一起，淡描边能把边界说清楚。
 *
 * 区别只有一个：**这里不请求焦点**。角色图鉴那个是"点放大镜才滑出来"，
 * 滑出来就该能直接打字；这个胶囊常驻在工具页上，自动聚焦会让键盘一直挡着网格。
 */
@Composable
private fun TagCodexSearchCapsule(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = com.kallan.naistudio.ui.LocalRef.current.text,
            fontSize = 14.sp,
        ),
        cursorBrush = SolidColor(com.kallan.naistudio.ui.LocalRef.current.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // 检索是**本地实时**的，回车没有额外动作，收键盘就够了
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(com.kallan.naistudio.ui.LocalRef.current.field)
            .border(1.dp, com.kallan.naistudio.ui.LocalRef.current.borderStrong, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        fontSize = 14.sp,
                        color = com.kallan.naistudio.ui.LocalRef.current.faint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            }
        },
    )
}

/**
 * **一份 tag，每个 tag 下面跟一行中文翻译** ✓
 * （第 ㊿ 批 2026-09-21 起 ✓，第 ㊿b 批 2026-09-22 改口径 ✓，**双端同一份** ✓）。
 *
 * ## 用户口径（第 ㊿b 批）
 *
 * 「**不还是要上一版，去掉旧的 tag 显示，用新的 tag 加翻译**」✓
 *  · 屏上**只许有一份 tag** ✓ —— 原来那块整段原文 `Text` **已删除** ✗；
 *  · 本函数**既是原文的画者、也是翻译的画者** ✓（合成一处 ✓，不再两处各画一份 ✓）；
 *  · 每个 tag **一行英文** ✓、**下一行小字中文** ✓，**按逗号切** ✓；
 *  · **查不到中文的那个 tag 只画英文** ✓（不留空行 ✗）；
 *  · ⚠️ **点一下复制的是 `text` 原文** ✓ —— 中文只是**画上去** ✓，
 *    一个字节都不掺进复制内容 ✓（`copy.js` 的 `entryPromptText` 也是拿原文 ✓）。
 *
 * ## 为什么合成一处（上一版两次踩的坑）
 *
 *  · ㊿ 版：调用点画整段原文 + 本函数**又画一遍原文** ⇒ **tag 两份** ✗；
 *  · ㊿ 版改后：调用点画原文 + 本函数只画中文 ⇒ 原文和翻译**分成两段** ✓，
 *    但用户看到的仍是"旧的那块 tag" + "另一列翻译" ⇒ **不是他要素的形态** ✗。
 *  · **本版**：调用点**不再画原文** ✗，全部交给本函数 ✓ ⇒ **一份 tag、下面跟翻译** ✓。
 *
 * ## 画法（第 ㊿c 批 2026-09-22 改）
 *
 * 「**tag 显示是一行一个，不行，变为排列形式，自动换行，每个 tag 都可以直接点击复制**」✓
 *
 * **`FlowRow` 横排** ✓、**自动换行** ✓ —— 不再一段一行竖着排 ✗（那是长条，扫起来费劲 ✗）。
 * **每个 tag 一枚可点胶囊** ✓：
 *  · 点它**只复制这一个 tag** 的原文 ✓（不是整串 ✓）；
 *  · 英文在上、中文在下（**都在胶囊里** ✓，中文有就画、没有就只画英文 ✓）。
 *
 * ⚠️ **复制的是 `piece.text` 单个 tag** ✓ —— 中文**绝不掺进**复制内容 ✓。
 * ⚠️ **整块底部那三颗按钮**（复制正向/人物/反向）仍然复制**整串** ✓，两者分工不同 ✓。
 *
 * @param modifier 加在整块上（底色 / 圆角的容器那几处传进来 ✓）。
 * @param textColor 英文原文的颜色（画师串和人物词用主色 ✓、负面词用错误色 ✓）。
 * @param clipboard 剪贴板（**本文件统一由参数传** ✓，与上面那几处复制同一个来源 ✓）。
 * @param onCopied 点单个 tag 复制成功后的提示回调 ✓。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagZhLine(
    state: AppState,
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    zhStyle: androidx.compose.ui.text.TextStyle,
    clipboard: ClipboardManager,
    modifier: Modifier = Modifier,
    textColor: androidx.compose.ui.graphics.Color = com.kallan.naistudio.ui.LocalRef.current.accent,
    onCopied: (String) -> Unit = {},
) {
    // ⚠️ 词表没备好也**照画英文** ✓（不能因为中文没到就把原文藏起来 ✗ —— 那是丢内容 ✓）
    val pieces = if (state.tagCodexZhReady) state.tagCodexZhPieces(text) else null

    if (pieces == null) {
        Text(text, style = style, color = textColor, modifier = modifier)
        return
    }

    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        pieces.forEach { (piece, zh) ->
            if (piece.text.isBlank()) return@forEach
            Column(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        // ⚠️ **只复制这一个 tag 的原文** ✓（`piece.text` ✓，不带分隔符 ✓、不带中文 ✓）
                        clipboard.setText(AnnotatedString(piece.text))
                        onCopied(piece.text)
                    }
                    .background(com.kallan.naistudio.ui.LocalRef.current.subtle)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(
                    piece.text,
                    style = style,
                    color = textColor,
                )
                if (zh != null) {
                    Text(
                        zh,
                        style = zhStyle,
                        color = com.kallan.naistudio.ui.LocalRef.current.faint,
                    )
                }
            }
        }
    }
}
