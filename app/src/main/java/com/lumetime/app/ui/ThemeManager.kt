package com.lumetime.app.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.view.WindowCompat

/**
 * Curated Apple-style Liquid Glass themes with smooth ambient crossfades,
 * Dark/Light/Auto glass mode control, and crisp haptic feedback.
 */
object ThemeManager {

    private const val PREFS_NAME = "lumetime_prefs"
    private const val KEY_THEME_ID = "selected_theme_id"
    private const val KEY_APPEARANCE = "appearance_mode"

    enum class AppearanceMode(val id: String) {
        AUTO("auto"),
        DARK("dark"),
        LIGHT("light");

        companion object {
            fun fromId(id: String?): AppearanceMode =
                entries.firstOrNull { it.id == id } ?: DARK
        }
    }

    enum class AppTheme(
        val id: String,
        val title: String,
        val tagline: String,
        val previewStart: Int,
        val previewEnd: Int,
        val darkAccent: Int,
        val darkAccentEnd: Int,
        val lightAccent: Int,
        val lightAccentEnd: Int,
        val darkBgTop: Int,
        val darkBgMid: Int,
        val darkBgBottom: Int,
        val lightBgTop: Int,
        val lightBgMid: Int,
        val lightBgBottom: Int,
        val glowPrimary: Int,
        val glowSecondary: Int
    ) {
        OBSIDIAN(
            id = "obsidian",
            title = "Midnight Obsidian",
            tagline = "Deep space indigo & cyan luminescence",
            previewStart = 0xFF38BDF8.toInt(),
            previewEnd = 0xFF4F46E5.toInt(),
            darkAccent = 0xFF67E8F9.toInt(),
            darkAccentEnd = 0xFF3B82F6.toInt(),
            lightAccent = 0xFF0284C7.toInt(),
            lightAccentEnd = 0xFF4F46E5.toInt(),
            darkBgTop = 0xFF060A16.toInt(),
            darkBgMid = 0xFF0C1328.toInt(),
            darkBgBottom = 0xFF131B38.toInt(),
            lightBgTop = 0xFFEFF6FF.toInt(),
            lightBgMid = 0xFFF7FAFF.toInt(),
            lightBgBottom = 0xFFE2ECFF.toInt(),
            glowPrimary = 0xFF38BDF8.toInt(),
            glowSecondary = 0xFF6366F1.toInt()
        ),
        FROST(
            id = "frost",
            title = "Apple Frost",
            tagline = "Crisp ice-blue translucent glass",
            previewStart = 0xFF7DD3FC.toInt(),
            previewEnd = 0xFF0EA5E9.toInt(),
            darkAccent = 0xFF7DD3FC.toInt(),
            darkAccentEnd = 0xFF0284C7.toInt(),
            lightAccent = 0xFF0A84FF.toInt(),
            lightAccentEnd = 0xFF0284C7.toInt(),
            darkBgTop = 0xFF07111E.toInt(),
            darkBgMid = 0xFF0D1C30.toInt(),
            darkBgBottom = 0xFF122742.toInt(),
            lightBgTop = 0xFFEDF6FF.toInt(),
            lightBgMid = 0xFFF6FAFF.toInt(),
            lightBgBottom = 0xFFDCEEFF.toInt(),
            glowPrimary = 0xFF38BDF8.toInt(),
            glowSecondary = 0xFF0EA5E9.toInt()
        ),
        AURORA(
            id = "aurora",
            title = "Aurora Mint",
            tagline = "Bioluminescent emerald & seafoam",
            previewStart = 0xFF34D399.toInt(),
            previewEnd = 0xFF059669.toInt(),
            darkAccent = 0xFF5EEAD4.toInt(),
            darkAccentEnd = 0xFF10B981.toInt(),
            lightAccent = 0xFF059669.toInt(),
            lightAccentEnd = 0xFF0D9488.toInt(),
            darkBgTop = 0xFF051310.toInt(),
            darkBgMid = 0xFF0A201B.toInt(),
            darkBgBottom = 0xFF0E2C24.toInt(),
            lightBgTop = 0xFFECFEF6.toInt(),
            lightBgMid = 0xFFF5FFFA.toInt(),
            lightBgBottom = 0xFFD9FAEC.toInt(),
            glowPrimary = 0xFF34D399.toInt(),
            glowSecondary = 0xFF2DD4BF.toInt()
        ),
        SUNSET(
            id = "sunset",
            title = "Sunset Rose",
            tagline = "Warm coral blush & twilight amber",
            previewStart = 0xFFFB7185.toInt(),
            previewEnd = 0xFFF59E0B.toInt(),
            darkAccent = 0xFFFDA4AF.toInt(),
            darkAccentEnd = 0xFFF43F5E.toInt(),
            lightAccent = 0xFFE11D48.toInt(),
            lightAccentEnd = 0xFFEA580C.toInt(),
            darkBgTop = 0xFF170910.toInt(),
            darkBgMid = 0xFF230F19.toInt(),
            darkBgBottom = 0xFF2E1421.toInt(),
            lightBgTop = 0xFFFFF1F3.toInt(),
            lightBgMid = 0xFFFFF7F8.toInt(),
            lightBgBottom = 0xFFFFE2E7.toInt(),
            glowPrimary = 0xFFFB7185.toInt(),
            glowSecondary = 0xFFF59E0B.toInt()
        ),
        OCEAN(
            id = "ocean",
            title = "Deep Ocean",
            tagline = "Midnight cobalt & electric azure",
            previewStart = 0xFF60A5FA.toInt(),
            previewEnd = 0xFF2563EB.toInt(),
            darkAccent = 0xFF93C5FD.toInt(),
            darkAccentEnd = 0xFF3B82F6.toInt(),
            lightAccent = 0xFF2563EB.toInt(),
            lightAccentEnd = 0xFF1D4ED8.toInt(),
            darkBgTop = 0xFF060E1F.toInt(),
            darkBgMid = 0xFF0B1834.toInt(),
            darkBgBottom = 0xFF10224A.toInt(),
            lightBgTop = 0xFFEEF4FF.toInt(),
            lightBgMid = 0xFFF6F9FF.toInt(),
            lightBgBottom = 0xFFDCE8FF.toInt(),
            glowPrimary = 0xFF60A5FA.toInt(),
            glowSecondary = 0xFF3B82F6.toInt()
        ),
        LAVENDER(
            id = "lavender",
            title = "Lavender Mist",
            tagline = "Dreamy violet-tinted glass",
            previewStart = 0xFFA78BFA.toInt(),
            previewEnd = 0xFF7C3AED.toInt(),
            darkAccent = 0xFFC4B5FD.toInt(),
            darkAccentEnd = 0xFF8B5CF6.toInt(),
            lightAccent = 0xFF7C3AED.toInt(),
            lightAccentEnd = 0xFF6D28D9.toInt(),
            darkBgTop = 0xFF0D091C.toInt(),
            darkBgMid = 0xFF150F2B.toInt(),
            darkBgBottom = 0xFF1E163D.toInt(),
            lightBgTop = 0xFFF4F0FF.toInt(),
            lightBgMid = 0xFFFAF8FF.toInt(),
            lightBgBottom = 0xFFE9E0FF.toInt(),
            glowPrimary = 0xFFA78BFA.toInt(),
            glowSecondary = 0xFFC084FC.toInt()
        );

        companion object {
            fun fromId(id: String?): AppTheme =
                entries.firstOrNull { it.id == id } ?: OBSIDIAN
        }
    }

