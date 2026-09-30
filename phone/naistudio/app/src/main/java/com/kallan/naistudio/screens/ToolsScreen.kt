package com.kallan.naistudio.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import com.kallan.naistudio.ui.RefButton as Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.NaiOption
import com.kallan.naistudio.models.ToolCatalog
import com.kallan.naistudio.services.PromptRules
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.FileImage
import com.kallan.naistudio.ui.SectionCard
import com.kallan.naistudio.ui.SegmentedCapsule
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.font.FontFamily
import com.kallan.naistudio.ui.LocalRef
import com.kallan.naistudio.ui.RefBtn
import com.kallan.naistudio.ui.RefBtnKind
import com.kallan.naistudio.ui.RefHint
import com.kallan.naistudio.ui.RefSeg
import com.kallan.naistudio.ui.PictureIcon
import com.kallan.naistudio.ui.SparkleIcon

/**
 * 工具页。
 *
 * 顶部是**工具清单胶囊**（和侧边栏「工具」下拉共用 [ToolCatalog] 一份目录）；
 * 下面渲染当前工具的界面。住在生成页的工具（导演台 / 角色分区）点了会翻回生成页
 * 并把对应面板打开 —— 那边本来就有完整 UI，不在这里抄第二份。
 *
 * 「反推提示词」是从图库搬过来的：图库长按弹窗只留放大/图生图，
 * 反推是"拿一张图产出提示词"的一次性动作，和"这张图的参数/操作"不是一类东西。
 */
