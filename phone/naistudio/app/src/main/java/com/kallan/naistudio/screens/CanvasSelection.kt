package com.kallan.naistudio.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.models.MaskPoint
import com.kallan.naistudio.models.SelectionGeometry
import com.kallan.naistudio.models.SelectionShape
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * **三期：选区**（矩形 / 套索 + 移动 / 缩放 / 旋转）。
 *
 * 这个文件只管"看得见的部分"和"手势落在哪一个控制点"：
 *  · [CanvasSelectionOverlay] 画选框（虚线）、8 个控制点、以及上面那个旋转点；
 *  · [CanvasFloatingLayer] 把"抬起来的那块像素"按当前变换画在正确的位置；
 *  · [selectionHandleAt] 是命中判定（屏幕坐标 → 落在哪个控制点上）；
 *  · [imageToScreen] 是坐标换算（`CanvasEditor` 里那个反变换的逆）。
 *
 * 真正的像素运算在 `models/SelectionOps.kt`（纯函数、可单测）。
 */

/** 一次选区手势的类型。 */
internal enum class SelectionGesture {
    NONE,

    /** 拉一个新框（矩形）。 */
    MARQUEE,

    /** 拉一个新套索。 */
    LASSO,

    /** 拖动整块。 */
    MOVE,

    /** 拖控制点缩放。 */
    SCALE,

    /** 拖旋转点。 */
    ROTATE,
}

/** 8 个控制点里是哪一个（用来定"对面的角"当锚点）。 */
internal enum class SelectionHandle {
    TOP_LEFT,
    TOP,
    TOP_RIGHT,
    RIGHT,
    BOTTOM_RIGHT,
    BOTTOM,
    BOTTOM_LEFT,
    LEFT,
    ROTATE,
}

/** 对角那个控制点（缩放时拿它当不动的锚点）。 */
internal fun oppositeHandle(handle: SelectionHandle): SelectionHandle? = when (handle) {
    SelectionHandle.TOP_LEFT -> SelectionHandle.BOTTOM_RIGHT
    SelectionHandle.TOP -> SelectionHandle.BOTTOM
    SelectionHandle.TOP_RIGHT -> SelectionHandle.BOTTOM_LEFT
    SelectionHandle.RIGHT -> SelectionHandle.LEFT
    SelectionHandle.BOTTOM_RIGHT -> SelectionHandle.TOP_LEFT
    SelectionHandle.BOTTOM -> SelectionHandle.TOP
    SelectionHandle.BOTTOM_LEFT -> SelectionHandle.TOP_RIGHT
    SelectionHandle.LEFT -> SelectionHandle.RIGHT
    SelectionHandle.ROTATE -> null
}

/** 手势过程中的那些"上一次"值（`remember` 一个就够，界面和手势循环共用）。 */
internal class SelectionGestureState {
    var gesture by mutableStateOf(SelectionGesture.NONE)
    var handle by mutableStateOf<SelectionHandle?>(null)

    /** 上一个指针位置（**画布像素**）。 */
    var lastX by mutableStateOf(0f)
    var lastY by mutableStateOf(0f)

    /** 拉框的起点（画布像素）。 */
    var startX by mutableStateOf(0f)
    var startY by mutableStateOf(0f)

    /** 套索沿途的点（归一化）。 */
    var lasso by mutableStateOf<List<MaskPoint>>(emptyList())

    /** 缩放用的锚点（画布像素）+ 上一次的局部坐标。 */
    var anchorX by mutableStateOf(0f)
    var anchorY by mutableStateOf(0f)
    var lastLocalX by mutableStateOf(0f)
    var lastLocalY by mutableStateOf(0f)

    /** 旋转累计（度）——Shift 吸附要用累计值，不能只看单帧增量。 */
    var rotationAccum by mutableStateOf(0f)

    /** 上一个"指针相对中心的角度"（旋转手势用）。 */
    var lastAngle by mutableStateOf(0f)

    val active: Boolean get() = gesture != SelectionGesture.NONE

    fun reset() {
        gesture = SelectionGesture.NONE
        handle = null
        lasso = emptyList()
        rotationAccum = 0f
    }
}

