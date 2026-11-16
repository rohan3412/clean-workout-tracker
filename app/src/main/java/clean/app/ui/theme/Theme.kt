package clean.app.ui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import clean.app.R
import java.io.File

// Fixed colours rather than the phone's wallpaper colours, so the app looks the same everywhere.
private val Dark = darkColorScheme(
    primary = Color(0xFF8AB4FF),
    onPrimary = Color(0xFF06214A),
    primaryContainer = Color(0xFF1D3A6B),
    onPrimaryContainer = Color(0xFFD8E5FF),
    tertiary = Color(0xFFFFC53D),
    onTertiary = Color(0xFF3A2A00),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F3F5),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF2F3F5),
    surfaceVariant = Color(0xFF1C1E24),
    onSurfaceVariant = Color(0xFFA0A4AE),
    surfaceContainer = Color(0xFF121316),
    surfaceContainerHigh = Color(0xFF1C1E24),
    surfaceContainerHighest = Color(0xFF1C1E24),
    surfaceContainerLow = Color(0xFF121316),
    outline = Color(0xFF6A6F7B),
    outlineVariant = Color(0xFF3A3D46),
    error = Color(0xFFFF7A70),
    onError = Color(0xFF3A0A08),
    errorContainer = lerp(Color.Black, Color(0xFFFF7A70), 0.24f),
    onErrorContainer = lerp(Color(0xFFFF7A70), Color.White, 0.6f),
    surfaceTint = Color(0xFF8AB4FF),
    inverseSurface = Color(0xFFF2F3F5),
    inverseOnSurface = Color(0xFF000000),
    inversePrimary = Color(0xFF2456B8),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2A2C33),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
)

private val Light = lightColorScheme(
    primary = Color(0xFF2456B8),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF16181D),
    onSurface = Color(0xFF16181D),
    onSurfaceVariant = Color(0xFF50545E),
    outline = Color(0xFF767B87),
    primaryContainer = Color(0xFFD8E5FF),
    onPrimaryContainer = Color(0xFF06214A),
    tertiary = Color(0xFF9A5B00),
    background = Color(0xFFF6F7FA),
    surface = Color(0xFFF6F7FA),
    surfaceVariant = Color(0xFFE6E8EF),
    surfaceContainer = Color(0xFFECEEF3),
    surfaceContainerHigh = Color(0xFFE6E8EF),
    surfaceContainerHighest = Color(0xFFE6E8EF),
    surfaceContainerLow = Color(0xFFECEEF3),
    outlineVariant = Color(0xFFCDD1DB),
    error = Color(0xFFC62F33),
    onError = Color.White,
    errorContainer = lerp(Color(0xFFF6F7FA), Color(0xFFC62F33), 0.16f),
    onErrorContainer = lerp(Color(0xFFC62F33), Color.Black, 0.5f),
    surfaceTint = Color(0xFF2456B8),
    inverseSurface = Color(0xFF16181D),
    inverseOnSurface = Color(0xFFF6F7FA),
    inversePrimary = Color(0xFF8AB4FF),
    scrim = Color.Black,
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFDDE0E7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

/**
 * How big the text is next to the size the app starts with (1 at Medium). Cards scale the air in
 * and around them by it, so a small text size gives a tight page and a large one a roomy page.
 */
val LocalTextScale = staticCompositionLocalOf { 1f }

/**
 * The colours that carry a meaning, the same wherever they appear: a ticked set and a PR, the set
 * types, and anything that deletes. Screens read them from here and type none of their own.
 */
class CleanColors(
    val done: Color, val warmup: Color, val failure: Color, val drop: Color, val danger: Color,
    /** Fatigue (and the BMI bands): ready / healthy, recovering / overweight, fatigued / obese, and strength at its full; underweight is BMI's alone. */
    val ready: Color, val recovering: Color, val fatigued: Color, val full: Color, val underweight: Color,
) {
    /** Every colour through [invertColour], for the negative look. */
    fun inverted(trueInvert: Boolean): CleanColors {
        fun c(x: Color) = invertColour(x, trueInvert)
        return CleanColors(c(done), c(warmup), c(failure), c(drop), c(danger), c(ready), c(recovering), c(fatigued), c(full), c(underweight))
    }
}

