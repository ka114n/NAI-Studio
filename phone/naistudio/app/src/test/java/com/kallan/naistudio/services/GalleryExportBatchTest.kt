package com.kallan.naistudio.services

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GalleryExportBatchTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun copiesExactBytesAndRetainsSource() {
        val source = temp.newFile("original.png")
        val bytes = byteArrayOf(0, 1, 2, -1, 42)
        source.writeBytes(bytes)
        val output = ByteArrayOutputStream()
        val result = GalleryExportBatch.copy(listOf(source)) {
            GalleryExportBatch.Target({ output }, { fail("Successful output must remain") })
        }
        assertEquals(GalleryExportBatch.Result(1, 0), result)
        assertArrayEquals(bytes, output.toByteArray())
        assertArrayEquals(bytes, source.readBytes())
    }

    @Test fun nullStreamCleansDestinationAndContinues() {
        val first = temp.newFile("first.png")
        val second = temp.newFile("second.png").apply { writeText("original") }
        var cleaned = false
        val output = ByteArrayOutputStream()
        val result = GalleryExportBatch.copy(listOf(first, second)) {
            if (it == first) GalleryExportBatch.Target({ null }, { cleaned = true })
            else GalleryExportBatch.Target({ output }, {})
        }
        assertTrue(cleaned)
        assertEquals(GalleryExportBatch.Result(1, 1), result)
        assertEquals("original", output.toString("UTF-8"))
        assertTrue(first.exists())
    }

    @Test fun failedWriteRemovesIncompleteCopyAndMissingSourceDoesNotCreateTarget() {
        val source = temp.newFile("source.png").apply { writeText("image") }
        val missing = java.io.File(temp.root, "missing.png")
        var created = 0
        var cleaned = 0
        val result = GalleryExportBatch.copy(listOf(source, missing)) {
            created++
            GalleryExportBatch.Target({ object : OutputStream() {
                override fun write(value: Int) { throw IOException("Full destination") }
            } }, { cleaned++ })
        }
        assertEquals(GalleryExportBatch.Result(0, 2), result)
        assertEquals(1, created)
        assertEquals(1, cleaned)
        assertEquals("image", source.readText())
    }
}
