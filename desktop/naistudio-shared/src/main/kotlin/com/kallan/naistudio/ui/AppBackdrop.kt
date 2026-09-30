package com.kallan.naistudio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * **电脑端铺在窗口最底下的那层"磨砂壁纸"**（`DesktopBackdrop` 生成的那张 192 宽、糊过的位图）
 * 加上它铺开时的窗口尺寸。
 *
 * 手机端永远是 `null`（没有这层），所以共用树里的代码只能"有就用"。
 */
data class AppBackdrop(
    val bitmap: ImageBitmap,
    /** 这层壁纸铺开时的**窗口客户区**尺寸（Crop 的对齐基准）。 */
    val windowSize: IntSize,
)

/**
 * 提供 [AppBackdrop]。只有电脑端 `Main.kt` 会 provide；默认 `null`。
 */
val LocalAppBackdrop = staticCompositionLocalOf<AppBackdrop?> { null }

/**
 * **让这一块自己画出"窗口级磨砂壁纸"在它那个位置上的部分**。
 *
 * ## 为什么需要它（用户 2026-09-19）
 *
 * 用户要的是"提示词栏 / 底栏和**常驻侧栏**一样的毛玻璃"。侧栏之所以像玻璃，是因为它背后
 * 就是这层**已经糊过的壁纸**；而提示词栏 / 底栏背后是页面底色（一层把壁纸洗成近白的
 * 半透明底），叠一层半透明就变白板了。
 *
 * 之前的做法是"把页面底色去掉"，结果踩了两个坑：
 *  · `Scaffold(containerColor = Color.Transparent)` 让 M3 的 `contentColorFor` 算不出内容色，
 *    退回 **黑**色 → 深色模式下不指定 tint 的图标全看不见；
 *  · 页面那层主题色调没了 → 深浅色切换几乎看不出变化，扩散动画像失效。
 *
 * 现在的做法是**把页面底色还给页面**，让这两块面板**自己按窗口对齐重画一遍壁纸** ——
 * 面板背后于是和侧栏一模一样（浮动拖到哪儿，壁纸都跟着窗口对得上，像真的玻璃）。
 *
 * ## 口径
 *
 *  · 壁纸是按**窗口等比铺满（Crop）再居中**画的（和 `Main.kt` 里那层 `ContentScale.Crop`
 *    完全一致），所以这里用 `positionInWindow()` 把自己的位置减掉就能对齐；
 *  · ⚠️ 只有这一层用**很小**的位图（192 宽）放大 → 天然就是"糊"的，不需要再做模糊；
 *  · 修 pi 链顺序：**先 `.clip(...)` 再 `.glassBackdrop()`**，圆角才会裁到这一层。
 */
@Composable
fun Modifier.glassBackdrop(): Modifier {
    val backdrop = LocalAppBackdrop.current ?: return this
    val density = LocalDensity.current
    var positionInWindow by remember { mutableStateOf(Offset.Zero) }

    return this
        .onGloballyPositioned { positionInWindow = it.positionInWindow() }
        .drawBehind {
            val winWidth = backdrop.windowSize.width.toFloat()
            val winHeight = backdrop.windowSize.height.toFloat()
            if (winWidth <= 0f || winHeight <= 0f) return@drawBehind
            val imageWidth = backdrop.bitmap.width.toFloat()
            val imageHeight = backdrop.bitmap.height.toFloat()
            if (imageWidth <= 0f || imageHeight <= 0f) return@drawBehind

            // 和 `Image(ContentScale.Crop)` 同一套算法：等比放大到盖住窗口，然后居中
            val scale = maxOf(winWidth / imageWidth, winHeight / imageHeight)
            val drawWidth = imageWidth * scale
            val drawHeight = imageHeight * scale
            val left = (winWidth - drawWidth) / 2f - positionInWindow.x
            val top = (winHeight - drawHeight) / 2f - positionInWindow.y

            drawImage(
                image = backdrop.bitmap,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(backdrop.bitmap.width, backdrop.bitmap.height),
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(drawWidth.roundToInt(), drawHeight.roundToInt()),
            )
            // density 只是为了让 Lint 看见它被用了（位置换算不涉及 dp）
            @Suppress("UNUSED_EXPRESSION")
            density
        }
}
