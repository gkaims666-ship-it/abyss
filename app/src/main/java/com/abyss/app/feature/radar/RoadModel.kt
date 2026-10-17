package com.abyss.app.feature.radar

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Road situation model shared by every traffic source, plus pure helpers
 * (classification, area limits, de-duplication, OSM camera parsing input).
 * No Android or JSON types here, so it is unit-tested on the JVM.
 */

enum class RoadKind { Accident, Jam, Closure, LaneClosed, Roadworks, Hazard, Weather, Breakdown, Event, Other }

/** 0 unknown · 1 minor · 2 moderate · 3 major · 4 closed / indefinite */
data class RoadIncident(
    val id: String,
    val sources: Set<String>,
    val kind: RoadKind,
    val severity: Int,
    val title: String?,
    val details: List<String> = emptyList(),
    val road: String? = null,
    val from: String? = null,
    val to: String? = null,
    val delaySec: Int? = null,
    val lengthM: Double? = null,
    val start: String? = null,
    val end: String? = null,
    val reports: Int? = null,
    val unconfirmed: Boolean = false,
    val lat: Double,
    val lon: Double,
    /** Affected stretch as [lat, lon] points, if the source gives one. */
    val line: List<DoubleArray>? = null,
    val url: String? = null,
)

enum class CamKind { Speed, RedLight, AvgSpeed, Traffic, Live }

data class RoadCamera(
    val id: String,
    val source: String,
    val kind: CamKind,
    val lat: Double,
    val lon: Double,
    val maxSpeed: String? = null,
    val direction: String? = null,
    val title: String? = null,
    val imageUrl: String? = null,
    val pageUrl: String? = null,
    val ref: String? = null,
    val operator: String? = null,
    /** For average-speed sections: the other end of the zone. */
    val endLat: Double? = null,
    val endLon: Double? = null,
)

/** Map viewport (degrees). */
data class GeoBox(val north: Double, val south: Double, val east: Double, val west: Double) {
    val centerLat get() = (north + south) / 2
    val centerLon get() = (east + west) / 2

    fun intersects(o: GeoBox) = !(o.west > east || o.east < west || o.south > north || o.north < south)
    fun contains(lat: Double, lon: Double) = lat in south..north && lon in west..east

    /** Approximate area in km². */
    fun areaKm2(): Double {
        val h = (north - south) * 111.32
        val w = (east - west) * 111.32 * cos(centerLat * PI / 180)
        return h * w
    }

    /** Same centre, shrunk (never grown) so the area is ≤ [maxKm2] (with margin). */
    fun clampArea(maxKm2: Double): GeoBox {
        val a = areaKm2()
        if (a <= maxKm2) return this
        val k = sqrt(maxKm2 / a) * 0.97
        return scaled(k)
    }

    /** Same centre, each side ≤ [maxDeg]. */
    fun clampDegrees(maxDeg: Double): GeoBox {
        val h = north - south
        val w = east - west
        if (h <= maxDeg && w <= maxDeg) return this
        val hh = min(h, maxDeg) / 2 * 0.98
        val hw = min(w, maxDeg) / 2 * 0.98
        return GeoBox(centerLat + hh, centerLat - hh, centerLon + hw, centerLon - hw)
    }

    private fun scaled(k: Double): GeoBox {
        val hh = (north - south) / 2 * k
        val hw = (east - west) / 2 * k
        return GeoBox(min(85.0, centerLat + hh), max(-85.0, centerLat - hh), centerLon + hw, centerLon - hw)
    }

    /** Radius (km) of the circle covering the box. */
    fun radiusKm(): Double {
        val h = (north - south) * 111.32 / 2
        val w = (east - west) * 111.32 * cos(centerLat * PI / 180) / 2
        return sqrt(h * h + w * w)
    }
}

object RoadRegions {
    val Finland = GeoBox(70.2, 59.4, 31.8, 19.0)
    val BritishColumbia = GeoBox(60.1, 48.2, -114.0, -139.2)
    val Singapore = GeoBox(1.48, 1.15, 104.1, 103.6)
}

object RoadLogic {
    /** TomTom iconCategory → kind. */
    fun tomtomKind(icon: Int?): RoadKind = when (icon) {
        1 -> RoadKind.Accident
        2, 4, 5, 10, 11 -> RoadKind.Weather
        3 -> RoadKind.Hazard
        6 -> RoadKind.Jam
        7 -> RoadKind.LaneClosed
        8 -> RoadKind.Closure
        9 -> RoadKind.Roadworks
        14 -> RoadKind.Breakdown
        else -> RoadKind.Other
    }

