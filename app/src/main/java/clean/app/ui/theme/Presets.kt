package clean.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import clean.app.PALETTES
import clean.app.paletteOf
import clean.app.parseColor

/**
 * The parts a look is made of. Each has a few real values (a stable id kept in the options, and the words the Appearance page
 * shows); a preset picks one value per part, and the user can swap any single part (`theme.mix.<key>`). Ids are shared by
 * value, not by preset: two presets that use the same thing use the same id.
 */
enum class Part(val key: String, val label: String, val options: Map<String, String>) {
    BACKGROUND("background", "Background", linkedMapOf("light" to "Light grey", "slate" to "Slate", "black" to "Black", "paper" to "Paper", "linen" to "Warm off-white")),
    ACCENT("accent", "Accent", linkedMapOf("orange" to "Orange", "blue" to "Blue", "ink" to "Ink", "linen" to "Warm orange")),
    COLOUR_SCALE("scale", "Colour scale", linkedMapOf("orange" to "Orange ramp", "blue" to "Blue ramp", "ember" to "Ember", "ink" to "Paper to ink")),
    TODAY_CARD("today", "Today card", linkedMapOf("plain" to "Plain", "tinted" to "Tinted with the accent")),
    CARDS("cards", "Cards", linkedMapOf("white" to "Solid white", "slate" to "Solid slate", "glass" to "See-through", "clear" to "Transparent", "sheet" to "White sheet, grey tiles")),
    OUTLINES("outlines", "Outlines", linkedMapOf("none" to "None", "thin" to "Thin", "ink" to "Ink, 1.5 dp", "hairline" to "Hairline")),
    SHADOWS("shadows", "Shadows", linkedMapOf("soft" to "Soft", "none" to "None")),
    TAB_BAR("tabbar", "Tab bar", linkedMapOf("black" to "Black", "match" to "Same as cards")),
    TAB_STYLE("tabstyle", "Tab bar style", linkedMapOf("words" to "Words only", "icons" to "Icons and words")),
    FONT("font", "Font", linkedMapOf("roboto" to "Roboto, the phone's font", "inter" to "Inter")),
    NUMBERS("numbers", "Numbers", linkedMapOf("same" to "Same as the font", "plex" to "IBM Plex Mono", "light" to "Inter Light, small units")),
    CORNERS("corners", "Corners", linkedMapOf("18" to "18 dp", "26" to "26 dp")),
    BUTTONS("buttons", "Buttons", linkedMapOf("round" to "Fully round", "card" to "Card corner")),
    CHART_FILL("chartfill", "Chart fill", linkedMapOf("faint" to "Faint", "strong" to "Strong", "fade" to "Grey, fading")),
    SPEED("speed", "Animation speed", linkedMapOf("standard" to "Standard", "fast" to "Fast")),
    TEXTURE("texture", "Paper texture", linkedMapOf("none" to "None", "grain" to "Grain", "fine" to "Fine grain")),
    ICON("icon", "App icon", linkedMapOf("white" to "White tile", "dark" to "Dark grey tile", "black" to "Black tile", "paper" to "Paper tile, ink outline", "linen" to "Off-white tile")),
    PAGE("page", "Page layout", linkedMapOf("cards" to "Cards on the page", "sheet" to "One sheet under the title")),
    MAIN_BUTTONS("mainbuttons", "Main buttons", linkedMapOf("accent" to "Accent", "ink" to "Black")),
    TITLES("titles", "Titles", linkedMapOf("bold" to "Bold", "twotone" to "Two-tone")),
    ROW_ICONS("rowicons", "Row icons", linkedMapOf("plain" to "Plain", "circles" to "In grey circles")),
}

