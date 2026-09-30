package com.kallan.naistudio.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.kallan.naistudio.models.NaiOption
import com.kallan.naistudio.platform.LocalPlatform

/** 桌面页面共用的滚动容器：设置、我的与主页共用内容宽度及左右边距。 */
@Composable
fun StudioPageContainer(
    title: String,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val width = LocalWindowSize.current.width
    val pagePadding = when {
        width < 600.dp -> 16.dp
        width < 1120.dp -> 24.dp
        else -> 32.dp
    }
    CompositionLocalProvider(
        LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
            fontFamily = NaiSkinTokens.ReferenceFontFamily,
        ),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 1120.dp).padding(horizontal = pagePadding, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                content = {
                    StudioPageHeader(title = title, subtitle = subtitle, actions = actions)
                    content()
                },
            )
        }
    }
}

/** 主页、设置、我的统一的标题基线与摘要栏。 */
@Composable
fun StudioPageHeader(
    title: String,
    subtitle: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 27.sp, lineHeight = 34.sp),
                fontWeight = FontWeight.SemiBold,
                color = NaiSkinTokens.text(),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = NaiSkinTokens.muted(),
                )
            }
        }
        if (actions != null) Row(content = actions)
    }
}

enum class StudioButtonKind { Primary, Secondary, Ghost }

/** 三种桌面按钮共用同一 40dp 盒、描边层级与鼠标状态，不缩放布局盒。 */
@Composable
fun StudioButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    kind: StudioButtonKind = StudioButtonKind.Secondary,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        when {
            !enabled -> NaiSkinTokens.surface()
            kind == StudioButtonKind.Primary && pressed -> NaiSkinTokens.accentPressed()
            kind == StudioButtonKind.Primary && hovered -> NaiSkinTokens.accentStrong()
            kind == StudioButtonKind.Primary -> NaiSkinTokens.accent()
            pressed -> NaiSkinTokens.strip()
            hovered -> NaiSkinTokens.subtleHover()
            kind == StudioButtonKind.Secondary -> NaiSkinTokens.surface()
            else -> Color.Transparent
        },
        tween(140, easing = FastOutSlowInEasing),
        label = "studioButtonBackground",
    )
    val foreground = when {
        !enabled -> NaiSkinTokens.faint()
        kind == StudioButtonKind.Primary -> NaiSkinTokens.accentContrast()
        kind == StudioButtonKind.Ghost -> NaiSkinTokens.text()
        else -> NaiSkinTokens.text()
    }
    val focusColor = NaiSkinTokens.focus()
    val outline = if (kind == StudioButtonKind.Secondary) NaiSkinTokens.controlBorder() else Color.Transparent
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = shape,
        color = background,
        contentColor = foreground,
        border = BorderStroke(1.dp, outline),
        modifier = modifier
            .heightIn(min = 40.dp)
            .hoverable(interaction)
            .onFocusChanged { focused = it.isFocused }
            .drawWithContent {
                drawContent()
                if (focused) {
                    val inset = 3.dp.toPx()
                    val stroke = 2.dp.toPx()
                    drawRoundRect(
                        color = focusColor,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(stroke),
                    )
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/** 主侧栏导航单盒：背景、裁切、描边与 hit target 共用同一形状和交互状态。 */
@Composable
fun StudioNavigationItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    var focused by remember { mutableStateOf(false) }
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    val background by animateColorAsState(
        when {
            selected && pressed -> NaiSkinTokens.selectedPressed()
            selected && hovered -> NaiSkinTokens.selectedHover()
            selected -> NaiSkinTokens.selected()
            pressed -> NaiSkinTokens.strip()
            hovered -> NaiSkinTokens.subtleHover()
            else -> Color.Transparent
        },
        tween(140, easing = FastOutSlowInEasing),
        label = "studioNavigationBackground",
    )
    val foreground = if (selected) NaiSkinTokens.onSelected() else NaiSkinTokens.muted()
    val focusColor = NaiSkinTokens.focus()
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = shape,
        color = background,
        contentColor = foreground,
        border = BorderStroke(
            1.dp,
            if (selected && referenceSkin) NaiSkinTokens.selectedBorder()
            else if (selected) NaiSkinTokens.border() else Color.Transparent,
        ),
        modifier = modifier
            .height(44.dp)
            .hoverable(interaction)
            .onFocusChanged { focused = it.isFocused }
            .semantics { this.selected = selected }
            .drawWithContent {
                drawContent()
                if (focused) {
                    val inset = 3.dp.toPx()
                    val stroke = 2.dp.toPx()
                    drawRoundRect(
                        color = focusColor,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                        style = Stroke(stroke),
                    )
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = NaiSkinTokens.faint(), maxLines = 1)
                }
            }
            if (selected) Box(Modifier.size(6.dp).clip(CircleShape).background(foreground))
            if (trailing != null) Row(content = trailing)
        }
    }
}

