package clean.app.ui.theme

import clean.app.generated.resources.Res
import clean.app.generated.resources.inter
import org.jetbrains.compose.resources.Font

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

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
data class CleanColors(
    val done: Color, val warmup: Color, val failure: Color, val drop: Color, val danger: Color,
    /** Fatigue (and the BMI bands): ready / healthy, recovering / overweight, fatigued / obese, and strength at its full; underweight is BMI's alone. */
    val ready: Color, val recovering: Color, val fatigued: Color, val full: Color, val underweight: Color,
)

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
// Light values keep 4.5:1 against the light background, like [MeaningLight] (Round 34: the vermillion is B34C00, as the Graphite page is F1F1F0 now and the darkest theme page E7F3F0).
private val MeaningLightSafe = CleanColors(
    done = Color(0xFF0072B2), warmup = Color(0xFFA86A00), failure = Color(0xFFB34C00), drop = Color(0xFF6A45C8), danger = Color(0xFFC62F33),
    ready = Color(0xFF0072B2), recovering = Color(0xFF8A6400), fatigued = Color(0xFFB34C00), full = Color(0xFF7D6200), underweight = Color(0xFFA8497F),
)

/**
 * The rule that keeps a meaning colour apart from the accent (round 20). A meaning colour whose hue, in Oklch, is less than [MEANING_HUE_GAP] degrees
 * from the accent's (the accent being chromatic: Graphite's near-white has no hue to clash with) is turned to [MEANING_HUE_TARGET] degrees from it, on the
 * side it already sat on; done and ready, the "good" colours, always go to the cool side (green to teal to blue: Moss's good becomes teal). Its lightness
 * and chroma are kept, then its lightness is moved until it has the contrast against the look's card it had before (at most 4.5 : 1), so a colour
 * that was readable stays readable. A colour that is already far enough, and a grey, is left alone.
 */
const val MEANING_HUE_GAP = 36f
const val MEANING_HUE_TARGET = 38f
/** An accent or a colour with less chroma than this has no hue to speak of. */
private const val MIN_HUE_CHROMA = 0.03f

private fun Color.apartFrom(accent: Oklch, card: Color, dark: Boolean, cool: Boolean): Color {
    val me = toOklch()
    if (accent.c < MIN_HUE_CHROMA || me.c < MIN_HUE_CHROMA) return this
    val signed = ((me.h - accent.h + 540f) % 360f) - 180f
    if (abs(signed) >= MEANING_HUE_GAP) return this
    val side = if (cool || abs(signed) < 1f) 1f else if (signed > 0f) 1f else -1f
    val hue = (accent.h + side * MEANING_HUE_TARGET + 360f) % 360f
    val need = minOf(contrastRatio(this, card), 4.5f)
    var l = me.l
    var out = oklch(l, me.c, hue)
    var guard = 0
    while (contrastRatio(out, card) < need && guard++ < 60) {
        l += if (dark) 0.01f else -0.01f
        out = oklch(l, me.c, hue)
    }
    return out
}

private val GRAPHITE_LIGHT_OUTLINE = Color(0xFF757575)
private val GRAPHITE_LIGHT_HAIRLINE = Color(0xFFC8C8C8)

/**
 * The amber of a matched best and of the favourite star (`colorScheme.tertiary`, so every use of it follows), kept off the accent by the same rule as the
 * meaning colours (round 22): within [MEANING_HUE_GAP] degrees of a chromatic accent it is turned to [MEANING_HUE_TARGET] degrees from it, on the side
 * it sat on (Ember's orange pushes it toward a yellow-olive), its lightness moved until it has the contrast it had against the card.
 */
fun Look.tertiary(): Color {
    val amber = if (dark) Dark.tertiary else Light.tertiary
    return amber.apartFrom(orange.toOklch(), card, dark, cool = false)
}

