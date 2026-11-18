package clean.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import clean.core.LoadType
import clean.core.WeightGoal
import clean.core.exerciseGoalProgress
import clean.core.parseGoal
import clean.core.projectedLine
import java.time.LocalDate

/*
 * Goals (added 2026-10-16): an exercise's goal (set on its Info tab), a body-weight goal (kv `goal.weight:<profileId>`) and the weekly target of
 * workouts, each with how far it has come and, where the last eight weeks' trend says anything, a day. Rules: docs/domain.md, Goals.
 */

/** One goal as the Goals card lists it: [fraction] of the way, a line of [detail] under the name and the page it opens. */
internal class GoalRow(val name: String, val fraction: Double, val detail: String, val open: () -> Unit) {
    val percent: String get() = "${Math.round(fraction * 100)} %"
}

/** The body-weight goal of the active profile, null when there is none. */
@Composable
internal fun weightGoalOf(model: AppModel): WeightGoal? {
    val options by model.options.collectAsState()
    val id by model.activeId.collectAsState()
    return remember(options, id) { WeightGoal.decode(options["goal.weight:$id"]) }
}

/** Stores a body-weight goal of [targetKg], taking [currentKg] as where it starts; says so when the two are the same. */
internal fun setWeightGoal(model: AppModel, targetKg: Double, currentKg: Double) {
    val id = model.activeId.value ?: return
    val goal = WeightGoal.set(targetKg, currentKg, LocalDate.now())
    if (goal == null) model.message = "Pick a weight different from your current one"
    else model.setOption("goal.weight:$id", goal.encode())
}

/** The goals that exist, exercises first (A to Z), then body weight, then the weekly target. */
@Composable
internal fun goalRows(model: AppModel): List<GoalRow> {
    val workouts by model.workouts.collectAsState()
    val settings by model.settings.collectAsState()
    val bodyweights by model.bodyweights.collectAsState()
    val rule by model.oneRm.collectAsState()
    val consistency by model.consistency.collectAsState()
    val profile by model.profile.collectAsState()
    val goal = weightGoalOf(model)
    val weights = weighIns(model)
    return remember(workouts, settings, bodyweights, rule, consistency, profile, goal, weights) {
        val today = LocalDate.now()
        val unit = profile?.unit ?: "kg"
        val rows = mutableListOf<GoalRow>()
        for (name in settings.keys.sorted()) {
            val (kg, reps) = parseGoal(settings[name]?.goal) ?: continue
            val loadType = model.loadTypeOf(name)
            val p = exerciseGoalProgress(model.sessionsIn(workouts, name), kg, reps, rule, loadType, bodyweights::at, today) ?: continue
            val sign = when (loadType) { LoadType.BODYWEIGHT -> "+"; LoadType.ASSISTED -> "−"; else -> "" }
            val about = "Goal $sign${showWeight(kg, model.unitOf(name))} × $reps"
            rows += GoalRow(name, p.fraction, listOfNotNull(about, if (p.reached) "Reached" else p.projected?.let { projectedLine(it, today) }).joinToString(" · ")) {
                model.open(Screen.ExerciseDetail(name, tab = 0))
            }
        }
        if (goal != null) {
            val p = goal.progressOf(weights, today)
            val about = "${if (goal.lose) "Lose to" else "Gain to"} ${showWeight(goal.targetKg, unit)} $unit"
            rows += GoalRow("Body weight", p?.fraction ?: 0.0, listOfNotNull(about, if (p?.reached == true) "Reached" else p?.projected?.let { projectedLine(it, today) }).joinToString(" · ")) {
                model.open(Screen.MeasurementDetail("weight.csv"))
            }
        }
        consistency?.takeIf { workouts.isNotEmpty() }?.let { c ->
            val week = c.now
            val streak = c.streak.length
            rows += GoalRow(
                "Workouts per week", (week.count.toDouble() / week.target.coerceAtLeast(1)).coerceIn(0.0, 1.0),
                "${week.count} of ${week.target} this week" + if (streak > 0) " · ${plural(streak, "week")} streak" else "",
            ) { model.open(Screen.Trophies) }
        }
        rows
    }
}

