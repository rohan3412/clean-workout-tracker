package clean.app

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import clean.app.ui.theme.LocalLook
import clean.app.ui.theme.cardCorner
import clean.app.ui.theme.cardShape
import kotlin.math.abs
import kotlin.math.max

/*
 * A card opening into its page (Material 3's container transform), and Back as its exact reverse. The tabs page stays composed under
 * the pages (so on Back the card is where it was, on the first frame), the container's bounds, the elements on both card and page
 * and the fades all run on one clock (Motion.CARD_TO_PAGE). How it fits together:
 *  - the card side lives in the tabs page, whose scope is [TabsScope]; the page side is each page's AnimatedContent scope;
 *  - a card names itself ([LocalCardKey]) and its tap runs under that name, so the page opened by that tap is its page; the page
 *    root is the other half of the shared bounds ([cardTarget]) and tells its content which card it came from ([LocalPageCard]);
 *  - what is on both ([morphs]) shares a key made of the card's and a role ("title", "figure", ...) and travels between its two places.
 * Only the card's own tap does this: a row or button inside a card, and every other page change, is the blink.
 */

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
/** The scope the content being drawn belongs to: the page's own, or the tabs page's (see [TabsScope]). */
val LocalPageScope = compositionLocalOf<AnimatedVisibilityScope?> { null }
/** The key of the card being drawn, provided by the page that lists cards (Home, Progress, Profile); null where a card does not open into its page. */
val LocalCardKey = staticCompositionLocalOf<String?> { null }
/** The key of the card the page being drawn grew out of, while it is growing or shrinking; null otherwise. */
val LocalPageCard = staticCompositionLocalOf<String?> { null }
/** Runs a card's own tap under its key ([AppModel.fromCard]). */
val LocalCardPress = staticCompositionLocalOf<(String?, () -> Unit) -> Unit> { { _, tap -> tap() } }
/** Whether the change under way opens a page (true) or closes it, read when a shared move starts. */
val LocalPageForward = staticCompositionLocalOf<() -> Boolean> { { true } }
/** Where the cards of the tabs page are, for the app to find the card a page shrinks into. */
val LocalCardBounds = staticCompositionLocalOf<CardBounds?> { null }
/** The shape of the growing container, shared by both halves of the move. */
val LocalMorphClip = staticCompositionLocalOf<MorphClip?> { null }
/** The card the change under way grows out of or shrinks into; only that card's content fades with it. */
val LocalMorphCard = staticCompositionLocalOf<String?> { null }
/** Whether a card change is under way (the card is being opened or closed); shared things match across the two pages only while it is. */
val LocalMorphing = staticCompositionLocalOf<() -> Boolean> { { false } }
/** Whether the shared things of what is being drawn may travel: false for a row cut off by its list, which stays where it is and fades with its card (it would show its hidden part the moment it left the list). */
val LocalMorphEnabled = staticCompositionLocalOf<() -> Boolean> { { true } }
/** How far the tab bar has gone down with the tabs page (0 at rest, 1 gone) while a card morph runs. */
val LocalBarOut = staticCompositionLocalOf<State<Float>?> { null }

/*
 * A card on a page that opens another page (a workout card on the History page) is a card the same way, with its own set of the
 * locals above: its key starts with "page:" and the app tells the two kinds apart by it ([isPageKey]). A page card's change is
 * between two pages in one AnimatedContent, so its progress is the page's own transition's, not the tab bar's.
 */
/** Whether a change between two pages that grows out of a page card is under way (true) or has settled (false). */
val LocalPageMorphing = staticCompositionLocalOf<() -> Boolean> { { false } }
/** The page card the change between two pages under way grows out of or shrinks into. */
val LocalPageMorphCard = staticCompositionLocalOf<String?> { null }
/** How far the page being drawn is along a page card's change: 0 to 1 for the page that grows (1 to 0 going back), 0 to 1 the other way for the page under it. */
val LocalPageProgress = staticCompositionLocalOf<State<Float>?> { null }


/** Whether [key] is the key of a card on a page (page to page) and not of one on the tabs page. */
fun isPageKey(key: String) = key.startsWith("page:")
@Composable fun morphingOf(key: String): () -> Boolean = if (isPageKey(key)) LocalPageMorphing.current else LocalMorphing.current
@Composable fun morphCardOf(key: String): String? = if (isPageKey(key)) LocalPageMorphCard.current else LocalMorphCard.current
@Composable fun progressOf(key: String): State<Float>? = if (isPageKey(key)) LocalPageProgress.current else LocalBarOut.current

/**
 * Lets a shared thing match only while a card change is under way: on a blink between two pages (a page over the card's page and Back
 * to it) the card and the page are both composed too, and must not fly to each other.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class MorphConfig(private val on: () -> Boolean) : SharedTransitionScope.SharedContentConfig {
    override val SharedTransitionScope.SharedContentState.isEnabled: Boolean get() = on()
    override val shouldKeepEnabledForOngoingAnimation get() = true
}

/** The scope of the tabs page, which stays composed under the pages: visible when no page is open, gone while one is. */
class TabsScope(override val transition: Transition<EnterExitState>) : AnimatedVisibilityScope

/** A card on the tabs page: where it is in the window, its corner and its outline. */
class CardSpot(val rect: Rect, val corner: Float, val border: BorderStroke?, val fill: androidx.compose.ui.graphics.Color)

