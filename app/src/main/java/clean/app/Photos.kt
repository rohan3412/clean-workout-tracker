package clean.app

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewModelScope
import clean.app.data.MeasurementEntity
import clean.app.ui.theme.cardShape
import clean.app.ui.theme.SmallShape
import clean.core.Photo
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
import java.io.File
import java.io.InputStream
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Progress photos. One JPEG per photo under `filesDir/photos/<profileId>/`, named for its moment and pose
 * (`20261012-093000_front.jpg`); the name is the whole record, so changing the date or the pose renames the file.
 * A photo is turned upright, scaled to at most 2048 px on its longer side and written without EXIF (no location is
 * kept). Nothing here is written to the gallery or leaves the phone but through the share sheet, by hand.
 * Rules and layout: docs/data-format.md, "Progress photos".
 */
object PhotoFiles {
    private const val LONGEST = 2048
    private const val QUALITY = 88
    private const val INCOMING = "incoming.tmp"

    private fun folder(context: Context, profileId: Long) = File(File(context.filesDir, "photos"), profileId.toString())

    fun file(context: Context, profileId: Long, name: String) = File(folder(context, profileId), name)

    /** A removed photo waits here, in the private cache, until its Undo has passed. */
    private fun aside(context: Context) = File(context.cacheDir, "photo-removed")

    /** The cache folder the camera writes to (`file_paths.xml`). */
    fun capture(context: Context): File = File(File(context.cacheDir, "camera").apply { mkdirs() }, "capture.jpg")

    /** The profile's photos, newest first; files whose names are not photos' are left alone and not listed. Blocking. */
    fun list(context: Context, profileId: Long): List<Photo> = newestFirst(folder(context, profileId).list().orEmpty().mapNotNull(::parsePhoto))

    fun deleteAll(context: Context, profileId: Long) {
        folder(context, profileId).deleteRecursively()
    }

