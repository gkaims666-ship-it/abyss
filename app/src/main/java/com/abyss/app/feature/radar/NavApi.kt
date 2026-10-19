package com.abyss.app.feature.radar

import com.abyss.app.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.Locale

/** Optional keys for the keyed engines (both have free tiers). */
data class NavKeys(val graphhopper: String?, val ors: String?)

/**
 * Worldwide routing from several engines at once, combined by [NavLogic.pick]:
 *  - OSRM via the FOSSGIS servers that power openstreetmap.org — key-free,
 *    separate car / foot / bike graphs;
 *  - GraphHopper (free key) — strong localized turn instructions;
 *  - OpenRouteService (free key) — independent OSM graph with its own profiles.
 * Every engine runs in parallel; one failing never blocks the others.
 */
object NavApi {
    private const val UA = "Abyss/1.0 (Android; routing)"
    private val client = Http.client
    private val json = Http.json

    suspend fun route(
        fromLat: Double, fromLon: Double, toLat: Double, toLon: Double,
        mode: NavMode, lang: String, keys: NavKeys,
    ): NavResult? = coroutineScope {
        val jobs = buildList {
            add(async(Dispatchers.IO) { runCatching { osrm(fromLat, fromLon, toLat, toLon, mode, lang) }.getOrNull() })
            keys.graphhopper?.let { k ->
                add(async(Dispatchers.IO) { runCatching { graphhopper(fromLat, fromLon, toLat, toLon, mode, lang, k) }.getOrNull() })
            }
            keys.ors?.let { k ->
                add(async(Dispatchers.IO) { runCatching { ors(fromLat, fromLon, toLat, toLon, mode, lang, k) }.getOrNull() })
            }
        }
        NavLogic.pick(jobs.awaitAll().filterNotNull())
    }

    // ------------------------------------------------------------ OSRM (FOSSGIS)

    private fun osrm(fLat: Double, fLon: Double, tLat: Double, tLon: Double, mode: NavMode, lang: String): NavRoute? {
        val graph = when (mode) { NavMode.Car -> "routed-car"; NavMode.Foot -> "routed-foot"; NavMode.Bike -> "routed-bike" }
        val url = String.format(Locale.US,
            "https://routing.openstreetmap.de/%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true",
            graph, fLon, fLat, tLon, tLat)
        val root = json.parseToJsonElement(get(url) ?: return null).jsonObject
        val r = (root["routes"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val pts = lineLonLat((r["geometry"] as? JsonObject)?.get("coordinates"))
        val steps = ArrayList<NavStep>()
        ((r["legs"] as? JsonArray)?.firstOrNull() as? JsonObject)?.let { leg ->
            (leg["steps"] as? JsonArray)?.forEach { e ->
                val s = e as? JsonObject ?: return@forEach
                val m = s["maneuver"] as? JsonObject ?: return@forEach
                val loc = m["location"] as? JsonArray ?: return@forEach
                val lon = (loc.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return@forEach
                val lat = (loc.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return@forEach
                steps += NavStep(
                    NavLogic.osrmText(m.str("type"), m.str("modifier"), s.str("name"), (m["exit"] as? JsonPrimitive)?.intOrNull, lang),
                    s.num("distance"), s.num("duration"), lat, lon,
                )
            }
        }
        return NavRoute(NavLogic.OSRM, pts, r.num("distance"), r.num("duration"), steps)
    }

    // ------------------------------------------------------------ GraphHopper

    private fun graphhopper(fLat: Double, fLon: Double, tLat: Double, tLon: Double, mode: NavMode, lang: String, key: String): NavRoute? {
        val profile = when (mode) { NavMode.Car -> "car"; NavMode.Foot -> "foot"; NavMode.Bike -> "bike" }
        val url = String.format(Locale.US,
            "https://graphhopper.com/api/1/route?point=%.6f,%.6f&point=%.6f,%.6f&profile=%s&locale=%s&points_encoded=false&instructions=true&key=%s",
            fLat, fLon, tLat, tLon, profile, NavLogic.ghLocale(lang), enc(key))
        val root = json.parseToJsonElement(get(url) ?: return null).jsonObject
        val p = (root["paths"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        val pts = lineLonLat((p["points"] as? JsonObject)?.get("coordinates"))
        val steps = (p["instructions"] as? JsonArray)?.mapNotNull { e ->
            val s = e as? JsonObject ?: return@mapNotNull null
            val i0 = ((s["interval"] as? JsonArray)?.getOrNull(0) as? JsonPrimitive)?.intOrNull ?: 0
            val at = pts.getOrNull(i0) ?: return@mapNotNull null
            NavStep(s.str("text") ?: "", s.num("distance"), s.num("time") / 1000.0, at[0], at[1])
        } ?: emptyList()
        return NavRoute(NavLogic.GRAPHHOPPER, pts, p.num("distance"), p.num("time") / 1000.0, steps)
    }

    // ------------------------------------------------------------ OpenRouteService

    private fun ors(fLat: Double, fLon: Double, tLat: Double, tLon: Double, mode: NavMode, lang: String, key: String): NavRoute? {
        val profile = when (mode) { NavMode.Car -> "driving-car"; NavMode.Foot -> "foot-walking"; NavMode.Bike -> "cycling-regular" }
        val body = String.format(Locale.US,
            "{\"coordinates\":[[%.6f,%.6f],[%.6f,%.6f]],\"language\":\"%s\",\"instructions\":true}",
            fLon, fLat, tLon, tLat, NavLogic.orsLanguage(lang))
        val req = Request.Builder()
            .url("https://api.openrouteservice.org/v2/directions/$profile/geojson")
            .header("User-Agent", UA)
            .header("Authorization", key)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val text = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null
        val f = ((json.parseToJsonElement(text).jsonObject["features"] as? JsonArray)?.firstOrNull() as? JsonObject) ?: return null
        val pts = lineLonLat((f["geometry"] as? JsonObject)?.get("coordinates"))
        val props = f["properties"] as? JsonObject ?: return null
        val summary = props["summary"] as? JsonObject
        val steps = ArrayList<NavStep>()
        (props["segments"] as? JsonArray)?.forEach { seg ->
            ((seg as? JsonObject)?.get("steps") as? JsonArray)?.forEach { e ->
                val s = e as? JsonObject ?: return@forEach
                val w0 = ((s["way_points"] as? JsonArray)?.getOrNull(0) as? JsonPrimitive)?.intOrNull ?: 0
                val at = pts.getOrNull(w0) ?: return@forEach
                steps += NavStep(s.str("instruction") ?: "", s.num("distance"), s.num("duration"), at[0], at[1])
            }
        }
        return NavRoute(NavLogic.ORS, pts, summary?.num("distance") ?: 0.0, summary?.num("duration") ?: 0.0, steps)
    }

    // ------------------------------------------------------------ helpers

    /** GeoJSON [[lon,lat],…] → list of (lat, lon). */
    private fun lineLonLat(el: kotlinx.serialization.json.JsonElement?): List<DoubleArray> =
        (el as? JsonArray)?.mapNotNull { pt ->
            val a = pt as? JsonArray ?: return@mapNotNull null
            val lon = (a.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val lat = (a.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            doubleArrayOf(lat, lon)
        } ?: emptyList()

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.num(k: String): Double = (this[k] as? JsonPrimitive)?.doubleOrNull ?: 0.0

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    }.getOrNull()
}
