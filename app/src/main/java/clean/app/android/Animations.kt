package clean.app.android

import android.content.Context
import android.net.Uri
import clean.app.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

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
    suspend fun import(context: Context, uri: Uri, wanted: Set<String>): Int = withContext(Dispatchers.IO) {
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

