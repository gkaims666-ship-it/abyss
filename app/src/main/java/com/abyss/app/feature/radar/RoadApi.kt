package com.abyss.app.feature.radar

import com.abyss.app.core.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.Request
import java.util.Locale

/** Keys the user typed in (null = not set). */
data class RoadKeys(val tomtom: String?, val here: String?, val windy: String?)

/** Per-source outcome: item count, or null if the source failed. */
data class RoadResult<T>(val items: List<T>, val perSource: Map<String, Int?>)

/**
 * Road situation from every source we can reach legally:
 *
 * Worldwide (user's own free key): TomTom Traffic (live flow tiles + incident
 * details), HERE Traffic v7 incidents, Windy Webcams.
 * Worldwide (no key): OpenStreetMap speed / red-light / average-speed / traffic
 * cameras via Overpass.
 * Open government feeds (no key): Finland Digitraffic (incidents, road works,
 * ~800 road weather cameras), British Columbia DriveBC Open511, Singapore LTA
 * traffic cameras.
 */
object RoadApi {
    private const val UA = "Abyss/1.0 (Android; com.abyss.app; contact: abyss-app@users.noreply.github.com)"
    private val client = Http.client

    const val TOMTOM = "TomTom"
    const val HERE = "HERE"
    const val WINDY = "Windy"
    const val OSM = "OpenStreetMap"
    const val DIGITRAFFIC = "Digitraffic (FI)"
    const val DRIVEBC = "DriveBC (CA-BC)"
    const val LTA = "LTA (SG)"

    // ------------------------------------------------------------------ flow tiles

    /** TomTom raster flow tiles: base URL + filename ending for osmdroid's XYTileSource. */
    fun tomtomFlowTiles(key: String, dark: Boolean): Pair<String, String> {
        val style = if (dark) "relative0-dark" else "relative0"
        return "https://api.tomtom.com/traffic/map/4/tile/flow/$style/" to ".png?tileSize=256&key=" + enc(key)
    }

    // ------------------------------------------------------------------ incidents

    suspend fun incidents(box: GeoBox, lang: String, keys: RoadKeys): RoadResult<RoadIncident> = coroutineScope {
        val jobs = LinkedHashMap<String, kotlinx.coroutines.Deferred<List<RoadIncident>?>>()
        keys.tomtom?.let { k -> jobs[TOMTOM] = async(Dispatchers.IO) { runCatching { tomtom(box, lang, k) }.getOrNull() } }
        keys.here?.let { k -> jobs[HERE] = async(Dispatchers.IO) { runCatching { here(box, lang, k) }.getOrNull() } }
        if (box.intersects(RoadRegions.Finland)) jobs[DIGITRAFFIC] = async(Dispatchers.IO) { runCatching { digitraffic(box) }.getOrNull() }
        if (box.intersects(RoadRegions.BritishColumbia)) jobs[DRIVEBC] = async(Dispatchers.IO) { runCatching { driveBc(box) }.getOrNull() }
        val per = LinkedHashMap<String, Int?>()
        val all = ArrayList<RoadIncident>()
        for ((name, d) in jobs) {
            val r = d.await()
            per[name] = r?.size
            if (r != null) all += r
        }
        RoadResult(RoadLogic.dedupe(all), per)
    }

