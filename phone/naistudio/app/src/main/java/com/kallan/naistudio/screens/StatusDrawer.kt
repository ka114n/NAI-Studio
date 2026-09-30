package com.kallan.naistudio.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.kallan.naistudio.ui.RefIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.kallan.naistudio.ui.RefTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.ConversationTurn
import com.kallan.naistudio.models.StatusLine
import com.kallan.naistudio.platform.LogLine
import com.kallan.naistudio.platform.recentLogs
import com.kallan.naistudio.state.AppState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import com.kallan.naistudio.ui.scrim

/**
 * 底部那行字幕 —— **点一下弹出抽屉**（用户 2026-09-16 的口径：
 * "将底下显示正在跑图 1/1… 那一行字幕做成一个抽屉，点击弹出"）。
 *
 * 空闲时也保留这一行（显示"点这里看对话 / 日志"），否则没话说的时候入口就消失了。
 */
@Composable
internal fun StatusBarLine(
    state: AppState,
    t: (String) -> String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ready = RuntimeText.text(state.settings.language, RuntimeText.READY)
    val text = state.displayStatus
    val idle = text == ready
    // 网页 `.statusline`：field 底、描边、圆角 8、12 faint（忙 = accent）+ 向上箭头
    com.kallan.naistudio.ui.RefStatusLine(
        text = if (idle) t("drawer.openHint") else text,
        busy = !idle && state.busy,
        onClick = onOpen,
        modifier = modifier,
    )
}

/**
 * **底部上弹的半屏抽屉**：左边竖排两个 Tab（对话 / 日志）。
 *
 * · 对话：一条条 AI 交互（用了什么模型、发了什么、回了什么）—— 纯展示，见 [ConversationTurn]；
 * · 日志：底部状态行的历史 + 网络/错误日志（`logInfo/logWarn/logError` 的内存环形缓冲）。
 *
 * 点上方遮罩或右上角 ✕ 收起。
 *
 * ⚠️ **用 Dialog 画，而不是就地画在 GenerateScreen 末尾**（2026-09-16 修）：
 * GenerateScreen 是 `HorizontalPager` 的一个 page，就地画的浮层能不能盖住、有没有高度
 * 全看 pager 那个槽位怎么量 —— 用户实测"点了不弹出"。Dialog 是**独立窗口**，
 * 永远在最上层，和父级布局无关。
 */
@Composable
internal fun StatusDrawer(
    state: AppState,
    t: (String) -> String,
    open: Boolean,
    onClose: () -> Unit,
) {
    if (!open) return
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DrawerBody(state = state, t = t, onClose = onClose)
    }
}

