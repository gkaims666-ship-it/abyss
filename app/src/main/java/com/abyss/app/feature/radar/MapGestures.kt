package com.abyss.app.feature.radar

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.ln

/** What a recognised gesture asks the camera to do. */
interface MapGestureSink {
    /** First finger touched: stop any fling / animation (catch the map). */
    fun gestureStart()
    /** The user actually moved the camera (cancels "follow aircraft"). */
    fun userMoved()
    fun pan(dx: Float, dy: Float)
    fun zoom(dz: Double, fx: Float, fy: Float)
    fun rotate(deg: Double, fx: Float, fy: Float)
    /** Release with momentum, screen px/s. */
    fun fling(vx: Float, vy: Float)
    /** Pinch released while still spreading/closing, levels/s about (fx, fy). */
    fun zoomFling(vz: Double, fx: Float, fy: Float)
    /** Animated discrete zoom (double tap: +1, two-finger tap: −1). */
    fun animateZoom(dz: Double, fx: Float, fy: Float)
    fun tap(x: Float, y: Float)
    fun longPress(x: Float, y: Float)
}

/**
 * Google-Maps-style gesture recogniser, free of any Android type so it can be
 * unit-tested. Feed it pointer downs/moves/ups with uptime milliseconds and call
 * [tick] whenever [nextDeadline] passes.
 *
 *  - one finger drag → pan, release → fling with inertia
 *  - two fingers → pan + pinch-zoom + rotate together, all about the fingers
 *    (rotation engages after a small twist so pinching doesn't wobble the map)
 *  - double tap → zoom in about the tap; double-tap-and-drag → one-finger zoom
 *    (drag down = in, up = out)
 *  - two-finger tap → zoom out
 *  - long press → long-press action; single tap → confirmed after the
 *    double-tap window
 *  - any new touch stops a running fling (you can catch the map)
 */
class MapGestureEngine(density: Float, private val sink: MapGestureSink) {
    private val slop = 8f * density
    private val doubleTapSlop = 100f * density
    private val quickZoomPx = 150f * density   // drag distance per zoom level
    private val minFling = 60f * density
    private val maxFling = 8000f * density / 2.6f
    private val longPressMs = 420L
    private val doubleTapMs = 260L
    private val twoFingerTapMs = 260L

    private enum class Mode { Idle, Pending, Pan, QuickPending, QuickZoom, Transform, Done }
    private var mode = Mode.Idle

    // pointer id -> position, in down order
    private val ids = LongArray(10)
    private val xs = FloatArray(10)
    private val ys = FloatArray(10)
    private var n = 0

    private var downX = 0f; private var downY = 0f; private var downT = 0L
    private var lastX = 0f; private var lastY = 0f
    private var longPressAt = Long.MAX_VALUE

    private var pendingTap = false
    private var tapX = 0f; private var tapY = 0f; private var tapUpT = 0L

    private var qFx = 0f; private var qFy = 0f

    // transform
    private var aId = -1L; private var bId = -1L
    private var pcx = 0f; private var pcy = 0f; private var pSpan = 1f; private var pAng = 0.0
    private var c0x = 0f; private var c0y = 0f; private var span0 = 1f
    private var trT = 0L
    private var engaged = false
    private var zooming = false
    private var rotating = false
    private var accRot = 0.0

    // velocity samples of the focus point (and zoom level)
    private val vt = LongArray(VEL); private val vx = FloatArray(VEL); private val vy = FloatArray(VEL)
    private val vz = DoubleArray(VEL)
    private var vn = 0; private var vh = 0
    private var zAcc = 0.0

    val isActive get() = n > 0

    fun nextDeadline(): Long {
        var d = Long.MAX_VALUE
        if (mode == Mode.Pending) d = minOf(d, longPressAt)
        if (pendingTap) d = minOf(d, tapUpT + doubleTapMs)
        return d
    }

    fun tick(t: Long) {
        if (mode == Mode.Pending && t >= longPressAt) {
            longPressAt = Long.MAX_VALUE
            mode = Mode.Done
            sink.longPress(downX, downY)
        }
        if (pendingTap && t >= tapUpT + doubleTapMs) {
            pendingTap = false
            sink.tap(tapX, tapY)
        }
    }

