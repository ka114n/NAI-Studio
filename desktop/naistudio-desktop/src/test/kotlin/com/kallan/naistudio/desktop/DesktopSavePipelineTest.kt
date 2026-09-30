package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.DesktopImageIo
import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.AppSettings
import com.kallan.naistudio.models.GenerateParams
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.services.Storage
import com.kallan.naistudio.services.Thumbnails
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **电脑端"生成图落盘 → 图库 → 缩略图"这条链路的集成测试。**
 *
 * 为什么要它：这条路是"电脑版到底能不能用"的主干，而它**在真机上要花 Anlas 才能走到**
 *（得有 token、得真生成一张）。但除了网络那一段，其余全是本地代码 ——
 * 完全可以用一张**自己合成的 PNG**把本地这一段跑通：
 *
 * ```
 * 合成 PNG 字节 → Storage.saveImage → 私有目录 images/ + 历史索引
 *                                    → 缩略图磁盘缓存（Thumbnails → Platform.images）
 *                                    → 相册（WindowsGallerySink → 图片\NAI Studio）
 * ```
 *
 * ⚠️ 档案是**隔离**的：`build.gradle.kts` 里把测试 JVM 的 `APPDATA` 与 `user.home`
 * 都指到 `build/test-home`，所以这里写文件不会碰到用户真实的 `%APPDATA%\NAI Studio`
 * 与"图片"目录（这一点必须保证，否则测试就是在往用户相册里丢垃圾）。
 */
class DesktopSavePipelineTest {

    private lateinit var dataDir: File
    private lateinit var picturesDir: File
    private lateinit var platform: Platform
    private lateinit var storage: Storage

    @Before
    fun setUp() {
        val home = System.getProperty("user.home")
        // `%APPDATA%\NAI Studio`（测试里被指到 build/test-home 下）
        dataDir = File(System.getenv("APPDATA") ?: home, "NAI Studio")
        picturesDir = File(File(home, "Pictures"), "NAI Studio")
        // 每个用例从干净档案开始（同一个测试 JVM 里多个用例共用这套目录）
        dataDir.deleteRecursively()
        picturesDir.deleteRecursively()

        platform = desktopPlatform()
        storage = Storage(platform)
    }

    /** 合成一张 PNG（棋盘格，方便肉眼认出是"测试图"）。 */
    private fun syntheticPng(width: Int, height: Int): ByteArray {
        val pixels = IntArray(width * height) { i ->
            if ((i / width + i % width) % 2 == 0) 0xFF3366CC.toInt() else 0xFFEEDDCC.toInt()
        }
        return DesktopImageIo.pngBytes(DesktopImageIo.fromArgb(pixels, width, height))
    }

    private fun defaultSettings() = AppSettings(
        saveToGallery = false,
        keepImageMetadata = true,
    )

    @Test
    fun saved_image_lands_in_private_dir_and_shows_up_in_history() = runBlocking {
        val item = storage.saveImage(
            bytes = syntheticPng(64, 96),
            params = GenerateParams(),
            seed = 123456789L,
            settings = defaultSettings(),
            groups = emptyList(),
        )

        // 1) 文件真的在 `<数据目录>/images/` 之下（再下面还有一层**日期**目录，
        //    见 `Storage.resolveSaveDir` —— 图按天分文件夹放，别写成"直接躺在 images 下"）
        val file = File(item.filePath)
        assertTrue("图片应落盘：${item.filePath}", file.isFile)
        assertTrue(item.filePath.endsWith(".png"))
        val imagesRoot = File(dataDir, "images").canonicalPath
        assertTrue(
            "应落在 images 之下，实际：${file.canonicalPath}",
            file.canonicalPath.startsWith(imagesRoot),
        )
        assertEquals(
            "日期目录名应为 yyyy-MM-dd：${file.parentFile!!.name}",
            true,
            file.parentFile!!.name.matches(Regex("\\d{4}-\\d{2}-\\d{2}")),
        )

        // 2) 尺寸/格式经得起解码器再读一遍
        assertEquals(64 to 96, platform.images.size(item.filePath))

        // 3) 历史索引里查得到（重启后图库就是靠这个列出来的）
        val history = storage.getHistory()
        assertEquals(1, history.size)
        assertEquals(item.id, history.first().id)
        assertEquals(item.filePath, history.first().filePath)
    }

