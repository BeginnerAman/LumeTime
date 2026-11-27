package com.lumetime.app.ui

import com.lumetime.app.core.TimeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class YearDisplayFormatterTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun utcMillis(year: Int, monthOneBased: Int, day: Int, hour: Int = 0): Long {
        return Calendar.getInstance(utc).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthOneBased - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
        }.timeInMillis
    }

    @Test
    fun `metric mode cycles cleanly through all 4 modes and wraps around`() {
        var mode = YearPulseMetricMode.DAYS_LEFT
        mode = mode.next()
        assertEquals(YearPulseMetricMode.HOURS_LEFT, mode)
        mode = mode.next()
        assertEquals(YearPulseMetricMode.PERCENT_DONE, mode)
        mode = mode.next()
        assertEquals(YearPulseMetricMode.WEEKS_LEFT, mode)
        mode = mode.next()
        assertEquals(YearPulseMetricMode.DAYS_LEFT, mode)
    }

    @Test
    fun `format renders all 4 year pulse modes accurately`() {
        val now = utcMillis(2026, 11, 27, 12)
        val progress = TimeCalculator.computeYearProgress(now, utc)

        val daysView = YearDisplayFormatter.format(progress, YearPulseMetricMode.DAYS_LEFT, now, utc)
        assertEquals("34", daysView.primaryValue)
        assertEquals("days left", daysView.primaryUnit)
        assertEquals("Day 331 / 365", daysView.dayOfYearBadge)
        assertTrue(daysView.liveFooter.contains("34d 12h 00m 00s until 2027"))

        val hoursView = YearDisplayFormatter.format(progress, YearPulseMetricMode.HOURS_LEFT, now, utc)
        assertEquals("828", hoursView.primaryValue)
        assertEquals("hours left", hoursView.primaryUnit)

        val pctView = YearDisplayFormatter.format(progress, YearPulseMetricMode.PERCENT_DONE, now, utc)
        assertEquals("completed", pctView.primaryUnit)
        assertTrue(pctView.primaryValue.endsWith("%"))

        val weeksView = YearDisplayFormatter.format(progress, YearPulseMetricMode.WEEKS_LEFT, now, utc)
        assertEquals("4w 6d", weeksView.primaryValue)
        assertEquals("remaining", weeksView.primaryUnit)
    }

    @Test
    fun `AppTheme and AppearanceMode resolve defaults safely`() {
        assertEquals(6, ThemeManager.AppTheme.entries.size)
        assertEquals(ThemeManager.AppTheme.OBSIDIAN, ThemeManager.AppTheme.fromId("unknown"))
        assertEquals(ThemeManager.AppTheme.AURORA, ThemeManager.AppTheme.fromId("aurora"))
        assertEquals(ThemeManager.AppearanceMode.DARK, ThemeManager.AppearanceMode.fromId(null))
        assertEquals(ThemeManager.AppearanceMode.AMOLED, ThemeManager.AppearanceMode.fromId("amoled"))
        assertEquals(ThemeManager.AppearanceMode.LIGHT, ThemeManager.AppearanceMode.fromId("light"))
    }
}
