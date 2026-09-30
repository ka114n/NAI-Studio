package com.kallan.naistudio.desktop

import com.kallan.naistudio.desktop.platform.DesktopImageIo
import com.kallan.naistudio.desktop.platform.desktopPlatform
import com.kallan.naistudio.models.ConversationTurn
import com.kallan.naistudio.models.SelectionShape
import com.kallan.naistudio.platform.MAX_LOG_LINES
import com.kallan.naistudio.platform.clearRecentLogs
import com.kallan.naistudio.platform.logInfo
import com.kallan.naistudio.platform.recentLogs
import com.kallan.naistudio.state.AppState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **底部抽屉（对话 / 日志）的数据层**（不需要界面、不联网）。
 *
 * 用户 2026-09-16："将底下显示正在跑图 1/1… 那一行字幕做成一个抽屉，点击弹出，
 * 可在左边选择对话模式和日志模式"；对话模式要能回答"用了什么模型、发了什么、回了什么"。
 */
class ConversationLogTest {

    private fun makeImage(): File {
        val w = 64
        val h = 64
        val pixels = IntArray(w * h) { 0xFF808080.toInt() }
        val image = DesktopImageIo.fromArgb(pixels, w, h)
        val bytes = DesktopImageIo.pngBytes(image)
        image.recycle()
        val file = File(System.getProperty("user.home"), "conversation-log-test.png")
        file.writeBytes(bytes)
        return file
    }

    private fun newState(): AppState = AppState(desktopPlatform())

    @Test
    fun `every status change is captured into the history`() {
        val state = newState()
        val before = state.statusHistory.size
        // 这几个公开入口都会写 status（图生图设底图 / 清聚焦框 / 拖聚焦框的工具提示）
        state.useImageAsI2iBase(makeImage().absolutePath, 64, 64)
        state.updateMaskFocus(SelectionShape.Rect(0.1f, 0.1f, 0.4f, 0.4f))
        state.clearMaskFocus()
        assertTrue(
            "状态行历史应当被记下来（新增 ${state.statusHistory.size - before} 条）",
            state.statusHistory.size >= before + 2,
        )
        assertTrue("最后一条要有内容", state.statusHistory.last().text.isNotBlank())
        // 时间是单调的（抽屉按时间排序显示）
        val times = state.statusHistory.map { it.atMillis }
        assertEquals("历史应当是时间递增的", times.sorted(), times)
    }

    @Test
    fun `conversation records what was sent and what came back`() {
        val state = newState()
        state.recordTurn(
            kindKey = "conversation.kind.optimize",
            provider = "llm",
            model = "gpt-4o-mini",
            systemPrompt = "你是提示词助手",
            userPrompt = "把这句话优化成 NAI 标签",
            response = "1girl, smile, best quality",
            durationMs = 1234,
            extra = "temperature 0.3",
        )
        val turn = state.conversation.last()
        assertEquals("conversation.kind.optimize", turn.kindKey)
        assertEquals("llm", turn.provider)
        assertEquals("gpt-4o-mini", turn.model)
        assertEquals("把这句话优化成 NAI 标签", turn.userPrompt)
        assertEquals("1girl, smile, best quality", turn.response)
        assertTrue(turn.ok)
        assertEquals(1234L, turn.durationMs)
    }

    @Test
    fun `an image generation is recorded as a conversation turn too`() {
        val state = newState()
        state.recordTurn(
            kindKey = "conversation.kind.text2img",
            provider = "novelai",
            model = "nai-diffusion-5-full",
            userPrompt = "1girl, solo",
            imageCount = 2,
            response = "出了 2 张图",
            extra = "832×1216 · steps 28 · seed 123",
        )
        val turn = state.conversation.last()
        assertEquals("novelai", turn.provider)
        assertEquals(2, turn.imageCount)
        assertEquals(0, turn.toolsOffered) // tools 还没做：字段在，值恒 0
        assertTrue(turn.extra.contains("832×1216"))
    }

    @Test
    fun `conversation is capped and can be cleared`() {
        val state = newState()
        repeat(ConversationTurn.MAX_TURNS + 20) { index ->
            state.recordTurn(kindKey = "conversation.kind.storyboard", provider = "llm", model = "m$index")
        }
        assertEquals("超出上限要丢最旧的", ConversationTurn.MAX_TURNS, state.conversation.size)
        assertEquals("留下的该是最新的那批", "m${ConversationTurn.MAX_TURNS + 19}", state.conversation.last().model)
        state.clearConversation()
        assertEquals(0, state.conversation.size)
    }

    @Test
    fun `the drawer open state is shared and toggles`() {
        val state = newState()
        assertFalse(state.statusDrawerOpen)
        state.openStatusDrawer()
        assertTrue(state.statusDrawerOpen)
        state.closeStatusDrawer()
        assertFalse(state.statusDrawerOpen)
    }

    @Test
    fun `network logs land in the in-memory buffer and are capped`() {
        clearRecentLogs()
        logInfo("NaiApi", "generate: POST https://image.novelai.net")
        assertEquals(1, recentLogs.size)
        assertEquals('i', recentLogs.last().level)
        assertEquals("NaiApi", recentLogs.last().tag)
        repeat(MAX_LOG_LINES + 10) { index -> logInfo("T", "line $index") }
        assertEquals("日志缓冲要有上限", MAX_LOG_LINES, recentLogs.size)
        clearRecentLogs()
        assertEquals(0, recentLogs.size)
    }
}