/** The live cards of the tabs page by key; a card scrolled out of its list or removed is not in it. */
class CardBounds {
    private val spots = HashMap<String, CardSpot>()
    /** Where each card last was, kept after it leaves the screen with its page: a page under another is not composed, and on Back it begins its clip from here. */
    private val last = HashMap<String, CardSpot>()
    fun put(key: String, spot: CardSpot) { spots[key] = spot; last[key] = spot }
    fun remove(key: String) { spots.remove(key) }
    /** A card of a page that is not composed now (Back to it): where it was, which is where it comes back, the page keeping its scroll. */
    fun kept(key: String, width: Float, height: Float): CardSpot? =
        (spots[key] ?: last[key])?.takeIf { it.rect.width > 0f && it.rect.height > 0f && it.rect.overlaps(Rect(0f, 0f, width, height)) }
    /** The card, when at least part of it is on the screen of [width] x [height] px. */
    fun onScreen(key: String, width: Float, height: Float): CardSpot? =
        spots[key]?.takeIf { it.rect.width > 0f && it.rect.height > 0f && it.rect.overlaps(Rect(0f, 0f, width, height)) }
}

/** The dock's clock pill, a card of the tabs page on every tab: the running workout grows out of it and shrinks back into it. */
const val DOCK_WORKOUT = "dock:workout"

/** The tab a card key belongs to (`home:...`, `progress:...`, `profile:...`, `workout:...`, `exercises:...`). */
fun tabOfCard(key: String): Tab? = when (key.substringBefore(':')) {
    "home" -> Tab.Home
    "progress" -> Tab.Progress
    "exercises" -> Tab.Exercises
    "profile" -> Tab.Profile
    else -> null
}

/** The app's one container clip, for the frame tests. */
@OptIn(ExperimentalSharedTransitionApi::class)
object MorphProbe {
    @kotlin.concurrent.Volatile var clip: MorphClip? = null
    /** Every shared thing by its full key, to ask whether it found its match (a title found once is one text that moves, not two). */
    val states = HashMap<String, SharedTransitionScope.SharedContentState>()
    /** Every chart line drawn travelling between a card and its page, by `<card>/<role>`. */
    val chartsDrawn: MutableSet<String> = HashSet()
    /** The progress (0 card, 1 page) of the travelling line in the last frame it was drawn, and how many frames it has been drawn in; for tests. */
    @kotlin.concurrent.Volatile var chartQ = -1f
    @kotlin.concurrent.Volatile var chartDraws = 0
    /** The words each travelling text shows by `<card>/<role>[/<id>]/card|page`, and where its box is in the window now. */
    val texts = HashMap<String, MorphTextSpec>()
    val boxes = HashMap<String, Rect>()
    /** What the root last provided ([LocalSolidGlass], [LocalPerformance], [LocalMotion]), for the tier tests. */
    @kotlin.concurrent.Volatile var solidGlass = false
    @kotlin.concurrent.Volatile var performance = Performance.Full
    @kotlin.concurrent.Volatile var moving = true
}

/**
 * The growing container's clip: a rounded rectangle on the shared bounds whose corners go from the card's to the page's (square) as
 * it grows. [begin] is told which card and how big the page is; both halves of the move draw through the same clip, so the outline
 * ([CardEdge]) is drawn from the very bounds the content was clipped to in that frame.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class MorphClip : SharedTransitionScope.OverlayClip {
    private var from: CardSpot? = null
    private var page = Size.Zero
    /** The container's bounds and how far it has grown (0 the card, 1 the page) as of the last frame drawn. */
    var rect = Rect.Zero
        private set
    var grown by androidx.compose.runtime.mutableFloatStateOf(0f)
        private set
    /** The corner radius the container has now, in px. */
    var corner = 0f
        private set

    /** How many times the container has been drawn; the frame tests (MotionFramesTest) read it, with [rect] and [grown], to follow the container. */
    var draws = 0
        private set
    init { MorphProbe.clip = this }

    /**
     * Where the top of the tab bar is now (in the window), or [Float.MAX_VALUE] when it is not in the way: a card that lies partly behind the bar
     * is drawn only above it, so its hidden part is not shown from the tap on, and shows as the bar leaves.
     */
    var barTop: () -> Float = { Float.MAX_VALUE }

    fun begin(spot: CardSpot, pageSize: Size) { from = spot; page = pageSize }
    /** [path] cut to the area above the tab bar. */
    fun aboveBar(path: Path, bounds: Rect): Path {
        val cut = barTop()
        if (cut >= bounds.bottom) return path
        return Path.combine(androidx.compose.ui.graphics.PathOperation.Intersect, path, Path().apply { addRect(Rect(bounds.left - 1f, bounds.top - 1f, bounds.right + 1f, cut)) })
    }
    /** The card's bounds in the window, where the container starts (and, going back, ends). */
    val start: Rect? get() = from?.rect
    val outline: BorderStroke? get() = from?.border
    /** The colour the container starts in, laid over the page's background: the card's fill (or its glass), fading away as the container grows; clear for a row on the page. */
    val fill: androidx.compose.ui.graphics.Color? get() = from?.fill

    override fun getClipPath(state: SharedTransitionScope.SharedContentState, bounds: Rect, layoutDirection: LayoutDirection, density: Density): Path? {
        val card = from ?: return null
        fun share(now: Float, start: Float, end: Float) = if (abs(end - start) < 1f) 0f else ((now - start) / (end - start)).coerceIn(0f, 1f)
        rect = bounds
        draws++
        grown = max(share(bounds.width, card.rect.width, page.width), share(bounds.height, card.rect.height, page.height))
        corner = card.corner * (1f - grown)
        return aboveBar(Path().apply { addRoundRect(RoundRect(bounds, CornerRadius(corner))) }, bounds)
    }
}

/** No clip: for what may pass over the container's edge on its way (a title). */
@OptIn(ExperimentalSharedTransitionApi::class)
object NoClip : SharedTransitionScope.OverlayClip {
    override fun getClipPath(state: SharedTransitionScope.SharedContentState, bounds: Rect, layoutDirection: LayoutDirection, density: Density): Path? = null
}

