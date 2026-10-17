package com.abyss.app.feature.radar

import com.abyss.app.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Live aircraft from the open ADS-B network (community receivers, no API key):
 * primary adsb.lol, fallback airplanes.live — both serve the same readsb "v2"
 * JSON. Routes (origin → destination) come from adsb.lol's route set.
 */

/** One aircraft as last reported. Units as broadcast: feet, knots, ft/min, degrees. */
data class Plane(
    val hex: String,
    val callsign: String?,
    val reg: String?,
    val type: String?,
    val desc: String?,
    val operator: String?,
    val lat: Double,
    val lon: Double,
    val altFt: Int?,
    val onGround: Boolean,
    val gsKt: Double?,
    val track: Double?,
    val vrateFpm: Int?,
    val squawk: String?,
    val emergency: String?,
    val military: Boolean,
    val category: String?,
    /** Seconds since the position was received, at fetch time. */
    val seenPos: Double,
    /** System.currentTimeMillis() when fetched. */
    val fetchedAt: Long,
) {
    /** 7500 hijack, 7600 radio failure, 7700 general emergency, or a declared emergency. */
    val isEmergency: Boolean
        get() = squawk == "7500" || squawk == "7600" || squawk == "7700" ||
            (emergency != null && emergency != "none")

    /**
     * Dead-reckoned position now: moves along the track at ground speed since
     * the last fix (capped at 45 s), so planes glide smoothly between updates.
     */
    fun positionAt(nowMs: Long, out: DoubleArray) {
        out[0] = lat; out[1] = lon
        val gs = gsKt ?: return
        val tr = track ?: return
        if (onGround && gs < 40.0) return
        val dt = ((nowMs - fetchedAt) / 1000.0 + seenPos).coerceIn(0.0, 45.0)
        val distNm = gs * dt / 3600.0
        destination(lat, lon, tr, distNm, out)
    }
}

data class Airport(val icao: String?, val iata: String?, val name: String?, val city: String?, val lat: Double, val lon: Double)

data class Route(val origin: Airport?, val destination: Airport?, val codes: String?)

class RadarException(message: String) : Exception(message)

object RadarApi {
    private val bases = listOf("https://api.adsb.lol", "https://api.airplanes.live")
    private val client: OkHttpClient = Http.client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private const val UA = "Abyss/1.0 (Android; com.abyss.app)"

    /** Aircraft within [radiusNm] (max 250) of a point. */
    suspend fun around(lat: Double, lon: Double, radiusNm: Int): List<Plane> =
        v2("/v2/point/%.4f/%.4f/%d".format(java.util.Locale.US, lat, lon, radiusNm.coerceIn(1, 250)))

    suspend fun byCallsign(cs: String) = v2("/v2/callsign/" + enc(cs.trim().uppercase()))
    suspend fun byReg(reg: String) = v2("/v2/reg/" + enc(reg.trim().uppercase()))
    suspend fun byHex(hex: String) = v2("/v2/hex/" + enc(hex.trim().lowercase()))
    suspend fun military() = v2("/v2/mil")
    suspend fun bySquawk(sq: String) = v2("/v2/sqk/" + enc(sq.trim()))

    /**
     * Global search: tries the query as a callsign, a registration and a hex
     * code; returns everything found (deduplicated).
     */
    suspend fun search(q: String): List<Plane> {
        val t = q.trim()
        if (t.length < 2) return emptyList()
        val out = LinkedHashMap<String, Plane>()
        runCatching { byCallsign(t) }.getOrNull()?.forEach { out[it.hex] = it }
        runCatching { byReg(t) }.getOrNull()?.forEach { out[it.hex] = it }
        if (t.length == 6 && t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            runCatching { byHex(t) }.getOrNull()?.forEach { out[it.hex] = it }
        }
        if (t.length == 4 && t.all { it.isDigit() }) {
            runCatching { bySquawk(t) }.getOrNull()?.forEach { out[it.hex] = it }
        }
        return out.values.toList()
    }

