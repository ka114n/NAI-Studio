package com.kallan.naistudio.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.kallan.naistudio.platform.LocalPlatform
import com.kallan.naistudio.platform.NativeImage
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.services.Thumbnails
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 从 `content://` / `file://` / 普通路径解码显示（用于预设封面图）。
 * 同样在后台线程解码，并且只解到 [maxDimension]。
 *
 * 读取统一走 [Platform.openInput]（手机 = `ContentResolver`，电脑 = 打开文件），
 * 拿不到就按**普通文件路径**读 —— 这样两端都不用关心 ref 是哪种形式。
 */
@Composable
fun UriImage(
    uriString: String,
    maxDimension: Int = 256,
    modifier: Modifier = Modifier,
) {
    val platform = LocalPlatform.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uriString, maxDimension) {
        value = if (uriString.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = readRefBytes(platform, uriString) ?: return@runCatching null
                    decodeToFit(platform, bytes, maxDimension)
                }.getOrNull()
            }
        }
    }

    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

/**
 * 缩略图内存缓存（按「路径 + 解码上限」做键的 LRU）。
 *
 * 为什么必须要有：图库住在 `HorizontalPager` 的页面里，来回快速切页时页面会被销毁重建，
 * 每次重建都要重新解码所有可见缩略图。原图解码**中途不可取消**，于是快速来回滑动会
 * 堆积一批还在跑的解码任务 + 大量位图分配，表现就是越滑越卡、最后像卡死。
 * 加一层缓存后重建页面直接命中，不再重复解码。
 *
 * ## 从 Android 版搬过来时改了什么
 *
 * 原来用 `android.util.LruCache`，现在自己实现：`LinkedHashMap(accessOrder = true)`
 * 天然就是 LRU（`get`/`put` 都算访问，`entries` 迭代顺序 = 最久未使用在前），
 * 按「宽 × 高 × 4 字节」估算占用，超上限就从最久未用的开始丢。两端共用同一份。
 */
private object ThumbnailCache {
    private const val MAX_BYTES = 48 * 1024 * 1024

    private val map = LinkedHashMap<String, ImageBitmap>(64, 0.75f, true)
    private var bytes = 0

    private fun sizeOf(value: ImageBitmap): Int = value.width * value.height * 4

    @Synchronized
    fun get(key: String): ImageBitmap? = map[key]

    @Synchronized
    fun put(key: String, value: ImageBitmap) {
        map.remove(key)?.let { bytes -= sizeOf(it) }
        map[key] = value
        bytes += sizeOf(value)
        // 至少留一张（单张就超上限时也得能显示）
        val it = map.entries.iterator()
        while (bytes > MAX_BYTES && map.size > 1 && it.hasNext()) {
            val eldest = it.next()
            bytes -= sizeOf(eldest.value)
            it.remove()
        }
    }
}

/**
 * 从文件降采样解码后显示。
 *
 * 硬约束：**UI 层只持有已解码位图或文件路径，绝不持有 base64 / 原始 PNG 字节。**
 * 解码走 `ImageIo.decodeSampled`（手机 = `inSampleSize`）在后台线程执行，结果进 [ThumbnailCache]。
 *
 * [useThumbnail] = true 时**先读磁盘缩略图**（[Thumbnails.fileFor]，第一次会生成小图再读），
 * 图库列表/网格用它，避免每次都去解码几 MB 的原图。
 */
@Composable
fun FileImage(
    path: String,
    maxDimension: Int = 1536,
    modifier: Modifier = Modifier,
    /** 图库网格要 `Crop`（铺满 2:3 格子），预览要 `Fit`（保留真实比例）。 */
    contentScale: ContentScale = ContentScale.Fit,
    /**
     * `Fit` 时留白画在哪一侧。生成页传 `TopCenter` —— 图片是按宽度缩放的竖图，
     * 居中会在顶栏下面留一条空带，贴顶才贴合参考页面的观感。
     */
    alignment: Alignment = Alignment.Center,
    useThumbnail: Boolean = false,
) {
    val platform = LocalPlatform.current
    val cacheKey = "$path@$maxDimension@$useThumbnail"
    val bitmap by produceState<ImageBitmap?>(initialValue = ThumbnailCache.get(cacheKey), cacheKey) {
        val cached = ThumbnailCache.get(cacheKey)
        if (cached != null) {
            value = cached
            return@produceState
        }

        val decoded = withContext(Dispatchers.IO) {
            // 图库走磁盘缩略图；生成/失败时回落到原图
            val source = if (useThumbnail) {
                Thumbnails.fileFor(platform, path, maxDimension) ?: path
            } else {
                path
            }
            runCatching {
                val bytes = File(source).readBytes()
                decodeToFit(platform, bytes, maxDimension)
            }.getOrNull()
        }
        if (decoded != null) ThumbnailCache.put(cacheKey, decoded)
        value = decoded
    }

    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current,
            contentDescription = null,
            contentScale = contentScale,
            alignment = alignment,
            // ⚠️ 用户 2026-09-20：「图库的缩略图**狗牙多、模糊**」——
            // Compose 的 `Image` 默认 `FilterQuality.Low`（双线性、无 mipmap）：把一张比格子大的图
            // 缩到格子里时边缘会出锯齿（"狗牙"）✗，放大时又发糊 ✗。改成 `High`
            //（Skia 上 = mipmap + 三次采样），缩略图边缘和细节都干净得多。
            filterQuality = FilterQuality.High,
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        Box(
            modifier.fillMaxWidth().height(160.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
    }
}

// ---------------------------------------------------------------------------
// 共用的解码小工具
// ---------------------------------------------------------------------------

/** 读一个 ref 的字节：先试平台通道（手机 `content://`），不行再当普通文件读。 */
private fun readRefBytes(platform: Platform, ref: String): ByteArray? {
    platform.openInput(ref)?.use { return it.readBytes() }
    val file = File(ref)
    return if (file.isFile) file.readBytes() else null
}

/**
 * 降采样解码 + **补一次带滤波的缩放**，返回可直接画进 Compose 的位图。
 *
 * 关键在那第二次缩放：`inSampleSize` 只是「隔几个像素取一个」，不做滤波，
 * 直接拿去画就会有锯齿（观感上「小图有锯齿」的根因）。这里再按目标尺寸
 * 做一次 `scale(smooth = true)`，缩略图就干净了。
 *
 * ⚠️ 返回的位图**不能**回收：`toComposeImage` 是把同一份像素包一层给 Compose 用，
 * 回收了界面就画到已释放的位图上（手机上会直接抛异常）。中间那张被替换掉的才回收。
 */
private fun decodeToFit(platform: Platform, bytes: ByteArray, maxDimension: Int): ImageBitmap? {
    val decoded: NativeImage = platform.images.decodeSampled(bytes, maxDimension) ?: return null
    val longest = maxOf(decoded.width, decoded.height)
    if (longest <= maxDimension) return platform.images.toComposeImage(decoded)

    val ratio = maxDimension.toFloat() / longest
    val scaled = platform.images.scale(
        decoded,
        (decoded.width * ratio).roundToInt().coerceAtLeast(1),
        (decoded.height * ratio).roundToInt().coerceAtLeast(1),
        true,
    )
    if (scaled !== decoded) decoded.recycle()
    return platform.images.toComposeImage(scaled)
}
