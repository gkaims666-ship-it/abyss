package com.abyss.app.feature.vpn

import android.net.Uri
import android.util.Base64
import com.abyss.app.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.net.URI
import java.net.URLDecoder

/**
 * Parses proxy share links and subscriptions (the same formats Happ and
 * v2ray-style clients use): vmess, vless, trojan, shadowsocks, and a
 * subscription URL whose body is a (often base64-encoded) list of such links.
 */
object SubscriptionParser {

    fun decodeBase64(s: String): String = runCatching {
        String(Base64.decode(s.trim().replace('-', '+').replace('_', '/'), Base64.DEFAULT))
    }.getOrDefault("")

    /** Parse a block of text that may be base64 or plain newline-separated links. */
    fun parseMany(body: String): List<VpnServer> {
        val text = if (body.contains("://")) body else decodeBase64(body).ifBlank { body }
        return text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            .mapNotNull { runCatching { parseLink(it) }.getOrNull() }
    }

    suspend fun fetchSubscription(url: String): List<VpnServer> = withContext(Dispatchers.IO) {
        runCatching {
            Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                parseMany(r.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    fun parseLink(link: String): VpnServer? = when {
        link.startsWith("vmess://") -> parseVmess(link)
        link.startsWith("vless://") -> parseStd(link, VpnProtocol.VLESS)
        link.startsWith("trojan://") -> parseStd(link, VpnProtocol.TROJAN)
        link.startsWith("ss://") -> parseSs(link)
        else -> null
    }

    private fun id(seed: String) = seed.hashCode().toUInt().toString(16)

    private fun parseVmess(link: String): VpnServer? {
        val json = decodeBase64(link.removePrefix("vmess://"))
        if (json.isBlank()) return null
        val o = Http.json.parseToJsonElement(json).jsonObject
        fun s(k: String) = o[k]?.jsonPrimitive?.content ?: ""
        val host = s("add"); val port = s("port").toIntOrNull() ?: return null
        val params = mapOf(
            "network" to s("net").ifBlank { "tcp" },
            "headerType" to s("type"),
            "host" to s("host"),
            "path" to s("path"),
            "security" to s("tls"),
            "sni" to s("sni"),
            "encryption" to s("scy").ifBlank { "auto" },
            "alterId" to s("aid").ifBlank { "0" },
        )
        return VpnServer(id("vmess$host$port"), s("ps"), VpnProtocol.VMESS, host, port, s("id"), params)
    }

    private fun parseStd(link: String, proto: VpnProtocol): VpnServer? {
        val uri = URI(link)
        val host = uri.host ?: return null
        val port = uri.port.takeIf { it > 0 } ?: return null
        val secret = uri.userInfo ?: return null
        val q = parseQuery(uri.rawQuery)
        val remark = uri.fragment?.let { URLDecoder.decode(it, "UTF-8") } ?: ""
        val params = mapOf(
            "network" to (q["type"] ?: "tcp"),
            "security" to (q["security"] ?: ""),
            "sni" to (q["sni"] ?: q["peer"] ?: ""),
            "fp" to (q["fp"] ?: ""),
            "flow" to (q["flow"] ?: ""),
            "path" to (q["path"] ?: ""),
            "host" to (q["host"] ?: ""),
            "serviceName" to (q["serviceName"] ?: ""),
            "pbk" to (q["pbk"] ?: ""),
            "sid" to (q["sid"] ?: ""),
            "alpn" to (q["alpn"] ?: ""),
        )
        return VpnServer(id("$proto$host$port$secret"), remark, proto, host, port, secret, params)
    }

    private fun parseSs(link: String): VpnServer? {
        val body = link.removePrefix("ss://")
        val hashIdx = body.indexOf('#')
        val remark = if (hashIdx >= 0) URLDecoder.decode(body.substring(hashIdx + 1), "UTF-8") else ""
        val main = (if (hashIdx >= 0) body.substring(0, hashIdx) else body).substringBefore('?')
        // SIP002: base64(method:pass)@host:port  — or legacy base64(method:pass@host:port)
        val (method, pass, host, port) = if (main.contains('@')) {
            val userinfo = decodeBase64(main.substringBefore('@')).ifBlank { main.substringBefore('@') }
            val hp = main.substringAfter('@')
            val m = userinfo.substringBefore(':'); val p = userinfo.substringAfter(':')
            Quad(m, p, hp.substringBeforeLast(':'), hp.substringAfterLast(':').toIntOrNull() ?: return null)
        } else {
            val dec = decodeBase64(main)
            val at = dec.lastIndexOf('@'); if (at < 0) return null
            val ui = dec.substring(0, at); val hp = dec.substring(at + 1)
            Quad(ui.substringBefore(':'), ui.substringAfter(':'),
                hp.substringBeforeLast(':'), hp.substringAfterLast(':').toIntOrNull() ?: return null)
        }
        return VpnServer(id("ss$host$port"), remark, VpnProtocol.SHADOWSOCKS, host, port, pass,
            mapOf("method" to method))
    }

    private data class Quad(val a: String, val b: String, val c: String, val d: Int)

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&').mapNotNull {
            val i = it.indexOf('='); if (i < 0) null else
                URLDecoder.decode(it.substring(0, i), "UTF-8") to URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }.toMap()
    }
}
