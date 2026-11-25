package clean.app

/** How long a slip can be taken back, everywhere in the app; a further step in that time starts it again. */
const val UNDO_MILLIS = 8_000L

/**
 * One step that can be taken back: what the bar says, how to put it back, and what to do once the time to take it back has passed
 * (a photo's kept file is deleted then).
 */
class UndoStep(val text: String, val undo: () -> Unit, val expire: () -> Unit = {})

/**
 * The steps that can still be taken back. One Undo takes them all back, newest first, so two slips in a row are both undone by it
 * instead of the second putting the first out of reach.
 */
class Undos private constructor(val steps: List<UndoStep>) {
    constructor(step: UndoStep) : this(listOf(step))

    operator fun plus(step: UndoStep) = Undos(steps + step)

    /** What the bar says: the step's own words, or for several the count and their shared last word ("2 removed"), else "2 changes". */
    val text: String
        get() {
            if (steps.size == 1) return steps[0].text
            val word = steps.map { it.text.substringAfterLast(' ').lowercase() }.distinct().singleOrNull()
            return "${steps.size} ${word ?: "changes"}"
        }

    fun undoAll() { for (step in steps.asReversed()) step.undo() }

    fun expireAll() { for (step in steps) step.expire() }
}
