package com.lumetime.app.core

import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.CountdownMode
import com.lumetime.app.model.RepeatRule
import com.lumetime.app.model.TimeBreakdown
import com.lumetime.app.model.YearProgress
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 * Pure, deterministic time engine for year progress, countdown/count-up breakdowns,
 * and O(1) monthly/yearly recurrence resolution. Compatible with API 24+.
 */
object TimeCalculator {

    const val MILLIS_PER_SECOND: Long = 1_000L
    const val MILLIS_PER_MINUTE: Long = 60L * MILLIS_PER_SECOND
    const val MILLIS_PER_HOUR: Long = 60L * MILLIS_PER_MINUTE
    const val MILLIS_PER_DAY: Long = 24L * MILLIS_PER_HOUR

    data class TargetCycle(
        val effectiveStartMillis: Long,
        val effectiveTargetMillis: Long
    )

    /**
     * Computes exact remaining days, hours, weeks, and percentage progress for the calendar year
     * containing [nowMillis] in [timeZone].
     */
    fun computeYearProgress(
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): YearProgress {
        val nowCal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val year = nowCal.get(Calendar.YEAR)
        val isLeapYear = GregorianCalendar(timeZone).isLeapYear(year)
        val totalDays = nowCal.getActualMaximum(Calendar.DAY_OF_YEAR)
        val todayDayOfYear = nowCal.get(Calendar.DAY_OF_YEAR).coerceIn(1, totalDays)

        val yearStartMillis = startOfYearMillis(year, timeZone)
        val nextYearStartMillis = startOfYearMillis(year + 1, timeZone)
        val totalYearMillis = (nextYearStartMillis - yearStartMillis).coerceAtLeast(1L)

        val elapsedMillis = (nowMillis - yearStartMillis).coerceIn(0L, totalYearMillis)
        val remainingMillis = (nextYearStartMillis - nowMillis).coerceAtLeast(0L)

        val daysLeft = remainingMillis / MILLIS_PER_DAY
        val hoursLeft = remainingMillis / MILLIS_PER_HOUR
        val weeksLeft = daysLeft / 7L
        val daysPassed = (todayDayOfYear - 1).coerceAtLeast(0)

        val progressFraction =
            (elapsedMillis.toDouble() / totalYearMillis.toDouble()).toFloat().coerceIn(0f, 1f)

        return YearProgress(
            year = year,
            isLeapYear = isLeapYear,
            totalDays = totalDays,
            todayDayOfYear = todayDayOfYear,
            daysPassed = daysPassed,
            daysLeft = daysLeft,
            hoursLeft = hoursLeft,
            weeksLeft = weeksLeft,
            progressFraction = progressFraction,
            percentCompleted = progressFraction * 100f
        )
    }

    /**
     * Resolves the active start and target timestamps for [item], advancing repeating
     * countdowns to the next upcoming occurrence after [nowMillis].
     */
    fun resolveTargetCycle(
        item: CountdownItem,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): TargetCycle {
        if (item.mode == CountdownMode.COUNT_UP || item.repeatRule == RepeatRule.NONE) {
            return TargetCycle(
                effectiveStartMillis = item.startEpochMillis,
                effectiveTargetMillis = item.targetEpochMillis
            )
        }

        val anchor = Calendar.getInstance(timeZone).apply { timeInMillis = item.targetEpochMillis }
        val nowCal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }

        if (item.targetEpochMillis > nowMillis) {
            val prevOccurrence = shiftFromAnchor(anchor, item.repeatRule, -1, timeZone)
            val start = maxOf(item.startEpochMillis, prevOccurrence)
                .coerceAtMost(item.targetEpochMillis)
            return TargetCycle(
                effectiveStartMillis = start,
                effectiveTargetMillis = item.targetEpochMillis
            )
        }

        val initialStep = when (item.repeatRule) {
            RepeatRule.YEARLY -> (nowCal.get(Calendar.YEAR) - anchor.get(Calendar.YEAR) - 1)
                .coerceAtLeast(0)
            RepeatRule.MONTHLY -> {
                val monthDelta =
                    (nowCal.get(Calendar.YEAR) - anchor.get(Calendar.YEAR)) * 12 +
                        (nowCal.get(Calendar.MONTH) - anchor.get(Calendar.MONTH))
                (monthDelta - 1).coerceAtLeast(0)
            }
            RepeatRule.NONE -> 0
        }

        var step = initialStep
        var candidate = shiftFromAnchor(anchor, item.repeatRule, step, timeZone)
        var guard = 0
        while (candidate <= nowMillis && guard < 12) {
            step++
            candidate = shiftFromAnchor(anchor, item.repeatRule, step, timeZone)
            guard++
        }

