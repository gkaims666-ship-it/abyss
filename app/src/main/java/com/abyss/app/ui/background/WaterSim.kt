package com.abyss.app.ui.background

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * The water behind Abyss. Pure Kotlin (no Android types) so it runs on its own
 * thread and can be unit-tested on a plain JVM.
 *
 * A damped, mildly non-linear 2-D wave equation on a coarse grid (~92 × 200):
 *  - fronts reflect off the screen frame and off every registered UI element;
 *  - each element periodically switches how the water treats it: SOLID (bounce
 *    + rim glow), UNDER (water slows and slips beneath it, bending around) or
 *    OVER (same slow zone, but the crests are drawn ON TOP of the element);
 *  - spatially varying, anisotropic "currents" stretch and warp every front, so
 *    nothing is ever a clean circle; amplitude-dependent speed makes crossing
 *    waves steepen, stack and visibly collide (collision points are reported).
 *
 * Threading: the UI thread only enqueues inputs and reads finished snapshots;
 * all simulation work runs on a single background thread (triple buffering, no
 * locks held while computing, zero allocations per frame).
 */

internal const val KIND_OPEN: Byte = 0
internal const val KIND_SOLID: Byte = 1
internal const val KIND_UNDER: Byte = 2
internal const val KIND_OVER: Byte = 3

internal const val MODE_SOLID = 1
internal const val MODE_UNDER = 2
internal const val MODE_OVER = 3

private const val TAU_F = (2.0 * PI).toFloat()

/** Fixed timestep → identical motion at 60/90/120 Hz. */
private const val STEP_HZ = 150f
private const val MAX_STEPS = 4
private const val GRID_COLS = 116
private const val C2_BASE = 0.30f
private const val C2_MIN = 0.07f
private const val C2_MAX = 0.46f
private const val NONLINEAR = 0.30f
private const val DAMPING = 0.9968f
private const val DAMPING_SLOW = 0.9915f
/** Inside UNDER/OVER zones the water is this much "slower" → refraction + partial echo. */
private const val SLOW_ZONE = 0.40f
private const val H_LIMIT = 3f
/** |h| above this only happens where fronts pile up → a collision. */
private const val COLLIDE_H = 0.55f

/**
 * How an element currently treats the water. Changes every few seconds per
 * element (deterministic from its seed + time, so UI and simulation agree).
 */
internal fun obstacleMode(seed: Int, t: Float): Int {
    val period = 5.5f + (seed and 7) * 0.6f
    val slot = floor((t + (seed and 1023) * 0.731f) / period).toInt()
    var h = seed * -0x61c88647 + slot * 0x27d4eb2d
    h = h xor (h ushr 15); h *= 0x2c1b3c6d; h = h xor (h ushr 12)
    val r = (h ushr 1) % 100
    return when {
        r < 52 -> MODE_SOLID
        r < 76 -> MODE_UNDER
        else -> MODE_OVER
    }
}

/** A UI element, in root pixels, as handed to the simulation thread. */
internal class ObstacleSnap(
    @JvmField val l: Float, @JvmField val t: Float, @JvmField val r: Float, @JvmField val b: Float,
    @JvmField val radius: Float, @JvmField val seed: Int,
)

/** One finished frame of water, safe to read on the UI thread. */
internal class WaterSnapshot {
    var cols = 0; private set
    var rows = 0; private set
    var cell = 1f
    var heights = FloatArray(0); private set
    /** ARGB per cell: R = height, G = ∂x, B = ∂y (soft-compressed bytes). */
    var pixels = IntArray(0); private set
    /** ARGB per cell: R = solid wall, G = near an element (gate for the exact-outline shader pass). */
    var wallPixels = IntArray(0); private set
    var wallVersion = -1
    var active = false
    var generation = 0
    val collisions = FloatArray(8)
    var collisionCount = 0

    val ready get() = cols > 0

    fun ensure(c: Int, r: Int) {
        if (c == cols && r == rows) return
        cols = c; rows = r
        heights = FloatArray(c * r)
        pixels = IntArray(c * r) { REST_PIXEL }
        wallPixels = IntArray(c * r) { OPEN_PIXEL }
        wallVersion = -1
    }

