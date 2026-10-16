package com.abyss.app.feature.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.abyss.app.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

sealed class VpnStatus {
    object Disconnected : VpnStatus()
    object Connecting : VpnStatus()
    data class Connected(val label: String) : VpnStatus()
    data class Error(val message: String) : VpnStatus()
}

/** App-wide VPN state + start/stop entry points. */
object VpnController {
    private val _status = MutableStateFlow<VpnStatus>(VpnStatus.Disconnected)
    val status: StateFlow<VpnStatus> = _status.asStateFlow()
    internal fun set(s: VpnStatus) { _status.value = s }

    fun connect(context: Context, server: VpnServer) {
        _status.value = VpnStatus.Connecting
        val cfg = XrayConfig.build(server)
        val i = Intent(context, AbyssVpnService::class.java).apply {
            action = AbyssVpnService.ACTION_START
            putExtra(AbyssVpnService.EXTRA_CONFIG, cfg)
            putExtra(AbyssVpnService.EXTRA_LABEL, server.label())
        }
        ContextCompat.startForegroundService(context, i)
    }

    fun disconnect(context: Context) {
        val i = Intent(context, AbyssVpnService::class.java).apply { action = AbyssVpnService.ACTION_STOP }
        ContextCompat.startForegroundService(context, i)
    }
}

class AbyssVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(
                intent.getStringExtra(EXTRA_CONFIG).orEmpty(),
                intent.getStringExtra(EXTRA_LABEL).orEmpty())
            ACTION_STOP -> stopAll()
        }
        return START_STICKY
    }

    private fun start(config: String, label: String) {
        instance = this
        if (!VpnBridge.available) {
            VpnController.set(VpnStatus.Error(com.abyss.app.ui.i18n.I18n.current.vpnErrNoEngine))
            stopSelf(); return
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification(label),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notification(label))
        }

        val fd = establishTun()
        if (fd == null) { VpnController.set(VpnStatus.Error(com.abyss.app.ui.i18n.I18n.current.vpnErrTunnel)); stopSelf(); return }
        tun = fd

        val cfgFile = File(filesDir, "xray_config.json").apply { writeText(config) }
        val ok = VpnBridge.start(cfgFile.absolutePath, fd.fd, SOCKS_PORT, MTU)
        if (ok) VpnController.set(VpnStatus.Connected(label))
        else { VpnController.set(VpnStatus.Error(com.abyss.app.ui.i18n.I18n.current.vpnErrStart)); stopAll() }
    }

    private fun establishTun(): ParcelFileDescriptor? = runCatching {
        Builder()
            .setSession("Abyss")
            .setMtu(MTU)
            .addAddress("10.1.10.1", 30)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .addRoute("0.0.0.0", 0)
            .establish()
    }.getOrNull()

    private fun stopAll() {
        VpnBridge.stop()
        runCatching { tun?.close() }; tun = null
        VpnController.set(VpnStatus.Disconnected)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() { stopAll() }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    private fun notification(label: String): android.app.Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "VPN", NotificationManager.IMPORTANCE_LOW))
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Abyss VPN")
            .setContentText(com.abyss.app.ui.i18n.I18n.current.vpnConnected(label))
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    companion object {
        const val ACTION_START = "com.abyss.app.VPN_START"
        const val ACTION_STOP = "com.abyss.app.VPN_STOP"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_LABEL = "label"
        const val SOCKS_PORT = 10808
        const val MTU = 1500
        const val CHANNEL = "abyss_vpn"
        const val NOTIF_ID = 4201

        @Volatile private var instance: AbyssVpnService? = null

        /** Called from the native engine to keep its sockets outside the tunnel. */
        @JvmStatic
        fun protectFd(fd: Int): Boolean = instance?.protect(fd) ?: false
    }
}
