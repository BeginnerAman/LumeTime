package com.lumetime.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View

/**
 * Circular Liquid Glass progress ring used in full-screen Zen Focus Mode.
 * Allocates zero objects inside [onDraw].
 */
class GlassProgressRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var progress: Float = 0f
    private var trackColor: Int = 0x24FFFFFF
    private var startColor: Int = 0xFF3B82F6.toInt()
    private var endColor: Int = 0xFF67E8F9.toInt()
    private var shaderDirty: Boolean = true

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcRect = RectF()

    init {
        val strokePx = 10f * resources.displayMetrics.density
        trackPaint.strokeWidth = strokePx
        arcPaint.strokeWidth = strokePx
        glowPaint.strokeWidth = strokePx * 1.9f
    }

    fun setProgress(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (clamped != progress) {
            progress = clamped
            invalidate()
        }
    }

    fun setColors(track: Int, accentStart: Int, accentEnd: Int) {
        trackColor = track
        startColor = accentStart
        endColor = accentEnd
        trackPaint.color = trackColor
        glowPaint.color = ThemeManager.withAlpha(accentEnd, 0x30)
        shaderDirty = true
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shaderDirty = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return

        val pad = glowPaint.strokeWidth * 0.55f
        val left = (width - size) * 0.5f + pad
        val top = (height - size) * 0.5f + pad
        val right = (width + size) * 0.5f - pad
        val bottom = (height + size) * 0.5f - pad
        arcRect.set(left, top, right, bottom)

        canvas.drawArc(arcRect, 0f, 360f, false, trackPaint)

        if (progress <= 0f) return
        if (shaderDirty) {
            shaderDirty = false
            arcPaint.shader = SweepGradient(
                width * 0.5f,
                height * 0.5f,
                intArrayOf(startColor, endColor, startColor),
                floatArrayOf(0f, 0.75f, 1f)
            )
        }

        val sweep = (360f * progress).coerceIn(4f, 360f)
        canvas.save()
        canvas.rotate(-90f, width * 0.5f, height * 0.5f)
        canvas.drawArc(arcRect, 0f, sweep, false, glowPaint)
        canvas.drawArc(arcRect, 0f, sweep, false, arcPaint)
        canvas.restore()
    }
}
