@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package clean.app.ios

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import clean.app.ioDispatcher
import clean.core.Moment
import clean.core.Photo
import clean.core.Pose
import clean.core.freePhoto
import clean.core.momentOf
import clean.core.newestFirst
import clean.core.newestMomentsFirst
import clean.core.parseExifDate
import clean.core.parseMoment
import clean.core.parsePhoto
import clean.core.time.now
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.jetbrains.skia.TextLine
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSDictionary
import platform.Foundation.NSUUID
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import kotlin.math.roundToInt

private val SAMPLING = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

/** The pictures the app keeps in its own storage on the iPhone: progress photos, moments, the profile's picture and the exercise animations. Same folders, names and sizes as Android's `PhotoFiles`, `MomentFiles`, `AvatarFiles` and `Animations`. */
object IosPictures {
    private const val LONGEST = 2048
    private const val QUALITY = 0.88
    private const val INCOMING = "incoming.tmp"

    // ---- Reading a picture

    /** The EXIF `DateTimeOriginal` of the picture in [bytes], as the text EXIF keeps it (`2026:10:12 09:30:00`), or null. */
    private fun exifText(bytes: ByteArray): String? {
        val data = bytes.toNSData()
        val retained = CFBridgingRetain(data)
        val source = CGImageSourceCreateWithData(retained?.reinterpret(), null)
        if (source == null) { CFRelease(retained); return null }
        try {
            val properties = CGImageSourceCopyPropertiesAtIndex(source, 0u, null) ?: return null
            val dictionary = CFBridgingRelease(properties) as? NSDictionary
            val exif = dictionary?.objectForKey("{Exif}") as? NSDictionary
            return exif?.objectForKey("DateTimeOriginal") as? String
        } finally {
            CFRelease(source)
            CFRelease(retained)
        }
    }

    /** [bytes] as an upright image (UIKit turns it by its EXIF orientation as it draws), scaled so the longer side is at most [longest]; null when it is not a picture. */
    private fun upright(bytes: ByteArray, longest: Int): UIImage? {
        val image = UIImage.imageWithData(bytes.toNSData()) ?: return null
        val (w, h) = image.size.useContents { width to height }
        if (w <= 0.0 || h <= 0.0) return null
        val scale = minOf(1.0, longest.toDouble() / maxOf(w, h))
        return draw(image, (w * scale).roundToInt().toDouble(), (h * scale).roundToInt().toDouble(), 0.0, 0.0, (w * scale).roundToInt().toDouble(), (h * scale).roundToInt().toDouble())
    }

    /** [image] drawn at [dx], [dy] and [dw] by [dh] on a canvas of [w] by [h] points (one pixel each, opaque). */
    private fun draw(image: UIImage, w: Double, h: Double, dx: Double, dy: Double, dw: Double, dh: Double): UIImage {
        val format = UIGraphicsImageRendererFormat.defaultFormat()
        format.scale = 1.0
        format.opaque = true
        return UIGraphicsImageRenderer(size = CGSizeMake(w, h), format = format).imageWithActions { image.drawInRect(CGRectMake(dx, dy, dw, dh)) }
    }

    private fun jpeg(image: UIImage, quality: Double): ByteArray? = UIImageJPEGRepresentation(image, quality)?.toByteArray()

    /** Writes [bytes] into [dir] under [name], by way of a name that is not a picture's, so a half-written file is never listed. */
    private fun place(dir: String, name: String, bytes: ByteArray): Boolean {
        val temp = Fs.join(dir, INCOMING)
        if (!Fs.write(temp, bytes) || !Fs.move(temp, Fs.join(dir, name))) {
            Fs.delete(temp)
            return false
        }
        return true
    }

    // ---- Photos

    private fun photos(profile: Long) = Fs.join(Fs.root, "photos", profile.toString())
    private fun photoAside() = Fs.join(Fs.cache, "photo-removed")

