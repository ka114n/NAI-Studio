package com.kallan.naistudio.screens

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.models.EditRect
import com.kallan.naistudio.models.capInfiniteFrameArea
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.CANVAS_CMD_RESET_PLACEMENT
import com.kallan.naistudio.ui.CANVAS_CMD_ZOOM_IN
import com.kallan.naistudio.ui.CANVAS_CMD_ZOOM_OUT
import com.kallan.naistudio.ui.LocalCanvasViewCommands
import kotlin.math.abs
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt

/** 无限画布的缩放范围（与生图页同一档 ✓）。 */
private const val INFINITE_MIN_SCALE = 0.2f
private const val INFINITE_MAX_SCALE = 6f

/** 滚轮每一格缩放多少（与生图页 `PREVIEW_WHEEL_ZOOM_STEP` 同一个手感 ✓）。 */
private const val INFINITE_WHEEL_STEP = 0.15f

/** 框的最小边长（**文档像素** ✓）——再小就没法生成（inpaint 有下限 ✓，见 `MaskCodec`）。 */
private const val INFINITE_FRAME_MIN = 64f


/** 这一下在框上是什么意思（拖动那串手势据此分派 ✓）。 */
private enum class FrameDrag { CREATE, MOVE }

/** Fixed-size movable crop frame. The request and paste-back use exactly this rectangle. */
@Composable
fun InfiniteCanvasArea(
    state: AppState,
    t: (String) -> String,
    modifier: Modifier = Modifier,
) {
    val canvas = state.infiniteCanvas
    val image = state.infiniteCanvasImage()
    val density = LocalDensity.current

    var scale by remember { mutableFloatStateOf(state.canvasViewScale) }
    var offsetX by remember { mutableFloatStateOf(state.canvasViewX) }
    var offsetY by remember { mutableFloatStateOf(state.canvasViewY) }
    val viewCommands = LocalCanvasViewCommands.current
    DisposableEffect(viewCommands) {
        viewCommands.register { id ->
            when (id) {
                CANVAS_CMD_RESET_PLACEMENT -> {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                    true
                }
                CANVAS_CMD_ZOOM_IN, CANVAS_CMD_ZOOM_OUT -> {
                    scale = (scale * if (id == CANVAS_CMD_ZOOM_IN) 1.2f else 1f / 1.2f)
                        .coerceIn(INFINITE_MIN_SCALE, INFINITE_MAX_SCALE)
                    true
                }
                else -> false
            }
        }
        onDispose { viewCommands.register(null) }
    }
    // ⚠️ `drop(1)` 丢掉挂载时那一发初值 ✓（否则一进就把共用值写成"挂载时的初值" ✗）。
    LaunchedEffect(state) {
        snapshotFlow { Triple(scale, offsetX, offsetY) }
            .drop(1)
            .collect { (s, x, y) -> state.holdCanvasView(s, x, y) }
    }

    var paramsExpanded by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        val originX = (canvas?.originX ?: 0).toFloat()
        val originY = (canvas?.originY ?: 0).toFloat()
        val contentW = (canvas?.width ?: 0).toFloat()
        val contentH = (canvas?.height ?: 0).toFloat()
        val fit = if (contentW > 0f && contentH > 0f) {
            minOf(viewW / contentW, viewH / contentH)
        } else {
            1f
        }
        val drawW = contentW * fit * scale
        val drawH = contentH * fit * scale

        // 手势里要用的「视口 → 文档」换算：**现读** ✓（`pointerInput` 协程不随重组重启 ✗ 不能捕获局部值）
        val transform = ViewTransform(
            fit = fit,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            viewWidth = viewW,
            viewHeight = viewH,
            contentWidth = contentW,
            contentHeight = contentH,
            originX = originX,
            originY = originY,
        )
        val view = rememberUpdatedState(transform)

        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                // ① 滚轮 = 以光标为锚点缩放（公式与生图页一致 ✓）
                .pointerInput(canvas?.width, canvas?.height) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val scroll = change.scrollDelta.y
                            if (scroll == 0f) continue
                            val next = (scale * (1f - scroll * INFINITE_WHEEL_STEP))
                                .coerceIn(INFINITE_MIN_SCALE, INFINITE_MAX_SCALE)
                            if (next != scale) {
                                val anchorX = change.position.x - viewW / 2f
                                val anchorY = change.position.y - viewH / 2f
                                val ratio = next / scale
                                offsetX = anchorX - (anchorX - offsetX) * ratio
                                offsetY = anchorY - (anchorY - offsetY) * ratio
                                scale = next
                            }
                            change.consume()
                        }
                    }
                }
                // ② 左键 = **动框**（新建 / 拖动 / 四角缩放），空格 或 中键 = **平移** ✓ —— 合成同一个手势节点 ✓。
                // ⛔ **框优先** ✗ 不许省：按在框上 / 角上时不管空格按没按都是动框 ✓
                //    （`canvasSpacePan` 卡在 true 时"框永远拖不动"会原样复发 ✗）。
                .pointerInput(canvas?.width, canvas?.height) {
                    val hit = 12.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val mouse = down.type == PointerType.Mouse
                        val v = view.value
                        val frame = state.infiniteFrame
                        val inside = frame != null && insideFrame(frame, v.toDoc(down.position))
                        val panning =
                            (state.canvasSpacePan || (mouse && currentEvent.buttons.isTertiaryPressed))
                        if (panning) {
                            var last = down.position
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                offsetX += change.position.x - last.x
                                offsetY += change.position.y - last.y
                                last = change.position
                                change.consume()
                            }
                            return@awaitEachGesture
                        }
                        val mode = when {
                            frame == null -> FrameDrag.CREATE
                            inside -> FrameDrag.MOVE
                            else -> FrameDrag.CREATE
                        }
                        val pressDoc = v.toDoc(down.position)
                        if (mode == FrameDrag.CREATE) {
                            val size = com.kallan.naistudio.models.InfiniteFrameSizes.nearest(frame?.w ?: 1024, frame?.h ?: 1024)
                            state.updateInfiniteFrame(EditRect((pressDoc.x - size.width / 2f).roundToInt(),
                                (pressDoc.y - size.height / 2f).roundToInt(), size.width, size.height))
                        }
                        var lastDoc = pressDoc
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val now = view.value.toDoc(change.position)
                            when (mode) {
                                FrameDrag.CREATE -> {
                                    val size = com.kallan.naistudio.models.InfiniteFrameSizes.nearest(
                                        state.infiniteFrame?.w ?: state.settings.infiniteFrameW.takeIf { it > 0 } ?: 1024,
                                        state.infiniteFrame?.h ?: state.settings.infiniteFrameH.takeIf { it > 0 } ?: 1024)
                                    state.updateInfiniteFrame(EditRect((now.x - size.width / 2f).roundToInt(),
                                        (now.y - size.height / 2f).roundToInt(), size.width, size.height))
                                }

                                FrameDrag.MOVE -> {
                                    val f = state.infiniteFrame ?: break
                                    val dx = (now.x - lastDoc.x).roundToInt()
                                    val dy = (now.y - lastDoc.y).roundToInt()
                                    if (dx != 0 || dy != 0) {
                                        state.updateInfiniteFrame(f.copy(x = f.x + dx, y = f.y + dy))
                                    }
                                }

                            }
                            lastDoc = now
                            change.consume()
                        }
                        // 太小的框留着没意义（生成有下限 ✓）⇒ 收掉并**如实说一句** ✓
                        val made = state.infiniteFrame
                        if (mode == FrameDrag.CREATE && made != null &&
                            (made.w < INFINITE_FRAME_MIN || made.h < INFINITE_FRAME_MIN)
                        ) {
                            state.updateInfiniteFrame(null)
                            state.showToast(t("infinite.frameTooSmall"))
                        }
                        // 手势结束 ⇒ **落一次盘** ✓
                        state.commitInfiniteCanvas()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (image != null && drawW > 0f && drawH > 0f) {
                Box(
                    Modifier
                        .graphicsLayer {
                            translationX = offsetX
                            translationY = offsetY
                        }
                        .requiredSize(
                            width = with(density) { drawW.toDp() },
                            height = with(density) { drawH.toDp() },
                        ),
                ) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        filterQuality = FilterQuality.Medium,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                val frame = state.infiniteFrame
                if (frame != null) {
                    FrameOverlay(
                        frame = transform.screenRect(frame),
                        accent = MaterialTheme.colorScheme.primary,
                    )
                    // 左上角「宽 × 高」（文档像素 ✓）；框贴着视口顶时挪进框里 ✓
                    val fr = transform.screenRect(frame)
                    val labelGap = with(density) { 24.dp.toPx() }
                    val labelY = if (fr.top > labelGap) fr.top - labelGap else fr.top + with(density) { 6.dp.toPx() }
                    Text(
                        "${t("infinite.redrawArea")} ${frame.w} × ${frame.h}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset { IntOffset(fr.left.roundToInt(), labelY.roundToInt()) }
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            } else {
                Text(
                    t("infinite.empty"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ⚠️ 这一行是手势层的**兄弟**而不是孩子 ✓：挂在里面时，拖滑条 / 点按钮会被外层
        //    "动框"那条手势（`requireUnconsumed = false`）一起收到 ⇒ 拖滑条变成画新框 ✗。
        // ⚠️ 提示那一行和真正发出去的请求用的是**同一个函数**（`AppState.infiniteFramePlan` ✓）。
        val planText = state.infinitePlanText()
        val hint = when {
            state.infiniteRunning -> t("infinite.generating")
            planText != null -> planText
            else -> t("infinite.runNeedFrame")
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CanvasChip(
                text = t("infinite.undo"),
                enabled = state.infiniteCanUndo && !state.infiniteRunning,
                onClick = { state.undoInfiniteCanvas() },
            )
            CanvasChip(
                text = t("infinite.redo"),
                enabled = state.infiniteCanRedo && !state.infiniteRunning,
                onClick = { state.redoInfiniteCanvas() },
            )
            state.infiniteFrame?.let { frame ->
                FrameSizeChip(
                    state = state,
                    frame = frame,
                    label = t("infinite.frameSize"),
                    background = chipBackground(true),
                    textColor = MaterialTheme.colorScheme.onSurface,
                )
            }

            CanvasChip(text = hint)
        }
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
                .width(380.dp).heightIn(max = maxHeight * 0.7f)
                .verticalScroll(rememberScrollState()),
        ) {
            InpaintParamsTrigger(t, paramsExpanded, { paramsExpanded = !paramsExpanded })
            if (paramsExpanded) InpaintParamsPanel(state, t, infinite = true)
        }
    }
}

/**
 * 框的覆盖层（**视口坐标** ✓）：压暗请求范围以外 → 上下文圈 → 框 → 四角角标 + 把手 ✓。
 * 没有指针处理 ✓ —— 不挡下面那层手势 ✓。
 */
@Composable
private fun FrameOverlay(frame: Rect, accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val outer = frame
        val dim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addRect(outer)
        }
        drawPath(dim, Color.Black.copy(alpha = 0.28f))

        drawRect(accent.copy(alpha = 0.18f), frame.topLeft, frame.size)
        // 框：先一道暗描边垫底（浅色图上也看得清 ✓），再一道主色细线
        drawRect(Color.Black.copy(alpha = 0.35f), frame.topLeft, frame.size, style = Stroke(3.dp.toPx()))
        drawRect(accent, frame.topLeft, frame.size, style = Stroke(1.5.dp.toPx()))

    }
}

