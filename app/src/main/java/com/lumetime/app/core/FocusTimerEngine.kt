package com.lumetime.app.core

import java.util.Locale

enum class FocusToolTab {
    TIMER,
    STOPWATCH
}

/**
 * Immutable wall-clock-backed timer & stopwatch snapshot for zero-background-drain persistence.
 */
data class FocusTimerSnapshot(
    val activeTab: FocusToolTab = FocusToolTab.TIMER,
    val timerDurationMillis: Long = DEFAULT_POMODORO_MILLIS,
    val timerRemainingMillis: Long = DEFAULT_POMODORO_MILLIS,
    val timerTargetEndMillis: Long = 0L,
    val isTimerRunning: Boolean = false,
    val stopwatchStartMillis: Long = 0L,
    val stopwatchAccumulatedMillis: Long = 0L,
    val isStopwatchRunning: Boolean = false,
    val lapsMillis: List<Long> = emptyList()
)

/**
 * Pure, deterministic Focus Timer (Pomodoro/Custom) and Stopwatch engine.
 * Never allocates timers or wake locks; computes exact elapsed/remaining durations from epoch timestamps.
 */
object FocusTimerEngine {

    const val DEFAULT_POMODORO_MILLIS: Long = 25L * TimeCalculator.MILLIS_PER_MINUTE
    const val DEEP_WORK_MILLIS: Long = 50L * TimeCalculator.MILLIS_PER_MINUTE
    const val SHORT_BREAK_MILLIS: Long = 5L * TimeCalculator.MILLIS_PER_MINUTE
    const val LONG_BREAK_MILLIS: Long = 15L * TimeCalculator.MILLIS_PER_MINUTE
    private const val MAX_DURATION_MILLIS: Long = 180L * TimeCalculator.MILLIS_PER_MINUTE

    fun selectPreset(snapshot: FocusTimerSnapshot, durationMillis: Long): FocusTimerSnapshot {
        val safeDuration = durationMillis.coerceIn(TimeCalculator.MILLIS_PER_MINUTE, MAX_DURATION_MILLIS)
        return snapshot.copy(
            timerDurationMillis = safeDuration,
            timerRemainingMillis = safeDuration,
            timerTargetEndMillis = 0L,
            isTimerRunning = false
        )
    }

    fun adjustMinutes(
        snapshot: FocusTimerSnapshot,
        deltaMinutes: Int,
        nowMillis: Long
    ): FocusTimerSnapshot {
        val deltaMillis = deltaMinutes.toLong() * TimeCalculator.MILLIS_PER_MINUTE
        return if (snapshot.isTimerRunning) {
            val currentRemaining = remainingTimerMillis(snapshot, nowMillis)
            val nextRemaining = (currentRemaining + deltaMillis)
                .coerceIn(TimeCalculator.MILLIS_PER_MINUTE, MAX_DURATION_MILLIS)
            val nextDuration = maxOf(snapshot.timerDurationMillis, nextRemaining)
            snapshot.copy(
                timerDurationMillis = nextDuration,
                timerRemainingMillis = nextRemaining,
                timerTargetEndMillis = nowMillis + nextRemaining
            )
        } else {
            val base = if (snapshot.timerRemainingMillis > 0L) snapshot.timerRemainingMillis else snapshot.timerDurationMillis
            val next = (base + deltaMillis).coerceIn(TimeCalculator.MILLIS_PER_MINUTE, MAX_DURATION_MILLIS)
            snapshot.copy(
                timerDurationMillis = next,
                timerRemainingMillis = next,
                timerTargetEndMillis = 0L
            )
        }
    }

