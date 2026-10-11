package com.kallan.naistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.models.ToolCatalog
import com.kallan.naistudio.platform.LocalUiHost
import com.kallan.naistudio.screens.ToolContent
import com.kallan.naistudio.state.AppState

internal val DesktopToolsRailWidth = 68.dp

/** Right sidebar: an always accessible tool rail and a collapsible tool body. */
@Composable
internal fun DesktopToolsPane(
    state: AppState,
    t: (String) -> String,
    widthState: MutableFloatState,
    maxWidth: Float,
    collapsed: Boolean,
    onToggleCollapsed: () -> Unit,
    onResizeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val limit by rememberUpdatedState(maxWidth)
    val commit by rememberUpdatedState(onResizeEnd)
    val focusManager = LocalFocusManager.current
    val cursor = LocalUiHost.current.horizontalResizeCursor()
    var dragging by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(collapsed) { if (collapsed) focusManager.clearFocus() }
    Row(modifier.background(NaiSkinTokens.sidebar())) {
        if (!collapsed) {
            Box(Modifier.width(7.dp).fillMaxHeight().then(cursor)
                .onGloballyPositioned { coordinates = it }.pointerInput(density, widthState) {
                    var startX = 0f
                    var startWidth = 0f
                    detectDragGestures(
                        onDragStart = { position ->
                            startX = coordinates?.localToRoot(position)?.x ?: 0f
                            startWidth = widthState.floatValue.coerceIn(240f, limit)
                            dragging = true
                        },
                        onDragEnd = { dragging = false; commit() },
                        onDragCancel = { dragging = false; commit() },
                    ) { change, _ ->
                        val rootX = coordinates?.localToRoot(change.position)?.x ?: return@detectDragGestures
                        change.consume()
                        // Absolute root coordinates stay stable while this divider itself moves.
                        widthState.floatValue = (startWidth + (startX - rootX) / density.density).coerceIn(240f, limit)
                    }
                }, contentAlignment = Alignment.Center) {
                Box(Modifier.width(if (dragging) 3.dp else 1.dp).fillMaxHeight()
                    .background(if (dragging) NaiSkinTokens.accent() else NaiSkinTokens.border()))
            }
            Column(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                    Text(t(ToolCatalog.byId(state.selectedTool).titleKey), color = NaiSkinTokens.text(),
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider(color = NaiSkinTokens.border())
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    key(state.selectedTool) { ToolContent(toolId = state.selectedTool, state = state) }
                }
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(NaiSkinTokens.border()))
        Column(Modifier.width(DesktopToolsRailWidth - 1.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = { focusManager.clearFocus(); onToggleCollapsed() }, modifier = Modifier.size(48.dp)) {
                Icon(if (collapsed) Icons.Filled.KeyboardArrowLeft else Icons.Filled.KeyboardArrowRight,
                    contentDescription = t(if (collapsed) "drawer.expand" else "drawer.collapse"), tint = NaiSkinTokens.text())
            }
            Text(t("tools.title"), color = NaiSkinTokens.muted(), style = MaterialTheme.typography.labelSmall)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolCatalog.entries.forEach { entry ->
                    val selected = state.selectedTool == entry.id
                    val icon = when (entry.id) {
                        ToolCatalog.DICTIONARY -> Icons.Filled.Search
                        ToolCatalog.ANIMADEX -> Icons.Filled.AccountCircle
                        ToolCatalog.TAGCODEX -> SparkleIcon
                        ToolCatalog.METADATA -> TuneIcon
                        else -> PictureIcon
                    }
                    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (selected) NaiSkinTokens.accentSoft() else androidx.compose.ui.graphics.Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab, onClick = {
                            focusManager.clearFocus()
                            state.selectTool(entry.id)
                            if (collapsed) onToggleCollapsed()
                        }).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp),
                            tint = if (selected) NaiSkinTokens.accent() else NaiSkinTokens.muted())
                        Spacer(Modifier.height(4.dp))
                        Text(t(entry.titleKey), color = if (selected) NaiSkinTokens.accent() else NaiSkinTokens.muted(),
                            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
