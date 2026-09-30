package com.kallan.naistudio.screens

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

/** 上下文滑条的上限（`FocusedInpaint.plan` 内部也夹在 0..512 ✓）。 */
private const val INFINITE_CONTEXT_MAX = 512f

/** 这一下在框上是什么意思（拖动那串手势据此分派 ✓）。 */
private enum class FrameDrag { CREATE, MOVE, RESIZE }

/**
 * **无限画布本体**（第 ㊿k 批 ✓，方案 `docs/69` ✓）。
 *
 *  1. 在画布上**拖一个框** ✓（也可以拖到**画布外面** ✓ —— 那就是"往那边拓展"的意思 ✓）；
 *  2. 框可以**整块拖动** ✓（按在框里拖 ✓）；**四个角**都能调大小 ✓（2026-09-29 起，按住的角跟手、对角钉住 ✓）；
 *  3. 生成时把文档**扩到装得下这个框** ✓，没画到的地方保持透明 ✓。
 *
 * ## 框的画法（用户 2026-09-29「无限画布的框优化」✓）
 *
 * 整块覆盖层画在**视口坐标**里（不跟内容层一起 `graphicsLayer` 平移 ✓ —— 线宽不随缩放变粗变细 ✓）：
 *  · 请求范围（框 + 上下文圈）以外**压暗** ✓；
 *  · 上下文圈（只给模型当参考、不重绘 ✓）淡色填充 + 虚线外框 ✓；
 *  · 框本身：细实线 + 四角 L 形粗角标 + 四个方形把手 ✓；左上角挂一块「宽 × 高」✓。
 *
 * ## 上下文滑条
 *
 * 底部那一排多了「上下文」滑条 ✓（`AppSettings.infiniteContext` ✓）：拖动时覆盖层与"将请求 …"**现算** ✓，
 * 松手才写一次盘 ✓（每帧写盘 ✗）。
 *
 * ⚠️ **框用文档坐标**（`AppState.infiniteFrame` ✓）：换成画布像素就会被画布边界夹住 ✗。
 */
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

    // 上下文滑条的"拖动中"值 ✓：设置变了（别处改 / 松手写回）就跟着重置 ✓
    val savedContext = state.settings.infiniteContext
    var liveContext by remember(savedContext) { mutableFloatStateOf(savedContext.toFloat()) }
    val contextPx = liveContext.roundToInt()

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
                        val corner = if (frame != null) v.cornerAt(frame, down.position, hit) else -1
                        val onHandle = corner >= 0
                        val inside = frame != null && insideFrame(frame, v.toDoc(down.position))
                        val panning = !(onHandle || inside) &&
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
                            onHandle -> FrameDrag.RESIZE
                            inside -> FrameDrag.MOVE
                            else -> FrameDrag.CREATE
                        }
                        // 缩放：**对角钉住** ✓（手势开始时算一次 ✓），按住的那个角跟手 ✓
                        val anchor = if (frame != null && onHandle) {
                            val l = frame.x.toFloat()
                            val tp = frame.y.toFloat()
                            val r = (frame.x + frame.w).toFloat()
                            val b = (frame.y + frame.h).toFloat()
                            when (corner) {
                                0 -> Offset(r, b)
                                1 -> Offset(l, b)
                                2 -> Offset(l, tp)
                                else -> Offset(r, tp)
                            }
                        } else {
                            Offset.Zero
                        }
                        val dragRight = corner == 1 || corner == 2
                        val dragDown = corner == 2 || corner == 3
                        // ⛔ 新建时"角点"钉在按下那一点（`pressDoc` 全程不动 ✓），拖动按增量走（`lastDoc` ✓）
                        val pressDoc = v.toDoc(down.position)
                        var lastDoc = pressDoc
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val now = view.value.toDoc(change.position)
                            when (mode) {
                                FrameDrag.CREATE -> {
                                    val left = minOf(pressDoc.x, now.x)
                                    val top = minOf(pressDoc.y, now.y)
                                    val right = maxOf(pressDoc.x, now.x)
                                    val bottom = maxOf(pressDoc.y, now.y)
                                    state.updateInfiniteFrame(
                                        EditRect(
                                            x = left.roundToInt(),
                                            y = top.roundToInt(),
                                            w = (right - left).roundToInt().coerceAtLeast(1),
                                            h = (bottom - top).roundToInt().coerceAtLeast(1),
                                        ).let {
                                            // 总像素 ≤ 1024×1024（钉住按下那一点 ✓）
                                            capInfiniteFrameArea(it, keepRight = now.x < pressDoc.x, keepBottom = now.y < pressDoc.y)
                                        }.let { state.infiniteCanvas?.clampFrame(it) ?: it },
                                    )
                                }

                                FrameDrag.MOVE -> {
                                    val f = state.infiniteFrame ?: break
                                    val dx = (now.x - lastDoc.x).roundToInt()
                                    val dy = (now.y - lastDoc.y).roundToInt()
                                    if (dx != 0 || dy != 0) {
                                        state.updateInfiniteFrame(f.copy(x = f.x + dx, y = f.y + dy))
                                    }
                                }

                                FrameDrag.RESIZE -> {
                                    // 拖过对角不翻面 ✓，并且不小于最小边长 ✓
                                    val nx = if (dragRight) {
                                        maxOf(now.x, anchor.x + INFINITE_FRAME_MIN)
                                    } else {
                                        minOf(now.x, anchor.x - INFINITE_FRAME_MIN)
                                    }
                                    val ny = if (dragDown) {
                                        maxOf(now.y, anchor.y + INFINITE_FRAME_MIN)
                                    } else {
                                        minOf(now.y, anchor.y - INFINITE_FRAME_MIN)
                                    }
                                    state.updateInfiniteFrame(
                                        EditRect(
                                            x = minOf(anchor.x, nx).roundToInt(),
                                            y = minOf(anchor.y, ny).roundToInt(),
                                            w = abs(nx - anchor.x).roundToInt(),
                                            h = abs(ny - anchor.y).roundToInt(),
                                        ).let {
                                            // 总像素 ≤ 1024×1024（钉住对角 ✓）
                                            capInfiniteFrameArea(it, keepRight = !dragRight, keepBottom = !dragDown)
                                        }.let { state.infiniteCanvas?.clampFrame(it) ?: it },
                                    )
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
                    // 请求范围（框 + 上下文圈）：与真正发出去的请求**同一个函数**算 ✓
                    val cropDoc = state.infiniteFramePlan(contextPx)?.let { (grown, plan) ->
                        val x = (plan.cropX + grown.originX).toFloat()
                        val y = (plan.cropY + grown.originY).toFloat()
                        Rect(x, y, x + plan.cropW, y + plan.cropH)
                    }
                    FrameOverlay(
                        frame = transform.screenRect(frame),
                        crop = cropDoc?.let { transform.screenRect(it) },
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

        // ---- 底部那一行：**撤销 / 重做 / 上下文滑条** + "这一次会请求多大" ----
        // ⚠️ 这一行是手势层的**兄弟**而不是孩子 ✓：挂在里面时，拖滑条 / 点按钮会被外层
        //    "动框"那条手势（`requireUnconsumed = false`）一起收到 ⇒ 拖滑条变成画新框 ✗。
        // ⚠️ 提示那一行和真正发出去的请求用的是**同一个函数**（`AppState.infiniteFramePlan` ✓）。
        val planText = state.infinitePlanText(contextPx)
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
            ContextSliderChip(
                label = t("infinite.context"),
                value = liveContext,
                enabled = !state.infiniteRunning,
                onValue = { liveContext = it },
                onDone = {
                    val next = liveContext.roundToInt().coerceIn(0, INFINITE_CONTEXT_MAX.toInt())
                    if (next != state.settings.infiniteContext) {
                        state.setSettings { it.copy(infiniteContext = next) }
                    }
                },
            )
            CanvasChip(text = hint)
        }
    }
}