    /** Origin/destination for a callsign (null if unknown). */
    suspend fun route(callsign: String, lat: Double, lon: Double): Route? = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("planes", buildJsonArray {
                add(buildJsonObject {
                    put("callsign", callsign.trim().uppercase())
                    put("lat", lat)
                    put("lng", lon)
                })
            })
        }.toString()
        val req = Request.Builder()
            .url("https://api.adsb.lol/api/0/routeset")
            .header("User-Agent", UA)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val text = runCatching {
            client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
        }.getOrNull() ?: return@withContext null
        parseRoute(text)
    }

    // ---------------------------------------------------------------- internals

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private suspend fun v2(path: String): List<Plane> = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (base in bases) {
            try {
                val req = Request.Builder().url(base + path).header("User-Agent", UA).build()
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) throw RadarException("HTTP ${r.code}")
                    val text = r.body?.string() ?: throw RadarException("empty")
                    return@withContext parsePlanes(text)
                }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: RadarException("unreachable")
    }

    private fun parsePlanes(text: String): List<Plane> {
        val root = Http.json.parseToJsonElement(text).jsonObject
        val ac = (root["ac"] ?: root["aircraft"]) as? JsonArray ?: return emptyList()
        val now = System.currentTimeMillis()
        val out = ArrayList<Plane>(ac.size)
        for (el in ac) {
            val o = el as? JsonObject ?: continue
            var lat = o.d("lat")
            var lon = o.d("lon")
            if (lat == null || lon == null) {
                // Recently lost position: readsb keeps the last one here.
                val lp = o["lastPosition"] as? JsonObject
                lat = lp?.d("lat"); lon = lp?.d("lon")
            }
            if (lat == null || lon == null) continue
            val altRaw = o["alt_baro"]
            val ground = (altRaw as? JsonPrimitive)?.contentOrNull == "ground"
            val alt = if (ground) 0 else (altRaw as? JsonPrimitive)?.intOrNull ?: o.i("alt_geom")
            val db = o.i("dbFlags") ?: 0
            out += Plane(
                hex = o.s("hex")?.trim()?.lowercase() ?: continue,
                callsign = o.s("flight")?.trim()?.takeIf { it.isNotEmpty() },
                reg = o.s("r")?.trim()?.takeIf { it.isNotEmpty() },
                type = o.s("t")?.trim()?.takeIf { it.isNotEmpty() },
                desc = o.s("desc")?.trim()?.takeIf { it.isNotEmpty() },
                operator = o.s("ownOp")?.trim()?.takeIf { it.isNotEmpty() },
                lat = lat, lon = lon,
                altFt = alt,
                onGround = ground,
                gsKt = o.d("gs"),
                track = o.d("track") ?: o.d("true_heading") ?: o.d("mag_heading"),
                vrateFpm = o.i("baro_rate") ?: o.i("geom_rate"),
                squawk = o.s("squawk"),
                emergency = o.s("emergency"),
                military = (db and 1) != 0,
                category = o.s("category"),
                seenPos = o.d("seen_pos") ?: o.d("seen") ?: 0.0,
                fetchedAt = now,
            )
        }
        return out
    }

    private fun parseRoute(text: String): Route? {
        val el: JsonElement = runCatching { Http.json.parseToJsonElement(text) }.getOrNull() ?: return null
        val first = when (el) {
            is JsonArray -> el.firstOrNull() as? JsonObject
            is JsonObject -> (el["routes"] as? JsonArray)?.firstOrNull() as? JsonObject ?: el
            else -> null
        } ?: return null
        val airports = (first["_airports"] as? JsonArray)?.mapNotNull { a ->
            val o = a as? JsonObject ?: return@mapNotNull null
            val la = o.d("lat") ?: return@mapNotNull null
            val lo = o.d("lon") ?: return@mapNotNull null
            Airport(o.s("icao"), o.s("iata"), o.s("name"), o.s("location"), la, lo)
        } ?: emptyList()
        val codes = first.s("_airport_codes_iata") ?: first.s("airport_codes")
        if (airports.isEmpty() && codes == null) return null
        if (codes == "unknown") return null
        return Route(airports.firstOrNull(), airports.lastOrNull()?.takeIf { airports.size > 1 }, codes)
    }

    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.d(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.i(k: String): Int? =
        (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
}

// ---------------------------------------------------------------- geodesy

private const val EARTH_NM = 3440.065

/** Point reached from (lat, lon) after [distNm] on initial bearing [brgDeg]; written to out[0..1]. */
fun destination(lat: Double, lon: Double, brgDeg: Double, distNm: Double, out: DoubleArray) {
    val d = distNm / EARTH_NM
    val b = Math.toRadians(brgDeg)
    val p1 = Math.toRadians(lat)
    val l1 = Math.toRadians(lon)
    val p2 = asin(sin(p1) * cos(d) + cos(p1) * sin(d) * cos(b))
    val l2 = l1 + atan2(sin(b) * sin(d) * cos(p1), cos(d) - sin(p1) * sin(p2))
    out[0] = Math.toDegrees(p2)
    out[1] = (Math.toDegrees(l2) + 540.0) % 360.0 - 180.0
}

/** Great-circle distance in nautical miles. */
fun distanceNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
    val dp = p2 - p1; val dl = Math.toRadians(lon2 - lon1)
    val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * EARTH_NM * atan2(sqrt(a), sqrt(1 - a))
}

/** Point at fraction [f] along the great circle between two points; written to out[0..1]. */
fun interpolate(lat1: Double, lon1: Double, lat2: Double, lon2: Double, f: Double, out: DoubleArray) {
    val p1 = Math.toRadians(lat1); val l1 = Math.toRadians(lon1)
    val p2 = Math.toRadians(lat2); val l2 = Math.toRadians(lon2)
    val d = distanceNm(lat1, lon1, lat2, lon2) / EARTH_NM
    if (d < 1e-9) { out[0] = lat1; out[1] = lon1; return }
    val a = sin((1 - f) * d) / sin(d)
    val b = sin(f * d) / sin(d)
    val x = a * cos(p1) * cos(l1) + b * cos(p2) * cos(l2)
    val y = a * cos(p1) * sin(l1) + b * cos(p2) * sin(l2)
    val z = a * sin(p1) + b * sin(p2)
    out[0] = Math.toDegrees(atan2(z, sqrt(x * x + y * y)))
    out[1] = Math.toDegrees(atan2(y, x))
}
