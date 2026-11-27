package com.lumetime.app.core

import java.util.Calendar
import java.util.TimeZone

data class DateDifferenceResult(
    val startEarlierOrEqual: Boolean,
    val years: Int,
    val months: Int,
    val days: Int,
    val totalDays: Long,
    val weeks: Long,
    val remainingDaysAfterWeeks: Long,
    val totalHours: Long,
    val weekdaysCount: Long
)

/**
 * Pure Kotlin date difference, exact age, working-days, and date-offset calculator.
 * Safe for Android API 24+ using UTC/specified timezone Calendar normalization.
 */
object DateCalculatorEngine {

    fun computeDateDifference(
        firstMillis: Long,
        secondMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): DateDifferenceResult {
        val startCal = midnightCalendar(minOf(firstMillis, secondMillis), timeZone)
        val endCal = midnightCalendar(maxOf(firstMillis, secondMillis), timeZone)
        val earlierOrEqual = firstMillis <= secondMillis

        val totalDays = countCalendarDays(startCal, endCal)
        val weeks = totalDays / 7L
        val remDays = totalDays % 7L
        val totalHours = totalDays * 24L
        val weekdays = countWeekdays(startCal, totalDays)

        val cursor = startCal.clone() as Calendar
        var years = 0
        while (true) {
            val next = cursor.clone() as Calendar
            next.add(Calendar.YEAR, 1)
            if (!next.after(endCal)) {
                cursor.add(Calendar.YEAR, 1)
                years++
            } else {
                break
            }
        }

        var months = 0
        while (true) {
            val next = cursor.clone() as Calendar
            next.add(Calendar.MONTH, 1)
            if (!next.after(endCal)) {
                cursor.add(Calendar.MONTH, 1)
                months++
            } else {
                break
            }
        }

        val days = countCalendarDays(cursor, endCal).toInt()

        return DateDifferenceResult(
            startEarlierOrEqual = earlierOrEqual,
            years = years,
            months = months,
            days = days,
            totalDays = totalDays,
            weeks = weeks,
            remainingDaysAfterWeeks = remDays,
            totalHours = totalHours,
            weekdaysCount = weekdays
        )
    }

    fun addDays(
        baseMillis: Long,
        offsetDays: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val cal = midnightCalendar(baseMillis, timeZone)
        cal.add(Calendar.DAY_OF_YEAR, offsetDays)
        return cal.timeInMillis
    }

    private fun countCalendarDays(start: Calendar, end: Calendar): Long {
        // Compute in UTC Y-M-D day space to stay immune to DST 23h/25h transitions.
        val utcStart = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH))
        }
        val utcEnd = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(end.get(Calendar.YEAR), end.get(Calendar.MONTH), end.get(Calendar.DAY_OF_MONTH))
        }
        return ((utcEnd.timeInMillis - utcStart.timeInMillis) / TimeCalculator.MILLIS_PER_DAY)
            .coerceAtLeast(0L)
    }

    private fun countWeekdays(startMidnight: Calendar, totalDays: Long): Long {
        if (totalDays <= 0L) return 0L
        val fullWeeks = totalDays / 7L
        val extraDays = (totalDays % 7L).toInt()
        var weekdays = fullWeeks * 5L
        val cal = startMidnight.clone() as Calendar
        for (i in 0 until extraDays) {
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            if (dow != Calendar.SATURDAY && dow != Calendar.SUNDAY) {
                weekdays++
            }
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return weekdays
    }

    private fun midnightCalendar(millis: Long, timeZone: TimeZone): Calendar {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
}
