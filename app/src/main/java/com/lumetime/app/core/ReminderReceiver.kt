package com.lumetime.app.core

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lumetime.app.FocusClockActivity
import com.lumetime.app.R
import com.lumetime.app.data.CountdownStore

/**
 * Displays lightweight local countdown reminders and reschedules repeating reminders if needed.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val itemId = intent?.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_TITLE).orEmpty()
        val stage = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_STAGE)
            ?: ReminderScheduler.STAGE_EVENT_TIME

        if (title.isBlank()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        TimerAlertManager.ensureHighImportanceChannel(context)

        val bodyText = if (stage == ReminderScheduler.STAGE_DAY_BEFORE) {
            "24 hours left until $title"
        } else {
            "Countdown reached for $title"
        }

        val tapIntent = FocusClockActivity.createIntent(context, itemId)
        val tapPending = PendingIntent.getActivity(
            context,
            itemId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, TimerAlertManager.ALERTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sparkle)
            .setContentTitle(title.trim())
            .setContentText(bodyText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .build()

        NotificationManagerCompat.from(context).notify(itemId.hashCode() + stage.hashCode(), notification)
        if (stage == ReminderScheduler.STAGE_EVENT_TIME) {
            TimerAlertManager.playCompletionChimeAndVibrate(context)
        }

        // If repeating event arrived, queue next cycle's reminder.
        if (stage == ReminderScheduler.STAGE_EVENT_TIME) {
            val item = CountdownStore.create(context)
                .loadAll(forceReload = true)
                .firstOrNull { it.id == itemId }
            if (item != null && item.reminderEnabled) {
                ReminderScheduler.scheduleForItem(context, item)
            }
        }
    }
}
