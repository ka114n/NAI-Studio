package com.kallan.naistudio.services

/**
 * AnimaDex 的**显示用中文标签**。
 *
 * ⚠️ 关键约定：服务端的 `value` 是英文（`blue hair`、`vocaloid`、`1girl`…），
 * **查询时必须原字面发英文**（发中文会搜不到）；这里只把 `label` 换成中文给人看。
 * 所以这一层是"纯显示映射"，查不到的按服务端给的 label 回显。
 */
object AnimaDexLabels {

    /** 筛选维度名。 */
    private val FACETS = mapOf(
        "copyright" to "作品",
        "hair_color" to "发色",
        "hair_length" to "发长",
        "eye_color" to "瞳色",
        "gender" to "性别",
        "character" to "角色",
    )

    /**
     * 取值名（按服务端的 `value` 英文做键）。
     * 同一个英文在不同维度里含义一致（Brown 发色/瞳色都是"棕"），所以共用一张表。
     */
    private val VALUES = mapOf(
        // 发色
        "black hair" to "黑", "brown hair" to "棕", "blonde hair" to "金", "white hair" to "白",
        "blue hair" to "蓝", "grey hair" to "灰", "pink hair" to "粉", "purple hair" to "紫",
        "red hair" to "红", "green hair" to "绿", "streaked hair" to "挑染", "two-tone hair" to "双色",
        "orange hair" to "橙", "gradient hair" to "渐变", "multicolored hair" to "多彩",
        "aqua hair" to "水蓝", "light brown hair" to "浅棕", "silver hair" to "银",
        "light blue hair" to "浅蓝", "split-color hair" to "分裂色", "light purple hair" to "浅紫",
        "dark blue hair" to "深蓝", "light green hair" to "浅绿",
        // 发长
        "very short hair" to "超短", "short hair" to "短", "medium hair" to "中",
        "long hair" to "长", "very long hair" to "超长", "absurdly long hair" to "极长",
        // 瞳色
        "blue eyes" to "蓝", "red eyes" to "红", "brown eyes" to "棕", "green eyes" to "绿",
        "purple eyes" to "紫", "yellow eyes" to "黄", "pink eyes" to "粉", "black eyes" to "黑",
        "grey eyes" to "灰", "orange eyes" to "橙", "aqua eyes" to "水蓝",
        "multicolored eyes" to "多彩", "two-tone eyes" to "双色", "gradient eyes" to "渐变",
        // 性别
        "1girl" to "女", "1boy" to "男", "no humans" to "非人类", "1other" to "不明",
        // 常见作品（按 slug；没收录的按服务端给的 label 显示）
        "original" to "原创",
        "pokemon" to "宝可梦",
        "touhou" to "东方 Project",
        "vocaloid" to "Vocaloid",
        "azur_lane" to "碧蓝航线",
        "arknights" to "明日方舟",
        "genshin_impact" to "原神",
        "blue_archive" to "蔚蓝档案",
        "kantai_collection" to "舰队 Collection",
        "hololive" to "Hololive",
        "nijisanji" to "彩虹社",
        "girls'_frontline" to "少女前线",
        "final_fantasy" to "最终幻想",
        "umamusume" to "赛马娘",
        "gundam" to "高达",
        "idolmaster" to "偶像大师",
        "fire_emblem" to "火焰纹章",
        "league_of_legends" to "英雄联盟",
        "honkai_(series)" to "崩坏系列",
        "precure" to "光之美少女",
        "jojo_no_kimyou_na_bouken" to "JOJO 的奇妙冒险",
        "one_piece" to "海贼王",
        "tales_of_(series)" to "传说系列",
        "digimon" to "数码宝贝",
        "granblue_fantasy" to "碧蓝幻想",
        "kemono_friends" to "兽娘动物园",
        "indie_virtual_youtuber" to "独立 VTuber",
        "yu-gi-oh!" to "游戏王",
        "mahou_shoujo_madoka_magica" to "魔法少女小圆",
    )

    /** 维度名（查不到就返回服务端给的 label）。 */
    fun facet(key: String, fallback: String): String = FACETS[key] ?: fallback.ifBlank { key }

    /** 取值名：先按服务端 `value`（英文）查，查不到再按 label 查，仍没有就原样显示。 */
    fun value(value: String, fallback: String): String =
        VALUES[value.lowercase()] ?: VALUES[fallback.lowercase()] ?: fallback.ifBlank { value }

    /** 作品名（cards 用 slug 作键）。 */
    fun series(slug: String, fallback: String): String =
        VALUES[slug.lowercase()] ?: fallback.ifBlank { slug }
}
