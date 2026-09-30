package com.kallan.naistudio.models

/**
 * **提示词翻译管道**（第 ㊿h 批 2026-09-22）—— 网页原型 `translate/pipeline.js` 的 Kotlin 版，
 * 口径逐条对齐（那边每一处都有实测记录与判据）。
 *
 * ## 它解决什么
 *
 * 原来「翻译」是把**整串**丢给百度/LLM。现在：
 * **① 切段修标点 → ② 词典命中（频次首选，不出候选）→
 *   ③ 其余合一批交翻译 → ④ 按行号回填**。
 * 好处：词典命中的**一个字符都不外发**（省额度 + tag 更准）。
 *
 * ## 最要命的地方：**回填对齐**
 *
 * 百度 `trans_result` 没有「一行进一行出」的保证（实测**吞空行**：5 行进 ⇒ 3 行出）。
 * 行数对不上还硬按顺序回填 ⇒ **翻译静默串位**，用户看不出来、图就画歪了。
 * ⇒ 每一步校验行数，对不上就**二分重试**，拆到单条还不行就**放弃这一条**（保留原文）。
 * **宁可某条没翻，绝不串位。**
 *
 * ## 标点（用户口径，逐条）
 *
 * · **只修逗号类**（`,` `，` `、` `;` `；` ⇒ `, `）—— 句号、换行**不动**
 * · **句号不是分隔符**（NAI 吃自然语言，切开会把一句话拆成两截）
 * · **换行保留**；连续多个换行 = 多个空行，原样保留；结尾多余的丢掉
 * · **全角标点半角化**（`。`⇒`.`）—— ⚠️ **只在输出端做**：
 *   实测把 `。` 提前换成 `.` 再送百度，**百度会把标点整个丢掉**；送全角它反而产出规范标点
 *
 * ## 权重壳（三种写法 + 异形）
 *
 * 实测百度：`{t}` `[t]` `{{t}}` ⇒ **括号被吃掉**；`1.2::t::` ⇒ **改坏**（多空格）。
 * ⇒ **一律不外发**：剥壳 → 送内层实体 → **本地原样拼回**。
 * · `[a,b,c]` 里逗号**不是分隔符**（壳内多 tag 是一组，外壳只拼一次）
 * · `[[[[[[[tag]]]]]]]` 整串 run 一次配对
 * · `{{{tag::` 是**异形**（等效 `{{{tag}}}`，但**输出原样保留用户的写法**）
 * · 全角 `（）` 也当壳；**半角 `()` 不当壳**（`artist:hiro (dismaless)` 是真 tag）
 */
object PromptTranslate {

    // ------------------------------------------------------------------ 标点

    private val PUNCT_MAP = mapOf(
        '。' to ".", '．' to ".", '！' to "!", '？' to "?",
        '：' to ":", '；' to ";",
        '（' to "(", '）' to ")", '【' to "[", '】' to "]",
        '《' to "<", '》' to ">",
        '「' to "\"", '」' to "\"", '『' to "\"", '』' to "\"",
        '“' to "\"", '”' to "\"", '‘' to "'", '’' to "'",
        '～' to "~", '…' to "...", '—' to "-", '－' to "-", '　' to " ",
        // 漏在正文里的全角逗号也一并半角化（掩蔽没掩到的那些）
        '，' to ",", '、' to ",",
    )

    /** 中文标点 ⇒ 半角（**只在输出端调用**，见类注释）。 */
    fun toAsciiPunct(s: String): String = buildString(s.length) {
        for (c in s) append(PUNCT_MAP[c] ?: c.toString())
    }

    /**
     * 句末标点 —— 查表时剥掉、输出时半角化拼回。
     * ⚠️ **不含冒号**：数字权重的收尾就是 `::`，把冒号算作句末标点会把它剥掉，
     * 外壳就再也认不出来了（`1.2::long hair::` ⇒ `1.2::long hair` + 尾巴 `::`）。
     */
    private val TAIL_RE = Regex("([.!?。！？]+)$")

