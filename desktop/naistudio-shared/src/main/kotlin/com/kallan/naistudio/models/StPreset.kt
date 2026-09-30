package com.kallan.naistudio.models

import org.json.JSONArray
import org.json.JSONObject

/**
 * **SillyTavern Chat Completion 预设**的解析与归一化（用户 2026-09-17："加载这个文件"）。
 *
 * 口径**照 SillyTavern 源码**（release 分支，本地 grep 过，附行号）：
 *  · `INJECTION_POSITION = { RELATIVE: 0, ABSOLUTE: 1 }`、`DEFAULT_DEPTH = 4`、`DEFAULT_ORDER = 100`
 *    —— `PromptManager.js:31`、`PromptManager.js:37-40`
 *  ⚠️ **这两个名字的方向容易被"想当然"搞反**（我第一版就搞反了，用户一问才发现）：
 *  · **ABSOLUTE (1) = 按 depth 插进对话**：`openai.js:1334` 把 `absolutePrompts` 交给
 *    `populationInjectionPrompts()`；`PromptManager.js:571-572` 也只在 ABSOLUTE 时才显示 depth/order 输入框。
 *    该函数里：按 `injection_depth` **从对话末尾往前数第 N 条**插入；同 depth 内按 `injection_order` 降序，
 *    再按 role `system → user → assistant` 分组，**同组合并成一条**（`\n` 连接）—— `openai.js:820-870`
 *  · **RELATIVE (0) = 放在提示词序列里**（按 `prompt_order` 的位置排布，**不**做深度注入）：
 *    `openai.js:1242-1257` 的 `userRelativePrompts` 逐个 `addToChatCompletion()`；
 *    而 `openai.js:1198-1201` 明确**跳过** ABSOLUTE 的条目不进 prompt list。
 *  · `marker: true` 是**占位符**（`worldInfoBefore/After`、`charDescription`、`charPersonality`、
 *    `scenario`、`personaDescription`、`dialogueExamples`、`chatHistory`），内容由别处填 —— `openai.js:1212-1218`
 *  · `system_prompt: true` 是 ST 的核心条目（不可删、可停用）—— `PromptManager.js:635/659`
 *
 * 本文件是**纯函数**（不碰平台 API），两条线共用；单测见 `StPresetTest`。
 */
object StPreset {

    /** `injection_position`：**进提示词序列**（按 `prompt_order` 排布，不做深度注入）。 */
    const val POSITION_RELATIVE = 0

    /** `injection_position`：**按 depth 插进对话**（名字叫"绝对"，但实际是深度注入）。 */
    const val POSITION_ABSOLUTE = 1

    /** ST 默认插入深度 / 优先级。 */
    const val DEFAULT_DEPTH = 4
    const val DEFAULT_ORDER = 100

    /** 防止畸形文件把内存吃爆。 */
    const val MAX_PROMPTS = 5000

    /** 一条提示词条目。 */
    data class Entry(
        val identifier: String,
        val name: String,
        /** `system` / `user` / `assistant`。 */
        val role: String,
        val content: String,
        val enabled: Boolean,
        /**
         * [POSITION_RELATIVE]（0，进提示词序列）/ [POSITION_ABSOLUTE]（1，按 depth 插进对话）。
         * **别搞反**：名字是迷惑性的，见类注释。
         */
        val position: Int,
        val depth: Int,
        val order: Int,
        /** 占位符（内容由别处填，我们自己没有这些数据 → 跳过）。 */
        val marker: Boolean,
        /** ST 核心条目（`system_prompt: true`）。 */
        val core: Boolean,
        /**
         * 这条**在 `prompt_order` 里**吗（决定它有没有"位置"）。
         *
         * 文件里被停用的条目也在 order 里（`enabled=false`）→ 用户可以把它**重新开起来**；
         * 不在 order 里的条目没有位置，用户开启后会接在序列最后（我们的扩展，ST 不会发它们）。
         */
        val inOrder: Boolean = false,
    )

