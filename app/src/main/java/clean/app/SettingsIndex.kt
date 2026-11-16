package clean.app

/**
 * One place in Settings a search can lead to: the page it is on, the section's name as shown, other words it may be looked for by,
 * and the key of its item on that page (so the page can be scrolled to it).
 */
data class SettingPlace(val page: String, val title: String, val words: String, val key: String)

/** Every section of Settings, in page order. Kept beside the pages in Settings.kt: a section added there gets a line here. */
val SETTINGS_INDEX = listOf(
    SettingPlace("Profile", "This profile", "name units kg lb pounds kilograms height", "s-profile"),
    SettingPlace("Profile", "Profiles", "switch add new delete profile", "s-profiles"),
    SettingPlace("Workout", "Auto-fill sets", "warm-up working drop sets count", "s-autofill"),
    SettingPlace("Workout", "Consistency", "weekly target streak workouts per week goal", "s-consistency"),
    SettingPlace("Workout", "Weekly sets per muscle", "volume target range", "s-weeksets"),
    SettingPlace("Workout", "Estimated 1RM", "one rep max formula epley brzycki hybrid rep limit xrm e1rm rpe factor", "s-onerm"),
    SettingPlace("Workout", "Layout", "set rows compact comfortable focus mode one-handed swipe right left finish delete", "s-layout"),
    SettingPlace("Workout", "Rest timer", "countdown style full screen ring bar hide pill presets notification compact expanded", "s-rest"),
    SettingPlace("Workout", "Feedback", "vibration haptic tick sound bell tone rest end PR celebration glow confetti chime", "s-feedback"),
    SettingPlace("Workout", "Outside the app", "launcher shortcuts home screen icon", "s-outside"),
    SettingPlace("Workout", "Template colours", "colour dots random manual folders", "s-tplcolors"),
    SettingPlace("Workout", "During a workout", "rest length warm-up failure drop between sides background alert alarm extra hard set return summary RPE next set suggest shrink workout in progress bar banner exercise tab", "s-during"),
    SettingPlace("Appearance", "Presets", "theme look light dark amoled mono linen saved preset reset", "ap-preset"),
    SettingPlace("Appearance", "Mono", "paper ink texture grain negative invert", "ap-mono"),
    SettingPlace("Appearance", "Day & night", "day night automatic switch sunrise sunset times phone", "ap-daynight"),
    SettingPlace("Appearance", "Mix and match", "parts outlines card border cards corners shadows font numbers tab bar style buttons accent colour scale icon colour-blind safe", "ap-mix"),
    SettingPlace("Appearance", "Home", "home layout stagger even charts edge inset arrange", "ap-home"),
    SettingPlace("Appearance", "Tab bar", "tab order navigation", "ap-tabs"),
    SettingPlace("Appearance", "Charts", "chart style line bars smooth", "ap-charts"),
    SettingPlace("Appearance", "Customize", "theme dark light system background black grey text size font animations live preview sticky notes blur photos", "ap-customize"),
    SettingPlace("Lists", "Lists", "exercise list calendar library hide built-in", "s-lists"),
    SettingPlace("Data", "Backup", "back up restore zip everything", "s-backup"),
    SettingPlace("Data", "Import", "strong hevy fitnotes lyfta fitbod csv measurements history", "s-import"),
    SettingPlace("Data", "Export", "strong csv hevy fitnotes json fit share", "s-export"),
    SettingPlace("Data", "Health Connect", "health connect write workouts weigh-ins", "s-health"),
    SettingPlace("Data", "Exercise animations", "animations gif zip import", "s-animations"),
    SettingPlace("Data", "Templates", "export import templates file", "s-templates"),
    SettingPlace("Data", "Renamed exercises", "rename merge undo", "s-renamed"),
    SettingPlace("Data", "Tidy up", "notes variations", "s-tidy"),
    SettingPlace("Data", "Delete everything", "wipe erase reset all data", "s-delete"),
    SettingPlace("Credits", "Source code", "github open source code repository mit", "s-source"),
    SettingPlace("Credits", "What's new", "changes updates versions release notes", "s-changes"),
    SettingPlace("Credits", "Credits", "licence license MuscleMap exercises dataset fonts", "s-credits"),
    SettingPlace("Tips", "Tips", "gestures hidden hold swipe tap", "s-tips"),
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
