package com.lumetime.app.core

import com.lumetime.app.model.CountdownCategory
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.CountdownMode
import com.lumetime.app.model.RepeatRule
import com.lumetime.app.model.TimePulseScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimeCalculatorTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private fun utcMillis(
        year: Int,
        monthOneBased: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0
    ): Long {
        return Calendar.getInstance(utc).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthOneBased - 1)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `computeYearProgress handles standard year midpoint accurately`() {
        val now = utcMillis(2026, 11, 27, 12, 0, 0)
        val result = TimeCalculator.computeYearProgress(now, utc)

        assertEquals(2026, result.year)
        assertFalse(result.isLeapYear)
        assertEquals(365, result.totalDays)
        assertEquals(331, result.todayDayOfYear)
        assertEquals(330, result.daysPassed)
        assertEquals(34L, result.daysLeft)
        assertEquals(34L * 24L + 12L, result.hoursLeft)
        assertEquals(4L, result.weeksLeft)
        assertTrue(result.percentCompleted > 90f && result.percentCompleted < 91f)
    }

    @Test
    fun `computeYearProgress handles leap year February and totalDays`() {
        val now = utcMillis(2024, 2, 29, 0, 0, 0)
        val result = TimeCalculator.computeYearProgress(now, utc)

        assertEquals(2024, result.year)
        assertTrue(result.isLeapYear)
        assertEquals(366, result.totalDays)
        assertEquals(60, result.todayDayOfYear)
        assertEquals(59, result.daysPassed)
        assertEquals(307L, result.daysLeft)
    }

    @Test
    fun `calculateBreakdown returns exact days hours minutes seconds and progress`() {
        val start = utcMillis(2026, 11, 1, 0, 0, 0)
        val target = utcMillis(2026, 11, 11, 0, 0, 0)
        val now = utcMillis(2026, 11, 6, 12, 30, 45)

        val item = CountdownItem(
            id = "exam-1",
            title = "Final Exam",
            targetEpochMillis = target,
            startEpochMillis = start,
            createdAtMillis = start,
            category = CountdownCategory.EXAM
        )

        val breakdown = TimeCalculator.calculateBreakdown(item, now, utc)

        assertFalse(breakdown.isExpired)
        assertFalse(breakdown.isCountUp)
        assertEquals(4L, breakdown.days)
        assertEquals(11, breakdown.hours)
        assertEquals(29, breakdown.minutes)
        assertEquals(15, breakdown.seconds)
        assertEquals(107L, breakdown.totalHours)
        assertEquals(0.5521f, breakdown.progress, 0.01f)
    }

    @Test
    fun `calculateBreakdown marks past non-repeating countdown as expired`() {
        val start = utcMillis(2026, 1, 1)
        val target = utcMillis(2026, 5, 1)
        val now = utcMillis(2026, 6, 1)

        val item = CountdownItem(
            id = "expired-1",
            title = "Past Trip",
            targetEpochMillis = target,
            startEpochMillis = start
        )

        val breakdown = TimeCalculator.calculateBreakdown(item, now, utc)

        assertTrue(breakdown.isExpired)
        assertEquals(0L, breakdown.days)
        assertEquals(0, breakdown.hours)
        assertEquals(0, breakdown.minutes)
        assertEquals(0, breakdown.seconds)
        assertEquals(1f, breakdown.progress, 0.0001f)
    }

    @Test
    fun `calculateBreakdown supports count-up milestone mode`() {
        val milestone = utcMillis(2026, 10, 1, 8, 0, 0)
        val now = utcMillis(2026, 10, 15, 14, 20, 10)

        val item = CountdownItem(
            id = "streak-1",
            title = "Clean Streak",
            targetEpochMillis = milestone,
            startEpochMillis = milestone,
            mode = CountdownMode.COUNT_UP,
            category = CountdownCategory.MILESTONE
        )

        val breakdown = TimeCalculator.calculateBreakdown(item, now, utc)

        assertTrue(breakdown.isCountUp)
        assertFalse(breakdown.isExpired)
        assertEquals(14L, breakdown.days)
        assertEquals(6, breakdown.hours)
        assertEquals(20, breakdown.minutes)
        assertEquals(10, breakdown.seconds)
        assertEquals(14L * 24L + 6L, breakdown.totalHours)
    }

    @Test
    fun `yearly repeat rolls over leap day to Feb 28 in non-leap year`() {
        val anchor = utcMillis(2024, 2, 29, 9, 0, 0)
        val now = utcMillis(2024, 3, 1, 9, 0, 0)

        val item = CountdownItem(
            id = "bday-leap",
            title = "Leap Birthday",
            targetEpochMillis = anchor,
            startEpochMillis = utcMillis(2024, 1, 1),
            repeatRule = RepeatRule.YEARLY,
            category = CountdownCategory.BIRTHDAY
        )

        val breakdown = TimeCalculator.calculateBreakdown(item, now, utc)

        assertFalse(breakdown.isExpired)
        assertEquals(utcMillis(2025, 2, 28, 9, 0, 0), breakdown.effectiveTargetMillis)
        assertEquals(364L, breakdown.days)
    }

    @Test
    fun `monthly repeat clamps 31st to end of February and restores 31st in March`() {
        val anchor = utcMillis(2026, 1, 31, 10, 0, 0)
        val afterJan = utcMillis(2026, 2, 1, 10, 0, 0)
        val afterFeb = utcMillis(2026, 3, 1, 10, 0, 0)

        val item = CountdownItem(
            id = "bill-31",
            title = "Monthly Renewal",
            targetEpochMillis = anchor,
            startEpochMillis = anchor,
            repeatRule = RepeatRule.MONTHLY,
            category = CountdownCategory.RENEWAL
        )

        val febCycle = TimeCalculator.resolveTargetCycle(item, afterJan, utc)
        assertEquals(utcMillis(2026, 2, 28, 10, 0, 0), febCycle.effectiveTargetMillis)

        val marCycle = TimeCalculator.resolveTargetCycle(item, afterFeb, utc)
        assertEquals(utcMillis(2026, 3, 31, 10, 0, 0), marCycle.effectiveTargetMillis)
    }

    @Test
    fun `computePeriodProgress calculates today midpoint and remaining hours accurately`() {
        val now = utcMillis(2026, 11, 27, 12, 0, 0)
        val period = TimeCalculator.computePeriodProgress(TimePulseScope.TODAY, now, utc)

        assertEquals(TimePulseScope.TODAY, period.scope)
        assertEquals("Hour 13 / 24", period.badgeText)
        assertEquals(24, period.totalUnits)
        assertEquals(13, period.activeUnitOneBased)
        assertEquals("12h 00m", period.summaryLeft)
        assertEquals(50.0f, period.percentCompleted, 0.05f)
    }

    @Test
    fun `computePeriodProgress calculates week progress starting Monday`() {
        // 2026-11-27 is a Friday (day 5 of Mon-Sun week) at 00:00 -> 4 full days passed
        val fridayMidnight = utcMillis(2026, 11, 27, 0, 0, 0)
        val period = TimeCalculator.computePeriodProgress(TimePulseScope.WEEK, fridayMidnight, utc)

        assertEquals(TimePulseScope.WEEK, period.scope)
        assertEquals("Day 5 / 7", period.badgeText)
        assertEquals(7, period.totalUnits)
        assertEquals(5, period.activeUnitOneBased)
        assertEquals(4, period.elapsedUnits)
        assertEquals("3d 0h", period.summaryLeft)
    }

    @Test
    fun `computePeriodProgress calculates month progress for November`() {
        val novMiddle = utcMillis(2026, 11, 16, 0, 0, 0)
        val period = TimeCalculator.computePeriodProgress(TimePulseScope.MONTH, novMiddle, utc)

        assertEquals(TimePulseScope.MONTH, period.scope)
        assertEquals("NOVEMBER PULSE", period.title)
        assertEquals("Day 16 / 30", period.badgeText)
        assertEquals(30, period.totalUnits)
        assertEquals(16, period.activeUnitOneBased)
        assertEquals("15", period.summaryLeft)
        assertEquals(50.0f, period.percentCompleted, 0.05f)
    }
}
