package com.lumetime.app

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.databinding.ActivityMainBinding
import com.lumetime.app.ui.ThemeManager
import com.lumetime.app.ui.ThemePickerDialog
import com.lumetime.app.ui.YearDisplayFormatter
import com.lumetime.app.ui.YearPulseMetricMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private val headerDateFormat = SimpleDateFormat("EEE, MMM d • yyyy", Locale.getDefault())

    private var metricMode: YearPulseMetricMode = YearPulseMetricMode.DAYS_LEFT
    private var lastBasePaddingTop: Int = 0
    private var lastBasePaddingBottom: Int = 0
    private var lastBasePaddingLeft: Int = 0
    private var lastBasePaddingRight: Int = 0

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateYearPulse()
            val now = System.currentTimeMillis()
            val delayToNextSecond = 1000L - (now % 1000L)
            mainHandler.postDelayed(this, delayToNextSecond.coerceAtLeast(150L))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState != null) {
            val savedOrdinal = savedInstanceState.getInt(KEY_METRIC_MODE, 0)
            metricMode = YearPulseMetricMode.entries.getOrElse(savedOrdinal) {
                YearPulseMetricMode.DAYS_LEFT
            }
        }

        captureBasePadding()
        setupInsets()
        setupInteractions()
        applyTheme(animate = false)
        updateYearPulse()
    }

    override fun onStart() {
        super.onStart()
        binding.ambientGlowView.startBreathing()
        binding.yearDotMatrix.startPulse()
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.post(tickRunnable)
    }

    override fun onStop() {
        mainHandler.removeCallbacks(tickRunnable)
        binding.ambientGlowView.stopBreathing()
        binding.yearDotMatrix.stopPulse()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_METRIC_MODE, metricMode.ordinal)
    }

    private fun captureBasePadding() {
        lastBasePaddingLeft = binding.contentColumn.paddingLeft
        lastBasePaddingTop = binding.contentColumn.paddingTop
        lastBasePaddingRight = binding.contentColumn.paddingRight
        lastBasePaddingBottom = binding.contentColumn.paddingBottom
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentColumn) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                lastBasePaddingLeft + bars.left,
                lastBasePaddingTop + bars.top,
                lastBasePaddingRight + bars.right,
                lastBasePaddingBottom + bars.bottom
            )
            insets
        }
    }

    private fun setupInteractions() {
        binding.btnThemePicker.setOnClickListener { view ->
            ThemeManager.performTapHaptic(view)
            ThemePickerDialog.show(this) { _, _ ->
                applyTheme(animate = true)
            }
        }

        binding.cardYearPulse.setOnClickListener { view ->
            ThemeManager.performSoftTick(view)
            metricMode = metricMode.next()
            animateMetricSwitch()
            updateYearPulse()
        }
    }

    private fun animateMetricSwitch() {
        val container = binding.yearMetricContainer
        container.animate().cancel()
        container.alpha = 0.45f
        container.translationY = ThemeManager.dp(this, 6f)
        container.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun applyTheme(animate: Boolean) {
        val palette = ThemeManager.getPalette(this)
        ThemeManager.applySystemBars(this, palette)
        ThemeManager.applyAmbientBackground(binding.rootContainer, palette, animate)

        binding.ambientGlowView.setThemePalette(palette)
        binding.yearDotMatrix.setThemePalette(palette)
        binding.yearProgressBar.setColors(
            track = palette.progressTrack,
            accentStart = palette.accentEnd,
            accentEnd = palette.accent
        )

        binding.tvBrandTitle.setTextColor(palette.textPrimary)
        binding.tvTodayDate.setTextColor(palette.textSecondary)

        binding.btnThemePicker.background = ThemeManager.pillDrawable(this, palette)
        binding.ivPaletteIcon.setColorFilter(palette.accent)
        binding.tvThemeButtonLabel.setTextColor(palette.textPrimary)

        binding.cardYearPulse.background = ThemeManager.cardDrawable(this, palette, isHero = true)
        binding.tvYearPulseTitle.setTextColor(palette.accent)
        binding.tvDayOfYearBadge.background = ThemeManager.tagDrawable(this, palette)
        binding.tvDayOfYearBadge.setTextColor(palette.accent)
        binding.tvYearPrimaryValue.setTextColor(palette.textPrimary)
        binding.tvYearPrimaryUnit.setTextColor(palette.textSecondary)
        binding.tvYearSecondaryCaption.setTextColor(palette.textSecondary)
        binding.tvYearLiveFooter.setTextColor(palette.textSecondary)
        binding.ivSwapHint.setColorFilter(palette.textTertiary)
        binding.tvTapToCycle.setTextColor(palette.textTertiary)

        binding.cardUpcomingCountdowns.background = ThemeManager.cardDrawable(this, palette)
        binding.ivSparkleHint.setColorFilter(palette.accent)
        binding.tvUpcomingTitle.setTextColor(palette.textPrimary)
        binding.tvUpcomingDesc.setTextColor(palette.textSecondary)
    }

    private fun updateYearPulse() {
        val now = System.currentTimeMillis()
        val yearProgress = TimeCalculator.computeYearProgress(now)
        val display = YearDisplayFormatter.format(yearProgress, metricMode, now)

        binding.tvTodayDate.text = headerDateFormat.format(Date(now))
        binding.tvYearPulseTitle.text = String.format(
            Locale.US,
            "%d • %s",
            yearProgress.year,
            getString(R.string.year_pulse_badge)
        )
        binding.tvDayOfYearBadge.text = display.dayOfYearBadge
        binding.tvYearPrimaryValue.text = display.primaryValue
        binding.tvYearPrimaryUnit.text = display.primaryUnit
        binding.tvYearSecondaryCaption.text = display.secondaryCaption
        binding.tvYearLiveFooter.text = display.liveFooter

        binding.yearDotMatrix.setData(yearProgress.totalDays, yearProgress.todayDayOfYear)
        binding.yearProgressBar.setProgress(yearProgress.progressFraction)
    }

    companion object {
        private const val KEY_METRIC_MODE = "metric_mode"
    }
}
