package com.lumetime.app.ui

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.lumetime.app.core.DateCalculatorEngine
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.databinding.DialogDateCalculatorBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Liquid Glass bottom sheet for calculating exact Date/Age differences,
 * weekdays, and +/- day offsets with one-tap Countdown creation.
 */
object DateCalculatorDialog {

    private val quickOffsets = listOf(7, 30, 90, 100, 180, 365, -30)

    fun show(
        context: Context,
        palette: ThemeManager.ThemePalette,
        onCreateCountdownAtDate: (targetMillis: Long, suggestedTitle: String) -> Unit
    ) {
        val dialog = GlassSheetHelper.createBottomSheet(context)
        val binding = DialogDateCalculatorBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        GlassSheetHelper.configureBottomWindow(dialog, context)

        binding.dateCalcSheetRoot.background = ThemeManager.sheetDrawable(context, palette)
        binding.dragHandle.background = ThemeManager.dragHandleDrawable(context, palette)
        binding.tvDateCalcTitle.setTextColor(palette.textPrimary)
        binding.btnCloseDateCalc.background = ThemeManager.pillDrawable(context, palette)
        binding.btnCloseDateCalc.setColorFilter(palette.textPrimary)
        binding.calcTabContainer.background = ThemeManager.pillDrawable(context, palette)

        val displayDateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val fullResultFmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())

