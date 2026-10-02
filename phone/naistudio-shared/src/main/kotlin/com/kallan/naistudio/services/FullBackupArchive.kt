package com.kallan.naistudio.services

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable, password-encrypted archive for the full device snapshot.
 *
 * Container v1 (big endian): 8-byte ASCII magic, int version, int PBKDF2 iterations,
 * 16-byte salt, 12-byte base nonce, then frames [int plaintext size, ciphertext + 16-byte tag].
 * PBKDF2-HMAC-SHA256 derives an AES-256 key. Every AES-GCM frame authenticates the complete
 * header, its zero-based long index, and its plaintext size. The nonce is the base nonce with
 * the frame index XORed into its final eight bytes. A zero-size authenticated frame terminates
 * the container; missing termination, appended bytes, reordered frames, or any mutation fail.
 * Payload frames are 1 MiB except for the last one, bounding both memory and cipher invocation count.
 *
 * Bounded frames matter: some JVM GCM providers buffer all input until authentication. One
 * cipher for an entire gallery would therefore use gallery-sized memory. Here each cipher sees
 * at most 1 MiB. Plaintext is written only into a private temporary file until all frames verify.
 * Neither encrypt nor decrypt closes the caller's stream or modifies its password array.
 */
object FullBackupArchive {
    const val MIN_PASSWORD_LENGTH = 8
    const val PBKDF2_ITERATIONS = 210_000
    const val MAX_FILE_BYTES = 2L * 1024 * 1024 * 1024
    const val MAX_TOTAL_BYTES = 20L * 1024 * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 16 * 1024 * 1024
    const val MAX_ENTRIES = 100_000

    private val magic = "NAIFBK01".toByteArray(StandardCharsets.US_ASCII)
    private const val VERSION = 1
    private const val MAX_PBKDF2_ITERATIONS = 1_000_000
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val HEADER_BYTES = 8 + 4 + 4 + SALT_BYTES + NONCE_BYTES
    private const val FRAME_BYTES = 1024 * 1024
    private const val COPY_BUFFER_BYTES = 64 * 1024
    private const val MAX_PATH_LENGTH = 4096
    private const val MANIFEST_PATH = "manifest.json"
    private const val MAX_ZIP_BYTES = MAX_TOTAL_BYTES + MAX_MANIFEST_BYTES + MAX_ENTRIES.toLong() * MAX_PATH_LENGTH * 2
    private val random = SecureRandom()
    private val shaPattern = Regex("[0-9a-f]{64}")
    private val reservedWindowsName = Regex("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?")

    class BackupException(message: String, cause: Throwable? = null) : IOException(message, cause)

    fun encrypt(zip: File, output: OutputStream, password: CharArray) {
        validatePassword(password)
        if (!zip.isFile || zip.length() > MAX_ZIP_BYTES) throw BackupException("Invalid backup archive size")
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(magic).putInt(VERSION).putInt(PBKDF2_ITERATIONS).put(salt).put(nonce).array()
        val key = deriveKey(password, salt, PBKDF2_ITERATIONS)
        val plain = ByteArray(FRAME_BYTES)
        try {
            val destination = DataOutputStream(output)
            destination.write(header)
            zip.inputStream().buffered(COPY_BUFFER_BYTES).use { source ->
                var index = 0L
                var total = 0L
                while (true) {
                    val count = fillFrame(source, plain)
                    total += count
                    if (total > MAX_ZIP_BYTES) throw BackupException("Backup archive exceeds the size limit")
                    destination.writeInt(count)
                    destination.write(frameCipher(Cipher.ENCRYPT_MODE, key, nonce, header, index, count).doFinal(plain, 0, count))
                    if (count == 0) break
                    index++
                }
                if (total != zip.length()) throw BackupException("Backup archive changed while encrypting")
            }
            destination.flush()
        } catch (error: GeneralSecurityException) {
            throw BackupException("Unable to encrypt backup", error)
        } finally {
            key.fill(0)
            plain.fill(0)
        }
    }

