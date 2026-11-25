package clean.app.platform

/**
 * What the app was opened to do, as an action for `AppModel.widgetAction`, from a `clean://` link or a quick action:
 * `clean://start`, `clean://start?template=Push`, `clean://weight`, `clean://resume`, `clean://open?tab=history` (also `body`, `home`,
 * `progress`, `library` or `exercises`, `workout` for the Workout sheet, `profile`). [host] is the part after `clean://`; null for anything else.
 */
fun actionOfLink(host: String?, template: String?, tab: String?): String? = when (host) {
    "start" -> template?.let { "templateName:$it" } ?: "start"
    "open" -> tab?.let { "tab:$it" }
    "weight" -> "weight"
    "resume" -> "workout"
    else -> null
}
