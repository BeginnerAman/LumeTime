package com.lumetime.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Split-glass precision horology odometer digit tile for DD / HH / MM / SS cells.
 * Renders frosted glass sheen, subtle top specular rim, and center split hairline
 * with zero per-frame allocations inside [onDraw].
 */
class RollingDigitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var currentText: String = "00"
    private var previousText: String = "00"
    private var rollFraction: Float = 1f

    private var bgColor: Int = 0x7A1A2542.toInt()
    private var strokeColor: Int = 0x4DFFFFFF
    private var textColor: Int = 0xFFF8FAFC.toInt()
    private var shaderDirty: Boolean = true

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val topSheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val splitLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x26000000
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        fontFeatureSettings = "tnum"
    }
    private val boundsRect = RectF()
    private val topHalfRect = RectF()
    private val rollInterpolator = DecelerateInterpolator(1.6f)
    private var animator: ValueAnimator? = null

    init {
        val density = resources.displayMetrics.density
        textPaint.textSize = 24f * density
        strokePaint.strokeWidth = 1f * density
        splitLinePaint.strokeWidth = (1f * density).coerceAtLeast(1f)
    }

    fun setStyle(bg: Int, stroke: Int, text: Int, textSizeSp: Float = 24f) {
        bgColor = bg
        strokeColor = stroke
        textColor = text
        bgPaint.color = bgColor
        strokePaint.color = strokeColor
        textPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            textSizeSp,
            resources.displayMetrics
        )
        shaderDirty = true
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shaderDirty = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val corner = 18f * resources.displayMetrics.density
        val inset = strokePaint.strokeWidth * 0.5f
        boundsRect.set(inset, inset, w - inset, h - inset)

        if (shaderDirty) {
            shaderDirty = false
            topSheenPaint.shader = LinearGradient(
                0f,
                0f,
                0f,
                h * 0.55f,
                0x24FFFFFF,
                0x05FFFFFF,
                Shader.TileMode.CLAMP
            )
        }

        // Base frosted glass body
        canvas.drawRoundRect(boundsRect, corner, corner, bgPaint)

        // Top-half horology glass reflection
        canvas.save()
        topHalfRect.set(inset, inset, w - inset, h * 0.5f)
        canvas.clipRect(topHalfRect)
        canvas.drawRoundRect(boundsRect, corner, corner, topSheenPaint)
        canvas.restore()

        // Specular rim border
        canvas.drawRoundRect(boundsRect, corner, corner, strokePaint)

        // Rolling digits clipped inside glass tile
        canvas.save()
        canvas.clipRect(boundsRect)

        val cx = w * 0.5f
        val fm = textPaint.fontMetrics
        val baselineOffset = -(fm.ascent + fm.descent) * 0.5f
        val baseY = h * 0.5f + baselineOffset
        val travel = h * 0.40f

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

        // Subtle center split hairline across digit cell
        canvas.drawLine(inset + 4f, h * 0.5f, w - inset - 4f, h * 0.5f, splitLinePaint)

        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }
}
