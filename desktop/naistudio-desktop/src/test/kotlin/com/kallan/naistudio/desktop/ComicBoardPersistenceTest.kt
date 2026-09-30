package com.kallan.naistudio.desktop

import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.models.ComicPanel
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.state.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **「高级漫画」关掉再打开要还在**（`docs/43` M1 的交付标准之一）。
 *
 * 为什么值得一条真跑状态层的单测：
 *  · 两个键（`comic.page.current` / `comic.pages`）是**惰性读盘**的
 *    （第一次进那一页才读，见 `AppState.ensureComicBoardLoaded`）——
 *    只测 JSON 编解码根本证明不了"第二个 AppState 实例读得回来"；
 *  · 写盘时机是这块地基最容易踩的坑（拖动过程每帧写会磨穿 prefs.json，
 *    所以"手势里只改内存、结束时 commit"—— 这条只有真读一遍盘才验得出来 ✓）。
 *
 * ⚠️ 用的是**测试隔离档案**（构建脚本把 `%APPDATA%` 指到 `build/test-home`，
 * 见 `build.gradle.kts` 里 `tasks.withType<Test>` 那段）—— 绝不碰用户真实的 prefs.json ✓。
 * 每次跑之前先把这两个键清掉：隔离档案是**跨次保留**的，不清就会出现
 * "第二次跑多出几个格子"这种假失败 ✗。
 */
class ComicBoardPersistenceTest {

    @Test
    fun `底板与格子能活过重启 —— 同一份 kv 换一个 AppState 也读得回来`() {
        val platform: Platform = desktopPlatform()
        // 清场：两个键都清掉（空串 = 没有页；见 ComicBoardStore.decodePages）
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .apply()

        // ---- 第一次"开机"：建底板 + 手拖一格 ----
        val first = AppState(platform)
        first.ensureComicBoardLoaded()
        first.setComicBoardBase(width = 2480, height = 3508)
        val panelId = first.addComicPanel(x = 120f, y = 200f, w = 900f, h = 600f)
        assertNotNull("这一格应该建出来", panelId)
        first.commitComicBoard()

        val created = first.comicBoardPage
        assertNotNull("第一台应该有当前页", created)
        assertEquals(1, created!!.panels.size)

        // ---- 第二次"开机"：**换一个 AppState 实例**（= 关掉应用再打开）----
        val second = AppState(platform)
        second.ensureComicBoardLoaded()

        val restored = second.comicBoardPage
        assertNotNull("重启之后应该还有一页", restored)
        assertEquals("底板宽要还在", 2480, restored!!.baseWidth)
        assertEquals("底板高要还在", 3508, restored.baseHeight)
        assertEquals("格子数要还在", 1, restored.panels.size)

        val panel: ComicPanel = restored.panels.first()
        assertEquals("格子的 x 要还在", 120f, panel.x, 0.01f)
        assertEquals("格子的 y 要还在", 200f, panel.y, 0.01f)
        assertEquals("格子的宽要还在", 900f, panel.w, 0.01f)
        assertEquals("格子的高要还在", 600f, panel.h, 0.01f)
        assertEquals("序号要还在", 0, panel.order)
        // 目标像素是**算出来**的（不走盘）：900×600 是 3:2 的横图 → 长边取整后 1280×832 超了面积上限，
        // 只退长边一档 64 → **1216×832**（见 InpaintSize 的算法与 InpaintSizeTest）
        assertEquals("目标像素要按 InpaintSize 算", "1216×832", panel.targetPixelText())
    }

    @Test
    fun `拖动过程不写盘、commit 之后才落盘`() {
        val platform: Platform = desktopPlatform()
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .apply()

        val state = AppState(platform)
        state.ensureComicBoardLoaded()
        state.setComicBoardBase(width = 2048, height = 2048)
        val panelId = state.addComicPanel(100f, 100f, 800f, 800f)!!

        // 手势里的一堆位移：只改内存
        state.moveComicPanelBy(panelId, 50f, 25f)
        state.moveComicPanelBy(panelId, 50f, 25f)
        val inMemory = state.comicBoardPanels.first()
        assertEquals(200f, inMemory.x, 0.01f)
        assertEquals(150f, inMemory.y, 0.01f)

        // 同一份 kv 交给"另一个实例"读：**还没 commit**，所以读到的是上一次落盘的样子
        val reader = AppState(platform)
        reader.ensureComicBoardLoaded()
        assertEquals("没 commit 之前盘上应该还是老位置", 100f, reader.comicBoardPanels.first().x, 0.01f)

        // commit（= 手势结束）之后才落盘
        state.commitComicBoard()
        val reader2 = AppState(platform)
        reader2.ensureComicBoardLoaded()
        assertEquals("commit 之后盘上应该是最新位置", 200f, reader2.comicBoardPanels.first().x, 0.01f)
        assertTrue(reader2.comicBoardPanels.isNotEmpty())
    }
}
