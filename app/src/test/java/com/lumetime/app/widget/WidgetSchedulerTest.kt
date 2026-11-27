package com.lumetime.app.widget

import com.lumetime.app.core.TimeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WidgetSchedulerTest {

    @Test
    fun `nextTopOfHourMillis aligns cleanly to next exact hour`() {
        val baseHour = 1_795_003_200_000L // divisible by 3,600,000
        assertEquals(0L, baseHour % TimeCalculator.MILLIS_PER_HOUR)

        val midHour = baseHour + 15L * TimeCalculator.MILLIS_PER_MINUTE
        assertEquals(baseHour + TimeCalculator.MILLIS_PER_HOUR, WidgetScheduler.nextTopOfHourMillis(midHour))

        // Exact boundary advances a full hour
        assertEquals(baseHour + TimeCalculator.MILLIS_PER_HOUR, WidgetScheduler.nextTopOfHourMillis(baseHour))
    }

    @Test
    fun `nextMidnightMillis returns next day 00_00_01 in given timezone`() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 27, 23, 45, 0)
        }.timeInMillis

        val nextMidnight = WidgetScheduler.nextMidnightMillis(now, utc)
        val cal = Calendar.getInstance(utc).apply { timeInMillis = nextMidnight }

        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, cal.get(Calendar.MONTH))
        assertEquals(28, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertTrue(nextMidnight > now)
    }

    @Test
    fun `WidgetPrefs enums resolve defaults cleanly`() {
        assertEquals(WidgetPrefs.WidgetGlassStyle.DARK, WidgetPrefs.WidgetGlassStyle.fromId(null))
        assertEquals(WidgetPrefs.WidgetGlassStyle.FROST, WidgetPrefs.WidgetGlassStyle.fromId("frost"))
        assertEquals(WidgetPrefs.WidgetOpacity.BALANCED, WidgetPrefs.WidgetOpacity.fromId("nope"))
        assertEquals(WidgetPrefs.WidgetOpacity.CLEAR, WidgetPrefs.WidgetOpacity.fromId("clear"))
    }
}
