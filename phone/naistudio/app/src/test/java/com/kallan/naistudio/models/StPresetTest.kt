package com.kallan.naistudio.models

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * **SillyTavern 预设解析**的单测（2026-09-17，手机线先行）。
 *
 * 最后一组用例会去读**真实的那份 415 KB 预设**（`TGbreak😺V3.1.2.json`）——
 * 文件不在本机时自动跳过（`assumeTrue`），所以别的机器上跑也不会红。
 */
class StPresetTest {

    /** 合成样例：够覆盖 order / enabled / marker / role / 位置 / depth / order 优先级。 */
    private val sample = """
        {
          "temperature": 1.0,
          "top_p": 0.9,
          "frequency_penalty": 0.2,
          "presence_penalty": 0.1,
          "openai_max_tokens": 9500,
          "top_k": 40,
          "repetition_penalty": 1.05,
          "prompts": [
            {"identifier":"main","name":"Main","role":"system","content":"主提示词 {{user}}","enabled":true,"system_prompt":true},
            {"identifier":"worldInfoBefore","name":"WI","role":"system","content":"","enabled":true,"marker":true},
            {"identifier":"chatHistory","name":"History","role":"system","content":"","enabled":true,"marker":true},
            {"identifier":"jb","name":"Jailbreak","role":"system","content":"越狱段落 {{setvar::cot::开}}之后 {{getvar::cot}}","enabled":true,"injection_position":1,"injection_depth":0,"injection_order":50},
            {"identifier":"deep","name":"Depth","role":"system","content":"深度注入","enabled":true,"injection_position":0,"injection_depth":4,"injection_order":100},
            {"identifier":"off","name":"Disabled","role":"system","content":"不该出现","enabled":false},
            {"identifier":"not-in-order","name":"NotInOrder","role":"system","content":"不在 order 里就不发","enabled":true},
            {"identifier":"prefill","name":"Prefill","role":"assistant","content":"好的","enabled":true}
          ],
          "prompt_order": [
            {"character_id":100000,"order":[
              {"identifier":"main","enabled":true},
              {"identifier":"worldInfoBefore","enabled":true},
              {"identifier":"jb","enabled":true},
              {"identifier":"deep","enabled":true},
              {"identifier":"off","enabled":true},
              {"identifier":"chatHistory","enabled":true},
              {"identifier":"prefill","enabled":true}
            ]}
          ]
        }
    """.trimIndent()

    @Test
    fun `parses sampler params and keeps non-standard ones aside`() {
        val preset = StPreset.parse(sample) ?: error("解析失败")
        assertEquals(1.0, preset.temperature!!, 1e-9)
        assertEquals(0.9, preset.topP!!, 1e-9)
        assertEquals(0.2, preset.frequencyPenalty!!, 1e-9)
        assertEquals(0.1, preset.presencePenalty!!, 1e-9)
        assertEquals(9500, preset.maxTokens)
        // 非 OpenAI 标准字段单独留着（要塞 extra_parameters）
        assertEquals(40.0, preset.extraSamplers["top_k"]!!, 1e-9)
        assertEquals(1.05, preset.extraSamplers["repetition_penalty"]!!, 1e-9)
    }

    @Test
    fun `keeps only prompts listed in prompt_order, in that order`() {
        val preset = StPreset.parse(sample)!!
        // ⚠️ `enabled` 以 **prompt_order 里那条**为准（PromptManager.js:449 切换写的就是 order 条目、
        // 1196-1198 过滤也读 order 条目）—— 所以样例里 `off` 虽然在 prompts[] 里是 false，
        // 但 order 里写的是 true → 它**会**被发出去（这正是 ST 的行为）
        assertEquals(
            listOf("main", "worldInfoBefore", "jb", "deep", "off", "chatHistory", "prefill"),
            preset.entries.map { it.identifier },
        )
        // 不在 order 里的那条即使 enabled 也不发（ST 口径）
        assertTrue(preset.entries.none { it.identifier == "not-in-order" })
        // allEntries 仍然保留全部（给查看器）
        assertTrue(preset.allEntries.any { it.identifier == "not-in-order" })
    }

    @Test
    fun `order entry can switch a prompt off`() {
        val json = sample.replace("""{"identifier":"deep","enabled":true}""", """{"identifier":"deep","enabled":false}""")
        val preset = StPreset.parse(json)!!
        assertTrue(preset.entries.none { it.identifier == "deep" })
    }

    @Test
    fun `positions depths and roles survive parsing`() {
        val preset = StPreset.parse(sample)!!
        val jb = preset.entries.first { it.identifier == "jb" }
        assertEquals(StPreset.POSITION_ABSOLUTE, jb.position)
        assertEquals(50, jb.order)
        val deep = preset.entries.first { it.identifier == "deep" }
        assertEquals(StPreset.POSITION_RELATIVE, deep.position)
        assertEquals(4, deep.depth)
        assertEquals(100, deep.order)
        val prefill = preset.entries.first { it.identifier == "prefill" }
        assertEquals("assistant", prefill.role)
    }

