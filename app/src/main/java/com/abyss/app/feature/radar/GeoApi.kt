package com.abyss.app.feature.radar

import com.abyss.app.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.Request
import java.util.Locale

/**
 * Open geospatial lookups for the radar's map, all key-free:
 *  - place / address search and reverse-geocoding via OpenStreetMap Nominatim,
 *  - notable places nearby via the Wikipedia geosearch API,
 *  - a live precipitation layer via RainViewer.
 *
 * These describe places and public data, not people. Nominatim asks callers to
 * identify themselves and stay under ~1 request/second, which the UI does.
 */

data class Place(
    val name: String,
    val detail: String?,
    val lat: Double,
    val lon: Double,
    val kind: String?,
)

data class WikiPlace(val title: String, val lat: Double, val lon: Double, val distM: Int, val lang: String)

/** Administrative region at a point (used to match public alert status). */
data class Region(
    val state: String?,
    val county: String? = null,
    val municipality: String? = null,
    val city: String?,
    val country: String?,
)

/** A real-world event pinned to the map (an earthquake, a reported news event…). */
data class MapEvent(
    val kind: Kind,
    val title: String,
    val description: String?,
    val url: String?,
    val lat: Double,
    val lon: Double,
    val timeMs: Long,
    val mag: Double? = null,
) {
    enum class Kind { Quake, News }
}

/** A photo of an aircraft from Planespotters (free, attribution required). */
data class PlanePhoto(val thumbUrl: String, val link: String, val photographer: String?)

object GeoApi {
    private const val UA = "Abyss/1.0 (Android; com.abyss.app; contact: abyss-app@users.noreply.github.com)"
    private val client = Http.client

