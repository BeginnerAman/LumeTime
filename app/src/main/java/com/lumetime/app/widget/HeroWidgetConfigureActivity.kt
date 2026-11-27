package com.lumetime.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.data.CountdownStore
import com.lumetime.app.databinding.ActivityWidgetConfigureBinding
import com.lumetime.app.ui.ThemeManager
import java.util.Locale

/**
 * Liquid Glass configuration screen allowing users to bind each Hero Widget
 * instance to either Auto-Hero mode or any specific countdown/count-up item.
 */
class HeroWidgetConfigureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigureBinding
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        binding = ActivityWidgetConfigureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val baseLeft = binding.configContent.paddingLeft
        val baseTop = binding.configContent.paddingTop
        val baseRight = binding.configContent.paddingRight
        val baseBottom = binding.configContent.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.configContent.setPadding(
                baseLeft + bars.left,
                baseTop + bars.top,
                baseRight + bars.right,
                baseBottom + bars.bottom
            )
            insets
        }

        val palette = ThemeManager.getPalette(this)
        ThemeManager.applySystemBars(this, palette)
        ThemeManager.applyAmbientBackground(binding.configRoot, palette, animate = false)
        binding.configAmbientGlow.setThemePalette(palette)

        binding.tvConfigTitle.setTextColor(palette.textPrimary)
        binding.tvConfigSubtitle.setTextColor(palette.textSecondary)

        val currentTargetId = WidgetPrefs.getHeroTargetId(this, appWidgetId)

        binding.btnConfigAutoHero.background = if (currentTargetId == null) {
            ThemeManager.accentedCardDrawable(this, palette, palette.accent, isHero = false)
        } else {
            ThemeManager.pillDrawable(this, palette)
        }
        binding.btnConfigAutoHero.setTextColor(if (currentTargetId == null) palette.accent else palette.textPrimary)
        binding.btnConfigAutoHero.setOnClickListener {
            ThemeManager.performTapHaptic(it)
            finishWithSelection(null)
        }

        binding.btnConfigCancel.background = ThemeManager.pillDrawable(this, palette)
        binding.btnConfigCancel.setTextColor(palette.textSecondary)
        binding.btnConfigCancel.setOnClickListener {
            ThemeManager.performSoftTick(it)
            finish()
        }

        val now = System.currentTimeMillis()
        val items = CountdownStore.create(this).loadAll(now, forceReload = true)
        val iconSize = ThemeManager.dp(this, 20f).toInt()
        val padH = ThemeManager.dp(this, 16f).toInt()
        val padV = ThemeManager.dp(this, 13f).toInt()
        val gap = ThemeManager.dp(this, 10f).toInt()

        items.forEach { item ->
            val itemAccent = runCatching { Color.parseColor(item.accentHex) }.getOrDefault(palette.accent)
            val selected = item.id == currentTargetId
            val breakdown = TimeCalculator.calculateBreakdown(item, now)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padH, padV, padH, padV)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = gap }
                background = ThemeManager.accentedCardDrawable(
                    this@HeroWidgetConfigureActivity,
                    palette,
                    itemAccent,
                    isHero = selected
                )
            }

            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginEnd = ThemeManager.dp(this@HeroWidgetConfigureActivity, 12f).toInt()
                }
                setImageResource(item.resolveIcon().drawableRes)
                setColorFilter(itemAccent)
            }

            val titleView = TextView(this).apply {
                text = item.title
                textSize = 14f
                setTextColor(palette.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val statusView = TextView(this).apply {
                text = when {
                    breakdown.isExpired -> "Done"
                    breakdown.isCountUp -> "+${breakdown.days}d"
                    else -> String.format(Locale.US, "%dd left", breakdown.days)
                }
                textSize = 12f
                setTextColor(itemAccent)
            }

            row.addView(icon)
            row.addView(titleView)
            row.addView(statusView)
            row.setOnClickListener {
                ThemeManager.performTapHaptic(it)
                finishWithSelection(item.id)
            }
            binding.configItemsContainer.addView(row)
        }
    }

    private fun finishWithSelection(countdownId: String?) {
        WidgetPrefs.setHeroTargetId(this, appWidgetId, countdownId)
        val manager = AppWidgetManager.getInstance(this)
        HeroWidgetProvider.updateWidgets(this, manager, intArrayOf(appWidgetId))
        WidgetScheduler.scheduleNextRefresh(this)

        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, result)
        finish()
    }
}
