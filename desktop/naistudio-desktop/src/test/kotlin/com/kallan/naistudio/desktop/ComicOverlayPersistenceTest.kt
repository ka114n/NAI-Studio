package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.ComicBoardStore
import com.kallan.naistudio.models.ComicBubbleStyles
import com.kallan.naistudio.models.ComicLayer
import com.kallan.naistudio.platform.Platform
import com.kallan.naistudio.state.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **气泡 / 文本 / 图层"关掉再打开要还在"**（`docs/43` §9.1 §9.2 §9.3 的交付标准之一）。
 *
 * 为什么值得一条**真跑状态层**的单测：气泡的尾巴尖、字号、字体、以及"哪一层盖在上面"，
 * 全是用户一点一点调出来的东西 —— JSON 编解码测过不代表"第二个 AppState 实例读得回来"
 * （惰性读盘、两个键一起写、图层叠对齐都在状态层里 ✓）。
 *
 * ⚠️ 用的是**测试隔离档案**（构建脚本把 `%APPDATA%` 指到 `build/test-home` ✓），
 * 而且每次先把两个键清掉 —— 隔离档案是跨次保留的，不清就会出现假失败 ✗。
 */
class ComicOverlayPersistenceTest {

    private fun freshState(): Pair<Platform, AppState> {
        val platform: Platform = desktopPlatform()
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .apply()
        val state = AppState(platform)
        state.ensureComicBoardLoaded()
        state.setComicBoardBase(width = 2048, height = 2048)
        return platform to state
    }

    @Test
    fun `气泡与文本能活过重启、连图层一起`() {
        val (platform, state) = freshState()

        val bubbleId = state.addComicBubble(ComicBubbleStyles.THOUGHT, 100f, 100f, 700f, 400f)
        assertNotNull("这一下应该放出一个气泡", bubbleId)
        val textId = state.addComicText(200f, 900f)
        assertNotNull("这一下应该落一段文本", textId)

        // 用户改的东西：字 / 字体 / 尾巴尖 / 透明度
        state.updateComicBubble(bubbleId!!) {
            it.copy(text = "你好", fontFile = "my-font.ttf", tailX = 400f, tailY = 720f, fontSize = 96f)
        }
        state.setComicLayerOpacity(bubbleId, 0.5f)
        state.commitComicBoard()

        // ---- 换一个 AppState 实例 = 关掉应用再打开 ----
        val reopened = AppState(platform)
        reopened.ensureComicBoardLoaded()
        val page = reopened.comicBoardPage
        assertNotNull("重启之后应该还有一页", page)

        assertEquals("气泡数", 1, page!!.bubbles.size)
        val bubble = page.bubbles.first()
        assertEquals("泡里的字", "你好", bubble.text)
        assertEquals("字体", "my-font.ttf", bubble.fontFile)
        assertEquals("样式", ComicBubbleStyles.THOUGHT, bubble.style)
        assertEquals("尾巴尖 X", 400f, bubble.tailX!!, 0.01f)
        assertEquals("尾巴尖 Y", 720f, bubble.tailY!!, 0.01f)
        assertEquals("字号", 96f, bubble.fontSize, 0.01f)

        assertEquals("文本数", 1, page.texts.size)
        assertEquals("文本框的宽默认按底板算", 2048 * 0.42f, page.texts.first().w, 0.5f)

        // 图层：底板在最下 + 气泡一层 + 文本一层 ✓，而且顺序就是加的顺序 ✓
        assertEquals(3, page.layers.size)
        assertEquals(ComicLayer.Kind.BASE, page.layers.first().kind)
        assertEquals(listOf(bubbleId, textId!!), page.layers.drop(1).map { it.id })
        assertEquals("透明度要存下来", 0.5f, page.layers.first { it.id == bubbleId }.opacity, 0.01f)
    }

    @Test
    fun `删掉气泡时它的图层一起走、隐藏不等于删除`() {
        val (platform, state) = freshState()
        val bubbleId = state.addComicBubble(ComicBubbleStyles.ROUND, 100f, 100f, 700f, 400f)!!
        assertEquals(2, state.comicBoardLayers.size)

        // 隐藏：**还在盘上**（只是不画）✓
        state.setComicLayerVisible(bubbleId, false)
        state.commitComicBoard()
        val hidden = AppState(platform).also { it.ensureComicBoardLoaded() }
        assertEquals("隐藏之后气泡还在", 1, hidden.comicBoardBubbles.size)
        assertFalse("那一层记着「隐藏」", hidden.comicBoardLayers.first { it.id == bubbleId }.visible)

        // 删除：气泡和它的图层**一起**走 ✓（不留下点不中的幽灵层 ✗）
        hidden.removeComicOverlay(bubbleId)
        assertEquals(0, hidden.comicBoardBubbles.size)
        assertEquals(0, hidden.comicBoardTexts.size)
        assertNull("那一层也没了", hidden.comicBoardLayers.firstOrNull { it.id == bubbleId })
        assertTrue("底板层还在", hidden.comicBoardLayers.any { it.kind == ComicLayer.Kind.BASE })
    }

    @Test
    fun `图层能上移下移、到边界什么也不做`() {
        val (_, state) = freshState()
        val bubbleId = state.addComicBubble(ComicBubbleStyles.ROUND, 100f, 100f, 700f, 400f)!!
        val textId = state.addComicText(100f, 900f)!!
        // 加的顺序：气泡在下、文本在上（新加的盖在上面 ✓）
        assertEquals(listOf(bubbleId, textId), state.comicBoardLayers.drop(1).map { it.id })

        // 文本往下挪一层 → 跑到气泡下面
        state.moveComicLayer(textId, -1)
        assertEquals(listOf(textId, bubbleId), state.comicBoardLayers.drop(1).map { it.id })

        // 已经是最下面了：再往下什么也不发生（不抛、不绕回去 ✓）
        state.moveComicLayer(textId, -1)
        assertEquals(listOf(textId, bubbleId), state.comicBoardLayers.drop(1).map { it.id })

        // 底板层不给排（界面上它的上下移是 disabled ✓）；就算硬调一次，它也得老老实实待在叠底 ✓
        // 不这么兜的话，气泡一挪到底板下面就会被底板图盖住 —— 页面上再也看不见了 ✗
        state.moveComicLayer(state.comicBoardLayers.first().id, +1)
        assertEquals(ComicLayer.Kind.BASE, state.comicBoardLayers.first().kind)
        assertEquals(ComicLayer.Kind.BUBBLE, state.comicBoardLayers.last().kind)
    }

    @Test
    fun `没有底板时放不出气泡、太小也放不出`() {
        val platform: Platform = desktopPlatform()
        platform.kv.edit()
            .remove(ComicBoardStore.KEY_PAGES)
            .remove(ComicBoardStore.KEY_CURRENT)
            .apply()
        val state = AppState(platform)
        state.ensureComicBoardLoaded()
        assertNull("还没有底板", state.addComicBubble(ComicBubbleStyles.ROUND, 0f, 0f, 500f, 500f))
        assertNull("还没有底板", state.addComicText(0f, 0f))

        state.setComicBoardBase(width = 2048, height = 2048)
        assertNull(
            "太小的一下（没到最小边长）不该落一个气泡",
            state.addComicBubble(ComicBubbleStyles.ROUND, 0f, 0f, 10f, 10f),
        )
    }
}
