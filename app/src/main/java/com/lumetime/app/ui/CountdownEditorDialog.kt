package com.lumetime.app.ui

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.lumetime.app.R
import com.lumetime.app.core.PrecisionClockTicker
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.databinding.DialogCountdownEditorBinding
import com.lumetime.app.model.ChecklistStep
import com.lumetime.app.model.CountdownCategory
import com.lumetime.app.model.CountdownIcon
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.CountdownMode
import com.lumetime.app.model.RepeatRule
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * Floating Liquid Glass dialog for creating or editing countdowns & count-up milestones.
 */
object CountdownEditorDialog {

    private data class QuickTemplate(
        val label: String,
        val title: String,
        val iconId: String,
        val category: CountdownCategory,
        val mode: CountdownMode,
        val repeatRule: RepeatRule,
        val offsetDays: Int
    )

    private val quickTemplates = listOf(
        QuickTemplate("New Year", "New Year", CountdownIcon.SPARKLE.id, CountdownCategory.GENERAL, CountdownMode.COUNTDOWN, RepeatRule.YEARLY, 0),
        QuickTemplate("Exam", "Exam Day", CountdownIcon.BOOK.id, CountdownCategory.EXAM, CountdownMode.COUNTDOWN, RepeatRule.NONE, 30),
        QuickTemplate("Birthday", "Birthday", CountdownIcon.CAKE.id, CountdownCategory.BIRTHDAY, CountdownMode.COUNTDOWN, RepeatRule.YEARLY, 45),
        QuickTemplate("Trip", "Dream Trip", CountdownIcon.PLANE.id, CountdownCategory.TRAVEL, CountdownMode.COUNTDOWN, RepeatRule.NONE, 14),
        QuickTemplate("30d Goal", "30-Day Goal", CountdownIcon.TARGET.id, CountdownCategory.GOAL, CountdownMode.COUNTDOWN, RepeatRule.NONE, 30),
        QuickTemplate("Streak", "Habit Streak", CountdownIcon.FLAME.id, CountdownCategory.MILESTONE, CountdownMode.COUNT_UP, RepeatRule.NONE, 0)
    )

    private val accentHexChoices = listOf(
        "#67E8F9",
        "#A78BFA",
        "#34D399",
        "#FB7185",
        "#FBBF24",
        "#60A5FA"
    )