    @Test
    fun `marker entries are recognised and excluded from the payload`() {
        val preset = StPreset.parse(sample)!!
        assertTrue(preset.entries.first { it.identifier == "chatHistory" }.marker)
        assertTrue(preset.payloadEntries.none { it.marker })
        // 有正文的是这 5 条（两条 marker 没有正文；`off` 因为 order 里 enabled=true 所以也在）
        assertEquals(
            listOf("main", "jb", "deep", "off", "prefill"),
            preset.payloadEntries.map { it.identifier },
        )
    }

    @Test
    fun `defaults are applied when fields are missing`() {
        val json = """{"prompts":[{"identifier":"a","content":"x"}],"prompt_order":[{"character_id":100000,"order":[{"identifier":"a"}]}]}"""
        val preset = StPreset.parse(json)!!
        val entry = preset.entries.single()
        assertEquals("system", entry.role)
        assertEquals(StPreset.POSITION_RELATIVE, entry.position)
        assertEquals(StPreset.DEFAULT_DEPTH, entry.depth)
        assertEquals(StPreset.DEFAULT_ORDER, entry.order)
        assertTrue(entry.enabled)
        assertNull("没有的采样参数就是 null", preset.temperature)
    }

    @Test
    fun `junk input is rejected instead of crashing`() {
        assertNull(StPreset.parse("不是 JSON"))
        assertNull(StPreset.parse("{}"))
        assertNull(StPreset.parse("""{"prompts":[]}"""))
    }

    @Test
    fun `macros expand user char setvar and getvar`() {
        val vars = mutableMapOf<String, String>()
        val first = StPreset.expandMacros("你好 {{user}}，我是 {{char}}。{{setvar::cot::开启}}", StPreset.MacroNames("玩家", "旁白"), vars)
        assertEquals("你好 玩家，我是 旁白。", first.text)
        assertEquals(1, first.variables)
        val second = StPreset.expandMacros("当前状态：{{getvar::cot}}", StPreset.MacroNames("玩家", "旁白"), vars)
        assertEquals("当前状态：开启", second.text)
    }

    @Test
    fun `unknown macros are stripped and counted`() {
        val out = StPreset.expandMacros("A{{random::1,2}}B{{unknown}}C")
        assertEquals("ABC", out.text)
        assertEquals(2, out.unknownMacros)
    }

    @Test
    fun `triple braces are normalised`() {
        val vars = mutableMapOf("x" to "值")
        val out = StPreset.expandMacros("{{{getvar::x}}", StPreset.MacroNames(), vars)
        assertEquals("值", out.text)
    }

    @Test
    fun `token estimate counts only payload text`() {
        val preset = StPreset.parse(sample)!!
        assertEquals(preset.payloadEntries.sumOf { it.content.length }, preset.charCount)
        assertTrue(preset.estimatedTokens in 1..100)
    }

    @Test
    fun `expandAll shares one variable table across entries`() {
        val preset = StPreset.parse(sample)!!
        val (entries, info) = StPreset.expandAll(preset.entries)
        val jb = entries.first { it.identifier == "jb" }
        assertEquals("越狱段落 之后 开", jb.content)
        assertEquals(0, info.unknownMacros)
    }

    // ------------------------------------------------------------------ 真文件

