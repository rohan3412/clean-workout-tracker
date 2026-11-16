package clean.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import kotlin.math.absoluteValue

/**
 * Every duration, curve and spring the app moves with, by name, the way Theme.kt holds every colour. A screen
 * types no number of its own: it says whether a thing is arriving, leaving, following a finger or being rewarded.
 */
object Motion {
    /** Material 3's standard curve, for what neither arrives nor leaves. */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Things arriving: quick to start, slow to settle. */
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    /** Things leaving: slow to start, quick to go. */
    val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    /** Fades, colour changes, a thing leaving. */
    const val QUICK = 100
    /** Small moves: the bar settling, folds, lifts. */
    const val SHORT = 160
    /** A thing leaving (a swiped row flying off). */
    const val PAGE_OUT = 140
    /** A chart drawing in, the ring sweeping. */
    const val SLOW = 600
    /** The activity chart drifting back to the present, after a scroll. */
    const val DRIFT_CHART = 2400
    /** Progress's History card drifting back to the newest workout. */
    const val DRIFT_LIST = 1200
    /** The rest's drain: one second per second, so it is linear. */
    const val CLOCK = 1000
    /** The glow on a set that earned a PR, fading. */
    const val GLOW = 1000
    /** One way of the tilt of a card in Arrange Home; [jiggle] varies it a little per card so they do not move in step. */
    const val JIGGLE = 140
    /** Arrange Home's wiggle growing from still when the page opens; each card starts [JIGGLE_STAGGER] after the one before (at most [JIGGLE_STAGGER_CARDS] steps). */
    const val JIGGLE_IN = 500
    const val JIGGLE_STAGGER = 40
    const val JIGGLE_STAGGER_CARDS = 8
    /** The wiggle settling to still before Arrange Home closes. */
    const val JIGGLE_OUT = 180
    /** The confetti from a set that earned a PR. */
    const val CONFETTI = 1200

    // The blink (2026-10-20, the user's words: "just make everything the simple blink animation"). Every screen-level change, a
    // tab, a page and Back, a sheet, dialog, picker or the Undo bar, and a thing appearing or going, is one quick fade through in
    // place: the old is gone in BLINK_OUT_MS, then the new fades in over BLINK_IN_MS. Nothing slides, grows, shrinks or moves (but a card
    // opening its page, below), the
    // look's speed does not touch it, and with animations off (the app's or the phone's) it is a cut. Both at 0 is an instant cut.

    /** The old screen fading out... */
    const val BLINK_OUT_MS = 60
    /** ...then the new one fading in, after it. */
    const val BLINK_IN_MS = 90

    // The one exception, from 2026-11-16 at the user's word ("it should be like morph animation, borders should grow out, elements
    // inside should morph out and up into the new position"): a card opening the page it belongs to is a container transform, and
    // Back is its exact reverse. One clock for all of it: the container's bounds, the elements that move from the card to the page,
    // the card's own content fading out and the page's fading in, the corners and the outline, the tab bar leaving.

    /** A card growing into its page, and the page shrinking into its card; the look's speed does not touch it. */
    const val CARD_TO_PAGE = 500
    /** The share of [CARD_TO_PAGE] in which the card's own content is gone (opening) or comes back (going back); the page's takes the rest. */
    const val CARD_FADE_SHARE = 0.3f
    /** The share of the way the container has grown after which its outline fades; the outline is gone when it has the page's size. */
    const val OUTLINE_FADE_FROM = 0.75f
    /**
     * Material 3's emphasized curve (the container transform's), the same both ways so opening and Back feel alike. Its path is two
     * cubics, which are exactly the emphasized accelerate (to 1/6 of the time, 0.4 of the way) and then the emphasized decelerate.
     */
    val Emphasized = androidx.compose.animation.core.Easing { t ->
        if (t < 1f / 6f) 0.4f * Accelerate.transform(t * 6f) else 0.4f + 0.6f * Decelerate.transform((t - 1f / 6f) * 1.2f)
    }

    /** The container's bounds, and everything that moves with them: Material 3's emphasized curve both ways. */
    @Suppress("UNUSED_PARAMETER")
    fun <T> cardBounds(forward: Boolean): TweenSpec<T> = tween(CARD_TO_PAGE, easing = Emphasized)
    /** The card's content by how far the container has grown (0 the card, 1 the page): gone by [CARD_FADE_SHARE]. */
    fun cardContentAlpha(grown: Float): Float = (1f - grown / CARD_FADE_SHARE).coerceIn(0f, 1f)
    /** The page's content: in from [CARD_FADE_SHARE] to [PAGE_IN_BY], so Back takes it away first, the same way reversed. */
    fun pageContentAlpha(grown: Float): Float = ((grown - CARD_FADE_SHARE) / (PAGE_IN_BY - CARD_FADE_SHARE)).coerceIn(0f, 1f)
    const val PAGE_IN_BY = 0.8f
    /** What is on both the card and the page but not alike (a title, a number, a chart) crosses over while it travels. */
    fun morphIn(): TweenSpec<Float> = tween(CARD_TO_PAGE / 2, delayMillis = CARD_TO_PAGE / 4, easing = Emphasized)
    fun morphOut(): TweenSpec<Float> = tween(CARD_TO_PAGE / 2, delayMillis = CARD_TO_PAGE / 4, easing = Emphasized)
    /** The tabs page stays under the growing container until it is done (opening), and is there from the first frame going back. */
    fun <T> tabsHold(forward: Boolean): TweenSpec<T> = if (forward) tween(1, delayMillis = CARD_TO_PAGE) else tween(1)

