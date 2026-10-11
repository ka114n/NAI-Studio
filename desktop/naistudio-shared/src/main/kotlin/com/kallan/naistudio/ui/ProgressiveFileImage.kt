package com.kallan.naistudio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.kallan.naistudio.platform.LocalPlatform
import com.kallan.naistudio.platform.Platform
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// One detailed image, separate from gallery thumbnails. The pixel budget bounds the retained
// raster, not transient decoder buffers or the GPU texture. Wheel events never start new work.
private object DetailedPreviewCache {
    const val MAX_PIXELS = 33_554_432L // 128 MiB in ARGB; e.g. 6048 x 3456 stays full resolution.
    val decodeMutex = Mutex()
    private var identity: String? = null
    private var image: ImageBitmap? = null

    @Synchronized fun get(key: String): ImageBitmap? = image.takeIf { identity == key }
    @Synchronized fun put(key: String, value: ImageBitmap) {
        identity = key
        image = value
    }
}

/** Show a small raster first, then preload detail without clearing the visible image. */
@Composable
internal fun ProgressiveFileImage(path: String, modifier: Modifier = Modifier) {
    val platform = LocalPlatform.current
    val bitmap by key(path) {
        produceState<ImageBitmap?>(initialValue = null, path) {
            val identity = withContext(Dispatchers.IO) {
                val file = File(path)
                "$path@${file.length()}@${file.lastModified()}"
            }
            DetailedPreviewCache.get(identity)?.let {
                value = it
                return@produceState
            }
            val quick = withContext(Dispatchers.IO) {
                DetailedPreviewCache.decodeMutex.withLock { decodePreview(platform, path, quick = true) }
            }
            value = quick
            // Keep the quick image on screen throughout the expensive pass. Cancellation on a
            // new path prevents a completed old decode from replacing the newly selected image.
            val detail = withContext(Dispatchers.IO) {
                DetailedPreviewCache.decodeMutex.withLock { decodePreview(platform, path, quick = false) }
            }
            currentCoroutineContext().ensureActive()
            (detail ?: quick)?.let {
                DetailedPreviewCache.put(identity, it)
                value = it
            }
        }
    }
    val current = bitmap
    if (current != null) {
        Image(current, contentDescription = null, modifier = modifier,
            contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
    } else {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

private suspend fun decodePreview(platform: Platform, path: String, quick: Boolean): ImageBitmap? {
    val context = currentCoroutineContext()
    context.ensureActive()
    return runCatching {
        ImageIO.createImageInputStream(File(path)).use { stream ->
            if (stream == null) return@use null
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return@use null
            val reader = readers.next()
            try {
                reader.setInput(stream, true, true)
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                var sample = if (quick) ((maxOf(width, height).toLong() + 1535) / 1536).toInt() else 1
                sample = sample.coerceAtLeast(1)
                while (((width.toLong() + sample - 1) / sample) *
                    ((height.toLong() + sample - 1) / sample) > DetailedPreviewCache.MAX_PIXELS) {
                    sample *= 2
                }
                val params = reader.defaultReadParam.apply { setSourceSubsampling(sample, sample, 0, 0) }
                val decoded = reader.read(0, params) ?: return@use null
                context.ensureActive()
                try {
                    val pixels = decoded.getRGB(0, 0, decoded.width, decoded.height, null, 0, decoded.width)
                    context.ensureActive()
                    platform.images.composeImageOf(pixels, decoded.width, decoded.height)
                } finally {
                    decoded.flush()
                }
            } finally {
                reader.dispose()
            }
        }
    }.getOrElse {
        context.ensureActive() // Do not swallow cancellation as a failed image.
        null
    }
}
