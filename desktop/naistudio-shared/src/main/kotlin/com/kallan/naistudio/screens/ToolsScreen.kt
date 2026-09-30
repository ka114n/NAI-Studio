package com.kallan.naistudio.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.NaiOption
import com.kallan.naistudio.models.ToolCatalog
import com.kallan.naistudio.platform.LocalUiHost
import com.kallan.naistudio.services.PromptRules
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.FileImage
import com.kallan.naistudio.ui.SectionCard
import com.kallan.naistudio.ui.SegmentedCapsule
import kotlinx.coroutines.delay

/**
 * 工具页。
 *
 * 顶部是**工具清单胶囊**（和侧边栏「工具」下拉共用 [ToolCatalog] 一份目录）；
 * 下面渲染当前工具的界面。住在生成页的工具（导演台 / 角色分区）点了会翻回生成页
 * 并把对应面板打开 —— 那边本来就有完整 UI，不在这里抄第二份。
 *
 * 「反推提示词」是从图库搬过来的：图库长按弹窗只留放大/图生图，
 * 反推是"拿一张图产出提示词"的一次性动作，和"这张图的参数/操作"不是一类东西。
 *
 * ⚠️ 2026-09-20：侧边栏点工具**不再翻到这一页**，改成在主窗口里浮出一扇窗口
 *（用户：「不用跳转页面，出现一个和提示词框差不多的新窗口」）。这里只剩"工具页"这一种容器，
 * 界面本体是 [ToolContent]，两处共用。
 */
@Composable
fun ToolsScreen(state: AppState) {
    ToolContent(toolId = state.selectedTool, state = state)
}

/**
 * **一个工具的界面本体** —— 工具页与「工具浮动窗口」**共用这一份**。
 *
 * 用户 2026-09-20：「工具这些功能不用跳转页面，出现一个和提示词框差不多的新窗口」——
 * 窗口那边只是换了个容器装同一个 [ToolContent]，**不抄第二份界面**（抄一份迟早会不一致）。
 *
 * 分派口径：角色图鉴 / 画师超市自带 `LazyVerticalGrid`，**不能**再套进 `verticalScroll`
 *（嵌套滚动会打架），所以这两个各铺满可用区域；其余工具才是一列可滚动的卡片。
 */