@Composable
fun ToolsScreen(state: AppState) {
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
    // 当前工具（从侧边栏「工具」分组点进来）。
    // ⚠️ 2026-09-13：工具页顶部那排胶囊**去掉了**（用户要求）—— 每个工具都是独立工具，
    // 只从侧边栏的工具收纳进入；工具页本身只渲染当前工具这张卡片。
    val currentTool = ToolCatalog.byId(state.selectedTool)

    // 角色图鉴自带网格滚动（LazyVerticalGrid），不能套在 verticalScroll 里 —— 嵌套滚动会打架。
    if (currentTool.id == ToolCatalog.ANIMADEX) {
        AnimaDexTool(
            state = state,
            t = t,
            clipboard = clipboard,
            onCopied = { copied = true },
        )
        return
    }

    // 画师超市同理：也是 LazyVerticalGrid，而且还有自己的全屏详情，一样不能套滚动。
    if (currentTool.id == ToolCatalog.TAGCODEX) {
        TagCodexTool(
            state = state,
            t = t,
            clipboard = clipboard,
            onCopied = { copied = true },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 30.dp),
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
            else -> RefHint(t(currentTool.hintKey))
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
    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) state.selectMetadataImage(uri)
    }
    val pick: () -> Unit = {
        pickLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }
    // 「完整元数据」默认收着：大多数时候看解析出来的那几个字段就够了
    var rawOpen by remember { mutableStateOf(false) }
    val meta = state.metadataResult

    // 网页「元数据」：`.pick-img` 选图框 + hint + 一张张 `.kv` 卡片（无外层卡片）
    ToolPickImage(
        path = state.metadataImagePath,
        label = t("metadata.selectImage"),
        onPick = pick,
    )
    if (state.metadataImagePath != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RefBtn(t("metadata.reselect"), onClick = pick, modifier = Modifier.weight(1f), kind = RefBtnKind.Secondary)
            RefBtn(t("metadata.clear"), onClick = { state.clearMetadataImage() }, kind = RefBtnKind.Ghost)
        }
    }

    when {
        state.metadataBusy -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalRef.current.accent)
            Text(t("metadata.parsing"), fontSize = 12.sp, color = LocalRef.current.accent)
        }

        meta == null && state.metadataImagePath == null -> RefHint(t("metadata.hint"))

        meta == null -> RefHint(t("metadata.noData"))

        else -> {
            Text(
                t("metadata.source") + "：" + t("metadata.source.${meta.source.id}"),
                fontSize = 13.5.sp,
                color = LocalRef.current.muted,
            )
            meta.fields.forEach { field ->
                ToolKv(
                    label = field.label,
                    value = field.value,
                    actionLabel = t("common.copy"),
                    onAction = {
                        clipboard.setText(AnnotatedString(field.value))
                        onCopied()
                    },
                )
            }
            if (meta.hasPrompt || meta.params != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (meta.hasPrompt) {
                        RefBtn(
                            t("metadata.importPrompts"),
                            onClick = { state.importMetadataPrompts() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (meta.params != null) {
                        RefBtn(
                            t("metadata.applyParams"),
                            onClick = { state.applyMetadataParams() },
                            modifier = Modifier.weight(1f),
                            kind = RefBtnKind.Secondary,
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RefBtn(
                    if (rawOpen) t("metadata.hideRaw") else t("metadata.showRaw"),
                    onClick = { rawOpen = !rawOpen },
                    kind = RefBtnKind.Ghost,
                    small = true,
                )
                RefBtn(
                    if (copied) t("metadata.copied") else t("metadata.copyAll"),
                    onClick = {
                        clipboard.setText(AnnotatedString(metadataAsText(state, t)))
                        onCopied()
                    },
                    kind = RefBtnKind.Ghost,
                    small = true,
                )
            }
            if (rawOpen) {
                meta.rawChunks.forEach { (keyword, text) ->
                    ToolKv(label = keyword, value = text, mono = true)
                }
            }
            RefHint(t("metadata.hint"))
        }
    }
}

/** 网页 `.pick-img`：高 150、圆角 12、1.5 虚线 borderStrong、field 底；有图后 190 高实线、图片居中。 */
@Composable
private fun ToolPickImage(path: String?, label: String, onPick: () -> Unit) {
    val ref = LocalRef.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (path == null) 150.dp else 190.dp)
            .clip(shape)
            .background(ref.field)
            .then(
                if (path == null) {
                    Modifier.drawBehind {
                        val w = 1.5.dp.toPx()
                        drawRoundRect(
                            color = ref.borderStrong,
                            topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                            size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = w,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                                ),
                            ),
                        )
                    }
                } else {
                    Modifier.border(1.5.dp, ref.borderStrong, shape)
                },
            )
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        if (path == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(PictureIcon, contentDescription = null, tint = ref.faint, modifier = Modifier.size(26.dp))
                Text(label, fontSize = 13.5.sp, color = ref.faint)
            }
        } else {
            FileImage(
                path = path,
                maxDimension = 1024,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** 网页 `.kv`：field 底 + 描边 + 圆角 12 + padding 10/12；k 12 faint（右侧 ghost 小按钮），v 13.5 / 1.5。 */
@Composable
private fun ToolKv(
    label: String,
    value: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    mono: Boolean = false,
) {
    val ref = LocalRef.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ref.field)
            .border(1.dp, ref.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = ref.faint, modifier = Modifier.weight(1f))
            if (actionLabel != null) RefBtn(actionLabel, onClick = onAction, kind = RefBtnKind.Ghost, small = true)
        }
        SelectionContainer {
            Text(
                value.ifBlank { "—" },
                fontSize = if (mono) 11.5.sp else 13.5.sp,
                lineHeight = if (mono) 18.sp else 20.sp,
                fontFamily = if (mono) FontFamily.Monospace else null,
                color = if (mono) ref.muted else ref.text,
            )
        }
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
    // 选图：走系统 Photo Picker（不需要读存储权限）
    val pickLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) state.selectReverseImage(uri)
    }
    val pick: () -> Unit = {
        pickLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    // 网页「反推提示词」：pick-img → label + seg → 主按钮 → 结果框 → 复制 / 发送 → hint
    ToolPickImage(
        path = state.reverseImagePath,
        label = t("reverse.selectImage"),
        onPick = pick,
    )
    if (state.reverseImagePath != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RefBtn(t("reverse.reselect"), onClick = pick, modifier = Modifier.weight(1f), kind = RefBtnKind.Secondary)
            RefBtn(t("reverse.clear"), onClick = { state.clearReverseImage() }, kind = RefBtnKind.Ghost)
        }
    }

    Text(t("reverse.mode"), fontSize = 12.5.sp, color = LocalRef.current.muted)
    RefSeg(
        options = PromptRules.MODES.map { mode ->
            when (mode) {
                PromptRules.MODE_NATURAL -> t("mode.natural")
                PromptRules.MODE_MIXED -> t("mode.mixed")
                else -> t("mode.tag")
            }
        },
        selected = PromptRules.MODES.indexOf(state.settings.reverseRuleMode).coerceAtLeast(0),
        onSelect = { i -> state.setSettings { it.copy(reverseRuleMode = PromptRules.MODES[i]) } },
    )

    RefBtn(
        if (state.reverseBusy) t("reverse.running") else t("reverse.start"),
        onClick = { state.runReverseForTool() },
        modifier = Modifier.fillMaxWidth(),
        enabled = state.reverseImagePath != null && !state.reverseBusy,
        icon = SparkleIcon,
    )

    val result = state.reverseResult
    val ref = LocalRef.current
    // 结果框 = `textarea.field`（5 行）：field 底 + 描边 + 圆角 12
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ref.field)
            .border(1.dp, ref.border, RoundedCornerShape(12.dp))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (result != null) {
            SelectionContainer { Text(result, fontSize = 13.5.sp, lineHeight = 20.sp, color = ref.text) }
        } else {
            Text(t("reverse.noResult"), fontSize = 13.5.sp, color = ref.faint)
        }
    }
    if (result != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RefBtn(
                if (copied) t("reverse.copied") else t("reverse.copy"),
                onClick = {
                    clipboard.setText(AnnotatedString(result))
                    onCopied()
                },
                modifier = Modifier.weight(1f),
                kind = RefBtnKind.Secondary,
            )
            RefBtn(
                t("reverse.import"),
                onClick = { state.importReverseResultToPrompt() },
                modifier = Modifier.weight(1f),
                kind = RefBtnKind.Tint,
            )
        }
    }
    RefHint(t("reverse.hint"))
}