    fun heightAt(px: Float, py: Float): Float {
        if (!active || cols == 0) return 0f
        val x = (px / cell + 1f).toInt()
        val y = (py / cell + 1f).toInt()
        if (x < 1 || y < 1 || x >= cols - 1 || y >= rows - 1) return 0f
        return heights[y * cols + x]
    }

    /** Slope (per cell) at a root pixel into out[0], out[1]. */
    fun slopeAt(px: Float, py: Float, out: FloatArray) {
        out[0] = 0f; out[1] = 0f
        if (!active || cols == 0) return
        val x = (px / cell + 1f).toInt()
        val y = (py / cell + 1f).toInt()
        if (x < 1 || y < 1 || x >= cols - 1 || y >= rows - 1) return
        val i = y * cols + x
        out[0] = (heights[i + 1] - heights[i - 1]) * 0.5f
        out[1] = (heights[i + cols] - heights[i - cols]) * 0.5f
    }

    companion object {
        fun enc(v: Float): Int {
            val e = v / (1f + abs(v))
            val b = ((e * 0.5f + 0.5f) * 255f + 0.5f).toInt()
            return if (b < 0) 0 else if (b > 255) 255 else b
        }
        val REST_PIXEL = (0xFF shl 24) or (128 shl 16) or (128 shl 8) or 128
        const val OPEN_PIXEL = -0x1000000              // opaque black
    }
}

internal class WaterSim {
    /** Cell size in px; read by the UI thread to size splashes. */
    @Volatile var cell = 12f; private set
    @Volatile var ready = false; private set

    // ------------------------------------------------ inputs (guarded by lock)
    private val lock = Any()
    private var reqW = 0f
    private var reqH = 0f
    private var reqObstacles: List<ObstacleSnap>? = null
    private var pendingDt = 0f
    private var reqT = 0f
    private var events = FloatArray(5 * 256)        // x, y, amp, radius, lobes
    private var eventCount = 0
    private var spareEvents = FloatArray(5 * 256)

    // ------------------------------------------------ outputs (triple buffer)
    private var back = WaterSnapshot()
    private var mid = WaterSnapshot()
    private var midNew = false
    private var front = WaterSnapshot()

    // ------------------------------------------------ simulation state (sim thread only)
    private var cols = 0
    private var rows = 0
    private var widthPx = 0f
    private var heightPx = 0f
    private var cur = FloatArray(0)
    private var prev = FloatArray(0)
    private var kind = ByteArray(0)
    private var c2 = FloatArray(0)
    /** Speed factor for half-covered edge cells (1 = free water). */
    private var edgeK = FloatArray(0)
    private var near = ByteArray(0)
    private var aniso = FloatArray(0)
    private var wallPixels = IntArray(0)
    private var wallVersion = 0
    private var obstacles: List<ObstacleSnap> = emptyList()
    private var modes = IntArray(0)
    private var active = false
    private var publishedRest = true
    private var quiet = 0
    private var speedRow = 0
    private var accumulator = 0f
    private var simTime = 0f
    private var lastCollision = -10f
    private var generation = 0
    /** Recent energy inputs (x, y in cells, time): a tall crest right at a fresh splash is not a collision. */
    private val recent = FloatArray(3 * 12) { if (it % 3 == 2) -10f else 0f }
    private var recentNext = 0
    private val rnd = Random(7)

    private val busy = AtomicBoolean(false)
    @Volatile private var executor: ExecutorService? = null
    private val runner = Runnable {
        try { tick() } catch (_: Throwable) { } finally { busy.set(false) }
    }

    // ================================================================ UI-thread API

