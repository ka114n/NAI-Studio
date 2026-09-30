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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import com.kallan.naistudio.platform.LocalUiHost
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
    /** 「另存为…」：把这张图拷到用户选定的位置（两个平台都是系统对话框/选择器）。 */
    onSaveAs: () -> Unit,
    onUseAsI2i: () -> Unit,
    /** 「使用官方放大放大图像」：对这张图跑一次 NovelAI 官方 2× 放大（付费）。 */
    onOfficialUpscale: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val params = remember(item.id) { GenerateParamsCodec.fromJson(item.params) }
    var copied by remember { mutableStateOf<String?>(null) }
    // 「跳转到文件管理器」：把图片所在目录当 `file://` 交给系统（目录取不到就不显示这一行）
    val uiHost = LocalUiHost.current
    val folderUri = remember(item.id) {
        runCatching {
            java.io.File(item.filePath).parentFile?.toURI()?.toString()
        }.getOrNull()
    }
    val onOpenInFileManager: () -> Unit = {
        folderUri?.let { uiHost.openUrl(it) }
    }

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
                // 反推提示词已经搬到「工具」页（选图 → 选模式 → 开始反推），图库这里只留放大/图生图
                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onReuse) { Text(t("gallery.reuseParams")) }
                    TextButton(onClick = onRename) { Text(t("gallery.rename")) }
                    // 「另存为…」：手机走 SAF 建文件、电脑走"另存为"对话框。
                    // 图库里的图躺在 App 私有目录里，用户想"存到桌面/发给别人"总得有个入口。
                    TextButton(onClick = onSaveAs) { Text(t("gallery.saveAs")) }
                }
            }
            // ⚠️ 原来这里还有一颗「跳转到文件管理器」—— 用户 2026-09-19 让**去掉**：
            // 它挪到"图片右键那个小窗口"里去了（见 `GalleryItemContextMenu`），
            // 弹窗这一层只留"参数相关"的动作。
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
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(10.dp),
        ) {
            Text(
                text.ifBlank { "（空）" },
                style = MaterialTheme.typography.bodySmall,
                color = if (text.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
