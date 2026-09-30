package com.kallan.naistudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp

/**
 * **快捷键设置对话框**（用户 2026-09-19：「在帮助里添加一个快捷键选项，可以改变各个功能的快捷键」）。
 *
 * 一行一个动作：左边是动作名，右边是当前键串（改过的标一下「已改」）。
 * 点一行进入"采集"状态 —— 这时**按什么就绑什么**（Esc 取消、Backspace 恢复默认）。
 *
 * 采集用 `onPreviewKeyEvent` + `focusable`：焦点在那一行上，按键先被它吃掉，
 * 不会顺手触发别的功能（这一步是**预览阶段**，早于入口的 `onKeyEvent`）。
 */
@Composable
fun ShortcutSettingsDialog(
    bindings: ShortcutBindings,
    t: (String) -> String,
    onDismiss: () -> Unit,
) {
    var capturing by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    // 进入采集状态就把焦点抢过来（不然按键落到别处去）
    LaunchedEffect(capturing) {
        if (capturing != null) runCatching { focusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("shortcuts.title")) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    t("shortcuts.hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(1.dp))
                APP_SHORTCUTS.forEach { spec ->
                    val isCapturing = capturing == spec.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { capturing = if (isCapturing) null else spec.id }
                            .then(
                                if (isCapturing) {
                                    Modifier
                                        .focusRequester(focusRequester)
                                        .focusable()
                                        .onPreviewKeyEvent { event ->
                                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                            when (event.key) {
                                                Key.Escape -> {
                                                    capturing = null
                                                    true
                                                }
                                                Key.Backspace, Key.Delete -> {
                                                    bindings.reset(spec.id)
                                                    capturing = null
                                                    true
                                                }
                                                else -> {
                                                    val keys = formatEvent(event)
                                                    if (keys != null) {
                                                        bindings.setKeys(spec.id, keys)
                                                        capturing = null
                                                    }
                                                    true
                                                }
                                            }
                                        }
                                } else {
                                    Modifier
                                },
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            t(spec.labelKey),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (bindings.isCustom(spec.id)) {
                            Text(
                                t("shortcuts.custom"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        Text(
                            text = if (isCapturing) t("shortcuts.press") else bindings.keysFor(spec.id),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCapturing) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .border(
                                    1.dp,
                                    if (isCapturing) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    },
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(t("common.confirm")) }
        },
        dismissButton = {
            TextButton(onClick = { bindings.resetAll() }) { Text(t("shortcuts.resetAll")) }
        },
    )
}
