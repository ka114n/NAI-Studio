package com.kallan.naistudio.ui

import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **太阳 / 月亮图标**（侧栏左下角那颗明暗切换按钮，用户 2026-09-26）的冒烟测试。
 *
 * 为什么值得单测：这两个图标是**手写 `ImageVector`**，其中月亮的路径是 `PathParser`
 * 解析的 SVG 字符串 —— 字符串写错的话在真机上是"一点开界面就崩"，而**编译查不出来**
 *（`svgPath()` 在 `by lazy` 里，第一次画到那颗按钮时才跑）。
 * 这里把它们真的构造一遍：路径解析 + `build()` 都跑通、viewport 是 24×24、路径非空。
 */
class ThemeIconSmokeTest {

    private fun nodeCount(icon: ImageVector): Int {
        var n = 0
        icon.root.forEach { n++ }
        return n
    }

    @Test
    fun `sun and moon icons build`() {
        val sun = SunIcon
        val moon = MoonIcon
        assertEquals(24f, sun.viewportWidth, 0f)
        assertEquals(24f, sun.viewportHeight, 0f)
        assertEquals(24f, moon.viewportWidth, 0f)
        assertEquals(24f, moon.viewportHeight, 0f)
        assertTrue("太阳没有路径", nodeCount(sun) > 0)
        assertTrue("月亮没有路径", nodeCount(moon) > 0)
    }
}
