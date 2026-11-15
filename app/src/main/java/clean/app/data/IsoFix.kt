package clean.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * What the Iso-Lateral Row fix needs to take itself back: every row of the workouts it rewrote, exactly as stored (ids
 * included, so the order is the same) (kv `draft:isofix:<profile>`, a `draft:` key so the options flow never carries it).
 */
class IsoFixSaved(val rows: List<RowEntity>) {
    fun toText(): String = JSONObject().put("rows", JSONArray(rows.map { r ->
        JSONObject().put("id", r.id).put("profileId", r.profileId).put("workoutNumber", r.workoutNumber).put("date", r.date)
            .put("workoutName", r.workoutName).put("durationSeconds", r.durationSeconds).put("exerciseName", r.exerciseName)
            .put("setOrder", r.setOrder).put("weightKg", r.weightKg).put("reps", r.reps).put("rpe", r.rpe)
            .put("distanceMeters", r.distanceMeters).put("seconds", r.seconds).put("notes", r.notes).put("workoutNotes", r.workoutNotes)
    })).toString()

    companion object {
        fun fromText(text: String?): IsoFixSaved? = runCatching {
            val o = JSONObject(text ?: return null)
            val a = o.getJSONArray("rows")
            IsoFixSaved((0 until a.length()).map { i ->
                a.getJSONObject(i).let { r ->
                    RowEntity(
                        r.getLong("id"), r.getLong("profileId"), r.getString("workoutNumber"), r.getString("date"), r.getString("workoutName"),
                        r.getString("durationSeconds"), r.getString("exerciseName"), r.getString("setOrder"), r.getString("weightKg"), r.getString("reps"),
                        r.getString("rpe"), r.getString("distanceMeters"), r.getString("seconds"), r.getString("notes"), r.getString("workoutNotes"),
                    )
                }
            })
        }.getOrNull()
    }
}
