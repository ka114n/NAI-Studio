package com.kallan.naistudio.services

import com.kallan.naistudio.models.*
import org.junit.Assert.*
import org.junit.Test

class InfiniteCanvasBlendTest {
    private val plan = FocusedInpaint.plan(EditRect(0, 0, 256, 128), 0, 256, 128, autoSizeByRect = true)!!
    private fun input() = InfiniteCanvasInput.prepare(
        IntArray(256 * 128) { if (it % 256 < 128) 0xFF0000FF.toInt() else 0 }, 256, 128, plan,
    )

    @Test fun softEdgesBlendOriginalAndNewPixelsNeverFadeToWhite() {
        val input = input()
        val mask = MaskExpand.dilate(input.mask, 7)
        val alpha = input.completeBlankAlpha(InpaintComposite.blendAlpha(mask, 20, true))
        val output = InpaintComposite.composite(IntArray(input.pixels.size) { 0xFFFF0000.toInt() }, input.pixels, alpha)
        assertEquals(0xFF0000FF.toInt(), output[64 * 256])
        assertNotEquals(0xFF0000FF.toInt(), output[64 * 256 + 95])
        assertNotEquals(0xFFFF0000.toInt(), output[64 * 256 + 95])
        assertEquals(0xFFFF0000.toInt(), output[64 * 256 + 128])
        assertEquals(0xFFFF0000.toInt(), output.last())
    }

    @Test fun disablingEdgeProtectionUsesHardExpandedMask() {
        val input = input()
        val mask = MaskExpand.dilate(input.mask, 7)
        val alpha = input.completeBlankAlpha(InpaintComposite.blendAlpha(mask, 20, false))
        assertEquals(0, alpha[64 * 256 + 120].toInt() and 255)
        assertEquals(255, alpha[64 * 256 + 121].toInt() and 255)
        assertEquals(255, alpha[64 * 256 + 128].toInt() and 255)
    }

    @Test fun zeroExpansionPreservesOriginalWithHardEdges() {
        val input = input()
        val alpha = input.completeBlankAlpha(InpaintComposite.blendAlpha(MaskExpand.dilate(input.mask, 0), 20, false))
        val output = InpaintComposite.composite(IntArray(input.pixels.size) { 0xFFFF0000.toInt() }, input.pixels, alpha)
        assertEquals(0xFF0000FF.toInt(), output[64 * 256 + 127])
        assertEquals(0xFFFF0000.toInt(), output[64 * 256 + 128])
    }
    @Test fun expandedRequestMaskCanIntrudeIntoOpaqueWhiteReference() {
        val crop = input()
        val transformed = MaskCodec.inpaintMask(MaskExpand.dilate(crop.mask, 7), 256, 128)!!
        val mask = crop.completeRequestMask(transformed)
        assertEquals(0, mask.valueAt(64, 64))
        assertEquals(255, mask.valueAt(127, 64))
        assertEquals(255, mask.valueAt(128, 64))
    }

    @Test fun emptySliverSurvivesLatentMaskAlignment() {
        val source = IntArray(256 * 128) { 0xFF123456.toInt() }
        for (y in 0 until 128) source[y * 256 + 127] = 0
        val crop = InfiniteCanvasInput.prepare(source, 256, 128, plan)
        val emptyCandidate = PixelMask(256, 128, ByteArray(256 * 128))
        val protected = crop.completeRequestMask(emptyCandidate)
        assertEquals(255, protected.valueAt(127, 64))
        assertEquals(0, protected.valueAt(126, 64))
    }

}
