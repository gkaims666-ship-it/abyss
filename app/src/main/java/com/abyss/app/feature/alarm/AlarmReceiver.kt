package com.abyss.app.feature.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.core.app.NotificationCompat
import com.abyss.app.core.data.AbyssDb
import com.abyss.app.core.data.AlarmEntity
import com.abyss.app.core.insights.UsageAnalyzer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (id < 0) return
        val snooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZE, false)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AbyssDb.get(context).alarmDao()
                val alarm = dao.byId(id) ?: if (snooze) AlarmEntity(id = id, hour = 0, minute = 0)
                    else return@launch
                if (!snooze && !alarm.enabled) return@launch

                applySmartVolume(context, alarm)
                showAlarm(context, alarm)

                if (!snooze) {
                    // Recurring: schedule next. One-shot: disable.
                    if (AlarmScheduler.parseDays(alarm.days).isEmpty()) {
                        dao.update(alarm.copy(enabled = false))
                    } else {
                        AlarmScheduler.schedule(context, alarm)
                    }
                }
            } finally { pending.finish() }
        }
    }

    private fun applySmartVolume(context: Context, alarm: AlarmEntity) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        var percent = alarm.baseVolumePercent.coerceIn(0, 100)

        if (alarm.smartVolume) {
            val weekday = isoToday()
            val workday = weekday in 1..5
            val lateNight = runCatching { UsageAnalyzer(context).wentToBedLate() }.getOrDefault(false)
            // On workdays after a late night, push volume up so it actually wakes you.
            if (workday && lateNight) percent = maxOf(percent, 95)
        }
        val level = (max * percent / 100).coerceIn(1, max)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, level, 0) }
    }

    private fun showAlarm(context: Context, alarm: AlarmEntity) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL, "Alarms", NotificationManager.IMPORTANCE_HIGH
        ).apply { setBypassDnd(true); enableVibration(true) }
        nm.createNotificationChannel(channel)

        val full = Intent(context, AlarmRingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_LABEL, alarm.label)
            putExtra(EXTRA_VIBRATE, alarm.vibrate)
        }
        val pi = PendingIntent.getActivity(
            context, alarm.id.toInt(), full,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(alarm.label.ifBlank { "Alarm" })
            .setContentText(String.format("%02d:%02d", alarm.hour, alarm.minute))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(pi, true)
            .setOngoing(true)
            .setAutoCancel(true)
            .build()
        nm.notify(alarm.id.toInt(), notif)
    }

    private fun isoToday(): Int = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
        else -> 7
    }

    companion object {
        const val CHANNEL = "abyss_alarms"
        const val EXTRA_LABEL = "alarm_label"
        const val EXTRA_VIBRATE = "alarm_vibrate"
    }
}
