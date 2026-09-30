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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            // 稍微做厚一点 + 默认最小高度：那行字本来只有十几像素高，不好点
            .defaultMinSize(minHeight = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (idle) t("drawer.openHint") else text,
            style = MaterialTheme.typography.bodySmall,
            color = if (idle) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.KeyboardArrowUp,
            contentDescription = t("drawer.openHint"),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
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
        // 点空白处收起（半透明遮罩）
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(onClick = onClose),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.55f),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                // 顶部把手 + 标题 + 收起
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (tab == 0) t("conversation.title") else t("log.title"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (state.settings.llmMemoryEnabled) {
                        Text(
                            t("conversation.memory").replace("{n}", state.llmMemoryTotalRounds().toString()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        if (tab == 0) {
                            state.clearConversation()
                            // 记忆也跟着清：用户点「清空」的语义是"忘掉之前聊过什么"
                            state.clearLlmMemory()
                        } else {
                            state.clearStatusHistory()
                            com.kallan.naistudio.platform.clearRecentLogs()
                        }
                    }) { Text(t("conversation.clear"), style = MaterialTheme.typography.labelMedium) }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = t("conversation.close"))
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxSize()) {
                    // 左侧竖排 Tab
                    Column(
                        Modifier.width(84.dp).fillMaxHeight().padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DrawerTab(
                            label = t("conversation.title"),
                            badge = state.conversation.size,
                            selected = tab == 0,
                            onClick = { tab = 0 },
                        )
                        Spacer(Modifier.height(6.dp))
                        DrawerTab(
                            label = t("log.title"),
                            badge = state.statusHistory.size + recentLogs.size,
                            selected = tab == 1,
                            onClick = { tab = 1 },
                        )
                    }
                    HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))
                    Box(Modifier.fillMaxSize()) {
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
    val bg = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        if (badge > 0) {
            Text(
                badge.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.8f),
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
        contentPadding = PaddingValues(10.dp),
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
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // 头一行：用途 + 模型 + 时间/耗时 + 成败 + 展开箭头
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (turn.ok) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        t(turn.kindKey),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (turn.ok) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${turn.provider} · ${turn.model.ifBlank { "-" }}",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    time.format(Date(turn.atMillis)) +
                        if (turn.durationMs > 0) " · ${turn.durationMs} ms" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Text(
                    preview.ifBlank { "—" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (turn.ok) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
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
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items(merged) { line ->
            val color = when (line.level) {
                'e' -> MaterialTheme.colorScheme.error
                'w' -> MaterialTheme.colorScheme.tertiary
                's' -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurface
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
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (line.tag.isBlank()) line.text else "${line.tag}: ${line.text}",
                        style = MaterialTheme.typography.bodySmall,
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
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopStart) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
