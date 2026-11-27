package com.lumetime.app.core

import com.lumetime.app.data.CountdownJsonCodec
import com.lumetime.app.data.CountdownStore
import com.lumetime.app.model.ChecklistStep
import com.lumetime.app.model.CountdownCategory
import com.lumetime.app.model.CountdownItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar
import java.util.TimeZone

class ChecklistAndWorldClockTest {

    @Test
    fun `checklist steps serialize cleanly and toggle persistence works`() {
        val tempFile = File.createTempFile("lumetime_checklist_test", ".json").apply {
            writeText("[]")
            deleteOnExit()
        }
        val store = CountdownStore(tempFile)
        val item = CountdownItem(
            id = "exam-prep",
            title = "OS Final",
            targetEpochMillis = 1_900_000_000_000L,
            category = CountdownCategory.EXAM,
            checklist = listOf(
                ChecklistStep("s1", "Revise Deadlocks", done = false),
                ChecklistStep("s2", "Solve Mock Paper", done = true)
            )
        )
        store.upsert(item)

        val rawJson = CountdownJsonCodec.encode(store.loadAll())
        val decoded = CountdownJsonCodec.decode(rawJson).first()
        assertEquals(2, decoded.checklist.size)
        assertEquals("Revise Deadlocks", decoded.checklist[0].text)
        assertFalse(decoded.checklist[0].done)
        assertTrue(decoded.checklist[1].done)

        store.toggleChecklistStep("exam-prep", "s1")
        val reloaded = CountdownStore(tempFile).loadAll(forceReload = true).first()
        assertTrue(reloaded.checklist[0].done)
        assertTrue(reloaded.checklist[1].done)
    }

    @Test
    fun `worldClockEngine calculates exact time offsets and work-hour flags`() {
        val utc = TimeZone.getTimeZone("UTC")
        // 2026-11-27 10:00 UTC -> London 10:00 (Work), New Delhi 15:30 (Work), New York 05:00 (Not work)
        val refMillis = Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 27, 10, 0, 0)
        }.timeInMillis

        val cities = listOf(
            WorldCity("lon", "London", "UK", "Europe/London"),
            WorldCity("ist", "New Delhi", "India", "Asia/Kolkata"),
            WorldCity("nyc", "New York", "USA", "America/New_York")
        )
        val clocks = WorldClockEngine.evaluateZones(cities, refMillis, localZone = utc, use24Hour = true)

        assertEquals("10:00", clocks[0].formattedTime)
        assertEquals("Same as local", clocks[0].offsetDiffLabel)
        assertTrue(clocks[0].isWorkHours)

        assertEquals("15:30", clocks[1].formattedTime)
        assertEquals("+5h 30m vs local", clocks[1].offsetDiffLabel)
        assertTrue(clocks[1].isWorkHours)

        assertEquals("05:00", clocks[2].formattedTime)
        assertEquals("-5h vs local", clocks[2].offsetDiffLabel)
        assertFalse(clocks[2].isWorkHours)
    }
}
