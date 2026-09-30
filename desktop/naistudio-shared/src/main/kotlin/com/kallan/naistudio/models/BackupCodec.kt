package com.kallan.naistudio.models

import com.kallan.naistudio.services.PromptRules
import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份文件的编解码（**纯逻辑、不碰 Android**，所以能写 JVM 单测）。
 *
 * ## 格式规范（v1）
 *
 * ```jsonc
 * {
 *   "format": "naistudio.backup",       // 固定标识，认不出来就拒绝导入
 *   "formatVersion": 1,                 // 结构版本；比本 App 支持的还新 → 拒绝
 *   "exportedAt": "2026-09-12T05:40:00Z",// ISO-8601 UTC
 *   "app": { "package": "com.kallan.naistudio", "versionName": "0.1.0" },
 *   "payload": {                        // 每个小节都可以缺省 = 不覆盖那一部分
 *     "settings":        { ...AppSettings.toBackupJson()... }, // 设置页的参数，**不含凭证**
 *     "promptRules":     { ...优化/反推规则（单独一节，见下）... },
 *     "generateParams":  { ...GenerateParamsCodec... },
 *     "stylePresets":    { ...StylePresetLibrary.toJson()... },
 *     "characterPrompts": [ ...CharCaptionCodec... ]
 *   }
 * }
 * ```
 *
 * ## ⚠️ 备份**不带凭证**
 *
 * `settings` 那一节走的是 [AppSettings.toBackupJson]，会剔掉
 * `llmApiKey` / `baiduTranslateAppId` / `baiduTranslateSecret`
 * （见 [AppSettings.SECRET_KEYS]）。理由与代价权衡写在那边的注释里。
 *
 * 导入侧不需要特殊处理：`settingsOverlay` 是**逐键合并**
 *（[mergeSettingsJson]），备份里没有的键根本不会碰本机设置 ——
 * 所以导入一份别人的备份**不会**把你的 API Key 清掉，也不会带进他的。
 *
 * `promptRules` 是**给人看/给别的工具读**的一节（设置页「提示词优化与反推规则」那两段
 * system prompt，三种模式各一份）；规则本身也在 `settings` 的 `optimizeRules*` /
 * `reverseRules*` 字段里，导入时两节会**按字段合并**，所以只有 `promptRules` 的手写备份
 * 也能单独恢复规则。
 *
 * 几条硬规则：
 *  · **token 永不进备份**（`AppSettings` 里本来也没有 token，它单独存在 `TokenVault` 里）；
 *  · 导入时**先校验 `format` / `formatVersion`**，不认识就报中文错，绝不半途改状态；
 *  · 缺省的小节 / 缺省的字段**不动**本地对应数据（导入是"按备份里有的键覆盖"，
 *    所以老备份缺的新字段不会被重置成默认值）；
 *  · 未知字段忽略（留给以后加字段）；导入前可以用 [Summary] 告诉用户"这份备份里有什么"。
 */
object BackupCodec {

    /** 文件标识：认不出这个字符串就不是本 App 的备份。 */
    const val FORMAT = "naistudio.backup"

    /** 结构版本：加字段不用动它，只有**不兼容地改结构**时才 +1。 */
    const val FORMAT_VERSION = 1

    const val APP_PACKAGE = "com.kallan.naistudio"

    /** payload 里的键名（`promptRules` 是单独一节，见类注释）。 */
    private const val KEY_SETTINGS = "settings"
    private const val KEY_PROMPT_RULES = "promptRules"
    private const val KEY_PARAMS = "generateParams"
    private const val KEY_STYLES = "stylePresets"
    private const val KEY_CHARACTERS = "characterPrompts"

    /**
     * 漫画模式小节。**新增的可缺省小节**：老备份没有它 → 导入时不动本机的漫画数据；
     * 老版本 App 读到它也只是忽略未知键。所以两个方向都兼容。
     */
    private const val KEY_COMIC = "comic"

    /** 摘要里的小节标识。 */
    const val SECTION_SETTINGS = "settings"
    const val SECTION_PARAMS = "params"
    const val SECTION_STYLES = "styles"
    const val SECTION_CHARACTERS = "characters"
    const val SECTION_COMIC = "comic"

