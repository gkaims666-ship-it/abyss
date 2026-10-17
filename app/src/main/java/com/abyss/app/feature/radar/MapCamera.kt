package com.abyss.app.feature.radar

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * The map camera as plain numbers — centre, zoom, bearing — with the three
 * operations a finger can perform: drag, zoom about a point, rotate about a
 * point. Every operation keeps the geographic point under the finger(s) exactly
 * under the finger(s), which is what makes a map feel like a physical sheet.
 *
 * Screen model (same as osmdroid): the unrotated map is rotated by [bearing]
 * degrees around the view centre (Android canvas rotation, clockwise on screen).
 * World coordinates are Web-Mercator pixels at the current zoom, map size
 * [mapSize] (zoom) pixels.
 */
class MapCamera(
    var lat: Double = 0.0,
    var lon: Double = 0.0,
    var zoom: Double = 3.0,
    var bearing: Double = 0.0,
    var minZoom: Double = 2.0,
    var maxZoom: Double = 19.0,
    private val mapSize: (Double) -> Double = { z -> 256.0 * 2.0.pow(z) },
) {
    var width = 1f
    var height = 1f

    // ---------------------------------------------------------------- ops

    /** Content follows a finger that moved by (dx, dy) screen pixels. */
    fun pan(dx: Float, dy: Float) {
        val s = mapSize(zoom)
        var x = mercX(lon, s)
        var y = mercY(lat, s)
        unrotate(dx.toDouble(), dy.toDouble(), bearing)
        x -= rx; y -= ry
        setFromWorld(x, y, s)
    }

    /** Zoom by [dz] levels keeping the point under (fx, fy) fixed. */
    fun zoomAround(dz: Double, fx: Float, fy: Float) {
        val nz = (zoom + dz).coerceIn(minZoom, maxZoom)
        if (nz == zoom) return
        val s = mapSize(zoom)
        val ns = mapSize(nz)
        val k = ns / s
        unrotate((fx - width / 2f).toDouble(), (fy - height / 2f).toDouble(), bearing)
        val vx = rx; val vy = ry
        val wx = (mercX(lon, s) + vx) * k
        val wy = (mercY(lat, s) + vy) * k
        zoom = nz
        setFromWorld(wx - vx, wy - vy, ns)
    }

    /** Rotate the map by [deg] (screen-clockwise positive) about (fx, fy). */
    fun rotateAround(deg: Double, fx: Float, fy: Float) {
        if (deg == 0.0) return
        val s = mapSize(zoom)
        val ox = (fx - width / 2f).toDouble()
        val oy = (fy - height / 2f).toDouble()
        unrotate(ox, oy, bearing)
        val wx = mercX(lon, s) + rx
        val wy = mercY(lat, s) + ry
        bearing = normBearing(bearing + deg)
        unrotate(ox, oy, bearing)
        setFromWorld(wx - rx, wy - ry, s)
    }

    /** Geographic point under a screen pixel. */
    fun screenToGeo(px: Float, py: Float, out: DoubleArray) {
        val s = mapSize(zoom)
        unrotate((px - width / 2f).toDouble(), (py - height / 2f).toDouble(), bearing)
        val x = mercX(lon, s) + rx
        val y = mercY(lat, s) + ry
        out[0] = latOf(y, s)
        out[1] = wrapLon(lonOf(x, s))
    }

    /** Screen pixel of a geographic point (nearest world copy horizontally). */
    fun geoToScreen(la: Double, lo: Double, out: FloatArray) {
        val s = mapSize(zoom)
        var dx = mercX(lo, s) - mercX(lon, s)
        if (dx > s / 2) dx -= s
        if (dx < -s / 2) dx += s
        val dy = mercY(la, s) - mercY(lat, s)
        // rotate by +bearing
        val t = bearing * PI / 180.0
        val c = cos(t); val sn = sin(t)
        out[0] = (dx * c - dy * sn + width / 2f).toFloat()
        out[1] = (dx * sn + dy * c + height / 2f).toFloat()
    }

    fun set(la: Double, lo: Double, z: Double = zoom, b: Double = bearing) {
        zoom = z.coerceIn(minZoom, maxZoom)
        bearing = normBearing(b)
        val s = mapSize(zoom)
        setFromWorld(mercX(lo, s), mercY(la, s), s)
    }

    // ---------------------------------------------------------------- internals

    private var rx = 0.0
    private var ry = 0.0

    /** Screen-space vector → unrotated-map vector (rotate by −bearing). */
    private fun unrotate(x: Double, y: Double, b: Double) {
        val t = -b * PI / 180.0
        val c = cos(t); val s = sin(t)
        rx = x * c - y * s
        ry = x * s + y * c
    }

    private fun setFromWorld(x: Double, y0: Double, s: Double) {
        // Keep the world's top/bottom edge from sliding into view (no grey void).
        val half = height / 2.0
        val y = if (s <= height) s / 2 else y0.coerceIn(half, s - half)
        lat = latOf(y, s)
        lon = wrapLon(lonOf(x, s))
    }

    companion object {
        const val MAX_LAT = 85.05112878

        fun mercX(lon: Double, s: Double) = (lon + 180.0) / 360.0 * s
        fun mercY(lat: Double, s: Double): Double {
            val la = lat.coerceIn(-MAX_LAT, MAX_LAT) * PI / 180.0
            val sn = sin(la)
            return (0.5 - ln((1 + sn) / (1 - sn)) / (4 * PI)) * s
        }
        fun lonOf(x: Double, s: Double) = x / s * 360.0 - 180.0
        fun latOf(y: Double, s: Double): Double {
            val n = PI * (1 - 2 * y / s)
            return atan(0.5 * (exp(n) - exp(-n))) * 180.0 / PI
        }
        fun wrapLon(l: Double): Double {
            var v = (l + 180.0) % 360.0
            if (v < 0) v += 360.0
            return v - 180.0
        }
        fun normBearing(b: Double): Double {
            var v = b % 360.0
            if (v < 0) v += 360.0
            return v
        }
        /** Shortest signed angular difference to - from, in (−180, 180]. */
        fun angleDelta(from: Double, to: Double): Double {
            var d = (to - from) % 360.0
            if (d > 180) d -= 360.0
            if (d <= -180) d += 360.0
            return d
        }
    }
}