/**
 * 画布底部那一排小胶囊（提示 / 撤销 / 重做 ✓）。
 *
 * ⚠️ **不许拿焦点** ✗：桌面端「按住空格 = 平移画布」走的是键事件 ✓，拿着焦点的按钮会被空格"激活"✗。
 */
@Composable
private fun CanvasChip(
    text: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val clickable = onClick != null && enabled
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(chipBackground(enabled))
            .then(if (clickable) Modifier.clickable { onClick?.invoke() } else Modifier)
            .focusProperties { canFocus = false }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}




/** Select a fixed free-size preset. */
@Composable
private fun FrameSizeChip(
    state: AppState,
    frame: EditRect,
    label: String,
    background: Color,
    textColor: Color,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CanvasChip(text = "$label ${frame.w} × ${frame.h} ▾",
            enabled = !state.infiniteRunning, onClick = { expanded = true })
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            com.kallan.naistudio.models.InfiniteFrameSizes.presets.forEach { size ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("${size.width} × ${size.height}") },
                    onClick = {
                        state.setInfiniteFrameSize(size.width, size.height, true)
                        expanded = false
                    },
                )
            }
        }
    }
}



@Composable
private fun chipBackground(enabled: Boolean): Color =
    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 0.92f else 0.6f)

/** 这一点在不在框里（**文档坐标** ✓）。 */
private fun insideFrame(frame: EditRect, point: Offset): Boolean =
    point.x >= frame.x && point.x <= frame.x + frame.w &&
        point.y >= frame.y && point.y <= frame.y + frame.h