/** The six presets: one value id for every part. AMOLED is how the app has always looked. */
val PRESETS: Map<String, Map<Part, String>> = linkedMapOf(
    "light" to mapOf(
        Part.BACKGROUND to "light", Part.ACCENT to "orange", Part.COLOUR_SCALE to "orange", Part.TODAY_CARD to "plain", Part.CARDS to "white",
        Part.OUTLINES to "none", Part.SHADOWS to "soft", Part.TAB_BAR to "black", Part.TAB_STYLE to "icons", Part.FONT to "roboto", Part.NUMBERS to "same", Part.CORNERS to "18",
        Part.BUTTONS to "round", Part.CHART_FILL to "faint", Part.SPEED to "standard", Part.TEXTURE to "none", Part.ICON to "white",
        Part.PAGE to "cards", Part.MAIN_BUTTONS to "accent", Part.TITLES to "bold", Part.ROW_ICONS to "plain",
    ),
    "dark" to mapOf(
        Part.BACKGROUND to "slate", Part.ACCENT to "blue", Part.COLOUR_SCALE to "blue", Part.TODAY_CARD to "tinted", Part.CARDS to "slate",
        Part.OUTLINES to "none", Part.SHADOWS to "none", Part.TAB_BAR to "match", Part.TAB_STYLE to "icons", Part.FONT to "roboto", Part.NUMBERS to "same", Part.CORNERS to "18",
        Part.BUTTONS to "card", Part.CHART_FILL to "strong", Part.SPEED to "standard", Part.TEXTURE to "none", Part.ICON to "dark",
        Part.PAGE to "cards", Part.MAIN_BUTTONS to "accent", Part.TITLES to "bold", Part.ROW_ICONS to "plain",
    ),
    "amoled" to mapOf(
        Part.BACKGROUND to "black", Part.ACCENT to "blue", Part.COLOUR_SCALE to "ember", Part.TODAY_CARD to "tinted", Part.CARDS to "clear",
        Part.OUTLINES to "thin", Part.SHADOWS to "none", Part.TAB_BAR to "match", Part.TAB_STYLE to "icons", Part.FONT to "roboto", Part.NUMBERS to "same", Part.CORNERS to "18",
        Part.BUTTONS to "card", Part.CHART_FILL to "strong", Part.SPEED to "standard", Part.TEXTURE to "none", Part.ICON to "black",
        Part.PAGE to "cards", Part.MAIN_BUTTONS to "accent", Part.TITLES to "bold", Part.ROW_ICONS to "plain",
    ),
    "mono" to mapOf(
        Part.BACKGROUND to "paper", Part.ACCENT to "ink", Part.COLOUR_SCALE to "ink", Part.TODAY_CARD to "plain", Part.CARDS to "white",
        Part.OUTLINES to "ink", Part.SHADOWS to "none", Part.TAB_BAR to "match", Part.TAB_STYLE to "icons", Part.FONT to "roboto", Part.NUMBERS to "plex", Part.CORNERS to "18",
        Part.BUTTONS to "card", Part.CHART_FILL to "faint", Part.SPEED to "fast", Part.TEXTURE to "grain", Part.ICON to "paper",
        Part.PAGE to "cards", Part.MAIN_BUTTONS to "accent", Part.TITLES to "bold", Part.ROW_ICONS to "plain",
    ),
    "linen" to mapOf(
        Part.BACKGROUND to "linen", Part.ACCENT to "linen", Part.COLOUR_SCALE to "orange", Part.TODAY_CARD to "plain", Part.CARDS to "sheet",
        Part.OUTLINES to "hairline", Part.SHADOWS to "none", Part.TAB_BAR to "black", Part.TAB_STYLE to "words", Part.FONT to "inter", Part.NUMBERS to "light", Part.CORNERS to "26",
        Part.BUTTONS to "round", Part.CHART_FILL to "fade", Part.SPEED to "standard", Part.TEXTURE to "fine", Part.ICON to "linen",
        Part.PAGE to "sheet", Part.MAIN_BUTTONS to "ink", Part.TITLES to "twotone", Part.ROW_ICONS to "circles",
    ),
)

const val DEFAULT_PRESET = "amoled"
val PRESET_LABELS: Map<String, String> = linkedMapOf("light" to "Light", "dark" to "Dark", "amoled" to "AMOLED", "mono" to "Mono", "linen" to "Linen")

/** Linen's colours: the warm off-white page, its ink and quiet text, the faintest text, the grey stat tile, the hairline and the orange. */
val LINEN_PAGE = Color(0xFFF1EFEA)
val LINEN_INK = Color(0xFF1C1C1A)
val LINEN_MUTED = Color(0xFF8A8883)
val LINEN_FAINT = Color(0xFFA9A7A1)
val LINEN_TILE = Color(0xFFF3F1ED)
val LINEN_HAIRLINE = Color(0xFFE6E3DD)
/** The hairline on a dark page (Linen as Day & night's night look, or Mix and match on a dark preset). */
val LINEN_HAIRLINE_DARK = Color(0xFF34322E)
val LINEN_ACCENT = Color(0xFFE8642A)

