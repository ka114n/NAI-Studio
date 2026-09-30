package com.kallan.naistudio.models

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class InfiniteCanvasSettingsTest {
    @Test
    fun contextSurvivesRestartAndStaysIndependentFromOrdinaryInpaint() {
        val saved = AppSettings(infiniteContext = 384, inpaintFocusContext = 48)
        val restored = AppSettings.fromJson(JSONObject(saved.toJson().toString()))

        assertEquals(384, restored.infiniteContext)
        assertEquals(48, restored.inpaintFocusContext)
    }

    @Test
    fun legacySettingsUseTheDefaultInfiniteContext() {
        val restored = AppSettings.fromJson(JSONObject("""{"canvasMode":1}"""))
        assertEquals(96, restored.infiniteContext)
    }
}
