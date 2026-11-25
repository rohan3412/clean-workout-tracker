package clean.app

import clean.app.platform.LocalPlatform
import clean.app.ui.theme.SmallShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import clean.app.ui.theme.cardShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An exercise's animation, when the user has imported it, with the credit its owner asks for. Nothing otherwise. */
@Composable
fun ExerciseAnimation(media: String) {
    val picture = LocalPlatform.current.rememberAnimationPicture(media) ?: return
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        // The pictures are drawn on white, so they sit on a white tile whatever the theme.
        picture(Modifier.size(200.dp).clip(cardShape()).background(Color.White))
        Text("© Gym visual · gymvisual.com", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Stills already cut from animations, by exercise name; small, so a few hundred are kept. */
private val STILLS = HashMap<String, ImageBitmap>()

/**
 * A small still of an exercise's animation (its first frame), for the exercise list. The space is
 * kept empty for an exercise that has none, so the names stay in line.
 */
@Composable
fun ExerciseThumb(name: String) {
    val platform = LocalPlatform.current
    val still by produceState(STILLS[name], name) {
        if (value == null) value = withContext(ioDispatcher) {
            val media = ExerciseInfos.of(platform, name)?.media ?: return@withContext null
            platform.animationStill(media)?.also { STILLS[name] = it }
        }
    }
    Box(Modifier.size(40.dp).clip(SmallShape).background(if (still != null) Color.White else Color.Transparent)) {
        still?.let { Image(it, null, Modifier.size(40.dp)) }
    }
}