/** The fine grain's fixed strength (0 to 1; Mono's slider defaults to 0.35), for Linen. */
const val FINE_GRAIN = 0.18f

/** Mono's paper and ink with their sliders at 0: white paper, the app's near-black ink. */
val MONO_PAPER = Color(0xFFFFFFFF)
val MONO_INK = Color(0xFF16181D)

/** The paper grain's strength when its slider has not been touched. */
const val MONO_TEXTURE = 35

/** Mono's own settings (kv `mono.*`): the paper and ink sliders (0 to 100), the grain's strength, and the negative with the way it inverts. */
data class MonoSettings(val paper: Int = 0, val ink: Int = 0, val texture: Int = MONO_TEXTURE, val negative: Boolean = false, val trueInvert: Boolean = true)

/** Mono's settings from the options: a slider is clamped to 0 to 100; the negative is off and the true invert on unless said otherwise (`on` / `off`). */
fun monoSettingsOf(o: Map<String, String>) = MonoSettings(
    paper = o["mono.paper"]?.toIntOrNull()?.coerceIn(0, 100) ?: 0,
    ink = o["mono.ink"]?.toIntOrNull()?.coerceIn(0, 100) ?: 0,
    texture = o["mono.texture"]?.toIntOrNull()?.coerceIn(0, 100) ?: MONO_TEXTURE,
    negative = o["mono.negative"] == "on",
    trueInvert = o["mono.trueInvert"] != "off",
)

private fun grey(v: Double) = Math.round(v).toInt().coerceIn(0, 255).let { Color(it, it, it) }

/** The paper at [slider] (0 to 100): white at 0, then each channel is 255 less 0.4 per step, down to a light grey. */
fun monoPaper(slider: Int): Color = grey(255 - slider.coerceIn(0, 100) * 0.4)

/** The ink at [slider] (0 to 100): the app's `#16181D` at 0, then a grey of 22 plus 0.62 per step in every channel, up to a dark grey. */
fun monoInk(slider: Int): Color = if (slider <= 0) MONO_INK else grey(22 + slider.coerceIn(0, 100) * 0.62)

/** `#RRGGBB` for a slider's readout. */
fun Color.rgbHex(): String = "#%02X%02X%02X".format(Math.round(red * 255), Math.round(green * 255), Math.round(blue * 255))

/** The paper-to-ink colour scale: four steps from a faint ink wash to the ink itself. */
fun monoScale(paper: Color, ink: Color): List<Color> = listOf(0.2f, 0.45f, 0.7f, 1f).map { lerp(paper, ink, it) }

/**
 * The negative of one colour. [trueInvert]: every channel becomes `1 - c`, so hues flip (orange becomes blue). Otherwise only the
 * lightness turns over (HSL, `L` to `1 - L`) and the hue and saturation stay. Alpha is kept. The one colour inversion in the app.
 */
fun invertColour(c: Color, trueInvert: Boolean): Color {
    if (trueInvert) return Color(1f - c.red, 1f - c.green, 1f - c.blue, c.alpha)
    val max = maxOf(c.red, c.green, c.blue)
    val min = minOf(c.red, c.green, c.blue)
    val l = (max + min) / 2f
    val d = max - min
    val s = if (d == 0f) 0f else d / (1f - Math.abs(2f * l - 1f))
    val h = hueOf(c)
    val l2 = 1f - l
    val q = s * (1f - Math.abs(2f * l2 - 1f))
    val x = q * (1f - Math.abs((h / 60f) % 2f - 1f))
    val m = l2 - q / 2f
    val (r, g, b) = when ((h / 60f).toInt().coerceIn(0, 5)) {
        0 -> Triple(q, x, 0f)
        1 -> Triple(x, q, 0f)
        2 -> Triple(0f, q, x)
        3 -> Triple(0f, x, q)
        4 -> Triple(x, 0f, q)
        else -> Triple(q, 0f, x)
    }
    return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f), c.alpha)
}

