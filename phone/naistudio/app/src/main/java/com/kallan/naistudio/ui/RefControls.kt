package com.kallan.naistudio.ui

import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.KeyboardArrowDown

/**
 * **Reference 控件**（1.1.122，1:1 对齐手机网页原型 `20260928-phone-ui-web/index.html`）。
 *
 * 与 [RefButton] 同一个做法：签名贴近 M3，各页用 import 别名接过来
 * （`import com.kallan.naistudio.ui.RefSwitch as Switch` …），调用处一行不用改。
 */

// ---------------------------------------------------------------- 开关 .sw：42×26，1.5 描边，17 圆点

@Suppress("UNUSED_PARAMETER")
@Composable
fun RefSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    val ref = LocalRef.current
    val shape = RoundedCornerShape(50)
    val x by animateDpAsState(if (checked) 19.dp else 3.dp, tween(160), label = "sw")
    Box(
        modifier
            .size(42.dp, 26.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .background(if (checked) ref.accent else ref.subtle)
            .border(1.5.dp, if (checked) ref.accent else ref.borderStrong, shape)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            Modifier
                .offset(x = x - 1.5.dp, y = 3.dp - 1.5.dp)
                .padding(start = 1.5.dp, top = 1.5.dp)
                .size(17.dp)
                .clip(CircleShape)
                .background(if (checked) ref.accentContrast else ref.borderStrong),
        )
    }
}

// ---------------------------------------------------------------- 滑块：4dp 轨道 + 18dp 圆点（4dp panel 描圈）

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun RefSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val ref = LocalRef.current
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.alpha(if (enabled) 1f else 0.45f),
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        steps = steps,
        valueRange = valueRange,
        thumb = {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(ref.panel),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(ref.accent))
            }
        },
        track = { state: SliderState -> RefSliderTrack(state) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefSliderTrack(state: SliderState) {
    val ref = LocalRef.current
    val span = state.valueRange.endInclusive - state.valueRange.start
    val frac = if (span <= 0f) 0f else ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f)
    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(ref.strip, cornerRadius = r)
        drawRoundRect(ref.accent, size = Size(size.width * frac, size.height), cornerRadius = r, topLeft = Offset.Zero)
    }
}

// ---------------------------------------------------------------- 输入框 .field：field 底 + borderStrong 描边，≥44 高

@Composable
fun refFieldColors(): TextFieldColors {
    val ref = LocalRef.current
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = ref.text,
        unfocusedTextColor = ref.text,
        disabledTextColor = ref.muted,
        focusedContainerColor = ref.field,
        unfocusedContainerColor = ref.field,
        disabledContainerColor = ref.field,
        errorContainerColor = ref.field,
        cursorColor = ref.accent,
        focusedBorderColor = ref.focus,
        unfocusedBorderColor = ref.borderStrong,
        disabledBorderColor = ref.border,
        errorBorderColor = ref.negative,
        focusedPlaceholderColor = ref.faint,
        unfocusedPlaceholderColor = ref.faint,
        disabledPlaceholderColor = ref.faint,
        focusedLeadingIconColor = ref.faint,
        unfocusedLeadingIconColor = ref.faint,
        focusedTrailingIconColor = ref.muted,
        unfocusedTrailingIconColor = ref.muted,
        focusedSupportingTextColor = ref.faint,
        unfocusedSupportingTextColor = ref.faint,
        disabledSupportingTextColor = ref.faint,
        errorSupportingTextColor = ref.negative,
    )
}

