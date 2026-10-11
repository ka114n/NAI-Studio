package com.kallan.naistudio.desktop.platform

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real bootstrap/inference test; normal test runs never download a multi-GB model. */
class LocalImageTaggerIntegrationTest {
    @Test fun bootstrapAndTagWithoutSystemPythonOrApi() = runBlocking {
        assumeTrue(System.getenv("NAI_TEST_LOCAL_TAGGER") == "1")
        val picture = File.createTempFile("nai-tagger-", ".png")
        val image = BufferedImage(512, 512, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().let { g ->
            g.color = Color.WHITE; g.fillRect(0, 0, 512, 512)
            g.color = Color.BLACK; g.fillOval(80, 30, 352, 420)
            g.color = Color(255, 220, 190); g.fillOval(112, 90, 288, 300)
            g.color = Color.BLUE; g.fillOval(168, 210, 32, 24); g.fillOval(304, 210, 32, 24)
            g.color = Color.RED; g.drawArc(210, 270, 90, 50, 180, 180)
            g.dispose()
        }
        ImageIO.write(image, "png", picture)
        val tagger = DesktopLocalImageTagger()
        try {
            val firstStages = mutableListOf<String>()
            val first = tagger.infer(picture.absolutePath) { firstStages += it; println("TAGGER STAGE: $it") }
            assertTrue("Worker must confirm a CUDA model", firstStages.contains("gpu"))
            assertTrue(first.isNotBlank())
            val warmStages = mutableListOf<String>()
            val second = tagger.infer(picture.absolutePath) { warmStages += it }
            assertEquals(first, second)
            assertFalse(warmStages.contains("model"))
            assertFalse(warmStages.contains("loading"))
            val code = "g".repeat(43)
            val server = TaggerHttpServer(code, tagger, { 512 to 512 }, 0)
            server.start(5000, true)
            try {
                val remote = com.kallan.naistudio.services.RemoteTaggerClient(true)
                assertEquals(first, remote.request("http://127.0.0.1:${server.listeningPort}", code, picture))
            } finally { server.stop() }
        } finally { tagger.close(); picture.delete() }
    }

    @Test fun nvidiaVfxUpscalesStillImageAndReturnsItThroughPairingHttp() = runBlocking {
        assumeTrue(System.getenv("NAI_TEST_NVIDIA_VFX") == "1")
        val picture = File.createTempFile("nai-vfx-", ".png")
        val image = BufferedImage(640, 512, BufferedImage.TYPE_INT_ARGB)
        image.createGraphics().let { g ->
            g.color = Color(38, 106, 218, 255); g.fillRect(0, 0, 640, 512)
            g.color = Color(248, 214, 78, 255); g.fillOval(120, 80, 400, 350)
            g.color = Color.BLACK; g.fillOval(220, 210, 28, 20); g.fillOval(392, 210, 28, 20)
            g.drawArc(275, 275, 90, 55, 180, 180)
            g.dispose()
        }
        ImageIO.write(image, "png", picture)
        val pressure = java.util.concurrent.atomic.AtomicLong(0)
        val tagger = DesktopLocalImageTagger(gpuMemory = object : GpuMemoryUsage {
            override fun usedBytes() = pressure.get()
        })
        try {
            val withTagger = System.getenv("NAI_TEST_LOCAL_TAGGER") == "1"
            if (withTagger) assertTrue(tagger.infer(picture.absolutePath) {}.isNotBlank())
            NvidiaGpuMemory().use { memory -> assertTrue("NVML must read real driver memory", (memory.usedBytes() ?: 0) > 0) }
            val firstStarted = System.nanoTime()
            val output = tagger.upscale(picture.absolutePath)
            println("RTX cold request ms=" + (System.nanoTime() - firstStarted) / 1_000_000)
            val residentIds = tagger.residentWorkerIds()
            assertEquals(if (withTagger) 2 else 1, residentIds.size)
            val local = ImageIO.read(java.io.ByteArrayInputStream(output))
            assertEquals(1280, local.width)
            assertEquals(1024, local.height)
            assertEquals(4, local.colorModel.numComponents)

            val code = "v".repeat(43)
            val server = TaggerHttpServer(code, tagger, { 640 to 512 }, 0)
            server.start(5000, true)
            try {
                val request = Request.Builder().url("http://127.0.0.1:${server.listeningPort}/v1/upscale")
                    .header("Authorization", "Bearer $code")
                    .post(picture.readBytes().toRequestBody("image/png".toMediaType())).build()
                val warmStarted = System.nanoTime()
                OkHttpClient().newCall(request).execute().use { response ->
                    assertEquals(200, response.code)
                    val returned = ImageIO.read(response.body!!.byteStream())
                    assertEquals(1280, returned.width)
                    assertEquals(1024, returned.height)
                }
                println("RTX warm HTTP request ms=" + (System.nanoTime() - warmStarted) / 1_000_000)
                assertEquals("Second request must reuse the RTX process", residentIds, tagger.residentWorkerIds())
            } finally { server.stop() }
            // Changing dimensions rebuilds the effect inside the same CUDA process.
            ImageIO.write(BufferedImage(320, 256, BufferedImage.TYPE_INT_RGB), "png", picture)
            val resized = ImageIO.read(java.io.ByteArrayInputStream(tagger.upscale(picture.absolutePath)))
            assertEquals(640, resized.width)
            assertEquals(512, resized.height)
            assertEquals(residentIds, tagger.residentWorkerIds())
            pressure.set(7L * 1024 * 1024 * 1024)
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8)
            while (tagger.residentWorkerIds().isNotEmpty() && System.nanoTime() < deadline) kotlinx.coroutines.delay(100)
            assertTrue("Qualified pressure must release the resident RTX process", tagger.residentWorkerIds().isEmpty())
            for (id in residentIds) {
                val handle = ProcessHandle.of(id)
                if (handle.isPresent) handle.get().onExit().get(5, java.util.concurrent.TimeUnit.SECONDS)
            }
            pressure.set(0)
            tagger.upscale(picture.absolutePath)
            assertNotEquals("Next request must recreate a released worker", residentIds, tagger.residentWorkerIds())
        } finally { tagger.close(); picture.delete() }
    }

}
