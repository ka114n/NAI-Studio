package com.kallan.naistudio.services

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 显示用中文标签。
 *
 * 这里锁住一条**关键约定**：界面显示中文，但**查询用的 value 仍是服务端的英文**
 * （发中文给 AnimaDex 会搜不到）。所以映射函数只吃"英文 value + 服务端 label"，返回中文，
 * 不会改动发给服务端的值 —— 拼 URL 的用例在 [AnimaDexProtocolTest] 里。
 */
class AnimaDexLabelsTest {

    @Test
    fun `facet names are translated`() {
        assertEquals("作品", AnimaDexLabels.facet("copyright", "Copyright"))
        assertEquals("发色", AnimaDexLabels.facet("hair_color", "Hair Color"))
        assertEquals("发长", AnimaDexLabels.facet("hair_length", "Hair Length"))
        assertEquals("瞳色", AnimaDexLabels.facet("eye_color", "Eye Color"))
        assertEquals("性别", AnimaDexLabels.facet("gender", "Gender"))
    }

    @Test
    fun `facet values are translated by english value`() {
        assertEquals("蓝", AnimaDexLabels.value("blue hair", "Blue"))
        assertEquals("金", AnimaDexLabels.value("blonde hair", "Blonde"))
        assertEquals("超长", AnimaDexLabels.value("very long hair", "Very Long"))
        assertEquals("女", AnimaDexLabels.value("1girl", "Female"))
        assertEquals("非人类", AnimaDexLabels.value("no humans", "Non-Human"))
        assertEquals("水蓝", AnimaDexLabels.value("aqua eyes", "Aqua"))
    }

    @Test
    fun `unknown values fall back to the server label`() {
        // 服务端以后加了新取值，界面上照原样显示，不会变成空白
        assertEquals("Spring Green", AnimaDexLabels.value("spring green hair", "Spring Green"))
        assertEquals("Whatever", AnimaDexLabels.facet("new_dimension", "Whatever"))
    }

    @Test
    fun `series names are translated by slug with fallback`() {
        assertEquals("原神", AnimaDexLabels.series("genshin_impact", "Genshin Impact"))
        assertEquals("东方 Project", AnimaDexLabels.series("touhou", "Touhou"))
        assertEquals("蔚蓝档案", AnimaDexLabels.series("blue_archive", "Blue Archive"))
        // 没收录的作品照原样显示
        assertEquals("Some New Series", AnimaDexLabels.series("some_new_series", "Some New Series"))
    }

    @Test
    fun `unmapped values never blank out`() {
        // 表里没有的：原样返回服务端给的值/label，绝不返回空串
        assertEquals("unmapped hair", AnimaDexLabels.value("unmapped hair", ""))
        assertEquals("Whatever", AnimaDexLabels.facet("brand_new_dimension", "Whatever"))
        assertEquals("brand_new_slug", AnimaDexLabels.series("brand_new_slug", ""))
    }
}
