@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package clean.app.ios

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import clean.app.AppModel
import clean.app.STACK_KEY
import clean.app.data.CleanDb
import clean.app.data.DB_FILE
import clean.app.data.open
import clean.app.platform.actionOfLink
import clean.core.AutoBackupFrequency
import clean.core.ShortcutKind
import clean.core.shortcutsShown
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationShortcutIcon
import platform.UIKit.UIApplicationShortcutIconType
import platform.UIKit.UIApplicationShortcutItem
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UIKit.UIInterfaceOrientationMask
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIScreen
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.alternateIconName
import platform.UIKit.setAlternateIconName
import platform.UIKit.setNeedsUpdateOfSupportedInterfaceOrientations
import platform.UIKit.shortcutItems
import platform.UIKit.supportsAlternateIcons

/**
 * Everything on the iPhone that lives outside one screen: what the Swift host calls (launch, a link or quick action opening the app), the
 * model that is on screen, the screen's appearance and orientation, the quick actions, the app icon and the background backup task.
 * One object for the process; [IosPlatform] and [IosNotifications] reach the rest through it.
 */
object IosHost {
    val platform: IosPlatform by lazy { IosPlatform(this) }
    val notifications: IosNotifications by lazy { IosNotifications(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Raised to make the root build a new model (after a restore or a wipe, which on Android restart the process). */
    val generation = mutableIntStateOf(0)
    /** The phone's "Reduce motion", read each time the app comes to the front. */
    val phoneStill = mutableStateOf(false)
    /** Whether the phone itself is in dark mode, whatever the window is overridden to. */
    val systemDark = mutableStateOf(false)

    private var model: AppModel? = null
    private var handle: SavedStateHandle? = null
    private var pendingAction: String? = null
    private var pendingFile: String? = null

    // ---- Launch

    /** Called by the Swift app delegate before the app has finished launching: the notification delegate and the background task have to be there by then. */
    fun didFinishLaunching() {
        Fs.delete(Fs.join(Fs.tmp, "picked"))
        notifications.install()
        registerBackgroundTask()
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { didBecomeActive() }
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { didEnterBackground() }
    }

    private fun didBecomeActive() {
        phoneStill.value = UIAccessibilityIsReduceMotionEnabled()
        systemDark.value = UIScreen.mainScreen.traitCollection.userInterfaceStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark
        notifications.refreshPermission()
        model?.onStarted()
    }

    private fun didEnterBackground() {
        // What Android keeps in the saved state for a process the phone kills behind the app.
        IosSavedState.save(handle?.get<ArrayList<String>>(STACK_KEY))
        model?.onStopped()
        val task = UIApplication.sharedApplication.beginBackgroundTaskWithName("clean.autobackup") {}
        scope.launch {
            try {
                val (freq, due) = IosAutoBackup.due()
                if (due) IosAutoBackup.run(platform.appVersionName.orEmpty())
                scheduleBackgroundBackup(freq)
            } finally {
                if (task != UIBackgroundTaskInvalid) onMain { UIApplication.sharedApplication.endBackgroundTask(task) }
            }
        }
    }

    // ---- The model on screen

    fun newSavedState(): SavedStateHandle {
        val stack = IosSavedState.load()
        return SavedStateHandle(if (stack == null) emptyMap() else mapOf(STACK_KEY to stack)).also { handle = it }
    }

    /** The root has a model: it gets what asked for the app while there was none. */
    fun attach(model: AppModel) {
        this.model = model
        pendingAction?.let { pendingAction = null; model.widgetAction(it) }
        pendingFile?.let { pendingFile = null; openSharedFile(model, it) }
    }

    fun detach(model: AppModel) { if (this.model === model) this.model = null }

    /** Builds a new model under the same screen: the database is read afresh, and Room migrates an older file. */
    fun restartModel() {
        IosSavedState.save(null)
        onMain { generation.intValue++ }
    }

    /** What a link, quick action or notification asked for (an action as `AppModel.widgetAction` takes it). */
    fun openAction(action: String) {
        val current = model
        if (current == null) pendingAction = action else onMain { current.widgetAction(action) }
    }

    private fun openSharedFile(model: AppModel, path: String) {
        model.stack.clear()
        model.openWorkoutSheet()
        model.sharedFile = path
    }

    // ---- What the Swift host passes in

    /** A `clean://` link, or a file opened with the app (a templates file from Files or a share sheet). */
    fun handleUrl(url: NSURL) {
        if (url.isFileURL()) {
            val scoped = url.startAccessingSecurityScopedResource()
            val path = url.path?.let { source -> Fs.newTmp(source.substringAfterLast('/')).also { Fs.copy(source, it) } }
            if (scoped) url.stopAccessingSecurityScopedResource()
            if (path == null) return
            val current = model
            if (current == null) pendingFile = path else onMain { openSharedFile(current, path) }
            return
        }
        if (url.scheme != "clean") return
        val items = NSURLComponents(url, false).queryItems?.filterIsInstance<NSURLQueryItem>().orEmpty()
        fun query(name: String) = items.firstOrNull { it.name == name }?.value
        actionOfLink(url.host, query("template"), query("tab"))?.let { openAction(it) }
    }

    /** A quick action of the home-screen icon, by its type (a [ShortcutKind] key). */
    fun handleShortcut(type: String) {
        when (type) {
            ShortcutKind.START.key -> openAction("start")
            ShortcutKind.RESUME.key -> openAction("workout")
            ShortcutKind.WEIGHT.key -> openAction("weight")
        }
    }

    // ---- Quick actions

    /** The home-screen icon's quick actions: Start, Resume (only while a workout runs) and Log bodyweight, as Android's launcher shortcuts. They do not name the next template. */
    fun refreshShortcuts() {
        scope.launch {
            val dao = CleanDb.open(Fs.join(Fs.root, DB_FILE)).dao()
            val id = dao.get("active")?.toLongOrNull()
            val running = id != null && dao.get("draft:$id") != null
            onMain {
                UIApplication.sharedApplication.shortcutItems = shortcutsShown(running).map { kind ->
                    val (title, icon) = when (kind) {
                        ShortcutKind.START -> "Start workout" to UIApplicationShortcutIconType.UIApplicationShortcutIconTypePlay
                        ShortcutKind.RESUME -> "Resume workout" to UIApplicationShortcutIconType.UIApplicationShortcutIconTypeTask
                        ShortcutKind.WEIGHT -> "Log bodyweight" to UIApplicationShortcutIconType.UIApplicationShortcutIconTypeAdd
                    }
                    UIApplicationShortcutItem(kind.key, title, null, UIApplicationShortcutIcon.iconWithType(icon), null)
                }
            }
        }
    }

    // ---- Appearance and orientation

    /** The status bar's icons follow the window's light or dark style, so the window is set to match the app's theme (and left to the phone for the "phone" theme). */
    private var baseIconsDark: Boolean? = null
    private var forcedIconsDark: Boolean? = null

    /** The theme chose its look: [followSystem] for the "phone" theme, else [dark] says what the pages are. */
    fun applyAppearance(followSystem: Boolean, dark: Boolean) {
        baseIconsDark = if (followSystem) null else !dark
        restyle()
    }

    private fun restyle() = onMain {
        val icons = forcedIconsDark ?: baseIconsDark
        topViewController()?.view?.window?.overrideUserInterfaceStyle = when (icons) {
            null -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            true -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
            false -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        }
    }

    /** Makes the status bar's icons dark or light for a screen that wants them (the photo viewer); returns what they were. */
    fun setIconsDark(dark: Boolean): Boolean {
        val theme = baseIconsDark ?: !systemDark.value
        val was = forcedIconsDark ?: theme
        forcedIconsDark = if (dark == theme) null else dark
        restyle()
        return was
    }

    /** Whether the app may turn to landscape (a full-screen chart); the Swift app delegate asks for the supported orientations. */
    var landscapeOn: Boolean = false
        private set

    fun supportedOrientations(): UIInterfaceOrientationMask = if (landscapeOn) UIInterfaceOrientationMaskLandscape else UIInterfaceOrientationMaskPortrait

    fun setLandscape(on: Boolean) {
        landscapeOn = on
        onMain {
            topViewController()?.setNeedsUpdateOfSupportedInterfaceOrientations()
            val scene = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>().firstOrNull()
            scene?.requestGeometryUpdateWithPreferences(UIWindowSceneGeometryPreferencesIOS(supportedOrientations())) { }
        }
    }

    // ---- The app icon

    /** The alternate icon of each theme; Graphite is the app's own icon. The names are the ones in the asset catalog (`iosApp/Clean/Assets.xcassets`) and in Info.plist. */
    val ICONS: Map<String, String?> = mapOf(
        "Graphite" to null, "Ember" to "AppIcon-Ember", "Crimson" to "AppIcon-Crimson", "Moss" to "AppIcon-Moss",
        "Ocean" to "AppIcon-Ocean", "Plum" to "AppIcon-Plum", "Ice" to "AppIcon-Ice", "Rose" to "AppIcon-Rose",
        // Round 34: the twelve themes wear the nearest of these eight icons.
        "Slate" to null, "Saffron" to "AppIcon-Ember", "Green" to "AppIcon-Moss", "Teal" to "AppIcon-Moss", "Lagoon" to "AppIcon-Ice",
        "Cobalt" to "AppIcon-Ocean", "Iris" to "AppIcon-Plum", "Orchid" to "AppIcon-Rose",
    )

    fun applyIcon(theme: String) = onMain {
        val app = UIApplication.sharedApplication
        if (!app.supportsAlternateIcons) return@onMain
        val want = ICONS[theme]
        if (want == app.alternateIconName) return@onMain
        app.setAlternateIconName(want) { }
    }

    // ---- The scheduled backup

    private const val BACKUP_TASK = "clean.app.autobackup"
    private var backup: Job? = null

    private fun registerBackgroundTask() {
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(BACKUP_TASK, null) { task ->
            val job = scope.launch {
                val ok = IosAutoBackup.run(platform.appVersionName.orEmpty())
                scheduleBackgroundBackup(IosAutoBackup.due().first)
                task?.setTaskCompletedWithSuccess(ok)
            }
            task?.expirationHandler = { job.cancel() }
        }
    }

    /** Asks iOS for a background refresh a day or a week from now (it decides the moment); null cancels it. Best effort: the backup is also made when the app goes to the background and is due. */
    fun scheduleBackgroundBackup(frequency: AutoBackupFrequency?) {
        val scheduler = BGTaskScheduler.sharedScheduler
        scheduler.cancelTaskRequestWithIdentifier(BACKUP_TASK)
        if (frequency == null) return
        val request = BGAppRefreshTaskRequest(BACKUP_TASK)
        request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(frequency.days * 86_400.0)
        withError { scheduler.submitTaskRequest(request, it) }
    }
}
