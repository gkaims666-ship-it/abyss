package com.abyss.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A full color palette for Abyss. Every screen, the shader background and the
 * accents all read from one of these, so switching a palette restyles the
 * whole app at once.
 *
 * [symbol] and [wave] feed the AGSL background shader; the rest drive Compose.
 */
data class AbyssPalette(
    val id: String,
    val displayName: String,
    val dark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,     // main accent (buttons, send, highlights)
    val secondary: Color,   // secondary accent
    val onBackground: Color,
    val onSurface: Color,
    val onPrimary: Color,
    val outline: Color,
    val userBubble: Color,
    val assistantBubble: Color,
    val symbol: Color,      // drifting glyphs in the background
    val wave: Color,        // touch ripple color
    val error: Color = Color(0xFFFF5C6C),
)

object Palettes {

    /** Default: warm charcoal, cream type, terracotta accent. */
    val Ember = AbyssPalette(
        id = "ember",
        displayName = "Ember",
        dark = true,
        background = Color(0xFF1F1E1D),
        surface = Color(0xFF262624),
        surfaceVariant = Color(0xFF30302E),
        primary = Color(0xFFD97757),
        secondary = Color(0xFFE3A587),
        onBackground = Color(0xFFFAF9F5),
        onSurface = Color(0xFFE8E6DF),
        onPrimary = Color(0xFF1F1E1D),
        outline = Color(0xFF3E3D39),
        userBubble = Color(0xFF30302E),
        assistantBubble = Color(0x00000000),
        symbol = Color(0xFF6B6A64),
        wave = Color(0xFFD97757),
        error = Color(0xFFE5675B),
    )

