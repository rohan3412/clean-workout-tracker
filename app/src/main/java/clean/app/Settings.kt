package clean.app

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import clean.app.ui.theme.cardShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import clean.app.ui.theme.ACCENTS
import clean.core.LOOK_ACCENT_KEY
import clean.core.LOOK_BLACK_KEY
import clean.core.LOOK_THEMES
import clean.core.LOOK_THEME_KEY
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.draw.clip
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.delay
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import clean.app.data.MeasurementImport
import clean.app.data.WorkoutImport
import clean.core.ImportFile
import clean.core.ImportSource
import clean.core.buildWorkouts
import clean.core.exportFitNotes
import clean.core.exportHevy
import clean.core.exportJson
import clean.core.exportPlainCsv
import clean.core.needsUnit
import clean.core.readImportFile
import clean.core.OneRmFormula
import clean.core.RestEndFeedback
import clean.core.restEndFeedback
import clean.core.SetRange
import clean.core.estimate1Rm
import clean.core.shortNumber
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SettingsScreen(model: AppModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by model.profile.collectAsState()
    // Which colour the wheel is open for: only the sticky note's band.
    var wheel by remember { mutableStateOf<String?>(null) }
    var credits by remember { mutableStateOf(false) }
    var datasetCredits by remember { mutableStateOf(false) }
    val textSize by model.textSize.collectAsState()
    val theme by model.theme.collectAsState()
    val look by model.look.collectAsState()
    val auto by model.auto.collectAsState()
    val oneRm by model.oneRm.collectAsState()
    val restDefault by model.restDefault.collectAsState()
    val stickyColor by model.stickyColor.collectAsState()
    val saveTemplates = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            writeBytes(context, uri, model.templatesFile().toByteArray())
            model.message = "Templates exported"
        }
    }

    /** Runs [work] for the active profile and reports anything that goes wrong instead of crashing. */
    fun task(work: suspend (Long) -> Unit) {
        val id = profile?.id ?: return
        scope.launch {
            try {
                work(id)
            } catch (e: Exception) {
                model.message = e.message ?: "That did not work"
            }
        }
    }

    // Backup (Backup.kt); restoring one is in [rememberHistoryIn].
    var withAnimations by remember { mutableStateOf(true) }
    var backupChoice by remember { mutableStateOf(false) }
    var backingUp by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf<Backup.Contents?>(null) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            backingUp = true
            model.message = "Backing up ${plural(model.workouts.value.size, "workout")} and everything else…"
            try {
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { Backup.write(context, it, withAnimations) } }
                model.message = "Backup saved"
            } catch (e: Exception) {
                model.message = e.message ?: "The backup could not be saved"
            }
            backingUp = false
        }
    }
    // The switch for the animations is only worth a question when there are any.
    fun startBackup() { if (Backup.animationsBytes(context) > 0) backupChoice = true else saveBackup.launch("Clean backup ${LocalDate.now()}.zip") }
    // Import and restore are the flows of Data, shared with the welcome questionnaire.
    val history = rememberHistoryIn(model)
    LaunchedEffect(model.importNow) {
        if (model.importNow) {
            model.importNow = false
            history.pickImport()
        }
    }
    // The format is chosen in the export sheet; the file picker then names the file.
    var exporting by remember { mutableStateOf(false) }
    var exportKind by remember { mutableStateOf(ExportKind.STRONG) }
    fun exportTo(uri: Uri?) {
        if (uri != null) task {
            writeBytes(context, uri, exportBytes(model, it, exportKind))
            model.message = "Workouts exported"
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { exportTo(it) }
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { exportTo(it) }
    val saveMeasurements = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) task {
            writeBytes(context, uri, model.repo.exportMeasurements(it, System.currentTimeMillis()))
            model.message = "Measurements exported"
        }
    }

    val askHealth = rememberLauncherForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(Health.PERMISSIONS)) model.setOption("health", "on") else model.message = "Health Connect did not allow it, so nothing will be written"
    }
    // Asked once, when the background alert for a rest is switched on; if it is refused the switch stays off.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.setOption("rest.background", "on") else model.message = "Notifications are not allowed, so nothing will alert you in the background"
    }
    // The same, when the workout notification is switched on; refused, it stays off.
    val askWorkoutNotice = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.setOption("workout.notif", if (granted) "on" else "off")
        if (!granted) model.message = "Notifications are not allowed, so the workout will not show on the lock screen"
    }
    // And when the first day of a workout reminder is chosen; refused, the days are kept but nothing can be shown.
    val askReminder = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) model.message = "Notifications are not allowed, so the reminders cannot be shown"
    }
    var animations by remember { mutableStateOf(Animations.count(context)) }
    var importing by remember { mutableStateOf(false) }
    val pickAnimations = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            val kept = runCatching { Animations.import(context, uri) }.getOrDefault(-1)
            importing = false
            animations = Animations.count(context)
            model.message = when (kept) {
                -1 -> "That file could not be read as a zip"
                0 -> "No animations were found in that zip"
                else -> "$kept animations imported"
            }
        }
    }

    // Settings is a short list of four headings; each opens its own page of options.
    var page by remember { mutableStateOf(model.settingsPage.also { model.settingsPage = null }) }
    BackHandler(enabled = page != null) { page = null }
    val motion = LocalMotion.current
    val openPage: (String) -> Unit = { page = it }
    // A search over every section; a result opens its page scrolled to that section.
    var query by rememberSaveable { mutableStateOf("") }
    var jump by remember { mutableStateOf<String?>(null) }
    var restKinds by remember { mutableStateOf(false) }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar(page ?: "Settings", { if (page != null) page = null else model.back() }) }) { padding ->
        // The list and its pages swap like pages do: into a page is a step in, back to the list is a step out. The outgoing page keeps showing what it showed.
        AnimatedContent(
            page,
            modifier = Modifier.padding(padding).fillMaxSize(),
            transitionSpec = {
                Motion.blinkIn(motion) togetherWith Motion.blinkOut(motion)
            },
            label = "settings page",
        ) { page ->
        // A page of options opening settles its first sections in (Roll.kt); the list coming back does not.
        val entered = remember(page) { if (page != null) SettleState(armed = true) else null }
        LaunchedEffect(entered) { if (entered != null) { delay(Motion.settleWindowMillis()); entered.armed = false } }
        CompositionLocalProvider(LocalSettle provides (entered ?: LocalSettle.current)) {
        val listState = rememberLazyListState()
        // Scrolled to the section a search led to: the list is stepped through until its item is laid out, then brought to the top.
        if (page != null) LaunchedEffect(Unit) {
            val target = jump ?: return@LaunchedEffect
            jump = null
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            for (i in 0 until listState.layoutInfo.totalItemsCount) {
                val seen = listState.layoutInfo.visibleItemsInfo.firstOrNull { (it.key as? String)?.startsWith(target) == true }
                if (seen != null) { listState.scrollToItem(seen.index); break }
                listState.scrollToItem(i)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), listState) {
            if (page == null) item(key = "search") {
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s),
                    placeholder = { Text("Search settings") }, singleLine = true,
                    leadingIcon = { Icon(CleanIcons.Search, null) },
                    trailingIcon = if (query.isEmpty()) null else ({ IconButton(onClick = { query = "" }) { Icon(CleanIcons.Close, "Clear search") } }),
                )
            }
            if (page == null && query.isNotBlank()) {
                val found = searchSettings(query)
                if (found.isEmpty()) item(key = "none") { Text("No setting matches “${query.trim()}”.", Modifier.padding(Space.l), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(found, key = { it.key }) { place ->
                    // Inset like the search box above; LinkRow has no side padding of its own.
                    Box(Modifier.padding(horizontal = Space.l)) { LinkRow(place.title, place.page, "") { jump = place.key; query = ""; openPage(place.page) } }
                }
            }
            if (page == null && query.isBlank()) {
                items(SETTINGS_PAGES) { (title, about) ->
                    Row(Modifier.settleNext().fillMaxWidth().clickable { openPage(title) }.padding(horizontal = Space.l, vertical = Space.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                        val icon = when (title) {
                            PAGE_WORKOUT -> CleanIcons.DumbbellLine
                            PAGE_APPEARANCE -> CleanIcons.StarLine
                            PAGE_DATA -> CleanIcons.Folder
                            else -> CleanIcons.Info
                        }
                        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainer, cardShape()), contentAlignment = Alignment.Center) {
                            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(CleanIcons.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            // Data and backup
            if (page == PAGE_DATA) item(key = "s-backup") {
                PageSection("Backup", Modifier.settleNext()) {
                    Text(
                        "One zip with everything in Clean. Restoring it replaces what is on the phone. Keep it somewhere off the phone.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    QuietButton(if (backingUp) "Backing up ${plural(model.workouts.collectAsState().value.size, "workout")}…" else "Back up everything", { startBackup() }, Modifier.fillMaxWidth(), enabled = !backingUp)
                    QuietButton("Restore a backup", { history.pickRestore() }, Modifier.fillMaxWidth())
                }
            }
            if (page == PAGE_DATA) item(key = "s-import") {
                PageSection("Import into ${profile?.name ?: "profile"}") {
                    Text(
                        "From Strong, Hevy, Lyfta, Fitbod, FitNotes, GymMane or openGym, or any CSV with a date, an exercise and reps; a zip with one of them in it works too, and so does Strong's measurements zip. A Clean backup picked here is restored instead. Clean says what the file is and what it would add before anything is saved.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    QuietButton("Import", { history.pickImport() }, Modifier.fillMaxWidth())
                }
            }
            if (page == PAGE_DATA) item(key = "s-export") {
                PageSection("Export from ${profile?.name ?: "profile"}") {
                    Text("Strong's format is the exact one, so the file opens anywhere a Strong export does. The other formats hold only what those apps have room for.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    QuietButton("Workouts", { exporting = true }, Modifier.fillMaxWidth())
                    QuietButton("Measurements", { saveMeasurements.launch("clean_measurements_${LocalDate.now()}.zip") }, Modifier.fillMaxWidth())
                    Toggle(model, "export.rest", "Write rest timers", "A row after each set with the rest that followed it, as older Strong exports have. Applies to workouts finished from now on.", default = false)
                }
            }
            if (page == PAGE_DATA) item(key = "s-health") {
                PageSection("Health Connect", Modifier.settleNext()) {
                    Text(
                        "Health Connect is the store on this phone that health apps share. With this on, Clean writes each finished workout to it (as strength training, with its name and times) and each weigh-in. Clean reads nothing from it, and none of it goes online.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!Health.available(context)) Text("Health Connect is not available on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        // Switching it on asks Health Connect for the two permissions; it stays off if they are refused.
                        val health = model.flag("health", false)
                        val setHealth: (Boolean) -> Unit = { on -> if (on) askHealth.launch(Health.PERMISSIONS) else model.setOption("health", "off") }
                        Row(Modifier.toggleable(health, role = Role.Switch, onValueChange = setHealth).minTouch(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Write workouts and weigh-ins", Modifier.weight(1f))
                            Switch(health, null)
                        }
                        if (model.flag("health", false)) QuietButton("Send everything logged so far", {
                            scope.launch { model.message = "${model.sendHistoryToHealth()} records sent to Health Connect" }
                        }, Modifier.fillMaxWidth())
                    }
                }
            }
            if (page == PAGE_DATA) item(key = "s-animations") {
                PageSection("Exercise animations", Modifier.settleNext()) {
                    Text(
                        "The steps on an exercise's Info tab can have a moving picture above them. The pictures are © Gym visual and are not part of Clean: for your own use, download the zip of github.com/hasaneyldrm/exercises-dataset (Code, Download ZIP, about 130 MB) in a browser and pick it here. Only the pictures are copied in; the app still never goes online.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LabelValue("On this phone", if (animations == 0) "none" else "$animations")
                    QuietButton(if (importing) "Importing…" else "Import animations from a zip", { pickAnimations.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), enabled = !importing)
                    if (animations > 0) DangerButton("Remove the animations", { Animations.clear(context); animations = 0 })
                }
            }
            if (page == PAGE_DATA) item(key = "s-templates") {
                PageSection("Templates", Modifier.settleNext()) {
                    Text("Strong's export has no templates, so they travel in a file of Clean's own, folders included. The same two actions are in the Workout sheet's ⋯ menu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    QuietButton("Export templates", { saveTemplates.launch("clean_templates_${LocalDate.now()}.json") }, Modifier.fillMaxWidth())
                    QuietButton("Import templates", { model.importTemplatesNow = true; model.stack.clear(); model.openWorkoutSheet() }, Modifier.fillMaxWidth())
                }
            }
            if (page == PAGE_DATA) item(key = "s-renamed") { RenamedExercises(model) }
            if (page == PAGE_DATA) item(key = "s-tidy") {
                PageSection("Tidy up", Modifier.settleNext()) {
                    Text("Notes such as “Wide grip” can be turned into variations, so those sets get their own history and PRs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    QuietButton("Turn notes into variations", { model.open(Screen.NoteReview()) }, Modifier.fillMaxWidth())
                }
            }
            if (page == PAGE_DATA) item(key = "s-delete") {
                PageSection("Delete everything", Modifier.settleNext()) {
                    Text(
                        "Takes every profile, workout, template, measurement, option and imported file off this phone, and Clean starts as when it was first installed.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DangerButton("Delete everything…", { scope.launch { wipe = withContext(Dispatchers.IO) { Backup.current(context) } } })
                }
            }
            // Appearance (D4, F5): theme, accent, then rows.
            if (page == PAGE_APPEARANCE) item(key = "ap-theme") {
                PageSection("Theme", Modifier.settleNext()) {
                    Segmented(LOOK_THEMES, theme, { THEME_NAMES.getValue(it) }) { model.setOption(LOOK_THEME_KEY, it) }
                    Text(
                        if (theme == "phone") "Follows the phone: Dark or Light as it is." else "Dark is easy on the eyes at the gym; Light is for bright rooms.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (page == PAGE_APPEARANCE) item(key = "ap-accent") {
                PageSection("Accent", Modifier.settleNext()) {
                    Text("Buttons, selection, links, the running workout, and the calendar, body figure and charts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for ((name, shades) in ACCENTS) {
                            val on = name == look.accentName
                            val colour = if (look.dark) shades.first else shades.second
                            Box(
                                Modifier.size(44.dp).background(colour, CircleShape)
                                    .border(if (on) 3.dp else 0.dp, if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (on) Icon(CleanIcons.Check, null, Modifier.size(22.dp), tint = colour.readableOn())
                                Swatch(name, on) { model.setOption(LOOK_ACCENT_KEY, name) }
                            }
                        }
                    }
                }
            }
            if (page == PAGE_APPEARANCE) item(key = "ap-text") {
                PageSection("Text size", Modifier.settleNext()) {
                    Choice(listOf("Smallest", "Small", "Medium", "Large", "Largest"), textSize, { it }) { model.setOption("textSize", it) }
                    Text("The spacing of cards and lists follows the text: smaller text packs more onto a screen, larger text gives it room.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (page == PAGE_APPEARANCE) item(key = "ap-black") {
                PageSection("Background", Modifier.settleNext()) {
                    // Only the Dark theme has a black page; Light has nothing to turn black.
                    val meaningful = theme != "light"
                    Toggle(
                        model, LOOK_BLACK_KEY, "Pure black background",
                        if (meaningful) "A black page under the cards, for OLED screens." else "Dark theme only: switch to Dark (or Phone) to use it.",
                        default = false, enabled = meaningful,
                    )
                }
            }
            if (page == PAGE_APPEARANCE) item(key = "ap-glass") {
                PageSection("Transparency", Modifier.settleNext()) {
                    Toggle(model, "glass.reduce", "Reduce transparency", "Solid bars, sheets and menus. Also on when the phone's own reduce-transparency or high-contrast setting is on.", default = false)
                }
            }
            if (page == PAGE_APPEARANCE) item(key = "ap-cbsafe") {
                PageSection("Colours", Modifier.settleNext()) {
                    Toggle(model, "theme.cbsafe", "Colour-blind safe colours", "Blue and orange in place of green and red for PRs, fatigue, strength and BMI.", default = false)
                }
            }
            if (page == PAGE_APPEARANCE) item(key = "ap-motion") {
                PageSection("Animations", Modifier.settleNext()) {
                    Toggle(model, "ui.motion", "Animations", "Cards growing into pages, fades, glides and celebrations. Off, everything changes at once, and ticking a set makes no buzz.")
                }
            }
            if (page == PAGE_APPEARANCE) item(key = "ap-sticky") {
                PageSection("Sticky notes", Modifier.settleNext()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        for (name in STICKY_COLORS.keys) {
                            val on = name == stickyColor || (stickyColor.isEmpty() && name == STICKY_COLORS.keys.first())
                            Box(
                                Modifier.size(34.dp).background(stickyBand(name, look.dark), CircleShape)
                                    .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Swatch(name, on) { model.setOption("sticky.color", name) } }
                        }
                        WheelSwatch(34, stickyColor.startsWith("#")) { wheel = "sticky.color" }
                    }
                    Text("The colour of a note's band in a workout.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (page == PAGE_APPEARANCE) item(key = "ap-blur") {
                PageSection("Photos", Modifier.settleNext()) {
                    Toggle(model, "photos.blur", "Blur progress photos", "On Profile and in the photo list. A photo is sharp once you open it.", default = false)
                }
            }
            // Workout: Rest, then Training, then the rest of what a workout does.
            if (page == PAGE_WORKOUT) item(key = "s-rest") {
                val stored by model.options.collectAsState()
                PageSection("Rest", Modifier.settleNext()) {
                    RestChoice("Default rest", restDefault, listOf(0, 60, 90, 120, 180, 300), { model.setOption("rest.seconds", it.toString()) })
                    Text("An exercise can have its own length, from its options in a workout.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RestChoice("Rest between sides", stored["rest.sides"]?.toIntOrNull() ?: 60, listOf(0, 30, 60, 90, 120), { model.setOption("rest.sides", it.toString()) })
                    Text("After the first side of a left and right exercise, before the other", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    QuietButton("Rest by set type…", { restKinds = true }, Modifier.fillMaxWidth(), summary = "Warm-up, failure and drop sets, and after a hard set")
                    Text("When the rest ends", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val ends = listOf(RestEndFeedback.SOUND, RestEndFeedback.VIBRATE, RestEndFeedback.BOTH)
                    val end = restEndFeedback(stored["feedback.restEnd"], stored["rest.sound"], stored["rest.alert"])
                    Choice(ends, end, { if (it == RestEndFeedback.VIBRATE) "Buzz" else it.key.replaceFirstChar(Char::uppercase) }) { model.setOption("feedback.restEnd", it.key); if (it.sound) Feedback.bell(RestTone.Deep) }
                    Text(
                        "Sound is the bell at zero, at the phone's media volume, so it comes through headphones. Buzz is a pulse on each of the last five seconds, the last one longer.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Toggle(
                        model, "rest.background", "Alert outside the app",
                        "An alarm on the phone buzzes through the last five seconds and rings the bell as the rest ends, with a notification counting down. Nothing goes online.", default = false,
                    ) { on ->
                        if (!on) model.setOption("rest.background", "off")
                        else if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else model.setOption("rest.background", "on")
                    }
                    Toggle(model, "rest.return", "Go back to the workout when the rest ends")
                }
            }
            if (page == PAGE_WORKOUT) item(key = "s-consistency") {
                PageSection("Training", Modifier.settleNext()) {
                    val goals = model.targets.collectAsState().value.orEmpty()
                    val monday = clean.core.weekStart(java.time.LocalDate.now())
                    Text("Weekly target", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choice((1..7).toList(), clean.core.targetFor(goals, monday), { "$it" }) { model.setWeeklyTarget(it) }
                    (goals.lastOrNull { it.from <= monday } ?: goals.firstOrNull())?.let {
                        Text(
                            "Workouts a week that count as a good week. Applies from the week of ${prettyDay(it.from)}; earlier weeks keep the target they had.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val program by model.program.collectAsState()
                    LabelValue("Program", if (program.isEmpty()) "None" else "On") { model.open(Screen.Program) }
                    Text(
                        "Follow a weekly plan of templates and rest days; a streak counts the weeks it was kept.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Toggle(model, "split.on", "Current split", "Rotate Up next through one folder", default = false)
                    Toggle(model, "workout.hint", "Suggest what to try", "Under an exercise's name: last time's best set and the set that would beat your best. Not shown where a progression is set.")
                    Toggle(model, "reward.celebrate", "Celebrate on finishing", "A target reached, a new badge or a shield earned shows at the top of the summary, with a chime and two taps. Silent with animations off.")
                }
            }
            if (page == PAGE_WORKOUT) item(key = "s-onerm") {
                PageSection("Estimated 1RM", Modifier.settleNext()) {
                    Text(
                        "The heaviest single you could likely lift, worked out from a set's weight and reps. It is what 1RM PRs, the charts, the 1RM tiles and the L 1RM and R 1RM columns show.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Formula", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // The three on offer; a formula chosen before from the longer list stays on the list until another is picked.
                    val formulas = (ONE_RM_CHOICES + oneRm.formula).distinct()
                    Choice(formulas, oneRm.formula, { if (it == OneRmFormula.HYBRID) "Hybrid (recommended)" else it.label }) { model.setOption("onerm.formula", it.name) }
                    Text(
                        when (oneRm.formula) {
                            OneRmFormula.HYBRID -> "Hybrid uses Brzycki under 10 reps and Epley from 10 up."
                            OneRmFormula.BRZYCKI -> "Brzycki: weight × 36 ÷ (37 − reps). Reads lower than Epley at high reps."
                            OneRmFormula.EPLEY -> "Epley: weight × (1 + reps ÷ 30). Reads higher than Brzycki at low reps."
                            else -> "${oneRm.formula.label}, chosen before; pick one of the three above to change it."
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Toggle(
                        model, "onerm.rpe", "Factor in RPE",
                        "Adjusts the estimate by a set's RPE: the reps left in reserve (10 − RPE) count as done, so 100 × 8 at RPE 8 counts as 10 reps. RPE 10, no RPE, or an RPE under 5 changes nothing.",
                        default = false,
                    )
                    Text("Most reps it is used for", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choice(ONE_RM_LIMITS, oneRm.maxReps, { "$it" }) { model.setOption("onerm.maxReps", it.toString()) }
                    Text("100 ${profile?.unit ?: "kg"} × 8 → ${shortNumber(Math.round(estimate1Rm(100.0, 8, oneRm)!! * 10) / 10.0)} ${profile?.unit ?: "kg"}")
                    Text(
                        "Sets with more reps than the limit get no estimate. Changing any of this recalculates every estimate and PR; no workout is changed.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (page == PAGE_WORKOUT) item(key = "s-weeksets") {
                PageSection("Weekly sets per muscle", Modifier.settleNext()) {
                    val range by model.defaultSetRange.collectAsState()
                    var low by remember { mutableStateOf(range.min.toString()) }
                    var high by remember { mutableStateOf(range.max.toString()) }
                    fun store() { SetRange.decode("${low.trim()}-${high.trim()}")?.let { model.setSetRange(null, it.encode()) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        OutlinedTextField(low, { low = it; store() }, label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(high, { high = it; store() }, label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Text(
                        "Sets a week to aim for in each muscle group, counted as on the Body page. Each muscle can have its own range, from its page.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (page == PAGE_WORKOUT) item(key = "s-autofill") {
                PageSection("Auto-fill sets", Modifier.settleNext()) {
                    Text(
                        "“Auto-fill sets” in an exercise's options during a workout lays out these sets and fills in a weight and reps that would beat your best estimated 1RM: more reps at the same weight first, then more weight.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Warm-up sets", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choice((0..3).toList(), auto.third, { it.toString() }) { model.setOption("auto.warm", it.toString()) }
                    Text("One warm-up is half the working weight; more of them step up evenly from there.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Working sets", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choice((1..5).toList(), auto.first, { it.toString() }) { model.setOption("auto.sets", it.toString()) }
                    Text("Drop sets", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choice((0..3).toList(), auto.second, { it.toString() }) { model.setOption("auto.drops", it.toString()) }
                }
            }
            if (page == PAGE_WORKOUT) item(key = "s-during") {
                val stored by model.options.collectAsState()
                PageSection("In a workout", Modifier.settleNext()) {
                    Toggle(model, "workout.summary", "Summary when a workout is finished", "Duration, volume, PRs and muscles, next to the last time you did the same routine.")
                    Toggle(model, "workout.rpe", "RPE on the number pad", "A key for how hard a set felt, 6 to 10. It is saved in the export's RPE column.")
                    Toggle(model, "workout.highlight", "Show the next set", "The set to do next is marked and scrolled to. In a superset the turn alternates.")
                    Toggle(model, "workout.compact", "Shrink finished exercises", "An exercise with every set ticked takes a line or two until touched.")
                    Toggle(
                        model, "workout.notif", "Workout on the lock screen",
                        "While the app is behind, a quiet notification shows the workout's time, the rest and the next set; a tap goes back to it.",
                    ) { on ->
                        if (!on) model.setOption("workout.notif", "off")
                        else if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) askWorkoutNotice.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else model.setOption("workout.notif", "on")
                    }
                    // Workout-day reminders: off until a day is chosen.
                    val remindDays = clean.core.parseRemindDays(stored["remind.days"])
                    val remindAt = clean.core.parseClock(stored["remind.time"]) ?: clean.core.REMIND_DEFAULT_TIME
                    Text("Remind me to train on", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        for (day in java.time.DayOfWeek.entries) CleanChip(day in remindDays, {
                            val next = if (day in remindDays) remindDays - day else remindDays + day
                            if (remindDays.isEmpty() && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) askReminder.launch(Manifest.permission.POST_NOTIFICATIONS)
                            model.setOption("remind.days", clean.core.encodeRemindDays(next))
                        }, { Text(day.getDisplayName(java.time.format.TextStyle.SHORT, androidx.compose.ui.platform.LocalConfiguration.current.locales[0])) })
                    }
                    if (remindDays.isNotEmpty()) QuietButton("At ${clean.core.clockText(remindAt)}", {
                        android.app.TimePickerDialog(context, { _, hour, minute -> model.setOption("remind.time", clean.core.clockText(hour * 60 + minute)) }, remindAt / 60, remindAt % 60, true).blinking().show()
                    }, summary = "Not on a day you have already trained")
                    var plateUnit by remember { mutableStateOf<String?>(null) }
                    val unit = profile?.unit ?: "kg"
                    QuietButton("Bar and plates…", { plateUnit = unit }, summary = "Your own bar and plates, for the plate calculator in a barbell set's menu")
                    plateUnit?.let { PlateKitDialog(model, it) { plateUnit = null } }
                }
            }
            // About and help
            if (page == PAGE_ABOUT) item(key = "s-source") {
                PageSection("Source code", Modifier.settleNext()) {
                    Text("Clean is open source under the MIT License. The code is at:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Just the address, to read or copy; the app has no internet access and opens nothing.
                    SelectionContainer { Text(SOURCE_URL, color = MaterialTheme.colorScheme.primary) }
                }
            }
            if (page == PAGE_ABOUT) item(key = "s-changes") {
                // Every version; the pop-up after an update shows only the newest.
                val releases = remember { readChanges(context) }
                if (releases.isNotEmpty()) PageSection("What's new") { ChangeList(releases) }
            }
            if (page == PAGE_ABOUT) item(key = "s-credits") {
                PageSection("Licences and credits", Modifier.settleNext()) {
                    Text("The body figure is drawn from MuscleMap by Melih Colpan, used under the MIT License.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { credits = true }) { Text("Show the licence") }
                    Text("The steps on an exercise's Info tab are from exercises-dataset by Hasan Emir Yıldırım (github.com/hasaneyldrm/exercises-dataset), used under the MIT License. None of that dataset's pictures or animations are in the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { datasetCredits = true }) { Text("Show the licence") }
                    Text("The text and numbers are set in Inter, Copyright 2020 The Inter Project Authors (github.com/rsms/inter), used under the SIL Open Font License, Version 1.1 (openfontlicense.org). The font file is unchanged.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Some icons are Google's Material icons, used under the Apache License 2.0 (apache.org/licenses/LICENSE-2.0).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("The blur behind bars and sheets is done by Haze by Chris Banes (github.com/chrisbanes/haze), used under the Apache License 2.0 (apache.org/licenses/LICENSE-2.0).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (page == PAGE_ABOUT) item(key = "s-tips") {
                PageSection("Tips", Modifier.settleNext()) {
                    for (tip in TIPS) Text(tip)
                }
            }
            item { Spacer(Modifier.height(Space.xxl)) }
        }
        }
        }
    }

    if (credits || datasetCredits) {
        // Both are under the MIT License; only the copyright line differs.
        CleanDialog(
            onDismissRequest = { credits = false; datasetCredits = false },
            title = { Text(if (credits) "MuscleMap" else "exercises-dataset") },
            text = { Text(if (credits) MUSCLEMAP_LICENCE else MUSCLEMAP_LICENCE.replace("Melih Colpan", "Hasan Emir Yıldırım"), Modifier.verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { credits = false; datasetCredits = false }) { Text("Close") } },
        )
    }
    wheel?.let { target ->
        val initial = STICKY_COLORS[stickyColor] ?: parseColor(stickyColor) ?: STICKY_COLORS.values.first()
        ColorPickerDialog(initial, onPick = { model.setOption(target, it.hex()) }) { wheel = null }
    }
    if (restKinds) {
        val stored by model.options.collectAsState()
        CleanDialog(
            onDismissRequest = { restKinds = false },
            title = { Text("Rest by set type") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    // A kind of set can have a rest of its own; "Same" follows the default, and a warm-up left alone rests half of it.
                    for ((key, label) in listOf("rest.warmup" to "After a warm-up set", "rest.failure" to "After a set to failure", "rest.drop" to "After a drop set")) {
                        val own = stored[key]?.toIntOrNull() ?: -1
                        RestChoice(
                            label, own, if (key == "rest.warmup") listOf(-1, 0, 15, 30, 60, 90, 120, 180, 300) else listOf(-1, 0, 30, 60, 90, 120, 180, 300),
                            { model.setOption(key, if (it < 0) null else it.toString()) },
                            value = if (own >= 0) restLabel(own) else if (key == "rest.warmup") "Half · ${restLabel(restDefault / 2)}" else "Same · ${restLabel(restDefault)}",
                            unset = if (key == "rest.warmup") "Half" else "Same",
                        )
                    }
                    val hard = stored["rest.hard"]?.toIntOrNull() ?: 0
                    RestChoice("Extra rest after a hard set", hard, listOf(0, 15, 30, 60), { model.setOption("rest.hard", it.toString()) })
                    Text("RPE 9 or more, or a set to failure", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { restKinds = false }) { Text("Done") } },
        )
    }
    if (exporting) Sheet({ exporting = false }, "Export workouts") {
        for (kind in ExportKind.entries) SheetRow(kind.label) {
            exporting = false
            exportKind = kind
            val name = "${kind.file}${LocalDate.now()}"
            if (kind == ExportKind.JSON) saveJson.launch("$name.json") else saveCsv.launch("$name.csv")
        }
    }
    if (backupChoice) {
        val megabytes = remember { (Backup.animationsBytes(context) + 999_999) / 1_000_000 }
        CleanDialog(
            onDismissRequest = { backupChoice = false },
            title = { Text("Back up everything") },
            text = {
                Row(Modifier.toggleable(withAnimations, role = Role.Switch, onValueChange = { withAnimations = it }).minTouch(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Include exercise animations ($megabytes MB)", Modifier.weight(1f))
                    Switch(withAnimations, null)
                }
            },
            confirmButton = { TextButton(onClick = { backupChoice = false; saveBackup.launch("Clean backup ${LocalDate.now()}.zip") }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { backupChoice = false }) { Text("Cancel") } },
        )
    }
    wipe?.let { now ->
        CleanDialog(
            onDismissRequest = { wipe = null },
            title = { Text("Delete everything?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("This removes profiles: ${now.profiles.size}, workouts: ${now.workouts}, templates: ${now.templates}, weigh-ins: ${now.weighIns}, every option, and the imported animations. It cannot be undone.")
                    QuietButton("Back up first", { wipe = null; startBackup() }, Modifier.fillMaxWidth())
                    SlideToConfirm("Slide to delete everything", "Delete everything", {
                        wipe = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO + NonCancellable) { Backup.wipe(context) }
                            } catch (e: Exception) {
                                model.message = e.message ?: "That did not work"
                                return@launch
                            }
                            Backup.restart(context)
                        }
                    }, Modifier.fillMaxWidth())
                    TextButton(onClick = { wipe = null }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            },
            confirmButton = {},
        )
    }
}

/** The two ways of bringing history in: pick a file of workouts or measurements, or pick a Clean backup. */
class HistoryIn(val pickImport: () -> Unit, val pickRestore: () -> Unit)

/**
 * The Import and Restore flows of Settings, Data, for Settings and for the welcome questionnaire: a picked file is looked at, a summary is
 * shown (and the unit asked for when the file does not say), and nothing is written until the user confirms. [onImported] runs once an
 * import has been written (a restore ends in a restart instead).
 */
@Composable
fun rememberHistoryIn(model: AppModel, onImported: () -> Unit = {}): HistoryIn {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by model.profile.collectAsState()
    var workoutImport by remember { mutableStateOf<WorkoutImport?>(null) }
    var measurementImport by remember { mutableStateOf<MeasurementImport?>(null) }
    // A picked backup is inspected and held until the user confirms that it replaces everything.
    var restore by remember { mutableStateOf<PendingRestore?>(null) }
    // A file whose weights carry no unit, held until the user says kg or lb.
    var awaitingUnit by remember { mutableStateOf<ImportFile?>(null) }

    /** Runs [work] for the active profile and reports anything that goes wrong instead of crashing. */
    fun task(work: suspend (Long) -> Unit) {
        val id = profile?.id ?: return
        scope.launch {
            try {
                work(id)
            } catch (e: Exception) {
                model.message = e.message ?: "That did not work"
            }
        }
    }
    suspend fun offerRestore(file: File) {
        try {
            restore = withContext(Dispatchers.IO) { PendingRestore(file, Backup.inspect(context, file), Backup.current(context)) }
        } catch (e: Exception) {
            file.delete()
            model.message = e.message ?: "That backup could not be read"
        }
    }
    val pickRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                offerRestore(withContext(Dispatchers.IO) { Backup.copyToCache(context, context.contentResolver.openInputStream(uri)!!) })
            } catch (e: Exception) {
                model.message = e.message ?: "That file could not be read"
            }
        }
    }
    val pickImport = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) task { id ->
            // A Clean backup is a zip too, and goes to the restore instead.
            model.message = "Reading the file…"
            val picked = withContext(Dispatchers.IO) { Backup.copyToCache(context, context.contentResolver.openInputStream(uri)!!) }
            if (withContext(Dispatchers.IO) { Backup.isBackup(picked) }) offerRestore(picked)
            else {
                val bytes = withContext(Dispatchers.IO) { picked.readBytes().also { picked.delete() } }
                model.message = "Reading ${"%,d".format(maxOf(1, bytes.size / 1024))} KB…"
                // Strong's measurements zip is the one thing here that is not a workout file.
                val measurements = withContext(Dispatchers.Default) { runCatching { model.repo.previewMeasurements(id, bytes) }.getOrNull() }?.takeIf { it.files.isNotEmpty() }
                if (measurements != null) measurementImport = measurements
                else {
                    val file = withContext(Dispatchers.Default) { readImportFile(bytes) }
                    if (file.source != ImportSource.STRONG && withContext(Dispatchers.Default) { needsUnit(file.text) }) awaitingUnit = file
                    else workoutImport = model.repo.previewImport(id, file, profile?.unit == "lb")
                }
            }
        }
    }
    awaitingUnit?.let { file ->
        // Tapping outside cancels; the two buttons are the answers.
        CleanDialog(
            onDismissRequest = { awaitingUnit = null },
            title = { Text("Which unit are the weights in?") },
            text = { Text("This file does not say whether its weights are kilograms or pounds.") },
            confirmButton = { TextButton(onClick = { awaitingUnit = null; task { workoutImport = model.repo.previewImport(it, file, false) } }) { Text("kg") } },
            dismissButton = { TextButton(onClick = { awaitingUnit = null; task { workoutImport = model.repo.previewImport(it, file, true) } }) { Text("lb") } },
        )
    }
    workoutImport?.let { import ->
        ImportDialog(
            title = import.label,
            lines = listOfNotNull(
                "${import.workouts} workouts to add (${import.rows.count { it.setOrder != "Note" && it.setOrder != "Rest Timer" }} sets)",
                if (import.from != null) "From ${prettyDay(import.from)} to ${prettyDay(import.to!!)}" else "Nothing new in this file",
                if (import.newExercises > 0) "${import.newExercises} exercises new to this profile" else null,
                if (import.weights.isNotEmpty()) "${import.weights.size} weigh-ins to add" else null,
                "${import.alreadyThere} workouts already here, left as they are",
            ) + import.skipped.map { (why, n) -> "$n left out: $why" },
            canImport = import.rows.isNotEmpty() || import.weights.isNotEmpty(),
            onConfirm = {
                task {
                    model.message = "Adding ${plural(import.workouts, "workout")}…"
                    model.repo.commit(it, import)
                    model.message = "${import.workouts} workouts imported" + if (import.weights.isEmpty()) "" else " and ${import.weights.size} weigh-ins"
                    onImported()
                }
            },
            onDismiss = { workoutImport = null },
        )
    }
    measurementImport?.let { import ->
        ImportDialog(
            title = "Import measurements",
            lines = listOf(
                "${import.files.size} files in the zip",
                "${import.newEntries.size} entries to add",
                "${import.alreadyThere} entries already here, left as they are",
            ),
            canImport = import.newEntries.isNotEmpty(),
            onConfirm = { task { model.repo.commit(it, import); model.message = "${import.newEntries.size} entries imported"; onImported() } },
            onDismiss = { measurementImport = null },
        )
    }
    restore?.let { r ->
        val found = r.found.contents
        CleanDialog(
            onDismissRequest = { r.file.delete(); restore = null },
            title = { Text("Restore this backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    if (r.found.manifest.created.isNotEmpty()) Text("Made ${prettyDay(r.found.manifest.created.take(10))}")
                    Text("Profiles: ${found.profiles.joinToString(", ").ifEmpty { "none" }}")
                    Text("Workouts: ${found.workouts}" + if (found.first != null) ", ${prettyDay(found.first)} to ${prettyDay(found.last!!)}" else "")
                    Text("Weigh-ins: ${found.weighIns}, templates: ${found.templates}")
                    if (r.found.animations) Text("With exercise animations")
                    Text("This replaces everything on this phone now (profiles: ${r.now.profiles.size}, workouts: ${r.now.workouts}, with their templates, options and imported files). It cannot be undone.")
                    SlideToConfirm("Slide to replace everything", "Restore this backup and replace everything", {
                        restore = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO + NonCancellable) { Backup.apply(context, r.file) }
                            } catch (e: Exception) {
                                model.message = e.message ?: "That did not work"
                                return@launch
                            } finally {
                                r.file.delete()
                            }
                            Backup.restart(context)
                        }
                    }, Modifier.fillMaxWidth())
                    TextButton(onClick = { r.file.delete(); restore = null }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            },
            confirmButton = {},
        )
    }
    return HistoryIn({ pickImport.launch(arrayOf("*/*")) }, { pickRestore.launch(arrayOf("*/*")) })
}

/** A backup that has been looked at, with what is on the phone now, waiting for the user to confirm the replacement. */
private class PendingRestore(val file: File, val found: Backup.Inspected, val now: Backup.Contents)

/** The formats the export sheet offers; Strong's is the exact one and comes first. */
private enum class ExportKind(val label: String, val file: String) {
    STRONG("Strong CSV (the exact format)", "clean_workouts_"), HEVY("Hevy CSV", "clean_hevy_"), FITNOTES("FitNotes CSV", "clean_fitnotes_"),
    PLAIN("Plain CSV", "clean_plain_"), JSON("JSON, every field of every row", "clean_rows_"),
}

/** The file's bytes for [kind]; only Strong's reads the rows straight out, the others are built from the workouts they make. */
private suspend fun exportBytes(model: AppModel, profileId: Long, kind: ExportKind): ByteArray {
    if (kind == ExportKind.STRONG) return model.repo.exportWorkouts(profileId)
    val rows = model.repo.rows(profileId)
    return withContext(Dispatchers.Default) {
        when (kind) {
            ExportKind.JSON -> exportJson(rows)
            ExportKind.HEVY -> exportHevy(buildWorkouts(rows))
            ExportKind.FITNOTES -> exportFitNotes(buildWorkouts(rows))
            else -> exportPlainCsv(buildWorkouts(rows), model.oneRm.value)
        }.toByteArray()
    }
}

/** A switch for an on/off option stored under [key]; [enabled] false greys it and takes no tap. */
@Composable
private fun Toggle(model: AppModel, key: String, title: String, about: String = "", default: Boolean = true, inverted: Boolean = false, enabled: Boolean = true, onSwitch: (Boolean) -> Unit = { model.setOption(key, if (it != inverted) "on" else "off") }) {
    // [inverted]: the switch shows on while the stored flag is off.
    val on = model.flag(key, default) != inverted
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    // The whole row is the switch, read out as one; the drawn switch only shows the state.
    Row(Modifier.toggleable(on, enabled = enabled, role = Role.Switch, onValueChange = onSwitch).minTouch(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else quiet.copy(alpha = 0.6f))
            if (about.isNotEmpty()) Text(about, style = MaterialTheme.typography.bodySmall, color = quiet)
        }
        Switch(on, null, enabled = enabled)
    }
}

/** A colour on offer, named for a screen reader; the 48 dp hit area is laid over the smaller swatch it sits in. */
@Composable
internal fun Swatch(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.requiredSize(48.dp).clip(CircleShape).selectable(selected, role = Role.RadioButton, onClick = onClick).semantics { contentDescription = name })
}

@Composable
private fun ImportDialog(title: String, lines: List<String>, canImport: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    CleanDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(Space.s)) { for (line in lines) Text(line) } },
        confirmButton = { TextButton(enabled = canImport, onClick = { onConfirm(); onDismiss() }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The notice the MIT License asks to be kept with MuscleMap's outlines (the body figure). */
private const val MUSCLEMAP_LICENCE = """MIT License

Copyright (c) 2026 Melih Colpan

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE."""

/** The four pages of Settings (D4, F5): their names are also the page ids the search and the Profile and Home buttons use. */
const val PAGE_WORKOUT = "Workout"
const val PAGE_APPEARANCE = "Appearance"
const val PAGE_DATA = "Data and backup"
const val PAGE_ABOUT = "About and help"

/** The main page's four rows with their one-line hints. */
private val SETTINGS_PAGES = listOf(
    PAGE_WORKOUT to "Rest, weekly target, Up next, estimated 1RM, auto-fill",
    PAGE_APPEARANCE to "Theme, accent, text size, transparency, animations",
    PAGE_DATA to "Backup, restore, import, export, Health Connect, delete everything",
    PAGE_ABOUT to "What's new, source code, licences and credits, tips",
)

/** The theme choices by their stored word. */
private val THEME_NAMES = mapOf("dark" to "Dark", "light" to "Light", "phone" to "Phone")

/** The public repository; shown as text (to read or copy) under Settings, About and help. */
const val SOURCE_URL = "https://github.com/rohan3412/clean-workout-tracker"

private val TIPS = listOf(
    "Tap a set's Previous to copy last time's weight and reps into it.",
    "Tap the PR badge on a ticked set to see the best it beat.",
    "Hold an exercise's name in a workout, then drag it to move it.",
    "Swipe a set to the left to remove it; Undo is offered for a few seconds.",
    "Tap a set's number to change its type: normal, warm-up, failure or drop.",
    "Hold a day in the calendar to log a workout you did not log at the time.",
    "Pinch the body figure to zoom; a double tap puts it back.",
    "Swipe the figure sideways to go between Balance, Fatigue and Strength.",
    "Open the arrow on a muscle's row for its heads.",
    "Tap the ring on the Home tab for Trophies.",
    "clean://start, clean://start?template=Name and clean://open?tab=history open the app from a link, and the launcher icon's shortcuts start a workout.",
)
