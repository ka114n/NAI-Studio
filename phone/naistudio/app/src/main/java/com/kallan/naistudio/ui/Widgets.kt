package com.kallan.naistudio.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.kallan.naistudio.ui.RefCard as Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import com.kallan.naistudio.ui.RefOutlinedButton as OutlinedButton
import com.kallan.naistudio.ui.RefOutlinedTextField as OutlinedTextField
import com.kallan.naistudio.ui.RefSlider as Slider
import androidx.compose.material3.Surface
import com.kallan.naistudio.ui.RefSwitch as Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.kallan.naistudio.models.NaiOption

/** 带标题的卡片分区（网页 `.card`：panel 底 + 描边 + 16 圆角 + 14 内边距 + 10 间距，标题 15/600）。 */
@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    val ref = LocalRef.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = NaiShape.Card,
        colors = CardDefaults.cardColors(containerColor = ref.panel, contentColor = ref.text),
        border = BorderStroke(1.dp, ref.border),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = ref.text)
            content()
        }
    }
}

/**
 * **胶囊分段控件**：把 2–3 个选项切成并排的小格，选中的那格高亮。
 *
 * 用于「优化模式 / 反推模式」这种三选一（tag / 自然语言 / 混合）——
 * 比下拉菜单少一步点击，而且当前选中项一眼可见。
 */
@Composable
fun SegmentedCapsule(
    options: List<NaiOption>,
    selected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    // Reference seg：field 底 + 描边 + 12 圆角 + 4 内边距；选中格 = accentSoft 底 + accent 字 + 强描边（8 圆角）
    val ref = LocalRef.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ref.field)
            .border(1.dp, ref.border, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) ref.accentSoft else Color.Transparent)
                    .border(1.dp, if (isSelected) ref.borderStrong else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(option.value) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) ref.accent else ref.muted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 网页 `.chev`：faint 色的下箭头，展开时转 180°。 */
@Composable
internal fun RefChevron(expanded: Boolean, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val rot by animateFloatAsState(if (expanded) 180f else 0f, label = "chev")
    Icon(
        imageVector = Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        tint = LocalRef.current.faint,
        modifier = modifier.size(size).rotate(rot),
    )
}

/** 网页 `.ccard > .head`：标题 15/600 + 收起时 12 faint 摘要 + 右侧箭头；内边距 13/14。 */
@Composable
private fun CCardHead(title: String, subtitle: String?, expanded: Boolean, onToggle: () -> Unit) {
    val ref = LocalRef.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = ref.text)
            if (subtitle != null && !expanded) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = ref.faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        RefChevron(expanded)
    }
}

/**
 * 可折叠卡片（网页 `.ccard`）：标题行可点，右侧箭头指示展开状态；收起时只留标题 + 摘要。
 *
 * 用于参数区的"收纳"——参数项很多，收起来才能让预览图留在视野里。
 */
