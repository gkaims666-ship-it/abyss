package com.abyss.app.feature.radar

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Aircraft model, multi-network merge and geodesy. Pure Kotlin (no Android,
 * no network) so the merge rules are unit-tested on a plain JVM.
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
    /** Networks that reported this aircraft (e.g. "adsb.lol", "OpenSky Network"). */
    val sources: Set<String> = emptySet(),
    /** Country of registration, when a network reports it. */
    val country: String? = null,
) {
    /** 7500 hijack, 7600 radio failure, 7700 general emergency, or a declared emergency. */
    val isEmergency: Boolean
        get() = squawk == "7500" || squawk == "7600" || squawk == "7700" ||
            (emergency != null && emergency != "none")

    /** Age of the position fix in seconds at [nowMs]. */
    fun ageSec(nowMs: Long): Double = seenPos + (nowMs - fetchedAt) / 1000.0

    /**
     * Dead-reckoned position now: moves along the track at ground speed since
     * the last fix (capped at 75 s), so planes glide smoothly between updates.
     */
    fun positionAt(nowMs: Long, out: DoubleArray) {
        out[0] = lat; out[1] = lon
        val gs = gsKt ?: return
        val tr = track ?: return
        if (onGround && gs < 40.0) return
        val dt = ageSec(nowMs).coerceIn(0.0, 75.0)
        destination(lat, lon, tr, gs * dt / 3600.0, out)
    }
}

data class Airport(val icao: String?, val iata: String?, val name: String?, val city: String?, val lat: Double, val lon: Double)

data class Route(val origin: Airport?, val destination: Airport?, val codes: String?)

/**
 * Combines two reports of the same aircraft from different networks: the
 * fresher position wins, and every field the fresher report lacks is filled in
 * from the other one. Sources are unioned.
 */
fun mergePlane(a: Plane?, b: Plane, nowMs: Long): Plane {
    if (a == null) return b
    val (f, o) = if (b.ageSec(nowMs) < a.ageSec(nowMs)) b to a else a to b
    return f.copy(
        callsign = f.callsign ?: o.callsign,
        reg = f.reg ?: o.reg,
        type = f.type ?: o.type,
        desc = f.desc ?: o.desc,
        operator = f.operator ?: o.operator,
        altFt = f.altFt ?: o.altFt,
        gsKt = f.gsKt ?: o.gsKt,
        track = f.track ?: o.track,
        vrateFpm = f.vrateFpm ?: o.vrateFpm,
        squawk = f.squawk ?: o.squawk,
        emergency = f.emergency ?: o.emergency,
        military = f.military || o.military,
        category = f.category ?: o.category,
        country = f.country ?: o.country,
        sources = f.sources + o.sources,
    )
}

/** Merges per-network lists into one list keyed by ICAO hex (first-seen order kept). */
fun mergeAll(lists: List<List<Plane>>, nowMs: Long): List<Plane> {
    val map = LinkedHashMap<String, Plane>()
    for (l in lists) for (p in l) map[p.hex] = mergePlane(map[p.hex], p, nowMs)
    return map.values.toList()
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
