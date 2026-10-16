package com.abyss.app.feature.vpn

import android.util.Log

/**
 * JNI bridge to the native VPN engine (libabyss_vpn.so = Xray-core built with
 * gomobile + a tun2socks forwarder). Built by CI only with -PwithVpn, so the
 * default APK always builds. When absent, [available] is false and the VPN UI
 * says the engine isn't in this build.
 *
 * The native side runs Xray with the given config (a local SOCKS inbound) and
 * forwards the TUN file descriptor into that SOCKS. It protects its own sockets
 * from the VPN loop via the [AbyssVpnService.protectFd] callback.
 */
object VpnBridge {
    val available: Boolean = try {
        System.loadLibrary("abyss_vpn")
        true
    } catch (t: Throwable) {
        Log.i("Abyss", "VPN native engine not present: ${t.message}")
        false
    }

    external fun nativeStart(configPath: String, tunFd: Int, socksPort: Int, mtu: Int): Boolean
    external fun nativeStop()

    fun start(configPath: String, tunFd: Int, socksPort: Int, mtu: Int): Boolean =
        if (available) runCatching { nativeStart(configPath, tunFd, socksPort, mtu) }.getOrDefault(false)
        else false

    fun stop() { if (available) runCatching { nativeStop() } }
}
