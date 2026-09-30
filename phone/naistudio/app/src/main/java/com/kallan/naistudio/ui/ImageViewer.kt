package com.kallan.naistudio.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import com.kallan.naistudio.ui.RefIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.kallan.naistudio.ui.RefTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kallan.naistudio.services.MaskCodec
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** 查看器里的一项：给 [path] 就按文件显示（顺带用缩略图缓存），给 [bitmap] 就直接画（流式预览帧）。 */
data class ViewerImage(
    val path: String? = null,
    val bitmap: Bitmap? = null,
)

/** 双击放大到的倍数。 */
private const val DOUBLE_TAP_SCALE = 2.5f
private const val MAX_SCALE = 6f

/**
 * 全屏图片查看器：黑底铺满、**双指缩放 + 拖动**、**双击放大/还原**，
 * 多张时**左右滑切上一张 / 下一张**，而且用的是和「文生图 ↔ 图库」**同一个 `HorizontalPager`**，
 * 所以切换动画是同款的（跟手 + 吸附）。
 *
 * 手势分工：
 *  · 未放大：单指横向拖动**不消费**，交给 pager 翻页（所以翻页动画原生跟手）
 *  · 放大后：单指拖动变成平移，并且 `userScrollEnabled = false` 关掉翻页
 *  · 双指：缩放 + 平移，围绕捏合中心（`t' = anchor + pan - (anchor - t) * f`）
 *
 * 用 `Dialog(usePlatformDefaultWidth = false)` 是为了覆盖整个界面（含顶部栏与底部栏），
 * 顺带白拿一个能力：系统返回键自动关掉它。
 */
