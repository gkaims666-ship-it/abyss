package com.abyss.app.feature.radar

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.abyss.app.core.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps watching the public air-raid alert status for the user's own region
 * while the app is closed, and warns with a vibration + a heads-up pop-up the
 * moment an alert is declared or lifted. No sound (the user asked for silent).
 *
 * It only ever reads the official region-level alert feed and shows which
 * region is under alert — never any object, position or trajectory.
 */
class AlertService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        ensureChannels()
        startForegroundStatus(getString(com_abyss_watching()))
        if (loop == null || loop?.isActive != true) loop = scope.launch { run() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun run() {
        val prefs = getSharedPreferences("radar", Context.MODE_PRIVATE)
        var regionAt = 0L
        var names = RegionNames.load(prefs)
        while (scope.isActive) {
            val token = runCatching { ServiceLocator.keyStore.getKey("alertsua") }.getOrNull() ?: ""
            if (token.isBlank()) { updateStatus(getString(R_need_token())); delay(60_000); continue }

            val now = System.currentTimeMillis()
            val lat = prefs.getFloat("alert_lat", Float.NaN)
            val lon = prefs.getFloat("alert_lon", Float.NaN)
            if (!lat.isNaN() && !lon.isNaN() && now - regionAt > 10 * 60_000L) {
                val r = runCatching { GeoApi.regionOf(lat.toDouble(), lon.toDouble()) }.getOrNull()
                if (r != null) { names = RegionNames.of(r); names.save(prefs); regionAt = now }
            }

            val alerts = runCatching { SafetyApi.activeAlerts(token) }.getOrNull()
            if (alerts == null) { updateStatus(getString(R_offline())); delay(45_000); continue }
            val mine = AlertLogic.matchRegion(names.state, names.county, names.municipality, names.city, names.country, alerts)

            val prevLevel = prefs.getString("alert_last_level", "none") ?: "none"
            val curLevel = mine?.level?.name?.lowercase() ?: "none"
            if (curLevel != prevLevel) {
                val label = names.label ?: mine?.title ?: ""
                if (mine != null && prevLevel == "none") {
                    notifyAlert(mine, label)
                    prefs.edit().putLong("alert_started", now).apply()
                } else if (mine == null && prevLevel != "none") {
                    notifyClear(label)
                    appendHistory(prefs, prevLevel, label, prefs.getLong("alert_started", now), now)
                } else if (mine != null) {
                    notifyAlert(mine, label)   // level changed (e.g. yellow → red)
                }
                prefs.edit()
                    .putString("alert_last_level", curLevel)
                    .putString("alert_last_kind", mine?.kind?.name ?: "")
                    .apply()
            }
            updateStatus(statusText(names.label, mine, alerts.size))
            delay(45_000)
        }
    }

    private fun appendHistory(prefs: android.content.SharedPreferences, level: String, region: String, start: Long, end: Long) {
        val kind = prefs.getString("alert_last_kind", "") ?: ""
        val list = AlertHistory.decodeAll(prefs.getString("alert_history", null)) +
            AlertEvent(kind, level, region, start, end)
        prefs.edit().putString("alert_history", AlertHistory.encodeAll(list)).apply()
    }

    // --------------------------------------------------------------- notifications

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_STATUS, "Alert monitoring", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Air-raid alert", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 700)
            setSound(null, null)            // vibration + pop-up only, no sound
            setBypassDnd(true)
            lightColor = 0xFFFF0000.toInt()
            enableLights(true)
        })
    }

    private fun contentIntent(): PendingIntent {
        val i = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().setClassName(packageName, "$packageName.ui.MainActivity")
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun stopPending(): PendingIntent {
        val i = Intent(this, AlertService::class.java).setAction(ACTION_STOP)
        return PendingIntent.getService(this, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun startForegroundStatus(text: String) {
        val n = statusNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(ID_STATUS, n)
    }

    private fun statusNotification(text: String): Notification =
        NotificationCompat.Builder(this, CH_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(com_abyss_watching()))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(contentIntent())
            .addAction(0, getString(R_stop()), stopPending())
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    private fun updateStatus(text: String) {
        if (!canNotify()) return
        NotificationManagerCompat.from(this).notify(ID_STATUS, statusNotification(text))
    }

    private fun notifyAlert(a: RegionAlert, label: String) {
        if (!canNotify()) return
        val title = kindTitle(a.kind) + if (a.level == AlertLevel.Yellow) "  ·  " + getString(R_possible()) else ""
        val body = buildString {
            append(label)
            if (a.threats.isNotEmpty()) { append("\n"); append(a.threats.joinToString(" · ")) }
        }
        val n = NotificationCompat.Builder(this, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(label)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(if (a.level == AlertLevel.Red) 0xFFD32F2F.toInt() else 0xFFF5A623.toInt())
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 700))
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        NotificationManagerCompat.from(this).notify(ID_ALERT, n)
    }

    private fun notifyClear(label: String) {
        if (!canNotify()) return
        val n = NotificationCompat.Builder(this, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R_allclear()))
            .setContentText(label)
            .setColor(0xFF2E7D32.toInt())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(longArrayOf(0, 200, 150, 200))
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        NotificationManagerCompat.from(this).notify(ID_ALERT, n)
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun statusText(label: String?, mine: RegionAlert?, total: Int): String = when {
        mine != null -> kindTitle(mine.kind) + " · " + (label ?: mine.title)
        label != null -> getString(R_calm()) + " · " + label
        else -> getString(R_active_total()) + " " + total
    }

    // Locale-aware strings via the app's own i18n (so the service matches the UI language).
    private val S get() = com.abyss.app.ui.i18n.I18n.current
    private fun kindTitle(k: AlertKind) = when (k) {
        AlertKind.AirRaid -> S.alertAirRaid; AlertKind.Artillery -> S.alertArtillery; AlertKind.UrbanFights -> S.alertUrbanFights
        AlertKind.Chemical -> S.alertChemical; AlertKind.Nuclear -> S.alertNuclear; AlertKind.Other -> S.alertOther
    }
    private fun getString(v: String) = v
    private fun com_abyss_watching() = S.safetyWatching
    private fun R_need_token() = S.safetyNeedToken
    private fun R_offline() = S.radarOffline
    private fun R_stop() = S.safetyStopWatch
    private fun R_allclear() = S.safetyAllClear
    private fun R_calm() = S.safetyNoAlert
    private fun R_possible() = S.safetyPossible
    private fun R_active_total() = S.safetyActive

    companion object {
        const val ACTION_STOP = "com.abyss.app.ALERT_STOP"
        private const val CH_STATUS = "abyss_alert_status"
        private const val CH_ALERT = "abyss_alert"
        private const val ID_STATUS = 7701
        private const val ID_ALERT = 7702

        fun start(ctx: Context) {
            val i = Intent(ctx, AlertService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, AlertService::class.java).setAction(ACTION_STOP))
        }
    }
}

/** The user's region names, persisted so the service can match without the UI. */
data class RegionNames(
    val state: String?, val county: String?, val municipality: String?, val city: String?,
    val country: String?, val label: String?,
) {
    fun save(p: android.content.SharedPreferences) {
        p.edit()
            .putString("alert_r_state", state).putString("alert_r_county", county)
            .putString("alert_r_muni", municipality).putString("alert_r_city", city)
            .putString("alert_r_country", country).putString("alert_r_label", label)
            .apply()
    }
    companion object {
        fun load(p: android.content.SharedPreferences) = RegionNames(
            p.getString("alert_r_state", null), p.getString("alert_r_county", null),
            p.getString("alert_r_muni", null), p.getString("alert_r_city", null),
            p.getString("alert_r_country", null), p.getString("alert_r_label", null),
        )
        fun of(r: Region) = RegionNames(
            r.state, r.county, r.municipality, r.city, r.country,
            r.city ?: r.municipality ?: r.county ?: r.state,
        )
    }
}
