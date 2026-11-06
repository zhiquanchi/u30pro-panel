package com.u30pro.panel.data.goform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun parseJsonObject(text: String): JsonObject? =
    runCatching { lenientJson.parseToJsonElement(text) as? JsonObject }.getOrNull()

fun parseJsonElement(text: String): JsonElement? =
    runCatching { lenientJson.parseToJsonElement(text) }.getOrNull()

/** goform scalars are always strings, and empty string means "not available". */
fun JsonObject.string(key: String): String? {
    val element = get(key) ?: return null
    if (element is JsonNull) return null
    val primitive = element as? JsonPrimitive ?: return null
    val content = primitive.contentOrNull ?: return null
    return content.takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
}

fun JsonObject.int(key: String): Int? = string(key)?.toIntOrNull()

fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()

fun JsonObject.double(key: String): Double? = string(key)?.toDoubleOrNull()

fun JsonObject.boolean(key: String): Boolean? = when (string(key)?.lowercase()) {
    "1", "true", "yes", "on" -> true
    "0", "false", "no", "off" -> false
    else -> null
}

fun JsonObject.firstOf(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { string(it) }

/**
 * station_list arrives either as a real array (U30 Air) or as a JSON-encoded
 * string (some F50 firmware). Accept both.
 */
fun parseObjectList(element: JsonElement?): List<JsonObject> = when (element) {
    null, JsonNull -> emptyList()
    is JsonArray -> element.mapNotNull { it as? JsonObject }
    is JsonPrimitive -> {
        val text = element.contentOrNull.orEmpty().trim()
        if (text.isEmpty()) {
            emptyList()
        } else {
            when (val parsed = parseJsonElement(text)) {
                is JsonArray -> parsed.mapNotNull { it as? JsonObject }
                is JsonObject -> listOf(parsed)
                else -> emptyList()
            }
        }
    }
    is JsonObject -> listOf(element)
    else -> emptyList()
}

fun String.semicolonList(): List<String> =
    split(';', ',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }