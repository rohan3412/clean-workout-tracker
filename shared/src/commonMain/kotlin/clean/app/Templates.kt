package clean.app

import clean.app.platform.LocalPlatform

import clean.core.time.*
import clean.app.ui.theme.cardShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import clean.core.upNextTemplate
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import clean.core.SetType
import clean.core.TemplateBlock
import clean.core.sameShape
import clean.core.shapeChange
import clean.core.valueIncreases
import clean.core.withStructureUpdated
import clean.core.withValuesUpdated
import clean.core.Workout
import clean.core.canonicalName
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun ago(date: String?, today: LocalDate): String {
    val day = date?.let(::parseDate)?.toLocalDate() ?: return "Not done yet"
    return when (val days = ChronoUnit.DAYS.between(day, today)) {
        0L -> "Today"
        1L -> "Yesterday"
        in 2..30 -> "$days days ago"
        else -> prettyDay(day)
    }
}

/**
 * What the Workout sheet holds (F6): "Start a workout" with New template and the sheet's menu, a search over the templates, Quick start,
 * then every template under its folder, the one Up next would pick marked. A tap on a template opens its page (which has Start); a hold,
 * or its ⋮, opens what a template's card used to offer. [header] carries the drag that closes the sheet.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WorkoutSheetContent(model: AppModel, header: Modifier = Modifier) {
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val templates by model.templates.collectAsState()
    val workouts by model.workouts.collectAsState()
    val splitOn by model.splitOn.collectAsState()
    val plan by model.programToday.collectAsState()
    val skipped = model.skippedTemplates
    // The last time a workout of each name was done, which is when its template was last used.
    val lastDone = remember(workouts) { workouts.associate { it.name to it.date } }
    var menu by remember { mutableStateOf(false) }
    var incoming by remember { mutableStateOf<List<Template>?>(null) }
    // Round 37: the same pill as every other search (Search.kt), kept out as the exercise pickers keep theirs (Round 28), since the sheet is not a page that can be pulled.
    val search = rememberSearchOpener().also { it.pinned = true; it.open = true }
    val query = search.query
    var picking by remember { mutableStateOf(false) }
    val folders = remember(templates) { templates.map { it.folder }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() } }
    // The program decides first, else the Up next rule (see [upNextTemplate]).
    val upNextId = remember(plan, templates, workouts, splitOn, skipped) {
        plan?.candidates?.firstOrNull { it.day.templateId !in skipped }?.day?.templateId?.takeIf { id -> templates.any { it.id == id } }
            ?: upNextTemplate(templates.map { it.ref() }, workouts, splitOn, CURRENT_SPLIT, LocalDate.now(), skipped)?.template?.id
    }

    val open = platform.rememberFilePicker { uri ->
        if (uri != null) scope.launch {
            try {
                incoming = model.readTemplatesFile(platform.readFile(uri).decodeToString())
            } catch (e: Exception) {
                model.message = "That is not a Clean templates file"
            }
        }
    }
    val save = platform.rememberFileSaver("application/json") { uri ->
        if (uri != null) scope.launch {
            platform.writeFile(uri, model.templatesFile().encodeToByteArray())
            model.message = "Templates exported"
        }
    }

    // A templates file handed over by another app is read like one picked here.
    LaunchedEffect(model.sharedFile) {
        model.sharedFile?.let { uri ->
            model.sharedFile = null
            try {
                incoming = model.readTemplatesFile(platform.readFile(uri).decodeToString())
            } catch (e: Exception) {
                model.message = "That is not a Clean templates file"
            }
        }
    }
    // Settings can send the user here to pick a templates file.
    LaunchedEffect(model.importTemplatesNow) {
        if (model.importTemplatesNow) {
            model.importTemplatesNow = false
            open()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(header.fillMaxWidth()) {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(Modifier.align(Alignment.CenterHorizontally).semantics { contentDescription = "Workout sheet handle. Drag down to close"; onClick("Close the workout sheet") { model.workoutSheet = false; true } })
            Row(Modifier.fillMaxWidth().padding(start = Space.xl, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Text("Start a workout", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = { model.open(Screen.TemplateEdit(null)) }) { Text("New template") }
                IconButton(onClick = { menu = true }) { Icon(CleanIcons.More, "Template options") }
            }
        }
        if (menu) Sheet({ menu = false }, "Templates") {
            SheetRow("Make from past workouts", CleanIcons.History) { menu = false; model.templatesFromHistory() }
            SheetRow("Import templates", CleanIcons.Down) { menu = false; open() }
            SheetRow("Export templates", CleanIcons.Up) { menu = false; save("clean_templates_${LocalDate.now()}.json") }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Space.xxl)) {
            item {
                // Round 28: two ways to begin, side by side: nothing planned, or the exercises chosen first.
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    StartCard("Empty workout", "Start now, add exercises as you go", CleanIcons.Play, Modifier.weight(1f).testTag("start empty")) { model.startWorkout() }
                    StartCard("Pick exercises", "Choose them first, then start", CleanIcons.Add, Modifier.weight(1f).testTag("start pick")) { picking = true }
                }
            }
            item { SearchBox(search, "Search templates", Modifier.padding(horizontal = Space.l, vertical = Space.s)) }
            if (templates.isEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = Space.xl, vertical = Space.m), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        SectionTitle("No templates yet")
                        Text("A template is a workout you repeat: its exercises and sets, ready to fill in.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (workouts.isNotEmpty()) {
                            Text("Strong's export does not carry your templates, but your history does: Clean can make one per workout name, from the latest workout of that name.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            QuietButton("Make from past workouts", { model.templatesFromHistory() })
                        }
                    }
                }
            }
            val shown = templates.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            if (templates.isNotEmpty() && shown.isEmpty()) item { Text("No template is called “${query.trim()}”.", Modifier.padding(horizontal = Space.xl, vertical = Space.m), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            // Each folder under its name; with the Current split switch on, that folder comes first. Without folders there is one list.
            val grouped = shown.sortedByDescending { lastDone[it.name] ?: "" }.groupBy { it.folder }
                .sortedMergedBy(compareBy<String> { it.isEmpty() }.thenBy { !(splitOn && it.equals(CURRENT_SPLIT, ignoreCase = true)) }.thenBy { it.lowercase() })
            for ((folder, list) in grouped) {
                item(key = "f:$folder") { SectionTitle(folder.ifEmpty { if (folders.isEmpty()) "Templates" else "No folder" }, Modifier.padding(start = Space.xl, end = Space.xl, top = Space.l)) }
                items(list, key = { it.id }) { template -> TemplateRow(model, template, lastDone[template.name], folders, template.id == upNextId) }
            }
        }
    }

    incoming?.let { list -> ImportTemplates(model, list, workouts) { incoming = null } }
    if (picking) ExercisePicker(
        model, workouts, onPick = {}, onDismiss = { picking = false }, onPickMany = { names -> picking = false; model.startWithExercises(names) },
        title = "Pick exercises", multiOnly = true, confirm = "Start",
    )
}

/** One template in the Workout sheet: its name, when it was last done and, quietly, whether it is Up next. A tap opens its page; a hold or the ⋮ opens its menu. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TemplateRow(model: AppModel, template: Template, lastDone: String?, folders: List<String>, upNext: Boolean) {
    val platform = LocalPlatform.current
    var menu by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    if (moving) {
        var name by remember { mutableStateOf(template.folder) }
        CleanDialog(
            onDismissRequest = { moving = false },
            title = { Text("Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Folder name") }, placeholder = { Text("Push") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    // "Current split" is always on offer: with its switch on in Settings, Up next goes round the templates in it.
                    for (f in (listOf(CURRENT_SPLIT) + folders).distinctBy { it.lowercase() }) Text(f, Modifier.fillMaxWidth().clickable { name = f }.padding(vertical = Space.s), color = MaterialTheme.colorScheme.primary)
                    Text("With “Current split” switched on in Settings, Up next goes round the templates in $CURRENT_SPLIT.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (template.folder.isNotEmpty()) Text("No folder", Modifier.fillMaxWidth().clickable { name = "" }.padding(vertical = Space.s), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { model.save(template.copy(folder = name.trim())); moving = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { moving = false }) { Text("Cancel") } },
        )
    }
    var deleting by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .clickable { model.open(Screen.TemplateStats(template.id)) }
            .holdMenu(cardShape(), {
                listOf(
                    HoldAction("Open", CleanIcons.Forward) { model.open(Screen.TemplateStats(template.id)) },
                    HoldAction("Start", CleanIcons.Play) { model.start(template) },
                    HoldAction("Edit", CleanIcons.Edit) { model.open(Screen.TemplateEdit(template.id)) },
                    HoldAction("Delete", CleanIcons.Delete, danger = true) { deleting = true },
                )
            }, peek = { templatePeek(template, lastDone) })
            .padding(start = Space.xl, end = Space.xs).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = Space.s)) {
            Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(ago(lastDone, model.today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (upNext) Text("Up next", Modifier.padding(horizontal = Space.s), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Drawn 32 dp wide; its hit area is 48 dp, laid over the neighbours without moving them.
        Box(Modifier.size(32.dp).reach().clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
            Icon(CleanIcons.More, "Options for ${template.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (menu) Sheet({ menu = false }, template.name) {
        SheetRow("Start", CleanIcons.Play) { menu = false; model.start(template) }
        SheetRow("Edit", CleanIcons.Edit) { menu = false; model.open(Screen.TemplateEdit(template.id)) }
        SheetRow("Folder", CleanIcons.Folder, value = template.folder.ifEmpty { "None" }) { menu = false; moving = true }
        SheetRow("Share as a file", CleanIcons.Share) { menu = false; platform.shareFile("${template.name}.clean.json", model.templatesFile(listOf(template)).encodeToByteArray(), "application/json") }
        SheetGap()
        SheetRow("Delete", CleanIcons.Delete, danger = true) { menu = false; deleting = true }
    }
    if (deleting) Confirm("Delete ${template.name}?", "Only the template goes; workouts already logged from it stay.", "Delete", { model.deleteTemplate(template.id) }) { deleting = false }
}

@Composable
fun TemplateEditScreen(model: AppModel, id: Long?) {
    val templates by model.templates.collectAsState()
    val workouts by model.workouts.collectAsState()
    val original = remember(id) { templates.firstOrNull { it.id == id } }
    // Kept across a turn of the phone and the app being put away, so nothing typed or added is lost.
    var name by rememberSaveable { mutableStateOf(original?.name ?: "") }
    var blocks by rememberSaveable(stateSaver = TemplateBlocksSaver) { mutableStateOf(original?.blocks ?: emptyList()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    // Leaving with changes that are not saved asks first.
    val changed = if (original == null) name.isNotBlank() || blocks.isNotEmpty() else name != original.name || blocks != original.blocks
    var discarding by rememberSaveable { mutableStateOf(false) }
    val leave = { if (changed) discarding = true else model.back() }
    BackHandler(enabled = changed) { discarding = true }

    Scaffold(containerColor = pageContainer(), topBar = {
        BackBar(if (original == null) "New template" else "Edit template", leave) {
            // A template with no name is called Workout.
            TextButton(enabled = blocks.isNotEmpty(), onClick = {
                model.save(Template(original?.id ?: 0, name.trim().ifEmpty { "Workout" }, blocks, original?.folder.orEmpty()))
                model.back()
            }) { Text("Save") }
        }
    }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(Space.l)) }
            itemsIndexed(blocks) { index, block ->
                fun change(sets: List<SetType>?) {
                    // The planned values stay with the sets that stay.
                    blocks = blocks.toMutableList().also { if (sets == null) it.removeAt(index) else it[index] = block.copy(sets = sets, targets = block.targets.take(sets.size)) }
                }
                SwipeToRemove(onRemove = { change(null) }) {
                Row(Modifier.fillMaxWidth().padding(start = Space.l), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(block.exercise)
                        Text(block.sets.joinToString(" ") { it.letter.ifEmpty { "•" } }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(enabled = block.sets.size > 1, onClick = { change(block.sets.dropLast(1)) }) { Text("−") }
                    Text(plural(block.sets.size, "set"))
                    TextButton(onClick = { change(block.sets + SetType.NORMAL) }) { Text("+") }
                    IconButton(onClick = { change(null) }) { Icon(CleanIcons.Close, "Remove exercise", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                }
            }
            item {
                QuietButton("Add exercise", { picking = true }, Modifier.fillMaxWidth().padding(Space.l))
            }
        }
    }
    if (picking) {
        ExercisePicker(
            model, workouts, onPick = { blocks = blocks + TemplateBlock(it, List(3) { SetType.NORMAL }); picking = false }, onDismiss = { picking = false },
            onPickMany = { names -> blocks = blocks + names.map { TemplateBlock(it, List(3) { SetType.NORMAL }) }; picking = false },
            title = "Add exercise",
        )
    }
    if (discarding) Confirm("Discard changes?", "What you changed in this template is not saved.", "Discard", model::back) { discarding = false }
}

/**
 * Importing templates: every exercise name in the file is settled before anything is saved. A name
 * that already exists here is used as it is; for any other the user chooses to create it or to
 * use one of their own exercises instead, since two people often have two names for one exercise.
 */
