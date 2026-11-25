package clean.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import clean.core.ACCENT_CHOICES

/**
 * One theme in one mode (Round 34, `artifacts/round6/themes-proposal.md`): the page, the card (white in Light), text and quiet text, the accent and the ink on it,
 * the good, bad and PR colours, and the four chart colours that follow the accent. Dark and Light are designed separately; the values are the proposal's computed ones.
 */
class ModeColours(
    val page: Color, val card: Color, val text: Color, val muted: Color, val accent: Color, val ink: Color,
    val good: Color, val bad: Color, val pr: Color, val series: List<Color>,
) {
}

private fun mode(page: Long, card: Long, text: Long, muted: Long, accent: Long, ink: Long, good: Long, bad: Long, pr: Long, series: List<Long>) =
    ModeColours(Color(page), Color(card), Color(text), Color(muted), Color(accent), Color(ink), Color(good), Color(bad), Color(pr), series.map { Color(it) })

/** One of the twelve themes: its Dark and its Light colours. */
class ThemeDef(val dark: ModeColours, val light: ModeColours) {
    val ground: Color get() = dark.page
    val accent: Color get() = dark.accent
    val ink: Color get() = dark.ink
    val lightAccent: Color get() = light.accent
    /** The colour that lightens the Dark page to the card at 5.5 % (the card is the proposal's own value), for the raised steps and previews. */
    val tint: Color get() = tintFor(dark.page, dark.card)
    /** The ambient light behind the top of a tab page, alpha included (white 7 % in Graphite, else the accent at 15 %). */
    val ambient: Color get() = if (dark.accent == THEME_GRAPHITE_ACCENT) Color.White.copy(alpha = 0.07f) else dark.accent.copy(alpha = 0.15f)
}

private val THEME_GRAPHITE_ACCENT = Color(0xFFF4F4F5)

/** The colour that, laid over [page] at 5.5 %, gives [card]. */
private fun tintFor(page: Color, card: Color): Color {
    fun c(p: Float, k: Float) = (p + (k - p) / 0.055f).coerceIn(0f, 1f)
    return Color(c(page.red, card.red), c(page.green, card.green), c(page.blue, card.blue))
}