/** 带标题的卡片分区。生成页除了参数卡不折叠，其余保持一致的分区样式。 */
@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = NaiSkinTokens.glass(),
        border = BorderStroke(1.dp, NaiSkinTokens.border()),
        shadowElevation = if (LocalNaiSkin.current == NaiSkin.Reference) 2.dp else 0.dp,
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), fontWeight = FontWeight.SemiBold, color = NaiSkinTokens.text())
                    if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = NaiSkinTokens.muted())
                }
                if (trailing != null) Row(content = trailing)
            }
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
    val referenceSkin = LocalNaiSkin.current == NaiSkin.Reference
    val groupShape = if (referenceSkin) RoundedCornerShape(12.dp) else RoundedCornerShape(50)
    Row(
        modifier
            .fillMaxWidth()
            .clip(groupShape)
            .background(
                if (referenceSkin) NaiSkinTokens.field() else MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            .then(if (referenceSkin) Modifier.border(1.dp, NaiSkinTokens.border(), groupShape) else Modifier)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected
            val segmentShape = if (referenceSkin) RoundedCornerShape(9.dp) else RoundedCornerShape(50)
            val interaction = remember(option.value) { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val pressed by interaction.collectIsPressedAsState()
            val referenceFill by animateColorAsState(
                targetValue = when {
                    !referenceSkin -> if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    isSelected && pressed -> NaiSkinTokens.accentSoft(0.95f)
                    isSelected && hovered -> NaiSkinTokens.accentSoft()
                    isSelected -> NaiSkinTokens.accentSoft()
                    pressed -> NaiSkinTokens.accentSoft(0.72f)
                    hovered -> NaiSkinTokens.subtleHover()
                    else -> Color.Transparent
                },
                animationSpec = if (referenceSkin) tween(120, easing = FastOutSlowInEasing) else androidx.compose.animation.core.snap(),
                label = "segmentedCapsuleFill",
            )
            val referenceText by animateColorAsState(
                targetValue = when {
                    isSelected && referenceSkin -> NaiSkinTokens.accent()
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    referenceSkin && hovered -> NaiSkinTokens.text()
                    referenceSkin -> NaiSkinTokens.muted()
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = if (referenceSkin) tween(120, easing = FastOutSlowInEasing) else androidx.compose.animation.core.snap(),
                label = "segmentedCapsuleText",
            )
            val pressScale by animateFloatAsState(
                targetValue = if (referenceSkin && pressed) 0.985f else 1f,
                animationSpec = if (referenceSkin) tween(120, easing = FastOutSlowInEasing) else androidx.compose.animation.core.snap(),
                label = "segmentedCapsulePressScale",
            )
            Box(
                Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .clip(segmentShape)
                    .background(referenceFill)
                    .then(
                        if (referenceSkin && isSelected) {
                            Modifier.border(1.dp, NaiSkinTokens.border(), segmentShape)
                        } else {
                            Modifier
                        },
                    )
                    .hoverable(interaction)
                    .clickable(
                        interactionSource = interaction,
                        indication = if (referenceSkin) null else LocalIndication.current,
                    ) { onSelect(option.value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = referenceText,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 可折叠卡片：标题行可点，右侧箭头指示展开状态；收起时只留标题。
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = NaiSkinTokens.glass(),
        border = BorderStroke(1.dp, NaiSkinTokens.border()),
        shadowElevation = if (LocalNaiSkin.current == NaiSkin.Reference) 2.dp else 0.dp,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = NaiSkinTokens.text())
                    if (subtitle != null && !expanded) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = NaiSkinTokens.muted(),
                            maxLines = 1,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = NaiSkinTokens.muted(),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (subtitle != null && !expanded) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier
                        .heightIn(max = maxContentHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * 卡片内部的**可折叠子分区**（细线 + 小标题 + 右侧箭头）。
 * 用于把参数里的某一组（比如尺寸）单独收起来。
 */
@Composable
fun CollapsibleSubSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(top = 4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

/**
 * 卡片内部的次级分组标题。
 * 参数项全部平铺（参考实现的参数区就是不折叠的），但用细线 + 小标题分段，
 * 否则一屏十几个控件很难扫读。
 */
@Composable
fun SubHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 4.dp)) {
        HorizontalDivider()
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** 下拉选择。`options` 用 label/value 对，label 保持参考实现原文。 */
@Composable
fun PickerField(
    label: String,
    options: List<NaiOption>,
    selected: String,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selected }?.label ?: selected

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(selectedLabel, modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
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

/** 带数值显示的滑块。 */
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
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.72f),
                activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                inactiveTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            ),
        )
    }
}

/**
 * 开关行：左侧标题（+ 可选的「!」注释入口），右侧开关。
 *
 * ## 两个"说明"参数的分工（用户 2026-09-16 要求做"功能注释整合"）
 *
 *  · [help] —— **推荐**。标题右边挂一个「!」小圆标，点开才弹注释小窗口。
 *    默认状态下一行只有一个开关，界面干净；解释文字想看的时候才看。
 *  · [hint] —— 老写法，直接把说明铺在标题下面。**新代码别再用它**；
 *    现存的那几处正在逐个迁到 [help]。
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
    var helpOpen by remember { mutableStateOf(false) }
    var rowHeightPx by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().onSizeChanged { rowHeightPx = it.height },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    if (help != null) {
                        Spacer(Modifier.width(6.dp))
                        HelpBadge(onClick = { helpOpen = true })
                    }
                }
                if (hint != null) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
 * 「!」小圆标：功能开关旁边的注释入口（用户 2026-09-16 要求）。
 *
 * 手绘圆圈 + 一个文字感叹号，不另造图标：core 图标集里 `Info` 是实心圆里一个 i，
 * 而用户要的是**空心圆里一个 `!`**；这种纯几何 + 字形的东西直接画最省事，
 * 也不必再担心"抠 Material 路径抄漏一段"（[com.kallan.naistudio.ui.LockClosedIcon] 栽过）。
 */
@Composable
fun HelpBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .size(18.dp)
            .clip(CircleShape)
            .border(1.4.dp, color, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "!",
            color = color,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            // 去掉行高留白（手机是 includeFontPadding）：中文/默认字体的上下留白
            // 会把 "!" 在圆圈里压低。桌面没这个概念，平台层原样返回。
            style = LocalPlatform.current.compactTextStyle(LocalTextStyle.current),
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

/**
 * 「!」小圆标 **带自己的注释小窗口**（成对使用的简写）。
 *
 * [HelpBadge] 本身只是个按钮，弹窗得调用方自己挂（[SwitchRow] 就是这么写的：自己记一个
 * `helpOpen`、自己量行高）。这里是给**不是 [SwitchRow] 的自定义行**用的：直接用这一句就完事。
 *
 * @param alignEnd 弹窗往**左**弹（右对齐到徽标）。徽标靠近面板右缘时（比如漫画模式那个
 *   126dp 的窄列）必须开，否则 264dp 的窗口会顶出屏幕。
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
 * 注释小窗口。
 *
 * 贴在被点的「!」那一行的下方弹出（`Popup` 的 offset 相对父节点，所以只要给行高即可），
 * 窗口宽度固定、左右收在屏幕内。
 */
@Composable
private fun HelpPopup(
    text: String,
    topOffsetPx: Int,
    onDismiss: () -> Unit,
    alignEnd: Boolean = false,
) {
    Popup(
        alignment = if (alignEnd) Alignment.TopEnd else Alignment.TopStart,
        offset = IntOffset(0, topOffsetPx + 6),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.width(264.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
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
 * ## 为什么不是叠两个 Text
 *
 * 用两个独立的 [Text] 分别画描边和填充，两层各自的文字布局/抗锯齿会有细微差异，
 * 叠起来就是"错位"。
 *
 * ## 搬进共用树时重写了（原实现是 Android 专属）
 *
 * 老实现直接 `drawContext.canvas.nativeCanvas` 调 `android.graphics.Paint`
 * （`Typeface.DEFAULT_BOLD` + `measureText` + `fontMetrics` 手算基线），桌面端没有这一套。
 * 现在用 **Compose 自带的 [rememberTextMeasurer]**：量一次拿到 `TextLayoutResult`，
 * 再用**同一个布局对象**画两遍 —— 先 `drawStyle = Stroke(...)` 的黑描边，再白填充。
 * 对齐由"同一份布局"保证（比老实现更稳），而且两端都能跑。
 *
 * 观感上唯一的差异：布局高度是**行高**（含升/降部留白），老实现的 `descent - ascent`
 * 同样是文字自身高度，所以贴在图片上的位置基本一致。
 */
@Composable
fun StrokedText(text: String, modifier: Modifier = Modifier, fontSize: TextUnit = 12.sp) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = TextStyle(
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
    )
    // 量一次：描边与填充共用这一份布局，像素级对齐
    val layout = remember(text, style, density) { measurer.measure(text, style) }
    val strokeWidthPx = with(density) { fontSize.toPx() } * 0.18f
    val widthDp = with(density) { layout.size.width.toDp() }
    val heightDp = with(density) { layout.size.height.toDp() }

    Canvas(modifier.size(widthDp, heightDp)) {
        drawText(
            textLayoutResult = layout,
            color = Color.Black,
            topLeft = Offset.Zero,
            drawStyle = Stroke(width = strokeWidthPx, join = StrokeJoin.Round),
        )
        drawText(textLayoutResult = layout, color = Color.White, topLeft = Offset.Zero)
    }
}
