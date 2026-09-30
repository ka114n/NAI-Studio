package com.kallan.naistudio.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * **右键点击**（鼠标副键）。回调给的是**相对本节点的位置**，方便以后拿它定位上下文菜单。
 *
 * 为什么不用现成的：Compose 的 `clickable` / `combinedClickable` 只管主键和长按 ——
 * 「长按」在手机上是对的动作，但**鼠标右键在电脑上是另一套习惯**，两者不该混为一谈
 *（长按在桌面要么没有、要么被识别成拖拽起手）。
 *
 * ## 几个实现细节
 *
 * · 只在 `Press`（按下）且 `isSecondaryPressed` 时才触发，**并且把这一笔事件消费掉** ——
 *   不然底下的主键点击逻辑可能也跟着响应，出现"点右键等于点左键"；
 * · 手机上 `isSecondaryPressed` 一直是 false（触摸不产生副键），所以共用代码里加上它
 *   **对手机零影响**；
 * · 用 [composed] + `rememberUpdatedState` 接回调：本文件是共享的顶层扩展函数，
 *   没有自己的 `remember` 作用域，而直接把 lambda 当 `pointerInput` 的 key 会导致
 *   每次重组都重启手势检测（按下的那一刻被打断）。
 */
fun Modifier.onSecondaryClick(onClick: (Offset) -> Unit): Modifier = composed {
    val current by rememberUpdatedState(onClick)
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Press) continue
                if (!event.buttons.isSecondaryPressed) continue
                val position = event.changes.firstOrNull()?.position ?: continue
                current(position)
                event.changes.forEach { it.consume() }
            }
        }
    }
}
