package com.lumetime.app.core

import com.lumetime.app.model.CountdownCategory
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.CountdownMode
import com.lumetime.app.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSchedulerTest {

    @Test
    fun `computeTriggers schedules 24h before and event time for distant countdown`() {
        val now = 1_795_000_000_000L
        val target = now + 5L * TimeCalculator.MILLIS_PER_DAY
        val item = CountdownItem(
            id = "rem-1",
            title = "Exam",
            targetEpochMillis = target,
            startEpochMillis = now,
            category = CountdownCategory.EXAM,
            reminderEnabled = true
        )

        val triggers = ReminderScheduler.computeTriggers(item, now)
        assertEquals(2, triggers.size)
        assertEquals(ReminderScheduler.STAGE_DAY_BEFORE, triggers[0].stage)
        assertEquals(target - TimeCalculator.MILLIS_PER_DAY, triggers[0].triggerAtMillis)
        assertEquals(ReminderScheduler.STAGE_EVENT_TIME, triggers[1].stage)
        assertEquals(target, triggers[1].triggerAtMillis)
    }

    @Test
    fun `computeTriggers skips day-before when target is under 24h away`() {
        val now = 1_795_000_000_000L
        val target = now + 6L * TimeCalculator.MILLIS_PER_HOUR
        val item = CountdownItem(
            id = "rem-2",
            title = "Flight",
            targetEpochMillis = target,
            startEpochMillis = now,
            reminderEnabled = true
        )

        val triggers = ReminderScheduler.computeTriggers(item, now)
        assertEquals(1, triggers.size)
        assertEquals(ReminderScheduler.STAGE_EVENT_TIME, triggers[0].stage)
    }

    @Test
    fun `computeTriggers returns empty when disabled or count-up`() {
        val now = 1_795_000_000_000L
        val disabled = CountdownItem(
            id = "rem-3",
            title = "Disabled",
            targetEpochMillis = now + 3L * TimeCalculator.MILLIS_PER_DAY,
            reminderEnabled = false
        )
        assertTrue(ReminderScheduler.computeTriggers(disabled, now).isEmpty())

        val countUp = CountdownItem(
            id = "rem-4",
            title = "Streak",
            targetEpochMillis = now - 3L * TimeCalculator.MILLIS_PER_DAY,
            mode = CountdownMode.COUNT_UP,
            reminderEnabled = true
        )
        assertTrue(ReminderScheduler.computeTriggers(countUp, now).isEmpty())
    }

    @Test
    fun `computeTriggers advances repeating countdown to next future cycle`() {
        val now = 1_795_000_000_000L
        val pastAnchor = now - 5L * TimeCalculator.MILLIS_PER_DAY
        val item = CountdownItem(
            id = "rem-5",
            title = "Yearly Birthday",
            targetEpochMillis = pastAnchor,
            repeatRule = RepeatRule.YEARLY,
            reminderEnabled = true
        )

        val triggers = ReminderScheduler.computeTriggers(item, now)
        assertEquals(2, triggers.size)
        assertTrue(triggers[1].triggerAtMillis > now)
    }
}
