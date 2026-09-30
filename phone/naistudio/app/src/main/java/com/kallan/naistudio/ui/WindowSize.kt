package com.kallan.naistudio.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 窗口尺寸分类。
 *
 * **断点与判定方式照抄参考实现** `mobile/lib/ui/studio_shell.dart`：
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