/**
 * 框的覆盖层（**视口坐标** ✓）：压暗请求范围以外 → 上下文圈 → 框 → 四角角标 + 把手 ✓。
 * 没有指针处理 ✓ —— 不挡下面那层手势 ✓。
 */
@Composable
private fun FrameOverlay(frame: Rect, crop: Rect?, accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val outer = crop ?: frame
        val dim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(Offset.Zero, size))
            addRect(outer)
        }
        drawPath(dim, Color.Black.copy(alpha = 0.28f))
        if (crop != null) {
            // 用户 2026-09-29：「半透明色是上下文 + 拉框，拉框是重绘区域」⇒ 整块请求范围一层淡色 ✓
            drawRect(accent.copy(alpha = 0.10f), crop.topLeft, crop.size)
            drawRect(
                color = accent.copy(alpha = 0.75f),
                topLeft = crop.topLeft,
                size = crop.size,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                ),
            )
        }
        // 框 = **重绘区域**：再叠一层更实的主色 ✓（和只作参考的上下文圈一眼分开 ✓）
        drawRect(accent.copy(alpha = 0.18f), frame.topLeft, frame.size)
        // 框：先一道暗描边垫底（浅色图上也看得清 ✓），再一道主色细线
        drawRect(Color.Black.copy(alpha = 0.35f), frame.topLeft, frame.size, style = Stroke(3.dp.toPx()))
        drawRect(accent, frame.topLeft, frame.size, style = Stroke(1.5.dp.toPx()))
        val corners = listOf(frame.topLeft, frame.topRight, frame.bottomRight, frame.bottomLeft)
        val arm = minOf(18.dp.toPx(), frame.width / 3f, frame.height / 3f)
        val bracket = 3.dp.toPx()
        val handle = 9.dp.toPx()
        corners.forEachIndexed { i, c ->
            val sx = if (i == 1 || i == 2) -1f else 1f
            val sy = if (i == 2 || i == 3) -1f else 1f
            drawLine(accent, c, Offset(c.x + sx * arm, c.y), bracket, cap = StrokeCap.Square)
            drawLine(accent, c, Offset(c.x, c.y + sy * arm), bracket, cap = StrokeCap.Square)
            val tl = Offset(c.x - handle / 2f, c.y - handle / 2f)
            drawRect(Color.White, tl, Size(handle, handle))
            drawRect(accent, tl, Size(handle, handle), style = Stroke(1.5.dp.toPx()))
        }
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

