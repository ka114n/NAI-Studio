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
import com.kallan.naistudio.ui.RefAlertDialog as AlertDialog
import com.kallan.naistudio.ui.RefButton as Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import com.kallan.naistudio.ui.RefIconButton as IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import com.kallan.naistudio.ui.RefSlider as Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.kallan.naistudio.ui.RefTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.BlurIcon
import com.kallan.naistudio.ui.BucketIcon
import com.kallan.naistudio.ui.CanvasSelectIcon
import com.kallan.naistudio.ui.CloneIcon
import com.kallan.naistudio.ui.DropperIcon
import com.kallan.naistudio.ui.EraserIcon
import com.kallan.naistudio.ui.ExpandCornersIcon
import com.kallan.naistudio.ui.LassoIcon
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

/** 每颗工具 / 动作按钮的格子：宽 43dp（放得下"油漆桶"三个字）、高 44dp（20dp 图标 + 一行字）。 */
private val CanvasBarButtonWidth = 43.dp

/** 高 44dp：20dp 图标 + 1dp 缝 + 一行 `labelSmall`（≈16dp）。 */
private val CanvasBarButtonHeight = 44.dp

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

    // ⚠️ 手机线和电脑线在这里**故意不一样**：电脑线挂的是 `LocalEditorShortcuts`
    // （窗口层收键盘：b/e/g/s… 切工具、`[` `]` 调笔刷、Ctrl+Z 撤销、空格平移）。
    // 手机线没有那套键位信箱（`ui/PageShortcuts.kt` 没搬过来），触摸设备也没有"按住空格"
    // 这个概念，所以**键位整体没搬** —— 详见同目录文件末尾的说明。
    // 但"编辑器没了就把空格状态复位"这一条照搬：属性留在 `AppState` 里，
    // 以后要把键位接上（外接键盘 / DeX）时，状态不会残留成按着。
    DisposableEffect(Unit) {
        onDispose { state.holdCanvasSpace(false) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize()) {
            CanvasTopBar(state, t, onCancel = onCancel, onCommit = onCommit)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // ⚠️ 用户 2026-09-26：「**画布编辑器的工具放到下面**」——
            //    原来顺序是 顶栏 → 工具行 → 参数行 → 画布（工具在最上面）；
            //    现在整条工具摞到**底下**：顶栏 → 画布 → 参数行 → 工具行。
            //    画布因此必须 `weight(1f)`（见 `CanvasStage` 的 `modifier` 参数）：
            //    在 Column 里它排中间，`fillMaxSize()` 会把下面那两行挤成 0 高。
            CanvasStage(state, t, modifier = Modifier.weight(1f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CanvasParamRow(state, t, onOpenColor = { colorDialogOpen = true })
            CanvasToolRow(
                state = state,
                t = t,
                onOpenResize = { resizeDialogOpen = true },
            )
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
        Button(onClick = onCommit) {
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

/**
 * 画布编辑器的工具区：**两行**，每颗图标底下都写着功能名（用户 2026-09-26）。
 *
 * ## 为什么拆成两行
 *
 * 一共 13 颗（8 个工具 + 右侧 5 个动作）。每个格子下面要挂一行字 ⇒ 一格至少 ~43dp 宽，
 * 13 × 43 = 559dp，360dp 的手机屏一行放不下（早前一行排得下是因为**只有图标**，一格 40dp）。
 * 拆成 **8 + 5** 两行，每行都够宽，字也放得下。
 *
 * ## 顺序
 *
 * 用户：「**工具放到下面**」⇒ **工具行在最底下**，动作行（调节/清除/改尺寸/撤销/重做）
 * 在它上面一行。两行底色连着（整块 `surfaceContainer`），中间不露缝。
 *
 * ⚠️ 早前那颗"悬停才显示工具名"的 `Popup` 提示**去掉了**：名字现在一直挂在图标下面，
 *    再叠一个悬停提示就是同一个词写两遍（而且手机上"悬停"本来就不成立）。
 */
@Composable
private fun CanvasToolRow(
    state: AppState,
    t: (String) -> String,
    onOpenResize: () -> Unit,
) {
    val soonLabel = t("canvas.toolSoon")
    // HSV 调整是**挂在按钮下面的弹窗**（用户 2026-09-16 要求：不要在画面正中间开对话框）
    var hsvOpen by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        // ---- 动作行（官方截图右侧那一组；放上面一行）----
        Row(
            Modifier
                .fillMaxWidth()
                .height(CanvasBarHeight)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // 那颗"调节"在官方是设置面板；我们这里全图级的调整只有 HSV，就让它直接开 HSV 调整。
            Box {
                CanvasToolButton(
                    icon = TuneIcon,
                    // 短名：原来叫「HSV 调整」，挂在 43dp 的格子里会溢出
                    label = t("canvas.hsvShort"),
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
                // 短名：原来叫「调整画布」
                label = t("canvas.resizeShort"),
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
        // ---- 工具行（官方那 8 个；**钉在最底下**）----
        Row(
            Modifier
                .fillMaxWidth()
                .height(CanvasBarHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CanvasTool.entries.forEach { tool ->
                val label = t(labelKeyOf(tool))
                CanvasToolButton(
                    icon = iconOf(tool),
                    // 吸管那颗的英文全称是「Color Picker」——挂在 43dp 格子里太长，用短名
                    label = if (tool == CanvasTool.PICKER) {
                        t("canvas.tool.pickerShort")
                    } else {
                        label
                    },
                    selected = state.canvasTool == tool,
                    dimmed = !tool.implemented,
                    onClick = {
                        if (tool.implemented) {
                            state.chooseCanvasTool(tool)
                        } else {
                            // 还没做的工具：明说一句，别让人以为点了没反应。
                            // ⚠️ 电脑线这里走的是 `LocalUiHost.current.toast(...)`；
                            // 手机线的 UiHost 那层还没搬过来（`platform/UiHost.kt` 手机线没有），
                            // 宿主提示统一走 `AppState.toastEvent`（外壳监听它弹 Toast）。
                            state.showToast(soonLabel.replace("{tool}", label))
                        }
                    },
                )
            }
        }
    }
}

/**
 * 工具 / 动作按钮：**图标 + 底下一行功能名**（用户 2026-09-26）。
 *
 * 格子 [CanvasBarButtonWidth] × [CanvasBarButtonHeight]，两边都够放"油漆桶"这种三个字
 * （`labelSmall` 三个汉字 ≈ 33dp）。名字永远是显示的 —— 早前那个"悬停才冒出来"的
 * `Popup` 提示因此删掉了（同一个词没必要写两遍，手机上也谈不上悬停）。
 */
@Composable
private fun CanvasToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 禁用态：M3 的规矩是不透明度压到 0.38（图标和字一起，别只压图标）
    val effective = if (dimmed) tint.copy(alpha = 0.38f) else tint
    Column(
        Modifier
            .size(width = CanvasBarButtonWidth, height = CanvasBarButtonHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = effective,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = effective,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
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
private fun CanvasStage(state: AppState, t: (String) -> String, modifier: Modifier = Modifier) {
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

    val density = LocalDensity.current
    val aspect = if (imageHeight > 0) imageWidth.toFloat() / imageHeight.toFloat() else 0f
    val fit = fitRect(area, aspect)

    // ⚠️ 电脑线这里还有一段 `uiHost.panCursor(grabbing = panning)` 的鼠标指针（SAI 那两只手）。
    // 手机线没有指针这个概念（`UiHost.panCursor` 在手机实现里本来就返回空 Modifier），
    // 所以这一段没搬；`panning` 还留着 —— 画笔光标圈要靠它判断"正在平移，别画圈"。

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
        modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .onSizeChanged { area = it }
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

                        // ⚠️ 手机线唯一的**输入层**改动（电脑线原样是
                        // `if (change.type != PointerType.Mouse) continue`）：
                        // 原来"不是鼠标就跳过"，在手机上手写/触屏点下去等于什么都没发生 ——
                        // 这套工具是给触摸设备用的，所以触摸 / 触控笔也放行。
                        // （滚轮缩放、中键拖动、空格平移、悬停光标圈仍然是鼠标专属：
                        //   它们在触摸下压根不会触发，行为与电脑线一致，只是用不上。）
                        if (change.type != PointerType.Mouse &&
                            change.type != PointerType.Touch &&
                            change.type != PointerType.Stylus
                        ) {
                            continue
                        }

                        // 指针位置（画笔光标圈要用）
                        hover = change.position

                        // 3) 左键 = 涂抹 / 吸管 / 填充 / Alt 定图章源点
                        //    触摸 / 触控笔没有"按键"这个概念（`buttons` 在触摸事件里是空的），
                        //    所以那两种输入用"指针按着"当左键（`change.pressed`）。
                        val primary = if (change.type == PointerType.Mouse) {
                            event.buttons.isPrimaryPressed
                        } else {
                            change.pressed
                        }
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
                            // ⚠️ 手机线加的收尾：触摸/触控笔抬手之后**不会再有 Enter/Exit 事件**
                            //（那是鼠标悬停才有），`hover` 会一直停在最后一个笔迹点上 ——
                            // 画布上就留着一个"笔刷大小圈"擦不掉。这里抬手时把它清掉。
                            // 鼠标保持原样（电脑线那套悬停语义不动）。
                            if (change.type != PointerType.Mouse) hover = null
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
 * 口径照官方：可以直接改宽高，也可以用「移动边缘」四边加减像素。
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
// ⚠️ 手机线**没有搬**的一段：快捷键（电脑）
// ---------------------------------------------------------------------------
//
// 电脑线这个文件末尾有一整块 `onCanvasShortcut` / `brushStep`：
//  `b e g s l c r j` 切工具、`[` `]` 调笔刷、`Ctrl+Z` / `Ctrl+Shift+Z`（也收 `Ctrl+Y`）撤销重做、
//  空格按住 = 平移模式、回车落回选区 / Esc 取消 / 退格删除选区内像素。
//
// 为什么没搬：那套键位**不是本文件自己收键盘**，而是挂在 `ui/PageShortcuts.kt` 的
// `EditorShortcuts`（`LocalEditorShortcuts`）信箱上 —— 那个文件是电脑线新加的窗口层设施，
// 手机线没有（手机端的返回键/键盘入口都在 `StudioShell` 里另一套写法）。
// 只搬 `onCanvasShortcut` 会得到一段**永远不被调用**的死代码，所以整块去掉。
//
// 手机上的对应能力并没有全丢：
//  · 切工具 / 撤销 / 重做 / 清除 / 调整画布 —— 都在上面那条工具行上有按钮；
//  · 落回选区 —— 点框外、或换一个工具都会自动落回（见 `AppState.commitCanvasSelection` 那两条调用路径）；
//  · 平移模式（空格）—— 触摸设备没有"按住空格"，本来就用不上；
//  · 键盘：外接键盘 / DeX 下如果以后要接，把手机线的 `EditorShortcuts` 补上再把这函数搬回来即可。
//
// `AppState` 那边的 `canvasSpacePan` / `holdCanvasSpace` / `canvasTextFocused` /
// `reportCanvasTextFocus` **照搬了**（语义照电脑线）：前两个现在没人触发（状态恒为 false），
// 后两个还在用 —— 输入框拿到焦点要报上去，以后键位接上就能立刻按电脑线那套规则工作。
