package clean.core

/** How a rest announces its end (Settings, Workout, Feedback; kv `feedback.restEnd`): the bell, the buzzes of the last five seconds, or both. [OFF] only comes from migrating a phone that had switched both old options off. */
enum class RestEndFeedback(val key: String, val sound: Boolean, val vibrate: Boolean) {
    SOUND("sound", true, false),
    VIBRATE("vibrate", false, true),
    BOTH("both", true, true),
    OFF("off", false, false),
}

/**
 * The rest-end feedback from the stored choice; when none is stored yet, from the two older switches (kv `rest.sound` and
 * `rest.alert`, each on unless "off"), so a phone keeps what it had chosen.
 */
fun restEndFeedback(stored: String?, oldSound: String?, oldAlert: String?): RestEndFeedback {
    RestEndFeedback.entries.firstOrNull { it.key == stored }?.let { return it }
    val sound = oldSound != "off"
    val vibrate = oldAlert != "off"
    return when {
        sound && vibrate -> RestEndFeedback.BOTH
        sound -> RestEndFeedback.SOUND
        vibrate -> RestEndFeedback.VIBRATE
        else -> RestEndFeedback.OFF
    }
}

/** The rest lengths offered as chips while resting (kv `rest.presets`, seconds joined by commas). */
val DEFAULT_REST_PRESETS = listOf(60, 90, 120, 180)

/** How many preset chips there are. */
const val REST_PRESET_COUNT = 4

/** Up to [REST_PRESET_COUNT] lengths from stored text; anything unreadable, or outside 1 s to 30 min, falls back to the default list. */
fun parseRestPresets(text: String?): List<Int> {
    val parts = text?.split(',')?.map { it.trim().toIntOrNull() }.orEmpty()
    if (parts.isEmpty() || parts.size > REST_PRESET_COUNT || parts.any { it == null || it !in 1..1800 }) return DEFAULT_REST_PRESETS
    return parts.filterNotNull()
}
