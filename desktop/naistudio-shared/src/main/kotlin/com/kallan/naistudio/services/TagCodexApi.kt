package com.kallan.naistudio.services

import com.kallan.naistudio.models.TagCodexProtocol
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 画师超市 · 接口层。
 *
 * 只做三件事：解析数据源、按路径取 JSON、在 R2 直连失败时退回同源便利路径。
 * 数据结构的解析全在 [TagCodexProtocol]（纯逻辑、可单测），这里只负责网络。
 *
 * ## 为什么要有"两条路"
 *
 * 站点自己就是双路的：优先直连 Cloudflare R2 的**不可变 release**，
 * 失败时走同源 Pages Function 代理读**同一份** release（`data-source.js` 里逐字写着这么选，
 * 理由是避免两个 release 混读）。我们照做，但**便利路径**（`/data/<file>`）也留着：
 * 试过，它永远指向当前 release，实现最简单，拿不到 R2 指针时就是它。
 */
class TagCodexApi(
    private val client: OkHttpClient = defaultClient(),
    /**
     * **本地缓存目录**（第 ㊿ 批 2026-09-21 加 ✓）—— 用户在"显示中文对照"那一问里选的口径：
     * **「下过一次就存着，不再每次下载」** ✓。
     *
     * ## 为什么是"本地缓存"而不是"打包进 App"（**这条要留着，别再问一遍** ✗）
     *
     * 词表（`tag_zh` 那两片）与词条数据同源 ✓，都是**在线读取、不许再分发** ✓
     *（那条授权写在 [TagCodexProtocol.LICENSE_NOTE] ✓，设置页也对用户承诺过
     *  「**没有打包进 App**」✓）。打包进 APK 就**变成再分发** ✗，那句承诺也跟着变成假话 ✗。
     * 缓存到**用户自己的机器上** ✓ 则两件事都不违反 ✓，而体验与打包**没有区别** ✓
     *（只有第一次要下 ✓ —— 之后一次都不联网 ✓）。
     *
     * `null` = 不缓存 ✓（单测 / 无盘环境下照旧纯在线 ✓）。
     */
    private val cacheDir: File? = null,
) {

    /** 解析出来的数据源。`baseUrl == null` = 用同源便利路径。 */
    data class Source(val baseUrl: String?, val release: String?) {
        fun fileUrl(path: String): String =
            if (baseUrl != null && release != null) {
                TagCodexProtocol.releaseUrl(baseUrl, release, path)
            } else {
                TagCodexProtocol.proxyUrl(path)
            }
    }

    /**
     * 解析数据源。**任何一步失败都不抛** —— 直接退回便利路径，
     * 因为便利路径在实测里永远能拿到当前 release 的数据。
     */
    fun resolveSource(): Source {
        val baseUrl = runCatching {
            TagCodexProtocol.parseConfig(JSONObject(getRaw(TagCodexProtocol.configUrl())))
        }.getOrNull()
        if (baseUrl == null) return Source(null, null)

        val release = runCatching {
            TagCodexProtocol.parseRelease(JSONObject(getRaw(TagCodexProtocol.pointerUrl(baseUrl))))
        }.getOrNull()
        if (release == null) return Source(null, null)

        return Source(baseUrl, release)
    }

    fun codexes(): JSONArray = parseArray(fetch("codexes.json"))

    fun media(): JSONObject = runCatching { parseObject(fetch("media.json")) }.getOrDefault(JSONObject())

    fun doc(codexId: String): JSONObject = parseObject(fetch("$codexId.json"))

    // ------------------------------------------------------------- 内部

    /**
     * 取一个数据文件。先走解析出来的源，失败就换**同一条 release 的代理**，
     * 最后再退到便利路径 —— 和站点 `fetchDataJsonResult` 的三级降级一致。
     *
     * ⚠️ **第 ㊿ 批**：现在**先看本地缓存** ✓（配了 [cacheDir] 时 ✓）——
     *    命中就直接返回 ✓、**一个网络请求都不发** ✓（这就是用户要的"不用每次下载"✓）。
     */
    @Synchronized
    private fun fetch(path: String): String {
        readCache(path)?.let { return it }
        val body = fetchNetwork(path)
        writeCache(path, body)
        return body
    }

    /** 纯网络那一半（三级降级 ✓，口径与站点 `fetchDataJsonResult` 一致 ✓）。 */
    private fun fetchNetwork(path: String): String {
        val source = cachedSource ?: resolveSource().also { cachedSource = it }
        val attempts = buildList {
            add(source.fileUrl(path))
            source.release?.let { add(TagCodexProtocol.proxyReleaseUrl(it, path)) }
            add(TagCodexProtocol.proxyUrl(path))
        }.distinct()

        var lastError: Exception? = null
        for (url in attempts) {
            try {
                return getRaw(url)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw TagCodexException(
            "取数据失败：${lastError?.message ?: "未知错误"}（已试过 ${attempts.size} 个地址）",
        )
    }

    // ------------------------------------------------------- 第 ㊿ 批：本地缓存

    /**
     * 缓存文件名 = **路径把斜杠换成 `_`** ✓（`tag_zh/core.json` → `tag_zh_core.json` ✓）。
     * ⚠️ 路径里全是小写字母 / 数字 / 下划线 / 点 / 斜杠 ✓（云端那几份都是 ✓）——
     * 真出现别的字符也不怕 ✗：下面整段用 `runCatching` 兜住 ✓，缓存坏了当没有 ✓。
     */
    private fun cacheFile(path: String): File? {
        val dir = cacheDir ?: return null
        return File(dir, path.replace('/', '_').replace('\\', '_'))
    }

    private fun readCache(path: String): String? {
        val dir = cacheDir ?: return null
        return runCatching {
            if (!dir.isDirectory) return null
            val file = cacheFile(path) ?: return null
            if (!file.isFile) return null
            val text = file.readText()
            // 空文件 = 上次写了一半就断了 ✓ ⇒ 当**没有缓存** ✓（下次重下 ✓，不拿半份糊弄 ✓）
            text.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun writeCache(path: String, body: String) {
        val dir = cacheDir ?: return
        runCatching {
            if (!dir.isDirectory) dir.mkdirs()
            val file = cacheFile(path) ?: return
            // ⚠️ **先写临时文件再改名** ✗：直接覆盖的话，写到一半被杀（切后台 / 没电 ✓）
            //    会留下**半份 JSON** ✓ —— 下次读它会解析失败 ✓，而且我们无从分辨 ✗。
            //    改名在同一分区上是原子的 ✓ ⇒ 要么是完整旧版、要么是完整新版 ✓。
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeText(body)
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                // 改名失败（少见 ✓）⇒ 退一步直接写 ✓，至少内容是对的 ✓
                file.writeText(body)
                tmp.delete()
            }
        }
    }

    /**
     * **取一份 JSON 文本并解析**（第 ㊿ 批加 ✓）—— 给"tag 中文对照"那两片词表用 ✓。
     *
     * 与 [doc] 走**同一条缓存 + 三级降级** ✓（不另起一套 ✓）。
     */
    fun json(path: String): JSONObject = parseObject(fetch(path))

    private var cachedSource: Source? = null

    private fun parseObject(body: String): JSONObject = runCatching { JSONObject(body) }.getOrElse {
        throw TagCodexException("返回的内容看不懂（不是 JSON 对象）")
    }

    private fun parseArray(body: String): JSONArray = runCatching { JSONArray(body) }.getOrElse {
        throw TagCodexException("返回的内容看不懂（不是 JSON 数组）")
    }

    private fun getRaw(url: String): String {
        val request = Request.Builder()
            .url(url)
            // 站点会按 UA 限流；给个明确的自定义 UA，礼貌且便于对方识别
            .header("user-agent", "NAI-Studio-Android/1.0 (TagCodex tool)")
            .header("accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw TagCodexException("HTTP ${response.code}")
            if (body.isBlank()) throw TagCodexException("返回了空内容")
            return body
        }
    }

    companion object {
        /**
         * 读超时给到 60 秒：画师词典那一本 JSON 有 **2.9 MB**，
         * 手机网络下按"查一下就回来"的短超时会直接失败。
         */
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

/** 画师超市接口的错误（消息直接给用户看，中文）。 */
class TagCodexException(message: String) : Exception(message)
