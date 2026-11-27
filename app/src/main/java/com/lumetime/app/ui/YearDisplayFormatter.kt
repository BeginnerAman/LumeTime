package com.lumetime.app.ui

import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.model.YearProgress
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Tap-to-cycle display modes for the Year Pulse hero card.
 */
enum class YearPulseMetricMode {
    DAYS_LEFT,
    HOURS_LEFT,
    PERCENT_DONE,
    WEEKS_LEFT;

    fun next(): YearPulseMetricMode {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

data class YearPulseDisplay(
    val primaryValue: String,
    val primaryUnit: String,
    val secondaryCaption: String,
    val dayOfYearBadge: String,
    val liveFooter: String
)

/**
 * Pure Kotlin formatter for the Year Pulse card metrics and live year-end countdown string.
 */
object YearDisplayFormatter {

    fun format(
        progress: YearProgress,
        mode: YearPulseMetricMode,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): YearPulseDisplay {
        val nextJan1Millis = Calendar.getInstance(timeZone).apply {
            clear()
            set(Calendar.YEAR, progress.year + 1)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

        val remainingMillis = (nextJan1Millis - nowMillis).coerceAtLeast(0L)
        val remDays = remainingMillis / TimeCalculator.MILLIS_PER_DAY
        val remHours = ((remainingMillis % TimeCalculator.MILLIS_PER_DAY) / TimeCalculator.MILLIS_PER_HOUR).toInt()
        val remMins = ((remainingMillis % TimeCalculator.MILLIS_PER_HOUR) / TimeCalculator.MILLIS_PER_MINUTE).toInt()
        val remSecs = ((remainingMillis % TimeCalculator.MILLIS_PER_MINUTE) / TimeCalculator.MILLIS_PER_SECOND).toInt()

        val primaryValue: String
        val primaryUnit: String
        val secondaryCaption: String

        when (mode) {
            YearPulseMetricMode.DAYS_LEFT -> {
                primaryValue = String.format(Locale.US, "%d", progress.daysLeft)
                primaryUnit = if (progress.daysLeft == 1L) "day left" else "days left"
                secondaryCaption = String.format(
                    Locale.US,
                    "%,d hours remaining • %.1f%% passed",
                    progress.hoursLeft,
                    progress.percentCompleted
                )
            }
            YearPulseMetricMode.HOURS_LEFT -> {
                primaryValue = String.format(Locale.US, "%,d", progress.hoursLeft)
                primaryUnit = "hours left"
                secondaryCaption = String.format(
                    Locale.US,
                    "%dd %02dh left in %d",
                    remDays,
                    remHours,
                    progress.year
                )
            }
            YearPulseMetricMode.PERCENT_DONE -> {
                primaryValue = String.format(Locale.US, "%.1f%%", progress.percentCompleted)
                primaryUnit = "completed"
                secondaryCaption = String.format(
                    Locale.US,
                    "%d passed • %d days left in %d",
                    progress.daysPassed,
                    progress.daysLeft,
                    progress.year
                )
            }
            YearPulseMetricMode.WEEKS_LEFT -> {
                val extraDays = progress.daysLeft % 7L
                primaryValue = String.format(Locale.US, "%dw %dd", progress.weeksLeft, extraDays)
                primaryUnit = "remaining"
                secondaryCaption = String.format(
                    Locale.US,
                    "%d of %d days completed",
                    progress.todayDayOfYear,
                    progress.totalDays
                )
            }
        }

        val dayBadge = String.format(
            Locale.US,
            "Day %d / %d",
            progress.todayDayOfYear,
            progress.totalDays
        )
        val liveFooter = String.format(
            Locale.US,
            "%dd %02dh %02dm %02ds until %d",
            remDays,
            remHours,
            remMins,
            remSecs,
            progress.year + 1
        )

        return YearPulseDisplay(
            primaryValue = primaryValue,
            primaryUnit = primaryUnit,
            secondaryCaption = secondaryCaption,
            dayOfYearBadge = dayBadge,
            liveFooter = liveFooter
        )
    }
}
