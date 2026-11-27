package com.lumetime.app.data

import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.model.CountdownCategory
import com.lumetime.app.model.CountdownIcon
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.CountdownMode
import com.lumetime.app.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CountdownStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `json codec round trips countdown items accurately`() {
        val original = listOf(
            CountdownItem(
                id = "id-1",
                title = "Final Exam",
                targetEpochMillis = 1_800_000_000_000L,
                startEpochMillis = 1_790_000_000_000L,
                createdAtMillis = 1_790_000_000_000L,
                mode = CountdownMode.COUNTDOWN,
                repeatRule = RepeatRule.YEARLY,
                category = CountdownCategory.EXAM,
                accentHex = "#FB7185",
                isPinned = true,
                emoji = CountdownIcon.BOOK.id,
                reminderEnabled = true
            ),
            CountdownItem(
                id = "id-2",
                title = "Gym Streak",
                targetEpochMillis = 1_780_000_000_000L,
                startEpochMillis = 1_780_000_000_000L,
                createdAtMillis = 1_780_000_000_000L,
                mode = CountdownMode.COUNT_UP,
                repeatRule = RepeatRule.NONE,
                category = CountdownCategory.MILESTONE,
                accentHex = "#34D399",
                isPinned = false,
                emoji = CountdownIcon.FLAME.id,
                reminderEnabled = false
            )
        )

        val encoded = CountdownJsonCodec.encode(original)
        val decoded = CountdownJsonCodec.decode(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `store seeds starter items once and handles upsert pin delete`() {
        val file = File(tempFolder.root, "countdowns.json")
        val now = 1_795_000_000_000L
        val store = CountdownStore(file)

        val seeded = store.loadAll(now)
        assertEquals(3, seeded.size)
        assertTrue(file.exists())

        val newItem = CountdownItem(
            id = "custom-1",
            title = "Goa Trip",
            targetEpochMillis = now + 5L * TimeCalculator.MILLIS_PER_DAY,
            startEpochMillis = now,
            createdAtMillis = now,
            category = CountdownCategory.TRAVEL,
            isPinned = true,
            emoji = CountdownIcon.PLANE.id
        )
        val afterAdd = store.upsert(newItem)
        assertEquals(4, afterAdd.size)
        assertEquals(1, afterAdd.count { it.isPinned })
        assertEquals("custom-1", afterAdd.first { it.isPinned }.id)

        // Reload from disk with fresh instance
        val reloaded = CountdownStore(file).loadAll(now)
        assertEquals(4, reloaded.size)
        assertEquals("Goa Trip", CountdownStore.selectHeroItem(reloaded, now)?.title)

        val deleted = store.delete("custom-1")
        assertNotNull(deleted)
        assertEquals(3, store.loadAll(now).size)
        assertNull(store.delete("nonexistent"))
    }

    @Test
    fun `store recovers gracefully from corrupt json file`() {
        val file = File(tempFolder.root, "corrupt.json")
        file.writeText("{not valid json")
        val store = CountdownStore(file)

        val items = store.loadAll(1_795_000_000_000L)
        assertTrue(items.isEmpty())
    }

    @Test
    fun `filterAndSort orders soonest upcoming countdowns first`() {
        val now = 1_795_000_000_000L
        val later = CountdownItem("2", "Later", now + 20L * TimeCalculator.MILLIS_PER_DAY, now)
        val sooner = CountdownItem("1", "Sooner", now + 3L * TimeCalculator.MILLIS_PER_DAY, now)
        val expired = CountdownItem("3", "Expired", now - 2L * TimeCalculator.MILLIS_PER_DAY, now - 10L * TimeCalculator.MILLIS_PER_DAY)
        val countUp = CountdownItem("4", "Streak", now - 5L * TimeCalculator.MILLIS_PER_DAY, now - 5L * TimeCalculator.MILLIS_PER_DAY, mode = CountdownMode.COUNT_UP)

        val sorted = CountdownStore.filterAndSort(
            listOf(expired, later, countUp, sooner),
            CountdownFilter.All,
            CountdownSortMode.SOONEST,
            now
        )

        assertEquals(listOf("1", "2", "4", "3"), sorted.map { it.id })

        val onlyCountUp = CountdownStore.filterAndSort(
            sorted,
            CountdownFilter.CountUp,
            CountdownSortMode.SOONEST,
            now
        )
        assertEquals(listOf("4"), onlyCountUp.map { it.id })
    }

    @Test
    fun `exportJson and importJson merge items and preserve single pin`() {
        val now = 1_795_000_000_000L
        val fileA = File(tempFolder.root, "a.json").apply { writeText("[]") }
        val storeA = CountdownStore(fileA)
        storeA.upsert(CountdownItem("1", "First", now + 10_000L, now, isPinned = true))

        val incomingJson = CountdownJsonCodec.encode(
            listOf(
                CountdownItem("2", "Imported Pin", now + 20_000L, now, isPinned = true),
                CountdownItem("3", "Imported Normal", now + 30_000L, now, isPinned = false)
            )
        )

        val importedCount = storeA.importJson(incomingJson)
        assertEquals(2, importedCount)

        val all = storeA.loadAll(now, forceReload = true)
        assertEquals(3, all.size)
        assertEquals(1, all.count { it.isPinned })
        assertEquals("1", all.first { it.isPinned }.id)

        val exported = storeA.exportJson(now)
        assertEquals(3, CountdownJsonCodec.decode(exported).size)
    }
}