    /** 逗号类分隔符 —— **只有这些**算分隔。 */
    private const val SEP_CHARS = ",，、;；"

    /** 权重壳内部的逗号换成这个，避免被当分隔符切开（切完在 [splitWeight] 还原）。 */
    private const val MASK = '\u0001'

    // ------------------------------------------------------------------ 权重壳

    /** 壳的配对。`（）` 是全角 —— 半角 `()` 是真 tag 的一部分，不能当壳。 */
    private val PAIRS = listOf('{' to '}', '[' to ']', '（' to '）')

    private val NUM_START = Regex("-?\\d+(?:\\.\\d+)?::")

    /**
     * ⚠️ **正则一律预先编译**（第 ㊿h 批 2026-09-22 —— 用户报「手机点击翻译大概率卡死」✗）。
     *
     * 原来 [cleanTag] / [splitWeight] / [classify] 把 `Regex(...)` 写在**函数体里** ⇒
     * **每调用一次就重新编译一次** ✗。而 [buildDict] 会对词表里**每一个 tag** 调一次
     * [cleanTag]（8 个正则 × 约 8 万条 ⇒ **约 64 万次编译** ✗），
     * 全在主线程（`viewModelScope` 默认 `Dispatchers.Main` ✗）⇒ 一点就卡死 ✗。
     *
     * 提到 class 字段后**整个进程只编译一次** ✓。
     */
    private val WEIGHT_NUM_RE = Regex("^(-?\\d+(?:\\.\\d+)?)::([\\s\\S]*?)::$")
    private val WS_RE = Regex("\\s+")
    private val CLEAN_COMMENT_RE = Regex("/\\*[\\s\\S]*?\\*/")
    private val CLEAN_HTML_RE = Regex("</?[a-zA-Z][^>]*>")
    private val CLEAN_BACKTICK_RE = Regex("`+")
    private val CLEAN_ESCAPED_NL_RE = Regex("\\\\n|\\n")
    private val CLEAN_CTRL_RE = Regex("[\\r\\n\\t]+")
    private val CLEAN_LEAD_NUM_RE = Regex("^\\s*-?\\d+(?:\\.\\d+)?\\s+")
    private val CLEAN_LEAD_JUNK_RE = Regex("^[\\s|:;,.·\\-{}\\[\\]\"'`]+")
    private val CLEAN_TAIL_JUNK_RE = Regex("[\\s|:;,.·\\-{}\\[\\]\"'`]+$")
    private val CLEAN_MULTI_WS_RE = Regex("\\s{2,}")

    /**
     * [cleanTag] 的**快路径**：这些字符一个都没出现 ⇒ 那 9 个正则全都改不动它 ✓
     * ⇒ 直接短路，省掉 9 趟扫描 ✓。词表里绝大多数 tag 是干净的英文短语（`1girl` / `long hair` ✓）。
     *
     * 查表用 `BooleanArray`（O(1) ✓），不用 `CharArray.contains`（那是线性扫 ✗）。
     */
    private val CLEAN_DIRTY = BooleanArray(256).also { t ->
        for (c in charArrayOf(
            '/', '<', '>', '`', '\\', '\n', '\r', '\t', '*',
            '|', ':', ';', '.', ',', '·', '-', '{', '}', '[', ']', '"', '\'',
        )) {
            t[c.code] = true
        }
    }

    /** 见 [CLEAN_DIRTY]。首尾空白 / 连续两个空格也算"脏"（`\s{2,}` 与 `trim` 要处理 ✓）。 */
    private fun isCleanTag(raw: String): Boolean {
        if (raw.isEmpty()) return false
        if (raw.first().isWhitespace() || raw.last().isWhitespace()) return false
        for (c in raw) if (c.code < 256 && CLEAN_DIRTY[c.code]) return false
        return !raw.contains("  ")
    }

