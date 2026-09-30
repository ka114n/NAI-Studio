package com.kallan.naistudio.models

import com.kallan.naistudio.services.PromptRules
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份格式（`BackupCodec`）的规范与容错。
 *
 * 这些用例就是"规范格式"的可执行定义：**格式标识 / 版本号 / 各小节的可选性 /
 * 不认识的东西要拒绝 / token 绝不出现**。
 */
class BackupCodecTest {

    private val settings = AppSettings(
        apiBaseUrl = "https://api.novelai.net",
        theme = "dark",
        language = "en-US",
        i2iStrength = 0.55,
        inpaintMaskExpand = 12,
        galleryColumns = 4,
    )

    private val params = GenerateParams(
        model = NaiModelId.V5_FULL,
        positivePrompt = "1girl, solo",
        negativePrompt = "bad hands",
        width = 832,
        height = 1216,
        steps = 23,
        seed = 12345L,
        qualityPreset = "light",
    )

    private val library = StylePresetLibrary(
        presets = listOf(
            StylePreset(id = "p1", name = "厚涂", positive = "thick paint", enabled = true),
            StylePreset(id = "p2", name = "线稿", positive = "lineart", enabled = false),
        ),
    )

    private val characters = listOf(
        CharCaptionItem(prompt = "1girl, red hair", negativePrompt = "hat", useCoords = true, x = 0.25, y = 0.75),
        CharCaptionItem(prompt = "1boy, blue hair"),
    )

    private fun bundle() = BackupCodec.Bundle(
        settings = settings,
        params = params,
        styleLibrary = library,
        charCaptions = characters,
    )

    @Test
    fun `envelope carries format and version`() {
        val json = JSONObject(BackupCodec.encode(bundle(), "0.2.0", "2026-09-12T05:40:00Z"))

        assertEquals(BackupCodec.FORMAT, json.getString("format"))
        assertEquals(BackupCodec.FORMAT_VERSION, json.getInt("formatVersion"))
        assertEquals("2026-09-12T05:40:00Z", json.getString("exportedAt"))
        assertEquals(BackupCodec.APP_PACKAGE, json.getJSONObject("app").getString("package"))
        assertEquals("0.2.0", json.getJSONObject("app").getString("versionName"))
        assertTrue(json.has("payload"))
    }

