package com.lumetime.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.lumetime.app.core.PrecisionClockTicker
import com.lumetime.app.core.ReminderScheduler
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.data.CountdownStore
import com.lumetime.app.databinding.ActivityFocusClockBinding
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.RepeatRule
import com.lumetime.app.ui.CountdownEditorDialog
import com.lumetime.app.ui.FocusAnimations
import com.lumetime.app.ui.ShareCardGenerator
import com.lumetime.app.ui.ThemeManager
import com.lumetime.app.widget.WidgetScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen Zen Focus Clock and Landscape Desk Clock for any countdown or milestone.
 */
class FocusClockActivity : AppCompatActivity() {

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private lateinit var binding: ActivityFocusClockBinding
    private lateinit var store: CountdownStore

    private val mainHandler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy • h:mm a", Locale.getDefault())

    private var countdownId: String = ""
    private var currentItem: CountdownItem? = null
    private var keepAwake: Boolean = false
    private var firstRender: Boolean = true

    private var basePadLeft = 0
    private var basePadTop = 0
    private var basePadRight = 0
    private var basePadBottom = 0

    private val tickRunnable = object : Runnable {
        override fun run() {
            val rawNow = System.currentTimeMillis()
            val snappedNow = PrecisionClockTicker.snapNowToSecond(rawNow)
            renderCountdown(snappedNow, animate = !firstRender)
            firstRender = false
            val delay = PrecisionClockTicker.nextMidpointDelayMillis(System.currentTimeMillis())
            mainHandler.postDelayed(this, delay)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusClockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        countdownId = intent.getStringExtra(EXTRA_COUNTDOWN_ID).orEmpty()
        if (savedInstanceState != null) {
            keepAwake = savedInstanceState.getBoolean(KEY_KEEP_AWAKE, false)
        }

        store = CountdownStore.create(this)
        currentItem = store.loadAll().firstOrNull { it.id == countdownId }
        if (currentItem == null) {
            finish()
            return
        }

        capturePadding()
        setupInsets()
        setupInteractions()
        applyKeepAwakeFlag()
        applyTheme()
        renderCountdown(System.currentTimeMillis(), animate = false)
    }

    override fun onStart() {
        super.onStart()
        binding.focusAmbientGlow.startBreathing()
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.post(tickRunnable)
    }

    override fun onStop() {
        mainHandler.removeCallbacks(tickRunnable)
        binding.focusAmbientGlow.stopBreathing()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_KEEP_AWAKE, keepAwake)
    }

