package com.abyss.app.feature.vpn

import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Builds an Xray-core JSON config for a server: a local SOCKS inbound that the
 * TUN tunnel feeds into, and a protocol-specific outbound. The VPN service runs
 * Xray with this config; packets flow TUN → tun2socks → SOCKS inbound → server.
 */
object XrayConfig {

    fun build(server: VpnServer, socksPort: Int = 10808): String {
        fun p(k: String) = server.params[k].orEmpty()
        return buildJsonObject {
            putJsonObject("log") { put("loglevel", "warning") }
            putJsonArray("inbounds") {
                addJsonObject {
                    put("tag", "socks-in"); put("listen", "127.0.0.1"); put("port", socksPort)
                    put("protocol", "socks")
                    putJsonObject("settings") { put("udp", true); put("auth", "noauth") }
                    putJsonObject("sniffing") {
                        put("enabled", true)
                        putJsonArray("destOverride") { add("http"); add("tls"); add("quic") }
                    }
                }
            }
            putJsonArray("outbounds") {
                add(outbound(server, ::p))
                addJsonObject { put("tag", "direct"); put("protocol", "freedom") }
                addJsonObject { put("tag", "block"); put("protocol", "blackhole") }
            }
            putJsonObject("routing") {
                put("domainStrategy", "IPIfNonMatch")
                putJsonArray("rules") {
                    addJsonObject {
                        put("type", "field"); put("outboundTag", "direct")
                        putJsonArray("ip") { add("geoip:private") }
                    }
                }
            }
        }.toString()
    }

    private fun outbound(s: VpnServer, p: (String) -> String) = buildJsonObject {
        put("tag", "proxy")
        when (s.protocol) {
            VpnProtocol.VMESS, VpnProtocol.VLESS -> {
                put("protocol", if (s.protocol == VpnProtocol.VMESS) "vmess" else "vless")
                putJsonObject("settings") {
                    putJsonArray("vnext") {
                        addJsonObject {
                            put("address", s.host); put("port", s.port)
                            putJsonArray("users") {
                                addJsonObject {
                                    put("id", s.secret)
                                    if (s.protocol == VpnProtocol.VMESS) {
                                        put("alterId", p("alterId").toIntOrNull() ?: 0)
                                        put("security", p("encryption").ifBlank { "auto" })
                                    } else {
                                        put("encryption", "none")
                                        if (p("flow").isNotBlank()) put("flow", p("flow"))
                                    }
                                }
                            }
                        }
                    }
                }
                put("streamSettings", stream(s, p))
            }
            VpnProtocol.TROJAN -> {
                put("protocol", "trojan")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        addJsonObject { put("address", s.host); put("port", s.port); put("password", s.secret) }
                    }
                }
                put("streamSettings", stream(s, p))
            }
            VpnProtocol.SHADOWSOCKS -> {
                put("protocol", "shadowsocks")
                putJsonObject("settings") {
                    putJsonArray("servers") {
                        addJsonObject {
                            put("address", s.host); put("port", s.port)
                            put("method", p("method").ifBlank { "aes-256-gcm" })
                            put("password", s.secret)
                        }
                    }
                }
            }
        }
    }

    private fun stream(s: VpnServer, p: (String) -> String) = buildJsonObject {
        val net = p("network").ifBlank { "tcp" }
        put("network", net)
        val security = p("security")
        if (security == "tls" || security == "reality" || security == "xtls") {
            put("security", if (security == "xtls") "tls" else security)
            if (security == "reality") {
                putJsonObject("realitySettings") {
                    put("serverName", p("sni")); put("fingerprint", p("fp").ifBlank { "chrome" })
                    put("publicKey", p("pbk")); put("shortId", p("sid"))
                }
            } else {
                putJsonObject("tlsSettings") {
                    put("serverName", p("sni").ifBlank { s.host })
                    if (p("fp").isNotBlank()) put("fingerprint", p("fp"))
                    if (p("alpn").isNotBlank()) putJsonArray("alpn") {
                        p("alpn").split(',').forEach { add(it.trim()) }
                    }
                }
            }
        }
        when (net) {
            "ws" -> putJsonObject("wsSettings") {
                put("path", p("path").ifBlank { "/" })
                if (p("host").isNotBlank()) putJsonObject("headers") { put("Host", p("host")) }
            }
            "grpc" -> putJsonObject("grpcSettings") { put("serviceName", p("serviceName")) }
            "tcp" -> if (p("headerType") == "http") putJsonObject("tcpSettings") {
                putJsonObject("header") { put("type", "http") }
            }
        }
    }
}