/**
 * 原型的输入框：标签在框**上方**（12.5 muted），说明在框下方（12 faint），框本身 12 圆角 / 44 高 / 10·12 内边距。
 * M3 的浮动标签 + 56 高换掉了；参数与 `OutlinedTextField(value: String, …)` 同名。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = NaiShape.Field,
    colors: TextFieldColors = refFieldColors(),
) {
    val ref = LocalRef.current
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val merged = MaterialTheme.typography.bodyMedium.merge(textStyle)
        .merge(TextStyle(color = if (textStyle.color != Color.Unspecified) textStyle.color else if (enabled) ref.text else ref.muted))
    Column(modifier) {
        if (label != null) {
            CompositionLocalProvider(LocalContentColor provides ref.muted) {
                ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(color = ref.muted)) {
                    Box(Modifier.padding(bottom = 5.dp)) { label() }
                }
            }
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = merged,
            cursorBrush = SolidColor(ref.accent),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = source,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            decorationBox = { inner ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = inner,
                    enabled = enabled,
                    singleLine = singleLine,
                    visualTransformation = visualTransformation,
                    interactionSource = source,
                    isError = isError,
                    placeholder = placeholder?.let { p ->
                        { ProvideTextStyle(merged.copy(color = ref.faint)) { p() } }
                    },
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    prefix = prefix,
                    suffix = suffix,
                    colors = colors,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    container = {
                        OutlinedTextFieldDefaults.Container(
                            enabled = enabled,
                            isError = isError,
                            interactionSource = source,
                            colors = colors,
                            shape = shape,
                            focusedBorderThickness = 2.dp,
                            unfocusedBorderThickness = 1.dp,
                        )
                    },
                )
            },
        )
        if (supportingText != null) {
            CompositionLocalProvider(LocalContentColor provides if (isError) ref.negative else ref.faint) {
                ProvideTextStyle(MaterialTheme.typography.bodySmall.copy(color = if (isError) ref.negative else ref.faint)) {
                    Box(Modifier.padding(top = 6.dp)) { supportingText() }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 卡片 .card：panel 底 + border + 16 圆角

@Composable
fun RefCard(
    modifier: Modifier = Modifier,
    shape: Shape = NaiShape.Panel,
    colors: CardColors = CardDefaults.cardColors(containerColor = LocalRef.current.panel),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = BorderStroke(1.dp, LocalRef.current.border),
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Card(
    modifier = modifier,
    shape = shape,
    colors = colors,
    elevation = elevation,
    border = border,
    content = content,
)

// ---------------------------------------------------------------- 底部弹层 .sheet：顶 20 圆角 + 描边 + 36×4 把手

@Composable
fun RefSheetHandle() {
    val ref = LocalRef.current
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(36.dp, 4.dp)
                .clip(RoundedCornerShape(50))
                .background(ref.borderStrong.copy(alpha = 0.8f)),
        )
    }
}

/** 原型遮罩色：暗 rgba(4,12,18,.5) / 浅 rgba(24,50,71,.28)。 */
fun RefTokens.scrim(): Color = if (dark) Color(0x04, 0x0C, 0x12, 0x80) else Color(0x18, 0x32, 0x47, 0x47)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    containerColor: Color = LocalRef.current.panel,
    contentColor: Color = LocalRef.current.text,
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = LocalRef.current.scrim(),
    dragHandle: @Composable (() -> Unit)? = { RefSheetHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) = ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    modifier = modifier.border(1.dp, LocalRef.current.border, shape),
    sheetState = sheetState,
    sheetMaxWidth = sheetMaxWidth,
    shape = shape,
    containerColor = containerColor,
    contentColor = contentColor,
    tonalElevation = tonalElevation,
    scrimColor = scrimColor,
    dragHandle = dragHandle,
    contentWindowInsets = contentWindowInsets,
    properties = properties,
    content = content,
)

// ---------------------------------------------------------------- 对话框 .dialog：panel + 描边 + 20 圆角，标题 17/600

@Composable
fun RefAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color = LocalRef.current.panel,
    iconContentColor: Color = LocalRef.current.accent,
    titleContentColor: Color = LocalRef.current.text,
    textContentColor: Color = LocalRef.current.muted,
    tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties(),
) {
    val titleStyle = MaterialTheme.typography.titleMedium
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.border(1.dp, LocalRef.current.border, shape),
        dismissButton = dismissButton,
        icon = icon,
        title = title?.let { tt -> { ProvideTextStyle(titleStyle) { tt() } } },
        text = text,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}

// ---------------------------------------------------------------- 筛选胶囊 .fchip

