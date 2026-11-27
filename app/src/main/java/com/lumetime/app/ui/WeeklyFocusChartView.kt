package com.lumetime.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.lumetime.app.data.DayFocusStat

/**
 * Compact 7-day Liquid Glass Focus Bar Chart rendered directly on Canvas
 * with zero per-frame allocations.
 */
class WeeklyFocusChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var stats: List<DayFocusStat> = emptyList()
    private var goalMinutes: Int = 60

    private var trackColor: Int = 0x24FFFFFF
    private var accentColor: Int = 0xFF67E8F9.toInt()
    private var pastBarColor: Int = 0x993B82F6.toInt()
    private var labelColor: Int = 0xFFB8C4D9.toInt()

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goalLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val barRect = RectF()

    init {
        val d = resources.displayMetrics.density
        textPaint.textSize = 10f * d
        goalLinePaint.strokeWidth = (1f * d).coerceAtLeast(1f)
    }

    fun setThemePalette(palette: ThemeManager.ThemePalette) {
        trackColor = palette.progressTrack
        accentColor = palette.accent
        pastBarColor = ThemeManager.withAlpha(palette.accentEnd, 0xCC)
        labelColor = palette.textSecondary
        goalLinePaint.color = ThemeManager.withAlpha(palette.accent, 0x55)
        invalidate()
    }

    fun setStats(days: List<DayFocusStat>, dailyGoalMinutes: Int) {
        stats = days
        goalMinutes = dailyGoalMinutes.coerceAtLeast(15)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = stats.size
        if (count == 0) return

        val d = resources.displayMetrics.density
        val availW = (width - paddingLeft - paddingRight).toFloat()
        val labelZoneH = 16f * d
        val chartH = (height - paddingTop - paddingBottom - labelZoneH).coerceAtLeast(12f)
        if (availW <= 0f) return

        val slotW = availW / count.toFloat()
        val barW = (slotW * 0.42f).coerceIn(8f * d, 20f * d)
        val radius = barW * 0.5f

        val maxMin = maxOf(goalMinutes, stats.maxOfOrNull { it.minutes } ?: goalMinutes).coerceAtLeast(15)
        val goalY = paddingTop + chartH * (1f - (goalMinutes.toFloat() / maxMin.toFloat()).coerceIn(0f, 1f))
        canvas.drawLine(paddingLeft.toFloat(), goalY, (width - paddingRight).toFloat(), goalY, goalLinePaint)

        for (i in 0 until count) {
            val day = stats[i]
            val cx = paddingLeft + slotW * (i + 0.5f)
            val left = cx - barW * 0.5f
            val right = cx + barW * 0.5f
            val bottom = paddingTop + chartH

            trackPaint.color = trackColor
            barRect.set(left, paddingTop.toFloat(), right, bottom)
            canvas.drawRoundRect(barRect, radius, radius, trackPaint)

            if (day.minutes > 0) {
                val frac = (day.minutes.toFloat() / maxMin.toFloat()).coerceIn(0.08f, 1f)
                val top = bottom - chartH * frac
                barPaint.color = if (day.isToday) accentColor else pastBarColor
                barRect.set(left, top, right, bottom)
                canvas.drawRoundRect(barRect, radius, radius, barPaint)
            }

            textPaint.color = if (day.isToday) accentColor else labelColor
            canvas.drawText(day.dayShortLabel, cx, bottom + 13f * d, textPaint)
        }
    }
}
