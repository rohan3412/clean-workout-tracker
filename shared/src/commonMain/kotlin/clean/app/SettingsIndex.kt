package clean.app

/**
 * One place in Settings a search can lead to: the [page] it is on, the [group] (the heading of the card it is in), the [title] it is shown under (the group's own
 * name, or one setting of it), other words it may be looked for by, and the [key] of its section on that page (so the page can be scrolled to it).
 */
data class SettingPlace(val page: String, val title: String, val words: String, val key: String, val group: String = title)

/**
 * Every group of Settings, and the settings worth finding by name, in page order, on the nine pages (Round 37). Kept beside the pages in Settings.kt and
 * SettingsMap.kt: a section added there gets a line here, and a setting removed from there loses its line.
 */
val SETTINGS_INDEX = listOf(
    SettingPlace(PAGE_WORKOUT, "Rest timer", "default rest between sides rest by set type warm-up failure drop hard set length presets seconds minutes", "s-rest"),
    SettingPlace(PAGE_WORKOUT, "Default rest", "length how long rest timer presets seconds minutes 2:00", "s-rest", "Rest timer"),
    SettingPlace(PAGE_WORKOUT, "Rest between sides", "left right sides unilateral", "s-rest", "Rest timer"),
    SettingPlace(PAGE_WORKOUT, "Rest by set type", "warm-up failure drop set hard set rest after a drop set no rest before a drop set", "s-rest", "Rest timer"),
    SettingPlace(PAGE_WORKOUT, "Alert outside the app", "rest in the notification background alarm countdown notification buzz bell", "s-rest", "Rest in the background"),
    SettingPlace(PAGE_WORKOUT, "Go back to the workout when the rest ends", "return countdown", "s-rest", "Rest in the background"),
    SettingPlace(PAGE_WORKOUT, "Auto-fill sets", "warm-up working drop sets count", "s-autofill"),
    SettingPlace(PAGE_WORKOUT, "In a workout", "summary RPE number pad next set shrink finished exercises lock screen notification reminders train days plates bar", "s-during"),
    SettingPlace(PAGE_WORKOUT, "Suggest what to try", "hint last time best set beat your best progression", "s-during", "In a workout"),
    SettingPlace(PAGE_WORKOUT, "Celebrate on finishing", "target reached badge shield chime summary", "s-during", "In a workout"),
    SettingPlace(PAGE_WORKOUT, "Workout on the lock screen", "notification lock screen behind", "s-during", "In a workout"),
    SettingPlace(PAGE_WORKOUT, "Remind me to train on", "reminders days workout day time", "s-during", "In a workout"),
    SettingPlace(PAGE_WORKOUT, "Bar and plates", "plate calculator barbell kit", "s-during", "In a workout"),
    SettingPlace(PAGE_TRAINING, "Weekly target", "weekly target streak workouts per week goal program current split up next rotate rotation folder template good week", "s-consistency"),
    SettingPlace(PAGE_TRAINING, "Program", "weekly plan templates rest days", "s-consistency", "Weekly target"),
    SettingPlace(PAGE_TRAINING, "Current split", "rotate up next one folder", "s-consistency", "Weekly target"),
    SettingPlace(PAGE_TRAINING, "Estimated 1RM", "one rep max formula epley brzycki hybrid rep limit xrm e1rm rpe factor", "s-onerm"),
    SettingPlace(PAGE_TRAINING, "Weekly sets per muscle", "volume target range set targets", "s-weeksets"),
    SettingPlace(PAGE_LOOK, "Mode", "dark light phone system follow night day", "ap-mode"),
    SettingPlace(PAGE_LOOK, "Theme", "accent colour color graphite slate crimson ember saffron green teal lagoon cobalt iris orchid rose swatch calendar chart body figure", "ap-theme"),
    SettingPlace(PAGE_LOOK, "Background", "background wallpaper theme's own pattern grid dots topo pools studio grain blueprint marks prism slant ripple tide beams pulse paper corner photo frosted glass pure black amoled oled dark page", "ap-black"),
    SettingPlace(PAGE_LOOK, "Pure black background", "amoled oled black page cards", "ap-black", "Background"),
    SettingPlace(PAGE_LOOK, "Dock", "dock plus button left right hand", "ap-dock"),
    SettingPlace(PAGE_LOOK, "Text size", "font size smallest largest bigger smaller", "ap-text"),
    SettingPlace(PAGE_LOOK, "Colour-blind safe colours", "colour-blind safe colorblind deuteranopia protanopia blue orange", "ap-cbsafe", "Colours"),
    SettingPlace(PAGE_LOOK, "Sticky notes", "sticky note colour band", "ap-sticky"),
    SettingPlace(PAGE_SOUND, "When a rest ends", "sound buzz both vibrate alert feedback rest end", "s-rest-end"),
    SettingPlace(PAGE_SOUND, "Rest sound", "rest sound tone bell deep bell gong soft tone wood block play listen alarm ring", "s-rest-sound"),
    SettingPlace(PAGE_SOUND, "Finish chime", "finish chime workout finished soft chime", "s-rest-sound", "Finishing"),
    SettingPlace(PAGE_SOUND, "Haptic ticks", "haptic tick buzz vibration vibrate segmented calendar day feedback set done record rest undo", "ap-haptic"),
    SettingPlace(PAGE_SOUND, "Animations", "animations motion off performance fast battery reduce", "ap-motion"),
    SettingPlace(PAGE_SOUND, "Reduce transparency", "transparency glass blur solid bars sheets menus contrast see-through", "ap-glass", "Transparency"),
    SettingPlace(PAGE_SOUND, "Performance", "performance slow phone battery light balanced full blur glass animations lag low memory", "ap-performance"),
    SettingPlace(PAGE_PRIVACY, "Blur photos", "blur photos progress snapshots small views", "ap-blur", "Photos"),
    SettingPlace(PAGE_PRIVACY, "Lock photos", "lock fingerprint face pin pattern private snapshots", "ap-blur", "Photos"),
    SettingPlace(PAGE_BACKUP, "Automatic backup", "back up folder schedule frequency daily weekly last backup sync", "s-autobackup"),
    SettingPlace(PAGE_BACKUP, "Back up now", "sync backup folder now", "s-autobackup", "Automatic backup"),
    SettingPlace(PAGE_BACKUP, "Backup folder", "folder choose where", "s-autobackup", "Automatic backup"),
    SettingPlace(PAGE_BACKUP, "Backup file", "back up restore zip everything", "s-backup"),
    SettingPlace(PAGE_BACKUP, "Restore a backup", "restore replace zip", "s-backup", "Backup file"),
    SettingPlace(PAGE_BACKUP, "Delete everything", "wipe erase reset all data", "s-delete"),
    SettingPlace(PAGE_TRANSFER, "Import", "strong hevy fitnotes lyfta fitbod csv measurements history", "s-import"),
    SettingPlace(PAGE_TRANSFER, "Export", "strong csv hevy fitnotes json fit share rest timers", "s-export"),
    SettingPlace(PAGE_TRANSFER, "Health Connect", "health connect write workouts weigh-ins body fat weight steps", "s-health"),
    SettingPlace(PAGE_LIBRARY, "Templates", "export import templates file", "s-templates"),
    SettingPlace(PAGE_LIBRARY, "Renamed exercises", "rename merge undo", "s-renamed"),
    SettingPlace(PAGE_LIBRARY, "Tidy up", "notes variations", "s-tidy"),
    SettingPlace(PAGE_LIBRARY, "Exercise animations", "animations gif zip import pictures", "s-animations"),
    SettingPlace(PAGE_ABOUT, "What's new", "changes updates versions release notes", "s-changes"),
    SettingPlace(PAGE_ABOUT, "Source code", "github open source code repository address mit", "s-source"),
    SettingPlace(PAGE_ABOUT, "Licences and credits", "licence license credits MuscleMap exercises dataset fonts inter haze", "s-credits"),
    SettingPlace(PAGE_ABOUT, "Tips", "gestures hidden hold swipe tap help", "s-tips"),
)

/** The places whose page, name or words hold every word of [query], best first: a match in the name before one only in the words. */
fun searchSettings(query: String): List<SettingPlace> {
    val terms = query.lowercase().split(' ').filter { it.isNotBlank() }
    if (terms.isEmpty()) return emptyList()
    val hits = SETTINGS_INDEX.filter { p ->
        val text = "${p.page} ${p.group} ${p.title} ${p.words}".lowercase()
        terms.all { it in text }
    }
    return hits.sortedBy { p -> if (terms.all { it in p.title.lowercase() }) 0 else 1 }
}
