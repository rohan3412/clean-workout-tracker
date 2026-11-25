package clean.app

import clean.core.time.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.CleanColors
import clean.app.ui.theme.LocalCleanColors
import clean.core.shortNumber
import kotlinx.datetime.LocalDate

/** The standard adult ranges: where each starts, its name and its colour on the chart. */
internal fun bmiBands(c: CleanColors) = listOf(
    Triple(0.0, "Underweight", c.underweight),
    Triple(18.5, "Healthy weight", c.ready),
    Triple(25.0, "Overweight", c.recovering),
    Triple(30.0, "Obese", c.fatigued),
)

fun bmi(kg: Double, heightCm: Double): Double = kg / ((heightCm / 100) * (heightCm / 100))

private fun one(value: Double) = shortNumber(roundHalfUp(value * 10) / 10.0)

/** The band [value] falls in. */
internal fun bmiBandOf(value: Double, bands: List<Triple<Double, String, Color>>) = bands.last { value >= it.first }

/** The bar's scale: the bands side by side from [BAR_FROM] to [BAR_TO], so the healthy one is the widest. */
private const val BAR_FROM = 15.0
private const val BAR_TO = 40.0

/**
 * The four ranges as one strip in their colours, with a marker where [value] is (null: no marker). [height] is the strip; the marker stands a little over it.
 * Decoration for the figure above it, which says the same in words, so a screen reader reads [spoken].
 */
@Composable
internal fun BmiBandBar(value: Double?, bands: List<Triple<Double, String, Color>>, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 10.dp, spoken: String? = null) {
    val marker = MaterialTheme.colorScheme.onSurface
    val ground = MaterialTheme.colorScheme.background
    val edge = height / 2
    Canvas(
        modifier.fillMaxWidth().height(height + 12.dp).then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier),
    ) {
        val gap = 2.dp.toPx()
        val top = 6.dp.toPx()
        val h = height.toPx()
        fun x(v: Double) = ((v.coerceIn(BAR_FROM, BAR_TO) - BAR_FROM) / (BAR_TO - BAR_FROM)).toFloat() * size.width
        for ((i, b) in bands.withIndex()) {
            val from = x(maxOf(b.first, BAR_FROM)) + (if (i == 0) 0f else gap / 2)
            val to = x(bands.getOrNull(i + 1)?.first ?: BAR_TO) - (if (i == bands.lastIndex) 0f else gap / 2)
            val round = edge.toPx()
            // The ends of the strip are round, the joins between ranges square.
            drawRoundRect(b.third, Offset(from, top), Size((to - from).coerceAtLeast(1f), h), CornerRadius(if (i == 0 || i == bands.lastIndex) round else 2.dp.toPx()))
        }
        if (value != null) {
            val at = x(value)
            drawCircle(ground, 8.dp.toPx(), Offset(at, top + h / 2))
            drawCircle(marker, 6.dp.toPx(), Offset(at, top + h / 2))
        }
    }
}

/** Body mass index over time, worked out from the weigh-ins and the profile's height; nothing is stored for it. */
@Composable
fun BmiScreen(model: AppModel) {
    val measurements by model.measurements.collectAsState()
    val profile by model.profile.collectAsState()
    val height = profile?.heightCm?.takeIf { it > 0 }
    val unit = profile?.unit ?: "kg"
    val bands = bmiBands(LocalCleanColors.current)
    val seen = remember { HashSet<String>() }
    var why by rememberSaveable { mutableStateOf(false) }
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
                if (latest == null) EmptyState("No BMI yet", "Log a body weight and your BMI appears here.", "Log weight", { model.logWeight() })
                else Section("Your BMI", Modifier.settleIn(0)) {
                    val band = bmiBandOf(latest.second, bands)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        Text(countUpOnce(seen, "bmi", 0, latest.second) { one(it) }, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
                        Text(band.second, Modifier.padding(bottom = Space.s), style = MaterialTheme.typography.titleMedium, color = band.third)
                    }
                    BmiBandBar(latest.second, bands, spoken = "${one(latest.second)}, ${band.second}")
                    LabelValue("From", prettyDay(latest.first)) { model.open(Screen.MeasurementDetail("weight.csv")) }
                    LabelValue("Height", heightText(height, unit)) { model.open(Screen.Settings) }
                    val low = 18.5 * (height / 100) * (height / 100)
                    val high = 25.0 * (height / 100) * (height / 100)
                    LabelValue("Healthy range at this height", "${showWeight(roundHalfUp(low * 10) / 10.0, unit)} to ${showWeight(roundHalfUp(high * 10) / 10.0, unit)} $unit")
                }
            }
            if (latest == null) item {
                Section("Height") { LabelValue("Height", heightText(height, unit)) { model.open(Screen.Settings) } }
            }
            item {
                Section("Over time", Modifier.settleIn(1)) {
                    val selector = model.measurementRangeSelector()
                    val range = selector.days
                    val shown = if (range == 0L) all else all.filter { it.first >= LocalDate.now().minusDays(range) }
                    if (shown.size < 2) Text("Not enough weigh-ins in this period to draw a line.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else LineChart(
                        shown, "",
                        // Always show the healthy band and a little of its neighbours.
                        range = 17.0..27.0,
                        bands = bands.mapIndexed { i, b -> b.first..(bands.getOrNull(i + 1)?.first ?: 100.0) to b.third },
                        onTap = { model.open(Screen.MeasurementDetail("weight.csv")) },
                        selector = selector,
                    )
                    for ((i, b) in bands.withIndex()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                            Box(Modifier.size(12.dp).background(b.third, clean.app.ui.theme.LegendShape))
                            val end = bands.getOrNull(i + 1)?.first
                            Text(b.second, Modifier.weight(1f))
                            Text(if (i == 0) "under ${one(end!!)}" else if (end == null) "${one(b.first)} and over" else "${one(b.first)} to ${one(end)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                Section("About BMI", Modifier.settleIn(2)) {
                    Text("BMI only compares weight with height, so it cannot tell muscle from fat. Treat it as a rough guide.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.TextButton(onClick = { why = !why }) {
                        Text("Why this may mislead")
                        Icon(if (why) CleanIcons.ChevronUp else CleanIcons.ChevronDown, null)
                    }
                    val motion = LocalMotion.current
                    AnimatedVisibility(why, enter = Motion.appear(motion), exit = Motion.vanish(motion)) {
                        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                            Text("Someone heavy with low body fat reads as overweight or obese, so it is not a reliable measure for a muscular build. Look at body fat or waist measurements alongside it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("These are the standard ranges for adults; they are the same at every adult age. They do not apply to under-20s, who are assessed against growth charts for their age and sex.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