@Composable
fun RefFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(50),
    colors: SelectableChipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = LocalRef.current.muted,
        iconColor = LocalRef.current.muted,
        selectedContainerColor = LocalRef.current.accentSoft,
        selectedLabelColor = LocalRef.current.accent,
        selectedLeadingIconColor = LocalRef.current.accent,
        selectedTrailingIconColor = LocalRef.current.accent,
    ),
    border: BorderStroke? = FilterChipDefaults.filterChipBorder(
        enabled = enabled,
        selected = selected,
        borderColor = LocalRef.current.borderStrong,
        selectedBorderColor = Color.Transparent,
        borderWidth = 1.dp,
        selectedBorderWidth = 0.dp,
    ),
    interactionSource: MutableInteractionSource? = null,
) = androidx.compose.material3.FilterChip(
    selected = selected,
    onClick = onClick,
    label = label,
    modifier = modifier,
    enabled = enabled,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    shape = shape,
    colors = colors,
    border = border,
    interactionSource = interactionSource,
)

// ---------------------------------------------------------------- 顶栏 .topbar：56 高，玻璃底 + 底边线，标题 17/600

@Composable
fun RefTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val ref = LocalRef.current
    Column(modifier.fillMaxWidth().background(ref.glass())) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            navigation?.invoke()
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = ref.text, maxLines = 1)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 11.5.sp, color = ref.faint, maxLines = 1)
                }
            }
            actions()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ref.border))
    }
}

/** 原型 --glass：暗 rgba(28,43,57,.88) / 浅 rgba(255,255,255,.9)（跟 panel 走，调色板改了也跟着变）。 */
fun RefTokens.glass(): Color = panel.copy(alpha = if (dark) 0.88f else 0.9f)

/** 原型 --sidebar：暗 panel·.9 / 浅 panel·.92。 */
fun RefTokens.sidebar(): Color = if (dark) mixSrgb(panel, 0.7f, bg).copy(alpha = 0.9f) else panel.copy(alpha = 0.92f)

/** 原型 --brand-star：暗 #5ABCF3 / 浅 #08799B。 */
fun RefTokens.brandStar(): Color = if (dark) Color(0xFF5ABCF3) else Color(0xFF08799B)

/** 原型 --brand-ink：暗 #F5F8FB / 浅 #183247。 */
fun RefTokens.brandInk(): Color = brandInkColor.takeOrElse { if (dark) Color(0xFFF5F8FB) else text }

/** `.sub-header`：12 / 600 / faint，字距 .3。 */
@Composable
fun RefSubHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 6.dp),
        fontSize = 12.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        letterSpacing = 0.3.sp,
        color = LocalRef.current.faint,
    )
}

/** `.hint`：12 faint，行高 1.55。 */
@Composable
fun RefHint(text: String, modifier: Modifier = Modifier, color: Color = LocalRef.current.faint) {
    Text(text, modifier = modifier, fontSize = 12.sp, lineHeight = 18.6.sp, color = color)
}


/** 原型 `.brand-mark` 里那颗四角星（viewBox 24）。 */
val RefBrandStarVector: ImageVector by lazy {
    ImageVector.Builder("brandStar", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes("M12 2c.6 5 2 7.4 7 8.5v.9c-5 1.1-6.4 3.5-7 8.6h-.9c-.6-5.1-2-7.5-7-8.6v-.9c5-1.1 6.4-3.5 7-8.5z"),
            fill = SolidColor(Color.White),
        )
        .build()
}

/** `.brand-mark`：56 方块、16 圆角、accent → brandStar 渐变，中间白色四角星。 */
@Composable
fun RefBrandMark(modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val ref = LocalRef.current
    val shape = RoundedCornerShape(size * (16f / 56f))
    Box(
        modifier
            .size(size)
            .shadow(8.dp, shape, ambientColor = Color(0x4D28AAD2), spotColor = Color(0x4D28AAD2))
            .clip(shape)
            .background(Brush.linearGradient(listOf(ref.accent, ref.brandStar()))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(RefBrandStarVector, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * (30f / 56f)))
    }
}

