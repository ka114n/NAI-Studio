package com.kallan.naistudio.models

import java.math.BigDecimal
import java.math.RoundingMode

/** A lossless display projection. Only explicit edits serialize it back into the prompt. */
object PromptTagCards {
    data class Shell(val id: Int, val pre: String, val suf: String)
    data class Tag(val id: Int, val body: String, val shells: List<Shell>, val line: Int, val weight: Double)
    private val number = Regex("-?\\d+(?:\\.\\d+)?::")
    private const val separators = ",，、;；\n\r"

    fun parse(text: String): List<Tag> {
        val tags = mutableListOf<Tag>()
        val weights = PromptWeight.scan(text)
        var groupId = 0
        fun visit(start: Int, end: Int, path: List<Shell>) {
            var a = start
            var b = end
            while (a < b && text[a].isWhitespace()) a++
            while (b > a && text[b - 1].isWhitespace()) b--
            if (a == b) return
            val raw = text.substring(a, b)
            val num = number.find(raw)?.takeIf { it.range.first == 0 }
            // Peel one complete outer group, then recurse so every nested tag gets its own card.
            val pair = when (raw.first()) { '{' -> '}'; '[' -> ']'; else -> null }
            val bracketEnd = if (pair != null) {
                var depth = 0
                raw.indices.firstOrNull { i ->
                    if (raw[i] == raw.first()) depth++
                    if (raw[i] == pair) depth--
                    depth == 0
                }
            } else null
            when {
                num != null && raw.indexOf("::", num.value.length) == raw.length - 2 -> {
                    visit(a + num.value.length, b - 2, path + Shell(++groupId, num.value, "::"))
                    return
                }
                pair != null && bracketEnd == raw.lastIndex -> {
                    visit(a + 1, b - 1, path + Shell(++groupId, raw.first().toString(), pair.toString()))
                    return
                }
            }
            var depth = 0
            var numeric = false
            var begin = a
            var i = a
            val cuts = mutableListOf<Pair<Int, Int>>()
            while (i < b) {
                val numericStart = number.find(text, i)?.takeIf { it.range.first == i &&
                    (i == a || text[i - 1].isWhitespace() || text[i - 1] in separators) }
                if (numericStart != null) { numeric = true; i += numericStart.value.length; continue }
                if (text.startsWith("::", i)) { numeric = false; i += 2; continue }
                when (text[i]) {
                    '{', '[' -> depth++
                    '}', ']' -> depth--
                }
                if (depth == 0 && !numeric && text[i] in separators) {
                    cuts += begin to i
                    begin = i + 1
                }
                i++
            }
            // Incomplete syntax stays intact until edited in text mode.
            if (cuts.isNotEmpty() && depth == 0 && !numeric) {
                cuts += begin to b
                cuts.forEach { (from, to) -> visit(from, to, path) }
            } else {
                val weight = weights.firstOrNull { a in it.start until it.end }?.weight ?: 1.0
                tags += Tag(tags.size, raw, path, text.take(a).count { it == '\n' }, weight)
            }
        }
        visit(0, text.length, emptyList())
        return tags
    }

    fun render(tags: List<Tag>): String {
        val grouped = renderGroups(tags)
        val parsed = parse(grouped)
        // Numeric :: closes open bracket scopes in NAI. If editing a mixed group changes
        // that scope, isolate the tags numerically rather than changing a neighbour's weight.
        if (parsed.size == tags.size && parsed.zip(tags).all {
                it.first.body == it.second.body && kotlin.math.abs(it.first.weight - it.second.weight) < 1e-8
            }) return grouped
        if (tags.any { tag -> tag.body.any { it in "{}[]" } || tag.body.contains("::") || !tag.weight.isFinite() }) return grouped
        return renderGroups(tags.map { tag ->
            val w = BigDecimal.valueOf(tag.weight).stripTrailingZeros().toPlainString()
            tag.copy(shells = listOf(Shell(-tag.id - 1, "$w::", "::")))
        })
    }

    private fun renderGroups(tags: List<Tag>): String = buildString {
        var open = emptyList<Shell>()
        tags.forEachIndexed { index, tag ->
            val common = open.zip(tag.shells).takeWhile { it.first.id == it.second.id }.size
            open.drop(common).asReversed().forEach { append(PromptWeight.closingSuffix(this, it.suf)) }
            if (index > 0) {
                val lines = (tag.line - tags[index - 1].line).coerceAtLeast(0)
                append(if (lines > 0) "\n".repeat(lines) else ", ")
            }
            tag.shells.drop(common).forEach { append(it.pre) }
            append(tag.body)
            open = tag.shells
        }
        open.asReversed().forEach { append(PromptWeight.closingSuffix(this, it.suf)) }
    }

    fun adjust(tags: List<Tag>, id: Int, delta: Double): List<Tag> = tags.map { tag ->
        if (tag.id != id) tag else {
            val w = BigDecimal.valueOf(tag.weight).add(BigDecimal.valueOf(delta))
                .setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
            // Isolate this tag from shared weight groups; its neighbours retain their original shell.
            val shell = if (w.compareTo(BigDecimal.ONE) == 0) emptyList()
                else listOf(Shell(-id - 1, "${w.toPlainString()}::", "::"))
            tag.copy(shells = shell, weight = w.toDouble())
        }
    }

    fun displayLabel(tag: Tag, body: String = tag.body): String {
        val weight = BigDecimal.valueOf(tag.weight).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
        return if (weight.compareTo(BigDecimal.ONE) == 0) body
            else "${weight.toPlainString()}::$body${PromptWeight.closingSuffix(body, "::")}"
    }

    /** Editing a card changes its text, retaining its weight and its neighbours' scopes. */
    fun rename(tags: List<Tag>, id: Int, text: String): List<Tag> =
        if (text.isBlank()) tags.filter { it.id != id }
        else tags.map { if (it.id == id) it.copy(body = text.trim()) else it }

    fun move(tags: List<Tag>, fromId: Int, toId: Int): List<Tag> {
        val from = tags.indexOfFirst { it.id == fromId }
        val to = tags.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0 || from == to) return tags
        // Position owns the line; moving across lines keeps the original line layout.
        val moved = tags.toMutableList().apply { add(to, removeAt(from)) }
        return moved.mapIndexed { i, tag -> tag.copy(line = tags[i].line) }
    }
}
