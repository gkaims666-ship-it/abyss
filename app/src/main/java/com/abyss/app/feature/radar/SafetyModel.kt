package com.abyss.app.feature.radar

/**
 * Civil-defense model: public air-raid alert status (what the official siren
 * system broadcasts to the population) and civilian shelters from open map
 * data. This exists to warn people and help them reach cover — it carries no
 * positions, counts or trajectories of any military object. Pure JVM types,
 * unit-tested.
 */

enum class AlertLevel { None, Yellow, Red }

enum class AlertKind { AirRaid, Artillery, UrbanFights, Chemical, Nuclear, Other }

/** One region currently under a public alert. */
data class RegionAlert(
    val uid: String,
    val title: String,
    val titleEn: String?,
    val kind: AlertKind,
    val level: AlertLevel,
    val locationType: String,
    val since: String?,
    val threats: List<String> = emptyList(),
)

data class AlertState(
    val active: List<RegionAlert>,
    val fetchedAt: Long,
    val ok: Boolean,
    /** The alert covering the user's own region, if any. */
    val mine: RegionAlert? = null,
    val myRegion: String? = null,
)

enum class ShelterKind { Bomb, Metro, Underground, Parking, General }

data class Shelter(
    val id: String,
    val lat: Double,
    val lon: Double,
    val name: String?,
    val kind: ShelterKind,
    val access: String?,
    val operator: String?,
    val capacity: String?,
    val distanceNm: Double = 0.0,
) {
    val osmUrl get() = "https://www.openstreetmap.org/" +
        (if (id.startsWith("w")) "way/" + id.drop(1) else "node/" + id.drop(1))
}

object AlertLogic {
    fun kindOf(type: String?): AlertKind = when (type?.lowercase()) {
        "air_raid" -> AlertKind.AirRaid
        "artillery_shelling" -> AlertKind.Artillery
        "urban_fights" -> AlertKind.UrbanFights
        "chemical" -> AlertKind.Chemical
        "nuclear" -> AlertKind.Nuclear
        else -> AlertKind.Other
    }

    fun levelOf(level: String?): AlertLevel = when (level?.lowercase()) {
        "red" -> AlertLevel.Red
        "yellow" -> AlertLevel.Yellow
        else -> AlertLevel.Red   // active alert with no stated level = treat as red
    }

    fun rank(l: AlertLevel) = when (l) { AlertLevel.Red -> 2; AlertLevel.Yellow -> 1; AlertLevel.None -> 0 }

    /** Compare for sorting: most serious first, then air-raid before others. */
    fun worseFirst(a: RegionAlert, b: RegionAlert): Int {
        val l = rank(b.level) - rank(a.level)
        if (l != 0) return l
        return kindWeight(b.kind) - kindWeight(a.kind)
    }

    private fun kindWeight(k: AlertKind) = when (k) {
        AlertKind.Nuclear -> 5; AlertKind.Chemical -> 4; AlertKind.AirRaid -> 3
        AlertKind.Artillery -> 2; AlertKind.UrbanFights -> 1; AlertKind.Other -> 0
    }

    /** Oblast / city name reduced to comparable letters (handles "область", "м." etc). */
    fun normalize(s: String?): String {
        if (s == null) return ""
        var t = s.lowercase()
        for (w in listOf("область", "oblast", "місто", "город", "city of", "м.", "obl.", "район", "raion")) {
            t = t.replace(w, " ")
        }
        return t.filter { it.isLetter() }
    }

    private fun isUkraine(country: String?): Boolean {
        val c = (country ?: return true).lowercase()
        return c.isEmpty() || c == "ua" || c == "ukraine" || c == "україна" || c == "украина"
    }

    /**
     * The alert covering the user's region, matched from the most specific
     * administrative level down to the oblast: city / hromada → raion → oblast.
     * A match at a finer level wins over a coarser one even if less severe,
     * because it describes the user's place more precisely; ties within a level
     * are broken by severity.
     */
    fun matchRegion(
        state: String?, county: String?, municipality: String?, city: String?,
        country: String?, active: List<RegionAlert>,
    ): RegionAlert? {
        if (!isUkraine(country)) return null
        // Each tier: (candidate name, the location_types that represent it)
        val tiers = listOf(
            normalize(city) to setOf("city", "hromada"),
            normalize(municipality) to setOf("hromada", "city"),
            normalize(county) to setOf("raion"),
            normalize(state) to setOf("oblast"),
        )
        for ((name, types) in tiers) {
            if (name.isEmpty()) continue
            val hit = active.filter { it.locationType in types && normalize(it.title) == name }
                .minWithOrNull(::worseFirst)
            if (hit != null) return hit
        }
        // Fallback: any alert whose title matches the oblast name, any level.
        val ns = normalize(state)
        if (ns.isNotEmpty()) return active.filter { normalize(it.title) == ns }.minWithOrNull(::worseFirst)
        return null
    }

