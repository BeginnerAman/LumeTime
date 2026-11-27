package com.lumetime.app.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.lumetime.app.FocusClockActivity
import com.lumetime.app.R
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.data.CountdownFilter
import com.lumetime.app.data.CountdownSortMode
import com.lumetime.app.data.CountdownStore
import com.lumetime.app.model.CountdownItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RemoteViewsService backing the scrollable Multi-Countdown Home Screen Widget.
 */
class ListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ListWidgetFactory(applicationContext)
    }
}

private class ListWidgetFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<CountdownItem> = emptyList()
    private val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val now = System.currentTimeMillis()
        val loaded = CountdownStore.create(context).loadAll(now, forceReload = true)
        items = CountdownStore.filterAndSort(
            loaded,
            CountdownFilter.All,
            CountdownSortMode.SOONEST,
            now
        ).take(25)
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.item_widget_countdown)

        val now = System.currentTimeMillis()
        val breakdown = TimeCalculator.calculateBreakdown(item, now)
        val isDark = WidgetPrefs.getGlassStyle(context) == WidgetPrefs.WidgetGlassStyle.DARK
        val rowBg = if (isDark) R.drawable.bg_widget_row_dark else R.drawable.bg_widget_row_light
        val primaryText = if (isDark) 0xFFF8FAFC.toInt() else 0xFF0F172A.toInt()
        val secondaryText = if (isDark) 0xFFB8C4D9.toInt() else 0xFF475569.toInt()
        val accentText = when {
            breakdown.isExpired -> secondaryText
            !breakdown.isCountUp && breakdown.totalHours < 24L -> 0xFFFB7185.toInt()
            !breakdown.isCountUp && breakdown.days < 7L -> 0xFFFBBF24.toInt()
            isDark -> 0xFF67E8F9.toInt()
            else -> 0xFF0284C7.toInt()
        }

        val metricText = when {
            breakdown.isExpired -> "Done"
            breakdown.isCountUp -> "+${breakdown.days}d"
            else -> "${breakdown.days}d"
        }
        val hoursText = when {
            breakdown.isExpired -> "Reached"
            breakdown.isCountUp -> String.format(Locale.US, "+%,dh", breakdown.totalHours)
            else -> String.format(Locale.US, "%02dh • %,dh", breakdown.hours, breakdown.totalHours)
        }

        val fillInIntent = Intent().apply {
            putExtra(FocusClockActivity.EXTRA_COUNTDOWN_ID, item.id)
        }

        return RemoteViews(context.packageName, R.layout.item_widget_countdown).apply {
            setInt(R.id.widgetRowRoot, "setBackgroundResource", rowBg)
            setImageViewResource(R.id.ivWidgetRowIcon, item.resolveIcon().drawableRes)
            setInt(R.id.ivWidgetRowIcon, "setColorFilter", accentText)
            setTextViewText(R.id.tvWidgetRowTitle, item.title)
            setTextColor(R.id.tvWidgetRowTitle, primaryText)
            setTextViewText(
                R.id.tvWidgetRowDate,
                dateFormatter.format(Date(breakdown.effectiveTargetMillis))
            )
            setTextColor(R.id.tvWidgetRowDate, secondaryText)
            setTextViewText(R.id.tvWidgetRowMetric, metricText)
            setTextColor(R.id.tvWidgetRowMetric, accentText)
            setTextViewText(R.id.tvWidgetRowHours, hoursText)
            setTextColor(R.id.tvWidgetRowHours, secondaryText)
            setOnClickFillInIntent(R.id.widgetRowRoot, fillInIntent)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