    fun show(
        context: Context,
        palette: ThemeManager.ThemePalette,
        existing: CountdownItem? = null,
        onSave: (CountdownItem) -> Unit,
        onDelete: ((CountdownItem) -> Unit)? = null,
        onRequestNotificationPermission: (() -> Unit)? = null,
        prefillTargetMillis: Long? = null,
        prefillTitle: String? = null,
        onResetStreak: ((CountdownItem) -> Unit)? = null
    ) {
        val dialog = GlassSheetHelper.createBottomSheet(context)
        val binding = DialogCountdownEditorBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        GlassSheetHelper.configureBottomWindow(dialog, context)

        val now = System.currentTimeMillis()
        val initialTarget = existing?.targetEpochMillis
            ?: prefillTargetMillis
            ?: (now + 7L * TimeCalculator.MILLIS_PER_DAY)
        val selectedCal = Calendar.getInstance().apply {
            timeInMillis = initialTarget
            if (existing == null && prefillTargetMillis == null) {
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }

        var mode = existing?.mode ?: CountdownMode.COUNTDOWN
        var repeatRule = existing?.repeatRule ?: RepeatRule.NONE
        var category = existing?.category ?: CountdownCategory.GENERAL
        var selectedIconId = existing?.resolveIcon()?.id ?: CountdownIcon.forCategory(category).id
        var accentHex = existing?.accentHex ?: accentHexChoices.first()
        var isPinned = existing?.isPinned ?: false
        var reminderEnabled = existing?.reminderEnabled ?: false
        val checklistSteps = existing?.checklist?.toMutableList() ?: mutableListOf()

        binding.tvEditorTitle.text = context.getString(
            if (existing == null) R.string.add_countdown else R.string.edit_countdown
        )
        binding.etTitle.setText(existing?.title ?: prefillTitle.orEmpty())
        binding.etNote.setText(existing?.note ?: "")
        binding.templatesSection.visibility = if (existing == null) View.VISIBLE else View.GONE

        if (existing != null && onDelete != null) {
            binding.btnDeleteItem.visibility = View.VISIBLE
            binding.btnDeleteItem.background = ThemeManager.pillDrawable(context, palette)
            binding.btnDeleteItem.setTextColor(palette.urgentCoral)
            binding.btnDeleteItem.setOnClickListener {
                ThemeManager.performTapHaptic(it)
                onDelete(existing)
                dialog.dismiss()
            }
        }

        val dateFmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat(
            if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a",
            Locale.getDefault()
        )

        fun renderEditor() {
            binding.sheetDragHandle.background = ThemeManager.dragHandleDrawable(context, palette)
            binding.editorSheetRoot.background = ThemeManager.sheetDrawable(context, palette)
            binding.tvEditorTitle.setTextColor(palette.textPrimary)
            binding.tvTemplatesLabel.setTextColor(palette.textSecondary)

            binding.etTitle.background = ThemeManager.pillDrawable(context, palette)
            binding.etTitle.setTextColor(palette.textPrimary)
            binding.etTitle.setHintTextColor(palette.textTertiary)

            binding.etNote.background = ThemeManager.pillDrawable(context, palette)
            binding.etNote.setTextColor(palette.textPrimary)
            binding.etNote.setHintTextColor(palette.textTertiary)

            binding.tvChecklistSectionLabel.setTextColor(palette.textSecondary)
            binding.etAddStep.background = ThemeManager.pillDrawable(context, palette)
            binding.etAddStep.setTextColor(palette.textPrimary)
            binding.etAddStep.setHintTextColor(palette.textTertiary)
            binding.btnAddStep.background = ThemeManager.pillDrawable(context, palette)
            binding.btnAddStep.setTextColor(palette.accent)

            binding.checklistStepsContainer.removeAllViews()
            checklistSteps.forEachIndexed { idx, step ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(
                        ThemeManager.dp(context, 12f).toInt(),
                        ThemeManager.dp(context, 7f).toInt(),
                        ThemeManager.dp(context, 10f).toInt(),
                        ThemeManager.dp(context, 7f).toInt()
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = ThemeManager.dp(context, 5f).toInt() }
                    background = ThemeManager.pillDrawable(context, palette)
                }
                val tvText = TextView(context).apply {
                    text = if (step.done) "✓ ${step.text}" else "• ${step.text}"
                    textSize = 12f
                    setTextColor(if (step.done) palette.accent else palette.textPrimary)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        ThemeManager.performSoftTick(it)
                        checklistSteps[idx] = step.copy(done = !step.done)
                        renderEditor()
                    }
                }
                val btnRemove = TextView(context).apply {
                    text = "Remove"
                    textSize = 11f
                    setTextColor(palette.urgentCoral)
                    setOnClickListener {
                        ThemeManager.performSoftTick(it)
                        checklistSteps.removeAt(idx)
                        renderEditor()
                    }
                }
                row.addView(tvText)
                row.addView(btnRemove)
                binding.checklistStepsContainer.addView(row)
            }

            binding.modeSwitchContainer.background = ThemeManager.pillDrawable(context, palette)
            val modeBtns = listOf(
                binding.btnModeCountdown to CountdownMode.COUNTDOWN,
                binding.btnModeCountUp to CountdownMode.COUNT_UP
            )
            modeBtns.forEach { (btn, targetMode) ->
                val active = mode == targetMode
                btn.background = if (active) ThemeManager.activeChipDrawable(context, palette) else null
                btn.setTextColor(if (active) Color.WHITE else palette.textSecondary)
                btn.setOnClickListener {
                    ThemeManager.performSoftTick(it)
                    if (mode != targetMode) {
                        mode = targetMode
                        if (mode == CountdownMode.COUNT_UP) {
                            repeatRule = RepeatRule.NONE
                            if (selectedCal.timeInMillis > System.currentTimeMillis()) {
                                selectedCal.timeInMillis = System.currentTimeMillis()
                            }
                        }
                        renderEditor()
                    }
                }
            }

            binding.btnPickDate.background = ThemeManager.pillDrawable(context, palette)
            binding.btnPickDate.setTextColor(palette.textPrimary)
            binding.btnPickDate.text = dateFmt.format(selectedCal.time)

            binding.btnPickTime.background = ThemeManager.pillDrawable(context, palette)
            binding.btnPickTime.setTextColor(palette.textPrimary)
            binding.btnPickTime.text = timeFmt.format(selectedCal.time)

            // Repeat row hidden in Count-Up mode
            binding.repeatContainer.visibility =
                if (mode == CountdownMode.COUNTDOWN) View.VISIBLE else View.GONE
            binding.repeatContainer.background = ThemeManager.pillDrawable(context, palette)
            listOf(
                binding.btnRepeatNone to RepeatRule.NONE,
                binding.btnRepeatMonthly to RepeatRule.MONTHLY,
                binding.btnRepeatYearly to RepeatRule.YEARLY
            ).forEach { (btn, rule) ->
                val active = repeatRule == rule
                btn.background = if (active) ThemeManager.activeChipDrawable(context, palette) else null
                btn.setTextColor(if (active) Color.WHITE else palette.textSecondary)
                btn.setOnClickListener {
                    ThemeManager.performSoftTick(it)
                    repeatRule = rule
                    renderEditor()
                }
            }

            // Vector Icon chips (zero raw emojis)
            binding.emojiContainer.removeAllViews()
            val iconPillSize = ThemeManager.dp(context, 40f).toInt()
            val iconGlyphSize = ThemeManager.dp(context, 20f).toInt()
            val iconMargin = ThemeManager.dp(context, 8f).toInt()
            CountdownIcon.entries.forEach { icon ->
                val active = icon.id == selectedIconId
                val pill = FrameLayout(context).apply {
                    layoutParams = LinearLayout.LayoutParams(iconPillSize, iconPillSize).apply {
                        marginEnd = iconMargin
                    }
                    background = if (active) {
                        ThemeManager.activeChipDrawable(context, palette)
                    } else {
                        ThemeManager.pillDrawable(context, palette)
                    }
                    val iv = ImageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(iconGlyphSize, iconGlyphSize, Gravity.CENTER)
                        setImageResource(icon.drawableRes)
                        setColorFilter(if (active) Color.WHITE else palette.textPrimary)
                    }
                    addView(iv)
                    setOnClickListener {
                        ThemeManager.performSoftTick(it)
                        selectedIconId = icon.id
                        renderEditor()
                    }
                }
                binding.emojiContainer.addView(pill)
            }

