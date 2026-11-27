package com.lumetime.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lumetime.app.MainActivity
import com.lumetime.app.R
import com.lumetime.app.core.TimeCalculator
import java.util.Locale

/**
 * 4x1 Apple-style Glass Home Screen Widget displaying Year Pulse days, hours, and % progress.
 */
class YearWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
        WidgetScheduler.scheduleNextRefresh(context)
    }

    override fun onEnabled(context: Context) {
        WidgetScheduler.scheduleNextRefresh(context)
    }

    override fun onDisabled(context: Context) {
        if (!WidgetScheduler.hasAnyActiveWidgets(context)) {
            WidgetScheduler.cancelScheduledRefresh(context)
        }
    }

    companion object {
        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val now = System.currentTimeMillis()
            val progress = TimeCalculator.computeYearProgress(now)
            val style = WidgetPrefs.getGlassStyle(context)
            val opacity = WidgetPrefs.getOpacity(context)
            val bgRes = WidgetPrefs.resolveBackgroundRes(style, opacity)
            val isDark = style == WidgetPrefs.WidgetGlassStyle.DARK

            val primaryText = if (isDark) 0xFFF8FAFC.toInt() else 0xFF0F172A.toInt()
            val secondaryText = if (isDark) 0xFFB8C4D9.toInt() else 0xFF475569.toInt()
            val accentText = if (isDark) 0xFF67E8F9.toInt() else 0xFF0284C7.toInt()

            val launchIntent = Intent(context, MainActivity::class.java)
            val clickPending = PendingIntent.getActivity(
                context,
                5101,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_year_pulse).apply {
                    setInt(R.id.widgetYearRoot, "setBackgroundResource", bgRes)
                    setOnClickPendingIntent(R.id.widgetYearRoot, clickPending)

                    setTextViewText(R.id.tvWidgetYearBadge, "${progress.year} • YEAR PULSE")
                    setTextColor(R.id.tvWidgetYearBadge, accentText)

                    setTextViewText(
                        R.id.tvWidgetYearPercent,
                        String.format(Locale.US, "%.1f%%", progress.percentCompleted)
                    )
                    setTextColor(R.id.tvWidgetYearPercent, secondaryText)

                    setTextViewText(
                        R.id.tvWidgetYearDays,
                        String.format(Locale.US, "%d days left", progress.daysLeft)
                    )
                    setTextColor(R.id.tvWidgetYearDays, primaryText)

                    setTextViewText(
                        R.id.tvWidgetYearHours,
                        String.format(Locale.US, "• %,d hrs", progress.hoursLeft)
                    )
                    setTextColor(R.id.tvWidgetYearHours, secondaryText)

                    setProgressBar(
                        R.id.pbWidgetYear,
                        1000,
                        (progress.progressFraction * 1000f).toInt().coerceIn(0, 1000),
                        false
                    )
                }
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
