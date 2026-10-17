package com.abyss.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.abyss.app.ui.theme.AbyssPalette

/**
 * ~0.85s launch intro that continues the system splash: the black hole grows
 * in with a fast-spinning disk, then collapses into a point — at that moment
 * [onCollapse] fires (the app sends a wave out from the centre) and the
 * overlay dissolves to reveal the UI.
 *
 * All motion is read inside graphicsLayer, so it never recomposes per frame.
 */
@Composable
fun AbyssIntro(palette: AbyssPalette, onCollapse: () -> Unit, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // Gravitational collapse: a slow swell, then an accelerating plunge into
        // a point — eased so the two phases meet with matched velocity (no jolt).
        p.animateTo(0.55f, tween(560, easing = FastOutSlowInEasing))
        onCollapse()
        p.animateTo(1f, tween(520, easing = IntoPoint))
        onDone()
    }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                val v = p.value
                // Hold opaque through the swell, then dissolve smoothstep'd so the
                // UI bleeds through exactly as the mark reaches the singularity.
                val d = ((v - 0.55f) / 0.45f).coerceIn(0f, 1f)
                alpha = 1f - d * d * (3f - 2f * d)
            }
            .background(palette.background)
            .blockTaps(),
        contentAlignment = Alignment.Center,
    ) {
        AbyssMark(
            palette, size = 132.dp, speedMs = 1100, pulse = true, detailed = true,
            modifier = Modifier.graphicsLayer {
                val v = p.value
                val s: Float; val spin: Float
                if (v < 0.55f) {
                    val a = v / 0.55f
                    s = 0.80f + 0.30f * (a * a * (3f - 2f * a))   // ease-in swell to 1.10×
                    spin = 0f
                } else {
                    val b = (v - 0.55f) / 0.45f
                    val e = b * b                                 // accelerate inward
                    s = 1.10f * (1f - e)                          // collapse to a point
                    spin = -70f * e                               // whirl as it falls in
                }
                scaleX = s; scaleY = s
                rotationZ = spin
            },
        )
    }
}

/** Accelerating plunge: slow lip, fast fall into the singularity. */
private val IntoPoint = CubicBezierEasing(0.6f, 0f, 0.9f, 0.25f)