/**
 * What the shared elements are clipped to: the container, as it was drawn in this frame (the container is drawn first), so nothing
 * that moves between card and page is ever outside it.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class FollowClip(private val container: MorphClip) : SharedTransitionScope.OverlayClip {
    override fun getClipPath(state: SharedTransitionScope.SharedContentState, bounds: Rect, layoutDirection: LayoutDirection, density: Density): Path? {
        if (container.rect.isEmpty) return null
        return container.aboveBar(Path().apply { addRoundRect(RoundRect(container.rect, CornerRadius(container.corner))) }, container.rect)
    }
}

/** Runs a card's tap under its key, so the page it opens grows out of the card. */
@Composable
fun cardTap(onClick: () -> Unit): () -> Unit {
    val key = LocalCardKey.current ?: return onClick
    val press = LocalCardPress.current
    return { press(key, onClick) }
}

/**
 * The card side of a card that opens a page: its bounds are the container's start (and end, going back), and while it opens its own
 * content fades out in the first part of the clock and, going back, returns in the last. Put it before the card's clip and press
 * modifiers. Nothing but a tag and a note of where the card is when the card has no key or animations are off. A row that opens a page
 * is a container the same way ([square]: corners of 0; [border] null; [fill] the card's it sits on, or transparent on the page itself); [round] is a pill (the dock's clock).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.opensInto(border: BorderStroke?, fill: androidx.compose.ui.graphics.Color, square: Boolean = false, round: Boolean = false, frosted: Boolean = false): Modifier {
    val key = LocalCardKey.current ?: return this
    val bounds = LocalCardBounds.current
    val corner = if (square) 0f else with(LocalDensity.current) { cardCorner().toPx() }
    DisposableEffect(key) { onDispose { bounds?.remove(key) } }
    val page = MaterialTheme.colorScheme.background
    // Round 34 (I-145): the spot keeps the fill as it lies over the page's background, not flattened over the page colour: a row on the page has none, and a frosted card
    // has its tone at the glass's tint (the unblurred background under it is the nearest the container's first frame can come to the blur).
    val frost = LocalFrost.current
    val spotFill = if (frosted && frost != null && fill.alpha > 0f) frostTone(fill, page, frost.tint).copy(alpha = frost.tint) else fill
    val noted = testTag("card:$key").onGloballyPositioned { bounds?.put(key, CardSpot(it.boundsInRoot(), if (round) it.size.height / 2f else corner, border, spotFill)) }
    val shared = LocalSharedScope.current
    val scope = LocalPageScope.current
    val clip = LocalMorphClip.current
    if (shared == null || scope == null || clip == null || !LocalMotion.current) return noted
    val forward = LocalPageForward.current
    val morphingNow = morphingOf(key)
    // Only the card being opened fades (out in the first part of the clock, back in the last); the others stay as they are. The fade is
    // always set up, so it is there on the frame the card is tapped.
    val mine = morphCardOf(key) == key
    val progress = progressOf(key)
    return with(shared) {
        noted.sharedBounds(
            rememberSharedContentState("card:$key", MorphConfig(morphingNow)), scope, androidx.compose.animation.EnterTransition.None, androidx.compose.animation.ExitTransition.None,
            { _, _ -> Motion.cardBounds(forward()) }, resizeMode = UNSCALED, clipInOverlayDuringTransition = clip,
            // Above the growing container, so its content is seen fading out and not hidden at once.
            zIndexInOverlay = 1f,
        ).graphicsLayer {
            // How far the container has gone from the card (0) to the page (1), on the container's own clock: the card's content fades on
            // it and stays where it rests on the card while the container moves away (the container draws it at its own corner).
            val q = if (mine && shared.isTransitionActive) progress?.value ?: 0f else 0f
            alpha = Motion.cardContentAlpha(q)
            clip.start?.let { translationX = it.left * q; translationY = it.top * q }
        }
    }
}

/** A chart or calendar that becomes another is stretched to the bounds while it crosses over (it has no text to blow up). */
@OptIn(ExperimentalSharedTransitionApi::class)
private val FILLED = SharedTransitionScope.ResizeMode.scaleToBounds(androidx.compose.ui.layout.ContentScale.FillBounds, androidx.compose.ui.Alignment.Center)

/** A page's content is never scaled while its container grows or shrinks: it keeps its own size and is clipped. */
@OptIn(ExperimentalSharedTransitionApi::class)
private val UNSCALED = SharedTransitionScope.ResizeMode.scaleToBounds(androidx.compose.ui.layout.ContentScale.None, androidx.compose.ui.Alignment.TopStart)

