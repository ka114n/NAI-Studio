package com.kallan.naistudio.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.kallan.naistudio.models.BrushShape
import com.kallan.naistudio.models.CanvasPalette
import com.kallan.naistudio.models.CanvasTool
import com.kallan.naistudio.models.ImageEditOps
import com.kallan.naistudio.models.MaskPoint
import com.kallan.naistudio.models.SelectionOps
import com.kallan.naistudio.models.SelectionShape
import com.kallan.naistudio.models.fitNaiImageSize
import com.kallan.naistudio.platform.LocalUiHost
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.BlurIcon
import com.kallan.naistudio.ui.BucketIcon
import com.kallan.naistudio.ui.CanvasSelectIcon
import com.kallan.naistudio.ui.CloneIcon
import com.kallan.naistudio.ui.DropperIcon
import com.kallan.naistudio.ui.EraserIcon
import com.kallan.naistudio.ui.ExpandCornersIcon
import com.kallan.naistudio.ui.LassoIcon
import com.kallan.naistudio.ui.LocalEditorShortcuts
import com.kallan.naistudio.ui.NaiColorPicker
import com.kallan.naistudio.ui.NaiHexField
import com.kallan.naistudio.ui.NaiSwatchRow
import com.kallan.naistudio.ui.RedoIcon
import com.kallan.naistudio.ui.TuneIcon
import com.kallan.naistudio.ui.UndoIcon
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** 顶栏 / 工具行的高度。 */
private val CanvasBarHeight = 48.dp

/** 工具行里每颗按钮的边长（悬停提示的定位也用它）。 */
private val CanvasBarButtonSize = 40.dp

/** 参数行的高度（比工具行矮一点，它只有滑杆和几个小按钮）。 */
private val CanvasParamRowHeight = 44.dp

/** 画布区棋盘格的格子边长（表示"这里是透明的"）。 */
private val CanvasCheckerCell = 12.dp

/** 画布缩放的上下限。 */
private const val CANVAS_MIN_SCALE = 0.1f
private const val CANVAS_MAX_SCALE = 16f
private const val CANVAS_WHEEL_ZOOM_STEP = 0.15f

/**
 * **画布编辑器**（运行条那颗铅笔）：官方 Canvas 那一套工具。
 *
 * 和「遮罩」是两件事：这里改的是**图本身的像素**，点「完成」之后这张图变成图生图底图；
 * 遮罩那颗按钮管的是"局部重绘涂哪一块"，图不动。
 *
 * 结构（上行→下行）：顶栏（取消 / 重置 / 完成）→ 工具行（8 个工具 + 右侧一组）→
 * 参数行（跟着当前工具换）→ 画布。
 */
@Composable
internal fun CanvasEditorScreen(
    state: AppState,
    t: (String) -> String,
    onCancel: () -> Unit,
    onCommit: () -> Unit,
) {
    var colorDialogOpen by remember { mutableStateOf(false) }
    var resizeDialogOpen by remember { mutableStateOf(false) }

    // 键位：编辑器只在**自己被组合着**的时候接管键盘（关掉自动注销）
    val shortcuts = LocalEditorShortcuts.current
    val liveState = rememberUpdatedState(state)
    DisposableEffect(shortcuts) {
        shortcuts.register { event -> onCanvasShortcut(liveState.value, event) }
        onDispose {
            shortcuts.register(null)
            // 编辑器没了就把"空格按住"复位（不然切页出去再回来，空格状态还是按着的）
            liveState.value.holdCanvasSpace(false)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize()) {
            CanvasTopBar(state, t, onCancel = onCancel, onCommit = onCommit)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CanvasToolRow(
                state = state,
                t = t,
                onOpenResize = { resizeDialogOpen = true },
            )
            CanvasParamRow(state, t, onOpenColor = { colorDialogOpen = true })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CanvasStage(state, t)
        }
    }

    if (colorDialogOpen) {
        CanvasColorDialog(
            state = state,
            t = t,
            initial = state.canvasColor,
            onDismiss = {
                state.reportCanvasTextFocus(false)
                colorDialogOpen = false
            },
            onPick = { color ->
                state.chooseCanvasColor(color)
                state.reportCanvasTextFocus(false)
                colorDialogOpen = false
            },
        )
    }

    if (resizeDialogOpen) {
        CanvasResizeDialog(
            state = state,
            t = t,
            onDismiss = {
                state.reportCanvasTextFocus(false)
                resizeDialogOpen = false
            },
            onApply = { width, height, left, top ->
                state.applyCanvasResize(width, height, left, top)
                state.reportCanvasTextFocus(false)
                resizeDialogOpen = false
            },
        )
    }
}

// ---------------------------------------------------------------------------
// 顶栏
// ---------------------------------------------------------------------------

