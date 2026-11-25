package clean.app

import clean.core.Session
import clean.core.SetItem
import clean.core.SetType
import clean.core.lbToKg
import clean.core.KG_PER_LB
import clean.core.time.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.datetime.LocalDate

/*
 * Round 17 (2026-11-20): the pure parts of eight small delights, kept here so they are tested without a screen.
 */

/** The Library hold's peek: the day of the exercise's last session, then its working sets as "100 kg × 6" (warm-ups only if there is nothing else), at most [shown]. Null when never done. */
fun lastSessionPeek(session: Session?, unit: String, shown: Int = 6): List<String>? {
    if (session == null || session.sets.isEmpty()) return null
    val sets = session.sets.filter { it.type != SetType.WARMUP }.ifEmpty { session.sets }
    val lines = sets.take(shown).map { setValues(it, unit) }
    return listOf(prettyDay(session.date)) + lines + (if (sets.size > shown) listOf("+${sets.size - shown} more") else emptyList())
}

/** Whether a set done at [weightKg] for [reps] beats [before] (the same set last time): more reps at the same weight or more. */
fun beatsLastReps(weightKg: Double?, reps: Int?, before: SetItem?): Boolean {
    val was = before?.reps ?: return false
    if (reps == null) return false
    return reps > was && (weightKg ?: 0.0) >= (before.weight ?: 0.0) - 1e-9
}

/** The heaviest working weight (kg) in the sessions dated before [date], or null when there is none: what a new weight PR counts up from. */
fun previousBestWeight(sessions: List<Session>, date: String): Double? =
    sessions.filter { it.date < date }.flatMap { s -> s.sets.filter { it.type != SetType.WARMUP }.mapNotNull { it.weight } }.filter { it > 0.0 }.maxOrNull()

/** The weight shown while a PR counts up: [progress] (0 to 1) of the way from [from] to [to], in steps of half a unit of [unit], exact at the end. */
fun rolledWeight(from: Double, to: Double, progress: Float, unit: String): Double {
    if (progress >= 1f) return to
    val now = from + (to - from) * progress.coerceAtLeast(0f)
    val shown = if (unit == "lb") now / KG_PER_LB else now
    val stepped = kotlin.math.floor(shown * 2.0 + 0.5) / 2.0
    return if (unit == "lb") lbToKg(stepped) else stepped
}

/** "412 h trained since Apr 2026": every workout's length added up. Null with no workouts. */
fun trainedLine(totalSeconds: Long, first: LocalDate?): String? {
    if (first == null || totalSeconds <= 0) return null
    val time = if (totalSeconds >= 3600) thousandsComma((totalSeconds + 1800) / 3600) + " h" else "${maxOf(1L, (totalSeconds + 30) / 60)} min"
    return "$time trained since ${first.format(LocalePattern("MMM yyyy"))}"
}

/** The Snapshots cards' lean for a scroll of [delta] px since the last frame, blended with the lean [previous]: -1 to 1, 0 at rest. */
fun tiltStep(previous: Float, delta: Float): Float =
    (previous * 0.6f + (delta / 40f).coerceIn(-1f, 1f) * 0.4f).coerceIn(-1f, 1f)

/** How many dp of pull past the top of the Library open its search. */
const val PULL_TO_SEARCH_DP = 72

/**
 * Round 17 (19): while [on], a slow scale and fade pulse (the Finish button once every set is done); it stops the moment [on] turns off, and is
 * never there with animations off. Drawn in a graphics layer: nothing moves in the layout.
 */
@androidx.compose.runtime.Composable
fun Modifier.breathing(on: Boolean): Modifier {
    if (!on || !flourishOn()) return this
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "finish breath")
    val breath = transition.animateFloat(0f, 1f, Motion.finishBreath(), label = "finish breath")
    return graphicsLayer { val b = breath.value; scaleX = 1f + 0.05f * b; scaleY = 1f + 0.05f * b; alpha = 1f - 0.18f * b }
}

/** Whether the workout has sets and every one of them is ticked: what makes the Finish button breathe. */
fun everySetDone(blocks: List<DraftBlock>): Boolean =
    blocks.any { b -> b.items.any { it is DraftSet } } && blocks.all { b -> b.items.all { it !is DraftSet || it.done } }

/** Round 19: the template hold's peek, like the Library's: the day it was last done, then its exercises and sets. Null for a template never done. */
fun templatePeek(template: Template, lastDone: String?): List<String>? {
    if (lastDone == null) return null
    return listOf(prettyDay(lastDone), "${plural(template.blocks.size, "exercise")} · ${plural(template.blocks.sumOf { it.sets.size }, "set")}")
}
