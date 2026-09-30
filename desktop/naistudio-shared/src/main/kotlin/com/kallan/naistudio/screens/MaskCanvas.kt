package com.kallan.naistudio.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.models.MASK_BRUSH_MAX_PIXELS
import com.kallan.naistudio.models.MASK_BRUSH_MIN_PIXELS
import com.kallan.naistudio.models.MaskPoint
import com.kallan.naistudio.models.MaskStroke
import com.kallan.naistudio.models.EditRect
import com.kallan.naistudio.models.SelectionGeometry
import com.kallan.naistudio.models.SelectionShape
import com.kallan.naistudio.state.AppState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.vector.ImageVector
import com.kallan.naistudio.ui.BrushIcon
import com.kallan.naistudio.ui.EraserIcon
import com.kallan.naistudio.ui.UndoIcon
import com.kallan.naistudio.ui.MaskSelectIcon
import com.kallan.naistudio.ui.NaiGlass
import kotlin.math.roundToInt

/** 遮罩叠加层的不透明度（和参考实现的 0.72 对齐）。 */
private const val MASK_OVERLAY_ALPHA = 0.72f

/**
 * **顶栏那一行的高度**（外壳的 `TopAppBar` 与遮罩模式顶替它的工具条**共用**这一个数）。
 *
 * 2026-09-19 用户：「顶栏要只变矮」→ 从 Material 默认的 64dp 收到这里。
 * 两处必须同源：遮罩模式一进来顶栏就换成工具条，高度不一致的话图片会**跳一下**。
 */
