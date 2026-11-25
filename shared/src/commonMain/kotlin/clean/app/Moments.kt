package clean.app

import androidx.compose.ui.backhandler.BackHandler
import clean.app.platform.LocalPlatform

import clean.core.time.*
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import clean.app.ui.theme.SmallShape
import clean.core.Moment
import clean.core.photosSpan
import clean.core.photosSummary
import androidx.compose.ui.platform.testTag
import clean.core.groupMoments
import clean.core.momentOf
import clean.core.momentStats
import clean.core.parseExifDate
import clean.core.parseMoment
import clean.core.newestMomentsFirst
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- The model's side of it ---

/** Files each of [sources] as a moment and says how many went in. [afterwards] runs when all are done (the camera's temporary file goes then). */
fun AppModel.importMoments(sources: List<String>, afterwards: () -> Unit = {}) {
    val id = activeId.value ?: return
    viewModelScope.launch {
        val added = withContext(ioDispatcher) { sources.count { platform.addMoment(id, it) != null }.also { afterwards() } }
        refreshMoments()
        message = when {
            added == sources.size -> if (added == 1) "Photo added" else "$added photos added"
            added == 0 -> "That could not be read as a photo"
            else -> "$added of ${sources.size} photos added"
        }
    }
}

/** Removes [moments] at once with an Undo; the files are kept aside in the private cache until the Undo has passed. */
fun AppModel.deleteMoments(moments: List<Moment>) {
    val id = activeId.value ?: return
    viewModelScope.launch {
        val batch = "${currentMillis()}-${kotlin.random.Random.nextInt(Int.MAX_VALUE)}"
        val away = withContext(ioDispatcher) { platform.removeMomentsAside(id, moments, batch) }
        if (away == null) { message = "Could not delete the photos"; return@launch }
        refreshMoments()
        say(if (moments.size == 1) "Photo deleted" else "${moments.size} photos deleted", expire = { viewModelScope.launch(ioDispatcher) { platform.discardAside(away) } }) {
            viewModelScope.launch {
                withContext(ioDispatcher) { platform.restoreMoments(id, away) }
                refreshMoments()
            }
        }
    }
}

// --- Pictures on screen ---

/** Thumbnails already cut, by file and size; about eight megabytes. */
val MOMENT_THUMBS = ImageCache(8 * 1024 * 1024)

/**
 * A moment cut to about [px], cropped to fill; a plain tile until it is ready. Decoded off the main thread and kept. With "Blur photos" on (kv
 * `photos.blur`, Round 30) it takes the same path as a progress photo's thumbnail ([decodeThumb]: decoded small and blurred in software, Round 31); the
 * full-screen picture stays sharp. Every small view of a moment (the fan, the grid) is this one.
 */
