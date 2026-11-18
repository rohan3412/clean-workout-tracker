package clean.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import clean.core.comparePose
import clean.core.shortNumber
import clean.core.volumeOf
import java.time.format.DateTimeFormatter

private val SINCE_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

/**
 * Profile (F4), a page over Home from the initial at its top right: who this is, then fixed sections on the page (a small heading, rows split
 * by hairlines, no cards): Measurements, Photos, Goals and trophies, Lifetime, and Switch profile. Everything is optional; a part with
 * nothing in it is one quiet line that adds the first.
 */
@Composable
fun ProfileScreen(model: AppModel) {
    if (model.profileEdit) ProfileEditDialog(model) { model.profileEdit = false }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar("Profile", model::back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { ProfileTop(model) }
            item { MeasurementsSection(model) }
            item { PhotosSection(model) }
            item { GoalsSection(model) }
            item { LifetimeSection(model) }
            item { SwitchRow(model) }
            item { BarSpace() }
        }
    }
}

/** The profile's name, units and height are edited, and profiles switched, in a dialog on this page (it was a Settings page before 1.1.0). */
private fun AppModel.editProfile() { profileEdit = true }

/** The initial in a circle, the name, and "kg · since April 2026 · Edit". */
@Composable
private fun ProfileTop(model: AppModel) {
    val profile by model.profile.collectAsState()
    val workouts by model.workouts.collectAsState()
    val first = remember(workouts) { workouts.firstOrNull()?.let { parseDate(it.date)?.toLocalDate() } }
    val name = profile?.name.orEmpty().ifBlank { "Me" }
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.l)) {
        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text(name.trim().firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val quiet = MaterialTheme.colorScheme.onSurfaceVariant
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(listOfNotNull(profile?.unit ?: "kg", first?.let { "since ${it.format(SINCE_MONTH)}" }).joinToString(" · ") + " ·", style = MaterialTheme.typography.bodyMedium, color = quiet)
                Text("Edit", Modifier.clickable { model.editProfile() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** A section of the page: its small heading with [trailing] at the right, then rows. */
@Composable
private fun ProfileSection(title: String, trailing: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = Space.xl)) {
        PageHeading(title, Modifier.padding(horizontal = Space.l), trailing)
        Column(Modifier.padding(top = Space.xs)) { content() }
    }
}

/** A small text action at the right of a heading ("Add", "Compare"). */
@Composable
private fun HeadingAction(text: String, onClick: () -> Unit) {
    Box(Modifier.clickable(onClick = onClick).minTouch().padding(horizontal = Space.xs), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
    }
}

/** One row of a section: [label] (and a grey [sub] line) at the left, [value] at the right, a hairline under it. A row with no [onClick] is a plain line. */
@Composable
private fun ProfileRow(label: String, value: String = "", sub: String = "", quiet: Boolean = false, onClick: (() -> Unit)? = null) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).minTouch().padding(horizontal = Space.l, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = if (quiet) grey else MaterialTheme.colorScheme.onSurface)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = grey)
        }
        if (value.isNotEmpty()) Text(value, style = MaterialTheme.typography.bodyLarge, color = if (quiet) grey else MaterialTheme.colorScheme.onSurface)
        if (onClick != null && !quiet) Text("›", color = grey)
    }
    HorizontalDivider(Modifier.padding(horizontal = Space.l), color = MaterialTheme.colorScheme.outlineVariant)
}