internal val TOP_BAR_HEIGHT = 52.dp

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
    /**
     * 画布当前的缩放 / 平移（和图片层那套 `graphicsLayer` **同一个口径**，见 [shownImageRect]）。
     *
     * 用户 2026-09-22：「遮罩模式…遮罩和聚焦框跟随图片」—— 笔迹必须画在**图片当前实际
     * 显示的那块矩形**上，而不是"未缩放的 Fit 矩形"上；否则放大/拖动之后涂上去的位置
     * 和手指（以及真正落进遮罩位图的像素）差一大截。
     */
    scale: Float = 1f,
    offset: Offset = Offset.Zero,
    modifier: Modifier = Modifier,
) {
    val aspect = if (sourceHeight > 0) sourceWidth.toFloat() / sourceHeight.toFloat() else 0f
    val rect = shownImageRect(area, aspect, scale, offset)
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
 * 遮罩工具条：**画笔 / 橡皮 / 聚焦框 / 撤销 / 大小**。
 *
 * 它顶替**被隐藏掉的顶栏**那一条 —— 所以做成**单行**、高度不超过原来的顶栏（64dp），
 * 图片既不会被压住，也不会因为多一行而缩小。颜色沿用顶栏的 `surfaceContainer`。
 * （「清除遮罩」在运行条那一行，不占这里的宽度。）
 *
 * ## 聚焦重绘（官方 Focused Inpainting，做在遮罩层里）
 *
 * 虚线框那颗 = 在图上**拖一个矩形**圈出"要重绘的那一小块"：
 *  · 框里可以照常涂遮罩，**不涂就是整框重绘**（官方口径）；
 *  · 发请求前这一块会被放大到约 100 万像素（细节更多，见 `FocusedInpaint`）；
 *  · 官方那根「Minimum Context Area」滑杆在这里**顶替"画笔大小"**（本来就是同一个位置）。
 */
@Composable
internal fun MaskToolBar(state: AppState, t: (String) -> String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        // 毛玻璃：和外壳顶栏同一档（`BAR_ALPHA`）—— 工具条在遮罩模式下**顶替顶栏**，
        // 而且宽屏布局里它是**浮在画布上**的一条，背后就是图，半透明才有意义。
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = NaiGlass.BAR_ALPHA),
        // ⚠️ **必须显式给内容色**：上面那个色是"调过 alpha 的"，`contentColorFor()` 认不出它、
        // 会返回 `Unspecified` —— 于是里面不指定 tint 的图标在深色模式下会变成黑的（看不见）。
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        // Material 组件默认要求 48dp 的最小触控高度，会把这一行撑到 80dp —— 比顶栏还高，
        // 图片就缩小了。这里显式放开限制，并把整行钉成**和顶栏一样高（64dp）**：
        // 顶栏消失、工具条顶上，图片的可用高度和平时一模一样。
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(TOP_BAR_HEIGHT)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 顶部**一律用图标**（用户 2026-09-17："遮罩模式的顶部都变成图标，不用文字"）
                MaskIconToggle(BrushIcon, t("mask.draw"), selected = !state.maskErasing) {
                    state.setMaskEraser(false)
                }
                MaskIconToggle(EraserIcon, t("mask.erase"), selected = state.maskErasing) {
                    state.setMaskEraser(true)
                }
                MaskIconToggle(MaskSelectIcon, t("mask.focus"), selected = state.maskFocusTool) {
                    state.chooseMaskFocusTool(!state.maskFocusTool)
                }
                if (state.maskFocusRect != null) {
                    IconButton(onClick = { state.clearMaskFocus() }) {
                        Icon(Icons.Default.Close, contentDescription = t("mask.focusClear"))
                    }
                }
                IconButton(onClick = { state.undoMask() }, enabled = state.maskCanUndo) {
                    Icon(UndoIcon, contentDescription = t("mask.undo"))
                }

                // 聚焦模式下这根滑杆的位置正好是官方的「Minimum Context Area」
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

/**
 * **聚焦框的叠加层**：虚线框 + 外面那圈「最小上下文」（半透明红，只做参考、不重绘）。
 *
 * 坐标口径和 [MaskStrokeOverlay] 一样：**图片当前实际显示的矩形**（`Fit` 之后再套
 * 画布的缩放 / 平移，见 [shownImageRect]）+ 原图像素换算 —— 所以放大、拖动之后
 * 框和笔迹一样**跟着图走**。
 */
@Composable
internal fun MaskFocusOverlay(
    focus: SelectionShape.Rect,
    contextPixels: Int,
    sourceWidth: Int,
    sourceHeight: Int,
    area: IntSize,
    /** 画布当前的缩放 / 平移，口径同 [MaskStrokeOverlay]。 */
    scale: Float = 1f,
    offset: Offset = Offset.Zero,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val contextTint = Color(0xFFE53935).copy(alpha = 0.16f)
    val shadow = Color(0xCC000000)
    val aspect = if (sourceHeight > 0) sourceWidth.toFloat() / sourceHeight.toFloat() else 0f
    Canvas(modifier) {
        val rect = shownImageRect(area, aspect, scale, offset)
        if (rect.width <= 0f || rect.height <= 0f || sourceWidth <= 0) return@Canvas
        val frame = SelectionGeometry.bounds(focus, sourceWidth, sourceHeight) ?: return@Canvas
        // ⚠️ 这里是**屏幕像素 / 原图像素**的比例（= Fit 比例 × 画布缩放），和上面的 `scale`
        //    （画布缩放倍率）不是一回事，别混用 —— 所以另起一个名字。
        val pxScale = rect.width / sourceWidth.toFloat()
        fun screen(x: Int, y: Int) = Offset(rect.left + x * pxScale, rect.top + y * pxScale)

        // 上下文圈（框外那一圈）
        val context = contextPixels.coerceAtLeast(0)
        if (context > 0) {
            val band = EditRect(
                frame.x - context,
                frame.y - context,
                frame.w + context * 2,
                frame.h + context * 2,
            ).clampTo(sourceWidth, sourceHeight)
            if (!band.isEmpty) {
                val topLeft = screen(band.x, band.y)
                drawRect(
                    color = contextTint,
                    topLeft = topLeft,
                    size = Size(band.w * pxScale, band.h * pxScale),
                )
            }
        }

        // 框本体：黑描边 + 虚线
        val topLeft = screen(frame.x, frame.y)
        val size = Size(frame.w * pxScale, frame.h * pxScale)
        drawRect(color = shadow, topLeft = topLeft, size = size, style = Stroke(width = 3f))
        drawRect(
            color = accent,
            topLeft = topLeft,
            size = size,
            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
        )
    }
}

/** 图片按 `ContentScale.Fit` 在给定区域里的实际矩形。 */
internal fun fitRect(area: IntSize, aspect: Float): Rect {    if (area.width <= 0 || area.height <= 0 || aspect <= 0f) return Rect.Zero
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

/**
 * 图片在屏幕上**真实占据**的那块矩形。
 *
 * = `ContentScale.Fit` 出来的矩形（[fitRect]），再套上画布的**缩放与平移** ——
 * 也就是生成页预览层 `graphicsLayer { scaleX/scaleY/translationX/translationY }`
 * 实际把图放大 / 挪到哪儿了。
 *
 * 变换口径必须和那一层**逐字一致**：`graphicsLayer` 的缩放原点默认是**图层中心**
 * （图层是填满可视区的 Box，所以中心 = `area` 的中心），于是
 * ```
 * screen = center + (content - center) * scale + offset
 * ```
 * 生成页的手势层（滚轮缩放的锚点公式、`shown` 判定）用的就是这一条，所以两边永远自洽。
 *
 * 为什么要有它（用户 2026-09-22）：「遮罩模式…遮罩和聚焦框跟随图片」——
 * 笔迹 / 聚焦框的坐标换算与绘制都得按**这块矩形**算：手指按在图上哪个像素，
 * 涂出来就落在哪个像素，放大与拖动之后也照样对得上。
 * 早先它们按"未缩放的 Fit 矩形"算，图一放大就整体错位。
 */
internal fun shownImageRect(
    area: IntSize,
    aspect: Float,
    scale: Float,
    offset: Offset,
): Rect {
    val base = fitRect(area, aspect)
    if (base.width <= 0f || base.height <= 0f) return Rect.Zero
    val center = Offset(area.width / 2f, area.height / 2f)
    return Rect(
        offset = center + (base.topLeft - center) * scale + offset,
        size = base.size * scale,
    )
}

/**
 * **宽高比的只读标签**（`3:1` / `1:3` / `13:19` …）——聚焦尺寸旁边那一小截。
 *
 * 为什么拿**框的原始像素**来算、而不是拿 `InpaintSize.forRect` 算出来的目标尺寸：
 * 目标尺寸被"保 64"退过档（3:1 的框 → `1792×576`，约成 28:9），直接显示会变成 `28:9`，
 * 而用户要的是「我框了个 3:1，比例保住了 ✓」那句话（用户 2026-09-26 的例子就是 `3:1 ✓`）。
 *
 * 规则：先按最大公约数约分，两边都不超过 16 就直接写 `a:b`；否则退回小数（`1:1.17`）——
 * 免得出现 `417:487` 这种看不出比例的标签。
 */
internal fun aspectRatioLabel(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return ""
    var a = width
    var b = height
    while (b != 0) {
        val t = a % b
        a = b
        b = t
    }
    val g = if (a <= 0) 1 else a
    val rw = width / g
    val rh = height / g
    return if (rw <= 16 && rh <= 16) {
        "$rw:$rh"
    } else if (width >= height) {
        "%.2f:1".format(width.toDouble() / height.toDouble())
    } else {
        "1:%.2f".format(height.toDouble() / width.toDouble())
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

/** 工具条上的**图标开关**（遮罩模式顶部只留图标，2026-09-17 用户要求）。 */
@Composable
private fun MaskIconToggle(
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconToggleButton(checked = selected, onCheckedChange = { onClick() }) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}