@Composable
fun FullscreenImageViewer(
    images: List<ViewerImage>,
    onDismiss: () -> Unit,
    initialPage: Int = 0,
    onPageChange: (Int) -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    if (images.isEmpty()) return

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, images.lastIndex),
    ) { images.size }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }
    val resetZoom = {
        zoomScale = 1f
        zoomOffset = Offset.Zero
    }

    /**
     * 「是否处于放大状态」—— **必须用 derivedStateOf**。
     *
     * 早前这里（以及 `userScrollEnabled`）直接写 `zoomScale > 1.05f`：那是**在 composition 里读缩放值**，
     * 于是捏合/拖动的**每一帧都重组整个 Dialog 内容**（含 pager 与它组合出来的所有页面），
     * 表现就是"双指放大时图片颤抖、放大后拖动又慢又卡"（真机上踩过两次）。
     * derivedStateOf 只在**布尔真的翻转**时才通知，所以一次手势最多重组两次。
     */
    val zoomed by remember { derivedStateOf { zoomScale > 1.05f } }

    // 翻页后把缩放复位，否则下一张会带着上一张的倍数
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            resetZoom()
            onPageChange(page)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                // ⚠️ **不要**用 `userScrollEnabled = 缩放值 > 1` 来挡翻页：
                // 那会在捏合过程中反复改 pager 的 modifier 结构 → 手势被中断、pager 重新布局，
                // 于是"抖动 → 重组 → 更抖"，表现是又颤又卡（真机上踩过）。
                // 这里恒定开启，改由下面的手势处理器**消费事件**来阻止翻页（消费掉的事件 pager 收不到）。
            ) { page ->
                val image = images[page]
                // 图片的原始像素尺寸：用来按**实际显示矩形**夹取平移量（不是按整个容器）
                val intrinsic = remember(image.path, image.bitmap) {
                    image.bitmap?.let { it.width to it.height }
                        ?: image.path?.let { runCatching { MaskCodec.imageSize(it) }.getOrNull() }
                }
                Box(
                    // ⚠️ **顺序至关重要：手势在前、graphicsLayer 在后。**
                    // Compose 的 modifier 是链式的：写在 `graphicsLayer{}` **后面**的 pointerInput
                    // 处在**被缩放后的坐标空间**里 —— 缩放 2× 时手指移 100px，处理器只读到 50px
                    // （表现为"放大后拖动很慢"）；而捏合时 `span` 所在空间又随缩放一起变，
                    // 于是缩放量算出来忽大忽小（表现为"双指放大时颤抖"）。真机上踩了三轮才定到这条。
                    // 手势放前面 → 读到的是**原始屏幕坐标**，平移 1:1、捏合比例准确。
                    Modifier
                        .fillMaxSize()
                        // 双指缩放 / 放大后平移。未放大时**不消费**单指横向拖动，让 pager 翻页。
                        // 用 Initial 通道：抢在 pager 之前处理并消费，避免 pager 也来抢这一笔手势。
                        .pointerInput(page, images.size, intrinsic) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                // ---- 以"手势开始那一刻"为基准做绝对计算（不逐帧累积）----
                                // 逐帧累积 + 每帧夹取会让误差来回震荡；基准快照下夹取只截断结果，不污染后续帧。
                                var baseScale = zoomScale
                                var baseOffset = zoomOffset
                                var startCentroid = Offset.Zero
                                var startSpan = 0f
                                var started = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    if (pressed.size >= 2) {
                                        val centroid = pressed.fold(Offset.Zero) { acc, change ->
                                            acc + change.position
                                        } / pressed.size.toFloat()
                                        val span = (pressed[0].position - pressed[1].position).getDistance()
                                        if (!started || startSpan <= 0f) {
                                            // 刚变成双指：重新取基准，避免把单指平移的量算进缩放
                                            started = true
                                            baseScale = zoomScale
                                            baseOffset = zoomOffset
                                            startCentroid = centroid
                                            startSpan = span
                                        } else if (span > 0f) {
                                            val newScale = (baseScale * (span / startSpan))
                                                .coerceIn(1f, MAX_SCALE)
                                            val factor = newScale / baseScale
                                            val center = Offset(size.width / 2f, size.height / 2f)
                                            // 让捏合中心下的内容不动，同时跟随中心位移：
                                            // offset' = offset + (c - c0) + (c - center - offset) * (1 - factor)
                                            val raw = baseOffset + (centroid - startCentroid) +
                                                (centroid - center - baseOffset) * (1f - factor)
                                            zoomOffset = clampOffset(
                                                raw,
                                                newScale,
                                                size.width,
                                                size.height,
                                                intrinsic,
                                            )
                                            zoomScale = newScale
                                        }
                                        pressed.forEach { it.consume() }
                                    } else {
                                        // 单指：只有放大之后才是平移（否则让 pager 翻页）
                                        started = false
                                        startSpan = 0f
                                        val change = pressed[0]
                                        if (zoomScale > 1.005f) {
                                            val delta = change.position - change.previousPosition
                                            zoomOffset = clampOffset(
                                                zoomOffset + delta,
                                                zoomScale,
                                                size.width,
                                                size.height,
                                                intrinsic,
                                            )
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                        // 变换只作用在**绘制**上（延迟到绘制阶段的 lambda，不触发重组）
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = zoomOffset.x
                            translationY = zoomOffset.y
                        }
                        // 双击放大 / 还原；**单击图片就退出**（文生图那边点开的大图再点一下就关）。
                        // 这块在 graphicsLayer 之后也不影响正确性：未放大时变换是恒等（坐标一致），
                        // 放大后双击是"复位"，锚点不参与计算。
                        .pointerInput(page, images.size) {
                            detectTapGestures(
                                onTap = { onDismiss() },
                                onDoubleTap = { tap ->
                                    val target = if (zoomScale > 1.05f) 1f else DOUBLE_TAP_SCALE
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val factor = target / zoomScale
                                    val anchor = tap - center
                                    val next = if (target <= 1f) {
                                        Offset.Zero
                                    } else {
                                        clampOffset(
                                            anchor - (anchor - zoomOffset) * factor,
                                            target,
                                            size.width,
                                            size.height,
                                            intrinsic,
                                        )
                                    }
                                    scope.launch {
                                        launch {
                                            animate(zoomScale, target, animationSpec = spring(stiffness = 700f)) { v, _ ->
                                                zoomScale = v
                                            }
                                        }
                                        launch {
                                            animate(zoomOffset.x, next.x, animationSpec = spring(stiffness = 700f)) { v, _ ->
                                                zoomOffset = zoomOffset.copy(x = v)
                                            }
                                        }
                                        launch {
                                            animate(zoomOffset.y, next.y, animationSpec = spring(stiffness = 700f)) { v, _ ->
                                                zoomOffset = zoomOffset.copy(y = v)
                                            }
                                        }
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        image.bitmap != null -> Image(
                            bitmap = image.bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )

                        image.path != null -> FileImage(
                            path = image.path,
                            maxDimension = 2048,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )

                        else -> Text("没有可显示的图片", color = Color.White)
                    }
                }
            }

            // 顶栏：左边关闭，右边是调用方塞进来的操作。要躲开状态栏
            // （dialog 是 decorFitsSystemWindows=false，内容会顶到屏幕最上面）。
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxSize()
                    .statusBarsPadding(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }

            // 注意这里是 `zoomed`（derivedStateOf）而不是直接读 zoomScale —— 否则每帧重组
            if (zoomed) {
                TextButton(
                    onClick = {
                        scope.launch {
                            launch {
                                animate(zoomScale, 1f, animationSpec = spring(stiffness = 700f)) { v, _ ->
                                    zoomScale = v
                                }
                            }
                            launch {
                                animate(zoomOffset.x, 0f, animationSpec = spring(stiffness = 700f)) { v, _ ->
                                    zoomOffset = zoomOffset.copy(x = v)
                                }
                            }
                            launch {
                                animate(zoomOffset.y, 0f, animationSpec = spring(stiffness = 700f)) { v, _ ->
                                    zoomOffset = zoomOffset.copy(y = v)
                                }
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    Text("100%", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/**
 * 把平移量限制在「图片不会被拖出屏幕」的范围内。
 *
 * 按 **ContentScale.Fit 之后的实际显示矩形**算，而不是按整个容器：
 *  · 竖图在横屏/宽容器里左右有留白，按容器算会允许把图片拖进留白里，边界上会和手指打架；
 *  · 缩放后某一边比容器还小时，该轴直接归零（居中），不会出现"能拖动一点点"的抖动。
 */
private fun clampOffset(
    offset: Offset,
    scale: Float,
    width: Int,
    height: Int,
    intrinsic: Pair<Int, Int>?,
): Offset {
    val (iw, ih) = intrinsic ?: (width to height)
    // Fit：等比缩放到容器内
    val fit = minOf(width.toFloat() / iw, height.toFloat() / ih)
    val displayedW = iw * fit * scale
    val displayedH = ih * fit * scale
    val maxX = ((displayedW - width) / 2f).coerceAtLeast(0f)
    val maxY = ((displayedH - height) / 2f).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}
