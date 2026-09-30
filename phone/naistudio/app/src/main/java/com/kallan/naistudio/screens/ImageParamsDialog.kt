package com.kallan.naistudio.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import com.kallan.naistudio.ui.RefAlertDialog as AlertDialog
import com.kallan.naistudio.ui.RefButton as Button
import androidx.compose.material3.Icon
import com.kallan.naistudio.ui.RefIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.kallan.naistudio.ui.RefOutlinedButton as OutlinedButton
import androidx.compose.material3.Text
import com.kallan.naistudio.ui.RefTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.models.GenerateParamsCodec
import com.kallan.naistudio.models.HistoryItem
import kotlinx.coroutines.delay

/**
 * 图片参数界面（图库里**长按**图片打开）。
 *
 * 版式（按用户要求）：
 *  · 顶部：日期 + 右上角**垃圾桶**（删掉这张图，要二次确认）+ 关闭
 *  · 中间：**风格提示词 / 正面提示词 / 负面提示词**三个方框，每个方框右上角一个「复制」
 *  · 底部：「其他参数」——模型 / 尺寸 / seed / 采样器 / 步数 / CFG / 文件名 / 类型
 *  · 最下面：复用参数 / 重命名
 *
 * 三个提示词从 `item.params`（生成当时的参数快照）解出来，所以历史图也能看到原样内容。
 */
@Composable
fun ImageParamsDialog(
    item: HistoryItem,
    t: (String) -> String,
    onDismiss: () -> Unit,
    onReuse: () -> Unit,
    onRename: () -> Unit,
    onUseAsI2i: () -> Unit,
    /** 「使用官方放大放大图像」：对这张图跑一次 NovelAI 官方 2× 放大（付费）。 */
    onOfficialUpscale: () -> Unit,
    /**
     * 这张图是不是**画布编辑另存出来的**（用户 2026-09-26：「改变的图…可以重置修改」）。
     * 只有为 true 时才显示那颗「重置修改」—— 普通生成出来的图没有"原图"可退。
     */
    canResetEdit: Boolean = false,
    /** 丢掉这次画布编辑：派生图和这条记录一起删掉、底图退回原图。 */
    onResetEdit: () -> Unit = {},
) {
    val clipboard = LocalClipboardManager.current
    val params = remember(item.id) { GenerateParamsCodec.fromJson(item.params) }
    var copied by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copied) {
        if (copied != null) {
            delay(1500)
            copied = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(item.date, style = MaterialTheme.typography.titleMedium)
                // 垃圾桶不在这里：按用户要求放在图库页右上角、横杠排序按钮左边
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = t("common.cancel"))
                }
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PromptBox(
                    label = t("generate.stylePrompt"),
                    text = params.stylePrompt,
                    copied = copied,
                    onCopy = { value ->
                        clipboard.setText(AnnotatedString(value))
                        copied = "style"
                    },
                    copyKey = "style",
                )
                PromptBox(
                    label = t("generate.positivePrompt"),
                    text = params.positivePrompt.ifBlank { item.prompt },
                    copied = copied,
                    onCopy = { value ->
                        clipboard.setText(AnnotatedString(value))
                        copied = "positive"
                    },
                    copyKey = "positive",
                )
                PromptBox(
                    label = t("generate.negativePrompt"),
                    text = params.negativePrompt,
                    copied = copied,
                    onCopy = { value ->
                        clipboard.setText(AnnotatedString(value))
                        copied = "negative"
                    },
                    copyKey = "negative",
                )

                Text("其他参数", style = MaterialTheme.typography.labelMedium)
                Text(
                    "模型：${item.model}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "尺寸：${item.width}×${item.height}　seed：${item.seed}　类型：${item.feature}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "采样器：${params.sampler}　步数：${params.steps}　" +
                        "CFG：${params.cfgScale}　Rescale：${params.cfgRescale}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "文件名前缀：${params.fileNamePrefix.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    item.filePath.substringAfterLast('/'),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        // 底部按钮**全部放在同一个 Column 里**（而不是一半放 confirm、一半放 dismiss）：
        // 早前把「复用参数 / 重命名」放 dismiss 槽时，M3 的按钮区会把两个槽并排/换行混排，
        // 结果这两个文字按钮和上面的胶囊不在一条基线上，看起来就是"字体错位"。
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                Button(
                    onClick = onUseAsI2i,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(t("gallery.useAsI2i"))
                }
                OutlinedButton(
                    onClick = onOfficialUpscale,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    Text(t("upscale.galleryButton"))
                }
                // ⚠️ 用户 2026-09-26：「画布编辑器…点击完成后自动多保存一份，原图不动，
                //    改变的图多一份，**改变的图片在没有清后台的情况下可以重置修改**」——
                //    这一颗只在**它确实是画布另存出来的**时候出现（[canResetEdit]）。
                if (canResetEdit) {
                    OutlinedButton(
                        onClick = onResetEdit,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) {
                        Text(t("gallery.resetEdit"))
                    }
                    Text(
                        t("gallery.resetEditHint"),
                        style = MaterialTheme.typography.labelSmall,
                        color = com.kallan.naistudio.ui.LocalRef.current.muted,
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                    )
                }
                // 反推提示词已经搬到「工具」页（选图 → 选模式 → 开始反推），图库这里只留放大/图生图
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onReuse) { Text(t("gallery.reuseParams")) }
                    TextButton(onClick = onRename) { Text(t("gallery.rename")) }
                }
            }
        },
        dismissButton = {},
    )
}

/** 一个只读提示词方框：右上角是「复制 / 已复制」。 */
@Composable
private fun PromptBox(
    label: String,
    text: String,
    copied: String?,
    copyKey: String,
    onCopy: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            TextButton(
                onClick = { onCopy(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(if (copied == copyKey) "已复制" else "复制")
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(com.kallan.naistudio.ui.LocalRef.current.subtle.copy(alpha = 0.4f))
                .padding(10.dp),
        ) {
            Text(
                text.ifBlank { "（空）" },
                style = MaterialTheme.typography.bodySmall,
                color = if (text.isBlank()) {
                    com.kallan.naistudio.ui.LocalRef.current.muted
                } else {
                    com.kallan.naistudio.ui.LocalRef.current.text
                },
            )
        }
    }
}
