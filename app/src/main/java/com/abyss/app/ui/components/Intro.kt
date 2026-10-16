package com.abyss.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
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
        p.animateTo(0.55f, tween(470, easing = FastOutSlowInEasing))
        onCollapse()
        p.animateTo(1f, tween(380, easing = FastOutLinearInEasing))
        onDone()
    }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                val v = p.value
                alpha = if (v < 0.55f) 1f else (1f - (v - 0.55f) / 0.45f).coerceIn(0f, 1f)
            }
            .background(palette.background)
            .blockTaps(),
        contentAlignment = Alignment.Center,
    ) {
        AbyssMark(
            palette, size = 132.dp, speedMs = 1100, pulse = true, detailed = true,
            modifier = Modifier.graphicsLayer {
                val v = p.value
                val s = if (v < 0.55f) 0.86f + 0.22f * (v / 0.55f)          // grow in
                else 1.08f * (1f - (v - 0.55f) / 0.45f).coerceIn(0f, 1f)  // collapse to a point
                scaleX = s; scaleY = s
                rotationZ = if (v < 0.55f) 0f else -40f * ((v - 0.55f) / 0.45f)
            },
        )
    }
}
