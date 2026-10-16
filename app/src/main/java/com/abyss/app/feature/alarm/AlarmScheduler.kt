package com.abyss.app.feature.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.abyss.app.core.data.AlarmEntity
import java.util.Calendar

/**
 * Schedules alarms with [AlarmManager.setAlarmClock] (exact, survives doze,
 * shows the system alarm icon). Handles recurring days, skip-Sunday and
 * one-shot alarms.
 */
object AlarmScheduler {

    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_SNOOZE = "alarm_snooze"
    private const val ACTION_FIRE = "com.abyss.app.ALARM_FIRE"
    private const val SNOOZE_REQ_OFFSET = 1_000_000

    fun pendingIntent(context: Context, alarmId: Long, flags: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        return PendingIntent.getBroadcast(context, alarmId.toInt(), intent, flags)
    }

    fun schedule(context: Context, alarm: AlarmEntity) {
        if (!alarm.enabled) { cancel(context, alarm.id); return }
        val triggerAt = nextTriggerMillis(alarm) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = pendingIntent(context, alarm.id,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val fire = pendingIntent(context, alarm.id,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), fire)
    }

    fun snooze(context: Context, alarmId: Long, minutes: Int = 9) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_SNOOZE, true)
        }
        val pi = PendingIntent.getBroadcast(
            context, alarmId.toInt() + SNOOZE_REQ_OFFSET, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val at = System.currentTimeMillis() + minutes * 60_000L
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, pi), pi)
    }

    fun cancel(context: Context, alarmId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, alarmId,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    /** Next fire time in ms, honoring recurring days + skip-Sunday, or null. */
    fun nextTriggerMillis(alarm: AlarmEntity, from: Long = System.currentTimeMillis()): Long? {
        val days = parseDays(alarm.days)
        for (offset in 0..8) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = from
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= from) continue
            val weekday = isoWeekday(cal) // 1=Mon..7=Sun
            if (alarm.skipSunday && weekday == 7) continue
            if (days.isEmpty()) return cal.timeInMillis            // one-shot: next occurrence
            if (weekday in days) return cal.timeInMillis
        }
        return null
    }

    fun parseDays(s: String): Set<Int> =
        s.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()

    private fun isoWeekday(cal: Calendar): Int {
        // Calendar: SUNDAY=1..SATURDAY=7 -> ISO Mon=1..Sun=7
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
            else -> 7
        }
    }
}
