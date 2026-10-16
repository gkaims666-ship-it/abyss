package com.abyss.app.ui.background

import android.content.Context
import android.graphics.RuntimeShader
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.abyss.app.ui.theme.AbyssPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// ============================================================ model

/** One touch wave. [reach] scales its travel (trail waves are small, charged ones big). */
class Ripple(val center: Offset, val bornNanos: Long, val strength: Float, val reach: Float)

/** A spark thrown out of a wave origin. */
internal class Spark(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val born: Long, val lifeNs: Long, val size: Float, val warm: Float,
)

private const val RIPPLE_LIFE_NS = 1_800_000_000L
private const val MAX_RIPPLES = 6
private const val CHARGE_DELAY_NS = 380_000_000L
private const val CHARGE_FULL_NS = 1_100_000_000L

/**
 * One engine shared by: the touch modifier, the background (shader + glyphs)
 * and the overlay (rings, sparks, charge orb — drawn ABOVE content).
 */
@Stable
class AbyssBackgroundState {
    internal val ripples = mutableStateListOf<Ripple>()
    internal val sparks = ArrayList<Spark>()
    private val rnd = Random(3)

    var frameNanos by mutableLongStateOf(0L)
        internal set

    // Hold-to-charge.
    internal var charging = false
    internal var chargeStart = 0L
    internal var chargePos = Offset.Zero

    // Last known canvas size (for centre-screen waves).
    internal var viewW = 0f
    internal var viewH = 0f

    /** A wave from just above screen centre — used on launch and theme change. */
    fun emitCenter(strength: Float = 1f, reach: Float = 1.3f, sparkCount: Int = 20) {
        if (viewW <= 0f) return
        emit(Offset(viewW / 2f, viewH * 0.42f), strength, reach, sparkCount)
    }

    // Device tilt parallax (smoothed in the frame loop).
    internal var tiltX = 0f
    internal var tiltY = 0f
    internal var tiltTargetX = 0f
    internal var tiltTargetY = 0f

    fun emit(at: Offset, strength: Float = 1f, reach: Float = 1f, sparkCount: Int = 0) {
        val now = System.nanoTime()
        ripples.add(Ripple(at, now, strength, reach))
        while (ripples.size > MAX_RIPPLES) ripples.removeAt(0)
        repeat(sparkCount) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val v = (240f + rnd.nextFloat() * 420f) * strength
            sparks.add(Spark(
                at.x, at.y, cos(a) * v, sin(a) * v, now,
                (550_000_000L + (rnd.nextFloat() * 600_000_000L).toLong()),
                1.6f + rnd.nextFloat() * 3.2f, rnd.nextFloat(),
            ))
        }
        while (sparks.size > 90) sparks.removeAt(0)
    }

    internal fun age(r: Ripple, now: Long): Float =
        ((now - r.bornNanos).coerceAtLeast(0L)).toFloat() / RIPPLE_LIFE_NS

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
        if (c > 0.02f) emit(chargePos, 1f + 0.7f * c, 1f + 0.5f * c, sparkCount = 12 + (22 * c).toInt())
    }
}

@Composable
fun rememberAbyssBackgroundState(): AbyssBackgroundState = remember { AbyssBackgroundState() }

/**
 * Put on the app root. Observes touches on the Initial pass without consuming
 * them, so everything underneath stays fully interactive.
 *  - press → a wave + a few sparks, and starts charging
 *  - hold still → charge builds; release → a bigger wave and a burst of sparks
 *  - drag → cancels the charge and leaves a trail of small waves
 */
fun Modifier.abyssTouchWaves(state: AbyssBackgroundState): Modifier = pointerInput(state) {
    val lastPos = HashMap<PointerId, Offset>()
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { c ->
                when {
                    c.pressed && !c.previousPressed -> {
                        state.emit(c.position, 1f, 1f, sparkCount = 7)
                        state.beginCharge(c.position)
                        lastPos[c.id] = c.position
                    }
                    c.pressed -> {
                        if (state.charging && (c.position - state.chargePos).getDistance() > 28f) state.cancelCharge()
                        val prev = lastPos[c.id]
                        if (prev != null && (c.position - prev).getDistance() > 120f) {
                            state.emit(c.position, 0.32f, 0.32f, sparkCount = 2)
                            lastPos[c.id] = c.position
                        }
                    }
                    else -> {
                        if (lastPos.remove(c.id) != null) state.releaseCharge()
                    }
                }
            }
        }
    }
}