    /**
     * 找出**已闭合**的权重壳区间（只记最外层）。
     *
     * ⚠️ 数字权重 `1.2::…::` **也能包多个 tag**（`1.2::微笑,蓝眼睛::`），
     *    里面的逗号同样不是分隔符 —— 这一支容易漏。
     */
    fun findWrapperSpans(text: String): List<IntRange> {
        val spans = mutableListOf<IntRange>()
        var i = 0
        while (i < text.length) {
            // ① 数字权重
            val nm = NUM_START.find(text, i)
            if (nm != null && nm.range.first == i) {
                val close = text.indexOf("::", i + nm.value.length)
                if (close >= 0) {
                    spans.add(i..(close + 1))
                    i = close + 2
                    continue
                }
            }
            // ② 花括号 / 方括号 / 全角括号（含异形 `{{{tag::`）
            val ch = text[i]
            val pair = PAIRS.firstOrNull { it.first == ch }
            if (pair == null) { i++; continue }
            val closeChar = pair.second
            var j = i
            while (j < text.length && text[j] == ch) j++
            val need = j - i
            var k = j
            var seen = 0
            var end = -1
            while (k < text.length) {
                val c = text[k]
                if (c == closeChar) {
                    seen++
                    if (seen == need) { end = k; break }
                } else if (c == ':' && k + 1 < text.length && text[k + 1] == ':') {
                    end = k + 1; break            // 异形：`{{{tag::`
                }
                k++
            }
            if (end >= 0) {
                spans.add(i..end)
                i = end + 1                        // 只记最外层
            } else {
                i = j                              // 闭合不上 ⇒ 不当壳
            }
        }
        return spans
    }

    // ------------------------------------------------------------------ 切段

    data class Piece(val text: String, val line: Int)

    /**
     * 切段：**按行切、行内按逗号切**，行号随段带出去（输出时照原样还原分行）。
     *
     * ⚠️ 权重壳**内部**的逗号先掩蔽成 [MASK]，切完由 [splitWeight] 还原 ——
     *    这样 `[a,b]` 是**一段**（壳里两个 tag），而不是两段（`[a` 和 `b]`）。
     * ⚠️ 这里**不做半角化**（提前换会让百度把标点整个吃掉）。
     */
    fun splitInput(s: String): List<Piece> {
        val spans = findWrapperSpans(s)
        fun inSpan(idx: Int) = spans.any { idx > it.first && idx < it.last }

        val out = mutableListOf<Piece>()
        val buf = StringBuilder()
        var line = 0
        fun flush() {
            val t = buf.toString().trim()
            if (t.isNotEmpty()) out.add(Piece(t, line))
            buf.setLength(0)
        }
        for (i in s.indices) {
            val ch = s[i]
            when {
                ch == '\n' -> {
                    if (inSpan(i)) buf.append(ch) else flush()
                    line++
                }
                ch == '\r' -> Unit
                SEP_CHARS.indexOf(ch) >= 0 -> {
                    if (inSpan(i)) buf.append(MASK) else flush()
                }
                else -> buf.append(ch)
            }
        }
        flush()
        return out
    }

    /** 剥壳结果。`body` 内层实体（查表/送翻译都用它）；`pre`+`suf` 外壳（本地拼回）。 */
    data class Shell(val w: String, val pre: String, val suf: String, val body: String, val tail: String)

