package com.lumetime.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.lumetime.app.R
import com.lumetime.app.databinding.DialogGlassThemeBinding
import com.lumetime.app.databinding.ItemThemeOptionBinding

/**
 * Floating translucent glass dialog for choosing LumeTime's theme and Dark/Light/Auto glass mode.
 */
object ThemePickerDialog {

    fun show(
        context: Context,
        onSelectionChanged: (ThemeManager.AppTheme, ThemeManager.AppearanceMode) -> Unit
    ) {
        val dialog = GlassSheetHelper.createBottomSheet(context)
        val binding = DialogGlassThemeBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        GlassSheetHelper.configureBottomWindow(dialog, context)

        var selectedTheme = ThemeManager.getSavedTheme(context)
        var selectedMode = ThemeManager.getSavedAppearance(context)

        fun renderDialog() {
            val palette = ThemeManager.getPalette(context, selectedTheme, selectedMode)
            binding.sheetDragHandle.background = ThemeManager.dragHandleDrawable(context, palette)
            binding.themeSheetRoot.background = ThemeManager.sheetDrawable(context, palette)
            binding.tvThemeTitle.setTextColor(palette.textPrimary)
            binding.tvThemeSubtitle.setTextColor(palette.textSecondary)
            binding.appearanceSwitchContainer.background = ThemeManager.pillDrawable(context, palette)
            binding.btnThemeDone.background = ThemeManager.fabDrawable(context, palette)

            val modeButtons = listOf(
                binding.btnModeAuto to ThemeManager.AppearanceMode.AUTO,
                binding.btnModeDark to ThemeManager.AppearanceMode.DARK,
                binding.btnModeAmoled to ThemeManager.AppearanceMode.AMOLED,
                binding.btnModeLight to ThemeManager.AppearanceMode.LIGHT
            )
            modeButtons.forEach { (btn, mode) ->
                val active = mode == selectedMode
                btn.background = if (active) {
                    ThemeManager.activeChipDrawable(context, palette)
                } else {
                    null
                }
                btn.setTextColor(if (active) Color.WHITE else palette.textSecondary)
                btn.setOnClickListener {
                    ThemeManager.performTapHaptic(it)
                    if (selectedMode != mode) {
                        selectedMode = mode
                        ThemeManager.saveAppearance(context, mode)
                        onSelectionChanged(selectedTheme, selectedMode)
                        renderDialog()
                    }
                }
            }

            binding.themeOptionsContainer.removeAllViews()
            val inflater = LayoutInflater.from(context)
            ThemeManager.AppTheme.entries.forEach { theme ->
                val item = ItemThemeOptionBinding.inflate(
                    inflater,
                    binding.themeOptionsContainer,
                    false
                )
                val isSelected = theme == selectedTheme
                item.themeOptionCard.background =
                    ThemeManager.themeCardDrawable(context, palette, isSelected)
                item.viewSwatchOrb.background = ThemeManager.swatchOrbDrawable(context, theme)
                item.tvThemeName.text = theme.title
                item.tvThemeName.setTextColor(palette.textPrimary)
                item.tvThemeTagline.text = theme.tagline
                item.tvThemeTagline.setTextColor(palette.textSecondary)
                item.ivThemeCheck.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
                item.ivThemeCheck.setColorFilter(palette.accent)

                item.root.setOnClickListener {
                    ThemeManager.performTapHaptic(it)
                    if (selectedTheme != theme) {
                        selectedTheme = theme
                        ThemeManager.saveTheme(context, theme)
                        onSelectionChanged(selectedTheme, selectedMode)
                        renderDialog()
                    }
                }
                binding.themeOptionsContainer.addView(item.root)
            }
        }

        binding.btnThemeDone.setOnClickListener {
            ThemeManager.performTapHaptic(it)
            dialog.dismiss()
        }

        renderDialog()
        dialog.show()
    }
}