            // Category chips
            binding.categoryContainer.removeAllViews()
            CountdownCategory.entries.forEach { cat ->
                val active = cat == category
                val chip = makeChip(context, cat.label, active, palette) {
                    category = cat
                    if (existing == null) selectedIconId = CountdownIcon.forCategory(cat).id
                    renderEditor()
                }
                binding.categoryContainer.addView(chip)
            }

            // Accent Swatches + Live Preview
            val activeAccentInt = runCatching { Color.parseColor(accentHex) }.getOrDefault(palette.accent)
            binding.tvAccentLabel.setTextColor(palette.textSecondary)
            binding.tvAccentPreview.background = ThemeManager.tintedTagDrawable(
                context,
                palette,
                activeAccentInt
            )
            binding.tvAccentPreview.setTextColor(activeAccentInt)

            binding.accentSwatchContainer.removeAllViews()
            val orbSize = ThemeManager.dp(context, 30f).toInt()
            val orbMargin = ThemeManager.dp(context, 10f).toInt()
            accentHexChoices.forEach { hex ->
                val selected = hex.equals(accentHex, ignoreCase = true)
                val orb = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(orbSize, orbSize).apply {
                        marginEnd = orbMargin
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor(hex))
                        setStroke(
                            ThemeManager.dp(context, if (selected) 3f else 1f).toInt().coerceAtLeast(1),
                            if (selected) palette.textPrimary else palette.glassStroke
                        )
                    }
                    scaleX = if (selected) 1.15f else 1f
                    scaleY = if (selected) 1.15f else 1f
                    setOnClickListener {
                        ThemeManager.performSoftTick(it)
                        accentHex = hex
                        renderEditor()
                    }
                }
                binding.accentSwatchContainer.addView(orb)
            }

            // Pin row
            binding.rowPinHero.background = ThemeManager.pillDrawable(context, palette)
            binding.ivPinToggleIcon.setColorFilter(if (isPinned) palette.accent else palette.textTertiary)
            binding.tvPinToggleLabel.setTextColor(palette.textPrimary)
            binding.tvPinStatusBadge.text = if (isPinned) "ON" else "OFF"
            binding.tvPinStatusBadge.setTextColor(if (isPinned) palette.accent else palette.textTertiary)

            // Reminder row (Countdown mode only)
            binding.rowReminderToggle.visibility =
                if (mode == CountdownMode.COUNTDOWN) View.VISIBLE else View.GONE
            binding.rowReminderToggle.background = ThemeManager.pillDrawable(context, palette)
            binding.ivReminderToggleIcon.setColorFilter(
                if (reminderEnabled) palette.accent else palette.textTertiary
            )
            binding.tvReminderToggleLabel.setTextColor(palette.textPrimary)
            binding.tvReminderStatusBadge.text = if (reminderEnabled) "ON" else "OFF"
            binding.tvReminderStatusBadge.setTextColor(
                if (reminderEnabled) palette.accent else palette.textTertiary
            )

            val showResetStreak = existing != null &&
                mode == CountdownMode.COUNT_UP &&
                onResetStreak != null
            binding.btnResetStreak.visibility = if (showResetStreak) View.VISIBLE else View.GONE
            binding.btnResetStreak.background = ThemeManager.pillDrawable(context, palette)
            binding.btnResetStreak.setTextColor(palette.warningAmber)

            binding.btnSaveCountdown.background = ThemeManager.fabDrawable(context, palette)
        }

        // Quick templates
        if (existing == null) {
            binding.templatesContainer.removeAllViews()
            quickTemplates.forEach { tpl ->
                val chip = makeChip(context, tpl.label, false, palette) {
                    if (tpl.title == "New Year") {
                        val nextYear = Calendar.getInstance().get(Calendar.YEAR) + 1
                        binding.etTitle.setText("New Year $nextYear")
                        selectedCal.clear()
                        selectedCal.set(nextYear, Calendar.JANUARY, 1, 0, 0, 0)
                    } else {
                        binding.etTitle.setText(tpl.title)
                        selectedCal.timeInMillis =
                            System.currentTimeMillis() + tpl.offsetDays.toLong() * TimeCalculator.MILLIS_PER_DAY
                        if (tpl.mode == CountdownMode.COUNTDOWN) {
                            selectedCal.set(Calendar.HOUR_OF_DAY, 9)
                            selectedCal.set(Calendar.MINUTE, 0)
                            selectedCal.set(Calendar.SECOND, 0)
                            selectedCal.set(Calendar.MILLISECOND, 0)
                        } else {
                            selectedCal.set(Calendar.MILLISECOND, 0)
                        }
                    }
                    selectedIconId = tpl.iconId
                    category = tpl.category
                    mode = tpl.mode
                    repeatRule = tpl.repeatRule
                    renderEditor()
                }
                binding.templatesContainer.addView(chip)
            }
        }

        binding.btnPickDate.setOnClickListener {
            ThemeManager.performSoftTick(it)
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    selectedCal.set(Calendar.YEAR, y)
                    selectedCal.set(Calendar.MONTH, m)
                    selectedCal.set(Calendar.DAY_OF_MONTH, d)
                    renderEditor()
                },
                selectedCal.get(Calendar.YEAR),
                selectedCal.get(Calendar.MONTH),
                selectedCal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.btnPickTime.setOnClickListener {
            ThemeManager.performSoftTick(it)
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    selectedCal.set(Calendar.HOUR_OF_DAY, hour)
                    selectedCal.set(Calendar.MINUTE, minute)
                    selectedCal.set(Calendar.SECOND, 0)
                    selectedCal.set(Calendar.MILLISECOND, 0)
                    renderEditor()
                },
                selectedCal.get(Calendar.HOUR_OF_DAY),
                selectedCal.get(Calendar.MINUTE),
                DateFormat.is24HourFormat(context)
            ).show()
        }

        binding.rowPinHero.setOnClickListener {
            ThemeManager.performSoftTick(it)
            isPinned = !isPinned
            renderEditor()
        }

        binding.rowReminderToggle.setOnClickListener {
            ThemeManager.performSoftTick(it)
            reminderEnabled = !reminderEnabled
            if (reminderEnabled) {
                onRequestNotificationPermission?.invoke()
            }
            renderEditor()
        }

        binding.btnResetStreak.setOnClickListener {
            val targetItem = existing ?: return@setOnClickListener
            ThemeManager.performTapHaptic(it)
            onResetStreak?.invoke(targetItem)
            dialog.dismiss()
        }

        binding.btnAddStep.setOnClickListener {
            val text = binding.etAddStep.text?.toString()?.trim().orEmpty()
            if (text.isEmpty()) return@setOnClickListener
            ThemeManager.performSoftTick(it)
            checklistSteps.add(ChecklistStep(id = UUID.randomUUID().toString(), text = text))
            binding.etAddStep.setText("")
            renderEditor()
        }

        binding.btnSaveCountdown.setOnClickListener {
            val rawTitle = binding.etTitle.text?.toString()?.trim().orEmpty()
            if (rawTitle.isEmpty()) {
                binding.etTitle.error = "Enter a title"
                binding.etTitle.requestFocus()
                return@setOnClickListener
            }

            ThemeManager.performTapHaptic(it)
            val saveNow = PrecisionClockTicker.normalizeToWholeSecond(System.currentTimeMillis())
            val cleanTarget = PrecisionClockTicker.normalizeToWholeSecond(selectedCal.timeInMillis)
            val rawNote = binding.etNote.text?.toString()?.trim().orEmpty()
            val item = CountdownItem(
                id = existing?.id ?: UUID.randomUUID().toString(),
                title = rawTitle,
                targetEpochMillis = cleanTarget,
                startEpochMillis = PrecisionClockTicker.normalizeToWholeSecond(
                    existing?.startEpochMillis ?: saveNow
                ),
                createdAtMillis = existing?.createdAtMillis ?: saveNow,
                mode = mode,
                repeatRule = if (mode == CountdownMode.COUNT_UP) RepeatRule.NONE else repeatRule,
                category = category,
                accentHex = accentHex,
                isPinned = isPinned,
                emoji = selectedIconId,
                reminderEnabled = if (mode == CountdownMode.COUNT_UP) false else reminderEnabled,
                note = rawNote,
                checkInCount = if (mode == CountdownMode.COUNT_UP) existing?.checkInCount ?: 0 else 0,
                lastCheckInDayKey = if (mode == CountdownMode.COUNT_UP) {
                    existing?.lastCheckInDayKey.orEmpty()
                } else "",
                checklist = checklistSteps.toList()
            )
            onSave(item)
            dialog.dismiss()
        }

        renderEditor()
        dialog.show()
    }

    private fun makeChip(
        context: Context,
        label: String,
        active: Boolean,
        palette: ThemeManager.ThemePalette,
        onClick: () -> Unit
    ): TextView {
        val padH = ThemeManager.dp(context, 12f).toInt()
        val height = ThemeManager.dp(context, 34f).toInt()
        val marginEnd = ThemeManager.dp(context, 8f).toInt()
        return TextView(context).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(padH, 0, padH, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                height
            ).apply {
                this.marginEnd = marginEnd
            }
            background = if (active) {
                ThemeManager.activeChipDrawable(context, palette)
            } else {
                ThemeManager.pillDrawable(context, palette)
            }
            setTextColor(if (active) Color.WHITE else palette.textPrimary)
            setOnClickListener {
                ThemeManager.performSoftTick(it)
                onClick()
            }
        }
    }
}