/** The presets' own calendar and body figure ramps, least to most; "ember" is the app's own list in `PALETTES`. */
private val SCALES: Map<String, List<Color>> = mapOf(
    "orange" to listOf(Color(0xFFF9D4BD), Color(0xFFF4A56F), Color(0xFFEC6A2E), Color(0xFFB8430F)),
    "blue" to listOf(Color(0xFF1D3A6B), Color(0xFF3566B8), Color(0xFF5E92E8), Color(0xFF8AB4FF)),
    "ink" to listOf(Color(0xFFCFD0D3), Color(0xFF9A9CA1), Color(0xFF5E6065), Color(0xFF16181D)),
)

/** A preset's own ramp by the name `Look.scaleName` gives it, or null when it is not one of them. */
fun presetScale(name: String): List<Color>? = SCALES[name]

/**
 * What the screens read: every colour, switch and factor of the current look, already resolved. Plain data. The Material colour
 * scheme is built from it (`Look.colorScheme()` in Theme.kt); the shared pieces (cards, buttons, the tab bar, the charts) read
 * the rest from [LocalLook].
 */
data class Look(
    val preset: String,
    val dark: Boolean,
    /** The page, the raised surfaces under cards and boxes, and the colour of text and of quiet text. */
    val page: Color,
    val raised: Color,
    val higher: Color,
    val text: Color,
    val muted: Color,
    /** The accent on a dark background, then on a light one (the same twice for the presets' own). */
    val accent: Pair<Color, Color>,
    /** How much of the card colour shows: 1 solid, 0.55 see-through, 0 transparent (the outline alone). */
    val cardAlpha: Float,
    /** The outline round a card in dp (0 none) and its colour; null is the theme's outline variant at 60 %. */
    val outlineWidth: Float,
    val outline: Color?,
    val shadow: Boolean,
    /** How much of the accent washes the Today card: 0 plain, 0.1 tinted. */
    val heroTint: Float,
    val blackTabBar: Boolean,
    /** Whether the tab bar is words only (no icons; the tab bar style part) rather than icons with words under them. */
    val tabWords: Boolean,
    val roundButtons: Boolean,
    /** The opacity of an area drawn under a chart line. */
    val chartFill: Float,
    /** 1 is the app's own durations; less is faster. */
    val speed: Float,
    /** The paper grain's strength, 0 to 1 (0 when the texture part is none). */
    val texture: Float,
    val scale: List<Color>,
    /** The name the colour scale goes by (a name in `PALETTES`, a preset ramp, or a `#AARRGGBB` colour). */
    val scaleName: String,
    /** The launcher icon: the tile's id (a value of the icon part) and the accent drawn on it (a name in `ACCENTS`, or `Ink`). */
    val icon: String,
    val iconAccent: String,
    /** Whether the Mono sliders shape this look (the paper background is on show). */
    val mono: Boolean = false,
    /** Whether the numbers are set in IBM Plex Mono (the numbers part). */
    val plexNumbers: Boolean = false,
    /** Whether this look is the negative of the one the parts describe, and how it was inverted (see [negated]). */
    val negative: Boolean = false,
    val trueInvert: Boolean = true,
    /** Whether the meaning colours are the blue-to-orange set (kv `theme.cbsafe`); Mono ignores it. */
    val colourBlindSafe: Boolean = false,
    /** The quietest text (Linen's `#A9A7A1`); the muted colour in every other look. */
    val faint: Color = muted,
    /** The fill of a stat tile (a step under the card in Linen, where the card is the white sheet); the raised colour elsewhere. */
    val tile: Color = raised,
    /** The hairline between rows (Linen's `#E6E3DD`); null is the theme's outline variant. */
    val divider: Color? = null,
    /** The corner of a card in dp (18, or 26 in Linen); read by `cardShape()`. */
    val corner: Float = 18f,
    /** Whether the font is Inter (the font part), and whether the big numbers are set in its light weight (the numbers part). */
    val inter: Boolean = false,
    val lightNumbers: Boolean = false,
    /** Whether a main button (Start, Save) is black with white words rather than the accent (the main buttons part). */
    val inkMainButton: Boolean = false,
    /** Whether the page is a title on the background over one white sheet with large top corners (the page part); drawn by the screens, not yet. */
    val sheetPage: Boolean = false,
    /** Whether a page title is set in two tones, the first word in the text colour and the rest in the quiet colour (the titles part); not yet drawn. */
    val twoToneTitles: Boolean = false,
    /** Whether a row's icon sits in a grey circle (the row icons part); not yet drawn. */
    val iconCircles: Boolean = false,
    /** A chart's line: its colour (null is the chart's own), its width in dp (0 is the chart's own) and whether the area under it fades to nothing (the chart fill part); the charts do not read these yet. */
    val chartLine: Color? = null,
    val chartLineWidth: Float = 0f,
    val chartFade: Boolean = false,
)

