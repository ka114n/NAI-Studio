package com.kallan.naistudio.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.hypot
import kotlin.math.max

/**
 * 一次「圆形扩散」主题切换的状态（用户 2026-09-16 要求）。
 *
 * 点深/浅色开关时，**新主题从按钮位置像水波一样铺满整屏**，而不是整屏"啪"地换色。
 *
 * ## 为什么必须带一张截图
 *
 * 要做到"圆的里侧是新主题、外侧是旧主题"，就得同时存在**两份画面**。
 * 但 Compose 一改主题，整棵树立刻重组成新主题，旧画面当场就没了 —— 除非把它**截下来**。
 * 两份都实时渲染是不行的：`StudioShell` 会被组合两次（两份 pager、两个 BackHandler、
 * 两张 sheet），副作用会打架。截图是死的，没有任何副作用。
 *
 * [snapshot] 就是**切换前**那一帧的整窗截图，[originX] / [originY] 是按钮中心
 * （窗口坐标 px），也就是水波的圆心。
 */
data class ThemeWipe(
    val snapshot: ImageBitmap,
    val originX: Float,
    val originY: Float,
)

/**
 * 「换成深色 / 浅色」的统一入口，由 `MainActivity` 提供。
 *
 * 参数：圆心（窗口坐标 px）、目标是否深色。调用方只管报圆心，抓屏和动画都在实现里。
 *
 * 默认值是空实现，所以任何没套到 Provider 的地方（截图测试之类）点了也不炸。
 */
val LocalThemeSwitch = staticCompositionLocalOf<(Offset, Boolean) -> Unit> { { _, _ -> } }

/** 「换配色方案」的统一入口（圆心 + 方案 key），动画同 [LocalThemeSwitch]。 */
val LocalSchemeSwitch = staticCompositionLocalOf<(Offset, String) -> Unit> { { _, _ -> } }

/**
 * 抓当前**整窗**一帧。
 *
 * ## 为什么不用 `view.draw(Canvas)`
 *
 * 那是在往**软件** Canvas 上回放硬件加速的 View：Compose 的内容是 RenderNode 画的，
 * 这么回放经常拿到空白或残缺的结果。`PixelCopy` 是系统提供的、专门拷"实际已经上屏的
 * 那一帧"的接口，edge-to-edge 下连状态栏那一条也一起抓到 —— 就它了。
 *
 * @return `false` = 没抓起来（尺寸异常 / OOM / 系统拒绝）。调用方应当**直接切主题、
 *   不播动画** —— 宁可没有动画，也不要退回"纯色圆铺开"那种被用户否掉的观感。
 */
fun captureWindowFrame(window: Window, onCaptured: (Bitmap) -> Unit): Boolean {
    val decor = window.decorView
    val width = decor.width
    val height = decor.height
    if (width <= 0 || height <= 0) return false

    val bitmap = try {
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    } catch (e: OutOfMemoryError) {
        return false
    }
    return try {
        PixelCopy.request(
            window,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) onCaptured(bitmap) else bitmap.recycle()
            },
            Handler(Looper.getMainLooper()),
        )
        true
    } catch (e: Exception) {
        bitmap.recycle()
        false
    }
}

/**
 * 主题切换的**圆形扩散遮罩**。
 *
 * ## 怎么做到"圆里是新主题、圆外是旧主题"
 *
 * 遮罩整张就是**旧主题的截图**，然后在它上面**挖一个越来越大的洞** —— 洞底下露出的是
 * 底下那层已经切成新主题的界面。所以看起来就是"新主题从按钮往外扩散"。
 *
 * 注意是**挖洞**不是画圆：画一个旧主题的圆，那就是旧画面在扩散，方向正好反了。
 * 挖洞用 `BlendMode.Clear`，它必须在一个**离屏图层**里做（`CompositingStrategy.Offscreen`），
 * 否则会把底下那层也一起擦掉。
 *
 * ## 半径怎么算
 *
 * 圆心是按钮中心，所以取"圆心到**最远那个角**的距离"当最大半径 —— 不管按钮在哪儿，
 * 圆一长满必定盖住整屏。
 *
 * ## 时序
 *
 * 主题**已经**切过去了（见 `AppState.startThemeWipe`，它和铺遮罩是同一次状态写入，
 * 所以是同一帧上屏，中间不会闪出新主题）。这里只负责把洞挖大，挖满就收工。
 */
@Composable
fun ThemeWipeOverlay(
    wipe: ThemeWipe,
    onFinished: () -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val progress = remember { Animatable(0f) }

    val maxRadius = remember(size, wipe) {
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val dx = max(wipe.originX, w - wipe.originX)
        val dy = max(wipe.originY, h - wipe.originY)
        hypot(dx, dy)
    }

    LaunchedEffect(wipe, maxRadius) {
        // 尺寸还没量到（第一帧）就先别播，否则会从 0 半径弹一下
        if (maxRadius <= 0f) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            // 450ms + LinearOutSlowIn：起步就快（"扩散"的观感），收尾缓下来。
            // 比铺纯色那版（420ms）略长 —— 整屏换主题比铺一层色"重"一点。
            animationSpec = tween(durationMillis = 450, easing = LinearOutSlowInEasing),
        )
        onFinished()
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            // ⚠️ 必须在离屏图层里挖洞，否则 Clear 会把底下那层（新主题）一起擦掉
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            // 动画期间把触摸全吃掉：否则会点到正在被抠掉的界面
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        // 旧主题截图铺满整屏。位图和 Canvas 都是像素、尺寸一致，实际不缩放；
        // 显式给 src/dst 是为了万一窗口尺寸和截图差一点，也能拉满而不是留白边。
        drawImage(
            image = wipe.snapshot,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(wipe.snapshot.width, wipe.snapshot.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width, size.height),
            filterQuality = FilterQuality.None,
        )
        val radius = maxRadius * progress.value
        if (radius > 0f) {
            drawCircle(
                color = Color.Black,
                radius = radius,
                center = Offset(wipe.originX, wipe.originY),
                blendMode = BlendMode.Clear,
            )
        }
    }
}
