package com.lumetime.app.ui

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.core.WorldClockEngine
import com.lumetime.app.data.WorldClockStore
import com.lumetime.app.databinding.DialogWorldClockBinding
import java.util.Locale

/**
 * Liquid Glass World Clock & Meeting Time-Zone Shifter sheet.
 */
object WorldClockDialog {

    fun show(context: Context, palette: ThemeManager.ThemePalette) {
        val dialog = GlassSheetHelper.createBottomSheet(context)
        val binding = DialogWorldClockBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)
        GlassSheetHelper.configureBottomWindow(dialog, context)

        binding.worldClockSheetRoot.background = ThemeManager.sheetDrawable(context, palette)
        binding.dragHandle.background = ThemeManager.dragHandleDrawable(context, palette)
        binding.tvWorldClockTitle.setTextColor(palette.textPrimary)
        binding.tvWorldClockSubtitle.setTextColor(palette.textSecondary)
        binding.tvManageCitiesLabel.setTextColor(palette.textSecondary)
        binding.btnCloseWorldClock.background = ThemeManager.pillDrawable(context, palette)
        binding.btnCloseWorldClock.setColorFilter(palette.textPrimary)

        var shiftHours = 0
        val use24h = DateFormat.is24HourFormat(context)
        val handler = Handler(Looper.getMainLooper())

        fun renderCityToggleChips() {
            val pinned = WorldClockStore.loadPinnedIds(context)
            binding.worldCityToggleChips.removeAllViews()
            val padH = ThemeManager.dp(context, 12f).toInt()
            val height = ThemeManager.dp(context, 32f).toInt()
            val gap = ThemeManager.dp(context, 8f).toInt()

            WorldClockEngine.catalog.forEach { city ->
                val active = pinned.contains(city.id)
                val chip = TextView(context).apply {
                    text = city.city.substringBefore(" /")
                    textSize = 11f
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
                    setTextColor(if (active) Color.WHITE else palette.textSecondary)
                    setOnClickListener {
                        ThemeManager.performSoftTick(it)
                        WorldClockStore.togglePinned(context, city.id)
                        renderCityToggleChips()
                    }
                }
                binding.worldCityToggleChips.addView(chip)
            }
        }

        fun renderClocks() {
            val refMillis = System.currentTimeMillis() +
                shiftHours.toLong() * TimeCalculator.MILLIS_PER_HOUR

            binding.btnShiftMinusHour.background = ThemeManager.pillDrawable(context, palette)
            binding.btnShiftMinusHour.setTextColor(palette.textPrimary)
            binding.btnShiftPlusHour.background = ThemeManager.pillDrawable(context, palette)
            binding.btnShiftPlusHour.setTextColor(palette.textPrimary)

            val isLive = shiftHours == 0
            binding.btnShiftLiveNow.background = if (isLive) {
                ThemeManager.activeChipDrawable(context, palette)
            } else {
                ThemeManager.tintedTagDrawable(context, palette, palette.accent)
            }
            binding.btnShiftLiveNow.setTextColor(if (isLive) Color.WHITE else palette.accent)
            binding.btnShiftLiveNow.text = when {
                shiftHours == 0 -> "Live Now"
                shiftHours > 0 -> "+${shiftHours}h (Tap Reset)"
                else -> "${shiftHours}h (Tap Reset)"
            }

            val cities = WorldClockStore.loadPinnedCities(context)
            val clocks = WorldClockEngine.evaluateZones(cities, refMillis, use24Hour = use24h)
            binding.worldCitiesContainer.removeAllViews()

            val padH = ThemeManager.dp(context, 14f).toInt()
            val padV = ThemeManager.dp(context, 12f).toInt()
            val gap = ThemeManager.dp(context, 8f).toInt()

            clocks.forEach { clock ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(padH, padV, padH, padV)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = gap }
                    background = ThemeManager.cardDrawable(context, palette, isHero = false)
                }

                val leftCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val titleRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val tvCity = TextView(context).apply {
                    text = clock.city.city
                    textSize = 14f
                    setTextColor(palette.textPrimary)
                }
                val tvWorkTag = TextView(context).apply {
                    text = if (clock.isWorkHours) "WORK" else "OFF"
                    textSize = 9f
                    setPadding(
                        ThemeManager.dp(context, 6f).toInt(),
                        ThemeManager.dp(context, 2f).toInt(),
                        ThemeManager.dp(context, 6f).toInt(),
                        ThemeManager.dp(context, 2f).toInt()
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = ThemeManager.dp(context, 6f).toInt() }
                    val tagColor = if (clock.isWorkHours) palette.accent else palette.textTertiary
                    background = ThemeManager.tintedTagDrawable(context, palette, tagColor)
                    setTextColor(tagColor)
                }
                titleRow.addView(tvCity)
                titleRow.addView(tvWorkTag)

                val tvSub = TextView(context).apply {
                    text = String.format(
                        Locale.US,
                        "%s • %s",
                        clock.formattedDate,
                        clock.offsetDiffLabel
                    )
                    textSize = 11f
                    setTextColor(palette.textSecondary)
                }
                leftCol.addView(titleRow)
                leftCol.addView(tvSub)

                val tvTime = TextView(context).apply {
                    text = clock.formattedTime
                    textSize = 20f
                    setTextColor(palette.accent)
                }

                row.addView(leftCol)
                row.addView(tvTime)
                binding.worldCitiesContainer.addView(row)
            }
        }

        val tickRunnable = object : Runnable {
            override fun run() {
                renderClocks()
                handler.postDelayed(this, 1000L)
            }
        }

        binding.btnCloseWorldClock.setOnClickListener {
            ThemeManager.performSoftTick(it)
            dialog.dismiss()
        }
        binding.btnShiftMinusHour.setOnClickListener {
            ThemeManager.performSoftTick(it)
            shiftHours -= 1
            renderClocks()
        }
        binding.btnShiftPlusHour.setOnClickListener {
            ThemeManager.performSoftTick(it)
            shiftHours += 1
            renderClocks()
        }
        binding.btnShiftLiveNow.setOnClickListener {
            ThemeManager.performSoftTick(it)
            shiftHours = 0
            renderClocks()
        }

        dialog.setOnDismissListener {
            handler.removeCallbacks(tickRunnable)
        }

        renderCityToggleChips()
        renderClocks()
        handler.post(tickRunnable)
        dialog.show()
    }
}
