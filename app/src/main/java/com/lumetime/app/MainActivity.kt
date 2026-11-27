package com.lumetime.app

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.data.CountdownFilter
import com.lumetime.app.data.CountdownSortMode
import com.lumetime.app.data.CountdownStore
import com.lumetime.app.databinding.ActivityMainBinding
import com.lumetime.app.model.CountdownCategory
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.ui.CountdownAdapter
import com.lumetime.app.ui.CountdownEditorDialog
import com.lumetime.app.ui.FilterChipAdapter
import com.lumetime.app.ui.FocusAnimations
import com.lumetime.app.ui.ShareCardGenerator
import com.lumetime.app.ui.ThemeManager
import com.lumetime.app.ui.ThemePickerDialog
import com.lumetime.app.ui.WidgetStudioDialog
import com.lumetime.app.ui.YearDisplayFormatter
import com.lumetime.app.ui.YearPulseMetricMode
import com.lumetime.app.widget.WidgetScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: CountdownStore
    private lateinit var countdownAdapter: CountdownAdapter
    private lateinit var filterChipAdapter: FilterChipAdapter

    private val mainHandler = Handler(Looper.getMainLooper())
    private val headerDateFormat = SimpleDateFormat("EEE, MMM d • yyyy", Locale.getDefault())
    private val heroMetaDateFormat = SimpleDateFormat("EEE, MMM d, yyyy • h:mm a", Locale.getDefault())

    private var allItems: List<CountdownItem> = emptyList()
    private var heroItem: CountdownItem? = null
    private var activeFilter: CountdownFilter = CountdownFilter.All
    private var sortMode: CountdownSortMode = CountdownSortMode.SOONEST
    private var showTotalHoursMode: Boolean = false
    private var metricMode: YearPulseMetricMode = YearPulseMetricMode.DAYS_LEFT
    private var recentlyDeletedItem: CountdownItem? = null
    private var lastHeroId: String? = null

    private var basePadLeft = 0
    private var basePadTop = 0
    private var basePadRight = 0
    private var basePadBottom = 0
    private var baseFabMarginBottom = 0
    private var baseFabMarginEnd = 0

    private val tickRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            updateYearPulse(now)
            updateHeroCountdown(now, animateRoll = true)
            countdownAdapter.refreshTick(now)

            val delayToNextSecond = 1000L - (now % 1000L)
            mainHandler.postDelayed(this, delayToNextSecond.coerceAtLeast(150L))
        }
    }

    private val hideUndoRunnable = Runnable {
        binding.undoBanner.visibility = View.GONE
        recentlyDeletedItem = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState != null) {
            val savedMetric = savedInstanceState.getInt(KEY_METRIC_MODE, 0)
            metricMode = YearPulseMetricMode.entries.getOrElse(savedMetric) {
                YearPulseMetricMode.DAYS_LEFT
            }
            val savedSort = savedInstanceState.getInt(KEY_SORT_MODE, 0)
            sortMode = CountdownSortMode.entries.getOrElse(savedSort) {
                CountdownSortMode.SOONEST
            }
            showTotalHoursMode = savedInstanceState.getBoolean(KEY_HOURS_MODE, false)
        }

        store = CountdownStore.create(this)
        captureBaseDimensions()
        setupInsets()
        setupLists()
        setupInteractions()
        reloadCountdowns(animateHeroRoll = false)
        applyTheme(animate = false)
    }

    override fun onStart() {
        super.onStart()
        reloadCountdowns(animateHeroRoll = false, forceReloadFromDisk = true)
        binding.ambientGlowView.startBreathing()
        binding.yearDotMatrix.startPulse()
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.post(tickRunnable)
    }

    override fun onStop() {
        mainHandler.removeCallbacks(tickRunnable)
        mainHandler.removeCallbacks(hideUndoRunnable)
        binding.ambientGlowView.stopBreathing()
        binding.yearDotMatrix.stopPulse()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_METRIC_MODE, metricMode.ordinal)
        outState.putInt(KEY_SORT_MODE, sortMode.ordinal)
        outState.putBoolean(KEY_HOURS_MODE, showTotalHoursMode)
    }

    private fun captureBaseDimensions() {
        basePadLeft = binding.contentColumn.paddingLeft
        basePadTop = binding.contentColumn.paddingTop
        basePadRight = binding.contentColumn.paddingRight
        basePadBottom = binding.contentColumn.paddingBottom
        val fabParams = binding.fabAddCountdown.layoutParams as ViewGroup.MarginLayoutParams
        baseFabMarginBottom = fabParams.bottomMargin
        baseFabMarginEnd = fabParams.marginEnd
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootContainer) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.contentColumn.setPadding(
                basePadLeft + bars.left,
                basePadTop + bars.top,
                basePadRight + bars.right,
                basePadBottom + bars.bottom
            )
            (binding.fabAddCountdown.layoutParams as ViewGroup.MarginLayoutParams).apply {
                bottomMargin = baseFabMarginBottom + bars.bottom
                marginEnd = baseFabMarginEnd + bars.right
            }.also { binding.fabAddCountdown.layoutParams = it }

            (binding.undoBanner.layoutParams as ViewGroup.MarginLayoutParams).apply {
                bottomMargin = baseFabMarginBottom + bars.bottom
                marginStart = baseFabMarginEnd + bars.left
            }.also { binding.undoBanner.layoutParams = it }
            insets
        }
    }

    private fun setupLists() {
        val filters = buildList {
            add(CountdownFilter.All)
            add(CountdownFilter.CountUp)
            CountdownCategory.entries.forEach { add(CountdownFilter.Category(it)) }
        }
        filterChipAdapter = FilterChipAdapter(filters) { selected ->
            activeFilter = selected
            val pal = ThemeManager.getPalette(this)
            filterChipAdapter.setState(activeFilter, pal)
            refreshListOnly()
        }
        binding.rvFilterChips.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvFilterChips.adapter = filterChipAdapter

        countdownAdapter = CountdownAdapter(
            onCardClick = { item -> startActivity(FocusClockActivity.createIntent(this, item.id)) },
            onCardLongClick = { item -> openEditor(item) },
            onPinClick = { item ->
                allItems = store.togglePin(item.id)
                reloadCountdowns(animateHeroRoll = false)
                WidgetScheduler.refreshAllWidgets(this)
            }
        )
        countdownAdapter.setShowTotalHours(showTotalHoursMode)
        binding.rvCountdowns.layoutManager = LinearLayoutManager(this)
        binding.rvCountdowns.adapter = countdownAdapter
        binding.rvCountdowns.itemAnimator = null
    }

    private fun setupInteractions() {
        FocusAnimations.attachGlassPress(binding.btnWidgetsStudio, 0.95f)
        FocusAnimations.attachGlassPress(binding.btnThemePicker, 0.95f)
        FocusAnimations.attachGlassPress(binding.cardYearPulse, 0.985f)
        FocusAnimations.attachGlassPress(binding.cardHeroCountdown, 0.985f)
        FocusAnimations.attachGlassPress(binding.fabAddCountdown, 0.94f)

        binding.btnWidgetsStudio.setOnClickListener { view ->
            ThemeManager.performTapHaptic(view)
            WidgetStudioDialog.show(this, ThemeManager.getPalette(this))
        }

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
            updateYearPulse(System.currentTimeMillis())
        }

        binding.btnShareYearPulse.setOnClickListener { view ->
            ThemeManager.performTapHaptic(view)
            val palette = ThemeManager.getPalette(this)
            lifecycleScope.launch {
                val uri = ShareCardGenerator.generateYearPosterUri(this@MainActivity, palette)
                ShareCardGenerator.launchShareChooser(this@MainActivity, uri, getString(R.string.app_name))
            }
        }

        binding.cardHeroCountdown.setOnClickListener { view ->
            val currentHero = heroItem ?: return@setOnClickListener
            ThemeManager.performTapHaptic(view)
            startActivity(FocusClockActivity.createIntent(this, currentHero.id))
        }

        binding.cardHeroCountdown.setOnLongClickListener { view ->
            val currentHero = heroItem ?: return@setOnLongClickListener false
            ThemeManager.performTapHaptic(view)
            openEditor(currentHero)
            true
        }

        binding.btnEditHero.setOnClickListener { view ->
            val currentHero = heroItem ?: return@setOnClickListener
            ThemeManager.performTapHaptic(view)
            openEditor(currentHero)
        }

        binding.btnToggleHours.setOnClickListener { view ->
            ThemeManager.performSoftTick(view)
            showTotalHoursMode = !showTotalHoursMode
            countdownAdapter.setShowTotalHours(showTotalHoursMode)
            updateToolbarLabels()
        }

        binding.btnSortMode.setOnClickListener { view ->
            ThemeManager.performSoftTick(view)
            sortMode = sortMode.next()
            updateToolbarLabels()
            refreshListOnly()
        }

        binding.fabAddCountdown.setOnClickListener { view ->
            ThemeManager.performTapHaptic(view)
            openEditor(null)
        }

        binding.btnUndoDelete.setOnClickListener { view ->
            val restore = recentlyDeletedItem ?: return@setOnClickListener
            ThemeManager.performTapHaptic(view)
            mainHandler.removeCallbacks(hideUndoRunnable)
            binding.undoBanner.visibility = View.GONE
            recentlyDeletedItem = null
            allItems = store.upsert(restore)
            reloadCountdowns(animateHeroRoll = false)
            WidgetScheduler.refreshAllWidgets(this)
        }
    }

    private fun openEditor(existing: CountdownItem?) {
        val palette = ThemeManager.getPalette(this)
        CountdownEditorDialog.show(
            context = this,
            palette = palette,
            existing = existing,
            onSave = { saved ->
                allItems = store.upsert(saved)
                reloadCountdowns(animateHeroRoll = false)
                WidgetScheduler.refreshAllWidgets(this)
            },
            onDelete = { deleted ->
                store.delete(deleted.id)
                allItems = store.loadAll()
                recentlyDeletedItem = deleted
                showUndoBanner(deleted.title)
                reloadCountdowns(animateHeroRoll = false)
                WidgetScheduler.refreshAllWidgets(this)
            }
        )
    }

    private fun showUndoBanner(title: String) {
        mainHandler.removeCallbacks(hideUndoRunnable)
        binding.tvUndoMessage.text = "\"$title\" ${getString(R.string.deleted_banner_suffix)}"
        binding.undoBanner.visibility = View.VISIBLE
        mainHandler.postDelayed(hideUndoRunnable, 4_500L)
    }

    private fun reloadCountdowns(
        animateHeroRoll: Boolean,
        forceReloadFromDisk: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        allItems = store.loadAll(now, forceReload = forceReloadFromDisk)
        heroItem = CountdownStore.selectHeroItem(allItems, now)
        updateHeroCountdown(now, animateRoll = animateHeroRoll)
        refreshListOnly(now)
        updateToolbarLabels()
    }

    private fun refreshListOnly(now: Long = System.currentTimeMillis()) {
        val filtered = CountdownStore.filterAndSort(allItems, activeFilter, sortMode, now)
        countdownAdapter.submitList(filtered)
        binding.cardEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvCountdowns.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateToolbarLabels() {
        binding.tvHoursModeLabel.text = if (showTotalHoursMode) "Hours" else "Days"
        binding.tvSortModeLabel.text = sortMode.label
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

        binding.btnWidgetsStudio.background = ThemeManager.pillDrawable(this, palette)
        binding.ivWidgetsIcon.setColorFilter(palette.accent)
        binding.tvWidgetsButtonLabel.setTextColor(palette.textPrimary)

        binding.btnThemePicker.background = ThemeManager.pillDrawable(this, palette)
        binding.ivPaletteIcon.setColorFilter(palette.accent)
        binding.tvThemeButtonLabel.setTextColor(palette.textPrimary)

        // Year Pulse Card
        binding.cardYearPulse.background = ThemeManager.cardDrawable(this, palette, isHero = true)
        binding.tvYearPulseTitle.setTextColor(palette.accent)
        binding.tvDayOfYearBadge.background = ThemeManager.tagDrawable(this, palette)
        binding.tvDayOfYearBadge.setTextColor(palette.accent)
        binding.btnShareYearPulse.background = ThemeManager.pillDrawable(this, palette)
        binding.btnShareYearPulse.setColorFilter(palette.accent)
        binding.tvYearPrimaryValue.setTextColor(palette.textPrimary)
        binding.tvYearPrimaryUnit.setTextColor(palette.textSecondary)
        binding.tvYearSecondaryCaption.setTextColor(palette.textSecondary)
        binding.tvYearLiveFooter.setTextColor(palette.textSecondary)
        binding.ivSwapHint.setColorFilter(palette.textTertiary)
        binding.tvTapToCycle.setTextColor(palette.textTertiary)

        // Hero Countdown Card
        binding.cardHeroCountdown.background = ThemeManager.cardDrawable(this, palette, isHero = true)
        binding.tvHeroBadge.setTextColor(palette.accent)
        binding.tvHeroHoursBadge.background = ThemeManager.tagDrawable(this, palette)
        binding.tvHeroHoursBadge.setTextColor(palette.accent)
        binding.btnEditHero.background = ThemeManager.pillDrawable(this, palette)
        binding.btnEditHero.setColorFilter(palette.textSecondary)
        binding.tvHeroTitle.setTextColor(palette.textPrimary)
        binding.tvHeroMeta.setTextColor(palette.textSecondary)

        val digitTextSizeSp = if (resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        ) 20f else 24f
        listOf(
            binding.rollingDays,
            binding.rollingHours,
            binding.rollingMinutes,
            binding.rollingSeconds
        ).forEach { roller ->
            roller.setStyle(
                bg = palette.glassPill,
                stroke = palette.glassStroke,
                text = palette.textPrimary,
                textSizeSp = digitTextSizeSp
            )
        }
        listOf(binding.tvUnitDays, binding.tvUnitHrs, binding.tvUnitMin, binding.tvUnitSec)
            .forEach { it.setTextColor(palette.textTertiary) }

        // Toolbar & Adapters
        binding.tvSectionCountdowns.setTextColor(palette.textPrimary)
        binding.btnToggleHours.background = ThemeManager.pillDrawable(this, palette)
        binding.ivHoursIcon.setColorFilter(palette.accent)
        binding.tvHoursModeLabel.setTextColor(palette.textPrimary)
        binding.btnSortMode.background = ThemeManager.pillDrawable(this, palette)
        binding.ivSortIcon.setColorFilter(palette.accent)
        binding.tvSortModeLabel.setTextColor(palette.textPrimary)

        filterChipAdapter.setState(activeFilter, palette)
        countdownAdapter.setThemePalette(palette)

        // Empty State, Undo, FAB
        binding.cardEmptyState.background = ThemeManager.cardDrawable(this, palette)
        binding.ivEmptySparkle.setColorFilter(palette.accent)
        binding.tvEmptyTitle.setTextColor(palette.textPrimary)
        binding.tvEmptyDesc.setTextColor(palette.textSecondary)

        binding.undoBanner.background = ThemeManager.sheetDrawable(this, palette)
        binding.tvUndoMessage.setTextColor(palette.textPrimary)
        binding.btnUndoDelete.setTextColor(palette.accent)
        binding.fabAddCountdown.background = ThemeManager.fabDrawable(this, palette)

        updateHeroCountdown(System.currentTimeMillis(), animateRoll = false)
    }

    private fun updateYearPulse(now: Long) {
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

    private fun updateHeroCountdown(now: Long, animateRoll: Boolean) {
        val item = heroItem
        if (item == null) {
            binding.cardHeroCountdown.visibility = View.GONE
            lastHeroId = null
            return
        }
        binding.cardHeroCountdown.visibility = View.VISIBLE

        val sameHero = item.id == lastHeroId
        lastHeroId = item.id
        val shouldAnimate = animateRoll && sameHero

        val palette = ThemeManager.getPalette(this)
        val breakdown = TimeCalculator.calculateBreakdown(item, now)
        val itemAccent = runCatching { Color.parseColor(item.accentHex) }.getOrDefault(palette.accent)

        binding.tvHeroBadge.text = when {
            breakdown.isCountUp -> "MILESTONE FOCUS"
            item.isPinned -> getString(R.string.pinned_hero_label)
            else -> "UP NEXT"
        }
        binding.tvHeroEmoji.text = item.emoji
        binding.tvHeroTitle.text = item.title

        binding.rollingDays.setValue(
            String.format(Locale.US, "%02d", breakdown.days),
            animate = shouldAnimate
        )
        binding.rollingHours.setValue(
            String.format(Locale.US, "%02d", breakdown.hours),
            animate = shouldAnimate
        )
        binding.rollingMinutes.setValue(
            String.format(Locale.US, "%02d", breakdown.minutes),
            animate = shouldAnimate
        )
        binding.rollingSeconds.setValue(
            String.format(Locale.US, "%02d", breakdown.seconds),
            animate = shouldAnimate
        )

        binding.tvHeroHoursBadge.text = when {
            breakdown.isExpired -> "Reached"
            breakdown.isCountUp -> String.format(Locale.US, "+%,d hrs", breakdown.totalHours)
            else -> String.format(Locale.US, "%,d hrs left", breakdown.totalHours)
        }

        if (breakdown.isCountUp) {
            binding.heroProgressBar.visibility = View.GONE
            binding.tvHeroMeta.text =
                "Started ${heroMetaDateFormat.format(Date(breakdown.effectiveTargetMillis))}"
        } else {
            binding.heroProgressBar.visibility = View.VISIBLE
            binding.heroProgressBar.setColors(
                track = palette.progressTrack,
                accentStart = ThemeManager.withAlpha(itemAccent, 0xCC),
                accentEnd = itemAccent
            )
            binding.heroProgressBar.setProgress(breakdown.progress)
            val pct = (breakdown.progress * 100f)
            binding.tvHeroMeta.text = String.format(
                Locale.US,
                "%s • %.0f%% elapsed",
                heroMetaDateFormat.format(Date(breakdown.effectiveTargetMillis)),
                pct
            )
        }
    }

    companion object {
        private const val KEY_METRIC_MODE = "metric_mode"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_HOURS_MODE = "hours_mode"
    }
}