// ============================================================ glyph field

private val GLYPHS = listOf(
    "0", "1", "{", "}", "<", ">", "/", "λ", "Σ", "∞", "#", "*", "+", "~", "·", "∴",
    "◇", "≈", "∂", "∇", "Ω", "π", "=>", "fn", "::", "[]", "()", "&&", "|>", "%", "\$", "@",
)
private val LAYER_SIZE = floatArrayOf(11f, 14.5f, 19f)

private class Glyph(
    var x: Float, var y: Float,
    val layer: Int, val speed: Float, val baseAlpha: Float,
    val phase: Float, val sway: Float,
    var char: String, var nextScramble: Long, var scrambleUntil: Long = 0L,
)

/**
 * The living background. Warm breathing aurora (AGSL, Android 13+) that the
 * waves refract like water, with sparkling caustics on each ring; three
 * parallax layers of glyphs that follow a soft flow field, iridesce under a
 * travelling sheen, occasionally "decrypt", get pushed and lit by wavefronts,
 * and shift with device tilt. Pair with [AbyssWaveOverlay].
 */
@Composable
fun AbyssAnimatedBackground(
    palette: AbyssPalette,
    state: AbyssBackgroundState,
    modifier: Modifier = Modifier,
    dim: Float = 0f,
    glyphCount: Int = 56,
) {
    val context = LocalContext.current
    val measurer = rememberTextMeasurer()
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(AURORA_SRC) else null
    }
    val ripUniform = remember { FloatArray(MAX_RIPPLES * 4) }
    val layouts = remember { HashMap<String, TextLayoutResult>() }
    var lastNanos by remember { mutableLongStateOf(0L) }
    var lastAmbient by remember { mutableLongStateOf(0L) }
    var canvasSize by remember { mutableStateOf(Offset.Zero) }
    val rnd = remember { Random(11) }

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
        val start = System.nanoTime()
        List(glyphCount) { i ->
            val layer = when { i % 5 == 0 -> 2; i % 2 == 0 -> 1; else -> 0 }
            Glyph(
                x = r.nextFloat(), y = r.nextFloat() * 2600f, layer = layer,
                speed = when (layer) { 0 -> 4f + r.nextFloat() * 5f; 1 -> 8f + r.nextFloat() * 7f; else -> 14f + r.nextFloat() * 9f },
                baseAlpha = when (layer) { 0 -> 0.05f + r.nextFloat() * 0.07f; 1 -> 0.08f + r.nextFloat() * 0.10f; else -> 0.12f + r.nextFloat() * 0.12f },
                phase = r.nextFloat() * (2f * PI.toFloat()),
                sway = 3f + r.nextFloat() * (4f + layer * 4f),
                char = GLYPHS[r.nextInt(GLYPHS.size)],
                nextScramble = start + 2_000_000_000L + (r.nextFloat() * 10_000_000_000L).toLong(),
            )
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
                lastNanos = now
                state.frameNanos = now

                val k = (dt * 3.5f).coerceAtMost(1f)
                state.tiltX += (state.tiltTargetX - state.tiltX) * k
                state.tiltY += (state.tiltTargetY - state.tiltY) * k

                val h = if (canvasSize.y > 0f) canvasSize.y else 2600f
                glyphs.forEach { g ->
                    g.y -= g.speed * dt
                    if (g.y < -40f) { g.y = h + 40f; g.x = rnd.nextFloat() }
                    if (now > g.nextScramble) {
                        g.scrambleUntil = now + 480_000_000L
                        g.nextScramble = now + 4_000_000_000L + (rnd.nextFloat() * 12_000_000_000L).toLong()
                        g.char = GLYPHS[rnd.nextInt(GLYPHS.size)]
                    }
                }

                val drag = 0.90f.pow(dt * 60f)
                val iter = state.sparks.iterator()
                while (iter.hasNext()) {
                    val s = iter.next()
                    if (now - s.born > s.lifeNs) { iter.remove(); continue }
                    s.x += s.vx * dt; s.y += s.vy * dt
                    s.vx *= drag; s.vy = s.vy * drag + 30f * dt   // a whisper of gravity
                }

                state.ripples.removeAll { now - it.bornNanos > RIPPLE_LIFE_NS }
                if (canvasSize.x > 0f && now - lastAmbient > 6_500_000_000L) {
                    lastAmbient = now
                    state.emit(
                        Offset(canvasSize.x * (0.15f + rnd.nextFloat() * 0.7f),
                            canvasSize.y * (0.15f + rnd.nextFloat() * 0.6f)),
                        strength = 0.3f, reach = 0.8f,
                    )
                }
            }
        }
    }

    Canvas(modifier.fillMaxSize()) {
        canvasSize = Offset(size.width, size.height)
        state.viewW = size.width
        state.viewH = size.height
        val now = state.frameNanos
        drawBase(palette, shader, ripUniform, state, now)
        drawGlyphs(palette, glyphs, measurer, layouts, now, state, dim)
    }
}

