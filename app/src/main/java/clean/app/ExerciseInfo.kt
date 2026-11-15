package clean.app

import android.content.Context
import clean.core.canonicalName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** How to do an exercise: the steps in order, and the id of its animation in the dataset the text comes from. */
class ExerciseInfo(val steps: List<String>, val media: String)

/**
 * The how-to texts of the built-in exercises, from `assets/exercise_info.json` (made by
 * `tools/exercise_info`, credited in NOTICE.md). Read once, off the main thread, and kept.
 */
object ExerciseInfos {
    private val lock = Mutex()
    @Volatile private var all: Map<String, ExerciseInfo>? = null

    /**
     * The text for [name]: a library exercise's exact name, or another name for one. Null when the
     * exercise is the user's own or the dataset has nothing that is surely the same exercise; a
     * variation is not given a sibling's text.
     */
    suspend fun of(context: Context, name: String): ExerciseInfo? {
        val map = all ?: lock.withLock { all ?: load(context.applicationContext).also { all = it } }
        return map[name] ?: canonicalName(name)?.let { map[it] }
    }

    /** The animation ids of every exercise that has steps. */
    suspend fun media(context: Context): Set<String> {
        val map = all ?: lock.withLock { all ?: load(context.applicationContext).also { all = it } }
        return map.values.mapTo(HashSet()) { it.media }
    }

    private suspend fun load(context: Context): Map<String, ExerciseInfo> = withContext(Dispatchers.IO) {
        val root = JSONObject(context.assets.open("exercise_info.json").bufferedReader().use { it.readText() })
        val map = HashMap<String, ExerciseInfo>(root.length() * 2)
        for (key in root.keys()) {
            val entry = root.getJSONObject(key)
            val steps = entry.getJSONArray("s")
            map[key] = ExerciseInfo(List(steps.length()) { steps.getString(it) }, entry.getString("m"))
        }
        map
    }
}
