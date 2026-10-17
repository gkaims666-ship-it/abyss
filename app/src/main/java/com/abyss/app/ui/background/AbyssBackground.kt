package com.abyss.app.ui.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.ui.theme.AbyssPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// ============================================================ constants

private const val TAU = (2.0 * PI).toFloat()
private const val CHARGE_DELAY_NS = 380_000_000L
private const val CHARGE_FULL_NS = 1_100_000_000L
/** Elements whose exact outline the shader draws (must match MAXO in the shaders). */
private const val MAX_OBS = 40

// ============================================================ model

/** A spark thrown out of a wave origin or a wave collision. */
internal class Spark(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val born: Long, val lifeNs: Long, val size: Float, val warm: Float,
)

/** A UI element the water reacts to, in root pixels. */
internal class Obstacle(
    var l: Float, var t: Float, var r: Float, var b: Float, var radius: Float, val seed: Int,
)

/**
 * An invisible moving source — like a boat crossing the surface. Its curving
 * wake crosses other waves and the UI, so the water is never still or regular.
 */
private class Wanderer {
    var x = 0f; var y = 0f; var vx = 0f; var vy = 0f; var turn = 0f
    var until = 0L; var next = 0L; var lastDrip = 0L
}

/**
 * One engine shared by: the touch modifier, every UI element registered as an
 * obstacle, the background (shader + glyphs) and the overlay (sparks, charge,
 * crests flowing OVER elements). The physics runs on its own thread
 * ([WaterSim]); this class only feeds it and holds the latest frame.
 */
@Stable
class AbyssBackgroundState {
    internal val sim = WaterSim()
    /** Latest finished water frame (UI thread only). */
    internal var water = WaterSnapshot()
    internal val sparks = ArrayList<Spark>()
    internal val gpu = WaterGpu()
    /** Exact element outlines for the shaders: (cx, cy, halfW, halfH) and (radius, mode, 0, 0). */
    internal val obsA = FloatArray(MAX_OBS * 4)
    internal val obsB = FloatArray(MAX_OBS * 4)
    internal var obsN = 0
    private val rnd = Random(3)

    var frameNanos by mutableLongStateOf(0L)
        internal set
    internal var startNanos = 0L

    // Obstacles: plain map (no snapshot state) — written from layout, read in the frame loop.
    internal val obstacles = HashMap<Any, Obstacle>()
    internal var obstaclesDirty = true
    private var nextSeed = 0x2545F491

    // Hold-to-charge.
    internal var charging = false
    internal var chargeStart = 0L
    internal var chargePos = Offset.Zero

    // Last known canvas size.
    internal var viewW = 0f
    internal var viewH = 0f

    // Device tilt parallax (smoothed in the frame loop).
    internal var tiltX = 0f
    internal var tiltY = 0f
    internal var tiltTargetX = 0f
    internal var tiltTargetY = 0f

    internal fun timeSec(now: Long): Float =
        if (startNanos == 0L) 0f else (now - startNanos) / 1_000_000_000f

    /** A splash at [at]. [reach] scales its size, [strength] its height. */
    fun emit(at: Offset, strength: Float = 1f, reach: Float = 1f, sparkCount: Int = 0) {
        sim.splash(at.x, at.y, 0.95f * strength, sim.cell * (2.2f + 1.4f * reach))
        throwSparks(at.x, at.y, sparkCount, 240f, 420f * strength, 550_000_000L, 600_000_000L, 1.6f)
    }

    /** A wave from just above screen centre — used on launch and theme change. */
    fun emitCenter(strength: Float = 1f, reach: Float = 1.3f, sparkCount: Int = 20) {
        if (viewW <= 0f) return
        emit(Offset(viewW / 2f, viewH * 0.42f), strength, reach, sparkCount)
    }

    /** Two fronts slammed into each other: a short, low spray. */
    internal fun collisionBurst(x: Float, y: Float) {
        throwSparks(x, y, 3 + rnd.nextInt(3), 70f, 190f, 300_000_000L, 380_000_000L, 1.1f)
    }

    private fun throwSparks(
        x: Float, y: Float, count: Int, vMin: Float, vRange: Float,
        lifeMin: Long, lifeRange: Long, sizeMin: Float,
    ) {
        if (count <= 0) return
        val now = System.nanoTime()
        repeat(count) {
            val a = rnd.nextFloat() * TAU
            val v = vMin + rnd.nextFloat() * vRange
            sparks.add(Spark(
                x, y, cos(a) * v, sin(a) * v, now,
                lifeMin + (rnd.nextFloat() * lifeRange).toLong(),
                sizeMin + rnd.nextFloat() * 2.8f, rnd.nextFloat(),
            ))
        }
        while (sparks.size > 110) sparks.removeAt(0)
    }

    /** Finger wake: a continuous line of small disturbances. */
    internal fun trail(from: Offset, to: Offset) {
        val cell = sim.cell
        val d = (to - from).getDistance()
        val n = (d / (cell * 1.2f)).toInt().coerceIn(1, 12)
        for (i in 1..n) {
            val p = from + (to - from) * (i / n.toFloat())
            sim.drip(p.x, p.y, 0.22f, cell * 1.5f)
        }
    }

    internal fun putObstacle(key: Any, l: Float, t: Float, r: Float, b: Float, radius: Float) {
        val o = obstacles[key]
        if (o == null) {
            nextSeed = nextSeed * 1103515245 + 12345
            obstacles[key] = Obstacle(l, t, r, b, radius, nextSeed ushr 1); obstaclesDirty = true
        } else if (abs(o.l - l) > 0.5f || abs(o.t - t) > 0.5f || abs(o.r - r) > 0.5f ||
            abs(o.b - b) > 0.5f || o.radius != radius) {
            o.l = l; o.t = t; o.r = r; o.b = b; o.radius = radius; obstaclesDirty = true
        }
    }

