package clean.app

import clean.app.ui.theme.numbers
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import clean.app.data.Profile
import clean.app.ui.theme.cardShape
import clean.app.ui.theme.SmallShape
import clean.core.cmToInches
import clean.core.feetAndInches
import clean.core.inchesToCm
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Six questions, one to a page. */
private const val QUESTIONS = 6
private const val LAST = QUESTIONS - 1

/** Where a ruler starts when nothing is known: 170 cm (67 inches), 70 kg (155 lb). */
private const val START_CM = 170.0
private const val START_INCHES = 67

private fun heightRuler(unit: String) =
    if (unit == "lb") RulerSpec(48, 96, 1, 12, { "${it / 12}′" }, "Height", { v -> feetAndInches(v.roundToInt()).let { (feet, inches) -> "$feet feet $inches inches" } }, "1 inch")
    else RulerSpec(100, 230, 1, 10, { it.toString() }, "Height", { "${it.roundToInt()} centimetres" }, "1 centimetre")

/**
 * The welcome questionnaire (rules in docs/design.md): one question to a page, shown once to a profile that has no data
 * (`AppModel.welcoming`). An answer is written, through the function that already writes that thing, when its page is left
 * with Next; a page passed with Skip or Back writes nothing. Skip at the top ends all of it and keeps what was written.
 */
