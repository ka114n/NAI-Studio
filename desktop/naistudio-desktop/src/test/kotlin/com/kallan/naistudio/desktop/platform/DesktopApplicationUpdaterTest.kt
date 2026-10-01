package com.kallan.naistudio.desktop.platform
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
class DesktopApplicationUpdaterTest {
    private fun archive(name: String): File { val f=File.createTempFile("nai-update", ".zip"); ZipOutputStream(f.outputStream()).use { it.putNextEntry(ZipEntry(name)); it.write(byteArrayOf(1)); it.closeEntry() }; return f }
    @Test fun rejectsTraversal() { val zip=archive("app/../../escape"); val stage=Files.createTempDirectory("nai-update-stage").toFile(); try { assertTrue(runCatching { DesktopApplicationUpdater.extract(zip,stage) }.isFailure); assertFalse(File(stage.parentFile,"escape").exists()) } finally { zip.delete(); stage.deleteRecursively() } }
    @Test fun rejectsUserDataAndIncompletePackages() { for (name in listOf("prefs.json","app/test.jar")) { val zip=archive(name); val stage=Files.createTempDirectory("nai-update-stage").toFile(); try { assertTrue(runCatching { DesktopApplicationUpdater.extract(zip,stage) }.isFailure) } finally { zip.delete(); stage.deleteRecursively() } } }
}
