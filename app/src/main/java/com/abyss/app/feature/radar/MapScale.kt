package com.abyss.app.feature.radar

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

enum class ScaleUnit { M, KM, FT, MI }

/** A chosen scale-bar segment: how wide to draw it and what round value it marks. */
data class ScaleChoice(val px: Float, val value: Int, val unit: ScaleUnit)

/**
 * The map's distance ruler. Pure Web-Mercator maths, so it is unit-tested:
 * pick the largest "nice" distance (1 / 2 / 5 × 10ⁿ) whose bar fits within
 * [maxPx] at the current latitude and zoom, in metric or imperial units.
 */
object MapScale {
    private const val EARTH_CIRCUM = 2 * Math.PI * 6378137.0   // metres
    private const val MILE_M = 1609.344
    private const val FOOT_M = 0.3048

    fun metersPerPixel(lat: Double, zoom: Double): Double =
        EARTH_CIRCUM * cos(lat * Math.PI / 180.0) / (256.0 * 2.0.pow(zoom))

    private fun niceBelow(max: Double): Double {
        if (max <= 0) return 0.0
        val p = 10.0.pow(floor(log10(max)))
        return when {
            max >= 5 * p -> 5 * p
            max >= 2 * p -> 2 * p
            else -> p
        }
    }

    fun choose(lat: Double, zoom: Double, metric: Boolean, maxPx: Float): ScaleChoice {
        val mpp = metersPerPixel(lat.coerceIn(-85.0, 85.0), zoom)
        val maxMeters = maxPx * mpp
        if (maxMeters <= 0 || mpp <= 0) return ScaleChoice(0f, 0, ScaleUnit.M)
        return if (metric) {
            val m = niceBelow(maxMeters)
            if (m >= 1000) ScaleChoice((m / mpp).toFloat(), (m / 1000).toInt(), ScaleUnit.KM)
            else ScaleChoice((m / mpp).toFloat(), m.toInt().coerceAtLeast(1), ScaleUnit.M)
        } else {
            if (maxMeters >= MILE_M) {
                val mi = niceBelow(maxMeters / MILE_M)
                ScaleChoice((mi * MILE_M / mpp).toFloat(), mi.toInt().coerceAtLeast(1), ScaleUnit.MI)
            } else {
                val ft = niceBelow(maxMeters / FOOT_M)
                ScaleChoice((ft * FOOT_M / mpp).toFloat(), ft.toInt().coerceAtLeast(1), ScaleUnit.FT)
            }
        }
    }
}