    /**
     * 解析后的预设。
     *
     * @param entries **按 `prompt_order` 排好序、且启用**的条目（真正要发出去的那批）
     * @param allEntries 全部条目（含停用的，给"只读查看器"用）
     */
    data class Preset(
        val name: String,
        val temperature: Double?,
        val topP: Double?,
        val frequencyPenalty: Double?,
        val presencePenalty: Double?,
        val maxTokens: Int?,
        /** `squash_system_messages`：相邻 system 消息是否合并（`openai.js:3919-3950`）。这份预设为 true。 */
        val squashSystemMessages: Boolean = false,
        /** 非 OpenAI 标准字段（`top_k` / `top_a` / `min_p` / `repetition_penalty`…）—— 原样保留，塞进 `extra_parameters`。 */
        val extraSamplers: Map<String, Double>,
        val entries: List<Entry>,
        val allEntries: List<Entry>,
        /** `prompt_order` 里**出现过的**标识符（不在里面的条目即使 enabled 也不发）。 */
        val orderedIds: List<String> = emptyList(),
    ) {
        /** 启用条目的正文字符数（不含 marker，它们没有正文）。 */
        val charCount: Int get() = entries.sumOf { if (it.marker) 0 else it.content.length }

        /** 粗估 token：**3 字符 ≈ 1 token**（实测那份 19,675 字符 ≈ 6.5k，够准；真实值看服务商分词）。 */
        val estimatedTokens: Int get() = (charCount + 2) / 3

        /** 有正文、需要注入的条目（marker 与空内容不算）。 */
        val payloadEntries: List<Entry> get() = entries.filter { !it.marker && it.content.isNotBlank() }
    }

    /** 非标准采样字段（OpenAI 兼容接口不认，但部分中转认）—— 按需塞 `extra_parameters`。 */
    val EXTRA_SAMPLER_KEYS = listOf(
        "top_k", "top_a", "min_p", "repetition_penalty", "typical_p", "tfs", "epsilon_cutoff",
        "eta_cutoff", "smoothing_factor", "smoothing_curve", "dry_multiplier", "xtc_threshold",
    )

    /**
     * 解析预设 JSON。
     *
     * @param fallbackName 文件里没有名字时用的名字（调用方一般传文件名）
     * @return 解析失败（不是 JSON / 没有 prompts）返回 null
     */
    fun parse(json: String, fallbackName: String = "预设"): Preset? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val promptsArray = root.optJSONArray("prompts") ?: return null

        val all = ArrayList<Entry>()
        for (index in 0 until minOf(promptsArray.length(), MAX_PROMPTS)) {
            val item = promptsArray.optJSONObject(index) ?: continue
            val identifier = item.optText("identifier").ifBlank { "prompt-$index" }
            all.add(
                Entry(
                    identifier = identifier,
                    name = item.optText("name").ifBlank { identifier },
                    role = item.optText("role").ifBlank { "system" }.lowercase(),
                    content = item.optText("content"),
                    enabled = item.optBoolean("enabled", true),
                    position = item.optInt("injection_position", POSITION_RELATIVE),
                    depth = item.optInt("injection_depth", DEFAULT_DEPTH),
                    order = item.optInt("injection_order", DEFAULT_ORDER),
                    marker = item.optBoolean("marker", false),
                    core = item.optBoolean("system_prompt", false),
                ),
            )
        }
        if (all.isEmpty()) return null

        val byId = all.associateBy { it.identifier }
        val orderedAll = orderedEntries(root, byId)
        val ordered = orderedAll.filter { it.enabled }
        // 把"在不在 order 里 / 文件里是否启用"回填到全部条目（查看器与"逐条自定义"都靠它）
        val orderedById = orderedAll.associateBy { it.identifier }
        val allWithFlags = all.map { entry ->
            val fromOrder = orderedById[entry.identifier]
            if (fromOrder == null) {
                entry.copy(enabled = false, inOrder = false)
            } else {
                entry.copy(enabled = fromOrder.enabled, inOrder = true)
            }
        }