    /**
     * Files the picture [open] reads as a photo of [pose]: dated by its EXIF `DateTimeOriginal` when [exifDate] and it has one,
     * else now. Null when it cannot be read as a picture. Blocking.
     */
    fun add(context: Context, profileId: Long, open: () -> InputStream?, pose: Pose, exifDate: Boolean): Photo? {
        val exif = open()?.use { runCatching { ExifInterface(it) }.getOrNull() }
        val turn = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) ?: ExifInterface.ORIENTATION_NORMAL
        val taken = (if (exifDate) parseExifDate(exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)) else null) ?: LocalDateTime.now()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, LONGEST) }
        val decoded = open()?.use { runCatching { BitmapFactory.decodeStream(it, null, options) }.getOrNull() } ?: return null
        val upright = upright(decoded, turn)
        val scale = LONGEST.toFloat() / maxOf(upright.width, upright.height)
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(upright, (upright.width * scale).roundToInt(), (upright.height * scale).roundToInt(), true) else upright
        val dir = folder(context, profileId).apply { mkdirs() }
        val photo = freePhoto(taken, pose, dir.list().orEmpty().toSet())
        // Written under a name that is not a photo's, so a half-written file is never listed.
        val temp = File(dir, INCOMING)
        val written = runCatching { temp.outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) } }.getOrDefault(false)
        if (!written || !temp.renameTo(File(dir, photo.name))) {
            temp.delete()
            return null
        }
        return photo
    }

    /** [photo] moved to [at] and [pose] (renamed; a second is added to a taken name). The photo as it is now, or null when the rename failed. Blocking. */
    fun change(context: Context, profileId: Long, photo: Photo, at: LocalDateTime, pose: Pose): Photo? {
        val dir = folder(context, profileId)
        val target = freePhoto(at, pose, dir.list().orEmpty().toSet() - photo.name)
        if (target.name == photo.name) return photo
        return target.takeIf { File(dir, photo.name).renameTo(File(dir, it.name)) }
    }

    /** Takes [photo] out of the folder and keeps it aside; the file kept aside, or null when it could not be moved. Blocking. */
    fun removeAside(context: Context, profileId: Long, photo: Photo): File? {
        val away = aside(context).apply { deleteRecursively(); mkdirs() }
        val to = File(away, "$profileId-${photo.name}")
        return to.takeIf { file(context, profileId, photo.name).renameTo(it) }
    }

    /** Puts a photo that was kept [from] aside back where it was. Blocking. */
    fun putBack(context: Context, profileId: Long, photo: Photo, from: File): Boolean {
        val dir = folder(context, profileId).apply { mkdirs() }
        return from.renameTo(File(dir, freePhoto(photo.at, photo.pose, dir.list().orEmpty().toSet()).name))
    }

    /** The largest power of two that still leaves the longer side at least [px]. */
    private fun sampleFor(width: Int, height: Int, px: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= px) sample *= 2
        return sample
    }

    /** The picture at most about [px] on its longer side (never smaller than [px] unless the file is), or null. Blocking. */
    fun decode(file: File, px: Int, config: Bitmap.Config): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleFor(bounds.outWidth, bounds.outHeight, px); inPreferredConfig = config }
        return runCatching { BitmapFactory.decodeFile(file.path, options) }.getOrNull()
    }

    private fun upright(src: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(-90f)
            else -> return src
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /** One picture of two photos side by side, each fitted in its cell, with the date under each and [middle] between the rows of text. JPEG bytes. Blocking. */
    fun pair(context: Context, profileId: Long, left: Photo, right: Photo, leftText: String, rightText: String, middle: List<String>): ByteArray? {
        val cellW = 1080
        val cellH = 1440
        val gap = 24
        val textH = 120 + 64 * middle.size
        val a = decode(file(context, profileId, left.name), cellH, Bitmap.Config.ARGB_8888) ?: return null
        val b = decode(file(context, profileId, right.name), cellH, Bitmap.Config.ARGB_8888) ?: return null
        val out = Bitmap.createBitmap(2 * cellW + gap, cellH + textH, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(out)
        canvas.drawColor(android.graphics.Color.BLACK)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 48f; textAlign = Paint.Align.CENTER }
        val quiet = Paint(ink).apply { color = 0xB3FFFFFF.toInt(); textSize = 40f }
        for ((i, pair) in listOf(a to leftText, b to rightText).withIndex()) {
            val (bitmap, text) = pair
            val x = i * (cellW + gap)
            val fit = minOf(cellW.toFloat() / bitmap.width, cellH.toFloat() / bitmap.height)
            val w = bitmap.width * fit
            val h = bitmap.height * fit
            val at = RectF(x + (cellW - w) / 2, (cellH - h) / 2, x + (cellW + w) / 2, (cellH + h) / 2)
            canvas.drawBitmap(bitmap, null, at, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.drawText(text, x + cellW / 2f, cellH + 72f, ink)
        }
        middle.forEachIndexed { i, line -> canvas.drawText(line, out.width / 2f, cellH + 72f + 64f * (i + 1), quiet) }
        return java.io.ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }.toByteArray()
    }
}

// --- The model's side of it ---

/** Files each of [sources] as a photo of [pose] and says how many went in. [afterwards] runs when all are done (the camera's temporary file goes then). */
fun AppModel.importPhotos(sources: List<() -> InputStream?>, pose: Pose, exifDate: Boolean, afterwards: () -> Unit = {}) {
    val id = activeId.value ?: return
    val context = getApplication<Application>()
    viewModelScope.launch {
        val added = withContext(Dispatchers.IO) { sources.count { PhotoFiles.add(context, id, it, pose, exifDate) != null }.also { afterwards() } }
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
    val context = getApplication<Application>()
    viewModelScope.launch {
        val moved = withContext(Dispatchers.IO) { PhotoFiles.change(context, id, photo, at, pose) }
        if (moved == null) message = "Could not change the photo"
        else { refreshPhotos(); then(moved) }
    }
}

/** Removes [photo] at once with an Undo; the file is kept aside in the private cache until the Undo has passed. */
fun AppModel.deletePhoto(photo: Photo) {
    val id = activeId.value ?: return
    val context = getApplication<Application>()
    viewModelScope.launch {
        val away = withContext(Dispatchers.IO) { PhotoFiles.removeAside(context, id, photo) }
        if (away == null) { message = "Could not delete the photo"; return@launch }
        refreshPhotos()
        // The kept file goes once the Undo has passed.
        say("Photo deleted", expire = { viewModelScope.launch(Dispatchers.IO) { away.delete() } }) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { PhotoFiles.putBack(context, id, photo, away) }
                refreshPhotos()
            }
        }
    }
}

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
private val THUMBS = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 2
}

