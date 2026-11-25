package clean.app.data

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import clean.core.ForeignWeight
import clean.core.ImportFile
import clean.core.ImportSource
import clean.core.MeasurementEntry
import clean.core.Muscle
import clean.core.MeasurementFile
import clean.core.Measurements
import clean.core.NewBlock
import clean.core.STANDARD_MEASUREMENTS
import clean.core.WorkoutCsv
import clean.core.parseForeign
import clean.core.toRows
import clean.core.IdRow
import clean.core.IsoRowPlan
import clean.core.WorkoutRow
import clean.core.renameInProgram
import clean.core.renameRows
import clean.core.restoreRows
import clean.core.withSidesMarker
import clean.core.rowsFor

/** What an import would add, shown to the user before anything is written. */
class WorkoutImport(
    val rows: List<WorkoutRow>, val workouts: Int, val alreadyThere: Int, val from: String?, val to: String?,
    /** What kind of file it was, how many of its exercises this profile has not logged, what was left out and why, and the weigh-ins to add. */
    val label: String = "", val newExercises: Int = 0, val skipped: Map<String, Int> = emptyMap(), val weights: List<ForeignWeight> = emptyList(),
)
class MeasurementImport(val files: List<MeasurementFile>, val newEntries: List<MeasurementEntity>, val alreadyThere: Int)

class Repo(private val db: CleanDb) {
    val dao = db.dao()

    suspend fun createProfile(name: String): Long = db.withTransaction {
        val id = dao.insert(Profile(name = name))
        dao.insertFiles(STANDARD_MEASUREMENTS.map { (file, header) -> MeasurementFileEntity(profileId = id, name = file, header = header) })
        id
    }

    suspend fun deleteProfile(id: Long) = db.withTransaction {
        dao.deleteRows(id)
        dao.deleteMeasurements(id)
        dao.deleteFiles(id)
        dao.deleteSettings(id)
        dao.deleteTemplates(id)
        dao.remove("draft:$id")
        dao.remove("draft:rest:$id")
        dao.remove(correctionKey(id))
        dao.remove(correctionFromKey(id))
        dao.remove(renameLogKey(id))
        dao.remove(isoFixKey(id))
        dao.remove("isofix.done:$id")
        dao.remove("isofix.hidden:$id")
        for (base in listOf("goal.week", "program", "reward.seen", "target.sets", "deload.snooze", "home.cards", "profile.cards", "home.lift", "goal.weight", "welcome", "tpl.colors", "tpl.colors.scope") + Muscle.entries.map { "target.${it.name}" }) dao.remove("$base:$id")
        dao.removePrefix("prog:$id:")
        dao.removePrefix("sides:$id:")
        dao.removePrefix("tag:$id:")
        dao.removePrefix("tag.out:$id:")
        dao.removePrefix("tpl.color:$id:")
        dao.deleteProfileRow(id)
    }

    // --- Workouts ---

    /** A workout is the same workout if it starts at the same second, so importing a file twice adds nothing. */
    suspend fun previewWorkouts(profileId: Long, bytes: ByteArray): WorkoutImport = previewRows(profileId, WorkoutCsv.parse(bytes), ImportSource.STRONG.label)

    /** A Strong file goes the exact way above; any other recognised file is read by `parseForeign` into the same kind of rows. */
    suspend fun previewImport(profileId: Long, file: ImportFile, lbs: Boolean): WorkoutImport = withContext(Dispatchers.Default) {
        if (file.source == ImportSource.STRONG) previewWorkouts(profileId, file.bytes)
        else parseForeign(file.text, lbs).let { previewRows(profileId, toRows(it), file.source.label, it.skipped, it.weights) }
    }

    private suspend fun previewRows(profileId: Long, rows: List<WorkoutRow>, label: String, skipped: Map<String, Int> = emptyMap(), weights: List<ForeignWeight> = emptyList()): WorkoutImport {
        val stored = dao.rowsNow(profileId)
        val have = stored.mapTo(HashSet()) { it.date }
        val fresh = rows.filter { it.date !in have }
        val dates = fresh.map { it.date }.distinct()
        val known = stored.mapTo(HashSet()) { it.exerciseName }
        // A weigh-in is skipped when its day has one already.
        val weighed = dao.measurementsNow(profileId).filter { it.file == "weight.csv" }.mapTo(HashSet()) { it.date.take(10) }
        return WorkoutImport(
            fresh, dates.size, rows.map { it.date }.distinct().size - dates.size, dates.minOrNull(), dates.maxOrNull(),
            label, fresh.map { it.exerciseName }.filter { it !in known }.distinct().size, skipped, weights.filter { it.date.take(10) !in weighed }.distinctBy { it.date.take(10) },
        )
    }

