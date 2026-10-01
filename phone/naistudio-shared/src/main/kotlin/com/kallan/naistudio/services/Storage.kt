package com.kallan.naistudio.services

import com.kallan.naistudio.models.AppSettings
import com.kallan.naistudio.models.CharCaptionCodec
import com.kallan.naistudio.models.CharCaptionItem
import com.kallan.naistudio.models.ComicSettings
import com.kallan.naistudio.models.GenerateParams
import com.kallan.naistudio.models.GenerateParamsCodec
import com.kallan.naistudio.models.HistoryGroup
import com.kallan.naistudio.models.HistoryItem
import com.kallan.naistudio.models.StylePresetLibrary
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.platform.clearAuthTokens
import com.kallan.naistudio.platform.clearGatewayToken
import com.kallan.naistudio.platform.clearToken
import com.kallan.naistudio.platform.getAccessToken
import com.kallan.naistudio.platform.getGatewayToken
import com.kallan.naistudio.platform.getRefreshToken
import com.kallan.naistudio.platform.getToken
import com.kallan.naistudio.platform.setAuthTokens
import com.kallan.naistudio.platform.setGatewayToken
import com.kallan.naistudio.platform.setToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 存储层。
 *
 * **行为对齐参考实现** `mobile/lib/services/storage.dart`：
 *  · 存储 key 名照抄（`app_settings` / `gen_params` / `character_prompts_v1` /
 *    `history_index_v2` / `history_groups`）
 *  · **历史索引不是文件**，就是一条 JSON 数组，索引 0 = 最新
 *  · 历史索引有**内存镜像**（参考实现的注释：以前每次保存都重新解码整份列表并重新编码，
 *    单次 O(N)、批量就是 O(M·N)）
 *  · 大负载下把 JSON 编解码放到后台线程（参考实现用 isolate，阈值 64 KB 读 / 200 条写）
 *  · 图片路径 `<base>/<日期>/<分组>/`，文件名模板默认 `{date}_{seq}_{model}`
 *
 * ## 它现在住在**共用树**里（2026-09-16）
 *
 * 这层本来满身 Android（`Context` / `SharedPreferences` / `MediaStore` / SAF），
 * 现在只认 [Platform] 那几个接口 —— 于是手机和电脑**编的是同一份**。
 * 三处平台能力被换掉了：
 *  · `SharedPreferences` → [Platform.kv]（接口刻意照着 SharedPreferences 的形状做，
 *    所以下面几十处 `prefs.edit().putX().apply()` **一行都没改**）；
 *  · `TokenVault` → [Platform.secrets]（手机上还是 Keystore，电脑上换 DPAPI）；
 *  · `filesDir` / `MediaStore` / SAF → [Platform.paths] / [Platform.gallery] / [Platform.exportToUserDir]。
 */
class Storage(private val platform: Platform) {

    /** 键值存储。名字仍叫 `prefs` —— 调用点的写法一个字没变（见类注释）。 */
    private val prefs = platform.kv

    /** 密钥存储。同上，名字仍叫 `vault`，token 那几个方法由 `platform/Platform.kt` 里的扩展提供。 */
    private val vault = platform.secrets

    /** 历史索引的内存镜像。所有落盘都走 [writeHistory]，所以镜像不会漂移。 */
    private var historyCache: List<HistoryItem>? = null

    /** 进程内自增的保存序号，重启归零（与参考实现一致）。 */
    private var saveSequence = 0

    // =======================================================================
    // Token
    // =======================================================================

    fun getToken(): String = vault.getToken()
    fun setToken(token: String) = vault.setToken(token)
    fun clearToken() = vault.clearToken()

    // 第三方网关那把 Key（和上面那把**各存各的**，用户 2026-09-26 要求分开存储）
    fun getGatewayToken(): String = vault.getGatewayToken()
    fun setGatewayToken(token: String) = vault.setGatewayToken(token)
    fun clearGatewayToken() = vault.clearGatewayToken()

    // 自建账号的会话 token（见 AuthApi / AppState）
    fun setAuthTokens(access: String, refresh: String) = vault.setAuthTokens(access, refresh)
    fun getAccessToken(): String = vault.getAccessToken()
    fun getRefreshToken(): String = vault.getRefreshToken()
    fun clearAuthTokens() = vault.clearAuthTokens()