    /** HERE incidentDetails.type → kind. */
    fun hereKind(type: String?, roadClosed: Boolean): RoadKind {
        if (roadClosed) return RoadKind.Closure
        return when (type?.lowercase()) {
            "accident" -> RoadKind.Accident
            "congestion" -> RoadKind.Jam
            "construction" -> RoadKind.Roadworks
            "disabledvehicle" -> RoadKind.Breakdown
            "plannedevent" -> RoadKind.Event
            "roadhazard" -> RoadKind.Hazard
            "roadclosure" -> RoadKind.Closure
            "weather" -> RoadKind.Weather
            "lanerestriction" -> RoadKind.LaneClosed
            else -> RoadKind.Other
        }
    }

    fun hereSeverity(criticality: String?, roadClosed: Boolean): Int {
        if (roadClosed) return 4
        return when (criticality?.lowercase()) {
            "critical" -> 3
            "major" -> 3
            "minor" -> 2
            "lowimpact" -> 1
            else -> 0
        }
    }

    /** Open511 (DriveBC) event → kind. */
    fun open511Kind(type: String?, text: String, closed: Boolean): RoadKind {
        if (closed) return RoadKind.Closure
        val t = text.lowercase()
        return when (type?.uppercase()) {
            "CONSTRUCTION" -> RoadKind.Roadworks
            "SPECIAL_EVENT" -> RoadKind.Event
            "WEATHER_CONDITION" -> RoadKind.Weather
            "ROAD_CONDITION" -> RoadKind.Hazard
            "INCIDENT" -> when {
                "collision" in t || "accident" in t || "crash" in t -> RoadKind.Accident
                "vehicle" in t && ("stall" in t || "disabled" in t) -> RoadKind.Breakdown
                else -> RoadKind.Hazard
            }
            else -> RoadKind.Other
        }
    }

    fun open511Severity(s: String?): Int = when (s?.uppercase()) {
        "MINOR" -> 1; "MODERATE" -> 2; "MAJOR" -> 3; else -> 0
    }

    /** Digitraffic (Finland) situation → kind, or null for permits we don't show. */
    fun digitrafficKind(situationType: String?, announcementType: String?, text: String): RoadKind? {
        val t = text.lowercase()
        return when (situationType?.uppercase()) {
            "ROAD_WORK" -> RoadKind.Roadworks
            "TRAFFIC_ANNOUNCEMENT" -> when {
                announcementType?.uppercase()?.contains("ACCIDENT") == true -> RoadKind.Accident
                "onnettomuus" in t || "accident" in t || "olycka" in t -> RoadKind.Accident
                "suljettu" in t || "closed" in t || "avstängd" in t -> RoadKind.Closure
                "ruuhka" in t || "jono" in t || "queue" in t -> RoadKind.Jam
                else -> RoadKind.Hazard
            }
            "WEIGHT_RESTRICTION", "EXEMPTED_TRANSPORT" -> null
            else -> RoadKind.Other
        }
    }

    /**
     * Merge the same event reported by several providers: same kind family and
     * within [radiusM] metres. The richer record wins, sources are unioned.
     */
    fun dedupe(list: List<RoadIncident>, radiusM: Double = 180.0): List<RoadIncident> {
        val out = ArrayList<RoadIncident>(list.size)
        for (inc in list.sortedByDescending { richness(it) }) {
            val k = out.indexOfFirst { o ->
                family(o.kind) == family(inc.kind) && o.sources.intersect(inc.sources).isEmpty() &&
                    distanceNm(o.lat, o.lon, inc.lat, inc.lon) * 1852.0 < radiusM
            }
            if (k < 0) out += inc
            else {
                val o = out[k]
                out[k] = o.copy(
                    sources = o.sources + inc.sources,
                    severity = max(o.severity, inc.severity),
                    details = (o.details + listOfNotNull(inc.title) + inc.details).distinct()
                        .filter { it != o.title },
                    delaySec = o.delaySec ?: inc.delaySec,
                    lengthM = o.lengthM ?: inc.lengthM,
                    road = o.road ?: inc.road,
                    from = o.from ?: inc.from,
                    to = o.to ?: inc.to,
                    start = o.start ?: inc.start,
                    end = o.end ?: inc.end,
                    line = o.line ?: inc.line,
                )
            }
        }
        return out
    }

    private fun family(k: RoadKind) = when (k) {
        RoadKind.Closure, RoadKind.LaneClosed, RoadKind.Roadworks -> 1
        RoadKind.Accident, RoadKind.Breakdown -> 2
        RoadKind.Jam -> 3
        RoadKind.Weather, RoadKind.Hazard -> 4
        else -> 5
    }

    private fun richness(i: RoadIncident): Int =
        (if (i.line != null) 4 else 0) + (if (i.delaySec != null) 2 else 0) + (if (i.title != null) 2 else 0) +
            (if (i.road != null) 1 else 0) + i.details.size

