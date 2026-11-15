package clean.app

import clean.app.ui.theme.SmallShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.ImageView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import clean.app.ui.theme.cardShape
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The exercise animations. They are not part of the app: they are (c) Gym visual, and the user
 * brings them in for their own use from a zip of the dataset the instruction text comes from
 * (Settings, Data). Each is kept in the app's private folder under its id in that dataset.
 */
object Animations {
    private fun folder(context: Context) = File(context.filesDir, "animations")

    fun file(context: Context, media: String) = File(folder(context), "$media.gif")

    fun count(context: Context): Int = folder(context).list()?.size ?: 0

    fun clear(context: Context) {
        folder(context).deleteRecursively()
    }

    /**
     * Copies out of the zip at [uri] every animation an exercise in the app has steps for: entries
     * named `…/<number>-<id>.gif`, as in the dataset's `videos` folder. Returns how many were kept.
     */
    suspend fun import(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        val wanted = ExerciseInfos.media(context)
        val folder = folder(context).apply { mkdirs() }
        var kept = 0
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name.substringAfterLast('/')
                    val media = name.substringAfter('-', "").removeSuffix(".gif")
                    // Only the file's own name is used, so nothing in the zip can be written outside the folder.
                    if (!entry.isDirectory && name.endsWith(".gif") && media in wanted && media.all { it.isLetterOrDigit() || it == '_' }) {
                        File(folder, "$media.gif").outputStream().use { zip.copyTo(it) }
                        kept++
                    }
                }
            }
        }
        kept
    }
}

/** An exercise's animation, when the user has imported it, with the credit its owner asks for. Nothing otherwise. */
@Composable
fun ExerciseAnimation(media: String) {
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(null, media) {
        value = withContext(Dispatchers.IO) {
            val file = Animations.file(context, media)
            if (!file.exists()) null
            else runCatching {
                // Moving pictures need Android 9; before that the first frame is shown still.
                if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
                else BitmapDrawable(context.resources, BitmapFactory.decodeFile(file.path))
            }.getOrNull()
        }
    }
    val picture = drawable ?: return
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        AndroidView(
            factory = { ImageView(it) },
            // The pictures are drawn on white, so they sit on a white tile whatever the theme.
            modifier = Modifier.size(200.dp).clip(cardShape()).background(Color.White),
            update = { view ->
                view.setImageDrawable(picture)
                if (Build.VERSION.SDK_INT >= 28) (picture as? AnimatedImageDrawable)?.start()
            },
        )
        Text("© Gym visual · gymvisual.com", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Stills already cut from animations, by exercise name; small, so a few hundred are kept. */
private val STILLS = object : android.util.LruCache<String, ImageBitmap>(400) {}

/**
 * A small still of an exercise's animation (its first frame), for the exercise list. The space is
 * kept empty for an exercise that has none, so the names stay in line.
 */
@Composable
fun ExerciseThumb(name: String) {
    val context = LocalContext.current
    val still by produceState(STILLS.get(name), name) {
        if (value == null) value = withContext(Dispatchers.IO) {
            val file = ExerciseInfos.of(context, name)?.let { Animations.file(context, it.media) }?.takeIf { it.exists() } ?: return@withContext null
            // Half size is plenty for a thumbnail, and two bytes a dot keeps a long list light.
            val options = BitmapFactory.Options().apply { inSampleSize = 2; inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 }
            runCatching { BitmapFactory.decodeFile(file.path, options)?.asImageBitmap() }.getOrNull()?.also { STILLS.put(name, it) }
        }
    }
    Box(Modifier.size(40.dp).clip(SmallShape).background(if (still != null) Color.White else Color.Transparent)) {
        still?.let { Image(it, null, Modifier.size(40.dp)) }
    }
}