    /** Packs the current element rectangles + their water modes for the shaders. */
    internal fun packObstacles(t: Float) {
        var n = 0
        for (o in obstacles.values) {
            if (n == MAX_OBS) break
            val hw = (o.r - o.l) * 0.5f
            val hh = (o.b - o.t) * 0.5f
            if (hw < 1f || hh < 1f) continue
            val i = n * 4
            obsA[i] = o.l + hw; obsA[i + 1] = o.t + hh; obsA[i + 2] = hw; obsA[i + 3] = hh
            obsB[i] = o.radius.coerceAtMost(minOf(hw, hh)); obsB[i + 1] = obstacleMode(o.seed, t).toFloat()
            n++
        }
        obsN = n
    }

    internal fun removeObstacle(key: Any) {
        if (obstacles.remove(key) != null) obstaclesDirty = true
    }

    /** 0..1 charge level of the current hold, or 0 when not charging. */
    internal fun chargeLevel(now: Long): Float {
        if (!charging) return 0f
        val held = now - chargeStart - CHARGE_DELAY_NS
        return (held.toFloat() / (CHARGE_FULL_NS - CHARGE_DELAY_NS)).coerceIn(0f, 1f)
    }

    internal fun beginCharge(at: Offset) { charging = true; chargeStart = System.nanoTime(); chargePos = at }
    internal fun cancelCharge() { charging = false }
    internal fun releaseCharge() {
        if (!charging) return
        val c = chargeLevel(System.nanoTime())
        charging = false
        if (c > 0.02f) emit(chargePos, 1f + 0.8f * c, 1f + 0.9f * c, sparkCount = 12 + (22 * c).toInt())
    }
}

@Composable
fun rememberAbyssBackgroundState(): AbyssBackgroundState = remember { AbyssBackgroundState() }

/** Provided at the app root so any element can become an obstacle for the water. */
val LocalAbyssField = staticCompositionLocalOf<AbyssBackgroundState?> { null }

/**
 * Makes this element part of the water's world: depending on the moment,
 * waves bounce off it (with a glowing rim), slip beneath it, or roll over it.
 * [corner] should match the element's shape. Cheap — one bounds update per
 * layout change, physics happens off the UI thread.
 */
fun Modifier.abyssObstacle(corner: Dp = 0.dp): Modifier = composed {
    val st = LocalAbyssField.current
    if (st == null) Modifier else {
        val key = remember { Any() }
        val rad = with(LocalDensity.current) { corner.toPx() }
        DisposableEffect(st, key) { onDispose { st.removeObstacle(key) } }
        Modifier.onGloballyPositioned { c ->
            if (!c.isAttached) { st.removeObstacle(key); return@onGloballyPositioned }
            val b = c.boundsInRoot()
            if (b.width < 1f || b.height < 1f) st.removeObstacle(key)
            else st.putObstacle(key, b.left, b.top, b.right, b.bottom, rad)
        }
    }
}

/**
 * Put on the app root. Observes touches on the Initial pass without consuming
 * them, so everything underneath stays fully interactive.
 *  - press → a splash + a few sparks, and starts charging
 *  - hold still → charge builds (the finger "hums" waves); release → big splash
 *  - drag → cancels the charge and drags a wake through the water
 */
fun Modifier.abyssTouchWaves(state: AbyssBackgroundState): Modifier = pointerInput(state) {
    val lastPos = HashMap<PointerId, Offset>()
    val lastSpark = HashMap<PointerId, Offset>()
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { c ->
                when {
                    c.pressed && !c.previousPressed -> {
                        state.emit(c.position, 1f, 1f, sparkCount = 7)
                        state.beginCharge(c.position)
                        lastPos[c.id] = c.position
                        lastSpark[c.id] = c.position
                    }
                    c.pressed -> {
                        if (state.charging && (c.position - state.chargePos).getDistance() > 28f) state.cancelCharge()
                        val prev = lastPos[c.id]
                        if (prev != null && (c.position - prev).getDistance() > 6f) {
                            state.trail(prev, c.position)
                            lastPos[c.id] = c.position
                        }
                        val ls = lastSpark[c.id]
                        if (ls != null && (c.position - ls).getDistance() > 140f) {
                            state.emit(c.position, 0.25f, 0.2f, sparkCount = 2)
                            lastSpark[c.id] = c.position
                        }
                    }
                    else -> {
                        lastSpark.remove(c.id)
                        if (lastPos.remove(c.id) != null) state.releaseCharge()
                    }
                }
            }
        }
    }
}

// ============================================================ glyph field

/** English letters and binary digits only. */
private val GLYPHS: Array<String> =
    (('A'..'Z') + ('a'..'z') + listOf('0', '1', '0', '1')).map { it.toString() }.toTypedArray()
private val LAYER_SIZE = floatArrayOf(9f, 12.5f, 17f)

private class Glyph(
    var x: Float, var y: Float,
    val layer: Int, val speed: Float, val baseAlpha: Float,
    val phase: Float, val sway: Float,
    /** Each glyph re-rolls its symbol every [periodNs] (≈5 times a second), out of step with the rest. */
    val seed: Int, val periodNs: Long, val offsetNs: Long,
)

/**
 * Every glyph of every layer pre-rendered once into a small white atlas; each
 * frame the whole field is then ONE drawVertices call (tinted per vertex)
 * instead of ~900 separate drawText operations on the UI thread.
 */
