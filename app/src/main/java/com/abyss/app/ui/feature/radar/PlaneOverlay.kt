package com.abyss.app.ui.feature.radar

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface
import android.view.MotionEvent
import com.abyss.app.feature.radar.Plane
import com.abyss.app.feature.radar.Route
import com.abyss.app.feature.radar.distanceNm
import com.abyss.app.feature.radar.interpolate
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Draws every aircraft as a heading-rotated silhouette, dead-reckoned to the
 * current moment (so they glide between data updates), plus — for the
 * selected one — its flown trail and the remaining great-circle leg to the
 * destination. Pure canvas drawing, zero allocations per frame.
 */
class PlaneOverlay(private val density: Float) : Overlay() {

    // ---- data (set from the UI thread, read while drawing on the UI thread)
    var planes: List<Plane> = emptyList()
    var selectedHex: String? = null
    var trail: List<DoubleArray> = emptyList()
    var route: Route? = null
    var me: DoubleArray? = null
    var onTap: ((Plane?) -> Unit)? = null
    /** Called when the user drags/pinches the map (cancels "follow"). */
    var onUserGesture: (() -> Unit)? = null

    // ---- colours (ARGB), refreshed with the palette
    var lowColor = 0xFFE8A35C.toInt()
    var highColor = 0xFFD97757.toInt()
    var selectedColor = -0x1
    var glowColor = 0xFFD97757.toInt()
    var groundColor = 0xFF8C8C8C.toInt()
    var emergencyColor = 0xFFE5484D.toInt()
    var militaryColor = 0xFF8FBF6A.toInt()
    var labelColor = 0xCCFFFFFF.toInt()
    var outlineColor = 0xAA000000.toInt()

