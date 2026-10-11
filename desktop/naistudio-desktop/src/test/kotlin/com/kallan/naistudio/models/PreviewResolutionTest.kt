package com.kallan.naistudio.models

import org.junit.Assert.*
import org.junit.Test

class PreviewResolutionTest {
    @Test fun largeCompositeLoadsMoreDetailOnZoom() {
        assertEquals(1024, PreviewResolution.dimension(4096, 2048, 1000, 700, 1f))
        assertEquals(4096, PreviewResolution.dimension(4096, 2048, 1000, 700, 4f))
        assertEquals(4096, PreviewResolution.dimension(4096, 2048, 1000, 700, 6f))
    }
    @Test fun resolutionIsStableWithinBucketAndPreservesSmallOriginal() {
        assertEquals(2048, PreviewResolution.dimension(4096, 2048, 1000, 700, 1.1f))
        assertEquals(2048, PreviewResolution.dimension(4096, 2048, 1000, 700, 1.5f))
        assertEquals(800, PreviewResolution.dimension(800, 600, 1000, 700, 6f))
    }
    @Test fun hugeSquareStaysWithinMemoryBudget() {
        assertEquals(4096, PreviewResolution.dimension(16000, 16000, 2000, 2000, 6f))
        assertEquals(8192, PreviewResolution.dimension(16000, 1000, 2000, 2000, 6f))
    }
    @Test fun samplingDoesNotThrowAwayRequestedPixels() {
        assertEquals(1, PreviewResolution.sample(2000, 1000, 1440))
        assertEquals(2, PreviewResolution.sample(4096, 2048, 1440))
        assertEquals(4, PreviewResolution.sample(4096, 2048, 1024))
        assertEquals(4, PreviewResolution.sample(16000, 16000, 4096))
    }
}
