package com.kallan.naistudio.models

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * **一天的使用统计**（用户 2026-09-23 点的单 ✓）：
 * 「**记录每天跑图的数量、消耗的积分、tag 的字数**」✓。
 *
 * @param date 哪一天（`yyyy-MM-dd` ✓ —— **本地日期** ✓：用户看的是"我今天跑了多少" ✓）
 * @param images 这天**成功出图**的张数 ✓（按落库那张算，不是按请求次数 ✓：
 *   一次请求回两张 ZIP 多图时是 2 张 ✓）
 * @param anlas 这天**实扣的点数**（拿服务端余额前后差算 ✓，拿不到就记 0 ✓ —— **绝不编** ✗）
 * @param tagChars 这天**提示词的字符数**（正面 + 负面 ✓，按出图那一刻的提示词记 ✓）
 */
data class UsageStat(
    val date: String,
    val images: Int = 0,
    val anlas: Int = 0,
    val tagChars: Int = 0,
)

/**
 * **每日使用统计表**（纯逻辑 ✓ 不碰平台 ✓ 可单测 ✓，见 `docs/76` ✓）。
 *
 * ## 为什么按天存、而且**只增不减**
 *
 * 用户要的是那张"月度贡献表"式的格子图 ✓ ⇒ 需要**按天**的计数 ✓；
 * 而统计这东西"事后补不回来" ✗（余额差、提示词都在那一刻才有意义 ✓）⇒
 * 每次出图**就地累加**一份小数据 ✓（几十字节 / 天 ✓，比存历史便宜得多 ✓）。
 *
 * ## 三条口径
 *
 *  1. **一天一条** ✓（同一个 key 累加 ✓，不是追加 ✓）；
 *  2. **只保留最近 [KEEP_DAYS] 天** ✓ —— 格子上最多看一年 ✓，
 *     再老的清掉 ✓（不清的话 prefs 会一直长 ✓，而那张格子图一个字都多不出来 ✓）；
 *  3. 临时图库不参与 ✗：统计的是**真跑出来的图** ✓（`persistGeneratedImages` 那一刻 ✓）。
 */
class UsageStats(val days: Map<String, UsageStat> = emptyMap()) {

    /** 这天有没有记录 ✓。 */
    fun of(date: String): UsageStat? = days[date]

    /**
     * **记一笔** ✓（出图后调用 ✓）。
     *
     * @param date `yyyy-MM-dd`（本地日期 ✓）
     * @param images 这次出图张数 ✓（≤0 就只记点数 / 字数 ✓ —— 失败的请求也烧点数 ✓）
     * @param anlas 这次实扣点数 ✓
     * @param tagChars 这次提示词字符数 ✓
     */
    fun plus(date: String, images: Int, anlas: Int, tagChars: Int): UsageStats {
        val before = days[date] ?: UsageStat(date)
        val after = before.copy(
            images = before.images + images.coerceAtLeast(0),
            anlas = before.anlas + anlas.coerceAtLeast(0),
            tagChars = before.tagChars + tagChars.coerceAtLeast(0),
        )
        return UsageStats(prune(days - date + (date to after)))
    }

    /** 把太久以前的删掉 ✓（见 [KEEP_DAYS] ✓）。 */
    private fun prune(map: Map<String, UsageStat>): Map<String, UsageStat> {
        if (map.size <= KEEP_DAYS) return map
        return map.entries
            .sortedByDescending { it.key } // `yyyy-MM-dd` 的字典序 == 时间序 ✓（所以不用解析日期 ✓）
            .take(KEEP_DAYS)
            .associate { it.key to it.value }
    }

    /** 序列化：`"2026-09-23:12,345,678;2026-09-22:3,10,90"` ✓（紧凑、纯文本、好读 ✓）。 */
    fun encode(): String = days.entries
        .sortedBy { it.key }
        .joinToString(";") { (date, s) -> "$date:${s.images},${s.anlas},${s.tagChars}" }

    companion object {
        /** 格子上最多看一年 ✓（与 GitHub 那张图同量级 ✓）。 */
        const val KEEP_DAYS = 400

        /**
         * 反序列化 ✓。
         *
         * ⚠️ **认不出来的条目一律跳过** ✗（不抛异常 ✓）：这一份是"统计"，坏一条不该拖垮启动 ✓
         *（`prefs.json` 里它旁边还有历史、风格库那些要紧东西 ✓）。
         */
        fun decode(raw: String?): UsageStats {
            if (raw.isNullOrBlank()) return UsageStats()
            val parsed = LinkedHashMap<String, UsageStat>()
            raw.split(';').forEach { entry ->
                val piece = entry.trim()
                if (piece.isEmpty()) return@forEach
                val colon = piece.indexOf(':')
                if (colon <= 0) return@forEach
                val date = piece.substring(0, colon)
                val nums = piece.substring(colon + 1).split(',')
                if (nums.size < 3) return@forEach
                val images = nums[0].trim().toIntOrNull() ?: return@forEach
                val anlas = nums[1].trim().toIntOrNull() ?: return@forEach
                val tags = nums[2].trim().toIntOrNull() ?: return@forEach
                parsed[date] = UsageStat(date, images, anlas, tags)
            }
            return UsageStats(parsed)
        }
    }
}

/**
 * **那张格子图的一格**（纯数据 ✓，界面只负责画 ✓）。
 *
 * @param date `yyyy-MM-dd` ✓
 * @param day 这一天是几号（**新口径：不按星期排，直接按月里的第几天排** ✓，用户 2026-09-24 ✓）
 * @param level 0..4 ✓（0 = 那天没出图 ✓ —— 界面据此上色 ✓）
 */
