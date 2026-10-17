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
    suspend fun weatherTileTemplate(): String? = withContext(Dispatchers.IO) {
        val text = get("https://api.rainviewer.com/public/weather-maps.json") ?: return@withContext null
        val root = runCatching { Http.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return@withContext null
        val host = root.s("host") ?: "https://tilecache.rainviewer.com"
        val radar = root["radar"] as? JsonObject ?: return@withContext null
        val past = radar["past"] as? JsonArray ?: return@withContext null
        val latest = (past.lastOrNull() as? JsonObject)?.s("path") ?: return@withContext null
        // host + path + /{size}/{z}/{x}/{y}/{color}/{smooth}_{snow}.png
        host + latest + "/256/{z}/{x}/{y}/4/1_1.png"
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
}
