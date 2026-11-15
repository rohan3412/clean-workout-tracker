package clean.app

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import clean.app.ui.theme.LocalLook
import clean.app.ui.theme.Look
import clean.app.ui.theme.PillShape
import clean.app.ui.theme.colorScheme
import kotlinx.coroutines.delay

/** Kv `preview.live`: `off` turns the preview pop-up off; absent is on. */
internal const val PREVIEW_LIVE = "preview.live"

/** What a pop-up shows: the look and Home's cards (the Today card and tiles), Home's cards alone, or the tab bar. */
enum class PreviewKind(val about: String, val height: Int) {
    LOOK("the look", 280), HOME("Home's cards", 280), TABS("the tab bar", 96),
}

/** The settings that change [PreviewKind.LOOK] besides the `theme.` and `mono.` families. */
private val LOOK_KEYS = setOf("accent", "palette", "background", "theme", "font", "textSize")

/** Which part of the app a set of changed option keys affects, or null when none of it is previewed. */
private fun previewKindOf(keys: Set<String>): PreviewKind? = when {
    // A whole preset (or a saved look, or Reset) is a change you see at once on the page itself: no pop-up (2026-10-16, the user's word).
    "theme.preset" in keys -> null
    keys.any { it == HOME_LAYOUT || it == HOME_CHARTS } -> PreviewKind.HOME
    keys.any { it == "nav.order" || it == "theme.mix.tabstyle" || it == "theme.mix.tabbar" } -> PreviewKind.TABS
    keys.any { (it.startsWith("theme.") && it != "theme.iconApplied" && it != "theme.saved") || it.startsWith("mono.") || it in LOOK_KEYS } -> PreviewKind.LOOK
    else -> null
}

/** Pop-up timing: a change must stay put this long (a slider drag has settled) before it shows. */
private const val SETTLE_MS = 450L
/** The pop-up goes Before, After [SWAPS] times, each held [HOLD_MS], then stays on After for [END_MS] and closes itself. */
private const val SWAPS = 3
private const val HOLD_MS = 900L
private const val END_MS = 1200L

/** The last state no pop-up has shown yet: what a change is compared with. */
private class Baseline(var options: Map<String, String>, var look: Look)

/** One pop-up: the part of the app, and the look and options from before the change(s). */
private class Shot(val kind: PreviewKind, val look: Look, val options: Map<String, String>)

/**
 * Room a settings list leaves at its end (its bottom `contentPadding`), so the floating Live preview switch never covers the last row;
 * with a workout in progress the banner docked at the bottom of the screen takes its height too.
 */
@Composable
internal fun settingsFloatSpace(model: AppModel): Dp = 72.dp + bannerRoom(model)

@Composable
private fun bannerRoom(model: AppModel): Dp {
    val scope by model.bannerScope.collectAsState()
    return if (scope == "Everywhere" && model.draft != null) BANNER_ROOM else 0.dp
}

/**
 * A settings page ([content]) on every page reached from Settings, with two things added. A Live preview switch floats at its bottom right (kv `preview.live`).
 * While it is on, a change to a visual setting ([previewKindOf]) opens a pop-up once the value has settled: a small non-interactive copy of the part of the app
 * it affects, labelled Before and After and swapping between the two three times, ending on After; tapping Before or After stops the swaps and holds that side. A further change updates the open pop-up; tapping outside, Done or the end of the swaps (when no pill was tapped) close it. A preset chosen as a whole shows none.
 */
