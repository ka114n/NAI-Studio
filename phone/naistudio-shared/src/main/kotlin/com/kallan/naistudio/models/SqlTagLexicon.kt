package com.kallan.naistudio.models

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream
import org.json.JSONObject

data class LexiconEntry(val tag: String, val chinese: String, val frequency: Long, val category: Int)

/** A bounded, read-only SQL service; the full lexicon is never expanded into JVM maps. */
class SqlTagLexicon(private val query: (String, List<String>) -> List<List<String>>) {
    val count: Int = query("SELECT value FROM metadata WHERE key='rows'", emptyList()).first().first().toInt()
    private val entries = object : LinkedHashMap<String, LexiconEntry?>(512, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LexiconEntry?>?) = size > 512
    }

    init { require(query("SELECT value FROM metadata WHERE key='schema'", emptyList()).first().first() == "1") }

    @Synchronized fun entry(text: String): LexiconEntry? {
        val key = normalize(text)
        if (entries.containsKey(key)) return entries[key]
        val value = rows("SELECT name,cn_name,post_count,category FROM tags WHERE name=?", listOf(key)).firstOrNull()
        entries[key] = value
        return value
    }

    fun chinese(text: String): List<LexiconEntry> = rows(
        "SELECT name,cn_name,post_count,category FROM tags WHERE cn_key=? ORDER BY post_count DESC,name LIMIT 256",
        listOf(normalize(text)),
    )

    /** Translation may expand partial names only within Danbooru's character category. */
    fun fuzzyChineseCharacters(text: String): List<LexiconEntry> {
        val key = normalize(text)
        if (key.isEmpty()) return emptyList()
        return rows("""SELECT t.name,t.cn_name,t.post_count,t.category FROM tag_search
            JOIN tags t ON t.id=tag_search.docid WHERE tag_search MATCH ? AND t.category=4 AND t.cn_key LIKE ? ESCAPE '\'
            ORDER BY CASE WHEN t.cn_key LIKE ? ESCAPE '\' THEN 0 ELSE 1 END,
            t.post_count DESC,length(t.cn_key),t.cn_key,t.name LIMIT 256""",
            listOf(matchTokens(key), "%${escapeLike(key)}%", "${escapeLike(key)}%"))
    }

    fun search(text: String, limit: Int, category: Int? = null): List<LexiconEntry> {
        val key = normalize(text).take(200)
        if (key.isEmpty() || limit <= 0) return emptyList()
        val words = key.split(' ')
        val args = mutableListOf(matchTokens(key))
        val en = words.joinToString(" AND ") { args.add("%${escapeLike(it)}%"); "t.name LIKE ? ESCAPE '\\'" }
        val zh = words.joinToString(" AND ") { args.add("%${escapeLike(it)}%"); "t.cn_key LIKE ? ESCAPE '\\'" }
        val categoryFilter = if (category != null) { args.add(category.toString()); " AND t.category=?" } else ""
        args.addAll(listOf(key, key, "${escapeLike(key)}%", "${escapeLike(key)}%", limit.coerceAtMost(10000).toString()))
        // Parameters carry both the FTS tokens and literal substring patterns. No input is SQL.
        return rows("""SELECT t.name,t.cn_name,t.post_count,t.category FROM tag_search
            JOIN tags t ON t.id=tag_search.docid WHERE tag_search MATCH ? AND (($en) OR ($zh))$categoryFilter
            ORDER BY CASE WHEN t.name=? OR t.cn_key=? THEN 0
                WHEN t.name LIKE ? ESCAPE '\' OR t.cn_key LIKE ? ESCAPE '\' THEN 1 ELSE 2 END,
                t.post_count DESC,t.name LIMIT ?""", args)
    }

    private fun rows(sql: String, args: List<String>): List<LexiconEntry> = query(sql, args).map {
        LexiconEntry(it[0], it[1], it[2].toLong(), it[3].toInt())
    }

    companion object {
        const val CHARACTER_CATEGORY = 4
        private val spaces = Regex("\\s+")
        fun normalize(text: String) = text.lowercase(Locale.ROOT).replace('_', ' ').replace(spaces, " ").trim()
        private fun escapeLike(text: String) = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        private fun matchTokens(text: String): String = text.split(' ').filter { it.isNotEmpty() }.flatMap { word ->
            val chars = word.codePoints().toArray()
            if (chars.size == 1) listOf("g" + chars[0].toString(16).padStart(6, '0'))
            else (0 until chars.size - 1).map { i ->
                "g" + chars[i].toString(16).padStart(6, '0') + chars[i + 1].toString(16).padStart(6, '0')
            }
        }.distinct().take(64).joinToString(" ")
    }
}

