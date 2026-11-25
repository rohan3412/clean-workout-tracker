package clean.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import clean.app.platform.LocalPlatform
import clean.core.AutoBackupFrequency
import clean.core.OneRmFormula
import clean.core.currentLocalDateTime
import clean.core.relativeTime
import clean.core.targetFor
import clean.core.weekStart
import clean.core.time.*
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate

/**
 * Round 37: Settings is nine short pages. Their names are also the page ids that the search and the Profile and Home buttons use.
 * A page is a list of sections (the lazy list's items, whose keys the search leads to); the jump chips of a page are made of them.
 */
const val PAGE_WORKOUT = "Workout"
const val PAGE_TRAINING = "Training"
const val PAGE_LOOK = "Look"
const val PAGE_SOUND = "Sound and feel"
const val PAGE_PRIVACY = "Photos and privacy"
const val PAGE_BACKUP = "Backup and sync"
const val PAGE_TRANSFER = "Import, export, Health Connect"
const val PAGE_LIBRARY = "Library upkeep"
const val PAGE_ABOUT = "About and help"

/** A jump chip: its label and the sections it leads to (it scrolls to the first; it is lit while any of them is the one in view). */
class SettingsChip(val label: String, val keys: List<String>)

/** A page: its icon, its sections in order and its chips. A chip whose sections are all absent (Health Connect on a phone without it) is left out. */
class SettingsPageDef(val id: String, val icon: ImageVector, val sections: List<String>, val chips: List<SettingsChip>)

val SETTINGS_PAGE_DEFS: List<SettingsPageDef> = listOf(
    SettingsPageDef(
        PAGE_WORKOUT, CleanIcons.DumbbellLine, listOf("s-rest", "s-autofill", "s-during"),
        listOf(SettingsChip("Rest", listOf("s-rest")), SettingsChip("Auto-fill", listOf("s-autofill")), SettingsChip("In a workout", listOf("s-during"))),
    ),
    SettingsPageDef(
        PAGE_TRAINING, CleanIcons.ChartLine, listOf("s-consistency", "s-onerm", "s-weeksets"),
        listOf(SettingsChip("Weekly target", listOf("s-consistency")), SettingsChip("Estimated 1RM", listOf("s-onerm")), SettingsChip("Weekly sets", listOf("s-weeksets"))),
    ),
    SettingsPageDef(
        PAGE_LOOK, CleanIcons.LookLine, listOf("ap-mode", "ap-theme", "ap-black", "ap-dock", "ap-text", "ap-cbsafe", "ap-sticky"),
        listOf(
            SettingsChip("Theme", listOf("ap-mode", "ap-theme")), SettingsChip("Background", listOf("ap-black")), SettingsChip("Dock", listOf("ap-dock")),
            SettingsChip("Text", listOf("ap-text")), SettingsChip("Colours", listOf("ap-cbsafe", "ap-sticky")),
        ),
    ),
    SettingsPageDef(
        PAGE_SOUND, CleanIcons.SoundLine, listOf("s-rest-end", "s-rest-sound", "ap-haptic", "ap-motion", "ap-glass", "ap-performance"),
        listOf(
            SettingsChip("Sounds", listOf("s-rest-end", "s-rest-sound")), SettingsChip("Haptics", listOf("ap-haptic")),
            SettingsChip("Motion", listOf("ap-motion", "ap-glass")), SettingsChip("Performance", listOf("ap-performance")),
        ),
    ),
    SettingsPageDef(PAGE_PRIVACY, CleanIcons.LockLine, listOf("ap-blur"), emptyList()),
    SettingsPageDef(
        PAGE_BACKUP, CleanIcons.Refresh, listOf("s-autobackup", "s-backup", "s-delete"),
        listOf(SettingsChip("Automatic", listOf("s-autobackup")), SettingsChip("Backup file", listOf("s-backup")), SettingsChip("Delete", listOf("s-delete"))),
    ),
    SettingsPageDef(
        PAGE_TRANSFER, CleanIcons.TransferLine, listOf("s-import", "s-export", "s-health"),
        listOf(SettingsChip("Import", listOf("s-import")), SettingsChip("Export", listOf("s-export")), SettingsChip("Health Connect", listOf("s-health"))),
    ),
    SettingsPageDef(
        PAGE_LIBRARY, CleanIcons.BooksLine, listOf("s-templates", "s-renamed", "s-tidy", "s-animations"),
        listOf(SettingsChip("Templates", listOf("s-templates", "s-renamed")), SettingsChip("Tidy up", listOf("s-tidy")), SettingsChip("Animations", listOf("s-animations"))),
    ),
    SettingsPageDef(
        PAGE_ABOUT, CleanIcons.Info, listOf("s-changes", "s-source", "s-credits", "s-tips"),
        listOf(SettingsChip("What's new", listOf("s-changes")), SettingsChip("Source", listOf("s-source")), SettingsChip("Licences", listOf("s-credits")), SettingsChip("Tips", listOf("s-tips"))),
    ),
)