    suspend fun commit(profileId: Long, import: WorkoutImport) = db.withTransaction {
        dao.insertRows(import.rows.map { RowEntity.of(profileId, it) })
        renumber(profileId)
        dao.insertMeasurements(import.weights.map { MeasurementEntity(profileId = profileId, file = "weight.csv", date = it.date, value = Measurements.newText(it.kg)) })
    }

    /** Every stored row in order, for the exports that are not Strong's. */
    suspend fun rows(profileId: Long): List<WorkoutRow> = dao.rowsNow(profileId).map { it.toRow() }

    suspend fun exportWorkouts(profileId: Long): ByteArray {
        val rows = dao.rowsNow(profileId)
        return withContext(Dispatchers.Default) { WorkoutCsv.serialise(rows.map { it.toRow() }) }
    }

    /**
     * Saves a finished workout and removes the stored draft in one transaction, so a kill between the two
     * cannot leave a draft behind that would be finished (and saved) a second time. Rows identical to ones
     * already stored for [date] are not added again.
     */
    suspend fun saveWorkout(profileId: Long, date: String, name: String, durationSeconds: Long, blocks: List<NewBlock>, workoutNotes: String = "") = db.withTransaction {
        val rows = rowsFor(0, date, name, durationSeconds, blocks, workoutNotes).map { RowEntity.of(profileId, it) }
        fun RowEntity.content() = copy(id = 0, workoutNumber = "")
        val there = dao.rowsNow(profileId).filter { it.date == date }.map { it.content() }
        if (there != rows.map { it.content() }) {
            dao.insertRows(rows)
            renumber(profileId)
        }
        dao.remove("draft:$profileId")
    }

    /**
     * Moves the blocks of [exercise] that open with the note [note] (compared without case or
     * surrounding spaces) to the exercise [to], by rewriting the exercise name on their rows. The
     * note itself stays where it is. Returns how many blocks moved.
     */
    suspend fun moveNoted(profileId: Long, exercise: String, note: String, to: String): Int = db.withTransaction {
        val changed = mutableListOf<RowEntity>()
        var moved = 0
        // A block is a run of rows of one exercise in one workout; a note after a set starts a new one.
        val block = mutableListOf<RowEntity>()
        fun close() {
            val opening = block.takeWhile { it.setOrder == "Note" }
            if (block.isNotEmpty() && block.first().exerciseName == exercise && opening.any { it.notes.trim().lowercase() == note }) {
                changed += block.map { it.copy(exerciseName = to) }
                moved++
            }
            block.clear()
        }
        for (row in dao.rowsNow(profileId)) {
            val last = block.lastOrNull()
            if (last != null && (last.date != row.date || last.exerciseName != row.exerciseName || (row.setOrder == "Note" && last.setOrder != "Note"))) close()
            block += row
        }
        close()
        changed.chunked(500).forEach { dao.updateRows(it) }
        moved
    }

    /**
     * Marks the [nth] block of [exercise] in the workout starting at [date] as left and right ([paired]), or takes the mark
     * off (docs/left-right.md): the marker row is the only row added or removed. The workout's rows are written again in the
     * same order (rows sort by date, then id, and a workout is the only one at its date), in one transaction. False, and
     * nothing done, when there is no such block or it is already so.
     */
    suspend fun markSides(profileId: Long, date: String, exercise: String, nth: Int, paired: Boolean): Boolean = db.withTransaction {
        val rows = withSidesMarker(dao.rowsOn(profileId, date).map { it.toRow() }, exercise, nth, paired) ?: return@withTransaction false
        dao.deleteWorkout(profileId, date)
        dao.insertRows(rows.map { RowEntity.of(profileId, it) })
        true
    }

    /**
     * Saves a correction of a finished workout (the Correct page): the workout that started at [oldDate] is replaced by these rows in
     * one transaction, and the correction kept for the page is dropped. The workout in progress, if any, is not touched.
     */
    suspend fun correctWorkout(profileId: Long, oldDate: String, date: String, name: String, durationSeconds: Long, blocks: List<NewBlock>, workoutNotes: String) = db.withTransaction {
        dao.deleteWorkout(profileId, oldDate)
        dao.insertRows(rowsFor(0, date, name, durationSeconds, blocks, workoutNotes).map { RowEntity.of(profileId, it) })
        renumber(profileId)
        dao.remove(correctionKey(profileId))
        dao.remove(correctionFromKey(profileId))
    }

