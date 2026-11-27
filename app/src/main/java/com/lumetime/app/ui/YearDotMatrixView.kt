package com.lumetime.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Renders the current calendar year as a 365/366-dot living glass mosaic.
 * Passed days glow with the active theme accent, today pulses gently while visible,
 * and remaining days stay translucent. Zero per-frame object allocations in [onDraw].
 */
class YearDotMatrixView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var totalDays: Int = 365
    private var todayIndexZeroBased: Int = 0
    private var pulsePhase: Float = 0f

    private var accentColor: Int = 0xFF67E8F9.toInt()
    private var accentEndColor: Int = 0xFF3B82F6.toInt()
    private var futureDotColor: Int = 0x29FFFFFF

    private val passedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val futurePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val todayHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var pulseAnimator: ValueAnimator? = null

    fun setData(totalDays: Int, todayDayOfYear: Int) {
        val clampedTotal = totalDays.coerceIn(7, 366)
        val clampedToday = (todayDayOfYear - 1).coerceIn(0, clampedTotal - 1)
        if (this.totalDays != clampedTotal || this.todayIndexZeroBased != clampedToday) {
            this.totalDays = clampedTotal
            this.todayIndexZeroBased = clampedToday
            invalidate()
        }
    }

    fun setThemePalette(palette: ThemeManager.ThemePalette) {
        accentColor = palette.accent
        accentEndColor = palette.accentEnd
        futureDotColor = palette.dotFuture
        futurePaint.color = futureDotColor
        todayPaint.color = palette.textPrimary
        invalidate()
    }

    fun startPulse() {
        if (pulseAnimator?.isRunning == true) return
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1_800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                pulsePhase = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        val defaultHeight = (104f * density).toInt()
        val resolvedHeight = resolveSize(defaultHeight, heightMeasureSpec)
        setMeasuredDimension(desiredWidth, resolvedHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val availW = (width - paddingLeft - paddingRight).toFloat()
        val availH = (height - paddingTop - paddingBottom).toFloat()
        if (availW <= 0f || availH <= 0f) return

        val cols = when {
            totalDays <= 7 -> totalDays
            totalDays <= 24 -> 12
            totalDays <= 31 -> 16
            availW > availH * 3.0f -> 31
            else -> 26
        }
        val rows = (totalDays + cols - 1) / cols
        val cellW = availW / cols
        val cellH = availH / rows
        val radius = when {
            totalDays <= 7 -> (minOf(cellW, cellH) * 0.24f).coerceIn(6f, 16f)
            totalDays <= 31 -> (minOf(cellW, cellH) * 0.28f).coerceIn(4f, 11f)
            else -> (minOf(cellW, cellH) * 0.28f).coerceIn(2.2f, 8.5f)
        }
        val startX = paddingLeft + cellW * 0.5f
        val startY = paddingTop + cellH * 0.5f

        for (i in 0 until totalDays) {
            val col = i % cols
            val row = i / cols
            val cx = startX + col * cellW
            val cy = startY + row * cellH

            when {
                i < todayIndexZeroBased -> {
                    val ratio = if (todayIndexZeroBased > 0) {
                        i.toFloat() / todayIndexZeroBased.toFloat()
                    } else 1f
                    passedPaint.color = blendColors(accentEndColor, accentColor, ratio)
                    canvas.drawCircle(cx, cy, radius, passedPaint)
                }
                i == todayIndexZeroBased -> {
                    val haloRadius = radius * (1.55f + 0.65f * pulsePhase)
                    val haloAlpha = (145 - (80 * pulsePhase)).toInt().coerceIn(30, 180)
                    todayHaloPaint.color = ThemeManager.withAlpha(accentColor, haloAlpha)
                    canvas.drawCircle(cx, cy, haloRadius, todayHaloPaint)
                    canvas.drawCircle(cx, cy, radius * 1.18f, todayPaint)
                }
                else -> {
                    canvas.drawCircle(cx, cy, radius * 0.92f, futurePaint)
                }
            }
        }
    }

    private fun blendColors(from: Int, to: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val r = ((from shr 16 and 0xFF) + ((to shr 16 and 0xFF) - (from shr 16 and 0xFF)) * f).toInt()
        val g = ((from shr 8 and 0xFF) + ((to shr 8 and 0xFF) - (from shr 8 and 0xFF)) * f).toInt()
        val b = ((from and 0xFF) + ((to and 0xFF) - (from and 0xFF)) * f).toInt()
        return (0xE8 shl 24) or (r shl 16) or (g shl 8) or b
    }

    override fun onDetachedFromWindow() {
        stopPulse()
        super.onDetachedFromWindow()
    }
}
