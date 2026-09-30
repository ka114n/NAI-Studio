package com.kallan.naistudio.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.UsageCell
import com.kallan.naistudio.models.usageMonthCells
import com.kallan.naistudio.models.usageMonthTotals
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.NaiSkinTokens
import java.time.LocalDate
import java.time.YearMonth

/**
 * **统计**（用户 2026-09-23 起、到 2026-09-24 定稿 ✓）。
 *
 * ## 口径沿革（每一条都是用户当场说的 ✓）
 *
 *  1. 「像 github 的月度贡献表样式…记录每天跑图的数量、消耗的积分、tag 的字数」✓；
 *  2. 「统计是最近 53 周，**我只要最近 1 个月**」⇒ 收到一个月 ✓；
 *  3. 「窗子 340×440、每日格子减小、去除标语」✓；
 *  4. 「把**有出图天数换成本月**，框内显示 **xx 天**，**可以切换月份**，
 *     **不要以周一，周二排列**，直接把**本月天数的格子**列出来」✓ ← **本次** ✓。
 *
 * ## 现在长什么样
 *
 *  · 顶部一行：`‹ 2026 年 9 月 ›` ✓（**能翻月份** ✓，翻到未来会被挡住 ✓）；右边一个「回本月」✓；
 *  · 四块汇总：出图 / 消耗点数 / tag 字数 / **本月**（那一块的值就是 `xx 天` ✓ —— 本月有几天出过图 ✓）；
 *  · 下面那张格子：**当月 1 号到月末，一天一格** ✓（**不按星期对齐** ✗ —— 用户点名不要 ✓），
 *    按 7 列自动折行 ✓（30 天就是 5 行略少 ✓）；
 *  · 点某一天 ⇒ 底下出那天的明细 ✓。
 *
 * ⚠️ **今天之后的那几格**照样画成**淡格** ✓ 且**不可点** ✗ —— 让它们能点，
 * 就会得到一片"这天什么都没干"的假信息 ✓。
 *
 * ⚠️ **内容本身不带任何背景/边框** ✗（2026-09-24 拆出来 ✓）：窗子的底色、圆角、毛玻璃
 * 全由外面那层负责 ✓（`StudioShell.StatsWindow` ✓，款式 = ⑨c 苹果风·压扁 ✓）。
 */
