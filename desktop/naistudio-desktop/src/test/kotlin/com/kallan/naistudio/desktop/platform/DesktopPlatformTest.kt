package com.kallan.naistudio.desktop.platform

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **电脑端平台层的单测。**
 *
 * 为什么要有：这一层是"电脑版正确性"的所在，但里面几条代码路径**用户不常走**，
 * 出问题也很难被肉眼发现 ——
 *
 *  · "保存到相册"的同名改名（用户有一张同名图时才知道有没有覆盖）；
 *  · 密钥文件的**老格式迁移**与**换机器后的恢复**（都要有第二台机器才试得出来）；
 *  · 键值存储的落盘格式（这次写坏了，下次启动才发现）。
 *
 * 统一用 `@TempDir` 风格的临时目录，**绝不碰用户真实的 `%APPDATA%` 与"图片"目录**。
 */
class DesktopPlatformTest {

    private fun tempDir(): File = Files.createTempDirectory("naist-desktop-test").toFile()

    // -----------------------------------------------------------------------
    // 画布编辑器的显示位图：ARGB 像素 → Compose ImageBitmap（颜色通道顺序最容易写反）
    //
    // `composeImageOf` 走的是 skia 的 `makeRaster`，而 skia 要的是 **BGRA_8888**：
    // 写成 RGBA 的话红蓝会对调 —— 光看代码看不出来，只有把像素读回来才认得出。
    // -----------------------------------------------------------------------

    @Test
    fun compose_image_of_keeps_argb_channels() {
        val red = 0xFFFF0000.toInt()
        val half = 0x80123456.toInt()
        val pixels = intArrayOf(red, half)

        val bitmap = DesktopImageIo.composeImageOf(pixels, 2, 1)
        assertTrue("应该能建出位图", bitmap != null)

        val map = bitmap!!.toPixelMap()
        assertEquals(
            "红：R 必须留在 R 上（写成 RGBA 会红蓝互换）",
            red,
            map[0, 0].toArgb(),
        )
        assertEquals("半透明色要逐位一致", half, map[1, 0].toArgb())
    }

    // -----------------------------------------------------------------------
    // 键值存储：落盘 + 重新打开还在（设置就是这样活过重启的）
    // -----------------------------------------------------------------------

    @Test
    fun key_value_store_survives_reopen() {
        val file = File(tempDir(), "prefs.json")

        FileKeyValueStore(file).apply {
            edit().putString("theme", "dark").putBoolean("saveToGallery", false).putInt("n", 7).apply()
        }

        // 新开一个实例 = 模拟"关掉 App 再打开"
        val reopened = FileKeyValueStore(file)
        assertEquals("dark", reopened.getString("theme"))
        assertFalse(reopened.getBoolean("saveToGallery", true))
        assertEquals(7, reopened.getInt("n", 0))
        // 没写过的键走默认值
        assertEquals("fallback", reopened.getString("missing", "fallback"))
    }

    @Test
    fun key_value_store_edit_remove_and_clear() {
        val file = File(tempDir(), "prefs.json")
        val store = FileKeyValueStore(file)
        store.edit().putString("a", "1").putString("b", "2").apply()
        store.edit().remove("a").apply()
        val reopened = FileKeyValueStore(file)
        assertEquals(null, reopened.getString("a"))
        assertEquals("2", reopened.getString("b"))
    }

    // -----------------------------------------------------------------------
    // 密钥存储：DPAPI 往返 / 老格式迁移（主密钥不变）/ 换机器后的恢复
    // -----------------------------------------------------------------------

    @Test
    fun secret_store_roundtrip_and_hides_plaintext() {
        val dir = tempDir()
        val store = FileSecretStore(File(dir, "secrets.bin"))
        store.putSecret("nai_token", "TOKEN-abc-123")

        // 换实例再读 = 真从盘上读
        assertEquals("TOKEN-abc-123", FileSecretStore(File(dir, "secrets.bin")).getSecret("nai_token"))
        // 密文文件里不该出现明文
        val raw = File(dir, "secrets.bin").readText()
        assertFalse(raw.contains("TOKEN-abc-123"))
        // 空值等于删除
        store.putSecret("nai_token", "  ")
        assertEquals("", store.getSecret("nai_token"))
    }

    @Test
    fun secret_store_migrates_legacy_raw_key_without_changing_it() {
        val dir = tempDir()
        val keyFile = File(dir, "secret.key")
        val legacy = ByteArray(32) { it.toByte() }
        keyFile.writeBytes(legacy)

        val store = FileSecretStore(File(dir, "secrets.bin"))
        store.putSecret("nai_token", "LEGACY-TOKEN")
        assertEquals("LEGACY-TOKEN", store.getSecret("nai_token"))

        // 迁移后：文件被 DPAPI 包过，但**主密钥必须还是原来那 32 字节**
        //（不然老密文全解不开，用户得重新登录 —— 这是迁移的唯一验收点）
        val migrated = keyFile.readBytes()
        assertTrue("应带 DPAPI 前缀", migrated.size > 32)
        assertNotEquals(32, migrated.size)
        val restored = com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(
            migrated.copyOfRange("DPAPI1\n".length, migrated.size),
        )
        assertTrue("解包结果必须等于老密钥", restored.contentEquals(legacy))
    }

