package clean.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.CleanColors
import clean.app.ui.theme.LocalCleanColors
import clean.core.shortNumber
import java.time.LocalDate

/** The standard adult ranges: where each starts, its name and its colour on the chart. */
private fun bmiBands(c: CleanColors) = listOf(
    Triple(0.0, "Underweight", c.underweight),
    Triple(18.5, "Healthy weight", c.ready),
    Triple(25.0, "Overweight", c.recovering),
    Triple(30.0, "Obese", c.fatigued),
)

fun bmi(kg: Double, heightCm: Double): Double = kg / ((heightCm / 100) * (heightCm / 100))

private fun one(value: Double) = shortNumber(Math.round(value * 10) / 10.0)

/** Body mass index over time, worked out from the weigh-ins and the profile's height; nothing is stored for it. */
@Composable
fun BmiScreen(model: AppModel) {
    val measurements by model.measurements.collectAsState()
    val profile by model.profile.collectAsState()
    val height = profile?.heightCm?.takeIf { it > 0 }
    val unit = profile?.unit ?: "kg"
    val bands = bmiBands(LocalCleanColors.current)
    var range by remember { mutableStateOf(0L) }
    Scaffold(containerColor = pageContainer(), topBar = { BackBar("BMI", model::back) }) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            if (height == null) {
                item {
                    Section("Height needed") {
                        Text("BMI is your weight divided by your height squared. Enter your height in Settings and it is worked out for every weigh-in.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MainButton("Open Settings", { model.open(Screen.Settings) })
                    }
                }
                return@LazyColumn
            }
            // Oldest first.
            val all: List<Pair<LocalDate, Double>> = measurements.filter { it.file == "weight.csv" }.mapNotNull { m ->
                val day = parseDate(m.date)?.toLocalDate() ?: return@mapNotNull null
                m.value.toDoubleOrNull()?.let { day to bmi(it, height) }
            }.reversed()
            val latest = all.lastOrNull()
            item {
                Section("") {
                    if (latest == null) Text("Log a body weight and your BMI appears here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        val band = bands.last { latest.second >= it.first }
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                            Text(one(latest.second), style = MaterialTheme.typography.displaySmall)
                            Text(band.second, Modifier.padding(bottom = Space.s), color = band.third)
                        }
                        LabelValue("From", prettyDay(latest.first)) { model.open(Screen.MeasurementDetail("weight.csv")) }
                    }
                    LabelValue("Height", "${shortNumber(height)} cm") { model.open(Screen.Settings) }
                    val low = 18.5 * (height / 100) * (height / 100)
                    val high = 25.0 * (height / 100) * (height / 100)
                    LabelValue("Healthy range at this height", "${showWeight(Math.round(low * 10) / 10.0, unit)} to ${showWeight(Math.round(high * 10) / 10.0, unit)} $unit")
                }
            }
            item {
                Section("Over time") {
                    Choice(listOf(90L, 365L, 0L), range, { if (it == 0L) "All" else if (it == 90L) "3M" else "1Y" }) { range = it }
                    val shown = if (range == 0L) all else all.filter { it.first >= LocalDate.now().minusDays(range) }
                    if (shown.size < 2) Text("Not enough weigh-ins in this period to draw a line.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else LineChart(
                        shown, "",
                        // Always show the healthy band and a little of its neighbours.
                        range = 17.0..27.0,
                        bands = bands.mapIndexed { i, b -> b.first..(bands.getOrNull(i + 1)?.first ?: 100.0) to b.third },
                        onTap = { model.open(Screen.MeasurementDetail("weight.csv")) },
                    )
                    for ((i, b) in bands.withIndex()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            Box(Modifier.size(12.dp).background(b.third, RoundedCornerShape(3.dp)))
                            val end = bands.getOrNull(i + 1)?.first
                            Text(b.second, Modifier.weight(1f))
                            Text(if (i == 0) "under ${one(end!!)}" else if (end == null) "${one(b.first)} and over" else "${one(b.first)} to ${one(end)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Section("How far to trust it") {
                    Text(
                        "BMI only compares weight with height. It cannot tell muscle from fat, so it is not a reliable measure for a muscular build: someone heavy with low body fat reads as overweight or obese. Treat it as a rough guide, and look at body fat or waist measurements alongside it.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("These are the standard ranges for adults; they are the same at every adult age. They do not apply to under-20s, who are assessed against growth charts for their age and sex.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
