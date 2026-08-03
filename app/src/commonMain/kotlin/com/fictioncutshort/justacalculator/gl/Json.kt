package com.fictioncutshort.justacalculator.gl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A minimal `org.json.JSONObject`/`JSONArray` work-alike over
 * kotlinx-serialization.
 *
 * Deliberately mirrors the org.json method names — `getInt`, `optJSONArray`,
 * `length()` — so the glTF parser's ~30 call sites port unchanged. Only the
 * methods that parser actually uses are here; this is not a general JSON facade.
 */
class JsonObj(private val element: JsonObject) {

    companion object {
        private val parser = Json { ignoreUnknownKeys = true; isLenient = true }
        fun parse(text: String): JsonObj = JsonObj(parser.parseToJsonElement(text).jsonObject)
    }

    fun has(key: String): Boolean = element.containsKey(key)

    fun getJSONObject(key: String): JsonObj = JsonObj(element.getValue(key).jsonObject)
    fun optJSONObject(key: String): JsonObj? = element[key]?.let { JsonObj(it.jsonObject) }

    fun getJSONArray(key: String): JsonArr = JsonArr(element.getValue(key).jsonArray)
    fun optJSONArray(key: String): JsonArr? = element[key]?.let { JsonArr(it.jsonArray) }

    fun getInt(key: String): Int = element.getValue(key).jsonPrimitive.content.toDouble().toInt()
    fun optInt(key: String, fallback: Int = 0): Int =
        element[key]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt() ?: fallback

    fun getDouble(key: String): Double = element.getValue(key).jsonPrimitive.content.toDouble()

    fun optBoolean(key: String, fallback: Boolean = false): Boolean =
        (element[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: fallback

    fun getString(key: String): String = element.getValue(key).jsonPrimitive.content
    fun optString(key: String, fallback: String = ""): String =
        (element[key] as? JsonPrimitive)?.content ?: fallback

    fun optDouble(key: String, fallback: Double = 0.0): Double =
        (element[key] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: fallback
}

class JsonArr(private val element: JsonArray) {
    fun length(): Int = element.size
    fun getJSONObject(index: Int): JsonObj = JsonObj(element[index].jsonObject)
    fun getJSONArray(index: Int): JsonArr = JsonArr(element[index].jsonArray)
    fun getInt(index: Int): Int = element[index].jsonPrimitive.content.toDouble().toInt()
    fun getDouble(index: Int): Double = element[index].jsonPrimitive.content.toDouble()
    fun getString(index: Int): String = element[index].jsonPrimitive.content

    // Overpass returns heterogeneous elements, so the reader skips what it does
    // not understand rather than assuming every entry has the same shape.
    fun optJSONObject(index: Int): JsonObj? =
        (element.getOrNull(index) as? JsonObject)?.let { JsonObj(it) }

    fun optJSONArray(index: Int): JsonArr? =
        (element.getOrNull(index) as? JsonArray)?.let { JsonArr(it) }
}
