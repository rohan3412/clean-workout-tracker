package clean.app

import clean.core.Head
import clean.core.Muscle
import clean.core.Pose

/*
 * The back stack as plain strings, so it can be kept in the saved state across a recreation and the process being killed in the
 * background. A page is its name and its fields, joined by a unit separator; a missing field is a record separator alone.
 * A page that cannot be read back (renamed in a later version, say) is left out.
 */

private const val SEP = '\u001F'
private const val NULL = "\u001E"

private fun field(value: Any?): String = value?.toString() ?: NULL

private fun encode(name: String, vararg fields: Any?): String = (listOf(name) + fields.map(::field)).joinToString(SEP.toString())

fun encodeScreen(screen: Screen): String = when (screen) {
    is Screen.TemplateEdit -> encode("TemplateEdit", screen.id)
    is Screen.WorkoutDetail -> encode("WorkoutDetail", screen.date)
    is Screen.ExerciseDetail -> encode("ExerciseDetail", screen.name, screen.edit, screen.tab)
    is Screen.Summary -> encode("Summary", screen.date)
    is Screen.Week -> encode("Week", screen.monday)
    is Screen.MeasurementDetail -> encode("MeasurementDetail", screen.file)
    is Screen.MuscleDetail -> encode("MuscleDetail", screen.muscle.name)
    is Screen.HeadDetail -> encode("HeadDetail", screen.head.name)
    is Screen.Routine -> encode("Routine", screen.name)
    is Screen.TemplateStats -> encode("TemplateStats", screen.id)
    is Screen.Month -> encode("Month", screen.month)
    is Screen.PrTimeline -> encode("PrTimeline", screen.exercise)
    is Screen.Calendar -> encode("Calendar", screen.day)
    is Screen.Overall -> encode("Overall", screen.exercise)
    is Screen.NoteReview -> encode("NoteReview", screen.exercise)
    is Screen.Photo -> encode("Photo", screen.name, screen.pose?.name)
    is Screen.PhotoCompare -> encode("PhotoCompare", screen.pose?.name)
    // The rest have no fields: their name is the whole of them.
    else -> screen::class.simpleName!!
}

private val OBJECTS: Map<String, Screen> = listOf(
    Screen.Active, Screen.Settings, Screen.ExerciseSetup, Screen.Chart, Screen.Trends, Screen.Bmi, Screen.Trophies, Screen.Program,
    Screen.PersonalBests, Screen.History, Screen.Body, Screen.Measurements, Screen.Profile,
    Screen.Photos, Screen.Correct,
).associateBy { it::class.simpleName!! }

fun decodeScreen(text: String): Screen? = runCatching {
    val parts = text.split(SEP)
    val f = parts.drop(1).map { if (it == NULL) null else it }
    fun s(i: Int) = f[i]!!
    when (parts[0]) {
        "TemplateEdit" -> Screen.TemplateEdit(f[0]?.toLong())
        "WorkoutDetail" -> Screen.WorkoutDetail(s(0))
        "ExerciseDetail" -> Screen.ExerciseDetail(s(0), s(1).toBooleanStrict(), f[2]?.toInt())
        "Summary" -> Screen.Summary(s(0))
        "Week" -> Screen.Week(s(0))
        "MeasurementDetail" -> Screen.MeasurementDetail(s(0))
        "MuscleDetail" -> Screen.MuscleDetail(Muscle.valueOf(s(0)))
        "HeadDetail" -> Screen.HeadDetail(Head.valueOf(s(0)))
        "Routine" -> Screen.Routine(s(0))
        "TemplateStats" -> Screen.TemplateStats(s(0).toLong())
        "Month" -> Screen.Month(s(0))
        "PrTimeline" -> Screen.PrTimeline(s(0))
        "Calendar" -> Screen.Calendar(f[0])
        "Overall" -> Screen.Overall(s(0))
        "NoteReview" -> Screen.NoteReview(f[0])
        "Photo" -> Screen.Photo(s(0), f[1]?.let { Pose.valueOf(it) })
        "PhotoCompare" -> Screen.PhotoCompare(f[0]?.let { Pose.valueOf(it) })
        else -> OBJECTS[parts[0]]
    }
}.getOrNull()
