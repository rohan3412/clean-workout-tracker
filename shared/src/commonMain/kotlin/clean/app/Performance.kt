package clean.app

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Settings, Appearance, Performance (round 26): how much of the app's costly drawing is on, for slower phones. The tier is a floor the
 * individual switches can only go further down from: Reduce transparency (`glass.reduce`) and Animations (`ui.motion`) still apply on top of it.
 * Read through [LocalPerformance]; the switches that were already there ([LocalSolidGlass], [LocalMotion]) are set from it in `CleanRoot`.
 */
enum class Performance(val key: String, val label: String, val about: String) {
    /** Everything the app does. */
    Full("full", "Full", "Blurred glass, cards that grow into pages, charts that morph, counting numbers."),
    /** Motion stays; the heaviest drawing goes. */
    Balanced("balanced", "Balanced", "Opaque glass in place of blur, no blur behind a hold, charts cross-fade instead of morphing. Everything else moves."),
    /** The cheapest that still answers a touch. */
    Light("light", "Light", "Opaque glass, a quick fade where cards would grow into pages, no counting up or drawing in, and nothing that moves on its own.");

    /** Glass is drawn opaque, and the page behind a hold is not blurred (the same switch as Reduce transparency). */
    val solidGlass: Boolean get() = this != Full
    /** A chart's line travels between a card and its page (Full), or the two simply cross-fade. */
    val morphLines: Boolean get() = this == Full
    /** A card grows into its page and Back shrinks it (Full, Balanced), or the page opens with the blink. */
    val sharedCards: Boolean get() = this != Light
    /** Numbers count up, rings and sparklines draw in, rows settle, the calendar waves, the finish button and the muscles breathe. */
    val flourishes: Boolean get() = this != Light

    companion object {
        const val KEY = "performance"
        /** Set once the phone has been offered Balanced for being low on memory or on battery saver. */
        const val SUGGESTED_KEY = "performance.suggested"
        fun of(key: String?): Performance = entries.firstOrNull { it.key == key } ?: Full
    }
}

val LocalPerformance = staticCompositionLocalOf { Performance.Full }

/** Whether the app's flourishes (counting up, drawing in, settling, breathing) play: animations on and the tier is not Light. */
@androidx.compose.runtime.Composable
fun flourishOn(): Boolean = LocalMotion.current && LocalPerformance.current.flourishes
