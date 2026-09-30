package com.kallan.naistudio.ui

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
import kotlin.math.roundToInt
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
 *
 * ⚠️ 抓屏那一步是平台相关的（手机 `PixelCopy`），留在手机侧
 * （`naistudio/.../ui/ThemeWipe.kt` 的 `captureWindowFrame`）；这里只有纯 Compose 的那部分，
 * 所以它住在共用树里。
 */
data class ThemeWipe(
    val snapshot: ImageBitmap,
    val originX: Float,
    val originY: Float,
    /**
     * 截图在窗口客户区里的**物理像素**偏移 —— 窗口有一部分在屏幕外（最大化最常见）时非零：
     * 那时只抓得到屏幕里那部分，铺遮罩要按这个偏移摆，不能拉满整屏。
     */
    val snapshotOffsetX: Int = 0,
    val snapshotOffsetY: Int = 0,
    /**
     * 窗口客户区的**物理像素**尺寸（拿它把上面的偏移和截图尺寸换算成 Compose 的逻辑坐标）。
     * `<= 0` 表示"不知道，按整屏铺满"（老行为，手机侧就是这么传的）。
     */
    val windowWidthPx: Int = 0,
    val windowHeightPx: Int = 0,
)

/**
 * 「换成深色 / 浅色」的统一入口，由 `MainActivity` 提供。
 *
 * 参数：圆心（窗口坐标 px）、目标是否深色。调用方只管报圆心，抓屏和动画都在实现里。
 *
 * 默认值是空实现，所以任何没套到 Provider 的地方（截图测试之类）点了也不炸。
 */
val LocalThemeSwitch = staticCompositionLocalOf<(Offset, Boolean) -> Unit> { { _, _ -> } }

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
        // ⚠️ 2026-09-19：用户报"动画失效"时先看 `%APPDATA%\NAI Studio\app.log` 这几行 ——
        // 有 start 没 finished = 播放被打断；一行都没有 = 遮罩根本没铺上（抓屏那步就失败了）。
        com.kallan.naistudio.platform.logInfo("ThemeWipe", "start maxRadius=$maxRadius")
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            // 450ms + LinearOutSlowIn：起步就快（"扩散"的观感），收尾缓下来。
            // 比铺纯色那版（420ms）略长 —— 整屏换主题比铺一层色"重"一点。
            animationSpec = tween(durationMillis = 450, easing = LinearOutSlowInEasing),
        )
        com.kallan.naistudio.platform.logInfo("ThemeWipe", "finished")
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
        //
        // ⚠️ 2026-09-19：窗口**有一部分在屏幕外**时（最大化），截图只是屏幕里那部分 ——
        // 这时必须**按偏移摆**，不能拉满整屏（拉满会把画面放大并错位）。
        // 物理像素 → 逻辑像素用「Canvas 宽度 / 窗口物理宽度」这一个比例换算。
        val scale = when {
            wipe.windowWidthPx > 0 && size.width > 0 ->
                size.width.toFloat() / wipe.windowWidthPx.toFloat()

            wipe.snapshot.width > 0 && size.width > 0 ->
                size.width.toFloat() / wipe.snapshot.width.toFloat()

            else -> 1f
        }
        drawImage(
            image = wipe.snapshot,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(wipe.snapshot.width, wipe.snapshot.height),
            dstOffset = IntOffset(
                (wipe.snapshotOffsetX * scale).roundToInt(),
                (wipe.snapshotOffsetY * scale).roundToInt(),
            ),
            dstSize = IntSize(
                (wipe.snapshot.width * scale).roundToInt(),
                (wipe.snapshot.height * scale).roundToInt(),
            ),
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
