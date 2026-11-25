package clean.app.android

import clean.app.Haptic
import clean.app.RestAlert
import clean.app.WorkoutNotice
import clean.app.WorkoutReminder
import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Region
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import androidx.health.connect.client.PermissionController
import clean.app.BackupContents
import clean.app.BackupInspected
import clean.app.ExerciseInfos
import clean.app.Motion
import clean.app.R
import clean.app.RestTone
import clean.app.data.CleanDb
import clean.app.data.open
import clean.app.data.Repo
import clean.app.platform.HitRegion
import clean.app.platform.LabelPaint
import clean.app.platform.Platform
import clean.core.AutoBackupFrequency
import clean.core.Moment
import clean.core.Photo
import clean.core.Pose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaMonth
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * [Platform] on Android: each method is the Android code that used to sit in the screens, moved here as it was (the objects in this
 * package: `PhotoFiles`, `Backup`, `RestAlert` and the rest, still take a `Context`). A picked file or folder is a `content://` address
 * as text; a file in the app's own folders is its path.
 */
class AndroidPlatform(private val app: Application) : Platform {
    /** The running activity, for what needs a window (the pickers, the screen flags); set by [MainActivity]. */
    @Volatile var activity: Activity? = null

    // ---- Clock and language

    override fun uptimeMillis(): Long = SystemClock.uptimeMillis()
    override val use24HourClock: Boolean get() = DateFormat.is24HourFormat(app)

