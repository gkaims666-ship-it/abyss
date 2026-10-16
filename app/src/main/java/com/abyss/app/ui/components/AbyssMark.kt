package com.abyss.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abyss.app.ui.theme.AbyssPalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * Abyss's own mark: a stylised black hole — tilted accretion disk with a hot
 * spot orbiting it, a pure-dark event horizon and a thin photon ring.
 * [pulse] makes the glow breathe faster (used as the "thinking" indicator).
 */
@Composable
fun AbyssMark(
    palette: AbyssPalette,
    size: Dp = 28.dp,
    modifier: Modifier = Modifier,
    speedMs: Int = 6000,
    pulse: Boolean = false,
    animated: Boolean = true,
) {
    val angle: Float
    val glow: Float
    if (animated) {
        val tr = rememberInfiniteTransition(label = "mark")
        angle = tr.animateFloat(
            0f, 360f, infiniteRepeatable(tween(speedMs, easing = LinearEasing)), label = "angle").value
        glow = tr.animateFloat(
            if (pulse) 0.45f else 0.75f, 1f,
            infiniteRepeatable(tween(if (pulse) 650 else 2600), RepeatMode.Reverse), label = "glow").value
    } else {
        angle = 210f   // hot spot resting at the back-left, reads as a still frame
        glow = 0.9f
    }
    val core = if (palette.dark) Color(0xFF0B0A09) else Color(0xFF141413)

    Canvas(modifier.size(size)) {
        val w = this.size.width
        val c = Offset(w / 2f, w / 2f)
        val horizon = w * 0.25f
        val diskW = w * 0.94f
        val diskH = w * 0.34f
        val stroke = w * 0.07f
        val th = Math.toRadians(angle.toDouble())
        val spot = Offset(c.x + diskW / 2f * cos(th).toFloat(), c.y + diskH / 2f * sin(th).toFloat())
        val spotInFront = sin(th) > 0

        // Halo.
        drawCircle(
            brush = Brush.radialGradient(
                listOf(palette.primary.copy(alpha = 0.32f * glow), Color.Transparent),
                center = c, radius = w * 0.5f),
            radius = w * 0.5f, center = c,
        )
        // Back half of the disk.
        rotate(-22f, c) {
            drawOval(
                brush = Brush.horizontalGradient(
                    listOf(palette.secondary.copy(alpha = 0.55f * glow),
                        palette.primary.copy(alpha = glow),
                        palette.secondary.copy(alpha = 0.55f * glow)),
                ),
                topLeft = Offset(c.x - diskW / 2f, c.y - diskH / 2f),
                size = Size(diskW, diskH),
                style = Stroke(width = stroke),
            )
            if (!spotInFront) hotSpot(spot, w, palette, glow)
        }
        // Event horizon + photon ring.
        drawCircle(color = core, radius = horizon, center = c)
        drawCircle(
            color = palette.secondary.copy(alpha = 0.9f * glow),
            radius = horizon * 1.07f, center = c, style = Stroke(width = w * 0.028f),
        )
        // Front half of the disk crosses over the horizon.
        rotate(-22f, c) {
            drawArc(
                color = palette.primary.copy(alpha = glow),
                startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(c.x - diskW / 2f, c.y - diskH / 2f),
                size = Size(diskW, diskH),
                style = Stroke(width = stroke),
            )
            if (spotInFront) hotSpot(spot, w, palette, glow)
        }
    }
}

private fun DrawScope.hotSpot(at: Offset, w: Float, palette: AbyssPalette, glow: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.9f * glow), palette.primary.copy(alpha = 0.0f)),
            center = at, radius = w * 0.12f),
        radius = w * 0.12f, center = at,
    )
}
