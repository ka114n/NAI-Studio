package com.kallan.naistudio.screens

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import com.kallan.naistudio.desktop.platform.isolatedDesktopTestPlatform
import com.kallan.naistudio.models.PromptTagCards
import com.kallan.naistudio.platform.LocalPlatform
import com.kallan.naistudio.state.AppState
import org.junit.Assert.*
import org.junit.Test
import javax.swing.SwingUtilities

/** Exercise actual Compose pointer dispatch, including recomposition during a slide. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class BilingualTagGestureTest {
    private val held = PointerButtons(isPrimaryPressed = true)

    private fun runEditor(readOnly: Boolean = false, fieldKey: String = "positive", trash: TagDragTrashState = TagDragTrashState(), action: (ImageComposeScene, () -> String, () -> Boolean) -> Unit) {
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait {
            try {
                val platform = isolatedDesktopTestPlatform()
                val state = AppState(platform)
                val value = mutableStateOf("smile, rain")
                state.setSettings { it.copy(positiveTagCardMode = true) }
                val scene = ImageComposeScene(400, 300, Density(1f)) {
                    CompositionLocalProvider(LocalPlatform provides platform) {
                        MaterialTheme {
                            TagDragScope(trash) {
                                Column {
                                    BilingualTagEditor(state, value.value, readOnly, {}, { value.value = it }, fieldKey)
                                    Spacer(Modifier.height(100.dp))
                                    TagTrashToolbar(state, fieldKey) { Box(Modifier.fillMaxWidth().height(34.dp)) }
                                }
                            }
                        }
                    }
                }
                try {
                    scene.render().close()
                    action(scene, { value.value }, { state.settings.positiveTagCardMode })
                } finally { scene.close() }
            } catch (t: Throwable) { failure = t }
        }
        failure?.let { throw it }
    }

    private fun ImageComposeScene.press(point: Offset) {
        sendPointerEvent(PointerEventType.Press, point, buttons = held, button = PointerButton.Primary)
        render().close()
    }

    private fun ImageComposeScene.release(point: Offset) {
        sendPointerEvent(PointerEventType.Release, point, buttons = PointerButtons(), button = PointerButton.Primary)
        render().close()
    }

    private fun ImageComposeScene.type(text: String) {
        for (character in text) {
            val event = java.awt.event.KeyEvent(java.awt.Canvas(), java.awt.event.KeyEvent.KEY_TYPED, 0L, 0,
                java.awt.event.KeyEvent.VK_UNDEFINED, character)
            sendKeyEvent(KeyEvent(key = Key.Unknown, type = KeyEventType.Unknown, codePoint = character.code, nativeEvent = event))
            render().close()
        }
    }

    @Test fun clickInTagPlacesCaretWithoutSelectingWholeText() = runEditor { scene, value, _ ->
        scene.press(Offset(18f, 28f))
        scene.release(Offset(18f, 28f))
        scene.type("X")
        scene.press(Offset(350f, 28f))
        scene.release(Offset(350f, 28f))
        val body = PromptTagCards.parse(value()).first().body
        assertEquals("Click should insert into the tag instead of replacing it", "smile", body.replace("X", ""))
        assertTrue("Caret should be inside the clicked word: $body", body.indexOf('X') in 1 until body.lastIndex)
    }

    @Test fun plusCreatesEditableCardAndCommitsNewTag() = runEditor { scene, value, cardMode ->
        scene.press(Offset(120f, 28f))
        scene.release(Offset(120f, 28f))
        scene.type("new")
        scene.press(Offset(350f, 28f))
        scene.release(Offset(350f, 28f))
        assertEquals(listOf("smile", "rain", "new"), PromptTagCards.parse(value()).map { it.body })
        assertTrue(cardMode())
    }

    @Test fun shortHeldHorizontalDragMovesEntireCardWithoutChangingWeight() = runEditor { scene, value, cardMode ->
        scene.sendPointerEvent(PointerEventType.Press, Offset(20f, 28f), timeMillis = 1000L, buttons = held, button = PointerButton.Primary)
        scene.render().close()
        scene.sendPointerEvent(PointerEventType.Move, Offset(75f, 28f), timeMillis = 1220L, buttons = held)
        scene.render().close()
        scene.sendPointerEvent(PointerEventType.Release, Offset(75f, 28f), timeMillis = 1250L, buttons = PointerButtons(), button = PointerButton.Primary)
        scene.render().close()
        val tags = PromptTagCards.parse(value())
        assertEquals(listOf("rain", "smile"), tags.map { it.body })
        assertTrue(tags.all { it.weight == 1.0 })
        assertTrue(cardMode())
    }

    @Test fun tapEditsCardWithoutChangingWeightOrSwitchingModes() = runEditor { scene, value, cardMode ->
        scene.press(Offset(20f, 28f))
        scene.release(Offset(20f, 28f))
        assertEquals("smile, rain", value())
        assertTrue(cardMode())
        // Clicking unused space commits the unchanged card and keeps bilingual mode.
        scene.press(Offset(350f, 28f))
        scene.release(Offset(350f, 28f))
        assertTrue(cardMode())
        assertEquals("smile, rain", value())
    }

    @Test fun horizontalSlideSurvivesRecompositionAndPreservesNeighbour() = runEditor { scene, value, cardMode ->
        scene.sendPointerEvent(PointerEventType.Press, Offset(20f, 28f), timeMillis = 2000L, buttons = held, button = PointerButton.Primary)
        scene.render().close()
        val weights = mutableListOf<Double>()
        for ((index, x) in listOf(40f, 60f, 100f).withIndex()) {
            scene.sendPointerEvent(PointerEventType.Move, Offset(x, 28f), timeMillis = 2020L + index * 20L, buttons = held)
            scene.render().close()
            weights += PromptTagCards.parse(value()).first().weight
        }
        scene.sendPointerEvent(PointerEventType.Release, Offset(100f, 28f), timeMillis = 2080L, buttons = PointerButtons(), button = PointerButton.Primary)
        scene.render().close()
        val tags = PromptTagCards.parse(value())
        assertEquals(listOf("smile", "rain"), tags.map { it.body })
        assertTrue("Sliding should continue through subsequent frames: $weights", weights.last() > weights[1])
        assertTrue("Expected positive weight change: ${value()}", tags[0].weight > 1.0)
        assertEquals(1.0, tags[1].weight, 0.000001)
        assertTrue(cardMode())
    }

    @Test fun dragKeepsOriginalGrabPointWithPanelAndCardOffsets() {
        val origin = Offset(120f, 180f)
        val grab = Offset(37f, 29f)
        val pointer = Offset(330f, 450f)
        val topLeft = tagDragTopLeft(pointer, origin, grab)
        assertEquals(pointer, origin + topLeft + grab)
        assertEquals(Offset(173f, 241f), topLeft)
        assertEquals(Offset(-147f, -179f), tagDragTopLeft(Offset(10f, 30f), origin, grab))
    }

    @Test fun styleCardsEditAndBlankSpaceKeepsSharedMode() = runEditor(fieldKey = "style") { scene, value, cardMode ->
        scene.press(Offset(18f, 28f))
        scene.release(Offset(18f, 28f))
        scene.type("X")
        scene.press(Offset(350f, 28f))
        scene.release(Offset(350f, 28f))
        assertTrue(PromptTagCards.parse(value()).first().body.contains("X"))
        assertTrue(cardMode())
    }

    @Test fun heldDragShowsCircularTargetAndDropDeletesOnlyDraggedCard() {
        val trash = TagDragTrashState()
        runEditor(trash = trash) { scene, value, cardMode ->
            assertFalse(trash.visible)
            scene.sendPointerEvent(PointerEventType.Press, Offset(20f, 28f), timeMillis = 3000L, buttons = held, button = PointerButton.Primary)
            scene.render().close()
            assertFalse("Press alone must not show the target", trash.visible)
            scene.sendPointerEvent(PointerEventType.Move, Offset(80f, 28f), timeMillis = 3220L, buttons = held)
            scene.render().close()
            assertTrue(trash.visible)
            assertEquals(48f, trash.bounds.width, .01f)
            assertEquals("Circle must stay square above the toolbar", 48f, trash.bounds.height, .01f)
            val center = trash.bounds.center
            scene.sendPointerEvent(PointerEventType.Move, center, timeMillis = 3260L, buttons = held)
            scene.render().close()
            assertTrue("Hovering the circle opens the lid", trash.hovered)
            scene.sendPointerEvent(PointerEventType.Release, center, timeMillis = 3290L, buttons = PointerButtons(), button = PointerButton.Primary)
            scene.render().close()
            assertEquals("rain", value())
            assertFalse(trash.visible)
            assertTrue(cardMode())
        }
        assertFalse(trash.visible)
    }

    @Test fun circularCornersAreNotDeleteTargetsAndCancelHidesTarget() {
        val trash = TagDragTrashState()
        runEditor(trash = trash) { scene, value, _ ->
            scene.sendPointerEvent(PointerEventType.Press, Offset(20f, 28f), timeMillis = 4000L, buttons = held, button = PointerButton.Primary)
            scene.render().close()
            scene.sendPointerEvent(PointerEventType.Move, Offset(80f, 28f), timeMillis = 4220L, buttons = held)
            scene.render().close()
            val corner = trash.bounds.topLeft + Offset(1f, 1f)
            scene.sendPointerEvent(PointerEventType.Move, corner, timeMillis = 4260L, buttons = held)
            scene.render().close()
            assertFalse(trash.hovered)
            scene.sendPointerEvent(PointerEventType.Release, corner, timeMillis = 4290L, buttons = PointerButtons(), button = PointerButton.Primary)
            scene.render().close()
            assertEquals("smile, rain", value())
            assertFalse(trash.visible)
            scene.sendPointerEvent(PointerEventType.Press, Offset(20f, 28f), timeMillis = 5000L, buttons = held, button = PointerButton.Primary)
            scene.render().close()
            scene.sendPointerEvent(PointerEventType.Move, Offset(80f, 28f), timeMillis = 5220L, buttons = held)
            scene.render().close()
            assertTrue(trash.visible)
            // Disposing the scene while holding the pointer must clear the transient target.
        }
        assertFalse(trash.visible)
    }

    @Test fun quickWeightSlideNeverShowsTrash() {
        val trash = TagDragTrashState()
        runEditor(trash = trash) { scene, value, _ ->
            scene.sendPointerEvent(PointerEventType.Press, Offset(20f, 28f), timeMillis = 6000L, buttons = held, button = PointerButton.Primary)
            scene.render().close()
            scene.sendPointerEvent(PointerEventType.Move, Offset(80f, 28f), timeMillis = 6040L, buttons = held)
            scene.render().close()
            assertFalse(trash.visible)
            scene.sendPointerEvent(PointerEventType.Release, Offset(80f, 28f), timeMillis = 6080L, buttons = PointerButtons(), button = PointerButton.Primary)
            scene.render().close()
            assertTrue(PromptTagCards.parse(value()).first().weight > 1.0)
            assertFalse(trash.visible)
        }
    }

    @Test fun lockedCardsIgnoreSlideAndEmptySpaceInput() = runEditor(readOnly = true) { scene, value, cardMode ->
        scene.press(Offset(20f, 28f))
        scene.sendPointerEvent(PointerEventType.Move, Offset(80f, 28f), buttons = held)
        scene.release(Offset(80f, 28f))
        scene.press(Offset(350f, 28f))
        scene.release(Offset(350f, 28f))
        assertEquals("smile, rain", value())
        assertTrue(cardMode())
    }
}
