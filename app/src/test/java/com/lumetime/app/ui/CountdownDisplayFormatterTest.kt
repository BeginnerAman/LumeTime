package com.lumetime.app.ui

import com.lumetime.app.core.TimeCalculator
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.CountdownMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownDisplayFormatterTest {

    @Test
    fun `format renders all 4 display formats and cycles cleanly`() {
        var fmt = CountdownDisplayFormat.DAYS_HOURS
        fmt = fmt.next()
        assertEquals(CountdownDisplayFormat.TOTAL_HOURS, fmt)
        fmt = fmt.next()
        assertEquals(CountdownDisplayFormat.WEEKS_DAYS, fmt)
        fmt = fmt.next()
        assertEquals(CountdownDisplayFormat.MONTHS_DAYS, fmt)
        fmt = fmt.next()
        assertEquals(CountdownDisplayFormat.DAYS_HOURS, fmt)

        val now = 1_795_000_000_000L
        val target = now + 40L * TimeCalculator.MILLIS_PER_DAY + 6L * TimeCalculator.MILLIS_PER_HOUR +
            15L * TimeCalculator.MILLIS_PER_MINUTE + 9L * TimeCalculator.MILLIS_PER_SECOND
        val item = CountdownItem("f1", "Trip", target, now)
        val bd = TimeCalculator.calculateBreakdown(item, now)

        val daysHrs = CountdownDisplayFormatter.format(bd, CountdownDisplayFormat.DAYS_HOURS)
        assertEquals("40d", daysHrs.primary)
        assertEquals("06h 15m 09s", daysHrs.secondary)

        val totalHrs = CountdownDisplayFormatter.format(bd, CountdownDisplayFormat.TOTAL_HOURS)
        assertEquals("966h", totalHrs.primary)
        assertEquals("15m 09s", totalHrs.secondary)

        val weeksDays = CountdownDisplayFormatter.format(bd, CountdownDisplayFormat.WEEKS_DAYS)
        assertEquals("5w 5d", weeksDays.primary)

        val monthsDays = CountdownDisplayFormatter.format(bd, CountdownDisplayFormat.MONTHS_DAYS)
        assertEquals("1m 10d", monthsDays.primary)
    }

    @Test
    fun `format handles count-up prefix and expired state`() {
        val now = 1_795_000_000_000L
        val streak = CountdownItem(
            "s1",
            "Streak",
            now - 10L * TimeCalculator.MILLIS_PER_DAY,
            now - 10L * TimeCalculator.MILLIS_PER_DAY,
            mode = CountdownMode.COUNT_UP
        )
        val up = CountdownDisplayFormatter.format(
            TimeCalculator.calculateBreakdown(streak, now),
            CountdownDisplayFormat.DAYS_HOURS
        )
        assertEquals("+10d", up.primary)

        val past = CountdownItem("e1", "Past", now - 5_000L, now - 10_000L)
        val expired = CountdownDisplayFormatter.format(
            TimeCalculator.calculateBreakdown(past, now),
            CountdownDisplayFormat.TOTAL_HOURS
        )
        assertEquals("Done", expired.primary)
    }
}