/**
 * `.nav-item`：内边距 10/12、12 圆角、14/500 muted，图标与字间距 12；
 * 选中 = selected 底 + onSelected 字 + selectedBorder 描边。
 */
@Composable
fun RefNavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ref = LocalRef.current
    val shape = RoundedCornerShape(12.dp)
    val fg = if (selected) ref.onSelected else ref.muted
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) ref.selected else Color.Transparent)
            .border(1.dp, if (selected) ref.selectedBorder else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 15.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, color = fg, modifier = Modifier.weight(1f))
        trailing?.let { CompositionLocalProvider(LocalContentColor provides fg) { it() } }
    }
}

/** `.subnav button`：7/10 内边距、8 圆角；标题 13.5/500，说明 11 faint；选中 = accentSoft + selectedBorder + accent 标题。 */
@Composable
fun RefSubNavItem(
    title: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ref = LocalRef.current
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) ref.accentSoft else Color.Transparent)
            .border(1.dp, if (selected) ref.selectedBorder else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(title, fontSize = 13.5.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, color = if (selected) ref.accent else ref.text)
        Text(hint, fontSize = 11.sp, lineHeight = 15.sp, color = ref.faint, maxLines = 1, modifier = Modifier.padding(top = 1.dp))
    }
}

/**
 * `.sh-head` 的标题行：h3 17/600 + 可选 extra（12.5 accent）+ 右侧 32 小关闭钮。
 * 把手由 [RefModalBottomSheet] 的 dragHandle 画，这里只画标题行（内边距 6/8/8/12 + 外层 8）。
 */
@Composable
fun RefSheetTitle(
    title: String,
    modifier: Modifier = Modifier,
    extra: String? = null,
    onClose: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val ref = LocalRef.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 0.dp, bottom = 8.dp)
            .defaultMinSize(minHeight = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = ref.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        if (extra != null) Text(extra, fontSize = 12.5.sp, color = ref.accent)
        actions()
        if (onClose != null) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = ref.muted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** `.icon-btn`：40（sm 32）方、8 圆角、muted 图标；`on` 时 accent。 */
@Composable
fun RefIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    on: Boolean = false,
    size: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    val ref = LocalRef.current
    Box(
        modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (on) ref.accent else ref.muted) { content() }
    }
}

/**
 * 与 M3 `TopAppBar(navigationIcon, title, actions, colors)` 同名参数的 `.topbar`：
 * 56 高、glass 底、底边 1px 描边、标题 17/600、图标 muted。状态栏内边距自己吃。
 */
@Composable
fun RefTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    @Suppress("UNUSED_PARAMETER") colors: Any? = null,
) {
    val ref = LocalRef.current
    Column(modifier.fillMaxWidth().background(ref.glass())) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides ref.muted) { navigationIcon() }
            Box(Modifier.weight(1f).padding(start = 4.dp), contentAlignment = Alignment.CenterStart) {
                CompositionLocalProvider(LocalContentColor provides ref.text) {
                    ProvideTextStyle(MaterialTheme.typography.titleMedium) { title() }
                }
            }
            CompositionLocalProvider(LocalContentColor provides ref.muted) {
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(ref.border))
    }
}