    fun onDown(id: Long, x: Float, y: Float, t: Long) {
        if (n == ids.size || indexOf(id) >= 0) return
        ids[n] = id; xs[n] = x; ys[n] = y; n++
        if (n == 1) {
            sink.gestureStart()
            resetVel()
            downX = x; downY = y; downT = t
            if (pendingTap && t - tapUpT <= doubleTapMs && hypot(x - tapX, y - tapY) < doubleTapSlop) {
                pendingTap = false
                mode = Mode.QuickPending
                qFx = x; qFy = y; lastY = y
            } else {
                if (pendingTap) { pendingTap = false; sink.tap(tapX, tapY) }
                mode = Mode.Pending
                longPressAt = t + longPressMs
            }
            sample(t, x, y)
            return
        }
        // Second (or further) finger.
        when (mode) {
            Mode.Done -> {}
            Mode.Transform -> baseline(t, keepEngaged = true)
            else -> {
                val tapCandidate = mode == Mode.Pending
                longPressAt = Long.MAX_VALUE
                mode = Mode.Transform
                trT = t
                engaged = !tapCandidate
                zooming = false; rotating = false; accRot = 0.0; zAcc = 0.0
                baseline(t, keepEngaged = true)
                c0x = pcx; c0y = pcy; span0 = pSpan
            }
        }
    }

    /** Positions for pointers that moved; unknown ids are ignored. */
    fun onMove(t: Long, mid: LongArray, mx: FloatArray, my: FloatArray, count: Int) {
        for (i in 0 until count) {
            val k = indexOf(mid[i])
            if (k >= 0) { xs[k] = mx[i]; ys[k] = my[i] }
        }
        if (n == 0) return
        when (mode) {
            Mode.Pending -> {
                if (hypot(xs[0] - downX, ys[0] - downY) > slop) {
                    mode = Mode.Pan
                    longPressAt = Long.MAX_VALUE
                    sink.userMoved()
                    // Continue from the touch-down point so the content stays
                    // glued to the finger (no lag of one slop).
                    lastX = downX; lastY = downY
                    panTo(t, xs[0], ys[0])
                }
            }
            Mode.Pan -> panTo(t, xs[0], ys[0])
            Mode.QuickPending -> {
                if (abs(ys[0] - lastY) > slop) {
                    mode = Mode.QuickZoom
                    sink.userMoved()
                    quickTo(t, ys[0])
                }
            }
            Mode.QuickZoom -> quickTo(t, ys[0])
            Mode.Transform -> transform(t)
            else -> {}
        }
    }

    fun onUp(id: Long, x: Float, y: Float, t: Long) {
        val k = indexOf(id)
        if (k < 0) return
        xs[k] = x; ys[k] = y
        val wasMode = mode
        // positions before removal, for the transform centroid
        val cx = pcx; val cy = pcy
        remove(k)
        when (wasMode) {
            Mode.Pending -> {
                mode = Mode.Idle
                if (t - downT < longPressMs) {
                    pendingTap = true
                    tapX = downX; tapY = downY; tapUpT = t
                }
            }
            Mode.Pan -> {
                if (n == 0) { mode = Mode.Idle; flingOut(t) }
            }
            Mode.QuickPending -> { mode = Mode.Idle; sink.animateZoom(1.0, qFx, qFy) }
            Mode.QuickZoom -> {
                mode = Mode.Idle
                val v = zoomVelocity(t)
                if (abs(v) > 1.2) sink.zoomFling(v, qFx, qFy)
            }
            Mode.Transform -> when {
                n >= 2 -> baseline(t, keepEngaged = true)
                !engaged && t - trT < twoFingerTapMs * 2 && t - downT < 600 -> {
                    sink.animateZoom(-1.0, cx, cy)
                    mode = if (n == 0) Mode.Idle else Mode.Done
                }
                else -> {
                    if (zooming) {
                        val v = zoomVelocity(t)
                        if (abs(v) > 1.5) sink.zoomFling(v.coerceIn(-6.0, 6.0), cx, cy)
                    }
                    if (n == 1) {
                        // Keep panning with the remaining finger, no jump.
                        mode = if (engaged) Mode.Pan else Mode.Done
                        lastX = xs[0]; lastY = ys[0]
                        resetVel(); sample(t, xs[0], ys[0])
                    } else {
                        mode = Mode.Idle
                        if (engaged) flingOut(t)
                    }
                }
            }
            Mode.Done -> if (n == 0) mode = Mode.Idle
            Mode.Idle -> {}
        }
        if (n == 0 && mode != Mode.Idle) mode = Mode.Idle
    }

    fun onCancel() {
        n = 0
        mode = Mode.Idle
        pendingTap = false
        longPressAt = Long.MAX_VALUE
    }

    // ---------------------------------------------------------------- internals

    private fun panTo(t: Long, x: Float, y: Float) {
        val dx = x - lastX; val dy = y - lastY
        if (dx != 0f || dy != 0f) sink.pan(dx, dy)
        lastX = x; lastY = y
        sample(t, x, y)
    }

    private fun quickTo(t: Long, y: Float) {
        val dz = ((y - lastY) / quickZoomPx).toDouble()
        lastY = y
        if (dz != 0.0) { sink.zoom(dz, qFx, qFy); zAcc += dz }
        sample(t, qFx, qFy)
    }

