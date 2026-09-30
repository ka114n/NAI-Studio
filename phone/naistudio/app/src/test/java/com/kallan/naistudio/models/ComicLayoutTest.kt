package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 漫画版式的纯函数单测。
 *
 * 这些断言是从网页原型 `web/comic-mode/verify.mjs` 逐条搬过来的 ——
 * 版式几何与阅读顺序是最容易"看着对、其实错"的东西，必须机械验证。
 *
 * 特别是三条被真实事故逼出来的：
 *  1. 「上下对半」是两个**上下堆叠的通栏矩形**，不是并排竖条
 *     （早先缩略图按 `ceil(sqrt(格数))` 猜格子大小，2 格算成 cols=2/rows=1 就画错了）；
 *  2. 锚点必须**等于矩形中心**（单一事实源，不许两套算法）；
 *  3. 「主格+三副格」的阅读顺序里**主格是第 1 个**（通高主格的中心 Y 落在中间，
 *     靠 Y 排序会被算成第 3）。
 */
class ComicLayoutTest {

    // -----------------------------------------------------------------------
    // 版式表本身
    // -----------------------------------------------------------------------

    @Test
    fun `除自动版式外每个模板都有固定格数`() {
        val fixed = ComicLayout.templates.filter { it.panelCount != null }
        assertEquals("应该有 10 个固定格数的版式", 10, fixed.size)
        assertNull("auto 不该有固定格数", ComicLayout.panelCountOf(ComicLayout.AUTO))
    }

    @Test
    fun `每个矩形都在页面内且有正面积`() {
        ComicLayout.templates.forEach { template ->
            template.rects?.forEach { rect ->
                assertTrue("${template.id} 的 x 越界", rect.x >= 0.0 && rect.x <= 1.0)
                assertTrue("${template.id} 的 y 越界", rect.y >= 0.0 && rect.y <= 1.0)
                assertTrue("${template.id} 的宽必须为正", rect.w > 0.0)
                assertTrue("${template.id} 的高必须为正", rect.h > 0.0)
                assertTrue("${template.id} 的右边界越界", rect.x + rect.w <= 1.0001)
                assertTrue("${template.id} 的下边界越界", rect.y + rect.h <= 1.0001)
            }
        }
    }

    @Test
    fun `固定格数与矩形数一致`() {
        ComicLayout.templates.forEach { template ->
            template.rects?.let { rects ->
                assertEquals(template.id, rects.size, template.panelCount)
            }
        }
    }

    // -----------------------------------------------------------------------
    // ★ 曾画错的几何
    // -----------------------------------------------------------------------

    @Test
    fun `上下对半是上下两个通栏矩形_不是并排竖条`() {
        val rects = ComicLayout.template("v2").rects!!
        assertEquals(2, rects.size)
        // 两个都几乎通栏宽
        assertTrue("第一格应该通栏：w=${rects[0].w}", rects[0].w > 0.9)
        assertTrue("第二格应该通栏：w=${rects[1].w}", rects[1].w > 0.9)
        // 而且是上下堆叠
        assertTrue("应该是上下关系", rects[0].y + rects[0].h <= rects[1].y + 0.001)
    }

    @Test
    fun `左右对半是左右两个竖长矩形`() {
        val rects = ComicLayout.template("v2h").rects!!
        assertTrue("两格都应该是通高", rects.all { it.h > 0.9 })
        assertTrue("第二格在第一格右边", rects[1].x > rects[0].x + rects[0].w - 0.001)
    }

    @Test
    fun `竖排三格三个矩形都是通栏横条`() {
        val rects = ComicLayout.template("v3v").rects!!
        assertEquals(3, rects.size)
        assertTrue("每格都通栏", rects.all { it.w > 0.9 })
        assertTrue("每格都矮", rects.all { it.h < 0.35 })
    }

    @Test
    fun `主格加三副格的第一格明显大于其余三格`() {
        val rects = ComicLayout.template("v4a").rects!!
        val main = rects[0].area
        rects.drop(1).forEach { assertTrue("主格应该明显更大", main > 2 * it.area) }
    }

    @Test
    fun `锚点等于矩形中心`() {
        val rects = ComicLayout.template("v4").rects!!
        val anchors = ComicLayout.anchorsFor("v4", ComicLayout.ORDER_LTR, 4)
        assertEquals(4, anchors.size)
        val centers = rects.map { it.centerX to it.centerY }
        anchors.forEach { anchor ->
            assertTrue(
                "锚点 $anchor 不在任何矩形中心上：$centers",
                centers.any { it.first == anchor.first && it.second == anchor.second },
            )
        }
    }

    // -----------------------------------------------------------------------
    // 阅读顺序
    // -----------------------------------------------------------------------

    @Test
    fun `田字四格_rtl第一格在上排右侧_ltr第一格在上排左侧`() {
        val rtl = ComicLayout.anchorsFor("v4", ComicLayout.ORDER_RTL, 4)
        val ltr = ComicLayout.anchorsFor("v4", ComicLayout.ORDER_LTR, 4)

        assertTrue("rtl 第一格应该偏右：x=${rtl[0].first}", rtl[0].first > 0.5)
        assertTrue("ltr 第一格应该偏左：x=${ltr[0].first}", ltr[0].first < 0.5)
    }

    @Test
    fun `两种阅读顺序的几何是同一套_只改分配顺序`() {
        val rtl = ComicLayout.anchorsFor("v4", ComicLayout.ORDER_RTL, 4).sortedBy { it.first }
        val ltr = ComicLayout.anchorsFor("v4", ComicLayout.ORDER_LTR, 4).sortedBy { it.first }
        assertEquals("几何集合必须一致", rtl, ltr)
    }

