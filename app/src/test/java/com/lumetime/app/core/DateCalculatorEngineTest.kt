package com.lumetime.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DateCalculatorEngineTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun utcDate(year: Int, monthOneBased: Int, day: Int): Long {
        return Calendar.getInstance(utc).apply {
            clear()
            set(year, monthOneBased - 1, day, 0, 0, 0)
        }.timeInMillis
    }

    @Test
    fun `computeDateDifference returns exact years months days and weekdays`() {
        // 2024-01-01 (Monday) to 2025-03-15
        val start = utcDate(2024, 1, 1)
        val end = utcDate(2025, 3, 15)

        val diff = DateCalculatorEngine.computeDateDifference(start, end, utc)

        assertTrue(diff.startEarlierOrEqual)
        assertEquals(1, diff.years)
        assertEquals(2, diff.months)
        assertEquals(14, diff.days)
        // 2024 is leap year (366 days) + Jan(31) + Feb(28) + 14 = 439 days
        assertEquals(439L, diff.totalDays)
        assertEquals(62L, diff.weeks)
        assertEquals(5L, diff.remainingDaysAfterWeeks)
        // 62 full weeks * 5 weekdays = 310 + Mon..Fri (5 days) minus weekend = 315? Let's verify Mon+Tue+Wed+Thu+Fri:
        // Wait: 62 weeks = 434 days (ends Sun before 2025-03-10), extra 5 days = Mon(10)..Fri(14) -> 5 weekdays! Total 315.
        assertEquals(315L, diff.weekdaysCount)
    }

    @Test
    fun `computeDateDifference handles reversed dates cleanly`() {
        val earlier = utcDate(2026, 11, 1)
        val later = utcDate(2026, 11, 10)

        val diff = DateCalculatorEngine.computeDateDifference(later, earlier, utc)

        assertFalse(diff.startEarlierOrEqual)
        assertEquals(9L, diff.totalDays)
    }

    @Test
    fun `addDays adds positive and negative day offsets accurately`() {
        val base = utcDate(2026, 2, 20)
        val plusTen = DateCalculatorEngine.addDays(base, 10, utc)
        val minusTwenty = DateCalculatorEngine.addDays(base, -20, utc)

        assertEquals(utcDate(2026, 3, 2), plusTen)
        assertEquals(utcDate(2026, 1, 31), minusTwenty)
    }
}
