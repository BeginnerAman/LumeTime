package com.lumetime.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.lumetime.app.data.CountdownStore
import com.lumetime.app.model.CountdownItem
import com.lumetime.app.model.CountdownMode

/**
 * Zero-drain local reminder scheduler using inexact/windowed AlarmManager triggers.
 * Schedules an optional 24h-before heads-up and/or target-arrival notification.
 */
object ReminderScheduler {

    const val EXTRA_REMINDER_ID = "extra_reminder_id"
    const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
    const val EXTRA_REMINDER_EMOJI = "extra_reminder_emoji"
    const val EXTRA_REMINDER_STAGE = "extra_reminder_stage"

    const val STAGE_DAY_BEFORE = "day_before"
    const val STAGE_EVENT_TIME = "event_time"

    data class PlannedTrigger(
        val triggerAtMillis: Long,
        val stage: String
    )

    /**
     * Pure helper returning the scheduled reminder triggers strictly after [nowMillis].
     */
    fun computeTriggers(item: CountdownItem, nowMillis: Long): List<PlannedTrigger> {
        if (!item.reminderEnabled || item.mode == CountdownMode.COUNT_UP) {
            return emptyList()
        }
        val cycle = TimeCalculator.resolveTargetCycle(item, nowMillis)
        val target = cycle.effectiveTargetMillis
        if (target <= nowMillis) return emptyList()

        val triggers = ArrayList<PlannedTrigger>(2)
        val oneDayBefore = target - TimeCalculator.MILLIS_PER_DAY
        if (oneDayBefore > nowMillis + TimeCalculator.MILLIS_PER_MINUTE) {
            triggers.add(PlannedTrigger(oneDayBefore, STAGE_DAY_BEFORE))
        }
        triggers.add(PlannedTrigger(target, STAGE_EVENT_TIME))
        return triggers
    }

    fun scheduleForItem(
        context: Context,
        item: CountdownItem,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        cancelForItem(context, item.id)
        val triggers = computeTriggers(item, nowMillis)
        if (triggers.isEmpty()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        triggers.forEachIndexed { index, plan ->
            val pending = createPendingIntent(
                context = context,
                item = item,
                stage = plan.stage,
                slotOffset = index
            )
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                plan.triggerAtMillis,
                5L * TimeCalculator.MILLIS_PER_MINUTE,
                pending
            )
        }
    }

    fun cancelForItem(context: Context, itemId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        for (slot in 0..1) {
            val intent = Intent(context, ReminderReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                requestCodeFor(itemId, slot),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
    }

    fun rescheduleAll(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val items = CountdownStore.create(context).loadAll(nowMillis, forceReload = true)
        items.forEach { item ->
            if (item.reminderEnabled) {
                scheduleForItem(context, item, nowMillis)
            } else {
                cancelForItem(context, item.id)
            }
        }
    }

    private fun createPendingIntent(
        context: Context,
        item: CountdownItem,
        stage: String,
        slotOffset: Int
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, item.id)
            putExtra(EXTRA_REMINDER_TITLE, item.title)
            putExtra(EXTRA_REMINDER_EMOJI, item.emoji)
            putExtra(EXTRA_REMINDER_STAGE, stage)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(item.id, slotOffset),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCodeFor(itemId: String, slotOffset: Int): Int {
        return (itemId.hashCode() and 0x0FFFFFFF) * 2 + slotOffset + 10_000
    }
}
