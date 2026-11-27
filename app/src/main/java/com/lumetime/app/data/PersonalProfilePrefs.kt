package com.lumetime.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    val focusedDayKey: String = ""
)

/**
 * Pure greeting & focus-stats helpers plus SharedPreferences persistence
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
        return if (profile.focusedDayKey == today) {
            profile
        } else {
            profile.copy(
                focusedMinutesToday = 0,
                focusedSessionsToday = 0,
                focusedDayKey = today
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
        return normalized.copy(
            focusedMinutesToday = normalized.focusedMinutesToday + safeMinutes,
            focusedSessionsToday = normalized.focusedSessionsToday + 1
        )
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
            focusedDayKey = prefs.getString(KEY_FOCUSED_DAY_KEY, "").orEmpty()
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
