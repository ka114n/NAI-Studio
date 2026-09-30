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
     * **本地缓存目录**（第 ㊿ 批 2026-09-21 加 ✓）—— 用户口径：
     * **「不能下载在app或软件里不用每次下载吗」** ✓ ⇒ 下过一次就存着 ✓、之后不再联网 ✓。
     *
     * ⚠️ **为什么不打包进 APK** ✗：授权写明"数据汇编未经许可不得再分发"✓
     *（[TagCodexProtocol.LICENSE_NOTE] ✓，设置页也承诺过"没有打包进 App" ✓）。
     * 缓存在**用户自己的机器上** ✓ 两件事都不违反 ✓，体验与打包**没有区别** ✓。
     *
     * `null` = 不缓存 ✓（单测环境照旧纯在线 ✓）。
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
     * 取一个数据文件。**先看本地缓存** ✓，命中就直接返回、**一个网络请求都不发** ✓；
     * 否则走三级降级（直连 → 同 release 代理 → 便利路径 ✓，与站点 `fetchDataJsonResult` 一致 ✓），
     * 拿到之后**写进缓存** ✓。
     *
     * ⚠️ **第 ㊿ 批（2026-09-21）**：用户口径「**不能下载在app或软件里不用每次下载吗**」✓
     * ⇒ 下过一次就存着 ✓（词表 1.8 MB，第一次进工具页下一次 ✓，之后一次都不联网 ✓）。
     * ⚠️ 为什么不是"打包进包"✗：授权写明"数据汇编未经许可不得再分发"✓ ——
     * 缓存在**用户自己的机器上**不违反 ✓，体验与打包**没有区别** ✓。
     */
    @Synchronized
    private fun fetch(path: String): String {
        readCache(path)?.let { return it }
        val body = fetchNetwork(path)
        writeCache(path, body)
        return body
    }

    /** 纯网络那一半（三级降级 ✓）。 */
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

    /** 缓存文件名 = 路径把斜杠换成 `_` ✓（`tag_zh/core.json` → `tag_zh_core.json` ✓）。 */
    private fun cacheFile(path: String): File? {
        val dir = cacheDir ?: return null
        return File(dir, path.replace('/', '_').replace('\\', '_'))
    }

    private fun readCache(path: String): String? {
        val dir = cacheDir ?: return null
        return runCatching {
            val file = cacheFile(path) ?: return null
            if (!file.isFile) return null
            // 空文件 = 上次写了一半就断了 ⇒ 当**没有缓存**（下次重下，不拿半份糊弄）
            file.readText().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun writeCache(path: String, body: String) {
        val dir = cacheDir ?: return
        runCatching {
            if (!dir.isDirectory) dir.mkdirs()
            val file = cacheFile(path) ?: return
            // ⚠️ **先写临时文件再改名**：直接覆盖的话，写到一半被杀（切后台 / 没电）
            //    会留下**半份 JSON**，下次读它解析失败且无从分辨。改名在同分区是原子的。
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeText(body)
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                file.writeText(body)
                tmp.delete()
            }
        }
    }

    /** **取一份 JSON 文本并解析**（第 ㊿ 批加 ✓）—— 给"tag 中文对照"那两片词表用 ✓。 */
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