    /** Light counterpart: ivory paper, near-black ink, same terracotta. */
    val Ivory = AbyssPalette(
        id = "ivory",
        displayName = "Ivory",
        dark = false,
        background = Color(0xFFFAF9F5),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF0EEE6),
        primary = Color(0xFFC96442),
        secondary = Color(0xFFD97757),
        onBackground = Color(0xFF141413),
        onSurface = Color(0xFF1F1E1D),
        onPrimary = Color(0xFFFFFFFF),
        outline = Color(0xFFE3E0D6),
        userBubble = Color(0xFFF0EEE6),
        assistantBubble = Color(0x00000000),
        symbol = Color(0xFFC9C5B8),
        wave = Color(0xFFC96442),
        error = Color(0xFFC0392B),
    )

    /** The original look: deep near-black, cyan/blue glow, drifting glyphs. */
    val Abyss = AbyssPalette(
        id = "abyss",
        displayName = "Abyss",
        dark = true,
        background = Color(0xFF05070D),
        surface = Color(0xFF0B0F1A),
        surfaceVariant = Color(0xFF131A2B),
        primary = Color(0xFF53D7FF),
        secondary = Color(0xFF7C8CFF),
        onBackground = Color(0xFFE6ECF5),
        onSurface = Color(0xFFD5DEEC),
        onPrimary = Color(0xFF04121A),
        outline = Color(0xFF243049),
        userBubble = Color(0xFF16233B),
        assistantBubble = Color(0xFF0D1422),
        symbol = Color(0xFF2E6C8C),
        wave = Color(0xFF53D7FF),
    )

    /** Aggressive black / red / white. */
    val Crimson = AbyssPalette(
        id = "crimson",
        displayName = "Crimson",
        dark = true,
        background = Color(0xFF060606),
        surface = Color(0xFF0E0E0E),
        surfaceVariant = Color(0xFF1A1111),
        primary = Color(0xFFFF2032),
        secondary = Color(0xFFFF5B5B),
        onBackground = Color(0xFFF5F5F5),
        onSurface = Color(0xFFEDEDED),
        onPrimary = Color(0xFFFFFFFF),
        outline = Color(0xFF3A1414),
        userBubble = Color(0xFF240A0C),
        assistantBubble = Color(0xFF121212),
        symbol = Color(0xFF7A1018),
        wave = Color(0xFFFF2032),
        error = Color(0xFFFF2032),
    )

    val CatppuccinMocha = AbyssPalette(
        id = "catppuccin",
        displayName = "Catppuccin Mocha",
        dark = true,
        background = Color(0xFF1E1E2E),
        surface = Color(0xFF181825),
        surfaceVariant = Color(0xFF313244),
        primary = Color(0xFFCBA6F7),
        secondary = Color(0xFF89DCEB),
        onBackground = Color(0xFFCDD6F4),
        onSurface = Color(0xFFBAC2DE),
        onPrimary = Color(0xFF11111B),
        outline = Color(0xFF45475A),
        userBubble = Color(0xFF302D41),
        assistantBubble = Color(0xFF1E1E2E),
        symbol = Color(0xFF6C7086),
        wave = Color(0xFFCBA6F7),
    )

    val TokyoNight = AbyssPalette(
        id = "tokyonight",
        displayName = "Tokyo Night",
        dark = true,
        background = Color(0xFF1A1B26),
        surface = Color(0xFF16161E),
        surfaceVariant = Color(0xFF24283B),
        primary = Color(0xFF7AA2F7),
        secondary = Color(0xFFBB9AF7),
        onBackground = Color(0xFFC0CAF5),
        onSurface = Color(0xFFA9B1D6),
        onPrimary = Color(0xFF0B0C14),
        outline = Color(0xFF2A2E42),
        userBubble = Color(0xFF24283B),
        assistantBubble = Color(0xFF1A1B26),
        symbol = Color(0xFF3B4261),
        wave = Color(0xFF7AA2F7),
    )

    val Nord = AbyssPalette(
        id = "nord",
        displayName = "Nord",
        dark = true,
        background = Color(0xFF2E3440),
        surface = Color(0xFF272B35),
        surfaceVariant = Color(0xFF3B4252),
        primary = Color(0xFF88C0D0),
        secondary = Color(0xFF81A1C1),
        onBackground = Color(0xFFECEFF4),
        onSurface = Color(0xFFD8DEE9),
        onPrimary = Color(0xFF1C2028),
        outline = Color(0xFF434C5E),
        userBubble = Color(0xFF3B4252),
        assistantBubble = Color(0xFF2E3440),
        symbol = Color(0xFF4C566A),
        wave = Color(0xFF88C0D0),
    )

    val RosePine = AbyssPalette(
        id = "rosepine",
        displayName = "Rosé Pine",
        dark = true,
        background = Color(0xFF191724),
        surface = Color(0xFF1F1D2E),
        surfaceVariant = Color(0xFF26233A),
        primary = Color(0xFFEBBCBA),
        secondary = Color(0xFFC4A7E7),
        onBackground = Color(0xFFE0DEF4),
        onSurface = Color(0xFFCCC9DC),
        onPrimary = Color(0xFF1A141F),
        outline = Color(0xFF403D52),
        userBubble = Color(0xFF26233A),
        assistantBubble = Color(0xFF191724),
        symbol = Color(0xFF524F67),
        wave = Color(0xFFEBBCBA),
    )

    val Gruvbox = AbyssPalette(
        id = "gruvbox",
        displayName = "Gruvbox",
        dark = true,
        background = Color(0xFF282828),
        surface = Color(0xFF1D2021),
        surfaceVariant = Color(0xFF3C3836),
        primary = Color(0xFFFABD2F),
        secondary = Color(0xFF83A598),
        onBackground = Color(0xFFEBDBB2),
        onSurface = Color(0xFFD5C4A1),
        onPrimary = Color(0xFF1D2021),
        outline = Color(0xFF504945),
        userBubble = Color(0xFF3C3836),
        assistantBubble = Color(0xFF282828),
        symbol = Color(0xFF665C54),
        wave = Color(0xFFFABD2F),
    )

    /** Light option so the switcher isn't all-dark. */
    val Daylight = AbyssPalette(
        id = "daylight",
        displayName = "Daylight",
        dark = false,
        background = Color(0xFFF4F6FB),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE6EAF2),
        primary = Color(0xFF1E6FFF),
        secondary = Color(0xFF6C4BFF),
        onBackground = Color(0xFF111622),
        onSurface = Color(0xFF1C2433),
        onPrimary = Color(0xFFFFFFFF),
        outline = Color(0xFFC6CEDC),
        userBubble = Color(0xFFDCE6FF),
        assistantBubble = Color(0xFFFFFFFF),
        symbol = Color(0xFFB9C4D8),
        wave = Color(0xFF1E6FFF),
    )

    val all: List<AbyssPalette> = listOf(
        Ember, Ivory, Abyss, Crimson, CatppuccinMocha, TokyoNight, Nord, RosePine, Gruvbox, Daylight,
    )

    val default: AbyssPalette = Ember

    fun byId(id: String?): AbyssPalette = all.firstOrNull { it.id == id } ?: default
}