    fun toggleTimer(snapshot: FocusTimerSnapshot, nowMillis: Long): FocusTimerSnapshot {
        return if (snapshot.isTimerRunning) {
            val remaining = remainingTimerMillis(snapshot, nowMillis)
            snapshot.copy(
                timerRemainingMillis = remaining,
                timerTargetEndMillis = 0L,
                isTimerRunning = false
            )
        } else {
            val startFrom = if (snapshot.timerRemainingMillis > 0L) {
                snapshot.timerRemainingMillis
            } else {
                snapshot.timerDurationMillis
            }
            snapshot.copy(
                timerRemainingMillis = startFrom,
                timerTargetEndMillis = nowMillis + startFrom,
                isTimerRunning = true
            )
        }
    }

    fun resetTimer(snapshot: FocusTimerSnapshot): FocusTimerSnapshot {
        return snapshot.copy(
            timerRemainingMillis = snapshot.timerDurationMillis,
            timerTargetEndMillis = 0L,
            isTimerRunning = false
        )
    }

    fun remainingTimerMillis(snapshot: FocusTimerSnapshot, nowMillis: Long): Long {
        if (!snapshot.isTimerRunning) {
            return snapshot.timerRemainingMillis.coerceAtLeast(0L)
        }
        return (snapshot.timerTargetEndMillis - nowMillis).coerceAtLeast(0L)
    }

    fun isTimerCompleted(snapshot: FocusTimerSnapshot, nowMillis: Long): Boolean {
        return snapshot.isTimerRunning && snapshot.timerTargetEndMillis <= nowMillis
    }

    fun timerProgressFraction(snapshot: FocusTimerSnapshot, nowMillis: Long): Float {
        val total = snapshot.timerDurationMillis.coerceAtLeast(1L)
        val remaining = remainingTimerMillis(snapshot, nowMillis)
        val elapsed = (total - remaining).coerceIn(0L, total)
        return (elapsed.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    fun toggleStopwatch(snapshot: FocusTimerSnapshot, nowMillis: Long): FocusTimerSnapshot {
        return if (snapshot.isStopwatchRunning) {
            val elapsed = elapsedStopwatchMillis(snapshot, nowMillis)
            snapshot.copy(
                stopwatchAccumulatedMillis = elapsed,
                stopwatchStartMillis = 0L,
                isStopwatchRunning = false
            )
        } else {
            snapshot.copy(
                stopwatchStartMillis = nowMillis,
                isStopwatchRunning = true
            )
        }
    }

    fun recordLap(snapshot: FocusTimerSnapshot, nowMillis: Long): FocusTimerSnapshot {
        val elapsed = elapsedStopwatchMillis(snapshot, nowMillis)
        if (elapsed <= 0L) return snapshot
        val updatedLaps = listOf(elapsed) + snapshot.lapsMillis
        return snapshot.copy(lapsMillis = updatedLaps.take(30))
    }

    fun resetStopwatch(snapshot: FocusTimerSnapshot): FocusTimerSnapshot {
        return snapshot.copy(
            stopwatchStartMillis = 0L,
            stopwatchAccumulatedMillis = 0L,
            isStopwatchRunning = false,
            lapsMillis = emptyList()
        )
    }

    fun elapsedStopwatchMillis(snapshot: FocusTimerSnapshot, nowMillis: Long): Long {
        if (!snapshot.isStopwatchRunning) {
            return snapshot.stopwatchAccumulatedMillis.coerceAtLeast(0L)
        }
        val delta = (nowMillis - snapshot.stopwatchStartMillis).coerceAtLeast(0L)
        return snapshot.stopwatchAccumulatedMillis + delta
    }

    fun formatMmSs(millis: Long): String {
        val totalSeconds = ((millis + 999L) / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val mins = (totalSeconds % 3600L) / 60L
        val secs = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun formatStopwatch(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val tenths = ((safe % 1000L) / 100L).toInt()
        val totalSeconds = safe / 1000L
        val hours = totalSeconds / 3600L
        val mins = (totalSeconds % 3600L) / 60L
        val secs = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d.%d", hours, mins, secs, tenths)
        } else {
            String.format(Locale.US, "%02d:%02d.%d", mins, secs, tenths)
        }
    }
}
