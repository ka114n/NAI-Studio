package com.kallan.naistudio.services

import java.io.File
import java.io.OutputStream

/** Copies only the selected original files; cleans up incomplete destination documents. */
object GalleryExportBatch {
    data class Target(val open: () -> OutputStream?, val delete: () -> Unit)
    data class Result(val exported: Int, val failed: Int)
    fun copy(files: List<File>, create: (File) -> Target?): Result {
        var exported = 0
        var failed = 0
        for (source in files) {
            var target: Target? = null
            try {
                require(source.isFile) { "Image file is unavailable" }
                target = create(source) ?: error("Cannot create destination image")
                val output = target.open() ?: error("Cannot open destination image")
                output.use { out -> source.inputStream().use { it.copyTo(out) }; out.flush() }
                exported++
            } catch (e: Exception) {
                target?.let { runCatching { it.delete() } }
                failed++
            }
        }
        return Result(exported, failed)
    }
}
