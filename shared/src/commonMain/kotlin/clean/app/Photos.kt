package clean.app

import clean.app.platform.LocalPlatform

import clean.core.time.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import clean.app.data.MeasurementEntity
import clean.app.ui.theme.cardShape
import clean.app.ui.theme.SmallShape
import clean.core.Photo
import clean.core.PhotoFilter
import clean.core.Pic
import clean.core.asPic
import clean.core.compareOffered
import clean.core.groupPicsByDay
import clean.core.photosSpan
import clean.core.photosSummary
import clean.core.Pose
import clean.core.afterFirstText
import clean.core.agoText
import clean.core.apartText
import clean.core.comparePose
import clean.core.freePhoto
import clean.core.groupByDay
import clean.core.newestFirst
import clean.core.parseExifDate
import clean.core.parsePhoto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Files each of [sources] as a photo of [pose] and says how many went in. [afterwards] runs when all are done (the camera's temporary file goes then). */
fun AppModel.importPhotos(sources: List<String>, pose: Pose, exifDate: Boolean, afterwards: () -> Unit = {}) {
    val id = activeId.value ?: return
    viewModelScope.launch {
        val added = withContext(ioDispatcher) { sources.count { platform.addPhoto(id, it, pose, exifDate) != null }.also { afterwards() } }
        refreshPhotos()
        message = when {
            added == sources.size -> if (added == 1) "Photo added" else "$added photos added"
            added == 0 -> "That could not be read as a photo"
            else -> "$added of ${sources.size} photos added"
        }
    }
}

/** Renames [photo] to [at] and [pose]; [then] gets the photo as it is now. */
fun AppModel.changePhoto(photo: Photo, at: LocalDateTime, pose: Pose, then: (Photo) -> Unit = {}) {
    val id = activeId.value ?: return
    viewModelScope.launch {
        val moved = withContext(ioDispatcher) { platform.changePhoto(id, photo, at, pose) }
        if (moved == null) message = "Could not change the photo"
        else { refreshPhotos(); then(moved) }
    }
}

/** Removes [photo] at once with an Undo; the file is kept aside in the private cache until the Undo has passed. */
fun AppModel.deletePhoto(photo: Photo) {
    val id = activeId.value ?: return
    viewModelScope.launch {
        val away = withContext(ioDispatcher) { platform.removePhotoAside(id, photo) }
        if (away == null) { message = "Could not delete the photo"; return@launch }
        refreshPhotos()
        // The kept file goes once the Undo has passed.
        say("Photo deleted", expire = { viewModelScope.launch(ioDispatcher) { platform.discardAside(away) } }) {
            viewModelScope.launch {
                withContext(ioDispatcher) { platform.restorePhoto(id, photo, away) }
                refreshPhotos()
            }
        }
    }
}

/** Gives [pic] a new moment and pose (null: a snapshot), across the two folders when the kind changes; [then] gets the picture as it is now. */
fun AppModel.changePic(pic: Pic, at: LocalDateTime, pose: Pose?, then: (Pic) -> Unit = {}) {
    val id = activeId.value ?: return
    viewModelScope.launch {
        val moved = withContext(ioDispatcher) { platform.refile(id, pic, at, pose) }
        if (moved == null) message = "Could not change the photo"
        else { refreshPhotos(); refreshMoments(); then(moved) }
    }
}

/** Removes [pic] at once with an Undo, whichever store it is in. */
fun AppModel.deletePic(pic: Pic) {
    val moment = pic.moment
    if (moment != null) deleteMoments(listOf(moment)) else deletePhoto(pic.photo!!)
}

/** Files [sources] as photos of [pose], or as snapshots when it is null. */
fun AppModel.importPics(sources: List<String>, pose: Pose?, exifDate: Boolean, afterwards: () -> Unit = {}) {
    if (pose != null) importPhotos(sources, pose, exifDate, afterwards) else importMoments(sources, afterwards)
}

/** Where a picture's file is. */
fun clean.app.platform.Platform.picPath(profile: Long, pic: Pic): String = if (pic.snapshot) momentPath(profile, pic.name) else photoPath(profile, pic.name)

/** The weight logged on each day, in kg; the day's last entry when there are several. */
private fun weighInsByDay(all: List<MeasurementEntity>): Map<LocalDate, Double> =
    all.filter { it.file == "weight.csv" }
        .mapNotNull { m -> val at = parseDate(m.date); val kg = m.value.toDoubleOrNull(); if (at != null && kg != null) at to kg else null }
        .sortedBy { it.first }.associate { it.first.toLocalDate() to it.second }

@Composable
private fun AppModel.weighIns(): Map<LocalDate, Double> {
    val all by measurements.collectAsState()
    return remember(all) { weighInsByDay(all) }
}

@Composable
private fun AppModel.weightText(kg: Double): String {
    val unit = profile.collectAsState().value?.unit ?: "kg"
    return "${showWeight(kg, unit)} $unit"
}

// --- Pictures on screen ---

/** Thumbnails already cut, by file and size; about a dozen megabytes. */
val PHOTO_THUMBS = ImageCache(12 * 1024 * 1024)

/**
 * A photo cut to about [px], cropped to fill; a plain tile until it is ready. It is decoded off the main thread and kept.
 * With "Blur progress photos" on (kv `photos.blur`) it is decoded small and blurred in software ([softBlurred]), the same on any
 * Android version; the full-screen photo and Compare stay sharp.
 */
@Composable
fun PhotoThumb(model: AppModel, photo: Photo, px: Int, modifier: Modifier = Modifier) {
    val platform = LocalPlatform.current
    val id = model.activeId.collectAsState().value
    val file = remember(id, photo.name) { id?.let { platform.photoPath(it, photo.name) } }
    // Shut photos are blurred whatever the setting says (Round 31, Lock photos).
    val blur = model.flag("photos.blur", false) || model.photosShut()
    val key = thumbKey(file, px, blur)
    var bitmap by remember(key) { mutableStateOf(PHOTO_THUMBS.get(key)) }
    LaunchedEffect(key) {
        if (bitmap == null && file != null) bitmap = decodeThumb(platform, file, px, blur)?.also { PHOTO_THUMBS.put(key, it) }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** A picture of either kind, cut small: a progress photo's thumbnail or a snapshot's (the same blur and the same lock for both). */
@Composable
fun PicThumb(model: AppModel, pic: Pic, px: Int, modifier: Modifier = Modifier) {
    val photo = pic.photo
    val moment = pic.moment
    if (photo != null) PhotoThumb(model, photo, px, modifier) else if (moment != null) MomentThumb(model, moment, px, modifier)
}

/** The cache key of a thumbnail: a blurred one has a key of its own, so switching "Blur photos" shows at once and neither is ever served for the other. */
fun thumbKey(file: String?, px: Int, blur: Boolean) = "$file@${if (blur) "blur" else px}"

/**
 * A stored picture cut for a small view, off the main thread. Blurred (Round 31): the decoder is only asked for a small picture ([BLUR_DECODE] px; a phone's JPEG
 * decoder may not shrink past an eighth, so it is cut to [BLUR_SIDE] px as well) and that is blurred in software ([softBlurred]), so the thumbnail is smooth and
 * the same on every Android version and on iOS. Only the blurred pixels are kept; no sharp copy of the picture is held for a blurred view.
 * Shared by progress photos and moments.
 */
suspend fun decodeThumb(platform: clean.app.platform.Platform, file: String, px: Int, blur: Boolean): ImageBitmap? = withContext(ioDispatcher) {
    if (blur) platform.decodeStored(file, BLUR_DECODE, light = true)?.let { softBlurred(it) }
    else platform.decodeStored(file, px, light = true)
}

/** How many pixels a blurred thumbnail keeps on its longer side, and how large the decoder is asked to make it first. */
const val BLUR_SIDE = 32
const val BLUR_DECODE = 48

/** The box passes of [softBlurred]: three of radius [BLUR_RADIUS] make a close match of a Gaussian of sigma about 3.5 px, a neighbourhood of a ninth of the picture's width. */
private const val BLUR_RADIUS = 3
private const val BLUR_PASSES = 3

/**
 * [src] as a soft blur: cut down to [BLUR_SIDE] px, blurred with [BLUR_PASSES] box passes over a [BLUR_RADIUS]-pixel neighbourhood (the edges repeat, so no dark rim),
 * in common code, so it is the same on any Android version and on iOS. Drawn larger, the small bitmap is smoothed by the draw's bilinear filter. Only colour shapes
 * are left: nothing of the picture can be read back from it.
 */
fun softBlurred(src: ImageBitmap): ImageBitmap {
    val small = shrunk(src, BLUR_SIDE)
    val w = small.width
    val h = small.height
    val argb = IntArray(w * h)
    small.readPixels(argb, 0, 0, w, h)
    val channels = Array(3) { c -> FloatArray(w * h) { i -> ((argb[i] shr (16 - 8 * c)) and 0xFF).toFloat() } }
    for (channel in channels) repeat(BLUR_PASSES) { boxBlur(channel, w, h, BLUR_RADIUS) }
    val out = ImageBitmap(w, h)
    val canvas = androidx.compose.ui.graphics.Canvas(out)
    val paint = androidx.compose.ui.graphics.Paint().apply { isAntiAlias = false; blendMode = androidx.compose.ui.graphics.BlendMode.Src }
    for (y in 0 until h) for (x in 0 until w) {
        val i = y * w + x
        paint.color = androidx.compose.ui.graphics.Color(channels[0][i].toInt().coerceIn(0, 255), channels[1][i].toInt().coerceIn(0, 255), channels[2][i].toInt().coerceIn(0, 255))
        canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
    }
    return out
}

/** One box blur of [v] (w by h) over [radius] pixels either way, along the rows and then the columns; the edge pixel repeats. */
private fun boxBlur(v: FloatArray, w: Int, h: Int, radius: Int) {
    val line = FloatArray(maxOf(w, h))
    val size = 2 * radius + 1
    fun run(count: Int, at: (Int) -> Int, put: (Int, Float) -> Unit) {
        // [at] is the index of the n-th pixel of the line, [put] writes it back from [line].
        var sum = 0f
        for (k in -radius..radius) sum += v[at(k.coerceIn(0, count - 1))]
        for (n in 0 until count) {
            line[n] = sum / size
            sum += v[at((n + radius + 1).coerceIn(0, count - 1))] - v[at((n - radius).coerceIn(0, count - 1))]
        }
        for (n in 0 until count) put(n, line[n])
    }
    for (y in 0 until h) run(w, { y * w + it }, { n, f -> v[y * w + n] = f })
    for (x in 0 until w) run(h, { it * w + x }, { n, f -> v[n * w + x] = f })
}

/** [src] drawn down to [side] pixels on its longer side. */
fun shrunk(src: androidx.compose.ui.graphics.ImageBitmap, side: Int): androidx.compose.ui.graphics.ImageBitmap {
    val scale = side.toFloat() / maxOf(src.width, src.height)
    if (scale >= 1f) return src
    val w = (src.width * scale).toInt().coerceAtLeast(1)
    val h = (src.height * scale).toInt().coerceAtLeast(1)
    val out = androidx.compose.ui.graphics.ImageBitmap(w, h)
    androidx.compose.ui.graphics.Canvas(out).drawImageRect(
        src, dstSize = androidx.compose.ui.unit.IntSize(w, h),
        paint = androidx.compose.ui.graphics.Paint().apply { filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium },
    )
    return out
}

/** A photo at the size of the screen, fitted whole. Decoded off the main thread, not kept. */
@Composable
private fun PhotoFull(model: AppModel, photo: Pic, description: String?, modifier: Modifier = Modifier) {
    val platform = LocalPlatform.current
    val id = model.activeId.collectAsState().value
    val px = platform.screenLongSidePx
    var bitmap by remember(id, photo.name) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(id, photo.name) {
        bitmap = id?.let { withContext(ioDispatcher) { platform.decodeStored(platform.picPath(it, photo), px, light = false) } }
    }
    Box(modifier) {
        bitmap?.let { Image(it, description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

// --- Pinch zoom ---

/** How far a photo is pinched in and where: a point p of it is drawn at p * scale + pan, from its top left. */
private class PhotoZoom {
    var scale by mutableFloatStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)

    fun reset() { scale = 1f; pan = Offset.Zero }

    fun clamp(pan: Offset, scale: Float, width: Float, height: Float) =
        Offset(pan.x.coerceIn(width * (1 - scale), 0f), pan.y.coerceIn(height * (1 - scale), 0f))
}

private const val MAX_PHOTO_ZOOM = 4f

/**
 * Two fingers pinch and pan [zoom] (up to 4x); while it is zoomed one finger pans. Like the body figure's: read ahead of
 * the pager or list around it, and with one finger at normal size nothing is taken, so a pager swipes at 1x. [columns] is
 * how many equal pictures the box holds (the pan keeps each over its own part).
 */
private fun Modifier.pinchZoom(zoom: PhotoZoom, columns: Int = 1): Modifier = pointerInput(zoom) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var pinching = false
        var panning = false
        var drift = Offset.Zero
        val width = size.width.toFloat() / columns
        val height = size.height.toFloat()
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val fingers = event.changes.filter { it.pressed }
            if (fingers.size > 1) pinching = true
            if (pinching) {
                val moved = fingers.filter { it.previousPressed }
                if (moved.size > 1) {
                    val before = moved.fold(Offset.Zero) { a, c -> a + c.previousPosition } / moved.size.toFloat()
                    val now = moved.fold(Offset.Zero) { a, c -> a + c.position } / moved.size.toFloat()
                    val spreadBefore = moved.sumOf { hypot(it.previousPosition.x - before.x, it.previousPosition.y - before.y).toDouble() }.toFloat()
                    val spreadNow = moved.sumOf { hypot(it.position.x - now.x, it.position.y - now.y).toDouble() }.toFloat()
                    val to = if (spreadBefore > 0f) (zoom.scale * spreadNow / spreadBefore).coerceIn(1f, MAX_PHOTO_ZOOM) else zoom.scale
                    // The point under the fingers stays under them.
                    zoom.pan = zoom.clamp(now - (before - zoom.pan) * (to / zoom.scale), to, width, height)
                    zoom.scale = to
                }
                event.changes.forEach { it.consume() }
            } else if (zoom.scale > 1f && fingers.size == 1) {
                val change = fingers.first()
                drift += change.positionChange()
                if (drift.getDistance() > slop) panning = true
                if (panning) {
                    zoom.pan = zoom.clamp(zoom.pan + change.positionChange(), zoom.scale, width, height)
                    change.consume()
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

private fun Modifier.zoomed(zoom: PhotoZoom): Modifier = graphicsLayer {
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = zoom.scale
    scaleY = zoom.scale
    translationX = zoom.pan.x
    translationY = zoom.pan.y
}

// --- Adding ---

/**
 * One Add for the whole section (Round 39): the function returned opens a small sheet with "Take a photo" and "Choose from the phone" (the camera into the cache through the
 * file provider, the system picker for several pictures; no permission either way). Once there is a picture, a second sheet asks what it is: a progress photo (and the pose,
 * the last one used remembered as `photo.pose`) or a snapshot (the last kind remembered as `photo.kind`). A sheet put away without an answer files nothing.
 */
@Composable
fun rememberPhotoAdd(model: AppModel, asker: String): () -> Unit {
    val platform = LocalPlatform.current
    var show by remember { mutableStateOf(false) }
    var poseKey by model.remembered("photo.pose", Pose.FRONT.key)
    var kind by model.remembered("photo.kind", "progress")
    val pose = Pose.of(poseKey) ?: Pose.FRONT
    val pick = platform.rememberPhotoPicker(true) { picked -> if (picked.isNotEmpty()) { model.photoAsker = asker; model.photoPending = picked to false } }
    val take = platform.rememberCamera { shot -> if (shot != null) { model.photoAsker = asker; model.photoPending = listOf(shot) to true } }
    if (show) Sheet({ show = false }, "Add photo") {
        SheetRow("Take a photo", CleanIcons.Face) {
            show = false
            if (!take()) model.message = "There is no camera app on this phone"
        }
        SheetRow("Choose from the phone", CleanIcons.Add) {
            show = false
            pick()
        }
    }
    model.photoPending?.takeIf { model.photoAsker == asker }?.let { (sources, camera) ->
        fun discard() { if (camera) sources.forEach { platform.discardStaged(it) } }
        Sheet({ model.photoPending = null; discard() }, "What is it?") {
            Column(Modifier.padding(horizontal = Space.xl, vertical = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Choice(listOf("progress", "snapshot"), kind, { if (it == "progress") "Progress" else "Snapshot" }) { kind = it }
                if (kind == "progress") {
                    Caption("Pose")
                    Choice(Pose.entries, pose, { it.label }) { poseKey = it.key }
                }
            }
            SheetGap()
            SheetRow("Add", CleanIcons.Add) {
                model.photoPending = null
                model.importPics(sources, if (kind == "progress") pose else null, exifDate = !camera) { discard() }
            }
        }
    }
    return { model.photoAsker = asker; show = true }
}

// --- The timeline ---

/** The Photos page (Round 39): progress photos and snapshots in one timeline by day, filtered by kind (and, under Progress, by pose). */
@Composable
fun PhotosScreen(model: AppModel, first: PhotoFilter = PhotoFilter.ALL) {
    val pics by model.pics.collectAsState()
    val weighIns = model.weighIns()
    var filter by remember { mutableStateOf(first) }
    val add = rememberPhotoAdd(model, "page")
    val days = remember(pics, filter) { groupPicsByDay(pics, filter) }
    val today = remember { LocalDate.now() }
    val compare = remember(pics, filter) { compareOffered(pics, filter) }
    Scaffold(
        containerColor = pageContainer(),
        topBar = { BackBar("Photos", model::back, actions = { IconButton(add) { Icon(CleanIcons.Add, "Add photo") } }) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = Space.xxl)) {
            if (pics.isEmpty()) item { EmptyState("No photos yet", "Progress photos and snapshots, kept on this phone only. Front, side and back photos taken now and then show how your body changes.", "Add photo", add) }
            else {
                item {
                    Column(Modifier.padding(horizontal = Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                        // The kind first. The poses are a second row, there only while Progress is the kind: a pose is a way to look at progress photos, not at snapshots.
                        Choice(listOf(PhotoFilter.ALL, PhotoFilter.PROGRESS, PhotoFilter.SNAPSHOTS), if (filter.progress) PhotoFilter.PROGRESS else filter, { it.label }) { filter = it }
                        if (filter.progress) Choice(listOf<Pose?>(null) + Pose.entries, filter.pose, { it?.label ?: "All poses" }) { filter = PhotoFilter.of(it) }
                        if (compare) QuietButton("Compare", { model.open(Screen.PhotoCompare(filter.pose)) })
                    }
                }
                if (days.isEmpty()) item { Text("No ${filter.label.lowercase()} photos yet.", Modifier.padding(Space.l), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                for (day in days) {
                    item(key = "day-${day.day}") {
                        Column(Modifier.padding(start = Space.l, end = Space.l, top = Space.xl, bottom = Space.s)) {
                            SectionTitle(prettyDay(day.day))
                            val quiet = listOfNotNull(agoText(day.day, today), weighIns[day.day]?.let { model.weightText(it) }).joinToString(" · ")
                            Text(quiet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(day.pics.chunked(3), key = { "row-${it.first().name}" }) { row ->
                        Row(Modifier.padding(horizontal = Space.l).padding(bottom = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            for (pic in row) Column(Modifier.weight(1f)) {
                                PicThumb(
                                    model, pic, 360,
                                    // Before the clip, so the photo travelling in from the card has the thumbnail's corners.
                                    Modifier.fillMaxWidth().aspectRatio(3f / 4f).morphs("photo", pic.name, scaled = true).clip(SmallShape)
                                        .semantics { contentDescription = "${pic.label} photo, ${prettyDay(pic.day)}"; role = Role.Button }
                                        .pressable { model.open(Screen.Photo(pic.name, filter)) },
                                )
                                Text(pic.label, Modifier.padding(top = Space.xs).testTag("pic label"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

// --- Full screen ---

private val PHOTO_INK = Color.White
private val PHOTO_QUIET = Color.White.copy(alpha = 0.7f)
private val PHOTO_BAR = Color.Black.copy(alpha = 0.6f)

@Composable
fun PhotoScreen(model: AppModel, name: String, filter: PhotoFilter) {
    val all by model.pics.collectAsState()
    val list = remember(all, filter) { all.filter { filter.matches(it) } }
    val weighIns = model.weighIns()
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(list.indexOfFirst { it.name == name }.coerceAtLeast(0)) { list.size }
    var bars by remember { mutableStateOf(true) }
    var zoomedIn by remember { mutableStateOf(false) }
    var menu by remember { mutableIntStateOf(0) }
    // After a rename the photo has a new name and a new place in the list; the pager follows it there.
    var follow by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(list, follow) {
        val wanted = follow ?: return@LaunchedEffect
        val index = list.indexOfFirst { it.name == wanted }
        if (index >= 0) { pager.scrollToPage(index); follow = null }
    }
    // The last photo was deleted: this page has nothing left to show. A page that closes itself checks it is still on top.
    LaunchedEffect(list.isEmpty()) { if (list.isEmpty() && model.stack.lastOrNull() is Screen.Photo) model.back() }
    // White icons on the black page, whatever the theme.
    DisposableEffect(Unit) {
        val was = platform.setStatusBarIconsDark(false)
        onDispose { if (was != null) platform.setStatusBarIconsDark(was) }
    }
    val photo = list.getOrNull(pager.currentPage)
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomedIn, key = { list[it].name }) { page ->
            val shown = list[page]
            val zoom = remember(shown.name) { PhotoZoom() }
            LaunchedEffect(zoom) { snapshotFlow { zoom.scale > 1f }.collect { zoomedIn = it } }
            DisposableEffect(zoom) { onDispose { zoomedIn = false } }
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(zoom) { detectTapGestures(onDoubleTap = { zoom.reset() }, onTap = { bars = !bars }) }
                    .pinchZoom(zoom),
            ) {
                Box(Modifier.fillMaxSize().zoomed(zoom)) {
                    PhotoFull(model, shown, "${shown.label} photo, ${prettyDay(shown.day)}", Modifier.fillMaxSize())
                }
            }
        }
        if (bars && photo != null) {
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().background(PHOTO_BAR).statusBarsPadding().padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = model::back) { Icon(CleanIcons.Back, "Back", tint = PHOTO_INK) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { menu = 1 }) { Icon(CleanIcons.More, "Photo options", tint = PHOTO_INK) }
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(PHOTO_BAR).navigationBarsPadding().padding(horizontal = Space.l, vertical = Space.m)) {
                Text("${prettyDay(photo.day)} · ${photo.label}", color = PHOTO_INK, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(all.lastOrNull()?.let { afterFirstText(it.day, photo.day) }.orEmpty(), color = PHOTO_QUIET, style = MaterialTheme.typography.bodyMedium)
                    weighIns[photo.day]?.let { kg ->
                        Text(" · ", color = PHOTO_QUIET)
                        Text(model.weightText(kg), Modifier.minTouch().clickable { model.open(Screen.MeasurementDetail("weight.csv")) }.padding(vertical = Space.m), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
    val id = model.activeId.collectAsState().value
    if (menu == 1 && photo != null) Sheet({ menu = 0 }) {
        SheetRow("Change date", CleanIcons.Calendar) {
            menu = 0
            platform.pickDateTime(photo.at) { at -> model.changePic(photo, at, photo.pose) { moved -> follow = moved.name } }
        }
        SheetRow("Change pose", CleanIcons.Person, value = photo.label) { menu = 2 }
        SheetRow("Share", CleanIcons.Share) {
            menu = 0
            if (id != null) scope.launch {
                val bytes = withContext(ioDispatcher) { if (photo.snapshot) platform.momentBytes(id, photo.name) else platform.photoBytes(id, photo.name) }
                val file = if (photo.snapshot) "Snapshot ${photo.day}.jpg" else "Progress ${photo.day} ${photo.label}.jpg"
                if (bytes != null) platform.shareFile(file, bytes, "image/jpeg")
            }
        }
        SheetGap()
        SheetRow("Delete", CleanIcons.Delete, danger = true) { menu = 0; model.deletePic(photo) }
    }
    // The pose sheet also holds "Snapshot": a rename from one folder to the other turns a snapshot into a progress photo and back.
    if (menu == 2 && photo != null) Sheet({ menu = 0 }, "Pose") {
        for (p in listOf<Pose?>(Pose.FRONT, Pose.SIDE, Pose.BACK, null)) SheetRow(p?.label ?: "Snapshot", value = if (p == photo.pose) "Current" else "") {
            menu = 0
            // Out of a filtered list it goes; the pager then shows the photo that took its place.
            model.changePic(photo, photo.at, p) { moved -> if (filter.matches(moved)) follow = moved.name }
        }
    }
}

// --- Compare ---

@Composable
fun PhotoCompareScreen(model: AppModel, asked: Pose?) {
    val photos by model.photos.collectAsState()
    val weighIns = model.weighIns()
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(comparePose(photos, asked)) }
    // Oldest first, so older is down the list and newer is up it.
    val pool = remember(photos, chosen) { photos.filter { chosen == null || it.pose == chosen }.sortedBy { it.at } }
    var older by remember(chosen) { mutableIntStateOf(0) }
    var newer by remember(chosen) { mutableIntStateOf(Int.MAX_VALUE) }
    val zoom = remember { PhotoZoom() }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar("Compare", model::back) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (pool.size < 2) {
                EmptyState("Nothing to compare", "Two photos of the same pose are needed.")
                return@Column
            }
            val a = older.coerceIn(0, pool.lastIndex)
            val b = newer.coerceIn(0, pool.lastIndex)
            val left = pool[a]
            val right = pool[b]
            Box(Modifier.padding(horizontal = Space.l)) {
                Choice(listOf<Pose?>(null) + Pose.entries.filter { p -> photos.count { it.pose == p } >= 2 }, chosen, { it?.label ?: "All" }) { chosen = it; zoom.reset() }
            }
            Row(Modifier.weight(1f).fillMaxWidth().padding(Space.l).pinchZoom(zoom, columns = 2), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                CompareHalf(model, left, chosen, a > 0, a < pool.lastIndex, { older = a - 1 }, { older = a + 1 }, zoom)
                CompareHalf(model, right, chosen, b > 0, b < pool.lastIndex, { newer = b - 1 }, { newer = b + 1 }, zoom)
            }
            val apart = apartText(left.day, right.day)
            val before = weighIns[left.day]
            val after = weighIns[right.day]
            val change = if (before != null && after != null) after - before else null
            // Left is the older of the two unless the user has stepped past the other.
            val middle = listOfNotNull(
                apart,
                change?.let { if (abs(it) < 0.05) "Body weight unchanged" else "Body weight ${if (it > 0) "+" else "−"}${model.weightText(abs(it))}" },
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(apart, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (before != null && after != null) {
                    Text(
                        "${model.weightText(before)} → ${model.weightText(after)}" + (change?.let { " · ${if (abs(it) < 0.05) "no change" else (if (it > 0) "+" else "−") + model.weightText(abs(it))}" } ?: ""),
                        Modifier.minTouch().clickable { model.open(Screen.MeasurementDetail("weight.csv")) }.padding(vertical = Space.m),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            val id = model.activeId.collectAsState().value
            QuietButton("Share", {
                if (id != null) scope.launch {
                    val bytes = withContext(ioDispatcher) { platform.comparePhotos(id, left, right, prettyDay(left.day), prettyDay(right.day), middle) }
                    if (bytes == null) model.message = "Could not make the picture" else platform.shareFile("Progress ${left.day} to ${right.day}.jpg", bytes, "image/jpeg")
                }
            }, Modifier.align(Alignment.CenterHorizontally).padding(bottom = Space.l))
        }
    }
}

/**
 * One side of the comparison: the photo, and under it its date with arrows (a swipe on the photo does the same) to step through the
 * pose's photos. A tap on the photo opens it full screen with its date, in the photo viewer over the same pose ([pose]; null is all).
 */
@Composable
private fun RowScope.CompareHalf(model: AppModel, photo: Photo, pose: Pose?, hasOlder: Boolean, hasNewer: Boolean, stepOlder: () -> Unit, stepNewer: () -> Unit, zoom: PhotoZoom) {
    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(cardShape()).background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClickLabel = "Open full screen") { model.open(Screen.Photo(photo.name, PhotoFilter.of(pose))) }
                .swipeSteps({ if (hasOlder) stepOlder() }, { if (hasNewer) stepNewer() }),
        ) {
            Box(Modifier.fillMaxSize().zoomed(zoom)) {
                PhotoFull(model, photo.asPic(), "${photo.pose.label} photo, ${prettyDay(photo.day)}", Modifier.fillMaxSize())
            }
        }
        Text(prettyDay(photo.day), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = stepOlder, enabled = hasOlder) { Icon(CleanIcons.Back, "Older photo") }
            IconButton(onClick = stepNewer, enabled = hasNewer) { Icon(CleanIcons.Forward, "Newer photo") }
        }
    }
}