@Composable
private fun ImportTemplates(model: AppModel, incoming: List<Template>, workouts: List<Workout>, onDismiss: () -> Unit) {
    val templates by model.templates.collectAsState()
    val known = remember(workouts, templates) {
        workouts.flatMapTo(HashSet()) { w -> w.blocks.map { it.exercise } } + templates.flatMap { t -> t.blocks.map { it.exercise } }
    }
    val unknown = remember(incoming, known) { incoming.flatMap { t -> t.blocks.map { it.exercise } }.distinct().filter { it !in known } }
    // Unknown name -> the existing exercise to use instead; absent means create it under its own name.
    // A name the library knows under another name starts out assigned to it; the user can still change that.
    val assigned = remember { mutableStateMapOf<String, String>().apply { for (n in unknown) canonicalName(n)?.let { put(n, it) } } }
    var choosing by remember { mutableStateOf<String?>(null) }

    CleanDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import ${incoming.size} templates") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                item {
                    Text(if (unknown.isEmpty()) "Every exercise in the file is one you already have." else "${unknown.size} exercises in the file are new here. Keep each as a new exercise, or tap it to use one of yours instead.")
                }
                items(unknown) { name ->
                    Column(Modifier.fillMaxWidth().clickable { choosing = name }) {
                        Text(name, fontWeight = FontWeight.Bold)
                        Text(assigned[name]?.let { "Use: $it" } ?: "Create as new", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                for (t in incoming) model.save(t.copy(id = 0, blocks = t.blocks.map { it.copy(exercise = assigned[it.exercise] ?: it.exercise) }))
                model.message = "${incoming.size} templates imported"
                onDismiss()
            }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    choosing?.let { name ->
        ExercisePicker(model, workouts, onPick = { picked -> if (picked == name) assigned.remove(name) else assigned[name] = picked; choosing = null }, onDismiss = { choosing = null }, title = "Use another exercise")
    }
}

/**
 * Shown after a workout that was started from a template and finished in a different shape, or with more than it
 * planned. The first option shown is the one this workout most likely wants; the last leads to naming a new template.
 */
@Composable
fun TemplateOfferDialog(model: AppModel) {
    val offer = model.templateOffer ?: return
    val templates by model.templates.collectAsState()
    val before = offer.template.blocks
    val values = remember(offer) { valueIncreases(before, offer.blocks).size }
    val change = remember(offer) { shapeChange(before, offer.blocks) }
    val structure = remember(offer) { !sameShape(before, offer.blocks) }
    // The second page: a name no template has yet, for the "new template" choice.
    var naming by remember(offer) { mutableStateOf(false) }
    var name by remember(offer) {
        val taken = templates.mapTo(HashSet()) { it.name }
        mutableStateOf(generateSequence(2) { it + 1 }.map { "${offer.template.name} $it" }.first { it !in taken })
    }
    val close = { model.templateOffer = null }
    val valuesPart = "Updates values for ${plural(values, "set")}."
    val shapePart = listOfNotNull(
        change.addedExercises.takeIf { it > 0 }?.let { "Adds ${plural(it, "exercise")}." },
        change.removedExercises.takeIf { it > 0 }?.let { "Removes ${plural(it, "exercise")}." },
        change.addedSets.takeIf { it > 0 }?.let { "Adds ${plural(it, "set")}." },
        change.removedSets.takeIf { it > 0 }?.let { "Removes ${plural(it, "set")}." },
    ).joinToString(" ").ifEmpty { "Changes the order or the kinds of sets." }
    // One of the four choices has to be made: a tap outside or Back does not close it (2026-10-16, the user's word).
    CleanDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(if (naming) "Create new template" else "Update template?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                if (naming) {
                    OutlinedTextField(name, { name = it }, label = { Text("Template name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    MainButton("Create", { model.save(Template(0, name.trim(), offer.blocks, offer.template.folder)); model.message = "New template saved"; close() }, Modifier.fillMaxWidth(), enabled = name.isNotBlank())
                    QuietButton("Back", { naming = false }, Modifier.fillMaxWidth())
                } else {
                    Text("You've made changes to your original template. Would you like to update it?")
                    // The first update on offer is the highlighted one.
                    var main = true
                    if (values > 0) {
                        MainButton("Update values only", { model.save(offer.template.copy(blocks = withValuesUpdated(before, offer.blocks))); model.message = "Template values updated"; close() }, Modifier.fillMaxWidth(), summary = valuesPart)
                        main = false
                    }
                    if (structure) {
                        val title = if (values > 0) "Update template and values" else "Update template"
                        val summary = if (values > 0) "$valuesPart $shapePart" else shapePart
                        val action = { model.save(offer.template.copy(blocks = withStructureUpdated(before, offer.blocks))); model.message = "Template updated"; close() }
                        if (main) MainButton(title, action, Modifier.fillMaxWidth(), summary = summary) else QuietButton(title, action, Modifier.fillMaxWidth(), summary = summary)
                    }
                    QuietButton("Keep original template", close, Modifier.fillMaxWidth(), summary = "Changes nothing.")
                    QuietButton("Create new template", { naming = true }, Modifier.fillMaxWidth(), summary = "Saves this workout as another template.")
                }
            }
        },
        confirmButton = {},
    )
}

/** A template's blocks as the JSON they are stored as, for keeping the editor's list in the saved state. */
private val TemplateBlocksSaver = Saver<List<TemplateBlock>, String>(
    save = { Template(0, "", it).blocksJson().toString() },
    restore = { Template.blocksOf(clean.app.data.JSONArray(it)) },
)

/** One of the two ways to begin a workout at the top of the Workout sheet (round 28): a tile with a picture, what it does and one line on how. */
@Composable
private fun StartCard(title: String, line: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Tile("", modifier, onClick = onClick, minHeight = 104.dp) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
