package com.kallan.naistudio.services

import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.platform.logInfo
import com.kallan.naistudio.platform.logWarn
import java.io.File

/**
 * **自定义背景图**的落盘管理（用户 2026-09-20）。
 *
 * 口径只有一句话：**设置里只记文件名，图躺在 App 数据目录里**，
 * 「选进来 / 读出来 / 清掉」三件事全在这一处 —— 设置页与入口（`Main.kt`）都只调这里的函数，
 * "盘上到底是什么"就只有一个答案。
 *
 * ## 为什么必须复制一份，而不是把用户选的路径存下来
 *
 * 用户选的那张图**是他自己的文件**：随时可能被挪走、改名、删除，或者放在一个 U 盘 / 网络盘上。
 * 存原路径的话，下次启动背景就没了（用户只会觉得"这功能坏了"，还查不出原因）。
 * 复制进 App 私有目录之后，**设置与图同生共死**，不会再受外面影响。
 *
 * ## 命名
 *
 * `filesDir/custom_background_<毫秒时间戳>.<ext>`（同一时刻只有一张）。换图时先 [clear] 再写新的 ——
 * 换了扩展名（png → jpg）时旧的那张不会被覆盖，留着就是永远不会被引用的垃圾。
 * **时间戳是刻意的**（不是 "custom_background.<ext>" 那种固定名），理由见 [install]。
 */
object CustomBackground {

    /** 落盘文件名前缀，[clear] 按它认"哪些是我们放的"。 */
    const val FILE_PREFIX = "custom_background"

    /**
     * 认识的图片扩展名。**只影响落盘文件名的后缀**（解码看的是文件魔数，不看后缀），
     * 所以认不出来时回落 `png` 不会影响能不能显示。
     *
     * ⚠️ `webp` 保留在这里（**防御性**：万一用户从别处拷了张 webp 进来，落盘文件名别丢后缀），
     * 但**选图器里已经不列它了** —— JDK 17 的 ImageIO 读不了 webp，选了也只会静默回落主题底色 ✗。
     */
    private val KNOWN_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

    /**
     * 把用户选中的图**复制进 App 数据目录**，返回落盘文件名；失败（读不到 / 写不动）返回 null。
     *
     * ## ⚠️ 为什么文件名带时间戳，而不是固定的 `custom_background.<ext>`
     *
     * 设置里存的**就是这个文件名**，而"设置变了"是界面重新读图**唯一**的触发器
     * （见 `Main.kt`：`remember(settings.customBackground)`）。用固定名字的话，
     * 用户再选一张**同为 png** 的图 → 落盘名字一模一样 → 设置值没变 → 赋值给 Compose 的
     * 状态连"变了"都不算 → 界面继续画旧的那张，**要重启才换**（实测推演出来的坑）。
     * 带上毫秒时间戳，每次选图名字都不同，这条链路就永远是通的。
     *
     * @param ref 选图器给的不透明引用：电脑 = 文件路径，手机 = `content://…`；
     *   优先走 [Platform.openInput]（两端各自的通道），拿不到再当普通文件路径试一次。
     */
    fun install(platform: Platform, ref: String): String? = runCatching {
        val dir = platform.paths.filesDir.apply { mkdirs() }
        val name = "${FILE_PREFIX}_${System.currentTimeMillis()}.${extensionOf(ref)}"
        val target = File(dir, name)
        // 先清旧的：同一时刻只留一张（换扩展名 / 换图留下的旧文件不会被覆盖，留着就是垃圾）
        clear(platform)
        val wrote = platform.openInput(ref)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
            true
        } ?: copyPlainFile(ref, target)
        if (!wrote || !target.isFile) return null
        logInfo("CustomBackground", "自定义背景图已落盘：$name（${target.length()} 字节）")
        name
    }.onFailure { logWarn("CustomBackground", "自定义背景图落盘失败：${it.message}") }.getOrNull()

    /** 读回设置里记着的那张图；没配（空串）或文件已经不在 → null。 */
    fun fileOf(platform: Platform, name: String): File? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        // ⚠️ 只认**裸文件名**：设置文件是用户可编辑的，不能让它带出 `..\..\` 这种路径
        if (File(trimmed).name != trimmed) return null
        return File(platform.paths.filesDir, trimmed).takeIf { it.isFile }
    }

    /** 清掉所有落盘的自定义背景图（换图、以及设置页的「清除」都会调）。 */
    fun clear(platform: Platform) {
        runCatching {
            platform.paths.filesDir.listFiles()?.forEach { file ->
                // ⚠️ 前缀**不带点**：落盘名是 `custom_background_<时间戳>.<ext>`（见 [install]）
                if (file.isFile && file.name.startsWith(FILE_PREFIX)) file.delete()
            }
        }
    }

    /** 平台通道读不到时的退路（电脑上是普通路径；手机上的 `content://` 走不到这里）。 */
    private fun copyPlainFile(ref: String, target: File): Boolean = runCatching {
        val source = File(ref)
        if (!source.isFile) return false
        source.copyTo(target, overwrite = true)
        true
    }.getOrDefault(false)

    /** 从引用里抠扩展名（`content://…/1234` 这种没有扩展名的回落 `png`）。 */
    private fun extensionOf(ref: String): String {
        val ext = ref.substringBefore('?').substringAfterLast('/').substringAfterLast('.').lowercase()
        return if (ext in KNOWN_EXTENSIONS) ext else "png"
    }
}
