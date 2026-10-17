package com.abyss.app.feature.radar

import com.abyss.app.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Live aircraft from several independent, open ADS-B networks queried in
 * parallel and merged per aircraft (see [mergeAll]). Each network has its own
 * receivers (and MLAT coverage), so together they see far more than any one.
 *  - adsb.lol, airplanes.live, adsb.fi — community readsb networks ("v2" JSON);
 *  - OpenSky Network — research network (anonymous quota, so polled sparingly).
 * Registry details and backup routes come from adsbdb; primary routes from
 * adsb.lol's route set. All key-free.
 */

/** A live-position network. */
enum class RadarSource(val id: String, val label: String) {
    AdsbLol("adsblol", "adsb.lol"),
    AirplanesLive("airplaneslive", "airplanes.live"),
    AdsbFi("adsbfi", "adsb.fi"),
    OpenSky("opensky", "OpenSky Network"),
}

/** One merged fetch: the aircraft plus which networks answered. */
data class RadarSnapshot(
    val planes: List<Plane>,
    val ok: Set<RadarSource>,
    val failed: Set<RadarSource>,
    val perSource: Map<RadarSource, Int>,
)

/** Registry details for an airframe (adsbdb). */
data class AircraftInfo(
    val type: String?,
    val icaoType: String?,
    val manufacturer: String?,
    val registration: String?,
    val owner: String?,
    val ownerCountry: String?,
    val photoThumb: String?,
    val photo: String?,
)

/** Airline + route for a callsign (adsbdb). */
data class FlightInfo(val airline: String?, val airlineIata: String?, val route: Route?)

class RadarException(message: String) : Exception(message)

object RadarApi {
    private val client: OkHttpClient = Http.client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private const val UA = "Abyss/1.0 (Android; com.abyss.app)"

    // ------------------------------------------------------------ politeness

    /**
     * Minimum spacing between requests to one host. adsb.fi temporarily bans
     * IPs that exceed 1 req/s; airplanes.live asks for the same.
     */
    private val minGapMs = mapOf(
        "opendata.adsb.fi" to 1100L,
        "api.airplanes.live" to 1050L,
        "api.adsb.lol" to 250L,
        "opensky-network.org" to 1000L,
        "api.adsbdb.com" to 300L,
    )
    private val gates = HashMap<String, Mutex>()
    private val lastAt = HashMap<String, Long>()

    private suspend fun <T> gated(host: String, block: suspend () -> T): T {
        val m = synchronized(gates) { gates.getOrPut(host) { Mutex() } }
        return m.withLock {
            val gap = minGapMs[host] ?: 0L
            val wait = (synchronized(lastAt) { lastAt[host] ?: 0L }) + gap - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            try { block() } finally { synchronized(lastAt) { lastAt[host] = System.currentTimeMillis() } }
        }
    }

    // ------------------------------------------------------------ area

    // OpenSky's anonymous quota is 400 credits/day: poll it at most once a
    // minute and only for areas ≤ 100 sq° (1–2 credits), reusing the result.
    private const val OPENSKY_MIN_INTERVAL_MS = 60_000L
    private var openskyAt = 0L
    private var openskyCache: List<Plane> = emptyList()

