package com.kallan.naistudio.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **预设条目自定义**（2026-09-18，用户："在手机上开放预设条目自定义"）。
 *
 * 覆盖纯函数 [withDisabled]（过滤"真正发出去"的条目 + 把查看器的启用状态改成真实状态）
 * 与 [StPresetRef] 的 JSON 往返（停用清单 / 编辑标记 / 原始副本标记）。
 */
class StPresetCustomizeTest {

    private val json = """
        {
          "prompts": [
            {"identifier":"a","name":"A","role":"system","content":"甲"},
            {"identifier":"b","name":"B","role":"system","content":"乙"},
            {"identifier":"chatHistory","name":"H","role":"system","content":"","marker":true},
            {"identifier":"out","name":"O","role":"system","content":"不在 order 里"}
          ],
          "prompt_order": [
            {"character_id":100000,"order":[
              {"identifier":"a","enabled":true},
              {"identifier":"b","enabled":true},
              {"identifier":"chatHistory","enabled":true}
            ]}
          ]
        }
    """.trimIndent()

    @Test
    fun `disabling an entry removes it from the payload but keeps it in the viewer`() {
        val preset = StPreset.parse(json)!!
        val filtered = preset.withDisabled(listOf("b"))
        assertEquals(listOf("a", "chatHistory"), filtered.entries.map { it.identifier })
        assertTrue("查看器里还在", filtered.allEntries.any { it.identifier == "b" })
        assertEquals(
            "查看器里显示为停用",
            false,
            filtered.allEntries.first { it.identifier == "b" }.enabled,
        )
        assertEquals("其它条目照旧启用", true, filtered.allEntries.first { it.identifier == "a" }.enabled)
    }

    @Test
    fun `entries outside prompt_order show as disabled`() {
        val preset = StPreset.parse(json)!!
        val filtered = preset.withDisabled(listOf("a"))
        assertFalse(
            "不在 order 里的条目，查看器应显示为停用",
            filtered.allEntries.first { it.identifier == "out" }.enabled,
        )
    }

    @Test
    fun `empty disabled list returns the very same instance`() {
        val preset = StPreset.parse(json)!!
        assertSame(preset, preset.withDisabled(emptyList()))
    }

    @Test
    fun `markers can be disabled too`() {
        val preset = StPreset.parse(json)!!
        val filtered = preset.withDisabled(listOf("chatHistory"))
        assertTrue(filtered.entries.none { it.identifier == "chatHistory" })
    }

    @Test
    fun `an entry disabled by the preset file can be turned back on`() {
        // "能关不能开"就是这里：文件里 order 写着 enabled=false
        val offline = """
            {
              "prompts": [{"identifier":"a","name":"A","content":"甲"},{"identifier":"b","name":"B","content":"乙"}],
              "prompt_order": [{"character_id":100000,"order":[
                {"identifier":"a","enabled":true},
                {"identifier":"b","enabled":false}
              ]}]
            }
        """.trimIndent()
        val preset = StPreset.parse(offline)!!
        assertTrue("默认不发", preset.entries.none { it.identifier == "b" })
        assertTrue("但它在 order 里（有位置）", preset.allEntries.first { it.identifier == "b" }.inOrder)
        val on = preset.withOverrides(listOf("b"), emptyList())
        assertEquals(listOf("a", "b"), on.entries.map { it.identifier })
        assertTrue(on.allEntries.first { it.identifier == "b" }.enabled)
    }

    @Test
    fun `an entry outside prompt_order is appended at the end when enabled`() {
        val on = StPreset.parse(json)!!.withOverrides(listOf("out"), emptyList())
        assertEquals(listOf("a", "b", "chatHistory", "out"), on.entries.map { it.identifier })
    }

    @Test
    fun `explicit disable wins over explicit enable`() {
        val both = StPreset.parse(json)!!.withOverrides(listOf("a"), listOf("a"))
        assertTrue(both.entries.none { it.identifier == "a" })
    }

    @Test
    fun `preset ref survives a json round trip with overrides`() {
        val ref = StPresetRef(
            id = "st-1",
            name = "我的预设",
            fileName = "st-1.json",
            entryCount = 17,
            estimatedTokens = 1706,
            disabledEntries = listOf("a", "b"),
            enabledEntries = listOf("c"),
            hasEdits = true,
            hasOriginal = true,
        )
        val back = StPresetRef.fromJson(ref.toJson())
        assertEquals(ref, back)
        assertEquals(listOf("a", "b"), back.disabledEntries)
        assertEquals(listOf("c"), back.enabledEntries)
        assertTrue(back.hasEdits)
        assertTrue(back.hasOriginal)
    }

    @Test
    fun `older ref json without the new fields still loads`() {
        val legacy = org.json.JSONObject("""{"id":"st-2","name":"旧","fileName":"st-2.json","entryCount":5}""")
        val ref = StPresetRef.fromJson(legacy)
        assertEquals("st-2", ref.id)
        assertTrue("缺字段就是空清单", ref.disabledEntries.isEmpty())
        assertFalse(ref.hasEdits)
        assertFalse(ref.hasOriginal)
    }
}
