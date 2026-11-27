package com.lumetime.app.model

/**
 * Defines whether an entry counts down toward a future date or counts up from a past milestone.
 */
enum class CountdownMode {
    COUNTDOWN,
    COUNT_UP
}

/**
 * Recurrence rule for repeating countdowns such as birthdays or monthly renewals.
 */
enum class RepeatRule {
    NONE,
    MONTHLY,
    YEARLY
}

/**
 * Semantic category used for filtering, visual badges, and quick templates.
 */
enum class CountdownCategory(val label: String) {
    GENERAL("General"),
    EXAM("Exam"),
    BIRTHDAY("Birthday"),
    TRAVEL("Travel"),
    GOAL("Goal"),
    RENEWAL("Renewal"),
    MILESTONE("Milestone")
}

/**
 * Persistent user countdown or milestone entry.
 * Note: [emoji] stores the [CountdownIcon.id] (or legacy emoji string, auto-migrated via [resolveIcon]).
 */
data class CountdownItem(
    val id: String,
    val title: String,
    val targetEpochMillis: Long,
    val startEpochMillis: Long = System.currentTimeMillis(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    val mode: CountdownMode = CountdownMode.COUNTDOWN,
    val repeatRule: RepeatRule = RepeatRule.NONE,
    val category: CountdownCategory = CountdownCategory.GENERAL,
    val accentHex: String = "#67E8F9",
    val isPinned: Boolean = false,
    val emoji: String = CountdownIcon.forCategory(category).id,
    val reminderEnabled: Boolean = false,
    val note: String = ""
) {
    fun resolveIcon(): CountdownIcon = CountdownIcon.fromStored(emoji, category)
}

/**
 * Computed breakdown for a countdown/count-up item at a specific instant.
 */
data class TimeBreakdown(
    val days: Long,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val totalHours: Long,
    val totalMinutes: Long,
    val totalSeconds: Long,
    val isExpired: Boolean,
    val isCountUp: Boolean,
    val progress: Float,
    val effectiveStartMillis: Long,
    val effectiveTargetMillis: Long
)

/**
 * Computed progress and remaining statistics for the current calendar year.
 */
data class YearProgress(
    val year: Int,
    val isLeapYear: Boolean,
    val totalDays: Int,
    val todayDayOfYear: Int,
    val daysPassed: Int,
    val daysLeft: Long,
    val hoursLeft: Long,
    val weeksLeft: Long,
    val progressFraction: Float,
    val percentCompleted: Float
)
