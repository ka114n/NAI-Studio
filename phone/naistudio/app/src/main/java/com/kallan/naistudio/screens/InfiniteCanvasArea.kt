package com.kallan.naistudio.screens

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
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
import kotlin.math.abs
import kotlin.math.roundToInt

/** 无限画布的缩放范围（与生图页同一档 ✓）。 */
private const val INFINITE_MIN_SCALE = 0.2f
private const val INFINITE_MAX_SCALE = 6f

/** 滚轮每一格缩放多少（与生图页 `PREVIEW_WHEEL_ZOOM_STEP` 同一个手感 ✓）。 */
private const val INFINITE_WHEEL_STEP = 0.15f

/** 框的最小边长（**文档像素** ✓）——再小就没法生成（inpaint 有下限 ✓，见 `MaskCodec`）。 */
private const val INFINITE_FRAME_MIN = 64f
private const val INFINITE_CONTEXT_MAX = 512f


/** 这一下在框上是什么意思（拖动那串手势据此分派 ✓）。 */
private enum class FrameDrag { CREATE, MOVE, PAN }

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

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
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

        // 手势里要用的「视口 → 文档」换算：**现读** ✓（每次重组刷新一份 ✓）——
        // ⚠️ 不能在 `pointerInput` 里捕获成局部值 ✗：那个协程**不会因为重组而重启** ✓，
        //    捕获进去就永远停在旧值（本仓库在这条上踩过好几次 ✓，见 [ViewTransform] 的注释 ✓）。
        val view = rememberUpdatedState(
            ViewTransform(
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
            ),
        )

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
                // ② 用户 2026-09-29（手机）：「默认滑动是拖动或放大缩小画布，双击画布拉框」✓
                //    双击（第二下按住拖）= 拉新框 ✓；双击不拖 = 以那一点为中心放一个框（沿用当前框尺寸 ✓）。
                //    仍是**同一个手势节点** ✓（分开挂会出"看得见、抓不着"✗，见旧注释的教训）。
                .pointerInput(canvas?.width, canvas?.height) {
                    var lastTapUp = 0L
                    var lastTapPos = Offset.Zero
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val v = view.value
                        val frame = state.infiniteFrame
                        val slop = viewConfiguration.touchSlop
                        val doubleTap = down.uptimeMillis - lastTapUp <= viewConfiguration.doubleTapTimeoutMillis &&
                            (down.position - lastTapPos).getDistance() <= slop * 4f
                        lastTapUp = 0L
                        var mode = when {
                            doubleTap -> FrameDrag.CREATE
                            frame != null && insideFrame(frame, v.toDoc(down.position)) -> FrameDrag.MOVE
                            else -> FrameDrag.PAN
                        }
                        val pressDoc = v.toDoc(down.position)
                        var lastDoc = pressDoc
                        var lastPos = down.position
                        var pointerId = down.id
                        var moved = false
                        var pinching = false
                        var lastSpan = 0f
                        var lastCenter = Offset.Zero
                        var upTime = down.uptimeMillis
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                upTime = event.changes.firstOrNull()?.uptimeMillis ?: upTime
                                break
                            }
                            if (pressed.size >= 2) {
                                // 双指 = 以两指中点为锚缩放 + 跟着中点平移 ✓（公式与滚轮一致 ✓）
                                val p0 = pressed[0].position
                                val p1 = pressed[1].position
                                val center = Offset((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
                                val span = (p0 - p1).getDistance()
                                if (pinching && lastSpan > 0f) {
                                    val next = (scale * span / lastSpan).coerceIn(INFINITE_MIN_SCALE, INFINITE_MAX_SCALE)
                                    val ratio = next / scale
                                    val ax = center.x - viewW / 2f
                                    val ay = center.y - viewH / 2f
                                    val px = offsetX + (center.x - lastCenter.x)
                                    val py = offsetY + (center.y - lastCenter.y)
                                    offsetX = ax - (ax - px) * ratio
                                    offsetY = ay - (ay - py) * ratio
                                    scale = next
                                }
                                pinching = true
                                moved = true
                                mode = FrameDrag.PAN
                                lastSpan = span
                                lastCenter = center
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            pinching = false
                            val change = pressed.firstOrNull { it.id == pointerId }
                            if (change == null) {
                                // 松开一根手指后换了一根在动 ⇒ 从它当前位置接着平移 ✓（不跳）
                                val c = pressed[0]
                                pointerId = c.id
                                lastPos = c.position
                                lastDoc = view.value.toDoc(c.position)
                                continue
                            }
                            if (!moved && (change.position - down.position).getDistance() < slop) continue
                            moved = true
                            val now = view.value.toDoc(change.position)
                            when (mode) {
                                FrameDrag.PAN -> {
                                    offsetX += change.position.x - lastPos.x
                                    offsetY += change.position.y - lastPos.y
                                }

                                FrameDrag.CREATE -> {
                                    val size = com.kallan.naistudio.models.InfiniteFrameSizes.nearest(
                                        state.infiniteFrame?.w ?: state.settings.infiniteFrameW.takeIf { it > 0 } ?: 1024,
                                        state.infiniteFrame?.h ?: state.settings.infiniteFrameH.takeIf { it > 0 } ?: 1024,
                                    )
                                    state.updateInfiniteFrame(EditRect(
                                        (now.x - size.width / 2f).roundToInt(),
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
                            lastPos = change.position
                            lastDoc = now
                            change.consume()
                        }
                        if (!moved) {
                            if (doubleTap) {
                                // 双击不拖 ⇒ 以这一点为中心放一个框（沿用当前框尺寸，没有就 1024×1024 ✓）
                                val w = frame?.w ?: 1024
                                val h = frame?.h ?: 1024
                                val placed = EditRect(
                                    (pressDoc.x - w / 2f).roundToInt(),
                                    (pressDoc.y - h / 2f).roundToInt(),
                                    w,
                                    h,
                                )
                                state.updateInfiniteFrame(state.infiniteCanvas?.clampFrame(placed) ?: placed)
                            } else {
                                lastTapUp = upTime
                                lastTapPos = down.position
                            }
                        } else if (mode == FrameDrag.CREATE) {
                            // 太小的框留着没意义（生成有下限 ✓）⇒ 收掉并**如实说一句** ✓
                            val made = state.infiniteFrame
                            if (made != null && (made.w < INFINITE_FRAME_MIN || made.h < INFINITE_FRAME_MIN)) {
                                state.updateInfiniteFrame(null)
                                state.showToast(t("infinite.frameTooSmall"))
                            }
                        }
                        // 框变了才落盘 ✓（纯平移 / 缩放不写盘 ✓）
                        if (state.infiniteFrame != frame) state.commitInfiniteCanvas()
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
                        )
                ) {
                    Image(
                        bitmap = image,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        filterQuality = FilterQuality.Medium,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                state.infiniteFrame?.let { frame ->
                    val transform = view.value
                    val frameRect = transform.screenRect(frame)
                    val accent = MaterialTheme.colorScheme.primary
                    FrameOverlay(frameRect, accent)
                    Text(
                        "${t("infinite.redrawArea")} ${frame.w} × ${frame.h}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset {
                                IntOffset(frameRect.left.roundToInt(), (frameRect.top - 22.dp.toPx()).roundToInt())
                            }
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent)
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

            // 控件与画布手势层平级，选择规格不会触发画布手势。
            // ⚠️ 提示那一行和真正发出去的请求用的是**同一个函数**（`AppState.infiniteFramePlan` ✓）——
            //    界面写一个数、请求发另一个数，是最伤人的那种不一致 ✗（本仓库吃过这个亏 ✓）。
            // ⚠️ 撤销 / 重做**是真按钮**（栈里有货才亮 ✓）—— 亮着点了没反应是最烦的那种坏 ✗。
            val planText = state.infinitePlanText()
            val hint = when {
                state.infiniteRunning -> t("infinite.generating")
                planText != null -> planText
                else -> t("infinite.runNeedFrame")
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                state.infiniteFrame?.let { frame ->
                    FrameSizeChip(
                        state = state,
                        frame = frame,
                        label = t("infinite.frameSize"),
                        background = Color(0xCC1B1E24),
                        textColor = Color(0xFFE6E7EA),
                    )
                }
                Row(
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
                    CanvasChip(text = hint)
                }
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

/** Draw the fixed crop frame in viewport coordinates. */
@Composable
private fun FrameOverlay(frame: Rect, accent: Color) {
    Canvas(Modifier.fillMaxSize()) {

        drawRoundRect(
            accent.copy(alpha = 0.18f), frame.topLeft, frame.size,
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
        drawRoundRect(
            accent, frame.topLeft, frame.size,
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(2.dp.toPx()),
        )
    }
}

/**
 * 画布底部那一排小胶囊（提示 / 撤销 / 重做 ✓）。
 *
 * ⚠️ **不许拿焦点** ✗：桌面端「按住空格 = 平移画布」（`state.canvasSpacePan` ✓）走的是键事件 ✓，
 * 鼠标点过的按钮会**一直拿着焦点** ⇒ 之后按空格会去"激活"那颗按钮 ✗（生图页那几颗也踩过同一个坑 ✓，
 * 见 `GenerateScreen.NoButtonFocus` 的注释 ✓）。
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
            .background(if (enabled) Color(0xCC1B1E24) else Color(0x801B1E24))
            .then(if (clickable) Modifier.clickable { onClick?.invoke() } else Modifier)
            .focusProperties { canFocus = false }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) Color(0xFFE6E7EA) else Color(0xFF8A8D93),
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



/** 这一点在不在框里（**文档坐标** ✓）。 */
private fun insideFrame(frame: EditRect, point: Offset): Boolean =
    point.x >= frame.x && point.x <= frame.x + frame.w &&
        point.y >= frame.y && point.y <= frame.y + frame.h

/**
 * **视口坐标 → 文档坐标**的那一次换算（无限画布的框手势用 ✓）。
 *
 * 内容层在视口里是**居中 + `offsetX/offsetY` 平移**过的 ✓（见 `InfiniteCanvasArea` 的布局 ✓），
 * 而框存的是**文档坐标** ✓ ⇒ 换算三步：
 *  1. 内容层左上角 = `((视口 − 内容层像素尺寸) / 2) + 平移` ✓；
 *  2. 相对内容层的屏幕位移 ÷ `k`（`k = fit × scale` = **屏幕 px / 文档像素** ✓）= 画布像素 ✓；
 *  3. 再加**原点**（`pixel = doc − origin` ✓ 的反向 ✓）= 文档坐标 ✓。
 *
 * ⚠️ 这几个数**每一帧现读** ✗ 不能在手势开始时捕获成局部值 ✓：
 * `pointerInput` 里那个协程**不会因为重组而重启** ✓，捕获进去就永远停在旧值 ✗
 * —— 这正是用户报「**拖不动框**」修的那一处旁边最容易再犯的错 ✓。
 *
 * ⚠️ 为什么这个换算长在**手势层**（而不是复用 `InfiniteCanvas.pixelX/docX` ✓）：
 * 那对函数管的是"画布像素 ↔ 文档" ✓（**内容层内部**的坐标 ✓），
 * 而手势拿到的 `position` 是**视口**坐标（含居中与平移 ✓）—— 多出来的这一层只有界面知道 ✓。
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
    /** 屏幕 px / 文档像素 ✓（缩放到极端值时也**不许是 0** ✗ —— 除零会算出 Infinity 的框 ✓）。 */
    val k: Float get() = (fit * scale).coerceAtLeast(0.0001f)

    private val contentLeft: Float get() = (viewWidth - contentWidth * k) / 2f + offsetX
    private val contentTop: Float get() = (viewHeight - contentHeight * k) / 2f + offsetY

    fun toDoc(position: Offset): Offset = Offset(
        x = (position.x - contentLeft) / k + originX,
        y = (position.y - contentTop) / k + originY,
    )

    fun screenRect(frame: EditRect): Rect = screenRect(
        Rect(frame.x.toFloat(), frame.y.toFloat(), (frame.x + frame.w).toFloat(), (frame.y + frame.h).toFloat()),
    )

    fun screenRect(rect: Rect): Rect = Rect(
        contentLeft + (rect.left - originX) * k,
        contentTop + (rect.top - originY) * k,
        contentLeft + (rect.right - originX) * k,
        contentTop + (rect.bottom - originY) * k,
    )

}