private class GlyphAtlas(maxGlyphs: Int) {
    val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    var px = -1f
    val cellW = FloatArray(3)
    val cellH = FloatArray(3)
    val rowY = FloatArray(3)
    val ascent = FloatArray(3)
    val textSize = FloatArray(3)
    val pad = 2f
    val verts = FloatArray(maxGlyphs * 8)
    val texs = FloatArray(maxGlyphs * 8)
    // Sized generously: some Android versions range-check colors against vertexCount.
    val colors = IntArray(maxGlyphs * 8)
    val indices = ShortArray(maxGlyphs * 6).also { idx ->
        for (g in 0 until maxGlyphs) {
            val v = (g * 4).toShort(); val o = g * 6
            idx[o] = v; idx[o + 1] = (v + 1).toShort(); idx[o + 2] = (v + 2).toShort()
            idx[o + 3] = v; idx[o + 4] = (v + 2).toShort(); idx[o + 5] = (v + 3).toShort()
        }
    }
    // Palette colours as packed RGB, refreshed when the palette changes.
    var paletteKey: AbyssPalette? = null
    var symbol = 0; var secondary = 0; var wave = 0

    fun build(pxPerSp: Float) {
        px = pxPerSp
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; color = -0x1 }
        var y = 0f
        var width = 0f
        for (i in 0 until 3) {
            tp.textSize = LAYER_SIZE[i] * pxPerSp
            val fm = tp.fontMetrics
            textSize[i] = tp.textSize
            ascent[i] = fm.ascent
            cellW[i] = kotlin.math.ceil(tp.measureText("W")) + pad * 2f
            cellH[i] = kotlin.math.ceil(fm.descent - fm.ascent) + pad * 2f
            rowY[i] = y
            y += cellH[i]
            width = max(width, cellW[i] * GLYPHS.size)
        }
        val bmp = Bitmap.createBitmap(kotlin.math.ceil(width).toInt() + 1, kotlin.math.ceil(y).toInt() + 1, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        for (i in 0 until 3) {
            tp.textSize = textSize[i]
            for (j in GLYPHS.indices) c.drawText(GLYPHS[j], j * cellW[i] + pad, rowY[i] + pad - ascent[i], tp)
        }
        paint.shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
}

/**
 * The living background: a breathing aurora seen THROUGH the water surface
 * (refraction, specular glints, crest light, foam where fronts collide, glowing
 * rims where waves hit UI elements), plus 920 glyphs in three parallax layers
 * that float on the waves, get lit by crests and constantly re-roll. Pair with
 * [AbyssWaveOverlay].
 */
@Composable
fun AbyssAnimatedBackground(
    palette: AbyssPalette,
    state: AbyssBackgroundState,
    modifier: Modifier = Modifier,
    dim: Float = 0f,
    glyphCount: Int = 920,
) {
    val context = LocalContext.current
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(WATER_SRC) else null
    }
    val atlas = remember(glyphCount) { GlyphAtlas(glyphCount) }
    val slope = remember { FloatArray(2) }
    // Plain holders, not snapshot state: written every frame, never observed.
    val clock = remember { LongArray(3) }      // [0] last frame, [1] next ambient drop, [2] last water generation
    val rnd = remember { Random(11) }
    val wanderers = remember { Array(2) { Wanderer() } }

    // Device tilt → parallax.
    DisposableEffect(state) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                state.tiltTargetX = (-e.values[0] / 9.81f).coerceIn(-1f, 1f)
                state.tiltTargetY = (e.values[1] / 9.81f - 0.6f).coerceIn(-1f, 1f)
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (sm != null && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm?.unregisterListener(listener) }
    }

    val glyphs = remember(glyphCount) {
        val r = Random(42)
        Array(glyphCount) { i ->
            val layer = when { i % 5 == 0 -> 2; i % 2 == 0 -> 1; else -> 0 }
            Glyph(
                x = r.nextFloat(), y = r.nextFloat() * 2600f, layer = layer,
                speed = when (layer) { 0 -> 4f + r.nextFloat() * 5f; 1 -> 8f + r.nextFloat() * 7f; else -> 14f + r.nextFloat() * 9f },
                // 2.5× the glyphs → each fainter, so the field reads as depth, not noise.
                baseAlpha = when (layer) { 0 -> 0.022f + r.nextFloat() * 0.036f; 1 -> 0.036f + r.nextFloat() * 0.056f; else -> 0.056f + r.nextFloat() * 0.072f },
                phase = r.nextFloat() * TAU,
                sway = 3f + r.nextFloat() * (4f + layer * 4f),
                seed = r.nextInt(),
                periodNs = 140_000_000L + (r.nextFloat() * 120_000_000L).toLong(),
                offsetNs = (r.nextFloat() * 1_000_000_000L).toLong(),
            )
        }
    }

    LaunchedEffect(state) {
        val sim = state.sim
        sim.start()
        try {
            while (true) {
                withFrameNanos { now ->
                    if (state.startNanos == 0L) state.startNanos = now
                    val last = clock[0]
                    val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    clock[0] = now
                    val t = state.timeSec(now)

                    val k = (dt * 3.5f).coerceAtMost(1f)
                    state.tiltX += (state.tiltTargetX - state.tiltX) * k
                    state.tiltY += (state.tiltTargetY - state.tiltY) * k

                    // ---- feed the water (all cheap: the physics runs on its own thread)
                    val vw = state.viewW
                    val vh = state.viewH
                    if (vw > 0f) sim.resize(vw, vh)
                    if (state.obstaclesDirty) {
                        state.obstaclesDirty = false
                        sim.setObstacles(state.obstacles.values.map {
                            ObstacleSnap(it.l, it.t, it.r, it.b, it.radius, it.seed)
                        })
                    }
                    val cell = sim.cell
                    val charge = state.chargeLevel(now)
                    if (charge > 0f) {
                        // A held finger hums: a pulsing source that keeps sending rings out.
                        sim.drip(state.chargePos.x, state.chargePos.y, 0.07f * charge * sin(t * TAU * 2.6f), cell * 1.3f)
                    }
                    if (vw > 0f && now > clock[1]) {
                        // Ambient life: drops and small splashes all over the surface.
                        clock[1] = now + 450_000_000L + (rnd.nextFloat() * 950_000_000L).toLong()
                        val x = vw * (0.05f + rnd.nextFloat() * 0.9f)
                        val y = vh * (0.05f + rnd.nextFloat() * 0.9f)
                        if (rnd.nextFloat() < 0.65f) sim.drip(x, y, 0.22f + rnd.nextFloat() * 0.25f, cell * (1.3f + rnd.nextFloat()))
                        else sim.splash(x, y, 0.42f + rnd.nextFloat() * 0.3f, cell * (2.2f + rnd.nextFloat() * 1.5f), lobes = 3)
                    }
                    if (vw > 0f) for (wd in wanderers) driveWanderer(wd, now, dt, vw, vh, cell, rnd, sim)
                    sim.request(dt, t)

                    val water = sim.latest()
                    state.water = water
                    if (water.generation.toLong() != clock[2]) {
                        clock[2] = water.generation.toLong()
                        for (c in 0 until water.collisionCount) {
                            state.collisionBurst(water.collisions[c * 2], water.collisions[c * 2 + 1])
                        }
                    }

                    // ---- glyph drift (symbols are re-rolled in the draw pass from the clock)
                    val h = if (vh > 0f) vh else 2600f
                    for (g in glyphs) {
                        g.y -= g.speed * dt
                        if (g.y < -40f) { g.y = h + 40f; g.x = rnd.nextFloat() }
                    }

                    // ---- sparks
                    if (state.sparks.isNotEmpty()) {
                        val drag = 0.90f.pow(dt * 60f)
                        val iter = state.sparks.iterator()
                        while (iter.hasNext()) {
                            val s = iter.next()
                            if (now - s.born > s.lifeNs) { iter.remove(); continue }
                            s.x += s.vx * dt; s.y += s.vy * dt
                            s.vx *= drag; s.vy = s.vy * drag + 30f * dt
                        }
                    }
                    state.frameNanos = now
                }
            }
        } finally {
            sim.stop()
        }
    }

    Canvas(modifier.fillMaxSize()) {
        state.viewW = size.width
        state.viewH = size.height
        val now = state.frameNanos
        drawWater(palette, shader, state, now)
        drawGlyphs(palette, glyphs, atlas, slope, now, state, dim)
    }
}

