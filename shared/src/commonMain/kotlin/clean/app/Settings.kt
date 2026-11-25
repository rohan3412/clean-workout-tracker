package clean.app

import clean.app.platform.LocalPlatform

import clean.core.time.*
import kotlinx.datetime.DayOfWeek
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import clean.app.ui.theme.cardShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import clean.app.ui.theme.THEMES
import clean.app.ui.theme.orange
import clean.app.ui.theme.resolveLook
import clean.core.LOOK_ACCENT_KEY
import clean.core.LOOK_BLACK_KEY
import clean.core.LOOK_THEMES
import clean.core.LOOK_THEME_KEY
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.ExperimentalFoundationApi
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
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
fun SettingsScreen(model: AppModel) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val profile by model.profile.collectAsState()
    // Which colour the wheel is open for: only the sticky note's band.
    var wheel by remember { mutableStateOf<String?>(null) }
    var credits by remember { mutableStateOf(false) }
    var datasetCredits by remember { mutableStateOf(false) }
    val textSize by model.textSize.collectAsState()
    val performance by model.performance.collectAsState()
    val theme by model.theme.collectAsState()
    val look by model.look.collectAsState()
    val auto by model.auto.collectAsState()
    val oneRm by model.oneRm.collectAsState()
    val restDefault by model.restDefault.collectAsState()
    val stickyColor by model.stickyColor.collectAsState()
    val saveTemplates = platform.rememberFileSaver("application/json") { uri ->
        if (uri != null) scope.launch {
            platform.writeFile(uri, model.templatesFile().encodeToByteArray())
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
    var wipe by remember { mutableStateOf<BackupContents?>(null) }
    // Switching Health Connect off asks, once, whether what Clean already sent stays there.
    var healthOffAsk by remember { mutableStateOf(false) }
    val saveBackup = platform.rememberFileSaver("application/zip") { uri ->
        if (uri != null) scope.launch {
            backingUp = true
            model.message = "Backing up ${plural(model.allWorkouts.value.size, "workout")} and everything else…"
            try {
                platform.writeBackup(uri, withAnimations)
                model.message = "Backup saved"
            } catch (e: Exception) {
                model.message = e.message ?: "The backup could not be saved"
            }
            backingUp = false
        }
    }
    // The switch for the animations is only worth a question when there are any.
    fun startBackup() { if (platform.animationsBytes() > 0) backupChoice = true else saveBackup("Clean backup ${LocalDate.now()}.zip") }
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
    fun exportTo(uri: String?) {
        if (uri != null) task {
            platform.writeFile(uri, exportBytes(model, it, exportKind))
            model.message = "Workouts exported"
        }
    }
    val saveCsv = platform.rememberFileSaver("text/csv") { exportTo(it) }
    val saveJson = platform.rememberFileSaver("application/json") { exportTo(it) }
    val saveMeasurements = platform.rememberFileSaver("application/zip") { uri ->
        if (uri != null) task {
            platform.writeFile(uri, model.repo.exportMeasurements(it, currentMillis()))
            model.message = "Measurements exported"
        }
    }

    val askHealth = platform.rememberHealthPermissionRequest(false) { granted ->
        if (granted) model.setOption("health", "on") else model.message = "Health Connect did not allow it, so nothing will be written"
    }
    val askSteps = platform.rememberHealthPermissionRequest(true) { granted ->
        if (granted) model.setOption("health.steps", "on") else model.message = "Health Connect did not allow it, so Home shows no steps"
    }
    // Asked once, when the background alert for a rest is switched on; if it is refused the switch stays off.
    val askNotifications = platform.rememberNotificationPermissionRequest { granted ->
        if (granted) model.setOption("rest.background", "on") else model.message = "Notifications are not allowed, so nothing will alert you in the background"
    }
    // The same, when the workout notification is switched on; refused, it stays off.
    val askWorkoutNotice = platform.rememberNotificationPermissionRequest { granted ->
        model.setOption("workout.notif", if (granted) "on" else "off")
        if (!granted) model.message = "Notifications are not allowed, so the workout will not show on the lock screen"
    }
    // And when the first day of a workout reminder is chosen; refused, the days are kept but nothing can be shown.
    val askReminder = platform.rememberNotificationPermissionRequest { granted ->
        if (!granted) model.message = "Notifications are not allowed, so the reminders cannot be shown"
    }
    var animations by remember { mutableStateOf(platform.animationCount()) }
    var importing by remember { mutableStateOf(false) }
    val pickAnimations = platform.rememberFilePicker { uri ->
        if (uri != null) scope.launch {
            importing = true
            val kept = runCatching { platform.importAnimations(uri, ExerciseInfos.media(platform)) }.getOrDefault(-1)
            importing = false
            animations = platform.animationCount()
            model.message = when (kept) {
                -1 -> "That file could not be read as a zip"
                0 -> "No animations were found in that zip"
                else -> "$kept animations imported"
            }
        }
    }

    // Round 37: a main page of nine rows, each with what it is set to now; each opens one short page, with jump chips when it has three groups or more.
    var page by remember { mutableStateOf(model.settingsPage.also { model.settingsPage = null }) }
    BackHandler(enabled = page != null) { page = null }
    val motion = LocalMotion.current
    val openPage: (String) -> Unit = { page = it }
    val now = rememberSettingsNow(model)
    // The search is Library's and History's (Search.kt): an icon in the bar, a pull down on the list; a result opens its page scrolled to its section.
    val search = rememberSearchOpener()
    val query = search.query
    var jump by remember { mutableStateOf<String?>(null) }
    var highlight by remember { mutableStateOf<String?>(null) }
    // The section a jump chip has just led to, and the request that lights its first heading once the page is at rest (Round 40).
    var shine by remember { mutableStateOf<Pair<String, ShineRequest>?>(null) }
    var shines by remember { mutableStateOf(0) }
    var restKinds by remember { mutableStateOf(false) }
    val barActions: @Composable () -> Unit = {
        if (page == null) IconButton(onClick = { scope.launch { search.openAndFocus() } }) { Icon(CleanIcons.Search, "Search settings") }
    }
    /** A section of a page: one item of its list, washed for a moment when a search led to it. */
    fun LazyListScope.section(key: String, content: @Composable () -> Unit) = item(key = key) {
        val request = shine?.takeIf { it.first == key }?.second
        CompositionLocalProvider(LocalHeadingShine provides request) { SettingsFlash(highlight == key, { if (highlight == key) highlight = null }, content) }
    }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar(page ?: "Settings", { if (page != null) page = null else model.back() }, barActions) }) { padding ->
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
        val def = page?.let(::settingsPageDef)
        val sections = def?.sections.orEmpty()
        val chips = def?.chips.orEmpty().filter { c -> c.keys.any { it != "s-health" || platform.hasHealth } }
        val chipsOn = chips.size >= CHIPS_FROM
        val headerItems = 0 // the chips are above the list, not an item of it
        val chipTop = with(LocalDensity.current) { CHIP_BAR_STUCK.toPx().toInt() }
        // The index in the list of the section a chip leads to; the first section is the top of the page.
        fun targetOf(chip: Int): Int = chips[chip].keys.map { sections.indexOf(it) }.filter { it >= 0 }.min().let { if (it == 0) 0 else headerItems + it }
        // Scrolled to the section a search led to, once the list has its items.
        if (page != null) LaunchedEffect(Unit) {
            val target = jump ?: return@LaunchedEffect
            jump = null
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            val at = sections.indexOf(target)
            if (at > 0) listState.scrollToItem(headerItems + at, -chipTop) else listState.scrollToItem(0)
            highlight = target
        }
        // The chip lit: the one of the section in view, unless a finger is sliding along them or a tap's scroll is on its way.
        val lit by remember(listState, chips, sections) {
            derivedStateOf {
                if (!chipsOn) return@derivedStateOf 0
                val info = listState.layoutInfo
                var current = 0
                for (v in info.visibleItemsInfo) {
                    val at = sections.indexOf(v.key as? String)
                    if (at >= 0 && v.offset <= chipTop * 3 / 2) current = maxOf(current, at)
                }
                if (listState.canScrollBackward && !listState.canScrollForward) current = sections.lastIndex
                chips.indexOfLast { c -> c.keys.minOf { k -> sections.indexOf(k).let { if (it < 0) Int.MAX_VALUE else it } } <= current }.coerceAtLeast(0)
            }
        }
        var slid by remember { mutableStateOf<Int?>(null) }
        var tapped by remember { mutableStateOf<Int?>(null) }
        var restAt by remember { mutableStateOf<Pair<Int, Int>?>(null) }
        LaunchedEffect(listState) {
            snapshotFlow { Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.isScrollInProgress) }.collect { (index, offset, moving) ->
                val rest = restAt
                if (rest != null && !moving && (index to offset) != rest) { tapped = null; restAt = null }
            }
        }
        val pull = rememberPullToSearch(listState, search, enabled = page == null && !search.open)
        Column(Modifier.fillMaxSize()) {
        if (page == null) SearchBox(search, "Search settings", Modifier.padding(horizontal = Space.l, vertical = Space.xs))
        if (page != null && chipsOn) {
            JumpChips(
                chips.map { it.label }, slid ?: tapped ?: lit, { slid = it },
                { i ->
                    tapped = i
                    restAt = null
                    scope.launch {
                        val to = targetOf(i)
                        val offset = if (to > 0) -chipTop else 0
                        try {
                            if (motion) listState.animateScrollToItem(to, offset) else listState.scrollToItem(to, offset)
                        } finally {
                        // The chip tapped stays lit where the page came to rest (a short page's end would light the last one) until the page is scrolled again.
                            restAt = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                        }
                        // The heading the chip leads to answers the tap, also where the page could not scroll to it.
                        val key = chips[i].keys.filter { sections.indexOf(it) >= 0 }.minByOrNull { sections.indexOf(it) }
                        if (key != null) { shines++; shine = key to ShineRequest(shines) }
                    }
                },
                { i -> for (k in chips[i].keys) for (line in now.lines(k)) PeekLine(line) },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        if (page == null) PullHint(pull, Modifier.align(Alignment.TopCenter))
        LazyColumn(Modifier.fillMaxSize().then(if (page == null) Modifier.pulled(pull) else Modifier), listState) {
            if (page == null && query.isNotBlank()) {
                val found = searchSettings(query).filter { platform.hasHealth || it.key != "s-health" }
                if (found.isEmpty()) item(key = "none") { Text("No setting matches “${query.trim()}”.", Modifier.padding(Space.l), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else item(key = "found") {
                    SettingsGroup(null) {
                        for (place in found) {
                            val value = now.valueOf(place)
                            SettingsRow(
                                place.title, Modifier.testTag("result ${place.key}"), summary = "${place.page} › ${place.group}" + (value?.let { " · $it" } ?: ""), chevron = true,
                                onClick = { jump = place.key; search.close(); openPage(place.page) },
                            )
                        }
                    }
                }
            }
            if (page == null && query.isBlank()) for ((n, group) in SETTINGS_MAIN_GROUPS.withIndex()) item(key = "pages-$n") {
                SettingsGroup(null) {
                    for (id in group) {
                        val info = settingsPageDef(id)!!
                        SettingsRow(
                            id, Modifier.settleNext().testTag("page $id"), summary = now.page(id), summaryLines = 1, onClick = { openPage(id) }, chevron = true, titleWeight = FontWeight.SemiBold,
                            leading = {
                                Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), cardShape()), contentAlignment = Alignment.Center) {
                                    Icon(info.icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                        )
                    }
                }
            }
            for (sec in sections) when (sec) {
            "s-autobackup" -> section("s-autobackup") { Column(Modifier.settleNext()) { AutoBackupSection(model) } }
            "s-backup" -> section("s-backup") {
                SettingsGroup("Backup file", Modifier.settleNext(), footer = "One zip with everything in Clean. Restoring it replaces what is on the phone. Keep it somewhere off the phone.") {
                    ActionRow(if (backingUp) "Backing up ${plural(model.allWorkouts.collectAsState().value.size, "workout")}…" else "Back up everything", { startBackup() }, enabled = !backingUp)
                    ActionRow("Restore a backup", { history.pickRestore() })
                }
            }
            "s-import" -> section("s-import") {
                SettingsGroup(
                    "Import into ${profile?.name ?: "profile"}",
                    footer = "From Strong, Hevy, Lyfta, Fitbod, FitNotes, GymMane or openGym, or any CSV with a date, an exercise and reps. A zip with one of them in it works too, and so does Strong's measurements zip. A Clean backup picked here is restored instead. Clean says what the file is and what it would add before anything is saved.",
                ) {
                    ActionRow("Import", { history.pickImport() })
                }
            }
            "s-export" -> section("s-export") {
                SettingsGroup(
                    "Export from ${profile?.name ?: "profile"}",
                    footer = "Strong's format is the exact one, so the file opens anywhere a Strong export does. The other formats hold only what those apps have room for.",
                ) {
                    ActionRow("Workouts", { exporting = true })
                    ActionRow("Measurements", { saveMeasurements("clean_measurements_${LocalDate.now()}.zip") })
                    Toggle(model, "export.rest", "Write rest timers", "A row after each set with its rest, as older Strong exports have. Only for workouts finished from now on.", default = false)
                }
            }
            "s-health" -> section("s-health") {
                if (platform.hasHealth) SettingsGroup(
                    "Health Connect", Modifier.settleNext(),
                    footer = "Health Connect is the store on this phone that health apps share. With the first switch on, Clean keeps it in line with the app: each finished workout (as strength training, with its name, times and a short summary of its sets), each weigh-in and each body fat figure is written, and one you edit or delete is changed or removed there too. Only records Clean wrote are ever touched. With the second switch on, Clean reads today's steps from it, for one quiet line on Home, and nothing else. None of it goes online.",
                ) {
                    if (!platform.healthAvailable()) NoteRow("Health Connect is not available on this phone.", quiet = true)
                    else {
                        // Switching it on asks Health Connect for its three permissions (workouts, weight, body fat); it stays off if they are all refused.
                        val health = model.flag("health", false)
                        SettingsSwitch(
                            "Send to Health Connect", health, { on -> if (on) askHealth() else { model.setOption("health", "off"); healthOffAsk = true } },
                            summary = "Workouts (with a summary of the sets), body weight and body fat",
                        )
                        if (health) {
                            val last by model.healthLast.collectAsState()
                            if (last.isNotEmpty()) NoteRow("Last sync: $last", quiet = true)
                            // Switched on before body fat was sent: its permission is not there yet, and asking is up to the user.
                            val noFat by model.healthBodyFatMissing.collectAsState()
                            if (noFat) ActionRow("Allow body fat", { askHealth() }, summary = "Health Connect has not been allowed to take it.")
                            ActionRow("Send everything logged so far", {
                                scope.launch { model.message = "${model.sendHistoryToHealth()} records sent to Health Connect" }
                            })
                        }
                        // Reading is its own switch, off until chosen; it asks Health Connect for the steps permission and stays off if refused.
                        val steps = model.flag("health.steps", false)
                        SettingsSwitch("Show today's steps on Home", steps, { on -> if (on) askSteps() else model.setOption("health.steps", "off") }, summary = "Read from Health Connect; nothing is shown when there are none.")
                    }
                }
            }
            "s-animations" -> section("s-animations") {
                SettingsGroup(
                    "Exercise animations", Modifier.settleNext(),
                    footer = "The steps on an exercise's Info tab can have a moving picture above them. The pictures are © Gym visual and are not part of Clean: for your own use, download the zip of github.com/hasaneyldrm/exercises-dataset (Code, Download ZIP, about 130 MB) in a browser and pick it here. Only the pictures are copied in; the app still never goes online.",
                ) {
                    SettingsRow("On this phone", value = if (animations == 0) "none" else "$animations")
                    ActionRow(if (importing) "Importing…" else "Import animations from a zip", { pickAnimations() }, enabled = !importing)
                    if (animations > 0) ActionRow("Remove the animations", { platform.clearAnimations(); animations = 0 }, danger = true)
                }
            }
            "s-templates" -> section("s-templates") {
                SettingsGroup(
                    "Templates", Modifier.settleNext(),
                    footer = "Strong's export has no templates, so they travel in a file of Clean's own, folders included. The same two actions are in the Workout sheet's ⋯ menu.",
                ) {
                    ActionRow("Export templates", { saveTemplates("clean_templates_${LocalDate.now()}.json") })
                    ActionRow("Import templates", { model.importTemplatesNow = true; model.stack.clear(); model.openWorkoutSheet() })
                }
            }
            "s-renamed" -> section("s-renamed") { RenamedExercises(model) }
            "s-tidy" -> section("s-tidy") {
                SettingsGroup("Tidy up", Modifier.settleNext(), footer = "Notes such as “Wide grip” can be turned into variations, so those sets get their own history and PRs.") {
                    ActionRow("Turn notes into variations", { model.open(Screen.NoteReview()) })
                }
            }
            "s-delete" -> section("s-delete") {
                Spacer(Modifier.height(Space.xl))
                SettingsGroup(
                    "Delete everything", Modifier.settleNext(),
                    footer = "Takes every profile, workout, template, measurement, option and imported file off this phone, and Clean starts as when it was first installed.",
                ) {
                    ActionRow("Delete everything…", { scope.launch { wipe = withContext(ioDispatcher) { platform.currentContents() } } }, danger = true)
                }
            }
            // Appearance (1.1.0 remake): mode, the eight themes, then rows.
            "ap-mode" -> section("ap-mode") {
                SettingsGroup(
                    "Mode", Modifier.settleNext(),
                    footer = if (theme == "phone") "Follows the phone: Dark or Light as it is." else "Dark is easy on the eyes at the gym; Light is for bright rooms.",
                ) {
                    ChoiceRow(null) { Segmented(LOOK_THEMES, theme, { THEME_NAMES.getValue(it) }) { model.setOption(LOOK_THEME_KEY, it) } }
                }
            }
            "ap-theme" -> section("ap-theme") {
                SettingsGroup(
                    "Theme", Modifier.settleNext(),
                    footer = "Each theme sets the page, the accent, the good, bad and PR colours and the chart colours together, separately for Dark and for Light. Swipe along the row for the rest.",
                ) {
                    ChoiceRow(null) {
                        // E16: pressing and holding a swatch shows that theme's accent on a small dock until the finger lifts.
                        var held by remember { mutableStateOf<String?>(null) }
                        // Round 34: twelve swatches in one scrolling row, the chosen one scrolled into view.
                        val names = THEMES.keys.toList()
                        val rowState = rememberLazyListState(initialFirstVisibleItemIndex = (names.indexOf(look.themeName) - 1).coerceAtLeast(0))
                        androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth().testTag("theme-row"), state = rowState, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            items(names, key = { it }) { name -> ThemeSwatch(name, name == look.themeName, look.dark, { held = if (it) name else null }) { model.setOption(LOOK_ACCENT_KEY, name) } }
                        }
                        held?.let { name -> DockPreview(model, resolveLook(if (look.dark) "dark" else "light", accent = name)) }
                    }
                }
            }
            "ap-dock" -> section("ap-dock") {
                SettingsGroup("Dock", Modifier.settleNext(), footer = "Puts the plus button on the left of the bar, for the left hand.") {
                    Toggle(model, "dock.left", "Plus on the left", default = false)
                }
            }
            "ap-black" -> section("ap-black") {
                // Only the Dark theme has a black page; Light has nothing to turn black.
                val meaningful = theme != "light"
                val own = resolveBackground(BACKGROUND_THEME, look.themeName)
                SettingsGroup(
                    "Background", Modifier.settleNext(),
                    footer = "Theme's own follows the theme: ${look.themeName} goes with ${own.name}. Pick another and it stays when the theme changes. " +
                        if (meaningful) "Pure black is a black page under the cards, for OLED screens." else "Pure black is for the Dark theme only: switch to Dark (or Phone) to use it.",
                ) {
                    BackgroundStrip(model)
                    Toggle(model, LOOK_BLACK_KEY, "Pure black background", default = false, enabled = meaningful)
                }
            }
            "ap-performance" -> section("ap-performance") {
                SettingsGroup(
                    "Performance", Modifier.settleNext(),
                    footer = performance.about + " Reduce transparency and Animations still apply on top of this.",
                ) {
                    ChoiceRow(null) { Choice(Performance.entries, performance, { it.label }) { model.setOption(Performance.KEY, if (it == Performance.Full) null else it.key) } }
                }
            }
            "ap-glass" -> section("ap-glass") {
                SettingsGroup(
                    "Transparency", Modifier.settleNext(),
                    footer = "Solid bars, sheets and menus. Also on when the phone's own reduce-transparency or high-contrast setting is on.",
                ) {
                    Toggle(model, "glass.reduce", "Reduce transparency", default = false)
                }
            }
            "ap-text" -> section("ap-text") {
                SettingsGroup(
                    "Text size", Modifier.settleNext(),
                    footer = "The spacing of cards and lists follows the text: smaller text packs more onto a screen, larger text gives it room.",
                ) {
                    ChoiceRow(null) { Choice(listOf("Smallest", "Small", "Medium", "Large", "Largest"), textSize, { it }) { model.setOption("textSize", it) } }
                }
            }
            "ap-cbsafe" -> section("ap-cbsafe") {
                SettingsGroup("Colours", Modifier.settleNext(), footer = "Blue and orange in place of green and red for PRs, fatigue, strength and BMI.") {
                    Toggle(model, "theme.cbsafe", "Colour-blind safe colours", default = false)
                }
            }
            "ap-motion" -> section("ap-motion") {
                SettingsGroup(
                    "Animations", Modifier.settleNext(),
                    footer = "Cards growing into pages, fades, glides and celebrations. Off, everything changes at once, and ticking a set makes no buzz.",
                ) {
                    Toggle(model, "ui.motion", "Animations")
                }
            }
            "ap-haptic" -> section("ap-haptic") {
                SettingsGroup("Haptic ticks", Modifier.settleNext(), footer = "Light buzzes: a switch changing, a day tapped, a swipe or a hold taking effect, a set ticked, a personal record, a finished workout, a rest ending, an Undo.") {
                    Toggle(model, "haptic.ticks", "Haptic ticks")
                }
            }
            "ap-sticky" -> section("ap-sticky") {
                SettingsGroup("Sticky notes", Modifier.settleNext(), footer = "The colour of a note's band in a workout.") {
                    ChoiceRow(null) {
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
                    }
                }
            }
            "ap-blur" -> section("ap-blur") {
                SettingsGroup("Photos", Modifier.settleNext(), footer = "On Profile and in the lists. A photo is sharp once you open it.") {
                    Toggle(model, "photos.blur", "Blur photos", about = "Every photo in Photos, progress and snapshots, in every small view.", default = false)
                    SettingsSwitch(
                        "Lock photos", model.flag("photos.lock", false), { model.setPhotoLock(it) },
                        summary = "Opening Photos asks for the phone's fingerprint, face, PIN or pattern, until the app is left. Locked, small views are blurred.",
                    )
                }
            }
            // Workout: Rest, then Training, then the rest of what a workout does.
            "s-rest" -> section("s-rest") {
                val stored by model.options.collectAsState()
                Column(Modifier.settleNext()) {
                    SettingsGroup("Rest timer", footer = "An exercise can have its own length, from its options in a workout.") {
                        RestChoice("Default rest", restDefault, listOf(0, 60, 90, 120, 180, 300), { model.setOption("rest.seconds", it.toString()) }, grouped = true)
                        RestChoice(
                            "Rest between sides", stored["rest.sides"]?.toIntOrNull() ?: 60, listOf(0, 30, 60, 90, 120), { model.setOption("rest.sides", it.toString()) },
                            grouped = true, summary = "After the first side of a left and right exercise, before the other",
                        )
                        SettingsRow("Rest by set type", summary = "Warm-up, failure and drop sets, and after a hard set", onClick = { restKinds = true }, chevron = true)
                    }
                    SettingsGroup(
                        "Rest in the background",
                        footer = "What rings and buzzes as a rest ends is under Sound and feel.",
                    ) {
                        Toggle(
                            model, "rest.background", "Alert outside the app",
                            "Buzzes through the last five seconds and rings the bell as the rest ends, with a notification counting down. Nothing goes online.", default = false,
                        ) { on ->
                            if (!on) model.setOption("rest.background", "off")
                            else if (!platform.notificationsPermitted) askNotifications()
                            else model.setOption("rest.background", "on")
                        }
                        Toggle(model, "rest.return", "Go back to the workout when the rest ends")
                    }
                }
            }
            "s-rest-end" -> section("s-rest-end") {
                val stored by model.options.collectAsState()
                SettingsGroup(
                    "When a rest ends", Modifier.settleNext(),
                    footer = "Sound is the rest sound (below) at zero, at the phone's media volume, so it comes through headphones. Buzz is a pulse on each of the last five seconds, the last one longer.",
                ) {
                    val ends = listOf(RestEndFeedback.SOUND, RestEndFeedback.VIBRATE, RestEndFeedback.BOTH)
                    val end = restEndFeedback(stored["feedback.restEnd"], stored["rest.sound"], stored["rest.alert"])
                    ChoiceRow(null) {
                        Choice(ends, end, { if (it == RestEndFeedback.VIBRATE) "Buzz" else it.key.replaceFirstChar(Char::uppercase) }) { model.setOption("feedback.restEnd", it.key); if (it.sound) model.platform.bell(RestTone.of(stored["rest.tone"])) }
                    }
                }
            }
            "s-rest-sound" -> section("s-rest-sound") {
                val chosen = RestTone.of(model.options.collectAsState().value["rest.tone"])
                Column(Modifier.settleNext()) {
                    SettingsGroup("Rest sound", footer = "Rings as a rest ends and in the rest notification. A tap on a row picks it and plays it.") {
                        for (tone in RestTone.entries) SettingsRow(
                            tone.label, Modifier.selectable(tone == chosen, role = Role.RadioButton) { model.setOption("rest.tone", tone.key); platform.bell(tone) },
                            titleWeight = if (tone == chosen) FontWeight.SemiBold else null,
                            trailing = {
                                if (tone == chosen) Icon(CleanIcons.Check, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                IconButton({ platform.bell(tone) }) { Icon(CleanIcons.Play, "Play ${tone.label}") }
                            },
                        )
                    }
                    SettingsGroup("Finishing", footer = "A soft chime when a workout is finished.") {
                        Toggle(model, "finish.chime", "Finish chime", default = false)
                    }
                }
            }
            "s-consistency" -> section("s-consistency") {
                val goals = model.targets.collectAsState().value.orEmpty()
                val monday = clean.core.weekStart(LocalDate.now())
                val program by model.program.collectAsState()
                val applies = (goals.lastOrNull { it.from <= monday } ?: goals.firstOrNull())?.let { "The target applies from the week of ${prettyDay(it.from)}; earlier weeks keep the target they had. " }.orEmpty()
                SettingsGroup("Weekly target", Modifier.settleNext(), footer = applies + "A streak counts the weeks a program was kept.") {
                    ChoiceRow("Weekly target", summary = "Workouts a week that count as a good week") {
                        Choice((1..7).toList(), clean.core.targetFor(goals, monday), { "$it" }) { model.setWeeklyTarget(it) }
                    }
                    SettingsRow("Program", summary = "A weekly plan of templates and rest days", value = if (program.isEmpty()) "None" else "On", onClick = { model.open(Screen.Program) }, chevron = true)
                    Toggle(model, "split.on", "Current split", "Rotate Up next through one folder", default = false)
                }
            }
            "s-onerm" -> section("s-onerm") {
                SettingsGroup(
                    "Estimated 1RM", Modifier.settleNext(),
                    footer = "The heaviest single you could likely lift, worked out from a set's weight and reps. It is what 1RM PRs, the charts, the 1RM tiles and the L 1RM and R 1RM columns show. Sets with more reps than the limit get no estimate. Changing any of this recalculates every estimate and PR; no workout is changed.",
                ) {
                    // The three on offer; a formula chosen before from the longer list stays on the list until another is picked.
                    val formulas = (ONE_RM_CHOICES + oneRm.formula).distinct()
                    ChoiceRow(
                        "Formula",
                        summary = when (oneRm.formula) {
                            OneRmFormula.HYBRID -> "Hybrid uses Brzycki under 10 reps and Epley from 10 up."
                            OneRmFormula.BRZYCKI -> "Brzycki: weight × 36 ÷ (37 − reps). Reads lower than Epley at high reps."
                            OneRmFormula.EPLEY -> "Epley: weight × (1 + reps ÷ 30). Reads higher than Brzycki at low reps."
                            else -> "${oneRm.formula.label}, chosen before; pick one of the three above to change it."
                        },
                    ) { Choice(formulas, oneRm.formula, { if (it == OneRmFormula.HYBRID) "Hybrid (recommended)" else it.label }) { model.setOption("onerm.formula", it.name) } }
                    Toggle(
                        model, "onerm.rpe", "Factor in RPE",
                        "The reps left in reserve (10 − RPE) count as done, so 100 × 8 at RPE 8 counts as 10 reps. RPE 10, no RPE, or an RPE under 5 changes nothing.",
                        default = false,
                    )
                    ChoiceRow("Most reps it is used for") { Choice(ONE_RM_LIMITS, oneRm.maxReps, { "$it" }) { model.setOption("onerm.maxReps", it.toString()) } }
                    SettingsRow("Example", value = "100 ${profile?.unit ?: "kg"} × 8 → ${shortNumber(roundHalfUp(estimate1Rm(100.0, 8, oneRm)!! * 10) / 10.0)} ${profile?.unit ?: "kg"}")
                }
            }
            "s-weeksets" -> section("s-weeksets") {
                SettingsGroup(
                    "Weekly sets per muscle", Modifier.settleNext(),
                    footer = "Sets a week to aim for in each muscle group, counted as on the Body page. Each muscle can have its own range, from its page.",
                ) {
                    val range by model.defaultSetRange.collectAsState()
                    var low by remember { mutableStateOf(range.min.toString()) }
                    var high by remember { mutableStateOf(range.max.toString()) }
                    fun store() { SetRange.decode("${low.trim()}-${high.trim()}")?.let { model.setSetRange(null, it.encode()) } }
                    ChoiceRow(null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                            OutlinedTextField(low, { low = it; store() }, label = { Text("From") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(high, { high = it; store() }, label = { Text("To") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            "s-autofill" -> section("s-autofill") {
                SettingsGroup(
                    "Auto-fill sets", Modifier.settleNext(),
                    footer = "“Auto-fill sets” in an exercise's options during a workout lays out these sets and fills in a weight and reps that would beat your best estimated 1RM: more reps at the same weight first, then more weight.",
                ) {
                    ChoiceRow("Warm-up sets", summary = "One warm-up is half the working weight; more of them step up evenly from there.") {
                        Choice((0..3).toList(), auto.third, { it.toString() }) { model.setOption("auto.warm", it.toString()) }
                    }
                    ChoiceRow("Working sets") { Choice((1..5).toList(), auto.first, { it.toString() }) { model.setOption("auto.sets", it.toString()) } }
                    ChoiceRow("Drop sets") { Choice((0..3).toList(), auto.second, { it.toString() }) { model.setOption("auto.drops", it.toString()) } }
                }
            }
            "s-during" -> section("s-during") {
                val stored by model.options.collectAsState()
                SettingsGroup("In a workout", Modifier.settleNext()) {
                    Toggle(model, "workout.hint", "Suggest what to try", "Under an exercise's name: last time's best set and the set that would beat your best. Not shown where a progression is set.")
                    Toggle(model, "reward.celebrate", "Celebrate on finishing", "A target reached, a new badge or a shield earned shows at the top of the summary, with a chime and two taps. Silent with animations off.")
                    Toggle(model, "workout.summary", "Summary when a workout is finished", "Duration, volume, PRs and muscles, next to the last time you did the same routine.")
                    Toggle(model, "workout.rpe", "RPE on the number pad", "A key for how hard a set felt, 6 to 10. It is saved in the export's RPE column.")
                    Toggle(model, "workout.highlight", "Show the next set", "The set to do next is marked and scrolled to. In a superset the turn alternates.")
                    Toggle(model, "workout.compact", "Shrink finished exercises", "An exercise with every set ticked takes a line or two until touched.")
                    Toggle(
                        model, "workout.notif", "Workout on the lock screen",
                        "While the app is behind, a quiet notification shows the workout's time, the rest and the next set; a tap goes back.",
                    ) { on ->
                        if (!on) model.setOption("workout.notif", "off")
                        else if (!platform.notificationsPermitted) askWorkoutNotice()
                        else model.setOption("workout.notif", "on")
                    }
                    // Workout-day reminders: off until a day is chosen.
                    val remindDays = clean.core.parseRemindDays(stored["remind.days"])
                    val remindAt = clean.core.parseClock(stored["remind.time"]) ?: clean.core.REMIND_DEFAULT_TIME
                    ChoiceRow("Remind me to train on") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            for (day in DayOfWeek.entries) CleanChip(day in remindDays, {
                                val next = if (day in remindDays) remindDays - day else remindDays + day
                                if (remindDays.isEmpty() && !platform.notificationsPermitted) askReminder()
                                model.setOption("remind.days", clean.core.encodeRemindDays(next))
                            }, { Text(day.localName(false)) })
                        }
                    }
                    if (remindDays.isNotEmpty()) SettingsRow("Reminder time", summary = "Not on a day you have already trained", value = clean.core.clockText(remindAt), onClick = {
                        platform.pickTime(remindAt / 60, remindAt % 60) { hour, minute -> model.setOption("remind.time", clean.core.clockText(hour * 60 + minute)) }
                    }, chevron = true)
                    var plateUnit by remember { mutableStateOf<String?>(null) }
                    val unit = profile?.unit ?: "kg"
                    SettingsRow("Bar and plates", summary = "Your own bar and plates, for the plate calculator in a barbell set's menu", onClick = { plateUnit = unit }, chevron = true)
                    plateUnit?.let { PlateKitDialog(model, it) { plateUnit = null } }
                }
            }
            // About and help
            "s-source" -> section("s-source") {
                SettingsGroup("Source code", Modifier.settleNext(), footer = "Clean is open source under the MIT License.") {
                    // Just the address, to read or copy; the app has no internet access and opens nothing.
                    SelectionContainer { NoteRow(SOURCE_URL, color = MaterialTheme.colorScheme.primary) }
                }
            }
            "s-changes" -> section("s-changes") {
                // Every version; the pop-up after an update shows only the newest.
                val releases = remember { readChanges(platform) }
                // Round 24: the latest is shown; the rest on request.
                var allChanges by remember { mutableStateOf(false) }
                if (releases.isNotEmpty()) SettingsGroup("What's new") {
                    ActionRow("Open the What's new page", { model.showWhatsNew() })
                    for (r in if (allChanges) releases else releases.take(1)) ChoiceRow(null) { ChangeList(listOf(r)) }
                    if (releases.size > 1) ActionRow(if (allChanges) "Show only the latest" else "Show all", { allChanges = !allChanges })
                }
            }
            "s-credits" -> section("s-credits") {
                SettingsGroup("Licences and credits", Modifier.settleNext()) {
                    NoteRow("The body figure is drawn from MuscleMap by Melih Colpan, used under the MIT License.", quiet = true)
                    ActionRow("Show the licence", { credits = true })
                    NoteRow("The steps on an exercise's Info tab are from exercises-dataset by Hasan Emir Yıldırım (github.com/hasaneyldrm/exercises-dataset), used under the MIT License. None of that dataset's pictures or animations are in the app.", quiet = true)
                    ActionRow("Show the licence", { datasetCredits = true })
                    NoteRow("The text and numbers are set in Inter, Copyright 2020 The Inter Project Authors (github.com/rsms/inter), used under the SIL Open Font License, Version 1.1 (openfontlicense.org). The font file is unchanged.", quiet = true)
                    NoteRow("Some icons are Google's Material icons, used under the Apache License 2.0 (apache.org/licenses/LICENSE-2.0).", quiet = true)
                    NoteRow("The blur behind bars and sheets is done by Haze by Chris Banes (github.com/chrisbanes/haze), used under the Apache License 2.0 (apache.org/licenses/LICENSE-2.0).", quiet = true)
                }
            }
            "s-tips" -> section("s-tips") {
                SettingsGroup("Tips", Modifier.settleNext()) {
                    for (tip in TIPS) NoteRow(tip)
                }
            }
            else -> {}
            }
            item { Spacer(Modifier.height(Space.xxl)) }
        }
        }
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
            if (kind == ExportKind.JSON) saveJson("$name.json") else saveCsv("$name.csv")
        }
    }
    if (backupChoice) {
        val megabytes = remember { (platform.animationsBytes() + 999_999) / 1_000_000 }
        CleanDialog(
            onDismissRequest = { backupChoice = false },
            title = { Text("Back up everything") },
            text = {
                Row(Modifier.toggleable(withAnimations, role = Role.Switch, onValueChange = { withAnimations = it }).minTouch(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Include exercise animations ($megabytes MB)", Modifier.weight(1f))
                    Switch(withAnimations, null)
                }
            },
            confirmButton = { TextButton(onClick = { backupChoice = false; saveBackup("Clean backup ${LocalDate.now()}.zip") }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { backupChoice = false }) { Text("Cancel") } },
        )
    }
    if (healthOffAsk) CleanDialog(
        onDismissRequest = { healthOffAsk = false },
        title = { Text("Health Connect is off") },
        text = { Text("Workouts, weight and body fat Clean already sent can stay in Health Connect or be removed. Only what Clean wrote is removed; nothing another app wrote is touched.") },
        confirmButton = { TextButton(onClick = { healthOffAsk = false; scope.launch { model.healthRemoveAll() } }) { Text("Remove them") } },
        dismissButton = { TextButton(onClick = { healthOffAsk = false }) { Text("Keep them") } },
    )
    val healthSending = model.flag("health", false)
    wipe?.let { now ->
        CleanDialog(
            onDismissRequest = { wipe = null },
            title = { Text("Delete everything?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("This removes profiles: ${now.profiles.size}, workouts: ${now.workouts}, templates: ${now.templates}, weigh-ins: ${now.weighIns}, every option, and the imported animations. It cannot be undone." + if (healthSending) " What Clean sent to Health Connect is removed too." else "")
                    QuietButton("Back up first", { wipe = null; startBackup() }, Modifier.fillMaxWidth())
                    SlideToConfirm("Slide to delete everything", "Delete everything", {
                        wipe = null
                        scope.launch {
                            try {
                                // What Clean sent to Health Connect goes too (the switch is wiped with everything else, so nothing would remove it later).
                                if (healthSending) model.healthRemoveAll()
                                withContext(ioDispatcher + NonCancellable) { platform.wipeEverything() }
                            } catch (e: Exception) {
                                model.message = e.message ?: "That did not work"
                                return@launch
                            }
                            platform.restartApp()
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
    val platform = LocalPlatform.current
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
    suspend fun offerRestore(file: String) {
        try {
            restore = withContext(ioDispatcher) { PendingRestore(file, platform.inspectBackup(file), platform.currentContents()) }
        } catch (e: Exception) {
            platform.discardStaged(file)
            model.message = e.message ?: "That backup could not be read"
        }
    }
    val pickRestore = platform.rememberFilePicker { uri ->
        if (uri != null) scope.launch {
            try {
                offerRestore(platform.stageFile(uri))
            } catch (e: Exception) {
                model.message = e.message ?: "That file could not be read"
            }
        }
    }
    val pickImport = platform.rememberFilePicker { uri ->
        if (uri != null) task { id ->
            // A Clean backup is a zip too, and goes to the restore instead.
            model.message = "Reading the file…"
            val picked = platform.stageFile(uri)
            if (withContext(ioDispatcher) { platform.isBackup(picked) }) offerRestore(picked)
            else {
                val bytes = platform.takeStaged(picked)
                model.message = "Reading ${thousands(maxOf(1, bytes.size / 1024).toLong())} KB…"
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
                    platform.refreshWidgets()
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
            onConfirm = {
                task {
                    model.repo.commit(it, import)
                    platform.refreshWidgets()
                    model.message = "${import.newEntries.size} entries imported"
                    onImported()
                }
            },
            onDismiss = { measurementImport = null },
        )
    }
    restore?.let { r ->
        val found = r.found.contents
        CleanDialog(
            onDismissRequest = { platform.discardStaged(r.file); restore = null },
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
                                withContext(ioDispatcher + NonCancellable) { platform.applyBackup(r.file) }
                            } catch (e: Exception) {
                                model.message = e.message ?: "That did not work"
                                return@launch
                            } finally {
                                platform.discardStaged(r.file)
                            }
                            platform.restartApp()
                        }
                    }, Modifier.fillMaxWidth())
                    TextButton(onClick = { platform.discardStaged(r.file); restore = null }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            },
            confirmButton = {},
        )
    }
    return HistoryIn({ pickImport() }, { pickRestore() })
}

/** A backup that has been looked at, with what is on the phone now, waiting for the user to confirm the replacement. */
private class PendingRestore(val file: String, val found: BackupInspected, val now: BackupContents)

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
        }.encodeToByteArray()
    }
}

/** A switch row for an on/off option stored under [key]; [about] is a short line under the title, [enabled] false greys it and takes no tap. */
@Composable
private fun Toggle(model: AppModel, key: String, title: String, about: String = "", default: Boolean = true, inverted: Boolean = false, enabled: Boolean = true, onSwitch: (Boolean) -> Unit = { model.setOption(key, if (it != inverted) "on" else "off") }) {
    // [inverted]: the switch shows on while the stored flag is off.
    SettingsSwitch(title, model.flag(key, default) != inverted, onSwitch, summary = about.ifEmpty { null }, enabled = enabled)
}

/** A colour on offer, named for a screen reader; the 48 dp hit area is laid over the smaller swatch it sits in. */
@Composable
fun Swatch(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.requiredSize(48.dp).clip(CircleShape).selectable(selected, role = Role.RadioButton, onClick = onClick).semantics { contentDescription = name })
}

/**
 * A theme as a small phone (the picker of `artifacts/round3`): its ground with its light at the top, a faint bar and the accent as a button; the
 * chosen one is ringed in its accent and its name is bold. Drawn in the mode being used, so Light shows the Light accent on the tinted white.
 */
@Composable
private fun ThemeSwatch(name: String, selected: Boolean, dark: Boolean, onHold: (Boolean) -> Unit, onClick: () -> Unit) {
    val theme = resolveLook(if (dark) "dark" else "light", accent = name)
    val shape = cardShape()
    val ring = if (selected) theme.orange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Column(
        Modifier.width(76.dp).clip(MaterialTheme.shapes.small)
            // A tap selects; a press held for a moment shows the theme on a small dock until it lifts (selecting on lift, unless the finger moved away).
            .semantics { this.selected = selected; role = Role.RadioButton; onClick { onClick(); true } }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val up = withTimeoutOrNull(350) { tryAwaitRelease() }
                        if (up == null) { onHold(true); tryAwaitRelease(); onHold(false) }
                    },
                    onTap = { onClick() },
                )
            }
            .padding(vertical = Space.xs).semantics { contentDescription = name },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.s),
    ) {
        Box(Modifier.size(width = 72.dp, height = 96.dp).clip(shape).background(theme.page).border(if (selected) 2.dp else 1.dp, ring, shape).drawBehind {
            // The light, brighter than on a page so it reads at this size.
            val centre = Offset(size.width / 2f, 5.dp.toPx())
            val radius = 55.dp.toPx()
            val glow = theme.ambient.copy(alpha = (theme.ambient.alpha * 2f).coerceAtMost(0.5f))
            val stretch = (size.width + 40.dp.toPx()) / (2 * radius)
            withTransform({ scale(stretch, 1f, pivot = centre) }) {
                drawCircle(Brush.radialGradient(listOf(glow, glow.copy(alpha = 0f)), centre, radius), radius, centre)
            }
            drawRoundRect(if (dark) theme.tint.copy(alpha = 0.08f) else theme.higher, Offset(10.dp.toPx(), 30.dp.toPx()), Size(size.width - 20.dp.toPx(), 16.dp.toPx()), CornerRadius(6.dp.toPx()))
            // The theme's chart colours after the accent: four dots between the bar and the button.
            for ((i, c) in theme.series.drop(1).withIndex()) drawCircle(c, 3.5.dp.toPx(), Offset(size.width / 2f + (i - 1.5f) * 11.dp.toPx(), size.height / 2f + 8.dp.toPx()))
            drawRoundRect(theme.orange, Offset(10.dp.toPx(), size.height - 30.dp.toPx()), Size(size.width - 20.dp.toPx(), 18.dp.toPx()), CornerRadius(9.dp.toPx()))
        })
        Text(name, style = MaterialTheme.typography.bodySmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
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

/** E16: the dock (bar and plus) as it would look in the held theme, for as long as the press lasts. It takes no touch. */
@Composable
private fun DockPreview(model: AppModel, look: clean.app.ui.theme.Look) {
    androidx.compose.runtime.CompositionLocalProvider(clean.app.ui.theme.LocalLook provides look) {
        Row(Modifier.fillMaxWidth().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }.clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val plus: @Composable () -> Unit = { Box(Modifier.size(64.dp).background(look.orange, CircleShape), contentAlignment = Alignment.Center) { Icon(CleanIcons.Add, null, Modifier.size(28.dp), tint = look.ink) } }
            if (model.flag("dock.left", false)) plus()
            TabBar(model, TAB_ORDER, Modifier.weight(1f))
            if (!model.flag("dock.left", false)) plus()
        }
    }
}