/**
 * The look turned inside out: every colour (page, cards, text, accent, outline, colour scale) through [invertColour], and light
 * becomes dark. Applied twice it gives the look back (to within a rounding), which `colorScheme()` relies on.
 */
fun Look.negated(): Look {
    fun c(x: Color) = invertColour(x, trueInvert)
    return copy(
        dark = !dark, page = c(page), raised = c(raised), higher = c(higher), text = c(text), muted = c(muted),
        faint = c(faint), tile = c(tile), divider = divider?.let(::c), chartLine = chartLine?.let(::c),
        accent = c(accent.first) to c(accent.second), outline = outline?.let(::c), scale = scale.map(::c), negative = !negative,
    )
}

/** The fast speed's factor: 160 ms for 300. */
const val SPEED_FAST = 160f / 300f

private fun lifted(back: Color, r: Int, g: Int, b: Int) = Color((back.red + r / 255f).coerceAtMost(1f), (back.green + g / 255f).coerceAtMost(1f), (back.blue + b / 255f).coerceAtMost(1f))

/**
 * The look for [preset] with its parts swapped by [mixes] (a value id for a part; an id that is not one of the part's is ignored)
 * and the older single options as overrides: [accentOverride] (a name in `ACCENTS` or `#AARRGGBB`), [scaleOverride] (a name in
 * `PALETTES` or `#AARRGGBB`), [modeOverride] (`Dark`, `Light`, `System`) and [backgroundOverride] (`Black`, `Dark grey` or a colour,
 * used on a dark look) and [iconAccentOverride] (a name in `ACCENTS`, or `Ink`). With nothing overridden and the AMOLED preset this is exactly the look the app had before presets.
 */
