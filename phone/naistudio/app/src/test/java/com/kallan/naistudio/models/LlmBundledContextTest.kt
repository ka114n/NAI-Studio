package com.kallan.naistudio.models

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The public build ships no prefilled user conversation history. */
class LlmBundledContextTest {
    private fun assetFile(): File =
        listOf("src/main/assets/llm_context.json", "app/src/main/assets/llm_context.json")
            .map { File(it) }
            .firstOrNull { it.exists() }
            ?: error("Missing assets/llm_context.json")

    @Test
    fun `bundled asset contains no prefilled conversation`() {
        assertTrue(LlmContext.parse(assetFile().readText()).isEmpty())
    }

    @Test
    fun `empty bundled context round-trips through user history format`() {
        val turns = LlmContext.parse(assetFile().readText())
        assertEquals(turns, LlmContext.parse(LlmContext.toUserHistoryJson(turns)))
    }
}
