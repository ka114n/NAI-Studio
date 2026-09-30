package com.kallan.naistudio.services

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.kallan.naistudio.platform.AppPaths
import com.kallan.naistudio.platform.DeviceInfo
import com.kallan.naistudio.platform.GallerySink
import com.kallan.naistudio.platform.KeyValueStore
import com.kallan.naistudio.models.INFINITE_MAX_SIDE_PHONE
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.platform.SecretStore
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * **手机端的平台层实现**（2026-09-16）。
 *
 * 共用树里的代码只认 `com.kallan.naistudio.platform.*` 那几个接口；
 * Android 的味道**全部收在这一个文件里** —— 换到电脑上就是另写一个同样接口的文件。
 *
 * 几处**不能动的契约**（动了用户数据就没了）：
 *  · `KeyValueStore` 背后的 SharedPreferences 文件名：`nai_store`（设置/参数/历史索引）；
 *  · `SecretStore` 背后的 SharedPreferences 文件名：`nai_secure`（密文）；Keystore 别名
 *    `nai_studio_token_key`、变换 `AES/GCM/NoPadding`、密文格式 `base64(IV):base64(密文)`；
 *  · token 的键名（`nai_token` / `account_access_token` / `account_refresh_token`）
 *    定义在共用树 `platform/Platform.kt` 里。
 */
fun androidPlatform(context: Context): Platform {
    val app = context.applicationContext
    return object : Platform {
        override val kv: KeyValueStore = PrefsKeyValueStore(
            app.getSharedPreferences(PREFS_STORE, Context.MODE_PRIVATE),
        )
        override val secrets: SecretStore = KeystoreSecretStore(app)
        override val paths: AppPaths = AndroidAppPaths(app)
        override val gallery: GallerySink = MediaStoreGallerySink(app)
        override val device: DeviceInfo = AndroidDeviceInfo
        // 无限画布上限：**手机是 3072** ✓（桌面 4096 ✓，用户 2026-09-22 拍板 ✓）——
        // 内存峰值 = 上限面积 × 2 张全尺寸表面：4096² ≈ 134 MB ✗（手机偏重）/
        // 3072² ≈ 74 MB ✓（见 `docs/69` §二 ✓）
        override val infiniteCanvasMaxSide: Int = INFINITE_MAX_SIDE_PHONE
        override fun exportToUserDir(source: File, treeUri: String): Boolean =
            exportViaSaf(app, source, treeUri)
    }
}

/** SharedPreferences 文件名。**存储契约，不能改。** */
private const val PREFS_STORE = "nai_store"

// ---------------------------------------------------------------------------
// 键值：SharedPreferences
// ---------------------------------------------------------------------------

class PrefsKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {

    override fun getString(key: String, defaultValue: String?): String? =
        prefs.getString(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    override fun edit(): KeyValueStore.Editor = object : KeyValueStore.Editor {
        private val editor = prefs.edit()
        override fun putString(key: String, value: String): KeyValueStore.Editor =
            apply { editor.putString(key, value) }

        override fun putBoolean(key: String, value: Boolean): KeyValueStore.Editor =
            apply { editor.putBoolean(key, value) }

        override fun remove(key: String): KeyValueStore.Editor = apply { editor.remove(key) }

        override fun apply() {
            editor.apply()
        }
    }
}

// ---------------------------------------------------------------------------
// 密钥：Android Keystore（AES-GCM）—— 从原来的 TokenVault 原样搬过来
// ---------------------------------------------------------------------------

class KeystoreSecretStore(context: Context) : SecretStore {

    private val prefs = context.getSharedPreferences(PREFS_SECURE, Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /**
     * 加密写入任意键。值为空串等于删除。
     * 存储格式与原 token 一致：`base64(IV):base64(密文)`；同一把 Keystore 主密钥。
     */
    override fun putSecret(key: String, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            removeSecret(key)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) +
            SEPARATOR +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(key, payload).apply()
    }

    /** 解密读取任意键；缺失或解不开返回空串（解不开时丢弃该键）。 */
    override fun getSecret(key: String): String {
        val payload = prefs.getString(key, null) ?: return ""
        val parts = payload.split(SEPARATOR, limit = 2)
        if (parts.size != 2) {
            removeSecret(key)
            return ""
        }
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), spec)
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            // 密钥不可用（清除数据、恢复到新设备）→ 丢弃旧密文
            removeSecret(key)
            ""
        }
    }

    override fun removeSecret(key: String) {
        prefs.edit().remove(key).apply()
    }

    private companion object {
        const val PREFS_SECURE = "nai_secure"
        const val KEY_ALIAS = "nai_studio_token_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val SEPARATOR = ":"
    }
}

// ---------------------------------------------------------------------------
// 目录 / 相册 / 设备 / 另存
// ---------------------------------------------------------------------------

class AndroidAppPaths(private val context: Context) : AppPaths {
    override val filesDir: File get() = context.filesDir

    /** 手机上生成图落在私有目录 `filesDir/images`（相册是**另存**一份，见 GallerySink）。 */
    override fun defaultImagesDir(): File = File(context.filesDir, "images").apply { mkdirs() }
}

/** API 29+ 走 MediaStore（无需权限）；更低版本不申请 `WRITE_EXTERNAL_STORAGE`，直接跳过。 */
class MediaStoreGallerySink(private val context: Context) : GallerySink {
    override fun putImage(file: File): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/NAI Studio",
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        resolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { it.copyTo(output) }
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }
}

object AndroidDeviceInfo : DeviceInfo {
    override val manufacturer: String get() = Build.MANUFACTURER
    override val model: String get() = Build.MODEL
}

/**
 * 另存到用户选的目录（SAF tree URI）。
 *
 * 用 `DocumentsContract.createDocument`，**不引 androidx.documentfile** ——
 * 本项目运行时第三方依赖只有 OkHttp。同名文件由系统自动改名为 `xxx (1).png`。
 */
private fun exportViaSaf(context: Context, source: File, tree: String): Boolean = runCatching {
    val treeUri = Uri.parse(tree)
    val parent = DocumentsContract.buildDocumentUriUsingTree(
        treeUri,
        DocumentsContract.getTreeDocumentId(treeUri),
    )
    val created = DocumentsContract.createDocument(
        context.contentResolver,
        parent,
        "image/png",
        source.name,
    ) ?: return@runCatching false
    context.contentResolver.openOutputStream(created)?.use { out ->
        source.inputStream().use { input -> input.copyTo(out) }
        out.flush()
    }
    true
}.getOrDefault(false)
