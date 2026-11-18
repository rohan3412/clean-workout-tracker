package clean.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import clean.core.ACCENT_CHOICES

/** The accent colours on offer: the shade used on a dark background, then the one used on a light background. Blue is first and the default. Both shades keep 4.5 : 1 against their page. */
val ACCENTS: Map<String, Pair<Color, Color>> = linkedMapOf(
    "Blue" to (Color(0xFF8AB4FF) to Color(0xFF2456B8)),
    "Teal" to (Color(0xFF5FD4D0) to Color(0xFF00746F)),
    "Green" to (Color(0xFF6FD39A) to Color(0xFF1E7A46)),
    "Orange" to (Color(0xFFFFB068) to Color(0xFFA65200)),
    "Pink" to (Color(0xFFFF9EC4) to Color(0xFFB2306B)),
    "Purple" to (Color(0xFFC4A5FF) to Color(0xFF6A3FC0)),
).also { check(it.keys.toList() == ACCENT_CHOICES) }

/**
 * What the screens read: every colour and switch of the current look, already resolved. Plain data. The Material colour scheme is built
 * from it (`Look.colorScheme()` in Theme.kt). Since 1.1.0 a look is only Dark or Light, the Pure black switch (Dark) and one accent.
 */
data class Look(
    val dark: Boolean,
    /** Whether the page is black (Dark only): the old AMOLED. */
    val pureBlack: Boolean,
    /** The accent's name (one of `ACCENTS`). */
    val accentName: String,
    /** The page, the raised surfaces under cards and boxes, and the colour of text and of quiet text. */
    val page: Color,
    val raised: Color,
    val higher: Color,
    val text: Color,
    val muted: Color,
    /** The accent on a dark background, then on a light one. */
    val accent: Pair<Color, Color>,
    /** The calendar, body figure and chart shading, least to most: shades of the accent from dim to bright. */
    val scale: List<Color>,
    /** Whether the meaning colours are the blue-to-orange set (kv `theme.cbsafe`). */
    val colourBlindSafe: Boolean = false,
)

/** The accent as it is drawn on this look's page. */
val Look.orange: Color get() = if (dark) accent.first else accent.second

/** The colour scale of an accent: four steps from dim to bright, from the page (Dark) or from white (Light) up to the accent itself (B3). */
fun accentScale(accent: Pair<Color, Color>, dark: Boolean, page: Color): List<Color> =
    if (dark) listOf(0.28f, 0.5f, 0.75f, 1f).map { lerp(page, accent.first, it) }
    else listOf(0.3f, 0.5f, 0.75f, 1f).map { lerp(Color.White, accent.second, it) }

/**
 * The look for a stored choice: [theme] is `dark`, `light` or `phone` (follows [systemDark]); [pureBlack] only matters in Dark; [accent] is a
 * name in `ACCENTS` (anything else is Blue). The Dark page is a near-black slate with cards one step lighter; with Pure black it is black
 * and the cards sit one step lower. The Light page is a light grey under white cards.
 */
fun resolveLook(theme: String = "dark", pureBlack: Boolean = false, accent: String = "Blue", systemDark: Boolean = false, colourBlindSafe: Boolean = false): Look {
    val dark = when (theme) { "light" -> false; "phone" -> systemDark; else -> true }
    val name = if (accent in ACCENTS) accent else "Blue"
    val colours = ACCENTS.getValue(name)
    val black = dark && pureBlack
    val page = when { !dark -> Color(0xFFF3F4F6); black -> Color.Black; else -> Color(0xFF121316) }
    val raised = when { !dark -> Color.White; black -> Color(0xFF121316); else -> Color(0xFF1C1E24) }
    val higher = when { !dark -> Color(0xFFE6E8EF); black -> Color(0xFF1C1E24); else -> Color(0xFF2A2C33) }
    return Look(
        dark = dark, pureBlack = black, accentName = name, page = page, raised = raised, higher = higher,
        text = if (dark) Color(0xFFF2F3F5) else Color(0xFF121212), muted = if (dark) Color(0xFFA0A4AE) else Color(0xFF6A6A66),
        accent = colours, scale = accentScale(colours, dark, page), colourBlindSafe = colourBlindSafe,
    )
}

/** What the shared pieces read: the card, the button, the charts and the calendar all follow it. */
val LocalLook = staticCompositionLocalOf { resolveLook() }