@Composable
fun WelcomeScreen(model: AppModel) {
    val profile by model.profile.collectAsState()
    val first = profile ?: return
    val motion = LocalMotion.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(first.name) }
    var unit by rememberSaveable { mutableStateOf(first.unit) }
    // Height in centimetres, and weight in [unit]; null until the ruler has been moved, which is how a page passed unanswered is told.
    var height by rememberSaveable { mutableStateOf(first.heightCm) }
    var weight by rememberSaveable { mutableStateOf<Double?>(null) }
    var weighed by rememberSaveable { mutableStateOf(false) }
    var days by rememberSaveable { mutableIntStateOf(3) }
    var target by rememberSaveable { mutableStateOf<Int?>(null) }
    // The profile as written so far, so that two answers in a row never write over each other.
    var saved by remember { mutableStateOf(first) }

    fun update(change: (Profile) -> Profile) { saved = change(saved); model.update(saved) }
    fun write(page: Int) {
        when (page) {
            0 -> name.trim().takeIf { it.isNotEmpty() && it != saved.name }?.let { n -> update { it.copy(name = n) } }
            1 -> if (unit != saved.unit) update { it.copy(unit = unit) }
            2 -> height?.takeIf { it != saved.heightCm }?.let { cm -> update { it.copy(heightCm = cm) } }
            // Logged once: going back and changing it afterwards is done on the weight page.
            3 -> weight?.takeIf { !weighed }?.let { w -> weighed = true; scope.launch { model.addMeasurement("weight.csv", toKg(w, unit)) } }
            4 -> { model.setWeeklyTarget(days); target = days }
        }
    }
    fun next() {
        write(step)
        if (step == LAST) model.finishWelcome() else step++
    }
    // History brought in ends the questionnaire on Home; the weekly target answered before it then keeps the imported weeks at the user's own pace.
    val history = rememberHistoryIn(model) { model.finishWelcome(target) }

    BackHandler(enabled = step > 0) { step-- }
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = Space.l)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            val quiet = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            if (step > 0) TextButton({ step-- }, Modifier.align(Alignment.CenterStart).minTouch(), colors = quiet) { Text("Back") }
            Row(Modifier.clearAndSetSemantics { contentDescription = "Question ${step + 1} of $QUESTIONS" }, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                repeat(QUESTIONS) { i ->
                    Box(Modifier.size(width = 20.dp, height = 4.dp).background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), SmallShape))
                }
            }
            TextButton({ model.finishWelcome() }, Modifier.align(Alignment.CenterEnd).minTouch(), colors = quiet) { Text("Skip") }
        }
        AnimatedContent(
            step, Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                Motion.blinkIn(motion) togetherWith Motion.blinkOut(motion)
            },
            label = "question",
        ) { page ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.l)) {
                when (page) {
                    0 -> Question("What should we call you?", "The name of your profile. You can change it any time in Settings.") {
                        OutlinedTextField(
                            name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { next() }),
                        )
                        // Coming from another phone: the backup holds the profile and its answers, so a restore skips the rest (the app restarts into it).
                        Text("Moving from another phone?", Modifier.padding(top = Space.s), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        QuietButton("Restore a backup", { history.pickRestore() }, Modifier.fillMaxWidth())
                    }
                    1 -> Question("Kilograms or pounds?", "Weights are shown in this unit. You can switch any time.") {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                            for (choice in listOf("kg", "lb")) BigChoice(choice, unit == choice, Modifier.weight(1f)) {
                                if (unit != choice) {
                                    unit = choice
                                    // A weight picked in the other unit means nothing now.
                                    if (!weighed) weight = null
                                }
                            }
                        }
                    }
                    2 -> Question("How tall are you?", "Used for BMI only.") {
                        val inches = unit == "lb"
                        val shown = height?.let { if (inches) cmToInches(it).toDouble() else it.roundToInt().toDouble() } ?: (if (inches) START_INCHES.toDouble() else START_CM)
                        val text = if (inches) feetAndInches(shown.roundToInt()).let { (feet, inch) -> "$feet′ $inch″" } else "${shown.roundToInt()} cm"
                        Reading(text, height != null)
                        Ruler(shown, { v -> height = if (inches) inchesToCm(v.roundToInt()) else v.roundToInt().toDouble() }, heightRuler(unit))
                        QuietButton("I'd rather not say", { height = first.heightCm; step++ }, Modifier.fillMaxWidth())
                    }
                    3 -> Question("What do you weigh?", "Bodyweight exercises and the weight chart use it.") {
                        val shown = weight ?: if (unit == "lb") 155.0 else 70.0
                        Reading("${weightText(shown)} $unit", weight != null)
                        Ruler(shown, { weight = it }, weightRuler(unit))
                        QuietButton("Skip this", { weight = null; step++ }, Modifier.fillMaxWidth())
                    }
                    4 -> Question("How many days a week do you want to train?", "Your week ring and streak count against it.") {
                        for (row in listOf(1..4, 5..7)) Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            for (d in row) BigChoice("$d", days == d, Modifier.weight(1f)) { days = d }
                            repeat(4 - row.count()) { Spacer(Modifier.weight(1f)) }
                        }
                        Text("${plural(days, "day")} a week", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // Where you train (places) is asked here, before the history, once places are built.
                    else -> Question("Bring your history?", "Workouts from another app, or a backup made by Clean.") {
                        QuietButton("Import from another app", { history.pickImport() }, Modifier.fillMaxWidth())
                        QuietButton("Restore a Clean backup", { history.pickRestore() }, Modifier.fillMaxWidth())
                        QuietButton("Start fresh", { model.finishWelcome() }, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        MainButton(if (step == LAST) "Done" else "Next", { next() }, Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = Space.s))
    }
}

/** A question's big title, one short line of why, and its control. */
@Composable
private fun Question(title: String, why: String, control: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = Space.l), verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(why, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column(Modifier.padding(top = Space.s), verticalArrangement = Arrangement.spacedBy(Space.l), content = control)
}

/** The value a ruler shows as a big number, muted until the ruler has been moved (until then it answers nothing). */
@Composable
private fun ColumnScope.Reading(text: String, answered: Boolean) {
    Text(
        text, Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.displayMedium.numbers(), fontWeight = FontWeight.Bold,
        color = if (answered) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One of a few big choices, exclusive: outlined, and in the accent when chosen. */
@Composable
private fun BigChoice(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier.heightIn(min = 72.dp).clip(cardShape())
            .background(if (selected) accent.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainer, cardShape())
            .border(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant, cardShape())
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = if (selected) accent else MaterialTheme.colorScheme.onSurface)
    }
}