    @Test
    fun `主格版式里主格是第1个_靠Y排序会算成第3`() {
        val rects = ComicLayout.template("v4a").rects!!
        val ordered = ComicLayout.orderedRects("v4a", ComicLayout.ORDER_RTL, 4)
        assertEquals("主格必须排第一", rects[0], ordered[0])

        // 反证：如果按"中心 Y 再 X"排，主格会被挤到中间
        val byCenterY = rects.sortedWith(
            compareBy({ it.centerY }, { -it.centerX }),
        )
        assertTrue("主格不该是中心Y排序的第一名（否则这条断言就没意义）", byCenterY[0] != rects[0])
    }

    @Test
    fun `阅读顺序normalize_只认ltr_其余一律rtl`() {
        assertEquals(ComicLayout.ORDER_LTR, ComicLayout.normalizeOrder("ltr"))
        assertEquals(ComicLayout.ORDER_RTL, ComicLayout.normalizeOrder("rtl"))
        assertEquals(ComicLayout.ORDER_RTL, ComicLayout.normalizeOrder(null))
        assertEquals(ComicLayout.ORDER_RTL, ComicLayout.normalizeOrder(""))
        assertEquals(ComicLayout.ORDER_RTL, ComicLayout.normalizeOrder("乱写的"))
    }

    // -----------------------------------------------------------------------
    // 格数不够 / 超出
    // -----------------------------------------------------------------------

    @Test
    fun `格数少于模板时截断`() {
        assertEquals(3, ComicLayout.anchorsFor("v4", ComicLayout.ORDER_RTL, 3).size)
        assertEquals(1, ComicLayout.anchorsFor("v6", ComicLayout.ORDER_RTL, 1).size)
    }

    @Test
    fun `格数多于模板时回退到自动网格`() {
        val anchors = ComicLayout.anchorsFor("v2", ComicLayout.ORDER_RTL, 9)
        assertEquals(9, anchors.size)
        assertTrue("自动网格的锚点仍在 0~1", anchors.all { it.first > 0 && it.first < 1 && it.second > 0 && it.second < 1 })
    }

    @Test
    fun `autoRects 生成的矩形互不重叠`() {
        listOf(1, 2, 3, 4, 5, 6, 9).forEach { n ->
            val rects = ComicLayout.autoRects(n)
            assertEquals(n, rects.size)
            rects.forEachIndexed { i, a ->
                rects.forEachIndexed { j, b ->
                    if (i != j) {
                        val disjoint = a.x + a.w <= b.x + 1e-9 || b.x + b.w <= a.x + 1e-9 ||
                            a.y + a.h <= b.y + 1e-9 || b.y + b.h <= a.y + 1e-9
                        assertTrue("n=$n 时第 $i 与第 $j 个矩形重叠了", disjoint)
                    }
                }
            }
        }
    }

    @Test
    fun `autoRects 为零或负数时返回空列表`() {
        assertTrue(ComicLayout.autoRects(0).isEmpty())
        assertTrue(ComicLayout.autoRects(-3).isEmpty())
    }

    // -----------------------------------------------------------------------
    // 缩减分格：只删末尾空格
    // -----------------------------------------------------------------------

    @Test
    fun `removableTail_只数末尾的空格`() {
        // 全空 → 都能删
        assertEquals(4, ComicLayout.removableTail(listOf("", "", "", ""), 0))
        // 末尾有内容 → 一个都不能删
        assertEquals(0, ComicLayout.removableTail(listOf("a", "b", "c"), 2))
        // 末尾空、中间有内容 → 删到有内容为止
        assertEquals(1, ComicLayout.removableTail(listOf("a", "b", ""), 1))
        // 纯空白也算空
        assertEquals(2, ComicLayout.removableTail(listOf("a", "  ", "\n"), 1))
        // 目标已达成 → 0
        assertEquals(0, ComicLayout.removableTail(listOf("", ""), 2))
    }

    // -----------------------------------------------------------------------
    // 斜切版式（没有矩形，只有锚点）
    // -----------------------------------------------------------------------

    @Test
    fun `斜切版式有自己的锚点且没有矩形`() {
        val diag = ComicLayout.template("diag")
        assertTrue("斜切版式应该标了 diagonal", diag.diagonal)
        assertNull("斜切版式不该有矩形", diag.rects)
        assertEquals(2, diag.cells.size)

        val anchors = ComicLayout.anchorsFor("diag", ComicLayout.ORDER_RTL, 2)
        assertEquals(2, anchors.size)
        assertTrue(ComicLayout.orderedRects("diag", ComicLayout.ORDER_RTL, 2).isEmpty())
    }

    // -----------------------------------------------------------------------
    // 找不到的 id 要安全回落
    // -----------------------------------------------------------------------

    @Test
    fun `未知版式id回落到默认版式`() {
        assertEquals(ComicLayout.DEFAULT, ComicLayout.template("不存在").id)
        assertEquals(ComicLayout.DEFAULT, ComicLayout.template(null).id)
        assertNotNull(ComicLayout.template("v4a"))
        assertEquals("主格+三副格", ComicLayout.nameOf("v4a"))
    }

    @Test
    fun `每个模板的id都能被nameOf解析`() {
        ComicLayout.templates.forEach { template ->
            assertEquals(template.id, ComicLayout.template(template.id).id)
            assertTrue("${template.id} 的名字不能为空", ComicLayout.nameOf(template.id).isNotBlank())
        }
    }
}
