package com.lumetime.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DayFocusStat(
    val dayKey: String,
    val dayShortLabel: String,
    val minutes: Int,
    val isToday: Boolean
)

data class PersonalProfile(
    val userName: String = "",
    val dailyIntention: String = "",
    val dailyFocusGoalMinutes: Int = 60,
    val soundAlertEnabled: Boolean = true,
    val vibrationAlertEnabled: Boolean = true,
    val useAwakeDayForTodayPulse: Boolean = false,
    val wakeHour: Int = 7,
    val sleepHour: Int = 23,
    val focusedMinutesToday: Int = 0,
    val focusedSessionsToday: Int = 0,
    val focusedDayKey: String = "",
    val weeklyMinutesByDay: Map<String, Int> = emptyMap(),
    val totalLifetimeSessions: Int = 0
)

/**
 * Pure greeting & 7-day focus-history helpers plus SharedPreferences persistence
 * for user personalization without any cloud/account overhead.
 */
object PersonalProfilePrefs {

    private const val PREFS_NAME = "lumetime_personal_profile"
    private const val KEY_NAME = "user_name"
    private const val KEY_INTENTION = "daily_intention"
    private const val KEY_GOAL_MIN = "daily_focus_goal_minutes"
    private const val KEY_SOUND = "sound_alert_enabled"
    private const val KEY_VIBRATION = "vibration_alert_enabled"
    private const val KEY_AWAKE_MODE = "use_awake_day_pulse"
    private const val KEY_WAKE_HOUR = "wake_hour"
    private const val KEY_SLEEP_HOUR = "sleep_hour"
    private const val KEY_FOCUSED_MIN_TODAY = "focused_minutes_today"
    private const val KEY_FOCUSED_SESSIONS_TODAY = "focused_sessions_today"
    private const val KEY_FOCUSED_DAY_KEY = "focused_day_key"
    private const val KEY_WEEKLY_HISTORY = "weekly_focus_history"
    private const val KEY_TOTAL_SESSIONS = "total_lifetime_sessions"

