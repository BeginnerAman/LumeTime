package com.lumetime.app.core

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lumetime.app.MainActivity
import com.lumetime.app.R
import com.lumetime.app.data.PersonalProfilePrefs

/**
 * Reliable zero-drain alert & completion feedback engine for Focus Timers and Reminders.
 * Combines AlarmManager wakeups (background) with crisp chimes + multi-pulse vibration (foreground/background).
 */
object TimerAlertManager {

    const val ALERTS_CHANNEL_ID = "lumetime_alerts_v2"
    private const val FOCUS_ALARM_REQUEST_CODE = 7701
    private const val FOCUS_NOTIFICATION_ID = 7702
    const val EXTRA_FOCUS_LABEL = "extra_focus_label"
    const val EXTRA_FOCUS_MINUTES = "extra_focus_minutes"

    fun syncFocusAlarm(context: Context, snapshot: FocusTimerSnapshot) {
        if (snapshot.isTimerRunning && snapshot.timerTargetEndMillis > System.currentTimeMillis()) {
            val mins = ((snapshot.timerDurationMillis + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
            scheduleFocusTimerAlarm(
                context = context,
                targetEndMillis = snapshot.timerTargetEndMillis,
                label = snapshot.sessionLabel,
                durationMinutes = mins
            )
        } else {
            cancelFocusTimerAlarm(context)
        }
    }

    fun scheduleFocusTimerAlarm(
        context: Context,
        targetEndMillis: Long,
        label: String,
        durationMinutes: Int
    ) {
        cancelFocusTimerAlarm(context)
        if (targetEndMillis <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = createFocusPendingIntent(context, label, durationMinutes)

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetEndMillis, pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetEndMillis, pending)
        }
    }

    fun cancelFocusTimerAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = PendingIntent.getBroadcast(
            context,
            FOCUS_ALARM_REQUEST_CODE,
            Intent(context, FocusTimerReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) {
            alarmManager.cancel(pending)
            pending.cancel()
        }
    }

    fun playCompletionChimeAndVibrate(context: Context) {
        val profile = PersonalProfilePrefs.load(context)
        if (profile.vibrationAlertEnabled) {
            vibrateCompletionPattern(context)
        }
        if (profile.soundAlertEnabled) {
            playGentleChime(context)
        }
    }

    fun showFocusCompleteNotification(
        context: Context,
        label: String,
        durationMinutes: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureHighImportanceChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            FOCUS_NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cleanLabel = label.trim().ifEmpty { "Focus Session" }
        val notification = NotificationCompat.Builder(context, ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle("Timer Complete • $cleanLabel")
            .setContentText("$durationMinutes min finished! Tap to log or start your break.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .build()

        NotificationManagerCompat.from(context).notify(FOCUS_NOTIFICATION_ID, notification)
    }

    fun ensureHighImportanceChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(ALERTS_CHANNEL_ID) != null) return

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            ALERTS_CHANNEL_ID,
            "Timers & Countdown Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High-priority chimes and alerts when Focus timers or countdowns finish"
            enableVibration(true)
            vibrationPattern = longArrayOf(0L, 240L, 120L, 240L, 120L, 360L)
            setSound(soundUri, audioAttributes)
        }
        manager.createNotificationChannel(channel)
    }

    private fun playGentleChime(context: Context) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = if (uri != null) RingtoneManager.getRingtone(context, uri) else null
            if (ringtone != null) {
                ringtone.play()
                return
            }
        }
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 380)
        }
    }

    private fun vibrateCompletionPattern(context: Context) {
        val pattern = longArrayOf(0L, 220L, 110L, 220L, 110L, 340L)
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun createFocusPendingIntent(
        context: Context,
        label: String,
        durationMinutes: Int
    ): PendingIntent {
        val intent = Intent(context, FocusTimerReceiver::class.java).apply {
            putExtra(EXTRA_FOCUS_LABEL, label)
            putExtra(EXTRA_FOCUS_MINUTES, durationMinutes)
        }
        return PendingIntent.getBroadcast(
            context,
            FOCUS_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
