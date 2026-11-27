package com.lumetime.app.ui

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.lumetime.app.core.FocusTimerEngine
import com.lumetime.app.core.FocusTimerSnapshot
import com.lumetime.app.core.FocusToolTab
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.data.FocusTimerStore
import com.lumetime.app.databinding.DialogFocusTimerBinding
import java.util.Locale

/**
 * Liquid Glass bottom sheet providing a zero-drain Focus Timer (Pomodoro + Custom)
 * and Stopwatch with lap splits persisted across app restarts.
 */
object FocusTimerDialog {

    private data class TimerPreset(val label: String, val durationMillis: Long)

    private val presets = listOf(
        TimerPreset("25m Focus", FocusTimerEngine.DEFAULT_POMODORO_MILLIS),
        TimerPreset("50m Deep", FocusTimerEngine.DEEP_WORK_MILLIS),
        TimerPreset("5m Break", FocusTimerEngine.SHORT_BREAK_MILLIS),
        TimerPreset("15m Rest", FocusTimerEngine.LONG_BREAK_MILLIS)
    )

    fun show(
        context: Context,
        palette: ThemeManager.ThemePalette,
        onStateChanged: () -> Unit = {}
    ) {
        val dialog = GlassSheetHelper.createBottomSheet(context)
        val binding = DialogFocusTimerBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        GlassSheetHelper.configureBottomWindow(dialog, context)

        binding.focusTimerSheetRoot.background = ThemeManager.sheetDrawable(context, palette)
        binding.dragHandle.background = ThemeManager.dragHandleDrawable(context, palette)
        binding.tvTimerSheetTitle.setTextColor(palette.textPrimary)
        binding.btnCloseTimerSheet.background = ThemeManager.pillDrawable(context, palette)
        binding.btnCloseTimerSheet.setColorFilter(palette.textPrimary)
        binding.timerTabContainer.background = ThemeManager.pillDrawable(context, palette)
        binding.timerProgressRing.setStrokeWidthDp(8f)

        var snapshot = FocusTimerStore.load(context)
        var completionCelebrated = false
        val handler = Handler(Looper.getMainLooper())

        fun saveAndNotify() {
            FocusTimerStore.save(context, snapshot)
            onStateChanged()
        }

        fun renderPresets() {
            binding.timerPresetsContainer.removeAllViews()
            val padH = ThemeManager.dp(context, 12f).toInt()
            val height = ThemeManager.dp(context, 34f).toInt()
            val gap = ThemeManager.dp(context, 8f).toInt()

            presets.forEach { preset ->
                val selected = snapshot.timerDurationMillis == preset.durationMillis
                val chip = TextView(context).apply {
                    text = preset.label
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setPadding(padH, 0, padH, 0)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        height
                    ).apply { marginEnd = gap }
                    background = if (selected) {
                        ThemeManager.activeChipDrawable(context, palette)
                    } else {
                        ThemeManager.pillDrawable(context, palette)
                    }
                    setTextColor(if (selected) Color.WHITE else palette.textPrimary)
                    setOnClickListener {
                        ThemeManager.performSoftTick(it)
                        completionCelebrated = false
                        snapshot = FocusTimerEngine.selectPreset(snapshot, preset.durationMillis)
                        saveAndNotify()
                        renderPresets()
                    }
                }
                binding.timerPresetsContainer.addView(chip)
            }
        }

