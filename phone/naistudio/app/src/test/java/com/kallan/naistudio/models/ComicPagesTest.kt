package com.kallan.naistudio.models

import com.kallan.naistudio.services.PromptRules
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 狂暴漫画模式（0.2.100）：**多页数据模型**与**分页规划解析**。
 *
 * 这一批的核心不变量只有一条，其它都是它的推论：
 *
 * > `comicPanels` / `comicLayout` / `comicOrder` 是「当前页的活副本」，
 * > `comicPages` 是「全部页」，两者必须**每次写入都一起变**。
 *
 * 一旦破坏它，症状是"翻页再翻回来改动没了""批量出图有几页用的是别页的分格" ——
 * 都是那种跑一次才发现的错，所以这里逐条钉死。
 */
class ComicPagesTest {

    // ---------------------------------------------------------------- 工具

    private fun panel(name: String, prompt: String = "") =
        CharCaptionItem(name = name, prompt = prompt)

    private fun page(
        layout: String,
        vararg names: String,
        summary: String = "",
        order: String = ComicLayout.ORDER_RTL,
    ) = ComicPage(
        layout = layout,
        order = order,
        panels = names.map { panel(it, "prompt of $it") },
        summary = summary,
    )

    private fun extras(
        comicMode: Boolean = true,
        panels: List<CharCaptionItem> = emptyList(),
        layout: String = "v2",
        pages: List<ComicPage> = emptyList(),
        pageIndex: Int = 0,
    ) = GenerateExtras(
        comicMode = comicMode,
        comicPanels = panels,
        comicLayout = layout,
        comicPages = pages,
        comicPageIndex = pageIndex,
    )

    // ---------------------------------------------------------------- 页表补建

    @Test
    fun `角色模式下 ensureComicPages 什么都不做`() {
        val before = extras(comicMode = false, panels = listOf(panel("角色 1")))
        assertEquals(before, before.ensureComicPages())
    }

    @Test
    fun `漫画模式没有页表时补出一页 内容就是当前的活副本`() {
        val before = extras(
            panels = listOf(panel("格 1", "a"), panel("格 2", "b")),
            layout = "v4a",
        )
        val after = before.ensureComicPages()

        assertEquals(1, after.comicPages.size)
        assertEquals("v4a", after.comicPages[0].layout)
        assertEquals(listOf("a", "b"), after.comicPages[0].panels.map { it.prompt })
        assertEquals(0, after.comicPageIndex)
        // 补页不该改动活副本
        assertEquals(before.comicPanels, after.comicPanels)
    }

    @Test
    fun `已有页表时 ensureComicPages 只是把活副本同步回去`() {
        val pages = listOf(page("v2", "格 1", "格 2"), page("v3", "甲", "乙", "丙"))
        // 活副本是第一页，但被改过了（多了一个格）
        val before = extras(
            panels = listOf(panel("格 1", "改过"), panel("格 2"), panel("格 3")),
            pages = pages,
            pageIndex = 0,
        )
        val after = before.ensureComicPages()

        assertEquals(2, after.comicPages.size)
        assertEquals(3, after.comicPages[0].panels.size)
        assertEquals("改过", after.comicPages[0].panels[0].prompt)
        // 第二页原样不动
        assertEquals(3, after.comicPages[1].panels.size)
        assertEquals("甲", after.comicPages[1].panels[0].name)
    }

    // ---------------------------------------------------------------- 镜像写入

    @Test
    fun `漫画模式下 withActiveItems 同时改活副本与页表`() {
        val pages = listOf(page("v2", "格 1", "格 2"), page("v3", "甲", "乙", "丙"))
        val before = extras(panels = pages[0].panels, pages = pages, pageIndex = 0)

        val after = before.withActiveItems(listOf(panel("新 1"), panel("新 2"), panel("新 3")))

        assertEquals(3, after.comicPanels.size)
        assertEquals(3, after.comicPages[0].panels.size)
        assertEquals("新 1", after.comicPages[0].panels[0].name)
        // **另一页不能被碰到** —— 这是"分别储存"在多页下的延续
        assertEquals("甲", after.comicPages[1].panels[0].name)
    }

    @Test
    fun `漫画模式下 withActiveItems 改的是当前页而不是第一页`() {
        val pages = listOf(page("v2", "格 1", "格 2"), page("v3", "甲", "乙", "丙"))
        val before = extras(panels = pages[1].panels, pages = pages, pageIndex = 1)

        val after = before.withActiveItems(listOf(panel("只有一格")))

        assertEquals("格 1", after.comicPages[0].panels[0].name)
        assertEquals("只有一格", after.comicPages[1].panels[0].name)
    }