/** The themes on offer, Graphite first and the default; then wheel order, neutrals first (the proposal's `DATA`). */
val THEMES: Map<String, ThemeDef> = linkedMapOf(
    "Graphite" to ThemeDef(
        dark = mode(0xFF0E0E0F, 0xFF1B1B1C, 0xFFF2F2F3, 0xFFA3A3A7, 0xFFF4F4F5, 0xFF111113, 0xFF2FD3A6, 0xFFE5534B, 0xFFF6D653, listOf(0xFFE1BE8E, 0xFF8D8952, 0xFF6788B3, 0xFFBEBEF4)),
        light = mode(0xFFF1F1F0, 0xFFFFFFFF, 0xFF121212, 0xFF5F5F5B, 0xFF1A1A1C, 0xFFFFFFFF, 0xFF047C5D, 0xFFC0352D, 0xFF7F6A03, listOf(0xFF947446, 0xFF534F18, 0xFF304E75, 0xFF7574A5)),
    ),
    "Slate" to ThemeDef(
        dark = mode(0xFF0B0D10, 0xFF181A1D, 0xFFEDF0F4, 0xFF9EA5AD, 0xFFB0C0D0, 0xFF0B151F, 0xFF2FD3A6, 0xFFE5534B, 0xFFF6D653, listOf(0xFFAF756B, 0xFFB7A36D, 0xFFAFEED2, 0xFF6788B3)),
        light = mode(0xFFECF1F5, 0xFFFFFFFF, 0xFF161B20, 0xFF535C66, 0xFF445160, 0xFFFFFFFF, 0xFF047C5D, 0xFFC0352D, 0xFF7F6A03, listOf(0xFFA2686C, 0xFF6F3F29, 0xFF6A6630, 0xFF4F80A2)),
    ),
    "Crimson" to ThemeDef(
        dark = mode(0xFF120B0B, 0xFF1F1818, 0xFFF4EFEE, 0xFFADA1A1, 0xFFEB4F55, 0xFF1E0F10, 0xFF2FD3A6, 0xFFD454A6, 0xFFF6D653, listOf(0xFFE2C162, 0xFF16BBBC, 0xFFB1E8FE, 0xFF6A81CE)),
        light = mode(0xFFFAEDEC, 0xFFFFFFFF, 0xFF201818, 0xFF665756, 0xFFB4232A, 0xFFFFFFFF, 0xFF047C5D, 0xFFB23683, 0xFF7F6A03, listOf(0xFF258E6A, 0xFF02707E, 0xFF1D4D87, 0xFF7470B9)),
    ),
    "Ember" to ThemeDef(
        dark = mode(0xFF120C09, 0xFF1F1916, 0xFFF4EFED, 0xFFADA29D, 0xFFF58028, 0xFF1D100A, 0xFF2FD3A6, 0xFFE45069, 0xFFD8E263, listOf(0xFFBF6583, 0xFFEAE381, 0xFF96EFFE, 0xFF87A0F0)),
        light = mode(0xFFF9EDE9, 0xFFFFFFFF, 0xFF201916, 0xFF655852, 0xFFB15901, 0xFFFFFFFF, 0xFF047C5D, 0xFFB1223F, 0xFF9F4F7C, listOf(0xFF7B2E4B, 0xFF258E6A, 0xFF1D4D87, 0xFF856AB2)),
    ),
    "Saffron" to ThemeDef(
        dark = mode(0xFF100D07, 0xFF1D1A13, 0xFFF2F0EC, 0xFFA9A49B, 0xFFFAC13B, 0xFF191306, 0xFF2FD3A6, 0xFFE5534B, 0xFF93F29A, listOf(0xFFC3656F, 0xFF8BF7CA, 0xFF5188CD, 0xFFBDBCFE)),
        light = mode(0xFFF4F0E6, 0xFFFFFFFF, 0xFF1D1A14, 0xFF605A4E, 0xFF866701, 0xFFFFFFFF, 0xFF047C5D, 0xFF9D050A, 0xFFA94F49, listOf(0xFF7E3126, 0xFF067272, 0xFF6176BB, 0xFF573C7F)),
    ),
    "Green" to ThemeDef(
        dark = mode(0xFF0A0F0A, 0xFF161C16, 0xFFEEF1ED, 0xFFA0A79F, 0xFF6CD362, 0xFF0D170C, 0xFF10D2B9, 0xFFE5534B, 0xFFFECE94, listOf(0xFFFFD1D3, 0xFFA28218, 0xFF6FA7EE, 0xFFB86797)),
        light = mode(0xFFEBF3EA, 0xFFFFFFFF, 0xFF181C17, 0xFF555E53, 0xFF227727, 0xFFFFFFFF, 0xFF027A6D, 0xFF8F0006, 0xFF8D6803, listOf(0xFFB25D65, 0xFF028A9B, 0xFF5E589F, 0xFF75305B)),
    ),
    "Teal" to ThemeDef(
        dark = mode(0xFF070F0D, 0xFF131C1A, 0xFFECF1F0, 0xFF9CA8A4, 0xFF54D4B8, 0xFF061713, 0xFF0DCBEA, 0xFFE5534B, 0xFFF6D653, listOf(0xFFBF6583, 0xFFD09945, 0xFFEAE381, 0xFF9D99ED)),
        light = mode(0xFFE7F3F0, 0xFFFFFFFF, 0xFF151C1A, 0xFF4F5F5A, 0xFF0D6D5E, 0xFFFFFFFF, 0xFF08768B, 0xFFBC312A, 0xFF7F6A03, listOf(0xFF733E01, 0xFF94771A, 0xFF6176BB, 0xFF573C7F)),
    ),
    "Lagoon" to ThemeDef(
        dark = mode(0xFF070F11, 0xFF131C1E, 0xFFECF1F3, 0xFF9CA7AB, 0xFF65D1F4, 0xFF06161C, 0xFF2FD3A6, 0xFFE5534B, 0xFFF6D653, listOf(0xFFC3656F, 0xFFFED7A1, 0xFFB0A946, 0xFF6A81CE)),
        light = mode(0xFFE7F2F7, 0xFFFFFFFF, 0xFF151C1F, 0xFF4F5E63, 0xFF117393, 0xFFFFFFFF, 0xFF047C5D, 0xFFC0352D, 0xFF7F6A03, listOf(0xFF7B2E4B, 0xFF855904, 0xFF258E6A, 0xFF364789)),
    ),
    "Cobalt" to ThemeDef(
        dark = mode(0xFF0B0D13, 0xFF181A20, 0xFFEEF0F4, 0xFFA0A5AE, 0xFF6489FE, 0xFF0E141F, 0xFF2FD3A6, 0xFFE5534B, 0xFFF6D653, listOf(0xFFB0A946, 0xFFBCF09F, 0xFF60D4FE, 0xFFB86797)),
        light = mode(0xFFECF0FA, 0xFFFFFFFF, 0xFF181B20, 0xFF555B66, 0xFF2A44BC, 0xFFFFFFFF, 0xFF047C5D, 0xFFC0352D, 0xFF7F6A03, listOf(0xFF94771A, 0xFF475403, 0xFF028A9B, 0xFF6D3369)),
    ),
    "Iris" to ThemeDef(
        dark = mode(0xFF0E0C12, 0xFF1B191F, 0xFFF1EFF4, 0xFFA6A3AC, 0xFFBA8FF6, 0xFF15111D, 0xFF2FD3A6, 0xFFE5534B, 0xFFF6D653, listOf(0xFFBF6583, 0xFFD09945, 0xFFEAE381, 0xFF96EFFE)),
        light = mode(0xFFF1EEF8, 0xFFFFFFFF, 0xFF1B191F, 0xFF5C5865, 0xFF6D37AE, 0xFFFFFFFF, 0xFF047C5D, 0xFFC0352D, 0xFF7F6A03, listOf(0xFF7C350E, 0xFF94771A, 0xFF025763, 0xFF3082B5)),
    ),
    "Orchid" to ThemeDef(
        dark = mode(0xFF110B0F, 0xFF1E181C, 0xFFF3EFF1, 0xFFABA1A7, 0xFFEA82C5, 0xFF1C0F17, 0xFF2FD3A6, 0xFFE5534B, 0xFFF6D653, listOf(0xFFC4685A, 0xFFFED7A1, 0xFFB0A946, 0xFF6CF7F7)),
        light = mode(0xFFF7EDF3, 0xFFFFFFFF, 0xFF1F181C, 0xFF63575E, 0xFFA52778, 0xFFFFFFFF, 0xFF047C5D, 0xFFC0352D, 0xFF7F6A03, listOf(0xFFA07020, 0xFF554F02, 0xFF258E6A, 0xFF4B5EA2)),
    ),
    "Rose" to ThemeDef(
        dark = mode(0xFF120B0C, 0xFF1F1819, 0xFFF4EEEF, 0xFFADA1A3, 0xFFF7ACBC, 0xFF1D0F12, 0xFF2FD3A6, 0xFFC35CC4, 0xFFF6D653, listOf(0xFFB07A20, 0xFFEAE381, 0xFF039C89, 0xFF9D99ED)),
        light = mode(0xFFF9ECEF, 0xFFFFFFFF, 0xFF20181A, 0xFF655659, 0xFFB9476A, 0xFFFFFFFF, 0xFF047C5D, 0xFFA040A4, 0xFF7F6A03, listOf(0xFF94771A, 0xFF0B5D2A, 0xFF0A87AA, 0xFF633876)),
    ),
).also { check(it.keys.toList() == ACCENT_CHOICES) }