    private val icon = planePath()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val dash = DashPathEffect(floatArrayOf(10f * density, 8f * density), 0f)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = 10.5f * density
    }
    private val linePath = Path()
    private val pt = Point()
    private val geo = GeoPoint(0.0, 0.0)
    private val pos = DoubleArray(2)
    private val tmp = DoubleArray(2)
    private val clip = Rect()

    // Screen positions of the last frame, for tap hit-testing.
    private var hitX = FloatArray(512)
    private var hitY = FloatArray(512)
    private var hitPlane = arrayOfNulls<Plane>(512)
    private var hitCount = 0

    override fun draw(c: Canvas, mv: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = mv.projection
        val now = System.currentTimeMillis()
        c.getClipBounds(clip)
        val margin = 40f * density
        val zoom = mv.zoomLevelDouble
        val size = (if (zoom >= 9) 12f else if (zoom >= 6) 10f else 8f) * density
        val labels = zoom >= 8.5

        val sel = selectedHex?.let { h -> planes.firstOrNull { it.hex == h } }
        if (sel != null) drawSelectedPaths(c, mv, sel, now)

        // "You are here"
        me?.let { m ->
            geo.setCoords(m[0], m[1]); proj.toPixels(geo, pt)
            fill.color = 0x334C8DFF; c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 16f * density, fill)
            fill.color = 0xFF4C8DFF.toInt(); c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 5.5f * density, fill)
            stroke.color = -0x1; stroke.strokeWidth = 2f * density
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 5.5f * density, stroke)
        }

        ensureHitCapacity(planes.size)
        hitCount = 0
        for (p in planes) {
            if (p.hex == selectedHex) continue          // drawn last, on top
            drawPlane(c, mv, p, now, size, false, labels, margin)
        }
        if (sel != null) drawPlane(c, mv, sel, now, size * 1.25f, true, true, margin)
    }

    private fun drawPlane(
        c: Canvas, mv: MapView, p: Plane, now: Long, size: Float,
        selected: Boolean, label: Boolean, margin: Float,
    ) {
        p.positionAt(now, pos)
        geo.setCoords(pos[0], pos[1])
        mv.projection.toPixels(geo, pt)
        val x = pt.x.toFloat()
        val y = pt.y.toFloat()
        if (x < clip.left - margin || x > clip.right + margin || y < clip.top - margin || y > clip.bottom + margin) return

        hitX[hitCount] = x; hitY[hitCount] = y; hitPlane[hitCount] = p; hitCount++

        val color = when {
            selected -> selectedColor
            p.isEmergency -> emergencyColor
            p.military -> militaryColor
            p.onGround -> groundColor
            else -> lerpColor(lowColor, highColor, ((p.altFt ?: 0) / 38000f).coerceIn(0f, 1f))
        }
        c.save()
        c.translate(x, y)
        c.rotate((p.track ?: 0.0).toFloat())
        val ground = p.onGround || p.category?.startsWith("C") == true
        if (ground && !selected) {
            fill.color = color
            c.drawCircle(0f, 0f, size * 0.32f, fill)
        } else {
            c.scale(size, size)
            if (selected) {
                fill.color = (glowColor and 0x00FFFFFF) or 0x55000000
                c.save(); c.scale(1.45f, 1.45f); c.drawPath(icon, fill); c.restore()
            }
            fill.color = color
            c.drawPath(icon, fill)
            stroke.color = outlineColor
            stroke.strokeWidth = 1.1f * density / size
            c.drawPath(icon, stroke)
        }
        c.restore()

        if (label) {
            val name = p.callsign ?: p.reg ?: return
            text.color = if (selected) selectedColor else labelColor
            c.drawText(name, x + size * 1.15f, y - size * 0.35f, text)
        }
    }

    private fun drawSelectedPaths(c: Canvas, mv: MapView, p: Plane, now: Long) {
        val proj = mv.projection
        p.positionAt(now, pos)
        // Flown trail (collected while watching) up to the live position.
        if (trail.size >= 1) {
            linePath.reset()
            var first = true
            for (q in trail) {
                geo.setCoords(q[0], q[1]); proj.toPixels(geo, pt)
                if (first) { linePath.moveTo(pt.x.toFloat(), pt.y.toFloat()); first = false }
                else linePath.lineTo(pt.x.toFloat(), pt.y.toFloat())
            }
            geo.setCoords(pos[0], pos[1]); proj.toPixels(geo, pt)
            linePath.lineTo(pt.x.toFloat(), pt.y.toFloat())
            line.pathEffect = null
            line.color = (glowColor and 0x00FFFFFF) or 0x40000000
            line.strokeWidth = 7f * density
            c.drawPath(linePath, line)
            line.color = glowColor
            line.strokeWidth = 2.6f * density
            c.drawPath(linePath, line)
        }
        val r = route ?: return
        // Remaining leg: dashed great circle to the destination.
        r.destination?.let { d ->
            val total = distanceNm(pos[0], pos[1], d.lat, d.lon)
            if (total > 1.0) {
                linePath.reset()
                val n = (total / 25.0).toInt().coerceIn(8, 96)
                for (i in 0..n) {
                    interpolate(pos[0], pos[1], d.lat, d.lon, i / n.toDouble(), tmp)
                    geo.setCoords(tmp[0], tmp[1]); proj.toPixels(geo, pt)
                    if (i == 0) linePath.moveTo(pt.x.toFloat(), pt.y.toFloat()) else linePath.lineTo(pt.x.toFloat(), pt.y.toFloat())
                }
                line.pathEffect = dash
                line.color = (selectedColor and 0x00FFFFFF) or 0x99000000.toInt()
                line.strokeWidth = 1.8f * density
                c.drawPath(linePath, line)
                line.pathEffect = null
            }
        }
        // Airports.
        for (a in listOfNotNull(r.origin, r.destination)) {
            geo.setCoords(a.lat, a.lon); proj.toPixels(geo, pt)
            fill.color = glowColor
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 5f * density, fill)
            stroke.color = -0x1; stroke.strokeWidth = 1.6f * density
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 5f * density, stroke)
            val code = a.iata ?: a.icao ?: continue
            text.color = selectedColor
            c.drawText(code, pt.x + 8f * density, pt.y + 4f * density, text)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val r = 26f * density
        var best = -1
        var bestD = r * r
        for (i in 0 until hitCount) {
            val dx = hitX[i] - e.x
            val dy = hitY[i] - e.y
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = i }
        }
        onTap?.invoke(if (best >= 0) hitPlane[best] else null)
        return best >= 0
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_MOVE || event.pointerCount > 1) onUserGesture?.invoke()
        return false
    }

    private fun ensureHitCapacity(n: Int) {
        if (hitX.size >= n + 1) return
        val cap = n + 256
        hitX = FloatArray(cap); hitY = FloatArray(cap); hitPlane = arrayOfNulls(cap)
    }

    companion object {
        /** Unit airliner silhouette pointing north, roughly within [-1, 1]. */
        fun planePath(): Path {
            val half = floatArrayOf(
                0f, -1f,
                0.11f, -0.86f,
                0.13f, -0.30f,
                0.98f, 0.16f,
                0.98f, 0.30f,
                0.13f, 0.08f,
                0.10f, 0.62f,
                0.40f, 0.84f,
                0.40f, 0.95f,
                0f, 0.86f,
            )
            val p = Path()
            p.moveTo(half[0], half[1])
            var i = 2
            while (i < half.size) { p.lineTo(half[i], half[i + 1]); i += 2 }
            i = half.size - 4
            while (i >= 2) { p.lineTo(-half[i], half[i + 1]); i -= 2 }
            p.close()
            return p
        }

        fun lerpColor(a: Int, b: Int, f: Float): Int {
            val q = (f * 256f).toInt().coerceIn(0, 256)
            val p = 256 - q
            val r = (((a shr 16) and 0xFF) * p + ((b shr 16) and 0xFF) * q) shr 8
            val g = (((a shr 8) and 0xFF) * p + ((b shr 8) and 0xFF) * q) shr 8
            val bl = ((a and 0xFF) * p + (b and 0xFF) * q) shr 8
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
        }
    }
}