    private fun realPresetFile(): File? {
        val candidates = listOf(
            File(
                System.getProperty("user.home") +
                    "\\AppData\\Roaming\\open-deepseek-harness-desktop\\dsh-home\\attachments\\v1\\files" +
                    "\\2a\\2aa0f252446357237b697637b14db5e065775ae6949db8f86c73edd113cadb8f\\TGbreak😺V3.1.2.json",
            ),
            File(System.getenv("TEMP") ?: "", "TGbreak😺V3.1.2.json"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    @Test
    fun `parses the real 415 KB preset`() {
        val file = realPresetFile()
        assumeTrue("真实预设不在本机，跳过", file != null)
        val preset = StPreset.parse(file!!.readText(), file.name)
        assertNotNull(preset)
        preset!!
        // 结构量级（用范围而不是硬编码，换版本也不会假报错）
        assertTrue("条目数应在 100~200", preset.allEntries.size in 100..200)
        assertTrue("启用条目应在 40~90", preset.entries.size in 40..90)
        assertTrue("正文规模应在 1 万~3 万字符", preset.charCount in 10_000..30_000)
        assertTrue("估算 token 应在 3k~10k", preset.estimatedTokens in 3_000..10_000)
        // 采样参数
        assertEquals(1.0, preset.temperature!!, 1e-9)
        assertEquals(0.9, preset.topP!!, 1e-9)
        assertEquals(9500, preset.maxTokens)
        // 8 个 marker 都在，且一个都不进 payload
        assertEquals(8, preset.entries.count { it.marker })
        assertTrue(preset.payloadEntries.none { it.marker })
        // 宏展开：不该再剩下 {{...}}
        val (expanded, info) = StPreset.expandAll(preset.payloadEntries)
        assertTrue("展开后不应还有未处理宏", expanded.none { it.content.contains("{{") })
        assertTrue("不该有大量未知宏（说明宏引擎漏了常见宏）", info.unknownMacros < 40)
    }

    /**
     * 「只保留开启的片段，其余删除」（用户 2026-09-18）：导入时就把关着的条目从 JSON 里删掉。
     *
     * 口径与 [StPreset.parse] 一致 —— **顺序表说了算**：不在顺序里的（哪怕它自己写着
     * `enabled=true`）也删，因为 ST 本来就不会发它们。
     */
    @Test
    fun `pruneToEnabled 只留开着的条目，其余真的从 JSON 里删掉`() {
        val json = """
            {
              "temperature": 1.0,
              "openai_max_tokens": 9500,
              "prompts": [
                {"identifier":"a","name":"开着的","content":"A","enabled":true},
                {"identifier":"b","name":"关着的","content":"B","enabled":false},
                {"identifier":"c","name":"不在顺序里","content":"C","enabled":true}
              ],
              "prompt_order": [{"character_id":100000,"order":[
                {"identifier":"a","enabled":true},
                {"identifier":"b","enabled":false},
                {"identifier":"c","enabled":false}
              ]}]
            }
        """.trimIndent()

        val pruned = StPreset.pruneToEnabled(json)
        val preset = StPreset.parse(pruned)!!
        assertEquals("只该剩 1 条", 1, preset.allEntries.size)
        assertEquals("a", preset.allEntries.first().identifier)
        assertTrue(preset.allEntries.first().enabled)
        assertEquals(1, preset.entries.size)
        // 其它顶层字段原样保留（采样参数不能因为瘦身丢了）
        assertEquals(1.0, preset.temperature!!, 1e-9)
        assertEquals(9500, preset.maxTokens)
        // 关掉的条目连名字都不该再出现
        assertFalse(pruned.contains("关着的"))
        assertFalse(pruned.contains("不在顺序里"))
        assertTrue(pruned.contains("开着的"))
    }

    /** 一条都没开着时**原样返回**：宁可不动，也不能把文件删成空的。 */
    @Test
    fun `pruneToEnabled 全关着或坏文件都原样返回`() {
        val allOff = """{"prompts":[{"identifier":"a","content":"A","enabled":false}],"prompt_order":[{"order":[{"identifier":"a","enabled":false}]}]}"""
        assertEquals(allOff, StPreset.pruneToEnabled(allOff))
        val broken = "{ 不是 JSON"
        assertEquals(broken, StPreset.pruneToEnabled(broken))
    }

    /**
     * `pruneToIds`：**按给定的 id 留**，而且**不改任何开关**。
     *
     * 用户 2026-09-18 报："预设条目点删除后全部会自动打开" —— 根因就是这里以前把留下的条目
     * 一律写成 `enabled=true` ✗：对"开关是烘在文件里"的预设（比如 6 开 / 119 关），
     * 删掉任意一条就会把剩下那些**全打开**。
     */
    @Test
    fun `pruneToIds 只按 id 过滤，开关原样保留`() {
        val json = pruneSample()
        val slim = StPreset.pruneToIds(json, setOf("a", "b", "c"))
        val preset = StPreset.parse(slim)!!
        assertEquals(listOf("a", "b", "c"), preset.allEntries.map { it.identifier })
        assertTrue("a 本来是开的", preset.allEntries.first { it.identifier == "a" }.enabled)
        assertFalse(
            "b 本来是关的 —— 删别的条目不该把它打开",
            preset.allEntries.first { it.identifier == "b" }.enabled,
        )
        // 真正会发出去的仍然只有 a
        assertEquals(listOf("a"), preset.entries.map { it.identifier })
        assertFalse(slim.contains("压根没开"))
        assertEquals(1.0, preset.temperature!!, 1e-9)
        // 空集合 = 原样返回（不删空）
        assertEquals(json, StPreset.pruneToIds(json, emptySet()))
    }

    /** 需要时（"删掉所有关着的"那条路）能把**不在顺序里**的接回目标分组末尾，保住用户开的 extras。 */
    @Test
    fun `pruneToIds 可以要求把不在顺序里的接回顺序表`() {
        val json = pruneSample()
        val slim = StPreset.pruneToIds(json, setOf("a", "c"), appendMissingToOrder = true)
        val preset = StPreset.parse(slim)!!
        assertTrue(preset.orderedIds.contains("c"))
        assertTrue("接回去的是开着的", preset.allEntries.first { it.identifier == "c" }.enabled)
        assertEquals(listOf("a", "c"), preset.entries.map { it.identifier })
    }

    /** 共用样例：a 开、b 关、c 不在顺序里、d 压根没开。 */
    private fun pruneSample() = """
        {
          "temperature": 1.0,
          "prompts": [
            {"identifier":"a","name":"开着的","content":"A","enabled":true},
            {"identifier":"b","name":"关着的","content":"B","enabled":false},
            {"identifier":"c","name":"不在顺序里","content":"C","enabled":true},
            {"identifier":"d","name":"压根没开","content":"D","enabled":false}
          ],
          "prompt_order": [{"character_id":100000,"order":[
            {"identifier":"a","enabled":true},
            {"identifier":"b","enabled":false}
          ]}]
        }
    """.trimIndent()
}
