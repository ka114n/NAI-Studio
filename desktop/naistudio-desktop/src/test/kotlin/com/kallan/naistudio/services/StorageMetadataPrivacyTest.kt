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

class StorageMetadataPrivacyTest {
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


    @Test fun save_without_metadata_clears_text_and_stealth() = runBlocking {
        val storage = Storage(com.kallan.naistudio.desktop.platform.desktopPlatform())
        val withText = requireNotNull(PngMetadata.embedText(fixture, "secret-parameters", "Comment"))
        val item = storage.saveImage(withText, GenerateParams(), 1,
            AppSettings(saveToGallery = false, keepImageMetadata = false, imageOutputDir = temp.newFolder().path), emptyList())
        val saved = File(item.filePath).readBytes()
        assertTrue(PngMetadata.readTextChunks(saved).isEmpty())
        assertNull(StealthPng.decode(saved))
        val image = javax.imageio.ImageIO.read(File(item.filePath))
        assertEquals(64, image.width)
        assertEquals(64, image.height)
        val before = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(fixture))
        for (y in 0 until 64) for (x in 0 until 64) {
            assertEquals(before.getRGB(x, y) and 0x00ffffff, image.getRGB(x, y) and 0x00ffffff)
            val a0 = before.getRGB(x, y) ushr 24
            val a1 = image.getRGB(x, y) ushr 24
            assertTrue(kotlin.math.abs(a0 - a1) <= 1)
        }
    }
    @Test fun keep_metadata_keeps_original() = runBlocking {
        val storage = Storage(com.kallan.naistudio.desktop.platform.desktopPlatform())
        val item = storage.saveImage(fixture, GenerateParams(), 1,
            AppSettings(saveToGallery = false, keepImageMetadata = true, imageOutputDir = temp.newFolder().path), emptyList())
        assertArrayEquals(fixture, File(item.filePath).readBytes())
    }
    @Test fun truncated_png_is_rejected() {
        val storage = Storage(com.kallan.naistudio.desktop.platform.desktopPlatform())
        try { storage.stripPngMetadata(fixture.copyOf(24)); fail("Expected rejection") }
        catch (_: IllegalArgumentException) { }
    }
}
