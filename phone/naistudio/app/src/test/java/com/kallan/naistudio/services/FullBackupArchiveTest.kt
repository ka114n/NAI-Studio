package com.kallan.naistudio.services

import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FullBackupArchiveTest {
    private lateinit var root: File
    private val password = "correct horse battery".toCharArray()
    private val manifest = JSONObject().put("format", "test-full-backup")
        .put("formatVersion", 1).put("preferences", JSONObject().put("apiKey", "fake-portable-secret")).toString()

    @Before
    fun createTemporaryDirectory() {
        root = Files.createTempDirectory("full-backup-test-").toFile()
    }

    @After
    fun deleteTemporaryDirectory() {
        root.deleteRecursively()
    }

    @Test
    fun `encrypted multi frame round trip preserves files manifest and caller password`() {
        val source = File(root, "image.bin").apply {
            writeBytes(ByteArray(2 * 1024 * 1024 + 173).also(Random(17)::nextBytes))
        }
        val small = File(root, "settings.json").apply { writeText("{\"theme\":\"dark\"}") }
        val zip = File(root, "snapshot.zip")
        val progress = mutableListOf<Pair<Int, Int>>()
        FullBackupArchive.writeZip(
            zip, manifest, listOf("files/settings.json" to small, "external/gallery/图像.bin" to source),
        ) { completed, total -> progress += completed to total }
        assertEquals(listOf(0 to 2, 1 to 2, 2 to 2), progress)
        val encrypted = encrypt(zip)
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("fake-portable-secret"))
        assertFalse(String(encrypted, Charsets.ISO_8859_1).contains("files/settings.json"))
        val decrypted = File(root, "restored.zip")
        FullBackupArchive.decrypt(ByteArrayInputStream(encrypted), decrypted, password)
        assertArrayEquals(zip.readBytes(), decrypted.readBytes())
        val readback = JSONObject(FullBackupArchive.verifyZip(decrypted))
        val stage = File(root, "stage")
        val extracted = JSONObject(FullBackupArchive.extractVerified(decrypted, stage))
        assertEquals(readback.toString(), extracted.toString())
        assertEquals("fake-portable-secret", extracted.getJSONObject("preferences").getString("apiKey"))
        assertEquals(2, extracted.getJSONArray("files").length())
        assertArrayEquals(source.readBytes(), File(stage, "external/gallery/图像.bin").readBytes())
        assertArrayEquals(small.readBytes(), File(stage, "files/settings.json").readBytes())
        assertFalse(File(stage, "manifest.json").exists())
        assertArrayEquals("correct horse battery".toCharArray(), password)
    }

    @Test
    fun `wrong password exposes no final plaintext and removes scratch file`() {
        val encrypted = encrypt(simpleZip())
        val output = File(root, "restored.zip")
        rejected { FullBackupArchive.decrypt(ByteArrayInputStream(encrypted), output, "wrong password".toCharArray()) }
        assertFalse(output.exists())
        assertNoPlaintextScratchFiles()
    }

    @Test
    fun `authentication failure preserves an existing destination`() {
        val encrypted = encrypt(simpleZip()).apply { this[lastIndex - 21] = (this[lastIndex - 21].toInt() xor 1).toByte() }
        val output = File(root, "restored.zip").apply { writeText("previous verified backup") }
        rejected { FullBackupArchive.decrypt(ByteArrayInputStream(encrypted), output, password) }
        assertEquals("previous verified backup", output.readText())
        assertNoPlaintextScratchFiles()
    }

    @Test
    fun `damage after an authenticated frame still removes all temporary plaintext`() {
        val source = File(root, "random.bin").apply { writeBytes(ByteArray(2 * 1024 * 1024 + 77).also(Random(23)::nextBytes)) }
        val zip = File(root, "large.zip")
        FullBackupArchive.writeZip(zip, manifest, listOf("files/random.bin" to source))
        val encrypted = encrypt(zip)
        val secondFrameCiphertext = 44 + 4 + 1024 * 1024 + 16 + 4
        encrypted[secondFrameCiphertext + 23] = (encrypted[secondFrameCiphertext + 23].toInt() xor 1).toByte()
        val output = File(root, "restored.zip")
        rejected { FullBackupArchive.decrypt(ByteArrayInputStream(encrypted), output, password) }
        assertFalse(output.exists())
        assertNoPlaintextScratchFiles()
    }

    @Test
    fun `header salt is authenticated`() {
        val encrypted = encrypt(simpleZip()).apply { this[16] = (this[16].toInt() xor 1).toByte() }
        val output = File(root, "restored.zip")
        rejected { FullBackupArchive.decrypt(ByteArrayInputStream(encrypted), output, password) }
        assertFalse(output.exists())
    }

    @Test
    fun `unreasonable iteration count is rejected before derivation`() {
        val encrypted = encrypt(simpleZip())
        ByteBuffer.wrap(encrypted).putInt(12, Int.MAX_VALUE)
        rejected { FullBackupArchive.decrypt(ByteArrayInputStream(encrypted), File(root, "restored.zip"), password) }
        assertNoPlaintextScratchFiles()
    }

    @Test
    fun `truncation cannot remove the authenticated terminator`() {
        val encrypted = encrypt(simpleZip())
        val output = File(root, "restored.zip")
        rejected { FullBackupArchive.decrypt(ByteArrayInputStream(encrypted.copyOf(encrypted.size - 20)), output, password) }
        assertFalse(output.exists())
        assertNoPlaintextScratchFiles()
    }

    @Test
    fun `appended bytes are rejected`() {
        val encrypted = encrypt(simpleZip()) + byteArrayOf(7)
        val output = File(root, "restored.zip")
        rejected { FullBackupArchive.decrypt(ByteArrayInputStream(encrypted), output, password) }
        assertFalse(output.exists())
    }

    @Test
    fun `same source and password produces independently salted archives`() {
        val zip = simpleZip()
        val first = encrypt(zip)
        val second = encrypt(zip)
        assertFalse(first.contentEquals(second))
        assertFalse(first.copyOfRange(16, 44).contentEquals(second.copyOfRange(16, 44)))
    }

    @Test
    fun `short password is rejected without creating output`() {
        val zip = simpleZip()
        val encrypted = ByteArrayOutputStream()
        rejected { FullBackupArchive.encrypt(zip, encrypted, "short".toCharArray()) }
        assertEquals(0, encrypted.size())
        val output = File(root, "restored.zip")
        rejected { FullBackupArchive.decrypt(ByteArrayInputStream(byteArrayOf()), output, "short".toCharArray()) }
        assertFalse(output.exists())
    }

    @Test
    fun `caller input and output streams stay open`() {
        val output = object : ByteArrayOutputStream() {
            var wasClosed = false
            override fun close() { wasClosed = true; super.close() }
        }
        FullBackupArchive.encrypt(simpleZip(), output, password)
        assertFalse(output.wasClosed)
        val input = object : ByteArrayInputStream(output.toByteArray()) {
            var wasClosed = false
            override fun close() { wasClosed = true; super.close() }
        }
        FullBackupArchive.decrypt(input, File(root, "restored.zip"), password)
        assertFalse(input.wasClosed)
    }

    @Test
    fun `traversal absolute backslash and drive paths are rejected before extraction`() {
        val unsafe = listOf("files/../escape.txt", "/files/escape.txt", "C:/escape.txt", "files\\escape.txt", "files/a//b", "external/./a")
        unsafe.forEachIndexed { index, path ->
            val zip = rawZip(listOf(path to "bad".toByteArray()), inventory(path to "bad".toByteArray()))
            val stage = File(root, "stage-$index")
            rejected { FullBackupArchive.extractVerified(zip, stage) }
            assertFalse(stage.exists())
        }
        assertFalse(File(root, "escape.txt").exists())
    }

    @Test
    fun `case insensitive duplicate payload names are rejected`() {
        val bytes = "image".toByteArray()
        val zip = rawZip(listOf("files/Gallery/a.png" to bytes, "files/gallery/A.png" to bytes), inventory("files/Gallery/a.png" to bytes, "files/gallery/A.png" to bytes))
        rejected { FullBackupArchive.verifyZip(zip) }
        val stage = File(root, "stage")
        rejected { FullBackupArchive.extractVerified(zip, stage) }
        assertFalse(stage.exists())
    }

    @Test
    fun `duplicate manifest inventory paths are rejected`() {
        val bytes = "image".toByteArray()
        val zip = rawZip(listOf("files/a.png" to bytes), inventory("files/a.png" to bytes, "files/a.png" to bytes))
        rejected { FullBackupArchive.verifyZip(zip) }
    }

    @Test
    fun `file and descendant path conflict is rejected`() {
        val bytes = "image".toByteArray()
        val zip = rawZip(listOf("files/A" to bytes, "files/a/image.png" to bytes), inventory("files/A" to bytes, "files/a/image.png" to bytes))
        rejected { FullBackupArchive.verifyZip(zip) }
    }

    @Test
    fun `missing mandatory manifest is rejected`() {
        val zip = rawZip(listOf("files/a.txt" to "a".toByteArray()), null)
        rejected { FullBackupArchive.verifyZip(zip) }
    }

    @Test
    fun `missing payload listed by manifest is rejected`() {
        val zip = rawZip(emptyList(), inventory("files/missing.png" to "image".toByteArray()))
        rejected { FullBackupArchive.verifyZip(zip) }
    }

    @Test
    fun `unexpected payload absent from manifest is rejected`() {
        val zip = rawZip(listOf("files/extra.png" to "image".toByteArray()), inventory())
        rejected { FullBackupArchive.verifyZip(zip) }
    }

    @Test
    fun `hash mismatch fails and clears already extracted payloads`() {
        val bytes = "image".toByteArray()
        val zip = rawZip(listOf("files/first.png" to bytes, "files/second.png" to bytes), inventory("files/first.png" to bytes, "files/second.png" to "wrong".toByteArray()))
        rejected { FullBackupArchive.verifyZip(zip) }
        val stage = File(root, "stage")
        rejected { FullBackupArchive.extractVerified(zip, stage) }
        assertFalse(stage.exists())
    }

    @Test
    fun `manifest size mismatch is rejected`() {
        val bytes = "image".toByteArray()
        val declaration = JSONObject(inventory("files/a.png" to bytes)).apply { getJSONArray("files").getJSONObject(0).put("size", bytes.size + 1) }
        rejected { FullBackupArchive.verifyZip(rawZip(listOf("files/a.png" to bytes), declaration.toString())) }
    }

    @Test
    fun `oversized declared file is rejected before reading payload`() {
        val declaration = JSONObject(inventory("files/a.png" to byteArrayOf())).apply {
            getJSONArray("files").getJSONObject(0).put("size", FullBackupArchive.MAX_FILE_BYTES + 1)
        }
        rejected { FullBackupArchive.verifyZip(rawZip(listOf("files/a.png" to byteArrayOf()), declaration.toString())) }
    }

    @Test
    fun `nonempty staging directory is preserved`() {
        val stage = File(root, "stage").apply { mkdirs() }
        val existing = File(stage, "keep.txt").apply { writeText("keep") }
        rejected { FullBackupArchive.extractVerified(simpleZip(), stage) }
        assertEquals("keep", existing.readText())
    }

    @Test
    fun `failed export preserves previous archive and cleans temporary file`() {
        val destination = File(root, "snapshot.zip").apply { writeText("previous snapshot") }
        val source = File(root, "source.txt").apply { writeText("initial") }
        rejected {
            FullBackupArchive.writeZip(destination, manifest, listOf("files/source.txt" to source)) { completed, _ ->
                if (completed == 1) throw FullBackupArchive.BackupException("Cancelled")
            }
        }
        assertEquals("previous snapshot", destination.readText())
        assertNoPlaintextScratchFiles()
    }

    @Test
    fun `writer rejects unsafe and duplicate paths`() {
        val source = File(root, "source.txt").apply { writeText("data") }
        rejected { FullBackupArchive.writeZip(File(root, "bad.zip"), manifest, listOf("files/../escape" to source)) }
        rejected { FullBackupArchive.writeZip(File(root, "bad.zip"), manifest, listOf("files/a" to source, "files/A" to source)) }
        assertFalse(File(root, "bad.zip").exists())
    }

    private fun simpleZip(): File {
        val source = File(root, "source.txt").apply { writeText("portable data") }
        return File(root, "snapshot.zip").also {
            FullBackupArchive.writeZip(it, manifest, listOf("files/source.txt" to source))
        }
    }

    private fun encrypt(zip: File): ByteArray = ByteArrayOutputStream().also {
        FullBackupArchive.encrypt(zip, it, password)
    }.toByteArray()

    private fun rawZip(entries: List<Pair<String, ByteArray>>, manifest: String?): File {
        val zip = File(root, "raw-${System.nanoTime()}.zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            entries.forEach { (name, bytes) ->
                output.putNextEntry(ZipEntry(name))
                output.write(bytes)
                output.closeEntry()
            }
            if (manifest != null) {
                output.putNextEntry(ZipEntry("manifest.json"))
                output.write(manifest.toByteArray())
                output.closeEntry()
            }
        }
        return zip
    }

    private fun inventory(vararg entries: Pair<String, ByteArray>): String {
        val inventory = JSONArray()
        entries.forEach { (name, bytes) ->
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
            inventory.put(JSONObject().put("path", name).put("size", bytes.size).put("sha256", hash))
        }
        return JSONObject(manifest).put("files", inventory).toString()
    }

    private fun rejected(block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("Expected BackupException, got $error", error is FullBackupArchive.BackupException)
    }

    private fun assertNoPlaintextScratchFiles() {
        assertFalse(root.listFiles().orEmpty().any { it.name.startsWith(".full-backup-") })
    }
}
