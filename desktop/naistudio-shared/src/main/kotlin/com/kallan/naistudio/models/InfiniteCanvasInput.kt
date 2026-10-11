package com.kallan.naistudio.models

/** Prepare outpainting without treating empty padding as preserved image context. */
object InfiniteCanvasInput {
    data class Crop(val pixels: IntArray, val mask: PixelMask, val hasBlankInFrame: Boolean, val hasReference: Boolean) {
        fun strength(requested: Double): Double = if (hasBlankInFrame) 1.0 else requested

        /** Never fade newly generated empty pixels toward the white encoding placeholder. */
        fun completeBlankAlpha(alpha: ByteArray): ByteArray {
            require(alpha.size == pixels.size)
            for (i in alpha.indices) {
                if (mask.pixels[i].toInt() != 0) alpha[i] = 255.toByte()
            }
            return alpha
        }

        /** Keep dilation into the original, while restoring blank slivers lost in latent alignment. */
        fun completeRequestMask(candidate: PixelMask): PixelMask {
            val result = candidate.pixels.copyOf()
            for (y in 0 until candidate.height) {
                val sy = (y.toLong() * mask.height / candidate.height).toInt()
                for (x in 0 until candidate.width) {
                    val sx = (x.toLong() * mask.width / candidate.width).toInt()
                    // Fully fill missing pixels, including narrow gaps swallowed by latent snapping.
                    if (mask.pixels[sy * mask.width + sx].toInt() != 0) result[y * candidate.width + x] = 255.toByte()
                }
            }
            return PixelMask(candidate.width, candidate.height, result)
        }

    }

    fun prepare(source: IntArray, width: Int, height: Int, plan: FocusedInpaint.Plan): Crop {
        require(source.size == width * height)
        val pixels = IntArray(plan.cropW * plan.cropH)
        val mask = ByteArray(pixels.size)
        val frame = plan.frameInCrop
        var hasBlankInFrame = false
        var hasReference = false
        for (y in 0 until plan.cropH) {
            val sy = plan.cropY + y
            for (x in 0 until plan.cropW) {
                val sx = plan.cropX + x
                val index = y * plan.cropW + x
                val original = if (sx in 0 until width && sy in 0 until height) source[sy * width + sx] else 0
                val blank = (original ushr 24) == 0
                val inFrame = x >= frame.x && x < frame.x + frame.w && y >= frame.y && y < frame.y + frame.h
                pixels[index] = if (blank) 0xFFFFFFFF.toInt() else original
                // White is only an encoding substitute for transparency, never reference content.
                if (blank) mask[index] = 255.toByte()
                else hasReference = true
                if (blank && inFrame) hasBlankInFrame = true
            }
        }
        return Crop(pixels, PixelMask(plan.cropW, plan.cropH, mask), hasBlankInFrame, hasReference)
    }
}
