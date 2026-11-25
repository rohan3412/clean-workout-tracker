package clean.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.LocalLook
import clean.app.ui.theme.SmallShape
import clean.core.PrKind
import clean.core.SetRange
import clean.core.Side
import clean.core.time.*
import clean.core.shortNumber
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlin.math.pow

/*
 * Round 23: the pieces the rarely visited pages share (Month, Week, Measurements, BMI, an exercise's PRs and History), so they read like the main tabs:
 * one hero, small-caps labels, accent for meaning only. Nothing here is stored; every figure is worked out from the log when it is drawn.
 */

/** [value] with the app's own signs ("+3", "−2", "0"): a minus is U+2212 everywhere, never a hyphen. [places] decimals at most, none trailing. */
fun signed(value: Double, places: Int = 0): String {
    val scale = 10.0.pow(places)
    val rounded = roundHalfUp(value * scale) / scale
    if (rounded == 0.0) return "0"
    return (if (rounded > 0) "+" else "−") + shortNumber(kotlin.math.abs(rounded))
}

/** How far [now] is from [before], in percent ("+12%", "−8%"), or null when there is nothing to compare with. */
fun percentDelta(now: Double, before: Double): String? =
    if (before <= 0.0) null else signed((now - before) / before * 100.0).let { if (it == "0") "0%" else "$it%" }

/** A height as the profile's unit shows it: centimetres, or feet and inches for a profile in pounds ("5 ft 10 in"). Storage stays in centimetres. */
fun heightText(cm: Double, unit: String): String {
    if (unit != "lb") return "${shortNumber(cm)} cm"
    val inches = roundHalfUp(cm / 2.54)
    return "${inches / 12} ft ${inches % 12} in"
}

/** "‹ Month before" and "Month after ›": one component for the Week and Month pages. The way forward is off past today. */
@Composable
fun PeriodNav(before: String, after: String, afterEnabled: Boolean, onBefore: () -> Unit, onAfter: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.s), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBefore) { Text("‹ $before") }
        TextButton(onClick = onAfter, enabled = afterEnabled) { Text("$after ›") }
    }
}

/** A small figure's change against what came before: the accent when it rose (the thing being aimed at), quiet grey when it fell or held. */
@Composable
fun DeltaChip(text: String, rose: Boolean, spoken: String? = null, modifier: Modifier = Modifier) {
    val color = if (rose) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val tint = if (rose) prTint(LocalLook.current.dark) else 0.10f
    Text(
        text, modifier.background(color.copy(alpha = tint), SmallShape).padding(horizontal = Space.s, vertical = Space.xxs)
            .then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier),
        color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
    )
}

/** One of the three smaller figures under a page's hero: [delta] against what came before, when there is something to compare with. */
class HeroStat(val label: String, val value: String, val delta: String?, val rose: Boolean, val spoken: String? = null)

/**
 * The top of a Month or Week page: the volume large with its change against [versus] in the accent, then the workouts, time and PRs smaller beside each other.
 * The strings come counted up by the page (countUpOnce) and the deltas worked out from the log.
 */
@Composable
fun PeriodHero(volume: String, volumeDelta: String?, volumeRose: Boolean, versus: String, stats: List<HeroStat>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Caption("Volume")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                Text(volume, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                if (volumeDelta != null && volumeDelta != "0%") DeltaChip("$volumeDelta vs $versus", volumeRose, spoken = "$volumeDelta against $versus")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            for (s in stats) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Caption(s.label)
                Text(s.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (s.delta != null && s.delta != "0" && s.delta != "0%") DeltaChip(s.delta, s.rose, spoken = s.spoken)
            }
        }
    }
}

/**
 * A muscle's sets as a bar. Against the weekly target (the soft band, [range]) it is amber under it, the accent within it and quiet over it, as the Week page
 * has always said; the thin tick is what is usual ([usual]). [scaleMax] is where the bar's end is, the same for every muscle on the page.
 */
