package com.lumetime.app.ui

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator

/**
 * Attaches an iOS-like subtle spring press-scale interaction to interactive glass cards & pills.
 */
object FocusAnimations {

    private val releaseInterpolator = OvershootInterpolator(1.6f)

    @SuppressLint("ClickableViewAccessibility")
    fun attachGlassPress(view: View, pressedScale: Float = 0.975f) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(pressedScale)
                        .scaleY(pressedScale)
                        .setDuration(90L)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200L)
                        .setInterpolator(releaseInterpolator)
                        .start()
                }
            }
            false
        }
    }
}
