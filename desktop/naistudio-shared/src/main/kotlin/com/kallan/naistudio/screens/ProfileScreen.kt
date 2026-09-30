package com.kallan.naistudio.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import kotlin.math.roundToInt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.AppSettings
import com.kallan.naistudio.models.AuthState
import com.kallan.naistudio.models.BackupCodec
import com.kallan.naistudio.models.NaiOption
import com.kallan.naistudio.platform.LocalPlatform
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.kallan.naistudio.models.LlmPresetEntry
import com.kallan.naistudio.models.LlmPresets
import com.kallan.naistudio.models.StPreset
import com.kallan.naistudio.models.StPresetRef
import com.kallan.naistudio.platform.LocalUiHost
import com.kallan.naistudio.services.PromptRules
import com.kallan.naistudio.services.DEFAULT_IMAGE_BASE
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.PickerField
import com.kallan.naistudio.ui.LocalNaiSkin
import com.kallan.naistudio.ui.NaiSkin
import com.kallan.naistudio.ui.NaiSkinTokens
import com.kallan.naistudio.ui.SectionCard
import com.kallan.naistudio.ui.StudioButton
import com.kallan.naistudio.ui.StudioButtonKind
import com.kallan.naistudio.ui.StudioPageContainer
import com.kallan.naistudio.ui.trackTextInputFocus
import kotlinx.coroutines.launch

/**
 * 「我的」页（完整版）/「API」页（纯净版）。
 *
 * · **完整版 `HOSTED_EDITION = true`**：账号相关（头像入口 / 登录状态 / 登出 / 额度 /
 *   账号服务器地址 / 未登录时的 BYO 自填 token）都在这里；备份恢复也在这页。
 * · **纯净版 `local`（无账号）**：整块账号 UI 去掉 —— 没有头像、没有"未登录"、没有登录/注册/登出，
 *   只留**服务器地址**（NovelAI 接口地址）+ **NovelAI API token**，标题就是「API」。
 */
@Composable
fun ProfileScreen(state: AppState) {
    val language = state.settings.language
    val t = { key: String -> RuntimeText.text(language, key) }

    // 备份：导出用系统"另存为"、导入用系统"打开文件"（都不需要存储权限）。
    // 两端都是**系统对话框**，只是手机走 ActivityResult、电脑走 AWT，见 `UiHost`。
    val exportLauncher = LocalUiHost.current.rememberFileCreator("application/json") { ref ->
        if (ref != null) state.exportBackup(ref)
    }
    val importLauncher = LocalUiHost.current.rememberFilePicker(
        listOf("application/json", "image/png", "text/plain"),
    ) { ref -> if (ref != null) state.importBackup(ref) }
    // 「导出到图片」两步：① 选图片 → ② 选保存位置（PNG）。第二步必须在选完图之后启动。
    var backupImageUri by remember { mutableStateOf<String?>(null) }
    var pendingBackupImage by remember { mutableStateOf<String?>(null) }
    val pickBackupImage = LocalUiHost.current.rememberFilePicker(listOf("image/png")) { ref ->
        if (ref != null) pendingBackupImage = ref
    }
    val saveBackupImage = LocalUiHost.current.rememberFileCreator("image/png") { ref ->
        val source = backupImageUri
        if (ref != null && source != null) state.exportBackupToImage(source, ref)
        backupImageUri = null
    }
    LaunchedEffect(pendingBackupImage) {
        val picked = pendingBackupImage ?: return@LaunchedEffect
        pendingBackupImage = null
        backupImageUri = picked
        saveBackupImage.launch(profileBackupImageFileNameNow())
    }

    val inheritedColors = MaterialTheme.colorScheme
    val profileColors = if (LocalNaiSkin.current == NaiSkin.Reference) {
        inheritedColors.copy(
            primary = NaiSkinTokens.accent(),
            onPrimary = NaiSkinTokens.accentContrast(),
            primaryContainer = NaiSkinTokens.selected(),
            onPrimaryContainer = NaiSkinTokens.onSelected(),
            surface = NaiSkinTokens.surface(),
            onSurface = NaiSkinTokens.text(),
            onSurfaceVariant = NaiSkinTokens.muted(),
            outline = NaiSkinTokens.controlBorder(),
            outlineVariant = NaiSkinTokens.border(),
            surfaceContainerLow = NaiSkinTokens.glass(),
            surfaceContainerHigh = NaiSkinTokens.field(),
            surfaceContainerHighest = NaiSkinTokens.subtleHover(),
        )
    } else {
        inheritedColors
    }
    MaterialTheme(colorScheme = profileColors) {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
                fontFamily = NaiSkinTokens.ReferenceFontFamily,
            ),
        ) {
    val platform = LocalPlatform.current
    StudioPageContainer(
        title = t("profile.title"),
        subtitle = t("profile.subtitle"),
        actions = {
            StudioButton(
                onClick = { exportLauncher.launch(profileBackupFileNameNow()) },
                enabled = !state.busy,
                kind = StudioButtonKind.Secondary,
            ) { Text(t("backup.export")) }
        },
    ) {
        // 依据 StudioPageContainer 扣完左右留白后的实际宽度切换，避免内容窗格看似够宽、
        // 卡片却已被内边距挤窄时仍然强行并排。
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wideLayout = maxWidth >= 900.dp
            val compactHero = maxWidth < 560.dp
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ProfileHero(state, t, compact = compactHero)

                val accountCard: @Composable () -> Unit = {
                    if (platform.hostedEdition) HostedAccountCard(state, t) else LocalApiCard(state, t)
                }
                val backupCard: @Composable () -> Unit = {
                    SectionCard(t("backup.title")) {
                        Text(
                            t("backup.hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            backupContentSummary(state, language, t),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            StudioButton(
                                onClick = { importLauncher.launch(null) },
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                                kind = StudioButtonKind.Primary,
                            ) { Text(t("backup.import")) }
                            StudioButton(
                                onClick = { pickBackupImage.launch(null) },
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                                kind = StudioButtonKind.Secondary,
                            ) { Text(t("backup.exportToImage")) }
                        }
                        Text(
                            t("backup.exportToImageHint"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            t("backup.tokenNote"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (wideLayout) {
                    // 对齐网页示例：左侧账户连接，右侧概览指标与备份；两列等宽。
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f)) { accountCard() }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            ProfileOverviewCards(state, t)
                            backupCard()
                        }
                    }
                } else {
                    accountCard()
                    ProfileOverviewCards(state, t)
                    backupCard()
                }

                // 生成页翻译与提示词优化使用的配置依然保留；默认折叠，避免占据首屏。
                LlmApiCard(state, t)

                // 其他页面不显示随生成状态变化的提示，避免在无关页面出现“正在生成”。
            }
        }
    }
        }
    }
}

