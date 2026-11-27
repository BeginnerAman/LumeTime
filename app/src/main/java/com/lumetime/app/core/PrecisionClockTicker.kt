package com.lumetime.app.core

/**
 * Metronome-steady 1-second clock helper that eliminates Handler boundary jitter
 * and prevents 2-second skips or double ticks.
 *
 * Strategy:
 * - Target timestamps are normalized to exact whole seconds (:000 ms).
 * - UI ticks aim for the +500ms midpoint of each wall-clock second (e.g. 12:00:01.500),
 *   so +/- 350ms OS/frame jitter never crosses a :000ms second boundary.
 * - [snapNowToSecond] floors the instant to the current whole second for deterministic math.
 */
object PrecisionClockTicker {

    fun normalizeToWholeSecond(epochMillis: Long): Long {
        return (epochMillis / 1000L) * 1000L
    }

    fun snapNowToSecond(nowMillis: Long): Long {
        return (nowMillis / 1000L) * 1000L
    }

    /**
     * Computes delay in milliseconds until the +500ms midpoint of the next second,
     * guaranteed to stay >= 250ms so two ticks never fire in the same second.
     */
    fun nextMidpointDelayMillis(nowMillis: Long): Long {
        val mod = ((nowMillis % 1000L) + 1000L) % 1000L
        var delay = 1500L - mod
        if (delay < 250L) {
            delay += 1000L
        }
        return delay
    }
}
