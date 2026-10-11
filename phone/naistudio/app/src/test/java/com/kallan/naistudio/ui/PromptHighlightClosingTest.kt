package com.kallan.naistudio.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.kallan.naistudio.models.PromptWeight
import org.junit.Assert.*
import org.junit.Test

class PromptHighlightClosingTest {
    @Test fun onlyMatchingNumericClosersAreGreenAndOffsetsStayUnchanged() {
        val raw = "1.2::smile::, 0.5::rain::, -1::night::, stray::, 1girl1.2::"
        val colors = PromptHighlightColors(Color.Red, Color.Blue, Color.Magenta, Color.Green)
        val output = PromptHighlightTransformation(colors).filter(AnnotatedString(raw))
        val green = output.text.spanStyles.filter { it.item.color == Color.Green }.map { it.start until it.end }
        assertEquals(PromptWeight.closingMarkers(raw), green)
        assertEquals(3, green.size)
        assertTrue(green.all { raw.substring(it.first, it.last + 1) == "::" })
        assertEquals(raw, output.text.text)
        raw.indices.forEach { assertEquals(it, output.offsetMapping.originalToTransformed(it)) }
        assertEquals(green, listOf(raw.indexOf("::", raw.indexOf("smile")) until raw.indexOf("::", raw.indexOf("smile")) + 2,
            raw.indexOf("::", raw.indexOf("rain")) until raw.indexOf("::", raw.indexOf("rain")) + 2,
            raw.indexOf("::", raw.indexOf("night")) until raw.indexOf("::", raw.indexOf("night")) + 2))
    }

    @Test fun numericLookingTagEndIsACloserAndUnfinishedWeightsHaveNone() {
        assertEquals(listOf(10..11), PromptWeight.closingMarkers("1.2::pose2::"))
        assertTrue(PromptWeight.closingMarkers("1.2::unfinished").isEmpty())
        assertTrue(PromptWeight.closingMarkers("stray::").isEmpty())
    }
}
