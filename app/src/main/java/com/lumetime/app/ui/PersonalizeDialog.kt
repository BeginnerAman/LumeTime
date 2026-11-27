package com.lumetime.app.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.lumetime.app.core.TimerAlertManager
import com.lumetime.app.data.PersonalProfilePrefs
import com.lumetime.app.databinding.DialogPersonalizeBinding
import java.util.Locale

/**
 * Liquid Glass sheet for user personalization: name, greeting, daily focus goal,
 * active waking-day schedule, sound/vibration alerts, and JSON backup access.
 */
object PersonalizeDialog {

    private val focusGoals = listOf(30, 60, 90, 120, 180)

    fun show(
        context: Context,
        palette: ThemeManager.ThemePalette,
        onSaved: () -> Unit,
        onOpenBackup: () -> Unit
    ) {
        val dialog = GlassSheetHelper.createBottomSheet(context)
        val binding = DialogPersonalizeBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        GlassSheetHelper.configureBottomWindow(dialog, context)

        var profile = PersonalProfilePrefs.load(context)

        binding.personalizeSheetRoot.background = ThemeManager.sheetDrawable(context, palette)
        binding.dragHandle.background = ThemeManager.dragHandleDrawable(context, palette)
        binding.tvPersonalizeTitle.setTextColor(palette.textPrimary)
        binding.tvPersonalizeSubtitle.setTextColor(palette.textSecondary)
        binding.btnClosePersonalize.background = ThemeManager.pillDrawable(context, palette)
        binding.btnClosePersonalize.setColorFilter(palette.textPrimary)

        binding.etProfileName.setText(profile.userName)
        binding.etProfileIntention.setText(profile.dailyIntention)

        fun formatHour(h: Int): String {
            val mod = h % 12
            val label = if (mod == 0) 12 else mod
            return "$label:00 ${if (h < 12) "AM" else "PM"}"
        }

        fun render() {
            binding.cardDailyFocusStats.background = ThemeManager.cardDrawable(context, palette, isHero = true)
            binding.tvFocusStatsTag.setTextColor(palette.accent)
            binding.tvFocusStatsHeadline.setTextColor(palette.textPrimary)
            binding.tvFocusStatsHeadline.text = String.format(
                Locale.US,
                "%d / %d min • %d session%s today",
                profile.focusedMinutesToday,
                profile.dailyFocusGoalMinutes,
                profile.focusedSessionsToday,
                if (profile.focusedSessionsToday == 1) "" else "s"
            )
            val goalProgress = (profile.focusedMinutesToday.toFloat() /
                profile.dailyFocusGoalMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            binding.pbFocusGoalToday.setColors(
                track = palette.progressTrack,
                accentStart = palette.accentEnd,
                accentEnd = palette.accent
            )
            binding.pbFocusGoalToday.setProgress(goalProgress)

            binding.etProfileName.background = ThemeManager.pillDrawable(context, palette)
            binding.etProfileName.setTextColor(palette.textPrimary)
            binding.etProfileName.setHintTextColor(palette.textTertiary)

            binding.etProfileIntention.background = ThemeManager.pillDrawable(context, palette)
            binding.etProfileIntention.setTextColor(palette.textPrimary)
            binding.etProfileIntention.setHintTextColor(palette.textTertiary)

            binding.tvFocusGoalLabel.setTextColor(palette.textSecondary)
            binding.focusGoalChipsContainer.removeAllViews()
            val padH = ThemeManager.dp(context, 13f).toInt()
            val height = ThemeManager.dp(context, 34f).toInt()
            val gap = ThemeManager.dp(context, 8f).toInt()
            focusGoals.forEach { goalMins ->
                val active = profile.dailyFocusGoalMinutes == goalMins
                val chip = TextView(context).apply {
                    text = "${goalMins}m"
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(padH, 0, padH, 0)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        height
                    ).apply { marginEnd = gap }
                    background = if (active) {
                        ThemeManager.activeChipDrawable(context, palette)
                    } else {
                        ThemeManager.pillDrawable(context, palette)
                    }
                    setTextColor(if (active) Color.WHITE else palette.textPrimary)
                    setOnClickListener {
                        ThemeManager.performSoftTick(it)
                        profile = profile.copy(dailyFocusGoalMinutes = goalMins)
                        render()
                    }
                }
                binding.focusGoalChipsContainer.addView(chip)
            }

            binding.rowAwakePulseToggle.background = ThemeManager.pillDrawable(context, palette)
            binding.tvAwakePulseLabel.setTextColor(palette.textPrimary)
            binding.tvAwakePulseStatus.text = if (profile.useAwakeDayForTodayPulse) "AWAKE" else "24H"
            binding.tvAwakePulseStatus.setTextColor(
                if (profile.useAwakeDayForTodayPulse) palette.accent else palette.textTertiary
            )

            binding.awakeHoursRow.visibility =
                if (profile.useAwakeDayForTodayPulse) View.VISIBLE else View.GONE
            binding.btnWakeHour.background = ThemeManager.pillDrawable(context, palette)
            binding.btnWakeHour.setTextColor(palette.textPrimary)
            binding.btnWakeHour.text = "Wake: ${formatHour(profile.wakeHour)}"

            binding.btnSleepHour.background = ThemeManager.pillDrawable(context, palette)
            binding.btnSleepHour.setTextColor(palette.textPrimary)
            binding.btnSleepHour.text = "Sleep: ${formatHour(profile.sleepHour)}"

            binding.btnSoundToggle.background = if (profile.soundAlertEnabled) {
                ThemeManager.tintedTagDrawable(context, palette, palette.accent)
            } else {
                ThemeManager.pillDrawable(context, palette)
            }
            binding.btnSoundToggle.setTextColor(
                if (profile.soundAlertEnabled) palette.accent else palette.textSecondary
            )
            binding.btnSoundToggle.text = if (profile.soundAlertEnabled) "Alert Sound: ON" else "Sound: OFF"

            binding.btnVibrationToggle.background = if (profile.vibrationAlertEnabled) {
                ThemeManager.tintedTagDrawable(context, palette, palette.accent)
            } else {
                ThemeManager.pillDrawable(context, palette)
            }
            binding.btnVibrationToggle.setTextColor(
                if (profile.vibrationAlertEnabled) palette.accent else palette.textSecondary
            )
            binding.btnVibrationToggle.text =
                if (profile.vibrationAlertEnabled) "Vibration: ON" else "Vibrate: OFF"

            binding.btnTestAlert.background = ThemeManager.pillDrawable(context, palette)
            binding.btnTestAlert.setTextColor(palette.textSecondary)

            binding.btnOpenBackupSheet.background = ThemeManager.pillDrawable(context, palette)
            binding.btnOpenBackupSheet.setTextColor(palette.accent)
            binding.btnSavePersonalize.background = ThemeManager.fabDrawable(context, palette)
        }

        binding.btnClosePersonalize.setOnClickListener {
            ThemeManager.performSoftTick(it)
            dialog.dismiss()
        }
        binding.rowAwakePulseToggle.setOnClickListener {
            ThemeManager.performSoftTick(it)
            profile = profile.copy(useAwakeDayForTodayPulse = !profile.useAwakeDayForTodayPulse)
            render()
        }
        binding.btnWakeHour.setOnClickListener {
            ThemeManager.performSoftTick(it)
            val nextWake = if (profile.wakeHour >= 11) 5 else profile.wakeHour + 1
            val safeSleep = maxOf(profile.sleepHour, nextWake + 4).coerceAtMost(23)
            profile = profile.copy(wakeHour = nextWake, sleepHour = safeSleep)
            render()
        }
        binding.btnSleepHour.setOnClickListener {
            ThemeManager.performSoftTick(it)
            val nextSleep = if (profile.sleepHour >= 23) maxOf(profile.wakeHour + 8, 19) else profile.sleepHour + 1
            profile = profile.copy(sleepHour = nextSleep.coerceIn(profile.wakeHour + 1, 23))
            render()
        }
        binding.btnSoundToggle.setOnClickListener {
            ThemeManager.performSoftTick(it)
            profile = profile.copy(soundAlertEnabled = !profile.soundAlertEnabled)
            render()
        }
        binding.btnVibrationToggle.setOnClickListener {
            ThemeManager.performSoftTick(it)
            profile = profile.copy(vibrationAlertEnabled = !profile.vibrationAlertEnabled)
            render()
        }
        binding.btnTestAlert.setOnClickListener {
            TimerAlertManager.playAlertWithOptions(
                context,
                soundEnabled = profile.soundAlertEnabled,
                vibrationEnabled = profile.vibrationAlertEnabled
            )
        }
        binding.btnOpenBackupSheet.setOnClickListener {
            ThemeManager.performTapHaptic(it)
            dialog.dismiss()
            onOpenBackup()
        }
        binding.btnSavePersonalize.setOnClickListener {
            ThemeManager.performTapHaptic(it)
            val updated = profile.copy(
                userName = binding.etProfileName.text?.toString()?.trim().orEmpty(),
                dailyIntention = binding.etProfileIntention.text?.toString()?.trim().orEmpty()
            )
            PersonalProfilePrefs.save(context, updated)
            onSaved()
            dialog.dismiss()
        }

        render()
        dialog.show()
    }
}
