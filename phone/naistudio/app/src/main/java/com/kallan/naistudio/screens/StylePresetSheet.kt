package com.kallan.naistudio.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import com.kallan.naistudio.ui.RefCard as Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.kallan.naistudio.ui.RefIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.kallan.naistudio.ui.RefModalBottomSheet as ModalBottomSheet
import com.kallan.naistudio.ui.RefOutlinedButton as OutlinedButton
import com.kallan.naistudio.ui.RefOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.StylePreset
import com.kallan.naistudio.state.AppState
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp

/**
 * **预设面板**（用户 2026-09-24 两次定稿 ✓）。
 *
 * ## 第一版口径
 *
 * > 「预设功能改为**储存正面加负面**，**不是在分组里新加预设**，而是点击储存当前预设时
 * >  只显示**开关，名字，垃圾桶**，点击**下拉出现文本框**，可选**胶囊开关切换查看正面和负面**，
 * >  现有的预设**合并为正面提示词**」＋「**正面到风格，负面进负面**」
 *
 * ## 第二版口径（本次 ✓ —— 第一版那一行**太挤**）
 *
 * > 「预设列表的排布**紧凑点**，**字看不见了**，把**调整上下和展开预设的按钮删除**，
 * >  变为**点击展开**，加一个**三条横杠在左边**，**长按拖动**改变顺位」
 *
 * ⚠️ 为什么第一版"字看不见"（值得写下来 ✗ 免得再犯）：那一行里塞了
 * 勾选框 + **一个完整的 `OutlinedTextField`** + **三个 `IconButton`**
 *（M3 的 `IconButton` 默认 **48dp** 见方 ✓）⇒ 名字那一格被挤到只剩几十 dp ✓，文字自然看不到 ✓。
 *
 * ⇒ 第二版把这一行瘦下来 ✓：
 *  · **左边一条 ☰**（自绘 ✓，不占 48dp ✓）—— **长按拖动**改顺位 ✓；
 *  · **整行点击 = 展开 / 收起** ✓（箭头按钮删掉 ✓）；
 *  · **上下移按钮删掉** ✓（改拖动 ✓，又省两个 48dp ✓）；
 *  · 名字改成**一行纯文本** ✓（收起态不可编辑 ✓ —— 要改名**先点开展开**，
 *    展开区第一格就是名字输入框 ✓）⇒ 行里只写一行字 ✓，最省地方 ✓。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StylePresetSheet(state: AppState, onDismiss: () -> Unit) {
    val language = state.settings.language
    val t: (String) -> String = { key -> RuntimeText.text(language, key) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val refBorder = com.kallan.naistudio.ui.LocalRef.current.border
    // 「点了储存但两个框都空」⇒ 如实提示一句（不静默 ✓）
    var saveHint by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val library = state.styleLibrary
        val activeCount = library.activeCount()
        com.kallan.naistudio.ui.RefSheetTitle(
            title = t("preset.title"),
            extra = if (activeCount > 0) "${t("preset.activePrefix")}$activeCount${t("preset.activeSuffix")}" else null,
            onClose = onDismiss,
        )
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            com.kallan.naistudio.ui.RefHint(t("preset.applyHint"))
            if (saveHint) {
                com.kallan.naistudio.ui.RefHint(
                    t("preset.saveEmpty"),
                    color = com.kallan.naistudio.ui.LocalRef.current.negative,
                )
            }

            LazyColumn(
                Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (library.presets.isEmpty()) {
                    item {
                        Text(
                            t("preset.empty"),
                            fontSize = 13.5.sp,
                            color = com.kallan.naistudio.ui.LocalRef.current.faint,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        )
                    }
                }
                items(library.presets, key = { it.id }) { preset ->
                    PresetBlock(state = state, preset = preset, t = t)
                }
            }
        }
        // `.sh-foot`：顶部分隔线 + 两颗按钮（储存当前 = 主按钮、新建 = 次按钮）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .drawBehind {
                    drawLine(
                        refBorder,
                        androidx.compose.ui.geometry.Offset(0f, 0f),
                        androidx.compose.ui.geometry.Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            com.kallan.naistudio.ui.RefBtn(
                text = t("preset.add"),
                onClick = {
                    saveHint = false
                    state.addStylePreset()
                },
                kind = com.kallan.naistudio.ui.RefBtnKind.Secondary,
                icon = Icons.Filled.Add,
            )
            com.kallan.naistudio.ui.RefBtn(
                text = t("preset.saveCurrent"),
                onClick = { saveHint = !state.saveCurrentAsPreset() },
                modifier = Modifier.weight(1f),
                kind = com.kallan.naistudio.ui.RefBtnKind.Primary,
            )
        }
    }
}

/**
 * **一条预设**（第二版 ✓）：收起时只有 **☰ + 开关 + 名字 + 垃圾桶** 四样 ✓，
 * **点整行展开** ✓，展开区里是 **正面 / 负面胶囊 + 名字框 + 正文框** ✓。
 */