/** The four colours that follow the accent in a chart, per theme: for a dark page, then for a light one (the proposal's series; the first line is always the accent). */
val SERIES: Map<String, Pair<List<Color>, List<Color>>> = THEMES.mapValues { it.value.dark.series to it.value.light.series }

/** The accents of the themes: the shade used on a dark background, then the one used on a light background. */
val ACCENTS: Map<String, Pair<Color, Color>> = THEMES.mapValues { it.value.dark.accent to it.value.light.accent }

/**
 * What the screens read: every colour and switch of the current look, already resolved. Plain data. The Material colour scheme is built
 * from it (`Look.colorScheme()` in Theme.kt). A look is Dark or Light, the Pure black switch (Dark) and one of twelve themes.
 */
data class Look(
    val dark: Boolean,
    /** Whether the page is black (Dark only): the old AMOLED. */
    val pureBlack: Boolean,
    /** The theme's name (one of `THEMES`). */
    val themeName: String,
    /** The page (the theme's ground), the raised surfaces under cards and boxes, and the colour of text and of quiet text. */
    val page: Color,
    val raised: Color,
    val higher: Color,
    val text: Color,
    val muted: Color,
    /** The accent on a dark background, then on a light one. */
    val accent: Pair<Color, Color>,
    /** The calendar, body figure and chart shading, least to most: four steps of the accent (greys for Graphite) from dim to bright. */
    val scale: List<Color>,
    /** The colours of a chart's lines when it has several, for this look: the accent, then the theme's family (see [SERIES]). */
    val series: List<Color> = emptyList(),
    /** The colour of text and icons on an accent fill. */
    val ink: Color,
    /** The theme's light with its alpha: drawn behind the top of a tab page. */
    val ambient: Color,
    /** The fill of a card (the ground's tint at 5.5 % in Dark, white in Light) and of a tile (4.5 %), solid. */
    val card: Color,
    val tile: Color,
    /** The colour the theme lightens its surfaces with. */
    val tint: Color,
    /** The theme's own good, bad and PR colours for this mode (Round 34): done and ready, failure and fatigued, and a PR mark. */
    val good: Color,
    val bad: Color,
    val pr: Color,
    /** Whether the meaning colours are the blue-to-orange set (kv `theme.cbsafe`). */
    val colourBlindSafe: Boolean = false,
)