/**
 * The page side of [opensInto]: the root of the page grown from the card [key] (null for a page opened any other way, or when the card
 * could not be found, which are left as they are: the blink). While it moves the page has a floor of its own, or the tabs page would
 * show through the growing container, and its content fades in after the card's has gone (and, going back, out before the card's returns).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.cardTarget(key: String?, morphing: Boolean, backdrop: PageBackdrop? = null): Modifier {
    val shared = LocalSharedScope.current
    val page = LocalPageScope.current
    val clip = LocalMorphClip.current
    // The page's own background, behind its content (Round 34, I-145): the same layer the tabs have, so a page over a patterned background is not a flat patch.
    val paint = if (backdrop != null) Modifier.drawBehind { backdrop.draw(this) } else Modifier
    if (key == null || shared == null || page == null || clip == null || !LocalMotion.current) return this.then(paint)
    val forward = LocalPageForward.current
    val morphingNow = morphingOf(key)
    val background = MaterialTheme.colorScheme.background
    // While the container grows, the page's background is the floor: the ground and the backdrop with the card's fill over it, the fill fading as the container grows.
    // It lies in the page's own frame (below), so a pattern in it is where the tabs' is, and is clipped to the container. Only the page that is growing has one: the page
    // under it may itself have grown from a card, and keeps its own background while the other change runs. Otherwise the background is drawn with the page's content
    // (inside its fade), so a page that has not faded in yet is not a full-screen sheet over the tabs.
    val floorOn = morphing && shared.isTransitionActive
    val floor = if (floorOn) Modifier.drawBehind {
        if (backdrop != null) backdrop.draw(this) else drawRect(background)
        clip.fill?.let { drawRect(it.copy(alpha = it.alpha * (1f - clip.grown))) }
    } else Modifier
    val progress = progressOf(key)
    // The floor goes after the shared bounds, so it is part of the growing container and clipped to it, not a full-screen sheet over the tabs.
    // The container is solid from the first frame; only the page's content fades in over the floor.
    return with(shared) {
        sharedBounds(
            rememberSharedContentState("card:$key", MorphConfig(morphingNow)), page, androidx.compose.animation.EnterTransition.None, androidx.compose.animation.ExitTransition.None,
            { _, _ -> Motion.cardBounds(forward()) }, resizeMode = UNSCALED, clipInOverlayDuringTransition = clip, zIndexInOverlay = 0.5f,
        ).graphicsLayer {
            // The page is where it rests on the page, not riding in from the container's top corner (its floor with it).
            val q = if (morphing && shared.isTransitionActive) progress?.value ?: 1f else 1f
            clip.start?.let { translationX = -it.left * (1f - q); translationY = -it.top * (1f - q) }
        }.then(floor).graphicsLayer {
            // The page's own content fades in after the card's has gone.
            val q = if (morphing && shared.isTransitionActive) progress?.value ?: 1f else 1f
            alpha = Motion.pageContentAlpha(q)
        }.then(if (floorOn) Modifier else paint)
    }
}

/**
 * The container's outline while it grows: the card's own, carried along the edge of the growing container (drawn from the bounds the
 * container was clipped to in the same frame, above the page and below the card's content) and gone as the container becomes the page.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CardEdge() {
    val shared = LocalSharedScope.current ?: return
    val clip = LocalMorphClip.current ?: return
    with(shared) {
        Box(
            Modifier.fillMaxSize()
                .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 0.75f) { shared.isTransitionActive }
                .drawWithContent {
                    val border = clip.outline
                    if (!shared.isTransitionActive || border == null || clip.rect.isEmpty) return@drawWithContent
                    val fade = 1f - ((clip.grown - Motion.OUTLINE_FADE_FROM) / (1f - Motion.OUTLINE_FADE_FROM)).coerceIn(0f, 1f)
                    if (fade <= 0f) return@drawWithContent
                    val width = border.width.toPx()
                    val r = clip.rect.deflate(width / 2f)
                    drawRoundRect(
                        border.brush, r.topLeft, r.size, CornerRadius(maxOf(0f, clip.corner - width / 2f)),
                        style = Stroke(width), alpha = fade,
                    )
                },
        )
    }
}

/**
 * Marks what is on both a card and its page under [role] (and [id] when there are several, like a workout's row): on the card side
 * (the key of the card being drawn) and on the page side (the key of the card the page grew from), so it travels from its place on
 * the card to its place on the page and back, resizing, instead of fading and appearing again. [same] is for the very same thing
 * (the body figure), drawn once at the animated size; otherwise the two are different (a title, a number, a chart) and cross over
 * as they travel; [late] is for what travels with no counterpart to become (a row going below the page), which fades only near the end
 * of the way. Does nothing when the page was not opened by a card or animations are off.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.morphs(role: String, id: String? = null, same: Boolean = false, scaled: Boolean = false, clipped: Boolean = true, late: Boolean = false): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val scope = LocalPageScope.current ?: return this
    val key = LocalCardKey.current ?: LocalPageCard.current ?: return this
    if (!LocalMotion.current) return this
    val forward = LocalPageForward.current
    val morphingNow = morphingOf(key)
    val enabledHere = LocalMorphEnabled.current
    val container = LocalMorphClip.current ?: return this
    val follow = remember(container) { FollowClip(container) }
    // A History row's name, date and PR count each go by legs, so they never cross ([Motion.legged]).
    val legged = role == "row:name" || role == "row:date" || role == "row:prs"
    val bounds: androidx.compose.animation.BoundsTransform = androidx.compose.animation.BoundsTransform { initial, target -> if (legged) Motion.legged(initial, target, forward()) else Motion.cardBounds(forward()) }
    val full0 = if (id == null) "$key/$role" else "$key/$role/$id"
    val side = if (LocalCardKey.current != null) "card" else "page"
    return with(shared) {
        val full = if (id == null) "$key/$role" else "$key/$role/$id"
        val state = rememberSharedContentState(full, MorphConfig(if (!enabledHere()) ({ false }) else if (role != "title" && role != "number") morphingNow else {
            // A title must be known to the match before the change starts, or it starts from where it is going: the card's while the tabs
            // page is shown, the page's while it is there; the pair only meets when a card change is under way or the tabs page is shown.
            // A page that is leaving for another page (its own card's change over, not under way) has nothing to match: its title stays as it is.
            ({ morphingNow() || scope.transition.targetState == EnterExitState.Visible })
        }))
        MorphProbe.states[full] = state
(        if (same) sharedElement(state, scope, bounds, zIndexInOverlay = 2f, clipInOverlayDuringTransition = if (clipped) follow else NoClip)
        else sharedBounds(state, scope, fadeIn(if (late) Motion.rowIn() else Motion.morphIn()), fadeOut(if (late) Motion.rowOut() else Motion.morphOut()), bounds, resizeMode = if (scaled || role.startsWith("chart:") || role == "calendar") FILLED else UNSCALED, zIndexInOverlay = 2f, clipInOverlayDuringTransition = if (clipped) follow else NoClip)).then(Modifier.onGloballyPositioned { MorphProbe.boxes[full0 + "/" + side] = it.boundsInRoot() })
    }
}

/** [morphs] for what is only the card's side (its title: a page's own sections have titles too, and only one thing on the page is the match). */
@Composable
fun Modifier.morphsFromCard(role: String, id: String? = null): Modifier = if (LocalCardKey.current == null) this else morphs(role, id)

