package com.lumetime.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.lumetime.app.data.CountdownFilter
import com.lumetime.app.databinding.ItemFilterChipBinding

/**
 * Horizontal glass filter chip bar for All / Count-Up / Categories.
 */
class FilterChipAdapter(
    private val filters: List<CountdownFilter>,
    private val onSelect: (CountdownFilter) -> Unit
) : RecyclerView.Adapter<FilterChipAdapter.ChipViewHolder>() {

    private var selectedFilter: CountdownFilter = CountdownFilter.All
    private var palette: ThemeManager.ThemePalette? = null

    fun setState(filter: CountdownFilter, themePalette: ThemeManager.ThemePalette) {
        selectedFilter = filter
        palette = themePalette
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChipViewHolder {
        val binding = ItemFilterChipBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChipViewHolder, position: Int) {
        holder.bind(filters[position])
    }

    override fun getItemCount(): Int = filters.size

    inner class ChipViewHolder(
        private val binding: ItemFilterChipBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(filter: CountdownFilter) {
            val pal = palette ?: return
            val isSelected = filter == selectedFilter
            val tv = binding.tvChip
            tv.text = filter.label
            if (isSelected) {
                tv.background = ThemeManager.activeChipDrawable(tv.context, pal)
                tv.setTextColor(Color.WHITE)
            } else {
                tv.background = ThemeManager.pillDrawable(tv.context, pal)
                tv.setTextColor(pal.textSecondary)
            }
            tv.setOnClickListener {
                ThemeManager.performSoftTick(it)
                onSelect(filter)
            }
        }
    }
}