@Composable
private fun DrawerBody(state: AppState, t: (String) -> String, onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val time = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Box(Modifier.fillMaxSize()) {
        // 点空白处收起（网页 `.scrim`）
        val ref = com.kallan.naistudio.ui.LocalRef.current
        Box(
            Modifier
                .fillMaxSize()
                .background(ref.scrim())
                .clickable(onClick = onClose),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.55f),
            color = ref.panel,
            contentColor = ref.text,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ref.border),
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    com.kallan.naistudio.ui.RefSheetHandle()
                }
                com.kallan.naistudio.ui.RefSheetTitle(
                    title = t("conversation.title") + "/" + t("log.title"),
                    modifier = Modifier.padding(top = 6.dp),
                    extra = if (state.settings.llmMemoryEnabled) {
                        t("conversation.memory").replace("{n}", state.llmMemoryTotalRounds().toString())
                    } else {
                        null
                    },
                    actions = {
                        com.kallan.naistudio.ui.RefBtn(
                            text = t("conversation.clear"),
                            onClick = {
                                if (tab == 0) {
                                    state.clearConversation()
                                    // 记忆也跟着清：用户点「清空」的语义是"忘掉之前聊过什么"
                                    state.clearLlmMemory()
                                } else {
                                    state.clearStatusHistory()
                                    com.kallan.naistudio.platform.clearRecentLogs()
                                }
                            },
                            kind = com.kallan.naistudio.ui.RefBtnKind.Ghost,
                            small = true,
                        )
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = t("conversation.close"),
                                tint = ref.muted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                Row(
                    Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // `.rail`：宽 84，竖排按钮间距 6
                    Column(
                        Modifier.width(84.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DrawerTab(
                            label = t("conversation.title"),
                            badge = state.conversation.size,
                            selected = tab == 0,
                            onClick = { tab = 0 },
                        )
                        DrawerTab(
                            label = t("log.title"),
                            badge = state.statusHistory.size + recentLogs.size,
                            selected = tab == 1,
                            onClick = { tab = 1 },
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (tab == 0) {
                            ConversationList(state, t, time)
                        } else {
                            LogList(state, t, time)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerTab(label: String, badge: Int, selected: Boolean, onClick: () -> Unit) {
    // `.rail button`：padding 9/10、圆角 8、13.5/500；选中 = selected 底 + selectedBorder；角标 18 高胶囊
    val ref = com.kallan.naistudio.ui.LocalRef.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) ref.selected else Color.Transparent)
            .border(1.dp, if (selected) ref.selectedBorder else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) ref.onSelected else ref.muted,
            maxLines = 1,
        )
        Box(
            Modifier
                .height(18.dp)
                .defaultMinSize(minWidth = 18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) ref.accent else ref.subtle)
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                badge.toString(),
                fontSize = 11.sp,
                lineHeight = 12.sp,
                color = if (selected) ref.accentContrast else ref.muted,
            )
        }
    }
}

/** 对话模式：一条条 AI 交互（最新在最上面）。 */
@Composable
private fun ConversationList(state: AppState, t: (String) -> String, time: SimpleDateFormat) {
    val turns = state.conversation
    if (turns.isEmpty()) {
        EmptyHint(t("conversation.empty"))
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(turns.reversed(), key = { it.id }) { turn -> ConversationCard(turn, t, time) }
    }
}

@Composable
private fun ConversationCard(turn: ConversationTurn, t: (String) -> String, time: SimpleDateFormat) {
    // 每条对话 = 一张**可收纳**的卡片（用户 2026-09-17 要求）：
    // 收起时只留头一行 + 一句预览，点一下展开完整输入输出。
    // 展开状态按 turn.id 记 —— 列表刷新不会把已经打开的卡片收回去。
    var expanded by remember(turn.id) { mutableStateOf(false) }
    val ref = com.kallan.naistudio.ui.LocalRef.current
    Surface(
        color = ref.field,
        contentColor = ref.text,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ref.border),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            // 头一行：用途 + 模型 + 时间/耗时 + 成败 + 展开箭头
            Row(verticalAlignment = Alignment.CenterVertically) {
                // `.msg .who`：11/600 accent（失败 = negative）
                Text(
                    t(turn.kindKey),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (turn.ok) ref.accent else ref.negative,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${turn.provider} · ${turn.model.ifBlank { "-" }}",
                    fontSize = 11.sp,
                    color = ref.faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    time.format(Date(turn.atMillis)) +
                        if (turn.durationMs > 0) " · ${turn.durationMs} ms" else "",
                    fontSize = 11.sp,
                    color = ref.faint,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = t(if (expanded) "conversation.collapse" else "conversation.expand"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (!expanded) {
                // 收起态：一句话预览（成功看回复、失败看错误），点整张卡片展开
                val preview = (if (turn.ok) turn.response else turn.error)
                    .replace(Regex("\\s+"), " ").trim()
                // 用户 2026-09-18：「自动标记不计入上下文」—— 失败 / 拒答这两种**不进上下文**，
                // 在这里给个标记，免得用户以为它们会影响后面几轮。
                val excluded = !turn.ok || com.kallan.naistudio.models.LlmRefusal.looksLikeRefusal(turn.response)
                Text(
                    (if (excluded) t("conversation.excluded") + " · " else "") + preview.ifBlank { "—" },
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (turn.ok) ref.text else ref.negative,
                )
            } else {
                if (turn.systemPrompt.isNotBlank()) {
                    LabeledBlock(t("conversation.system"), turn.systemPrompt)
                }
                if (turn.userPrompt.isNotBlank()) {
                    LabeledBlock(t("conversation.user"), turn.userPrompt)
                }
                val head = buildString {
                    if (turn.imageCount > 0) append(t("conversation.images").replace("{n}", turn.imageCount.toString()))
                    if (turn.toolsOffered > 0) append(t("conversation.tools").replace("{n}", turn.toolsOffered.toString()))
                    if (turn.extra.isNotBlank()) { if (isNotEmpty()) append(" "); append(turn.extra) }
                }
                if (head.isNotBlank()) {
                    Text(
                        head,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (turn.ok) {
                    LabeledBlock(t("conversation.replied"), turn.response.ifBlank { "—" })
                } else {
                    LabeledBlock(t("conversation.failed"), turn.error, error = true)
                }
            }
        }
    }
}

@Composable
private fun LabeledBlock(label: String, text: String, error: Boolean = false) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        // 展开后就是"全部展示"（用户 2026-09-17：不要再有省略号）
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 日志模式：状态行历史 + 网络/错误日志，按时间倒序（最新在最上面）。 */
@Composable
private fun LogList(state: AppState, t: (String) -> String, time: SimpleDateFormat) {
    // 两类来源合成一条时间线：'s' = 状态行（界面发生了什么），其余 = logInfo/logWarn/logError
    val merged = remember(state.statusHistory.size, recentLogs.size) {
        val statuses = state.statusHistory.map { MergedLine(it.atMillis, 's', "", it.text, it.detail) }
        val logs = recentLogs.map { MergedLine(it.atMillis, it.level, it.tag, it.message, emptyList()) }
        (statuses + logs).sortedBy { it.atMillis }.takeLast(300).reversed()
    }
    if (merged.isEmpty()) {
        EmptyHint(t("log.empty"))
        return
    }
    // 展开态（用户 2026-09-22：「已翻译并填入…」那条要能下拉看明细）。
    // ⚠️ 键用 `时间 + 文字`，不用下标 —— 新日志插到最前面时下标会整体错位，展开的行会"跳"✗
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(merged) { line ->
            val color = when (line.level) {
                'e' -> MaterialTheme.colorScheme.error
                'w' -> MaterialTheme.colorScheme.tertiary
                's' -> com.kallan.naistudio.ui.LocalRef.current.muted
                else -> com.kallan.naistudio.ui.LocalRef.current.text
            }
            val key = "${line.atMillis}:${line.text}"
            val open = expanded[key] == true
            Column(
                Modifier
                    .fillMaxWidth()
                    // 有明细才可点（没明细的行点了也不动，别给人"能展开"的错觉）
                    .then(
                        if (line.detail.isEmpty()) Modifier
                        else Modifier.clickable { expanded[key] = !open },
                    ),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        time.format(Date(line.atMillis)),
                        fontSize = 11.5.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = com.kallan.naistudio.ui.LocalRef.current.faint,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (line.tag.isBlank()) line.text else "${line.tag}: ${line.text}",
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = color,
                        modifier = Modifier.weight(1f),
                    )
                    if (line.detail.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (open) {
                                Icons.Filled.KeyboardArrowUp
                            } else {
                                Icons.Filled.KeyboardArrowDown
                            },
                            contentDescription = t(if (open) "conversation.collapse" else "conversation.expand"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (open) {
                    line.detail.forEach { row ->
                        Text(
                            row,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 26.dp, top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class MergedLine(
    val atMillis: Long,
    val level: Char,
    val tag: String,
    val text: String,
    /** 可展开的明细（只有带明细的状态行非空，见 `StatusLine.detail`）。 */
    val detail: List<String> = emptyList(),
)

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
        Text(
            text,
            fontSize = 13.5.sp,
            color = com.kallan.naistudio.ui.LocalRef.current.faint,
        )
    }
}