@Composable
private fun ProfileHero(state: AppState, t: (String) -> String, compact: Boolean) {
    val platform = LocalPlatform.current
    val loggedIn = state.isLoggedIn
    val loggedInUser = (state.authState as? AuthState.LoggedIn)?.user?.email
    val status = when {
        platform.hostedEdition && loggedIn -> t("account.connected")
        platform.hostedEdition -> t("account.notLoggedIn")
        state.usingGateway -> t("api.thirdParty")
        state.hasToken -> t("account.connected")
        else -> t("settings.token")
    }
    val detail = when {
        platform.hostedEdition && loggedIn -> loggedInUser?.takeIf { it.isNotBlank() } ?: t("account.loggedInAs")
        platform.hostedEdition -> t("account.welcome")
        state.usingGateway -> t("api.thirdPartyHint")
        state.hasToken -> t("api.officialHint")
        else -> t("settings.tokenHint")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = NaiSkinTokens.glass(),
        border = BorderStroke(1.dp, NaiSkinTokens.border()),
        shadowElevation = 2.dp,
    ) {
        @Composable
        fun IdentityBlock(modifier: Modifier = Modifier) {
            Row(
                modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(NaiSkinTokens.selected()),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "N",
                    color = NaiSkinTokens.onSelected(),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    t("profile.workspace"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = NaiSkinTokens.text(),
                )
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = NaiSkinTokens.muted(),
                )
                Text(
                    status,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(NaiSkinTokens.selected())
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = NaiSkinTokens.onSelected(),
                )
            }
            }
        }

        @Composable
        fun ActionButton(modifier: Modifier = Modifier) {
            if (platform.hostedEdition) {
                StudioButton(
                    onClick = { if (loggedIn) state.refreshQuota() else state.beginLogin() },
                    enabled = !state.authBusy,
                    modifier = modifier,
                    kind = StudioButtonKind.Primary,
                ) { Text(if (loggedIn) t("account.refresh") else t("account.login")) }
            } else if (state.hasToken) {
                StudioButton(
                    onClick = { state.refreshAnlas() },
                    modifier = modifier,
                    kind = StudioButtonKind.Primary,
                ) { Text(t("account.refresh")) }
            }
        }

        if (compact) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IdentityBlock(Modifier.fillMaxWidth())
                if (platform.hostedEdition || state.hasToken) {
                    ActionButton(Modifier.fillMaxWidth())
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                IdentityBlock(Modifier.weight(1f))
                ActionButton()
            }
        }
    }
}