/**
 * What one side of a label shows: its words and how they are set, so the other side can draw them in the same moving box. [rest] is the
 * constraints this side's words are laid out in at rest (noted when its screen is laid out, so a travelling copy of the words is set
 * exactly as they are there: the same width, the same ellipsis), [overflowed] and [lines] how they came out.
 */
class MorphTextSpec(
    val text: androidx.compose.ui.text.AnnotatedString, val style: androidx.compose.ui.text.TextStyle, val color: androidx.compose.ui.graphics.Color, val maxLines: Int,
    val overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip, val softWrap: Boolean = true,
    val textAlign: androidx.compose.ui.text.style.TextAlign? = null,
) {
    @kotlin.concurrent.Volatile var rest: androidx.compose.ui.unit.Constraints? = null
    /** How wide the words come out at rest, in px: the box that carries them is scaled to fit between the two sides' widths. */
    @kotlin.concurrent.Volatile var shown = 0
    /** The width of the box that carries the words at rest, and now (the box travels by legs, so its width is not the clock's progress). */
    @kotlin.concurrent.Volatile var boxW = 0
    @kotlin.concurrent.Volatile var boxNow = 0
    @kotlin.concurrent.Volatile var overflowed = false
    @kotlin.concurrent.Volatile var lines = 1
    /** Whether the other side's words are set the same way and both fit, so one text can stand for both while they travel. */
    fun looksLike(o: MorphTextSpec) = text == o.text && style == o.style && color == o.color && maxLines == o.maxLines && overflow == o.overflow && softWrap == o.softWrap && textAlign == o.textAlign &&
        !overflowed && !o.overflowed && lines == o.lines
}

/** The words each card label and page title (or number) shows, by `<card>/<role>/card|page`. */
val LocalMorphTexts = staticCompositionLocalOf<androidx.compose.runtime.snapshots.SnapshotStateMap<String, MorphTextSpec>?> { null }