    /**
     * Aircraft in the current view from every enabled network, merged.
     * [radiusNm] (≤ 250) for the readsb networks; the bounds for OpenSky.
     */
    suspend fun around(
        lat: Double, lon: Double, radiusNm: Int,
        north: Double, south: Double, east: Double, west: Double,
        enabled: Set<RadarSource>,
    ): RadarSnapshot = coroutineScope {
        val r = radiusNm.coerceIn(1, 250)
        val ll = String.format(Locale.US, "%.4f", lat)
        val lo = String.format(Locale.US, "%.4f", lon)
        val jobs = enabled.map { src ->
            async(Dispatchers.IO) {
                src to runCatching {
                    when (src) {
                        RadarSource.AdsbLol -> readsb(src, "https://api.adsb.lol/v2/point/$ll/$lo/$r")
                        RadarSource.AirplanesLive -> readsb(src, "https://api.airplanes.live/v2/point/$ll/$lo/$r")
                        RadarSource.AdsbFi -> readsb(src, "https://opendata.adsb.fi/api/v3/lat/$ll/lon/$lo/dist/$r")
                        RadarSource.OpenSky -> openSky(north, south, east, west)
                    }
                }
            }
        }
        val results = jobs.awaitAll()
        val ok = results.filter { it.second.isSuccess }.map { it.first }.toSet()
        val failed = results.filter { it.second.isFailure }.map { it.first }.toSet()
        val lists = results.mapNotNull { it.second.getOrNull() }
        val per = results.associate { (s, res) -> s to (res.getOrNull()?.size ?: 0) }
        if (ok.isEmpty() && failed.isNotEmpty()) throw RadarException("all sources failed")
        RadarSnapshot(mergeAll(lists, System.currentTimeMillis()), ok, failed, per)
    }

    private suspend fun openSky(north: Double, south: Double, east: Double, west: Double): List<Plane> {
        val area = (north - south) * (if (east >= west) east - west else 360 - (west - east))
        val now = System.currentTimeMillis()
        if (area > 100.0 || now - openskyAt < OPENSKY_MIN_INTERVAL_MS) {
            // Outside budget: reuse the last batch (only planes still inside the view).
            return openskyCache.filter { it.lat in south..north && inLon(it.lon, west, east) }
        }
        val url = "https://opensky-network.org/api/states/all?lamin=%.4f&lomin=%.4f&lamax=%.4f&lomax=%.4f"
            .format(Locale.US, south, west, north, east)
        val text = gated("opensky-network.org") { fetch(url) } ?: throw RadarException("opensky")
        val planes = parseOpenSky(text)
        openskyAt = now
        openskyCache = planes
        return planes
    }

    private fun inLon(lon: Double, west: Double, east: Double) =
        if (west <= east) lon in west..east else (lon >= west || lon <= east)

    // ------------------------------------------------------------ lookups

    private enum class Query { Callsign, Reg, Hex, Mil, Squawk }

    private fun queryUrl(src: RadarSource, q: Query, arg: String): String? {
        val a = enc(arg)
        return when (src) {
            RadarSource.AdsbLol -> "https://api.adsb.lol" + when (q) {
                Query.Callsign -> "/v2/callsign/$a"; Query.Reg -> "/v2/reg/$a"; Query.Hex -> "/v2/hex/$a"
                Query.Mil -> "/v2/mil"; Query.Squawk -> "/v2/sqk/$a"
            }
            RadarSource.AirplanesLive -> "https://api.airplanes.live" + when (q) {
                Query.Callsign -> "/v2/callsign/$a"; Query.Reg -> "/v2/reg/$a"; Query.Hex -> "/v2/hex/$a"
                Query.Mil -> "/v2/mil"; Query.Squawk -> "/v2/squawk/$a"
            }
            RadarSource.AdsbFi -> "https://opendata.adsb.fi/api" + when (q) {
                Query.Callsign -> "/v2/callsign/$a"; Query.Reg -> "/v2/registration/$a"; Query.Hex -> "/v2/hex/$a"
                Query.Mil -> "/v2/mil"; Query.Squawk -> "/v2/sqk/$a"
            }
            RadarSource.OpenSky -> null   // no lookup endpoints in the anonymous API
        }
    }

    /** Runs a lookup on every enabled readsb network in parallel and merges. */
    private suspend fun lookup(q: Query, arg: String, enabled: Set<RadarSource>): List<Plane> = coroutineScope {
        val jobs = enabled.mapNotNull { src ->
            val url = queryUrl(src, q, arg) ?: return@mapNotNull null
            async(Dispatchers.IO) { runCatching { readsb(src, url) }.getOrNull() }
        }
        mergeAll(jobs.awaitAll().filterNotNull(), System.currentTimeMillis())
    }

