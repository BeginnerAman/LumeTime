package com.lumetime.app.data

import android.content.Context
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.model.CountdownCategory
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.CountdownMode
import com.lumetime.app.model.RepeatRule
import java.io.File
import java.util.Calendar
import java.util.UUID

/**
 * Filter chips shown above the countdown list.
 */
sealed class CountdownFilter(val label: String) {
    data object All : CountdownFilter("All")
    data object CountUp : CountdownFilter("Count-Up")
    data class Category(val category: CountdownCategory) :
        CountdownFilter("${category.defaultEmoji} ${category.label}")
}

enum class CountdownSortMode(val label: String) {
    SOONEST("Soonest"),
    PINNED_FIRST("Pinned"),
    TITLE("A–Z");

    fun next(): CountdownSortMode {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

/**
 * Atomic JSON file store for [CountdownItem] entries with in-memory caching,
 * first-launch starter templates, and pure sorting/filtering helpers.
 */
class CountdownStore(
    private val storageFile: File,
    private val prefs: android.content.SharedPreferences? = null
) {

    private var cachedItems: MutableList<CountdownItem>? = null

    @Synchronized
    fun loadAll(nowMillis: Long = System.currentTimeMillis()): List<CountdownItem> {
        cachedItems?.let { return it.toList() }

        val loaded = if (storageFile.exists()) {
            runCatching {
                CountdownJsonCodec.decode(storageFile.readText(Charsets.UTF_8))
            }.getOrElse { emptyList() }
        } else {
            emptyList()
        }

        val items = loaded.toMutableList()
        val seeded = prefs?.getBoolean(KEY_SEEDED, false) ?: storageFile.exists()
        if (!seeded && items.isEmpty()) {
            items.addAll(createStarterItems(nowMillis))
            persistLocked(items)
            prefs?.edit()?.putBoolean(KEY_SEEDED, true)?.apply()
        } else if (!seeded) {
            prefs?.edit()?.putBoolean(KEY_SEEDED, true)?.apply()
        }

        cachedItems = items
        return items.toList()
    }

    @Synchronized
    fun upsert(item: CountdownItem): List<CountdownItem> {
        val list = ensureLoadedLocked()
        val normalized = if (item.isPinned) {
            list.map { existing ->
                if (existing.id != item.id && existing.isPinned) existing.copy(isPinned = false)
                else existing
            }.toMutableList()
        } else {
            list
        }

        val idx = normalized.indexOfFirst { it.id == item.id }
        if (idx >= 0) {
            normalized[idx] = item
        } else {
            normalized.add(0, item)
        }
        cachedItems = normalized
        persistLocked(normalized)
        return normalized.toList()
    }

    @Synchronized
    fun delete(id: String): CountdownItem? {
        val list = ensureLoadedLocked()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val removed = list.removeAt(idx)
        persistLocked(list)
        return removed
    }

    @Synchronized
    fun togglePin(id: String): List<CountdownItem> {
        val list = ensureLoadedLocked()
        val target = list.firstOrNull { it.id == id } ?: return list.toList()
        val newPinState = !target.isPinned
        val updated = list.map { item ->
            when {
                item.id == id -> item.copy(isPinned = newPinState)
                newPinState && item.isPinned -> item.copy(isPinned = false)
                else -> item
            }
        }.toMutableList()
        cachedItems = updated
        persistLocked(updated)
        return updated.toList()
    }

    private fun ensureLoadedLocked(): MutableList<CountdownItem> {
        cachedItems?.let { return it }
        loadAll()
        return cachedItems ?: mutableListOf<CountdownItem>().also { cachedItems = it }
    }

    private fun persistLocked(items: List<CountdownItem>) {
        storageFile.parentFile?.mkdirs()
        val tempFile = File(storageFile.parentFile, "${storageFile.name}.tmp")
        tempFile.writeText(CountdownJsonCodec.encode(items), Charsets.UTF_8)
        if (!tempFile.renameTo(storageFile)) {
            storageFile.writeText(CountdownJsonCodec.encode(items), Charsets.UTF_8)
            tempFile.delete()
        }
    }

    companion object {
        private const val PREFS_NAME = "lumetime_prefs"
        private const val FILE_NAME = "countdowns.json"
        private const val KEY_SEEDED = "starter_countdowns_seeded"

        fun create(context: Context): CountdownStore {
            val file = File(context.filesDir, FILE_NAME)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return CountdownStore(file, prefs)
        }

        fun selectHeroItem(
            items: List<CountdownItem>,
            nowMillis: Long
        ): CountdownItem? {
            if (items.isEmpty()) return null
            items.firstOrNull { it.isPinned }?.let { return it }

            val activeCountdowns = items
                .filter { it.mode == CountdownMode.COUNTDOWN }
                .map { it to TimeCalculator.calculateBreakdown(it, nowMillis) }
                .filter { !it.second.isExpired }
                .minByOrNull { it.second.effectiveTargetMillis }

            return activeCountdowns?.first ?: items.first()
        }

        fun filterAndSort(
            items: List<CountdownItem>,
            filter: CountdownFilter,
            sortMode: CountdownSortMode,
            nowMillis: Long
        ): List<CountdownItem> {
            val filtered = when (filter) {
                CountdownFilter.All -> items
                CountdownFilter.CountUp -> items.filter { it.mode == CountdownMode.COUNT_UP }
                is CountdownFilter.Category -> items.filter { it.category == filter.category }
            }

            return when (sortMode) {
                CountdownSortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
                CountdownSortMode.PINNED_FIRST -> filtered.sortedWith(
                    compareByDescending<CountdownItem> { it.isPinned }
                        .thenBy { sortUrgencyKey(it, nowMillis) }
                )
                CountdownSortMode.SOONEST -> filtered.sortedWith(
                    compareBy<CountdownItem> { sortUrgencyKey(it, nowMillis) }
                        .thenByDescending { it.isPinned }
                )
            }
        }

        private fun sortUrgencyKey(item: CountdownItem, nowMillis: Long): Long {
            val breakdown = TimeCalculator.calculateBreakdown(item, nowMillis)
            return when {
                item.mode == CountdownMode.COUNT_UP -> Long.MAX_VALUE - 10_000L - breakdown.days
                breakdown.isExpired -> Long.MAX_VALUE
                else -> breakdown.effectiveTargetMillis
            }
        }

        fun createStarterItems(nowMillis: Long): List<CountdownItem> {
            val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val nextYear = cal.get(Calendar.YEAR) + 1
            val newYearMillis = Calendar.getInstance().apply {
                clear()
                set(Calendar.YEAR, nextYear)
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis

            val sprintTarget = nowMillis + 30L * TimeCalculator.MILLIS_PER_DAY
            val streakStart = nowMillis - 7L * TimeCalculator.MILLIS_PER_DAY

            return listOf(
                CountdownItem(
                    id = UUID.randomUUID().toString(),
                    title = "New Year $nextYear",
                    targetEpochMillis = newYearMillis,
                    startEpochMillis = nowMillis,
                    createdAtMillis = nowMillis,
                    mode = CountdownMode.COUNTDOWN,
                    repeatRule = RepeatRule.YEARLY,
                    category = CountdownCategory.GENERAL,
                    accentHex = "#67E8F9",
                    isPinned = true,
                    emoji = "🎉"
                ),
                CountdownItem(
                    id = UUID.randomUUID().toString(),
                    title = "30-Day Focus Sprint",
                    targetEpochMillis = sprintTarget,
                    startEpochMillis = nowMillis,
                    createdAtMillis = nowMillis,
                    mode = CountdownMode.COUNTDOWN,
                    repeatRule = RepeatRule.NONE,
                    category = CountdownCategory.GOAL,
                    accentHex = "#A78BFA",
                    isPinned = false,
                    emoji = "🎯"
                ),
                CountdownItem(
                    id = UUID.randomUUID().toString(),
                    title = "Daily Journey Streak",
                    targetEpochMillis = streakStart,
                    startEpochMillis = streakStart,
                    createdAtMillis = nowMillis,
                    mode = CountdownMode.COUNT_UP,
                    repeatRule = RepeatRule.NONE,
                    category = CountdownCategory.MILESTONE,
                    accentHex = "#34D399",
                    isPinned = false,
                    emoji = "✨"
                )
            )
        }
    }
}
