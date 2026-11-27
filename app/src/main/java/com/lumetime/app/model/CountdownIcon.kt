package com.lumetime.app.model

import com.lumetime.app.R

/**
 * Crisp SF-style monochrome vector icons replacing raw Unicode emojis across LumeTime.
 */
enum class CountdownIcon(
    val id: String,
    val label: String,
    val drawableRes: Int
) {
    HOURGLASS("hourglass", "Timer", R.drawable.ic_icon_hourglass),
    TARGET("target", "Goal", R.drawable.ic_icon_target),
    BOOK("book", "Study", R.drawable.ic_icon_book),
    CAKE("cake", "Birthday", R.drawable.ic_icon_cake),
    PLANE("plane", "Travel", R.drawable.ic_icon_plane),
    BELL("bell", "Alert", R.drawable.ic_bell),
    SPARKLE("sparkle", "Special", R.drawable.ic_sparkle),
    HEART("heart", "Love", R.drawable.ic_icon_heart),
    FLAME("flame", "Streak", R.drawable.ic_icon_flame),
    GRAD("grad", "Career", R.drawable.ic_icon_grad);

    companion object {
        private val legacyEmojiMap = mapOf(
            "⏳" to HOURGLASS,
            "🎯" to TARGET,
            "📚" to BOOK,
            "🎂" to CAKE,
            "✈️" to PLANE,
            "🔔" to BELL,
            "✨" to SPARKLE,
            "🎉" to SPARKLE,
            "❤️" to HEART,
            "🔥" to FLAME,
            "🎓" to GRAD
        )

        fun forCategory(category: CountdownCategory): CountdownIcon = when (category) {
            CountdownCategory.GENERAL -> HOURGLASS
            CountdownCategory.EXAM -> BOOK
            CountdownCategory.BIRTHDAY -> CAKE
            CountdownCategory.TRAVEL -> PLANE
            CountdownCategory.GOAL -> TARGET
            CountdownCategory.RENEWAL -> BELL
            CountdownCategory.MILESTONE -> SPARKLE
        }

        fun fromStored(raw: String?, fallbackCategory: CountdownCategory): CountdownIcon {
            if (raw.isNullOrBlank()) return forCategory(fallbackCategory)
            val trimmed = raw.trim()
            entries.firstOrNull { it.id.equals(trimmed, ignoreCase = true) }?.let { return it }
            legacyEmojiMap[trimmed]?.let { return it }
            return forCategory(fallbackCategory)
        }
    }
}
