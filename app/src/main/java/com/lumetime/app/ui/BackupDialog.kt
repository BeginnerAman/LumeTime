package com.lumetime.app.ui

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import com.lumetime.app.BuildConfig
import com.lumetime.app.R
import com.lumetime.app.databinding.DialogGlassBackupBinding

/**
 * Floating Glass dialog for zero-permission SAF JSON Backup & Restore.
 */
object BackupDialog {

    fun show(
        context: Context,
        palette: ThemeManager.ThemePalette,
        countdownCount: Int,
        onExportClick: () -> Unit,
        onRestoreClick: () -> Unit
    ) {
        val dialog = GlassSheetHelper.createBottomSheet(context)
        val binding = DialogGlassBackupBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        GlassSheetHelper.configureBottomWindow(dialog, context)

        binding.sheetDragHandle.background = ThemeManager.dragHandleDrawable(context, palette)
        binding.backupSheetRoot.background = ThemeManager.sheetDrawable(context, palette)
        binding.tvBackupTitle.setTextColor(palette.textPrimary)
        binding.tvBackupSubtitle.setTextColor(palette.textSecondary)

        binding.tvBackupStats.background = ThemeManager.cardDrawable(context, palette)
        binding.tvBackupStats.setTextColor(palette.textPrimary)
        binding.tvBackupStats.text = "$countdownCount saved countdowns • 100% offline local storage"

        binding.btnExportBackup.background = ThemeManager.fabDrawable(context, palette)
        binding.btnRestoreBackup.background = ThemeManager.pillDrawable(context, palette)
        binding.btnRestoreBackup.setTextColor(palette.textPrimary)

        binding.tvAboutVersion.setTextColor(palette.textTertiary)
        binding.tvAboutVersion.text = "LumeTime v${BuildConfig.VERSION_NAME} • Zero-Drain Engine"

        binding.btnExportBackup.setOnClickListener {
            ThemeManager.performTapHaptic(it)
            dialog.dismiss()
            onExportClick()
        }

        binding.btnRestoreBackup.setOnClickListener {
            ThemeManager.performTapHaptic(it)
            dialog.dismiss()
            onRestoreClick()
        }

        dialog.show()
    }
}
