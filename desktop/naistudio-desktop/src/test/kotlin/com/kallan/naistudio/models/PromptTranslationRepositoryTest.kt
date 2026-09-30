package com.kallan.naistudio.models

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PromptTranslationRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun seed(): File {
        val directory = temporary.newFolder()
        File(directory, "tag_zh_core.json").writeText(JSONObject()
            .put("schema", 1).put("m", JSONObject())
            .put("d", JSONObject().put("long hair", "长发").put("longhair", "长发"))
            .put("a", JSONObject().put("smile", "微笑")).toString())
        File(directory, "tag_zh_sample.json").writeText(JSONObject()
            .put("schema", 1).put("d", JSONObject().put("blue eyes", "蓝眼睛")).toString())
        File(directory, "sample.json").writeText(JSONObject()
            .put("entries", JSONArray().put(JSONObject().put("id", "one")
                .put("tags", "long hair, long hair, smile"))).toString())
        return directory
    }

    private fun repository(directory: File, calls: AtomicInteger) =
        PromptTranslationRepository(directory) { path ->
            calls.incrementAndGet()
            JSONObject(File(directory, path.replace('/', '_')).readText())
        }

    @Test fun restartReadsPersistedIndexWithoutLoadingOrRebuildingData() = runBlocking {
        val directory = seed()
        val initial = repository(directory, AtomicInteger()).prepare("sample")
        assertEquals("long hair", initial.rev.getValue("长发").first())
        assertTrue(File(directory, "translation_index_sample.json").isFile)

        val calls = AtomicInteger()
        val restarted = repository(directory, calls).prepare("sample")
        assertEquals(initial.fwd, restarted.fwd)
        assertEquals(initial.rev, restarted.rev)
        assertEquals(0, calls.get())
    }

    @Test fun changedDataInvalidatesIndexEvenWithSameLengthAndTimestamp() = runBlocking {
        val directory = seed()
        repository(directory, AtomicInteger()).prepare("sample")
        val core = File(directory, "tag_zh_core.json")
        val timestamp = core.lastModified()
        val length = core.length()
        core.writeText(core.readText().replace("微笑", "笑容"))
        assertTrue(core.setLastModified(timestamp))
        assertEquals(length, core.length())

        val calls = AtomicInteger()
        val updated = repository(directory, calls).prepare("sample")
        assertEquals("笑容", updated.fwd["smile"])
        assertEquals(3, calls.get())
    }

    @Test fun concurrentStartupAndTranslationShareOnePreparation() = runBlocking {
        val directory = seed()
        val calls = AtomicInteger()
        val repository = repository(directory, calls)
        val indexes = (0 until 6).map { async { repository.prepare("sample") } }.awaitAll()
        assertEquals(3, calls.get())
        indexes.forEach { assertEquals("蓝眼睛", it.fwd["blue eyes"]) }
    }

    @Test fun malformedOrOlderIndexIsRebuilt() = runBlocking {
        val directory = seed()
        File(directory, "translation_index_sample.json").writeText("{broken")
        val calls = AtomicInteger()
        assertEquals("微笑", repository(directory, calls).prepare("sample").fwd["smile"])
        assertEquals(3, calls.get())
        val index = File(directory, "translation_index_sample.json")
        index.writeText(JSONObject(index.readText()).put("version", 0).toString())
        calls.set(0)
        repository(directory, calls).prepare("sample")
        assertEquals(3, calls.get())
    }

    @Test fun missingOptionalShardStillPreparesCoreDictionary() = runBlocking {
        val directory = seed()
        assertTrue(File(directory, "tag_zh_sample.json").delete())
        val calls = AtomicInteger()
        val index = repository(directory, calls).prepare("sample")
        assertEquals("微笑", index.fwd["smile"])
        assertEquals("long hair", index.rev.getValue("长发").first())
        calls.set(0)
        repository(directory, calls).prepare("sample")
        assertEquals(0, calls.get())
    }

    @Test fun failureCanRetryAndNeverLeavesAUsablePartialIndex() = runBlocking {
        val directory = seed()
        val required = File(directory, "tag_zh_core.json")
        val original = required.readText()
        required.writeText("{}"); val calls = AtomicInteger()
        val repository = repository(directory, calls)
        try {
            repository.prepare("sample")
            fail("Missing core dictionary must not create an index")
        } catch (_: IllegalStateException) { }
        assertFalse(File(directory, "translation_index_sample.json").exists())
        required.writeText(original)
        assertEquals("微笑", repository.prepare("sample").fwd["smile"])
    }
}
