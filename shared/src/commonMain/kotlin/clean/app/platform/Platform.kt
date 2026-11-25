package clean.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import clean.app.BackupContents
import clean.app.BackupInspected
import clean.app.RestTone
import clean.app.data.Repo
import clean.core.AutoBackupFrequency
import clean.core.Moment
import clean.core.Photo
import clean.core.Pose
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month

/**
 * Everything the app's screens and model need from the phone that common Kotlin cannot do: files, pickers, the share sheet, sounds,
 * notifications and alarms, Health Connect, widgets, the phone's language and clock. The types are plain Kotlin (strings, bytes,
 * kotlinx-datetime, Compose images and lambdas); a file or folder the user picked is an opaque `String` handle that only the
 * platform understands, and so is the path of a file kept in the app's own storage. Android's is `clean.app.android.AndroidPlatform`.
 *
 * Screens get it from [LocalPlatform]; [clean.app.AppModel] is handed it for the code that is not in a composition.
 */
interface Platform {
    // ---- The phone's clock and language

    /** A clock that only runs forward (milliseconds since the phone started), for gaps between touches. */
    fun uptimeMillis(): Long

    /** Whether the phone shows the time as 24 hours. */
    val use24HourClock: Boolean

    /** [at] in the phone's language, in the java.time pattern letters (`EEE d MMM, HH:mm`). */
    fun formatLocalized(pattern: String, at: LocalDateTime): String
    fun formatLocalized(pattern: String, day: LocalDate): String

    /** [day] in the phone's own best pattern for the [skeleton] (`EEEdMMM`), as the phone's locale writes it. */
    fun formatBestPattern(skeleton: String, day: LocalDate): String

    fun weekdayName(day: DayOfWeek, full: Boolean): String
    fun monthName(month: Month, full: Boolean): String

    /** [value] with the phone's thousands separator (`12,345`). */
    fun formatThousands(value: Long): String

    /** [value] to [decimals] places as the phone's number formatting writes it (`%.2f`); [phoneLocale] false gives the plain dot of the invariant locale. */
    fun formatFixed(value: Double, decimals: Int, phoneLocale: Boolean = true): String

    // ---- The app and the phone it runs on

    /** Whether this is a debug build. */
    val isDebuggable: Boolean

    /** The app's version name, as the next "What's new" compares it. */
    val appVersionName: String?

    /** Whether glass has to be drawn solid: the phone cannot blur, or has its high-contrast text setting on. Read when the app starts. */
    val solidGlassForced: Boolean

    /** Whether the phone is short of memory or has its battery saver on, so the app may offer the Balanced performance tier once. Read when the app starts. */
    val lowPowerHint: Boolean get() = false

    /** The phone's own "Remove animations" setting; read again each time the app comes to the front. */
    val systemAnimationsOff: Boolean

    /** Whether the notification permission is held (or not needed, before Android 13). */
    val notificationsPermitted: Boolean

    /** The longer side of the screen in pixels, the size a photo is decoded to for full-screen viewing. */
    val screenLongSidePx: Int

    /** A line for the developer log (debug builds only show it). */
    fun log(message: String)

    /** A text file bundled with the app (`exercise_info.json`, `changes.md`), or null when it is missing. Blocking. */
    fun readBundledText(name: String): String?

    /** A font file imported by an older version, left in the app's folder, is deleted. */
    fun removeLeftoverFonts()

    /** Keeps the screen on while on (the running workout). */
    fun keepScreenOn(on: Boolean)

    /** Turns the screen onto its side for a full-screen chart while on, upright again when off. */
    fun landscape(on: Boolean)

    /** Makes the status bar's icons light or dark; returns what they were (null when they cannot be read). */
    fun setStatusBarIconsDark(dark: Boolean): Boolean?

    /** Inside a dialog's content: its window blinks in and out in place, as every page does (when motion is on). */
    @Composable fun BlinkWindow()

