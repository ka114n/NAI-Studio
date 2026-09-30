package com.kallan.naistudio.models

import org.json.JSONArray
import org.json.JSONObject

/**
 * **预设库**（用户 2026-09-24 改版 ✓）。
 *
 * ## 这一版的口径（用户逐条点的 ✓）
 *
 * > 「预设功能改为**储存正面加负面**，**不是在分组里新加预设**，而是点击储存当前预设时
 * >  只显示**开关，名字，垃圾桶**，点击**下拉出现文本框**，可选**胶囊开关切换查看正面和负面**，
 * >  现有的预设**合并为正面提示词**」
 * > ＋ 追问时补的一句：「**正面到风格，负面进负面**，负面的**风格提示词栏删除**」✓
 *
 * ⇒ 于是：
 *  · **一条预设 = 名字 + 正面文本 + 负面文本** ✓（[StylePreset.positive] / [StylePreset.negative] ✓）；
 *  · **分组没了** ✗ —— 预览列表**平铺** ✓（[StylePresetLibrary.presets] 直接列 ✓）；
 *  · 开关打开时：**正面 → 风格提示词** ✓、**负面 → 负面提示词** ✓（见 [activePositive] / [activeNegative] ✓）；
 *  · 界面上「负面」那一侧**不再有风格提示词栏** ✓（那一栏只在正面出现 ✓）。
 *
 * ## 老数据怎么搬（**不丢** ✗ 是本文件最要紧的一条 ✓）
 *
 * 老结构是 `groups` + `presets`（带 `groupId`，正文只有一段 `prompt` ✓）。
 * 读老 JSON 时：
 *  · 老预设的那段 `prompt` ⇒ **挪到 [StylePreset.positive]** ✓ —— 正是用户说的
 *    「**现有的预设合并为正面提示词**」✓；
 *  · `negative` 留空 ✓（老数据里没有负面 ✓，**不编** ✗）；
 *  · 老的 `groups` **整块丢掉** ✓（用户说不要分组了 ✓）——
 *    但**先确认它确实没用**：老字段 `enabled` 决定"组内预设生不生效"✓，
 *    分组一丢，那条"组关掉 ⇒ 组内所有预设失效"的规则就**没法表达**了 ✓
 *    ⇒ 迁移时把**分组的 enabled 落到它组内每一条预设的开关上** ✓
 *    （组关着 ⇒ 那些预设读出来是**关**的 ✓）—— 不然"我明明关掉的分组，升级后全亮了" ✗。
 *
 * ⚠️ **`groupId` 字段留着** ✗（新写入恒为空串 ✓）：删字段会让**老 JSON 解析更啰嗦** ✓，
 *    而且备份文件（`BackupCodec` ✓）里那张表还带着它 ✓ —— 留着**只是不再用它做分组** ✓。
 */
data class StylePreset(
    val id: String,
    /** ⚠️ 老字段：分组 id ✓。**新数据恒为空串** ✓ —— 分组这个功能已经去掉了 ✓（见文件头 ✓）。 */
    val groupId: String = "",
    val name: String,
    /** **正面**（开关打开时拼进**风格提示词** ✓，用户口径：「正面到风格」✓）。 */
    val positive: String = "",
    /** **负面**（开关打开时拼进**负面提示词** ✓，用户口径：「负面进负面」✓）。 */
    val negative: String = "",
    val enabled: Boolean = false,
    val createdAt: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("groupId", groupId)
        put("name", name)
        put("positive", positive)
        put("negative", negative)
        put("enabled", enabled)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): StylePreset = StylePreset(
            id = json.optString("id"),
            groupId = json.optString("groupId", ""),
            name = json.optString("name", ""),
            // ⚠️ 老结构只有一段 `prompt` ✓ ⇒ 落到**正面** ✓（「现有的预设合并为正面提示词」✓）。
            //    新结构才有 `positive` ✓；两个都没有 ⇒ 空串 ✓（不编 ✗）。
            positive = json.optString("positive", "")
                .ifEmpty { json.optString("prompt", "") },
            negative = json.optString("negative", ""),
            enabled = json.optBoolean("enabled", false),
            createdAt = json.optString("createdAt", ""),
        )
    }
}

/** **预设库**：只有一张**平铺**的表 ✓（分组已经去掉 ✓，见 [StylePreset] 的文件头 ✓）。 */
data class StylePresetLibrary(
    val presets: List<StylePreset> = emptyList(),
) {
    /**
     * **开关打开的那几条，它们的正面词** ✓（按列表顺序拼 ✓，界面按顺序显示 ✓）。
     *
     * ⚠️ 空白的一条**要滤掉** ✗（否则会往提示词里拼进一个空串 + 一个分隔符 ✓）。
     */
    fun activePositive(): List<String> = presets
        .filter { it.enabled }
        .map { it.positive }
        .filter { it.isNotBlank() }

    /** **开关打开的那几条，它们的负面词** ✓（同上 ✓）。 */
    fun activeNegative(): List<String> = presets
        .filter { it.enabled }
        .map { it.negative }
        .filter { it.isNotBlank() }

    /** 开着的条数 ✓（界面上"已启用 N 条"那个数 ✓）。 */
    fun activeCount(): Int = presets.count { it.enabled }

    fun toJson(): JSONObject = JSONObject().apply {
        put("presets", JSONArray().apply { presets.forEach { put(it.toJson()) } })
    }

    companion object {
        /**
         * 读盘 ✓ —— **兼容老结构** ✗（`groups` + 带 `groupId` 的预设 ✓，见 [StylePreset] 的文件头 ✓）。
         */
        fun fromJson(json: JSONObject?): StylePresetLibrary {
            if (json == null) return StylePresetLibrary()
            val array = json.optJSONArray("presets") ?: return StylePresetLibrary()
            // 老结构里"分组的 enabled"决定组内预设生不生效 ✓ ⇒ 迁移时落到每条预设自己的开关上 ✓
            val offGroups: Set<String> = json.optJSONArray("groups")?.let { groups ->
                (0 until groups.length())
                    .mapNotNull { groups.optJSONObject(it) }
                    .filter { !it.optBoolean("enabled", true) }
                    .map { it.optString("id") }
                    .toSet()
            } ?: emptySet()
            val presets = (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { obj ->
                    val preset = StylePreset.fromJson(obj)
                    // 组关着 ⇒ 这条读出来是**关**的 ✓（组没了，这条规则只能落到预设自己身上 ✓）
                    if (preset.groupId.isNotEmpty() && preset.groupId in offGroups) {
                        preset.copy(enabled = false)
                    } else {
                        preset
                    }
                }
            }
            return StylePresetLibrary(presets = presets)
        }
    }
}