@Composable
internal fun MomentThumb(model: AppModel, moment: Moment, px: Int, modifier: Modifier = Modifier) {
    val platform = LocalPlatform.current
    val id = model.activeId.collectAsState().value
    val file = remember(id, moment.name) { id?.let { platform.momentPath(it, moment.name) } }
    // Shut photos are blurred whatever the setting says (Round 31, Lock photos).
    val blur = model.flag("photos.blur", false) || model.photosShut()
    val key = thumbKey(file, px, blur)
    var bitmap by remember(key) { mutableStateOf(MOMENT_THUMBS.get(key)) }
    LaunchedEffect(key) {
        if (bitmap == null && file != null) bitmap = decodeThumb(platform, file, px, blur)?.also { MOMENT_THUMBS.put(key, it) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

// --- The tile on Profile ---

private val FAN_TURN = listOf(-6f, -2f, 3f, 7f)
private val FAN_RISE = listOf(-4, 5, -3, 4)

/**
 * Profile's Photos (Round 39; the Snapshots fan of Round 31 and 38, now the one entry to the Photos section): the newest four pictures of both kinds, progress photos and
 * snapshots together, as framed cards in a fan, a little turned and lifted, faint frames where there are fewer. No card behind it and no padding; it sits on the page under
 * Profile's own "Photos" heading and its cards are as large as the page's width allows ([fanLayout]). Right below it, one quiet line (two when there is a second figure to
 * give: [photosSummary], [photosSpan]). A tap on the fan or on the lines opens the Photos page; with none, one quiet "Add photo" pill starts the add flow and nothing else opens.
 * The page grows out of the fan's bounds (`profile:moments`; with no card, the container is the fan's box and the page's own colour).
 */
@Composable
fun PhotosTile(model: AppModel) {
    val pics by model.pics.collectAsState()
    val add = rememberPhotoAdd(model, "profile")
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    val label = if (pics.size == 1) "Photos, 1 photo" else "Photos, ${pics.size} photos"
    // Round 17 (15): the cards lean a couple of degrees with the page scrolling past, and settle flat at rest.
    val tilt = rememberScrollTilt(model.tabLists.getValue(Tab.Profile))
    val camera = LocalDensity.current.density * 12f
    androidx.compose.runtime.CompositionLocalProvider(LocalCardKey provides "profile:moments") {
        val tap = if (pics.isEmpty()) null else cardTap { model.open(Screen.Photos) }
        val pressed = remember { androidx.compose.runtime.mutableStateOf(false) }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                val fan = fanLayout(maxWidth)
                Box(
                    Modifier.fillMaxWidth().height(fan.height).then(if (tap != null) Modifier.opensInto(null, Color.Transparent) else Modifier)
                        .then(if (tap != null) Modifier.pressable(dip = 0.98f, pressed = pressed, onClick = tap) else Modifier)
                        .semantics { if (pics.isNotEmpty()) contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    val shown = pics.take(4)
                    // The fan is always there: empty slots are outlined frames. The newest is the first slot and drawn last, on top.
                    for (slot in 3 downTo 0) {
                        val pic = shown.getOrNull(slot)
                        val x = fan.step * (slot - 1.5f)
                        val card = Modifier.offset(x, (FAN_RISE[slot].toFloat() * fan.card.value / FAN_W.value).dp).size(fan.card, fan.card * FAN_RATIO).graphicsLayer {
                            rotationZ = FAN_TURN[slot]
                            cameraDistance = camera
                            rotationX = tilt() * TILT_DEGREES
                            rotationY = tilt() * TILT_DEGREES * (slot - 1.5f) / 1.5f
                        }
                        if (pic == null) Box(
                            card.clip(MaterialTheme.shapes.medium)
                                .background(clean.app.ui.theme.LocalLook.current.tile)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), MaterialTheme.shapes.medium),
                        )
                        else Box(card.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface).padding(FRAME)) {
                            PicThumb(model, pic, 360, Modifier.fillMaxSize().clip(SmallShape))
                        }
                    }
                    if (pics.isEmpty()) {
                        Row(
                            Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable(onClick = add).minTouch().padding(horizontal = Space.l),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s),
                        ) {
                            Icon(CleanIcons.Add, null, Modifier.size(18.dp), tint = grey)
                            Text("Add photo", color = grey, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (pics.isNotEmpty() && tap != null) {
                val now = remember(pics) { LocalDateTime.now() }
                Column(Modifier.fillMaxWidth().clickable(onClick = tap).testTag("photos stats"), horizontalAlignment = Alignment.CenterHorizontally) {
                    TileLine(photosSummary(pics, now))
                    photosSpan(pics)?.let { TileLine(it) }
                }
            }
        }
    }
}

/** How the fan is laid out for a width: a card's width, the distance between neighbours' centres and the fan's height. */
class FanLayout(val card: Dp, val step: Dp, val height: Dp)

private val FAN_W = 76.dp
private const val FAN_RATIO = 100f / 76f
/** A step is this share of a card's width (the cards overlap), and [FAN_MARGIN] is what the turning corners and the lift keep clear at the two ends. */
private const val FAN_STEP_SHARE = 0.62f
private val FAN_MARGIN = 14.dp

/** The largest fan that fits [width]: four cards a step apart, their turned corners kept inside. */
fun fanLayout(width: Dp): FanLayout {
    val card = ((width - FAN_MARGIN * 2) / (1f + 3f * FAN_STEP_SHARE)).coerceAtMost(150.dp)
    return FanLayout(card, card * FAN_STEP_SHARE, card * FAN_RATIO + 20.dp)
}
private val FRAME = 2.dp

/** The most the Snapshots cards lean, in degrees, for the fastest scroll. */
private const val TILT_DEGREES = 3f

/**
 * Round 17 (15): how far the page's scroll is leaning things now, -1 to 1: follows the scroll's speed frame by frame ([tiltStep]) and settles to 0 a
 * moment after it stops (also with a finger held still). Always 0 with animations off. Read in a draw phase, so scrolling recomposes nothing.
 */
@Composable
private fun rememberScrollTilt(state: LazyListState): () -> Float {
    val motion = LocalMotion.current
    val tilt = remember { Animatable(0f) }
    LaunchedEffect(state, motion) {
        if (!motion) return@LaunchedEffect
        var last = state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collectLatest { now ->
            // Crossing from one row of the page to the next has no steady distance: that frame leaves the lean as it was.
            if (now.first == last.first) tilt.snapTo(tiltStep(tilt.value, (now.second - last.second).toFloat()))
            last = now
            delay(90)
            tilt.animateTo(0f, Motion.tiltSettle())
        }
    }
    return { tilt.value }
}
