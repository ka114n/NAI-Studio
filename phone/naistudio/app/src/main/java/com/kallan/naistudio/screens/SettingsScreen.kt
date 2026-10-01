package com.kallan.naistudio.screens

import com.kallan.naistudio.ui.LocalSchemeSwitch
import com.kallan.naistudio.ui.RefSchemeKeys
import com.kallan.naistudio.ui.refSchemeBase
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.kallan.naistudio.ui.RefModalBottomSheet as ModalBottomSheet
import com.kallan.naistudio.ui.RefOutlinedButton as OutlinedButton
import com.kallan.naistudio.ui.RefOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import com.kallan.naistudio.ui.RefTextButton as TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.BuildConfig
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.ComicStoryboard
import com.kallan.naistudio.models.NaiCatalog
import com.kallan.naistudio.models.NaiOption
import com.kallan.naistudio.services.PromptRules
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.CollapsibleSectionCard
import com.kallan.naistudio.ui.NaiColorPicker
import com.kallan.naistudio.ui.NaiColorPreview
import com.kallan.naistudio.ui.NaiHexField
import com.kallan.naistudio.ui.NaiSwatchRow
import com.kallan.naistudio.ui.NumberField
import com.kallan.naistudio.ui.PaletteSlot
import com.kallan.naistudio.ui.PickerField
import com.kallan.naistudio.ui.SectionCard
import com.kallan.naistudio.ui.SegmentedCapsule
import com.kallan.naistudio.ui.SwitchRow
import com.kallan.naistudio.ui.clearAllPaletteSlots
import com.kallan.naistudio.ui.contrastRatio
import com.kallan.naistudio.ui.paletteHexOf
import com.kallan.naistudio.ui.parseHexColor
import com.kallan.naistudio.ui.toHexString
import com.kallan.naistudio.ui.withPaletteSlot
import com.kallan.naistudio.ui.LocalRef
import com.kallan.naistudio.ui.LocalThemeSwitch
import com.kallan.naistudio.ui.PaletteOverrides
import com.kallan.naistudio.ui.paletteOverrideCount
import com.kallan.naistudio.ui.paletteOverridesOf
import com.kallan.naistudio.ui.paletteSwatches
import com.kallan.naistudio.ui.refTokens
import com.kallan.naistudio.ui.RefButton as Button
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow

