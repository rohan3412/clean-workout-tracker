package clean.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.LocalCleanColors
import clean.app.ui.theme.LocalLook
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/** One choice in a hold menu: what it does, its icon, and whether it deletes (red, and last). */
class HoldAction(val label: String, val icon: ImageVector?, val danger: Boolean = false, val onClick: () -> Unit)

/** The item being held: where it is in the window, a picture of it, and what can be done with it. */
class Hold(val bounds: Rect, val layer: GraphicsLayer, val shape: Shape, val fill: Color, val actions: List<HoldAction>)

/** The one place a hold lives (B5): the item held, or null. Provided to the whole app by [App]; read by [HoldOverlay] and the page blur. */
class HoldHost {
    var hold by mutableStateOf<Hold?>(null)
}

val LocalHoldHost = staticCompositionLocalOf<HoldHost?> { null }

/** The held item's actions as a screen reader's own actions on it: where a sighted user holds, TalkBack gets the same choices (C4). */
private fun androidx.compose.ui.semantics.SemanticsPropertyReceiver.heldActions(list: () -> List<HoldAction>) {
    customActions = list().map { a -> CustomAccessibilityAction(a.label) { a.onClick(); true } }
}

/**
 * One hold gesture everywhere (C1, B5): holding the item for a long press blurs and dims the page, lifts the item a little with a
 * shadow and shows [actions] on a glass menu beside it ([HoldOverlay]). A tap on the item is whatever it already was, and a drag
 * (a scroll) is not a hold. The item is drawn into a picture only from the touch to the hold, so a list that scrolls pays nothing.
 * Put it after the item's own click and press modifiers, so the picture is the item at rest. [fill] is what the lifted item sits on
 * (the card's own fill for a row, which has none). With no [actions] it is nothing.
 */
@Composable
fun Modifier.holdMenu(shape: Shape, actions: (() -> List<HoldAction>)?, fill: Color = cardFill()): Modifier {
    val host = LocalHoldHost.current
    if (actions == null || host == null) return this
    val layer = rememberGraphicsLayer()
    val spot = remember { HoldSpot() }
    var armed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val actionsNow by rememberUpdatedState(actions)
    val fillNow by rememberUpdatedState(fill)
    // The picture goes with the item: nothing may be drawn from a layer that has been released.
    DisposableEffect(layer) { onDispose { if (host.hold?.layer === layer) host.hold = null } }
    return this
        .onGloballyPositioned { spot.at(it.boundsInWindow(), it.size.width, it.size.height) }
        .drawWithContent { if (armed) record(layer) else drawContent() }
        .semantics { heldActions(actionsNow) }
        .pointerInput(Unit) {
            val timeout = viewConfiguration.longPressTimeoutMillis
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                armed = true
                try {
                    val held = withTimeoutOrNull(timeout) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                            if (!change.pressed || (change.position - down.position).getDistance() > slop || event.changes.size > 1) return@withTimeoutOrNull false
                        }
                        @Suppress("UNREACHABLE_CODE") false
                    }
                    if (held == null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        host.hold = Hold(spot.rect, layer, shape, fillNow, actionsNow())
                        // The rest of this touch is the hold's: the item under the finger gets no tap from it.
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                } finally {
                    armed = false
                }
            }
        }
}

/** The item's window bounds, taken at its true size even while a press dips it (the dip scales about the centre). */
private class HoldSpot {
    var rect: Rect = Rect.Zero
    fun at(seen: Rect, width: Int, height: Int) {
        rect = Rect(Offset(seen.center.x - width / 2f, seen.center.y - height / 2f), androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat()))
    }
}

private fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.record(layer: GraphicsLayer) {
    val scope = this
    layer.record { scope.drawContent() }
    drawLayer(layer)
}

/**
 * The hold's picture over the dimmed page: the held item lifted (scale 1.03, a shadow) in the place it was, and its actions on a glass
 * menu beside it (the one [CleanMenu]). A tap outside, Back or any action closes it; with animations off the lift is there at once.
 */
@Composable
fun HoldOverlay(host: HoldHost) {
    val hold = host.hold ?: return
    val motion = LocalMotion.current
    val lift = remember(hold) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(hold) { if (motion) lift.animateTo(1f, Motion.short()) }
    val close = { host.hold = null }
    BackHandler(onBack = close)
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val dark = LocalLook.current.dark
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        Box(
            Modifier.fillMaxSize().graphicsLayer { alpha = lift.value }.background(Color.Black.copy(alpha = if (dark) 0.5f else 0.38f))
                .clickable(remember { MutableInteractionSource() }, null, onClickLabel = "Close menu", onClick = close),
        )
        val at = hold.bounds
        Box(Modifier.offset { IntOffset((at.left - origin.x).roundToInt(), (at.top - origin.y).roundToInt()) }.size(with(density) { at.width.toDp() }, with(density) { at.height.toDp() })) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        val k = 1f + 0.03f * lift.value
                        scaleX = k; scaleY = k
                        shadowElevation = 24.dp.toPx() * lift.value
                        shape = hold.shape
                        clip = false
                        ambientShadowColor = Color.Black; spotShadowColor = Color.Black
                    }
                    .background(hold.fill, hold.shape)
                    .drawWithContent { drawContent(); drawHeld(hold.layer) },
            )
            CleanMenu(true, close) {
                for (action in hold.actions) {
                    val tint = if (action.danger) LocalCleanColors.current.danger else MaterialTheme.colorScheme.onSurface
                    DropdownMenuItem(
                        text = { Text(action.label, color = tint) },
                        leadingIcon = action.icon?.let { icon -> { Icon(icon, null, tint = if (action.danger) tint else MaterialTheme.colorScheme.onSurfaceVariant) } },
                        onClick = { close(); action.onClick() },
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawHeld(layer: GraphicsLayer) = drawLayer(layer)

/** What a hold on a workout offers (a History card, Home's last workout): open it, do it again, keep it as a template, share it, delete it (Undo). Also shows the share sheet. */
@Composable
fun workoutHold(model: AppModel, workout: clean.core.Workout): () -> List<HoldAction> {
    var sharing by remember { mutableStateOf(false) }
    if (sharing) ShareWorkoutDialog(model, workout) { sharing = false }
    return {
        listOf(
            HoldAction("Open", CleanIcons.Forward) { model.open(Screen.WorkoutDetail(workout.date)) },
            HoldAction("Start again", CleanIcons.Play) { model.startFrom(workout) },
            HoldAction("Save as template", CleanIcons.Copy) { model.saveAsTemplate(workout) },
            HoldAction("Share", CleanIcons.Share) { sharing = true },
            HoldAction("Delete", CleanIcons.Delete, danger = true) { model.deleteWorkout(workout.date) },
        )
    }
}