/** [colour] at [alpha] laid over [base], blended in sRGB like a translucent fill (Compose's `lerp` blends in Oklab, which makes a few per cent of white far too dark on black). */
fun over(colour: Color, alpha: Float, base: Color): Color = colour.copy(alpha = alpha).compositeOver(base)

/** The page: what [Look.page] is, under the name the remake's spec uses. */
val Look.ground: Color get() = page

/** The accent as it is drawn on this look's page. */
val Look.orange: Color get() = if (dark) accent.first else accent.second

/** The colour scale of an accent: four steps from dim to bright, from the page (Dark) or from white (Light) up to the accent itself (B3). */
fun accentScale(accent: Pair<Color, Color>, dark: Boolean, page: Color): List<Color> =
    if (dark) listOf(0.28f, 0.5f, 0.75f, 1f).map { lerp(page, accent.first, it) }
    else listOf(0.3f, 0.5f, 0.75f, 1f).map { lerp(Color.White, accent.second, it) }

/**
 * Round 22, Graphite in Light. Its accent is near-black, so everything made by tinting with it (the boxes `over(accent, 0.16, white)`, the raised step, the
 * page) comes out a flat, dark grey and the shared tokens fall short. The page takes 5.5 % (card against page 1.11 : 1, as Apple's grouped grey is), the quiet
 * text is darkened to 4.5 : 1 on the set boxes (the darkest surface text sits on), and the four steps of the calendar, body figure and bars start at 3 : 1
 * against page and card (they began at 1.9 : 1, as light as the figure's untrained grey) and keep 1.6 : 1 or more between steps.
 */
val GRAPHITE_LIGHT_SCALE = listOf(Color(0xFF8A8A8A), Color(0xFF626262), Color(0xFF3E3E3F), Color(0xFF1A1A1C))

