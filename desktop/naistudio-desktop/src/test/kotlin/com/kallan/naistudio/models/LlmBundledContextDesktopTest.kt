package com.kallan.naistudio.models

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The public build ships no prefilled user conversation history. */
class LlmBundledContextDesktopTest {
    private fun resourceFile(): File =
        listOf("src/main/resources/llm_context.json", "naistudio-desktop/src/main/resources/llm_context.json")
            .map { File(it) }
            .firstOrNull { it.exists() }
            ?: error("Missing resources/llm_context.json")

    @Test
    fun `bundled resource contains no prefilled conversation`() {
        assertTrue(LlmContext.parse(resourceFile().readText()).isEmpty())
    }

    @Test
    fun `empty bundled context round-trips through user history format`() {
        val turns = LlmContext.parse(resourceFile().readText())
        assertEquals(turns, LlmContext.parse(LlmContext.toUserHistoryJson(turns)))
    }
}
