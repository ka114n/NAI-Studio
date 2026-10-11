package com.kallan.naistudio.models

import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/** Decode in stable resolution steps as a fitted preview is zoomed, bounded to 64 MiB ARGB. */
object PreviewResolution {
    fun dimension(width: Int, height: Int, viewportWidth: Int, viewportHeight: Int, scale: Float): Int {
        if (width <= 0 || height <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return 1440
        val longest = maxOf(width, height)
        val fit = min(viewportWidth.toDouble() / width, viewportHeight.toDouble() / height)
        val desired = ceil(longest * fit * scale.takeIf { it.isFinite() }?.coerceIn(1f, 6f).let { it ?: 1f }).toInt()
        val bucket = listOf(512, 1024, 2048, 4096, 8192).firstOrNull { it >= desired } ?: 8192
        val budget = (longest * min(1.0, sqrt(16_777_216.0 / (width.toDouble() * height)))).toInt().coerceAtLeast(1)
        return minOf(bucket, longest, budget)
    }

    /** Preserve target detail before filtered resizing, unless the pixel budget requires sampling. */
    fun sample(width: Int, height: Int, target: Int): Int {
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / (sample.toLong() * 2) >= target.coerceAtLeast(1)) sample *= 2
        while ((width / sample).toLong() * (height / sample) > 16_777_216L) sample *= 2
        return sample
    }
}