    private val readsbAll = setOf(RadarSource.AdsbLol, RadarSource.AirplanesLive, RadarSource.AdsbFi)

    suspend fun byCallsign(cs: String, en: Set<RadarSource> = readsbAll) = lookup(Query.Callsign, cs.trim().uppercase(), en)
    suspend fun byReg(reg: String, en: Set<RadarSource> = readsbAll) = lookup(Query.Reg, reg.trim().uppercase(), en)
    suspend fun byHex(hex: String, en: Set<RadarSource> = readsbAll) = lookup(Query.Hex, hex.trim().lowercase(), en)
    suspend fun military(en: Set<RadarSource> = readsbAll) = lookup(Query.Mil, "", en)
    suspend fun bySquawk(sq: String, en: Set<RadarSource> = readsbAll) = lookup(Query.Squawk, sq.trim(), en)

    /** Global search: callsign + registration (+ hex / squawk when it looks like one), merged. */
    suspend fun search(q: String, en: Set<RadarSource> = readsbAll): List<Plane> {
        val t = q.trim()
        if (t.length < 2) return emptyList()
        val lists = ArrayList<List<Plane>>()
        lists += runCatching { byCallsign(t, en) }.getOrDefault(emptyList())
        lists += runCatching { byReg(t, en) }.getOrDefault(emptyList())
        if (t.length == 6 && t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            lists += runCatching { byHex(t, en) }.getOrDefault(emptyList())
        }
        if (t.length == 4 && t.all { it.isDigit() }) {
            lists += runCatching { bySquawk(t, en) }.getOrDefault(emptyList())
        }
        return mergeAll(lists, System.currentTimeMillis())
    }

