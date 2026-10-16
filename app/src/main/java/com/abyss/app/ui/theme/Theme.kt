package com.abyss.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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

/**
 * Fills EVERY Material 3 role from the palette. Leaving roles unset makes
 * menus, switches, sliders and dialogs fall back to the stock purple-grey
 * baseline, which clashed with the app.
 */
private fun AbyssPalette.toColorScheme(): ColorScheme {
    val muted = onSurface.copy(alpha = 0.65f)
    val primaryTint = primary.copy(alpha = 0.22f).compositeOver(surface)
    val c1 = surface
    val c2 = surfaceVariant.copy(alpha = 0.6f).compositeOver(surface)
    val c3 = surfaceVariant
    return if (dark) darkColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryTint, onPrimaryContainer = onSurface,
        inversePrimary = primary,
        secondary = secondary, onSecondary = onPrimary,
        secondaryContainer = c3, onSecondaryContainer = onSurface,
        tertiary = secondary, onTertiary = onPrimary,
        tertiaryContainer = c3, onTertiaryContainer = onSurface,
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = muted,
        surfaceTint = Color.Transparent,
        inverseSurface = onSurface, inverseOnSurface = background,
        error = error, onError = onPrimary,
        errorContainer = error.copy(alpha = 0.2f).compositeOver(surface), onErrorContainer = onSurface,
        outline = outline, outlineVariant = outline,
        scrim = Color.Black,
        surfaceBright = c3, surfaceDim = background,
        surfaceContainerLowest = background, surfaceContainerLow = c1,
        surfaceContainer = c1, surfaceContainerHigh = c2, surfaceContainerHighest = c3,
    ) else lightColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryTint, onPrimaryContainer = onSurface,
        inversePrimary = primary,
        secondary = secondary, onSecondary = onPrimary,
        secondaryContainer = c3, onSecondaryContainer = onSurface,
        tertiary = secondary, onTertiary = onPrimary,
        tertiaryContainer = c3, onTertiaryContainer = onSurface,
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = muted,
        surfaceTint = Color.Transparent,
        inverseSurface = onSurface, inverseOnSurface = background,
        error = error, onError = onPrimary,
        errorContainer = error.copy(alpha = 0.15f).compositeOver(surface), onErrorContainer = onSurface,
        outline = outline, outlineVariant = outline,
        scrim = Color.Black,
        surfaceBright = surface, surfaceDim = c3,
        surfaceContainerLowest = surface, surfaceContainerLow = background,
        surfaceContainer = surface, surfaceContainerHigh = c2, surfaceContainerHighest = c3,
    )
}

/** Editorial serif for display + reading, clean sans for UI chrome. */
object AbyssFonts {
    val Display = FontFamily.Serif
    val Reading = FontFamily.Serif
    val Ui = FontFamily.SansSerif
    val Code = FontFamily.Monospace
}

private val AbyssType = Typography(
    displaySmall = TextStyle(fontFamily = AbyssFonts.Display, fontSize = 34.sp, lineHeight = 40.sp),
    headlineSmall = TextStyle(fontFamily = AbyssFonts.Display, fontSize = 24.sp, lineHeight = 30.sp),
    bodyLarge = TextStyle(fontFamily = AbyssFonts.Ui, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = AbyssFonts.Ui, fontSize = 14.sp, lineHeight = 20.sp),
    titleLarge = TextStyle(fontFamily = AbyssFonts.Ui, fontSize = 20.sp),
    labelLarge = TextStyle(fontFamily = AbyssFonts.Ui, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = AbyssFonts.Ui, fontSize = 11.sp),
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