/** 图片像素坐标 → 屏幕坐标（`CanvasEditor` 里 `offsetToImagePixel` 的逆运算）。 */
internal fun imageToScreen(
    imageX: Float,
    imageY: Float,
    area: IntSize,
    imageWidth: Int,
    imageHeight: Int,
    zoom: Float,
    offset: Offset,
): Offset {
    if (area.width <= 0 || area.height <= 0 || imageWidth <= 0 || imageHeight <= 0) return Offset.Zero
    val aspect = imageWidth.toFloat() / imageHeight.toFloat()
    val fit = fitRect(area, aspect)
    val centerX = area.width / 2f
    val centerY = area.height / 2f
    val contentX = fit.left + imageX / imageWidth * fit.width
    val contentY = fit.top + imageY / imageHeight * fit.height
    return Offset(
        x = centerX + (contentX - centerX) * zoom + offset.x,
        y = centerY + (contentY - centerY) * zoom + offset.y,
    )
}

/** 控制点判定半径（屏幕像素）。 */
private const val HANDLE_TOUCH_RADIUS = 10f

/** 选框上任一点在**画布像素**坐标下的位置（没抬起来时就是那个轴对齐的框）。 */
internal fun selectionControlPoints(
    shape: SelectionShape,
    imageWidth: Int,
    imageHeight: Int,
    corners: List<Pair<Float, Float>>? = null,
): Map<SelectionHandle, Offset> {
    val quad = corners ?: run {
        val bounds = SelectionGeometry.bounds(shape, imageWidth, imageHeight) ?: return emptyMap()
        listOf(
            bounds.x.toFloat() to bounds.y.toFloat(),
            (bounds.x + bounds.w).toFloat() to bounds.y.toFloat(),
            (bounds.x + bounds.w).toFloat() to (bounds.y + bounds.h).toFloat(),
            bounds.x.toFloat() to (bounds.y + bounds.h).toFloat(),
        )
    }
    if (quad.size < 4) return emptyMap()
    val tl = Offset(quad[0].first, quad[0].second)
    val tr = Offset(quad[1].first, quad[1].second)
    val br = Offset(quad[2].first, quad[2].second)
    val bl = Offset(quad[3].first, quad[3].second)
    val top = Offset((tl.x + tr.x) / 2f, (tl.y + tr.y) / 2f)
    val right = Offset((tr.x + br.x) / 2f, (tr.y + br.y) / 2f)
    val bottom = Offset((bl.x + br.x) / 2f, (bl.y + br.y) / 2f)
    val left = Offset((tl.x + bl.x) / 2f, (tl.y + bl.y) / 2f)
    // 旋转点：从"上边中点"往框外再挪一段（按框的大小给个固定比例，别太远）
    val span = hypot(tr.x - tl.x, tr.y - tl.y).coerceAtLeast(1f)
    val outward = Offset(top.x - (left.x + right.x) / 2f, top.y - (left.y + right.y) / 2f)
    val outwardLength = hypot(outward.x, outward.y).coerceAtLeast(0.001f)
    val rotateDistance = (span * 0.18f).coerceIn(16f, 60f)
    val rotate = Offset(
        top.x + outward.x / outwardLength * rotateDistance,
        top.y + outward.y / outwardLength * rotateDistance,
    )
    return mapOf(
        SelectionHandle.TOP_LEFT to tl,
        SelectionHandle.TOP to top,
        SelectionHandle.TOP_RIGHT to tr,
        SelectionHandle.RIGHT to right,
        SelectionHandle.BOTTOM_RIGHT to br,
        SelectionHandle.BOTTOM to bottom,
        SelectionHandle.BOTTOM_LEFT to bl,
        SelectionHandle.LEFT to left,
        SelectionHandle.ROTATE to rotate,
    )
}