    fun <T> blinkOutSpec(): TweenSpec<T> = tween(BLINK_OUT_MS, easing = LinearEasing)
    /** The incoming half, starting [delay] ms in: after the outgoing half for a swap, at once for a thing appearing on its own. */
    fun <T> blinkInSpec(delay: Int = BLINK_OUT_MS): TweenSpec<T> = tween(BLINK_IN_MS, delayMillis = delay, easing = LinearEasing)
    /** A screen swap's two halves (AnimatedContent): never both on show at once. */
    fun blinkIn(on: Boolean): EnterTransition = if (on) fadeIn(blinkInSpec()) else EnterTransition.None
    fun blinkOut(on: Boolean): ExitTransition = if (on) fadeOut(blinkOutSpec()) else ExitTransition.None
    /** A thing appearing or going in place (AnimatedVisibility): no expand, slide or scale. */
    fun appear(on: Boolean): EnterTransition = if (on) fadeIn(blinkInSpec(0)) else EnterTransition.None
    fun vanish(on: Boolean): ExitTransition = if (on) fadeOut(blinkOutSpec()) else ExitTransition.None
    /** Whether things move at all (the app's Animations switch and the phone's scale); set by MainActivity, read by windows. */
    @Volatile var moving = true
    /** A window's own animation (a dialog, sheet or the phone's picker): the blink in res/anim, or none. */
    fun windowAnimation(): Int = if (moving && BLINK_OUT_MS + BLINK_IN_MS > 0) R.style.BlinkWindow else R.style.CutWindow

    /** 1 is the durations above; the look's speed (Mono is fast) shortens the tweens below, never the blink. Set by MainActivity from the look. */
    @Volatile var speed = 1f

    /** True when the phone's "Remove animations" / animator duration scale is 0; then nothing moves, as with the app's own switch off. */
    fun systemAnimationsOff(context: android.content.Context): Boolean =
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    private fun scaled(base: Int) = (base * speed).toInt().coerceAtLeast(1)

    fun <T> quick(): TweenSpec<T> = tween(scaled(QUICK), easing = Standard)
    fun <T> short(): TweenSpec<T> = tween(scaled(SHORT), easing = Standard)
    fun <T> arrive(ms: Int = SHORT): TweenSpec<T> = tween(scaled(ms), easing = Decelerate)
    fun <T> leave(ms: Int = PAGE_OUT): TweenSpec<T> = tween(scaled(ms), easing = Accelerate)
    fun <T> slow(): TweenSpec<T> = tween(scaled(SLOW), easing = Standard)
    /** A drift: slow, and it slows as it nears the end. */
    fun <T> drift(ms: Int): TweenSpec<T> = tween(scaled(ms), easing = Decelerate)
    /** Card [index]'s wiggle growing from still, a beat after the card before it. */
    fun <T> jiggleIn(index: Int): TweenSpec<T> = tween(scaled(JIGGLE_IN), delayMillis = scaled(JIGGLE_STAGGER) * index.coerceIn(0, JIGGLE_STAGGER_CARDS), easing = Standard)
    /** The wiggle settling to still. */
    fun <T> jiggleOut(): TweenSpec<T> = tween(scaled(JIGGLE_OUT), easing = Standard)
    /** How long Arrange Home waits for the wiggle to settle before it closes. */
    fun jiggleOutMillis(): Long = scaled(JIGGLE_OUT).toLong()
    /** A PR's glow fading out. */
    fun <T> glow(): TweenSpec<T> = tween(scaled(GLOW), easing = Standard)
    fun jiggle(seed: Int): InfiniteRepeatableSpec<Float> = infiniteRepeatable(tween(scaled(JIGGLE) + (seed.absoluteValue % 6) * 15, easing = Standard), RepeatMode.Reverse)
    /** A PR's confetti flying: linear, the pieces carry their own easing. */
    fun <T> confetti(): TweenSpec<T> = tween(scaled(CONFETTI), easing = LinearEasing)
    /** The rest's drain. */
    fun <T> clock(): TweenSpec<T> = tween(CLOCK, easing = LinearEasing)
    /** Anything the finger moved, settling: no visible bounce. */
    fun <T> follow(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
    /** A set ticked and a PR arriving, and nothing else: the one bounce there is. */
    fun <T> reward(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    /** The dip under a press. */
    fun <T> press(): SpringSpec<T> = spring(stiffness = Spring.StiffnessMedium)
    /** The dip under a press that starts as the finger lands. */
    fun <T> pressNow(): SpringSpec<T> = spring(stiffness = Spring.StiffnessHigh)
}

/** The phone's own dialogs (date and time pickers) blink like the app's: call before `show()`. */
fun <D : android.app.Dialog> D.blinking(): D = apply { window?.setWindowAnimations(Motion.windowAnimation()) }

/** Inside a Compose dialog's content: its window blinks in and out in place rather than the platform's scale and fade. */
@androidx.compose.runtime.Composable
fun BlinkWindow() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.setWindowAnimations(Motion.windowAnimation())
    }
}