    /**
     * **剥壳**：句末标点与权重外壳**交替**剥，得到内层实体。
     *
     * ⚠️ 必须交替剥：句末标点可能在壳里（`{微笑。}`）也可能在壳外（`[微笑]。`）。
     */
    fun splitWeight(text: String): Shell {
        var t = text.replace(MASK.toString(), ",")
        var tail = ""
        val pre = StringBuilder()
        val suf = ArrayDeque<String>()
        var w = ""
        for (round in 0 until 6) {
            var changed = false
            // ① 句末标点（只认最外层第一个）
            if (tail.isEmpty()) {
                val tm = TAIL_RE.find(t)
                if (tm != null && tm.range.last == t.length - 1) {
                    tail = toAsciiPunct(tm.value)
                    t = t.substring(0, tm.range.first)
                    changed = true
                }
            }
            // ② 数字权重 `1.2::x::`
            val num = WEIGHT_NUM_RE.find(t)
            if (num != null) {
                val g = num.groupValues
                pre.append(g[1]).append("::")
                suf.addFirst("::")
                if (w.isEmpty()) w = g[1]
                t = g[2]
                continue
            }
            // ③ 括号壳（含异形 `{{{x::`）
            var peeled = false
            for ((open, close) in PAIRS) {
                val need = t.takeWhile { it == open }.length
                if (need == 0) continue
                val closeRun = t.takeLastWhile { it == close }.length
                if (closeRun == need && t.substring(need, t.length - need).isNotBlank()) {
                    pre.append(t.substring(0, need))
                    suf.addFirst(t.substring(t.length - need))
                    t = t.substring(need, t.length - need)
                    peeled = true
                    break
                }
                // ⚠️ 异形：`{{{tag::` 等效 `{{{tag}}}` —— 但**输出原样保留用户的写法**（照抄 `::`）
                if (t.endsWith("::") && t.substring(need, t.length - 2).isNotBlank()) {
                    pre.append(t.substring(0, need))
                    suf.addFirst("::")
                    t = t.substring(need, t.length - 2)
                    peeled = true
                    break
                }
            }
            if (peeled) continue
            if (!changed) break
        }
        return Shell(w, pre.toString(), suf.joinToString(""), t, tail)
    }

    // ------------------------------------------------------------------ 查表键

    /** 照 `TagZhProtocol.key` —— 这里只用于**正向**查中文（tag 原文 ⇒ 键）。 */
    private fun zhKey(piece: String): String = TagZhProtocol.key(piece)

    private fun hasCJK(s: String): Boolean = s.any {
        it.code in 0x3400..0x4DBF || it.code in 0x4E00..0x9FFF || it.code in 0xF900..0xFAFF
    }

    // ------------------------------------------------------------------ 词典

    /**
     * 双向词典。**在 App 里由已加载的 `TagZhProtocol.Shard` 现构**（不打包数据 —— 授权口径）。
     *
     * @param freq tag ⇒ 出现次数（**排序用**）。没有也给得对，只是同义候选里可能挑到冷门的
     *   （实测：`长发` 有 long hair / long hairs / longhair 三个候选，没有频次会挑到 `longhair`）。
     *   数据来源见 `AppState.promptTranslateFreq()`：**用已经下载在本地的那本词条现算**。
     */
    class Dict(val fwd: Map<String, String>, val rev: Map<String, List<String>>)

