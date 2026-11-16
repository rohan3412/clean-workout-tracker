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
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Typeface
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
import androidx.compose.material3.RadioButton
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
import clean.app.data.Profile
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
import clean.core.ChartStyle
import clean.core.chartStyleOf
import clean.core.DEFAULT_REST_HIDE
import clean.core.REST_HIDE_CHOICES
import clean.core.REST_PRESET_COUNT
import clean.core.RestEndFeedback
import clean.core.parseRestPresets
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
    val profiles by model.profiles.collectAsState()
    val profile by model.profile.collectAsState()
    val palette by model.palette.collectAsState()
    val accent by model.accent.collectAsState()
    val background by model.background.collectAsState()
    // Which colour the wheel is open for: "accent", "background" or "palette".
    var wheel by remember { mutableStateOf<String?>(null) }
    var credits by remember { mutableStateOf(false) }
    var datasetCredits by remember { mutableStateOf(false) }
    val textSize by model.textSize.collectAsState()
    val font by model.font.collectAsState()
    val auto by model.auto.collectAsState()
    val oneRm by model.oneRm.collectAsState()
    val restDefault by model.restDefault.collectAsState()
    val bannerScope by model.bannerScope.collectAsState()
    val exerciseTab by model.workoutExerciseTab.collectAsState()
    val stickyColor by model.stickyColor.collectAsState()
    val saveTemplates = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            writeBytes(context, uri, model.templatesFile().toByteArray())
            model.message = "Templates exported"
        }
    }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Profile?>(null) }

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
    val pickFont = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                // Copied into the app's own folder, so it keeps working if the original is moved or deleted.
                val file = File(context.filesDir, "font-${System.currentTimeMillis()}")
                val bytes = readBytes(context, uri)
                withContext(Dispatchers.IO) { file.writeBytes(bytes) }
                if (Typeface.Builder(file).build() == null) {
                    file.delete()
                    model.message = "That file is not a font. Pick a .ttf or .otf file."
                } else {
                    context.filesDir.listFiles { f -> f.name.startsWith("font-") && f != file }?.forEach { it.delete() }
                    model.setOption("font", file.path + "|" + (uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Imported font"))
                }
            } catch (e: Exception) {
                model.message = e.message ?: "That did not work"
            }
        }
    }

    // Settings is a short list of headings; each opens its own page of options.
    var page by remember { mutableStateOf(model.settingsPage.also { model.settingsPage = null }) }
    BackHandler(enabled = page != null) { page = null }
    val motion = LocalMotion.current
    val openPage: (String) -> Unit = { page = it }
    // A search over every section; a result opens its page scrolled to that section.
    var query by rememberSaveable { mutableStateOf("") }
    var jump by remember { mutableStateOf<String?>(null) }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar(page ?: "Settings", { if (page != null) page = null else model.back() }) }) { padding ->
        SettingsFloat(model, Modifier.padding(padding).fillMaxSize()) {
        // The list and its pages swap like pages do: into a page is a step in, back to the list is a step out. The outgoing page keeps showing what it showed.
        AnimatedContent(
            page,
            transitionSpec = {
                Motion.blinkIn(motion) togetherWith Motion.blinkOut(motion)
            },
            label = "settings page",
        ) { page ->
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
        LazyColumn(Modifier.fillMaxSize(), listState, contentPadding = PaddingValues(bottom = settingsFloatSpace(model))) {
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
                    // Inset like the search box above; LinkRow has no side padding of its own (inside a card the card gives it).
                    Box(Modifier.padding(horizontal = Space.l)) { LinkRow(place.title, place.page, "") { jump = place.key; query = ""; openPage(place.page) } }
                }
            }
            if (page == null && query.isBlank()) {
                items(SETTINGS_PAGES) { (title, about) ->
                    Row(Modifier.fillMaxWidth().clickable { openPage(title) }.padding(horizontal = Space.l, vertical = Space.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                        val icon = when (title) {
                            "Profile" -> CleanIcons.Person
                            "Workout" -> CleanIcons.Dumbbell
                            "Appearance" -> CleanIcons.StarLine
                            "Lists" -> CleanIcons.Notes
                            "Data" -> CleanIcons.Folder
                            "Tips" -> CleanIcons.Bolt
                            else -> CleanIcons.Info
                        }
                        // Linen sets the icon in a small grey circle.
                        val circles = clean.app.ui.theme.LocalLook.current.iconCircles
                        Box(Modifier.size(if (circles) 36.dp else 40.dp).background(if (circles) clean.app.ui.theme.LocalLook.current.tile else MaterialTheme.colorScheme.surfaceContainer, if (circles) CircleShape else cardShape()), contentAlignment = Alignment.Center) {
                            Icon(icon, null, Modifier.size(if (circles) 20.dp else 22.dp), tint = if (circles) clean.app.ui.theme.LocalLook.current.text else MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(CleanIcons.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            if (page == "Profile") profile?.let { p ->
                item(key = "s-profile:${p.id}") {
                    Section("This profile") {
                        var name by remember { mutableStateOf(p.name) }
                        var height by remember { mutableStateOf(p.heightCm?.let(::shortNumber) ?: "") }
                        OutlinedTextField(name, { name = it; if (it.isNotBlank()) model.update(p.copy(name = it.trim())) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                            Text("Weights in")
                            Choice(listOf("kg", "lb"), p.unit, { it }) { model.update(p.copy(unit = it)) }
                        }
                        OutlinedTextField(
                            height, { height = it; model.update(p.copy(heightCm = parseNumber(it))) },
                            label = { Text("Height (cm)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (page == "Profile") item(key = "s-profiles") {
                Section("Profiles") {
                    for (p in profiles) {
                        Row(Modifier.fillMaxWidth().clickable { model.switchTo(p.id) }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = p.id == profile?.id, onClick = { model.switchTo(p.id) })
                            Text(p.name, Modifier.weight(1f))
                            if (profiles.size > 1) IconButton(onClick = { deleting = p }) { Icon(CleanIcons.Delete, "Delete profile") }
                        }
                    }
                    QuietButton("New profile", { adding = true })
                }
            }
            if (page == "Data") item(key = "s-backup") {
                Section("Backup") {
                    Text(
                        "One zip with everything in Clean. Restoring it replaces what is on the phone. Keep it somewhere off the phone.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    QuietButton(if (backingUp) "Backing up…" else "Back up everything", { startBackup() }, Modifier.fillMaxWidth(), enabled = !backingUp)
                    QuietButton("Restore a backup", { history.pickRestore() }, Modifier.fillMaxWidth())
                }
            }
            if (page == "Data") item(key = "s-import") {
                Section("Import into ${profile?.name ?: "profile"}") {
                    Text(
                        "From Strong, Hevy, Lyfta, Fitbod, FitNotes, GymMane or openGym, or any CSV with a date, an exercise and reps; a zip with one of them in it works too, and so does Strong's measurements zip. A Clean backup picked here is restored instead. Clean says what the file is and what it would add before anything is saved.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    QuietButton("Import", { history.pickImport() }, Modifier.fillMaxWidth())
                }
            }
            if (page == "Data") item(key = "s-export") {
                Section("Export from ${profile?.name ?: "profile"}") {
                    Text("Strong's format is the exact one, so the file opens anywhere a Strong export does. The other formats hold only what those apps have room for.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    QuietButton("Workouts", { exporting = true }, Modifier.fillMaxWidth())
                    QuietButton("Measurements", { saveMeasurements.launch("clean_measurements_${LocalDate.now()}.zip") }, Modifier.fillMaxWidth())
                    Toggle(model, "export.rest", "Write rest timers", "A row after each set with the rest that followed it, as older Strong exports have. Applies to workouts finished from now on.", default = false)
                }
            }
            if (page == "Data") item(key = "s-health") {
                Section("Health Connect") {
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
            if (page == "Data") item(key = "s-animations") {
                Section("Exercise animations") {
                    Text(
                        "The steps on an exercise's Info tab can have a moving picture above them. The pictures are © Gym visual and are not part of Clean: for your own use, download the zip of github.com/hasaneyldrm/exercises-dataset (Code, Download ZIP, about 130 MB) in a browser and pick it here. Only the pictures are copied in; the app still never goes online.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LabelValue("On this phone", if (animations == 0) "none" else "$animations")
                    QuietButton(if (importing) "Importing…" else "Import animations from a zip", { pickAnimations.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth(), enabled = !importing)
                    if (animations > 0) DangerButton("Remove the animations", { Animations.clear(context); animations = 0 })
                }
            }
            if (page == "Data") item(key = "s-templates") {
                Section("Templates") {
                    Text("Strong's export has no templates, so they travel in a file of Clean's own, folders included. The same two actions are in the Workout tab's ⋯ menu.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    QuietButton("Export templates", { saveTemplates.launch("clean_templates_${LocalDate.now()}.json") }, Modifier.fillMaxWidth())
                    QuietButton("Import templates", { model.importTemplatesNow = true; model.stack.clear(); model.tab = Tab.Workout }, Modifier.fillMaxWidth())
                }
            }
            if (page == "Appearance") item(key = "ap-preset") { PresetSection(model) }
            if (page == "Appearance") item(key = "ap-mono") { MonoSection(model) }
            if (page == "Appearance") item(key = "ap-daynight") { DayNightSection(model) }
            if (page == "Appearance") item(key = "ap-mix") { MixSection(model) { wheel = it } }
            if (page == "Appearance") item(key = "ap-home") {
                Section("Home") {
                    HomeOptions(model)
                    LinkRow("Arrange home", "Move, resize, hide or add the cards on Home", "") { model.open(Screen.EditHome) }
                    LinkRow("Arrange profile", "Move, resize, hide or add the cards on Profile", "") { model.open(Screen.EditProfile) }
                }
            }
            if (page == "Appearance") item(key = "ap-tabs") {
                Section("Tab bar") { LinkRow("Tab order", "Where each tab sits; Workout stays in the middle", "") { model.open(Screen.Navigation) } }
            }
            if (page == "Appearance") item(key = "ap-charts") {
                val stored by model.options.collectAsState()
                Section("Charts") {
                    Text("How the charts on exercise, weight, trends and template pages draw their figures", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choice(ChartStyle.entries.toList(), chartStyleOf(stored["chart.style"]), { it.label }) { model.setOption("chart.style", it.key) }
                    Text(
                        "Line is the default. Bars draw one bar per point, a left and right session as two bars. Smoothed draws a 5-point moving average with the points themselves faint. The small lines on Home's tiles stay lines.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (page == "Appearance") item(key = "ap-customize") {
                Section("Customize") {
                    // "Preset's own" clears the override, so the part is the preset's value again (2026-10-16).
                    val stored by model.options.collectAsState()
                    Text("Theme", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choice(listOf(PRESET_OWN, "Dark", "Light", "System"), stored["theme"] ?: PRESET_OWN, { it }) { model.setOption("theme", it.takeIf { v -> v != PRESET_OWN }) }
                    Text("Sticky notes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        for (name in STICKY_COLORS.keys) {
                            val on = name == stickyColor || (stickyColor.isEmpty() && name == STICKY_COLORS.keys.first())
                            Box(
                                Modifier.size(34.dp).background(stickyBand(name, clean.app.ui.theme.LocalLook.current.dark).inLook(), CircleShape)
                                    .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Swatch(name, on) { model.setOption("sticky.color", name) } }
                        }
                        WheelSwatch(34, stickyColor.startsWith("#")) { wheel = "sticky.color" }
                    }
                    Text("Dark background", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choice(listOf(PRESET_OWN, "Black", "Dark grey", "Custom"), if (stored["background"] == null) PRESET_OWN else if (background.startsWith("#")) "Custom" else background, { it }) {
                        if (it == "Custom") wheel = "background" else model.setOption("background", it.takeIf { v -> v != PRESET_OWN })
                    }
                    Text("Text size", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choice(listOf("Smallest", "Small", "Medium", "Large", "Largest"), textSize, { it }) { model.setOption("textSize", it) }
                    Text("The spacing of cards and lists follows the text: smaller text packs more onto a screen, larger text gives it room.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Font", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(font?.substringAfter('|') ?: "The preset's own")
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        QuietButton("Import a font file", { pickFont.launch(arrayOf("*/*")) })
                        if (font != null) TextButton(onClick = {
                            font?.substringBefore('|')?.let { File(it).delete() }
                            model.setOption("font", null)
                        }) { Text("Use the preset's") }
                    }
                    Text("A .ttf or .otf file from this phone. It is copied into the app, nothing is downloaded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Toggle(model, "ui.motion", "Animations", "Pages sliding in, buttons dipping when pressed, sets and cards gliding into place.")
                    Toggle(model, PREVIEW_LIVE, "Live preview", "After you change how something looks, a small pop-up shows it before and after.")
                    Toggle(model, "photos.blur", "Blur progress photos", "On Profile and in the photo list. A photo is sharp once you open it.", default = false)
                }
            }
            if (page == "Workout") item(key = "s-autofill") {
                Section("Auto-fill sets") {
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
            if (page == "Workout") item(key = "s-consistency") {
                Section("Consistency") {
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
                    Toggle(model, "reward.celebrate", "Celebrate on finishing", "A target reached, a new badge or a shield earned shows at the top of the summary, with a chime and two taps. Silent with animations off.")
                }
            }
            if (page == "Workout") item(key = "s-weeksets") {
                Section("Weekly sets per muscle") {
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
            if (page == "Workout") item(key = "s-onerm") {
                Section("Estimated 1RM") {
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
            if (page == "Workout") item(key = "s-layout") {
                val stored by model.options.collectAsState()
                Section("Layout") {
                    Text("Set rows", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val rows = listOf("comfortable" to "Comfortable", "compact" to "Compact")
                    Choice(rows, rows.firstOrNull { it.first == stored["workout.rows"] } ?: rows[0], { it.second }) { model.setOption("workout.rows", it.first) }
                    Text(
                        "Comfortable shows last time's set beside each set. Compact leaves that column out and packs the rows closer; last time still shows in grey in the empty boxes.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val swipes = listOf("finish" to "Finish", "delete" to "Delete", "off" to "Off")
                    Text("Swipe a set right", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choice(swipes, swipes.firstOrNull { it.first == stored["swipe.right"] } ?: swipes[0], { it.second }) { model.setOption("swipe.right", it.first) }
                    Text("Swipe a set left", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choice(swipes, swipes.firstOrNull { it.first == stored["swipe.left"] } ?: swipes[1], { it.second }) { model.setOption("swipe.left", it.first) }
                    Text(
                        "Finish ticks the set as its box does and buzzes at the threshold; swiping a done set un-ticks it. Delete can be undone from the bar at the bottom. In a left and right pair a swipe acts on one half at a time.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Toggle(model, "workout.focus", "Focus mode", "One exercise at a time, its next set large with a Done button. Swipe for the next exercise; All in the top bar returns to the list.", default = false)
                    Toggle(model, "workout.oneHand", "One-handed mode", "The workout starts a third of the way down the screen, within thumb reach, and the Done button is taller.", default = false)
                }
            }
            if (page == "Workout") item(key = "s-rest") {
                val stored by model.options.collectAsState()
                Section("Rest timer") {
                    Text("While resting, the countdown is shown as", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val styles = listOf("full" to "Full screen", "ring" to "Ring", "bar" to "Slim bar", "hide" to "Full screen then hide")
                    Choice(styles, styles.firstOrNull { it.first == stored["rest.style"] } ?: styles[0], { it.second }) { model.setOption("rest.style", it.first) }
                    if ((stored["rest.style"] ?: "full") == "hide") {
                        Text("Full screen for", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Choice(REST_HIDE_CHOICES, stored["rest.hideAfter"]?.toIntOrNull() ?: DEFAULT_REST_HIDE, { "$it s" }) { model.setOption("rest.hideAfter", it.toString()) }
                        Text("Then it shrinks to a small pill at the top; a tap brings it back, and it returns by itself for the last 10 seconds.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Rest again for", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PresetFields(parseRestPresets(stored["rest.presets"])) { model.setOption("rest.presets", it.joinToString(",")) }
                    Text(
                        "Four lengths in seconds, shown as chips while resting: a tap starts the rest again at that length. An exercise can have its own rest length, from its options in a workout (Rest timer).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Notification", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val forms = listOf("expanded" to "Expanded", "compact" to "Compact")
                    Choice(forms, forms.firstOrNull { it.first == stored["rest.notif"] } ?: forms[0], { it.second }) { model.setOption("rest.notif", it.first) }
                    Text(
                        "With the app behind (and the alert in the background switched on), the rest shows on the lock screen. Expanded adds a bar and the next set; the arrow on it switches form.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (page == "Workout") item(key = "s-feedback") {
                val stored by model.options.collectAsState()
                Section("Feedback") {
                    Text("When a set is ticked", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val ticks = listOf("off" to "Off", "light" to "Light", "strong" to "Strong")
                    Choice(ticks, ticks.firstOrNull { it.first == stored["feedback.tick"] } ?: ticks[1], { it.second }) { model.setOption("feedback.tick", it.first); if (it.first == "strong") Feedback.pulse(40) }
                    Text("When the rest ends", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val ends = listOf(RestEndFeedback.SOUND, RestEndFeedback.VIBRATE, RestEndFeedback.BOTH)
                    val end = restEndFeedback(stored["feedback.restEnd"], stored["rest.sound"], stored["rest.alert"])
                    Choice(ends, end, { it.key.replaceFirstChar(Char::uppercase) }) { model.setOption("feedback.restEnd", it.key) }
                    Text(
                        "Sound is the bell at zero, at the phone's media volume, so it comes through headphones. Vibrate is a pulse on each of the last five seconds, the last one longer.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Picking a sound plays it.
                    if (end.sound) Choice(RestTone.entries, RestTone.of(stored["rest.tone"]), { it.label }) { model.setOption("rest.tone", it.key); Feedback.bell(it) }
                    Text("When a set earns a PR", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val celebrations = listOf("none" to "None", "glow" to "Glow", "confetti" to "Confetti")
                    Choice(celebrations, celebrations.firstOrNull { it.first == stored["pr.celebrate"] } ?: celebrations[0], { it.second }) { model.setOption("pr.celebrate", it.first) }
                    Text("The set lights up in the accent, or a few pieces fly from it. Never with animations off.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Toggle(model, "pr.moment", "Sound and buzz on a new PR", "A soft chime and two taps when a set earns a new estimated 1RM. Silent with animations off.")
                }
            }
            if (page == "Workout") item(key = "s-outside") {
                Section("Outside the app") { LinkRow("Launcher shortcuts", shortcutsName(model.options.collectAsState().value["shortcuts"]), "") { model.open(Screen.Shortcuts) } }
            }
            if (page == "Workout") item(key = "s-tplcolors") { TemplateColorSettings(model) }
            if (page == "Workout") item(key = "s-during") {
                Section("During a workout") {
                    RestChoice("Rest after a set", restDefault, listOf(0, 60, 90, 120, 180, 300), { model.setOption("rest.seconds", it.toString()) })
                    Text("An exercise can have its own length, from its options in a workout.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // A kind of set can have a rest of its own; "Same" follows the length above, and a warm-up left alone rests half of it.
                    val stored by model.options.collectAsState()
                    for ((key, label) in listOf("rest.warmup" to "After a warm-up set", "rest.failure" to "After a set to failure", "rest.drop" to "After a drop set")) {
                        val own = stored[key]?.toIntOrNull() ?: -1
                        RestChoice(
                            label, own, if (key == "rest.warmup") listOf(-1, 0, 15, 30, 60, 90, 120, 180, 300) else listOf(-1, 0, 30, 60, 90, 120, 180, 300),
                            { model.setOption(key, if (it < 0) null else it.toString()) },
                            value = if (own >= 0) restLabel(own) else if (key == "rest.warmup") "Half · ${restLabel(restDefault / 2)}" else "Same · ${restLabel(restDefault)}",
                            unset = if (key == "rest.warmup") "Half" else "Same",
                        )
                    }
                    RestChoice("Between sides", stored["rest.sides"]?.toIntOrNull() ?: 60, listOf(0, 30, 60, 90, 120), { model.setOption("rest.sides", it.toString()) })
                    Text("After the first side of a left and right exercise, before the other", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Toggle(
                        model, "rest.background", "Alert when the app is in the background",
                        "An alarm on the phone buzzes through the last five seconds and rings the bell as the rest ends, with a notification counting down. Nothing goes online.", default = false,
                    ) { on ->
                        if (!on) model.setOption("rest.background", "off")
                        else if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else model.setOption("rest.background", "on")
                    }
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
                    val hard = stored["rest.hard"]?.toIntOrNull() ?: 0
                    RestChoice("Extra rest after a hard set", hard, listOf(0, 15, 30, 60), { model.setOption("rest.hard", it.toString()) })
                    Text("RPE 9 or more, or a set to failure", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Toggle(model, "rest.return", "Go back to the workout when the rest ends")
                    Text("Tapping an exercise's name in a workout opens its", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choice(EXERCISE_TABS.indices.toList(), exerciseTab, { EXERCISE_TABS[it] }) { model.setOption("workout.exerciseTab", it.toString()) }
                    Toggle(model, "workout.summary", "Summary when a workout is finished", "Duration, volume, PRs and muscles, next to the last time you did the same routine.")
                    Toggle(model, "workout.rpe", "RPE on the number pad", "A key for how hard a set felt, 6 to 10. It is saved in the export's RPE column.")
                    Toggle(model, "workout.highlight", "Show the next set", "The set to do next is marked and scrolled to. In a superset the turn alternates.")
                    Toggle(model, "workout.hint", "Suggest what to try", "Under an exercise's name: last time's best set and the set that would beat your best. Not shown where a progression is set.")
                    Toggle(model, "workout.compact", "Shrink finished exercises", "An exercise with every set ticked takes a line or two until touched.")
                    Text("“Workout in progress” bar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Choice(listOf("Everywhere", "Main tabs", "Off"), bannerScope, { it }) { model.setOption("banner", it) }
                }
            }
            if (page == "Data") item(key = "s-renamed") { RenamedExercises(model) }
            if (page == "Tips") item(key = "s-tips") {
                Section("Gestures worth knowing") {
                    for (tip in TIPS) Text(tip)
                }
            }
            if (page == "Data") item(key = "s-tidy") {
                Section("Tidy up") {
                    Text("Notes such as “Wide grip” can be turned into variations, so those sets get their own history and PRs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    QuietButton("Turn notes into variations", { model.open(Screen.NoteReview()) }, Modifier.fillMaxWidth())
                }
            }
            if (page == "Data") item(key = "s-delete") {
                Section("Delete everything") {
                    Text(
                        "Takes every profile, workout, template, measurement, option and imported file off this phone, and Clean starts as when it was first installed.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DangerButton("Delete everything…", { scope.launch { wipe = withContext(Dispatchers.IO) { Backup.current(context) } } })
                }
            }
            if (page == "Lists") item(key = "s-lists") {
                Section("Lists") {
                    Toggle(model, "library.hide", "Hide built-in exercises I haven't used", "They still turn up when you search or filter.")
                    Toggle(model, "exercise.plateau", "Point out stalled exercises", "A line on an exercise's page when its last five sessions set no new best estimated 1RM.")
                    Toggle(model, "calendar.weeks", "Week totals in the calendar")
                }
            }
            if (page == "Credits") item(key = "s-source") {
                Section("Source code") {
                    Text("Clean is open source under the MIT License. The code is at $SOURCE_URL.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // Handed to the phone's browser; Clean itself still has no internet access. Without a browser the address is copied instead.
                    TextButton(onClick = {
                        try {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
                        } catch (e: android.content.ActivityNotFoundException) {
                            (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                .setPrimaryClip(android.content.ClipData.newPlainText("Source code", SOURCE_URL))
                            model.message = "No browser found. The address is copied."
                        }
                    }) { Text("Open in the browser") }
                }
            }
            if (page == "Credits") item(key = "s-changes") {
                // Every version; the pop-up after an update shows only the newest.
                val releases = remember { readChanges(context) }
                if (releases.isNotEmpty()) Section("What's new") { ChangeList(releases) }
            }
            if (page == "Credits") item(key = "s-credits") {
                Section("Credits") {
                    Text("The body figure is drawn from MuscleMap by Melih Colpan, used under the MIT License.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { credits = true }) { Text("Show the licence") }
                    Text("The steps on an exercise's Info tab are from exercises-dataset by Hasan Emir Yıldırım (github.com/hasaneyldrm/exercises-dataset), used under the MIT License. None of that dataset's pictures or animations are in the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { datasetCredits = true }) { Text("Show the licence") }
                    Text("The numbers of the Mono look are set in IBM Plex Mono, Copyright 2017 IBM Corp. with Reserved Font Name \"Plex\", used under the SIL Open Font License, Version 1.1 (openfontlicense.org). The font files are unchanged.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("The text and numbers of the Linen look are set in Inter, Copyright 2020 The Inter Project Authors (github.com/rsms/inter), used under the SIL Open Font License, Version 1.1 (openfontlicense.org). The font file is unchanged.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Some icons are Google's Material icons, used under the Apache License 2.0 (apache.org/licenses/LICENSE-2.0).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        val initial = when (target) {
            "accent" -> ACCENTS[accent]?.first ?: parseColor(accent)
            "background" -> if (background == "Dark grey") Color(0xFF121316) else parseColor(background)
            "sticky.color" -> STICKY_COLORS[stickyColor] ?: parseColor(stickyColor) ?: STICKY_COLORS.values.first()
            else -> parseColor(palette) ?: paletteOf(palette).last()
        } ?: MaterialTheme.colorScheme.primary
        ColorPickerDialog(initial, onPick = { model.setOption(target, it.hex()) }) { wheel = null }
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
                    Text("This removes profiles: ${now.profiles.size}, workouts: ${now.workouts}, templates: ${now.templates}, weigh-ins: ${now.weighIns}, every option, and the imported font and animations. It cannot be undone.")
                    QuietButton("Back up first", { wipe = null; startBackup() }, Modifier.fillMaxWidth())
                    DangerButton("Delete everything", {
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
    if (adding) {
        var name by remember { mutableStateOf("") }
        CleanDialog(
            onDismissRequest = { adding = false },
            title = { Text("New profile") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { model.createProfile(name.trim()); adding = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
    deleting?.let { p ->
        Confirm("Delete ${p.name}?", "All of this profile's workouts and measurements are removed from this phone. Export first if you want to keep them.", "Delete", { model.deleteProfile(p.id) }) { deleting = null }
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
            val picked = withContext(Dispatchers.IO) { Backup.copyToCache(context, context.contentResolver.openInputStream(uri)!!) }
            if (withContext(Dispatchers.IO) { Backup.isBackup(picked) }) offerRestore(picked)
            else {
                val bytes = withContext(Dispatchers.IO) { picked.readBytes().also { picked.delete() } }
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
                    if (r.found.animations || r.found.font) Text("With " + listOfNotNull("exercise animations".takeIf { r.found.animations }, "an imported font".takeIf { r.found.font }).joinToString(" and "))
                    Text("This replaces everything on this phone now (profiles: ${r.now.profiles.size}, workouts: ${r.now.workouts}, with their templates, options and imported files). It cannot be undone.")
                    DangerButton("Replace everything", {
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

/** The four rest lengths as number fields (seconds); a valid set is saved as it is typed. */
@Composable
private fun PresetFields(current: List<Int>, onSave: (List<Int>) -> Unit) {
    val texts = remember(current) { mutableStateListOf(*Array(REST_PRESET_COUNT) { current.getOrNull(it)?.toString().orEmpty() }) }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        for (i in texts.indices) androidx.compose.material3.OutlinedTextField(
            texts[i], { value ->
                texts[i] = value.filter { it.isDigit() }.take(4)
                val parsed = texts.map { it.toIntOrNull() }
                if (parsed.all { it != null && it in 1..1800 }) onSave(parsed.filterNotNull())
            },
            Modifier.weight(1f), singleLine = true, label = { Text("s") }, isError = texts[i].toIntOrNull()?.let { it !in 1..1800 } ?: true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        )
    }
}

/** A switch for an on/off option stored under [key]. */
@Composable
private fun Toggle(model: AppModel, key: String, title: String, about: String = "", default: Boolean = true, onSwitch: (Boolean) -> Unit = { model.setOption(key, if (it) "on" else "off") }) {
    val on = model.flag(key, default)
    // The whole row is the switch, read out as one; the drawn switch only shows the state.
    Row(Modifier.toggleable(on, role = Role.Switch, onValueChange = onSwitch).minTouch(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (about.isNotEmpty()) Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, null)
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

/** The choice that clears an appearance override, so the part is the chosen preset's own value again. */
private const val PRESET_OWN = "Preset's own"

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

/** The headings of Settings and what is behind each. */
private val SETTINGS_PAGES = listOf(
    "Profile" to "Name, units, height, and switching between profiles",
    "Workout" to "Rest timer and its style, feedback, set rows, swipes and focus mode, weekly target, auto-fill, estimated 1RM, template colours, outside the app",
    "Appearance" to "Presets, Mix and match, Day & night, Mono, Home, tab bar, charts, text size and font",
    "Lists" to "What the exercise list and the calendar show",
    "Data" to "Backup and restore, import and export, Health Connect, animations, renames, delete everything",
    "Credits" to "Source code, what's new and licences",
    "Tips" to "Gestures that are not labelled",
)

/** The gestures nothing on screen points to, each checked against the code. */
/** The public repository; opened in the browser from Settings, Credits. */
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
    "Tap the title of the Activity chart to bring it back to today.",
    "clean://start, clean://start?template=Name and clean://open?tab=history open the app from a link, and the launcher icon's shortcuts start a workout.",
)