/** 「上下文」滑条胶囊：框外留多少像素给模型参考（0..512 px ✓）。 */
@Composable
private fun ContextSliderChip(
    label: String,
    value: Float,
    enabled: Boolean,
    onValue: (Float) -> Unit,
    onDone: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(chipBackground(true))
            .focusProperties { canFocus = false }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Slider(
            value = value,
            onValueChange = onValue,
            onValueChangeFinished = onDone,
            valueRange = 0f..INFINITE_CONTEXT_MAX,
            enabled = enabled,
            modifier = Modifier
                .width(140.dp)
                .height(30.dp)
                .focusProperties { canFocus = false },
        )
        Text(
            "${value.roundToInt()} px",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp),
        )
    }
}

/**
 * 「框 宽 × 高」输入胶囊（用户 2026-09-29：「可自己设置拉框的长宽」✓）。
 * 回车 / 离开输入框时才提交 ✓（边打边改会把"1"当成 64 立刻套上去 ✗）；以框中心为准、总像素 ≤ 1024×1024 ✓。
 */
@Composable
private fun FrameSizeChip(
    state: AppState,
    frame: EditRect,
    label: String,
    background: Color,
    textColor: Color,
) {
    var wText by remember(frame.w) { mutableStateOf(frame.w.toString()) }
    var hText by remember(frame.h) { mutableStateOf(frame.h.toString()) }
    fun commit(keepWidth: Boolean) {
        val w = wText.toIntOrNull()
        val h = hText.toIntOrNull()
        if (w != null && h != null && (w != frame.w || h != frame.h)) {
            state.setInfiniteFrameSize(w, h, keepWidth)
        }
        val now = state.infiniteFrame ?: frame
        wText = now.w.toString()
        hText = now.h.toString()
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = textColor)
        FrameSizeField(wText, { wText = it }, { commit(true) }, textColor)
        Text("×", style = MaterialTheme.typography.labelMedium, color = textColor)
        FrameSizeField(hText, { hText = it }, { commit(false) }, textColor)
    }
}

@Composable
private fun FrameSizeField(
    value: String,
    onValue: (String) -> Unit,
    onCommit: () -> Unit,
    textColor: Color,
) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = { onValue(it.filter(Char::isDigit).take(4)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.labelMedium.copy(color = textColor, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(textColor),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier
            .width(46.dp)
            .onFocusChanged {
                if (focused && !it.isFocused) onCommit()
                focused = it.isFocused
            }
            .onPreviewKeyEvent {
                if (it.key == Key.Enter || it.key == Key.NumPadEnter) {
                    if (it.type == KeyEventType.KeyUp) focus.clearFocus()
                    true
                } else {
                    false
                }
            }
            .clip(RoundedCornerShape(4.dp))
            .background(textColor.copy(alpha = 0.10f))
            .padding(horizontal = 4.dp, vertical = 3.dp),
    )
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