@Composable
fun UsageStatsScreen(
    state: AppState,
    t: (String) -> String,
    modifier: Modifier = Modifier,
    /** 卡片自己的壳 ✓：`Card` = 三块各自浅底 ✓；`Flat` = 全摊在窗子那层玻璃上 ✓。 */
    surfaceStyle: StatsSurfaceStyle = StatsSurfaceStyle.Card,
) {
    val thisMonth = remember { YearMonth.now() }
    // 看的是哪个月 ✓（默认本月 ✓；`‹ ›` 翻 ✓；翻到未来用「›」挡回来 ✓）
    var shown by remember { mutableStateOf(thisMonth) }
    var picked by remember { mutableStateOf<String?>(null) }

    val todayKey = remember { LocalDate.now().toString() }
    val cells = remember(state.usage, shown) { usageMonthCells(state.usage, shown) }
    val totals = remember(state.usage, shown) { usageMonthTotals(state.usage, shown) }

    CompositionLocalProvider(
        LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
            fontFamily = NaiSkinTokens.ReferenceFontFamily,
        ),
    ) {
    Column(
        modifier
            .then(if (surfaceStyle == StatsSurfaceStyle.Reference) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
            .verticalScroll(rememberScrollState())
            .padding(if (surfaceStyle == StatsSurfaceStyle.Reference) 16.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(
            if (surfaceStyle == StatsSurfaceStyle.Reference) 12.dp else 6.dp,
        ),
    ) {
        // ---- 月份切换 ‹ 2026 年 9 月 ›（本月时右边给一颗「回本月」✓）----
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthNavButton(t("stats.prevMonth"), enabled = true) {
                shown = shown.minusMonths(1)
                picked = null
            }
            Spacer(Modifier.width(4.dp))
            Text(
                RuntimeText.format(
                    state.settings.language,
                    "stats.monthTitle",
                    mapOf("y" to shown.year, "m" to shown.monthValue),
                ),
                style = MaterialTheme.typography.titleSmall,
                color = NaiSkinTokens.text(),
                modifier = Modifier.weight(1f),
            )
            if (shown < thisMonth) {
                Text(
                    t("stats.backToThisMonth"),
                    style = MaterialTheme.typography.labelSmall,
                    color = NaiSkinTokens.accent(),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            shown = thisMonth
                            picked = null
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(2.dp))
            }
            MonthNavButton(t("stats.nextMonth"), enabled = shown < thisMonth) {
                shown = shown.plusMonths(1)
                picked = null
            }
        }

        // ---- 四块汇总（最后一块 = **本月 xx 天** ✓）----
        val tileValues = listOf(
            t("stats.images") to totals.images.toString(),
            t("stats.anlas") to totals.anlas.toString(),
            t("stats.tags") to totals.tagChars.toString(),
            t("stats.thisMonth") to RuntimeText.format(
                state.settings.language,
                "stats.daysUnit",
                mapOf("n" to totals.activeDays),
            ),
        )
        if (surfaceStyle == StatsSurfaceStyle.Reference) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tileValues.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (label, value) ->
                            StatTile(label, value, surfaceStyle, Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                tileValues.forEach { (label, value) ->
                    StatTile(label, value, surfaceStyle, Modifier.weight(1f))
                }
            }
        }

        // ---- 当月每一天（**1 号到月末，不按星期对齐** ✓）----
        StatsCard(surfaceStyle) {
            Column(
                Modifier.padding(
                    horizontal = if (surfaceStyle == StatsSurfaceStyle.Reference) 12.dp else 8.dp,
                    vertical = if (surfaceStyle == StatsSurfaceStyle.Reference) 10.dp else 7.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        t("stats.range"),
                        style = MaterialTheme.typography.labelSmall,
                        color = NaiSkinTokens.muted(),
                    )
                    Spacer(Modifier.weight(1f))
                    // 图例：少 → 多
                    Text(
                        t("stats.less"),
                        style = MaterialTheme.typography.labelSmall,
                        color = NaiSkinTokens.muted(),
                    )
                    Spacer(Modifier.width(3.dp))
                    (0..4).forEach { level ->
                        Box(
                            Modifier
                                .padding(horizontal = 1.dp)
                                .size(8.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(levelColor(level)),
                        )
                    }
                    Spacer(Modifier.width(3.dp))
                    Text(
                        t("stats.more"),
                        style = MaterialTheme.typography.labelSmall,
                        color = NaiSkinTokens.muted(),
                    )
                }

                DayGrid(
                    cells = cells,
                    todayKey = todayKey,
                    picked = picked,
                    onPick = { picked = it },
                    referenceStyle = surfaceStyle == StatsSurfaceStyle.Reference,
                )
            }
        }

        // ---- 选中的那一天 ----
        // ⚠️ 先把 `picked` 落进局部变量 ✗ 不然下面的 `picked` 是委托属性，智能转换用不了 ✓
        val pickedDay = picked
        val day = pickedDay?.let { state.usage.of(it) }
        if (pickedDay != null) {
            StatsCard(surfaceStyle) {
                Column(
                    Modifier.padding(
                        horizontal = if (surfaceStyle == StatsSurfaceStyle.Reference) 12.dp else 8.dp,
                        vertical = if (surfaceStyle == StatsSurfaceStyle.Reference) 10.dp else 7.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(pickedDay, style = MaterialTheme.typography.labelLarge, color = NaiSkinTokens.text())
                    if (day == null || day.images == 0 && day.anlas == 0 && day.tagChars == 0) {
                        Text(
                            t("stats.dayEmpty"),
                            style = MaterialTheme.typography.bodySmall,
                            color = NaiSkinTokens.muted(),
                        )
                    } else {
                        Text(
                            RuntimeText.format(
                                state.settings.language,
                                "stats.dayLine",
                                mapOf(
                                    "images" to (day?.images ?: 0),
                                    "anlas" to (day?.anlas ?: 0),
                                    "tags" to (day?.tagChars ?: 0),
                                ),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = NaiSkinTokens.text(),
                        )
                    }
                }
            }
        }
        // ⛔ 「一格 = 一天，按星期对齐……」那串标语**去掉了** ✗（用户 2026-09-24 ✓）。
    }
    }
}

/** 卡片那三块**要不要各自带浅底** ✓（⑨c 用 [Card] ✓，摊平那一档留给以后的对比 ✓）。 */
enum class StatsSurfaceStyle {
    /** 三块各自一块浅底 ✓。 */
    Card,

    /** 全部摊在窗子那层玻璃上 ✓（不再套一层不透明底 ✓）。 */
    Flat,

    /** 网页参考稿：青色热度格与 2×2 统计卡。 */
    Reference,
}

/** 三块卡片的外壳：按 [surfaceStyle] 决定"套浅底"还是"什么都不套" ✓。 */
@Composable
private fun StatsCard(style: StatsSurfaceStyle, content: @Composable () -> Unit) {
    when (style) {
        StatsSurfaceStyle.Card -> Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { content() }

        StatsSurfaceStyle.Flat -> Box(Modifier.fillMaxWidth()) { content() }

        StatsSurfaceStyle.Reference -> Surface(
            color = NaiSkinTokens.glass(),
            shape = NaiSkinTokens.largeShape(),
            border = BorderStroke(1.dp, NaiSkinTokens.border()),
            modifier = Modifier.fillMaxWidth(),
        ) { content() }
    }
}

/** 月份切换那颗（`‹` / `›` ✓；到头了就是**禁用态** ✓ —— 不做"点了没反应"的假控件 ✗）。 */
@Composable
private fun MonthNavButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = NaiSkinTokens.smallShape(),
        color = if (enabled) NaiSkinTokens.field() else Color.Transparent,
        contentColor = if (enabled) NaiSkinTokens.text() else NaiSkinTokens.faint(),
        border = BorderStroke(1.dp, if (enabled) NaiSkinTokens.controlBorder() else NaiSkinTokens.border()),
        modifier = Modifier.size(30.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = if (enabled) NaiSkinTokens.text() else NaiSkinTokens.faint())
        }
    }
}

