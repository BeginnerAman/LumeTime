package com.lumetime.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrecisionClockTickerTest {

    @Test
    fun `nextMidpointDelayMillis targets 500ms offset and never double-ticks`() {
        val exactMidpoint = 1_795_000_000_500L
        assertEquals(1000L, PrecisionClockTicker.nextMidpointDelayMillis(exactMidpoint))

        // 200ms late wake -> shorter delay to realign to next .500
        assertEquals(800L, PrecisionClockTicker.nextMidpointDelayMillis(1_795_000_000_700L))

        // 200ms early wake -> longer delay to realign to next .500
        assertEquals(1200L, PrecisionClockTicker.nextMidpointDelayMillis(1_795_000_000_300L))

        // Very late wake (.251 -> 1249ms; .400 -> 1100ms; .990 -> 510ms; .250 -> 1250ms)
        for (ms in 0 until 1000 step 13) {
            val delay = PrecisionClockTicker.nextMidpointDelayMillis(1_795_000_000_000L + ms)
            assertTrue("Delay $delay at ms=$ms must be >= 250ms", delay >= 250L)
            assertTrue("Delay $delay at ms=$ms must be <= 1250ms", delay <= 1250L)
        }
    }

    @Test
    fun `snapNowToSecond keeps +/- 350ms jitter inside the exact same whole second`() {
        val baseSecond = 1_795_000_005_000L
        assertEquals(baseSecond, PrecisionClockTicker.snapNowToSecond(baseSecond + 150L))
        assertEquals(baseSecond, PrecisionClockTicker.snapNowToSecond(baseSecond + 500L))
        assertEquals(baseSecond, PrecisionClockTicker.snapNowToSecond(baseSecond + 850L))
        assertEquals(baseSecond + 1000L, PrecisionClockTicker.snapNowToSecond(baseSecond + 1500L))
    }

    @Test
    fun `normalizeToWholeSecond strips sub-second milliseconds`() {
        assertEquals(
            1_795_000_005_000L,
            PrecisionClockTicker.normalizeToWholeSecond(1_795_000_005_873L)
        )
    }
}
