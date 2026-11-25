@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalNativeApi::class)

package clean.app.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.asSkiaPath
import androidx.compose.ui.graphics.nativeCanvas
import clean.app.BackupContents
import clean.app.BackupInspected
import clean.app.RestTone
import clean.app.data.CleanDb
import clean.app.data.DB_FILE
import clean.app.data.Repo
import clean.app.data.open
import clean.app.platform.HitRegion
import clean.app.platform.LabelPaint
import clean.app.platform.Platform
import clean.core.AutoBackupFrequency
import clean.core.Moment
import clean.core.Photo
import clean.core.Pose
import clean.core.parseClock
import clean.core.parseRemindDays
import clean.core.REMIND_DEFAULT_TIME
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.IRect
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Region
import platform.Foundation.NSBundle
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UIScreen
import kotlin.experimental.ExperimentalNativeApi

/**
 * [Platform] on the iPhone. Each member does what Android's does with what iOS has; the ones that cannot are no-ops or reduced, and the
 * log (docs/build-log.md, "iOS stage 3") says which and why. A picked file or folder is a handle from [IosHandles]; a file in the app's own
 * storage is its path. Written without a Mac to run it on: compiled only.
 */
class IosPlatform(private val host: IosHost) : Platform {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pickers = IosPickers()
    private val notifications get() = host.notifications

    // ---- Clock and language

    override fun uptimeMillis(): Long = IosFormats.uptimeMillis()
    override val use24HourClock: Boolean get() = IosFormats.use24HourClock()
    override fun formatLocalized(pattern: String, at: LocalDateTime): String = IosFormats.format(pattern, at)
    override fun formatLocalized(pattern: String, day: LocalDate): String = IosFormats.format(pattern, day)
    override fun formatBestPattern(skeleton: String, day: LocalDate): String = IosFormats.bestPattern(skeleton, day)
    override fun weekdayName(day: DayOfWeek, full: Boolean): String = IosFormats.weekdayName(day, full)
    override fun monthName(month: Month, full: Boolean): String = IosFormats.monthName(month, full)
    override fun formatThousands(value: Long): String = IosFormats.thousands(value)
    override fun formatFixed(value: Double, decimals: Int, phoneLocale: Boolean): String = IosFormats.fixed(value, decimals, phoneLocale)

    // ---- The app and the phone

    override val isDebuggable: Boolean get() = kotlin.native.Platform.isDebugBinary
    override val appVersionName: String? get() = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
    override val solidGlassForced: Boolean get() = UIAccessibilityIsReduceTransparencyEnabled()
    override val systemAnimationsOff: Boolean get() = UIAccessibilityIsReduceMotionEnabled()
    override val notificationsPermitted: Boolean get() = notifications.permitted
    override val screenLongSidePx: Int get() = UIScreen.mainScreen.nativeBounds.useContents { maxOf(size.width, size.height).toInt() }

    override fun log(message: String) { println("Clean: $message") }
    override fun readBundledText(name: String): String? = Fs.bundledText(name)
    override fun removeLeftoverFonts() {}

    override fun keepScreenOn(on: Boolean) = onMain { UIApplication.sharedApplication.idleTimerDisabled = on }
    override fun landscape(on: Boolean) = host.setLandscape(on)
    override fun setStatusBarIconsDark(dark: Boolean): Boolean? = host.setIconsDark(dark)

    // The dialogs are drawn by Compose inside the window, and Compose animates them: there is no window animation to choose.
    @Composable override fun BlinkWindow() {}
    @Composable override fun CutWindow() {}

    // ---- The data

    override fun createRepo(): Repo = Repo(CleanDb.open(Fs.join(Fs.root, DB_FILE)))

    override fun refreshWidgets() = host.refreshShortcuts()
    override fun applyAppIcon(theme: String) = host.applyIcon(theme)
    override fun scheduleAutoBackup(frequency: AutoBackupFrequency?) = host.scheduleBackgroundBackup(frequency)
    override suspend fun backupNow(): String? = IosAutoBackup.attempt(appVersionName.orEmpty())