    @Test
    fun `角色模式下 withActiveItems 完全不碰漫画页表`() {
        val pages = listOf(page("v2", "格 1", "格 2"))
        val before = extras(comicMode = false, pages = pages)

        val after = before.withActiveItems(listOf(panel("角色 1")))

        assertEquals(listOf("角色 1"), after.charCaptions.map { it.name })
        assertEquals(before.comicPages, after.comicPages)
        assertTrue(after.comicPanels.isEmpty())
    }

    @Test
    fun `withComicPage 能同时换版式与阅读顺序`() {
        val pages = listOf(page("v2", "格 1", "格 2"))
        val after = extras(panels = pages[0].panels, pages = pages)
            .withComicPage(layout = "v6a", order = ComicLayout.ORDER_LTR)

        assertEquals("v6a", after.comicLayout)
        assertEquals("v6a", after.comicPages[0].layout)
        assertEquals(ComicLayout.ORDER_LTR, after.comicPages[0].order)
    }

    @Test
    fun `withComicPage 能写概要 且不传时不覆盖已有概要`() {
        val pages = listOf(page("v2", "格 1", "格 2", summary = "原来的概要"))
        val kept = extras(panels = pages[0].panels, pages = pages).withComicPage(layout = "v3")
        assertEquals("原来的概要", kept.comicPages[0].summary)

        val replaced = kept.withComicPage(summary = "新的概要")
        assertEquals("新的概要", replaced.comicPages[0].summary)
    }

    @Test
    fun `没有页表时 withComicPage 只改活副本 不凭空造页表`() {
        val after = extras(panels = emptyList(), pages = emptyList())
            .withComicPage(panels = listOf(panel("格 1")))
        assertTrue(after.comicPages.isEmpty())
        assertEquals(1, after.comicPanels.size)
    }

    // ---------------------------------------------------------------- 翻页

    @Test
    fun `翻页会先把当前页的改动写回页表`() {
        val pages = listOf(page("v2", "格 1", "格 2"), page("v3", "甲", "乙", "丙"))
        // 活副本停在第 0 页，但内容已经被改过
        val before = extras(
            panels = listOf(panel("格 1", "改过")),
            pages = pages,
            pageIndex = 0,
        )

        val after = before.withComicPageAt(1)

        assertEquals(1, after.comicPageIndex)
        // 目标页装进来了
        assertEquals(listOf("甲", "乙", "丙"), after.comicPanels.map { it.name })
        assertEquals("v3", after.comicLayout)
        // **源页的改动留下了** —— 这是这个函数存在的全部理由
        assertEquals("改过", after.comicPages[0].panels[0].prompt)
        assertEquals(1, after.comicPages[0].panels.size)
    }

    @Test
    fun `翻回自己那一页 内容等于刚才改的`() {
        val pages = listOf(page("v2", "格 1", "格 2"), page("v3", "甲", "乙", "丙"))
        val after = extras(panels = listOf(panel("改过")), pages = pages, pageIndex = 0)
            .withComicPageAt(1)
            .withComicPageAt(0)

        assertEquals(0, after.comicPageIndex)
        assertEquals(listOf("改过"), after.comicPanels.map { it.name })
    }

    @Test
    fun `越界的页下标会被夹住 不会崩`() {
        val pages = listOf(page("v2", "格 1", "格 2"), page("v3", "甲", "乙", "丙"))

        assertEquals(1, extras(panels = pages[0].panels, pages = pages).withComicPageAt(99).comicPageIndex)
        assertEquals(0, extras(panels = pages[1].panels, pages = pages, pageIndex = 1).withComicPageAt(-5).comicPageIndex)
    }

    @Test
    fun `comicPageSafeIndex 在没有页表时是 0`() {
        assertEquals(0, extras(pages = emptyList(), pageIndex = 7).comicPageSafeIndex)
        assertEquals(1, extras(pages = listOf(page("v2", "a", "b"), page("v2", "c", "d")), pageIndex = 9).comicPageSafeIndex)
    }

    // ---------------------------------------------------------------- 批量出图快照

