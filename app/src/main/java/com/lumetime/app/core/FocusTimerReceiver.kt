package com.lumetime.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lumetime.app.data.FocusTimerStore
import com.lumetime.app.data.PersonalProfilePrefs

/**
 * Fires when a scheduled Focus Timer finishes in background/doze:
 * marks completion, logs focused minutes once, chimes/vibrates, and posts heads-up notification.
 */
class FocusTimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val now = System.currentTimeMillis()
        var snapshot = FocusTimerStore.load(context)
        val extraLabel = intent?.getStringExtra(TimerAlertManager.EXTRA_FOCUS_LABEL).orEmpty()
        val extraMinutes = intent?.getIntExtra(TimerAlertManager.EXTRA_FOCUS_MINUTES, 25) ?: 25

        if (!snapshot.isTimerRunning && snapshot.completedLogged) {
            return
        }

        val sessionLabel = snapshot.sessionLabel.ifBlank { extraLabel.ifBlank { "Focus Session" } }
        val durationMins = ((snapshot.timerDurationMillis + 30_000L) / 60_000L)
            .toInt()
            .coerceAtLeast(1)

        if (!snapshot.completedLogged) {
            PersonalProfilePrefs.recordFocusSession(context, durationMins, now)
        }

        snapshot = snapshot.copy(
            isTimerRunning = false,
            timerRemainingMillis = 0L,
            timerTargetEndMillis = 0L,
            completedLogged = true,
            completedAcknowledged = false
        )
        FocusTimerStore.save(context, snapshot)

        TimerAlertManager.playCompletionChimeAndVibrate(context)
        TimerAlertManager.showFocusCompleteNotification(context, sessionLabel, extraMinutes.coerceAtLeast(durationMins))
    }
}