        fun renderLaps() {
            binding.lapsContainer.removeAllViews()
            if (snapshot.lapsMillis.isEmpty()) return
            val padH = ThemeManager.dp(context, 14f).toInt()
            val padV = ThemeManager.dp(context, 9f).toInt()
            val marginBottom = ThemeManager.dp(context, 6f).toInt()

            val total = snapshot.lapsMillis.size
            snapshot.lapsMillis.forEachIndexed { idx, lapTotalMillis ->
                val prevLap = snapshot.lapsMillis.getOrNull(idx + 1) ?: 0L
                val split = (lapTotalMillis - prevLap).coerceAtLeast(0L)
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(padH, padV, padH, padV)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { this.bottomMargin = marginBottom }
                    background = ThemeManager.pillDrawable(context, palette)
                }
                val tvIdx = TextView(context).apply {
                    text = String.format(Locale.US, "Lap %02d", total - idx)
                    textSize = 12f
                    setTextColor(palette.textSecondary)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val tvSplit = TextView(context).apply {
                    text = "+${FocusTimerEngine.formatStopwatch(split)}"
                    textSize = 12f
                    setTextColor(palette.accent)
                    setPadding(0, 0, ThemeManager.dp(context, 12f).toInt(), 0)
                }
                val tvTime = TextView(context).apply {
                    text = FocusTimerEngine.formatStopwatch(lapTotalMillis)
                    textSize = 13f
                    setTextColor(palette.textPrimary)
                }
                row.addView(tvIdx)
                row.addView(tvSplit)
                row.addView(tvTime)
                binding.lapsContainer.addView(row)
            }
        }

        fun renderStaticControls() {
            val isTimerTab = snapshot.activeTab == FocusToolTab.TIMER
            binding.panelFocusTimer.visibility = if (isTimerTab) View.VISIBLE else View.GONE
            binding.panelStopwatch.visibility = if (isTimerTab) View.GONE else View.VISIBLE

            binding.tabFocusTimer.background = if (isTimerTab) {
                ThemeManager.activeChipDrawable(context, palette)
            } else null
            binding.tabFocusTimer.setTextColor(if (isTimerTab) Color.WHITE else palette.textSecondary)

            binding.tabStopwatch.background = if (!isTimerTab) {
                ThemeManager.activeChipDrawable(context, palette)
            } else null
            binding.tabStopwatch.setTextColor(if (!isTimerTab) Color.WHITE else palette.textSecondary)

            binding.btnTimerMinusFive.background = ThemeManager.pillDrawable(context, palette)
            binding.btnTimerMinusFive.setTextColor(palette.textPrimary)
            binding.btnTimerPlusFive.background = ThemeManager.pillDrawable(context, palette)
            binding.btnTimerPlusFive.setTextColor(palette.textPrimary)
            binding.btnTimerReset.background = ThemeManager.pillDrawable(context, palette)
            binding.btnTimerReset.setTextColor(palette.textSecondary)
            binding.btnTimerStartPause.background = ThemeManager.fabDrawable(context, palette)

            binding.btnStopwatchStartPause.background = ThemeManager.fabDrawable(context, palette)
            binding.btnStopwatchLap.background = ThemeManager.pillDrawable(context, palette)
            binding.btnStopwatchLap.setTextColor(palette.accent)
            binding.btnStopwatchReset.background = ThemeManager.pillDrawable(context, palette)
            binding.btnStopwatchReset.setTextColor(palette.textSecondary)

            renderPresets()
            renderLaps()
        }

        fun renderDynamicTick() {
            val now = System.currentTimeMillis()

            if (snapshot.isTimerRunning && FocusTimerEngine.isTimerCompleted(snapshot, now)) {
                snapshot = snapshot.copy(
                    isTimerRunning = false,
                    timerRemainingMillis = 0L,
                    timerTargetEndMillis = 0L
                )
                saveAndNotify()
                if (!completionCelebrated) {
                    completionCelebrated = true
                    ThemeManager.performTapHaptic(binding.root)
                }
            }

            val remaining = FocusTimerEngine.remainingTimerMillis(snapshot, now)
            val progress = FocusTimerEngine.timerProgressFraction(snapshot, now)
            val ringColor = when {
                remaining == 0L -> palette.urgentCoral
                snapshot.isTimerRunning -> palette.accent
                else -> palette.accentEnd
            }

            binding.timerProgressRing.setColors(
                track = palette.progressTrack,
                accentStart = ThemeManager.withAlpha(ringColor, 0xCC),
                accentEnd = ringColor
            )
            binding.timerProgressRing.setProgress(progress)

            binding.tvTimerReadout.text = FocusTimerEngine.formatMmSs(remaining)
            binding.tvTimerReadout.setTextColor(palette.textPrimary)
            binding.tvTimerStatus.text = when {
                remaining == 0L -> "SESSION DONE"
                snapshot.isTimerRunning -> "FOCUSING"
                remaining < snapshot.timerDurationMillis -> "PAUSED"
                else -> "READY"
            }
            binding.tvTimerStatus.setTextColor(ringColor)
            binding.btnTimerStartPause.text = when {
                remaining == 0L -> "Restart"
                snapshot.isTimerRunning -> "Pause"
                remaining < snapshot.timerDurationMillis -> "Resume"
                else -> "Start Focus"
            }

            val swElapsed = FocusTimerEngine.elapsedStopwatchMillis(snapshot, now)
            binding.tvStopwatchReadout.text = FocusTimerEngine.formatStopwatch(swElapsed)
            binding.tvStopwatchReadout.setTextColor(palette.textPrimary)
            binding.btnStopwatchStartPause.text = if (snapshot.isStopwatchRunning) "Pause" else {
                if (swElapsed > 0L) "Resume" else "Start"
            }
        }