    /** Severity ordering for lists: closures and big delays first. */
    fun rank(i: RoadIncident): Int {
        val kindW = when (i.kind) {
            RoadKind.Accident -> 6; RoadKind.Closure -> 5; RoadKind.Jam -> 3
            RoadKind.Breakdown, RoadKind.Hazard -> 3; RoadKind.LaneClosed -> 2
            RoadKind.Roadworks -> 1; else -> 0
        }
        return i.severity * 10 + kindW + ((i.delaySec ?: 0) / 300).coerceAtMost(20)
    }

    /** Overpass QL for road enforcement + traffic cameras in a box. */
    fun overpassQuery(b: GeoBox): String {
        val bb = "(%.5f,%.5f,%.5f,%.5f)".format(java.util.Locale.US, b.south, b.west, b.north, b.east)
        return "[out:json][timeout:25];(" +
            "node[\"highway\"=\"speed_camera\"]$bb;" +
            "relation[\"type\"=\"enforcement\"]$bb;" +
            "node[\"man_made\"=\"surveillance\"][\"surveillance\"=\"traffic\"]$bb;" +
            "node[\"man_made\"=\"surveillance\"][\"surveillance:type\"=\"camera\"][\"surveillance:zone\"=\"traffic\"]$bb;" +
            ");out body;>;out skel qt;"
    }

    class OsmNode(val id: Long, val lat: Double, val lon: Double, val tags: Map<String, String>)
    class OsmMember(val type: String, val ref: Long, val role: String)
    class OsmRelation(val id: Long, val tags: Map<String, String>, val members: List<OsmMember>)

    /** Turn Overpass elements into cameras; relations describe enforcement zones. */
    fun camerasFromOsm(nodes: Map<Long, OsmNode>, relations: List<OsmRelation>): List<RoadCamera> {
        val out = ArrayList<RoadCamera>()
        val used = HashSet<Long>()
        for (r in relations) {
            val enf = r.tags["enforcement"]?.lowercase() ?: continue
            val kind = when {
                enf.contains("average_speed") -> CamKind.AvgSpeed
                enf.contains("traffic_signals") -> CamKind.RedLight
                enf.contains("maxspeed") || enf.contains("speed") -> CamKind.Speed
                else -> continue
            }
            val device = r.members.firstOrNull { it.type == "node" && it.role == "device" }?.let { nodes[it.ref] }
            val from = r.members.firstOrNull { it.type == "node" && it.role == "from" }?.let { nodes[it.ref] }
            val to = r.members.firstOrNull { it.type == "node" && it.role == "to" }?.let { nodes[it.ref] }
            val at = device ?: from ?: continue
            device?.let { used += it.id }
            val maxs = r.tags["maxspeed"] ?: device?.tags?.get("maxspeed")
            out += RoadCamera(
                id = "osm-r${r.id}", source = "OpenStreetMap", kind = kind, lat = at.lat, lon = at.lon,
                maxSpeed = maxs, direction = device?.tags?.get("direction") ?: device?.tags?.get("camera:direction"),
                title = r.tags["name"] ?: device?.tags?.get("name"), ref = r.tags["ref"] ?: device?.tags?.get("ref"),
                operator = r.tags["operator"] ?: device?.tags?.get("operator"),
                endLat = if (kind == CamKind.AvgSpeed) to?.lat else null,
                endLon = if (kind == CamKind.AvgSpeed) to?.lon else null,
            )
        }
        for (n in nodes.values) {
            if (n.id in used || n.tags.isEmpty()) continue
            val kind = when {
                n.tags["highway"] == "speed_camera" -> when (n.tags["speed_camera"]?.lowercase()) {
                    "average_speed", "section" -> CamKind.AvgSpeed
                    "traffic_signals", "red_light" -> CamKind.RedLight
                    else -> CamKind.Speed
                }
                n.tags["man_made"] == "surveillance" -> CamKind.Traffic
                else -> continue
            }
            out += RoadCamera(
                id = "osm-n${n.id}", source = "OpenStreetMap", kind = kind, lat = n.lat, lon = n.lon,
                maxSpeed = n.tags["maxspeed"], direction = n.tags["direction"] ?: n.tags["camera:direction"],
                title = n.tags["name"], ref = n.tags["ref"], operator = n.tags["operator"],
            )
        }
        return out
    }

    /** TomTom language tag for the app language (unsupported → English). */
    fun tomtomLang(lang: String): String = when (lang.substringBefore('-').lowercase()) {
        "ru" -> "ru-RU"; "es" -> "es-ES"; "de" -> "de-DE"; "fr" -> "fr-FR"; "pt" -> "pt-PT"
        "tr" -> "tr-TR"; "zh" -> "zh-TW"; "ja" -> "ja-JP"; else -> "en-GB"
    }

    /** Midpoint (by vertex count) of a polyline. */
    fun midpoint(line: List<DoubleArray>): DoubleArray = line[line.size / 2]
}
