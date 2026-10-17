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

    val Dracula = AbyssPalette(
        id = "dracula", displayName = "Dracula", dark = true,
        background = Color(0xFF282A36), surface = Color(0xFF21222C), surfaceVariant = Color(0xFF44475A),
        primary = Color(0xFFBD93F9), secondary = Color(0xFFFF79C6),
        onBackground = Color(0xFFF8F8F2), onSurface = Color(0xFFE6E6E0), onPrimary = Color(0xFF21222C),
        outline = Color(0xFF44475A), userBubble = Color(0xFF44475A), assistantBubble = Color(0xFF282A36),
        symbol = Color(0xFF6272A4), wave = Color(0xFFBD93F9),
    )
    val Everforest = AbyssPalette(
        id = "everforest", displayName = "Everforest", dark = true,
        background = Color(0xFF2B3339), surface = Color(0xFF272E33), surfaceVariant = Color(0xFF3A454A),
        primary = Color(0xFFA7C080), secondary = Color(0xFF83C092),
        onBackground = Color(0xFFD3C6AA), onSurface = Color(0xFFC0B6A0), onPrimary = Color(0xFF272E33),
        outline = Color(0xFF4A555B), userBubble = Color(0xFF3A454A), assistantBubble = Color(0xFF2B3339),
        symbol = Color(0xFF5C6A72), wave = Color(0xFFA7C080),
    )
    val Kanagawa = AbyssPalette(
        id = "kanagawa", displayName = "Kanagawa", dark = true,
        background = Color(0xFF1F1F28), surface = Color(0xFF16161D), surfaceVariant = Color(0xFF2A2A37),
        primary = Color(0xFF7E9CD8), secondary = Color(0xFF957FB8),
        onBackground = Color(0xFFDCD7BA), onSurface = Color(0xFFC8C093), onPrimary = Color(0xFF16161D),
        outline = Color(0xFF363646), userBubble = Color(0xFF2A2A37), assistantBubble = Color(0xFF1F1F28),
        symbol = Color(0xFF54546D), wave = Color(0xFF7E9CD8),
    )
    val Solarized = AbyssPalette(
        id = "solarized", displayName = "Solarized Dark", dark = true,
        background = Color(0xFF002B36), surface = Color(0xFF073642), surfaceVariant = Color(0xFF0A4652),
        primary = Color(0xFF268BD2), secondary = Color(0xFF2AA198),
        onBackground = Color(0xFF93A1A1), onSurface = Color(0xFF839496), onPrimary = Color(0xFF002B36),
        outline = Color(0xFF0A4652), userBubble = Color(0xFF073642), assistantBubble = Color(0xFF002B36),
        symbol = Color(0xFF586E75), wave = Color(0xFF268BD2),
    )
    val Macchiato = AbyssPalette(
        id = "macchiato", displayName = "Catppuccin Macchiato", dark = true,
        background = Color(0xFF24273A), surface = Color(0xFF1E2030), surfaceVariant = Color(0xFF363A4F),
        primary = Color(0xFF8AADF4), secondary = Color(0xFFC6A0F6),
        onBackground = Color(0xFFCAD3F5), onSurface = Color(0xFFB8C0E0), onPrimary = Color(0xFF1E2030),
        outline = Color(0xFF494D64), userBubble = Color(0xFF363A4F), assistantBubble = Color(0xFF24273A),
        symbol = Color(0xFF6E738D), wave = Color(0xFF8AADF4),
    )
    val Latte = AbyssPalette(
        id = "latte", displayName = "Catppuccin Latte", dark = false,
        background = Color(0xFFEFF1F5), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE6E9EF),
        primary = Color(0xFF1E66F5), secondary = Color(0xFF8839EF),
        onBackground = Color(0xFF4C4F69), onSurface = Color(0xFF5C5F77), onPrimary = Color(0xFFFFFFFF),
        outline = Color(0xFFCCD0DA), userBubble = Color(0xFFDCE0E8), assistantBubble = Color(0xFFEFF1F5),
        symbol = Color(0xFF9CA0B0), wave = Color(0xFF1E66F5),
    )
    val Onedark = AbyssPalette(
        id = "onedark", displayName = "One Dark", dark = true,
        background = Color(0xFF282C34), surface = Color(0xFF21252B), surfaceVariant = Color(0xFF3B4048),
        primary = Color(0xFF61AFEF), secondary = Color(0xFFC678DD),
        onBackground = Color(0xFFABB2BF), onSurface = Color(0xFF9DA5B4), onPrimary = Color(0xFF21252B),
        outline = Color(0xFF3B4048), userBubble = Color(0xFF3B4048), assistantBubble = Color(0xFF282C34),
        symbol = Color(0xFF5C6370), wave = Color(0xFF61AFEF),
    )
    val Monokai = AbyssPalette(
        id = "monokai", displayName = "Monokai Pro", dark = true,
        background = Color(0xFF2D2A2E), surface = Color(0xFF221F22), surfaceVariant = Color(0xFF403E41),
        primary = Color(0xFFFFD866), secondary = Color(0xFFFC9867),
        onBackground = Color(0xFFFCFCFA), onSurface = Color(0xFFE3E1DE), onPrimary = Color(0xFF221F22),
        outline = Color(0xFF5B595C), userBubble = Color(0xFF403E41), assistantBubble = Color(0xFF2D2A2E),
        symbol = Color(0xFF727072), wave = Color(0xFFFF6188),
    )
    val Gruvboxlight = AbyssPalette(
        id = "gruvboxlight", displayName = "Gruvbox Light", dark = false,
        background = Color(0xFFFBF1C7), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEBDBB2),
        primary = Color(0xFFAF3A03), secondary = Color(0xFF427B58),
        onBackground = Color(0xFF3C3836), onSurface = Color(0xFF504945), onPrimary = Color(0xFFFFFFFF),
        outline = Color(0xFFD5C4A1), userBubble = Color(0xFFEBDBB2), assistantBubble = Color(0xFFFBF1C7),
        symbol = Color(0xFFBDAE93), wave = Color(0xFFAF3A03),
    )
    val Carbonfox = AbyssPalette(
        id = "carbonfox", displayName = "Carbonfox", dark = true,
        background = Color(0xFF161616), surface = Color(0xFF0C0C0C), surfaceVariant = Color(0xFF252525),
        primary = Color(0xFF78A9FF), secondary = Color(0xFFBE95FF),
        onBackground = Color(0xFFF2F4F8), onSurface = Color(0xFFDDE1E6), onPrimary = Color(0xFF0C0C0C),
        outline = Color(0xFF353535), userBubble = Color(0xFF252525), assistantBubble = Color(0xFF161616),
        symbol = Color(0xFF525252), wave = Color(0xFF78A9FF),
    )
    val Oceanic = AbyssPalette(
        id = "oceanic", displayName = "Oceanic", dark = true,
        background = Color(0xFF0F111A), surface = Color(0xFF090B10), surfaceVariant = Color(0xFF1F2233),
        primary = Color(0xFF82AAFF), secondary = Color(0xFFC792EA),
        onBackground = Color(0xFFA6ACCD), onSurface = Color(0xFF8F96B3), onPrimary = Color(0xFF090B10),
        outline = Color(0xFF2A2F45), userBubble = Color(0xFF1F2233), assistantBubble = Color(0xFF0F111A),
        symbol = Color(0xFF4B526D), wave = Color(0xFF82AAFF),
    )
    val Ayu = AbyssPalette(
        id = "ayu", displayName = "Ayu Dark", dark = true,
        background = Color(0xFF0B0E14), surface = Color(0xFF0D1017), surfaceVariant = Color(0xFF1C2230),
        primary = Color(0xFFFFB454), secondary = Color(0xFF59C2FF),
        onBackground = Color(0xFFBFBDB6), onSurface = Color(0xFFACA9A1), onPrimary = Color(0xFF0D1017),
        outline = Color(0xFF1C2230), userBubble = Color(0xFF1C2230), assistantBubble = Color(0xFF0B0E14),
        symbol = Color(0xFF565B66), wave = Color(0xFFFFB454),
    )
    val Ayumirage = AbyssPalette(
        id = "ayumirage", displayName = "Ayu Mirage", dark = true,
        background = Color(0xFF1F2430), surface = Color(0xFF191E2A), surfaceVariant = Color(0xFF2D3443),
        primary = Color(0xFFFFCC66), secondary = Color(0xFF73D0FF),
        onBackground = Color(0xFFCBCCC6), onSurface = Color(0xFFB8B9B3), onPrimary = Color(0xFF191E2A),
        outline = Color(0xFF343B49), userBubble = Color(0xFF2D3443), assistantBubble = Color(0xFF1F2430),
        symbol = Color(0xFF5C6773), wave = Color(0xFFFFCC66),
    )
    val Tokyostorm = AbyssPalette(
        id = "tokyostorm", displayName = "Tokyo Night Storm", dark = true,
        background = Color(0xFF24283B), surface = Color(0xFF1F2335), surfaceVariant = Color(0xFF2E3550),
        primary = Color(0xFF7AA2F7), secondary = Color(0xFFBB9AF7),
        onBackground = Color(0xFFC0CAF5), onSurface = Color(0xFFA9B1D6), onPrimary = Color(0xFF1F2335),
        outline = Color(0xFF3B4261), userBubble = Color(0xFF2E3550), assistantBubble = Color(0xFF24283B),
        symbol = Color(0xFF565F89), wave = Color(0xFF7AA2F7),
    )
    val Rosepinemoon = AbyssPalette(
        id = "rosepinemoon", displayName = "Rosé Pine Moon", dark = true,
        background = Color(0xFF232136), surface = Color(0xFF2A273F), surfaceVariant = Color(0xFF393552),
        primary = Color(0xFFEA9A97), secondary = Color(0xFFC4A7E7),
        onBackground = Color(0xFFE0DEF4), onSurface = Color(0xFFCCC9DC), onPrimary = Color(0xFF232136),
        outline = Color(0xFF44415A), userBubble = Color(0xFF393552), assistantBubble = Color(0xFF232136),
        symbol = Color(0xFF56526E), wave = Color(0xFFEA9A97),
    )

    val all: List<AbyssPalette> = listOf(
        Abyss, Ember, Ivory, Crimson, CatppuccinMocha, TokyoNight, Nord, RosePine, Gruvbox, Daylight,
        Dracula, Everforest, Kanagawa, Solarized, Macchiato, Latte, Onedark, Monokai, Gruvboxlight, Carbonfox, Oceanic, Ayu, Ayumirage, Tokyostorm, Rosepinemoon,
    )

    val default: AbyssPalette = Abyss

    fun byId(id: String?): AbyssPalette = all.firstOrNull { it.id == id } ?: default
}
