package com.kallan.naistudio.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import com.kallan.naistudio.services.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 从 `content://` / `file://` URI 解码显示（用于预设封面图）。
 * 同样在后台线程解码，并且只解到 [maxDimension]。
 */
@Composable
fun UriImage(
    uriString: String,
    maxDimension: Int = 256,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uriString, maxDimension) {
        value = if (uriString.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(uriString)
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@runCatching null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    if (bounds.outWidth <= 0) return@runCatching null
                    var sample = 1
                    while (bounds.outWidth / sample > maxDimension ||
                        bounds.outHeight / sample > maxDimension
                    ) {
                        sample *= 2
                    }
                    BitmapFactory.decodeByteArray(
                        bytes,
                        0,
                        bytes.size,
                        BitmapFactory.Options().apply { inSampleSize = sample },
                    )
                }.getOrNull()
            }
        }
    }

    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current.asImageBitmap(),
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
 * 每次重建都要重新解码所有可见缩略图。`BitmapFactory.decodeFile` **中途不可取消**，
 * 于是快速来回滑动会堆积一批还在跑的解码任务 + 大量 Bitmap 分配，表现就是越滑越卡、
 * 最后像卡死。加一层缓存后重建页面直接命中，不再重复解码。
 */
private object ThumbnailCache {
    private const val MAX_BYTES = 48 * 1024 * 1024

    private val cache = object : android.util.LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, value: Bitmap) {
        cache.put(key, value)
    }
}

/**
 * 从文件降采样解码后显示。
 *
 * 硬约束：**UI 层只持有 Bitmap 或文件路径，绝不持有 base64 / 原始 PNG 字节。**
 * 解码走 `inJustDecodeBounds` + `inSampleSize`，在后台线程执行，结果进 [ThumbnailCache]。
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
    val context = LocalContext.current
    val cacheKey = "$path@$maxDimension@$useThumbnail"
    val bitmap by produceState<Bitmap?>(initialValue = ThumbnailCache.get(cacheKey), cacheKey) {
        val cached = ThumbnailCache.get(cacheKey)
        if (cached != null) {
            value = cached
            return@produceState
        }

        val decoded = withContext(Dispatchers.IO) {
            // 图库走磁盘缩略图；生成/失败时回落到原图
            val source = if (useThumbnail) {
                Thumbnails.fileFor(context, path, maxDimension) ?: path
            } else {
                path
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
                sample *= 2
            }
            val raw = BitmapFactory.decodeFile(
                source,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return@withContext null

            // `inSampleSize` 不做滤波，缩到目标尺寸时会有锯齿；再补一次带滤波的缩放。
            val longest = maxOf(raw.width, raw.height)
            if (longest <= maxDimension) {
                raw
            } else {
                val ratio = maxDimension.toFloat() / longest
                val scaled = Bitmap.createScaledBitmap(
                    raw,
                    (raw.width * ratio).roundToInt().coerceAtLeast(1),
                    (raw.height * ratio).roundToInt().coerceAtLeast(1),
                    true,
                )
                if (scaled !== raw) raw.recycle()
                scaled
            }
        }
        if (decoded != null) ThumbnailCache.put(cacheKey, decoded)
        value = decoded
    }

    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = null,
            contentScale = contentScale,
            alignment = alignment,
            modifier = modifier.fillMaxWidth(),
        )
    } else {
        Box(
            modifier.fillMaxWidth().height(160.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
    }
}