    // ---- Sounds and buzzes

    override fun prepareFeedback() { scope.launch { IosFeedback.prepare() } }
    override fun bell(tone: RestTone) = IosFeedback.bell(tone)
    override fun chime(volume: Float) = IosFeedback.chime(volume)
    override fun haptic(kind: clean.app.Haptic) = IosFeedback.haptic(kind)
    override fun pulse(millis: Long) = IosFeedback.pulse(millis)

    // ---- Notifications

    override fun scheduleRestAlert(end: Long, key: String?, total: Int, next: String?, inWorkoutNotice: Boolean) {
        scope.launch {
            IosFeedback.prepare()
            notifications.scheduleRest(end, key, total, next, notifications.restTone())
        }
    }

    override fun cancelRestAlert() = notifications.cancelRest()
    override fun postWorkoutNotice(name: String, startMillis: Long, text: String?, setDone: Boolean, rest: clean.app.platform.NoticeRest?) = notifications.postWorkout(name, text, setDone)
    override fun cancelWorkoutNotice() = notifications.cancelWorkout()

    override fun syncWorkoutReminder(days: String?, time: String?) =
        notifications.syncReminder(parseRemindDays(days).map { it.isoDayNumber }.toSet(), parseClock(time) ?: REMIND_DEFAULT_TIME)

    override var onRestNotificationAction: ((Boolean) -> Unit)? = null
    override var onSetDoneNotification: (() -> Unit)? = null

