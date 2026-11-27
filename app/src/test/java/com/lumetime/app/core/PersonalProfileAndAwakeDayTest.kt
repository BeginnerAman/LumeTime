package com.lumetime.app.core

import com.lumetime.app.data.PersonalProfile
import com.lumetime.app.data.PersonalProfilePrefs
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class PersonalProfileAndAwakeDayTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun utcMillis(year: Int, monthOneBased: Int, day: Int, hour: Int, minute: Int = 0): Long {
        return Calendar.getInstance(utc).apply {
            clear()
            set(year, monthOneBased - 1, day, hour, minute, 0)
        }.timeInMillis
    }

    @Test
    fun `buildGreeting adapts across time of day and optional user name`() {
        assertEquals("Good Morning, Aman", PersonalProfilePrefs.buildGreeting(8, "Aman"))
        assertEquals("Good Afternoon", PersonalProfilePrefs.buildGreeting(14, "  "))
        assertEquals("Good Evening, Aman", PersonalProfilePrefs.buildGreeting(19, "Aman"))
        assertEquals("Late Night Focus", PersonalProfilePrefs.buildGreeting(2, ""))
    }

    @Test
    fun `applyCompletedSession accumulates today focus stats and resets cleanly next day`() {
        val day1 = utcMillis(2026, 11, 27, 15)
        val day2 = utcMillis(2026, 11, 28, 9)

        var profile = PersonalProfile(userName = "Aman", dailyFocusGoalMinutes = 60)
        profile = PersonalProfilePrefs.applyCompletedSession(profile, 25, day1, utc)
        profile = PersonalProfilePrefs.applyCompletedSession(profile, 15, day1, utc)

        assertEquals(40, profile.focusedMinutesToday)
        assertEquals(2, profile.focusedSessionsToday)
        assertEquals("2026-11-27", profile.focusedDayKey)

        // Next day normalizes cleanly to zero before logging new session
        val rolled = PersonalProfilePrefs.normalizeForToday(profile, day2, utc)
        assertEquals(0, rolled.focusedMinutesToday)
        assertEquals(0, rolled.focusedSessionsToday)
        assertEquals("2026-11-28", rolled.focusedDayKey)
    }

    @Test
    fun `computeAwakeDayProgress computes mid-day remaining awake window accurately`() {
        // Wake 7 AM, Sleep 11 PM (16 hours window). At 3 PM (15:00) -> 8h elapsed = 50%
        val threePm = utcMillis(2026, 11, 27, 15, 0)
        val awake = TimeCalculator.computeAwakeDayProgress(threePm, wakeHour = 7, sleepHour = 23, timeZone = utc)

        assertEquals("ACTIVE DAY • 7 AM–11 PM", awake.title)
        assertEquals(16, awake.totalUnits)
        assertEquals("8h 00m", awake.summaryLeft)
        assertEquals("awake left", awake.unitLabel)
        assertEquals(50.0f, awake.percentCompleted, 0.1f)
    }

    @Test
    fun `buildLast7DaysStats returns ordered 7 day window preserving historical minutes`() {
        val day1 = utcMillis(2026, 11, 25, 10)
        val day2 = utcMillis(2026, 11, 27, 16)

        var profile = PersonalProfile(dailyFocusGoalMinutes = 60)
        profile = PersonalProfilePrefs.applyCompletedSession(profile, 45, day1, utc)
        profile = PersonalProfilePrefs.applyCompletedSession(profile, 30, day2, utc)

        val stats = PersonalProfilePrefs.buildLast7DaysStats(profile, day2, utc)
        assertEquals(7, stats.size)
        assertEquals("2026-11-27", stats.last().dayKey)
        assertEquals(30, stats.last().minutes)
        assertEquals(45, stats.first { it.dayKey == "2026-11-25" }.minutes)
    }
}
