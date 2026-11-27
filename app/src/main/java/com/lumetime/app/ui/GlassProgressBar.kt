package com.lumetime.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Lightweight rounded glass progress bar with horizontal accent gradient and zero tick allocations.
 */
class GlassProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var progress: Float = 0f
    private var trackColor: Int = 0x24FFFFFF
    private var startColor: Int = 0xFF3B82F6.toInt()
    private var endColor: Int = 0xFF67E8F9.toInt()
    private var shaderDirty: Boolean = true

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val fillRect = RectF()

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
        shaderDirty = true
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shaderDirty = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = h * 0.5f
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, radius, radius, trackPaint)

        if (progress <= 0f) return
        val fillW = (w * progress).coerceAtLeast(h).coerceAtMost(w)
        if (shaderDirty) {
            shaderDirty = false
            fillPaint.shader = LinearGradient(
                0f,
                0f,
                w,
                0f,
                startColor,
                endColor,
                Shader.TileMode.CLAMP
            )
        }
        fillRect.set(0f, 0f, fillW, h)
        canvas.drawRoundRect(fillRect, radius, radius, fillPaint)
    }
}