    fun start() {
        if (executor != null) return
        executor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "abyss-water").apply { isDaemon = true; priority = Thread.NORM_PRIORITY + 1 }
        }
    }

    fun stop() {
        executor?.shutdownNow()
        executor = null
        busy.set(false)
    }

    fun resize(w: Float, h: Float) = synchronized(lock) { reqW = w; reqH = h }

    fun setObstacles(list: List<ObstacleSnap>) = synchronized(lock) { reqObstacles = list }

    /** Irregular multi-lobe splash. */
    fun splash(x: Float, y: Float, amp: Float, radius: Float, lobes: Int = 4) = push(x, y, amp, radius, lobes)

    /** Single soft bump (finger wake, drops, wanderers). */
    fun drip(x: Float, y: Float, amp: Float, radius: Float) = push(x, y, amp, radius, 0)

    private fun push(x: Float, y: Float, amp: Float, radius: Float, lobes: Int) = synchronized(lock) {
        if (eventCount * 5 + 5 > events.size) return@synchronized   // flooded: drop
        val o = eventCount * 5
        events[o] = x; events[o + 1] = y; events[o + 2] = amp; events[o + 3] = radius
        events[o + 4] = lobes.toFloat()
        eventCount++
    }

    /** Asks the sim thread to advance by [dt] s; if it is still busy the time is carried over. */
    fun request(dt: Float, t: Float) {
        synchronized(lock) { pendingDt = min(pendingDt + dt, 0.1f); reqT = t }
        val ex = executor ?: return
        if (busy.compareAndSet(false, true)) {
            try { ex.execute(runner) } catch (_: Throwable) { busy.set(false) }
        }
    }

    /** Latest finished snapshot. Call on the UI thread only. */
    fun latest(): WaterSnapshot {
        synchronized(lock) {
            if (midNew) { val tmp = front; front = mid; mid = tmp; midNew = false }
        }
        return front
    }

    /** Runs one tick on the calling thread (tests). */
    internal fun tickNow() = tick()

    // ================================================================ sim thread

    private fun tick() {
        val dt: Float
        val t: Float
        val w: Float
        val h: Float
        val obs: List<ObstacleSnap>?
        val evs: FloatArray
        val evCount: Int
        synchronized(lock) {
            dt = pendingDt; pendingDt = 0f
            t = reqT; w = reqW; h = reqH
            obs = reqObstacles; reqObstacles = null
            evs = events; evCount = eventCount
            events = spareEvents; spareEvents = evs; eventCount = 0
        }
        resizeInternal(w, h)
        if (cols == 0) return
        simTime = t

        // Obstacles + their current modes.
        var wallsDirty = false
        if (obs != null) { obstacles = obs; wallsDirty = true }
        if (modes.size != obstacles.size) { modes = IntArray(obstacles.size); wallsDirty = true }
        for (i in obstacles.indices) {
            val m = obstacleMode(obstacles[i].seed, t)
            if (m != modes[i]) { modes[i] = m; wallsDirty = true }
        }
        if (wallsDirty) rebuildWalls()

        for (e in 0 until evCount) {
            val o = e * 5
            val lobes = evs[o + 4].toInt()
            if (lobes > 0) splashInternal(evs[o], evs[o + 1], evs[o + 2], evs[o + 3], lobes)
            else bump(evs[o], evs[o + 1], evs[o + 2], evs[o + 3])
            if (evs[o + 2] > 0.2f) {
                val ri = recentNext * 3
                recent[ri] = evs[o] / cell + 0.5f; recent[ri + 1] = evs[o + 1] / cell + 0.5f; recent[ri + 2] = t
                recentNext = (recentNext + 1) % 12
            }
            active = true; quiet = 0
        }

        // Currents drift continuously: refresh 1/10 of the rows per tick.
        repeat(max(1, rows / 10)) { updateSpeedRow(speedRow, t); speedRow = (speedRow + 1) % rows }

        var changed = wallsDirty
        if (active) {
            accumulator += dt
            var steps = 0
            var peak = 0f
            while (accumulator >= 1f / STEP_HZ && steps < MAX_STEPS) {
                peak = max(peak, step())
                accumulator -= 1f / STEP_HZ
                steps++
            }
            if (steps == MAX_STEPS) accumulator = 0f
            if (steps > 0) {
                changed = true
                if (peak < 0.0022f) {
                    if (quiet++ > 3) {
                        java.util.Arrays.fill(cur, 0f); java.util.Arrays.fill(prev, 0f)
                        active = false
                    }
                } else quiet = 0
            }
        }
        if (!changed && !active && publishedRest) return
        publish()
    }

    private fun resizeInternal(w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return
        if (cols > 0 && abs(w - widthPx) < 1f && abs(h - heightPx) < 1f) return
        widthPx = w; heightPx = h
        val cl = max(8f, w / GRID_COLS)
        cols = ceil(w / cl).toInt() + 2
        rows = ceil(h / cl).toInt() + 2
        val n = cols * rows
        cur = FloatArray(n); prev = FloatArray(n); kind = ByteArray(n)
        c2 = FloatArray(n); aniso = FloatArray(n); wallPixels = IntArray(n)
        edgeK = FloatArray(n) { 1f }; near = ByteArray(n)
        for (y in 0 until rows) updateSpeedRow(y, simTime)
        cell = cl
        modes = IntArray(0)          // forces a wall rebuild
        rebuildWalls()
        active = true
        ready = true
    }

    /** 1 if (px, py) lies inside the rounded rect [o] (with corner radius [rad]). */
    private fun inside(o: ObstacleSnap, rad: Float, px: Float, py: Float): Boolean {
        if (px < o.l || px > o.r || py < o.t || py > o.b) return false
        if (rad <= 0f) return true
        val dx = max(max(o.l + rad - px, px - (o.r - rad)), 0f)
        val dy = max(max(o.t + rad - py, py - (o.b - rad)), 0f)
        return dx * dx + dy * dy <= rad * rad
    }

    /**
     * Rasterises the elements with 4 sub-samples per cell, so the water's
     * boundary follows the real outline instead of a staircase: mostly covered
     * cells become walls / slow zones, half covered edge cells stay water but
     * are slowed (wave fronts bend smoothly around the corner). The exact
     * outline itself is drawn by the shader from the element rectangles.
     */
    private fun rebuildWalls() {
        val c = cols
        val newKind = kind
        java.util.Arrays.fill(newKind, KIND_OPEN)
        java.util.Arrays.fill(edgeK, 1f)
        java.util.Arrays.fill(near, 0)
        for (x in 0 until c) { newKind[x] = KIND_SOLID; newKind[(rows - 1) * c + x] = KIND_SOLID }
        for (y in 0 until rows) { newKind[y * c] = KIND_SOLID; newKind[y * c + c - 1] = KIND_SOLID }
        val q1 = cell * 0.25f
        val q3 = cell * 0.75f
        for (k in obstacles.indices) {
            val o = obstacles[k]
            val w = o.r - o.l
            val h = o.b - o.t
            if (w < cell * 0.5f || h < cell * 0.5f) continue
            val mode = if (k < modes.size) modes[k] else MODE_SOLID
            val kd = when (mode) { MODE_UNDER -> KIND_UNDER; MODE_OVER -> KIND_OVER; else -> KIND_SOLID }
            val rad = min(o.radius, min(w, h) * 0.5f)
            // Gate for the shader's exact-outline pass: the element plus a 3-cell halo.
            val gx0 = max(0, (o.l / cell - 2f).toInt()); val gx1 = min(c - 1, (o.r / cell + 4f).toInt())
            val gy0 = max(0, (o.t / cell - 2f).toInt()); val gy1 = min(rows - 1, (o.b / cell + 4f).toInt())
            for (y in gy0..gy1) { val row = y * c; for (x in gx0..gx1) near[row + x] = 1 }

            val x0 = max(1, (o.l / cell).toInt()); val x1 = min(c - 2, (o.r / cell + 2f).toInt())
            val y0 = max(1, (o.t / cell).toInt()); val y1 = min(rows - 2, (o.b / cell + 2f).toInt())
            for (y in y0..y1) {
                val top = (y - 1) * cell
                val row = y * c
                for (x in x0..x1) {
                    val left = (x - 1) * cell
                    var n = 0
                    if (inside(o, rad, left + q1, top + q1)) n++
                    if (inside(o, rad, left + q3, top + q1)) n++
                    if (inside(o, rad, left + q1, top + q3)) n++
                    if (inside(o, rad, left + q3, top + q3)) n++
                    if (n == 0) continue
                    val i = row + x
                    if (n >= 3) {
                        // SOLID always wins over a permeable zone underneath it.
                        if (newKind[i] != KIND_SOLID) newKind[i] = kd
                    } else if (newKind[i] == KIND_OPEN) {
                        // Partly covered edge cell: still water, but slowed in proportion.
                        val f = if (kd == KIND_SOLID) 1f - 0.28f * n else 1f - (1f - SLOW_ZONE) * 0.5f * n
                        if (f < edgeK[i]) edgeK[i] = f
                    }
                }
            }
        }
        for (i in newKind.indices) {
            val g = if (near[i].toInt() != 0) 0x0000FF00 else 0
            if (newKind[i] == KIND_SOLID) {
                cur[i] = 0f; prev[i] = 0f
                wallPixels[i] = -0x1000000 or 0x00FF0000 or g
            } else wallPixels[i] = -0x1000000 or g
        }
        wallVersion++
        active = true; quiet = 0
    }

    private fun bump(px: Float, py: Float, amp: Float, r: Float) {
        val gx = px / cell + 0.5f
        val gy = py / cell + 0.5f
        val rc = max(r / cell, 0.8f)
        val reach = (rc * 2.6f).toInt() + 1
        val x0 = max(1, gx.toInt() - reach); val x1 = min(cols - 2, gx.toInt() + reach)
        val y0 = max(1, gy.toInt() - reach); val y1 = min(rows - 2, gy.toInt() + reach)
        if (x0 > x1 || y0 > y1) return
        val inv = 1f / (rc * rc)
        for (y in y0..y1) {
            val dy = y - gy
            val row = y * cols
            for (x in x0..x1) {
                val i = row + x
                if (kind[i] == KIND_SOLID) continue
                val dx = x - gx
                val k = exp(-(dx * dx + dy * dy) * inv)
                if (k > 0.004f) {
                    // Displace without a velocity kick (prev moves too) → no blow-up on impact.
                    val v = (cur[i] + amp * k).coerceIn(-H_LIMIT, H_LIMIT)
                    prev[i] += v - cur[i]; cur[i] = v
                }
            }
        }
    }

    /** Lopsided from its very first frame: offset lobes of different size and sign. */
    private fun splashInternal(px: Float, py: Float, amp: Float, radius: Float, lobes: Int) {
        val n = lobes + rnd.nextInt(3)
        for (i in 0 until n) {
            val a = rnd.nextFloat() * TAU_F
            val d = if (i == 0) 0f else radius * (0.25f + rnd.nextFloat() * 0.7f)
            val sign = if (i > 0 && rnd.nextFloat() < 0.28f) -0.65f else 1f
            bump(px + cos(a) * d, py + sin(a) * d,
                amp * sign * (if (i == 0) 0.72f else 0.22f + rnd.nextFloat() * 0.36f),
                radius * (if (i == 0) 0.7f else 0.3f + rnd.nextFloat() * 0.45f))
        }
    }

    /** Drifting currents: speed AND direction-dependence vary in space and time. */
    private fun updateSpeedRow(y: Int, t: Float) {
        val row = y * cols
        val fy = y.toFloat()
        val sy1 = sin(fy * 0.071f - t * 0.29f)
        val sy2 = sin(fy * 0.163f + t * 0.37f)
        val sy3 = sin(fy * 0.047f + t * 0.21f)
        for (x in 0 until cols) {
            val fx = x.toFloat()
            val n = sin(fx * 0.097f + t * 0.33f) * sy1 +
                0.55f * sin((fx + fy) * 0.052f + t * 0.47f) +
                0.35f * sin(fx * 0.21f - t * 0.23f) * sy2
            c2[row + x] = (C2_BASE * (1f + 0.42f * n)).coerceIn(C2_MIN, C2_MAX)
            // −0.38..0.38: fronts stretch horizontally here, vertically there.
            aniso[row + x] = 0.38f * sin(fx * 0.061f - t * 0.27f + 2.0f * sy3) *
                (0.6f + 0.4f * sin((fx - fy) * 0.083f + t * 0.31f))
        }
    }

    /** One leapfrog step. Returns the peak |h|. */
    private fun step(): Float {
        val c = cols
        val cu = cur
        val pr = prev
        val kd = kind
        val sp = c2
        val an = aniso
        val ek = edgeK
        var peak = 0f
        for (y in 1 until rows - 1) {
            val row = y * c
            for (x in 1 until c - 1) {
                val i = row + x
                val kk = kd[i]
                if (kk == KIND_SOLID) { pr[i] = 0f; continue }
                val h = cu[i]
                val l = cu[i - 1]; val r = cu[i + 1]; val u = cu[i - c]; val d = cu[i + c]
                // Isotropic 9-point Laplacian + an anisotropic term from the current map.
                val lap = (4f * (l + r + u + d) +
                    cu[i - c - 1] + cu[i - c + 1] + cu[i + c - 1] + cu[i + c + 1] - 20f * h) * (1f / 6f) +
                    an[i] * ((l + r) - (u + d))
                var k = sp[i] * ek[i] * (1f + NONLINEAR * h)
                if (k < C2_MIN) k = C2_MIN else if (k > C2_MAX) k = C2_MAX
                var damp = DAMPING
                if (kk != KIND_OPEN) { k *= SLOW_ZONE; damp = DAMPING_SLOW }
                var n = (2f * h - pr[i] + k * lap) * damp
                if (n > H_LIMIT) n = H_LIMIT else if (n < -H_LIMIT) n = -H_LIMIT
                pr[i] = n
                val a = if (n < 0f) -n else n
                if (a > peak) peak = a
            }
        }
        prev = cu
        cur = pr
        return peak
    }

    private fun nearFreshSplash(x: Int, y: Int): Boolean {
        for (k in 0 until 12) {
            val o = k * 3
            if (simTime - recent[o + 2] > 0.55f) continue
            val dx = x - recent[o]; val dy = y - recent[o + 1]
            if (dx * dx + dy * dy < 49f) return true
        }
        return false
    }

    private fun publish() {
        val s = back
        s.ensure(cols, rows)
        s.cell = cell
        val c = cols
        val cu = cur
        val px = s.pixels
        var bestA = COLLIDE_H
        var bestI = -1
        if (active) {
            System.arraycopy(cu, 0, s.heights, 0, cu.size)
            for (y in 1 until rows - 1) {
                val row = y * c
                for (x in 1 until c - 1) {
                    val i = row + x
                    val h = cu[i]
                    px[i] = (0xFF shl 24) or (WaterSnapshot.enc(h) shl 16) or
                        (WaterSnapshot.enc(cu[i + 1] - cu[i - 1]) shl 8) or WaterSnapshot.enc(cu[i + c] - cu[i - c])
                    if (h > bestA && kind[i] == KIND_OPEN && !nearFreshSplash(x, y)) { bestA = h; bestI = i }
                }
            }
            publishedRest = false
        } else {
            java.util.Arrays.fill(s.heights, 0f)
            java.util.Arrays.fill(px, WaterSnapshot.REST_PIXEL)
            publishedRest = true
        }
        s.collisionCount = 0
        if (bestI >= 0 && simTime - lastCollision > 0.11f) {
            lastCollision = simTime
            s.collisions[0] = ((bestI % c) - 0.5f) * cell
            s.collisions[1] = ((bestI / c) - 0.5f) * cell
            s.collisionCount = 1
        }
        if (s.wallVersion != wallVersion) {
            System.arraycopy(wallPixels, 0, s.wallPixels, 0, wallPixels.size)
            s.wallVersion = wallVersion
        }
        s.active = active
        s.generation = ++generation
        synchronized(lock) {
            val tmp = mid; mid = back; back = tmp; midNew = true
        }
    }
}
