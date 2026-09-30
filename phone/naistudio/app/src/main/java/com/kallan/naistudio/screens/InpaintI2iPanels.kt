package com.kallan.naistudio.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import com.kallan.naistudio.ui.RefSlider as Slider
import androidx.compose.material3.Surface
import com.kallan.naistudio.ui.RefSwitch as Switch
import androidx.compose.material3.Text
import com.kallan.naistudio.ui.RefTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.state.AppState
import kotlin.math.roundToInt
import com.kallan.naistudio.ui.LocalRef
import com.kallan.naistudio.ui.glass
import com.kallan.naistudio.ui.RefBtn
import com.kallan.naistudio.ui.RefBtnKind
import com.kallan.naistudio.ui.RefStripTrigger
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 「重绘参数」触发行的行高（贴着工具条，薄一点不占图片）。 */
private val INPAINT_TRIGGER_HEIGHT = 28.dp

/** 紧凑滑杆的高度（Material 默认 48dp 太高，6 项叠起来会超过 400dp）。 */
private val COMPACT_SLIDER_HEIGHT = 28.dp

/**
 * 「重绘参数」的触发行：**紧贴在工具条下面**，文字居中 + 上/下箭头，**不用胶囊背景**
 * （和工具条同一底色，看起来就是工具条的第二行）。
 */
@Composable
internal fun InpaintParamsTrigger(
    t: (String) -> String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 网页 `.strip .trigger`（装在 RefStrip 里）
    RefStripTrigger(text = t("inpaint.params"), expanded = expanded, onClick = onToggle, modifier = modifier)
}

/**
 * 「重绘参数」面板：**从工具条下方滑出来、盖在图片上**（不占布局高度、不挤压图片），
 * 并且**一次把所有参数都摆出来**（不限高、不内部滚动）。
 *
 * 滑条与开关都用了紧凑排布：标签/数值同一行、滑杆高度压到 28dp，
 * 否则 Material 默认的 48dp 触控高度会把这 6 项撑到 400dp 以上。
 */
@Composable
internal fun InpaintParamsPanel(state: AppState, t: (String) -> String, modifier: Modifier = Modifier) {
    val settings = state.settings
    Surface(
        // 网页 `.strip .drop`：和工具条同一张 glass 卡的样子（圆角 16、描边、左右 12）
        shape = RoundedCornerShape(16.dp),
        color = LocalRef.current.glass(),
        contentColor = LocalRef.current.text,
        border = BorderStroke(1.dp, LocalRef.current.border),
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            // 吞掉面板范围内的点击：否则会穿透到下面的涂画层
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompactSlider(
                label = t("inpaint.strength"),
                valueText = "%.2f".format(settings.inpaintStrength),
                value = settings.inpaintStrength.toFloat(),
                range = 0.01f..1f,
                steps = 98,
                onValueChange = { value ->
                    state.setSettings { it.copy(inpaintStrength = value.toDouble()) }
                },
            )
            CompactSlider(
                label = t("inpaint.noise"),
                valueText = "%.2f".format(settings.inpaintNoise),
                value = settings.inpaintNoise.toFloat(),
                range = 0f..1f,
                steps = 99,
                onValueChange = { value ->
                    state.setSettings { it.copy(inpaintNoise = value.toDouble()) }
                },
            )
            CompactSlider(
                label = t("inpaint.maskExpand"),
                valueText = "${settings.inpaintMaskExpand} px",
                value = settings.inpaintMaskExpand.toFloat(),
                range = 0f..128f,
                steps = 127,
                onValueChange = { value ->
                    state.setSettings { it.copy(inpaintMaskExpand = value.roundToInt()) }
                },
            )
            CompactSlider(
                label = t("inpaint.maskFeather"),
                valueText = "${settings.inpaintMaskFeather} px",
                value = settings.inpaintMaskFeather.toFloat(),
                range = 0f..64f,
                steps = 63,
                onValueChange = { value ->
                    state.setSettings { it.copy(inpaintMaskFeather = value.roundToInt()) }
                },
            )
            CompactSwitch(
                label = t("inpaint.edgeProtection"),
                hint = t("inpaint.edgeProtectionHint"),
                checked = settings.inpaintEdgeProtection,
                onCheckedChange = { value ->
                    state.setSettings { it.copy(inpaintEdgeProtection = value) }
                },
            )
            CompactSwitch(
                label = t("inpaint.addOriginalImage"),
                hint = t("inpaint.addOriginalImageHint"),
                checked = settings.inpaintAddOriginalImage,
                onCheckedChange = { value ->
                    state.setSettings { it.copy(inpaintAddOriginalImage = value) }
                },
            )
        }
    }
}