/** The meaning colours this look draws: the green-to-red set or, with [Look.colourBlindSafe], the blue-to-orange one, the dark or the light shades by the look, each kept [MEANING_HUE_GAP] degrees of hue from the accent. */
fun Look.meaning(): CleanColors {
    val set = if (colourBlindSafe) (if (dark) MeaningDarkSafe else MeaningLightSafe) else (if (dark) MeaningDark else MeaningLight)
    val accent = orange.toOklch()
    fun Color.away(cool: Boolean = false) = apartFrom(accent, card, dark, cool)
    val shifted = set.copy(
        done = set.done.away(cool = true), warmup = set.warmup.away(), failure = set.failure.away(), drop = set.drop.away(), danger = set.danger.away(),
        ready = set.ready.away(cool = true), recovering = set.recovering.away(), fatigued = set.fatigued.away(), full = set.full.away(), underweight = set.underweight.away(),
    )
    // Round 34: the theme's own good and bad (computed in the proposal to clear the accent, also under colour-vision deficiency) are done and ready, failure and fatigued;
    // the blue-to-orange set keeps its own. The other colours still go through the rule above.
    val own = if (colourBlindSafe) shifted else shifted.copy(done = good, ready = good, failure = bad, fatigued = bad, danger = bad)
    // In Crimson red is the accent, so what deletes is drawn in the text colour (with a bin icon, see DangerButton) and red keeps one meaning.
    return if (themeName == "Crimson") own.copy(danger = text) else own
}

val LocalCleanColors = staticCompositionLocalOf { MeaningDark }

/** Three corner sizes and no others: 8 for boxes, chips and badges, 18 for cards, buttons and bands, 24 for sheets, dialogs and the tab bar. */
val SmallShape = RoundedCornerShape(8.dp)
/** The corner of the small colour swatches in a legend (Round 36: five places typed it); smaller than a box, so the swatch stays a square. */
val LegendShape = RoundedCornerShape(3.dp)
private val CardShape = RoundedCornerShape(18.dp)
/** The fully round button of the looks that ask for one; screens choose between this and the card corner through `buttonShape()`. */
val PillShape = RoundedCornerShape(50)
val SheetShape = RoundedCornerShape(24.dp)
/** The sheet's corners on the top edge only, for the number pad docked to the bottom of the screen; a card docked there takes `cardTopShape()`. */
val SheetTopShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
val CardCorner = 18.dp
private val shapes = Shapes(extraSmall = SmallShape, small = SmallShape, medium = CardShape, large = CardShape, extraLarge = SheetShape)

/** The Material colour scheme for a look: raised surfaces are the look's own steps, text and quiet text its own, the accent drives primary. */
fun Look.colorScheme(): ColorScheme {
    val (onDark, onLight) = accent
    val back = page
    val tertiary = tertiary()
    return if (dark) {
        Dark.copy(
            primary = onDark, onPrimary = ink, primaryContainer = lerp(back, onDark, 0.3f), onPrimaryContainer = lerp(onDark, Color.White, 0.6f),
            // Chips, tinted buttons and the tab bar's marker take the accent too, not Material's own purple.
            secondary = onDark, onSecondary = ink, secondaryContainer = lerp(back, onDark, 0.24f), onSecondaryContainer = lerp(onDark, Color.White, 0.6f),
            tertiary = tertiary, tertiaryContainer = lerp(back, tertiary, 0.24f), onTertiaryContainer = lerp(tertiary, Color.White, 0.6f),
            onBackground = text, onSurface = text, onSurfaceVariant = muted,
            background = back, surface = back, surfaceContainer = raised, surfaceContainerLow = raised,
            surfaceVariant = higher, surfaceContainerHigh = higher, surfaceContainerHighest = higher,
            errorContainer = lerp(back, Dark.error, 0.24f), surfaceTint = onDark, inversePrimary = onLight,
            inverseSurface = text, inverseOnSurface = back, surfaceDim = back, surfaceContainerLowest = back,
        )
    } else {
        Light.copy(
            primary = onLight, onPrimary = ink, primaryContainer = lerp(Color.White, onLight, 0.18f), onPrimaryContainer = lerp(onLight, Color.Black, 0.5f),
            secondary = onLight, secondaryContainer = lerp(Color.White, onLight, 0.16f), onSecondaryContainer = lerp(onLight, Color.Black, 0.5f),
            tertiary = tertiary, tertiaryContainer = lerp(Color.White, tertiary, 0.16f), onTertiaryContainer = lerp(tertiary, Color.Black, 0.5f),
            // Graphite has no hue, so its outlines are greys too (the scheme's own are blue-grey): 4.1 : 1 on the page for a control's edge, a hairline for dividers.
            outline = if (themeName == "Graphite") GRAPHITE_LIGHT_OUTLINE else Light.outline,
            outlineVariant = if (themeName == "Graphite") GRAPHITE_LIGHT_HAIRLINE else Light.outlineVariant,
            onBackground = text, onSurface = text, onSurfaceVariant = muted,
            background = back, surface = back, surfaceContainer = raised, surfaceContainerLow = raised,
            surfaceVariant = higher, surfaceContainerHigh = higher, surfaceContainerHighest = higher,
            surfaceTint = onLight, inversePrimary = onDark,
        )
    }
}

