package clean.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import clean.app.platform.LocalPlatform
import clean.app.platform.Platform

/**
 * The app's one haptic vocabulary (round 26). Every buzz in the app is one of these five, whatever it marks, so the same kind of moment
 * always feels the same. The phone's own effect for each is chosen by the platform (`Platform.haptic`): on Android `HapticFeedbackConstants`
 * with a fall-back on older phones, on the iPhone the Taptic Engine's generators.
 */
enum class Haptic(
    /** Which of two buzzes wins when they land together (see [HapticGate]). */
    val rank: Int,
) {
    /** A selection moved or a threshold passed: a segmented control, a calendar day, a slider or ruler step, a swipe's trigger point, a hold's peek opening, the pull to search, a number-pad key. The lightest. */
    Tick(0),
    /** A notch on a dial that is a little firmer than the steps around it: a whole kilo on the weigh-in ruler. */
    Detent(1),
    /** Something was done or saved: a set ticked, a weigh-in saved, a rest over. */
    Confirm(2),
    /** A moment to be proud of: a PR, a badge, a finished workout. */
    Heavy(3),
    /** Something was taken back or refused: an Undo. */
    Reject(4),
}

/**
 * No event fires two buzzes: a buzz that lands within [WINDOW_MS] of one at least as strong is dropped (a set ticked that is also a PR gives
 * the PR's, not both; a tick at a swipe's end after the swipe's own is not felt twice).
 */
object HapticGate {
    const val WINDOW_MS = 150L
    private var lastAt = Long.MIN_VALUE / 2
    private var lastRank = -1

    fun allows(kind: Haptic, now: Long): Boolean {
        if (now - lastAt < WINDOW_MS && kind.rank <= lastRank) return false
        lastAt = now
        lastRank = kind.rank
        return true
    }

    fun reset() { lastAt = Long.MIN_VALUE / 2; lastRank = -1 }
}

/** Gives [kind] to the phone unless it would double a buzz just given ([HapticGate]). Used by code that is not in a composition; screens use [rememberHaptic]. */
fun Platform.buzz(kind: Haptic) {
    if (HapticGate.allows(kind, uptimeMillis())) haptic(kind)
}

/** The buzz of a screen: [Haptic] in, nothing when Haptic ticks is off (kv `haptic.ticks`, [LocalHapticTicks]). */
@Composable
fun rememberHaptic(): (Haptic) -> Unit {
    val platform = LocalPlatform.current
    val on = LocalHapticTicks.current
    return remember(platform, on) { { kind -> if (on) platform.buzz(kind) } }
}

/** A light tick for a selection or a threshold, or nothing when Haptic ticks is off. */
@Composable
fun rememberTick(): () -> Unit {
    val haptic = rememberHaptic()
    return remember(haptic) { { haptic(Haptic.Tick) } }
}

/** A firmer detent than [rememberTick] (a whole kilo on the weigh-in ruler), or nothing when Haptic ticks is off. */
@Composable
fun rememberDetent(): () -> Unit {
    val haptic = rememberHaptic()
    return remember(haptic) { { haptic(Haptic.Detent) } }
}

/** [Platform.buzz] for what happens in the model or a launched effect, not in a composition: nothing when Haptic ticks is off. */
fun AppModel.buzz(kind: Haptic) {
    if (options.value["haptic.ticks"] != "off") platform.buzz(kind)
}