    /** Inside a dialog's content: its window has no animation of its own, for one that moves its content itself. */
    @Composable fun CutWindow()

    // ---- The data

    /** The repository over the app's database, opened once and shared. */
    fun createRepo(): Repo

    /** Redraws the home-screen widgets and brings the launcher shortcuts up to date. */
    fun refreshWidgets()

    /** Makes the launcher icon the one of the [theme]. */
    fun applyAppIcon(theme: String)

    /** Starts, changes or stops the scheduled backup ([frequency] null is off). */
    fun scheduleAutoBackup(frequency: AutoBackupFrequency?)

    /** Runs the scheduled backup into the chosen folder at once and records how it went (Round 36). Null when it worked, else the sentence that says why not. */
    suspend fun backupNow(): String?

    // ---- Sounds and buzzes

    /** Loads the sounds once; asked again it does nothing. */
    fun prepareFeedback()
    fun bell(tone: RestTone)
    fun chime(volume: Float = 0.8f)
    /** One buzz of the app's vocabulary ([clean.app.Haptic]): the phone's own effect for it (Android's `HapticFeedbackConstants` with a fall-back on older phones, the iPhone's Taptic generators). Screens go through `rememberHaptic`, which also honours Haptic ticks and doubles nothing. */
    fun haptic(kind: clean.app.Haptic)
    fun pulse(millis: Long)

    // ---- Notifications and alarms

    /** Sets the alarms for a rest ending at [end] (clock milliseconds) and shows its countdown, for when the app is behind; with [inWorkoutNotice] the workout notification shows the countdown, so no notification of its own. */
    fun scheduleRestAlert(end: Long, key: String?, total: Int, next: String?, inWorkoutNotice: Boolean)
    fun cancelRestAlert()

    /** The notification of the workout in progress; with [rest] it shows that rest (its countdown, the next set, "+15 s" and "Skip") in place of the separate rest notification. */
    fun postWorkoutNotice(name: String, startMillis: Long, text: String?, setDone: Boolean, rest: NoticeRest? = null)
    fun cancelWorkoutNotice()

    /** The alarm of the workout-day reminder, from the stored [days] and [time]. */
    fun syncWorkoutReminder(days: String?, time: String?)

    /** The running app's own way of changing its rest from the notification's buttons (true adds [REST_NOTIFICATION_MORE] seconds, false skips). */
    var onRestNotificationAction: ((Boolean) -> Unit)?

    /** The running app's own way of ticking the next set from the workout notification. */
    var onSetDoneNotification: (() -> Unit)?

    /** Asks for the notification permission; the callback says whether it was given. */
    @Composable fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit

    // ---- Health Connect

    /** Whether Settings shows the Health section at all; the iPhone app has none (HealthKit needs a paid developer account). */
    val hasHealth: Boolean get() = true

    fun healthAvailable(): Boolean
    suspend fun healthAllowed(): Boolean
    suspend fun healthStepsToday(): Long?
    /**
     * Does what [request] asks of Health Connect (writes in batches, removes by id, the grant checked once) and says what went through.
     * Never throws except for cancellation; a failure is in the report.
     */
    suspend fun healthApply(request: clean.core.HealthRequest): clean.core.HealthReport

    /** Takes away every record Clean wrote, any profile (a delete by time range, which Health Connect limits to the app's own data); null when it went through. */
    suspend fun healthRemoveAll(): clean.core.HealthFailure?

    /** What Clean has sent to Health Connect, as `encodeLedger` wrote it: kept on the phone, outside the database, so a restore does not change it. */
    fun healthLedger(): String
    fun setHealthLedger(text: String)

    /** Asks for the writing of workouts and weights, or, with [steps], the reading of steps; the callback says whether it was given. */
    @Composable fun rememberHealthPermissionRequest(steps: Boolean, onResult: (Boolean) -> Unit): () -> Unit

    // ---- Pickers and the files they name