private val MeaningDark = CleanColors(
    done = Color(0xFF2FD3A6), warmup = Color(0xFFE0A030), failure = Color(0xFFE5534B), drop = Color(0xFF9B7BE8), danger = Color(0xFFE5484D),
    ready = Color(0xFF34B454), recovering = Color(0xFFF0B429), fatigued = Color(0xFFE5534B), full = Color(0xFFF0C419), underweight = Color(0xFF4A90D9),
)
// The light counterparts keep at least 4.5:1 against the light background, so they serve as text as well as graphics.
private val MeaningLight = CleanColors(
    done = Color(0xFF0E8F6C), warmup = Color(0xFFA86A00), failure = Color(0xFFC0352D), drop = Color(0xFF6A45C8), danger = Color(0xFFC62F33),
    ready = Color(0xFF1B7F37), recovering = Color(0xFF9A5F00), fatigued = Color(0xFFC0352D), full = Color(0xFF7D6200), underweight = Color(0xFF2A6AB5),
)

/**
 * The colour-blind-safe meaning colours (Okabe-Ito hues): blue to orange in place of green to red, so good and bad stay apart for deuteranopia and
 * protanopia. Ready and done are blue, recovering amber-orange, fatigued and failure vermillion, underweight (BMI) reddish purple so it differs from healthy.
 */
private val MeaningDarkSafe = CleanColors(
    done = Color(0xFF56B4E9), warmup = Color(0xFFE0A030), failure = Color(0xFFD55E00), drop = Color(0xFF9B7BE8), danger = Color(0xFFE5484D),
    ready = Color(0xFF56B4E9), recovering = Color(0xFFE69F00), fatigued = Color(0xFFD55E00), full = Color(0xFFF0E442), underweight = Color(0xFFCC79A7),
)
// Light values keep 4.5:1 against the light background, like [MeaningLight].
private val MeaningLightSafe = CleanColors(
    done = Color(0xFF0072B2), warmup = Color(0xFFA86A00), failure = Color(0xFFB84F00), drop = Color(0xFF6A45C8), danger = Color(0xFFC62F33),
    ready = Color(0xFF0072B2), recovering = Color(0xFF8A6400), fatigued = Color(0xFFB84F00), full = Color(0xFF7D6200), underweight = Color(0xFFA8497F),
)

/**
 * The meaning colours this look draws: Mono's greys (turned over when negative), else the green-to-red set or, with [Look.colourBlindSafe], the
 * blue-to-orange one, the light set turned over for the negative look. Mono ignores the colour-blind switch: its lightness carries the meaning.
 */
fun Look.meaning(): CleanColors {
    if (mono) {
        // Greys of the paper look, turned over with the rest when the look is negative.
        val paperLook = if (negative) negated() else this
        return monoMeaning(paperLook.page, paperLook.text).let { if (negative) it.inverted(trueInvert) else it }
    }
    val light = if (colourBlindSafe) MeaningLightSafe else MeaningLight
    val dark = if (colourBlindSafe) MeaningDarkSafe else MeaningDark
    val set = if (negative) light.inverted(trueInvert) else if (this.dark) dark else light
    // Linen's Strength view: "Full" is one step deeper on its orange ramp, not the gold, so the five steps read as one ramp.
    if (preset == "linen" && scaleName == "orange" && !negative) return set.withFull(LINEN_FULL)
    // Light's Strength view: "Full" is a near-black burnt orange past the ramp's top, in place of the olive (the user's pick, 2026-11-16).
    if (preset == "light" && scaleName == "orange" && !negative) return set.withFull(LIGHT_FULL)
    return set
}

/** Light's strength at its full: near black on the orange ramp. */
val LIGHT_FULL = Color(0xFF3A1404)

/** Linen's strength at its full: the orange ramp's top step (`#B8430F`) taken one step deeper. */
val LINEN_FULL = Color(0xFF8A300A)

private fun CleanColors.withFull(c: Color) = CleanColors(done, warmup, failure, drop, danger, ready, recovering, fatigued, c, underweight)

/** A colour as the grey of the same lightness (alpha kept): Mono has no hue anywhere. */
fun Color.toGrey(): Color {
    val l = (0.2126f * red + 0.7152f * green + 0.0722f * blue).coerceIn(0f, 1f)
    return Color(l, l, l, alpha)
}