fun resolveLook(
    preset: String,
    mixes: Map<Part, String> = emptyMap(),
    accentOverride: String? = null,
    scaleOverride: String? = null,
    modeOverride: String? = null,
    backgroundOverride: String? = null,
    systemDark: Boolean = false,
    iconAccentOverride: String? = null,
    mono: MonoSettings = MonoSettings(),
    colourBlindSafe: Boolean = false,
): Look {
    val id = if (preset in PRESETS) preset else DEFAULT_PRESET
    val base = PRESETS.getValue(id)
    fun value(part: Part): String = mixes[part]?.takeIf { it in part.options } ?: base.getValue(part)

    val naturalDark = value(Part.BACKGROUND).let { it == "slate" || it == "black" }
    val dark = when (modeOverride) { "Dark" -> true; "Light" -> false; "System" -> systemDark; else -> naturalDark }
    // The background part knows its own page, text and quiet text; a mode it is not made for gets the app's usual ones.
    // Mono's sliders shape the paper look only, and only while that background is really on show.
    val monoLook = value(Part.BACKGROUND) == "paper" && dark == naturalDark
    val paper = monoPaper(mono.paper)
    val ink = monoInk(mono.ink)
    val (page, text, muted) = when {
        dark != naturalDark && dark -> Triple(Color.Black, Color(0xFFF2F3F5), Color(0xFFA0A4AE))
        dark != naturalDark -> Triple(Color(0xFFF6F7FA), Color(0xFF16181D), Color(0xFF50545E))
        else -> when (value(Part.BACKGROUND)) {
            "light" -> Triple(Color(0xFFEFEFED), Color(0xFF121212), Color(0xFF8C8C89))
            "slate" -> Triple(Color(0xFF121316), Color(0xFFF2F3F5), Color(0xFFA0A4AE))
            "paper" -> Triple(paper, ink, lerp(paper, ink, 0.6f))
            "linen" -> Triple(LINEN_PAGE, LINEN_INK, LINEN_MUTED)
            else -> Triple(Color.Black, Color(0xFFF2F3F5), Color(0xFFA0A4AE))
        }
    }
    val linenPage = value(Part.BACKGROUND) == "linen" && dark == naturalDark
    val pageColour = if (dark) backgroundOverride?.let { if (it == "Dark grey") Color(0xFF121316) else if (it == "Black") Color.Black else parseColor(it) } ?: page else page
    // A card colour only fits the mode it was made for; anything else is the see-through card. Transparent shows only the outline.
    val cards = value(Part.CARDS).takeIf { it == "glass" || it == "clear" || ((it == "white" || it == "sheet") && !dark) || (it == "slate" && dark) } ?: "glass"
    val (raised, higher) = when (cards) {
        "white" -> if (monoLook) paper to lerp(paper, ink, 0.1f) else Color.White to Color(0xFFF1F1EF)
        "sheet" -> Color.White to LINEN_TILE
        "slate" -> Color(0xFF1C1E24) to Color(0xFF2A2C33)
        else -> if (dark) lifted(pageColour, 0x12, 0x13, 0x16) to lifted(pageColour, 0x1C, 0x1E, 0x24)
            else if (monoLook) lerp(paper, ink, 0.06f) to lerp(paper, ink, 0.1f) else Color(0xFFECEEF3) to Color(0xFFE6E8EF)
    }
    val accentColours = accentOverride?.let { ACCENTS[it] ?: parseColor(it)?.let { c -> c to lerp(c, Color.Black, 0.35f) } }
        ?: when (value(Part.ACCENT)) {
            "orange" -> Color(0xFFEC6A2E).let { it to it }
            "linen" -> LINEN_ACCENT.let { it to it }
            "ink" -> (if (monoLook) ink else MONO_INK).let { it to it }
            else -> ACCENTS.getValue("Blue")
        }
    // Mono has no hue: whatever accent was chosen, the accent is the ink.
    val accent = if (monoLook) ink to ink else accentColours
    val scaleId = value(Part.COLOUR_SCALE)
    val scaleName = scaleOverride ?: if (scaleId == "ember") PALETTES.keys.first() else scaleId
    val outlines = value(Part.OUTLINES)
    // The launcher icon wears the accent chosen for it, else the look's accent as the nearest of the named ones (Ink for the ink accent).
    val accentName = when {
        accentOverride != null -> accentOverride.takeIf { it in ACCENTS } ?: parseColor(accentOverride)?.let { nearestAccent(it) } ?: "Blue"
        else -> when (value(Part.ACCENT)) { "orange", "linen" -> "Orange"; "ink" -> "Ink"; else -> "Blue" }
    }
    val iconAccent = iconAccentOverride?.takeIf { it == "Ink" || it in ACCENTS } ?: accentName
    val look = Look(
        preset = id, dark = dark, page = pageColour, raised = raised, higher = higher, text = text, muted = muted, accent = accent,
        cardAlpha = when (cards) { "clear" -> 0f; "glass" -> 0.55f; else -> 1f },
        outlineWidth = when (outlines) { "none" -> 0f; "ink" -> 1.5f; else -> 1f },
        outline = when (outlines) { "ink" -> text; "hairline" -> if (dark) LINEN_HAIRLINE_DARK else LINEN_HAIRLINE; else -> null },
        divider = if (outlines == "hairline") (if (dark) LINEN_HAIRLINE_DARK else LINEN_HAIRLINE) else null,
        shadow = value(Part.SHADOWS) == "soft",
        heroTint = if (value(Part.TODAY_CARD) == "tinted") 0.1f else 0f,
        blackTabBar = value(Part.TAB_BAR) == "black",
        tabWords = value(Part.TAB_STYLE) == "words",
        roundButtons = value(Part.BUTTONS) == "round",
        chartFill = when (value(Part.CHART_FILL)) { "faint" -> 0.07f; "fade" -> 0.14f; else -> 0.22f },
        speed = if (value(Part.SPEED) == "fast") SPEED_FAST else 1f,
        texture = when (value(Part.TEXTURE)) { "grain" -> mono.texture / 100f; "fine" -> FINE_GRAIN; else -> 0f },
        scale = if (monoLook) monoScale(paper, ink) else paletteOf(scaleName),
        scaleName = scaleName,
        icon = value(Part.ICON),
        iconAccent = iconAccent,
        mono = monoLook,
        plexNumbers = value(Part.NUMBERS) == "plex",
        lightNumbers = value(Part.NUMBERS) == "light",
        inter = value(Part.FONT) == "inter",
        faint = if (linenPage) LINEN_FAINT else muted,
        tile = if (cards == "sheet") LINEN_TILE else raised,
        corner = if (value(Part.CORNERS) == "26") 26f else 18f,
        inkMainButton = value(Part.MAIN_BUTTONS) == "ink",
        sheetPage = value(Part.PAGE) == "sheet",
        twoToneTitles = value(Part.TITLES) == "twotone",
        iconCircles = value(Part.ROW_ICONS) == "circles",
        chartFade = value(Part.CHART_FILL) == "fade",
        chartLine = if (value(Part.CHART_FILL) == "fade") text else null,
        chartLineWidth = if (value(Part.CHART_FILL) == "fade") 1.5f else 0f,
        trueInvert = mono.trueInvert,
        colourBlindSafe = colourBlindSafe,
    )
    if (!(monoLook && mono.negative)) return look
    // The negative of Mono is dark, and its launcher icon is the black tile with the light ink.
    val negative = look.negated()
    return if (negative.icon == "paper") negative.copy(icon = "black", iconAccent = iconAccentOverride?.takeIf { it == "Ink" || it in ACCENTS } ?: "Ink") else negative
}