    /** The phone's file saver; the returned function takes the suggested name, the callback the handle (null when cancelled). */
    @Composable fun rememberFileSaver(mimeType: String, onPicked: (String?) -> Unit): (String) -> Unit

    /** The phone's file picker for any file. */
    @Composable fun rememberFilePicker(onPicked: (String?) -> Unit): () -> Unit

    /** The phone's folder picker; the platform keeps the access to the folder across restarts. */
    @Composable fun rememberFolderPicker(onPicked: (String?) -> Unit): () -> Unit

    /** The phone's photo picker, one or several pictures; the callback gets their handles. */
    @Composable fun rememberPhotoPicker(multiple: Boolean, onPicked: (List<String>) -> Unit): () -> Unit

    /** The camera; the returned function starts it and says false when there is no camera app, the callback gets the handle of the picture taken (null when cancelled). */
    @Composable fun rememberCamera(onShot: (String?) -> Unit): () -> Boolean

    /** The phone's date picker and then its time picker; [onPick] gets the choice, with [initial]'s seconds. Not in the future. */
    fun pickDateTime(initial: LocalDateTime, onPick: (LocalDateTime) -> Unit)

    /** The phone's time picker. */
    fun pickTime(hour: Int, minute: Int, onPick: (hour: Int, minute: Int) -> Unit)

    suspend fun readFile(handle: String): ByteArray
    suspend fun writeFile(handle: String, bytes: ByteArray)

    /** The name of the folder [handle] names (null when it is gone), and the end of the app's access to one it no longer uses. */
    fun folderName(handle: String): String?
    fun releaseFolder(handle: String)

    /** Copies a picked file into the app's cache and returns the path of the copy. */
    suspend fun stageFile(handle: String): String
    fun discardStaged(path: String)

    /** The bytes of a staged file; the copy is deleted. */
    suspend fun takeStaged(path: String): ByteArray

    // ---- Sharing

    /** Hands a file to the share sheet. */
    fun shareFile(name: String, bytes: ByteArray, mimeType: String)
    fun shareText(text: String)
    fun copyToClipboard(label: String, text: String)
    fun encodePng(image: ImageBitmap): ByteArray

    // ---- Backup (Settings, Data)

    /** The size of the imported animations, which are most of a big backup. */
    fun animationsBytes(): Long
    suspend fun writeBackup(handle: String, withAnimations: Boolean)
    fun isBackup(path: String): Boolean

    /** What the backup at [path] holds; throws with a sentence fit to show when it cannot be restored. Blocking. */
    fun inspectBackup(path: String): BackupInspected

    /** What the phone holds now. Blocking. */
    fun currentContents(): BackupContents

    /** Replaces the phone's database and files with the backup's; the app has to be [restartApp]ed after. Blocking. */
    fun applyBackup(path: String)

    /** Takes everything the app keeps off the phone; then [restartApp]. Blocking. */
    fun wipeEverything()

    /** Starts the app again so everything is read afresh: on Android a new process (it does not return), on iOS a new model under the same screen. */
    fun restartApp()

    // ---- The exercise animations the user imported

    fun animationCount(): Int
    fun clearAnimations()

    /** Copies out of the zip [handle] names every animation whose id is in [wanted]; how many were kept, or throws when it is not a zip. */
    suspend fun importAnimations(handle: String, wanted: Set<String>): Int

    /** The moving picture of an exercise, as something to draw, or null while it loads or when there is none. */
    @Composable fun rememberAnimationPicture(media: String): (@Composable (Modifier) -> Unit)?

    /** A small still of an exercise's animation (its first frame), or null. Blocking. */
    fun animationStill(media: String): ImageBitmap?

    // ---- Pictures kept in the app's storage (all blocking: call them off the main thread)

    fun listPhotos(profile: Long): List<Photo>
    fun photoPath(profile: Long, name: String): String
    fun addPhoto(profile: Long, source: String, pose: Pose, exifDate: Boolean): Photo?
    fun changePhoto(profile: Long, photo: Photo, at: LocalDateTime, pose: Pose): Photo?

