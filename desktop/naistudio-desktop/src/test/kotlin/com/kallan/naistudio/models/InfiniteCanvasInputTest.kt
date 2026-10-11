package com.kallan.naistudio.models

import org.junit.Assert.*
import org.junit.Test

class InfiniteCanvasInputTest {
    private fun plan() = FocusedInpaint.plan(EditRect(2, 2, 4, 4), 2, 8, 8, autoSizeByRect = true)!!

    @Test fun partialExtensionMasksBlankContextAndFullyGeneratesNewPixels() {
        val original = IntArray(8 * 8) { if (it % 8 < 4) 0xFF123456.toInt() else 0 }
        val input = InfiniteCanvasInput.prepare(original, 8, 8, plan())
        assertEquals(255, input.mask.valueAt(5, 0)) // Empty context beyond frame.
        assertEquals(0, input.mask.valueAt(0, 0)) // Existing context remains protected.
        assertEquals(0, input.mask.valueAt(3, 3)) // Existing image remains reference during extension.
        assertEquals(255, input.mask.valueAt(5, 3)) // Empty image inside selection is generated too.
        assertEquals(0xFFFFFFFF.toInt(), input.pixels[5])
        assertEquals(0xFF123456.toInt(), input.pixels[0])
        assertTrue(input.hasBlankInFrame)
        assertTrue(input.hasReference)
        assertEquals(1.0, input.strength(0.3), 0.0)
    }

    @Test fun internalSelectionPreservesUserStrengthAndContext() {
        val input = InfiniteCanvasInput.prepare(IntArray(64) { 0xFFFFFFFF.toInt() }, 8, 8, plan())
        assertFalse(input.hasBlankInFrame) // Opaque white is actual image, not empty canvas.
        assertEquals(0.3, input.strength(0.3), 0.0)
        assertEquals(0, input.mask.valueAt(0, 0))
        assertEquals(0, input.mask.valueAt(3, 3))
    }

    @Test fun cropBeyondCanvasAndTransparentHolesAreMarkedForGeneration() {
        val p = plan().copy(cropX = -4, cropY = -4)
        val input = InfiniteCanvasInput.prepare(IntArray(64), 8, 8, p)
        assertTrue(input.hasBlankInFrame)
        assertFalse(input.hasReference)
        assertTrue(input.mask.pixels.all { (it.toInt() and 255) == 255 })
        assertTrue(input.pixels.all { it == 0xFFFFFFFF.toInt() })
    }
    @Test fun generatedFramePastesAcrossImageBoundaryWithoutChangingOutsidePixels() {
        val canvas = IntArray(64) { if (it % 8 < 4) 0xFF123456.toInt() else 0 }
        val generated = IntArray(64) { 0xFFABCDEF.toInt() }
        assertNotNull(FocusedInpaint.pasteBack(canvas, 8, 8, generated, plan()))
        assertEquals(0xFFABCDEF.toInt(), canvas[3 * 8 + 3])
        assertEquals(0xFFABCDEF.toInt(), canvas[3 * 8 + 5])
        assertEquals(0xFF123456.toInt(), canvas[0])
        assertEquals(0, canvas[7])
    }

    @Test fun outpaintPastePreservesExistingPixelsInsideFrame() {
        val canvas = IntArray(64) { if (it % 8 < 4) 0xFF123456.toInt() else 0 }
        val generated = IntArray(64) { 0xFFABCDEF.toInt() }
        assertNotNull(FocusedInpaint.pasteBack(canvas, 8, 8, generated, plan(), onlyBlankPixels = true))
        assertEquals(0xFF123456.toInt(), canvas[3 * 8 + 3])
        assertEquals(0xFFABCDEF.toInt(), canvas[3 * 8 + 5])
        assertEquals(0, canvas[7]) // Blank context is not pasted.
    }

    @Test fun frameOutsideImageUsesExistingContextAsReference() {
        val p = plan().copy(frameInCrop = EditRect(4, 2, 4, 4))
        val source = IntArray(64) { if (it % 8 < 4) 0xFF123456.toInt() else 0 }
        val input = InfiniteCanvasInput.prepare(source, 8, 8, p)
        assertTrue(input.hasBlankInFrame)
        assertTrue(input.hasReference)
        assertEquals(0, input.mask.valueAt(3, 3))
        assertEquals(255, input.mask.valueAt(4, 3))
    }

}
