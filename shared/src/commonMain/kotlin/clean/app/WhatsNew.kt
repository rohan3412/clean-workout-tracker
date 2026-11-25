package clean.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import clean.app.platform.Platform
import clean.app.ui.theme.cardShape
import clean.core.Change
import clean.core.Release
import clean.core.parseChanges

/** The app's list of changes, `assets/changes.md`, newest version first. */
fun readChanges(platform: Platform): List<Release> =
    runCatching { parseChanges(platform.readBundledText("changes.md") ?: error("no changes file")) }.getOrDefault(emptyList())

/** How many of a version's changes the What's new page gives a place of their own: the first ones in `changes.md`; the rest are under "See everything". */
const val WHATS_NEW_HIGHLIGHTS = 6

/** The first changes of a release, the ones the What's new page shows. */
fun highlightsOf(release: Release): List<Change> = release.changes.take(WHATS_NEW_HIGHLIGHTS)

/** The picture beside a highlight, chosen by the first word of its headline (`Look`, `Progress`, ...); a star when it is none of these. */
fun highlightIcon(title: String): ImageVector = when (title.substringBefore(' ').trimEnd(':', ',').lowercase()) {
    "look" -> CleanIcons.Eye
    "cards" -> CleanIcons.Forward
    "hold" -> CleanIcons.Pin
    "logging" -> CleanIcons.DumbbellLine
    "progress" -> CleanIcons.TabProgress
    "performance" -> CleanIcons.Bolt
    "history", "records" -> CleanIcons.History
    "body" -> CleanIcons.Body
    "backup", "data" -> CleanIcons.Folder
    "timer", "rest" -> CleanIcons.TimerLine
    else -> CleanIcons.Star
}

/**
 * What's new, once after an update (`AppModel.whatsNew`) and from Settings, About: a page over the app with the version as its title,
 * the first few changes as highlights (a picture, a bold headline and one line each) on the glass material, "See everything" for the
 * whole list of every version, and one Continue. Back closes it too. The page blinks in as every window does; moving between the
 * highlights and everything is the blink.
 */
@Composable
fun WhatsNewPage(releases: List<Release>, release: Release, backdrop: Backdrop, onClose: () -> Unit) {
    var everything by remember { mutableStateOf(false) }
    val motion = LocalMotion.current
    Dialog(onDismissRequest = onClose, properties = fullScreenDialogProperties()) {
        BlinkWindow()
        // The page carries the background, as every page does (Round 36); a clear surface over it, not the flat ground.
        Surface(Modifier.fillMaxSize().drawBackdrop(backdrop).testTag("whatsnew page"), color = androidx.compose.ui.graphics.Color.Transparent) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.l, vertical = Space.xl),
                    verticalArrangement = Arrangement.spacedBy(Space.l),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                        Text("What's new", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Clean ${release.version}", Modifier.semantics { heading() }, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    }
                    AnimatedContent(everything, transitionSpec = { Motion.blinkIn(motion) togetherWith Motion.blinkOut(motion) }, label = "whats new") { all ->
                        Column(verticalArrangement = Arrangement.spacedBy(Space.l)) {
                            Column(Modifier.fillMaxWidth().glass(cardShape(), haze = null, lift = 8.dp).padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.l)) {
                                if (!all) for (change in highlightsOf(release)) Highlight(change)
                                else ChangeList(releases.ifEmpty { listOf(release) })
                            }
                            if (!all) QuietButton("See everything", { everything = true }, Modifier.fillMaxWidth())
                            else QuietButton("Back to the highlights", { everything = false }, Modifier.fillMaxWidth())
                        }
                    }
                }
                MainButton("Continue", onClose, Modifier.fillMaxWidth().padding(horizontal = Space.l, vertical = Space.m))
            }
        }
    }
}

/** One highlight: a picture in a disc of the accent's tint, the headline in bold and one line under it. */
@Composable
private fun Highlight(change: Change) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(highlightIcon(change.title), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(change.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (change.text.isNotEmpty()) Text(change.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Every version's changes, newest first, for the What's new page and Settings, About. */
@Composable
fun ChangeList(releases: List<Release>) {
    for (r in releases) {
        Text(r.version + (r.date?.let { "  ·  $it" } ?: ""), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        for (c in r.changes) Text("• " + c.title + if (c.text.isNotEmpty()) ": ${c.text}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