/** Rings, sparks and the charge orb — above all content, touch-transparent. */
@Composable
fun AbyssWaveOverlay(
    palette: AbyssPalette,
    state: AbyssBackgroundState,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxSize()) {
        val now = state.frameNanos
        drawRings(palette, state, now)
        drawSparks(palette, state, now)
        drawCharge(palette, state, now)
    }
}

// ============================================================ drawing: base + glyphs

private fun DrawScope.drawBase(
    palette: AbyssPalette, shader: RuntimeShader?, rip: FloatArray,
    state: AbyssBackgroundState, now: Long,
) {
    if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        for (i in 0 until MAX_RIPPLES) {
            val r = state.ripples.getOrNull(i)
            val o = i * 4
            if (r == null) { rip[o] = 0f; rip[o + 1] = 0f; rip[o + 2] = -1f; rip[o + 3] = 0f }
            else {
                rip[o] = r.center.x; rip[o + 1] = r.center.y
                rip[o + 2] = state.age(r, now).coerceIn(0f, 1f)
                rip[o + 3] = r.strength * (0.4f + 0.6f * r.reach)
            }
        }
        shader.setFloatUniform("uRes", size.width, size.height)
        shader.setFloatUniform("uTime", (now % 3_600_000_000_000L) / 1_000_000_000f)
        shader.setFloatUniform("uMaxR", size.maxDimension * 0.8f)
        shader.setFloatUniform("uTilt", state.tiltX, state.tiltY)
        shader.setFloatUniform("uBg", palette.background.red, palette.background.green, palette.background.blue)
        shader.setFloatUniform("uAccent", palette.wave.red, palette.wave.green, palette.wave.blue)
        shader.setFloatUniform("uAccent2", palette.secondary.red, palette.secondary.green, palette.secondary.blue)
        shader.setFloatUniform("uDark", if (palette.dark) 1f else 0f)
        shader.setFloatUniform("uRip", rip)
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
    }
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, palette.background.copy(alpha = 0.6f)),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.maxDimension * 0.82f,
        ),
    )
}