/** The main page's four groups of rows, as in the mockup. */
val SETTINGS_MAIN_GROUPS: List<List<String>> = listOf(
    listOf(PAGE_WORKOUT, PAGE_TRAINING),
    listOf(PAGE_LOOK, PAGE_SOUND, PAGE_PRIVACY),
    listOf(PAGE_BACKUP, PAGE_TRANSFER, PAGE_LIBRARY),
    listOf(PAGE_ABOUT),
)

fun settingsPageDef(id: String): SettingsPageDef? = SETTINGS_PAGE_DEFS.firstOrNull { it.id == id }

/** A page shows jump chips when three or more of its chips lead somewhere on this phone. */
const val CHIPS_FROM = 3

/**
 * What Settings is set to now, in plain words: the main page's line for each page ([page]) and the value a search result carries ([section]). Made by
 * [rememberSettingsNow], which reads the stored options, so whatever shows it is drawn again when a setting changes.
 */
class SettingsNow(
    private val o: Map<String, String>, private val model: AppModel, private val folder: String?, private val animations: Int, private val hasHealth: Boolean,
    private val renamed: Int, private val now: kotlinx.datetime.LocalDateTime, private val look: clean.app.ui.theme.Look, private val goal: Int,
    private val templates: Int, private val version: String?,
) {
    private fun on(key: String, default: Boolean) = when (o[key]) { "on" -> true; "off" -> false; else -> default }
    private fun joined(vararg parts: String?) = parts.filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ")

    private val rest get() = "Rest ${restLabel(o["rest.seconds"]?.toIntOrNull() ?: 120)}"
    private val warm get() = o["auto.warm"]?.toIntOrNull() ?: 1
    private val working get() = o["auto.sets"]?.toIntOrNull() ?: 2
    private val drops get() = o["auto.drops"]?.toIntOrNull() ?: 1
    private val oneRm get() = model.oneRm.value
    private val formula get() = oneRm.formula.let { if (it == OneRmFormula.HYBRID) "Hybrid" else it.label }
    private val mode get() = mapOf("dark" to "Dark", "light" to "Light", "phone" to "Phone")[model.theme.value] ?: "Dark"
    private val background get() = resolveBackground(model.background.value, look.themeName).name
    private val tone get() = RestTone.of(o["rest.tone"]).label
    private val haptics get() = if (on("haptic.ticks", true)) "haptics on" else "haptics off"
    private val blurred get() = if (on("photos.blur", false)) "Blurred" else "Not blurred"
    private val locked get() = if (on("photos.lock", false)) "locked" else "not locked"
    private val last: String? get() = o[AutoBackupKeys.LAST]?.let { l -> parseDate(l)?.let { relativeTime(it, now) } ?: prettyDate(l) }
    private val schedule get() = AutoBackupFrequency.of(o[AutoBackupKeys.FREQ])?.label?.lowercase()
    private val healthText get() = if (!hasHealth) null else if (on("health", false)) "Health Connect on" else "Health Connect off"

    /** The main page's line for the page [id]: the most telling values first. */
    fun page(id: String): String = when (id) {
        PAGE_WORKOUT -> joined(rest, "auto-fill $warm warm-up, $working sets, $drops drop")
        PAGE_TRAINING -> "$goal a week · 1RM by $formula, up to ${oneRm.maxReps} reps"
        PAGE_LOOK -> joined("${look.themeName} · $background · $mode", (o["textSize"] ?: "Medium").takeIf { it != "Medium" }?.let { "text ${it.lowercase()}" })
        PAGE_SOUND -> joined(tone, haptics, model.performance.value.label)
        PAGE_PRIVACY -> "$blurred · $locked"
        PAGE_BACKUP -> joined(last, folder, schedule).ifEmpty { "Not set up" }
        PAGE_TRANSFER -> joined(healthText, o["health.last"]?.takeIf { hasHealth && on("health", false) && it.isNotEmpty() }?.let { "last sync $it" }).ifEmpty { "Import and export workouts" }
        PAGE_LIBRARY -> joined(plural(templates, "template"), if (renamed > 0) "$renamed renamed" else null, if (animations > 0) "$animations animations" else null)
        PAGE_ABOUT -> joined(version?.let { "Clean $it" } ?: "Clean", "what's new, source, tips")
        else -> ""
    }

    /** The value a search result for the section [key] shows beside its page and group; null when there is nothing short to say. */
    fun section(key: String): String? = when (key) {
        "s-rest" -> restLabel(o["rest.seconds"]?.toIntOrNull() ?: 120)
        "s-rest-end" -> restEndWord()
        "s-rest-sound" -> tone
        "s-autofill" -> "$warm warm-up, $working working, $drops drop"
        "s-consistency" -> "$goal a week"
        "s-onerm" -> "$formula, up to ${oneRm.maxReps} reps"
        "s-weeksets" -> model.defaultSetRange.value.let { "${it.min} to ${it.max}" }
        "ap-mode" -> mode
        "ap-theme" -> look.themeName
        "ap-black" -> background
        "ap-dock" -> if (on("dock.left", false)) "plus on the left" else "plus on the right"
        "ap-text" -> o["textSize"] ?: "Medium"
        "ap-cbsafe" -> if (on("theme.cbsafe", false)) "on" else "off"
        "ap-haptic" -> if (on("haptic.ticks", true)) "on" else "off"
        "ap-motion" -> if (on("ui.motion", true)) "on" else "off"
        "ap-glass" -> if (on("glass.reduce", false)) "on" else "off"
        "ap-performance" -> model.performance.value.label
        "ap-blur" -> "$blurred, $locked"
        "s-autobackup" -> joined(last, folder, schedule).ifEmpty { "not set up" }
        "s-health" -> healthText?.removePrefix("Health Connect ")
        "s-templates" -> plural(templates, "template")
        "s-renamed" -> if (renamed > 0) "$renamed" else null
        "s-animations" -> if (animations == 0) "none" else "$animations"
        "s-changes" -> version
        else -> null
    }

    private fun word(key: String, default: Boolean) = if (on(key, default)) "on" else "off"

    /** The settings of the section [key] with their values, a line each, for the peek a hold on a jump chip shows. */
    fun lines(key: String): List<String> = when (key) {
        "s-rest" -> listOf(
            "Default rest · ${restLabel(o["rest.seconds"]?.toIntOrNull() ?: 120)}", "Rest between sides · ${restLabel(o["rest.sides"]?.toIntOrNull() ?: 60)}",
            "Alert outside the app · ${word("rest.background", false)}", "Go back to the workout when the rest ends · ${word("rest.return", true)}",
        )
        "s-rest-end" -> listOf("When a rest ends · ${restEndWord()}")
        "s-rest-sound" -> listOf("Rest sound · $tone", "Finish chime · ${word("finish.chime", false)}")
        "s-autofill" -> listOf("Warm-up sets · $warm", "Working sets · $working", "Drop sets · $drops")
        "s-during" -> listOf(
            "Suggest what to try · ${word("workout.hint", true)}", "Celebrate on finishing · ${word("reward.celebrate", true)}", "Summary · ${word("workout.summary", true)}",
            "RPE on the number pad · ${word("workout.rpe", true)}", "Show the next set · ${word("workout.highlight", true)}", "Shrink finished exercises · ${word("workout.compact", true)}",
            "Workout on the lock screen · ${word("workout.notif", true)}",
        )
        "s-consistency" -> listOf("Weekly target · $goal a week", "Current split · ${word("split.on", false)}")
        "s-onerm" -> listOf("Formula · $formula", "Most reps it is used for · ${oneRm.maxReps}", "Factor in RPE · ${word("onerm.rpe", false)}")
        "ap-black" -> listOf("Background · $background", "Pure black background · ${word(clean.core.LOOK_BLACK_KEY, false)}")
        "ap-blur" -> listOf("Blur photos · ${word("photos.blur", false)}", "Lock photos · ${word("photos.lock", false)}")
        "s-autobackup" -> listOf("Last backup · ${last ?: "none yet"}", "Backup folder · ${folder ?: "not chosen"}", "Schedule · ${schedule ?: "off"}")
        else -> listOfNotNull(section(key)?.let { v -> "${SETTINGS_INDEX.firstOrNull { it.key == key }?.group ?: key} · $v" })
    }

    private fun restEndWord(): String = when (clean.core.restEndFeedback(o["feedback.restEnd"], o["rest.sound"], o["rest.alert"])) {
        clean.core.RestEndFeedback.SOUND -> "sound"
        clean.core.RestEndFeedback.VIBRATE -> "buzz"
        else -> "sound and buzz"
    }
}

