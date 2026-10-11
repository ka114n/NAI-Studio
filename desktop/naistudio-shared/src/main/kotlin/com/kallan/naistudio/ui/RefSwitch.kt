package com.kallan.naistudio.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

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
    val accent = NaiSkinTokens.accent()
    val subtle = NaiSkinTokens.subtle()
    val border = NaiSkinTokens.borderStrong()
    val contrast = NaiSkinTokens.accentContrast()
    val progress by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "switchThumb")
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    Canvas(modifier.size(42.dp, 26.dp).alpha(if (enabled) 1f else 0.38f)
        .then(if (onCheckedChange != null) Modifier.toggleable(value = checked, enabled = enabled,
            role = Role.Switch, interactionSource = interaction, indication = null,
            onValueChange = onCheckedChange) else Modifier)) {
        val trackHeight = minOf(size.height, size.width)
        val top = (size.height - trackHeight) / 2f
        val stroke = minOf(1.5.dp.toPx(), trackHeight / 8f)
        val radius = trackHeight / 2f
        val corner = CornerRadius(radius, radius)
        drawRoundRect(if (checked) accent else subtle, topLeft = Offset(0f, top),
            size = Size(size.width, trackHeight), cornerRadius = corner)
        drawRoundRect(if (checked) accent else border, topLeft = Offset(stroke / 2f, top + stroke / 2f),
            size = Size(size.width - stroke, trackHeight - stroke),
            cornerRadius = CornerRadius(radius - stroke / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        val fraction = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) 1f - progress else progress
        drawCircle(if (checked) contrast else border, radius = trackHeight * (17f / 52f),
            center = Offset(radius + (size.width - trackHeight) * fraction, size.height / 2f))
    }
}