    /** Takes the photo out of the folder into a place to be thrown away after the Undo; its path, or null. */
    fun removePhotoAside(profile: Long, photo: Photo): String?
    fun restorePhoto(profile: Long, photo: Photo, aside: String): Boolean
    fun discardAside(path: String)
    fun photoBytes(profile: Long, name: String): ByteArray?

    /** Two photos side by side with a line of text under each and [middle] between, as JPEG bytes. */
    fun comparePhotos(profile: Long, left: Photo, right: Photo, leftText: String, rightText: String, middle: List<String>): ByteArray?

    fun listMoments(profile: Long): List<Moment>
    fun momentPath(profile: Long, name: String): String
    fun addMoment(profile: Long, source: String): Moment?
    fun removeMomentsAside(profile: Long, moments: List<Moment>, batch: String): String?
    fun restoreMoments(profile: Long, aside: String)
    fun momentBytes(profile: Long, name: String): ByteArray?

    /**
     * Gives [pic] the moment [at] and the pose [pose] (null: a snapshot) by renaming its file, from one folder to the other when the kind changes (Round 39); the picture as it is
     * now, or null when it could not be moved. A taken name gets a second (a photo) or a new id (a snapshot) until it is free.
     */
    fun refile(profile: Long, pic: clean.core.Pic, at: LocalDateTime, pose: Pose?): clean.core.Pic?

    /** Whether the phone has a lock the app can ask: a fingerprint or face, or a PIN, pattern or password. False on a phone with none set. */
    val canAuthenticate: Boolean

    /**
     * Asks the phone's own lock (biometric, or the PIN, pattern or password as the fallback) with [reason] as its prompt, and calls [onResult] on the main thread with
     * whether the person got through; false for a cancel or a failure. Round 31: the "Lock photos" switch.
     */
    fun authenticate(reason: String, onResult: (Boolean) -> Unit)

    /** A stored picture cut to about [px] on its longer side; [light] keeps two bytes a dot (thumbnails), otherwise four. Null when it cannot be read. */
    fun decodeStored(path: String, px: Int, light: Boolean): ImageBitmap?

    /** The profile's picture (a square, at most 512 px), or null when it has none. */
    fun loadAvatar(profile: Long): ImageBitmap?
    fun setAvatar(profile: Long, source: String): Boolean
    fun deleteAvatar(profile: Long)

    /** Takes every picture of the profile (photos, moments, avatar) off the phone. */
    fun deleteProfilePictures(profile: Long)

    // ---- Drawing the phone does for us

    /** Text drawn straight on a chart's canvas, the way the chart has always drawn its labels. */
    fun labelPaint(argb: Int, textSizePx: Float, bold: Boolean): LabelPaint

    /** Which whole pixels of a [width] x [height] area [path] covers; for picking a muscle on the body figure. */
    fun hitRegion(path: Path, width: Int, height: Int): HitRegion
}

/** A paint for [Platform.labelPaint]: the text size in pixels, its opacity (0 to 255) and the drawing of one line. */
interface LabelPaint {
    val textSize: Float
    var alpha: Int
    fun draw(canvas: Canvas, text: String, x: Float, y: Float)
}

/** Whether a whole pixel is inside a shape. */
fun interface HitRegion {
    fun contains(x: Int, y: Int): Boolean
}

/** Seconds the rest notification's "+15 s" adds. */
const val REST_NOTIFICATION_MORE = 15

val LocalPlatform = staticCompositionLocalOf<Platform> { error("No Platform provided: the host (MainActivity) provides LocalPlatform") }

/** A rest running while the workout notification is shown: when it ends (clock milliseconds), where it is stored, its length in seconds and the next set in words. */
class NoticeRest(val end: Long, val key: String?, val total: Int, val next: String?)