/** The latest of each measurement with its day, Height, BMI once there is a height and a weigh-in, and the page with all of them. */
@Composable
private fun MeasurementsSection(model: AppModel) {
    val files by model.files.collectAsState()
    val measurements by model.measurements.collectAsState()
    val profile by model.profile.collectAsState()
    val unit = profile?.unit ?: "kg"
    var adding by remember { mutableStateOf(false) }
    if (adding) NewMeasurementDialog(model) { adding = false }
    // Newest first per file, as the Measurements page reads them.
    val rows = remember(files, measurements) {
        val byFile = measurements.groupBy { it.file }
        files.mapNotNull { f -> byFile[f.name]?.let { f to it.first() } }.sortedBy { if (it.first.name == "weight.csv") 0 else 1 }
    }
    val height = profile?.heightCm?.takeIf { it > 0 }
    val kg = rows.firstOrNull { it.first.name == "weight.csv" }?.second?.value?.toDoubleOrNull()
    ProfileSection("Measurements", { HeadingAction("Add") { adding = true } }) {
        for ((file, last) in rows.take(6)) {
            val (label, fileUnit) = splitHeader(file.header)
            val suffix = if (file.name == "weight.csv") unit else fileUnit
            ProfileRow(label, "${shown(last, unit)} $suffix".trim(), prettyDay(last.date)) { model.open(Screen.MeasurementDetail(file.name)) }
        }
        ProfileRow("Height", height?.let { "${shortNumber(it)} cm" } ?: "Add", onClick = { model.editProfile() })
        if (kg != null && height != null) ProfileRow("BMI", shortNumber(Math.round(bmi(kg, height) * 10) / 10.0)) { model.open(Screen.Bmi) }
        ProfileRow("All measurements") { model.open(Screen.Measurements) }
    }
}

/** The newest three photos in a row; with none, one quiet line that adds the first. */
@Composable
private fun PhotosSection(model: AppModel) {
    val photos by model.photos.collectAsState()
    var adding by remember { mutableStateOf(false) }
    AddPhotoHost(model, adding) { adding = false }
    val pose = remember(photos) { comparePose(photos, null) }
    ProfileSection("Photos", { if (pose != null) HeadingAction("Compare") { model.open(Screen.PhotoCompare(pose)) } }) {
        if (photos.isEmpty()) ProfileRow("Add a photo", quiet = true, onClick = { adding = true })
        else {
            Row(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.xs), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                for (slot in 0 until 3) {
                    val photo = photos.getOrNull(slot)
                    val cell = Modifier.weight(1f).aspectRatio(3f / 4f).clip(MaterialTheme.shapes.small)
                    if (photo == null) Box(cell) else PhotoThumb(
                        model, photo, 360,
                        cell.semantics { contentDescription = "${photo.pose.label} photo, ${prettyDay(photo.day)}" }.clickable { model.open(Screen.Photo(photo.name, null)) },
                    )
                }
            }
            ProfileRow("All photos", plural(photos.size, "photo")) { model.open(Screen.Photos) }
        }
    }
}

/** Each goal as a row with how far it has come and, where the trend says so, when; Trophies with the badges earned; "Set a goal" when there is none. */
@Composable
private fun GoalsSection(model: AppModel) {
    val goals = goalRows(model)
    val consistency by model.consistency.collectAsState()
    val awards = consistency?.awards.orEmpty()
    var choosing by remember { mutableStateOf(false) }
    if (choosing) SetGoalSheet(model) { choosing = false }
    ProfileSection("Goals and trophies", { if (goals.isNotEmpty()) HeadingAction("Add") { choosing = true } }) {
        for (g in goals) ProfileRow(g.name, g.percent, g.detail, onClick = g.open)
        if (goals.isEmpty()) ProfileRow("Set a goal", quiet = true, onClick = { choosing = true })
        ProfileRow("Trophies", if (awards.isEmpty()) "" else "${awards.count { it.earned != null }} earned") { model.open(Screen.Trophies) }
    }
}

/** Workouts, time trained and volume lifted since the first workout, as plain lines. */
@Composable
private fun LifetimeSection(model: AppModel) {
    val workouts by model.workouts.collectAsState()
    // settings and bodyweights are keys only so the volume is worked out again when they change.
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val volume = remember(workouts, settings, bodyweights) { workouts.sumOf { volumeOf(it, model::loadOf) } }
    ProfileSection("Lifetime") {
        if (workouts.isEmpty()) ProfileRow("Start a workout", quiet = true, onClick = { model.openWorkoutSheet() })
        else {
            ProfileRow("Workouts", workouts.size.toString())
            ProfileRow("Time trained", duration(workouts.sumOf { it.durationSeconds }))
            ProfileRow("Volume lifted", model.volume(volume))
        }
    }
}

/** Switching profile (or adding one) is the Settings page that lists them. */
@Composable
private fun SwitchRow(model: AppModel) {
    Column(Modifier.padding(top = Space.xl)) { ProfileRow("Switch profile") { model.editProfile() } }
}