    /** 漫画模式小节：四项设置 + 分格列表（与角色分区**分别存储**）。 */
    data class ComicBundle(
        val settings: ComicSettings = ComicSettings(),
        val panels: List<CharCaptionItem> = emptyList(),
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("settings", settings.toJson())
            put(
                "panels",
                JSONArray().apply { panels.forEach { put(CharCaptionCodec.toJson(it)) } },
            )
        }

        companion object {
            fun fromJson(json: JSONObject?): ComicBundle? {
                if (json == null) return null
                val panels = json.optJSONArray("panels")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optJSONObject(index)?.let { CharCaptionCodec.fromJson(it) }
                    }
                } ?: emptyList()
                return ComicBundle(
                    settings = ComicSettings.fromJson(json.optJSONObject("settings")),
                    panels = panels,
                )
            }
        }
    }

    /** 备份内容（各节可空 = 这份备份里没有这一节）。 */
    data class Bundle(
        val settings: AppSettings? = null,
        val params: GenerateParams? = null,
        val styleLibrary: StylePresetLibrary? = null,
        val charCaptions: List<CharCaptionItem>? = null,
        /** 漫画模式那一节（可空 = 这份备份里没有）。 */
        val comic: ComicBundle? = null,
        /**
         * 设置 + 提示词规则的**原始 JSON 覆盖层**（导入时用）。
         *
         * 为什么不直接拿 [settings] 去覆盖本机设置：`AppSettings.fromJson` 会给缺失的键
         * 填**默认值**，所以"备份里没有这个键"和"备份里这个键就是默认值"分不出来 ——
         * 直接覆盖会把本机的新设置（老备份里还不存在的字段）悄悄重置成默认值。
         * 导入时按 [mergeSettingsJson] 逐键覆盖，只动备份里真正写了的字段。
         */
        val settingsOverlay: JSONObject? = null,
        /**
         * 这份备份里**有没有 `promptRules` 那一节**。
         *
         * 导出时只要带设置就会写上这一节；解析时按 payload 里实际有没有来定
         * （老备份只有 `settings`，没有这一节 —— 规则照样在 `settings` 的字段里）。
         */
        val promptRulesSection: Boolean = false,
    ) {
        /** 解开之后给 UI 看的摘要（"这份备份里有什么"）。 */
        val summary: Summary
            get() = Summary(
                hasSettings = settings != null,
                hasParams = params != null,
                // ⚠️ 分组功能已去掉（用户 2026-09-24）⇒ 这里**恒为 0**（字段留着：老备份文件里还有它）
                  styleGroups = 0,
                stylePresets = styleLibrary?.presets?.size ?: 0,
                characters = charCaptions?.size ?: 0,
                comicPanels = comic?.panels?.size ?: 0,
                // 多页（狂暴模式）：只报当前页的格数会让"6 页的漫画"看起来像一页，
                // 所以页数单独报一份
                comicPages = comic?.settings?.pages?.size ?: 0,
                // 解析出来的备份看"有没有那一节"；手工构造的导出用 Bundle 只要带设置就会写那一节
                hasPromptRules = promptRulesSection || (settings != null && settingsOverlay == null),
            )
    }

    data class Summary(
        val hasSettings: Boolean,
        val hasParams: Boolean,
        val styleGroups: Int,
        val stylePresets: Int,
        val characters: Int,
        /** 漫画分格数量（当前页；0 = 这份备份里没有漫画数据）。 */
        val comicPanels: Int = 0,
        /**
         * 漫画**页数**（狂暴模式的多页漫画）。
         *
         * `0` 或 `1` = 单页时代的备份，界面只报格数；
         * `>1` 时界面会改成"共 N 页" —— 只看 `comicPanels` 会把 6 页的漫画说成
         * "漫画分格 4 个"，那是在骗人。
         */
        val comicPages: Int = 0,
        /** 这份备份里带没带提示词规则那一节（v1 早期的备份没有）。 */
        val hasPromptRules: Boolean = false,
    ) {
        /** 这份备份是不是空的（什么都没有）。 */
        val isEmpty: Boolean
            get() = !hasSettings && !hasParams && styleGroups == 0 &&
                stylePresets == 0 && characters == 0 && comicPanels == 0 && comicPages == 0

        /** 备份里的小节标识（顺序固定；界面按它拼"这份备份里有什么"）。 */
        val sections: List<String>
            get() = buildList {
                if (hasSettings) add(SECTION_SETTINGS)
                if (hasParams) add(SECTION_PARAMS)
                if (styleGroups > 0 || stylePresets > 0) add(SECTION_STYLES)
                if (characters > 0) add(SECTION_CHARACTERS)
                if (comicPanels > 0 || comicPages > 0) add(SECTION_COMIC)
            }
    }

    class BackupException(message: String) : Exception(message)

    fun encode(
        bundle: Bundle,
        appVersion: String,
        exportedAt: String,
        /** true = 缩进排版（存成 .json 好看）；false = 紧凑（**塞进图片里用**）。 */
        pretty: Boolean = true,
    ): String {
        val payload = JSONObject().apply {
            bundle.settings?.let { settings ->
                // ⚠️ 用 toBackupJson() 而**不是** toJson()：备份不带 API Key 一类的凭证
                //（用户要求"llm 的密钥和翻译密钥不备份"）。见 AppSettings.SECRET_KEYS。
                put(KEY_SETTINGS, settings.toBackupJson())
                // 规则**单独再写一节**：文件自己说得清"规则也备份了"，别的工具也能只读这一节
                put(KEY_PROMPT_RULES, promptRulesJson(settings))
            }
            bundle.params?.let { put(KEY_PARAMS, GenerateParamsCodec.toJson(it)) }
            bundle.styleLibrary?.let { put(KEY_STYLES, it.toJson()) }
            bundle.charCaptions?.let { items ->
                put(
                    KEY_CHARACTERS,
                    JSONArray().apply { items.forEach { put(CharCaptionCodec.toJson(it)) } },
                )
            }
            bundle.comic?.let { put(KEY_COMIC, it.toJson()) }
        }
        return JSONObject().apply {
            put("format", FORMAT)
            put("formatVersion", FORMAT_VERSION)
            put("exportedAt", exportedAt)
            put(
                "app",
                JSONObject().apply {
                    put("package", APP_PACKAGE)
                    put("versionName", appVersion)
                },
            )
            put("payload", payload)
        }.let { if (pretty) it.toString(2) else it.toString() }
    }

    /** 解析备份文本；任何不合法都以 [BackupException] 抛出（消息是给用户看的中文）。 */
    fun decode(text: String): Bundle {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BackupException("这不是一个合法的 JSON 文件")
        }
        val format = root.optString("format", "")
        if (format != FORMAT) {
            throw BackupException("这不是 NAI Studio 的备份文件（缺少 format 标识）")
        }
        val version = root.optInt("formatVersion", 0)
        if (version <= 0) {
            throw BackupException("备份文件缺少 formatVersion")
        }
        if (version > FORMAT_VERSION) {
            throw BackupException("备份来自更新的版本（v$version），当前只支持到 v$FORMAT_VERSION")
        }
        val payload = root.optJSONObject("payload")
            ?: throw BackupException("备份文件里没有 payload")

        // 设置 + 规则合成"覆盖层"：settings 是主体，promptRules 那一节写回同样的字段
        val settingsJson = payload.optJSONObject(KEY_SETTINGS)
        val rulesJson = payload.optJSONObject(KEY_PROMPT_RULES)?.let { promptRulesToSettingsJson(it) }
        val overlay = when {
            settingsJson != null && rulesJson != null -> mergeSettingsJson(settingsJson, rulesJson)
            settingsJson != null -> settingsJson
            rulesJson != null -> rulesJson
            else -> null
        }

        val settings = overlay?.let { AppSettings.fromJson(it) }
        val params = payload.optJSONObject(KEY_PARAMS)?.let { GenerateParamsCodec.fromJson(it) }
        val styleLibrary = payload.optJSONObject(KEY_STYLES)?.let { StylePresetLibrary.fromJson(it) }
        val charCaptions = payload.optJSONArray(KEY_CHARACTERS)?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                CharCaptionCodec.fromJson(item)
            }
        }

        val bundle = Bundle(
            settings = settings,
            params = params,
            styleLibrary = styleLibrary,
            charCaptions = charCaptions,
            comic = ComicBundle.fromJson(payload.optJSONObject(KEY_COMIC)),
            settingsOverlay = overlay,
            promptRulesSection = payload.has(KEY_PROMPT_RULES),
        )
        if (bundle.summary.isEmpty) {
            throw BackupException("备份里没有任何可以恢复的内容")
        }
        return bundle
    }

    /**
     * 逐键覆盖：以 [base]（本机当前设置）为底，把 [incoming]（备份里的设置）里写了的键盖上去。
     *
     * 这样"备份里没有的键"保持本机现值 —— 老备份（没有后来新增的字段）导入后
     * 不会把新字段重置成默认值。
     */
    fun mergeSettingsJson(base: JSONObject, incoming: JSONObject): JSONObject {
        val merged = JSONObject()
        base.keys().forEach { key -> merged.put(key, base.opt(key)) }
        incoming.keys().forEach { key -> merged.put(key, incoming.opt(key)) }
        return merged
    }

    /** 把设置里的六段规则抽成单独一节（文件里看得见的那份）。 */
    private fun promptRulesJson(settings: AppSettings): JSONObject = JSONObject().apply {
        put("optimizeMode", settings.optimizeRuleMode)
        put(
            "optimize",
            JSONObject().apply {
                put(PromptRules.MODE_TAG, settings.optimizeRulesTag)
                put(PromptRules.MODE_NATURAL, settings.optimizeRulesNatural)
                put(PromptRules.MODE_MIXED, settings.optimizeRulesMixed)
            },
        )
        put("reverseMode", settings.reverseRuleMode)
        put(
            "reverse",
            JSONObject().apply {
                put(PromptRules.MODE_TAG, settings.reverseRulesTag)
                put(PromptRules.MODE_NATURAL, settings.reverseRulesNatural)
                put(PromptRules.MODE_MIXED, settings.reverseRulesMixed)
            },
        )
        // 漫画那两套规则（分镜 / 分页规划）也放进这一节（用户 2026-09-17：
        // "备份也备份设置里的各个规则"）—— 这样文件里四套规则都看得见、手写备份也能设它们。
        put(
            "comic",
            JSONObject().apply {
                put("storyboard", settings.comicStoryboardRules)
                put("pagePlan", settings.comicPagePlanRules)
                put("version", settings.comicRulesVersion)
            },
        )
    }

    /** [promptRulesJson] 的逆运算：摊平成 `settings` 里的字段名（好和 settings 合并）。 */
    private fun promptRulesToSettingsJson(rules: JSONObject): JSONObject = JSONObject().apply {
        rules.optString("optimizeMode").takeIf { it.isNotEmpty() }
            ?.let { put("optimizeRuleMode", it) }
        rules.optString("reverseMode").takeIf { it.isNotEmpty() }
            ?.let { put("reverseRuleMode", it) }
        rules.optJSONObject("optimize")?.let { optimize ->
            putRule(optimize, PromptRules.MODE_TAG, "optimizeRulesTag")
            putRule(optimize, PromptRules.MODE_NATURAL, "optimizeRulesNatural")
            putRule(optimize, PromptRules.MODE_MIXED, "optimizeRulesMixed")
        }
        rules.optJSONObject("reverse")?.let { reverse ->
            putRule(reverse, PromptRules.MODE_TAG, "reverseRulesTag")
            putRule(reverse, PromptRules.MODE_NATURAL, "reverseRulesNatural")
            putRule(reverse, PromptRules.MODE_MIXED, "reverseRulesMixed")
        }
        // 漫画那两套规则（用户 2026-09-17）
        rules.optJSONObject("comic")?.let { comic ->
            putRule(comic, "storyboard", "comicStoryboardRules")
            putRule(comic, "pagePlan", "comicPagePlanRules")
            // 版本跟着一起还：否则恢复回来的规则会被"老规则迁移"当成待升级的再覆盖一次
            if (comic.has("version")) put("comicRulesVersion", comic.optInt("version", PromptRules.COMIC_RULES_VERSION))
        }
    }

    /** 把规则一节里的某一段写进设置字段（缺失/为 null 就不写，保持本机值）。 */
    private fun JSONObject.putRule(source: JSONObject, mode: String, target: String) {
        if (source.has(mode) && !source.isNull(mode)) put(target, source.optString(mode))
    }

    /** 备份文件名（统一命名，方便认）。 */
    fun fileName(dateStamp: String): String = "naistudio-backup-$dateStamp.json"
}
