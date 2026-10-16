package com.abyss.app.ui.background

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.abyss.app.ui.theme.AbyssPalette
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private val GLYPHS = listOf(
    "0", "1", "{", "}", "<", ">", "/", "λ", "Σ", "∞", "Ø", "#", "*", "+",
    "a", "x", "·", "∴", "◇", "⟐", "≈", "⌀", "01", "10", "fn", "=>",
)

private class Glyph(
    var x: Float,          // 0..1 of width
    var y: Float,          // pixels
    val speed: Float,      // px/sec upward
    val size: Float,       // sp
    val baseAlpha: Float,
    val shimmer: Float,    // shimmer rate
    val phase: Float,
    val text: String,
    var layout: TextLayoutResult? = null,
)

private class Ripple(val center: Offset, val bornNanos: Long)

private const val RIPPLE_LIFE_NS = 1_250_000_000L // 1.25s

/**
 * The signature Abyss background: a deep gradient, slowly drifting shimmering
 * glyphs, and expanding wave ripples wherever the screen is tapped.
 *
 * Rendered on a Compose Canvas so it runs at the display's full refresh rate
 * (120 Hz on Nothing Phone 3a) on every supported Android version. An AGSL
 * depth layer (Android 13+) is planned as a later enhancement; [agslSupported]
 * is the capability hook for it.
 *
 * Place it as the lowest child of a Box. It observes taps on the Initial
 * pointer pass without consuming them, so content above still scrolls/clicks.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun AbyssAnimatedBackground(
    palette: AbyssPalette,
    modifier: Modifier = Modifier,
    glyphCount: Int = 46,
) {
    val measurer = rememberTextMeasurer()
    val waveShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(WAVE_SHADER_SRC) else null
    }
    var frameNanos by remember { mutableLongStateOf(0L) }
    var lastNanos by remember { mutableLongStateOf(0L) }
    var sizePx by remember { mutableStateOf(Offset.Zero) }
    val ripples = remember { mutableStateListOf<Ripple>() }

    val glyphs = remember(glyphCount) {
        val rnd = Random(42)
        List(glyphCount) {
            Glyph(
                x = rnd.nextFloat(),
                y = rnd.nextFloat() * 2600f,
                speed = 6f + rnd.nextFloat() * 20f,
                size = 11f + rnd.nextFloat() * 16f,
                baseAlpha = 0.05f + rnd.nextFloat() * 0.28f,
                shimmer = 0.4f + rnd.nextFloat() * 1.6f,
                phase = rnd.nextFloat() * (2f * PI.toFloat()),
                text = GLYPHS[rnd.nextInt(GLYPHS.size)],
            )
        }
    }

    // Drive one tick per display frame.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { now ->
                val dt = if (lastNanos == 0L) 0f else (now - lastNanos) / 1_000_000_000f
                lastNanos = now
                frameNanos = now
                val h = if (sizePx.y > 0f) sizePx.y else 2600f
                glyphs.forEach { g ->
                    g.y -= g.speed * dt
                    if (g.y < -40f) g.y = h + 40f
                }
                // Reap dead ripples.
                ripples.removeAll { now - it.bornNanos > RIPPLE_LIFE_NS }
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull { it.pressed && !it.previousPressed }
                            ?.let { ripples.add(Ripple(it.position, frameNanos)) }
                    }
                }
            },
    ) {
        sizePx = Offset(size.width, size.height)
        drawBase(palette, waveShader, frameNanos)
        drawGlyphs(palette, glyphs, measurer, frameNanos)
        drawRipples(palette, ripples, frameNanos)
    }
}

private fun DrawScope.drawBase(palette: AbyssPalette, shader: RuntimeShader?, nowNanos: Long) {
    if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val t = nowNanos / 1_000_000_000f
        shader.setFloatUniform("uRes", size.width, size.height)
        shader.setFloatUniform("uTime", t)
        shader.setFloatUniform("uWave", palette.wave.red, palette.wave.green, palette.wave.blue)
        shader.setFloatUniform("uBg", palette.background.red, palette.background.green, palette.background.blue)
        drawRect(ShaderBrush(shader))
    } else {
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to palette.background,
                0.55f to palette.surface,
                1f to palette.background,
            ),
        )
    }
    // Soft radial glow near the top for depth.
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.radialGradient(
            colors = listOf(palette.primary.copy(alpha = 0.06f), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.18f),
            radius = size.maxDimension * 0.6f,
        ),
    )
}

private const val WAVE_SHADER_SRC = """
uniform float2 uRes;
uniform float uTime;
uniform float3 uWave;
uniform float3 uBg;
half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uRes;
    float t = uTime;
    float w = 0.0;
    w += sin(uv.x * 6.0 + t * 0.6);
    w += sin((uv.y * 8.0 + t * 0.4) + sin(uv.x * 4.0 + t * 0.3) * 1.5);
    w += sin(length(uv - 0.5) * 10.0 - t * 0.8);
    w = w / 3.0;
    float glow = smoothstep(0.2, 1.0, 0.5 + 0.5 * w);
    float3 col = mix(uBg, uWave, glow * 0.18);
    col = col * (0.9 + 0.1 * uv.y);
    return half4(col.r, col.g, col.b, 1.0);
}
"""

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawGlyphs(
    palette: AbyssPalette,
    glyphs: List<Glyph>,
    measurer: TextMeasurer,
    nowNanos: Long,
) {
    val t = nowNanos / 1_000_000_000f
    glyphs.forEach { g ->
        val layout = g.layout ?: measurer.measure(
            text = g.text,
            style = TextStyle(fontSize = g.size.sp, fontFamily = FontFamily.Monospace),
        ).also { g.layout = it }

        val shimmer = (sin(t * g.shimmer + g.phase) * 0.5f + 0.5f)
        val alpha = (g.baseAlpha * (0.35f + 0.65f * shimmer)).coerceIn(0f, 1f)
        drawText(
            textLayoutResult = layout,
            color = palette.symbol,
            topLeft = Offset(g.x * size.width, g.y),
            alpha = alpha,
        )
    }
}

private fun DrawScope.drawRipples(
    palette: AbyssPalette,
    ripples: SnapshotStateList<Ripple>,
    nowNanos: Long,
) {
    ripples.forEach { r ->
        val age = (nowNanos - r.bornNanos).coerceAtLeast(0L).toFloat() / RIPPLE_LIFE_NS
        if (age > 1f) return@forEach
        val maxR = size.maxDimension * 0.55f
        val eased = 1f - (1f - age) * (1f - age) // ease-out
        val radius = eased * maxR
        val alpha = (1f - age) * 0.5f
        // Two concentric rings for a richer wave.
        drawCircle(
            color = palette.wave.copy(alpha = alpha),
            radius = radius,
            center = r.center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
        )
        drawCircle(
            color = palette.wave.copy(alpha = alpha * 0.5f),
            radius = radius * 0.72f,
            center = r.center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
        )
        // Faint fill flash at birth.
        if (age < 0.25f) {
            drawCircle(
                color = palette.wave.copy(alpha = (0.18f * (1f - age / 0.25f))),
                radius = radius,
                center = r.center,
            )
        }
    }
}

/** True when the AGSL RuntimeShader path is available (Android 13+). */
val agslSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
