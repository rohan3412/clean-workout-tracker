package clean.core

import kotlin.random.Random

/*
 * Template colours (Settings, Workout, Template colours). A template may carry one of ten colours, shown as a dot on the Today card, on
 * History rows and on the activity calendar. The rules here are pure: which colour a template gets, and which templates a scope covers.
 */

/** How the colours are given out. */
enum class TemplateColorMode(val key: String, val label: String) {
    OFF("off", "Off"),
    RANDOM("random", "Random"),
    MANUAL("manual", "Manual"),
}

/** The stored `tpl.colors:<profileId>`; absent or unknown is off. */
fun templateColorModeOf(stored: String?): TemplateColorMode = TemplateColorMode.entries.firstOrNull { it.key == stored } ?: TemplateColorMode.OFF

/** The hue, in degrees, of each of the ten colours; the app turns each into a dark-theme and a light-theme shade. */
val TEMPLATE_HUES: List<Double> = listOf(0.0, 24.0, 45.0, 80.0, 140.0, 172.0, 195.0, 215.0, 265.0, 325.0)

val TEMPLATE_COLOR_COUNT: Int get() = TEMPLATE_HUES.size

/** A colour stored as `none`: the user took it off a template, which Random then leaves alone. */
const val NO_TEMPLATE_COLOR = -1

/** The stored `tpl.color:<profileId>:<template id>`: an index into the ten colours, [NO_TEMPLATE_COLOR] for `none`, null when nothing is stored (or the text is no colour). */
fun parseTemplateColor(stored: String?): Int? = when {
    stored == null -> null
    stored == "none" -> NO_TEMPLATE_COLOR
    else -> stored.toIntOrNull()?.takeIf { it in 0 until TEMPLATE_COLOR_COUNT }
}

fun encodeTemplateColor(index: Int): String = if (index == NO_TEMPLATE_COLOR) "none" else index.toString()

/** Which templates Random colours: all, the templates in some folders (by folder name), or some chosen templates (by id). */
data class TemplateColorScope(val kind: Kind = Kind.ALL, val items: List<String> = emptyList()) {
    enum class Kind(val key: String) { ALL("all"), FOLDERS("folders"), SOME("some") }

    /** Whether Random colours the template with this [id] in [folder]. */
    fun covers(id: Long, folder: String): Boolean = when (kind) {
        Kind.ALL -> true
        Kind.FOLDERS -> folder.isNotEmpty() && items.any { it.equals(folder, ignoreCase = true) }
        Kind.SOME -> id.toString() in items
    }

    fun encode(): String = if (kind == Kind.ALL) "all" else "${kind.key}:${items.joinToString(";")}"
}

/** The stored `tpl.colors.scope:<profileId>` (`all`, `folders:Push;Pull`, `some:12;15`); anything else is all. */
fun parseTemplateColorScope(stored: String?): TemplateColorScope {
    if (stored == null) return TemplateColorScope()
    val kind = TemplateColorScope.Kind.entries.firstOrNull { it != TemplateColorScope.Kind.ALL && stored.startsWith("${it.key}:") } ?: return TemplateColorScope()
    return TemplateColorScope(kind, stored.substringAfter(':').split(';').filter { it.isNotEmpty() })
}

/** The distance in degrees between two hues, round the circle. */
fun hueDistance(a: Double, b: Double): Double {
    val d = Math.abs(a - b) % 360.0
    return if (d > 180.0) 360.0 - d else d
}

/** The hue of a colour given as red, green, blue in 0..1, in degrees; 0 for a grey. */
fun hueOfRgb(r: Double, g: Double, b: Double): Double {
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    if (delta == 0.0) return 0.0
    val h = when (max) {
        r -> ((g - b) / delta) % 6.0
        g -> (b - r) / delta + 2.0
        else -> (r - g) / delta + 4.0
    } * 60.0
    return if (h < 0) h + 360.0 else h
}

/** The index of the template colour whose hue is nearest [accentHue]: the one Random never gives out, so a dot is never mistaken for the accent. */
fun nearestToAccent(accentHue: Double): Int = TEMPLATE_HUES.indices.minByOrNull { hueDistance(TEMPLATE_HUES[it], accentHue) }!!

/**
 * The colour Random gives a template: the one used by the fewest of [used] (the colours the other templates in scope already
 * hold; [NO_TEMPLATE_COLOR] entries do not count), never [excluded], a tie broken by [random]. So no colour comes round
 * twice until every colour has been used.
 */
fun pickTemplateColor(used: List<Int>, excluded: Int?, random: Random = Random.Default): Int {
    val counts = IntArray(TEMPLATE_COLOR_COUNT)
    for (u in used) if (u in counts.indices) counts[u]++
    val allowed = counts.indices.filter { it != excluded }
    val least = allowed.minOf { counts[it] }
    return allowed.filter { counts[it] == least }.random(random)
}

/**
 * The colours Random must now give out: for each template in scope (of [templates], as `id to folder`) with nothing stored,
 * in order, the pick from the colours held so far, counting the ones just given. [stored] is every stored colour by template id.
 */
fun assignTemplateColors(
    templates: List<Pair<Long, String>>,
    stored: Map<Long, Int>,
    scope: TemplateColorScope,
    excluded: Int?,
    random: Random = Random.Default,
): Map<Long, Int> {
    val inScope = templates.filter { scope.covers(it.first, it.second) }
    val held = inScope.mapNotNull { stored[it.first] }.toMutableList()
    val given = LinkedHashMap<Long, Int>()
    for ((id, _) in inScope) {
        if (id in stored) continue
        val pick = pickTemplateColor(held, excluded, random)
        given[id] = pick
        held += pick
    }
    return given
}