@Composable
fun SetsBar(value: Double, scaleMax: Double, color: Color, modifier: Modifier = Modifier, usual: Double? = null, range: SetRange? = null) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val tick = MaterialTheme.colorScheme.onSurface
    val band = goalColor()
    Canvas(modifier.fillMaxWidth().height(16.dp).clearAndSetSemantics { }) {
        val top = 4.dp.toPx()
        val h = 8.dp.toPx()
        val r = CornerRadius(h / 2)
        fun x(v: Double) = (v / scaleMax).toFloat().coerceIn(0f, 1f) * size.width
        drawRoundRect(track, Offset(0f, top), Size(size.width, h), r)
        if (range != null) drawRect(band.copy(alpha = 0.28f), Offset(x(range.min.toDouble()), top), Size((x(range.max.toDouble()) - x(range.min.toDouble())).coerceAtLeast(2.dp.toPx()), h))
        if (value > 0) drawRoundRect(color, Offset(0f, top), Size(x(value).coerceAtLeast(h), h), r)
        if (usual != null && usual > 0) drawRoundRect(tick, Offset(x(usual) - 1.dp.toPx(), 1.dp.toPx()), Size(2.dp.toPx(), size.height - 2.dp.toPx()), CornerRadius(1.dp.toPx()))
    }
}

/**
 * A month's days as a small calendar, Monday first: a day trained is shaded by the time spent that day (the Calendar's own scale, [palette], faintest to
 * strongest), a rest day is the quiet fill. Tapping a day (not one still to come) is [onDay]; the day says what it holds to a screen reader.
 */
