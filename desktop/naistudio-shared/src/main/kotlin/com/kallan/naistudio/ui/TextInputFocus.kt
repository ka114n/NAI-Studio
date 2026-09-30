package com.kallan.naistudio.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import com.kallan.naistudio.state.AppState

/**
 * **给一个文本输入框挂上"我在拿着焦点"的登记**（用户 2026-09-22：「去除空格的焦点击功能」）。
 *
 * 用法：把本修饰符**挂到那个输入框自己的 `modifier` 上**（不是外面包一层 Box —— 要的是
 * 这个框自己的焦点回调）：
 *
 * ```kotlin
 * OutlinedTextField(
 *     value = params.fileNamePrefix,
 *     onValueChange = { … },
 *     modifier = Modifier.fillMaxWidth().trackTextInputFocus(state, "generate.fileNamePrefix"),
 * )
 * ```
 *
 * ## 为什么非登记不可
 *
 * 入口（`Main.kt` 的 `onPreviewKeyEvent`）现在**在预览阶段就把空格吃掉**（没有输入框拿焦点时）——
 * 那是唯一能抢在焦点控件前面的位置：Compose 的 `clickable` 会把 Space 的**按下**当
 * `PressInteraction`、**抬起**当 `onClick`，而鼠标点过的按钮会一直拿着焦点，于是"按住空格拖图"
 * 顺手就把那颗按钮又点了一次。吃掉空格之后按钮就再也收不到它，空格只剩「按住 = 拖图」。
 *
 * 但吃空格不能无条件：**输入框里必须还能打出空格**。所以闸门是 [AppState.textInputFocused]，
 * 而它靠每个输入框在焦点变化时报上来 —— 漏登记的框，在里面按空格会打不出空格。
 *
 * ⚠️ 因此：**以后新加任何文本输入框，都要挂上这个修饰符**。纯数字 / 十六进制这类
 * "反正也打不出空格"的框挂了也无害（它们本来就会把空格过滤掉），统一挂上最省心。
 *
 * @param state 共用状态（标记住在 `AppState` 里，和画布编辑器那份是同一个）。
 * @param key 这个框的**稳定且唯一**的标识。同一个框每次都要传同一个串；两个框不能重名
 *   （重名会让"一个框失焦"顺手删掉另一个框的那条）。
 *   建议用"页面.字段"的写法，例如 `"generate.fileNamePrefix"` / `"session.search"`。
 */
fun Modifier.trackTextInputFocus(state: AppState, key: String): Modifier =
    this.onFocusChanged { state.reportTextInputFocus(key, it.isFocused) }