@Composable
private fun PresetBlock(state: AppState, preset: StylePreset, t: (String) -> String) {
    var expanded by remember { mutableStateOf(false) }
    // 这一条现在看的是正面还是负面 ✓（不落盘 ✓，每次从正面开始 ✓）
    var sidePositive by remember { mutableStateOf(true) }

    // 网页 `.preset`：field 底 + 描边 + 圆角 12；top 行 padding 8/8/8/6
    val ref = com.kallan.naistudio.ui.LocalRef.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ref.field),
    ) {
        Column(Modifier.padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
            // ---- 收起那一行：**☰ / 开关 / 名字 / 垃圾桶**（点整行 = 展开 ✓）----
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // **左边那条 ☰**：长按之后拖动改顺位（单击不做事 —— 免得跟"点整行展开"抢）
                PresetDragHandle(
                    onMove = { direction -> state.moveStylePreset(preset, direction) },
                    label = t("preset.dragHint"),
                )
                Checkbox(
                    checked = preset.enabled,
                    onCheckedChange = { value ->
                        state.updateStylePreset(preset.id) { it.copy(enabled = value) }
                    },
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.width(4.dp))
                // 名字：**一行纯文本**（收起态不编辑 ⇒ 行里只写一行字，最省地方）
                Text(
                    text = preset.name.ifBlank { t("preset.nameHint") },
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = if (preset.name.isBlank()) ref.faint else ref.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // `.pol`：11、胶囊；正面 = accentSoft/accent，负面 = negativeSoft/negative
                if (preset.positive.isNotBlank()) PresetPol(t("generate.positivePrompt").removeSuffix("提示词").removeSuffix(" prompt"), neg = false)
                if (preset.negative.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    PresetPol(t("generate.negativePrompt").removeSuffix("提示词").removeSuffix(" prompt"), neg = true)
                }
                IconButton(
                    onClick = { state.deleteStylePreset(preset.id) },
                    size = 32.dp,
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = t("common.delete"),
                        tint = ref.negative,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = ref.muted,
                    modifier = Modifier.size(20.dp),
                )
            }

            // ---- 展开区：名字 + 胶囊（正面 / 负面）+ 正文 ----
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                // 名字（展开才可改 —— 收起态那行只是显示）
                OutlinedTextField(
                    value = preset.name,
                    onValueChange = { value ->
                        state.updateStylePreset(preset.id) { it.copy(name = value) }
                    },
                    placeholder = { Text(t("preset.nameHint")) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        ,
                )
                Spacer(Modifier.height(6.dp))
                PresetSideCapsule(
                    positive = sidePositive,
                    onSelect = { sidePositive = it },
                    t = t,
                )
                Spacer(Modifier.height(6.dp))
                // ⚠️ 只画**被选中的那一面** ✓（"切换查看"✓）；写回去时另一面原样不动 ✓。
                val value = if (sidePositive) preset.positive else preset.negative
                OutlinedTextField(
                    value = value,
                    onValueChange = { next ->
                        state.updateStylePreset(preset.id) {
                            if (sidePositive) it.copy(positive = next) else it.copy(negative = next)
                        }
                    },
                    placeholder = {
                        Text(
                            if (sidePositive) t("preset.positiveHint") else t("preset.negativeHint"),
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp),
                )
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

/**
 * **预设那一行的 ☰ 拖动手柄**（用户 2026-09-24：「加一个**三条横杠在左边**，
 * **长按拖动**改变顺位」✓）。
 *
 * ## 三个刻意的取舍
 *
 *  1. **自绘三条横杠**（不用 `IconButton` / 不用 `Icons.Filled.Menu`）——
 *     `IconButton` 默认 **48dp** 见方 ✓，塞进这一行就是"一排全是按钮、名字没地方" ✗
 *     （这正是第一版"字看不见"的成因之一 ✓）；这里只要 **20dp 宽** ✓。
 *  2. **长按才开始拖**（`detectDragGesturesAfterLongPress` ✓）——
 *     单击不做事 ✓：这一行**整行点击 = 展开** ✓，手柄要是也吃单击就会互相抢 ✓。
 *  3. **拖动阈值 = 半个行高**（28dp ✓）—— 每拖过一截才交换一次 ✓，
 *     不然手指抖一下就连跳好几格 ✓。
 *
 * ⚠️ 与旧的 `DragHandle`（已随分组一起删掉 ✓）同一套手感 ✓：
 * 只是**不再进 `IconButton`** ✓、并**自己上报"往哪边移"** ✓。
 */
@Composable
private fun PresetDragHandle(
    onMove: (Int) -> Unit,
    label: String,
) {
    val threshold = with(LocalDensity.current) { 28.dp.toPx() }
    var accumulated by remember { mutableStateOf(0f) }
    val color = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        Modifier
            .size(width = 20.dp, height = 40.dp)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragEnd = { accumulated = 0f },
                    onDragCancel = { accumulated = 0f },
                ) { _, dragAmount ->
                    accumulated += dragAmount.y
                    while (accumulated >= threshold) {
                        onMove(1)
                        accumulated -= threshold
                    }
                    while (accumulated <= -threshold) {
                        onMove(-1)
                        accumulated += threshold
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(width = 16.dp, height = 12.dp)) {
            val stroke = 1.6f * density
            val widths = listOf(1f, 0.7f, 1f)
            val step = size.height / (widths.size + 1)
            widths.forEachIndexed { index, fraction ->
                val y = step * (index + 1)
                drawLine(
                    color = color,
                    start = Offset(0f, y),
                    end = Offset(size.width * fraction, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * **「正面 / 负面」长条胶囊**（样式照 `角色模式 / 漫画模式` 那个 `SegmentedCapsule` ✓，
 * 只有颜色按极性分 ✓：**正面蓝、负面红** ✓）。
 */
@Composable
private fun PresetSideCapsule(
    positive: Boolean,
    onSelect: (Boolean) -> Unit,
    t: (String) -> String,
) {
    val darkNow = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val negativeColor = if (darkNow) Color(0xFFE06C75) else Color(0xFFC62828)
    val selectedColor = if (positive) MaterialTheme.colorScheme.primary else negativeColor
    val selectedInk = if (positive) MaterialTheme.colorScheme.onPrimary else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(
            t("generate.positivePrompt") to true,
            t("generate.negativePrompt") to false,
        ).forEach { (label, isPositive) ->
            val isSelected = isPositive == positive
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) selectedColor else Color.Transparent)
                    .clickable { onSelect(isPositive) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        selectedInk
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PresetPol(text: String, neg: Boolean) {
    val ref = com.kallan.naistudio.ui.LocalRef.current
    Text(
        text,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = if (neg) ref.negative else ref.accent,
        modifier = Modifier
            .padding(end = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(if (neg) ref.negativeSoft else ref.accentSoft)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
