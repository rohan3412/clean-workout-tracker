package clean.app.data

import org.json.JSONArray
import org.json.JSONObject

/** A template's blocks before and after a rename, as the stored JSON text. */
class TemplateChange(val id: Long, val before: String, val after: String)

/**
 * One rename or merge of an exercise, with everything needed to take it back exactly (`Repo.undoRename`).
 * The rows changed all carried [from] exactly, so only their ids are kept.
 */
class RenameEntry(
    /** When it was done, as a stored date (`yyyy-MM-dd HH:mm:ss`). */
    val at: String,
    val from: String,
    val to: String,
    val merged: Boolean,
    val rows: List<Long>,
    /** How many workouts had the exercise. */
    val workouts: Int,
    /** The settings row [from] had, as JSON; null when it had none. */
    val settingFrom: String?,
    /** The settings row [to] had before, as JSON; null when it had none. Put back only when [wroteTo]. */
    val settingTo: String?,
    /** Whether a settings row was written for [to] (moved from [from], or made from its automatic values). */
    val wroteTo: Boolean,
    /** Other exercises whose overall exercise was [from] and became [to]. */
    val regrouped: List<String>,
    /** The progression scheme [from] had (kv `prog:<profile>:<from>`), and whether it was moved to [to]. */
    val progFrom: String?,
    val progMoved: Boolean,
    val templates: List<TemplateChange>,
    /** The weekly program before and after, when it named [from]. */
    val programBefore: String?,
    val programAfter: String?,
    /** Whether the workout in progress had [from] in it. */
    val draft: Boolean,
    /** The left and right flag [from] had (kv `sides:<profile>:<from>`), and whether it was moved to [to]. Entries from before it have none. */
    val sidesFrom: String? = null,
    val sidesMoved: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("at", at); put("from", from); put("to", to); put("merged", merged)
        put("rows", rows.joinToString(","))
        put("workouts", workouts)
        settingFrom?.let { put("settingFrom", it) }
        settingTo?.let { put("settingTo", it) }
        put("wroteTo", wroteTo)
        put("regrouped", JSONArray(regrouped))
        progFrom?.let { put("progFrom", it) }
        put("progMoved", progMoved)
        put("templates", JSONArray(templates.map { JSONObject().put("id", it.id).put("before", it.before).put("after", it.after) }))
        programBefore?.let { put("programBefore", it) }
        programAfter?.let { put("programAfter", it) }
        put("draft", draft)
        sidesFrom?.let { put("sidesFrom", it) }
        put("sidesMoved", sidesMoved)
    }

    /** The same entry told apart from others in the log. */
    fun sameAs(other: RenameEntry) = at == other.at && from == other.from && to == other.to

    companion object {
        /** At most this many are kept. */
        const val KEEP = 20

        fun fromJson(o: JSONObject) = RenameEntry(
            o.getString("at"), o.getString("from"), o.getString("to"), o.getBoolean("merged"),
            o.getString("rows").split(',').mapNotNull { it.toLongOrNull() },
            o.getInt("workouts"),
            if (o.has("settingFrom")) o.getString("settingFrom") else null,
            if (o.has("settingTo")) o.getString("settingTo") else null,
            o.getBoolean("wroteTo"),
            o.getJSONArray("regrouped").let { a -> (0 until a.length()).map { a.getString(it) } },
            if (o.has("progFrom")) o.getString("progFrom") else null,
            o.getBoolean("progMoved"),
            o.getJSONArray("templates").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { t -> TemplateChange(t.getLong("id"), t.getString("before"), t.getString("after")) } } },
            if (o.has("programBefore")) o.getString("programBefore") else null,
            if (o.has("programAfter")) o.getString("programAfter") else null,
            o.getBoolean("draft"),
            if (o.has("sidesFrom")) o.getString("sidesFrom") else null,
            o.optBoolean("sidesMoved"),
        )

        /** The log as stored (kv `draft:renames:<profile>`, newest first); anything unreadable is dropped. */
        fun listFrom(text: String?): List<RenameEntry> = runCatching {
            val a = JSONArray(text ?: return emptyList())
            (0 until a.length()).mapNotNull { runCatching { fromJson(a.getJSONObject(it)) }.getOrNull() }
        }.getOrDefault(emptyList())

        fun toText(entries: List<RenameEntry>): String = JSONArray(entries.map { it.toJson() }).toString()
    }
}

fun ExerciseSetting.toJson(): String = JSONObject()
    .put("profileId", profileId).put("name", name).put("loadType", loadType).put("muscles", muscles).put("note", note)
    .put("overall", overall).put("noteColor", noteColor).put("favourite", favourite).put("rest", rest).put("goal", goal)
    .also { o -> unit?.let { o.put("unit", it) } }
    .toString()

fun settingFromJson(text: String): ExerciseSetting = JSONObject(text).let { o ->
    ExerciseSetting(
        o.getLong("profileId"), o.getString("name"), o.getString("loadType"), if (o.has("unit")) o.getString("unit") else null,
        o.getString("muscles"), o.getString("note"), o.getString("overall"), o.getString("noteColor"),
        o.getBoolean("favourite"), o.getInt("rest"), o.getString("goal"),
    )
}
