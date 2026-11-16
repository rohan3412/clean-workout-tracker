package clean.app

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * Stage F3 (2026-11-16): what the user last saw (so a number or a ring that changed while its card was away shows the old figure until
 * the card is in view, then moves once), numbers that roll digit by digit, and a page's first rows settling in.
 */

/** What the user last saw for each thing that moves to a new value, by key, for the session; provided by [App] from the model, so a new app session starts empty. */
internal val LocalSeen = staticCompositionLocalOf { HashMap<String, Any?>() }

/** Whether the composable this goes on is inside the window: a page of the pager that is composed beside the one shown, or a card of another tab, is not. */
internal class Spot(val visible: Boolean, val modifier: Modifier)

@Composable
internal fun rememberSpot(): Spot {
    var visible by remember { mutableStateOf(false) }
    val window = LocalWindowInfo.current.containerSize
    val modifier = Modifier.onGloballyPositioned { at ->
        val b = at.boundsInWindow()
        visible = at.isAttached && b.width > 0f && b.right > 0f && b.left < window.width && b.bottom > 0f && b.top < window.height
    }
    return Spot(visible, modifier)
}

/** [shown] is the value the user last saw until the composable is on screen, then [value]; [spot] goes on the composable so it can tell. */
internal class Seen<T>(val shown: T, val spot: Modifier)

/**
 * The value to draw for [key]: the last one seen while the composable is not on the screen (a card of another tab, or one scrolled
 * away and composed again), [value] from the moment it is. A key seen for the first time, no key, or animations off give [value] at
 * once, so only a change the user was not there for moves, and it moves once.
 */
@Composable
internal fun <T> rememberSeen(key: String?, value: T): Seen<T> {
    if (key == null) return Seen(value, Modifier)
    val store = LocalSeen.current
    val motion = LocalMotion.current
    val spot = rememberSpot()
    @Suppress("UNCHECKED_CAST")
    var shown by remember(key) { mutableStateOf(if (motion && store.containsKey(key)) store[key] as T else value) }
    LaunchedEffect(key, value, spot.visible, motion) {
        if (spot.visible || !motion) { shown = value; store[key] = value }
    }
    return Seen(shown, spot.modifier)
}

/** A text changing, as it is to be drawn: [from] is the text rolling out (null at rest) and [progress] how far the roll has gone. */
internal class Roll(val from: AnnotatedString?, val progress: Float)

