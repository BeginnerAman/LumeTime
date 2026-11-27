package com.lumetime.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Silky-smooth vertical odometer digit view for DD / HH / MM / SS cells.
 * Animates only when the displayed text changes and allocates zero objects inside [onDraw].
 */
class RollingDigitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var currentText: String = "00"
    private var previousText: String = "00"
    private var rollFraction: Float = 1f

    private var bgColor: Int = 0x99232E4D.toInt()
    private var strokeColor: Int = 0x38FFFFFF
    private var textColor: Int = 0xFFF8FAFC.toInt()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        fontFeatureSettings = "tnum"
    }
    private val boundsRect = RectF()
    private val rollInterpolator = DecelerateInterpolator(1.6f)
    private var animator: ValueAnimator? = null

    init {
        val density = resources.displayMetrics.density
        textPaint.textSize = 24f * density
        strokePaint.strokeWidth = 1f * density
    }

    fun setStyle(bg: Int, stroke: Int, text: Int, textSizeSp: Float = 24f) {
        bgColor = bg
        strokeColor = stroke
        textColor = text
        bgPaint.color = bgColor
        strokePaint.color = strokeColor
        textPaint.textSize = textSizeSp * resources.displayMetrics.scaledDensity
        invalidate()
    }

    fun setValue(newValue: String, animate: Boolean = true) {
        if (newValue == currentText) return
        if (!animate || width == 0 || height == 0) {
            animator?.cancel()
            previousText = newValue
            currentText = newValue
            rollFraction = 1f
            invalidate()
            return
        }

        previousText = currentText
        currentText = newValue
        animator?.cancel()
        rollFraction = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            interpolator = rollInterpolator
            addUpdateListener { anim ->
                rollFraction = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val corner = 16f * resources.displayMetrics.density
        val inset = strokePaint.strokeWidth * 0.5f
        boundsRect.set(inset, inset, w - inset, h - inset)
        canvas.drawRoundRect(boundsRect, corner, corner, bgPaint)
        canvas.drawRoundRect(boundsRect, corner, corner, strokePaint)

        canvas.save()
        canvas.clipRect(boundsRect)

        val cx = w * 0.5f
        val fm = textPaint.fontMetrics
        val baselineOffset = -(fm.ascent + fm.descent) * 0.5f
        val baseY = h * 0.5f + baselineOffset
        val travel = h * 0.42f

        if (rollFraction < 1f && previousText != currentText) {
            val outAlpha = ((1f - rollFraction) * 255f).toInt().coerceIn(0, 255)
            textPaint.color = ThemeManager.withAlpha(textColor, outAlpha)
            canvas.drawText(previousText, cx, baseY - travel * rollFraction, textPaint)

            val inAlpha = (rollFraction * 255f).toInt().coerceIn(0, 255)
            textPaint.color = ThemeManager.withAlpha(textColor, inAlpha)
            canvas.drawText(currentText, cx, baseY + travel * (1f - rollFraction), textPaint)
        } else {
            textPaint.color = textColor
            canvas.drawText(currentText, cx, baseY, textPaint)
        }

        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