    private fun capturePadding() {
        basePadLeft = binding.focusContent.paddingLeft
        basePadTop = binding.focusContent.paddingTop
        basePadRight = binding.focusContent.paddingRight
        basePadBottom = binding.focusContent.paddingBottom
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.focusContent) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                basePadLeft + bars.left,
                basePadTop + bars.top,
                basePadRight + bars.right,
                basePadBottom + bars.bottom
            )
            insets
        }
    }

    private fun setupInteractions() {
        FocusAnimations.attachGlassPress(binding.btnFocusBack, 0.92f)
        FocusAnimations.attachGlassPress(binding.btnKeepAwake, 0.94f)
        FocusAnimations.attachGlassPress(binding.btnFocusShare, 0.92f)
        FocusAnimations.attachGlassPress(binding.btnFocusEdit, 0.92f)

        binding.btnFocusBack.setOnClickListener {
            ThemeManager.performTapHaptic(it)
            finish()
        }

        binding.btnKeepAwake.setOnClickListener {
            ThemeManager.performSoftTick(it)
            keepAwake = !keepAwake
            applyKeepAwakeFlag()
            applyTheme()
        }

        binding.btnFocusShare.setOnClickListener { view ->
            val item = currentItem ?: return@setOnClickListener
            ThemeManager.performTapHaptic(view)
            val palette = ThemeManager.getPalette(this)
            lifecycleScope.launch {
                val uri = ShareCardGenerator.generateCountdownPosterUri(
                    context = this@FocusClockActivity,
                    item = item,
                    palette = palette
                )
                ShareCardGenerator.launchShareChooser(
                    this@FocusClockActivity,
                    uri,
                    item.title
                )
            }
        }

        binding.btnFocusEdit.setOnClickListener { view ->
            val item = currentItem ?: return@setOnClickListener
            ThemeManager.performTapHaptic(view)
            val palette = ThemeManager.getPalette(this)
            CountdownEditorDialog.show(
                context = this,
                palette = palette,
                existing = item,
                onSave = { updated ->
                    store.upsert(updated)
                    ReminderScheduler.scheduleForItem(this, updated)
                    currentItem = updated
                    firstRender = true
                    applyTheme()
                    renderCountdown(System.currentTimeMillis(), animate = false)
                    WidgetScheduler.refreshAllWidgets(this)
                },
                onDelete = { deleted ->
                    store.delete(deleted.id)
                    ReminderScheduler.cancelForItem(this, deleted.id)
                    WidgetScheduler.refreshAllWidgets(this)
                    finish()
                },
                onRequestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            requestNotificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        }
                    }
                }
            )
        }
    }

    private fun applyKeepAwakeFlag() {
        if (keepAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun applyTheme() {
        val palette = ThemeManager.getPalette(this)
        ThemeManager.applySystemBars(this, palette)
        ThemeManager.applyAmbientBackground(binding.focusRoot, palette, animate = false)
        binding.focusAmbientGlow.setThemePalette(palette)

        binding.btnFocusBack.background = ThemeManager.pillDrawable(this, palette)
        binding.btnFocusBack.setColorFilter(palette.textPrimary)

        binding.btnFocusShare.background = ThemeManager.pillDrawable(this, palette)
        binding.btnFocusShare.setColorFilter(palette.accent)

        binding.btnFocusEdit.background = ThemeManager.pillDrawable(this, palette)
        binding.btnFocusEdit.setColorFilter(palette.textPrimary)

        if (keepAwake) {
            binding.btnKeepAwake.background = ThemeManager.activeChipDrawable(this, palette)
            binding.ivAwakeIcon.setColorFilter(Color.WHITE)
            binding.tvAwakeLabel.setTextColor(Color.WHITE)
        } else {
            binding.btnKeepAwake.background = ThemeManager.pillDrawable(this, palette)
            binding.ivAwakeIcon.setColorFilter(palette.textSecondary)
            binding.tvAwakeLabel.setTextColor(palette.textSecondary)
        }

        binding.focusCard.background = ThemeManager.cardDrawable(this, palette, isHero = true)
        binding.tvFocusTitle.setTextColor(palette.textPrimary)
        binding.tvFocusNote.setTextColor(palette.textSecondary)
        binding.tvFocusRingPercent.setTextColor(palette.textPrimary)
        binding.tvFocusTargetDate.setTextColor(palette.textSecondary)

        val isLandscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val digitSp = if (isLandscape) 30f else 32f
        listOf(
            binding.focusRollingDays,
            binding.focusRollingHours,
            binding.focusRollingMinutes,
            binding.focusRollingSeconds
        ).forEach { roller ->
            roller.setStyle(
                bg = palette.glassPill,
                stroke = palette.glassStroke,
                text = palette.textPrimary,
                textSizeSp = digitSp
            )
        }
        listOf(
            binding.tvFocusUnitDays,
            binding.tvFocusUnitHrs,
            binding.tvFocusUnitMin,
            binding.tvFocusUnitSec
        ).forEach { it.setTextColor(palette.textTertiary) }
    }

    private fun renderCountdown(now: Long, animate: Boolean) {
        val item = currentItem ?: return
        val palette = ThemeManager.getPalette(this)
        val breakdown = TimeCalculator.calculateBreakdown(item, now)
        val itemAccent = runCatching { Color.parseColor(item.accentHex) }.getOrDefault(palette.accent)
        val displayAccent = when {
            breakdown.isCountUp -> itemAccent
            breakdown.isExpired -> palette.textTertiary
            breakdown.totalHours < 24L -> palette.urgentCoral
            breakdown.days < 7L -> palette.warningAmber
            else -> itemAccent
        }

        val repeatText = when (item.repeatRule) {
            RepeatRule.YEARLY -> " • YEARLY"
            RepeatRule.MONTHLY -> " • MONTHLY"
            RepeatRule.NONE -> ""
        }
        val modeLabel = if (breakdown.isCountUp) "MILESTONE" else item.category.label.uppercase(Locale.US)
        binding.tvFocusCategoryBadge.text = "$modeLabel$repeatText"
        binding.tvFocusCategoryBadge.background = ThemeManager.tagDrawable(this, palette)
        binding.tvFocusCategoryBadge.setTextColor(displayAccent)

        binding.ivFocusIcon.setImageResource(item.resolveIcon().drawableRes)
        binding.ivFocusIcon.setColorFilter(displayAccent)
        binding.tvFocusTitle.text = item.title
        if (item.note.isNotBlank()) {
            binding.tvFocusNote.visibility = android.view.View.VISIBLE
            binding.tvFocusNote.text = item.note
        } else {
            binding.tvFocusNote.visibility = android.view.View.GONE
        }

        binding.focusProgressRing.setColors(
            track = palette.progressTrack,
            accentStart = ThemeManager.withAlpha(displayAccent, 0xCC),
            accentEnd = displayAccent
        )
        binding.focusProgressRing.setProgress(if (breakdown.isCountUp) 1f else breakdown.progress)
        binding.tvFocusRingPercent.text = if (breakdown.isCountUp) {
            "LIVE"
        } else {
            String.format(Locale.US, "%.0f%%", breakdown.progress * 100f)
        }

        binding.focusRollingDays.setValue(
            String.format(Locale.US, "%02d", breakdown.days),
            animate = animate
        )
        binding.focusRollingHours.setValue(
            String.format(Locale.US, "%02d", breakdown.hours),
            animate = animate
        )
        binding.focusRollingMinutes.setValue(
            String.format(Locale.US, "%02d", breakdown.minutes),
            animate = animate
        )
        binding.focusRollingSeconds.setValue(
            String.format(Locale.US, "%02d", breakdown.seconds),
            animate = animate
        )

        binding.tvFocusTotalHours.background = ThemeManager.tagDrawable(this, palette)
        binding.tvFocusTotalHours.setTextColor(displayAccent)
        binding.tvFocusTotalHours.text = when {
            breakdown.isExpired -> "Target Reached"
            breakdown.isCountUp -> String.format(Locale.US, "+%,d total hours", breakdown.totalHours)
            else -> String.format(Locale.US, "%,d total hours left", breakdown.totalHours)
        }

        val prefix = if (breakdown.isCountUp) "Started " else "Target • "
        binding.tvFocusTargetDate.text = prefix + dateFormat.format(Date(breakdown.effectiveTargetMillis))
    }

    companion object {
        const val EXTRA_COUNTDOWN_ID = "extra_countdown_id"
        private const val KEY_KEEP_AWAKE = "key_keep_awake"

        fun createIntent(context: Context, countdownId: String): Intent {
            return Intent(context, FocusClockActivity::class.java).apply {
                putExtra(EXTRA_COUNTDOWN_ID, countdownId)
            }
        }
    }
}