    fun listPhotos(profile: Long): List<Photo> = newestFirst(Fs.list(photos(profile)).mapNotNull(::parsePhoto))
    fun photoPath(profile: Long, name: String): String = Fs.join(photos(profile), name)
    fun photoBytes(profile: Long, name: String): ByteArray? = Fs.read(photoPath(profile, name))

    fun addPhoto(profile: Long, source: String, pose: Pose, exifDate: Boolean): Photo? {
        val bytes = IosHandles.read(source) ?: return null
        val taken = (if (exifDate) parseExifDate(exifText(bytes)) else null) ?: LocalDateTime.now()
        val data = upright(bytes, LONGEST)?.let { jpeg(it, QUALITY) } ?: return null
        val dir = photos(profile).also { Fs.mkdirs(it) }
        val photo = freePhoto(taken, pose, Fs.list(dir).toSet())
        return if (place(dir, photo.name, data)) photo else null
    }

    fun changePhoto(profile: Long, photo: Photo, at: LocalDateTime, pose: Pose): Photo? {
        val dir = photos(profile)
        val target = freePhoto(at, pose, Fs.list(dir).toSet() - photo.name)
        if (target.name == photo.name) return photo
        return target.takeIf { Fs.move(Fs.join(dir, photo.name), Fs.join(dir, it.name)) }
    }

    fun removePhotoAside(profile: Long, photo: Photo): String? {
        val away = photoAside().also { Fs.delete(it); Fs.mkdirs(it) }
        val to = Fs.join(away, "$profile-${photo.name}")
        return to.takeIf { Fs.move(photoPath(profile, photo.name), it) }
    }

    fun restorePhoto(profile: Long, photo: Photo, aside: String): Boolean {
        val dir = photos(profile).also { Fs.mkdirs(it) }
        return Fs.move(aside, Fs.join(dir, freePhoto(photo.at, photo.pose, Fs.list(dir).toSet()).name))
    }

    fun deleteAside(path: String) { Fs.delete(path) }

    // ---- Moments

    private fun moments(profile: Long) = Fs.join(Fs.root, "moments", profile.toString())

    fun listMoments(profile: Long): List<Moment> = newestMomentsFirst(Fs.list(moments(profile)).mapNotNull(::parseMoment))
    fun momentPath(profile: Long, name: String): String = Fs.join(moments(profile), name)

    private fun newId() = NSUUID().UUIDString.lowercase().take(8)

    fun addMoment(profile: Long, source: String): Moment? {
        val bytes = IosHandles.read(source) ?: return null
        val taken = parseExifDate(exifText(bytes)) ?: LocalDateTime.now()
        val data = upright(bytes, LONGEST)?.let { jpeg(it, QUALITY) } ?: return null
        val dir = moments(profile).also { Fs.mkdirs(it) }
        val present = Fs.list(dir).toSet()
        var moment = momentOf(taken, newId())
        while (moment.name in present) moment = momentOf(taken, newId())
        return if (place(dir, moment.name, data)) moment else null
    }

    fun removeMomentsAside(profile: Long, moments: List<Moment>, batch: String): String? {
        val away = Fs.join(Fs.cache, "moment-removed", batch).also { Fs.mkdirs(it) }
        val moved = moments.count { Fs.move(momentPath(profile, it.name), Fs.join(away, it.name)) }
        return away.takeIf { moved > 0 }
    }

    fun restoreMoments(profile: Long, aside: String) {
        val dir = moments(profile).also { Fs.mkdirs(it) }
        for (name in Fs.list(aside)) Fs.move(Fs.join(aside, name), Fs.join(dir, name))
        Fs.delete(aside)
    }

    fun momentBytes(profile: Long, name: String): ByteArray? = Fs.read(momentPath(profile, name))

    fun refile(profile: Long, pic: clean.core.Pic, at: LocalDateTime, pose: Pose?): clean.core.Pic? {
        val from = if (pic.snapshot) momentPath(profile, pic.name) else photoPath(profile, pic.name)
        val target = clean.core.refiled(pic, at, pose, Fs.list(photos(profile)).toSet(), Fs.list(moments(profile)).toSet()) { newId() }
        if (target.name == pic.name) return pic
        val dir = (if (target.snapshot) moments(profile) else photos(profile)).also { Fs.mkdirs(it) }
        return target.takeIf { Fs.move(from, Fs.join(dir, it.name)) }
    }

