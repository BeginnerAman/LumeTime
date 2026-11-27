package com.lumetime.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.lumetime.app.FocusClockActivity
import com.lumetime.app.MainActivity
import com.lumetime.app.R
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.data.CountdownStore
import com.lumetime.app.model.CountdownIcon
import java.util.Locale

/**
 * 2x2 / 4x1 Glass Hero Countdown Widget with optional zero-drain native Chronometer live tick.
 */
class HeroWidgetProvider : AppWidgetProvider() {

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
            val items = CountdownStore.create(context).loadAll(now, forceReload = true)
            val hero = CountdownStore.selectHeroItem(items, now)

            val style = WidgetPrefs.getGlassStyle(context)
            val opacity = WidgetPrefs.getOpacity(context)
            val bgRes = WidgetPrefs.resolveBackgroundRes(style, opacity)
            val isDark = style == WidgetPrefs.WidgetGlassStyle.DARK
            val liveChrono = WidgetPrefs.isLiveChronometerEnabled(context)
            val iconBadgeBg = if (isDark) R.drawable.bg_widget_icon_dark else R.drawable.bg_widget_icon_light

            val primaryText = if (isDark) 0xFFF8FAFC.toInt() else 0xFF0F172A.toInt()
            val secondaryText = if (isDark) 0xFFB8C4D9.toInt() else 0xFF475569.toInt()
            val accentText = if (isDark) 0xFF67E8F9.toInt() else 0xFF0284C7.toInt()

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_hero_countdown)
                views.setInt(R.id.widgetHeroRoot, "setBackgroundResource", bgRes)
                views.setInt(R.id.widgetHeroIconBadge, "setBackgroundResource", iconBadgeBg)

                if (hero == null) {
                    val mainPending = PendingIntent.getActivity(
                        context,
                        5200,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widgetHeroRoot, mainPending)
                    views.setImageViewResource(R.id.ivWidgetHeroIcon, CountdownIcon.HOURGLASS.drawableRes)
                    views.setInt(R.id.ivWidgetHeroIcon, "setColorFilter", accentText)
                    views.setTextViewText(R.id.tvWidgetHeroTag, "LUMETIME")
                    views.setTextViewText(R.id.tvWidgetHeroTitle, "Tap to add a countdown")
                    views.setTextViewText(R.id.tvWidgetHeroDays, "--")
                    views.setTextViewText(R.id.tvWidgetHeroSub, "")
                    views.setViewVisibility(R.id.widgetHeroChrono, View.GONE)
                    views.setProgressBar(R.id.pbWidgetHero, 1000, 0, false)
                } else {
                    val breakdown = TimeCalculator.calculateBreakdown(hero, now)
                    val heroAccent = runCatching {
                        android.graphics.Color.parseColor(hero.accentHex)
                    }.getOrDefault(accentText)
                    val activeColor = if (breakdown.isExpired) secondaryText else heroAccent
                    val launchIntent = FocusClockActivity.createIntent(context, hero.id)
                    val clickPending = PendingIntent.getActivity(
                        context,
                        5201 + widgetId,
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widgetHeroRoot, clickPending)

                    views.setImageViewResource(R.id.ivWidgetHeroIcon, hero.resolveIcon().drawableRes)
                    views.setInt(R.id.ivWidgetHeroIcon, "setColorFilter", activeColor)
                    views.setTextViewText(
                        R.id.tvWidgetHeroTag,
                        when {
                            breakdown.isCountUp -> "MILESTONE"
                            hero.isPinned -> "PINNED FOCUS"
                            else -> "UP NEXT"
                        }
                    )
                    views.setTextColor(R.id.tvWidgetHeroTag, activeColor)

                    views.setTextViewText(R.id.tvWidgetHeroTitle, hero.title)
                    views.setTextColor(R.id.tvWidgetHeroTitle, primaryText)

                    if (breakdown.isExpired) {
                        views.setTextViewText(R.id.tvWidgetHeroDays, "Done")
                        views.setTextViewText(R.id.tvWidgetHeroSub, "Target reached")
                        views.setViewVisibility(R.id.widgetHeroChrono, View.GONE)
                        views.setProgressBar(R.id.pbWidgetHero, 1000, 1000, false)
                    } else {
                        val prefix = if (breakdown.isCountUp) "+" else ""
                        views.setTextViewText(
                            R.id.tvWidgetHeroDays,
                            String.format(Locale.US, "%s%dd", prefix, breakdown.days)
                        )
                        views.setTextViewText(
                            R.id.tvWidgetHeroSub,
                            String.format(
                                Locale.US,
                                "%02dh • %,d hrs",
                                breakdown.hours,
                                breakdown.totalHours
                            )
                        )

                        if (liveChrono && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            val deltaMillis = if (breakdown.isCountUp) {
                                (now - breakdown.effectiveTargetMillis).coerceAtLeast(0L)
                            } else {
                                (breakdown.effectiveTargetMillis - now).coerceAtLeast(0L)
                            }
                            val base = if (breakdown.isCountUp) {
                                SystemClock.elapsedRealtime() - deltaMillis
                            } else {
                                SystemClock.elapsedRealtime() + deltaMillis
                            }
                            views.setViewVisibility(R.id.widgetHeroChrono, View.VISIBLE)
                            views.setChronometerCountDown(R.id.widgetHeroChrono, !breakdown.isCountUp)
                            views.setChronometer(R.id.widgetHeroChrono, base, null, true)
                            views.setTextColor(R.id.widgetHeroChrono, activeColor)
                        } else {
                            views.setViewVisibility(R.id.widgetHeroChrono, View.GONE)
                        }

                        val progressValue = if (breakdown.isCountUp) 1000 else {
                            (breakdown.progress * 1000f).toInt().coerceIn(0, 1000)
                        }
                        views.setProgressBar(R.id.pbWidgetHero, 1000, progressValue, false)
                    }

                    views.setTextColor(R.id.tvWidgetHeroDays, primaryText)
                    views.setTextColor(R.id.tvWidgetHeroSub, secondaryText)
                }

                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }
    }
}
