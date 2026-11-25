package clean.app

import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.PillShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A search that is an icon until it is wanted (round 20, item 14): the Library's and History's. [open] says whether the field is out, [query] is what is
 * typed in it. The icon in the page's header and a pull down past the top of the list both call [openAndFocus]; the field's own close control, Back
 * and a loss of focus with nothing typed call [close]. The field is [SearchBox], the pull is [rememberPullToSearch]; both pages use these and nothing else.
 */
@Stable
class SearchOpener internal constructor(private val keyboard: State<SoftwareKeyboardController?>) {
    internal val focus = FocusRequester()
    var open by mutableStateOf(false)
    var query by mutableStateOf("")
    /** Round 28: a pinned field (the exercise pickers') stays out: its close control, Back and a lost focus only empty it. */
    var pinned = false

    /** Collapses the field, forgetting what was typed, and puts the keyboard away. */
    fun close() {
        if (pinned) { keyboard.value?.hide(); query = ""; return }
        keyboard.value?.hide()
        open = false
        query = ""
    }

    /**
     * Brings the field out, focuses it and asks for the keyboard: the field is composed on the next frame, focus is asked for as soon as it exists (a few
     * frames at most) and the keyboard a frame after that, never inside the gesture that called this (focus alone does not raise it on every phone).
     */
    suspend fun openAndFocus() {
        open = true
        var tries = 0
        while (tries++ < 6) {
            withFrameNanos { }
            if (runCatching { focus.requestFocus() }.isSuccess) break
        }
        withFrameNanos { }
        keyboard.value?.show()
    }
}

@Composable
fun rememberSearchOpener(): SearchOpener {
    val keyboard = rememberUpdatedState(LocalSoftwareKeyboardController.current)
    return remember { SearchOpener(keyboard) }
}

/**
 * The search field of [opener], opening down out of the line above it and closing back into it (a short fade and grow, none with animations off): a
 * pill in the tile tone of the theme with the lit edge, the search glyph at its left, the placeholder, and at its right a close control that empties the
 * field and collapses it. Back collapses it too; so does leaving it with nothing typed.
 */
@Composable
fun SearchBox(opener: SearchOpener, placeholder: String, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    BackHandler(enabled = opener.open && !opener.pinned) { opener.close() }
    AnimatedVisibility(
        opener.open,
        enter = if (motion) expandVertically(Motion.search(), Alignment.Top) + fadeIn(Motion.search()) else EnterTransition.None,
        exit = if (motion) shrinkVertically(Motion.searchOut(), Alignment.Top) + fadeOut(Motion.searchOut()) else ExitTransition.None,
    ) { SearchPill(opener, placeholder, modifier) }
}

@Composable
private fun SearchPill(opener: SearchOpener, placeholder: String, modifier: Modifier) {
    val keyboard = LocalSoftwareKeyboardController.current
    // A field that has never been focused is not "left": only a focus that was had, then lost, collapses it.
    var had by remember { mutableStateOf(false) }
    // Round 28: Back (or the back swipe) that only puts the keyboard away leaves the pill out; with nothing typed the pill goes too. With text typed, the first Back hides the keyboard and the second (SearchBox's BackHandler) clears and collapses.
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    // The keyboard counts as going down as soon as it has dropped below 85 % of the height it rose to, so the pill starts closing with the
    // keyboard instead of after it has finished sliding away (Round 29, "the closing should be faster").
    LaunchedEffect(ime, density) {
        var up = false
        val watch = KeyboardWatch()
        snapshotFlow { ime.getBottom(density) }.collect { h ->
            val now = watch.up(h)
            if (collapsesOnKeyboardDown(up, now, opener.query)) opener.close()
            up = now
        }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth().heightIn(min = 44.dp).tile(PillShape, frosted = true)
            // In Light the white pill is 1.1 : 1 against the page, so its edge is drawn in the outline colour (3 : 1 or more).
            .then(if (!clean.app.ui.theme.LocalLook.current.dark) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, PillShape) else Modifier)
            .testTag("search field").padding(start = Space.m),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Icon(CleanIcons.Search, null, Modifier.size(18.dp), tint = muted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (opener.query.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = muted, maxLines = 1)
            BasicTextField(
                opener.query, { opener.query = it }, Modifier.fillMaxWidth().focusRequester(opener.focus).onFocusChanged {
                    if (it.isFocused) had = true else if (had && opener.query.isEmpty()) opener.close()
                },
                singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            )
        }
        Box(
            Modifier.size(44.dp).clickable(role = Role.Button) { opener.close() }.semantics { contentDescription = "Close search" },
            contentAlignment = Alignment.Center,
        ) { Icon(CleanIcons.Close, null, Modifier.size(18.dp), tint = muted) }
    }
}