private fun DrawScope.drawGlyphs(
    palette: AbyssPalette, glyphs: List<Glyph>, measurer: TextMeasurer,
    cache: HashMap<String, TextLayoutResult>, now: Long, state: AbyssBackgroundState, dim: Float,
) {
    val t = (now % 3_600_000_000_000L) / 1_000_000_000f
    val base = size.maxDimension * 0.8f
    glyphs.forEach { g ->
        val scrambling = now < g.scrambleUntil
        val shown = if (scrambling) GLYPHS[((now / 55_000_000L) + g.phase.toLong() * 7).toInt().mod(GLYPHS.size)] else g.char
        val fs = LAYER_SIZE[g.layer]
        val layout = cache.getOrPut("$shown|$fs") {
            measurer.measure(shown, TextStyle(fontSize = fs.sp, fontFamily = FontFamily.Monospace))
        }
        val depth = 1f + g.layer

        // Flow field + sway + tilt parallax (near layers move more).
        var x = g.x * size.width +
            sin(t * (0.25f + g.layer * 0.12f) + g.phase) * g.sway +
            sin(g.y * 0.0032f + t * 0.22f + g.layer) * 9f * depth +
            state.tiltX * (5f + g.layer * 10f)
        var y = g.y + cos(g.x * 6.0f + t * 0.18f) * 5f * depth + state.tiltY * (4f + g.layer * 8f)

        var glow = 0f
        state.ripples.forEach { r ->
            val age = state.age(r, now)
            if (age <= 1f) {
                val radius = easeOutCubic(age) * base * r.reach
                val dx = x - r.center.x
                val dy = y - r.center.y
                val d = hypot(dx, dy).coerceAtLeast(1f)
                val band = 80f + 60f * age
                val q = (d - radius) / band
                val kk = exp(-q * q) * (1f - age) * r.strength
                val push = kk * (10f + g.layer * 9f)
                x += dx / d * push
                y += dy / d * push
                if (kk > glow) glow = kk
            }
        }
        // The charge orb gently attracts nearby glyphs.
        val charge = state.chargeLevel(now)
        if (charge > 0f) {
            val dx = state.chargePos.x - x
            val dy = state.chargePos.y - y
            val d = hypot(dx, dy).coerceAtLeast(1f)
            val pull = charge * 26f * exp(-(d / 260f) * (d / 260f))
            x += dx / d * pull; y += dy / d * pull
            glow = maxOf(glow, charge * 0.6f * exp(-(d / 200f) * (d / 200f)))
        }

        val band = sin((x * 0.7f + y) * 0.0032f - t * 0.55f)
        val sheen = ((band - 0.80f) / 0.20f).coerceIn(0f, 1f)
        val tw = sin(t * (0.6f + g.layer * 0.3f) + g.phase) * 0.5f + 0.5f

        var alpha = g.baseAlpha * (0.4f + 0.6f * tw) + sheen * 0.16f + glow * 0.75f
        if (scrambling) alpha += 0.12f
        alpha *= (1f - 0.55f * dim * (1f - glow))

        val c1 = lerp(palette.symbol, palette.secondary, sheen * 0.85f)
        val color = lerp(c1, palette.wave, glow.coerceIn(0f, 1f))
        drawText(textLayoutResult = layout, color = color, topLeft = Offset(x, y), alpha = alpha.coerceIn(0f, 1f))
    }
}

// ============================================================ drawing: overlay

private fun DrawScope.drawRings(palette: AbyssPalette, state: AbyssBackgroundState, now: Long) {
    val base = size.maxDimension * 0.8f
    state.ripples.forEach { r ->
        val age = state.age(r, now)
        if (age > 1f) return@forEach
        val life = 1f - age
        val s = r.strength
        val maxR = base * r.reach
        val w = (18f + 46f * age) * (0.5f + 0.5f * r.reach.coerceAtMost(1.3f))
        for (i in 0 until 3) {
            val lag = i * 0.11f
            val a = ((age - lag) / (1f - lag)).coerceIn(0f, 1f)
            if (a <= 0f) continue
            val radius = easeOutCubic(a) * maxR
            val k = (1f - a) * s.coerceAtMost(1.4f) * (1f - i * 0.32f)
            glowRing(r.center, radius, w * (1f - i * 0.2f), palette.wave, 0.24f * k)
            drawCircle(
                color = lerp(palette.wave, palette.secondary, i * 0.35f).copy(alpha = (0.5f * k).coerceAtMost(0.8f)),
                radius = radius, center = r.center,
                style = Stroke(width = (2.4f - i * 0.6f) * (0.6f + life)),
            )
        }
        if (age < 0.35f) {
            val k = 1f - age / 0.35f
            val rr = 40f + 260f * age
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(palette.wave.copy(alpha = (0.30f * k * s).coerceAtMost(0.6f)), Color.Transparent),
                    center = r.center, radius = rr),
                radius = rr, center = r.center,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.18f * k * s.coerceAtMost(1f)),
                radius = 12f + 90f * easeOutCubic(age / 0.35f), center = r.center,
                style = Stroke(width = 1.2f),
            )
        }
    }
}

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
    if (!state.charging) return
    val held = now - state.chargeStart
    if (held < 160_000_000L) return
    val appear = ((held - 160_000_000L) / 220_000_000f).coerceIn(0f, 1f)
    val c = state.chargeLevel(now)
    val p = state.chargePos
    val t = now / 1_000_000_000f
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
    // Progress ring that fills as the charge builds.
    val ringR = orb + 18f
    drawCircle(
        color = palette.wave.copy(alpha = 0.18f * appear),
        radius = ringR, center = p, style = Stroke(width = 2f),
    )
    drawArc(
        color = palette.wave.copy(alpha = 0.85f * appear),
        startAngle = -90f, sweepAngle = 360f * c, useCenter = false,
        topLeft = Offset(p.x - ringR, p.y - ringR),
        size = androidx.compose.ui.geometry.Size(ringR * 2f, ringR * 2f),
        style = Stroke(width = 3f, cap = StrokeCap.Round),
    )
}

