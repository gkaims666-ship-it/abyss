package com.abyss.app.feature.radar

import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Pure geodesy/astronomy for the radar: the day/night terminator and a
 * tolerant "go to coordinates" parser. No Android dependencies, so it is
 * unit-tested on a plain JVM.
 */

/** Where the sun is directly overhead right now, and the resulting terminator. */
data class SubSolar(val lat: Double, val lon: Double, val declination: Double)

object SunCalc {
    /** Sub-solar point for [epochMs] (default: now). Standard low-precision solar model. */
    fun subSolar(epochMs: Long = System.currentTimeMillis()): SubSolar {
        val jd = epochMs / 86_400_000.0 + 2440587.5
        val d = jd - 2451545.0
        val g = Math.toRadians((357.529 + 0.98560028 * d).mod(360.0))
        val q = (280.459 + 0.98564736 * d).mod(360.0)
        val lsun = Math.toRadians((q + 1.915 * sin(g) + 0.020 * sin(2 * g)).mod(360.0))
        val e = Math.toRadians(23.439 - 0.00000036 * d)
        val dec = asin(sin(e) * sin(lsun))
        val ra = Math.toDegrees(atan2(cos(e) * sin(lsun), cos(lsun)))
        val gmst = (280.46061837 + 360.98564736629 * d).mod(360.0)
        val lon = ((ra - gmst + 540.0).mod(360.0)) - 180.0
        return SubSolar(Math.toDegrees(dec), lon, dec)
    }

    /**
     * A closed polygon (lat/lon pairs) covering the night hemisphere for
     * [epochMs], ready to fill on the map. Returns [] near an equinox where the
     * declination is ~0 (the polygon degenerates).
     */
    fun nightPolygon(epochMs: Long = System.currentTimeMillis(), stepDeg: Int = 2): List<DoubleArray> {
        val s = subSolar(epochMs)
        if (kotlin.math.abs(Math.toDegrees(s.declination)) < 0.3) return emptyList()
        val tanDec = tan(s.declination)
        val edge = ArrayList<DoubleArray>()
        var lon = -180.0
        while (lon <= 180.0) {
            val h = Math.toRadians(lon - s.lon)
            val lat = Math.toDegrees(atan(-cos(h) / tanDec))
            edge.add(doubleArrayOf(lat, lon))
            lon += stepDeg
        }
        // The dark pole: opposite the sun's hemisphere.
        val pole = if (Math.toDegrees(s.declination) > 0) -90.0 else 90.0
        edge.add(doubleArrayOf(pole, 180.0))
        edge.add(doubleArrayOf(pole, -180.0))
        return edge
    }
}

/** A parsed coordinate. */
data class LatLon(val lat: Double, val lon: Double)

/**
 * Parses coordinates a person might paste: "55.75, 37.62", "55.75 37.62",
 * "55.75N 37.62E", "37°37'E 55°45'N", with the two values in either order.
 * Returns null if the text isn't a coordinate pair.
 */
fun parseLatLon(input: String): LatLon? {
    val t = input.trim()
    if (t.isEmpty()) return null
    // Decimal with optional hemisphere letter.
    val dec = Regex("([+-]?\\d{1,3}(?:\\.\\d+)?)\\s*([NSEWnsew])?")
    // DMS: 55°45'30.5"N
    val dms = Regex("(\\d{1,3})[°º:\\s]+(\\d{1,2})['′:\\s]+(\\d{1,2}(?:\\.\\d+)?)?[\"″]?\\s*([NSEWnsew])")

    data class V(val deg: Double, val hemi: Char?)
    val vals = ArrayList<V>()

    val dmsMatches = dms.findAll(t).toList()
    if (dmsMatches.size >= 2) {
        for (m in dmsMatches.take(2)) {
            val deg = m.groupValues[1].toDouble() + m.groupValues[2].toDouble() / 60.0 +
                (m.groupValues[3].toDoubleOrNull() ?: 0.0) / 3600.0
            vals.add(V(deg, m.groupValues[4].firstOrNull()))
        }
    } else {
        for (m in dec.findAll(t)) {
            val num = m.groupValues[1].toDoubleOrNull() ?: continue
            val hemi = m.groupValues[2].firstOrNull()
            vals.add(V(num, hemi))
            if (vals.size == 2) break
        }
    }
    if (vals.size < 2) return null

    fun signed(v: V): Double {
        val c = v.hemi?.uppercaseChar()
        return if (c == 'S' || c == 'W') -kotlin.math.abs(v.deg) else v.deg
    }
    val a = vals[0]; val b = vals[1]
    // Decide which value is latitude: by hemisphere letter, else assume lat first.
    val aIsLat = when (a.hemi?.uppercaseChar()) {
        'N', 'S' -> true
        'E', 'W' -> false
        else -> when (b.hemi?.uppercaseChar()) { 'N', 'S' -> false; 'E', 'W' -> true; else -> true }
    }
    val lat = signed(if (aIsLat) a else b)
    val lon = signed(if (aIsLat) b else a)
    if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) return null
    return LatLon(lat, lon)
}