    @Test
    fun `forComicPage 取出的是那一页自己的分格与版式`() {
        val pages = listOf(page("v2", "格 1", "格 2"), page("v6a", "甲", "乙", "丙"))
        val base = extras(panels = pages[0].panels, pages = pages, pageIndex = 0)

        val second = base.forComicPage(pages, 1)

        assertEquals("v6a", second.comicLayout)
        assertEquals("v6a", second.comicPages[1].layout)
        assertEquals(listOf("甲", "乙", "丙"), second.comicPanels.map { it.name })
        assertEquals(1, second.comicPageIndex)
        assertTrue(second.comicMode)
    }

    @Test
    fun `forComicPage 不改动原状态`() {
        val pages = listOf(page("v2", "格 1", "格 2"), page("v6a", "甲", "乙", "丙"))
        val base = extras(panels = pages[0].panels, pages = pages, pageIndex = 0)

        base.forComicPage(pages, 1)

        // base 是 data class，immutable —— 这里其实是钉"没有返回同一个实例"
        assertNotEquals(base, base.forComicPage(pages, 1))
        assertEquals(0, base.comicPageIndex)
        assertEquals("v2", base.comicLayout)
    }

    @Test
    fun `forComicPage 越界时原样返回`() {
        val pages = listOf(page("v2", "格 1", "格 2"))
        val base = extras(panels = pages[0].panels, pages = pages)
        assertEquals(base, base.forComicPage(pages, 5))
    }

    // ---------------------------------------------------------------- 持久化

    @Test
    fun `页表能原样从 ComicSettings 的 JSON 回来`() {
        val pages = listOf(
            ComicPage(
                layout = "v4a",
                order = ComicLayout.ORDER_LTR,
                summary = "雨夜街角",
                panels = listOf(
                    CharCaptionItem(
                        name = "格 1",
                        prompt = "city street at night",
                        panelRole = "scene",
                        useCoords = true,
                        x = 0.25,
                        y = 0.75,
                        enabled = false,
                    ),
                ),
            ),
            page("v6a", "甲", "乙", "丙"),
        )
        val settings = ComicSettings(mode = true, pages = pages, pageIndex = 1)

        val back = ComicSettings.fromJson(JSONObject(settings.toJson().toString()))

        assertEquals(2, back.pages.size)
        assertEquals(1, back.pageIndex)
        val first = back.pages[0]
        assertEquals("v4a", first.layout)
        assertEquals(ComicLayout.ORDER_LTR, first.order)
        assertEquals("雨夜街角", first.summary)
        assertEquals(1, first.panels.size)
        assertEquals("scene", first.panels[0].panelRole)
        assertTrue(first.panels[0].useCoords)
        assertEquals(0.25, first.panels[0].x, 1e-9)
        assertEquals(0.75, first.panels[0].y, 1e-9)
        assertEquals(false, first.panels[0].enabled)
        assertEquals(listOf("甲", "乙", "丙"), back.pages[1].panels.map { it.name })
    }

    @Test
    fun `页表为空时 JSON 里根本不写 pages 键`() {
        val json = ComicSettings(mode = true, pages = emptyList()).toJson()
        assertEquals(false, json.has("pages"))
        assertEquals(false, json.has("pageIndex"))
    }

    @Test
    fun `老设置的 JSON 没有 pages 键 读出来是空页表`() {
        // 模拟 0.2.99 之前存下来的 comic_settings_v1
        val legacy = JSONObject(
            """{"mode":true,"layout":"v4a","order":"ltr","stylePrompt":true,"style":"manga page"}""",
        )
        val back = ComicSettings.fromJson(legacy)

        assertTrue(back.mode)
        assertEquals("v4a", back.layout)
        assertEquals(ComicLayout.ORDER_LTR, back.order)
        assertTrue(back.pages.isEmpty())
        assertEquals(0, back.pageIndex)
    }

    @Test
    fun `pages 里的坏元素被丢掉 好的照收`() {
        val json = JSONObject(
            """{"mode":true,"layout":"v2","order":"rtl","stylePrompt":true,"style":"s",
               "pages":[{"layout":"v3","panels":[{"name":"A","positive":"p"}]},{"layout":"v3"}],
               "pageIndex":5}""",
        )
        val back = ComicSettings.fromJson(json)

        // `{"layout":"v3"}` 没有 panels 数组 —— fromJson 收成"空 panels 的页"，仍在表里
        assertEquals(2, back.pages.size)
        assertEquals(1, back.pages[0].panels.size)
        assertTrue(back.pages[1].panels.isEmpty())
        assertEquals(5, back.pageIndex)
    }

