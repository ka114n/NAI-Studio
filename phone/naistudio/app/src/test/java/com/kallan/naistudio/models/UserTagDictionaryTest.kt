package com.kallan.naistudio.models

import org.junit.Assert.*
import org.junit.Test

class UserTagDictionaryTest {
    private val base = PromptTranslate.Dict(mapOf("smile" to "微笑", "grin" to "微笑"), mapOf("微笑" to listOf("smile", "grin")))

    @Test fun aliasesPreserveBuiltInTranslationsInBothDirections() {
        val dict = UserTagDictionary.merge(base, mapOf("smile" to listOf("笑容"), "new tag" to listOf("新标签")))
        assertEquals(listOf(UserTagDictionary.Match("smile", listOf("笑容", "微笑"))), UserTagDictionary.search("{smile}", dict))
        assertEquals(listOf("smile"), UserTagDictionary.search("笑容", dict).map { it.tag })
        assertEquals(setOf("smile", "grin"), UserTagDictionary.search("微笑", dict).map { it.tag }.toSet())
        assertEquals(listOf(UserTagDictionary.Match("new tag", listOf("新标签"))), UserTagDictionary.search("new tag", dict))
        assertEquals(listOf("smile", "grin"), base.rev["微笑"])
    }

    @Test fun customTranslationIsAlsoUsedByNormalPromptTranslation() {
        val dict = UserTagDictionary.merge(base, mapOf("new tag" to listOf("新标签")))
        val planned = PromptTranslate.plan("新标签", dict)
        assertEquals("new tag", planned.single().tag)
        assertEquals(PromptTranslate.Kind.ZH, planned.single().kind)
    }

    @Test fun importAndSettingsBackupRoundTrip() {
        val entries = UserTagDictionary.parseImport("Smile\t微笑\nnew tag\t新标签")
        assertEquals(entries, UserTagDictionary.decode(UserTagDictionary.encode(entries)))
        val settings = AppSettings(customTagDictionaryJson = UserTagDictionary.encode(entries), positiveTagCardMode = true)
        val restored = AppSettings.fromJson(settings.toJson())
        assertEquals(entries, UserTagDictionary.decode(restored.customTagDictionaryJson))
        assertTrue(restored.positiveTagCardMode)
        assertEquals(entries, UserTagDictionary.parseImport(UserTagDictionary.encode(entries)))
    }

    @Test fun invalidBatchIsRejectedWithoutPartialImport() {
        assertThrows(IllegalArgumentException::class.java) { UserTagDictionary.parseImport("smile\t微笑\nbroken row") }
        assertThrows(IllegalArgumentException::class.java) { UserTagDictionary.parseImport("a,b\t组合") }
        assertEquals(emptyMap<String, List<String>>(), UserTagDictionary.decode("not json"))
        assertEquals(emptyList<UserTagDictionary.Match>(), UserTagDictionary.search("absent", base))
    }
}
