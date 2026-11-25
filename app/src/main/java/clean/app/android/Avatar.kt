package clean.app.android

import clean.app.*

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import java.io.File
import java.io.InputStream

/**
 * A profile's own picture, shown in Profile's circle instead of the initial. One square JPEG per profile in `filesDir/avatars/<profileId>.jpg`: the
 * picked picture turned upright, cropped to a centred square and scaled to at most 512 px, written without EXIF. It is in the backup zip
 * (`files/avatars/`), never in a CSV. Rules and layout: docs/data-format.md, "The profile picture".
 */
object AvatarFiles {
    private const val SIDE = 512
    private const val QUALITY = 90

    private fun folder(context: Context) = File(context.filesDir, "avatars")

    fun file(context: Context, profileId: Long) = File(folder(context), "$profileId.jpg")

    /** The picture as a bitmap, or null when the profile has none. Blocking. */
    fun load(context: Context, profileId: Long): Bitmap? =
        file(context, profileId).takeIf { it.isFile }?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }

    fun delete(context: Context, profileId: Long) {
        file(context, profileId).delete()
    }

    /** The side of the centred square cut from a picture [width] by [height], and where it starts: (left, top, side). */
    internal fun squareOf(width: Int, height: Int): Triple<Int, Int, Int> {
        val side = minOf(width, height)
        return Triple((width - side) / 2, (height - side) / 2, side)
    }

    /** Stores the picture [open] reads as the profile's, cropped to a centred square. False when it cannot be read as a picture. Blocking. */
    fun set(context: Context, profileId: Long, open: () -> InputStream?): Boolean {
        val turn = open()?.use { runCatching { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrNull() } ?: ExifInterface.ORIENTATION_NORMAL
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIDE) sample *= 2
        val decoded = open()?.use { runCatching { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }.getOrNull() } ?: return false
        val upright = PhotoFiles.upright(decoded, turn)
        val (left, top, side) = squareOf(upright.width, upright.height)
        val square = Bitmap.createBitmap(upright, left, top, side, side)
        val sized = if (side > SIDE) Bitmap.createScaledBitmap(square, SIDE, SIDE, true) else square
        val dir = folder(context).apply { mkdirs() }
        val temp = File(dir, "incoming.tmp")
        val written = runCatching { temp.outputStream().use { sized.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) } }.getOrDefault(false)
        if (!written || !temp.renameTo(file(context, profileId).also { it.delete() })) {
            temp.delete()
            return false
        }
        return true
    }
}
