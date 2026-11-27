package com.lumetime.app.ui

import com.lumetime.app.model.TimeBreakdown
import java.util.Locale

/**
 * Display unit formats cycled via the countdown list toolbar.
 */
enum class CountdownDisplayFormat(val shortLabel: String) {
    DAYS_HOURS("Days"),
    TOTAL_HOURS("Hours"),
    WEEKS_DAYS("Weeks"),
    MONTHS_DAYS("Months");

    fun next(): CountdownDisplayFormat {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

data class FormattedCountdownMetric(
    val primary: String,
    val secondary: String
)

/**
 * Pure Kotlin formatter for countdown cards across all 4 display formats.
 */
object CountdownDisplayFormatter {

    fun format(
        breakdown: TimeBreakdown,
        format: CountdownDisplayFormat
    ): FormattedCountdownMetric {
        if (breakdown.isExpired) {
            return FormattedCountdownMetric(primary = "Done", secondary = "Target reached")
        }

        val prefix = if (breakdown.isCountUp) "+" else ""
        return when (format) {
            CountdownDisplayFormat.DAYS_HOURS -> FormattedCountdownMetric(
                primary = String.format(Locale.US, "%s%dd", prefix, breakdown.days),
                secondary = String.format(
                    Locale.US,
                    "%02dh %02dm %02ds",
                    breakdown.hours,
                    breakdown.minutes,
                    breakdown.seconds
                )
            )
            CountdownDisplayFormat.TOTAL_HOURS -> FormattedCountdownMetric(
                primary = String.format(Locale.US, "%s%,dh", prefix, breakdown.totalHours),
                secondary = String.format(
                    Locale.US,
                    "%02dm %02ds",
                    breakdown.minutes,
                    breakdown.seconds
                )
            )
            CountdownDisplayFormat.WEEKS_DAYS -> {
                val weeks = breakdown.days / 7L
                val remDays = breakdown.days % 7L
                FormattedCountdownMetric(
                    primary = String.format(Locale.US, "%s%dw %dd", prefix, weeks, remDays),
                    secondary = String.format(
                        Locale.US,
                        "%02dh %02dm",
                        breakdown.hours,
                        breakdown.minutes
                    )
                )
            }
            CountdownDisplayFormat.MONTHS_DAYS -> {
                val months = breakdown.days / 30L
                val remDays = breakdown.days % 30L
                val primary = if (months > 0L) {
                    String.format(Locale.US, "%s%dm %dd", prefix, months, remDays)
                } else {
                    String.format(Locale.US, "%s%dd", prefix, remDays)
                }
                FormattedCountdownMetric(
                    primary = primary,
                    secondary = String.format(
                        Locale.US,
                        "%02dh %02dm",
                        breakdown.hours,
                        breakdown.minutes
                    )
                )
            }
        }
    }
}
