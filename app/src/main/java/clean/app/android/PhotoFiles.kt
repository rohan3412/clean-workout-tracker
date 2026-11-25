package clean.app.android

import clean.app.*
import clean.core.time.*
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
import clean.app.data.MeasurementEntity
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
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
    internal fun sampleFor(width: Int, height: Int, px: Int): Int {
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

    internal fun upright(src: Bitmap, orientation: Int): Bitmap {
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

