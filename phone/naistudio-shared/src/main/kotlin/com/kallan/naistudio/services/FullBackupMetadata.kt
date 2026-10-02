package com.kallan.naistudio.services

import org.json.JSONArray
import org.json.JSONObject

/** Android preferences are typed; preserve unknown keys and string sets too. */
object FullBackupMetadata {
    fun validPreferenceName(name: String) = name.matches(Regex("[A-Za-z0-9._-]{1,128}")) && name != "nai_secure"

    fun encodePreferences(stores: Map<String, Map<String, *>>): JSONObject = JSONObject().apply {
        stores.forEach { (name, values) ->
            require(validPreferenceName(name))
            put(name, JSONObject().apply {
                values.forEach { (key, value) ->
                    val type = when (value) {
                        is String -> "string"
                        is Boolean -> "boolean"
                        is Int -> "int"
                        is Long -> "long"
                        is Float -> "float"
                        is Set<*> -> "stringSet"
                        else -> error("Unsupported preference type")
                    }
                    if (value is Set<*>) require(value.all { it is String })
                    put(key, JSONObject().put("type", type).put("value", if (value is Set<*>) JSONArray(value.toList()) else value))
                }
            })
        }
    }

    fun decodePreferences(stores: JSONObject): Map<String, Map<String, Any>> = stores.keys().asSequence().associateWith { name ->
        require(validPreferenceName(name)) { "Invalid preference store" }
        val values = stores.getJSONObject(name)
        values.keys().asSequence().associateWith { key ->
            val item = values.getJSONObject(key)
            when (item.getString("type")) {
                "string" -> item.getString("value")
                "boolean" -> item.getBoolean("value")
                "int" -> item.getLong("value").let { require(it in Int.MIN_VALUE..Int.MAX_VALUE); it.toInt() }
                "long" -> item.getLong("value")
                "float" -> item.getDouble("value").toFloat().also { require(it.isFinite()) }
                "stringSet" -> item.getJSONArray("value").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                else -> error("Invalid preference type")
            }
        }
    }

    /** Only entire path values change; ordinary prompt text containing paths stays unchanged. */
    fun remapString(value: String, oldRoot: String, newRoot: String, external: Map<String, String>): String {
        external[value]?.let { return it }
        if (value == oldRoot || value.startsWith(oldRoot.trimEnd('/') + "/")) return newRoot + value.removePrefix(oldRoot)
        if (!value.trimStart().startsWith('{') && !value.trimStart().startsWith('[')) return value
        fun remap(v: Any?): Any? = when (v) {
            is String -> external[v] ?: if (v == oldRoot || v.startsWith(oldRoot.trimEnd('/') + "/")) newRoot + v.removePrefix(oldRoot) else v
            is JSONObject -> v.apply { keys().asSequence().toList().forEach { put(it, remap(get(it))) } }
            is JSONArray -> v.apply { for (i in 0 until length()) put(i, remap(get(i))) }
            else -> v
        }
        return runCatching { remap(if (value.trimStart().startsWith('{')) JSONObject(value) else JSONArray(value)).toString() }.getOrDefault(value)
    }

    fun pathReferences(stores: Map<String, Map<String, *>>): Set<String> {
        val result = mutableSetOf<String>()
        fun walk(value: Any?) {
            when (value) {
                is JSONObject -> value.keys().asSequence().forEach { walk(value.get(it)) }
                is JSONArray -> for (i in 0 until value.length()) walk(value.get(i))
                is String -> {
                    if (value.startsWith('/')) result.add(value)
                    else if (value.trimStart().startsWith('{')) runCatching { walk(JSONObject(value)) }
                    else if (value.trimStart().startsWith('[')) runCatching { walk(JSONArray(value)) }
                }
            }
        }
        stores.values.forEach { values -> values.values.forEach { walk(it) } }
        return result
    }
}