    /** The final [zip] is replaced only after complete authentication; failure removes the temporary plaintext. */
    fun decrypt(input: InputStream, zip: File, password: CharArray) {
        validatePassword(password)
        val source = DataInputStream(input)
        val header = ByteArray(HEADER_BYTES)
        try {
            source.readFully(header)
        } catch (error: EOFException) {
            throw BackupException("Truncated encrypted backup", error)
        }
        val fields = ByteBuffer.wrap(header)
        val readMagic = ByteArray(magic.size).also(fields::get)
        if (!readMagic.contentEquals(magic) || fields.int != VERSION) throw BackupException("Unsupported encrypted backup format")
        val iterations = fields.int
        if (iterations !in PBKDF2_ITERATIONS..MAX_PBKDF2_ITERATIONS) throw BackupException("Invalid backup key derivation parameters")
        val salt = ByteArray(SALT_BYTES).also(fields::get)
        val nonce = ByteArray(NONCE_BYTES).also(fields::get)
        val key = deriveKey(password, salt, iterations)
        var temporary: File? = null
        try {
            val scratch = createSiblingTemporary(zip)
            temporary = scratch
            scratch.outputStream().buffered(COPY_BUFFER_BYTES).use { destination ->
                var index = 0L
                var total = 0L
                var sawShortFrame = false
                while (true) {
                    val count = source.readInt()
                    if (count !in 0..FRAME_BYTES) throw BackupException("Invalid encrypted backup frame")
                    if (sawShortFrame && count != 0) throw BackupException("Invalid encrypted backup frame sequence")
                    if (count in 1 until FRAME_BYTES) sawShortFrame = true
                    total += count
                    if (total > MAX_ZIP_BYTES) throw BackupException("Backup archive exceeds the size limit")
                    val ciphertext = ByteArray(count + TAG_BYTES)
                    source.readFully(ciphertext)
                    val plaintext = frameCipher(Cipher.DECRYPT_MODE, key, nonce, header, index, count).doFinal(ciphertext)
                    try {
                        if (plaintext.size != count) throw BackupException("Invalid encrypted backup frame")
                        destination.write(plaintext)
                    } finally {
                        plaintext.fill(0)
                    }
                    if (count == 0) {
                        if (source.read() != -1) throw BackupException("Unexpected data after encrypted backup")
                        break
                    }
                    index++
                }
            }
            replaceFile(scratch, zip)
            temporary = null
        } catch (error: GeneralSecurityException) {
            // Do not reveal whether the password or any particular archive byte was incorrect.
            throw BackupException("Incorrect password or damaged backup", error)
        } catch (error: EOFException) {
            throw BackupException("Truncated encrypted backup", error)
        } finally {
            key.fill(0)
            temporary?.delete()
        }
    }

