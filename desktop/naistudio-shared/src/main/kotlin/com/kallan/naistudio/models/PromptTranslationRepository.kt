package com.kallan.naistudio.models

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** User-local translation data and its derived index. No upstream data is bundled. */
class PromptTranslationRepository(
    private val cacheDir: File,
    private val loadJson: (String) -> JSONObject,
) {
    private val mutex = Mutex()
    private var memoryId: String? = null
    private var memoryFingerprint: String? = null
    private var memoryDict: PromptTranslate.Dict? = null

    suspend fun prepare(codexId: String): PromptTranslate.Dict = mutex.withLock {
        require(codexId.matches(Regex("[a-zA-Z0-9_-]+")))
        val fingerprint = withContext(Dispatchers.IO) { sourceFingerprint(codexId) }
        memoryDict?.let {
            if (memoryId == codexId && memoryFingerprint == fingerprint && fingerprint != null) {
                return@withLock it
            }
        }
        val saved = withContext(Dispatchers.IO) { readIndex(codexId, fingerprint) }
        if (saved != null) {
            remember(codexId, fingerprint, saved)
            return@withLock saved
        }

        // Fetch the required files once. API loaders themselves persist the original data.
        val (rawDoc, shards) = withContext(Dispatchers.IO) {
            val core = TagZhProtocol.parseShard(loadJson(TagZhProtocol.CORE_PATH))
                ?.takeUnless { it.isEmpty }
                ?: error("The core translation dictionary is unavailable")
            val extra = try {
                TagZhProtocol.parseShard(loadJson(TagZhProtocol.shardPath(codexId)))
                    ?.takeUnless { it.isEmpty }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null // Some codexes have no dedicated translation shard.
            }
            loadJson("$codexId.json") to listOfNotNull(core, extra)
        }
        val built = withContext(Dispatchers.Default) {
            val doc = TagCodexProtocol.parseDoc(
                rawDoc, TagCodexSummary(id = codexId, title = codexId), TagCodexMedia(),
            )
            PromptTranslate.buildDict(shards, tagFrequency(doc))
        }
        val finalFingerprint = withContext(Dispatchers.IO) {
            sourceFingerprint(codexId).also { signature ->
                if (signature != null) writeIndex(codexId, signature, built)
            }
        }
        remember(codexId, finalFingerprint, built)
        built
    }

    private fun remember(id: String, fingerprint: String?, dict: PromptTranslate.Dict) {
        memoryId = id
        memoryFingerprint = fingerprint
        memoryDict = dict
    }

    private fun tagFrequency(doc: TagCodexDoc): Map<String, Int> {
        val frequency = HashMap<String, Int>()
        for (entry in doc.entries) {
            val source = buildString {
                append(entry.tags)
                for (character in entry.characters) { append(','); append(character.prompt) }
            }
            for (piece in source.split(',')) {
                val key = TagZhProtocol.key(piece).ifEmpty {
                    piece.trim().replace(WEIGHT_PREFIX, "").removeSuffix("::").trim().lowercase()
                }
                if (key.isNotEmpty()) frequency[key] = (frequency[key] ?: 0) + 1
            }
        }
        return frequency
    }

    private fun sourceFingerprint(id: String): String? {
        val paths = listOf(TagZhProtocol.CORE_PATH, TagZhProtocol.shardPath(id), "$id.json")
        if (!sourceFile(paths.first()).isFile || !sourceFile(paths.last()).isFile) return null
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("translation-index:$VERSION".toByteArray(Charsets.UTF_8))
        for (path in paths) {
            digest.update(path.toByteArray(Charsets.UTF_8))
            val file = sourceFile(path)
            if (!file.isFile) {
                digest.update(0.toByte())
                continue
            }
            digest.update(1.toByte())
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sourceFile(path: String) = File(cacheDir, path.replace('/', '_'))
    private fun indexFile(id: String) = File(cacheDir, "translation_index_$id.json")

    private fun readIndex(id: String, fingerprint: String?): PromptTranslate.Dict? {
        if (fingerprint == null) return null
        return runCatching {
            val root = JSONObject(indexFile(id).readText())
            require(root.getInt("version") == VERSION && root.getString("source") == fingerprint)
            val fwdJson = root.getJSONObject("forward")
            val reverseJson = root.getJSONObject("reverse")
            val forward = HashMap<String, String>()
            for (key in fwdJson.keys()) {
                val value = fwdJson.getString(key)
                require(key.isNotBlank() && value.isNotBlank())
                forward[key] = value
            }
            require(forward.isNotEmpty())
            val reverse = HashMap<String, List<String>>()
            for (key in reverseJson.keys()) {
                val values = reverseJson.getJSONArray(key)
                require(key.isNotBlank() && values.length() > 0)
                reverse[key] = (0 until values.length()).map { index ->
                    values.getString(index).also { require(it.isNotBlank()) }
                }
            }
            PromptTranslate.Dict(forward, reverse)
        }.getOrNull()
    }

    private fun writeIndex(id: String, fingerprint: String, dict: PromptTranslate.Dict) {
        val root = JSONObject()
            .put("version", VERSION).put("source", fingerprint)
            .put("forward", JSONObject(dict.fwd))
        val reverse = JSONObject()
        for ((key, values) in dict.rev) reverse.put(key, JSONArray(values))
        root.put("reverse", reverse)
        // Saving is optional; translation still succeeds on read-only or full storage.
        runCatching {
            cacheDir.mkdirs()
            val destination = indexFile(id)
            val temporary = File(cacheDir, "${destination.name}.tmp")
            temporary.writeText(root.toString())
            try {
                Files.move(temporary.toPath(), destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    companion object {
        private const val VERSION = 1
        private val WEIGHT_PREFIX = Regex("^-?\\d+(?:\\.\\d+)?::")
    }
}