/** 屏幕坐标落在哪个控制点上（旋转点优先，它离得远、不容易和别的抢）。 */
internal fun selectionHandleAt(
    screen: Offset,
    shape: SelectionShape,
    imageWidth: Int,
    imageHeight: Int,
    area: IntSize,
    zoom: Float,
    offset: Offset,
    corners: List<Pair<Float, Float>>? = null,
    rotateScreen: Offset? = null,
): SelectionHandle? {
    val points = selectionControlPoints(shape, imageWidth, imageHeight, corners)
    if (points.isEmpty()) return null
    // 旋转点由调用方算好（它跟的是浮动块的变换），这里用它替换掉"轴对齐"的那个
    val effective = if (rotateScreen != null) points + (SelectionHandle.ROTATE to rotateScreen) else points
    var best: SelectionHandle? = null
    var bestDistance = HANDLE_TOUCH_RADIUS * HANDLE_TOUCH_RADIUS
    effective.forEach { (handle, canvasPoint) ->
        val screenPoint = imageToScreen(canvasPoint.x, canvasPoint.y, area, imageWidth, imageHeight, zoom, offset)
        val dx = screenPoint.x - screen.x
        val dy = screenPoint.y - screen.y
        val distance = dx * dx + dy * dy
        if (distance <= bestDistance) {
            best = handle
            bestDistance = distance
        }
    }
    return best
}

/** 归一化坐标的点在不在选区里（屏幕上的一点 → 归一化 → 判形状）。 */
internal fun screenInsideSelection(
    screen: Offset,
    shape: SelectionShape,
    imageWidth: Int,
    imageHeight: Int,
    area: IntSize,
    zoom: Float,
    offset: Offset,
): Boolean {
    val point = offsetToImagePixel(screen, area, imageWidth, imageHeight, zoom, offset) ?: return false
    return SelectionGeometry.contains(shape, point.x / imageWidth, point.y / imageHeight)
}

/**
 * 选框叠加层：虚线框 + 8 个控制点 + 旋转点。
 *
 * @param corners 浮动块变换后的四个角（**画布像素**）；null = 还没抬起来，画轴对齐的框
 */
@Composable
internal fun CanvasSelectionOverlay(
    shape: SelectionShape,
    imageWidth: Int,
    imageHeight: Int,
    area: IntSize,
    zoom: Float,
    offset: Offset,
    corners: List<Pair<Float, Float>>?,
    /** 画不画控制点（只有选择类工具下才画；其它工具下选框只当"工作范围"提示）。 */
    handlesVisible: Boolean = true,
) {
    val accent = Color(0xFF2196F3)
    val shadow = Color(0xCC000000)
    Canvas(Modifier.fillMaxSize()) {
        // 框：矩形直接画屏幕矩形（抬起来之后跟着四个角走，可能是斜的）
        val quad = corners?.map {
            imageToScreen(it.first, it.second, area, imageWidth, imageHeight, zoom, offset)
        }
        if (quad != null && quad.size == 4) {
            val path = Path().apply {
                moveTo(quad[0].x, quad[0].y)
                lineTo(quad[1].x, quad[1].y)
                lineTo(quad[2].x, quad[2].y)
                lineTo(quad[3].x, quad[3].y)
                close()
            }
            drawPath(path, color = shadow, style = Stroke(width = 3f))
            drawPath(
                path,
                color = accent,
                style = Stroke(
                    width = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                ),
            )
        } else {
            drawShapeOutline(shape, imageWidth, imageHeight, area, zoom, offset, accent, shadow)
        }

        // 控制点（只有选择类工具下才画）
        if (!handlesVisible) return@Canvas
        val points = selectionControlPoints(shape, imageWidth, imageHeight, corners)
        val rotatePoint = points[SelectionHandle.ROTATE]
        val topPoint = points[SelectionHandle.TOP]
        if (rotatePoint != null && topPoint != null) {
            val topScreen = imageToScreen(topPoint.x, topPoint.y, area, imageWidth, imageHeight, zoom, offset)
            val rotateScreen = imageToScreen(rotatePoint.x, rotatePoint.y, area, imageWidth, imageHeight, zoom, offset)
            drawLine(shadow, topScreen, rotateScreen, strokeWidth = 3f)
            drawLine(accent, topScreen, rotateScreen, strokeWidth = 1.5f)
            drawCircle(Color.White, radius = 6f, center = rotateScreen)
            drawCircle(accent, radius = 6f, center = rotateScreen, style = Stroke(width = 1.5f))
        }
        points.forEach { (handle, point) ->
            if (handle == SelectionHandle.ROTATE) return@forEach
            val screen = imageToScreen(point.x, point.y, area, imageWidth, imageHeight, zoom, offset)
            val half = 4.5f
            drawRect(
                color = shadow,
                topLeft = Offset(screen.x - half - 1.5f, screen.y - half - 1.5f),
                size = Size(half * 2 + 3f, half * 2 + 3f),
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(screen.x - half, screen.y - half),
                size = Size(half * 2, half * 2),
            )
            drawRect(
                color = accent,
                topLeft = Offset(screen.x - half, screen.y - half),
                size = Size(half * 2, half * 2),
                style = Stroke(width = 1.2f),
            )
        }
    }
}

