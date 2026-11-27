package com.lumetime.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lumetime.app.core.ReminderScheduler

/**
 * Handles scheduled hourly/midnight widget updates and system clock/boot events.
 */
class WidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        when (action) {
            WidgetScheduler.ACTION_WIDGET_REFRESH -> {
                WidgetScheduler.refreshAllWidgets(context)
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED -> {
                WidgetScheduler.refreshAllWidgets(context)
                ReminderScheduler.rescheduleAll(context)
            }
        }
    }
}
