package com.kallan.naistudio.platform

import java.io.File

/**
 * **平台层**：手机与电脑共用的代码只认这里的接口，不认 `Context` / `Uri` / `Bitmap`。
 *
 * ## 为什么这么设计
 *
 * 工程的规矩是「`naistudio-shared` 里不许出现 `import android.*`」，而共用代码又确实需要
 * 存设置、读密钥、写图片 —— 于是把这几件事抽成接口，两端各写一个实现：
 *
 * | 能力 | 手机 | 电脑 |
 * |---|---|---|
 * | [KeyValueStore] | SharedPreferences | `%APPDATA%` 下的文件 |
 * | [SecretStore] | Android Keystore（AES-GCM） | DPAPI（绑当前用户） |
 * | [AppPaths] | `filesDir` | `%APPDATA%\NAI Studio` |
 * | [GallerySink] | MediaStore 写相册 | 没有这回事（空实现） |
 * | [DeviceInfo] | `Build.MANUFACTURER/MODEL` | `os.name` |
 *
 * ## 一个刻意的设计：[KeyValueStore.edit] 照着 SharedPreferences 的形状做
 *
 * `prefs.edit().putString(k, v).apply()` 这种写法在 `Storage.kt` 里有几十处。
 * 如果接口只给 `putString(k,v)`，那几十处**每一处都要改**（还要小心漏掉 `.apply()`）；
 * 照抄 SharedPreferences 的编辑器形状，`Storage` 里就只用把 `prefs` 的来源换掉，
 * 其余一行不动 —— 这种"零 diff 搬迁"能显著降低把手机版改坏的风险。
 */
interface KeyValueStore {
    fun getString(key: String, defaultValue: String? = null): String?
    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    fun edit(): Editor

    /** 对齐 `SharedPreferences.Editor`：链式写入 + [apply] 落盘。 */
    interface Editor {
        fun putString(key: String, value: String): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun remove(key: String): Editor
        fun apply()
    }
}

/**
 * 密钥存储：**明文绝不落盘**。
 *
 * 手机上原来叫 `TokenVault`（Android Keystore 的 AES-GCM）；电脑上没有 Keystore，
 * 实现换成 Windows DPAPI。共用代码只调下面那几个扩展函数，不关心底下是什么。
 */
interface SecretStore {
    /** 读；缺失或解不开返回空串（解不开时顺手丢弃那条密文）。 */
    fun getSecret(key: String): String

    /** 写；值为空串等于删除。 */
    fun putSecret(key: String, value: String)

    fun removeSecret(key: String)
}

/** App 私有目录。 */
interface AppPaths {
    /** App 文档目录（历史索引、缩略图缓存等）。 */
    val filesDir: File

    /** 生成图的默认落盘根目录（手机上是 `filesDir/images`）。 */
    fun defaultImagesDir(): File
}

/** 把一张图塞进"系统相册"。手机 = MediaStore；电脑没有相册 → 返回 false。 */
interface GallerySink {
    fun putImage(file: File): Boolean
}

/** 设备信息（备份元数据里要记"从哪台设备导出的"）。 */
interface DeviceInfo {
    val manufacturer: String
    val model: String
}

/**
 * 平台能力总集：`Storage` 只依赖这一个对象。
 *
 * 收成一个而不是散着传，是为了**以后加能力不用改构造签名**
 * （下一步要加的图片编解码、文件选择器都往这儿挂）。
 */
interface Platform {
    val kv: KeyValueStore
    val secrets: SecretStore
    val paths: AppPaths
    val gallery: GallerySink
    val device: DeviceInfo

    /**
     * **无限画布的最大边长**（px ✓）—— 用户 2026-09-22 拍板：**桌面 4096 / 手机 3072** ✓
     *（理由见 `docs/69` §二 ✓：内存峰值 = 上限面积 × 2 张全尺寸表面 ⇒ 4096² ≈ 134 MB、
     * 3072² ≈ 74 MB ✓）。默认给桌面那一档；**手机这边覆盖成 3072** ✓（见 `AndroidPlatform` ✓）。
     */
    val infiniteCanvasMaxSide: Int
        get() = com.kallan.naistudio.models.INFINITE_MAX_SIDE_DESKTOP

    /**
     * 把一张图另存到用户选定的目录。
     *
     * · 手机：`treeUri` 是 SAF 的 tree URI（`content://…`），走 `DocumentsContract`；
     * · 电脑：`treeUri` 就是一个普通目录路径，直接拷过去。
     *
     * @return true = 真的写出去了；false = 没配 / 写不动（调用方按"可选步骤"处理，不当失败）
     */
    fun exportToUserDir(source: File, treeUri: String): Boolean
}

// ---------------------------------------------------------------------------
// token 的键名与语义（**键名是存储契约的一部分，不能改** —— 改了用户就得重新登录）
// ---------------------------------------------------------------------------

private const val KEY_TOKEN = "nai_token"
private const val KEY_ACCESS = "account_access_token"
private const val KEY_REFRESH = "account_refresh_token"

/**
 * **第三方网关那把 Key**（用户 2026-09-26：「第三方和官方的 api **分开存储**啊，可切换」）。
 *
 * 和官方的 `nai_token` **各存各的**：来回切的时候两把都留着，不用重填。
 * ⚠️ 新键名，老版本没有 ⇒ 读出来就是空串（不存在迁移问题）；
 *    也**不要**把老 token 搬过来当网关 Key —— 官方 token 和 `nai-...` 虚拟 Key
 *    是两种东西，搬过去只会让人以为连上了。
 */
private const val KEY_GATEWAY_TOKEN = "gateway_token"

/** 对应参考实现的 `Storage.setToken`。 */
fun SecretStore.setToken(token: String) = putSecret(KEY_TOKEN, token)

fun SecretStore.getToken(): String = getSecret(KEY_TOKEN)

fun SecretStore.clearToken() = removeSecret(KEY_TOKEN)

// ---- 第三方网关那把 Key（和上面那把完全独立）----

fun SecretStore.setGatewayToken(token: String) = putSecret(KEY_GATEWAY_TOKEN, token)

fun SecretStore.getGatewayToken(): String = getSecret(KEY_GATEWAY_TOKEN)

fun SecretStore.clearGatewayToken() = removeSecret(KEY_GATEWAY_TOKEN)

fun SecretStore.setAuthTokens(accessToken: String, refreshToken: String) {
    putSecret(KEY_ACCESS, accessToken)
    putSecret(KEY_REFRESH, refreshToken)
}

fun SecretStore.getAccessToken(): String = getSecret(KEY_ACCESS)

fun SecretStore.getRefreshToken(): String = getSecret(KEY_REFRESH)

fun SecretStore.clearAuthTokens() {
    removeSecret(KEY_ACCESS)
    removeSecret(KEY_REFRESH)
}