/** 一天一格的大小（**算出来的** ✓ —— 见 [DayGrid] 的注释 ✓）。 */
private val CELL_FLOOR = 17.dp
private val CELL_CEIL = 25.dp

/** 格子之间的缝（上下左右同一套 ✓）。 */
private val CELL_GAP = 3.dp

/**
 * **当月每一天的格子**（1 号 → 月末 ✓，**按 7 列折行** ✓）。
 *
 * ⚠️ 与上一版的差别（用户 2026-09-24 ✓）：
 *  · **不按星期对齐** ✗ —— 不再有"周一在最左"这种列含义 ✓，单纯从 1 号排到月末 ✓；
 *  · 也没有星期表头 ✓（那行是给"按星期对齐"用的 ✓，现在没有意义 ✓）。
 *
 * ⚠️ 格子边长**按可用宽度算** ✓ 不是写死：窗子小（300 宽 ✓）又要"保证信息齐全" ✓ ⇒
 * 一行 7 格正好铺满那点宽度 ✓（写死就会在某个宽度下挤出去或者浪费一截 ✓）。
 */
@Composable
private fun DayGrid(
    cells: List<UsageCell>,
    todayKey: String,
    picked: String?,
    onPick: (String) -> Unit,
    referenceStyle: Boolean,
) {
    if (cells.isEmpty()) return
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val avail = with(density) { maxWidth.toPx() } -
                with(density) { (CELL_GAP * 6).toPx() }
            val side = (avail / 7f).coerceAtLeast(with(density) { CELL_FLOOR.toPx() })
            val cell = with(density) {
                side.coerceAtMost(CELL_CEIL.toPx()).toDp()
            }
            Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                cells.chunked(7).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                        row.forEach { cellData ->
                            DayCell(
                                cell = cellData,
                                todayKey = todayKey,
                                picked = picked,
                                onPick = onPick,
                                size = cell,
                                referenceStyle = referenceStyle,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一格 = 一天 ✓（点一下看这一天的明细 ✓；格子里写着**几号** ✓ —— 不按星期排之后必须能认出是哪天 ✓）。 */
@Composable
private fun DayCell(
    cell: UsageCell,
    todayKey: String,
    picked: String?,
    onPick: (String) -> Unit,
    size: androidx.compose.ui.unit.Dp,
    referenceStyle: Boolean,
) {
    val isFuture = cell.date > todayKey
    val selected = cell.date == picked
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(5.dp))
            .background(
                when {
                    selected -> NaiSkinTokens.selected()
                    isFuture && referenceStyle -> NaiSkinTokens.subtle()
                    isFuture -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    else -> levelColor(cell.level)
                },
            )
            .then(
                if (isFuture) {
                    Modifier
                } else {
                    Modifier
                        .clickable { onPick(cell.date) }
                        .focusProperties { canFocus = false }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 号数：格子上直接写"几号" ✓，状态文字与底色使用配对 token。
        Text(
            cell.day.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = when {
                selected -> NaiSkinTokens.onSelected()
                isFuture -> NaiSkinTokens.faint()
                else -> NaiSkinTokens.activityText(cell.level)
            },
        )
    }
}

/**
 * **格子颜色**（0..4 五档，越深/越亮 = 跑得越多）；色阶按 UI 主题保持数字可读。
 * 毛玻璃旧风格仍沿用既有绿阶；网页风格使用对比更高的青色阶。
 */
@Composable
private fun levelColor(level: Int): Color = NaiSkinTokens.activityLevel(level)

@Composable
private fun StatTile(
    label: String,
    value: String,
    style: StatsSurfaceStyle,
    modifier: Modifier = Modifier,
) {
    val inner: @Composable () -> Unit = {
        Column(
            Modifier.padding(
                horizontal = if (style == StatsSurfaceStyle.Reference) 12.dp else 5.dp,
                vertical = if (style == StatsSurfaceStyle.Reference) 9.dp else 5.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (style == StatsSurfaceStyle.Reference) 2.dp else 0.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = NaiSkinTokens.muted(),
                // ⚠️ **最多两行** ✗ 不是一行：300 宽的小窗里四块并排，一格宽只有 ~66dp ✓，
                //    "本月"这种短标签也要留出换行的余地 ✓（信息一点不能少 ✓）。
                maxLines = 2,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                color = if (style == StatsSurfaceStyle.Reference) NaiSkinTokens.accent() else NaiSkinTokens.text(),
            )
        }
    }
    when (style) {
        StatsSurfaceStyle.Card -> Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(10.dp),
            modifier = modifier,
        ) { inner() }

        StatsSurfaceStyle.Flat -> Box(modifier) { inner() }

        StatsSurfaceStyle.Reference -> Surface(
            color = NaiSkinTokens.field(),
            shape = NaiSkinTokens.mediumShape(),
            border = BorderStroke(1.dp, NaiSkinTokens.border()),
            modifier = modifier,
        ) { inner() }
    }
}
