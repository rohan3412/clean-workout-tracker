package clean.app

/**
 * One place in Settings a search can lead to: the page it is on, the section's name as shown, other words it may be looked for by,
 * and the key of its item on that page (so the page can be scrolled to it).
 */
data class SettingPlace(val page: String, val title: String, val words: String, val key: String)

/**
 * Every section of Settings, in page order, on the four pages (Workout, Appearance, Data and backup, About and help). Kept beside the
 * pages in Settings.kt: a section added there gets a line here, and a setting removed from there loses its line.
 */
val SETTINGS_INDEX = listOf(
    SettingPlace(PAGE_WORKOUT, "Rest", "default rest between sides rest by set type warm-up failure drop hard set when it ends sound buzz both bell alert outside the app background alarm return countdown presets", "s-rest"),
    SettingPlace(PAGE_WORKOUT, "Training", "weekly target streak workouts per week goal program current split up next rotate rotation folder template suggest what to try celebrate finishing", "s-consistency"),
    SettingPlace(PAGE_WORKOUT, "Estimated 1RM", "one rep max formula epley brzycki hybrid rep limit xrm e1rm rpe factor", "s-onerm"),
    SettingPlace(PAGE_WORKOUT, "Weekly sets per muscle", "volume target range set targets", "s-weeksets"),
    SettingPlace(PAGE_WORKOUT, "Auto-fill sets", "warm-up working drop sets count", "s-autofill"),
    SettingPlace(PAGE_WORKOUT, "In a workout", "summary RPE number pad next set shrink finished exercises lock screen notification reminders train days plates bar", "s-during"),
    SettingPlace(PAGE_APPEARANCE, "Theme", "dark light phone system follow mode night day", "ap-theme"),
    SettingPlace(PAGE_APPEARANCE, "Accent", "colour color blue teal green orange pink purple calendar chart body figure", "ap-accent"),
    SettingPlace(PAGE_APPEARANCE, "Text size", "font size smallest largest bigger smaller", "ap-text"),
    SettingPlace(PAGE_APPEARANCE, "Background", "pure black amoled oled dark page", "ap-black"),
    SettingPlace(PAGE_APPEARANCE, "Transparency", "reduce transparency glass blur solid bars sheets menus contrast see-through", "ap-glass"),
    SettingPlace(PAGE_APPEARANCE, "Colours", "colour-blind safe colorblind deuteranopia protanopia blue orange", "ap-cbsafe"),
    SettingPlace(PAGE_APPEARANCE, "Animations", "animations motion off performance fast battery reduce", "ap-motion"),
    SettingPlace(PAGE_APPEARANCE, "Sticky notes", "sticky note colour band", "ap-sticky"),
    SettingPlace(PAGE_APPEARANCE, "Photos", "blur progress photos", "ap-blur"),
    SettingPlace(PAGE_DATA, "Backup", "back up restore zip everything", "s-backup"),
    SettingPlace(PAGE_DATA, "Import", "strong hevy fitnotes lyfta fitbod csv measurements history", "s-import"),
    SettingPlace(PAGE_DATA, "Export", "strong csv hevy fitnotes json fit share", "s-export"),
    SettingPlace(PAGE_DATA, "Health Connect", "health connect write workouts weigh-ins", "s-health"),
    SettingPlace(PAGE_DATA, "Exercise animations", "animations gif zip import", "s-animations"),
    SettingPlace(PAGE_DATA, "Templates", "export import templates file", "s-templates"),
    SettingPlace(PAGE_DATA, "Renamed exercises", "rename merge undo", "s-renamed"),
    SettingPlace(PAGE_DATA, "Tidy up", "notes variations", "s-tidy"),
    SettingPlace(PAGE_DATA, "Delete everything", "wipe erase reset all data", "s-delete"),
    SettingPlace(PAGE_ABOUT, "Source code", "github open source code repository address mit", "s-source"),
    SettingPlace(PAGE_ABOUT, "What's new", "changes updates versions release notes", "s-changes"),
    SettingPlace(PAGE_ABOUT, "Licences and credits", "licence license credits MuscleMap exercises dataset fonts inter haze", "s-credits"),
    SettingPlace(PAGE_ABOUT, "Tips", "gestures hidden hold swipe tap help", "s-tips"),
)

/** The places whose page, name or words hold every word of [query], best first: a match in the name before one only in the words. */
fun searchSettings(query: String): List<SettingPlace> {
    val terms = query.lowercase().split(' ').filter { it.isNotBlank() }
    if (terms.isEmpty()) return emptyList()
    val hits = SETTINGS_INDEX.filter { p ->
        val text = "${p.page} ${p.title} ${p.words}".lowercase()
        terms.all { it in text }
    }
    return hits.sortedBy { p -> if (terms.all { it in p.title.lowercase() }) 0 else 1 }
}
