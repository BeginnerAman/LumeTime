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
import com.lumetime.app.model.TimePulseScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 4x2 Specular Liquid Glass Widget displaying live Today, Week, Month, and Year progress bars.
 */
class TimePulseGridWidgetProvider : AppWidgetProvider() {

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
            val today = TimeCalculator.computePeriodProgress(TimePulseScope.TODAY, now)
            val week = TimeCalculator.computePeriodProgress(TimePulseScope.WEEK, now)
            val month = TimeCalculator.computePeriodProgress(TimePulseScope.MONTH, now)
            val year = TimeCalculator.computePeriodProgress(TimePulseScope.YEAR, now)

            val style = WidgetPrefs.getGlassStyle(context)
            val opacity = WidgetPrefs.getOpacity(context)
            val bgRes = WidgetPrefs.resolveBackgroundRes(style, opacity)
            val isDark = style == WidgetPrefs.WidgetGlassStyle.DARK

            val primaryText = if (isDark) 0xFFF8FAFC.toInt() else 0xFF0F172A.toInt()
            val secondaryText = if (isDark) 0xFFB8C4D9.toInt() else 0xFF475569.toInt()
            val accentText = if (isDark) 0xFF67E8F9.toInt() else 0xFF0284C7.toInt()

            val dateLabel = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(now))
            val launchPending = PendingIntent.getActivity(
                context,
                5401,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_time_pulse_grid).apply {
                    setInt(R.id.widgetPulseGridRoot, "setBackgroundResource", bgRes)
                    setOnClickPendingIntent(R.id.widgetPulseGridRoot, launchPending)

                    setTextColor(R.id.tvPulseGridHeader, accentText)
                    setTextViewText(R.id.tvPulseGridDate, dateLabel)
                    setTextColor(R.id.tvPulseGridDate, secondaryText)

                    // Today
                    setTextColor(R.id.tvPulseTodayLabel, primaryText)
                    setTextViewText(
                        R.id.tvPulseTodayVal,
                        String.format(Locale.US, "%.0f%% • %s", today.percentCompleted, today.summaryLeft)
                    )
                    setTextColor(R.id.tvPulseTodayVal, secondaryText)
                    setProgressBar(
                        R.id.pbPulseToday,
                        1000,
                        (today.progressFraction * 1000f).toInt().coerceIn(0, 1000),
                        false
                    )

                    // Week
                    setTextColor(R.id.tvPulseWeekLabel, primaryText)
                    setTextViewText(
                        R.id.tvPulseWeekVal,
                        String.format(Locale.US, "%.0f%% • %s", week.percentCompleted, week.badgeText)
                    )
                    setTextColor(R.id.tvPulseWeekVal, secondaryText)
                    setProgressBar(
                        R.id.pbPulseWeek,
                        1000,
                        (week.progressFraction * 1000f).toInt().coerceIn(0, 1000),
                        false
                    )

                    // Month
                    setTextColor(R.id.tvPulseMonthLabel, primaryText)
                    setTextViewText(
                        R.id.tvPulseMonthVal,
                        String.format(Locale.US, "%.0f%% • %sd left", month.percentCompleted, month.summaryLeft)
                    )
                    setTextColor(R.id.tvPulseMonthVal, secondaryText)
                    setProgressBar(
                        R.id.pbPulseMonth,
                        1000,
                        (month.progressFraction * 1000f).toInt().coerceIn(0, 1000),
                        false
                    )

                    // Year
                    setTextColor(R.id.tvPulseYearLabel, primaryText)
                    setTextViewText(
                        R.id.tvPulseYearVal,
                        String.format(Locale.US, "%.1f%% • %sd left", year.percentCompleted, year.summaryLeft)
                    )
                    setTextColor(R.id.tvPulseYearVal, secondaryText)
                    setProgressBar(
                        R.id.pbPulseYear,
                        1000,
                        (year.progressFraction * 1000f).toInt().coerceIn(0, 1000),
                        false
                    )
                }
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