    @Composable
    override fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
        val latest by rememberUpdatedState(onResult)
        return remember { { notifications.requestPermission { latest(it) } } }
    }

    // ---- Health: HealthKit needs a paid developer account, so the iPhone app has none of it (Settings hides the section)

    override val hasHealth: Boolean get() = false
    override fun healthAvailable(): Boolean = false
    override suspend fun healthAllowed(): Boolean = false
    override suspend fun healthStepsToday(): Long? = null
    override suspend fun healthApply(request: clean.core.HealthRequest): clean.core.HealthReport = clean.core.HealthReport()
    override suspend fun healthRemoveAll(): clean.core.HealthFailure? = null
    override fun healthLedger(): String = ""
    override fun setHealthLedger(text: String) {}

    @Composable
    override fun rememberHealthPermissionRequest(steps: Boolean, onResult: (Boolean) -> Unit): () -> Unit = remember { { onResult(false) } }

    // ---- Pickers and files

    @Composable
    override fun rememberFileSaver(mimeType: String, onPicked: (String?) -> Unit): (String) -> Unit {
        val latest by rememberUpdatedState(onPicked)
        return remember { { name -> pickers.saveFile(name) { latest(it) } } }
    }

    @Composable
    override fun rememberFilePicker(onPicked: (String?) -> Unit): () -> Unit {
        val latest by rememberUpdatedState(onPicked)
        return remember { { pickers.openFile { latest(it) } } }
    }

    @Composable
    override fun rememberFolderPicker(onPicked: (String?) -> Unit): () -> Unit {
        val latest by rememberUpdatedState(onPicked)
        return remember { { pickers.pickFolder { latest(it) } } }
    }

    @Composable
    override fun rememberPhotoPicker(multiple: Boolean, onPicked: (List<String>) -> Unit): () -> Unit {
        val latest by rememberUpdatedState(onPicked)
        return remember(multiple) { { pickers.pickPhotos(multiple) { latest(it) } } }
    }

    @Composable
    override fun rememberCamera(onShot: (String?) -> Unit): () -> Boolean {
        val latest by rememberUpdatedState(onShot)
        return remember { { pickers.camera { latest(it) } } }
    }

    override fun pickDateTime(initial: LocalDateTime, onPick: (LocalDateTime) -> Unit) {
        pickers.pickDateTime(IosFormats.date(initial), platform.Foundation.NSDate()) { date ->
            val chosen = IosFormats.reading(date)
            // The seconds of the date being changed are kept, as on Android.
            onPick(LocalDateTime(chosen.year, chosen.month, chosen.day, chosen.hour, chosen.minute, initial.second, 0))
        }
    }

    override fun pickTime(hour: Int, minute: Int, onPick: (hour: Int, minute: Int) -> Unit) {
        pickers.pickTime(IosFormats.date(LocalDateTime(2000, 1, 1, hour, minute, 0, 0))) { date ->
            val chosen = IosFormats.reading(date)
            onPick(chosen.hour, chosen.minute)
        }
    }

    override suspend fun readFile(handle: String): ByteArray = withContext(Dispatchers.Default) { IosHandles.read(handle) ?: throw IllegalStateException("That file could not be read") }
    override suspend fun writeFile(handle: String, bytes: ByteArray) {
        withContext(Dispatchers.Default) { if (!IosHandles.write(handle, bytes)) throw IllegalStateException("That file could not be written") }
    }

    override fun folderName(handle: String): String? = IosHandles.folderName(handle)
    override fun releaseFolder(handle: String) {}

    override suspend fun stageFile(handle: String): String = withContext(Dispatchers.Default) {
        IosHandles.stage(handle, "picked.zip") ?: throw IllegalStateException("That file could not be read")
    }

    override fun discardStaged(path: String) { Fs.delete(path) }
    override suspend fun takeStaged(path: String): ByteArray = withContext(Dispatchers.Default) { (Fs.read(path) ?: ByteArray(0)).also { Fs.delete(path) } }

    // ---- Sharing

    override fun shareFile(name: String, bytes: ByteArray, mimeType: String) {
        val path = Fs.join(Fs.tmp, "shared", name.replace(Regex("[^A-Za-z0-9._-]+"), "_"))
        Fs.write(path, bytes)
        pickers.shareItems(listOf(fileUrl(path)))
    }

    override fun shareText(text: String) = pickers.shareItems(listOf(text))
    override fun copyToClipboard(label: String, text: String) = onMain { UIPasteboard.generalPasteboard.string = text }

    override fun encodePng(image: ImageBitmap): ByteArray =
        org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)

    // ---- Backup

    override fun animationsBytes(): Long = IosBackup.animationsBytes()

    override suspend fun writeBackup(handle: String, withAnimations: Boolean) = withContext(Dispatchers.Default) {
        val bytes = IosBackup.build(appVersionName.orEmpty(), withAnimations, clean.core.currentLocalDateTime())
        if (!IosHandles.write(handle, bytes)) throw IllegalStateException("The backup could not be written there")
    }

    override fun isBackup(path: String): Boolean = IosBackup.isBackup(path)
    override fun inspectBackup(path: String): BackupInspected = IosBackup.inspect(path)
    override fun currentContents(): BackupContents = IosBackup.current()
    override fun applyBackup(path: String) = IosBackup.apply(path)
    override fun wipeEverything() = IosBackup.wipe()

    /** There is no process to restart: the root builds a new model, which opens the database afresh (and Room migrates an older file). */
    override fun restartApp() = host.restartModel()

    // ---- Animations

    override fun animationCount(): Int = IosPictures.animationCount()
    override fun clearAnimations() = IosPictures.clearAnimations()
    override suspend fun importAnimations(handle: String, wanted: Set<String>): Int = withContext(Dispatchers.Default) { IosPictures.importAnimations(handle, wanted) }

    @Composable
    override fun rememberAnimationPicture(media: String): (@Composable (Modifier) -> Unit)? = IosPictures.rememberAnimationPicture(media)

    override fun animationStill(media: String): ImageBitmap? = IosPictures.animationStill(media)

    // ---- Pictures

    override fun listPhotos(profile: Long): List<Photo> = IosPictures.listPhotos(profile)
    override fun photoPath(profile: Long, name: String): String = IosPictures.photoPath(profile, name)
    override fun addPhoto(profile: Long, source: String, pose: Pose, exifDate: Boolean): Photo? = IosPictures.addPhoto(profile, source, pose, exifDate)
    override fun changePhoto(profile: Long, photo: Photo, at: LocalDateTime, pose: Pose): Photo? = IosPictures.changePhoto(profile, photo, at, pose)
    override fun removePhotoAside(profile: Long, photo: Photo): String? = IosPictures.removePhotoAside(profile, photo)
    override fun restorePhoto(profile: Long, photo: Photo, aside: String): Boolean = IosPictures.restorePhoto(profile, photo, aside)
    override fun discardAside(path: String) = IosPictures.deleteAside(path)
    override fun photoBytes(profile: Long, name: String): ByteArray? = IosPictures.photoBytes(profile, name)
    override fun comparePhotos(profile: Long, left: Photo, right: Photo, leftText: String, rightText: String, middle: List<String>): ByteArray? =
        IosPictures.comparePhotos(profile, left, right, leftText, rightText, middle)

    override fun listMoments(profile: Long): List<Moment> = IosPictures.listMoments(profile)
    override fun momentPath(profile: Long, name: String): String = IosPictures.momentPath(profile, name)
    override fun addMoment(profile: Long, source: String): Moment? = IosPictures.addMoment(profile, source)
    override fun removeMomentsAside(profile: Long, moments: List<Moment>, batch: String): String? = IosPictures.removeMomentsAside(profile, moments, batch)
    override fun restoreMoments(profile: Long, aside: String) = IosPictures.restoreMoments(profile, aside)
    override fun momentBytes(profile: Long, name: String): ByteArray? = IosPictures.momentBytes(profile, name)
    override fun refile(profile: Long, pic: clean.core.Pic, at: LocalDateTime, pose: Pose?): clean.core.Pic? = IosPictures.refile(profile, pic, at, pose)

    override fun decodeStored(path: String, px: Int, light: Boolean): ImageBitmap? = IosPictures.decodeStored(path, px)

    // The phone's own lock: Face ID or Touch ID, with the passcode as the fallback (`deviceOwnerAuthentication`).
    override val canAuthenticate: Boolean get() = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, null)
    override fun authenticate(reason: String, onResult: (Boolean) -> Unit) {
        LAContext().evaluatePolicy(LAPolicyDeviceOwnerAuthentication, reason) { ok, _ -> onMain { onResult(ok) } }
    }
    override fun loadAvatar(profile: Long): ImageBitmap? = IosPictures.loadAvatar(profile)
    override fun setAvatar(profile: Long, source: String): Boolean = IosPictures.setAvatar(profile, source)
    override fun deleteAvatar(profile: Long) = IosPictures.deleteAvatar(profile)
    override fun deleteProfilePictures(profile: Long) = IosPictures.deleteProfilePictures(profile)

    // ---- Drawing

    override fun labelPaint(argb: Int, textSizePx: Float, bold: Boolean): LabelPaint {
        val paint = Paint().apply { color = argb; isAntiAlias = true }
        val font = Font(FontMgr.default.matchFamilyStyle(null, if (bold) FontStyle.BOLD else FontStyle.NORMAL), textSizePx)
        return object : LabelPaint {
            override val textSize: Float get() = font.size
            override var alpha: Int
                get() = (paint.color ushr 24)
                set(value) { paint.color = (paint.color and 0x00FFFFFF) or (value.coerceIn(0, 255) shl 24) }
            override fun draw(canvas: Canvas, text: String, x: Float, y: Float) { canvas.nativeCanvas.drawString(text, x, y, font, paint) }
        }
    }

    override fun hitRegion(path: Path, width: Int, height: Int): HitRegion {
        val clip = Region().apply { setRect(IRect.makeWH(width, height)) }
        val region = Region().apply { setPath(path.asSkiaPath(), clip) }
        return HitRegion { x, y -> region.contains(x, y) }
    }
}