@Composable
fun MonthGrid(month: YearMonth, seconds: Map<LocalDate, Long>, counts: Map<LocalDate, Int>, today: LocalDate, palette: List<Color>, onDay: (LocalDate) -> Unit) {
    val top = seconds.values.maxOrNull()?.takeIf { it > 0 } ?: 1L
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val cells: List<LocalDate?> = List(month.atDay(1).dayOfWeek.isoDayNumber - 1) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Row {
            for (d in "MTWTFSS") Text(d.toString(), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (week in cells.chunked(7)) Row(horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
            for (day in week) {
                if (day == null) { Spacer(Modifier.weight(1f)); continue }
                val s = seconds[day] ?: 0L
                val n = counts[day] ?: 0
                val future = day > today
                val fill = if (s <= 0L) empty else shade(palette, s.toDouble() / top)
                Box(
                    Modifier.weight(1f).height(32.dp).background(fill, SmallShape)
                        .then(if (future) Modifier else Modifier.clickable { onDay(day) })
                        .semantics(mergeDescendants = true) { contentDescription = "${prettyDay(day)}, " + (if (n == 0) "no workout" else plural(n, "workout")) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                        color = if (future) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else if (s <= 0L) MaterialTheme.colorScheme.onSurfaceVariant else fill.readableOn(),
                    )
                }
            }
            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** A PR of a Month or Week page: the exercise and its set, and a [PrChip] for each record it broke instead of the grey words. */
@Composable
fun PrChipRow(model: AppModel, row: PrRow, onClick: () -> Unit) {
    val unit = model.unitOf(row.exercise)
    val set = setValues(row.set, unit)
    val kinds = PrKind.entries.filter { it in row.kinds }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Space.xs)
            .semantics(mergeDescendants = true) { contentDescription = "${row.exercise}, ${prettyDay(row.date)}, ${row.sideWord}set $set, ${kinds.joinToString(", ") { it.label }}" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.exercise, fontWeight = FontWeight.SemiBold)
            Text("${prettyDay(row.date)} · ${row.sidePrefix}$set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            for (k in kinds) PrChip(k.label)
        }
    }
}

/** One muscle's line on the Month and Week pages: its name (opens the muscle), a bar drawn by [bar] and the figure, with a quieter [sub] under it. */
@Composable
fun SetsRow(label: String, value: String, spoken: String, sub: String? = null, onClick: () -> Unit, bar: @Composable (Modifier) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Space.xxs).semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(96.dp), maxLines = 1)
        bar(Modifier.weight(1f))
        Column(Modifier.width(64.dp).padding(start = Space.s), horizontalAlignment = Alignment.End) {
            Text(value, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/**
 * The y range of a measurement's chart: the values with at least 5 % of their span of air above and below, and never narrower than 3 % of their middle
 * (at least 0.5), so a change of 0.4 kg on 82 kg is a small step and not a cliff. Rounded out to a tenth.
 */
fun paddedRange(values: List<Double>): ClosedFloatingPointRange<Double> {
    if (values.isEmpty()) return 0.0..1.0
    val low = values.min()
    val high = values.max()
    val mid = (low + high) / 2
    val span = maxOf((high - low) * 1.1, kotlin.math.abs(mid) * 0.03, 0.5)
    return (kotlin.math.floor((mid - span / 2) * 10) / 10)..(kotlin.math.ceil((mid + span / 2) * 10) / 10)
}

/** The role a measurement's line travels under from a card to its page, one per file: body weight keeps the key Home's and Profile's cards give it, every other file has its own, so a line can never travel to another measurement's card. */
fun measurementMorphKey(fileName: String): String = if (fileName == "weight.csv") "chart:weight" else "chart:m:$fileName"

/** A measurement entry's day in the short form with its time after it, grey ("3 Nov  08:00"). */
@Composable
fun entryDay(date: String, use24Hour: Boolean): androidx.compose.ui.text.AnnotatedString {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val parsed = parseDate(date) ?: return androidx.compose.ui.text.AnnotatedString(date)
    return androidx.compose.ui.text.buildAnnotatedString {
        append(prettyDay(parsed.toLocalDate()))
        withStyle(androidx.compose.ui.text.SpanStyle(color = dim)) { append("  " + parsed.format(LocalePattern(if (use24Hour) "HH:mm" else "h:mm a"))) }
    }
}

/** One record of an exercise as a tile: its figure large with the unit beside it, the set it came from, the day, and each side's own where it was done left and right. A hold peeks at the workout. */
@Composable
fun RecordTile(
    label: String, number: String, unit: String?, detail: String?, date: String, sides: String?, modifier: Modifier = Modifier,
    peek: (@Composable ColumnScope.() -> Unit)?, onClick: () -> Unit,
) {
    Tile(label, modifier, onClick = onClick, peekTag = "record peek", peek = peek) {
        TileNumber(number, unit)
        if (detail != null) TileLine(detail)
        TileLine(date)
        if (sides != null) TileLine(sides)
    }
}

/** One rung of the PRs tab's ladder: a weight, a bar of the most reps done at it (against the best of the ladder, [top]), the reps and the day. */
@Composable
fun LadderRow(weight: String, reps: Int, top: Int, date: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Space.xs).semantics(mergeDescendants = true) { contentDescription = "$weight, ${plural(reps, "rep")}, $date" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(weight, Modifier.width(76.dp), maxLines = 1)
        ThinBar(reps.toFloat() / top, MaterialTheme.colorScheme.primary, 6.dp, Modifier.weight(1f))
        Text("$reps", Modifier.width(32.dp).padding(start = Space.s), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold)
        Text(date, Modifier.width(60.dp).padding(start = Space.s), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}


// ---- Round 24: the PR timeline, the year's bars and ranked rows ----

/**
 * For each PR of one exercise (oldest first): how far each record kind it set rose over the previous record of that kind on the same side. [valueOf] is
 * the figure a set stands for under a kind (kg, reps or seconds); a kind with no earlier record, or no figure, has null (it was the first).
 */
fun prImprovements(rows: List<PrRow>, valueOf: (PrRow, PrKind) -> Double?): List<Map<PrKind, Double?>> {
    val last = HashMap<Pair<PrKind, Side?>, Double>()
    return rows.map { row ->
        PrKind.entries.filter { it in row.kinds }.associateWith { kind ->
            val v = valueOf(row, kind)
            val key = kind to row.side
            val before = last[key]
            if (v != null) last[key] = v
            if (v != null && before != null) v - before else null
        }
    }
}

/** A PR's gain in words ("1RM +3.4 kg", "Reps +1", "Duration +5 s"), the app's own signs. Weights are kilograms given in [unit]. */
fun gainText(kind: PrKind, delta: Double, unit: String): String {
    val sign = if (delta < 0) "−" else "+"
    val size = kotlin.math.abs(delta)
    return kind.label + " " + sign + when (kind) {
        PrKind.REPS -> shortNumber(size) + if (size == 1.0) " rep" else " reps"
        PrKind.DURATION -> shortNumber(roundHalfUp(size * 10) / 10.0) + " s"
        else -> showEstimate(size, unit) + " " + unit
    }
}

/** The thin rail of a timeline: a line down the item's whole height, and, with [dot], a PR's dot where its first line is. [top] and [bottom] say whether the line goes on above and below. */
@Composable
fun TimelineRail(dot: Boolean, top: Boolean, bottom: Boolean, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val color = prColor()
    val page = MaterialTheme.colorScheme.background
    Canvas(modifier.width(24.dp).fillMaxHeight().clearAndSetSemantics { }) {
        val x = size.width / 2
        val y = if (dot) 20.dp.toPx() else size.height / 2
        val w = 2.dp.toPx()
        if (top) drawLine(line, Offset(x, 0f), Offset(x, y), w, StrokeCap.Butt)
        if (bottom) drawLine(line, Offset(x, y), Offset(x, size.height), w, StrokeCap.Butt)
        if (dot) {
            drawCircle(color, 6.dp.toPx(), Offset(x, y))
            drawCircle(page, 2.5.dp.toPx(), Offset(x, y))
        }
    }
}

/** A month's heading on the timeline, with the rail passing through it and the month's PR count at the right. */
@Composable
fun TimelineMonth(title: String, count: String, first: Boolean) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = Space.l), verticalAlignment = Alignment.CenterVertically) {
        TimelineRail(dot = false, top = !first, bottom = true)
        Row(Modifier.weight(1f).padding(start = Space.s, top = Space.l, bottom = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Caption(title, Modifier.weight(1f))
            Text(count, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * One PR on the timeline: the set and its day, a [PrChip] for each record it broke, what each rose by ([gains]) and the rail with its dot. A tap opens the workout; a hold peeks
 * at it ([peek]).
 */
@Composable
fun TimelineRow(set: String, day: String, kinds: List<String>, gains: String, spoken: String, last: Boolean, peek: (@Composable ColumnScope.() -> Unit)?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = Space.l)) {
        TimelineRail(dot = true, top = true, bottom = !last)
        HoldPeekBox("workout peek", peek, Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = Space.s, top = Space.s, bottom = Space.m).semantics(mergeDescendants = true) { contentDescription = spoken },
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(set, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(day, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) { for (k in kinds) PrChip(k) }
                Text(gains, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The year's volume, a bar for each month (January first) with its initial under it; the best month is the full accent and the others half of it. A bar opens that
 * month's page ([onMonth] is given 1 to 12); a month with no workout is a hairline and does nothing.
 */
@Composable
fun MonthBars(volumes: List<Double>, names: List<String>, onMonth: (Int) -> Unit) {
    val top = volumes.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        for ((i, v) in volumes.withIndex()) {
            val has = v > 0
            Column(
                Modifier.weight(1f).then(if (has) Modifier.clickable { onMonth(i + 1) } else Modifier)
                    .semantics(mergeDescendants = true) { contentDescription = names[i] + if (has) ", ${shortNumber(roundHalfUp(v).toDouble())}" else ", no workout" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                Box(Modifier.height(96.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height((96.dp * (v / top).toFloat()).coerceAtLeast(2.dp)).background(if (v == top) accent else accent.copy(alpha = if (has) 0.5f else 0.2f), SmallShape))
                }
                Text(names[i].take(1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A ranked line: its place, a name with a [ThinBar] of [fraction] under it, and the figure at the right. The first place is the full accent, the rest quieter. */
@Composable
fun RankedRow(rank: Int, name: String, figure: String, fraction: Float, spoken: String, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Space.xs).semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.m),
    ) {
        Text(rank.toString(), Modifier.width(20.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = if (rank == 1) accent else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(name, fontWeight = if (rank == 1) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            ThinBar(fraction, if (rank == 1) accent else accent.copy(alpha = 0.5f), 6.dp)
        }
        Text(figure, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