/**
 * A photo cut to about [px], cropped to fill; a plain tile until it is ready. It is decoded off the main thread and kept.
 * With "Blur progress photos" on (kv `photos.blur`) it is decoded a few pixels wide and stretched, which blurs it on any
 * Android version at no cost; the full-screen photo and Compare stay sharp.
 */
@Composable
private fun PhotoThumb(model: AppModel, photo: Photo, px: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val id = model.activeId.collectAsState().value
    val file = remember(id, photo.name) { id?.let { PhotoFiles.file(context, it, photo.name) } }
    val px = if (model.flag("photos.blur", false)) 8 else px
    val key = "${file?.path}@$px"
    var bitmap by remember(key) { mutableStateOf(THUMBS.get(key)) }
    LaunchedEffect(key) {
        if (bitmap == null && file != null) {
            bitmap = withContext(Dispatchers.IO) { PhotoFiles.decode(file, px, Bitmap.Config.RGB_565)?.asImageBitmap() }?.also { THUMBS.put(key, it) }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        bitmap?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** A photo at the size of the screen, fitted whole. Decoded off the main thread, not kept. */
@Composable
private fun PhotoFull(model: AppModel, photo: Photo, description: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val id = model.activeId.collectAsState().value
    val px = context.resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) }
    var bitmap by remember(id, photo.name) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(id, photo.name) {
        bitmap = id?.let { withContext(Dispatchers.IO) { PhotoFiles.decode(PhotoFiles.file(context, it, photo.name), px, Bitmap.Config.ARGB_8888)?.asImageBitmap() } }
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

/** The sheet that adds photos, and the camera and picker it starts. Always composed; the sheet shows while [show]. */
@Composable
fun AddPhotoHost(model: AppModel, show: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var poseKey by model.remembered("photo.pose", Pose.FRONT.key)
    val pose = Pose.of(poseKey) ?: Pose.FRONT
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) model.importPhotos(uris.map { uri -> { context.contentResolver.openInputStream(uri) } }, pose, exifDate = true)
    }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val shot = PhotoFiles.capture(context)
        if (ok) model.importPhotos(listOf({ shot.inputStream() }), pose, exifDate = false) { shot.delete() } else shot.delete()
    }
    if (!show) return
    Sheet(onDismiss, "Add photo") {
        Column(Modifier.padding(horizontal = Space.xl, vertical = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Caption("File under")
            Choice(Pose.entries, pose, { it.label }) { poseKey = it.key }
        }
        SheetGap()
        SheetRow("Take a photo", CleanIcons.Face) {
            onDismiss()
            val shot = PhotoFiles.capture(context)
            try {
                shot.createNewFile()
                take.launch(FileProvider.getUriForFile(context, "clean.app.files", shot))
            } catch (e: ActivityNotFoundException) {
                model.message = "There is no camera app on this phone"
            }
        }
        SheetRow("Choose from the phone", CleanIcons.Add) {
            onDismiss()
            pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

// --- Profile ---

/** How many photos the wide Progress photos card shows side by side. */
private const val STRIP_SLOTS = 4

/** A photo cropped to fill, with its day over the foot of it on a scrim that fades in from the bottom; [onClick] opens it. */
@Composable
private fun DatedPhoto(model: AppModel, photo: Photo, px: Int, modifier: Modifier, showDay: Boolean, onClick: () -> Unit) {
    val scrim = MaterialTheme.colorScheme.scrim
    Box(modifier.semantics { contentDescription = "${photo.pose.label} photo, ${prettyDay(photo.day)}"; role = Role.Button }.pressable(onClick = onClick)) {
        // The same photo is in the timeline on the page this card opens: it travels there, resizing (both are 3:4, so it only scales).
        PhotoThumb(model, photo, px, Modifier.fillMaxSize().morphs("photo", photo.name, scaled = true))
        if (showDay) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, scrim.copy(alpha = 0.7f)))).padding(start = Space.m, end = Space.m, top = Space.xxxl, bottom = Space.s)) {
                Text(prettyDay(photo.day), color = scrim.readableOn(), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/**
 * Profile's Progress photos card, image first. Wide: the latest four photos side by side, running to the card's edges and cropped to fill,
 * the newest with its day over it, and under them "Add photo" and (with two photos of one pose) "Compare", first against latest. Half: the
 * latest photo filling the card with its day over it. With none, a line and a way to add the first. The card opens the timeline.
 */
@Composable
fun ProfilePhotosCard(model: AppModel, half: Boolean) {
    val photos by model.photos.collectAsState()
    var adding by remember { mutableStateOf(false) }
    AddPhotoHost(model, adding) { adding = false }
    val open = { model.open(Screen.Photos) }
    val latest = photos.firstOrNull()
    if (latest == null) {
        Section("Progress photos") {
            EmptyPreview("Front, side and back photos taken now and then show how your body changes.", "Add a photo", { adding = true }) { PreviewSamples.Frames() }
        }
        return
    }
    if (half) {
        Section("", inner = 0.dp, outer = Space.s, onClick = open, bleed = { DatedPhoto(model, latest, 540, Modifier.fillMaxWidth().aspectRatio(3f / 4f), true, open) }) {}
        return
    }
    val pose = comparePose(photos, null)
    Section(
        "Progress photos", onClick = open,
        bleed = {
            Row(Modifier.padding(top = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
                for (slot in 0 until STRIP_SLOTS) {
                    val photo = photos.getOrNull(slot)
                    val cell = Modifier.weight(1f).aspectRatio(3f / 4f)
                    if (photo == null) Box(cell.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)))
                    else DatedPhoto(model, photo, 360, cell, slot == 0) { model.open(Screen.Photo(photo.name, null)) }
                }
            }
            Row(Modifier.padding(horizontal = Space.l, vertical = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                QuietButton("Add photo", { adding = true })
                if (pose != null) QuietButton("Compare first and latest", { model.open(Screen.PhotoCompare(pose)) })
            }
        },
    ) {
        Text(
            "${plural(photos.size, "photo")}, latest ${agoText(latest.day, java.time.LocalDate.now()).lowercase()}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- The timeline ---

@Composable
fun PhotosScreen(model: AppModel) {
    val photos by model.photos.collectAsState()
    val weighIns = model.weighIns()
    var filter by remember { mutableStateOf<Pose?>(null) }
    var adding by remember { mutableStateOf(false) }
    AddPhotoHost(model, adding) { adding = false }
    val days = remember(photos, filter) { groupByDay(photos, filter) }
    val today = remember { LocalDate.now() }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar("Photos", model::back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = Space.xxl)) {
            if (photos.isEmpty()) item { EmptyState("No photos yet", "Front, side and back photos taken now and then show how your body changes.", "Add photo") { adding = true } }
            else {
                item {
                    Column(Modifier.padding(horizontal = Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                        Choice(listOf<Pose?>(null) + Pose.entries, filter, { it?.label ?: "All" }) { filter = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                            MainButton("Add photo", { adding = true }, Modifier.weight(1f))
                            if (photos.size >= 2) QuietButton("Compare", { model.open(Screen.PhotoCompare(filter)) })
                        }
                    }
                }
                if (days.isEmpty()) item { Text("No ${filter?.label?.lowercase()} photos yet.", Modifier.padding(Space.l), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                for (day in days) {
                    item(key = "day-${day.day}") {
                        Column(Modifier.padding(start = Space.l, end = Space.l, top = Space.xl, bottom = Space.s)) {
                            SectionTitle(prettyDay(day.day))
                            val quiet = listOfNotNull(agoText(day.day, today), weighIns[day.day]?.let { model.weightText(it) }).joinToString(" · ")
                            Text(quiet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(day.photos.chunked(3), key = { "row-${it.first().name}" }) { row ->
                        Row(Modifier.padding(horizontal = Space.l).padding(bottom = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            for (photo in row) Column(Modifier.weight(1f)) {
                                PhotoThumb(
                                    model, photo, 360,
                                    // Before the clip, so the photo travelling in from the card has the thumbnail's corners.
                                    Modifier.fillMaxWidth().aspectRatio(3f / 4f).morphs("photo", photo.name, scaled = true).clip(SmallShape)
                                        .semantics { contentDescription = "${photo.pose.label} photo, ${prettyDay(photo.day)}"; role = Role.Button }
                                        .pressable { model.open(Screen.Photo(photo.name, filter)) },
                                )
                                Text(photo.pose.label, Modifier.padding(top = Space.xs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
fun PhotoScreen(model: AppModel, name: String, pose: Pose?) {
    val all by model.photos.collectAsState()
    val list = remember(all, pose) { all.filter { pose == null || it.pose == pose } }
    val weighIns = model.weighIns()
    val context = LocalContext.current
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
    val view = LocalView.current
    DisposableEffect(Unit) {
        val controller = (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
        val was = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (controller != null && was != null) controller.isAppearanceLightStatusBars = was }
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
                    PhotoFull(model, shown, "${shown.pose.label} photo, ${prettyDay(shown.day)}", Modifier.fillMaxSize())
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
                Text("${prettyDay(photo.day)} · ${photo.pose.label}", color = PHOTO_INK, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            pickDateTime(context, photo.at) { at -> model.changePhoto(photo, at, photo.pose) { moved -> follow = moved.name } }
        }
        SheetRow("Change pose", CleanIcons.Person, value = photo.pose.label) { menu = 2 }
        SheetRow("Share", CleanIcons.Share) {
            menu = 0
            if (id != null) scope.launch {
                val bytes = withContext(Dispatchers.IO) { PhotoFiles.file(context, id, photo.name).readBytes() }
                shareFile(context, "Progress ${photo.day} ${photo.pose.label}.jpg", bytes, "image/jpeg")
            }
        }
        SheetGap()
        SheetRow("Delete", CleanIcons.Delete, danger = true) { menu = 0; model.deletePhoto(photo) }
    }
    if (menu == 2 && photo != null) Sheet({ menu = 0 }, "Pose") {
        for (p in Pose.entries) SheetRow(p.label, value = if (p == photo.pose) "Current" else "") {
            menu = 0
            // Out of a filtered list it goes; the pager then shows the photo that took its place.
            model.changePhoto(photo, photo.at, p) { moved -> if (pose == null || moved.pose == pose) follow = moved.name }
        }
    }
}

// --- Compare ---

@Composable
fun PhotoCompareScreen(model: AppModel, asked: Pose?) {
    val photos by model.photos.collectAsState()
    val weighIns = model.weighIns()
    val context = LocalContext.current
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
                    val bytes = withContext(Dispatchers.IO) { PhotoFiles.pair(context, id, left, right, prettyDay(left.day), prettyDay(right.day), middle) }
                    if (bytes == null) model.message = "Could not make the picture" else shareFile(context, "Progress ${left.day} to ${right.day}.jpg", bytes, "image/jpeg")
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
                .clickable(onClickLabel = "Open full screen") { model.open(Screen.Photo(photo.name, pose)) }
                .swipeSteps({ if (hasOlder) stepOlder() }, { if (hasNewer) stepNewer() }),
        ) {
            Box(Modifier.fillMaxSize().zoomed(zoom)) {
                PhotoFull(model, photo, "${photo.pose.label} photo, ${prettyDay(photo.day)}", Modifier.fillMaxSize())
            }
        }
        Text(prettyDay(photo.day), Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = stepOlder, enabled = hasOlder) { Icon(CleanIcons.Back, "Older photo") }
            IconButton(onClick = stepNewer, enabled = hasNewer) { Icon(CleanIcons.Forward, "Newer photo") }
        }
    }
}