        val extra = LinkedHashMap<String, Double>()
        EXTRA_SAMPLER_KEYS.forEach { key ->
            if (root.has(key)) {
                val value = root.optDouble(key, Double.NaN)
                if (!value.isNaN()) extra[key] = value
            }
        }

        return Preset(
            name = fallbackName,
            temperature = root.optDoubleOrNull("temperature"),
            topP = root.optDoubleOrNull("top_p"),
            frequencyPenalty = root.optDoubleOrNull("frequency_penalty"),
            presencePenalty = root.optDoubleOrNull("presence_penalty"),
            maxTokens = root.optIntOrNull("openai_max_tokens"),
            squashSystemMessages = root.optBoolean("squash_system_messages", false),
            extraSamplers = extra,
            entries = ordered,
            allEntries = allWithFlags,
            orderedIds = orderedAll.map { it.identifier },
        )
    }

    /**
     * **只留开着的条目，其余真的删掉**（用户 2026-09-18：「只保留开启的片段，其余删除」）。
     *
     * 导入时就把文件里**没启用**的条目从 JSON 里去掉 —— 不是"存着再用标志位跳过"。
     * 一份 134 条的 TGbreak 预设里只有 25 条是开着的，剩下 109 条既占体积（373 KB → 几十 KB），
     * 又让界面上一堆用不上的东西。
     *
     * 谁算"开着"**完全按 [parse] 的口径**（顺序表说了算，见 [orderedEntries]）：
     *  · 在 `prompt_order` 里且 `enabled=true` 的 → 留；
     *  · 不在顺序表里的（包括它自己写着 `enabled=true` 的）→ 删（ST 本来就不会发它们）；
     *  · `prompt_order` 跟着只留这些 id，**顺序不变**；留下的条目 `enabled` 统一写 true。
     *
     * 其它字段（采样参数 / `extensions` / 各种 prompt 模板）**原样保留**。
     * 解析不了、或者一条都没开着，就**原样返回**（宁可不动，也别把文件删空/把导入搞挂）。
     */
    fun pruneToEnabled(json: String): String {
        val preset = parse(json, "__prune__") ?: return json
        val keep = preset.allEntries.filter { it.enabled }.map { it.identifier }.toSet()
        return pruneToIds(json, keep)
    }

    /**
     * **只留这些 id，其余真的删掉**（[pruneToEnabled] 的底层；也用于「查看」里的删条目）。
     *
     * ⚠️ **不再改动留下条目的 `enabled`**（用户 2026-09-18 报的 bug：删一条之后"全部自动打开"）。
     * 之前这里把留下的条目统一写成 `enabled=true` ✗ —— 对"开关烘在文件里"的预设（比如 6 开 / 119 关），
     * 删掉任意一条就会把剩下 119 条**全打开**。现在原样保留各自的开关，只做"按 id 过滤"。
     *
     * @param keep 要留下的条目 id（空集合 = 不删，原样返回 —— 绝不把文件删空）
     * @param appendMissingToOrder 留下来的条目里**不在 `prompt_order` 里**的，是否接到目标分组末尾
     *   （`character_id = 100000`，没有就用第一组）。
     *   `false`（默认）= 保持"不在顺序里"的原状（`parse` 会把它当关着，跟删除前一致）；
     *   `true` = 用在"删掉所有关着的"那条路上 —— 那份 keep 是"用户实际会发出去"的集合，
     *   不接回顺序表的话，用户手动开启的 extras 会被当成关着丢掉。
     */
    fun pruneToIds(json: String, keep: Set<String>, appendMissingToOrder: Boolean = false): String {
        if (keep.isEmpty()) return json
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return json
        val prompts = root.optJSONArray("prompts") ?: return json

        val keptPrompts = JSONArray()
        for (index in 0 until prompts.length()) {
            val item = prompts.optJSONObject(index) ?: continue
            val identifier = item.optText("identifier").ifBlank { "prompt-$index" }
            if (identifier !in keep) continue
            keptPrompts.put(item) // ⚠️ 开关原样保留，别动
        }
        if (keptPrompts.length() == 0) return json
        root.put("prompts", keptPrompts)

        // 顺序表也一起瘦身（分组结构原样保留，只换每个分组的 order 数组）
        root.optJSONArray("prompt_order")?.let { groups ->
            val inOrder = LinkedHashSet<String>()
            var target: JSONObject? = null
            for (g in 0 until groups.length()) {
                val group = groups.optJSONObject(g) ?: continue
                if (target == null) target = group
                if (group.optLong("character_id", -1L) == 100000L) target = group
                val order = group.optJSONArray("order") ?: continue
                val keptOrder = JSONArray()
                for (index in 0 until order.length()) {
                    val item = order.optJSONObject(index) ?: continue
                    val identifier = item.optText("identifier")
                    if (identifier !in keep) continue
                    inOrder.add(identifier)
                    keptOrder.put(item) // ⚠️ 开关原样保留
                }
                group.put("order", keptOrder)
            }
            if (appendMissingToOrder && target != null) {
                val order = target.optJSONArray("order") ?: JSONArray().also { target.put("order", it) }
                keep.filter { it !in inOrder }.forEach { id ->
                    order.put(JSONObject().apply { put("identifier", id); put("enabled", true) })
                }
            }
        }
        return root.toString()
    }

    /**
     * 按 `prompt_order` 取"实际会发出去"的条目。
     *
     * ST 的口径：**只有出现在 order 列表里的条目才参与**（不在里面的即使 `enabled` 也不发）；
     * 每条在 order 里还带一个自己的 `enabled`（与 `prompts[].enabled` 取"与"更保守，这里按 ST 前端
     * 的行为取 **order 里的那个为准**，缺失才回落到 prompts 里的）。
     *
     * `prompt_order` 是按 `character_id` 分组的数组，全局那份是 `100000`。
     */
    private fun orderedEntries(root: JSONObject, byId: Map<String, Entry>): List<Entry> {
        val groups = root.optJSONArray("prompt_order") ?: return byId.values.filter { it.enabled }
        if (groups.length() == 0) return byId.values.filter { it.enabled }
        var chosen: JSONObject? = null
        for (index in 0 until groups.length()) {
            val group = groups.optJSONObject(index) ?: continue
            if (group.optLong("character_id", -1L) == 100000L) {
                chosen = group
                break
            }
            if (chosen == null) chosen = group
        }
        val order = chosen?.optJSONArray("order") ?: return byId.values.filter { it.enabled }

        // ⚠️ 返回**完整顺序**（含文件里被停用的那些）—— 否则"文件停用的条目"永远开不起来（用户报过"能关不能开"）
        val out = ArrayList<Entry>()
        for (index in 0 until order.length()) {
            val item = order.optJSONObject(index) ?: continue
            val identifier = item.optText("identifier")
            val entry = byId[identifier] ?: continue
            val orderEnabled = if (item.has("enabled")) item.optBoolean("enabled", true) else entry.enabled
            out.add(entry.copy(enabled = orderEnabled, inOrder = true))
        }
        return out
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key)) optDouble(key, Double.NaN).takeIf { !it.isNaN() } else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key)) optInt(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE } else null

    // ------------------------------------------------------------------ 宏

    /** 宏展开时替换的"人名"（我们没有角色卡，给两个可配置的名字）。 */
    data class MacroNames(val user: String = "用户", val character: String = "角色")

    /** 一次宏展开的结果（顺带告诉我们剥掉了多少个不认识的宏，好在日志里说清楚）。 */
    data class Expanded(val text: String, val unknownMacros: Int, val variables: Int)

    /**
     * 展开 ST 宏。**这是必须做的一步**：那份预设里 `{{setvar::…}}/{{getvar::…}}` 出现 15 次，
     * 不处理就会把宏原文当正文发给模型。
     *
     * 支持：`{{user}}` / `{{char}}` / `{{setvar::名字::值}}`（写变量，**自身输出空**）/
     * `{{getvar::名字}}`（读，未定义 → 空）。
     * 其余未知宏 → **剥掉**（并计数）。
     *
     * @param vars 跨条目共享的变量表（同一次请求内有效；ST 的变量是持久化的，我们只做会话内）
     */
    fun expandMacros(text: String, names: MacroNames = MacroNames(), vars: MutableMap<String, String> = mutableMapOf()): Expanded {
        if (!text.contains("{{")) return Expanded(text, 0, 0)
        // 三花括号（`{{{getvar::x}}`）是 ST 里的写法之一：先归一成两层，免得正则漏掉
        val normalized = text.replace("{{{", "{{")
        var unknown = 0
        var applied = 0
        val out = StringBuilder()
        var index = 0
        val regex = Regex("\\{\\{([^{}]*)\\}\\}")
        for (match in regex.findAll(normalized)) {
            out.append(normalized, index, match.range.first)
            val body = match.groupValues[1].trim()
            val replaced = when {
                body.equals("user", ignoreCase = true) -> names.user
                body.equals("char", ignoreCase = true) -> names.character
                body.startsWith("setvar::", ignoreCase = true) -> {
                    val parts = body.split("::", limit = 3)
                    if (parts.size >= 2) {
                        vars[parts[1].trim()] = parts.getOrElse(2) { "" }
                        applied++
                        ""
                    } else {
                        unknown++
                        ""
                    }
                }
                body.startsWith("getvar::", ignoreCase = true) -> {
                    val key = body.split("::", limit = 2).getOrElse(1) { "" }.trim()
                    applied++
                    vars[key].orEmpty()
                }
                else -> {
                    unknown++
                    ""
                }
            }
            out.append(replaced)
            index = match.range.last + 1
        }
        out.append(normalized, index, normalized.length)
        return Expanded(out.toString(), unknown, applied)
    }

    /** 把一批条目里的宏全部展开（同一次请求共享一张变量表）。 */
    fun expandAll(entries: List<Entry>, names: MacroNames = MacroNames()): Pair<List<Entry>, Expanded> {
        val vars = mutableMapOf<String, String>()
        var unknown = 0
        var applied = 0
        val out = entries.map { entry ->
            if (!entry.content.contains("{{")) return@map entry
            val expanded = expandMacros(entry.content, names, vars)
            unknown += expanded.unknownMacros
            applied += expanded.variables
            entry.copy(content = expanded.text)
        }
        return out to Expanded("", unknown, applied)
    }
}