@Composable
internal fun SettingsFloat(model: AppModel, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val live = model.flag(PREVIEW_LIVE, true)
    val options by model.options.collectAsState()
    val look = LocalLook.current
    val lookNow = rememberUpdatedState(look)
    val base = remember { Baseline(options, look) }
    var shot by remember { mutableStateOf<Shot?>(null) }
    SideEffect { if (options == base.options) base.look = look }
    LaunchedEffect(options) {
        if (options == base.options) return@LaunchedEffect
        val changed = (options.keys + base.options.keys).filter { options[it] != base.options[it] }.toSet()
        val kind = previewKindOf(changed)
        if (kind != null) delay(SETTLE_MS)
        if (kind != null && live) shot = Shot(kind, base.look, base.options)
        base.options = options
        if (kind != null) base.look = lookNow.value
    }
    Box(modifier) {
        content()
        Row(
            Modifier.align(Alignment.BottomEnd).padding(start = Space.m, end = Space.m, top = Space.s, bottom = Space.s + bannerRoom(model))
                .clip(PillShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .toggleable(live, role = Role.Switch) { model.setOption(PREVIEW_LIVE, if (live) "off" else null) }
                .semantics(mergeDescendants = true) { contentDescription = "Live preview, ${if (live) "on" else "off"}" }
                .minTouch().padding(start = Space.l, end = Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Live preview", style = MaterialTheme.typography.labelLarge)
            Switch(live, null)
        }
    }
    shot?.let { PreviewPopup(model, it) { shot = null } }
}

/** The card over a scrim: [shot]'s part of the app with its old look, then the current one fading in over it. It takes no touch of its own. */
@Composable
private fun PreviewPopup(model: AppModel, shot: Shot, onDone: () -> Unit) {
    val motion = LocalMotion.current
    val now = LocalLook.current
    val options by model.options.collectAsState()
    // Before and After in turn, three times over, ending on After; with animations off they swap without the fade.
    // A tap on a pill stops the swaps and holds that side until Done or a tap outside.
    val mix = remember(shot) { Animatable(0f) }
    val side = remember(shot) { PreviewSide() }
    LaunchedEffect(shot, side.target) {
        side.target?.let { if (motion) mix.animateTo(it, Motion.slow()) else mix.snapTo(it); return@LaunchedEffect }
        repeat(SWAPS) { round ->
            delay(HOLD_MS)
            if (motion) mix.animateTo(1f, Motion.slow()) else mix.snapTo(1f)
            delay(HOLD_MS)
            if (round < SWAPS - 1) { if (motion) mix.animateTo(0f, Motion.slow()) else mix.snapTo(0f) }
        }
        delay(END_MS)
        onDone()
    }
    val after = side.picked ?: (mix.value >= 0.5f)
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BlinkWindow()
        Surface(Modifier.padding(Space.xxl).fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
            Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("Preview", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    BeforeAfter("Before", !after, side.picked == false) { side.tap(false) }
                    BeforeAfter("After", after, side.picked == true) { side.tap(true) }
                }
                Box(Modifier.fillMaxWidth().height(shot.kind.height.dp).clip(MaterialTheme.shapes.large).clearAndSetSemantics { contentDescription = "Preview of ${shot.kind.about}, before and after the change" }) {
                    PreviewTheme(shot.look) { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { PreviewBody(model, shot.kind, shot.options) } }
                    PreviewTheme(now) { Box(Modifier.fillMaxSize().graphicsLayer { alpha = mix.value }.background(MaterialTheme.colorScheme.background)) { PreviewBody(model, shot.kind, options) } }
                    // On top of everything, so no tap or drag reaches the copies.
                    Box(Modifier.matchParentSize().pointerInput(Unit) {
                        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                    })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onDone) { Text("Done") } }
            }
        }
    }
}

/** Which side the pop-up shows: the automatic swaps until a pill is tapped ([picked] null), then the tapped side, held. */
internal class PreviewSide {
    var picked by mutableStateOf<Boolean?>(null)
        private set
    fun tap(after: Boolean) { picked = after }
    /** Where the fade goes: 1 for After, 0 for Before; null while the swaps run. */
    val target: Float? get() = picked?.let { if (it) 1f else 0f }
}

/** One of the two labels over the preview: filled while its side is the one showing, with a tick once it was tapped; the tap shows its side. Its hit area is 48 dp. */
@Composable
private fun BeforeAfter(word: String, showing: Boolean, picked: Boolean, onTap: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.selectable(showing, role = Role.Tab, onClick = onTap).minTouch(), contentAlignment = Alignment.Center) {
        Row(
            Modifier.clip(PillShape).background(if (showing) scheme.primary else scheme.surfaceContainerHighest).padding(start = if (picked) Space.s else Space.m, end = Space.m, top = Space.xs, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            val colour = if (showing) scheme.onPrimary else scheme.onSurfaceVariant
            if (picked) Icon(CleanIcons.Check, null, Modifier.size(16.dp), tint = colour)
            Text(word, color = colour, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** How far a preview is shrunk so a whole card fits in the pop-up. */
private const val LOOK_SCALE = 0.7f
private const val HOME_SCALE = 0.8f

/** What the preview of [kind] draws: real composables from Home and the tab bar, fed by the same state, with [options] as the layout and tab settings. */
@Composable
private fun PreviewBody(model: AppModel, kind: PreviewKind, options: Map<String, String>) {
    val edge = options[HOME_CHARTS] != "inset"
    when (kind) {
        PreviewKind.LOOK -> Scaled(LOOK_SCALE) {
            val templates by model.templates.collectAsState()
            val workouts by model.workouts.collectAsState()
            Column {
                HomeHeader(model)
                TodayCard(model, templates, workouts)
                CompositionLocalProvider(LocalEdgeCharts provides edge) { HalfCards(false) { Box { VolumeTile(model) }; Box { StreakTile(model) } } }
            }
        }
        PreviewKind.HOME -> Scaled(HOME_SCALE) {
            CompositionLocalProvider(LocalEdgeCharts provides edge) {
                HalfCards(options[HOME_LAYOUT] == "even") {
                    Box { OneRmTile(model, 0) }; Box { StreakTile(model) }; Box { VolumeTile(model) }; Box { WeightTile(model) }
                }
            }
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TabBar(model, tabOrderOf(options["nav.order"]), false, false, 0.dp, Modifier, current = Tab.Home)
        }
    }
}

/** [content] laid out at the page's width over [scale], then shrunk towards its top centre (a box wider than its space is laid out centred), so a whole card fits where a fraction of the page is given. */
@Composable
private fun Scaled(scale: Float, content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        Box(
            Modifier.requiredWidth(maxWidth / scale).wrapContentHeight(Alignment.Top, unbounded = true)
                .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0.5f, 0f) },
        ) { content() }
    }
}

/** The theme with the colours of [look], so a copy drawn for an older look keeps it whatever the app now uses. */
@Composable
private fun PreviewTheme(look: Look, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = look.colorScheme(), typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        CompositionLocalProvider(LocalLook provides look, content = content)
    }
}