/** 「图生图参数」的触发行（导入图片后才出现），和重绘参数同一套做法。 */
@Composable
internal fun I2iParamsTrigger(
    t: (String) -> String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 网页 `.strip .trigger`（装在 RefStrip 里）
    RefStripTrigger(text = t("i2i.params"), expanded = expanded, onClick = onToggle, modifier = modifier)
}

/** 「图生图参数」面板：强度 / 附加噪声 / 尺寸策略（默认值照 NAI 图生图采样器节点）。 */
@Composable
internal fun I2iParamsPanel(state: AppState, t: (String) -> String, modifier: Modifier = Modifier) {
    val settings = state.settings
    Surface(
        // 网页 `.strip .drop`：和工具条同一张 glass 卡的样子（圆角 16、描边、左右 12）
        shape = RoundedCornerShape(16.dp),
        color = LocalRef.current.glass(),
        contentColor = LocalRef.current.text,
        border = BorderStroke(1.dp, LocalRef.current.border),
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompactSlider(
                label = t("i2i.strength"),
                valueText = "%.2f".format(settings.i2iStrength),
                value = settings.i2iStrength.toFloat(),
                range = 0f..1f,
                steps = 99,
                onValueChange = { value ->
                    state.setSettings { it.copy(i2iStrength = value.toDouble()) }
                },
            )
            CompactSlider(
                label = t("i2i.noise"),
                valueText = "%.2f".format(settings.i2iNoise),
                value = settings.i2iNoise.toFloat(),
                range = 0f..1f,
                steps = 99,
                onValueChange = { value ->
                    state.setSettings { it.copy(i2iNoise = value.toDouble()) }
                },
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(t("i2i.sizeMode"), fontSize = 13.sp, color = LocalRef.current.muted, modifier = Modifier.weight(1f))
                RefBtn(
                    text = if (settings.i2iSizeMode == "core") t("i2i.sizeCore") else t("i2i.sizeSource"),
                    onClick = {
                        state.setSettings {
                            it.copy(i2iSizeMode = if (it.i2iSizeMode == "core") "source" else "core")
                        }
                    },
                    kind = RefBtnKind.Ghost,
                    small = true,
                )
            }
        }
    }
}

/** 紧凑滑条：标签与数值同一行，滑杆本身压到 [COMPACT_SLIDER_HEIGHT]。 */
@Composable
private fun CompactSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    // 放开 Material 的 48dp 最小触控高度，否则 6 项加起来超过 400dp
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 网页 `.slider-head`：12.5 muted，数值 text 500
                Text(label, fontSize = 12.5.sp, color = LocalRef.current.muted)
                Text(
                    valueText,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = LocalRef.current.text,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                steps = steps,
                modifier = Modifier.fillMaxWidth().height(COMPACT_SLIDER_HEIGHT),
            )
        }
    }
}

/** 紧凑开关行：左侧标题（+可选小字说明），右侧开关。 */
@Composable
private fun CompactSwitch(
    label: String,
    hint: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                // 网页 `.sw-row`：标题 14、小字 12 faint
                Text(label, fontSize = 14.sp, color = LocalRef.current.text)
                if (hint != null) {
                    Text(
                        hint,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = LocalRef.current.faint,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