/** Steers one wanderer: idle → a curving dash across the water that leaves a wake → idle. */
private fun driveWanderer(
    w: Wanderer, now: Long, dt: Float, vw: Float, vh: Float, cell: Float, rnd: Random, sim: WaterSim,
) {
    if (now > w.until) {
        if (w.next == 0L) w.next = now + (rnd.nextFloat() * 2_500_000_000L).toLong()
        if (now < w.next) return
        w.x = vw * (0.1f + rnd.nextFloat() * 0.8f)
        w.y = vh * (0.1f + rnd.nextFloat() * 0.8f)
        val a = rnd.nextFloat() * TAU
        val v = 260f + rnd.nextFloat() * 300f
        w.vx = cos(a) * v; w.vy = sin(a) * v
        w.turn = (rnd.nextFloat() - 0.5f) * 3.2f
        w.until = now + 900_000_000L + (rnd.nextFloat() * 1_600_000_000L).toLong()
        w.next = w.until + 1_800_000_000L + (rnd.nextFloat() * 4_000_000_000L).toLong()
        return
    }
    // Curve, wobble, bounce off the frame.
    w.turn += (rnd.nextFloat() - 0.5f) * 4f * dt
    val ca = cos(w.turn * dt); val sa = sin(w.turn * dt)
    val nvx = w.vx * ca - w.vy * sa
    w.vy = w.vx * sa + w.vy * ca; w.vx = nvx
    w.x += w.vx * dt; w.y += w.vy * dt
    if (w.x < cell * 2f || w.x > vw - cell * 2f) { w.vx = -w.vx; w.x = w.x.coerceIn(cell * 2f, vw - cell * 2f) }
    if (w.y < cell * 2f || w.y > vh - cell * 2f) { w.vy = -w.vy; w.y = w.y.coerceIn(cell * 2f, vh - cell * 2f) }
    if (now - w.lastDrip > 38_000_000L) {
        w.lastDrip = now
        sim.drip(w.x, w.y, 0.11f, cell * 1.25f)
    }
}

/**
 * Sparks, the charge orb and the crests that roll OVER elements — above all
 * content, touch-transparent.
 */
@Composable
fun AbyssWaveOverlay(
    palette: AbyssPalette,
    state: AbyssBackgroundState,
    modifier: Modifier = Modifier,
) {
    val overShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(OVER_SRC) else null
    }
    val bound = remember { arrayOfNulls<BitmapShader>(2) }
    Canvas(modifier.fillMaxSize()) {
        val now = state.frameNanos
        if (overShader != null) drawOverCrests(palette, overShader, bound, state, now)
        if (state.sparks.isNotEmpty()) drawSparks(palette, state, now)
        if (state.charging) drawCharge(palette, state, now)
    }
}

// ============================================================ drawing: water

/**
 * The water's GPU side: the latest snapshot uploaded into two small bitmaps
 * (bilinear-filtered so a 92-column grid renders as a smooth surface). Bitmap
 * shaders are rebuilt only when their content changes, so the GPU always sees
 * the newest surface.
 */
internal class WaterGpu {
    var field: Bitmap? = null
    var wall: Bitmap? = null
    var fieldShader: BitmapShader? = null
    var wallShader: BitmapShader? = null
    private var cols = 0
    private var rows = 0
    private var generation = -1
    private var wallVersion = -1
    var boundField: BitmapShader? = null
    var boundWall: BitmapShader? = null
    // Android 10–12 fallback.
    val fallbackPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    val dst = RectF()
    var fallbackPixels = IntArray(0)
    var fallbackBitmap: Bitmap? = null
    var fallbackGeneration = -1