/**
 * The meaning colours for Mono: greys of distinct lightness on the ramp from [paper] to [ink], so what a colour meant stays readable by lightness
 * (ready is light, fatigued dark; a PR is darker than a matched set) and the words and icons carry the rest. The danger colour is the ink.
 */
fun monoMeaning(paper: Color, ink: Color): CleanColors {
    fun at(t: Float) = lerp(paper, ink, t)
    return CleanColors(
        done = at(0.85f), warmup = at(0.45f), failure = at(0.7f), drop = at(0.6f), danger = ink,
        ready = at(0.35f), recovering = at(0.6f), fatigued = at(0.95f), full = ink, underweight = at(0.45f),
    )
}

val LocalCleanColors = staticCompositionLocalOf { MeaningDark }

/** Three corner sizes and no others: 8 for boxes, chips and badges, 18 for cards, buttons and bands, 24 for sheets, dialogs and the tab bar. */
val SmallShape = RoundedCornerShape(8.dp)
private val CardShape = RoundedCornerShape(18.dp)
/** The fully round button of the looks that ask for one; screens choose between this and the card corner through `buttonShape()`. */
val PillShape = RoundedCornerShape(50)
val SheetShape = RoundedCornerShape(24.dp)
/** The sheet's corners on the top edge only, for the number pad docked to the bottom of the screen; a card docked there takes `cardTopShape()`. */
val SheetTopShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
/** A card's corner under [look]: 18 dp, or 26 in Linen. */
private fun cardShapeOf(look: Look) = if (look.corner == 18f) CardShape else RoundedCornerShape(look.corner.dp)
private fun shapesOf(look: Look): Shapes = cardShapeOf(look).let { Shapes(extraSmall = SmallShape, small = SmallShape, medium = it, large = it, extraLarge = SheetShape) }

/** The accent colours on offer: the shade used on a dark background, then the one used on a light background. The first is the default. */
val ACCENTS: Map<String, Pair<Color, Color>> = linkedMapOf(
    "Blue" to (Color(0xFF8AB4FF) to Color(0xFF2456B8)),
    "Teal" to (Color(0xFF5FD4D0) to Color(0xFF00746F)),
    "Green" to (Color(0xFF6FD39A) to Color(0xFF1E7A46)),
    "Orange" to (Color(0xFFFFB068) to Color(0xFFB25A00)),
    "Red" to (Color(0xFFFF8A80) to Color(0xFFB3261E)),
    "Pink" to (Color(0xFFFF9EC4) to Color(0xFFB2306B)),
    "Purple" to (Color(0xFFC4A5FF) to Color(0xFF6A3FC0)),
)

/**
 * The Material colour scheme for a look. With the AMOLED preset it is the scheme the app had before presets: raised surfaces are the
 * page plus a fixed amount of light, text and quiet text are the dark or light theme's own.
 */
