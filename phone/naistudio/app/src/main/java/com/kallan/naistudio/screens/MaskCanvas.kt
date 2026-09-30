package com.kallan.naistudio.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.kallan.naistudio.ui.RefSlider as Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.kallan.naistudio.ui.RefTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.models.MASK_BRUSH_MAX_PIXELS
import com.kallan.naistudio.models.MASK_BRUSH_MIN_PIXELS
import com.kallan.naistudio.models.MaskPoint
import com.kallan.naistudio.models.MaskStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import com.kallan.naistudio.models.FocusedInpaint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.kallan.naistudio.ui.RefIconButton as IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.ui.graphics.vector.ImageVector
import com.kallan.naistudio.ui.BrushIcon
import com.kallan.naistudio.ui.EraserIcon
import com.kallan.naistudio.ui.MaskSelectIcon
import com.kallan.naistudio.ui.UndoIcon
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.state.MaskFocusFrame
import kotlin.math.roundToInt
import com.kallan.naistudio.ui.LocalRef
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.sp

/** 遮罩叠加层的不透明度（和参考实现的 0.72 对齐）。 */
private const val MASK_OVERLAY_ALPHA = 0.72f

/** 顶栏高度（Material3 `TopAppBar` 默认 64dp）：工具条顶替顶栏时按同一高度，图片才不缩小。 */
private val TOP_BAR_HEIGHT = 64.dp

/**
 * 遮罩笔迹的矢量叠加层。
 *
 * 直接按笔迹描边（笔宽 = 笔刷像素 × 显示缩放），**不重建位图** ——
 * 这也是"逐像素遮罩"能涂得顺的原因（832×1216 的位图每帧重建是 4 MB，扛不住）。
 * 橡皮用 `BlendMode.Clear`，所以整层要开离屏合成（Offscreen）。
 */
@Composable
internal fun MaskStrokeOverlay(
    strokes: List<MaskStroke>,
    sourceWidth: Int,
    sourceHeight: Int,
    area: IntSize,
    modifier: Modifier = Modifier,
) {
    val aspect = if (sourceHeight > 0) sourceWidth.toFloat() / sourceHeight.toFloat() else 0f
    val rect = fitRect(area, aspect)
    Canvas(
        modifier = modifier.graphicsLayer {
            alpha = MASK_OVERLAY_ALPHA
            compositingStrategy = CompositingStrategy.Offscreen
        },
    ) {
        if (rect.width <= 0f || rect.height <= 0f || sourceWidth <= 0) return@Canvas
        val scale = rect.width / sourceWidth.toFloat()
        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            val width = (stroke.brushPixels * scale).coerceAtLeast(1f)
            val blend = if (stroke.erasing) BlendMode.Clear else BlendMode.SrcOver
            val first = stroke.points.first()
            val start = Offset(
                rect.left + first.x * rect.width,
                rect.top + first.y * rect.height,
            )
            if (stroke.points.size == 1) {
                // 单点（轻点一下）
                drawCircle(color = Color.White, radius = width / 2f, center = start, blendMode = blend)
                continue
            }
            val path = Path().apply {
                moveTo(start.x, start.y)
                for (point in stroke.points.drop(1)) {
                    lineTo(rect.left + point.x * rect.width, rect.top + point.y * rect.height)
                }
            }
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(
                    width = width,
                    cap = if (stroke.square) StrokeCap.Butt else StrokeCap.Round,
                    join = if (stroke.square) StrokeJoin.Miter else StrokeJoin.Round,
                ),
                blendMode = blend,
            )
        }
    }
}

/**
 * 遮罩工具条：**画笔 / 橡皮 / 撤销 / 大小**（用户点名的那几个）。
 *
 * 它顶替**被隐藏掉的顶栏**那一条 —— 所以做成**单行**、高度不超过原来的顶栏（64dp），
 * 图片既不会被压住，也不会因为多一行而缩小。颜色沿用顶栏的 `surfaceContainer`。
 * （「清除遮罩」在运行条那一行，不占这里的宽度。）
 */
