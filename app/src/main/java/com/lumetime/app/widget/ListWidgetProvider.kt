package com.lumetime.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.lumetime.app.FocusClockActivity
import com.lumetime.app.MainActivity
import com.lumetime.app.R
import com.lumetime.app.core.TimeCalculator
import java.util.Locale

/**
 * 4x2 / 4x4 Scrollable Multi-Countdown Glass List Widget with Year Pulse header.
 */
class ListWidgetProvider : AppWidgetProvider() {

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
            val yearProgress = TimeCalculator.computeYearProgress(now)
            val style = WidgetPrefs.getGlassStyle(context)
            val opacity = WidgetPrefs.getOpacity(context)
            val bgRes = WidgetPrefs.resolveBackgroundRes(style, opacity)
            val isDark = style == WidgetPrefs.WidgetGlassStyle.DARK

            val primaryText = if (isDark) 0xFFF8FAFC.toInt() else 0xFF0F172A.toInt()
            val secondaryText = if (isDark) 0xFFB8C4D9.toInt() else 0xFF475569.toInt()
            val accentText = if (isDark) 0xFF67E8F9.toInt() else 0xFF0284C7.toInt()

            val headerIntent = Intent(context, MainActivity::class.java)
            val headerPending = PendingIntent.getActivity(
                context,
                5300,
                headerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val itemTemplateIntent = Intent(context, FocusClockActivity::class.java)
            val itemTemplatePending = PendingIntent.getActivity(
                context,
                5301,
                itemTemplateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            appWidgetIds.forEach { widgetId ->
                val serviceIntent = Intent(context, ListWidgetService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }

                val views = RemoteViews(context.packageName, R.layout.widget_countdown_list).apply {
                    setInt(R.id.widgetListRoot, "setBackgroundResource", bgRes)
                    setOnClickPendingIntent(R.id.widgetListHeader, headerPending)

                    setTextColor(R.id.tvWidgetListBrand, primaryText)
                    setTextViewText(
                        R.id.tvWidgetListYearMeta,
                        String.format(
                            Locale.US,
                            "%d: %dd left • %.1f%%",
                            yearProgress.year,
                            yearProgress.daysLeft,
                            yearProgress.percentCompleted
                        )
                    )
                    setTextColor(R.id.tvWidgetListYearMeta, accentText)
                    setTextColor(R.id.widgetListEmpty, secondaryText)

                    @Suppress("DEPRECATION")
                    setRemoteAdapter(R.id.widgetListView, serviceIntent)
                    setEmptyView(R.id.widgetListView, R.id.widgetListEmpty)
                    setPendingIntentTemplate(R.id.widgetListView, itemTemplatePending)
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
