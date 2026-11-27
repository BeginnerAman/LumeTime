package com.lumetime.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTimerEngineTest {

    @Test
    fun `start pause resume and complete timer preserve exact remaining duration`() {
        var state = FocusTimerSnapshot()
        val t0 = 1_000_000L

        state = FocusTimerEngine.toggleTimer(state, t0)
        assertTrue(state.isTimerRunning)
        assertEquals(t0 + 25L * 60_000L, state.timerTargetEndMillis)

        // Advance 10 minutes
        val t1 = t0 + 10L * 60_000L
        assertEquals(15L * 60_000L, FocusTimerEngine.remainingTimerMillis(state, t1))
        assertEquals(0.4f, FocusTimerEngine.timerProgressFraction(state, t1), 0.001f)
        assertFalse(FocusTimerEngine.isTimerCompleted(state, t1))

        // Pause at t1
        state = FocusTimerEngine.toggleTimer(state, t1)
        assertFalse(state.isTimerRunning)
        assertEquals(15L * 60_000L, state.timerRemainingMillis)

        // Time passes while paused -> remaining stays 15 minutes
        val t2 = t1 + 30L * 60_000L
        assertEquals(15L * 60_000L, FocusTimerEngine.remainingTimerMillis(state, t2))

        // Resume at t2 and advance past end
        state = FocusTimerEngine.toggleTimer(state, t2)
        val tEnd = t2 + 15L * 60_000L
        assertTrue(FocusTimerEngine.isTimerCompleted(state, tEnd))
        assertEquals(0L, FocusTimerEngine.remainingTimerMillis(state, tEnd + 5_000L))
    }

    @Test
    fun `preset and plus or minus minute adjustments stay bounded cleanly`() {
        var state = FocusTimerSnapshot()
        state = FocusTimerEngine.selectPreset(state, FocusTimerEngine.SHORT_BREAK_MILLIS)
        assertEquals(5L * 60_000L, state.timerDurationMillis)

        state = FocusTimerEngine.adjustMinutes(state, -10, 0L)
        assertEquals(60_000L, state.timerDurationMillis)

        state = FocusTimerEngine.adjustMinutes(state, 5, 0L)
        assertEquals(6L * 60_000L, state.timerDurationMillis)
        assertEquals("06:00", FocusTimerEngine.formatMmSs(state.timerRemainingMillis))
    }

    @Test
    fun `stopwatch tracks elapsed across pause resume and records laps in newest-first order`() {
        var state = FocusTimerSnapshot()
        val t0 = 50_000L

        state = FocusTimerEngine.toggleStopwatch(state, t0)
        state = FocusTimerEngine.recordLap(state, t0 + 12_400L)
        assertEquals(1, state.lapsMillis.size)
        assertEquals(12_400L, state.lapsMillis.first())

        // Pause at +20s, sleep 100s, resume, advance 5.5s -> total 25.5s
        state = FocusTimerEngine.toggleStopwatch(state, t0 + 20_000L)
        assertEquals(20_000L, FocusTimerEngine.elapsedStopwatchMillis(state, t0 + 999_000L))

        val tResume = t0 + 120_000L
        state = FocusTimerEngine.toggleStopwatch(state, tResume)
        state = FocusTimerEngine.recordLap(state, tResume + 5_500L)
        assertEquals(25_500L, state.lapsMillis.first())
        assertEquals("00:25.5", FocusTimerEngine.formatStopwatch(25_500L))

        state = FocusTimerEngine.resetStopwatch(state)
        assertFalse(state.isStopwatchRunning)
        assertEquals(0L, state.stopwatchAccumulatedMillis)
        assertTrue(state.lapsMillis.isEmpty())
    }
}