@Composable
internal fun MaskToolBar(state: AppState, t: (String) -> String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth()) {
        // Material 组件默认要求 48dp 的最小触控高度，会把这一行撑到 80dp —— 比顶栏还高，
        // 图片就缩小了。这里显式放开限制，并把整行钉成**和顶栏一样高（64dp）**：
        // 顶栏消失、工具条顶上，图片的可用高度和平时一模一样。
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 顶部**一律用图标**（用户 2026-09-17："遮罩模式的顶部都变成图标，不用文字"）
                // ⚠️ 用户 2026-09-26：「**遮罩的图标标注**」—— 图标照旧，但每颗**底下加一行功能名**
                //    （`MaskLabeledButton`）。早前是纯图标，画笔/橡皮还好认，**框选那颗
                //    （虚线框+指针）和清框那颗（叉）全靠猜**；名字用滑杆让出来的那点宽度放。
                MaskLabeledButton(
                    icon = BrushIcon,
                    label = t("mask.draw"),
                    selected = !state.maskErasing,
                ) { state.setMaskEraser(false) }
                MaskLabeledButton(
                    icon = EraserIcon,
                    label = t("mask.erase"),
                    selected = state.maskErasing,
                ) { state.setMaskEraser(true) }
                // 聚焦重绘：与电脑线同一个位置（顶部工具条），图标 + 底下一行字
                MaskLabeledButton(
                    icon = MaskSelectIcon,
                    label = t("mask.focus"),
                    selected = state.maskFocusTool,
                ) { state.chooseMaskFocusTool(!state.maskFocusTool) }
                if (state.maskFocusRect != null) {
                    // 清框（原来是一颗 48dp 的 IconButton，现在和别的按钮同一个样式 + 底下有字）
                    MaskLabeledButton(
                        icon = Icons.Default.Close,
                        label = t("mask.focusClear"),
                        selected = false,
                    ) { state.clearMaskFocus() }
                }
                MaskLabeledButton(
                    icon = UndoIcon,
                    label = t("mask.undo"),
                    selected = false,
                    enabled = state.maskCanUndo,
                ) { state.undoMask() }
                // 滑杆：聚焦模式下是官方「Minimum Context Area」，否则是笔刷大小
                if (state.maskFocusTool) {
                    Slider(
                        value = state.settings.inpaintFocusContext.toFloat(),
                        onValueChange = { raw ->
                            state.setSettings {
                                it.copy(inpaintFocusContext = raw.roundToInt().coerceIn(0, 256))
                            }
                        },
                        valueRange = 0f..256f,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                } else {
                    Slider(
                        value = state.maskBrushPixels.toFloat(),
                        onValueChange = { raw -> state.setMaskBrush(raw.roundToInt()) },
                        // 无极调控：1..350 像素，没有 step
                        valueRange = MASK_BRUSH_MIN_PIXELS.toFloat()..MASK_BRUSH_MAX_PIXELS.toFloat(),
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

/** 工具条上的紧凑开关（画笔 / 橡皮）。 */
@Composable
private fun MaskModeToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/** 图片按 `ContentScale.Fit` 在给定区域里的实际矩形。 */
internal fun fitRect(area: IntSize, aspect: Float): Rect {
    if (area.width <= 0 || area.height <= 0 || aspect <= 0f) return Rect.Zero
    val areaAspect = area.width.toFloat() / area.height.toFloat()
    return if (areaAspect > aspect) {
        val height = area.height.toFloat()
        val width = height * aspect
        Rect(Offset((area.width - width) / 2f, 0f), Size(width, height))
    } else {
        val width = area.width.toFloat()
        val height = width / aspect
        Rect(Offset(0f, (area.height - height) / 2f), Size(width, height))
    }
}

/** 屏幕坐标 → 归一化图片坐标；落在图片矩形外返回 null（涂到黑边上不算）。 */
internal fun toMaskPoint(position: Offset, rect: Rect): MaskPoint? {
    if (rect.width <= 0f || rect.height <= 0f) return null
    if (position.x < rect.left || position.x > rect.right) return null
    if (position.y < rect.top || position.y > rect.bottom) return null
    return MaskPoint(
        x = ((position.x - rect.left) / rect.width).coerceIn(0f, 1f),
        y = ((position.y - rect.top) / rect.height).coerceIn(0f, 1f),
    )
}
/**
 * **聚焦重绘的框 + 最小上下文提示圈**（手机线，2026-09-17 从电脑线移植）。
 *
 *  · 虚线白框 = 会被重绘的区域；
 *  · 框外半透明红圈 = 官方「Minimum Context Area」那圈**只给模型参考、不重绘**的上下文。
 */
@Composable
internal fun MaskFocusOverlay(
    state: AppState,
    sourceWidth: Int,
    sourceHeight: Int,
    area: IntSize,
    modifier: Modifier = Modifier,
) {
    val frame = state.maskFocusRect ?: return
    val aspect = if (sourceHeight > 0) sourceWidth.toFloat() / sourceHeight.toFloat() else 0f
    val rect = fitRect(area, aspect)
    if (rect.width <= 0f || rect.height <= 0f || sourceWidth <= 0 || sourceHeight <= 0) return
    val plan = state.maskFocusPlan()
    Canvas(modifier = modifier) {
        fun screenX(nx: Float) = rect.left + nx * rect.width
        fun screenY(ny: Float) = rect.top + ny * rect.height
        plan?.let { p ->
            val left = screenX(p.cropX.toFloat() / sourceWidth)
            val top = screenY(p.cropY.toFloat() / sourceHeight)
            val right = screenX((p.cropX + p.cropW).toFloat() / sourceWidth)
            val bottom = screenY((p.cropY + p.cropH).toFloat() / sourceHeight)
            drawRect(
                color = Color(0x33FF3355),
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
            )
        }
        val a = Offset(screenX(minOf(frame.left, frame.right)), screenY(minOf(frame.top, frame.bottom)))
        val b = Offset(screenX(maxOf(frame.left, frame.right)), screenY(maxOf(frame.top, frame.bottom)))
        drawRect(
            color = Color.White,
            topLeft = a,
            size = Size(b.x - a.x, b.y - a.y),
            style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 9f))),
        )
    }
}

/**
 * 聚焦模式下在图上**拖一个框**（单指；坐标归一化后交给 [AppState.updateMaskFocus]）。
 * 双指仍然只是"中止这一拖"，与画笔手势同款处理。
 */
@Composable
internal fun MaskFocusGestureLayer(
    state: AppState,
    sourceWidth: Int,
    sourceHeight: Int,
    area: IntSize,
    modifier: Modifier = Modifier,
) {
    val aspect = if (sourceHeight > 0) sourceWidth.toFloat() / sourceHeight.toFloat() else 0f
    Box(
        modifier = modifier.pointerInput(state.maskFocusTool) {
            if (!state.maskFocusTool) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = toMaskPoint(down.position, fitRect(area, aspect)) ?: return@awaitEachGesture
                var jumpedToPinch = false
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.isEmpty()) break
                    if (pressed.size >= 2) {
                        jumpedToPinch = true
                        pressed.forEach { it.consume() }
                        continue
                    }
                    if (jumpedToPinch) continue
                    val change = pressed[0]
                    val point = toMaskPoint(change.position, fitRect(area, aspect)) ?: continue
                    state.updateMaskFocus(MaskFocusFrame(start.x, start.y, point.x, point.y))
                    change.consume()
                }
            }
        },
    )
}