/** A soft glowing ring: bright at [radius], feathering out over [width]. */
private fun DrawScope.glowRing(center: Offset, radius: Float, width: Float, color: Color, alpha: Float) {
    if (radius < 2f || alpha <= 0.002f) return
    val outer = radius + width
    val inner = (radius - width * 1.8f).coerceAtLeast(0f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            (inner / outer) to Color.Transparent,
            (radius / outer) to color.copy(alpha = alpha.coerceIn(0f, 1f)),
            1f to Color.Transparent,
            center = center, radius = outer,
        ),
        radius = outer, center = center,
    )
}

private fun easeOutCubic(x: Float): Float { val i = 1f - x; return 1f - i * i * i }

// ============================================================ shader

/**
 * Breathing warm aurora that the waves REFRACT (the field is sampled through a
 * lens displacement around each ring), plus soft ring light, sparkling
 * caustics along the ring, and brighter interference where waves overlap.
 */
private const val AURORA_SRC = """
uniform float2 uRes;
uniform float uTime;
uniform float uMaxR;
uniform float2 uTilt;
uniform float3 uBg;
uniform float3 uAccent;
uniform float3 uAccent2;
uniform float uDark;
uniform float4 uRip[6];

half4 main(float2 p) {
    float2 disp = float2(0.0, 0.0);
    float light = 0.0;
    float tint = 0.0;
    float sparkle = 0.0;
    for (int i = 0; i < 6; i++) {
        float4 r = uRip[i];
        if (r.z >= 0.0 && r.z <= 1.0) {
            float a = r.z;
            float ia = 1.0 - a;
            float rad = (1.0 - ia * ia * ia) * uMaxR;
            float d = distance(p, r.xy);
            float w = 50.0 + 120.0 * a;
            float q = (d - rad) / w;
            float ring = exp(-q * q);
            float2 dir = (p - r.xy) / max(d, 1.0);
            float e = ring * ia * r.w;
            disp += dir * e * 22.0;
            float inner = (1.0 - smoothstep(0.0, max(rad, 1.0), d)) * 0.3;
            light += (ring + inner) * ia * r.w;
            tint += e;
            float ang = atan(dir.y, dir.x);
            sparkle += e * (0.5 + 0.5 * sin(ang * 14.0 + uTime * 2.2 + float(i) * 1.7));
        }
    }

    float2 sp = p - disp + uTilt * 36.0;
    float2 uv = sp / uRes;
    float t = uTime * 0.12;
    float f = sin(uv.x * 3.1 + t * 1.7 + sin(uv.y * 2.3 + t));
    f += sin(uv.y * 4.2 - t * 1.3 + sin(uv.x * 3.7 - t * 0.7) * 1.2);
    f += sin((uv.x + uv.y) * 2.6 + t * 0.9);
    f = f / 3.0;
    float breathe = 0.85 + 0.15 * sin(uTime * 0.35);
    float band = smoothstep(0.25, 0.95, 0.5 + 0.5 * f) * breathe;
    float top = 1.0 - smoothstep(0.0, 0.85, uv.y);
    float k = uDark > 0.5 ? 1.0 : 0.6;
    float3 col = mix(uBg, mix(uAccent, uAccent2, 0.5 + 0.5 * f), band * (0.05 + 0.07 * top) * k);

    float boost = light + light * light * 0.35;
    float3 lit = mix(uAccent, uAccent2, clamp(tint * 0.5, 0.0, 1.0));
    col = mix(col, lit, clamp(boost * 0.18 * k + sparkle * 0.12 * k, 0.0, 0.5));
    return half4(col.r, col.g, col.b, 1.0);
}
"""
