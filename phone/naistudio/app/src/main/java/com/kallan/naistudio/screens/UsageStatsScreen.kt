package com.kallan.naistudio.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.kallan.naistudio.ui.LocalRef
import com.kallan.naistudio.ui.RefBtn
import com.kallan.naistudio.ui.RefBtnKind

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
    val todayKey = remember { LocalDate.now().toString() }
    // 网页：打开就选中今天 ✓
    var picked by remember { mutableStateOf<String?>(todayKey) }
    val ref = LocalRef.current

    val cells = remember(state.usage, shown) { usageMonthCells(state.usage, shown) }
    val totals = remember(state.usage, shown) { usageMonthTotals(state.usage, shown) }

    // 网页 `.sh-body`：padding 4/16/28、间距 14
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---- `.st-month`：‹ 2026 年 9 月 ›
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MonthNavButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("stats.prevMonth"), enabled = true) {
                shown = shown.minusMonths(1)
                picked = null
            }
            Text(
                RuntimeText.format(
                    state.settings.language,
                    "stats.monthTitle",
                    mapOf("y" to shown.year, "m" to shown.monthValue),
                ),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ref.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            if (shown < thisMonth) {
                RefBtn(
                    text = t("stats.backToThisMonth"),
                    onClick = {
                        shown = thisMonth
                        picked = todayKey
                    },
                    kind = RefBtnKind.Ghost,
                    small = true,
                )
            }
            MonthNavButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("stats.nextMonth"), enabled = shown < thisMonth) {
                shown = shown.plusMonths(1)
                picked = if (shown == thisMonth) todayKey else null
            }
        }

        // ---- `.st-tiles`：4 列、间距 6
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatTile(t("stats.images"), totals.images.toString(), surfaceStyle, Modifier.weight(1f))
            StatTile(t("stats.anlas"), totals.anlas.toString(), surfaceStyle, Modifier.weight(1f))
            StatTile(t("stats.tags"), totals.tagChars.toString(), surfaceStyle, Modifier.weight(1f))
            StatTile(
                t("stats.thisMonth"),
                RuntimeText.format(
                    state.settings.language,
                    "stats.daysUnit",
                    mapOf("n" to totals.activeDays),
                ),
                surfaceStyle,
                Modifier.weight(1f),
            )
        }

        // ---- `.row`：label + `.legend`（少 ■■■■■ 多）
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(t("stats.range"), fontSize = 12.5.sp, color = ref.muted, modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t("stats.less"), fontSize = 11.sp, color = ref.faint)
                    (0..4).forEach { level ->
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(ref.lv[level]),
                        )
                    }
                    Text(t("stats.more"), fontSize = 11.sp, color = ref.faint)
                }
            }

            DayGrid(
                cells = cells,
                todayKey = todayKey,
                picked = picked,
                onPick = { picked = it },
            )
        }

        // ---- 选中的那一天：`.card`（panel 底、border、圆角 16、padding 14、间距 10）
        // ⚠️ 先把 `picked` 落进局部变量 ✗ 不然下面的 `picked` 是委托属性，智能转换用不了 ✓
        val pickedDay = picked
        val day = pickedDay?.let { state.usage.of(it) }
        if (pickedDay != null) {
            StatsCard(surfaceStyle) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(pickedDay, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ref.text)
                    if (day == null || day.images == 0 && day.anlas == 0 && day.tagChars == 0) {
                        Text(t("stats.dayEmpty"), fontSize = 13.5.sp, color = ref.muted)
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
                            fontSize = 13.5.sp,
                            color = ref.muted,
                        )
                    }
                }
            }
        }
        // ⛔ 「一格 = 一天，按星期对齐……」那串标语**去掉了** ✗（用户 2026-09-24 ✓）。
    }
}

/** 卡片那三块**要不要各自带浅底** ✓（⑨c 用 [Card] ✓，摊平那一档留给以后的对比 ✓）。 */
enum class StatsSurfaceStyle {
    /** 三块各自一块浅底 ✓。 */
    Card,

    /** 全部摊在窗子那层玻璃上 ✓（不再套一层不透明底 ✓）。 */
    Flat,
}

/** 网页 `.card`：panel 底 + border + 圆角 16。 */
@Composable
private fun StatsCard(style: StatsSurfaceStyle, content: @Composable () -> Unit) {
    val ref = LocalRef.current
    when (style) {
        StatsSurfaceStyle.Card -> Surface(
            color = ref.panel,
            contentColor = ref.text,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, ref.border),
            modifier = Modifier.fillMaxWidth(),
        ) { content() }

        StatsSurfaceStyle.Flat -> Box(Modifier.fillMaxWidth()) { content() }
    }
}

/** `.st-nav`：32 方、圆角 8、border、muted 图标；到头 = 35% 透明的禁用态 ✓。 */
@Composable
private fun MonthNavButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val ref = LocalRef.current
    Box(
        Modifier
            .size(32.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, ref.border, RoundedCornerShape(8.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = ref.muted, modifier = Modifier.size(18.dp))
    }
}

/**
 * **当月每一天的格子**（1 号 → 月末 ✓，**按 7 列折行** ✓，不按星期对齐 ✓）。
 * 网页 `.days`：7 列等分铺满、间距 4、正方形格子。
 */
@Composable
private fun DayGrid(
    cells: List<UsageCell>,
    todayKey: String,
    picked: String?,
    onPick: (String) -> Unit,
) {
    if (cells.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        cells.chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { cellData ->
                    DayCell(
                        cell = cellData,
                        todayKey = todayKey,
                        picked = picked,
                        onPick = onPick,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** `.day`：圆角 6、11 号字；hi = 深档反色 600；future = subtle 45%；today = 1.5 faint 内圈；picked = 2 accent 内圈 700。 */
@Composable
private fun DayCell(
    cell: UsageCell,
    todayKey: String,
    picked: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ref = LocalRef.current
    val isFuture = cell.date > todayKey
    val selected = cell.date == picked
    val isToday = cell.date == todayKey
    val hi = !isFuture && cell.level >= 3
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .alpha(if (isFuture) 0.45f else 1f)
            .clip(shape)
            .background(if (isFuture) ref.subtle else ref.lv[cell.level.coerceIn(0, 4)])
            .then(
                when {
                    selected -> Modifier.border(2.dp, ref.accent, shape)
                    isToday -> Modifier.border(1.5.dp, ref.faint, shape)
                    else -> Modifier
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
        Text(
            cell.day.toString(),
            fontSize = 11.sp,
            fontWeight = when {
                selected -> FontWeight.Bold
                hi -> FontWeight.SemiBold
                else -> FontWeight.Normal
            },
            color = when {
                isFuture -> ref.faint
                hi -> if (ref.dark) ref.accentContrast else Color.White
                else -> ref.text
            },
        )
    }
}

/** `.st-tile`：field 底、border、圆角 12、padding 8/6、居中；b 16/700，small 11 muted。 */
@Composable
private fun StatTile(
    label: String,
    value: String,
    style: StatsSurfaceStyle,
    modifier: Modifier = Modifier,
) {
    val ref = LocalRef.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .then(
                if (style == StatsSurfaceStyle.Card) {
                    Modifier.clip(shape).background(ref.field).border(1.dp, ref.border, shape)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ref.text, maxLines = 1)
        Text(label, fontSize = 11.sp, color = ref.muted, maxLines = 2, textAlign = TextAlign.Center)
    }
}
