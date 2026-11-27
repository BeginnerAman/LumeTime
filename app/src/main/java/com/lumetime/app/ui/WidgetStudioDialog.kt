package com.lumetime.app.ui

import android.app.Dialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import com.lumetime.app.R
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.databinding.DialogWidgetStudioBinding
import com.lumetime.app.widget.HeroWidgetProvider
import com.lumetime.app.widget.ListWidgetProvider
import com.lumetime.app.widget.WidgetPrefs
import com.lumetime.app.widget.WidgetScheduler
import com.lumetime.app.widget.YearWidgetProvider
import java.util.Locale

/**
 * In-App Widget Studio for customizing Home Screen Glass Widgets and 1-tap pinning.
 */
object WidgetStudioDialog {

    fun show(context: Context, palette: ThemeManager.ThemePalette) {
        val dialog = Dialog(context, R.style.Theme_LumeTime_GlassDialog)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogWidgetStudioBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        val width = (context.resources.displayMetrics.widthPixels * 0.92f).toInt()
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        var selectedStyle = WidgetPrefs.getGlassStyle(context)
        var selectedOpacity = WidgetPrefs.getOpacity(context)
        var liveChrono = WidgetPrefs.isLiveChronometerEnabled(context)

        val yearProgress = TimeCalculator.computeYearProgress(System.currentTimeMillis())

        fun renderStudio() {
            binding.widgetStudioRoot.background = ThemeManager.sheetDrawable(context, palette)
            binding.tvStudioTitle.setTextColor(palette.textPrimary)
            binding.tvStudioSubtitle.setTextColor(palette.textSecondary)
            binding.tvStyleLabel.setTextColor(palette.textSecondary)
            binding.tvOpacityLabel.setTextColor(palette.textSecondary)
            binding.tvPinSectionLabel.setTextColor(palette.textSecondary)

            // Preview wallpaper backdrop
            binding.studioPreviewWallpaper.background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(palette.theme.previewStart, palette.theme.previewEnd)
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = ThemeManager.dp(context, 18f)
            }
            binding.studioPreviewCard.setBackgroundResource(
                WidgetPrefs.resolveBackgroundRes(selectedStyle, selectedOpacity)
            )
            val isDarkWidget = selectedStyle == WidgetPrefs.WidgetGlassStyle.DARK
            binding.tvPreviewTag.setTextColor(
                if (isDarkWidget) 0xFF67E8F9.toInt() else 0xFF0284C7.toInt()
            )
            binding.tvPreviewDays.text =
                String.format(Locale.US, "%d days left", yearProgress.daysLeft)
            binding.tvPreviewDays.setTextColor(
                if (isDarkWidget) 0xFFF8FAFC.toInt() else 0xFF0F172A.toInt()
            )
            binding.tvPreviewSub.text = String.format(
                Locale.US,
                "%,d hrs • %.1f%% of %d",
                yearProgress.hoursLeft,
                yearProgress.percentCompleted,
                yearProgress.year
            )
            binding.tvPreviewSub.setTextColor(
                if (isDarkWidget) 0xFFB8C4D9.toInt() else 0xFF475569.toInt()
            )

            // Style pills
            binding.styleSwitchContainer.background = ThemeManager.pillDrawable(context, palette)
            listOf(
                binding.btnStyleDark to WidgetPrefs.WidgetGlassStyle.DARK,
                binding.btnStyleFrost to WidgetPrefs.WidgetGlassStyle.FROST
            ).forEach { (btn, style) ->
                val active = style == selectedStyle
                btn.background = if (active) ThemeManager.activeChipDrawable(context, palette) else null
                btn.setTextColor(if (active) Color.WHITE else palette.textSecondary)
                btn.setOnClickListener {
                    ThemeManager.performSoftTick(it)
                    selectedStyle = style
                    WidgetPrefs.setGlassStyle(context, style)
                    WidgetScheduler.refreshAllWidgets(context)
                    renderStudio()
                }
            }

            // Opacity pills
            binding.opacitySwitchContainer.background = ThemeManager.pillDrawable(context, palette)
            listOf(
                binding.btnOpacityFrosted to WidgetPrefs.WidgetOpacity.FROSTED,
                binding.btnOpacityBalanced to WidgetPrefs.WidgetOpacity.BALANCED,
                binding.btnOpacityClear to WidgetPrefs.WidgetOpacity.CLEAR
            ).forEach { (btn, opacity) ->
                val active = opacity == selectedOpacity
                btn.background = if (active) ThemeManager.activeChipDrawable(context, palette) else null
                btn.setTextColor(if (active) Color.WHITE else palette.textSecondary)
                btn.setOnClickListener {
                    ThemeManager.performSoftTick(it)
                    selectedOpacity = opacity
                    WidgetPrefs.setOpacity(context, opacity)
                    WidgetScheduler.refreshAllWidgets(context)
                    renderStudio()
                }
            }

            // Chronometer row
            binding.rowChronoToggle.background = ThemeManager.pillDrawable(context, palette)
            binding.tvChronoToggleLabel.setTextColor(palette.textPrimary)
            binding.tvChronoStatus.text = if (liveChrono) "ON" else "OFF"
            binding.tvChronoStatus.setTextColor(if (liveChrono) palette.accent else palette.textTertiary)

            // Pin buttons
            listOf(
                binding.btnPinYearWidget to YearWidgetProvider::class.java,
                binding.btnPinHeroWidget to HeroWidgetProvider::class.java,
                binding.btnPinListWidget to ListWidgetProvider::class.java
            ).forEach { (btn, providerClass) ->
                btn.background = ThemeManager.pillDrawable(context, palette)
                btn.setTextColor(palette.textPrimary)
                btn.setOnClickListener {
                    ThemeManager.performTapHaptic(it)
                    requestPin(context, providerClass)
                }
            }

            binding.btnStudioDone.background = ThemeManager.fabDrawable(context, palette)
        }

        binding.rowChronoToggle.setOnClickListener {
            ThemeManager.performSoftTick(it)
            liveChrono = !liveChrono
            WidgetPrefs.setLiveChronometerEnabled(context, liveChrono)
            WidgetScheduler.refreshAllWidgets(context)
            renderStudio()
        }

        binding.btnStudioDone.setOnClickListener {
            ThemeManager.performTapHaptic(it)
            dialog.dismiss()
        }

        renderStudio()
        dialog.show()
    }

    private fun requestPin(context: Context, providerClass: Class<*>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(
                context,
                context.getString(R.string.widget_pin_fallback),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) {
            Toast.makeText(
                context,
                context.getString(R.string.widget_pin_fallback),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val provider = ComponentName(context, providerClass)
        manager.requestPinAppWidget(provider, null, null)
    }
}