    /**
     * Writes an atomic ZIP snapshot, augmenting the caller's manifest with files[{path,size,sha256}].
     * Hashes and lengths are measured from exactly the bytes copied, rather than from an earlier read.
     * A source changing length aborts export. [progress] receives (completed files, total files).
     */
    fun writeZip(
        destination: File,
        manifest: String,
        files: List<Pair<String, File>>,
        progress: (Int, Int) -> Unit = { _, _ -> },
    ) {
        if (files.size >= MAX_ENTRIES) throw BackupException("Too many backup files")
        val manifestObject = parseManifestObject(manifest)
        val names = files.map { it.first }
        validateNames(names)
        files.forEach { (_, file) ->
            if (!file.isFile || file.length() > MAX_FILE_BYTES) throw BackupException("Invalid backup source file")
        }
        val temporary = createSiblingTemporary(destination)
        try {
            val inventory = JSONArray()
            var total = 0L
            ZipOutputStream(temporary.outputStream().buffered(COPY_BUFFER_BYTES)).use { archive ->
                progress(0, files.size)
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                files.forEachIndexed { index, (name, file) ->
                    val sourceSize = file.length()
                    val hash = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    archive.putNextEntry(ZipEntry(name).apply { time = file.lastModified() })
                    file.inputStream().buffered(COPY_BUFFER_BYTES).use { source ->
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            size += count
                            total += count
                            if (size > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES) throw BackupException("Backup files exceed the size limit")
                            archive.write(buffer, 0, count)
                            hash.update(buffer, 0, count)
                        }
                    }
                    archive.closeEntry()
                    if (size != sourceSize || file.length() != sourceSize) throw BackupException("Backup source file changed while exporting")
                    inventory.put(JSONObject().put("path", name).put("size", size).put("sha256", hex(hash.digest())))
                    progress(index + 1, files.size)
                }
                val bytes = manifestObject.put("files", inventory).toString().toByteArray(StandardCharsets.UTF_8)
                if (bytes.size > MAX_MANIFEST_BYTES) throw BackupException("Backup manifest exceeds the size limit")
                archive.putNextEntry(ZipEntry(MANIFEST_PATH))
                archive.write(bytes)
                archive.closeEntry()
            }
            if (temporary.length() > MAX_ZIP_BYTES) throw BackupException("Backup archive exceeds the size limit")
            replaceFile(temporary, destination)
        } finally {
            temporary.delete()
        }
    }

    /**
     * Validates inventory and streams verified payloads into an empty [stage]. Returns manifest JSON
     * for the caller to validate its application schema before applying any file to live storage.
     * manifest.json is returned, not extracted. A failure clears only this owned, initially empty stage.
     */
    fun extractVerified(zip: File, stage: File): String {
        if (stage.exists() && (!stage.isDirectory || stage.list()?.isEmpty() != true)) throw BackupException("Backup staging directory must be empty")
        if (!stage.exists() && !stage.mkdirs()) throw BackupException("Unable to create backup staging directory")
        val root = stage.canonicalFile
        try {
            return verifyZipContents(zip, root)
        } catch (error: Exception) {
            stage.deleteRecursively()
            if (error is BackupException) throw error
            throw BackupException("Unable to verify backup archive", error)
        }
    }

    /** Reads and checks every payload byte without writing extracted copies; useful for export readback. */
    fun verifyZip(zip: File): String = try {
        verifyZipContents(zip, null)
    } catch (error: Exception) {
        if (error is BackupException) throw error
        throw BackupException("Unable to verify backup archive", error)
    }

    private fun verifyZipContents(zip: File, root: File?): String {
        if (!zip.isFile || zip.length() > MAX_ZIP_BYTES) throw BackupException("Invalid backup archive size")
        return ZipFile(zip).use { archive ->
                val entries = LinkedHashMap<String, ZipEntry>()
                val iterator = archive.entries()
                while (iterator.hasMoreElements()) {
                    val entry = iterator.nextElement()
                    if (entries.size >= MAX_ENTRIES) throw BackupException("Too many backup entries")
                    if (entry.isDirectory || entries.put(entry.name, entry) != null) throw BackupException("Invalid or duplicate backup entry")
                }
                val manifestEntry = entries[MANIFEST_PATH] ?: throw BackupException("Backup manifest is missing")
                if (manifestEntry.size !in 0..MAX_MANIFEST_BYTES.toLong()) throw BackupException("Invalid backup manifest size")
                val names = entries.keys.filter { it != MANIFEST_PATH }
                validateNames(names)
                val manifest = archive.getInputStream(manifestEntry).use { readManifest(it) }
                val inventory = readInventory(parseManifestObject(manifest))
                if (entries.keys != inventory.keys + MANIFEST_PATH) throw BackupException("Backup file inventory does not match archive")
                var declaredTotal = 0L
                inventory.forEach { (name, expected) ->
                    val entry = entries.getValue(name)
                    if (entry.size != expected.size) throw BackupException("Backup file size does not match manifest")
                    declaredTotal += expected.size
                    if (declaredTotal > MAX_TOTAL_BYTES) throw BackupException("Backup files exceed the size limit")
                    if (root != null) containedFile(root, name)
                }
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                var actualTotal = 0L
                inventory.forEach { (name, expected) ->
                    val target = root?.let { containedFile(it, name) }
                    if (target != null) {
                        if (!target.parentFile.isDirectory && !target.parentFile.mkdirs()) throw BackupException("Unable to stage backup file")
                        if (target.exists()) throw BackupException("Backup file destination already exists")
                    }
                    val hash = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    archive.getInputStream(entries.getValue(name)).use { source ->
                        target?.outputStream()?.buffered(COPY_BUFFER_BYTES).use { output ->
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                size += count
                                actualTotal += count
                                if (size > expected.size || actualTotal > MAX_TOTAL_BYTES) throw BackupException("Backup file exceeds its declared size")
                                output?.write(buffer, 0, count)
                                hash.update(buffer, 0, count)
                            }
                        }
                    }
                    if (size != expected.size || hex(hash.digest()) != expected.sha256) throw BackupException("Backup file integrity check failed")
                }
                manifest
        }
    }

    private data class InventoryFile(val size: Long, val sha256: String)

    private fun readInventory(manifest: JSONObject): LinkedHashMap<String, InventoryFile> {
        val array = manifest.opt("files") as? JSONArray ?: throw BackupException("Backup manifest file inventory is missing")
        if (array.length() >= MAX_ENTRIES) throw BackupException("Too many backup files")
        val result = LinkedHashMap<String, InventoryFile>()
        for (index in 0 until array.length()) {
            val item = array.opt(index) as? JSONObject ?: throw BackupException("Invalid backup file inventory")
            val name = item.opt("path") as? String ?: throw BackupException("Invalid backup file path")
            val sizeValue = item.opt("size") as? Number ?: throw BackupException("Invalid backup file size")
            val size = sizeValue.toString().toLongOrNull() ?: throw BackupException("Invalid backup file size")
            val hash = item.opt("sha256") as? String ?: throw BackupException("Invalid backup file checksum")
            if (size !in 0..MAX_FILE_BYTES || !shaPattern.matches(hash)) throw BackupException("Invalid backup file integrity metadata")
            if (result.put(name, InventoryFile(size, hash)) != null) throw BackupException("Duplicate backup file inventory")
        }
        validateNames(result.keys.toList())
        return result
    }

    private fun validateNames(names: List<String>) {
        val foldedNames = HashSet<String>()
        names.forEach { name ->
            if (name.length > MAX_PATH_LENGTH || !(name.startsWith("files/") || name.startsWith("external/")) ||
                name.any { it == '\\' || it == ':' || it.code < 32 || it.code == 127 }
            ) throw BackupException("Unsafe backup file path")
            val segments = name.split('/')
            if (segments.any { it.isEmpty() || it == "." || it == ".." || it.endsWith('.') || it.endsWith(' ') || reservedWindowsName.matches(it) }) {
                throw BackupException("Unsafe backup file path")
            }
            if (!foldedNames.add(name.lowercase(Locale.ROOT))) throw BackupException("Duplicate backup file path")
        }
        foldedNames.forEach { name ->
            var separator = name.indexOf('/')
            while (separator >= 0) {
                if (name.substring(0, separator) in foldedNames) throw BackupException("Conflicting backup file paths")
                separator = name.indexOf('/', separator + 1)
            }
        }
    }

    private fun containedFile(root: File, name: String): File {
        val target = File(root, name).canonicalFile
        if (!target.path.startsWith(root.path + File.separator)) throw BackupException("Backup file path escapes staging directory")
        return target
    }

    private fun parseManifestObject(manifest: String): JSONObject {
        if (manifest.length > MAX_MANIFEST_BYTES || manifest.toByteArray(StandardCharsets.UTF_8).size > MAX_MANIFEST_BYTES) {
            throw BackupException("Backup manifest exceeds the size limit")
        }
        return try {
            JSONObject(manifest)
        } catch (error: Exception) {
            throw BackupException("Invalid backup manifest", error)
        }
    }

    private fun readManifest(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (output.size().toLong() + count > MAX_MANIFEST_BYTES) throw BackupException("Backup manifest exceeds the size limit")
            output.write(buffer, 0, count)
        }
        // A malformed UTF-8 manifest must not silently gain replacement characters in file paths.
        return try {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(output.toByteArray())).toString()
        } catch (error: Exception) {
            throw BackupException("Invalid backup manifest encoding", error)
        }
    }

    private fun validatePassword(password: CharArray) {
        if (password.size < MIN_PASSWORD_LENGTH) throw BackupException("Backup password must contain at least $MIN_PASSWORD_LENGTH characters")
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } catch (error: GeneralSecurityException) {
            throw BackupException("Backup encryption is unavailable", error)
        } finally {
            spec.clearPassword()
        }
    }

    private fun frameCipher(mode: Int, key: ByteArray, baseNonce: ByteArray, header: ByteArray, index: Long, count: Int): Cipher {
        val nonce = baseNonce.copyOf()
        for (offset in 0 until 8) nonce[nonce.lastIndex - offset] = (nonce[nonce.lastIndex - offset].toInt() xor (index ushr (offset * 8)).toInt()).toByte()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
            updateAAD(header)
            updateAAD(ByteBuffer.allocate(12).putLong(index).putInt(count).array())
        }
    }

    private fun fillFrame(input: InputStream, buffer: ByteArray): Int {
        var count = 0
        while (count < buffer.size) {
            val read = input.read(buffer, count, buffer.size - count)
            if (read < 0) break
            if (read == 0) {
                val single = input.read()
                if (single < 0) break
                buffer[count++] = single.toByte()
            } else {
                count += read
            }
        }
        return count
    }

    private fun createSiblingTemporary(destination: File): File {
        val parent = destination.absoluteFile.parentFile ?: throw BackupException("Invalid backup destination")
        if (!parent.isDirectory && !parent.mkdirs()) throw BackupException("Unable to create backup destination directory")
        return File.createTempFile(".full-backup-", ".tmp", parent)
    }

    private fun replaceFile(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
