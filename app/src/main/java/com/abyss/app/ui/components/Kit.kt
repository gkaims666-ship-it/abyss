package com.abyss.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abyss.app.ui.theme.AbyssFonts
import com.abyss.app.ui.theme.AbyssPalette
import kotlinx.coroutines.delay

/**
 * Swallows taps that land on this element itself, so a sheet sitting on a
 * tap-to-dismiss scrim doesn't close when you touch its body. Children still
 * receive their own clicks first.
 */
fun Modifier.blockTaps(): Modifier = pointerInput(Unit) { detectTapGestures { } }

/** Large editorial screen title with optional muted subtitle. */
@Composable
fun ScreenTitle(title: String, palette: AbyssPalette, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(bottom = 14.dp)) {
        Text(title, color = palette.onBackground, fontFamily = AbyssFonts.Display, fontSize = 30.sp, lineHeight = 36.sp)
        if (subtitle != null) {
            Spacer(Modifier.size(4.dp))
            Text(subtitle, color = palette.onSurface.copy(alpha = 0.55f), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

/** Small muted section label (letter-spaced caps). */
@Composable
fun SectionLabel(text: String, palette: AbyssPalette, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), color = palette.onSurface.copy(alpha = 0.5f),
        fontSize = 11.5.sp, letterSpacing = 1.4.sp, fontFamily = AbyssFonts.Ui,
        modifier = modifier.padding(top = 22.dp, bottom = 10.dp, start = 2.dp),
    )
}

/** Back affordance + serif title, used by nested screens (tools, permissions). */
@Composable
fun BackBar(title: String, palette: AbyssPalette, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, com.abyss.app.ui.i18n.LocalStrings.current.back, tint = palette.onBackground.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(title, color = palette.onBackground, fontFamily = AbyssFonts.Display, fontSize = 24.sp)
    }
}

/** Fades + lifts content in once after [delayMs]. With [enabled]=false renders immediately. */
@Composable
fun Appear(delayMs: Int = 0, enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        p.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    Box(Modifier.graphicsLayer { alpha = p.value; translationY = (1f - p.value) * 18f }) { content() }
}
