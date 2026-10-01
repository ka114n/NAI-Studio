package com.kallan.naistudio.screens

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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.ComicStoryboard
import com.kallan.naistudio.models.NaiCatalog
import com.kallan.naistudio.models.NaiOption
import com.kallan.naistudio.platform.LocalPlatform
import com.kallan.naistudio.platform.LocalUiHost
import com.kallan.naistudio.services.CustomBackground
import com.kallan.naistudio.services.PromptRules
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.CollapsibleSectionCard
import com.kallan.naistudio.ui.NaiColorPicker
import com.kallan.naistudio.ui.NaiColorPreview
import com.kallan.naistudio.ui.NaiHexField
import com.kallan.naistudio.ui.NaiSkin
import com.kallan.naistudio.ui.NaiSwatchRow
import com.kallan.naistudio.ui.NaiPickerSwatches
import com.kallan.naistudio.ui.NumberField
import com.kallan.naistudio.ui.PaletteSlot
import com.kallan.naistudio.ui.PickerField
import com.kallan.naistudio.ui.SectionCard
import com.kallan.naistudio.ui.StudioButton
import com.kallan.naistudio.ui.StudioButtonKind
import com.kallan.naistudio.ui.StudioPageContainer
import com.kallan.naistudio.ui.SegmentedCapsule
import com.kallan.naistudio.ui.SwitchRow
import com.kallan.naistudio.ui.clearAllPaletteSlots
import com.kallan.naistudio.ui.contrastRatio
import com.kallan.naistudio.ui.hasAnyPaletteOverride
import com.kallan.naistudio.ui.paletteHexOf
import com.kallan.naistudio.ui.parseHexColor
import com.kallan.naistudio.ui.toHexString
import com.kallan.naistudio.ui.trackTextInputFocus
import com.kallan.naistudio.ui.withPaletteSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 设置页：存储 / 生成 / 参数记忆 / 外观 / 网络。（账号与备份已移到「我的」页。） */
@Composable
fun SettingsScreen(state: AppState) {
    val language = state.settings.language
    val t = { key: String -> RuntimeText.text(language, key) }
    val settings = state.settings
    val glassPaletteOptions = listOf(
        NaiOption(if (language.startsWith("en")) "Glacier cyan · Glass" else "冰川青 · 毛玻璃", "glass_cyan"),
        NaiOption(if (language.startsWith("en")) "Iris violet · Glass" else "鸢尾紫 · 毛玻璃", "glass_violet"),
        NaiOption(if (language.startsWith("en")) "Emerald green · Glass" else "翡翠绿 · 毛玻璃", "glass_emerald"),
        NaiOption(if (language.startsWith("en")) "Rose coral · Glass" else "玫瑰珊瑚 · 毛玻璃", "glass_rose"),
    )
    // "关于"那一栏的构建信息：原来读的是 Android 的 BuildConfig，现在走平台层
    val platform = LocalPlatform.current
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

    // ---- 自定义背景图（用户 2026-09-20）----
    //
    // 选图器给的是**引用**（电脑 = 文件路径，手机 = content://），选完立刻把它**复制进 App 数据目录**，
    // 设置里只记落盘文件名 —— 用户之后挪走 / 删掉原图都不影响（见 `CustomBackground`）。
    //
    // 复制是磁盘 IO（几 MB 到几十 MB），**不能压在选完图的那一下回调里**
    // （电脑上那是 AWT 事件线程，卡住就是整个窗口不响应），所以丢进组合作用域的协程里做。
    val uiHost = LocalUiHost.current
    val scope = rememberCoroutineScope()
    val pickBackground = uiHost.rememberFilePicker(
        // ⚠️ **不给 webp**（用户 2026-09-20 定的）：JDK 17 的 ImageIO 读不了 webp ——
        // 选了也能复制、能存，但下次启动读不出来，只会静默回落主题底色（日志一行 WARN）✗。
        // 与其让用户踩这个坑，不如在选择器里就别列出来。
        listOf("image/png", "image/jpeg", "image/bmp", "image/gif"),
    ) { ref ->
        if (ref != null) {
            scope.launch {
                val copied = withContext(Dispatchers.IO) { CustomBackground.install(platform, ref) }
                if (copied == null) {
                    uiHost.toast(t("settings.customBackgroundFailed"), long = true)
                } else {
                    state.setSettings { it.copy(customBackground = copied) }
                }
            }
        }
    }

    StudioPageContainer(title = t("settings.title")) {
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
            // 自定义保存目录：**点一下弹系统文件夹选择器**，不再手打路径
            // —— 手机（SAF）上往任意手打路径写盘基本会被拒，之前那个输入框实际是失效的；
            // 电脑上就是一个普通目录。走宿主能力，两端都是系统对话框。
            val pickFolder = LocalUiHost.current.rememberFolderPicker { ref ->
                state.setOutputFolder(ref)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t("settings.imageOutputDir"), style = MaterialTheme.typography.labelMedium)
                StudioButton(
                    onClick = { pickFolder.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    kind = StudioButtonKind.Secondary,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (settings.imageOutputTreeUri.isNotEmpty()) {
                    StudioButton(onClick = { state.setOutputFolder(null) }, kind = StudioButtonKind.Ghost) {
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
                // 文件名模板要登记焦点（空格闸门读 `AppState.textInputFocused`）：
                // 模板里带空格 / `{prompt}` 这类占位符，漏了就打不出空格。
                modifier = Modifier
                    .fillMaxWidth()
                    .trackTextInputFocus(state, "settings.fileNameTemplate"),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

        // 界面：配色（经典画廊 / 蓝白）。与侧边栏的深/浅色开关正交：
        // 深浅决定明暗，配色决定色系，两维组合出四套外观。
        CollapsibleSectionCard(
            title = t("settings.interface"),
            expanded = interfaceOpen,
            onToggle = { interfaceOpen = !interfaceOpen },
            subtitle = glassPaletteOptions.firstOrNull { it.value == settings.palette }?.label ?: when (settings.palette) {
                "bluewhite" -> t("settings.paletteBluewhite")
                "novelai" -> t("settings.paletteNovelAi")
                "violet" -> t("settings.paletteViolet")
                "forest" -> t("settings.paletteForest")
                "sepia" -> t("settings.paletteSepia")
                else -> t("settings.paletteClassic")
            },
        ) {
            PickerField(
                label = t("settings.palette"),
                options = listOf(
                    NaiOption(t("settings.paletteClassic"), "classic"),
                    NaiOption(t("settings.paletteBluewhite"), "bluewhite"),
                    // 用户 2026-09-26：照 NovelAI 官方暗色界面取的那套 ✓
                    NaiOption(t("settings.paletteNovelAi"), "novelai"),
                    // 三个明显不同色系的（用户：「三个选项全是一种配色，多做几个色系的」）
                    NaiOption(t("settings.paletteViolet"), "violet"),
                    NaiOption(t("settings.paletteForest"), "forest"),
                    NaiOption(t("settings.paletteSepia"), "sepia"),
                    // 参考稿那套青蓝（用户 2026-09-27：「网页做的 UI 做电脑线 UI」+「新配色」）
                    NaiOption(t("settings.paletteAccent"), "accent"),
                ) + glassPaletteOptions,
                selected = settings.palette,
                onSelect = { value -> state.setSettings { it.copy(palette = value) } },
            )
            Text(
                t("settings.paletteHint"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // ---- 外观风格（用户 2026-09-27：「两套共存，做成可切换风格」）----
            // ⚠️ 和上面的配色是**两个正交的维度**，所以这里必须写清楚各自管什么，
            //    否则用户会以为"选了青蓝配色就该变成参考稿那个样子"（其实还得配实色风格）。
            HorizontalDivider(
                Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            PickerField(
                label = t("settings.skin"),
                options = NaiSkin.entries.map { NaiOption(t(it.labelKey), it.id) },
                selected = NaiSkin.fromId(settings.skin).id,
                onSelect = { value -> state.setSettings { it.copy(skin = value) } },
            )
            Text(
                t("settings.skinHint"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (glassPaletteOptions.any { it.value == settings.palette }) {
                Text(
                    if (language.startsWith("en")) {
                        "Includes a frosted color background in both light and dark modes. A custom image takes priority. Saved custom colors remain active; reset them below to see the full preset."
                    } else {
                        "深浅模式均有配套磨砂背景，自定义背景图优先。已保存的自定义颜色仍然生效，可在下方恢复跟随主题，查看完整预设。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- 调色板（用户 2026-09-16 要求）----
            // 四档可调：主色（生成按钮那一类）/ 胶囊浅色 / 页面底色 / 文字色。
            // **深浅各一套** —— 现在在哪个模式就调哪一套，所以先把这句话写在上面，
            // 免得调完切到另一个模式以为"没生效"。
            HorizontalDivider(
                Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                t("settings.paletteCustom"),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                t(if (darkNow) "settings.paletteEditingDark" else "settings.paletteEditingLight"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                t("settings.paletteDeriveHint"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            PaletteSlot.entries.forEach { slot ->
                val hex = paletteHexOf(settings, darkNow, slot)
                val custom = parseHexColor(hex) != null
                // 色块显示"当前实际生效的颜色"：自定义过就用它，否则用主题当前值
                val shown = parseHexColor(hex) ?: when (slot) {
                    PaletteSlot.PRIMARY -> MaterialTheme.colorScheme.primary
                    PaletteSlot.CONTAINER -> MaterialTheme.colorScheme.primaryContainer
                    PaletteSlot.BACKGROUND -> MaterialTheme.colorScheme.background
                    PaletteSlot.TEXT -> MaterialTheme.colorScheme.onBackground
                    // 标题栏没设过 = 跟随面板底色（那才是"没有独立标题栏"的样子 ✓）
                    PaletteSlot.TITLE_BAR -> MaterialTheme.colorScheme.surface
                    // 画布没设过 = 跟随主题的 surfaceContainer（就是现在的画布底 ✓）
                    PaletteSlot.CANVAS -> MaterialTheme.colorScheme.surfaceContainer
                    // 窗口背景没设过 = 跟随主题的 surface（面板底色 ✓）
                    PaletteSlot.WINDOW -> MaterialTheme.colorScheme.surface
                }
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
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(8.dp),
                            ),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t(paletteSlotNameKey(slot)), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (custom) hex else t("settings.paletteFollowTheme"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // 「这一档恢复跟随主题」只在改过之后才显示，免得整列都是按钮
                    if (custom) {
                        TextButton(
                            onClick = {
                                state.setSettings { withPaletteSlot(it, darkNow, slot, "") }
                            },
                        ) {
                            Text(t("common.reset"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (hasAnyPaletteOverride(settings)) {
                TextButton(onClick = { state.setSettings { clearAllPaletteSlots(it) } }) {
                    Text(t("settings.paletteResetAll"))
                }
            }

            // ---- 自定义背景图（用户 2026-09-20）----
            // 只影响**电脑端**：铺在窗口最底下当背景（见 `desktop/Main.kt`）。
            // 没设置时底层就是主题自己的不透明底色，浅色是主题米白、深色是主题近黑。
            HorizontalDivider(
                Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(t("settings.customBackground"), style = MaterialTheme.typography.labelMedium)
            Text(
                if (settings.customBackground.isBlank()) {
                    t("settings.customBackgroundNone")
                } else {
                    // 只报"设没设"，不报落盘文件名：那是内部名字（带时间戳），对用户没有意义
                    t("settings.customBackgroundSet")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                t("settings.customBackgroundHint"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { pickBackground.launch(null) },
                    modifier = Modifier.weight(1f),
                ) { Text(t("settings.customBackgroundPick")) }
                OutlinedButton(
                    // 清除 = 设置回空串（底层回到主题底色）**并且**把盘上那张删掉 ——
                    // 只改设置不删文件的话，那张图会永远躺在数据目录里没人认领。
                    onClick = {
                        state.setSettings { it.copy(customBackground = "") }
                        scope.launch(Dispatchers.IO) { CustomBackground.clear(platform) }
                    },
                    enabled = settings.customBackground.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text(t("settings.customBackgroundClear")) }
            }
            // ---- 毛玻璃开关（用户 2026-09-20：「自选的背景图可选毛玻璃质感」）----
            // 关着 = 原图原样铺（加这个开关之前的行为，默认如此 —— 用户说的是"**可选**"）。
            // 开着 = 缩到小尺寸再两遍模糊，铺成磨砂纹理底（见 `DesktopBackdrop.customBackgroundBitmap`）。
            //
            // **没选背景图时禁用**（而不是"能点但没效果"）：那时底层压根不铺这一层，
            // 开关点下去界面上不会有任何变化 —— 与其让人以为"点了没反应 / 坏了"，
            // 不如直接置灰，并把 hint 换成"先选一张背景图"。
            Spacer(Modifier.height(4.dp))
            SwitchRow(
                label = t("settings.customBackgroundFrosted"),
                hint = if (settings.customBackground.isBlank()) {
                    t("settings.customBackgroundFrostedDisabled")
                } else {
                    t("settings.customBackgroundFrostedHint")
                },
                checked = settings.customBackgroundFrosted,
                enabled = settings.customBackground.isNotBlank(),
                onCheckedChange = { frosted ->
                    state.setSettings { it.copy(customBackgroundFrosted = frosted) }
                },
            )
        }

        // （注：窗口标题栏颜色已经并进上面那组**调色板**里了 —— 用户 2026-09-26
        //  「这个放到界面里的调色板里」✓：它现在是 `PaletteSlot.TITLE_BAR` 那一行，
        //   和 主色/容器/背景/文字 并排，点开走同一个取色弹窗 ✓。这里不再单独占一块 ✗。）

        // 取色器弹层：**拖动时不落盘**，点保存才写回设置（否则每帧写一次 prefs + 全界面重组）
        val editing = pickerSlot
        if (editing != null) {
            val themeBg = MaterialTheme.colorScheme.background
            val themeText = MaterialTheme.colorScheme.onBackground
            val bgNow = parseHexColor(paletteHexOf(settings, darkNow, PaletteSlot.BACKGROUND)) ?: themeBg
            val textNow = parseHexColor(paletteHexOf(settings, darkNow, PaletteSlot.TEXT)) ?: themeText
            PalettePickerSheet(
                slot = editing,
                dark = darkNow,
                initial = parseHexColor(paletteHexOf(settings, darkNow, editing)) ?: when (editing) {
                    PaletteSlot.PRIMARY -> MaterialTheme.colorScheme.primary
                    PaletteSlot.CONTAINER -> MaterialTheme.colorScheme.primaryContainer
                    PaletteSlot.BACKGROUND -> themeBg
                    PaletteSlot.TEXT -> themeText
                    PaletteSlot.TITLE_BAR -> MaterialTheme.colorScheme.surface
                    PaletteSlot.CANVAS -> MaterialTheme.colorScheme.surfaceContainer
                    PaletteSlot.WINDOW -> MaterialTheme.colorScheme.surface
                },
                otherColor = when (editing) {
                    PaletteSlot.TEXT -> bgNow
                    PaletteSlot.BACKGROUND -> textNow
                    else -> null
                },
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CollapsibleSectionCard(
            title = t("settings.about"),
            expanded = aboutOpen,
            onToggle = { aboutOpen = !aboutOpen },
            subtitle = platform.appVersion,
        ) {
            com.kallan.naistudio.ui.ApplicationUpdateHost(platform.appVersion, "Windows", java.io.File(platform.paths.filesDir, "updates"), platform::installApplicationUpdate, language, manual = true)
            // 版本号从平台层读（手机是每次构建自动生成的 BuildConfig，电脑是版本常量）——
            // 装的是哪一次构建一眼可见，不是硬编码。
            Text(
                "NAI Studio · ${t("settings.version")} ${platform.appVersion}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "versionCode ${platform.versionCode}　构建 ${platform.buildStamp}　" +
                    platform.edition,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "模型选项：${NaiCatalog.models.size} 个　采样器：${NaiCatalog.samplers.size} 个",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 自建接口地址（原「网络」栏）：属于高级项，和"关于"一样点开才看
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackTextInputFocus(state, "settings.imageBaseUrl"),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackTextInputFocus(state, "settings.apiBaseUrl"),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
 * 切换模式会载入该模式已保存的规则（默认值是本项目原创文案，见 `PromptRules`），
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            // 提示词优化规则：**整段自然语言**，空格是刚需 → 必须登记焦点
            modifier = Modifier
                .fillMaxWidth()
                .trackTextInputFocus(state, "settings.optimizeRules"),
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
            // 反推规则：同样是整段自然语言 → 登记焦点
            modifier = Modifier
                .fillMaxWidth()
                .trackTextInputFocus(state, "settings.reverseRules"),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            // 「按剧情分镜」的 system prompt：整段自然语言 → 登记焦点
            modifier = Modifier
                .fillMaxWidth()
                .trackTextInputFocus(state, "settings.storyboardRules"),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            // 分页规则的 system prompt：整段自然语言 → 登记焦点
            modifier = Modifier
                .fillMaxWidth()
                .trackTextInputFocus(state, "settings.pagePlanRules"),
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
 * 用系统浏览器而不是内置 WebView：这几条只是"我想去看看"，
 * 交给用户自己的浏览器更合适（有登录态、有书签、有他习惯的阅读模式）。
 * 两端都是系统浏览器，只是手机是 `Intent(ACTION_VIEW)`、电脑是 `Desktop.browse`，
 * 所以走宿主能力的 [LocalUiHost]。
 *
 * ⚠️ 打开动作在实现里套了 `runCatching`：设备上可能没有浏览器、或者
 * 被家长控制/企业策略拦掉。为了一条致谢把整个设置页搞崩是不值得的 —— 点不动就点不动。
 */
@Composable
private fun CreditEntry(name: String, role: String, url: String) {
    val uiHost = LocalUiHost.current
    val open: () -> Unit = {
        uiHost.openUrl(url)
        Unit
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // 名称是**主链接**（用户要求"点击名称跳转"）：用主色 + 下划线，
        // 让它一眼看着就是能点的东西，而不是一段普通标题
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = open)
                .padding(vertical = 2.dp),
        )
        Text(
            role,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 网址也点得动（同一个目标），但不加下划线 —— 一行下划线足够了
        Text(
            url,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
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
    PaletteSlot.CONTAINER -> "settings.paletteContainer"
    PaletteSlot.BACKGROUND -> "settings.paletteBackground"
    PaletteSlot.TEXT -> "settings.paletteText"
    // 复用之前那栏的文案键（用户 2026-09-26：把标题栏颜色并进调色板 ✓）
    PaletteSlot.TITLE_BAR -> "settings.windowTitleBar"
    PaletteSlot.CANVAS -> "settings.paletteCanvas"
    PaletteSlot.WINDOW -> "settings.paletteWindow"
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
    /** 另一档当前生效的色（文字色 ↔ 底色），用来比对比度；主色/胶囊不需要 → null。 */
    otherColor: Color?,
    t: (String) -> String,
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var color by remember(slot, dark) { mutableStateOf(initial) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            NaiColorPicker(color = color, onColorChange = { color = it })

            Spacer(Modifier.height(14.dp))
            NaiColorPreview(color = color)
            Spacer(Modifier.height(12.dp))
            NaiSwatchRow(
                colors = NaiPickerSwatches,
                selected = color,
                onPick = { color = it },
            )
            Spacer(Modifier.height(12.dp))
            NaiHexField(value = color, onHex = { color = it })

            // 对比度提醒（只提示，不拦）：文字色和底色撞在一起会看不清。
            // 比的是"这一档的新色"和"另一档当前生效的色"，由调用方算好传进来。
            if (otherColor != null) {
                val bg = if (slot == PaletteSlot.BACKGROUND) color else otherColor
                val fg = if (slot == PaletteSlot.TEXT) color else otherColor
                if (contrastRatio(bg, fg) < 3.0f) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t("settings.paletteLowContrast"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

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
                TextButton(
                    onClick = { onApply(color) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(t("common.save"))
                }
            }
        }
    }
}
