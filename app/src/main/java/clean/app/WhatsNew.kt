package clean.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import clean.app.ui.theme.cardShape
import clean.core.Release
import clean.core.parseChanges

/** The app's list of changes, `assets/changes.md`, newest version first. */
fun readChanges(context: Context): List<Release> =
    runCatching { context.assets.open("changes.md").bufferedReader().use { parseChanges(it.readText()) } }.getOrDefault(emptyList())

/**
 * What's new, once after an update (`AppModel.whatsNew`): a card in the middle with the app dimmed behind it, one change per page
 * to swipe through, dots for where you are, and a close button (the user's design, 2026-11-16). Back or a tap outside closes it too.
 */
@Composable
fun WhatsNewPopup(release: Release, onClose: () -> Unit) {
    val pager = rememberPagerState { release.changes.size }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BlinkWindow()
        Surface(Modifier.fillMaxWidth().padding(horizontal = Space.l), shape = cardShape(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("What's new in ${release.version}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onClose) { Icon(CleanIcons.Close, "Close what's new") }
                }
                HorizontalPager(pager, Modifier.fillMaxWidth().heightIn(min = 120.dp), pageSpacing = Space.l, verticalAlignment = Alignment.Top) { i ->
                    val change = release.changes[i]
                    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                        Text(change.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        if (change.text.isNotEmpty()) Text(change.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (release.changes.size > 1) Row(
                    Modifier.fillMaxWidth().semantics { contentDescription = "Change ${pager.currentPage + 1} of ${release.changes.size}" },
                    horizontalArrangement = Arrangement.spacedBy(Space.s, Alignment.CenterHorizontally),
                ) {
                    repeat(release.changes.size) { i ->
                        val on = i == pager.currentPage
                        Box(Modifier.size(if (on) 8.dp else 6.dp).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                    }
                }
            }
        }
    }
}

/** Every version's changes, newest first, for Settings, Credits. */
@Composable
fun ChangeList(releases: List<Release>) {
    for (r in releases) {
        Text(r.version + (r.date?.let { "  ·  $it" } ?: ""), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        for (c in r.changes) Text("• " + c.title + if (c.text.isNotEmpty()) ": ${c.text}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