/** Reads what [SettingsNow] needs; the "last backup" words are worked out again every minute while this is composed. */
@Composable
fun rememberSettingsNow(model: AppModel): SettingsNow {
    val platform = LocalPlatform.current
    val options by model.options.collectAsState()
    // These are read here so that a change in any of them draws the lines again.
    val oneRm by model.oneRm.collectAsState()
    val theme by model.theme.collectAsState()
    val background by model.background.collectAsState()
    val performance by model.performance.collectAsState()
    val look by model.look.collectAsState()
    val range by model.defaultSetRange.collectAsState()
    val goals = model.targets.collectAsState().value.orEmpty()
    val templates by model.templates.collectAsState()
    val profile by model.profile.collectAsState()
    var now by remember { mutableStateOf(currentLocalDateTime()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = currentLocalDateTime() } }
    var renamed by remember { mutableStateOf(0) }
    LaunchedEffect(profile?.id, options) { renamed = model.renameLog().size }
    val folderPath = options[AutoBackupKeys.FOLDER].orEmpty()
    val folder = remember(folderPath) { if (folderPath.isEmpty()) null else runCatching { platform.folderName(folderPath) }.getOrNull() ?: "chosen folder" }
    val goal = targetFor(goals, weekStart(LocalDate.now()))
    return remember(options, oneRm, theme, background, performance, look, range, goals, templates, now, renamed, folder) {
        SettingsNow(options, model, folder, platform.animationCount(), platform.hasHealth, renamed, now, look, goal, templates.size, platform.appVersionName)
    }
}

/** The value a search result shows: the group's own for a group, the one setting's for a setting of it (the line the chip's peek has for it); null when there is none. */
fun SettingsNow.valueOf(place: SettingPlace): String? =
    if (place.title == place.group) section(place.key)
    else lines(place.key).firstOrNull { it.startsWith(place.title + " · ") }?.substringAfter(" · ")