    // =======================================================================
    // 设置
    // =======================================================================

    fun getSettings(): AppSettings {
        val raw = prefs.getString(_K_SETTINGS, null) ?: return AppSettings()
        return try {
            AppSettings.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun setSettings(settings: AppSettings) {
        prefs.edit().putString(_K_SETTINGS, settings.toJson().toString()).apply()
    }

    // =======================================================================
    // 生成参数
    // =======================================================================

    /**
     * 读取生成参数。**自愈**：解码后再归一化一遍，若与存盘内容不一致就回写，
     * 免得旧的非法值一直留在盘上（参考实现里有注释说明这个坑）。
     */
    fun getParams(): GenerateParams {
        val raw = prefs.getString(_K_PARAMS, null) ?: return GenerateParams()
        return try {
            val decoded = JSONObject(raw)
            val repaired = GenerateParamsCodec.fromJson(decoded)
            if (decoded.toString() != GenerateParamsCodec.toJson(repaired).toString()) {
                setParams(repaired)
            }
            repaired
        } catch (e: Exception) {
            val repaired = GenerateParams()
            setParams(repaired)
            repaired
        }
    }

    fun setParams(params: GenerateParams) {
        prefs.edit()
            .putString(_K_PARAMS, GenerateParamsCodec.toJson(params.normalized()).toString())
            .apply()
    }

    /** 一次性迁移用的标记位。返回 true 表示这个迁移已经跑过了。 */
    fun getFlag(key: String): Boolean = prefs.getBoolean(key, false)

    fun setFlag(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    /** 角色提示词快照。读时限制 32 条。 */
    fun getCharacterPrompts(): List<CharCaptionItem> {
        val raw = prefs.getString(_K_CHARACTER_PROMPTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { CharCaptionCodec.fromJson(it) }
            }.take(32)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setCharacterPrompts(items: List<CharCaptionItem>) {
        // 先快照再落盘，避免并发编辑改动正在写入的内容
        val snapshot = items.toList()
        val array = JSONArray()
        snapshot.forEach { array.put(CharCaptionCodec.toJson(it)) }
        prefs.edit().putString(_K_CHARACTER_PROMPTS, array.toString()).apply()
    }

    // =======================================================================
    // 漫画模式（与角色分区**分别存储、互不干扰**）
    // =======================================================================

    /** 漫画分格快照。与角色提示词各存各的 key，上限口径一致（32 条）。 */
    fun getComicPanels(): List<CharCaptionItem> {
        val raw = prefs.getString(_K_COMIC_PANELS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { CharCaptionCodec.fromJson(it) }
            }.take(32)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun setComicPanels(items: List<CharCaptionItem>) {
        val snapshot = items.toList()
        val array = JSONArray()
        snapshot.forEach { array.put(CharCaptionCodec.toJson(it)) }
        prefs.edit().putString(_K_COMIC_PANELS, array.toString()).apply()
    }

    /** 漫画模式四项设置（开关 / 版式 / 阅读顺序 / 风格词）。 */
    fun getComicSettings(): ComicSettings {
        val raw = prefs.getString(_K_COMIC_SETTINGS, null) ?: return ComicSettings()
        return try {
            ComicSettings.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            ComicSettings()
        }
    }

    fun setComicSettings(settings: ComicSettings) {
        prefs.edit().putString(_K_COMIC_SETTINGS, settings.toJson().toString()).apply()
    }

    // =======================================================================
    // 历史
    // =======================================================================

    /** 读取历史索引。索引 0 = 最新。 */
    suspend fun getHistory(): List<HistoryItem> {
        historyCache?.let { return it }
        val raw = prefs.getString(_K_HISTORY, null)
        if (raw == null) {
            historyCache = emptyList()
            return emptyList()
        }
        val decoded = try {
            if (raw.length >= ISOLATE_JSON_THRESHOLD) {
                withContext(Dispatchers.Default) { HistoryItem.listFromJson(raw) }
            } else {
                HistoryItem.listFromJson(raw)
            }
        } catch (e: Exception) {
            emptyList()
        }
        historyCache = decoded
        return decoded
    }

    /** 写入历史索引。大列表在后台编码。 */
    suspend fun writeHistory(items: List<HistoryItem>) {
        historyCache = items.toList()
        val array = JSONArray()
        items.forEach { array.put(it.toJson()) }
        val encoded = if (items.size > ISOLATE_ITEM_THRESHOLD) {
            withContext(Dispatchers.Default) { array.toString() }
        } else {
            array.toString()
        }
        prefs.edit().putString(_K_HISTORY, encoded).apply()
    }

    suspend fun deleteHistory(id: String) {
        val history = getHistory()
        history.firstOrNull { it.id == id }?.let { item ->
            runCatching { File(item.filePath).delete() }
        }
        writeHistory(history.filterNot { it.id == id })
    }

    fun renameHistoryFile(item: HistoryItem, requestedName: String): HistoryItem {
        val source = File(item.filePath)
        if (!source.exists()) {
            throw IllegalStateException("本地图片不存在，无法重命名")
        }
        val extension = source.extension.ifEmpty { "png" }
        val stem = safeFileStem(requestedName)
        val parent = source.parentFile ?: throw IllegalStateException("无法定位图片所在目录")

        var candidate = File(parent, "$stem.$extension")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(parent, "$stem-$suffix.$extension")
            suffix += 1
        }
        if (!source.renameTo(candidate)) {
            throw IllegalStateException("重命名失败")
        }
        return item.copy(filePath = candidate.absolutePath)
    }

    // =======================================================================
    // 分组
    // =======================================================================

    fun getGroups(): List<HistoryGroup> {
        val raw = prefs.getString(_K_GROUPS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { HistoryGroup.fromJson(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun writeGroups(groups: List<HistoryGroup>) {
        val array = JSONArray()
        groups.forEach { array.put(it.toJson()) }
        prefs.edit().putString(_K_GROUPS, array.toString()).apply()
    }

    // =======================================================================
    // 风格预设库
    // =======================================================================

    fun getStylePresets(): StylePresetLibrary {
        val raw = prefs.getString(_K_STYLE_PRESETS, null) ?: return StylePresetLibrary()
        return try {
            StylePresetLibrary.fromJson(JSONObject(raw))
        } catch (e: Exception) {
            StylePresetLibrary()
        }
    }

    fun setStylePresets(library: StylePresetLibrary) {
        prefs.edit().putString(_K_STYLE_PRESETS, library.toJson().toString()).apply()
    }

    // =======================================================================
    // 图片落盘
    // =======================================================================

    /** App 文档目录下的 images。 */
    fun appImagesDir(): File = platform.paths.defaultImagesDir()

    private fun sanitizeFolderName(name: String): String {
        val cleaned = name.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "_")
            .replace(Regex("\\.+$"), "")
            .trim()
        return cleaned.ifEmpty { "Untitled group" }
    }

    /**
     * 解析保存目录：`<base>/<日期>/<分组>/`。
     *
     * base 优先用用户自定义目录；建不出来就依次回退，**保证保存不会因为自定义路径
     * 不可写而彻底失败**（Android 11+ 上外置路径失效是常见情况）。
     */
    private fun resolveSaveDir(settings: AppSettings, date: String, groupName: String?): File {
        val candidates = buildList {
            // Android history originals always remain in the app sandbox, including legacy settings.
            add(platform.paths.defaultImagesDir())
        }
        var lastError: Exception? = null
        for (base in candidates) {
            try {
                var dir = File(base, date)
                if (groupName != null) dir = File(dir, sanitizeFolderName(groupName))
                if (!dir.exists() && !dir.mkdirs()) {
                    throw IllegalStateException("无法创建目录：${dir.absolutePath}")
                }
                return dir
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IllegalStateException("没有可写的保存目录", lastError)
    }

    private fun pad(value: Int): String = value.toString().padStart(2, '0')

    /** 文件名模板渲染。规则照抄参考实现的 `_renderImageName`。 */
    private fun renderImageName(
        params: GenerateParams,
        settings: AppSettings,
        seed: Long,
        model: String,
        feature: String,
        sequence: Int,
        now: Date,
    ): String {
        val calendar = java.util.Calendar.getInstance().apply { time = now }
        val date = "%04d-%02d-%02d".format(
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.DAY_OF_MONTH),
        )
        val time = pad(calendar.get(java.util.Calendar.HOUR_OF_DAY)) +
            pad(calendar.get(java.util.Calendar.MINUTE)) +
            pad(calendar.get(java.util.Calendar.SECOND))

        val custom = safeFilePrefix(params.fileNamePrefix)
        val tokens = mapOf(
            "date" to date,
            "time" to time,
            "seq" to sequence.toString().padStart(2, '0'),
            "seed" to seed.toString(),
            "model" to safeFilePrefix(model),
            "type" to safeFilePrefix(feature),
            "name" to custom,
            "ts" to now.time.toString(),
        )

        val template = settings.imageNameTemplate.trim()
        val pattern = template.ifEmpty { "{date}_{seq}_{model}" }
        var name = Regex("\\{(\\w+)\\}").replace(pattern) { match ->
            tokens[match.groupValues[1]] ?: ""
        }
        if (custom.isNotEmpty() && !pattern.contains("{name}")) {
            name = "${custom}_$name"
        }
        name = safeFilePrefix(name.replace(' ', '_'))
        return name.ifEmpty { "${now.time}-$sequence" }
    }

    private fun uniqueFilePath(dir: File, baseName: String, extension: String): File {
        var candidate = File(dir, "$baseName.$extension")
        var suffix = 1
        while (candidate.exists()) {
            candidate = File(dir, "$baseName-$suffix.$extension")
            suffix += 1
        }
        return candidate
    }

    /**
     * 保存一张生成图并写入历史索引。
     *
     * @param feature t2i / i2i / inpaint / upscale / director-<tool>
     */
    suspend fun saveImage(
        bytes: ByteArray,
        params: GenerateParams,
        seed: Long,
        settings: AppSettings,
        groups: List<HistoryGroup>,
        feature: String = "t2i",
        model: String? = null,
        width: Int? = null,
        height: Int? = null,
        groupId: String? = null,
        /**
         * 直接指定文件名主干（不含扩展名），会做安全化处理。
         * 官方放大用它取名 —— 「放大前那张的名字 + 2x」，一眼能对上。
         */
        stemOverride: String? = null,
        /** 仅官方放大版：放大前那张图的路径。 */
        upscaleOfPath: String? = null,
    ): HistoryItem {
        val now = Date()
        val sequence = ++saveSequence
        val resolvedModel = model ?: params.model
        val groupName = groupId?.let { id -> groups.firstOrNull { it.id == id }?.name }

        val calendar = java.util.Calendar.getInstance().apply { time = now }
        val date = "%04d-%02d-%02d".format(
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.DAY_OF_MONTH),
        )

        val dir = resolveSaveDir(settings, date, groupName)
        val baseName = stemOverride?.let { safeFileStem(it) }
            ?: renderImageName(params, settings, seed, resolvedModel, feature, sequence, now)
        val target = uniqueFilePath(dir, baseName, "png")

        val output = if (settings.keepImageMetadata) bytes else stripPngMetadata(bytes)
        target.writeBytes(output)

        // 相册写入是尽力而为：权限被拒或设备没有相册都不该影响已保存的原图
        if (settings.saveToGallery) {
            runCatching { putImageToGallery(target) }
        }
        // 另存一份到用户选的文件夹（SAF 授权）：同样尽力而为，失败不影响已落盘的原图
        if (settings.saveToGallery) runCatching { exportToOutputFolder(settings, target) }

        val item = HistoryItem(
            // 进程内自增序号拼进 id：同一毫秒保存多张（一个 ZIP 多图 / 连发）时，
            // 光靠 now.time 会撞 id，之后按 id 删除/选中会误伤同 id 的多张。
            id = "${now.time}_$sequence",
            filePath = target.absolutePath,
            date = date,
            createdAt = iso8601(now),
            seed = seed,
            model = resolvedModel,
            width = width ?: params.width,
            height = height ?: params.height,
            prompt = params.positivePrompt,
            feature = feature,
            // 仅官方放大版有值：放大前那张的路径（图库据此并格 / 编辑据此回落）
            upscaleOfPath = upscaleOfPath,
            groupId = groupId,
            params = GenerateParamsCodec.toJson(params),
        )

        val history = getHistory()
        writeHistory(listOf(item) + history)
        return item
    }

    /**
     * 把刚存好的图**另存一份**到用户通过系统文件夹选择器选的目录（SAF tree URI）。
     *
     * 为什么是"另存"而不是"改存到那里"：App 的图库、缩略图、遮罩编辑、查看器全都按**本地文件路径**
     * 工作（`HistoryItem.filePath`），SAF 目录给不出可用的文件路径，所以必须留一份在私有目录。
     * 用户要的"图片在我的文件夹里"由这一步满足。
     *
     * 平台差异交给 [Platform.exportToUserDir]：手机走 `DocumentsContract`
     * （**不引 androidx.documentfile**，本项目运行时依赖只有 OkHttp），电脑就是一个普通目录。
     * 同名文件由系统自动改名。
     */
    private fun exportToOutputFolder(settings: AppSettings, source: File) {
        val tree = settings.imageOutputTreeUri.trim()
        if (tree.isEmpty()) return
        platform.exportToUserDir(source, tree)
    }

    /** 相册：手机 = MediaStore；电脑没有相册 → 实现返回 false，这里当"可选步骤"忽略。 */
    private fun putImageToGallery(file: File) {
        platform.gallery.putImage(file)
    }

    // =======================================================================
    // 工具函数
    // =======================================================================

    private fun iso8601(date: Date): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(date)

    /** 文件名安全化：非法字符换下划线、连续空白折成单空格、去尾部点与空格、截断 80。 */
    fun safeFilePrefix(value: String): String {
        var result = value.replace(Regex("[<>:\"/\\\\|?*\\x00-\\x1f]"), "_")
        result = result.replace(Regex("\\s+"), " ")
        result = result.replace(Regex("[. ]+$"), "")
        if (result.length > 80) result = result.take(80).trimEnd()
        return result
    }

    /** 用于重命名与导出包名。 */
    fun safeFileStem(value: String): String = safeFilePrefix(value).replace(' ', '_').ifEmpty { "image" }

    /** Clear PNG text/EXIF chunks and NovelAI pixel carriers; fail rather than leak on invalid PNG. */
    fun stripPngMetadata(bytes: ByteArray): ByteArray {
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        require(bytes.size >= 8 && signature.indices.all { bytes[it] == signature[it] }) {
            "Cannot clear metadata: image is not a PNG"
        }
        val output = ByteArrayOutputStream(bytes.size)
        output.write(signature)
        var offset = 8
        var ended = false
        while (offset + 12 <= bytes.size) {
            val length = readInt(bytes, offset)
            require(length >= 0 && length <= bytes.size - offset - 12) { "Invalid PNG chunk" }
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            if (type !in PNG_METADATA_CHUNKS) output.write(bytes, offset, 12 + length)
            offset += 12 + length
            if (type == "IEND") { ended = true; break }
        }
        require(ended) { "Incomplete PNG: metadata removal failed" }
        return com.kallan.naistudio.models.StealthPng.stripHiddenMetadata(output.toByteArray())
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private companion object {
        const val PREFS = "nai_store"

        /** key 名照抄参考实现。 */
        const val _K_SETTINGS = "app_settings"
        const val _K_PARAMS = "gen_params"
        const val _K_CHARACTER_PROMPTS = "character_prompts_v1"

        /** 漫画模式：分格列表与设置**各存各的 key**（与角色分区互不干扰）。 */
        const val _K_COMIC_PANELS = "comic_panels_v1"
        const val _K_COMIC_SETTINGS = "comic_settings_v1"
        const val _K_HISTORY = "history_index_v2"
        const val _K_GROUPS = "history_groups"
        const val _K_STYLE_PRESETS = "style_presets_v1"

        /** 参考实现：读侧按 64 KB 字节数分流。 */
        const val ISOLATE_JSON_THRESHOLD = 64 * 1024

        /** 参考实现：写侧按条数分流（注意与读侧不是一个口径，源码原文如此）。 */
        const val ISOLATE_ITEM_THRESHOLD = 200

        val PNG_METADATA_CHUNKS = setOf("tEXt", "iTXt", "zTXt", "eXIf")
    }
}