/**
 * **预设引用**（存进设置里那份；正文不在这里，落盘在 `filesDir/st_presets/<id>.json`）。
 *
 * 为什么不把正文塞进设置：ST 预设动辄几百 KB（用户那份 415 KB），settings 是**每次改都整体落盘**的 ✗。
 */
data class StPresetRef(
    val id: String,
    val name: String,
    /** 落盘文件名（`filesDir/st_presets/` 下）。 */
    val fileName: String,
    /** 条目数 / 估算 token（界面上直接显示，不必读盘）。 */
    val entryCount: Int = 0,
    val estimatedTokens: Int = 0,
    /** 用户**逐条停用**的条目标识（手机端"自定义"用；空 = 全部按预设原样）。 */
    val disabledEntries: List<String> = emptyList(),
    /**
     * 用户**逐条显式开启**的条目标识 —— 这是"能关不能开"那个 bug 的修复：
     *
     * 光有停用清单只能"减"，**文件里本来就停用的条目永远开不起来** ✗（用户实测报过）。
     * 所以改成双向：`enabledEntries` 里的一定发（覆盖文件里的停用），`disabledEntries` 里的一定不发。
     */
    val enabledEntries: List<String> = emptyList(),
    /** 用户改过正文（`恢复默认` 会把原始副本覆盖回来）。 */
    val hasEdits: Boolean = false,
    /** 是否留着原始副本（`<id>.orig.json`）。 */
    val hasOriginal: Boolean = false,
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", id)
        put("name", name)
        put("fileName", fileName)
        put("entryCount", entryCount)
        put("estimatedTokens", estimatedTokens)
        put(
            "disabledEntries",
            org.json.JSONArray().apply { disabledEntries.forEach { put(it) } },
        )
        put(
            "enabledEntries",
            org.json.JSONArray().apply { enabledEntries.forEach { put(it) } },
        )
        put("hasEdits", hasEdits)
        put("hasOriginal", hasOriginal)
    }

    companion object {
        fun fromJson(json: org.json.JSONObject): StPresetRef = StPresetRef(
            id = json.optString("id"),
            name = json.optText("name"),
            fileName = json.optString("fileName"),
            entryCount = json.optInt("entryCount"),
            estimatedTokens = json.optInt("estimatedTokens"),
            disabledEntries = json.optJSONArray("disabledEntries")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf { id -> id.isNotBlank() } }
            } ?: emptyList(),
            enabledEntries = json.optJSONArray("enabledEntries")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf { id -> id.isNotBlank() } }
            } ?: emptyList(),
            hasEdits = json.optBoolean("hasEdits", false),
            hasOriginal = json.optBoolean("hasOriginal", false),
        )
    }
}