/** 设置页：存储 / 生成 / 参数记忆 / 外观 / 网络。（账号与备份已移到「我的」页。） */
@Composable
fun SettingsScreen(state: AppState) {
    val language = state.settings.language
    val t = { key: String -> RuntimeText.text(language, key) }
    val settings = state.settings
    // 当前是深色还是浅色（"跟随系统"时按系统）。调色板和取色器都要用它挑"哪一套"，
    // 所以提到最外层 —— 放在某一节的内容 lambda 里，末尾那个弹层就够不着了。
    val darkNow = when (settings.theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    // 每一栏都能"收纳"（用户要求全部可折叠），分别记住展开状态
    var storageOpen by rememberSaveable { mutableStateOf(false) }
    var generateOpen by rememberSaveable { mutableStateOf(false) }
    var persistOpen by rememberSaveable { mutableStateOf(false) }
    var interfaceOpen by rememberSaveable { mutableStateOf(false) }
    var rulesOpen by rememberSaveable { mutableStateOf(false) }
    var networkOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    // 正在用取色器调的那一档（null = 没开）。取色器本身是个底部弹层，见文件末尾。
    var pickerSlot by remember { mutableStateOf<PaletteSlot?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CollapsibleSectionCard(
            title = t("settings.storage"),
            expanded = storageOpen,
            onToggle = { storageOpen = !storageOpen },
            subtitle = if (settings.saveToGallery) t("settings.saveToGallery") else t("settings.keepMetadata"),
        ) {
            SwitchRow(
                label = t("settings.keepMetadata"),
                checked = settings.keepImageMetadata,
                onCheckedChange = { value -> state.setSettings { it.copy(keepImageMetadata = value) } },
            )
            SwitchRow(
                label = t("settings.saveToGallery"),
                help = t("settings.saveToGalleryHint"),
                checked = settings.saveToGallery,
                onCheckedChange = { value -> state.setSettings { it.copy(saveToGallery = value) } },
            )
            // 自定义保存目录：**点一下弹系统文件夹选择器**（SAF），不再手打路径
            // —— 现代 Android 上往任意手打路径写盘基本会被拒，之前那个输入框实际是失效的。
            val pickFolder = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
            ) { uri -> state.setOutputFolder(uri) }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t("settings.imageOutputDir"), style = MaterialTheme.typography.labelMedium)
                OutlinedButton(
                    onClick = { pickFolder.launch(null) },
                    enabled = settings.saveToGallery,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        state.outputFolderLabel().ifEmpty { t("storage.folderNone") },
                        modifier = Modifier.weight(1f),
                    )
                    Text("📁")
                }
                Text(
                    t("settings.imageOutputDirHint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = com.kallan.naistudio.ui.LocalRef.current.muted,
                )
                if (settings.imageOutputTreeUri.isNotEmpty()) {
                    TextButton(onClick = { state.setOutputFolder(null) }) {
                        Text(t("storage.folderClear"))
                    }
                }
            }
            var template by remember(settings.imageNameTemplate) { mutableStateOf(settings.imageNameTemplate) }
            OutlinedTextField(
                value = template,
                onValueChange = { value ->
                    template = value
                    state.setSettings { it.copy(imageNameTemplate = value) }
                },
                label = { Text(t("settings.fileNameTemplate")) },
                supportingText = { Text(t("settings.fileNameTemplateHint")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        CollapsibleSectionCard(
            title = "生成",
            expanded = generateOpen,
            onToggle = { generateOpen = !generateOpen },
            subtitle = if (settings.streamPreviewEnabled) t("generate.streamPreview") else null,
        ) {
            // 从生成页移到这里；默认开启（与参考实现一致）
            SwitchRow(
                label = t("generate.streamPreview"),
                help = "边算边显示去噪中间稿（模糊→清晰）。服务端不支持时会自动回退常规模式",
                checked = settings.streamPreviewEnabled,
                onCheckedChange = { value -> state.setSettings { it.copy(streamPreviewEnabled = value) } },
            )
            // 官方 2× 放大：付费接口，默认关；提示里明确写清消耗与"编辑仍用放大前那张"
            SwitchRow(
                label = t("upscale.settingAuto"),
                help = t("upscale.settingAutoHint"),
                checked = settings.officialUpscaleAfterGenerate,
                onCheckedChange = { value ->
                    state.setSettings { it.copy(officialUpscaleAfterGenerate = value) }
                },
            )
            // 漫画「按剧情分镜」：生成完分镜要不要直接生图。
            // **这是本项目唯一会自动扣 Anlas 的开关**，所以默认关、提示里写明花费。
            SwitchRow(
                label = t("comic.autoGenerate"),
                help = t("comic.autoGenerateHint"),
                checked = settings.comicAutoGenerate,
                onCheckedChange = { value ->
                    state.setSettings { it.copy(comicAutoGenerate = value) }
                },
            )
            // 狂暴模式的页数上限：不是"目标页数"，是护栏 ——
            // 防止模型把一格拆成一页、一口气排出去几十张图（那都是真金白银）。
            NumberField(
                label = t("comic.berserkMaxPages"),
                value = settings.comicMaxPages,
                hint = t("comic.berserkMaxPagesHint"),
                maxLength = 2,
                onCommit = { value ->
                    state.setSettings {
                        it.copy(
                            comicMaxPages = value.coerceIn(
                                ComicStoryboard.MIN_PAGES,
                                ComicStoryboard.MAX_PAGES,
                            ),
                        )
                    }
                },
            )
            // 生成行为相关（从「参数记忆」挪过来：它决定的是下一张用哪张图，不是"记不记参数"）
            SwitchRow(
                label = t("settings.i2iContinueLatest"),
                help = t("settings.i2iContinueLatestHint"),
                checked = settings.i2iSourceMode == "latest",
                onCheckedChange = { value ->
                    state.setSettings { it.copy(i2iSourceMode = if (value) "latest" else "original") }
                },
            )

            val params = state.params
            // 从生成页移到这里：负面预设（UC）
            PickerField(
                label = t("generate.ucPreset"),
                options = NaiCatalog.ucPresets,
                selected = params.ucPreset.toString(),
                onSelect = { value -> state.setParam { it.copy(ucPreset = value.toInt()) } },
            )
            // 从生成页移到这里：质量标签（V5 之外没有 light 档）
            PickerField(
                label = t("generate.quality"),
                options = if (params.isV5) {
                    NaiCatalog.qualityPresets
                } else {
                    NaiCatalog.qualityPresets.filter { it.value != "light" }
                },
                selected = params.qualityPreset,
                onSelect = { value -> state.setParam { it.copy(qualityPreset = value) } },
            )
        }

        CollapsibleSectionCard(
            title = t("settings.persistence"),
            expanded = persistOpen,
            onToggle = { persistOpen = !persistOpen },
            subtitle = t("settings.persistenceHint"),
        ) {
            Text(
                t("settings.persistenceHint"),
                style = MaterialTheme.typography.bodySmall,
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
            )
            SwitchRow(
                label = t("settings.persistGenerate"),
                checked = settings.persistGenerateParams,
                onCheckedChange = { value -> state.setSettings { it.copy(persistGenerateParams = value) } },
            )
            SwitchRow(
                label = "记住重绘参数",
                checked = settings.persistInpaintParams,
                onCheckedChange = { value -> state.setSettings { it.copy(persistInpaintParams = value) } },
            )
            SwitchRow(
                label = "记住超分参数",
                checked = settings.persistUpscaleParams,
                onCheckedChange = { value -> state.setSettings { it.copy(persistUpscaleParams = value) } },
            )
            SwitchRow(
                label = "记住 Director 参数",
                checked = settings.persistDirectorParams,
                onCheckedChange = { value -> state.setSettings { it.copy(persistDirectorParams = value) } },
            )
        }

        // 界面：主题（暗色 / 浅色）+ 自定义调色板（Reference 皮肤，口径同手机网页原型）。
        // 配色只剩 Reference 一套；调色板**深浅各一套**，在哪个模式就调哪一套。
        val ref = LocalRef.current
        val customCount = paletteOverrideCount(settings, darkNow)
        CollapsibleSectionCard(
            title = t("settings.interface"),
            expanded = interfaceOpen,
            onToggle = { interfaceOpen = !interfaceOpen },
            subtitle = t("settings.interfaceSubtitle"),
        ) {
            Text(t("settings.themeMode"), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            val switchTheme = LocalThemeSwitch.current
            var segCenter by remember { mutableStateOf(Offset.Zero) }
            SegmentedCapsule(
                options = listOf(
                    NaiOption(t("settings.themeDark"), "dark"),
                    NaiOption(t("settings.themeLight"), "light"),
                ),
                selected = if (darkNow) "dark" else "light",
                modifier = Modifier.onGloballyPositioned { segCenter = it.boundsInWindow().center },
                onSelect = { value ->
                    val targetDark = value == "dark"
                    if (targetDark != darkNow) switchTheme(segCenter, targetDark)
                },
            )

            // 网页 `.scheme-grid`：3 列色块 + 名称，点了走圆形扩散
            Spacer(Modifier.height(12.dp))
            Text(t("settings.colorScheme"), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            val switchScheme = LocalSchemeSwitch.current
            RefSchemeKeys.chunked(3).forEach { rowKeys ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowKeys.forEach { key ->
                        SchemeTile(
                            name = t("scheme.$key"),
                            key = key,
                            selected = ref.scheme == key,
                            dark = darkNow,
                            modifier = Modifier.weight(1f),
                            onPick = { center -> if (ref.scheme != key) switchScheme(center, key) },
                        )
                    }
                }
            }
            Text(
                t("settings.colorSchemeHint"),
                style = MaterialTheme.typography.labelSmall,
                color = ref.muted,
            )

            HorizontalDivider(
                Modifier.padding(vertical = 12.dp),
                color = com.kallan.naistudio.ui.LocalRef.current.border,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t("settings.paletteCustom"), style = MaterialTheme.typography.labelMedium)
                    Text(
                        t(if (darkNow) "settings.paletteEditingDark" else "settings.paletteEditingLight") +
                            if (customCount > 0) {
                                " · " + t("settings.paletteCustomCount").replace("{n}", customCount.toString())
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = com.kallan.naistudio.ui.LocalRef.current.accent,
                    )
                }
                TextButton(
                    enabled = customCount > 0,
                    onClick = { state.setSettings { clearAllPaletteSlots(it, darkNow) } },
                ) {
                    Text(t("settings.paletteResetAll"), style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                t("settings.paletteDeriveHint"),
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
            )
            Spacer(Modifier.height(4.dp))

            PaletteSlot.entries.forEach { slot ->
                val hex = paletteHexOf(settings, darkNow, slot)
                val custom = parseHexColor(hex) != null
                // 色块 = 当前实际生效的颜色（自定义过就是它，否则是 Reference 原值）
                val shown = slot.of(ref)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { pickerSlot = slot }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(shown)
                            .border(1.dp, ref.borderStrong, RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t(paletteSlotNameKey(slot)), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            (if (custom) t("settings.paletteCustomized") else t("settings.paletteFollowTheme")) +
                                " · " + shown.toHexString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (custom) ref.accent else ref.muted,
                        )
                    }
                    if (custom) {
                        TextButton(
                            onClick = { state.setSettings { withPaletteSlot(it, darkNow, slot, "") } },
                        ) {
                            Text(t("common.reset"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // 取色器弹层：**拖动时不落盘**，点保存才写回设置（否则每帧写一次 prefs + 全界面重组）
        val editing = pickerSlot
        if (editing != null) {
            PalettePickerSheet(
                slot = editing,
                dark = darkNow,
                initial = editing.of(ref),
                baseOverrides = paletteOverridesOf(settings, darkNow),
                t = t,
                onDismiss = { pickerSlot = null },
                onApply = { picked ->
                    state.setSettings { withPaletteSlot(it, darkNow, editing, picked.toHexString()) }
                    pickerSlot = null
                },
            )
        }

        // 提示词优化与反推规则：两段可编辑的 system prompt，各自可选 tag / 自然语言 / 混合
        PromptRulesCard(state, t, expanded = rulesOpen, onToggle = { rulesOpen = !rulesOpen })

        CollapsibleSectionCard(
            title = t("settings.langDataset"),
            expanded = networkOpen,
            onToggle = { networkOpen = !networkOpen },
            subtitle = settings.language,
        ) {
            PickerField(
                label = t("settings.interfaceLanguage"),
                options = listOf(NaiOption("简体中文", "zh-CN"), NaiOption("English", "en-US")),
                selected = settings.language,
                onSelect = { value -> state.setSettings { it.copy(language = value) } },
            )
            // 「数据集」= NovelAI 模型训练数据的分类（Anime / Furry / Background），
            // 它决定模型在哪种画风上更强，和界面语言一样是"全局口径"，所以放在同一栏。
            PickerField(
                label = t("settings.dataset"),
                options = listOf(
                    NaiOption("Anime", "anime"),
                    NaiOption("Furry", "furry"),
                    NaiOption("Background", "background"),
                ),
                selected = settings.modelMode,
                onSelect = { value -> state.setSettings { it.copy(modelMode = value) } },
            )
            Text(
                t("settings.datasetHint"),
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
            )
        }

        CollapsibleSectionCard(
            title = t("settings.about"),
            expanded = aboutOpen,
            onToggle = { aboutOpen = !aboutOpen },
            subtitle = BuildConfig.VERSION_NAME,
        ) {
            val updaterContext = LocalContext.current
            com.kallan.naistudio.ui.ApplicationUpdateHost(BuildConfig.VERSION_NAME, "Android", java.io.File(updaterContext.cacheDir, "updates"), { com.kallan.naistudio.services.AndroidApplicationUpdater.install(updaterContext, it) }, language, manual = true)
            // 版本号从 BuildConfig 读（每次构建自动生成），不再硬编码 —— 装的是哪一次构建一眼可见
            Text(
                "NAI Studio · ${t("settings.version")} ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "versionCode ${BuildConfig.VERSION_CODE}　构建 ${BuildConfig.BUILD_STAMP}　" +
                    BuildConfig.EDITION,
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
            )
            Text(
                "模型选项：${NaiCatalog.models.size} 个　采样器：${NaiCatalog.samplers.size} 个",
                style = MaterialTheme.typography.bodySmall,
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
            )
            // 自建接口地址（原「网络」栏）：属于高级项，和"关于"一样点开才看
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = com.kallan.naistudio.ui.LocalRef.current.border,
            )
            Text(
                t("settings.advancedEndpoint"),
                style = MaterialTheme.typography.labelLarge,
            )
            SwitchRow(
                label = t("settings.allowCustomEndpoint"),
                help = t("settings.allowCustomEndpointHint"),
                checked = settings.allowCustomEndpoint,
                onCheckedChange = { value -> state.setSettings { it.copy(allowCustomEndpoint = value) } },
            )
            if (settings.allowCustomEndpoint) {
                var imageBase by remember(settings.imageBaseUrl) { mutableStateOf(settings.imageBaseUrl) }
                OutlinedTextField(
                    value = imageBase,
                    onValueChange = { value ->
                        imageBase = value
                        state.setSettings { it.copy(imageBaseUrl = value) }
                    },
                    label = { Text("imageBaseUrl") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                var apiBase by remember(settings.apiBaseUrl) { mutableStateOf(settings.apiBaseUrl) }
                OutlinedTextField(
                    value = apiBase,
                    onValueChange = { value ->
                        apiBase = value
                        state.setSettings { it.copy(apiBaseUrl = value) }
                    },
                    label = { Text("apiBaseUrl") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // 这里原来显示 state.displayStatus（用户要求：**只在生图界面**显示当前状态，
        // 比如"正在生成 1/1"。其他页面不再写状态。）

        // ---------------- 致谢（用户要求：放在设置**最底下**）----------------
        // 位置说明：必须是**本函数 Column 的最后一个子项**。
        // 我第一版把它插到了下面 PromptRulesCard 的里面 —— 那是个**默认收起**的
        // 可折叠卡，结果是"看不见"（用户反馈"没在设置里看到致谢"）。
        // 以后往这里加东西，先确认自己还在 SettingsScreen 里，别滑进隔壁的函数。
        SectionCard(t("settings.credits")) {
            Text(
                t("settings.creditsHint"),
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
            )
            CreditEntry(
                name = "NovelAI Harness",
                role = t("settings.creditHarnessRole"),
                url = "https://github.com/saltysalrua/Novelai-harness",
            )
            CreditEntry(
                name = "法典图鉴 · NovelAI 提示词",
                role = t("settings.creditCodexRole"),
                url = "https://novelai.quicktagcloud.com",
            )
        }
    }
}

/**
 * 「提示词优化与反推规则」卡片。
 *
 * 优化与反推**各自**可选三种模式（tag / 自然语言 / 混合），每种模式**分别保存**一份规则：
 * 切换模式会载入该模式已保存的规则（默认值取自 ComfyUI「提示词小助手」插件 / 按其口径写），
 * 在文本框里直接改就是在改当前模式的规则；「恢复默认」只重置当前模式。
 */
@Composable
private fun PromptRulesCard(
    state: AppState,
    t: (String) -> String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val settings = state.settings

    fun modeLabel(mode: String): String = when (mode) {
        PromptRules.MODE_NATURAL -> t("rules.modeNatural")
        PromptRules.MODE_MIXED -> t("rules.modeMixed")
        else -> t("rules.modeTag")
    }
    // 胶囊里三格并排，用短标签（"tag（标签流）"这种长文案会被切掉）
    fun modeShort(mode: String): String = when (mode) {
        PromptRules.MODE_NATURAL -> t("mode.natural")
        PromptRules.MODE_MIXED -> t("mode.mixed")
        else -> t("mode.tag")
    }
    val modeOptions = PromptRules.MODES.map { NaiOption(modeShort(it), it) }

    fun optimizeRules(mode: String): String = when (mode) {
        PromptRules.MODE_NATURAL -> settings.optimizeRulesNatural
        PromptRules.MODE_MIXED -> settings.optimizeRulesMixed
        else -> settings.optimizeRulesTag
    }
    fun reverseRules(mode: String): String = when (mode) {
        PromptRules.MODE_NATURAL -> settings.reverseRulesNatural
        PromptRules.MODE_MIXED -> settings.reverseRulesMixed
        else -> settings.reverseRulesTag
    }

    CollapsibleSectionCard(
        title = t("rules.title"),
        expanded = expanded,
        onToggle = onToggle,
        subtitle = "${modeLabel(settings.optimizeRuleMode)} · ${modeLabel(settings.reverseRuleMode)}",
    ) {
        Text(
            t("rules.hint"),
            style = MaterialTheme.typography.bodySmall,
            color = com.kallan.naistudio.ui.LocalRef.current.muted,
        )

        // ---------------- 优化规则 ----------------
        Text(t("rules.optimizeMode"), style = MaterialTheme.typography.labelMedium)
        SegmentedCapsule(
            options = modeOptions,
            selected = settings.optimizeRuleMode,
            onSelect = { value -> state.setSettings { it.copy(optimizeRuleMode = value) } },
        )
        val optimizeMode = settings.optimizeRuleMode
        var optimized by remember(optimizeMode, settings.optimizeRulesTag, settings.optimizeRulesNatural, settings.optimizeRulesMixed) {
            mutableStateOf(optimizeRules(optimizeMode))
        }
        Text(t("rules.optimize"), style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = optimized,
            onValueChange = { value ->
                optimized = value
                state.setSettings {
                    when (optimizeMode) {
                        PromptRules.MODE_NATURAL -> it.copy(optimizeRulesNatural = value)
                        PromptRules.MODE_MIXED -> it.copy(optimizeRulesMixed = value)
                        else -> it.copy(optimizeRulesTag = value)
                    }
                }
            },
            minLines = 4,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = {
                val preset = PromptRules.defaultOptimizeRules(optimizeMode)
                optimized = preset
                state.setSettings {
                    when (optimizeMode) {
                        PromptRules.MODE_NATURAL -> it.copy(optimizeRulesNatural = preset)
                        PromptRules.MODE_MIXED -> it.copy(optimizeRulesMixed = preset)
                        else -> it.copy(optimizeRulesTag = preset)
                    }
                }
            },
        ) { Text(t("rules.reset")) }

        // ---------------- 反推规则 ----------------
        Text(t("rules.reverseMode"), style = MaterialTheme.typography.labelMedium)
        SegmentedCapsule(
            options = modeOptions,
            selected = settings.reverseRuleMode,
            onSelect = { value -> state.setSettings { it.copy(reverseRuleMode = value) } },
        )
        val reverseMode = settings.reverseRuleMode
        var reversed by remember(reverseMode, settings.reverseRulesTag, settings.reverseRulesNatural, settings.reverseRulesMixed) {
            mutableStateOf(reverseRules(reverseMode))
        }
        Text(t("rules.reverse"), style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = reversed,
            onValueChange = { value ->
                reversed = value
                state.setSettings {
                    when (reverseMode) {
                        PromptRules.MODE_NATURAL -> it.copy(reverseRulesNatural = value)
                        PromptRules.MODE_MIXED -> it.copy(reverseRulesMixed = value)
                        else -> it.copy(reverseRulesTag = value)
                    }
                }
            },
            minLines = 4,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = {
                val preset = PromptRules.defaultReverseRules(reverseMode)
                reversed = preset
                state.setSettings {
                    when (reverseMode) {
                        PromptRules.MODE_NATURAL -> it.copy(reverseRulesNatural = preset)
                        PromptRules.MODE_MIXED -> it.copy(reverseRulesMixed = preset)
                        else -> it.copy(reverseRulesTag = preset)
                    }
                }
            },
        ) { Text(t("rules.reset")) }

        // ---------------- 漫画分镜规则（「按剧情分镜」的 system prompt）----------------
        Text(
            t("comic.storyboardRules"),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            t("comic.storyboardRulesHint"),
            style = MaterialTheme.typography.bodySmall,
            color = com.kallan.naistudio.ui.LocalRef.current.muted,
        )
        var storyboardRules by remember(settings.comicStoryboardRules) {
            mutableStateOf(settings.comicStoryboardRules)
        }
        OutlinedTextField(
            value = storyboardRules,
            onValueChange = { value ->
                storyboardRules = value
                state.setSettings { it.copy(comicStoryboardRules = value) }
            },
            minLines = 4,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = {
                val preset = PromptRules.COMIC_STORYBOARD_RULES
                storyboardRules = preset
                state.setSettings { it.copy(comicStoryboardRules = preset) }
            },
        ) { Text(t("rules.reset")) }

        // ---------------- 分页规则（狂暴模式**第一段**的 system prompt）----------------
        // 与上面那段是两段独立的提示词：先分页，再逐页分镜。分开列在这里，
        // 免得改了一段以为改了两段（狂暴模式跑出来还是老样子）。
        Text(
            t("comic.berserkPagePlanRules"),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            t("comic.berserkPagePlanRulesHint"),
            style = MaterialTheme.typography.bodySmall,
            color = com.kallan.naistudio.ui.LocalRef.current.muted,
        )
        var pagePlanRules by remember(settings.comicPagePlanRules) {
            mutableStateOf(settings.comicPagePlanRules)
        }
        OutlinedTextField(
            value = pagePlanRules,
            onValueChange = { value ->
                pagePlanRules = value
                state.setSettings { it.copy(comicPagePlanRules = value) }
            },
            minLines = 4,
            maxLines = 12,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = {
                val preset = PromptRules.COMIC_PAGE_PLAN_RULES
                pagePlanRules = preset
                state.setSettings { it.copy(comicPagePlanRules = preset) }
            },
        ) { Text(t("rules.reset")) }
    }
}

/**
 * 致谢里的一条：**名称与网址都可以点**，点了用系统浏览器打开。
 *
 * 用 `Intent.ACTION_VIEW` 而不是内置 WebView：这几条只是"我想去看看"，
 * 交给用户自己的浏览器更合适（有登录态、有书签、有他习惯的阅读模式）。
 *
 * ⚠️ `startActivity` 外面套了 `runCatching`：设备上可能没有浏览器、或者
 * 被家长控制/企业策略拦掉，那时会抛 `ActivityNotFoundException`。
 * 为了一条致谢把整个设置页搞崩是不值得的 —— 点不动就点不动。
 */
@Composable
private fun CreditEntry(name: String, role: String, url: String) {
    val context = LocalContext.current
    val open: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        Unit
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // 名称是**主链接**（用户要求"点击名称跳转"）：用主色 + 下划线，
        // 让它一眼看着就是能点的东西，而不是一段普通标题
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = com.kallan.naistudio.ui.LocalRef.current.accent,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = open)
                .padding(vertical = 2.dp),
        )
        Text(
            role,
            style = MaterialTheme.typography.bodySmall,
            color = com.kallan.naistudio.ui.LocalRef.current.muted,
        )
        // 网址也点得动（同一个目标），但不加下划线 —— 一行下划线足够了
        Text(
            url,
            style = MaterialTheme.typography.labelSmall,
            color = com.kallan.naistudio.ui.LocalRef.current.accent,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = open)
                .padding(vertical = 2.dp),
        )
    }
}

/** 色槽 → i18n 名字。 */
private fun paletteSlotNameKey(slot: PaletteSlot): String = when (slot) {
    PaletteSlot.PRIMARY -> "settings.palettePrimary"
    PaletteSlot.BACKGROUND -> "settings.paletteBackground"
    PaletteSlot.PANEL -> "settings.palettePanel"
    PaletteSlot.TEXT -> "settings.paletteText"
}

/**
 * **取色弹层**：色环 + 饱和/明度方块 + 预设色 + HEX + 预览。
 *
 * 拖动时**不落盘**（每帧写一次设置会不停触发全界面重组，还刷屏写 prefs），
 * 只更新这里的一份本地色；点「保存」才 [onApply] 写回设置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PalettePickerSheet(
    slot: PaletteSlot,
    dark: Boolean,
    initial: Color,
    /** 当前模式已保存的覆盖项：预览 / 对比度按"改完之后"的整套 token 算。 */
    baseOverrides: PaletteOverrides,
    t: (String) -> String,
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var color by remember(slot, dark) { mutableStateOf(initial) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = com.kallan.naistudio.ui.LocalRef.current.panel,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${t("settings.paletteEditing")}${t(paletteSlotNameKey(slot))}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                t(if (dark) "settings.paletteEditingDark" else "settings.paletteEditingLight"),
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
            )
            Spacer(Modifier.height(14.dp))

            NaiColorPicker(color = color, onColorChange = { color = it })

            Spacer(Modifier.height(14.dp))
            NaiColorPreview(color = color)
            Spacer(Modifier.height(12.dp))
            NaiSwatchRow(
                colors = paletteSwatches(slot, dark),
                selected = color,
                onPick = { color = it },
            )
            Spacer(Modifier.height(12.dp))
            NaiHexField(value = color, onHex = { color = it })

            // 对比度读数（只提示，不拦；口径同网页原型）：
            // 主色 ↔ 它上面的字；文字色 ↔ 卡片色；底色 / 卡片色 ↔ 文字色。
            val preview = refTokens(
                dark,
                when (slot) {
                    PaletteSlot.PRIMARY -> baseOverrides.copy(primary = color)
                    PaletteSlot.BACKGROUND -> baseOverrides.copy(background = color)
                    PaletteSlot.PANEL -> baseOverrides.copy(panel = color)
                    PaletteSlot.TEXT -> baseOverrides.copy(text = color)
                },
                LocalRef.current.scheme,
            )
            val (pairBg, pairFg) = when (slot) {
                PaletteSlot.PRIMARY -> preview.accent to preview.accentContrast
                PaletteSlot.TEXT -> preview.panel to preview.text
                PaletteSlot.BACKGROUND -> preview.bg to preview.text
                PaletteSlot.PANEL -> preview.panel to preview.text
            }
            val ratio = contrastRatio(pairBg, pairFg)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(pairBg)
                    .border(1.dp, preview.border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(t("settings.paletteSample"), color = pairFg, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                t("settings.paletteContrast").replace("{r}", "%.1f".format(ratio)) + " · " + t(
                    when {
                        ratio >= 4.5f -> "settings.paletteContrastGood"
                        ratio >= 3f -> "settings.paletteContrastWeak"
                        else -> "settings.paletteContrastBad"
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    ratio >= 4.5f -> LocalRef.current.success
                    ratio >= 3f -> LocalRef.current.warning
                    else -> com.kallan.naistudio.ui.LocalRef.current.negative
                },
            )

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(t("common.cancel"))
                }
                Button(
                    onClick = { onApply(color) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(t("common.save"))
                }
            }
        }
    }
}

/** 网页 `.scheme`：field 底、描边、圆角 12；上面一块 30 高的预览（底色 + 卡片 + 主色圆点），下面名字。 */
@Composable
private fun SchemeTile(
    name: String,
    key: String,
    selected: Boolean,
    dark: Boolean,
    modifier: Modifier,
    onPick: (Offset) -> Unit,
) {
    val ref = LocalRef.current
    val pv = refSchemeBase(key, dark)
    var center by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(12.dp)
    val pvShape = RoundedCornerShape(8.dp)
    Column(
        modifier
            .onGloballyPositioned { center = it.boundsInWindow().center }
            .clip(shape)
            .background(if (selected) ref.selected else ref.field)
            .border(1.dp, if (selected) ref.selectedBorder else ref.border, shape)
            .clickable { onPick(center) }
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 7.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(pvShape)
                .background(pv.bg)
                .border(1.dp, Color(0x407F7F7F), pvShape)
                .padding(5.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(pv.panel),
            )
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(pv.accent),
            )
        }
        Text(
            name,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) ref.onSelected else ref.muted,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