@Composable
internal fun ToolContent(toolId: String, state: AppState) {
    val language = state.settings.language
    val t: (String) -> String = { key -> RuntimeText.text(language, key) }

    // 「已复制」提示 1.5 秒后自己消失
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    val clipboard = LocalClipboardManager.current
    // 要画哪个工具：浮动窗口按**它自己的 id** 打开（同一时间可以开着好几扇），工具页按 `selectedTool`。
    // ⚠️ 2026-09-13：工具页顶部那排胶囊**去掉了**（用户要求）—— 每个工具都是独立工具，
    // 只从侧边栏的工具收纳进入；这里也只渲染这一个工具。
    val currentTool = ToolCatalog.byId(toolId)

    when (currentTool.id) {
        // 角色图鉴：自带网格滚动（LazyVerticalGrid），铺满整块可用区域
        ToolCatalog.ANIMADEX -> AnimaDexTool(
            state = state,
            t = t,
            clipboard = clipboard,
            onCopied = { copied = true },
        )

        // 画师超市同理：也是 LazyVerticalGrid，而且还有自己的整块详情
        ToolCatalog.TAGCODEX -> TagCodexTool(
            state = state,
            t = t,
            clipboard = clipboard,
            onCopied = { copied = true },
        )

        else -> Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (currentTool.id) {
                ToolCatalog.METADATA -> MetadataTool(
                    state = state,
                    t = t,
                    clipboard = clipboard,
                    copied = copied,
                    onCopied = { copied = true },
                )
                ToolCatalog.REVERSE -> ReversePromptTool(
                    state = state,
                    t = t,
                    clipboard = clipboard,
                    copied = copied,
                    onCopied = { copied = true },
                )
                else -> SectionCard(t(currentTool.titleKey)) {
                    Text(
                        t(currentTool.hintKey),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 「元数据」这张卡片：把图片里自带的生成参数解析出来。
 *
 * 口径参考 ComfyUI 的 **NAI 元数据加载器** 节点：元数据就写在 PNG 的文本块里，
 * 所以能认 **NovelAI / WebUI(A1111) / ComfyUI** 三类来源（解析逻辑在 `ImageMetadataReader`）。
 */
@Composable
private fun MetadataTool(
    state: AppState,
    t: (String) -> String,
    clipboard: ClipboardManager,
    copied: Boolean,
    onCopied: () -> Unit,
) {
    // 选图走宿主能力（手机 = 系统相册 / Photo Picker，电脑 = 文件对话框）
    val pickImage = LocalUiHost.current.rememberImagePicker { ref ->
        if (ref != null) state.selectMetadataImage(ref)
    }
    val pick: () -> Unit = { pickImage.launch(null) }
    // 「完整元数据」默认收着：大多数时候看解析出来的那几个字段就够了
    var rawOpen by remember { mutableStateOf(false) }
    val meta = state.metadataResult

    SectionCard(t("metadata.title")) {
        Text(
            t("metadata.hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = pick,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (state.metadataImagePath == null) t("metadata.selectImage")
                    else t("metadata.reselect"),
                )
            }
            if (state.metadataImagePath != null) {
                TextButton(onClick = { state.clearMetadataImage() }) {
                    Text(t("metadata.clear"))
                }
            }
        }

        state.metadataImagePath?.let { path ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                FileImage(
                    path = path,
                    maxDimension = 1024,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        when {
            state.metadataBusy -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(t("metadata.parsing"), style = MaterialTheme.typography.bodySmall)
            }

            meta == null && state.metadataImagePath == null -> Text(
                t("metadata.emptyPick"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            meta == null -> Text(
                t("metadata.noData"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                // 来源徽标 + 字段表
                Text(
                    t("metadata.source") + "：" + t("metadata.source.${meta.source.id}"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    meta.fields.forEach { field ->
                        MetadataField(
                            label = field.label,
                            value = field.value,
                            copyLabel = t("common.copy"),
                            onCopy = {
                                clipboard.setText(AnnotatedString(field.value))
                                onCopied()
                            },
                        )
                    }
                }
                if (meta.hasPrompt) {
                    Button(
                        onClick = { state.importMetadataPrompts() },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(t("metadata.importPrompts"))
                    }
                }
                if (meta.params != null) {
                    OutlinedButton(
                        onClick = { state.applyMetadataParams() },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(t("metadata.applyParams"))
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { rawOpen = !rawOpen }) {
                        Text(
                            if (rawOpen) t("metadata.hideRaw") else t("metadata.showRaw"),
                        )
                    }
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(metadataAsText(state, t)))
                            onCopied()
                        },
                    ) {
                        Text(if (copied) t("metadata.copied") else t("metadata.copyAll"))
                    }
                }
                if (rawOpen) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        meta.rawChunks.forEach { (keyword, text) ->
                            Column {
                                Text(
                                    keyword,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(text, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 一行「标签 → 值」，值太长就折行显示，右上角点一下复制这一条。 */
@Composable
private fun MetadataField(
    label: String,
    value: String,
    copyLabel: String,
    onCopy: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onCopy) {
                Text(copyLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 「复制全部」的内容：来源 + 字段表 + 原始块。 */
private fun metadataAsText(state: AppState, t: (String) -> String): String {
    val meta = state.metadataResult ?: return ""
    return buildString {
        append(t("metadata.source")).append(": ").append(t("metadata.source.${meta.source.id}"))
        meta.fields.forEach { field ->
            append('\n').append(field.label).append(": ").append(field.value)
        }
        meta.rawChunks.forEach { (keyword, value) ->
            append("\n\n[").append(keyword).append("]\n").append(value)
        }
    }
}

/**
 * 「反推提示词」这张卡片（工具页唯一的自有工具）。
 *
 * 选图 → 选反推模式 → 开始反推 → 结果框里「复制 / 导入正向提示词」。
 */
@Composable
private fun ReversePromptTool(
    state: AppState,
    t: (String) -> String,
    clipboard: ClipboardManager,
    copied: Boolean,
    onCopied: () -> Unit,
) {
    // 选图：走宿主能力（手机是系统 Photo Picker，不需要读存储权限）
    val pickImage = LocalUiHost.current.rememberImagePicker { ref ->
        if (ref != null) state.selectReverseImage(ref)
    }
    val pick: () -> Unit = { pickImage.launch(null) }

    SectionCard(t("reverse.title")) {
        Text(
            t("reverse.hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 选图：选过之后按钮变「换一张」，右边跟一个「清除」
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = pick,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (state.reverseImagePath == null) t("reverse.selectImage")
                    else t("reverse.reselect"),
                )
            }
            if (state.reverseImagePath != null) {
                TextButton(onClick = { state.clearReverseImage() }) {
                    Text(t("reverse.clear"))
                }
            }
        }

        // 预览：等宽方块，圆角裁掉
        state.reverseImagePath?.let { path ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                FileImage(
                    path = path,
                    maxDimension = 1024,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        // 反推模式：和「设置 → 提示词优化与反推规则」里的反推模式是同一个值
        Text(t("reverse.mode"), style = MaterialTheme.typography.labelLarge)
        SegmentedCapsule(
            options = PromptRules.MODES.map { mode ->
                NaiOption(
                    when (mode) {
                        PromptRules.MODE_NATURAL -> t("mode.natural")
                        PromptRules.MODE_MIXED -> t("mode.mixed")
                        else -> t("mode.tag")
                    },
                    mode,
                )
            },
            selected = state.settings.reverseRuleMode,
            onSelect = { value -> state.setSettings { it.copy(reverseRuleMode = value) } },
        )

        Button(
            onClick = { state.runReverseForTool() },
            shape = RoundedCornerShape(50),
            enabled = state.reverseImagePath != null && !state.reverseBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.reverseBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(t("reverse.running"))
            } else {
                Text(t("reverse.start"))
            }
        }

        // 结果框：只读文本 + 「复制 / 导入正向提示词」
        val result = state.reverseResult
        if (result != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t("reverse.result"), style = MaterialTheme.typography.labelLarge)
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(result))
                        onCopied()
                    },
                ) {
                    Text(if (copied) t("reverse.copied") else t("reverse.copy"))
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                Text(result, style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { state.importReverseResultToPrompt() },
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(t("reverse.import"))
            }
        } else {
            Text(
                t("reverse.noResult"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