    private fun tomtom(box0: GeoBox, lang: String, key: String): List<RoadIncident>? {
        val b = box0.clampArea(9_500.0)
        val fields = "{incidents{type,geometry{type,coordinates},properties{id,iconCategory,magnitudeOfDelay," +
            "events{description,code,iconCategory},startTime,endTime,from,to,length,delay,roadNumbers," +
            "timeValidity,probabilityOfOccurrence,numberOfReports,lastReportTime}}}"
        fun url(l: String) = "https://api.tomtom.com/traffic/services/5/incidentDetails?key=" + enc(key) +
            "&bbox=" + f(b.west) + "," + f(b.south) + "," + f(b.east) + "," + f(b.north) +
            "&fields=" + enc(fields) + "&language=" + l + "&timeValidityFilter=present"
        val l = RoadLogic.tomtomLang(lang)
        val text = get(url(l)) ?: (if (l != "en-GB") get(url("en-GB")) else null) ?: return null
        val root = parse(text) as? JsonObject ?: return null
        val arr = root["incidents"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val p = o["properties"] as? JsonObject ?: return@mapNotNull null
            val geo = geometry(o["geometry"]) ?: return@mapNotNull null
            val events = (p["events"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.s("description") }.orEmpty()
            val kind = RoadLogic.tomtomKind(p.i("iconCategory"))
            val mag = p.i("magnitudeOfDelay") ?: 0
            val roads = (p["roadNumbers"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            val prob = p.s("probabilityOfOccurrence")
            val at = geo.second
            RoadIncident(
                id = "tt-" + (p.s("id") ?: "${at[0]},${at[1]}"), sources = setOf(TOMTOM),
                kind = kind, severity = if (kind == RoadKind.Closure) 4 else mag.coerceIn(0, 4),
                title = events.firstOrNull(), details = events.drop(1),
                road = roads.joinToString(", ").ifBlank { null },
                from = p.s("from"), to = p.s("to"), delaySec = p.i("delay"), lengthM = p.d("length"),
                start = p.s("startTime"), end = p.s("endTime"), reports = p.i("numberOfReports"),
                unconfirmed = prob != null && prob != "certain",
                lat = at[0], lon = at[1], line = geo.first,
            )
        }
    }

    private fun here(box0: GeoBox, lang: String, key: String): List<RoadIncident>? {
        val b = box0.clampDegrees(0.95)
        val url = "https://data.traffic.hereapi.com/v7/incidents?in=bbox:" +
            f(b.west) + "," + f(b.south) + "," + f(b.east) + "," + f(b.north) +
            "&locationReferencing=shape&lang=" + enc(lang) + "&apiKey=" + enc(key)
        val root = parse(get(url) ?: return null) as? JsonObject ?: return null
        val arr = root["results"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val d = o["incidentDetails"] as? JsonObject ?: return@mapNotNull null
            val loc = o["location"] as? JsonObject
            val pts = ArrayList<DoubleArray>()
            ((loc?.get("shape") as? JsonObject)?.get("links") as? JsonArray)?.forEach { link ->
                ((link as? JsonObject)?.get("points") as? JsonArray)?.forEach { pt ->
                    val q = pt as? JsonObject ?: return@forEach
                    val la = q.d("lat"); val lo = q.d("lng")
                    if (la != null && lo != null) pts += doubleArrayOf(la, lo)
                }
            }
            if (pts.isEmpty()) return@mapNotNull null
            val closed = d.b("roadClosed") == true
            val desc = (d["description"] as? JsonObject)?.s("value")
            val summary = (d["summary"] as? JsonObject)?.s("value")
            val mid = RoadLogic.midpoint(pts)
            RoadIncident(
                id = "here-" + (d.s("id") ?: "${mid[0]},${mid[1]}"), sources = setOf(HERE),
                kind = RoadLogic.hereKind(d.s("type"), closed),
                severity = RoadLogic.hereSeverity(d.s("criticality"), closed),
                title = summary ?: desc, details = listOfNotNull(desc?.takeIf { it != summary }),
                road = loc?.s("description"), lengthM = loc?.d("length"),
                start = d.s("startTime"), end = d.s("endTime"),
                lat = mid[0], lon = mid[1], line = if (pts.size > 1) pts else null,
            )
        }
    }

    private fun driveBc(box: GeoBox): List<RoadIncident>? {
        val url = "https://api.open511.gov.bc.ca/events?format=json&status=ACTIVE&limit=500&bbox=" +
            f(box.west) + "," + f(box.south) + "," + f(box.east) + "," + f(box.north)
        val root = parse(get(url) ?: return null) as? JsonObject ?: return null
        val arr = root["events"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val geo = geometry(o["geography"]) ?: return@mapNotNull null
            val roads = (o["roads"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
            val r0 = roads.firstOrNull()
            val closed = roads.any { it.s("state")?.uppercase() == "CLOSED" }
            val headline = o.s("headline")
            val desc = o.s("description")
            val delayMin = roads.firstNotNullOfOrNull { it.i("delay") }
            RoadIncident(
                id = "bc-" + (o.s("id") ?: "${geo.second[0]},${geo.second[1]}"), sources = setOf(DRIVEBC),
                kind = RoadLogic.open511Kind(o.s("event_type"), "${headline.orEmpty()} ${desc.orEmpty()}", closed),
                severity = if (closed) 4 else RoadLogic.open511Severity(o.s("severity")),
                title = desc ?: headline, details = listOfNotNull(headline?.takeIf { desc != null }),
                road = r0?.s("name")?.let { n -> r0.s("direction")?.let { "$n ($it)" } ?: n },
                from = r0?.s("from"), to = r0?.s("to"), delaySec = delayMin?.let { it * 60 },
                start = o.s("created"), end = null,
                lat = geo.second[0], lon = geo.second[1], line = geo.first,
                url = "https://www.drivebc.ca/",
            )
        }
    }

    private fun digitraffic(box: GeoBox): List<RoadIncident>? {
        val out = ArrayList<RoadIncident>()
        var ok = false
        for (path in listOf("traffic-announcements", "roadworks")) {
            val text = get("https://tie.digitraffic.fi/api/traffic-message/v2/$path?includeAreaGeometry=false",
                digitraffic = true) ?: continue
            ok = true
            val root = parse(text) as? JsonObject ?: continue
            (root["features"] as? JsonArray)?.forEach { el ->
                val o = el as? JsonObject ?: return@forEach
                val geo = geometry(o["geometry"]) ?: return@forEach
                if (!box.contains(geo.second[0], geo.second[1])) return@forEach
                val p = o["properties"] as? JsonObject ?: return@forEach
                val ann = (p["announcements"] as? JsonArray)?.firstOrNull() as? JsonObject
                val title = ann?.s("title") ?: p.s("title")
                val locDesc = (ann?.get("location") as? JsonObject)?.s("description")
                val feats = (ann?.get("features") as? JsonArray)?.mapNotNull { f ->
                    (f as? JsonObject)?.s("name") ?: (f as? JsonPrimitive)?.contentOrNull
                }.orEmpty()
                val comment = ann?.s("comment")
                val kind = RoadLogic.digitrafficKind(p.s("situationType"), p.s("trafficAnnouncementType"),
                    "${title.orEmpty()} ${feats.joinToString(" ")}") ?: return@forEach
                val tad = ann?.get("timeAndDuration") as? JsonObject
                out += RoadIncident(
                    id = "fi-" + (p.s("situationId") ?: "${geo.second[0]},${geo.second[1]}"),
                    sources = setOf(DIGITRAFFIC), kind = kind,
                    severity = if (kind == RoadKind.Closure) 4 else 0,
                    title = title, details = (listOfNotNull(locDesc) + feats + listOfNotNull(comment)).distinct(),
                    start = tad?.s("startTime") ?: p.s("releaseTime"), end = tad?.s("endTime"),
                    lat = geo.second[0], lon = geo.second[1], line = geo.first,
                    url = "https://liikennetilanne.fintraffic.fi/",
                )
            }
        }
        return if (ok) out else null
    }

    // ------------------------------------------------------------------ cameras

    suspend fun cameras(box: GeoBox, zoom: Double, lang: String, keys: RoadKeys): RoadResult<RoadCamera> = coroutineScope {
        val jobs = LinkedHashMap<String, kotlinx.coroutines.Deferred<List<RoadCamera>?>>()
        if (zoom >= 9.0) jobs[OSM] = async(Dispatchers.IO) { runCatching { osm(box.clampDegrees(0.7)) }.getOrNull() }
        keys.windy?.let { k -> jobs[WINDY] = async(Dispatchers.IO) { runCatching { windy(box, lang, k) }.getOrNull() } }
        if (box.intersects(RoadRegions.Finland)) jobs[DIGITRAFFIC] = async(Dispatchers.IO) { runCatching { weathercams(box) }.getOrNull() }
        if (box.intersects(RoadRegions.Singapore)) jobs[LTA] = async(Dispatchers.IO) { runCatching { singapore() }.getOrNull() }
        val per = LinkedHashMap<String, Int?>()
        val all = ArrayList<RoadCamera>()
        for ((name, d) in jobs) {
            val r = d.await()
            per[name] = r?.size
            if (r != null) all += r
        }
        RoadResult(all, per)
    }

    private val overpassHosts = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )

    private fun osm(box: GeoBox): List<RoadCamera>? {
        val q = RoadLogic.overpassQuery(box)
        var text: String? = null
        for (h in overpassHosts) {
            text = post(h, q)
            if (text != null) break
        }
        val root = parse(text ?: return null) as? JsonObject ?: return null
        val nodes = HashMap<Long, RoadLogic.OsmNode>()
        val rels = ArrayList<RoadLogic.OsmRelation>()
        (root["elements"] as? JsonArray)?.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val id = o.l("id") ?: return@forEach
            val tags = (o["tags"] as? JsonObject)?.mapNotNull { (k, v) ->
                (v as? JsonPrimitive)?.contentOrNull?.let { k to it }
            }?.toMap().orEmpty()
            when (o.s("type")) {
                "node" -> {
                    val la = o.d("lat") ?: return@forEach
                    val lo = o.d("lon") ?: return@forEach
                    val prev = nodes[id]
                    // "out skel" repeats nodes without tags: keep the tagged copy.
                    if (prev == null || prev.tags.isEmpty()) nodes[id] = RoadLogic.OsmNode(id, la, lo, tags)
                }
                "relation" -> {
                    val members = (o["members"] as? JsonArray)?.mapNotNull { m ->
                        val mo = m as? JsonObject ?: return@mapNotNull null
                        RoadLogic.OsmMember(mo.s("type") ?: return@mapNotNull null, mo.l("ref") ?: return@mapNotNull null,
                            mo.s("role").orEmpty())
                    }.orEmpty()
                    if (tags.isNotEmpty()) rels += RoadLogic.OsmRelation(id, tags, members)
                }
            }
        }
        return RoadLogic.camerasFromOsm(nodes, rels)
    }

    private fun windy(box: GeoBox, lang: String, key: String): List<RoadCamera>? {
        val r = box.radiusKm().coerceIn(5.0, 250.0).toInt()
        val url = "https://api.windy.com/webcams/api/v3/webcams?nearby=" + f(box.centerLat) + "," + f(box.centerLon) +
            "," + r + "&limit=50&include=location,images,urls,player&lang=" + enc(lang.substringBefore('-'))
        val text = get(url, header = "x-windy-api-key" to key) ?: return null
        val root = parse(text) as? JsonObject ?: return null
        val arr = root["webcams"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val loc = o["location"] as? JsonObject ?: return@mapNotNull null
            val la = loc.d("latitude") ?: return@mapNotNull null
            val lo = loc.d("longitude") ?: return@mapNotNull null
            val id = o.s("webcamId") ?: o.s("id") ?: return@mapNotNull null
            val cur = ((o["images"] as? JsonObject)?.get("current") as? JsonObject)
            val urls = o["urls"] as? JsonObject
            val place = listOfNotNull(loc.s("city"), loc.s("country")).joinToString(", ").ifBlank { null }
            RoadCamera(
                id = "windy-$id", source = WINDY, kind = CamKind.Live, lat = la, lon = lo,
                title = o.s("title") ?: place, imageUrl = cur?.s("preview") ?: cur?.s("thumbnail"),
                pageUrl = urls?.s("detail") ?: "https://www.windy.com/webcams/$id", operator = place,
            )
        }
    }

    @Volatile private var fiCams: List<RoadCamera>? = null
    @Volatile private var fiCamsAt = 0L

    private fun weathercams(box: GeoBox): List<RoadCamera>? {
        val now = System.currentTimeMillis()
        val cached = fiCams
        val all = if (cached != null && now - fiCamsAt < 30 * 60_000L) cached else {
            val text = get("https://tie.digitraffic.fi/api/weathercam/v1/stations", digitraffic = true) ?: return null
            val root = parse(text) as? JsonObject ?: return null
            val list = (root["features"] as? JsonArray)?.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val c = ((o["geometry"] as? JsonObject)?.get("coordinates") as? JsonArray) ?: return@mapNotNull null
                val lo = (c.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                val la = (c.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
                val p = o["properties"] as? JsonObject ?: return@mapNotNull null
                if (p.s("collectionStatus")?.uppercase()?.let { it != "GATHERING" } == true) return@mapNotNull null
                val preset = (p["presets"] as? JsonArray)?.mapNotNull { it as? JsonObject }
                    ?.firstOrNull { it.b("inCollection") != false }?.s("id") ?: return@mapNotNull null
                RoadCamera(
                    id = "fi-" + (p.s("id") ?: preset), source = DIGITRAFFIC, kind = CamKind.Live,
                    lat = la, lon = lo, title = p.s("name"),
                    imageUrl = "https://weathercam.digitraffic.fi/$preset.jpg",
                    pageUrl = "https://liikennetilanne.fintraffic.fi/",
                )
            }.orEmpty()
            fiCams = list; fiCamsAt = now
            list
        }
        return all.filter { box.contains(it.lat, it.lon) }
    }

    private fun singapore(): List<RoadCamera>? {
        val root = parse(get("https://api.data.gov.sg/v1/transport/traffic-images") ?: return null) as? JsonObject ?: return null
        val item = (root["items"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return emptyList()
        return (item["cameras"] as? JsonArray)?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val loc = o["location"] as? JsonObject ?: return@mapNotNull null
            val la = loc.d("latitude") ?: return@mapNotNull null
            val lo = loc.d("longitude") ?: return@mapNotNull null
            val id = o.s("camera_id") ?: return@mapNotNull null
            RoadCamera(id = "sg-$id", source = LTA, kind = CamKind.Live, lat = la, lon = lo,
                title = "LTA #$id", imageUrl = o.s("image"), pageUrl = "https://onemotoring.lta.gov.sg/")
        }.orEmpty()
    }

    // ------------------------------------------------------------------ helpers

    /** GeoJSON-ish geometry → (line as [lat,lon] or null, representative point). */
    private fun geometry(g: JsonElement?): Pair<List<DoubleArray>?, DoubleArray>? {
        val o = g as? JsonObject ?: return null
        val c = o["coordinates"] as? JsonArray ?: return null
        fun pt(e: JsonElement?): DoubleArray? {
            val a = e as? JsonArray ?: return null
            val lo = (a.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return null
            val la = (a.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return null
            return doubleArrayOf(la, lo)
        }
        return when (o.s("type")) {
            "Point" -> pt(c)?.let { null to it }
            "LineString" -> {
                val line = c.mapNotNull { pt(it) }
                if (line.isEmpty()) null else (if (line.size > 1) line else null) to RoadLogic.midpoint(line)
            }
            "MultiLineString", "Polygon" -> {
                val line = c.flatMap { part -> (part as? JsonArray)?.mapNotNull { pt(it) }.orEmpty() }
                if (line.isEmpty()) null else (if (line.size > 1) line else null) to RoadLogic.midpoint(line)
            }
            "MultiPoint" -> pt(c.firstOrNull())?.let { null to it }
            else -> null
        }
    }

    private fun get(url: String, header: Pair<String, String>? = null, digitraffic: Boolean = false): String? = runCatching {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        header?.let { b.header(it.first, it.second) }
        if (digitraffic) b.header("Digitraffic-User", "Abyss/1.0")
        client.newCall(b.build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    }.getOrNull()

    private fun post(url: String, query: String): String? = runCatching {
        val body = FormBody.Builder().add("data", query).build()
        client.newCall(Request.Builder().url(url).header("User-Agent", UA).post(body).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    }.getOrNull()

    private fun parse(t: String): JsonElement? = runCatching { Http.json.parseToJsonElement(t) }.getOrNull()

    suspend fun fetchBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.bytes() else null
            }
        }.getOrNull()
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
    private fun f(v: Double) = String.format(Locale.US, "%.5f", v)

    private fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.d(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
    private fun JsonObject.b(k: String) = (this[k] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.i(k: String) = (this[k] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
    private fun JsonObject.l(k: String) = (this[k] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
}
