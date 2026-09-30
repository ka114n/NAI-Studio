package com.kallan.naistudio.models

/**
 * **提示词词典**（用户 2026-09-24：「看看 35 万 tag 的文件在哪里，**补充词典库**」✓）。
 *
 * ## 先把事实说清楚（这条最要紧 ✗）
 *
 *  · This bundled TSV was derived from an external Danbooru tag-index snapshot.
 *    The exact dataset revision, generator, attribution, and redistribution terms
 *    must be documented and verified before public release.
 *
 * ## 这份文件是什么、为什么是它
 *
 * `src/main/resources/dictionary.tsv` ✓（生成脚本 `tools/build-dictionary.js` ✓）：
 * 一行一条 `tag<TAB>分类码<TAB>热度` ✓（**纯英文** ✓ —— Danbooru 那份零中文 ✓，实测确认 ✓）。
 *
 * | 取舍 | 为什么 |
 * | --- | --- |
 * | **只留热度 ≥ 200** ✓（39,577 条 / 831 KB ✓） | 全量 10.7 万是 2.2 MB ✓；按热度砍掉长尾 ✓，常用的一个不少 ✓ |
 * | **分类码只留一个字符** ✓ | `g`=general `c`=character `s`=series `a`=artist `m`=meta ✓ |
 * | **别名先不进** ✗ | 11,277 条别名换来体积翻倍 ✓，而"补全"这一步**用不到** ✓（将来要做"别名归一"再加 ✓） |
 * | 整个文件 gzip 后 348 KB ✓ | 两端的包都吃得下 ✓（图标库那点体积的量级 ✓） |
 *
 * ## 它**不是**什么（别搞混 ✗）
 *
 *  · **不是中文对照** ✗ —— 中文那套是 `TagZh.kt` ✓（在线读 + 用户本机缓存 ✓，
 *    授权写明"不得再分发"✓，这是当初定的口径 ✓）；
 *  · **不是"官方 tag 白名单"** ✗ —— 它是"**哪些 tag 有人用过、有多热**"✓，
 *    拿来**补全 / 提示**正合适 ✓，拿来**校验**就过头了 ✗（用户想写冷门 tag 是他的自由 ✓）。
 */
object TagDictionary {

    /** 一条词典条目 ✓。 */
    data class Entry(
        /** tag 原文 ✓（Danbooru 的下划线写法 ✓，如 `long_hair` ✓）。 */
        val tag: String,
        /** 分类码 ✓（[categoryLabel] 翻成人话 ✓）。 */
        val category: Char,
        /** 用过的次数 ✓（热度 ✓ —— 排序与"先给常用的"都靠它 ✓）。 */
        val count: Int,
    ) {
        /** 显示用的分类名 ✓（给界面用 ✓；中文 ✓）。 */
        val categoryLabel: String get() = when (category) {
            'c' -> "角色"
            's' -> "作品"
            'a' -> "画师"
            'm' -> "元数据"
            else -> "通用"
        }
    }

    /**
     * **解析一行** ✓（纯逻辑 ✓ 可单测 ✓）—— 认不出来就返回 `null` ✗（**跳过而不是抛** ✓：
     * 词典坏一条不该拖垮启动 ✓，和 `UsageStats.decode` 同一条口径 ✓）。
     */
    fun parseLine(line: String): Entry? {
        if (line.isBlank()) return null
        val parts = line.split('\t')
        if (parts.size < 3) return null
        val tag = parts[0].trim()
        val category = parts[1].trim().firstOrNull() ?: return null
        val count = parts[2].trim().toIntOrNull() ?: return null
        if (tag.isEmpty() || tag.startsWith("#")) return null
        return Entry(tag, category, count)
    }

    /**
     * **把整份文本解析成词典** ✓（[raw] = 资源文件的全文 ✓）。
     *
     * ⚠️ 解析在**调用方的线程**上做 ✓（39,577 行 ≈ 十几 MB 的字符串扫描 ✓，
     * 手机上大概几十毫秒 ✓）—— 两端都放在**后台**加载 ✓（见各自的 `AppState` ✓）。
     */
    fun parse(raw: String): List<Entry> {
        val out = ArrayList<Entry>(40960)
        raw.lineSequence().forEach { line ->
            parseLine(line)?.let { out.add(it) }
        }
        return out
    }

    /** 分类码 → 中文名 ✓（不建 [Entry] 也能用 ✓，界面按分类过滤时用 ✓）。 */
    fun categoryLabel(code: Char): String = when (code) {
        'c' -> "角色"
        's' -> "作品"
        'a' -> "画师"
        'm' -> "元数据"
        else -> "通用"
    }

    /**
     * **按前缀 / 子串找候选** ✓（补全那一层用 ✓，纯逻辑 ✓ 可单测 ✓）。
     *
     * 口径（**可预期** ✓ 不是"猜" ✗）：
     *  1. **前缀命中排前面** ✓（`long` → `long_hair` ✓），子串命中排后面 ✓；
     *  2. 同级**按热度降序** ✓（先给"最多人用的" ✓）；
     *  3. 下划线 / 空格**归一**再比 ✓（用户打 `long hair` 也该命中 `long_hair` ✓）；
     *  4. `limit` 封顶 ✓（界面上就那么几行 ✓）。
     *
     * @param query 用户已经打出来的那一截 ✓（空串 ⇒ 返回**最热的**前 [limit] 条 ✓ —— 那是"打开就有的建议" ✓）
     */
    fun suggest(
        entries: List<Entry>,
        query: String,
        limit: Int = 8,
    ): List<Entry> {
        if (limit <= 0) return emptyList()
        val q = normalize(query)
        if (q.isEmpty()) {
            return entries.asSequence().sortedByDescending { it.count }.take(limit).toList()
        }
        val prefix = ArrayList<Entry>()
        val contains = ArrayList<Entry>()
        for (e in entries) {
            val key = normalize(e.tag)
            when {
                key.startsWith(q) -> prefix.add(e)
                key.contains(q) -> contains.add(e)
            }
        }
        prefix.sortByDescending { it.count }
        contains.sortByDescending { it.count }
        return (prefix + contains).take(limit)
    }

    /** 比对用的归一：小写 + 下划线/连字符当空格 + 压空白 ✓（和 `TagZhProtocol.key` 同一套手感 ✓）。 */
    private fun normalize(value: String): String = value
        .lowercase()
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .replace(Regex("\\s+"), " ")
}