/** The package contains only the manifest. Network access is explicit and user initiated. */
object TagLexiconAsset {
    private const val ARCHIVE_URL = "https://raw.githubusercontent.com/ka114n/NAI-Studio-dictionary/c5e42ce0633cb8895a0fed9d5962703855683af1/danbooru.sqlite.gz"
    const val DOWNLOAD_BYTES = 38334560L
    private val manifest by lazy {
        JSONObject(resource("/danbooru-manifest.json").bufferedReader().use { it.readText() }).also {
            require(it.getInt("schema") == 1)
            require(it.getString("sha256").matches(Regex("[a-f0-9]{64}")))
        }
    }
    private fun destination(directory: File) = File(directory, "danbooru-${manifest.getString("sha256").take(16)}.sqlite")
    fun installed(directory: File): Boolean = destination(directory).let { it.isFile && it.length() == manifest.getLong("bytes") }

    // Includes databases extracted by earlier bundled versions, without downloading again.
    @Synchronized fun prepare(directory: File): File {
        val checksum = manifest.getString("sha256")
        val file = destination(directory)
        check(installed(directory)) { "Main dictionary has not been downloaded" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val bytes = ByteArray(65536)
            while (true) {
                val length = input.read(bytes)
                if (length < 0) break
                digest.update(bytes, 0, length)
            }
        }
        check(digest.digest().joinToString("") { "%02x".format(it) } == checksum) { "Dictionary checksum mismatch; download again" }
        return file
    }

    @Synchronized fun download(directory: File, progress: (Int) -> Unit, checkActive: () -> Unit) {
        val checksum = manifest.getString("sha256")
        val expectedSize = manifest.getLong("bytes")
        check(directory.isDirectory || directory.mkdirs())
        val file = destination(directory)
        val temporary = File(directory, "${file.name}.tmp")
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(5, java.util.concurrent.TimeUnit.MINUTES).build()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            var downloaded = 0L
            var lastProgress = -1
            client.newCall(okhttp3.Request.Builder().url(ARCHIVE_URL).header("Accept-Encoding", "identity").build()).execute().use { response ->
                check(response.isSuccessful) { "Dictionary download failed: HTTP ${response.code}" }
                val body = checkNotNull(response.body)
                val counted = object : java.io.FilterInputStream(body.byteStream()) {
                    override fun read(b: ByteArray, off: Int, len: Int): Int = `in`.read(b, off, len).also {
                        checkActive()
                        if (it > 0) {
                            downloaded += it
                            require(downloaded <= DOWNLOAD_BYTES) { "Unexpected dictionary download size" }
                            val percent = (downloaded * 100 / DOWNLOAD_BYTES).toInt().coerceAtMost(99)
                            if (percent != lastProgress) { lastProgress = percent; progress(percent) }
                        }
                    }
                }
                GZIPInputStream(counted.buffered()).use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val bytes = ByteArray(65536)
                        while (true) {
                            checkActive()
                            val length = input.read(bytes)
                            if (length < 0) break
                            written += length
                            require(written <= expectedSize)
                            output.write(bytes, 0, length)
                            digest.update(bytes, 0, length)
                        }
                    }
                }
            }
            require(written == expectedSize && digest.digest().joinToString("") { "%02x".format(it) } == checksum)
            checkActive()
            java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            progress(100)
        } finally {
            temporary.delete()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun resource(name: String) = TagLexiconAsset::class.java.getResourceAsStream(name)
        ?: error("Missing packaged dictionary resource: $name")
}
