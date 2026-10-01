package com.kallan.naistudio.services
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class ApplicationUpdatesTest {
    private fun release(digest: String = "a".repeat(64), url: String = "https://github.com/ka114n/NAI-Studio/releases/download/v1.1.143/NAI-Studio-Windows-1.1.143.zip", extra: String = ""): String = """{"tag_name":"v1.1.143",$extra"assets":[{"name":"NAI-Studio-Windows-1.1.143.zip","state":"uploaded","size":3,"digest":"sha256:$digest","browser_download_url":"$url"}]}"""
    @Test fun numericVersions() { assertTrue(ApplicationUpdates.newer("1.1.143", "1.1.9")); assertFalse(ApplicationUpdates.newer("1.1.142", "1.1.142")); assertFalse(ApplicationUpdates.newer("1.1.143-beta", "1.1.142")); assertFalse(ApplicationUpdates.newer("1.0.999", "1.1.1")) }
    @Test fun onlyStableAndNew() { assertNull(ApplicationUpdates.parse(release(extra="\"prerelease\":true,"), "Windows", "1.1.142")); assertNull(ApplicationUpdates.parse(release(), "Windows", "1.1.143")); assertEquals("1.1.143", ApplicationUpdates.parse(release(), "Windows", "1.1.142")!!.version) }
    @Test(expected=IllegalArgumentException::class) fun rejectsMissingDigest() { ApplicationUpdates.parse(release(digest=""), "Windows", "1.1.142") }
    @Test(expected=IllegalArgumentException::class) fun rejectsForeignDownload() { ApplicationUpdates.parse(release(url="https://example.com/update.zip"), "Windows", "1.1.142") }
    @Test fun rejectsAlteredAndTruncatedFiles() {
        val file=File.createTempFile("nai-update", ".zip")
        try {
            file.writeBytes(byteArrayOf(1,2,3))
            val digest=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
            val item=ApplicationUpdates.parse(release(digest=digest), "Windows", "1.1.142")!!
            ApplicationUpdates.verify(file,item)
            file.writeBytes(byteArrayOf(1,2,4)); assertTrue(runCatching { ApplicationUpdates.verify(file,item) }.isFailure)
            file.writeBytes(byteArrayOf(1)); assertTrue(runCatching { ApplicationUpdates.verify(file,item) }.isFailure)
        } finally { file.delete() }
    }
}