    val ready get() = fieldShader != null && wallShader != null

    fun sync(w: WaterSnapshot) {
        if (!w.ready) return
        if (w.cols != cols || w.rows != rows) {
            cols = w.cols; rows = w.rows
            field = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
            wall = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888)
            generation = -1; wallVersion = -1
        }
        if (w.generation != generation) {
            field?.let { it.setPixels(w.pixels, 0, cols, 0, 0, cols, rows); fieldShader = linearShader(it) }
            generation = w.generation
        }
        if (w.wallVersion != wallVersion) {
            wall?.let { it.setPixels(w.wallPixels, 0, cols, 0, 0, cols, rows); wallShader = linearShader(it) }
            wallVersion = w.wallVersion
        }
    }
}

private fun linearShader(b: Bitmap): BitmapShader =
    BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) filterMode = BitmapShader.FILTER_MODE_LINEAR
    }

private fun DrawScope.drawWater(
    palette: AbyssPalette, shader: RuntimeShader?, state: AbyssBackgroundState, now: Long,
) {
    val water = state.water
    val gpu = state.gpu
    gpu.sync(water)
    if (shader != null && gpu.ready && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val fs = gpu.fieldShader!!
        val ws = gpu.wallShader!!
        if (gpu.boundField !== fs) { shader.setInputShader("uField", fs); gpu.boundField = fs }
        if (gpu.boundWall !== ws) { shader.setInputShader("uWall", ws); gpu.boundWall = ws }
        shader.setFloatUniform("uRes", size.width, size.height)
        shader.setFloatUniform("uTime", (now % 3_600_000_000_000L) / 1_000_000_000f)
        shader.setFloatUniform("uTilt", state.tiltX, state.tiltY)
        shader.setFloatUniform("uCell", water.cell)
        shader.setFloatUniform("uBg", palette.background.red, palette.background.green, palette.background.blue)
        shader.setFloatUniform("uAccent", palette.wave.red, palette.wave.green, palette.wave.blue)
        shader.setFloatUniform("uAccent2", palette.secondary.red, palette.secondary.green, palette.secondary.blue)
        shader.setFloatUniform("uDark", if (palette.dark) 1f else 0f)
        state.packObstacles(state.timeSec(now))
        shader.setFloatUniform("uObs", state.obsA)
        shader.setFloatUniform("uObsB", state.obsB)
        shader.setFloatUniform("uObsN", state.obsN.toFloat())
        drawRect(ShaderBrush(shader))
    } else {
        drawRect(palette.background)
        drawRect(
            Brush.radialGradient(
                listOf(palette.wave.copy(alpha = if (palette.dark) 0.07f else 0.05f), Color.Transparent),
                center = Offset(size.width * (0.5f + state.tiltX * 0.05f), size.height * 0.3f),
                radius = size.maxDimension * 0.7f,
            ),
        )
        drawWaterFallback(palette, gpu, water)
    }
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, palette.background.copy(alpha = 0.55f)),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.maxDimension * 0.82f,
        ),
    )
}

/** Android 10–12: colourise the surface on the CPU and stretch it with bilinear filtering. */
private fun DrawScope.drawWaterFallback(palette: AbyssPalette, gpu: WaterGpu, water: WaterSnapshot) {
    if (!water.ready || !water.active) return
    val n = water.cols * water.rows
    if (gpu.fallbackPixels.size != n) {
        gpu.fallbackPixels = IntArray(n)
        gpu.fallbackBitmap = Bitmap.createBitmap(water.cols, water.rows, Bitmap.Config.ARGB_8888)
        gpu.fallbackGeneration = -1
    }
    val bmp = gpu.fallbackBitmap ?: return
    if (gpu.fallbackGeneration != water.generation) {
        gpu.fallbackGeneration = water.generation
        val rgb = palette.wave.toArgb() and 0x00FFFFFF
        val cu = water.heights
        val c = water.cols
        for (y in 1 until water.rows - 1) {
            val row = y * c
            for (x in 1 until c - 1) {
                val i = row + x
                val h = cu[i]
                val s = abs(cu[i + 1] - cu[i - 1]) + abs(cu[i + c] - cu[i - c])
                val a = ((abs(h) * 1.1f + s * 1.6f) * 0.55f).coerceIn(0f, 0.6f)
                gpu.fallbackPixels[i] = ((a * 255f).toInt() shl 24) or rgb
            }
        }
        bmp.setPixels(gpu.fallbackPixels, 0, c, 0, 0, c, water.rows)
    }
    // Cell (x,y) sits at ((x-1)*cell .. x*cell): shift by one cell.
    val cell = water.cell
    gpu.dst.set(-cell, -cell, (water.cols - 1) * cell, (water.rows - 1) * cell)
    drawIntoCanvas { it.nativeCanvas.drawBitmap(bmp, null, gpu.dst, gpu.fallbackPaint) }
}

/** Crests drawn on top of the elements that are currently in OVER mode. */
private fun DrawScope.drawOverCrests(
    palette: AbyssPalette, shader: RuntimeShader, bound: Array<BitmapShader?>,
    state: AbyssBackgroundState, now: Long,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val water = state.water
    val gpu = state.gpu
    if (!water.active || !gpu.ready || state.obstacles.isEmpty()) return
    val t = state.timeSec(now)
    var brush: ShaderBrush? = null
    for (o in state.obstacles.values) {
        if (obstacleMode(o.seed, t) != MODE_OVER) continue
        if (brush == null) {
            val fs = gpu.fieldShader!!
            if (bound[0] !== fs) { shader.setInputShader("uField", fs); bound[0] = fs }
            shader.setFloatUniform("uCell", water.cell)
            shader.setFloatUniform("uTime", (now % 3_600_000_000_000L) / 1_000_000_000f)
            shader.setFloatUniform("uAccent", palette.wave.red, palette.wave.green, palette.wave.blue)
            shader.setFloatUniform("uAccent2", palette.secondary.red, palette.secondary.green, palette.secondary.blue)
            shader.setFloatUniform("uDark", if (palette.dark) 1f else 0f)
            brush = ShaderBrush(shader)
        }
        val r = o.radius.coerceAtMost(minOf(o.r - o.l, o.b - o.t) / 2f)
        drawRoundRect(brush, topLeft = Offset(o.l, o.t), size = Size(o.r - o.l, o.b - o.t),
            cornerRadius = CornerRadius(r, r))
    }
}