    // ---- The profile's picture

    private const val SIDE = 512
    private fun avatar(profile: Long) = Fs.join(Fs.root, "avatars", "$profile.jpg")

    fun loadAvatar(profile: Long): ImageBitmap? = Fs.read(avatar(profile))?.let { runCatching { org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }
    fun deleteAvatar(profile: Long) { Fs.delete(avatar(profile)) }

    /** A centred square cut from the picture, at most 512 px, as JPEG. */
    fun setAvatar(profile: Long, source: String): Boolean {
        val bytes = IosHandles.read(source) ?: return false
        val image = UIImage.imageWithData(bytes.toNSData()) ?: return false
        val (w, h) = image.size.useContents { width to height }
        if (w <= 0.0 || h <= 0.0) return false
        val side = minOf(w, h)
        val out = minOf(side, SIDE.toDouble()).roundToInt().toDouble()
        val scale = out / side
        // Drawn so that the middle of the picture fills the square: the overhang of the longer side falls outside it.
        val square = draw(image, out, out, -(w - side) / 2 * scale, -(h - side) / 2 * scale, w * scale, h * scale)
        val data = jpeg(square, 0.90) ?: return false
        val dir = Fs.join(Fs.root, "avatars")
        Fs.mkdirs(dir)
        return place(dir, "$profile.jpg", data)
    }

    fun deleteProfilePictures(profile: Long) {
        Fs.delete(photos(profile)); Fs.delete(moments(profile)); deleteAvatar(profile)
    }

    // ---- Stored pictures on screen

    /** The largest power of two that still leaves the longer side at least [px]. */
    private fun sampleFor(width: Int, height: Int, px: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= px) sample *= 2
        return sample
    }

    /** The picture at most about [px] on its longer side (never smaller than [px] unless the file is), or null. */
    fun decodeStored(path: String, px: Int): ImageBitmap? {
        val bytes = Fs.read(path) ?: return null
        val image = runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
        val sample = sampleFor(image.width, image.height, px)
        if (sample == 1) return image.toComposeImageBitmap()
        val w = image.width / sample
        val h = image.height / sample
        val bitmap = Bitmap().apply { allocN32Pixels(w, h) }
        org.jetbrains.skia.Canvas(bitmap).drawImageRect(image, Rect.makeWH(image.width.toFloat(), image.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()), SAMPLING, null, true)
        return bitmap.asComposeImageBitmap()
    }

