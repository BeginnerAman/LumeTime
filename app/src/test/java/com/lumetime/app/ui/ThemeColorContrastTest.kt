package com.lumetime.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorContrastTest {

    private fun perceivedLuminance(color: Int): Double {
        val red = (color ushr 16) and 0xFF
        val green = (color ushr 8) and 0xFF
        val blue = color and 0xFF
        return (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0
    }

    @Test
    fun `bright pastel accents darken cleanly for white text legibility`() {
        val brightCyan = 0xFF67E8F9.toInt()
        val richCyan = ThemeManager.ensureRichFillColor(brightCyan)
        assertTrue(perceivedLuminance(richCyan) <= 0.425)
        assertEquals(0xFF, (richCyan ushr 24) and 0xFF)

        val brightAmber = 0xFFFBBF24.toInt()
        val richAmber = ThemeManager.ensureRichFillColor(brightAmber)
        assertTrue(perceivedLuminance(richAmber) <= 0.425)
        assertEquals(0xFF, (richAmber ushr 24) and 0xFF)
    }

    @Test
    fun `already rich deep accents remain unchanged`() {
        val deepIndigo = 0xFF4F46E5.toInt()
        assertEquals(deepIndigo, ThemeManager.ensureRichFillColor(deepIndigo))

        val deepOcean = 0xFF0284C7.toInt()
        assertEquals(deepOcean, ThemeManager.ensureRichFillColor(deepOcean))
    }
}
