package clean.core

import clean.core.time.*
import kotlinx.datetime.LocalDate

/** How far back a template counts as "in use" for Up next when the Current split switch is off. */
const val UP_NEXT_DAYS = 28L

/**
 * The template Up next picks when no program decides. With [splitOn] it is [nextTemplate]'s rotation through the [split] folder.
 * Off, the folder is an ordinary one: among the templates used in the last [UP_NEXT_DAYS] days (a workout carries the template's exact
 * name) the one done longest ago; none used in that time, the most recently used; none ever used, the first template (never done).
 * No templates at all gives null (the Quick start card). [skipped] templates are passed over unless that leaves none.
 */
fun upNextTemplate(templates: List<TemplateRef>, workouts: List<Workout>, splitOn: Boolean, split: String, today: LocalDate, skipped: Set<Long> = emptySet()): NextTemplate? {
    if (splitOn) return nextTemplate(templates, workouts, split, today, skipped)
    if (templates.isEmpty()) return null
    val lastDone = HashMap<String, String>()
    for (w in workouts) if (w.day > (lastDone[w.name] ?: "")) lastDone[w.name] = w.day
    val pool = templates.filter { it.id !in skipped }.ifEmpty { templates }
    val since = today.minusDays(UP_NEXT_DAYS).toString()
    val recent = pool.filter { (lastDone[it.name] ?: "") >= since && it.name in lastDone }
    val next = recent.minWithOrNull(compareBy<TemplateRef> { lastDone[it.name] }.thenBy { it.id })
        ?: pool.filter { it.name in lastDone }.maxWithOrNull(compareBy<TemplateRef> { lastDone[it.name] }.thenByDescending { it.id })
        ?: pool.first()
    return NextTemplate(next, lastDone[next.name]?.let { "last done ${agoText(it, today)}" } ?: "never done", false)
}
