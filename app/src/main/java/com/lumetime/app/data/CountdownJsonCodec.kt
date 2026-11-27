package com.lumetime.app.data

import com.lumetime.app.model.CountdownCategory
import com.lumetime.app.model.CountdownIcon
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.CountdownMode
import com.lumetime.app.model.RepeatRule
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight, dependency-free JSON codec for [CountdownItem] lists.
 */
object CountdownJsonCodec {

    fun encode(items: List<CountdownItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("targetEpochMillis", item.targetEpochMillis)
                put("startEpochMillis", item.startEpochMillis)
                put("createdAtMillis", item.createdAtMillis)
                put("mode", item.mode.name)
                put("repeatRule", item.repeatRule.name)
                put("category", item.category.name)
                put("accentHex", item.accentHex)
                put("isPinned", item.isPinned)
                put("emoji", item.emoji)
                put("reminderEnabled", item.reminderEnabled)
                put("note", item.note)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun decode(rawJson: String): List<CountdownItem> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return emptyList()
        val array = JSONArray(trimmed)
        val result = ArrayList<CountdownItem>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id", "").trim()
            val title = obj.optString("title", "").trim()
            if (id.isEmpty() || title.isEmpty()) continue

            val category = enumOrDefault(obj.optString("category"), CountdownCategory.GENERAL)
            val mode = enumOrDefault(obj.optString("mode"), CountdownMode.COUNTDOWN)
            val repeatRule = enumOrDefault(obj.optString("repeatRule"), RepeatRule.NONE)

            result.add(
                CountdownItem(
                    id = id,
                    title = title,
                    targetEpochMillis = obj.optLong("targetEpochMillis", 0L),
                    startEpochMillis = obj.optLong("startEpochMillis", 0L),
                    createdAtMillis = obj.optLong("createdAtMillis", 0L),
                    mode = mode,
                    repeatRule = repeatRule,
                    category = category,
                    accentHex = obj.optString("accentHex", "#67E8F9"),
                    isPinned = obj.optBoolean("isPinned", false),
                    emoji = CountdownIcon.fromStored(
                        obj.optString("emoji", ""),
                        category
                    ).id,
                    reminderEnabled = obj.optBoolean("reminderEnabled", false),
                    note = obj.optString("note", "").trim()
                )
            )
        }
        return result
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, fallback: T): T {
        if (name.isNullOrBlank()) return fallback
        return enumValues<T>().firstOrNull { it.name == name } ?: fallback
    }
}