/** The name in `ACCENTS` whose hue is nearest to [colour]'s, or `Ink` for a grey. */
fun nearestAccent(colour: Color): String {
    if (maxOf(colour.red, colour.green, colour.blue) - minOf(colour.red, colour.green, colour.blue) < 0.12f) return "Ink"
    val hue = hueOf(colour)
    return ACCENTS.minBy { (_, shades) -> hueGap(hue, hueOf(shades.first)) }.key
}

/** A colour's hue in degrees, 0 for a grey. */
private fun hueOf(c: Color): Float {
    val max = maxOf(c.red, c.green, c.blue)
    val d = max - minOf(c.red, c.green, c.blue)
    if (d == 0f) return 0f
    return when (max) {
        c.red -> 60f * (((c.green - c.blue) / d + 6f) % 6f)
        c.green -> 60f * ((c.blue - c.red) / d + 2f)
        else -> 60f * ((c.red - c.green) / d + 4f)
    }
}

private fun hueGap(a: Float, b: Float): Float = Math.abs(a - b).let { if (it > 180f) 360f - it else it }

/** One option of a part's dropdown: the value, its words, and the presets that use it. */
data class PartChoice(val id: String, val label: String, val presets: List<String>)

/** The distinct values the presets give [part], each with the presets that share it; computed from [PRESETS], so it splits by itself when a preset changes a value. */
fun partChoices(part: Part): List<PartChoice> =
    PRESETS.entries.groupBy({ it.value.getValue(part) }, { it.key }).map { (id, presets) -> PartChoice(id, part.options.getValue(id), presets) }

/** `Light`, `Light and Dark`, `Light, Dark and Mono`. */
fun joinNames(names: List<String>): String = if (names.size < 2) names.joinToString() else names.dropLast(1).joinToString(", ") + " and " + names.last()

/** The words of a dropdown option: `Black (AMOLED)`. */
fun PartChoice.words(): String = "$label (${joinNames(presets.map { PRESET_LABELS.getValue(it) })})"

/** Whether a stat tile wears the card's outline: always, except a tile in Linen's own grey under the hairline, which goes without. */
fun Look.tileOutlined(): Boolean = tile == raised || divider == null

/** Whether the tinted Today card's outline is the accent's: only while the look's outline is the default one; an ink or hairline outline is the same on every card. */
fun Look.heroOutlineAccent(): Boolean = heroTint > 0f && outlineWidth > 0f && outline == null

/** The look keys Day & night keeps through Reset to preset besides the preset itself. */
val DAYNIGHT_KEYS = setOf("theme.day", "theme.night", "theme.switch", "theme.dayFrom", "theme.nightFrom", "theme.lat")

/** The stored look after Reset to preset, from the look keys [before]: the preset (and for Day & night its day and night presets and switch); every mix, override and slider cleared. */
fun resetLookKeys(before: Map<String, String>): Map<String, String> =
    before.filterKeys { it == "theme.preset" || (before["theme.preset"] == "daynight" && it in DAYNIGHT_KEYS) }.ifEmpty { mapOf("theme.preset" to DEFAULT_PRESET) }