    @Test
    fun `toComicSettings 会把页表与下标一起带出去`() {
        val pages = listOf(page("v2", "格 1", "格 2"), page("v3", "甲", "乙", "丙"))
        val settings = extras(panels = pages[1].panels, layout = "v3", pages = pages, pageIndex = 1)
            .toComicSettings()

        assertEquals(2, settings.pages.size)
        assertEquals(1, settings.pageIndex)
        assertEquals("v3", settings.layout)
    }

    // ---------------------------------------------------------------- 分页规划解析

    private fun plan(raw: String, maxPages: Int = ComicStoryboard.DEFAULT_MAX_PAGES) =
        ComicStoryboard.parsePagePlan(raw, maxPages)

    @Test
    fun `规划 标准对象`() {
        val result = plan(
            """
            {"title":"雨夜徽章","style":"manga page, monochrome",
             "pages":[{"summary":"少女回头","layout":"v2"},
                      {"summary":"追过两条街","layout":"v4a"},
                      {"summary":"捡起徽章","layout":"v3"}]}
            """.trimIndent(),
        )

        assertEquals("雨夜徽章", result.title)
        assertEquals("manga page, monochrome", result.style)
        assertEquals(3, result.pages.size)
        assertEquals(listOf("v2", "v4a", "v3"), result.pages.map { it.layout })
        assertEquals(listOf(2, 4, 3), result.pages.map { it.panelCount })
        assertEquals("少女回头", result.pages[0].summary)
    }

    @Test
    fun `规划 能忍受 Markdown 代码块与前言`() {
        val result = plan(
            """
            好的，我把它分成了两页：

            ```json
            {"style":"manga","pages":[{"summary":"开场","layout":"v2"},
            {"summary":"高潮","layout":"v6a"}]}
            ```

            希望对你有帮助。
            """.trimIndent(),
        )
        assertEquals(2, result.pages.size)
        assertEquals("v6a", result.pages[1].layout)
    }

    @Test
    fun `规划 根节点是裸数组也能解析`() {
        val result = plan("""[{"summary":"A","layout":"v2"},{"summary":"B","layout":"v4a"}]""")
        assertEquals(2, result.pages.size)
        assertEquals("", result.style)
        assertEquals("A", result.pages[0].summary)
    }

    @Test
    fun `规划 版式 id 不认识时按格数挑默认版式`() {
        val result = plan("""{"pages":[{"summary":"A","layout":"v99","panels":6}]}""")
        // 6 格 → 通栏主格+五格（不对称优先）
        assertEquals("v6a", result.pages[0].layout)
        assertEquals(6, result.pages[0].panelCount)
    }

    @Test
    fun `规划 auto 不算合法版式 会按格数兜底`() {
        val result = plan("""{"pages":[{"summary":"A","layout":"auto","panels":4}]}""")
        assertEquals("v4a", result.pages[0].layout)
        assertEquals(4, result.pages[0].panelCount)
    }

    @Test
    fun `规划 只给格数不给版式`() {
        val result = plan("""{"pages":[{"summary":"A","panels":3}]}""")
        assertEquals("v3", result.pages[0].layout)
        assertEquals(3, result.pages[0].panelCount)
    }

    @Test
    fun `规划 panels 是数组时按长度算格数`() {
        val result = plan(
            """{"pages":[{"summary":"A","panels":[{"prompt":"x"},{"prompt":"y"}]}]}""",
        )
        assertEquals("v2", result.pages[0].layout)
        assertEquals(2, result.pages[0].panelCount)
    }

    @Test
    fun `规划 格数离谱时夹回 2 到 6`() {
        // 先夹格数再挑版式：99 → 6 → 通栏主格+五格
        assertEquals(6, plan("""{"pages":[{"summary":"A","panels":99}]}""").pages[0].panelCount)
        assertEquals(2, plan("""{"pages":[{"summary":"A","panels":1}]}""").pages[0].panelCount)
        // 负数 / 0 被视为"没给格数"，落到 4 格默认值（与裸字符串那页一致）
        assertEquals(4, plan("""{"pages":[{"summary":"A","panels":-3}]}""").pages[0].panelCount)
        assertEquals(4, plan("""{"pages":[{"summary":"A","panels":0}]}""").pages[0].panelCount)
    }

