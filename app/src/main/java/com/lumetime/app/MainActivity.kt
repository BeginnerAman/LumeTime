package com.lumetime.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.lumetime.app.core.FocusTimerEngine
import com.lumetime.app.core.PrecisionClockTicker
import com.lumetime.app.core.ReminderScheduler
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.core.TimerAlertManager
import com.lumetime.app.data.CountdownFilter
import com.lumetime.app.data.CountdownSortMode
import com.lumetime.app.data.CountdownStore
import com.lumetime.app.data.FocusTimerStore
import com.lumetime.app.data.PersonalProfilePrefs
import com.lumetime.app.databinding.ActivityMainBinding
import com.lumetime.app.model.CountdownCategory
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.TimePulseScope
import com.lumetime.app.ui.BackupDialog
import com.lumetime.app.ui.CountdownAdapter
import com.lumetime.app.ui.CountdownDisplayFormat
import com.lumetime.app.ui.CountdownEditorDialog
import com.lumetime.app.ui.DateCalculatorDialog
import com.lumetime.app.ui.FilterChipAdapter
import com.lumetime.app.ui.FocusAnimations
import com.lumetime.app.ui.FocusTimerDialog
import com.lumetime.app.ui.PersonalizeDialog
import com.lumetime.app.ui.ShareCardGenerator
import com.lumetime.app.ui.ThemeManager
import com.lumetime.app.ui.ThemePickerDialog
import com.lumetime.app.ui.WidgetStudioDialog
import com.lumetime.app.ui.WorldClockDialog
import com.lumetime.app.ui.YearDisplayFormatter
import com.lumetime.app.ui.YearPulseMetricMode
import com.lumetime.app.widget.WidgetScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "Notification permission is needed to show countdown alerts",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val payload = store.exportJson()
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "Backup exported", Toast.LENGTH_SHORT).show()
        }.onFailure {
            Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            }.orEmpty()
            val count = store.importJson(raw)
            if (count > 0) {
                reloadCountdowns(animateHeroRoll = false, forceReloadFromDisk = true)
                WidgetScheduler.refreshAllWidgets(this)
                ReminderScheduler.rescheduleAll(this)
                Toast.makeText(this, "Restored $count countdown(s)", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No countdowns found in file", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            Toast.makeText(this, "Could not read backup file", Toast.LENGTH_SHORT).show()
        }
    }

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
    private var displayFormat: CountdownDisplayFormat = CountdownDisplayFormat.DAYS_HOURS
    private var metricMode: YearPulseMetricMode = YearPulseMetricMode.DAYS_LEFT
    private var activePulseScope: TimePulseScope = TimePulseScope.YEAR
    private var yearDotsExpanded: Boolean = false
    private var searchVisible: Boolean = false
    private var searchQuery: String = ""
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
            val rawNow = System.currentTimeMillis()
            val snappedNow = PrecisionClockTicker.snapNowToSecond(rawNow)
            updateQuickToolsBar(rawNow)
            updateYearPulse(snappedNow)
            updateHeroCountdown(snappedNow, animateRoll = true)
            countdownAdapter.refreshTick(snappedNow)

            val delay = PrecisionClockTicker.nextMidpointDelayMillis(System.currentTimeMillis())
            mainHandler.postDelayed(this, delay)
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
            val savedFormat = savedInstanceState.getInt(KEY_DISPLAY_FORMAT, 0)
            displayFormat = CountdownDisplayFormat.entries.getOrElse(savedFormat) {
                CountdownDisplayFormat.DAYS_HOURS
            }
            val savedScope = savedInstanceState.getInt(KEY_PULSE_SCOPE, TimePulseScope.YEAR.ordinal)
            activePulseScope = TimePulseScope.entries.getOrElse(savedScope) {
                TimePulseScope.YEAR
            }
            yearDotsExpanded = savedInstanceState.getBoolean(KEY_DOTS_EXPANDED, false)
            searchVisible = savedInstanceState.getBoolean(KEY_SEARCH_VISIBLE, false)
            searchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY, "")
        } else {
            val savedScopeName = getSharedPreferences("lumetime_ui_prefs", MODE_PRIVATE)
                .getString(KEY_PULSE_SCOPE, TimePulseScope.YEAR.name)
            activePulseScope = TimePulseScope.entries.firstOrNull { it.name == savedScopeName }
                ?: TimePulseScope.YEAR
        }

        store = CountdownStore.create(this)
        captureBaseDimensions()
        setupInsets()
        setupLists()
        setupInteractions()
        applyYearDotsVisibility(animate = false)
        applySearchVisibility(animate = false)
        if (searchQuery.isNotEmpty()) {
            binding.etSearch.setText(searchQuery)
        }
        reloadCountdowns(animateHeroRoll = false)
        applyTheme(animate = false)
    }

    override fun onStart() {
        super.onStart()
        reloadCountdowns(animateHeroRoll = false, forceReloadFromDisk = true)
        binding.ambientGlowView.startBreathing()
        if (yearDotsExpanded) {
            binding.yearDotMatrix.startPulse()
        }
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
        outState.putInt(KEY_PULSE_SCOPE, activePulseScope.ordinal)
        outState.putInt(KEY_SORT_MODE, sortMode.ordinal)
        outState.putInt(KEY_DISPLAY_FORMAT, displayFormat.ordinal)
        outState.putBoolean(KEY_DOTS_EXPANDED, yearDotsExpanded)
        outState.putBoolean(KEY_SEARCH_VISIBLE, searchVisible)
        outState.putString(KEY_SEARCH_QUERY, searchQuery)
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
            },
            onCheckInClick = { item ->
                allItems = store.checkInMilestone(item.id)
                reloadCountdowns(animateHeroRoll = false)
            }
        )
        countdownAdapter.setDisplayFormat(displayFormat)
        binding.rvCountdowns.layoutManager = LinearLayoutManager(this)
        binding.rvCountdowns.adapter = countdownAdapter
        binding.rvCountdowns.itemAnimator = null
    }

    private fun setupInteractions() {
        FocusAnimations.attachGlassPress(binding.btnSearchToggle, 0.92f)
        FocusAnimations.attachGlassPress(binding.btnBackup, 0.92f)
        FocusAnimations.attachGlassPress(binding.btnWidgetsStudio, 0.92f)
        FocusAnimations.attachGlassPress(binding.btnThemePicker, 0.92f)
        FocusAnimations.attachGlassPress(binding.btnFocusTimerTool, 0.96f)
        FocusAnimations.attachGlassPress(binding.btnDateCalcTool, 0.96f)
        FocusAnimations.attachGlassPress(binding.btnWorldClockTool, 0.96f)
        FocusAnimations.attachGlassPress(binding.cardYearPulse, 0.985f)
        FocusAnimations.attachGlassPress(binding.cardHeroCountdown, 0.985f)
        FocusAnimations.attachGlassPress(binding.fabAddCountdown, 0.94f)

        binding.btnSearchToggle.setOnClickListener { view ->
            ThemeManager.performSoftTick(view)
            searchVisible = !searchVisible
            if (!searchVisible) {
                searchQuery = ""
                binding.etSearch.setText("")
            }
            applySearchVisibility(animate = true)
            if (searchVisible) {
                binding.etSearch.requestFocus()
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val next = s?.toString().orEmpty()
                binding.btnClearSearch.visibility = if (next.isEmpty()) View.GONE else View.VISIBLE
                if (searchQuery != next) {
                    searchQuery = next
                    refreshListOnly()
                }
            }
        })

        binding.btnClearSearch.setOnClickListener { view ->
            ThemeManager.performSoftTick(view)
            binding.etSearch.setText("")
        }

        binding.btnToggleYearDots.setOnClickListener { view ->
            ThemeManager.performSoftTick(view)
            yearDotsExpanded = !yearDotsExpanded
            applyYearDotsVisibility(animate = true)
        }

        binding.btnBackup.setOnClickListener { view ->
            ThemeManager.performTapHaptic(view)
            openPersonalizeSheet()
        }

        binding.greetingHeaderContainer.setOnClickListener { view ->
            ThemeManager.performSoftTick(view)
            openPersonalizeSheet()
        }

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

        binding.btnFocusTimerTool.setOnClickListener { view ->
            ThemeManager.performTapHaptic(view)
            FocusTimerDialog.show(
                context = this,
                palette = ThemeManager.getPalette(this),
                onStateChanged = {
                    val now = System.currentTimeMillis()
                    updateQuickToolsBar(now)
                    updatePersonalizedHeader(now)
                },
                onRequestNotificationPermission = { ensureNotificationPermission() }
            )
        }

        binding.cardLiveFocusBanner.setOnClickListener { view ->
            ThemeManager.performSoftTick(view)
            FocusTimerDialog.show(
                context = this,
                palette = ThemeManager.getPalette(this),
                onStateChanged = {
                    val now = System.currentTimeMillis()
                    updateQuickToolsBar(now)
                    updatePersonalizedHeader(now)
                },
                onRequestNotificationPermission = { ensureNotificationPermission() }
            )
        }

        binding.btnLiveSessionPrimary.setOnClickListener { view ->
            ThemeManager.performTapHaptic(view)
            val now = System.currentTimeMillis()
            var snap = FocusTimerStore.load(this)
            snap = when {
                !snap.completedAcknowledged && snap.timerRemainingMillis == 0L -> {
                    snap.copy(
                        timerRemainingMillis = snap.timerDurationMillis,
                        timerTargetEndMillis = 0L,
                        isTimerRunning = false,
                        completedLogged = false,
                        completedAcknowledged = true
                    )
                }
                snap.activeTab == com.lumetime.app.core.FocusToolTab.STOPWATCH &&
                    (snap.isStopwatchRunning || snap.stopwatchAccumulatedMillis > 0L) ->
                    FocusTimerEngine.toggleStopwatch(snap, now)
                else -> {
                    val toggled = FocusTimerEngine.toggleTimer(snap, now)
                    if (toggled.isTimerRunning) ensureNotificationPermission()
                    toggled
                }
            }
            FocusTimerStore.save(this, snap)
            TimerAlertManager.cancelFocusNotification(this)
            TimerAlertManager.syncFocusAlarm(this, snap)
            updateQuickToolsBar(now)
        }

        binding.btnLiveSessionSecondary.setOnClickListener { view ->
            ThemeManager.performSoftTick(view)
            val now = System.currentTimeMillis()
            var snap = FocusTimerStore.load(this)
            snap = if (!snap.completedAcknowledged && snap.timerRemainingMillis == 0L) {
                ensureNotificationPermission()
                val breakSnap = FocusTimerEngine.selectPreset(
                    snap.copy(sessionLabel = "Short Break"),
                    FocusTimerEngine.SHORT_BREAK_MILLIS
                )
                FocusTimerEngine.toggleTimer(breakSnap, now)
            } else if (snap.isTimerRunning || snap.timerRemainingMillis < snap.timerDurationMillis) {
                FocusTimerEngine.adjustMinutes(snap, 5, now)
            } else {
                FocusTimerEngine.recordLap(snap, now)
            }
            FocusTimerStore.save(this, snap)
            TimerAlertManager.cancelFocusNotification(this)
            TimerAlertManager.syncFocusAlarm(this, snap)
            updateQuickToolsBar(now)
        }

        binding.btnLiveSessionDismiss.setOnClickListener { view ->
            ThemeManager.performSoftTick(view)
            val now = System.currentTimeMillis()
            var snap = FocusTimerStore.load(this)
            snap = if (snap.isStopwatchRunning || snap.stopwatchAccumulatedMillis > 0L) {
                FocusTimerEngine.resetStopwatch(snap)
            } else {
                FocusTimerEngine.resetTimer(snap)
            }
            FocusTimerStore.save(this, snap)
            TimerAlertManager.cancelFocusNotification(this)
            TimerAlertManager.cancelFocusTimerAlarm(this)
            updateQuickToolsBar(now)
        }

        binding.btnDateCalcTool.setOnClickListener { view ->
            ThemeManager.performTapHaptic(view)
            DateCalculatorDialog.show(this, ThemeManager.getPalette(this)) { targetMillis, suggestedTitle ->
                openEditor(null, prefillTargetMillis = targetMillis, prefillTitle = suggestedTitle)
            }
        }

        binding.btnWorldClockTool.setOnClickListener { view ->
            ThemeManager.performTapHaptic(view)
            WorldClockDialog.show(this, ThemeManager.getPalette(this))
        }

        binding.cardYearPulse.setOnClickListener { view ->
            ThemeManager.performSoftTick(view)
            metricMode = metricMode.next()
            animateMetricSwitch()
            updateYearPulse(System.currentTimeMillis())
        }

        listOf(
            binding.btnScopeToday to TimePulseScope.TODAY,
            binding.btnScopeWeek to TimePulseScope.WEEK,
            binding.btnScopeMonth to TimePulseScope.MONTH,
            binding.btnScopeYear to TimePulseScope.YEAR
        ).forEach { (btn, scope) ->
            btn.setOnClickListener { view ->
                if (activePulseScope == scope) return@setOnClickListener
                ThemeManager.performSoftTick(view)
                activePulseScope = scope
                getSharedPreferences("lumetime_ui_prefs", MODE_PRIVATE)
                    .edit()
                    .putString(KEY_PULSE_SCOPE, scope.name)
                    .apply()
                stylePulseScopeButtons(ThemeManager.getPalette(this))
                animateMetricSwitch()
                updateYearPulse(System.currentTimeMillis())
            }
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
            displayFormat = displayFormat.next()
            countdownAdapter.setDisplayFormat(displayFormat)
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
            ReminderScheduler.scheduleForItem(this, restore)
            reloadCountdowns(animateHeroRoll = false)
            WidgetScheduler.refreshAllWidgets(this)
        }
    }

    private fun openPersonalizeSheet() {
        val palette = ThemeManager.getPalette(this)
        PersonalizeDialog.show(
            context = this,
            palette = palette,
            onSaved = {
                val now = System.currentTimeMillis()
                updatePersonalizedHeader(now)
                updateYearPulse(now)
            },
            onOpenBackup = {
                BackupDialog.show(
                    context = this,
                    palette = ThemeManager.getPalette(this),
                    countdownCount = allItems.size,
                    onExportClick = { exportBackupLauncher.launch("lumetime_backup.json") },
                    onRestoreClick = {
                        importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    }
                )
            }
        )
    }

    private fun openEditor(
        existing: CountdownItem?,
        prefillTargetMillis: Long? = null,
        prefillTitle: String? = null
    ) {
        val palette = ThemeManager.getPalette(this)
        CountdownEditorDialog.show(
            context = this,
            palette = palette,
            existing = existing,
            prefillTargetMillis = prefillTargetMillis,
            prefillTitle = prefillTitle,
            onSave = { saved ->
                allItems = store.upsert(saved)
                ReminderScheduler.scheduleForItem(this, saved)
                reloadCountdowns(animateHeroRoll = false)
                WidgetScheduler.refreshAllWidgets(this)
            },
            onDelete = { deleted ->
                store.delete(deleted.id)
                ReminderScheduler.cancelForItem(this, deleted.id)
                allItems = store.loadAll()
                recentlyDeletedItem = deleted
                showUndoBanner(deleted.title)
                reloadCountdowns(animateHeroRoll = false)
                WidgetScheduler.refreshAllWidgets(this)
            },
            onResetStreak = { milestone ->
                allItems = store.resetMilestone(milestone.id)
                reloadCountdowns(animateHeroRoll = false)
                WidgetScheduler.refreshAllWidgets(this)
                Toast.makeText(this, "Streak restarted from now", Toast.LENGTH_SHORT).show()
            },
            onRequestNotificationPermission = { ensureNotificationPermission() }
        )
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
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

    private fun applyYearDotsVisibility(animate: Boolean) {
        if (animate) {
            TransitionManager.beginDelayedTransition(binding.contentColumn)
        }
        binding.yearDotMatrix.visibility = if (yearDotsExpanded) View.VISIBLE else View.GONE
        binding.btnToggleYearDots.text = getString(
            if (yearDotsExpanded) R.string.dots_hide else R.string.dots_show
        )
        if (yearDotsExpanded) {
            binding.yearDotMatrix.startPulse()
        } else {
            binding.yearDotMatrix.stopPulse()
        }
    }

    private fun applySearchVisibility(animate: Boolean) {
        if (animate) {
            TransitionManager.beginDelayedTransition(binding.contentColumn)
        }
        binding.searchPill.visibility = if (searchVisible) View.VISIBLE else View.GONE
    }

    private fun refreshListOnly(now: Long = System.currentTimeMillis()) {
        val filtered = CountdownStore.filterAndSort(
            allItems,
            activeFilter,
            sortMode,
            now,
            searchQuery
        )
        countdownAdapter.submitList(filtered)
        binding.cardEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvCountdowns.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateToolbarLabels() {
        binding.tvHoursModeLabel.text = displayFormat.shortLabel
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

        binding.btnSearchToggle.background = ThemeManager.pillDrawable(this, palette)
        binding.btnSearchToggle.setColorFilter(palette.accent)

        binding.btnBackup.background = ThemeManager.pillDrawable(this, palette)
        binding.btnBackup.setColorFilter(palette.accent)

        binding.btnWidgetsStudio.background = ThemeManager.pillDrawable(this, palette)
        binding.btnWidgetsStudio.setColorFilter(palette.accent)

        binding.btnThemePicker.background = ThemeManager.pillDrawable(this, palette)
        binding.viewThemeSwatch.background = ThemeManager.swatchOrbDrawable(this, palette.theme)

        binding.btnFocusTimerTool.background = ThemeManager.pillDrawable(this, palette)
        binding.ivFocusToolIcon.setColorFilter(palette.accent)
        binding.tvFocusToolLabel.setTextColor(palette.textPrimary)

        binding.btnDateCalcTool.background = ThemeManager.pillDrawable(this, palette)
        binding.ivDateCalcToolIcon.setColorFilter(palette.accent)
        binding.tvDateCalcToolLabel.setTextColor(palette.textPrimary)

        binding.btnWorldClockTool.background = ThemeManager.pillDrawable(this, palette)
        binding.ivWorldClockToolIcon.setColorFilter(palette.accent)
        binding.tvWorldClockToolLabel.setTextColor(palette.textPrimary)
        updateQuickToolsBar(System.currentTimeMillis())

        binding.searchPill.background = ThemeManager.pillDrawable(this, palette)
        binding.ivSearchPillIcon.setColorFilter(palette.accent)
        binding.etSearch.setTextColor(palette.textPrimary)
        binding.etSearch.setHintTextColor(palette.textTertiary)
        binding.btnClearSearch.setColorFilter(palette.textSecondary)

        // Year Pulse Card
        binding.cardYearPulse.background = ThemeManager.cardDrawable(this, palette, isHero = true)
        binding.tvYearPulseTitle.setTextColor(palette.accent)
        binding.tvDayOfYearBadge.background = ThemeManager.tagDrawable(this, palette)
        binding.tvDayOfYearBadge.setTextColor(palette.accent)
        binding.btnToggleYearDots.background = ThemeManager.pillDrawable(this, palette)
        binding.btnToggleYearDots.setTextColor(palette.accent)
        binding.btnShareYearPulse.background = ThemeManager.pillDrawable(this, palette)
        binding.btnShareYearPulse.setColorFilter(palette.accent)
        stylePulseScopeButtons(palette)
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

        binding.undoBanner.background = ThemeManager.cardDrawable(this, palette, isHero = true)
        binding.tvUndoMessage.setTextColor(palette.textPrimary)
        binding.btnUndoDelete.setTextColor(palette.accent)
        binding.fabAddCountdown.background = ThemeManager.fabDrawable(this, palette)

        updateHeroCountdown(System.currentTimeMillis(), animateRoll = false)
    }

    private fun updatePersonalizedHeader(now: Long) {
        val profile = PersonalProfilePrefs.load(this, now)
        val greeting = PersonalProfilePrefs.buildGreeting(
            PersonalProfilePrefs.currentHour(now),
            profile.userName
        )
        binding.tvBrandTitle.text = greeting
        val dateText = headerDateFormat.format(Date(now))
        binding.tvTodayDate.text = if (profile.dailyIntention.isNotBlank()) {
            "${profile.dailyIntention} • ${profile.focusedMinutesToday}/${profile.dailyFocusGoalMinutes}m"
        } else if (profile.focusedMinutesToday > 0) {
            "$dateText • Focused ${profile.focusedMinutesToday}/${profile.dailyFocusGoalMinutes}m"
        } else {
            "$dateText • Goal ${profile.dailyFocusGoalMinutes}m"
        }
    }

    private fun updateQuickToolsBar(now: Long) {
        var timerState = FocusTimerStore.load(this)
        val palette = ThemeManager.getPalette(this)

        // Detect completion even when FocusTimerDialog bottom sheet is closed!
        if (timerState.isTimerRunning && FocusTimerEngine.isTimerCompleted(timerState, now)) {
            val mins = ((timerState.timerDurationMillis + 30_000L) / 60_000L).toInt().coerceAtLeast(1)
            if (!timerState.completedLogged) {
                PersonalProfilePrefs.recordFocusSession(this, mins, now)
                updatePersonalizedHeader(now)
            }
            timerState = timerState.copy(
                isTimerRunning = false,
                timerRemainingMillis = 0L,
                timerTargetEndMillis = 0L,
                completedLogged = true,
                completedAcknowledged = false
            )
            FocusTimerStore.save(this, timerState)
            TimerAlertManager.cancelFocusTimerAlarm(this)
            TimerAlertManager.playCompletionChimeAndVibrate(this)
        }

        if (timerState.isTimerRunning) {
            val rem = FocusTimerEngine.remainingTimerMillis(timerState, now)
            binding.tvFocusToolLabel.text = "Focus ${FocusTimerEngine.formatMmSs(rem)}"
            binding.btnFocusTimerTool.background = ThemeManager.tintedTagDrawable(this, palette, palette.accent)
        } else if (timerState.isStopwatchRunning) {
            val elapsed = FocusTimerEngine.elapsedStopwatchMillis(timerState, now)
            binding.tvFocusToolLabel.text = "SW ${FocusTimerEngine.formatMmSs(elapsed)}"
            binding.btnFocusTimerTool.background = ThemeManager.tintedTagDrawable(this, palette, palette.accent)
        } else {
            binding.tvFocusToolLabel.text = getString(R.string.quick_focus_label)
            binding.btnFocusTimerTool.background = ThemeManager.pillDrawable(this, palette)
        }

        val timerPausedMid = !timerState.isTimerRunning &&
            timerState.timerRemainingMillis in 1L until timerState.timerDurationMillis
        val justFinished = !timerState.completedAcknowledged && timerState.timerRemainingMillis == 0L
        val stopwatchActive = timerState.isStopwatchRunning || timerState.stopwatchAccumulatedMillis > 0L

        if (timerState.isTimerRunning || timerPausedMid || justFinished || stopwatchActive) {
            binding.cardLiveFocusBanner.visibility = View.VISIBLE
            val bannerAccent = if (justFinished) palette.urgentCoral else palette.accent
            binding.cardLiveFocusBanner.background = ThemeManager.accentedCardDrawable(
                this,
                palette,
                bannerAccent,
                isHero = false
            )
            binding.tvLiveSessionStatus.setTextColor(bannerAccent)
            binding.tvLiveSessionTime.setTextColor(palette.textPrimary)
            binding.btnLiveSessionSecondary.background = ThemeManager.pillDrawable(this, palette)
            binding.btnLiveSessionSecondary.setTextColor(palette.accent)
            binding.btnLiveSessionDismiss.background = ThemeManager.pillDrawable(this, palette)
            binding.btnLiveSessionDismiss.setTextColor(palette.textSecondary)
            binding.btnLiveSessionPrimary.background = ThemeManager.fabDrawable(this, palette)
            binding.pbLiveSession.setColors(
                track = palette.progressTrack,
                accentStart = ThemeManager.withAlpha(bannerAccent, 0xCC),
                accentEnd = bannerAccent
            )

            when {
                justFinished -> {
                    binding.tvLiveSessionStatus.text = "TIME'S UP • ${timerState.sessionLabel.uppercase(Locale.US)} COMPLETE"
                    binding.tvLiveSessionTime.text = "00:00"
                    binding.btnLiveSessionPrimary.text = "Log & OK"
                    binding.btnLiveSessionSecondary.text = "5m Break"
                    binding.btnLiveSessionDismiss.text = "Reset"
                    binding.pbLiveSession.visibility = View.VISIBLE
                    binding.pbLiveSession.setProgress(1f)
                }
                timerState.isTimerRunning || timerPausedMid -> {
                    val rem = FocusTimerEngine.remainingTimerMillis(timerState, now)
                    val stateWord = if (timerState.isTimerRunning) "FOCUSING" else "PAUSED"
                    binding.tvLiveSessionStatus.text = "$stateWord • ${timerState.sessionLabel.uppercase(Locale.US)}"
                    binding.tvLiveSessionTime.text = FocusTimerEngine.formatMmSs(rem)
                    binding.btnLiveSessionPrimary.text = if (timerState.isTimerRunning) "Pause" else "Resume"
                    binding.btnLiveSessionSecondary.text = "+5m"
                    binding.btnLiveSessionDismiss.text = "Reset"
                    binding.pbLiveSession.visibility = View.VISIBLE
                    binding.pbLiveSession.setProgress(FocusTimerEngine.timerProgressFraction(timerState, now))
                }
                else -> {
                    val elapsed = FocusTimerEngine.elapsedStopwatchMillis(timerState, now)
                    binding.tvLiveSessionStatus.text =
                        if (timerState.isStopwatchRunning) "STOPWATCH • LIVE" else "STOPWATCH • PAUSED"
                    binding.tvLiveSessionTime.text = FocusTimerEngine.formatStopwatch(elapsed)
                    binding.btnLiveSessionPrimary.text =
                        if (timerState.isStopwatchRunning) "Pause" else "Resume"
                    binding.btnLiveSessionSecondary.text = "Lap"
                    binding.btnLiveSessionDismiss.text = "Reset"
                    binding.pbLiveSession.visibility = View.GONE
                }
            }
        } else {
            binding.cardLiveFocusBanner.visibility = View.GONE
        }
    }

    private fun stylePulseScopeButtons(palette: ThemeManager.ThemePalette) {
        binding.pulseScopeContainer.background = ThemeManager.pillDrawable(this, palette)
        listOf(
            binding.btnScopeToday to TimePulseScope.TODAY,
            binding.btnScopeWeek to TimePulseScope.WEEK,
            binding.btnScopeMonth to TimePulseScope.MONTH,
            binding.btnScopeYear to TimePulseScope.YEAR
        ).forEach { (btn, scope) ->
            val selected = activePulseScope == scope
            btn.background = if (selected) ThemeManager.activeChipDrawable(this, palette) else null
            btn.setTextColor(if (selected) Color.WHITE else palette.textSecondary)
        }
    }

    private fun updateYearPulse(now: Long) {
        updatePersonalizedHeader(now)
        if (activePulseScope == TimePulseScope.YEAR) {
            val yearProgress = TimeCalculator.computeYearProgress(now)
            val display = YearDisplayFormatter.format(yearProgress, metricMode, now)
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
        } else {
            val profile = PersonalProfilePrefs.load(this, now)
            val period = if (activePulseScope == TimePulseScope.TODAY && profile.useAwakeDayForTodayPulse) {
                TimeCalculator.computeAwakeDayProgress(now, profile.wakeHour, profile.sleepHour)
            } else {
                TimeCalculator.computePeriodProgress(activePulseScope, now)
            }
            binding.tvYearPulseTitle.text = period.title
            binding.tvDayOfYearBadge.text = period.badgeText
            if (metricMode == YearPulseMetricMode.PERCENT_DONE) {
                binding.tvYearPrimaryValue.text =
                    String.format(Locale.US, "%.1f%%", period.percentCompleted)
                binding.tvYearPrimaryUnit.text = "completed"
            } else {
                binding.tvYearPrimaryValue.text = period.summaryLeft
                binding.tvYearPrimaryUnit.text = period.unitLabel
            }
            binding.tvYearSecondaryCaption.text = period.subtitle
            binding.tvYearLiveFooter.text = period.liveFooter

            binding.yearDotMatrix.setData(period.totalUnits, period.activeUnitOneBased)
            binding.yearProgressBar.setProgress(period.progressFraction)
        }
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
        val heroTint = if (breakdown.isExpired) palette.textTertiary else itemAccent

        binding.cardHeroCountdown.background = ThemeManager.accentedCardDrawable(
            context = this,
            palette = palette,
            accentColor = itemAccent,
            isHero = true
        )
        binding.tvHeroBadge.setTextColor(heroTint)
        binding.tvHeroHoursBadge.background = ThemeManager.tintedTagDrawable(this, palette, heroTint)
        binding.tvHeroHoursBadge.setTextColor(heroTint)

        binding.tvHeroBadge.text = when {
            breakdown.isCountUp -> "MILESTONE FOCUS"
            item.isPinned -> getString(R.string.pinned_hero_label)
            else -> "UP NEXT"
        }
        val heroIcon = item.resolveIcon()
        binding.heroIconContainer.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(ThemeManager.withAlpha(itemAccent, if (palette.isDark) 0x33 else 0x22))
            setStroke(
                ThemeManager.dp(this@MainActivity, 1f).toInt().coerceAtLeast(1),
                ThemeManager.withAlpha(itemAccent, 0x66)
            )
        }
        binding.ivHeroIcon.setImageResource(heroIcon.drawableRes)
        binding.ivHeroIcon.setColorFilter(itemAccent)
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

        val stepsSummary = if (item.checklist.isNotEmpty()) {
            val done = item.checklist.count { it.done }
            " • $done/${item.checklist.size} steps"
        } else ""
        val noteSummary = if (item.note.isNotBlank()) " • ${item.note}" else ""

        if (breakdown.isCountUp) {
            binding.heroProgressBar.visibility = View.GONE
            binding.tvHeroMeta.text =
                "Started ${heroMetaDateFormat.format(Date(breakdown.effectiveTargetMillis))}$stepsSummary$noteSummary"
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
                "%s • %.0f%% elapsed%s%s",
                heroMetaDateFormat.format(Date(breakdown.effectiveTargetMillis)),
                pct,
                stepsSummary,
                noteSummary
            )
        }
    }

    companion object {
        private const val KEY_METRIC_MODE = "metric_mode"
        private const val KEY_PULSE_SCOPE = "pulse_scope"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_DISPLAY_FORMAT = "display_format"
        private const val KEY_DOTS_EXPANDED = "dots_expanded"
        private const val KEY_SEARCH_VISIBLE = "search_visible"
        private const val KEY_SEARCH_QUERY = "search_query"
    }
}
