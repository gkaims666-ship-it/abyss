package com.abyss.app.feature.radar

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Coordinate formats for a map point — a standard open-source-geolocation aid.
 * Pure WGS-84 maths (no Android), unit-tested against known reference points.
 * Decimal, degrees-minutes-seconds, UTM and MGRS.
 */
data class Utm(val zone: Int, val hemisphere: Char, val easting: Double, val northing: Double) {
    fun format(): String = "%d%c %.0f E  %.0f N".format(java.util.Locale.US, zone, hemisphere, easting, northing)
}

object GeoFormats {
    private const val A = 6378137.0
    private const val F = 1.0 / 298.257223563
    private const val K0 = 0.9996
    private val E2 = F * (2 - F)
    private val EP2 = E2 / (1 - E2)

    fun decimal(lat: Double, lon: Double): String =
        "%.6f, %.6f".format(java.util.Locale.US, lat, lon)

    fun dms(lat: Double, lon: Double): String {
        fun one(v: Double, pos: Char, neg: Char): String {
            val h = if (v >= 0) pos else neg
            var x = abs(v)
            val d = floor(x).toInt(); x = (x - d) * 60
            val m = floor(x).toInt(); val s = (x - m) * 60
            return "%d°%02d'%04.1f\"%c".format(java.util.Locale.US, d, m, s, h)
        }
        return one(lat, 'N', 'S') + " " + one(lon, 'E', 'W')
    }

    fun utmZone(lat: Double, lon: Double): Int {
        var z = (floor((lon + 180) / 6).toInt() % 60) + 1
        // Norway / Svalbard exceptions
        if (lat in 56.0..64.0 && lon in 3.0..12.0) z = 32
        if (lat in 72.0..84.0) {
            when {
                lon in 0.0..9.0 -> z = 31
                lon in 9.0..21.0 -> z = 33
                lon in 21.0..33.0 -> z = 35
                lon in 33.0..42.0 -> z = 37
            }
        }
        return z
    }

    fun utm(lat: Double, lon: Double): Utm {
        val zone = utmZone(lat, lon)
        val lon0 = Math.toRadians((zone - 1) * 6.0 - 180.0 + 3.0)
        val phi = Math.toRadians(lat)
        val lam = Math.toRadians(lon)
        val n = A / sqrt(1 - E2 * sin(phi) * sin(phi))
        val t = tan(phi) * tan(phi)
        val c = EP2 * cos(phi) * cos(phi)
        val a1 = cos(phi) * (lam - lon0)
        val m = A * ((1 - E2 / 4 - 3 * E2 * E2 / 64 - 5 * E2 * E2 * E2 / 256) * phi
            - (3 * E2 / 8 + 3 * E2 * E2 / 32 + 45 * E2 * E2 * E2 / 1024) * sin(2 * phi)
            + (15 * E2 * E2 / 256 + 45 * E2 * E2 * E2 / 1024) * sin(4 * phi)
            - (35 * E2 * E2 * E2 / 3072) * sin(6 * phi))
        var easting = K0 * n * (a1 + (1 - t + c) * a1 * a1 * a1 / 6
            + (5 - 18 * t + t * t + 72 * c - 58 * EP2) * Math.pow(a1, 5.0) / 120) + 500000.0
        var northing = K0 * (m + n * tan(phi) * (a1 * a1 / 2
            + (5 - t + 9 * c + 4 * c * c) * Math.pow(a1, 4.0) / 24
            + (61 - 58 * t + t * t + 600 * c - 330 * EP2) * Math.pow(a1, 6.0) / 720))
        val hemi = if (lat >= 0) 'N' else 'S'
        if (lat < 0) northing += 10_000_000.0
        return Utm(zone, hemi, easting, northing)
    }

    private const val BANDS = "CDEFGHJKLMNPQRSTUVWX"   // 8° bands from -80

    fun latBand(lat: Double): Char {
        if (lat < -80) return 'C'
        if (lat >= 84) return 'X'
        val i = ((lat + 80) / 8).toInt().coerceIn(0, BANDS.length - 1)
        return BANDS[i]
    }

    fun mgrs(lat: Double, lon: Double): String {
        val u = utm(lat, lon)
        val band = latBand(lat)
        val colSet = when (u.zone % 3) { 1 -> "ABCDEFGH"; 2 -> "JKLMNPQR"; else -> "STUVWXYZ" }
        val colIdx = (floor(u.easting / 100000).toInt() - 1).coerceIn(0, 7)
        val colLetter = colSet[colIdx]
        val rowSet = if (u.zone % 2 == 1) "ABCDEFGHJKLMNPQRSTUV" else "FGHJKLMNPQRSTUVABCDE"
        val rowIdx = (floor((u.northing % 2_000_000) / 100000).toInt()) % rowSet.length
        val rowLetter = rowSet[rowIdx]
        val e5 = (u.easting % 100000).toInt()
        val n5 = (u.northing % 100000).toInt()
        return "%d%c %c%c %05d %05d".format(java.util.Locale.US, u.zone, band, colLetter, rowLetter, e5, n5)
    }
}