// ============================================================ drawing: glyphs

/** Blends packed RGB [a]→[b] by [f] (0..1). */
private fun mixRgb(a: Int, b: Int, f: Float): Int {
    val q = (f * 256f).toInt().coerceIn(0, 256)
    val p = 256 - q
    val r = (((a shr 16) and 0xFF) * p + ((b shr 16) and 0xFF) * q) shr 8
    val g = (((a shr 8) and 0xFF) * p + ((b shr 8) and 0xFF) * q) shr 8
    val bl = ((a and 0xFF) * p + (b and 0xFF) * q) shr 8
    return (r shl 16) or (g shl 8) or bl
}

private fun DrawScope.drawGlyphs(
    palette: AbyssPalette, glyphs: Array<Glyph>, at: GlyphAtlas, slope: FloatArray,
    now: Long, state: AbyssBackgroundState, dim: Float,
) {
    val px = 1.sp.toPx()
    if (px != at.px) at.build(px)
    if (at.paletteKey !== palette) {
        at.paletteKey = palette
        at.symbol = palette.symbol.toArgb() and 0xFFFFFF
        at.secondary = palette.secondary.toArgb() and 0xFFFFFF
        at.wave = palette.wave.toArgb() and 0xFFFFFF
    }
    val t = (now % 3_600_000_000_000L) / 1_000_000_000f
    val charge = state.chargeLevel(now)
    val water = state.water
    val waterLive = water.active
    val w = size.width
    val hView = size.height
    val n = GLYPHS.size
    val dimK = 0.55f * dim
    val verts = at.verts
    val texs = at.texs
    val colors = at.colors
    var count = 0

    for (g in glyphs) {
        val depth = 1f + g.layer
        // Flow field + sway + tilt parallax (near layers move more).
        var x = g.x * w +
            sin(t * (0.25f + g.layer * 0.12f) + g.phase) * g.sway +
            sin(g.y * 0.0032f + t * 0.22f + g.layer) * 9f * depth +
            state.tiltX * (5f + g.layer * 10f)
        var y = g.y + cos(g.x * 6.0f + t * 0.18f) * 5f * depth + state.tiltY * (4f + g.layer * 8f)
        if (y < -30f || y > hView + 30f) continue

        var glow = 0f
        if (waterLive) {
            // Glyphs float ON the water: slide down the slopes, light up on crests.
            water.slopeAt(x, y, slope)
            val push = 26f + g.layer * 20f
            x -= slope[0] * push
            y -= slope[1] * push
            val hh = water.heightAt(x, y)
            glow = (abs(hh) * 1.3f + (abs(slope[0]) + abs(slope[1])) * 2.2f).coerceAtMost(1f)
        }
        if (charge > 0f) {
            val dx = state.chargePos.x - x
            val dy = state.chargePos.y - y
            val d = hypot(dx, dy).coerceAtLeast(1f)
            val pull = charge * 26f * exp(-(d / 260f) * (d / 260f))
            x += dx / d * pull; y += dy / d * pull
            glow = max(glow, charge * 0.6f * exp(-(d / 200f) * (d / 200f)))
        }

        // Symbol re-roll: a new character every ~0.2 s, each glyph on its own beat.
        val tt = now + g.offsetNs
        val slot = (tt / g.periodNs).toInt()
        val fresh = (tt % g.periodNs).toFloat() / g.periodNs   // 0 right after a change
        var hs = slot * -0x61c88647 xor g.seed
        hs = hs xor (hs ushr 15); hs *= 0x2c1b3c6d; hs = hs xor (hs ushr 12)
        val shown = (hs ushr 1) % n

        val band = sin((x * 0.7f + y) * 0.0032f - t * 0.55f)
        val sheen = ((band - 0.80f) / 0.20f).coerceIn(0f, 1f)
        val tw = sin(t * (0.6f + g.layer * 0.3f) + g.phase) * 0.5f + 0.5f

        var alpha = g.baseAlpha * (0.4f + 0.6f * tw) + sheen * 0.12f + glow * 0.75f
        if (fresh < 0.2f) alpha += 0.05f * (1f - fresh * 5f)    // a flicker on every change
        alpha *= (1f - dimK * (1f - glow))
        if (alpha < 0.012f) continue   // invisible → not emitted

        val rgb = mixRgb(mixRgb(at.symbol, at.secondary, sheen * 0.85f), at.wave, glow)
        val argb = ((alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24) or rgb

        val L = g.layer
        val cw = at.cellW[L]
        val ch = at.cellH[L]
        val left = x - at.pad
        val top = y + at.textSize[L] * 0.8f + at.ascent[L] - at.pad
        val u0 = shown * cw
        val v0 = at.rowY[L]
        val o = count * 8
        verts[o] = left; verts[o + 1] = top
        verts[o + 2] = left + cw; verts[o + 3] = top
        verts[o + 4] = left + cw; verts[o + 5] = top + ch
        verts[o + 6] = left; verts[o + 7] = top + ch
        texs[o] = u0; texs[o + 1] = v0
        texs[o + 2] = u0 + cw; texs[o + 3] = v0
        texs[o + 4] = u0 + cw; texs[o + 5] = v0 + ch
        texs[o + 6] = u0; texs[o + 7] = v0 + ch
        val co = count * 4
        colors[co] = argb; colors[co + 1] = argb; colors[co + 2] = argb; colors[co + 3] = argb
        count++
    }
    if (count == 0) return
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawVertices(
            android.graphics.Canvas.VertexMode.TRIANGLES, count * 8, verts, 0, texs, 0,
            colors, 0, at.indices, 0, count * 6, at.paint,
        )
    }
}