@Composable
private fun CanvasTopBar(
    state: AppState,
    t: (String) -> String,
    onCancel: () -> Unit,
    onCommit: () -> Unit,
) {
    val uiHost = LocalUiHost.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(CanvasBarHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Filled.Close, contentDescription = t("common.cancel"))
        }
        Text(
            t("canvas.title"),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "${state.canvasWidth}×${state.canvasHeight}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { state.resetCanvas() }) {
            Text(t("canvas.reset"), maxLines = 1)
        }
        Spacer(Modifier.width(6.dp))
        Button(
            onClick = {
                // 一个像素都没动过：明说一句（`closeCanvasEditor` 那边也不会落盘/切底图，
                // 见 `AppState.canvasHasChanges` —— 判据只有那一个）
                if (!state.canvasHasChanges) {
                    uiHost.toast(t("canvas.noChanges"), long = false)
                }
                onCommit()
            },
        ) {
            Text(t("canvas.done"), maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// 工具行
// ---------------------------------------------------------------------------

/** 工具图标：顺序 = [CanvasTool] 的声明顺序（官方那 8 个）。 */
private fun iconOf(tool: CanvasTool): ImageVector = when (tool) {
    CanvasTool.DRAW -> Icons.Filled.Edit
    CanvasTool.ERASE -> EraserIcon
    CanvasTool.FILL -> BucketIcon
    CanvasTool.SELECT -> CanvasSelectIcon
    CanvasTool.LASSO -> LassoIcon
    CanvasTool.PICKER -> DropperIcon
    CanvasTool.BLUR -> BlurIcon
    CanvasTool.CLONE -> CloneIcon
}

private fun labelKeyOf(tool: CanvasTool): String = when (tool) {
    CanvasTool.DRAW -> "canvas.tool.draw"
    CanvasTool.ERASE -> "canvas.tool.erase"
    CanvasTool.FILL -> "canvas.tool.fill"
    CanvasTool.SELECT -> "canvas.tool.select"
    CanvasTool.LASSO -> "canvas.tool.lasso"
    CanvasTool.PICKER -> "canvas.tool.picker"
    CanvasTool.BLUR -> "canvas.tool.blur"
    CanvasTool.CLONE -> "canvas.tool.clone"
}

@Composable
private fun CanvasToolRow(
    state: AppState,
    t: (String) -> String,
    onOpenResize: () -> Unit,
) {
    val uiHost = LocalUiHost.current
    val soonLabel = t("canvas.toolSoon")
    // HSV 调整是**挂在按钮下面的弹窗**（用户 2026-09-16 要求：不要在画面正中间开对话框）
    var hsvOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(CanvasBarHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CanvasTool.entries.forEach { tool ->
            val label = t(labelKeyOf(tool))
            CanvasToolButton(
                icon = iconOf(tool),
                label = label,
                selected = state.canvasTool == tool,
                dimmed = !tool.implemented,
                onClick = {
                    if (tool.implemented) {
                        state.chooseCanvasTool(tool)
                    } else {
                        // 还没做的工具：明说一句，别让人以为点了没反应
                        uiHost.toast(soonLabel.replace("{tool}", label), long = false)
                    }
                },
            )
        }

        Spacer(Modifier.weight(1f))

        // ---- 右侧那一组（官方截图里就是这几颗：调节 / 清除 / 调整画布 / 撤销 / 重做）----
        // 那颗"调节"在官方是设置面板；我们这里全图级的调整只有 HSV，就让它直接开 HSV 调整。
        Box {
            CanvasToolButton(
                icon = TuneIcon,
                label = t("canvas.hsv"),
                selected = hsvOpen,
                dimmed = false,
                onClick = { hsvOpen = !hsvOpen },
            )
            DropdownMenu(
                expanded = hsvOpen,
                onDismissRequest = {
                    state.cancelCanvasHsv()
                    hsvOpen = false
                },
            ) {
                CanvasHsvPanel(
                    state = state,
                    t = t,
                    onApply = {
                        state.commitCanvasHsv()
                        hsvOpen = false
                    },
                    onCancel = {
                        state.cancelCanvasHsv()
                        hsvOpen = false
                    },
                )
            }
        }
        CanvasToolButton(
            icon = Icons.Filled.Delete,
            label = t("canvas.clear"),
            selected = false,
            dimmed = !state.canvasCanUndo,
            onClick = { state.clearCanvas() },
        )
        CanvasToolButton(
            icon = ExpandCornersIcon,
            label = t("canvas.resize"),
            selected = false,
            dimmed = false,
            onClick = onOpenResize,
        )
        CanvasToolButton(
            icon = UndoIcon,
            label = t("canvas.undo"),
            selected = false,
            dimmed = !state.canvasCanUndo,
            onClick = { state.undoCanvas() },
        )
        CanvasToolButton(
            icon = RedoIcon,
            label = t("canvas.redo"),
            selected = false,
            dimmed = !state.canvasCanRedo,
            onClick = { state.redoCanvas() },
        )
    }
}

@Composable
private fun CanvasToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    // 悬停显示工具名（用户 2026-09-16 要求）。
    // 为什么不用 `TooltipArea`：那是**桌面专属** API，共用树里编不过（同一个理由见 §27 的注释），
    // 所以自己来：`PointerEventType.Enter/Exit` 判悬停 + `Popup` 挂在按钮正下方。
    var hovered by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(CanvasBarButtonSize)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        when (awaitPointerEvent().type) {
                            PointerEventType.Enter -> hovered = true
                            PointerEventType.Exit -> hovered = false
                            else -> Unit
                        }
                    }
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier
                .size(22.dp)
                .alpha(if (dimmed) 0.38f else 1f),
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
    if (hovered) {
        // ⚠️ 位置**必须自己算**：`Popup(offset = …)` 在桌面端是按窗口左上角算的，
        // 结果提示会跑到窗口左上角去（用户 2026-09-16 报的"名字不在图标下面"）。
        // 用 `PopupPositionProvider` 拿到锚点（按钮）自己的 bounds，才能稳定贴在下面。
        Popup(
            popupPositionProvider = remember {
                object : PopupPositionProvider {
                    override fun calculatePosition(
                        anchorBounds: IntRect,
                        windowSize: IntSize,
                        layoutDirection: LayoutDirection,
                        popupContentSize: IntSize,
                    ): IntOffset {
                        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
                        val y = anchorBounds.bottom + 4
                        // 别跑出窗口右边（边上的按钮提示会溢出）
                        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                        return IntOffset(x.coerceIn(0, maxX), y)
                    }
                }
            },
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 4.dp,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 参数行（跟着当前工具换）
// ---------------------------------------------------------------------------

@Composable
private fun CanvasParamRow(
    state: AppState,
    t: (String) -> String,
    onOpenColor: () -> Unit,
) {
    val tool = state.canvasTool
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // Material 组件默认要求 48dp 的最小触控高度，会把 44dp 的参数行撑高 ——
        // 这里显式放开（和遮罩那条单行工具条同一个做法）。
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(CanvasParamRowHeight)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (tool) {
                    CanvasTool.DRAW -> {
                        ColorSwatch(state.canvasColor, onClick = onOpenColor)
                        BrushSizeControls(state, t)
                        BrushShapeControls(state, t)
                    }

                    CanvasTool.ERASE -> {
                        BrushSizeControls(state, t)
                        BrushShapeControls(state, t)
                    }

                    // 油漆桶：官方就是"当前颜色 + 容忍度"两样
                    CanvasTool.FILL -> {
                        ColorSwatch(state.canvasColor, onClick = onOpenColor)
                        LabeledValue(t("canvas.tolerance"), "${state.canvasFillTolerance}")
                        Slider(
                            value = state.canvasFillTolerance.toFloat(),
                            onValueChange = { raw -> state.chooseCanvasFillTolerance(raw.roundToInt()) },
                            valueRange = 0f..255f,
                            modifier = Modifier.width(200.dp),
                        )
                        LabeledValue(t("canvas.hint.fill"), "", dim = true)
                    }

                    CanvasTool.BLUR -> {
                        BrushSizeControls(state, t)
                        LabeledValue(t("canvas.intensity"), "${state.canvasBlurIntensity}")
                        Slider(
                            value = state.canvasBlurIntensity.toFloat(),
                            onValueChange = { raw -> state.chooseCanvasBlurIntensity(raw.roundToInt()) },
                            valueRange = 0f..100f,
                            modifier = Modifier.width(160.dp),
                        )
                    }

                    // 图章：大小/笔尖 + 源点状态（源点靠 Alt+点击定）
                    CanvasTool.CLONE -> {
                        BrushSizeControls(state, t)
                        BrushShapeControls(state, t)
                        ParamHint(
                            if (state.canvasCloneReady) {
                                t("canvas.cloneSourceSet")
                            } else {
                                t("canvas.hint.clone")
                            },
                        )
                    }

                    CanvasTool.PICKER -> ParamHint(t("canvas.hint.picker"))

                    // 三期：选区。框内拖＝移动，框角＝缩放（Shift 等比 / Alt 以中心），
                    // 上面那个圆点＝旋转（Shift 吸附 15°）；回车落回、Esc 取消。
                    CanvasTool.SELECT, CanvasTool.LASSO -> {
                        val (selWidth, selHeight) = state.canvasSelectionSize()
                        LabeledValue(
                            t("canvas.select.size"),
                            if (selWidth > 0 && selHeight > 0) {
                                "$selWidth×$selHeight"
                            } else {
                                t("canvas.select.none")
                            },
                        )
                        // 局部重绘的**自动尺寸**（用户 2026-09-26：「长宽自动计算……1024×1024
                        // 以下自动计算最高像素；宽高可以超过 1024，但总像素不行」）。
                        // `InpaintSize.forRect` 保证：等比 ✓、总面积 ≤ 1024×1024 ✓、两边 64 倍数 ✓。
                        // ⚠️ **只读**——这里只把"这块区域会被算成多大"显示出来，没有手改入口 ✗。
                        // 坐标口径：`canvasSelectionSize()` 走的是 `SelectionOps.sizeOf(shape, 画布宽, 画布高)`，
                        // 而画布的宽高**就是原图像素**（编辑器的画布尺寸 = 图片尺寸）—— 归一化选区
                        // → 原图像素这一步在 `AppState.canvasSelectionSize()` 里就已经做完了 ✓。
                        val (redrawW, redrawH) = state.canvasSelectionInpaintSize()
                        if (redrawW > 0 && redrawH > 0) {
                            LabeledValue(
                                t("canvas.select.redraw"),
                                "$redrawW×$redrawH（${aspectRatioLabel(selWidth, selHeight)} ✓）",
                            )
                        }
                        ParamHint(
                            if (tool == CanvasTool.SELECT) {
                                t("canvas.hint.select")
                            } else {
                                t("canvas.hint.lasso")
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParamHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 「标签 + 数值」那一小段（容忍度 / 强度都用它，省得每处再写一遍排版）。 */
@Composable
private fun LabeledValue(label: String, value: String, dim: Boolean = false) {
    Text(
        if (value.isEmpty()) label else "$label $value",
        style = MaterialTheme.typography.labelMedium,
        color = if (dim) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 当前颜色的小方块（点开选色）。 */
@Composable
private fun ColorSwatch(color: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(color))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    )
}

/** 笔刷大小：滑杆 + **可以直接点着改的数值框**（官方两种改法都给）。 */
@Composable
private fun BrushSizeControls(state: AppState, t: (String) -> String) {
    Text(
        t("canvas.penSize"),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
    Slider(
        value = state.canvasBrushPixels.toFloat(),
        onValueChange = { raw -> state.setCanvasBrush(raw.roundToInt()) },
        valueRange = ImageEditOps.BRUSH_MIN_PIXELS.toFloat()..ImageEditOps.BRUSH_MAX_PIXELS.toFloat(),
        modifier = Modifier.width(160.dp),
    )
    BrushSizeField(
        value = state.canvasBrushPixels,
        onCommit = { state.setCanvasBrush(it) },
        onFocusChange = { state.reportCanvasTextFocus(it) },
    )
}

/** 紧凑的数值输入（`NumberField` 是整行宽的，这一行塞不下）。 */
@Composable
private fun BrushSizeField(
    value: Int,
    onCommit: (Int) -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
) = CompactNumberField(value = value, onCommit = onCommit, onFocusChange = onFocusChange)

/**
 * 小号数字输入框（笔刷大小 / 调整画布的宽高都用它）。
 *
 * `onFocusChange` 不是可选的：编辑器**不能一边让人打字一边抢字母键**
 * （见 `AppState.canvasTextFocused` 的注释），所以每个文本输入框都得把焦点状态报上去。
 */
@Composable
private fun CompactNumberField(
    value: Int,
    onCommit: (Int) -> Unit,
    onFocusChange: (Boolean) -> Unit = {},
    width: Dp = 44.dp,
    maxLength: Int = 5,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    BasicTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() }.take(maxLength)
            text = filtered
            filtered.toIntOrNull()?.let(onCommit)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.labelLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier
            .width(width)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/** 笔尖形状：圆 / 软圆 / 方（官方那三种）。 */
@Composable
private fun BrushShapeControls(state: AppState, t: (String) -> String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        BrushShape.entries.forEach { shape ->
            val selected = state.canvasBrushShape == shape
            val label = when (shape) {
                BrushShape.ROUND -> t("canvas.shape.round")
                BrushShape.SOFT_ROUND -> t("canvas.shape.soft")
                BrushShape.SQUARE -> t("canvas.shape.square")
            }
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .clickable { state.chooseCanvasBrushShape(shape) },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(16.dp)) {
                    val ink = if (selected) Color(0xFF000000) else Color(0xFF808080)
                    when (shape) {
                        BrushShape.ROUND -> drawCircle(ink, radius = size.minDimension / 2f)
                        BrushShape.SOFT_ROUND -> {
                            // 软圆：外圈淡、内圈实 —— 一眼看出是"羽化"的
                            drawCircle(
                                color = ink.copy(alpha = 0.35f),
                                radius = size.minDimension / 2f,
                            )
                            drawCircle(ink, radius = size.minDimension / 3.4f)
                        }

                        BrushShape.SQUARE -> drawRect(
                            color = ink,
                            size = Size(size.width, size.height),
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 画布
// ---------------------------------------------------------------------------

/**
 * 画布本体：**滚轮缩放 / 中键或「空格+左键」拖动平移 / 左键涂抹**。
 *
 * 坐标换算和生成页预览同一套（图层以中心为原点缩放、再平移）：
 * `content = center + (screen - center - offset) / scale`，
 * 再减掉 `ContentScale.Fit` 出来的那个矩形，就得到图片像素坐标。
 *
 * ⚠️ 两条必须对齐的东西（对不上就是"图偏了、笔也偏了"）：
 *  1. 这个 Box 得是 `contentAlignment = Center` —— `fitRect` 算出来的是**居中**的矩形，
 *     外层默认左上角对齐的话，图会画在左上角，而命中判定仍按居中算（用户 2026-09-16 报的"错位"）；
 *  2. 图层的缩放原点是**图层中心**（`graphicsLayer` 默认），居中之后它正好等于上面的 `center`。
 */
@Composable
private fun CanvasStage(state: AppState, t: (String) -> String) {
    val bitmap = state.canvasImage()
    val imageWidth = state.canvasWidth
    val imageHeight = state.canvasHeight
    val tool = state.canvasTool
    val spacePan = state.canvasSpacePan

    var area by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var hover by remember { mutableStateOf<Offset?>(null) }
    // 正在拖动画布（空格+左键 / 中键）—— 只为换光标：拖动中 = 握手
    var panning by remember { mutableStateOf(false) }
    // 三期：选区手势（拖框 / 套索 / 移动 / 缩放 / 旋转）的状态
    val selection = remember { SelectionGestureState() }

    val uiHost = LocalUiHost.current
    val density = LocalDensity.current
    val aspect = if (imageHeight > 0) imageWidth.toFloat() / imageHeight.toFloat() else 0f
    val fit = fitRect(area, aspect)

    // 「可以拖动」= 张手，「正在拖动」= 握手（用户 2026-09-16 指定的两态）。
    // 空格没按、也没在拖的时候不加这个修饰符（画笔/吸管保持普通箭头）。
    val panCursor = if (spacePan || panning) uiHost.panCursor(grabbing = panning) else Modifier

    /** 屏幕上的一点 → 图片像素坐标（落在图片外就是 null）。 */
    fun toPixel(position: Offset): Offset? =
        offsetToImagePixel(position, area, imageWidth, imageHeight, scale, offset)

    /** 浮动块变换后的四个角（画布像素）；没浮动返回 null。 */
    fun floatingCorners(): List<Pair<Float, Float>>? = state.canvasFloating?.corners()

    /** 旋转控制点在屏幕上的位置（跟着浮动块的变换走）。 */
    fun rotateHandleScreen(): Offset? {
        val float = state.canvasFloating ?: return null
        val quad = float.corners()
        if (quad.size < 4) return null
        val topMidX = (quad[0].first + quad[1].first) / 2f
        val topMidY = (quad[0].second + quad[1].second) / 2f
        val bottomMidX = (quad[3].first + quad[2].first) / 2f
        val bottomMidY = (quad[3].second + quad[2].second) / 2f
        val outX = topMidX - bottomMidX
        val outY = topMidY - bottomMidY
        val outLength = hypot(outX, outY).coerceAtLeast(0.001f)
        val span = hypot(quad[1].first - quad[0].first, quad[1].second - quad[0].second)
            .coerceAtLeast(1f)
        val distance = (span * 0.18f).coerceIn(16f, 60f)
        return imageToScreen(
            topMidX + outX / outLength * distance,
            topMidY + outY / outLength * distance,
            area, imageWidth, imageHeight, scale, offset,
        )
    }

    /**
     * 选区的按下/拖动：第一次按下**决定这次是什么手势**，之后每一帧都是"更新"。
     *
     * 六个分支：拖新框 / 画套索 / 拖控制点缩放 / 拖旋转点 / 框内拖动（移动）/ 点框外（落回并重新框）。
     */
    fun selectionPress(
        screen: Offset,
        canvasX: Float,
        canvasY: Float,
        altDown: Boolean,
        shiftDown: Boolean,
    ) {
        val shape = state.canvasSelection
        if (selection.active) {
            when (selection.gesture) {
                SelectionGesture.MARQUEE -> state.updateCanvasSelection(
                    SelectionOps.rectShape(
                        selection.startX, selection.startY, canvasX, canvasY,
                        imageWidth, imageHeight,
                    ),
                )

                SelectionGesture.LASSO -> {
                    selection.lasso = selection.lasso + MaskPoint(
                        (canvasX / imageWidth).coerceIn(0f, 1f),
                        (canvasY / imageHeight).coerceIn(0f, 1f),
                    )
                    state.updateCanvasSelection(SelectionShape.Polygon(selection.lasso))
                }

                SelectionGesture.MOVE -> {
                    state.translateCanvasSelection(canvasX - selection.lastX, canvasY - selection.lastY)
                    selection.lastX = canvasX
                    selection.lastY = canvasY
                }

                SelectionGesture.SCALE -> {
                    val float = state.canvasFloating ?: return
                    val (localX, localY) = float.toLocal(canvasX, canvasY)
                    val (anchorLocalX, anchorLocalY) = float.toLocal(selection.anchorX, selection.anchorY)
                    val prevX = selection.lastLocalX
                    val prevY = selection.lastLocalY
                    val factorX = if (abs(prevX - anchorLocalX) < 1e-3f) {
                        1f
                    } else {
                        (localX - anchorLocalX) / (prevX - anchorLocalX)
                    }
                    val factorY = if (abs(prevY - anchorLocalY) < 1e-3f) {
                        1f
                    } else {
                        (localY - anchorLocalY) / (prevY - anchorLocalY)
                    }
                    // Shift = 等比（用 X 的比例，和官方"保持比例"一个意思）
                    val useX = if (shiftDown) factorX else factorX
                    val useY = if (shiftDown) useX else factorY
                    state.scaleCanvasSelection(useX, useY, selection.anchorX, selection.anchorY)
                    selection.lastLocalX = localX
                    selection.lastLocalY = localY
                }

                SelectionGesture.ROTATE -> {
                    val float = state.canvasFloating ?: return
                    val angle = Math.toDegrees(
                        atan2((canvasY - float.centerY).toDouble(), (canvasX - float.centerX).toDouble()),
                    ).toFloat()
                    var delta = angle - selection.lastAngle
                    while (delta > 180f) delta -= 360f
                    while (delta < -180f) delta += 360f
                    selection.lastAngle = angle
                    selection.rotationAccum += delta
                    val apply = if (shiftDown) {
                        // Shift = 吸附到 15°：按**累计角**吸附，只看单帧增量是吸不住的
                        val snapped = (selection.rotationAccum / 15f).roundToInt() * 15f
                        snapped - (selection.rotationAccum - delta)
                    } else {
                        delta
                    }
                    state.rotateCanvasSelection(apply)
                }

                SelectionGesture.NONE -> Unit
            }
            return
        }

        // ---- 第一次按下：决定手势 ----
        val corners = floatingCorners()
        val handle = if (shape != null) {
            selectionHandleAt(
                screen = screen,
                shape = shape,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                area = area,
                zoom = scale,
                offset = offset,
                corners = corners,
                rotateScreen = rotateHandleScreen(),
            )
        } else {
            null
        }
        val hasFloat = state.canvasFloating != null
        when {
            // 旋转点（只有在已经抬起来、四个角都在的时候才有意义）
            handle == SelectionHandle.ROTATE && hasFloat -> {
                val float = state.canvasFloating!!
                selection.gesture = SelectionGesture.ROTATE
                selection.lastAngle = Math.toDegrees(
                    atan2((canvasY - float.centerY).toDouble(), (canvasX - float.centerX).toDouble()),
                ).toFloat()
                selection.rotationAccum = 0f
            }

            // 控制点：缩放。锚点 = **对角那个点**（Alt = 以中心缩放）
            handle != null && shape != null -> {
                state.liftCanvasSelection()
                selection.gesture = SelectionGesture.SCALE
                selection.handle = handle
                val float = state.canvasFloating
                val points = selectionControlPoints(shape, imageWidth, imageHeight, corners)
                val opposite = oppositeHandle(handle)
                val anchor = if (altDown || float == null || opposite == null) {
                    // 以中心缩放：锚点就是中心
                    (canvasX) to (canvasY)
                } else {
                    val point = points[opposite]
                    if (point == null) canvasX to canvasY else point.x to point.y
                }
                selection.anchorX = anchor.first
                selection.anchorY = anchor.second
                if (float != null) {
                    val (localX, localY) = float.toLocal(canvasX, canvasY)
                    selection.lastLocalX = localX
                    selection.lastLocalY = localY
                }
            }

            // 框内拖动 = 移动整块（第一次移动才把像素抬起来）
            shape != null && !hasFloat && screenInsideSelection(
                screen, shape, imageWidth, imageHeight, area, scale, offset,
            ) -> {
                state.liftCanvasSelection()
                selection.gesture = SelectionGesture.MOVE
                selection.lastX = canvasX
                selection.lastY = canvasY
            }

            shape != null && hasFloat && screenInsideSelection(
                screen, shape, imageWidth, imageHeight, area, scale, offset,
            ) -> {
                selection.gesture = SelectionGesture.MOVE
                selection.lastX = canvasX
                selection.lastY = canvasY
            }

            // 其它地方：落回旧的那块，重新框一个
            else -> {
                if (hasFloat) state.commitCanvasSelection()
                selection.gesture = if (tool == CanvasTool.SELECT) {
                    SelectionGesture.MARQUEE
                } else {
                    SelectionGesture.LASSO
                }
                selection.startX = canvasX
                selection.startY = canvasY
                selection.lasso = listOf(
                    MaskPoint(
                        (canvasX / imageWidth).coerceIn(0f, 1f),
                        (canvasY / imageHeight).coerceIn(0f, 1f),
                    ),
                )
                if (selection.gesture == SelectionGesture.MARQUEE) {
                    state.updateCanvasSelection(
                        SelectionOps.rectShape(canvasX, canvasY, canvasX, canvasY, imageWidth, imageHeight),
                    )
                } else {
                    state.updateCanvasSelection(SelectionShape.Polygon(selection.lasso))
                }
            }
        }
    }

    /** 抬手：手势结束（浮动块**留在原地**，等换工具/回车/点框外再落回）。 */
    fun selectionRelease() {
        if (!selection.active) return
        // 只为"拖出一个小到看不见的框"兜个底：那种框直接不算数
        if (selection.gesture == SelectionGesture.MARQUEE) {
            val shape = state.canvasSelection
            if (shape is SelectionShape.Rect) {
                val wide = abs(shape.right - shape.left) * imageWidth
                val high = abs(shape.bottom - shape.top) * imageHeight
                if (wide < 3f || high < 3f) state.updateCanvasSelection(null)
            }
        }
        selection.reset()
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .onSizeChanged { area = it }
            .then(panCursor)
            .pointerInput(imageWidth, imageHeight, tool, spacePan) {
                if (imageWidth <= 0 || imageHeight <= 0) return@pointerInput
                awaitPointerEventScope {
                    var painting = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue

                        // 1) 滚轮 = 围绕指针缩放
                        val scroll = change.scrollDelta.y
                        if (scroll != 0f) {
                            val next = (scale * (1f - scroll * CANVAS_WHEEL_ZOOM_STEP))
                                .coerceIn(CANVAS_MIN_SCALE, CANVAS_MAX_SCALE)
                            if (next != scale) {
                                val anchor = Offset(
                                    x = change.position.x - area.width / 2f,
                                    y = change.position.y - area.height / 2f,
                                )
                                val ratio = next / scale
                                offset = Offset(
                                    x = anchor.x - (anchor.x - offset.x) * ratio,
                                    y = anchor.y - (anchor.y - offset.y) * ratio,
                                )
                                scale = next
                            }
                            change.consume()
                            continue
                        }

                        // 2) 平移：中键拖动，或**按住空格 + 左键拖动**（用户 2026-09-16 要求）。
                        //    左键在别的时候是画笔，所以这里必须先判空格，否则一按空格就画上去了。
                        val tertiary = change.type == PointerType.Mouse && event.buttons.isTertiaryPressed
                        if (tertiary || (spacePan && change.pressed)) {
                            if (painting) {
                                // 正在涂的时候按了空格：先把手头这一笔收了口，免得画出飞线
                                painting = false
                                state.endCanvasStroke()
                            }
                            panning = true
                            val delta = change.positionChange()
                            offset = Offset(offset.x + delta.x, offset.y + delta.y)
                            change.consume()
                            continue
                        }
                        // 抬手：结束拖动状态（光标回到张手）。⚠️ 少了这一句，
                        // 光标会一直停在"握手"，看着像还在拖（一开始就是这么漏的）。
                        if (panning && !change.pressed) {
                            panning = false
                            change.consume()
                            continue
                        }

                        if (change.type != PointerType.Mouse) continue

                        // 指针位置（画笔光标圈要用）
                        hover = change.position

                        // 3) 左键 = 涂抹 / 吸管 / 填充 / Alt 定图章源点
                        val primary = event.buttons.isPrimaryPressed
                        val point = toPixel(change.position)
                        if (change.pressed && primary) {
                            if (point != null) {
                                val px = point.x.toInt()
                                val py = point.y.toInt()
                                when {
                                    // Alt+点击 = 定仿制源点（官方口径；任何工具下都能定）
                                    event.keyboardModifiers.isAltPressed -> {
                                        state.setCanvasCloneSource(px, py)
                                    }

                                    // 吸管（选了吸管工具，或随手 Ctrl+点击）
                                    tool == CanvasTool.PICKER ||
                                        event.keyboardModifiers.isCtrlPressed -> {
                                        state.pickCanvasColor(px, py)
                                    }

                                    // 油漆桶：**一次点击只灌一次**（拖过去连灌会刷出一堆撤销步）
                                    tool == CanvasTool.FILL -> {
                                        if (!painting) state.fillCanvasAt(px, py)
                                    }

                                    // 三期：选区（矩形 / 套索 + 移动 / 缩放 / 旋转）。
                                    // 按下与拖动走同一个入口：它自己判"这次是新手势还是继续"。
                                    tool == CanvasTool.SELECT || tool == CanvasTool.LASSO -> {
                                        selectionPress(
                                            screen = change.position,
                                            canvasX = point.x,
                                            canvasY = point.y,
                                            altDown = event.keyboardModifiers.isAltPressed,
                                            shiftDown = event.keyboardModifiers.isShiftPressed,
                                        )
                                    }

                                    else -> {
                                        if (state.canvasDrawing) {
                                            state.canvasStrokeTo(point.x, point.y)
                                        } else {
                                            state.beginCanvasStroke(point.x, point.y)
                                        }
                                    }
                                }
                                painting = true
                            }
                            change.consume()
                        } else if (painting) {
                            painting = false
                            state.endCanvasStroke()
                            selectionRelease()
                        }
                    }
                }
            },
        // ⚠️ 居中！`fit` 是 `fitRect` 算出来的**居中**矩形，这里不居中就对不上（图偏、笔也偏）
        contentAlignment = Alignment.Center,
    ) {
        // 棋盘底：铺满整个画布区，表示"透明"
        CheckerboardBackground(Modifier.fillMaxSize())

        if (bitmap != null && fit.width > 0f) {
            val fitWidth = with(density) { fit.width.toDp() }
            val fitHeight = with(density) { fit.height.toDp() }
            Box(
                Modifier
                    .size(fitWidth, fitHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 三期：抬起来的那块（按当前变换画在正确的位置上）+ 选框/控制点
        val float = state.canvasFloating
        if (float != null) {
            CanvasFloatingLayer(
                bitmap = state.canvasFloatingImage(),
                patchWidth = float.patchWidth,
                patchHeight = float.patchHeight,
                originX = float.originX,
                originY = float.originY,
                patchScaleX = float.scaleX,
                patchScaleY = float.scaleY,
                rotationDeg = float.rotationDeg,
                deltaX = float.dx,
                deltaY = float.dy,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                area = area,
                zoom = scale,
                offset = offset,
            )
        }
        val shape = state.canvasSelection
        if (shape != null) {
            // ⚠️ 选框**一直画着**（用户 2026-09-16 要求"留存"）：换了画笔/模糊也看得见，
            // 因为这时候所有操作都被裁在选区里，看不见框等于在瞎画。
            // 控制点只在选择类工具下画 —— 别的工具下拖控制点没有意义。
            val selectTool = tool == CanvasTool.SELECT || tool == CanvasTool.LASSO
            CanvasSelectionOverlay(
                shape = shape,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                area = area,
                zoom = scale,
                offset = offset,
                corners = floatingCorners(),
                handlesVisible = selectTool,
            )
        }

        // 画笔光标圈：让"笔有多大"看得见（鼠标悬停 + 画笔类工具）。
        // ⚠️ **平移模式下不画**（用户 2026-09-16 要求）：那时候指针是"手"，
        // 再叠一个圆框既不对（此时不动笔）又挡视线。
        val cursor = hover
        val brushLike = tool == CanvasTool.DRAW || tool == CanvasTool.ERASE ||
            tool == CanvasTool.BLUR || tool == CanvasTool.CLONE
        if (cursor != null && brushLike && !spacePan && !panning && fit.width > 0f) {
            val radiusPx = with(density) { (state.canvasBrushPixels / 2f).dp.toPx() }
            val ringRadius = radiusPx * (fit.width / imageWidth) * scale
            val outline = MaterialTheme.colorScheme.onSurface
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = outline.copy(alpha = 0.9f),
                    radius = ringRadius.coerceAtLeast(1f),
                    center = cursor,
                    style = Stroke(width = 1.5f),
                )
            }
        }

        // 还没做的工具：画布中央给一句，避免"点了没反应"
        if (!tool.implemented) {
            Text(
                t("canvas.toolSoon").replace("{tool}", t(labelKeyOf(tool))),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** 棋盘格底（透明区可见）。 */
@Composable
private fun CheckerboardBackground(modifier: Modifier = Modifier) {
    val light = MaterialTheme.colorScheme.surfaceContainerLowest
    val dark = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier) {
        val cell = CanvasCheckerCell.toPx()
        if (cell <= 0f) return@Canvas
        drawRect(color = light, size = size)
        var row = 0
        var y = 0f
        while (y < size.height) {
            var col = 0
            var x = 0f
            while (x < size.width) {
                if ((row + col) % 2 == 0) {
                    drawRect(color = dark, topLeft = Offset(x, y), size = Size(cell, cell))
                }
                x += cell
                col++
            }
            y += cell
            row++
        }
    }
}

/**
 * 屏幕坐标 → **图片像素坐标**；落在图片外面返回 null。
 *
 * 和生成页预览同一套变换（图层中心为缩放原点、平移在缩放之后）：
 * `content = center + (screen - center - offset) / scale`。
 */
internal fun offsetToImagePixel(
    screen: Offset,
    area: IntSize,
    imageWidth: Int,
    imageHeight: Int,
    scale: Float,
    offset: Offset,
): Offset? {
    if (area.width <= 0 || area.height <= 0 || imageWidth <= 0 || imageHeight <= 0) return null
    if (scale <= 0f) return null
    val fit = fitRect(area, imageWidth.toFloat() / imageHeight.toFloat())
    if (fit.width <= 0f || fit.height <= 0f) return null
    val centerX = area.width / 2f
    val centerY = area.height / 2f
    val contentX = centerX + (screen.x - centerX - offset.x) / scale
    val contentY = centerY + (screen.y - centerY - offset.y) / scale
    if (contentX < fit.left || contentX > fit.right) return null
    if (contentY < fit.top || contentY > fit.bottom) return null
    return Offset(
        x = (contentX - fit.left) / fit.width * imageWidth,
        y = (contentY - fit.top) / fit.height * imageHeight,
    )
}

// ---------------------------------------------------------------------------
// 选色弹窗（复用设置页那套色环）
// ---------------------------------------------------------------------------

@Composable
private fun CanvasColorDialog(
    state: AppState,
    t: (String) -> String,
    initial: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    var color by remember { mutableStateOf(Color(initial)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("canvas.color.title")) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                // 对话框里一旦有焦点（十六进制输入框 / 色环），编辑器就别抢字母键
                modifier = Modifier.onFocusChanged { state.reportCanvasTextFocus(it.hasFocus) },
            ) {
                NaiSwatchRow(
                    colors = CanvasPalette.map { Color(it) },
                    onPick = { color = it },
                    selected = color,
                )
                NaiColorPicker(
                    color = color,
                    onColorChange = { color = it },
                    ringSize = 150.dp,
                )
                NaiHexField(value = color, onHex = { color = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(color.toArgb()) }) { Text(t("common.confirm")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("common.cancel")) }
        },
    )
}

// ---------------------------------------------------------------------------
// HSV 调整 / 调整画布大小（官方那两个对话框）
// ---------------------------------------------------------------------------

/**
 * **HSV 调整**的面板内容（官方那个对话框）。
 *
 * ⚠️ 它现在是**挂在"调节"按钮下面的弹窗**（`DropdownMenu`）的内容，不是画面正中间的对话框
 * —— 用户 2026-09-16 明确要求"在按钮下弹出窗口，不在画面中间"。
 *
 * 交互按官方：**拖动即时预览**（`onPreview` 每次都从快照重算），
 * 「应用」才算一步（可撤销），「取消」还原。
 */
@Composable
private fun CanvasHsvPanel(
    state: AppState,
    t: (String) -> String,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var value by remember { mutableFloatStateOf(0f) }
    // 每次拖动都推一次预览；AppState 那边是从快照重算，所以来回拖不会累积
    fun emit() = state.previewCanvasHsv(hue, saturation, value)

    Column(
        Modifier
            .width(280.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(t("canvas.hsv.title"), style = MaterialTheme.typography.titleSmall)
        HsvSlider(
            label = t("canvas.hsv.hue"),
            value = hue,
            valueText = "${hue.roundToInt()}°",
            range = -180f..180f,
            onChange = { hue = it; emit() },
        )
        HsvSlider(
            label = t("canvas.hsv.saturation"),
            value = saturation,
            valueText = "${saturation.roundToInt()}%",
            range = -100f..100f,
            onChange = { saturation = it; emit() },
        )
        HsvSlider(
            label = t("canvas.hsv.brightness"),
            value = value,
            valueText = "${value.roundToInt()}%",
            range = -100f..100f,
            onChange = { value = it; emit() },
        )
        Text(
            t("canvas.hsv.hint"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) { Text(t("common.cancel")) }
            TextButton(onClick = onApply) { Text(t("common.confirm")) }
        }
    }
}

@Composable
private fun HsvSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(72.dp),
                maxLines = 1,
            )
            Text(valueText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * **调整画布大小**（官方那个对话框）。
 *
 * 口径与官方一致：可以直接改宽高，也可以用「移动边缘」四边加减像素。
 * 这里**以四个边为唯一真源**：宽 = 原宽 + 左 + 右，高 = 原高 + 上 + 下；
 * 直接改宽高时，差额补到右边/下边（也就是内容锚在左上）。
 * 「裁剪至最接近的有效生成尺寸」用现成的 `fitNaiImageSize()`，并且**居中**放。
 */
@Composable
private fun CanvasResizeDialog(
    state: AppState,
    t: (String) -> String,
    onDismiss: () -> Unit,
    onApply: (Int, Int, Int, Int) -> Unit,
) {
    val originWidth = state.canvasWidth
    val originHeight = state.canvasHeight
    var left by remember { mutableIntStateOf(0) }
    var top by remember { mutableIntStateOf(0) }
    var right by remember { mutableIntStateOf(0) }
    var bottom by remember { mutableIntStateOf(0) }
    val targetWidth = originWidth + left + right
    val targetHeight = originHeight + top + bottom
    val valid = targetWidth in 64..4096 && targetHeight in 64..4096

    fun cropToValid() {
        val (fitWidth, fitHeight) = fitNaiImageSize(originWidth, originHeight)
        val deltaWidth = fitWidth - originWidth
        val deltaHeight = fitHeight - originHeight
        left = deltaWidth / 2
        top = deltaHeight / 2
        right = deltaWidth - left
        bottom = deltaHeight - top
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("canvas.resize.title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("canvas.resize.size"), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompactNumberField(
                        value = targetWidth,
                        onCommit = { width -> right = width - originWidth - left },
                        onFocusChange = { state.reportCanvasTextFocus(it) },
                        width = 72.dp,
                    )
                    Text("  ×  ", style = MaterialTheme.typography.labelLarge)
                    CompactNumberField(
                        value = targetHeight,
                        onCommit = { height -> bottom = height - originHeight - top },
                        onFocusChange = { state.reportCanvasTextFocus(it) },
                        width = 72.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${originWidth}×${originHeight}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { cropToValid() }) {
                    Text(t("canvas.resize.cropToValid"), maxLines = 1)
                }
                Text(t("canvas.resize.shiftEdges"), style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShiftField(t("canvas.resize.left"), left, state) { left = it }
                    ShiftField(t("canvas.resize.top"), top, state) { top = it }
                    ShiftField(t("canvas.resize.right"), right, state) { right = it }
                    ShiftField(t("canvas.resize.bottom"), bottom, state) { bottom = it }
                }
                Text(
                    t("canvas.resize.hint"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onApply(targetWidth, targetHeight, left, top) },
            ) { Text(t("canvas.resize.apply")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(t("common.cancel")) }
        },
    )
}

/** 「移动边缘」里的一个小格：上面是"左/上/右/下"，下面是可以输负数的输入框。 */
@Composable
private fun ShiftField(
    label: String,
    value: Int,
    state: AppState,
    onChange: (Int) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 3.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        SignedNumberField(value = value, onCommit = onChange, onFocusChange = { state.reportCanvasTextFocus(it) })
    }
}

/** 可以输负数的紧凑数字框（边缘加减要负数）。 */
@Composable
private fun SignedNumberField(
    value: Int,
    onCommit: (Int) -> Unit,
    onFocusChange: (Boolean) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    BasicTextField(
        value = text,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() || it == '-' }.take(5)
            text = filtered
            filtered.toIntOrNull()?.let(onCommit)
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.labelLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier
            .width(52.dp)
            .onFocusChanged { onFocusChange(it.isFocused) }
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

// ---------------------------------------------------------------------------
// 快捷键（电脑）
// ---------------------------------------------------------------------------

/**
 * 编辑器的键位（官方那套）：`b e g s l c r j` 切工具、`[` `]` 调笔刷、
 * `Ctrl+Z` / `Ctrl+Shift+Z`（也收 `Ctrl+Y`）撤销重做。
 *
 * 只在按下的那一下（[KeyEventType.KeyDown]）处理；返回 true = 吃掉这个键。
 * 输入框里有焦点时这些键会被输入框先消费掉，所以往数值框里打字不会被抢。
 */
private fun onCanvasShortcut(state: AppState, event: KeyEvent): Boolean {
    // 输入框拿着焦点时**一个键都不吃**：这套键位是从窗口层"抢在焦点之前"收的
    //（为了不漏掉空格的抬起），不能让字母/空格把输入框里的打字抢走。
    if (state.canvasTextFocused) return false

    // 空格：**按下和抬起都要接**（按住期间是"平移模式"），所以放在下面那个 KeyDown 早退之前。
    // 返回值恒为 true：吃掉它，免得空格去点聚焦着的按钮或者滚动页面。
    if (event.key == Key.Spacebar) {
        state.holdCanvasSpace(event.type == KeyEventType.KeyDown)
        return true
    }
    if (event.type != KeyEventType.KeyDown) return false

    // 选区：回车 = 落回（提交），Esc = 取消浮动 / 清掉选区，
    // 退格 / 删除 = 把选区里的图像清成透明（"挖透明通道"最直接的入口）
    //
    // ⚠️ **带修饰键的回车不吃**（2026-09-19 修「右 Ctrl+回车不触发执行」这条线时发现的硬伤）：
    // `Ctrl+Enter` 是外壳的「执行一次生图」（`Shortcuts` 里的 `generate` 绑定）。
    // 本函数跑在**窗口的预览阶段**（`Main.kt` 的 `onPreviewKeyEvent`），而入口认快捷键在
    // **冒泡阶段**（`onKeyEvent`）—— 这里一旦无条件吃掉回车，`Ctrl+Enter` 就永远到不了外壳：
    // 现象正是"编辑器开着时按 Ctrl+Enter 没有任何生成动作，只是把浮动选区落回了"。
    // 所以只吃**光回车**（不带 Ctrl/Alt/Shift），带修饰键的原样放行给外壳。
    if ((event.key == Key.Enter || event.key == Key.NumPadEnter) &&
        !event.isCtrlPressed && !event.isAltPressed && !event.isShiftPressed
    ) {
        state.commitCanvasSelection()
        return true
    }
    if (event.key == Key.Escape) {
        if (state.canvasSelectionFloating) {
            state.cancelCanvasSelection()
        } else {
            state.clearCanvasSelection()
        }
        return true
    }
    if (event.key == Key.Delete || event.key == Key.Backspace) {
        if (state.canvasSelection != null) {
            state.eraseCanvasSelection()
            return true
        }
        return false
    }

    if (event.isCtrlPressed && event.isShiftPressed && event.key == Key.Z) {
        state.redoCanvas()
        return true
    }
    if (event.isCtrlPressed && !event.isShiftPressed) {
        return when (event.key) {
            Key.A -> {
                state.updateCanvasSelection(SelectionShape.Rect(0f, 0f, 1f, 1f))
                true
            }
            Key.D -> {
                state.clearCanvasSelection()
                true
            }
            Key.Z -> {
                state.undoCanvas()
                true
            }

            Key.Y -> {
                state.redoCanvas()
                true
            }

            else -> false
        }
    }
    if (event.isCtrlPressed) return false

    val tool = when (event.key) {
        Key.B -> CanvasTool.DRAW
        Key.E -> CanvasTool.ERASE
        Key.G -> CanvasTool.FILL
        Key.M, Key.V -> CanvasTool.SELECT
        Key.L -> CanvasTool.LASSO
        Key.I -> CanvasTool.PICKER
        Key.R -> CanvasTool.BLUR
        Key.S -> CanvasTool.CLONE
        else -> null
    }
    if (tool != null) {
        // 已经是当前工具就跳过（免得选区的浮动块被自己这一下"落回"掉）
        if (tool.implemented && state.canvasTool != tool) state.chooseCanvasTool(tool)
        return true
    }
    return when (event.key) {
        Key.LeftBracket -> {
            state.setCanvasBrush(state.canvasBrushPixels - brushStep(state.canvasBrushPixels))
            true
        }

        Key.RightBracket -> {
            state.setCanvasBrush(state.canvasBrushPixels + brushStep(state.canvasBrushPixels))
            true
        }

        else -> false
    }
}

/** `[` / `]` 的步长：笔小的时候细调，笔大的时候粗调。 */
private fun brushStep(current: Int): Int = when {
    current < 10 -> 1
    current < 50 -> 5
    else -> 10
}