    @Test
    fun secret_store_recovers_when_key_cannot_be_unwrapped() {
        val dir = tempDir()
        val keyFile = File(dir, "secret.key")
        // 造一份"换了机器"的现场：格式对、内容解不开
        keyFile.writeBytes("DPAPI1\n".toByteArray() + ByteArray(180) { 7 })

        val store = FileSecretStore(File(dir, "secrets.bin"))
        store.putSecret("nai_token", "NEW-TOKEN")
        // 关键：**不能静默失败** —— 换机器后重新填的 token 必须存得下、读得回
        assertEquals("NEW-TOKEN", store.getSecret("nai_token"))
        assertEquals("NEW-TOKEN", FileSecretStore(File(dir, "secrets.bin")).getSecret("nai_token"))
    }

    // -----------------------------------------------------------------------
    // 相册：另存 + 同名自动改名（不覆盖用户已有的图）
    // -----------------------------------------------------------------------

    @Test
    fun gallery_sink_copies_and_renames_on_conflict() {
        val dir = tempDir()
        val source = File(dir, "shot.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val target = File(dir, "gallery")

        assertTrue(WindowsGallerySink.putImageInto(target, source))
        assertEquals(listOf("shot.png"), target.list()!!.toList())

        // 再来一张同名：必须变成 "shot (2).png"，而不是覆盖
        assertTrue(WindowsGallerySink.putImageInto(target, source))
        assertEquals(listOf("shot (2).png", "shot.png"), target.list()!!.sorted())
        // 原图还在，内容也对
        assertEquals(3, File(target, "shot.png").length().toInt())
    }

    @Test
    fun gallery_sink_reports_failure_instead_of_throwing() {
        val dir = tempDir()
        val missing = File(dir, "not-there.png")
        assertFalse(WindowsGallerySink.putImageInto(File(dir, "gallery"), missing))
    }

    @Test
    fun gallery_sink_keeps_extension_when_renaming() {
        val dir = tempDir()
        File(dir, "a.jpg").writeBytes(byteArrayOf(1))
        val renamed = WindowsGallerySink.uniqueTarget(dir, "a.jpg")
        assertEquals("a (2).jpg", renamed.name)
    }

    // -----------------------------------------------------------------------
    // 图片编解码：尺寸 / alpha / 两种编码 / 缩放
    // -----------------------------------------------------------------------

    @Test
    fun image_io_roundtrip_size_and_alpha() {
        // 2×2：一个不透明红、一个半透明蓝 —— 用来验证 alpha 判定
        val pixels = intArrayOf(
            0xFFFF0000.toInt(), 0x800000FF.toInt(),
            0xFFFF0000.toInt(), 0xFFFF0000.toInt(),
        )
        val image = DesktopImageIo.fromArgb(pixels, 2, 2)
        assertEquals(2, image.width)
        assertEquals(2, image.height)
        assertTrue("半透明像素应被识别为含 alpha", DesktopImageIo.hasAlpha(image))

        // 存成 PNG 再读回来：尺寸一致（走的是真实文件路径）
        val file = File(tempDir(), "img.png")
        file.writeBytes(DesktopImageIo.pngBytes(image))
        assertEquals(2 to 2, DesktopImageIo.size(file.absolutePath))

        // `decodeFull` 也走一遍
        val decoded = DesktopImageIo.decodeFull(file.absolutePath)!!
        assertEquals(2, decoded.width)
        assertEquals(4, DesktopImageIo.pixelsOf(decoded).size)
    }

    @Test
    fun image_io_jpeg_drops_alpha_without_crashing() {
        // JPEG 不支持 alpha：半透明像素要能正常写出去（内部先铺一层不透明底）
        val pixels = intArrayOf(0x8000FF00.toInt(), 0x8000FF00.toInt(), 0x8000FF00.toInt(), 0x8000FF00.toInt())
        val image = DesktopImageIo.fromArgb(pixels, 2, 2)
        val bytes = DesktopImageIo.jpegBytes(image, 85)
        assertTrue("应产出非空 JPEG", bytes.size > 0)
        assertEquals(0xFF, bytes[0].toInt() and 0xFF)
        assertEquals(0xD8, bytes[1].toInt() and 0xFF) // JPEG SOI
    }

    @Test
    fun image_io_scale_smooth_and_exact_size() {
        val image = DesktopImageIo.fromArgb(IntArray(16) { 0xFF112233.toInt() }, 4, 4)
        val scaled = DesktopImageIo.scale(image, 2, 2, smooth = true)
        assertEquals(2, scaled.width)
        assertEquals(2, scaled.height)
    }

    @Test
    fun image_io_size_returns_null_for_non_image() {
        val file = File(tempDir(), "not-an-image.txt").apply { writeText("hello") }
        assertEquals(null, DesktopImageIo.size(file.absolutePath))
        assertEquals(null, DesktopImageIo.size(File(tempDir(), "missing.png").absolutePath))
    }

    /** `toPng(bytes, maxDimension)`：图生图/遮罩的底图都要先过这一道（降采样 + 转 PNG）。 */
    @Test
    fun image_io_to_png_downsamples_to_limit() {
        val image = DesktopImageIo.fromArgb(IntArray(64) { 0xFF4488CC.toInt() }, 8, 8)
        val png = DesktopImageIo.pngBytes(image)

        // 限到 4：最长边应落到 4（手机端同一套"最长边不超过 maxDimension"的口径）
        val limited = DesktopImageIo.toPng(png, maxDimension = 4)!!
        assertEquals(4 to 4, DesktopImageIo.size(File(tempDir(), "x.png").let { f ->
            f.writeBytes(limited); f.absolutePath
        }))

        // 限得比原图还大 → 尺寸不变
        val untouched = DesktopImageIo.toPng(png, maxDimension = 4096)!!
        assertEquals(8 to 8, DesktopImageIo.size(File(tempDir(), "y.png").let { f ->
            f.writeBytes(untouched); f.absolutePath
        }))
    }

    /**
     * 非方图：按**最长边**缩到上限，比例不能变（竖图被压成方图就是灾难）。
     *
     * 注：桌面这条路比手机那条**更准** —— 它直接按"最长边不超过 maxDimension"算，
     * 而 Android 的 `inSampleSize` 只能取 2 的幂次、之后还需要补一次滤波缩放
     *（见 `ui/FileImage.kt` 里那段说明）。所以这里是 `(2, 8)` 而不是"留到 16"。
     */
    @Test
    fun image_io_decode_sampled_keeps_aspect_ratio() {
        val image = DesktopImageIo.fromArgb(IntArray(4 * 16) { 0xFF000000.toInt() }, 4, 16)
        val png = DesktopImageIo.pngBytes(image)

        val sampled = DesktopImageIo.decodeSampled(png, maxDimension = 8)!!
        // 4×16 的最长边是 16 → 乘 8/16 = 0.5 → 2×8
        assertEquals(8, maxOf(sampled.width, sampled.height))
        assertEquals(2, sampled.width)
        assertEquals(8, sampled.height)

        // 比例保持 4:16 = 1:4
        assertEquals(0.25f, sampled.width.toFloat() / sampled.height.toFloat(), 0.001f)

        // 原图本来就比上限小 → 原样返回，不放大
        val untouched = DesktopImageIo.decodeSampled(png, maxDimension = 64)!!
        assertEquals(4, untouched.width)
        assertEquals(16, untouched.height)
    }

    /** ref 读写：`openInput`/`openOutput` 就是"引用即路径"的那条通道。 */
    @Test
    fun platform_open_input_output_round_trip() {
        val platform = desktopPlatform()
        val file = File(tempDir(), "sub/dir/blob.bin")

        platform.openOutput(file.absolutePath)!!.use { it.write("你好".toByteArray()) }
        val text = platform.openInput(file.absolutePath)!!.use { String(it.readBytes()) }
        assertEquals("你好", text)

        // 打不开的引用 → null（调用方按失败处理），而不是抛
        assertEquals(null, platform.openInput(File(tempDir(), "missing.bin").absolutePath))
    }

    // -----------------------------------------------------------------------
    // 路径与"另存到用户目录"
    // -----------------------------------------------------------------------

    @Test
    fun app_paths_point_under_appdata() {
        val dir = DesktopAppPaths.filesDir.absolutePath
        assertTrue("数据目录应在用户的 NAI Studio 下：$dir", dir.endsWith("NAI Studio"))
        assertTrue(DesktopAppPaths.defaultImagesDir().absolutePath.endsWith("images"))
        assertTrue(DesktopAppPaths.defaultImagesDir().isDirectory)
    }

    @Test
    fun export_to_user_dir_copies_the_file() {
        val dir = tempDir()
        val source = File(dir, "pic.png").apply { writeBytes(byteArrayOf(9, 9)) }
        val target = File(dir, "out").apply { mkdirs() }

        val platform = desktopPlatform()
        assertTrue(platform.exportToUserDir(source, target.absolutePath))
        assertTrue(File(target, "pic.png").isFile)
        // 目录不存在 → 返回 false，而不是抛
        assertFalse(platform.exportToUserDir(source, File(dir, "nope").absolutePath))
    }

    // -----------------------------------------------------------------------
    // 版本信息（"关于"那一栏读它）
    // -----------------------------------------------------------------------

    @Test
    fun version_fields_are_filled_for_the_about_card() {
        val platform = desktopPlatform()
        assertEquals(DESKTOP_VERSION, platform.appVersion)
        assertEquals("电脑版", platform.edition)
        assertTrue(platform.buildStamp.isNotBlank())
        assertFalse(platform.hostedEdition)
        assertTrue(platform.debugBuild)
    }
}
