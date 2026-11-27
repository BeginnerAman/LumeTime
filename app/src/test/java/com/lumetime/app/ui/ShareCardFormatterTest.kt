package com.lumetime.app.ui

import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.model.CountdownCategory
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.CountdownMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ShareCardFormatterTest {

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
    fun `buildCountdownCopy formats countdown, count-up, and expired posters accurately`() {
        val now = utcMillis(2026, 11, 27, 12)
        val futureItem = CountdownItem(
            id = "c1",
            title = "Final Exam",
            targetEpochMillis = now + 10L * TimeCalculator.MILLIS_PER_DAY + 5L * TimeCalculator.MILLIS_PER_HOUR,
            startEpochMillis = now - 10L * TimeCalculator.MILLIS_PER_DAY,
            category = CountdownCategory.EXAM,
            emoji = "📚"
        )
        val futureBreakdown = TimeCalculator.calculateBreakdown(futureItem, now, utc)
        val futureCopy = ShareCardGenerator.buildCountdownCopy(futureItem, futureBreakdown, "Dec 7, 2026")

        assertEquals("EXAM • COUNTDOWN", futureCopy.badge)
        assertEquals("📚 Final Exam", futureCopy.title)
        assertEquals("10d", futureCopy.heroNumber)
        assertEquals("05h 00m", futureCopy.heroUnit)
        assertTrue(futureCopy.subline.contains("245 hours left"))

        val streakItem = CountdownItem(
            id = "c2",
            title = "Clean Streak",
            targetEpochMillis = now - 4L * TimeCalculator.MILLIS_PER_DAY,
            startEpochMillis = now - 4L * TimeCalculator.MILLIS_PER_DAY,
            mode = CountdownMode.COUNT_UP,
            category = CountdownCategory.MILESTONE,
            emoji = "✨"
        )
        val streakBreakdown = TimeCalculator.calculateBreakdown(streakItem, now, utc)
        val streakCopy = ShareCardGenerator.buildCountdownCopy(streakItem, streakBreakdown, "Nov 23, 2026")

        assertEquals("MILESTONE • COUNT-UP", streakCopy.badge)
        assertEquals("+4d", streakCopy.heroNumber)
        assertTrue(streakCopy.subline.contains("+96 total hours elapsed"))

        val expiredItem = CountdownItem(
            id = "c3",
            title = "Done Event",
            targetEpochMillis = now - TimeCalculator.MILLIS_PER_DAY,
            startEpochMillis = now - 5L * TimeCalculator.MILLIS_PER_DAY
        )
        val expiredCopy = ShareCardGenerator.buildCountdownCopy(
            expiredItem,
            TimeCalculator.calculateBreakdown(expiredItem, now, utc),
            "Nov 26, 2026"
        )
        assertEquals("00d", expiredCopy.heroNumber)
        assertEquals("target reached", expiredCopy.heroUnit)
    }

    @Test
    fun `buildYearCopy renders year pulse poster copy accurately`() {
        val now = utcMillis(2026, 11, 27, 12)
        val yearProgress = TimeCalculator.computeYearProgress(now, utc)
        val copy = ShareCardGenerator.buildYearCopy(yearProgress)

        assertEquals("2026 • YEAR PULSE", copy.badge)
        assertEquals("Day 331 of 365", copy.title)
        assertEquals("34", copy.heroNumber)
        assertEquals("days left", copy.heroUnit)
        assertTrue(copy.subline.contains("828 hours left"))
        assertEquals("4 weeks remaining in 2026", copy.footerMeta)
    }
}