    fun redCount(active: List<RegionAlert>) = active.count { it.level == AlertLevel.Red }
}

/** One past alert for the user's region, kept locally for the history view. */
data class AlertEvent(val kindName: String, val level: String, val region: String, val startMs: Long, val endMs: Long)

object AlertHistory {
    /** Encode one event to a single prefs line (no separators clash: region last). */
    fun encode(e: AlertEvent) = listOf(e.kindName, e.level, e.startMs.toString(), e.endMs.toString(), e.region).joinToString("\u001F")
    fun decode(line: String): AlertEvent? {
        val p = line.split("\u001F")
        if (p.size < 5) return null
        return AlertEvent(p[0], p[1], p.subList(4, p.size).joinToString("\u001F"),
            p[2].toLongOrNull() ?: return null, p[3].toLongOrNull() ?: return null)
    }
    fun encodeAll(list: List<AlertEvent>) = list.takeLast(100).joinToString("\n") { encode(it) }
    fun decodeAll(blob: String?) = blob?.lineSequence()?.mapNotNull { decode(it) }?.toList().orEmpty()
}

object ShelterLogic {
    fun overpassQuery(b: GeoBox): String {
        val c = b.clampDegrees(0.5)
        val bb = "(%.5f,%.5f,%.5f,%.5f)".format(java.util.Locale.US, c.south, c.west, c.north, c.east)
        return "[out:json][timeout:25];(" +
            "nwr[\"amenity\"=\"shelter\"][\"shelter_type\"=\"bomb_shelter\"]$bb;" +
            "nwr[\"emergency\"=\"shelter\"]$bb;" +
            "nwr[\"bunker_type\"=\"bomb_shelter\"]$bb;" +
            "nwr[\"building\"=\"civil_defense\"]$bb;" +
            "nwr[\"railway\"=\"subway_entrance\"]$bb;" +
            "nwr[\"station\"=\"subway\"]$bb;" +
            "nwr[\"amenity\"=\"parking\"][\"location\"=\"underground\"]$bb;" +
            "nwr[\"amenity\"=\"parking\"][\"parking\"=\"underground\"]$bb;" +
            ");out center tags;"
    }

    fun kindOf(tags: Map<String, String>): ShelterKind {
        val st = tags["shelter_type"]?.lowercase()
        val bt = tags["bunker_type"]?.lowercase()
        val railway = tags["railway"]?.lowercase()
        val amenity = tags["amenity"]?.lowercase()
        return when {
            st == "bomb_shelter" || bt == "bomb_shelter" || tags["building"]?.lowercase() == "civil_defense" -> ShelterKind.Bomb
            railway == "subway_entrance" || tags["station"]?.lowercase() == "subway" ||
                (railway == "station" && tags["subway"]?.lowercase() == "yes") -> ShelterKind.Metro
            amenity == "parking" &&
                (tags["location"]?.lowercase() == "underground" || tags["parking"]?.lowercase() == "underground") -> ShelterKind.Underground
            amenity == "parking" -> ShelterKind.Parking
            else -> ShelterKind.General
        }
    }

    /** Build shelters from already-parsed Overpass elements (lat/lon/tags). */
    fun fromElements(elements: List<Triple<String, DoubleArray, Map<String, String>>>): List<Shelter> =
        elements.mapNotNull { (id, pt, tags) ->
            if (tags["access"]?.lowercase() == "private" || tags["access"]?.lowercase() == "no") return@mapNotNull null
            Shelter(
                id = id, lat = pt[0], lon = pt[1],
                name = tags["name"] ?: tags["name:uk"] ?: tags["official_name"],
                kind = kindOf(tags),
                access = tags["access"],
                operator = tags["operator"] ?: tags["operator:uk"],
                capacity = tags["capacity"] ?: tags["capacity:persons"],
            )
        }

    fun nearest(list: List<Shelter>, lat: Double, lon: Double, limit: Int = 25): List<Shelter> =
        list.map { it.copy(distanceNm = distanceNm(lat, lon, it.lat, it.lon)) }
            .sortedBy { it.distanceNm }
            .take(limit)
}
