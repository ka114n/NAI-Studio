package com.kallan.naistudio.platform

/** Optional offline image tagging, provided by the Windows host. */
interface LocalImageTagger {
    suspend fun infer(imagePath: String, onProgress: (String) -> Unit): String
    suspend fun upscale(imagePath: String): ByteArray = error("RTX VFX upscale is unavailable on this computer")
    fun close()
}