    @Test
    fun `规划 认中文键名`() {
        val result = plan("""{"pages":[{"概要":"她回头了","版式":"v3"}]}""")
        assertEquals("她回头了", result.pages[0].summary)
        assertEquals("v3", result.pages[0].layout)
    }

    @Test
    fun `规划 一页被写成裸字符串也能收`() {
        val result = plan("""{"pages":["雨夜，少女回头",{"summary":"B","layout":"v2"}]}""")
        assertEquals(2, result.pages.size)
        assertEquals("雨夜，少女回头", result.pages[0].summary)
        // 裸字符串那页没有版式信息 → 兜底 4 格
        assertEquals(4, result.pages[0].panelCount)
    }

    @Test
    fun `规划 超过页数上限时截断`() {
        val raw = (1..10).joinToString(",") { """{"summary":"第 $it 页","layout":"v2"}""" }
        val result = plan("""{"pages":[$raw]}""", maxPages = 3)
        assertEquals(3, result.pages.size)
        assertEquals("第 1 页", result.pages[0].summary)
        assertEquals("第 3 页", result.pages[2].summary)
    }

    @Test
    fun `规划 页数上限本身会被夹进硬护栏`() {
        val raw = (1..40).joinToString(",") { """{"summary":"p$it","layout":"v2"}""" }
        // 传 999 → 夹到 MAX_PAGES
        assertEquals(ComicStoryboard.MAX_PAGES, plan("""{"pages":[$raw]}""", maxPages = 999).pages.size)
        // 传 0 → 夹到 MIN_PAGES，至少留一页
        assertEquals(1, plan("""{"pages":[$raw]}""", maxPages = 0).pages.size)
    }

    @Test
    fun `规划 空 pages 抛错`() {
        assertThrows(ComicStoryboard.StoryboardException::class.java) {
            plan("""{"style":"manga","pages":[]}""")
        }
    }

    @Test
    fun `规划 没有 pages 键抛错`() {
        assertThrows(ComicStoryboard.StoryboardException::class.java) {
            plan("""{"style":"manga","shots":[]}""")
        }
    }

    @Test
    fun `规划 完全没有 JSON 抛错`() {
        assertThrows(ComicStoryboard.StoryboardException::class.java) {
            plan("我建议你先写一个更完整的剧情。")
        }
    }

    @Test
    fun `规划 提示词里的版式表与 ComicLayout 对得上`() {
        // 提示词是给模型看的"合法清单"，ComicLayout 是解析器的真值来源。
        // 两边一旦漂移，模型会照着提示词给一个我们认不出的 id，白跑一趟。
        val rules = PromptRules.COMIC_PAGE_PLAN_RULES
        ComicLayout.templates.forEach { template ->
            if (template.id == ComicLayout.AUTO) return@forEach
            assertTrue(
                "提示词里缺少版式 ${template.id}（${template.name}）",
                rules.contains(template.id),
            )
        }
    }

    @Test
    fun `规划 提示词里的示例自己就能被解析`() {
        // 示例是最容易被模型抄走的一段 —— 它自己必须过得了解析器
        val rules = PromptRules.COMIC_PAGE_PLAN_RULES
        val example = rules.substringAfter("Your output:").trim()
        val result = plan(example)

        assertEquals("雨夜徽章", result.title)
        assertEquals(4, result.pages.size)
        assertEquals(listOf("v2", "v4a", "v3", "v2"), result.pages.map { it.layout })
    }

    @Test
    fun `漫画分镜规则明令不要写风格词`() {
        // 用户要求：漫画分镜规则不要写风格词。
        // 整页画风由工作台统一追加，写进单格只会重复占额度、还和技术风格打架。
        val rules = PromptRules.COMIC_STORYBOARD_RULES
        assertTrue(rules.contains("not what style it is"))
        assertTrue(rules.contains("page-wide"))
        assertTrue(rules.contains("Never write"))
        // 举了例子，模型才知道"风格词"具体指什么
        assertTrue(rules.contains("monochrome"))
        assertTrue(rules.contains("screentone"))
        // 但**镜头语言不算风格词** —— 景别/机位是分镜该管的事，必须写
        assertTrue(rules.contains("camera language is not a style word"))
    }

    // ------------------------------------------------------------------
    // 用户要求：设置里那两段提示词改成**全英文**。
    //
    // "全英文"指的是**给模型的指令**；面板名与每页概要是给用户看的显示字段，
    // 仍然要求中文输出（否则中文界面里会冒出 "Panel 1" 和英文概要）。
    // 下面两条把这个界限钉死。
    // ------------------------------------------------------------------

