package com.abyss.app.feature.radar

import com.abyss.app.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.FormBody
import okhttp3.Request

/**
 * Civilian-protection data:
 *  - public air-raid alert status from alerts.in.ua (the official siren feed
 *    every resident already receives; the user supplies a free token, which
 *    their rules allow to live on the device itself),
 *  - civilian shelters from OpenStreetMap via Overpass (key-free, worldwide).
 *
 * Region-level alert status and shelter locations only — never positions,
 * counts or paths of any weapon or aircraft.
 */
object SafetyApi {
    private const val UA = "Abyss/1.0 (Android; com.abyss.app; contact: abyss-app@users.noreply.github.com)"
    private val client = Http.client

    // --------------------------------------------------------------- alerts

    /** Active public alerts by region. [token] is the user's alerts.in.ua key. */
    suspend fun activeAlerts(token: String): List<RegionAlert>? = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext null
        val url = "https://api.alerts.in.ua/v1/alerts/active.json?token=" + enc(token)
        val text = get(url) ?: return@withContext null
        val root = parse(text) as? JsonObject ?: return@withContext null
        val arr = root["alerts"] as? JsonArray ?: return@withContext emptyList()
        arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            if (o.s("finished_at") != null) return@mapNotNull null
            val threats = (o["threats"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.s("threat_type") }.orEmpty()
            RegionAlert(
                uid = o.s("location_uid") ?: return@mapNotNull null,
                title = o.s("location_title") ?: return@mapNotNull null,
                titleEn = o.s("location_title_en"),
                kind = AlertLogic.kindOf(o.s("alert_type")),
                level = AlertLogic.levelOf(o.s("alert_level")),
                locationType = o.s("location_type") ?: "unknown",
                since = o.s("started_at"),
                threats = threats,
            )
        }
    }

    // -------------------------------------------------------------- shelters

    private val overpassHosts = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )

    suspend fun shelters(box: GeoBox): List<Shelter>? = withContext(Dispatchers.IO) {
        val q = ShelterLogic.overpassQuery(box)
        var text: String? = null
        for (h in overpassHosts) { text = post(h, q); if (text != null) break }
        val root = parse(text ?: return@withContext null) as? JsonObject ?: return@withContext null
        val elements = (root["elements"] as? JsonArray)?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val id = (o.s("type")?.firstOrNull() ?: 'n').toString() + (o["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            val lat = o.d("lat") ?: (o["center"] as? JsonObject)?.d("lat") ?: return@mapNotNull null
            val lon = o.d("lon") ?: (o["center"] as? JsonObject)?.d("lon") ?: return@mapNotNull null
            val tags = (o["tags"] as? JsonObject)?.mapNotNull { (k, v) ->
                (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
            }?.toMap().orEmpty()
            if (tags.isEmpty()) return@mapNotNull null
            Triple(id, doubleArrayOf(lat, lon), tags)
        }.orEmpty()
        ShelterLogic.fromElements(elements)
    }

    // --------------------------------------------------------------- helpers

    private fun get(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    }.getOrNull()

    private fun post(url: String, query: String): String? = runCatching {
        val body = FormBody.Builder().add("data", query).build()
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).post(body).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    }.getOrNull()

    private fun parse(t: String) = runCatching { Http.json.parseToJsonElement(t) }.getOrNull()
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.d(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
}
