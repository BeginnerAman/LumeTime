package com.lumetime.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.lumetime.app.R
import com.lumetime.app.core.TimeCalculator
import java.util.Calendar
import java.util.TimeZone

/**
 * Zero-drain widget scheduler: refreshes all active LumeTime widgets only on
 * top-of-hour / midnight boundaries or immediately when user data changes in-app.
 */
object WidgetScheduler {

    const val ACTION_WIDGET_REFRESH = "com.lumetime.app.ACTION_WIDGET_REFRESH"
    private const val REQUEST_CODE_HOURLY = 5001

    /**
     * Returns the next top-of-hour epoch timestamp strictly after [nowMillis].
     */
    fun nextTopOfHourMillis(nowMillis: Long): Long {
        val remainder = nowMillis % TimeCalculator.MILLIS_PER_HOUR
        val delta = if (remainder == 0L) TimeCalculator.MILLIS_PER_HOUR else (TimeCalculator.MILLIS_PER_HOUR - remainder)
        return nowMillis + delta
    }

    /**
     * Returns the next midnight epoch timestamp strictly after [nowMillis] in [timeZone].
     */
    fun nextMidnightMillis(
        nowMillis: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 1)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun hasAnyActiveWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val providers = listOf(
            YearWidgetProvider::class.java,
            TimePulseGridWidgetProvider::class.java,
            HeroWidgetProvider::class.java,
            ListWidgetProvider::class.java
        )
        return providers.any { cls ->
            manager.getAppWidgetIds(ComponentName(context, cls)).isNotEmpty()
        }
    }

    fun scheduleNextRefresh(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = createRefreshPendingIntent(context)

        if (!hasAnyActiveWidgets(context)) {
            alarmManager.cancel(pending)
            return
        }

        val triggerAt = minOf(nextTopOfHourMillis(nowMillis), nextMidnightMillis(nowMillis))
        // Inexact window allows Android Doze/AppStandby batching -> practically 0% battery impact.
        alarmManager.setWindow(
            AlarmManager.RTC,
            triggerAt,
            5L * TimeCalculator.MILLIS_PER_MINUTE,
            pending
        )
    }

    fun cancelScheduledRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.cancel(createRefreshPendingIntent(context))
    }

    fun refreshAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)

        val yearIds = manager.getAppWidgetIds(ComponentName(context, YearWidgetProvider::class.java))
        if (yearIds.isNotEmpty()) {
            YearWidgetProvider.updateWidgets(context, manager, yearIds)
        }

        val pulseGridIds = manager.getAppWidgetIds(
            ComponentName(context, TimePulseGridWidgetProvider::class.java)
        )
        if (pulseGridIds.isNotEmpty()) {
            TimePulseGridWidgetProvider.updateWidgets(context, manager, pulseGridIds)
        }

        val heroIds = manager.getAppWidgetIds(ComponentName(context, HeroWidgetProvider::class.java))
        if (heroIds.isNotEmpty()) {
            HeroWidgetProvider.updateWidgets(context, manager, heroIds)
        }

        val listIds = manager.getAppWidgetIds(ComponentName(context, ListWidgetProvider::class.java))
        if (listIds.isNotEmpty()) {
            @Suppress("DEPRECATION")
            manager.notifyAppWidgetViewDataChanged(listIds, R.id.widgetListView)
            ListWidgetProvider.updateWidgets(context, manager, listIds)
        }

        scheduleNextRefresh(context)
    }

    private fun createRefreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetUpdateReceiver::class.java).apply {
            action = ACTION_WIDGET_REFRESH
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_HOURLY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