    @Test
    fun `两段提示词的指令部分没有中文`() {
        val cjk = Regex("[\\u4e00-\\u9fff]")

        // 指令 = 示例之前的部分。示例里带着中文的 name / summary（那是输出数据）
        listOf(
            "分镜规则" to PromptRules.COMIC_STORYBOARD_RULES,
            "分页规则" to PromptRules.COMIC_PAGE_PLAN_RULES,
        ).forEach { (label, rules) ->
            val head = rules.substringBefore("Full example")
            val found = cjk.find(head)
            assertTrue(
                "$label 的指令部分还有中文：「${found?.value}」附近 " +
                    head.substring((found?.range?.first ?: 0).coerceAtLeast(0), 
                        ((found?.range?.last ?: 0) + 22).coerceAtMost(head.length)),
                found == null,
            )
        }
    }

    @Test
    fun `显示字段仍然要求中文输出`() {
        // name（面板名）显示在分镜卡片上，summary（每页概要）显示在页条上
        assertTrue(
            PromptRules.COMIC_STORYBOARD_RULES.contains("display label")
        )
        assertTrue(
            PromptRules.COMIC_PAGE_PLAN_RULES.contains("in Chinese")
        )
    }

    @Test
    fun `两段提示词里都没有 style 输出项`() {
        // 上一条要求的另一半：规则里一旦又出现 style 输出项，模型就会开始改整页风格词。
        // 这条断言是那个约束的看门狗。
        assertFalse(PromptRules.COMIC_STORYBOARD_RULES.contains("\"style\""))
        assertFalse(PromptRules.COMIC_PAGE_PLAN_RULES.contains("\"style\""))
        // 连"style 字段"这种把 style 摆到台面上的写法也不要再出现
        assertFalse(PromptRules.COMIC_STORYBOARD_RULES.contains("style 字段"))
        assertFalse(PromptRules.COMIC_PAGE_PLAN_RULES.contains("style 字段"))
        assertFalse(PromptRules.COMIC_PAGE_PLAN_RULES.contains("style 怎么写"))
        assertFalse(PromptRules.COMIC_STORYBOARD_RULES.contains("style 怎么写"))
    }

    @Test
    fun `解析器仍然容忍多出来的 style 字段`() {
        // 用户可能自己改过提示词，老的自定义规则还会让模型吐 style。
        // 忽略它，但不能因为多了一个键就解析失败。
        val board = ComicStoryboard.parse(
            """{"style":"whatever","panels":[{"name":"格 1","prompt":"a"},{"name":"格 2","prompt":"b"}]}""",
        )
        assertEquals(2, board.panels.size)
        assertEquals("whatever", board.style)
    }

    // ---------------------------------------------------------------- 规则文案迁移

    @Test
    fun `空规则算没改过`() {
        assertTrue(PromptRules.isUntouchedRules("", PromptRules.COMIC_STORYBOARD_RULES))
        assertTrue(PromptRules.isUntouchedRules("   \n ", PromptRules.COMIC_STORYBOARD_RULES))
    }

    @Test
    fun `已经等于当前默认算没改过`() {
        assertTrue(
            PromptRules.isUntouchedRules(
                PromptRules.COMIC_STORYBOARD_RULES,
                PromptRules.COMIC_STORYBOARD_RULES,
            ),
        )
    }

    @Test
    fun `命中历史默认文案的特征串算没改过`() {
        // 0.2.103/104 存到盘上的那两段（含"不要输出 style 字段"）
        val v1Storyboard = """
            Role
            你是一位资深漫画分镜师…
            · **不要输出 style 字段**：整页的画风与排版氛围由工作台统一指定，不归你管。
        """.trimIndent()
        assertTrue(PromptRules.isUntouchedRules(v1Storyboard, PromptRules.COMIC_STORYBOARD_RULES))

        // 更早的 0.2.102 版（含"style 怎么写"一节）
        val v0Storyboard = "Role\n…\nstyle 怎么写\n整页共用的画风与排版氛围，例如：manga page…"
        assertTrue(PromptRules.isUntouchedRules(v0Storyboard, PromptRules.COMIC_STORYBOARD_RULES))

        val v0Plan = """{"style": "整部统一的漫画风格词（英文，逗号分隔）"}"""
        assertTrue(PromptRules.isUntouchedRules(v0Plan, PromptRules.COMIC_PAGE_PLAN_RULES))
    }

