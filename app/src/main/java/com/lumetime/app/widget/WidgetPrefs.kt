package com.lumetime.app.widget

import android.content.Context
import com.lumetime.app.R

/**
 * Appearance and behavior preferences for LumeTime Home Screen Widgets.
 */
object WidgetPrefs {

    private const val PREFS_NAME = "lumetime_widget_prefs"
    private const val KEY_GLASS_STYLE = "widget_glass_style"
    private const val KEY_OPACITY = "widget_opacity"
    private const val KEY_LIVE_CHRONOMETER = "widget_live_chronometer"

    enum class WidgetGlassStyle(val id: String, val label: String) {
        DARK("dark", "Dark Glass"),
        FROST("frost", "Frost Light");

        companion object {
            fun fromId(id: String?): WidgetGlassStyle =
                entries.firstOrNull { it.id == id } ?: DARK
        }
    }

    enum class WidgetOpacity(val id: String, val label: String) {
        FROSTED("frosted", "85%"),
        BALANCED("balanced", "65%"),
        CLEAR("clear", "35%");

        companion object {
            fun fromId(id: String?): WidgetOpacity =
                entries.firstOrNull { it.id == id } ?: BALANCED
        }
    }

    fun getGlassStyle(context: Context): WidgetGlassStyle {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetGlassStyle.fromId(prefs.getString(KEY_GLASS_STYLE, WidgetGlassStyle.DARK.id))
    }

    fun setGlassStyle(context: Context, style: WidgetGlassStyle) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GLASS_STYLE, style.id)
            .apply()
    }

    fun getOpacity(context: Context): WidgetOpacity {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetOpacity.fromId(prefs.getString(KEY_OPACITY, WidgetOpacity.BALANCED.id))
    }

    fun setOpacity(context: Context, opacity: WidgetOpacity) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_OPACITY, opacity.id)
            .apply()
    }

    fun isLiveChronometerEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LIVE_CHRONOMETER, true)
    }

    fun setLiveChronometerEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LIVE_CHRONOMETER, enabled)
            .apply()
    }

    fun resolveBackgroundRes(style: WidgetGlassStyle, opacity: WidgetOpacity): Int {
        return when (style) {
            WidgetGlassStyle.DARK -> when (opacity) {
                WidgetOpacity.FROSTED -> R.drawable.bg_widget_dark_frosted
                WidgetOpacity.BALANCED -> R.drawable.bg_widget_dark_balanced
                WidgetOpacity.CLEAR -> R.drawable.bg_widget_dark_clear
            }
            WidgetGlassStyle.FROST -> when (opacity) {
                WidgetOpacity.FROSTED -> R.drawable.bg_widget_light_frosted
                WidgetOpacity.BALANCED -> R.drawable.bg_widget_light_balanced
                WidgetOpacity.CLEAR -> R.drawable.bg_widget_light_clear
            }
        }
    }
}
