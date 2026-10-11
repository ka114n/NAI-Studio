package com.kallan.naistudio.desktop.platform

import androidx.compose.runtime.Composer
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import com.kallan.naistudio.platform.UiHost
import java.lang.reflect.Modifier
import org.junit.Assert.*
import org.junit.Test

class DesktopUiHostCompatibilityTest {
    @Test fun realHostImplementsEveryCompiledInterfaceMethod() {
        val methods = UiHost::class.java.methods.filter { Modifier.isAbstract(it.modifiers) }
        assertTrue(methods.isNotEmpty())
        for (method in methods) {
            val implementation = DesktopUiHost::class.java.getMethod(method.name, *method.parameterTypes)
            assertFalse("Missing implementation for $method", Modifier.isAbstract(implementation.modifiers))
        }
        val back = methods.single { it.name == "backHandler" }
        assertEquals(4, back.parameterCount)
        assertEquals(Composer::class.java, back.parameterTypes[2])
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun realBackHandlerCanBeComposedThroughHostInterface() {
        val host: UiHost = DesktopUiHost()
        val image = renderComposeScene(32, 32) {
            host.backHandler(enabled = false) { fail("Disabled handler must not fire") }
        }
        assertNotNull(image)
        image.close()
    }
}