    data class ThemePalette(
        val theme: AppTheme,
        val isDark: Boolean,
        val bgTop: Int,
        val bgMid: Int,
        val bgBottom: Int,
        val glassSurface: Int,
        val glassHeroSurface: Int,
        val glassSurfaceStrong: Int,
        val glassPill: Int,
        val glassStroke: Int,
        val glassHeroStroke: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val textTertiary: Int,
        val accent: Int,
        val accentEnd: Int,
        val accentSoft: Int,
        val dotFuture: Int,
        val progressTrack: Int,
        val warningAmber: Int,
        val urgentCoral: Int
    )

    private var bgAnimator: ValueAnimator? = null
    private var currentBgColors: IntArray? = null

    fun getSavedTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppTheme.fromId(prefs.getString(KEY_THEME_ID, AppTheme.OBSIDIAN.id))
    }

    fun saveTheme(context: Context, theme: AppTheme) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_ID, theme.id)
            .apply()
    }

    fun getSavedAppearance(context: Context): AppearanceMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AppearanceMode.fromId(prefs.getString(KEY_APPEARANCE, AppearanceMode.DARK.id))
    }

    fun saveAppearance(context: Context, mode: AppearanceMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APPEARANCE, mode.id)
            .apply()
    }

    fun isDarkActive(context: Context, mode: AppearanceMode = getSavedAppearance(context)): Boolean {
        return when (mode) {
            AppearanceMode.DARK -> true
            AppearanceMode.LIGHT -> false
            AppearanceMode.AUTO -> {
                val uiMode =
                    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                uiMode == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    fun getPalette(
        context: Context,
        theme: AppTheme = getSavedTheme(context),
        appearance: AppearanceMode = getSavedAppearance(context)
    ): ThemePalette {
        val dark = isDarkActive(context, appearance)
        val accent = if (dark) theme.darkAccent else theme.lightAccent
        val accentEnd = if (dark) theme.darkAccentEnd else theme.lightAccentEnd
        val accentSoft = withAlpha(accent, if (dark) 0x36 else 0x22)

        return if (dark) {
            ThemePalette(
                theme = theme,
                isDark = true,
                bgTop = theme.darkBgTop,
                bgMid = theme.darkBgMid,
                bgBottom = theme.darkBgBottom,
                glassSurface = 0x6E121B33.toInt(),
                glassHeroSurface = 0x7E15203C.toInt(),
                glassSurfaceStrong = 0xEE11192E.toInt(),
                glassPill = 0x731C2847.toInt(),
                glassStroke = 0x45FFFFFF,
                glassHeroStroke = 0x66FFFFFF,
                textPrimary = 0xFFF8FAFC.toInt(),
                textSecondary = 0xFFB8C4D9.toInt(),
                textTertiary = 0xFF7D8CA8.toInt(),
                accent = accent,
                accentEnd = accentEnd,
                accentSoft = accentSoft,
                dotFuture = 0x29FFFFFF,
                progressTrack = 0x24FFFFFF,
                warningAmber = 0xFFFBBF24.toInt(),
                urgentCoral = 0xFFFB7185.toInt()
            )
        } else {
            ThemePalette(
                theme = theme,
                isDark = false,
                bgTop = theme.lightBgTop,
                bgMid = theme.lightBgMid,
                bgBottom = theme.lightBgBottom,
                glassSurface = 0x9EFFFFFF.toInt(),
                glassHeroSurface = 0xB5FFFFFF.toInt(),
                glassSurfaceStrong = 0xF5FFFFFF.toInt(),
                glassPill = 0x9EFFFFFF.toInt(),
                glassStroke = 0xCCFFFFFF.toInt(),
                glassHeroStroke = 0xF0FFFFFF.toInt(),
                textPrimary = 0xFF0F172A.toInt(),
                textSecondary = 0xFF475569.toInt(),
                textTertiary = 0xFF8392A8.toInt(),
                accent = accent,
                accentEnd = accentEnd,
                accentSoft = accentSoft,
                dotFuture = 0x240F172A,
                progressTrack = 0x1A0F172A,
                warningAmber = 0xFFD97706.toInt(),
                urgentCoral = 0xFFE11D48.toInt()
            )
        }
    }

    fun applySystemBars(activity: Activity, palette: ThemePalette) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = !palette.isDark
        controller.isAppearanceLightNavigationBars = !palette.isDark
    }

    fun applyAmbientBackground(view: View, palette: ThemePalette, animate: Boolean) {
        val target = intArrayOf(palette.bgTop, palette.bgMid, palette.bgBottom)
        val previous = currentBgColors
        bgAnimator?.cancel()

        if (!animate || previous == null) {
            currentBgColors = target
            view.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, target)
            return
        }

        val evaluator = ArgbEvaluator()
        val start = previous.copyOf()
        bgAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 380L
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                val blended = intArrayOf(
                    evaluator.evaluate(f, start[0], target[0]) as Int,
                    evaluator.evaluate(f, start[1], target[1]) as Int,
                    evaluator.evaluate(f, start[2], target[2]) as Int
                )
                currentBgColors = blended
                view.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, blended)
            }
            start()
        }
    }

    fun performTapHaptic(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    fun performSoftTick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun dp(context: Context, value: Float): Float =
        value * context.resources.displayMetrics.density

    private fun roundMask(radiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(Color.WHITE)
        }

    fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun buildSpecularGlassLayer(
        context: Context,
        fillColor: Int,
        strokeColor: Int,
        isDark: Boolean,
        cornerRadii: FloatArray
    ): LayerDrawable {
        val base = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadii = cornerRadii
            setColor(fillColor)
        }
        val sheenColors = if (isDark) {
            intArrayOf(0x2EFFFFFF, 0x0DFFFFFF, 0x02FFFFFF)
        } else {
            intArrayOf(0x78FFFFFF, 0x24FFFFFF, 0x08FFFFFF)
        }
        val sheen = GradientDrawable(GradientDrawable.Orientation.TL_BR, sheenColors).apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadii = cornerRadii
        }
        val rim = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadii = cornerRadii
            setColor(Color.TRANSPARENT)
            setStroke(dp(context, 1f).toInt().coerceAtLeast(1), strokeColor)
        }
        return LayerDrawable(arrayOf(base, sheen, rim))
    }

    fun cardDrawable(context: Context, palette: ThemePalette, isHero: Boolean = false): Drawable {
        val radius = dp(context, if (isHero) 26f else 22f)
        val radii = FloatArray(8) { radius }
        val fill = if (isHero) palette.glassHeroSurface else palette.glassSurface
        val stroke = if (isHero) palette.glassHeroStroke else palette.glassStroke
        val layer = buildSpecularGlassLayer(context, fill, stroke, palette.isDark, radii)
        return RippleDrawable(ColorStateList.valueOf(palette.accentSoft), layer, roundMask(radius))
    }

    fun pillDrawable(context: Context, palette: ThemePalette): Drawable {
        val radius = dp(context, 999f)
        val radii = FloatArray(8) { radius }
        val layer = buildSpecularGlassLayer(
            context,
            palette.glassPill,
            palette.glassStroke,
            palette.isDark,
            radii
        )
        return RippleDrawable(ColorStateList.valueOf(palette.accentSoft), layer, roundMask(radius))
    }

    fun tagDrawable(context: Context, palette: ThemePalette): Drawable {
        val radius = dp(context, 999f)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(palette.accentSoft)
        }
    }

    fun activeChipDrawable(context: Context, palette: ThemePalette): Drawable {
        val radius = dp(context, 999f)
        val content = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(palette.accent, palette.accentEnd)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
        }
        return RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), content, roundMask(radius))
    }

    fun fabDrawable(context: Context, palette: ThemePalette): Drawable {
        val radius = dp(context, 999f)
        val content = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(palette.accent, palette.accentEnd)
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setStroke(dp(context, 1f).toInt().coerceAtLeast(1), 0x77FFFFFF)
        }
        return RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), content, roundMask(radius))
    }

    fun sheetDrawable(context: Context, palette: ThemePalette): Drawable {
        val r = dp(context, 30f)
        val topRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        return buildSpecularGlassLayer(
            context,
            palette.glassSurfaceStrong,
            palette.glassHeroStroke,
            palette.isDark,
            topRadii
        )
    }

    fun dragHandleDrawable(context: Context, palette: ThemePalette): Drawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(context, 999f)
            setColor(withAlpha(palette.textTertiary, 0x88))
        }
    }

    fun swatchOrbDrawable(context: Context, theme: AppTheme): Drawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(theme.previewStart, theme.previewEnd)
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(context, 1.5f).toInt().coerceAtLeast(1), 0x99FFFFFF.toInt())
        }
    }

    fun themeCardDrawable(context: Context, palette: ThemePalette, selected: Boolean): Drawable {
        val radius = dp(context, 18f)
        val strokeColor = if (selected) palette.accent else palette.glassStroke
        val strokeWidth = dp(context, if (selected) 1.75f else 1f).toInt().coerceAtLeast(1)
        val fillColor = if (selected) palette.accentSoft else palette.glassPill
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fillColor)
            setStroke(strokeWidth, strokeColor)
        }
        return RippleDrawable(ColorStateList.valueOf(palette.accentSoft), content, roundMask(radius))
    }
}
