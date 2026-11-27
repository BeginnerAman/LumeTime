package com.lumetime.app.data

import android.content.Context
import com.lumetime.app.core.WorldCity
import com.lumetime.app.core.WorldClockEngine

/**
 * Saves user's pinned World Clock city IDs cleanly in SharedPreferences.
 */
object WorldClockStore {

    private const val PREFS_NAME = "lumetime_world_clock_prefs"
    private const val KEY_PINNED_IDS = "pinned_city_ids"

    fun loadPinnedIds(context: Context): Set<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PINNED_IDS, null)
        if (raw.isNullOrBlank()) {
            return setOf("ist", "lon", "nyc", "dxb", "tyo")
        }
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun togglePinned(context: Context, cityId: String): Set<String> {
        val current = loadPinnedIds(context).toMutableSet()
        if (current.contains(cityId) && current.size > 1) {
            current.remove(cityId)
        } else {
            current.add(cityId)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PINNED_IDS, current.joinToString(","))
            .apply()
        return current
    }

    fun loadPinnedCities(context: Context): List<WorldCity> {
        val pinned = loadPinnedIds(context)
        return WorldClockEngine.catalog.filter { pinned.contains(it.id) }
    }
}