    @Test
    fun saving_twice_with_the_same_name_does_not_overwrite() = runBlocking {
        val first = storage.saveImage(
            bytes = syntheticPng(16, 16),
            params = GenerateParams(),
            seed = 1L,
            settings = defaultSettings(),
            groups = emptyList(),
            stemOverride = "same-name",
        )
        val second = storage.saveImage(
            bytes = syntheticPng(16, 16),
            params = GenerateParams(),
            seed = 2L,
            settings = defaultSettings(),
            groups = emptyList(),
            stemOverride = "same-name",
        )

        // 同一秒连发两张（手机/电脑都会遇到）：不能覆盖，得各占一个文件
        assertTrue(first.filePath != second.filePath)
        assertTrue(File(first.filePath).isFile)
        assertTrue(File(second.filePath).isFile)
        assertEquals(File(first.filePath).length(), File(second.filePath).length())
    }

    @Test
    fun thumbnail_cache_generates_and_then_hits() = runBlocking {
        val item = storage.saveImage(
            bytes = syntheticPng(512, 768),
            params = GenerateParams(),
            seed = 3L,
            settings = defaultSettings(),
            groups = emptyList(),
        )

        // 第一次：真的生成一张小图（走 Platform.images 的解码 + 编码 + 目录）
        val thumbPath = Thumbnails.fileFor(platform, item.filePath, maxDimension = 192)
        assertNotNull("缩略图应生成出来", thumbPath)
        val thumb = File(thumbPath!!)
        assertTrue(thumb.isFile)
        // 落在 App 私有目录的 thumbnails_v2/ 下，且**比原图小**（这就是它存在的意义）
        assertTrue(thumb.parentFile!!.name.startsWith("thumbnails"))
        assertTrue(
            "缩略图(${thumb.length()}) 应小于原图(${File(item.filePath).length()})",
            thumb.length() < File(item.filePath).length(),
        )
        // 能被解码，且最长边不超过请求的上限
        val size = platform.images.size(thumb.absolutePath)!!
        assertTrue("缩略图最长边 ${maxOf(size.first, size.second)} 应 ≤ 192", maxOf(size.first, size.second) <= 192)

        // 第二次：命中缓存，返回同一个路径（不重复解码原图）
        assertEquals(thumbPath, Thumbnails.fileFor(platform, item.filePath, maxDimension = 192))
    }

    @Test
    fun gallery_sink_gets_the_file_when_enabled() = runBlocking {
        val item = storage.saveImage(
            bytes = syntheticPng(32, 32),
            params = GenerateParams(),
            seed = 4L,
            settings = AppSettings(saveToGallery = true, keepImageMetadata = true),
            groups = emptyList(),
        )

        // "同时保存到相册"开着 → 另存一份到"图片\NAI Studio"（测试里 user.home 被隔离）
        val copied = File(picturesDir, File(item.filePath).name)
        assertTrue("相册里应有这一份：${copied.absolutePath}", copied.isFile)
        // 原图仍在私有目录（另存，不是搬走）
        assertTrue(File(item.filePath).isFile)
    }

    @Test
    fun delete_history_removes_file_and_index_entry() = runBlocking {
        val item = storage.saveImage(
            bytes = syntheticPng(48, 48),
            params = GenerateParams(),
            seed = 5L,
            settings = defaultSettings(),
            groups = emptyList(),
        )
        val file = File(item.filePath)
        assertTrue(file.isFile)

        storage.deleteHistory(item.id)

        assertFalse("文件应被删掉", file.exists())
        assertTrue("索引里不该再有它", storage.getHistory().none { it.id == item.id })
    }
}
