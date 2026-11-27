package com.lumetime.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownIconTest {

    @Test
    fun `fromStored resolves icon ids and migrates legacy emojis cleanly`() {
        assertEquals(CountdownIcon.BOOK, CountdownIcon.fromStored("book", CountdownCategory.GENERAL))
        assertEquals(CountdownIcon.PLANE, CountdownIcon.fromStored("✈️", CountdownCategory.GENERAL))
        assertEquals(CountdownIcon.SPARKLE, CountdownIcon.fromStored("🎉", CountdownCategory.GENERAL))
        assertEquals(CountdownIcon.FLAME, CountdownIcon.fromStored("🔥", CountdownCategory.GENERAL))
        assertEquals(CountdownIcon.CAKE, CountdownIcon.fromStored("", CountdownCategory.BIRTHDAY))
        assertEquals(CountdownIcon.TARGET, CountdownIcon.fromStored("unknown", CountdownCategory.GOAL))
    }
}