        val prev = shiftFromAnchor(anchor, item.repeatRule, step - 1, timeZone)
        return TargetCycle(
            effectiveStartMillis = prev,
            effectiveTargetMillis = candidate
        )
    }

    /**
     * Computes the full time breakdown and normalized progress for [item] at [nowMillis].
     */
    fun calculateBreakdown(
        item: CountdownItem,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): TimeBreakdown {
        val cycle = resolveTargetCycle(item, nowMillis, timeZone)

        if (item.mode == CountdownMode.COUNT_UP) {
            val elapsedMillis = (nowMillis - cycle.effectiveTargetMillis).coerceAtLeast(0L)
            return buildBreakdown(
                deltaMillis = elapsedMillis,
                isExpired = false,
                isCountUp = true,
                progress = 1f,
                effectiveStartMillis = cycle.effectiveTargetMillis,
                effectiveTargetMillis = cycle.effectiveTargetMillis
            )
        }

        val remainingMillis = cycle.effectiveTargetMillis - nowMillis
        if (remainingMillis <= 0L) {
            return buildBreakdown(
                deltaMillis = 0L,
                isExpired = true,
                isCountUp = false,
                progress = 1f,
                effectiveStartMillis = cycle.effectiveStartMillis,
                effectiveTargetMillis = cycle.effectiveTargetMillis
            )
        }

        val spanMillis = (cycle.effectiveTargetMillis - cycle.effectiveStartMillis).coerceAtLeast(1L)
        val elapsedSpan = (nowMillis - cycle.effectiveStartMillis).coerceIn(0L, spanMillis)
        val progress = (elapsedSpan.toDouble() / spanMillis.toDouble()).toFloat().coerceIn(0f, 1f)

        return buildBreakdown(
            deltaMillis = remainingMillis,
            isExpired = false,
            isCountUp = false,
            progress = progress,
            effectiveStartMillis = cycle.effectiveStartMillis,
            effectiveTargetMillis = cycle.effectiveTargetMillis
        )
    }

    private fun buildBreakdown(
        deltaMillis: Long,
        isExpired: Boolean,
        isCountUp: Boolean,
        progress: Float,
        effectiveStartMillis: Long,
        effectiveTargetMillis: Long
    ): TimeBreakdown {
        val safeDelta = deltaMillis.coerceAtLeast(0L)
        val days = safeDelta / MILLIS_PER_DAY
        val hours = ((safeDelta % MILLIS_PER_DAY) / MILLIS_PER_HOUR).toInt()
        val minutes = ((safeDelta % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE).toInt()
        val seconds = ((safeDelta % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND).toInt()

        return TimeBreakdown(
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            totalHours = safeDelta / MILLIS_PER_HOUR,
            totalMinutes = safeDelta / MILLIS_PER_MINUTE,
            totalSeconds = safeDelta / MILLIS_PER_SECOND,
            isExpired = isExpired,
            isCountUp = isCountUp,
            progress = progress,
            effectiveStartMillis = effectiveStartMillis,
            effectiveTargetMillis = effectiveTargetMillis
        )
    }

    private fun startOfYearMillis(year: Int, timeZone: TimeZone): Long {
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun shiftFromAnchor(
        anchor: Calendar,
        rule: RepeatRule,
        steps: Int,
        timeZone: TimeZone
    ): Long {
        val anchorDay = anchor.get(Calendar.DAY_OF_MONTH)
        val shifted = Calendar.getInstance(timeZone).apply {
            clear()
            set(Calendar.YEAR, anchor.get(Calendar.YEAR))
            set(Calendar.MONTH, anchor.get(Calendar.MONTH))
            // Pin day to 1 before shifting month/year so short months never roll over.
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, anchor.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, anchor.get(Calendar.MINUTE))
            set(Calendar.SECOND, anchor.get(Calendar.SECOND))
            set(Calendar.MILLISECOND, anchor.get(Calendar.MILLISECOND))
        }

        when (rule) {
            RepeatRule.YEARLY -> shifted.add(Calendar.YEAR, steps)
            RepeatRule.MONTHLY -> shifted.add(Calendar.MONTH, steps)
            RepeatRule.NONE -> Unit
        }

        val maxDay = shifted.getActualMaximum(Calendar.DAY_OF_MONTH)
        shifted.set(Calendar.DAY_OF_MONTH, anchorDay.coerceAtMost(maxDay))
        return shifted.timeInMillis
    }
}
