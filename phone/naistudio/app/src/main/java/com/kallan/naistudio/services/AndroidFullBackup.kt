package com.kallan.naistudio.services

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.kallan.naistudio.BuildConfig
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Complete saved data migration. Secrets leave the sandbox only inside a password-encrypted archive. */
object AndroidFullBackup {
    data class Report(val files: Int, val secretEntries: Int, val bytes: Long)
    private const val FORMAT = "nai-studio-full-backup"
    private var startupRecoveryChecked = false
    private fun journalFile(context: Context) = File(context.noBackupFilesDir, "full-restore-journal.json")
    fun hasPendingRestore(context: Context): Boolean = journalFile(context).isFile

    /** A killed process must recover the previous snapshot before AppState loads it. */
    @Synchronized
    fun recoverInterruptedRestore(context: Context) {
        if (startupRecoveryChecked) return
        val journal = journalFile(context)
        if (!journal.isFile) { startupRecoveryChecked = true; return }
        val saved = JSONObject(journal.readText())
        val work = File(saved.getString("work")).canonicalFile
        require(work.parentFile == context.cacheDir.canonicalFile && work.name.startsWith("full-restore-"))
        val root = context.filesDir.canonicalFile
        val previous = File(work, "previous-files")
        if (previous.exists()) {
            if (root.exists()) check(root.renameTo(File(work, "interrupted-files-${UUID.randomUUID()}")))
            check(previous.renameTo(root))
        }
        val stores = FullBackupMetadata.decodePreferences(saved.getJSONObject("preferences"))
        stores.forEach { (name, values) -> replacePreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE), values) }
        val targetNames = saved.getJSONArray("targetPreferenceNames")
        for (i in 0 until targetNames.length()) {
            val name = targetNames.getString(i)
            require(FullBackupMetadata.validPreferenceName(name))
            if (name !in stores) check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit())
        }
        val secure = saved.getJSONObject("secureCiphertext")
        replacePreferences(context.getSharedPreferences("nai_secure", Context.MODE_PRIVATE), secure.keys().asSequence().associateWith { secure.getString(it) })
        check(journal.delete())
        work.deleteRecursively()
        startupRecoveryChecked = true
    }

    private fun preferenceNames(context: Context): Set<String> {
        val directory = File(context.applicationInfo.dataDir, "shared_prefs")
        return directory.listFiles().orEmpty().filter { it.name.endsWith(".xml") || it.name.endsWith(".xml.bak") }
            .map { it.name.removeSuffix(".bak").removeSuffix(".xml") }.toSet() +
            setOf("nai_store", "animadex_tool", "tagcodex_tool", "nai_widget")
    }
    private fun preferences(context: Context): Map<String, Map<String, *>> = preferenceNames(context)
        .filter { it != "nai_secure" }.associateWith { name ->
            require(FullBackupMetadata.validPreferenceName(name))
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toMap()
        }

    private fun replacePreferences(prefs: SharedPreferences, values: Map<String, *>) {
        val editor = prefs.edit().clear()
        values.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> { require(value.all { it is String }); @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>) }
            else -> error("Unsupported preference type")
        } }
        check(editor.commit()) { "无法保存应用设置" }
    }

    fun export(context: Context, uri: Uri, password: CharArray, progress: (String) -> Unit): Report {
        val work = File(context.cacheDir, "full-backup-${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            progress("正在收集设置、密钥和全部已保存文件…")
            val stores = preferences(context)
            val secrets = KeystoreSecretStore(context).exportForMigration()
            val root = context.filesDir.canonicalFile
            val files = mutableListOf<Pair<String, File>>()
            root.walkTopDown().onFail { _, exception -> throw exception }.forEach { file ->
                require(file.canonicalFile.toPath().startsWith(root.toPath())) { "私有文件包含越界链接，无法完整备份" }
                if (file.isFile) files.add("files/" + file.relativeTo(root).invariantSeparatorsPath to file)
            }
            val external = JSONObject()
            FullBackupMetadata.pathReferences(stores).sorted().forEach { reference ->
                val file = File(reference)
                if (file.isFile && !file.canonicalFile.toPath().startsWith(root.toPath())) {
                    val path = "external/${external.length()}/${file.name}"
                    files.add(path to file)
                    external.put(reference, path)
                }
            }
            // History originals must be present; do not call a missing-image archive complete.
            val history = (stores["nai_store"]?.get("history_index_v2") as? String)?.let { org.json.JSONArray(it) }
            if (history != null) for (i in 0 until history.length()) {
                val item = history.getJSONObject(i)
                for (key in listOf("filePath", "upscaleOfPath")) {
                    val path = if (item.isNull(key)) "" else item.optString(key)
                    require(path.isBlank() || File(path).isFile) { "图库存在无法读取的原图；请先恢复或移除失效记录再完整备份" }
                }
            }
            val settings = (stores["nai_store"]?.get("app_settings") as? String)?.let { JSONObject(it) }
            if (settings != null) for (key in listOf("infiniteCanvasSnapshot", "infiniteCanvasSource")) {
                val path = if (settings.isNull(key)) "" else settings.optString(key)
                require(path.isBlank() || File(path).isFile) { "无限画布引用的图像缺失；请先恢复图像再完整备份" }
                if (key == "infiniteCanvasSnapshot" && path.isNotBlank()) {
                    require(File(path.removeSuffix(".png") + ".origin").isFile) { "无限画布原点记录缺失，无法完整备份" }
                }
            }
            val manifest = JSONObject().put("format", FORMAT).put("version", 1)
                .put("packageName", context.packageName).put("appVersion", BuildConfig.VERSION_NAME)
                .put("exportedAt", java.time.Instant.now().toString()).put("sourceFilesRoot", root.absolutePath)
                .put("preferences", FullBackupMetadata.encodePreferences(stores))
                .put("secrets", JSONObject(secrets)).put("externalPaths", external)
            val zip = File(work, "snapshot.zip")
            FullBackupArchive.writeZip(zip, manifest.toString(), files) { count, total -> progress("正在打包 $count / $total 个文件…") }
            progress("正在加密导出；请勿退出应用…")
            context.contentResolver.openOutputStream(uri, "wt")?.use { FullBackupArchive.encrypt(zip, it, password) }
                ?: error("无法打开备份保存位置")
            check(zip.delete()) { "无法清理临时快照" }
            progress("正在回读校验备份和全部文件…")
            context.contentResolver.openInputStream(uri)?.use { FullBackupArchive.decrypt(it, zip, password) }
                ?: error("无法回读备份，请选择本机可读取的文件夹")
            val verified = validateManifest(FullBackupArchive.verifyZip(zip), context.packageName)
            require(verified.getJSONObject("secrets").toString() == manifest.getJSONObject("secrets").toString())
            return report(verified)
        } finally { password.fill('\u0000'); work.deleteRecursively() }
    }

    fun restore(context: Context, uri: Uri, password: CharArray, progress: (String) -> Unit): Report {
        val work = File(context.cacheDir, "full-restore-${UUID.randomUUID()}").apply { check(mkdirs()) }
        var recoveryFailed = false
        try {
            progress("正在解密校验；验证完成前不会修改当前数据…")
            val zip = File(work, "snapshot.zip")
            context.contentResolver.openInputStream(uri)?.use { FullBackupArchive.decrypt(it, zip, password) } ?: error("无法打开备份")
            val manifest = validateManifest(FullBackupArchive.verifyZip(zip), context.packageName)
            val stage = File(work, "stage")
            FullBackupArchive.extractVerified(zip, stage)
            val restoredFiles = File(stage, "files").apply { mkdirs() }
            val root = context.filesDir.canonicalFile
            val external = manifest.getJSONObject("externalPaths")
            val mapped = external.keys().asSequence().associateWith { original ->
                val path = external.getString(original)
                require(path.startsWith("external/"))
                val source = File(stage, path).canonicalFile
                require(source.toPath().startsWith(File(stage, "external").canonicalFile.toPath()) && source.isFile)
                val destination = File(restoredFiles, "migrated-external-${UUID.randomUUID()}/${source.name}")
                check(destination.parentFile.mkdirs())
                check(source.renameTo(destination))
                File(root, destination.relativeTo(restoredFiles).path).absolutePath
            }
            val oldRoot = manifest.getString("sourceFilesRoot")
            val decoded = FullBackupMetadata.decodePreferences(manifest.getJSONObject("preferences"))
            val stores = decoded.mapValues { (_, values) -> values.mapValues { (_, value) ->
                if (value is String) FullBackupMetadata.remapString(value, oldRoot, root.absolutePath, mapped) else value
            } }
            val secretsJson = manifest.getJSONObject("secrets")
            val secrets = secretsJson.keys().asSequence().associateWith { secretsJson.getString(it) }
            val previousStores = preferences(context)
            val securePrefs = context.getSharedPreferences("nai_secure", Context.MODE_PRIVATE)
            val previousSecure = securePrefs.all.toMap()
            val previousFiles = File(work, "previous-files")
            val journal = journalFile(context)
            check(!journal.exists()) { "上次恢复尚未完成，请关闭重开应用" }
            val recovery = JSONObject().put("work", work.absolutePath)
                .put("preferences", FullBackupMetadata.encodePreferences(previousStores))
                .put("secureCiphertext", JSONObject(previousSecure))
                .put("targetPreferenceNames", org.json.JSONArray(stores.keys.toList()))
            // Persist recovery information before any replacement. It remains inside the app sandbox.
            journal.parentFile?.mkdirs()
            val pendingJournal = File(journal.parentFile, "full-restore-${UUID.randomUUID()}.pending")
            try {
                java.io.FileOutputStream(pendingJournal).use { out -> out.write(recovery.toString().toByteArray(Charsets.UTF_8)); out.fd.sync() }
                check(pendingJournal.renameTo(journal)) { "无法建立恢复记录" }
            } finally { pendingJournal.delete() }
            var movedOld = false
            var movedNew = false
            try {
                progress("校验通过，正在恢复原图、设置和凭据…")
                check(root.renameTo(previousFiles)) { "无法暂存原数据；未恢复" }
                movedOld = true
                check(restoredFiles.renameTo(root)) { "无法恢复图片文件" }
                movedNew = true
                stores.forEach { (name, values) -> replacePreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE), values) }
                KeystoreSecretStore(context).replaceFromMigration(secrets)
                check(journal.delete()) { "无法提交恢复记录" }
                return report(manifest)
            } catch (error: Exception) {
                try {
                    if (movedNew) check(root.renameTo(File(work, "failed-restored-files")))
                    if (movedOld) check(previousFiles.renameTo(root))
                    previousStores.forEach { (name, values) -> replacePreferences(context.getSharedPreferences(name, Context.MODE_PRIVATE), values) }
                    (stores.keys - previousStores.keys).forEach { name -> check(context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()) }
                    replacePreferences(securePrefs, previousSecure)
                    check(journal.delete())
                } catch (rollback: Exception) {
                    recoveryFailed = true
                    throw IllegalStateException("恢复和回滚未完成，原数据保留在 ${work.absolutePath}，请勿卸载", rollback)
                }
                throw error
            }
        } finally { password.fill('\u0000'); if (!recoveryFailed) work.deleteRecursively() }
    }

    private fun validateManifest(text: String, packageName: String): JSONObject = JSONObject(text).also {
        require(it.getString("format") == FORMAT && it.getInt("version") == 1 && it.getString("packageName") == packageName) { "不是本应用支持的完整备份" }
        val root = it.getString("sourceFilesRoot")
        require(root.startsWith('/') && root.length > 1)
        FullBackupMetadata.decodePreferences(it.getJSONObject("preferences"))
        val secrets = it.getJSONObject("secrets")
        secrets.keys().asSequence().forEach { key -> secrets.getString(key) }
        it.getJSONObject("externalPaths")
    }
    private fun report(manifest: JSONObject): Report {
        val files = manifest.getJSONArray("files")
        return Report(files.length(), manifest.getJSONObject("secrets").length(), (0 until files.length()).sumOf { files.getJSONObject(it).getLong("size") })
    }
}