    /** Where a correction in progress is kept between starts of the app, and the workout as it was when it was opened. */
    fun correctionKey(profileId: Long) = "draft:correct:$profileId"
    fun correctionFromKey(profileId: Long) = "draft:correct0:$profileId"

    /**
     * Sets the Workout Notes field of every row of the workout starting at [date] to [note] and nothing else; returns the note it had
     * (the first non-empty one), or null when there is no such workout.
     */
    suspend fun setWorkoutNote(profileId: Long, date: String, note: String): String? = db.withTransaction {
        val rows = dao.rowsOn(profileId, date)
        if (rows.isEmpty()) return@withTransaction null
        dao.updateRows(rows.map { it.copy(workoutNotes = note) })
        rows.firstOrNull { it.workoutNotes.isNotEmpty() }?.workoutNotes ?: ""
    }

    /** Returns the rows removed, exactly as stored, so [restoreWorkout] can put them back. */
    suspend fun deleteWorkout(profileId: Long, date: String): List<RowEntity> = db.withTransaction {
        val removed = dao.rowsOn(profileId, date)
        dao.deleteWorkout(profileId, date)
        renumber(profileId)
        removed
    }

    /**
     * Puts back rows [deleteWorkout] returned: the same text and the same ids, so the same order, and the workouts are
     * numbered again as for any save. False, and nothing done, if a workout now starts at that time.
     */
    suspend fun restoreWorkout(profileId: Long, rows: List<RowEntity>): Boolean = db.withTransaction {
        if (rows.isEmpty() || dao.rowsOn(profileId, rows.first().date).isNotEmpty()) return@withTransaction false
        dao.insertRows(rows)
        renumber(profileId)
        true
    }

    // --- Iso-Lateral Row history fix ---

    private fun isoFixKey(profileId: Long) = "draft:isofix:$profileId"

    /** What the fix wrote over, if it can still be undone. */
    suspend fun isoFixSaved(profileId: Long): IsoFixSaved? = IsoFixSaved.fromText(dao.get(isoFixKey(profileId)))

    /**
     * Writes [plan] in one transaction: every workout it names has its rows deleted and the planned rows inserted in order, and
     * the workouts are numbered as for any save. The rows as they were are kept under kv `draft:isofix:<profile>` for [undoIsoRowFix],
     * and kv `isofix.done:<profile>` marks the one-time fix as done.
     */
    suspend fun applyIsoRowFix(profileId: Long, plan: IsoRowPlan): IsoFixSaved = db.withTransaction {
        val originals = ArrayList<RowEntity>()
        for (fix in plan.workouts) {
            originals += dao.rowsOn(profileId, fix.date)
            dao.deleteWorkout(profileId, fix.date)
            dao.insertRows(fix.rows.map { r ->
                RowEntity(
                    profileId = profileId, workoutNumber = r.workoutNumber, date = r.date, workoutName = r.workoutName, durationSeconds = r.durationSeconds,
                    exerciseName = r.exerciseName, setOrder = r.setOrder, weightKg = r.weightKg, reps = r.reps, rpe = r.rpe,
                    distanceMeters = r.distanceMeters, seconds = r.seconds, notes = r.notes, workoutNotes = r.workoutNotes,
                )
            })
        }
        renumber(profileId)
        dao.put(Kv("isofix.done:$profileId", "1"))
        IsoFixSaved(originals).also { dao.put(Kv(isoFixKey(profileId), it.toText())) }
    }

    /** Puts [saved] back: the rows of those workouts as they were stored, and the done mark goes with the saved copy. */
    suspend fun undoIsoRowFix(profileId: Long, saved: IsoFixSaved) = db.withTransaction {
        for (date in saved.rows.map { it.date }.distinct()) dao.deleteWorkout(profileId, date)
        dao.insertRows(saved.rows)
        renumber(profileId)
        dao.remove("isofix.done:$profileId")
        dao.remove(isoFixKey(profileId))
    }

    // --- Renaming and merging an exercise ---

