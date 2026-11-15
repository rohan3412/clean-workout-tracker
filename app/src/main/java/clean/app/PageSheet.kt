package clean.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.LocalLook
import clean.app.ui.theme.onInk
import clean.app.ui.theme.paperGrain
import kotlin.math.roundToInt

/*
 * Linen's page: the title stays on the page colour and everything below it is one white sheet with large top corners
 * (Look.sheetPage). A tab pins its title above a clipped sheet its list scrolls in ([SheetTab]); a page with a BackBar has the
 * sheet drawn behind it by [SheetHost], which starts it under the bar. Every other look draws none of this.
 */

/** The sheet's top corner. */
val SheetCorner = 36.dp
private val SheetTop = RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner)

/** Where a page's sheet starts, told to its [SheetHost] by the page's bar. */
class SheetBehind {
    /** Whether the page has a bar to start the sheet under. */
    var wanted by mutableStateOf(false)
    /** Room under the 64 dp bar that is still the bar's, like the exercise page's tab row. */
    var extra by mutableStateOf(0.dp)
    /** A measured bottom of the bar in pixels, for a bar whose height is not 64 dp (the workout's); 0 until known. */
    var barPx by mutableFloatStateOf(0f)
}

val LocalSheetBehind = staticCompositionLocalOf<SheetBehind?> { null }

/**
 * Gives a page its Linen sheet: the page colour, and behind a bar-topped page's content one white sheet from under the bar, its
 * top corners masked in the page colour over the content so cards scrolled up to the top never stand outside the curve. Nothing
 * in any other look.
 */
@Composable
fun SheetHost(content: @Composable () -> Unit) {
    val look = LocalLook.current
    // The same tree in every look: the page keeps what it remembers (an open sub-page, where it was scrolled to) when the look turns Linen or back.
    val sheet = look.sheetPage
    val state = remember { SheetBehind() }
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val top = if (state.barPx > 0f) state.barPx else with(density) { (statusTop + 64.dp + state.extra).toPx() }
    Box(Modifier.fillMaxSize()) {
        GrainLayer()
        if (sheet && state.wanted) Box(Modifier.fillMaxSize().offset { IntOffset(0, top.roundToInt()) }.background(look.raised))
        CompositionLocalProvider(LocalSheetBehind provides if (sheet) state else null) { content() }
        if (sheet && state.wanted) {
            val page = look.page
            Canvas(Modifier.fillMaxSize()) {
                val r = SheetCorner.toPx()
                val left = Path().apply {
                    moveTo(0f, top); lineTo(r, top)
                    arcTo(Rect(0f, top, 2 * r, top + 2 * r), 270f, -90f, false)
                    close()
                }
                val right = Path().apply {
                    moveTo(size.width, top); lineTo(size.width - r, top)
                    arcTo(Rect(size.width - 2 * r, top, size.width, top + 2 * r), 270f, 90f, false)
                    close()
                }
                drawPath(left, page)
                drawPath(right, page)
            }
        }
    }
}

/** A page with a BackBar says so here, so its [SheetHost] draws the sheet under it. */
@Composable
internal fun RegisterSheetBar(extra: Dp = 0.dp) {
    val state = LocalSheetBehind.current ?: return
    androidx.compose.runtime.SideEffect { state.wanted = true; if (extra > state.extra) state.extra = extra }
}

/** A bar of another height (the workout's) marks where the sheet starts by its measured size. */
@Composable
fun Modifier.sheetBelow(): Modifier {
    val state = LocalSheetBehind.current ?: return this
    androidx.compose.runtime.SideEffect { state.wanted = true }
    return onSizeChanged { state.barPx = it.height.toFloat() }
}

/** The container of a Scaffold: the theme's background, or none inside a [SheetHost] (which draws the page and its sheet itself). */
@Composable
fun pageContainer(): Color = if (LocalSheetBehind.current != null) Color.Transparent else MaterialTheme.colorScheme.background

/** Whether a tab pins its title above the sheet (Linen) rather than carrying it as the first item of its list. */
@Composable
fun pinsTitle(): Boolean = LocalLook.current.sheetPage

/** A tab: in Linen the [header] pinned on the page colour over a white sheet with 36 dp top corners that holds [content]; otherwise just [content], whose list carries the header. */
@Composable
fun SheetTab(header: @Composable () -> Unit, content: @Composable () -> Unit) {
    val look = LocalLook.current
    // One tree in every look, so the tab's list keeps its place when the look turns Linen or back.
    val sheet = look.sheetPage
    Column(Modifier.fillMaxSize()) {
        if (sheet) header()
        Box(Modifier.weight(1f).fillMaxWidth().then(if (sheet) Modifier.clip(SheetTop).background(look.raised) else Modifier)) { content() }
    }
}

/**
 * A round button for a page's header. Linen draws it as a 40 dp white circle (black with a white icon when it is the page's [main]
 * action); every other look is the plain icon button. The touch area is 48 dp either way.
 */
@Composable
fun HeaderButton(icon: ImageVector, description: String, onClick: () -> Unit, main: Boolean = false) {
    val look = LocalLook.current
    if (!look.sheetPage) { IconButton(onClick = onClick) { Icon(icon, description) }; return }
    IconButton(onClick = onClick) {
        Box(Modifier.size(40.dp).background(if (main) look.text else look.raised, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(22.dp), tint = if (main) look.onInk else look.text)
        }
    }
}

/** A header's actions on a white capsule in Linen (a lone icon button is then a white circle); unchanged elsewhere. */
@Composable
fun HeaderActions(actions: @Composable RowScope.() -> Unit) {
    val look = LocalLook.current
    if (!look.sheetPage) { Row(verticalAlignment = Alignment.CenterVertically) { actions() }; return }
    Row(Modifier.padding(end = Space.s).clip(CircleShape).background(look.raised), verticalAlignment = Alignment.CenterVertically) { actions() }
}

/** The page colour with Linen's fine grain, drawn under the sheet and the cards so only the bare page (the title band) is grained. Nothing else in Linen carries the grain, and Mono keeps its own over everything. */
@Composable
fun GrainLayer() {
    val look = LocalLook.current
    if (!look.sheetPage) return
    Box(Modifier.fillMaxSize().background(look.page).paperGrain(look.texture, look.dark))
}

/** Whether the thing being drawn sits in a grey [Tile], where Linen's ring track is white rather than grey. */
val LocalOnTile = staticCompositionLocalOf { false }

/** A gap at the top of a tab's list inside Linen's sheet, so the first card clears the 36 dp corners. */
fun androidx.compose.foundation.lazy.LazyListScope.sheetGap(pinned: Boolean) {
    if (pinned) item { androidx.compose.foundation.layout.Spacer(Modifier.height(Space.l)) }
}
