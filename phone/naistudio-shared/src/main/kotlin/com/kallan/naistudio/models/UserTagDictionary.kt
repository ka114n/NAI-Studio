package com.kallan.naistudio.models

import org.json.JSONArray
import org.json.JSONObject

/** Supplemental aliases are stored separately; legacy single-label JSON is accepted. */
object UserTagDictionary {
    data class Match(val tag: String, val translations: List<String>)

    private fun jsonPairs(obj: JSONObject): List<Pair<String, String>> = buildList {
        obj.keys().forEach { key ->
            when (val value = obj.get(key)) {
                is String -> add(key to value)
                is JSONArray -> {
                    require(value.length() > 0) { "dictionary.invalidImport" }
                    for (i in 0 until value.length()) {
                        require(value.get(i) is String) { "dictionary.invalidImport" }
                        add(key to value.getString(i))
                    }
                }
                else -> error("dictionary.invalidImport")
            }
        }
    }

    private fun group(pairs: List<Pair<String, String>>): Map<String, List<String>> =
        pairs.groupBy({ TagZhProtocol.key(it.first) }, { it.second.trim() })
            .mapValues { (_, aliases) -> aliases.distinct() }

    fun decode(json: String): Map<String, List<String>> = runCatching {
        group(jsonPairs(JSONObject(json)).filter { valid(it.first, it.second.trim()) })
    }.getOrDefault(emptyMap())

    fun encode(entries: Map<String, List<String>>): String = JSONObject().apply {
        entries.forEach { (en, aliases) -> put(en, JSONArray(aliases.distinct())) }
    }.toString()

    fun aliases(text: String): List<String> = text.lineSequence().map { it.trim() }
        .filter { it.isNotEmpty() }.distinct().toList()

    fun valid(en: String, zh: String): Boolean = en.isNotBlank() && zh.isNotBlank() &&
        en.length <= 200 && zh.length <= 200 &&
        en.none { it in ",，、;；\n\r{}[]" } && zh.none { it == '\n' || it == '\r' } &&
        !en.contains("::") && en.any { it in 'a'..'z' || it in 'A'..'Z' }

    /** Repeated English rows append aliases; malformed batches reject the entire import. */
    fun parseImport(text: String): Map<String, List<String>> {
        val pairs = if (text.trim().startsWith("{")) jsonPairs(JSONObject(text))
        else text.lineSequence().filter { it.isNotBlank() }.map { line ->
            val parts = line.split('\t', limit = 2)
            require(parts.size == 2) { "dictionary.invalidImport" }
            parts[0] to parts[1]
        }.toList()
        require(pairs.isNotEmpty() && pairs.all { valid(it.first.trim(), it.second.trim()) }) { "dictionary.invalidImport" }
        return group(pairs)
    }

    fun append(base: Map<String, List<String>>, added: Map<String, List<String>>): Map<String, List<String>> =
        base.toMutableMap().apply {
            added.forEach { (en, aliases) ->
                val key = TagZhProtocol.key(en)
                put(key, (get(key).orEmpty() + aliases).distinct())
            }
        }

    fun removedTags(json: String): Set<String> = runCatching {
        val values = JSONArray(json)
        (0 until values.length()).mapNotNull { index ->
            (values.opt(index) as? String)?.let { TagZhProtocol.key(it) }?.takeIf { it.isNotBlank() }
        }.toSet()
    }.getOrDefault(emptySet())

    fun encodeRemoved(tags: Set<String>): String = JSONArray(tags.sorted()).toString()

    fun merge(base: PromptTranslate.Dict, custom: Map<String, List<String>>, removed: Set<String> = emptySet(), replaced: Set<String> = emptySet()): PromptTranslate.Dict {
        if (custom.isEmpty() && removed.isEmpty() && replaced.isEmpty()) return base
        val fwd = base.fwd + custom.mapValues { (_, aliases) -> aliases.first() }
        // Keep built-in aliases searchable and translatable alongside supplemental aliases.
        val rev = base.rev.mapValues { (_, tags) -> tags.filter { TagZhProtocol.key(it) !in replaced } }
            .filterValues { it.isNotEmpty() }.toMutableMap()
        custom.forEach { (en, aliases) -> aliases.forEach { zh ->
            rev[zh] = (listOf(en) + rev[zh].orEmpty()).distinct()
        } }
        return PromptTranslate.Dict(
            fwd.filterKeys { TagZhProtocol.key(it) !in removed },
            rev.mapValues { (_, tags) -> tags.filter { TagZhProtocol.key(it) !in removed } }
                .filterValues { it.isNotEmpty() },
            primary = base.primary, customAliases = custom, removed = removed, replaced = replaced,
        )
    }