fun Look.colorScheme(): ColorScheme {
    // The negative look is its own negative turned back (the paper look), made into a scheme, then every colour of that turned over.
    if (negative) return negated().colorScheme().inverted(trueInvert)
    val (onDark, onLight) = accent
    val back = page
    return if (dark) {
        Dark.copy(
            primary = onDark, onPrimary = Color(0xFF0B0D12), primaryContainer = lerp(back, onDark, 0.3f), onPrimaryContainer = lerp(onDark, Color.White, 0.6f),
            // Chips, tinted buttons and the tab bar's marker take the accent too, not Material's own purple.
            secondary = onDark, onSecondary = Color(0xFF0B0D12), secondaryContainer = lerp(back, onDark, 0.24f), onSecondaryContainer = lerp(onDark, Color.White, 0.6f),
            tertiaryContainer = lerp(back, Dark.tertiary, 0.24f), onTertiaryContainer = lerp(Dark.tertiary, Color.White, 0.6f),
            onBackground = text, onSurface = text, onSurfaceVariant = muted,
            background = back, surface = back, surfaceContainer = raised, surfaceContainerLow = raised,
            surfaceVariant = higher, surfaceContainerHigh = higher, surfaceContainerHighest = higher,
            errorContainer = lerp(back, Dark.error, 0.24f), surfaceTint = onDark, inversePrimary = onLight,
            inverseSurface = text, inverseOnSurface = back, surfaceDim = back, surfaceContainerLowest = back,
        )
    } else {
        Light.copy(
            primary = onLight, primaryContainer = lerp(Color.White, onLight, 0.18f), onPrimaryContainer = lerp(onLight, Color.Black, 0.5f),
            secondary = onLight, secondaryContainer = lerp(Color.White, onLight, 0.16f), onSecondaryContainer = lerp(onLight, Color.Black, 0.5f),
            tertiaryContainer = lerp(Color.White, Light.tertiary, 0.16f), onTertiaryContainer = lerp(Light.tertiary, Color.Black, 0.5f),
            onBackground = text, onSurface = text, onSurfaceVariant = muted,
            background = back, surface = back, surfaceContainer = raised, surfaceContainerLow = raised,
            surfaceVariant = higher, surfaceContainerHigh = higher, surfaceContainerHighest = higher,
            surfaceTint = onLight, inversePrimary = onDark,
        ).let { if (divider == null) it else it.copy(outlineVariant = divider) }.let {
            // Linen: the ink is the Material primary (buttons, tabs, switches, headings, names); the orange is asked for by name, see [orange].
            if (!inkMainButton) it else it.copy(
                primary = text, onPrimary = Color.White, primaryContainer = tile, onPrimaryContainer = text,
                secondary = text, onSecondary = Color.White, secondaryContainer = tile, onSecondaryContainer = text, surfaceTint = text,
                // Dialogs and tooltips read the high containers: white, like the sheet, not the grey tile.
                surfaceContainerHigh = raised, surfaceContainerHighest = raised,
            )
        }.let {
            // Mono: everything is paper and ink, so the dividers and tracks are ink at a strength and the text on the ink is the paper.
            if (!mono) it else it.copy(
                onPrimary = back, onSecondary = back, outline = lerp(back, text, 0.5f), outlineVariant = lerp(back, text, 0.18f),
                surfaceDim = lerp(back, text, 0.08f), surfaceBright = back, surfaceContainerLowest = back, inverseOnSurface = back,
                // Dialogs, tooltips and sheets are paper (an outline is drawn round them), not a grey.
                surfaceContainerHigh = back, surfaceContainerHighest = back,
                // No amber and no red: the star, the matched badge and a delete are ink at a strength.
                tertiary = lerp(back, text, 0.6f), onTertiary = back, tertiaryContainer = lerp(back, text, 0.12f), onTertiaryContainer = text,
                error = text, onError = back, errorContainer = lerp(back, text, 0.1f), onErrorContainer = text,
            )
        }
    }
}

/** Every colour of the scheme through [invertColour] (the scrim stays black), for the negative look. */
private fun ColorScheme.inverted(trueInvert: Boolean): ColorScheme {
    fun c(x: Color) = invertColour(x, trueInvert)
    return copy(
        primary = c(primary), onPrimary = c(onPrimary), primaryContainer = c(primaryContainer), onPrimaryContainer = c(onPrimaryContainer), inversePrimary = c(inversePrimary),
        secondary = c(secondary), onSecondary = c(onSecondary), secondaryContainer = c(secondaryContainer), onSecondaryContainer = c(onSecondaryContainer),
        tertiary = c(tertiary), onTertiary = c(onTertiary), tertiaryContainer = c(tertiaryContainer), onTertiaryContainer = c(onTertiaryContainer),
        background = c(background), onBackground = c(onBackground), surface = c(surface), onSurface = c(onSurface),
        surfaceVariant = c(surfaceVariant), onSurfaceVariant = c(onSurfaceVariant), surfaceTint = c(surfaceTint),
        inverseSurface = c(inverseSurface), inverseOnSurface = c(inverseOnSurface),
        error = c(error), onError = c(onError), errorContainer = c(errorContainer), onErrorContainer = c(onErrorContainer),
        outline = c(outline), outlineVariant = c(outlineVariant),
        surfaceBright = c(surfaceBright), surfaceDim = c(surfaceDim), surfaceContainer = c(surfaceContainer),
        surfaceContainerHigh = c(surfaceContainerHigh), surfaceContainerHighest = c(surfaceContainerHighest),
        surfaceContainerLow = c(surfaceContainerLow), surfaceContainerLowest = c(surfaceContainerLowest),
    )
}