    /** Origin/destination for a callsign from adsb.lol's route set (null if unknown). */
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
            gated("api.adsb.lol") { client.newCall(req).execute().use { r -> if (r.isSuccessful) r.body?.string() else null } }
        }.getOrNull() ?: return@withContext null
        parseRoute(text)
    }

    /**
     * Registry details (+ airline and route when [callsign] is given) from
     * adsbdb. Either half may be null.
     */
    suspend fun adsbdb(hex: String, callsign: String?): Pair<AircraftInfo?, FlightInfo?> = withContext(Dispatchers.IO) {
        val cs = callsign?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        val url = "https://api.adsbdb.com/v0/aircraft/" + enc(hex.lowercase()) + (if (cs != null) "?callsign=" + enc(cs) else "")
        val text = gated("api.adsbdb.com") { fetch(url) }
        var aircraft: AircraftInfo? = null
        var flight: FlightInfo? = null
        val resp = text?.let { runCatching { Http.json.parseToJsonElement(it).jsonObject["response"] as? JsonObject }.getOrNull() }
        if (resp != null) {
            aircraft = (resp["aircraft"] as? JsonObject)?.let(::parseAircraft)
            flight = (resp["flightroute"] as? JsonObject)?.let(::parseFlight)
        }
        // Unknown airframe (404) but a callsign: the route can still be known.
        if (flight == null && cs != null) {
            val t2 = gated("api.adsbdb.com") { fetch("https://api.adsbdb.com/v0/callsign/" + enc(cs)) }
            val r2 = t2?.let { runCatching { Http.json.parseToJsonElement(it).jsonObject["response"] as? JsonObject }.getOrNull() }
            flight = (r2?.get("flightroute") as? JsonObject)?.let(::parseFlight)
        }
        aircraft to flight
    }

    // ------------------------------------------------------------ internals

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun fetch(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    }.getOrNull()

    private suspend fun readsb(src: RadarSource, url: String): List<Plane> {
        val host = url.substringAfter("://").substringBefore('/')
        val text = gated(host) {
            client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
                if (!r.isSuccessful) throw RadarException("HTTP ${r.code}")
                r.body?.string() ?: throw RadarException("empty")
            }
        }
        return parseReadsb(text, src.label)
    }

    private fun parseReadsb(text: String, label: String): List<Plane> {
        val root = Http.json.parseToJsonElement(text).jsonObject
        val ac = (root["ac"] ?: root["aircraft"]) as? JsonArray ?: return emptyList()
        val now = System.currentTimeMillis()
        val src = setOf(label)
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
                hex = o.s("hex")?.trim()?.lowercase()?.removePrefix("~") ?: continue,
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
                sources = src,
            )
        }
        return out
    }

    /** OpenSky state vectors → Plane (metres → feet, m/s → knots / ft-per-min). */
    private fun parseOpenSky(text: String): List<Plane> {
        val root = Http.json.parseToJsonElement(text).jsonObject
        val states = root["states"] as? JsonArray ?: return emptyList()
        val serverTime = (root["time"] as? JsonPrimitive)?.doubleOrNull
        val now = System.currentTimeMillis()
        val src = setOf(RadarSource.OpenSky.label)
        val out = ArrayList<Plane>(states.size)
        for (el in states) {
            val a = el as? JsonArray ?: continue
            fun at(i: Int) = a.getOrNull(i) as? JsonPrimitive
            val hex = at(0)?.contentOrNull?.trim()?.lowercase() ?: continue
            val lon = at(5)?.doubleOrNull ?: continue
            val lat = at(6)?.doubleOrNull ?: continue
            val baroM = at(7)?.doubleOrNull
            val ground = at(8)?.booleanOrNull ?: false
            val vel = at(9)?.doubleOrNull
            val vr = at(11)?.doubleOrNull
            val geoM = at(13)?.doubleOrNull
            val tPos = at(3)?.doubleOrNull
            val age = if (serverTime != null && tPos != null) (serverTime - tPos).coerceAtLeast(0.0) else 0.0
            out += Plane(
                hex = hex,
                callsign = at(1)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() },
                reg = null, type = null, desc = null, operator = null,
                lat = lat, lon = lon,
                altFt = if (ground) 0 else (baroM ?: geoM)?.let { (it * 3.28084).roundToInt() },
                onGround = ground,
                gsKt = vel?.let { it * 1.943844 },
                track = at(10)?.doubleOrNull,
                vrateFpm = vr?.let { (it * 196.8504).roundToInt() },
                squawk = at(14)?.contentOrNull,
                emergency = null,
                military = false,
                category = null,
                seenPos = age,
                fetchedAt = now,
                sources = src,
                country = at(2)?.contentOrNull,
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

    private fun parseAircraft(o: JsonObject) = AircraftInfo(
        type = o.s("type"), icaoType = o.s("icao_type"), manufacturer = o.s("manufacturer"),
        registration = o.s("registration"), owner = o.s("registered_owner"),
        ownerCountry = o.s("registered_owner_country_name"),
        photoThumb = o.s("url_photo_thumbnail"), photo = o.s("url_photo"),
    )

    private fun parseFlight(o: JsonObject): FlightInfo {
        fun ap(x: JsonElement?): Airport? {
            val a = x as? JsonObject ?: return null
            val la = a.d("latitude") ?: return null
            val lo = a.d("longitude") ?: return null
            return Airport(a.s("icao_code"), a.s("iata_code"), a.s("name"), a.s("municipality"), la, lo)
        }
        val airline = o["airline"] as? JsonObject
        val origin = ap(o["origin"])
        val dest = ap(o["destination"])
        val route = if (origin != null || dest != null)
            Route(origin, dest, listOfNotNull(origin?.iata ?: origin?.icao, dest?.iata ?: dest?.icao).joinToString("-"))
        else null
        return FlightInfo(airline?.s("name"), airline?.s("iata"), route)
    }

    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.d(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.i(k: String): Int? =
        (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
}
