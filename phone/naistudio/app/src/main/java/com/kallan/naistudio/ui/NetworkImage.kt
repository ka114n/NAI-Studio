package com.kallan.naistudio.ui

import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 网络图片（缩略图）· 带内存缓存的最小实现。
 *
 * 为什么要自己写：App 里原来的图都是**本地生成后落盘**的（`FileImage` 读文件），
 * 没有"从网上下图"的需求。角色图鉴要显示 AnimaDex 的缩略图，所以补这一小块。
 *
 * 约定：
 *  · 只用**内存缓存**（`LruCache`，默认 96 张）—— 缩略图是几十 KB 的 webp，不落盘也够；
 *  · 相同 URL 并发请求会各下各的（图小，不值得加去重表）；
 *  · 失败/未加载时显示占位底色，不弹错（图挂了不该打断浏览）。
 */
object NetworkImageCache {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * 按**张数**算容量 ✓。
     *
     * ⚠️ **第 ㊿f 批**：96 ⇒ **320** ✓ —— 用户报「换到别的目录图加载不出来」✓。
     * 96 张在"一屏 12~20 张、来回切目录"的场景下**太小** ✓：
     * 切一本就把上一本整批挤出去 ✓，切回来**全部重下** ✓ ⇒ 抖动面无限放大 ✓。
     * 缩略图是几十 KB 的 jpg ✓，320 张约 **10~20 MB** ✓，手机吃得下 ✓
     * （`LruCache` 按张计数 ✓，不吃满也不会 OOM ✓）。
     */
    private val cache = object : LruCache<String, ImageBitmap>(320) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = 1
    }

    fun peek(url: String?): ImageBitmap? = url?.takeIf { it.isNotBlank() }?.let { cache.get(it) }

    suspend fun load(url: String): ImageBitmap? {
        peek(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url)
                    .header("user-agent", "NAI-Studio-Android/1.0")
                    .get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val bytes = response.body?.bytes() ?: return@runCatching null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }.getOrNull()?.also { cache.put(url, it) }
        }
    }
}

/**
 * 网络图：`url` 为空或还没下载完时显示一块占位底色（画廊风：不要转圈，安静地等）。
 *
 * ## ⚠️ **第 ㊿f 批（2026-09-22）修**：用户报「**加载是不是有问题，换到别的目录图加载不出来**」✓
 *
 * **根因**：原来是 `LaunchedEffect(url) { bitmap = load(url) }` ✓ ——
 * `LaunchedEffect` **只在 `url` 变化时重跑** ✓，而 `load` 失败返回 `null` ✓（不抛 ✓）。
 * 于是**任何一次网络抖动 / 超时，那一格就永远空白** ✗ ——
 * 除非把这张图划出屏幕再划回来（url 才"变"✓）或重进页面 ✓。
 * 「换到别的目录」正好最容易撞上 ✓：一次换掉整屏几十张图 ✓、
 * 内存 LRU 又只有 96 张 ✓（来回切会反复重下 ✓）⇒ 抖动概率大增 ✓。
 *
 * **修法**：**失败自动重试** ✓（指数退避 ✓，最多 4 次 ✓）——
 * 网络图这种"重试就大概率能成"的场景 ✓，重试是最省事也最有效的 ✓。
 * ⚠️ 缩短到 page 里没有"加载中"指示 ✓，所以重试期间**保留占位底色** ✓，
 * 用户看到的是"稍等就出来了"✓，而不是"永远空着"✗。
 */
@Composable
fun NetworkImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var bitmap by remember(url) { mutableStateOf(NetworkImageCache.peek(url)) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank() || bitmap != null) return@LaunchedEffect
        // ⚠️ **失败就退避重试** ✓（最多 4 次 ✓）—— 见上面 KDoc ✓
        var attempt = 0
        while (bitmap == null && attempt < 4) {
            if (attempt > 0) kotlinx.coroutines.delay(250L * attempt)
            bitmap = NetworkImageCache.load(url)
            attempt++
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
