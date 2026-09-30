package com.kallan.naistudio.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **中英文案一致性**（多线开工时特别有用 ✓）。
 *
 * 背景：`RuntimeText.kt` 里两张表是**文件私有**的 `zhCN` / `enUS` ✗，
 * 测试拿不到它们 ✓ —— 所以这里**直接读源文件**、把两边的键抽出来对比 ✓（零风险 ✓，不动任何源码 ✓）。
 *
 * 钉住的事：**两边键必须一一对应** ✓ —— 少一个键的后果是"英文界面里露中文或空白"✗，
 * 而多线开工时最容易发生的正是"加了 zh 忘了 en"✓。
 *
 * ⚠️ 它依赖仓库目录结构（`naistudio-desktop/` 与 `naistudio-shared/` 同级 ✓ ——
 * 和另一个测试 `LiveInpaintAlphaTest` 一个口径 ✓）；找不到文件就**跳过**而不是红 ✓
 * （免得在别的机器/打包环境里误报 ✗）。
 */
class RuntimeTextParityTest {

    private val source: File = File("../naistudio-shared/src/main/kotlin/com/kallan/naistudio/i18n/RuntimeText.kt")

    /**
     * 抽一张表的键（从 `private val <name> = mapOf(` 那一行开始，到收尾那个 `)` 结束 ✓）。
     *
     * ⚠️⚠️ **必须容忍缩进** ✗ —— 这里原来写的是 `it.startsWith("private val …")` 与
     * `lines[end] != ")"`，而 `RuntimeText.kt` 里两张表**都是缩进 4 格的**
     *（`    private val zhCN = mapOf(` … `    )` ✓）⇒ **两个条件永远不成立** ✗：
     * `start = -1` → 返回空表 → 上面两条测试都走 `if (… isEmpty()) return` **静默跳过** ✗ ——
     * 也就是说**这个守卫从加进来那天起就一次都没真正跑过** ✓（我 2026-09-20 自查时才发现 ✓，
     * 当时的"2/2 绿"是**假的绿** ✗）。现在改成 `trim()` 口径，并且**多断言一条"表不能是空的"** ✓，
     * 让"跳过"变成"看得出来" ✓（非仓库环境仍然允许跳过 ✓，但仓库里 parse 不到就必须红 ✓）。
     */
    private fun keysOf(name: String): List<String> {
        if (!source.isFile) return emptyList()
        val lines = source.readLines()
        val start = lines.indexOfFirst { it.trim().startsWith("private val $name = mapOf(") }
        if (start < 0) return emptyList()
        var end = start + 1
        while (end < lines.size && lines[end].trim() != ")") end++
        val regex = Regex("\"([^\"]+)\"\\s+to\\s")
        return lines.subList(start + 1, end)
            .mapNotNull { regex.find(it)?.groupValues?.get(1) }
    }

    @Test
    fun both_languages_cover_exactly_the_same_keys() {
        val zh = keysOf("zhCN").toSet()
        val en = keysOf("enUS").toSet()
        if (zh.isEmpty() || en.isEmpty()) return   // 找不到源文件（非仓库环境）→ 跳过 ✓

        val missingInEn = (zh - en).sorted()
        val missingInZh = (en - zh).sorted()
        assertTrue(
            "enUS 缺这些键（英文界面会露中文/空白 ✗）：${missingInEn.take(20)}",
            missingInEn.isEmpty(),
        )
        assertTrue(
            "zhCN 缺这些键：${missingInZh.take(20)}",
            missingInZh.isEmpty(),
        )
        assertTrue("两张表不该是空的", zh.size > 100)
    }

    /**
     * **同一张表里不许有重名键** ✓。
     *
     * 为什么值得钉：两张表都是 `mapOf`，**同名键后来者覆盖** ✓ —— 多线开工时
     * 很容易"新功能随手复用了旧功能的键"✗，结果旧界面的文案被**静默**顶掉 ✗，
     * 而且中英两边都覆盖 → 上面那条 parity 测试**照样绿** ✗（查不出来 ✗）。
     * 真实事故：高级漫画模式一开始复用了旧「分格」模式的 `comic.title` /
     * `comic.panelCount` / `comic.panelPromptHint` ✓（已改到 `comic.board.` 前缀 ✓）。
     */
    @Test
    fun no_table_defines_the_same_key_twice() {
        val zh = keysOf("zhCN")
        val en = keysOf("enUS")
        if (zh.isEmpty() || en.isEmpty()) return   // 找不到源文件（非仓库环境）→ 跳过 ✓

        val zhDup = zh.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        val enDup = en.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted()
        // ⚠️ 先钉"真抽到了东西" ✓ —— 抽不到就等于这条守卫又变成空跑 ✗（见 `keysOf` 的 ⚠️ 说明 ✓）
        assertTrue("两张表都没抽到键（解析器坏了 = 这条守卫白跑 ✗）：zh=${zh.size} en=${en.size}", zh.size > 100 && en.size > 100)
        assertTrue("zhCN 里这些键定义了不止一次（后来者覆盖，前面的文案会被静默顶掉 ✗）：$zhDup", zhDup.isEmpty())
        assertTrue("enUS 里这些键定义了不止一次：$enDup", enDup.isEmpty())
    }
}
