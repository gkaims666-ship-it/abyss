package com.abyss.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Access the full Abyss palette anywhere (the shader background needs it). */
val LocalAbyssPalette = staticCompositionLocalOf { Palettes.default }

private fun AbyssPalette.toColorScheme() = if (dark) {
    darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        outline = outline,
        error = error,
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        secondary = secondary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        outline = outline,
        error = error,
    )
}

private val AbyssType = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
)

@Composable
fun AbyssTheme(palette: AbyssPalette, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAbyssPalette provides palette) {
        MaterialTheme(
            colorScheme = palette.toColorScheme(),
            typography = AbyssType,
            content = content,
        )
    }
}