    /** 候选里明显的噪声（词表被污染过的原词）：`</style>1girl`、`blue eyes/*蓝眼睛*/` 之类。 */
    private fun cleanTag(raw: String): String {
        var t = raw
        // ⚠️ 快路径：没有脏字符 ⇒ 那 9 个正则一个都不会改动它 ⇒ 跳过（省 9 趟扫描 ✓）
        if (!isCleanTag(raw)) {
            t = t.replace(CLEAN_COMMENT_RE, " ")
            t = t.replace(CLEAN_HTML_RE, " ")
            t = t.replace(CLEAN_BACKTICK_RE, " ")
            t = t.replace(CLEAN_ESCAPED_NL_RE, " ")
            t = t.replace(CLEAN_CTRL_RE, " ")
            t = t.replace(CLEAN_LEAD_NUM_RE, "")
            t = t.replace(CLEAN_LEAD_JUNK_RE, "")
            t = t.replace(CLEAN_TAIL_JUNK_RE, "")
            t = t.replace(CLEAN_MULTI_WS_RE, " ")
            t = t.trim()
        }
        if (t.isEmpty()) return ""
        if (!t.any { it in 'a'..'z' || it in 'A'..'Z' }) return ""
        if (t.length > 60) return ""
        return t
    }

    /**
     * 从分片现构词典。
     *
     * 正向：**先到先得** = 分片顺序（core 在前）+ 层优先（`m` > `d` > `a`）。
     * 反向：把正向表倒过来，候选按 **频次↓ → 长度↑ → 字典序** 排（频次是唯一能挑出规范写法的信号）。
     */
    fun buildDict(shards: List<TagZhProtocol.Shard>, freq: Map<String, Int> = emptyMap()): Dict {
        val fwd = HashMap<String, String>()
        for (shard in shards) {
            // 层顺序 m → d → a 就是优先级；分片顺序（core 在前）也已经在外面保证
            for (map in listOf(shard.m, shard.d, shard.a)) {
                for ((tag, zhRaw) in map) {
                    val zh = zhRaw.trim()
                    if (zh.isEmpty()) continue
                    val key = tag.trim().lowercase()
                    if (key.isEmpty()) continue
                    // 先到先得：分片顺序 + 层顺序已经保证了优先级
                    if (!fwd.containsKey(key)) fwd[key] = zh
                }
            }
        }
        val revRaw = HashMap<String, MutableSet<String>>()
        for ((tag, zh) in fwd) {
            val clean = cleanTag(tag)
            if (clean.isEmpty()) continue
            revRaw.getOrPut(zh) { LinkedHashSet() }.add(clean)
        }
        val rev = HashMap<String, List<String>>(revRaw.size * 2)
        for ((zh, set) in revRaw) {
            rev[zh] = set.sortedWith(
                compareByDescending<String> { freq[it] ?: 0 }
                    .thenBy { it.length }
                    .thenBy { it },
            )
        }
        return Dict(fwd, rev)
    }

    // ------------------------------------------------------------------ 判定

    /** `tag` 本来就是 tag 且有中文；`zh` 中文反查得到 tag；`need` 词典查不到、要送翻译。 */
    enum class Kind { TAG, ZH, NEED }

    /** 方向：`zh2tag` 中文⇒英文（百度 `to=en`）；`tag2zh` 英文⇒中文（`to=zh`）。 */
    enum class Dir(val code: String) {
        ZH2TAG("en"),
        TAG2ZH("zh"),
    }

    data class Item(
        val raw: String,
        val body: String,
        val line: Int,
        val w: String,
        val pre: String,
        val suf: String,
        val tail: String,
        /** 同一个权重壳里的多个 tag 共享一个组号 —— 外壳在输出时**给整组拼一次**。 */
        val group: Int?,
        val kind: Kind,
        val dir: Dir,
        val tag: String?,
        val zh: String?,
        // 运行后填上
        val out: String? = null,
        val from: From = From.DICT,
    ) {
        /**
         * **内层实体文本**（外壳之外的正文）——
         * 没翻到的才做半角化（百度那条路它自己会产出规范英文标点）。
         */
        val inner: String
            get() = when {
                from == From.MISS -> PromptTranslate.toAsciiPunct(body)
                out != null -> out
                tag != null -> tag
                else -> PromptTranslate.toAsciiPunct(body)
            }
    }

    enum class From { DICT, API, MISS }

    data class Params(
        val maxChars: Int = 1500,
        val maxItems: Int = 40,
        val keepWeight: Boolean = true,
        val keepLines: Boolean = true,
        val space: Boolean = true,
    )

    private var groupSeq = 0

    /** 切段 + 逐段判定。壳里多个 tag ⇒ 多个 Item，共享一个 `group`。 */
    fun plan(raw: String, dict: Dict): List<Item> {
        groupSeq = 0
        val out = mutableListOf<Item>()
        for (piece in splitInput(raw)) {
            val sh = splitWeight(piece.text)
            val subs = sh.body.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val list = subs.ifEmpty { listOf(sh.body) }
            val grouped = list.size > 1
            val gid = if (grouped) ++groupSeq else null
            list.forEachIndexed { idx, sub ->
                val last = idx == list.size - 1
                out.add(classify(sub, piece, sh, gid, if (last) sh.tail else "", dict))
            }
        }
        return out
    }

    private fun classify(core: String, piece: Piece, sh: Shell, gid: Int?, tail: String, dict: Dict): Item {
        fun base(kind: Kind, dir: Dir, tag: String?, zh: String?) = Item(
            raw = piece.text, body = core, line = piece.line,
            w = sh.w, pre = sh.pre, suf = sh.suf, tail = tail, group = gid,
            kind = kind, dir = dir, tag = tag, zh = zh,
        )
        if (hasCJK(core)) {
            val key = core.replace(WS_RE, " ").trim()
            val cands = dict.rev[key] ?: dict.rev[core.trim()]
            if (!cands.isNullOrEmpty()) {
                // 用户口径：**不做候选**，只取频次首选
                val tag = cands[0]
                return base(Kind.ZH, Dir.ZH2TAG, tag, dict.fwd[tag] ?: core)
            }
            return base(Kind.NEED, Dir.ZH2TAG, null, core)
        }
        val k = zhKey(core).ifEmpty { core.trim().lowercase() }
        val zh = dict.fwd[k] ?: dict.fwd[core.trim().lowercase()]
        if (zh != null) return base(Kind.TAG, Dir.TAG2ZH, core, zh)
        return base(Kind.NEED, Dir.TAG2ZH, core, null)
    }

    // ------------------------------------------------------------------ 分批 / 对齐

    data class Need(val index: Int, val text: String, val dir: Dir)

    /**
     * 要送翻译的那些。
     * ⚠️ 送 `body`（**剥壳、剥句末标点**的内层实体）—— 壳绝不能外发。
     * ⚠️ 空白段**滤掉**：百度会吞空行（实测 5 行进 ⇒ 3 行出），那是它唯一的不守恒来源。
     */
    fun collectNeed(items: List<Item>): List<Need> = items.mapIndexedNotNull { i, p ->
        if (p.kind != Kind.NEED) return@mapIndexedNotNull null
        val text = p.body.trim()
        if (text.isEmpty()) return@mapIndexedNotNull null
        Need(i, text, p.dir)
    }

    /** 分批：同方向、不超长、装得下就一批（用户要的「一次性交给百度」）。 */
    fun planBatches(need: List<Need>, params: Params): List<List<Need>> {
        val batches = mutableListOf<List<Need>>()
        var cur = mutableListOf<Need>()
        var len = 0
        fun flush() {
            if (cur.isNotEmpty()) { batches.add(cur); cur = mutableListOf(); len = 0 }
        }
        for (item in need) {
            val l = item.text.length
            if (l > params.maxChars) { flush(); batches.add(listOf(item)); continue }
            if (cur.isNotEmpty() && (cur[0].dir != item.dir || len + l + 1 > params.maxChars || cur.size >= params.maxItems)) flush()
            cur.add(item)
            len += l + 1
        }
        flush()
        return batches
    }

    /** 把一批的结果对齐回原下标；**行数对不上返回 null**。 */
    fun alignBatch(items: List<Need>, lines: List<String>?): Map<Int, String>? {
        if (lines == null || lines.size != items.size) return null
        val m = HashMap<Int, String>()
        items.forEachIndexed { k, it ->
            val v = lines[k]
            if (v.isNotBlank()) m[it.index] = v.trim()
        }
        return m
    }

    /**
     * 发一批（含**二分重试**）。
     *
     * @param backend 约定**进 N 行出 N 行**；保证不了就如实返回短/长的列表（校验在这里做）。
     */
    suspend fun translateBatch(
        items: List<Need>,
        backend: suspend (List<String>) -> List<String>,
        out: MutableMap<Int, String>,
        trace: MutableList<String>,
    ) {
        if (items.isEmpty()) return
        val lines = runCatching { backend(items.map { it.text }) }.getOrNull()
        val aligned = alignBatch(items, lines)
        if (aligned != null) {
            trace.add("ok${items.size}")
            out.putAll(aligned)
            return
        }
        // ⚠️ 行数对不上 / 失败 ⇒ **二分**（不是逐条：百度免费档 1 QPS，逐条会等到天荒地老）
        if (items.size == 1) {
            trace.add("放弃1")
            return
        }
        trace.add("拆${items.size}")
        val mid = items.size / 2
        translateBatch(items.subList(0, mid), backend, out, trace)
        translateBatch(items.subList(mid, items.size), backend, out, trace)
    }

    data class Outcome(val items: List<Item>, val trace: List<String>, val batches: Int) {
        val dictHits get() = items.count { it.from == From.DICT }
        val apiHits get() = items.count { it.from == From.API }
        val misses get() = items.count { it.from == From.MISS }
    }

    /** 走完整条：词典先填，剩下的交给后端。 */
    suspend fun run(
        raw: String,
        dict: Dict,
        params: Params,
        backend: suspend (List<String>) -> List<String>,
    ): Outcome {
        val pieces = plan(raw, dict)
        val need = collectNeed(pieces)
        val batches = planBatches(need, params)
        val got = HashMap<Int, String>()
        val trace = mutableListOf<String>()
        for (b in batches) translateBatch(b, backend, got, trace)

        val items = pieces.mapIndexed { i, p ->
            if (p.kind != Kind.NEED) {
                p.copy(out = if (p.kind == Kind.ZH) p.tag else null, from = From.DICT)
            } else {
                val tr = got[i]
                when {
                    tr != null && p.dir == Dir.ZH2TAG -> p.copy(out = tr, from = From.API)
                    tr != null -> p.copy(zh = tr, from = From.API)
                    else -> p.copy(out = p.tag, from = From.MISS)
                }
            }
        }
        return Outcome(items, trace, batches.size)
    }

    // ------------------------------------------------------------------ 输出

    data class Rendered(val tags: String, val zh: String)

    private class Block(
        val items: List<Item>,
        val pre: String,
        val suf: String,
        val tail: String,
        val line: Int,
    )

    /**
     * 生成两个输出串。
     * · **换行保留**（连续多个换行 = 多个空行；结尾多余的丢掉）
     * · **外壳本地拼**，且**一组只拼一次**；句末标点拼在**壳外**
     */
    fun render(items: List<Item>, params: Params): Rendered {
        val sep = if (params.space) ", " else ","
        val blocks = mutableListOf<Block>()
        var i = 0
        while (i < items.size) {
            val it = items[i]
            val g = it.group
            if (g != null) {
                val members = mutableListOf(it)
                var j = i + 1
                while (j < items.size && items[j].group == g) { members.add(items[j]); j++ }
                blocks.add(Block(members, it.pre, it.suf, members.last().tail, it.line))
                i = j
            } else {
                blocks.add(Block(listOf(it), it.pre, it.suf, it.tail, it.line))
                i++
            }
        }
        val textOf = { b: Block ->
            val inner = b.items.joinToString(sep) { it.inner }
            val wrapped = if (params.keepWeight && b.pre.isNotEmpty()) "${b.pre}$inner${b.suf}" else inner
            wrapped + b.tail
        }
        val zhOf = { b: Block -> b.items.joinToString("，") { it.zh ?: it.raw } }

        fun join(one: (Block) -> String): String {
            if (!params.keepLines) return blocks.joinToString(sep) { one(it) }
            if (blocks.isEmpty()) return ""
            val byLine = blocks.groupBy { it.line }.toSortedMap()
            val lo = byLine.firstKey()
            val hi = byLine.lastKey()
            val rows = mutableListOf<String>()
            for (n in lo..hi) rows.add(byLine[n]?.joinToString(sep) { one(it) } ?: "")
            return rows.joinToString("\n")
        }
        return Rendered(join(textOf), join(zhOf))
    }
}