@Composable
fun CollapsibleSectionCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    val ref = LocalRef.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = NaiShape.Card,
        colors = CardDefaults.cardColors(containerColor = ref.panel, contentColor = ref.text),
        border = BorderStroke(1.dp, ref.border),
    ) {
        Column {
            CCardHead(title, subtitle, expanded, onToggle)
            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * 生成页的**抽屉式分区**：收起时只占一行（标题 + 可选的摘要），展开后内容有高度上限并自己滚动。
 *
 * 为什么要限高 + 内部滚动：生成页是「抽屉 → 图片 → 抽屉」的定高布局，
 * 不给上限的话展开会把图片挤没。外层这一列**不是**可滚动的，所以内部这层滚动不会嵌套冲突。
 */
@Composable
fun DrawerSection(
    title: String,
    subtitle: String? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    maxContentHeight: Dp,
    content: @Composable () -> Unit,
) {
    val ref = LocalRef.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = NaiShape.Card,
        colors = CardDefaults.cardColors(containerColor = ref.panel, contentColor = ref.text),
        border = BorderStroke(1.dp, ref.border),
    ) {
        Column {
            CCardHead(title, subtitle, expanded, onToggle)
            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier
                        .heightIn(max = maxContentHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * 卡片内部的**可折叠子分区**（网页 `.ccard.inner`：field 底 + 描边 + 12 圆角，标题 14/500）。
 * 用于把参数里的某一组（比如尺寸）单独收起来。
 */
@Composable
fun CollapsibleSubSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val ref = LocalRef.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ref.field)
            .border(1.dp, ref.border, shape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = ref.text,
                modifier = Modifier.weight(1f),
            )
            RefChevron(expanded)
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}

/**
 * 卡片内部的次级分组标题（网页 `.sub-header`：12/600 faint，字距 .3）。
 * 参数项全部平铺（参考实现的参数区就是不折叠的），用小标题分段，否则一屏十几个控件很难扫读。
 */
@Composable
fun SubHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.3.sp,
        color = LocalRef.current.faint,
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
    )
}

/** 下拉选择（网页 `.select-wrap`：field 底 + 强描边 + 12 圆角 + 44 高 + 右侧 faint 箭头）。 */
@Composable
fun PickerField(
    label: String,
    options: List<NaiOption>,
    selected: String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    val ref = LocalRef.current
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selected }?.label ?: selected
    val shape = RoundedCornerShape(12.dp)

    Column {
        if (label.isNotEmpty()) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = ref.muted,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .alpha(if (enabled) 1f else 0.38f)
                    .clip(shape)
                    .background(ref.field)
                    .border(
                        if (expanded) 2.dp else 1.dp,
                        if (expanded) ref.focus else ref.borderStrong,
                        shape,
                    )
                    .clickable(enabled = enabled) { expanded = true }
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedLabel,
                    fontSize = 14.sp,
                    color = ref.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                RefChevron(false, size = 16.dp)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(12.dp),
                containerColor = ref.panel,
                border = BorderStroke(1.dp, ref.borderStrong),
                tonalElevation = 0.dp,
            ) {
                options.forEach { option ->
                    val on = option.value == selected
                    DropdownMenuItem(
                        text = {
                            Text(
                                option.label,
                                fontSize = 14.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (on) ref.accent else ref.text,
                            )
                        },
                        onClick = {
                            onSelect(option.value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** 带数值显示的滑块（网页 `.slider`：表头 12.5 muted，数值 500 text 色，间距 8）。 */
@Composable
fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit,
) {
    val ref = LocalRef.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 12.5.sp, color = ref.muted)
            Text(valueText, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = ref.text)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.height(32.dp),
        )
    }
}

/**
 * 开关行（网页 `.sw-row`）：左侧标题 14（+ 可选的「?」注释入口），右侧开关。
 *
 * ## 两个"说明"参数的分工（用户 2026-09-16 要求做"功能注释整合"）
 *
 *  · [help] —— **推荐**。标题右边挂一个「?」小圆标，点开才弹注释小窗口。
 *    默认状态下一行只有一个开关，界面干净；解释文字想看的时候才看。
 *  · [hint] —— 老写法，直接把说明铺在标题下面（12 faint）。**新代码别再用它**。
 */
@Composable
fun SwitchRow(
    label: String,
    hint: String? = null,
    help: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val ref = LocalRef.current
    var helpOpen by remember { mutableStateOf(false) }
    var rowHeightPx by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { rowHeightPx = it.height }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontSize = 14.sp, lineHeight = 20.sp, color = ref.text)
                    if (help != null) {
                        Spacer(Modifier.width(6.dp))
                        HelpBadge(onClick = { helpOpen = true })
                    }
                }
                if (hint != null) {
                    Text(
                        hint,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = ref.faint,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
        if (helpOpen && help != null) {
            // 锚在这一行的正下方：`Popup` 的 offset 是相对**父节点**的，
            // 用行高当纵向偏移就不用去算窗口坐标，滚动时也不会错位。
            HelpPopup(
                text = help,
                topOffsetPx = rowHeightPx,
                onDismiss = { helpOpen = false },
            )
        }
    }
}

/**
 * 「?」小圆标（网页 `.help`：18 圆 + 1.4 faint 描边 + 11/700 问号）：功能开关旁边的注释入口。
 *
 * 纯几何 + 字形直接画，不另造图标。
 */
@Composable
fun HelpBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = LocalRef.current.faint
    Box(
        modifier
            .size(18.dp)
            .clip(CircleShape)
            .border(1.4.dp, color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "?",
            color = color,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            // 去掉 includeFontPadding：默认字体的上下留白会把字形在圆圈里压低
            style = LocalTextStyle.current.copy(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * 「?」小圆标 **带自己的注释小窗口**（成对使用的简写）。
 *
 * [HelpBadge] 本身只是个按钮，弹窗得调用方自己挂（[SwitchRow] 就是这么写的：自己记一个
 * `helpOpen`、自己量行高）。这里是给**不是 [SwitchRow] 的自定义行**用的：直接用这一句就完事。
 *
 * @param alignEnd 弹窗往**左**弹（右对齐到徽标）。徽标靠近面板右缘时（比如漫画模式那个
 *   126dp 的窄列）必须开，否则 250dp 的窗口会顶出屏幕。
 */
@Composable
fun HelpBadgeWithPopup(
    text: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    var anchorHeightPx by remember { mutableIntStateOf(0) }
    Box(modifier.onSizeChanged { anchorHeightPx = it.height }) {
        HelpBadge(onClick = { open = true })
        if (open) {
            HelpPopup(
                text = text,
                topOffsetPx = anchorHeightPx,
                alignEnd = alignEnd,
                onDismiss = { open = false },
            )
        }
    }
}

/**
 * 注释小窗口（网页 `.pop`：panel 底 + 强描边 + 8 圆角 + 12.5 muted，宽 250）。
 *
 * 贴在被点的「?」那一行的下方弹出（`Popup` 的 offset 相对父节点，所以只要给行高即可）。
 */
@Composable
private fun HelpPopup(
    text: String,
    topOffsetPx: Int,
    onDismiss: () -> Unit,
    alignEnd: Boolean = false,
) {
    val ref = LocalRef.current
    Popup(
        alignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart,
        offset = IntOffset(0, topOffsetPx + 6),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = ref.panel,
            contentColor = ref.muted,
            border = BorderStroke(1.dp, ref.borderStrong),
            shadowElevation = 8.dp,
            modifier = Modifier.width(250.dp),
        ) {
            Text(
                text,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = ref.muted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * 数字输入框。`commitOnly` 语义与参考实现一致：只在满足约束时才写回状态，
 * 这样用户中间态（比如清空重输）不会立刻把参数改成非法值。
 */
@Composable
fun NumberField(
    label: String,
    value: Int,
    hint: String? = null,
    keyboardType: KeyboardType = KeyboardType.Number,
    maxLength: Int = 10,
    enabled: Boolean = true,
    onCommit: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() }.take(maxLength)
            text = filtered
            filtered.toIntOrNull()?.let(onCommit)
        },
        label = { Text(label) },
        supportingText = hint?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * 白字黑边描边文字：叠在图片上时不管底图明暗都能看清。
 *
 * 用两个独立的 [Text] 分别画描边和填充，两层各自的文字布局/抗锯齿会有细微差异，
 * 叠起来就是"错位"。这里改成用同一个 [android.graphics.Paint] 的字号/字体在一个
 * [Canvas] 上先描边、后填充同一个基线位置，保证像素级对齐。
 */
@Composable
fun StrokedText(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 12.sp) {
    val density = LocalDensity.current
    val textSizePx = with(density) { fontSize.toPx() }
    val fillPaint = remember(textSizePx) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.FILL
            color = android.graphics.Color.WHITE
            textSize = textSizePx
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    val strokePaint = remember(textSizePx) {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeWidth = textSizePx * 0.18f
            color = android.graphics.Color.BLACK
            textSize = textSizePx
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    val metrics = fillPaint.fontMetrics
    val textWidthDp = with(density) { fillPaint.measureText(text).toDp() }
    val textHeightDp = with(density) { (metrics.descent - metrics.ascent).toDp() }
    Canvas(modifier.size(textWidthDp, textHeightDp)) {
        val baselineY = -metrics.ascent
        drawContext.canvas.nativeCanvas.drawText(text, 0f, baselineY, strokePaint)
        drawContext.canvas.nativeCanvas.drawText(text, 0f, baselineY, fillPaint)
    }
}