// ============================================================ drawing: overlay

private fun DrawScope.drawSparks(palette: AbyssPalette, state: AbyssBackgroundState, now: Long) {
    state.sparks.forEach { s ->
        val age = ((now - s.born).toFloat() / s.lifeNs).coerceIn(0f, 1f)
        val fade = (1f - age).pow(1.6f)
        val c = lerp(palette.wave, Color.White, 0.25f + 0.45f * s.warm)
        val r = s.size * (1f - 0.4f * age)
        drawCircle(
            brush = Brush.radialGradient(
                listOf(c.copy(alpha = 0.35f * fade), Color.Transparent),
                center = Offset(s.x, s.y), radius = r * 4f),
            radius = r * 4f, center = Offset(s.x, s.y),
        )
        drawCircle(color = c.copy(alpha = 0.9f * fade), radius = r, center = Offset(s.x, s.y))
    }
}

private fun DrawScope.drawCharge(palette: AbyssPalette, state: AbyssBackgroundState, now: Long) {
    val held = now - state.chargeStart
    if (held < 160_000_000L) return
    val appear = ((held - 160_000_000L) / 220_000_000f).coerceIn(0f, 1f)
    val c = state.chargeLevel(now)
    val p = state.chargePos
    val t = (now % 3_600_000_000_000L) / 1_000_000_000f
    val breathe = 1f + 0.06f * sin(t * 9f)
    val orb = (26f + 46f * c) * breathe * appear

    drawCircle(
        brush = Brush.radialGradient(
            listOf(palette.wave.copy(alpha = (0.18f + 0.32f * c) * appear), Color.Transparent),
            center = p, radius = orb * 2.6f),
        radius = orb * 2.6f, center = p,
    )
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.55f * appear * (0.4f + 0.6f * c)), palette.wave.copy(alpha = 0f)),
            center = p, radius = orb * 0.6f),
        radius = orb * 0.6f, center = p,
    )
    val ringR = orb + 18f
    drawCircle(color = palette.wave.copy(alpha = 0.18f * appear), radius = ringR, center = p, style = Stroke(width = 2f))
    drawArc(
        color = palette.wave.copy(alpha = 0.85f * appear),
        startAngle = -90f, sweepAngle = 360f * c, useCenter = false,
        topLeft = Offset(p.x - ringR, p.y - ringR),
        size = Size(ringR * 2f, ringR * 2f),
        style = Stroke(width = 3f, cap = StrokeCap.Round),
    )
}

// ============================================================ shaders

/**
 * The aurora seen through luminescent water. uField: R = height, G/B = slope
 * (soft-compressed bytes, bilinear-filtered); uWall: R = wall cell, G = "near
 * an element" gate. Element outlines are NOT taken from the coarse grid: the
 * exact rounded rectangles arrive as uniforms (uObs/uObsB), so rims are drawn
 * per pixel along the real shape — no staircase. Light: refraction of the
 * aurora, emissive crests and fronts, a soft bloom, foam where fronts stack,
 * specular glints, and a glowing contour where water hits a solid element.
 */
