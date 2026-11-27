package com.lumetime.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Hardware-accelerated multi-orb ambient liquid mesh rendered behind glass cards.
 * Uses native GPU RenderEffect blur on Android 12+ and animates strictly between
 * [startBreathing] and [stopBreathing] for 0% background battery drain.
 */
class AmbientGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val orb1Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orb2Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orb3Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var colorPrimary: Int = 0xFF38BDF8.toInt()
    private var colorSecondary: Int = 0xFF6366F1.toInt()
    private var colorAccent: Int = 0xFF67E8F9.toInt()
    private var isDarkMode: Boolean = true
    private var pureBlackMode: Boolean = false
    private var breathPhase: Float = 0f
    private var gradientsDirty: Boolean = true
    private var lastW: Int = 0
    private var lastH: Int = 0

    private var breathAnimator: ValueAnimator? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(RenderEffect.createBlurEffect(90f, 90f, Shader.TileMode.CLAMP))
        }
    }

    fun setThemePalette(palette: ThemeManager.ThemePalette) {
        pureBlackMode = palette.isPureBlack
        if (pureBlackMode) {
            stopBreathing()
            visibility = GONE
            return
        }
        visibility = VISIBLE
        colorPrimary = palette.theme.glowPrimary
        colorSecondary = palette.theme.glowSecondary
        colorAccent = palette.accent
        isDarkMode = palette.isDark
        gradientsDirty = true
        invalidate()
        if (isAttachedToWindow) {
            startBreathing()
        }
    }

    fun startBreathing() {
        if (pureBlackMode) return
        if (breathAnimator?.isRunning == true) return
        breathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 16_000L
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

        val alphaPrimary = if (isDarkMode) 0x7A else 0x5E
        val alphaSecondary = if (isDarkMode) 0x66 else 0x4A
        val alphaThird = if (isDarkMode) 0x4E else 0x3A

        val r1 = (width * 0.78f).coerceAtLeast(140f)
        orb1Paint.shader = RadialGradient(
            0f,
            0f,
            r1,
            ThemeManager.withAlpha(colorPrimary, alphaPrimary),
            0x00000000,
            Shader.TileMode.CLAMP
        )

        val r2 = (width * 0.74f).coerceAtLeast(140f)
        orb2Paint.shader = RadialGradient(
            0f,
            0f,
            r2,
            ThemeManager.withAlpha(colorSecondary, alphaSecondary),
            0x00000000,
            Shader.TileMode.CLAMP
        )

        val r3 = (width * 0.62f).coerceAtLeast(120f)
        orb3Paint.shader = RadialGradient(
            0f,
            0f,
            r3,
            ThemeManager.withAlpha(colorAccent, alphaThird),
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
        val driftX = (breathPhase - 0.5f) * w * 0.12f
        val driftY = (breathPhase - 0.5f) * h * 0.08f

        canvas.save()
        canvas.translate(w * 0.16f + driftX, h * 0.14f + driftY)
        canvas.drawCircle(0f, 0f, w * 0.78f, orb1Paint)
        canvas.restore()

        canvas.save()
        canvas.translate(w * 0.86f - driftX, h * 0.44f - driftY)
        canvas.drawCircle(0f, 0f, w * 0.74f, orb2Paint)
        canvas.restore()

        canvas.save()
        canvas.translate(w * 0.28f - driftX * 0.6f, h * 0.82f + driftY * 0.7f)
        canvas.drawCircle(0f, 0f, w * 0.62f, orb3Paint)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        stopBreathing()
        super.onDetachedFromWindow()
    }
}