/**
 * 读一段**可能是 JSON `null`** 的文本（`optString` 遇到 `null` 会给出字面量 "null" ✗）。
 *
 * 用户 2026-09-18 报"一直回 nullnull 循环后接正文"：预设里 `content: null` 的条目被读成 "null" 当正文发出去，
 * 模型看到就继续模仿。所以预设的每个字段都走这里。
 */
private fun JSONObject.optText(key: String): String =
    if (!has(key) || isNull(key)) "" else optString(key)

/**
 * **按用户的停用清单过滤**（手机端"逐条自定义"；纯函数，可单测）。
 *
 *  · `entries`（真正发出去的）去掉被停用的；
 *  · `allEntries` 的 `enabled` 改成"在 order 里 **且** 没被停用"，给查看器显示真实状态。
 *
 * ⚠️ 放在**文件顶层**而不是 `object StPreset` 里：object 内的扩展函数外面调不到（要 `StPreset.run { }`），
 * 调用方（AppState）用起来太别扭。
 */
fun StPreset.Preset.withOverrides(
    userEnabled: Collection<String>,
    userDisabled: Collection<String>,
): StPreset.Preset {
    if (userEnabled.isEmpty() && userDisabled.isEmpty()) return this
    val on = userEnabled.toSet()
    val off = userDisabled.toSet()
    fun isOn(entry: StPreset.Entry): Boolean = when {
        entry.identifier in off -> false
        entry.identifier in on -> true
        else -> entry.enabled
    }
    val inOrderIds = orderedIds.toSet()
    // 顺序里启用的那些（保持原顺序）；文件被停用的可以由用户显式开启
    val sequence = orderedIds
        .mapNotNull { id -> allEntries.firstOrNull { it.identifier == id } }
        .filter { isOn(it) }
    // 不在顺序里、但用户手动开了的：接在序列最后（ST 不会发它们，这是我们加的）
    val extras = allEntries.filter { entry ->
        !entry.marker && entry.identifier !in inOrderIds && isOn(entry) && entry.content.isNotBlank()
    }
    return copy(
        entries = sequence + extras,
        allEntries = allEntries.map { it.copy(enabled = isOn(it)) },
    )
}

/** 兼容旧调用：只有"停用清单"（等价于双向覆盖里只给了停用那一侧）。 */
fun StPreset.Preset.withDisabled(disabled: Collection<String>): StPreset.Preset =
    withOverrides(emptyList(), disabled)
