package com.lumetime.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lumetime.app.core.FocusTimerEngine
import com.lumetime.app.core.PrecisionClockTicker
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.core.TimerAlertManager
import com.lumetime.app.data.CountdownStore
import com.lumetime.app.data.FocusTimerStore
import com.lumetime.app.data.PersonalProfilePrefs
import com.lumetime.app.databinding.ActivityDeskClockBinding
import com.lumetime.app.ui.FocusAnimations
import com.lumetime.app.ui.FocusTimerDialog
import com.lumetime.app.ui.ThemeManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Full-screen Apple Liquid Glass Desk Clock & Focus Station with live rolling
 * HH:MM:SS odometer clock, 1-tap Pomodoro timer, and pinned Hero summary.
 */
class DeskClockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeskClockBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("EEEE • MMM d, yyyy", Locale.getDefault())

    private var keepAwake: Boolean = true
    private var firstRender: Boolean = true
    private var basePadLeft = 0
    private var basePadTop = 0
    private var basePadRight = 0
    private var basePadBottom = 0

    private val tickRunnable = object : Runnable {
        override fun run() {
            val rawNow = System.currentTimeMillis()
            val snapped = PrecisionClockTicker.snapNowToSecond(rawNow)
            renderClock(snapped, rawNow, animate = !firstRender)
            firstRender = false
            val delay = PrecisionClockTicker.nextMidpointDelayMillis(System.currentTimeMillis())
            mainHandler.postDelayed(this, delay)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeskClockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState != null) {
            keepAwake = savedInstanceState.getBoolean(KEY_KEEP_AWAKE, true)
        }

        basePadLeft = binding.deskClockContent.paddingLeft
        basePadTop = binding.deskClockContent.paddingTop
        basePadRight = binding.deskClockContent.paddingRight
        basePadBottom = binding.deskClockContent.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.deskClockContent) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                basePadLeft + bars.left,
                basePadTop + bars.top,
                basePadRight + bars.right,
                basePadBottom + bars.bottom
            )
            insets
        }

        FocusAnimations.attachGlassPress(binding.btnDeskBack, 0.92f)
        FocusAnimations.attachGlassPress(binding.btnDeskPureBlack, 0.94f)
        FocusAnimations.attachGlassPress(binding.btnDeskKeepAwake, 0.94f)
        FocusAnimations.attachGlassPress(binding.btnDeskFocusAction, 0.94f)

        binding.btnDeskBack.setOnClickListener {
            ThemeManager.performTapHaptic(it)
            finish()
        }
        binding.btnDeskPureBlack.setOnClickListener {
            ThemeManager.performSoftTick(it)
            ThemeManager.togglePureBlackForClock(this)
            applyTheme()
        }
        binding.btnDeskKeepAwake.setOnClickListener {
            ThemeManager.performSoftTick(it)
            keepAwake = !keepAwake
            applyKeepAwakeFlag()
            applyTheme()
        }
        binding.deskFocusStrip.setOnClickListener {
            ThemeManager.performSoftTick(it)
            val palette = ThemeManager.getPalette(
                this,
                forcePureBlack = ThemeManager.resolvePureBlackForClock(this)
            )
            FocusTimerDialog.show(this, palette) {
                renderClock(System.currentTimeMillis(), System.currentTimeMillis(), animate = false)
            }
        }
        binding.btnDeskFocusAction.setOnClickListener { view ->
            ThemeManager.performTapHaptic(view)
            val now = System.currentTimeMillis()
            var snap = FocusTimerStore.load(this)
            snap = FocusTimerEngine.toggleTimer(snap, now)
            FocusTimerStore.save(this, snap)
            TimerAlertManager.syncFocusAlarm(this, snap)
            renderClock(now, now, animate = false)
        }

        applyKeepAwakeFlag()
        applyTheme()
    }

    override fun onStart() {
        super.onStart()
        binding.deskAmbientGlow.startBreathing()
        firstRender = true
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.post(tickRunnable)
    }

    override fun onStop() {
        mainHandler.removeCallbacks(tickRunnable)
        binding.deskAmbientGlow.stopBreathing()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_KEEP_AWAKE, keepAwake)
    }

    private fun applyKeepAwakeFlag() {
        if (keepAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun applyTheme() {
        val pureBlack = ThemeManager.resolvePureBlackForClock(this)
        val palette = ThemeManager.getPalette(this, forcePureBlack = pureBlack)
        ThemeManager.applySystemBars(this, palette)
        ThemeManager.applyAmbientBackground(binding.deskClockRoot, palette, animate = false)
        binding.deskAmbientGlow.setThemePalette(palette)

        binding.btnDeskBack.background = ThemeManager.pillDrawable(this, palette)
        binding.btnDeskBack.setColorFilter(palette.textPrimary)

        if (palette.isPureBlack) {
            binding.btnDeskPureBlack.background = ThemeManager.activeChipDrawable(this, palette)
            binding.ivDeskPureBlackIcon.setColorFilter(Color.WHITE)
            binding.tvDeskPureBlackLabel.setTextColor(Color.WHITE)
        } else {
            binding.btnDeskPureBlack.background = ThemeManager.pillDrawable(this, palette)
            binding.ivDeskPureBlackIcon.setColorFilter(palette.textSecondary)
            binding.tvDeskPureBlackLabel.setTextColor(palette.textSecondary)
        }

        if (keepAwake) {
            binding.btnDeskKeepAwake.background = ThemeManager.activeChipDrawable(this, palette)
            binding.ivDeskAwakeIcon.setColorFilter(Color.WHITE)
            binding.tvDeskAwakeLabel.setTextColor(Color.WHITE)
        } else {
            binding.btnDeskKeepAwake.background = ThemeManager.pillDrawable(this, palette)
            binding.ivDeskAwakeIcon.setColorFilter(palette.textSecondary)
            binding.tvDeskAwakeLabel.setTextColor(palette.textSecondary)
        }

        binding.deskClockCard.background =
            ThemeManager.accentedCardDrawable(this, palette, palette.accent, isHero = true)
        binding.tvDeskGreetingTag.background = ThemeManager.tintedTagDrawable(this, palette, palette.accent)
        binding.tvDeskGreetingTag.setTextColor(palette.accent)
        binding.tvDeskDate.setTextColor(palette.textSecondary)
        binding.tvDeskAmPmAndProgress.setTextColor(palette.textTertiary)
        binding.deskFocusStrip.background = ThemeManager.pillDrawable(this, palette)
        binding.ivDeskFocusIcon.setColorFilter(palette.accent)
        binding.tvDeskFocusStatus.setTextColor(palette.textPrimary)
        binding.btnDeskFocusAction.background = ThemeManager.fabDrawable(this, palette)
        binding.tvDeskHeroEvent.setTextColor(palette.textSecondary)

        val digitSp = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            46f
        } else {
            36f
        }
        val digitBg = if (palette.isPureBlack) Color.BLACK else palette.glassPill
        val digitStroke = if (palette.isPureBlack) 0x26FFFFFF else palette.glassStroke
        listOf(binding.deskRollingHour, binding.deskRollingMinute, binding.deskRollingSecond)
            .forEach { roller ->
                roller.setStyle(
                    bg = digitBg,
                    stroke = digitStroke,
                    text = palette.textPrimary,
                    textSizeSp = digitSp
                )
            }
    }

    private fun renderClock(snappedNow: Long, rawNow: Long, animate: Boolean) {
        val cal = Calendar.getInstance().apply { timeInMillis = snappedNow }
        val is24h = DateFormat.is24HourFormat(this)
        val hour24 = cal.get(Calendar.HOUR_OF_DAY)
        val displayHour = if (is24h) hour24 else {
            val h12 = cal.get(Calendar.HOUR)
            if (h12 == 0) 12 else h12
        }
        val minute = cal.get(Calendar.MINUTE)
        val second = cal.get(Calendar.SECOND)

        binding.deskRollingHour.setValue(String.format(Locale.US, "%02d", displayHour), animate)
        binding.deskRollingMinute.setValue(String.format(Locale.US, "%02d", minute), animate)
        binding.deskRollingSecond.setValue(String.format(Locale.US, "%02d", second), animate)

        val profile = PersonalProfilePrefs.load(this, rawNow)
        binding.tvDeskGreetingTag.text =
            PersonalProfilePrefs.buildGreeting(hour24, profile.userName).uppercase(Locale.US)
        binding.tvDeskDate.text = dateFormat.format(Date(snappedNow))

        val yearPct = TimeCalculator.computeYearProgress(snappedNow).percentCompleted
        val amPm = if (is24h) "24H CLOCK" else if (hour24 < 12) "AM" else "PM"
        binding.tvDeskAmPmAndProgress.text = String.format(
            Locale.US,
            "%s  •  %.1f%% of %d  •  Today Focus %dm/%dm",
            amPm,
            yearPct,
            cal.get(Calendar.YEAR),
            profile.focusedMinutesToday,
            profile.dailyFocusGoalMinutes
        )

        var timerSnap = FocusTimerStore.load(this)
        if (timerSnap.isTimerRunning && FocusTimerEngine.isTimerCompleted(timerSnap, rawNow)) {
            val mins = ((timerSnap.timerDurationMillis + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
            if (!timerSnap.completedLogged) {
                PersonalProfilePrefs.recordFocusSession(this, mins, rawNow)
            }
            timerSnap = timerSnap.copy(
                isTimerRunning = false,
                timerRemainingMillis = 0L,
                timerTargetEndMillis = 0L,
                completedLogged = true,
                completedAcknowledged = false
            )
            FocusTimerStore.save(this, timerSnap)
            TimerAlertManager.cancelFocusTimerAlarm(this)
            TimerAlertManager.playCompletionChimeAndVibrate(this)
        }

        val rem = FocusTimerEngine.remainingTimerMillis(timerSnap, rawNow)
        binding.tvDeskFocusStatus.text = when {
            rem == 0L -> "Done! ${timerSnap.sessionLabel}"
            timerSnap.isTimerRunning -> "${timerSnap.sessionLabel} • ${FocusTimerEngine.formatMmSs(rem)}"
            else -> "${timerSnap.sessionLabel} • ${FocusTimerEngine.formatMmSs(rem)}"
        }
        binding.btnDeskFocusAction.text = when {
            timerSnap.isTimerRunning -> "Pause"
            rem == 0L -> "Restart"
            else -> "Start"
        }

        val hero = CountdownStore.selectHeroItem(CountdownStore.create(this).loadAll(snappedNow), snappedNow)
        if (hero != null) {
            val bd = TimeCalculator.calculateBreakdown(hero, snappedNow)
            binding.tvDeskHeroEvent.text = when {
                bd.isExpired -> "${hero.title} • Reached"
                bd.isCountUp -> "${hero.title} • +${bd.days}d ${bd.hours}h"
                else -> "Up Next: ${hero.title} in ${bd.days}d ${bd.hours}h"
            }
        } else {
            binding.tvDeskHeroEvent.text = getString(R.string.tagline_live)
        }
    }

    companion object {
        private const val KEY_KEEP_AWAKE = "desk_keep_awake"

        fun createIntent(context: Context): Intent =
            Intent(context, DeskClockActivity::class.java)
    }
}