    /** Free-text place / address / landmark search. */
    suspend fun searchPlaces(query: String, lang: String): List<Place> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext emptyList()
        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=8&accept-language=" +
            enc(lang) + "&q=" + enc(q)
        val text = get(url) ?: return@withContext emptyList()
        val arr = Http.json.parseToJsonElement(text) as? JsonArray ?: return@withContext emptyList()
        arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val lat = o.d("lat") ?: return@mapNotNull null
            val lon = o.d("lon") ?: return@mapNotNull null
            val display = o.s("display_name") ?: return@mapNotNull null
            val name = o.s("name")?.takeIf { it.isNotBlank() } ?: display.substringBefore(",")
            Place(name, display.substringAfter(",").trim().takeIf { it.isNotEmpty() }, lat, lon,
                o.s("type") ?: o.s("category"))
        }
    }

    /** What is at this point: nearest named place / address. */
    suspend fun reverse(lat: Double, lon: Double, lang: String): Place? = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&accept-language=" +
            enc(lang) + "&lat=" + fmt(lat) + "&lon=" + fmt(lon)
        val text = get(url) ?: return@withContext null
        val o = runCatching { Http.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return@withContext null
        val display = o.s("display_name") ?: return@withContext null
        val name = o.s("name")?.takeIf { it.isNotBlank() } ?: display.substringBefore(",")
        Place(name, display.substringAfter(",").trim().takeIf { it.isNotEmpty() }, lat, lon, o.s("type"))
    }

    /** Administrative region (oblast / raion / hromada / city) at a point, for matching alert status. */
    suspend fun regionOf(lat: Double, lon: Double): Region? = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&addressdetails=1" +
            "&accept-language=uk&lat=" + fmt(lat) + "&lon=" + fmt(lon)
        val text = get(url) ?: return@withContext null
        val o = runCatching { Http.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return@withContext null
        val addr = o["address"] as? JsonObject ?: return@withContext null
        Region(
            state = addr.s("state") ?: addr.s("region"),
            county = addr.s("county") ?: addr.s("district"),
            municipality = addr.s("municipality") ?: addr.s("city_district"),
            city = addr.s("city") ?: addr.s("town") ?: addr.s("village"),
            country = addr.s("country_code") ?: addr.s("country"),
        )
    }

    /** Notable places with Wikipedia articles within [radiusM] of a point. */
    suspend fun wikiNearby(lat: Double, lon: Double, radiusM: Int, lang: String): List<WikiPlace> =
        withContext(Dispatchers.IO) {
            val wl = wikiLang(lang)
            val url = "https://$wl.wikipedia.org/w/api.php?action=query&list=geosearch&format=json" +
                "&gscoord=" + fmt(lat) + "%7C" + fmt(lon) +
                "&gsradius=" + radiusM.coerceIn(10, 10000) + "&gslimit=50"
            val text = get(url) ?: return@withContext emptyList()
            val root = runCatching { Http.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return@withContext emptyList()
            val gs = (root["query"] as? JsonObject)?.get("geosearch") as? JsonArray ?: return@withContext emptyList()
            gs.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                WikiPlace(
                    o.s("title") ?: return@mapNotNull null,
                    o.d("lat") ?: return@mapNotNull null,
                    o.d("lon") ?: return@mapNotNull null,
                    o.i("dist") ?: 0, wl,
                )
            }
        }

    fun wikiArticleUrl(p: WikiPlace): String =
        "https://${p.lang}.wikipedia.org/wiki/" + enc(p.title.replace(' ', '_'))

    /**
     * Latest precipitation-radar frame from RainViewer, as a 256px XYZ URL
     * template (…/{z}/{x}/{y}…), or null if unavailable.
     */
    suspend fun weatherTileTemplate(): String? = weatherFrames().lastOrNull()?.template

    /** One precipitation-radar frame: its XYZ URL template and its UTC time. */
    data class RainFrame(val template: String, val timeSec: Long, val forecast: Boolean)

    /**
     * The whole RainViewer radar loop — recent past frames plus nowcast
     * (forecast) frames, oldest first — so the map can animate precipitation
     * over time instead of showing a single still frame. Key-free.
     */
    suspend fun weatherFrames(): List<RainFrame> = withContext(Dispatchers.IO) {
        val text = get("https://api.rainviewer.com/public/weather-maps.json") ?: return@withContext emptyList()
        val root = runCatching { Http.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return@withContext emptyList()
        val host = root.s("host") ?: "https://tilecache.rainviewer.com"
        val radar = root["radar"] as? JsonObject ?: return@withContext emptyList()
        fun frames(key: String, forecast: Boolean): List<RainFrame> =
            (radar[key] as? JsonArray).orEmpty().mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val path = o.s("path") ?: return@mapNotNull null
                val t = (o["time"] as? JsonPrimitive)?.longOrNull ?: 0L
                // host + path + /{size}/{z}/{x}/{y}/{color}/{smooth}_{snow}.png
                RainFrame(host + path + "/256/{z}/{x}/{y}/4/1_1.png", t, forecast)
            }
        frames("past", false) + frames("nowcast", true)
    }

    /**
     * Recent real-world events inside the given bounds: earthquakes (USGS, past
     * day) and geolocated news (GDELT). Public data; best-effort — either source
     * can be empty without failing the other.
     */
    suspend fun events(north: Double, south: Double, east: Double, west: Double, lang: String): List<MapEvent> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<MapEvent>()
            fun inBounds(la: Double, lo: Double) = la in south..north &&
                (if (west <= east) lo in west..east else (lo >= west || lo <= east))
            // USGS earthquakes (M2.5+, last 24 h).
            runCatching {
                val text = get("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson")
                if (text != null) {
                    val feats = (Http.json.parseToJsonElement(text).jsonObject["features"] as? JsonArray).orEmpty()
                    for (f in feats) {
                        val o = f as? JsonObject ?: continue
                        val g = (o["geometry"] as? JsonObject)?.get("coordinates") as? JsonArray ?: continue
                        val lon = (g.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: continue
                        val la = (g.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: continue
                        if (!inBounds(la, lon)) continue
                        val pr = o["properties"] as? JsonObject ?: continue
                        out += MapEvent(
                            MapEvent.Kind.Quake,
                            pr.s("title") ?: ("M " + (pr.d("mag") ?: 0.0)),
                            pr.s("place"),
                            pr.s("url"),
                            la, lon, pr.l("time") ?: 0L, pr.d("mag"),
                        )
                    }
                }
            }
            // GDELT geolocated news (last 24 h) for notable event types.
            runCatching {
                val q = enc("(earthquake OR explosion OR wildfire OR flood OR protest OR crash OR eruption OR storm OR outbreak)")
                val text = get("https://api.gdeltproject.org/api/v2/geo/geo?format=GeoJSON&mode=PointData&timespan=24H&query=$q")
                if (text != null) {
                    val feats = (Http.json.parseToJsonElement(text).jsonObject["features"] as? JsonArray).orEmpty()
                    for (f in feats) {
                        if (out.size > 200) break
                        val o = f as? JsonObject ?: continue
                        val g = (o["geometry"] as? JsonObject)?.get("coordinates") as? JsonArray ?: continue
                        val lon = (g.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: continue
                        val la = (g.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: continue
                        if (!inBounds(la, lon)) continue
                        val pr = o["properties"] as? JsonObject ?: continue
                        val html = pr.s("html")
                        val url = html?.let { Regex("href=\"([^\"]+)\"").find(it)?.groupValues?.get(1) }
                        val linkText = html?.let { Regex(">([^<]{4,})</a>").find(it)?.groupValues?.get(1) }
                        out += MapEvent(
                            MapEvent.Kind.News,
                            (linkText ?: pr.s("name") ?: "News").trim(),
                            pr.s("name"),
                            url,
                            la, lon, 0L,
                        )
                    }
                }
            }
            // Nearest first when a center is implied by the bounds.
            out.distinctBy { it.url ?: (it.title + it.lat) }.take(140)
        }

    /** Best photo of an aircraft by ICAO hex, or null. */
    suspend fun planePhoto(hex: String): PlanePhoto? = withContext(Dispatchers.IO) {
        val text = get("https://api.planespotters.net/pub/photos/hex/" + enc(hex.lowercase())) ?: return@withContext null
        val arr = runCatching { Http.json.parseToJsonElement(text).jsonObject["photos"] as? JsonArray }.getOrNull() ?: return@withContext null
        val first = arr.firstOrNull() as? JsonObject ?: return@withContext null
        val thumb = (first["thumbnail_large"] as? JsonObject)?.s("src")
            ?: (first["thumbnail"] as? JsonObject)?.s("src") ?: return@withContext null
        PlanePhoto(thumb, first.s("link") ?: "https://www.planespotters.net", first.s("photographer"))
    }

    /** Raw bytes of a URL (used to decode photo thumbnails off the UI thread). */
    suspend fun fetchBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes() else null
            }
        }.getOrNull()
    }

    private fun get(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    }.getOrNull()

    private fun wikiLang(lang: String): String {
        val code = lang.substringBefore('-').lowercase()
        return if (code in setOf("en", "ru", "uk", "es", "de", "fr", "pt", "tr", "zh", "ja")) code else "en"
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    private fun fmt(v: Double) = String.format(Locale.US, "%.6f", v)

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.d(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.i(k: String) = (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
    private fun JsonObject.l(k: String) = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
}
