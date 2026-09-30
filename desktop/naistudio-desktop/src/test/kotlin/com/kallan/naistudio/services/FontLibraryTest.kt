package com.kallan.naistudio.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 字体库里的**纯逻辑**钉子（目录/复制那种要真文件的，留给后面接线时手测 ✓）。
 * 用户 2026-09-26：「可以导入字体」← 这几条保证"认哪些格式、显示什么名字、别覆盖同名"✓。
 */
class FontLibraryTest {

    @Test
    fun recognises_the_three_supported_formats_only() {
        assertTrue(FontLibrary.isSupported("思源黑体.ttf"))
        assertTrue(FontLibrary.isSupported("SourceHan.OTF"))   // 大小写不敏感 ✓
        assertTrue(FontLibrary.isSupported("collection.ttc"))
        assertFalse(FontLibrary.isSupported("readme.txt"))
        assertFalse(FontLibrary.isSupported("noext"))
        assertFalse(FontLibrary.isSupported("font.ttf.bak"))
    }

    @Test
    fun display_name_is_file_name_without_extension() {
        assertEquals("思源黑体", FontLibrary.displayNameOf("思源黑体.ttf"))
        assertEquals("SourceHan", FontLibrary.displayNameOf("SourceHan.otf"))
        assertEquals("noext", FontLibrary.displayNameOf("noext"))
    }

    @Test
    fun sanitize_strips_directories_and_bad_characters() {
        assertEquals("font.ttf", FontLibrary.sanitize("C:\\Users\\me\\Desktop\\font.ttf"))
        assertEquals("font.ttf", FontLibrary.sanitize("/home/me/font.ttf"))
        assertEquals("a_b.ttf", FontLibrary.sanitize("a:b.ttf"))
        assertEquals("font.ttf", FontLibrary.sanitize("   "))
    }

    @Test
    fun unique_target_never_overwrites() {
        val dir = File(System.getProperty("java.io.tmpdir"), "nai-font-test-${System.nanoTime()}")
        dir.mkdirs()
        try {
            assertEquals("MyFont.ttf", FontLibrary.uniqueTarget(dir, "MyFont.ttf").name)
            File(dir, "MyFont.ttf").writeText("x")
            assertEquals("MyFont-2.ttf", FontLibrary.uniqueTarget(dir, "MyFont.ttf").name)
            File(dir, "MyFont-2.ttf").writeText("x")
            assertEquals("MyFont-3.ttf", FontLibrary.uniqueTarget(dir, "MyFont.ttf").name)
        } finally {
            dir.deleteRecursively()
        }
    }
}