// ---------------------------------------------------------------------------
// 网页原型的线条图标（`.i`：24 视框、描边 1.8、圆头圆角）
// ---------------------------------------------------------------------------
private fun refStroke(name: String, d: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        .addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()

val RefResizeIcon: ImageVector by lazy { refStroke("resize", "M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7") }
val RefMovieIcon: ImageVector by lazy {
    refStroke("movie", "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM3 9h18M7 5l2 4M12 5l2 4M17 5l2 4")
}
val RefUsersIcon: ImageVector by lazy {
    refStroke("users", "M6 8a3 3 0 1 0 6 0a3 3 0 1 0-6 0M3 20a6 6 0 0 1 12 0M16 5a3 3 0 0 1 0 6M21 20a6 6 0 0 0-4-5.6")
}
val RefTrashIcon: ImageVector by lazy { refStroke("trash", "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6") }
val RefHistoryIcon: ImageVector by lazy { refStroke("history", "M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5M12 7v5l3 2") }
val RefTranslateIcon: ImageVector by lazy {
    refStroke("translate", "M4 5h9M8.5 3v2M6 5c.5 3.5 2.5 6.5 5.5 8M11 5c-.5 3.5-3 6.5-7 8.5M13 21l4-9 4 9M14.5 18h5")
}
val RefUpIcon: ImageVector by lazy { refStroke("up", "M6 15l6-6 6 6") }
val RefEditIcon: ImageVector by lazy { refStroke("pencil", "M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4") }
val RefDropIcon: ImageVector by lazy { refStroke("drop", "M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z") }
val RefChevronRightIcon: ImageVector by lazy { refStroke("right", "M9 6l6 6-6 6") }

/**
 * 网页 v6 `.opt`：选项卡片 —— 高 44、圆角 12、subtle 底 + borderStrong 描边、13.5/600；
 * 左边 30 的 accentSoft 方块装 17 的 accent 图标，右边 [trailing]（数量徽章 + 箭头 / 开关）。
 * [selected] = 描边换成 selectedBorder（透明背景开着时）。
 */
@Composable
fun RefOptionCard(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val ref = LocalRef.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .height(44.dp)
            .clip(shape)
            .background(ref.subtle)
            .border(1.dp, if (selected) ref.selectedBorder else ref.borderStrong, shape)
            .clickable(onClick = onClick)
            // 用户 2026-09-29：「角色分区 / 透明背景显示不全（角色… / 透明…）」⇒ 两格并排时收紧 ✓
            .padding(start = 5.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)).background(ref.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = ref.accent, modifier = Modifier.size(15.dp))
        }
        Text(
            text,
            color = ref.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** 网页 v6 `.opt .n`：数量徽章 —— 高 20、圆角 10、accent 底 12/700；为 0 = strip 底 muted 字。 */
@Composable
fun RefCountBadge(n: Int) {
    val ref = LocalRef.current
    Box(
        Modifier
            .defaultMinSize(minWidth = 20.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (n > 0) ref.accent else ref.strip)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            n.toString(),
            color = if (n > 0) ref.accentContrast else ref.muted,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** `.btn` 的四种样式。 */
enum class RefBtnKind { Primary, Secondary, Tint, Ghost }

/**
 * `.btn`：min 40、padding 9/14、圆角 12、14/600；`sm` = min 32、padding 6/12、13。
 * primary = accent 底；secondary = subtle 底 + borderStrong 描边；tint = accentSoft/accent；ghost = 透明/accent。
 */
@Composable
fun RefBtn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: RefBtnKind = RefBtnKind.Primary,
    small: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    danger: Boolean = false,
    /** 图标单独上色（网页 v6 `.act-row .btn .i { color: accent }`）；不给 = 跟字同色。 */
    iconTint: Color = Color.Unspecified,
) {
    val ref = LocalRef.current
    val shape = RoundedCornerShape(12.dp)
    val bg: Color
    val fg: Color
    var bd: Color? = null
    when (kind) {
        RefBtnKind.Primary -> { bg = if (danger) ref.negative else ref.accent; fg = ref.accentContrast }
        RefBtnKind.Secondary -> { bg = ref.subtle; fg = if (danger) ref.negative else ref.text; bd = ref.borderStrong }
        RefBtnKind.Tint -> { bg = if (danger) ref.negativeSoft else ref.accentSoft; fg = if (danger) ref.negative else ref.accent }
        RefBtnKind.Ghost -> { bg = Color.Transparent; fg = if (danger) ref.negative else ref.accent }
    }
    Row(
        modifier
            .defaultMinSize(minHeight = if (small) 32.dp else 40.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .background(bg)
            .then(if (bd != null) Modifier.border(1.dp, bd, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (small) 12.dp else 14.dp, vertical = if (small) 6.dp else 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = iconTint.takeOrElse { fg }, modifier = Modifier.size(16.dp))
        Text(
            text,
            color = fg,
            fontSize = if (small) 13.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** `.chip`：12.5/500、padding 5/12、胶囊、accentSoft 底 accent 字；`box` = 8 圆角。 */
@Composable
fun RefChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    box: Boolean = false,
    icon: ImageVector? = null,
) {
    val ref = LocalRef.current
    val shape = if (box) RoundedCornerShape(8.dp) else RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(ref.accentSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = ref.accent, modifier = Modifier.size(14.dp))
        Text(text, color = ref.accent, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** `.fchip`：padding 6/12、胶囊、borderStrong 描边 muted 字；on = accentSoft 底 accent 字 500。 */
@Composable
fun RefFChip(
    text: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ref = LocalRef.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (on) ref.accentSoft else Color.Transparent)
            .then(if (on) Modifier else Modifier.border(1.dp, ref.borderStrong, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            color = if (on) ref.accent else ref.muted,
            fontSize = 12.5.sp,
            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/**
 * `.seg`：field 底、border、圆角 12、padding 4、间距 4；
 * 格子 padding 7/12、圆角 8、13/500 muted；on = accentSoft/accent/borderStrong 600；
 * on + neg = negativeSoft/negative/negative。
 */
@Composable
fun RefSeg(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    negIndex: Int = -1,
) {
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
        options.forEachIndexed { i, label ->
            val on = i == selected
            val neg = on && i == negIndex
            val shape = RoundedCornerShape(8.dp)
            Box(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(
                        when {
                            neg -> ref.negativeSoft
                            on -> ref.accentSoft
                            else -> Color.Transparent
                        },
                    )
                    .border(
                        1.dp,
                        when {
                            neg -> ref.negative
                            on -> ref.borderStrong
                            else -> Color.Transparent
                        },
                        shape,
                    )
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = when {
                        neg -> ref.negative
                        on -> ref.accent
                        else -> ref.muted
                    },
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** `.tabs`：底边线、左右 4；格子 padding 11/10、14/500 muted；on = accent 600 + 30%~70% 的 2px 下划线。 */
@Composable
fun RefTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ref = LocalRef.current
    Box(modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Box(Modifier.matchParentSize().padding(top = 0.dp), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(ref.border))
        }
        Row(Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { i, label ->
                val on = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(i) }
                        .padding(top = 11.dp, bottom = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (on) ref.accent else ref.muted,
                        fontSize = 14.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
        Row(Modifier.matchParentSize(), verticalAlignment = Alignment.Bottom) {
            tabs.forEachIndexed { i, _ ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                    if (i == selected) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.4f)
                                .height(2.dp)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(ref.accent),
                        )
                    }
                }
            }
        }
    }
}

/** `.statusline`：min 32、圆角 8、field 底、border、padding 0/10、12 faint（busy = accent）。 */
@Composable
fun RefStatusLine(
    text: String,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ref = LocalRef.current
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 32.dp)
            .clip(shape)
            .background(ref.field)
            .border(1.dp, ref.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val c = if (busy) ref.accent else ref.faint
        Text(
            text,
            color = c,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(RefUpIcon, contentDescription = null, tint = c, modifier = Modifier.size(14.dp))
    }
}

/**
 * `.strip`：左右 12 悬空、glass 底、描边、圆角 16、padding 8/10、间距 8、shadow-sm。
 * 遮罩工具条 / 图生图条都装在里面。
 */
@Composable
fun RefStrip(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val ref = LocalRef.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            .shadow(2.dp, shape)
            .clip(shape)
            .background(ref.glass())
            .border(1.dp, ref.border, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** `.strip .trigger`：13/500 accent + xs 下箭头（展开转 180°），左对齐。 */
@Composable
fun RefStripTrigger(text: String, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ref = LocalRef.current
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text, color = ref.accent, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        val angle by animateFloatAsState(if (expanded) 180f else 0f, label = "stripTrigger")
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = ref.accent,
            modifier = Modifier.size(14.dp).rotate(angle),
        )
    }
}
