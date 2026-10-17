package com.abyss.app.feature.vpn

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Server reachability check: a plain TCP connect to host:port, timed. It is not
 * a full proxy handshake, but it ranks servers by how quickly they answer and
 * flags dead ones — enough to pick a fast one. Pure ranking/formatting lives in
 * [PingLogic] and is unit-tested; the socket part is here.
 */
object VpnPing {
    suspend fun tcp(host: String, port: Int, timeoutMs: Int = 2500): Int? = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { s ->
                val t0 = System.nanoTime()
                s.connect(InetSocketAddress(host, port), timeoutMs)
                ((System.nanoTime() - t0) / 1_000_000L).toInt().coerceAtLeast(1)
            }
        }.getOrNull()
    }

    /** Ping many servers at once (bounded), returning id → latency ms or null. */
    suspend fun measureAll(servers: List<VpnServer>, timeoutMs: Int = 2500): Map<String, Int?> = coroutineScope {
        servers.map { s -> async { s.id to tcp(s.host, s.port, timeoutMs) } }.awaitAll().toMap()
    }
}

object PingLogic {
    /** Colour tier: 0 fast · 1 ok · 2 slow · -1 unreachable. */
    fun tier(ms: Int?): Int = when {
        ms == null -> -1
        ms < 150 -> 0
        ms < 350 -> 1
        else -> 2
    }

    fun label(ms: Int?): String = ms?.let { "$it ms" } ?: "—"

    /**
     * Order ids by latency: reachable fastest-first, then not-yet-tested, then
     * unreachable last. Stable for equal keys.
     */
    fun sortByLatency(ids: List<String>, latency: Map<String, Int?>): List<String> =
        ids.sortedWith(compareBy({ group(latency, it) }, { latency[it] ?: Int.MAX_VALUE }))

    private fun group(latency: Map<String, Int?>, id: String): Int = when {
        !latency.containsKey(id) -> 1       // untested
        latency[id] == null -> 2            // unreachable
        else -> 0                           // reachable
    }
}