/**
 * 遮罩工具条上的按钮：**图标 + 底下那行功能名**（用户 2026-09-26：「遮罩的图标标注」）。
 *
 * 尺寸和运行条那几颗对齐（44dp 见方）：图标 20dp + 1dp + 一行 `labelSmall` ≈ 37dp，居中放得下。
 * 这一行仍然不超过原来的顶栏高度（64dp，见 [MaskToolBar] 的说明）⇒ 图片可用高度不变。
 *
 * 为什么不再用 `IconToggleButton`：它只会画一个图标，加不了下面那行字。
 * 选中态沿用 M3 的"主色图标 + 淡染底"（原来是"主色图标、没有底"）——
 * 底下一行字之后，光靠变色不够醒目。
 */
@Composable
private fun MaskLabeledButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // 网页 `.tool`：46×46、圆角 8、10.5 muted；on = selected / onSelected / selectedBorder；禁用 .38
    val ref = LocalRef.current
    val tint = if (selected) ref.onSelected else ref.muted
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .size(width = 46.dp, height = 46.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(shape)
            .background(if (selected) ref.selected else Color.Transparent)
            .border(1.dp, if (selected) ref.selectedBorder else Color.Transparent, shape)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            fontSize = 10.5.sp,
            lineHeight = 12.sp,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}