/**
 * Inter (SIL Open Font Licence; the licence is in `tools/fonts`), one variable font file with the weight axis, for the Linen look: the
 * text in 300 to 700, and the big numbers in Light.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
fun interFamily(): FontFamily {
    val fonts = listOf(
        FontWeight.Light to 300, FontWeight.Normal to 400, FontWeight.Medium to 500, FontWeight.SemiBold to 600, FontWeight.Bold to 700,
    ).map { (weight, axis) -> Font(Res.font.inter, weight, variationSettings = FontVariation.Settings(FontVariation.weight(axis))) }
    return remember(fonts) { FontFamily(fonts) }
}

/**
 * [look] says every colour (`LocalLook`); [textScale] shapes the text. The font is Inter everywhere, with tabular digits (see `typography`).
 */
@Composable
fun CleanTheme(
    look: Look,
    textScale: Float = DEFAULT_TEXT_SCALE,
    crossFade: Boolean = false,
    content: @Composable () -> Unit,
) {
    val look = crossFaded(look, crossFade)
    val inter = interFamily()
    val scheme = remember(look) { look.colorScheme() }
    val meaning = remember(look) { look.meaning() }
    CompositionLocalProvider(LocalTextScale provides textScale / DEFAULT_TEXT_SCALE, LocalCleanColors provides meaning, LocalLook provides look) {
        MaterialTheme(colorScheme = scheme, typography = remember(textScale, inter) { typography(textScale, inter) }, shapes = shapes, content = content)
    }
}

/** The shape of a button: the card corner. */
@Composable
fun buttonShape() = CardShape

/** The shape of a card: 18 dp. Every card, tile, band and box the size of a card reads this; no screen types a card's corner. */
@Composable
fun cardShape() = CardShape

/** A card's corner, for a corner that animates or is set apart from the shape. */
@Composable
fun cardCorner(): Dp = CardCorner

/** The card's corners on the top edge only, for what is docked to the bottom of the screen: a finished workout's totals. */
@Composable
fun cardTopShape() = RoundedCornerShape(topStart = CardCorner, topEnd = CardCorner)
val GlassWindowShape = RoundedCornerShape(24.dp)

/**
 * [look], but when it changes with [on] the colours slide from the look that was shown to the new one over 250 ms (a switch of theme or of Dark and
 * Light cross-fades the whole app, since every colour is read from the look). With [on] false the change is at once.
 */
@Composable
private fun crossFaded(look: Look, on: Boolean): Look {
    var from by remember { mutableStateOf(look) }
    var to by remember { mutableStateOf(look) }
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(look) {
        if (look == to) return@LaunchedEffect
        from = lerpLook(from, to, progress.value)
        to = look
        if (on) {
            progress.snapTo(0f)
            progress.animateTo(1f, androidx.compose.animation.core.tween(250))
        } else progress.snapTo(1f)
    }
    return lerpLook(from, to, progress.value)
}
