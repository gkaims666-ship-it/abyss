package com.abyss.app.feature.radar

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sun position and sun-times for a point — a chronolocation aid (verify when a
 * photo was taken from shadow direction and length, or plan light). Standard
 * NOAA solar algorithm, pure doubles, unit-tested against known invariants.
 * All times are epoch milliseconds (UTC); the UI formats them to local time.
 */
data class SunPos(val azimuth: Double, val elevation: Double) {
    /** Compass bearing a shadow points to (opposite the sun); null below horizon. */
    val shadowBearing: Double? get() = if (elevation > 0) (azimuth + 180.0) % 360.0 else null
    /** Shadow length as a multiple of object height; null when the sun is down. */
    val shadowFactor: Double? get() = if (elevation > 0.5) 1.0 / tan(Math.toRadians(elevation)) else null
}

/** sunrise/solarNoon/sunset epoch-ms; nulls mean polar day or night. */
data class SunTimes(val sunrise: Long?, val solarNoon: Long, val sunset: Long?, val polarDay: Boolean, val polarNight: Boolean)

object SunMath {
    private const val DAY = 86_400_000.0
    private fun jd(ms: Long) = ms / DAY + 2440587.5
    private fun jc(ms: Long) = (jd(ms) - 2451545.0) / 36525.0

    private data class Solar(val declDeg: Double, val eqTimeMin: Double)

    private fun solar(jc: Double): Solar {
        val gmls = (280.46646 + jc * (36000.76983 + jc * 0.0003032)) % 360.0
        val gmas = 357.52911 + jc * (35999.05029 - 0.0001537 * jc)
        val ecc = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)
        val gmasR = Math.toRadians(gmas)
        val ctr = sin(gmasR) * (1.914602 - jc * (0.004817 + 0.000014 * jc)) +
            sin(2 * gmasR) * (0.019993 - 0.000101 * jc) + sin(3 * gmasR) * 0.000289
        val trueLong = gmls + ctr
        val appLong = trueLong - 0.00569 - 0.00478 * sin(Math.toRadians(125.04 - 1934.136 * jc))
        val obliq = 23.0 + (26.0 + (21.448 - jc * (46.815 + jc * (0.00059 - jc * 0.001813))) / 60.0) / 60.0
        val obliqCorr = obliq + 0.00256 * cos(Math.toRadians(125.04 - 1934.136 * jc))
        val declin = Math.toDegrees(asin(sin(Math.toRadians(obliqCorr)) * sin(Math.toRadians(appLong))))
        val y = tan(Math.toRadians(obliqCorr / 2)).let { it * it }
        val gmlsR = Math.toRadians(gmls)
        val eqTime = 4 * Math.toDegrees(
            y * sin(2 * gmlsR) - 2 * ecc * sin(gmasR) + 4 * ecc * y * sin(gmasR) * cos(2 * gmlsR)
                - 0.5 * y * y * sin(4 * gmlsR) - 1.25 * ecc * ecc * sin(2 * gmasR))
        return Solar(declin, eqTime)
    }

    fun position(lat: Double, lon: Double, epochMs: Long = System.currentTimeMillis()): SunPos {
        val s = solar(jc(epochMs))
        val minutesUtc = ((epochMs % DAY.toLong()) / 60000.0)
        val tst = (minutesUtc + s.eqTimeMin + 4 * lon) % 1440.0
        val ha = tst / 4.0 - 180.0   // degrees, solar noon = 0
        val haR = Math.toRadians(ha)
        val latR = Math.toRadians(lat)
        val declR = Math.toRadians(s.declDeg)
        val zenith = acos(sin(latR) * sin(declR) + cos(latR) * cos(declR) * cos(haR))
        val elev = 90.0 - Math.toDegrees(zenith)
        // Azimuth clockwise from north.
        var az = Math.toDegrees(
            atan2(sin(haR), cos(haR) * sin(latR) - tan(declR) * cos(latR)))
        az = (az + 180.0) % 360.0
        return SunPos(az, elev + refraction(elev))
    }

    /** Atmospheric refraction correction near the horizon, degrees. */
    private fun refraction(elevDeg: Double): Double {
        if (elevDeg > 85.0) return 0.0
        val te = tan(Math.toRadians(elevDeg))
        val r = when {
            elevDeg > 5.0 -> 58.1 / te - 0.07 / (te * te * te) + 0.000086 / Math.pow(te, 5.0)
            elevDeg > -0.575 -> 1735.0 + elevDeg * (-518.2 + elevDeg * (103.4 + elevDeg * (-12.79 + elevDeg * 0.711)))
            else -> -20.774 / te
        }
        return r / 3600.0
    }

    fun times(lat: Double, lon: Double, epochMs: Long = System.currentTimeMillis()): SunTimes {
        val dayStart = (epochMs / DAY.toLong()) * DAY.toLong()
        val s = solar(jc(dayStart + 43_200_000L))
        val latR = Math.toRadians(lat)
        val declR = Math.toRadians(s.declDeg)
        // Noon (solar) in minutes UTC, then epoch.
        val noonMin = 720.0 - 4 * lon - s.eqTimeMin
        val noonMs = dayStart + (noonMin * 60000.0).toLong()
        val cosH = (cos(Math.toRadians(90.833)) / (cos(latR) * cos(declR))) - tan(latR) * tan(declR)
        if (cosH < -1.0) return SunTimes(null, noonMs, null, polarDay = true, polarNight = false)
        if (cosH > 1.0) return SunTimes(null, noonMs, null, polarDay = false, polarNight = true)
        val ha = Math.toDegrees(acos(cosH))   // degrees
        val riseMin = noonMin - ha * 4.0
        val setMin = noonMin + ha * 4.0
        return SunTimes(
            dayStart + (riseMin * 60000.0).toLong(), noonMs, dayStart + (setMin * 60000.0).toLong(),
            polarDay = false, polarNight = false)
    }

    /** 16-point compass label for a bearing. */
    fun compass(bearing: Double): String {
        val names = arrayOf("N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW")
        return names[(((bearing % 360) + 360) % 360 / 22.5).toInt() % 16]
    }
}