    /**
     * Gives the exercise [from] the name [to], at the user's request, in one transaction: the exercise-name text of every row
     * named exactly [from] (no trimming, no case folding), and everything else keyed by that name: its settings row (and
     * any exercise whose overall exercise it is), the progression scheme, the templates' blocks and the weekly program.
     * The workout in progress is the caller's. [merged] says [to] is an exercise already: its settings and progression stay,
     * and [from]'s are kept only in the log. [automatic] is the settings row to make for a plain rename of an exercise that had
     * none, so that it is logged and trains as before. The entry in the log (`draft:renames:<profile>`, newest first, 20 kept)
     * is what [undoRename] needs; nothing in the workout numbers or any stored number changes.
     */
    suspend fun renameExercise(profileId: Long, from: String, to: String, merged: Boolean, automatic: ExerciseSetting?, draft: Boolean, at: String): RenameEntry = db.withTransaction {
        // The rows.
        val stored = dao.rowsNow(profileId)
        val (renamed, ids) = renameRows(stored.map { IdRow(it.id, it.toRow()) }, from, to)
        val moved = ids.toHashSet()
        renamed.filter { it.id in moved }.map { RowEntity.of(profileId, it.row).copy(id = it.id) }.chunked(500).forEach { dao.updateRows(it) }
        val workouts = stored.filter { it.id in moved }.mapTo(HashSet()) { it.date }.size

        // The settings row, and the exercises grouped under [from].
        val all = dao.settingsNow(profileId).associateBy { it.name }
        val source = all[from]
        val target = all[to]
        val adopted = if (target != null) null else source ?: automatic
        if (adopted != null) dao.put(adopted.copy(profileId = profileId, name = to, overall = if (adopted.overall == from) to else adopted.overall))
        if (source != null) dao.deleteSetting(profileId, from)
        val regrouped = all.values.filter { it.name != from && it.overall == from }
        regrouped.forEach { dao.put(it.copy(overall = to)) }

        // The progression scheme: [to] keeps its own when it has one.
        val fromKey = "prog:$profileId:$from"
        val toKey = "prog:$profileId:$to"
        val progFrom = dao.get(fromKey)
        var progMoved = false
        if (progFrom != null) {
            if (dao.get(toKey) == null) {
                dao.put(Kv(toKey, progFrom))
                progMoved = true
            }
            dao.remove(fromKey)
        }

        // Left and right: the flag follows the name, [to] keeping its own when it has one.
        val sidesFromKey = "sides:$profileId:$from"
        val sidesToKey = "sides:$profileId:$to"
        val sidesFrom = dao.get(sidesFromKey)
        var sidesMoved = false
        if (sidesFrom != null) {
            if (dao.get(sidesToKey) == null) {
                dao.put(Kv(sidesToKey, sidesFrom))
                sidesMoved = true
            }
            dao.remove(sidesFromKey)
        }

        // The templates, block by block.
        val templates = dao.templatesNow(profileId).mapNotNull { t ->
            renameInBlocks(t.blocks, from, to)?.also { dao.put(t.copy(blocks = it)) }?.let { TemplateChange(t.id, t.blocks, it) }
        }

        // The weekly program's snapshots of what each training day was planned with.
        val programKey = "program:$profileId"
        val programBefore = dao.get(programKey)
        val programAfter = renameInProgram(programBefore, from, to)
        if (programAfter != null) dao.put(Kv(programKey, programAfter))

        val entry = RenameEntry(
            at, from, to, merged, ids, workouts,
            source?.toJson(), target?.toJson(), adopted != null, regrouped.map { it.name },
            progFrom, progMoved, templates, programBefore.takeIf { programAfter != null }, programAfter, draft,
            sidesFrom, sidesMoved,
        )
        dao.put(Kv(renameLogKey(profileId), RenameEntry.toText((listOf(entry) + readRenameLog(profileId)).take(RenameEntry.KEEP))))
        entry
    }

    /** The renames that can still be taken back, newest first. */
    suspend fun renameLog(profileId: Long): List<RenameEntry> = readRenameLog(profileId)

