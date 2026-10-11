package com.kallan.naistudio.models

import org.junit.Assert.*
import org.junit.Test

class InfiniteFrameSizesTest {
    @Test fun presetsUseExactCropAndRequestWithoutContextOrScaling() {
        for (size in InfiniteFrameSizes.presets) {
            assertEquals(0, size.width % 64)
            assertEquals(0, size.height % 64)
            assertTrue(size.width * size.height <= 1024 * 1024)
            val p = InfiniteFrameSizes.plan(EditRect(384, 64, size.width, size.height))
            assertEquals(384, p.cropX)
            assertEquals(64, p.cropY)
            assertEquals(size.width, p.cropW)
            assertEquals(size.height, p.cropH)
            assertEquals(p.cropW, p.requestW)
            assertEquals(p.cropH, p.requestH)
            assertEquals(EditRect(0, 0, size.width, size.height), p.frameInCrop)
            assertEquals(1f, p.scale)
        }
    }
    @Test fun legacyFramesMigrateToClosestPresetAroundCenter() {
        val frame = EditRect(-600, -300, 900, 1200)
        val fixed = InfiniteFrameSizes.normalize(frame)
        assertEquals(832, fixed.w)
        assertEquals(1216, fixed.h)
        assertEquals(frame.x + frame.w / 2, fixed.x + fixed.w / 2)
        assertEquals(frame.y + frame.h / 2, fixed.y + fixed.h / 2)
    }
    @Test fun pasteChangesExpandedSeamButLeavesPixelsOutsideCropUntouched() {
        val w = 1280
        val h = 1024
        val source = IntArray(w * h) { 0xFF0000FF.toInt() }
        val p = InfiniteFrameSizes.plan(EditRect(256, 0, 1024, 1024))
        val crop = InfiniteCanvasInput.prepare(source, w, h, p)
        val alpha = ByteArray(crop.pixels.size) { 255.toByte() }
        val generated = com.kallan.naistudio.services.InpaintComposite.composite(
            IntArray(crop.pixels.size) { 0xFFFF0000.toInt() }, crop.pixels, alpha)
        FocusedInpaint.pasteBack(source, w, h, generated, p)
        assertEquals(0xFF0000FF.toInt(), source[255])
        assertEquals(0xFFFF0000.toInt(), source[256])
        assertEquals(0xFFFF0000.toInt(), source.last())
    }
}
