package com.kallan.naistudio.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * 窗口尺寸分类。
 *
 * **断点与判定方式与参考实现一致** `mobile/lib/ui/studio_shell.dart`：
 * `tablet = 600`、`wideTablet = 1180`。原文注释大意：
 * 手机横屏也很容易超过 600dp，但它的短边仍是手机尺寸；把它当平板会得到局促的侧栏
 * 布局和不可靠的点击区。所以**按短边分类，不是按宽**。
 */
enum class WindowClass { PHONE, TABLET, WIDE_TABLET }

const val TABLET_BREAKPOINT = 600
const val WIDE_TABLET_BREAKPOINT = 1180

fun classifyWindow(width: Dp, height: Dp): WindowClass {
    val shortSide = minOf(width, height)
    return when {
        shortSide < TABLET_BREAKPOINT.dp -> WindowClass.PHONE
        width >= WIDE_TABLET_BREAKPOINT.dp -> WindowClass.WIDE_TABLET
        else -> WindowClass.TABLET
    }
}

fun isLandscape(width: Dp, height: Dp): Boolean = width > height

/**
 * 生成页是否用"预览左 / 参数右"的双栏。
 *
 * 参考实现的条件是 `windowClass != phone || landscapePhone`：
 * 平板和"够宽的横屏手机"都用双栏，但外壳仍按手机分类（横屏手机继续用紧凑底栏，
 * 不切成平板侧栏）。
 */
fun useSplitLayout(width: Dp, height: Dp): Boolean =
    classifyWindow(width, height) != WindowClass.PHONE || isLandscape(width, height)

/**
 * **当前窗口尺寸**（dp）。
 *
 * 为什么要有它：手机工程里有几处直接读 `LocalConfiguration.current.screenWidthDp /
 * screenHeightDp`（图库算列宽、工具页量最大高度、生成页判双栏）。`LocalConfiguration`
 * 是 Android 平台的东西（`Configuration` 类型在 CMP 里根本没有），所以换成这个。
 *
 * 为什么要"量出来"而不是各平台各报一个：量的是**根布局拿到的约束**，
 * 平台无关、写一次两端都对（手机竖屏就是屏宽，电脑就是窗口宽度）。
 *
 * 默认值给个手机竖屏尺寸：没包 [ProvideWindowSize] 也不会崩，只是尺寸不准。
 */
val LocalWindowSize = staticCompositionLocalOf { DpSize(360.dp, 720.dp) }

/**
 * 在**根布局**包一层，把可用尺寸灌进 [LocalWindowSize]。
 *
 * 用法（两个入口各一次）：`ProvideWindowSize { 真界面 }`。
 */
@Composable
fun ProvideWindowSize(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWindowSize provides DpSize(maxWidth, maxHeight)) {
            content()
        }
    }
}