/**
 * A text that is the card's label (or number) and, on the page it opens, the page's title (or number), in different words or form
 * ("WEEK STREAK" and "Trophies", "6" and "6 weeks"): one box that travels from the label's place to the title's, resizing. Each side's
 * words are always set the way that side sets them at rest (its own width, lines, ellipsis, size and colour, never unbounded), so
 * nothing about them changes the moment the card is tapped or the move ends; while the box travels it draws both, each scaled from its
 * own size to the box's and anchored at the start, the card's fading out and the page's in during the middle of the way
 * ([Motion.textOut], [Motion.textIn]), which hides every difference between them (the ellipsis, the wording, the weight, the colour).
 * The words never draw outside the box. Both sides draw this same way ([role] "title" or "number"; the page's passes [onPage]). With no
 * card or page to pair with, or animations off, it is a plain Text.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MorphText(
    text: androidx.compose.ui.text.AnnotatedString, role: String?, modifier: Modifier = Modifier, onPage: Boolean = false,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.ui.text.TextStyle.Default, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null, textAlign: androidx.compose.ui.text.style.TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE, overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip, softWrap: Boolean = true,
    onTextLayout: ((androidx.compose.ui.text.TextLayoutResult) -> Unit)? = null, id: String? = null,
    /** Where the words sit in their box: at its start, or at its end for a column of figures set to the right. */
    alignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.CenterStart,
) {
    val resolved = androidx.compose.material3.LocalTextStyle.current.merge(style).let { if (fontWeight != null) it.merge(androidx.compose.ui.text.TextStyle(fontWeight = fontWeight)) else it }
    val ink = if (color.isSpecified) color else if (resolved.color.isSpecified) resolved.color else androidx.compose.material3.LocalContentColor.current
    val cardKey = LocalCardKey.current
    val key = cardKey ?: if (onPage) LocalPageCard.current else null
    val scope = LocalPageScope.current
    val texts = LocalMorphTexts.current
    val forward = LocalPageForward.current
    val morphingNow = if (key != null) morphingOf(key) else LocalMorphing.current
    if (role == null || key == null || scope == null || texts == null || !LocalMotion.current) {
        androidx.compose.material3.Text(text, modifier, ink, style = resolved, textAlign = textAlign, maxLines = maxLines, overflow = overflow, softWrap = softWrap, onTextLayout = onTextLayout ?: {})
        return
    }
    val cardSide = cardKey != null
    val enabled = LocalMorphEnabled.current
    val atEnd = alignment == androidx.compose.ui.Alignment.CenterEnd
    val mine = remember(text, resolved, ink, maxLines, overflow, softWrap, textAlign) { MorphTextSpec(text, resolved, ink, maxLines, overflow, softWrap, textAlign) }
    // [id] tells one text of a kind from another (a workout's name on a row of the History card and on its card on the page).
    val named = if (id == null) "$key/$role/" else "$key/$role/$id/"
    val here = named + if (cardSide) "card" else "page"
    val there = named + if (cardSide) "page" else "card"
    DisposableEffect(here, mine) {
        texts[here] = mine
        MorphProbe.texts[here] = mine
        onDispose { if (texts[here] === mine) texts.remove(here); if (MorphProbe.texts[here] === mine) MorphProbe.texts.remove(here); MorphProbe.boxes.remove(here) }
    }
    val other = texts[there]
    // The words' scale as the box travels: each version is as wide as the box's words are, the card's at its own width at 0 and the page's at 1 (so a
    // version never sticks out of the box, whatever the two sizes and wordings are).
    fun fit(version: MorphTextSpec, p: Float): Float {
        val card = (if (cardSide) mine.shown else other?.shown ?: 0).toFloat()
        val page = (if (cardSide) other?.shown ?: 0 else mine.shown).toFloat()
        if (card <= 0f || page <= 0f || version.shown <= 0) return 1f
        // How far the box has come, by its width when the two boxes differ in width (it goes by legs), else by the clock.
        val boxCard = (if (cardSide) mine.boxW else other?.boxW ?: 0).toFloat()
        val boxPage = (if (cardSide) other?.boxW ?: 0 else mine.boxW).toFloat()
        val u = if (kotlin.math.abs(boxPage - boxCard) > 2f && mine.boxNow > 0) ((mine.boxNow - boxCard) / (boxPage - boxCard)).coerceIn(0f, 1f) else p
        return (card + (page - card) * u) / version.shown
    }
    // How far the label has become the title (0 the card's words, 1 the page's), on the clock the box travels on.
    val toPage by scope.transition.animateFloat({ if (!morphingNow()) snap() else Motion.cardBounds(forward()) }, label = "label") {
        if (cardSide) (if (it == EnterExitState.Visible) 0f else 1f) else (if (it == EnterExitState.Visible) 1f else 0f)
    }
    // Words set where they rest, whatever box they are drawn in: the constraints are noted when the screen is laid out, and used while the box travels.
    val resting = Modifier.layout { measurable, constraints ->
        val rest = mine.rest
        if (isLookingAhead) mine.rest = constraints
        val placeable = measurable.measure(if (!isLookingAhead && rest != null && morphingNow()) rest else constraints)
        if (isLookingAhead) mine.shown = placeable.width
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    Box(
        modifier.morphs(role, id, same = true).layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            if (isLookingAhead) mine.boxW = placeable.width else mine.boxNow = placeable.width
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }.onGloballyPositioned { MorphProbe.boxes[here] = it.boundsInRoot() }.drawWithContent {
            // Nothing that travels is drawn outside its own box.
            if (morphingNow()) clipRect { this@drawWithContent.drawContent() } else drawContent()
        },
        contentAlignment = alignment,
    ) {
        androidx.compose.material3.Text(
            text, resting.graphicsLayer {
                if (!morphingNow()) return@graphicsLayer
                val p = toPage
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (atEnd) 1f else 0f, 0.5f)
                when {
                    // Words with no counterpart (a row going below the page, a label the page has no title for) stay as they are: the card's content fades with the
                    // card and the page's comes in with the page, in their own places, so there is nothing for these words to cross over to.
                    other == null || !enabled() -> {}
                    mine.looksLike(other) -> {}
                    else -> {
                        alpha = if (cardSide) Motion.textOut(p) else Motion.textIn(p)
                        val s = fit(mine, p)
                        scaleX = s; scaleY = s
                    }
                }
            },
            ink, style = resolved, textAlign = textAlign, maxLines = maxLines, overflow = overflow, softWrap = softWrap,
            // What the box does while it travels says nothing about whether the words fit where they rest (a number that shrinks to fit must not shrink for good).
            onTextLayout = {
                mine.overflowed = it.hasVisualOverflow; mine.lines = it.lineCount
                if (!morphingNow()) onTextLayout?.invoke(it)
            },
        )
        // The other side's words, set as that side sets them, drawn in the same box (they never make it bigger).
        if (other != null) Box(Modifier.matchParentSize().wrapContentSize(alignment, unbounded = true)) {
            androidx.compose.material3.Text(
                other.text,
                Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(other.rest ?: constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }.clearAndSetSemantics { }.graphicsLayer {
                    if (!morphingNow() || !enabled() || mine.looksLike(other)) { alpha = 0f; return@graphicsLayer }
                    val p = toPage
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (atEnd) 1f else 0f, 0.5f)
                    alpha = if (cardSide) Motion.textIn(p) else Motion.textOut(p)
                    val s = fit(other, p)
                    scaleX = s; scaleY = s
                },
                other.color, style = other.style, textAlign = other.textAlign, maxLines = other.maxLines, overflow = other.overflow, softWrap = other.softWrap,
            )
        }
    }
}

@Composable
fun MorphText(
    text: String, role: String, modifier: Modifier = Modifier, onPage: Boolean = false, fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE, overflow: androidx.compose.ui.text.style.TextOverflow = androidx.compose.ui.text.style.TextOverflow.Clip,
) = MorphText(androidx.compose.ui.text.AnnotatedString(text), role, modifier, onPage, fontWeight = fontWeight, maxLines = maxLines, overflow = overflow)

/** One side of a chart that morphs: its line as points in the window (x0, y0, x1, y1, ...), resampled to [CHART_SAMPLES], and how it is drawn. */
class ChartLine(val rawX: FloatArray, val rawY: FloatArray, val points: FloatArray, val color: androidx.compose.ui.graphics.Color, val width: Float, val dot: androidx.compose.ui.graphics.Color?, val dotRadius: Float, val wash: ChartWash? = null) {
    /** [rawY] with its small wiggles averaged away ([smoothedLine]): the line's course without its detail (see [pageDetailShare]). */
    val smoothY: FloatArray by lazy { smoothedLine(rawY) }
}

/** [y] as a moving average over about a twenty-fourth of its points either side (at least one), the ends clamped: the line's course without its day-to-day wiggle. */
fun smoothedLine(y: FloatArray): FloatArray {
    val n = y.size
    val half = maxOf(1, n / 24)
    val sums = DoubleArray(n + 1)
    for (i in 0 until n) sums[i + 1] = sums[i] + y[i]
    return FloatArray(n) { i ->
        val a = maxOf(0, i - half)
        val b = minOf(n - 1, i + half)
        ((sums[b + 1] - sums[a]) / (b - a + 1)).toFloat()
    }
}