    @Test
    fun `用户自己改过的规则不能被升级冲掉`() {
        val custom = "Role\n你是我的私人分镜师，只输出 panels，其它照我说的来。"
        assertFalse(PromptRules.isUntouchedRules(custom, PromptRules.COMIC_STORYBOARD_RULES))
    }

    @Test
    fun `历史特征串不能出现在当前默认文案里`() {
        // 这条防的是"标记串自己漂进新默认文本" —— 一旦漂进去，以后每一次迁移都会
        // 把新版文本误判成"没改过"，用户的手改反而会被反复覆盖。
        PromptRules.COMIC_RULES_LEGACY_MARKERS.forEach { marker ->
            assertFalse(
                "当前分镜规则里不该出现历史标记串：$marker",
                PromptRules.COMIC_STORYBOARD_RULES.contains(marker),
            )
            assertFalse(
                "当前分页规则里不该出现历史标记串：$marker",
                PromptRules.COMIC_PAGE_PLAN_RULES.contains(marker),
            )
        }
    }

    @Test
    fun `规则版本号已推到当前`() {
        // 0 = 初版，1 = 去掉 style 输出项，2 = 明令不写风格词，3 = 整段改英文
        assertEquals(3, PromptRules.COMIC_RULES_VERSION)
        assertTrue(PromptRules.COMIC_RULES_LEGACY_MARKERS.isNotEmpty())
    }

    @Test
    fun `改英文的那次迁移认得出中文旧版`() {
        // 3 版把默认文案从中文换成英文，所以迁移必须认得出**上一版中文默认**
        //（否则老装机永远停在中文 —— 文案是存在盘上的）。
        val v2Storyboard = "Role\n你是一位资深漫画分镜师，同时精通 NovelAI…"
        assertTrue(
            PromptRules.isUntouchedRules(v2Storyboard, PromptRules.COMIC_STORYBOARD_RULES),
        )
        val v2Plan = "Role\n你是一位资深漫画编剧兼分镜师。用户会给你**一整部漫画的全部剧情**…"
        assertTrue(
            PromptRules.isUntouchedRules(v2Plan, PromptRules.COMIC_PAGE_PLAN_RULES),
        )
        // 用户手改过的英文版则不该被覆盖
        val customEnglish = "Role\nYou are my private storyboard artist. Output panels only."
        assertFalse(
            PromptRules.isUntouchedRules(customEnglish, PromptRules.COMIC_STORYBOARD_RULES),
        )
    }

    @Test
    fun `老设置的 comicRulesVersion 读出来是 0 也就是要走一次迁移`() {
        val legacy = AppSettings.fromJson(JSONObject("""{"apiBaseUrl":"https://api.novelai.net"}"""))
        assertEquals(0, legacy.comicRulesVersion)

        val migrated = AppSettings.fromJson(
            JSONObject("""{"apiBaseUrl":"https://api.novelai.net","comicRulesVersion":2}"""),
        )
        assertEquals(2, migrated.comicRulesVersion)
    }

    @Test
    fun `comicRulesVersion 能存能读`() {
        val settings = AppSettings(comicRulesVersion = PromptRules.COMIC_RULES_VERSION)
        val back = AppSettings.fromJson(JSONObject(settings.toJson().toString()))
        assertEquals(PromptRules.COMIC_RULES_VERSION, back.comicRulesVersion)
    }

    @Test
    fun `默认页数上限在硬护栏之内`() {
        assertTrue(
            ComicStoryboard.DEFAULT_MAX_PAGES in
                ComicStoryboard.MIN_PAGES..ComicStoryboard.MAX_PAGES,
        )
    }

    @Test
    fun `备份里的老漫画小节仍然可用 页表缺省为空`() {
        val legacy = JSONObject(
            """{"settings":{"mode":true,"layout":"v2","order":"rtl","stylePrompt":true,"style":"s"},
               "panels":[{"name":"格 1","positive":"a"}]}""",
        )
        val bundle = BackupCodec.ComicBundle.fromJson(legacy)

        assertTrue(bundle != null)
        assertTrue(bundle!!.settings.pages.isEmpty())
        assertEquals(1, bundle.panels.size)
    }