    private val searchSpaces = Regex("\\s+")
    private fun normalizeSearch(label: String) =
        label.lowercase().replace('_', ' ').replace(searchSpaces, " ").trim()

    internal data class SearchLabel(val normalized: String, val tags: List<String>)
    internal class SearchIndex(val labels: List<SearchLabel>, val matches: Map<String, Match>)

    /** Construct once per immutable dictionary, on a background dispatcher before publication. */
    internal fun buildSearchIndex(dict: PromptTranslate.Dict): SearchIndex {
        val labels = ArrayList<SearchLabel>(dict.fwd.size + dict.rev.size)
        val translations = dict.fwd.mapValues { (_, zh) -> mutableListOf(zh) }.toMutableMap()
        dict.fwd.forEach { (en, _) -> labels.add(SearchLabel(normalizeSearch(en), listOf(en))) }
        dict.rev.forEach { (zh, tags) ->
            labels.add(SearchLabel(normalizeSearch(zh), tags))
            tags.forEach { translations.getOrPut(it) { mutableListOf() }.add(zh) }
        }
        return SearchIndex(labels, translations.mapValues { (en, zh) -> Match(en, zh.distinct()) })
    }

    fun prepareSearch(dict: PromptTranslate.Dict) { dict.searchIndex }

    /** Return English tags with all known labels, independent of query language. */
    private fun fallbackSearch(text: String, dict: PromptTranslate.Dict, limit: Int, charactersOnly: Boolean = false): List<Match> {
        val query = normalizeSearch(PromptTranslate.splitWeight(text.trim()).body)
        if (query.isEmpty()) return emptyList()
        val index = dict.searchIndex
        val words = query.split(' ')
        val matches = mutableMapOf<String, Pair<Int, String>>()
        index.labels.forEach { label ->
            val normalized = label.normalized
            if (words.all { normalized.contains(it) }) {
                val rank = when { normalized == query -> 0; normalized.startsWith(query) -> 1; else -> 2 }
                label.tags.filter { !charactersOnly || dict.primary?.entry(it)?.category == SqlTagLexicon.CHARACTER_CATEGORY }.forEach { tag ->
                    val previous = matches[tag]
                    if (previous == null || rank < previous.first) matches[tag] = rank to normalized
                }
            }
        }
        return matches.entries.sortedWith(compareBy({ it.value.first }, { it.value.second }, { it.key }))
            .take(limit).mapNotNull { index.matches[it.key] }
    }
    /** Bounded SQL candidates plus the legacy/custom aliases, with user changes applied last. */
    fun search(text: String, dict: PromptTranslate.Dict, limit: Int = 100, charactersOnly: Boolean = false): List<Match> {
        val query = normalizeSearch(PromptTranslate.splitWeight(text.trim()).body)
        if (query.isEmpty() || limit <= 0) return emptyList()
        val fallback = fallbackSearch(text, dict, limit.coerceAtMost(10000), charactersOnly)
        if (dict.primary == null) return fallback.take(limit)
        val candidates = dict.primary.search(query, (limit + dict.removed.size + dict.replaced.size).coerceAtMost(10000),
            category = if (charactersOnly) SqlTagLexicon.CHARACTER_CATEGORY else null)
            .filter { dict.primaryAllowed(it.tag) }
        val frequencies = candidates.associate { it.tag to it.frequency }.toMutableMap()
        val matches = LinkedHashMap<String, Match>()
        candidates.forEach { entry ->
            val labels = (dict.customAliases[entry.tag].orEmpty() + entry.chinese +
                dict.searchIndex.matches[entry.tag]?.translations.orEmpty()).distinct()
            matches[entry.tag] = Match(entry.tag, labels)
        }
        fallback.forEach { entry ->
            val main = if (dict.primaryAllowed(entry.tag)) dict.primary.entry(entry.tag) else null
            if (main != null) frequencies[entry.tag] = main.frequency
            val labels = (dict.customAliases[entry.tag].orEmpty() + listOfNotNull(main?.chinese) + entry.translations).distinct()
            matches[entry.tag] = Match(entry.tag, labels)
        }
        fun rank(match: Match): Int {
            val labels = listOf(normalizeSearch(match.tag)) + match.translations.map(::normalizeSearch)
            return if (query in labels) 0 else if (labels.any { it.startsWith(query) }) 1 else 2
        }
        return matches.values.sortedWith(compareBy<Match> { rank(it) }
            .thenBy { if (it.tag in dict.customAliases) 0 else 1 }
            .thenByDescending { frequencies[it.tag] ?: 0L }
            .thenBy { it.tag }).take(limit)
    }

}
