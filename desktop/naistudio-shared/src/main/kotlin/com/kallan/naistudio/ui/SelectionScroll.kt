package com.kallan.naistudio.ui

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlinx.coroutines.flow.collect

/**
 * **长按选词、把选择手柄往下拖的时候，画面跟着手指走。**
 *
 * ## 解决的是什么
 *
 * 用户报的：正面提示词里长按选词，把选词手柄往下拉，**画面不跟随** —— 手柄和选中的文字
 * 一起跑到可见区外面，就再也够不着了。
 *
 * 根因和「回车之后光标不跟随」是同一个（细节见 `GenerateScreen.PromptTextField` 的注释）：
 * 这类输入框**高度不受限**（`maxLines = Int.MAX_VALUE`，高度随内容长），它内部那个可滚动
 * 节点 `maxValue` 恒为 0 —— 输入框自己算出来的"让这一行可见"落不到外面，被它吃掉就没了。
 *
 * ## 怎么解决
 *
 * 不去碰输入框的文本与选区：那要么把每个框都改成 `TextFieldValue` 自己维护选区，要么自己
 * 算光标矩形，两种都很脆、还得逐个框改状态管理。
 *
 * 改成**在输入框外层看一眼手指**：只要有一根手指按着并且在动，就把"手指所在的那一点"请求
 * 进可见区 —— 祖先的滚动容器会接住它。手指往下拉，内容就跟着往下滚，输入框按手指的新位置
 * 继续扩选，于是"手柄拖到哪，画面跟到哪"。
 *
 * ## 为什么不会和"拖动滚动"打架
 *
 * 请求的矩形就是**手指那一个点**（1×1），不是"手指往下多少 dp"。于是：
 *  · 手指还在滚动容器的可见区里（正常拖动滚动就是这样）→ 请求的落点和当前滚动位置一致，
 *    是**空操作**，一次都不会滚；
 *  · 只有手指整个跑到可见区**之外**（拖手柄拖出边、拖到下面那排按钮上）才会真的触发滚动。
 *
 * 所以它只在"画面该跟却没跟"的时候才动手，正常滚动的手感一点没变。
 *
 * ## 用法
 *
 * 挂在**高度不受限的多行输入框**上（挂在输入框自己的 modifier 上，或者包着它的那个 Box 上）。
 * 高度受控的框（比如 `weight(1f)` 撑满面板的、或者写了 `maxLines = 12` 的）**不需要**：
 * 它们自己内部就能滚，Compose 原生的"选到边就滚"是好使的。
 */
@Composable
fun Modifier.followFingerForSelection(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    // 手指位置先落到 state，再转到指针回调**外面**去请求滚动：
    // `bringIntoView` 是挂起函数（要抢滚动的互斥锁），直接在事件分发里调会把分发堵住。
    var finger by remember { mutableStateOf<Offset?>(null) }
    LaunchedEffect(requester) {
        snapshotFlow { finger }.collect { position ->
            if (position != null) {
                requester.bringIntoView(Rect(offset = position, size = Size(1f, 1f)))
            }
        }
    }
    return this
        .bringIntoViewRequester(requester)
        // 只**看**、绝不 consume：输入框自己的长按选词、拖手柄、滚动必须原样收到事件。
        // 用 `Initial` 阶段：在输入框之前先看一眼（看了就走，不影响它）。
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.firstOrNull { it.pressed }
                    if (pressed == null) {
                        // 手抬起来了：清掉，免得下次重新布局时拿一个旧位置去请求滚动
                        finger = null
                    } else if (pressed.positionChanged()) {
                        finger = pressed.position
                    }
                }
            }
        }
}
