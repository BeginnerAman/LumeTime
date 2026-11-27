package com.lumetime.app.data

import android.content.Context
import com.lumetime.app.core.FocusTimerEngine
import com.lumetime.app.core.FocusTimerSnapshot
import com.lumetime.app.core.FocusToolTab

/**
 * Lightweight SharedPreferences store preserving Focus Timer & Stopwatch across closures.
 */
object FocusTimerStore {

    private const val PREFS_NAME = "lumetime_focus_timer_prefs"
    private const val KEY_TAB = "active_tab"
    private const val KEY_DURATION = "timer_duration"
    private const val KEY_REMAINING = "timer_remaining"
    private const val KEY_TARGET_END = "timer_target_end"
    private const val KEY_TIMER_RUNNING = "timer_running"
    private const val KEY_SW_START = "sw_start"
    private const val KEY_SW_ACCUM = "sw_accum"
    private const val KEY_SW_RUNNING = "sw_running"
    private const val KEY_SW_LAPS = "sw_laps"

    fun load(context: Context): FocusTimerSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tabName = prefs.getString(KEY_TAB, FocusToolTab.TIMER.name)
        val tab = FocusToolTab.entries.firstOrNull { it.name == tabName } ?: FocusToolTab.TIMER
        val lapsRaw = prefs.getString(KEY_SW_LAPS, "").orEmpty()
        val laps = if (lapsRaw.isBlank()) {
            emptyList()
        } else {
            lapsRaw.split(",").mapNotNull { it.toLongOrNull() }
        }
        return FocusTimerSnapshot(
            activeTab = tab,
            timerDurationMillis = prefs.getLong(KEY_DURATION, FocusTimerEngine.DEFAULT_POMODORO_MILLIS),
            timerRemainingMillis = prefs.getLong(KEY_REMAINING, FocusTimerEngine.DEFAULT_POMODORO_MILLIS),
            timerTargetEndMillis = prefs.getLong(KEY_TARGET_END, 0L),
            isTimerRunning = prefs.getBoolean(KEY_TIMER_RUNNING, false),
            stopwatchStartMillis = prefs.getLong(KEY_SW_START, 0L),
            stopwatchAccumulatedMillis = prefs.getLong(KEY_SW_ACCUM, 0L),
            isStopwatchRunning = prefs.getBoolean(KEY_SW_RUNNING, false),
            lapsMillis = laps
        )
    }

    fun save(context: Context, snapshot: FocusTimerSnapshot) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TAB, snapshot.activeTab.name)
            .putLong(KEY_DURATION, snapshot.timerDurationMillis)
            .putLong(KEY_REMAINING, snapshot.timerRemainingMillis)
            .putLong(KEY_TARGET_END, snapshot.timerTargetEndMillis)
            .putBoolean(KEY_TIMER_RUNNING, snapshot.isTimerRunning)
            .putLong(KEY_SW_START, snapshot.stopwatchStartMillis)
            .putLong(KEY_SW_ACCUM, snapshot.stopwatchAccumulatedMillis)
            .putBoolean(KEY_SW_RUNNING, snapshot.isStopwatchRunning)
            .putString(KEY_SW_LAPS, snapshot.lapsMillis.joinToString(","))
            .apply()
    }
}