    fun buildGreeting(hourOfDay: Int, userName: String): String {
        val salutation = when (hourOfDay) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..21 -> "Good Evening"
            else -> "Late Night Focus"
        }
        val trimmed = userName.trim()
        return if (trimmed.isEmpty()) salutation else "$salutation, $trimmed"
    }

    fun dayKey(
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            this.timeZone = timeZone
        }.format(Date(nowMillis))
    }

    fun normalizeForToday(
        profile: PersonalProfile,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): PersonalProfile {
        val today = dayKey(nowMillis, timeZone)
        val mergedHistory = profile.weeklyMinutesByDay.toMutableMap()
        if (profile.focusedDayKey.isNotBlank() && profile.focusedMinutesToday > 0) {
            mergedHistory[profile.focusedDayKey] = maxOf(
                mergedHistory[profile.focusedDayKey] ?: 0,
                profile.focusedMinutesToday
            )
        }
        val validKeys = lastNDaysKeys(7, nowMillis, timeZone).toSet()
        val prunedHistory = mergedHistory.filterKeys { validKeys.contains(it) }

        return if (profile.focusedDayKey == today) {
            profile.copy(weeklyMinutesByDay = prunedHistory)
        } else {
            profile.copy(
                focusedMinutesToday = prunedHistory[today] ?: 0,
                focusedSessionsToday = 0,
                focusedDayKey = today,
                weeklyMinutesByDay = prunedHistory
            )
        }
    }

    fun applyCompletedSession(
        profile: PersonalProfile,
        sessionMinutes: Int,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): PersonalProfile {
        val normalized = normalizeForToday(profile, nowMillis, timeZone)
        val safeMinutes = sessionMinutes.coerceAtLeast(1)
        val nextTodayMinutes = normalized.focusedMinutesToday + safeMinutes
        val updatedHistory = normalized.weeklyMinutesByDay.toMutableMap().apply {
            put(normalized.focusedDayKey, nextTodayMinutes)
        }
        return normalized.copy(
            focusedMinutesToday = nextTodayMinutes,
            focusedSessionsToday = normalized.focusedSessionsToday + 1,
            weeklyMinutesByDay = updatedHistory,
            totalLifetimeSessions = normalized.totalLifetimeSessions + 1
        )
    }

    fun buildLast7DaysStats(
        profile: PersonalProfile,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<DayFocusStat> {
        val normalized = normalizeForToday(profile, nowMillis, timeZone)
        val todayKey = dayKey(nowMillis, timeZone)
        val shortFmt = SimpleDateFormat("EEE", Locale.US).apply { this.timeZone = timeZone }
        val result = ArrayList<DayFocusStat>(7)
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -6)
        }
        for (i in 0 until 7) {
            val k = dayKey(cal.timeInMillis, timeZone)
            val label = shortFmt.format(Date(cal.timeInMillis)).take(1)
            val mins = if (k == todayKey) {
                maxOf(normalized.focusedMinutesToday, normalized.weeklyMinutesByDay[k] ?: 0)
            } else {
                normalized.weeklyMinutesByDay[k] ?: 0
            }
            result.add(
                DayFocusStat(
                    dayKey = k,
                    dayShortLabel = label,
                    minutes = mins,
                    isToday = k == todayKey
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    private fun lastNDaysKeys(days: Int, nowMillis: Long, timeZone: TimeZone): List<String> {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = nowMillis }
        val list = ArrayList<String>(days)
        for (i in 0 until days) {
            list.add(dayKey(cal.timeInMillis, timeZone))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return list
    }

    private fun decodeHistory(raw: String): Map<String, Int> {
        if (raw.isBlank()) return emptyMap()
        val map = LinkedHashMap<String, Int>()
        raw.split(";").forEach { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) {
                val mins = parts[1].toIntOrNull()
                if (parts[0].isNotBlank() && mins != null && mins > 0) {
                    map[parts[0]] = mins
                }
            }
        }
        return map
    }

    private fun encodeHistory(map: Map<String, Int>): String {
        return map.entries
            .filter { it.value > 0 }
            .joinToString(";") { "${it.key}:${it.value}" }
    }

    fun load(
        context: Context,
        nowMillis: Long = System.currentTimeMillis()
    ): PersonalProfile {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = PersonalProfile(
            userName = prefs.getString(KEY_NAME, "").orEmpty(),
            dailyIntention = prefs.getString(KEY_INTENTION, "").orEmpty(),
            dailyFocusGoalMinutes = prefs.getInt(KEY_GOAL_MIN, 60).coerceIn(15, 600),
            soundAlertEnabled = prefs.getBoolean(KEY_SOUND, true),
            vibrationAlertEnabled = prefs.getBoolean(KEY_VIBRATION, true),
            useAwakeDayForTodayPulse = prefs.getBoolean(KEY_AWAKE_MODE, false),
            wakeHour = prefs.getInt(KEY_WAKE_HOUR, 7).coerceIn(0, 22),
            sleepHour = prefs.getInt(KEY_SLEEP_HOUR, 23).coerceIn(1, 23),
            focusedMinutesToday = prefs.getInt(KEY_FOCUSED_MIN_TODAY, 0).coerceAtLeast(0),
            focusedSessionsToday = prefs.getInt(KEY_FOCUSED_SESSIONS_TODAY, 0).coerceAtLeast(0),
            focusedDayKey = prefs.getString(KEY_FOCUSED_DAY_KEY, "").orEmpty(),
            weeklyMinutesByDay = decodeHistory(prefs.getString(KEY_WEEKLY_HISTORY, "").orEmpty()),
            totalLifetimeSessions = prefs.getInt(KEY_TOTAL_SESSIONS, 0).coerceAtLeast(0)
        )
        return normalizeForToday(raw, nowMillis)
    }

    fun save(context: Context, profile: PersonalProfile) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NAME, profile.userName.trim())
            .putString(KEY_INTENTION, profile.dailyIntention.trim())
            .putInt(KEY_GOAL_MIN, profile.dailyFocusGoalMinutes.coerceIn(15, 600))
            .putBoolean(KEY_SOUND, profile.soundAlertEnabled)
            .putBoolean(KEY_VIBRATION, profile.vibrationAlertEnabled)
            .putBoolean(KEY_AWAKE_MODE, profile.useAwakeDayForTodayPulse)
            .putInt(KEY_WAKE_HOUR, profile.wakeHour.coerceIn(0, 22))
            .putInt(KEY_SLEEP_HOUR, profile.sleepHour.coerceIn(profile.wakeHour + 1, 23))
            .putInt(KEY_FOCUSED_MIN_TODAY, profile.focusedMinutesToday)
            .putInt(KEY_FOCUSED_SESSIONS_TODAY, profile.focusedSessionsToday)
            .putString(KEY_FOCUSED_DAY_KEY, profile.focusedDayKey)
            .putString(KEY_WEEKLY_HISTORY, encodeHistory(profile.weeklyMinutesByDay))
            .putInt(KEY_TOTAL_SESSIONS, profile.totalLifetimeSessions)
            .apply()
    }

    fun recordFocusSession(
        context: Context,
        minutes: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): PersonalProfile {
        val current = load(context, nowMillis)
        val updated = applyCompletedSession(current, minutes, nowMillis)
        save(context, updated)
        return updated
    }

    fun currentHour(nowMillis: Long = System.currentTimeMillis()): Int {
        return Calendar.getInstance().apply { timeInMillis = nowMillis }.get(Calendar.HOUR_OF_DAY)
    }
}