/**
 * Pulling a list down past its top opens a search (round 17, 12; one helper for the Library and History since round 20). The list follows the finger through
 * [Motion.rubber], the hint says "Pull to search" and then "Release to search" with one light tick at [threshold], and letting go past it calls
 * [SearchOpener.openAndFocus]; letting go before it just settles back. With animations off the list does not move.
 */
@Stable
class PullToSearch internal constructor(
    val threshold: Float, internal val motionOn: Boolean, private val tick: () -> Unit, private val scope: CoroutineScope,
    private val enabled: () -> Boolean, private val opener: SearchOpener,
) {
    /** How far the finger has pulled the list down, in px. */
    var pull by mutableFloatStateOf(0f)
        internal set
    private var settling = false

    // Letting go (with a fling or without, whichever the gesture ends in) settles the list back; past the threshold the search is opened after the gesture is over.
    fun release() {
        if (settling || pull <= 0f) return
        settling = true
        val open = pull >= threshold
        scope.launch {
            try {
                if (open) opener.openAndFocus()
                if (motionOn) androidx.compose.animation.core.animate(pull, 0f, animationSpec = Motion.pullBack()) { v, _ -> pull = v } else pull = 0f
            } finally { settling = false }
        }
    }

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (!enabled() || source != NestedScrollSource.UserInput || pull <= 0f || available.y >= 0f || settling) return Offset.Zero
            val take = maxOf(available.y, -pull)
            pull += take
            return Offset(0f, take)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!enabled() || source != NestedScrollSource.UserInput || available.y <= 0f || settling) return Offset.Zero
            val before = pull
            pull += available.y
            if (before < threshold && pull >= threshold) tick()
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            release()
            return Velocity.Zero
        }
    }
}

/** [PullToSearch] for [list], opening [opener]; [enabled] false (the search is already out) leaves the list alone. */
@Composable
fun rememberPullToSearch(list: LazyListState, opener: SearchOpener, enabled: Boolean): PullToSearch {
    val threshold = with(LocalDensity.current) { PULL_TO_SEARCH_DP.dp.toPx() }
    val motionOn = LocalMotion.current
    val tick = rememberTick()
    val scope = rememberCoroutineScope()
    val on = rememberUpdatedState(enabled)
    val pull = remember(threshold, motionOn, opener) { PullToSearch(threshold, motionOn, tick, scope, { on.value }, opener) }
    // A release the fling never reports (a slow lift): when the list's scroll ends with the pull still out, it is let go the same way.
    LaunchedEffect(list, pull) { snapshotFlow { list.isScrollInProgress }.collect { if (!it) pull.release() } }
    return pull
}

/** The list follows the finger of [pull] with a little resistance, and takes its nested scroll. */
fun Modifier.pulled(pull: PullToSearch): Modifier =
    nestedScroll(pull.connection).graphicsLayer { translationY = if (pull.motionOn) Motion.rubber(pull.pull, pull.threshold * 0.8f) else 0f }

/** "Pull to search", "Release to search" past the threshold: faded in with the pull, above the list. */
@Composable
fun PullHint(pull: PullToSearch, modifier: Modifier = Modifier) {
    val now = pull.pull
    if (now <= 0f) return
    Text(
        if (now >= pull.threshold) "Release to search" else "Pull to search",
        modifier.padding(top = Space.s).graphicsLayer { alpha = (now / pull.threshold).coerceIn(0f, 1f) }.testTag("pull hint"),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Whether the keyboard is up, from its height frame by frame: up once it has risen, down as soon as it falls below 85 % of the height it reached
 * (not only once it is gone), and up again only after it has gone all the way.
 */
class KeyboardWatch {
    private var peak = 0
    private var falling = false
    fun up(height: Int): Boolean {
        if (height == 0) { peak = 0; falling = false }
        if (!falling && height > peak) peak = height
        if (peak > 0 && height < peak * 0.85f) falling = true
        return height > 0 && !falling
    }
}

/** Whether the pill collapses as the keyboard goes down: it was up, it is down now, and nothing is typed. */
fun collapsesOnKeyboardDown(wasUp: Boolean, isUp: Boolean, query: String): Boolean = wasUp && !isUp && query.isEmpty()