    private fun transform(t: Long) {
        val a = indexOf(aId); val b = indexOf(bId)
        if (a < 0 || b < 0) { baseline(t, keepEngaged = true); return }
        val cx = (xs[a] + xs[b]) / 2f
        val cy = (ys[a] + ys[b]) / 2f
        val span = hypot(xs[b] - xs[a], ys[b] - ys[a]).coerceAtLeast(1f)
        val ang = Math.toDegrees(atan2((ys[b] - ys[a]).toDouble(), (xs[b] - xs[a]).toDouble()))

        if (!engaged) {
            var twist = ang - pAng
            if (twist > 180) twist -= 360.0
            if (twist < -180) twist += 360.0
            val moved = hypot(cx - c0x, cy - c0y) > slop || abs(span - span0) > slop || abs(twist) > 4.0
            if (!moved && t - trT < twoFingerTapMs) return
            engaged = true
            sink.userMoved()
            // First applied step starts from the baseline: catches up smoothly.
        }

        // 1) pan: world point under old centroid follows to the new centroid
        val dx = cx - pcx; val dy = cy - pcy
        if (dx != 0f || dy != 0f) sink.pan(dx, dy)

        // 2) zoom about the new centroid
        if (!zooming && abs(ln((span / span0).toDouble())) > 0.035) zooming = true
        if (zooming) {
            val dz = ln((span / pSpan).toDouble()) / LN2
            if (dz != 0.0) { sink.zoom(dz, cx, cy); zAcc += dz }
        }

        // 3) rotate about the new centroid, after a small deliberate twist
        var dA = ang - pAng
        if (dA > 180) dA -= 360.0
        if (dA < -180) dA += 360.0
        if (!rotating) {
            accRot += dA
            val need = if (zooming) 16.0 else 9.0
            if (abs(accRot) > need) rotating = true
        } else if (dA != 0.0) {
            sink.rotate(dA, cx, cy)
        }

        pcx = cx; pcy = cy; pSpan = span; pAng = ang
        sample(t, cx, cy)
    }

    /** Re-read the two tracked fingers after the pointer set changed. */
    private fun baseline(t: Long, keepEngaged: Boolean) {
        if (n < 2) return
        aId = ids[0]; bId = ids[1]
        pcx = (xs[0] + xs[1]) / 2f
        pcy = (ys[0] + ys[1]) / 2f
        pSpan = hypot(xs[1] - xs[0], ys[1] - ys[0]).coerceAtLeast(1f)
        pAng = Math.toDegrees(atan2((ys[1] - ys[0]).toDouble(), (xs[1] - xs[0]).toDouble()))
        if (!keepEngaged) engaged = false
        resetVel()
        sample(t, pcx, pcy)
    }

    private fun flingOut(t: Long) {
        val (fx, fy) = velocity(t)
        val sp = hypot(fx, fy)
        if (sp < minFling) return
        val k = if (sp > maxFling) maxFling / sp else 1f
        sink.fling(fx * k, fy * k)
    }

    private fun resetVel() { vn = 0; vh = 0; zAcc = 0.0 }

    private fun sample(t: Long, x: Float, y: Float) {
        vt[vh] = t; vx[vh] = x; vy[vh] = y; vz[vh] = zAcc
        vh = (vh + 1) % VEL
        if (vn < VEL) vn++
    }

    /** Velocity over the last ~100 ms; zero if the finger rested before release. */
    private fun velocity(t: Long): Pair<Float, Float> {
        if (vn < 2) return 0f to 0f
        val last = (vh - 1 + VEL) % VEL
        if (t - vt[last] > 70) return 0f to 0f
        var first = last
        for (i in 1 until vn) {
            val j = (last - i + VEL) % VEL
            if (vt[last] - vt[j] > 100) break
            first = j
        }
        val dt = (vt[last] - vt[first]) / 1000f
        if (dt < 0.008f) return 0f to 0f
        return (vx[last] - vx[first]) / dt to (vy[last] - vy[first]) / dt
    }

    private fun zoomVelocity(t: Long): Double {
        if (vn < 2) return 0.0
        val last = (vh - 1 + VEL) % VEL
        if (t - vt[last] > 70) return 0.0
        var first = last
        for (i in 1 until vn) {
            val j = (last - i + VEL) % VEL
            if (vt[last] - vt[j] > 100) break
            first = j
        }
        val dt = (vt[last] - vt[first]) / 1000.0
        if (dt < 0.008) return 0.0
        return (vz[last] - vz[first]) / dt
    }

    private fun indexOf(id: Long): Int {
        for (i in 0 until n) if (ids[i] == id) return i
        return -1
    }

    private fun remove(k: Int) {
        for (i in k until n - 1) { ids[i] = ids[i + 1]; xs[i] = xs[i + 1]; ys[i] = ys[i + 1] }
        n--
    }

    private companion object {
        const val VEL = 20
        val LN2 = ln(2.0)
    }
}
