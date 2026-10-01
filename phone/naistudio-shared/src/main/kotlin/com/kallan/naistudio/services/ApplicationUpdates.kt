package com.kallan.naistudio.services

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class ApplicationRelease(val version: String, val notes: String, val assetName: String, val url: String, val sha256: String, val size: Long)

/** Only stable releases from this repository, with a mandatory GitHub SHA-256 digest. */
object ApplicationUpdates {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(15, TimeUnit.MINUTES)
        .followSslRedirects(false).build()
    fun newer(candidate: String, current: String): Boolean {
        fun parts(v: String): List<Int> = v.removePrefix("v").split('.').map { it.toIntOrNull() ?: -1 }
        val a = parts(candidate); val b = parts(current)
        if (a.size != 3 || b.size != 3 || a.any { it < 0 } || b.any { it < 0 }) return false
        return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    }
    fun parse(json: String, kind: String, current: String): ApplicationRelease? {
        require(kind == "Windows" || kind == "Android")
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val version = root.getString("tag_name").removePrefix("v")
        if (!newer(version, current)) return null
        val name = "NAI-Studio-$kind-$version." + if (kind == "Windows") "zip" else "apk"
        val assets = root.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val item = assets.getJSONObject(i)
            if (item.optString("name") != name || item.optString("state") != "uploaded") continue
            val url = item.getString("browser_download_url")
            require(url == "https://github.com/ka114n/NAI-Studio/releases/download/v$version/$name") { "Unexpected update address" }
            val digest = item.getString("digest").removePrefix("sha256:")
            require(digest.matches(Regex("[0-9a-fA-F]{64}"))) { "Update checksum missing" }
            val size = item.getLong("size")
            require(size in 1..536870912L) { "Invalid update size" }
            return ApplicationRelease(version, root.optString("body"), name, url, digest, size)
        }
        error("This release has no verified $kind package")
    }
    fun check(kind: String, current: String): ApplicationRelease? {
        val req = Request.Builder().url("https://api.github.com/repos/ka114n/NAI-Studio/releases/latest")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "NAI-Studio/$current").build()
        return client.newCall(req).execute().use { response ->
            check(response.isSuccessful) { "Update server HTTP ${response.code}" }
            parse(response.body?.string() ?: error("Empty update response"), kind, current)
        }
    }
    fun verify(file: File, release: ApplicationRelease) {
        require(file.length() == release.size) { "Update download incomplete" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        require(actual.equals(release.sha256, true)) { "Update checksum mismatch" }
    }
    @Synchronized
    fun download(release: ApplicationRelease, directory: File, progress: (Float) -> Unit): File {
        directory.mkdirs()
        val partial = File(directory, release.assetName + ".partial")
        val destination = File(directory, release.assetName)
        try {
            client.newCall(Request.Builder().url(release.url).build()).execute().use { response ->
                check(response.isSuccessful) { "Download HTTP ${response.code}" }
                response.body!!.byteStream().use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(65536); var total = 0L; var notified = 0L
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        total += n; require(total <= release.size) { "Update download exceeds expected size" }
                        output.write(buffer, 0, n)
                        if (total - notified >= 262144) { progress(total.toFloat() / release.size); notified = total }
                    }
                } }
            }
            verify(partial, release)
            check(!destination.exists() || destination.delete())
            check(partial.renameTo(destination)) { "Cannot finalize update" }
            progress(1f)
            return destination
        } finally { partial.delete() }
    }
}
