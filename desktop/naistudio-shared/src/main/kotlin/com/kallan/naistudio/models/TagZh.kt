package com.kallan.naistudio.models

import org.json.JSONObject

/**
 * **tag 的中文对照**（第 ㊿ 批 2026-09-21 ✓）—— 口径**逐条照原网页** ✓
 *（`assets/app/tag-zh-core.js` + `tag-zh.js` ✓，不是我们自创 ✓）。
 *
 * ## 它长什么样（用户口径 ✓）
 *
 * 原网页是**每个 tag 下面跟一行中文小字** ✓，**复制时用原文、不带翻译** ✓。
 * 我们照做 ✓。
 *
 * ## 词表从哪来（**在线读 + 本地缓存** ✗ 不是打包 ✓）
 *
 * 两片 ✓（站点 `tag-zh.js` 的 `CORE_PATH` / 书分片 ✓）：
 *  · `tag_zh/core.json` —— 通用层 ✓（实测 1.1 MB ✓）；
 *  · `tag_zh/{codexId}.json` —— **该书专属** ✓（`nai5_community_pack` 实测 652 KB ✓）。
 *
 * ⚠️ **三层译名 + 优先级** ✓（`tag-zh-core.js` 的 `LOOKUP_ORDER` ✓）：
 * **`m` 人工校对 > `d` 社区词库 > `a` AI 机翻** ✓，同级先 core 再书分片 ✓。
 *
 * ⚠️ **为什么不打包进 App** ✗：授权写明"数据汇编未经许可不得再分发" ✓
 *（[TagCodexProtocol.LICENSE_NOTE] ✓），打包就变成再分发 ✓。
 * 缓存在**用户自己的机器上** ✓ 既不违反授权、体验又与打包一致 ✓（只有第一次要下 ✓）。
 */
object TagZhProtocol {

    /** 词表 schema 版本（站点 `TAG_ZH_SCHEMA = 1` ✓，对不上就当作没有对照表 ✓）。 */
    const val SCHEMA = 1

    /** 通用层路径 ✓（站点 `CORE_PATH` ✓）。 */
    const val CORE_PATH = "tag_zh/core.json"

    /** 某一本书的分片路径 ✓。 */
    fun shardPath(codexId: String): String = "tag_zh/$codexId.json"

    /**
     * **查表键** ✓ —— 站点 `tagZhKey` 的逐条翻译 ✓。
     *
     * 剥掉的东西（都是"同一个 tag 的不同写法"✓）：
     *  · NAI 的数字权重前缀 `1.2::` ✓；
     *  · NAI 的 `{}` / `[]` / `"` 包裹 ✓；
     *  · 只在一侧多出来的括号（`(girl` ✓）；
     *  · SD 的权重后缀 `(tag:1.2)` ✓；
     *  · 下划线当空格 ✓、压空白 ✓、转小写 ✓。
     *
     * 返回空串 = **不查** ✓（站点同口径 ✓）：
     *  · 空 ✓； · **不含英文字母**（纯数字 / 纯中文 ✓）；
     *  · **`artist:` 开头** ✓（画师名不翻译 ✓ —— 那是人名 ✓）；
     *  · 太长（> 300 ✓）。
     */
    /**
     * ⚠️ **正则必须预先编译**（第 ㊿h 批 2026-09-22 —— 用户报「手机点击翻译大概率卡死」✗）。
     *
     * 这几个 `Regex(...)` 原来写在 [key] / [split] 的**函数体里** ⇒
     * **每调用一次就重新编译一次** ✗。而 `AppState.promptTranslateFreq()` 要拿它跑
     * 几十万段提示词 ⇒ **上百万次正则编译** ✗ ⇒ 主线程卡死 ✗
     *（`viewModelScope` 默认就是 `Dispatchers.Main` ✗）。
     *
     * 提成 object 字段之后**只编译一次** ✓。
     */
    private val WEIGHT_PREFIX_RE = Regex("""^-?\d+(?:\.\d+)?::""")
    private val SD_SUFFIX_RE = Regex(""":\s*-?\d+(?:\.\d+)?$""")
    private val WS_RE = Regex("""\s+""")
    private val SPLIT_SEP_RE = Regex("""\r\n|\r|\n|,|，""")