@Composable
private fun ProfileOverviewCards(state: AppState, t: (String) -> String) {
    val platform = LocalPlatform.current
    val loggedIn = state.authState as? AuthState.LoggedIn
    val remaining = if (platform.hostedEdition && loggedIn != null) {
        loggedIn.quota.remaining ?: state.account.anlasBalance
    } else {
        state.account.anlasBalance
    }
    val apiSource = when {
        platform.hostedEdition && loggedIn != null -> t("account.title")
        state.usingGateway -> t("api.thirdParty")
        else -> t("api.official")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileMetricCard(
            title = t("settings.anlas"),
            value = remaining?.toString() ?: "—",
            detail = state.account.tierName ?: apiSource,
            modifier = Modifier.weight(1f),
        )
        ProfileMetricCard(
            title = t("profile.currentModel"),
            value = state.params.model.ifBlank { "—" },
            detail = apiSource,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ProfileMetricCard(
    title: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        SectionCard(title) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = NaiSkinTokens.text(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = NaiSkinTokens.muted(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 「LLM API」卡片：接口地址 / API Key / 模型名 / 翻译后端。
 *
 * **默认收起**（用户要求"做成收纳"）：只显示一行标题 + 当前状态摘要；点一下才展开全部字段。
 * 生成页正面提示词下面那两个图标（翻译、LLM 优化）与图库的「反推提示词」都用这里的配置。
 */
@Composable
private fun LlmApiCard(state: AppState, t: (String) -> String) {
    val settings = state.settings
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(t("llm.title")) {
        // 收起时的摘要：有没有配好、翻译走的哪个后端
        val configured = settings.llmApiUrl.isNotBlank() && settings.llmApiKey.isNotBlank()
        val serviceName = if (settings.translateService == "baidu") {
            t("llm.translateByBaidu")
        } else {
            t("llm.translateByLlm")
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (configured) "${settings.llmModel} · $serviceName" else t("llm.notConfiguredShort"),
                style = MaterialTheme.typography.bodySmall,
                color = if (configured) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!expanded) return@SectionCard

        Text(
            t("llm.hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 翻译用哪个后端：LLM 翻译 或 百度翻译
        PickerField(
            label = t("llm.translateService"),
            options = listOf(
                NaiOption(t("llm.translateByLlm"), "llm"),
                NaiOption(t("llm.translateByBaidu"), "baidu"),
            ),
            selected = settings.translateService,
            onSelect = { value -> state.setSettings { it.copy(translateService = value) } },
        )
        OutlinedTextField(
            value = settings.llmApiUrl,
            onValueChange = { value -> state.setSettings { it.copy(llmApiUrl = value) } },
            label = { Text(t("llm.url")) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .trackTextInputFocus(state, "llm.apiUrl"),
        )
        OutlinedTextField(
            value = settings.llmApiKey,
            onValueChange = { value -> state.setSettings { it.copy(llmApiKey = value) } },
            label = { Text(t("llm.key")) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .trackTextInputFocus(state, "llm.apiKey"),
        )
        OutlinedTextField(
            value = settings.llmModel,
            onValueChange = { value -> state.setSettings { it.copy(llmModel = value) } },
            label = { Text(t("llm.model")) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .trackTextInputFocus(state, "llm.model"),
        )
        // ---- LLM 预设（用户自建；2026-09-17 用户要求：形态照"提示词预设"）----
        Text(t("llm.presetsTitle"), style = MaterialTheme.typography.labelMedium)
        Text(
            t("llm.presetsHint"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LlmPresetManager(state = state, t = t)

        // 预设：导入/导出 launcher + "查看器"当前看的是哪一份
        // ⚠️ 必须声明在**使用之前**（Kotlin 局部变量不能先用后声明 —— 这里踩过两次）
        // 手机线这两个是 `rememberLauncherForActivityResult(OpenDocument / CreateDocument)`；
        // 电脑端没有 ActivityResult，走 `LocalUiHost` 的系统文件对话框（见 `platform/UiHost.kt`），
        // 拿到的 ref 就是**本地路径**，交给 AppState 直接读写文件。
        val stImport = LocalUiHost.current.rememberFilePicker(
            listOf("application/json", "text/plain"),
        ) { ref -> if (ref != null) state.importStPresetFromFile(ref) }
        // 「导入预设」旁边的「导出预设」：导出**当前选中**那一份（默认文件名 = 它的文件名）
        val stExport = LocalUiHost.current.rememberFileCreator("application/json") { ref ->
            if (ref != null) state.exportStPresetToFile(ref)
        }

        // ---- 预设（2026-09-17 手机线先行；2026-09-18 用户要求：名字就叫「预设」、
        //      选择做成和「思考程度」同款的下拉、列表里显示**文件名**而不是 SAF 的 document:xxx）----
        Text(t("st.title"), style = MaterialTheme.typography.labelMedium)
        Text(
            t("st.hint"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 老档案里存的名字是 SAF 给的 `document:1000045162` —— 显示时一律换成**文件名**
        fun presetLabel(ref: StPresetRef): String =
            ref.name.takeIf { it.isNotBlank() && !it.startsWith("document:") } ?: ref.fileName

        if (settings.stPresetRefs.isEmpty()) {
            Text(
                t("st.none"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // 选择：和「思考程度」同款的下拉（用户 2026-09-18）
            PickerField(
                label = t("st.pick"),
                options = listOf(NaiOption(t("st.off"), "")) +
                    settings.stPresetRefs.map { NaiOption(presetLabel(it), it.id) },
                selected = settings.stPresetId,
                onSelect = { value -> state.selectStPreset(value) },
            )
            // 选中那一份的信息 + 删除
            settings.stPresetRefs.firstOrNull { it.id == settings.stPresetId }?.let { ref ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        t("st.meta")
                            .replace("{n}", ref.entryCount.toString())
                            .replace("{tokens}", ref.estimatedTokens.toString()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    // ⚠️ 「查看」按钮与那个条目对话框**已按用户要求删除**（2026-09-18）：
                    // 导入时就把没开着的条目删掉了，剩下全是开着的，逐条开关/编辑那一套没必要留在界面上。
                    // `AppState` 里那几个函数（stPresetEntriesForView / setStPresetEntryEnabled /
                    // updateStPresetEntryContent / resetStPreset）留着没删 —— 要用回来把按钮加回即可。
                    TextButton(onClick = { state.removeStPreset(ref.id) }) { Text(t("st.delete")) }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = { stImport.launch(null) }) {
                Text(t("st.import"))
            }
            // 导出：只能在"有一份被选中"时点（点下去直接进另存为）
            val exportRef = settings.stPresetRefs.firstOrNull { it.id == settings.stPresetId }
            TextButton(
                onClick = { stExport.launch(exportRef?.let { presetLabel(it) } ?: "preset.json") },
                enabled = exportRef != null,
            ) { Text(t("st.export")) }
        }
        // ---- AI 对话配置（用户 2026-09-15 要求）：思考程度 / 温度 / Top-P / 最大 tokens ----
        Text(
            t("llm.chatParamsTitle"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // 「思考程度」：走 PickerField 保持同款交互（off = 不发字段）
        PickerField(
            label = t("llm.reasoningEffort"),
            options = listOf(
                NaiOption(t("llm.reasoningOff"), "off"),
                NaiOption(t("llm.reasoningLow"), "low"),
                NaiOption(t("llm.reasoningMedium"), "medium"),
                NaiOption(t("llm.reasoningHigh"), "high"),
            ),
            selected = settings.llmReasoningEffort,
            onSelect = { value -> state.setSettings { it.copy(llmReasoningEffort = value) } },
        )
        // ⚠️ 「AI 对话配置」现在**只剩「思考程度」一项**（用户 2026-09-16 要求）：
        // 温度滑条、Top-P 滑条、最大输出 tokens 滑条，以及「思考程度」下面那行说明，全部删除。
        // 采样参数改成固定值（见 LlmApi.AiParams）—— 见那边的注释，**不是**改成"不发"。
        //
        // ---- 2026-09-16 又加回来 4 项：用户要求参考 ComfyUI「API LLM通用链路」节点，
        //      给"AI 对话层"配关键四个开关（temperature / is_memory / conversation_rounds / is_enable）。
        //      这些**只影响 LLM 辅助功能与底部抽屉的记录**，不改变出图路径。
        //
        // ---- 2026-09-17（用户要求）：总开关去除（AI 恒可用）；「输出上限 / 额外请求参数 /
        //      ImgBB key / 附加系统提示词 / 已知信息 / 工具两项 / 历史覆盖」从界面隐藏 ——
        //      配置字段与请求行为**原样保留**（走默认值/已存值），要用回来把控件加回即可。
        Text(
            "${t("llm.temperature")}：${"%.1f".format(settings.llmTemperature)}",
            style = MaterialTheme.typography.labelMedium,
        )
        Slider(
            value = settings.llmTemperature.toFloat(),
            onValueChange = { value ->
                // 节点是 0.0–1.0、step 0.1 —— 这里对齐到 0.1 的刻度
                val stepped = (value * 10f).roundToInt() / 10f
                state.setSettings { it.copy(llmTemperature = stepped.toDouble()) }
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("llm.memory"), style = MaterialTheme.typography.labelLarge)
                Text(
                    t("llm.memoryHint"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = settings.llmMemoryEnabled,
                onCheckedChange = { value -> state.setSettings { it.copy(llmMemoryEnabled = value) } },
            )
        }
        Text(
            "${t("llm.rounds")}：${settings.llmHistoryRounds}",
            style = MaterialTheme.typography.labelMedium,
        )
        Slider(
            value = settings.llmHistoryRounds.toFloat(),
            onValueChange = { value ->
                // 上限 200（用户 2026-09-17，见 AppSettings.LLM_HISTORY_ROUNDS_MAX）
                state.setSettings {
                    it.copy(
                        llmHistoryRounds = value.roundToInt()
                            .coerceIn(1, AppSettings.LLM_HISTORY_ROUNDS_MAX),
                    )
                }
            },
            valueRange = 1f..AppSettings.LLM_HISTORY_ROUNDS_MAX.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
    // 「导出记忆」按钮已按用户要求删除（2026-09-18）；`AppState` 里 exportLlmMemoryJson /
    // exportLlmMemoryFile 留着没删（要用回来把按钮加回即可）。
        // ---- 上下文功能已删（用户 2026-09-18）----------------------------------------
        // 现在**默认就用安装包自带的那份上下文**（桌面端 `src/main/resources/llm_context.json`，
        // 启动时由 `AppState.loadBundledLlmContext()` 写进 `llmUserHistoryJson`）：
        // 界面上不再有"导入上下文 / 清空上下文 / 从记忆导入"，也不再提供"导出记忆"。
        // ---- 2026-09-18（用户要求）：这里原来还有三个开关 ——「锁定结果（is_locked）」
        //      「流式输出（stream）」「主脑（main_brain）」—— 全部删掉。
        //      字段与行为**原样保留**（流式仍然是默认开、锁定仍然是关、主脑仍按存值决定
        //      要不要提供 another_llm 工具），要用回来把这三个 Row 加回即可。
        // 选了百度翻译才显示百度那两个字段
        if (settings.translateService == "baidu") {
            Text(
                t("llm.baiduHint"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = settings.baiduTranslateAppId,
                onValueChange = { value -> state.setSettings { it.copy(baiduTranslateAppId = value) } },
                label = { Text(t("llm.baiduAppId")) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .trackTextInputFocus(state, "llm.baiduAppId"),
            )
            OutlinedTextField(
                value = settings.baiduTranslateSecret,
                onValueChange = { value -> state.setSettings { it.copy(baiduTranslateSecret = value) } },
                label = { Text(t("llm.baiduSecret")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .trackTextInputFocus(state, "llm.baiduSecret"),
            )
        }

        // ---- 分功能 AI（用户 2026-09-16 要求）----
        // 每个用到 AI 的功能都能单独配一套；三项各自留空即回落，所以只换模型名时
        // 地址和密钥不用重抄（见 AppSettings.llmConfig 的注释）。
        Text(
            t("llm.perFeatureTitle"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            t("llm.perFeatureHint"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val fallback = settings.llmModel.ifBlank { t("llm.perFeatureFallback") }
        LlmOverrideGroup(
            title = t("llm.feature.translate"),
            hint = "",
            fallback = settings.llmConfigForTranslate().model,
            presets = settings.llmPresets,
            selectedPreset = settings.llmTranslatePreset,
            onPreset = { v -> state.setSettings { it.copy(llmTranslatePreset = v) } },
            t = t,
        )
        LlmOverrideGroup(
            title = t("llm.feature.optimize"),
            hint = "",
            fallback = settings.llmConfigForOptimize().model,
            presets = settings.llmPresets,
            selectedPreset = settings.llmOptimizePreset,
            onPreset = { v -> state.setSettings { it.copy(llmOptimizePreset = v) } },
            t = t,
        )
        LlmOverrideGroup(
            title = t("llm.feature.storyboard"),
            hint = t("llm.feature.storyboardHint"),
            fallback = settings.llmConfigForStoryboard().model,
            presets = settings.llmPresets,
            selectedPreset = settings.llmStoryboardPreset,
            onPreset = { v -> state.setSettings { it.copy(llmStoryboardPreset = v) } },
            t = t,
        )
        LlmOverrideGroup(
            title = t("llm.feature.plan"),
            hint = t("llm.feature.planHint"),
            fallback = settings.llmConfigForPlan().model,
            presets = settings.llmPresets,
            selectedPreset = settings.llmPlanPreset,
            onPreset = { v -> state.setSettings { it.copy(llmPlanPreset = v) } },
            t = t,
        )
        LlmOverrideGroup(
            title = t("llm.feature.reverse"),
            hint = t("llm.feature.reverseHint"),
            fallback = settings.llmConfigForReverse().model,
            presets = settings.llmPresets,
            selectedPreset = settings.llmReversePreset,
            onPreset = { v -> state.setSettings { it.copy(llmReversePreset = v) } },
            t = t,
        )
    }
}

/**
 * 一个「分功能 AI」折叠组：标题行 + 收起时的状态摘要，展开是接口地址 / 密钥 / 模型三个框。
 *
 * 收起时摘要只显示**这个功能实际会用到的模型名** —— 没填就显示主配置那一个。
 * 这样不用逐个展开也知道当前有没有真的覆盖掉。
 */
@Composable
private fun LlmOverrideGroup(
    title: String,
    hint: String,
    fallback: String,
    presets: List<LlmPresetEntry>,
    selectedPreset: String,
    onPreset: (String) -> Unit,
    t: (String) -> String,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val picked = presets.firstOrNull { it.id == selectedPreset }
    // **实际生效的模型名**（用户 2026-09-17："使用的模型名字也要显示出来"）：
    // 选了预设 → 用预设里的模型名；没选 → 跟随主配置（fallback 由调用方按该功能的实际解析结果传进来）
    val effectiveModel = picked?.model?.takeIf { it.isNotBlank() } ?: fallback
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (picked != null) {
                        effectiveModel + " · " + picked.name
                    } else {
                        effectiveModel + " · " + t("llm.perFeatureFallback")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (picked != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Filled.KeyboardArrowUp
                    } else {
                        Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!expanded) return@Column
        if (hint.isNotBlank()) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 只留"选哪个预设"这一件事 —— 地址/密钥/模型名都在预设里，不再逐个填（用户 2026-09-17）
        LlmPresetSelector(
            presets = presets,
            selectedId = selectedPreset,
            t = t,
            onSelect = onPreset,
        )
        Text(
            if (picked != null) {
                t("llm.presetInUse").replace("{name}", picked.name) + " · " + picked.baseUrl
            } else {
                t("llm.presetFollowsMain")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 纯净版（`local`）的「NAI API」卡片：**官方 / 第三方**两种接口来源 + 对应的 token。
 *
 * ⚠️ 用户 2026-09-26：「在"我的"nai api 栏加一个按钮，**可选第三方/自有 api**，
 *    做这个项目的兼容」—— 那个项目是 https://github.com/fangchen2003/service-tools
 *    （**NAI Gate**：自托管的 NovelAI 转发网关，站长配上游 token、给用户发 `nai-...` 虚拟 Key）。
 *
 * ## 为什么只需要"换个地址"就够
 *
 * 网关对外就是**一套 NovelAI 形状的接口**（`{base}/ai/generate-image`、
 * `{base}/user/data`…），鉴权同样是 `Authorization: Bearer <key>` ⇒
 * 客户端这边**不需要另写一个协议**，把地址填进来就通了。真正要额外做的只有两件：
 *  · 它**没开 img2img / 局部重绘**（README 与《用户限制说明》都写明"当前未开放"）⇒
 *    进这两个模式时先给一句人话（拦在 `AppState.gatewayAllows`）；
 *  · 它的 V5 日额度 / 月度 Anlas 在 `/user/subscription` 的 `naiGate` 字段里 ⇒
 *    下面 [GatewayQuotaRow] 拉回来显示（官方那边没有这一档）。
 *
 * 历史：服务器地址曾经因为"纯正 NovelAI App"被整条拿掉；现在**按需**放回来 ——
 * 默认仍是官方、地址栏默认隐藏，只有选了「第三方」才出现。
 */
@Composable
private fun LocalApiCard(state: AppState, t: (String) -> String) {
    val settings = state.settings
    val gateway = state.usingGateway
    SectionCard(t("api.title")) {
        Text(
            t("api.source"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SourceChip(
                label = t("api.official"),
                hint = t("api.officialHint"),
                selected = !gateway,
                modifier = Modifier.weight(1f),
            ) {
                // 切回官方：**模式置 false** + 地址恢复默认（token 不动 —— 它本来就是官方那把）。
                // ⚠️ 模式那一位必须**显式写**：早前靠"地址像不像第三方"推，点「第三方」时地址
                //    还是空的 ⇒ 推出来是官方 ⇒ 刚点亮就熄、界面没反应（真机 bug）。
                state.setSettings {
                    it.copy(useThirdPartyApi = false, imageBaseUrl = DEFAULT_IMAGE_BASE)
                }
            }
            SourceChip(
                label = t("api.thirdParty"),
                hint = t("api.thirdPartyHint"),
                selected = gateway,
                modifier = Modifier.weight(1f),
            ) {
                // 切到第三方：模式置 true，地址**只在还是官方默认值时**清空 ——
                // 留着官方地址会让人以为已经连上了（实际是拿虚拟 Key 打官方，必然 401）；
                // 但如果用户之前填过自己的站点，**别把他的地址冲掉**。
                state.setSettings {
                    val keep = it.imageBaseUrl.takeIf { url ->
                        url.isNotBlank() && url.trimEnd('/') != DEFAULT_IMAGE_BASE
                    }
                    it.copy(useThirdPartyApi = true, imageBaseUrl = keep ?: "")
                }
                state.refreshGatewayQuota()
            }
        }
        if (gateway) {
            var base by remember(settings.imageBaseUrl) { mutableStateOf(settings.imageBaseUrl) }
            OutlinedTextField(
                value = base,
                onValueChange = { value ->
                    base = value
                    state.setSettings { it.copy(imageBaseUrl = value) }
                },
                label = { Text(t("api.serverUrl")) },
                supportingText = { Text(t("api.baseUrlHint")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // 改完地址就把网关那块配额拉一次（拉不到就什么都不显示，见 GatewayQuotaRow）
            LaunchedEffect(base) { state.refreshGatewayQuota() }
            GatewayQuotaRow(state, t)
        }
        NovelAiTokenBlock(state, t)
    }
}

/** 接口来源的一颗选项（官方 / 第三方）：选中变色，底下带一句说明。 */
@Composable
private fun SourceChip(
    label: String,
    hint: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 40.dp),
        )
    }
}

/**
 * 网关自己的额度（NAI Gate 独有的 `naiGate` 那一块）。
 *
 * 拉不到 / 不是网关 ⇒ **整块不画**（`gatewayQuota` 是 null）——
 * 不编一个"剩余 0"出来，那比不显示更容易让人误会额度没了。
 */
@Composable
private fun GatewayQuotaRow(state: AppState, t: (String) -> String) {
    val quota = state.gatewayQuota ?: return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            if (quota.v5Unlimited) {
                t("api.gatewayQuotaUnlimited")
            } else {
                t("api.gatewayQuota")
                    .replace("{left}", quota.v5LeftToday.toString())
                    .replace("{limit}", quota.v5DailyLimit.toString())
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (quota.anlasEnabled) {
            Text(
                t("api.gatewayAnlas").replace("{n}", quota.anlasLeft.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 完整版的账号卡片：头像即"账号模式入口"，未登录时给 BYO 自填 token 的入口。
 */
@Composable
private fun HostedAccountCard(state: AppState, t: (String) -> String) {
    val settings = state.settings
    SectionCard(t("settings.account")) {
        val loggedIn = state.isLoggedIn
        val loggedInAuth = state.authState as? AuthState.LoggedIn

        // 头像即账号模式入口：未登录=API 模式（点头像去登录）；登录成功=账号模式（托管）。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = if (loggedIn) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .size(48.dp)
                    .let { base -> if (loggedIn) base else base.clickable { state.beginLogin() } },
            )
            Column {
                Text(
                    if (loggedIn) {
                        loggedInAuth?.user?.email?.ifBlank { t("account.loggedInAs") } ?: t("account.loggedInAs")
                    } else {
                        t("account.notLoggedIn")
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!loggedIn) {
                    Text(
                        if (settings.accountServerUrl.isBlank()) t("account.serverNotSet") else t("account.welcome"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (loggedIn && loggedInAuth != null) {
            val remaining = loggedInAuth.quota.remaining ?: state.account.anlasBalance
            Text(
                "${t("account.remaining")}：${remaining ?: t("common.unknown")}",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { state.logout() },
                    modifier = Modifier.weight(1f),
                ) { Text(t("account.logout")) }
                TextButton(onClick = { state.refreshQuota() }) { Text(t("common.retry")) }
            }
        } else {
            var server by remember(settings.accountServerUrl) { mutableStateOf(settings.accountServerUrl) }
            OutlinedTextField(
                value = server,
                onValueChange = { value ->
                    server = value
                    state.setSettings { it.copy(accountServerUrl = value) }
                },
                label = { Text(t("account.serverUrl")) },
                supportingText = { Text(t("account.serverUrlHint")) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .trackTextInputFocus(state, "account.serverUrl"),
            )
            NovelAiTokenBlock(state, t)
        }
    }
}

/** NovelAI API token 输入 + 校验/清除 + 订阅信息（完整版与纯净版共用）。 */
@Composable
private fun NovelAiTokenBlock(state: AppState, t: (String) -> String) {
    val scope = rememberCoroutineScope()
    var tokenInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = tokenInput,
        onValueChange = { tokenInput = it; message = "" },
        label = { Text(t("settings.token")) },
        supportingText = { Text(t("settings.tokenHint")) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier
            .fillMaxWidth()
            .trackTextInputFocus(state, "account.novelaiToken"),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                verifying = true
                scope.launch {
                    val error = state.setToken(tokenInput)
                    message = error ?: t("settings.tokenSaved")
                    if (error == null) tokenInput = ""
                    verifying = false
                }
            },
            enabled = !verifying && tokenInput.isNotBlank(),
            modifier = Modifier.weight(1f),
        ) { Text(if (verifying) t("generate.reading") else t("settings.verifyToken")) }

        OutlinedButton(
            onClick = {
                state.clearToken()
                message = t("settings.tokenCleared")
            },
            modifier = Modifier.weight(1f),
        ) { Text(t("settings.clearToken")) }

        TextButton(onClick = { state.refreshAnlas() }) { Text(t("common.retry")) }
    }

    if (message.isNotEmpty()) {
        Text(message, style = MaterialTheme.typography.bodySmall)
    }

    val account = state.account
    if (account.hasToken) {
        Text(
            "${t("settings.tier")}：${account.tierName ?: t("common.unknown")}" +
                (if (account.stale) "（${t("status.accountSyncStale")}）" else ""),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "${t("settings.anlas")}：${account.anlasBalance ?: t("common.unknown")}",
            style = MaterialTheme.typography.bodySmall,
        )
        account.opusUsage?.let { usage ->
            Text(
                "${t("settings.opusUsage")}：${usage.percent.toInt()}%（透支：${usage.isNegative}）",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * 「本次导出包含」那一行：设置永远有，生成参数永远有，风格预设 / 角色按当前数量决定。
 *
 * 只是**给用户吃一颗定心丸** —— 备份里到底装了什么，点导出之前就该看得见。
 */
private fun backupContentSummary(
    state: AppState,
    language: String,
    t: (String) -> String,
): String {
    // 分组功能已去掉 ⇒ 不再报组数
      val groups = 0
    val presets = state.styleLibrary.presets.size
    val characters = state.extras.charCaptions.size
    val parts = buildList {
        add(t("backup.section.settings"))
        add(t("backup.section.params"))
        if (groups > 0 || presets > 0) {
            add(
                RuntimeText.format(
                    language,
                    "backup.section.styles",
                    mapOf("groups" to groups, "presets" to presets),
                ),
            )
        }
        if (characters > 0) {
            add(RuntimeText.format(language, "backup.section.characters", mapOf("count" to characters)))
        }
    }
    return t("backup.contentNow") + parts.joinToString("、")
}

/** 备份文件名：`naistudio-backup-YYYYMMDD-HHMM.json`（本地时间）。 */
private fun profileBackupFileNameNow(): String {
    val now = java.time.LocalDateTime.now()
    val stamp = "%04d%02d%02d-%02d%02d".format(
        now.year, now.monthValue, now.dayOfMonth, now.hour, now.minute,
    )
    return BackupCodec.fileName(stamp)
}

/** 内嵌备份的图片文件名（默认后缀改成 .png）。 */
private fun profileBackupImageFileNameNow(): String =
    profileBackupFileNameNow().removeSuffix(".json") + ".png"
/** **LLM 预设管理**（用户 2026-09-17："像提示词预设那样，自己填 url、模型名、api，起个名字"）。 */
@Composable
private fun LlmPresetManager(state: AppState, t: (String) -> String) {
    val presets = state.settings.llmPresets
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LlmPresetEntry?>(null) }
    if (presets.isEmpty()) {
        Text(
            t("llm.presetEmpty"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        presets.forEach { preset ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(preset.name, style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${preset.baseUrl} · ${preset.model}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = { editing = preset }) { Text(t("llm.presetEdit")) }
                TextButton(onClick = { state.removeLlmPreset(preset.id) }) { Text(t("llm.presetDelete")) }
            }
        }
    }
    TextButton(onClick = { creating = true }) { Text(t("llm.presetNew")) }
    if (creating) {
        LlmPresetEditorDialog(
            state = state,
            initial = null,
            t = t,
            onDismiss = { creating = false },
            onSave = { entry ->
                state.upsertLlmPreset(entry)
                creating = false
            },
        )
    }
    editing?.let { current ->
        LlmPresetEditorDialog(
            state = state,
            initial = current,
            t = t,
            onDismiss = { editing = null },
            onSave = { entry ->
                state.upsertLlmPreset(entry)
                editing = null
            },
        )
    }
}

/** 预设编辑框：名字 / 地址 / 密钥 / 模型名 +「常见服务商快速填充」。 */
@Composable
private fun LlmPresetEditorDialog(
    state: AppState,
    initial: LlmPresetEntry?,
    t: (String) -> String,
    onDismiss: () -> Unit,
    onSave: (LlmPresetEntry) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var key by remember { mutableStateOf(initial?.apiKey.orEmpty()) }
    var model by remember { mutableStateOf(initial?.model.orEmpty()) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) t("llm.presetNew") else t("llm.presetEdit")) },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        error = t("llm.presetNameRequired")
                    } else {
                        onSave(
                            LlmPresetEntry(
                                id = initial?.id ?: LlmPresets.newId(),
                                name = name.trim(),
                                baseUrl = url.trim().trimEnd('/'),
                                apiKey = key.trim(),
                                model = model.trim(),
                            ),
                        )
                    }
                },
            ) { Text(t("llm.presetSave")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("llm.presetCancel")) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = ""
                    },
                    label = { Text(t("llm.presetName")) },
                    singleLine = true,
                    // 预设名常带空格（"GPT 4o mini"）→ 必须登记焦点；这是个弹窗（独立场景层），
                    // 一样要走窗口预览阶段那把空格闸门。
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackTextInputFocus(state, "llm.preset.name"),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(t("llm.url")) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackTextInputFocus(state, "llm.preset.url"),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(t("llm.key")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackTextInputFocus(state, "llm.preset.key"),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(t("llm.model")) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackTextInputFocus(state, "llm.preset.model"),
                )
                if (error.isNotBlank()) {
                    Text(
                        error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    t("llm.presetQuickFill"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.heightIn(max = 200.dp)) {
                    items(LlmPresets.HINTS) { hint ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    url = hint.baseUrl
                                    model = hint.model
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Text(hint.label, style = MaterialTheme.typography.labelMedium)
                            Text(
                                hint.baseUrl + " · " + hint.model +
                                    if (hint.note.isNotBlank()) "（" + hint.note + "）" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}

/** 分功能 AI 选预设：选了就用它那一套（不再逐个手填三项）。 */
@Composable
private fun LlmPresetSelector(
    presets: List<LlmPresetEntry>,
    selectedId: String,
    t: (String) -> String,
    onSelect: (String) -> Unit,
) {
    PickerField(
        label = t("llm.presetUse"),
        options = listOf(NaiOption(t("llm.presetManual"), "")) + presets.map { NaiOption(it.name, it.id) },
        selected = selectedId,
        onSelect = onSelect,
    )
}
