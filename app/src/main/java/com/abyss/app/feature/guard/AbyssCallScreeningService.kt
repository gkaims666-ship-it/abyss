package com.abyss.app.feature.guard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.app.NotificationCompat
import com.abyss.app.core.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Screens incoming calls. The call itself is always allowed through; for
 * numbers that aren't in the user's contacts, it runs a scam assessment and
 * posts a warning notification with short analytics.
 */
class AbyssCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // Never block — only advise. Allow the call immediately.
        respondToCall(callDetails, CallResponse.Builder().build())

        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return
        val handle = callDetails.handle
        val number = handle?.schemeSpecificPart

        CoroutineScope(Dispatchers.IO).launch {
            val settings = ServiceLocator.settings.settings.first()
            if (!settings.guardEnabled) return@launch

            val lookup = ScamLookup(applicationContext)
            if (lookup.isKnownContact(number)) return@launch // in contacts → trusted

            val result = lookup.assess(number, settings.guardLookupUrl)
            if (result.risk == ScamRisk.LOW) return@launch
            notifyWarning(result)
        }
    }

    private fun notifyWarning(result: ScamResult) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Caller warnings", NotificationManager.IMPORTANCE_HIGH))
        val title = when (result.risk) {
            ScamRisk.HIGH -> "⚠ Likely scam caller"
            else -> "⚠ Unknown caller — be cautious"
        }
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("$title: ${result.number}")
            .setContentText(result.summary())
            .setStyle(NotificationCompat.BigTextStyle().bigText(result.summary()))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(result.number.hashCode(), notif)
    }

    companion object { const val CHANNEL = "abyss_guard" }
}
