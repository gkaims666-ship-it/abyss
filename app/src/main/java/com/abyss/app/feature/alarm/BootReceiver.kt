package com.abyss.app.feature.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abyss.app.core.data.AbyssDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Re-arms all enabled alarms after a reboot (alarms don't survive on their own). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AbyssDb.get(context).alarmDao().all()
                    .filter { it.enabled }
                    .forEach { AlarmScheduler.schedule(context, it) }
            } finally { pending.finish() }
        }
    }
}
