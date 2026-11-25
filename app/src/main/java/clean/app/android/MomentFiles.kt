package clean.app.android

import clean.app.*
import clean.core.time.*
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import clean.core.Moment
import clean.core.groupMoments
import clean.core.momentOf
import clean.core.momentStats
import clean.core.parseExifDate
import clean.core.parseMoment
import clean.core.newestMomentsFirst
import java.io.File
import java.io.InputStream
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Moments: the pictures on Profile's Snapshots tile and its page. One JPEG per picture under `filesDir/moments/<profileId>/`, named for the
 * moment it was taken and a short id (`20261012-093000_0a1b2c3d.jpg`, see `clean.core.Moment`); the name is the whole record. A picture is
 * turned upright, scaled to at most 2048 px on its longer side and written without EXIF, so no location is kept. Taken from the system photo
 * picker only (no permission). The folder is in the backup zip and goes with Delete everything. Rules: docs/data-format.md, "Moments".
 */
object MomentFiles {
    private const val LONGEST = 2048
    private const val QUALITY = 88
    private const val INCOMING = "incoming.tmp"

    private fun folder(context: Context, profileId: Long) = File(File(context.filesDir, "moments"), profileId.toString())

    fun file(context: Context, profileId: Long, name: String) = File(folder(context, profileId), name)

    /** The profile's moments, newest first; anything else in the folder is left alone and not listed. Blocking. */
    fun list(context: Context, profileId: Long): List<Moment> = newestMomentsFirst(folder(context, profileId).list().orEmpty().mapNotNull(::parseMoment))

    fun deleteAll(context: Context, profileId: Long) {
        folder(context, profileId).deleteRecursively()
    }

    /** Removed moments wait here, in the private cache, until their Undo has passed; one folder for each removal. */
    private fun aside(context: Context, batch: String) = File(File(context.cacheDir, "moment-removed"), batch)

    /** Files the picture [open] reads as a moment, dated by its EXIF `DateTimeOriginal` or else now. Null when it cannot be read as a picture. Blocking. */
    fun add(context: Context, profileId: Long, open: () -> InputStream?): Moment? {
        val exif = open()?.use { runCatching { ExifInterface(it) }.getOrNull() }
        val turn = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) ?: ExifInterface.ORIENTATION_NORMAL
        val taken = parseExifDate(exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)) ?: LocalDateTime.now()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = PhotoFiles.sampleFor(bounds.outWidth, bounds.outHeight, LONGEST) }
        val decoded = open()?.use { runCatching { BitmapFactory.decodeStream(it, null, options) }.getOrNull() } ?: return null
        val upright = PhotoFiles.upright(decoded, turn)
        val scale = LONGEST.toFloat() / maxOf(upright.width, upright.height)
        val sized = if (scale < 1f) Bitmap.createScaledBitmap(upright, (upright.width * scale).roundToInt(), (upright.height * scale).roundToInt(), true) else upright
        val dir = folder(context, profileId).apply { mkdirs() }
        val taken0 = dir.list().orEmpty().toSet()
        var moment = momentOf(taken, UUID.randomUUID().toString().take(8))
        while (moment.name in taken0) moment = momentOf(taken, UUID.randomUUID().toString().take(8))
        // Written under a name that is not a moment's, so a half-written file is never listed.
        val temp = File(dir, INCOMING)
        val written = runCatching { temp.outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) } }.getOrDefault(false)
        if (!written || !temp.renameTo(File(dir, moment.name))) {
            temp.delete()
            return null
        }
        return moment
    }

    /** Takes [moments] out of the folder and keeps them aside under [batch]; the folder they wait in, or null when none could be moved. Blocking. */
    fun removeAside(context: Context, profileId: Long, moments: List<Moment>, batch: String): File? {
        val away = aside(context, batch).apply { mkdirs() }
        val moved = moments.count { file(context, profileId, it.name).renameTo(File(away, it.name)) }
        return away.takeIf { moved > 0 }
    }

    /** Puts what was kept in [away] back where it was, and clears it. Blocking. */
    fun putBack(context: Context, profileId: Long, away: File) {
        val dir = folder(context, profileId).apply { mkdirs() }
        away.listFiles().orEmpty().forEach { it.renameTo(File(dir, it.name)) }
        away.deleteRecursively()
    }
}