/**
 * How much of a dense page line's own wiggle the travelling line carries at progress [q] (0 the card, 1 the page): none in the last 8 % of the way to the card, all of it from 40 %,
 * a smooth step between. Going Back the line is the card's smooth shape well before the card lands, so no wiggle is left to flatten after it has; at the page's end it is the
 * page's own line to the pixel (Round 21).
 */
fun pageDetailShare(q: Float): Float {
    val t = ((q - 0.08f) / 0.32f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The tint under a chart's line, down to [floor] (all y in the window): [alphaTop] of the line's colour from [top] fading to [alphaBottom] at the
 * floor, and, when [reach] is more than 0, fading out sideways over that many px at the line's far end (and at its start unless [edge], the
 * line running to the card's edge). The travelling line carries it, so it moves and reshapes with the line instead of fading where it was.
 */
class ChartWash(val floor: Float, val top: Float, val alphaTop: Float, val alphaBottom: Float, val reach: Float, val edge: Boolean)

/** The charts' lines by `<card>/<role>/card|page`. */
val LocalChartLines = staticCompositionLocalOf<androidx.compose.runtime.snapshots.SnapshotStateMap<String, ChartLine>?> { null }

/**
 * Points per side of a travelling chart line. Dense enough that the line drawn through them as straight steps is, at the end of the
 * move, the chart's own curve to the pixel, so the real line takes over with no jump (with 48 the page's finer, less smoothed line
 * popped in after the move).
 */
const val CHART_SAMPLES = 240

/**
 * The curve a chart draws through [xs], [ys] ([curveThrough]: the same monotone cubic) at [n] evenly spaced x, offset by [origin].
 * Sampling the curve itself, not straight lines between the points, is what makes the travelling line end exactly as the chart's.
 */
fun resampleLine(xs: FloatArray, ys: FloatArray, origin: androidx.compose.ui.geometry.Offset, n: Int = CHART_SAMPLES): FloatArray {
    val out = FloatArray(n * 2)
    val m = clean.core.monotoneSlopes(DoubleArray(xs.size) { xs[it].toDouble() }, DoubleArray(ys.size) { ys[it].toDouble() })
    var k = 0
    for (i in 0 until n) {
        val x = xs.first() + (xs.last() - xs.first()) * i / (n - 1)
        while (k < xs.size - 2 && xs[k + 1] < x) k++
        val dx = xs[k + 1] - xs[k]
        val y = if (dx <= 0f) ys[k + 1] else {
            // Hermite form of the cubic curveThrough draws between points k and k + 1.
            val t = ((x - xs[k]) / dx).coerceIn(0f, 1f)
            val t2 = t * t; val t3 = t2 * t
            (2 * t3 - 3 * t2 + 1) * ys[k] + (t3 - 2 * t2 + t) * dx * m[k].toFloat() + (-2 * t3 + 3 * t2) * ys[k + 1] + (t3 - t2) * dx * m[k + 1].toFloat()
        }
        out[2 * i] = origin.x + x
        out[2 * i + 1] = origin.y + y
    }
    return out
}

/** A chart's half of the one line that travels between card and page: [modifier] notes where its line is, [hidden] says when the travelling line stands in for it. */
class ChartMorphSide(val hidden: () -> Boolean, val modifier: Modifier)

/**
 * Makes the line of a chart on a card and the line of the chart on its page (the same [role]) one line while the card opens or closes:
 * both real lines step aside and [ChartMorphLine] draws a line that moves and reshapes from the one to the other, point by point.
 * [line] gives the line's points for the chart's size, in the chart's own px. With no pair, or animations off, nothing changes.
 */
@Composable
fun rememberChartMorph(
    role: String?, color: androidx.compose.ui.graphics.Color, width: Float, dot: androidx.compose.ui.graphics.Color? = null, dotRadius: Float = 0f,
    wash: ((Size) -> ChartWash?)? = null,
    line: (Size) -> Pair<FloatArray, FloatArray>?,
): ChartMorphSide {
    val cardKey = LocalCardKey.current
    val key = cardKey ?: LocalPageCard.current
    val lines = LocalChartLines.current
    if (role == null || key == null || lines == null || !LocalMotion.current || !LocalPerformance.current.morphLines) return ChartMorphSide({ false }, Modifier)
    val here = "$key/$role/" + if (cardKey != null) "card" else "page"
    val there = "$key/$role/" + if (cardKey != null) "page" else "card"
    val morphingNow = morphingOf(key)
    val mine = morphCardOf(key) == key
    // The travelling line is drawn while the move is between its ends (0 the card, 1 the page); at either end the chart's own line is, in the same frame, so no frame has both lines or neither.
    val progress = progressOf(key)
    val current = androidx.compose.runtime.rememberUpdatedState(line)
    DisposableEffect(here) { onDispose { lines.remove(here) } }
    return ChartMorphSide(
        { mine && morphingNow() && progress?.value?.let { it > 0f && it < 1f } == true && lines[here] != null && lines[there] != null },
        Modifier.onGloballyPositioned { c ->
            val (xs, ys) = current.value(Size(c.size.width.toFloat(), c.size.height.toFloat())) ?: return@onGloballyPositioned run { lines.remove(here); Unit }
            if (xs.size < 2) { lines.remove(here); return@onGloballyPositioned }
            val at = c.positionInRoot()
            lines[here] = ChartLine(xs.map { it + at.x }.toFloatArray(), ys.map { it + at.y }.toFloatArray(), resampleLine(xs, ys, at, (xs.size * 16).coerceIn(CHART_SAMPLES, 8000)), color, width, dot, dotRadius, wash?.invoke(Size(c.size.width.toFloat(), c.size.height.toFloat()))?.let { ChartWash(it.floor + at.y, it.top + at.y, it.alphaTop, it.alphaBottom, it.reach, it.edge) })
        },
    )
}

/**
 * Where the travelling line is (0 the card, 1 the page) at the move's progress [q]: it is the card's line, in the card's place, over the last
 * [CHART_CARD_HOLD] of the way. The move's easing spends its last third of the time on that last tenth of the way, and a line still going
 * a tenth of the long way from the page's chart drifts visibly after the card and its words have landed; held, it is still by then (Round 25).
 */
const val CHART_CARD_HOLD = 0.12f
fun chartLineProgress(q: Float): Float = ((q - CHART_CARD_HOLD) / (1f - CHART_CARD_HOLD)).coerceIn(0f, 1f)

/** The one line of a chart on both the card and its page, drawn over both while the card [card] opens or closes ([rememberChartMorph]). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ChartMorphLine(card: String) {
    val shared = LocalSharedScope.current ?: return
    val lines = LocalChartLines.current ?: return
    val progress = LocalBarOut.current ?: return
    val morphingNow = LocalMorphing.current
    with(shared) {
        Box(
            Modifier.fillMaxSize()
                .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 2.5f) { shared.isTransitionActive }
                .drawWithContent {
                    if (!morphingNow()) return@drawWithContent
                    val raw = progress.value
                    // At the ends the chart's own line is drawn (see [rememberChartMorph]), so this one stops in the frame it takes over.
                    if (raw <= 0f || raw >= 1f) return@drawWithContent
                    val q = chartLineProgress(raw)
                    for ((name, from) in lines) {
                        if (!name.startsWith("$card/") || !name.endsWith("/card")) continue
                        val to = lines[name.removeSuffix("/card") + "/page"] ?: continue
                        MorphProbe.chartsDrawn += name.removeSuffix("/card")
                        MorphProbe.chartQ = q; MorphProbe.chartDraws++
                        // The grid is the denser side's own points, so at its end the line is that chart's own curve through its own points (a page of hundreds of
                        // weigh-ins keeps its crooked shape to the last frame and the real line takes over pixel for pixel); the other side is read at the same
                        // places along its length from its finely sampled curve.
                        val gridIsCard = from.rawX.size >= to.rawX.size
                        val g = if (gridIsCard) from else to
                        val o = if (gridIsCard) to else from
                        val n = g.rawX.size
                        val span = (g.rawX.last() - g.rawX[0]).coerceAtLeast(0.001f)
                        val m = o.points.size / 2
                        fun other(i: Int, k: Int): Float {
                            val u = (g.rawX[i] - g.rawX[0]) / span * (m - 1)
                            val j = u.toInt().coerceIn(0, m - 2)
                            return o.points[2 * j + k] + (o.points[2 * j + 2 + k] - o.points[2 * j + k]) * (u - j)
                        }
                        fun mix(mine: Float, theirs: Float) = if (gridIsCard) mine * (1f - q) + theirs * q else theirs * (1f - q) + mine * q
                        val xs = FloatArray(n) { mix(g.rawX[it], other(it, 0)) }
                        // A page's dense line brings its wiggle only as far along as [pageDetailShare] lets it, so near the card it is the card's own course.
                        val share = if (gridIsCard) 1f else pageDetailShare(q)
                        val ys = FloatArray(n) { mix(if (gridIsCard) g.rawY[it] else g.smoothY[it] + (g.rawY[it] - g.smoothY[it]) * share, other(it, 1)) }
                        val path = Path().apply { curveThrough(xs, ys) }
                        val color = androidx.compose.ui.graphics.lerp(from.color, to.color, q)
                        // The tint under the line goes with it: from the card's to the page's (or to none, or from none).
                        fun flat(w: ChartWash) = ChartWash(w.floor, w.top, 0f, 0f, w.reach, w.edge)
                        val a = from.wash ?: to.wash?.let(::flat)
                        val b = to.wash ?: from.wash?.let(::flat)
                        if (a != null && b != null) {
                            fun mix(x: Float, y: Float) = x + (y - x) * q
                            val top = mix(a.top, b.top)
                            val floor = mix(a.floor, b.floor)
                            val above = mix(a.alphaTop, b.alphaTop)
                            val below = mix(a.alphaBottom, b.alphaBottom)
                            val reach = mix(a.reach, b.reach)
                            if (above > 0f || below > 0f) {
                                val tint = Path().apply { addPath(path); lineTo(xs.last(), floor); lineTo(xs.first(), floor); close() }
                                val brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color.copy(alpha = above), color.copy(alpha = below)), startY = top, endY = floor)
                                if (reach > 0f) {
                                    val edge = if (q < 0.5f) a.edge else b.edge
                                    val share = reach / (xs.last() - xs.first()).coerceAtLeast(1f)
                                    val inFrom = if (edge) 0f else share.coerceAtMost(0.5f)
                                    val mask = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                        0f to (if (edge) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.Transparent), inFrom to androidx.compose.ui.graphics.Color.Black,
                                        (1f - share).coerceAtLeast(inFrom) to androidx.compose.ui.graphics.Color.Black, 1f to androidx.compose.ui.graphics.Color.Transparent,
                                        startX = xs.first(), endX = xs.last(),
                                    )
                                    drawContext.canvas.saveLayer(tint.getBounds(), androidx.compose.ui.graphics.Paint())
                                    drawPath(tint, brush)
                                    drawRect(mask, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                                    drawContext.canvas.restore()
                                } else drawPath(tint, brush)
                            }
                        }
                        drawPath(path, color, style = Stroke(from.width + (to.width - from.width) * q, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                        val dot = to.dot ?: from.dot
                        if (dot != null) {
                            val r = (if (from.dot != null) from.dotRadius else 0f) * (1f - q) + (if (to.dot != null) to.dotRadius else 0f) * q
                            if (r > 0f) drawCircle(dot, r, androidx.compose.ui.geometry.Offset(xs.last(), ys.last()))
                        }
                    }
                },
        )
    }
}
