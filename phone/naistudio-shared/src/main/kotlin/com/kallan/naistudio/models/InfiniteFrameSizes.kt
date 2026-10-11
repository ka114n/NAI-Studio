package com.kallan.naistudio.models

import kotlin.math.abs

/** Fixed, 64-aligned request sizes with no extra crop/context band. */
object InfiniteFrameSizes {
    data class Size(val width: Int, val height: Int)
    val presets = listOf(Size(1024, 1024), Size(832, 1216), Size(1216, 832))
    fun nearest(width: Int, height: Int): Size = presets.minBy {
        abs(it.width.toLong() - width) + abs(it.height.toLong() - height)
    }
    fun normalize(frame: EditRect): EditRect {
        val size = nearest(frame.w, frame.h)
        return frame.copy(x = frame.x + (frame.w - size.width) / 2,
            y = frame.y + (frame.h - size.height) / 2, w = size.width, h = size.height)
    }
    fun plan(frame: EditRect): FocusedInpaint.Plan {
        require(presets.any { it.width == frame.w && it.height == frame.h })
        val local = EditRect(0, 0, frame.w, frame.h)
        return FocusedInpaint.Plan(frame.x, frame.y, frame.w, frame.h, frame.w, frame.h,
            local, local, 1f, 1f, false, frame.w * frame.h, true)
    }
}
