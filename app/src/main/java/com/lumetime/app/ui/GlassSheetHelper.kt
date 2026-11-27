package com.lumetime.app.ui

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import com.lumetime.app.R

/**
 * Configures bottom-anchored Apple Liquid Glass sheets with slide-up motion
 * and proper edge-to-edge width across phones & landscape/tablets.
 */
object GlassSheetHelper {

    fun createBottomSheet(context: Context): Dialog {
        return Dialog(context, R.style.Theme_LumeTime_GlassDialog).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
        }
    }

    fun configureBottomWindow(dialog: Dialog, context: Context) {
        val window = dialog.window ?: return
        val screenWidth = context.resources.displayMetrics.widthPixels
        val maxSheetWidth = ThemeManager.dp(context, 560f).toInt()
        val targetWidth = screenWidth.coerceAtMost(maxSheetWidth)
        window.setLayout(targetWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        window.setBackgroundDrawableResource(android.R.color.transparent)
    }
}