        val tickRunnable = object : Runnable {
            override fun run() {
                renderDynamicTick()
                handler.postDelayed(this, 100L)
            }
        }

        binding.btnCloseTimerSheet.setOnClickListener {
            ThemeManager.performSoftTick(it)
            dialog.dismiss()
        }

        binding.tabFocusTimer.setOnClickListener {
            ThemeManager.performSoftTick(it)
            snapshot = snapshot.copy(activeTab = FocusToolTab.TIMER)
            saveAndNotify()
            renderStaticControls()
            renderDynamicTick()
        }

        binding.tabStopwatch.setOnClickListener {
            ThemeManager.performSoftTick(it)
            snapshot = snapshot.copy(activeTab = FocusToolTab.STOPWATCH)
            saveAndNotify()
            renderStaticControls()
            renderDynamicTick()
        }

        binding.btnTimerStartPause.setOnClickListener {
            ThemeManager.performTapHaptic(it)
            completionCelebrated = false
            snapshot = FocusTimerEngine.toggleTimer(snapshot, System.currentTimeMillis())
            saveAndNotify()
            renderDynamicTick()
        }

        binding.btnTimerMinusFive.setOnClickListener {
            ThemeManager.performSoftTick(it)
            completionCelebrated = false
            snapshot = FocusTimerEngine.adjustMinutes(snapshot, -5, System.currentTimeMillis())
            saveAndNotify()
            renderPresets()
            renderDynamicTick()
        }

        binding.btnTimerPlusFive.setOnClickListener {
            ThemeManager.performSoftTick(it)
            completionCelebrated = false
            snapshot = FocusTimerEngine.adjustMinutes(snapshot, 5, System.currentTimeMillis())
            saveAndNotify()
            renderPresets()
            renderDynamicTick()
        }

        binding.btnTimerReset.setOnClickListener {
            ThemeManager.performSoftTick(it)
            completionCelebrated = false
            snapshot = FocusTimerEngine.resetTimer(snapshot)
            saveAndNotify()
            renderDynamicTick()
        }

        binding.btnStopwatchStartPause.setOnClickListener {
            ThemeManager.performTapHaptic(it)
            snapshot = FocusTimerEngine.toggleStopwatch(snapshot, System.currentTimeMillis())
            saveAndNotify()
            renderDynamicTick()
        }

        binding.btnStopwatchLap.setOnClickListener {
            ThemeManager.performSoftTick(it)
            snapshot = FocusTimerEngine.recordLap(snapshot, System.currentTimeMillis())
            saveAndNotify()
            renderLaps()
        }

        binding.btnStopwatchReset.setOnClickListener {
            ThemeManager.performSoftTick(it)
            snapshot = FocusTimerEngine.resetStopwatch(snapshot)
            saveAndNotify()
            renderLaps()
            renderDynamicTick()
        }

        dialog.setOnDismissListener {
            handler.removeCallbacks(tickRunnable)
        }

        renderStaticControls()
        renderDynamicTick()
        handler.post(tickRunnable)
        dialog.show()
    }
}