private const val WATER_SRC = """
uniform float2 uRes;
uniform float uTime;
uniform float2 uTilt;
uniform float uCell;
uniform float3 uBg;
uniform float3 uAccent;
uniform float3 uAccent2;
uniform float uDark;
uniform shader uField;
uniform shader uWall;
uniform float4 uObs[40];
uniform float4 uObsB[40];
uniform float uObsN;

float dec(float s) {
    float e = s * 2.0 - 1.0;
    return e / max(1.0 - abs(e), 0.02);
}

float energyAt(float2 c) {
    float4 f = float4(uField.eval(c));
    float h = dec(f.r);
    float2 g = float2(dec(f.g), dec(f.b)) * 0.5;
    return clamp(abs(h) * 2.4 + length(g) * 3.4, 0.0, 1.0);
}

half4 main(float2 p) {
    // Grid cell (x,y) covers [(x-1)*cell, x*cell): +1 cell offset, sample at centre.
    float2 c = p / uCell + float2(1.0, 1.0);
    float4 f = float4(uField.eval(c));
    float h = dec(f.r);
    float2 g = float2(dec(f.g), dec(f.b)) * 0.5;
    float slope = length(g);
    float gate = float(uWall.eval(c).g);

    // ---- exact element outlines (only near elements: the gate keeps this cheap)
    float dist = 1e4;
    float mode = 0.0;
    float2 nrm = float2(0.0);
    if (gate > 0.002) {
        for (int i = 0; i < 40; i++) {
            if (float(i) >= uObsN) break;
            float4 o = uObs[i];
            float2 dp = p - o.xy;
            float2 ad = abs(dp) - o.zw;
            if (max(ad.x, ad.y) > 48.0) continue;
            float r = uObsB[i].x;
            float2 q = ad + float2(r);
            float d = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
            if (d < dist) {
                dist = d;
                mode = uObsB[i].y;
                float2 sg = float2(dp.x < 0.0 ? -1.0 : 1.0, dp.y < 0.0 ? -1.0 : 1.0);
                if (max(q.x, q.y) > 0.0) nrm = normalize(max(q, float2(0.0)) + float2(0.0001)) * sg;
                else nrm = q.x > q.y ? float2(sg.x, 0.0) : float2(0.0, sg.y);
            }
        }
    }
    float insideAny = smoothstep(0.75, -0.75, dist);
    float solidIn = mode < 1.5 ? insideAny : 0.0;
    float underIn = (mode > 1.5 && mode < 2.5) ? insideAny : 0.0;

    // ---- aurora, refracted by the surface slope
    float2 sp = p - g * 52.0 + uTilt * 36.0;
    float2 uv = sp / uRes;
    float t = uTime * 0.12;
    float fl = sin(uv.x * 3.1 + t * 1.7 + sin(uv.y * 2.3 + t));
    fl += sin(uv.y * 4.2 - t * 1.3 + sin(uv.x * 3.7 - t * 0.7) * 1.2);
    fl += sin((uv.x + uv.y) * 2.6 + t * 0.9);
    fl = fl / 3.0;
    float breathe = 0.85 + 0.15 * sin(uTime * 0.35);
    float band = smoothstep(0.25, 0.95, 0.5 + 0.5 * fl) * breathe;
    float top = 1.0 - smoothstep(0.0, 0.85, uv.y);
    float dark = uDark > 0.5 ? 1.0 : 0.0;
    float k = dark > 0.5 ? 1.0 : 0.62;
    float3 col = mix(uBg, mix(uAccent, uAccent2, 0.5 + 0.5 * fl), band * (0.05 + 0.07 * top) * k);

    // ---- luminescent surface
    float3 n = normalize(float3(-g * 3.2, 1.0));
    float spec = pow(max(dot(n, float3(-0.32, -0.52, 0.79)), 0.0), 26.0);
    float crest = smoothstep(0.012, 0.36, h);
    float trough = smoothstep(0.015, 0.40, -h);
    float front = smoothstep(0.008, 0.12, slope);
    float energy = clamp(abs(h) * 2.2 + slope * 3.2, 0.0, 1.0);
    float glint = 0.5 + 0.5 * sin(p.x * 0.045 + p.y * 0.03 + uTime * 2.4 + h * 6.0);
    float grain = 0.5 + 0.5 * sin(p.x * 0.31 + sin(p.y * 0.27 + uTime * 3.1) * 2.0 + uTime * 5.0);
    float foam = smoothstep(0.40, 0.92, h) * (0.55 + 0.45 * grain);
    // Soft bloom: energy a couple of cells around, so bright fronts halo like phosphorescence.
    float wide = (energyAt(c + float2(2.3, 0.0)) + energyAt(c - float2(2.3, 0.0)) +
                  energyAt(c + float2(0.0, 2.3)) + energyAt(c - float2(0.0, 2.3))) * 0.25;

    float3 lit = mix(uAccent, uAccent2, clamp(0.5 + h * 0.9, 0.0, 1.0));
    float3 glowC = lit * 1.2 + float3(0.06);
    float emis = crest * 0.30 + front * 0.26 * (0.6 + 0.4 * glint) + spec * 0.65 * energy
               + foam * 0.50 + wide * 0.20;
    // Nothing glows inside a solid element; water slipping under one runs dim.
    emis *= (1.0 - solidIn) * (1.0 - 0.55 * underIn);

    // ---- glowing contour where the water strikes a solid element
    if (mode < 1.5 && dist < 40.0) {
        float2 pe = p + nrm * (max(-dist, 0.0) + uCell * 1.15);
        float e = energyAt(pe / uCell + float2(1.0, 1.0));
        float line = exp(-abs(dist) / 1.3);
        float halo = exp(-max(dist, 0.0) / 7.0) * (1.0 - insideAny);
        emis += (line * (0.10 + 1.15 * e) + halo * e * 0.65) * gate;
    }

    if (dark > 0.5) {
        col += glowC * emis * 0.95;
    } else {
        col = mix(col, lit, clamp(emis * 0.75, 0.0, 0.8));
    }
    col = mix(col, uBg * 0.6, (trough * 0.12 + underIn * 0.10) * k);
    col = min(col, float3(1.0));
    return half4(col.r, col.g, col.b, 1.0);
}
"""

/**
 * Crests rolling OVER an element (drawn clipped to the element's own rounded
 * shape, so the edge is exact): transparent except where the water moves,
 * premultiplied, glowing like the water below.
 */
private const val OVER_SRC = """
uniform float uCell;
uniform float uTime;
uniform float3 uAccent;
uniform float3 uAccent2;
uniform float uDark;
uniform shader uField;

float dec(float s) {
    float e = s * 2.0 - 1.0;
    return e / max(1.0 - abs(e), 0.02);
}

half4 main(float2 p) {
    float2 c = p / uCell + float2(1.0, 1.0);
    float4 f = float4(uField.eval(c));
    float h = dec(f.r);
    float2 g = float2(dec(f.g), dec(f.b)) * 0.5;
    float slope = length(g);
    float3 n = normalize(float3(-g * 3.2, 1.0));
    float spec = pow(max(dot(n, float3(-0.32, -0.52, 0.79)), 0.0), 22.0);
    float crest = smoothstep(0.02, 0.42, h);
    float front = smoothstep(0.012, 0.14, slope);
    float glint = 0.5 + 0.5 * sin(p.x * 0.05 + p.y * 0.035 + uTime * 2.6 + h * 6.0);
    float energy = clamp(abs(h) * 2.0 + slope * 3.0, 0.0, 1.0);
    float a = crest * 0.32 + front * 0.28 * (0.6 + 0.4 * glint) + spec * 0.5 * energy;
    a = clamp(a * (uDark > 0.5 ? 1.0 : 0.75), 0.0, 0.65);
    float3 col = mix(uAccent, uAccent2, clamp(0.5 + h * 0.9, 0.0, 1.0)) * 1.15 + float3(0.05);
    col = min(mix(col, float3(1.0), spec * 0.5), float3(1.0));
    return half4(col.r * a, col.g * a, col.b * a, a);
}
"""
