package com.kallan.naistudio.services

import com.kallan.naistudio.models.*
import com.kallan.naistudio.platform.*
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class StoragePrivacyTest {
    @get:Rule val temp = TemporaryFolder()
    private val fixtureBase64 = buildString {
        append("iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAACLUlEQVR42t2bsY7DMAxDM3e+uf//leItnQ6J9agOZ3oIiiRt")
        append("ASsSRdLK9fp5V3Poc/w91829uvluNb/91+N6WAQNxNOnmv+ICYAW97XIjvgMUJPqAk9eKQHoUv0uzQumeKUGgNS9mgDdBWPb")
        append("EigD3OiCCgRlywDUwyK7AHX4EFcCaupai9Sn/GH7LkDxQYn13/EAwYU8PfXtW+AKA5wnT0jT1gGophMI3o9B/qcSUCNoBPBh")
        append("e9QnGaABIZLJIbZsg1TyOgSpTg3AMccF0JtQ5BUviOEBGpCgTvLGtMFqaC1deC1ks1K0AKW8XZZUEhMUDERBYCSkaWsmSGTv")
        append("Nx5ihBok/mD325gMoEqQyuUIKUz3BTrF52ZLNAh2zo+gbojjAXRz5Cgx5FjhHfBVWgAo/ye4EOMIET1Qg5KJVYN0Q4RgQUwJ")
        append("EAeYmqSV5gcQwOvQv9KCcUGFN8WHOCpM6G2ZfCAKBB1H2DFIo0qATHkUuK40P2C61++qwwgiRPf6iJcY6wd015zdIqViwGT+")
        append("5whDZCKJ46ZDnCGpDh8Eu0NMF6C7POQ8EgM05PuVFoQLTns6FFmpPIBsfTkSOc4ScxnhMSDoTIKS8lBaBrgz/vWFWIobkyvY")
        append("7hyLfGsi1DE9JyixjtCUzxOvMNYSm3gCSgwAcYDpHNERJeCIJDJYGacGXQs8kgc4i5kowrg2SPq6qxniXphwxuWPoMLOcONk")
        append("MCoaBJ3WGP/OkGuH0bLYNgC/99atothFy9UAAAAASUVORK5CYII=")
    }

    private val fixture: ByteArray
        get() = Base64.getMimeDecoder().decode(fixtureBase64)

    /** 一张最小的合法 PNG（1×1 RGB），没有隐写。 */
    private val plainPng: ByteArray = Base64.getMimeDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
    )


    @Test fun private_save_ignores_shared_folder_and_gallery() = runBlocking {
        val platform = TestPlatform(temp.newFolder())
        val storage = Storage(platform)
        val settings = AppSettings(saveToGallery = false, imageOutputDir = temp.newFolder().path,
            imageOutputTreeUri = "content://shared/tree", keepImageMetadata = false)
        val withText = requireNotNull(PngMetadata.embedText(fixture, "secret-generation-parameters", "Comment"))
        val item = storage.saveImage(withText, GenerateParams(positivePrompt = "private prompt"), 1, settings, emptyList())
        val saved = File(item.filePath).readBytes()
        assertTrue(File(item.filePath).canonicalPath.startsWith(platform.paths.defaultImagesDir().canonicalPath + File.separator))
        assertEquals(0, platform.galleryCopies)
        assertEquals(0, platform.sharedCopies)
        assertTrue(PngMetadata.readTextChunks(saved).isEmpty())
        assertNull(StealthPng.decode(saved))
        assertEquals(1, storage.getHistory().size)
    }

    @Test fun opted_in_copies_use_cleaned_bytes() = runBlocking {
        val platform = TestPlatform(temp.newFolder())
        val storage = Storage(platform)
        storage.saveImage(fixture, GenerateParams(), 1,
            AppSettings(saveToGallery = true, keepImageMetadata = false, imageOutputTreeUri = "content://shared/tree"), emptyList())
        assertEquals(1, platform.galleryCopies)
        assertEquals(1, platform.sharedCopies)
        assertNull(StealthPng.decode(requireNotNull(platform.galleryBytes)))
        assertArrayEquals(platform.galleryBytes, platform.sharedBytes)
    }

    @Test fun keeping_metadata_preserves_original_bytes() = runBlocking {
        val platform = TestPlatform(temp.newFolder())
        val item = Storage(platform).saveImage(fixture, GenerateParams(), 1,
            AppSettings(saveToGallery = false, keepImageMetadata = true), emptyList())
        assertArrayEquals(fixture, File(item.filePath).readBytes())
    }

    @Test fun malformed_png_is_not_silently_saved_with_metadata() {
        val storage = Storage(TestPlatform(temp.newFolder()))
        try { storage.stripPngMetadata(fixture.copyOf(24)); fail("Expected invalid PNG to be rejected") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun new_install_defaults_to_private_storage() { assertFalse(AppSettings().saveToGallery) }

    private class TestPlatform(root: File) : Platform {
        var galleryCopies = 0
        var sharedCopies = 0
        var galleryBytes: ByteArray? = null
        var sharedBytes: ByteArray? = null
        override val paths = object : AppPaths {
            override val filesDir = root
            override fun defaultImagesDir() = File(root, "images").apply { mkdirs() }
        }
        override val gallery = object : GallerySink {
            override fun putImage(file: File): Boolean { galleryCopies++; galleryBytes = file.readBytes(); return true }
        }
        override fun exportToUserDir(source: File, treeUri: String): Boolean {
            sharedCopies++; sharedBytes = source.readBytes(); return true
        }
        override val device = object : DeviceInfo { override val manufacturer = "Test"; override val model = "Test" }
        override val secrets = object : SecretStore {
            override fun getSecret(key: String) = ""
            override fun putSecret(key: String, value: String) { }
            override fun removeSecret(key: String) { }
        }
        override val kv = object : KeyValueStore {
            val values = mutableMapOf<String, Any>()
            override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
            override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
            override fun edit() = object : KeyValueStore.Editor {
                override fun putString(key: String, value: String) = apply { values[key] = value }
                override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
                override fun remove(key: String) = apply { values.remove(key) }
                override fun apply() { }
            }
        }
    }
}
