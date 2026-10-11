package com.kallan.naistudio.desktop.platform

import com.kallan.naistudio.platform.AppPaths
import com.kallan.naistudio.platform.KeyValueStore
import com.kallan.naistudio.platform.Platform
import java.io.File
import java.util.UUID

/** Keep asynchronous AppState saves from different scenes in separate test files. */
internal fun isolatedDesktopTestPlatform(): Platform {
    val root = File("build/test-home/isolated/${UUID.randomUUID()}")
    return object : Platform by desktopPlatform() {
        override val kv: KeyValueStore = FileKeyValueStore(File(root, "prefs.json"))
        override val paths: AppPaths = object : AppPaths {
            override val filesDir = root
            override fun defaultImagesDir() = File(root, "images")
        }
        override fun prefs(name: String): KeyValueStore = FileKeyValueStore(File(root, "$name.json"))
    }
}