data class UsageCell(val date: String, val level: Int, val day: Int = 0)

/**
 * **把一天的记录翻成"格子等级"** ✓（GitHub 那张月度贡献表的同一个口径 ✓）：
 * 0 张 = 0 级 ✓、1 张 = 1 级 ✓、2–3 张 = 2 级 ✓、4–7 张 = 3 级 ✓、≥8 张 = 4 级 ✓。
 *
 * ⚠️ 用户口径是「**随着这天跑的图越多，格子越绿**」✓ ⇒ 等级只由**张数**决定 ✓，
 * 点数 / tag 字数**不参与上色** ✓（它们在同一天的悬浮明细里显示 ✓ —— 一个格子只能表达一个量 ✓）。
 */
fun usageLevel(images: Int): Int = when {
    images <= 0 -> 0
    images == 1 -> 1
    images <= 3 -> 2
    images <= 7 -> 3
    else -> 4
}

/**
 * **排出一整年的格子**（按周分列 ✓，和 GitHub 那张图一样从左到右一周一列 ✓）。
 *
 * ⚠️ 界面**已经不用它了** ✗（用户 2026-09-24 改成"不按星期、直接列本月天数" ✓，见 [usageMonthCells] ✓）——
 * 留着是因为它是那次口径的产物 ✓、还有单测在跑 ✓（要回到"一年视图"随时能接回去 ✓）。
 *
 * @param stats 统计表 ✓
 * @param today 今天（`yyyy-MM-dd` ✓ —— 由调用方给 ✓，纯逻辑里不读时钟 ✓ 才测得动 ✓）
 * @param weeks 排几周 ✓（默认 53 周 = 一年 ✓）
 * @return 每一列 7 格（周一在上 ✓）；列内可能含**未来**的日期 ✓（本周剩下的那几天 ✓）——
 *   界面对未来那几格画成"空格子" ✓（不上色、不响应悬浮 ✓）。
 */
fun usageGrid(stats: UsageStats, today: LocalDate, weeks: Int = 53): List<List<UsageCell>> {
    // 这一周的第一天（周一 ✓，与 GitHub 一致 ✓）
    val todayMonday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val firstMonday = todayMonday.minusDays((weeks - 1) * 7L)
    return (0 until weeks).map { week ->
        (0 until 7).map { day ->
            val date = firstMonday.plusDays(week * 7L + day)
            UsageCell(date.toString(), usageLevel(stats.of(date.toString())?.images ?: 0))
        }
    }
}

/** 两个日期差几天 ✓（界面提示"第几周"之类的文案时用 ✓；负数 = 未来 ✓）。 */
fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

/**
 * **最近 N 天**（含今天 ✓，从早到晚 ✓）—— ⚠️ 界面**也不用它了** ✗（那是"滚动 30 天"那版 ✓，
 * 用户 2026-09-24 要的是"按自然月、还能翻月份" ✓，见 [usageMonthCells] ✓），留着做单测的对照 ✓。
 */
fun usageMonth(stats: UsageStats, today: LocalDate, days: Int = 30): List<UsageCell> {
    if (days <= 0) return emptyList()
    return (0 until days).map { index ->
        val date = today.minusDays((days - 1 - index).toLong())
        val key = date.toString()
        UsageCell(key, usageLevel(stats.of(key)?.images ?: 0), date.dayOfMonth)
    }
}

/**
 * **某一个自然月的每一天** ✓（用户 2026-09-24：
 * 「**不要以周一，周二排列，直接把本月天数的格子列出来**」✓，并且「**可以切换月份**」✓）。
 *
 * @param stats 统计表 ✓
 * @param month 哪个月（`YearMonth` ✓ —— 由调用方给 ✓，纯逻辑里不读时钟 ✓ 才测得动 ✓）
 * @return **正好 `month.lengthOfMonth()` 格** ✓：第 1 格 = 1 号 ✓、最后一格 = 月末 ✓
 *   （**不补前后空格** ✗ —— 不按星期对齐了 ✓，用户点名"直接列出来" ✓）；
 *   未来的那几天照样在列表里 ✓（等级 0 ✓，界面按日期判断、画成不可点的淡格 ✓）。
 */
fun usageMonthCells(stats: UsageStats, month: YearMonth): List<UsageCell> {
    val days = month.lengthOfMonth()
    return (1..days).map { day ->
        val key = month.atDay(day).toString()
        UsageCell(key, usageLevel(stats.of(key)?.images ?: 0), day)
    }
}

/**
 * **某个月的四个汇总数**（图数 / 点数 / tag 字数 / **有出图的天数** ✓）——
 * 用户 2026-09-24：「把**有出图天数换成本月**，**框内显示 xx 天**」✓。
 *
 * @return `(images, anlas, tagChars, activeDays)` ✓ —— 只统计这一天在 `[1, 当月天数]` 里的记录 ✓；
 *   ⚠️ 翻到**未来月份**时自然是全 0 ✓（未来没有记录 ✓，不编数 ✓）。
 */
fun usageMonthTotals(stats: UsageStats, month: YearMonth): MonthTotals {
    var images = 0
    var anlas = 0
    var tags = 0
    var active = 0
    for (day in 1..month.lengthOfMonth()) {
        val stat = stats.of(month.atDay(day).toString()) ?: continue
        images += stat.images
        anlas += stat.anlas
        tags += stat.tagChars
        if (stat.images > 0) active++
    }
    return MonthTotals(images, anlas, tags, active)
}

/** 一个月的四个汇总数 ✓（见 [usageMonthTotals] ✓）。 */
data class MonthTotals(
    val images: Int,
    val anlas: Int,
    val tagChars: Int,
    val activeDays: Int,
)
