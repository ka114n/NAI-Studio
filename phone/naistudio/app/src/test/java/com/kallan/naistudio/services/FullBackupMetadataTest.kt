package com.kallan.naistudio.services

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FullBackupMetadataTest {
    @Test fun preservesPreferenceTypesAndAllKeys() {
        val input = mapOf("nai_store" to mapOf(
            "apiKey" to "synthetic-test-key", "bool" to true, "int" to 12,
            "long" to Long.MAX_VALUE, "float" to 0.25f, "set" to setOf("one", "two"), "unknown" to "keep"
        ))
        assertEquals(input, FullBackupMetadata.decodePreferences(FullBackupMetadata.encodePreferences(input)))
    }
    @Test fun remapsHistoryAndSnapshotsButNotPromptText() {
        val old = "/data/user/0/example/files"
        val new = "/data/user/10/example/files"
        val external = mapOf("/storage/emulated/0/old.png" to "$new/imported/new.png")
        val json = JSONObject().put("history", JSONArray().put(JSONObject().put("filePath", "$old/images/a.png").put("upscaleOfPath", "/storage/emulated/0/old.png")))
            .put("infiniteCanvasSnapshot", "$old/infinite/canvas.png")
            .put("prompt", "A picture at $old/images/a.png")
        val mapped = JSONObject(FullBackupMetadata.remapString(json.toString(), old, new, external))
        assertEquals("$new/images/a.png", mapped.getJSONArray("history").getJSONObject(0).getString("filePath"))
        assertEquals("$new/imported/new.png", mapped.getJSONArray("history").getJSONObject(0).getString("upscaleOfPath"))
        assertEquals("$new/infinite/canvas.png", mapped.getString("infiniteCanvasSnapshot"))
        assertEquals(json.getString("prompt"), mapped.getString("prompt"))
        assertEquals("$old-extra/a.png", FullBackupMetadata.remapString("$old-extra/a.png", old, new, external))
    }
    @Test fun findsNestedPrivateAndExternalPaths() {
        val values = mapOf("nai_store" to mapOf("history" to "[{\"filePath\":\"/storage/a.png\"}]", "settings" to "{\"nested\":{\"snapshot\":\"/data/files/a.png\"}}"))
        assertEquals(setOf("/storage/a.png", "/data/files/a.png"), FullBackupMetadata.pathReferences(values))
    }
    @Test fun rejectsUnsafeStoreAndUnknownTypes() {
        assertFalse(FullBackupMetadata.validPreferenceName("../nai_store"))
        assertFalse(FullBackupMetadata.validPreferenceName("nai_secure"))
        assertTrue(runCatching { FullBackupMetadata.decodePreferences(JSONObject("{\"nai_store\":{\"x\":{\"type\":\"object\",\"value\":{}}}}")) }.isFailure)
    }
}
