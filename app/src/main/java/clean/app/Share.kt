package clean.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Density
import clean.app.ui.theme.LocalLook
import clean.core.exportFit
import clean.core.exportText
import clean.core.muscleSets
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import clean.app.ui.theme.cardShape
import clean.core.Workout
import clean.core.bestRound
import clean.core.recordCount
import clean.core.roundLine
import clean.core.shortNumber
import clean.core.volumeOf
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.launch

/**
 * Hands a file to Android's share sheet, for the user to send with whatever app they pick. The
 * file is written to the app's cache and lent out by address; Clean itself sends nothing anywhere.
 */
fun shareFile(context: Context, name: String, bytes: ByteArray, type: String) {
    val folder = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(folder, name.replace(Regex("[^A-Za-z0-9._-]+"), "_")).apply { writeBytes(bytes) }
    val address = FileProvider.getUriForFile(context, "clean.app.files", file)
    val send = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, address).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}

/** Hands plain text to the share sheet. */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

/** The picture is 1080 x 1350 px whatever the phone: the card is laid out as 360 x 450 dp at a density of 3. */
private const val CARD_W = 1080
private const val CARD_H = 1350
private const val EXERCISE_LINES = 8

/**
 * A finished workout as a card to send as a picture, in the current look: the page colour with a card on it,
 * the look's outline, accent, scale and number font. Name, when, the four totals, the note, the exercises and
 * the body figure shaded by this workout's sets. The only brand on it is the word Clean.
 */
@Composable
private fun ShareCard(model: AppModel, workout: Workout, prs: Double) {
    val volume = remember(workout) { volumeOf(workout, model::loadOf) }
    val muscles = remember(workout) { muscleSets(listOf(workout), null) { model.musclesOf(it) } }
    val palette = LocalLook.current.scale
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val shown = workout.blocks.filter { it.workingCount > 0.0 }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Space.m)) {
        Column(
            Modifier.fillMaxSize().background(cardFill(), cardShape()).padding(Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.s),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text(workout.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (prs > 0) Badge(prsText(prs))
                }
                val time = parseDate(workout.date)?.format(DateTimeFormatter.ofPattern("HH:mm"))
                Text(listOfNotNull(prettyDay(workout.date), time).joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = muted)
            }
            Row(Modifier.fillMaxWidth()) {
                for ((label, value) in listOf("Time" to duration(workout.durationSeconds), "Volume" to model.volume(volume), "Sets" to shortNumber(workout.workingCount), "PRs" to shortNumber(prs))) {
                    Column(Modifier.weight(1f)) {
                        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        Text(label, style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                }
            }
            if (workout.notes.isNotEmpty()) Text(workout.notes, style = MaterialTheme.typography.bodySmall, color = muted, fontStyle = FontStyle.Italic, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.weight(1f).fillMaxWidth().clipToBounds(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    for (block in shown.take(EXERCISE_LINES)) {
                        // A round of a left and right exercise is one set, and its best is the best round.
                        val unit = model.unitOf(block.exercise)
                        val best = if (block.paired) bestRound(block.rounds)?.let { roundLine(it) { set -> setValues(set, unit) } }
                        else block.bestSet?.let { setValues(it, unit) }
                        Column {
                            Text("${shortNumber(block.workingCount)} × ${block.exercise}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(best.orEmpty(), style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (shown.size > EXERCISE_LINES) Text("+${shown.size - EXERCISE_LINES} more", style = MaterialTheme.typography.labelSmall, color = muted)
                }
                if (muscles.isNotEmpty()) Box(Modifier.weight(1.2f).fillMaxSize(), contentAlignment = Alignment.Center) {
                    val most = muscles.values.max()
                    BodyFigure(muscles.mapValues { (_, v) -> shade(palette, v / most) }, interactive = false, description = "Muscles worked")
                }
            }
            Text("Clean", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
    }
}

/** The finished workout's share sheet: the picture as it will be sent, and the three ways to send it. */
@Composable
fun ShareWorkoutDialog(model: AppModel, workout: Workout, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allPrs by model.prs.collectAsState()
    // What is drawn here is also kept as a picture, which is what gets sent.
    val picture = rememberGraphicsLayer()
    val prs = recordCount(workout, allPrs[workout.date].orEmpty())
    Sheet(onDismiss, "Share workout") {
        Column(Modifier.padding(horizontal = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BoxWithConstraints(Modifier.widthIn(max = 300.dp)) {
                    val density = LocalDensity.current
                    val scale = constraints.maxWidth / CARD_W.toFloat()
                    Box(
                        Modifier.fillMaxWidth().height(with(density) { (constraints.maxWidth * CARD_H / CARD_W.toFloat()).toDp() }).clipToBounds()
                            .semantics { contentDescription = "Preview of the picture: ${workout.name}, ${prettyDay(workout.date)}" },
                        contentAlignment = Alignment.TopStart,
                    ) {
                        Box(
                            Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).size(with(density) { CARD_W.toDp() }, with(density) { CARD_H.toDp() })
                                .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }
                                .drawWithContent {
                                    picture.record { this@drawWithContent.drawContent() }
                                    drawLayer(picture)
                                },
                        ) { CompositionLocalProvider(LocalDensity provides Density(CARD_W / 360f, 1f)) { ShareCard(model, workout, prs) } }
                    }
                }
            }
            MainButton("Share picture", {
                scope.launch {
                    val png = withContext(Dispatchers.Default) {
                        ByteArrayOutputStream().also { picture.toImageBitmap().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                    }
                    shareFile(context, "${workout.name} ${workout.day}.png", png, "image/png")
                    onDismiss()
                }
            }, Modifier.fillMaxWidth())
            QuietButton("Share as text", {
                shareText(context, exportText(workout, model.profile.value?.unit == "lb"))
                onDismiss()
            }, Modifier.fillMaxWidth())
            QuietButton("Export FIT file", {
                // Strava's app cannot import files; its upload page, opened in a browser, takes this one.
                model.message = "Send the .fit file to a browser; Strava takes it at strava.com/upload/select"
                shareFile(context, "${workout.name} ${workout.day}.fit", exportFit(workout, ZoneId.systemDefault()), "application/octet-stream")
                onDismiss()
            }, Modifier.fillMaxWidth())
        }
    }
}