/** IBM Plex Mono (SIL Open Font Licence; the licence is in `tools/fonts`), for the numbers when the look asks for it. */
val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal), Font(R.font.plex_mono_medium, FontWeight.Medium), Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
)

/**
 * Inter (SIL Open Font Licence; the licence is in `tools/fonts`), one variable font file with the weight axis, for the Linen look: the
 * text in 300 to 700, and the big numbers in Light.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val InterFamily = FontFamily(
    listOf(
        FontWeight.Light to 300, FontWeight.Normal to 400, FontWeight.Medium to 500, FontWeight.SemiBold to 600, FontWeight.Bold to 700,
    ).map { (weight, axis) -> Font(R.font.inter, weight, variationSettings = FontVariation.Settings(FontVariation.weight(axis))) },
)

/** The font the big numbers are set in, or null for the text's own. Screens do not read it directly; they use [numbers]. */
val LocalNumberFont = staticCompositionLocalOf<FontFamily?> { null }

/** Whether the big numbers are set in the light weight (Linen's numbers part). */
val LocalLightNumbers = staticCompositionLocalOf { false }

/** This style with the look's number font (IBM Plex Mono in Mono, Inter Light in Linen), for a value that is a figure: a tile's number, a set's weight, a clock. */
@Composable
fun TextStyle.numbers(): TextStyle {
    val light = LocalLightNumbers.current
    return LocalNumberFont.current?.let { copy(fontFamily = it, fontWeight = if (light) FontWeight.Light else fontWeight) } ?: this
}

/**
 * [look] says every colour and the switches the shared pieces read (`LocalLook`). [textScale] and [fontPath] (an
 * imported font file) shape the text.
 */
@Composable
fun CleanTheme(
    look: Look,
    textScale: Float = DEFAULT_TEXT_SCALE,
    fontPath: String? = null,
    content: @Composable () -> Unit,
) {
    val scheme = remember(look) { look.colorScheme() }
    // A font file that has gone missing or will not load falls back to the default.
    val imported = remember(fontPath) {
        fontPath?.let(::File)?.takeIf { it.exists() }?.let { runCatching { FontFamily(Font(it)) }.getOrNull() }
    }
    // An imported font wins over the look's own.
    val family = imported ?: if (look.inter) InterFamily else null
    // The negative look is made of the light look's colours turned over, the meaning colours included.
    val meaning = remember(look) { look.meaning() }
    CompositionLocalProvider(
        LocalTextScale provides textScale / DEFAULT_TEXT_SCALE, LocalCleanColors provides meaning, LocalLook provides look,
        LocalNumberFont provides if (look.plexNumbers) PlexMono else if (look.lightNumbers) InterFamily else null,
        LocalLightNumbers provides look.lightNumbers,
    ) {
        MaterialTheme(colorScheme = scheme, typography = remember(textScale, family) { typography(textScale, family) }, shapes = remember(look.corner) { shapesOf(look) }, content = content)
    }
}

/** The shape of a button under the current look: fully round or the card corner. */
@Composable
fun buttonShape() = if (LocalLook.current.roundButtons) PillShape else cardShape()

/** The shape of a card under the current look: 18 dp, or 26 in Linen. Every card, tile, band and box the size of a card reads this; no screen types a card's corner. */
@Composable
fun cardShape() = LocalLook.current.corner.let { if (it == 18f) CardShape else RoundedCornerShape(it.dp) }

/** A card's corner under the current look, for a corner that animates or is set apart from the shape. */
@Composable
fun cardCorner(): Dp = LocalLook.current.corner.dp

/** The card's corners on the top edge only, for what is docked to the bottom of the screen: the workout banner and a finished workout's totals. */
@Composable
fun cardTopShape() = cardCorner().let { RoundedCornerShape(topStart = it, topEnd = it) }

/** The look's accent colour itself. In Linen the Material primary is the ink, so the places that keep the orange (small badges, the selected chip, a chart's last point, the middle Workout button) read this. */
val Look.orange: Color get() = if (dark) accent.first else accent.second