    fun key(piece: String): String {
        var text = piece.replace("\\(", "\u0001").replace("\\)", "\u0002")
        val weightPrefix = WEIGHT_PREFIX_RE
        val sdSuffix = SD_SUFFIX_RE
        // 站点是"最多剥 12 轮、剥不动就停" ✓（嵌套括号那种 ✓）—— 这里同口径 ✓
        repeat(12) {
            val before = text
            text = text.trim().replace(weightPrefix, "")
            if (text.endsWith("::")) text = text.dropLast(2)
            text = text.trim { it in "{}[]\"" }
            while (text.startsWith("(") && text.count { it == '(' } > text.count { it == ')' }) {
                text = text.drop(1)
            }
            while (text.endsWith(")") && text.count { it == ')' } > text.count { it == '(' }) {
                text = text.dropLast(1)
            }
            if (text.startsWith("(") && text.endsWith(")")) text = text.drop(1).dropLast(1)
            text = text.replace(sdSuffix, "")
            if (text == before) return@repeat
        }
        text = text.replace("\u0001", "(").replace("\u0002", ")")
        val key = text.replace('_', ' ').replace(WS_RE, " ").trim().lowercase()
        if (key.isEmpty()) return ""
        if (!key.any { it in 'a'..'z' }) return ""
        if (key.startsWith("artist:")) return ""
        if (key.length > 300) return ""
        return key
    }

    /**
     * 把一串提示词切成**可渲染的段** ✓（站点 `splitPromptPieces` ✓）。
     *
     * 按**逗号 / 全角逗号 / 换行**切 ✓，每段带：
     *  · [Piece.text] 原文（**可带权重语法** ✓）； · [Piece.key] 查表键 ✓（空 = 不查 ✓）。
     *
     * ⚠️ **拼回 `lead + text + sep` 就是原文** ✓ —— 一个字符都不丢 ✓
     *（所以"复制"永远拿的是原文 ✓，与用户"复制不带翻译"的口径天然一致 ✓）。
     */
    fun split(value: String): List<Piece> {
        val sepRegex = SPLIT_SEP_RE
        val parts = sepRegex.split(value)
        val seps = sepRegex.findAll(value).map { it.value }.toList()
        val out = mutableListOf<Piece>()
        for (i in parts.indices) {
            val body = parts[i]
            val sep = seps.getOrNull(i) ?: ""
            if (body.isEmpty() && sep.isEmpty()) continue
            val lead = body.takeWhile { it.isWhitespace() }
            val text = body.substring(lead.length)
            out.add(
                Piece(
                    lead = lead,
                    text = text,
                    sep = if (i == parts.lastIndex) "" else sep,
                    key = key(text),
                ),
            )
        }
        return out
    }

    /** 一段提示词 ✓（站点 `splitPromptPieces` 那个对象的 Kotlin 版 ✓）。 */
    data class Piece(val lead: String, val text: String, val sep: String, val key: String)

    /**
     * **一片词表** ✓（站点 `normalizeTagZhShard` ✓）。
     *
     * 三层各一张表 ✓；`schema` 对不上就当**没有** ✓（站点同口径 ✓ —— 形态不对等于没对照表 ✓）。
     */
    data class Shard(
        val m: Map<String, String> = emptyMap(),
        val d: Map<String, String> = emptyMap(),
        val a: Map<String, String> = emptyMap(),
    ) {
        val isEmpty: Boolean get() = m.isEmpty() && d.isEmpty() && a.isEmpty()
    }

    /**
     * 解析一片词表 ✓。**形态不对返回 null** ✓（调用方当作"这一片没有" ✓，不崩 ✗）。
     */
    fun parseShard(json: JSONObject?): Shard? {
        if (json == null) return null
        if (json.optInt("schema", -1) != SCHEMA) return null
        return Shard(
            m = readMap(json.optJSONObject("m")),
            d = readMap(json.optJSONObject("d")),
            a = readMap(json.optJSONObject("a")),
        )
    }

    private fun readMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val out = HashMap<String, String>(obj.length() * 2)
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.optString(k).trim()
            if (v.isNotEmpty()) out[k] = v
        }
        return out
    }

    /**
     * **查一个键的译名** ✓（站点 `lookupTagZh` ✓）。
     *
     * 优先级 **`m` > `d` > `a`** ✓，同级**先 core 再书分片** ✓（传进来的顺序就是它 ✓）。
     * 查不到返回 `null` ✓（那一格就**只显示英文** ✓，不显示空占位 ✗）。
     */
    fun lookup(shards: List<Shard>, key: String): String? {
        if (key.isEmpty()) return null
        for (group in listOf({ s: Shard -> s.m }, { s: Shard -> s.d }, { s: Shard -> s.a })) {
            for (shard in shards) {
                group(shard)[key]?.let { if (it.isNotEmpty()) return it }
            }
        }
        return null
    }
}
