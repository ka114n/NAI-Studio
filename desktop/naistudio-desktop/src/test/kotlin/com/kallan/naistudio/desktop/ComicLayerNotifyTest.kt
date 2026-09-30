package com.kallan.naistudio.desktop

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.models.ComicBubbleStyles
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.state.AppState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **改图层显隐 / 透明度要真的通知到界面**（第 ⑫ 批 ④「PS 式图层面板」的钉子）。
 *
 * ## 为什么值得一条自己的测试（这是真踩出来的 ✗）
 *
 * `ComicLayer` 里那两样是**可变字段**（`var visible` / `var opacity` ✓）——
 * `ComicLayerStack.setVisible/setOpacity` 改的是**同一个实例**，而
 * `updateComicBoardPage` 走的是 `comicBoardPages = comicBoardPages.map { … }`。
 * `ComicBoardPage` / `ComicLayer` 都是 `data class`：**结构相等的判定比的是"同一个对象的当前值"**，
 * 于是新旧两页**结构上完全相等** ⇒ 默认的 `structuralEqualityPolicy` 会把这次赋值当成"没变"、
 * **一次都不通知** ✗ —— 表现就是"点了眼睛，图层列表的图标、画布上的气泡，全都没反应" ✗。
 *
 * 钉子就是这一条：**`state.comicBoardPages` 必须是引用相等策略**
 *（`referentialEqualityPolicy()` ✓，见 `AppState` 里那段注释 ✓）。
 *
 * ## 它测的是什么
 *
 * 用 `SnapshotStateObserver` **原样**复刻组合期那件事：先"读一次界面会读的那个量"
 *（`state.comicBoardLayers` ✓），再改图层，然后 `Snapshot.sendApplyNotifications()` ——
 * 判据是"那个读被通知了没" ✓。也就是说这条测试**不依赖**能不能起界面（离屏单测里没有
 * Recomposer ✓），但它钉的正是"重组会不会被叫醒"这件事本身 ✓。
 *
 * ⚠️ 用的是**测试隔离档案**（构建脚本把 `%APPDATA%` 指到 `build/test-home` ✓）。
 */
class ComicLayerNotifyTest {

    private fun freshState(): AppState {
        val platform: Platform = desktopPlatform()
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .apply()
        val state = AppState(platform)
        state.ensureComicBoardLoaded()
        state.setComicBoardBase(width = 2048, height = 2048)
        return state
    }

    /** 观察一次"界面读到的那个量"，返回"改完之后它被通知了没" ✓。 */
    private fun notifiedBy(read: () -> Unit, change: () -> Unit): Boolean {
        val observer = SnapshotStateObserver { command -> command() }
        observer.start()
        var invalidated = false
        try {
            observer.observeReads(Unit, { invalidated = true }, read)
            change()
            // 全局快照上的写入要 `sendApplyNotifications()` 才会派发给 apply observer ✓
            //（平时是 Recomposer / 界面帧在调它 ✓，离屏单测里得自己来 ✓）。
            Snapshot.sendApplyNotifications()
        } finally {
            observer.stop()
        }
        return invalidated
    }

    @Test
    fun `图层显隐与透明度会通知界面`() {
        val state = freshState()
        val bubbleId = state.addComicBubble(ComicBubbleStyles.ROUND, 100f, 100f, 700f, 400f)
        assertNotNull("先决条件：气泡应该建得出来", bubbleId)
        val id = bubbleId ?: return

        // ---- ① 点眼睛（显隐）----
        val hiddenNotified = notifiedBy(
            read = { state.comicBoardLayers.size },
            change = { state.setComicLayerVisible(id, false) },
        )
        assertTrue(
            "把一层藏起来必须通知界面（不然列表里的眼睛图标和画布上的气泡都不会变 ✗）",
            hiddenNotified,
        )
        assertFalse("这一层确实变成隐藏了", state.comicBoardLayers.first { it.id == id }.visible)

        // ---- ② 拖不透明度滑杆（`Live` = 不落盘的那条路）----
        val opacityNotified = notifiedBy(
            read = { state.comicBoardLayers.size },
            change = { state.setComicLayerOpacityLive(id, 0.4f) },
        )
        assertTrue("拖不透明度滑杆也必须通知界面 ✗", opacityNotified)

        // ---- ③ 落盘那条（松手收口）同理 ----
        val committedNotified = notifiedBy(
            read = { state.comicBoardLayers.size },
            change = { state.setComicLayerOpacity(id, 0.8f) },
        )
        assertTrue("松手那次 commit 版本的不透明度改动也必须通知界面 ✗", committedNotified)
    }
}