/** 没抬起来时的框（轴对齐矩形 / 套索多边形）。 */
private fun DrawScope.drawShapeOutline(
    shape: SelectionShape,
    imageWidth: Int,
    imageHeight: Int,
    area: IntSize,
    zoom: Float,
    offset: Offset,
    accent: Color,
    shadow: Color,
) {
    when (shape) {
        is SelectionShape.Rect -> {
            val a = imageToScreen(
                minOf(shape.left, shape.right) * imageWidth,
                minOf(shape.top, shape.bottom) * imageHeight,
                area, imageWidth, imageHeight, zoom, offset,
            )
            val b = imageToScreen(
                maxOf(shape.left, shape.right) * imageWidth,
                maxOf(shape.top, shape.bottom) * imageHeight,
                area, imageWidth, imageHeight, zoom, offset,
            )
            val size = Size(abs(b.x - a.x), abs(b.y - a.y))
            val topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y))
            drawRect(color = shadow, topLeft = topLeft, size = size, style = Stroke(width = 3f))
            drawRect(
                color = accent,
                topLeft = topLeft,
                size = size,
                style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
            )
        }

        is SelectionShape.Polygon -> {
            if (shape.points.size < 2) return
            val path = Path()
            shape.points.forEachIndexed { index, point ->
                val screen = imageToScreen(
                    point.x * imageWidth,
                    point.y * imageHeight,
                    area, imageWidth, imageHeight, zoom, offset,
                )
                if (index == 0) path.moveTo(screen.x, screen.y) else path.lineTo(screen.x, screen.y)
            }
            path.close()
            drawPath(path, color = shadow, style = Stroke(width = 3f))
            drawPath(
                path,
                color = accent,
                style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
            )
        }
    }
}

/**
 * 把"抬起来的那块"按当前变换画出来。
 *
 * 位置和变换都走 `graphicsLayer`（缩放→旋转→平移，缩放旋转都以**图层中心**为原点），
 * 和 `FloatingSelection` 里那套模型**顺序完全一致** —— 差一个顺序就会"看着对、合上去偏了"。
 */
@Composable
internal fun CanvasFloatingLayer(
    bitmap: ImageBitmap?,
    patchWidth: Int,
    patchHeight: Int,
    originX: Int,
    originY: Int,
    patchScaleX: Float,
    patchScaleY: Float,
    rotationDeg: Float,
    deltaX: Float,
    deltaY: Float,
    imageWidth: Int,
    imageHeight: Int,
    area: IntSize,
    zoom: Float,
    offset: Offset,
) {
    if (bitmap == null || patchWidth <= 0 || patchHeight <= 0 || imageWidth <= 0) return
    val topLeft = imageToScreen(
        originX.toFloat(),
        originY.toFloat(),
        area, imageWidth, imageHeight, zoom, offset,
    )
    val pixelToScreen = zoom * (fitRect(area, imageWidth.toFloat() / imageHeight.toFloat()).width / imageWidth)
    if (pixelToScreen <= 0f) return
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) }
                .size(
                    width = (patchWidth * pixelToScreen).dp,
                    height = (patchHeight * pixelToScreen).dp,
                )
                .graphicsLayer {
                    scaleX = patchScaleX
                    scaleY = patchScaleY
                    rotationZ = rotationDeg
                    translationX = deltaX * pixelToScreen
                    translationY = deltaY * pixelToScreen
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
}
