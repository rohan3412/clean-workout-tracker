package clean.core

/**
 * The 1.1.0 look: Dark, Light or Phone, a Pure black background switch (Dark only) and one accent of six. This file is the pure part:
 * what the stored options of an older version turn into. The app writes the result once at start-up ([migrateLook]) and then ignores
 * the old keys (`theme.preset`, `theme.mix.*`, `accent`, `palette`, `background`, `theme`, `mono.*`, `theme.day` ...).
 */
const val LOOK_THEME_KEY = "look.theme"
const val LOOK_BLACK_KEY = "look.black"
const val LOOK_ACCENT_KEY = "look.accent"

/** Written with the new keys so the migration never runs twice, whatever the user changes afterwards. */
const val LOOK_DONE_KEY = "look.v1"

/** The theme choices as stored: `dark`, `light`, `phone`. */
val LOOK_THEMES = listOf("dark", "light", "phone")

/** The six accents, blue first and the default. */
val ACCENT_CHOICES = listOf("Blue", "Teal", "Green", "Orange", "Pink", "Purple")

/** A colour's hue in degrees, 0 to 360 (0 for a grey). */
fun hueOfRgb(r: Int, g: Int, b: Int): Float {
    val max = maxOf(r, g, b)
    val d = (max - minOf(r, g, b)).toFloat()
    if (d == 0f) return 0f
    return when (max) {
        r -> 60f * (((g - b) / d + 6f) % 6f)
        g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
}

/** The hue of each accent that was ever on offer (its shade on a dark page), for finding the nearest of the six. */
private val ACCENT_HUES: Map<String, Float> = mapOf(
    "Blue" to hueOfRgb(0x8A, 0xB4, 0xFF), "Teal" to hueOfRgb(0x5F, 0xD4, 0xD0), "Green" to hueOfRgb(0x6F, 0xD3, 0x9A),
    "Orange" to hueOfRgb(0xFF, 0xB0, 0x68), "Pink" to hueOfRgb(0xFF, 0x9E, 0xC4), "Purple" to hueOfRgb(0xC4, 0xA5, 0xFF),
    "Red" to hueOfRgb(0xFF, 0x8A, 0x80),
)

private fun hueGap(a: Float, b: Float): Float = Math.abs(a - b).let { if (it > 180f) 360f - it else it }

private fun nearestOfSix(hue: Float): String = ACCENT_CHOICES.minBy { hueGap(hue, ACCENT_HUES.getValue(it)) }

/**
 * The accent of the six an old stored accent becomes: itself when it is one of the six; otherwise the nearest by hue (an old Red, or a
 * colour typed as `#AARRGGBB` or `#RRGGBB`); Blue for a grey, for Ink, or for anything unreadable.
 */
fun accentFor(old: String?): String {
    val name = old?.trim().orEmpty()
    if (name in ACCENT_CHOICES) return name
    if (name == "Red") return nearestOfSix(ACCENT_HUES.getValue("Red"))
    if (!name.startsWith("#")) return "Blue"
    val hex = name.drop(1).let { if (it.length == 8) it.drop(2) else it }
    val rgb = hex.takeIf { it.length == 6 }?.toIntOrNull(16) ?: return "Blue"
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF
    if (maxOf(r, g, b) - minOf(r, g, b) < 30) return "Blue"
    return nearestOfSix(hueOfRgb(r, g, b))
}

/** What an old look became: the theme (`dark`, `light`, `phone`), whether the page is pure black, and the accent. */
data class MigratedLook(val theme: String, val pureBlack: Boolean, val accent: String)

/**
 * The 1.1.0 look for the old stored [options]. The preset decides: `amoled` is Dark with pure black, `dark` Dark, `light` Light, `mono`
 * and `linen` Dark, `daynight` Phone; no preset (or one that is unknown, such as a saved look) is `amoled`, which was the default.
 * The older single options still on top of a preset are honoured as the user saw them: `theme` (`Light`, `Dark`, `System` become
 * Light, Dark, Phone) and, for a dark look, `background` (`Black` or `Dark grey`). A Light look has no pure black. The accent is
 * [accentFor] of the stored `accent`, else the preset's own (orange in `light` and `linen`, blue otherwise). Never throws.
 */
fun migratedLook(options: Map<String, String>): MigratedLook {
    val preset = options["theme.preset"]?.takeIf { it in setOf("amoled", "dark", "light", "mono", "linen", "daynight") } ?: "amoled"
    var theme = when (preset) { "light" -> "light"; "daynight" -> "phone"; else -> "dark" }
    var black = preset == "amoled"
    if (preset != "daynight") when (options["theme"]) {
        "Light" -> theme = "light"
        "Dark" -> theme = "dark"
        "System" -> theme = "phone"
    }
    if (theme != "light") when (options["background"]) {
        "Black" -> black = true
        "Dark grey" -> black = false
    }
    if (theme == "light") black = false
    val own = if (preset == "light" || preset == "linen") "Orange" else "Blue"
    val accent = options["accent"]?.let { accentFor(it) } ?: own
    return MigratedLook(theme, black, accent)
}

/**
 * The entries to write at start-up, or null when it has already run ([LOOK_DONE_KEY] present). A phone with none of the older look
 * keys (a new install) gets only the marker, so the defaults (Dark, pure black off, Blue) apply.
 */
fun migrateLook(options: Map<String, String>): Map<String, String>? {
    if (LOOK_DONE_KEY in options) return null
    val had = listOf("theme.preset", "accent", "palette", "background", "theme").any { it in options }
    if (!had) return mapOf(LOOK_DONE_KEY to "1")
    val look = migratedLook(options)
    return mapOf(LOOK_THEME_KEY to look.theme, LOOK_BLACK_KEY to if (look.pureBlack) "on" else "off", LOOK_ACCENT_KEY to look.accent, LOOK_DONE_KEY to "1")
}
