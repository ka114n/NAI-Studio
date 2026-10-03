package com.kallan.naistudio.screens

import android.net.Uri
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.kallan.naistudio.ui.RefAlertDialog as AlertDialog
import com.kallan.naistudio.models.StPreset
import com.kallan.naistudio.models.StPresetRef
import com.kallan.naistudio.models.LlmPresetEntry
import com.kallan.naistudio.models.LlmPresets
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
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
import com.kallan.naistudio.ui.RefButton as Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.kallan.naistudio.ui.RefOutlinedButton as OutlinedButton
import com.kallan.naistudio.ui.RefOutlinedTextField as OutlinedTextField
import androidx.compose.material3.RadioButton
import com.kallan.naistudio.ui.RefSlider as Slider
import com.kallan.naistudio.ui.RefSwitch as Switch
import androidx.compose.material3.Text
import com.kallan.naistudio.ui.RefTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.BuildConfig
import com.kallan.naistudio.i18n.RuntimeText
import com.kallan.naistudio.models.AppSettings
import com.kallan.naistudio.models.AuthState
import com.kallan.naistudio.models.BackupCodec
import com.kallan.naistudio.models.NaiOption
import com.kallan.naistudio.services.PromptRules
import com.kallan.naistudio.services.DEFAULT_IMAGE_BASE
import com.kallan.naistudio.state.AppState
import com.kallan.naistudio.ui.PickerField
import com.kallan.naistudio.ui.SectionCard
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

    // 备份：导出用系统"创建文件"，导入用系统"打开文件"（都不需要存储权限）
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) state.exportBackup(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) state.importBackup(uri) }
    // 「导出到图片」两步：① 选图片 → ② 选保存位置（PNG）。第二步必须在选完图之后启动。
    var backupImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingBackupImage by remember { mutableStateOf<Uri?>(null) }
    val pickBackupImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingBackupImage = uri }
    val saveBackupImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png"),
    ) { uri ->
        val source = backupImageUri
        if (uri != null && source != null) state.exportBackupToImage(source, uri)
        backupImageUri = null
    }
    LaunchedEffect(pendingBackupImage) {
        val picked = pendingBackupImage ?: return@LaunchedEffect
        pendingBackupImage = null
        backupImageUri = picked
        saveBackupImage.launch(profileBackupImageFileNameNow())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (BuildConfig.HOSTED_EDITION) {
            HostedAccountCard(state, t)
        } else {
            LocalApiCard(state, t)
        }

        // LLM API：给生成页那两个图标（翻译 / 优化提示词）用；默认**收起来**（用的人自己展开）
        LlmApiCard(state, t)

        FullBackupControls(state)

        SectionCard(t("backup.title")) {
            Text(
                t("backup.hint"),
                style = MaterialTheme.typography.bodySmall,
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
            )
            // 「本次导出包含」：把当前实际有的东西列出来（设置永远有，其余按数量）
            Text(
                backupContentSummary(state, language, t),
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.accent,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { exportLauncher.launch(profileBackupFileNameNow()) },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                ) { Text(t("backup.export")) }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "image/png", "*/*")) },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                ) { Text(t("backup.import")) }
            }
            OutlinedButton(
                onClick = { pickBackupImage.launch(arrayOf("image/*")) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(t("backup.exportToImage")) }
            Text(
                t("backup.exportToImageHint"),
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
            )
            Text(
                t("backup.tokenNote"),
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
            )
        }

        // 这里原来显示 state.displayStatus（用户要求：**只在生图界面**显示当前状态，
        // 比如"正在生成 1/1"。其他页面不再写状态 —— 它会跟着全局状态变，
        // 在一个跟生成无关的页面上冒出一句"正在生成…"反而莫名其妙。
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
                    com.kallan.naistudio.ui.LocalRef.current.text
                } else {
                    com.kallan.naistudio.ui.LocalRef.current.muted
                },
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = com.kallan.naistudio.ui.LocalRef.current.muted,
            )
        }
        if (!expanded) return@SectionCard

        Text(
            t("llm.hint"),
            style = MaterialTheme.typography.bodySmall,
            color = com.kallan.naistudio.ui.LocalRef.current.muted,
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
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.llmApiKey,
            onValueChange = { value -> state.setSettings { it.copy(llmApiKey = value) } },
            label = { Text(t("llm.key")) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.llmModel,
            onValueChange = { value -> state.setSettings { it.copy(llmModel = value) } },
            label = { Text(t("llm.model")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // ---- LLM 预设（用户自建；2026-09-17 用户要求：形态照"提示词预设"）----
        Text(t("llm.presetsTitle"), style = MaterialTheme.typography.labelMedium)
        Text(
            t("llm.presetsHint"),
            style = MaterialTheme.typography.labelSmall,
            color = com.kallan.naistudio.ui.LocalRef.current.muted,
        )
        LlmPresetManager(state = state, t = t)

        // 预设：导入/导出 launcher + "查看器"当前看的是哪一份
        // ⚠️ 必须声明在**使用之前**（Kotlin 局部变量不能先用后声明 —— 这里踩过两次）
        val stImport = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri -> if (uri != null) state.importStPresetUri(uri) }
        // 「导入预设」旁边的「导出预设」：导出**当前选中**那一份（默认文件名 = 它的文件名）
        val stExport = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
        ) { uri -> if (uri != null) state.exportStPresetUri(uri) }
        var stViewId by remember { mutableStateOf<String?>(null) }

        // ---- 预设（2026-09-17 手机线先行；2026-09-18：名字就叫「预设」、显示文件名、
        //      列表式 = 1.1.29 那套、外面套一层**胶囊收纳**（默认收起，点胶囊才铺开））----
        Text(t("st.title"), style = MaterialTheme.typography.labelMedium)
        // 老档案里存的名字是 SAF 给的 `document:1000045162` —— 显示时一律换成**文件名**
        fun presetLabel(ref: StPresetRef): String =
            ref.name.takeIf { it.isNotBlank() && !it.startsWith("document:") } ?: ref.fileName

        // 胶囊收纳（用户 2026-09-18：「加上删除功能，胶囊收纳」→「胶囊收纳用思考程度那种样式」）：
        // **和「思考程度」一个样子** —— 上面一行小标题 + 下面一个整宽的描边按钮（值 + `▾`）；
        // 点它铺开/收起下面的预设列表与导入/导出（默认收起，省高度）。
        var stPresetOpen by rememberSaveable { mutableStateOf(false) }
        val activePreset = settings.stPresetRefs.firstOrNull { it.id == settings.stPresetId }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t("st.pick"), style = MaterialTheme.typography.labelMedium)
            OutlinedButton(
                onClick = { stPresetOpen = !stPresetOpen },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    activePreset?.let { presetLabel(it) + " · " + it.entryCount + " 条" } ?: t("st.off"),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(if (stPresetOpen) "▴" else "▾")
            }
        }
        if (stPresetOpen) {
            Text(
                t("st.hint"),
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
            )
            if (settings.stPresetRefs.isEmpty()) {
                Text(
                    t("st.none"),
                    style = MaterialTheme.typography.labelSmall,
                    color = com.kallan.naistudio.ui.LocalRef.current.muted,
                )
            } else {
                // ⚠️ 用户 2026-09-18：「用下午 2 点那个预设系统」→ 列表式（每份一行：
                // `● 名字` + `N 条·≈tokens` + 启用/停用 + 查看 + 删除）
                settings.stPresetRefs.forEach { ref ->
                    val active = settings.stPresetId == ref.id
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (active) "● " + presetLabel(ref) else presetLabel(ref),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) {
                                    com.kallan.naistudio.ui.LocalRef.current.accent
                                } else {
                                    com.kallan.naistudio.ui.LocalRef.current.text
                                },
                            )
                            Text(
                                t("st.meta")
                                    .replace("{n}", ref.entryCount.toString())
                                    .replace("{tokens}", ref.estimatedTokens.toString()),
                                style = MaterialTheme.typography.labelSmall,
                                color = com.kallan.naistudio.ui.LocalRef.current.muted,
                            )
                        }
                        TextButton(onClick = { state.selectStPreset(if (active) "" else ref.id) }) {
                            Text(if (active) t("st.disable") else t("st.use"))
                        }
                        TextButton(onClick = { stViewId = ref.id }) { Text(t("st.view")) }
                        TextButton(onClick = { state.removeStPreset(ref.id) }) { Text(t("st.delete")) }
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { stImport.launch(arrayOf("application/json", "text/plain")) }) {
                    Text(t("st.import"))
                }
                // 导出：导出**当前选中**那一份
                val exportRef = settings.stPresetRefs.firstOrNull { it.id == settings.stPresetId }
                TextButton(
                    onClick = { stExport.launch(exportRef?.let { presetLabel(it) } ?: "preset.json") },
                    enabled = exportRef != null,
                ) { Text(t("st.export")) }
            }
        }
        stViewId?.let { viewId ->
            val ref = settings.stPresetRefs.firstOrNull { it.id == viewId }
            // ⚠️ key 用**整条 ref**（数据类，结构相等）：
            // 一开始只带了 disabledEntries / hasEdits —— 而"开启"改的是 enabledEntries ✗，
            // key 没变 → 列表不重算 → 开关点上去又弹回来（用户报"还是不能开"就是这个）。
            val entries = remember(viewId, ref) {
                state.stPresetEntriesForView(viewId)
            }
            var editingEntry by remember { mutableStateOf<StPreset.Entry?>(null) }
            AlertDialog(
                onDismissRequest = { stViewId = null },
                confirmButton = {
                    TextButton(onClick = { state.resetStPreset(viewId) }) { Text(t("st.reset")) }
                },
                dismissButton = {
                    TextButton(onClick = { stViewId = null }) { Text(t("st.close")) }
                },
                title = {
                    Text(
                        t("st.viewTitle") + " (" + entries.count { it.enabled } + "/" + entries.size + ")" +
                            if (ref?.hasEdits == true) " · " + t("st.edited") else "",
                    )
                },
                text = {
                    Column {
                        // 「删除关着的」= 手动瘦身（用户 2026-09-18：「加上删除功能」）。
                        // 单条删除在下面每一行；这里是一次性把没开着的都删掉。
                        TextButton(onClick = { state.pruneOffStPresetEntries(viewId) }) {
                            Text(t("st.deleteOff"))
                        }
                        LazyColumn(Modifier.heightIn(max = 420.dp)) {
                            items(entries) { entry ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            entry.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (entry.enabled) {
                                                com.kallan.naistudio.ui.LocalRef.current.text
                                            } else {
                                                com.kallan.naistudio.ui.LocalRef.current.muted
                                            },
                                        )
                                        Text(
                                            entry.role + " · " +
                                                (if (entry.marker) {
                                                    "marker"
                                                } else {
                                                    entry.content.length.toString() + " 字"
                                                }) +
                                                if (!entry.inOrder) {
                                                    " · " + t("st.notInOrder")
                                                } else {
                                                    ""
                                                },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = com.kallan.naistudio.ui.LocalRef.current.muted,
                                        )
                                    }
                                    if (!entry.marker) {
                                        TextButton(onClick = { editingEntry = entry }) {
                                            Text(t("st.entryEdit"))
                                        }
                                        // 单条删除：只动工作文件，`.orig` 留着 —— 删错了按「恢复默认」回来
                                        TextButton(
                                            onClick = { state.deleteStPresetEntry(viewId, entry.identifier) },
                                        ) { Text(t("st.delete")) }
                                    }
                                    Switch(
                                        checked = entry.enabled,
                                        onCheckedChange = { on ->
                                            state.setStPresetEntryEnabled(viewId, entry.identifier, on)
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
            editingEntry?.let { entry ->
                var draft by remember(entry.identifier) { mutableStateOf(entry.content) }
                AlertDialog(
                    onDismissRequest = { editingEntry = null },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                state.updateStPresetEntryContent(viewId, entry.identifier, draft)
                                editingEntry = null
                            },
                        ) { Text(t("llm.presetSave")) }
                    },
                    dismissButton = {
                        TextButton(onClick = { editingEntry = null }) { Text(t("llm.presetCancel")) }
                    },
                    title = { Text(entry.name) },
                    text = {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
        }
        // ---- AI 对话配置（用户 2026-09-15 要求）：思考程度 / 温度 / Top-P / 最大 tokens ----
        Text(
            t("llm.chatParamsTitle"),
            style = MaterialTheme.typography.labelMedium,
            color = com.kallan.naistudio.ui.LocalRef.current.muted,
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
        // ---- 2026-09-16 加回来 4 项：参考 ComfyUI「API LLM通用链路」节点的
        //      temperature / is_memory / conversation_rounds / is_enable ----
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
                // 节点是 0.0–1.0、step 0.1
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
                    color = com.kallan.naistudio.ui.LocalRef.current.muted,
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
    // exportLlmMemoryUri 留着没删（要用回来把按钮加回即可）。
        // ---- 上下文功能已删（用户 2026-09-18）----------------------------------------
        // 现在**默认就用安装包自带的那份上下文**（`app/src/main/assets/llm_context.json`，
        // 启动时由 `AppState.loadBundledLlmContext()` 写进 `llmUserHistoryJson`）：
        // 界面上不再有"导入上下文 / 清空上下文 / 从记忆导入"，也不再提供"导出记忆"。
        // ---- 2026-09-18（用户要求）：这里原来还有三个开关 ——「锁定结果（is_locked）」
        //      「流式输出（stream）」「主脑（main_brain）」—— 全部删掉。
        //      字段与行为**原样保留**（流式仍然是默认开、锁定仍然是关、主脑仍按存值决定
        //      要不要提供 another_llm 工具），要用回来把这三个 Row 加回即可。
        // ⚠️ 「AI 对话配置」现在**只剩「思考程度」一项**（用户 2026-09-16 要求）：
        // 温度滑条、Top-P 滑条、最大输出 tokens 滑条，以及「思考程度」下面那行说明，全部删除。
        // 采样参数改成固定值（见 LlmApi.AiParams）—— 见那边的注释，**不是**改成"不发"。
        // 选了百度翻译才显示百度那两个字段
        if (settings.translateService == "baidu") {
            Text(
                t("llm.baiduHint"),
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
            )
            OutlinedTextField(
                value = settings.baiduTranslateAppId,
                onValueChange = { value -> state.setSettings { it.copy(baiduTranslateAppId = value) } },
                label = { Text(t("llm.baiduAppId")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.baiduTranslateSecret,
                onValueChange = { value -> state.setSettings { it.copy(baiduTranslateSecret = value) } },
                label = { Text(t("llm.baiduSecret")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ---- 分功能 AI（用户 2026-09-16 要求）----
        // 每个用到 AI 的功能都能单独配一套；三项各自留空即回落，所以只换模型名时
        // 地址和密钥不用重抄（见 AppSettings.llmConfig 的注释）。
        Text(
            t("llm.perFeatureTitle"),
            style = MaterialTheme.typography.labelMedium,
            color = com.kallan.naistudio.ui.LocalRef.current.muted,
        )
        Text(
            t("llm.perFeatureHint"),
            style = MaterialTheme.typography.labelSmall,
            color = com.kallan.naistudio.ui.LocalRef.current.muted,
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
                color = com.kallan.naistudio.ui.LocalRef.current.text,
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
                        com.kallan.naistudio.ui.LocalRef.current.accent
                    } else {
                        com.kallan.naistudio.ui.LocalRef.current.muted
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
                    tint = com.kallan.naistudio.ui.LocalRef.current.muted,
                )
            }
        }
        if (!expanded) return@Column
        if (hint.isNotBlank()) {
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
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
            color = com.kallan.naistudio.ui.LocalRef.current.muted,
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
 *  · 图生图 / 局部重绘交由服务器按功能权限决定，拒绝原因按响应显示；
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
            color = com.kallan.naistudio.ui.LocalRef.current.muted,
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
                // 切回官方：只翻这个开关 —— **官方的地址与 token 一直原样留着**，
                // 不用重填（用户 2026-09-26：「第三方和官方的 api 分开存储啊，可切换」）。
                state.setSettings { it.copy(useThirdPartyApi = false) }
            }
            SourceChip(
                label = t("api.thirdParty"),
                hint = t("api.thirdPartyHint"),
                selected = gateway,
                modifier = Modifier.weight(1f),
            ) {
                // 切到第三方：同样只翻开关，**网关自己的地址与 Key 也一直留着**。
                state.setSettings { it.copy(useThirdPartyApi = true) }
                state.refreshGatewayQuota()
            }
        }
        if (gateway) {
            // ⚠️ 绑的是**网关自己那一份** `thirdPartyBaseUrl`，不是官方的 `imageBaseUrl` ——
            //    两档各存各的，来回切互不覆盖（这正是"分开存储"的意思）。
            var base by remember(settings.thirdPartyBaseUrl) {
                mutableStateOf(settings.thirdPartyBaseUrl)
            }
            OutlinedTextField(
                value = base,
                onValueChange = { value ->
                    base = value
                    state.setSettings { it.copy(thirdPartyBaseUrl = value) }
                },
                label = { Text(t("api.baseUrl")) },
                supportingText = { Text(t("api.baseUrlHint")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // 改完地址就把网关那块配额拉一次（拉不到就什么都不显示，见 GatewayQuotaRow）
            LaunchedEffect(base) { state.refreshGatewayQuota() }
            GatewayQuotaRow(state, t)
        }
        // ⚠️ token 那一段也要跟着走当前档：网关 → 网关那把 Key，官方 → 官方那把。
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
                    com.kallan.naistudio.ui.LocalRef.current.accent
                } else {
                    com.kallan.naistudio.ui.LocalRef.current.text
                },
            )
        }
        Text(
            hint,
            style = MaterialTheme.typography.labelSmall,
            color = com.kallan.naistudio.ui.LocalRef.current.muted,
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
                color = com.kallan.naistudio.ui.LocalRef.current.muted,
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
                    com.kallan.naistudio.ui.LocalRef.current.accent
                } else {
                    com.kallan.naistudio.ui.LocalRef.current.muted
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
                        color = com.kallan.naistudio.ui.LocalRef.current.muted,
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
                modifier = Modifier.fillMaxWidth(),
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
        modifier = Modifier.fillMaxWidth(),
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
            color = com.kallan.naistudio.ui.LocalRef.current.muted,
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
                        color = com.kallan.naistudio.ui.LocalRef.current.muted,
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
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(t("llm.url")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(t("llm.key")) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(t("llm.model")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error.isNotBlank()) {
                    Text(
                        error,
                        style = MaterialTheme.typography.labelSmall,
                        color = com.kallan.naistudio.ui.LocalRef.current.negative,
                    )
                }
                Text(
                    t("llm.presetQuickFill"),
                    style = MaterialTheme.typography.labelSmall,
                    color = com.kallan.naistudio.ui.LocalRef.current.muted,
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
                                color = com.kallan.naistudio.ui.LocalRef.current.muted,
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
