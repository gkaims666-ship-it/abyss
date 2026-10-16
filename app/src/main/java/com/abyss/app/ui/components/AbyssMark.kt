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
import androidx.compose.ui.graphics.StrokeCap
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
 *
 * At large sizes ([detailed], default when size ≥ 64dp) it adds the cinematic
 * details that only read when big: gravitational lensing (the far side of the
 * disk bent over and under the horizon), Doppler beaming (one side brighter),
 * a second inner disk band and orbiting specks. Everything is vector-drawn, so
 * it stays sharp at any resolution.
 *
 * [pulse] makes the glow breathe faster (thinking / loading).
 */
@Composable
fun AbyssMark(
    palette: AbyssPalette,
    size: Dp = 28.dp,
    modifier: Modifier = Modifier,
    speedMs: Int = 6000,
    pulse: Boolean = false,
    animated: Boolean = true,
    detailed: Boolean = size >= 64.dp,
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
    val core = if (palette.dark) Color(0xFF020306) else Color(0xFF141413)

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

        // Halo (layered when detailed for a softer falloff).
        drawCircle(
            brush = Brush.radialGradient(
                listOf(palette.primary.copy(alpha = 0.32f * glow), Color.Transparent),
                center = c, radius = w * 0.5f),
            radius = w * 0.5f, center = c,
        )
        if (detailed) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(palette.secondary.copy(alpha = 0.18f * glow), Color.Transparent),
                    center = c, radius = w * 0.36f),
                radius = w * 0.36f, center = c,
            )
        }

        // Back half of the disk (+ inner band when detailed).
        rotate(-22f, c) {
            drawOval(
                brush = beamingBrush(palette, glow, c.x - diskW / 2f, c.x + diskW / 2f),
                topLeft = Offset(c.x - diskW / 2f, c.y - diskH / 2f),
                size = Size(diskW, diskH),
                style = Stroke(width = stroke),
            )
            if (detailed) {
                val iw = diskW * 0.72f
                val ih = diskH * 0.62f
                drawOval(
                    color = palette.secondary.copy(alpha = 0.45f * glow),
                    topLeft = Offset(c.x - iw / 2f, c.y - ih / 2f),
                    size = Size(iw, ih),
                    style = Stroke(width = stroke * 0.45f),
                )
                specks(c, diskW, diskH, angle, palette, glow, front = false)
            }
            if (!spotInFront) hotSpot(spot, w, palette, glow)
        }

        // Gravitational lensing: the far side of the disk, bent over the top
        // and (fainter) under the bottom of the horizon. Drawn upright.
        if (detailed) {
            val lr = horizon * 1.42f
            drawArc(
                brush = Brush.horizontalGradient(
                    listOf(palette.secondary.copy(alpha = 0.15f), palette.primary.copy(alpha = 0.85f * glow),
                        palette.secondary.copy(alpha = 0.15f)),
                    startX = c.x - lr, endX = c.x + lr),
                startAngle = 196f, sweepAngle = 148f, useCenter = false,
                topLeft = Offset(c.x - lr, c.y - lr * 0.92f),
                size = Size(lr * 2f, lr * 1.84f),
                style = Stroke(width = stroke * 0.7f, cap = StrokeCap.Round),
            )
            drawArc(
                color = palette.primary.copy(alpha = 0.35f * glow),
                startAngle = 22f, sweepAngle = 136f, useCenter = false,
                topLeft = Offset(c.x - lr * 0.9f, c.y - lr * 0.8f),
                size = Size(lr * 1.8f, lr * 1.6f),
                style = Stroke(width = stroke * 0.4f, cap = StrokeCap.Round),
            )
        }

        // Event horizon + photon ring.
        drawCircle(color = core, radius = horizon, center = c)
        drawCircle(
            color = palette.secondary.copy(alpha = 0.9f * glow),
            radius = horizon * 1.07f, center = c, style = Stroke(width = w * 0.028f),
        )
        if (detailed) {
            drawCircle(
                color = Color.White.copy(alpha = 0.35f * glow),
                radius = horizon * 1.07f, center = c, style = Stroke(width = w * 0.008f),
            )
        }

        // Front half of the disk crosses over the horizon.
        rotate(-22f, c) {
            drawArc(
                brush = beamingBrush(palette, glow, c.x - diskW / 2f, c.x + diskW / 2f),
                startAngle = 0f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(c.x - diskW / 2f, c.y - diskH / 2f),
                size = Size(diskW, diskH),
                style = Stroke(width = stroke),
            )
            if (detailed) specks(c, diskW, diskH, angle, palette, glow, front = true)
            if (spotInFront) hotSpot(spot, w, palette, glow)
        }
    }
}

/** Doppler beaming: the approaching (left) side of the disk is brighter. */
private fun beamingBrush(palette: AbyssPalette, glow: Float, x0: Float, x1: Float): Brush =
    Brush.horizontalGradient(
        listOf(
            Color.White.copy(alpha = 0.85f * glow),
            palette.primary.copy(alpha = glow),
            palette.secondary.copy(alpha = 0.55f * glow),
            palette.secondary.copy(alpha = 0.30f * glow),
        ),
        startX = x0, endX = x1,
    )

private fun DrawScope.hotSpot(at: Offset, w: Float, palette: AbyssPalette, glow: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.9f * glow), palette.primary.copy(alpha = 0.0f)),
            center = at, radius = w * 0.12f),
        radius = w * 0.12f, center = at,
    )
}

/** Small specks orbiting on the disk (only the half requested). */
private fun DrawScope.specks(
    c: Offset, diskW: Float, diskH: Float, angle: Float,
    palette: AbyssPalette, glow: Float, front: Boolean,
) {
    val offsets = floatArrayOf(0f, 61f, 137f, 199f, 263f, 318f)
    val scales = floatArrayOf(1f, 0.82f, 0.9f, 0.76f, 0.95f, 0.86f)
    for (i in offsets.indices) {
        val a = Math.toRadians((angle * (1f + i * 0.07f) + offsets[i]).toDouble())
        val s = sin(a)
        if ((s > 0) != front) continue
        val rx = diskW / 2f * scales[i]
        val ry = diskH / 2f * scales[i]
        val p = Offset(c.x + rx * cos(a).toFloat(), c.y + ry * s.toFloat())
        drawCircle(color = Color.White.copy(alpha = 0.75f * glow), radius = diskW * 0.009f, center = p)
        drawCircle(color = palette.primary.copy(alpha = 0.3f * glow), radius = diskW * 0.022f, center = p)
    }
}