    @Test
    fun `round trip keeps every section`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(bundle(), "0.2.0", "2026-09-12T05:40:00Z"))

        assertEquals("dark", decoded.settings?.theme)
        assertEquals("en-US", decoded.settings?.language)
        assertEquals(0.55, decoded.settings?.i2iStrength ?: 0.0, 1e-9)
        assertEquals(12, decoded.settings?.inpaintMaskExpand)
        assertEquals(4, decoded.settings?.galleryColumns)

        assertEquals("1girl, solo", decoded.params?.positivePrompt)
        assertEquals(832, decoded.params?.width)
        assertEquals(12345L, decoded.params?.seed)
        assertEquals("light", decoded.params?.qualityPreset)

        assertEquals("厚涂", decoded.styleLibrary?.presets?.first()?.name)
        assertEquals(2, decoded.styleLibrary?.presets?.size)

        assertEquals(2, decoded.charCaptions?.size)
        assertEquals(0.25, decoded.charCaptions?.first()?.x ?: 0.0, 1e-9)
        assertTrue(decoded.charCaptions?.first()?.useCoords == true)
    }

    @Test
    fun `token never appears in the backup text`() {
        // AppSettings 里根本没有 token 字段，这里再钉一遍：备份文本里不许出现它
        val secret = "pst-abcdefghijklmnopqrstuvwxyz0123456789"
        val text = BackupCodec.encode(bundle(), "0.2.0", "2026-09-12T05:40:00Z")
        assertFalse(text.contains(secret))
        assertFalse(text.contains("token", ignoreCase = true))
        assertFalse(text.contains("authorization", ignoreCase = true))
    }

    @Test
    fun `partial backups only carry what they have`() {
        val onlySettings = BackupCodec.Bundle(settings = settings)
        val decoded = BackupCodec.decode(BackupCodec.encode(onlySettings, "0.2.0", "now"))

        assertTrue(decoded.settings != null)
        assertTrue(decoded.params == null)
        assertTrue(decoded.styleLibrary == null)
        assertTrue(decoded.charCaptions == null)
        assertTrue(decoded.summary.hasSettings)
        assertFalse(decoded.summary.hasParams)
        assertEquals(0, decoded.summary.stylePresets)
    }

    @Test
    fun `summary counts whats inside`() {
        val summary = BackupCodec.decode(BackupCodec.encode(bundle(), "0.2.0", "now")).summary
        assertTrue(summary.hasSettings)
        assertTrue(summary.hasParams)
        // 分组功能已去掉 ⇒ `styleGroups` 恒为 0（字段保留只为兼容老备份，见 BackupCodec.summary）
        assertEquals(0, summary.styleGroups)
        assertEquals(2, summary.stylePresets)
        assertEquals(2, summary.characters)
        assertFalse(summary.isEmpty)
    }

    @Test
    fun `rejects a json that is not ours`() {
        val error = runCatching { BackupCodec.decode("""{"foo":1}""") }.exceptionOrNull()
        assertTrue(error is BackupCodec.BackupException)
        assertTrue(error?.message?.contains("format") == true)
    }

    @Test
    fun `rejects broken json`() {
        val error = runCatching { BackupCodec.decode("{not json") }.exceptionOrNull()
        assertTrue(error is BackupCodec.BackupException)
    }

    @Test
    fun `rejects a newer format version`() {
        val text = BackupCodec.encode(bundle(), "0.2.0", "now")
        val bumped = JSONObject(text).put("formatVersion", BackupCodec.FORMAT_VERSION + 1).toString()
        val error = runCatching { BackupCodec.decode(bumped) }.exceptionOrNull()
        assertTrue(error is BackupCodec.BackupException)
        assertTrue(error?.message?.contains("更新的版本") == true)
    }

    @Test
    fun `rejects a backup with nothing inside`() {
        val empty = BackupCodec.encode(BackupCodec.Bundle(), "0.2.0", "now")
        val error = runCatching { BackupCodec.decode(empty) }.exceptionOrNull()
        assertTrue(error is BackupCodec.BackupException)
    }

    @Test
    fun `unknown keys are ignored`() {
        val text = BackupCodec.encode(BackupCodec.Bundle(settings = settings), "0.2.0", "now")
        val patched = JSONObject(text).apply {
            put("futureField", "whatever")
            getJSONObject("payload").put("somethingNew", JSONObject().put("a", 1))
        }.toString()

        val decoded = BackupCodec.decode(patched)
        assertEquals("dark", decoded.settings?.theme)
    }

    @Test
    fun `file name is predictable`() {
        assertEquals("naistudio-backup-20260912-1340.json", BackupCodec.fileName("20260912-1340"))
    }

    // ------------------------------------------------ 提示词规则与各项设置

    /** 一份"用户改过"的设置：规则六段、模式、LLM/百度密钥、导演参数、保存目录全都不是默认值。 */
    private val customized = AppSettings(
        optimizeRuleMode = PromptRules.MODE_NATURAL,
        optimizeRulesTag = "自定义优化·tag",
        optimizeRulesNatural = "自定义优化·自然语言",
        optimizeRulesMixed = "自定义优化·混合",
        reverseRuleMode = PromptRules.MODE_MIXED,
        reverseRulesTag = "自定义反推·tag",
        reverseRulesNatural = "自定义反推·自然语言",
        reverseRulesMixed = "自定义反推·混合",
        llmApiUrl = "https://api.deepseek.com",
        llmApiKey = "sk-test-key",
        llmModel = "deepseek-flash",
        // 分功能 AI：地址与模型名进备份，密钥不进（下面遍历登记表断言）
        llmStoryboardApiUrl = "https://api.moonshot.cn/v1",
        llmStoryboardModel = "kimi-k2",
        llmStoryboardApiKey = "sk-storyboard-key",
        llmReverseModel = "qwen-vl-max",
        translateService = "baidu",
        baiduTranslateAppId = "20260913002683708",
        baiduTranslateSecret = "baidu-secret",
        imageOutputTreeUri = "content://com.android.externalstorage.documents/tree/primary%3ANAI",
        officialUpscaleAfterGenerate = true,
        i2iSourceMode = "original",
        directorTool = "colorize",
        augmentColorizePrompt = "deep red",
        augmentEmotion = "sad",
        augmentEmotionLevel = 3.0,
        augmentKeepTextBubbles = true,
        persistDirectorParams = false,
        historyRetentionDays = 30,
        imageNameTemplate = "{date}_{seq}_{seed}",
        language = "en-US",
        theme = "dark",
    )

    @Test
    fun `prompt rules survive the round trip`() {
        val decoded = BackupCodec.decode(
            BackupCodec.encode(
                BackupCodec.Bundle(settings = customized),
                "0.2.0",
                "now",
            ),
        ).settings
            ?: error("settings 没有解出来")

        assertEquals(PromptRules.MODE_NATURAL, decoded.optimizeRuleMode)
        assertEquals("自定义优化·tag", decoded.optimizeRulesTag)
        assertEquals("自定义优化·自然语言", decoded.optimizeRulesNatural)
        assertEquals("自定义优化·混合", decoded.optimizeRulesMixed)
        assertEquals(PromptRules.MODE_MIXED, decoded.reverseRuleMode)
        assertEquals("自定义反推·tag", decoded.reverseRulesTag)
        assertEquals("自定义反推·自然语言", decoded.reverseRulesNatural)
        assertEquals("自定义反推·混合", decoded.reverseRulesMixed)
    }

    @Test
    fun `every settings item survives the round trip`() {
        val decoded = BackupCodec.decode(
            BackupCodec.encode(BackupCodec.Bundle(settings = customized), "0.2.0", "now"),
        ).settings
            ?: error("settings 没有解出来")

        // LLM / 百度 / 保存目录 / 放大 / 图生图 / 导演台 / 记忆开关 / 保留天数 / 命名 / 语言主题
        assertEquals("https://api.deepseek.com", decoded.llmApiUrl)
        assertEquals("deepseek-flash", decoded.llmModel)
        assertEquals("baidu", decoded.translateService)
        assertEquals(customized.imageOutputTreeUri, decoded.imageOutputTreeUri)
        assertTrue(decoded.officialUpscaleAfterGenerate)
        assertEquals("original", decoded.i2iSourceMode)
        assertEquals("colorize", decoded.directorTool)
        assertEquals("deep red", decoded.augmentColorizePrompt)
        assertEquals("sad", decoded.augmentEmotion)
        assertEquals(3.0, decoded.augmentEmotionLevel, 1e-9)
        assertTrue(decoded.augmentKeepTextBubbles)
        assertFalse(decoded.persistDirectorParams)
        assertEquals(30, decoded.historyRetentionDays)
        assertEquals("{date}_{seq}_{seed}", decoded.imageNameTemplate)
        assertEquals("en-US", decoded.language)
        assertEquals("dark", decoded.theme)

        // ⚠️ 所有密钥**故意不往返**（用户要求：备份不带凭证）。
        // 这里反过来断言它们是空的 —— 如果哪天有人"顺手又加回去了"，这条会红。
        // 完整的隐私约束在 BackupPrivacyTest。
        //
        // 遍历 SECRET_KEYS 而不是逐个点名：以后再加密钥字段时这条**自动覆盖**，
        // 不用回来补一行（漏补一行就是一条假的绿色）。
        AppSettings.SECRET_KEYS.forEach { key ->
            assertEquals("$key 不该出现在备份里", "", decoded.toJson().optString(key))
        }
        // 而"不是凭证"的那部分要照常往返
        assertEquals("https://api.moonshot.cn/v1", decoded.llmStoryboardApiUrl)
        assertEquals("kimi-k2", decoded.llmStoryboardModel)
        assertEquals("qwen-vl-max", decoded.llmReverseModel)
    }

    @Test
    fun `rules also live in their own readable section`() {
        val json = JSONObject(
            BackupCodec.encode(BackupCodec.Bundle(settings = customized), "0.2.0", "now"),
        )
        val rules = json.getJSONObject("payload").getJSONObject("promptRules")

        assertEquals(PromptRules.MODE_NATURAL, rules.getString("optimizeMode"))
        assertEquals(PromptRules.MODE_MIXED, rules.getString("reverseMode"))
        assertEquals("自定义优化·tag", rules.getJSONObject("optimize").getString(PromptRules.MODE_TAG))
        assertEquals(
            "自定义反推·混合",
            rules.getJSONObject("reverse").getString(PromptRules.MODE_MIXED),
        )
    }

    @Test
    fun `summary knows the backup carries prompt rules`() {
        val withRules = BackupCodec
            .decode(
                BackupCodec.encode(
                    bundle().copy(settings = customized),
                    "0.2.0",
                    "now",
                ),
            )
            .summary
        assertTrue(withRules.hasSettings)
        assertTrue(withRules.hasPromptRules)
        assertTrue(withRules.sections.contains(BackupCodec.SECTION_SETTINGS))
        assertTrue(withRules.sections.contains(BackupCodec.SECTION_PARAMS))
        assertTrue(withRules.sections.contains(BackupCodec.SECTION_STYLES))
        assertTrue(withRules.sections.contains(BackupCodec.SECTION_CHARACTERS))
        // 只有设置的那种备份：小节表里就只剩「设置」
        val settingsOnly = BackupCodec
            .decode(BackupCodec.encode(BackupCodec.Bundle(settings = customized), "0.2.0", "now"))
            .summary
        assertEquals(listOf(BackupCodec.SECTION_SETTINGS), settingsOnly.sections)
    }

    @Test
    fun `a rules only backup still restores the rules`() {
        // 手写/精简的备份：payload 里只有 promptRules 一节
        val text = JSONObject()
            .put("format", BackupCodec.FORMAT)
            .put("formatVersion", BackupCodec.FORMAT_VERSION)
            .put("exportedAt", "now")
            .put(
                "payload",
                JSONObject().put(
                    "promptRules",
                    JSONObject()
                        .put("reverseMode", PromptRules.MODE_NATURAL)
                        .put(
                            "reverse",
                            JSONObject().put(PromptRules.MODE_NATURAL, "只看图说话"),
                        ),
                ),
            )
            .toString()

        val decoded = BackupCodec.decode(text)
        assertEquals(PromptRules.MODE_NATURAL, decoded.settings?.reverseRuleMode)
        assertEquals("只看图说话", decoded.settings?.reverseRulesNatural)
        // 其余字段落到默认值（因为这份备份里只有规则）
        assertEquals(AppSettings().llmApiUrl, decoded.settings?.llmApiUrl)
    }

    @Test
    fun `import overlay keeps local values for keys the backup lacks`() {
        val local = JSONObject().put("llmApiUrl", "https://local.example/v1").put("llmModel", "local-model")
        val backup = JSONObject().put("llmModel", "backup-model").put("theme", "dark")

        val merged = AppSettings.fromJson(BackupCodec.mergeSettingsJson(local, backup))

        assertEquals("https://local.example/v1", merged.llmApiUrl) // 备份里没有 → 保留本机
        assertEquals("backup-model", merged.llmModel)             // 备份里有 → 覆盖
        assertEquals("dark", merged.theme)
    }

    @Test
    fun `an old backup without the rules section still restores settings`() {
        // 模拟 v1 早期（没有 promptRules 那一节）的备份：settings 里直接带规则
        val text = JSONObject()
            .put("format", BackupCodec.FORMAT)
            .put("formatVersion", BackupCodec.FORMAT_VERSION)
            .put("exportedAt", "now")
            .put(
                "payload",
                JSONObject().put(
                    "settings",
                    JSONObject().put("reverseRulesTag", "老备份里的反推规则"),
                ),
            )
            .toString()

        val decoded = BackupCodec.decode(text)
        assertEquals("老备份里的反推规则", decoded.settings?.reverseRulesTag)
        assertFalse(decoded.summary.hasPromptRules)
    }
}
