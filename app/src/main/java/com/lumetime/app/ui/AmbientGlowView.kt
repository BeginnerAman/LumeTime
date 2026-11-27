package com.lumetime.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Hardware-friendly ambient radial glow orbs rendered behind glass surfaces.
 * Strictly lifecycle-bound: animates only between [startBreathing] and [stopBreathing].
 */
class AmbientGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val orb1Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orb2Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var colorPrimary: Int = 0xFF38BDF8.toInt()
    private var colorSecondary: Int = 0xFF6366F1.toInt()
    private var isDarkMode: Boolean = true
    private var breathPhase: Float = 0f
    private var gradientsDirty: Boolean = true
    private var lastW: Int = 0
    private var lastH: Int = 0

    private var breathAnimator: ValueAnimator? = null

    fun setThemePalette(palette: ThemeManager.ThemePalette) {
        colorPrimary = palette.theme.glowPrimary
        colorSecondary = palette.theme.glowSecondary
        isDarkMode = palette.isDark
        gradientsDirty = true
        invalidate()
    }

    fun startBreathing() {
        if (breathAnimator?.isRunning == true) return
        breathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 14_000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                breathPhase = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopBreathing() {
        breathAnimator?.cancel()
        breathAnimator = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != lastW || h != lastH) {
            lastW = w
            lastH = h
            gradientsDirty = true
        }
    }

    private fun rebuildShadersIfNeeded() {
        if (!gradientsDirty || width <= 0 || height <= 0) return
        gradientsDirty = false

        val alphaPrimary = if (isDarkMode) 0x42 else 0x30
        val alphaSecondary = if (isDarkMode) 0x36 else 0x26

        val r1 = (width * 0.72f).coerceAtLeast(120f)
        orb1Paint.shader = RadialGradient(
            0f,
            0f,
            r1,
            ThemeManager.withAlpha(colorPrimary, alphaPrimary),
            0x00000000,
            Shader.TileMode.CLAMP
        )

        val r2 = (width * 0.68f).coerceAtLeast(120f)
        orb2Paint.shader = RadialGradient(
            0f,
            0f,
            r2,
            ThemeManager.withAlpha(colorSecondary, alphaSecondary),
            0x00000000,
            Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        rebuildShadersIfNeeded()

        val w = width.toFloat()
        val h = height.toFloat()
        val driftX = (breathPhase - 0.5f) * w * 0.08f
        val driftY = (breathPhase - 0.5f) * h * 0.05f

        canvas.save()
        canvas.translate(w * 0.18f + driftX, h * 0.16f + driftY)
        canvas.drawCircle(0f, 0f, w * 0.72f, orb1Paint)
        canvas.restore()

        canvas.save()
        canvas.translate(w * 0.84f - driftX, h * 0.58f - driftY)
        canvas.drawCircle(0f, 0f, w * 0.68f, orb2Paint)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        stopBreathing()
        super.onDetachedFromWindow()
    }
}
