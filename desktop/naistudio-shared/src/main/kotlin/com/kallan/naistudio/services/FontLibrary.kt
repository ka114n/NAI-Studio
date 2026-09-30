package com.kallan.naistudio.services

import com.kallan.naistudio.platform.Platform
import java.io.File

/**
 * **用户字体库**（高级漫画模式的"文本 / 对话气泡"要用）—— 用户 2026-09-26：
 * 「加可以打文本的功能，**可以导入字体**，打字」。
 *
 * ## 为什么单独一个目录
 * 导入的字体是**用户自己的资产**（可能是买的商用字体 ✗）：
 *  · 只存在本机 `<filesDir>/fonts/` ✓ —— **不进仓库、不进 git** ✓（版权）；
 *  · 卸载/清数据就没了，所以"字体列表"要能空着工作 ✓（回落到系统默认字体 ✓）。
 *
 * ## 支持的格式
 * `.ttf` / `.otf` / `.ttc`（Compose Desktop 走 `Font(File)`，这三样都能吃 ✓）。
 * 名字直接用**文件名去扩展名** ✓（要读家族名得解析字体表，这一版不做 ✗ ——
 * 想显示家族名的用户把文件名起清楚就行 ✓）。
 *
 * ⚠️ 这里**不碰 UI**：只负责"目录在哪、有什么、怎么装、怎么删" ✓。
 * 渲染交给 `ui/` 那边的 `FontFamily` 组装 ✓（下一批做 ✓）。
 */
object FontLibrary {

    /** 字体目录名（相对 `filesDir`）。 */
    const val DIR_NAME = "fonts"

    /** 认的扩展名（小写比较 ✓）。 */
    val EXTENSIONS = setOf("ttf", "otf", "ttc")

    /** 字体条目：`fileName` 是落盘名（唯一 ✓），`displayName` 是界面上显示的名字。 */
    data class Entry(val fileName: String, val displayName: String, val path: String)

    fun dir(platform: Platform): File =
        File(platform.paths.filesDir, DIR_NAME).apply { if (!exists()) mkdirs() }

    /** 列出可用字体：只认支持的扩展名、按显示名排序、同名只留一个 ✓。 */
    fun list(platform: Platform): List<Entry> =
        (dir(platform).listFiles() ?: emptyArray())
            .filter { it.isFile && isSupported(it.name) }
            .map { Entry(it.name, displayNameOf(it.name), it.absolutePath) }
            .distinctBy { it.displayName }
            .sortedBy { it.displayName.lowercase() }

    /**
     * 把一个字体**复制进字体库**（用户在设置/漫画页里"导入字体"走它 ✓）。
     *
     * @return 落盘文件名；读不到 / 不支持 / 写不动 → `null` ✓（调用方提示一下就行 ✓）。
     */
    fun install(platform: Platform, sourcePath: String): String? {
        if (!isSupported(sourcePath)) return null
        val source = File(sourcePath)
        if (!source.isFile) return null
        val target = uniqueTarget(dir(platform), sanitize(source.name))
        return try {
            source.copyTo(target, overwrite = false)
            target.name
        } catch (e: Exception) {
            null
        }
    }

    /** 删掉一个字体（按落盘名 ✓）。删不掉返回 false，不抛 ✓。 */
    fun remove(platform: Platform, fileName: String): Boolean = runCatching {
        val target = File(dir(platform), fileName)
        // 只允许删自己目录里的文件：名字里带路径分隔符的一律拒掉 ✓
        if (fileName.contains('/') || fileName.contains('\\')) return@runCatching false
        target.isFile && target.delete()
    }.getOrDefault(false)

    // ---------------------------------------------------------------- 纯逻辑（有单测 ✓）

    /** 这个文件名/路径是不是支持的字体格式。 */
    fun isSupported(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in EXTENSIONS
    }

    /** 界面上显示的名字：文件名去扩展名。 */
    fun displayNameOf(fileName: String): String =
        fileName.substringBeforeLast('.').ifBlank { fileName }

    /** 去掉路径分隔符与危险字符（用户选的文件名可能是别的目录里的 ✓）。 */
    fun sanitize(fileName: String): String =
        fileName.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\\\:*?\"<>|]"), "_")
            .ifBlank { "font.ttf" }

    /** 同名就加 `-2` / `-3` 后缀，不覆盖已有字体 ✓。 */
    fun uniqueTarget(dir: File, preferredName: String): File {
        val base = preferredName.substringBeforeLast('.')
        val ext = preferredName.substringAfterLast('.', "")
        var candidate = File(dir, preferredName)
        var n = 2
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isEmpty()) "$base-$n" else "$base-$n.$ext")
            n++
        }
        return candidate
    }
}