/** What the shared pieces read: the card, the button, the tab bar and the chart all follow it. */
val LocalLook = staticCompositionLocalOf { resolveLook(DEFAULT_PRESET) }

/** The black tab bar's fill and its unselected icons (the selected one is white). */
val BlackBar = Color(0xFF121212)
val BlackBarQuiet = Color(0xFF8E8C87)

/** Every kv key that makes up the look besides the `theme.mix.` family: the base preset, the overrides, Mono's sliders and the Day & night rule. */
val LOOK_KEYS = listOf(
    "theme.preset", "accent", "palette", "background", "theme", "theme.iconAccent",
    "mono.paper", "mono.ink", "mono.texture", "mono.negative", "mono.trueInvert",
    "theme.cbsafe", "theme.day", "theme.night", "theme.switch", "theme.dayFrom", "theme.nightFrom", "theme.lat",
)

/** The stored values that make up the look in [options]: [LOOK_KEYS] and every `theme.mix.` key. */
fun lookKeysOf(options: Map<String, String>): Map<String, String> =
    options.filterKeys { it in LOOK_KEYS || it.startsWith("theme.mix.") }

/** A look the user saved: [name] and the stored keys that reproduce it exactly. */
data class SavedPreset(val name: String, val keys: Map<String, String>)

/** Kv `theme.saved`: entries joined by `,`, each the URL-encoded name, a space, then `key=value` pairs joined by `&` (all URL-encoded), so any characters are safe. */
fun encodeSaved(list: List<SavedPreset>): String = list.joinToString(",") { p ->
    enc(p.name) + " " + p.keys.entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
}

fun decodeSaved(text: String?): List<SavedPreset> {
    if (text.isNullOrEmpty()) return emptyList()
    return text.split(",").mapNotNull { entry ->
        val space = entry.indexOf(' ')
        if (space < 0) return@mapNotNull null
        val keys = LinkedHashMap<String, String>()
        for (pair in entry.substring(space + 1).split("&")) {
            val eq = pair.indexOf('=')
            if (eq > 0) keys[dec(pair.substring(0, eq))] = dec(pair.substring(eq + 1))
        }
        SavedPreset(dec(entry.substring(0, space)), keys)
    }
}

/** How Day & night names a saved look: `saved:` and its name, beside the built-in preset ids. */
const val SAVED_REF = "saved:"

/** The saved looks Day & night can switch to: every one but a look that is itself Day & night, which would point at itself. */
fun dayNightSaved(list: List<SavedPreset>): List<SavedPreset> = list.filter { it.keys["theme.preset"] != "daynight" }

/** A Day & night choice as stored ([value]): a built-in preset id, or `saved:<name>` of a look in [saved] that Day & night can use; anything else is [fallback]. */
fun dayNightChoice(value: String?, saved: List<SavedPreset>, fallback: String): String = when {
    value == null -> fallback
    value in PRESETS -> value
    value.startsWith(SAVED_REF) && dayNightSaved(saved).any { it.name == value.removePrefix(SAVED_REF) } -> value
    else -> fallback
}

/** The stored options with the look in them replaced by [saved]'s keys: what the app would hold if that look were chosen. */
fun withSavedLook(options: Map<String, String>, saved: SavedPreset): Map<String, String> =
    options.filterKeys { it !in LOOK_KEYS && !it.startsWith("theme.mix.") } + saved.keys

/** The saved preset the stored look is exactly, if any. */
fun matchingSaved(list: List<SavedPreset>, options: Map<String, String>): SavedPreset? {
    val now = lookKeysOf(options)
    return list.firstOrNull { it.keys == now }
}

private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
private fun dec(s: String) = java.net.URLDecoder.decode(s, "UTF-8")

/** The words on a fill in the text colour (Linen's black main button, its main header circle): white by day, the page on a dark look, where the text is near-white. */
val Look.onInk: Color get() = if (dark) page else Color.White

/** A ring's track inside a grey tile: white by day, the page on a dark look. */
val Look.ringTrack: Color get() = if (dark) page else Color.White

/** The halo round a chart's last point, which sits on the card: white by day, the card colour on a dark look. */
val Look.chartHalo: Color get() = if (dark) raised else Color.White
