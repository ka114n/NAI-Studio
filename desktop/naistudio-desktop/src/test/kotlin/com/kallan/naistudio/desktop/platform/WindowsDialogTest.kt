package com.kallan.naistudio.desktop.platform

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Ole32
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class WindowsDialogTest {
    @Test fun filtersKeepAllFilesAndDeduplicateSupportedExtensions() {
        val request = WindowsDialogRequest(title = "导入", extensions = listOf(".png", ".jpg", ".png"))
        assertEquals("*.png;*.jpg", request.filters().first().second)
        assertEquals("*.*", request.filters().last().second)
        assertEquals(listOf("所有文件" to "*.*"), WindowsDialogRequest(title = "导入").filters())
    }

    @Test fun saveUsesSuggestedExtensionAndDoesNotReadParentDirectoryExtension() {
        val request = WindowsDialogRequest(save = true, title = "保存", extensions = listOf(".png"))
        assertEquals("png", request.defaultExtension())
        assertEquals("psd", request.copy(suggestedName = "页面.psd").defaultExtension())
        assertEquals("png", request.copy(suggestedName = "C:/folder.json/页面").defaultExtension())
        assertNull(request.copy(save = false).defaultExtension())
    }

    @Test fun windowsCreatesAndConfiguresAllThreeNativeDialogsWithoutShowingUi() {
        assumeTrue(isWindowsPlatform())
        val task = FutureTask {
            val ole = Ole32.INSTANCE
            val hr = ole.CoInitializeEx(null, 2).toInt()
            assertTrue(hr >= 0)
            try {
                val target = DesktopFolderDialog
                fun invoke(name: String, vararg args: Any): Any? {
                    val method = target.javaClass.declaredMethods.first { it.name == name }
                    method.isAccessible = true
                    return method.invoke(target, *args)
                }
                val requests = listOf(
                    WindowsDialogRequest(title = "导入图片", extensions = listOf(".png", ".jpg")),
                    WindowsDialogRequest(save = true, title = "保存", extensions = listOf(".png"), suggestedName = "测试.png"),
                    WindowsDialogRequest(folder = true, title = "选择目录"),
                )
                requests.forEach { request ->
                    val dialog = invoke("createDialog", ole, request.save) as Pointer?
                    assertNotNull(dialog)
                    dialog!!
                    fun call(index: Int, vararg args: Any?): Int {
                        val address = dialog.getPointer(0).getPointer(index.toLong() * Native.POINTER_SIZE)
                        return Function.getFunction(address, Function.ALT_CONVENTION).invokeInt(arrayOf(dialog, *args))
                    }
                    try {
                        invoke("applyOptions", dialog, request)
                        val keep = invoke("configureFiles", dialog, request)
                        val options = Memory(4)
                        assertEquals(0, call(10, options))
                        assertTrue(options.getInt(0) and 0x40 != 0)
                        assertEquals(request.folder, options.getInt(0) and 0x20 != 0)
                        assertEquals(0, options.getInt(0) and 0x200)
                        assertTrue(options.getInt(0) and (if (request.save) 0x2 else if (request.folder) 0x800 else 0x1000) != 0)
                        java.lang.ref.Reference.reachabilityFence(keep)
                    } finally { call(2) }
                }
            } finally { ole.CoUninitialize() }
        }
        Thread(task, "WindowsDialogTest-STA").apply { isDaemon = true; start() }
        task.get(20, TimeUnit.SECONDS)
    }
}
