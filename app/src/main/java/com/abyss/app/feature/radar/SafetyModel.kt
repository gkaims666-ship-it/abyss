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
     * The alert covering the user's region. Prefers a city-level match (e.g.
     * Kyiv city), then the oblast, choosing the most serious if several apply.
     */
    fun matchRegion(state: String?, city: String?, country: String?, active: List<RegionAlert>): RegionAlert? {
        if (!isUkraine(country)) return null
        val ns = normalize(state)
        val nc = normalize(city)
        val matches = active.filter { a ->
            val t = normalize(a.title)
            (nc.isNotEmpty() && a.locationType == "city" && t == nc) ||
                (ns.isNotEmpty() && t == ns) ||
                (nc.isNotEmpty() && t == nc)
        }
        return matches.minWithOrNull(::worseFirst)
    }

    fun redCount(active: List<RegionAlert>) = active.count { it.level == AlertLevel.Red }
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
            "nwr[\"amenity\"=\"shelter\"][\"shelter_type\"=\"public_transport\"][\"covered\"=\"yes\"]$bb;" +
            ");out center tags;"
    }

    fun kindOf(tags: Map<String, String>): ShelterKind {
        val st = tags["shelter_type"]?.lowercase()
        val bt = tags["bunker_type"]?.lowercase()
        val railway = tags["railway"]?.lowercase()
        val amenity = tags["amenity"]?.lowercase()
        return when {
            st == "bomb_shelter" || bt == "bomb_shelter" || tags["building"]?.lowercase() == "civil_defense" -> ShelterKind.Bomb
            railway == "station" && tags["station"]?.lowercase() == "subway" || tags["subway"] == "yes" -> ShelterKind.Metro
            tags["location"]?.lowercase() == "underground" || amenity == "parking" && tags["parking"]?.lowercase() == "underground" -> ShelterKind.Underground
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