    @Test
    fun `备份带页表时能原样回来`() {
        val pages = listOf(page("v2", "格 1", "格 2", summary = "开场"), page("v6a", "甲", "乙", "丙"))
        val original = BackupCodec.ComicBundle(
            settings = ComicSettings(mode = true, pages = pages, pageIndex = 1),
            panels = pages[1].panels,
        )

        val back = BackupCodec.ComicBundle.fromJson(JSONObject(original.toJson().toString()))

        assertTrue(back != null)
        assertEquals(2, back!!.settings.pages.size)
        assertEquals(1, back.settings.pageIndex)
        assertEquals("开场", back.settings.pages[0].summary)
        assertEquals("v6a", back.settings.pages[1].layout)
        assertEquals(listOf("甲", "乙", "丙"), back.panels.map { it.name })
    }

    @Test
    fun `备份摘要分开报页数 不会把多页漫画说成一页`() {
        val pages = listOf(page("v2", "格 1", "格 2"), page("v6a", "甲", "乙", "丙"))

        val multi = BackupCodec.Bundle(
            comic = BackupCodec.ComicBundle(
                settings = ComicSettings(mode = true, pages = pages),
                panels = pages[0].panels,
            ),
        ).summary

        assertEquals(2, multi.comicPages)
        assertEquals(2, multi.comicPanels)
        assertTrue(multi.sections.contains(BackupCodec.SECTION_COMIC))

        // 单页时代的老备份：页数为 0，只看格数
        val single = BackupCodec.Bundle(
            comic = BackupCodec.ComicBundle(panels = pages[0].panels),
        ).summary
        assertEquals(0, single.comicPages)
        assertEquals(2, single.comicPanels)
        assertTrue(single.sections.contains(BackupCodec.SECTION_COMIC))
    }

    @Test
    fun `只有页表而当前页为空时 备份也不算空`() {
        // 理论上不该出现，但真出现时不能让"导入"把这一节整个跳过
        val summary = BackupCodec.Bundle(
            comic = BackupCodec.ComicBundle(
                settings = ComicSettings(mode = true, pages = listOf(page("v2", "格 1", "格 2"))),
                panels = emptyList(),
            ),
        ).summary

        assertEquals(1, summary.comicPages)
        assertEquals(0, summary.comicPanels)
        assertTrue(summary.sections.contains(BackupCodec.SECTION_COMIC))
    }

    // ---------------------------------------------------------------- 狂暴开关与风格词退回

    @Test
    fun `狂暴开关能存能读`() {
        val settings = ComicSettings(mode = true, berserk = true, styleReverted = true)
        val back = ComicSettings.fromJson(JSONObject(settings.toJson().toString()))

        assertTrue(back.berserk)
        assertTrue(back.styleReverted)
    }

    @Test
    fun `狂暴开关默认关 关闭时就是只跑一页`() {
        // 默认必须是关的：打开它会改变主按钮的语义，悄悄打开很危险
        assertFalse(ComicSettings().berserk)
        val legacy = ComicSettings.fromJson(
            JSONObject("""{"mode":true,"layout":"v2","order":"rtl","stylePrompt":true,"style":"s"}"""),
        )
        assertFalse(legacy.berserk)
    }

    @Test
    fun `toComicSettings 会把狂暴开关带出去`() {
        val on = GenerateExtras(comicMode = true, comicBerserk = true).toComicSettings()
        assertTrue(on.berserk)

        val off = GenerateExtras(comicMode = true).toComicSettings()
        assertFalse(off.berserk)
    }

    @Test
    fun `老设置里没有 styleReverted 标记 读出来是 false 也就是要退一次`() {
        // 这个 false 就是"升级后把被 AI 改过的风格词退回默认"的触发条件。
        // 一旦它默认成 true，存量安装就永远退不回去了。
        val legacy = ComicSettings.fromJson(
            JSONObject("""{"mode":true,"layout":"v2","order":"rtl","stylePrompt":true,"style":"AI 改过的值"}"""),
        )
        assertFalse(legacy.styleReverted)

        // 而退过之后标记为 true，用户手改的值就不会再被覆盖
        val migrated = ComicSettings.fromJson(
            JSONObject(
                """{"mode":true,"layout":"v2","order":"rtl","stylePrompt":true,
                   "style":"用户手改的","styleReverted":true}""",
            ),
        )
        assertTrue(migrated.styleReverted)
        assertEquals("用户手改的", migrated.style)
    }

    @Test
    fun `风格词默认值就是常量`() {
        assertEquals(COMIC_DEFAULT_STYLE, ComicSettings().style)
        assertEquals(COMIC_DEFAULT_STYLE, GenerateExtras().comicStyle)
    }
}
