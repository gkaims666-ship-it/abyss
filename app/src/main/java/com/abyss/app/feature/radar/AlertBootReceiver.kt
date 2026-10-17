package com.abyss.app.feature.radar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Brings the air-raid alert watcher back after a reboot or an app update, but
 * only if the user had turned it on. A safety warning is no use if it silently
 * stops the first time the phone restarts.
 */
class AlertBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val on = context.getSharedPreferences("radar", Context.MODE_PRIVATE)
                    .getBoolean("alerts_bg", false)
                if (on) runCatching { AlertService.start(context) }
            }
        }
    }
}