        var showDiffTab = true
        val startCal = Calendar.getInstance()
        val endCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 30) }
        val offsetBaseCal = Calendar.getInstance()
        var offsetDays = 90

        fun pickDate(cal: Calendar, onPicked: () -> Unit) {
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    cal.set(Calendar.YEAR, y)
                    cal.set(Calendar.MONTH, m)
                    cal.set(Calendar.DAY_OF_MONTH, d)
                    onPicked()
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        fun renderOffsetPresets() {
            binding.offsetPresetsContainer.removeAllViews()
            val padH = ThemeManager.dp(context, 12f).toInt()
            val height = ThemeManager.dp(context, 32f).toInt()
            val gap = ThemeManager.dp(context, 8f).toInt()
            quickOffsets.forEach { days ->
                val selected = offsetDays == days
                val label = if (days > 0) "+${days}d" else "${days}d"
                val chip = TextView(context).apply {
                    text = label
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
                        offsetDays = days
                        binding.etDaysOffset.setText(days.toString())
                    }
                }
                binding.offsetPresetsContainer.addView(chip)
            }
        }

        fun render() {
            binding.panelDateDiff.visibility = if (showDiffTab) View.VISIBLE else View.GONE
            binding.panelAddDays.visibility = if (showDiffTab) View.GONE else View.VISIBLE

            binding.tabDateDiff.background = if (showDiffTab) {
                ThemeManager.activeChipDrawable(context, palette)
            } else null
            binding.tabDateDiff.setTextColor(if (showDiffTab) Color.WHITE else palette.textSecondary)

            binding.tabAddDays.background = if (!showDiffTab) {
                ThemeManager.activeChipDrawable(context, palette)
            } else null
            binding.tabAddDays.setTextColor(if (!showDiffTab) Color.WHITE else palette.textSecondary)

            // Diff Panel
            binding.btnPickStartDate.background = ThemeManager.pillDrawable(context, palette)
            binding.btnPickStartDate.setTextColor(palette.textPrimary)
            binding.btnPickStartDate.text = "From: ${displayDateFmt.format(startCal.time)}"

            binding.btnPickEndDate.background = ThemeManager.pillDrawable(context, palette)
            binding.btnPickEndDate.setTextColor(palette.textPrimary)
            binding.btnPickEndDate.text = "To: ${displayDateFmt.format(endCal.time)}"

            binding.btnSwapDates.background = ThemeManager.pillDrawable(context, palette)
            binding.btnSwapDates.setColorFilter(palette.accent)

            val diff = DateCalculatorEngine.computeDateDifference(
                startCal.timeInMillis,
                endCal.timeInMillis
            )
            binding.cardDiffResult.background = ThemeManager.cardDrawable(context, palette, isHero = true)
            binding.tvDiffLabel.setTextColor(palette.accent)
            binding.tvDiffLabel.text = if (diff.startEarlierOrEqual) "SPAN BREAKDOWN" else "REVERSE SPAN"
            binding.tvDiffPrimary.setTextColor(palette.textPrimary)

            val spanParts = buildList {
                if (diff.years > 0) add("${diff.years}y")
                if (diff.months > 0 || diff.years > 0) add("${diff.months}m")
                add("${diff.days}d")
            }.joinToString(" ")
            binding.tvDiffPrimary.text = "$spanParts  •  ${String.format(Locale.US, "%,d days", diff.totalDays)}"

            binding.tvDiffBreakdown.setTextColor(palette.textSecondary)
            binding.tvDiffBreakdown.text = String.format(
                Locale.US,
                "%dw %dd  •  %,d weekdays (Mon–Fri)  •  %,d hrs",
                diff.weeks,
                diff.remainingDaysAfterWeeks,
                diff.weekdaysCount,
                diff.totalHours
            )

            binding.btnSaveDiffAsCountdown.background = ThemeManager.fabDrawable(context, palette)

            // Add Days Panel
            binding.btnPickOffsetBaseDate.background = ThemeManager.pillDrawable(context, palette)
            binding.btnPickOffsetBaseDate.setTextColor(palette.textPrimary)
            binding.btnPickOffsetBaseDate.text = "Start Date: ${displayDateFmt.format(offsetBaseCal.time)}"

            binding.etDaysOffset.background = ThemeManager.pillDrawable(context, palette)
            binding.etDaysOffset.setTextColor(palette.textPrimary)
            binding.etDaysOffset.setHintTextColor(palette.textTertiary)

            val resultMillis = DateCalculatorEngine.addDays(offsetBaseCal.timeInMillis, offsetDays)
            val resultCal = Calendar.getInstance().apply { timeInMillis = resultMillis }
            binding.cardAddResult.background = ThemeManager.cardDrawable(context, palette, isHero = true)
            binding.tvAddResultCaption.setTextColor(palette.accent)
            binding.tvAddResultDate.setTextColor(palette.textPrimary)
            binding.tvAddResultDate.text = fullResultFmt.format(Date(resultMillis))
            binding.tvAddResultSub.setTextColor(palette.textSecondary)
            val weekOfYear = resultCal.get(Calendar.WEEK_OF_YEAR)
            val dayOfYear = resultCal.get(Calendar.DAY_OF_YEAR)
            val signText = if (offsetDays >= 0) "+$offsetDays" else "$offsetDays"
            binding.tvAddResultSub.text = String.format(
                Locale.US,
                "%s days from base  •  Week %d  •  Day %d of %d",
                signText,
                weekOfYear,
                dayOfYear,
                resultCal.getActualMaximum(Calendar.DAY_OF_YEAR)
            )
            binding.btnSaveOffsetAsCountdown.background = ThemeManager.fabDrawable(context, palette)
            renderOffsetPresets()
        }

        binding.etDaysOffset.setText(offsetDays.toString())
        binding.etDaysOffset.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val parsed = s?.toString()?.trim()?.toIntOrNull()
                if (parsed != null && parsed.coerceIn(-36500, 36500) != offsetDays) {
                    offsetDays = parsed.coerceIn(-36500, 36500)
                    render()
                }
            }
        })

        binding.btnCloseDateCalc.setOnClickListener {
            ThemeManager.performSoftTick(it)
            dialog.dismiss()
        }
        binding.tabDateDiff.setOnClickListener {
            ThemeManager.performSoftTick(it)
            showDiffTab = true
            render()
        }
        binding.tabAddDays.setOnClickListener {
            ThemeManager.performSoftTick(it)
            showDiffTab = false
            render()
        }
        binding.btnPickStartDate.setOnClickListener {
            ThemeManager.performSoftTick(it)
            pickDate(startCal) { render() }
        }
        binding.btnPickEndDate.setOnClickListener {
            ThemeManager.performSoftTick(it)
            pickDate(endCal) { render() }
        }
        binding.btnSwapDates.setOnClickListener {
            ThemeManager.performSoftTick(it)
            val tmp = startCal.timeInMillis
            startCal.timeInMillis = endCal.timeInMillis
            endCal.timeInMillis = tmp
            render()
        }
        binding.btnPickOffsetBaseDate.setOnClickListener {
            ThemeManager.performSoftTick(it)
            pickDate(offsetBaseCal) { render() }
        }
        binding.btnSaveDiffAsCountdown.setOnClickListener {
            ThemeManager.performTapHaptic(it)
            val targetCal = (endCal.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            dialog.dismiss()
            onCreateCountdownAtDate(
                targetCal.timeInMillis,
                "Target (${displayDateFmt.format(targetCal.time)})"
            )
        }
        binding.btnSaveOffsetAsCountdown.setOnClickListener {
            ThemeManager.performTapHaptic(it)
            val resultMillis = DateCalculatorEngine.addDays(offsetBaseCal.timeInMillis, offsetDays) +
                9L * TimeCalculator.MILLIS_PER_HOUR
            dialog.dismiss()
            val label = if (offsetDays >= 0) "${offsetDays}-Day Milestone" else "Saved Date"
            onCreateCountdownAtDate(resultMillis, label)
        }

        render()
        dialog.show()
    }
}
