package com.lumetime.app.data

import com.lumetime.app.model.CountdownCategory
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.CountdownMode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.TimeZone

class StreakCheckInTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun utcMillis(year: Int, monthOneBased: Int, day: Int, hour: Int): Long {
        return Calendar.getInstance(utc).apply {
            clear()
            set(year, monthOneBased - 1, day, hour, 0, 0)
        }.timeInMillis
    }

    @Test
    fun `checkInMilestone increments once per calendar day and resetMilestone clears streak`() {
        val tempFile = File.createTempFile("lumetime_streak_test", ".json").apply {
            writeText("[]")
            deleteOnExit()
        }
        val store = CountdownStore(tempFile)
        val initial = CountdownItem(
            id = "habit-1",
            title = "Workout Streak",
            targetEpochMillis = utcMillis(2026, 11, 20, 8),
            startEpochMillis = utcMillis(2026, 11, 20, 8),
            mode = CountdownMode.COUNT_UP,
            category = CountdownCategory.MILESTONE
        )
        store.upsert(initial)

        val day1Morning = utcMillis(2026, 11, 27, 9)
        val day1Evening = utcMillis(2026, 11, 27, 21)
        val day2Morning = utcMillis(2026, 11, 28, 8)

        val afterFirst = store.checkInMilestone("habit-1", day1Morning, utc).first()
        assertEquals(1, afterFirst.checkInCount)
        assertEquals("2026-11-27", afterFirst.lastCheckInDayKey)

        // Second check-in same day should not double count
        val afterSameDay = store.checkInMilestone("habit-1", day1Evening, utc).first()
        assertEquals(1, afterSameDay.checkInCount)

        // Next day check-in increments cleanly and persists via JSON
        store.checkInMilestone("habit-1", day2Morning, utc)
        val reloaded = CountdownStore(tempFile).loadAll(forceReload = true).first()
        assertEquals(2, reloaded.checkInCount)
        assertEquals("2026-11-28", reloaded.lastCheckInDayKey)

        // Reset streak clears check-ins and moves anchor timestamp
        val afterReset = store.resetMilestone("habit-1", day2Morning).first()
        assertEquals(0, afterReset.checkInCount)
        assertEquals("", afterReset.lastCheckInDayKey)
        assertEquals(day2Morning, afterReset.targetEpochMillis)
    }
}
