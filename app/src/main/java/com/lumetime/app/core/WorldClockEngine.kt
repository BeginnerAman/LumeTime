package com.lumetime.app.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class WorldCity(
    val id: String,
    val city: String,
    val region: String,
    val zoneId: String
)

data class WorldCityClock(
    val city: WorldCity,
    val formattedTime: String,
    val formattedDate: String,
    val offsetDiffLabel: String,
    val isWorkHours: Boolean
)

/**
 * Pure Kotlin World Clock & Meeting Time-Zone Converter (API 24+ safe).
 */
object WorldClockEngine {

    val catalog: List<WorldCity> = listOf(
        WorldCity("ist", "New Delhi / IST", "India", "Asia/Kolkata"),
        WorldCity("lon", "London", "United Kingdom", "Europe/London"),
        WorldCity("nyc", "New York", "USA East", "America/New_York"),
        WorldCity("sf", "San Francisco", "USA West", "America/Los_Angeles"),
        WorldCity("dxb", "Dubai", "UAE", "Asia/Dubai"),
        WorldCity("sgp", "Singapore", "Singapore", "Asia/Singapore"),
        WorldCity("tyo", "Tokyo", "Japan", "Asia/Tokyo"),
        WorldCity("syd", "Sydney", "Australia", "Australia/Sydney"),
        WorldCity("ber", "Berlin", "Germany", "Europe/Berlin"),
        WorldCity("tor", "Toronto", "Canada", "America/Toronto")
    )

    val defaultPinnedIds: List<String> = listOf("ist", "lon", "nyc", "dxb", "tok".let { "tyo" })

    fun evaluateZones(
        cities: List<WorldCity>,
        referenceEpochMillis: Long,
        localZone: TimeZone = TimeZone.getDefault(),
        use24Hour: Boolean = false
    ): List<WorldCityClock> {
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        val localOffsetMillis = localZone.getOffset(referenceEpochMillis)

        return cities.map { city ->
            val tz = TimeZone.getTimeZone(city.zoneId)
            val timeStr = SimpleDateFormat(pattern, Locale.getDefault()).apply {
                timeZone = tz
            }.format(Date(referenceEpochMillis))
            val dateStr = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).apply {
                timeZone = tz
            }.format(Date(referenceEpochMillis))

            val hour = Calendar.getInstance(tz).apply {
                timeInMillis = referenceEpochMillis
            }.get(Calendar.HOUR_OF_DAY)
            val isWorkHours = hour in 9..17

            val diffMinutes = (tz.getOffset(referenceEpochMillis) - localOffsetMillis) / 60_000
            val offsetDiffLabel = formatOffsetDiff(diffMinutes)

            WorldCityClock(
                city = city,
                formattedTime = timeStr,
                formattedDate = dateStr,
                offsetDiffLabel = offsetDiffLabel,
                isWorkHours = isWorkHours
            )
        }
    }

    fun formatOffsetDiff(diffMinutes: Int): String {
        if (diffMinutes == 0) return "Same as local"
        val sign = if (diffMinutes > 0) "+" else "-"
        val absMins = kotlin.math.abs(diffMinutes)
        val hours = absMins / 60
        val mins = absMins % 60
        return if (mins == 0) {
            "${sign}${hours}h vs local"
        } else {
            "${sign}${hours}h ${mins}m vs local"
        }
    }
}
