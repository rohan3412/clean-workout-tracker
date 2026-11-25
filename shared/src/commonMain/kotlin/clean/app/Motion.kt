package clean.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
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
    const val DRIFT_CHART = 3200
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
    /** The four dots flying out of a star that was just turned on. */
    const val STAR_BURST = 350
    /** A week ring closing: one soft overshoot, a spring that swells it a little past full and settles. */
    fun closeRing() = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)
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
    // Fades only (nothing that moves has a window of its own): the cross-fade of what is on both sides, and a row going below the page.
    fun morphIn(): TweenSpec<Float> = tween(CARD_TO_PAGE / 2, delayMillis = CARD_TO_PAGE / 4, easing = Emphasized)
    fun morphOut(): TweenSpec<Float> = tween(CARD_TO_PAGE / 2, delayMillis = CARD_TO_PAGE / 4, easing = Emphasized)
    /**
     * Words that travel between a card and its page cross over in the middle of the way, by how far the container has grown (0 the card,
     * 1 the page): the card's version is gone from [TEXT_OUT_FROM] to [TEXT_OUT_TO], the page's comes in from [TEXT_IN_FROM] to [TEXT_IN_TO],
     * so whatever differs between the two (the ellipsis, the wording, the weight, the colour) is changed where it cannot be seen.
     */
    const val TEXT_OUT_FROM = 0.35f
    const val TEXT_OUT_TO = 0.5f
    const val TEXT_IN_FROM = 0.5f
    const val TEXT_IN_TO = 0.65f
    fun textOut(grown: Float): Float = 1f - ((grown - TEXT_OUT_FROM) / (TEXT_OUT_TO - TEXT_OUT_FROM)).coerceIn(0f, 1f)
    fun textIn(grown: Float): Float = ((grown - TEXT_IN_FROM) / (TEXT_IN_TO - TEXT_IN_FROM)).coerceIn(0f, 1f)
    /** A row going below the page by itself fades only late in the way, 250 to 450 ms of the 500, so nothing is left when it ends (Back: 50 to 250 ms) */
    fun rowOut(): TweenSpec<Float> = tween(CARD_TO_PAGE * 2 / 5, delayMillis = CARD_TO_PAGE / 2, easing = LinearEasing)
    fun rowIn(): TweenSpec<Float> = tween(CARD_TO_PAGE * 2 / 5, delayMillis = CARD_TO_PAGE / 10, easing = LinearEasing)
    /**
     * The way of a History row's name, date and PR count between the row and its card, so no two of them ever cross: on opening the box first
     * moves up or down to its line (the first [LEG_SPLIT] of its way) and only then sideways and in size, on Back sideways first and then
     * to the row's line. The name keeps its width until the PR count has moved off, so its box never runs into it. Both legs run on the container's
     * clock ([Emphasized]), so every box of every row starts on the first frame and arrives on the last, with the container.
     */
    const val LEG_SPLIT = 0.45f
    /**
     * Where a box on its legs is when the container has gone [p] of its way (0 the start, 1 the end, on [Emphasized]): the two legs are laid end to
     * end and [p] says how far along them, so the box starts moving on the first frame and arrives on the last, with the container, whatever its legs' lengths.
     */
    fun leggedAt(initial: androidx.compose.ui.geometry.Rect, target: androidx.compose.ui.geometry.Rect, forward: Boolean, p: Float): androidx.compose.ui.geometry.Rect {
        val via = if (forward) androidx.compose.ui.geometry.Rect(initial.left, target.top, initial.left + initial.width, target.top + target.height)
            else androidx.compose.ui.geometry.Rect(target.left, initial.top, target.left + target.width, initial.top + initial.height)
        return if (p < LEG_SPLIT) androidx.compose.ui.geometry.lerp(initial, via, p / LEG_SPLIT) else androidx.compose.ui.geometry.lerp(via, target, (p - LEG_SPLIT) / (1f - LEG_SPLIT))
    }
    /** How many straight steps the legs are sampled in; each is shorter than a frame. */
    const val LEG_SAMPLES = 50
    fun legged(initial: androidx.compose.ui.geometry.Rect, target: androidx.compose.ui.geometry.Rect, forward: Boolean): androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.geometry.Rect> =
        androidx.compose.animation.core.keyframes {
            durationMillis = CARD_TO_PAGE
            // The legs on the container's own clock: the box is where [leggedAt] puts it for the eased progress at each step of the time.
            for (i in 1 until LEG_SAMPLES) leggedAt(initial, target, forward, Emphasized.transform(i / LEG_SAMPLES.toFloat())) at (CARD_TO_PAGE * i / LEG_SAMPLES)
        }
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
    @kotlin.concurrent.Volatile var moving = true
    /** Whether a window's own animation (a dialog, sheet or the phone's picker) is the blink, or none. */
    fun windowBlinks(): Boolean = moving && BLINK_OUT_MS + BLINK_IN_MS > 0

    /** 1 is the durations above; the look's speed (Mono is fast) shortens the tweens below, never the blink. Set by MainActivity from the look. */
    @kotlin.concurrent.Volatile var speed = 1f

    private fun scaled(base: Int) = (base * speed).toInt().coerceAtLeast(1)

    fun <T> quick(): TweenSpec<T> = tween(scaled(QUICK), easing = Standard)
    fun <T> short(): TweenSpec<T> = tween(scaled(SHORT), easing = Standard)
    fun <T> arrive(ms: Int = SHORT): TweenSpec<T> = tween(scaled(ms), easing = Decelerate)
    fun <T> leave(ms: Int = PAGE_OUT): TweenSpec<T> = tween(scaled(ms), easing = Accelerate)
    fun <T> slow(): TweenSpec<T> = tween(scaled(SLOW), easing = Standard)
    /** A drift: slow, and it slows as it nears the end. */
    fun <T> drift(ms: Int): TweenSpec<T> = tween(scaled(ms), easing = Decelerate)
    /** A very slow drift that eases in as well as out, so it never starts with a lurch (the activity chart returning to the present). */
    fun <T> driftSlow(ms: Int): TweenSpec<T> = tween(scaled(ms), easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f))
    /** Card [index]'s wiggle growing from still, a beat after the card before it. */
    fun <T> jiggleIn(index: Int): TweenSpec<T> = tween(scaled(JIGGLE_IN), delayMillis = scaled(JIGGLE_STAGGER) * index.coerceIn(0, JIGGLE_STAGGER_CARDS), easing = Standard)
    /** The wiggle settling to still. */
    fun <T> jiggleOut(): TweenSpec<T> = tween(scaled(JIGGLE_OUT), easing = Standard)
    /** How long Arrange Home waits for the wiggle to settle before it closes. */
    fun jiggleOutMillis(): Long = scaled(JIGGLE_OUT).toLong()
    /** A PR's glow fading out. */
    fun <T> glow(): TweenSpec<T> = tween(scaled(GLOW), easing = Standard)
    fun jiggle(seed: Int): InfiniteRepeatableSpec<Float> = infiniteRepeatable(tween(scaled(JIGGLE) + (seed.absoluteValue % 6) * 15, easing = Standard), RepeatMode.Reverse)
    /** The body figure's colours sliding in from the side after a swipe between its views; the grey body stays still. */
    const val VIEW_SLIDE = 300
    fun <T> viewSlide(): TweenSpec<T> = tween(scaled(VIEW_SLIDE), easing = Standard)
    // The workout screen's moves (2026-11-16): the next-set highlight gliding to the next row, a done tint spreading from the tick, a
    // finished exercise folding, a set's gap closing or opening, a dialog growing from what was tapped. All on the emphasized curve.
    const val GLIDE = 300
    const val SWEEP = 300
    const val FOLD = 300
    const val GAP = 300
    const val DIALOG = 300
    fun <T> glide(): TweenSpec<T> = tween(scaled(GLIDE), easing = Emphasized)
    fun <T> sweep(): TweenSpec<T> = tween(scaled(SWEEP), easing = Emphasized)
    fun <T> gap(): TweenSpec<T> = tween(scaled(GAP), easing = Emphasized)
    fun <T> dialog(): TweenSpec<T> = tween(scaled(DIALOG), easing = Emphasized)
    /** A row's idle tint goes only after the done tint has spread over it. */
    fun <T> afterSweep(): TweenSpec<T> = tween(scaled(QUICK), delayMillis = scaled(SWEEP), easing = Standard)
    /** Rows folding up into a line, and the line unfolding: the height follows, the content fades and rises a little. [delay] holds a fade-in back while the other side begins to go. */
    fun foldIn(on: Boolean, delay: Int = 0): EnterTransition = if (!on) EnterTransition.None else
        expandVertically(tween<IntSize>(scaled(FOLD), easing = Emphasized)) + fadeIn(tween(scaled(FOLD) / 2, delayMillis = scaled(delay), easing = LinearEasing)) +
            slideInVertically(tween<IntOffset>(scaled(FOLD), easing = Emphasized)) { -it / 10 }
    fun foldOut(on: Boolean): ExitTransition = if (!on) ExitTransition.None else
        shrinkVertically(tween<IntSize>(scaled(FOLD), easing = Emphasized)) + fadeOut(tween(scaled(FOLD) / 2, easing = LinearEasing)) +
            slideOutVertically(tween<IntOffset>(scaled(FOLD), easing = Emphasized)) { -it / 10 }
    // Stage F3 (2026-11-16): numbers rolling, a chart's range stretching, the calendar's first wave, lists settling in.
    /** A changed digit sliding up out and the new one in from below. */
    const val ROLL = 300
    /** A chart's line stretching or shrinking to a new range (the Statistics bars have [BARS]). */
    const val STRETCH = 400
    // Round 4c (2026-11-19): the Statistics bars reshaping, the next set's row glowing when a rest ends, the summary's numbers counting up.
    /** The Statistics bars reshaping between Week / Month / Year / All (each bar's height and slot). */
    const val BARS = 300
    fun <T> bars(): TweenSpec<T> = tween(scaled(BARS), easing = Emphasized)
    /** The next set's row glowing once when a rest ends: in over [GLOW_IN], out over [GLOW_OUT]. */
    const val GLOW_IN = 150
    const val GLOW_OUT = 600
    /** One number of the summary counting up from 0, and how long after the one before the next starts. */
    const val COUNT_UP = 250
    const val COUNT_STAGGER = 120
    fun <T> countUp(index: Int): TweenSpec<T> = tween(scaled(COUNT_UP), delayMillis = scaled(COUNT_STAGGER) * index, easing = Emphasized)
    /** Everything the count-up takes for [n] numbers, to time the tick after the last. */
    fun countUpMillis(n: Int): Long = (scaled(COUNT_STAGGER) * (n - 1).coerceAtLeast(0) + scaled(COUNT_UP)).toLong()
    /** The calendar's first fill: each diagonal of squares starts [WAVE_STEP] ms after the one before; a square takes [WAVE_CELL] ms. */
    const val WAVE_STEP = 45
    const val WAVE_CELL = 140
    /** A page's first rows rising into place: [SETTLE_ROWS] of them, each [SETTLE_STEP] ms after the one before, [SETTLE_MS] ms each, from [SETTLE_RISE_DP] dp below. */
    const val SETTLE_ROWS = 6
    const val SETTLE_STEP = 30
    const val SETTLE_MS = 200
    const val SETTLE_RISE_DP = 8
    /** The furthest diagonal that waits its turn: squares beyond it come with it, so the wave never takes long on a tall calendar. */
    const val WAVE_MAX = 20
    fun waveMillis(): Int = WAVE_MAX * WAVE_STEP + WAVE_CELL
    /** The wave's clock runs in milliseconds, linear: each square reads its own part of it ([waveCell]). */
    fun <T> wave(): TweenSpec<T> = tween(waveMillis(), easing = LinearEasing)
    /** How far square on diagonal [d] has come ([ms] into the wave): 0 hidden, 1 in. */
    fun waveCell(ms: Float, d: Int): Float = Decelerate.transform(((ms - d.coerceIn(0, WAVE_MAX) * WAVE_STEP) / WAVE_CELL).coerceIn(0f, 1f))
    fun <T> roll(): TweenSpec<T> = tween(scaled(ROLL), easing = Emphasized)
    fun <T> stretch(): TweenSpec<T> = tween(scaled(STRETCH), easing = Emphasized)
    /** One row of a page settling in, [index] rows after the first, held back until the page's blink has brought the page in. */
    fun <T> settle(index: Int): TweenSpec<T> = tween(scaled(SETTLE_MS), delayMillis = BLINK_OUT_MS + scaled(SETTLE_STEP) * index.coerceIn(0, SETTLE_ROWS), easing = Decelerate)
    /** How long a page counts as just opened: after it, a row that composes (scrolling, Back) does not rise. */
    fun settleWindowMillis(): Long = (BLINK_OUT_MS + BLINK_IN_MS + scaled(SETTLE_STEP) * SETTLE_ROWS + scaled(SETTLE_MS) + 100).toLong()
    // Stage F4 (2026-11-16): the tab bar's pill and the exercise page's underline gliding and stretching, a new PR badge landing, a badge flipping like a coin.
    /** A moving indicator (the tab bar's pill, an underline) running from one tab to the next. */
    const val PILL = 350
    /** The exercise page's content sliding in sideways after a tab change. */
    const val SLIDE = 300
    /** One light sweep across a PR badge that has just landed, starting [SHIMMER_AFTER] ms in, when the bounce has made it big enough to see. */
    const val SHIMMER = 450
    const val SHIMMER_AFTER = 120
    /** A badge, shield or streak flipping in on the summary. */
    const val COIN = 600
    fun <T> pill(): TweenSpec<T> = tween(scaled(PILL), easing = Emphasized)
    fun <T> slide(): TweenSpec<T> = tween(scaled(SLIDE), easing = Emphasized)
    fun <T> shimmer(): TweenSpec<T> = tween(scaled(SHIMMER), delayMillis = scaled(SHIMMER_AFTER), easing = LinearEasing)
    fun <T> coin(): TweenSpec<T> = tween(scaled(COIN), easing = Emphasized)
    /**
     * A moving indicator stretches between two tabs like a liquid: its leading edge runs ahead of its trailing edge and the trailing one
     * catches up. [t] is how far along the move is, 0 to 1; both are 0 at the start and 1 at the end.
     */
    fun stretchLead(t: Float): Float = 1f - (1f - t) * (1f - t)
    fun stretchTrail(t: Float): Float = t * t
    /** A PR's confetti flying: linear, the pieces carry their own easing. */
    fun <T> confetti(): TweenSpec<T> = tween(scaled(CONFETTI), easing = LinearEasing)
    /** The rest's drain. */
    fun <T> clock(): TweenSpec<T> = tween(CLOCK, easing = LinearEasing)
    /** Anything the finger moved, settling: no visible bounce. */
    fun <T> follow(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMedium)
    /** A set ticked and a PR arriving, and nothing else: the one bounce there is. */
    fun <T> reward(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    /** Others parting around an exercise being dragged (D19): a light spring with a hint of give. */
    fun <T> part(): SpringSpec<T> = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
    /** The plus when a workout ends (E19): up to [PLUS_BUMP] quickly, then one spring back to 1. */
    const val PLUS_BUMP = 1.08f
    fun <T> plusUp(): TweenSpec<T> = tween(scaled(90), easing = Standard)
    fun <T> plusBack(): SpringSpec<T> = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)
    /** The glow on the screen's edges when a rest ends (D6): in over [REST_GLOW_IN], out over [REST_GLOW_OUT], about 1.2 s in all. */
    const val REST_GLOW_IN = 400
    const val REST_GLOW_OUT = 800
    fun <T> restGlowIn(): TweenSpec<T> = tween(scaled(REST_GLOW_IN), easing = Decelerate)
    fun <T> restGlowOut(): TweenSpec<T> = tween(scaled(REST_GLOW_OUT), easing = Standard)
    /** The shimmer along the edge of a set row that has just become a PR (D2). */
    const val PR_EDGE = 600
    fun <T> prEdge(): TweenSpec<T> = tween(scaled(PR_EDGE), easing = LinearEasing)
    /**
     * Rubber-band resistance (D7, E14): how far a thing shows for a finger that has dragged [raw] (0 or more). It starts 1:1 and eases
     * toward [limit], which it never passes, so the drag gets heavier the further it goes.
     */
    fun rubber(raw: Float, limit: Float): Float = if (raw <= 0f) 0f else limit * (1f - kotlin.math.exp(-raw / limit))
    // Round 15 (2026-11-20): small delights, each once per occasion.
    /** Home's "Up next" name sliding on: the old one up and out, the new one up from below. */
    const val UP_NEXT = 400
    fun <T> upNext(): TweenSpec<T> = tween(scaled(UP_NEXT), easing = Emphasized)
    /** How long after Home is shown again the week ring's new segment starts, with the calendar square's own delay (D5). */
    const val RING_AFTER = 250
    /** The glint across the cap of an all-time-high bar. */
    const val CAP_GLINT = 600
    fun <T> capGlint(): TweenSpec<T> = tween(scaled(CAP_GLINT), easing = LinearEasing)
    /** The muscles trained today brightening and settling back, once: one breath. */
    const val BREATH = 800
    fun <T> breath(): TweenSpec<T> = tween(scaled(BREATH), easing = LinearEasing)
    // Round 17 (2026-11-20): eight more small delights.
    /** A new PR's weight counting up from the previous best, on the finish summary. */
    const val PR_ROLL = 700
    fun <T> prRoll(): TweenSpec<T> = tween(scaled(PR_ROLL), easing = Emphasized)
    /** The ▲ of a set that beat last time floating up from its row and fading. */
    const val BEAT_RISE = 700
    fun <T> beatRise(): TweenSpec<T> = tween(scaled(BEAT_RISE), easing = Decelerate)
    /** One breath of the Finish button (out and back is twice this), once every set is done. */
    const val FINISH_BREATH = 1400
    fun finishBreath(): InfiniteRepeatableSpec<Float> = infiniteRepeatable(tween(scaled(FINISH_BREATH), easing = Standard), RepeatMode.Reverse)
    /** The Snapshots cards settling flat after a scroll. */
    const val TILT_SETTLE = 400
    fun <T> tiltSettle(): TweenSpec<T> = tween(scaled(TILT_SETTLE), easing = Decelerate)
    /** The Library's pull-down settling back after release. */
    const val PULL_BACK = 220
    fun <T> pullBack(): TweenSpec<T> = tween(scaled(PULL_BACK), easing = Standard)
    /** A search field opening from its icon and closing again (round 20): the pill grows down out of the header and fades in. */
    const val SEARCH = 220
    fun <T> search(): TweenSpec<T> = tween(scaled(SEARCH), easing = Emphasized)
    /** The search field closing: quicker than it opens and accelerating away, so it is gone as the keyboard goes (Round 29). */
    const val SEARCH_OUT = 140
    fun <T> searchOut(): TweenSpec<T> = tween(scaled(SEARCH_OUT), easing = Accelerate)
    /** The dip under a press. */
    fun <T> press(): SpringSpec<T> = spring(stiffness = Spring.StiffnessMedium)
    /** The dip under a press that starts as the finger lands. */
    fun <T> pressNow(): SpringSpec<T> = spring(stiffness = Spring.StiffnessHigh)
}

/** Inside a Compose dialog's content: its window blinks in and out in place rather than the platform's scale and fade. */
@androidx.compose.runtime.Composable
fun BlinkWindow() = clean.app.platform.LocalPlatform.current.BlinkWindow()

/** Inside a Compose dialog's content: its window has no animation of its own, for a dialog that moves its content itself. */
@androidx.compose.runtime.Composable
fun CutWindow() = clean.app.platform.LocalPlatform.current.CutWindow()
