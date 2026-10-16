package com.abyss.app.feature.vpn

import kotlinx.serialization.Serializable

enum class VpnProtocol { VMESS, VLESS, TROJAN, SHADOWSOCKS }

/**
 * One proxy server, parsed from a share link or subscription. [secret] holds
 * the UUID (vmess/vless) or password (trojan/ss). [params] carries transport
 * and security options (network, tls, sni, ws path, reality keys, ss method…).
 */
@Serializable
data class VpnServer(
    val id: String,
    val remark: String,
    val protocol: VpnProtocol,
    val host: String,
    val port: Int,
    val secret: String,
    val params: Map<String, String> = emptyMap(),
) {
    fun label(): String = remark.ifBlank { "$host:$port" }
}