    /** Two photos side by side with a line of text under each and [middle] between, as JPEG. Same layout as Android's. */
    fun comparePhotos(profile: Long, left: Photo, right: Photo, leftText: String, rightText: String, middle: List<String>): ByteArray? {
        val cellW = 1080
        val cellH = 1440
        val gap = 24
        val textH = 120 + 64 * middle.size
        fun load(photo: Photo) = Fs.read(photoPath(profile, photo.name))?.let { runCatching { org.jetbrains.skia.Image.makeFromEncoded(it) }.getOrNull() }
        val a = load(left) ?: return null
        val b = load(right) ?: return null
        val surface = Surface.makeRasterN32Premul(2 * cellW + gap, cellH + textH)
        val canvas = surface.canvas
        canvas.clear(0xFF000000.toInt())
        val ink = Paint().apply { color = 0xFFFFFFFF.toInt(); isAntiAlias = true }
        val quiet = Paint().apply { color = 0xB3FFFFFF.toInt(); isAntiAlias = true }
        val inkFont = Font(null, 48f)
        val quietFont = Font(null, 40f)
        for ((i, pair) in listOf(a to leftText, b to rightText).withIndex()) {
            val (image, text) = pair
            val x = i * (cellW + gap)
            val fit = minOf(cellW.toFloat() / image.width, cellH.toFloat() / image.height)
            val w = image.width * fit
            val h = image.height * fit
            canvas.drawImageRect(image, Rect.makeWH(image.width.toFloat(), image.height.toFloat()), Rect.makeLTRB(x + (cellW - w) / 2, (cellH - h) / 2, x + (cellW + w) / 2, (cellH + h) / 2), SAMPLING, null, true)
            val line = TextLine.make(text, inkFont)
            canvas.drawTextLine(line, x + cellW / 2f - line.width / 2, cellH + 72f, ink)
        }
        middle.forEachIndexed { i, text ->
            val line = TextLine.make(text, quietFont)
            canvas.drawTextLine(line, (2 * cellW + gap) / 2f - line.width / 2, cellH + 72f + 64f * (i + 1), quiet)
        }
        return surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 88)?.bytes
    }

    // ---- The exercise animations

    private fun animationsFolder() = Fs.join(Fs.root, "animations")
    fun animationFile(media: String) = Fs.join(animationsFolder(), "$media.gif")
    fun animationCount(): Int = Fs.list(animationsFolder()).size
    fun clearAnimations() { Fs.delete(animationsFolder()) }

    /** Copies out of the zip [handle] names every animation whose id is in [wanted]: entries named `.../<number>-<id>.gif`. How many were kept. */
    fun importAnimations(handle: String, wanted: Set<String>): Int {
        val folder = animationsFolder().also { Fs.mkdirs(it) }
        val path = IosHandles.stage(handle, "animations.zip") ?: throw IllegalArgumentException("That file could not be read")
        val staged = path != handle
        var kept = 0
        try {
            val zip = FileZip(path)
            try {
                for (entry in zip.entries) {
                    val name = entry.name.substringAfterLast('/')
                    val media = name.substringAfter('-', "").removeSuffix(".gif")
                    // Only the file's own name is used, so nothing in the zip can be written outside the folder.
                    if (!entry.isDirectory && name.endsWith(".gif") && media in wanted && media.all { it.isLetterOrDigit() || it == '_' }) {
                        if (Fs.write(Fs.join(folder, "$media.gif"), zip.bytes(entry))) kept++
                    }
                }
            } finally {
                zip.close()
            }
        } finally {
            if (staged) Fs.delete(path)
        }
        return kept
    }

    /** The frames of an animation and how long each shows, in milliseconds. */
    class Frames(val images: List<ImageBitmap>, val millis: List<Int>)

    private fun decodeFrames(media: String): Frames? {
        val bytes = Fs.read(animationFile(media)) ?: return null
        return runCatching {
            val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
            val infos = codec.framesInfo
            val images = ArrayList<ImageBitmap>()
            val millis = ArrayList<Int>()
            for (i in 0 until codec.frameCount) {
                val bitmap = Bitmap().apply { allocPixels(codec.imageInfo) }
                codec.readPixels(bitmap, i)
                images += bitmap.asComposeImageBitmap()
                millis += infos.getOrNull(i)?.duration ?: 100
            }
            if (images.isEmpty()) null else Frames(images, millis)
        }.getOrNull()
    }

    /** A small still of an exercise's animation (its first frame), or null. */
    fun animationStill(media: String): ImageBitmap? {
        val bytes = Fs.read(animationFile(media)) ?: return null
        return runCatching { org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
    }

    @Composable
    fun rememberAnimationPicture(media: String): (@Composable (Modifier) -> Unit)? {
        val frames by produceState<Frames?>(null, media) { value = withContext(ioDispatcher) { decodeFrames(media) } }
        val shown = frames ?: return null
        return remember(shown) {
            @Composable { modifier: Modifier ->
                var index by remember { mutableIntStateOf(0) }
                if (shown.images.size > 1) LaunchedEffect(shown) {
                    while (true) {
                        delay(shown.millis[index].coerceAtLeast(20).toLong())
                        index = (index + 1) % shown.images.size
                    }
                }
                Image(shown.images[index], null, modifier, contentScale = ContentScale.Fit)
            }
        }
    }
}
