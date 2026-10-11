package com.kallan.naistudio.models

import org.junit.Assert.*
import org.junit.Test

class PromptTagCardsTest {
    @Test fun labelsOmitOnlyDefaultWeight() {
        val tags = PromptTagCards.parse("1::smile::, 1.2::rain::, -0.5::night::")
        assertEquals(listOf("smile", "1.2::rain::", "-0.5::night::"), tags.map { PromptTagCards.displayLabel(it) })
        assertEquals("1.2::雨::", PromptTagCards.displayLabel(tags[1], "雨"))
    }

    @Test fun editingOneDuplicateKeepsSharedWeightsAndNeighbours() {
        val tags = PromptTagCards.parse("{{smile, smile}}, 1.2::rain::")
        val changed = PromptTagCards.rename(tags, tags[1].id, " blue hair ")
        val parsed = PromptTagCards.parse(PromptTagCards.render(changed))
        assertEquals(listOf("smile", "blue hair", "rain"), parsed.map { it.body })
        tags.zip(parsed).forEach { (old, new) -> assertEquals(old.weight, new.weight, 0.000001) }
    }

    @Test fun clearingEditedTagPreservesNumericResetNeighbour() {
        val tags = PromptTagCards.parse("{smile, 1.2::rain::, night}, blue hair")
        val kept = PromptTagCards.rename(tags, tags[1].id, " ")
        val parsed = PromptTagCards.parse(PromptTagCards.render(kept))
        assertEquals(listOf("smile", "night", "blue hair"), parsed.map { it.body })
        kept.zip(parsed).forEach { (old, new) -> assertEquals(old.weight, new.weight, 0.000001) }
    }

    @Test fun nestedGroupsBecomeIndividualCardsAndKeepWeights() {
        val raw = "{smile, [blue hair]}, -0.5::rain, night::\nartist:someone (style)"
        val tags = PromptTagCards.parse(raw)
        assertEquals(listOf("smile", "blue hair", "rain", "night", "artist:someone (style)"), tags.map { it.body })
        listOf(1.05, 1.0, -0.5, -0.5, 1.0).zip(tags).forEach { (expected, tag) -> assertEquals(expected, tag.weight, 0.000001) }
        assertEquals(raw, PromptTagCards.render(tags))
    }

    @Test fun changingOneSharedTagPreservesNeighbourWeight() {
        val tags = PromptTagCards.parse("{{smile, blue hair}}, 1.2::{rain, night}::")
        val changed = PromptTagCards.adjust(tags, tags[1].id, 0.1)
        val reparsed = PromptTagCards.parse(PromptTagCards.render(changed))
        assertEquals(listOf("smile", "blue hair", "rain", "night"), reparsed.map { it.body })
        listOf(1.1025, 1.2025, 1.2, 1.2).zip(reparsed).forEach { (weight, tag) -> assertEquals(weight, tag.weight, 0.000001) }
    }

    @Test fun duplicatesAreIndependentlyMovableAndDeletable() {
        val tags = PromptTagCards.parse("smile, {smile}, rain")
        val moved = PromptTagCards.move(tags, tags[1].id, tags[2].id)
        assertEquals("smile, rain, {smile}", PromptTagCards.render(moved))
        assertEquals("{smile}, rain", PromptTagCards.render(tags.filter { it.id != tags[0].id }))
        assertEquals("", PromptTagCards.render(emptyList()))
    }

    @Test fun draggingAcrossLinesKeepsLineLayout() {
        val tags = PromptTagCards.parse("smile, rain\n\nblue hair")
        assertEquals("blue hair, smile\n\nrain", PromptTagCards.render(PromptTagCards.move(tags, tags[2].id, tags[0].id)))
    }

    @Test fun steppingBackToOneRemovesWeightShell() {
        val tags = PromptTagCards.parse("1.1::smile::")
        assertEquals("smile", PromptTagCards.render(PromptTagCards.adjust(tags, 0, -0.1)))
        assertEquals("-0.1::smile::", PromptTagCards.render(PromptTagCards.adjust(PromptTagCards.parse("0::smile::"), 0, -0.1)))
    }

    @Test fun incompleteSyntaxAndLiteralParenthesesStayIntact() {
        listOf("{{smile, rain", "1.2::smile, rain", "artist:someone (style)").forEach {
            assertEquals(it, PromptTagCards.render(PromptTagCards.parse(it)))
        }
    }

    @Test fun separatorsAndWhitespaceDoNotCreateEmptyTags() {
        assertEquals(listOf("smile", "rain", "night"), PromptTagCards.parse(" ,smile，rain；night, \n").map { it.body })
    }

    @Test fun deletionPreservesNumericResetInsideBracketGroup() {
        val tags = PromptTagCards.parse("{smile, 1.2::rain::, night}, blue hair")
        val kept = tags.filter { it.body != "rain" }
        val parsed = PromptTagCards.parse(PromptTagCards.render(kept))
        assertEquals(kept.map { it.body }, parsed.map { it.body })
        kept.zip(parsed).forEach { (old, new) -> assertEquals(old.weight, new.weight, 0.000001) }
    }
}
