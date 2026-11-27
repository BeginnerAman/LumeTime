package com.lumetime.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.data.CountdownStore
import com.lumetime.app.databinding.ItemCountdownBinding
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.CountdownMode
import com.lumetime.app.model.RepeatRule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DiffUtil-backed adapter for countdown cards featuring mini circular progress rings,
 * crisp SF-style vector icons, optional notes, and 4 display unit formats.
 */
class CountdownAdapter(
    private val onCardClick: (CountdownItem) -> Unit,
    private val onCardLongClick: (CountdownItem) -> Unit,
    private val onPinClick: (CountdownItem) -> Unit,
    private val onCheckInClick: (CountdownItem) -> Unit = {}
) : ListAdapter<CountdownItem, CountdownAdapter.CountdownViewHolder>(DiffCallback) {

    private var palette: ThemeManager.ThemePalette? = null
    private var displayFormat: CountdownDisplayFormat = CountdownDisplayFormat.DAYS_HOURS
    private var currentNowMillis: Long = System.currentTimeMillis()
    private val dateFormatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())

    fun setThemePalette(themePalette: ThemeManager.ThemePalette) {
        palette = themePalette
        notifyItemRangeChanged(0, itemCount)
    }

    fun setDisplayFormat(format: CountdownDisplayFormat) {
        if (displayFormat != format) {
            displayFormat = format
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
        binding.ringItemProgress.setStrokeWidthDp(3.2f)
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
            val displayColor = if (breakdown.isExpired) pal.textTertiary else itemAccent

            if (fullBind) {
                val context = binding.root.context
                binding.cardRoot.background = ThemeManager.accentedCardDrawable(
                    context = context,
                    palette = pal,
                    accentColor = itemAccent,
                    isHero = item.isPinned
                )

                val icon = item.resolveIcon()
                binding.ivItemIcon.setImageResource(icon.drawableRes)
                binding.ivItemIcon.setColorFilter(displayColor)

                binding.tvItemTitle.text = item.title
                binding.tvItemTitle.setTextColor(pal.textPrimary)

                if (item.note.isNotBlank()) {
                    binding.tvItemNote.visibility = View.VISIBLE
                    binding.tvItemNote.text = item.note
                    binding.tvItemNote.setTextColor(pal.textSecondary)
                } else {
                    binding.tvItemNote.visibility = View.GONE
                }

                binding.btnPinItem.setColorFilter(
                    if (item.isPinned) itemAccent else pal.textTertiary
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

                if (item.mode == CountdownMode.COUNT_UP) {
                    val todayKey = CountdownStore.todayDayKey(currentNowMillis)
                    val checkedToday = item.lastCheckInDayKey == todayKey
                    binding.btnCheckIn.visibility = View.VISIBLE
                    binding.btnCheckIn.text = when {
                        checkedToday && item.checkInCount > 0 -> "Checked • ${item.checkInCount}d"
                        item.checkInCount > 0 -> "Check In • ${item.checkInCount}d"
                        else -> "Check In Today"
                    }
                    binding.btnCheckIn.background = if (checkedToday) {
                        ThemeManager.tintedTagDrawable(context, pal, itemAccent)
                    } else {
                        ThemeManager.pillDrawable(context, pal)
                    }
                    binding.btnCheckIn.setTextColor(if (checkedToday) itemAccent else pal.textPrimary)
                    binding.btnCheckIn.setOnClickListener {
                        ThemeManager.performTapHaptic(it)
                        onCheckInClick(item)
                    }
                } else {
                    binding.btnCheckIn.visibility = View.GONE
                }
            }

            // Optional urgency badge without overriding user's chosen accent color
            when {
                breakdown.isCountUp -> binding.tvUrgencyTag.visibility = View.GONE
                breakdown.isExpired -> {
                    binding.tvUrgencyTag.visibility = View.VISIBLE
                    binding.tvUrgencyTag.text = "DONE"
                    binding.tvUrgencyTag.background = ThemeManager.tagDrawable(binding.root.context, pal)
                    binding.tvUrgencyTag.setTextColor(pal.textTertiary)
                }
                breakdown.totalHours < 24L -> {
                    binding.tvUrgencyTag.visibility = View.VISIBLE
                    binding.tvUrgencyTag.text = "URGENT"
                    binding.tvUrgencyTag.background = ThemeManager.tintedTagDrawable(
                        binding.root.context,
                        pal,
                        pal.urgentCoral
                    )
                    binding.tvUrgencyTag.setTextColor(pal.urgentCoral)
                }
                breakdown.days < 7L -> {
                    binding.tvUrgencyTag.visibility = View.VISIBLE
                    binding.tvUrgencyTag.text = "SOON"
                    binding.tvUrgencyTag.background = ThemeManager.tintedTagDrawable(
                        binding.root.context,
                        pal,
                        pal.warningAmber
                    )
                    binding.tvUrgencyTag.setTextColor(pal.warningAmber)
                }
                else -> binding.tvUrgencyTag.visibility = View.GONE
            }

            // Dynamic ring progress + metric updated smoothly on tick
            binding.ringItemProgress.setColors(
                track = pal.progressTrack,
                accentStart = ThemeManager.withAlpha(displayColor, 0xCC),
                accentEnd = displayColor
            )
            binding.ringItemProgress.setProgress(
                if (breakdown.isCountUp) 1f else breakdown.progress
            )
            binding.ivItemIcon.setColorFilter(displayColor)

            val formattedDate = dateFormatter.format(Date(breakdown.effectiveTargetMillis))
            val repeatTag = when (item.repeatRule) {
                RepeatRule.YEARLY -> " • Yearly"
                RepeatRule.MONTHLY -> " • Monthly"
                RepeatRule.NONE -> ""
            }
            val modeTag = if (breakdown.isCountUp) "Since " else ""
            binding.tvItemMeta.text = "$modeTag$formattedDate$repeatTag"
            binding.tvItemMeta.setTextColor(pal.textTertiary)

            val formatted = CountdownDisplayFormatter.format(breakdown, displayFormat)
            binding.tvPrimaryMetric.setTextColor(displayColor)
            binding.tvPrimaryMetric.text = formatted.primary
            binding.tvSecondaryMetric.setTextColor(pal.textSecondary)
            binding.tvSecondaryMetric.text = formatted.secondary
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