/** Rolls from the text last drawn to [text] whenever it changes (not the first time), on [Motion.roll]; nothing with animations off. */
@Composable
internal fun rememberRoll(text: AnnotatedString): Roll {
    val motion = LocalMotion.current
    var settled by remember { mutableStateOf(text) }
    var running by remember { mutableStateOf<AnnotatedString?>(null) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(text, motion) {
        if (text == settled) return@LaunchedEffect
        if (!motion) { settled = text; return@LaunchedEffect }
        running = text
        progress.snapTo(0f)
        progress.animateTo(1f, Motion.roll())
        settled = text
    }
    val from = if (text != settled && motion) settled else null
    // The frame the text changes in already starts the roll, so the new figure is never shown for a frame before it.
    return Roll(from, if (from != null && running != text) 0f else progress.value)
}

/**
 * The digits of [from] rolling to [to]: aligned at the left, where the text is drawn at rest (so nothing shifts when the roll begins), each changed digit slides up out while the new one comes in from below;
 * a changed unit or word crosses over. Drawn over the text at rest (which is hidden meanwhile), so the layout never moves.
 */
@Composable
internal fun RollOverlay(from: AnnotatedString, to: AnnotatedString, progress: Float, style: TextStyle, fontWeight: FontWeight?, modifier: Modifier = Modifier) {
    val n = maxOf(from.length, to.length)
    Row(modifier) {
        for (i in 0 until n) {
            val o = if (i < from.length) i else -1
            val c = if (i < to.length) i else -1
            val old = if (o >= 0) from.subSequence(o, o + 1) else AnnotatedString("")
            val new = if (c >= 0) to.subSequence(c, c + 1) else AnnotatedString("")
            if (old.text == new.text) {
                Text(new, style = style, fontWeight = fontWeight, maxLines = 1, softWrap = false)
                continue
            }
            val digits = old.text.firstOrNull()?.isDigit() == true || new.text.firstOrNull()?.isDigit() == true
            Box(Modifier.clipToBounds()) {
                Text(old, Modifier.graphicsLayer { alpha = 1f - progress; if (digits) translationY = -size.height * progress }, style = style, fontWeight = fontWeight, maxLines = 1, softWrap = false)
                Text(new, Modifier.graphicsLayer { alpha = progress; if (digits) translationY = size.height * (1f - progress) }, style = style, fontWeight = fontWeight, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** A plain text whose digits roll when it changes (and that holds the old figure until it is seen, when it has a [key]). */
@Composable
internal fun RollingText(text: String, key: String?, style: TextStyle, modifier: Modifier = Modifier, fontWeight: FontWeight? = null) {
    val seen = rememberSeen(key, text)
    val words = AnnotatedString(seen.shown)
    val roll = rememberRoll(words)
    Box(modifier.then(seen.spot)) {
        Text(words, Modifier.graphicsLayer { alpha = if (roll.from != null) 0f else 1f }, style = style, fontWeight = fontWeight)
        roll.from?.let { RollOverlay(it, words, roll.progress, style, fontWeight) }
    }
}

// --- A page's first rows settling in ---

/** Whether a page has just opened by the blink: its first rows rise while it does. Made when the page is pushed, by [AppModel.open]. */
internal class SettleState(armed: Boolean) {
    var armed by mutableStateOf(armed)
    /** The next row to ask for its place, for pages whose rows do not know their number ([settleNext]). */
    var next = 0
}

internal val LocalSettle = staticCompositionLocalOf<SettleState?> { null }

/**
 * One of a page's first rows (or cards) rising [Motion.SETTLE_RISE_DP] dp and fading in, [Motion.SETTLE_STEP] ms after the one before.
 * Only the first [Motion.SETTLE_ROWS] and only while the page has just opened by the blink: a page grown from a card, Back to a page,
 * a row scrolled into view and animations off are as they were. Whether a row rises is decided when it is first composed.
 */
internal fun Modifier.settleIn(index: Int): Modifier = composed {
    val motion = LocalMotion.current
    val state = LocalSettle.current
    val rises = remember { motion && state != null && state.armed && index < Motion.SETTLE_ROWS }
    if (!rises) return@composed Modifier
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, Motion.settle(index)) }
    val rise = with(LocalDensity.current) { Motion.SETTLE_RISE_DP.dp.toPx() }
    Modifier.graphicsLayer { alpha = shown.value; translationY = (1f - shown.value) * rise }
}

/** [content] as one of a page's first rows: see [settleIn]. */
@Composable
internal fun Settle(index: Int, content: @Composable () -> Unit) {
    Box(Modifier.settleIn(index), propagateMinConstraints = true) { content() }
}

// --- The calendar filling in a diagonal wave, the first time it is seen in a session ---

/**
 * How far the wave has run, in milliseconds, or null when there is none (it has run, it is not the first time, or animations are off).
 * Before it starts (the calendar not yet in view) it is -1 and every square is hidden. [skip] is for a calendar that arrives by the
 * card's own morph: the morph already moves it, and that first time is used up. The first time per [name] in the session, only.
 */
@Composable
internal fun rememberWave(name: String, visible: Boolean, skip: Boolean = false): Animatable<Float, androidx.compose.animation.core.AnimationVector1D>? {
    val store = LocalSeen.current
    val motion = LocalMotion.current
    val key = "wave:$name"
    val plays = remember { motion && !skip && store[key] != true }
    if (skip) store[key] = true
    val t = remember { Animatable(-1f) }
    var finished by remember { mutableStateOf(!plays) }
    LaunchedEffect(visible) {
        if (!plays || !visible || finished) return@LaunchedEffect
        store[key] = true
        t.snapTo(0f)
        t.animateTo(Motion.waveMillis().toFloat(), Motion.wave())
        finished = true
    }
    return if (finished) null else t
}

/** A calendar square in the wave: faded and shrunk until its diagonal [d] (counted from the top left) comes. */
internal fun Modifier.waved(wave: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>?, d: Int): Modifier =
    if (wave == null) this else graphicsLayer {
        val p = Motion.waveCell(wave.value, d)
        alpha = p
        scaleX = 0.6f + 0.4f * p
        scaleY = 0.6f + 0.4f * p
    }

/** [settleIn] for a row that does not know its number: it takes the next place when it first composes, so the first rows laid out are the first to rise. */
internal fun Modifier.settleNext(): Modifier = composed {
    val state = LocalSettle.current
    val index = remember { state?.takeIf { it.armed }?.let { it.next++ } ?: Motion.SETTLE_ROWS }
    Modifier.settleIn(index)
}