/**
 * **视口坐标 ↔ 文档坐标**（无限画布的框手势与覆盖层共用 ✓）。
 *
 * 内容层在视口里是**居中 + `offsetX/offsetY` 平移**过的 ✓ ⇒
 *  1. 内容层左上角 = `((视口 − 内容层像素尺寸) / 2) + 平移` ✓；
 *  2. 相对内容层的屏幕位移 ÷ `k`（`k = fit × scale`）= 画布像素 ✓；
 *  3. 再加**原点**（`pixel = doc − origin` 的反向 ✓）= 文档坐标 ✓。
 *
 * ⚠️ 手势里**每一帧现读** ✗ 不能在手势开始时捕获成局部值 ✓（`pointerInput` 协程不随重组重启 ✓）。
 */
private class ViewTransform(
    val fit: Float,
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val viewWidth: Float,
    val viewHeight: Float,
    val contentWidth: Float,
    val contentHeight: Float,
    val originX: Float,
    val originY: Float,
) {
    /** 屏幕 px / 文档像素 ✓（**不许是 0** ✗ —— 除零会算出 Infinity 的框 ✓）。 */
    val k: Float get() = (fit * scale).coerceAtLeast(0.0001f)

    private val contentLeft: Float get() = (viewWidth - contentWidth * fit * scale) / 2f + offsetX
    private val contentTop: Float get() = (viewHeight - contentHeight * fit * scale) / 2f + offsetY

    fun toDoc(position: Offset): Offset = Offset(
        x = (position.x - contentLeft) / k + originX,
        y = (position.y - contentTop) / k + originY,
    )

    fun screenRect(r: Rect): Rect = Rect(
        left = contentLeft + (r.left - originX) * k,
        top = contentTop + (r.top - originY) * k,
        right = contentLeft + (r.right - originX) * k,
        bottom = contentTop + (r.bottom - originY) * k,
    )

    fun screenRect(frame: EditRect): Rect = screenRect(
        Rect(
            frame.x.toFloat(),
            frame.y.toFloat(),
            (frame.x + frame.w).toFloat(),
            (frame.y + frame.h).toFloat(),
        ),
    )

    /**
     * 按在哪个角上：0 左上 / 1 右上 / 2 右下 / 3 左下，都不是 ⇒ -1 ✓
     * （命中半径按**屏幕 px** 算 ✓ —— 画布缩得再小也点得着 ✓）。
     */
    fun cornerAt(frame: EditRect, position: Offset, hit: Float): Int {
        val r = screenRect(frame)
        val corners = listOf(r.topLeft, r.topRight, r.bottomRight, r.bottomLeft)
        return corners.indexOfFirst { abs(position.x - it.x) <= hit && abs(position.y - it.y) <= hit }
    }
}