    /**
     * Takes [entry] back in one transaction: [from][RenameEntry.from] on exactly the rows that were renamed (those still named
     * [to][RenameEntry.to]; rows logged under the new name since were never in the entry and stay), the settings rows, grouping,
     * progression, templates and program as they were. A template or the program edited since is left as it is. Returns the
     * number of rows that got the old name back.
     */
    suspend fun undoRename(profileId: Long, entry: RenameEntry): Int = db.withTransaction {
        val now = dao.rowsNow(profileId)
        val back = restoreRows(now.map { IdRow(it.id, it.toRow()) }, entry.rows, entry.from, entry.to)
        val wasTo = now.filter { it.exerciseName == entry.to }.mapTo(HashSet()) { it.id }
        val restored = back.filter { it.id in wasTo && it.row.exerciseName == entry.from }
        restored.map { RowEntity.of(profileId, it.row).copy(id = it.id) }.chunked(500).forEach { dao.updateRows(it) }

        // Settings: the row made for the new name goes (or is replaced by the one it had), the old one comes back.
        val all = dao.settingsNow(profileId).associateBy { it.name }
        for (name in entry.regrouped) all[name]?.takeIf { it.overall == entry.to }?.let { dao.put(it.copy(overall = entry.from)) }
        if (entry.wroteTo) {
            if (entry.settingTo != null) dao.put(settingFromJson(entry.settingTo)) else dao.deleteSetting(profileId, entry.to)
        }
        if (entry.settingFrom != null) dao.put(settingFromJson(entry.settingFrom))

        if (entry.progFrom != null) {
            dao.put(Kv("prog:$profileId:${entry.from}", entry.progFrom))
            if (entry.progMoved) dao.remove("prog:$profileId:${entry.to}")
        }
        if (entry.sidesFrom != null) {
            dao.put(Kv("sides:$profileId:${entry.from}", entry.sidesFrom))
            if (entry.sidesMoved) dao.remove("sides:$profileId:${entry.to}")
        }
        for (change in entry.templates) {
            val template = dao.templatesNow(profileId).firstOrNull { it.id == change.id }
            if (template != null && template.blocks == change.after) dao.put(template.copy(blocks = change.before))
        }
        if (entry.programBefore != null && dao.get("program:$profileId") == entry.programAfter) dao.put(Kv("program:$profileId", entry.programBefore))

        dao.put(Kv(renameLogKey(profileId), RenameEntry.toText(readRenameLog(profileId).filterNot { it.sameAs(entry) })))
        restored.size
    }

    // Under a `draft:` key, which the options flow leaves out: the log can be large, and nothing on screen watches it.
    private fun renameLogKey(profileId: Long) = "draft:renames:$profileId"
    private suspend fun readRenameLog(profileId: Long) = RenameEntry.listFrom(dao.get(renameLogKey(profileId)))

    /** [blocks] (a template's stored JSON) with [from] named [to], or null when no block names it. */
    private fun renameInBlocks(blocks: String, from: String, to: String): String? = runCatching {
        val array = JSONArray(blocks)
        var changed = false
        for (i in 0 until array.length()) {
            val block = array.getJSONObject(i)
            if (block.optString("exercise") == from) {
                block.put("exercise", to)
                changed = true
            }
        }
        if (changed) array.toString() else null
    }.getOrNull()

    /** Workout # is 1-based and ascending in date order. Rows that already say so are left alone. */
    private suspend fun renumber(profileId: Long) {
        var number = 0
        var date: String? = null
        val changed = mutableListOf<RowEntity>()
        for (row in dao.rowsNow(profileId)) {
            if (row.date != date) {
                date = row.date
                number++
            }
            if (row.workoutNumber != number.toString()) changed += row.copy(workoutNumber = number.toString())
        }
        changed.chunked(500).forEach { dao.updateRows(it) }
    }

    // --- Measurements ---

    suspend fun previewMeasurements(profileId: Long, bytes: ByteArray): MeasurementImport {
        val files = Measurements.readZip(bytes)
        val have = dao.measurementsNow(profileId).mapTo(HashSet()) { it.file to it.date }
        val all = files.flatMap { f -> f.entries.map { MeasurementEntity(profileId = profileId, file = f.name, date = it.date, value = it.value) } }
        val fresh = all.filter { (it.file to it.date) !in have }
        return MeasurementImport(files, fresh, all.size - fresh.size)
    }

    suspend fun commit(profileId: Long, import: MeasurementImport) = db.withTransaction {
        val known = dao.filesNow(profileId).mapTo(HashSet()) { it.name }
        dao.insertFiles(import.files.filter { it.name !in known }.map { MeasurementFileEntity(profileId = profileId, name = it.name, header = it.valueHeader) })
        dao.insertMeasurements(import.newEntries)
    }

    suspend fun exportMeasurements(profileId: Long, timeMillis: Long): ByteArray {
        val entries = dao.measurementsNow(profileId).groupBy { it.file }
        val files = dao.filesNow(profileId).map { f ->
            MeasurementFile(f.name, f.header, entries[f.name].orEmpty().map { MeasurementEntry(it.date, it.value) })
        }
        return withContext(Dispatchers.Default) { Measurements.writeZip(files, timeMillis) }
    }

    /** Strong stores values as 32-bit floats, which is where `73.30000305175781` comes from; new entries are written the same way ([Measurements.newText]). */
    suspend fun addMeasurement(profileId: Long, file: String, date: String, value: Double) =
        dao.insertMeasurements(listOf(MeasurementEntity(profileId = profileId, file = file, date = date, value = Measurements.newText(value))))
}