/**
 * The look for a stored choice: [theme] is `dark`, `light` or `phone` (follows [systemDark]); [pureBlack] only matters in Dark; [accent] is a
 * theme's name in `THEMES` (anything else is Graphite). Round 34: Dark and Light are each the theme's own colours (page, card, text, quiet text, accent,
 * ink, good, bad, PR, series); the Dark card is the proposal's (the tint at 5.5 % over the ground), the tile 4.5 % and the higher step 10 % of the same
 * tint; with Pure black the page is black and the same tint lies over it. In Light the card and tile are white.
 */
fun resolveLook(theme: String = "dark", pureBlack: Boolean = false, accent: String = "Graphite", systemDark: Boolean = false, colourBlindSafe: Boolean = false): Look {
    val dark = when (theme) { "light" -> false; "phone" -> systemDark; else -> true }
    val name = if (accent in THEMES) accent else "Graphite"
    val def = THEMES.getValue(name)
    val m = if (dark) def.dark else def.light
    val colours = def.dark.accent to def.light.accent
    val black = dark && pureBlack
    val graphiteLight = !dark && name == "Graphite"
    val page = if (black) Color.Black else m.page
    val raised = if (dark) over(def.tint, 0.055f, page) else Color.White
    val tile = if (dark) over(def.tint, 0.045f, page) else Color.White
    val higher = if (dark) over(def.tint, 0.10f, page) else over(def.light.accent, 0.10f, Color.White)
    val ambient = if (dark) def.ambient else def.light.accent.copy(alpha = def.ambient.alpha / 2f)
    return Look(
        dark = dark, pureBlack = black, themeName = name, page = page, raised = raised, higher = higher,
        text = m.text, muted = m.muted,
        accent = colours, scale = if (graphiteLight) GRAPHITE_LIGHT_SCALE else accentScale(colours, dark, page),
        series = listOf(m.accent) + m.series, ink = m.ink, ambient = ambient,
        card = raised, tile = tile, tint = def.tint, good = m.good, bad = m.bad, pr = m.pr, colourBlindSafe = colourBlindSafe,
    )
}

/** What the shared pieces read: the card, the button, the charts and the calendar all follow it. */
val LocalLook = staticCompositionLocalOf { resolveLook() }

/** The look [t] of the way from [a] to [b]: every colour blended, the words (Dark or Light, the theme's name) those of [b]. Used to cross-fade a change of look. */
fun lerpLook(a: Look, b: Look, t: Float): Look = when {
    t <= 0f -> a
    t >= 1f -> b
    else -> b.copy(
        page = lerp(a.page, b.page, t), raised = lerp(a.raised, b.raised, t), higher = lerp(a.higher, b.higher, t),
        text = lerp(a.text, b.text, t), muted = lerp(a.muted, b.muted, t),
        accent = lerp(a.accent.first, b.accent.first, t) to lerp(a.accent.second, b.accent.second, t),
        scale = b.scale.mapIndexed { i, c -> lerp(a.scale.getOrElse(i) { c }, c, t) },
        series = b.series.mapIndexed { i, c -> lerp(a.series.getOrElse(i) { c }, c, t) },
        ink = lerp(a.ink, b.ink, t), good = lerp(a.good, b.good, t), bad = lerp(a.bad, b.bad, t), pr = lerp(a.pr, b.pr, t), ambient = lerp(a.ambient, b.ambient, t), card = lerp(a.card, b.card, t), tile = lerp(a.tile, b.tile, t), tint = lerp(a.tint, b.tint, t),
    )
}

/**
 * Each theme's own PR colour, for a dark page and for a light one (Round 28 made it a colour of its own; Round 34 takes the proposal's): a gold unless the
 * accent is within 60 degrees of gold, then the nearest hue that clears the accent, the good and the bad (lemon for Ember, mint for Saffron, apricot for Green).
 */
val PR_COLOURS: Map<String, Pair<Color, Color>> = THEMES.mapValues { it.value.dark.pr to it.value.light.pr }

/** The colour of a PR mark in this look (see [PR_COLOURS]). */
val Look.prColour: Color get() = pr