    private fun formatter(pattern: String): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault(Locale.Category.FORMAT))
    override fun formatLocalized(pattern: String, at: LocalDateTime): String = at.toJavaLocalDateTime().format(formatter(pattern))
    override fun formatLocalized(pattern: String, day: LocalDate): String = day.toJavaLocalDate().format(formatter(pattern))

    override fun formatBestPattern(skeleton: String, day: LocalDate): String {
        val locale = Locale.getDefault()
        return day.toJavaLocalDate().format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale))
    }

    override fun weekdayName(day: DayOfWeek, full: Boolean): String = day.toJavaDayOfWeek().getDisplayName(if (full) TextStyle.FULL else TextStyle.SHORT, Locale.getDefault())
    override fun monthName(month: Month, full: Boolean): String = month.toJavaMonth().getDisplayName(if (full) TextStyle.FULL else TextStyle.SHORT, Locale.getDefault())
    override fun formatThousands(value: Long): String = String.format("%,d", value)
    override fun formatFixed(value: Double, decimals: Int, phoneLocale: Boolean): String =
        if (phoneLocale) String.format("%.${decimals}f", value) else String.format(Locale.ROOT, "%.${decimals}f", value)

    // ---- The app and the phone

    override val isDebuggable: Boolean get() = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    override val appVersionName: String? get() = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull()
    override val solidGlassForced: Boolean
        get() = Build.VERSION.SDK_INT < 31 ||
            try { android.provider.Settings.Secure.getInt(app.contentResolver, "high_text_contrast_enabled", 0) == 1 } catch (e: Exception) { false }
    override val lowPowerHint: Boolean
        get() = runCatching {
            app.getSystemService(android.app.ActivityManager::class.java)?.isLowRamDevice == true || app.getSystemService(android.os.PowerManager::class.java)?.isPowerSaveMode == true
        }.getOrDefault(false)
    override val systemAnimationsOff: Boolean
        get() = android.provider.Settings.Global.getFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    override val notificationsPermitted: Boolean
        get() = Build.VERSION.SDK_INT < 33 || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    override val screenLongSidePx: Int get() = app.resources.displayMetrics.let { maxOf(it.widthPixels, it.heightPixels) }

    override fun log(message: String) { Log.i("Clean", message) }
    override fun readBundledText(name: String): String? = runCatching { app.assets.open(name).bufferedReader().use { it.readText() } }.getOrNull()
    override fun removeLeftoverFonts() { app.filesDir.listFiles { f -> f.name.startsWith("font-") }?.forEach { it.delete() } }

    override fun keepScreenOn(on: Boolean) { activity?.window?.decorView?.keepScreenOn = on }
    override fun landscape(on: Boolean) {
        activity?.requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun setStatusBarIconsDark(dark: Boolean): Boolean? {
        val window = activity?.window ?: return null
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val was = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = dark
        return was
    }

    @Composable
    override fun BlinkWindow() {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setWindowAnimations(windowAnimation()) }
    }

    @Composable
    override fun CutWindow() {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setWindowAnimations(R.style.CutWindow) }
    }

    /** A window's own animation (a dialog, sheet or the phone's picker): the blink in res/anim, or none. */
    private fun windowAnimation(): Int = if (Motion.windowBlinks()) R.style.BlinkWindow else R.style.CutWindow

    // ---- The data

    override fun createRepo(): Repo = Repo(CleanDb.open(app))
    override fun refreshWidgets() = clean.app.refreshWidgets(app)
    // A switched icon changes the launcher activity the shortcuts belong to, so they are published again.
    override fun applyAppIcon(theme: String) { if (AppIcon.applyIcon(app, theme)) clean.app.refreshWidgets(app) }
    override fun scheduleAutoBackup(frequency: AutoBackupFrequency?) = AutoBackup.schedule(app, frequency)
    override suspend fun backupNow(): String? = AutoBackup.attempt(app)

    // ---- Sounds and buzzes

    override fun prepareFeedback() = Feedback.init(app)
    override fun bell(tone: RestTone) = Feedback.bell(tone)
    override fun chime(volume: Float) = Feedback.chime(volume)
    override fun haptic(kind: Haptic) {
        activity?.window?.decorView?.performHapticFeedback(AndroidHaptics.constantFor(kind, Build.VERSION.SDK_INT))
    }
    override fun pulse(millis: Long) = Feedback.pulse(millis)

    // ---- Notifications and alarms

    override fun scheduleRestAlert(end: Long, key: String?, total: Int, next: String?, inWorkoutNotice: Boolean) = RestAlert.schedule(app, end, key, total, next, inWorkoutNotice)
    override fun cancelRestAlert() = RestAlert.cancel(app)
    override fun postWorkoutNotice(name: String, startMillis: Long, text: String?, setDone: Boolean, rest: clean.app.platform.NoticeRest?) = WorkoutNotice.post(app, name, startMillis, text, setDone, rest)
    override fun cancelWorkoutNotice() = WorkoutNotice.cancel(app)
    override fun syncWorkoutReminder(days: String?, time: String?) = WorkoutReminder.sync(app, days, time)
    override var onRestNotificationAction: ((Boolean) -> Unit)?
        get() = RestAlert.onAction
        set(value) { RestAlert.onAction = value }
    override var onSetDoneNotification: (() -> Unit)?
        get() = WorkoutNotice.onSetDone
        set(value) { WorkoutNotice.onSetDone = value }

    @Composable
    override fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(it) }
        return { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }

    // ---- Health Connect

    override fun healthAvailable(): Boolean = Health.available(app)
    override suspend fun healthAllowed(): Boolean = Health.allowed(app)
    override suspend fun healthStepsToday(): Long? = Health.stepsToday(app)
    override suspend fun healthApply(request: clean.core.HealthRequest): clean.core.HealthReport = Health.apply(app, request)
    override suspend fun healthRemoveAll(): clean.core.HealthFailure? = Health.removeAll(app)
    private val healthPrefs get() = app.getSharedPreferences("health", Context.MODE_PRIVATE)
    override fun healthLedger(): String = healthPrefs.getString("ledger", "").orEmpty()
    override fun setHealthLedger(text: String) { healthPrefs.edit().putString("ledger", text).apply() }

    @Composable
    override fun rememberHealthPermissionRequest(steps: Boolean, onResult: (Boolean) -> Unit): () -> Unit {
        val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            onResult(if (steps) Health.STEPS_PERMISSION in granted else granted.any { it in Health.PERMISSIONS })
        }
        return { launcher.launch(if (steps) setOf(Health.STEPS_PERMISSION) else Health.PERMISSIONS) }
    }

    // ---- Pickers and files

    @Composable
    override fun rememberFileSaver(mimeType: String, onPicked: (String?) -> Unit): (String) -> Unit {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { onPicked(it?.toString()) }
        return { name -> launcher.launch(name) }
    }

    @Composable
    override fun rememberFilePicker(onPicked: (String?) -> Unit): () -> Unit {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { onPicked(it?.toString()) }
        return { launcher.launch(arrayOf("*/*")) }
    }

    @Composable
    override fun rememberFolderPicker(onPicked: (String?) -> Unit): () -> Unit {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                // The permission is kept across restarts of the phone.
                runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            }
            onPicked(uri?.toString())
        }
        return { launcher.launch(null) }
    }

    @Composable
    override fun rememberPhotoPicker(multiple: Boolean, onPicked: (List<String>) -> Unit): () -> Unit {
        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (multiple) {
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris: List<Uri> -> onPicked(uris.map { it.toString() }) }
            return { launcher.launch(request) }
        }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> onPicked(listOfNotNull(uri?.toString())) }
        return { launcher.launch(request) }
    }

    @Composable
    override fun rememberCamera(onShot: (String?) -> Unit): () -> Boolean {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val shot = PhotoFiles.capture(app)
            if (ok) onShot(shot.path) else { shot.delete(); onShot(null) }
        }
        return {
            val shot = PhotoFiles.capture(app)
            try {
                shot.createNewFile()
                launcher.launch(FileProvider.getUriForFile(app, "clean.app.files", shot))
                true
            } catch (e: ActivityNotFoundException) {
                false
            }
        }
    }

    override fun pickDateTime(initial: LocalDateTime, onPick: (LocalDateTime) -> Unit) {
        val context = activity ?: return
        val dialog = DatePickerDialog(context, { _, year, month, day ->
            TimePickerDialog(context, { _, hour, minute ->
                onPick(LocalDateTime(year, month + 1, day, hour, minute, initial.second))
            }, initial.hour, initial.minute, true).blinking().show()
        }, initial.year, initial.month.ordinal, initial.day)
        dialog.datePicker.maxDate = System.currentTimeMillis()
        dialog.blinking().show()
    }

    override fun pickTime(hour: Int, minute: Int, onPick: (hour: Int, minute: Int) -> Unit) {
        val context = activity ?: return
        TimePickerDialog(context, { _, h, m -> onPick(h, m) }, hour, minute, true).blinking().show()
    }

    /** The phone's own dialogs (date and time pickers) blink like the app's: call before `show()`. */
    private fun <D : android.app.Dialog> D.blinking(): D = apply { window?.setWindowAnimations(windowAnimation()) }

    /** The stream a handle names: a `content://` address a picker gave, or a file's path. */
    private fun open(handle: String): InputStream? =
        if (handle.startsWith("content:") || handle.startsWith("file:")) app.contentResolver.openInputStream(Uri.parse(handle)) else File(handle).inputStream()

    override suspend fun readFile(handle: String): ByteArray = withContext(Dispatchers.IO) { open(handle)!!.use { it.readBytes() } }
    override suspend fun writeFile(handle: String, bytes: ByteArray) = withContext(Dispatchers.IO) { app.contentResolver.openOutputStream(Uri.parse(handle), "wt")!!.use { it.write(bytes) } }
    override fun folderName(handle: String): String? = DocumentFile.fromTreeUri(app, Uri.parse(handle))?.name
    override fun releaseFolder(handle: String) = app.contentResolver.releasePersistableUriPermission(Uri.parse(handle), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    override suspend fun stageFile(handle: String): String = withContext(Dispatchers.IO) { Backup.copyToCache(app, open(handle)!!).path }
    override fun discardStaged(path: String) { File(path).delete() }
    override suspend fun takeStaged(path: String): ByteArray = withContext(Dispatchers.IO) { File(path).let { f -> f.readBytes().also { f.delete() } } }

    // ---- Sharing

    override fun shareFile(name: String, bytes: ByteArray, mimeType: String) {
        val folder = File(app.cacheDir, "shared").apply { mkdirs() }
        val file = File(folder, name.replace(Regex("[^A-Za-z0-9._-]+"), "_")).apply { writeBytes(bytes) }
        val address = FileProvider.getUriForFile(app, "clean.app.files", file)
        val send = Intent(Intent.ACTION_SEND).setType(mimeType).putExtra(Intent.EXTRA_STREAM, address).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, null))
    }

    override fun shareText(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, null))
    }

    private fun startActivity(intent: Intent) {
        val from = activity
        if (from != null) from.startActivity(intent) else app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun copyToClipboard(label: String, text: String) {
        (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    }

    override fun encodePng(image: ImageBitmap): ByteArray = ByteArrayOutputStream().also { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

    // ---- Backup

    override fun animationsBytes(): Long = Backup.animationsBytes(app)
    override suspend fun writeBackup(handle: String, withAnimations: Boolean) = withContext(Dispatchers.IO) {
        app.contentResolver.openOutputStream(Uri.parse(handle), "wt")!!.use { Backup.write(app, it, withAnimations) }
    }
    override fun isBackup(path: String): Boolean = Backup.isBackup(File(path))
    override fun inspectBackup(path: String): BackupInspected = Backup.inspect(app, File(path))
    override fun currentContents(): BackupContents = Backup.current(app)
    override fun applyBackup(path: String) = Backup.apply(app, File(path))
    override fun wipeEverything() = Backup.wipe(app)
    override fun restartApp(): Nothing = Backup.restart(app)

    // ---- Animations

    override fun animationCount(): Int = Animations.count(app)
    override fun clearAnimations() = Animations.clear(app)
    override suspend fun importAnimations(handle: String, wanted: Set<String>): Int = Animations.import(app, Uri.parse(handle), wanted)

    @Composable
    override fun rememberAnimationPicture(media: String): (@Composable (Modifier) -> Unit)? {
        val drawable by produceState<Drawable?>(null, media) {
            value = withContext(Dispatchers.IO) {
                val file = Animations.file(app, media)
                if (!file.exists()) null
                else runCatching {
                    // Moving pictures need Android 9; before that the first frame is shown still.
                    if (Build.VERSION.SDK_INT >= 28) android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(file))
                    else BitmapDrawable(app.resources, BitmapFactory.decodeFile(file.path))
                }.getOrNull()
            }
        }
        val picture = drawable ?: return null
        return remember(picture) {
            @Composable { modifier: Modifier ->
                AndroidView(
                    factory = { ImageView(it) },
                    modifier = modifier,
                    update = { view ->
                        view.setImageDrawable(picture)
                        if (Build.VERSION.SDK_INT >= 28) (picture as? AnimatedImageDrawable)?.start()
                    },
                )
            }
        }
    }

    override fun animationStill(media: String): ImageBitmap? {
        val file = Animations.file(app, media).takeIf { it.exists() } ?: return null
        // Half size is plenty for a thumbnail, and two bytes a dot keeps a long list light.
        val options = BitmapFactory.Options().apply { inSampleSize = 2; inPreferredConfig = Bitmap.Config.RGB_565 }
        return runCatching { BitmapFactory.decodeFile(file.path, options)?.asImageBitmap() }.getOrNull()
    }

    // ---- Pictures

    override fun listPhotos(profile: Long): List<Photo> = PhotoFiles.list(app, profile)
    override fun photoPath(profile: Long, name: String): String = PhotoFiles.file(app, profile, name).path
    override fun addPhoto(profile: Long, source: String, pose: Pose, exifDate: Boolean): Photo? = PhotoFiles.add(app, profile, { open(source) }, pose, exifDate)
    override fun changePhoto(profile: Long, photo: Photo, at: LocalDateTime, pose: Pose): Photo? = PhotoFiles.change(app, profile, photo, at, pose)
    override fun removePhotoAside(profile: Long, photo: Photo): String? = PhotoFiles.removeAside(app, profile, photo)?.path
    override fun restorePhoto(profile: Long, photo: Photo, aside: String): Boolean = PhotoFiles.putBack(app, profile, photo, File(aside))
    override fun discardAside(path: String) { File(path).deleteRecursively() }
    override fun photoBytes(profile: Long, name: String): ByteArray? = runCatching { PhotoFiles.file(app, profile, name).readBytes() }.getOrNull()
    override fun comparePhotos(profile: Long, left: Photo, right: Photo, leftText: String, rightText: String, middle: List<String>): ByteArray? =
        PhotoFiles.pair(app, profile, left, right, leftText, rightText, middle)

    override fun listMoments(profile: Long): List<Moment> = MomentFiles.list(app, profile)
    override fun momentPath(profile: Long, name: String): String = MomentFiles.file(app, profile, name).path
    override fun addMoment(profile: Long, source: String): Moment? = MomentFiles.add(app, profile) { open(source) }
    override fun removeMomentsAside(profile: Long, moments: List<Moment>, batch: String): String? = MomentFiles.removeAside(app, profile, moments, batch)?.path
    override fun restoreMoments(profile: Long, aside: String) = MomentFiles.putBack(app, profile, File(aside))
    override fun momentBytes(profile: Long, name: String): ByteArray? = runCatching { MomentFiles.file(app, profile, name).readBytes() }.getOrNull()
    override fun refile(profile: Long, pic: clean.core.Pic, at: LocalDateTime, pose: Pose?): clean.core.Pic? {
        val from = if (pic.snapshot) MomentFiles.file(app, profile, pic.name) else PhotoFiles.file(app, profile, pic.name)
        val target = clean.core.refiled(
            pic, at, pose, PhotoFiles.list(app, profile).map { it.name }.toSet(), MomentFiles.list(app, profile).map { it.name }.toSet(),
        ) { java.util.UUID.randomUUID().toString().take(8) }
        if (target.name == pic.name) return pic
        val to = if (target.snapshot) MomentFiles.file(app, profile, target.name) else PhotoFiles.file(app, profile, target.name)
        to.parentFile?.mkdirs()
        return target.takeIf { from.renameTo(to) }
    }

    // ---- The phone's own lock (Lock photos)

    /** For tests: stands in for the phone's prompt, which Robolectric cannot show; null on a phone. */
    @Volatile var testLock: ((String, (Boolean) -> Unit) -> Unit)? = null
    @Volatile var testHasLock: Boolean? = null
    private val lockKinds = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    override val canAuthenticate: Boolean
        get() = testHasLock ?: (testLock != null || BiometricManager.from(app).canAuthenticate(lockKinds) == BiometricManager.BIOMETRIC_SUCCESS)

    override fun authenticate(reason: String, onResult: (Boolean) -> Unit) {
        testLock?.let { it(reason, onResult); return }
        val host = activity as? androidx.fragment.app.FragmentActivity
        if (host == null) { onResult(false); return }
        host.runOnUiThread {
            try {
                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onResult(false)
                }
                // Fingerprint or face, or the PIN, pattern or password as the fallback: the phone's own lock, so there is no button of ours on it.
                val info = BiometricPrompt.PromptInfo.Builder().setTitle("Clean").setSubtitle(reason).setAllowedAuthenticators(lockKinds).build()
                BiometricPrompt(host, ContextCompat.getMainExecutor(host), callback).authenticate(info)
            } catch (e: RuntimeException) {
                onResult(false)
            }
        }
    }

    override fun decodeStored(path: String, px: Int, light: Boolean): ImageBitmap? =
        PhotoFiles.decode(File(path), px, if (light) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888)?.asImageBitmap()

    override fun loadAvatar(profile: Long): ImageBitmap? = AvatarFiles.load(app, profile)?.asImageBitmap()
    override fun setAvatar(profile: Long, source: String): Boolean = AvatarFiles.set(app, profile) { open(source) }
    override fun deleteAvatar(profile: Long) = AvatarFiles.delete(app, profile)
    override fun deleteProfilePictures(profile: Long) {
        PhotoFiles.deleteAll(app, profile); MomentFiles.deleteAll(app, profile); AvatarFiles.delete(app, profile)
    }

    // ---- Drawing

    override fun labelPaint(argb: Int, textSizePx: Float, bold: Boolean): LabelPaint {
        val paint = Paint().apply { color = argb; textSize = textSizePx; isAntiAlias = true; isFakeBoldText = bold }
        return object : LabelPaint {
            override val textSize: Float get() = paint.textSize
            override var alpha: Int
                get() = paint.alpha
                set(value) { paint.alpha = value }
            override fun draw(canvas: Canvas, text: String, x: Float, y: Float) { canvas.nativeCanvas.drawText(text, x, y, paint) }
        }
    }

    override fun hitRegion(path: Path, width: Int, height: Int): HitRegion {
        val region = Region().apply { setPath(path.asAndroidPath(), Region(0, 0, width, height)) }
        return HitRegion { x, y -> region.contains(x, y) }
    }
}
