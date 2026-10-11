package com.kallan.naistudio.desktop.platform

internal data class WindowsDialogRequest(
    val folder: Boolean = false,
    val save: Boolean = false,
    val title: String,
    val extensions: List<String> = emptyList(),
    val suggestedName: String? = null,
) {
    fun filters(): List<Pair<String, String>> {
        val patterns = extensions.distinct().joinToString(";") { "*$it" }
        return if (patterns.isBlank()) listOf("所有文件" to "*.*")
        else listOf("支持的文件 ($patterns)" to patterns, "所有文件" to "*.*")
    }

    fun defaultExtension(): String? {
        if (!save) return null
        val supplied = suggestedName?.let { java.io.File(it).name }?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
        return supplied ?: extensions.singleOrNull()?.removePrefix(".")
    }
}
