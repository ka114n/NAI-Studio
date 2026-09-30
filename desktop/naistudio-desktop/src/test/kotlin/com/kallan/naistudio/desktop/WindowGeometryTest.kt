package com.kallan.naistudio.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **窗口几何的恢复规则**（记住上次的大小与位置）。
 *
 * 这段本身只有十几行，但它是"启动后窗口能不能看见"的唯一保障：
 *
 *  · 显示器拔了 / 分辨率变了 → 记下来的坐标可能完全在屏幕外，
 *    那样窗口**看不见也点不到**，用户只会觉得"App 打不开了"；
 *  · 4K 屏上记的尺寸带回 1024×600 的小屏 → 窗口比屏幕还大，界面挤成一团。
 *
 * 屏幕统一取 `(0, 0, 1920, 1080)`（主屏），坐标是窗口左上角。
 */
class WindowGeometryTest {

    private fun restore(
        width: Int,
        height: Int,
        x: Int = Int.MIN_VALUE / 2,
        y: Int = Int.MIN_VALUE / 2,
    ) = restoreWindowGeometry(
        savedWidth = width,
        savedHeight = height,
        savedX = x,
        savedY = y,
        screenX = 0,
        screenY = 0,
        screenWidth = 1920,
        screenHeight = 1080,
    )

    @Test
    fun saved_size_and_position_are_used_as_is() {
        val geo = restore(width = 1280, height = 900, x = 100, y = 60)
        assertEquals(1280, geo.width)
        assertEquals(900, geo.height)
        assertEquals(100, geo.x)
        assertEquals(60, geo.y)
    }

    @Test
    fun never_restored_before_centers_on_screen() {
        // 首次启动：没有位置记忆 → 居中（尺寸用默认值，由调用方给）
        val geo = restore(width = 1080, height = 820)
        assertEquals(1080, geo.width)
        assertEquals(820, geo.height)
        assertEquals((1920 - 1080) / 2, geo.x)
        assertEquals((1080 - 820) / 2, geo.y)
    }

    @Test
    fun size_is_clamped_to_a_minimum() {
        // 有人把窗口拖成一条缝 —— 恢复时不能真的还他一条缝
        val geo = restore(width = 100, height = 80, x = 10, y = 10)
        assertEquals(MIN_WINDOW_WIDTH, geo.width)
        assertEquals(MIN_WINDOW_HEIGHT, geo.height)
    }

    @Test
    fun size_is_clamped_to_the_screen() {
        // 4K 屏上的记录带到了小屏：不能比屏幕还大
        val geo = restore(width = 3840, height = 2160, x = 0, y = 0)
        assertEquals(1920, geo.width)
        assertEquals(1080, geo.height)
    }

    @Test
    fun position_far_off_screen_comes_back_to_center() {
        // 显示器拔了：记下来的坐标在"已经不存在的"那台屏上
        val geo = restore(width = 1080, height = 820, x = 5000, y = 3000)
        assertEquals((1920 - 1080) / 2, geo.x)
        assertEquals((1080 - 820) / 2, geo.y)
    }

    @Test
    fun negative_position_comes_back_to_center() {
        // 副屏在主屏左侧时可能记成负数；副屏没了就得回主屏中央
        val geo = restore(width = 1080, height = 820, x = -1500, y = -800)
        assertTrue("必须回到屏幕内", geo.x >= 0 && geo.y >= 0)
        assertEquals((1920 - 1080) / 2, geo.x)
    }

    @Test
    fun partially_visible_position_is_kept() {
        // 右边缘只露出 100px：还算"看得见"，保留（用户就是习惯把窗口怼在边上）
        val geo = restore(width = 1080, height = 820, x = 1920 - 100, y = 40)
        assertEquals(1920 - 100, geo.x)
        assertEquals(40, geo.y)
    }

    @Test
    fun fully_visible_corner_position_is_kept() {
        val geo = restore(width = 900, height = 600, x = 1920 - 900, y = 1080 - 600)
        assertEquals(1020, geo.x)
        assertEquals(480, geo.y)
    }

    /**
     * **"看起来是最大化时存下来的" → 位置不算数，回到居中**
     *（用户 2026-09-19：「顶部突出的窗口收回」）。
     *
     * 最大化窗口的尺寸/坐标是"工作区 + 四周 8px 隐形边框"（真机实测 `-8,-8 / 2576×1408`）：
     * 那串数字当普通窗口恢复出来，窗口就是**顶出屏幕**的样子。
     * 所以这种记录**尺寸和位置一律不采用** —— 回到默认尺寸 + 居中。
     */
    @Test
    fun saved_maximized_geometry_is_centered_instead_of_reused() {
        val geo = restore(width = 1936, height = 1096, x = -8, y = -8)
        // 默认尺寸本身也可能比"这块屏"大（比如默认改成 1692×1128、而这块屏只有 1920×1080），
        // 那就照旧夹到屏幕 —— 断言跟着这个口径写，免得默认值一改测试就红。
        val expectedW = DefaultWindowWidth.coerceAtMost(1920)
        val expectedH = DefaultWindowHeight.coerceAtMost(1080)
        assertEquals(expectedW, geo.width)
        assertEquals(expectedH, geo.height)
        assertEquals((1920 - expectedW) / 2, geo.x)
        assertEquals((1080 - expectedH) / 2, geo.y)
    }
}
