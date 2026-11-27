package com.lumetime.app.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.databinding.ItemCountdownBinding
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.RepeatRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DiffUtil-backed adapter for countdown cards with lightweight 1-second tick payloads
 * and automatic urgency color accents.
 */
class CountdownAdapter(
    private val onCardClick: (CountdownItem) -> Unit,
    private val onCardLongClick: (CountdownItem) -> Unit,
    private val onPinClick: (CountdownItem) -> Unit
) : ListAdapter<CountdownItem, CountdownAdapter.CountdownViewHolder>(DiffCallback) {

    private var palette: ThemeManager.ThemePalette? = null
    private var showTotalHoursMode: Boolean = false
    private var currentNowMillis: Long = System.currentTimeMillis()
    private val dateFormatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())

    fun setThemePalette(themePalette: ThemeManager.ThemePalette) {
        palette = themePalette
        notifyItemRangeChanged(0, itemCount)
    }

    fun setShowTotalHours(enabled: Boolean) {
        if (showTotalHoursMode != enabled) {
            showTotalHoursMode = enabled
            notifyItemRangeChanged(0, itemCount, PAYLOAD_TICK)
        }
    }

    fun refreshTick(nowMillis: Long) {
        currentNowMillis = nowMillis
        if (itemCount > 0) {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_TICK)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CountdownViewHolder {
        val binding = ItemCountdownBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CountdownViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CountdownViewHolder, position: Int) {
        holder.bind(getItem(position), fullBind = true)
    }

    override fun onBindViewHolder(
        holder: CountdownViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PAYLOAD_TICK)) {
            holder.bind(getItem(position), fullBind = false)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class CountdownViewHolder(
        private val binding: ItemCountdownBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CountdownItem, fullBind: Boolean) {
            val pal = palette ?: return
            val breakdown = TimeCalculator.calculateBreakdown(item, currentNowMillis)
            val itemAccent = parseHexOrFallback(item.accentHex, pal.accent)

            val urgencyColor = when {
                breakdown.isCountUp -> itemAccent
                breakdown.isExpired -> pal.textTertiary
                breakdown.totalHours < 24L -> pal.urgentCoral
                breakdown.days < 7L -> pal.warningAmber
                else -> itemAccent
            }

            if (fullBind) {
                val context = binding.root.context
                binding.cardRoot.background = ThemeManager.cardDrawable(context, pal, isHero = item.isPinned)
                binding.tvEmojiBadge.text = item.emoji
                binding.tvEmojiBadge.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(ThemeManager.withAlpha(itemAccent, if (pal.isDark) 0x33 else 0x22))
                    setStroke(
                        ThemeManager.dp(context, 1f).toInt().coerceAtLeast(1),
                        ThemeManager.withAlpha(itemAccent, 0x66)
                    )
                }

                binding.tvItemTitle.text = item.title
                binding.tvItemTitle.setTextColor(pal.textPrimary)

                binding.btnPinItem.setColorFilter(
                    if (item.isPinned) pal.accent else pal.textTertiary
                )
                binding.btnPinItem.alpha = if (item.isPinned) 1f else 0.55f

                FocusAnimations.attachGlassPress(binding.root)
                binding.root.setOnClickListener {
                    ThemeManager.performTapHaptic(it)
                    onCardClick(item)
                }
                binding.root.setOnLongClickListener {
                    ThemeManager.performTapHaptic(it)
                    onCardLongClick(item)
                    true
                }
                binding.btnPinItem.setOnClickListener {
                    ThemeManager.performSoftTick(it)
                    onPinClick(item)
                }
            }

            // Dynamic time + progress fields updated on every tick
            val formattedDate = dateFormatter.format(Date(breakdown.effectiveTargetMillis))
            val repeatTag = when (item.repeatRule) {
                RepeatRule.YEARLY -> " • Yearly"
                RepeatRule.MONTHLY -> " • Monthly"
                RepeatRule.NONE -> ""
            }
            val modeTag = if (breakdown.isCountUp) "Since " else ""
            binding.tvItemMeta.text = "$modeTag$formattedDate$repeatTag"
            binding.tvItemMeta.setTextColor(pal.textSecondary)

            binding.tvPrimaryMetric.setTextColor(urgencyColor)
            binding.tvSecondaryMetric.setTextColor(pal.textSecondary)

            if (breakdown.isExpired) {
                binding.tvPrimaryMetric.text = "Done"
                binding.tvSecondaryMetric.text = "Target reached"
            } else if (showTotalHoursMode) {
                val prefix = if (breakdown.isCountUp) "+" else ""
                binding.tvPrimaryMetric.text =
                    String.format(Locale.US, "%s%,dh", prefix, breakdown.totalHours)
                binding.tvSecondaryMetric.text =
                    String.format(Locale.US, "%02dm %02ds", breakdown.minutes, breakdown.seconds)
            } else {
                val prefix = if (breakdown.isCountUp) "+" else ""
                binding.tvPrimaryMetric.text =
                    String.format(Locale.US, "%s%dd", prefix, breakdown.days)
                binding.tvSecondaryMetric.text = String.format(
                    Locale.US,
                    "%02dh %02dm %02ds",
                    breakdown.hours,
                    breakdown.minutes,
                    breakdown.seconds
                )
            }

            if (breakdown.isCountUp) {
                binding.itemProgressBar.visibility = View.GONE
            } else {
                binding.itemProgressBar.visibility = View.VISIBLE
                binding.itemProgressBar.setColors(
                    track = pal.progressTrack,
                    accentStart = ThemeManager.withAlpha(urgencyColor, 0xCC),
                    accentEnd = urgencyColor
                )
                binding.itemProgressBar.setProgress(breakdown.progress)
            }
        }
    }

    private fun parseHexOrFallback(hex: String, fallback: Int): Int {
        return runCatching { Color.parseColor(hex) }.getOrDefault(fallback)
    }

    companion object {
        private const val PAYLOAD_TICK = "tick"

        private val DiffCallback = object : DiffUtil.ItemCallback<CountdownItem>() {
            override fun areItemsTheSame(oldItem: CountdownItem, newItem: CountdownItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CountdownItem, newItem: CountdownItem): Boolean =
                oldItem == newItem
        }
    }
}
