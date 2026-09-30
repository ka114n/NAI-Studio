package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **「跑这一页」逐格清单的钉子**（第 ③ 批：队列逐格生成）。
 *
 * 这里钉的是**确认框与执行器共用的那一份口径**（`ComicRunPlan`）：
 *  1. 顺序 = 生成序号（用户口径「按序号每次一张跑完一页」✓）；
 *  2. 尺寸 = `InpaintSize.forRect`（和格子上只读显示的那个数**同一个来源** ✓）——
 *     否则会出现"确认框写 1792×576、请求发 1024×1024"这种漂移 ✗；
 *  3. 喂给 `ComicCost` 的形状就是那两个数（报账与花钱同源 ✓）；
 *  4. 提示词空着的格子能被挑出来（发出去只会白花钱，跑之前要拦 ✓）。
 */
class ComicRunPlanTest {

    @Test
    fun `清单按生成序号排、尺寸走 InpaintSize`() {
        val panels = listOf(
            // 故意把 order=1 的放在前面：清单必须按**序号**排，不是按列表顺序 ✓
            ComicPanel("b", 0f, 0f, 200f, 200f, prompt = "第二格", order = 1),
            ComicPanel("a", 0f, 0f, 300f, 100f, prompt = "第一格", order = 0),
        )
        val entries = ComicRunPlan.of(panels)

        assertEquals(listOf("a", "b"), entries.map { it.panelId })
        assertEquals(listOf(0, 1), entries.map { it.index })
        // 3:1 的格子 → 1792×576（与格子上只读显示的那个字符串**同一个来源** ✓）
        assertEquals(1792, entries[0].width)
        assertEquals(576, entries[0].height)
        assertEquals("1792×576", panels.first { it.id == "a" }.targetPixelText())
        // 1:1 → 1024×1024
        assertEquals(1024, entries[1].width)
        assertEquals(1024, entries[1].height)
    }

    @Test
    fun `喂给 ComicCost 的尺寸与请求尺寸是同一份`() {
        val entries = ComicRunPlan.of(listOf(ComicPanel("p", 0f, 0f, 100f, 100f, prompt = "x")))
        assertEquals(listOf("p" to (1024 to 1024)), ComicRunPlan.costInput(entries))

        // 价格函数（注入 ✓）拿到的就是这两个数 → 估算与执行不会各算一份 ✗
        val estimate = ComicCost.estimate(ComicRunPlan.costInput(entries)) { w, h ->
            if (w == 1024 && h == 1024) 3 else -1
        }
        assertEquals(3, estimate.total)
        assertEquals(1, estimate.count)
    }

    @Test
    fun `空页没有清单也没有花费`() {
        assertTrue(ComicRunPlan.of(emptyList()).isEmpty())
        assertTrue(ComicRunPlan.costInput(emptyList()).isEmpty())
        assertEquals(0, ComicCost.estimate(ComicRunPlan.costInput(emptyList())) { _, _ -> 9 }.total)
    }

    @Test
    fun `提示词空着的格子能被挑出来（空串和纯空白都算空）`() {
        val entries = ComicRunPlan.of(
            listOf(
                ComicPanel("a", 0f, 0f, 100f, 100f, prompt = "有线", order = 0),
                ComicPanel("b", 0f, 0f, 100f, 100f, prompt = "   ", order = 1),
            ),
        )
        assertEquals(1, ComicRunPlan.blankPrompt(entries)?.index)
        assertNull(ComicRunPlan.blankPrompt(entries.filter { it.panelId == "a" }))
    }

    @Test
    fun `出图路径跟着格子落盘、读回来还在`() {
        val page = ComicBoardPage(
            id = "comic-page-1",
            name = "第 1 页",
            baseWidth = 2048,
            baseHeight = 2048,
            panels = listOf(
                ComicPanel(
                    id = "a",
                    x = 1f,
                    y = 2f,
                    w = 300f,
                    h = 100f,
                    prompt = "一",
                    resultPath = "/tmp/comic-a.png",
                    order = 0,
                ),
            ),
        )
        val restored = ComicBoardStore.decodePages(ComicBoardStore.encodePages(listOf(page))).first()

        assertEquals("/tmp/comic-a.png", restored.panels.first().resultPath)
        // 整个 data class 相等（加了字段也不能把往返搞坏 ✓）
        assertEquals(page.panels, restored.panels)
        // 没跑过的格子读回来还是 null（不写键 → 缺省 null ✓）
        assertNull(ComicPanel("z", 0f, 0f, 64f, 64f).resultPath)
    }
}
