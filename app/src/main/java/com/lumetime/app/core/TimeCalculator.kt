package com.lumetime.app.core

import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.CountdownMode
import com.lumetime.app.model.PeriodProgress
import com.lumetime.app.model.RepeatRule
import com.lumetime.app.model.TimeBreakdown
import com.lumetime.app.model.TimePulseScope
import com.lumetime.app.model.YearProgress
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
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

    /**
     * Computes progress and remaining breakdown across Today, This Week, This Month, or This Year.
     */
    fun computePeriodProgress(
        scope: TimePulseScope,
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): PeriodProgress {
        if (scope == TimePulseScope.YEAR) {
            val yp = computeYearProgress(nowMillis, timeZone)
            val nextJan1 = startOfYearMillis(yp.year + 1, timeZone)
            val remMillis = (nextJan1 - nowMillis).coerceAtLeast(0L)
            val remDays = remMillis / MILLIS_PER_DAY
            val remHours = ((remMillis % MILLIS_PER_DAY) / MILLIS_PER_HOUR).toInt()
            val remMins = ((remMillis % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE).toInt()
            val remSecs = ((remMillis % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND).toInt()
            return PeriodProgress(
                scope = TimePulseScope.YEAR,
                title = "${yp.year} • YEAR PULSE",
                badgeText = "Day ${yp.todayDayOfYear} / ${yp.totalDays}",
                progressFraction = yp.progressFraction,
                percentCompleted = yp.percentCompleted,
                remainingMillis = remMillis,
                elapsedUnits = yp.daysPassed,
                totalUnits = yp.totalDays,
                activeUnitOneBased = yp.todayDayOfYear,
                summaryLeft = "${yp.daysLeft}",
                unitLabel = if (yp.daysLeft == 1L) "day left" else "days left",
                subtitle = String.format(
                    Locale.US,
                    "%,d hours remaining • %.1f%% passed",
                    yp.hoursLeft,
                    yp.percentCompleted
                ),
                liveFooter = String.format(
                    Locale.US,
                    "%dd %02dh %02dm %02ds until %d",
                    remDays,
                    remHours,
                    remMins,
                    remSecs,
                    yp.year + 1
                )
            )
        }

        val nowCal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val startCal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val endCal = Calendar.getInstance(timeZone)

        when (scope) {
            TimePulseScope.TODAY -> {
                resetToMidnight(startCal)
                endCal.timeInMillis = startCal.timeInMillis
                endCal.add(Calendar.DAY_OF_YEAR, 1)

                val totalMillis = (endCal.timeInMillis - startCal.timeInMillis).coerceAtLeast(1L)
                val elapsedMillis = (nowMillis - startCal.timeInMillis).coerceIn(0L, totalMillis)
                val remMillis = (endCal.timeInMillis - nowMillis).coerceAtLeast(0L)
                val fraction = (elapsedMillis.toDouble() / totalMillis.toDouble()).toFloat().coerceIn(0f, 1f)
                val hourOfDay = nowCal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
                val remHours = remMillis / MILLIS_PER_HOUR
                val remMins = ((remMillis % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE).toInt()
                val remSecs = ((remMillis % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND).toInt()
                val elapsedMins = elapsedMillis / MILLIS_PER_MINUTE

                return PeriodProgress(
                    scope = TimePulseScope.TODAY,
                    title = "TODAY PULSE",
                    badgeText = "Hour ${hourOfDay + 1} / 24",
                    progressFraction = fraction,
                    percentCompleted = fraction * 100f,
                    remainingMillis = remMillis,
                    elapsedUnits = hourOfDay,
                    totalUnits = 24,
                    activeUnitOneBased = hourOfDay + 1,
                    summaryLeft = String.format(Locale.US, "%dh %02dm", remHours, remMins),
                    unitLabel = "left today",
                    subtitle = String.format(
                        Locale.US,
                        "%.1f%% of today done • %dh %dm passed",
                        fraction * 100f,
                        elapsedMins / 60L,
                        elapsedMins % 60L
                    ),
                    liveFooter = String.format(
                        Locale.US,
                        "%02dh %02dm %02ds until midnight",
                        remHours,
                        remMins,
                        remSecs
                    )
                )
            }

            TimePulseScope.WEEK -> {
                resetToMidnight(startCal)
                val dayOfWeek = startCal.get(Calendar.DAY_OF_WEEK)
                val daysSinceMonday = when (dayOfWeek) {
                    Calendar.MONDAY -> 0
                    Calendar.TUESDAY -> 1
                    Calendar.WEDNESDAY -> 2
                    Calendar.THURSDAY -> 3
                    Calendar.FRIDAY -> 4
                    Calendar.SATURDAY -> 5
                    else -> 6
                }
                startCal.add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
                endCal.timeInMillis = startCal.timeInMillis
                endCal.add(Calendar.DAY_OF_YEAR, 7)

                val totalMillis = (endCal.timeInMillis - startCal.timeInMillis).coerceAtLeast(1L)
                val elapsedMillis = (nowMillis - startCal.timeInMillis).coerceIn(0L, totalMillis)
                val remMillis = (endCal.timeInMillis - nowMillis).coerceAtLeast(0L)
                val fraction = (elapsedMillis.toDouble() / totalMillis.toDouble()).toFloat().coerceIn(0f, 1f)
                val dayIndex = (daysSinceMonday + 1).coerceIn(1, 7)
                val remDays = remMillis / MILLIS_PER_DAY
                val remHours = ((remMillis % MILLIS_PER_DAY) / MILLIS_PER_HOUR).toInt()
                val remMins = ((remMillis % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE).toInt()
                val remSecs = ((remMillis % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND).toInt()

                return PeriodProgress(
                    scope = TimePulseScope.WEEK,
                    title = "WEEK PULSE",
                    badgeText = "Day $dayIndex / 7",
                    progressFraction = fraction,
                    percentCompleted = fraction * 100f,
                    remainingMillis = remMillis,
                    elapsedUnits = daysSinceMonday,
                    totalUnits = 7,
                    activeUnitOneBased = dayIndex,
                    summaryLeft = "${remDays}d ${remHours}h",
                    unitLabel = "left in week",
                    subtitle = String.format(
                        Locale.US,
                        "%.1f%% of this week passed • %d days done",
                        fraction * 100f,
                        daysSinceMonday
                    ),
                    liveFooter = String.format(
                        Locale.US,
                        "%dd %02dh %02dm %02ds until Monday",
                        remDays,
                        remHours,
                        remMins,
                        remSecs
                    )
                )
            }

            TimePulseScope.MONTH -> {
                resetToMidnight(startCal)
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                endCal.timeInMillis = startCal.timeInMillis
                endCal.add(Calendar.MONTH, 1)

                val totalDays = nowCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val dayOfMonth = nowCal.get(Calendar.DAY_OF_MONTH).coerceIn(1, totalDays)
                val totalMillis = (endCal.timeInMillis - startCal.timeInMillis).coerceAtLeast(1L)
                val elapsedMillis = (nowMillis - startCal.timeInMillis).coerceIn(0L, totalMillis)
                val remMillis = (endCal.timeInMillis - nowMillis).coerceAtLeast(0L)
                val fraction = (elapsedMillis.toDouble() / totalMillis.toDouble()).toFloat().coerceIn(0f, 1f)
                val remDays = remMillis / MILLIS_PER_DAY
                val remHours = ((remMillis % MILLIS_PER_DAY) / MILLIS_PER_HOUR).toInt()
                val remMins = ((remMillis % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE).toInt()
                val remSecs = ((remMillis % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND).toInt()
                val monthName = SimpleDateFormat("MMMM", Locale.US).apply {
                    this.timeZone = timeZone
                }.format(Date(nowMillis)).uppercase(Locale.US)

                return PeriodProgress(
                    scope = TimePulseScope.MONTH,
                    title = "$monthName PULSE",
                    badgeText = "Day $dayOfMonth / $totalDays",
                    progressFraction = fraction,
                    percentCompleted = fraction * 100f,
                    remainingMillis = remMillis,
                    elapsedUnits = (dayOfMonth - 1).coerceAtLeast(0),
                    totalUnits = totalDays,
                    activeUnitOneBased = dayOfMonth,
                    summaryLeft = "$remDays",
                    unitLabel = if (remDays == 1L) "day left" else "days left",
                    subtitle = String.format(
                        Locale.US,
                        "%.1f%% of month done • %,d hours left",
                        fraction * 100f,
                        remMillis / MILLIS_PER_HOUR
                    ),
                    liveFooter = String.format(
                        Locale.US,
                        "%dd %02dh %02dm %02ds until next month",
                        remDays,
                        remHours,
                        remMins,
                        remSecs
                    )
                )
            }

            TimePulseScope.YEAR -> error("Handled above")
        }
    }

    /**
     * Computes Today Pulse progress strictly within the user's personalized waking window
     * ([wakeHour]:00 to [sleepHour]:00) so daily progress reflects actionable waking hours.
     */
    fun computeAwakeDayProgress(
        nowMillis: Long,
        wakeHour: Int,
        sleepHour: Int,
        timeZone: TimeZone = TimeZone.getDefault()
    ): PeriodProgress {
        val safeWake = wakeHour.coerceIn(0, 22)
        val safeSleep = sleepHour.coerceIn(safeWake + 1, 23)
        val totalWindowHours = (safeSleep - safeWake).coerceAtLeast(1)

        val wakeCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            resetToMidnight(this)
            set(Calendar.HOUR_OF_DAY, safeWake)
        }
        val sleepCal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            resetToMidnight(this)
            set(Calendar.HOUR_OF_DAY, safeSleep)
        }

        val wakeMillis = wakeCal.timeInMillis
        val sleepMillis = sleepCal.timeInMillis
        val windowMillis = (sleepMillis - wakeMillis).coerceAtLeast(1L)
        val elapsedMillis = (nowMillis - wakeMillis).coerceIn(0L, windowMillis)
        val remainingMillis = (sleepMillis - nowMillis).coerceAtLeast(0L)
        val fraction = (elapsedMillis.toDouble() / windowMillis.toDouble()).toFloat().coerceIn(0f, 1f)

        val currentHour = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
            .get(Calendar.HOUR_OF_DAY)
        val elapsedUnits = (currentHour - safeWake).coerceIn(0, totalWindowHours - 1)
        val activeUnit = (elapsedUnits + 1).coerceIn(1, totalWindowHours)

        val remHours = remainingMillis / MILLIS_PER_HOUR
        val remMins = ((remainingMillis % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE).toInt()
        val remSecs = ((remainingMillis % MILLIS_PER_MINUTE) / MILLIS_PER_SECOND).toInt()

        val summaryLeft: String
        val unitLabel: String
        val liveFooter: String
        when {
            nowMillis < wakeMillis -> {
                val untilWake = wakeMillis - nowMillis
                val h = untilWake / MILLIS_PER_HOUR
                val m = ((untilWake % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE).toInt()
                summaryLeft = String.format(Locale.US, "%dh %02dm", h, m)
                unitLabel = "until day starts"
                liveFooter = "Active window ${formatHourShort(safeWake)} – ${formatHourShort(safeSleep)}"
            }
            nowMillis >= sleepMillis -> {
                summaryLeft = "Done"
                unitLabel = "wind down"
                liveFooter = "Waking hours complete (${formatHourShort(safeWake)} – ${formatHourShort(safeSleep)})"
            }
            else -> {
                summaryLeft = String.format(Locale.US, "%dh %02dm", remHours, remMins)
                unitLabel = "awake left"
                liveFooter = String.format(
                    Locale.US,
                    "%02dh %02dm %02ds until %s",
                    remHours,
                    remMins,
                    remSecs,
                    formatHourShort(safeSleep)
                )
            }
        }

        return PeriodProgress(
            scope = TimePulseScope.TODAY,
            title = "ACTIVE DAY • ${formatHourShort(safeWake)}–${formatHourShort(safeSleep)}",
            badgeText = "Hr $activeUnit / $totalWindowHours",
            progressFraction = fraction,
            percentCompleted = fraction * 100f,
            remainingMillis = remainingMillis,
            elapsedUnits = elapsedUnits,
            totalUnits = totalWindowHours,
            activeUnitOneBased = activeUnit,
            summaryLeft = summaryLeft,
            unitLabel = unitLabel,
            subtitle = String.format(Locale.US, "%.1f%% of your waking day passed", fraction * 100f),
            liveFooter = liveFooter
        )
    }

    private fun formatHourShort(hour24: Int): String {
        val h = hour24 % 12
        val label = if (h == 0) 12 else h
        val amPm = if (hour24 < 12) "AM" else "PM"
        return "$label $amPm"
    }

    private fun resetToMidnight(cal: Calendar) {
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }
}