/** A goal as a row: its name and percent, a thin bar, and the line under it. */
@Composable
internal fun GoalLine(row: GoalRow) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = row.open).minTouch().padding(vertical = Space.xs)
            .semantics(mergeDescendants = true) { contentDescription = "${row.name}, ${Math.round(row.fraction * 100)} percent. ${row.detail}" },
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(row.name, Modifier.weight(1f), maxLines = 1)
            Text(row.percent, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(
            progress = { row.fraction.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {},
        )
        if (row.detail.isNotEmpty()) Text(row.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The goals that exist, with how far each has come; wide it also offers "Set a goal", half it lists the top two. */
@Composable
internal fun GoalsCard(model: AppModel, half: Boolean) {
    val rows = goalRows(model)
    var choosing by remember { mutableStateOf(false) }
    if (choosing) SetGoalSheet(model) { choosing = false }
    Section("Goals", spacing = Space.xs, onClick = if (rows.isEmpty()) ({ choosing = true }) else null) {
        if (rows.isEmpty()) EmptyPreview(if (half) "No goals yet. Tap to set one." else "Set a goal and how far you have come shows here.") { PreviewSamples.Rows(if (half) 36.dp else 48.dp, PreviewSamples.ROWS.take(2)) }
        for (row in if (half) rows.take(2) else rows) GoalLine(row)
        if (!half) QuietButton("Set a goal", { choosing = true })
    }
}

/** Where a new goal starts: an exercise (its picker, then the weight and reps) or body weight (the ruler). */
@Composable
internal fun SetGoalSheet(model: AppModel, onDismiss: () -> Unit) {
    val workouts by model.workouts.collectAsState()
    val current = weighIns(model).lastOrNull()?.second
    var picking by remember { mutableStateOf(false) }
    var exercise by remember { mutableStateOf<String?>(null) }
    var weighing by remember { mutableStateOf(false) }
    if (picking) ExercisePicker(model, workouts, { picking = false; exercise = it }, { picking = false })
    exercise?.let { name -> ExerciseGoalDialog(model, name) { exercise = null; onDismiss() } }
    if (weighing && current != null) WeightSheet(model, "Target weight", { kg -> setWeightGoal(model, kg, current) }) { weighing = false; onDismiss() }
    if (picking || exercise != null || weighing) return
    Sheet(onDismiss, "Set a goal") {
        Column(Modifier.padding(horizontal = Space.xl)) {
            NavRow("Exercise", "", { picking = true })
            NavRow("Body weight", "", { if (current == null) model.message = "Log a weight first" else weighing = true })
        }
    }
}

/** A weight and reps to work towards on [name]; empty fields remove the goal. The same value the Info tab's Edit fields keep. */
@Composable
internal fun ExerciseGoalDialog(model: AppModel, name: String, onDismiss: () -> Unit) {
    val settings by model.settings.collectAsState()
    val unit = model.unitOf(name)
    val existing = parseGoal(settings[name]?.goal)
    var weight by remember { mutableStateOf(existing?.first?.let { entryWeight(it, unit) }.orEmpty()) }
    var reps by remember { mutableStateOf(existing?.second?.toString().orEmpty()) }
    val kg = parseNumber(weight)?.takeIf { it > 0 }?.let { toKg(it, unit) }
    val count = reps.trim().toIntOrNull()?.takeIf { it > 0 }
    CleanDialog(
        onDismissRequest = onDismiss,
        title = { Text("Goal for $name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Text("A set to work towards. Progress is your best estimated 1RM against its estimate.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    OutlinedTextField(weight, { weight = it }, label = { Text("Weight ($unit)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(reps, { reps = it }, label = { Text("Reps") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = kg != null && count != null, onClick = {
                model.putSetting(name) { it.copy(goal = "${kg}x$count") }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (existing != null) DangerButton("Remove goal", { model.putSetting(name) { it.copy(goal = "") }; onDismiss() })
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * The weight page's goal: a quiet "Set a goal", or the goal with its bar, percent and day, a tap to change it and a way to remove it.
 * The chart beside it draws the target as its goal line.
 */
@Composable
internal fun WeightGoalSection(model: AppModel, unit: String) {
    val goal = weightGoalOf(model)
    val weights = weighIns(model)
    val id by model.activeId.collectAsState()
    var setting by remember { mutableStateOf(false) }
    val current = weights.lastOrNull()?.second
    if (setting) WeightSheet(model, "Target weight", { kg -> if (current != null) setWeightGoal(model, kg, current) }) { setting = false }
    val today = LocalDate.now()
    Column(Modifier.padding(horizontal = Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        if (goal == null) {
            QuietButton("Set a goal", { if (current == null) model.message = "Log a weight first" else setting = true })
            return@Column
        }
        val p = goal.progressOf(weights, today)
        LabelValue(
            "Goal", "${if (goal.lose) "Lose to" else "Gain to"} ${showWeight(goal.targetKg, unit)} $unit ›",
            spoken = "Goal, ${if (goal.lose) "lose" else "gain"} to ${showWeight(goal.targetKg, unit)} $unit. Change it",
        ) { setting = true }
        LinearProgressIndicator(
            progress = { (p?.fraction ?: 0.0).toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant, drawStopIndicator = {},
        )
        Text(
            (if (p?.reached == true) "Reached" else "${Math.round((p?.fraction ?: 0.0) * 100)} % of the way from ${showWeight(goal.startKg, unit)} $unit") +
                (p?.projected?.let { " · ${projectedLine(it, today)}" } ?: ""),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DangerButton("Remove goal", { model.setOption("goal.weight:$id", null) })
    }
}
